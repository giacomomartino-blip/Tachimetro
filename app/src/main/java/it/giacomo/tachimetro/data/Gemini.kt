package it.giacomo.tachimetro.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Domande generali girate a Gemini. Riceve solo il testo della domanda: niente posizione, messaggi o contatti.
 */
object Gemini {
    /** Modelli provati in ordine: se uno non è disponibile per la tua chiave si passa al successivo. */
    private val MODELS = listOf("gemini-flash-latest", "gemini-3.5-flash", "gemini-flash-lite-latest", "gemini-2.5-flash")

    class GeminiException(val spoken: String, val detail: String) : Exception(detail)

    suspend fun ask(apiKey: String, question: String): String {
        val prompt = "Rispondi in italiano, in al massimo due frasi brevi, adatte a essere lette ad alta voce a chi sta guidando. " +
            "Niente elenchi né simboli. Domanda: $question"
        val body = JSONObject().put(
            "contents",
            JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))),
        )
        var last: GeminiException? = null
        for (model in MODELS) {
            val (code, text) = post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent", apiKey.trim(), body)
            if (code in 200..299) return extract(text)
            val msg = try { JSONObject(text).getJSONObject("error").optString("message") } catch (e: Exception) { text.take(200) }
            last = when (code) {
                400, 401, 403 -> if (msg.contains("API key", true) || msg.contains("permission", true) || code != 400) {
                    GeminiException("La chiave di Gemini non è valida o non è abilitata.", "HTTP $code: $msg")
                } else {
                    GeminiException("Gemini ha rifiutato la domanda.", "HTTP $code: $msg")
                }
                404 -> GeminiException("Gemini non trova il modello.", "HTTP 404 ($model): $msg").also { }
                429 -> GeminiException("Hai superato il limite gratuito di Gemini per ora. Riprova più tardi.", "HTTP 429: $msg")
                else -> GeminiException("Gemini non risponde in questo momento.", "HTTP $code: $msg")
            }
            // con chiave non valida o limite raggiunto non serve provare altri modelli
            if (code == 401 || code == 403 || code == 429) break
        }
        throw last ?: GeminiException("Gemini non risponde in questo momento.", "nessuna risposta")
    }

    private fun extract(text: String): String {
        val res = JSONObject(text)
        val parts = res.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (!p.optBoolean("thought", false)) sb.append(p.optString("text"))
        }
        return sb.toString().replace("*", "").trim().ifBlank { "Non ho una risposta." }
    }

    private suspend fun post(url: String, key: String, body: JSONObject): Pair<Int, String> = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        // la chiave va nell'intestazione, non nell'indirizzo
        c.setRequestProperty("x-goog-api-key", key)
        try {
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            c.disconnect()
        }
    }
}
