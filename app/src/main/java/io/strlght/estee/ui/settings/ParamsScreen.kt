package io.strlght.estee.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.strlght.estee.R
import io.strlght.estee.data.ParamKind
import io.strlght.estee.data.ParamSpec
import io.strlght.estee.data.ParamSpecs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParamsScreen(
    viewModel: ParamsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val savedMessage = stringResource(R.string.params_saved)

    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbarHostState.showSnackbar(savedMessage)
            viewModel.consumeSaved()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_params)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.params_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ParamSpecs.GROUPS.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(group.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(group.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                group.specs.forEach { spec ->
                    val value = state.values[spec.name].orEmpty()
                    val error = state.errors[spec.name]
                    if (spec.kind == ParamKind.BOOLEAN) {
                        BooleanParamField(
                            spec = spec,
                            value = value,
                            onValueChange = { viewModel.onParamChange(spec.name, it) },
                        )
                    } else {
                        TextParamField(
                            spec = spec,
                            value = value,
                            error = error,
                            onValueChange = { viewModel.onParamChange(spec.name, it) },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.save()
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
                TextButton(onClick = viewModel::resetAll) {
                    Text(stringResource(R.string.action_reset))
                }
            }
        }
    }
}

@Composable
private fun TextParamField(
    spec: ParamSpec,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(spec.name, fontFamily = FontFamily.Monospace) },
        placeholder = { Text(stringResource(R.string.params_default_placeholder, spec.default)) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = when (spec.kind) {
                ParamKind.INTEGER -> KeyboardType.Number
                ParamKind.DECIMAL -> KeyboardType.Decimal
                else -> KeyboardType.Text
            },
            imeAction = ImeAction.Next,
        ),
        textStyle = when (spec.kind) {
            ParamKind.INTEGER, ParamKind.DECIMAL ->
                MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
            else -> MaterialTheme.typography.bodyLarge
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BooleanParamField(
    spec: ParamSpec,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val defaultOption = stringResource(R.string.params_option_server_default, spec.default)
    val options = listOf("" to defaultOption, "true" to "true", "false" to "false")

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == value }?.second ?: defaultOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(spec.name, fontFamily = FontFamily.Monospace) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (stored, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onValueChange(stored)
                        expanded = false
                    },
                )
            }
        }
    }
}
