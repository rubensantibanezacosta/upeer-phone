package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El pegamento entre el motor de audio y el transporte.
 *
 * Antes de esto, `CallAudioEngine` y `CallMediaPacket` existían y nadie los llamaba: la llamada se
 * quedaba en «El audio todavía no está disponible en este cliente». Aquí se prueba lo que puede
 * romperse sin oír nada en el dispositivo: cuándo se abre, cuándo se cierra, y que no se reproduzca
 * audio de una llamada que aún no está conectada.
 *
 * El micrófono y el códec reales no se pueden abrir en JVM, así que el motor se sustituye por uno falso.
 */
class CallMediaCoordinatorTest {

  /** Motor falso: el real necesita micrófono y códec, que en JVM no existen. */
  private class FakeSink(val sendFrame: (ByteArray) -> Unit) : CallMediaSink {
    var capture = false
    var playback = false
    var stopped = false
    val played = mutableListOf<ByteArray>()
    var muted: Boolean? = null
    override fun startCapture() { capture = true }
    override fun startPlayback() { playback = true }
    override fun playFrame(encoded: ByteArray) { played.add(encoded) }
    override fun setMuted(value: Boolean) { muted = value }
    override fun stop() { stopped = true }
  }

  private fun session(): CallSession = CallSession(
    callId = "call-1",
    peerUpeerId = "b".repeat(64),
    kind = CallKinds.AUDIO,
    outgoing = false,
    phase = CallPhases.CONNECTED,
    muted = false,
    cameraEnabled = false,
    startedAt = 1_000L,
    connectedAt = 1_000L,
    endedAt = 0L,
    endReason = "",
    eventWritten = false,
  )

  private fun frameData(sequence: Long = 1L): ByteArray =
    CallMediaFrames.encode(CallMediaFrames.KIND_AUDIO, true, 1.0, sequence, byteArrayOf(1, 2, 3, 4))

  @Test
  fun `abrir la media la deja viva y cerrarla la suelta`() {
    var sink: FakeSink? = null
    val coordinator = CallMediaCoordinator(createEngine = { send -> FakeSink(send).also { sink = it } })

    assertTrue(coordinator.start(session()) {})
    assertTrue(coordinator.live())
    assertTrue("el micrófono tiene que abrirse", sink?.capture == true)
    assertTrue("la reproducción también", sink?.playback == true)

    coordinator.stop()
    assertFalse(coordinator.live())
    assertTrue("cerrar tiene que soltar el motor", sink?.stopped == true)
  }

  @Test
  fun `un frame de entrada se reproduce cuando la llamada esta conectada`() {
    var sink: FakeSink? = null
    val coordinator = CallMediaCoordinator(createEngine = { send -> FakeSink(send).also { sink = it } })
    coordinator.start(session()) {}
    val bytes = frameData(1)

    assertTrue(coordinator.onInboundPacket(JSONObject().put("type", "CALL_MEDIA").put("data", bytes)))
    assertEquals(1, sink?.played?.size)
    assertEquals(1L, coordinator.received())
  }

  @Test
  fun `el frame que captura el motor sale como CALL_MEDIA al transporte`() {
    var sink: FakeSink? = null
    val sent = mutableListOf<JSONObject>()
    val coordinator = CallMediaCoordinator(createEngine = { send -> FakeSink(send).also { sink = it } })
    coordinator.start(session()) { packet -> sent.add(packet) }

    // El motor entrega el frame ya codificado y el coordinador lo envuelve.
    sink?.sendFrame?.invoke(frameData(1))

    assertEquals(1, sent.size)
    assertEquals("CALL_MEDIA", sent[0].optString("type"))
    assertEquals("call-1", sent[0].optString("callId"))
    // Lo que va en `data` es el sobre binario, no el frame pelado.
    assertTrue(CallMediaPacket.isBinary(sent[0].opt("data") as ByteArray))
  }

  @Test
  fun `el estado arranca cuando se abre el motor`() {
    val state = CallAudioState()
    assertFalse(state.isLive())
    assertTrue(state.start())
    assertTrue(state.isLive())
    state.stop()
    assertFalse(state.isLive())
  }

  @Test
  fun `no se reproduce media de una llamada sin conectar`() {
    // Reproducir antes de que alguien acepte haría sonar al usuario una llamada que no ha cogido.
    val coordinator = CallMediaCoordinator()
    val packet = JSONObject().put("type", "CALL_MEDIA").put("callId", "call-1").put("data", frameData())
    assertFalse(coordinator.onInboundPacket(packet))
  }

  @Test
  fun `un paquete de media sin datos no rompe nada`() {
    val coordinator = CallMediaCoordinator()
    assertFalse(coordinator.onInboundPacket(JSONObject().put("type", "CALL_MEDIA")))
    assertFalse(coordinator.onInboundPacket(JSONObject().put("type", "CALL_MEDIA").put("data", "no-base64")))
    assertFalse(coordinator.onInboundPacket(JSONObject().put("type", "CALL_MEDIA").put("data", ByteArray(0))))
  }

  @Test
  fun `el resumen dice si la media esta activa`() {
    val coordinator = CallMediaCoordinator()
    assertTrue(coordinator.summary().contains("parada"))
    coordinator.stop()
    assertFalse(coordinator.live())
  }

  @Test
  fun `un frame fuera de orden no se reproduce`() {
    val state = CallAudioState()
    state.start()
    assertTrue(state.onFrameReceived(5))
    assertFalse(state.onFrameReceived(4))
    assertFalse(state.onFrameReceived(5))
    assertEquals(1, state.framesReceived())
    assertEquals(2, state.droppedOutOfOrder())
  }
}
