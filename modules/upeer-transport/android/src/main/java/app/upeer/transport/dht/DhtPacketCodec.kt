package app.upeer.transport.dht

import app.upeer.transportbridge.transportbridge.CryptoIdentity
import app.upeer.transportbridge.transportbridge.Transportbridge
import org.json.JSONObject
import java.net.Inet6Address
import java.net.InetAddress

object DhtPacketCodec {
  private val reservedFields = setOf("signature", "senderUpeerId", "senderYggAddress")
  private val excludedFromSignature = setOf("signature", "contactCache", "renewalToken")

  fun build(identity: CryptoIdentity, senderYggAddress: String, payloadJSON: String): String {
    validatePayload(payloadJSON, senderYggAddress)
    return identity.buildSignedPacket(senderYggAddress, payloadJSON)
  }

  fun verify(packetJSON: String, publicKeyHex: String): JSONObject =
    JSONObject(Transportbridge.verifySignedPacket(packetJSON, publicKeyHex))

  internal fun buildWithSigner(
    payloadJSON: String,
    senderYggAddress: String,
    upeerId: String,
    signer: (String) -> String,
  ): String {
    val packet = validatePayload(payloadJSON, senderYggAddress)
    packet.put("senderUpeerId", upeerId)
    packet.put("senderYggAddress", senderYggAddress)
    if (!packet.has("isInternalSync")) packet.put("isInternalSync", false)
    val signedPayload = copyForSignature(packet)
    signedPayload.put("senderUpeerId", upeerId)
    signedPayload.put("senderYggAddress", senderYggAddress)
    packet.put("signature", signer(DhtCanonicalJson.canonical(signedPayload)))
    return packet.toString()
  }

  internal fun verifyWith(
    packetJSON: String,
    publicKeyHex: String,
    expectedUpeerId: String,
    verifier: (String, String, String) -> Boolean,
  ): JSONObject {
    val packet = DhtCanonicalJson.parse(packetJSON)
    val type = packet.optString("type")
    if (type.isEmpty()) throw IllegalArgumentException("signed packet type is required")
    val senderUpeerId = packet.optString("senderUpeerId")
    if (!senderUpeerId.equals(expectedUpeerId, ignoreCase = true)) {
      throw IllegalArgumentException("senderUpeerId does not match the peer public key")
    }
    val senderYggAddress = packet.optString("senderYggAddress")
    if (!isYggdrasilAddress(senderYggAddress)) {
      throw IllegalArgumentException("senderYggAddress must be a Yggdrasil IPv6 address")
    }
    val signature = packet.optString("signature")
    if (!isHex(signature, 64)) throw IllegalArgumentException("signature must be a 64-byte hexadecimal value")
    val signedPayload = copyForSignature(packet)
    signedPayload.put("senderUpeerId", senderUpeerId)
    signedPayload.put("senderYggAddress", senderYggAddress)
    val canonical = DhtCanonicalJson.canonical(signedPayload)
    if (!verifier(canonical, signature, publicKeyHex)) {
      throw IllegalArgumentException("invalid signed packet signature")
    }
    return JSONObject()
      .put("type", type)
      .put("senderUpeerId", senderUpeerId.lowercase())
      .put("senderYggAddress", senderYggAddress)
      .put("publicKey", publicKeyHex.lowercase())
      .put("id", packet.optString("id"))
  }

  private fun validatePayload(payloadJSON: String, senderYggAddress: String): JSONObject {
    val packet = DhtCanonicalJson.parse(payloadJSON)
    if (packet.optString("type").isEmpty()) throw IllegalArgumentException("signed packet type is required")
    reservedFields.forEach { field ->
      if (packet.has(field)) throw IllegalArgumentException("signed packet must not provide $field")
    }
    if (!isYggdrasilAddress(senderYggAddress)) {
      throw IllegalArgumentException("senderYggAddress must be a Yggdrasil IPv6 address")
    }
    DhtPacketPolicy.decode(packet)
    return packet
  }

  private fun copyForSignature(packet: JSONObject): JSONObject {
    val copy = JSONObject(packet.toString())
    excludedFromSignature.forEach(copy::remove)
    return copy
  }

  private fun isYggdrasilAddress(value: String): Boolean {
    if (!value.contains(':')) return false
    val address = runCatching { InetAddress.getByName(value) }.getOrNull() ?: return false
    if (address !is Inet6Address) return false
    return address.address[0].toInt() and 0xfe == 0x02
  }

  private fun isHex(value: String, expectedBytes: Int): Boolean {
    if (value.length != expectedBytes * 2) return false
    return value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
  }
}
