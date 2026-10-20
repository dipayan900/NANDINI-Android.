package com.dipayan.nandini

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional user-enabled service for reading visible accessibility text and current app identity.
 * It does not record or transmit screen contents automatically.
 */
class NandiniAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        val root = rootInActiveWindow ?: return
        val lines = ArrayList<String>()
        collectVisibleText(root, lines)
        getSharedPreferences("nandini_screen_context", MODE_PRIVATE).edit()
            .putString("package", pkg)
            .putString("visible_text", lines.take(120).joinToString("\n").take(12000))
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
        root.recycle()
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo, output: MutableList<String>) {
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        if (text.isNotBlank() && output.size < 120) output.add(text)
        if (description.isNotBlank() && description != text && output.size < 120) output.add(description)
        for (i in 0 until node.childCount) {
            if (output.size >= 120) break
            val child = node.getChild(i) ?: continue
            collectVisibleText(child, output)
            child.recycle()
        }
    }

    override fun onInterrupt() = Unit
}
