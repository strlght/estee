package io.strlght.estee.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.strlght.estee.data.SettingsRepository
import io.strlght.estee.data.toEditableValues
import io.strlght.estee.ui.params.buildInferenceParams
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class ParamsUiState(
    val values: Map<String, String> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
    val saved: Boolean = false,
)

@HiltViewModel
class ParamsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParamsUiState())
    val uiState: StateFlow<ParamsUiState> = _uiState.asStateFlow()

    private var loaded = false

    init {
        viewModelScope.launch {
            settings.inferenceParams.collect { stored ->
                if (!loaded) {
                    loaded = true
                    _uiState.update { it.copy(values = stored.toEditableValues(json)) }
                }
            }
        }
    }

    fun onParamChange(name: String, value: String) {
        _uiState.update {
            it.copy(
                values = if (value.isBlank()) it.values - name else it.values + (name to value),
                errors = it.errors - name,
                saved = false,
            )
        }
    }

    fun resetAll() {
        _uiState.update { it.copy(values = emptyMap(), errors = emptyMap(), saved = false) }
    }

    fun save(onSaved: () -> Unit = {}) {
        val outcome = buildInferenceParams(_uiState.value.values)
        if (outcome.errors.isNotEmpty()) {
            _uiState.update {
                it.copy(errors = outcome.errors.mapValues { (_, error) -> getString(error.messageRes) })
            }
            return
        }
        viewModelScope.launch {
            settings.setInferenceParams(outcome.params)
            _uiState.update { it.copy(saved = true) }
            onSaved()
        }
    }

    fun consumeSaved() {
        _uiState.update { it.copy(saved = false) }
    }

    private fun getString(resId: Int, vararg formatArgs: Any?): String =
        context.getString(resId, *formatArgs)
}
