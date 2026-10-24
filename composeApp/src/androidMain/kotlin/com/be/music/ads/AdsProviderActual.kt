package com.be.music.ads

import android.content.Context
import android.util.Log
import com.be.music.BeMusicApplication
import com.be.music.ActivityHolder
import com.be.music.premium.PremiumManager

/**
 * Android Unity Ads gerçek uygulaması — aynen eski davranış.
 *(premium kullanıcılarında interstitial/rewarded skip edilir)
 */
actual object AdsProvider : AdsGateway {
    private const val GAME_ID = "6153407"

    private var interstitial: InterstitialAdManager? = null
    private var rewarded: RewardedAdManager? = null

    override val available: Boolean get() = true

    /** Application onCreate içinde çağırılır. */
    fun initialize() {
        try {
            com.unity3d.ads.UnityAds.initialize(BeMusicApplication.app(), GAME_ID, false)
        } catch (e: Exception) {
            Log.e("AdsProvider", "Unity Ads init hatası: $e")
        }
        interstitial = InterstitialAdManager(BeMusicApplication.app()).also { it.loadAd() }
        rewarded = RewardedAdManager(BeMusicApplication.app()).also { it.loadAd() }
    }

    override fun initialize(gameId: String?) {
        initialize()
    }

    override fun loadInterstitial() {
        interstitial?.loadAd()
    }

    override fun loadRewarded() {
        rewarded?.loadAd()
    }

    override fun showInterstitial(onDone: () -> Unit) {
        val activity = ActivityHolder.current
        if (activity == null) {
            onDone()
            return
        }
        if (PremiumManager.isPremiumStatic) {
            onDone()
            return
        }
        try {
            interstitial?.showAd(activity)
        } catch (e: Exception) {
            Log.e("AdsProvider", "Interstitial gösterim hatası: $e")
        }
        onDone()
    }

    override fun showRewarded(onReward: () -> Unit, onDismiss: () -> Unit) {
        val activity = ActivityHolder.current
        if (activity == null) {
            onDismiss()
            return
        }
        try {
            rewarded?.showAd(activity, onDismissed = onDismiss, onRewardEarned = onReward)
        } catch (e: Exception) {
            Log.e("AdsProvider", "Rewarded gösterim hatası: $e")
            onDismiss()
        }
    }
}
