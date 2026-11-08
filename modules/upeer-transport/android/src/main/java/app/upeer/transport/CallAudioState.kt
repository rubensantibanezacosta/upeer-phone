package app.upeer.transport

/**
 * Estado del motor de audio, sin tocar Android.
 *
 * La parte que habla con `AudioRecord`/`MediaCodec` es un bucle que corre en su propio hilo y no se
 * puede probar en JVM sin un dispositivo. Lo que sí se puede probar —y es donde de verdad se rompe
 * una llamada— es el control: cuándo se abre el micrófono, qué pasa si el `start` falla, que el
 * cierre sea idempotente, y que un frame con la secuencia repetida no se cuele dos veces.
 *
 * Todo el acceso va con un candado porque lo llaman tres sitios a la vez: el hilo de la llamada, el
 * del códec y el manejador de paquetes.
 */
internal class CallAudioState(
  private val open: () -> Unit = {},
  private val close: () -> Unit = {},
) {
  enum class Mode { IDLE, CAPTURING, PLAYING }

  private val lock = Any()
  private var mode = Mode.IDLE
  private var failure: String? = null
  private var framesSent = 0L
  private var framesReceived = 0L
  private var droppedOutOfOrder = 0L
  private var lastSequence = -1L
  private var lastFailureAt = 0L

  fun mode(): Mode = synchronized(lock) { mode }

  fun isLive(): Boolean = synchronized(lock) { mode != Mode.IDLE }

  fun failure(): String? = synchronized(lock) { failure }

  fun framesSent(): Long = synchronized(lock) { framesSent }

  fun framesReceived(): Long = synchronized(lock) { framesReceived }

  fun droppedOutOfOrder(): Long = synchronized(lock) { droppedOutOfOrder }

  /**
   * Abre la captura y la reproducción. Devuelve false si no se pudo, con el motivo guardado para que
   * la UI pueda decir algo distinto de «no se oye».
   *
   * Si el motor ya estaba abierto devuelve true sin volver a abrirlo: la llamada puede pedir "empieza
   * a sonar" más de una vez (al aceptar, al recuperar el foco) y abrir dos veces el micrófono deja un
   * `AudioRecord` huérfano que sigue gastando batería hasta que muere el proceso.
   */
  fun start(nowMillis: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
    if (mode != Mode.IDLE) return@synchronized true
    val result = runCatching { open() }
    if (result.isFailure) {
      failure = result.exceptionOrNull()?.message ?: result.exceptionOrNull()?.javaClass?.simpleName ?: "error desconocido"
      lastFailureAt = nowMillis
      mode = Mode.IDLE
      return@synchronized false
    }
    mode = Mode.CAPTURING
    failure = null
    framesSent = 0L
    framesReceived = 0L
    droppedOutOfOrder = 0L
    lastSequence = -1L
    true
  }

  /** Cierra lo que esté abierto. Idempotente: llamarlo dos veces no es un error. */
  fun stop() {
    synchronized(lock) {
      if (mode == Mode.IDLE) return
      mode = Mode.IDLE
    }
    // Fuera del candado: cerrar el códec puede tardar y no puede bloquear al que pregunta el estado.
    runCatching { close() }
  }

  fun onFrameSent() {
    synchronized(lock) { if (mode != Mode.IDLE) framesSent += 1 }
  }

  /**
   * Un frame entrante. Devuelve false si se descarta por ir hacia atrás en la secuencia.
   *
   * La media viaja por UDP, que no garantiza orden y puede duplicar. Reproducir un frame viejo produce
   * un chasquido y uno repetido alarga el buffer sin aportar nada, así que se descartan. La primera
   * secuencia que llega siempre se acepta, porque al empezar no hay referencia de dónde estamos.
   */
  fun onFrameReceived(sequence: Long): Boolean = synchronized(lock) {
    if (mode == Mode.IDLE) return@synchronized false
    if (lastSequence >= 0 && sequence <= lastSequence) {
      droppedOutOfOrder += 1
      return@synchronized false
    }
    lastSequence = sequence
    framesReceived += 1
    true
  }

  fun lastFailureAt(): Long = synchronized(lock) { lastFailureAt }

  fun summary(): String =
    "audio=${if (isLive()) "activo" else "parado"} enviados=$framesSent recibidos=$framesReceived " +
      "descartados=$droppedOutOfOrder ${failure?.let { "error=$it" } ?: ""}".trim()
}
