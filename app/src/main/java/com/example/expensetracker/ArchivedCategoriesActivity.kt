package com.example.expensetracker

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ArchivedCategoriesActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_archived_categories)

        listContainer = findViewById(R.id.archivedListContainer)
        CategoryManager.initialize(this)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        buildList()
    }

    override fun onResume() { super.onResume(); buildList() }

    private fun buildList() {
        listContainer.removeAllViews()
        CategoryManager.archivedCategories.forEach { cat ->
            val row = layoutInflater.inflate(R.layout.item_archived_row, null)

            val iconBg = row.findViewById<android.widget.FrameLayout>(R.id.iconContainer)
            CategoryIcon.applyIcon(iconBg, cat.name, cat.colorHex, cat.iconTint)

            row.findViewById<TextView>(R.id.tvCategoryName).text = cat.name

            row.findViewById<ImageView>(R.id.btnActivate).setOnClickListener {
                CategoryManager.activateCategory(this, cat.id)
                buildList()
                Toast.makeText(this, "${cat.name} added to active", Toast.LENGTH_SHORT).show()
            }

            row.findViewById<ImageView>(R.id.btnEdit).setOnClickListener {
                showRenameDialog(cat.id, cat.name)
            }

            row.findViewById<ImageView>(R.id.btnDelete).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Delete Category")
                    .setMessage(getString(R.string.delete_confirmation))
                    .setPositiveButton("Delete") { _, _ ->
                        CategoryManager.deleteCategory(this, cat.id)
                        buildList()
                    }
                    .setNegativeButton("Cancel", null)
                    .create().apply {
                        setOnShowListener {
                            getButton(AlertDialog.BUTTON_POSITIVE)
                                .setTextColor(ThemeColors.amountNeg(this@ArchivedCategoriesActivity))
                        }
                    }.show()
            }

            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }

            listContainer.addView(row)
        }

        // Storage management card at bottom
        if (CategoryManager.archivedCategories.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "No archived categories"
                textSize = 14f
                setTextColor(ThemeColors.hint(this))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 48, 0, 48)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    private fun showRenameDialog(id: String, currentName: String) {
        val input = EditText(this).apply {
            setText(currentName); selectAll()
            setPadding(32, 16, 32, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("Rename Category")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    CategoryManager.renameCategory(this, id, newName)
                    buildList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
