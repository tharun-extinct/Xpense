package dev.xpensetracker.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.xpensetracker.app.ingestion.RuleAssets
import dev.xpensetracker.app.ingestion.SmsBackfillWorker
import dev.xpensetracker.app.ui.navigation.ExpenseNavHost
import dev.xpensetracker.app.ui.onboarding.BackfillPromptScreen
import dev.xpensetracker.app.ui.onboarding.OnboardingScreen
import dev.xpensetracker.app.ui.theme.AccentTheme
import dev.xpensetracker.app.ui.theme.ExpenseTrackerTheme
import kotlinx.coroutines.launch

private enum class AppStage { ONBOARDING, PERMISSION_PENDING, BACKFILL_PROMPT, HOME }

class MainActivity : ComponentActivity() {

    private val smsPermissions = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as ExpenseTrackerApp
        lifecycleScope.launch {
            // Categories first: merchant rules point at category ids, and a database created
            // fresh at version 2 never runs the migration that seeds them.
            app.repository.seedCategoriesIfMissing()
            RuleAssets.seedMerchantRules(applicationContext, app.repository)
        }

        val alreadyGranted = hasSmsPermission()

        setContent {
            // Collected above the theme so the whole tree, including onboarding, is drawn in the
            // chosen accent. The first frame uses the default while DataStore reads from disk;
            // blocking composition on that read to avoid one frame of orange would be a worse
            // trade than the flicker.
            val accent by app.themePreferences.accent.collectAsState(initial = AccentTheme.DEFAULT)
            val scope = rememberCoroutineScope()

            ExpenseTrackerTheme(accent = accent) {
                var stage by remember {
                    mutableStateOf(if (alreadyGranted) AppStage.BACKFILL_PROMPT else AppStage.ONBOARDING)
                }

                val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                ) { results ->
                    stage = if (results.values.all { it }) AppStage.BACKFILL_PROMPT else AppStage.HOME
                }

                when (stage) {
                    AppStage.ONBOARDING -> OnboardingScreen(
                        onContinue = {
                            permissionLauncher.launch(smsPermissions)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    AppStage.PERMISSION_PENDING -> Unit
                    AppStage.BACKFILL_PROMPT -> BackfillPromptScreen(
                        onScanNow = {
                            enqueueBackfill()
                            stage = AppStage.HOME
                        },
                        onSkip = { stage = AppStage.HOME },
                        modifier = Modifier.fillMaxSize(),
                    )
                    AppStage.HOME -> ExpenseNavHost(
                        repository = app.repository,
                        onRunBackfill = {
                            // A scan from Settings is reachable even when the user declined at
                            // onboarding, so ask again rather than starting a worker that would
                            // fail against the SMS provider.
                            if (hasSmsPermission()) enqueueBackfill() else permissionLauncher.launch(smsPermissions)
                        },
                        selectedAccent = accent,
                        onSelectAccent = { scope.launch { app.themePreferences.setAccent(it) } },
                    )
                }
            }
        }
    }

    private fun hasSmsPermission(): Boolean = smsPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun enqueueBackfill() {
        val request = OneTimeWorkRequestBuilder<SmsBackfillWorker>().build()
        WorkManager.getInstance(applicationContext).enqueue(request)
    }
}
