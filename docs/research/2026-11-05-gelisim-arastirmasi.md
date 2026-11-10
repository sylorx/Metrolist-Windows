# Metrolist Windows: sonraki gelişim alanları

Araştırma tarihi: **5 Kasım 2026**. İncelenen temel: bu çalışma ağacındaki Kotlin/Compose Windows istemcisi, `AppVersion.NAME = 1.1.0`. Bu belge bir geliştirme önerisidir; özelliklerin uygulandığı veya kullanıcı ihtiyacının kullanıcı görüşmeleriyle doğrulandığı anlamına gelmez.

## Yöntem ve kanıtların anlamı

- **Kod bulgusu:** mevcut yerel kaynakta doğrudan görülen davranış. Dosya bağlantıları verilmiştir. Bu çalışma sırasında performans benchmark’ı, cihaz laboratuvarı veya yeni canlı hesap testi yapılmadı.
- **Kaynak olgusu:** platformun veya ürünün kendi belgesi/kaynak koduyla doğrulanan imkân. Bir API’nin var olması, bu projede sorunsuz çalışacağı anlamına gelmez.
- **Öneri / çıkarım:** mevcut boşluk ve kaynak olgusundan üretilen tasarım kararı; garanti veya ölçülmüş sonuç değildir.
- **Efor:** S = mevcut modülde sınırlı genişletme; M = birden çok modül ve entegrasyon testi; L = yeni protokol, yerel köprü veya veri geçişi. Takvim taahhüdü değildir. Native Windows ve üçüncü taraf hizmet belirsizlikleri eforu büyütebilir.

## Mevcut özellikleri yeniden önermeme sınırı

Projede zaten sıra/liste düzenleme, çoklu seçim, üç sabit akıllı liste, 10 bant ekolayzır, RMS tabanlı ses dengeleme, crossfade/sonraki parçayı hazırlama, yerel dosya taraması, zamanlı LRC sözleri, dinleme istatistikleri, uyku zamanlayıcısı, JSON yedekleme, DPAPI oturum saklama ve uygulama içinden güncelleme bulunuyor. Bunlar yeniden “yeni özellik” olarak sayılmamalı. Bunların üzerine inşa edilen albüm ses dengesi, artımlı indeksleme ve imzalı yayın ise farklı geliştirmelerdir. Yerel dayanaklar: [README](../../README.md), [PlaybackController](../../desktop/src/main/kotlin/com/metrolist/desktop/PlaybackController.kt), [LibraryModels](../../desktop/src/main/kotlin/com/metrolist/desktop/LibraryModels.kt), [Updater](../../desktop/src/main/kotlin/com/metrolist/desktop/Updater.kt).

## Araştırmanın sonucu

**Ürün önerim:** Metrolist’i kişisel keşif, iyi yönetilen karma kitaplık ve Windows’ta sorunsuz günlük kullanım etrafında derinleştirmek. En güçlü yeni özellik paketi **kişisel DJ + kullanıcı tanımlı akıllı listeler + birleşik arama**. Bunu taşıyacak temel ise mantıksal parça kimliği, ayrı dinleme olayları, sağlam cihaz/medya entegrasyonu ve tanılama. Bu seçim kullanıcı araştırmasından çıkmış istatistik değil; mevcut kod ile aşağıdaki ürün/API incelemesine dayanan değerlendirmedir.

| Öncelik | Geliştirme | Kullanıcıya somut katkı | İlk efor | Bağımlılık |
|---|---|---|---|---|
| P0 | Tanılama ve gerçek yayın/yükseltme testi | Çalmama/güncellenmeme sebebini anlayabilme | S/M | A7–A8 |
| P1 | Kişisel DJ ve olumsuz geri bildirim | Aynı sanatçıya takılmayan, tercihleri öğrenen sıra | M | B1; dinleme olayları |
| P1 | Kural editörlü akıllı listeler | Kullanıcının kendi otomatik koleksiyonları | M | B2; metadata alanları |
| P1 | Birleşik arama ve komut paleti | YouTube, yerel müzik ve listeleri tek yerden bulma | M | B3; büyük ölçekte A5 |
| P1 | Artımlı tarama ve kalıcı parça kimliği | Yeni/taşınan dosyalarda kitaplığın bozulmaması | M | A4; B4 |
| P1 | Windows medya ve cihaz geçişleri | Kilit ekranı, kulaklık ve başka oynatıcılarla uyum | M/L | A1–A2 |
| P1 | Klavye/ekran okuyucu akışı | Fareye bağımlı olmadan kullanım | M | A6 |
| P2 | Last.fm/ListenBrainz bağlantısı | İstatistikleri diğer dinleme ortamlarıyla birleştirme | M | B5; olay/outbox modeli |
| P2 | Metadata düzeltme ve mükerrer adayları | Daha temiz arşiv ve daha doğru eşleşme | M/L | B4; A4–A5 |
| P2 | Telefondan kumanda ve OBS/Discord | Masadan uzakta ve yayın sırasında kontrol | M/L | B6–B7; ortak durum akışı |
| P2 | Gerçek hesap uzlaştırması | Uzak ve yerel liste değişikliklerini görünür eşitleme | L | B9; kararlı hesap kimliği |
| P3 | NAS/OpenSubsonic kaynağı | Kişisel sunucudaki müziği aynı uygulamada dinleme | L | B8; kaynak soyutlaması |
| P3 | Podcast/radyo ve çalışma araçları | Konuşma/öğrenme için ayrı kullanım biçimi | M/L | B10; kaynak yetenekleri |

P0, uygulamayı dağıtmayı mümkün kılan temel işlerdir. P1, çoğu günlük kullanıcıya etkisi yüksek görülen işlerdir. P2/P3 sırası kullanım profiline göre değişir; örneğin NAS sahibi biri için B8 çok daha yukarı çıkar.

## Benzer ürünlerden çıkarılan dersler

| Birincil kaynak | Doğrulanan yön | Metrolist’e çıkarım |
|---|---|---|
| [MusicBee resmi sitesi](https://getmusicbee.com/) | Otomatik etiketleme, WASAPI/ASIO, podcast ve radyo özellikleri listeleniyor. | Arşivi düzenleme ve cihaz davranışı, görünüm kadar değerli. Sitedeki RAM/paket boyutu gibi farklı koşullardaki sayılar bu projeyle kıyaslanmadı; eski servis entegrasyonu metinleri öneriye temel alınmadı. |
| [foobar2000 resmi özellikleri](https://www.foobar2000.org/?page=Overview) | ReplayGain, gelişmiş etiketleme, özelleştirilebilir kısayollar ve bileşen mimarisi. | Sesin ölçülmesi ve kullanıcı iş akışlarının genişletilmesi önemli; eklenti pazaryeri ise erken aşamada zorunlu değil. |
| [Feishin resmi README](https://github.com/jeffvli/feishin/blob/development/README.md) | MPV/web arka uçları, sunucuya scrobble ve Navidrome akıllı liste editörü; belgede farklı sunucu türleri de tanımlı. | İleri akıllı liste ve kişisel sunucu desteği karşılığı olan ürün yönleri. Başka uygulamanın motorunu değiştirmek tek başına kalite garantisi değil. |
| [Pear Desktop eklenti kaynakları](https://github.com/pear-devs/pear-desktop/tree/master/src/plugins) | Kaynak ağacında `discord`, `amuse`, `api-server`, `music-together` gibi modüller mevcut. | Yayıncı/entegrasyon özellikleri araştırmaya değer. Klasör varlığı kusursuz çalışmanın veya kullanıcı talebinin kanıtı değil; Electron eklentisi Kotlin’e doğrudan taşınabilir varsayılmadı. |

## A. Windows, ses, kitaplık ve yayın altyapısı

### A1. Windows medya bütünleşmesini tamamlama — öncelik yüksek, efor M/L

**Kod bulgusu:** SMTC tamamen eksik değil. [`MediaKeys.kt:85`](../../desktop/src/main/kotlin/com/metrolist/desktop/MediaKeys.kt#L85) zaten `Windows.Media.SystemMediaTransportControls` için WinRT/COM köprüsü kurmaya çalışıyor; `sync` oynatma durumunu iletiyor. Ancak sanatçı/başlık/kapak, zaman çizelgesi ve sistemden seek isteği için tamamlanmış akış görünmüyor. Ayrıca global klavye hook’u ve hotkey kaydı kullanılıyor; başka medya uygulamalarıyla birlikte çalışması test gerektiriyor. Bu bulgu “Windows entegrasyonu hiç yok” diye sunulmamalı.

**Kaynak olgusu:** Microsoft’un masaüstü `GetForWindow` API’si uygulamanın üst düzey HWND penceresine SMTC bağlar. SMTC metadata/kapak, zaman çizelgesi ve konum değiştirme isteklerini destekler. [Masaüstü interop](https://learn.microsoft.com/en-us/windows/win32/api/systemmediatransportcontrolsinterop/nf-systemmediatransportcontrolsinterop-isystemmediatransportcontrolsinterop-getforwindow), [SMTC durum/metadata/zaman çizelgesi](https://learn.microsoft.com/en-us/windows/apps/develop/media-playback/system-media-transport-controls).

**Öneri:** ham vtable kullanımını dar bir `WindowsMediaSession` modülüne kapat; başlık/sanatçı/albüm/kapak ve pozisyonu yayımla. Sistemden play/pause/seek/next çağrılarını `PlaybackController` komutlarına dönüştür. Global medya tuşlarını zorunlu tek sahiplenme yerine kontrollü yedek yol olarak değerlendir.

**Kabul ölçütleri:** Windows medya panelindeki bilgi parça değişince yenilenir; sistemden seek uygulamada doğru konuma gider; tarayıcı/başka oynatıcı açıkken tek tuş çift komut üretmez; kilit, uyku ve uygulama kapanışında kaynaklar temizlenir. Bunlar gerçek Windows cihaz testleri ister.

### A2. Kulaklık çıkarılmasını ve çıkış değişimini yönetme — yüksek, efor M

**Kod bulgusu:** [`FfmpegPlayer.kt:172`](../../desktop/src/main/kotlin/com/metrolist/desktop/FfmpegPlayer.kt#L172) cihazı Java Sound mixer adından seçiyor. [`AudioProcessor.kt:15`](../../desktop/src/main/kotlin/com/metrolist/desktop/AudioProcessor.kt#L15) cihaz listesini okuyor; takılma/çıkarılma ve varsayılan endpoint değişimini izleyen bir yaşam döngüsü görünmüyor. Mixer adını kalıcı kimlik gibi kullanmak da ileride belirsizlik yaratabilir.

**Kaynak olgusu:** Windows `IMMNotificationClient`, cihaz ekleme/çıkarma, durum değişimi ve varsayılan ses cihazının değişmesi için bildirim verir; endpoint kimliği opak kabul edilmelidir. Callback’in bloklamaması gerekir. [Microsoft MMDevice bildirimleri](https://learn.microsoft.com/en-us/windows/win32/api/mmdeviceapi/nn-mmdeviceapi-immnotificationclient).

**Öneri:** “Windows varsayılanını izle”, “bu cihazda kal” ve “kulaklık çıkınca duraklat” davranışlarını açık kullanıcı tercihleri yap. Yeniden bağlantıda kaldığı konumu koru; callback yalnız olay kuyruğuna yazsın, ağır açma/kapama işlemini ses işçisi yapsın.

**Kabul ölçütleri:** USB/Bluetooth kulaklık çıkarılırken çökmez; hoparlöre beklenmedik yüksek sesle geçiş önlenir; varsayılan cihaz değişimi seçilen politikaya uyar; bağlantı dönüşü çift ses akışı üretmez. Hedef davranış sürücü/Bluetooth donanımı üzerinde doğrulanmalı.

### A3. Albüm ses dengesi ve ölçülebilir ses kalitesi — orta, efor M; özel çıkış L

**Kod bulgusu:** [`AudioProcessor`](../../desktop/src/main/kotlin/com/metrolist/desktop/AudioProcessor.kt) kayan RMS tahminiyle dinamik kazanç ayarlıyor. Bu, mevcut ses dengelemenin varlığını gösterir; albüm bazlı ReplayGain veya ölçülmüş entegre LUFS kaydı değildir. [`FfmpegPlayer.kt:119`](../../desktop/src/main/kotlin/com/metrolist/desktop/FfmpegPlayer.kt#L119) çıktıyı 48 kHz/stereo/16-bit olarak sabitliyor; bu nedenle “bit-perfect” iddiası yapılmamalı.

**Kaynak olgusu:** FFmpeg `loudnorm`, EBU R128 tabanlı entegre ses yüksekliği, loudness range ve true-peak hedeflerini; tek/çift geçiş kiplerini destekler. [FFmpeg loudnorm](https://ffmpeg.org/ffmpeg-filters.html#loudnorm). WASAPI exclusive kipinin paylaşımlı kipten farklı cihaz erişimi/uyumluluk ödünleşimleri vardır. [Microsoft exclusive-mode streams](https://learn.microsoft.com/en-us/windows/win32/coreaudio/exclusive-mode-streams).

**Öneri:** önce yerel dosyalar için tarama sırasında ölçüm önbelleği, parça/albüm modu ve kazanç/tepe sınırı ekle. Albüm modunda parçalar arası kasıtlı seviye ilişkisini koru. WASAPI shared/exclusive seçeneğini ayrı bir ileri aşama yap; mevcut Java Sound yolunu sağlam yedek olarak tut. “Daha yüksek örnekleme her zaman daha iyi ses” veya internet kaynağını kayıpsız yapma vaadi verme.

**Kabul ölçütleri:** referans test dosyalarında LUFS/true-peak ölçümü doğrulanır; albüm modunda göreli seviyeler korunur; EQ + normalize + crossfade birlikte clipping testinden geçer; unsupported cihaz biçimi yedek yola döner. Müzik ve konuşma hedefleri ayrı değerlendirilmeli.

### A4. Kendiliğinden güncellenen yerel kitaplık ve taşınan dosyaları bulma — yüksek, efor M

**Kod bulgusu:** [`LocalMusic.kt:22`](../../desktop/src/main/kotlin/com/metrolist/desktop/LocalMusic.kt#L22) tam `Files.walk` yapıyor ve her ses dosyası için FFmpeg’i yeniden açıyor. Kimlik canonical dosya yolunun hash’inden türetiliyor. Dosyanın taşınması, aynı içeriğin yeni kimlik almasına yol açabilir; bunun geçmiş/beğeni etkisi uygulama tasarımında çözülmeli. Mevcut kodda mtime okunuyor, fakat bir indeks önbelleğiyle değişmeyeni atlama akışı görünmüyor.

**Kaynak olgusu:** JDK `WatchService` dizin olaylarını izler; `OVERFLOW` durumunda yeniden tarama gerekir ve uzak dosya sistemindeki değişimlerin algılanması garanti edilmez. [Java 21 WatchService](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/WatchService.html).

**Öneri:** ilk indeksleme + dosya boyutu/mtime ile artımlı tarama + debounce edilmiş dizin izleme. USB/NAS geri geldiğinde uzlaştırma taraması; eksik dosyaya “yeniden bağla”; yol kimliğini mantıksal şarkı kimliğinden ayır. İçerik hash’i yalnız gerektiğinde hesaplanmalı; her açılışta tüm ses dosyasını hash’lemek taramayı ağırlaştırabilir.

**Kabul ölçütleri:** değişmeyen dosyada yeniden metadata çözümlemesi yapılmaz; tek yeni dosya artımlı eklenir; olay taşması tam uzlaştırma tetikler; klasör taşımada beğeni/geçmiş korunur; çıkarılan diskteki kayıtlar sessizce silinmez. Performans kazancı ayrıca ölçülmeli.

### A5. Büyük kitaplık için indeksli arama ve ilişkisel kayıt — yüksek temel yatırım, efor L

**Kod bulgusu:** [`Store.kt`](../../desktop/src/main/kotlin/com/metrolist/desktop/Store.kt) atomic replace ve `.bak` kurtarması sağlıyor; bu değerli bir mevcut koruma. Bununla birlikte liste/geçmiş/dinleme kayıtları güncellenirken belgenin tamamı JSON’a yazılıyor. Büyüyen kitaplıkta arama, ilişki ve atomik çoklu değişiklik için yeni bir veri katmanı düşünülmeli. “JSON bugün yavaş” ölçülmüş bir sonuç değildir.

**Kaynak olgusu:** SQLite FTS5 tam metin indekslemesi sunar. WAL modunda okuma/yazma eşzamanlılığı artabilir; yine tek yazar ve checkpoint yönetimi vardır. WAL ağ dosya sistemine konacak ortak canlı veritabanı çözümü değildir. [FTS5](https://www.sqlite.org/fts5.html), [WAL](https://www.sqlite.org/wal.html).

**Öneri:** şarkı, kaynak kimliği, dosya, liste üyeliği, dinleme olayı ve sync outbox’ı tek yerel SQLite şemasında toplamak; FTS ile başlık/sanatçı/albüm araması; aşamalı JSON migration ve geri dönüş kopyası. SQLite dosyasını bulut klasöründen eşzamanlı açtırmak yerine uygulama seviyesinde eşitleme tasarla. Yedekleme WAL dosyası dikkate alınmadan ham DB kopyasına indirgenmemeli.

**Kabul ölçütleri:** belirlenmiş referans makinede 10k/50k/100k parça için açılış, arama p50/p95, bellek ve yazma gecikmesi ölçülür; işlem yarıda kesildiğinde liste üyeliği/outbox tutarlıdır; Türkçe `I/İ/ı/i` ve aksan eşleşmesi için açık arama kuralları ve testler vardır. Hedef süreler benchmark’tan sonra konur.

### A6. Tam klavye kullanımı ve ekran okuyucu desteği — yüksek, efor M

**Kod bulgusu:** [`Ui.kt`](../../desktop/src/main/kotlin/com/metrolist/desktop/Ui.kt) kısayollar ve birçok `contentDescription` içeriyor. Bununla birlikte global Space/Ctrl kısayolları, özel tıklanabilir kontroller, sürükleme tutamaçları ve odak düzeni için uçtan uca erişilebilirlik testi bulunmuş sayılmaz. [`desktop/build.gradle.kts`](../../desktop/build.gradle.kts) içinde `jdk.accessibility` modülü açıkça seçilmemiş; üretilen runtime ayrıca incelenmeli.

**Kaynak olgusu:** Compose Desktop Windows erişilebilirliği Java Access Bridge üzerinden sağlanır; JetBrains belgesi dağıtıma `jdk.accessibility` modülünün eklenmesini ve NVDA/JAWS ile doğrulamayı anlatır. [Kotlin/JetBrains masaüstü erişilebilirlik](https://kotlinlang.org/docs/multiplatform/compose-desktop-accessibility.html).

**Öneri:** fare olmadan arama → sonuç → sıraya ekleme → sıra düzenleme → ayar → geri dönüş akışını tamamla. Görünür odak halkası, anlamlı rol/durum açıklaması, yeniden atanabilir kısayollar ve sürüklemeye klavye alternatifi ekle. Metin alanında yazılan boşluk oynatmayı değiştirmemeli. Az hareket ve yüksek kontrast tercihleri ayrıca tasarlanmalı.

**Kabul ölçütleri:** tüm temel akış Tab/Shift+Tab/Enter/Escape ile tamamlanır; ekran okuyucu parça ve düğmenin durumunu doğru söyler; odak panel aç/kapatmada kaybolmaz; büyük yazıda kontrol kesilmez. Destek, paketlenmiş EXE + gerçek ekran okuyucu üzerinde doğrulanmalı; yalnız semantik etiket eklemek yeterli kanıt değildir.

### A7. “Neden çalmadı?” tanılama merkezi ve performans bütçesi — yüksek, efor S/M

**Kod bulgusu:** [`StreamResolver.kt`](../../desktop/src/main/kotlin/com/metrolist/desktop/StreamResolver.kt) çıkarıcı logger’ını boş callback’e bağlıyor; bazı Windows hataları yalnız `println` ile yazılıyor. Güncellemenin kendi günlükleri mevcut, fakat kaynak çözümleme → bağlantı → decoder → ses aygıtı zincirini bir arada açıklayan kullanıcı tanılama ekranı görünmüyor. Bu araştırmada gerçek arıza oranı ölçülmedi.

**Öneri:** son başarısız çalma denemelerini “kaynak bulunamadı / HTTP / decoder / aygıt / izin” olarak ayıran yerel tanılama; aşama süreleri; çalışan akış kaynağı ve codec bilgisi; kullanıcı tarafından önizlenebilen maskelenmiş destek dosyası. Çerez, yetkilendirme başlığı, tam imzalı akış URL’si ve özel klasör yolu varsayılan dışa aktarıma alınmamalı. Otomatik uzaktan telemetri zorunlu değil.

**Kaynak olgusu:** JDK Flight Recorder uygulamaya özel olaylar ve sürelerle teşhis verisi toplayabilir; `jfr` aracı kayıtları özetleyebilir. [Java 21 JFR API](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jfr/jdk/jfr/package-summary.html), [jfr aracı](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jfr.html).

**Kabul ölçütleri:** başarısız deneme kullanıcıya uygulanabilir bir sebep gösterir; test sırları raporda bulunmaz; günlük boyutu sınırlıdır; belirli senaryoda açılış süresi, ilk ses süresi, seek gecikmesi ve uzun süreli bellek değişimi ölçülür. Varsayımsal “%50 hızlanma” hedefi verilmemeli.

### A8. İmzalı, doğrulanabilir yayın ve gerçek yükseltme testi — yüksek, efor M + dış bağımlılık

**Kod bulgusu:** [`UpdateService.kt`](../../desktop/src/main/kotlin/com/metrolist/desktop/UpdateService.kt) sürüm paketini boyut/SHA-256/EXE başlığıyla doğruluyor; [`Build-Release.ps1`](../../scripts/Build-Release.ps1) checksum üretiyor. İncelenen yayın betiğinde Authenticode imzalama veya imzayı kontrol etme adımı yok. Bu, geçmişteki tüm dağıtımların imzasız olduğunun kanıtı değildir; mevcut otomasyonun kapsamını gösterir.

**Kaynak olgusu:** SignTool dosya imzalama, zaman damgası ve imza doğrulama işlemleri sağlar. Geçerli imza, SmartScreen uyarılarının her yeni dosyada kesin olarak kalkacağını garanti etmez; Microsoft bunu itibar sinyalleriyle birlikte değerlendirir. [SignTool](https://learn.microsoft.com/en-us/windows/win32/seccrypto/signtool), [Microsoft SmartScreen geliştirici rehberi](https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/smartscreen-reputation).

**Öneri:** tutarlı yayıncı kimliğiyle EXE/kurucuyu imzala, zaman damgası ekle ve yayın öncesi imzayı doğrula. Hash bütünlüğüne ek olarak kurulumda beklenen yayıncı kontrolünü değerlendir. Sürüm notu, kararlı/beta kanalı ve CI’da N−1 → N gerçek yükseltme/kurulum iptali testleri oluştur. İmzalama sırası hash üretiminden önce olmalı. Microsoft Store ayrı fizibilite olarak değerlendirilmeli; mevcut üçüncü taraf istemcinin kabul edileceği varsayılmamalı.

**Kabul ölçütleri:** yayınlanan dosya imzası doğrulanır; yanlış yayıncı reddedilir; başarısız kurulumdan sonra uygulama/veri açılır; UAC iptali kayıp yaratmaz; kullanıcı verisi taşınmadan sürüm yükselir. Sertifika/kimlik doğrulama ve olası hizmet maliyeti koddan bağımsız dış bağımlılıklardır; bu rapor satın alma önerisi vermez.

## Platform tarafında önerilen sıralama

1. Tanılama ve tekrarlanabilir cihaz/oynatma testleri; Windows medya bilgilerini tamamlama; erişilebilirlikte temel akış.
2. Artımlı kitaplık indeksleme ve taşınan dosyayı bağlama; ardından ölçülen ölçeğe göre SQLite/FTS geçişi.
3. Cihaz değişimini yönetme; albüm ses dengesi ve kalite testleri.
4. İmzalı yayın ve gerçek yükseltme testleri; WASAPI exclusive gibi daha dar kullanıcı kitlesine hitap eden seçenekler bundan sonra.

Bu sıralama araştırmacı değerlendirmesidir. Kullanıcıların çoğu yalnız çevrimiçi akış dinliyorsa büyük yerel kitaplık yatırımı geriye; büyük FLAC arşivi kullanıyorsa indeks/kimlik/metadata işi öne alınmalıdır.

## B. Yeni ürün özellikleri ve entegrasyonlar

Aşağıdaki MVP kapsamları ve kabul ölçütleri öneridir. API davranışıyla ilgili olgular kendi kaynaklarıyla verilmiştir; ürünün daha çok kullanılacağına dair ölçülmüş iddia yoktur.

Bu bölümdeki kod bulgularının birincil yerel kaynakları: [radyo/oynatma akışı](../../desktop/src/main/kotlin/com/metrolist/desktop/PlaybackController.kt), [akıllı listeler ve istatistik sıkıştırma](../../desktop/src/main/kotlin/com/metrolist/desktop/LibraryModels.kt), [arama ve podcast yönlendirmesi](../../desktop/src/main/kotlin/com/metrolist/desktop/AppModel.kt), [parça modeli](../../desktop/src/main/kotlin/com/metrolist/desktop/Models.kt), [dosya etiketlerini okuma](../../desktop/src/main/kotlin/com/metrolist/desktop/LocalMusic.kt), [eşitleme ve oturum işaretçisi](../../desktop/src/main/kotlin/com/metrolist/desktop/LibrarySync.kt), [veri kaydı](../../desktop/src/main/kotlin/com/metrolist/desktop/Store.kt).

### B1. Tercihleri öğrenen kişisel DJ — P1, efor M

**Mevcut durum:** `PlaybackController.ensureRadio`, YouTube `next` sonuçlarından, yeterli sonuç yoksa başlık/sanatçı aramasından sırayı uzatıyor. Aynı kuyruktaki ID’leri ayıklıyor. “Bu sanatçıyı daha az çal”, yakın geçmiş tekrar sınırı veya keşif oranı için açık model yok.

**MVP önerisi:** “Tanıdık / Dengeli / Keşif” kipi; parça ve sanatçı için açık olumlu/olumsuz geri bildirim; yakın geçmişe göre tekrar aralığı; aynı sanatçının art arda gelmesini sınırlama; “Neden önerildi?” açıklaması. Manuel her atlamayı otomatik nefret sinyali saymamak gerekir. İlk sürüm yerel kurallarla çalışabilir; ücretli bir yapay zekâ servisi zorunlu değil.

**Doğrulanan imkân:** [Last.fm track.getSimilar](https://www.last.fm/api/show/track.getSimilar), API anahtarıyla benzer parça metadata’sı verir. [ListenBrainz öneri API’si](https://listenbrainz.readthedocs.io/en/latest/users/api/recommendation.html), kayıt MBID’leri ve puanlar verir; belge bu endpoint’i deneysel olarak işaretliyor ve bazı kullanıcılar için öneri henüz üretilmemiş olabilir. Bunlar müziğin kendisini veya oynatılabilir akışı sağlamış sayılmaz.

**Bağımlılık / ödünleşim:** parça kimliği ve B5’teki dinleme olayları. Dış önerinin, mevcut kaynakta doğru kayda eşlenmesi ve erişilebilirliğinin kontrolü ayrı iştir. Soğuk başlangıçta seçilen tür/sanatçılarla başlamak mümkün.

**Kabul:** engellenen sanatçı otomatik sıraya girmez; açıklanan tekrar politikası uygulanır; öneri servisi yokken yerel kural yolu çalışır; kullanıcı DJ tercihlerini sıfırlayabilir. Başarı değerlendirmesi öncesi atlama ve kabul edilen öneri oranının ne anlama geldiği belirlenmeli.

### B2. Kullanıcı tanımlı akıllı liste editörü — P1, efor M

**Mevcut durum:** `SmartRule` yalnız `RecentLikes`, `Forgotten`, `MostPlayed` içeriyor. Bu bir otomatik liste altyapısı; serbest kural editörü değil.

**MVP önerisi:** VE/VEYA grupları; sanatçı, albüm, süre, beğeni, son dinleme ve çalma sayısı kuralları; sıralama, parça/süre limiti; kaydetmeden önce sonuç önizlemesi. Örnek: “Beğendiğim, son 60 günde dinlemediğim, 2–6 dakika arası 30 şarkı”. Sonraki aşamada tür/yıl/dil eklenebilir; mevcut `Track` bu alanları taşımadığı için önce B4 gerekir. YouTube yüklenme tarihini şarkının çıkış yılı diye kullanmamak gerekir.

**Dayanak:** Feishin’in resmi README’si akıllı liste editörünü doğruluyor; buradaki yerel kural kapsamı bizim tasarım önerimizdir. [Feishin](https://github.com/jeffvli/feishin/blob/development/README.md).

**Kabul:** iç içe kurallar yeniden açılınca aynı kalır; boş sonuç anlaşılır açıklanır; kaynak kaybı listeleri silmez; kural önizlemesi ile kaydedilen sonuç aynı değerlendirme motorunu kullanır. “Doğal dille liste oluştur” daha sonra bu sınırlı kural modeline çevrilebilir; ilk sürümde genel bir sohbet sistemi kurmak gerekmez.

### B3. Birleşik arama, komut paleti ve oturumlar — P1, efor M

**Mevcut durum:** `AppModel.search` çevrimiçi YouTube araması yapıyor; yerel listelerin kendi filtreleri var. Kaynakları birlikte arayan tek bir sonuç indeksi veya komut paleti görünmüyor.

**MVP önerisi:** tek aramada yerel şarkı + beğeniler + listeler + çevrimiçi sonuçlar; kaynağı belirten rozet; başlık/sanatçı/albüm alanı ayrımı; Türkçe harf ve aksan politikası; yazım hatasına tolerans. Ctrl+K paletinden “sıraya ekle”, “çıktı cihazını değiştir”, “sözlere git”. “Çalışma”, “oyun”, “akşam” gibi adlandırılmış oturumlar kendi kuyruk ve filtrelerini hatırlayabilir. Kuyruğun açılışta geri gelmesi zaten mevcut; yeni fark birden çok kaydedilmiş çalışma bağlamıdır.

**Bağımlılık:** A5’te önerilen yerel indeks, büyüyen kitaplıkta yardımcı olabilir. SQLite’ın [FTS5](https://www.sqlite.org/fts5.html) yeteneği kaynak olgusudur; bulanık eşleşme ve Türkçe normalizasyon için ayrıca tasarım/test gerekir.

**Kabul:** internet yokken yerel sonuçlar çalışır; eski çevrimiçi sorgunun geç cevabı yeni sorguyu değiştirmez; tüm eylemler klavyeden tamamlanır; kaynağı yanlış eşleşen iki parça sessizce birleştirilmez. Referans veri kümesinde arama gecikmesi ölçülmeden “anlık” garantisi verilmez.

### B4. Akıllı metadata ve mükerrer kayıt adayları — P2, efor M/L

**Mevcut durum:** `LocalMusic` dosya etiketleri ve kapak okuyor; `Track` temel başlık/sanatçı/albüm/süre tutuyor. Tür, yıl, disk/parça numarası, ayrı sanatçı kimliği, MBID/ISRC ve kayıt-sürüm ayrımı yok. A4’teki dosya yolu kimliği de taşımaya duyarlı.

**MVP önerisi:** önce okunan alanları genişlet; yanlış etiketler için düzeltme önerisi ve önce/sonra önizleme göster; kapak ve albüm eşleştirmesini kullanıcı seçsin. Aynı başlık tek başına mükerrer kanıtı sayılmasın. Bir canlı kayıt, remaster ve stüdyo kaydını farklı sürümler olarak tut. Dosyaya toplu yazma daha sonra geri alma/kopya korumasıyla eklenebilir; ilk aşamada yalnız uygulama içi düzeltme daha sınırlı değişikliktir.

**Kaynak olgusu:** [MusicBrainz API](https://musicbrainz.org/doc/MusicBrainz_API) sanatçı/kayıt/yayın metadata’sında arama ve ilişkili varlıkları bulmayı destekler. [AcoustID](https://acoustid.org/webservice) Chromaprint parmak iziyle ilişkili MusicBrainz metadata’sını aramaya olanak verir. AcoustID uygulama kaydı ister, ücretsiz hizmette ticari kullanım koşulu ve saniyede 3 istek sınırı belirtir. MusicBrainz [anlamlı User-Agent ve normalde saniyede yaklaşık 1 istek/IP](https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting) ister; önbellek ve istek kuyruğu tasarlanmalı. Ticari dağıtım koşulları uygulamadan önce yeniden doğrulanmalı.

**Kabul:** eşleşme güveni ve kaynak görünür; belirsiz sonuç otomatik dosya yazmaz/silmez; metadata değişiminde liste üyeliği ve geçmiş korunur; mükerrer adaylarında kullanıcının sürüm tercihi korunur. İsim benzerliği veya parmak izi tek başına her sürümün aynı kayıt olduğunu garanti etmez.

### B5. Last.fm ve ListenBrainz scrobbling — P2, efor M

**Mevcut durum:** yerel dinleme süresi tutuluyor. Ancak `Store.addSession` / `compactListening` kayıtları gün-parça bazında birleştiriyor. Bu toplamdan her bir dinlemenin başlangıcını, gerçek süresini ve bitme/atlama nedenini eksiksiz geri üretmek mümkün değil.

**MVP önerisi:** çalma motorundan `playId`, kayıt kimliği, başlangıç, aktif dinleme süresi ve bitiş nedeni olan olay üret. Günlük istatistik bu olaylardan ayrı bir özet olsun. Yalnız kullanıcı açtığında dış hizmete gönder; başarısız gönderimler kalıcı outbox’ta beklesin. Özel dinleme kipi hem Now Playing hem geçmiş gönderimini durdursun. Yetkilendirme bilgileri mevcut DPAPI yaklaşımını genelleştiren bir credential modülünde saklanabilir.

**Kaynak olgusu:** [Last.fm scrobbling](https://www.last.fm/api/scrobbling) için parça 30 saniyeden uzun olmalı ve yarısı veya 4 dakika dinlenmeli; erken olan eşik kullanılır. [ListenBrainz submit-listens](https://listenbrainz.readthedocs.io/en/latest/users/api/core.html) de yarım parça/4 dakika eşiğini ve kullanıcı token’ını belgeliyor. Bunlar yalnız “parça açıldı” sinyaliyle gönderim yapılmamasını gerektirir. Last.fm ayrıca HTTP 200 sonucunun tek başına uygulama düzeyinde başarı anlamına gelmediğini açıklıyor.

**Kabul:** seek ile atlanan süre sayılmaz; duraklatma eşiği ilerletmez; yeniden başlatma/kuyruk tekrarı mükerrer gönderim üretmez; özel kipte dış istek yoktur; token/log testleri yapılır. Ağ zaman aşımından sonraki uçtan uca tam bir kez teslim garantisi, sunucu desteği değerlendirilmeden vaat edilmemeli.

### B6. Telefondan kumanda — P2, efor M/L

**MVP önerisi:** aynı ağdaki telefon tarayıcısından QR ile eşleşme; çalan parça, ses, oynat/duraklat, sonraki/önceki ve sıra yönetimi. İlk sürüm yalnız PC’deki çalmayı kontrol eder; telefona ses taşıma ve çok cihazda eşzamanlı çalma ayrı projedir.

**Teknik dayanak:** [WebSocket RFC 6455](https://www.rfc-editor.org/rfc/rfc6455), tarayıcı ile sunucu arasında çift yönlü iletişim için standarttır. Bu standart tek başına uygulamaya kimlik doğrulama, güvenli eşleşme veya gecikmesiz grup dinleme sağlamaz; bunlar bizim protokol tasarımımız olur.

**Bağımlılık / kabul:** UI’den bağımsız bir oynatma durum akışı ve komut katmanı gerekir. LAN erişimi kullanıcı tarafından açılır; süreli eşleşme token’ı, Origin denetimi, bağlantı iptali ve cihaz listesi bulunur. Eşleşmemiş tarayıcı komut veremez; yeniden bağlanmada sıra/konum uzlaşır; ilk MVP’de İnternet’e port açma/bulut servisi gerekmemeli. Yerel ağın da otomatik güvenilir sayılmaması gerekir.

### B7. Yayıncı paketi: OBS, Discord ve tam ekran görünüm — P2, efor S/M–M/L

**MVP önerisi:** OBS için şeffaf “şu an çalıyor” web katmanı veya basit metin çıktısı; kapak/sanatçı/süre; gizleme ve kısaltma seçenekleri. Ardından opt-in Discord durum kartı, dinamik kapak renkli tam ekran görünüm ve isteğe bağlı spektrum. Bugünkü masaüstü widget’ı OBS tarayıcı kaynağıyla aynı özellik değildir.

**Kaynak olgusu:** [OBS Browser Source](https://obsproject.com/kb/browser-source) yerel HTML veya URL tabanlı içerik gösterebilir. [Discord Rich Presence](https://docs.discord.com/developers/platform/rich-presence) özel metin, zaman damgası ve görsel varlıklarını destekler. Discord’un güncel SDK seçenekleri ve müzik uygulaması kullanımının uygunluğu küçük bir fizibilite çalışmasıyla doğrulanmalı; genel durum kartını Spotify’a özgü dinleme işlevleriyle eşdeğer saymamak gerekir.

**Kabul:** özel kipte kapak/başlık dışa verilmez; Discord kapalıysa oynatma etkilenmez; OBS katmanının bağlantısı oynatıcıya komut yetkisi kazandırmaz; görselleştirme gizliyken CPU kullanımı sınırlanır. Dinamik renk/görselleştirme, A6’daki az hareket ve kontrast tercihleriyle uyumlu olmalı. OBS ilk adımı, Discord SDK köprüsünden daha dar MVP olabilir; bu efor tahminidir.

### B8. NAS / Navidrome / OpenSubsonic kaynağı — P3, efor L

**MVP önerisi:** kullanıcı kendi müzik sunucusunu ekler; albüm/sanatçı/listeler, arama ve stream tek arayüzde açılır. Önce tek protokol ve tek sunucu hedefle; sonra çoklu sunucu ve Jellyfin değerlendir. Kendi sunucusundaki/kullanım hakkı olan içerikte çevrimdışı dosya yönetimi ayrı bir kaynak yeteneği olarak tasarlanabilir.

**Kaynak olgusu:** [OpenSubsonic resmi API](https://opensubsonic.netlify.app/docs/) arama, albüm/şarkı, stream, liste ve kuyruk uçlarını; sunucu eklentilerini sorgulama imkânını belgeliyor. Bu, her sunucunun her uzantıyı uyguladığı anlamına gelmez. Feishin’in sunucu istemcisi yönü karşılaştırma dayanağıdır.

**Bağımlılık:** mevcut `Track.id` / `localPath` ve `file:` kontrolünü `provider + itemId` kaynak kimliğine ayır; bir kayıt birden fazla kaynaktan çalınabilsin. Sunucudan oynatma yetkisi, indirilebilirlik, seek ve son kullanma süresi gibi yetenekler açık modellenmeli. Kullanıcı adı/token, TLS, önbellek ve sunucu sürüm uyumluluğu ayrı test ister.

**Kabul:** aynı ID’ye sahip iki sunucu parçası çakışmaz; yanlış TLS sertifikası sessizce atlanmaz; oturum yenilenmesi sonsuz retry döngüsü yaratmaz; source kaybolsa liste üyeliği korunur. SQLite dosyasını NAS üzerinde ortak canlı DB olarak açma yaklaşımı önerilmiyor; A5’teki WAL sınırlaması dikkate alınmalı.

### B9. Gerçek iki yönlü hesap eşitlemesi ve oturum yönetimi — P2, efor L

**Mevcut durum:** `LibrarySync` yerel eylemleri sırayla uzak hesaba gönderiyor; mevcut bir outbox var. Uzak tarafta sonradan yapılan değişiklikleri yerel listelerle karşılaştıran tam uzlaştırma akışı görünmüyor. Hesap işaretçisi tüm çerez metninin hash’i; çerez değişmesi, aynı gerçek kullanıcıyı kod açısından yeni oturum gibi gösterebilir. Bu bir kod modeli bulgusudur; bu araştırmada canlı hesapla hata üretilmedi.

**MVP önerisi:** kararlı hesap kimliği ve ayrı oturum geçerliliği; uzak/yerel son durum snapshot’ları; eşitleme önizlemesi; “bu bilgisayardaki sıra / uzaktaki sıra” çakışmasını açıklama; yeniden deneme ve mükerrer işlem kontrolü. Birden çok PC’ye genel bulut eşitlemesi bunun üzerine ayrı tasarlanmalı; yalnız JSON dosyasını bulut klasörüne koymak yeterli sayılmamalı.

**Doğrulanan resmî yol ve sınırı:** Google, masaüstünde [sistem tarayıcısı, loopback dönüşü ve PKCE içeren OAuth akışını](https://developers.google.com/identity/protocols/oauth2/native-app) belgeliyor. [YouTube Data API playlists](https://developers.google.com/youtube/v3/docs/playlists), liste okuma/oluşturma/düzenleme/silmeyi destekliyor. Bunlar mevcut Innertube/YouTube Music çerez uçlarına doğrudan takılabilecek eşdeğer token sistemi olduğunu kanıtlamaz. Önce hangi işlevlerin resmî API ile karşılandığını gösteren ayrı bir auth/backend fizibilitesi gerekir; “çerez yerine Google girişi eklemek kolay” denmemeli.

**Kabul:** aynı hesabın oturumu yenilendiğinde uzak liste bağlantısı kaybolmaz; başka hesapta bekleyen yazma işlemi çalışmaz; uzak listeden silinen parça iki yönlü politika uyarınca ele alınır; çakışma kullanıcı verisini sessizce ezmez.

### B10. Podcast/radyo ve müzik çalışma araçları — P3, efor M/L

**Mevcut durum:** `AppModel.openItem` YouTube `PodcastItem`/`EpisodeItem` nesnelerini açabiliyor. Bu yüzden “podcast hiç yok” demek doğru değil. Eksik alan bağımsız RSS aboneliği, bölüm bazlı kaldığın yer ve konuşmaya özgü kontroller.

**Öneri:** podcast için RSS adresinden abonelik, bölüm kuyruğu, her bölüme ayrı konum, 15/30 saniye atlama ve konuşma hızı. Radyo için ülke/dil/tür, favoriler ve kopan istasyonda yeniden bağlantı. Yerel müzik/izinli içerikte A–B döngüsü, zaman işareti ve perdeyi koruyan hız kontrolü de müzik öğrenenlere yararlı bir ayrı kip olabilir.

**Kaynak olgusu:** [Apple’ın podcast RSS gereksinimleri](https://podcasters.apple.com/support/823-podcast-requirements) bölüm GUID ve medya `enclosure` alanlarını açıklıyor; bunlar podcast modeline dayanak, tüm bağımsız podcast’lere Apple’ın bütün kabul kurallarını dayatma gerekçesi değildir. [Radio Browser API](https://api.radio-browser.info/) istasyon keşfi ve sunucu seçimini belgeliyor; tek sunucu adresini sabitlememeyi ve kalıcı station UUID kullanmayı öneriyor. [FFmpeg atempo/rubberband](https://www.ffmpeg.org/ffmpeg-filters.html#atempo) tempo ve pitch işlemleri sunar; rubberband için ilgili derleme desteği gerekir, mevcut Windows paketinde var olduğu varsayılmamalı.

**Kabul:** canlı radyoda bilinmeyen süre arama çubuğuna sahte süre eklemez; bölüm konumları şarkı kuyruğundan ayrı korunur; hız değişince süre/seek/LRC saatinin eşlemesi bozulmaz; desteklenmeyen DSP açıkça belirtilir.

## C. Önkoşullar, kapsam sınırları ve uygulanabilir yol haritası

### Önce kurulması önerilen üç ortak model

1. **Parça ve kaynak ayrımı:** mantıksal kayıt/sürüm kimliği, kaynak kimliği ve dosya yolu farklı kavramlar olsun. Bu; taşıma, farklı servis eşleme, NAS ve scrobbling’in ortak temeli.
2. **Oynatma olayları:** her dinleme için kimlik ve aktif süre; günlük istatistik ayrı özet. Bunlar kişisel DJ, scrobble ve bölüm konumuna temel olur.
3. **UI’den bağımsız durum/komut katmanı:** Windows medya paneli, ekran, widget ve telefon aynı oynatıcı durumunu görsün; ayrı ayrı ses motoru yaratmasın.

Bunlar önerilen uygulama mimarisi; ek bir büyük backend veya mikroservis zorunluluğu değildir. Var olan `PlaybackEngine` ve `PlaybackStreams` arayüzleri başlangıç için uygun ayrımlar sunuyor.

### İlk sıraya almayacağım işler

- **Her kaynağa genel YouTube indirme/çevrimdışı önbellek sözü:** Google’ın resmî API politikaları indirme/çevrimdışı içerik ve ses-görüntü ayırma konusunda sınırlamalar getiriyor. Bu yüzden kaynak hakları ve oynatma yöntemi netleştirilmeden bu özellik tüm kaynaklara uygulanabilir gibi sunulmamalı; yerel dosya ve kullanıcının kendi sunucusundaki izinli dosyalar ayrı kapsamdır. Bu bir ürün/entegrasyon sınırı tespitidir, hukuki görüş değildir. [Google Developer Policies](https://developers.google.com/youtube/terms/developer-policies?authuser=2&hl=en).
- **Eklenti mağazası:** önce birinci taraf birkaç entegrasyonla API sınırını oturtmak daha yönetilebilir. Eklenti izolasyonu, sürüm uyumluluğu ve yetki modeli ayrı bir ürün yükü getirir; Pear’da eklenti bulunması buraya aynı mimariyi taşıma zorunluluğu doğurmaz.
- **Grup dinleme ve tüm cihazlarda kusursuz senkron:** telefondan kumandaya göre çok daha geniş kapsam; katılımcıların aynı kayda erişimi, saat sapması, farklı kaynak gecikmeleri ve davet/izin modeli çözülmeli.
- **Sırf görünüm için ağır görselleştirme veya bulut LLM:** fayda/CPU/maliyet ölçülmeden ilk sıraya konmamalı. Kapak rengi, tam ekran ve basit komut paleti daha küçük adımlardır.

### Önerilen sürüm paketleri

| Paket | Kapsam | Çıkış kapısı |
|---|---|---|
| **Günlük kullanım temeli** | A1/A2/A6/A7; tek arama akışının taslağı; A8’de gerçek güncelleme testi | Cihaz/uyku/medya tuşu ve klavye senaryoları; sırları maskeleyen rapor; N−1→N yükseltmesi |
| **Kişisel müzik asistanı** | B1/B2/B3; A4; parça kimliği ve oynatma olayları; B5 ilk hizmet | Kural doğruluğu, kaynak eşleştirme, tekrar politikası, scrobble kuyruk testleri |
| **Düzenli büyük arşiv** | B4; ölçeğe göre A5; A3 albüm modu | Taşıma/yeniden bağlama; metadata önizlemesi; referans ses ve kitaplık ölçümleri |
| **Bağlantılı kullanım** | B6/B7; ihtiyaç varsa B8/B9/B10 | Eşleştirme/izin/çakışma ve protokol uyumluluk testleri |

Bunlar sprint veya teslim tarihi taahhüdü değildir. Aynı anda tümünü açmak yerine her pakette bir kullanıcı akışını tam bitirmek önerilir.

### Araştırmadan sonra doğrulanacak belirsizlikler

- Kullanıcıların çoğu YouTube keşfi mi, büyük yerel arşiv mi, yayıncılık mı istiyor? Öncelikleri en çok bu değiştirir; bu çalışmada kullanıcı görüşmesi yok.
- Gerçek açılış/ilk ses/arama süresi ve bellek ne? Bu raporda yeni benchmark yapılmadı.
- Bluetooth/USB değişimleri ve SMTC/JAB davranışı paketlenmiş EXE’de nasıl? Donanım/ekran okuyucu testi gerekir.
- Resmî OAuth ile hangi mevcut YouTube Music işlevleri karşılanabilir? Henüz doğrulanmadı.
- Güncelleme dağıtım adresi, imzalama ve gerçek kurulum yükseltmesi operasyonel olarak hazır mı? Önceki geliştirme oturumunda release adresinden 404 görülmüştü; bu araştırma bunu yeni canlı kontrol gibi sunmuyor.
- Discord’un seçilecek SDK yolu ve metadata eşleştirme doğruluğu ne? API belgesinin varlığı sonuç garantisi değil.

**Somut başlangıç önerim:** A7 ile ölçüm/tanılama tabanı kurulduktan sonra, yeni kullanıcı özelliği olarak **B3 birleşik arama + B2 kural editörü**, ardından oynatma olaylarıyla **B1 kişisel DJ + B5 scrobbling**. Böylece hem hemen görülen kullanım farkı hem sonraki entegrasyonları taşıyan temel birlikte ilerler.
