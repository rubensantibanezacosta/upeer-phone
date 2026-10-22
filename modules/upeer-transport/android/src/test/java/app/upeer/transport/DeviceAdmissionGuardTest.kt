package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAdmissionGuardTest {
  @Test
  fun allowsUnknownAndAdmittedDevices() {
    val guard = DeviceAdmissionGuard({ null }, { throw AssertionError("unexpected revocation") })
    assertTrue(guard.isAdmitted())
    guard.require()

    val admitted = DeviceAdmissionGuard({ true }, { throw AssertionError("unexpected revocation") })
    assertTrue(admitted.isAdmitted())
    admitted.require()
  }

  @Test
  fun notifiesRevocationOnlyOnce() {
    var revocations = 0
    val guard = DeviceAdmissionGuard({ false }) { revocations += 1 }

    assertFalse(guard.isAdmitted())
    assertEquals(1, revocations)
    assertFalse(guard.isAdmitted())
    assertEquals(1, revocations)
    try {
      guard.require()
      throw AssertionError("revoked device was admitted")
    } catch (_: DeviceAdmissionException) {
      assertEquals(1, revocations)
    }
  }

  @Test
  fun notifiesAgainAfterTheDeviceIsAdmittedAndRevokedAgain() {
    var admitted: Boolean? = false
    var revocations = 0
    val guard = DeviceAdmissionGuard({ admitted }) { revocations += 1 }

    assertFalse(guard.isAdmitted())
    admitted = true
    guard.require()
    admitted = false
    assertFalse(guard.isAdmitted())
    assertEquals(2, revocations)
  }

  @Test
  fun revokesWhenTheRegistryRejectsAnExplicitAdmissionCheck() {
    var checks = 0
    var revocations = 0
    val guard = DeviceAdmissionGuard({ false }) { revocations += 1 }

    assertThrows(DeviceAdmissionException::class.java) {
      guard.ensure {
        checks += 1
        throw DeviceAdmissionException()
      }
    }
    assertEquals(1, checks)
    assertEquals(1, revocations)
  }
}
