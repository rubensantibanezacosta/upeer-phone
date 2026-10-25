package app.upeer.transport

import android.util.Log
import app.upeer.transport.vault.VaultChannel
import app.upeer.transport.vault.VaultCustodianCandidate
import app.upeer.transport.vault.VaultDirectory
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONObject

internal class OutboxVault(
  private val identity: YggdrasilIdentity,
  private val state: ProtocolStateStore,
  private val outbox: OutboxStore,
  private val link: OutboxLink,
  private val admission: DeviceAdmissionGuard,
  private val selfAddress: () -> String,
  private val isActive: (Bridge) -> Boolean,
) {
  private val channel = VaultChannel(identity, state, selfAddress)
  @Volatile
  private var directory: VaultDirectory? = null

  fun attachDirectory(value: VaultDirectory) {
    directory = value
  }

  fun queryVaults(activeBridge: Bridge) {
    if (!admission.isAdmitted()) return
    queryOwnMailbox(activeBridge)
    val peers = runCatching { JSONObject(state.peersJSON()) }.getOrNull() ?: return
    val keys = peers.keys()
    val targets = mutableListOf<String>()
    while (keys.hasNext()) {
      val key = keys.next()
      if (peers.optJSONObject(key) != null) targets.add(key)
    }
    if (targets.isEmpty()) return
    val thread = Thread({
      var answered = 0
      for (target in targets) {
        if (!isActive(activeBridge)) return@Thread
        if (queryVault(activeBridge, target)) answered += 1
      }
      Log.i(TAG, "Consulta de vault enviada a $answered de ${targets.size} contactos")
    }, "upeer-vault-query")
    thread.isDaemon = true
    thread.start()
  }

  fun queryVault(activeBridge: Bridge, upeerId: String): Boolean {
    if (!admission.isAdmitted()) return false
    val address = state.addressFor(upeerId) ?: return false
    val frame = channel.queryPacket(upeerId) ?: return false
    return link.sendPacket(activeBridge, address, frame)
  }

  fun escrow(activeBridge: Bridge, entry: JSONObject): Boolean {
    if (!admission.isAdmitted()) return false
    if (entry.optString("kind", "CHAT") == "CHAT_UPDATE") return false
    val packet = entry.optString("packet")
    if (packet.isEmpty()) return false
    val messageID = entry.optString("id")
    if (messageID.isEmpty()) return false
    val upeerId = entry.optString("upeerId")
    val self = identity.current()?.upeerID() ?: return false
    val envelope = channel.envelopeForStore(packet, upeerId, System.currentTimeMillis()) ?: return false
    val accepted = mutableListOf<VaultCustodianCandidate>()
    for (custodian in channel.custodians(self, upeerId)) {
      val frame = channel.storePacket(custodian.upeerId, envelope.json) ?: continue
      if (link.sendPacket(activeBridge, custodian.address, frame)) accepted.add(custodian)
    }
    if (accepted.isEmpty()) return false
    outbox.remove(messageID)
    state.messages.markStatus(messageID, "vaulted")
    Log.i(TAG, "Mensaje $messageID en custodia de ${accepted.size} custodios")
    publishPointer(accepted)
    return true
  }

  private fun publishPointer(custodians: List<VaultCustodianCandidate>) {
    val active = directory ?: return
    if (active.publish(custodians, System.currentTimeMillis())) {
      Log.i(TAG, "Puntero de vault publicado para ${custodians.size} custodios")
    } else {
      Log.w(TAG, "El puntero de vault no se pudo publicar; la entrega seguirá por sondeo")
    }
  }

  private fun queryOwnMailbox(activeBridge: Bridge) {
    val active = directory ?: return
    val self = identity.current()?.upeerID() ?: return
    val known = active.resolve(self, identity.currentDevice()?.deviceId, System.currentTimeMillis())
    if (known.isEmpty()) return
    val thread = Thread({
      var sent = 0
      for (custodian in known) {
        if (!isActive(activeBridge)) return@Thread
        if (queryVault(activeBridge, custodian.upeerId)) sent += 1
      }
      Log.i(TAG, "Puntero de vault resuelto: $sent de ${known.size} custodios consultados")
    }, "upeer-vault-pointer")
    thread.isDaemon = true
    thread.start()
  }

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
