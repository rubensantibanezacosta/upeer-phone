package app.upeer.transport

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.util.Log

internal class UPeerNotifications(
  private val context: Context,
  private val prefs: () -> NotificationPrefs = { NotificationPrefs.DEFAULT },
  private val isKnownContact: (String) -> Boolean = { true },
) {
  private val activeMessageIds = mutableSetOf<Int>()
  private val idLock = Any()

  fun createChannels() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = manager()
    manager.createNotificationChannel(
      NotificationChannel(CONNECTION_CHANNEL_ID, "Conexión uPeer", NotificationManager.IMPORTANCE_LOW).apply {
        description = "Conexión persistente con la red uPeer"
        setShowBadge(false)
      },
    )
    manager.createNotificationChannel(
      NotificationChannel(MESSAGES_CHANNEL_ID, "Mensajes", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Mensajes recibidos mientras uPeer está en segundo plano"
        enableVibration(true)
      },
    )
    manager.createNotificationChannel(
      NotificationChannel(MESSAGES_SILENT_CHANNEL_ID, "Mensajes sin sonido", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Mensajes recibidos con el sonido de notificación desactivado"
        setSound(null, null)
        enableVibration(false)
      },
    )
  }

  fun foregroundNotification(): Notification {
    val stopIntent = Intent(context, YggdrasilTransportService::class.java).setAction(YggdrasilTransportService.ACTION_STOP)
    val stopPendingIntent = PendingIntent.getService(context, STOP_REQUEST_CODE, stopIntent, pendingIntentFlags())
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val contentIntent = launchIntent?.let { PendingIntent.getActivity(context, 1, it, pendingIntentFlags()) }
    val builder = builder(CONNECTION_CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_upload_done)
      .setContentTitle("uPeer está conectado")
      .setContentText("Manteniendo la conexión segura activa")
      .setOngoing(true)
      .addAction(notificationAction(android.R.drawable.ic_menu_close_clear_cancel, "Desconectar", stopPendingIntent))
    if (contentIntent != null) builder.setContentIntent(contentIntent)
    return builder.build()
  }

  fun notifyMessage(upeerId: String, alias: String, content: String) {
    if (!canPostNotifications()) {
      Log.w(TAG, "No se muestra la notificación de $upeerId: POST_NOTIFICATIONS no está concedido")
      return
    }
    val current = prefs()
    val known = isKnownContact(upeerId)
    if (!current.allows(known)) {
      Log.i(TAG, "Notificación de $upeerId omitida por las preferencias (${if (known) "mensajes" else "solicitudes"} desactivadas)")
      return
    }
    val notificationId = messageNotificationId(upeerId)
    val title = alias.trim().ifEmpty { "uPeer · ${upeerId.take(8)}" }
    val preview = preview(content)
    val contentIntent = chatPendingIntent(upeerId, notificationId)
    val channelId = messageChannelId(current.sound)
    val publicVersion = builder(channelId)
      .setSmallIcon(android.R.drawable.stat_notify_chat)
      .setContentTitle(title)
      .setContentText("Nuevo mensaje")
      .setContentIntent(contentIntent)
      .build()
    val markReadIntent = Intent(context, YggdrasilTransportService::class.java)
      .setAction(ACTION_MARK_READ)
      .putExtra(EXTRA_UPEER_ID, upeerId)
    val markReadPendingIntent = PendingIntent.getService(context, notificationId, markReadIntent, pendingIntentFlags())
    val notification = builder(channelId)
      .setSmallIcon(android.R.drawable.stat_notify_chat)
      .setContentTitle(title)
      .setContentText(preview)
      .setStyle(Notification.BigTextStyle().bigText(preview))
      .setCategory(Notification.CATEGORY_MESSAGE)
      .setAutoCancel(true)
      .setContentIntent(contentIntent)
      .setVisibility(Notification.VISIBILITY_PRIVATE)
      .apply { if (!current.sound) applySilence() }
      .setPublicVersion(publicVersion)
      .addAction(notificationAction(android.R.drawable.checkbox_on_background, "Marcar como leído", markReadPendingIntent))
      .build()
    synchronized(idLock) { activeMessageIds.add(notificationId) }
    manager().notify(notificationId, notification)
  }

  fun clearConversation(upeerId: String) {
    val notificationId = messageNotificationId(upeerId)
    manager().cancel(notificationId)
    synchronized(idLock) { activeMessageIds.remove(notificationId) }
  }

  fun clearMessages() {
    val ids = synchronized(idLock) {
      val snapshot = activeMessageIds.toList()
      activeMessageIds.clear()
      snapshot
    }
    val manager = manager()
    for (id in ids) manager.cancel(id)
  }

  private fun chatPendingIntent(upeerId: String, requestCode: Int): PendingIntent {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("upeer://chat/$upeerId"))
      .setPackage(context.packageName)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    return PendingIntent.getActivity(context, requestCode, intent, pendingIntentFlags())
  }

  private fun notificationAction(icon: Int, title: String, intent: PendingIntent): Notification.Action =
    Notification.Action.Builder(Icon.createWithResource(context, icon), title, intent).build()

  private fun canPostNotifications(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

  private fun builder(channelId: String): Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    Notification.Builder(context, channelId)
  } else {
    @Suppress("DEPRECATION")
    Notification.Builder(context)
  }

  private fun Notification.Builder.applySilence(): Notification.Builder = setSound(null).setVibrate(null)

  private fun messageChannelId(sound: Boolean): String = if (sound) MESSAGES_CHANNEL_ID else MESSAGES_SILENT_CHANNEL_ID

  private fun manager(): NotificationManager = context.getSystemService(NotificationManager::class.java)

  private fun pendingIntentFlags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

  private fun preview(content: String): String {
    val normalized = content.replace(WHITESPACE, " ").trim()
    return if (normalized.length <= MAX_PREVIEW_LENGTH) normalized else normalized.take(MAX_PREVIEW_LENGTH - 1) + "…"
  }

  private fun messageNotificationId(upeerId: String): Int = MESSAGE_NOTIFICATION_BASE + (upeerId.hashCode() and 0x3fffffff)

  internal companion object {
    const val ACTION_MARK_READ = "app.upeer.transport.MARK_READ"
    const val EXTRA_UPEER_ID = "upeerId"
    private const val TAG = "UPeerTransport"
    private const val CONNECTION_CHANNEL_ID = "upeer_yggdrasil_transport"
    private const val MESSAGES_CHANNEL_ID = "upeer_messages"
    private const val MESSAGES_SILENT_CHANNEL_ID = "upeer_messages_silent"
    private const val MESSAGE_NOTIFICATION_BASE = 10_000
    private const val STOP_REQUEST_CODE = 7302
    private const val MAX_PREVIEW_LENGTH = 240
    private val WHITESPACE = Regex("\\s+")
  }
}
