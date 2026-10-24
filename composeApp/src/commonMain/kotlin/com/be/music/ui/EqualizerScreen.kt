package com.be.music.ui
import org.jetbrains.compose.resources.stringResource
import com.be.music.res.Res
import com.be.music.res.eq_custom
import com.be.music.res.eq_enable
import com.be.music.res.eq_presets
import com.be.music.res.eq_reset
import com.be.music.res.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.be.music.player.AudioEffects
import com.be.music.player.EffectsProvider
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Madde "Ekolayzer": yerleşik equalizer ayar ekranı (platform köprüsü üzerinden).
 *
 * Bant seviyeleri özel çizilmiş dikey kaydırma çubuklarıyla (Canvas + sürükleme
 * hareketi), hazır ayarlar ise platformun preset'leriyle kullanılır.
 * Değişiklikler oynatma aktifse canlı uygulanır ve kalıcı hale getirilir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    onBack: () -> Unit
) {
    /** revision: bant seviyeleri her değiştiğinde artar -> composables yeniden okur */
    var revision by remember { mutableIntStateOf(0) }

    var effects by remember { mutableStateOf<AudioEffects?>(EffectsProvider.create()) }
    LaunchedEffect(revision) {
        if (effects == null) {
            // oynatma başlatılmadan ekran açıldıysa tekrar dene
            delay(300)
            effects = EffectsProvider.create()
        }
    }

    val fx = effects

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.equalizer), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (fx == null) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(Res.string.eq_enable),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // ---- Aç / Kapa ----
            val enabledState = fx.isEnabled
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.eq_enable),
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = enabledState,
                    onCheckedChange = { value ->
                        fx.setEnabled(value)
                        revision++
                    }
                )
            }

            // ---- Hazır ayarlar ----
            Text(
                text = stringResource(Res.string.eq_presets),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val selectedPreset = fx.selectedPreset()
                // "Özel": kullanıcı el ile bant oynatınca otomatik seçilir
                FilterChip(
                    selected = selectedPreset == -1,
                    onClick = { revision++ },
                    label = { Text(stringResource(Res.string.eq_custom)) }
                )
                for (i in fx.presets()) {
                    val name = fx.presetName(i)
                    if (name.isBlank()) continue
                    FilterChip(
                        selected = selectedPreset == i,
                        onClick = {
                            fx.applyPreset(i)
                            revision++
                        },
                        label = { Text(name) }
                    )
                }
            }

            // ---- Bantlar ----
            val (gainMin, gainMax) = fx.gainRangeMilliBel()
            val bandCount = fx.bandCount
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (band in 0 until bandCount) {
                    VerticalBand(
                        band = band,
                        gainMin = gainMin,
                        gainMax = gainMax,
                        revision = revision,
                        effects = fx,
                        onLevelChanged = {
                            fx.setBandLevel(band, it)
                            revision++
                        }
                    )
                }
            }

            // ---- Sıfırla ----
            Button(
                onClick = {
                    fx.resetBandLevels()
                    revision++
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = stringResource(Res.string.eq_reset))
            }
        }
    }
}

@Composable
private fun VerticalBand(
    band: Int,
    gainMin: Int,
    gainMax: Int,
    revision: Int,
    effects: AudioEffects,
    onLevelChanged: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Üstte mevcut değer
        key(revision) {
            val level = effects.bandLevel(band)
            Text(
                text = formatDb(level),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        VerticalGainSlider(
            levelMillibel = effects.bandLevel(band),
            gainMin = gainMin,
            gainMax = gainMax,
            modifier = Modifier
                .weight(1f)
                .width(44.dp)
                .padding(vertical = 6.dp),
            onLevelChanged = onLevelChanged
        )

        // Altta frekans etiketi
        Text(
            text = formatFrequency(effects.bandFrequenciesMilliHz().getOrNull(band) ?: 0),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Özel çizilmiş dikey kaydırma çubuğu (Canvas + pointerInput).
 *
 * Üst = +dB (max), alt = -dB (min), orta = 0 dB.
 */
@Composable
private fun VerticalGainSlider(
    levelMillibel: Int,
    gainMin: Int,
    gainMax: Int,
    modifier: Modifier = Modifier,
    onLevelChanged: (Int) -> Unit
) {
    val thumbRadiusDp = 9.dp
    val trackWidthDp = 4.dp

    var value by remember { mutableFloatStateOf(levelMillibel.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(levelMillibel) {
        if (!dragging) value = levelMillibel.toFloat()
    }

    val density = LocalDensity.current
    val thumbRadiusPx = remember { with(density) { thumbRadiusDp.toPx() } }
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val activeColor = MaterialTheme.colorScheme.primary

    fun seekTo(yPx: Float, heightPx: Float) {
        val span = heightPx - 2 * thumbRadiusPx
        if (span <= 0f) return
        val frac = ((yPx - thumbRadiusPx) / span).coerceIn(0f, 1f) // 0 = üst (+max), 1 = alt (-min)
        val level = gainMax - frac * (gainMax - gainMin)
        value = level
        onLevelChanged(level.roundToInt())
    }

    Box(
        modifier = modifier.pointerInput(gainMin, gainMax) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                dragging = true
                down.consume()
                seekTo(down.position.y, size.height.toFloat())
                var pressed = true
                while (pressed) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                    if (change == null || !change.pressed) {
                        pressed = false
                        break
                    }
                    change.consume()
                    seekTo(change.position.y, size.height.toFloat())
                    pressed = event.changes.any { it.pressed }
                }
                dragging = false
            }
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val stroke = trackWidthDp.toPx()

            val minY = thumbRadiusPx
            val maxY = size.height - thumbRadiusPx
            val midY = (minY + maxY) / 2f
            val frac = ((gainMax - value) / (gainMax - gainMin).coerceAtLeast(1)).coerceIn(0f, 1f)
            val thumbY = minY + frac * (maxY - minY)

            drawLine(
                color = trackColor,
                start = Offset(cx, minY),
                end = Offset(cx, maxY),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = activeColor,
                start = Offset(cx, minOf(midY, thumbY)),
                end = Offset(cx, maxOf(midY, thumbY)),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = activeColor,
                radius = thumbRadiusPx,
                center = Offset(cx, thumbY)
            )
        }
    }
}

/** milliBel -> "+6 dB" gibi metin. */
private fun formatDb(millibel: Int): String {
    val db = millibel / 100f
    val sign = if (db >= 0) "+" else ""
    return String.format(Locale.US, "%s%.0f dB", sign, db)
}

/** mHz -> "60 Hz" / "230 Hz" / "1 kHz" / "14 kHz". */
private fun formatFrequency(mhz: Int): String = when {
    mhz <= 0 -> ""
    mhz >= 1_000_000 -> String.format(
        Locale.US,
        if (mhz % 1_000_000 == 0) "%.0f kHz" else "%.1f kHz",
        mhz / 1_000_000f
    )
    else -> String.format(Locale.US, "%.0f Hz", mhz / 1_000f)
}
