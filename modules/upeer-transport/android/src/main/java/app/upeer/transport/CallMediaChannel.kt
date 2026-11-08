package app.upeer.transport

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Canal UDP para la media de una llamada.
 *
 * El escritorio manda `CALL_MEDIA` por **UDP**, no por la conexión de control (`callMediaUdp.ts`:
 * `sendCallMediaDatagram` al puerto 50006). El móvil no tenía ningún socket UDP, así que aunque la
 * llamada conectara, el audio habría llegado a un puerto cerrado y se habría perdido en silencio: se
 * oiría nada y no habría ningún error que lo explicase.
 *
 * Tres razones para que la media vaya por UDP y no por TCP:
 *
 * - TCP **reordena y reintenta**: un paquete perdido bloquea todo lo que viene detrás hasta que se
 *   recupera, y en audio eso se oye como un parón. En UDP lo perdido se tira y se sigue.
 * - La media es **desechable**: un frame de 20 ms que llega tarde ya no sirve; reproducirlo desordena.
 * - La conexión de control no puede quedarse atascada por audio: si la media monopolizara el socket,
 *   un `CALL_END` llegaría después de los frames pendientes.
 *
 * El puente Go ya expone `dialUDP`/`writeDatagram`/`read` sobre la red de Yggdrasil, así que aquí solo
 * se abre el canal, se fragmenta (un frame de vídeo no cabe en 1200 B) y se lee en su hilo.
 */
internal class CallMediaChannel(
  private val bridge: () -> BridgeBindings,
  private val onPacket: (String, ByteArray) -> Unit,
  private val now: () -> Long = System::currentTimeMillis,
) {
  /** Lo que el canal necesita del puente; así se puede falsear en pruebas. */
  interface BridgeBindings {
    fun dialUDP(address: String, port: Int): Int
    fun writeDatagram(connectionID: Int, payload: ByteArray)
    fun read(connectionID: Int, maxBytes: Int, timeoutMillis: Int): ByteArray
    fun closeConnection(connectionID: Int)
  }

  private val lock = Any()
  private val connections = ConcurrentHashMap<String, Int>()
  private var reader: Thread? = null
  private var running = false
  private var sentDatagrams = 0L
  private var receivedDatagrams = 0L
  private var droppedOversize = 0L

  fun isOpen(): Boolean = synchronized(lock) { running }

  /**
   * Abre el canal hacia un dispositivo. Devuelve false si no se pudo; el llamador decide qué hacer.
   *
   * La conexión se guarda por dirección para poder mandar y cerrar sin volver a resolverla.
   */
  fun connect(address: String): Boolean {
    if (!PeerAddress.valid(address)) return false
    if (synchronized(lock) { connections.containsKey(address) }) return true
    val id = runCatching { bridge().dialUDP(address, MEDIA_PORT) }.getOrElse { cause ->
      Log.w(TAG, "Sin canal UDP de media hacia $address: ${cause.message ?: cause.javaClass.simpleName}")
      return false
    }
    if (id <= 0) return false
    synchronized(lock) {
      connections[address] = id
      if (!running) {
        running = true
        reader = Thread({ readLoop() }, "upeer-call-media").also { it.isDaemon = true; it.start() }
      }
    }
    Log.i(TAG, "Canal UDP de media abierto hacia $address")
    return true
  }

  /**
   * Manda un paquete de media, partiéndolo si no cabe en un datagrama.
   *
   * Un frame de audio de 20 ms es pequeño y va entero; uno de vídeo no. El que manda solo trozos se
   * descarta aquí: es entrada de un límite del protocolo, no un fallo de red.
   */
  fun send(address: String, packet: ByteArray): Boolean {
    val id = synchronized(lock) { connections[address] } ?: return false
    val datagrams = runCatching { CallMediaFragments.fragment(packet) }.getOrElse {
      droppedOversize += 1
      return false
    }
    var sent = 0
    for (datagram in datagrams) {
      val ok = runCatching { bridge().writeDatagram(id, datagram) }.isSuccess
      if (ok) {
        sent += 1
        sentDatagrams += 1
      }
    }
    return sent == datagrams.size
  }

  private fun readLoop() {
    while (synchronized(lock) { running }) {
      val ids = synchronized(lock) { connections.toMap() }
      if (ids.isEmpty()) {
        // Sin destinos: se espera un poco y se vuelve a mirar, en vez de girar en vacío quemando CPU.
        Thread.sleep(IDLE_POLL_MILLIS)
        continue
      }
      var gotSomething = false
      for ((address, connectionID) in ids) {
        val datagram = runCatching { bridge().read(connectionID, MAX_DATAGRAM_BYTES, READ_TIMEOUT_MILLIS) }.getOrNull()
        if (datagram == null || datagram.isEmpty()) continue
        gotSomething = true
        receivedDatagrams += 1
        val packet = CallMediaFragments.reassemble(datagram, now()) ?: continue
        runCatching { onPacket(address, packet) }
      }
      if (!gotSomething) Thread.sleep(IDLE_POLL_MILLIS)
    }
  }

  /** Cierra el canal. Idempotente: se llama también desde rutas de error y al colgar. */
  fun close() {
    val open: List<Int>
    synchronized(lock) {
      if (!running && connections.isEmpty()) return
      running = false
      open = connections.values.toList()
      connections.clear()
      reader = null
    }
    CallMediaFragments.clear()
    for (id in open) runCatching { bridge().closeConnection(id) }
    Log.i(TAG, "Canal UDP de media cerrado: enviados=$sentDatagrams recibidos=$receivedDatagrams")
  }

  fun summary(): String {
    val devices = synchronized(lock) { connections.size }
    return "udp=${if (isOpen()) "abierto" else "cerrado"} dispositivos=$devices " +
      "enviados=$sentDatagrams recibidos=$receivedDatagrams descartados=$droppedOversize"
  }

  companion object {
    private const val TAG = "UPeerTransport"

    /** Mismo puerto que `CALL_MEDIA_UDP_PORT` del escritorio. */
    const val MEDIA_PORT = 50006

    /** Tope de un datagrama entrante: el mismo que usa el escritorio al fragmentar. */
    const val MAX_DATAGRAM_BYTES = 1200

    private const val READ_TIMEOUT_MILLIS = 500
    private const val IDLE_POLL_MILLIS = 100L
  }
}
