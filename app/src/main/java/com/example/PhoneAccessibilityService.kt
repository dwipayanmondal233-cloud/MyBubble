package com.example.mybubble

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PhoneAccessibilityService : AccessibilityService() {

    companion object {
        var instance: PhoneAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

        override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            BubbleService.instance?.onInputFocusMaybeChanged()
        }
    }

    fun editableFocusState(): Boolean? {
        val root = rootInActiveWindow ?: return null
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return node.isEditable
    }

    override fun onInterrupt() {}

    fun insertText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false

        val current = if (node.isShowingHintText) "" else (node.text?.toString() ?: "")
        var start = node.textSelectionStart
        var end = node.textSelectionEnd
        if (start < 0 || end < 0 || start > current.length || end > current.length) {
            start = current.length
            end = current.length
        }
        val low = minOf(start, end)
        val high = maxOf(start, end)
        val newText = current.substring(0, low) + text + current.substring(high)

        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
                var ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!ok) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("phone", text))
            ok = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }

        val sel = Bundle()
        val pos = low + text.length
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, pos)
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, pos)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)

        return ok
    }
}