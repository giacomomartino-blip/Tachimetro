package it.giacomo.tachimetro.notify

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import it.giacomo.tachimetro.MainActivity

/**
 * Aiuto facoltativo per inviare messaggi WhatsApp a voce.
 * Tachimetro apre la chat con il testo già scritto; questo servizio preme «Invia» e torna a Tachimetro.
 * Agisce solo dentro WhatsApp e solo per pochi secondi dopo che hai confermato l'invio a voce.
 */
class WhatsAppSender : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (System.currentTimeMillis() > armedUntil) return
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return
        val root = rootInActiveWindow ?: return
        val button = findSend(root, pkg) ?: return
        if (button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            armedUntil = 0L
            lastResult = true
            main.postDelayed({ backToTachimetro() }, 900)
        }
    }

    private fun findSend(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        root.findAccessibilityNodeInfosByViewId("$pkg:id/send").firstOrNull { it.isEnabled }?.let { return clickable(it) }
        for (label in listOf("Invia", "Send")) {
            root.findAccessibilityNodeInfosByText(label)
                .firstOrNull { it.contentDescription?.toString().equals(label, true) && it.isEnabled }
                ?.let { return clickable(it) }
        }
        return null
    }

    private fun clickable(n: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var cur: AccessibilityNodeInfo? = n
        while (cur != null && !cur.isClickable) cur = cur.parent
        return cur ?: n
    }

    private fun backToTachimetro() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        } catch (e: Exception) {
        }
    }

    companion object {
        @Volatile
        private var instance: WhatsAppSender? = null

        @Volatile
        private var armedUntil = 0L

        @Volatile
        var lastResult = false

        /** Vero se hai attivato l'aiuto nelle impostazioni di accessibilità del telefono. */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, WhatsAppSender::class.java).flattenToString()
            val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return list.split(':').any { it.equals(me, true) }
        }

        /** Apre la chat WhatsApp con il testo scritto e, se l'aiuto è attivo, preme Invia da solo. */
        fun open(context: Context, phone: String, text: String, autoSend: Boolean): Boolean {
            val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull {
                try {
                    context.packageManager.getPackageInfo(it, 0)
                    true
                } catch (e: Exception) {
                    false
                }
            } ?: return false
            lastResult = false
            armedUntil = if (autoSend) System.currentTimeMillis() + 10_000 else 0L
            return try {
                val uri = Uri.parse("https://wa.me/$phone?text=" + Uri.encode(text))
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (e: Exception) {
                armedUntil = 0L
                false
            }
        }

        fun openSettings(context: Context) {
            try {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
            }
        }
    }
}
