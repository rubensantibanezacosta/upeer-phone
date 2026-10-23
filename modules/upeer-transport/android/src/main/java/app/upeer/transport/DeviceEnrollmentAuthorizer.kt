package app.upeer.transport

import android.content.Context
import android.util.Log
import app.upeer.transport.dht.DhtLocation
import org.json.JSONArray
import org.json.JSONObject

internal interface EnrollmentInvitePersistence {
  fun load(): ByteArray?
  fun save(value: ByteArray)
  fun clear()
}

internal interface EnrollmentAuthorizerAccount {
  val accountUpeerId: String

  fun createInvite(ttlMillis: Long, nowMillis: Long): String

  fun openRequest(inviteJSON: String, requestJSON: String, nowMillis: Long): String

  fun issueCertificate(inviteJSON: String, requestJSON: String, epoch: Long, issuedAt: Long, ttlMillis: Long): String

  fun openCertificate(inviteJSON: String, sealedJSON: String, nowMillis: Long): String

  fun nextEpoch(): Long

  fun registerDevice(deviceCertificateJSON: String): String
}

internal class DeviceEnrollmentAuthorizer(
  private val persistence: EnrollmentInvitePersistence,
  private val account: () -> EnrollmentAuthorizerAccount?,
  private val nowMillis: () -> Long = System::currentTimeMillis,
) {
  constructor(context: Context, account: () -> EnrollmentAuthorizerAccount?) : this(
    EnrollmentInviteKeystorePersistence(context),
    account,
  )

  private val lock = Any()

  fun createInvite(ttlMillis: Long): String = synchronized(lock) {
    val active = requireAccount()
    val inviteJSON = active.createInvite(ttlMillis, nowMillis())
    val offer = JSONObject(inviteJSON).optJSONObject("offer")
      ?: throw IllegalStateException("La invitación generada no incluye la cuenta.")
    val inviteId = offer.optString("inviteId").lowercase()
    if (!DhtLocation.isHex(inviteId, 32)) {
      throw IllegalStateException("La invitación generada no tiene un identificador válido.")
    }
    val now = nowMillis()
    val pending = loadPending().filter { it.expiresAt > now }.toMutableList()
    pending.removeAll { it.inviteId == inviteId }
    pending.add(Invite(inviteId, inviteJSON, offer.optLong("expiresAt")))
    savePending(pending)
    JSONObject()
      .put("code", JSONObject(inviteJSON).optString("code"))
      .put("inviteId", inviteId)
      .put("expiresAt", offer.optLong("expiresAt"))
      .put("accountUpeerId", active.accountUpeerId)
      .put("authorizerDeviceId", offer.optString("authorizerDeviceId"))
      .toString()
  }

  fun acceptRequest(requestCode: String): String = synchronized(lock) {
    val active = requireAccount()
    val requestJSON = requestCode.trim()
    if (requestJSON.isEmpty()) throw IllegalArgumentException("La solicitud está vacía.")
    val pending = loadPending().filter { it.expiresAt > nowMillis() }
    if (pending.isEmpty()) throw IllegalStateException("No hay ninguna invitación pendiente.")
    var lastError: Exception? = null
    for (invite in pending.sortedByDescending { it.expiresAt }) {
      try {
        val opened = active.openRequest(invite.inviteJSON, requestJSON, nowMillis())
        val request = JSONObject(opened)
        return@synchronized JSONObject()
          .put("inviteId", invite.inviteId)
          .put("inviteJSON", invite.inviteJSON)
          .put("requestJSON", opened)
          .put("deviceId", request.optString("deviceId"))
          .put("devicePublicKey", request.optString("devicePublicKey"))
          .put("platform", request.optString("platform"))
          .put("capabilities", request.optJSONArray("capabilities") ?: JSONArray())
          .put("expiresAt", invite.expiresAt)
          .toString()
      } catch (cause: Exception) {
        lastError = cause
      }
    }
    throw IllegalArgumentException("La solicitud no corresponde a ninguna invitación vigente.", lastError)
  }

  fun complete(enrollmentJSON: String, ttlMillis: Long): String = synchronized(lock) {
    val active = requireAccount()
    val accepted = JSONObject(enrollmentJSON)
    val inviteId = accepted.optString("inviteId").lowercase()
    val inviteJSON = accepted.optString("inviteJSON")
    val requestJSON = accepted.optString("requestJSON")
    if (inviteJSON.isEmpty() || requestJSON.isEmpty()) {
      throw IllegalArgumentException("La solicitud aceptada no se puede leer.")
    }
    val invite = loadPending().firstOrNull { it.inviteId == inviteId }
      ?: throw IllegalStateException("La invitación ya se consumió o caducó.")
    val issuedAt = nowMillis()
    if (invite.expiresAt <= issuedAt) {
      discard(inviteId)
      throw IllegalStateException("La invitación ha caducado.")
    }
    val epoch = active.nextEpoch()
    val sealedResponse = active.issueCertificate(inviteJSON, requestJSON, epoch, issuedAt, ttlMillis)
    val certificateJSON = try {
      active.openCertificate(inviteJSON, sealedResponse, nowMillis())
    } catch (cause: Exception) {
      throw IllegalStateException("No se pudo verificar el certificado emitido.", cause)
    }
    val certificate = JSONObject(certificateJSON)
    if (certificate.optString("accountUpeerId").lowercase() != active.accountUpeerId.lowercase()) {
      throw IllegalStateException("El certificado emitido pertenece a otra cuenta.")
    }
    active.registerDevice(certificateJSON)
    discard(inviteId)
    JSONObject()
      .put("code", sealedResponse)
      .put("deviceId", certificate.optString("deviceId"))
      .put("devicePublicKey", certificate.optString("devicePublicKey"))
      .put("platform", certificate.optString("platform"))
      .put("epoch", certificate.optLong("epoch"))
      .put("issuedAt", certificate.optLong("issuedAt"))
      .put("expiresAt", certificate.optLong("expiresAt"))
      .put("accountUpeerId", certificate.optString("accountUpeerId"))
      .toString()
  }

  fun pendingJSON(): String = synchronized(lock) {
    val now = nowMillis()
    val stored = loadPending()
    val pending = stored.filter { it.expiresAt > now }
    if (pending.size != stored.size) savePending(pending)
    val array = JSONArray()
    pending.sortedByDescending { it.expiresAt }.forEach { invite ->
      val offer = runCatching { JSONObject(invite.inviteJSON).optJSONObject("offer") }.getOrNull()
      array.put(
        JSONObject()
          .put("inviteId", invite.inviteId)
          .put("expiresAt", invite.expiresAt)
          .put("accountUpeerId", offer?.optString("accountUpeerId").orEmpty())
          .put("authorizerDeviceId", offer?.optString("authorizerDeviceId").orEmpty()),
      )
    }
    JSONObject().put("invites", array).toString()
  }

  fun cancel(inviteId: String) = synchronized(lock) {
    discard(inviteId.trim().lowercase())
  }

  fun cancelAll() = synchronized(lock) {
    persistence.clear()
  }

  private fun requireAccount(): EnrollmentAuthorizerAccount =
    account() ?: throw IllegalStateException("La cuenta no puede autorizar dispositivos desde este dispositivo.")

  private fun discard(inviteId: String) {
    val stored = loadPending()
    val pending = stored.filter { it.inviteId != inviteId }
    if (pending.size != stored.size) savePending(pending)
  }

  private fun loadPending(): List<Invite> {
    val raw = try {
      persistence.load()
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudieron leer las invitaciones de emparejamiento cifradas", cause)
      return emptyList()
    } ?: return emptyList()
    return try {
      val root = JSONObject(String(raw, Charsets.UTF_8))
      if (root.optInt("version") != STATE_VERSION) return emptyList()
      val array = root.optJSONArray("invites") ?: return emptyList()
      (0 until array.length()).mapNotNull { index -> Invite.fromJSON(array.optJSONObject(index)) }
    } catch (cause: Exception) {
      Log.w(TAG, "Las invitaciones de emparejamiento almacenadas no son válidas; se descartan", cause)
      emptyList()
    } finally {
      raw.fill(0)
    }
  }

  private fun savePending(invites: List<Invite>) {
    if (invites.isEmpty()) {
      persistence.clear()
      return
    }
    val encoded = JSONObject()
      .put("version", STATE_VERSION)
      .put("invites", JSONArray().also { array -> invites.forEach { array.put(it.toJSON()) } })
      .toString()
      .toByteArray(Charsets.UTF_8)
    try {
      persistence.save(encoded)
    } finally {
      encoded.fill(0)
    }
  }

  private data class Invite(val inviteId: String, val inviteJSON: String, val expiresAt: Long) {
    fun toJSON(): JSONObject = JSONObject()
      .put("inviteId", inviteId)
      .put("invite", inviteJSON)
      .put("expiresAt", expiresAt)

    companion object {
      fun fromJSON(value: JSONObject?): Invite? {
        if (value == null) return null
        val inviteId = value.optString("inviteId").lowercase()
        val inviteJSON = value.optString("invite")
        val expiresAt = value.optLong("expiresAt")
        if (!DhtLocation.isHex(inviteId, 32) || inviteJSON.isEmpty() || expiresAt <= 0) return null
        return Invite(inviteId, inviteJSON, expiresAt)
      }
    }
  }

  private class EnrollmentInviteKeystorePersistence(context: Context) : EnrollmentInvitePersistence {
    private val store = KeystoreStore(context, "upeer_enrollment_invites", "upeer-enrollment-invites", "invite_state")

    override fun load(): ByteArray? = store.load()

    override fun save(value: ByteArray) = store.save(value)

    override fun clear() = store.clear()
  }

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val STATE_VERSION = 1
  }
}
