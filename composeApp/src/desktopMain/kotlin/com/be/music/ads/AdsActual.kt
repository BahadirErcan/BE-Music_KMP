package com.be.music.ads

/**
 * Masaüstünde reklam sistemi yok — Premium da kaldırıldı.
 * Tüm metotlar güvenli no-op.
 */
actual object AdsProvider : AdsGateway {
    override val available: Boolean = false
    override fun initialize(gameId: String?) {}
    override fun loadInterstitial() {}
    override fun loadRewarded() {}
    override fun showInterstitial(onDone: () -> Unit) { onDone() }
    override fun showRewarded(onReward: () -> Unit, onDismiss: () -> Unit) {
        // Masaüstünde reklam yok: ödülü doğrudan veremeyiz — premium bypass zaten açık
        onDismiss()
    }
}
