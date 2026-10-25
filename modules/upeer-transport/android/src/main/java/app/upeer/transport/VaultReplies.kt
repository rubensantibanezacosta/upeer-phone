package app.upeer.transport

import android.util.Log
import app.upeer.transport.dht.DhtLocation
import app.upeer.transport.vault.VaultAcknowledgement
import app.upeer.transport.vault.VaultEntry
import app.upeer.transport.vault.VaultMailboxInsert
import app.upeer.transport.vault.VaultMailboxStore
import app.upeer.transport.vault.VaultQueryDelivery
import app.upeer.transport.vault.VaultRenewal
import app.upeer.transport.vault.VaultRenewalResult
import app.upeer.transport.vault.VaultStoreRequest
import app.upeer.transport.vault.VaultStoreResult
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONObject

internal class VaultReplies(
  private val identity: YggdrasilIdentity,
  private val deviceBindings: DeviceMessagingBindings,
  private val state: ProtocolStateStore,
  private val mailbox: VaultMailboxStore,
  private val policy: InboundPolicy,
  private val send: (Bridge, String, String) -> Boolean,
  private val replay: (Bridge, ByteArray, String) -> Unit,
) {
  fun handle(
    activeBridge: Bridge,
    payload: ByteArray,
    packet: JSONObject,
    type: String,
    sourceAddress: String,
  ): String {
    val senderUpeerId = packet.optString("senderUpeerId").lowercase()
    val publicKey = state.publicKeyFor(senderUpeerId)
      ?: throw IllegalStateException("unknown peer $senderUpeerId")
    val signature = PacketSignature.of(packet)
    val metadataJSON = when (signature) {
      is PacketSignature.Linked -> activeBridge.validateLinkedSignedPacket(
        payload,
        deviceSigningKey(senderUpeerId, signature.deviceId),
        senderUpeerId,
      )
      PacketSignature.Account -> activeBridge.validateSignedPacket(payload, publicKey)
    }
    val metadata = JSONObject(metadataJSON)
    if (!policy.allow(metadata, sourceAddress)) return policy.rateLimited(metadata)
    state.touchPresence(senderUpeerId)
    if (signature == PacketSignature.Account) state.rememberPeer(metadata)
    val now = System.currentTimeMillis()
    when (type) {
      "VAULT_STORE" -> store(activeBridge, packet, senderUpeerId, sourceAddress, now)
      "VAULT_QUERY" -> deliver(activeBridge, packet, senderUpeerId, sourceAddress, now)
      "VAULT_ACK" -> acknowledge(packet, senderUpeerId)
      "VAULT_RENEW" -> renew(packet, senderUpeerId, now)
      "VAULT_DELIVERY" -> applyDelivery(activeBridge, packet, senderUpeerId, sourceAddress, now)
      else -> throw IllegalArgumentException("unsupported vault type $type")
    }
    return metadataJSON
  }

  private fun store(
    activeBridge: Bridge,
    packet: JSONObject,
    senderUpeerId: String,
    sourceAddress: String,
    now: Long,
  ) {
    when (val parsed = VaultStoreRequest.parse(packet, senderUpeerId, now)) {
      is VaultStoreResult.Rejected -> {
        Log.w(TAG, "VAULT_STORE rechazado de $senderUpeerId: ${parsed.reason}")
        return
      }
      is VaultStoreResult.Accepted -> {
        when (val stored = mailbox.insert(parsed.entry, now)) {
          is VaultMailboxInsert.Rejected -> {
            Log.w(TAG, "VAULT_STORE no almacenado de $senderUpeerId: ${stored.reason}")
            return
          }
          is VaultMailboxInsert.Stored -> {
            Log.i(TAG, "VAULT_STORE almacenado de $senderUpeerId (${stored.entry.payloadHash.take(12)})")
            val address = replyAddress(senderUpeerId, sourceAddress) ?: return
            replyAck(activeBridge, address, listOf(stored.entry.payloadHash))
          }
        }
      }
    }
  }

  private fun deliver(
    activeBridge: Bridge,
    packet: JSONObject,
    senderUpeerId: String,
    sourceAddress: String,
    now: Long,
  ) {
    val requester = packet.optString("requesterSid")
    if (requester.lowercase() != senderUpeerId) {
      Log.w(TAG, "VAULT_QUERY de $senderUpeerId reclamando $requester; ignorada")
      return
    }
    val delivery = VaultQueryDelivery.deliver(
      entries = mailbox.snapshot(now).entries,
      authenticatedSender = senderUpeerId,
      requesterSid = requester,
      requesterDeviceId = packet.optString("requesterDeviceId"),
      payloadHash = packet.optString("payloadHash").takeIf(String::isNotEmpty),
      afterId = packet.optLong("afterId", 0L),
      nowMillis = now,
    )
    if (delivery == null) {
      Log.i(TAG, "VAULT_QUERY de $senderUpeerId sin entradas que entregar")
      return
    }
    val address = replyAddress(senderUpeerId, sourceAddress) ?: run {
      Log.w(TAG, "VAULT_QUERY de $senderUpeerId sin dirección de respuesta")
      return
    }
    val count = delivery.optJSONArray("entries")?.length() ?: 0
    if (!send(activeBridge, address, delivery.toString())) {
      Log.w(TAG, "No se pudo entregar el buzón a $address")
      return
    }
    Log.i(TAG, "VAULT_DELIVERY enviado a $address con $count entradas")
  }

  private fun acknowledge(packet: JSONObject, senderUpeerId: String) {
    val hashes = VaultAcknowledgement.parse(packet) ?: run {
      Log.w(TAG, "VAULT_ACK inválido de $senderUpeerId")
      return
    }
    val device = packet.optString("deviceId").takeIf(String::isNotEmpty)
    val releasable = mailbox.snapshot(System.currentTimeMillis()).entries
      .filter { hashes.contains(it.payloadHash) }
      .filter { VaultAcknowledgement.releasable(it, senderUpeerId, device) }
      .map { it.payloadHash }
    if (releasable.isEmpty()) {
      Log.i(TAG, "VAULT_ACK de $senderUpeerId sin entradas propias que liberar")
      return
    }
    val removed = mailbox.release(releasable)
    Log.i(TAG, "VAULT_ACK de $senderUpeerId liberó $removed entradas")
  }

  private fun renew(packet: JSONObject, senderUpeerId: String, now: Long) {
    val hash = packet.optString("payloadHash")
    val entry = mailbox.entry(hash, now)
    when (val parsed = VaultRenewal.parse(packet, senderUpeerId, entry, now)) {
      is VaultRenewalResult.Rejected -> {
        Log.w(TAG, "VAULT_RENEW rechazado de $senderUpeerId: ${parsed.reason}")
        return
      }
      is VaultRenewalResult.Accepted -> {
        if (mailbox.renew(parsed.request.payloadHash, parsed.request.expiresAt)) {
          Log.i(TAG, "VAULT_RENEW extendió ${parsed.request.payloadHash.take(12)} hasta ${parsed.request.expiresAt}")
        }
      }
    }
  }

  private fun applyDelivery(    activeBridge: Bridge,
    packet: JSONObject,
    senderUpeerId: String,
    sourceAddress: String,
    now: Long,
  ) {
    val entries = VaultQueryDelivery.parseDelivery(packet) ?: run {
      Log.w(TAG, "VAULT_DELIVERY inválido de $senderUpeerId")
      return
    }
    val applied = mutableListOf<String>()
    for (entry in entries) {
      val bytes = entry.data.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
      try {
        replay(activeBridge, bytes, sourceAddress)
        applied.add(entry.payloadHash)
      } catch (cause: Exception) {
        Log.w(TAG, "Entrada del vault ${entry.payloadHash.take(12)} no aplicable: ${message(cause)}")
      }
    }
    if (applied.isEmpty()) return
    mailbox.release(applied)
    val address = replyAddress(senderUpeerId, sourceAddress) ?: return
    replyAck(activeBridge, address, applied)
    Log.i(TAG, "VAULT_DELIVERY aplicado: ${applied.size} de ${entries.size} entradas")
  }

  private fun replyAddress(senderUpeerId: String, sourceAddress: String): String? {
    state.addressFor(senderUpeerId)?.let { return it }
    return sourceAddress.takeIf { YGG_ADDRESS.matches(it) }
  }

  private fun replyAck(activeBridge: Bridge, address: String, hashes: List<String>) {
    val ack = VaultAcknowledgement.build(hashes, identity.currentDevice()?.deviceId) ?: return
    if (!send(activeBridge, address, ack.toString())) Log.w(TAG, "No se pudo enviar VAULT_ACK a $address")
  }

  private fun deviceSigningKey(senderUpeerId: String, deviceId: String): String {
    if (deviceBindings.isRevoked(senderUpeerId, deviceId)) {
      throw IllegalArgumentException("sender device $deviceId is revoked")
    }
    val peer = state.peerFor(senderUpeerId)
      ?: throw IllegalStateException("unknown peer $senderUpeerId")
    if (!peer.optString("deviceId").equals(deviceId, ignoreCase = true)) {
      throw IllegalArgumentException("sender device $deviceId is unknown")
    }
    val key = peer.optString("devicePublicKey")
    if (!DhtLocation.isHex(key, 32)) throw IllegalArgumentException("peer device key is unavailable")
    return key
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
    private val YGG_ADDRESS = Regex("^[23][0-9a-f]{2}:.*", RegexOption.IGNORE_CASE)
  }
}
