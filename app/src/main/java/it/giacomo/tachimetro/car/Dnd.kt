package it.giacomo.tachimetro.car

import android.app.NotificationManager
import android.content.Context

/**
 * «Non disturbare» mentre guidi: il telefono non suona né vibra per notifiche e messaggi
 * (li annuncia Tachimetro), le chiamate e gli allarmi passano. Alla chiusura torna tutto come prima.
 */
object Dnd {
    private fun prefs(c: Context) = c.getSharedPreferences("non_disturbare", Context.MODE_PRIVATE)
    private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java)

    fun hasAccess(c: Context): Boolean = nm(c)?.isNotificationPolicyAccessGranted == true

    fun isApplied(c: Context): Boolean = prefs(c).getBoolean("applied", false)

    fun enable(c: Context) {
        val nm = nm(c) ?: return
        if (!nm.isNotificationPolicyAccessGranted || isApplied(c)) return
        try {
            val prev = nm.notificationPolicy
            prefs(c).edit()
                .putBoolean("applied", true)
                .putInt("filter", nm.currentInterruptionFilter)
                .putInt("cat", prev.priorityCategories)
                .putInt("calls", prev.priorityCallSenders)
                .putInt("msgs", prev.priorityMessageSenders)
                .putInt("vis", prev.suppressedVisualEffects)
                .commit()
            val cats = NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS or
                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or
                NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA or
                NotificationManager.Policy.PRIORITY_CATEGORY_SYSTEM
            nm.notificationPolicy = NotificationManager.Policy(
                cats,
                NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK,
            )
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        } catch (e: Exception) {
        }
    }

    fun restore(c: Context) {
        val p = prefs(c)
        if (!p.getBoolean("applied", false)) return
        val nm = nm(c)
        try {
            if (nm != null && nm.isNotificationPolicyAccessGranted) {
                nm.notificationPolicy = NotificationManager.Policy(
                    p.getInt("cat", 0), p.getInt("calls", 0), p.getInt("msgs", 0), p.getInt("vis", 0),
                )
                nm.setInterruptionFilter(p.getInt("filter", NotificationManager.INTERRUPTION_FILTER_ALL))
            }
        } catch (e: Exception) {
        }
        p.edit().putBoolean("applied", false).commit()
    }
}
