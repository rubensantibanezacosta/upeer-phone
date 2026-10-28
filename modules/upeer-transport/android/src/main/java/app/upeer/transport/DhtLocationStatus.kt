package app.upeer.transport

import org.json.JSONObject

internal class DhtLocationStatus(
  private val now: () -> Long = System::currentTimeMillis,
) {
  private val lock = Any()
  private var publishedAt: Long = 0L
  private var sequence: Long = 0L
  private var nodes: Int = 0
  private var fanoutAt: Long = 0L
  private var fanout: Int = 0
  private var failure: Failure? = null

  data class Failure(val kind: String, val message: String, val at: Long)

  fun notePublished(dhtSeq: Long, nodes: Int) {
    synchronized(lock) {
      publishedAt = now()
      sequence = dhtSeq
      this.nodes = nodes.coerceAtLeast(0)
      failure = null
    }
  }

  fun noteFanout(count: Int) {
    synchronized(lock) {
      fanoutAt = now()
      fanout = count.coerceAtLeast(0)
    }
  }

  fun noteFailure(kind: String, message: String) {
    synchronized(lock) {
      failure = Failure(kind, sanitize(message), now())
    }
  }

  fun failureOf(): Failure? = synchronized(lock) { failure }

  fun toJson(): JSONObject = synchronized(lock) {
    JSONObject()
      .put("publishedAt", publishedAt)
      .put("dhtSeq", sequence)
      .put("nodes", nodes)
      .put("fanoutAt", fanoutAt)
      .put("fanout", fanout)
      .put("error", failure?.let { error ->
        JSONObject()
          .put("kind", error.kind)
          .put("message", error.message)
          .put("at", error.at)
      } ?: JSONObject.NULL)
  }

  internal companion object {
    const val KIND_NETWORK = "red"
    const val KIND_CERTIFICATE = "certificado"
    const val KIND_IDENTITY = "identidad"

    private const val MAX_MESSAGE = 160
    private val IPV4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
    private val IPV6 = Regex("""\b(?:[0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}\b""")
    private val FINGERPRINT = Regex("""\b[0-9a-fA-F]{16,}\b""")

    fun sanitize(message: String): String {
      val withoutAddresses = IPV4.replace(IPV6.replace(message, "[dirección]"), "[dirección]")
      val withoutKeys = FINGERPRINT.replace(withoutAddresses, "[clave]")
      val trimmed = withoutKeys.replace(Regex("""\s+"""), " ").trim()
      if (trimmed.isEmpty()) return "Error sin detalle"
      return if (trimmed.length <= MAX_MESSAGE) trimmed else "${trimmed.take(MAX_MESSAGE)}…"
    }
  }
}
