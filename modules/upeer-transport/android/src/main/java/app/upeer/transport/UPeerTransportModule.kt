package app.upeer.transport

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class UPeerTransportModule : Module() {
  private val lock = Any()
  @Volatile private var transport: IYggdrasilTransport? = null
  @Volatile private var destroyed = false
  private var binding: ServiceConnection? = null

  override fun definition() = ModuleDefinition {
    Name("UPeerTransport")
    OnCreate { bind() }
    OnDestroy {
      destroyed = true
      synchronized(lock) {
        binding?.let { context().unbindService(it) }
        binding = null
        transport = null
      }
    }
    AsyncFunction("generateNodeSeed") { service().generateNodeSeed().map { it.toInt() and 0xff }.toIntArray() }
    AsyncFunction("deriveIdentity") { seed: IntArray ->
      require(seed.size == 32 && seed.all { it in 0..255 }) { "Invalid identity seed" }
      val identitySeed = seed.toByteArrayCompat()
      try {
        service().deriveIdentity(identitySeed)
      } catch (cause: RemoteException) {
        throw IllegalStateException("El servicio nativo de identidad se cerró durante la derivación.", cause)
      } finally {
        identitySeed.fill(0)
        seed.fill(0)
      }
    }
    AsyncFunction("startWithPeers") { peers: List<String>, seed: IntArray ->
      require(seed.size == 32 && seed.all { it in 0..255 }) { "Invalid Yggdrasil node seed" }
      startForegroundService()
      val activeTransport = service()
      activeTransport.startWithPeers(org.json.JSONArray(peers).toString(), seed.toByteArrayCompat()).also {
        activeTransport.ensureForeground()
      }.let(::asNodeInfo)
    }
    AsyncFunction("start") { config: IntArray ->
      require(config.isNotEmpty() && config.all { it in 0..255 }) { "Invalid Yggdrasil configuration" }
      startForegroundService()
      val activeTransport = service()
      activeTransport.start(config.toByteArrayCompat()).also {
        activeTransport.ensureForeground()
      }.let(::asNodeInfo)
    }
    AsyncFunction("nodeInfo") {
      service().nodeInfo().takeIf(String::isNotEmpty)?.let(::asNodeInfo)
    }
    AsyncFunction("stop") { service().stop() }
    AsyncFunction("setForeground") { startForegroundService() }
    AsyncFunction("peerStatus") { service().peerStatusJSON() }
    AsyncFunction("peerPool") { service().peerPoolJSON() }
    AsyncFunction("connectedPeerCount") { service().connectedPeerCount().toInt() }
    AsyncFunction("retryPeers") { service().retryPeers() }
    AsyncFunction("dialTCP") { address: String, port: Int -> service().dialTCP(address, port) }
    AsyncFunction("dialUDP") { address: String, port: Int -> service().dialUDP(address, port) }
    AsyncFunction("writeTCP") { id: Int, payload: IntArray -> service().write(id, payload.toByteArrayCompat()) }
    AsyncFunction("writeUDP") { id: Int, payload: IntArray -> service().writeDatagram(id, payload.toByteArrayCompat()) }
    AsyncFunction("read") { id: Int, maxBytes: Int, timeout: Int ->
      service().read(id, maxBytes, timeout).map { it.toInt() and 0xff }.toIntArray()
    }
    AsyncFunction("closeConnection") { id: Int -> service().closeConnection(id) }
  }

  private fun bind() {
    synchronized(lock) { bindLocked(null) }
  }

  private fun bindLocked(latch: CountDownLatch?) {
    if (binding != null) return
    val connection = createConnection(latch)
    binding = connection
    check(context().bindService(serviceIntent(), connection, Context.BIND_AUTO_CREATE)) {
      binding = null
      "Could not bind Yggdrasil transport service"
    }
  }

  private fun createConnection(latch: CountDownLatch?) = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
      transport = IYggdrasilTransport.Stub.asInterface(binder)
      latch?.countDown()
    }

    override fun onServiceDisconnected(name: ComponentName) { clearConnection(this, latch) }

    override fun onBindingDied(name: ComponentName) { clearConnection(this, latch) }

    override fun onNullBinding(name: ComponentName) { clearConnection(this, latch) }
  }

  private fun clearConnection(connection: ServiceConnection, latch: CountDownLatch?) {
    transport = null
    latch?.countDown()
    synchronized(lock) {
      if (destroyed) return
      if (binding === connection) binding = null
      if (binding != null) return
      val next = createConnection(null)
      binding = next
      if (!context().bindService(serviceIntent(), next, Context.BIND_AUTO_CREATE)) binding = null
    }
  }

  private fun service(): IYggdrasilTransport {
    check(!destroyed) { "Yggdrasil transport module is shutting down" }
    transport?.let { return it }
    val latch = CountDownLatch(1)
    synchronized(lock) {
      transport?.let { return it }
      binding?.let { context().unbindService(it) }
      binding = null
      bindLocked(latch)
    }
    check(latch.await(15, TimeUnit.SECONDS)) { "Timed out waiting for Yggdrasil transport service" }
    return transport ?: error("Yggdrasil transport service did not connect")
  }

  private fun startForegroundService() {
    val intent = serviceIntent().setAction(YggdrasilTransportService.ACTION_START)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) context().startForegroundService(intent)
    else context().startService(intent)
  }

  private fun context(): Context = appContext.reactContext ?: error("Android context is unavailable")

  private fun serviceIntent() = Intent(context(), YggdrasilTransportService::class.java)

  private fun asNodeInfo(value: String): Map<String, String> {
    val json = JSONObject(value)
    return mapOf("address" to json.getString("address"), "publicKey" to json.getString("publicKey"))
  }

}

private fun IntArray.toByteArrayCompat() = ByteArray(size) { this[it].toByte() }