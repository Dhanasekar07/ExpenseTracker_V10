package com.example.expensetracker

import android.animation.ObjectAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.*

class OverlayService : Service() {

    private var wm          : WindowManager? = null
    private var overlayView : View?          = null
    private var db          : ExpenseDbHelper? = null
    private var timerAnim   : ObjectAnimator? = null

    private var currentAmount  = 0.0
    private var currentSource  = ""
    private var currentChannel = ""

    private val handler  = Handler(Looper.getMainLooper())
    private val autoHide = Runnable { dismissCurrent(); showNext() }

    companion object {
        private const val TAG       = "OverlayService"
        private const val CH_ID     = "expense_overlay"
        private const val FG_NOTIF  = 1001
    }

    data class PendingTxn(val amount: Double, val source: String, val channel: String)
    private val queue = ArrayDeque<PendingTxn>()

    override fun onCreate() {
        super.onCreate()
        db = ExpenseDbHelper(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        goForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val amount  = intent?.getDoubleExtra("amount", 0.0) ?: 0.0
        val source  = intent?.getStringExtra("source") ?: ""
        val channel = intent?.getStringExtra("channel") ?: "unknown"

        if (overlayView != null) {
            queue.addLast(PendingTxn(amount, source, channel))
            Log.d(TAG, "Queued: amount=$amount (queue=${queue.size})")
        } else {
            currentAmount = amount; currentSource = source; currentChannel = channel
            showOverlay()
        }
        return START_STICKY
    }

    override fun onDestroy() { dismissCurrent(); queue.clear(); db?.close(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun showNext() {
        if (queue.isEmpty()) { stopSelf(); return }
        val next = queue.removeFirst()
        currentAmount = next.amount; currentSource = next.source; currentChannel = next.channel
        Log.d(TAG, "Next from queue: amount=$currentAmount (remaining=${queue.size})")
        showOverlay()
    }

    private fun goForeground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CH_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_ID, "Expense Overlay",
                    NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) }
            )
        }
        startForeground(FG_NOTIF,
            Notification.Builder(this, CH_ID)
                .setContentTitle("Expense Tracker Active")
                .setContentText("Monitoring payments...")
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setOngoing(true).build()
        )
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }

        try { CategoryManager.initialize(this) } catch (e: Exception) {
            Log.e(TAG, "CategoryManager init failed", e)
        }

        val sym = try { AppPreferences.getCurrencySymbol(this) } catch (_: Exception) { "₹" }
        val popupMax = try { AppPreferences.getPopupMaxCategories(this) } catch (_: Exception) { 4 }
        val aliveTime = try { AppPreferences.getPopupAliveTime(this) } catch (_: Exception) { 30 }
        val categories = CategoryManager.activeCategories.take(popupMax)

        // Main card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20f)
                setColor(Color.WHITE)
            }
            setPadding(dp(20f).toInt(), dp(16f).toInt(), dp(20f).toInt(), dp(12f).toInt())
            elevation = dp(8f)
        }

        // Header: amount + dismiss
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val amountSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        amountSection.addView(TextView(this).apply {
            text = "PAYMENT DETECTED"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(Color.parseColor("#6B7280"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
        })
        amountSection.addView(TextView(this).apply {
            text = "$sym${String.format("%.2f", currentAmount)}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Color.parseColor("#1A1A2E"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2f).toInt(), 0, 0)
        })
        header.addView(amountSection)

        // Dismiss button
        val dismissBtn = FrameLayout(this).apply {
            val size = dp(34f).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FEE2E2"))
            }
            setOnClickListener { dismissCurrent(); showNext() }
        }
        dismissBtn.addView(TextView(this).apply {
            text = "✕"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#EF4444")); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        })
        header.addView(dismissBtn)
        card.addView(header)

        // Chips — max 3 per row, center-aligned
        val chipBg = "#e3e8ff"; val chipIcon = "#141930"; val chipText = "#666b81"
        val chipList = if (categories.isEmpty()) {
            listOf("Food", "Shopping", "Grocery")
        } else {
            categories.map { it.name }
        }

        val rows = chipList.chunked(3)
        rows.forEach { rowItems ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10f).toInt() }
            }
            rowItems.forEach { name ->
                addChip(rowLayout, name, chipBg, chipIcon, chipText)
            }
            card.addView(rowLayout)
        }

        // Timer bar (6dp, animated)
        val timerTrack = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(6f).toInt()
            ).apply { topMargin = dp(12f).toInt() }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3f)
                setColor(Color.parseColor("#F3F4F6"))
            }
        }

        val timerBar = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3f)
                setColor(Color.parseColor("#2D6A4F"))
            }
        }
        timerTrack.addView(timerBar)

        // Only show timer if not "Never"
        if (aliveTime > 0) {
            card.addView(timerTrack)
        }

        overlayView = card
        Log.d(TAG, "Showing overlay: amount=$currentAmount source=$currentSource")

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            dp(370f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(60f).toInt() }

        try { wm?.addView(overlayView, lp) }
        catch (e: Exception) { Log.e(TAG, "Failed to show overlay", e); stopSelf(); return }

        // Timer animation + auto-dismiss
        timerAnim?.cancel()
        handler.removeCallbacks(autoHide)

        if (aliveTime > 0) {
            val timeMs = aliveTime * 1000L
            timerBar.pivotX = 0f
            timerAnim = ObjectAnimator.ofFloat(timerBar, "scaleX", 1f, 0f).apply {
                duration = timeMs
                interpolator = LinearInterpolator()
                start()
            }
            handler.postDelayed(autoHide, timeMs)
        }
        // aliveTime == -1 (Never): no auto-dismiss, only manual X button
    }

    private fun addChip(container: LinearLayout, name: String, bgHex: String, iconHex: String, textHex: String) {
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20f)
                setColor(Color.parseColor(bgHex))
            }
            setPadding(dp(12f).toInt(), dp(8f).toInt(), dp(16f).toInt(), dp(8f).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(4f).toInt(), 0, dp(4f).toInt(), 0) }
            setOnClickListener { logExpense(name) }
        }

        chip.addView(CategoryIcon.createChipIcon(this, name, Color.parseColor(iconHex), 18))
        chip.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(5f).toInt(), 1)
        })
        chip.addView(TextView(this).apply {
            text = if (name.length > 9) name.take(8) + ".." else name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor(textHex))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            maxLines = 1
        })
        container.addView(chip)
    }

    private fun logExpense(category: String) {
        Log.d(TAG, "Category tapped: $category — amount=$currentAmount source=$currentSource channel=$currentChannel")
        db?.insertExpense(category, currentAmount, currentSource, currentChannel)
        val sym = try { AppPreferences.getCurrencySymbol(this) } catch (_: Exception) { "₹" }
        Toast.makeText(this, "✓ $category — $sym${String.format("%.2f", currentAmount)}", Toast.LENGTH_SHORT).show()
        dismissCurrent(); showNext()
    }

    private fun dismissCurrent() {
        Log.d(TAG, "Overlay dismissed")
        timerAnim?.cancel(); timerAnim = null
        handler.removeCallbacks(autoHide)
        overlayView?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        overlayView = null
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
