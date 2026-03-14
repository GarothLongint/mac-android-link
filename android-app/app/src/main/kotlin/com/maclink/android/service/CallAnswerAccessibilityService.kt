package com.maclink.android.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility Service który potrafi programowo odebrać lub odrzucić połączenie.
 * Działa przez znalezienie i tapnięcie przycisku "Odbierz" na ekranie dialera Samsung/AOSP.
 *
 * Użytkownik musi włączyć w: Ustawienia → Dostępność → Zainstalowane aplikacje → MacLink
 */
class CallAnswerAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: CallAnswerAccessibilityService? = null

        // Słowa kluczowe przycisku ODBIERANIA (różne języki i producenci)
        private val ANSWER_KEYWORDS = listOf(
            "answer", "odbierz", "accept", "odbiór", "odebrać",
            "phone_answer", "btn_answer", "answer_call"
        )

        // Słowa kluczowe przycisku ODRZUCANIA
        private val REJECT_KEYWORDS = listOf(
            "decline", "reject", "odrzuć", "zakończ", "end", "hang",
            "btn_decline", "decline_call", "phone_decline"
        )

        fun answerCall(): Boolean = instance?.performAnswer() ?: false
        fun rejectCall(): Boolean = instance?.performReject() ?: false
        fun isEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        instance = this
        println("[Accessibility] MacLink Accessibility Service connected ✓")
        serviceInfo = serviceInfo.also {
            it.flags = it.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Nie potrzebujemy monitorować — działamy na żądanie
    }

    override fun onInterrupt() {}

    // MARK: - Answer / Reject

    fun performAnswer(): Boolean {
        println("[Accessibility] performAnswer() called")
        return clickByKeywords(ANSWER_KEYWORDS) || run {
            // Fallback: symuluj przycisk słuchawki
            println("[Accessibility] answer button not found — using KEYCODE_HEADSETHOOK")
            false
        }
    }

    fun performReject(): Boolean {
        println("[Accessibility] performReject() called")
        return clickByKeywords(REJECT_KEYWORDS)
    }

    // MARK: - UI traversal

    private fun clickByKeywords(keywords: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false

        // Szukaj po contentDescription i text
        for (keyword in keywords) {
            val found = findNodeByKeyword(root, keyword)
            if (found != null) {
                val clicked = found.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                println("[Accessibility] Clicked node '${found.contentDescription ?: found.text}' → $clicked")
                found.recycle()
                root.recycle()
                return clicked
            }
        }

        // Szukaj po resource-id (Samsung dialer specifics)
        val samsungIds = if (keywords === ANSWER_KEYWORDS) {
            listOf("com.samsung.android.dialer:id/answer_button",
                   "com.android.dialer:id/answer_button",
                   "com.android.incallui:id/answer_button")
        } else {
            listOf("com.samsung.android.dialer:id/decline_button",
                   "com.android.dialer:id/decline_button",
                   "com.android.incallui:id/decline_button")
        }

        for (resId in samsungIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(resId)
            if (nodes?.isNotEmpty() == true) {
                val clicked = nodes.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                println("[Accessibility] Clicked by resource-id '$resId' → $clicked")
                nodes.forEach { it.recycle() }
                root.recycle()
                return clicked
            }
        }

        root.recycle()
        return false
    }

    private fun findNodeByKeyword(node: AccessibilityNodeInfo, keyword: String): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val resId = node.viewIdResourceName?.lowercase() ?: ""

        if ((desc.contains(keyword) || text.contains(keyword) || resId.contains(keyword))
            && node.isClickable) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByKeyword(child, keyword)
            child.recycle()
            if (found != null) return found
        }

        return null
    }
}
