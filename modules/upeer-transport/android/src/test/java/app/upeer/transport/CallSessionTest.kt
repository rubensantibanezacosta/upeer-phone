package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSessionTest {
  private val peer = "a".repeat(64)

  private fun session(
    outgoing: Boolean = true,
    kind: String = CallKinds.AUDIO,
    connectedAt: Long = 0L,
    endedAt: Long = 1_800L,
    endReason: String = CallEndReasons.LOCAL_HANGUP,
  ) = CallSession(
    callId = "0".repeat(32),
    peerUpeerId = peer,
    kind = kind,
    outgoing = outgoing,
    phase = CallPhases.ENDED,
    muted = false,
    cameraEnabled = false,
    startedAt = 1_000L,
    connectedAt = connectedAt,
    endedAt = endedAt,
    endReason = endReason,
    eventWritten = true,
  )

  @Test
  fun `genera identificadores de 32 caracteres hexadecimales distintos`() {
    val first = CallIds.generate()
    val second = CallIds.generate()
    assertEquals(32, first.length)
    assertTrue(first.all { it in "0123456789abcdef" })
    assertNotEquals(first, second)
  }

  @Test
  fun `el identificador del evento es un uuid estable y compatible con el escritorio`() {
    val callId = "0123456789abcdef0123456789abcdef"
    assertEquals("01234567-89ab-4def-a123-456789abcdef", CallIds.eventMessageId(callId))
    assertEquals(CallIds.eventMessageId(callId), CallIds.eventMessageId(callId))
  }

  @Test
  fun `un callId corto se rellena hasta formar un uuid válido`() {
    val eventId = CallIds.eventMessageId("abc")
    assertEquals(36, eventId.length)
    assertTrue(ReplayPolicy.validMessageID(eventId))
  }

  @Test
  fun `una llamada con conversación se resume con duración e instante de conexión`() {
    val text = CallEvent.summary(session(connectedAt = 1_000L, endedAt = 66_000L))
    assertEquals("Llamada de voz · 01:05 · 1000", text)
  }

  @Test
  fun `una videollamada perdida se resume como perdida`() {
    assertEquals("Videollamada perdida", CallEvent.summary(session(kind = CallKinds.VIDEO, endReason = CallEndReasons.NO_ANSWER)))
  }

  @Test
  fun `una llamada entrante rechazada se resume como rechazada`() {
    assertEquals("Llamada de voz rechazada", CallEvent.summary(session(outgoing = false, endReason = CallEndReasons.REJECTED)))
  }

  @Test
  fun `una llamada entrante perdida no deja evento local`() {
    assertNull(CallEvent.summary(session(outgoing = false, endReason = CallEndReasons.NO_ANSWER)))
  }

  @Test
  fun `solo avisa al otro extremo cuando el escritorio también lo haría`() {
    assertTrue(CallEvent.notifiesPeer(session(outgoing = true)))
    assertFalse(CallEvent.notifiesPeer(session(outgoing = true, endReason = CallEndReasons.REJECTED)))
    assertTrue(CallEvent.notifiesPeer(session(outgoing = false, connectedAt = 1_000L)))
    assertFalse(CallEvent.notifiesPeer(session(outgoing = false)))
  }

  @Test
  fun `la duración se formatea en minutos y segundos`() {
    assertEquals("00:00", CallEvent.duration(0))
    assertEquals("00:09", CallEvent.duration(9))
    assertEquals("02:03", CallEvent.duration(123))
    assertEquals("00:00", CallEvent.duration(-5))
  }
}
