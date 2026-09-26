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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.expensetracker.app.ui.theme.BackgroundBase
import dev.expensetracker.app.ui.theme.AccentPrimary
import dev.expensetracker.app.ui.theme.TextPrimary
import dev.expensetracker.app.ui.theme.TextSecondary

/**
 * First-run explanation, shown before the system permission dialog, per
 * blueprints/permissions-and-onboarding.md. States the offline guarantee and the
 * Play Store distribution caveat explicitly.
 */
@Composable
fun OnboardingScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBase)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "Track spends from your SMS",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                modifier = Modifier.padding(top = 32.dp),
            )
            Text(
                text = "Expense Tracker reads bank and UPI SMS on this device to build your " +
                    "expense history automatically. Everything happens on your phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                modifier = Modifier.padding(top = 16.dp),
            )

            OnboardingPoint(
                title = "Fully offline",
                body = "This app has no internet access at all. Nothing you type or receive ever leaves your device.",
            )
            OnboardingPoint(
                title = "You stay in control",
                body = "Transactions the app isn't sure about are held for your review, never guessed silently.",
            )
            OnboardingPoint(
                title = "Not on the Play Store",
                body = "Reading SMS for expense tracking isn't allowed there, so this app is installed directly from a release build.",
            )
        }

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary, contentColor = BackgroundBase),
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun OnboardingPoint(title: String, body: String) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = AccentPrimary)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
