package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeerKeyLearningTest {
  private val upeerId = "a".repeat(64)
  private val publicKey = "b".repeat(64)

  @Test
  fun `learns the key of a peer that had none`() {
    assertEquals(publicKey, PeerKeyLearning.keyToLearn(upeerId, publicKey, ""))
  }

  @Test
  fun `normalises the case and the surrounding spaces`() {
    assertEquals(publicKey, PeerKeyLearning.keyToLearn(upeerId, " ${"B".repeat(64)} ", ""))
  }

  @Test
  fun `never replaces a key that already exists`() {
    assertNull(PeerKeyLearning.keyToLearn(upeerId, publicKey, "c".repeat(64)))
    assertNull(PeerKeyLearning.keyToLearn(upeerId, publicKey, publicKey))
  }

  @Test
  fun `rejects malformed keys and identities`() {
    assertNull(PeerKeyLearning.keyToLearn(upeerId, "", ""))
    assertNull(PeerKeyLearning.keyToLearn(upeerId, "b".repeat(63), ""))
    assertNull(PeerKeyLearning.keyToLearn(upeerId, "z".repeat(64), ""))
    assertNull(PeerKeyLearning.keyToLearn("a".repeat(63), publicKey, ""))
  }
}
