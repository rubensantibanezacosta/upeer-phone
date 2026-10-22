package app.upeer.transport

import android.content.Context
import android.util.Log
import app.upeer.transport.dht.DhtCanonicalJson
import app.upeer.transport.dht.DhtLimits
import app.upeer.transportbridge.transportbridge.Transportbridge
import org.json.JSONArray
import org.json.JSONObject

internal interface ContactRegistryPersistence {
  fun load(): ByteArray?
  fun save(value: ByteArray)
  fun clear()
}

internal data class ContactRegistrySnapshot(
  val accountUpeerId: String,
  val registryJSON: String,
  val epoch: Long,
  val issuedAt: Long,
  val expiresAt: Long,
  val devices: Int,
  val revoked: Set<String>,
  val updatedAt: Long,
)

internal class ContactRegistryStore(
  private val persistence: ContactRegistryPersistence,
  private val nowMillis: () -> Long = System::currentTimeMillis,
  private val verifyRegistry: (String, String, Long) -> Boolean = { registry, account, now ->
    Transportbridge.verifyAccountRegistry(registry, account, now)
  },
  private val verifyUpdate: (String, String, String, Long) -> Boolean = { previous, next, account, now ->
    Transportbridge.validateAccountRegistryUpdate(previous, next, account, now)
  },
  private val verifyAdmission: (String, String, String, Long) -> Boolean = { certificate, registry, account, now ->
    Transportbridge.validateDeviceAdmission(certificate, registry, account, now)
  },
) {
  private val lock = Any()
  private val registries = LinkedHashMap<String, ContactRegistrySnapshot>()
  private var loaded = false

  constructor(context: Context) : this(KeystorePersistence(context))

  fun adopt(registryJSON: String): ContactRegistrySnapshot = synchronized(lock) {
    ensureLoaded()
    require(registryJSON.isNotEmpty() && registryJSON.length <= DhtLimits.MAX_VALUE_CHARS) {
      "account registry size is invalid"
    }
    val next = parse(registryJSON)
    val now = nowMillis()
    if (!verifyRegistry(registryJSON, next.accountUpeerId, now)) {
      throw IllegalArgumentException("account registry signature or validity is invalid")
    }
    val previous = registries[next.accountUpeerId]
    if (previous != null) {
      if (next.epoch < previous.epoch) throw IllegalArgumentException("account registry epoch rolled back")
      if (next.epoch == previous.epoch) {
        if (!sameRegistry(previous.registryJSON, registryJSON)) {
          throw IllegalArgumentException("account registry changed without increasing its epoch")
        }
        return previous
      }
      if (!verifyUpdate(previous.registryJSON, registryJSON, next.accountUpeerId, now)) {
        throw IllegalArgumentException("account registry update is not monotonic")
      }
    }
    val snapshot = next.copy(updatedAt = now)
    registries[snapshot.accountUpeerId] = snapshot
    trim()
    persist()
    snapshot
  }

  fun current(accountUpeerId: String): ContactRegistrySnapshot? = synchronized(lock) {
    ensureLoaded()
    registries[normalizeAccountUpeerId(accountUpeerId)]
  }

  fun admits(certificateJSON: String, accountUpeerId: String): Boolean? = synchronized(lock) {
    ensureLoaded()
    val account = normalizeAccountUpeerId(accountUpeerId)
    val snapshot = registries[account] ?: return null
    val now = nowMillis()
    if (snapshot.expiresAt <= now) return null
    runCatching {
      verifyAdmission(certificateJSON, snapshot.registryJSON, account, now)
    }.getOrElse { cause ->
      Log.w(TAG, "No se pudo validar el certificado de un contacto", cause)
      false
    }
  }

  fun statusJSON(): String = synchronized(lock) {
    ensureLoaded()
    JSONObject()
      .put("ready", registries.isNotEmpty())
      .put("registries", registries.size)
      .put("latestEpoch", registries.values.maxOfOrNull(ContactRegistrySnapshot::epoch) ?: 0L)
      .toString()
  }

  fun clear() = synchronized(lock) {
    registries.clear()
    loaded = true
    persistence.clear()
  }

  private fun ensureLoaded() {
    if (loaded) return
    loaded = true
    val raw = try {
      persistence.load()
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo leer el almacén de registros de contactos", cause)
      return
    } ?: return
    try {
      val state = JSONObject(String(raw, Charsets.UTF_8))
      if (state.optInt("version") != STATE_VERSION) return
      val stored = state.optJSONObject("registries") ?: return
      stored.keys().forEach { account ->
        val value = stored.optJSONObject(account) ?: return@forEach
        val snapshot = runCatching { parse(value.optString("registry")) }.getOrNull() ?: return@forEach
        if (snapshot.accountUpeerId != account.lowercase()) return@forEach
        registries[snapshot.accountUpeerId] = snapshot.copy(updatedAt = value.optLong("updatedAt"))
      }
      trim()
    } catch (cause: Exception) {
      Log.w(TAG, "El almacén de registros de contactos no es válido; se descarta", cause)
      registries.clear()
    } finally {
      raw.fill(0)
    }
  }

  private fun persist() {
    val stored = JSONObject()
    registries.forEach { (account, snapshot) ->
      stored.put(
        account,
        JSONObject()
          .put("registry", snapshot.registryJSON)
          .put("updatedAt", snapshot.updatedAt),
      )
    }
    val state = JSONObject()
      .put("version", STATE_VERSION)
      .put("registries", stored)
    val encoded = state.toString().toByteArray(Charsets.UTF_8)
    try {
      persistence.save(encoded)
    } catch (cause: Exception) {
      Log.e(TAG, "No se pudo guardar el registro de contactos", cause)
      throw cause
    } finally {
      encoded.fill(0)
    }
  }

  private fun sameRegistry(previousJSON: String, nextJSON: String): Boolean {
    if (previousJSON == nextJSON) return true
    return runCatching {
      DhtCanonicalJson.canonical(JSONObject(previousJSON)) == DhtCanonicalJson.canonical(JSONObject(nextJSON))
    }.getOrElse { cause ->
      Log.w(TAG, "No se pudieron comparar dos registros de cuenta", cause)
      false
    }
  }

  private fun trim() {
    while (registries.size > MAX_REGISTRIES) {
      val oldest = registries.minByOrNull { it.value.updatedAt }?.key ?: return
      registries.remove(oldest)
    }
  }

  private fun parse(registryJSON: String): ContactRegistrySnapshot {
    return try {
      val registry = JSONObject(registryJSON)
      val account = normalizeAccountUpeerId(registry.optString("accountUpeerId"))
      val epoch = registry.optLong("epoch")
      val issuedAt = registry.optLong("issuedAt")
      val expiresAt = registry.optLong("expiresAt")
      val devices = registry.optJSONArray("devices") ?: JSONArray()
      require(epoch >= 0L && issuedAt > 0L && expiresAt > issuedAt) {
        "account registry validity window is invalid"
      }
      require(devices.length() <= MAX_DEVICES) { "account registry contains too many devices" }
      ContactRegistrySnapshot(
        account,
        registryJSON,
        epoch,
        issuedAt,
        expiresAt,
        devices.length(),
        revocationDeviceIds(registry),
        0L,
      )
    } catch (cause: IllegalArgumentException) {
      throw cause
    } catch (cause: Exception) {
      throw IllegalArgumentException("account registry is invalid", cause)
    }
  }

  private class KeystorePersistence(context: Context) : ContactRegistryPersistence {
    private val store = KeystoreStore(context, "upeer_contact_registries", "upeer-contact-registries", "contact_registries_state")

    override fun load(): ByteArray? = store.load()

    override fun save(value: ByteArray) = store.save(value)

    override fun clear() = store.clear()
  }

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val STATE_VERSION = 1
    private const val MAX_REGISTRIES = 256
    private const val MAX_DEVICES = 64
  }
}
