package it.giacomo.tachimetro.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** Alba e tramonto calcolati sul telefono, senza internet. */
object Sun {
    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun norm(v: Double, m: Double) = ((v % m) + m) % m

    /** Ora UTC (in ore) di alba o tramonto; null se il sole non sorge o non tramonta. */
    private fun event(day: Int, lat: Double, lng: Double, rising: Boolean): Double? {
        val lngHour = lng / 15.0
        val t = day + ((if (rising) 6.0 else 18.0) - lngHour) / 24.0
        val m = 0.9856 * t - 3.289
        val l = norm(m + 1.916 * sin(rad(m)) + 0.020 * sin(rad(2 * m)) + 282.634, 360.0)
        var ra = norm(deg(atan(0.91764 * tan(rad(l)))), 360.0)
        ra += floor(l / 90.0) * 90.0 - floor(ra / 90.0) * 90.0
        ra /= 15.0
        val sinDec = 0.39782 * sin(rad(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(rad(90.833)) - sinDec * sin(rad(lat))) / (cosDec * cos(rad(lat)))
        if (cosH > 1 || cosH < -1) return null
        val h = (if (rising) 360.0 - deg(acos(cosH)) else deg(acos(cosH))) / 15.0
        val tt = h + ra - 0.06571 * t - 6.622
        return norm(tt - lngHour, 24.0)
    }

    fun isNight(lat: Double, lng: Double, now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)): Boolean {
        val u = now.withZoneSameInstant(ZoneOffset.UTC)
        val day = u.dayOfYear
        val rise = event(day, lat, lng, true)
        val set = event(day, lat, lng, false)
        val h = u.hour + u.minute / 60.0
        if (rise == null || set == null) return u.monthValue in listOf(11, 12, 1)
        return if (rise < set) h < rise || h > set else h > set && h < rise
    }
}

/**
 * Tema automatico: notte dal tramonto all'alba e quando il sensore di luce rileva poca luce (per esempio in galleria).
 */
@Composable
fun rememberAutoNight(lat: Double?, lng: Double?): Boolean {
    val context = LocalContext.current
    var dark by remember { mutableStateOf(false) }
    var sunNight by remember { mutableStateOf(isNightByClock()) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
        var lowSince = 0L
        var highSince = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val lux = event.values.firstOrNull() ?: return
                val now = System.currentTimeMillis()
                if (lux < 8f) {
                    highSince = 0L
                    if (lowSince == 0L) lowSince = now
                    if (now - lowSince > 4000) dark = true
                } else if (lux > 40f) {
                    lowSince = 0L
                    if (highSince == 0L) highSince = now
                    if (now - highSince > 4000) dark = false
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm.unregisterListener(listener) }
    }

    LaunchedEffect(lat, lng) {
        while (true) {
            sunNight = if (lat != null && lng != null) Sun.isNight(lat, lng) else isNightByClock()
            delay(60_000)
        }
    }
    return sunNight || dark
}

private fun isNightByClock(): Boolean {
    val h = java.time.LocalTime.now().hour
    return h >= 19 || h < 7
}
