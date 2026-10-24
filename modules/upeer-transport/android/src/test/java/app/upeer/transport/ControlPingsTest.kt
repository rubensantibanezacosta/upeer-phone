package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Test

class ControlPingsTest {
  @Test
  fun `pong carries the expected type`() {
    assertEquals("PONG", ControlPings.pong().optString("type"))
  }
}
