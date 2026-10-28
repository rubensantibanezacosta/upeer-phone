package app.upeer.transport

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CallInboundTest {
  private val sent = mutableListOf<Triple<String, JSONObject, String>>()
  private val incoming = mutableListOf<CallSession>()
  private var clock = 1_000L
  private lateinit var store: CallStore
  private lateinit var inbound: CallInbound

  private val peer = "a60cc35ee3d4e106f7563d9ba4c2deaad4dc0596ded6acdab53585fba354948b"

  @Before
  fun setUp() {
    store = CallStore(now = { clock })
    inbound = CallInbound(
      store = store,
      send = { to, packet, type -> sent += Triple(to, packet, type) },
      onIncoming = { incoming += it },
      now = { clock },
    )
  }

  @After
  fun tearDown() {
    store.resetForTests()
  }

  private fun offerOf(callId: String, kind: String = CallKinds.AUDIO): JSONObject =
    CallPackets.offer(callId, kind, clock)

  @Test
  fun `offer inicia sesion entrante y responde ring`() {
    inbound.handle(offerOf("call-1"), peer, CallPackets.OFFER)

    val session = store.active()
    assertEquals("call-1", session?.callId)
    assertEquals(peer, session?.peerUpeerId)
    assertEquals(false, session?.outgoing)
    assertEquals(CallPhases.INCOMING, session?.phase)
    assertEquals(listOf(CallPackets.RING), sent.map { it.third })
    assertEquals("call-1", sent.first().second.optString("callId"))
    assertEquals(1, incoming.size)
  }

  @Test
  fun `offer con llamada en curso responde busy sin reemplazarla`() {
    inbound.handle(offerOf("call-1"), peer, CallPackets.OFFER)
    inbound.handle(offerOf("call-2"), peer, CallPackets.OFFER)

    assertEquals("call-1", store.active()?.callId)
    assertEquals(listOf(CallPackets.RING, CallPackets.BUSY), sent.map { it.third })
    assertEquals("call-2", sent.last().second.optString("callId"))
    assertEquals(1, incoming.size)
  }

  @Test
  fun `offer de grupo se ignora`() {
    val groupOffer = offerOf("call-1").put("groupMembers", org.json.JSONArray().put(peer).put("otro"))
    inbound.handle(groupOffer, peer, CallPackets.OFFER)

    assertNull(store.active())
    assertTrue(sent.isEmpty())
    assertTrue(incoming.isEmpty())
  }

  @Test
  fun `paquete con callId invalido se descarta`() {
    inbound.handle(offerOf("../etc/passwd"), peer, CallPackets.OFFER)

    assertNull(store.active())
    assertTrue(sent.isEmpty())
  }

  @Test
  fun `accept de una llamada saliente la deja conectada`() {
    store.start(peer, CallKinds.AUDIO, outgoing = true, callId = "call-1")
    clock = 2_000L
    inbound.handle(CallPackets.accept("call-1", clock), peer, CallPackets.ACCEPT)

    assertEquals(CallPhases.CONNECTED, store.active()?.phase)
    assertEquals(2_000L, store.active()?.connectedAt)
  }

  @Test
  fun `accept de una llamada que no es saliente no conecta nada`() {
    inbound.handle(offerOf("call-1"), peer, CallPackets.OFFER)
    inbound.handle(CallPackets.accept("call-1", clock), peer, CallPackets.ACCEPT)

    assertEquals(CallPhases.INCOMING, store.active()?.phase)
  }

  @Test
  fun `reject cierra la llamada con motivo rechazada`() {
    store.start(peer, CallKinds.VIDEO, outgoing = true, callId = "call-1")
    inbound.handle(CallPackets.reject("call-1", clock), peer, CallPackets.REJECT)

    val session = store.active()
    assertNull(session)
    val snapshot = JSONObject(store.snapshotJSON())
    assertEquals(CallPhases.ENDED, snapshot.optString("phase"))
    assertEquals(CallEndReasons.REJECTED, snapshot.optString("endReason"))
  }

  @Test
  fun `end remoto cierra la llamada conectada`() {
    store.start(peer, CallKinds.AUDIO, outgoing = true, callId = "call-1")
    inbound.handle(CallPackets.accept("call-1", clock), peer, CallPackets.ACCEPT)
    clock = 3_500L
    inbound.handle(CallPackets.end("call-1", clock), peer, CallPackets.END)

    val snapshot = JSONObject(store.snapshotJSON())
    assertEquals(CallPhases.ENDED, snapshot.optString("phase"))
    assertEquals(CallEndReasons.REMOTE_END, snapshot.optString("endReason"))
  }

  @Test
  fun `media update y media no alteran el estado`() {
    store.start(peer, CallKinds.VIDEO, outgoing = true, callId = "call-1")
    inbound.handle(CallPackets.mediaUpdate("call-1", muted = true, cameraEnabled = false, timestamp = clock), peer, CallPackets.MEDIA_UPDATE)
    val media = JSONObject().put("type", CallPackets.MEDIA).put("callId", "call-1").put("timestamp", clock)
    inbound.handle(media, peer, CallPackets.MEDIA)

    assertEquals(CallPhases.OUTGOING, store.active()?.phase)
    assertEquals(false, store.active()?.muted)
    assertTrue(sent.isEmpty())
  }
}
