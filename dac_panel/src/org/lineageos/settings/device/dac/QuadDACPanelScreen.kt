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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
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
                        SingleChoicePreferenceRow(
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
                        SingleChoicePreferenceRow(
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
                        SingleChoicePreferenceRow(
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
            item(key = "custom_filter_warning_banner") {
                CustomFilterWarningBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item(key = "custom_filter_card") {
                SettingsCard {
                    SingleChoicePreferenceRow(
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
                    SegmentedChoiceSettingRow(
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
                FilledTonalButton(
                    onClick = viewModel::onResetCoefficients,
                    enabled = state.extraEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.quad_dac_balance_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            val isCentered = state.balanceLeft == 0 && state.balanceRight == 0
                            if (!isCentered && state.extraEnabled) {
                                TextButton(
                                    onClick = {
                                        viewModel.onResetBalance()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.balance_center))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BalanceKnobColumn(
                                title = "L",
                                value = state.balanceLeft,
                                range = state.balanceRange,
                                enabled = state.extraEnabled,
                                onValueChange = { newVal, commitNow ->
                                    viewModel.onBalanceChanged(BalanceSide.LEFT, newVal, commitNow)
                                },
                                onCommit = {
                                    viewModel.onBalanceCommit(BalanceSide.LEFT)
                                },
                                onAdjust = { delta ->
                                    viewModel.onBalanceAdjusted(BalanceSide.LEFT, delta)
                                },
                            )
                            Box(
                                modifier = Modifier
                                    .height(120.dp)
                                    .width(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            )
                            BalanceKnobColumn(
                                title = "R",
                                value = state.balanceRight,
                                range = state.balanceRange,
                                enabled = state.extraEnabled,
                                onValueChange = { newVal, commitNow ->
                                    viewModel.onBalanceChanged(BalanceSide.RIGHT, newVal, commitNow)
                                },
                                onCommit = {
                                    viewModel.onBalanceCommit(BalanceSide.RIGHT)
                                },
                                onAdjust = { delta ->
                                    viewModel.onBalanceAdjusted(BalanceSide.RIGHT, delta)
                                },
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
private fun CustomFilterWarningBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .size(22.dp)
                    .padding(top = 2.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.customizable_filter_coeffs_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
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
private fun SingleChoicePreferenceRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedLabel = options.getOrNull(selectedIndex) ?: options.firstOrNull() ?: ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            if (selectedLabel.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                Column(modifier = Modifier.selectableGroup()) {
                    options.forEachIndexed { index, option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .selectable(
                                    selected = index == selectedIndex,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onSelected(index)
                                        showDialog = false
                                    },
                                )
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = index == selectedIndex,
                                onClick = null,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.back))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentedChoiceSettingRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
        Spacer(modifier = Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = index == selectedIndex,
                    onClick = { onSelected(index) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    enabled = enabled,
                ) {
                    Text(text = label, maxLines = 1)
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
                shape = MaterialTheme.shapes.small,
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

private fun angleToValue(
    touchX: Float,
    touchY: Float,
    centerX: Float,
    centerY: Float,
    currentValue: Int,
    range: IntRange,
): Int {
    val dx = touchX - centerX
    val dy = touchY - centerY
    if (dx * dx + dy * dy < 25f) return currentValue

    var angle = atan2(dy.toDouble(), dx.toDouble()) * (180.0 / PI)
    if (angle < 0.0) angle += 360.0

    // Arc starts at 135 deg (bottom-left) and sweeps clockwise 270 deg to 45 deg (bottom-right).
    // The bottom opening/gap is (45 deg, 135 deg).
    val sweep: Double = when {
        angle in 135.0..360.0 -> angle - 135.0
        angle in 0.0..45.0 -> angle + 360.0 - 135.0
        else -> {
            val mid = (range.first + range.last) / 2
            if (currentValue >= mid) 270.0 else 0.0
        }
    }
    val fraction = (sweep / 270.0).coerceIn(0.0, 1.0)
    val totalSteps = (range.last - range.first).coerceAtLeast(1)
    return range.first + (fraction * totalSteps).roundToInt()
}

@Composable
private fun RotaryKnob(
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onValueChange: (Int, Boolean) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceContainerHighestColor = MaterialTheme.colorScheme.surfaceContainerHighest

    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnCommit by rememberUpdatedState(onCommit)
    var lastTapTime by remember { mutableStateOf(0L) }

    Canvas(
        modifier = modifier
            .pointerInput(enabled, range) {
                if (!enabled) return@pointerInput
                val center = Offset(size.width / 2f, size.height / 2f)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val startTime = System.currentTimeMillis()

                    if (startTime - lastTapTime < 300L) {
                        currentOnValueChange(0, true)
                        lastTapTime = 0L
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                        }
                        return@awaitEachGesture
                    }

                    var isDrag = false
                    var lastReportedVal = currentValue

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        change.consume()

                        if (!change.pressed) {
                            if (!isDrag) {
                                val tappedVal = angleToValue(
                                    touchX = change.position.x,
                                    touchY = change.position.y,
                                    centerX = center.x,
                                    centerY = center.y,
                                    currentValue = lastReportedVal,
                                    range = range,
                                )
                                currentOnValueChange(tappedVal, true)
                                lastTapTime = startTime
                            } else {
                                currentOnCommit()
                            }
                            break
                        }

                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (!isDrag && (dx * dx + dy * dy) > 25f) {
                            isDrag = true
                        }

                        if (isDrag) {
                            val newVal = angleToValue(
                                touchX = change.position.x,
                                touchY = change.position.y,
                                centerX = center.x,
                                centerY = center.y,
                                currentValue = lastReportedVal,
                                range = range,
                            )
                            if (newVal != lastReportedVal) {
                                lastReportedVal = newVal
                                currentOnValueChange(newVal, false)
                            }
                        }
                    }
                }
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f
        val trackRadius = maxRadius - 10.dp.toPx()
        val trackStroke = 6.dp.toPx()
        val knobRadius = trackRadius - trackStroke / 2f - 6.dp.toPx()

        val totalSteps = (range.last - range.first).coerceAtLeast(1)
        val fraction = ((value - range.first).toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
        val activeSweep = fraction * 270f
        val indicatorAngle = 135f + activeSweep

        // 1. Ticks around the perimeter (7 ticks for major dB points)
        for (i in 0..6) {
            val tickAngle = 135f + (i / 6f) * 270f
            val rad = tickAngle * (PI / 180.0)
            val innerR = trackRadius + trackStroke / 2f + 3.dp.toPx()
            val outerR = innerR + if (i == 0 || i == 3 || i == 6) 4.5.dp.toPx() else 2.5.dp.toPx()
            val tickStart = Offset(
                x = center.x + (innerR * cos(rad)).toFloat(),
                y = center.y + (innerR * sin(rad)).toFloat(),
            )
            val tickEnd = Offset(
                x = center.x + (outerR * cos(rad)).toFloat(),
                y = center.y + (outerR * sin(rad)).toFloat(),
            )
            drawLine(
                color = outlineVariantColor.copy(alpha = if (enabled) 0.7f else 0.3f),
                start = tickStart,
                end = tickEnd,
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // 2. Background track arc (270 deg)
        drawArc(
            color = outlineVariantColor.copy(alpha = 0.4f),
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(center.x - trackRadius, center.y - trackRadius),
            size = Size(trackRadius * 2f, trackRadius * 2f),
            style = Stroke(width = trackStroke, cap = StrokeCap.Round),
        )

        // 3. Active progress arc
        if (activeSweep > 0.5f) {
            drawArc(
                color = if (enabled) primaryColor else outlineVariantColor,
                startAngle = 135f,
                sweepAngle = activeSweep,
                useCenter = false,
                topLeft = Offset(center.x - trackRadius, center.y - trackRadius),
                size = Size(trackRadius * 2f, trackRadius * 2f),
                style = Stroke(width = trackStroke, cap = StrokeCap.Round),
            )
        }

        // 4. Knob body
        drawCircle(
            color = surfaceContainerHighestColor,
            radius = knobRadius,
            center = center,
        )
        drawCircle(
            color = outlineVariantColor.copy(alpha = 0.6f),
            radius = knobRadius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx()),
        )

        // 5. Knob indicator line
        val indRad = indicatorAngle * (PI / 180.0)
        val pStart = Offset(
            x = center.x + (knobRadius * 0.40f * cos(indRad)).toFloat(),
            y = center.y + (knobRadius * 0.40f * sin(indRad)).toFloat(),
        )
        val pEnd = Offset(
            x = center.x + (knobRadius * 0.85f * cos(indRad)).toFloat(),
            y = center.y + (knobRadius * 0.85f * sin(indRad)).toFloat(),
        )
        drawLine(
            color = if (enabled) primaryColor else outlineVariantColor,
            start = pStart,
            end = pEnd,
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // 6. Knob center pivot dot
        drawCircle(
            color = if (enabled) primaryColor.copy(alpha = 0.7f) else outlineVariantColor,
            radius = 3.dp.toPx(),
            center = center,
        )
    }
}

@Composable
private fun BalanceKnobColumn(
    title: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onValueChange: (Int, Boolean) -> Unit,
    onCommit: () -> Unit,
    onAdjust: (Int) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        RotaryKnob(
            value = value,
            range = range,
            enabled = enabled,
            onValueChange = onValueChange,
            onCommit = onCommit,
            modifier = Modifier
                .size(120.dp)
                .semantics {
                    contentDescription = "$title: ${value / 2.0} dB"
                },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.balance_value_db, value / 2.0),
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            FilledTonalIconButton(
                onClick = { onAdjust(-1) },
                enabled = enabled && value > range.first,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            FilledTonalIconButton(
                onClick = { onAdjust(+1) },
                enabled = enabled && value < range.last,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
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
