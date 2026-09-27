package dev.xpensetracker.app.data.entity

/**
 * The seed category set. Ids are the former `SpendCategory` constant names, so every category
 * string already written to `transactions`, `budgets`, and `merchant_rules` resolves against a
 * seeded row and Migration1To2 never has to rewrite a stored value.
 *
 * Shared by the migration (which inserts these with raw SQL) and the first-run seeder, so the two
 * can never drift apart.
 */
object BuiltInCategories {

    val all: List<CategoryEntity> = listOf(
        seed("FOOD_AND_DRINKS", "Food & Drinks", 0xFFEC4899, "restaurant", 0),
        seed("GROCERIES", "Groceries", 0xFF34D399, "groceries", 1),
        seed("SHOPPING", "Shopping", 0xFF38BDF8, "shopping", 2),
        seed("BILLS", "Bills", 0xFF4ADE80, "receipt", 3),
        seed("EMI", "EMI", 0xFFB0B0B0, "emi", 4),
        seed("FUEL", "Fuel", 0xFFF59E0B, "fuel", 5),
        seed("INVESTMENT", "Investment", 0xFF8FA6C4, "investment", 6),
        seed("TRAVEL", "Travel", 0xFF818CF8, "travel", 7),
        seed("RENT", "Rent", 0xFFA78BFA, "home", 8),
        seed("SELF_CARE", "Self Care", 0xFFC084FC, "self_care", 9),
        seed("TRANSFER", "Transfer", 0xFF60A5FA, "transfer", 10),
        seed("FRIENDS", "Friends", 0xFFFBBF24, "friends", 11),
        seed("OTHER", "Other", 0xFFFB923C, "other", 12),
        seed(UNKNOWN_CATEGORY_ID, "Uncategorized", 0xFF6B7280, "unknown", 13),
    )

    val ids: Set<String> = all.map { it.id }.toSet()

    fun isBuiltIn(id: String): Boolean = id in ids

    private fun seed(id: String, name: String, colorArgb: Long, iconKey: String, sortOrder: Int) =
        CategoryEntity(
            id = id,
            name = name,
            colorArgb = colorArgb,
            iconKey = iconKey,
            isBuiltIn = true,
            sortOrder = sortOrder,
            isArchived = false,
        )
}
