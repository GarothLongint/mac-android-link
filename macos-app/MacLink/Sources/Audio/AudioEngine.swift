import AVFoundation
import Foundation

/// Streams audio bidirectionally between Mac and Android during a phone call.
///
/// Flow:
///   Mac mic  → AVAudioEngine input tap → Int16 PCM chunks → send AudioFrame to Android
///   Android  → receive AudioFrame      → Int16 PCM data   → schedule on AVAudioPlayerNode → Mac speakers
///
/// Internal format: Float32 (native AVAudioEngine format), 16 000 Hz, mono.
/// Network format : Int16 PCM, 16 000 Hz, mono (bandwidth-efficient).
final class AudioEngine: @unchecked Sendable {

    static let shared = AudioEngine()

    private let sampleRate: Double = 16_000
    // 20 ms at 16 kHz = 320 samples per chunk
    private let framesPerChunk: AVAudioFrameCount = 320

    private let engine = AVAudioEngine()
    private let playerNode = AVAudioPlayerNode()

    // Float32 format used inside AVAudioEngine
    private let float32Format: AVAudioFormat = {
        AVAudioFormat(commonFormat: .pcmFormatFloat32,
                      sampleRate: 16_000,
                      channels: 1,
                      interleaved: false)!
    }()

    private(set) var isStreaming = false
    private var callId: String = ""

    /// Provide raw serialized Maclink_Envelope bytes to send over TCP.
    var sendData: ((Data) -> Void)?

    // MARK: - Start / Stop

    func startStreaming(callId: String) {
        guard !isStreaming else { return }
        self.callId = callId
        self.isStreaming = true

        do {
            try setupEngine()
            try engine.start()
            playerNode.play()
            print("[AudioEngine] Started streaming for call \(callId)")
        } catch {
            print("[AudioEngine] Failed to start: \(error)")
            isStreaming = false
        }
    }

    func stopStreaming() {
        guard isStreaming else { return }
        isStreaming = false

        // Pusty frame = sygnał do Androida aby zatrzymał AudioStreamManager
        sendAudioFrame(Data())

        engine.inputNode.removeTap(onBus: 0)
        playerNode.stop()
        engine.stop()
        engine.reset()
        print("[AudioEngine] Stopped streaming")
    }

    // MARK: - Receive audio from Android → play on Mac speakers

    private var framesReceived = 0

    func receiveAudioFrame(_ int16Data: Data) {
        guard isStreaming, !int16Data.isEmpty else { return }

        framesReceived += 1
        if framesReceived % 50 == 0 {
            print("[AudioEngine] Received \(framesReceived) audio frames from Android")
        }

        let frameCount = AVAudioFrameCount(int16Data.count / 2) // 2 bytes / sample
        guard let buffer = AVAudioPCMBuffer(pcmFormat: float32Format,
                                            frameCapacity: frameCount) else { return }
        buffer.frameLength = frameCount

        // Convert Int16 → Float32 [-1.0, 1.0]
        int16Data.withUnsafeBytes { rawPtr in
            guard let src = rawPtr.baseAddress?.assumingMemoryBound(to: Int16.self),
                  let dst = buffer.floatChannelData?.pointee else { return }
            for i in 0 ..< Int(frameCount) {
                dst[i] = Float(src[i]) / 32768.0
            }
        }

        playerNode.scheduleBuffer(buffer, completionHandler: nil)
    }

    // MARK: - Engine setup

    private func setupEngine() throws {
        let inputNode = engine.inputNode
        let inputFormat = inputNode.inputFormat(forBus: 0)

        // Player node for incoming Android audio
        engine.attach(playerNode)
        engine.connect(playerNode, to: engine.mainMixerNode, format: float32Format)

        // Converter: native input format → 16 kHz Float32 mono
        guard let converter = AVAudioConverter(from: inputFormat, to: float32Format) else {
            throw AudioEngineError.converterFailed
        }

        inputNode.installTap(onBus: 0,
                             bufferSize: 4096,
                             format: inputFormat) { [weak self] buffer, _ in
            self?.processMicBuffer(buffer, converter: converter)
        }
    }

    // MARK: - Mic capture → convert → send to Android

    private func processMicBuffer(_ buffer: AVAudioPCMBuffer, converter: AVAudioConverter) {
        guard isStreaming else { return }

        // Output capacity: scaled for sample rate difference
        let ratio = float32Format.sampleRate / buffer.format.sampleRate
        let capacity = AVAudioFrameCount(Double(buffer.frameLength) * ratio) + 1

        guard let outBuffer = AVAudioPCMBuffer(pcmFormat: float32Format,
                                               frameCapacity: capacity) else { return }

        var error: NSError?
        var sourceConsumed = false
        converter.convert(to: outBuffer, error: &error) { _, outStatus in
            if sourceConsumed {
                outStatus.pointee = .noDataNow
                return nil
            }
            sourceConsumed = true
            outStatus.pointee = .haveData
            return buffer
        }

        guard error == nil, outBuffer.frameLength > 0,
              let floatPtr = outBuffer.floatChannelData?.pointee else { return }

        // Convert Float32 → Int16 for network
        let frameCount = Int(outBuffer.frameLength)
        var int16Bytes = Data(count: frameCount * 2)
        int16Bytes.withUnsafeMutableBytes { rawPtr in
            guard let dst = rawPtr.baseAddress?.assumingMemoryBound(to: Int16.self) else { return }
            for i in 0 ..< frameCount {
                let clamped = max(-1.0, min(1.0, floatPtr[i]))
                dst[i] = Int16(clamped * 32767.0)
            }
        }

        sendAudioFrame(int16Bytes)
    }

    private func sendAudioFrame(_ int16Data: Data) {
        guard let sendData else { return }

        var frame = Maclink_AudioFrame()
        frame.callID = callId
        frame.pcmData = int16Data

        var envelope = Maclink_Envelope()
        envelope.id = UUID().uuidString
        envelope.timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        envelope.audioFrame = frame

        guard let bytes = try? envelope.serializedData() else { return }
        sendData(bytes)
    }
}

enum AudioEngineError: Error {
    case converterFailed
}

