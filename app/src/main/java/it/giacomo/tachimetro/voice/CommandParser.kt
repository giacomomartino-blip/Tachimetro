package it.giacomo.tachimetro.voice

import it.giacomo.tachimetro.data.Places

/** Cosa ha chiesto la persona, ricavato dalla frase detta dopo «Ehi Tachi». */
sealed interface Command {
    data class Navigate(val place: String, val waze: Boolean) : Command
    data object BackToMaps : Command
    data object StopNavigation : Command
    data class PlayMusic(val query: String, val app: String?) : Command
    data class PlayRadio(val station: String) : Command
    data object NextRadio : Command
    data object Pause : Command
    data object Resume : Command
    data object Next : Command
    data object Previous : Command
    data class Volume(val up: Boolean) : Command
    data class Call(val name: String, val whatsapp: Boolean = false) : Command
    data class SendMessage(val rest: String, val app: String?) : Command
    data object CallBack : Command
    data object HangUp : Command
    data object Eta : Command
    data object WhereAmI : Command
    data class Avoid(val highways: Boolean, val tolls: Boolean) : Command
    data object FindParking : Command
    data object SaveParking : Command
    data class Reminder(val minutes: Int) : Command
    data object CameraAhead : Command
    data class SetPref(val key: String, val value: String) : Command
    data object Time : Command
    data class WeatherAt(val place: String?) : Command
    data object Battery : Command
    data class ReadMessages(val app: String?, val onlyLast: Boolean) : Command
    data class SearchNearby(val query: String, val best: Boolean) : Command
    data object LocalRadio : Command
    data object Go : Command
    data object Reply : Command
    data object JoinMeeting : Command
    data class OpenApp(val name: String) : Command
    data class SearchVideo(val title: String, val app: String) : Command
    data class SetMode(val driver: Boolean) : Command
    data object WhereIsCar : Command
    data object FindFuel : Command
    data class AddToBar(val app: String) : Command
    data class RemoveFromBar(val app: String) : Command
    data object Dashboard : Command
    data object Settings : Command
    data object Mute : Command
    data object Cancel : Command
    data object CloseApp : Command
    data class AskGemini(val question: String) : Command
    data class Unknown(val heard: String) : Command
}

object CommandParser {
    private val MUSIC_APPS = mapOf(
        "spotify" to "spotify",
        "youtube music" to "ytmusic",
        "you tube music" to "ytmusic",
        "amazon music" to "amazon",
        "amazon" to "amazon",
    )

    fun normalize(text: String): String = text.lowercase()
        .replace("’", "'")
        .replace(Regex("[.,;:!?¿¡\"]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .replace(Regex("^(ehi|hey|hei|ei|e|ok)\\s+(tachi|tacchi|taxi|taki|ta chi)\\s*"), "")
        // come il riconoscimento vocale scrive a volte WhatsApp e SMS
        .replace(Regex("\\b(whats app|what's app|what s app|watsapp|wazap|wassap|whatsup|whatsapp's|uotsapp|uazzap)\\b"), "whatsapp")
        .replace(Regex("\\b(esse emme esse|s m s|esseemmeesse)\\b"), "sms")
        .replace(Regex("\\b(in streaming|via streaming|in diretta|online|on line)\\b"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Modi gentili o indiretti di chiedere: «mi puoi chiamare Luca» diventa «chiama Luca». */
    private val POLITE = Regex("^(per favore|per piacere|per cortesia|mi puoi|mi potresti|puoi|potresti|riesci a|ti chiedo di|vorrei che|fammi il piacere di)\\s+")
    private val INFINITIVE = mapOf(
        "chiamare" to "chiama", "telefonare" to "telefona", "mandare" to "manda", "inviare" to "invia",
        "scrivere" to "scrivi", "leggere" to "leggi", "leggermi" to "leggimi", "cercare" to "cerca", "cercarmi" to "cercami",
        "trovare" to "trova", "trovarmi" to "trovami", "mettere" to "metti", "portarmi" to "portami", "aprire" to "apri",
        "impostare" to "imposta", "impostarmi" to "impostami", "accendere" to "accendi", "spegnere" to "spegni",
        "rispondere" to "rispondi", "richiamare" to "richiama", "salvare" to "salva", "ricordarmi" to "ricordami",
    )

    private fun polish(t: String): String {
        var x = t
        repeat(2) { x = x.replace(POLITE, "") }
        x = x.replace(Regex("\\s+(per favore|per piacere|per cortesia|grazie)$"), "")
        val first = x.substringBefore(" ")
        INFINITIVE[first]?.let { x = it + x.removePrefix(first) }
        return x.trim()
    }

    /** Pezzi della parola chiave che a volte restano all'inizio del comando («chi metti la radio»). */
    private val RESIDUE = setOf("ehi", "hey", "hei", "ei", "e", "ok", "chi", "ki", "ci", "ta", "taki", "tachi", "tacchi", "taxi")

    private val QUESTION = Regex(
        "^(chi|cosa|che|come|quando|dove|quanto|quanti|quanta|quante|perché|perche|qual|quale|quali|dimmi|spiegami|sai|mi sai|raccontami|chiedi|cerca|trova)\\b",
    )

    private const val NEAR = "(nelle vicinanze|qui vicino|qua vicino|vicino a me|qui intorno|nei dintorni|nei paraggi|in zona|da queste parti|più vicin[oaie])"

    fun parse(raw: String): Command {
        val base = normalize(raw)
        if (base.isBlank()) return Command.Cancel
        // prima la frase così com'è, poi senza gli eventuali resti di «Ehi Tachi» all'inizio
        val words = base.split(" ")
        val candidates = mutableListOf(base)
        polish(base).let { if (it != base) candidates += it }
        var i = 0
        while (i < words.size - 1 && i < 3 && words[i] in RESIDUE) {
            i++
            val c = words.drop(i).joinToString(" ")
            candidates += c
            polish(c).let { if (it != c) candidates += it }
        }
        for (c in candidates) tryCommand(c)?.let { return it }
        Regex("^chiedi a gemini\\s+(.+)$").find(base)?.let { return Command.AskGemini(it.groupValues[1]) }
        // a Gemini vanno solo le domande vere; il resto è un comando non capito
        return if (raw.contains("?") || QUESTION.containsMatchIn(base)) Command.AskGemini(base) else Command.Unknown(base)
    }

    private fun tryCommand(t: String): Command? {
        if (t.isBlank()) return null

        // annullare
        if (t in setOf("annulla", "lascia stare", "niente", "no", "basta così")) return Command.Cancel

        // chiusura
        if (Regex("^(chiudi|spegni|esci da) (tachimetro|l'app|l app|tutto)|^(esci|spegniti|chiuditi)$").containsMatchIn(t)) return Command.CloseApp

        // schermate
        if (Regex("^(torna al |vai al |apri il )?cruscotto|^torna indietro|^home$").containsMatchIn(t)) return Command.Dashboard
        if (Regex("^(apri )?(le )?impostazioni").containsMatchIn(t)) return Command.Settings
        if (Regex("silenzia (gli )?avvisi").containsMatchIn(t)) return Command.Mute

        // navigazione: «imposta il percorso per Roma», «navigatore per via Toledo», «come arrivo a Caserta»
        Regex("^(imposta|impostami|avvia|calcola|fammi|apri)( il| un| la)? (percorso|navigatore|navigazione|tragitto|strada)( per| verso| fino a| a| al| alla| in)? (.+)$").find(t)?.let {
            return navigateTo(it.groupValues[5])
        }
        Regex("^(percorso|navigazione|navigatore|strada|indicazioni)( per| verso| a| al| alla| in)? (.+)$").find(t)?.let {
            return navigateTo(it.groupValues[3])
        }
        Regex("^come (arrivo|si arriva|vado|ci arrivo) (a|al|alla|allo|in|da) (.+)$").find(t)?.let {
            return navigateTo(it.groupValues[3])
        }
        if (Regex("^(ok |sì |si |va bene )?(partiamo|parti|si parte|andiamo|vai|avvia|avvia la navigazione|inizia la navigazione|avvia il percorso)$").matches(t)) return Command.Go
        if (Regex("^(portami|naviga|navigatore|navigazione|imposta (il |un )?percorso|imposta la navigazione|voglio andare|portami da qualche parte)$").matches(t)) return Command.Navigate("", false)
        if (Regex("^(torna a|usa) (google )?maps").containsMatchIn(t)) return Command.BackToMaps
        if (Regex("(ferma|termina|interrompi|chiudi) (la )?navigazione").containsMatchIn(t)) return Command.StopNavigation
        Regex("^(portami|guidami|naviga|vai|andiamo|voglio andare)\\s+(verso |a |al |alla |allo |ai |alle |all'|in |da |dal |dalla )?(.+)$").find(t)?.let { m ->
            var place = m.groupValues[3].trim()
            var waze = false
            Regex("\\s+(con|su|usando) waze$").find(place)?.let {
                waze = true
                place = place.removeRange(it.range).trim()
            }
            if (!waze && looksNearby(place)) return Command.SearchNearby(cleanPlaceQuery(place), place.contains("miglior"))
            return Command.Navigate(normalizePlace(m.groupValues[2], place), waze)
        }

        // auto e servizi
        if (Regex("dov'è (la )?(mia )?(macchina|auto)|dove ho parcheggiato|dov è la macchina|dove è la macchina").containsMatchIn(t)) return Command.WhereIsCar
        if (Regex("(trova|cerca|dov'è|dove).*(benzina|distribut|gasolio|diesel|carburante|gpl|metano)").containsMatchIn(t) ||
            Regex("^(benzina|distributor[ei])").containsMatchIn(t)
        ) return Command.FindFuel
        // meteo: «che tempo fa», «che tempo fa all'arrivo», «che tempo fa a Napoli»
        Regex("^(che tempo fa|com'è il tempo|com è il tempo|meteo|che temperatura c'è|che temperatura fa)( a destinazione| all'arrivo| all arrivo| dove vado| a | in )?(.*)$").find(t)?.let {
            val where = it.groupValues[2].trim()
            val place = it.groupValues[3].trim()
            return when {
                where.startsWith("a destinazione") || where.contains("arrivo") || where.contains("dove vado") -> Command.WeatherAt("@arrivo")
                place.isNotBlank() && (where == "a" || where == "in") -> Command.WeatherAt(place)
                else -> Command.WeatherAt(null)
            }
        }
        if (Regex("^(che ore sono|che ora è|che ora e|dimmi l'ora)").containsMatchIn(t)) return Command.Time
        if (Regex("(quanta batteria|batteria del telefono|livello della batteria)").containsMatchIn(t)) return Command.Battery

        // auto
        if (Regex("^(quanto manca|a che ora arrivo|quando arrivo|tra quanto arrivo|quanta strada manca)").containsMatchIn(t)) return Command.Eta
        if (Regex("^(dove sono|dove mi trovo|in che via sono|in che strada sono)").containsMatchIn(t)) return Command.WhereAmI
        if (Regex("evita (le )?autostrade").containsMatchIn(t) || Regex("evita (i )?pedaggi").containsMatchIn(t)) {
            return Command.Avoid(t.contains("autostrad"), t.contains("pedagg"))
        }
        if (Regex("^(cerca|trova|cercami|trovami) (un )?parcheggio").containsMatchIn(t)) return Command.FindParking
        if (Regex("^(salva|segna|ricorda) (il |dove ho )?parcheggi").containsMatchIn(t)) return Command.SaveParking
        Regex("^ricordami (le )?strisce( blu)? tra (.+)$").find(t)?.let { m ->
            minutes(m.groupValues[3])?.let { return Command.Reminder(it) }
        }
        if (Regex("(c'è|ci sono|ci sono degli|c è) (un |degli )?autovelox|^autovelox").containsMatchIn(t)) return Command.CameraAhead

        // impostazioni a voce
        Regex("^(attiva|accendi|disattiva|spegni|togli) (il )?(non disturbare|schermo intero|lettura (dei )?messaggi|avvisi (degli )?autovelox)").find(t)?.let { m ->
            val on = m.groupValues[1] in setOf("attiva", "accendi")
            val what = m.groupValues[3]
            val key = when {
                what.startsWith("non disturbare") -> "dnd"
                what.startsWith("schermo") -> "fullScreen"
                what.startsWith("lettura") -> "readMessages"
                else -> "cameras"
            }
            return Command.SetPref(key, on.toString())
        }
        Regex("^(tema|modalità) (notte|scuro|giorno|chiaro|automatico|automatica)$").find(t)?.let {
            val v = when (it.groupValues[2]) { "notte", "scuro" -> "night"; "giorno", "chiaro" -> "day"; else -> "auto" }
            return Command.SetPref("themeMode", v)
        }
        Regex("^sensibilità (alta|normale|bassa)").find(t)?.let {
            return Command.SetPref("wakeSensitivity", mapOf("alta" to "high", "normale" to "normal", "bassa" to "low").getValue(it.groupValues[1]))
        }

        // modalità
        if (Regex("modalità passeggero").containsMatchIn(t)) return Command.SetMode(false)
        if (Regex("modalità guidatore|modalità guida").containsMatchIn(t)) return Command.SetMode(true)

        // barra delle app
        Regex("^aggiungi (.+?) (alla|nella) barra").find(t)?.let { return Command.AddToBar(it.groupValues[1]) }
        Regex("^(togli|rimuovi|leva) (.+?) dalla barra").find(t)?.let { return Command.RemoveFromBar(it.groupValues[2]) }

        // messaggi e chiamate
        if (Regex("^(leggi|leggimi|rileggi|rileggimi|fammi sentire|dimmi|ripeti)( mi)?( tutti)? ?(gli |i |il |l'|l |le |un |ultim)?").containsMatchIn(t) &&
            Regex("\\b(messaggi|messaggio|messaggini|notifiche|sms|whatsapp|chat|teams|scritto)\\b").containsMatchIn(t) &&
            !Regex("^(dimmi|ripeti) (chi|cosa|che|come|quando|dove|quanto)").containsMatchIn(t)
        ) return Command.ReadMessages(messageApp(t), onlyLast(t))
        if (Regex("^(ci sono|ho|hai) (dei |nuovi |altri )?(messaggi|notifiche)|^chi mi ha scritto|^novità su (whatsapp|teams|sms)|^(nuovi )?messaggi( nuovi)?$").containsMatchIn(t)) {
            return Command.ReadMessages(messageApp(t), false)
        }
        if (Regex("^(metti giù|metti giu|riaggancia|chiudi la chiamata|termina la chiamata|rifiuta( la chiamata)?)$").containsMatchIn(t)) return Command.HangUp
        if (Regex("^rispondi").containsMatchIn(t)) return Command.Reply
        if (Regex("(entra|partecipa|collegami)( nella| alla)? riunione").containsMatchIn(t)) return Command.JoinMeeting
        if (Regex("^richiama( l'ultimo| l ultimo)?( numero| chiamata)?$").containsMatchIn(t)) return Command.CallBack
        Regex("^(manda|invia|scrivi|mandagli|scrivigli|mandale|scrivile|messaggio)\\b ?(.*)$").find(t)?.let { m ->
            sendMessage(m.groupValues[1], m.groupValues[2])?.let { return it }
        }
        Regex("^(chiama|telefona a|telefona|fai una chiamata a|fai una telefonata a|videochiama|chiamata whatsapp a|chiamata su whatsapp a|chiamata a)\\s+(.+)$").find(t)?.let {
            var name = it.groupValues[2].trim()
            var wa = it.groupValues[1].contains("whatsapp")
            Regex("\\s?(su|con|via|tramite|in) whatsapp\\b").find(name)?.let { a ->
                wa = true
                name = name.removeRange(a.range).trim()
            }
            name = name.replace(Regex("^(a |ad )"), "").replace(Regex("^(la |il |mia |mio |la mia |il mio )"), "").trim()
            if (name.isNotBlank()) return Command.Call(name, wa)
        }

        // riproduzione
        if (t in setOf("pausa", "metti in pausa", "stop", "ferma", "ferma la musica", "silenzio")) return Command.Pause
        if (t in setOf("riprendi", "play", "continua", "riparti", "fai ripartire")) return Command.Resume
        if (Regex("^(avanti|successiv[ao]|prossim[ao]|salta)").containsMatchIn(t)) {
            return if (t.contains("radio")) Command.NextRadio else Command.Next
        }
        if (Regex("^(indietro|precedente)").containsMatchIn(t)) return Command.Previous
        if (Regex("alza (il )?volume|più forte").containsMatchIn(t)) return Command.Volume(true)
        if (Regex("abbassa (il )?volume|più piano").containsMatchIn(t)) return Command.Volume(false)

        // radio
        if (Regex("\\b(radio|stazioni|emittenti)( radio)? (locali|locale|della zona|di zona|della mia zona|qui vicino|del posto|regionali|regionale|della regione)\\b").containsMatchIn(t)) return Command.LocalRadio
        Regex("^(cerca|trova|cercami|trovami|metti|ascolta|sintonizza|sintonizzati su)( la| una)? (radio|stazione) (.+)$").find(t)?.let { return Command.PlayRadio(it.groupValues[4].trim()) }
        if (Regex("^(cambia|altra) (radio|stazione)|^radio successiva|^prossima radio").containsMatchIn(t)) return Command.NextRadio
        Regex("^(passa a|passa alla|vai su|metti su) (radio .+)$").find(t)?.let { return Command.PlayRadio(it.groupValues[2].removePrefix("radio ").trim()) }
        Regex("^radio (.+)$").find(t)?.let { return Command.PlayRadio(it.groupValues[1].trim()) }
        Regex("^(metti|ascolta|accendi|sintonizza)( la)? radio\\s*(.*)$").find(t)?.let { return Command.PlayRadio(it.groupValues[3].trim()) }
        Regex("^(metti|ascolta|accendi) (rai radio .+|rtl.*|rds|r101|m2o|isoradio)$").find(t)?.let { return Command.PlayRadio(it.groupValues[2].trim()) }

        // video: «cerca Stranger Things su Netflix»
        Regex("^cerca (.+) su (netflix|prime video|prime|dazn|now|raiplay|hbo max|hbo)$").find(t)?.let {
            return Command.SearchVideo(it.groupValues[1].trim(), it.groupValues[2])
        }

        // posti vicini: «ristorante cinese nelle vicinanze», «il miglior ristorante cinese qui vicino»
        nearby(t)?.let { return it }


        // musica: «metti Vasco su Spotify», «metti la playlist viaggio»
        Regex("^(metti|riproduci|suona|fammi sentire|ascolta)\\s+(.+)$").find(t)?.let { m ->
            var q = m.groupValues[2].trim()
            var app: String? = null
            for ((k, v) in MUSIC_APPS) {
                val r = Regex("\\s+(su|con|da) $k$")
                r.find(q)?.let {
                    app = v
                    q = q.removeRange(it.range).trim()
                }
            }
            if (q in setOf("musica", "la musica", "un po' di musica")) return Command.Resume
            return Command.PlayMusic(q, app)
        }

        // app
        Regex("^(apri|avvia|lancia)\\s+(.+)$").find(t)?.let { return Command.OpenApp(it.groupValues[2].trim()) }

        return null
    }

    private fun navigateTo(raw: String): Command {
        var place = raw.trim()
        var waze = false
        Regex("\\s+(con|su|usando) waze$").find(place)?.let {
            waze = true
            place = place.removeRange(it.range).trim()
        }
        if (!waze && looksNearby(place)) return Command.SearchNearby(cleanPlaceQuery(place), place.contains("miglior"))
        return Command.Navigate(normalizePlace("", place), waze)
    }

    private fun messageApp(t: String): String? = when {
        t.contains("whatsapp") -> "WhatsApp"
        Regex("\\bsms\\b|messaggini").containsMatchIn(t) -> "SMS"
        t.contains("teams") -> "Teams"
        else -> null
    }

    private fun onlyLast(t: String): Boolean =
        Regex("\\b(l'ultimo|l ultimo|ultimo messaggio|il messaggio|un messaggio)\\b").containsMatchIn(t) && !Regex("\\bultimi\\b").containsMatchIn(t)

    /**
     * «messaggio whatsapp a Catia moglie che arrivo», «un sms a Luca», «a mia moglie su whatsapp dicendo che…».
     * Il destinatario e il testo li separa poi chi invia, cercando il nome più lungo tra i contatti.
     */
    private fun sendMessage(verb: String, after: String): Command? {
        var rest = (if (verb == "messaggio") "messaggio $after" else after).trim() + " "
        var app: String? = null
        val head = Regex("^((un|il|uno|una|l')\\s+)?((messaggio|messaggino|sms|whatsapp)\\s+)?((su|con|via|tramite|per)\\s+(whatsapp|sms|teams)\\s+)?((messaggio|whatsapp|sms)\\s+)?")
        head.find(rest)?.let { h ->
            val words = listOf(h.groupValues[4], h.groupValues[7], h.groupValues[9])
            app = when {
                "whatsapp" in words -> "whatsapp"
                "sms" in words -> "sms"
                "teams" in words -> "teams"
                else -> null
            }
            rest = rest.substring(h.range.last + 1)
        }
        rest = rest.trim()
        // «manda la posizione», «invia la mail» e simili non sono messaggi
        if (app == null && verb != "messaggio" && !Regex("^(a|ad|al|alla|allo|all'|ai|per)\\b").containsMatchIn(rest) && rest.isNotBlank()) return null
        rest = rest.replace(Regex("^(a|ad|al|alla|allo|all'|ai|per)\\s*"), "")
        Regex("\\s(su|con|via|tramite) (whatsapp|sms|teams)\\b").find(" $rest")?.let { a ->
            app = a.groupValues[2]
            rest = (" $rest").removeRange(a.range).trim()
        }
        rest = rest.replace(Regex("^(la mia |il mio |mia |mio )"), "").trim()
        return Command.SendMessage(rest, app)
    }

    private fun looksNearby(place: String): Boolean =
        Regex("\\b$NEAR\\b").containsMatchIn(place) || place.contains("miglior") ||
            (Regex("^(un|una|uno|un') ").containsMatchIn(place) && Places.isCategory(place))

    private fun cleanPlaceQuery(q: String): String = q
        .replace(Regex("\\b(il|la|lo|l')?\\s*$NEAR\\b"), "")
        .replace(Regex("\\b(il |la |lo |l')?(miglior[ei]?|più buon[oa])\\b"), "")
        .replace(Regex("^(un'|l'|all')\\s*|^(una|uno|un|il|la|lo|i|le|gli|dei|delle|degli|qualche|al|alla|allo)\\s+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun nearby(t: String): Command? {
        val best = Regex("\\b(miglior[ei]?|più buon[oa]|più bell[oa]|consigliat[oa]|recensioni)\\b").containsMatchIn(t)
        val m = Regex("^(cerca|cercami|trova|trovami|consigliami|suggeriscimi|dove posso trovare|dove trovo|dov'è|dove c'è|c'è|ci sono|mi serve|ho bisogno di|qual è|quale è|dimmi)\\s+(.+)$").find(t)
        val body = m?.groupValues?.get(2) ?: t
        val near = Regex("\\b$NEAR\\b").containsMatchIn(body)
        val cat = Places.isCategory(body)
        // le domande vere («cerca chi ha vinto…») restano a Gemini
        val question = Regex("^(chi|cosa|che cosa|come|quando|perché|perche|quanto|quanti|quale|quali|se|il significato|notizie|informazioni)\\b").containsMatchIn(body)
        // «cerca un negozio di scarpe», «dove trovo un ferramenta», «ristorante cinese nelle vicinanze», «il miglior gommista qui vicino»
        val strong = m != null && Regex("^(cerca|cercami|trova|trovami|consigliami|suggeriscimi|dove posso trovare|dove trovo|mi serve|ho bisogno di)$").matches(m.groupValues[1])
        if ((strong && !question) || (m != null && !question && (near || cat || best)) || (m == null && (near || (best && cat)))) {
            val q = cleanPlaceQuery(body)
            if (q.isNotBlank()) return Command.SearchNearby(q, best)
        }
        return null
    }

    private val NUM = mapOf(
        "un" to 1, "uno" to 1, "una" to 1, "due" to 2, "tre" to 3, "quattro" to 4, "cinque" to 5, "dieci" to 10,
        "quindici" to 15, "venti" to 20, "trenta" to 30, "quaranta" to 40, "quarantacinque" to 45, "cinquanta" to 50,
        "novanta" to 90,
    )

    /** «un'ora», «mezz'ora», «due ore», «45 minuti» in minuti. */
    fun minutes(text: String): Int? {
        val t = text.replace("'", " ").trim()
        if (t.startsWith("mezz")) return 30
        if (Regex("^un ora e mezz").containsMatchIn(t)) return 90
        val m = Regex("^(\\d+|[a-z]+) (ora|ore|minuto|minuti)").find(t) ?: return null
        val n = m.groupValues[1].toIntOrNull() ?: NUM[m.groupValues[1]] ?: return null
        return if (m.groupValues[2].startsWith("or")) n * 60 else n
    }

    /** Sì o no detto a voce; null se non si capisce. */
    fun yesNo(text: String): Boolean? {
        val t = normalize(text).trim()
        if (t.isBlank()) return null
        if (Regex("^(no|non|nah|lascia|dopo|annulla|niente|basta|ignora|rifiuta)").containsMatchIn(t)) return false
        if (Regex("^(s[iì]|certo|ok|okay|va bene|leggi|leggilo|rispondi|invia|invialo|manda|mandalo|dai|perfetto|esatto|confermo)").containsMatchIn(t)) return true
        return null
    }

    private fun normalizePlace(prep: String, place: String): String {
        val p = place.trim()
        if (p == "casa" || p == "casa mia" || (prep.trim() == "a" && p == "casa")) return "@casa"
        if (p == "lavoro" || p == "ufficio" || p == "in ufficio") return "@lavoro"
        if (p.contains("prossimo appuntamento")) return "@appuntamento"
        if (p == "macchina" || p == "auto" || p == "alla macchina") return "@macchina"
        if (p == "primo distributore" || p == "primo" || p == "distributore") return "@distributore"
        return p
    }
}
