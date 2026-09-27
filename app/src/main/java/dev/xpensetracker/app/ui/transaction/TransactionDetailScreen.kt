package dev.xpensetracker.app.ui.transaction

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.xpensetracker.app.data.ExpenseRepository
import dev.xpensetracker.app.data.entity.TransactionDirection
import dev.xpensetracker.app.data.entity.TransactionEntity
import dev.xpensetracker.app.data.entity.TransactionState
import dev.xpensetracker.app.location.DeviceLocationSource
import dev.xpensetracker.app.location.LocationResult
import dev.xpensetracker.app.location.openInMaps
import dev.xpensetracker.app.ui.components.CategoryAvatar
import dev.xpensetracker.app.ui.components.CategoryLookup
import dev.xpensetracker.app.ui.components.CategoryPickerGrid
import dev.xpensetracker.app.ui.components.EmptyState
import dev.xpensetracker.app.ui.components.ScreenTopBar
import dev.xpensetracker.app.ui.format.toRupeeDisplay
import dev.xpensetracker.app.ui.theme.BackgroundBase
import dev.xpensetracker.app.ui.theme.Divider
import dev.xpensetracker.app.ui.theme.AccentPrimary
import dev.xpensetracker.app.ui.theme.PositiveGreen
import dev.xpensetracker.app.ui.theme.Spacing
import dev.xpensetracker.app.ui.theme.SurfaceCard
import dev.xpensetracker.app.ui.theme.TextPrimary
import dev.xpensetracker.app.ui.theme.TextSecondary
import dev.xpensetracker.app.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val detailDateFormatter = SimpleDateFormat("EEEE, d MMMM yyyy 'at' h:mm a", Locale.getDefault())

/**
 * A full screen rather than a sheet, because recategorizing is a deliberate task with a grid of
 * options and a consequential toggle — it deserves the whole viewport and its own back stack entry.
 */
@Composable
fun TransactionDetailScreen(
    repository: ExpenseRepository,
    transactionId: Long,
    onBack: () -> Unit,
    onCreateCategory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transaction by repository.observeTransaction(transactionId).collectAsState(initial = null)
    val categories by repository.observeSelectableCategories().collectAsState(initial = emptyList())
    val allCategories by repository.observeCategories().collectAsState(initial = emptyList())
    val lookup = remember(allCategories) { CategoryLookup.from(allCategories) }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val locationSource = remember(context) { DeviceLocationSource(context) }

    var pendingCategoryId by remember { mutableStateOf<String?>(null) }
    var pendingDirection by remember { mutableStateOf<TransactionDirection?>(null) }
    var teachMerchant by rememberSaveable { mutableStateOf(false) }

    // Note and location need a "seeded" flag rather than the null-means-unset trick the two above
    // use, because for these two null is a value the user can choose: clearing a note has to be
    // distinguishable from never having opened the screen. All four are saveable together, or a
    // process death would either discard typing or re-seed over the top of what was restored.
    var draftSeeded by rememberSaveable { mutableStateOf(false) }
    var noteDraft by rememberSaveable { mutableStateOf("") }
    var draftLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var draftLng by rememberSaveable { mutableStateOf<Double?>(null) }
    var locatingNow by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<String?>(null) }

    val locationDraft = remember(draftLat, draftLng) { taggedPointOf(draftLat, draftLng) }

    val current = transaction
    // Seeds the selection once the row arrives; the Flow starts as null.
    LaunchedEffect(current?.id) {
        if (pendingCategoryId == null) pendingCategoryId = current?.category
        if (pendingDirection == null) pendingDirection = current?.direction
        if (!draftSeeded && current != null) {
            noteDraft = current.note.orEmpty()
            draftLat = current.locationLat
            draftLng = current.locationLng
            draftSeeded = true
        }
    }

    val readLocation: suspend () -> Unit = {
        locatingNow = true
        locationMessage = null
        when (val result = locationSource.currentLocation()) {
            is LocationResult.Fix -> {
                draftLat = result.latitude
                draftLng = result.longitude
            }
            LocationResult.PermissionMissing ->
                locationMessage = "Location permission is needed to tag this transaction."
            LocationResult.LocationOff ->
                locationMessage = "Location is switched off. Turn it on in system settings and try again."
            LocationResult.Unavailable ->
                locationMessage = "Could not get a fix. Try again near a window or outdoors."
        }
        locatingNow = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) {
            scope.launch { readLocation() }
        } else {
            // A refusal costs the user nothing but this one feature, and the copy says so rather
            // than pushing them back toward the dialog.
            locationMessage = "No location tagged. Everything else on this screen still works."
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBase),
    ) {
        ScreenTopBar(title = "Transaction", onBack = onBack)

        if (current == null) {
            EmptyState(
                icon = Icons.Filled.SearchOff,
                title = "Transaction not found",
                message = "It may have been removed. Go back and pick another one.",
            )
            return@Column
        }

        val selectedId = pendingCategoryId ?: current.category
        val selectedDirection = pendingDirection ?: current.direction
        val isIncome = selectedDirection == TransactionDirection.CREDIT
        val categoryChanged = selectedId != current.category
        val directionChanged = selectedDirection != current.direction
        val noteChanged = noteDraft.trim() != current.note.orEmpty()
        val locationChanged = locationDraft != current.taggedPoint()
        val canSave = categoryChanged || directionChanged || noteChanged || locationChanged ||
            teachMerchant || current.state == TransactionState.NEEDS_REVIEW

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            TransactionSummary(current, lookup, selectedDirection)

            DetailPanel(current, selectedDirection)

            DirectionCard(
                accountLabel = current.accountLabel(),
                isIncome = isIncome,
                onIncomeChange = {
                    pendingDirection = if (it) TransactionDirection.CREDIT else TransactionDirection.DEBIT
                },
            )

            NoteCard(note = noteDraft, onNoteChange = { noteDraft = it })

            LocationCard(
                point = locationDraft,
                isLocating = locatingNow,
                message = locationMessage,
                onTag = {
                    if (locationSource.hasPermission()) {
                        scope.launch { readLocation() }
                    } else {
                        permissionLauncher.launch(LOCATION_PERMISSIONS)
                    }
                },
                onClear = {
                    draftLat = null
                    draftLng = null
                    locationMessage = null
                },
                onOpen = { point ->
                    if (!context.openInMaps(point.latitude, point.longitude, current.merchantRaw)) {
                        locationMessage = "No map app is installed to open this location."
                    }
                },
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                CategoryPickerGrid(
                    categories = categories,
                    selectedId = selectedId,
                    onSelect = { pendingCategoryId = it },
                    onCreateNew = onCreateCategory,
                )
            }

            if (current.merchantKey.isNotBlank()) {
                TeachMerchantToggle(
                    merchantLabel = current.merchantRaw,
                    checked = teachMerchant,
                    onCheckedChange = { teachMerchant = it },
                )
            }

            Spacer(Modifier.height(Spacing.sm))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard)
                .padding(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Button(
                onClick = {
                    scope.launch {
                        // Everything before the recategorize call: that one re-reads the row, so
                        // writing these after would save a copy taken before they landed.
                        repository.setTransactionDirection(current.id, selectedDirection)
                        repository.setTransactionNote(current.id, noteDraft)
                        repository.setTransactionLocation(
                            transactionId = current.id,
                            latitude = locationDraft?.latitude,
                            longitude = locationDraft?.longitude,
                        )
                        repository.recategorizeTransaction(
                            transactionId = current.id,
                            categoryId = selectedId,
                            alsoTeachMerchant = teachMerchant,
                        )
                        onBack()
                    }
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.minTouchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPrimary,
                    contentColor = BackgroundBase,
                    disabledContainerColor = Divider,
                    disabledContentColor = TextTertiary,
                ),
            ) {
                Text(
                    text = if (current.state == TransactionState.NEEDS_REVIEW) {
                        "Confirm transaction"
                    } else {
                        "Save changes"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if (current.state != TransactionState.IGNORED) {
                Text(
                    text = if (isIncome) "Not real income? Ignore it" else "Not a real xpense? Ignore it",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                repository.setTransactionState(current.id, TransactionState.IGNORED)
                                onBack()
                            }
                        }
                        .padding(vertical = Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun TransactionSummary(
    transaction: TransactionEntity,
    lookup: CategoryLookup,
    direction: TransactionDirection,
) {
    val isCredit = direction == TransactionDirection.CREDIT
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        CategoryAvatar(
            iconKey = lookup.iconKey(transaction.category),
            color = lookup.color(transaction.category),
            size = 64.dp,
        )
        Text(
            text = (if (isCredit) "+" else "\u2212") + transaction.amountMinor.toRupeeDisplay(),
            style = MaterialTheme.typography.displayMedium,
            color = if (isCredit) PositiveGreen else TextPrimary,
        )
        Text(
            text = transaction.merchantRaw,
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (transaction.state == TransactionState.NEEDS_REVIEW) {
            Text(
                text = "Needs review",
                style = MaterialTheme.typography.labelSmall,
                color = AccentPrimary,
                modifier = Modifier
                    .background(AccentPrimary.copy(alpha = 0.14f), MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }
    }
}

@Composable
private fun DetailPanel(transaction: TransactionEntity, direction: TransactionDirection) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, MaterialTheme.shapes.large)
            .border(1.dp, Divider, MaterialTheme.shapes.large)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        DetailRow("When", detailDateFormatter.format(Date(transaction.occurredAtUtcMillis)))
        DetailRow(
            label = "Direction",
            value = if (direction == TransactionDirection.CREDIT) "Money in" else "Money out",
        )
        DetailRow("Account", transaction.accountLabel())
        transaction.refNo?.let { DetailRow("Reference", it) }
        transaction.balanceMinor?.let { DetailRow("Balance after", it.toRupeeDisplay()) }
        if (transaction.isUserEdited) {
            DetailRow("Edited", "Changed by you")
        }
    }
}

/** The instrument the money moved on, or an honest blank when the SMS never named one. */
private fun TransactionEntity.accountLabel(): String = listOfNotNull(
    accountIssuer,
    accountTail?.let { "\u2022\u2022\u2022\u2022 $it" },
).joinToString(" ").ifBlank { "Not detected" }

/**
 * Lets the user overrule the parser's read of which way the money went.
 *
 * Direction decides which total a transaction lands in, so a misread credit silently inflates
 * spends and there was previously no way to say otherwise. The effect is spelled out rather than
 * left to the words "income" and "xpense", because the consequence — whether this amount counts
 * against the month's spending — is the part the user is actually deciding.
 */
@Composable
private fun DirectionCard(
    accountLabel: String,
    isIncome: Boolean,
    onIncomeChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, MaterialTheme.shapes.large)
            .border(1.dp, Divider, MaterialTheme.shapes.large)
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = accountLabel,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
            )
            Text(
                text = if (isIncome) {
                    "Counts as income. It stays out of your spends and budgets."
                } else {
                    "Counts as an xpense in your spends and budgets."
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
        Text(
            text = if (isIncome) "Income" else "Expense",
            style = MaterialTheme.typography.labelLarge,
            color = if (isIncome) PositiveGreen else TextPrimary,
            modifier = Modifier.padding(start = Spacing.md),
        )
        Switch(
            checked = isIncome,
            onCheckedChange = onIncomeChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BackgroundBase,
                checkedTrackColor = PositiveGreen,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Divider,
                uncheckedBorderColor = Divider,
            ),
            modifier = Modifier
                .padding(start = Spacing.sm)
                .semantics { contentDescription = "Count this as income" },
        )
    }
}

/**
 * The user's own words about a transaction, for the cases the parser can never recover: which
 * friend the transfer was for, what the unlabelled UPI handle actually was.
 *
 * Nothing reads this back. It is memory, not metadata, and that is the point — a note can say
 * anything without changing how a single rupee is counted.
 */
@Composable
private fun NoteCard(note: String, onNoteChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, MaterialTheme.shapes.large)
            .border(1.dp, Divider, MaterialTheme.shapes.large)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = "Note", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "What was this for?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            minLines = 2,
            maxLines = 6,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = AccentPrimary,
                unfocusedBorderColor = Divider,
                cursorColor = AccentPrimary,
                focusedContainerColor = BackgroundBase,
                unfocusedContainerColor = BackgroundBase,
            ),
        )
    }
}

/**
 * Attaches where the user is standing to a transaction they are reviewing.
 *
 * The caption is not decoration: an SMS can arrive minutes or days after the purchase, so this
 * records where the user is *now*, and letting someone believe otherwise would quietly corrupt
 * their own record. Tapping a tag hands it to the device's default map app, which is the only
 * moment anything stored here leaves this app, and it takes a deliberate tap.
 */
@Composable
private fun LocationCard(
    point: TaggedPoint?,
    isLocating: Boolean,
    message: String?,
    onTag: () -> Unit,
    onClear: () -> Unit,
    onOpen: (TaggedPoint) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, MaterialTheme.shapes.large)
            .border(1.dp, Divider, MaterialTheme.shapes.large)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = "Location", style = MaterialTheme.typography.titleSmall, color = TextPrimary)

        if (point == null) {
            Text(
                text = "Tag where you are now. Useful when the merchant name in the SMS says " +
                    "nothing about where you actually were.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(point) }
                    .heightIn(min = Spacing.minTouchTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    tint = AccentPrimary,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.padding(start = Spacing.md)) {
                    Text(
                        text = point.display(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Open in maps",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentPrimary,
                    )
                }
            }
        }

        if (message != null) {
            Text(text = message, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TextButton(onClick = onTag, enabled = !isLocating) {
                Text(
                    text = when {
                        isLocating -> "Getting location\u2026"
                        point == null -> "Tag location"
                        else -> "Update"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isLocating) TextTertiary else AccentPrimary,
                )
            }
            if (point != null) {
                TextButton(onClick = onClear) {
                    Text(
                        text = "Remove",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

/** A tagged position held in draft form, so it can be discarded by leaving without saving. */
private data class TaggedPoint(val latitude: Double, val longitude: Double) {
    /**
     * Fixed to five decimals (about a metre) and always dot-separated: a coordinate rendered with
     * the device's decimal comma reads as four numbers instead of two.
     */
    fun display(): String = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
}

/** A point exists only when both halves do; half a coordinate is a place nobody has been. */
private fun taggedPointOf(latitude: Double?, longitude: Double?): TaggedPoint? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    return TaggedPoint(lat, lng)
}

private fun TransactionEntity.taggedPoint(): TaggedPoint? = taggedPointOf(locationLat, locationLng)

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
        Box(modifier = Modifier.padding(start = Spacing.lg)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun TeachMerchantToggle(
    merchantLabel: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard, MaterialTheme.shapes.large)
            .border(1.dp, Divider, MaterialTheme.shapes.large)
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Remember for $merchantLabel",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
            )
            Text(
                // Opt-in and forward-only, stated plainly so the behaviour is not a surprise
                // later (architecture.md #identity-and-ownership).
                text = "Future transactions from this merchant get this category. " +
                    "Past ones stay as they are.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BackgroundBase,
                checkedTrackColor = AccentPrimary,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Divider,
                uncheckedBorderColor = Divider,
            ),
            modifier = Modifier.padding(start = Spacing.md),
        )
    }
}
