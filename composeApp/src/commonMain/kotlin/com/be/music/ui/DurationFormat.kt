package com.be.music.ui

/**
 * Süreyi `saat:dakika:saniye` olarak biçimlendirir. Saat sıfıra eşitse
 * başında `0:` yazmaz, yalnızca `dakika:saniye` gösterir.
 *
 * Örnekler: `225_000` -> `3:45`, `3_753_000` -> `1:02:33`, `45_000_000` -> `12:30:00`.
 */
fun formatDuration(millis: Long): String = formatDurationParts(millis / 1000)

/** [formatDuration] gibi, ancak süre saniye cinsinden verilir. */
fun formatDurationSeconds(seconds: Long): String = formatDurationParts(seconds)

private fun formatDurationParts(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val seconds = safeSeconds % 60
    return if (hours > 0L) {
        "$hours:${pad2(minutes)}:${pad2(seconds)}"
    } else {
        "${pad2(minutes)}:${pad2(seconds)}"
    }
}

private fun pad2(value: Long): String = if (value < 10L) "0$value" else value.toString()
