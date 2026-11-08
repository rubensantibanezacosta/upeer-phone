package app.upeer.transport

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Frame de media de una llamada, con el mismo formato binario que usa el escritorio.
 *
 * Cabecera de 16 bytes, idéntica a `encodeMediaFrame` de `features/call/mediaChunker.ts`:
 *
 * | offset | tamaño | campo                                                        |
 * |--------|--------|--------------------------------------------------------------|
 * | 0      | 1      | tipo: 0 audio, 1 vídeo, 2 pantalla                           |
 * | 1      | 1      | 1 si es fotograma clave, 0 si no                             |
 * | 2..3   | 2      | reservado (ceros)                                            |
 * | 4..11  | 8      | marca de tiempo en milisegundos, float64 **big endian**      |
 * | 12..15 | 4      | número de secuencia, uint32 **big endian**                   |
 *
 * El escritorio escribe esa cabecera con un `DataView` de JavaScript, que usa **big endian** por
 * defecto. Leerla aquí como little endian (el orden nativo de ARM, y el que pone `ByteOrder.LITTLE_ENDIAN`
 * por descuido) daría marcas de tiempo absurdas sin fallar en ningún sitio: el frame se decodifica, se
 * pinta negro y nadie sabe por qué. Todo va explícito en big endian y hay pruebas con vectores fijos.
 *
 * El codec en sí (capturar audio y vídeo) no puede ser el del escritorio: allí es WebCodecs, que es una
 * API del navegador y no existe en Android. Aquí se usará MediaCodec, pero el frame que sale por el
 * cable tiene que ser byte a byte el mismo, o los dos clientes no se oyen ni se ven.
 */
internal object CallMediaFrames {
  const val HEADER_BYTES = 16
  const val KIND_AUDIO = 0
  const val KIND_VIDEO = 1
  const val KIND_SCREEN = 2

  /** Tope de un frame ya codificado. Un keyframe de vídeo a 720p ronda las decenas de KB. */
  const val MAX_FRAME_BYTES = 512 * 1024

  data class Frame(val kind: Int, val keyFrame: Boolean, val timestamp: Double, val sequence: Long, val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Frame) return false
      return kind == other.kind &&
        keyFrame == other.keyFrame &&
        timestamp == other.timestamp &&
        sequence == other.sequence &&
        data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
      var result = kind
      result = 31 * result + keyFrame.hashCode()
      result = 31 * result + timestamp.hashCode()
      result = 31 * result + sequence.hashCode()
      result = 31 * result + data.contentHashCode()
      return result
    }
  }

  fun encode(kind: Int, keyFrame: Boolean, timestampMillis: Double, sequence: Long, data: ByteArray): ByteArray {
    requireValidKind(kind)
    val header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.BIG_ENDIAN)
    header.put(0, kind.toByte())
    header.put(1, (if (keyFrame) 1 else 0).toByte())
    header.putShort(2, 0)
    header.putDouble(4, timestampMillis)
    header.putInt(12, sequence.toInt())
    val out = ByteArray(HEADER_BYTES + data.size)
    System.arraycopy(header.array(), 0, out, 0, HEADER_BYTES)
    System.arraycopy(data, 0, out, HEADER_BYTES, data.size)
    return out
  }

  /**
   * Devuelve null en vez de lanzar: esto lo alimenta un par remoto y un frame inválido no puede tumbar
   * la llamada. Se descarta y se sigue, igual que hace `decodeMediaFrame` en el escritorio.
   */
  fun decode(encoded: ByteArray): Frame? {
    if (encoded.size < HEADER_BYTES) return null
    val header = ByteBuffer.wrap(encoded, 0, HEADER_BYTES).order(ByteOrder.BIG_ENDIAN)
    val kind = header.get(0).toInt()
    val typeByte = header.get(1).toInt()
    if (kind !in KIND_AUDIO..KIND_SCREEN) return null
    if (typeByte != 0 && typeByte != 1) return null
    val timestamp = header.getDouble(4)
    if (!timestamp.isFinite()) return null
    val sequence = header.getInt(12).toLong() and 0xFFFFFFFFL
    val data = ByteArray(encoded.size - HEADER_BYTES)
    System.arraycopy(encoded, HEADER_BYTES, data, 0, data.size)
    if (data.isEmpty()) return null
    return Frame(kind, typeByte == 1, timestamp, sequence, data)
  }

  fun requireValidKind(kind: Int) {
    require(kind in KIND_AUDIO..KIND_SCREEN) { "Tipo de media desconocido: $kind" }
  }
}
