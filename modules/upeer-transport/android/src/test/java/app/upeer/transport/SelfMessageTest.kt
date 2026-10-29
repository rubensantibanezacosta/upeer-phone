package app.upeer.transport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfMessageTest {
  private val me = "da4163f3528710db1adeca2211274d217728845044c70b912759e856493d1834"
  private val other = "a60cc35ee3d4e106f7563d9ba4c2deaad4dc0596ded6acdab53585fba354948b"

  @Test
  fun `mi propio chat se reconoce aunque cambie el formato`() {
    assertTrue(SelfMessage.isSelfChat(me, me))
    assertTrue(SelfMessage.isSelfChat(me.uppercase(), me))
    assertTrue(SelfMessage.isSelfChat(me, me.uppercase()))
  }

  @Test
  fun `el chat de un contacto no es el mio`() {
    assertFalse(SelfMessage.isSelfChat(other, me))
  }

  @Test
  fun `sin identidad propia no se adivina nada`() {
    assertFalse(SelfMessage.isSelfChat(me, null))
    assertFalse(SelfMessage.isSelfChat(me, ""))
    assertFalse(SelfMessage.isSelfChat("", me))
  }
}
