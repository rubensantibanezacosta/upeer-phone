package app.upeer.transport

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import app.upeer.transport.dht.ContactEncryptionAdmission
import app.upeer.transport.dht.DhtLocationStore
import app.upeer.transport.dht.LegacyContactPolicy
import app.upeer.transportbridge.transportbridge.Bridge
import app.upeer.transportbridge.transportbridge.Transportbridge
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

class YggdrasilTransportService : Service() {
  companion object {
    const val ACTION_START = "app.upeer.transport.START_NETWORK"
    internal const val ACTION_STOP = "app.upeer.transport.STOP_NETWORK"
    private const val NOTIFICATION_ID = 7301
    private const val INBOUND_PORT = 50005
    private const val TAG = "UPeerTransport"
  }

  private val bridgeLock = Any()
  private val inbound = YggdrasilInbound(INBOUND_PORT) { activeBridge, payload, source ->
    admissionGuard.require()
    dhtRuntime.handleFrame(String(payload, Charsets.UTF_8), source)
      ?: protocol.handleFrame(activeBridge, payload, source)
  }
  private lateinit var configStore: YggdrasilConfigStore
  private lateinit var protocolState: ProtocolStateStore
  private lateinit var identity: YggdrasilIdentity
  private lateinit var accountRegistry: AccountRegistryFacade
  private lateinit var notifications: UPeerNotifications
  private lateinit var protocol: YggdrasilProtocol
  private lateinit var dhtLocation: DhtLocationStore
  private lateinit var admissionGuard: DeviceAdmissionGuard
  private lateinit var dhtBindings: YggdrasilDhtBindings
  private lateinit var bridgeBindings: YggdrasilBridgeBindings
  private lateinit var contactRegistries: ContactRegistryStore
  private lateinit var contactAdmission: ContactEncryptionAdmission
  private lateinit var dhtRuntime: YggdrasilDhtRuntime
  private lateinit var enrollment: DeviceEnrollment
  private lateinit var enrollmentAuthorizer: DeviceEnrollmentAuthorizer
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
          inbound.start(activeBridge)
          protocol.start(activeBridge)
        } finally {
          config.fill(0)
        }
        identity.refreshRegistry()
        dhtRuntime.start()
        JSONObject().put("address", activeBridge.address()).put("publicKey", activeBridge.publicKey()).toString()
      } catch (cause: Exception) {
        synchronized(bridgeLock) { if (bridge === activeBridge) bridge = null }
        dhtRuntime.stop()
        protocol.stop()
        inbound.stop()
        YggdrasilNodeRestore.stopAfterFailedStart(activeBridge, cause)
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
        inbound.start(activeBridge)
        protocol.start(activeBridge)
        identity.refreshRegistry()
        dhtRuntime.start()
        JSONObject().put("address", activeBridge.address()).put("publicKey", activeBridge.publicKey()).toString()
      } catch (cause: Exception) {
        synchronized(bridgeLock) { if (bridge === activeBridge) bridge = null }
        dhtRuntime.stop()
        protocol.stop()
        inbound.stop()
        YggdrasilNodeRestore.stopAfterFailedStart(activeBridge, cause)
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
      dhtRuntime.stop()
      protocol.stop()
      synchronized(bridgeLock) {
        val activeBridge = bridge
        bridge = null
        activeBridge?.stop()
        configStore.clear()
      }
      inbound.stop()
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
    }

    override fun peerStatusJSON(): String = bridgeBindings.peerStatusJSON()
    override fun peerPoolJSON(): String = bridgeBindings.peerPoolJSON()
    override fun connectedPeerCount(): Long = bridgeBindings.connectedPeerCount()
    override fun retryPeers() = bridgeBindings.retryPeers()
    override fun dialTCP(address: String, port: Int): Int = bridgeBindings.dialTCP(address, port)
    override fun dialUDP(address: String, port: Int): Int = bridgeBindings.dialUDP(address, port)
    override fun write(connectionID: Int, payload: ByteArray) = bridgeBindings.write(connectionID, payload)
    override fun writeDatagram(connectionID: Int, payload: ByteArray) = bridgeBindings.writeDatagram(connectionID, payload)
    override fun closeConnection(connectionID: Int) = bridgeBindings.closeConnection(connectionID)
    override fun read(connectionID: Int, maxBytes: Int, timeoutMillis: Int): ByteArray =
      bridgeBindings.read(connectionID, maxBytes, timeoutMillis)
    override fun listenTCP(port: Int): Int = bridgeBindings.listenTCP(port)
    override fun acceptTCP(listenerID: Int, timeoutMillis: Int): Int = bridgeBindings.acceptTCP(listenerID, timeoutMillis)
    override fun closeListener(listenerID: Int) = bridgeBindings.closeListener(listenerID)
    override fun readFrame(connectionID: Int, timeoutMillis: Int): ByteArray = bridgeBindings.readFrame(connectionID, timeoutMillis)
    override fun writeFrame(connectionID: Int, payload: ByteArray) = bridgeBindings.writeFrame(connectionID, payload)
    override fun validateControlFrame(payload: ByteArray): String = bridgeBindings.validateControlFrame(payload)
    override fun inboundStatusJSON(): String = bridgeBindings.inboundStatusJSON()
    override fun setIdentity(secretKey: ByteArray) {
      try {
        identity.set(secretKey)
      } finally {
        secretKey.fill(0)
      }
      contactAdmission.clear()
      if (bridge != null) dhtRuntime.start()
    }
    override fun clearIdentity() {
      dhtRuntime.stop()
      try {
        dhtLocation.clearIdentity()
      } catch (cause: Exception) {
        Log.e(TAG, "No se pudo borrar la secuencia DHT durante el cierre de identidad", cause)
      }
      try {
        contactRegistries.clear()
      } catch (cause: Exception) {
        Log.e(TAG, "No se pudieron borrar los registros de contactos", cause)
      }
      protocol.clear()
      identity.clear()
      notifications.clearMessages()
    }
    override fun identityStatusJSON(): String = identity.statusJSON()
    override fun sendChat(upeerId: String, content: String, replyTo: String): String =
      admitted { protocol.sendChat(upeerId, content, replyTo) }
    override fun editChat(upeerId: String, messageId: String, content: String): Int =
      admitted { protocol.editChat(upeerId, messageId, content) }
    override fun messagesJSON(upeerId: String, limit: Int): String = protocol.messagesJSON(upeerId, limit)
    override fun conversationsJSON(): String = protocol.conversationsJSON()
    override fun markConversationRead(upeerId: String) {
      protocol.markConversationRead(upeerId)
      notifications.clearConversation(upeerId)
    }
    override fun deleteConversation(upeerId: String): Boolean = protocol.deleteConversation(upeerId)
    override fun setBlockedPeers(blockedJSON: String) {
      val parsed = JSONArray(blockedJSON.ifBlank { "[]" })
      protocol.setBlockedPeers(buildList { for (index in 0 until parsed.length()) add(parsed.optString(index)) })
    }
    override fun setKnownContacts(knownJSON: String) {
      val parsed = JSONArray(knownJSON.ifBlank { "[]" })
      val known = buildList { for (index in 0 until parsed.length()) add(parsed.optString(index)) }
      protocolState.setKnownContacts(known)
      Log.i(TAG, "Contactos conocidos sincronizados: ${known.size}")
    }
    override fun setNotificationPrefs(prefsJSON: String) {
      val prefs = NotificationPrefs.parse(prefsJSON)
      protocolState.setNotificationPrefs(prefs)
      Log.i(TAG, "Preferencias de notificaciones: mensajes=${prefs.messages} solicitudes=${prefs.requests} sonido=${prefs.sound}")
    }
    override fun registerPeer(upeerId: String, address: String, publicKey: String, signedPreKeyJSON: String): Boolean =
      protocol.registerPeer(upeerId, address, publicKey, signedPreKeyJSON)
    override fun flushOutbox() = protocol.flushOutbox()
    override fun protocolStatusJSON(): String =
      JSONObject(protocol.statusJSON()).put("dht", JSONObject(dhtRuntime.statusJSON())).toString()
    override fun locationBlockJSON(ttlMillis: Long): String = dhtBindings.locationBlockJSON(ttlMillis)
    override fun accountRegistryJSON(): String = dhtBindings.accountRegistryJSON()
    override fun accountRegistryStatusJSON(): String = dhtBindings.accountRegistryStatusJSON()
    override fun revokeAccountDevice(deviceId: String, reason: String): String =
      dhtBindings.revokeAccountDevice(deviceId, reason)
    override fun validateDeviceAdmission(
      certificateJSON: String,
      registryJSON: String,
      expectedAccountUpeerId: String,
      nowMillis: Long,
    ): Boolean = dhtBindings.validateDeviceAdmission(certificateJSON, registryJSON, expectedAccountUpeerId, nowMillis)
    override fun deviceIdentityStatusJSON(): String = dhtBindings.deviceIdentityStatusJSON()
    override fun dhtLocationStatusJSON(): String = dhtBindings.dhtLocationStatusJSON()
    override fun lookupContactLocation(upeerId: String): String = dhtRuntime.lookupContactLocation(upeerId).orEmpty()
    override fun verifyLocationBlock(blockJSON: String, publicKeyHex: String, nowMillis: Long): Boolean =
      dhtBindings.verifyLocationBlock(blockJSON, publicKeyHex, nowMillis)
    override fun deviceEnrollmentBegin(code: String): String = enrollment.begin(code)
    override fun deviceEnrollmentRequestJSON(): String = enrollment.requestJSON()
    override fun deviceEnrollmentFinish(sealedResponse: String): String = enrollment.finish(sealedResponse)
    override fun deviceEnrollmentCancel() = enrollment.cancel()
    override fun deviceEnrollmentStatusJSON(): String = enrollment.statusJSON()
    override fun enrollmentAuthorizerCreateInvite(ttlMillis: Long): String = enrollmentAuthorizer.createInvite(ttlMillis)
    override fun enrollmentAuthorizerAcceptRequest(requestCode: String): String = enrollmentAuthorizer.acceptRequest(requestCode)
    override fun enrollmentAuthorizerComplete(enrollmentJSON: String, ttlMillis: Long): String =
      enrollmentAuthorizer.complete(enrollmentJSON, ttlMillis)
    override fun enrollmentAuthorizerStatusJSON(): String = enrollmentAuthorizer.pendingJSON()
    override fun enrollmentAuthorizerCancel(inviteId: String) = enrollmentAuthorizer.cancel(inviteId)
    override fun enrollmentAuthorizerCancelAll() = enrollmentAuthorizer.cancelAll()
  }

  override fun onCreate() {
    super.onCreate()
    System.loadLibrary("gojni")
    app.upeer.transportbridge.transportbridge.Transportbridge.touch()
    configStore = YggdrasilConfigStore(this)
    enrollment = DeviceEnrollment(this)
    identity = YggdrasilIdentity(this)
    enrollmentAuthorizer = DeviceEnrollmentAuthorizer(this) { identity.authorizerAccount() }
    accountRegistry = AccountRegistryFacade(identity)
    dhtLocation = DhtLocationStore(this)
    contactRegistries = ContactRegistryStore(this)
    notifications = UPeerNotifications(this, { protocolState.notificationPrefs() }, { upeerId -> protocolState.isKnownContact(upeerId) })
    notifications.createChannels()
    val signedPreKeys = SignedPreKeyStore(this)
    admissionGuard = DeviceAdmissionGuard({ identity.currentDeviceAdmission() }) {
      try {
        protocol.clear()
      } catch (cause: Exception) {
        Log.e(TAG, "No se pudo borrar el estado de protocolo del dispositivo revocado", cause)
      }
    }
    protocolState = ProtocolStateStore(this)
    dhtBindings = YggdrasilDhtBindings(
      identity,
      accountRegistry,
      dhtLocation,
      { requireBridge().address() },
      { admissionGuard.ensure(identity::requireDeviceAdmission) },
      { admissionGuard.isAdmitted() },
    )
    bridgeBindings = YggdrasilBridgeBindings(::requireBridge, inbound)
    dhtRuntime = YggdrasilDhtRuntime(
      this, identity, accountRegistry, contactRegistries, { bridge }, { requireBridge().address() },
      { admissionGuard.ensure(identity::requireDeviceAdmission) },
      { dhtBindings.locationBlockJSON(0) },
      { upeerId -> protocolState.publicKeyFor(upeerId) },
      { protocolState.peersJSON() },
      locationStore = dhtLocation,
    )
    contactAdmission = ContactEncryptionAdmission(
      { upeerId -> protocolState.publicKeyFor(upeerId) },
      { upeerId -> dhtRuntime.lookupContactLocation(upeerId) },
      { upeerId -> contactRegistries.current(upeerId) },
      { upeerId -> dhtRuntime.requestContactRefresh(upeerId) },
      legacyPolicy = LegacyContactPolicy.ALLOW_WITH_AUDIT,
    )
    protocol = YggdrasilProtocol(
      identity,
      signedPreKeys,
      DeviceSignedPreKeyStore(this),
      DeviceMessagingBindings(),
      protocolState,
      OutboxStore(this),
      notifications::notifyMessage,
      admissionGuard,
      contactAdmission,
    )
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == YggdrasilTransportService.ACTION_STOP) {
      binder.stop()
      return START_NOT_STICKY
    }
    if (intent?.action == UPeerNotifications.ACTION_MARK_READ) {
      val upeerId = intent.getStringExtra(UPeerNotifications.EXTRA_UPEER_ID)
      if (upeerId.isNullOrBlank()) {
        Log.w(TAG, "Acción de marcar como leído sin upeerId")
      } else {
        binder.markConversationRead(upeerId)
      }
      return START_STICKY
    }
    startForeground(NOTIFICATION_ID, notifications.foregroundNotification())
    if (intent == null) restoreNode()
    return START_STICKY
  }

  fun ensureForeground() {
    startForeground(NOTIFICATION_ID, notifications.foregroundNotification())
  }
  override fun onBind(intent: Intent?): IBinder = binder
  override fun onDestroy() {
    dhtRuntime.stop()
    protocol.stop()
    synchronized(bridgeLock) {
      val activeBridge = bridge
      bridge = null
      activeBridge?.close()
    }
    inbound.stop()
    super.onDestroy()
  }
  private fun restoreNode() = YggdrasilNodeRestore.restoreIfNeeded(
    configStore, inbound, protocol, { bridge }, { synchronized(bridgeLock) { bridge = it } },
    { identity.refreshRegistry(); dhtRuntime.start(); startForeground(NOTIFICATION_ID, notifications.foregroundNotification()) },
    { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() },
  )

  private fun requireBridge(): Bridge = synchronized(bridgeLock) { requireNotNull(bridge) { "Yggdrasil bridge is not started" } }

  private fun <T> admitted(block: () -> T): T =
    admissionGuard.ensure(identity::requireDeviceAdmission).let { block() }

  override fun onTaskRemoved(rootIntent: Intent?) { if (bridge != null) startForeground(NOTIFICATION_ID, notifications.foregroundNotification()); super.onTaskRemoved(rootIntent) }

}