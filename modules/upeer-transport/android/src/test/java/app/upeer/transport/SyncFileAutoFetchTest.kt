package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncFileAutoFetchTest {
  private var now = 1_700_000_000_000L
  private val asked = mutableListOf<String>()
  private var stored = setOf<String>()
  private var pending = setOf<String>()
  private var partial = mapOf<String, Float>()
  private var accepts = true
  private var refs = listOf<AttachmentRef>()

  private val fetch = SyncFileAutoFetch(
    attachments = { refs },
    stored = { fileId -> fileId in stored },
    request = { fileId ->
      if (accepts) asked.add(fileId)
      accepts
    },
    isPending = { fileId -> fileId in pending },
    progress = { fileId -> partial[fileId] },
    nowMillis = { now },
  )

  @Test
  fun `pide una sola vez el adjunto que falta`() {
    refs = listOf(AttachmentRef("uno", null))

    val first = fetch.tick()
    assertEquals(1, first.total)
    assertEquals(1, first.pending)
    assertEquals(0, first.done)

    fetch.tick()
    assertEquals(listOf("uno"), asked)
  }

  @Test
  fun `no repite la peticion mientras el dispositivo siga sirviendola`() {
    refs = listOf(AttachmentRef("uno", null))
    fetch.tick()
    pending = setOf("uno")

    now += 5_000L
    fetch.tick()

    assertEquals(listOf("uno"), asked)
  }

  @Test
  fun `reintenta cuando la peticion caduca sin respuesta`() {
    refs = listOf(AttachmentRef("uno", null))
    fetch.tick()

    now += 25_000L
    fetch.tick()

    assertEquals(listOf("uno", "uno"), asked)
  }

  @Test
  fun `se rinde solo tras quince minutos sin respuesta`() {
    refs = listOf(AttachmentRef("uno", null))
    fetch.tick()
    repeat(60) {
      now += 25_000L
      fetch.tick()
    }

    assertTrue(asked.size > 10)
    assertEquals(1, fetch.snapshot().failed)
    assertEquals(0, fetch.snapshot().pending)
  }

  @Test
  fun `la linea base no cuenta como trabajo pendiente`() {
    refs = listOf(AttachmentRef("uno", null), AttachmentRef("dos", null))
    stored = setOf("uno")

    val work = fetch.tick()

    assertEquals(1, work.total)
    assertEquals(0, work.done)
    assertEquals(1, work.pending)
    assertEquals(listOf("dos"), asked)
  }

  @Test
  fun `lo que se trae despues si suma al progreso`() {
    refs = listOf(AttachmentRef("uno", null), AttachmentRef("dos", null))
    stored = setOf("uno")
    fetch.tick()

    stored = setOf("uno", "dos")
    val work = fetch.tick()

    assertEquals(1, work.total)
    assertEquals(1, work.done)
    assertEquals(0, work.pending)
  }

  @Test
  fun `si el adjunto desaparece del disco se vuelve a pedir`() {
    refs = listOf(AttachmentRef("uno", null), AttachmentRef("dos", null))
    stored = setOf("uno")
    fetch.tick()

    stored = setOf("dos")
    val work = fetch.tick()

    assertEquals(listOf("dos", "uno"), asked)
    assertEquals(2, work.total)
    assertEquals(1, work.done)
    assertEquals(1, work.pending)
  }

  @Test
  fun `da por bueno el adjunto con savedPath en disco`() {
    val file = java.io.File.createTempFile("upeer-adjunto", ".png")
    try {
      refs = listOf(AttachmentRef("uno", file.absolutePath))

      val work = fetch.tick()

      assertFalse(work.known)
      assertTrue(asked.isEmpty())
    } finally {
      file.delete()
    }
  }

  @Test
  fun `no pide nada si el otro dispositivo no acepta la peticion todavia`() {
    refs = listOf(AttachmentRef("uno", null))
    accepts = false

    val work = fetch.tick()

    assertEquals(1, work.pending)
    assertEquals(0, work.failed)

    now += 65_000L
    accepts = true
    fetch.tick()

    assertEquals(listOf("uno"), asked)
  }

  @Test
  fun `olvidar un adjunto permite volver a pedirlo`() {
    refs = listOf(AttachmentRef("uno", null))
    fetch.tick()
    fetch.forget("uno")
    fetch.tick()

    assertEquals(listOf("uno", "uno"), asked)
  }

  @Test
  fun `sin adjuntos el estado queda vacio`() {
    val work = fetch.tick()

    assertFalse(work.known)
    assertTrue(work.idle)
    assertEquals(SyncFileWork.EMPTY, work)
  }

  @Test
  fun `el progreso no retrocede si el adjunto deja de aparecer`() {
    refs = listOf(AttachmentRef("uno", null))
    fetch.tick()
    stored = setOf("uno")
    assertEquals(1, fetch.tick().done)

    refs = emptyList()
    stored = emptySet()
    val work = fetch.tick()

    assertEquals(1, work.total)
    assertEquals(1, work.done)
  }

  @Test
  fun `una enumeracion rota no rompe el bucle`() {
    val roto = SyncFileAutoFetch(
      attachments = { error("sin estado") },
      stored = { false },
      request = { false },
      isPending = { false },
      nowMillis = { now },
    )

    assertEquals(SyncFileWork.EMPTY, roto.tick())
  }

  @Test
  fun `la barra avanza con los tramos que ya llegaron`() {
    refs = listOf(AttachmentRef("uno", null), AttachmentRef("dos", null))
    fetch.tick()

    partial = mapOf("uno" to 0.5f)
    val work = fetch.refresh()

    assertEquals(2, work.total)
    assertEquals(0, work.done)
    assertEquals(0.5f, work.progressUnits ?: 0f, 0.001f)
    assertEquals(25, work.percent)
  }

  @Test
  fun `cada adjunto en curso publica su propio avance`() {
    refs = listOf(AttachmentRef("uno", null), AttachmentRef("dos", null))
    fetch.tick()

    partial = mapOf("uno" to 0.5f, "dos" to 0.25f)
    val work = fetch.refresh()

    assertEquals(mapOf("uno" to 50, "dos" to 25), work.active)
  }

  @Test
  fun `el adjunto ya traido deja de anunciar avance`() {
    refs = listOf(AttachmentRef("uno", null), AttachmentRef("dos", null))
    fetch.tick()

    partial = mapOf("uno" to 0.5f)
    fetch.tick()
    stored = setOf("uno")
    val work = fetch.tick()

    assertEquals(setOf("dos"), work.active.keys)
  }

  @Test
  fun `el refresco no vuelve a pedir ni a enumerar adjuntos`() {
    refs = listOf(AttachmentRef("uno", null))
    fetch.tick()

    refs = emptyList()
    fetch.refresh()
    fetch.refresh()

    assertEquals(listOf("uno"), asked)
    assertEquals(1, fetch.snapshot().total)
  }

  @Test
  fun `el refresco mantiene el pendiente y el fallido del ultimo repaso`() {
    refs = listOf(AttachmentRef("uno", null), AttachmentRef("dos", null))
    accepts = false
    fetch.tick()
    accepts = true
    now += 65_000L
    fetch.tick()

    val work = fetch.refresh()

    assertEquals(2, work.pending)
    assertEquals(0, work.failed)
  }
}
