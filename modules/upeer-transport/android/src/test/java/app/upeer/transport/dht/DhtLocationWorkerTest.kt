package app.upeer.transport.dht

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DhtLocationWorkerTest {
  private val account = "ab".repeat(32)
  private val device = "cd".repeat(32)
  private val publicKey = "11".repeat(32)
  private val registry = """{"accountUpeerId":"$account"}"""
  private val now = 1_800_000_000_000L

  @Test
  fun recoversThePublishedSequenceBeforeBuildingANewBlock() {
    var currentSeq = 0L
    var adopted: Pair<String, Long>? = null
    var storedSeq = -1L
    val worker = worker(
      ready = false,
      find = { block(41) },
      build = { block(++currentSeq) },
      store = { _, value, _ -> storedSeq = value.getLong("dhtSeq"); 1 },
      adopt = { accountId, seq -> adopted = Pair(accountId, seq); currentSeq = seq },
    )

    assertTrue(worker.publish(force = true))
    assertEquals(Pair(account, 41L), adopted)
    assertEquals(42L, storedSeq)
  }

  @Test
  fun startsAtOneWhenNoPublishedBlockExists() {
    var currentSeq = 0L
    var storedSeq = -1L
    val worker = worker(
      ready = false,
      find = { null },
      build = { block(++currentSeq) },
      store = { _, value, _ -> storedSeq = value.getLong("dhtSeq"); 1 },
    )

    assertTrue(worker.publish(force = true))
    assertEquals(1L, storedSeq)
  }

  @Test
  fun failsClosedWhenThePublishedSequenceCannotBeRead() {
    var built = false
    var stored = false
    val worker = worker(
      ready = false,
      find = { throw IllegalStateException("DHT offline") },
      build = { built = true; block(1) },
      store = { _, _, _ -> stored = true; 1 },
    )

    assertFalse(worker.publish(force = true))
    assertFalse(built)
    assertFalse(stored)
  }

  @Test
  fun failsClosedWhenThePublishedBlockCannotBeVerified() {
    var built = false
    val worker = worker(
      ready = false,
      find = { block(41) },
      build = { built = true; block(42) },
      verifyRecovery = false,
    )

    assertFalse(worker.publish(force = true))
    assertFalse(built)
  }

  @Test
  fun skipsRecoveryWhenLocalSequenceStateExists() {
    var currentSeq = 0L
    var storedSeq = -1L
    val worker = worker(
      ready = true,
      find = { throw IllegalStateException("must not be called") },
      build = { block(++currentSeq) },
      store = { _, value, _ -> storedSeq = value.getLong("dhtSeq"); 1 },
    )

    assertTrue(worker.publish(force = true))
    assertEquals(1L, storedSeq)
  }

  private fun worker(
    ready: Boolean,
    find: () -> Any?,
    build: () -> JSONObject,
    store: (String, JSONObject, String) -> Int = { _, _, _ -> 1 },
    adopt: (String, Long) -> Unit = { _, _ -> },
    verifyRecovery: Boolean = true,
  ): DhtLocationWorker = DhtLocationWorker(
    upeerId = { account },
    publicKeyHex = { publicKey },
    buildBlock = { build().toString() },
    store = store,
    find = { find() },
    publicKeyFor = { publicKey },
    registryFor = { registry },
    nowMillis = { now },
    verify = { _, _, registryJSON, _ -> registryJSON.isEmpty() || verifyRecovery },
    sequenceReady = { ready },
    adoptSequence = adopt,
    warn = {},
    info = {},
  )

  private fun block(dhtSeq: Long): JSONObject = JSONObject()
    .put("address", "200::1")
    .put("addresses", JSONArray().put("200::1"))
    .put("dhtSeq", dhtSeq)
    .put("expiresAt", now + 86_400_000L)
    .put("signature", "aa".repeat(64))
    .put("deviceId", device)
    .put(
      "deviceCertificate",
      JSONObject().put("accountUpeerId", account).put("deviceId", device),
    )
}
