package app.upeer.transport.vault

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultQueryDeliveryTest {
  private val sender = "aa".repeat(32)
  private val other = "ee".repeat(32)
  private val recipient = "bb".repeat(32)
  private val device = "cc".repeat(32)
  private val now = 1_700_000_000_000L

  private fun entry(
    id: Long,
    senderSid: String = sender,
    payloadHash: String = "dd".repeat(32),
    recipientSid: String = recipient,
    recipientDeviceId: String? = null,
    expiresAt: Long = now + 60_000L,
  ) = VaultEntry(
    id = id,
    payloadHash = payloadHash,
    recipientSid = recipientSid,
    recipientDeviceId = recipientDeviceId,
    senderSid = senderSid,
    priority = VaultStoreEnvelope.PRIORITY_CHAT,
    data = "0a0b",
    expiresAt = expiresAt,
  )

  private fun deliver(
    entries: List<VaultEntry>,
    authenticatedSender: String = recipient,
    requesterSid: String = recipient,
    requesterDeviceId: String? = null,
    payloadHash: String? = null,
    afterId: Long = 0L,
  ) = VaultQueryDelivery.deliver(entries, authenticatedSender, requesterSid, requesterDeviceId, payloadHash, afterId, now)

  @Test
  fun `query declares the requester and omits the optional fields`() {
    val query = VaultQueryDelivery.buildQuery(recipient.uppercase())

    assertEquals("VAULT_QUERY", query.getString("type"))
    assertEquals(recipient, query.getString("requesterSid"))
    assertTrue(!query.has("requesterDeviceId"))
    assertTrue(!query.has("payloadHash"))
  }

  @Test
  fun `query carries the device and the requested hash when present`() {
    val query = VaultQueryDelivery.buildQuery(recipient, device, "dd".repeat(32))

    assertEquals(device, query.getString("requesterDeviceId"))
    assertEquals("dd".repeat(32), query.getString("payloadHash"))
  }

  @Test
  fun `delivery only contains entries of the requester`() {
    val body = deliver(listOf(entry(1, senderSid = sender), entry(2, senderSid = other)))!!

    assertEquals(2, body.getJSONArray("entries").length())
  }

  @Test
  fun `delivery hides entries of another recipient`() {
    val foreign = entry(1, recipientSid = other)

    assertNull(deliver(listOf(foreign)))
  }

  @Test
  fun `delivery accepts the wildcard recipient`() {
    val wildcard = entry(1, recipientSid = VaultStoreRequest.WILDCARD)

    assertEquals(1, deliver(listOf(wildcard))!!.getJSONArray("entries").length())
  }

  @Test
  fun `delivery skips expired entries`() {
    assertNull(deliver(listOf(entry(1, expiresAt = now))))
  }

  @Test
  fun `delivery respects the recipient device`() {
    val forDevice = entry(1, recipientDeviceId = device)
    val forAny = entry(2)

    assertEquals(2, deliver(listOf(forDevice, forAny), requesterDeviceId = device)!!.getJSONArray("entries").length())
    assertNull(deliver(listOf(forDevice), requesterDeviceId = null))
  }

  @Test
  fun `delivery refuses a requester that is not the authenticated sender`() {
    assertNull(deliver(listOf(entry(1)), authenticatedSender = sender))
  }

  @Test
  fun `delivery pages at the maximum size and reports more`() {
    val entries = (1..VaultQueryDelivery.PAGE_SIZE + 5).map { entry(it.toLong()) }

    val page = deliver(entries)!!

    assertEquals(VaultQueryDelivery.PAGE_SIZE, page.getJSONArray("entries").length())
    assertTrue(page.getBoolean("hasMore"))
    assertEquals(1L, page.getJSONArray("entries").getJSONObject(0).getLong("id"))
  }

  @Test
  fun `delivery continues after the last seen id`() {
    val entries = (1..VaultQueryDelivery.PAGE_SIZE + 5).map { entry(it.toLong()) }

    val page = deliver(entries, afterId = VaultQueryDelivery.PAGE_SIZE.toLong())!!

    assertEquals(5, page.getJSONArray("entries").length())
    assertTrue(!page.getBoolean("hasMore"))
  }

  @Test
  fun `delivery of a single hash returns just that entry`() {
    val wanted = entry(1, payloadHash = "11".repeat(32))
    val otherEntry = entry(2, payloadHash = "22".repeat(32))

    val page = deliver(listOf(wanted, otherEntry), payloadHash = "11".repeat(32))!!

    assertEquals(1, page.getJSONArray("entries").length())
    assertEquals("11".repeat(32), page.getJSONArray("entries").getJSONObject(0).getString("payloadHash"))
    assertTrue(!page.getBoolean("hasMore"))
  }

  @Test
  fun `delivery of an unknown hash returns nothing`() {
    assertNull(deliver(listOf(entry(1)), payloadHash = "33".repeat(32)))
  }

  @Test
  fun `parse delivery rejects another type`() {
    assertNull(VaultQueryDelivery.parseDelivery(JSONObject().put("type", "VAULT_ACK")))
  }

  @Test
  fun `parse delivery rejects an entry with an invalid sender`() {
    val body = JSONObject()
      .put("type", "VAULT_DELIVERY")
      .put(
        "entries",
        org.json.JSONArray().put(
          JSONObject().put("senderSid", "aa").put("payloadHash", "dd").put("data", "0a"),
        ),
      )

    assertNull(VaultQueryDelivery.parseDelivery(body))
  }

  @Test
  fun `parse delivery returns the entries it carries`() {
    val body = deliver(listOf(entry(7, payloadHash = "dd".repeat(32))))!!

    val parsed = VaultQueryDelivery.parseDelivery(body)!!

    assertEquals(1, parsed.size)
    assertEquals(7L, parsed[0].id)
    assertEquals(sender, parsed[0].senderSid)
    assertEquals("dd".repeat(32), parsed[0].payloadHash)
  }
}
