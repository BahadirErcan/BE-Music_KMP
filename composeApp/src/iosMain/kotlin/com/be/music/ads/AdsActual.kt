package com.be.music.ads

/**
 * iOS UNITY ADS — KULLANICI KODU BEKLENİYOR:
 * Unity Ads iOS entegrasyonu kullanıcıdan gelecek Swift koduyla yapılacak.
 * iOS tarafında reklam birimleri şu an çalışmıyor:
 *  - Bu nesne güvenli, sessiz hiçbir iş yapmayan bir stub.
 *  - `iosApp/UnityAdsBridge.swift` köprüsü hazır olduğunda bu stub'un metotları
 *    buradan (Kotlin ↔ Swift interop) çağırılacak.
 * Placement'lar: "BE-Music-Interstitial", "BE-Music-Rewarded"
 */
actual object AdsProvider : AdsGateway {
    override val available: Boolean = false
    override fun initialize(gameId: String?) {
        // TODO(user): UnityAdsBridge.initialize(gameId) çağrısı buraya gelecek
    }
    override fun loadInterstitial() {}
    override fun loadRewarded() {}
    override fun showInterstitial(onDone: () -> Unit) { onDone() }
    override fun showRewarded(onReward: () -> Unit, onDismiss: () -> Unit) {
        // TODO(user): UnityAdsBridge.showRewarded() — reklam sonrası onReward çağır
        onDismiss()
    }
}
