package it.giacomo.tachimetro.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Voce di Tachimetro. Mentre parla abbassa la musica invece di fermarla. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val am = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs)
        .build()
    private val tts = TextToSpeech(context.applicationContext, this)
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    private var ready = false
    private val pending = ArrayList<Pair<String, (() -> Unit)?>>()
    private var counter = 0

    @Volatile
    var speaking = false
        private set

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Locale.ITALIAN
        tts.setAudioAttributes(attrs)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                speaking = true
            }

            override fun onDone(utteranceId: String?) = finish(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
        })
        ready = true
        val copy = ArrayList(pending)
        pending.clear()
        copy.forEach { say(it.first, it.second) }
    }

    private fun finish(id: String?) {
        main.post {
            if (!tts.isSpeaking) {
                speaking = false
                am?.abandonAudioFocusRequest(focus)
            }
            id?.let { callbacks.remove(it)?.invoke() }
        }
    }

    fun say(text: String, then: (() -> Unit)? = null) {
        if (!ready) {
            pending += text to then
            return
        }
        val id = "u" + (counter++)
        if (then != null) callbacks[id] = then
        speaking = true
        am?.requestAudioFocus(focus)
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, id)
    }

    fun stop() {
        tts.stop()
        speaking = false
        am?.abandonAudioFocusRequest(focus)
        callbacks.clear()
    }

    /** Tono breve e a volume basso, per gli avvisi discreti. */
    fun beep(volume: Int = 35) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, volume)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 160)
            main.postDelayed({ tg.release() }, 400)
        } catch (e: Exception) {
        }
    }

    fun shutdown() {
        tts.shutdown()
    }
}
