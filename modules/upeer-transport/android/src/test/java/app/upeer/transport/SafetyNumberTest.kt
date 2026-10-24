package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyNumberTest {
  private val keyA = "ab".repeat(32)
  private val keyB = "cd".repeat(32)
  private val keyC = "01".repeat(32)

  @Test
  fun `matches the published digest for a known pair`() {
    assertEquals("DCD7D 37B4A 82F51 61C1A D6968 B78C2 9B905 C3AF1", SafetyNumber.compute(keyA, keyB))
  }

  @Test
  fun `is symmetric regardless of argument order`() {
    assertEquals(SafetyNumber.compute(keyA, keyB), SafetyNumber.compute(keyB, keyA))
  }

  @Test
  fun `changes with either key`() {
    assertEquals("85103 6F62C 6A7A2 8DA91 6590B AD433 29E65 6BAFE", SafetyNumber.compute(keyC, keyA))
  }

  @Test
  fun `accepts uppercase input`() {
    assertEquals(SafetyNumber.compute(keyA, keyB), SafetyNumber.compute(keyA.uppercase(), keyB))
  }

  @Test
  fun `rejects malformed keys`() {
    assertEquals("", SafetyNumber.compute("", keyB))
    assertEquals("", SafetyNumber.compute(keyA, "zz".repeat(32)))
    assertEquals("", SafetyNumber.compute(keyA.dropLast(1), keyB))
  }

  @Test
  fun `formats eight groups of five`() {
    val groups = SafetyNumber.compute(keyA, keyB).split(" ")
    assertEquals(8, groups.size)
    assertEquals(true, groups.all { it.length == 5 })
  }
}
