package app.upeer.transport

import android.util.Base64
import android.util.Log
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

internal object YggdrasilPayloadCodec {
  fun decompress(value: ByteArray): String {
    val text = String(value, Charsets.UTF_8)
    if (!text.startsWith(COMPRESSION_PREFIX)) return text
    return try {
      val compressed = Base64.decode(text.substring(COMPRESSION_PREFIX.length), Base64.DEFAULT)
      GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo descomprimir el mensaje: ${message(cause)}")
      text
    }
  }

  fun hexToBytes(value: String): ByteArray {
    if (value.length % 2 != 0) throw IllegalArgumentException("invalid plaintext hex")
    return ByteArray(value.length / 2) { index ->
      val high = Character.digit(value[index * 2], 16)
      val low = Character.digit(value[index * 2 + 1], 16)
      if (high < 0 || low < 0) throw IllegalArgumentException("invalid plaintext hex")
      ((high shl 4) or low).toByte()
    }
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private const val TAG = "UPeerTransport"
  private const val COMPRESSION_PREFIX = "\u2763UP\u2763"
}
