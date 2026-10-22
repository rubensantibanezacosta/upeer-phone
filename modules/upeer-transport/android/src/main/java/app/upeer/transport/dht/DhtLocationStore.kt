package app.upeer.transport.dht

import android.content.Context
import android.util.Log
import app.upeer.transport.KeystoreStore
import org.json.JSONObject

internal interface DhtLocationPersistence {
  fun load(): ByteArray?
  fun save(value: ByteArray)
  fun clear()
}

internal class DhtLocationStore(
  private val persistence: DhtLocationPersistence,
) {
  private val lock = Any()
  private var state = loadState()

  constructor(context: Context) : this(KeystorePersistence(context))

  fun observeAddress(upeerId: String, address: String): Long = synchronized(lock) {
    require(DhtLocation.isHex(upeerId, 32)) { "upeerId must be a 32-byte hexadecimal value" }
    val normalizedAddress = DhtLocation.normalize(listOf(address)).first()
    val normalizedUpeerId = upeerId.lowercase()
    if (state.optString("upeerId") != normalizedUpeerId) {
      resetSession(normalizedUpeerId)
    }
    val previous = state.optString("lastAddress")
    val current = state.optLong("dhtSeq")
    val next = DhtSequence.next(current, previous, normalizedAddress)
    if (next != current || previous != normalizedAddress) {
      state.put("dhtSeq", next)
      state.put("lastAddress", normalizedAddress)
      persist()
    }
    next
  }

  fun hasState(upeerId: String): Boolean = synchronized(lock) {
    DhtLocation.isHex(upeerId, 32) && state.optString("upeerId") == upeerId.lowercase()
  }

  fun adoptSequence(upeerId: String, dhtSeq: Long): Long = synchronized(lock) {
    require(DhtLocation.isHex(upeerId, 32)) { "upeerId must be a 32-byte hexadecimal value" }
    require(dhtSeq >= 0L) { "dhtSeq must not be negative" }
    val normalizedUpeerId = upeerId.lowercase()
    val identityChanged = state.optString("upeerId") != normalizedUpeerId
    if (identityChanged) {
      resetSession(normalizedUpeerId)
    }
    val previous = state.optLong("dhtSeq", 0L)
    if (dhtSeq < previous) return previous
    if (identityChanged || dhtSeq != previous) {
      state.put("dhtSeq", dhtSeq)
      persist()
    }
    dhtSeq
  }

  fun statusJSON(upeerId: String?, deviceId: String? = null): String = synchronized(lock) {
    if (upeerId == null || state.optString("upeerId") != upeerId.lowercase()) {
      JSONObject().put("ready", false).put("dhtSeq", 0L).toString()
    } else {
      JSONObject()
        .put("ready", true)
        .put("upeerId", upeerId.lowercase())
        .put("dhtSeq", state.optLong("dhtSeq"))
        .put("lastAddress", state.optString("lastAddress"))
        .apply { if (deviceId != null) put("deviceId", deviceId) }
        .toString()
    }
  }

  fun clearIdentity() = synchronized(lock) {
    state = JSONObject()
    persistence.clear()
  }

  private fun resetSession(upeerId: String) {
    state = JSONObject()
    state.put("upeerId", upeerId)
    state.put("dhtSeq", 0L)
  }

  private fun loadState(): JSONObject {
    val raw = try {
      persistence.load()
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo leer el estado DHT cifrado; se reinicia la secuencia", cause)
      return JSONObject()
    } ?: return JSONObject()
    return try {
      sanitize(JSONObject(String(raw, Charsets.UTF_8)))
    } catch (cause: Exception) {
      Log.w(TAG, "El estado DHT almacenado no es válido; se reinicia la secuencia", cause)
      JSONObject()
    } finally {
      raw.fill(0)
    }
  }

  private fun sanitize(raw: JSONObject): JSONObject {
    val state = JSONObject()
    val upeerId = raw.optString("upeerId")
    val lastAddress = raw.opt("lastAddress")
    val dhtSeq = raw.opt("dhtSeq")
    val validSeq = dhtSeq is Int || dhtSeq is Long
    if (DhtLocation.isHex(upeerId, 32) && lastAddress is String && validSeq && (dhtSeq as Number).toLong() >= 0) {
      state.put("upeerId", upeerId.lowercase())
      state.put("lastAddress", lastAddress)
      state.put("dhtSeq", (dhtSeq as Number).toLong())
    } else if (DhtLocation.isHex(upeerId, 32) || lastAddress != null) {
      Log.w(TAG, "Estado de ubicación DHT incompleto; se descarta la secuencia almacenada")
    }
    return state
  }

  private fun persist() {
    val encoded = state.toString().toByteArray(Charsets.UTF_8)
    try {
      persistence.save(encoded)
    } catch (cause: Exception) {
      throw IllegalStateException("No se pudo persistir el estado DHT", cause)
    } finally {
      encoded.fill(0)
    }
  }

  private class KeystorePersistence(context: Context) : DhtLocationPersistence {
    private val store = KeystoreStore(context, "upeer_dht_location", "upeer-dht-location", "location_state")

    override fun load(): ByteArray? = store.load()

    override fun save(value: ByteArray) = store.save(value)

    override fun clear() = store.clear()
  }

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
