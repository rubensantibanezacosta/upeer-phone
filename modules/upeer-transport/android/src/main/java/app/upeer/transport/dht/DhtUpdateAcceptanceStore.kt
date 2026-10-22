package app.upeer.transport.dht

import android.content.Context
import android.util.Log
import app.upeer.transport.KeystoreStore
import app.upeer.transportbridge.transportbridge.Transportbridge
import org.json.JSONArray
import org.json.JSONObject

internal interface DhtUpdateAcceptancePersistence {
  fun load(): ByteArray?
  fun save(value: ByteArray)
  fun clear()
}

internal data class DhtAcceptedLocation(
  val accountUpeerId: String,
  val blockJSON: String,
  val primaryAddress: String,
  val addresses: List<String>,
  val dhtSeq: Long,
  val deviceId: String,
  val expiresAt: Long,
  val updatedAt: Long,
)

internal data class DhtUpdateAcceptance(
  val location: DhtAcceptedLocation,
  val changed: Boolean,
)

internal class DhtUpdateAcceptanceStore(
  private val persistence: DhtUpdateAcceptancePersistence,
  private val nowMillis: () -> Long = System::currentTimeMillis,
  private val verify: (String, String, String, Long) -> Boolean = { block, publicKey, registry, now ->
    Transportbridge.verifyDeviceLocationBlock(block, publicKey, registry, now)
  },
  private val verifyPow: (String, String) -> Boolean = { _, _ -> false },
  private val log: (String) -> Unit = { message -> Log.w(TAG, message) },
) {
  private val lock = Any()
  private val accepted = LinkedHashMap<String, DhtAcceptedLocation>()
  private val active = HashMap<String, String>()
  private var loaded = false

  constructor(context: Context) : this(KeystorePersistence(context))

  constructor(context: Context, nowMillis: () -> Long) : this(KeystorePersistence(context), nowMillis)

  fun accept(
    blockJSON: String,
    expectedUpeerId: String,
    publicKeyHex: String,
    registryJSON: String,
    powProof: String? = null,
  ): DhtUpdateAcceptance? = synchronized(lock) {
    ensureLoaded()
    val account = expectedUpeerId.trim().lowercase()
    if (registryJSON.isBlank()) return@synchronized null
    val now = nowMillis()
    val verified = DhtContactLocation.verifyOne(blockJSON, account, publicKeyHex, registryJSON, now, verify)
      ?: return@synchronized null
    val key = locationKey(account, verified.deviceId)
    val previous = accepted[key]
    val validation = DhtSequence.validate(previous?.dhtSeq ?: 0L, verified.dhtSeq)
    if (!validation.valid) {
      val proof = powProof?.takeIf(String::isNotBlank)
      if (!validation.requiresPoW || proof == null || !verifyPow(proof, account)) {
        log("DHT_UPDATE rechazado para $account/${verified.deviceId}: ${validation.reason}")
        return@synchronized null
      }
    }
    if (previous != null && verified.dhtSeq == previous.dhtSeq) {
      return@synchronized DhtUpdateAcceptance(previous, false)
    }
    val location = DhtAcceptedLocation(
      accountUpeerId = account,
      blockJSON = verified.blockJSON,
      primaryAddress = verified.block.optString("address").ifBlank { verified.addresses.first() },
      addresses = verified.addresses,
      dhtSeq = verified.dhtSeq,
      deviceId = verified.deviceId,
      expiresAt = verified.expiresAt,
      updatedAt = now,
    )
    accepted[key] = location
    trim()
    activate(location)
    persist()
    DhtUpdateAcceptance(location, true)
  }

  fun current(accountUpeerId: String): DhtAcceptedLocation? = synchronized(lock) {
    ensureLoaded()
    val account = accountUpeerId.trim().lowercase()
    active[account]?.let(accepted::get)
  }

  fun current(accountUpeerId: String, deviceId: String): DhtAcceptedLocation? = synchronized(lock) {
    ensureLoaded()
    accepted[locationKey(accountUpeerId.trim().lowercase(), deviceId.trim().lowercase())]
  }

  fun clear() {
    synchronized(lock) {
      accepted.clear()
      active.clear()
      persistence.clear()
    }
  }

  private fun ensureLoaded() {
    if (loaded) return
    loaded = true
    val raw = try {
      persistence.load()
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo leer la caché de DHT_UPDATE; se descarta", cause)
      return
    } ?: return
    try {
      val state = JSONObject(String(raw, Charsets.UTF_8))
      val version = state.optInt("version")
      if (version !in 1..STATE_VERSION) return
      val entries = state.optJSONObject("accepted") ?: return
      entries.keys().forEach { key ->
        val entry = entries.optJSONObject(key) ?: return@forEach
        val location = parse(key, entry) ?: return@forEach
        accepted[locationKey(location.accountUpeerId, location.deviceId)] = location
      }
      trim()
      rebuildActive()
    } catch (cause: Exception) {
      Log.w(TAG, "La caché de DHT_UPDATE no es válida; se descarta", cause)
      accepted.clear()
      active.clear()
    } finally {
      raw.fill(0)
    }
  }

  private fun parse(accountKey: String, entry: JSONObject): DhtAcceptedLocation? {
    val encodedAccount = entry.optString("account").ifBlank { accountKey.substringBefore('|') }
    val normalized = encodedAccount.trim().lowercase().takeIf { DhtLocation.isHex(it, 32) } ?: return null
    val blockJSON = entry.optString("block")
    runCatching { JSONObject(blockJSON) }.getOrNull() ?: return null
    val primary = entry.optString("address")
    val addresses = entry.optJSONArray("addresses") ?: return null
    val values = (0 until addresses.length()).mapNotNull { index -> addresses.optString(index).ifBlank { null } }
    val deviceId = entry.optString("deviceId")
    val dhtSeq = entry.optLong("dhtSeq", -1L)
    val expiresAt = entry.optLong("expiresAt", 0L)
    val updatedAt = entry.optLong("updatedAt", 0L)
    if (values.isEmpty() || primary.isBlank() || !DhtLocation.isHex(deviceId, 32)) return null
    if (dhtSeq < 0L || expiresAt <= 0L || updatedAt <= 0L) return null
    return DhtAcceptedLocation(normalized, blockJSON, primary, values, dhtSeq, deviceId, expiresAt, updatedAt)
  }

  private fun locationKey(accountUpeerId: String, deviceId: String): String =
    "${accountUpeerId.trim().lowercase()}|${deviceId.trim().lowercase()}"

  private fun activate(location: DhtAcceptedLocation) {
    active[location.accountUpeerId] = locationKey(location.accountUpeerId, location.deviceId)
  }

  private fun rebuildActive() {
    active.clear()
    accepted.values.groupBy { it.accountUpeerId }.forEach { (account, locations) ->
      locations.maxByOrNull { it.updatedAt }?.let { location ->
        active[account] = locationKey(location.accountUpeerId, location.deviceId)
      }
    }
  }

  private fun trim() {
    while (accepted.size > MAX_ENTRIES) {
      val oldest = accepted.minByOrNull { it.value.updatedAt }?.key ?: return
      accepted.remove(oldest)
      val account = oldest.substringBefore('|')
      if (active[account] == oldest) active.remove(account)
    }
  }

  private fun persist() {
    val entries = JSONObject()
    accepted.forEach { (key, location) ->
      entries.put(
        key,
        JSONObject()
          .put("account", location.accountUpeerId)
          .put("block", location.blockJSON)
          .put("address", location.primaryAddress)
          .put("addresses", JSONArray(location.addresses))
          .put("dhtSeq", location.dhtSeq)
          .put("deviceId", location.deviceId)
          .put("expiresAt", location.expiresAt)
          .put("updatedAt", location.updatedAt),
      )
    }
    val encoded = JSONObject()
      .put("version", STATE_VERSION)
      .put("accepted", entries)
      .toString()
      .toByteArray(Charsets.UTF_8)
    try {
      persistence.save(encoded)
    } catch (cause: Exception) {
      Log.e(TAG, "No se pudo guardar la caché de DHT_UPDATE", cause)
      throw cause
    } finally {
      encoded.fill(0)
    }
  }

  private class KeystorePersistence(context: Context) : DhtUpdateAcceptancePersistence {
    private val store = KeystoreStore(context, "upeer_dht_updates", "upeer-dht-updates", "accepted_updates")

    override fun load(): ByteArray? = store.load()

    override fun save(value: ByteArray) = store.save(value)

    override fun clear() = store.clear()
  }

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val STATE_VERSION = 2
    private const val MAX_ENTRIES = 256
  }
}
