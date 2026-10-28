package app.upeer.transport

import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONObject

internal class OutboxLink(
  private val identity: YggdrasilIdentity,
  private val signedPreKeys: SignedPreKeyStore,
  private val deviceSignedPreKeys: DeviceSignedPreKeyStore,
  private val deviceBindings: DeviceMessagingBindings,
  private val state: ProtocolStateStore,
  private val selfAddress: () -> String,
) {
  fun sendPacket(activeBridge: Bridge, address: String, packet: String): Boolean {
    var connectionID = 0
    return try {
      connectionID = activeBridge.dialTCP(address, PORT)
      activeBridge.writeFrame(connectionID, packet.toByteArray(Charsets.UTF_8))
      true
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo enviar paquete a $address: ${message(cause)}")
      false
    } finally {
      if (connectionID != 0) {
        try {
          activeBridge.closeConnection(connectionID)
        } catch (cause: Exception) {
          Log.w(TAG, "No se pudo cerrar la conexión saliente: ${message(cause)}")
        }
      }
    }
  }

  fun sendHandshakeRequest(activeBridge: Bridge, address: String) {
    val currentIdentity = identity.current() ?: return
    try {
      val packet = OutboxPacketFactory.handshakeRequest(
        currentIdentity,
        selfAddress(),
        { signedPreKeys.currentJSON(currentIdentity) },
        identity.currentDevice(),
        deviceSignedPreKeys,
        deviceBindings,
        state.profile(),
      )
      if (sendPacket(activeBridge, address, packet)) Log.i(TAG, "HANDSHAKE_REQ enviado a $address")
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo enviar HANDSHAKE_REQ a $address: ${message(cause)}")
    }
  }

  fun sendDrReset(
    activeBridge: Bridge,
    address: String,
    upeerId: String,
    peer: JSONObject,
    context: String = "direct",
  ): Boolean {
    val currentIdentity = identity.current() ?: return false
    val packet = try {
      val device = deviceBindings.bindingFor(peer, upeerId)
        ?.let { identity.currentDevice() }
        ?.takeIf { it.accountUpeerId == currentIdentity.upeerID() }
      if (device != null) {
        OutboxPacketFactory.drReset(currentIdentity, device, selfAddress(), deviceSignedPreKeys, context)
      } else {
        currentIdentity.buildDrReset(selfAddress(), signedPreKeys.currentJSON(currentIdentity), context)
      }
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo construir DR_RESET para $upeerId: ${message(cause)}")
      return false
    }
    if (!sendPacket(activeBridge, address, packet)) return false
    Log.i(TAG, "DR_RESET enviado a $address")
    return true
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val PORT = 50005L
  }
}
