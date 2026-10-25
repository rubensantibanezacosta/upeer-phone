package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRegistryDevicesTest {
  private val account = "a".repeat(64)

  private fun registry(vararg deviceIds: String, accountUpeerId: String = account): String {
    val devices = deviceIds.joinToString(",") { """{"deviceId":"$it"}""" }
    return """{"accountUpeerId":"$accountUpeerId","devices":[$devices]}"""
  }

  @Test
  fun readsDeviceIdsForTheExpectedAccount() {
    assertEquals(setOf("d1", "d2"), AccountRegistryDevices.deviceIds(registry("d1", "d2"), account))
  }

  @Test
  fun ignoresRegistriesFromAnotherAccount() {
    val other = registry("d1", accountUpeerId = "b".repeat(64))

    assertTrue(AccountRegistryDevices.deviceIds(other, account).isEmpty())
  }

  @Test
  fun toleratesMissingOrBrokenRegistries() {
    assertTrue(AccountRegistryDevices.deviceIds(null, account).isEmpty())
    assertTrue(AccountRegistryDevices.deviceIds("", account).isEmpty())
    assertTrue(AccountRegistryDevices.deviceIds("no-json", account).isEmpty())
    assertTrue(AccountRegistryDevices.deviceIds("""{"accountUpeerId":"$account"}""", account).isEmpty())
  }

  @Test
  fun reportsDevicesTheLocalRegistryDoesNotKnow() {
    val local = registry("d1")
    val published = registry("d1", "d2", "d3")

    assertEquals(setOf("d2", "d3"), AccountRegistryDevices.unknownDevices(local, published, account))
  }

  @Test
  fun reportsNothingWhenTheLocalRegistryCoversEveryPublishedDevice() {
    val local = registry("d1", "d2")
    val published = registry("d1", "d2")

    assertTrue(AccountRegistryDevices.unknownDevices(local, published, account).isEmpty())
  }

  @Test
  fun reportsNothingWhenNothingIsPublishedYet() {
    assertTrue(AccountRegistryDevices.unknownDevices(registry("d1"), null, account).isEmpty())
    assertTrue(AccountRegistryDevices.unknownDevices(registry("d1"), "", account).isEmpty())
  }

  @Test
  fun comparesDeviceIdsIgnoringCase() {
    val local = registry("D1")
    val published = registry("d1")

    assertTrue(AccountRegistryDevices.unknownDevices(local, published, account).isEmpty())
  }
}
