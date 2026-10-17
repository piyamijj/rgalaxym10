# Tek Tıkla Sistem ve Ağ Optimizasyonu (Samsung Galaxy M10, Android 10)

Root gerektirmeyen, yaşlı kullanıcılar için tek büyük düğmeli uygulama: **"Sorunları Çöz ve Optimize Et"**.

## Ne yapar?
1. **Ağ/DNS yenileme** – uygulama içi DNS önbelleğini kapatır, `requestNetwork` ile ağı yeniden talep eder, `bindProcessToNetwork` ile alttaki ağı tazeler.
2. **VPN kontrolü** – aktif VPN'i (TRANSPORT_VPN) algılar, V2rayNG kurulu mu bakar, `127.0.0.1:10808/10809` portlarına gerçek TCP denemesi yapar.
3. **Canlı teşhis** – her ağ (Wi-Fi/mobil/VPN) için DNS → TCP → TLS el sıkışması testi; sorunun DNS mi, yönlendirme mi, sertifika mı, protokol mü olduğunu sade Türkçe gösterir (Logcat etiketi: `RGalaxyDiag`).
4. **Yönlendirmeler** – batarya optimizasyonu muafiyeti, V2rayNG uygulama bilgisi, Veri Tasarrufu muafiyeti ekranları.
5. **Erişilebilirlik makrosu** – Samsung Ayarlar'da "Genel yönetim > Sıfırla > Ağ ayarlarını sıfırla" ve "Veri tasarrufu" öğelerini metne göre bulup dokunur.

## Dürüst sınırlar (root yok)
- Android 10'da **sistem DNS önbelleğini silen genel API yoktur**; yapılan işlem en iyi çabadır. Kesin temizlik için Uçak Modu aç/kapa veya ağ sıfırlama gerekir.
- **Başka uygulamanın (V2rayNG) batarya muafiyeti kod ile verilemez**; uygulama yalnızca ilgili ekranı açar, kullanıcı seçer. Aynısı Veri Tasarrufu için geçerlidir.
- `VpnService.prepare()` yalnızca bu uygulamanın iznini söyler, V2rayNG'yi etkilemez.
- 127.0.0.1 testi gerçek bağlantı denemesidir ama "IPv4 köprü" doğrulaması **kısmen simüledir**: portun dinlendiğini gösterir, tünelin internete çıkışı VPN satırındaki TLS testiyle anlaşılır.
- Erişilebilirlik makrosu Samsung menü yazılarına (Türkçe/İngilizce) bağlıdır; One UI sürümüne göre bulunamayabilir (45 sn sonra durur). **Ağ sıfırlamanın son onayına bilerek basılmaz** (Wi-Fi şifrelerini siler).
- Uygulama sorunu garanti çözmez; kök neden çoğunlukla V2rayNG sunucu/DNS ayarıdır.

## Derleme ve yayın (GitHub Actions)
`main` dalına her push, APK'yı derler (JDK 17, Gradle 8.4, AGP 8.2.2), `v1.0.<çalıştırma no>` etiketiyle **Releases**'e yükler. Yerleşik `GITHUB_TOKEN` yeterlidir (`permissions: contents: write`).

Release hata verirse: **Settings > Actions > General > Workflow permissions > Read and write permissions** seçin.
PAT gerekirse: GitHub > Settings > Developer settings > Personal access tokens (classic, `repo` yetkisi) oluşturun; depoda **Settings > Secrets and variables > Actions > New repository secret**, Name: `GH_TOKEN`, Secret: jeton; sonra workflow'da `GITHUB_TOKEN` yerine `${{ secrets.GH_TOKEN }}` yazın.

## Telefona kurulum
Releases sayfasından `.apk` indirin → dosyaya dokunun → "Bilinmeyen kaynaklara izin ver" → Kur. APK debug anahtarıyla imzalıdır (Play Store için değil).
Erişilebilirlik: Ayarlar > Erişilebilirlik > Yüklü uygulamalar > "Tek Tıkla Optimizasyon Yardımcısı" > Açık.
