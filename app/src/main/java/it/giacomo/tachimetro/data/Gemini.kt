package it.giacomo.tachimetro.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Domande generali girate a Gemini. Riceve solo il testo della domanda: niente posizione, messaggi o contatti.
 */
object Gemini {
    private const val MODEL = "gemini-flash-latest"

    suspend fun ask(apiKey: String, question: String): String {
        val prompt = "Rispondi in italiano, in al massimo due frasi brevi, adatte a essere lette ad alta voce a chi sta guidando. " +
            "Niente elenchi né simboli. Domanda: $question"
        val body = JSONObject().put(
            "contents",
            JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))),
        )
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=" + Net.enc(apiKey)
        val res = JSONObject(Net.postJson(url, body))
        val parts = res.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        return sb.toString().replace("*", "").trim()
    }
}
