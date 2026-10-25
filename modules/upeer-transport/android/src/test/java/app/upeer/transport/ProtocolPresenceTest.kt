package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolPresenceTest {
  private val peerId = "a".repeat(64)
  private val now = 1_700_000_000_000L

  private fun store(peer: JSONObject): Pair<JSONObject, ProtocolPresence> {
    val state = JSONObject().put("peers", JSONObject().put(peerId, peer))
    var persisted = 0
    val presence = ProtocolPresence({ state }, Any(), { persisted += 1 })
    return state to presence
  }

  @Test
  fun `records the user activity announced by the peer`() {
    val (state, presence) = store(JSONObject().put("publicKey", "b".repeat(64)))

    assertTrue(presence.touch(peerId, now, now))

    assertEquals(now, state.getJSONObject("peers").getJSONObject(peerId).getLong("lastActive"))
  }

  @Test
  fun `never moves the activity backwards`() {
    val (state, presence) = store(JSONObject().put("lastActive", now))

    assertFalse(presence.touch(peerId, now - 5_000, now))

    assertEquals(now, state.getJSONObject("peers").getJSONObject(peerId).getLong("lastActive"))
  }

  @Test
  fun `clamps a future timestamp from a skewed clock`() {
    val (state, presence) = store(JSONObject())

    assertTrue(presence.touch(peerId, now + 60_000, now))

    assertEquals(now, state.getJSONObject("peers").getJSONObject(peerId).getLong("lastActive"))
  }

  @Test
  fun `ignores an unknown peer without activity data`() {
    val (_, presence) = store(JSONObject().put("publicKey", "b".repeat(64)))

    assertFalse(presence.touch("c".repeat(64), now, now))
    assertFalse(presence.touch(peerId, 0L, now))
  }

  @Test
  fun `only targets reachable peers seen within the window`() {
    val peers = JSONObject()
      .put("a".repeat(64), JSONObject().put("address", "200::1").put("publicKey", "b".repeat(64)).put("lastSeen", now - 1_000))
      .put("c".repeat(64), JSONObject().put("address", "200::2").put("publicKey", "d".repeat(64)).put("lastSeen", now - 900_000))
      .put("e".repeat(64), JSONObject().put("address", "").put("publicKey", "f".repeat(64)).put("lastSeen", now))
    val state = JSONObject().put("peers", peers)
    val presence = ProtocolPresence({ state }, Any(), {})

    assertEquals(listOf("a".repeat(64)), presence.targets(now - 300_000, 20))
  }

  @Test
  fun `caps the number of targets`() {
    val peers = JSONObject()
    repeat(5) { index ->
      peers.put(index.toString().repeat(64), JSONObject().put("address", "200::1").put("publicKey", "b".repeat(64)).put("lastSeen", now))
    }
    val state = JSONObject().put("peers", peers)
    val presence = ProtocolPresence({ state }, Any(), {})

    assertEquals(2, presence.targets(now - 300_000, 2).size)
  }
}
