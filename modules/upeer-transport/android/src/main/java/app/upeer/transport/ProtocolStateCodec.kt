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
          "messages=${decoded.optJSONArray("messages")?.length() ?: 0}",
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

  private const val TAG = "UPeerTransport"
}
