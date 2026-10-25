package app.upeer.transport

import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge

internal class ProtocolEgress(
  private val address: () -> String,
  private val port: Long = CONTROL_PORT,
) {
  fun selfAddress(): String = address()

  fun send(activeBridge: Bridge, destination: String, packetJSON: String, label: String): Boolean {
    var connectionID = 0
    return try {
      connectionID = activeBridge.dialTCP(destination, port)
      activeBridge.writeFrame(connectionID, packetJSON.toByteArray(Charsets.UTF_8))
      true
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo enviar el paquete $label a $destination: ${message(cause)}")
      false
    } finally {
      if (connectionID != 0) {
        try {
          activeBridge.closeConnection(connectionID)
        } catch (cause: Exception) {
          Log.w(TAG, "No se pudo cerrar la conexión de $label: ${message(cause)}")
        }
      }
    }
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  companion object {
    private const val TAG = "UPeerTransport"
    private const val CONTROL_PORT = 50005L
  }
}
