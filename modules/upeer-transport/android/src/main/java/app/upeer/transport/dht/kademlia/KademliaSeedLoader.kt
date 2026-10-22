package app.upeer.transport.dht.kademlia

fun interface KademliaDnsTxtResolver {
  fun resolve(domain: String): List<List<String>>
}

data class KademliaSeedLoadResult(
  val seeds: List<KademliaSeed>,
  val rejected: Int,
  val duplicates: Int,
  val sourceFailures: Int,
)

class KademliaSeedLoader(
  private val selfUpeerId: String,
  private val existingContacts: () -> List<KademliaContact> = { emptyList() },
  private val readFile: () -> String? = { null },
  private val dnsResolver: KademliaDnsTxtResolver = KademliaDnsTxtResolver { emptyList() },
  private val staticSeeds: () -> List<KademliaSeed> = { emptyList() },
  private val maxSeeds: Int = KademliaConfig.MAX_SEED_NODES,
) {
  fun load(): KademliaSeedLoadResult {
    val accepted = LinkedHashMap<String, KademliaSeed>()
    var rejected = 0
    var duplicates = 0
    var sourceFailures = 0

    fun collect(candidates: List<KademliaSeed>) {
      candidates.forEach { candidate ->
        val normalized = KademliaSeedValidator.normalize(candidate, selfUpeerId)
        if (normalized == null) {
          rejected += 1
          return@forEach
        }
        if (accepted.containsKey(normalized.upeerId)) {
          duplicates += 1
          return@forEach
        }
        if (accepted.size < maxSeeds.coerceAtLeast(0)) accepted[normalized.upeerId] = normalized
      }
    }

    val existing = runCatching { existingContacts() }.getOrElse {
      sourceFailures += 1
      emptyList()
    }
    collect(existing.map { contact ->
      KademliaSeed(contact.upeerId, contact.address, contact.publicKey)
    })
    val static = runCatching { staticSeeds() }.getOrElse {
      sourceFailures += 1
      emptyList()
    }
    collect(static)

    val dnsRecords = runCatching {
      dnsResolver.resolve(KademliaSeedParser.DNS_DOMAIN)
    }.getOrElse {
      sourceFailures += 1
      emptyList()
    }
    collect(KademliaSeedParser.parseDns(dnsRecords))

    val fileContent = runCatching { readFile() }.getOrElse {
      sourceFailures += 1
      null
    }
    collect(KademliaSeedParser.parseFile(fileContent))

    return KademliaSeedLoadResult(accepted.values.toList(), rejected, duplicates, sourceFailures)
  }
}
