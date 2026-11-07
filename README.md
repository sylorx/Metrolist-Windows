# Metrolist Windows

Masaüstü için YouTube Music istemcisi. Telefon uygulamasının yerine geçmez; Windows’ta arama, ana sayfa, keşfet, kitaplık ve çalmayı tek pencerede toplar.

İngilizce özet: a third-party YouTube Music client for Windows. It is not affiliated with YouTube, Google, or Spotify.

![Metrolist penceresi](docs/ekran.png)

## Ne yapar

- Oturum açmadan şarkı çalar. Akış, herkese açık Invidious ve Piped uçlarından gelir; onlar yetmezse Innertube yedeği denenir.
- Ana sayfa, arama, albüm, sanatçı ve çalma listesi.
- Keşfet: Pop, Rap, Rock, Arabesk, Halk, Caz, Elektronik ve Akustik rafları, yeni çıkanlar ve ruh halleri.
- Bu bilgisayarda beğenilenler ve yerel çalma listeleri. Çerez olmadan da durur.
- Çaldığın şarkı bitince benzerleri sıraya eklenir, çalma devam eder.
- Kitaplık (beğenilenler, listeler, albümler, sanatçılar) için music.youtube.com çerezi. Çerez olmadan çalma açıktır.
- Bu bilgisayardaki dinleme geçmişi.
- Vurgu rengi, panel, köşe ve kart boyutu. Seçim `%LOCALAPPDATA%\Metrolist` altında durur.
- Sözler, sıra, karıştır, tekrar, ses.

Telefona özel parçalar bu sürümde yok: widget, Android Auto, Cast, ekolayzır, Listen Together.

## Arayüz

Koyu bir kabuk: solda gezinme, ortada içerik, altta oynatıcı. Oynatıcıda kapak ve ad solda, taşıma ve ince ilerleme çubuğu ortada, ses ve sıra sağda. Süre bilinmiyorsa çubuk sona yapışmaz; süre `—:—` görünür ve akıştan okununca dolar.

## Güncelleme

Uygulama açıldıktan kısa süre sonra ve ardından yaklaşık üç saatte bir [GitHub Releases](https://github.com/sylorx/Metrolist-Windows/releases) kontrol edilir. Yeni sürüm varsa pencerenin altında **Güncelle** çıkar. Ayarlar’da **Güncellemeleri kontrol et** aynı işi elle yapar.

Güncelleme, yayındaki `Metrolist` adlı `.exe` kurulum dosyasını ve aynı yayındaki `<dosya adı>.sha256` özetini indirir. Özet, dosyanın SHA-256 değeriyle aynı değilse, bozuksa veya yayında yoksa indirilen dosya silinir ve kurucu açılmaz. Özet tutarsa kurucu açılır ve program kapanır. Yayında böyle bir kurulum dosyası yoksa uygulama güncel kabul edilir.

Yeni sürüm yayınlamak için:

1. `desktop/build.gradle.kts` içindeki `packageVersion` ile `AppVersion.NAME` aynı sayı olsun.
2. `./gradlew :desktop:packageExe` ile kurucuyu üret.
3. GitHub’da `v1.2.0` gibi bir sürüm aç. `Metrolist-….exe` dosyasının yanına, adı tam olarak `<kurulum dosyası>.sha256` olan bir özet ekle. Dosya UTF-8 metindir ve tek kayıttır: 64 onaltılık karakter (büyük veya küçük harf), ardından iki boşluk ve kurulum dosyasının tam adı. Sondaki satır sonu kabul edilir. `sha256sum` bunu yazar. Yalnızca 64 karakterlik özet de kabul edilir. Özet yoksa veya dosya adı kayıtta farklıysa uygulama kurucuyu açmaz.

```bash
sha256sum Metrolist-1.2.0.exe > Metrolist-1.2.0.exe.sha256
```

## Derlemek

Yan yana iki klasör gerekir. Innertube kaynakları Android ağacından okunur, kopyalanmaz.

```
Metrolists-main/        https://github.com/MetrolistGroup/Metrolist
Metrolist-windows/      bu depo
```

JDK 21 ve `jpackage` içeren bir JDK (Android OpenJDK 21 uygundur).

```bat
set JAVA_HOME=C:\Program Files\Android\openjdk\jdk-21.0.8
gradlew.bat :desktop:createDistributable
```

Çalışan program: `desktop\build\compose\binaries\main\app\Metrolist\Metrolist.exe`

Kurucu: `desktop\build\compose\binaries\main\exe\Metrolist-1.0.0.exe`

## Veriler

Ayar, geçmiş ve kapak önbelleği `%LOCALAPPDATA%\Metrolist` içindedir. Çerez düz metin durur; o klasörü paylaşmayın.
