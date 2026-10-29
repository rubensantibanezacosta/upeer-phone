package app.upeer.transport

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

internal object ProtocolStateCodec {
  fun load(store: KeystoreStore): JSONObject {
    val encoded = try {
      store.load()
    } catch (cause: Exception) {
      Log.e(TAG, "No se pudo leer el estado del protocolo", cause)
      return empty()
    } ?: return empty()
    return try {
      val decoded = JSONObject(String(encoded, Charsets.UTF_8))
      if (decoded.optJSONObject("peers") == null) decoded.put("peers", JSONObject())
      if (decoded.optJSONObject("sessions") == null) decoded.put("sessions", JSONObject())
      if (decoded.optJSONArray("messages") == null) decoded.put("messages", JSONArray())
      if (decoded.optJSONObject("sync") == null) decoded.put("sync", JSONObject())
      Log.i(
        TAG,
        "Estado del protocolo cargado: peers=${decoded.optJSONObject("peers")?.length() ?: 0} " +
          "sessions=${decoded.optJSONObject("sessions")?.length() ?: 0} " +
          "messages=${decoded.optJSONArray("messages")?.length() ?: 0} " +
          "bytes=${encoded.size} eventos=${decoded.optJSONObject("sync")?.optJSONArray("events")?.length() ?: 0} ${sizes(decoded)} ${eventFields(decoded)}",
      )
      decoded
    } catch (cause: Exception) {
      Log.e(TAG, "El estado del protocolo no es JSON válido", cause)
      empty()
    }
  }

  fun empty(): JSONObject = JSONObject()
    .put("peers", JSONObject())
    .put("sessions", JSONObject())
    .put("messages", JSONArray())
    .put("sync", JSONObject())

  private fun eventFields(state: JSONObject): String {
    val events = state.optJSONObject("sync")?.optJSONArray("events") ?: return ""
    val sample = events.optJSONObject(0) ?: return ""
    val parts = ArrayList<String>()
    val keys = sample.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      parts.add("$key=${sample.opt(key)?.toString()?.toByteArray(Charsets.UTF_8)?.size ?: 0}")
    }
    return "evento0[$parts]"
  }

  private fun sizes(state: JSONObject): String {    val parts = ArrayList<String>()
    val keys = state.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      parts.add("$key=${state.opt(key)?.toString()?.toByteArray(Charsets.UTF_8)?.size ?: 0}")
    }
    parts.sortByDescending { it.substringAfter('=').toIntOrNull() ?: 0 }
    return parts.joinToString(" ")
  }

  private const val TAG = "UPeerTransport"
}
