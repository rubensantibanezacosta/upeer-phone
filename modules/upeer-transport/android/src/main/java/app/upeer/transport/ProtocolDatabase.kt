package app.upeer.transport

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

/**
 * Base de datos SQLite del protocolo.
 *
 * Réplica del esquema de almacenamiento de `upper-desktop` (`src/main_process/storage/schema.ts`):
 * las mismas 17 tablas, con los mismos nombres de tabla y de columna, para que ambos clientes
 * guarden los mismos datos en el mismo sitio.
 *
 * A diferencia del escritorio no se usa SQLCipher (añadiría una dependencia nativa nueva): las
 * columnas que contienen material sensible (`ratchet_sessions.state`, `pending_outbox.plaintext`,
 * `vault_storage.data`) guardan su valor cifrado con la clave del AndroidKeyStore mediante
 * [ProtocolCipher]. El resto del contenido ya viaja cifrado de extremo a extremo.
 */
internal class ProtocolDatabase(context: Context) {
  private val file = File(context.filesDir, DATABASE_NAME)
  private val gate = Any()
  private var connection: SQLiteDatabase? = null

  fun database(): SQLiteDatabase = synchronized(gate) {
    connection ?: SQLiteDatabase.openOrCreateDatabase(file, null).also { opened ->
      // `journal_mode` devuelve una fila con el modo aplicado: hay que consultarlo, no ejecutarlo.
      opened.rawQuery("PRAGMA journal_mode = WAL", null).use { it.moveToFirst() }
      opened.execSQL("PRAGMA synchronous = NORMAL")
      opened.execSQL("PRAGMA foreign_keys = ON")
      createSchema(opened)
      upgradeSchema(opened)
      connection = opened
      Log.i(TAG, "Base de datos del protocolo abierta (v${opened.version})")
    }
  }

  fun close() {
    synchronized(gate) {
      connection?.close()
      connection = null
    }
  }

  fun fileSize(): Long = if (file.exists()) file.length() else 0L

  private fun createSchema(db: SQLiteDatabase) {
    // app_settings
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS app_settings (" +
        "key TEXT PRIMARY KEY, value TEXT NOT NULL, updated_at INTEGER NOT NULL)",
    )
    // groups
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS groups (" +
        "group_id TEXT PRIMARY KEY, name TEXT NOT NULL, admin_upeer_id TEXT NOT NULL, " +
        "members TEXT NOT NULL DEFAULT '[]', is_favorite INTEGER NOT NULL DEFAULT 0, " +
        "status TEXT NOT NULL DEFAULT 'active', avatar TEXT, epoch INTEGER NOT NULL DEFAULT 1, " +
        "sender_key TEXT, sender_key_created_at INTEGER, member_public_keys TEXT NOT NULL DEFAULT '{}', " +
        "created_at INTEGER NOT NULL, last_cleared_at INTEGER NOT NULL DEFAULT 0)",
    )
    // messages
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS messages (" +
        "id TEXT PRIMARY KEY, chat_upeer_id TEXT NOT NULL, sender_upeer_id TEXT, " +
        "is_mine INTEGER NOT NULL, message TEXT NOT NULL, reply_to TEXT, signature TEXT, " +
        "status TEXT NOT NULL DEFAULT 'sent', version INTEGER NOT NULL DEFAULT 1, " +
        "is_deleted INTEGER NOT NULL DEFAULT 0, is_edited INTEGER NOT NULL DEFAULT 0, " +
        "timestamp INTEGER NOT NULL, expires_at INTEGER, delivered_to TEXT)",
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS messages_chat_ts_idx ON messages(chat_upeer_id, timestamp)")
    db.execSQL("CREATE INDEX IF NOT EXISTS messages_ts_idx ON messages(timestamp)")
    db.execSQL("CREATE INDEX IF NOT EXISTS messages_expires_idx ON messages(expires_at)")
    // reactions
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS reactions (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, message_id TEXT NOT NULL, upeer_id TEXT NOT NULL, " +
        "emoji TEXT NOT NULL, timestamp INTEGER NOT NULL)",
    )
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS reactions_unique_idx ON reactions(message_id, upeer_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS reactions_message_idx ON reactions(message_id)")
    // contacts
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS contacts (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, upeer_id TEXT UNIQUE, address TEXT NOT NULL, " +
        "name TEXT NOT NULL, is_favorite INTEGER NOT NULL DEFAULT 0, public_key TEXT, " +
        "ephemeral_public_key TEXT, ephemeral_public_key_updated_at TEXT, signed_pre_key TEXT, " +
        "signed_pre_key_signature TEXT, signed_pre_key_id INTEGER, device_public_key TEXT, device_id TEXT, " +
        "device_certificate TEXT, device_signed_pre_key TEXT, device_signed_pre_key_signature TEXT, " +
        "device_signed_pre_key_id INTEGER, dht_seq INTEGER NOT NULL DEFAULT 0, dht_signature TEXT, " +
        "dht_expires_at INTEGER, renewal_token TEXT, known_addresses TEXT NOT NULL DEFAULT '[]', " +
        "avatar TEXT, status TEXT NOT NULL DEFAULT 'connected', blocked_at TEXT, last_seen TEXT, " +
        "last_active TEXT, last_cleared_at INTEGER NOT NULL DEFAULT 0)",
    )
    // backup_pulse_sync
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS backup_pulse_sync (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, kit_id TEXT UNIQUE NOT NULL, name TEXT NOT NULL, " +
        "description TEXT, data TEXT NOT NULL, created TEXT, expires INTEGER, is_active INTEGER NOT NULL DEFAULT 1)",
    )
    // vault_storage
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS vault_storage (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, payload_hash TEXT UNIQUE NOT NULL, " +
        "recipient_sid TEXT NOT NULL, recipient_device_id TEXT, sender_sid TEXT NOT NULL, " +
        "priority INTEGER NOT NULL DEFAULT 1, data TEXT NOT NULL, expires_at INTEGER NOT NULL, timestamp TEXT)",
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS vault_expires_idx ON vault_storage(expires_at)")
    // distributed_assets
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS distributed_assets (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, file_hash TEXT NOT NULL, cid TEXT UNIQUE NOT NULL, " +
        "segment_index INTEGER NOT NULL DEFAULT 0, shard_index INTEGER NOT NULL, " +
        "total_shards INTEGER NOT NULL, custodian_sid TEXT NOT NULL, " +
        "status TEXT NOT NULL DEFAULT 'active', last_verified INTEGER)",
    )
    // redundancy_health
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS redundancy_health (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, asset_hash TEXT UNIQUE NOT NULL, " +
        "available_shards INTEGER NOT NULL, required_shards INTEGER NOT NULL, " +
        "health_status TEXT NOT NULL, last_check INTEGER)",
    )
    // reputation_vouches
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS reputation_vouches (" +
        "id TEXT PRIMARY KEY, from_id TEXT NOT NULL, to_id TEXT NOT NULL, type TEXT NOT NULL, " +
        "positive INTEGER NOT NULL, timestamp INTEGER NOT NULL, signature TEXT NOT NULL, " +
        "received_at INTEGER NOT NULL)",
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS rep_vouches_to_idx ON reputation_vouches(to_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS rep_vouches_from_idx ON reputation_vouches(from_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS rep_vouches_ts_idx ON reputation_vouches(timestamp)")
    // ratchet_sessions
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS ratchet_sessions (" +
        "upeer_id TEXT NOT NULL, context_id TEXT NOT NULL DEFAULT 'direct', state TEXT NOT NULL, " +
        "spk_id_used INTEGER, established_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, " +
        "PRIMARY KEY (upeer_id, context_id))",
    )
    // pending_outbox
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS pending_outbox (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, msg_id TEXT NOT NULL, recipient_sid TEXT NOT NULL, " +
        "plaintext TEXT NOT NULL, reply_to TEXT, created_at INTEGER NOT NULL, " +
        // Columnas propias del movil: el outbox necesita distinguir CHAT de CHAT_UPDATE y
        // recordar el estado de los reintentos, que el escritorio guarda solo con los campos base.
        "kind TEXT NOT NULL DEFAULT 'CHAT', target_msg_id TEXT, version INTEGER NOT NULL DEFAULT 0, " +
        "attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at INTEGER NOT NULL DEFAULT 0, " +
        "sends INTEGER NOT NULL DEFAULT 0, timestamp INTEGER NOT NULL DEFAULT 0, " +
        "last_error TEXT, sent_at INTEGER, packet TEXT)",
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS pending_outbox_recipient_idx ON pending_outbox(recipient_sid)")
    // devices
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS devices (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, upeer_id TEXT NOT NULL, device_id TEXT NOT NULL, " +
        "client_name TEXT, platform TEXT, client_version TEXT, last_seen INTEGER NOT NULL, " +
        "is_trusted INTEGER NOT NULL DEFAULT 1)",
    )
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS devices_unique_idx ON devices(upeer_id, device_id)")
    // sync_peers
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS sync_peers (" +
        "device_id TEXT PRIMARY KEY, upeer_id TEXT NOT NULL, device_public_key TEXT NOT NULL, " +
        "device_certificate TEXT NOT NULL, device_signed_pre_key TEXT NOT NULL, " +
        "device_signed_pre_key_signature TEXT NOT NULL, device_signed_pre_key_id INTEGER NOT NULL, " +
        "address TEXT, last_seen INTEGER NOT NULL, last_sequence INTEGER NOT NULL DEFAULT 0, " +
        "ack_sequence INTEGER NOT NULL DEFAULT 0, history_cursor TEXT, " +
        "history_completed INTEGER NOT NULL DEFAULT 0)",
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS sync_peers_upeer_idx ON sync_peers(upeer_id)")
    // sync_events
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS sync_events (" +
        "event_id TEXT PRIMARY KEY, origin_device_id TEXT NOT NULL, kind TEXT NOT NULL, " +
        "occurred_at INTEGER NOT NULL, sequence INTEGER NOT NULL DEFAULT 0, payload TEXT NOT NULL, " +
        "origin_signature TEXT, origin_certificate TEXT, entity_id TEXT, " +
        "direction TEXT NOT NULL, applied_at INTEGER NOT NULL)",
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS sync_events_origin_idx ON sync_events(origin_device_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS sync_events_entity_idx ON sync_events(entity_id, direction)")
    db.execSQL("CREATE INDEX IF NOT EXISTS sync_events_occurred_idx ON sync_events(occurred_at)")
    db.execSQL("CREATE INDEX IF NOT EXISTS sync_events_seq_idx ON sync_events(sequence)")
    // sync_relay
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS sync_relay (" +
        "event_id TEXT NOT NULL, target_device_id TEXT NOT NULL, sequence INTEGER NOT NULL DEFAULT 0, " +
        "created_at INTEGER NOT NULL, acked_at INTEGER, PRIMARY KEY (event_id, target_device_id))",
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS sync_relay_target_idx ON sync_relay(target_device_id, acked_at)")
    // _app_flags (control de migraciones one-time, igual que en escritorio)
    db.execSQL("CREATE TABLE IF NOT EXISTS _app_flags (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
  }

  /** Tablas del esquema, en el mismo orden que `upper-desktop/src/main_process/storage/schema.ts`. */
  fun tables(): List<String> = TABLES

  /**
   * Ajustes sobre una base de datos creada por una versión anterior del esquema.
   *
   * `CREATE TABLE IF NOT EXISTS` no toca una tabla que ya existe, así que los cambios de columnas
   * se aplican aquí, consultando `PRAGMA table_info` antes de cada uno.
   */
  private fun upgradeSchema(db: SQLiteDatabase) {
    renameColumn(db, "contacts", "signed_pre_key_sig", "signed_pre_key_signature")
    renameColumn(db, "contacts", "device_signed_pre_key_sig", "device_signed_pre_key_signature")
    renameColumn(db, "sync_peers", "device_signed_pre_key_sig", "device_signed_pre_key_signature")
    // Tabla creada por una version anterior y quitada del esquema: el escritorio no la tiene.
    db.execSQL("DROP TABLE IF EXISTS usage")
    for ((column, definition) in OUTBOX_EXTRA_COLUMNS) {
      addColumn(db, "pending_outbox", column, definition)
    }
  }

  private fun renameColumn(db: SQLiteDatabase, table: String, from: String, to: String) {
    val existing = columns(db, table)
    if (!existing.contains(from) || existing.contains(to)) return
    // `ALTER TABLE ... RENAME COLUMN` necesita SQLite 3.25+, que no está en todas las versiones de
    // Android: se añade la columna nueva y se copia el valor, que funciona en cualquier versión.
    runCatching {
      db.execSQL("ALTER TABLE $table ADD COLUMN $to TEXT")
      db.execSQL("UPDATE $table SET $to = $from")
    }.onFailure { cause ->
      Log.w(TAG, "No se pudo migrar $table.$from a $to", cause)
    }
  }

  private fun addColumn(db: SQLiteDatabase, table: String, column: String, definition: String) {
    if (columns(db, table).contains(column)) return
    runCatching { db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition") }
      .onFailure { cause -> Log.w(TAG, "No se pudo añadir $table.$column", cause) }
  }

  private fun columns(db: SQLiteDatabase, table: String): Set<String> =
    db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
      val names = HashSet<String>()
      while (cursor.moveToNext()) names.add(cursor.getString(1))
      names
    }

  companion object {
    const val DATABASE_NAME = "p2p-chat.db"

    val TABLES = listOf(
      "app_settings",
      "groups",
      "messages",
      "reactions",
      "contacts",
      "backup_pulse_sync",
      "vault_storage",
      "distributed_assets",
      "redundancy_health",
      "reputation_vouches",
      "ratchet_sessions",
      "pending_outbox",
      "devices",
      "sync_peers",
      "sync_events",
      "sync_relay",
    )

    private val OUTBOX_EXTRA_COLUMNS = listOf(
      "kind" to "TEXT NOT NULL DEFAULT 'CHAT'",
      "target_msg_id" to "TEXT",
      "version" to "INTEGER NOT NULL DEFAULT 0",
      "attempts" to "INTEGER NOT NULL DEFAULT 0",
      "next_attempt_at" to "INTEGER NOT NULL DEFAULT 0",
      "sends" to "INTEGER NOT NULL DEFAULT 0",
      "timestamp" to "INTEGER NOT NULL DEFAULT 0",
      "last_error" to "TEXT",
      "sent_at" to "INTEGER",
      "packet" to "TEXT",
    )

    private const val TAG = "UPeerTransport"
  }
}
