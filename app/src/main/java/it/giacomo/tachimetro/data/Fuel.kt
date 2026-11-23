package it.giacomo.tachimetro.data

import android.location.Location

data class FuelStation(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val fuel: String,
    val price: Double,
    val self: Boolean,
    val distanceKm: Double,
)

/**
 * Prezzi ufficiali dei carburanti pubblicati ogni giorno dal Ministero delle Imprese e del Made in Italy.
 * I file usano la barra verticale come separatore dal 10 febbraio 2026; per sicurezza si accetta anche il punto e virgola.
 * Colonne anagrafica: idImpianto, Gestore, Bandiera, Tipo, Nome, Indirizzo, Comune, Provincia, Latitudine, Longitudine.
 * Colonne prezzi: idImpianto, descCarburante, prezzo, isSelf, dtComu.
 */
object Fuel {
    private val BASES = listOf("https://www.mimit.gov.it/images/exportCSV/", "https://www.mise.gov.it/images/exportCSV/")

    private data class Plant(val name: String, val address: String, val lat: Double, val lng: Double)

    private var cacheDay = ""
    private var cache: List<FuelStation> = emptyList()
    private var cacheCenter: Location? = null

    private fun split(line: String): List<String> = if (line.contains('|')) line.split('|') else line.split(';')

    private suspend fun lines(file: String, onLine: (String) -> Unit) {
        var last: Exception? = null
        for (b in BASES) {
            try {
                Net.forEachLine(b + file, onLine = onLine)
                return
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("download non riuscito")
    }

    /** fuelType: "Benzina", "Gasolio", "GPL", "Metano". Restituisce i distributori entro radiusKm, dal più economico. */
    suspend fun nearby(here: Location, fuelType: String, radiusKm: Double = 10.0): List<FuelStation> {
        val day = java.time.LocalDate.now().toString()
        val center = cacheCenter
        if (day != cacheDay || center == null || center.distanceTo(here) > 2000 || cache.isEmpty()) {
            cache = download(here, radiusKm)
            cacheDay = day
            cacheCenter = here
        }
        return filter(cache, fuelType, here)
    }

    private suspend fun download(here: Location, radiusKm: Double): List<FuelStation> {
        val plants = HashMap<String, Plant>()
        val tmp = FloatArray(1)
        lines("anagrafica_impianti_attivi.csv") { line ->
            val f = split(line)
            if (f.size < 10 || f[0].isEmpty() || !f[0][0].isDigit()) return@lines
            val lat = f[8].trim().replace(',', '.').toDoubleOrNull() ?: return@lines
            val lng = f[9].trim().replace(',', '.').toDoubleOrNull() ?: return@lines
            Location.distanceBetween(here.latitude, here.longitude, lat, lng, tmp)
            if (tmp[0] / 1000.0 > radiusKm) return@lines
            val name = f[4].trim().ifBlank { f[2].trim() }
            plants[f[0].trim()] = Plant(name, "${f[5].trim()}, ${f[6].trim()}", lat, lng)
        }
        val out = ArrayList<FuelStation>()
        lines("prezzo_alle_8.csv") { line ->
            val f = split(line)
            if (f.size < 4 || f[0].isEmpty() || !f[0][0].isDigit()) return@lines
            val id = f[0].trim()
            val p = plants[id] ?: return@lines
            val price = f[2].trim().replace(',', '.').toDoubleOrNull() ?: return@lines
            out += FuelStation(id, p.name, p.address, p.lat, p.lng, f[1].trim(), price, f[3].trim() == "1", 0.0)
        }
        return out
    }

    private fun filter(all: List<FuelStation>, fuelType: String, here: Location): List<FuelStation> {
        val wanted = fuelType.lowercase()
        val tmp = FloatArray(1)
        return all
            .filter { it.fuel.lowercase().startsWith(wanted) && (it.self || wanted == "gpl" || wanted == "metano") }
            .groupBy { it.id }
            .map { (_, v) -> v.minBy { it.price } }
            .map {
                Location.distanceBetween(here.latitude, here.longitude, it.lat, it.lng, tmp)
                it.copy(distanceKm = tmp[0] / 1000.0)
            }
            .sortedWith(compareBy({ it.price }, { it.distanceKm }))
            .take(15)
    }
}
