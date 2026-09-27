package dev.xpensetracker.app.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private val smsPermissions = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

@Composable
fun rememberSmsPermissionState(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResultCompat(onResult)
    return { launcher() }
}

/** Thin wrapper kept in one place so the permission-request call site stays simple and testable. */
@Composable
private fun rememberLauncherForActivityResultCompat(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> onResult(results.values.all { it }) }

    return {
        val alreadyGranted = smsPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (alreadyGranted) {
            onResult(true)
        } else {
            launcher.launch(smsPermissions)
        }
    }
}
