package app.upeer.transport

import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge
import app.upeer.transportbridge.transportbridge.CryptoIdentity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal class GroupOutbound(
  private val identity: YggdrasilIdentity,
  private val state: ProtocolStateStore,
  private val deviceBindings: DeviceMessagingBindings,
  private val egress: ProtocolEgress,
  private val bridge: () -> Bridge?,
) {
  fun sendMessage(groupId: String, content: String, replyTo: String): String {
    val text = ChatSendPolicy.groupText(groupId, content, replyTo)
    val group = state.groups.get(groupId) ?: throw IllegalArgumentException("El grupo ya no existe")
    if (group.optString("status") != "active") {
      throw IllegalArgumentException("El grupo no está disponible en este dispositivo")
    }
    val current = identity.current() ?: throw IllegalStateException("La identidad no está lista")
    val senderKey = state.groupKeys.senderKey(groupId)
      ?: throw IllegalStateException("Falta la clave del grupo")
    val sealed = GroupMessageCrypto.seal(senderKey, text)
      ?: throw IllegalStateException("No se pudo cifrar el mensaje del grupo")
    val messageID = UUID.randomUUID().toString()
    val timestamp = System.currentTimeMillis()
    val packet = GroupMessagePacket.build(
      groupId = groupId,
      messageID = messageID,
      senderPublicKey = current.publicKeyHex(),
      timestamp = timestamp,
      sealed = sealed,
      epoch = state.groupKeys.epoch(groupId) ?: 0,
      replyTo = replyTo,
    )
    state.messages.save(
      JSONObject()
        .put("id", messageID)
        .put("upeerId", groupId)
        .put("isMine", true)
        .put("content", text)
        .put("status", "pending")
        .put("version", 1)
        .put("isEdited", false)
        .put("timestamp", timestamp)
        .apply { if (replyTo.isNotEmpty()) put("replyTo", replyTo) },
    )
    val delivered = fanOut(groupId, group, packet)
    state.messages.markStatus(messageID, if (delivered > 0) "sent" else "failed")
    if (delivered == 0) Log.w(TAG, "GROUP_MSG $messageID sin destinatarios alcanzables en $groupId")
    return messageID
  }

  private fun fanOut(groupId: String, group: JSONObject, packet: JSONObject): Int {
    val activeBridge = bridge() ?: return 0
    val current = identity.current() ?: return 0
    val selfAddress = activeBridge.address()
    var delivered = 0
    for (member in GroupMessagePacket.recipients(members(group), current.upeerID())) {
      val peer = state.peerFor(member) ?: continue
      val address = peer.optString("address")
      if (address.isBlank()) continue
      val publicKey = peer.optString("publicKey")
      if (publicKey.length != 64) continue
      val frame = SealedControl.tryFrame(current, deviceFor(peer, member, current), selfAddress, publicKey, packet, "GROUP_MSG $groupId")
      if (frame == null) continue
      if (egress.send(activeBridge, address, frame, "GROUP_MSG $groupId")) {
        delivered++
      }
    }
    return delivered
  }

  private fun deviceFor(peer: JSONObject, member: String, current: CryptoIdentity): DeviceIdentity? {
    if (deviceBindings.bindingFor(peer, member) == null) return null
    return identity.currentDevice()?.takeIf { it.accountUpeerId == current.upeerID() }
  }

  private fun members(group: JSONObject): List<String> {
    val raw: JSONArray = group.optJSONArray("members") ?: return emptyList()
    return (0 until raw.length()).mapNotNull { index ->
      raw.optString(index).takeIf(String::isNotEmpty)
    }
  }

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
