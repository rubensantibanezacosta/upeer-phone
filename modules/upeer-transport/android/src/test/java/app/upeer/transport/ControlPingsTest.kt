package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Test

class ControlPingsTest {
  @Test
  fun `pong carries the expected type`() {
    assertEquals("PONG", ControlPings.pong().optString("type"))
  }

  @Test
  fun `ping announces the activity time when sharing is enabled`() {
    val packet = ControlPings.active(1_700_000_000_000L, shareActivity = true)
    assertEquals("PING", packet.optString("type"))
    assertEquals(1_700_000_000_000L, packet.optLong("activeAt"))
  }

  @Test
  fun `ping omits the activity time when sharing is disabled`() {
    val packet = ControlPings.active(1_700_000_000_000L, shareActivity = false)
    assertEquals("PING", packet.optString("type"))
    assertEquals(false, packet.has("activeAt"))
  }
}
