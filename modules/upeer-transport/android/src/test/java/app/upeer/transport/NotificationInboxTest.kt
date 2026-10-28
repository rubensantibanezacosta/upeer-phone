package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationInboxTest {
  @Test
  fun startsEmpty() {
    val inbox = NotificationInbox()

    assertTrue(inbox.isEmpty())
    assertEquals(0, inbox.conversations())
    assertEquals(0, inbox.messages())
    assertEquals(emptyList<String>(), inbox.lines())
    assertEquals("", inbox.summaryText())
  }

  @Test
  fun newestConversationComesFirst() {
    val inbox = NotificationInbox()
    inbox.record("u1", "Ana", "hola")
    inbox.record("u2", "Beto", "ey")

    assertEquals(listOf("Beto: ey", "Ana: hola"), inbox.lines())
  }

  @Test
  fun repeatConversationMovesUpWithoutDuplicating() {
    val inbox = NotificationInbox()
    inbox.record("u1", "Ana", "hola")
    inbox.record("u2", "Beto", "ey")
    inbox.record("u1", "Ana", "sigues ahí")

    assertEquals(listOf("Ana: sigues ahí", "Beto: ey"), inbox.lines())
    assertEquals(2, inbox.conversations())
  }

  @Test
  fun countsMessagesAndConversationsSeparately() {
    val inbox = NotificationInbox()
    inbox.record("u1", "Ana", "hola")
    inbox.record("u1", "Ana", "qué tal")
    inbox.record("u2", "Beto", "ey")

    assertEquals(3, inbox.messages())
    assertEquals(2, inbox.conversations())
    assertEquals("3 mensajes nuevos en 2 conversaciones", inbox.summaryText())
  }

  @Test
  fun usesSingularForOneMessageInOneConversation() {
    val inbox = NotificationInbox()
    inbox.record("u1", "Ana", "hola")

    assertEquals("1 mensaje nuevo en 1 conversación", inbox.summaryText())
  }

  @Test
  fun forgetsOneConversation() {
    val inbox = NotificationInbox()
    inbox.record("u1", "Ana", "hola")
    inbox.record("u2", "Beto", "ey")
    inbox.forget("u1")

    assertEquals(listOf("Beto: ey"), inbox.lines())
    assertEquals(1, inbox.conversations())
  }

  @Test
  fun dropsOldestConversationsBeyondTheCap() {
    val inbox = NotificationInbox(maxLines = 2)
    inbox.record("u1", "Ana", "hola")
    inbox.record("u2", "Beto", "ey")
    inbox.record("u3", "Caro", "buenas")

    assertEquals(listOf("Caro: buenas", "Beto: ey"), inbox.lines())
    assertEquals(2, inbox.conversations())
  }

  @Test
  fun blankPreviewLeavesOnlyTheLabel() {
    val inbox = NotificationInbox()
    inbox.record("u1", "Ana", "  ")

    assertEquals(listOf("Ana"), inbox.lines())
    assertTrue(inbox.lines().isNotEmpty())
  }

  @Test
  fun ignoresBlankConversationIdentifiers() {
    val inbox = NotificationInbox()
    inbox.record("  ", "Ana", "hola")

    assertTrue(inbox.isEmpty())
    assertFalse(inbox.lines().isNotEmpty())
  }

  @Test
  fun clearResetsTheSummary() {
    val inbox = NotificationInbox()
    inbox.record("u1", "Ana", "hola")
    inbox.record("u2", "Beto", "ey")
    inbox.clear()

    assertTrue(inbox.isEmpty())
    assertEquals(0, inbox.messages())
    assertEquals("", inbox.summaryText())
  }
}
