package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncAttachmentAuditTest {
  private fun ref(fileId: String, savedPath: String?) = AttachmentRef(fileId, savedPath)

  @Test
  fun `cuenta como ausente el adjunto cuya ruta ya no existe`() {
    val missing = SyncAttachmentAudit.missing(
      listOf(ref("uno", "/otro/dispositivo/uno.png")),
      stored = { false },
      exists = { false },
    )

    assertEquals(1, missing)
  }

  @Test
  fun `da por presente el adjunto con copia sincronizada`() {
    val missing = SyncAttachmentAudit.missing(
      listOf(ref("uno", "/otro/dispositivo/uno.png")),
      stored = { it == "uno" },
      exists = { false },
    )

    assertEquals(0, missing)
  }

  @Test
  fun `da por presente el adjunto cuya ruta local existe`() {
    val missing = SyncAttachmentAudit.missing(
      listOf(ref("uno", "/aqui/uno.png")),
      stored = { false },
      exists = { it == "/aqui/uno.png" },
    )

    assertEquals(0, missing)
  }

  @Test
  fun `un adjunto sin ruta ni copia cuenta como ausente`() {
    assertFalse(SyncAttachmentAudit.present(ref("dos", null), stored = { false }, exists = { false }))
    assertTrue(SyncAttachmentAudit.present(ref("dos", null), stored = { true }, exists = { false }))
  }

  @Test
  fun `no propaga el fallo de la comprobacion de la ruta`() {
    val missing = SyncAttachmentAudit.missing(
      listOf(ref("uno", "/aqui/uno.png")),
      stored = { false },
      exists = { throw IllegalStateException("sin permiso") },
    )

    assertEquals(1, missing)
  }
}
