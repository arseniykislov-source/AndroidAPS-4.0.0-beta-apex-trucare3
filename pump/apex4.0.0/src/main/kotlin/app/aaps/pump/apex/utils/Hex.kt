package app.aaps.pump.apex.utils

/** Local replacement for the former app.aaps.core.utils.toHex extension (not present in AAPS 4.0.0). */
fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
