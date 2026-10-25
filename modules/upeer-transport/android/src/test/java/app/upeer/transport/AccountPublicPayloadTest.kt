package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AccountPublicPayloadTest {
  private val accountId = "aa".repeat(32)
  private val accountKey = "bb".repeat(32)

  @Test
  fun `encode rejects short account id`() {
    assertNull(AccountPublicPayload.encode("aa", accountKey))
  }

  @Test
  fun `encode rejects non hex account key`() {
    assertNull(AccountPublicPayload.encode(accountId, "zz".repeat(32)))
  }

  @Test
  fun `encode rejects empty values`() {
    assertNull(AccountPublicPayload.encode("", ""))
  }

  @Test
  fun `round trip preserves identity`() {
    val raw = AccountPublicPayload.encode(accountId, accountKey)
    assertNotNull(raw)
    val decoded = AccountPublicPayload.decode(raw!!)
    assertEquals(AccountPublicIdentity(accountId, accountKey), decoded)
  }

  @Test
  fun `encode normalizes uppercase to lowercase`() {
    val raw = AccountPublicPayload.encode(accountId.uppercase(), accountKey.uppercase())
    val decoded = AccountPublicPayload.decode(raw!!)
    assertEquals(AccountPublicIdentity(accountId, accountKey), decoded)
  }

  @Test
  fun `decode accepts uppercase stored values`() {
    val raw = """{"mode":"device","accountUpeerId":"${accountId.uppercase()}","accountPublicKey":"${accountKey.uppercase()}"}"""
    assertEquals(AccountPublicIdentity(accountId, accountKey), AccountPublicPayload.decode(raw))
  }

  @Test
  fun `decode rejects root mode payload`() {
    val raw = """{"mode":"root","accountUpeerId":"$accountId","accountPublicKey":"$accountKey"}"""
    assertNull(AccountPublicPayload.decode(raw))
  }

  @Test
  fun `decode rejects missing mode`() {
    val raw = """{"accountUpeerId":"$accountId","accountPublicKey":"$accountKey"}"""
    assertNull(AccountPublicPayload.decode(raw))
  }

  @Test
  fun `decode rejects malformed json`() {
    assertNull(AccountPublicPayload.decode("not json"))
  }

  @Test
  fun `decode rejects invalid hex`() {
    val raw = """{"mode":"device","accountUpeerId":"$accountId","accountPublicKey":"nope"}"""
    assertNull(AccountPublicPayload.decode(raw))
  }

  @Test
  fun `decode rejects missing fields`() {
    assertNull(AccountPublicPayload.decode("""{"mode":"device"}"""))
  }

  @Test
  fun `encode output declares device mode`() {
    val raw = AccountPublicPayload.encode(accountId, accountKey)
    org.junit.Assert.assertTrue(raw!!.contains("\"mode\":\"device\""))
  }
}
