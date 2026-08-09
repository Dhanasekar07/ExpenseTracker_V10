package com.example.expensetracker

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class ManageCategoriesActivity : AppCompatActivity() {

    private lateinit var previewContainer: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CategoryAdapter
    private var touchHelper: ItemTouchHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_categories)

        try { CategoryManager.initialize(this) } catch (e: Exception) { e.printStackTrace() }

        previewContainer = findViewById(R.id.previewContainer)
        recyclerView     = findViewById(R.id.recyclerView)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnArchive).setOnClickListener {
            startActivity(Intent(this, ArchivedCategoriesActivity::class.java))
        }
        findViewById<TextView>(R.id.btnAddNew).setOnClickListener { showAddCategoryDialog() }

        setupRecyclerView()
        setupBottomNav()
        refreshUI()
    }

    override fun onResume() {
        super.onResume()
        try { CategoryManager.initialize(this); refreshUI() } catch (e: Exception) { e.printStackTrace() }
    }

    private fun setupRecyclerView() {
        adapter = CategoryAdapter(
            mutableListOf(),
            onArchive = { cat ->
                CategoryManager.archiveCategory(this, cat.id)
                refreshUI()
                Toast.makeText(this, "${cat.name} archived", Toast.LENGTH_SHORT).show()
            },
            onRename = { cat -> showRenameDialog(cat.id, cat.name) },
            onDelete = { cat -> showDeleteConfirmation(cat.id, cat.name) },
            onStartDrag = { holder -> touchHelper?.startDrag(holder) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                val fromPos = from.adapterPosition
                val toPos = to.adapterPosition
                adapter.moveItem(fromPos, toPos)
                return true
            }
            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {}
            override fun clearView(rv: RecyclerView, holder: RecyclerView.ViewHolder) {
                super.clearView(rv, holder)
                // Persist sort order
                adapter.getItems().forEachIndexed { index, cat ->
                    CategoryDbHelper(this@ManageCategoriesActivity).updateSortOrder(cat.id, index)
                }
                CategoryManager.initialize(this@ManageCategoriesActivity)
                buildPreview()
            }
        }
        touchHelper = ItemTouchHelper(callback)
        touchHelper!!.attachToRecyclerView(recyclerView)
    }

    private fun refreshUI() {
        val active = CategoryManager.activeCategories.toMutableList()
        adapter.updateItems(active)
        buildPreview()
    }

    private fun buildPreview() {
        previewContainer.removeAllViews()
        previewContainer.orientation = LinearLayout.VERTICAL
        val maxPopup = AppPreferences.getPopupMaxCategories(this)
        val cats = CategoryManager.activeCategories.take(maxPopup)

        val chipsPerRow = 3
        var currentRow: LinearLayout? = null

        cats.forEachIndexed { index, cat ->
            if (index % chipsPerRow == 0) {
                currentRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { if (index > 0) topMargin = dp(6f).toInt() }
                }
                previewContainer.addView(currentRow)
            }

            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(20f)
                    setColor(Color.parseColor("#e3e8ff"))
                }
                setPadding(dp(12f).toInt(), dp(8f).toInt(), dp(14f).toInt(), dp(8f).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(4f).toInt(), 0, dp(4f).toInt(), 0) }
            }
            chip.addView(CategoryIcon.createChipIcon(this, cat.name, Color.parseColor("#141930"), 16))
            chip.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(4f).toInt(), 1)
            })
            chip.addView(TextView(this).apply {
                text = if (cat.name.length > 9) cat.name.take(8) + ".." else cat.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(Color.parseColor("#666b81"))
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            })
            currentRow?.addView(chip)
        }
    }

    private fun showDeleteConfirmation(id: String, name: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete \"$name\"?")
            .setMessage(getString(R.string.delete_confirmation))
            .setPositiveButton("Delete") { _, _ ->
                CategoryManager.deleteCategory(this, id)
                refreshUI()
            }
            .setNegativeButton("Cancel", null)
            .create().apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(ThemeColors.amountNeg(this@ManageCategoriesActivity))
                }
            }.show()
    }

    private fun showRenameDialog(id: String, currentName: String) {
        val input = EditText(this).apply {
            setText(currentName)
            setPadding(32, 16, 32, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("Rename Category")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    CategoryManager.renameCategory(this, id, newName)
                    refreshUI()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddCategoryDialog() {
        val input = EditText(this).apply {
            hint = "Category Name"
            setPadding(32, 16, 32, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("Add New Category")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty() && CategoryManager.allCategories.size < CategoryManager.MAX_CATEGORIES) {
                    val colors = CategoryManager.getNextCustomColor(CategoryManager.allCategories)
                    val newCat = Category(
                        id         = "custom_${System.currentTimeMillis()}",
                        name       = name,
                        iconName   = "custom",
                        colorHex   = colors.first,
                        chartColor = colors.second,
                        iconTint   = colors.third,
                        isActive   = true,
                        sortOrder  = CategoryManager.allCategories.size
                    )
                    CategoryDbHelper(this).insertCategory(newCat)
                    CategoryManager.initialize(this)
                    refreshUI()
                } else if (CategoryManager.allCategories.size >= CategoryManager.MAX_CATEGORIES) {
                    Toast.makeText(this, "Maximum ${CategoryManager.MAX_CATEGORIES} categories allowed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        findViewById<LinearLayout>(R.id.navCategoryPill).setBackgroundResource(R.drawable.bg_nav_active_pill)
        findViewById<ImageView>(R.id.navCategoryIcon).apply { alpha = 1f; setColorFilter(ThemeColors.brand(this)) }
        findViewById<TextView>(R.id.navCategoryLabel).setTextColor(ThemeColors.brand(this))

        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java)); finish()
        }
        findViewById<LinearLayout>(R.id.navTransactions).setOnClickListener {
            startActivity(Intent(this, TransactionsActivity::class.java)); finish()
        }
        findViewById<LinearLayout>(R.id.navCategory).setOnClickListener { }
        findViewById<LinearLayout>(R.id.navSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java)); finish()
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    // ── RecyclerView Adapter ─────────────────────────────────────────────
    inner class CategoryAdapter(
        private val items: MutableList<Category>,
        private val onArchive: (Category) -> Unit,
        private val onRename: (Category) -> Unit,
        private val onDelete: (Category) -> Unit,
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val dragHandle  = view.findViewById<ImageView>(R.id.imgDrag)
            val iconBg      = view.findViewById<FrameLayout>(R.id.iconContainer)
            val tvName      = view.findViewById<TextView>(R.id.tvCategoryName)
            val btnMinus    = view.findViewById<ImageView>(R.id.btnMinus)
            val btnEdit     = view.findViewById<ImageView>(R.id.btnEdit)
            val btnDelete   = view.findViewById<ImageView>(R.id.btnDelete)
        }

        fun updateItems(newItems: List<Category>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        fun moveItem(from: Int, to: Int) {
            Collections.swap(items, from, to)
            notifyItemMoved(from, to)
        }

        fun getItems(): List<Category> = items

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = layoutInflater.inflate(R.layout.item_category_row, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val cat = items[position]
            val maxPopup = AppPreferences.getPopupMaxCategories(this@ManageCategoriesActivity)

            // Green highlight for top N categories
            if (position < maxPopup) {
                holder.itemView.setBackgroundResource(R.drawable.bg_top4_highlight)
            } else {
                holder.itemView.setBackgroundResource(R.drawable.bg_normal_row)
            }

            CategoryIcon.applyIcon(holder.iconBg, cat.name, cat.colorHex, cat.iconTint)
            holder.tvName.text = cat.name

            // Minus shows as + for inactive (inventory items)
            holder.btnMinus.setImageResource(R.drawable.ic_minus)

            holder.btnMinus.setOnClickListener { onArchive(cat) }
            holder.btnEdit.setOnClickListener { onRename(cat) }
            holder.btnDelete.setOnClickListener { onDelete(cat) }

            // Drag handle
            holder.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(holder)
                }
                false
            }
        }

        override fun getItemCount() = items.size
    }
}
