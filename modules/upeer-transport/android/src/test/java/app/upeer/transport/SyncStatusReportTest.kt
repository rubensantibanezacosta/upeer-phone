package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStatusReportTest {
  private val now = 1_700_000_000_000L
  private val peer = SyncPeerRecord(
    deviceId = "bb".repeat(32),
    address = "200::1",
    devicePublicKey = "cc".repeat(32),
    certificateJSON = "{}",
    signedPreKey = org.json.JSONObject(),
    lastSeen = now,
  )

  @Test
  fun `no peers and no progress is idle`() {
    val report = SyncStatusReport.json(emptyList(), emptyList(), 0, 0, now)

    assertEquals(SyncStatusReport.STATE_IDLE, report.optString("state"))
    assertEquals(0, report.optInt("devices"))
    assertEquals(0L, report.optLong("lastPeerAt"))
  }

  @Test
  fun `un historico sin totales conocidos no bloquea la app`() {
    val report = SyncStatusReport.json(
      listOf(SyncHistoryProgress(cursor = null, done = false)),
      listOf(peer),
      0,
      0,
      now,
    )

    assertEquals(SyncStatusReport.STATE_LIVE, report.optString("state"))
  }

  @Test
  fun `un historico conocido a medias mantiene la sincronizacion`() {
    val report = SyncStatusReport.json(
      listOf(SyncHistoryProgress(cursor = "1:a", done = false, applied = 10, total = 40, known = true)),
      listOf(peer),
      0,
      0,
      now,
    )

    assertEquals(SyncStatusReport.STATE_SYNCING, report.optString("state"))
  }

  @Test
  fun `un historico a medias con el peer perdido no deja la app bloqueada`() {
    val report = SyncStatusReport.json(
      listOf(SyncHistoryProgress(cursor = "1:a", done = false, applied = 10, total = 40, known = true)),
      listOf(peer.copy(lastSeen = now - 120_000L)),
      0,
      0,
      now,
    )

    assertEquals(SyncStatusReport.STATE_LIVE, report.optString("state"))
  }

  @Test
  fun `el gate de arranque solo bloquea mientras falta historico`() {
    val booting = SyncStatusReport.json(
      listOf(SyncHistoryProgress(cursor = "1:a", done = false, applied = 10, total = 40, known = true)),
      listOf(peer),
      0,
      0,
      now,
      gate = true,
    )
    assertEquals(SyncStatusReport.GATE_BOOT, booting.optString("gate"))

    val ready = SyncStatusReport.json(
      listOf(SyncHistoryProgress(cursor = "1:a", done = true)),
      listOf(peer),
      0,
      0,
      now,
      gate = true,
    )
    assertEquals(SyncStatusReport.GATE_OPEN, ready.optString("gate"))
  }

  @Test
  fun `un historico a medias sin peer vivo no retiene el gate de arranque`() {
    val report = SyncStatusReport.json(
      listOf(SyncHistoryProgress(cursor = "1:a", done = false, applied = 10, total = 40, known = true)),
      listOf(peer.copy(lastSeen = now - 120_000L)),
      0,
      0,
      now,
      gate = true,
    )

    assertEquals(SyncStatusReport.GATE_OPEN, report.optString("gate"))
  }

  @Test
  fun `sin peers conocidos el gate de arranque sigue tapando la app`() {
    val report = SyncStatusReport.json(emptyList(), emptyList(), 0, 0, now, gate = true)

    assertEquals(SyncStatusReport.GATE_BOOT, report.optString("gate"))
  }

  @Test
  fun `los adjuntos pendientes no mantienen el gate de arranque`() {
    val report = SyncStatusReport.json(
      listOf(SyncHistoryProgress(cursor = "1:a", done = true)),
      listOf(peer),
      0,
      0,
      now,
      files = SyncFileWork(total = 5, done = 0, pending = 5),
      gate = true,
    )

    assertEquals(SyncStatusReport.STATE_SYNCING, report.optString("state"))
    assertEquals(SyncStatusReport.GATE_OPEN, report.optString("gate"))
  }

  @Test
  fun `a pending history with a fresh peer is syncing`() {
    val report = SyncStatusReport.json(
      listOf(SyncHistoryProgress(cursor = null, done = false)),
      listOf(peer),
      4,
      0,
      now,
    )

    assertEquals(SyncStatusReport.STATE_SYNCING, report.optString("state"))
    assertEquals(1, report.optInt("devices"))
    assertEquals(0, report.optInt("devicesDone"))
    assertEquals(4, report.optInt("pendingEvents"))
  }

  @Test
  fun `a pending history with a stale peer is live instead of spinning forever`() {
    val stale = peer.copy(lastSeen = now - 120_000L)
    val report = SyncStatusReport.json(
      listOf(SyncHistoryProgress(cursor = null, done = false)),
      listOf(stale),
      0,
      0,
      now,
    )

    assertEquals(SyncStatusReport.STATE_LIVE, report.optString("state"))
    assertEquals(120_000L, report.optLong("ageMillis"))
  }

  @Test
  fun `every history done is live`() {
    val report = SyncStatusReport.json(
      listOf(SyncHistoryProgress(cursor = "1:a", done = true)),
      listOf(peer),
      0,
      0,
      now,
    )

    assertEquals(SyncStatusReport.STATE_LIVE, report.optString("state"))
    assertEquals(1, report.optInt("devicesDone"))
  }

  @Test
  fun `unsupported events are reported so the ui can say the sync is partial`() {
    val report = SyncStatusReport.json(
      listOf(SyncHistoryProgress(cursor = "1:a", done = true)),
      listOf(peer),
      0,
      7,
      now,
    )

    assertEquals(7, report.optInt("unsupported"))
    assertTrue(report.optString("state") == SyncStatusReport.STATE_LIVE)
  }

  @Test
  fun `unsupported events carry their kind so the banner is not generic`() {
    val report = SyncStatusReport.json(
      listOf(SyncHistoryProgress(cursor = "1:a", done = true)),
      listOf(peer),
      0,
      3,
      now,
      unsupportedKinds = org.json.JSONObject().put("attachment", 3),
    )

    val kinds = report.optJSONObject("unsupportedKinds")
    assertEquals(3, kinds?.optInt("attachment"))
    assertEquals(0, kinds?.optInt("group") ?: 0)
  }

  @Test
  fun `the unsupported summary is never null`() {
    val report = SyncStatusReport.json(
      listOf(SyncHistoryProgress(cursor = "1:a", done = true)),
      listOf(peer),
      0,
      0,
      now,
    )

    assertEquals(0, report.optJSONObject("unsupportedKinds")?.length())
  }

  @Test
  fun `pending attachments keep the state syncing even when the history is done`() {
    val report = SyncStatusReport.json(
      listOf(SyncHistoryProgress(cursor = "1:a", done = true)),
      listOf(peer),
      0,
      0,
      now,
      files = SyncFileWork(total = 3, done = 1, pending = 2),
    )

    assertEquals(SyncStatusReport.STATE_SYNCING, report.optString("state"))
    val progress = report.optJSONObject("progress")
    assertEquals(2, progress?.optInt("pendingFiles"))
    assertEquals(1, progress?.optInt("filesDone"))
    assertEquals(3, progress?.optInt("filesTotal"))
  }

  @Test
  fun `attachments already downloaded leave the state live`() {
    val report = SyncStatusReport.json(
      listOf(SyncHistoryProgress(cursor = "1:a", done = true)),
      listOf(peer),
      0,
      0,
      now,
      files = SyncFileWork(total = 3, done = 3),
    )

    assertEquals(SyncStatusReport.STATE_LIVE, report.optString("state"))
    assertEquals(100, report.optJSONObject("progress")?.optInt("percent"))
  }
}
