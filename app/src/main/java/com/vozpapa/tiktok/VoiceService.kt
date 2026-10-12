package com.vozpapa.tiktok

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Escucha el micrófono de forma continua (servicio en primer plano) y manda
 * los comandos al [GestureService]. Responde con voz para que la persona
 * sepa qué pasó sin mirar la pantalla.
 */
class VoiceService : Service() {

    companion object {
        const val ACTION_STOP = "com.vozpapa.tiktok.STOP"
        private const val CHANNEL_ID = "voz_asistente"
        private const val NOTIF_ID = 1

        /** Tiempo que sigue "despierto" el asistente después de oír la palabra clave. */
        private const val ARMED_MS = 10_000L

        @Volatile
        var running = false
            private set
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var speaker: Speaker
    private var recognizer: SpeechRecognizer? = null

    private var listening = false
    private var speaking = false
    private var stopped = false
    private var stopAfterSpeech = false
    private var armedUntil = 0L
    private var networkErrors = 0

    private val restartRunnable = Runnable { startListening() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)

        val micOk = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!micOk || !goForeground()) {
            stopSelf()
            return
        }
        running = true

        speaker = Speaker(this) { onSpeechIdle() }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopAfterSpeech = true
            say("Este teléfono no tiene reconocimiento de voz disponible.")
            return
        }
        createRecognizer()
        say(introText())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) shutdown()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopped = true
        running = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        if (::speaker.isInitialized) speaker.release()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- arranque

    @SuppressLint("InlinedApi")
    private fun goForeground(): Boolean {
        return try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, VoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, getString(R.string.stop_assistant), stop)
            .build()
    }

    private fun introText(): String {
        val sb = StringBuilder("Asistente activado. ")
        if (prefs.wakeRequired) sb.append("Di ${prefs.wakeWord} y luego un comando. ")
        sb.append("Di ayuda para escuchar las opciones.")
        if (GestureService.instance == null) {
            sb.append(" Falta activar la accesibilidad en los ajustes del teléfono.")
        }
        return sb.toString()
    }

    // ------------------------------------------------------------ reconocimiento

    private fun createRecognizer() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(listener)
        }
        listening = false
    }

    private fun startListening() {
        if (stopped || speaking || listening) return
        val r = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
        }
        listening = true
        try {
            r.startListening(intent)
        } catch (_: Exception) {
            listening = false
            createRecognizer()
            scheduleRestart(1000)
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        main.removeCallbacks(restartRunnable)
        if (!stopped) main.postDelayed(restartRunnable, delayMs)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onResults(results: Bundle?) {
            listening = false
            networkErrors = 0
            val heard = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            handleTranscripts(heard)
            scheduleRestart(150)
        }

        override fun onError(error: Int) {
            listening = false
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    stopAfterSpeech = true
                    say("Necesito permiso para usar el micrófono.")
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    createRecognizer()
                    scheduleRestart(800)
                }
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> {
                    networkErrors++
                    if (networkErrors == 3) {
                        say("No logro entender la voz. Revisa la conexión a internet.")
                    }
                    scheduleRestart(1500)
                }
                SpeechRecognizer.ERROR_CLIENT -> scheduleRestart(500)
                // Silencio o nada reconocible: es lo normal, se vuelve a escuchar.
                else -> scheduleRestart(200)
            }
        }
    }

    // ------------------------------------------------------------------ comandos

    private fun handleTranscripts(candidates: List<String>) {
        if (stopped || candidates.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        val wakeWord = CommandParser.normalize(prefs.wakeWord)
        val needWake = prefs.wakeRequired && wakeWord.isNotEmpty()

        var heardWakeOnly = false
        var heardWakeUnknown = false

        for (raw in candidates) {
            var text = CommandParser.normalize(raw)
            var wakeHeard = false

            if (needWake) {
                val padded = " $text "
                if (padded.contains(" $wakeWord ")) {
                    wakeHeard = true
                    text = padded.replaceFirst(" $wakeWord ", " ").trim()
                } else if (now > armedUntil) {
                    continue // sin palabra clave y sin estar "despierto": se ignora
                }
            }

            if (text.isEmpty()) {
                if (wakeHeard) heardWakeOnly = true
                continue
            }

            val command = CommandParser.parse(text)
            if (command != Command.UNKNOWN) {
                if (needWake) armedUntil = now + ARMED_MS
                execute(command)
                return
            }
            if (wakeHeard) heardWakeUnknown = true
        }

        if (heardWakeOnly) {
            armedUntil = now + ARMED_MS
            speaker.beep()
        } else if (heardWakeUnknown) {
            say("No entendí. Di ayuda para escuchar las opciones.")
        }
    }

    private fun execute(command: Command) {
        when (command) {
            Command.HELP -> {
                say(CommandParser.HELP_TEXT)
                return
            }
            Command.STOP -> {
                stopAfterSpeech = true
                say("Hasta luego.")
                return
            }
            else -> Unit
        }

        val svc = GestureService.instance
        if (svc == null) {
            say("Falta activar la accesibilidad en los ajustes del teléfono.")
            return
        }

        if (command == Command.OPEN_TIKTOK) {
            if (svc.launchTikTok()) say("Abriendo TikTok")
            else say("No pude abrir TikTok. Revisa que esté instalado.")
            return
        }

        if (!svc.isTikTokInForeground()) {
            say("TikTok no está abierto. Di: abre TikTok.")
            return
        }

        val ok = when (command) {
            Command.NEXT -> svc.swipeVideo(true)
            Command.PREVIOUS -> svc.swipeVideo(false)
            Command.PLAY_PAUSE -> svc.tapCenter()
            Command.LIKE -> svc.doubleTapCenter()
            Command.COMMENTS -> svc.openComments()
            Command.CLOSE -> svc.goBack()
            else -> false
        }

        if (!ok) {
            say(
                if (command == Command.COMMENTS) "No encontré el botón de comentarios."
                else "No pude hacerlo."
            )
            return
        }

        when (command) {
            Command.LIKE -> say("Me gusta")
            Command.COMMENTS -> say("Comentarios")
            else -> speaker.beep()
        }
    }

    // ----------------------------------------------------------------- voz / cierre

    /** Habla y deja de escuchar mientras tanto, para no oírse a sí mismo. */
    private fun say(text: String) {
        speaking = true
        main.removeCallbacks(restartRunnable)
        if (listening) {
            recognizer?.cancel()
            listening = false
        }
        speaker.speak(text)
    }

    private fun onSpeechIdle() {
        speaking = false
        if (stopAfterSpeech) shutdown() else scheduleRestart(150)
    }

    private fun shutdown() {
        if (stopped) return
        stopped = true
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
