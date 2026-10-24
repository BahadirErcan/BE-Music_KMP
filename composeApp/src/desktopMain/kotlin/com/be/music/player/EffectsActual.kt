package com.be.music.player

import com.be.music.desktop.audio.DesktopAudioEngine

actual object EffectsProvider {
    private val engineFx = object : AudioEffects {
        override val bandCount: Int get() = DesktopAudioEngine.BAND_HZ.size

        override fun bandFrequenciesMilliHz(): List<Int> = DesktopAudioEngine.BAND_HZ.map { it * 1000 }

        override fun gainRangeMilliBel(): Pair<Int, Int> = -1500 to 1500

        override fun setBandLevel(bandIndex: Int, milliBel: Int) {
            DesktopAudioEngine.setBandLevel(bandIndex, milliBel)
        }

        override fun bandLevel(bandIndex: Int): Int = DesktopAudioEngine.bandLevel(bandIndex)

        override fun presets(): List<Int> = emptyList() // masaüstünde hazır preset yok (özel)

        override fun presetName(index: Int): String = ""

        override fun applyPreset(index: Int) {}

        override fun setEnabled(enabled: Boolean) = DesktopAudioEngine.setEnabled(enabled)

        override val isEnabled: Boolean get() = DesktopAudioEngine.eqEnabled

        override fun selectedPreset(): Int = -1

        override fun setBandLevels(levels: IntArray) {
            levels.forEachIndexed { i, l -> DesktopAudioEngine.setBandLevel(i, l) }
        }

        override fun bandLevels(): IntArray = IntArray(bandCount) { DesktopAudioEngine.bandLevel(it) }

        override fun resetBandLevels() {
            (0 until bandCount).forEach { DesktopAudioEngine.setBandLevel(it, 0) }
        }

        override fun persist() {}

        override fun release() {}
    }

    actual fun create(): AudioEffects? = engineFx

    actual fun supports8DGeneration(): Boolean = true
}
