package app.upeer.transport

import android.content.Context
import android.util.Log
import app.upeer.transportbridge.transportbridge.CryptoIdentity
import org.json.JSONObject

internal class YggdrasilIdentity(context: Context) {
  private val vault = IdentityVault(context)
  private val lock = Any()
  private var identity: CryptoIdentity? = null

  init {
    load()
  }

  fun set(secretKey: ByteArray) {
    val nextIdentity = CryptoIdentity(secretKey)
    vault.save(secretKey)
    synchronized(lock) { identity = nextIdentity }
    Log.i(TAG, "Identidad uPeer provisionada en el servicio")
  }

  fun clear() {
    vault.clear()
    synchronized(lock) { identity = null }
    Log.i(TAG, "Identidad uPeer eliminada del servicio")
  }

  fun current(): CryptoIdentity? = synchronized(lock) { identity }

  fun statusJSON(): String = synchronized(lock) {
    val activeIdentity = identity
    if (activeIdentity == null) {
      JSONObject().put("ready", false).toString()
    } else {
      JSONObject()
        .put("ready", true)
        .put("publicKey", activeIdentity.publicKeyHex())
        .put("upeerId", activeIdentity.upeerID())
        .toString()
    }
  }

  private fun load() {
    val secretKey = try {
      vault.load()
    } catch (cause: Exception) {
      Log.e(TAG, "No se pudo leer la identidad uPeer cifrada", cause)
      return
    } ?: return
    try {
      val storedIdentity = CryptoIdentity(secretKey)
      synchronized(lock) { identity = storedIdentity }
      Log.i(TAG, "Identidad uPeer cargada desde Keystore")
    } catch (cause: Exception) {
      Log.e(TAG, "La identidad uPeer almacenada no es válida; se elimina", cause)
      vault.clear()
    } finally {
      secretKey.fill(0)
    }
  }

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
