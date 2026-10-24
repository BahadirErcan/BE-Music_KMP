package com.be.music.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.atomic.AtomicBoolean

actual object YoutubeSearchEngine {
    private val initialized = AtomicBoolean(false)

    actual fun init() {
        if (initialized.compareAndSet(false, true)) {
            try {
                NewPipe.init(YoutubeDownloader())
            } catch (e: Exception) {
                println("[YoutubeSearch] NewPipe init hatası: $e")
            }
        }
    }

    actual suspend fun searchPage(
        query: String,
        token: SearchToken?
    ): Pair<List<YoutubeVideo>, SearchToken?> = withContext(Dispatchers.IO) {
        try {
            init()
            val service = ServiceList.YouTube
            val queryHandler = service.searchQHFactory.fromQuery(query)
            val itemsPage = if (token == null) {
                val info = SearchInfo.getInfo(service, queryHandler)
                ListExtractor.InfoItemsPage(info.relatedItems, info.nextPage, emptyList())
            } else {
                SearchInfo.getMoreItems(service, queryHandler, token.raw as org.schabi.newpipe.extractor.Page)
            }

            val items = itemsPage.items
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { item -> item.toYoutubeVideo() }

            (items.filter { it.id.isNotEmpty() } to itemsPage.nextPage?.let { SearchToken(it) })
        } catch (e: Exception) {
            println("[YoutubeSearch] Arama hatası: $query sayfa:$token → $e")
            (emptyList<YoutubeVideo>() to null)
        }
    }

    private fun StreamInfoItem.toYoutubeVideo(): YoutubeVideo? {
        return try {
            val url = url
            val videoId = when {
                url.contains("v=") -> url.split("v=").last().split("&").first()
                url.contains("youtu.be/") -> url.split("youtu.be/").last().split("?").first()
                else -> url.split("/").last()
            }
            YoutubeVideo(
                id = videoId,
                title = name ?: "Unknown",
                author = uploaderName ?: "Unknown",
                thumbnailUrl = thumbnails?.firstOrNull()?.url ?: "",
                durationSeconds = duration
            )
        } catch (ex: Exception) {
            null
        }
    }
}
