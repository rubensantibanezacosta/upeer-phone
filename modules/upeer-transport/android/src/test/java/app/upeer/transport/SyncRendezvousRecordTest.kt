package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRendezvousRecordTest {
  private val account = "aa".repeat(32)
  private val device = "bb".repeat(32)
  private val other = "cc".repeat(32)
  private val address = "200:1234::5"
  private val now = 1_700_000_000_000L

  private fun certificate(deviceId: String = device, accountId: String = account) = JSONObject()
    .put("deviceId", deviceId)
    .put("accountUpeerId", accountId)
    .put("devicePublicKey", "dd".repeat(32))
    .put("issuedAt", now - 1_000L)

  private fun record(publishedAt: Long = now, addr: String = address) =
    SyncRendezvousRecord.build(account, device, addr, certificate(), publishedAt)

  @Test
  fun `build produces a record that parse accepts`() {
    val built = record()!!
    val parsed = SyncRendezvousRecord.parse(built.toString(), account, now)!!

    assertEquals(account, parsed.accountUpeerId)
    assertEquals(device, parsed.deviceId)
    assertEquals(address, parsed.address)
  }

  @Test
  fun `build refuses a certificate for another device`() {
    assertNull(SyncRendezvousRecord.build(account, device, address, certificate(deviceId = other), now))
  }

  @Test
  fun `build refuses a certificate from another account`() {
    assertNull(SyncRendezvousRecord.build(account, device, address, certificate(accountId = other), now))
  }

  @Test
  fun `build refuses blank or oversized addresses`() {
    assertNull(SyncRendezvousRecord.build(account, device, "", certificate(), now))
    assertNull(SyncRendezvousRecord.build(account, device, "a".repeat(65), certificate(), now))
  }

  @Test
  fun `build refuses invalid identities and timestamps`() {
    assertNull(SyncRendezvousRecord.build("zz", device, address, certificate(), now))
    assertNull(SyncRendezvousRecord.build(account, "zz", address, certificate(), now))
    assertNull(SyncRendezvousRecord.build(account, device, address, certificate(), 0L))
  }

  @Test
  fun `parse refuses a record of another account`() {
    val built = record()!!

    assertNull(SyncRendezvousRecord.parse(built.toString(), other, now))
  }

  @Test
  fun `parse expires old records`() {
    val built = record(publishedAt = now - SyncRendezvousRecord.TTL_MILLIS - 1)!!

    assertNull(SyncRendezvousRecord.parse(built.toString(), account, now))
  }

  @Test
  fun `parse rejects timestamps far in the future`() {
    val built = record(publishedAt = now + 11 * 60 * 1_000L)!!

    assertNull(SyncRendezvousRecord.parse(built.toString(), account, now))
  }

  @Test
  fun `parse accepts a string or an object and rejects anything else`() {
    val built = record()!!

    assertTrue(SyncRendezvousRecord.parse(built, account, now) != null)
    assertNull(SyncRendezvousRecord.parse(42, account, now))
    assertNull(SyncRendezvousRecord.parse(null, account, now))
    assertNull(SyncRendezvousRecord.parse("no-json", account, now))
  }

  @Test
  fun `parse refuses a certificate whose device does not match`() {
    val tampered = record()!!.put("certificate", certificate(deviceId = other))

    assertNull(SyncRendezvousRecord.parse(tampered.toString(), account, now))
  }

  @Test
  fun `keys are deterministic and separate per device`() {
    val first = SyncRendezvousKey.key(account, device)

    assertEquals(first, SyncRendezvousKey.key(account.uppercase(), device.uppercase()))
    assertNotEquals(first, SyncRendezvousKey.key(account, other))
    assertNotEquals(first, SyncRendezvousKey.key(other, device))
    assertEquals(40, first.length)
  }

  @Test
  fun `keys refuse invalid identities`() {
    val failed = runCatching { SyncRendezvousKey.key("zz", device) }.isFailure
    val alsoFailed = runCatching { SyncRendezvousKey.key(account, "zz") }.isFailure

    assertTrue(failed)
    assertTrue(alsoFailed)
  }
}
