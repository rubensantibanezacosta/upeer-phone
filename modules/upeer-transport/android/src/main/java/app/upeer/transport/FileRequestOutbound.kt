package app.upeer.transport

import android.util.Log
import org.json.JSONObject

internal class FileRequestOutbound(
  private val send: (String, JSONObject, String) -> Boolean,
) {
  fun request(upeerId: String, fileId: String, messageId: String?, chatUpeerId: String?): Boolean {
    if (upeerId.isBlank() || fileId.isBlank()) return false
    val packet = JSONObject()
      .put("type", "FILE_REQUEST")
      .put("fileId", fileId)
    if (!messageId.isNullOrBlank()) packet.put("messageId", messageId)
    if (!chatUpeerId.isNullOrBlank()) packet.put("chatUpeerId", chatUpeerId)
    if (send(upeerId, packet, "FILE_REQUEST")) return true
    Log.w(TAG, "No se pudo pedir el fichero $fileId a $upeerId")
    return false
  }

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
