package app.upeer.transport

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import app.upeer.transportbridge.transportbridge.Bridge
import app.upeer.transportbridge.transportbridge.Transportbridge
import org.json.JSONObject
import java.security.SecureRandom

class YggdrasilTransportService : Service() {
  companion object {
    const val ACTION_START = "app.upeer.transport.START_NETWORK"
    private const val ACTION_STOP = "app.upeer.transport.STOP_NETWORK"
    private const val CHANNEL_ID = "upeer_yggdrasil_transport"
    private const val NOTIFICATION_ID = 7301
    private const val STOP_REQUEST_CODE = 7302
  }

  private val bridgeLock = Any()
  private lateinit var configStore: YggdrasilConfigStore
  private var bridge: Bridge? = null

  private val binder = object : IYggdrasilTransport.Stub() {
    override fun startWithPeers(peersJSON: String, seed: ByteArray): String = synchronized(bridgeLock) {
      check(bridge == null) { "Yggdrasil bridge already started" }
      val activeBridge = Transportbridge.newBridge()
      try {
        activeBridge.enablePeerManager()
        val config = activeBridge.nodeConfigWithPeers(peersJSON, seed).toByteArray()
        try {
          activeBridge.start(config)
          configStore.save(config)
          bridge = activeBridge
        } finally {
          config.fill(0)
        }
        JSONObject().put("address", activeBridge.address()).put("publicKey", activeBridge.publicKey()).toString()
      } catch (cause: Exception) {
        stopAfterFailedStart(activeBridge, cause)
        throw cause
      } finally {
        seed.fill(0)
      }
    }

    override fun start(configJSON: ByteArray): String = synchronized(bridgeLock) {
      check(bridge == null) { "Yggdrasil bridge already started" }
      val activeBridge = Transportbridge.newBridge()
      try {
        activeBridge.enablePeerManager()
        activeBridge.start(configJSON)
        configStore.save(configJSON)
        bridge = activeBridge
        JSONObject().put("address", activeBridge.address()).put("publicKey", activeBridge.publicKey()).toString()
      } catch (cause: Exception) {
        stopAfterFailedStart(activeBridge, cause)
        throw cause
      }
    }

    override fun nodeInfo(): String = synchronized(bridgeLock) {
      val activeBridge = bridge ?: return@synchronized ""
      JSONObject().put("address", activeBridge.address()).put("publicKey", activeBridge.publicKey()).toString()
    }

    override fun deriveIdentity(seed: ByteArray): String = Transportbridge.newBridge().deriveIdentity(seed)

    override fun generateNodeSeed(): ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)

    override fun ensureForeground() {
      this@YggdrasilTransportService.ensureForeground()
    }

    override fun stop() {
      synchronized(bridgeLock) {
        val activeBridge = bridge
        bridge = null
        activeBridge?.stop()
        configStore.clear()
      }
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
    }


    override fun peerStatusJSON(): String = requireBridge().peerStatusJSON()

    override fun peerPoolJSON(): String = requireBridge().peerPoolJSON()

    override fun connectedPeerCount(): Long = requireBridge().connectedPeerCount()

    override fun retryPeers() = requireBridge().retryPeers()

    override fun dialTCP(address: String, port: Int): Int = requireBridge().dialTCP(address, port.toLong())

    override fun dialUDP(address: String, port: Int): Int = requireBridge().dialUDP(address, port.toLong())

    override fun write(connectionID: Int, payload: ByteArray) = requireBridge().write(connectionID, payload)

    override fun writeDatagram(connectionID: Int, payload: ByteArray) = requireBridge().writeDatagram(connectionID, payload)

    override fun closeConnection(connectionID: Int) = requireBridge().closeConnection(connectionID)

    override fun read(connectionID: Int, maxBytes: Int, timeoutMillis: Int): ByteArray =
      requireBridge().read(connectionID, maxBytes.toLong(), timeoutMillis.toLong())

  }

  override fun onCreate() {
    super.onCreate()
    System.loadLibrary("gojni")
    app.upeer.transportbridge.transportbridge.Transportbridge.touch()
    configStore = YggdrasilConfigStore(this)
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      binder.stop()
      return START_NOT_STICKY
    }
    startForeground(NOTIFICATION_ID, buildNotification())
    if (intent == null) restoreNode()
    return START_STICKY
  }

  fun ensureForeground() {
    startForeground(NOTIFICATION_ID, buildNotification())
  }

  override fun onBind(intent: Intent?): IBinder = binder

  override fun onDestroy() {
    synchronized(bridgeLock) {
      val activeBridge = bridge
      bridge = null
      activeBridge?.close()
    }
    super.onDestroy()
  }

  private fun restoreNode() {
    synchronized(bridgeLock) {
      if (bridge != null) return
    }
    val config = try {
      configStore.load()
    } catch (cause: Exception) {
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return
    } ?: run {
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return
    }
    val activeBridge = Transportbridge.newBridge()
    try {
      activeBridge.enablePeerManager()
      activeBridge.start(config)
      synchronized(bridgeLock) { bridge = activeBridge }
      startForeground(NOTIFICATION_ID, buildNotification())
    } catch (cause: Exception) {
      stopAfterFailedStart(activeBridge, cause)
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
    } finally {
      config.fill(0)
    }
  }

  private fun requireBridge(): Bridge = synchronized(bridgeLock) {
    requireNotNull(bridge) { "Yggdrasil bridge is not started" }
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    if (bridge != null) startForeground(NOTIFICATION_ID, buildNotification())
    super.onTaskRemoved(rootIntent)
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(CHANNEL_ID, "Conexión uPeer", NotificationManager.IMPORTANCE_LOW)
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  private fun buildNotification(): Notification {
    val stopIntent = Intent(this, YggdrasilTransportService::class.java).setAction(ACTION_STOP)
    val stopPendingIntent = PendingIntent.getService(
      this,
      STOP_REQUEST_CODE,
      stopIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
    val contentIntent = launchIntent?.let {
      PendingIntent.getActivity(this, 1, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else {
      @Suppress("DEPRECATION")
      Notification.Builder(this)
    }
    return builder
      .setSmallIcon(android.R.drawable.stat_sys_upload_done)
      .setContentTitle("uPeer está conectado")
      .setContentText("Manteniendo la conexión Yggdrasil activa")
      .setOngoing(true)
      .setContentIntent(contentIntent)
      .addAction(Notification.Action.Builder(0, "Desconectar", stopPendingIntent).build())
      .build()
  }

  private fun stopAfterFailedStart(activeBridge: Bridge, cause: Exception) {
    try {
      activeBridge.stop()
    } catch (cleanupCause: Exception) {
      cause.addSuppressed(cleanupCause)
    }
  }

}