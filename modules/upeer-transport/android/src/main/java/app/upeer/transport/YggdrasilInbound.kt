package app.upeer.transport

import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONObject
import java.util.concurrent.Semaphore

internal class YggdrasilInbound(
  private val port: Int,
  private val onFrame: (Bridge, ByteArray, String) -> String,
) {
  private data class State(
    val listening: Boolean = false,
    val accepted: Long = 0,
    val frames: Long = 0,
    val valid: Long = 0,
    val invalid: Long = 0,
    val limited: Long = 0,
    val lastType: String = "",
    val lastSenderUpeerID: String = "",
    val lastSenderYggAddress: String = "",
    val lastError: String = "",
    val lastFrameAt: Long = 0,
  )

  private val lock = Any()
  private val connectionSlots = Semaphore(MAX_CONNECTIONS)
  private var bridge: Bridge? = null
  private var state = State()
  private var thread: Thread? = null

  fun start(activeBridge: Bridge) {
    synchronized(lock) {
      check(bridge == null) { "Inbound transport already started" }
      bridge = activeBridge
    }
    val listenerID = try {
      activeBridge.listenTCP(port.toLong())
    } catch (cause: Exception) {
      synchronized(lock) {
        bridge = null
        state = State(lastError = message(cause))
      }
      throw cause
    }
    synchronized(lock) {
      state = State(listening = true)
    }
    val acceptThread = Thread({ acceptConnections(activeBridge, listenerID) }, "upeer-inbound")
    acceptThread.isDaemon = true
    synchronized(lock) { thread = acceptThread }
    acceptThread.start()
    Log.i(TAG, "Escuchando frames de control en el puerto $port")
  }

  fun stop() {
    val activeThread = synchronized(lock) {
      val current = thread
      thread = null
      bridge = null
      state = State()
      current
    }
    activeThread?.interrupt()
  }

  fun statusJSON(): String = synchronized(lock) {
    JSONObject()
      .put("listening", state.listening)
      .put("port", port)
      .put("accepted", state.accepted)
      .put("frames", state.frames)
      .put("valid", state.valid)
      .put("invalid", state.invalid)
      .put("limited", state.limited)
      .put("lastType", state.lastType)
      .put("lastSenderUpeerId", state.lastSenderUpeerID)
      .put("lastSenderYggAddress", state.lastSenderYggAddress)
      .put("lastError", state.lastError)
      .put("lastFrameAt", state.lastFrameAt)
      .toString()
  }

  private fun acceptConnections(activeBridge: Bridge, listenerID: Int) {
    while (isActive(activeBridge)) {
      val connectionID = try {
        activeBridge.acceptTCP(listenerID, ACCEPT_TIMEOUT_MILLIS.toLong())
      } catch (cause: Exception) {
        if (!isActive(activeBridge)) return
        recordError("aceptar conexión entrante", cause)
        pause()
        continue
      }
      if (connectionID == 0) continue
      if (!connectionSlots.tryAcquire()) {
        Log.w(TAG, "Límite de conexiones entrantes alcanzado; se cierra $connectionID")
        closeConnection(activeBridge, connectionID)
        continue
      }
      synchronized(lock) { state = state.copy(accepted = state.accepted + 1) }
      val sourceAddress = connectionSource(activeBridge, connectionID)
      val reader = Thread({ readFrames(activeBridge, connectionID, sourceAddress) }, "upeer-inbound-$connectionID")
      reader.isDaemon = true
      try {
        reader.start()
      } catch (cause: Exception) {
        connectionSlots.release()
        closeConnection(activeBridge, connectionID)
        recordError("iniciar lector entrante", cause)
      }
    }
  }

  private fun readFrames(activeBridge: Bridge, connectionID: Int, sourceAddress: String) {
    try {
      while (isActive(activeBridge)) {
        val payload = activeBridge.readFrame(connectionID, 0L)
        synchronized(lock) {
          state = state.copy(frames = state.frames + 1, lastFrameAt = System.currentTimeMillis())
        }
        try {
          val metadataJSON = onFrame(activeBridge, payload, sourceAddress)
          val metadata = JSONObject(metadataJSON)
          if (metadata.optBoolean("rateLimited")) {
            synchronized(lock) {
              state = state.copy(
                limited = state.limited + 1,
                lastType = metadata.optString("type"),
                lastSenderUpeerID = metadata.optString("senderUpeerId"),
                lastSenderYggAddress = metadata.optString("senderYggAddress"),
                lastError = "rate limited",
              )
            }
            Log.w(TAG, "Frame limitado: ${metadata.optString("type")}")
            continue
          }
          synchronized(lock) {
            state = state.copy(
              valid = state.valid + 1,
              lastType = metadata.optString("type"),
              lastSenderUpeerID = metadata.optString("senderUpeerId"),
              lastSenderYggAddress = metadata.optString("senderYggAddress"),
              lastError = "",
            )
          }
          Log.i(TAG, "Frame de control validado: ${metadata.optString("type")}")
        } catch (cause: Exception) {
          synchronized(lock) {
            state = state.copy(invalid = state.invalid + 1, lastError = message(cause))
          }
          Log.w(TAG, "Frame de control rechazado: ${message(cause)}")
        }
      }
    } catch (cause: Exception) {
      if (isActive(activeBridge) && !isCleanClose(cause)) recordError("leer conexión entrante", cause)
      else Log.i(TAG, "Conexión entrante cerrada")
    } finally {
      closeConnection(activeBridge, connectionID)
      connectionSlots.release()
    }
  }

  private fun connectionSource(activeBridge: Bridge, connectionID: Int): String = try {
    activeBridge.connectionAddress(connectionID)
  } catch (cause: Exception) {
    Log.w(TAG, "No se pudo leer la dirección de la conexión $connectionID: ${message(cause)}")
    "connection:$connectionID"
  }

  private fun closeConnection(activeBridge: Bridge, connectionID: Int) {
    try {
      activeBridge.closeConnection(connectionID)
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo cerrar la conexión entrante $connectionID: ${message(cause)}")
    }
  }

  private fun isActive(candidate: Bridge): Boolean = synchronized(lock) { bridge === candidate }

  private fun recordError(operation: String, cause: Exception) {
    synchronized(lock) { state = state.copy(lastError = message(cause)) }
    Log.w(TAG, "No se pudo $operation: ${message(cause)}", cause)
  }

  private fun pause() {
    try {
      Thread.sleep(RETRY_DELAY_MILLIS)
    } catch (cause: InterruptedException) {
      Thread.currentThread().interrupt()
      Log.w(TAG, "Bucle de aceptación interrumpido: ${message(cause)}")
    }
  }

  private fun isCleanClose(cause: Throwable): Boolean {
    val detail = message(cause).lowercase()
    return detail == "eof" || detail.contains("closed") || detail.contains("reset by peer")
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val ACCEPT_TIMEOUT_MILLIS = 1000
    private const val RETRY_DELAY_MILLIS = 250L
    private const val MAX_CONNECTIONS = 32
  }
}
