package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignedPreKeyFieldsTest {
  private val currentKey = "aa".repeat(32)
  private val nextKey = "bb".repeat(32)

  private fun spk(id: Long, publicKey: String = currentKey): JSONObject = JSONObject()
    .put("spkPub", publicKey)
    .put("spkSig", "cc".repeat(64))
    .put("spkId", id)

  @Test
  fun validatesPublicSignedPreKeyFields() {
    assertTrue(SignedPreKeyFields.valid(spk(9)))
    assertFalse(SignedPreKeyFields.valid(spk(0)))
    assertFalse(SignedPreKeyFields.valid(JSONObject().put("spkPub", "00")))
  }

  @Test
  fun classifiesRotationAgainstCurrentDeviceSignedPreKey() {
    val current = spk(9)
    assertTrue(SignedPreKeyFields.isNewer(current, spk(10, nextKey)))
    assertFalse(SignedPreKeyFields.isNewer(current, spk(9)))
    assertFalse(SignedPreKeyFields.isNewer(current, spk(8, nextKey)))
    assertEquals(SignedPreKeyUpdate.INVALID, SignedPreKeyFields.classify(current, spk(9, nextKey)))
    assertEquals(SignedPreKeyUpdate.UPDATED, SignedPreKeyFields.classify(null, spk(10, nextKey)))
  }
}
