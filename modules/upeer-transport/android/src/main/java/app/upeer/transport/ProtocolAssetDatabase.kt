package app.upeer.transport

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

/**
 * Persistencia de las tablas de resiliencia del vault: `distributed_assets` y `redundancy_health`.
 *
 * Son el equivalente de `upper-desktop/src/main_process/storage/vault/asset-operations.ts`: cada
 * fragmento de fichero repartido entre amigos se anota con su custodio, y se guarda el estado de
 * salud del conjunto (cuántos fragmentos quedan disponibles frente a los necesarios).
 */
internal class ProtocolAssetDatabase(private val db: ProtocolDatabase) {
  fun trackAsset(
    fileHash: String,
    cid: String,
    shardIndex: Int,
    totalShards: Int,
    custodianSid: String,
    segmentIndex: Int = 0,
  ): Boolean {
    val values = ContentValues()
    values.put("file_hash", fileHash)
    values.put("cid", cid)
    values.put("shard_index", shardIndex)
    values.put("total_shards", totalShards)
    values.put("custodian_sid", custodianSid)
    values.put("segment_index", segmentIndex)
    values.put("status", "active")
    values.put("last_verified", System.currentTimeMillis())
    return db.database().insertWithOnConflict("distributed_assets", null, values, SQLiteDatabase.CONFLICT_REPLACE) > -1
  }

  fun markAssetLost(cid: String) {
    val values = ContentValues()
    values.put("status", "lost")
    db.database().update("distributed_assets", values, "cid = ?", arrayOf(cid))
  }

  fun custodianOf(cid: String): String? =
    db.database().rawQuery("SELECT custodian_sid FROM distributed_assets WHERE cid = ?", arrayOf(cid)).use {
      if (it.moveToFirst()) it.getString(0) else null
    }

  fun assetCount(fileHash: String): Int = db.database()
    .rawQuery("SELECT COUNT(*) FROM distributed_assets WHERE file_hash = ? AND status = 'active'", arrayOf(fileHash))
    .use { if (it.moveToFirst()) it.getInt(0) else 0 }

  fun assets(): List<JSONObject> {
    val found = ArrayList<JSONObject>()
    db.database().rawQuery("SELECT * FROM distributed_assets", null).use { cursor ->
      while (cursor.moveToNext()) {
        found.add(
          JSONObject()
            .put("fileHash", cursor.getString(1))
            .put("cid", cursor.getString(2))
            .put("segmentIndex", cursor.getLong(3))
            .put("shardIndex", cursor.getLong(4))
            .put("totalShards", cursor.getLong(5))
            .put("custodianSid", cursor.getString(6))
            .put("status", cursor.getString(7)),
        )
      }
    }
    return found
  }

  fun assetClear() = db.database().execSQL("DELETE FROM distributed_assets")

  /** Registra el estado de salud de un fichero repartido (`redundancy_health`). */
  fun updateHealth(assetHash: String, available: Int, required: Int) {
    val values = ContentValues()
    values.put("asset_hash", assetHash)
    values.put("available_shards", available)
    values.put("required_shards", required)
    values.put("health_status", healthOf(available, required))
    values.put("last_check", System.currentTimeMillis())
    db.database().insertWithOnConflict("redundancy_health", null, values, SQLiteDatabase.CONFLICT_REPLACE)
  }

  fun health(assetHash: String): JSONObject? =
    db.database().rawQuery("SELECT * FROM redundancy_health WHERE asset_hash = ?", arrayOf(assetHash)).use {
      if (!it.moveToNext()) return null
      JSONObject()
        .put("assetHash", it.getString(1))
        .put("availableShards", it.getLong(2))
        .put("requiredShards", it.getLong(3))
        .put("healthStatus", it.getString(4))
        .put("lastCheck", it.getLong(5))
    }

  fun healthClear() = db.database().execSQL("DELETE FROM redundancy_health")

  /** Mismo criterio que `healthStatus` del escritorio. */
  private fun healthOf(available: Int, required: Int): String = when {
    available == 0 -> "lost"
    available < required -> "critical"
    available == required -> "degraded"
    else -> "perfect"
  }
}
