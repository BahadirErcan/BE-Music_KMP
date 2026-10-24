//
//  UnityAdsBridge.swift
//  BE-Music iOS
//
//  iOS tarafında Unity Ads köprüsü — PLACEHOLDER.
//  Bu dosyanın gerçek gövdesi kendi Unity Ads Swift kodunla doldurulacak.
//  Kotlin tarafındaki stub: composeApp/src/iosMain/kotlin/com/be/music/ads/AdsActual.kt
//
//  NOT: Kotlin komut satırı Kotlin/Native bu dosyayı Xcode build'ine
//  gömülü Swift sınıfı olarak kullanır (objc export: @objcMembers).
//  Kotlin'den çağrı için KotlinSideAdsActual güncellenirken
//  `UnityAdsBridge.__initialize(...)` vb. yöntemler kullanılabilir.
//

import Foundation
import UIKit
// TODO: Unity Ads SDK eklendiğinde import:
// import UnityAds

@objcMembers
public final class UnityAdsBridge: NSObject {

    // BE-Music Unity Ads kimlikleri
    public static let GAME_ID = "6153407"
    public static let PLACEMENT_INTERSTITIAL = "BE-Music-Interstitial"
    public static let PLACEMENT_REWARDED = "BE-Music-Rewarded"

    /// SDK hazır mı (kullanıcı kodu dolduracak)
    public static var available: Bool {
        // TODO: gerçek implementasyon
        return false
    }

    /// SDK'yı başlat. App launch'de bir kez çağrılır.
    public static func initialize(gameId: String?) -> Void {
        // TODO: kullanıcı Unity Ads kodu
        // UnityAds.initialize(gameId: gameId ?? GAME_ID, testMode: false)
    }

    /// Interstitial yükle (background)
    public static func loadInterstitial() -> Void {
        // TODO
        // UnityAds.load(PLACEMENT_INTERSTITIAL)
    }

    /// Rewarded yükle (background)
    public static func loadRewarded() -> Void {
        // TODO
        // UnityAds.load(PLACEMENT_REWARDED)
    }

    /// Interstitial göster
    public static func showInterstitial(onDone: @escaping () -> Void) -> Void {
        // TODO: kullanıcı Unity Ads kodu (delegasyon + onDone callback)
        onDone()
    }

    /// Rewarded göster (ödül + kapatma callback'leri)
    public static func showRewarded(onReward: @escaping () -> Void,
                                    onDismiss: @escaping () -> Void) -> Void {
        // TODO: kullanıcı Unity Ads kodu (UnityShowAdDelegate)
        onDismiss()
    }
}
