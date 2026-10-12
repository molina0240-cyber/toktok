package com.vozpapa.tiktok

import java.text.Normalizer

enum class Command {
    NEXT, PREVIOUS, PLAY_PAUSE, LIKE, COMMENTS, CLOSE, OPEN_TIKTOK, HELP, STOP, UNKNOWN
}

/**
 * Convierte lo que el reconocedor de voz escuchó en un comando.
 * Para agregar sinónimos, solo edita la lista [rules].
 * El orden importa: la primera regla que coincida gana.
 */
object CommandParser {

    const val HELP_TEXT =
        "Puedes decir: siguiente, anterior, pausa, me gusta, comentarios, cerrar, " +
            "abre TikTok, y apágate."

    private val rules: List<Pair<Command, List<String>>> = listOf(
        Command.STOP to listOf(
            "apagate", "desactivate", "adios", "hasta luego", "terminar"
        ),
        Command.HELP to listOf(
            "ayuda", "comandos", "que puedo decir", "que puedo hacer", "opciones"
        ),
        Command.CLOSE to listOf(
            "cierra", "cerrar", "cierralo", "cierra comentarios", "cerrar comentarios",
            "salir de comentarios"
        ),
        Command.OPEN_TIKTOK to listOf(
            "abre tiktok", "abrir tiktok", "abre tik tok", "abrir tik tok",
            "abre tic toc", "abrir tic toc", "tiktok", "tik tok", "tic toc"
        ),
        Command.COMMENTS to listOf(
            "comentarios", "comentario", "abre comentarios", "ver comentarios"
        ),
        Command.LIKE to listOf(
            "me gusta", "dale like", "like", "corazon", "dale corazon"
        ),
        Command.PLAY_PAUSE to listOf(
            "pausa", "pausar", "pausalo", "detente", "detener", "alto",
            "continua", "continuar", "reanuda", "reanudar", "play",
            "reproduce", "reproducir"
        ),
        Command.PREVIOUS to listOf(
            "anterior", "atras", "regresa", "regresar", "vuelve", "volver",
            "previo", "el de antes"
        ),
        Command.NEXT to listOf(
            "siguiente", "sigue", "adelante", "otro", "otro video", "pasa", "pasar",
            "salta", "saltar", "avanza", "avanzar", "next"
        )
    )

    /** Minúsculas, sin acentos ni signos, espacios simples. */
    fun normalize(raw: String): String {
        val decomposed = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** [normalizedText] ya debe venir pasado por [normalize]. */
    fun parse(normalizedText: String): Command {
        if (normalizedText.isBlank()) return Command.UNKNOWN
        val padded = " $normalizedText "
        for ((command, phrases) in rules) {
            for (phrase in phrases) {
                if (padded.contains(" $phrase ")) return command
            }
        }
        return Command.UNKNOWN
    }
}
