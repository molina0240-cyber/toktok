package com.vozpapa.tiktok

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

/**
 * Pantalla de configuración (para quien ayuda a instalar) y arranque del asistente.
 * Al abrir la app, si ya hay permisos, el asistente empieza a escuchar solo; así
 * se puede iniciar por voz con "Ok Google, abre Voz para TikTok".
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var wakeSwitch: SwitchCompat
    private lateinit var wakeEdit: EditText
    private val handler = Handler(Looper.getMainLooper())

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasMic()) {
                startAssistant()
            } else {
                Toast.makeText(this, R.string.mic_denied, Toast.LENGTH_LONG).show()
            }
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        statusText = findViewById(R.id.statusText)
        toggleButton = findViewById(R.id.btnToggle)
        wakeSwitch = findViewById(R.id.switchWake)
        wakeEdit = findViewById(R.id.editWake)

        wakeSwitch.isChecked = prefs.wakeRequired
        wakeEdit.setText(prefs.wakeWord)
        wakeEdit.isEnabled = prefs.wakeRequired
        wakeSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.wakeRequired = checked
            wakeEdit.isEnabled = checked
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        toggleButton.setOnClickListener {
            if (VoiceService.running) stopAssistant() else ensureAndStart()
        }

        // Arranque automático al abrir la app (no al girar la pantalla).
        if (savedInstanceState == null && !VoiceService.running) ensureAndStart()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onPause() {
        prefs.wakeWord = wakeEdit.text.toString().ifBlank { Prefs.DEFAULT_WAKE }
        super.onPause()
    }

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureAndStart() {
        if (hasMic()) {
            startAssistant()
            return
        }
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(needed.toTypedArray())
    }

    private fun startAssistant() {
        ContextCompat.startForegroundService(this, Intent(this, VoiceService::class.java))
        handler.postDelayed({ refresh() }, 700)
    }

    private fun stopAssistant() {
        startService(Intent(this, VoiceService::class.java).setAction(VoiceService.ACTION_STOP))
        handler.postDelayed({ refresh() }, 700)
    }

    private fun refresh() {
        val mic = getString(if (hasMic()) R.string.ok_allowed else R.string.missing_permission)
        val acc = getString(
            if (GestureService.instance != null) R.string.ok_active else R.string.missing_enable
        )
        val run = getString(if (VoiceService.running) R.string.state_listening else R.string.state_off)
        statusText.text = getString(R.string.status_format, mic, acc, run)
        toggleButton.setText(
            if (VoiceService.running) R.string.stop_assistant else R.string.start_assistant
        )
    }
}
