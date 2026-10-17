package com.rgalaxy.optimizer

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var txtStatus: TextView
    private lateinit var txtDetail: TextView
    private lateinit var btn: Button
    private lateinit var progress: ProgressBar
    private val logLines = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        txtStatus = findViewById(R.id.txtStatus)
        txtDetail = findViewById(R.id.txtDetail)
        btn = findViewById(R.id.btnFix)
        progress = findViewById(R.id.progress)
        btn.setOnClickListener { optimize() }
    }

    private fun say(status: String, color: Int = 0xFF212121.toInt()) {
        txtStatus.text = status; txtStatus.setTextColor(color)
    }
    private fun log(s: String) { logLines.appendLine(s); runOnUiThread { txtDetail.text = logLines.toString() } }

    private fun optimize() {
        logLines.clear(); txtDetail.text = ""
        btn.isEnabled = false; progress.visibility = View.VISIBLE
        say(getString(R.string.status_running))
        lifecycleScope.launch {
            // 1) DNS / ağ yenileme
            say("1/4 Ağ yenileniyor…")
            DnsRefresher.refresh(this@MainActivity, ::log)
            delay(1000)

            // 2) VPN kontrolü
            say("2/4 VPN kontrol ediliyor…")
            val vpn = VpnChecker.check(this@MainActivity)
            log(vpn.summary)
            if (vpn.permissionIntent != null) log("Not: Bu uygulamanın VPN izni yok; gerekmiyor, yalnızca bilgi.")

            // 3) Teşhis
            say("3/4 Bağlantı test ediliyor…")
            val res = NetworkDiagnostics.run(this@MainActivity, ::log)
            val allOk = res.isNotEmpty() && res.all { it.cause == Cause.OK }
            val vpnPath = res.firstOrNull { it.label == "VPN" }
            log("\nSonuç:\n" + NetworkDiagnostics.summarize(res))

            progress.visibility = View.GONE
            btn.isEnabled = true
            if (allOk) {
                say(getString(R.string.status_ok), 0xFF1B5E20.toInt())
            } else {
                say("Bazı sorunlar bulundu. Yardım için aşağıyı okuyun.", 0xFFB71C1C.toInt())
                log(advice(vpn, vpnPath, res))
                // 4) Batarya ve gelişmiş onarım için kullanıcıyı yönlendir
                say("4/4 Ek ayarlar gerekiyor", 0xFFB71C1C.toInt())
                offerFixes(vpn)
            }
        }
    }

    private fun advice(vpn: VpnReport, vpnPath: PathResult?, res: List<PathResult>): String {
        val sb = StringBuilder("\nÖneri:\n")
        val bare = res.firstOrNull { it.label != "VPN" }
        if (bare != null && bare.cause != Cause.OK) sb.append("• Wi-Fi/mobil internetin kendisinde sorun var (${bare.detail}). Modemi yeniden başlatın.\n")
        if (vpnPath != null && vpnPath.cause != Cause.OK) when (vpnPath.cause) {
            Cause.DNS -> sb.append("• VPN içinde DNS çözülmüyor: V2rayNG ayarlarında DNS'i 1.1.1.1 yapın.\n")
            Cause.CERT -> sb.append("• Sertifika hatası: telefonun tarih/saatini otomatik yapın.\n")
            Cause.PROTOCOL -> sb.append("• TLS/protokol hatası: V2rayNG sunucu ayarlarını (TLS, SNI) kontrol edin.\n")
            else -> sb.append("• VPN tüneli veri geçirmiyor: V2rayNG'yi durdurup yeniden başlatın.\n")
        }
        if (vpn.vpnActive && vpn.loopbackPorts.isEmpty()) sb.append("• VPN açık görünüyor ama V2rayNG çekirdeği yanıt vermiyor; uygulamayı yeniden başlatın.\n")
        return sb.toString()
    }

    private fun offerFixes(vpn: VpnReport) {
        AlertDialog.Builder(this)
            .setTitle("Ek onarım")
            .setMessage("Batarya ve veri tasarrufu kısıtlamaları VPN'i durdurabilir. Ayar ekranlarını sizin için açalım mı?")
            .setPositiveButton("Evet") { _, _ -> startFixFlow(vpn) }
            .setNegativeButton("Hayır", null).show()
    }

    private fun startFixFlow(vpn: VpnReport) {
        if (!BatteryHelper.isExempt(this)) startActivity(BatteryHelper.requestSelfExemption(this))
        if (vpn.v2rayInstalled) {
            runCatching { startActivity(BatteryHelper.v2rayAppInfo()) }
            AlertDialog.Builder(this)
                .setTitle("V2rayNG için")
                .setMessage("Açılan ekranda Batarya > \"Kısıtlama yok\" seçin. Sonra Veri tasarrufu muafiyeti ekranını açacağız.")
                .setPositiveButton("Tamam") { _, _ -> runCatching { startActivity(BatteryHelper.dataSaverForV2ray()) }; askMacro() }
                .show()
        } else askMacro()
    }

    private fun askMacro() {
        if (MacroAccessibilityService.instance == null) {
            AlertDialog.Builder(this)
                .setTitle("Gelişmiş onarım (isteğe bağlı)")
                .setMessage(getString(R.string.a11y_prompt))
                .setPositiveButton("Ayarı Aç") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                .setNegativeButton("Şimdi değil", null).show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Ağ ayarları sıfırlansın mı?")
                .setMessage("Kayıtlı Wi-Fi şifreleri silinir. Son onayı siz vereceksiniz.")
                .setPositiveButton("Evet, ekranı aç") { _, _ ->
                    startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    MacroAccessibilityService.instance?.startMacro(MacroAccessibilityService.NETWORK_RESET) { }
                }
                .setNeutralButton("Veri tasarrufunu aç") { _, _ ->
                    startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    MacroAccessibilityService.instance?.startMacro(MacroAccessibilityService.DATA_SAVER) { }
                }
                .setNegativeButton("Hayır", null).show()
        }
    }
}
