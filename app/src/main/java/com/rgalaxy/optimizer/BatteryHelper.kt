package com.rgalaxy.optimizer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** Batarya ve Veri Tasarrufu yönlendirmeleri. Hepsi kullanıcı onayı gerektirir; sessizce yapılamaz. */
object BatteryHelper {
    fun isExempt(ctx: Context, pkg: String = ctx.packageName): Boolean =
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(pkg)

    /** Bu uygulama için muafiyet isteği (sistem diyaloğu açar). */
    fun requestSelfExemption(ctx: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * V2rayNG için başka uygulamanın muafiyeti istenemez; genel batarya
     * optimizasyon listesini açarız, kullanıcı V2rayNG'yi "Optimize etme"ye alır.
     */
    fun batteryListForV2ray(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** V2rayNG'nin uygulama bilgisi ekranı (Batarya > Kısıtlama yok). */
    fun v2rayAppInfo(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${VpnChecker.V2RAY_PKG}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Veri Tasarrufu muafiyeti (V2rayNG sınırsız veri kullanabilsin). */
    fun dataSaverForV2ray(): Intent =
        Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, Uri.parse("package:${VpnChecker.V2RAY_PKG}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
