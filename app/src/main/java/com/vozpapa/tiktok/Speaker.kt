package com.vozpapa.tiktok

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Voz de respuesta (TextToSpeech) y pitido corto.
 * [onIdle] se llama cuando termina de hablar todo lo pendiente, para que el
 * servicio sepa que ya puede volver a escuchar (así no se escucha a sí mismo).
 * Usar siempre desde el hilo principal.
 */
class Speaker(context: Context, private val onIdle: () -> Unit) {

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private val waiting = ArrayDeque<String>()
    private var pending = 0

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            main.post { onEngineReady(status) }
        }
    }

    private fun onEngineReady(status: Int) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            failed = true
            waiting.clear()
            pending = 0
            onIdle()
            return
        }
        val result = engine.setLanguage(Locale.forLanguageTag("es-MX"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.forLanguageTag("es"))
        }
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                main.post { finishOne() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { finishOne() }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                main.post { finishOne() }
            }
        })
        ready = true
        while (waiting.isNotEmpty()) speakNow(waiting.removeFirst())
    }

    fun speak(text: String) {
        if (failed) {
            main.post { onIdle() }
            return
        }
        pending++
        if (!ready) {
            waiting.addLast(text)
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        val result = tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "u${System.nanoTime()}")
        if (result != TextToSpeech.SUCCESS) finishOne()
    }

    private fun finishOne() {
        if (pending > 0) pending--
        if (pending == 0) onIdle()
    }

    /** Pitido corto de confirmación (para acciones que ya se notan por sí solas). */
    fun beep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            main.postDelayed({ tone.release() }, 300)
        } catch (_: Exception) {
            // Si el pitido falla no pasa nada.
        }
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
