package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallStoreTest {
  private val peer = "b".repeat(64)
  private var clock = 1_000L
  private val ended = mutableListOf<CallSession>()
  private val store = CallStore(now = { clock }, onEnded = { ended.add(it) })

  @Test
  fun `una llamada saliente arranca sonando y sin sesión previa`() {
    val session = store.start(peer, CallKinds.AUDIO, outgoing = true)
    assertNotNull(session)
    assertEquals(CallPhases.OUTGOING, session?.phase)
    assertFalse(session?.cameraEnabled ?: true)
    assertTrue(store.busy())
  }

  @Test
  fun `una videollamada entrante enciende la cámara por defecto`() {
    val session = store.start(peer, CallKinds.VIDEO, outgoing = false)
    assertEquals(CallKinds.VIDEO, session?.kind)
    assertTrue(session?.cameraEnabled ?: false)
  }

  @Test
  fun `no se puede empezar otra llamada con una en curso`() {
    store.start(peer, CallKinds.AUDIO, outgoing = true)
    assertNull(store.start("c".repeat(64), CallKinds.AUDIO, outgoing = false))
  }

  @Test
  fun `aceptar conecta y fija el instante de conexión`() {
    val session = store.start(peer, CallKinds.AUDIO, outgoing = false)!!
    assertTrue(store.accept(session.callId))
    assertEquals(CallPhases.NEGOTIATING, store.active()?.phase)
    clock = 5_000L
    assertTrue(store.connect(session.callId))
    assertEquals(CallPhases.CONNECTED, store.active()?.phase)
    assertEquals(5_000L, store.active()?.connectedAt)
  }

  @Test
  fun `terminar escribe el evento una sola vez`() {
    val session = store.start(peer, CallKinds.AUDIO, outgoing = true)!!
    assertTrue(store.end(session.callId, CallEndReasons.NO_ANSWER))
    assertEquals(1, ended.size)
    assertFalse(store.end(session.callId, CallEndReasons.NO_ANSWER))
    assertEquals(1, ended.size)
    assertEquals(CallEndReasons.NO_ANSWER, ended.first().endReason)
  }

  @Test
  fun `el silencio solo se cambia en una llamada viva`() {
    val session = store.start(peer, CallKinds.AUDIO, outgoing = true)!!
    assertTrue(store.setMuted(session.callId, true))
    assertTrue(store.active()?.muted ?: false)
    store.end(session.callId, CallEndReasons.LOCAL_HANGUP)
    assertFalse(store.setMuted(session.callId, false))
  }

  @Test
  fun `la cámara no se toca en una llamada de voz`() {
    val session = store.start(peer, CallKinds.AUDIO, outgoing = true)!!
    assertFalse(store.setCamera(session.callId, true))
  }

  @Test
  fun `el estado se publica como json y desaparece tras la ventana de cierre`() {
    assertEquals("{}", store.snapshotJSON())
    val session = store.start(peer, CallKinds.VIDEO, outgoing = false)!!
    val live = JSONObject(store.snapshotJSON())
    assertEquals(session.callId, live.optString("callId"))
    assertEquals("incoming", live.optString("direction"))
    store.end(session.callId, CallEndReasons.REJECTED)
    assertEquals("ended", JSONObject(store.snapshotJSON()).optString("phase"))
    clock += 5_000L
    assertEquals("{}", store.snapshotJSON())
  }
}

class CallPacketsTest {
  @Test
  fun `la oferta lleva tipo y modo`() {
    val packet = CallPackets.offer("abc", CallKinds.VIDEO, 42L)
    assertEquals("CALL_OFFER", packet.optString("type"))
    assertEquals("video", packet.optString("kind"))
    assertEquals(42L, packet.optLong("timestamp"))
    assertTrue(CallPackets.valid(packet))
  }

  @Test
  fun `una oferta sin modo válido se rechaza`() {
    val packet = JSONObject().put("type", "CALL_OFFER").put("callId", "abc").put("kind", "pantalla")
    assertFalse(CallPackets.valid(packet))
  }

  @Test
  fun `un callId vacío invalida el paquete`() {
    assertFalse(CallPackets.valid(JSONObject().put("type", "CALL_END").put("callId", "")))
  }

  @Test
  fun `la actualización de media copia los dos indicadores`() {
    val packet = CallPackets.mediaUpdate("abc", muted = true, cameraEnabled = false, timestamp = 7L)
    assertTrue(packet.optBoolean("muted"))
    assertFalse(packet.optBoolean("cameraEnabled"))
    assertTrue(CallPackets.valid(packet))
  }

  @Test
  fun `los miembros de grupo se normalizan a minúsculas y sin vacíos`() {
    val packet = JSONObject().put("type", "CALL_OFFER").put("callId", "abc").put("kind", "audio")
      .put("groupMembers", org.json.JSONArray(listOf("AA", "", "bb")))
    assertEquals(listOf("aa", "bb"), CallPackets.groupMembers(packet))
  }
}
