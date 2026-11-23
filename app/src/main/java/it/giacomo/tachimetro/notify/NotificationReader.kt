package it.giacomo.tachimetro.notify

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import it.giacomo.tachimetro.Hub
import it.giacomo.tachimetro.IncomingMessage
import it.giacomo.tachimetro.media.MediaSessions

/**
 * Riceve le notifiche di WhatsApp, SMS e Teams per leggerle ad alta voce.
 * Il contenuto resta solo in memoria finché serve: non viene scritto su disco né inviato altrove.
 */
class NotificationReader : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        MediaSessions.start(applicationContext)
    }

    override fun onListenerDisconnected() {
        connected = false
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = appName(sbn.packageName) ?: return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras
        val sender = (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString()?.trim().orEmpty()
        val text = lastMessageText(extras) ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (sender.isBlank() || text.isBlank()) return
        // riepiloghi tipo «3 nuovi messaggi»
        if (Regex("^\\d+ (nuovi )?messaggi").containsMatchIn(text.lowercase())) return

        var replyIntent: android.app.PendingIntent? = null
        var remote: RemoteInput? = null
        for (a in n.actions.orEmpty()) {
            val ri = a.remoteInputs?.firstOrNull { it.allowFreeFormInput }
            if (ri != null && replyIntent == null) {
                replyIntent = a.actionIntent
                remote = ri
            }
        }
        Hub.addMessage(
            IncomingMessage(
                key = sbn.key + "|" + text.hashCode(),
                app = app,
                sender = sender,
                text = text,
                time = sbn.postTime,
                replyIntent = replyIntent,
                remoteInput = remote,
            ),
        )
    }

    private fun lastMessageText(extras: Bundle): String? {
        @Suppress("DEPRECATION")
        val msgs = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        val last = msgs.lastOrNull() as? Bundle ?: return null
        return last.getCharSequence("text")?.toString()?.trim()
    }

    companion object {
        @Volatile
        var connected = false

        fun appName(pkg: String): String? = when (pkg) {
            "com.whatsapp", "com.whatsapp.w4b" -> "WhatsApp"
            "com.microsoft.teams" -> "Teams"
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
            "com.hihonor.mms", "com.android.messaging" -> "SMS"
            else -> null
        }

        /** Invia una risposta dettata usando il pulsante «Rispondi» della notifica originale. */
        fun reply(context: Context, msg: IncomingMessage, text: String): Boolean {
            val pi = msg.replyIntent ?: return false
            val ri = msg.remoteInput ?: return false
            val intent = Intent()
            val results = Bundle().apply { putCharSequence(ri.resultKey, text) }
            RemoteInput.addResultsToIntent(arrayOf(ri), intent, results)
            return try {
                pi.send(context, 0, intent)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
