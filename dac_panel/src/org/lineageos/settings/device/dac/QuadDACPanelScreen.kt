/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.device.dac

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import org.lineageos.settings.device.dac.utils.Constants

@Composable
fun QuadDACPanelTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    MaterialTheme(
        // Follow the system (Android 12+ dynamic color from wallpaper); material3
        // falls back to the baseline scheme on older releases.
        colorScheme = if (isSystemInDarkTheme()) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuadDACPanelScreen(
    initialSearchKey: String?,
    onBack: () -> Unit,
    viewModel: QuadDACViewModel = viewModel(factory = QuadDACViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Replacement for the old Fragment onResume/onPause receiver lifecycle.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_HEADSET_PLUG) {
                    val plugged = intent.getIntExtra("state", -1) == 1
                    viewModel.onHeadsetStateChanged(plugged)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_HEADSET_PLUG),
            ContextCompat.RECEIVER_EXPORTED,
        )
        viewModel.refreshRuntimeState()
        onDispose { context.unregisterReceiver(receiver) }
    }

    // One toast per failed HAL write (old showApplyFailureToast).
    LaunchedEffect(state.errorEvent) {
        if (state.errorEvent > 0L) {
            Toast.makeText(context, R.string.failed_to_apply_setting, Toast.LENGTH_SHORT).show()
        }
    }

    if (initialSearchKey != null) {
        Log.d(TAG, "Launched from search with key: ${initialSearchKey}")
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.quad_dac)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when {
            state.initializing -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            !state.serviceAvailable -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.quad_dac_unavail))
                }
            }
            else -> QuadDACPanelList(state, viewModel, Modifier.padding(padding))
        }
    }
}

@Composable
private fun QuadDACPanelList(state: QuadDACUiState, viewModel: QuadDACViewModel,
        modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        item(key = "dac_switch") {
            DacHeroSwitchCard(
                checked = state.dacEnabled,
                enabled = state.dacSwitchEnabled,
                onCheckedChange = viewModel::onDacEnabledChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (!state.headsetPlugged) {
            item(key = "headset_plug_hint") {
                HeadsetNotPluggedBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        if (state.hifiModeSupported || state.soundPresetSupported || state.digitalFilterSupported || state.avcSupported) {
            item(key = "section_audio_settings") {
                SectionTitle(stringResource(R.string.quad_dac_settings))
            }
            item(key = "audio_settings_card") {
                SettingsCard {
                    var needsDivider = false
                    if (state.hifiModeSupported) {
                        DropdownSettingRow(
                            title = stringResource(R.string.hifi_mode),
                            options = entriesFor(Constants.HIFI_MODE_KEY),
                            selectedIndex = state.hifiMode,
                            enabled = state.extraEnabled,
                            onSelected = viewModel::onHifiModeSelected,
                        )
                        needsDivider = true
                    }
                    if (state.soundPresetSupported) {
                        if (needsDivider) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        DropdownSettingRow(
                            title = stringResource(R.string.sound_preset),
                            options = entriesFor(Constants.SOUND_PRESET_KEY),
                            selectedIndex = state.soundPreset,
                            enabled = state.extraEnabled,
                            onSelected = viewModel::onSoundPresetSelected,
                        )
                        needsDivider = true
                    }
                    if (state.digitalFilterSupported) {
                        if (needsDivider) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        DropdownSettingRow(
                            title = stringResource(R.string.digital_filter),
                            options = entriesFor(Constants.DIGITAL_FILTER_KEY),
                            selectedIndex = state.digitalFilter,
                            enabled = state.extraEnabled,
                            onSelected = viewModel::onDigitalFilterSelected,
                        )
                        needsDivider = true
                    }
                    if (state.avcSupported) {
                        if (needsDivider) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        SliderSettingRow(
                            title = stringResource(R.string.avc_volume),
                            valueText = stringResource(R.string.avc_value_db, state.avcVolume),
                            value = state.avcVolume.toFloat(),
                            range = state.avcVolumeRange.first.toFloat()..state.avcVolumeRange.last.toFloat(),
                            enabled = state.extraEnabled,
                            onValueChange = viewModel::onAvcVolumeChanged,
                        )
                    }
                }
            }
        }
        if (state.customFilterVisible) {
            item(key = "section_custom_filter") {
                SectionTitle(stringResource(R.string.customizable_filter_settings))
            }
            item(key = "custom_filter_card") {
                SettingsCard {
                    DropdownSettingRow(
                        title = stringResource(R.string.cf_shape),
                        options = entriesFor(Constants.CUSTOM_FILTER_SHAPE_KEY),
                        selectedIndex = state.customFilterShape,
                        enabled = state.extraEnabled,
                        onSelected = viewModel::onCustomFilterShapeSelected,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    DropdownSettingRow(
                        title = stringResource(R.string.cf_symmetry),
                        options = entriesFor(Constants.CUSTOM_FILTER_SYMMETRY_KEY),
                        selectedIndex = state.customFilterSymmetry,
                        enabled = state.extraEnabled,
                        onSelected = viewModel::onCustomFilterSymmetrySelected,
                    )
                }
            }
            item(key = "coeff_section_title") {
                SectionTitle(stringResource(R.string.customizable_filter_coeffs))
            }
            item(key = "coeff_warning_banner") {
                CoefficientsWarningBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item(key = "coeffs_card") {
                SettingsCard {
                    state.coefficients.forEachIndexed { index, value ->
                        val coeffText = if (value < 0) "-0.${-value}" else "0.$value"
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            )
                        }
                        CoefficientSettingRow(
                            title = stringResource(R.string.cf_coeff_label, index, coeffText),
                            value = value,
                            range = COEFF_RANGE_FIRST..COEFF_RANGE_LAST,
                            enabled = state.extraEnabled,
                            onValueChange = { newValue, commitNow ->
                                viewModel.onCoefficientChanged(index, newValue, commitNow)
                            },
                        )
                    }
                }
            }
            item(key = Constants.RESET_COEFFICIENTS_KEY) {
                OutlinedButton(
                    onClick = viewModel::onResetCoefficients,
                    enabled = state.extraEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = MaterialTheme.shapes.full,
                ) {
                    Text(stringResource(R.string.cf_reset))
                }
            }
        }
        if (state.balanceSupported) {
            item(key = "section_balance") {
                SectionTitle(stringResource(R.string.quad_dac_balance))
            }
            item(key = Constants.BALANCE_KEY) {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.quad_dac_balance_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BalanceColumn(
                                title = "L",
                                value = state.balanceLeft,
                                range = state.balanceRange,
                                enabled = state.extraEnabled,
                                onAdjust = { delta -> viewModel.onBalanceAdjusted(BalanceSide.LEFT, delta) },
                            )
                            Box(
                                modifier = Modifier
                                    .height(48.dp)
                                    .width(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            )
                            BalanceColumn(
                                title = "R",
                                value = state.balanceRight,
                                range = state.balanceRange,
                                enabled = state.extraEnabled,
                                onAdjust = { delta -> viewModel.onBalanceAdjusted(BalanceSide.RIGHT, delta) },
                            )
                        }
                    }
                }
            }
        }
        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DacHeroSwitchCard(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (checked && enabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (checked && enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (checked && enabled) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_quad_dac_icon),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = if (checked && enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.quad_dac),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            if (checked) R.string.quad_dac_on else R.string.quad_dac_off
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.75f),
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
                thumbContent = if (checked) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                } else null,
            )
        }
    }
}

@Composable
private fun HeadsetNotPluggedBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Headphones,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.headset_not_plugged_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        content = content,
    )
}

@Composable
private fun CoefficientsWarningBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.customizable_filter_coeffs_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingRow(title: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        content()
    }
}

@Composable
private fun DropdownSettingRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.getOrNull(selectedIndex) ?: options.firstOrNull() ?: title

    SettingRow(title = title) {
        // The DropdownMenu must share a Box with its trigger: the menu's Popup
        // anchors to the nearest parent layout, which without the Box is the
        // full-width SettingRow — popping the menu at the screen's left edge.
        Box {
            TextButton(onClick = { expanded = true }, enabled = enabled) {
                Text(selectedLabel)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onSelected(index)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderSettingRow(
    title: String,
    valueText: String? = null,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Int, Boolean) -> Unit,
) {
    // onValueChangeFinished carries no value, so track the latest drag position here.
    var latestValue by remember { mutableStateOf(value) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (valueText != null) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = value.coerceIn(range),
            onValueChange = {
                latestValue = it
                onValueChange(it.roundToInt(), false)
            },
            onValueChangeFinished = { onValueChange(latestValue.roundToInt(), true) },
            valueRange = range,
            enabled = enabled,
        )
    }
}

/**
 * Coefficient row: slider for dragging plus a numeric text field for direct
 * entry. Input is committed on IME Done or focus loss; out-of-range values are
 * clamped, non-numeric input reverts to the current HAL value.
 */
@Composable
private fun CoefficientSettingRow(
    title: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onValueChange: (Int, Boolean) -> Unit,
) {
    var text by remember { mutableStateOf(value.toString()) }
    var latestValue by remember { mutableStateOf(value) }
    // Mirror external updates (slider drags, HAL rollbacks) unless mid-edit.
    LaunchedEffect(value) {
        if (text.toIntOrNull() != value) {
            text = value.toString()
        }
    }
    val focusManager = LocalFocusManager.current
    val commit = {
        val parsed = text.trim().toIntOrNull()
        if (parsed == null) {
            text = value.toString()        // not a number: revert
        } else {
            val clamped = parsed.coerceIn(range.first, range.last)
            text = clamped.toString()
            onValueChange(clamped, true)
        }
        focusManager.clearFocus()
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = text,
                onValueChange = { raw ->
                    // Digits only, with an optional single leading '-'; at most
                    // 7 digits caps the value at ±9999999. Bare "-" is allowed
                    // as an intermediate state while typing.
                    val digits = raw.filter { it in '0'..'9' }.take(7)
                    val cleaned = if (raw.startsWith('-')) "-$digits" else digits
                    val parsed = cleaned.toIntOrNull()
                    if (cleaned.isEmpty() || cleaned == "-"
                        || (parsed != null && parsed in range)) {
                        text = cleaned
                    }
                },
                modifier = Modifier.width(96.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                enabled = enabled,
            )
        }
        Slider(
            value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = {
                latestValue = it.roundToInt()
                onValueChange(latestValue, false)
            },
            onValueChangeFinished = { onValueChange(latestValue, true) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
        )
    }
}

@Composable
private fun BalanceColumn(
    title: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onAdjust: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onAdjust(-1) },
                enabled = enabled && value > range.first,
            ) {
                Icon(Icons.Filled.Remove, contentDescription = null)
            }
            Text(
                text = stringResource(R.string.balance_value_db, value / 2.0),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.width(64.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(
                onClick = { onAdjust(+1) },
                enabled = enabled && value < range.last,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    }
}

/** Resolves the arrays.xml entries array for a preference key (index-aligned with HAL values). */
@Composable
private fun entriesFor(key: String): List<String> {
    val context = LocalContext.current
    val arrayRes = when (key) {
        Constants.HIFI_MODE_KEY -> R.array.hifi_mode_entry_values
        Constants.SOUND_PRESET_KEY -> R.array.sound_preset_entry_values
        Constants.DIGITAL_FILTER_KEY -> R.array.digital_filter_entry_values
        Constants.CUSTOM_FILTER_SHAPE_KEY -> R.array.custom_filter_shapes
        Constants.CUSTOM_FILTER_SYMMETRY_KEY -> R.array.custom_filter_symmetries
        else -> 0
    }
    return if (arrayRes != 0) context.resources.getStringArray(arrayRes).toList() else emptyList()
}

/** Maps a Settings-search preference key to a LazyColumn item key (scroll target). */
object SearchKeyMapper {
    fun sectionFor(key: String?): String? = when (key) {
        Constants.DAC_SWITCH_KEY -> "dac_switch"
        Constants.HIFI_MODE_KEY,
        Constants.AVC_VOLUME_KEY,
        Constants.SOUND_PRESET_KEY,
        Constants.DIGITAL_FILTER_KEY -> "audio_settings_card"
        Constants.CUSTOM_FILTER_SHAPE_KEY,
        Constants.CUSTOM_FILTER_SYMMETRY_KEY -> "custom_filter_card"
        Constants.RESET_COEFFICIENTS_KEY -> Constants.RESET_COEFFICIENTS_KEY
        Constants.BALANCE_KEY -> Constants.BALANCE_KEY
        else -> null
    }
}

private const val TAG = "QuadDACPanel"
private const val COEFF_RANGE_FIRST = -9999999
private const val COEFF_RANGE_LAST = 9999999
