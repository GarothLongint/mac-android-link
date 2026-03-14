package com.maclink.android.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import com.maclink.android.proto.MacLinkProto.CallEvent
import com.maclink.android.proto.MacLinkProto.Envelope
import com.google.protobuf.ByteString
import java.io.ByteArrayOutputStream
import java.util.UUID

@RequiresApi(Build.VERSION_CODES.S)
class CallDetectorService(private val context: Context) {

    private var currentCallId: String? = null
    private var currentNumber: String? = null
    private var sendEnvelope: ((Envelope) -> Unit)? = null

    fun init(send: (Envelope) -> Unit) {
        this.sendEnvelope = send
        registerTelephonyCallback()
    }

    private val telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            handleCallState(state, null)
        }
    }

    // Telephony callback that also provides the phone number (requires READ_PHONE_STATE + READ_CALL_LOG)
    private val telephonyCallbackWithNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                handleCallState(state, null)
            }
        }
    } else null

    private fun registerTelephonyCallback() {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.registerTelephonyCallback(context.mainExecutor, telephonyCallback)
            println("[CallDetector] TelephonyCallback registered")
        } catch (e: SecurityException) {
            println("[CallDetector] No permission for TelephonyCallback: $e — call detection disabled")
        } catch (e: Exception) {
            println("[CallDetector] Failed to register TelephonyCallback: $e")
        }
    }

    fun unregister() {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        tm.unregisterTelephonyCallback(telephonyCallback)
    }

    private fun handleCallState(state: Int, number: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                // New incoming call
                currentCallId = UUID.randomUUID().toString()
                currentNumber = number
                val callerNumber = number ?: ""
                val (callerName, photoBytes) = lookupContact(callerNumber)
                sendCallEvent(
                    callId = currentCallId!!,
                    state = CallEvent.State.INCOMING,
                    callerName = callerName,
                    callerNumber = callerNumber,
                    photoBytes = photoBytes
                )
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                val id = currentCallId
                if (id != null) {
                    // Existing call was answered (incoming → accepted)
                    sendCallEvent(
                        callId = id,
                        state = CallEvent.State.ACCEPTED,
                        callerName = "",
                        callerNumber = currentNumber ?: "",
                        photoBytes = null
                    )
                } else {
                    // New outgoing call
                    currentCallId = UUID.randomUUID().toString()
                    val callerNumber = number ?: ""
                    val (callerName, photoBytes) = lookupContact(callerNumber)
                    sendCallEvent(
                        callId = currentCallId!!,
                        state = CallEvent.State.OUTGOING,
                        callerName = callerName,
                        callerNumber = callerNumber,
                        photoBytes = photoBytes
                    )
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                val id = currentCallId ?: return
                sendCallEvent(
                    callId = id,
                    state = CallEvent.State.ENDED,
                    callerName = "",
                    callerNumber = currentNumber ?: "",
                    photoBytes = null
                )
                currentCallId = null
                currentNumber = null
            }
        }
    }

    private fun sendCallEvent(
        callId: String,
        state: CallEvent.State,
        callerName: String,
        callerNumber: String,
        photoBytes: ByteArray?
    ) {
        val builder = CallEvent.newBuilder()
            .setCallId(callId)
            .setState(state)
            .setCallerName(callerName)
            .setCallerNumber(callerNumber)
        if (photoBytes != null) {
            builder.setCallerPhotoPng(ByteString.copyFrom(photoBytes))
        }
        val callEvent = builder.build()
        val envelope = Envelope.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setTimestamp(System.currentTimeMillis())
            .setCallEvent(callEvent)
            .build()
        sendEnvelope?.invoke(envelope)
    }

    private fun lookupContact(number: String): Pair<String, ByteArray?> {
        if (number.isBlank()) return Pair("", null)
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            val projection = arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_URI
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val photoIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val photoUriStr = if (photoIdx >= 0) cursor.getString(photoIdx) else null
                    val photo = photoUriStr?.let { loadContactPhoto(it) }
                    return Pair(name, photo)
                }
            }
        } catch (e: Exception) {
            println("[CallDetector] Contact lookup failed: $e")
        }
        return Pair("", null)
    }

    private fun loadContactPhoto(photoUriStr: String): ByteArray? {
        return try {
            val uri = Uri.parse(photoUriStr)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val original = BitmapFactory.decodeStream(stream) ?: return null
                val scaled = Bitmap.createScaledBitmap(original, 64, 64, true)
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.PNG, 90, out)
                if (scaled != original) scaled.recycle()
                original.recycle()
                out.toByteArray()
            }
        } catch (e: Exception) {
            println("[CallDetector] Photo load failed: $e")
            null
        }
    }

    @SuppressLint("MissingPermission")
    fun answerCall() {
        // Próba 1: TelecomManager (działa na czystym Androidzie, Samsung często blokuje)
        try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                tm.acceptRingingCall()
                println("[CallDetector] answerCall via TelecomManager")
                return
            }
        } catch (e: Exception) {
            println("[CallDetector] TelecomManager.acceptRingingCall failed: $e")
        }

        // Próba 2: HEADSETHOOK broadcast (symuluje przycisk słuchawki)
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(android.content.Intent.EXTRA_KEY_EVENT,
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_HEADSETHOOK))
            }
            context.sendOrderedBroadcast(intent, null)
            println("[CallDetector] answerCall via HEADSETHOOK")
        } catch (e: Exception) {
            println("[CallDetector] HEADSETHOOK failed: $e")
        }

        // Próba 3: Pokaż powiadomienie z prośbą o tapnięcie (fallback dla Samsung)
        showAnswerPromptNotification()
    }

    @SuppressLint("MissingPermission")
    fun rejectCall() {
        try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            @Suppress("DEPRECATION")
            tm.endCall()
            println("[CallDetector] rejectCall via TelecomManager")
            return
        } catch (e: Exception) {
            println("[CallDetector] TelecomManager.endCall failed: $e")
        }
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(android.content.Intent.EXTRA_KEY_EVENT,
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_HEADSETHOOK))
            }
            context.sendOrderedBroadcast(intent, null)
        } catch (e: Exception) {
            println("[CallDetector] rejectCall broadcast failed: $e")
        }
    }

    @SuppressLint("MissingPermission")
    private fun showAnswerPromptNotification() {
        try {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            val channelId = "maclink_answer_prompt"
            nm.createNotificationChannel(
                android.app.NotificationChannel(channelId, "MacLink — odbierz połączenie",
                    android.app.NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    setSound(null, null)
                }
            )
            val notif = android.app.Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("Odbierz połączenie z Maca")
                .setContentText("Kliknij aby odebrać połączenie")
                .setAutoCancel(true)
                .setFullScreenIntent(null, true)
                .build()
            nm.notify(9001, notif)
        } catch (e: Exception) {
            println("[CallDetector] showAnswerPrompt failed: $e")
        }
    }
}
