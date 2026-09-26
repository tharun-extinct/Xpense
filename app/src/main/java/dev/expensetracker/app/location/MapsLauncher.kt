package dev.expensetracker.app.location

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Hands a coordinate to whatever map app the user has installed, returning false when nothing
 * accepted it so the caller can say so instead of appearing to do nothing.
 *
 * A `geo:` intent rather than a Google Maps URL: this is the user's default map service, and the
 * app has no business deciding that is Google's. It is also the only moment a stored coordinate
 * leaves this app, and it happens because the user tapped it.
 */
fun Context.openInMaps(latitude: Double, longitude: Double, label: String): Boolean {
    val point = "$latitude,$longitude"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$point?q=$point(${Uri.encode(label)})"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(intent)
        true
    } catch (noMapApp: ActivityNotFoundException) {
        false
    }
}
