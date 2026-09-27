package dev.xpensetracker.app.ui.navigation

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import dev.xpensetracker.app.data.AppDatabase
import dev.xpensetracker.app.data.ExpenseRepository
import dev.xpensetracker.app.data.RoomTestDatabase
import dev.xpensetracker.app.data.entity.TransactionDirection
import dev.xpensetracker.app.data.entity.TransactionEntity
import dev.xpensetracker.app.data.entity.TransactionState
import dev.xpensetracker.app.data.entity.UNKNOWN_CATEGORY_ID
import dev.xpensetracker.app.ui.components.displayLabel
import dev.xpensetracker.app.ui.theme.ExpenseTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

/**
 * Smoke test for blueprints/design-system-and-navigation.md: every destination renders on an empty
 * database, and the layered full-screen routes are reachable and dismissible.
 *
 * The tabs are matched by content description rather than text, because the same words also appear
 * as headings inside the screens themselves. Taps go through the click semantics action rather than
 * synthetic touch input: this asserts that navigation is wired up, and hit-testing against a full
 * Scaffold on Robolectric's small default display is not what is under test here.
 *
 * Every tap target is resolved through [awaitNode], because a destination change is asynchronous:
 * the node a test wants next may be one frame away, and matching it eagerly fails for timing
 * reasons that look identical to a broken route.
 */
@RunWith(RobolectricTestRunner::class)
class ExpenseNavHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repository: ExpenseRepository

    @Before
    fun setUp() {
        db = RoomTestDatabase.create()
        repository = ExpenseRepository(db)
        runBlocking { repository.seedCategoriesIfMissing() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun render() {
        composeRule.setContent {
            ExpenseTrackerTheme {
                ExpenseNavHost(repository = repository)
            }
        }
    }

    private fun insertNeedsReviewTransaction() = runBlocking {
        val now = System.currentTimeMillis()
        repository.insertTransaction(
            TransactionEntity(
                contentHash = "hash-review",
                amountMinor = 25_000L,
                direction = TransactionDirection.DEBIT,
                merchantRaw = "Kirana Store",
                merchantKey = "kirana store",
                category = UNKNOWN_CATEGORY_ID,
                accountIssuer = "HDFC",
                accountTail = "1234",
                refNo = "REF1",
                balanceMinor = null,
                occurredAtUtcMillis = now,
                state = TransactionState.NEEDS_REVIEW,
                createdAtUtcMillis = now,
            ),
        )
    }

    private fun tab(label: String) = composeRule.awaitNode(
        matcher = hasContentDescription(label) and hasClickAction(),
        description = """the "$label" tab""",
    ).tap()

    @Test
    fun `all four destinations are reachable from an empty database`() {
        render()

        tab("Spends")
        composeRule.onNodeWithText("Manage categories").assertDoesNotExist()
        tab("Accounts")
        tab("Settings")
        // Exists, not displayed: Settings scrolls, and how far down this row sits depends on how
        // many panels precede it. Asserting visibility would make an unrelated copy change fail a
        // navigation test, which is not what this test is for.
        composeRule.onNodeWithText("Manage categories").assertExists()
        tab("Home")
    }

    // The month is hoisted into the host precisely so the two screens cannot disagree about it.
    // Matched with onAllNodes because a screen may legitimately print the month more than once.
    @Test
    fun `the month filter is present on both Home and Spends`() {
        render()
        val label = YearMonth.now().displayLabel()

        composeRule.onAllNodesWithText(label).onFirst().assertIsDisplayed()

        tab("Spends")
        composeRule.onAllNodesWithText(label).onFirst().assertIsDisplayed()
    }

    @Test
    fun `the needs-review banner opens the review screen and back returns home`() {
        insertNeedsReviewTransaction()
        render()

        composeRule.awaitNode(
            matcher = hasContentDescription("Open review queue") and hasClickAction(),
            description = "the needs-review banner",
        ).tap()
        composeRule.onNodeWithText("Needs review").assertIsDisplayed()
        composeRule.onNodeWithText("Kirana Store").assertIsDisplayed()

        composeRule.awaitNode(
            matcher = hasContentDescription("Go back") and hasClickAction(),
            description = "the back button",
        ).tap()
        composeRule.onNodeWithText("Needs review").assertDoesNotExist()
    }

    @Test
    fun `the category manager is reachable from settings`() {
        render()

        tab("Settings")
        composeRule.awaitNode(
            matcher = hasText("Manage categories") and hasClickAction(),
            description = "the category manager row",
        ).tap()

        composeRule.onNodeWithText("Shopping").assertIsDisplayed()
    }
}

private fun SemanticsNodeInteraction.tap(): SemanticsNodeInteraction =
    performSemanticsAction(SemanticsActions.OnClick)

/**
 * Resolves a node once it has settled, instead of assuming it is on screen the instant the caller
 * asks for it. The bottom bar in particular only joins the tree after the nav back stack has
 * emitted its first entry and the bar's reveal animation has run, so matching it eagerly is a race.
 *
 * On timeout the semantics tree is attached to the failure: CI only ever shows the assertion
 * message, and "could not find the node" is not enough to tell a wiring bug from a timing one.
 */
private fun ComposeContentTestRule.awaitNode(
    matcher: SemanticsMatcher,
    description: String,
): SemanticsNodeInteraction {
    try {
        waitUntil(timeoutMillis = 5_000) { onAllNodes(matcher).fetchSemanticsNodes().size == 1 }
    } catch (timeout: ComposeTimeoutException) {
        throw AssertionError(
            "Never found exactly one node for $description.\n${onRoot().printToString(maxDepth = 100)}",
            timeout,
        )
    }
    return onNode(matcher)
}
