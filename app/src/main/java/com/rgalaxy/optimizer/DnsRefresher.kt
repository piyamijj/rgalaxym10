package com.rgalaxy.optimizer

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.security.Security
import kotlin.coroutines.resume

/**
 * Root'suz DNS/ağ yenileme. DÜRÜST NOT: Android 10'da sistem DNS önbelleğini
 * doğrudan silen genel bir API yoktur. Burada yapılanlar:
 *  1) Uygulama içi JVM DNS önbelleğini kapatmak (TTL=0),
 *  2) requestNetwork ile sistemden internet yeteneği olan ağı yeniden talep etmek
 *     (ağ doğrulamasını/seçimini tetikler),
 *  3) Süreci VPN olmayan alttaki ağa bağlayıp bırakmak (bindProcessToNetwork).
 * Sistem düzeyinde tam temizlik için Uçak Modu aç/kapa veya Ağ ayarlarını sıfırla gerekir.
 */
object DnsRefresher {
    suspend fun refresh(ctx: Context, log: (String) -> Unit): Boolean {
        try {
            Security.setProperty("networkaddress.cache.ttl", "0")
            Security.setProperty("networkaddress.cache.negative.ttl", "0")
            log("Uygulama içi DNS önbelleği kapatıldı.")
        } catch (e: Exception) { log("JVM DNS önbelleği ayarlanamadı: ${e.message}") }

        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        var cb: ConnectivityManager.NetworkCallback? = null
        val net: Network? = withTimeoutOrNull(8000) {
            suspendCancellableCoroutine<Network?> { cont ->
                cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) { if (cont.isActive) cont.resume(network) }
                }
                try { cm.requestNetwork(req, cb!!) } catch (e: Exception) { if (cont.isActive) cont.resume(null) }
                cont.invokeOnCancellation { runCatching { cm.unregisterNetworkCallback(cb!!) } }
            }
        }
        runCatching { cb?.let { cm.unregisterNetworkCallback(it) } }
        return if (net != null) {
            // Önce alttaki ağa bağla (taze çözümleme), sonra varsayılana dön.
            cm.bindProcessToNetwork(net)
            runCatching { net.getAllByName("www.google.com") }
            cm.bindProcessToNetwork(null)
            log("Ağ yeniden talep edildi ve doğrulandı.")
            true
        } else {
            log("Ağ yeniden talep edilemedi (zaman aşımı).")
            false
        }
    }
}
