package com.be.music.ads

/**
 * Reklam köprüsü:
 * - Android: Unity Ads (aynı SDK, aynı placement'lar)
 * - iOS: Unity Ads iOS — KULLANICIDAN GELECEK KOD BEKLENİYOR (placeholder, AppStore anahtarı bağlı)
 * - Masaüstü: yok (Premium sistemi de kaldırıldı, hiçbir kısıtlama yok)
 */
interface AdsGateway {
    /** Reklam SDK'sı platformda hazır mı (desktop: false) */
    val available: Boolean
    fun initialize(gameId: String?)
    fun loadInterstitial()
    fun loadRewarded()
    fun showInterstitial(onDone: () -> Unit)
    fun showRewarded(onReward: () -> Unit, onDismiss: () -> Unit)
}

expect object AdsProvider : AdsGateway
