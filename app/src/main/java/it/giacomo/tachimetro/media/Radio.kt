package it.giacomo.tachimetro.media

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import it.giacomo.tachimetro.Hub
import it.giacomo.tachimetro.RadioState
import it.giacomo.tachimetro.data.Net
import org.json.JSONArray

data class Station(val name: String, val url: String)

/** Emittenti DAB italiane ascoltate in streaming. Gli indirizzi si cercano su radio-browser.info, un archivio libero. */
object Radio {
    val NATIONAL = listOf(
        "Rai Radio 1", "Rai Radio 2", "Rai Radio 3", "RTL 102.5", "Radio Deejay", "Radio 105",
        "Virgin Radio", "Radio Kiss Kiss", "RDS", "Radio Italia", "Radio Capital", "m2o",
        "R101", "Radio Monte Carlo", "Radio 24", "Radiofreccia", "Radio Zeta", "Radio Subasio",
        "Radio Maria", "Isoradio",
    )

    private var player: ExoPlayer? = null
    private val resolved = HashMap<String, String>()
    var current: String? = null
        private set

    private val SERVERS = listOf(
        "https://de1.api.radio-browser.info",
        "https://de2.api.radio-browser.info",
        "https://fi1.api.radio-browser.info",
    )

    private suspend fun query(path: String): JSONArray {
        var last: Exception? = null
        for (s in SERVERS) {
            try {
                return Net.getJsonArray(s + path)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("radio-browser non raggiungibile")
    }

    suspend fun resolve(name: String): Station? {
        resolved[name.lowercase()]?.let { return Station(name, it) }
        val arr = query("/json/stations/search?name=" + Net.enc(name) + "&countrycode=IT&order=clickcount&reverse=true&limit=5&hidebroken=true")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val url = o.optString("url_resolved").ifBlank { o.optString("url") }
            if (url.isNotBlank()) {
                resolved[name.lowercase()] = url
                return Station(o.optString("name", name).trim(), url)
            }
        }
        return null
    }

    suspend fun regional(region: String): List<String> {
        if (region.isBlank()) return emptyList()
        val arr = query("/json/stations/search?state=" + Net.enc(region) + "&countrycode=IT&order=clickcount&reverse=true&limit=20&hidebroken=true")
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val n = o.optString("name").trim()
            val url = o.optString("url_resolved")
            if (n.isNotBlank() && url.isNotBlank() && out.none { it.equals(n, true) }) {
                out += n
                resolved[n.lowercase()] = url
            }
        }
        return out
    }

    /** Trova l'emittente più simile a quanto detto, tra le nazionali. */
    fun match(spoken: String): String? {
        val s = spoken.lowercase().replace(".", "").replace("radio ", "").trim()
        if (s.isBlank()) return null
        return NATIONAL.firstOrNull { n ->
            val k = n.lowercase().replace(".", "").replace("radio ", "").trim()
            k == s || s.contains(k) || k.contains(s)
        }
    }

    fun play(context: Context, station: Station) {
        val p = player ?: ExoPlayer.Builder(context.applicationContext).build().also { ep ->
            ep.setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            ep.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    current?.let { Hub.radio.value = RadioState(it, isPlaying) }
                }

                override fun onPlayerError(error: PlaybackException) {
                    current?.let { Hub.radio.value = RadioState(it, false, "Streaming non disponibile") }
                }
            })
            player = ep
        }
        current = station.name
        Hub.radio.value = RadioState(station.name, false)
        p.setMediaItem(MediaItem.fromUri(station.url))
        p.prepare()
        p.play()
    }

    fun pause() {
        player?.pause()
    }

    fun resume() {
        player?.play()
    }

    fun isActive(): Boolean = player?.isPlaying == true

    fun stop() {
        player?.stop()
        current?.let { Hub.radio.value = RadioState(it, false) }
    }

    fun release() {
        player?.release()
        player = null
        current = null
        Hub.radio.value = null
    }
}
