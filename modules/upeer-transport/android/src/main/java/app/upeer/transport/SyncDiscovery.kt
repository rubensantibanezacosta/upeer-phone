package app.upeer.transport

import android.util.Log
import app.upeer.transport.dht.DhtLocation
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONObject

internal class SyncDiscovery(
  private val identity: YggdrasilIdentity,
  private val deviceSignedPreKeys: DeviceSignedPreKeyStore,
  private val selfAddress: () -> String,
  private val store: (String, String) -> Int,
  private val read: (String) -> Any?,
  private val send: (Bridge, String, String) -> Boolean,
  private val directPeer: () -> String = { "" },
  private val livePeers: () -> Set<String> = { emptySet() },
  private val nowMillis: () -> Long = System::currentTimeMillis,
) {
  @Volatile
  private var lastPublished = ""

  fun publish(): Int {
    val account = identity.current()?.upeerID() ?: return 0
    val device = identity.currentDevice() ?: return 0
    val address = selfAddress().takeIf(String::isNotEmpty) ?: return 0
    val record = SyncRendezvousRecord.build(account, device.deviceId, address, device.certificate, nowMillis())
      ?: return 0
    val key = runCatching { SyncRendezvousKey.key(account, device.deviceId) }.getOrNull() ?: return 0
    if (record.toString() == lastPublished) return 0
    val deliveries = store(key, record.toString())
    if (deliveries > 0) lastPublished = record.toString()
    Log.i(TAG, "Encuentro de sincronización publicado en $deliveries nodos DHT")
    return deliveries
  }

  fun discover(activeBridge: Bridge, registryJSON: String?): List<SyncRendezvous> {
    val account = identity.current()?.upeerID() ?: return emptyList()
    val myDevice = identity.currentDevice()?.deviceId ?: return emptyList()
    val devices = deviceIds(registryJSON, account).filter { it != myDevice }
    val found = mutableListOf<SyncRendezvous>()
    for (deviceId in devices) {
      val key = runCatching { SyncRendezvousKey.key(account, deviceId) }.getOrElse { continue }
      val record = SyncRendezvousRecord.parse(read(key), account, nowMillis()) ?: continue
      found.add(record)
    }
    if (found.isEmpty()) {
      Log.i(TAG, "Ningún dispositivo propio publicado en la DHT (${devices.size} en el registro)")
    }
    val direct = directPeer()
    val live = livePeers()
    val targets = (found.map { it.address } + listOfNotNull(direct.takeIf(String::isNotEmpty)))
      .filter { !isSelfAddress(it) }
      .filter { it !in live }
      .distinct()
    if (targets.isEmpty()) return found
    var greeted = 0
    for (address in targets) {
      val frame = helloFrame()
      if (frame == null) {
        Log.w(TAG, "SYNC_HELLO sin construir: no se puede firmar el saludo de ${myDevice.take(12)}")
        break
      }
      if (send(activeBridge, address, frame)) greeted += 1
    }
    Log.i(TAG, "SYNC_HELLO enviado a $greeted de ${targets.size} dispositivos propios")
    return found
  }

  private fun deviceIds(registryJSON: String?, account: String): List<String> {
    val registry = registryJSON?.takeIf(String::isNotEmpty)?.let { raw ->
      runCatching { JSONObject(raw) }.getOrNull()
    } ?: return emptyList()
    if (!registry.optString("accountUpeerId").equals(account, ignoreCase = true)) return emptyList()
    val devices = registry.optJSONArray("devices") ?: return emptyList()
    val ids = mutableListOf<String>()
    for (index in 0 until devices.length()) {
      val id = devices.optJSONObject(index)?.optString("deviceId").orEmpty().lowercase()
      if (DhtLocation.isHex(id, 32)) ids.add(id)
    }
    return ids.distinct()
  }

  private fun helloFrame(): String? {
    val account = identity.current() ?: run {
      Log.w(TAG, "SYNC_HELLO sin cuenta cargada")
      return null
    }
    val device = identity.currentDevice() ?: run {
      Log.w(TAG, "SYNC_HELLO sin dispositivo enrolado")
      return null
    }
    val address = selfAddress().takeIf(String::isNotEmpty) ?: run {
      Log.w(TAG, "SYNC_HELLO sin dirección propia: el nodo aún no tiene identidad de red")
      return null
    }
    val signedPreKey = runCatching { JSONObject(deviceSignedPreKeys.currentJSON(device)) }.getOrElse { cause ->
      Log.w(TAG, "SYNC_HELLO sin preclave firmada: ${cause.message ?: cause.javaClass.simpleName}")
      return null
    }
    val hello = SyncPackets.hello(
      SyncSender(device.deviceId, device.certificate),
      JSONObject()
        .put("spkPub", signedPreKey.optString("spkPub"))
        .put("spkSig", signedPreKey.optString("spkSig"))
        .put("spkId", signedPreKey.optLong("spkId")),
      ack = false,
      nowMillis = nowMillis(),
    )
    return runCatching {
      device.crypto.buildLinkedControl(device.deviceId, account.upeerID(), address, hello.toString())
    }.getOrElse { cause ->
      Log.w(TAG, "SYNC_HELLO sin firma: ${cause.message ?: cause.javaClass.simpleName}")
      null
    }
  }

  private fun isSelfAddress(candidate: String): Boolean =
    candidate.equals(selfAddress(), ignoreCase = true)

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
