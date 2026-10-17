package com.rgalaxy.optimizer

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

data class VpnReport(
    val permissionIntent: Intent?,   // null ise VPN izni bu uygulamada zaten verilmiş
    val vpnActive: Boolean,
    val v2rayInstalled: Boolean,
    val loopbackPorts: List<Int>,    // dinlenen yerel V2Ray portları
    val summary: String
)

/**
 * VPN altyapı kontrolü. DÜRÜST NOT:
 *  - VpnService.prepare() yalnızca BU uygulamanın VPN izni olup olmadığını söyler;
 *    V2rayNG'nin iznini değiştiremez/okuyamaz.
 *  - Aktif VPN, NetworkCapabilities.TRANSPORT_VPN ile algılanır.
 *  - 127.0.0.1 testi GERÇEK bir TCP bağlantı denemesidir (varsayılan V2rayNG
 *    SOCKS 10808 / HTTP 10809 portları). Port dinlemiyorsa V2rayNG çekirdeği çalışmıyor demektir.
 *  - "IPv4 köprü" testi simüle edilir: yalnızca 127.0.0.1'e bağlanılabildiği gösterilir,
 *    trafiğin tüneli gerçekten geçtiği internete çıkış testi (NetworkDiagnostics, VPN satırı) ile doğrulanır.
 */
object VpnChecker {
    const val V2RAY_PKG = "com.v2ray.ang"
    private val PORTS = listOf(10808, 10809)

    suspend fun check(ctx: Context): VpnReport = withContext(Dispatchers.IO) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val intent = VpnService.prepare(ctx)
        @Suppress("DEPRECATION")
        val vpn = cm.allNetworks.any {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
        val installed = runCatching { ctx.packageManager.getPackageInfo(V2RAY_PKG, 0); true }.getOrDefault(false)
        val open = PORTS.filter { p ->
            runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", p), 800); true } }.getOrDefault(false)
        }
        val s = buildString {
            append(if (vpn) "VPN bağlantısı algılandı." else "Aktif VPN algılanmadı.")
            append(if (installed) " V2rayNG yüklü." else " V2rayNG bulunamadı.")
            append(if (open.isNotEmpty()) " Yerel köprü açık (port ${open.joinToString()})." else " Yerel köprü (127.0.0.1) yanıt vermiyor.")
        }
        VpnReport(intent, vpn, installed, open, s)
    }
}
