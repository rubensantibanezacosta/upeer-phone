package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Canal UDP de la media de una llamada.
 *
 * El escritorio manda `CALL_MEDIA` por UDP al puerto 50006 (`callMediaUdp.ts`). El móvil no tenía
 * ningún socket UDP, así que el audio habría llegado a un puerto cerrado y se habría perdido en
 * silencio. El puente Go ya expone el UDP, así que aquí se prueba el canal con un puente falso: abrir,
 * mandar, fragmentar y cerrar sin dejar nada suelto.
 */
class CallMediaChannelTest {

  /** Puente falso: apunta lo que se le pide y devuelve lo que se le diga. */
  private class FakeBridge : CallMediaChannel.BridgeBindings {
    var nextId = 1
    val dialed = mutableListOf<Pair<String, Int>>()
    val written = mutableListOf<Pair<Int, Int>>()
    val closed = mutableListOf<Int>()
    var failDial = false
    var pending: ArrayDeque<ByteArray> = ArrayDeque()

    override fun dialUDP(address: String, port: Int): Int {
      if (failDial) error("sin red")
      dialed.add(address to port)
      return nextId++
    }

    override fun writeDatagram(connectionID: Int, payload: ByteArray) {
      written.add(connectionID to payload.size)
    }

    override fun read(connectionID: Int, maxBytes: Int, timeoutMillis: Int): ByteArray =
      pending.removeFirstOrNull() ?: ByteArray(0)

    override fun closeConnection(connectionID: Int) {
      closed.add(connectionID)
    }
  }

  private val DESKTOP = "200:7bba:1d01:9bb7:78bc:beae:f64a:6d59"

  private fun channel(bridge: FakeBridge, received: MutableList<Pair<String, ByteArray>> = mutableListOf()) =
    CallMediaChannel(bridge = { bridge }, onPacket = { address, packet -> received.add(address to packet) })

  @Test
  fun `abre el canal al puerto de media del escritorio`() {
    val bridge = FakeBridge()
    val channel = channel(bridge)

    assertTrue(channel.connect(DESKTOP))
    assertTrue(channel.isOpen())
    assertEquals(listOf(DESKTOP to CallMediaChannel.MEDIA_PORT), bridge.dialed)
    channel.close()
  }

  @Test
  fun `no abre el canal dos veces al mismo dispositivo`() {
    val bridge = FakeBridge()
    val channel = channel(bridge)

    channel.connect(DESKTOP)
    channel.connect(DESKTOP)

    assertEquals(1, bridge.dialed.size)
    channel.close()
  }

  @Test
  fun `una direccion invalida no abre nada`() {
    val bridge = FakeBridge()
    val channel = channel(bridge)

    assertFalse(channel.connect("no es una direccion"))
    assertTrue(bridge.dialed.isEmpty())
    assertFalse(channel.isOpen())
  }

  @Test
  fun `si el puente falla al abrir no se queda abierto`() {
    val bridge = FakeBridge().apply { failDial = true }
    val channel = channel(bridge)

    assertFalse(channel.connect(DESKTOP))
    assertFalse(channel.isOpen())
  }

  @Test
  fun `manda un frame pequeno en un solo datagrama`() {
    val bridge = FakeBridge()
    val channel = channel(bridge)
    channel.connect(DESKTOP)

    // Un frame de audio de verdad ronda las decenas o cientos de bytes: cabe entero.
    val frame = ByteArray(200) { it.toByte() }
    assertTrue(channel.send(DESKTOP, frame))

    assertEquals(1, bridge.written.size)
    channel.close()
  }

  @Test
  fun `un paquete grande se parte en varios datagramas`() {
    val bridge = FakeBridge()
    val channel = channel(bridge)
    channel.connect(DESKTOP)

    // Sin fragmentar, esto no cabría en un datagrama de 1200 B.
    val packet = ByteArray(2500) { (it % 256).toByte() }
    assertTrue(channel.send(DESKTOP, packet))

    assertEquals(3, bridge.written.size)
    assertTrue("ningún datagrama puede pasar del MTU", bridge.written.all { it.second <= CallMediaChannel.MAX_DATAGRAM_BYTES })
    channel.close()
  }

  @Test
  fun `no manda si el canal no esta abierto`() {
    val bridge = FakeBridge()
    val channel = channel(bridge)

    assertFalse(channel.send(DESKTOP, ByteArray(10)))
    assertTrue(bridge.written.isEmpty())
  }

  @Test
  fun `cerrar es idempotente y suelta las conexiones`() {
    val bridge = FakeBridge()
    val channel = channel(bridge)
    channel.connect(DESKTOP)

    channel.close()
    channel.close()
    channel.close()

    // Una conexión abierta no puede quedarse colgando: el puente la cuenta como recurso vivo.
    assertEquals(1, bridge.closed.size)
    assertFalse(channel.isOpen())
  }

  @Test
  fun `cerrar dos dispositivos cierra las dos conexiones`() {
    val bridge = FakeBridge()
    val channel = channel(bridge)
    channel.connect(DESKTOP)
    channel.connect("200:a15e:1111:2222:3333:4444:5555:723a")

    channel.close()

    assertEquals(2, bridge.closed.size)
  }

  @Test
  fun `el resumen dice lo que ha pasado`() {
    val bridge = FakeBridge()
    val channel = channel(bridge)
    channel.connect(DESKTOP)
    channel.send(DESKTOP, ByteArray(100))

    val summary = channel.summary()
    assertTrue(summary.contains("abierto"))
    assertTrue(summary.contains("dispositivos=1"))

    channel.close()
    assertTrue(channel.summary().contains("cerrado"))
  }

  @Test
  fun `el puerto de media coincide con el del escritorio`() {
    // `CALL_MEDIA_UDP_PORT` en server/constants.ts. Si cambia allí, esto avisa.
    assertEquals(50006, CallMediaChannel.MEDIA_PORT)
  }
}
