package app.upeer.transport

import android.util.Log
import org.json.JSONObject

internal class InboundPolicy(private val rateLimiter: InboundRateLimiter) {
  fun allow(metadata: JSONObject, sourceAddress: String): Boolean {
    val type = metadata.optString("type")
    val senderUpeerId = metadata.optString("senderUpeerId")
    return rateLimiter.allow("ip:$sourceAddress", type) && rateLimiter.allow("id:$senderUpeerId", type)
  }

  fun rateLimited(metadata: JSONObject): String {
    Log.w(TAG, "Límite de recepción alcanzado para ${metadata.optString("type")} de ${metadata.optString("senderUpeerId")}")
    return JSONObject(metadata.toString()).put("rateLimited", true).toString()
  }

  companion object {
    private const val TAG = "UPeerTransport"

    fun blocked(packet: JSONObject, senderUpeerId: String): String = JSONObject()
      .put("type", packet.optString("type"))
      .put("senderUpeerId", senderUpeerId)
      .put("blocked", true)
      .toString()

    fun rejected(packet: JSONObject): String = JSONObject()
      .put("type", packet.optString("type"))
      .put("senderUpeerId", packet.optString("senderUpeerId").lowercase())
      .put("rejected", true)
      .toString()

    fun rateLimitedMetadata(type: String): String = JSONObject()
      .put("type", type)
      .put("rateLimited", true)
      .toString()
  }
}
