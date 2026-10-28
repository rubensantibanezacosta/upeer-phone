package app.upeer.transport

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

internal class ReputationReplies(
  private val store: ReputationStore,
  private val send: (String, JSONObject, String) -> Boolean,
) {
  fun handle(packet: JSONObject, senderUpeerId: String, type: String, now: Long): Boolean = when (type) {
    GOSSIP -> onGossip(packet, senderUpeerId, now)
    REQUEST -> onRequest(packet, senderUpeerId, now)
    DELIVER -> onDeliver(packet, senderUpeerId, now)
    else -> false
  }

  private fun onGossip(packet: JSONObject, senderUpeerId: String, now: Long): Boolean {
    val ids = idsOf(packet, "ids") ?: return false
    val missing = store.missing(ids, now)
    if (missing.isEmpty()) return true
    val request = JSONObject()
      .put("type", REQUEST)
      .put("missing", JSONArray(missing))
    if (!send(senderUpeerId, request, "reputación")) {
      Log.w(TAG, "No se pudo pedir ${missing.size} vouches a $senderUpeerId")
      return false
    }
    return true
  }

  private fun onRequest(packet: JSONObject, senderUpeerId: String, now: Long): Boolean {
    val wanted = idsOf(packet, "missing") ?: return false
    val vouches = store.deliverable(wanted)
    if (vouches.isEmpty()) return true
    val array = JSONArray()
    for (vouch in vouches) array.put(vouch.toJson())
    val delivery = JSONObject()
      .put("type", DELIVER)
      .put("vouches", array)
    if (!send(senderUpeerId, delivery, "reputación")) {
      Log.w(TAG, "No se pudieron entregar ${vouches.size} vouches a $senderUpeerId")
      return false
    }
    return true
  }

  private fun onDeliver(packet: JSONObject, senderUpeerId: String, now: Long): Boolean {
    val array = packet.optJSONArray("vouches") ?: return false
    if (array.length() == 0 || array.length() > ReputationVouch.DELIVER_MAX_VOUCHES) return false
    val parsed = ArrayList<ReputationVouch>(array.length())
    for (index in 0 until array.length()) {
      val vouch = ReputationVouch.parse(array.optJSONObject(index), now) ?: continue
      // La firma de cada vouch no se verifica aquí: solo se aceptan los emitidos por quien
      // entrega, así que el paquete firmado del emisor autentica el contenido recibido.
      if (vouch.fromId != senderUpeerId) continue
      parsed.add(vouch)
    }
    val accepted = store.accept(senderUpeerId, parsed, now)
    if (accepted > 0) Log.i(TAG, "$accepted vouches nuevos de $senderUpeerId")
    return true
  }

  private fun idsOf(packet: JSONObject, field: String): List<String>? {
    val array = packet.optJSONArray(field) ?: return null
    if (array.length() > ReputationVouch.GOSSIP_MAX_IDS) return null
    val ids = ArrayList<String>(array.length())
    for (index in 0 until array.length()) {
      val id = array.optString(index).lowercase()
      if (id.length !in 32..128) return null
      if (!id.all { it.isDigit() || it in 'a'..'f' }) return null
      ids.add(id)
    }
    return ids
  }

  companion object {
    const val GOSSIP = "REPUTATION_GOSSIP"
    const val REQUEST = "REPUTATION_REQUEST"
    const val DELIVER = "REPUTATION_DELIVER"
    val TYPES = setOf(GOSSIP, REQUEST, DELIVER)
    private const val TAG = "UPeerTransport"
  }
}
