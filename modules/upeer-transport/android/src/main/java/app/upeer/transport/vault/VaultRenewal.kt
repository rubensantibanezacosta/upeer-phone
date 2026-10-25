package app.upeer.transport.vault

import org.json.JSONObject

internal data class VaultRenewalRequest(val payloadHash: String, val expiresAt: Long)

internal sealed class VaultRenewalResult {
  data class Accepted(val request: VaultRenewalRequest, val entry: VaultEntry) : VaultRenewalResult()
  data class Rejected(val reason: String) : VaultRenewalResult()
}

internal object VaultRenewal {
  const val HASH_LENGTH = 64

  fun parse(
    packet: JSONObject,
    authenticatedSender: String,
    entry: VaultEntry?,
    nowMillis: Long,
  ): VaultRenewalResult {
    val hash = packet.optString("payloadHash").trim().lowercase()
    if (hash.length != HASH_LENGTH || !isHex(hash)) return reject("hash inválido")
    val requested = packet.opt("newExpiresAt")
    if (requested !is Number) return reject("caducidad ausente")
    val requestedAt = requested.toLong()
    if (requestedAt < 0L) return reject("caducidad negativa")
    val target = entry ?: return reject("entrada desconocida")
    val sender = authenticatedSender.trim().lowercase()
    if (sender.isEmpty()) return reject("remitente vacío")
    // Se exige ser el emisor original: escritorio admite además custodios con
    // vouchScore >= 65, pero el móvil no tiene reputación y sin ese filtro
    // cualquier par podría estirar cuotas ajenas de forma indefinida.
    if (target.senderSid != sender) return reject("renovación de una entrada ajena")
    val ceiling = nowMillis + VaultStoreEnvelope.TTL_MILLIS
    val capped = requestedAt.coerceAtMost(ceiling)
    val expiresAt = maxOf(capped, target.expiresAt)
    return VaultRenewalResult.Accepted(VaultRenewalRequest(hash, expiresAt), target)
  }

  fun build(payloadHash: String, expiresAt: Long): JSONObject? {
    val hash = payloadHash.trim().lowercase()
    if (hash.length != HASH_LENGTH || !isHex(hash)) return null
    if (expiresAt < 0L) return null
    return JSONObject().put("payloadHash", hash).put("newExpiresAt", expiresAt)
  }

  private fun reject(reason: String) = VaultRenewalResult.Rejected(reason)

  private fun isHex(value: String): Boolean =
    value.all { it in '0'..'9' || it in 'a'..'f' }
}
