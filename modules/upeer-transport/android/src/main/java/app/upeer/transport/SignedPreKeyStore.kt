package app.upeer.transport

import android.content.Context
import android.util.Log
import app.upeer.transportbridge.transportbridge.CryptoIdentity
import org.json.JSONArray
import org.json.JSONObject

internal class SignedPreKeyStore(context: Context) {
  private val store = KeystoreStore(
    context = context,
    preferencesName = "upeer_yggdrasil_spk",
    keyAlias = "upeer-yggdrasil-spk",
    valueKey = "signed_prekey_state",
  )

  fun currentJSON(identity: CryptoIdentity): String {
    val state = load()
    val current = state?.optJSONObject("current")
    if (current != null && isValid(current) && isFresh(current.optLong("spkId")) && signedByIdentity(identity, current)) {
      return current.toString()
    }
    val next = JSONObject(identity.createSignedPreKey(System.currentTimeMillis()))
    val previous = JSONArray()
    val candidates = buildList {
      if (current != null && isValid(current)) add(current)
      val stored = state?.optJSONArray("previous")
      if (stored != null) {
        for (index in 0 until stored.length()) {
          val candidate = stored.optJSONObject(index)
          if (candidate != null && isValid(candidate)) add(candidate)
        }
      }
    }
    candidates
      .distinctBy { it.optLong("spkId") }
      .filter { signedByIdentity(identity, it) }
      .take(MAX_PREVIOUS)
      .forEach(previous::put)
    val wrapper = JSONObject()
      .put("current", next)
      .put("previous", previous)
    store.save(wrapper.toString().toByteArray(Charsets.UTF_8))
    Log.i(TAG, "Signed Pre-Key creada: ${next.optLong("spkId")}")
    return next.toString()
  }

  fun secretFor(spkID: Long): String? {
    val state = load() ?: return null
    val current = state.optJSONObject("current")
    if (current != null && current.optLong("spkId") == spkID) return current.optString("spkSk").ifEmpty { null }
    val previous = state.optJSONArray("previous") ?: return null
    for (index in 0 until previous.length()) {
      val candidate = previous.optJSONObject(index) ?: continue
      if (candidate.optLong("spkId") == spkID) return candidate.optString("spkSk").ifEmpty { null }
    }
    return null
  }

  fun clear() = store.clear()

  private fun load(): JSONObject? {
    val encoded = try {
      store.load()
    } catch (cause: Exception) {
      Log.e(TAG, "No se pudo leer el estado de Signed Pre-Key", cause)
      return null
    } ?: return null
    return try {
      JSONObject(String(encoded, Charsets.UTF_8))
    } catch (cause: Exception) {
      Log.e(TAG, "El estado de Signed Pre-Key no es JSON válido", cause)
      null
    }
  }

  private fun isValid(value: JSONObject): Boolean =
    value.optString("spkPub").length == 64 &&
      value.optString("spkSig").length == 128 &&
      value.optString("spkSk").length == 64 &&
      value.optLong("spkId") > 0

  private fun isFresh(spkID: Long): Boolean {
    val now = System.currentTimeMillis()
    return now >= spkID && now - spkID < ROTATION_INTERVAL_MS
  }

  private fun signedByIdentity(identity: CryptoIdentity, value: JSONObject): Boolean =
    try {
      identity.verifySignedPreKey(identity.publicKeyHex(), value.optString("spkPub"), value.optString("spkSig"))
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo verificar la Signed Pre-Key cacheada: ${cause.message}")
      false
    }

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val MAX_PREVIOUS = 5
    private const val ROTATION_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
  }
}
