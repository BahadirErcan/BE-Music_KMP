# BE-Music iOS (iosApp)

Bu klasör iOS hedefinin Xcode projesini barındırır. Proje Linux/Windows'tan da
build edilebilir — [MobAI-App/ios-builder](https://github.com/MobAI-App/ios-builder)
kullanılarak (repo içinde `.github/workflows/ios-build.yml` hazır).

## Dosyalar

| Dosya | Amaç |
|---|---|
| `iosApp.xcodeproj/` | Xcode projesi (hedef: `iosApp`, bundle id: `com.be.music.ios`) |
| `iosApp/iOSApp.swift` | SwiftUI `@main` giriş noktası |
| `iosApp/ContentView.swift` | Kotlin `MainViewControllerKt.MainViewController()` çağıran UIViewControllerRepresentable |
| `iosApp/Info.plist` | Uygulama bilgisi (`UIBackgroundModes: audio`, launch screen boş) |
| `iosApp/Assets.xcassets/` | AppIcon + AccentColor |
| `Config.xcconfig` | `APP_NAME` ve `BUNDLE_ID` |
| `UnityAdsBridge.swift` | Unity Ads köprüsü **PLACEHOLDER** — kendi Unity Ads Swift kodun buraya yazılacak |

## Kotlin tarafı

- Giriş noktası: `composeApp/src/iosMain/kotlin/com/be/music/MainViewController.kt`
  (`fun MainViewController(): UIViewController` — Koin başlatır, UI monteler)
- Framework adı: `ComposeApp` (`composeApp/build.gradle.kts`: `baseName = "ComposeApp"`)
- Kotlin'in derlediği framework, "Kotlin Gradle framework sync" adındaki
  Run Script Phase ile `composeApp/build/xcode-frameworks/ComposeApp.framework`
  içine kopyalanır ve app'e gömülür (Embed Frameworks phase).

```sh
cd composeApp
../gradlew :composeApp:embedAndSignAppleFrameworkForXcode
```

## Linux/Windows'tan build (ios-builder)

```bash
builder auth github        # bir kere, tarayıcıyla
builder init               # (workflow'lar zaten commitli)
builder ios build          # IPA'yı ./dist/ altına indirir
```

GitHub CLI ile workflow'u elle tetiklemek de mümkün:

```bash
gh workflow run ios-build.yml -f build_id=$(date +%s) -f ios_path=iosApp
gh run watch $(gh run list --workflow=ios-build.yml --limit 1 --json databaseId -q '.[0].databaseId')
gh run download <run-id>          # ipa artifact
```

Simülatörde denemek: `builder ios share` (MobAI API key gerektirir).

Cihazda çalıştırmak: `builder signing setup --devices-from-mobai` sonra
`builder ios build --profile development --distribute` (QR kod ile kurulum) —
veya unsigned IPA'yı MobAI ile ücretsiz Apple ID ile yeniden imzala.

## Unity Ads

`UnityAdsBridge.swift` placeholder'dır. Unity Ads SDK eklendiğinde (SPM
`https://github.com/unity-ads/unity-ads-sdk-ios` ya da CocoaPods) `import UnityAds`
satırı aktifleştirilir ve Initialize/load/show metotları real implementasyonla
doldurulur. Kotlin tarafındaki stub:
`composeApp/src/iosMain/kotlin/com/be/music/ads/AdsActual.kt` — SDK hazır
olunca objc interop ile `UnityAdsBridge` çağrılarına bağlanacak.

Placement id'ler: `BE-Music-Interstitial`, `BE-Music-Rewarded` (game id 6153407).

## İndirme (yt-dlp) — iOS güvenlik modeli

iOS sandbox'ında yerel yt-dlp çalıştırma mümkün değildir (App Store kuralları).
Plan: 7/24 çalışan uzak sunucu. O zamana kadar iOS'ta indirme ekranı
"sunucu bağlantısı bekleniyor" durumunda; Ayarlar > *BE-Music Sunucu Adresi*
alanına (iOS'ta görünür) sunucu API adresi girilir ve sunucu hazır olduğunda
`iosMain/youtube/DownloadLauncherActual.kt` REST akışına bağlanacak.
