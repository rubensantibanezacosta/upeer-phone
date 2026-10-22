package app.upeer.transport.dht

import android.util.Log
import org.json.JSONObject

internal class DhtUpdateService(
  private val acceptance: DhtUpdateAcceptanceStore,
  private val registryFor: (String) -> String?,
  private val onAccepted: (DhtAcceptedLocation) -> Unit,
  private val onMissingRegistry: (String) -> Unit = { account ->
    Log.w(TAG, "DHT_UPDATE pendiente de registro para $account")
  },
  private val log: (String) -> Unit = { message -> Log.w(TAG, message) },
) {
  fun handle(senderUpeerId: String, publicKeyHex: String, locationBlock: JSONObject, sourceAddress: String): Boolean {
    val account = senderUpeerId.trim().lowercase()
    if (!DhtLocation.isHex(account, 32) || !DhtLocation.isHex(publicKeyHex, 32)) return false
    val registry = runCatching { registryFor(account) }.getOrNull()
    if (registry.isNullOrBlank()) {
      log("DHT_UPDATE sin registro de cuenta para $account desde $sourceAddress")
      onMissingRegistry(account)
      return false
    }
    val accepted = acceptance.accept(
      locationBlock.toString(),
      account,
      publicKeyHex,
      registry,
      locationBlock.optString("powProof").takeIf(String::isNotBlank),
    ) ?: return false
    if (!accepted.changed) return true
    onAccepted(accepted.location)
    log(
      "DHT_UPDATE aplicado de $account device=${accepted.location.deviceId} " +
        "seq=${accepted.location.dhtSeq} address=${accepted.location.primaryAddress}",
    )
    return true
  }

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
