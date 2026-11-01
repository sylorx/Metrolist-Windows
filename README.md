# Metrolist Windows

Masaüstü için YouTube Music istemcisi. Telefon uygulamasının yerine geçmez; Windows’ta arama, ana sayfa, keşfet, kitaplık ve çalmayı tek pencerede toplar.

İngilizce özet: a third-party YouTube Music client for Windows. It is not affiliated with YouTube, Google, or Spotify.

## Ne yapar

- Oturum açmadan şarkı çalar. Akış, herkese açık Invidious ve Piped uçlarından gelir; onlar yetmezse Innertube yedeği denenir.
- Ana sayfa, arama, keşfet, albüm, sanatçı ve çalma listesi.
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

Güncelleme, yayındaki `Metrolist` adlı `.exe` kurulum dosyasını indirir, kurucuyu açar ve programı kapatır. Yayında böyle bir dosya yoksa uygulama güncel kabul edilir.

Yeni sürüm yayınlamak için:

1. `desktop/build.gradle.kts` içindeki `packageVersion` ile `AppVersion.NAME` aynı sayı olsun.
2. `./gradlew :desktop:packageExe` ile kurucuyu üret.
3. GitHub’da `v1.2.0` gibi bir sürüm aç ve `Metrolist-….exe` dosyasını ekle.

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
