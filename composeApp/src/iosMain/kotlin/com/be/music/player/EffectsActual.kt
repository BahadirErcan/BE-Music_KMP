package com.be.music.player

/** iOS: AVAudioUnitEQ ile daha sonra; şimdilik ekolayzer UI "önce şarkı çal" durumunda göstermez. */
actual object EffectsProvider {
    actual fun create(): AudioEffects? = null
    actual fun supports8DGeneration(): Boolean = false
}
