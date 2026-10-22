package app.upeer.transport

import android.net.ConnectivityManager
import app.upeer.transport.dht.DhtLocationWorker
import app.upeer.transport.dht.DhtUpdateDispatcher
import app.upeer.transport.dht.kademlia.KademliaBootstrap
import app.upeer.transport.dht.kademlia.KademliaNode
import app.upeer.transport.dht.kademlia.KademliaPacketRouter
import app.upeer.transport.dht.kademlia.KademliaSeedLoader
import java.util.concurrent.ScheduledExecutorService

internal data class YggdrasilDhtState(
  val node: KademliaNode,
  val router: KademliaPacketRouter,
  val bootstrap: KademliaBootstrap,
  val executor: ScheduledExecutorService,
  val networkCallback: ConnectivityManager.NetworkCallback?,
  val socket: DhtSocketTransport,
  val keys: MutableMap<String, String>,
  val loader: KademliaSeedLoader,
  val locations: DhtLocationWorker,
  val updates: DhtUpdateDispatcher,
)
