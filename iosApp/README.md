# BE-Music iOS (iosApp)

Bu klasör iOS hedefi için Xcode tarafı'nı tutar. Şu anda tek dosya var:

- **`UnityAdsBridge.swift`** — Unity Ads köprüsü için PLACEHOLDER. Kendi Unity Ads
  Swift kodun buraya yazılacak (initialize / loadInterstitial / loadRewarded /
  showInterstitial / showRewarded). Kotlin tarafında birebir çifti
  `composeApp/src/iosMain/kotlin/com/be/music/ads/AdsActual.kt` stub'ıdır;
  Swift köprüsü hazır olduğundan that stub `UnityAdsBridge` çağrılarına
  yönlendirilecek (objc interop via `@objcMembers`).

## Xcode projesi nasıl kurulur

1. Xcode'da yeni bir iOS App projesi aç; proje klasörünü `iosApp/` olarak kaydet.
2. Build Phases -> Run Script Phase ekle (İçeriği Gradle'a bağlar):

```sh
cd <ROOT>/composeApp
chmod +x ../gradlew
../gradlew :composeApp:embedAndSignAppleFrameworkForXcode
```

   a) Framework'ü "General > Frameworks, Libraries..." bölümüne ekleyin:
      `composeApp.xcframework` (yukarıdaki gradle task zaten arar).
3. "Build Settings > Search Paths > Framework Search Paths":
   `$(SRCROOT)/../composeApp/build/xcode-frameworks`
4. `UnityAdsBridge.swift` izle: App target'a ekle. Unity Ads SDK bağlıyken
   (Swift Package Manager veya CocoaPods) import bölümünü aktifleştir.
5. Info.plist: dosya paylaşımı / arka plan indirme gerekmez (iOS indirmeleri
   ileride 7/24 sunucu üzerinden yapılacak; o yüzden yt-dlp lokale gerek yok).

## Kotlin eştetığı

- İndirme (iOS): `composeApp/src/iosMain/kotlin/com/be/music/youtube/DownloadLauncherActual.kt`
  — "Sunucu bağlantısı bekleniyor" durumunu gösterir; sunucu hazırl olduğunda
  raw REST çağrıları buraya bağlanır.
- Ayarlar: sunucu URL alanı (Settings > Server URL) — AppPreferences `serverUrl` yazılıyor.
