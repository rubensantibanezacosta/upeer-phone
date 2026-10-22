package app.upeer.transport.dht

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DhtUpdateAcceptanceStoreTest {
  private val account = "ab".repeat(32)
  private val device = "cd".repeat(32)
  private val publicKey = "11".repeat(32)
  private val now = 1_800_000_000_000L
  private val registry = """{"accountUpeerId":"$account"}"""

  @Test
  fun acceptsOnlyStrictlyNewerSequencesAndPersistsThem() {
    val persistence = MemoryPersistence()
    val store = store(persistence)

    val first = store.accept(block(1).toString(), account, publicKey, registry)
    val second = store.accept(block(2, "200::2").toString(), account, publicKey, registry)
    val duplicate = store.accept(block(2, "200::2").toString(), account, publicKey, registry)

    assertTrue(first!!.changed)
    assertTrue(second!!.changed)
    assertEquals(2L, second.location.dhtSeq)
    assertEquals("200::2", second.location.primaryAddress)
    assertFalse(duplicate!!.changed)
    assertEquals(2L, store.current(account)?.dhtSeq)
    assertEquals(2L, store(persistence).current(account)?.dhtSeq)
  }

  @Test
  fun rejectsRollbackAndAbusiveJumpsWithoutProof() {
    val store = store()
    store.accept(block(10).toString(), account, publicKey, registry)

    assertNull(store.accept(block(9).toString(), account, publicKey, registry))
    assertNull(store.accept(block(10 + DhtSequence.MAX_JUMP + 1).toString(), account, publicKey, registry))
    assertEquals(10L, store.current(account)?.dhtSeq)
  }

  @Test
  fun acceptsALargeJumpOnlyWithAValidProof() {
    val store = DhtUpdateAcceptanceStore(
      MemoryPersistence(),
      { now },
      { _, _, _, _ -> true },
      { proof, upeerId -> proof == "proof" && upeerId == account },
      { message -> println(message) },
    )
    store.accept(block(1).toString(), account, publicKey, registry)

    assertNull(store.accept(block(1 + DhtSequence.MAX_JUMP + 1).toString(), account, publicKey, registry, "bad"))
    val accepted = store.accept(block(1 + DhtSequence.MAX_JUMP + 1).toString(), account, publicKey, registry, "proof")

    assertTrue(accepted!!.changed)
  }

  @Test
  fun tracksSequencesIndependentlyPerDevice() {
    val persistence = MemoryPersistence()
    val store = store(persistence)
    val otherDevice = "ef".repeat(32)

    val firstDevice = store.accept(block(10).toString(), account, publicKey, registry)
    val secondDevice = store.accept(block(2, "200::2", otherDevice).toString(), account, publicKey, registry)

    assertTrue(firstDevice!!.changed)
    assertTrue(secondDevice!!.changed)
    assertEquals(10L, store.current(account, device)?.dhtSeq)
    assertEquals(2L, store.current(account, otherDevice)?.dhtSeq)
    assertEquals(otherDevice, store.current(account)?.deviceId)
    assertNull(store.accept(block(1, "200::2", otherDevice).toString(), account, publicKey, registry))
    assertEquals(2L, store(persistence).current(account, otherDevice)?.dhtSeq)
  }

  @Test
  fun acceptsANewDeviceAfterAnotherDeviceUsedAHigherSequence() {
    val store = store()
    val otherDevice = "ef".repeat(32)
    store.accept(block(50).toString(), account, publicKey, registry)

    val reinstalled = store.accept(block(1, "200::3", otherDevice).toString(), account, publicKey, registry)

    assertTrue(reinstalled!!.changed)
    assertEquals(1L, reinstalled.location.dhtSeq)
    assertEquals(otherDevice, store.current(account)?.deviceId)
    assertEquals(50L, store.current(account, device)?.dhtSeq)
  }

  @Test
  fun migratesVersionOneStateWithoutLosingTheAcceptedLocation() {
    val persistence = MemoryPersistence()
    val legacy = JSONObject()
      .put("version", 1)
      .put(
        "accepted",
        JSONObject().put(
          account,
          JSONObject()
            .put("block", block(12).toString())
            .put("address", "200::1")
            .put("addresses", JSONArray().put("200::1"))
            .put("dhtSeq", 12L)
            .put("deviceId", device)
            .put("expiresAt", now + 86_400_000L)
            .put("updatedAt", now),
        ),
      )
    persistence.save(legacy.toString().toByteArray())

    val store = store(persistence)

    assertEquals(12L, store.current(account)?.dhtSeq)
    assertEquals(device, store.current(account)?.deviceId)
  }

  @Test
  fun rejectsMissingCertificatesWrongAccountsAndInvalidSignatures() {
    val store = store()
    val withoutCertificate = block(1).apply { remove("deviceCertificate") }
    val otherAccount = block(1).put(
      "deviceCertificate",
      JSONObject().put("accountUpeerId", "bc".repeat(32)).put("deviceId", device),
    )
    val unverifiable = DhtUpdateAcceptanceStore(
      MemoryPersistence(),
      { now },
      { _, _, _, _ -> false },
    )

    assertNull(store.accept(withoutCertificate.toString(), account, publicKey, registry))
    assertNull(store.accept(otherAccount.toString(), account, publicKey, registry))
    assertNull(unverifiable.accept(block(1).toString(), account, publicKey, registry))
  }

  @Test
  fun requiresAnAccountRegistry() {
    assertNull(store().accept(block(1).toString(), account, publicKey, ""))
  }

  private fun store(persistence: MemoryPersistence = MemoryPersistence()) = DhtUpdateAcceptanceStore(
    persistence,
    { now },
    { block, publicKeyHex, registryJSON, at ->
      publicKeyHex == publicKey && registryJSON == registry && at == now &&
        JSONObject(block).getJSONObject("deviceCertificate").getString("accountUpeerId") == account
    },
    log = { message -> println(message) },
  )

  private fun block(dhtSeq: Long, address: String = "200::1", deviceId: String = device): JSONObject = JSONObject()
    .put("address", address)
    .put("addresses", JSONArray().put(address))
    .put("dhtSeq", dhtSeq)
    .put("expiresAt", now + 86_400_000L)
    .put("signature", "aa".repeat(64))
    .put("deviceId", deviceId)
    .put(
      "deviceCertificate",
      JSONObject().put("accountUpeerId", account).put("deviceId", deviceId),
    )

  private class MemoryPersistence : DhtUpdateAcceptancePersistence {
    private var value: ByteArray? = null

    override fun load(): ByteArray? = value?.copyOf()

    override fun save(value: ByteArray) {
      this.value = value.copyOf()
    }

    override fun clear() {
      value = null
    }
  }
}
