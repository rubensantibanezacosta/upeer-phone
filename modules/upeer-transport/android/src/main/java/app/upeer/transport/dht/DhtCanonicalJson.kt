package app.upeer.transport.dht

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.math.BigDecimal
import java.math.BigInteger

object DhtCanonicalJson {
  fun parse(payload: String): JSONObject {
    val tokener = JSONTokener(payload)
    val value = tokener.nextValue()
    if (value !is JSONObject) throw IllegalArgumentException("canonical JSON must be an object")
    if (tokener.nextClean() != '\u0000') throw IllegalArgumentException("canonical JSON contains trailing data")
    return value
  }

  fun stringify(payload: String): String = canonical(parse(payload))

  fun canonical(value: Any?): String = buildString { appendValue(this, value) }

  private fun appendValue(builder: StringBuilder, value: Any?) {
    when (value) {
      null, JSONObject.NULL -> builder.append("null")
      is String -> appendString(builder, value)
      is Boolean -> builder.append(if (value) "true" else "false")
      is Int, is Long, is Short, is Byte -> builder.append((value as Number).toLong())
      is BigInteger -> {
        if (value.bitLength() > 63) throw IllegalArgumentException("canonical JSON integers must fit in 64 bits")
        builder.append(value)
      }
      is Double, is Float, is BigDecimal -> throw IllegalArgumentException("canonical JSON numbers must be integers")
      is JSONObject -> appendObject(builder, value)
      is JSONArray -> appendArray(builder, value)
      else -> throw IllegalArgumentException("unsupported canonical JSON value: ${value.javaClass.name}")
    }
  }

  private fun appendObject(builder: StringBuilder, value: JSONObject) {
    val keys = value.keys().asSequence().toList().sorted()
    builder.append('{')
    keys.forEachIndexed { index, key ->
      if (index > 0) builder.append(',')
      appendString(builder, key)
      builder.append(':')
      appendValue(builder, value.opt(key))
    }
    builder.append('}')
  }

  private fun appendArray(builder: StringBuilder, value: JSONArray) {
    builder.append('[')
    for (index in 0 until value.length()) {
      if (index > 0) builder.append(',')
      appendValue(builder, value.opt(index))
    }
    builder.append(']')
  }

  private fun appendString(builder: StringBuilder, value: String) {
    builder.append('"')
    for (character in value) {
      when (character) {
        '"' -> builder.append("\\\"")
        '\\' -> builder.append("\\\\")
        '\b' -> builder.append("\\b")
        '\u000c' -> builder.append("\\f")
        '\n' -> builder.append("\\n")
        '\r' -> builder.append("\\r")
        '\t' -> builder.append("\\t")
        else -> {
          if (character.code < 0x20) {
            builder.append("\\u").append(character.code.toString(16).padStart(4, '0'))
          } else {
            builder.append(character)
          }
        }
      }
    }
    builder.append('"')
  }
}
