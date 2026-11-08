package app.upeer.transport

import android.util.Log
import org.json.JSONObject

/**
 * Une el motor de audio con el transporte: captura, envía, recibe y reproduce.
 *
 * Antes, `CallAudioEngine` y `CallMediaPacket` existían por separado y **nadie los llamaba**: la
 * llamada se quedaba en «El audio todavía no está disponible en este cliente». Esto es el pegamento.
 *
 * Tres reglas que se siguen aquí y que no son obvias:
 *
 * - **Un motor por llamada**, creado al conectar y soltado al colgar. Reutilizarlo entre llamadas deja
 *   un `AudioRecord` apuntando al micrófono de la anterior.
 * - **El envío no espera a la red.** `sendFrame` lo llama el hilo de captura; si bloqueara, el
 *   micrófono se llenaría y el audio saldría con retardo creciente. El transporte encola.
 * - **La media no se manda si la llamada no está conectada.** Mandar antes de aceptar llena el buffer
 *   del otro extremo de audio que no toca.
 */
/**
 * Lo mínimo que el coordinador necesita de un motor de audio.
 *
 * Existe para poder probar el pegamento sin micrófono ni códec, que en JVM no se pueden abrir.
 */
internal interface CallMediaSink {
  fun startCapture()
  fun startPlayback()
  fun playFrame(encoded: ByteArray)
  fun setMuted(value: Boolean)
  fun stop()
}

internal class CallMediaCoordinator(
  private val now: () -> Long = System::currentTimeMillis,
  private val createEngine: (sendFrame: (ByteArray) -> Unit) -> CallMediaSink = { CallAudioEngine(it) },
) {
  private val lock = Any()
  private var engine: CallMediaSink? = null
  private var state: CallAudioState? = null
  private var callId: String? = null
  private val sentFrames = java.util.concurrent.atomic.AtomicLong()
  private val receivedFrames = java.util.concurrent.atomic.AtomicLong()

  /** Abre la media de una llamada. Devuelve false si ya había una o si el dispositivo no puede. */
  fun start(
    session: CallSession,
    send: (JSONObject) -> Unit,
  ): Boolean {
    synchronized(lock) {
      if (engine != null) return true
      callId = session.callId
      sentFrames.set(0)
      receivedFrames.set(0)
      val state = CallAudioState(
        open = {
          val created = createEngine { frame -> outbound(frame, session, send) }
          created.startCapture()
          try {
            created.startPlayback()
          } catch (cause: Exception) {
            // Sin reproducción al menos se puede hablar; se deja dicho y no se tumba la llamada.
            Log.w(TAG, "Sin reproducción de audio: ${cause.message ?: cause.javaClass.simpleName}")
          }
          engine = created
        },
        close = {
          engine?.stop()
          engine = null
        },
      )
      this.state = state
      val opened = state.start(now())
      if (!opened) {
        Log.w(TAG, "No se pudo abrir la media de ${session.callId}: ${state.failure()}")
        this.state = null
      }
      return opened
    }
  }

  private fun outbound(frame: ByteArray, session: CallSession, send: (JSONObject) -> Unit) {
    if (frame.size > CallMediaPacket.MAX_PACKET_BYTES) {
      Log.w(TAG, "Frame de audio descartado por tamaño: ${frame.size} B")
      return
    }
    // El motor entrega el frame ya codificado por `CallMediaFrames`; el sobre del transporte se arma
    // aquí para que el motor no tenga que saber nada de paquetes.
    val metadata = JSONObject()
      .put("type", CallPackets.MEDIA)
      .put("callId", session.callId)
      .put("timestamp", now())
    val frameBytes = CallMediaPacket.encode(metadata, frame)
    metadata.put("data", frameBytes)
    send(metadata)
    sentFrames.incrementAndGet()
    state?.onFrameSent()
  }

  /** Un `CALL_MEDIA` que llegó. El motor lo decodifica y lo reproduce. */
  fun onInboundPacket(packet: JSONObject): Boolean {
    val current = synchronized(lock) { engine }
    if (current == null) {
      // Llega media de una llamada que no está conectada: se descarta. Reproducirla antes de aceptar
      // haría sonar al usuario lo de una llamada que aún no ha cogido.
      return false
    }
    val raw = packet.opt("data")
    val bytes = when (raw) {
      is ByteArray -> raw
      is String -> runCatching { android.util.Base64.decode(raw, android.util.Base64.DEFAULT) }.getOrNull()
      else -> null
    }
    if (bytes == null || bytes.isEmpty()) return false
    val frame = CallMediaFrames.decode(bytes) ?: return false
    val state = synchronized(lock) { state } ?: return false
    if (!state.onFrameReceived(frame.sequence)) return false
    current.playFrame(bytes)
    receivedFrames.incrementAndGet()
    return true
  }

  fun setMuted(muted: Boolean) {
    synchronized(lock) { engine }?.setMuted(muted)
  }

  fun stop() {
    synchronized(lock) {
      state?.stop()
      engine = null
      state = null
      callId = null
    }
  }

  fun live(): Boolean = synchronized(lock) { state?.isLive() == true }

  fun sent(): Long = sentFrames.get()

  fun received(): Long = receivedFrames.get()

  fun summary(): String {
    val current = synchronized(lock) { state }
    return "media=${if (live()) "activa" else "parada"} enviados=${sentFrames.get()} recibidos=${receivedFrames.get()}" +
      (current?.failure()?.let { " error=$it" } ?: "")
  }

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
