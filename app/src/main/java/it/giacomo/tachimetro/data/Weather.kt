package it.giacomo.tachimetro.data

import it.giacomo.tachimetro.WeatherNow
import java.util.Locale

/** Meteo e temperatura esterna dal servizio gratuito Open-Meteo, per la zona in cui ti trovi. */
object Weather {
    suspend fun fetch(lat: Double, lng: Double): WeatherNow {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&timezone=auto",
            lat, lng,
        )
        val cur = Net.getJsonObject(url).getJSONObject("current")
        val code = cur.optInt("weather_code", 0)
        return WeatherNow(cur.optDouble("temperature_2m", 0.0), describe(code), code)
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Sereno"
        1, 2 -> "Poco nuvoloso"
        3 -> "Nuvoloso"
        45, 48 -> "Nebbia"
        51, 53, 55, 56, 57 -> "Pioviggine"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Pioggia"
        71, 73, 75, 77, 85, 86 -> "Neve"
        95, 96, 99 -> "Temporale"
        else -> "Variabile"
    }
}
