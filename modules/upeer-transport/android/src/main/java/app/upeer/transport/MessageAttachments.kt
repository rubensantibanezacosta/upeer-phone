package app.upeer.transport

import org.json.JSONObject

internal object MessageAttachments {
  fun content(message: JSONObject): String? = contentOf(message.optString("content"))

  fun contentOf(raw: String): String? {
    if (raw.isEmpty() || !raw.startsWith("{")) return null
    val parsed = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    if (parsed.optString("type") !in KINDS) return null
    return raw
  }

  fun fileIdOf(message: JSONObject): String? {
    val content = content(message) ?: return null
    return fileIdOfContent(content)
  }

  fun fileIdOfContent(raw: String): String? {
    val parsed = parsed(raw) ?: return null
    return parsed.optString("fileId").ifEmpty { null }
  }

  fun savedPathOf(message: JSONObject): String? {
    val content = content(message) ?: return null
    return savedPathOfContent(content)
  }

  fun savedPathOfContent(raw: String): String? {
    val parsed = parsed(raw) ?: return null
    return parsed.optString("savedPath").ifEmpty { null }
  }

  fun fileNameOfContent(raw: String): String = parsed(raw)?.optString("fileName").orEmpty()

  fun localPathOfContent(raw: String): String? {
    val parsed = parsed(raw) ?: return null
    val path = parsed.optString("localPath").ifEmpty { return null }
    if (!path.startsWith("/") && !path.startsWith("file://")) return null
    return path.removePrefix("file://")
  }

  private fun parsed(raw: String): JSONObject? =
    runCatching { JSONObject(raw) }.getOrNull()?.takeIf { it.optString("type") in KINDS }

  private val KINDS = setOf("file", "image")
}
