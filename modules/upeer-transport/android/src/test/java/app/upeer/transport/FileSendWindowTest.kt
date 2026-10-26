package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSendWindowTest {
  @Test
  fun `primera tanda llena la ventana inicial`() {
    val window = FileSendWindow(totalChunks = 100)
    assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), window.nextBatch(0L))
    assertEquals(8, window.pending())
    assertTrue(window.nextBatch(1L).isEmpty())
  }

  @Test
  fun `un acuse rapido abre hueco y crece la ventana`() {
    val window = FileSendWindow(totalChunks = 100)
    window.nextBatch(0L)
    assertTrue(window.ack(0, 50L))
    assertEquals(9, window.windowSize())
    assertEquals(listOf(8, 9), window.nextBatch(60L))
  }

  @Test
  fun `un acuse lento mantiene la ventana`() {
    val window = FileSendWindow(totalChunks = 100)
    window.nextBatch(0L)
    assertTrue(window.ack(3, 5_000L))
    assertEquals(8, window.windowSize())
  }

  @Test
  fun `un acuse duplicado no cuenta dos veces`() {
    val window = FileSendWindow(totalChunks = 100)
    window.nextBatch(0L)
    assertTrue(window.ack(1, 10L))
    assertTrue(window.ack(1, 20L))
    assertEquals(1, window.ackedCount())
  }

  @Test
  fun `un acuse fuera de rango se descarta`() {
    val window = FileSendWindow(totalChunks = 10)
    window.nextBatch(0L)
    assertFalse(window.ack(-1, 10L))
    assertFalse(window.ack(10, 10L))
    assertEquals(0, window.ackedCount())
  }

  @Test
  fun `los trozos sin acuse caducan y se reenvian`() {
    val window = FileSendWindow(totalChunks = 4, retransmitMillis = 1_000L)
    window.nextBatch(0L)
    assertTrue(window.expired(500L).isEmpty())
    assertEquals(listOf(0, 1, 2, 3), window.expired(1_000L))
    assertTrue(window.expired(1_999L).isEmpty())
    assertEquals(listOf(0, 1, 2, 3), window.expired(2_000L))
  }

  @Test
  fun `el retroceso reduce la ventana a la mitad`() {
    val window = FileSendWindow(totalChunks = 200, initialWindow = 16)
    window.backoff()
    assertEquals(8, window.windowSize())
    repeat(6) { window.backoff() }
    assertEquals(1, window.windowSize())
  }

  @Test
  fun `la ventana nunca supera el maximo`() {
    val window = FileSendWindow(totalChunks = 500, initialWindow = 30, maxWindow = 32)
    window.nextBatch(0L)
    repeat(20) { index ->
      window.ack(index, (index + 1) * 10L)
      window.nextBatch((index + 1) * 10L)
    }
    assertEquals(32, window.windowSize())
    assertTrue(window.windowSize() <= 32)
  }

  @Test
  fun `la transferencia termina cuando se acusan todos los trozos`() {
    val window = FileSendWindow(totalChunks = 3)
    window.nextBatch(0L)
    assertFalse(window.complete())
    assertTrue(window.ack(0, 10L))
    assertTrue(window.ack(1, 10L))
    assertTrue(window.hasWork())
    assertTrue(window.ack(2, 10L))
    assertTrue(window.complete())
    assertFalse(window.hasWork())
  }

  @Test
  fun `un archivo sin trozos se considera completo`() {
    val window = FileSendWindow(totalChunks = 0)
    assertTrue(window.complete())
    assertFalse(window.hasWork())
    assertTrue(window.nextBatch(0L).isEmpty())
  }
}
