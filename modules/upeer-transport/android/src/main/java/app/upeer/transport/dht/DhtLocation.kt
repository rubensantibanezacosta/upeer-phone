package app.upeer.transport.dht

import app.upeer.transportbridge.transportbridge.CryptoIdentity
import app.upeer.transportbridge.transportbridge.Transportbridge
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet6Address
import java.net.InetAddress

object DhtLocation {
  const val DEFAULT_TTL_MILLIS = 30L * 24L * 60L * 60L * 1000L
  const val MAX_TTL_MILLIS = 60L * 24L * 60L * 60L * 1000L
  const val MAX_DEVICE_ID_LENGTH = 128

  fun build(
    identity: CryptoIdentity,
    addresses: List<String>,
    dhtSeq: Long,
    deviceId: String,
    ttlMillis: Long = 0,
  ): String {
    require(dhtSeq >= 0) { "dhtSeq must not be negative" }
    require(deviceId.length <= MAX_DEVICE_ID_LENGTH) { "deviceId must be at most $MAX_DEVICE_ID_LENGTH characters" }
    val normalized = normalize(addresses)
    return identity.buildLocationBlock(JSONArray(normalized).toString(), dhtSeq, clampTTL(ttlMillis), deviceId)
  }

  fun buildDevice(
    deviceIdentity: CryptoIdentity,
    certificateJSON: String,
    addresses: List<String>,
    dhtSeq: Long,
    ttlMillis: Long = 0,
    nowMillis: Long = System.currentTimeMillis(),
  ): String {
    require(dhtSeq >= 0) { "dhtSeq must not be negative" }
    val normalized = normalize(addresses)
    return deviceIdentity.buildDeviceLocationBlock(
      certificateJSON,
      JSONArray(normalized).toString(),
      dhtSeq,
      clampTTL(ttlMillis),
      nowMillis,
    )
  }

  fun verify(blockJSON: String, publicKeyHex: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
    val decoded = validateStructure(blockJSON, publicKeyHex, nowMillis)
    if (decoded.deviceCertificate != null) {
      return Transportbridge.verifyDeviceLocationBlock(blockJSON, publicKeyHex, "", nowMillis)
    }
    return Transportbridge.verifyLocationBlock(blockJSON, publicKeyHex, nowMillis)
  }

  internal fun buildWithSigner(
    addresses: List<String>,
    dhtSeq: Long,
    deviceId: String,
    upeerId: String,
    ttlMillis: Long,
    nowMillis: Long,
    deviceMeta: JSONObject? = null,
    signer: (String) -> String,
  ): String {
    require(isHex(upeerId, 32)) { "upeerId must be a 32-byte hexadecimal value" }
    require(dhtSeq >= 0) { "dhtSeq must not be negative" }
    require(deviceId.length <= MAX_DEVICE_ID_LENGTH) { "deviceId must be at most $MAX_DEVICE_ID_LENGTH characters" }
    require(nowMillis > 0) { "nowMillis must be positive" }
    val normalized = normalize(addresses)
    val ttl = clampTTL(ttlMillis)
    require(nowMillis <= Long.MAX_VALUE - ttl) { "expiresAt overflows" }
    val expiresAt = nowMillis + ttl
    val signed = signedPayload(upeerId, normalized, dhtSeq, expiresAt, deviceId, deviceMeta)
    val signature = signer(DhtCanonicalJson.canonical(signed))
    require(isHex(signature, 64)) { "signature must be a 64-byte hexadecimal value" }
    val block = JSONObject()
      .put("address", normalized.first())
      .put("addresses", JSONArray(normalized))
      .put("dhtSeq", dhtSeq)
      .put("expiresAt", expiresAt)
      .put("signature", signature)
    if (deviceId.isNotEmpty()) block.put("deviceId", deviceId)
    if (deviceMeta != null) block.put("deviceMeta", deviceMeta)
    return block.toString()
  }

  internal fun verifyWith(
    blockJSON: String,
    publicKeyHex: String,
    expectedUpeerId: String,
    nowMillis: Long,
    verifier: (String, String, String) -> Boolean,
  ): JSONObject {
    val decoded = validateStructure(blockJSON, publicKeyHex, nowMillis)
    require(isHex(expectedUpeerId, 32)) { "expected upeerId must be a 32-byte hexadecimal value" }
    val signed = signedPayload(
      expectedUpeerId.lowercase(), decoded.addresses, decoded.dhtSeq, decoded.expiresAt, decoded.deviceId, decoded.deviceMeta,
    )
    val canonical = DhtCanonicalJson.canonical(signed)
    val signature = decoded.block.getString("signature")
    if (!verifier(canonical, signature, publicKeyHex)) {
      if (decoded.deviceId.isNotEmpty() || decoded.deviceMeta != null) {
        throw IllegalArgumentException("invalid location block signature")
      }
      val legacy = JSONObject()
        .put("upeerId", expectedUpeerId.lowercase())
        .put("address", decoded.block.optString("address"))
        .put("dhtSeq", decoded.dhtSeq)
        .put("expiresAt", decoded.expiresAt)
      if (!verifier(DhtCanonicalJson.canonical(legacy), signature, publicKeyHex)) {
        throw IllegalArgumentException("invalid location block signature")
      }
    }
    return metadata(decoded)
  }

  internal data class Decoded(
    val block: JSONObject,
    val addresses: List<String>,
    val dhtSeq: Long,
    val expiresAt: Long,
    val deviceId: String,
    val deviceMeta: JSONObject?,
    val deviceCertificate: JSONObject?,
  )

  internal fun validateStructure(blockJSON: String, publicKeyHex: String, nowMillis: Long): Decoded {
    require(isHex(publicKeyHex, 32)) { "public key must be a 32-byte hexadecimal value" }
    val block = DhtCanonicalJson.parse(blockJSON)
    val expiresAt = requireInteger(block, "expiresAt")
    require(expiresAt > nowMillis) { "location block is expired" }
    val dhtSeq = requireInteger(block, "dhtSeq")
    require(dhtSeq >= 0) { "dhtSeq must not be negative" }
    val signature = block.opt("signature")
    if (signature !is String || !isHex(signature, 64)) {
      throw IllegalArgumentException("signature must be a 64-byte hexadecimal value")
    }
    val deviceId = optionalString(block, "deviceId")
    require(deviceId.length <= MAX_DEVICE_ID_LENGTH) { "deviceId must be at most $MAX_DEVICE_ID_LENGTH characters" }
    val deviceMeta = optionalObject(block, "deviceMeta")
    val deviceCertificate = optionalObject(block, "deviceCertificate")
    val rawAddresses = when (val value = block.opt("addresses")) {
      null, JSONObject.NULL -> listOf(requireString(block, "address"))
      is JSONArray -> (0 until value.length()).map { index ->
        value.opt(index) as? String ?: throw IllegalArgumentException("location block addresses must be strings")
      }
      else -> throw IllegalArgumentException("location block addresses must be an array")
    }
    val normalized = normalize(if (rawAddresses.isEmpty()) listOf(requireString(block, "address")) else rawAddresses)
    return Decoded(block, normalized, dhtSeq, expiresAt, deviceId, deviceMeta, deviceCertificate)
  }

  internal fun normalize(addresses: List<String>): List<String> {
    val normalized = addresses.map(String::trim).onEach { address ->
      require(isYggdrasilAddress(address)) { "invalid Yggdrasil address" }
    }.distinct().sorted()
    require(normalized.isNotEmpty()) { "location block requires at least one address" }
    return normalized
  }

  internal fun clampTTL(ttlMillis: Long): Long = when {
    ttlMillis <= 0 -> DEFAULT_TTL_MILLIS
    ttlMillis > MAX_TTL_MILLIS -> MAX_TTL_MILLIS
    else -> ttlMillis
  }

  internal fun isHex(value: String, expectedBytes: Int): Boolean {
    if (value.length != expectedBytes * 2) return false
    return value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
  }

  internal fun decodeHex(value: String, expectedBytes: Int): ByteArray? {
    if (!isHex(value, expectedBytes)) return null
    return ByteArray(expectedBytes) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
  }

  internal fun hex(value: ByteArray): String = value.joinToString("") { "%02x".format(it) }

  private fun signedPayload(
    upeerId: String,
    addresses: List<String>,
    dhtSeq: Long,
    expiresAt: Long,
    deviceId: String,
    deviceMeta: JSONObject?,
  ): JSONObject {
    val payload = JSONObject()
      .put("upeerId", upeerId)
      .put("addresses", JSONArray(addresses))
      .put("dhtSeq", dhtSeq)
      .put("expiresAt", expiresAt)
    if (deviceId.isNotEmpty()) payload.put("deviceId", deviceId)
    if (deviceMeta != null) payload.put("deviceMeta", deviceMeta)
    return payload
  }

  private fun metadata(decoded: Decoded): JSONObject {
    val result = JSONObject()
      .put("addresses", JSONArray(decoded.addresses))
      .put("dhtSeq", decoded.dhtSeq)
      .put("expiresAt", decoded.expiresAt)
    if (decoded.deviceId.isNotEmpty()) result.put("deviceId", decoded.deviceId)
    if (decoded.deviceMeta != null) result.put("deviceMeta", decoded.deviceMeta)
    return result
  }

  private fun requireInteger(block: JSONObject, field: String): Long {
    val value = block.opt(field)
    if (value !is Int && value !is Long) throw IllegalArgumentException("$field must be an integer")
    return (value as Number).toLong()
  }

  private fun requireString(block: JSONObject, field: String): String {
    val value = block.opt(field)
    return value as? String ?: throw IllegalArgumentException("$field must be a string")
  }

  private fun optionalString(block: JSONObject, field: String): String {
    val value = block.opt(field)
    if (value == null || value == JSONObject.NULL) return ""
    return value as? String ?: throw IllegalArgumentException("$field must be a string")
  }

  private fun optionalObject(block: JSONObject, field: String): JSONObject? {
    val value = block.opt(field)
    if (value == null || value == JSONObject.NULL) return null
    return value as? JSONObject ?: throw IllegalArgumentException("$field must be an object")
  }

  private fun isYggdrasilAddress(value: String): Boolean {
    if (!value.contains(':')) return false
    val address = runCatching { InetAddress.getByName(value) }.getOrNull() ?: return false
    if (address !is Inet6Address) return false
    return address.address[0].toInt() and 0xfe == 0x02
  }
}

