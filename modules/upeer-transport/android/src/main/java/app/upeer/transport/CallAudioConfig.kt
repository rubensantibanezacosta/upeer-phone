package app.upeer.transport

/**
 * Contrato del audio de una llamada, con los mismos valores que usa el escritorio.
 *
 * El escritorio codifica con WebCodecs y `getAudioEncoderConfig` de `audioCodecConfig.ts` fija
 * **Opus, 48 kHz, mono y 64 kbps** para la voz (96 kbps si es el audio de compartir pantalla). El
 * móvil no puede usar WebCodecs: aquí el códec es MediaCodec, pero los parámetros tienen que ser los
 * mismos o el otro extremo decodifica ruido.
 *
 * Este objeto no toca Android a propósito: los números y las validaciones se prueban en JVM, y la
 * parte que necesita `MediaCodec`/`AudioRecord` vive en `CallAudioEngine`.
 */
internal object CallAudioConfig {
  const val MIME_TYPE = "audio/opus"
  const val SAMPLE_RATE = 48_000
  const val CHANNELS = 1
  const val VOICE_BITRATE = 64_000
  const val SCREEN_BITRATE = 96_000

  /** Cuántos milisegundos de audio entran en un frame. Opus trabaja cómodo en 20 ms. */
  const val FRAME_MILLIS = 20

  /** Muestras por frame a 48 kHz y 20 ms: 960. */
  const val SAMPLES_PER_FRAME = SAMPLE_RATE * FRAME_MILLIS / 1000

  /** Bytes de PCM 16 bits mono por frame. */
  const val BYTES_PER_FRAME = SAMPLES_PER_FRAME * 2

  fun bitrateFor(screenAudio: Boolean): Int = if (screenAudio) SCREEN_BITRATE else VOICE_BITRATE

  /**
   * El más bajo y el más alto que tiene sentido pedir. Un valor fuera de aquí no lo elige nadie, así
   * que si llega es que viene de un sitio equivocado y devolver el neutro es mejor que configurar el
   * códec con una barbaridad.
   */
  private val SAMPLE_RATES = setOf(8_000, 12_000, 16_000, 24_000, 48_000)
  private const val MIN_BITRATE = 6_000
  private const val MAX_BITRATE = 510_000

  fun sampleRateOr(default: Int = SAMPLE_RATE): Int = if (default in SAMPLE_RATES) default else SAMPLE_RATE

  fun sanitizeBitrate(value: Int, screenAudio: Boolean = false): Int {
    if (value < MIN_BITRATE || value > MAX_BITRATE) return bitrateFor(screenAudio)
    return value
  }

  /**
   * Las dos mitades de un intercambio de capacidades. El móvil anuncia esto y el escritorio decide;
   * si el escritorio pidiera un sample rate que no está en la lista, se ignora.
   */
  fun describe(): Map<String, Any> = mapOf(
    "mimeType" to MIME_TYPE,
    "sampleRate" to SAMPLE_RATE,
    "channels" to CHANNELS,
    "bitrate" to VOICE_BITRATE,
    "frameMillis" to FRAME_MILLIS,
  )
}
