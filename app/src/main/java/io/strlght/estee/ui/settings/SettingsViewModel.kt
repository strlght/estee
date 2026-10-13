package io.strlght.estee.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.strlght.estee.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class SettingsUiState(
    val serverUrl: String = "",
    val urlError: String? = null,
    val saved: Boolean = false,
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var loaded = false

    init {
        viewModelScope.launch {
            settings.serverUrl.collect { stored ->
                if (!loaded) {
                    loaded = true
                    _uiState.update { it.copy(serverUrl = stored) }
                }
            }
        }
    }

    fun onUrlChange(value: String) {
        _uiState.update { it.copy(serverUrl = value, urlError = null, saved = false) }
    }

    fun save(onSaved: () -> Unit = {}) {
        val trimmed = _uiState.value.serverUrl.trim().removeSuffix("/")
        val parsed = trimmed.toHttpUrlOrNull()
        when {
            trimmed.isEmpty() ->
                _uiState.update { it.copy(urlError = "URL must not be empty") }
            parsed == null ->
                _uiState.update { it.copy(urlError = "Not a valid URL — include http:// or https://") }
            else -> viewModelScope.launch {
                settings.setServerUrl(trimmed)
                _uiState.update { it.copy(serverUrl = trimmed, urlError = null, saved = true) }
                onSaved()
            }
        }
    }

    fun consumeSaved() {
        _uiState.update { it.copy(saved = false) }
    }
}
