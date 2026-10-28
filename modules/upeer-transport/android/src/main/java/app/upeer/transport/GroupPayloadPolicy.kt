package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject

internal data class GroupInviteFields(
  val name: String,
  val members: List<String>,
  val memberPublicKeys: Map<String, String>,
  val avatar: String?,
  val epoch: Int,
  val senderKey: String,
)

internal data class GroupUpdateFields(
  val name: String?,
  val avatar: String?,
  val removeAvatar: Boolean,
  val members: List<String>?,
  val memberPublicKeys: Map<String, String>?,
  val epoch: Int?,
  val senderKey: String?,
  val adminUpeerId: String?,
)

internal data class ExistingGroup(
  val adminUpeerId: String,
  val members: List<String>,
  val epoch: Int,
  val senderKey: String?,
)

internal data class GroupProjection(
  val name: String,
  val members: List<String>,
  val memberPublicKeys: Map<String, String>,
  val avatar: String?,
  val epoch: Int,
  val senderKey: String,
  val adminUpeerId: String,
)

internal sealed interface GroupDecision {
  data class Accept(val group: GroupProjection) : GroupDecision

  data class Reject(val reason: String) : GroupDecision
}

internal object GroupPayloadPolicy {
  const val MAX_NAME = 100
  const val MAX_MEMBERS = 500
  private const val MAX_AVATAR_CHARS = 2_000_000
  private val ACCOUNT = Regex("^[0-9a-f]{64}$")

  fun parseInvite(json: String): GroupInviteFields? {
    val payload = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val name = payload.optString("groupName")
    if (name.isEmpty() || name.length > MAX_NAME) return null
    val members = normalizeMembers(payload.optJSONArray("members") ?: return null) ?: return null
    val epoch = payload.optInt("epoch", 0)
    val senderKey = payload.optString("senderKey").lowercase()
    if (!ProtocolGroupKeys.validEpoch(epoch) || !ProtocolGroupKeys.validSenderKey(senderKey)) return null
    return GroupInviteFields(
      name = name,
      members = members,
      memberPublicKeys = memberPublicKeys(payload.optJSONObject("memberPublicKeys")),
      avatar = avatar(payload.opt("avatar")),
      epoch = epoch,
      senderKey = senderKey,
    )
  }

  fun parseUpdate(json: String): GroupUpdateFields? {
    val payload = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val rawAvatar = payload.opt("avatar")
    val epoch = payload.optLong("epoch", 0L)
    val members = payload.optJSONArray("members")?.let { normalizeMembers(it) }
    return GroupUpdateFields(
      name = payload.optString("groupName").takeIf { it.isNotEmpty() && it.length <= MAX_NAME },
      avatar = avatar(rawAvatar),
      removeAvatar = rawAvatar != null && rawAvatar === JSONObject.NULL,
      members = members,
      memberPublicKeys = payload.optJSONObject("memberPublicKeys")?.let(::memberPublicKeys),
      epoch = epoch.takeIf(ProtocolGroupKeys::validEpoch)?.toInt(),
      senderKey = payload.optString("senderKey").lowercase().takeIf(ProtocolGroupKeys::validSenderKey),
      adminUpeerId = payload.optString("adminUpeerId").lowercase()
        .takeIf { it.isNotEmpty() && it.length <= 128 },
    )
  }

  fun acceptInvite(
    fields: GroupInviteFields,
    adminUpeerId: String,
    senderUpeerId: String,
    myUpeerId: String,
    existing: ExistingGroup?,
  ): GroupDecision {
    val admin = adminUpeerId.lowercase().ifEmpty { senderUpeerId }
    if (admin != senderUpeerId) return GroupDecision.Reject("invite claims another administrator")
    if (admin !in fields.members) return GroupDecision.Reject("administrator is not a member")
    if (myUpeerId !in fields.members) return GroupDecision.Reject("invite does not include this account")
    if (existing == null) return GroupDecision.Accept(projection(fields, admin))
    if (existing.adminUpeerId != admin) {
      return GroupDecision.Reject("administrator changed without an update")
    }
    if (fields.epoch < existing.epoch) return GroupDecision.Reject("stale invite")
    if (fields.epoch == existing.epoch && existing.senderKey != null && existing.senderKey != fields.senderKey) {
      return GroupDecision.Reject("conflicting sender key for the same epoch")
    }
    if (fields.epoch == existing.epoch && existing.members.toSet() != fields.members.toSet()) {
      return GroupDecision.Reject("membership change without an epoch advance")
    }
    return GroupDecision.Accept(projection(fields, admin))
  }

  fun acceptUpdate(
    fields: GroupUpdateFields,
    claimedAdminUpeerId: String,
    senderUpeerId: String,
    myUpeerId: String,
    existing: ExistingGroup,
  ): GroupDecision {
    val claimed = claimedAdminUpeerId.lowercase().ifEmpty { senderUpeerId }
    if (existing.adminUpeerId != claimed || claimed != senderUpeerId) {
      return GroupDecision.Reject("update from a non-admin")
    }
    val isRevocation = fields.members != null && myUpeerId !in fields.members
    if ((fields.epoch != null) != (fields.senderKey != null) && !isRevocation) {
      return GroupDecision.Reject("partial sender state")
    }
    if (fields.epoch != null && fields.epoch < existing.epoch) return GroupDecision.Reject("stale update")
    if (isRevocation && (fields.epoch == null || fields.epoch <= existing.epoch)) {
      return GroupDecision.Reject("revocation without a newer epoch")
    }
    if (fields.members != null && fields.epoch != null && fields.epoch == existing.epoch &&
      fields.members.toSet() != existing.members.toSet()
    ) {
      return GroupDecision.Reject("membership change without an epoch advance")
    }
    if (fields.members != null && existing.adminUpeerId !in fields.members &&
      (fields.adminUpeerId == null || fields.adminUpeerId !in fields.members)
    ) {
      return GroupDecision.Reject("membership change removes the administrator without a successor")
    }
    val epoch = fields.epoch ?: existing.epoch
    val senderKey = fields.senderKey ?: existing.senderKey
    if (fields.epoch != null && fields.epoch == existing.epoch && senderKey != existing.senderKey) {
      return GroupDecision.Reject("conflicting sender key for the same epoch")
    }
    if (senderKey == null) return GroupDecision.Reject("update without sender key")
    return GroupDecision.Accept(
      GroupProjection(
        name = fields.name ?: "",
        members = fields.members ?: existing.members,
        memberPublicKeys = fields.memberPublicKeys ?: emptyMap(),
        avatar = if (fields.removeAvatar) null else fields.avatar,
        epoch = epoch,
        senderKey = senderKey,
        adminUpeerId = fields.adminUpeerId ?: existing.adminUpeerId,
      ),
    )
  }

  private fun projection(fields: GroupInviteFields, adminUpeerId: String) = GroupProjection(
    name = fields.name,
    members = fields.members,
    memberPublicKeys = fields.memberPublicKeys,
    avatar = fields.avatar,
    epoch = fields.epoch,
    senderKey = fields.senderKey,
    adminUpeerId = adminUpeerId,
  )

  private fun normalizeMembers(members: JSONArray): List<String>? {
    val result = LinkedHashSet<String>()
    for (index in 0 until members.length()) {
      val value = members.optString(index).lowercase()
      if (!ACCOUNT.matches(value)) return null
      result.add(value)
    }
    return if (result.isEmpty()) null else result.toList()
  }

  private fun memberPublicKeys(raw: JSONObject?): Map<String, String> {
    if (raw == null) return emptyMap()
    val result = LinkedHashMap<String, String>()
    for (key in raw.keys()) {
      val account = key.lowercase()
      val publicKey = raw.optString(key).lowercase()
      if (!ACCOUNT.matches(account) || !ACCOUNT.matches(publicKey)) continue
      result[account] = publicKey
    }
    return result
  }

  private fun avatar(raw: Any?): String? {
    if (raw !is String) return null
    if (!raw.startsWith("data:image/") || raw.length > MAX_AVATAR_CHARS) return null
    return raw
  }
}
