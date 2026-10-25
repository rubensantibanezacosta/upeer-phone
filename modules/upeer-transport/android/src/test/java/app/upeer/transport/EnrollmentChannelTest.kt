package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentChannelTest {
  @Test
  fun requestFrameCarriesInviteAddressAndSealedEnvelope() {
    val sealed = JSONObject().put("inviteId", "ab12").put("devicePublicKey", "cd34")
    val frame = JSONObject(EnrollmentChannel.requestFrame(sealed, "ab12", "200:1::5"))
    assertEquals(EnrollmentChannel.REQUEST, frame.optString("type"))
    assertEquals("ab12", frame.optString("inviteId"))
    assertEquals("200:1::5", frame.optString("senderAddress"))
    assertEquals("ab12", frame.optJSONObject("sealed")?.optString("inviteId"))
  }

  @Test
  fun requestSealedReturnsNestedEnvelope() {
    val sealed = JSONObject().put("inviteId", "ab12")
    val packet = JSONObject().put("type", EnrollmentChannel.REQUEST).put("sealed", sealed)
    assertEquals("ab12", JSONObject(EnrollmentChannel.requestSealed(packet)!!).optString("inviteId"))
    assertNull(EnrollmentChannel.requestSealed(JSONObject().put("type", EnrollmentChannel.REQUEST)))
  }

  @Test
  fun responseFrameMarksSuccessWithSealedCertificate() {
    val frame = JSONObject(EnrollmentChannel.responseFrame("ab12", "{\"certificate\":{}}"))
    assertEquals(EnrollmentChannel.RESPONSE, frame.optString("type"))
    assertEquals("ab12", frame.optString("inviteId"))
    assertTrue(frame.optBoolean("ok"))
    assertNotNull(frame.optJSONObject("sealed"))
    assertFalse(frame.has("error"))
  }

  @Test
  fun responseFrameMarksFailureWithoutSealedCertificate() {
    val frame = JSONObject(EnrollmentChannel.responseFrame("ab12", null))
    assertEquals(EnrollmentChannel.RESPONSE, frame.optString("type"))
    assertFalse(frame.optBoolean("ok"))
    assertEquals("invite", frame.optString("error"))
    assertFalse(frame.has("sealed"))
  }

  @Test
  fun sealedFromRequiresSuccessfulFrame() {
    val sealed = JSONObject().put("certificate", JSONObject())
    assertNotNull(EnrollmentChannel.sealedFrom(JSONObject().put("ok", true).put("sealed", sealed)))
    assertNull(EnrollmentChannel.sealedFrom(JSONObject().put("ok", false).put("sealed", sealed)))
    assertNull(EnrollmentChannel.sealedFrom(JSONObject().put("ok", true)))
  }

  @Test
  fun dialableAddressAcceptsYggdrasilAndRejectsJunk() {
    assertEquals("200:1::5", EnrollmentChannel.dialableAddress(" 200:1::5 "))
    assertEquals("200:ab::7", EnrollmentChannel.dialableAddress("200:AB::7"))
    assertNull(EnrollmentChannel.dialableAddress(""))
    assertNull(EnrollmentChannel.dialableAddress("2001"))
    assertNull(EnrollmentChannel.dialableAddress("[200:1::5]:50005"))
    assertNull(EnrollmentChannel.dialableAddress("http://200:1::5"))
  }
}
