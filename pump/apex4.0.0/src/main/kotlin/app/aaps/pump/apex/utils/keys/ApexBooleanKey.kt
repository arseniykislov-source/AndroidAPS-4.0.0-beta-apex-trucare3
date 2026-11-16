package app.aaps.pump.apex.utils.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.apex.R

enum class ApexBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    private val titleResId: Int,
    private val summaryResId: Int? = null,
    override val defaultedBySM: Boolean = true,
) : BooleanPreferenceKey {

    LogInsulinChange("apex_log_insulin_change", true, R.string.setting_log_insulin_change),
    LogBatteryChange("apex_log_battery_change", true, R.string.setting_log_battery_change),
    CalculateBatteryPercentage("apex_calc_battery_percentage", true, R.string.setting_calc_battery_title, R.string.setting_calc_battery_summary),
    HideSerial("apex_hide_serial", true, R.string.setting_hide_serial),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
    override val summary: TextRef? = summaryResId?.let { TextRef.AndroidRes(it) }
}
