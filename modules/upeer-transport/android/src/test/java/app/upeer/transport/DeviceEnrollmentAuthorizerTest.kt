package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceEnrollmentAuthorizerTest {
  private val account = "ab".repeat(32)
  private val authorizerDevice = "99".repeat(32)
  private val newDeviceId = "cd".repeat(32)
  private val newDevicePublicKey = "11".repeat(32)
  private val now = 1_800_000_000_000L
  private val inviteId = "ef".repeat(32)

  @Test
  fun createsAndPersistsAnInvitation() {
    val persistence = MemoryInvitePersistence()
    val authorizer = authorizer(persistence)

    val invite = JSONObject(authorizer.createInvite(0))

    assertEquals(account, invite.getString("accountUpeerId"))
    assertEquals("code-1", invite.getString("code"))
    assertEquals(inviteId, invite.getString("inviteId"))
    assertEquals(now + 300_000, invite.getLong("expiresAt"))
    assertEquals(1, persistence.saves)

    val pending = JSONObject(authorizer.pendingJSON()).getJSONArray("invites")
    assertEquals(1, pending.length())
    assertEquals(inviteId, pending.getJSONObject(0).getString("inviteId"))
  }

  @Test
  fun restoresPendingInvitationsAfterARestart() {
    val persistence = MemoryInvitePersistence()
    authorizer(persistence).createInvite(0)

    val restarted = authorizer(persistence)

    assertEquals(1, JSONObject(restarted.pendingJSON()).getJSONArray("invites").length())
  }

  @Test
  fun acceptsARequestAgainstThePendingInvitation() {
    val authorizer = authorizer(MemoryInvitePersistence())
    authorizer.createInvite(0)

    val accepted = JSONObject(authorizer.acceptRequest("sealed-request"))

    assertEquals(inviteId, accepted.getString("inviteId"))
    assertEquals(newDeviceId, accepted.getString("deviceId"))
    assertEquals(newDevicePublicKey, accepted.getString("devicePublicKey"))
    assertEquals("desktop", accepted.getString("platform"))
    assertEquals(newDeviceId, JSONObject(accepted.getString("requestJSON")).getString("deviceId"))
  }

  @Test
  fun requiresAPendingInvitationToAcceptARequest() {
    val authorizer = authorizer(MemoryInvitePersistence())

    val error = runCatching { authorizer.acceptRequest("sealed-request") }.exceptionOrNull()

    assertTrue(error is IllegalStateException)
  }

  @Test
  fun rejectsARequestThatMatchesNoInvitation() {
    val authorizer = authorizer(MemoryInvitePersistence(), openRequest = { _, _, _ -> throw IllegalArgumentException("nope") })
    authorizer.createInvite(0)

    val error = runCatching { authorizer.acceptRequest("sealed-request") }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
  }

  @Test
  fun issuesAndRegistersTheCertificate() {
    val persistence = MemoryInvitePersistence()
    val accountStub = StubAccount()
    val authorizer = authorizer(persistence, accountStub)
    authorizer.createInvite(0)
    val accepted = authorizer.acceptRequest("sealed-request")

    val issued = JSONObject(authorizer.complete(accepted, 0))

    assertEquals("sealed-response", issued.getString("code"))
    assertEquals(newDeviceId, issued.getString("deviceId"))
    assertEquals(7, issued.getLong("epoch"))
    assertEquals("desktop", issued.getString("platform"))
    assertEquals(account, issued.getString("accountUpeerId"))
    assertEquals(7, accountStub.issuedEpoch)
    assertEquals(1, accountStub.registered)
    assertEquals(accountStub.certificate, accountStub.registeredCertificate)
    assertEquals(0, JSONObject(authorizer.pendingJSON()).getJSONArray("invites").length())
  }

  @Test
  fun consumesTheInvitationOnlyOnce() {
    val authorizer = authorizer(MemoryInvitePersistence())
    authorizer.createInvite(0)
    val accepted = authorizer.acceptRequest("sealed-request")
    authorizer.complete(accepted, 0)

    val error = runCatching { authorizer.complete(accepted, 0) }.exceptionOrNull()

    assertTrue(error is IllegalStateException)
  }

  @Test
  fun refusesToAuthorizeWithoutTheAccount() {
    val authorizer = DeviceEnrollmentAuthorizer(MemoryInvitePersistence(), { null }, { now })

    val error = runCatching { authorizer.createInvite(0) }.exceptionOrNull()

    assertTrue(error is IllegalStateException)
  }

  @Test
  fun refusesACertificateFromAnotherAccount() {
    val accountStub = StubAccount()
    accountStub.certificate = JSONObject().put("accountUpeerId", "ff".repeat(32)).toString()
    val authorizer = authorizer(MemoryInvitePersistence(), accountStub)
    authorizer.createInvite(0)
    val accepted = authorizer.acceptRequest("sealed-request")

    val error = runCatching { authorizer.complete(accepted, 0) }.exceptionOrNull()

    assertTrue(error is IllegalStateException)
    assertEquals(0, accountStub.registered)
  }

  @Test
  fun dropsExpiredInvitations() {
    var clock = now
    val persistence = MemoryInvitePersistence()
    val authorizer = DeviceEnrollmentAuthorizer(persistence, { StubAccount() }, { clock })
    authorizer.createInvite(0)

    clock = now + 400_000
    val pending = JSONObject(authorizer.pendingJSON()).getJSONArray("invites")

    assertEquals(0, pending.length())
    assertFalse(persistence.value?.isNotEmpty() ?: false)
  }

  @Test
  fun cancelsAnInvitationById() {
    val authorizer = authorizer(MemoryInvitePersistence())
    authorizer.createInvite(0)

    authorizer.cancel(inviteId)

    assertEquals(0, JSONObject(authorizer.pendingJSON()).getJSONArray("invites").length())
  }

  @Test
  fun clearsEveryInvitation() {
    val authorizer = authorizer(MemoryInvitePersistence())
    authorizer.createInvite(0)

    authorizer.cancelAll()

    assertEquals(0, JSONObject(authorizer.pendingJSON()).getJSONArray("invites").length())
  }

  @Test
  fun survivesCorruptedInviteStorage() {
    val persistence = MemoryInvitePersistence()
    persistence.save("not json".toByteArray())

    val authorizer = authorizer(persistence)
    authorizer.createInvite(0)

    assertNotNull(JSONObject(authorizer.pendingJSON()).getJSONArray("invites"))
    assertEquals(1, JSONObject(authorizer.pendingJSON()).getJSONArray("invites").length())
  }

  private fun authorizer(
    persistence: MemoryInvitePersistence,
    accountStub: StubAccount? = StubAccount(),
    openRequest: (String, String, Long) -> String = { _, _, _ -> REQUEST_JSON },
  ): DeviceEnrollmentAuthorizer {
    accountStub?.openRequestHandler = openRequest
    return DeviceEnrollmentAuthorizer(persistence, { accountStub }, { now })
  }

  private inner class StubAccount : EnrollmentAuthorizerAccount {
    var openRequestHandler: (String, String, Long) -> String = { _, _, _ -> REQUEST_JSON }
    var certificate: String = CERTIFICATE_JSON
    var issuedEpoch = 0L
    var registered = 0
    var registeredCertificate = ""

    override val accountUpeerId: String
      get() = account

    override fun createInvite(ttlMillis: Long, nowMillis: Long): String = JSONObject()
      .put(
        "offer",
        JSONObject()
          .put("version", 1)
          .put("accountUpeerId", account)
          .put("accountPublicKey", "22".repeat(32))
          .put("authorizerDeviceId", authorizerDevice)
          .put("inviteId", inviteId)
          .put("expiresAt", nowMillis + if (ttlMillis <= 0) 300_000 else ttlMillis),
      )
      .put("secret", "33".repeat(32))
      .put("code", "code-1")
      .toString()

    override fun openRequest(inviteJSON: String, requestJSON: String, nowMillis: Long): String =
      openRequestHandler(inviteJSON, requestJSON, nowMillis)

    override fun issueCertificate(inviteJSON: String, requestJSON: String, epoch: Long, issuedAt: Long, ttlMillis: Long): String {
      issuedEpoch = epoch
      return "sealed-response"
    }

    override fun openCertificate(inviteJSON: String, sealedJSON: String, nowMillis: Long): String = certificate

    override fun nextEpoch(): Long = 7

    override fun registerDevice(deviceCertificateJSON: String): String {
      registered += 1
      registeredCertificate = deviceCertificateJSON
      return "{}"
    }
  }

  private class MemoryInvitePersistence : EnrollmentInvitePersistence {
    var value: ByteArray? = null
      private set
    var saves = 0
      private set

    override fun load(): ByteArray? = value?.copyOf()

    override fun save(value: ByteArray) {
      this.value = value.copyOf()
      saves += 1
    }

    override fun clear() {
      value = null
    }
  }

  private companion object {
    private val REQUEST_JSON = JSONObject()
      .put("version", 1)
      .put("inviteId", "ef".repeat(32))
      .put("accountUpeerId", "ab".repeat(32))
      .put("deviceId", "cd".repeat(32))
      .put("devicePublicKey", "11".repeat(32))
      .put("platform", "desktop")
      .put("capabilities", org.json.JSONArray().put("dht").put("location"))
      .put("requestedAt", 1_800_000_000_000L)
      .put("deviceSignature", "33".repeat(32))
      .toString()

    private val CERTIFICATE_JSON = JSONObject()
      .put("version", 1)
      .put("accountUpeerId", "ab".repeat(32))
      .put("accountPublicKey", "22".repeat(32))
      .put("deviceId", "cd".repeat(32))
      .put("devicePublicKey", "11".repeat(32))
      .put("platform", "desktop")
      .put("capabilities", org.json.JSONArray().put("dht").put("location"))
      .put("issuedAt", 1_800_000_000_000L)
      .put("expiresAt", 1_800_600_000_000L)
      .put("epoch", 7)
      .put("signature", "44".repeat(32))
      .toString()
  }
}
