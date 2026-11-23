package it.giacomo.tachimetro.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Una domanda sì/no a voce, anche quando il servizio vocale non è ancora attivo (per esempio: «Sei in auto?»). */
class QuickAsk(private val context: Context) {
    private val speaker = Speaker(context)
    private var sr: SpeechRecognizer? = null

    fun ask(question: String, onAnswer: (Boolean?) -> Unit) {
        speaker.say(question) { listen(onAnswer) }
    }

    private fun listen(onAnswer: (Boolean?) -> Unit) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            !SpeechRecognizer.isRecognitionAvailable(context)
        ) {
            onAnswer(null)
            return
        }
        val r = sr ?: SpeechRecognizer.createSpeechRecognizer(context).also { sr = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) = onAnswer(null)
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                onAnswer(CommandParser.yesNo(t))
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT"),
        )
    }

    fun release() {
        sr?.destroy()
        sr = null
        speaker.shutdown()
    }
}
