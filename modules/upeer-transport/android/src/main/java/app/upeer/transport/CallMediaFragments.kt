package app.upeer.transport

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Fragmentación y reensamblado de `CALL_MEDIA` sobre UDP, igual que `callMediaFragments.ts` del
 * escritorio.
 *
 * La media no puede ir por la conexión de control: si un paquete de audio se pierde, TCP lo reintenta
 * e **ordena la espera**, así que una pérdida puntual se convierte en un parón audible para todo lo que
 * viene detrás. Por UDP lo que llega tarde simplemente se tira, que es lo que quiere un oído.
 *
 * Un frame de audio cabe en un datagrama, pero uno de vídeo no: el tope de aquí es **1200 bytes** por
 * datagrama (el MTU seguro) y los paquetes grandes se parten. La cabecera de fragmento es de 28 bytes:
 *
 * | offset | tamaño | campo                                                  |
 * |--------|--------|--------------------------------------------------------|
 * | 0..3   | 4      | mágica `0x55 0x43 0x46 0x31` («UCF1»)                  |
 * | 4..19  | 16     | identificador del paquete (común a todos sus trozos)   |
 * | 20..21 | 2      | índice del trozo, `uint16` **big endian**              |
 * | 22..23 | 2      | total de trozos, `uint16` **big endian**               |
 * | 24..27 | 4      | bytes totales del paquete, `uint32` **big endian**     |
 * | 28..   | resto  | trozo del paquete                                      |
 *
 * Todo en big endian porque el escritorio usa `writeUInt16BE`/`writeUInt32BE`, y Android es little
 * endian por naturaleza.
 *
 * Un datagrama que **no** empieza por la mágica se devuelve tal cual: un paquete que cabe entero no se
 * fragmenta, así que el receptor tiene que aceptar los dos casos.
 */
internal object CallMediaFragments {
  val FRAGMENT_MAGIC = byteArrayOf(0x55, 0x43, 0x46, 0x31)
  const val FRAGMENT_HEADER_BYTES = 28
  const val MAX_FRAGMENT_COUNT = 512
  const val MAX_PACKET_BYTES = 600_000

  /** Tamaño de datagrama seguro para no pasar del MTU y que no lo parta la red por debajo. */
  const val MAX_DATAGRAM_BYTES = 1200

  const val MAX_FRAGMENT_PAYLOAD_BYTES = MAX_DATAGRAM_BYTES - FRAGMENT_HEADER_BYTES

  /** Cuántos paquetes a medio montar se guardan a la vez, y cuánto viven. */
  const val MAX_ASSEMBLIES = 64
  const val ASSEMBLY_TTL_MS = 5_000L

  private class Assembly(val count: Int, val totalBytes: Int) {
    val parts = HashMap<Int, ByteArray>()
    var receivedBytes = 0
    var lastAt = 0L
  }

  private val assemblies = LinkedHashMap<String, Assembly>()
  private val lock = Any()

  /**
   * Parte un paquete en datagramas. Si cabe entero se devuelve un único elemento sin cabecera, que es
   * lo que espera el receptor.
   */
  fun fragment(packet: ByteArray, maxDatagramBytes: Int = MAX_DATAGRAM_BYTES, nowMillis: Long = System.currentTimeMillis()): List<ByteArray> {
    require(maxDatagramBytes <= MAX_DATAGRAM_BYTES) { "el datagrama no puede pasar de $MAX_DATAGRAM_BYTES B" }
    if (packet.size <= maxDatagramBytes) return listOf(packet)
    val payloadBytes = maxDatagramBytes - FRAGMENT_HEADER_BYTES
    require(payloadBytes > 0) { "datagrama demasiado pequeño para llevar cabecera" }
    require(packet.size <= MAX_PACKET_BYTES) { "el paquete pasa de $MAX_PACKET_BYTES B" }
    val count = (packet.size + payloadBytes - 1) / payloadBytes
    require(count <= MAX_FRAGMENT_COUNT) { "el paquete necesita $count trozos, más de $MAX_FRAGMENT_COUNT" }
    val id = ByteArray(16)
    java.security.SecureRandom().nextBytes(id)
    val out = ArrayList<ByteArray>(count)
    for (index in 0 until count) {
      val start = index * payloadBytes
      val end = minOf(start + payloadBytes, packet.size)
      val part = packet.copyOfRange(start, end)
      val fragment = ByteArray(FRAGMENT_HEADER_BYTES + part.size)
      System.arraycopy(FRAGMENT_MAGIC, 0, fragment, 0, 4)
      System.arraycopy(id, 0, fragment, 4, 16)
      val header = ByteBuffer.wrap(fragment, 0, FRAGMENT_HEADER_BYTES).order(ByteOrder.BIG_ENDIAN)
      header.putShort(20, index.toShort())
      header.putShort(22, count.toShort())
      header.putInt(24, packet.size)
      System.arraycopy(part, 0, fragment, FRAGMENT_HEADER_BYTES, part.size)
      out.add(fragment)
    }
    return out
  }

  /**
   * Reensambla un datagrama.
   *
   * - Un datagrama sin mágica se devuelve entero (es un paquete que cabía, o algo que no es nuestro; el
   *   que llama ya valida el sobre de media).
   * - Devuelve null mientras falten trozos, y también si el datagrama es incoherente: es entrada de red
   *   y no puede tumbar la llamada ni reservar memoria a lo loco.
   */
  fun reassemble(datagram: ByteArray, nowMillis: Long = System.currentTimeMillis()): ByteArray? {
    if (!isFragment(datagram)) return datagram
    if (datagram.size <= FRAGMENT_HEADER_BYTES) return null
    val header = ByteBuffer.wrap(datagram, 0, FRAGMENT_HEADER_BYTES).order(ByteOrder.BIG_ENDIAN)
    val id = datagram.copyOfRange(4, 20).joinToString("") { "%02x".format(it) }
    val index = header.getShort(20).toInt() and 0xFFFF
    val count = header.getShort(22).toInt() and 0xFFFF
    val totalBytes = header.getInt(24).toLong() and 0xFFFFFFFFL
    val part = datagram.copyOfRange(FRAGMENT_HEADER_BYTES, datagram.size)
    if (count < 2 || count > MAX_FRAGMENT_COUNT) return null
    if (index >= count) return null
    if (totalBytes > MAX_PACKET_BYTES) return null
    // Coherencia entre el tamaño declarado y el número de trozos: evita reservar de más por un
    // datagrama mentiroso.
    if (totalBytes <= (count - 1).toLong() * MAX_FRAGMENT_PAYLOAD_BYTES) return null
    if (totalBytes > count.toLong() * MAX_FRAGMENT_PAYLOAD_BYTES) return null
    if (part.size > MAX_FRAGMENT_PAYLOAD_BYTES) return null

    synchronized(lock) {
      expire(nowMillis)
      var assembly = assemblies[id]
      if (assembly == null) {
        // Tope de conjuntos vivos: se tira el más antiguo en vez de crecer sin freno.
        if (assemblies.size >= MAX_ASSEMBLIES) {
          assemblies.keys.firstOrNull()?.let { assemblies.remove(it) }
        }
        assembly = Assembly(count, totalBytes.toInt())
        assemblies[id] = assembly
      }
      if (assembly.count != count || assembly.totalBytes != totalBytes.toInt()) {
        assemblies.remove(id)
        return null
      }
      assembly.lastAt = nowMillis
      if (!assembly.parts.containsKey(index)) {
        if (assembly.receivedBytes + part.size > assembly.totalBytes) {
          assemblies.remove(id)
          return null
        }
        assembly.parts[index] = part
        assembly.receivedBytes += part.size
      }
      if (assembly.parts.size != count) return null
      if (assembly.receivedBytes != assembly.totalBytes) {
        assemblies.remove(id)
        return null
      }
      val out = ByteArray(assembly.totalBytes)
      var offset = 0
      for (partIndex in 0 until count) {
        val chunk = assembly.parts[partIndex] ?: return null
        System.arraycopy(chunk, 0, out, offset, chunk.size)
        offset += chunk.size
      }
      assemblies.remove(id)
      return if (out.size == assembly.totalBytes) out else null
    }
  }

  fun isFragment(datagram: ByteArray): Boolean {
    if (datagram.size < FRAGMENT_MAGIC.size) return false
    for (index in FRAGMENT_MAGIC.indices) if (datagram[index] != FRAGMENT_MAGIC[index]) return false
    return true
  }

  /** Tira los conjuntos que llevan demasiado a medias: por UDP un trozo perdido no llega nunca. */
  fun expire(nowMillis: Long = System.currentTimeMillis()) {
    synchronized(lock) {
      val stale = assemblies.filterValues { nowMillis - it.lastAt > ASSEMBLY_TTL_MS }.keys
      for (key in stale) assemblies.remove(key)
    }
  }

  /** Solo para pruebas y para cortar una llamada: deja de haber paquetes a medio montar. */
  fun clear() {
    synchronized(lock) { assemblies.clear() }
  }

  fun pendingCount(): Int = synchronized(lock) { assemblies.size }
}
