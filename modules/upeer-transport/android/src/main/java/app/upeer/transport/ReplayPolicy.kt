package app.upeer.transport

internal object ReplayPolicy {
  private val MESSAGE_ID = Regex(
    "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
    RegexOption.IGNORE_CASE,
  )

  fun validMessageID(value: String): Boolean = MESSAGE_ID.matches(value)

  fun validAck(ownerUpeerId: String?, senderUpeerId: String, isMine: Boolean, status: String): Boolean =
    (status == "delivered" || status == "read") && isMine && ownerUpeerId == senderUpeerId
}
