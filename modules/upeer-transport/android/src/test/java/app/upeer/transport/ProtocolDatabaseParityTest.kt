package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Comprueba que la lista de tablas del móvil es exactamente la del esquema del escritorio.
 *
 * Lee `upper-desktop/src/main_process/storage/schema.ts` del repositorio, que es la referencia:
 * si alguien añade o quita una tabla allí, este test lo detecta.
 */
class ProtocolDatabaseParityTest {
  private fun desktopTables(): Set<String> {
    val schema = findSchema() ?: return emptySet()
    return Regex("""sqliteTable\('(\w+)'""").findAll(schema.readText())
      .map { it.groupValues[1] }
      .toSet()
  }

  private fun findSchema(): File? {
    var directory: File? = File(System.getProperty("user.dir")).absoluteFile
    repeat(8) {
      val current = directory ?: return null
      val candidate = File(current, "upper-desktop/src/main_process/storage/schema.ts")
      if (candidate.isFile) return candidate
      directory = current.parentFile
    }
    return null
  }

  @Test
  fun `el esquema del escritorio se lee de verdad`() {
    // Si no se encuentra schema.ts, la comparación no probaría nada: mejor fallar que callar.
    assertTrue(
      "No se encontró upper-desktop/src/main_process/storage/schema.ts desde ${File(System.getProperty("user.dir")).absolutePath}",
      findSchema() != null,
    )
    assertTrue("schema.ts no declara tablas de drizzle", desktopTables().size >= MIN_DESKTOP_TABLES)
  }

  @Test
  fun `el movil declara todas las tablas del escritorio`() {
    val desktop = desktopTables()
    if (desktop.isEmpty()) return // cubierto arriba
    val missing = desktop - ProtocolDatabase.TABLES.toSet()
    assertTrue("Faltan tablas del escritorio en el móvil: $missing", missing.isEmpty())
  }

  @Test
  fun `el movil no inventa tablas que el escritorio no tiene`() {
    val desktop = desktopTables()
    if (desktop.isEmpty()) return
    val extra = ProtocolDatabase.TABLES.toSet() - desktop
    assertTrue("Tablas del móvil que no existen en el escritorio: $extra", extra.isEmpty())
  }

  private companion object {
    /** El esquema del escritorio tiene 16 tablas; por debajo de 10, la lectura está rota. */
    const val MIN_DESKTOP_TABLES = 10
  }

  @Test
  fun `ninguna tabla se declara dos veces`() {
    assertEquals(ProtocolDatabase.TABLES.size, ProtocolDatabase.TABLES.toSet().size)
  }
}
