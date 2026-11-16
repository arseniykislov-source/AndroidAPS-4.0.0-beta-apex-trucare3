package app.aaps.pump.apex

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.DoseStepSize
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.pump.mapState
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.withEntriesProvider
import app.aaps.core.ui.compose.icons.IcPluginVirtualPump
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.utils.waitMillis
import app.aaps.pump.apex.connectivity.ApexBluetooth
import app.aaps.pump.apex.connectivity.FirmwareVersion
import app.aaps.pump.apex.ui.ApexComposeContent
import app.aaps.pump.apex.utils.keys.ApexBooleanKey
import app.aaps.pump.apex.utils.keys.ApexDoubleKey
import app.aaps.pump.apex.utils.keys.ApexStringKey
import app.aaps.pump.apex.utils.keys.ApexStringNonKey
import app.aaps.pump.apex.utils.toApexReadableProfile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.roundToLong
import dev.zacsweers.metro.IntKey as MetroIntKey

/**
 * @author Roman Rikhter (teledurak@gmail.com) - original driver
 * Port to AAPS 4.0.0 Pump interface.
 */
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@PumpDriver
@MetroIntKey(1140)
@SingleIn(AppScope::class)
@Inject
class ApexPumpPlugin(
    aapsLogger: AAPSLogger,
    override val rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val context: Context,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val pump: ApexPump,
    private val driverStatus: ApexDriverStatus,
    private val config: Config,
    private val pumpEnactResultProvider: () -> PumpEnactResult,
    notificationManager: NotificationManager,
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .icon(IcPluginVirtualPump)
        .pluginName(TextRef.AndroidRes(R.string.apex_plugin_name))
        .description(TextRef.AndroidRes(R.string.apex_plugin_description))
        .composeContent { plugin ->
            val self = plugin as ApexPumpPlugin
            ApexComposeContent(
                pluginName = rh.gs(R.string.apex_plugin_name),
                pump = pump,
                driverStatus = driverStatus,
                rh = rh,
                preferences = preferences,
                config = config,
                commandQueue = commandQueue,
                isConnected = { self.isConnected() },
                isConnecting = { self.isConnecting() },
            )
        },
    ownPreferences = ApexStringKey.entries + ApexDoubleKey.entries + ApexBooleanKey.entries + ApexStringNonKey.entries,
    aapsLogger, rh, preferences, commandQueue, notificationManager
), Pump {

    private var scope: CoroutineScope? = null
    private var service: ApexService? = null

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            aapsLogger.debug(LTag.PUMP, "Service is disconnected")
            service = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            aapsLogger.debug(LTag.PUMP, "Service is connected")
            val mLocalBinder = service as ApexService.LocalBinder
            this@ApexPumpPlugin.service = mLocalBinder.serviceInstance.also {
                it.startConnection()
            }
        }
    }

    override suspend fun onStart() {
        super.onStart()
        aapsLogger.debug(LTag.PUMP, "Starting APEX plugin")
        context.bindService(Intent(context, ApexService::class.java), connection, Context.BIND_AUTO_CREATE)
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        rxBus.toFlow(EventAppExit::class)
            .collectResilient(newScope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { unbindSafely() }
    }

    override suspend fun onStop() {
        aapsLogger.debug(LTag.PUMP, "Stopping APEX plugin")
        service?.disconnect()
        unbindSafely()
        service = null
        scope?.cancel()
        scope = null
        super.onStop()
    }

    private fun unbindSafely() {
        try {
            context.unbindService(connection)
        } catch (_: IllegalArgumentException) {
            // already unbound
        }
    }

    // ---------------------------------------------------------------- state

    override val isFakingTempsByExtendedBoluses = false
    override fun canHandleDST() = false

    override fun manufacturer() = ManufacturerType.Apex
    override fun model() = PumpType.APEX_TRUCARE_III
    override fun serialNumber() = preferences.get(ApexStringNonKey.LastConnectedSerialNumber)

    override val pumpDescription = PumpDescription().fillFor(model())

    override val lastDataTime: StateFlow<Long> = pump.lastDataTimeFlow
    override val lastBolusTime: StateFlow<Long?> = pump.lastBolusTimeFlow
    override val lastBolusAmount: StateFlow<PumpInsulin?> = pump.lastBolusAmountFlow.mapState { it?.let(::PumpInsulin) }
    override val baseBasalRate: PumpRate get() = PumpRate(pump.basal?.rate ?: 0.0)
    override val reservoirLevel: StateFlow<PumpInsulin> = pump.reservoirFlow.mapState(::PumpInsulin)
    override val batteryLevel: StateFlow<Int?> = pump.batteryFlow

    override fun isBusy() = false
    override fun isSuspended() = pump.isSuspended
    override fun isInitialized() = pump.isInitialized && service != null
    override fun isConnecting() = computeConnecting()
    override fun isHandshakeInProgress() = false
    override fun isConnected() = computeConnected()
    override fun isBatteryChangeLoggingEnabled() = preferences.get(ApexBooleanKey.LogBatteryChange)

    private fun computeConnecting() = when (service?.connectionStatus) {
        ApexBluetooth.Status.CONNECTING -> true
        ApexBluetooth.Status.CONNECTED -> service?.isReadyForExecutingCommands == false
        else -> false
    }

    private fun computeConnected() =
        service?.connectionStatus == ApexBluetooth.Status.CONNECTED && service?.isReadyForExecutingCommands == true

    private fun notReady() = pumpEnactResultProvider().success(false).enacted(false).comment(rh.gs(R.string.error_not_ready))
    private fun suspended() = pumpEnactResultProvider().success(false).enacted(false).comment(rh.gs(R.string.error_pump_suspended))
    private fun failed(resId: Int) = pumpEnactResultProvider().success(false).enacted(false).comment(rh.gs(resId))

    // ------------------------------------------------------------ connection

    // We should be always connected to the pump.
    override fun connect(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Triggered connect: $reason")
        if (service?.apexBluetooth?.status == ApexBluetooth.Status.DISCONNECTED) {
            service?.startConnection()
        }
    }

    override fun disconnect(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Triggered disconnect: $reason")
    }

    override fun stopConnecting() {
        aapsLogger.debug(LTag.PUMP, "Triggered stopConnecting")
    }

    // ---------------------------------------------------------------- status

    override fun extendedStatus(): JsonObject = buildJsonObject {
        if (!isInitialized() || !isConnected()) return@buildJsonObject
        val date = pump.dateTime
        if (date.millis + 60 * 60 * 1000L < System.currentTimeMillis()) return@buildJsonObject
        val status = pump.status ?: return@buildJsonObject
        val now = System.currentTimeMillis()

        pump.lastBolus?.let {
            put("lastBolus", dateUtil.dateAndTimeString(it.dateTime.millis))
            put("lastBolusAmount", it.standardPerformed * 0.025)
        }
        status.tbr?.let { tbr ->
            put("TempBasalAbsoluteRate", tbr.rate ?: (baseBasalRate.cU * tbr.percentage!! / 100.0))
            put("TempBasalStart", dateUtil.dateAndTimeString(now - tbr.elapsedMinutes * 60_000L))
            put("TempBasalRemaining", tbr.durationMinutes - tbr.elapsedMinutes)
        }
        put("BaseBasalRate", baseBasalRate.cU)
    }

    override fun pumpSpecificShortStatus(veryShort: Boolean): String {
        if (!isInitialized() || !isConnected()) return rh.gs(R.string.status_not_initialized)
        val status = pump.status ?: return ""
        val ret = "${rh.gs(R.string.status_conn_status)}: ${service!!.connectionStatus.toLocalString(rh)}\n" +
            "${rh.gs(R.string.status_pump_status)}: ${status.getPumpStatus(rh)}\n" +
            "${rh.gs(R.string.status_last_bolus)}: ${pump.lastBolus?.toShortLocalString(rh) ?: "-"}\n" +
            "${rh.gs(R.string.status_tbr)}: ${status.getTBR(rh)}\n" +
            "${rh.gs(R.string.status_basal)}: ${status.getBasal(rh)}\n" +
            "${rh.gs(R.string.status_reservoir)}: ${status.getReservoirLevel(rh)}\n" +
            "${rh.gs(R.string.status_battery)}: ${status.getBatteryLevel(rh)}"
        aapsLogger.debug(LTag.PUMP, "Short status: $ret")
        return ret
    }

    fun updatePumpDescription() {
        aapsLogger.debug(LTag.PUMP, "Updating pump description")
        pumpDescription.maxTempAbsolute = pump.maxBasal
        pumpDescription.basalMaximumRate = pump.maxBasal
    }

    override suspend fun getPumpStatus(reason: String) = withContext(Dispatchers.IO) {
        if (!isInitialized() || !isConnected()) return@withContext
        aapsLogger.debug(LTag.PUMP, "Requested pump status cause of $reason")
        service!!.getStatus("ApexPumpPlugin-getPumpStatus")
        Unit
    }

    override suspend fun loadTDDs(): PumpEnactResult = withContext(Dispatchers.IO) {
        if (!isInitialized() || !isConnected()) return@withContext notReady()
        val ok = service!!.getTDDs("ApexPumpPlugin-loadTDDs")
        pumpEnactResultProvider().success(ok).enacted(ok)
    }

    // --------------------------------------------------------------- profile

    override suspend fun setNewBasalProfile(profile: PumpProfile): PumpEnactResult = withContext(Dispatchers.IO) {
        if (!isInitialized() || !isConnected()) return@withContext notReady()

        if (!service!!.updateBasalPatternIndex(ApexService.USED_BASAL_PATTERN_INDEX, "ApexPumpPlugin-setNewBasalProfile"))
            return@withContext failed(R.string.error_failed_to_switch_basal_profile_index)

        if (!service!!.updateCurrentBasalPattern(profile.toApexReadableProfile(), "ApexPumpPlugin-setNewBasalProfile"))
            return@withContext failed(R.string.error_failed_to_update_basal_profile)

        pumpEnactResultProvider().success(true).enacted(true)
    }

    override fun isThisProfileSet(profile: PumpProfile): Boolean {
        if (!isInitialized() || !isConnected()) return true
        val pumpBasalProfiles = service!!.getBasalProfiles("ApexPumpPlugin-isThisProfileSet") ?: return true
        val pumpBasalProfile = pumpBasalProfiles[ApexService.USED_BASAL_PATTERN_INDEX] ?: return true
        for (i in 0..<48) {
            val profileBasal = profile.getBasalTimeFromMidnight(i * 30 * 60)
            val pumpBasal = pumpBasalProfile[i]
            val precision = DoseStepSize.Apex.getStepSizeForAmount(profileBasal) / 2
            if (abs(pumpBasal - profileBasal) > precision) {
                aapsLogger.info(LTag.PUMP, "Profiles are not same: req $profileBasal != pump $pumpBasal, block $i, time ${i * 30 * 60}")
                return false
            }
        }
        return true
    }

    // ---------------------------------------------------------------- bolus

    override suspend fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult = withContext(Dispatchers.IO) {
        // Insulin value must be greater than 0
        require(detailedBolusInfo.carbs == 0.0) { detailedBolusInfo.toString() }
        require(detailedBolusInfo.insulin > 0) { detailedBolusInfo.toString() }
        // Bolus is already constrained by the command queue before it reaches the driver.

        if (!isInitialized() || !isConnected()) return@withContext notReady()
        if (isSuspended()) return@withContext suspended()

        if (!service!!.bolus(detailedBolusInfo, "ApexPumpPlugin-deliverTreatment"))
            return@withContext failed(R.string.error_bolus_start_failed)

        // We send max of 3 commands for bolus: StatusV1, StatusV2, Bolus
        // Max wait time for any of them is 2.5s => 7.5s in total.
        // We may reconnect pump in order to try to fix connection issues. This takes ~45s.
        // Pump sets boluses in steps of 0.025U/s for boluses <=1U, 0.05U/s for boluses >1U.
        val maxReasonableBolusTime = (
            if (detailedBolusInfo.insulin <= 1.0)
                detailedBolusInfo.insulin / 0.025
            else
                detailedBolusInfo.insulin / 0.05
            ).roundToLong() + 45 + 8
        // Keep the reference: the service clears pump.inProgressBolus ~100 ms after completion.
        val bolus = pump.inProgressBolus
        bolus?.let {
            synchronized(it) {
                it.waitMillis(maxReasonableBolusTime * 1000)
            }
        }

        val successful = bolus != null && !bolus.failed
        val result = pumpEnactResultProvider().success(successful)
        if (successful) {
            result.enacted(bolus.currentDose > 0.024)
            result.bolusDelivered = bolus.currentDose
        }
        result
    }

    override fun stopBolusDelivering() {
        if (!isInitialized() || !isConnected()) return
        val s = service ?: return
        val sc = scope
        if (sc != null) sc.launch { s.cancelBolus("ApexPumpPlugin-stopBolusDelivering") }
        else s.cancelBolus("ApexPumpPlugin-stopBolusDelivering")
    }

    // ------------------------------------------------------------------ TBR

    override suspend fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult =
        withContext(Dispatchers.IO) {
            // Rate is already constrained by the command queue.
            val duration = durationInMinutes - durationInMinutes % 15

            if (!isInitialized() || !isConnected()) return@withContext notReady()
            if (isSuspended()) return@withContext suspended()

            val status = pump.status!!
            if (enforceNew && status.tbr != null) {
                if (!service!!.cancelTemporaryBasal("ApexPumpPlugin-setTempBasal"))
                    return@withContext failed(R.string.error_tbr_cancel_failed)
            }

            if (!service!!.temporaryBasal(absoluteRate, duration, tbrType, "ApexPumpPlugin-setTempBasal"))
                return@withContext failed(R.string.error_tbr_set_failed)

            pumpEnactResultProvider().success(true).enacted(true).duration(duration).absolute(absoluteRate).isPercent(false).isTempCancel(false)
        }

    override suspend fun setTempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult =
        failed(R.string.error_only_absolute_supported)

    override suspend fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult = withContext(Dispatchers.IO) {
        if (!isInitialized() || !isConnected()) return@withContext notReady()
        if (isSuspended()) return@withContext suspended()

        if (!service!!.cancelTemporaryBasal("ApexPumpPlugin-cancelTempBasal"))
            return@withContext failed(R.string.error_tbr_cancel_failed)

        pumpEnactResultProvider().success(true).enacted(true).isTempCancel(true)
    }

    // Extended boluses are not supported yet
    override suspend fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult = notReady()
    override suspend fun cancelExtendedBolus(): PumpEnactResult = notReady()

    override suspend fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
        withContext(Dispatchers.IO) {
            if (!isInitialized() || !isConnected()) return@withContext
            service!!.syncDateTime("ApexService-timezoneOrDSTChanged")
        }
    }

    // ----------------------------------------------------------- preferences

    override fun hasPreferences(): Boolean = true

    override fun getPreferenceScreenContent(): PreferenceSubScreenDef {
        val versions = FirmwareVersion.realValues.filter { !it.engineeringModeOnly || config.isEngineeringMode() }
        val firmwareEntries: () -> Map<String, TextRef> = {
            buildMap {
                put(FirmwareVersion.AUTO.name, TextRef.AndroidRes(R.string.auto))
                versions.forEach { put(it.name, TextRef.Literal(it.displayName)) }
            }
        }
        return PreferenceSubScreenDef(
            key = "apex_settings",
            title = TextRef.AndroidRes(R.string.apex_settings),
            items = listOf(
                ApexStringKey.SerialNumber,
                ApexStringKey.FirmwareVer.withEntriesProvider(provider = firmwareEntries),
                ApexStringKey.AlarmSoundLength,
                ApexBooleanKey.CalculateBatteryPercentage,
                ApexStringKey.CalcBatteryType,
                ApexDoubleKey.BatteryLowVoltage,
                ApexDoubleKey.BatteryHighVoltage,
                ApexDoubleKey.MaxBasal,
                ApexDoubleKey.MaxBolus,
                ApexBooleanKey.LogInsulinChange,
                ApexBooleanKey.LogBatteryChange,
                ApexBooleanKey.HideSerial,
            ),
            icon = pluginDescription.icon
        )
    }
}
