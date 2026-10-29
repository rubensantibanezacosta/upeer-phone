package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxFallbackTest {
  private fun entry(
    kind: String = OutboxFallback.KIND_CHAT,
    id: String = "050dd8da-9384-42d7-ba0b-e0398ccdf483",
    upeerId: String = "da4163f3528710db1adeca2211274d217728845044c70b912759e856493d1834",
    packet: String? = null,
    sends: Int? = null,
  ) = JSONObject().apply {
    put("kind", kind)
    put("id", id)
    put("upeerId", upeerId)
    if (packet != null) put("packet", packet)
    if (sends != null) {
      put("sends", sends)
      put("sentAt", 1_793_278_500_000L)
    }
  }

  @Test
  fun `un mensaje al contacto merece custodia aunque nunca se pudiera sellar`() {
    assertTrue(OutboxFallback.deservesVault(entry()))
  }

  @Test
  fun `una actualizacion de chat no se custodia`() {
    assertFalse(OutboxFallback.deservesVault(entry(kind = OutboxFallback.KIND_UPDATE)))
  }

  @Test
  fun `una entrada sin destinatario no se puede custodiar`() {
    assertFalse(OutboxFallback.deservesVault(entry(upeerId = "")))
  }

  @Test
  fun `una entrada sin identificador no se puede custodiar`() {
    assertFalse(OutboxFallback.deservesVault(entry(id = "")))
  }

  @Test
  fun `sin envio previo nunca se da por agotados los reenvios`() {
    assertFalse(OutboxFallback.outOfResends(entry(packet = "sobre")))
    assertFalse(OutboxFallback.outOfResends(entry(packet = "sobre", sends = 0)))
  }

  @Test
  fun `tras ocho reenvios se da por agotado el envio directo`() {
    assertFalse(OutboxFallback.outOfResends(entry(packet = "sobre", sends = OutboxRetryPolicy.MAX_SENDS - 1)))
    assertTrue(OutboxFallback.outOfResends(entry(packet = "sobre", sends = OutboxRetryPolicy.MAX_SENDS)))
  }
}
