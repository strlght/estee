package io.strlght.estee.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.strlght.estee.R
import io.strlght.estee.data.SettingsRepository
import io.strlght.estee.data.overrideCount
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class SettingsUiState(
    val serverUrl: String = "",
    val urlError: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val overrideCount: StateFlow<Int> = settings.inferenceParams
        .map { it.overrideCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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
                _uiState.update { it.copy(urlError = getString(R.string.error_url_empty)) }
            parsed == null ->
                _uiState.update { it.copy(urlError = getString(R.string.error_url_invalid)) }
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

    private fun getString(resId: Int, vararg formatArgs: Any?): String =
        context.getString(resId, *formatArgs)
}
