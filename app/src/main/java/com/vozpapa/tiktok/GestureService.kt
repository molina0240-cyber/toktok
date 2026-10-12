package com.vozpapa.tiktok

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Servicio de accesibilidad: es quien "toca" la pantalla por la persona.
 * Solo actúa cuando TikTok está en primer plano.
 */
class GestureService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: GestureService? = null
            private set

        /** TikTok global, TikTok en Asia y TikTok Lite/Go. */
        val TIKTOK_PACKAGES = listOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically.go"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No necesitamos reaccionar a eventos; solo ejecutar gestos cuando nos lo piden.
    }

    override fun onInterrupt() {}

    fun isTikTokInForeground(): Boolean {
        val pkg = rootInActiveWindow?.packageName?.toString() ?: return false
        return pkg in TIKTOK_PACKAGES
    }

    fun launchTikTok(): Boolean {
        val pm = packageManager
        val pkg = TIKTOK_PACKAGES.firstOrNull { pm.getLaunchIntentForPackage(it) != null }
            ?: return false
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Desliza hacia arriba (siguiente video) o hacia abajo (video anterior). */
    fun swipeVideo(next: Boolean): Boolean {
        val m = resources.displayMetrics
        val x = m.widthPixels / 2f
        val fromY = m.heightPixels * if (next) 0.75f else 0.30f
        val toY = m.heightPixels * if (next) 0.30f else 0.75f
        val path = Path().apply {
            moveTo(x, fromY)
            lineTo(x, toY)
        }
        return dispatch(GestureDescription.StrokeDescription(path, 0, 220))
    }

    /** Un toque en el centro: pausa o reanuda el video. */
    fun tapCenter(): Boolean = dispatch(tapStroke(0))

    /** Doble toque en el centro: da "me gusta". */
    fun doubleTapCenter(): Boolean = dispatch(tapStroke(0), tapStroke(130))

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    /** Busca el botón de comentarios por su descripción y lo presiona. */
    fun openComments(): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findClickable(root, 0) {
            it.contains("comment", ignoreCase = true) ||
                it.contains("comentario", ignoreCase = true)
        } ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun tapStroke(startMs: Long): GestureDescription.StrokeDescription {
        val m = resources.displayMetrics
        val path = Path().apply { moveTo(m.widthPixels / 2f, m.heightPixels * 0.45f) }
        return GestureDescription.StrokeDescription(path, startMs, 40)
    }

    private fun dispatch(vararg strokes: GestureDescription.StrokeDescription): Boolean {
        val builder = GestureDescription.Builder()
        strokes.forEach { builder.addStroke(it) }
        return dispatchGesture(builder.build(), null, null)
    }

    private fun findClickable(
        node: AccessibilityNodeInfo?,
        depth: Int,
        match: (String) -> Boolean
    ): AccessibilityNodeInfo? {
        if (node == null || depth > 25) return null
        val label = "${node.contentDescription ?: ""} ${node.text ?: ""}".trim()
        if (label.isNotEmpty() && match(label)) {
            var candidate: AccessibilityNodeInfo? = node
            var steps = 0
            while (candidate != null && !candidate.isClickable && steps < 4) {
                candidate = candidate.parent
                steps++
            }
            if (candidate != null && candidate.isClickable) return candidate
        }
        for (i in 0 until node.childCount) {
            val found = findClickable(node.getChild(i), depth + 1, match)
            if (found != null) return found
        }
        return null
    }
}
