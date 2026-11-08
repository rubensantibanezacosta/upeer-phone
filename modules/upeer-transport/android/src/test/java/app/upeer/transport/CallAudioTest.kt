package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El audio de la llamada: parámetros iguales a los del escritorio y un control que no deje recursos
 * abiertos.
 *
 * El códec en sí (MediaCodec, AudioRecord) no se puede ejecutar en JVM, así que aquí se prueba la capa
 * que decide **cuándo** se abre y se cierra. Es donde de verdad se rompen las llamadas: un micrófono
 * que se abre dos veces, un cierre que no cierra, o frames viejos que se reproducen encima de los
 * nuevos.
 */
class CallAudioConfigTest {

  @Test
  fun `los parametros coinciden con los del escritorio`() {
    // Los del escritorio están en audioCodecConfig.ts: Opus, 48 kHz, mono, 64 kbps para voz y 96 para
    // el audio de compartir pantalla. Si cambian allí, esto avisa.
    assertEquals("audio/opus", CallAudioConfig.MIME_TYPE)
    assertEquals(48_000, CallAudioConfig.SAMPLE_RATE)
    assertEquals(1, CallAudioConfig.CHANNELS)
    assertEquals(64_000, CallAudioConfig.VOICE_BITRATE)
    assertEquals(96_000, CallAudioConfig.SCREEN_BITRATE)
    assertEquals(64_000, CallAudioConfig.bitrateFor(screenAudio = false))
    assertEquals(96_000, CallAudioConfig.bitrateFor(screenAudio = true))
  }

  @Test
  fun `un frame de 20 ms a 48 kHz son 960 muestras y 1920 bytes`() {
    // El tamaño tiene que cuadrar con lo que se lee del micrófono; si no, cada frame arrastra media
    // muestra y el audio se va descolocando.
    assertEquals(20, CallAudioConfig.FRAME_MILLIS)
    assertEquals(960, CallAudioConfig.SAMPLES_PER_FRAME)
    assertEquals(1_920, CallAudioConfig.BYTES_PER_FRAME)
  }

  @Test
  fun `un sample rate que no esta en la lista se sustituye por el de voz`() {
    assertEquals(48_000, CallAudioConfig.sampleRateOr(1234))
    assertEquals(48_000, CallAudioConfig.sampleRateOr(-1))
    // Los válidos se respetan.
    assertEquals(24_000, CallAudioConfig.sampleRateOr(24_000))
    assertEquals(48_000, CallAudioConfig.sampleRateOr(48_000))
  }

  @Test
  fun `un bitrate absurdo se cambia por el del tipo`() {
    assertEquals(64_000, CallAudioConfig.sanitizeBitrate(0))
    assertEquals(64_000, CallAudioConfig.sanitizeBitrate(-5))
    assertEquals(64_000, CallAudioConfig.sanitizeBitrate(999_999))
    assertEquals(96_000, CallAudioConfig.sanitizeBitrate(0, screenAudio = true))
    // Un valor razonable se respeta.
    assertEquals(32_000, CallAudioConfig.sanitizeBitrate(32_000))
  }

  @Test
  fun `describe anuncia lo mismo que se usa`() {
    val described = CallAudioConfig.describe()
    assertEquals("audio/opus", described["mimeType"])
    assertEquals(48_000, described["sampleRate"])
    assertEquals(1, described["channels"])
    assertEquals(64_000, described["bitrate"])
  }
}

class CallAudioStateTest {

  @Test
  fun `abre y cierra una vez`() {
    var opened = 0
    var closed = 0
    val state = CallAudioState(open = { opened += 1 }, close = { closed += 1 })

    assertFalse(state.isLive())
    assertTrue(state.start())
    assertTrue(state.isLive())
    assertEquals(1, opened)

    state.stop()
    assertFalse(state.isLive())
    assertEquals(1, closed)
  }

  @Test
  fun `el cierre es idempotente`() {
    var closed = 0
    val state = CallAudioState(close = { closed += 1 })
    state.start()
    state.stop()
    state.stop()
    state.stop()
    assertEquals("cerrar tres veces no puede soltar recursos tres veces", 1, closed)
  }

  @Test
  fun `abrir dos veces no deja un microfono huerfano`() {
    // Dos AudioRecord abiertos sobre el mismo micrófono: uno se queda sin que nadie lo cierre y sigue
    // gastando batería. El segundo start no puede volver a abrir.
    var opened = 0
    val state = CallAudioState(open = { opened += 1 })
    assertTrue(state.start())
    assertTrue(state.start())
    assertTrue(state.start())
    assertEquals(1, opened)
  }

  @Test
  fun `si abrir falla se queda parado y guarda el motivo`() {
    val state = CallAudioState(open = { error("sin micrófono") })
    assertFalse(state.start())
    assertFalse(state.isLive())
    assertNotNull(state.failure())
    assertTrue(state.failure()!!.contains("micrófono"))
    // Un fallo no puede dejar el candado tomado: se puede volver a intentar.
    assertFalse(state.start())
  }

  @Test
  fun `tras un fallo se puede reintentar`() {
    var attempts = 0
    val state = CallAudioState(open = {
      attempts += 1
      if (attempts == 1) error("primera vez falla")
    })
    assertFalse(state.start())
    assertTrue(state.start())
    assertTrue(state.isLive())
    assertNull(state.failure())
  }

  @Test
  fun `los frames fuera de orden se descartan`() {
    val state = CallAudioState()
    state.start()
    assertTrue("el primero entra siempre", state.onFrameReceived(10))
    assertTrue(state.onFrameReceived(11))
    assertFalse("uno repetido no puede colarse", state.onFrameReceived(11))
    assertFalse("uno que va hacia atrás tampoco", state.onFrameReceived(5))
    assertTrue(state.onFrameReceived(12))
    assertEquals(3, state.framesReceived())
    assertEquals(2, state.droppedOutOfOrder())
  }

  @Test
  fun `no se aceptan frames con el motor parado`() {
    val state = CallAudioState()
    assertFalse(state.onFrameReceived(1))
    state.start()
    assertTrue(state.onFrameReceived(1))
    state.stop()
    assertFalse("con la llamada colgada no entra nada", state.onFrameReceived(2))
  }

  @Test
  fun `la secuencia se reinicia al volver a abrir`() {
    val state = CallAudioState()
    state.start()
    state.onFrameReceived(1000)
    state.stop()
    state.start()
    // Una llamada nueva empieza en secuencias bajas; si no se reiniciara, se descartaría todo.
    assertTrue(state.onFrameReceived(1))
    assertEquals(1, state.framesReceived())
  }

  @Test
  fun `los frames enviados solo cuentan con la llamada viva`() {
    val state = CallAudioState()
    state.start()
    state.onFrameSent()
    state.onFrameSent()
    assertEquals(2, state.framesSent())
    state.stop()
    state.onFrameSent()
    assertEquals("con la llamada cerrada no se cuentan envíos", 2, state.framesSent())
  }
}
