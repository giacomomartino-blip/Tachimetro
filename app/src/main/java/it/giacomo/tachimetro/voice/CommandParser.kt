package it.giacomo.tachimetro.voice

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
    data class Call(val name: String) : Command
    data object ReadMessages : Command
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
    data object Weather : Command
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
        .trim()

    /** Pezzi della parola chiave che a volte restano all'inizio del comando («chi metti la radio»). */
    private val RESIDUE = setOf("ehi", "hey", "hei", "ei", "e", "ok", "chi", "ki", "ci", "ta", "taki", "tachi", "tacchi", "taxi")

    private val QUESTION = Regex(
        "^(chi|cosa|che|come|quando|dove|quanto|quanti|quanta|quante|perché|perche|qual|quale|quali|dimmi|spiegami|sai|mi sai|raccontami|chiedi)\\b",
    )

    fun parse(raw: String): Command {
        val base = normalize(raw)
        if (base.isBlank()) return Command.Cancel
        // prima la frase così com'è, poi senza gli eventuali resti di «Ehi Tachi» all'inizio
        val words = base.split(" ")
        val candidates = mutableListOf(base)
        var i = 0
        while (i < words.size - 1 && i < 3 && words[i] in RESIDUE) {
            i++
            candidates += words.drop(i).joinToString(" ")
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
        if (Regex("^(torna a|usa) (google )?maps").containsMatchIn(t)) return Command.BackToMaps
        if (Regex("(ferma|termina|interrompi|chiudi) (la )?navigazione").containsMatchIn(t)) return Command.StopNavigation
        Regex("^(portami|guidami|naviga|vai|andiamo|voglio andare)\\s+(verso |a |al |alla |allo |ai |alle |all'|in |da |dal |dalla )?(.+)$").find(t)?.let { m ->
            var place = m.groupValues[3].trim()
            var waze = false
            Regex("\\s+(con|su|usando) waze$").find(place)?.let {
                waze = true
                place = place.removeRange(it.range).trim()
            }
            return Command.Navigate(normalizePlace(m.groupValues[2], place), waze)
        }

        // auto e servizi
        if (Regex("dov'è (la )?(mia )?(macchina|auto)|dove ho parcheggiato|dov è la macchina|dove è la macchina").containsMatchIn(t)) return Command.WhereIsCar
        if (Regex("(trova|cerca|dov'è|dove).*(benzina|distribut|gasolio|diesel|carburante|gpl|metano)").containsMatchIn(t) ||
            Regex("^(benzina|distributor[ei])").containsMatchIn(t)
        ) return Command.FindFuel
        if (Regex("(che tempo fa|meteo|temperatura)").containsMatchIn(t) && !t.contains("chiedi")) return Command.Weather

        // modalità
        if (Regex("modalità passeggero").containsMatchIn(t)) return Command.SetMode(false)
        if (Regex("modalità guidatore|modalità guida").containsMatchIn(t)) return Command.SetMode(true)

        // barra delle app
        Regex("^aggiungi (.+?) (alla|nella) barra").find(t)?.let { return Command.AddToBar(it.groupValues[1]) }
        Regex("^(togli|rimuovi|leva) (.+?) dalla barra").find(t)?.let { return Command.RemoveFromBar(it.groupValues[2]) }

        // messaggi e chiamate
        if (Regex("(leggi|leggimi) (i |il )?(messaggi|messaggio|ultimo)").containsMatchIn(t)) return Command.ReadMessages
        if (Regex("^rispondi").containsMatchIn(t)) return Command.Reply
        if (Regex("(entra|partecipa|collegami)( nella| alla)? riunione").containsMatchIn(t)) return Command.JoinMeeting
        Regex("^(chiama|telefona a|fai una chiamata a)\\s+(.+)$").find(t)?.let { return Command.Call(it.groupValues[2].trim()) }

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
        if (Regex("^(cambia|altra) (radio|stazione)|^radio successiva|^prossima radio").containsMatchIn(t)) return Command.NextRadio
        Regex("^(passa a|passa alla|vai su|metti su) (radio .+)$").find(t)?.let { return Command.PlayRadio(it.groupValues[2].removePrefix("radio ").trim()) }
        Regex("^radio (.+)$").find(t)?.let { return Command.PlayRadio(it.groupValues[1].trim()) }
        Regex("^(metti|ascolta|accendi|sintonizza)( la)? radio\\s*(.*)$").find(t)?.let { return Command.PlayRadio(it.groupValues[3].trim()) }
        Regex("^(metti|ascolta|accendi) (rai radio .+|rtl.*|rds|r101|m2o|isoradio)$").find(t)?.let { return Command.PlayRadio(it.groupValues[2].trim()) }

        // video: «cerca Stranger Things su Netflix»
        Regex("^cerca (.+) su (netflix|prime video|prime|dazn|now|raiplay|hbo max|hbo)$").find(t)?.let {
            return Command.SearchVideo(it.groupValues[1].trim(), it.groupValues[2])
        }

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
        return Command.Navigate(normalizePlace("", place), waze)
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
