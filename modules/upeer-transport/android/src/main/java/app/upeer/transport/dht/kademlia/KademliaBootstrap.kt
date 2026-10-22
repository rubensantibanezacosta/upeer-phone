package app.upeer.transport.dht.kademlia

data class KademliaBootstrapStats(
  val attempts: Long,
  val successes: Long,
  val failures: Long,
  val seedsLoaded: Int,
  val seedsRejected: Int,
  val seedsDuplicate: Int,
  val seedSourceFailures: Int,
  val verifiedContacts: Int,
  val contactCount: Int,
  val bootstrapped: Boolean,
  val lastAttemptMillis: Long,
  val lastSuccessMillis: Long,
  val nextAttemptMillis: Long,
  val persistFailures: Long,
)

data class KademliaBootstrapResult(
  val ran: Boolean,
  val bootstrapped: Boolean,
  val verifiedContacts: Int,
  val contactCount: Int,
  val seedsLoaded: Int,
  val seedsRejected: Int,
  val nextAttemptMillis: Long,
)

class KademliaBootstrap(
  private val node: KademliaNode,
  private val loader: KademliaSeedLoader,
  private val store: KademliaContactStore? = null,
  private val clock: () -> Long = System::currentTimeMillis,
  minNodes: Int = KademliaConfig.BOOTSTRAP_MIN_NODES,
  retryMillis: Long = KademliaConfig.BOOTSTRAP_RETRY_MILLIS,
  maxRetryMillis: Long = KademliaConfig.BOOTSTRAP_MAX_RETRY_MILLIS,
  private val probeTimeoutMillis: Long = KademliaConfig.QUERY_TIMEOUT_MILLIS,
  lookupRounds: Int = KademliaConfig.BOOTSTRAP_LOOKUP_ROUNDS,
  probeBatch: Int = KademliaConfig.BOOTSTRAP_PROBE_BATCH,
) {
  private val stateLock = Any()
  private val attemptLock = Any()
  private val minNodes = minNodes.coerceAtLeast(1)
  private val retryMillis = retryMillis.coerceAtLeast(1L)
  private val maxRetryMillis = maxRetryMillis.coerceAtLeast(this.retryMillis)
  private val lookupRounds = lookupRounds.coerceAtLeast(0)
  private val probeBatch = probeBatch.coerceAtLeast(1)
  private val verified = HashSet<String>()
  private var attempts = 0L
  private var successes = 0L
  private var failures = 0L
  private var seedsLoaded = 0
  private var seedsRejected = 0
  private var seedsDuplicate = 0
  private var seedSourceFailures = 0
  private var lastAttempt = 0L
  private var lastSuccess = 0L
  private var nextAttempt = 0L
  private var consecutiveFailures = 0
  private var persistFailures = 0L
  private var bootstrapped = false

  init {
    val persisted = runCatching { store?.load() ?: emptyList() }.getOrElse { emptyList() }
    if (persisted.isEmpty()) {
      synchronized(stateLock) { updateStatusLocked() }
    } else {
      bootstrapFromContacts(persisted)
    }
  }

  fun bootstrapFromContacts(contacts: List<KademliaContact>): Int {
    var added = 0
    synchronized(stateLock) {
      contacts.forEach { contact ->
        val normalized = KademliaSeedValidator.normalize(contact, node.upeerId) ?: return@forEach
        if (node.addContact(normalized)) added += 1
        verified.add(normalized.upeerId)
      }
      updateStatusLocked()
    }
    return added
  }

  fun attempt(): KademliaBootstrapResult = synchronized(attemptLock) {
    val startedAt = clock()
    val alreadyBootstrapped = synchronized(stateLock) {
      updateStatusLocked()
      bootstrapped
    }
    if (alreadyBootstrapped) return@synchronized result(false, 0)

    val load = loader.load()
    synchronized(stateLock) {
      attempts += 1
      lastAttempt = startedAt
      seedsLoaded = load.seeds.size
      seedsRejected += load.rejected
      seedsDuplicate += load.duplicates
      seedSourceFailures += load.sourceFailures
    }

    val seedContacts = load.seeds.map { seed ->
      KademliaContact(KademliaId.fromUpeerId(seed.upeerId), seed.upeerId, seed.address, seed.publicKey, startedAt)
    }
    markVerified(node.probeContacts(seedContacts, probeTimeoutMillis))

    for (round in 0 until lookupRounds) {
      if (currentVerifiedCount() >= minNodes) break
      val before = node.routingTable.contactCount()
      node.findNode(node.upeerId)
      if (node.routingTable.contactCount() <= before) break
    }

    val attempted = HashSet<String>()
    while (currentVerifiedCount() < minNodes) {
      val candidates = node.routingTable.allContacts()
        .mapNotNull { contact -> KademliaSeedValidator.normalize(contact, node.upeerId) }
        .filter { contact -> !verified.contains(contact.upeerId) && attempted.add(contact.upeerId) }
        .take(probeBatch)
      if (candidates.isEmpty()) break
      markVerified(node.probeContacts(candidates, probeTimeoutMillis))
    }

    val finishedAt = clock()
    val summary = synchronized(stateLock) {
      updateStatusLocked()
      if (bootstrapped) {
        successes += 1
        lastSuccess = finishedAt
        consecutiveFailures = 0
        nextAttempt = finishedAt + retryMillis
      } else {
        failures += 1
        consecutiveFailures += 1
        nextAttempt = finishedAt + backoffLocked()
      }
      result(true, load.seeds.size)
    }
    persistVerifiedContacts()
    summary
  }

  fun maintenance(): KademliaBootstrapResult? {
    val shouldAttempt = synchronized(stateLock) {
      updateStatusLocked()
      !bootstrapped && clock() >= nextAttempt
    }
    return if (shouldAttempt) attempt() else null
  }

  fun retryNow(): KademliaBootstrapResult {
    synchronized(stateLock) {
      nextAttempt = 0L
      consecutiveFailures = 0
    }
    return attempt()
  }

  fun onNetworkChanged() {
    synchronized(stateLock) {
      nextAttempt = 0L
      consecutiveFailures = 0
    }
  }

  fun isBootstrapped(): Boolean = synchronized(stateLock) {
    updateStatusLocked()
    bootstrapped
  }

  fun verifiedContactCount(): Int = synchronized(stateLock) {
    updateStatusLocked()
    verifiedContactsLocked().size
  }

  fun contactCount(): Int = node.routingTable.contactCount()

  fun stats(): KademliaBootstrapStats = synchronized(stateLock) {
    updateStatusLocked()
    KademliaBootstrapStats(
      attempts,
      successes,
      failures,
      seedsLoaded,
      seedsRejected,
      seedsDuplicate,
      seedSourceFailures,
      verifiedContactsLocked().size,
      node.routingTable.contactCount(),
      bootstrapped,
      lastAttempt,
      lastSuccess,
      nextAttempt,
      persistFailures,
    )
  }

  private fun markVerified(contacts: List<KademliaContact>) {
    if (contacts.isEmpty()) return
    synchronized(stateLock) {
      contacts.forEach { contact -> verified.add(contact.upeerId) }
      updateStatusLocked()
    }
  }

  private fun currentVerifiedCount(): Int = synchronized(stateLock) {
    updateStatusLocked()
    verifiedContactsLocked().size
  }

  private fun persistVerifiedContacts() {
    val contactStore = store ?: return
    val contacts = synchronized(stateLock) { verifiedContactsLocked() }
    if (contacts.isEmpty()) return
    val saved = runCatching { contactStore.save(contacts) }.getOrElse { false }
    if (!saved) {
      synchronized(stateLock) { persistFailures += 1 }
    }
  }

  private fun verifiedContactsLocked(): List<KademliaContact> {
    val present = node.routingTable.allContacts()
    val presentIds = present.map { it.upeerId }.toSet()
    verified.retainAll(presentIds)
    return present.filter { verified.contains(it.upeerId) }
  }

  private fun updateStatusLocked() {
    bootstrapped = verifiedContactsLocked().size >= minNodes
  }

  private fun backoffLocked(): Long {
    val exponent = (consecutiveFailures - 1).coerceIn(0, 4)
    return (retryMillis shl exponent).coerceAtMost(maxRetryMillis)
  }

  private fun result(ran: Boolean, loadedSeeds: Int): KademliaBootstrapResult =
    KademliaBootstrapResult(
      ran,
      bootstrapped,
      verifiedContactsLocked().size,
      node.routingTable.contactCount(),
      loadedSeeds,
      seedsRejected,
      nextAttempt,
    )
}
