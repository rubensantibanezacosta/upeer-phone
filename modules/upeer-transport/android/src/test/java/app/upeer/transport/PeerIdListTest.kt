package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerIdListTest {
  @Test
  fun normalizeTrimsLowercasesAndDeduplicates() {
    assertEquals(listOf("abc", "def"), PeerIdList.normalize(listOf(" ABC ", "def", "abc", "  ")))
  }

  @Test
  fun containsIgnoresCaseAndPadding() {
    val ids = PeerIdList.normalize(listOf("ab".repeat(32)))
    assertTrue(PeerIdList.contains(ids, "  ${"AB".repeat(32)} "))
    assertFalse(PeerIdList.contains(ids, "cd".repeat(32)))
  }

  @Test
  fun emptyListMatchesNothing() {
    assertFalse(PeerIdList.contains(emptyList(), "ab".repeat(32)))
  }
}
