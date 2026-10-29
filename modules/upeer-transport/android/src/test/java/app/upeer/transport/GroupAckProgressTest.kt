package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupAckProgressTest {
  private val me = "a".repeat(64)
  private val ivan = "b".repeat(64)
  private val dev = "c".repeat(64)

  private fun message() = JSONObject()
    .put("id", "m1")
    .put("upeerId", "grp-1")
    .put("isMine", true)
    .put("status", "sent")

  @Test
  fun `los destinatarios esperados son los miembros menos yo mismo`() {
    val expected = GroupAckProgress.recipients(listOf(me, ivan, dev.uppercase()), me)

    assertEquals(setOf(ivan, dev), expected)
  }

  @Test
  fun `un solo ack no completa la entrega`() {
    val message = message()
    val expected = GroupAckProgress.recipients(listOf(me, ivan, dev), me)

    val delivered = GroupAckProgress.record(message, ivan)

    assertFalse(GroupAckProgress.complete(delivered, expected))
    assertEquals("sent", message.optString("status"))
  }

  @Test
  fun `cuando responden todos los miembros la entrega se completa`() {
    val message = message()
    val expected = GroupAckProgress.recipients(listOf(me, ivan, dev), me)

    GroupAckProgress.record(message, ivan)
    val delivered = GroupAckProgress.record(message, dev)

    assertTrue(GroupAckProgress.complete(delivered, expected))
  }

  @Test
  fun `el mismo miembro dos veces no adelanta nada`() {
    val message = message()
    val expected = GroupAckProgress.recipients(listOf(me, ivan, dev), me)

    GroupAckProgress.record(message, ivan)
    val delivered = GroupAckProgress.record(message, ivan)

    assertEquals(setOf(ivan), delivered)
    assertFalse(GroupAckProgress.complete(delivered, expected))
  }

  @Test
  fun `un acuse de alguien que no es miembro no completa la entrega`() {
    val message = message()
    val expected = GroupAckProgress.recipients(listOf(me, ivan, dev), me)

    val delivered = GroupAckProgress.record(message, "d".repeat(64))

    assertFalse(GroupAckProgress.complete(delivered, expected))
  }

  @Test
  fun `sin miembros esperados nunca se da por entregado`() {
    val message = message()

    val delivered = GroupAckProgress.record(message, ivan)

    assertFalse(GroupAckProgress.complete(delivered, emptySet()))
  }

  @Test
  fun `la lista de acuses se guarda ordenada y sin duplicados`() {
    val message = message()

    GroupAckProgress.record(message, dev)
    GroupAckProgress.record(message, ivan)

    assertEquals(listOf(ivan, dev), GroupAckProgress.delivered(message).sorted())
    assertEquals(2, message.optJSONArray(GroupAckProgress.FIELD)?.length())
  }
}
