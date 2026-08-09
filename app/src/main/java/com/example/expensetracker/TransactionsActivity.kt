package com.example.expensetracker

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class TransactionsActivity : AppCompatActivity() {

    private lateinit var db       : ExpenseDbHelper
    private lateinit var container: LinearLayout

    private var currentFilter = "day"
    private var customFrom    = 0L
    private var customTo      = 0L
    private var searchQuery   = ""
    private var isStackEntry  = false
    private val currency get() = AppPreferences.getCurrencySymbol(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transactions)

        db           = ExpenseDbHelper(this)
        container    = findViewById(R.id.transactionContainer)
        isStackEntry = intent.getStringExtra("entry") == "stack"

        try { CategoryManager.initialize(this) } catch (e: Exception) { e.printStackTrace() }

        val btnBack   = findViewById<ImageView>(R.id.btnBack)
        val btnSearch = findViewById<ImageView>(R.id.btnSearch)
        val searchBar = findViewById<LinearLayout>(R.id.searchBar)
        val etSearch  = searchBar.findViewById<EditText>(R.id.etSearch)

        btnBack.visibility = if (isStackEntry) View.VISIBLE else View.GONE
        btnBack.setOnClickListener { finish() }

        btnSearch.setOnClickListener {
            searchBar.visibility =
                if (searchBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.lowercase() ?: ""
                refreshList()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        setupFilterTabs()
        setupBottomNav()
        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun setupFilterTabs() {
        mapOf(
            R.id.tabDay to "day", R.id.tabWeek to "week",
            R.id.tabMonth to "month", R.id.tabCustom to "custom"
        ).forEach { (id, filter) ->
            findViewById<TextView>(id).setOnClickListener {
                if (filter == "custom") showDatePicker()
                else { currentFilter = filter; updateTabUI(); refreshList() }
            }
        }
        updateTabUI()
    }

    private fun updateTabUI() {
        listOf(
            R.id.tabDay to "day", R.id.tabWeek to "week",
            R.id.tabMonth to "month", R.id.tabCustom to "custom"
        ).forEach { (id, f) ->
            val tv = findViewById<TextView>(id)
            if (f == currentFilter) {
                tv.setBackgroundResource(R.drawable.bg_filter_active)
                tv.setTextColor(Color.WHITE)
            } else {
                tv.setBackgroundResource(R.drawable.bg_filter_inactive)
                tv.setTextColor(ThemeColors.secondary(this))
            }
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val from = Calendar.getInstance().apply {
                set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            DatePickerDialog(this, { _, y2, m2, d2 ->
                val to = Calendar.getInstance().apply {
                    set(y2, m2, d2, 23, 59, 59); set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                customFrom = from; customTo = to; currentFilter = "custom"
                updateTabUI(); refreshList()
            }, y, m, d).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun getFromTs(): Long {
        val cal = Calendar.getInstance()
        return when (currentFilter) {
            "day"    -> { cal.set(Calendar.HOUR_OF_DAY,0); cal.set(Calendar.MINUTE,0); cal.set(Calendar.SECOND,0); cal.timeInMillis }
            "week"   -> { cal.add(Calendar.DAY_OF_YEAR,-7); cal.timeInMillis }
            "month"  -> { cal.set(Calendar.DAY_OF_MONTH,1); cal.set(Calendar.HOUR_OF_DAY,0); cal.timeInMillis }
            "custom" -> customFrom
            else     -> 0L
        }
    }

    private fun getToTs() =
        if (currentFilter == "custom") customTo else System.currentTimeMillis()

    private fun refreshList() {
        container.removeAllViews()

        var expenses = db.getExpenses(getFromTs(), getToTs())

        if (searchQuery.isNotEmpty()) {
            expenses = expenses.filter {
                it.category.lowercase().contains(searchQuery) ||
                it.source.lowercase().contains(searchQuery)
            }
        }

        if (expenses.isEmpty()) {
            container.addView(TextView(this).apply {
                text     = "No transactions found"
                textSize = 14f
                setTextColor(ThemeColors.hint(this))
                gravity  = android.view.Gravity.CENTER
                setPadding(0, 48, 0, 48)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            return
        }

        val dateFmt   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today     = dateFmt.format(Date())
        val yesterday = dateFmt.format(Date(System.currentTimeMillis() - 86_400_000L))
        val grouped   = expenses.groupBy { dateFmt.format(Date(it.timestamp)) }

        grouped.forEach { (dateKey, txns) ->
            val headerText = when (dateKey) {
                today     -> "TODAY"
                yesterday -> "YESTERDAY"
                else -> {
                    val parsed = dateFmt.parse(dateKey)
                    SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                        .format(parsed ?: Date()).uppercase()
                }
            }

            container.addView(TextView(this).apply {
                text     = headerText
                textSize = 12f
                setTextColor(ThemeColors.hint(this))
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 16, 0, 8)
            })

            txns.forEach { expense -> addTransactionRow(expense) }
        }
    }

    private fun addTransactionRow(expense: Expense) {
        val cat      = try { CategoryManager.getCategoryByName(expense.category) } catch (e: Exception) { null }
        val colorHex = cat?.colorHex ?: "#F0F2F5"
        val sdf      = SimpleDateFormat("hh:mm a", Locale.getDefault())

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL
            background  = getDrawable(R.drawable.bg_normal_row)
            setPadding(12, 14, 12, 14)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
        }

        val iconBg = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(48, 48).apply { setMargins(0, 0, 14, 0) }
        }
        CategoryIcon.applyIcon(iconBg, expense.category,
            cat?.colorHex ?: "#E2E8F0",
            cat?.iconTint ?: "#475569"
        )

        val info = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(TextView(this).apply {
            text = expense.category; textSize = 15f
            setTextColor(ThemeColors.primary(this))
            typeface = Typeface.DEFAULT_BOLD
        })
        info.addView(TextView(this).apply {
            text = sdf.format(Date(expense.timestamp)); textSize = 12f
            setTextColor(ThemeColors.hint(this))
        })

        val amt = TextView(this).apply {
            text = if (expense.amount > 0)
                "$currency${String.format("%.0f", expense.amount)}" else "$currency-"
            textSize = 15f
            setTextColor(ThemeColors.amountNeg(this))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 8, 0) }
        }

        val btnEdit = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(28, 28).apply { setMargins(0, 0, 6, 0) }
            setImageResource(R.drawable.ic_edit)
        }
        btnEdit.setOnClickListener { showEditCategorySheet(expense) }

        val btnDelete = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(28, 28)
            setImageResource(R.drawable.ic_delete)
        }
        btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage(getString(R.string.delete_transaction_confirmation))
                .setPositiveButton("Delete") { _, _ ->
                    db.deleteExpense(expense.id); refreshList()
                }
                .setNegativeButton("Cancel", null)
                .create().apply {
                    setOnShowListener {
                        getButton(AlertDialog.BUTTON_POSITIVE)
                            .setTextColor(ThemeColors.amountNeg(this@TransactionsActivity))
                    }
                }.show()
        }

        row.addView(iconBg); row.addView(info)
        row.addView(amt); row.addView(btnEdit); row.addView(btnDelete)
        container.addView(row)
    }

    private fun showEditCategorySheet(expense: Expense) {
        val categories = CategoryManager.activeCategories
        AlertDialog.Builder(this)
            .setTitle("Re-assign Category")
            .setItems(categories.map { it.name }.toTypedArray()) { _, which ->
                db.updateExpenseCategory(expense.id, categories[which].name)
                refreshList()
            }.show()
    }


    private fun setupBottomNav() {
        listOf(
            Triple(R.id.navHomePill, R.id.navHomeIcon, R.id.navHomeLabel),
            Triple(R.id.navTransactionsPill, R.id.navTransactionsIcon, R.id.navTransactionsLabel),
            Triple(R.id.navCategoryPill, R.id.navCategoryIcon, R.id.navCategoryLabel),
            Triple(R.id.navSettingsPill, R.id.navSettingsIcon, R.id.navSettingsLabel)
        ).forEach { (p, i, l) ->
            findViewById<LinearLayout>(p).background = null
            findViewById<ImageView>(i).apply { alpha = 0.5f; clearColorFilter() }
            findViewById<TextView>(l).setTextColor(ThemeColors.hint(this))
        }

        // Highlight Transactions if root nav
        if (!isStackEntry) {
            findViewById<LinearLayout>(R.id.navTransactionsPill)
                .setBackgroundResource(R.drawable.bg_nav_active_pill)
            findViewById<ImageView>(R.id.navTransactionsIcon).apply {
                alpha = 1f; setColorFilter(ThemeColors.brand(this))
            }
            findViewById<TextView>(R.id.navTransactionsLabel)
                .setTextColor(ThemeColors.brand(this))
        }

        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java)); finish()
        }
        findViewById<LinearLayout>(R.id.navTransactions).setOnClickListener {}
        findViewById<LinearLayout>(R.id.navCategory).setOnClickListener {
            startActivity(Intent(this, ManageCategoriesActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
}
