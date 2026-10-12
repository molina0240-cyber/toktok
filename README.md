# Voz para TikTok

Asistente de voz para Android que permite controlar TikTok sin tocar la pantalla,
pensado para personas invidentes. Todo se maneja con la voz y la app responde
hablando.

## Cómo funciona

- **VoiceService**: escucha el micrófono de forma continua (servicio en primer plano).
- **CommandParser**: convierte lo que escuchó en un comando. Los sinónimos se editan ahí.
- **GestureService**: servicio de accesibilidad que desliza, toca y presiona botones.
  Solo actúa si TikTok está en primer plano.
- **Speaker**: responde con voz (y pitidos) para que no haya que mirar la pantalla.

## Instalación

1. Abre la carpeta `VozTikTok` en **Android Studio** (File > Open) y espera a que sincronice Gradle.
2. Conecta el teléfono con depuración USB y presiona **Run**.
3. En el teléfono:
   - Abre la app y permite el micrófono.
   - Toca **Abrir ajustes de accesibilidad**, entra a **Voz para TikTok** y actívalo.
   - Si instalaste el APK por fuera de Play Store (WhatsApp, descarga directa) y Android
     bloquea el interruptor: Ajustes > Apps > Voz para TikTok > menú ⋮ >
     **Permitir ajustes restringidos**, y vuelve a intentar.
4. Recomendado: descarga el reconocimiento de voz en español sin conexión
   (Ajustes > Idiomas > Escritura por voz de Google), para que funcione más rápido y sin internet.
5. Recomendado: Ajustes > Apps > Voz para TikTok > Batería > **Sin restricciones**.

Para iniciar solo con la voz: **"Ok Google, abre Voz para TikTok"**. Al abrirse, la app
empieza a escuchar sola.

## Comandos

Con la palabra clave activada (por defecto: **"asistente"**), se dice la palabra y luego
el comando: *"asistente, siguiente"*. Después de la palabra clave quedan 10 segundos
en los que se pueden encadenar comandos sin repetirla.

| Di                                   | Hace                                  |
|--------------------------------------|---------------------------------------|
| siguiente / sigue / otro / pasa      | Siguiente video                       |
| anterior / atrás / regresa           | Video anterior                        |
| pausa / continúa / play              | Pausa o reanuda                       |
| me gusta / like / corazón            | Da me gusta (doble toque)             |
| comentarios                          | Abre los comentarios                  |
| cerrar / cierra                      | Botón atrás (cierra comentarios)      |
| abre TikTok                          | Abre TikTok                           |
| ayuda                                | Lee la lista de comandos              |
| apágate / adiós                      | Apaga el asistente                    |

## Limitaciones conocidas

- **No se ha probado en un teléfono real.** Es una primera versión; probarla con calma y
  ajustar antes de dársela a tu papá.
- **El audio de los videos entra al micrófono.** Por eso existe la palabra clave. Se puede
  desactivar en la app, pero habrá comandos falsos cuando el video diga "siguiente", "pausa", etc.
- **"Me gusta" no se puede quitar** por voz: el doble toque solo da like, no lo retira.
- **Comentarios** busca el botón por su descripción de accesibilidad; si TikTok la cambia,
  puede dejar de funcionar. Los deslizamientos, pausa y like no dependen de eso.
- Algunos teléfonos emiten un pitido cada vez que el reconocedor de Google reinicia la escucha.
- El reconocimiento depende de Google; sin internet ni paquete sin conexión no entiende.

## Siguientes pasos posibles

- Reconocimiento 100 % sin conexión con Vosk y palabra de activación con Porcupine.
- Leer en voz alta el autor y la descripción del video actual.
- Compartir, seguir cuenta, buscar por voz.
- Íconos y pantalla principal más simple.
