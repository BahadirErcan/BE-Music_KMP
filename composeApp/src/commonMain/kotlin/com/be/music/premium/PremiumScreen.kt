package com.be.music.premium
import com.be.music.ui.getStringResource
import com.be.music.platform.showToast
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.be.music.res.Res
import com.be.music.res.back
import com.be.music.res.icon_clear
import com.be.music.res.icon_error_decode
import com.be.music.res.icon_error_generic
import com.be.music.res.icon_error_too_large
import com.be.music.res.icon_success
import com.be.music.res.keep_alive
import com.be.music.res.keep_alive_desc
import com.be.music.res.premium_active
import com.be.music.res.premium_days_remaining
import com.be.music.res.premium_feature_1080p
import com.be.music.res.premium_feature_1080p_desc
import com.be.music.res.premium_feature_auto_playlist
import com.be.music.res.premium_feature_auto_playlist_desc
import com.be.music.res.premium_feature_fast_downloads
import com.be.music.res.premium_feature_fast_downloads_desc
import com.be.music.res.premium_feature_highest
import com.be.music.res.premium_feature_highest_desc
import com.be.music.res.premium_feature_icon
import com.be.music.res.premium_feature_icon_desc
import com.be.music.res.premium_feature_no_ads
import com.be.music.res.premium_feature_no_ads_desc
import com.be.music.res.premium_feature_parallel_downloads
import com.be.music.res.premium_feature_parallel_downloads_desc
import com.be.music.res.premium_feature_rgb
import com.be.music.res.premium_feature_rgb_desc
import com.be.music.res.premium_features_title
import com.be.music.res.premium_reward_hint
import com.be.music.res.premium_reward_progress
import com.be.music.res.premium_select_image
import com.be.music.res.premium_title
import com.be.music.res.premium_watch_ad

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    onBack: () -> Unit,
    viewModel: PremiumViewModel = koinViewModel()
) {
    val premiumState by viewModel.premiumState.collectAsStateWithLifecycle()
    val keepAliveEnabled by viewModel.keepAliveEnabled.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val iconResult by viewModel.iconResult.collectAsStateWithLifecycle()
    LaunchedEffect(iconResult) {
        iconResult?.let { r ->
            val msg = when (r) {
                com.be.music.platform.LauncherIconResult.TOO_LARGE -> getStringResource(Res.string.icon_error_too_large)
                com.be.music.platform.LauncherIconResult.DECODE_FAILED -> getStringResource(Res.string.icon_error_decode)
                com.be.music.platform.LauncherIconResult.GENERIC_ERROR -> getStringResource(Res.string.icon_error_generic)
                else -> getStringResource(Res.string.icon_success)
            }
            showToast(msg)
            viewModel.clearIconMessage()
        }
    }

    // İkon seçici (sadece desteklenen platformda aktif)
    val pickImage: () -> Unit = {
        scope.launch {
            val bytes = com.be.music.platform.pickIconImage()
            if (bytes != null) viewModel.setCustomIcon(bytes)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.premium_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFFFC107), Color(0xFFFF9800))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.premium_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFC107)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (premiumState.isPremium && !premiumState.isExpired) {
                PremiumActiveCard(remainingDays = premiumState.remainingDays)
            } else {
                PremiumProgressCard(rewardCount = premiumState.rewardedAdCount)
                Spacer(modifier = Modifier.height(16.dp))
                WatchAdButton(
                    onClick = { viewModel.watchRewardedAd() },
                    enabled = !premiumState.isPremium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(Res.string.premium_features_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            PremiumFeatureItem(
                icon = Icons.Default.Block,
                title = stringResource(Res.string.premium_feature_no_ads),
                description = stringResource(Res.string.premium_feature_no_ads_desc)
            )

            PremiumFeatureItem(
                icon = Icons.Default.HighQuality,
                title = stringResource(Res.string.premium_feature_1080p),
                description = stringResource(Res.string.premium_feature_1080p_desc)
            )

            PremiumFeatureItem(
                icon = Icons.Default.Star,
                title = stringResource(Res.string.premium_feature_highest),
                description = stringResource(Res.string.premium_feature_highest_desc)
            )

            PremiumFeatureItem(
                icon = Icons.Default.Palette,
                title = stringResource(Res.string.premium_feature_rgb),
                description = stringResource(Res.string.premium_feature_rgb_desc)
            )

            PremiumFeatureItem(
                icon = Icons.Default.Speed,
                title = stringResource(Res.string.premium_feature_fast_downloads),
                description = stringResource(Res.string.premium_feature_fast_downloads_desc)
            )

            PremiumFeatureItem(
                icon = Icons.Default.Download,
                title = stringResource(Res.string.premium_feature_parallel_downloads),
                description = stringResource(Res.string.premium_feature_parallel_downloads_desc)
            )

            PremiumFeatureItem(
                icon = Icons.Default.AppRegistration,
                title = stringResource(Res.string.premium_feature_icon),
                description = stringResource(Res.string.premium_feature_icon_desc),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (com.be.music.platform.LauncherIcon.supported && viewModel.iconUri.value != null) {
                            AsyncImage(
                                model = viewModel.iconUri.value,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { viewModel.clearCustomIcon() }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(Res.string.icon_clear))
                            }
                        } else {
                            OutlinedButton(
                                onClick = { pickImage() },
                                enabled = premiumState.isPremium && !premiumState.isExpired
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(Res.string.premium_select_image))
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PremiumFeatureItem(
                icon = Icons.Default.Lock,
                title = stringResource(Res.string.keep_alive),
                description = stringResource(Res.string.keep_alive_desc)
            )

            PremiumFeatureItem(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                title = stringResource(Res.string.premium_feature_auto_playlist),
                description = stringResource(Res.string.premium_feature_auto_playlist_desc)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PremiumActiveCard(remainingDays: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFC107).copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFF4CAF50)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.premium_active),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4CAF50)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.premium_days_remaining, remainingDays),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PremiumProgressCard(rewardCount: Int) {
    val progress = rewardCount.toFloat() / PremiumState.REWARD_THRESHOLD.toFloat()
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.premium_reward_progress, rewardCount, PremiumState.REWARD_THRESHOLD),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFFFFC107),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.premium_reward_hint, PremiumState.REWARD_THRESHOLD),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WatchAdButton(onClick: () -> Unit, enabled: Boolean) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFFC107),
            contentColor = Color(0xFF1A1A1A)
        )
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.premium_watch_ad),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PremiumFeatureItem(
    icon: ImageVector,
    title: String,
    description: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFC107).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color(0xFFFFC107)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailing?.invoke()
        }
    }
}
