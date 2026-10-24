package com.be.music.di

import com.be.music.data.AppDatabase
import com.be.music.data.MusicRepository
import com.be.music.data.PlayHistoryManager
import com.be.music.premium.PremiumManager
import com.be.music.premium.PremiumViewModel
import com.be.music.premium.TimeProvider
import com.be.music.ui.MusicViewModel
import com.be.music.ui.YoutubeDownloadViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { MusicViewModel(get()) }
    viewModel { YoutubeDownloadViewModel(get()) }
    viewModel { PremiumViewModel(get()) }
    single { AppDatabase.getDatabase() }

    single { get<AppDatabase>().songDao() }
    single { get<AppDatabase>().artistDao() }
    single { get<AppDatabase>().albumDao() }
    single { get<AppDatabase>().playlistDao() }
    single { get<AppDatabase>().filterSettingsDao() }
    single { get<AppDatabase>().songOverrideDao() }

    single { PlayHistoryManager() }
    single {
        MusicRepository(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }
    single { TimeProvider() }
    single { PremiumManager(get()) }
}
