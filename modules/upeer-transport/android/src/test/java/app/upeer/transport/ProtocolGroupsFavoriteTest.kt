package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolGroupsFavoriteTest {
  private val groupId = "grp-0123456789abcdef"
  private val admin = "aa".repeat(32)
  private val member = "bb".repeat(32)
  private val state = JSONObject().put("groups", JSONObject())

  private fun groups(): ProtocolGroups = ProtocolGroups({ state }, Any(), {})

  private fun payload(name: String = "Equipo") = JSONObject()
    .put("groupId", groupId)
    .put("name", name)
    .put("adminUpeerId", admin)
    .put("members", JSONArray().put(admin).put(member))
    .put("status", "active")

  private fun favorite(): Boolean =
    groups().snapshot().getJSONObject(0).optBoolean("isFavorite")

  @Test
  fun `un grupo nuevo no nace como favorito`() {
    groups().upsert(payload())

    assertFalse(favorite())
  }

  @Test
  fun `marcar y desmarcar el favorito`() {
    groups().upsert(payload())

    assertTrue(groups().setFavorite(groupId, true))
    assertTrue(favorite())

    assertTrue(groups().setFavorite(groupId, false))
    assertFalse(favorite())
  }

  @Test
  fun `marcar el favorito de un grupo que no existe no lo crea`() {
    assertFalse(groups().setFavorite("grp-ffffffffffffffff", true))
    assertTrue(groups().snapshot().length() == 0)
  }

  @Test
  fun `un GROUP_UPSERT posterior conserva el favorito`() {
    groups().upsert(payload())
    groups().setFavorite(groupId, true)

    groups().upsert(payload(name = "Equipo renombrado"))

    assertTrue(favorite())
    assertTrue(groups().snapshot().getJSONObject(0).optString("name") == "Equipo renombrado")
  }

  @Test
  fun `un GROUP_UPSERT que trae el favorito manda`() {
    groups().upsert(payload())
    groups().setFavorite(groupId, true)

    groups().upsert(payload().put("isFavorite", false))

    assertFalse(favorite())
  }
}
