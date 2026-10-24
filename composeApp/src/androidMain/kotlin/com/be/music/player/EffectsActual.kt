package com.be.music.player

/**
 * Android ekolayzer köprüsü: EqualizerManager'ın sarmalanması.
 */
actual object EffectsProvider {
    actual fun create(): AudioEffects? {
        return try {
            if (EqualizerManager.isAttached || EqualizerManager.appContext != null) {
                AndroidAudioEffects
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    actual fun supports8DGeneration(): Boolean = true
}

object AndroidAudioEffects : AudioEffects {
    override val bandCount: Int get() = EqualizerManager.numberOfBands

    override fun bandFrequenciesMilliHz(): List<Int> {
        return (0 until bandCount).map { EqualizerManager.bandCenterFreqMhz(it) }
    }

    override fun gainRangeMilliBel(): Pair<Int, Int> = EqualizerManager.gainRange()

    override fun setBandLevel(bandIndex: Int, milliBel: Int) {
        EqualizerManager.setBandLevel(bandIndex, milliBel)
    }

    override fun bandLevel(bandIndex: Int): Int = EqualizerManager.getBandLevel(bandIndex)

    override fun presets(): List<Int> = (0 until EqualizerManager.presetCount()).toList()

    override fun presetName(index: Int): String = EqualizerManager.presetName(index)

    override fun applyPreset(index: Int) {
        EqualizerManager.applyPreset(index)
    }

    override fun setEnabled(enabled: Boolean) {
        EqualizerManager.enabled = enabled
    }

    override val isEnabled: Boolean get() = EqualizerManager.enabled

    override fun selectedPreset(): Int = EqualizerManager.selectedPreset

    override fun setBandLevels(levels: IntArray) {
        levels.forEachIndexed { b, l -> if (b < bandCount) EqualizerManager.setBandLevel(b, l) }
    }

    override fun bandLevels(): IntArray = IntArray(bandCount) { EqualizerManager.getBandLevel(it) }

    override fun resetBandLevels() {
        EqualizerManager.resetBandLevels()
    }

    override fun persist() {
        // Android tarafında.set* çağrıları zaten kalıcı yazar
    }

    override fun release() {
        // Ekolayzer, oynatma oturumu bitince ayrılır (PlaybackService yönetirken serbest bırakılır)
    }
}
