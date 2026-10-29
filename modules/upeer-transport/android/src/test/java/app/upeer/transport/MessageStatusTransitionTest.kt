package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageStatusTransitionTest {
  @Test
  fun `asciende hasta leido`() {
    assertTrue(MessageStatusTransition.canTransition("sent", "vaulted"))
    assertTrue(MessageStatusTransition.canTransition("vaulted", "delivered"))
    assertTrue(MessageStatusTransition.canTransition("delivered", "read"))
  }

  @Test
  fun `no rebaja un mensaje entregado ni leido`() {
    assertFalse(MessageStatusTransition.canTransition("delivered", "sent"))
    assertFalse(MessageStatusTransition.canTransition("read", "delivered"))
    assertFalse(MessageStatusTransition.canTransition("read", "failed"))
    assertFalse(MessageStatusTransition.canTransition("delivered", "failed"))
  }

  @Test
  fun `un mensaje guardado en el vault si puede fallar`() {
    assertTrue(MessageStatusTransition.canTransition("vaulted", "failed"))
    assertTrue(MessageStatusTransition.canTransition("sent", "failed"))
  }

  @Test
  fun `un fallo se puede reintentar`() {
    assertEquals("sent", MessageStatusTransition.next("failed", "sent"))
    assertEquals("vaulted", MessageStatusTransition.next("failed", "vaulted"))
    assertEquals("pending", MessageStatusTransition.next("failed", "pending"))
  }

  @Test
  fun `un acuse tardio rescata un mensaje fallido`() {
    assertEquals("delivered", MessageStatusTransition.next("failed", "delivered"))
  }

  @Test
  fun `el mismo estado no cambia nada`() {
    assertEquals("vaulted", MessageStatusTransition.next("vaulted", "vaulted"))
    assertEquals("failed", MessageStatusTransition.next("failed", "failed"))
  }

  @Test
  fun `un estado desconocido no rebaja lo que ya estaba`() {
    assertEquals("delivered", MessageStatusTransition.next("delivered", "raro"))
  }
}
