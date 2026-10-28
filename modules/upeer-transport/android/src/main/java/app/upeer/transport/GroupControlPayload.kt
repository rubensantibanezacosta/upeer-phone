package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject

internal object GroupControlPayload {
  private const val MAX_SERIALIZED = 90_000

  fun invite(
    name: String,
    members: List<String>,
    memberPublicKeys: Map<String, String>,
    epoch: Int,
    senderKey: String,
    avatar: String?,
  ): String? {
    if (name.isEmpty() || name.length > GroupPayloadPolicy.MAX_NAME) return null
    if (members.isEmpty() || members.size > GroupPayloadPolicy.MAX_MEMBERS) return null
    if (!ProtocolGroupKeys.validEpoch(epoch) || !ProtocolGroupKeys.validSenderKey(senderKey)) return null
    val payload = JSONObject()
      .put("groupName", name)
      .put("members", JSONArray(members))
      .put("memberPublicKeys", JSONObject(memberPublicKeys))
      .put("epoch", epoch)
      .put("senderKey", senderKey.lowercase())
    avatar?.takeIf(String::isNotEmpty)?.let { payload.put("avatar", it) }
    return serialize(payload)
  }

  fun update(
    name: String? = null,
    avatar: String? = null,
    removeAvatar: Boolean = false,
    members: List<String>? = null,
    memberPublicKeys: Map<String, String>? = null,
    epoch: Int? = null,
    senderKey: String? = null,
    adminUpeerId: String? = null,
  ): String? {
    if (name == null && avatar == null && !removeAvatar && members == null && epoch == null &&
      senderKey == null && adminUpeerId == null
    ) {
      return null
    }
    if (name != null && (name.isEmpty() || name.length > GroupPayloadPolicy.MAX_NAME)) return null
    if (members != null && (members.isEmpty() || members.size > GroupPayloadPolicy.MAX_MEMBERS)) return null
    if (epoch != null && !ProtocolGroupKeys.validEpoch(epoch)) return null
    if (senderKey != null && !ProtocolGroupKeys.validSenderKey(senderKey)) return null
    val payload = JSONObject()
    name?.let { payload.put("groupName", it) }
    when {
      removeAvatar -> payload.put("avatar", JSONObject.NULL)
      avatar != null -> payload.put("avatar", avatar)
    }
    members?.let { payload.put("members", JSONArray(it)) }
    memberPublicKeys?.let { payload.put("memberPublicKeys", JSONObject(it)) }
    epoch?.let { payload.put("epoch", it) }
    senderKey?.let { payload.put("senderKey", it.lowercase()) }
    adminUpeerId?.let { payload.put("adminUpeerId", it.lowercase()) }
    return serialize(payload)
  }

  private fun serialize(payload: JSONObject): String? =
    payload.toString().takeIf { it.length <= MAX_SERIALIZED }
}
