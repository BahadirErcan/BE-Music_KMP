package com.be.music.player

/**
 * Sistem ekolayzeri / ses efektleri köprüsü.
 * - Android: device equalizer (AudioEffect) — EqualizerManager
 * - Masaüstü: ffmpeg PCM hattı üzerinde biquad EQ DSP
 * - iOS: AVAudioUnitEQ
 */
interface AudioEffects {
    val bandCount: Int
    fun bandFrequenciesMilliHz(): List<Int> // merkez frekanslar (mHz)
    fun gainRangeMilliBel(): Pair<Int, Int> // genelde -1500..1500
    fun setBandLevel(bandIndex: Int, milliBel: Int) // -1500..1500 millibel
    fun presets(): List<Int> // preset indeksleri
    fun presetName(index: Int): String
    fun applyPreset(index: Int)
    fun setEnabled(enabled: Boolean)
    val isEnabled: Boolean
    fun selectedPreset(): Int
    fun bandLevel(bandIndex: Int): Int
    fun setBandLevels(levels: IntArray)
    fun bandLevels(): IntArray
    /** Tüm bantları 0'a getirir ve "Özel" moduna geçer. */
    fun resetBandLevels()
    /** Değişiklikleri kalıcı saklama (platform tercihi) */
    fun persist()
    fun release()
}

expect object EffectsProvider {
    fun create(): AudioEffects? // desteklemeyen platformda null
    /** 8D ses dosyası üretimi destekleniyor mu (Android+Desktop: evet / iOS: no) */
    fun supports8DGeneration(): Boolean
}
