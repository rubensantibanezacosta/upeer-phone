package app.upeer.transport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSessionPolicyTest {
  private val menor = "0d3180fc5bfa0000"
  private val mayor = "23c5177a3a9f0000"

  @Test
  fun `el dispositivo de id menor abre la sesion`() {
    assertTrue(SyncSessionPolicy.mayInitiate(menor, mayor, hasSession = false))
  }

  @Test
  fun `el dispositivo de id mayor espera a que la abran`() {
    assertFalse(SyncSessionPolicy.mayInitiate(mayor, menor, hasSession = false))
  }

  @Test
  fun `con sesion abierta los dos pueden enviar`() {
    assertTrue(SyncSessionPolicy.mayInitiate(mayor, menor, hasSession = true))
    assertTrue(SyncSessionPolicy.mayInitiate(menor, mayor, hasSession = true))
  }

  @Test
  fun `sin identificadores no se abre nada`() {
    assertFalse(SyncSessionPolicy.mayInitiate("", mayor, hasSession = false))
    assertFalse(SyncSessionPolicy.mayInitiate(menor, "  ", hasSession = false))
  }

  @Test
  fun `el mismo identificador no abre sesion contra si mismo`() {
    assertFalse(SyncSessionPolicy.mayInitiate(menor, menor, hasSession = false))
  }

  @Test
  fun `las mayusculas no cambian el orden`() {
    assertTrue(SyncSessionPolicy.mayInitiate("0D3180FC5BFA0000", mayor, hasSession = false))
    assertFalse(SyncSessionPolicy.mayInitiate("23C5177A3A9F0000", menor, hasSession = false))
  }
}
