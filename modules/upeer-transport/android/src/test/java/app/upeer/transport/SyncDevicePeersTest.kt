package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDevicePeersTest {
  private val now = 1_700_000_000_000L
  private val account = "e".repeat(64)

  private fun certificate(deviceId: String, devicePublicKey: String) = JSONObject()
    .put("deviceId", deviceId)
    .put("devicePublicKey", devicePublicKey)
    .put("accountUpeerId", account)
    .put("epoch", 3)

  private fun hello(deviceId: String, spkId: Long = 1): JSONObject = JSONObject()
    .put("deviceId", deviceId)
    .put("deviceCertificate", certificate(deviceId, "a".repeat(64)))
    .put("deviceSignedPreKey", JSONObject()
      .put("spkPub", "b".repeat(64))
      .put("spkSig", "c".repeat(128))
      .put("spkId", spkId))
    .put("timestamp", now)

  private fun peers(): Triple<JSONObject, SyncDevicePeers, () -> Int> {
    val state = JSONObject()
    var persisted = 0
    val store = SyncDevicePeers({ state }, Any(), { persisted += 1 })
    return Triple(state, store, { persisted })
  }

  @Test
  fun `builds a binding from a well formed hello`() {
    val binding = SyncHello.binding(hello("d".repeat(64)))

    assertEquals("d".repeat(64), binding?.deviceId)
    assertEquals("a".repeat(64), binding?.devicePublicKey)
    assertEquals(1L, binding?.signedPreKey?.optLong("spkId"))
  }

  @Test
  fun `rejects a hello whose certificate device does not match`() {
    val payload = hello("d".repeat(64)).put("deviceId", "f".repeat(64))

    assertNull(SyncHello.binding(payload))
  }

  @Test
  fun `rejects a hello with an unsigned pre key`() {
    val payload = hello("d".repeat(64))
    payload.getJSONObject("deviceSignedPreKey").put("spkSig", "c".repeat(20))

    assertNull(SyncHello.binding(payload))
  }

  @Test
  fun `rejects a hello older than the tolerated skew`() {
    assertTrue(SyncHello.timestampIsFresh(now - 60_000, now))
    assertFalse(SyncHello.timestampIsFresh(now - 600_000, now))
    assertFalse(SyncHello.timestampIsFresh(0, now))
  }

  @Test
  fun `keeps the peer address across hellos`() {
    val (_, store, persisted) = peers()
    val binding = SyncHello.binding(hello("d".repeat(64)))!!

    store.remember(binding, "200::1", now)
    store.remember(binding, "", now + 1_000)

    val peer = store.list().single()
    assertEquals("200::1", peer.address)
    assertEquals(2, persisted())
  }

  @Test
  fun `keeps the newer signed pre key`() {
    val (_, store, _) = peers()
    store.remember(SyncHello.binding(hello("d".repeat(64), spkId = 5))!!, "200::1", now)
    store.remember(SyncHello.binding(hello("d".repeat(64), spkId = 2))!!, "200::1", now)

    assertEquals(5L, store.list().single().signedPreKey.optLong("spkId"))
  }

  @Test
  fun `excludes the sender and the origin from the relay targets`() {
    val (_, store, _) = peers()
    val sender = "1".repeat(64)
    val origin = "2".repeat(64)
    val third = "3".repeat(64)
    for (deviceId in listOf(sender, origin, third)) {
      store.remember(SyncHello.binding(hello(deviceId))!!, "200::1", now)
    }

    assertEquals(listOf(third), store.relayTargets(sender, origin).map { it.deviceId })
  }

  @Test
  fun `skips peers without a known address when relaying`() {
    val (_, store, _) = peers()
    val peer = "3".repeat(64)
    store.remember(SyncHello.binding(hello(peer))!!, "", now)

    assertTrue(store.relayTargets("1".repeat(64), null).isEmpty())
  }

  @Test
  fun `forgets a device`() {
    val (_, store, _) = peers()
    val peer = "3".repeat(64)
    store.remember(SyncHello.binding(hello(peer))!!, "200::1", now)

    assertTrue(store.forget(peer))
    assertFalse(store.forget(peer))
    assertTrue(store.list().isEmpty())
  }
}
