package app.upeer.transport.dht

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DhtUpdateServiceTest {
  private val account = "ab".repeat(32)
  private val device = "cd".repeat(32)
  private val publicKey = "11".repeat(32)
  private val now = 1_800_000_000_000L
  private val registry = """{"accountUpeerId":"$account"}"""

  @Test
  fun appliesANewerBlockAndSkipsDuplicates() {
    val applied = mutableListOf<DhtAcceptedLocation>()
    val service = service(applied)
    val block = block(3)

    assertTrue(service.handle(account, publicKey, block, "200::9"))
    assertTrue(service.handle(account, publicKey, block, "200::9"))

    assertTrue(applied.single().dhtSeq == 3L)
    assertTrue(applied.single().primaryAddress == "200::1")
  }

  @Test
  fun rejectsUpdatesWithoutARegistryAndRequestsARefresh() {
    val requested = mutableListOf<String>()
    val service = DhtUpdateService(
      acceptance(),
      { null },
      { _ -> error("must not accept") },
      { requested += it },
      { message -> println(message) },
    )

    assertFalse(service.handle(account, publicKey, block(1), "200::9"))
    assertTrue(requested.single() == account)
  }

  @Test
  fun rejectsInvalidSendersAndBlocks() {
    val applied = mutableListOf<DhtAcceptedLocation>()
    val service = service(applied)

    assertFalse(service.handle("bad", publicKey, block(1), "200::9"))
    assertFalse(service.handle(account, publicKey, block(1).apply { remove("deviceCertificate") }, "200::9"))
    assertTrue(applied.isEmpty())
  }

  private fun service(applied: MutableList<DhtAcceptedLocation>) = DhtUpdateService(
    acceptance(),
    { registry },
    { applied += it },
    log = { message -> println(message) },
  )

  private fun acceptance() = DhtUpdateAcceptanceStore(
    object : DhtUpdateAcceptancePersistence {
      private var value: ByteArray? = null
      override fun load(): ByteArray? = value?.copyOf()
      override fun save(value: ByteArray) { this.value = value.copyOf() }
      override fun clear() { value = null }
    },
    { now },
    { block, key, expectedRegistry, at ->
      key == publicKey && expectedRegistry == registry && at == now &&
        JSONObject(block).getJSONObject("deviceCertificate").getString("accountUpeerId") == account
    },
    log = { message -> println(message) },
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
