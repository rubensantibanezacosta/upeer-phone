package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Banco de medidas del emisor de adjuntos: cuánto cuesta **construir** los frames de un fichero con
 * un tramo por frame y con varios.
 *
 * No mide la red ni el cifrado del ratchet —eso necesita los dos dispositivos—, pero sí aísla la parte
 * que el agrupado cambia directamente: cuántos frames se serializan y cuánto se tarda en ello. Es lo
 * que permite afirmar algo con número en vez de «debería ser más rápido».
 */
class SyncFileBatchingBenchmarkTest {
  private val sender = SyncSender("a".repeat(64), JSONObject().put("deviceId", "a".repeat(64)))
  private val recipient = "b".repeat(64)
  private val requestId = "abc12345"

  private fun sampleFile(bytes: Int): File {
    val directory = createTempDirectory("upeer-bench").toFile()
    val file = File(directory, "video.bin")
    val chunk = ByteArray(SyncFilePackets.CHUNK_SIZE) { (it % 251).toByte() }
    file.outputStream().use { out ->
      var written = 0
      while (written < bytes) {
        val size = minOf(chunk.size, bytes - written)
        out.write(chunk, 0, size)
        written += size
      }
    }
    return file
  }

  private fun framesFor(file: File): Pair<Int, Long> {
    val size = file.length()
    val total = SyncFilePackets.chunkCount(size, SyncFilePackets.CHUNK_SIZE)
    val meta = SyncFilePackets.ResMeta(
      fileName = file.name,
      fileSize = size,
      chunkSize = SyncFilePackets.CHUNK_SIZE,
      totalChunks = total,
    )
    val header = SyncFilePackets.response(sender, recipient, requestId, "file-1", meta)
    requireNotNull(header) { "cabecera del adjunto no construible" }
    val packets = SyncFileOutgoing(header, sender, recipient, requestId, file, size, total)
    var frames = 0
    var bytes = 0L
    val started = System.nanoTime()
    try {
      while (packets.hasNext()) {
        val packet = packets.next()
        frames += 1
        bytes += packet.toString().toByteArray(Charsets.UTF_8).size
      }
    } finally {
      packets.release()
    }
    val elapsed = (System.nanoTime() - started) / 1_000_000
    println("MEDIDA frames=$frames bytes=$bytes ms=$elapsed")
    return frames to elapsed
  }

  @Test
  fun `el agrupado reduce el numero de frames de un video de 140 tramos`() {
    // Mismo tamaño que el vídeo de las pruebas de §3.5: 27 822 547 B ⇒ 142 tramos.
    val file = sampleFile(27_822_547)
    file.deleteOnExit()

    val (frames, elapsed) = framesFor(file)

    val chunks = SyncFilePackets.chunkCount(file.length(), SyncFilePackets.CHUNK_SIZE)
    // 1 frame de cabecera + un frame por lote de 8 tramos.
    val batches = (chunks + SyncFilePackets.MAX_BATCH_CHUNKS - 1) / SyncFilePackets.MAX_BATCH_CHUNKS
    val expected = 1 + batches
    println("MEDIDA video chunks=$chunks frames=$frames (1 cabecera + $batches lotes) ms=$elapsed")

    assertTrue("$chunks tramos deberían salir en $expected frames, fueron $frames", frames == expected)
    assertTrue("el agrupado debería bajar de $chunks frames a menos de 30", frames < 30)
    assertTrue(
      "construir los frames no debería tardar $elapsed ms en un equipo de desarrollo",
      elapsed < 60_000,
    )
  }

  @Test
  fun `un fichero de un solo tramo no se agrupa`() {
    val file = sampleFile(1024)
    file.deleteOnExit()

    val (frames, _) = framesFor(file)

    // Cabecera + un único tramo sin agrupar.
    assertTrue("un fichero pequeño son cabecera + 1 tramo, salieron $frames", frames == 2)
  }

  @Test
  fun `el ultimo lote de un fichero irregular lleva los tramos que quedan`() {
    // 10 tramos con lotes de 8 ⇒ 8 + 2 = dos frames.
    val file = sampleFile(SyncFilePackets.CHUNK_SIZE * 10)
    file.deleteOnExit()

    val (frames, _) = framesFor(file)

    // Cabecera + (8 tramos) + (2 tramos).
    assertTrue("10 tramos deberían salir en 3 frames (cabecera + 2 lotes), salieron $frames", frames == 3)
  }
}
