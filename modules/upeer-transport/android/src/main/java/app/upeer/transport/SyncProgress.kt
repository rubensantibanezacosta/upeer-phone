package app.upeer.transport

import org.json.JSONObject

internal object SyncProgress {
  fun json(
    counts: List<SyncHistoryProgress>,
    pendingEvents: Int,
    files: SyncFileWork,
    phase: String,
  ): JSONObject {
    val done = counts.sumOf { it.applied.coerceAtLeast(0) }
    val total = counts.sumOf { it.total.coerceAtLeast(0) }
    val known = counts.isNotEmpty() && counts.all { it.known } && total > 0
    val historyPart = if (known) done.toLong() else 0L
    val historySize = if (known) total.toLong() else 0L
    val combinedDone = historyPart.toFloat() + files.units
    val combinedTotal = historySize + files.total
    val determinate = (known || files.known) && combinedTotal > 0
    val percent = if (determinate) ((combinedDone * 100f) / combinedTotal.toFloat()).toInt().coerceIn(0, 100) else 0
    return JSONObject()
      .put("determinate", determinate)
      .put("percent", percent)
      .put("historyDone", done)
      .put("historyTotal", if (known) total else 0)
      .put("historyKnown", known)
      .put("pendingEvents", pendingEvents.coerceAtLeast(0))
      .put("pendingFiles", files.pending.coerceAtLeast(0))
      .put("filesDone", files.done.coerceAtLeast(0))
      .put("filesTotal", files.total.coerceAtLeast(0))
      .put("filesFailed", files.failed.coerceAtLeast(0))
      .put("filesKnown", files.known)
      .put("headline", headline(phase))
      .put("detail", detail(done, total, known, pendingEvents, files, phase))
  }

  private fun headline(phase: String): String =
    if (phase == SyncStatusReport.STATE_SYNCING) "Sincronizando tus dispositivos…" else "¡Al día!"

  private fun detail(
    done: Int,
    total: Int,
    known: Boolean,
    pendingEvents: Int,
    files: SyncFileWork,
    phase: String,
  ): String {
    val parts = mutableListOf<String>()
    when {
      known -> parts.add("$done de ${plural(total, "mensaje", "mensajes")}")
      done > 0 -> parts.add(plural(done, "mensaje sincronizado", "mensajes sincronizados"))
      files.known && files.done > 0 -> Unit
      phase == SyncStatusReport.STATE_SYNCING -> parts.add("Conectando con tus dispositivos")
      else -> parts.add("Todo sincronizado")
    }
    if (files.known) parts.add("${files.done} de ${plural(files.total, "adjunto", "adjuntos")}")
    if (pendingEvents > 0) parts.add("${plural(pendingEvents, "evento pendiente", "eventos pendientes")} de aplicar")
    if (files.failed > 0) parts.add("${plural(files.failed, "adjunto sin respuesta", "adjuntos sin respuesta")}")
    return parts.joinToString(" · ")
  }

  private fun plural(total: Int, singular: String, plural: String): String =
    "$total ${if (total == 1) singular else plural}"
}
