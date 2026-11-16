package app.aaps.pump.apex.utils.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.apex.R
import app.aaps.pump.apex.connectivity.FirmwareVersion
import app.aaps.pump.apex.connectivity.commands.pump.AlarmLength
import app.aaps.pump.apex.misc.BatteryType

/** User-visible string settings. Entries of [FirmwareVer] are filled by the plugin (depend on engineering mode). */
enum class ApexStringKey(
    override val key: String,
    override val defaultValue: String,
    private val titleResId: Int,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    private val entriesResIds: Map<String, Int> = emptyMap(),
    override val defaultedBySM: Boolean = false,
    override val isPassword: Boolean = false,
    override val isPin: Boolean = false,
    override val dependency: BooleanPreferenceKey? = null,
) : StringPreferenceKey {

    SerialNumber("apex_serial_number", "", R.string.setting_serial_number),
    AlarmSoundLength(
        "apex_alarm_length", AlarmLength.Short.name, R.string.setting_alarm_length,
        preferenceType = PreferenceType.LIST,
        entriesResIds = mapOf(
            AlarmLength.Long.name to R.string.setting_alarm_length_long,
            AlarmLength.Medium.name to R.string.setting_alarm_length_medium,
            AlarmLength.Short.name to R.string.setting_alarm_length_short,
        )
    ),
    CalcBatteryType(
        "apex_battery_type", BatteryType.Custom.name, R.string.setting_calc_battery_type_title,
        preferenceType = PreferenceType.LIST,
        entriesResIds = mapOf(
            BatteryType.Alkaline.name to R.string.setting_calc_battery_type_alkaline,
            BatteryType.Lithium.name to R.string.setting_calc_battery_type_lithium,
            BatteryType.NiMh.name to R.string.setting_calc_battery_type_ni_mh,
            BatteryType.NiZn.name to R.string.setting_calc_battery_type_ni_zn,
            BatteryType.Custom.name to R.string.setting_calc_battery_type_custom,
        ),
        dependency = ApexBooleanKey.CalculateBatteryPercentage,
    ),
    FirmwareVer("apex_fw_ver", FirmwareVersion.AUTO.name, R.string.firmware_version, preferenceType = PreferenceType.LIST),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
    override val entries: Map<String, TextRef> = entriesResIds.mapValues { TextRef.AndroidRes(it.value) }
}
