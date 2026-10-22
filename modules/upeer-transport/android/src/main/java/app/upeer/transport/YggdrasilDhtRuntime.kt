package app.upeer.transport

import android.content.Context
import android.util.Log
import app.upeer.transport.dht.DhtLocationWorker
import app.upeer.transport.dht.DhtLocationStore
import app.upeer.transport.dht.DhtMessage
import app.upeer.transport.dht.DhtPacketPolicy
import app.upeer.transport.dht.DhtUpdateAcceptanceStore
import app.upeer.transport.dht.DhtUpdateDispatcher
import app.upeer.transport.dht.DhtUpdateService
import app.upeer.transport.dht.dhtUpdateTargets
import app.upeer.transport.dht.kademlia.AndroidKademliaDnsTxtResolver
import app.upeer.transport.dht.kademlia.KademliaBootstrap
import app.upeer.transport.dht.kademlia.KademliaContact
import app.upeer.transport.dht.kademlia.KademliaContactFileStore
import app.upeer.transport.dht.kademlia.KademliaId
import app.upeer.transport.dht.kademlia.KademliaNode
import app.upeer.transport.dht.kademlia.KademliaPacketRouter
import app.upeer.transport.dht.kademlia.KademliaPacketSender
import app.upeer.transport.dht.kademlia.KademliaSeedLoader
import app.upeer.transportbridge.transportbridge.Bridge
import app.upeer.transportbridge.transportbridge.Transportbridge
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class YggdrasilDhtRuntime(
  private val context: Context,
  private val identity: YggdrasilIdentity,
  private val accountRegistry: AccountRegistryFacade,
  private val contactRegistries: ContactRegistryStore,
  private val bridge: () -> Bridge?,
  private val ownAddress: () -> String,
  private val admission: () -> Unit,
  private val locationBlock: () -> String? = { null },
  private val knownPublicKey: (String) -> String? = { null },
  private val intimatePeersJSON: () -> String? = { null },
  private val nowMillis: () -> Long = System::currentTimeMillis,
  private val locationStore: DhtLocationStore? = null,
) {
  private val lock = Any()
  private val dhtRateLimiter = InboundRateLimiter()
  @Volatile private var state: YggdrasilDhtState? = null
  @Volatile private var lastPublished = ""

  fun start() {
    synchronized(lock) {
      if (state != null) return@synchronized
      val account = identity.current() ?: return@synchronized
      val accountId = account.upeerID()
      val contacts = KademliaContactFileStore(File(context.filesDir, "dht-contacts.json"), accountId)
      val loader = KademliaSeedLoader(
        accountId,
        existingContacts = { contacts.load() },
        readFile = { File(context.filesDir, "seednodes.json").takeIf(File::isFile)?.readText() },
        dnsResolver = AndroidKademliaDnsTxtResolver(context),
      )
      val socket = DhtSocketTransport(context, bridge, ::onNetworkChanged)
      val sender = KademliaPacketSender({ identity.current() }, ownAddress, socket::transmit)
      val node = KademliaNode(accountId, sender::send)
      val keys = ConcurrentHashMap<String, String>()
      val acceptance = DhtUpdateAcceptanceStore(context, nowMillis)
      val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "upeer-dht").apply { isDaemon = true }
      }
      val locations = DhtLocationWorker(
        { identity.current()?.upeerID() },
        { identity.current()?.publicKeyHex() },
        locationBlock,
        { key, value, publisher -> node.storeValue(key, value, publisher) },
        { key -> node.findValue(key, localFirst = false)?.value },
        { upeerId -> keys[upeerId] ?: node.routingTable.findContact(upeerId)?.publicKey?.ifBlank { null } ?: knownPublicKey(upeerId) },
        ::refreshRegistry,
        nowMillis,
        sequenceReady = { account -> locationStore?.hasState(account) ?: true },
        adoptSequence = { account, seq -> locationStore?.adoptSequence(account, seq) },
      )
      val updateService = DhtUpdateService(
        acceptance,
        { account -> contactRegistries.current(account)?.takeIf { it.expiresAt > nowMillis() }?.registryJSON },
        { accepted ->
          locations.cacheAccepted(accepted.accountUpeerId, accepted.blockJSON, accepted.expiresAt)
          node.applyLocation(accepted.accountUpeerId, accepted.primaryAddress, accepted.dhtSeq)
        },
        { account -> executor.execute { refreshRegistry(account) } },
      )
      val router = KademliaPacketRouter(
        node,
        { upeerId -> keys[upeerId] ?: node.routingTable.findContact(upeerId)?.publicKey?.ifBlank { null } ?: knownPublicKey(upeerId) },
        sender::send,
        applyUpdate = updateService::handle,
      )
      val updates = DhtUpdateDispatcher(
        {
          dhtUpdateTargets(intimatePeersJSON()) { upeerId ->
            buildList {
              acceptance.current(upeerId)?.let { location ->
                add(location.primaryAddress)
                addAll(location.addresses)
              }
              node.routingTable.findContact(upeerId)?.address?.let(::add)
            }
          }
        },
        sender::send,
      )
      val bootstrap = KademliaBootstrap(node, loader, contacts)
      val callback = socket.register()
      state = YggdrasilDhtState(node, router, bootstrap, executor, callback, socket, keys, loader, locations, updates)
      executor.execute { attemptBootstrap() }
      executor.scheduleWithFixedDelay(::maintenance, MAINTENANCE_SECONDS, MAINTENANCE_SECONDS, TimeUnit.SECONDS)
    }
  }

  fun stop() {
    val current = synchronized(lock) {
      val active = state
      state = null
      lastPublished = ""
      active
    } ?: return
    current.networkCallback?.let { callback ->
      current.socket.unregister(callback)
    }
    current.executor.shutdownNow()
    current.bootstrap.onNetworkChanged()
  }

  fun handleFrame(payloadJSON: String, sourceAddress: String): String? {
    val current = synchronized(lock) { state } ?: return null
    val outer = runCatching { DhtPacketPolicy.parse(payloadJSON) }.getOrNull() ?: return null
    val type = runCatching { DhtPacketPolicy.type(outer) }.getOrNull() ?: return null
    if (!type.startsWith(DHT_PREFIX)) return null
    if (type == "DHT_UPDATE") {
      val sender = outer.optString("senderUpeerId").lowercase()
      val allowed = dhtRateLimiter.allow("ip:$sourceAddress", type) &&
        (sender.isBlank() || dhtRateLimiter.allow("id:$sender", type))
      if (!allowed) {
        Log.w(TAG, "DHT_UPDATE limitado desde $sourceAddress")
        return JSONObject().put("type", type).put("rateLimited", true).toString()
      }
    }
    return runCatching {
      current.router.handle(payloadJSON, sourceAddress)?.toString()
    }.getOrElse { cause ->
      Log.w(TAG, "Paquete DHT rechazado: ${message(cause)}")
      JSONObject().put("handled", false).put("error", message(cause)).toString()
    }
  }

  fun onNetworkChanged() {
    val current = synchronized(lock) { state } ?: return
    current.bootstrap.onNetworkChanged()
    current.executor.execute {
      runCatching { current.bootstrap.retryNow() }.onFailure { cause ->
        Log.w(TAG, "No se pudo reintentar el bootstrap DHT", cause)
      }
      publishRegistry(force = true)
      current.updates.reset()
      publishLocation(current, force = true)
      refreshKnownRegistries(current)
    }
  }

  fun publishRegistry(force: Boolean = false) {
    val current = synchronized(lock) { state } ?: return
    if (!current.bootstrap.isBootstrapped() || !admitted()) return
    val account = identity.current() ?: return
    val registry = accountRegistry.registryJSON().takeIf(String::isNotEmpty) ?: return
    if (!force && registry == lastPublished) return
    val key = Transportbridge.accountRegistryKey(account.upeerID())
    val deliveries = runCatching {
      current.node.storeValue(key, JSONObject(registry), account.upeerID())
    }.getOrElse { cause ->
      Log.w(TAG, "No se pudo publicar el registro de cuenta en DHT", cause)
      return
    }
    lastPublished = registry
    Log.i(TAG, "Registro de cuenta publicado en $deliveries nodos DHT")
  }

  fun refreshRegistry(accountUpeerId: String): String? {
    val current = synchronized(lock) { state } ?: return null
    val key = runCatching { Transportbridge.accountRegistryKey(accountUpeerId) }.getOrElse { cause ->
      Log.w(TAG, "La cuenta DHT no es válida", cause)
      return null
    }
    val first = lookupRegistry(current.node, key) ?: return null
    runCatching { contactRegistries.adopt(first) }.getOrNull()?.let { return it.registryJSON }
    current.node.valueStore.delete(key)
    val retry = lookupRegistry(current.node, key) ?: return null
    return runCatching { contactRegistries.adopt(retry).registryJSON }.getOrElse { cause ->
      Log.w(TAG, "El registro DHT del contacto no es válido", cause)
      null
    }
  }

  fun statusJSON(): String {
    val current = synchronized(lock) { state }
      ?: return JSONObject().put("ready", false).toString()
    val stats = current.bootstrap.stats()
    return JSONObject()
      .put("ready", true)
      .put("nodeId", current.node.nodeId.hex)
      .put("contacts", current.node.routingTable.contactCount())
      .put("bootstrapped", stats.bootstrapped)
      .put("seedsLoaded", stats.seedsLoaded)
      .put("lastSuccessAt", stats.lastSuccessMillis)
      .put("nextAttemptAt", stats.nextAttemptMillis)
      .put("published", lastPublished.isNotEmpty())
      .put("locationPublished", current.locations.published)
      .put("remoteRegistries", JSONObject(contactRegistries.statusJSON()).optInt("registries"))
      .toString()
  }

  fun lookupContactLocation(accountUpeerId: String): String? {
    val current = synchronized(lock) { state } ?: return null
    return current.locations.refresh(accountUpeerId)
  }

  private fun attemptBootstrap() {
    val current = synchronized(lock) { state } ?: return
    val seeds = runCatching { current.loader.load().seeds }.getOrElse { cause ->
      Log.w(TAG, "No se pudieron cargar las semillas DHT", cause)
      emptyList()
    }
    seeds.forEach { seed ->
      current.keys[seed.upeerId] = seed.publicKey
      current.node.addContact(
        KademliaContact(KademliaId.fromUpeerId(seed.upeerId), seed.upeerId, seed.address, seed.publicKey, nowMillis()),
      )
    }
    runCatching { current.bootstrap.attempt() }.onFailure { cause ->
      Log.w(TAG, "No se pudo completar el bootstrap DHT", cause)
    }
    publishRegistry(force = true)
    publishLocation(current, force = true)
    if (current.bootstrap.isBootstrapped()) refreshKnownRegistries(current)
  }

  private fun maintenance() {
    val current = synchronized(lock) { state } ?: return
    current.node.maintenance()
    runCatching { current.bootstrap.maintenance() }.onFailure { cause ->
      Log.w(TAG, "No se pudo mantener el bootstrap DHT", cause)
    }
    publishRegistry()
    publishLocation(current, force = false)
  }

  private fun publishLocation(current: YggdrasilDhtState, force: Boolean) {
    current.locations.publish(force)
    val block = current.locations.lastVerifiedBlock() ?: return
    val sent = current.updates.dispatch(block)
    if (sent > 0) Log.i(TAG, "DHT_UPDATE enviado a $sent direcciones de contactos íntimos")
  }

  private fun lookupRegistry(node: KademliaNode, key: String): String? {
    val stored = node.findValue(key, localFirst = false) ?: return null
    return when (val value = stored.value) {
      is JSONObject -> value.toString()
      is String -> value
      else -> null
    }
  }

  private fun refreshKnownRegistries(current: YggdrasilDhtState) {
    current.node.routingTable.allContacts()
      .asSequence()
      .filter { it.upeerId != current.node.upeerId }
      .take(MAX_CONTACT_REFRESH)
      .forEach { contact -> refreshRegistry(contact.upeerId) }
  }

  private fun admitted(): Boolean = runCatching { admission() }.isSuccess

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val DHT_PREFIX = "DHT_"
    private const val MAX_CONTACT_REFRESH = 8
    private const val MAINTENANCE_SECONDS = 60L
  }
}

