package app.upeer.transport.dht.kademlia

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class KademliaValueStoreTest {
  private var now = 1_000L
  private val clock = { now }

  @Test
  fun enforcesEntryCapAndEvictsOldest() {
    val store = KademliaValueStore(clock, maxEntries = 2)
    store.set("aa", "one", "peer")
    store.set("bb", "two", "peer")
    store.set("cc", "three", "peer")
    assertEquals(2, store.size())
    assertNull(store.get("aa"))
    assertEquals("three", store.get("cc")?.value)
    assertEquals(10_000, KademliaConfig.MAX_STORE_ENTRIES)
  }

  @Test
  fun expiresValuesAfterThirtyDays() {
    val store = KademliaValueStore(clock)
    store.set("aa", "value", "peer")
    now += KademliaConfig.VALUE_TTL_MILLIS
    assertEquals(1, store.size())
    now += 1
    assertEquals(1, store.cleanupExpiredValues())
    assertFalse(store.has("aa"))
  }

  @Test
  fun deduplicatesDevicesAndSortsByDhtSequence() {
    val store = KademliaValueStore(clock)
    store.set("aa", block("device-a", 5, "200::1"), "peer")
    store.set("aa", block("device-b", 9, "200::2"), "peer")
    store.set("aa", block("device-a", 12, "200::3"), "peer")
    val devices = store.get("aa")?.value as JSONArray
    assertEquals(2, devices.length())
    assertEquals("device-a", devices.getJSONObject(0).getString("deviceId"))
    assertEquals(12, devices.getJSONObject(0).getInt("dhtSeq"))
    assertEquals("200::3", devices.getJSONObject(0).getString("address"))
    assertEquals("device-b", devices.getJSONObject(1).getString("deviceId"))
  }

  @Test
  fun capsDevicesPerKeyAtTen() {
    val store = KademliaValueStore(clock)
    repeat(12) { index ->
      store.set("aa", block("device-$index", index.toLong(), "200::$index"), "peer")
    }
    val devices = store.get("aa")?.value as JSONArray
    assertEquals(10, devices.length())
    assertEquals("device-11", devices.getJSONObject(0).getString("deviceId"))
    assertEquals("device-2", devices.getJSONObject(9).getString("deviceId"))
  }

  @Test
  fun replacesPlainValuesAndReturnsStoredMetadata() {
    val store = KademliaValueStore(clock)
    store.set("AA", "first", "peer-one")
    now += 10
    store.set("aa", "second", "peer-two")
    val stored = store.get("aa")
    assertEquals("second", stored?.value)
    assertEquals("peer-two", stored?.publisher)
    assertEquals(now, stored?.timestamp)
    assertEquals(1, store.size())
  }

  private fun block(deviceId: String, dhtSeq: Long, address: String): JSONObject =
    JSONObject()
      .put("deviceId", deviceId)
      .put("dhtSeq", dhtSeq)
      .put("address", address)
}
