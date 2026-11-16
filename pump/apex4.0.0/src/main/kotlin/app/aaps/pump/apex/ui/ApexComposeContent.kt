package app.aaps.pump.apex.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.pump.apex.ApexDriverStatus
import app.aaps.pump.apex.ApexPump
import app.aaps.pump.apex.R
import app.aaps.pump.apex.utils.keys.ApexBooleanKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.aaps.core.ui.R as CoreUiR

/**
 * Minimal Compose replacement of the old ApexFragment (XML layout).
 * Values are polled every 2 s; this keeps the screen free of ViewModels/DI.
 */
class ApexComposeContent(
    private val pluginName: String,
    private val pump: ApexPump,
    private val driverStatus: ApexDriverStatus,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val config: Config,
    private val commandQueue: CommandQueue,
    private val isConnected: () -> Boolean,
    private val isConnecting: () -> Boolean,
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val navIcon: @Composable () -> Unit = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiR.string.back))
            }
        }
        val actions: @Composable RowScope.() -> Unit = {
            onSettings?.let { action ->
                IconButton(onClick = action) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(CoreUiR.string.settings))
                }
            }
        }
        LaunchedEffect(Unit) {
            setToolbarConfig(ToolbarConfig(title = pluginName, navigationIcon = navIcon, actions = actions))
        }

        var tick by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(2000)
                tick++
            }
        }
        val scope = rememberCoroutineScope()

        // Reading `tick` makes everything below recompose on every poll.
        @Suppress("UNUSED_EXPRESSION") tick
        val st = pump.status

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoRow(
                rh.gs(R.string.status_conn_status),
                when {
                    st == null -> rh.gs(CoreUiR.string.unknown)
                    isConnected() -> rh.gs(R.string.overview_connection_status_connected)
                    isConnecting() -> rh.gs(R.string.overview_connection_status_connecting)
                    else -> rh.gs(R.string.overview_connection_status_disconnected)
                }
            )
            if (!preferences.get(ApexBooleanKey.HideSerial))
                InfoRow(rh.gs(R.string.setting_serial_number), pump.serialNumber)
            InfoRow(rh.gs(R.string.status_pump_status), st?.let { it.getPumpStatusIcon() + " " + it.getPumpStatus(rh) } ?: "?")
            InfoRow(rh.gs(R.string.status_battery), st?.let { it.getBatteryIcon() + " " + it.getBatteryLevel(rh) } ?: "?")
            InfoRow(rh.gs(R.string.status_reservoir), st?.getReservoirLevel(rh) ?: "?")
            InfoRow(rh.gs(R.string.status_tbr), st?.getTBR(rh) ?: "?")
            InfoRow(rh.gs(R.string.status_basal), st?.getBasal(rh) ?: "?")
            InfoRow(rh.gs(R.string.firmware_version), pump.firmwareVersion?.toLocalString(rh) ?: "?")
            InfoRow(rh.gs(R.string.status_last_bolus), pump.lastBolus?.toShortLocalString(rh) ?: "?")
            InfoRow(
                rh.gs(R.string.status_last_update),
                if (st == null) "?" else {
                    val d = st.dateTime
                    val hm = "${d.hourOfDay.toString().padStart(2, '0')}:${d.minuteOfHour.toString().padStart(2, '0')}"
                    if (config.isEngineeringMode()) hm + ":" + d.secondOfMinute.toString().padStart(2, '0') else hm
                }
            )
            driverStatus.message?.let { InfoRow(rh.gs(R.string.status_current_action), it) }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { scope.launch { commandQueue.readStatus("ApexComposeContent-manualRefresh") } }
            ) { Text(rh.gs(R.string.ui_refresh)) }

            if (pump.inProgressBolus != null) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { commandQueue.cancelAllBoluses(null) }
                ) { Text(rh.gs(R.string.ui_cancel_bolus)) }
            }
        }
    }

    @Composable
    private fun InfoRow(label: String, value: String) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
