package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * El chat directo indexa la sesión del Double Ratchet por dispositivo, no por cuenta.
 *
 * Con una sola sesión por cuenta, dos dispositivos del mismo contacto se pisaban el
 * ratchet y uno dejaba de descifrar; el "escribiendo" seguía llegando porque no pasa
 * por el ratchet.
 */
class DirectRatchetContextTest {
  private val deviceA = "aa".repeat(32)
  private val deviceB = "bb".repeat(32)

  @Test
  fun `aísla la sesión por dispositivo de la misma cuenta`() {
    assertNotEquals(
      DirectRatchetContext.forDevice(deviceA),
      DirectRatchetContext.forDevice(deviceB),
    )
    assertEquals("direct:$deviceA", DirectRatchetContext.forDevice(deviceA))
    assertEquals("direct:$deviceB", DirectRatchetContext.forDevice(deviceB))
  }

  @Test
  fun `mantiene el contexto histórico sin dispositivo conocido`() {
    assertEquals("direct", DirectRatchetContext.forDevice(null))
    assertEquals("direct", DirectRatchetContext.forDevice(""))
    assertEquals("direct", DirectRatchetContext.forDevice("   "))
  }

  @Test
  fun `normaliza a minúsculas y sin espacios`() {
    assertEquals("direct:$deviceA", DirectRatchetContext.forDevice("  ${deviceA.uppercase()}  "))
  }

  @Test
  fun `al descifrar prueba el dispositivo emisor y luego el histórico`() {
    assertEquals(listOf("direct:$deviceA", "direct"), DirectRatchetContext.candidates(deviceA))
    assertEquals(listOf("direct"), DirectRatchetContext.candidates(null))
  }
}
