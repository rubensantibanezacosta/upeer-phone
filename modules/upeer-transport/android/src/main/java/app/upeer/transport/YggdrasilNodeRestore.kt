package app.upeer.transport

import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge
import app.upeer.transportbridge.transportbridge.Transportbridge

internal object YggdrasilNodeRestore {
  fun restoreIfNeeded(
    configStore: YggdrasilConfigStore,
    inbound: YggdrasilInbound,
    protocol: YggdrasilProtocol,
    currentBridge: () -> Bridge?,
    setBridge: (Bridge) -> Unit,
    onRestored: () -> Unit,
    onUnavailable: () -> Unit,
  ) {
    if (currentBridge() != null) return
    val activeBridge = restore(configStore, inbound, protocol) ?: run {
      onUnavailable()
      return
    }
    setBridge(activeBridge)
    onRestored()
  }

  fun stopAfterFailedStart(activeBridge: Bridge, cause: Exception) {
    try {
      activeBridge.stop()
    } catch (cleanupCause: Exception) {
      cause.addSuppressed(cleanupCause)
    }
  }

  fun restore(configStore: YggdrasilConfigStore, inbound: YggdrasilInbound, protocol: YggdrasilProtocol): Bridge? {
    val config = try {
      configStore.load()
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo cargar la configuración del nodo para restaurarlo", cause)
      return null
    } ?: return null
    val activeBridge = Transportbridge.newBridge()
    try {
      activeBridge.enablePeerManager()
      activeBridge.start(config)
      inbound.start(activeBridge)
      protocol.start(activeBridge)
      return activeBridge
    } catch (cause: Exception) {
      protocol.stop()
      inbound.stop()
      try {
        activeBridge.stop()
      } catch (cleanupCause: Exception) {
        cause.addSuppressed(cleanupCause)
      }
      Log.e(TAG, "No se pudo restaurar el nodo Yggdrasil", cause)
      return null
    } finally {
      config.fill(0)
    }
  }

  private const val TAG = "UPeerTransport"
}
