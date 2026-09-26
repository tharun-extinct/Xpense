package dev.expensetracker.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.expensetracker.app.ui.theme.BackgroundBase
import dev.expensetracker.app.ui.theme.AccentPrimary
import dev.expensetracker.app.ui.theme.TextPrimary
import dev.expensetracker.app.ui.theme.TextSecondary

/**
 * Opt-in trigger for the historical inbox scan (SmsBackfillWorker); never runs automatically.
 * See blueprints/sms-ingestion.md and blueprints/permissions-and-onboarding.md.
 */
@Composable
fun BackfillPromptScreen(onScanNow: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBase)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "Import past transactions?",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                modifier = Modifier.padding(top = 32.dp),
            )
            Text(
                text = "We can scan your existing SMS inbox once to bring in transactions you " +
                    "already have. You can also do this later from Settings.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        Column {
            Button(
                onClick = onScanNow,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary, contentColor = BackgroundBase),
            ) {
                Text("Scan inbox now")
            }
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text("Skip for now")
            }
        }
    }
}
