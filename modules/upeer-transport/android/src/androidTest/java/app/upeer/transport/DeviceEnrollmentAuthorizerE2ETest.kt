package app.upeer.transport

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.upeer.transport.dht.DhtLocation
import app.upeer.transportbridge.transportbridge.CryptoIdentity
import app.upeer.transportbridge.transportbridge.Transportbridge
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class DeviceEnrollmentAuthorizerE2ETest {
  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

  @Test
  fun authorizesASecondDeviceAndRegistersIt() {
    val accountSeed = ByteArray(32).also(SecureRandom()::nextBytes)
    val newDeviceSeed = ByteArray(32).also(SecureRandom()::nextBytes)
    val accountSecret = deriveSecretKey(accountSeed)
    val newDeviceSecret = deriveSecretKey(newDeviceSeed)
    val account = CryptoIdentity(accountSecret)
    val newDevice = CryptoIdentity(newDeviceSecret)

    val identity = YggdrasilIdentity(context)
    identity.set(accountSecret)
    val authorizer = DeviceEnrollmentAuthorizer(context) { identity.authorizerAccount() }
    authorizer.cancelAll()

    try {
      val invite = JSONObject(authorizer.createInvite(0))
      val code = invite.getString("code")
      assertTrue(code.isNotEmpty())
      assertEquals(account.upeerID(), invite.getString("accountUpeerId"))

      val inviteJSON = Transportbridge.parseEnrollmentInvite(code, System.currentTimeMillis())
      val sealedRequest = newDevice.buildEnrollmentRequest(inviteJSON, "desktop", System.currentTimeMillis())

      val accepted = JSONObject(authorizer.acceptRequest(sealedRequest))
      assertEquals(invite.getString("inviteId"), accepted.getString("inviteId"))
      assertEquals("desktop", accepted.getString("platform"))
      assertEquals(deriveDeviceId(newDevice.publicKeyHex()), accepted.getString("deviceId"))

      val issued = JSONObject(authorizer.complete(accepted.toString(), 0))
      assertEquals(deriveDeviceId(newDevice.publicKeyHex()), issued.getString("deviceId"))
      assertEquals("desktop", issued.getString("platform"))
      assertEquals(account.upeerID(), issued.getString("accountUpeerId"))
      assertTrue(issued.getLong("epoch") >= 1)

      val opened = Transportbridge.openEnrollmentCertificate(
        inviteJSON,
        issued.getString("code"),
        account.upeerID(),
        System.currentTimeMillis(),
      )
      val certificate = JSONObject(opened)
      assertEquals(issued.getString("deviceId"), certificate.getString("deviceId"))
      assertEquals(deriveDeviceId(newDevice.publicKeyHex()), certificate.getString("deviceId"))
      assertEquals(account.upeerID(), certificate.getString("accountUpeerId"))

      val registry = JSONObject(identity.currentRegistryJSON())
      val devices = registry.optJSONArray("devices") ?: error("registry has no devices")
      assertTrue("registry should list at least two devices", devices.length() >= 2)
      val registered = (0 until devices.length()).map { devices.getJSONObject(it).getString("deviceId") }
      assertTrue(registered.contains(certificate.getString("deviceId")))
      assertEquals(0, JSONObject(authorizer.pendingJSON()).getJSONArray("invites").length())
    } finally {
      authorizer.cancelAll()
      identity.clear()
      accountSecret.fill(0)
      newDeviceSecret.fill(0)
    }
  }

  @Test
  fun refusesARequestWithoutAPendingInvitation() {
    val accountSecret = deriveSecretKey(ByteArray(32).also(SecureRandom()::nextBytes))
    val identity = YggdrasilIdentity(context)
    identity.set(accountSecret)
    val authorizer = DeviceEnrollmentAuthorizer(context) { identity.authorizerAccount() }
    authorizer.cancelAll()
    try {
      val error = runCatching { authorizer.acceptRequest("{}") }.exceptionOrNull()
      assertTrue(error is IllegalStateException)
    } finally {
      authorizer.cancelAll()
      identity.clear()
      accountSecret.fill(0)
    }
  }

  private fun deriveSecretKey(seed: ByteArray): ByteArray {
    val derived = JSONObject(Transportbridge.newBridge().deriveIdentity(seed))
    return DhtLocation.decodeHex(derived.getString("secretKey"), 64)
      ?: error("bridge did not return a valid secret key")
  }

  private fun deriveDeviceId(publicKeyHex: String): String {
    val publicKey = DhtLocation.decodeHex(publicKeyHex, 32) ?: error("invalid device public key")
    return DhtLocation.hex(app.upeer.transport.crypto.Blake2b.hash256(publicKey))
  }
}
