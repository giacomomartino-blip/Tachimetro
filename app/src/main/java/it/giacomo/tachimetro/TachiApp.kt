package it.giacomo.tachimetro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class TachiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Ascolto di «Ehi Tachi»", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Avviso fisso mentre Tachimetro ascolta la parola chiave"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CAR, "Auto e parcheggio", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Parcheggio salvato, strisce blu, apertura all'avvio dell'auto"
            },
        )
    }

    companion object {
        const val CH_SERVICE = "servizio"
        const val CH_CAR = "auto"
    }
}
