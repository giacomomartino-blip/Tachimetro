package it.giacomo.tachimetro.data

import android.location.Location
import java.util.Locale
import kotlin.math.abs

data class SpeedCamera(val id: Long, val lat: Double, val lng: Double, val maxSpeed: Int?)

/**
 * Autovelox fissi dalla mappa collaborativa OpenStreetMap (servizio Overpass), per la zona attorno a te.
 * L'elenco ufficiale del MIT e il calendario dei controlli mobili della Polizia Stradale arrivano in una versione successiva.
 */
object SpeedCameras {
    private var cams: List<SpeedCamera> = emptyList()
    private var loadedAt: Location? = null
    private var loadedTime = 0L
    private val warned = HashSet<Long>()

    suspend fun refreshIfNeeded(here: Location) {
        val l = loadedAt
        val stale = System.currentTimeMillis() - loadedTime > 6 * 3600_000L
        if (l != null && l.distanceTo(here) < 6000 && !stale) return
        val q = String.format(
            Locale.US,
            "[out:json][timeout:20];node[highway=speed_camera](around:12000,%.5f,%.5f);out;",
            here.latitude, here.longitude,
        )
        val json = Net.getJsonObject("https://overpass-api.de/api/interpreter?data=" + Net.enc(q))
        val els = json.optJSONArray("elements")
        val list = ArrayList<SpeedCamera>()
        if (els != null) {
            for (i in 0 until els.length()) {
                val e = els.getJSONObject(i)
                val tags = e.optJSONObject("tags")
                val max = tags?.optString("maxspeed")?.takeWhile { it.isDigit() }?.toIntOrNull()
                list += SpeedCamera(e.optLong("id"), e.optDouble("lat"), e.optDouble("lon"), max)
            }
        }
        cams = list
        loadedAt = here
        loadedTime = System.currentTimeMillis()
    }

    /** Restituisce l'autovelox più vicino davanti a te entro warnMeters, oppure null. */
    fun ahead(here: Location, warnMeters: Int): Pair<SpeedCamera, Int>? {
        if (!here.hasBearing() || here.speed < 5f) return null
        var best: Pair<SpeedCamera, Int>? = null
        val tmp = FloatArray(2)
        for (c in cams) {
            Location.distanceBetween(here.latitude, here.longitude, c.lat, c.lng, tmp)
            val d = tmp[0].toInt()
            if (d > warnMeters) continue
            var diff = abs(tmp[1] - here.bearing) % 360f
            if (diff > 180f) diff = 360f - diff
            if (diff > 40f) continue
            if (best == null || d < best.second) best = c to d
        }
        return best
    }

    /** true la prima volta che si avvisa per questo autovelox. */
    fun firstWarning(id: Long): Boolean {
        if (warned.size > 500) warned.clear()
        return warned.add(id)
    }
}
