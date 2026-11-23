package it.giacomo.tachimetro.notify

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import it.giacomo.tachimetro.Hub
import it.giacomo.tachimetro.IncomingCall
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
        instance = this
        MediaSessions.start(applicationContext)
    }

    override fun onListenerDisconnected() {
        connected = false
        instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (n.category == Notification.CATEGORY_CALL) {
            onCall(sbn)
            return
        }
        val app = appName(sbn.packageName) ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val convTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
        val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) || !convTitle.isNullOrBlank()
        // nei gruppi il mittente è nell'ultimo messaggio, oppure nel titolo «Gruppo: Nome»
        val sender = lastMessageSender(extras)
            ?: title.substringAfterLast(": ").ifBlank { title }
        val group = if (isGroup) {
            (convTitle ?: title.substringBefore(": ")).replace(Regex("\\s*\\(\\d+ messaggi[^)]*\\)"), "").trim().takeIf { it.isNotBlank() && it != sender }
        } else {
            null
        }
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
                group = group,
                text = text,
                time = sbn.postTime,
                replyIntent = replyIntent,
                remoteInput = remote,
            ),
        )
    }

    /** Chiamata in arrivo: il nome lo prende dalla notifica del telefono. */
    private fun onCall(sbn: StatusBarNotification) {
        val n = sbn.notification
        val extras = n.extras
        val type = extras.getInt(Notification.EXTRA_CALL_TYPE, 0)
        val ringing = type == Notification.CallStyle.CALL_TYPE_INCOMING || isRinging()
        if (!ringing) return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val caller = (if (title.isBlank() || title.contains("chiamata", true) || title.contains("call", true)) text else title)
            .ifBlank { "numero sconosciuto" }
        if (Hub.incomingCall.value?.key == sbn.key) return
        Hub.incomingCall.value = IncomingCall(sbn.key, caller, System.currentTimeMillis())
    }

    private fun isRinging(): Boolean {
        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) return false
        return try {
            @Suppress("DEPRECATION")
            getSystemService(android.telephony.TelephonyManager::class.java)?.callState == android.telephony.TelephonyManager.CALL_STATE_RINGING
        } catch (e: Exception) {
            false
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (Hub.incomingCall.value?.key == sbn.key) Hub.incomingCall.value = null
    }

    private fun lastMessageSender(extras: Bundle): String? {
        @Suppress("DEPRECATION")
        val msgs = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        val last = msgs.lastOrNull() as? Bundle ?: return null
        @Suppress("DEPRECATION")
        val person = last.getParcelable<android.app.Person>("sender_person")
        val name = person?.name?.toString() ?: last.getCharSequence("sender")?.toString()
        return name?.trim()?.takeIf { it.isNotBlank() }
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

        @Volatile
        private var instance: NotificationReader? = null

        /** Ricollega la lettura delle notifiche quando apri Tachimetro. */
        fun rebind(context: Context) {
            try {
                requestRebind(android.content.ComponentName(context, NotificationReader::class.java))
            } catch (e: Exception) {
            }
        }

        /** Scollega la lettura delle notifiche quando chiudi Tachimetro: nessun processo resta attivo. */
        fun unbind() {
            try {
                instance?.requestUnbind()
            } catch (e: Exception) {
            }
            Hub.messages.value = emptyList()
        }

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
