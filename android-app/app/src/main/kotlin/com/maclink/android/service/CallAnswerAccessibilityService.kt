package com.maclink.android.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility Service który potrafi programowo odebrać lub odrzucić połączenie.
 * Przeszukuje WSZYSTKIE okna (nie tylko rootInActiveWindow) żeby znaleźć
 * przycisk odbierz/odrzuć w ekranie przychodzącego połączenia.
 *
 * Użytkownik musi włączyć w: Ustawienia → Dostępność → Zainstalowane aplikacje → MacLink
 */
class CallAnswerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MacLink.Accessibility"

        @Volatile var instance: CallAnswerAccessibilityService? = null

        private val DIALER_PACKAGES = setOf(
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.android.dialer",
            "com.android.incallui",
            "com.google.android.dialer",
            "com.google.android.incallui"
        )

        // Resource-id przycisku odbierania — Samsung / AOSP / Google
        private val ANSWER_RESOURCE_IDS = listOf(
            "com.samsung.android.dialer:id/answer_button",
            "com.samsung.android.dialer:id/incoming_call_answer_button",
            "com.samsung.android.dialer:id/btn_incoming_call_accept",
            "com.samsung.android.incallui:id/answer_button",
            "com.samsung.android.incallui:id/incoming_call_answer_button",
            "com.android.dialer:id/answer_button",
            "com.android.incallui:id/answer_button",
            "com.google.android.dialer:id/answer_button"
        )

        private val DECLINE_RESOURCE_IDS = listOf(
            "com.samsung.android.dialer:id/decline_button",
            "com.samsung.android.dialer:id/incoming_call_decline_button",
            "com.samsung.android.incallui:id/decline_button",
            "com.android.dialer:id/decline_button",
            "com.android.incallui:id/decline_button",
            "com.google.android.dialer:id/decline_button"
        )

        // Resource-id przycisku kończenia AKTYWNEJ rozmowy (inny niż odrzucenie)
        private val END_CALL_RESOURCE_IDS = listOf(
            "com.samsung.android.incallui:id/end_call_button",
            "com.samsung.android.incallui:id/floating_end_call_button",
            "com.samsung.android.incallui:id/btn_endcall",
            "com.samsung.android.incallui:id/btn_end_call",
            "com.samsung.android.dialer:id/end_call_button",
            "com.android.incallui:id/end_call_button",
            "com.android.incallui:id/floating_end_call_button",
            "com.google.android.dialer:id/end_call_button"
        )

        // Słowa kluczowe (contentDescription / text) w różnych językach
        private val ANSWER_KEYWORDS = listOf(
            "answer", "odbierz", "accept", "odbiór", "odebrać", "odebranie",
            "phone_answer", "btn_answer", "answer_call", "accept call"
        )

        private val REJECT_KEYWORDS = listOf(
            "decline", "reject", "odrzuć",
            "btn_decline", "decline_call", "phone_decline", "reject call"
        )

        private val END_CALL_KEYWORDS = listOf(
            "end", "zakończ", "hang up", "hangup", "rozłącz",
            "end call", "zakończ połączenie", "btn_endcall"
        )

        fun answerCall(): Boolean = instance?.performAnswer() ?: false
        fun rejectCall(): Boolean = instance?.performReject() ?: false
        fun endCall(): Boolean = instance?.performEndCall() ?: false
        fun isEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        instance = this
        Log.i(TAG, "MacLink Accessibility Service connected ✓")
        serviceInfo = serviceInfo.also {
            it.flags = it.flags or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Loguj okna dialera żeby pomóc w debugowaniu
        val pkg = event.packageName?.toString() ?: return
        if (pkg in DIALER_PACKAGES && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d(TAG, "Dialer window event from $pkg: ${event.className}")
        }
    }

    override fun onInterrupt() {}

    // MARK: - Answer / Reject

    fun performAnswer(): Boolean {
        Log.i(TAG, "performAnswer() called — searching all windows")
        return clickInAllWindows(ANSWER_RESOURCE_IDS, ANSWER_KEYWORDS)
    }

    fun performReject(): Boolean {
        Log.i(TAG, "performReject() called — searching all windows")
        return clickInAllWindows(DECLINE_RESOURCE_IDS, REJECT_KEYWORDS)
    }

    fun performEndCall(): Boolean {
        Log.i(TAG, "performEndCall() called — searching all windows")
        // Próbuj END_CALL najpierw, potem DECLINE (działa dla przychodzących)
        return clickInAllWindows(END_CALL_RESOURCE_IDS, END_CALL_KEYWORDS)
            || clickInAllWindows(DECLINE_RESOURCE_IDS, REJECT_KEYWORDS)
    }

    // MARK: - Multi-window search

    /**
     * Przeszukuje WSZYSTKIE dostępne okna (nie tylko aktywne).
     * Ważne przy zablokowanym ekranie lub gdy incoming call jest jako overlay.
     */
    private fun clickInAllWindows(resourceIds: List<String>, keywords: List<String>): Boolean {
        // 1. Pobierz listę wszystkich okien
        val allWindows = windows ?: emptyList()
        Log.d(TAG, "Total accessible windows: ${allWindows.size}")

        for (window in allWindows) {
            val root = window.root ?: continue
            val pkgName = root.packageName?.toString() ?: ""
            Log.d(TAG, "Window pkg=$pkgName title=${window.title}")

            // Szukaj po resource-id (najbardziej precyzyjne)
            for (resId in resourceIds) {
                val nodes = root.findAccessibilityNodeInfosByViewId(resId)
                if (nodes?.isNotEmpty() == true) {
                    val node = nodes.first()
                    val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.i(TAG, "Clicked by resource-id '$resId' in pkg=$pkgName → $clicked")
                    nodes.forEach { it.recycle() }
                    root.recycle()
                    return clicked
                }
            }

            // Szukaj po tekście/contentDescription
            val found = findNodeByKeywords(root, keywords)
            if (found != null) {
                val clicked = found.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.i(TAG, "Clicked by keyword '${found.contentDescription ?: found.text}' in pkg=$pkgName → $clicked")
                found.recycle()
                root.recycle()
                return clicked
            }

            root.recycle()
        }

        // 2. Fallback: rootInActiveWindow (stary sposób)
        Log.w(TAG, "No button found in any window — trying rootInActiveWindow")
        val root = rootInActiveWindow
        if (root != null) {
            for (resId in resourceIds) {
                val nodes = root.findAccessibilityNodeInfosByViewId(resId)
                if (nodes?.isNotEmpty() == true) {
                    val node = nodes.first()
                    val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.i(TAG, "Clicked by resource-id '$resId' (fallback root) → $clicked")
                    nodes.forEach { it.recycle() }
                    root.recycle()
                    return clicked
                }
            }
            val found = findNodeByKeywords(root, keywords)
            if (found != null) {
                val clicked = found.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.i(TAG, "Clicked by keyword (fallback root) → $clicked")
                found.recycle()
                root.recycle()
                return clicked
            }
            root.recycle()
        }

        Log.e(TAG, "Could not find answer/reject button in any window!")
        return false
    }

    private fun findNodeByKeywords(node: AccessibilityNodeInfo, keywords: List<String>): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val resId = node.viewIdResourceName?.lowercase() ?: ""

        if (node.isClickable && keywords.any { kw ->
            desc.contains(kw) || text.contains(kw) || resId.contains(kw)
        }) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByKeywords(child, keywords)
            child.recycle()
            if (found != null) return found
        }

        return null
    }
}
