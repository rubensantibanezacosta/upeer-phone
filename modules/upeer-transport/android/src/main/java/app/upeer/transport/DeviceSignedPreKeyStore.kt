package app.upeer.transport

import android.content.Context
import android.util.Log
import app.upeer.transport.dht.DhtLocation
import org.json.JSONArray
import org.json.JSONObject

internal interface DeviceSpkPersistence {
  fun load(): ByteArray?
  fun save(value: ByteArray)
  fun clear()
}

internal class DeviceSignedPreKeyStore(
  private val persistence: DeviceSpkPersistence,
  private val nowMillis: () -> Long = System::currentTimeMillis,
) {
  constructor(context: Context) : this(KeystorePersistence(context))

  fun currentJSON(device: DeviceIdentity): String {
    val state = load()
    val sameDevice = state?.optString("deviceId").equals(device.deviceId, ignoreCase = true)
    val current = state?.optJSONObject("current")?.takeIf(::isValid)
    if (sameDevice && current != null && isFresh(current.optLong("spkId"))) return current.toString()
    val next = JSONObject(device.crypto.createSignedPreKey(nowMillis()))
    if (!isValid(next)) throw IllegalStateException("El puente devolvió una SPK de dispositivo inválida")
    val previous = JSONArray()
    if (sameDevice) {
      buildList {
        current?.let(::add)
        val stored = state?.optJSONArray("previous")
        if (stored != null) {
          for (index in 0 until stored.length()) {
            stored.optJSONObject(index)?.takeIf(::isValid)?.let(::add)
          }
        }
      }.distinctBy { it.optLong("spkId") }
        .take(MAX_PREVIOUS)
        .forEach(previous::put)
    }
    val wrapper = JSONObject()
      .put("version", STATE_VERSION)
      .put("deviceId", device.deviceId)
      .put("current", next)
      .put("previous", previous)
    persistence.save(wrapper.toString().toByteArray(Charsets.UTF_8))
    Log.i(TAG, "SPK de dispositivo creada: ${next.optLong("spkId")}")
    return next.toString()
  }

  fun secretFor(device: DeviceIdentity, spkID: Long): String? {
    val state = load() ?: return null
    if (!state.optString("deviceId").equals(device.deviceId, ignoreCase = true)) return null
    val current = state.optJSONObject("current")
    if (current != null && current.optLong("spkId") == spkID) {
      return current.optString("spkSk").ifEmpty { null }
    }
    val previous = state.optJSONArray("previous") ?: return null
    for (index in 0 until previous.length()) {
      val candidate = previous.optJSONObject(index) ?: continue
      if (candidate.optLong("spkId") == spkID) return candidate.optString("spkSk").ifEmpty { null }
    }
    return null
  }

  fun clear() = persistence.clear()

  private fun load(): JSONObject? {
    val encoded = try {
      persistence.load()
    } catch (cause: Exception) {
      Log.e(TAG, "No se pudo leer el estado de SPK de dispositivo", cause)
      return null
    } ?: return null
    return try {
      val decoded = JSONObject(String(encoded, Charsets.UTF_8))
      if (decoded.optInt("version") == STATE_VERSION) decoded else null
    } catch (cause: Exception) {
      Log.w(TAG, "El estado de SPK de dispositivo no es JSON válido", cause)
      null
    }
  }

  private fun isValid(value: JSONObject): Boolean =
    DhtLocation.isHex(value.optString("spkPub"), 32) &&
      DhtLocation.isHex(value.optString("spkSig"), 64) &&
      DhtLocation.isHex(value.optString("spkSk"), 32) &&
      value.optLong("spkId") > 0

  private fun isFresh(spkID: Long): Boolean {
    val now = nowMillis()
    return now >= spkID && now - spkID < ROTATION_INTERVAL_MS
  }

  private class KeystorePersistence(context: Context) : DeviceSpkPersistence {
    private val store = KeystoreStore(context, "upeer_device_spk", "upeer-device-spk", "device_spk_state")

    override fun load(): ByteArray? = store.load()
    override fun save(value: ByteArray) = store.save(value)
    override fun clear() = store.clear()
  }

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val STATE_VERSION = 1
    private const val MAX_PREVIOUS = 5
    private const val ROTATION_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
  }
}
