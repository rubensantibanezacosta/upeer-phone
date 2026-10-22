package app.upeer.transport.dht.kademlia

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class KademliaValueMergeTest {
  @Test
  fun keepsTheHighestSequenceForEachDeviceAndDeduplicates() {
    val first = block("device-a", 1)
    val second = block("device-a", 2)
    val third = block("device-b", 5)

    val merged = KademliaValueMerge.merge(
      listOf(first, JSONArray().put(second).put(third)),
    ) as JSONArray

    assertEquals(2, merged.length())
    assertEquals(2L, deviceBlock(merged, "device-a").getLong("dhtSeq"))
    assertEquals(5L, deviceBlock(merged, "device-b").getLong("dhtSeq"))
  }

  @Test
  fun selectsTheRegistryWithTheHighestEpoch() {
    val older = JSONObject().put("epoch", 3L).put("issuedAt", 200L)
    val newer = JSONObject().put("epoch", 5L).put("issuedAt", 100L)

    val merged = KademliaValueMerge.merge(listOf(older, newer)) as JSONObject

    assertEquals(5L, merged.getLong("epoch"))
    assertEquals(100L, merged.getLong("issuedAt"))
  }

  @Test
  fun keepsPrimitiveValuesUnchanged() {
    assertEquals("value", KademliaValueMerge.merge(listOf("value", "other")))
  }

  private fun block(deviceId: String, dhtSeq: Long): JSONObject =
    JSONObject().put("deviceId", deviceId).put("dhtSeq", dhtSeq).put("address", "200::1")

  private fun deviceBlock(blocks: JSONArray, deviceId: String): JSONObject =
    (0 until blocks.length())
      .map { index -> blocks.getJSONObject(index) }
      .first { block -> block.getString("deviceId") == deviceId }
}
