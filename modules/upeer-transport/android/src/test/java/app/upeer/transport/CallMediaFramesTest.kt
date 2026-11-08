package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * El frame de media del móvil tiene que ser byte a byte el mismo que el del escritorio.
 *
 * Los `base64` de abajo **no están escritos a mano**: salen de ejecutar `encodeMediaFrame` del
 * escritorio (`upper-desktop/src/features/call/mediaChunker.ts`). Si alguien cambia el formato en un
 * cliente y no en el otro, esto falla antes de que la llamada se quede muda en el teléfono.
 *
 * El caso que más se escapa es el orden de bytes: `DataView` escribe big endian y Android es little
 * endian por naturaleza. Con el orden mal, el frame se decodifica igual y la marca de tiempo sale
 * absurda; no revienta nada, simplemente no se oye ni se ve.
 */
class CallMediaFramesTest {

  private fun decode64(value: String): ByteArray = Base64.getDecoder().decode(value)

  @Test
  fun `decodifica el frame de audio del escritorio`() {
    val frame = CallMediaFrames.decode(decode64("AAEAAEJ6DEUGwAAAAAAAAQECAwQ="))!!
    assertEquals(CallMediaFrames.KIND_AUDIO, frame.kind)
    assertTrue(frame.keyFrame)
    assertEquals(1790000000000.0, frame.timestamp, 0.0)
    assertEquals(1L, frame.sequence)
    assertTrue(frame.data.contentEquals(byteArrayOf(1, 2, 3, 4)))
  }

  @Test
  fun `decodifica el frame de video sin fotograma clave`() {
    val frame = CallMediaFrames.decode(decode64("AQAAAEJ6DEUGx7gA/////96tvu8="))!!
    assertEquals(CallMediaFrames.KIND_VIDEO, frame.kind)
    assertTrue(!frame.keyFrame)
    // Marca de tiempo con decimales y secuencia en el máximo de uint32: los dos se pierden si se
    // leen con el orden de bytes equivocado.
    assertEquals(1790000000123.5, frame.timestamp, 0.0)
    assertEquals(4294967295L, frame.sequence)
    assertTrue(frame.data.contentEquals(byteArrayOf(0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte())))
  }

  @Test
  fun `decodifica el frame de pantalla`() {
    val frame = CallMediaFrames.decode(decode64("AgEAAEJ6DEUG/nAAAAAAAAk="))!!
    assertEquals(CallMediaFrames.KIND_SCREEN, frame.kind)
    assertTrue(frame.keyFrame)
    assertEquals(1790000000999.0, frame.timestamp, 0.0)
    assertEquals(0L, frame.sequence)
    assertTrue(frame.data.contentEquals(byteArrayOf(9)))
  }

  @Test
  fun `codificar y decodificar da el mismo frame`() {
    val data = ByteArray(4096) { (it % 251).toByte() }
    val encoded = CallMediaFrames.encode(CallMediaFrames.KIND_VIDEO, false, 1790000000123.5, 4294967295L, data)
    assertEquals(CallMediaFrames.HEADER_BYTES + data.size, encoded.size)
    val frame = CallMediaFrames.decode(encoded)!!
    assertEquals(CallMediaFrames.KIND_VIDEO, frame.kind)
    assertTrue(!frame.keyFrame)
    assertEquals(1790000000123.5, frame.timestamp, 0.0)
    assertEquals(4294967295L, frame.sequence)
    assertTrue(frame.data.contentEquals(data))
  }

  @Test
  fun `la cabecera del movil coincide con la del escritorio byte a byte`() {
    // Se compara la cabecera codificada aquí con la que produjo el escritorio para el mismo frame.
    val ours = CallMediaFrames.encode(CallMediaFrames.KIND_AUDIO, true, 1790000000000.0, 1L, byteArrayOf(1, 2, 3, 4))
    assertTrue(ours.contentEquals(decode64("AAEAAEJ6DEUGwAAAAAAAAQECAwQ=")))
  }

  @Test
  fun `un frame invalido se descarta sin lanzar`() {
    // Lo alimenta un par remoto: no puede tumbar la llamada.
    assertNull(CallMediaFrames.decode(ByteArray(0)))
    assertNull(CallMediaFrames.decode(ByteArray(CallMediaFrames.HEADER_BYTES - 1)))
    // Tipo de media desconocido.
    val badKind = CallMediaFrames.encode(CallMediaFrames.KIND_AUDIO, true, 1.0, 1L, byteArrayOf(1))
    badKind[0] = 9
    assertNull(CallMediaFrames.decode(badKind))
    // Byte de fotograma clave fuera de rango.
    val badType = CallMediaFrames.encode(CallMediaFrames.KIND_AUDIO, true, 1.0, 1L, byteArrayOf(1))
    badType[1] = 7
    assertNull(CallMediaFrames.decode(badType))
    // Cabecera sin datos.
    assertNull(CallMediaFrames.decode(CallMediaFrames.encode(CallMediaFrames.KIND_AUDIO, true, 1.0, 1L, ByteArray(0))))
    // Marca de tiempo no finita.
    assertNull(CallMediaFrames.decode(CallMediaFrames.encode(CallMediaFrames.KIND_AUDIO, true, Double.NaN, 1L, byteArrayOf(1))))
  }

  @Test
  fun `la secuencia se lee como uint32 sin signo`() {
    // Un contador que pase de 2^31 no puede volverse negativo: el receptor ordena por este número.
    val encoded = CallMediaFrames.encode(CallMediaFrames.KIND_AUDIO, false, 1.0, 2147483648L, byteArrayOf(1))
    assertEquals(2147483648L, CallMediaFrames.decode(encoded)!!.sequence)
  }

  @Test
  fun `encode rechaza un tipo de media que no existe`() {
    val failed = runCatching {
      CallMediaFrames.encode(7, true, 1.0, 1L, byteArrayOf(1))
    }
    assertTrue("debería rechazar el tipo 7", failed.isFailure)
  }
}
