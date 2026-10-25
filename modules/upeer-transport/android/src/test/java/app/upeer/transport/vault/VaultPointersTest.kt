package app.upeer.transport.vault

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultPointersTest {
  private val first = "aa".repeat(32)
  private val second = "bb".repeat(32)
  private val third = "cc".repeat(32)
  private val device = "dd".repeat(32)
  private val now = 1_700_000_000_000L

  private fun endpoint(upeerId: String, address: String) = VaultPointerEndpoint(upeerId, address)

  @Test
  fun `build keeps the custodians in order and deduplicates them`() {
    val pointer = VaultPointers.build(listOf(first, second, first.uppercase()), emptyList(), null, now)!!

    assertEquals(listOf(first, second), pointer.custodians)
  }

  @Test
  fun `build refuses an empty custodian list`() {
    assertNull(VaultPointers.build(emptyList(), emptyList(), null, now))
  }

  @Test
  fun `build drops invalid custodians`() {
    assertNull(VaultPointers.build(listOf("aa", "", "zz"), emptyList(), null, now))
  }

  @Test
  fun `build caps the custodians`() {
    val many = (1..VaultPointers.MAX_CUSTODIANS + 5).map { "%064x".format(it) }

    assertEquals(VaultPointers.MAX_CUSTODIANS, VaultPointers.build(many, emptyList(), null, now)!!.custodians.size)
  }

  @Test
  fun `build deduplicates endpoints by address`() {
    val pointer = VaultPointers.build(
      listOf(first),
      listOf(endpoint(first, "200::1"), endpoint(second, "200::1")),
      null,
      now,
    )!!

    assertEquals(1, pointer.endpoints.size)
    assertEquals(first, pointer.endpoints[0].upeerId)
  }

  @Test
  fun `build drops endpoints without address`() {
    val pointer = VaultPointers.build(listOf(first), listOf(endpoint(first, "  ")), null, now)!!

    assertTrue(pointer.endpoints.isEmpty())
  }

  @Test
  fun `build caps the endpoints`() {
    val many = (1..VaultPointers.MAX_ENDPOINTS + 5).map { endpoint(first, "200::$it") }

    assertEquals(VaultPointers.MAX_ENDPOINTS, VaultPointers.build(listOf(first), many, null, now)!!.endpoints.size)
  }

  @Test
  fun `build refuses an invalid device`() {
    assertNull(VaultPointers.build(listOf(first), emptyList(), "dd", now))
  }

  @Test
  fun `json round trip keeps the pointer`() {
    val pointer = VaultPointers.build(listOf(first, second), listOf(endpoint(first, "200::1")), device, now)!!

    val parsed = VaultPointers.parse(VaultPointers.toJSON(pointer), now)!!

    assertEquals(pointer.custodians, parsed.custodians)
    assertEquals(pointer.endpoints, parsed.endpoints)
    assertEquals(device, parsed.recipientDeviceId)
    assertEquals(now, parsed.updatedAt)
  }

  @Test
  fun `json omits the device when there is none`() {
    val pointer = VaultPointers.build(listOf(first), emptyList(), null, now)!!

    assertTrue(!JSONObject(VaultPointers.toJSON(pointer)).has("recipientDeviceId"))
  }

  @Test
  fun `parse accepts the desktop shape`() {
    val body = JSONObject()
      .put("custodians", org.json.JSONArray().put(first))
      .put("custodianEndpoints", org.json.JSONArray().put(JSONObject().put("upeerId", first).put("address", "200::1")))
      .put("updatedAt", now - 1_000L)

    val parsed = VaultPointers.parse(body, now)!!

    assertEquals(listOf(first), parsed.custodians)
    assertEquals(1, parsed.endpoints.size)
    assertEquals(now - 1_000L, parsed.updatedAt)
  }

  @Test
  fun `parse accepts a raw string document`() {
    val pointer = VaultPointers.build(listOf(first), emptyList(), null, now)!!

    assertEquals(listOf(first), VaultPointers.parse(VaultPointers.toJSON(pointer), now)!!.custodians)
  }

  @Test
  fun `parse refuses an unknown value`() {
    assertNull(VaultPointers.parse(null, now))
    assertNull(VaultPointers.parse(42, now))
    assertNull(VaultPointers.parse("{ no es json", now))
  }

  @Test
  fun `parse refuses a pointer without valid custodians`() {
    assertNull(VaultPointers.parse(JSONObject().put("custodians", org.json.JSONArray().put("zz")), now))
  }

  @Test
  fun `parse drops invalid endpoints but keeps the pointer`() {
    val body = JSONObject()
      .put("custodians", org.json.JSONArray().put(first))
      .put("custodianEndpoints", org.json.JSONArray().put(JSONObject().put("upeerId", "zz").put("address", "200::1")))

    assertTrue(VaultPointers.parse(body, now)!!.endpoints.isEmpty())
  }

  @Test
  fun `parse refuses an invalid device`() {
    val body = JSONObject()
      .put("custodians", org.json.JSONArray().put(first))
      .put("recipientDeviceId", "dd")

    assertNull(VaultPointers.parse(body, now))
  }

  @Test
  fun `parse tolerates a future updatedAt`() {
    val body = JSONObject()
      .put("custodians", org.json.JSONArray().put(first))
      .put("updatedAt", now + 60_000L)

    assertEquals(now, VaultPointers.parse(body, now)!!.updatedAt)
  }

  @Test
  fun `parse tolerates a missing updatedAt`() {
    val body = JSONObject().put("custodians", org.json.JSONArray().put(first))

    assertEquals(now, VaultPointers.parse(body, now)!!.updatedAt)
  }

  @Test
  fun `plan publishes under the recipient key so the recipient can find its mailbox`() {
    val publication = VaultPointers.plan(first, second, listOf(VaultCustodianCandidate(first, "200::1", 0L)), now)!!

    assertEquals(VaultPointerKey.key(second, null, now), publication.key)
    assertTrue(publication.key != VaultPointerKey.key(first, null, now))
  }

  @Test
  fun `plan always includes the sender as a custodian`() {
    val publication = VaultPointers.plan(first, second, listOf(VaultCustodianCandidate(third, "200::1", 0L)), now)!!

    val pointer = VaultPointers.parse(publication.value, now)!!
    assertEquals(listOf(third, first), pointer.custodians)
  }

  @Test
  fun `plan publishes without a device scope`() {
    val publication = VaultPointers.plan(first, second, listOf(VaultCustodianCandidate(first, "200::1", 0L)), now)!!

    assertNull(VaultPointers.parse(publication.value, now)!!.recipientDeviceId)
  }

  @Test
  fun `plan refuses an empty custodian list`() {
    assertNull(VaultPointers.plan(first, second, emptyList(), now))
  }

  @Test
  fun `plan refuses to publish to itself`() {
    assertNull(VaultPointers.plan(first, first, listOf(VaultCustodianCandidate(third, "200::1", 0L)), now))
    assertNull(VaultPointers.plan(first, first.uppercase(), listOf(VaultCustodianCandidate(third, "200::1", 0L)), now))
  }

  @Test
  fun `plan refuses invalid identities`() {
    val custodians = listOf(VaultCustodianCandidate(third, "200::1", 0L))

    assertNull(VaultPointers.plan("aa", second, custodians, now))
    assertNull(VaultPointers.plan(first, "bb", custodians, now))
  }

  @Test
  fun `parse deduplicates custodians`() {
    val body = JSONObject().put("custodians", org.json.JSONArray().put(first).put(first).put(third))

    assertEquals(listOf(first, third), VaultPointers.parse(body, now)!!.custodians)
  }
}
