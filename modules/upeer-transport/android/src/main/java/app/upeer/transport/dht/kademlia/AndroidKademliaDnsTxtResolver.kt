package app.upeer.transport.dht.kademlia

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.net.ConnectivityManager
import android.net.DnsResolver
import android.os.Build
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AndroidKademliaDnsTxtResolver(private val context: Context) : KademliaDnsTxtResolver {
  override fun resolve(domain: String): List<List<String>> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
    return resolveWithDnsResolver(domain)
  }

  @Suppress("DEPRECATION")
  @SuppressLint("WrongConstant", "UseRequiresApi")
  @TargetApi(Build.VERSION_CODES.Q)
  private fun resolveWithDnsResolver(domain: String): List<List<String>> {
    val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
    val network = connectivity.activeNetwork ?: return emptyList()
    val resolver = DnsResolver.getInstance()
    val latch = CountDownLatch(1)
    var answer: ByteArray? = null
    val callback = object : DnsResolver.Callback<ByteArray> {
      override fun onAnswer(value: ByteArray, rcode: Int) {
        answer = value
        latch.countDown()
      }

      override fun onError(error: DnsResolver.DnsException) {
        latch.countDown()
      }
    }
    resolver.rawQuery(
      network,
      domain,
      TYPE_TXT,
      CLASS_IN,
      DnsResolver.FLAG_NO_CACHE_LOOKUP,
      context.mainExecutor,
      null,
      callback,
    )
    try {
      if (!latch.await(3, TimeUnit.SECONDS)) return emptyList()
    } catch (_: InterruptedException) {
      Thread.currentThread().interrupt()
      return emptyList()
    }
    return KademliaDnsTxtParser.parse(answer ?: return emptyList())
  }

  private companion object {
    const val TYPE_TXT = 16
    const val CLASS_IN = 1
  }
}
