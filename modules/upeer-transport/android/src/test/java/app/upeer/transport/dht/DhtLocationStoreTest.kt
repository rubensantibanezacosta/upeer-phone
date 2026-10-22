package app.upeer.transport.dht

import app.upeer.transport.dht.DhtLocation.isHex
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DhtLocationStoreTest {
  private val upeerId = "8d2d1c260127c74476b27136c5e38c003b66b889f5c80032fb81ebc3f44f45a3"
  private val address = "200:9f8e:7d6c:5b4a:3928:1706:f5e4:d3c2"
  private val otherAddress = "200:1111:2222:3333:4444:5555:6666:7777"

  @Test
  fun incrementsSequenceOnlyWhenAddressChangesAndPersistsAcrossRestart() {
    val persistence = MemoryPersistence()
    val store = DhtLocationStore(persistence)
    assertEquals(1L, store.observeAddress(upeerId, address))
    assertEquals(1L, store.observeAddress(upeerId, address))
    assertEquals(1L, store.observeAddress(upeerId, " $address "))
    val restarted = DhtLocationStore(persistence)
    assertEquals(1L, restarted.observeAddress(upeerId, address))
    assertEquals(2L, restarted.observeAddress(upeerId, otherAddress))
    val status = JSONObject(restarted.statusJSON(upeerId))
    assertTrue(status.getBoolean("ready"))
    assertEquals(2L, status.getLong("dhtSeq"))
    assertEquals(otherAddress, status.getString("lastAddress"))
    assertEquals(upeerId, status.getString("upeerId"))
  }

  @Test
  fun resetsSequenceForANewIdentityAndPersistsIt() {
    val persistence = MemoryPersistence()
    val store = DhtLocationStore(persistence)
    assertFalse(JSONObject(store.statusJSON(upeerId)).getBoolean("ready"))
    store.observeAddress(upeerId, address)
    store.observeAddress(upeerId, otherAddress)
    val otherUpeerId = "11".repeat(32)
    assertEquals(1L, store.observeAddress(otherUpeerId, address))
    assertFalse(JSONObject(store.statusJSON(upeerId)).getBoolean("ready"))
    assertEquals(1L, JSONObject(store.statusJSON(otherUpeerId)).getLong("dhtSeq"))
    val restarted = DhtLocationStore(persistence)
    assertEquals(1L, JSONObject(restarted.statusJSON(otherUpeerId)).getLong("dhtSeq"))
  }

  @Test
  fun clearIdentityResetsSequence() {
    val persistence = MemoryPersistence()
    val store = DhtLocationStore(persistence)
    store.observeAddress(upeerId, address)
    store.observeAddress(upeerId, otherAddress)
    store.clearIdentity()
    assertFalse(JSONObject(store.statusJSON(upeerId)).getBoolean("ready"))
    val restarted = DhtLocationStore(persistence)
    assertEquals(1L, restarted.observeAddress(upeerId, address))
    assertNotEquals("", restarted.statusJSON(upeerId))
  }

  @Test
  fun adoptsARecoveredSequenceWhenLocalStateIsMissing() {
    val persistence = MemoryPersistence()
    val store = DhtLocationStore(persistence)
    assertFalse(store.hasState(upeerId))

    assertEquals(41L, store.adoptSequence(upeerId, 41))
    assertTrue(store.hasState(upeerId))
    assertEquals(42L, store.observeAddress(upeerId, address))
    assertEquals(43L, store.observeAddress(upeerId, otherAddress))

    val restarted = DhtLocationStore(persistence)
    assertTrue(restarted.hasState(upeerId))
    assertEquals(43L, JSONObject(restarted.statusJSON(upeerId)).getLong("dhtSeq"))
  }

  @Test
  fun adoptedSequenceNeverMovesTheLocalStateBackwards() {
    val store = DhtLocationStore(MemoryPersistence())
    store.observeAddress(upeerId, address)
    store.observeAddress(upeerId, otherAddress)

    assertEquals(2L, store.adoptSequence(upeerId, 1))
    assertEquals(2L, store.observeAddress(upeerId, otherAddress))
  }

  @Test
  fun statusCarriesTheDeviceIdentifierProvidedByTheIdentityStore() {
    val store = DhtLocationStore(MemoryPersistence())
    store.observeAddress(upeerId, address)
    val deviceId = "ab".repeat(32)
    val status = JSONObject(store.statusJSON(upeerId, deviceId))
    assertTrue(status.getBoolean("ready"))
    assertEquals(deviceId, status.getString("deviceId"))
    assertTrue(isHex(status.getString("deviceId"), 32))
  }

  @Test
  fun statusWithoutIdentityIsNeverReady() {
    val store = DhtLocationStore(MemoryPersistence())
    val status = JSONObject(store.statusJSON(null))
    assertFalse(status.getBoolean("ready"))
    assertEquals(0L, status.getLong("dhtSeq"))
  }

  private class MemoryPersistence : DhtLocationPersistence {
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
