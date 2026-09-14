package io.music_assistant.client.ui.compose.item

import androidx.lifecycle.ViewModel
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.settings.ViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ViewModeViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    fun viewModeFor(mediaType: MediaType): StateFlow<ViewMode> {
        if (mediaType == MediaType.TRACK) return MutableStateFlow(ViewMode.LIST).asStateFlow()
        return settingsRepository.viewMode(mediaType)
    }

    fun toggleFor(mediaType: MediaType) {
        if (mediaType == MediaType.TRACK) return
        val current = settingsRepository.viewMode(mediaType).value
        settingsRepository.setViewMode(mediaType, current.toggled())
    }
}
