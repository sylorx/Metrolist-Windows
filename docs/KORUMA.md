# Elle açılacak koruma

Bu depodaki dosyalar GitHub ayarlarını kendiliğinden açmaz. Aşağıdakileri yalnız sahip `sylorx` depo ayarlarından çevirir. Başka bir hesaba yönetici veya ruleset bypass vermeyin.

## `main` için ruleset

**Settings → Rules → Rulesets → New branch ruleset.** Enforcement **Active** olsun. Hedef, varsayılan dal `main` olsun.

Açılacak kurallar:

- **Require a pull request before merging** — birleştirmeden önce çekme isteği zorunlu
- **Restrict deletions** — bu dalın silinmesini kısıtla
- **Block force pushes** — zorla itmeyi engelle

`CODEOWNERS` dosyasının inceleme istemesi için, çekme isteği kuralının içinde **Require review from Code Owners** da açılabilir. Bypass listesi boş kalsın ya da yalnız `sylorx` olsun.

## Dependabot

**Settings → Code security** (eski adıyla Code security and analysis):

- **Dependabot alerts** — açık
- **Dependabot security updates** — açık

`.github/dependabot.yml` Gradle ve GitHub Actions için haftalık sürüm çekme isteklerini açar. Uyarılar ve güvenlik güncellemeleri bu iki anahtar olmadan gelmez.

## Secret scanning ve push protection

Aynı **Code security** sayfası:

- **Secret scanning** — açık
- **Push protection** — açık

Herkese açık depoda ikisi de ücretsizdir. Push protection, bilinen bir sırrın itilmesini keser.

## Dış katkıların iş akışları

**Settings → Actions → General → Fork pull request workflows:**

- **Require approval for all outside collaborators**

Yabancı bir hesabın iş akışı, sahip onaylamadan çalışmasın. Bu depodaki iş akışları `pull_request_target` kullanmaz.

## Özel güvenlik açığı bildirimi

**Settings → Code security → Private vulnerability reporting** açık olsun.

Bu anahtar, `SECURITY.md` içindeki **Report a vulnerability** yolunu açar. Açıklar herkese açık issue ile toplanmaz.

## Yöneticiler

**Settings → Collaborators** (Manage access):

- Yönetici yalnız `sylorx` olsun.
- Başka hesaba Admin vermeyin. Depoyu silme yetkisi yöneticidedir.
- Başkası katkı verecekse Write yeter; ruleset, Actions ve güvenlik ayarlarını değiştiremesin.

## Lisans

Bu depoda `LICENSE` dosyası yok. Uygulama derlenirken kardeş klasördeki [MetrolistGroup/Metrolist](https://github.com/MetrolistGroup/Metrolist) Kotlin kaynaklarını okur. O depo **GPL-3.0** lisanslıdır (`main` üzerindeki `876e355181fd3b04e7063b10442fef1b721d53b3` taahhüdü buna örnektir).

GPL-3.0 kodu derleyip dağıtmak, bu deponun lisansını da netleştirmeyi gerektirir. Bu dosya bir lisans seçmez ve depoya `LICENSE` eklemez. Karar `sylorx`'e aittir.
