package com.rgalaxy.optimizer

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Bir makro adımı: ekranda bu metinlerden birini içeren öğeyi bul ve dokun. */
data class Step(val texts: List<String>, val optional: Boolean = false)

/**
 * Samsung Ayarlar'da metne göre gezinen makro. GÜVENLİK: Ağ sıfırlamada son
 * onay düğmesine (Wi-Fi şifrelerini siler) BASILMAZ; kullanıcıya bırakılır.
 */
class MacroAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var steps: List<Step> = emptyList()
    private var idx = 0
    private var ticks = 0
    private var running = false
    private var onDone: ((Boolean) -> Unit)? = null

    companion object {
        @Volatile var instance: MacroAccessibilityService? = null
        val NETWORK_RESET = listOf(
            Step(listOf("Genel yönetim", "General management")),
            Step(listOf("Sıfırla", "Reset")),
            Step(listOf("Ağ ayarlarını sıfırla", "Reset network settings")),
            Step(listOf("Ayarları sıfırla", "Reset settings"), optional = true) // son onaya basılmaz, bkz. tick()
        )
        val DATA_SAVER = listOf(
            Step(listOf("Bağlantılar", "Connections")),
            Step(listOf("Veri kullanımı", "Data usage")),
            Step(listOf("Veri tasarrufu", "Data saver"))
        )
    }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onInterrupt() { stopMacro(false) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* ilerleme zamanlayıcıyla yürür */ }

    fun startMacro(s: List<Step>, done: (Boolean) -> Unit) {
        steps = s; idx = 0; ticks = 0; running = true; onDone = done
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ tick() }, 1200)
    }

    private fun stopMacro(ok: Boolean) {
        if (!running) return
        running = false
        handler.removeCallbacksAndMessages(null)
        onDone?.invoke(ok)
    }

    private fun tick() {
        if (!running) return
        if (++ticks > 45) { stopMacro(false); return }
        val root = rootInActiveWindow
        if (root != null && idx < steps.size) {
            val step = steps[idx]
            val node = step.texts.firstNotNullOfOrNull { findByText(root, it) }
            if (node != null) {
                val isFinalConfirm = step.optional && idx == steps.size - 1
                if (isFinalConfirm) { stopMacro(true); return } // kullanıcı kendisi onaylar
                if (clickNode(node)) { idx++; if (idx >= steps.size) { stopMacro(true); return } }
            } else {
                scrollForward(root)
            }
        }
        handler.postDelayed({ tick() }, 1000)
    }

    private fun findByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? =
        root.findAccessibilityNodeInfosByText(text).firstOrNull()

    private fun clickNode(n: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = n
        while (cur != null) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            cur = cur.parent
        }
        return false
    }

    private fun scrollForward(root: AccessibilityNodeInfo) {
        val q = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            if (n.isScrollable && n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return
            for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
        }
    }
}
