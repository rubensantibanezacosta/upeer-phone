package app.upeer.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge

internal class DhtSocketTransport(
  private val context: Context,
  private val bridge: () -> Bridge?,
  private val onNetworkChanged: () -> Unit,
) {
  fun transmit(address: String, packetJSON: String) {
    val activeBridge = bridge() ?: return
    var connectionID = 0
    try {
      connectionID = activeBridge.dialTCP(address, DHT_PORT)
      activeBridge.writeFrame(connectionID, packetJSON.toByteArray(Charsets.UTF_8))
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo enviar un paquete DHT a $address: ${message(cause)}")
    } finally {
      if (connectionID != 0) {
        runCatching { activeBridge.closeConnection(connectionID) }.onFailure { cause ->
          Log.w(TAG, "No se pudo cerrar la conexión DHT: ${message(cause)}")
        }
      }
    }
  }

  fun register(): ConnectivityManager.NetworkCallback? {
    val manager = connectivity() ?: return null
    val callback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) = onNetworkChanged()

      override fun onLost(network: Network) = onNetworkChanged()
    }
    return runCatching {
      manager.registerDefaultNetworkCallback(callback)
      callback
    }.getOrElse { cause ->
      Log.w(TAG, "No se pudo observar la red para DHT", cause)
      null
    }
  }

  fun unregister(callback: ConnectivityManager.NetworkCallback) {
    runCatching { connectivity()?.unregisterNetworkCallback(callback) }.onFailure { cause ->
      Log.w(TAG, "No se pudo desregistrar el monitor de red DHT", cause)
    }
  }

  private fun connectivity(): ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val DHT_PORT = 50005L
  }
}
