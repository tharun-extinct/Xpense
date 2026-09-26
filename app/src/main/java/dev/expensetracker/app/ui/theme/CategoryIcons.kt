package dev.expensetracker.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `categories.iconKey` to a drawable. Keys are stored as stable strings rather than resource ids
 * so a row stays valid across icon-library changes; an unrecognised key falls back instead of
 * crashing, because the value can come from a database a newer build wrote.
 */
object CategoryIcons {

    /** The set offered when creating or editing a category, in picker order. */
    val selectableKeys: List<String> = listOf(
        "restaurant",
        "groceries",
        "shopping",
        "receipt",
        "emi",
        "fuel",
        "investment",
        "travel",
        "home",
        "self_care",
        "transfer",
        "friends",
        "car",
        "health",
        "entertainment",
        "education",
        "internet",
        "gift",
        "pets",
        "other",
    )

    fun resolve(iconKey: String): ImageVector = when (iconKey) {
        "restaurant" -> Icons.Filled.Restaurant
        "groceries" -> Icons.Filled.LocalGroceryStore
        "shopping" -> Icons.Filled.ShoppingBag
        "receipt" -> Icons.Filled.Receipt
        "emi" -> Icons.Filled.AccountBalance
        "fuel" -> Icons.Filled.LocalGasStation
        "investment" -> Icons.Filled.TrendingUp
        "travel" -> Icons.Filled.Flight
        "home" -> Icons.Filled.Home
        "self_care" -> Icons.Filled.Spa
        "transfer" -> Icons.Filled.SwapHoriz
        "friends" -> Icons.Filled.People
        "car" -> Icons.Filled.DirectionsCar
        "health" -> Icons.Filled.LocalHospital
        "entertainment" -> Icons.Filled.MovieFilter
        "education" -> Icons.Filled.School
        "internet" -> Icons.Filled.Wifi
        "gift" -> Icons.Filled.CardGiftcard
        "pets" -> Icons.Filled.Pets
        else -> Icons.Filled.Category
    }
}
