package it.giacomo.tachimetro.voice

import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {
    private val cases: List<Pair<String, (Command) -> Boolean>> = listOf(
        "manda messaggio whatsapp a Catia moglie" to { c -> c is Command.SendMessage && c.rest == "catia moglie" && c.app == "whatsapp" },
        "Manda un messaggio WhatsApp a Catia moglie che arrivo tra dieci minuti" to { c -> c is Command.SendMessage && c.rest == "catia moglie che arrivo tra dieci minuti" && c.app == "whatsapp" },
        "scrivi a Marco su WhatsApp che arrivo" to { c -> c is Command.SendMessage && c.rest == "marco che arrivo" && c.app == "whatsapp" },
        "manda un sms a Luca" to { c -> c is Command.SendMessage && c.rest == "luca" && c.app == "sms" },
        "puoi mandare un messaggio a mia moglie" to { c -> c is Command.SendMessage && c.rest == "moglie" },
        "manda un messaggio" to { c -> c is Command.SendMessage && c.rest.isBlank() },
        "chi manda messaggio whatsapp a Catia" to { c -> c is Command.SendMessage && c.rest == "catia" },
        "leggi ultimi messaggi whatsapp" to { c -> c is Command.ReadMessages && c.app == "WhatsApp" && !c.onlyLast },
        "leggimi i messaggi" to { c -> c is Command.ReadMessages && c.app == null },
        "leggi l'ultimo messaggio" to { c -> c is Command.ReadMessages && c.onlyLast },
        "ci sono messaggi su Teams" to { c -> c is Command.ReadMessages && c.app == "Teams" },
        "chi mi ha scritto" to { c -> c is Command.ReadMessages },
        "cerca radio locale in streaming" to { c -> c is Command.LocalRadio },
        "metti le radio locali" to { c -> c is Command.LocalRadio },
        "cerca radio Kiss Kiss" to { c -> c is Command.PlayRadio && c.station == "kiss kiss" },
        "metti Radio Deejay" to { c -> c is Command.PlayRadio },
        "ristorante cinese nelle vicinanze" to { c -> c is Command.SearchNearby && c.query == "ristorante cinese" && !c.best },
        "miglior ristorante cinese nelle vicinanze" to { c -> c is Command.SearchNearby && c.query == "ristorante cinese" && c.best },
        "cerca un negozio di scarpe" to { c -> c is Command.SearchNearby && c.query == "negozio di scarpe" },
        "dove trovo una ferramenta" to { c -> c is Command.SearchNearby && c.query == "ferramenta" },
        "trovami un gommista qui vicino" to { c -> c is Command.SearchNearby && c.query == "gommista" },
        "c'è una farmacia aperta qui vicino" to { c -> c is Command.SearchNearby },
        "cerca chi ha vinto la partita" to { c -> c is Command.AskGemini },
        "portami a ufficio postale in via Marmorata" to { c -> c is Command.Navigate && c.place == "ufficio postale in via marmorata" },
        "portami al ristorante cinese più vicino" to { c -> c is Command.SearchNearby && c.query == "ristorante cinese" },
        "portami a casa" to { c -> c is Command.Navigate && c.place == "@casa" },
        "portami" to { c -> c is Command.Navigate && c.place.isBlank() },
        "imposta il percorso per Caserta" to { c -> c is Command.Navigate && c.place == "caserta" },
        "partiamo" to { c -> c is Command.Go },
        "chiama Luca su WhatsApp" to { c -> c is Command.Call && c.name == "luca" && c.whatsapp },
        "mi puoi chiamare Catia moglie" to { c -> c is Command.Call && c.name == "catia moglie" },
        "che tempo fa a Napoli" to { c -> c is Command.WeatherAt && c.place == "napoli" },
        "cerca un parcheggio" to { c -> c is Command.FindParking },
        "trova benzina qui vicino" to { c -> c is Command.FindFuel },
        "cerca Stranger Things su Netflix" to { c -> c is Command.SearchVideo },
        "metti Vasco su Spotify" to { c -> c is Command.PlayMusic },
        "chi è il presidente della Repubblica" to { c -> c is Command.AskGemini },
        "blabla patata" to { c -> c is Command.Unknown },
    )

    @Test
    fun frasi() {
        val wrong = cases.mapNotNull { (phrase, ok) ->
            val c = CommandParser.parse(phrase)
            if (ok(c)) null else "«$phrase» → $c"
        }
        assertTrue("Frasi capite male:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }
}
