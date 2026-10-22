package app.upeer.transport.dht.kademlia

object KademliaDnsTxtParser {
  private const val HEADER_BYTES = 12
  private const val TYPE_TXT = 16

  fun parse(packet: ByteArray): List<List<String>> {
    if (packet.size < HEADER_BYTES) return emptyList()
    val questions = readU16(packet, 4)
    val answers = readU16(packet, 6)
    var offset = HEADER_BYTES
    repeat(questions) {
      offset = skipName(packet, offset) ?: return emptyList()
      offset += 4
      if (offset > packet.size) return emptyList()
    }
    val records = mutableListOf<List<String>>()
    repeat(answers) {
      offset = skipName(packet, offset) ?: return records
      if (offset + 10 > packet.size) return records
      val type = readU16(packet, offset)
      val length = readU16(packet, offset + 8)
      offset += 10
      if (offset + length > packet.size) return records
      if (type == TYPE_TXT) {
        val chunks = readTxtChunks(packet, offset, length)
        if (chunks.isNotEmpty()) records.add(chunks)
      }
      offset += length
    }
    return records
  }

  private fun readTxtChunks(packet: ByteArray, start: Int, length: Int): List<String> {
    val chunks = mutableListOf<String>()
    var offset = start
    val end = start + length
    while (offset < end) {
      val chunkLength = packet[offset].toInt() and 0xff
      offset += 1
      if (offset + chunkLength > end) return emptyList()
      chunks.add(String(packet, offset, chunkLength, Charsets.UTF_8))
      offset += chunkLength
    }
    return chunks
  }

  private fun skipName(packet: ByteArray, start: Int): Int? {
    var offset = start
    while (offset < packet.size) {
      val length = packet[offset].toInt() and 0xff
      if (length == 0) return offset + 1
      if (length and 0xc0 == 0xc0) return offset + 2
      offset += length + 1
    }
    return null
  }

  private fun readU16(packet: ByteArray, offset: Int): Int {
    if (offset + 1 >= packet.size) return 0
    return ((packet[offset].toInt() and 0xff) shl 8) or (packet[offset + 1].toInt() and 0xff)
  }
}
