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
}
