package com.be.music.youtube

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.be.music.ui.formatDurationSeconds

data class YoutubeVideo(
    val id: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    /** Saniye cinsinden süre. Canlı yayınlarda negatif olabilir. */
    val durationSeconds: Long
)

/** Sayfalama için platform-agnostik token (JVM'de NewPipe Page sarmalar). */
class SearchToken(val raw: Any?)

object VttParser {
    fun parse(content: String): List<LyricLine> {
        val lines = content.lineSequence().map { it.trim() }.toList()
        val result = mutableListOf<LyricLine>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            if (line.contains("-->")) {
                val timeParts = line.split("-->")
                val startStr = timeParts[0].trim()
                val startMs = try {
                    parseVttTime(startStr)
                } catch (e: Exception) {
                    null
                }

                i++
                val textBuilder = StringBuilder()
                while (i < lines.size && lines[i].isNotEmpty() && !lines[i].contains("-->")) {
                    val cleanedLine = lines[i].replace("<[^>]*>".toRegex(), "").trim()
                    if (cleanedLine.isNotEmpty()) {
                        if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                        textBuilder.append(cleanedLine)
                    }
                    i++
                }
                val text = textBuilder.toString().trim()
                if (text.isNotEmpty() && startMs != null) {
                    result.add(LyricLine(startMs, text))
                }
            } else {
                i++
            }
        }

        if (result.isEmpty()) {
            val plainLines = lines.filter {
                it.isNotEmpty() &&
                !it.startsWith("WEBVTT") &&
                !it.startsWith("NOTE") &&
                !it.startsWith("STYLE")
            }
            return plainLines.map {
                LyricLine(null, it.replace("<[^>]*>".toRegex(), "").trim())
            }.filter { it.text.isNotEmpty() }
        }

        return result
    }

    private fun parseVttTime(timeStr: String): Long {
        val cleaned = timeStr.trim().replace(',', '.')
        val parts = cleaned.split(":")
        if (parts.size == 2) {
            val minutes = parts[0].toLong()
            val secondsParts = parts[1].split(".")
            val seconds = secondsParts[0].toLong()
            val ms = if (secondsParts.size > 1) secondsParts[1].padEnd(3, '0').take(3).toLong() else 0L
            return (minutes * 60 + seconds) * 1000 + ms
        } else if (parts.size == 3) {
            val hours = parts[0].toLong()
            val minutes = parts[1].toLong()
            val secondsParts = parts[2].split(".")
            val seconds = secondsParts[0].toLong()
            val ms = if (secondsParts.size > 1) secondsParts[1].padEnd(3, '0').take(3).toLong() else 0L
            return ((hours * 60 + minutes) * 60 + seconds) * 1000 + ms
        }
        throw IllegalArgumentException("Invalid WebVTT time format: $timeStr")
    }
}

data class LyricLine(
    val timeMs: Long?,
    val text: String
)

@Composable
fun YoutubeVideoItem(
    video: YoutubeVideo,
    onDownloadMusic: () -> Unit,
    onDownloadVideo: () -> Unit,
    onCancelMusic: () -> Unit = {},
    onCancelVideo: () -> Unit = {},
    isMusicDownloading: Boolean = false,
    isVideoDownloading: Boolean = false,
    musicProgress: Int = 0,
    videoProgress: Int = 0
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(80.dp)
            ) {
                coil3.compose.AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                if (video.durationSeconds > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.75f)
                    ) {
                        Text(
                            text = formatDurationSeconds(video.durationSeconds),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = video.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(text = video.author, style = MaterialTheme.typography.bodySmall)

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { if (isMusicDownloading) onCancelMusic() else onDownloadMusic() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMusicDownloading) Color(0xFF1565C0) else Color(0xFF1976D2),
                            disabledContainerColor = Color(0xFF1565C0)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        if (isMusicDownloading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${musicProgress}%", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            }
                        } else {
                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }

                    Button(
                        onClick = { if (isVideoDownloading) onCancelVideo() else onDownloadVideo() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVideoDownloading) Color(0xFF6A1B9A) else Color(0xFF7B1FA2),
                            disabledContainerColor = Color(0xFF6A1B9A)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        if (isVideoDownloading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${videoProgress}%", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            }
                        } else {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
