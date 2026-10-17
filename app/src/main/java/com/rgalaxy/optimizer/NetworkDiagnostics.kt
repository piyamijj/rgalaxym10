package com.rgalaxy.optimizer

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Bir ağ üzerindeki testin sonucu. */
data class PathResult(
    val label: String,
    val dnsOk: Boolean,
    val tcpOk: Boolean,
    val tlsOk: Boolean,
    val cause: Cause,
    val detail: String
)

enum class Cause { OK, DNS, ROUTE, CERT, PROTOCOL, NO_NETWORK }

/**
 * Canlı ağ teşhisi: DNS -> TCP -> TLS el sıkışması. Her ağ (Wi-Fi/mobil/VPN)
 * için Network nesnesinin kendi soket fabrikası kullanıldığından test gerçekten
 * o ağ üzerinden yapılır.
 */
object NetworkDiagnostics {
    private const val TAG = "RGalaxyDiag"
    private const val HOST = "www.google.com"
    private const val IP = "1.1.1.1"
    private const val TIMEOUT = 5000

    suspend fun run(ctx: Context, log: (String) -> Unit): List<PathResult> = withContext(Dispatchers.IO) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val results = mutableListOf<PathResult>()
        @Suppress("DEPRECATION")
        val nets = cm.allNetworks
        if (nets.isEmpty()) {
            results += PathResult("Ağ yok", false, false, false, Cause.NO_NETWORK, "Cihaz hiçbir ağa bağlı değil.")
        }
        for (n in nets) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            val label = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobil veri"
                else -> "Diğer ağ"
            }
            val r = probe(n, label)
            log("[$label] DNS=${r.dnsOk} TCP=${r.tcpOk} TLS=${r.tlsOk} -> ${r.cause} (${r.detail})")
            Log.i(TAG, "$label $r")
            results += r
        }
        results
    }

    private fun probe(n: Network, label: String): PathResult {
        // 1) DNS
        var dnsOk = false
        var dnsErr = ""
        try {
            dnsOk = n.getAllByName(HOST).isNotEmpty()
        } catch (e: Exception) { dnsErr = e.javaClass.simpleName }

        // 2) TCP (DNS'ten bağımsız, doğrudan IP)
        var tcpOk = false
        var tcpErr = ""
        try {
            n.socketFactory.createSocket().use { s ->
                s.connect(InetSocketAddress(IP, 443), TIMEOUT)
                tcpOk = true
            }
        } catch (e: Exception) { tcpErr = e.javaClass.simpleName }

        // 3) TLS el sıkışması
        var tlsOk = false
        var tlsErr = ""
        var cert = false
        if (tcpOk) {
            try {
                val raw: Socket = n.socketFactory.createSocket()
                raw.soTimeout = TIMEOUT
                raw.connect(InetSocketAddress(IP, 443), TIMEOUT)
                val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(raw, HOST, 443, true) as SSLSocket
                ssl.soTimeout = TIMEOUT
                ssl.use { it.startHandshake(); tlsOk = true }
            } catch (e: SSLPeerUnverifiedException) { cert = true; tlsErr = "Sertifika doğrulanamadı"
            } catch (e: SSLHandshakeException) {
                cert = e.message?.contains("cert", true) == true || e.cause is java.security.cert.CertificateException
                tlsErr = e.message ?: "Handshake"
            } catch (e: Exception) { tlsErr = e.javaClass.simpleName + " " + (e.message ?: "") }
        }

        val cause = when {
            dnsOk && tcpOk && tlsOk -> Cause.OK
            !tcpOk -> Cause.ROUTE
            !dnsOk -> Cause.DNS
            cert -> Cause.CERT
            else -> Cause.PROTOCOL
        }
        return PathResult(label, dnsOk, tcpOk, tlsOk, cause, listOf(dnsErr, tcpErr, tlsErr).filter { it.isNotEmpty() }.joinToString("; ").ifEmpty { "sorun yok" })
    }

    /** Sade Türkçe sonuç metni. */
    fun summarize(rs: List<PathResult>): String {
        if (rs.isEmpty()) return "Ağ bilgisi alınamadı."
        return rs.joinToString("\n") { r ->
            val t = when (r.cause) {
                Cause.OK -> "Çalışıyor ✔"
                Cause.DNS -> "Adres çözümleme (DNS) sorunu"
                Cause.ROUTE -> "Bağlantı kurulamıyor (yönlendirme/engel)"
                Cause.CERT -> "Sertifika sorunu (tarih/saat veya ara katman)"
                Cause.PROTOCOL -> "Güvenli bağlantı (TLS) protokol sorunu"
                Cause.NO_NETWORK -> "Ağ yok"
            }
            "• ${r.label}: $t"
        }
    }
}
