package it.giacomo.tachimetro.data

import android.location.Location
import java.util.Locale

/** Un posto proposto a voce: ristorante, ufficio postale, farmacia… */
data class Place(
    val name: String,
    val address: String,
    val lat: Double?,
    val lng: Double?,
    val note: String? = null,
    val distanceM: Int? = null,
)

/**
 * Ricerca dei posti vicini. Con la chiave di Gemini usa la ricerca Google (recensioni, «il migliore»);
 * senza, o se Gemini non la concede, usa la mappa libera OpenStreetMap e propone i più vicini.
 */
object Places {
    private data class Cat(val words: List<String>, val filter: String, val label: String)

    private val CUISINES = listOf(
        listOf("cinese") to "chinese",
        listOf("giapponese", "sushi") to "japanese|sushi",
        listOf("indiano", "indiana") to "indian",
        listOf("messicano", "messicana") to "mexican",
        listOf("thailandese", "thai") to "thai",
        listOf("pesce") to "seafood|fish",
        listOf("kebab") to "kebab",
        listOf("hamburger", "burger") to "burger",
        listOf("vegano", "vegana", "vegetariano", "vegetariana") to "vegan|vegetarian",
        listOf("romano", "romana", "tipico", "tipica", "italiano", "italiana") to "italian|regional",
        listOf("greco", "greca") to "greek",
        listOf("coreano", "coreana") to "korean",
    )

    private val CATEGORIES = listOf(
        Cat(listOf("ufficio postale", "uffici postali", "poste", "posta"), "[\"amenity\"=\"post_office\"]", "ufficio postale"),
        Cat(listOf("farmacia", "farmacie"), "[\"amenity\"=\"pharmacy\"]", "farmacia"),
        Cat(listOf("pronto soccorso", "ospedale"), "[\"amenity\"=\"hospital\"]", "ospedale"),
        Cat(listOf("bancomat", "sportello"), "[\"amenity\"=\"atm\"]", "bancomat"),
        Cat(listOf("banca", "banche"), "[\"amenity\"=\"bank\"]", "banca"),
        Cat(listOf("supermercato", "supermercati", "alimentari"), "[\"shop\"~\"supermarket|convenience\"]", "supermercato"),
        Cat(listOf("pizzeria", "pizzerie", "pizza"), "[\"amenity\"=\"restaurant\"][\"cuisine\"~\"pizza\",i]", "pizzeria"),
        Cat(listOf("gelateria", "gelato"), "[\"amenity\"=\"ice_cream\"]", "gelateria"),
        Cat(listOf("fast food", "mcdonald"), "[\"amenity\"=\"fast_food\"]", "fast food"),
        Cat(listOf("ristorante", "ristoranti", "trattoria", "osteria", "mangiare"), "[\"amenity\"=\"restaurant\"]", "ristorante"),
        Cat(listOf("bar", "caffè", "caffe", "colazione"), "[\"amenity\"~\"cafe|bar\"]", "bar"),
        Cat(listOf("panetteria", "forno", "pasticceria"), "[\"shop\"~\"bakery|pastry\"]", "forno"),
        Cat(listOf("hotel", "albergo", "alberghi", "b&b", "bed and breakfast"), "[\"tourism\"~\"hotel|guest_house\"]", "hotel"),
        Cat(listOf("museo", "musei"), "[\"tourism\"=\"museum\"]", "museo"),
        Cat(listOf("stazione"), "[\"railway\"=\"station\"]", "stazione"),
        Cat(listOf("carabinieri", "polizia", "questura"), "[\"amenity\"=\"police\"]", "forze dell'ordine"),
        Cat(listOf("officina", "meccanico", "elettrauto"), "[\"shop\"=\"car_repair\"]", "officina"),
        Cat(listOf("gommista"), "[\"shop\"=\"tyres\"]", "gommista"),
        Cat(listOf("colonnina", "ricarica"), "[\"amenity\"=\"charging_station\"]", "colonnina di ricarica"),
        Cat(listOf("tabaccheria", "tabacchi", "tabaccaio"), "[\"shop\"=\"tobacco\"]", "tabaccheria"),
        Cat(listOf("autolavaggio"), "[\"amenity\"=\"car_wash\"]", "autolavaggio"),
        Cat(listOf("chiesa"), "[\"amenity\"=\"place_of_worship\"]", "chiesa"),
        Cat(listOf("parcheggio", "parcheggi"), "[\"amenity\"=\"parking\"]", "parcheggio"),
        Cat(listOf("negozio di scarpe", "scarpe", "calzature"), "[\"shop\"=\"shoes\"]", "negozio di scarpe"),
        Cat(listOf("abbigliamento", "vestiti", "negozio di vestiti"), "[\"shop\"~\"clothes|fashion\"]", "abbigliamento"),
        Cat(listOf("elettronica", "informatica", "computer"), "[\"shop\"~\"electronics|computer|mobile_phone\"]", "elettronica"),
        Cat(listOf("telefonia", "negozio di telefoni"), "[\"shop\"=\"mobile_phone\"]", "telefonia"),
        Cat(listOf("ferramenta"), "[\"shop\"~\"hardware|doityourself\"]", "ferramenta"),
        Cat(listOf("bricolage", "fai da te"), "[\"shop\"=\"doityourself\"]", "bricolage"),
        Cat(listOf("ottico", "ottica", "occhiali"), "[\"shop\"=\"optician\"]", "ottico"),
        Cat(listOf("parrucchiere", "parrucchiera"), "[\"shop\"=\"hairdresser\"]", "parrucchiere"),
        Cat(listOf("barbiere"), "[\"shop\"=\"hairdresser\"]", "barbiere"),
        Cat(listOf("estetista", "centro estetico"), "[\"shop\"=\"beauty\"]", "estetista"),
        Cat(listOf("lavanderia", "tintoria"), "[\"shop\"~\"laundry|dry_cleaning\"]", "lavanderia"),
        Cat(listOf("libreria", "libri"), "[\"shop\"=\"books\"]", "libreria"),
        Cat(listOf("fioraio", "fiori"), "[\"shop\"=\"florist\"]", "fioraio"),
        Cat(listOf("edicola", "giornalaio"), "[\"shop\"~\"newsagent|kiosk\"]", "edicola"),
        Cat(listOf("profumeria"), "[\"shop\"=\"perfumery\"]", "profumeria"),
        Cat(listOf("gioielleria", "orologeria"), "[\"shop\"~\"jewelry|watches\"]", "gioielleria"),
        Cat(listOf("giocattoli"), "[\"shop\"=\"toys\"]", "giocattoli"),
        Cat(listOf("macelleria"), "[\"shop\"=\"butcher\"]", "macelleria"),
        Cat(listOf("pescheria"), "[\"shop\"=\"seafood\"]", "pescheria"),
        Cat(listOf("fruttivendolo", "frutta e verdura"), "[\"shop\"=\"greengrocer\"]", "fruttivendolo"),
        Cat(listOf("enoteca", "vini"), "[\"shop\"=\"wine\"]", "enoteca"),
        Cat(listOf("centro commerciale"), "[\"shop\"=\"mall\"]", "centro commerciale"),
        Cat(listOf("negozio di animali", "animali"), "[\"shop\"=\"pet\"]", "negozio di animali"),
        Cat(listOf("veterinario"), "[\"amenity\"=\"veterinary\"]", "veterinario"),
        Cat(listOf("dentista"), "[\"amenity\"=\"dentist\"]", "dentista"),
        Cat(listOf("medico", "guardia medica", "ambulatorio"), "[\"amenity\"~\"doctors|clinic\"]", "medico"),
        Cat(listOf("palestra"), "[\"leisure\"=\"fitness_centre\"]", "palestra"),
        Cat(listOf("piscina"), "[\"leisure\"=\"swimming_pool\"]", "piscina"),
        Cat(listOf("parco", "giardini"), "[\"leisure\"=\"park\"]", "parco"),
        Cat(listOf("cinema"), "[\"amenity\"=\"cinema\"]", "cinema"),
        Cat(listOf("teatro"), "[\"amenity\"=\"theatre\"]", "teatro"),
        Cat(listOf("spiaggia", "lido", "stabilimento balneare"), "[\"natural\"=\"beach\"]", "spiaggia"),
        Cat(listOf("bagno pubblico", "toilette", "bagni"), "[\"amenity\"=\"toilets\"]", "bagno pubblico"),
        Cat(listOf("area di servizio", "autogrill"), "[\"highway\"=\"services\"]", "area di servizio"),
        Cat(listOf("ricarica auto"), "[\"amenity\"=\"charging_station\"]", "ricarica auto"),
        Cat(listOf("noleggio auto", "autonoleggio"), "[\"amenity\"=\"car_rental\"]", "noleggio auto"),
        Cat(listOf("concessionaria", "concessionario"), "[\"shop\"=\"car\"]", "concessionaria"),
        Cat(listOf("comune", "municipio"), "[\"amenity\"=\"townhall\"]", "comune"),
        Cat(listOf("scuola"), "[\"amenity\"=\"school\"]", "scuola"),
        Cat(listOf("università"), "[\"amenity\"=\"university\"]", "università"),
        Cat(listOf("biblioteca"), "[\"amenity\"=\"library\"]", "biblioteca"),
        Cat(listOf("aeroporto"), "[\"aeroway\"=\"aerodrome\"]", "aeroporto"),
        Cat(listOf("metro", "metropolitana"), "[\"railway\"=\"station\"][\"station\"=\"subway\"]", "metro"),
        Cat(listOf("fermata dell'autobus", "fermata del bus", "fermata"), "[\"highway\"=\"bus_stop\"]", "fermata dell'autobus"),
        Cat(listOf("taxi"), "[\"amenity\"=\"taxi\"]", "taxi"),
        Cat(listOf("distributore", "benzinaio", "benzina"), "[\"amenity\"=\"fuel\"]", "distributore"),
    )

    /** Filtro della mappa libera per una richiesta a voce, se è un tipo di posto conosciuto. */
    fun filterFor(query: String): String? {
        val q = " " + query.lowercase(Locale.ITALIAN) + " "
        val cuisine = CUISINES.firstOrNull { (words, _) -> words.any { q.contains(" $it ") } }?.second
        val cat = CATEGORIES.firstOrNull { c -> c.words.any { q.contains(" $it ") || q.contains(" $it,") } }
            ?: return cuisine?.let { "[\"amenity\"~\"restaurant|fast_food\"][\"cuisine\"~\"$it\",i]" }
        return when {
            cuisine != null && cat.filter.contains("restaurant") && !cat.filter.contains("pizza") ->
                "[\"amenity\"~\"restaurant|fast_food\"][\"cuisine\"~\"$cuisine\",i]"
            else -> cat.filter
        }
    }

    /** Vero se la frase nomina un tipo di posto («ufficio postale in via Marmorata»). */
    fun isCategory(query: String): Boolean = filterFor(query) != null

    /** Posti più vicini a un punto, dalla mappa libera OpenStreetMap. */
    suspend fun nearest(filter: String, lat: Double, lng: Double, max: Int = 3): List<Place> {
        for (radius in listOf(1500, 5000)) {
            val q = String.format(Locale.US, "[out:json][timeout:20];nwr(around:%d,%.5f,%.5f)%s;out center tags 60;", radius, lat, lng, filter)
            val els = Net.getJsonObject("https://overpass-api.de/api/interpreter?data=" + Net.enc(q)).getJSONArray("elements")
            val out = ArrayList<Place>()
            for (i in 0 until els.length()) {
                val e = els.getJSONObject(i)
                val c = e.optJSONObject("center")
                val plat = c?.optDouble("lat") ?: e.optDouble("lat", Double.NaN)
                val plng = c?.optDouble("lon") ?: e.optDouble("lon", Double.NaN)
                if (plat.isNaN() || plng.isNaN()) continue
                val tags = e.optJSONObject("tags")
                val d = FloatArray(1)
                Location.distanceBetween(lat, lng, plat, plng, d)
                val street = tags?.optString("addr:street").orEmpty()
                val num = tags?.optString("addr:housenumber").orEmpty()
                val name = tags?.optString("name").orEmpty().ifBlank { tags?.optString("brand").orEmpty() }
                out += Place(
                    name = name.ifBlank { "Senza nome" },
                    address = listOf(street, num).filter { it.isNotBlank() }.joinToString(" "),
                    lat = plat,
                    lng = plng,
                    distanceM = d[0].toInt(),
                )
            }
            // prima quelli con un nome, poi i più vicini
            val sorted = out.sortedWith(compareBy<Place>({ it.name == "Senza nome" }, { it.distanceM }))
                .distinctBy { it.name.lowercase() + it.address.lowercase() }
            if (sorted.isNotEmpty()) return sorted.take(max)
        }
        return emptyList()
    }

    /** Filtro per nome, per i posti che non rientrano in nessun tipo conosciuto («Ikea», «Decathlon»). */
    fun nameFilter(query: String): String {
        val words = query.lowercase(Locale.ITALIAN)
            .replace(Regex("^(negozio|negozi|un|una|il|la|lo|di|del|della)\\s+"), "")
            .replace(Regex("[^a-z0-9àèéìòù' ]"), " ").trim()
        val pattern = words.split(" ").filter { it.length >= 3 }.joinToString(".*").ifBlank { words }
        return "[\"name\"~\"$pattern\",i]"
    }

    /** Distanza detta a voce: «300 metri», «1,2 chilometri». */
    fun spokenDistance(m: Int): String =
        if (m < 1000) "${(m / 50).coerceAtLeast(1) * 50} metri" else String.format(Locale.ITALIAN, "%.1f chilometri", m / 1000.0)
}
