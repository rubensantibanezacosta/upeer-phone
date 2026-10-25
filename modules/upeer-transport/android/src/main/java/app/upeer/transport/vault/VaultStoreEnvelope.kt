package app.upeer.transport.vault

import app.upeer.transport.dht.DhtLocation
import org.json.JSONObject
import java.security.MessageDigest

internal object VaultStoreEnvelope {
  const val TTL_MILLIS = 60L * 24L * 60L * 60L * 1000L

  const val PRIORITY_CHAT = 1
  const val PRIORITY_MESSAGE = 2
  const val PRIORITY_CONTROL = 3

  private val CONTROL_TYPES = setOf("CHAT_CLEAR_ALL", "CHAT_DELETE")

  data class Envelope(val payloadHash: String, val json: String)

  fun priority(type: String): Int = when {
    CONTROL_TYPES.contains(type) -> PRIORITY_CONTROL
    type == "CHAT" -> PRIORITY_CHAT
    else -> PRIORITY_MESSAGE
  }

  fun build(
    packetJSON: String,
    recipientUpeerId: String,
    senderUpeerId: String,
    recipientDeviceId: String? = null,
    nowMillis: Long,
    ttlMillis: Long = TTL_MILLIS,
  ): Envelope {
    if (packetJSON.isBlank()) throw IllegalArgumentException("El paquete a custodiar está vacío")
    val packet = runCatching { JSONObject(packetJSON) }
      .getOrElse { throw IllegalArgumentException("El paquete a custodiar no es JSON válido") }
    val type = packet.optString("type")
    if (type.isBlank()) throw IllegalArgumentException("El paquete a custodiar no declara tipo")
    val recipient = recipientUpeerId.lowercase()
    if (!DhtLocation.isHex(recipient, 32)) throw IllegalArgumentException("El destinatario no es un uPeer ID válido")
    val sender = senderUpeerId.lowercase()
    if (!DhtLocation.isHex(sender, 32)) throw IllegalArgumentException("El remitente no es un uPeer ID válido")
    val device = recipientDeviceId?.lowercase()?.takeIf(String::isNotEmpty)
    if (device != null && !DhtLocation.isHex(device, 32)) throw IllegalArgumentException("El dispositivo destino no es válido")
    if (ttlMillis <= 0L) throw IllegalArgumentException("El tiempo de vida debe ser positivo")
    val expiresAt = nowMillis + ttlMillis.coerceAtMost(TTL_MILLIS)
    val bytes = packetJSON.toByteArray(Charsets.UTF_8)
    val payloadHash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val json = JSONObject()
      .put("type", "VAULT_STORE")
      .put("payloadHash", payloadHash)
      .put("recipientSid", recipient)
      .put("senderSid", sender)
      .put("priority", priority(type))
      .put("data", bytes.joinToString("") { "%02x".format(it) })
      .put("expiresAt", expiresAt)
      .apply { if (device != null) put("recipientDeviceId", device) }
    return Envelope(payloadHash, json.toString())
  }
}
