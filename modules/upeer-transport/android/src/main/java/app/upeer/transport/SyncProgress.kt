package app.upeer.transport

import org.json.JSONObject

internal object SyncProgress {
  fun json(
    counts: List<SyncHistoryProgress>,
    pendingEvents: Int,
    activeFiles: Int,
    phase: String,
  ): JSONObject {
    val done = counts.sumOf { it.applied.coerceAtLeast(0) }
    val total = counts.sumOf { it.total.coerceAtLeast(0) }
    val known = counts.isNotEmpty() && counts.all { it.known } && total > 0
    val determinate = known
    val percent = if (determinate) ((done.toLong() * 100) / total).coerceIn(0L, 100L).toInt() else 0
    return JSONObject()
      .put("determinate", determinate)
      .put("percent", percent)
      .put("historyDone", done)
      .put("historyTotal", if (determinate) total else 0)
      .put("historyKnown", known)
      .put("pendingEvents", pendingEvents.coerceAtLeast(0))
      .put("pendingFiles", activeFiles.coerceAtLeast(0))
      .put("headline", headline(phase))
      .put("detail", detail(done, total, determinate, pendingEvents, activeFiles))
  }

  private fun headline(phase: String): String =
    if (phase == SyncStatusReport.STATE_SYNCING) "Sincronizando tus dispositivos…" else "¡Al día!"

  private fun detail(done: Int, total: Int, determinate: Boolean, pendingEvents: Int, activeFiles: Int): String {
    val parts = mutableListOf<String>()
    when {
      determinate -> parts.add("$done de ${plural(total, "mensaje", "mensajes")}")
      done > 0 -> parts.add(plural(done, "mensaje sincronizado", "mensajes sincronizados"))
      else -> parts.add("Conectando con tus dispositivos")
    }
    if (pendingEvents > 0) parts.add("${plural(pendingEvents, "evento pendiente", "eventos pendientes")} de aplicar")
    if (activeFiles > 0) parts.add("${plural(activeFiles, "archivo", "archivos")} en curso")
    return parts.joinToString(" · ")
  }

  private fun plural(total: Int, singular: String, plural: String): String =
    "$total ${if (total == 1) singular else plural}"
}
