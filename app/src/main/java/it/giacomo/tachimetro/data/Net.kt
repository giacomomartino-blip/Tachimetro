package it.giacomo.tachimetro.data

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object Net {
    private const val UA = "Tachimetro/0.1 (app personale Android)"

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    suspend fun getText(url: String, timeoutMs: Int = 15000): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.setRequestProperty("User-Agent", UA)
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    suspend fun postJson(url: String, body: JSONObject, timeoutMs: Int = 20000): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("User-Agent", UA)
        try {
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}: ${text.take(200)}")
            text
        } finally {
            c.disconnect()
        }
    }

    /** Legge un file riga per riga senza tenerlo tutto in memoria. */
    suspend fun forEachLine(url: String, timeoutMs: Int = 60000, onLine: (String) -> Unit) = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.setRequestProperty("User-Agent", UA)
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            BufferedReader(InputStreamReader(c.inputStream, Charsets.UTF_8)).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    onLine(line)
                }
            }
        } finally {
            c.disconnect()
        }
    }

    suspend fun getJsonArray(url: String): JSONArray = JSONArray(getText(url))
    suspend fun getJsonObject(url: String): JSONObject = JSONObject(getText(url))
}
