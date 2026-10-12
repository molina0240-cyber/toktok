package com.vozpapa.tiktok

import android.content.Context

/** Ajustes sencillos guardados en el teléfono. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("voz_tiktok", Context.MODE_PRIVATE)

    /** Si es true, los comandos solo se aceptan después de decir la palabra clave. */
    var wakeRequired: Boolean
        get() = sp.getBoolean("wake_required", true)
        set(value) = sp.edit().putBoolean("wake_required", value).apply()

    var wakeWord: String
        get() = sp.getString("wake_word", DEFAULT_WAKE) ?: DEFAULT_WAKE
        set(value) = sp.edit().putString("wake_word", value.trim()).apply()

    companion object {
        const val DEFAULT_WAKE = "asistente"
    }
}
