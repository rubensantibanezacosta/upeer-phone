package app.upeer.transport

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DeviceEnrollmentE2ETest {
  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
  private val directory get() = context.getExternalFilesDir(null) ?: context.filesDir

  @Test
  fun phaseOneBuildsTheRequestFromTheDesktopInvite() {
    val inviteFile = File(directory, INVITE_FILE)
    assumeTrue("$INVITE_FILE missing: push an invite code to run this phase", inviteFile.isFile)
    val invite = inviteFile.readText().trim()
    assertTrue("invite code is empty", invite.isNotEmpty())

    val enrollment = DeviceEnrollment(context)
    val result = JSONObject(enrollment.begin(invite))

    val request = result.getString("request")
    assertTrue("request is empty", request.isNotEmpty())
    assertEquals(request, enrollment.requestJSON())
    val pending = JSONObject(enrollment.statusJSON()).getJSONObject("pending")
    assertEquals(result.getJSONObject("offer").getString("accountUpeerId"), pending.getString("accountUpeerId"))
    File(directory, REQUEST_FILE).writeText(request)
    Log.i(TAG, "request-ready ${pending.getString("accountUpeerId")}")
  }

  @Test
  fun phaseTwoInstallsTheSealedCertificate() {
    val responseFile = File(directory, RESPONSE_FILE)
    assumeTrue("$RESPONSE_FILE missing: push a sealed certificate to run this phase", responseFile.isFile)
    val sealed = responseFile.readText().trim()
    assertTrue("response code is empty", sealed.isNotEmpty())

    val enrollment = DeviceEnrollment(context)
    val installed = JSONObject(enrollment.finish(sealed))

    val deviceId = installed.getString("deviceId")
    assertTrue("deviceId is not 32-byte hex", Regex("^[0-9a-f]{64}$").matches(deviceId))
    assertEquals(deviceId, JSONObject(enrollment.statusJSON()).getJSONObject("installed").getString("deviceId"))
    assertEquals("", enrollment.requestJSON())
    File(directory, INSTALLED_FILE).writeText(installed.toString(2))
    Log.i(TAG, "certificate-installed $deviceId")
  }

  private companion object {
    private const val TAG = "UPeerEnrollmentE2E"
    private const val INVITE_FILE = "enrollment-invite.txt"
    private const val REQUEST_FILE = "enrollment-request.txt"
    private const val RESPONSE_FILE = "enrollment-response.txt"
    private const val INSTALLED_FILE = "enrollment-installed.json"
  }
}
