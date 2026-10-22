package app.upeer.transport

import app.upeer.transportbridge.transportbridge.Bridge

internal class YggdrasilBridgeBindings(
  private val bridge: () -> Bridge,
  private val inbound: YggdrasilInbound,
) {
  fun peerStatusJSON(): String = bridge().peerStatusJSON()

  fun peerPoolJSON(): String = bridge().peerPoolJSON()

  fun connectedPeerCount(): Long = bridge().connectedPeerCount()

  fun retryPeers() = bridge().retryPeers()

  fun dialTCP(address: String, port: Int): Int = bridge().dialTCP(address, port.toLong())

  fun dialUDP(address: String, port: Int): Int = bridge().dialUDP(address, port.toLong())

  fun write(connectionID: Int, payload: ByteArray) = bridge().write(connectionID, payload)

  fun writeDatagram(connectionID: Int, payload: ByteArray) = bridge().writeDatagram(connectionID, payload)

  fun closeConnection(connectionID: Int) = bridge().closeConnection(connectionID)

  fun read(connectionID: Int, maxBytes: Int, timeoutMillis: Int): ByteArray =
    bridge().read(connectionID, maxBytes.toLong(), timeoutMillis.toLong())

  fun listenTCP(port: Int): Int = bridge().listenTCP(port.toLong())

  fun acceptTCP(listenerID: Int, timeoutMillis: Int): Int = bridge().acceptTCP(listenerID, timeoutMillis.toLong())

  fun closeListener(listenerID: Int) = bridge().closeListener(listenerID)

  fun readFrame(connectionID: Int, timeoutMillis: Int): ByteArray = bridge().readFrame(connectionID, timeoutMillis.toLong())

  fun writeFrame(connectionID: Int, payload: ByteArray) = bridge().writeFrame(connectionID, payload)

  fun validateControlFrame(payload: ByteArray): String = bridge().validateControlFrame(payload)

  fun inboundStatusJSON(): String = inbound.statusJSON()
}
