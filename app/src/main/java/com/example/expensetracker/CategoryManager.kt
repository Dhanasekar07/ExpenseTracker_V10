package com.example.expensetracker

import android.content.Context
import android.graphics.Color

data class Category(
    val id        : String,
    val name      : String,
    val iconName  : String,
    val colorHex  : String,       // light pastel — icon background
    val chartColor: String = "",  // mid-tone — pie/bar charts
    val iconTint  : String = "",  // dark — icon tint
    var isActive  : Boolean = false,
    var sortOrder : Int = 0
) {
    fun color()     = parseC(colorHex)
    fun chartCol()  = parseC(chartColor.ifEmpty { colorHex })
    fun tintCol()   = parseC(iconTint.ifEmpty { "#666666" })
    private fun parseC(hex: String) = try { Color.parseColor(hex) } catch (e: Exception) { Color.GRAY }
}

object CategoryManager {

    const val MAX_CATEGORIES = 40

    // 30 predefined categories with three-tier colours
    val CATALOG = listOf(
        Category("cat_01", "Food",              "restaurant",     "#FDBA74", "#F59E0B", "#C2410C"),
        Category("cat_02", "Tea/Coffee",        "coffee",         "#CAA47E", "#B45309", "#78350F"),
        Category("cat_03", "Snacks",            "snacks",         "#FED7AA", "#F97316", "#EA580C"),
        Category("cat_04", "Grocery",           "shopping_cart",  "#BBF7D0", "#22C55E", "#15803D"),
        Category("cat_05", "Shopping",          "shopping_bag",   "#D6BBFA", "#8B5CF6", "#7C3AED"),
        Category("cat_06", "Entertainment",     "entertainment",  "#FBCFE8", "#EC4899", "#BE185D"),
        Category("cat_07", "Movies",            "movies",         "#E9D5FF", "#A855F7", "#7E22CE"),
        Category("cat_08", "OTT",               "subscriptions",  "#FDE047", "#EAB308", "#854D0E"),
        Category("cat_09", "Fuel",              "fuel",           "#5EEAD4", "#14B8A6", "#115E59"),
        Category("cat_10", "Transport",         "transport",      "#67E8F9", "#06B6D4", "#155E75"),
        Category("cat_11", "Taxi/Ride",         "taxi",           "#99F6E4", "#0D9488", "#0F766E"),
        Category("cat_12", "Medicine",          "medicine",       "#FCA5A5", "#EF4444", "#991B1B"),
        Category("cat_13", "Mutual Funds",      "investment",     "#86EFAC", "#16A34A", "#166534"),
        Category("cat_14", "Loan EMI",          "credit_card",    "#A7F3D0", "#10B981", "#065F46"),
        Category("cat_15", "Online Order",      "package",        "#F472B6", "#DB2777", "#9D174D"),
        Category("cat_16", "Personal Grooming", "grooming",       "#FDA4AF", "#FB7185", "#9F1239"),
        Category("cat_17", "Internet",          "internet",       "#7DD3FC", "#0EA5E9", "#075985"),
        Category("cat_18", "Electricity",       "electricity",    "#C7D2FE", "#6366F1", "#4338CA"),
        Category("cat_19", "Gas",               "gas",            "#BAE6FD", "#38BDF8", "#0C4A6E"),
        Category("cat_20", "House Rent",        "home",           "#93C5FD", "#3B82F6", "#1E40AF"),
        Category("cat_21", "Insurance Premium", "insurance",      "#A5B4FC", "#818CF8", "#3730A3"),
        Category("cat_22", "Education",         "education",      "#DDD6FE", "#7C3AED", "#5B21B6"),
        Category("cat_23", "Fitness",           "fitness",        "#BEF264", "#84CC16", "#3F6212"),
        Category("cat_24", "Savings",           "savings",        "#6EE7B7", "#059669", "#064E3B"),
        Category("cat_25", "Laundry",           "laundry",        "#D9F99D", "#65A30D", "#4D7C0F"),
        Category("cat_26", "Water Bill",        "water",          "#A5F3FC", "#22D3EE", "#164E63"),
        Category("cat_27", "Travel",            "travel",         "#CFFAFE", "#06B6D4", "#0E7490"),
        Category("cat_28", "Gifts",             "gifts",          "#FBBF24", "#F59E0B", "#78350F"),
        Category("cat_29", "Donation",          "donation",       "#FFE4E6", "#F43F5E", "#BE123C"),
        Category("cat_30", "Monthly Bills",     "receipt",        "#C7D2FE", "#6366F1", "#4338CA")
    )

    val DEFAULT_ACTIVE_IDS = listOf(
        "cat_01", "cat_04", "cat_05", "cat_06", "cat_09", "cat_10"
    )

    // Custom category colour pool (for user-created categories)
    val CUSTOM_COLORS = listOf(
        Triple("#CBD5E1", "#64748B", "#334155"),
        Triple("#D1D5DB", "#6B7280", "#374151"),
        Triple("#E2E8F0", "#94A3B8", "#475569"),
        Triple("#FECDD3", "#E11D48", "#881337"),
        Triple("#FCE7F3", "#D946EF", "#A21CAF"),
        Triple("#FEF08A", "#CA8A04", "#713F12"),
        Triple("#FCD34D", "#D97706", "#92400E"),
        Triple("#F3E8FF", "#9333EA", "#6B21A8"),
        Triple("#CCFBF1", "#14B8A6", "#134E4A"),
        Triple("#F0ABFC", "#C026D3", "#86198F")
    )

    var allCategories: List<Category> = emptyList()
        private set

    var activeCategories: List<Category> = emptyList()
        private set

    val archivedCategories: List<Category>
        get() {
            val archived = allCategories.filter { !it.isActive }
            val systemIds = CATALOG.map { it.id }.toSet()
            val system = archived.filter { it.id in systemIds }.sortedBy { it.name.lowercase() }
            val custom = archived.filter { it.id !in systemIds }.sortedBy { it.name.lowercase() }
            return system + custom
        }

    fun initialize(context: Context) {
        val dbHelper = CategoryDbHelper(context)
        val stored   = dbHelper.getAllCategories()

        if (stored.isEmpty()) {
            CATALOG.forEachIndexed { i, cat ->
                val active = cat.id in DEFAULT_ACTIVE_IDS
                dbHelper.insertCategory(cat.copy(isActive = active, sortOrder = i))
            }
            allCategories    = dbHelper.getAllCategories()
            activeCategories = allCategories.filter { it.isActive }
        } else {
            allCategories    = stored
            activeCategories = stored.filter { it.isActive }
        }
    }

    fun getCategoryByName(name: String): Category? {
        return activeCategories.find { it.name.equals(name, ignoreCase = true) }
            ?: CATALOG.find { it.name.equals(name, ignoreCase = true) }
    }

    fun getNextCustomColor(existing: List<Category>): Triple<String, String, String> {
        val usedColors = existing.map { it.colorHex }.toSet()
        return CUSTOM_COLORS.firstOrNull { it.first !in usedColors }
            ?: CUSTOM_COLORS[0]
    }

    fun archiveCategory(context: Context, id: String) {
        CategoryDbHelper(context).updateCategoryActive(id, false)
        initialize(context)
    }

    fun activateCategory(context: Context, id: String) {
        val db = CategoryDbHelper(context)
        db.updateCategoryActive(id, true)
        // Set sort order to last position among active categories
        val maxOrder = allCategories.filter { it.isActive }.maxOfOrNull { it.sortOrder } ?: -1
        db.updateSortOrder(id, maxOrder + 1)
        initialize(context)
    }

    fun deleteCategory(context: Context, id: String) {
        CategoryDbHelper(context).deleteCategory(id)
        initialize(context)
    }

    fun renameCategory(context: Context, id: String, name: String) {
        CategoryDbHelper(context).renameCategory(id, name)
        initialize(context)
    }
}
