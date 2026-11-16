package app.aaps.pump.apex.utils.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

/** Internal state, not shown in settings. Key names are unchanged from the old driver so stored values survive. */
enum class ApexStringNonKey(
    override val key: String,
    override val defaultValue: String,
) : StringNonPreferenceKey {

    LastConnectedSerialNumber("apex_last_connected_serial_number", ""),
    BluetoothAddress("apex_bt_address", ""),
}
