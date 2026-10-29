package app.upeer.transport

import java.io.File

internal object SyncAttachmentAudit {
  fun missing(
    refs: List<AttachmentRef>,
    stored: (String) -> Boolean,
    exists: (String) -> Boolean = { File(it).isFile },
  ): Int = refs.count { !present(it, stored, exists) }

  fun present(
    ref: AttachmentRef,
    stored: (String) -> Boolean,
    exists: (String) -> Boolean = { File(it).isFile },
  ): Boolean {
    val path = ref.savedPath
    if (!path.isNullOrBlank() && runCatching { exists(path) }.getOrDefault(false)) return true
    return runCatching { stored(ref.fileId) }.getOrDefault(false)
  }
}
