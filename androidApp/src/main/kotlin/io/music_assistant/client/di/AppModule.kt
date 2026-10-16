package io.music_assistant.client.di

import io.music_assistant.client.auto.AutoLibrary
import io.music_assistant.client.data.CarConnectionMonitor
import io.music_assistant.client.offline.AndroidOfflinePlayer
import io.music_assistant.client.offline.AndroidOfflineStorage
import io.music_assistant.client.offline.OfflinePlayer
import io.music_assistant.client.offline.OfflineStorage
import io.music_assistant.client.services.AndroidCarConnectionMonitor
import io.music_assistant.client.services.SharedMediaSessionManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

fun appModule() = module {
    single { AutoLibrary(androidContext(), get(), get(), get(), get(), get()) }
    single { SharedMediaSessionManager(androidContext(), get(), get()) }
    single<CarConnectionMonitor> { AndroidCarConnectionMonitor(androidContext()) }
    single<OfflineStorage> { AndroidOfflineStorage(androidContext()) }
    single<OfflinePlayer> { AndroidOfflinePlayer(androidContext(), get()) }
}
