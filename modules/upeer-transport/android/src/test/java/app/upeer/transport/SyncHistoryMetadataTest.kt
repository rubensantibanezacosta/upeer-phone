package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncHistoryMetadataTest {
  private val deviceId = "a".repeat(64)

  private fun signature(record: SyncEventRecord): SyncOriginSignature =
    SyncOriginSignature(deviceId, "f".repeat(128), JSONObject().put("deviceId", deviceId))

  private fun groups(count: Int): JSONArray {
    val array = JSONArray()
    for (index in 0 until count) {
      array.put(JSONObject().put("groupId", "grp-$index").put("name", "grupo $index").put("status", "active"))
    }
    return array
  }

  @Test
  fun `cada grupo del almacen viaja como evento firmado`() {
    val entries = SyncHistoryMetadata.groupEntries(deviceId, groups(2), 1000L, ::signature, 199)

    assertEquals(2, entries.size)
    val first = entries[0]
    assertEquals("group-grp-0", first.optString("eventId"))
    assertEquals(SyncEventKinds.GROUP_UPSERT, first.optString("kind"))
    assertEquals("grp-0", first.optJSONObject("payload")?.optString("groupId"))
    assertEquals(deviceId, first.optString("originDeviceId"))
    assertEquals("f".repeat(128), first.optString("originSignature"))
  }

  @Test
  fun `no inventa eventos para grupos sin identificador`() {
    val array = JSONArray()
    array.put(JSONObject().put("name", "sin id"))
    array.put(JSONObject().put("groupId", "grp-ok"))

    val entries = SyncHistoryMetadata.groupEntries(deviceId, array, 1000L, ::signature, 199)

    assertEquals(1, entries.size)
    assertEquals("group-grp-ok", entries[0].optString("eventId"))
  }

  @Test
  fun `un grupo que no se puede firmar no viaja`() {
    val entries = SyncHistoryMetadata.groupEntries(deviceId, groups(3), 1000L, { null }, 199)

    assertTrue(entries.isEmpty())
  }

  @Test
  fun `respeta el hueco que le deja la pagina`() {
    val entries = SyncHistoryMetadata.groupEntries(deviceId, groups(10), 1000L, ::signature, 4)

    assertEquals(4, entries.size)
  }
}
