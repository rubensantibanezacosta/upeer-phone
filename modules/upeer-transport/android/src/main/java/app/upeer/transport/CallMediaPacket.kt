package app.upeer.transport

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Sobre binario de `CALL_MEDIA`, igual que `binaryCallMedia.ts` del escritorio.
 *
 * La media no viaja como JSON con el frame en base64: eso hincharía cada paquete un 33 % y el audio de
 * una llamada son cientos de paquetes por minuto. El sobre es:
 *
 * | offset | tamaño      | campo                                                     |
 * |--------|-------------|-----------------------------------------------------------|
 * | 0..3   | 4           | `0x55 0x43 0x4d 0x31` («UCM1»)                             |
 * | 4..7   | 4           | longitud de los metadatos, `uint32` **big endian**         |
 * | 8..    | `longitud`  | metadatos en JSON UTF-8, sin el campo `data`               |
 * | resto  | lo que sobre| el frame de media en crudo                                 |
 *
 * Los metadatos llevan `mediaHash` (SHA-256 en hexadecimal del frame) y el receptor lo comprueba: un
 * frame corrupto se descarta en vez de reproducirse como ruido.
 *
 * Igual que en `CallMediaFrames`, los enteros van explícitos en **big endian**, que es lo que escribe
 * `writeUInt32BE` en el escritorio; el orden nativo de Android es el contrario.
 */
internal object CallMediaPacket {
  val MAGIC = byteArrayOf(0x55, 0x43, 0x4D, 0x31)
  const val HEADER_BYTES = 8

  /** Tope por paquete: el escritorio corta en 600 000 B por la fragmentación de UDP. */
  const val MAX_PACKET_BYTES = 600_000

  private const val HEX = "0123456789abcdef"

  fun hash(data: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(data)
    val builder = StringBuilder(digest.size * 2)
    for (byte in digest) {
      val value = byte.toInt() and 0xFF
      builder.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
    }
    return builder.toString()
  }

  /** Construye el sobre. [metadata] ya debe traer `mediaHash`; si falta se añade aquí. */
  fun encode(metadata: JSONObject, data: ByteArray): ByteArray {
    val copy = JSONObject(metadata.toString())
    copy.remove("data")
    if (!copy.has("mediaHash")) copy.put("mediaHash", hash(data))
    val metadataBytes = copy.toString().toByteArray(Charsets.UTF_8)
    val header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.BIG_ENDIAN)
    header.put(MAGIC)
    header.putInt(metadataBytes.size)
    val out = ByteArrayOutputStream(HEADER_BYTES + metadataBytes.size + data.size)
    out.write(header.array())
    out.write(metadataBytes)
    out.write(data)
    return out.toByteArray()
  }

  /**
   * Devuelve los metadatos con `data` dentro, o null si el sobre no es válido o el hash no cuadra.
   *
   * Se comprueba el hash aquí y no al reproducir: un frame corrupto que llegue al decodificador puede
   * dejarlo en un estado raro, y descartarlo antes es más barato que recuperarlo después.
   */
  fun decode(frame: ByteArray): JSONObject? {
    if (frame.size < HEADER_BYTES) return null
    for (index in MAGIC.indices) if (frame[index] != MAGIC[index]) return null
    val header = ByteBuffer.wrap(frame, 0, HEADER_BYTES).order(ByteOrder.BIG_ENDIAN)
    val metadataLength = header.getInt(4)
    if (metadataLength <= 0) return null
    if (metadataLength > frame.size - HEADER_BYTES) return null
    val metadata = runCatching {
      JSONObject(String(frame, HEADER_BYTES, metadataLength, Charsets.UTF_8))
    }.getOrNull() ?: return null
    if (metadata.optString("type") != CallPackets.MEDIA) return null
    val expected = metadata.optString("mediaHash")
    if (expected.length != 64) return null
    val data = frame.copyOfRange(HEADER_BYTES + metadataLength, frame.size)
    if (data.isEmpty()) return null
    if (!expected.equals(hash(data), ignoreCase = true)) return null
    metadata.put("data", data)
    return metadata
  }

  fun isBinary(frame: ByteArray): Boolean {
    if (frame.size < MAGIC.size) return false
    for (index in MAGIC.indices) if (frame[index] != MAGIC[index]) return false
    return true
  }
}
