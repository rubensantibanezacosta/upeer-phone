package app.upeer.transport.vault

import app.upeer.transport.dht.DhtLocation
import org.json.JSONObject

internal data class VaultEntry(
  val id: Long,
  val payloadHash: String,
  val recipientSid: String,
  val recipientDeviceId: String?,
  val senderSid: String,
  val priority: Int,
  val data: String,
  val expiresAt: Long,
)

internal sealed class VaultStoreResult {
  data class Accepted(val entry: VaultEntry) : VaultStoreResult()
  data class Rejected(val reason: String) : VaultStoreResult()
}

internal object VaultStoreRequest {
  const val MAX_HASH_LENGTH = 200
  const val MAX_DATA_LENGTH = 150_000
  const val MAX_PRIORITY = 3

  fun parse(
    packet: JSONObject,
    authenticatedSender: String,
    nowMillis: Long,
    ttlMillis: Long = VaultStoreEnvelope.TTL_MILLIS,
  ): VaultStoreResult {
    if (packet.optString("type") != "VAULT_STORE") return reject("tipo inesperado")
    val payloadHash = packet.optString("payloadHash")
    if (payloadHash.isEmpty() || payloadHash.length > MAX_HASH_LENGTH) return reject("hash inválido")
    if (!isHex(payloadHash)) return reject("hash no hexadecimal")
    val recipient = packet.optString("recipientSid")
    if (recipient.isEmpty() || recipient.length > 64) return reject("destinatario inválido")
    if (recipient != WILDCARD && !DhtLocation.isHex(recipient, 32)) return reject("destinatario no hexadecimal")
    val device = packet.optString("recipientDeviceId")
    if (packet.has("recipientDeviceId") && !DhtLocation.isHex(device, 32)) return reject("dispositivo destino inválido")
    val data = packet.optString("data")
    if (data.isEmpty()) return reject("sin datos")
    if (data.length > MAX_DATA_LENGTH) return reject("datos demasiado grandes")
    if (!isHex(data)) return reject("datos no hexadecimales")
    val priority = if (packet.has("priority")) packet.optInt("priority", -1) else VaultStoreEnvelope.PRIORITY_MESSAGE
    if (priority < VaultStoreEnvelope.PRIORITY_CHAT || priority > MAX_PRIORITY) return reject("prioridad fuera de rango")
    val sender = authenticatedSender.lowercase()
    if (!DhtLocation.isHex(sender, 32)) return reject("remitente autenticado inválido")
    val requested = packet.optLong("expiresAt", 0L)
    val ceiling = nowMillis + ttlMillis.coerceAtMost(VaultStoreEnvelope.TTL_MILLIS)
    val expiresAt = if (requested <= 0L) ceiling else minOf(requested, ceiling)
    if (expiresAt <= nowMillis) return reject("ya caducado")
    return VaultStoreResult.Accepted(
      VaultEntry(
        id = 0L,
        payloadHash = payloadHash.lowercase(),
        recipientSid = recipient.lowercase(),
        recipientDeviceId = if (device.isEmpty()) null else device.lowercase(),
        senderSid = sender,
        priority = priority,
        data = data.lowercase(),
        expiresAt = expiresAt,
      ),
    )
  }

  private fun reject(reason: String): VaultStoreResult = VaultStoreResult.Rejected(reason)

  private fun isHex(value: String): Boolean =
    value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

  const val WILDCARD = "*"
}
