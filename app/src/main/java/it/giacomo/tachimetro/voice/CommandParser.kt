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
    data class AskGemini(val question: String) : Command
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
        .replace(Regex("^(ehi|hey|hei|ei|e)\\s+(tachi|tacchi|taxi|ta chi)[,]?\\s*"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun parse(raw: String): Command {
        val t = normalize(raw)
        if (t.isBlank()) return Command.Cancel

        // annullare
        if (t in setOf("annulla", "lascia stare", "niente", "no", "basta così")) return Command.Cancel

        // schermate
        if (Regex("^(torna al |vai al |apri il )?cruscotto|^torna indietro|^home$").containsMatchIn(t)) return Command.Dashboard
        if (Regex("^(apri )?(le )?impostazioni").containsMatchIn(t)) return Command.Settings
        if (Regex("silenzia (gli )?avvisi").containsMatchIn(t)) return Command.Mute

        // navigazione
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

        // tutto il resto va a Gemini
        Regex("^chiedi a gemini\\s+(.+)$").find(t)?.let { return Command.AskGemini(it.groupValues[1]) }
        return Command.AskGemini(t)
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
