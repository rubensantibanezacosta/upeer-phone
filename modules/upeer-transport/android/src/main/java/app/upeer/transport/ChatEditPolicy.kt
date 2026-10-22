package app.upeer.transport

import org.json.JSONObject

internal object ChatEditPolicy {
  data class Plan(val text: String, val version: Int, val pending: Boolean)

  fun plan(message: JSONObject, upeerId: String, messageID: String, content: String): Plan {
    val text = text(content)
    requireEditable(message, upeerId, messageID)
    return Plan(text, nextVersion(message), message.optString("status") == "pending")
  }

  private fun text(content: String): String {
    val text = content.trim()
    if (text.isEmpty()) throw IllegalArgumentException("El mensaje está vacío")
    if (text.toByteArray(Charsets.UTF_8).size > MAX_MESSAGE_BYTES) {
      throw IllegalArgumentException("El mensaje es demasiado grande")
    }
    return text
  }

  fun requireEditable(message: JSONObject, upeerId: String, messageID: String) {
    if (message.optString("id") != messageID) throw IllegalArgumentException("El mensaje no coincide")
    if (message.optString("upeerId") != upeerId || !message.optBoolean("isMine")) {
      throw IllegalArgumentException("Solo se pueden editar mensajes propios")
    }
    if (message.optString("status") == "failed") {
      throw IllegalArgumentException("No se puede editar un mensaje fallido")
    }
  }

  fun nextVersion(message: JSONObject): Int = message.optInt("version", 0).coerceAtLeast(1) + 1

  private const val MAX_MESSAGE_BYTES = 256 * 1024
}
