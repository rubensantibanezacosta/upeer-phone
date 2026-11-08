package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Llamar a otro dispositivo de mi propia cuenta.
 *
 * Antes estaba prohibido sin más: `startCall` cortaba con «no puedes llamarte a ti mismo». Con la
 * cuenta abierta en el móvil y el escritorio eso deja las llamadas **sin ningún uso posible**, porque
 * todos los contactos son uno mismo. Comprobado en el dispositivo el 08/11: cuatro contactos, los
 * cuatro de la misma cuenta, y ningún botón que funcione.
 *
 * La dirección de los otros dispositivos ya está en `sync_peers`, así que no hay que inventar nada:
 * solo leerla y ofrecer la llamada a ellos.
 */
class SelfCallTargetsTest {

  private val MY_DEVICE = "0d3180fc5bfa5c1a178e9550ef807adc9d21eaf788a59042894372babe880e17"
  private val OTHER_DEVICE = "a15e1111222233334444555566667777888899990000aaaabbbbccccddddeeee"
  private val DESKTOP = "200:7bba:1d01:9bb7:78bc:beae:f64a:6d59"
  private val LAPTOP = "200:a15e:1111:2222:3333:4444:5555:723a"

  private fun peer(deviceId: String, address: String): JSONObject =
    JSONObject().put("deviceId", deviceId).put("address", address)

  @Test
  fun `devuelve la direccion del otro dispositivo`() {
    val targets = SelfCallTargets.addresses(listOf(peer(OTHER_DEVICE, DESKTOP)), MY_DEVICE)
    assertEquals(listOf(DESKTOP), targets)
  }

  @Test
  fun `no se llama a si mismo`() {
    // Llamarse al propio teléfono saldría y volvería, y encima se contestaría «ocupado» a sí mismo.
    val targets = SelfCallTargets.addresses(listOf(peer(MY_DEVICE, DESKTOP)), MY_DEVICE)
    assertTrue(targets.isEmpty())
  }

  @Test
  fun `con varios dispositivos devuelve todas las direcciones`() {
    val targets = SelfCallTargets.addresses(
      listOf(peer(OTHER_DEVICE, DESKTOP), peer("b".repeat(64), LAPTOP)),
      MY_DEVICE,
    )
    assertEquals(listOf(DESKTOP, LAPTOP), targets)
  }

  @Test
  fun `descarta dispositivos sin direccion`() {
    // Un dispositivo guardado pero sin dirección no da a dónde llamar.
    val targets = SelfCallTargets.addresses(listOf(peer(OTHER_DEVICE, "")), MY_DEVICE)
    assertTrue(targets.isEmpty())
  }

  @Test
  fun `descarta direcciones invalidas`() {
    val targets = SelfCallTargets.addresses(
      listOf(peer(OTHER_DEVICE, "no es una direccion"), peer("c".repeat(64), DESKTOP)),
      MY_DEVICE,
    )
    assertEquals(listOf(DESKTOP), targets)
  }

  @Test
  fun `no repite la misma direccion`() {
    val targets = SelfCallTargets.addresses(
      listOf(peer(OTHER_DEVICE, DESKTOP), peer("d".repeat(64), DESKTOP)),
      MY_DEVICE,
    )
    assertEquals(listOf(DESKTOP), targets)
  }

  @Test
  fun `canCallSelf dice si hay a quien llamar`() {
    assertTrue(SelfCallTargets.canCallSelf(listOf(peer(OTHER_DEVICE, DESKTOP)), MY_DEVICE))
    // Solo yo: no hay a quién.
    assertFalse(SelfCallTargets.canCallSelf(listOf(peer(MY_DEVICE, DESKTOP)), MY_DEVICE))
    assertFalse(SelfCallTargets.canCallSelf(emptyList(), MY_DEVICE))
  }

  @Test
  fun `el identificador de dispositivo se compara sin distinguir mayusculas`() {
    val targets = SelfCallTargets.addresses(listOf(peer(OTHER_DEVICE.uppercase(), DESKTOP)), MY_DEVICE)
    assertEquals(listOf(DESKTOP), targets)
  }

  @Test
  fun `sin dispositivo local propio se devuelven todos`() {
    // Sin enrolar (modo solo dispositivo) no hay un «yo» que descartar.
    val targets = SelfCallTargets.addresses(listOf(peer(OTHER_DEVICE, DESKTOP)), null)
    assertEquals(listOf(DESKTOP), targets)
  }
}
