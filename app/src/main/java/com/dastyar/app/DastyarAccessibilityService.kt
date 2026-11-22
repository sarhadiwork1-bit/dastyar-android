package com.dastyar.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

/** Lets the assistant read the screen and click/type/scroll in any app (except blocked ones). */
class DastyarAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: DastyarAccessibilityService? = null
    }

    private var snap: List<AccessibilityNodeInfo> = emptyList()

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun blocked(): Set<String> =
        Prefs.blockedPackages.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun root(): AccessibilityNodeInfo {
        val r = rootInActiveWindow ?: throw IllegalStateException("no active window")
        val pkg = r.packageName?.toString() ?: ""
        if (pkg in blocked()) throw IllegalStateException("this app is blocked by the user's settings: $pkg")
        return r
    }

    fun readScreen(): String {
        val r = root()
        val out = ArrayList<AccessibilityNodeInfo>()
        val sb = StringBuilder("package=${r.packageName}\n")
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (out.size >= 150 || depth > 30) return
            if (n.isVisibleToUser && !n.isPassword) {
                val txt = n.text?.toString()?.take(80)
                val d = n.contentDescription?.toString()?.take(80)
                if (!txt.isNullOrBlank() || !d.isNullOrBlank() || n.isClickable || n.isEditable) {
                    out.add(n)
                    sb.append('[').append(out.size - 1).append("] ")
                        .append(n.className?.toString()?.substringAfterLast('.') ?: "?")
                    if (!txt.isNullOrBlank()) sb.append(" text=\"").append(txt).append('"')
                    if (!d.isNullOrBlank()) sb.append(" desc=\"").append(d).append('"')
                    if (n.isClickable) sb.append(" clickable")
                    if (n.isEditable) sb.append(" editable")
                    if (n.isCheckable) sb.append(if (n.isChecked) " checked" else " unchecked")
                    if (n.isScrollable) sb.append(" scrollable")
                    sb.append('\n')
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(r, 0)
        snap = out
        return sb.toString()
    }

    fun nodeAt(i: Int): AccessibilityNodeInfo? = snap.getOrNull(i)

    fun describeNode(n: AccessibilityNodeInfo): String =
        listOfNotNull(n.text?.toString(), n.contentDescription?.toString()).joinToString(" / ")

    fun findByText(t: String): AccessibilityNodeInfo? =
        root().findAccessibilityNodeInfosByText(t).firstOrNull { it.isVisibleToUser }

    fun clickNode(n: AccessibilityNodeInfo): Boolean {
        var p: AccessibilityNodeInfo? = n
        while (p != null) {
            if (p.isClickable && p.isEnabled) return p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            p = p.parent
        }
        return false
    }

    fun typeText(text: String, replace: Boolean): String {
        val r = root()
        val target = r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(r)
            ?: return "no text field found; click a text field first"
        if (target.isPassword) return "refused: password field"
        val cur = if (replace) "" else (target.text?.toString() ?: "")
        val args = Bundle().putCharSequenceCompat(cur + text)
        return if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "typed" else "typing failed"
    }

    private fun Bundle.putCharSequenceCompat(v: CharSequence): Bundle {
        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, v)
        return this
    }

    private fun findEditable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isEditable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { c -> findEditable(c)?.let { return it } }
        return null
    }

    private fun findScrollable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isScrollable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { c -> findScrollable(c)?.let { return it } }
        return null
    }

    fun scroll(down: Boolean): String {
        val n = findScrollable(root()) ?: return "nothing scrollable"
        val a = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return if (n.performAction(a)) "scrolled" else "scroll failed"
    }

    fun global(action: String): String {
        val code = when (action) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "lock" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_LOCK_SCREEN else return "unsupported"
            "screenshot" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_TAKE_SCREENSHOT else return "unsupported"
            else -> return "unknown action"
        }
        return if (performGlobalAction(code)) "done" else "failed"
    }

    private fun stateOf(start: AccessibilityNodeInfo): Boolean? {
        var p: AccessibilityNodeInfo? = start
        for (i in 0 until 4) {
            if (p == null) return null
            if (p.isCheckable) return p.isChecked
            val s = buildString {
                if (Build.VERSION.SDK_INT >= 30) append(p!!.stateDescription ?: "").append(' ')
                append(p!!.contentDescription ?: "")
            }.lowercase()
            if (Regex("\\boff\\b|خاموش").containsMatchIn(s)) return false
            if (Regex("\\bon\\b|روشن").containsMatchIn(s)) return true
            p = p.parent
        }
        return null
    }

    /** Opens the quick-settings panel, finds the tile by any of its labels and toggles it. Best effort. */
    suspend fun quickToggle(labels: List<String>, on: Boolean): String {
        if (!performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)) return "could not open quick settings"
        delay(1000)
        val r = rootInActiveWindow ?: return "quick settings not readable"
        var node: AccessibilityNodeInfo? = null
        for (l in labels) {
            node = r.findAccessibilityNodeInfosByText(l).firstOrNull { it.isVisibleToUser }
            if (node != null) break
        }
        if (node == null) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return "tile not found; use open_settings + ui_* tools instead"
        }
        val state = stateOf(node)
        val msg = when {
            state == on -> "already ${if (on) "on" else "off"}"
            clickNode(node) -> if (state == null) "tapped the tile (previous state unknown, verify)" else "turned ${if (on) "on" else "off"}"
            else -> "tile not clickable"
        }
        delay(500)
        performGlobalAction(GLOBAL_ACTION_BACK)
        return msg
    }
}
