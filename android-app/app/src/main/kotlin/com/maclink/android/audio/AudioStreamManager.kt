package com.maclink.android.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.maclink.android.proto.MacLinkProto.AudioFrame
import com.maclink.android.proto.MacLinkProto.Envelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Bidirectional raw-PCM audio streaming between Android and Mac.
 *
 * Format: 16-bit signed PCM, 16 000 Hz, mono, 20 ms chunks (640 bytes).
 *
 * Podczas rozmowy przez MacLink:
 *   - STREAM_VOICE_CALL wyciszony → GSM audio z telefonu nie słychać
 *   - Mac mic → AudioTrack (STREAM_MUSIC, osobny) → głośnik telefonu
 *   - Mikrofon telefonu → AudioRecord → Mac (bez mutu)
 *   - Po rozmowie: przywrócenie oryginalnych ustawień głośności i speakerphone
 */
class AudioStreamManager(private val context: Context) {

    private val TAG = "MacLink.Audio"

    private val sampleRate = 16_000
    private val channelIn = AudioFormat.CHANNEL_IN_MONO
    private val channelOut = AudioFormat.CHANNEL_OUT_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT
    private val bytesPerChunk = 640 // 320 samples × 2 bytes, 20 ms

    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null

    private var sendEnvelope: ((Envelope) -> Unit)? = null
    private var currentCallId: String = ""

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recordJob: Job? = null

    var isStreaming = false
        private set

    // MARK: - Start / Stop

    @SuppressLint("MissingPermission")
    fun startStreaming(callId: String, send: (Envelope) -> Unit) {
        if (isStreaming) return
        currentCallId = callId
        sendEnvelope = send
        isStreaming = true

        // DO NOT touch AudioManager settings — touching speakerphone or stream volumes
        // during an active VoIP call (WhatsApp etc.) causes the call to be suspended.
        // Let the calling app (WhatsApp/dialer) manage its own audio session.

        Log.i(TAG, "Audio streaming started passively (callId=$callId)")

        setupPlayer()
        setupRecorder()
        if (isStreaming) startRecordLoop()
    }

    fun stopStreaming() {
        if (!isStreaming) return
        isStreaming = false

        recordJob?.cancel()
        recordJob = null

        recorder?.stop()
        recorder?.release()
        recorder = null

        player?.stop()
        player?.release()
        player = null

        Log.i(TAG, "Audio streaming stopped")
    }

    // MARK: - Incoming audio from Mac → play through Android speaker

    fun playIncoming(pcmData: ByteArray) {
        val p = player ?: return
        if (!isStreaming || pcmData.isEmpty()) return
        p.write(pcmData, 0, pcmData.size)
    }

    // MARK: - Setup helpers

    @SuppressLint("MissingPermission")
    private fun setupRecorder() {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelIn, encoding)
        val bufSize = maxOf(minBuf, bytesPerChunk * 4)

        // Use MIC source — least invasive, doesn't steal audio focus from WhatsApp/dialer.
        // VOICE_COMMUNICATION would compete with VoIP apps and suspend their call.
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC,
            sampleRate, channelIn, encoding, bufSize)

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord(MIC) failed — no RECORD_AUDIO permission?")
            rec.release()
            isStreaming = false
            return
        }

        recorder = rec
        rec.startRecording()
        Log.d(TAG, "AudioRecord(MIC) started (bufSize=$bufSize)")
    }

    private fun setupPlayer() {
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelOut, encoding)
        val bufSize = maxOf(minBuf, bytesPerChunk * 4)

        // USAGE_MEDIA — plays through media stream independently of VoIP call audio.
        // Does not interfere with WhatsApp or GSM call audio session.
        player = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelOut)
                .setEncoding(encoding)
                .build(),
            bufSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        ).also { it.play() }

        Log.d(TAG, "AudioTrack(USAGE_MEDIA) started (bufSize=$bufSize)")
    }

    // MARK: - Record loop: phone mic → Mac

    private fun startRecordLoop() {
        recordJob = scope.launch {
            val buf = ByteArray(bytesPerChunk)
            val rec = recorder ?: return@launch
            var packetsSent = 0

            while (isActive && isStreaming) {
                val read = rec.read(buf, 0, buf.size)
                if (read > 0) {
                    val chunk = if (read == buf.size) buf.clone() else buf.copyOf(read)
                    sendAudioFrame(chunk)
                    packetsSent++
                    if (packetsSent % 50 == 0) { // log co ~1s (50 × 20ms)
                        Log.d(TAG, "Sent $packetsSent audio packets to Mac")
                    }
                }
            }
        }
    }

    private fun sendAudioFrame(pcm: ByteArray) {
        val frame = AudioFrame.newBuilder()
            .setCallId(currentCallId)
            .setPcmData(com.google.protobuf.ByteString.copyFrom(pcm))
            .build()

        val envelope = Envelope.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setTimestamp(System.currentTimeMillis())
            .setAudioFrame(frame)
            .build()

        sendEnvelope?.invoke(envelope)
    }
}
