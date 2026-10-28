package app.upeer.transport

import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID

internal class GroupAdmin(
  private val identity: YggdrasilIdentity,
  private val store: ProtocolStateStore,
  private val egress: ProtocolEgress,
  private val sealer: GroupControlSealer,
  private val bridge: () -> Bridge?,
  private val onGroupChanged: (String) -> Unit,
) {
  fun create(name: String, memberIds: List<String>, avatar: String?): String {
    val self = requireSelf()
    val title = name.trim()
    require(title.isNotEmpty() && title.length <= ProtocolGroups.MAX_NAME) { "El nombre del grupo no es válido" }
    val invited = memberIds.map { it.lowercase() }.filter { it != self }.distinct()
    require(invited.all { ACCOUNT.matches(it) }) { "Hay un miembro con identificador inválido" }
    val members = listOf(self) + invited
    require(members.size <= GroupPayloadPolicy.MAX_MEMBERS) { "El grupo supera el máximo de miembros" }
    val groupId = ProtocolGroups.PREFIX + UUID.randomUUID().toString()
    val senderState = rotateSenderState(0)
    persist(groupId, title, self, members, "active", avatar, senderState)
    val keys = memberPublicKeys(members)
    var delivered = 0
    for (member in members) {
      if (sendInvite(groupId, member, title, members, keys, senderState, avatar, self)) delivered += 1
    }
    Log.i(TAG, "Grupo $groupId creado con ${members.size} miembros; $delivered invitaciones entregadas")
    onGroupChanged(groupId)
    return groupId
  }

  fun invite(groupId: String, memberId: String) {
    val group = requireGroup(groupId)
    val self = requireSelf()
    require(group.optString("adminUpeerId").lowercase() == self) { "Solo el administrador puede invitar" }
    val target = memberId.lowercase()
    require(ACCOUNT.matches(target)) { "El miembro no es válido" }
    val members = membersOf(group)
    if (target in members) return
    val updated = members + target
    val senderState = rotateSenderState(store.groupKeys.epoch(groupId) ?: 0)
    val title = group.optString("name")
    persist(groupId, title, self, updated, "active", null, senderState)
    val keys = memberPublicKeys(updated)
    if (!sendInvite(groupId, target, title, updated, keys, senderState, null, self)) {
      Log.w(TAG, "No se pudo entregar la invitación de $groupId a $target")
    }
    for (member in updated.filter { it != target }) {
      sendUpdate(groupId, member, self, updated, keys, senderState.epoch, senderState.senderKey)
    }
    onGroupChanged(groupId)
  }

  fun removeMember(groupId: String, memberId: String) {
    val group = requireGroup(groupId)
    val self = requireSelf()
    require(group.optString("adminUpeerId").lowercase() == self) { "Solo el administrador puede expulsar" }
    val target = memberId.lowercase()
    require(target != self) { "Para irte del grupo usa la opción de salir" }
    val members = membersOf(group)
    require(target in members) { "Ese miembro no está en el grupo" }
    val remaining = members - target
    val senderState = rotateSenderState(store.groupKeys.epoch(groupId) ?: 0)
    persist(groupId, group.optString("name"), self, remaining, "active", null, senderState)
    val keys = memberPublicKeys(remaining)
    for (member in remaining) {
      sendUpdate(groupId, member, self, remaining, keys, senderState.epoch, senderState.senderKey)
    }
    sendRevocation(groupId, target, self, remaining, keys, senderState.epoch + 1)
    onGroupChanged(groupId)
  }

  fun transferAdmin(groupId: String, memberId: String) {
    val group = requireGroup(groupId)
    val self = requireSelf()
    require(group.optString("adminUpeerId").lowercase() == self) { "Solo el administrador puede transferir el grupo" }
    val target = memberId.lowercase()
    val members = membersOf(group)
    require(target != self && target in members) { "No se puede transferir la administración a ese miembro" }
    val epoch = store.groupKeys.epoch(groupId)
    val senderKey = store.groupKeys.senderKey(groupId)
    require(epoch != null && senderKey != null) { "Falta el estado criptográfico del grupo" }
    persist(groupId, group.optString("name"), target, members, "active", null, SenderState(epoch, senderKey))
    val keys = memberPublicKeys(members)
    for (member in members.filter { it != self }) {
      sendAdminChange(groupId, member, self, target, members, keys)
    }
    onGroupChanged(groupId)
  }

  fun rotateAfterMemberLeft(groupId: String, memberId: String, remaining: List<String>) {
    val group = store.groups.get(groupId) ?: return
    val self = identity.current()?.upeerID() ?: return
    if (group.optString("adminUpeerId").lowercase() != self) return
    if (memberId !in membersOf(group)) return
    val senderState = rotateSenderState(store.groupKeys.epoch(groupId) ?: 0)
    persist(groupId, group.optString("name"), self, remaining, "active", null, senderState)
    val keys = memberPublicKeys(remaining)
    for (member in remaining) {
      sendUpdate(groupId, member, self, remaining, keys, senderState.epoch, senderState.senderKey)
    }
    sendRevocation(groupId, memberId, self, remaining, keys, senderState.epoch + 1)
    onGroupChanged(groupId)
  }

  fun leave(groupId: String) {
    val group = requireGroup(groupId)
    val self = requireSelf()
    val members = membersOf(group)
    if (group.optString("adminUpeerId").lowercase() == self && members.any { it != self }) {
      throw IllegalStateException("Transfiere la administración antes de salir del grupo")
    }
    val packet = JSONObject()
      .put("type", "GROUP_LEAVE")
      .put("groupId", groupId)
      .put("timestamp", System.currentTimeMillis())
    for (member in members.filter { it != self }) {
      if (!deliverRaw(groupId, member, packet)) Log.w(TAG, "No se pudo avisar de la salida de $groupId a $member")
    }
    store.deleteConversation(groupId)
    store.groups.delete(groupId)
    store.groupKeys.forget(groupId)
    onGroupChanged(groupId)
  }

  fun rename(groupId: String, name: String) {
    val group = requireGroup(groupId)
    val self = requireSelf()
    require(group.optString("adminUpeerId").lowercase() == self) { "Solo el administrador puede editar el grupo" }
    val title = name.trim()
    require(title.isNotEmpty() && title.length <= ProtocolGroups.MAX_NAME) { "El nombre del grupo no es válido" }
    val epoch = store.groupKeys.epoch(groupId)
    val senderKey = store.groupKeys.senderKey(groupId)
    require(epoch != null && senderKey != null) { "Falta el estado criptográfico del grupo" }
    persist(groupId, title, self, membersOf(group), "active", null, SenderState(epoch, senderKey))
    val payload = GroupControlPayload.update(name = title)
      ?: throw IllegalStateException("No se pudo preparar la actualización del grupo")
    for (member in membersOf(group).filter { it != self }) {
      deliverControl("GROUP_UPDATE", groupId, member, self, payload)
    }
    onGroupChanged(groupId)
  }

  private fun sendInvite(
    groupId: String,
    target: String,
    name: String,
    members: List<String>,
    keys: Map<String, String>,
    senderState: SenderState,
    avatar: String?,
    admin: String,
  ): Boolean {
    val payload = GroupControlPayload.invite(name, members, keys, senderState.epoch, senderState.senderKey, avatar)
      ?: return false
    return deliverControl("GROUP_INVITE", groupId, target, admin, payload)
  }

  private fun sendUpdate(
    groupId: String,
    target: String,
    admin: String,
    members: List<String>,
    keys: Map<String, String>,
    epoch: Int,
    senderKey: String,
  ): Boolean {
    val payload = GroupControlPayload.update(
      members = members,
      memberPublicKeys = keys,
      epoch = epoch,
      senderKey = senderKey,
    ) ?: return false
    return deliverControl("GROUP_UPDATE", groupId, target, admin, payload)
  }

  private fun sendRevocation(
    groupId: String,
    target: String,
    admin: String,
    members: List<String>,
    keys: Map<String, String>,
    epoch: Int,
  ): Boolean {
    val payload = GroupControlPayload.update(members = members, memberPublicKeys = keys, epoch = epoch)
      ?: return false
    return deliverControl("GROUP_UPDATE", groupId, target, admin, payload)
  }

  private fun sendAdminChange(
    groupId: String,
    target: String,
    admin: String,
    newAdmin: String,
    members: List<String>,
    keys: Map<String, String>,
  ): Boolean {
    val payload = GroupControlPayload.update(
      members = members,
      memberPublicKeys = keys,
      adminUpeerId = newAdmin,
    ) ?: return false
    return deliverControl("GROUP_UPDATE", groupId, target, admin, payload)
  }

  private fun deliverControl(
    type: String,
    groupId: String,
    target: String,
    admin: String,
    payload: String,
  ): Boolean {
    val sealed = sealer.seal(groupId, target, payload) ?: return false
    val packet = JSONObject()
      .put("type", type)
      .put("groupId", groupId)
      .put("adminUpeerId", admin)
    for (key in sealed.keys()) packet.put(key, sealed.get(key))
    return deliverRaw(groupId, target, packet)
  }

  private fun deliverRaw(groupId: String, target: String, packet: JSONObject): Boolean {
    val activeBridge = bridge() ?: return false
    val current = identity.current() ?: return false
    val address = store.addressFor(target) ?: return false
    val peerPublicKey = store.publicKeyFor(target) ?: return false
    val frame = SealedControl.tryFrame(current, null, activeBridge.address(), peerPublicKey, packet, "${packet.optString("type")} $groupId")
      ?: return false
    return egress.send(activeBridge, address, frame, "${packet.optString("type")} $groupId")
  }

  private fun persist(
    groupId: String,
    name: String,
    admin: String,
    members: List<String>,
    status: String,
    avatar: String?,
    senderState: SenderState,
  ) {
    val payload = JSONObject()
      .put("groupId", groupId)
      .put("name", name)
      .put("adminUpeerId", admin)
      .put("members", JSONArray(members))
      .put("status", status)
    avatar?.takeIf(String::isNotEmpty)?.let { payload.put("avatar", it) }
    if (!store.groups.upsert(payload, clearAvatar = false)) {
      throw IllegalStateException("No se pudo guardar el grupo $groupId")
    }
    store.groupKeys.put(groupId, senderState.epoch, senderState.senderKey)
  }

  private fun memberPublicKeys(members: List<String>): Map<String, String> {
    val keys = LinkedHashMap<String, String>()
    val self = identity.current()
    for (member in members) {
      val publicKey = if (self != null && member == self.upeerID()) {
        self.publicKeyHex()
      } else {
        store.publicKeyFor(member).orEmpty()
      }
      if (ACCOUNT.matches(publicKey)) keys[member] = publicKey.lowercase()
    }
    return keys
  }

  private fun membersOf(group: JSONObject): List<String> {
    val raw = group.optJSONArray("members") ?: return emptyList()
    return (0 until raw.length()).mapNotNull { index ->
      raw.optString(index).lowercase().takeIf(String::isNotEmpty)
    }
  }

  private fun requireGroup(groupId: String): JSONObject =
    store.groups.get(groupId) ?: throw IllegalArgumentException("El grupo ya no existe")

  private fun requireSelf(): String =
    identity.current()?.upeerID() ?: throw IllegalStateException("La identidad no está lista")

  private fun rotateSenderState(epoch: Int): SenderState {
    val next = epoch + 1
    require(ProtocolGroupKeys.validEpoch(next)) { "La época del grupo se ha agotado" }
    val key = ByteArray(SENDER_KEY_BYTES)
    RANDOM.nextBytes(key)
    return SenderState(next, key.joinToString("") { byte -> "%02x".format(byte) })
  }

  private data class SenderState(val epoch: Int, val senderKey: String)

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val SENDER_KEY_BYTES = 32
    private val ACCOUNT = Regex("^[0-9a-f]{64}$")
    private val RANDOM = SecureRandom()
  }
}
