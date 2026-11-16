package app.aaps.pump.apex.utils.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.apex.R

enum class ApexDoubleKey(
    override val key: String,
    override val defaultValue: Double,
    override val min: Double,
    override val max: Double,
    private val titleResId: Int,
    override val defaultedBySM: Boolean = false,
    override val calculatedBySM: Boolean = false,
    override val dependency: BooleanPreferenceKey? = null,
) : DoublePreferenceKey {

    // 0 == uninitialized
    MaxBasal("apex_max_basal", 0.0, 0.0, 25.0, R.string.setting_max_basal),
    MaxBolus("apex_max_bolus", 0.0, 0.0, 25.0, R.string.setting_max_bolus),
    BatteryLowVoltage("apex_low_batt_vtg", 1.2, 1.0, 1.8, R.string.setting_calc_battery_low_vtg, dependency = ApexBooleanKey.CalculateBatteryPercentage),
    BatteryHighVoltage("apex_high_batt_vtg", 1.5, 1.0, 1.8, R.string.setting_calc_battery_high_vtg, dependency = ApexBooleanKey.CalculateBatteryPercentage),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
}
