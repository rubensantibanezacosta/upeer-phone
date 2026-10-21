package app.upeer.transport

import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONObject

internal class YggdrasilHandshake(
  private val identity: YggdrasilIdentity,
  private val signedPreKeys: SignedPreKeyStore,
) {
  fun respond(activeBridge: Bridge, metadataJSON: String) {
    val metadata = try {
      JSONObject(metadataJSON)
    } catch (cause: Exception) {
      Log.w(TAG, "Metadatos de handshake inválidos: ${message(cause)}")
      return
    }
    if (metadata.optString("type") != "HANDSHAKE_REQ") return
    val target = metadata.optString("senderYggAddress")
    if (target.isBlank()) return
    val currentIdentity = identity.current()
    if (currentIdentity == null) {
      Log.w(TAG, "No se puede aceptar el handshake sin identidad uPeer")
      return
    }
    val packet = try {
      currentIdentity.buildHandshakeAccept(target, signedPreKeys.currentJSON(currentIdentity), "")
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo construir HANDSHAKE_ACCEPT: ${message(cause)}")
      return
    }
    var connectionID = 0
    try {
      connectionID = activeBridge.dialTCP(target, PORT)
      activeBridge.writeFrame(connectionID, packet.toByteArray(Charsets.UTF_8))
      Log.i(TAG, "HANDSHAKE_ACCEPT enviado a $target")
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo enviar HANDSHAKE_ACCEPT a $target: ${message(cause)}")
    } finally {
      if (connectionID != 0) {
        try {
          activeBridge.closeConnection(connectionID)
        } catch (cause: Exception) {
          Log.w(TAG, "No se pudo cerrar la conexión de handshake: ${message(cause)}")
        }
      }
    }
  }

  fun clear() = signedPreKeys.clear()

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val PORT = 50005L
  }
}
