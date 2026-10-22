package app.upeer.transport.dht

import android.util.Log
import app.upeer.transportbridge.transportbridge.Transportbridge
import org.json.JSONObject

internal class DhtLocationWorker(
  private val upeerId: () -> String?,
  private val publicKeyHex: () -> String?,
  private val buildBlock: () -> String?,
  private val store: (String, JSONObject, String) -> Int,
  private val find: (String) -> Any?,
  private val publicKeyFor: (String) -> String?,
  private val registryFor: (String) -> String?,
  private val nowMillis: () -> Long = System::currentTimeMillis,
  private val verify: (String, String, String, Long) -> Boolean = { block, publicKey, registry, now ->
    Transportbridge.verifyDeviceLocationBlock(block, publicKey, registry, now)
  },
  private val sequenceReady: (String) -> Boolean = { true },
  private val adoptSequence: (String, Long) -> Unit = { _, _ -> },
  private val warn: (String) -> Unit = { message -> Log.w(TAG, message) },
  private val info: (String) -> Unit = { message -> Log.i(TAG, message) },
) {
  @Volatile private var lastPublished = ""
  private var lastSeq = -1L
  private var lastAddresses: List<String> = emptyList()
  private var recoveryAttempted = false
  private val cache = DhtLocationCache(nowMillis)

  val published: Boolean get() = lastPublished.isNotEmpty()

  fun lastVerifiedBlock(): String? = lastPublished.takeIf(String::isNotEmpty)

  fun cacheAccepted(accountUpeerId: String, blockJSON: String, expiresAt: Long) {
    val account = accountUpeerId.trim().lowercase()
    if (!DhtLocation.isHex(account, 32)) return
    cache.put(account, blockJSON, expiresAt)
  }

  fun publish(force: Boolean = false): Boolean {
    val account = upeerId() ?: return false
    val publicKey = publicKeyHex() ?: return false
    if (!ensureSequence(account, publicKey)) return false
    val block = runCatching { buildBlock() }.getOrNull()?.takeIf(String::isNotEmpty) ?: return false
    val now = nowMillis()
    val verified = DhtContactLocation.verifyOne(block, account, publicKey, "", now, verify)
    if (verified == null) {
      warn("El bloque de ubicación no incluye un certificado de dispositivo válido; no se publica")
      return false
    }
    val unchanged = verified.dhtSeq == lastSeq && verified.addresses == lastAddresses
    if (!force && unchanged && verified.expiresAt - now > REPUBLISH_BEFORE_MS) return true
    val deliveries = runCatching {
      store(account, JSONObject(block), account)
    }.getOrElse { cause ->
      warn("No se pudo publicar la ubicación en DHT: ${message(cause)}")
      return false
    }
    lastPublished = block
    lastSeq = verified.dhtSeq
    lastAddresses = verified.addresses
    info("Ubicación de dispositivo publicada en $deliveries nodos DHT")
    return true
  }

  fun refresh(accountUpeerId: String): String? {
    val account = accountUpeerId.trim().lowercase()
    if (!DhtLocation.isHex(account, 32)) return null
    cache.get(account)?.let { return it }
    val publicKey = publicKeyFor(account) ?: return null
    val registry = registryFor(account) ?: return null
    val value = runCatching { find(account) }.getOrElse { cause ->
      warn("No se pudo consultar la ubicación DHT de $account: ${message(cause)}")
      return null
    } ?: return null
    val verified = DhtContactLocation.select(value, account, publicKey, registry, nowMillis(), verify) ?: return null
    cache.put(account, verified.blockJSON, verified.expiresAt)
    info("Ubicación DHT verificada de $account (dispositivo ${verified.deviceId}, seq ${verified.dhtSeq})")
    return verified.blockJSON
  }

  fun reset() {
    lastPublished = ""
    lastSeq = -1L
    lastAddresses = emptyList()
    recoveryAttempted = false
    cache.clear()
  }

  private fun ensureSequence(account: String, publicKey: String): Boolean {
    if (sequenceReady(account)) return true
    if (recoveryAttempted) return true
    val value = try {
      find(account)
    } catch (cause: Exception) {
      warn("No se pudo recuperar la secuencia DHT publicada: ${message(cause)}")
      return false
    }
    if (value == null) {
      recoveryAttempted = true
      return true
    }
    val registry = registryFor(account) ?: return false
    val verified = DhtContactLocation.select(value, account, publicKey, registry, nowMillis(), verify)
    if (verified == null) {
      warn("La ubicación DHT previa no se pudo verificar; no se publica una secuencia nueva")
      return false
    }
    val adopted = runCatching {
      adoptSequence(account, verified.dhtSeq)
    }.onFailure { cause ->
      warn("No se pudo persistir la secuencia DHT recuperada: ${message(cause)}")
    }.isSuccess
    if (!adopted) return false
    recoveryAttempted = true
    info("Secuencia DHT recuperada sin estado local: ${verified.dhtSeq} (dispositivo ${verified.deviceId})")
    return true
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val REPUBLISH_BEFORE_MS = 24L * 60L * 60L * 1000L
  }
}
