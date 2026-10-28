package app.upeer.transport

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

internal class GroupInbound(
  private val state: ProtocolStateStore,
  private val decryptor: YggdrasilChatDecryptor,
  private val controlSender: ControlSender,
  private val onInboundMessage: (String, String, String) -> Unit,
  private val onGroupChanged: (String) -> Unit = {},
  private val onMemberLeft: (String, String, List<String>) -> Unit = { _, _, _ -> },
  private val myUpeerId: () -> String,
) {
  fun receiveInvite(
    packet: JSONObject,
    senderUpeerId: String,
    senderIdentityKey: String,
    isDevicePacket: Boolean,
    metadataJSON: String,
  ): String {
    val groupId = packet.optString("groupId")
    if (!ProtocolGroups.validId(groupId)) throw IllegalArgumentException("GROUP_INVITE groupId is invalid")
    val plaintext = decryptControl(packet, groupId, senderUpeerId, senderIdentityKey, isDevicePacket, "GROUP_INVITE")
      ?: return metadataJSON
    val fields = GroupPayloadPolicy.parseInvite(plaintext)
    if (fields == null) {
      Log.w(TAG, "GROUP_INVITE de $senderUpeerId con payload inválido en $groupId")
      return metadataJSON
    }
    val self = myUpeerId()
    val decision = GroupPayloadPolicy.acceptInvite(
      fields = fields,
      adminUpeerId = packet.optString("adminUpeerId"),
      senderUpeerId = senderUpeerId,
      myUpeerId = self,
      existing = existing(groupId),
    )
    when (decision) {
      is GroupDecision.Reject -> Log.w(TAG, "GROUP_INVITE de $senderUpeerId rechazada en $groupId: ${decision.reason}")
      is GroupDecision.Accept -> apply(groupId, decision.group, "active", clearAvatar = false)
    }
    return metadataJSON
  }

  fun receiveUpdate(
    packet: JSONObject,
    senderUpeerId: String,
    senderIdentityKey: String,
    isDevicePacket: Boolean,
    metadataJSON: String,
  ): String {
    val groupId = packet.optString("groupId")
    if (!ProtocolGroups.validId(groupId)) throw IllegalArgumentException("GROUP_UPDATE groupId is invalid")
    val current = existing(groupId)
    if (current == null) {
      Log.w(TAG, "GROUP_UPDATE de $senderUpeerId para un grupo desconocido $groupId")
      return metadataJSON
    }
    val plaintext = decryptControl(packet, groupId, senderUpeerId, senderIdentityKey, isDevicePacket, "GROUP_UPDATE")
      ?: return metadataJSON
    val fields = GroupPayloadPolicy.parseUpdate(plaintext)
    if (fields == null) {
      Log.w(TAG, "GROUP_UPDATE de $senderUpeerId con payload inválido en $groupId")
      return metadataJSON
    }
    when (val decision = GroupPayloadPolicy.acceptUpdate(
      fields = fields,
      claimedAdminUpeerId = packet.optString("adminUpeerId"),
      senderUpeerId = senderUpeerId,
      myUpeerId = myUpeerId(),
      existing = current,
    )) {
      is GroupDecision.Reject -> Log.w(TAG, "GROUP_UPDATE de $senderUpeerId rechazada en $groupId: ${decision.reason}")
      is GroupDecision.Accept -> {
        val name = decision.group.name.ifEmpty { state.groups.title(groupId) }
        apply(
          groupId,
          decision.group.copy(name = name),
          status = if (myUpeerId() in decision.group.members) "active" else "removed",
          clearAvatar = fields.removeAvatar,
        )
      }
    }
    return metadataJSON
  }

  fun receiveLeave(packet: JSONObject, senderUpeerId: String, metadataJSON: String): String {
    val groupId = packet.optString("groupId")
    if (!ProtocolGroups.validId(groupId)) throw IllegalArgumentException("GROUP_LEAVE groupId is invalid")
    val group = state.groups.get(groupId) ?: return metadataJSON
    val self = myUpeerId()
    val members = members(group)
    if (senderUpeerId !in members) return metadataJSON
    if (senderUpeerId == self) {
      state.messages.deleteConversation(groupId)
      state.groups.delete(groupId)
      state.groupKeys.forget(groupId)
      onGroupChanged(groupId)
      Log.i(TAG, "El grupo $groupId se borró tras confirmarse la salida desde otro dispositivo")
      return metadataJSON
    }
    val remaining = members - senderUpeerId
    val admin = group.optString("adminUpeerId").lowercase()
    if (senderUpeerId == admin || remaining.isEmpty()) {
      applyStatus(groupId, group, remaining, "removed")
      return metadataJSON
    }
    if (admin == self) {
      onMemberLeft(groupId, senderUpeerId, remaining)
      return metadataJSON
    }
    applyStatus(groupId, group, remaining, group.optString("status").ifEmpty { "active" })
    Log.i(TAG, "El miembro $senderUpeerId dejó el grupo $groupId; quedan ${remaining.size}")
    return metadataJSON
  }

  fun receiveMessage(packet: JSONObject, senderUpeerId: String, metadataJSON: String): String {
    val groupId = packet.optString("groupId")
    if (!ProtocolGroups.validId(groupId)) throw IllegalArgumentException("GROUP_MSG groupId is invalid")
    val messageID = packet.optString("id")
    if (!ReplayPolicy.validMessageID(messageID)) throw IllegalArgumentException("GROUP_MSG message id must be a UUID")
    val group = state.groups.get(groupId)
    if (group == null) {
      Log.w(TAG, "GROUP_MSG de $senderUpeerId para un grupo desconocido $groupId")
      return metadataJSON
    }
    if (group.optString("status") == "removed") {
      Log.i(TAG, "GROUP_MSG descartado: el grupo $groupId está eliminado en este dispositivo")
      return metadataJSON
    }
    if (senderUpeerId !in members(group)) {
      Log.w(TAG, "GROUP_MSG de $senderUpeerId rechazado: no es miembro de $groupId")
      return metadataJSON
    }
    val epoch = packet.optInt("epoch", 0)
    if (!state.groupKeys.matches(groupId, epoch)) {
      Log.w(TAG, "GROUP_MSG de $senderUpeerId con epoch desconocido $epoch en $groupId")
      return metadataJSON
    }
    val existingOwner = state.messages.owner(messageID)
    if (existingOwner != null) {
      if (existingOwner != groupId) throw IllegalArgumentException("GROUP_MSG message ID collision across conversations")
      controlSender.scheduleGroupAck(senderUpeerId, messageID, groupId)
      return metadataJSON
    }
    val senderKey = state.groupKeys.senderKey(groupId)
    val plaintext = senderKey?.let {
      GroupMessageCrypto.open(it, packet.optString("nonce"), packet.optString("content"))
    }
    if (senderKey == null || plaintext == null) {
      Log.w(TAG, "GROUP_MSG de $senderUpeerId no se pudo descifrar en $groupId")
      return metadataJSON
    }
    val replyTo = packet.optString("replyTo")
    state.messages.save(
      JSONObject()
        .put("id", messageID)
        .put("upeerId", groupId)
        .put("isMine", false)
        .put("content", plaintext)
        .put("status", "delivered")
        .put("version", packet.optInt("version", 1))
        .put("isEdited", false)
        .put("timestamp", packet.optLong("timestamp", System.currentTimeMillis()))
        .put("receivedAt", System.currentTimeMillis())
        .apply { if (ReplayPolicy.validMessageID(replyTo)) put("replyTo", replyTo) },
    )
    controlSender.scheduleGroupAck(senderUpeerId, messageID, groupId)
    Log.i(TAG, "GROUP_MSG descifrado de $senderUpeerId en $groupId (${plaintext.length} caracteres)")
    try {
      onInboundMessage(groupId, state.groups.title(groupId), plaintext)
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo notificar el mensaje de grupo $groupId: ${message(cause)}")
    }
    return metadataJSON
  }

  private fun decryptControl(
    packet: JSONObject,
    groupId: String,
    senderUpeerId: String,
    senderIdentityKey: String,
    isDevicePacket: Boolean,
    label: String,
  ): String? {
    val plaintext = decryptor.decryptControl(
      packet = packet,
      field = "payload",
      senderUpeerId = senderUpeerId,
      publicKey = senderIdentityKey,
      isDevicePacket = isDevicePacket,
      context = "group:$groupId",
    )
    if (plaintext == null) Log.w(TAG, "$label de $senderUpeerId no se pudo descifrar en $groupId")
    return plaintext
  }

  private fun apply(groupId: String, group: GroupProjection, status: String, clearAvatar: Boolean) {
    val members = JSONArray()
    for (member in group.members) members.put(member)
    val payload = JSONObject()
      .put("groupId", groupId)
      .put("name", group.name)
      .put("adminUpeerId", group.adminUpeerId)
      .put("members", members)
      .put("status", status)
    group.avatar?.let { payload.put("avatar", it) }
    if (!state.groups.upsert(payload, clearAvatar = clearAvatar)) {
      Log.w(TAG, "No se pudo guardar el grupo $groupId con los datos recibidos")
      return
    }
    state.groupKeys.put(groupId, group.epoch, group.senderKey)
    onGroupChanged(groupId)
  }

  private fun applyStatus(groupId: String, group: JSONObject, members: List<String>, status: String) {
    val list = JSONArray()
    for (member in members) list.put(member)
    val payload = JSONObject()
      .put("groupId", groupId)
      .put("name", group.optString("name"))
      .put("adminUpeerId", group.optString("adminUpeerId"))
      .put("members", list)
      .put("status", status)
    if (!state.groups.upsert(payload, clearAvatar = false)) {
      Log.w(TAG, "No se pudo actualizar la lista de miembros de $groupId")
      return
    }
    onGroupChanged(groupId)
  }

  private fun existing(groupId: String): ExistingGroup? {
    val stored = state.groups.get(groupId) ?: return null
    return ExistingGroup(
      adminUpeerId = stored.optString("adminUpeerId").lowercase(),
      members = members(stored),
      epoch = state.groupKeys.epoch(groupId) ?: 0,
      senderKey = state.groupKeys.senderKey(groupId),
    )
  }

  private fun members(group: JSONObject): List<String> {
    val raw = group.optJSONArray("members") ?: return emptyList()
    return (0 until raw.length()).mapNotNull { index ->
      raw.optString(index).takeIf(String::isNotEmpty)
    }
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
