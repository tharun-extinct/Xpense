package dev.xpensetracker.app.ui.format

import java.text.NumberFormat
import java.util.Locale

/** Converts Long minor units (paise) to a displayed rupee string only at render time. */
fun Long.toRupeeDisplay(): String {
    val rupees = this / 100
    val format = NumberFormat.getNumberInstance(Locale("en", "IN"))
    return "\u20B9${format.format(rupees)}"
}
