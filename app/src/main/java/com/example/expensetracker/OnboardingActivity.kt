package com.example.expensetracker

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class OnboardingActivity : AppCompatActivity() {

    enum class PermState { IDLE, GRANTED, DENIED }

    private var notifState   = PermState.IDLE
    private var overlayState = PermState.IDLE
    private var currentStep  = 1

    private var waitingForNotif   = false
    private var waitingForOverlay = false

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var stepNotification    : LinearLayout
    private lateinit var imgNotifIcon        : android.widget.ImageView
    private lateinit var imgNotifStatus      : android.widget.ImageView
    private lateinit var notifStatusBox      : LinearLayout
    private lateinit var tvNotifStatusMsg    : TextView
    private lateinit var btnGrantNotification: Button

    private lateinit var stepOverlay         : LinearLayout
    private lateinit var imgOverlayIcon      : android.widget.ImageView
    private lateinit var imgOverlayStatus    : android.widget.ImageView
    private lateinit var overlayStatusBox    : LinearLayout
    private lateinit var tvOverlayStatusMsg  : TextView
    private lateinit var btnGrantOverlay     : Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If both permissions granted AND onboarded → go straight to main
        if (isNotifGranted() && isOverlayGranted() && AppPreferences.isOnboarded(this)) {
            goToMain(); return
        }

        // If both permissions granted but NOT onboarded → go to username
        if (isNotifGranted() && isOverlayGranted() && !AppPreferences.isOnboarded(this)) {
            goToUsername(); return
        }

        setContentView(R.layout.activity_onboarding)
        bindViews()
        setupListeners()
        showStep(1, animate = false)
    }

    override fun onResume() {
        super.onResume()

        if (waitingForNotif) {
            waitingForNotif = false
            if (isNotifGranted()) handleNotifGranted()
            else handleNotifDenied()
        }

        if (waitingForOverlay) {
            waitingForOverlay = false
            if (isOverlayGranted()) handleOverlayGranted()
            else handleOverlayDenied()
        }
    }

    private fun bindViews() {
        stepNotification     = findViewById(R.id.stepNotification)
        imgNotifIcon         = findViewById(R.id.imgNotifIcon)
        imgNotifStatus       = findViewById(R.id.imgNotifStatus)
        notifStatusBox       = findViewById(R.id.notifStatusBox)
        tvNotifStatusMsg     = findViewById(R.id.tvNotifStatusMsg)
        btnGrantNotification = findViewById(R.id.btnGrantNotification)

        stepOverlay          = findViewById(R.id.stepOverlay)
        imgOverlayIcon       = findViewById(R.id.imgOverlayIcon)
        imgOverlayStatus     = findViewById(R.id.imgOverlayStatus)
        overlayStatusBox     = findViewById(R.id.overlayStatusBox)
        tvOverlayStatusMsg   = findViewById(R.id.tvOverlayStatusMsg)
        btnGrantOverlay      = findViewById(R.id.btnGrantOverlay)
    }

    private fun setupListeners() {
        btnGrantNotification.setOnClickListener {
            waitingForNotif = true
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        btnGrantOverlay.setOnClickListener {
            waitingForOverlay = true
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        }
    }

    private fun handleNotifGranted() {
        notifState = PermState.GRANTED
        showStatusIcon(imgNotifIcon, imgNotifStatus, true)
        showStatusBox(notifStatusBox, tvNotifStatusMsg, "✓  Access given", true)
        btnGrantNotification.text      = "Granted ✓"
        btnGrantNotification.isEnabled = false
        btnGrantNotification.alpha     = 0.6f
        handler.postDelayed({ showStep(2, animate = true) }, 1500)
    }

    private fun handleNotifDenied() {
        notifState = PermState.DENIED
        showStatusIcon(imgNotifIcon, imgNotifStatus, false)
        showStatusBox(notifStatusBox, tvNotifStatusMsg,
            "Sorry, we cannot work without the Notification permission.", false)
        btnGrantNotification.text = "Try Again"
    }

    private fun handleOverlayGranted() {
        overlayState = PermState.GRANTED
        showStatusIcon(imgOverlayIcon, imgOverlayStatus, true)
        showStatusBox(overlayStatusBox, tvOverlayStatusMsg, "✓  Access given", true)
        btnGrantOverlay.text      = "Granted ✓"
        btnGrantOverlay.isEnabled = false
        btnGrantOverlay.alpha     = 0.6f
        // After 1.5s → go to username
        handler.postDelayed({ goToUsername() }, 1500)
    }

    private fun handleOverlayDenied() {
        overlayState = PermState.DENIED
        showStatusIcon(imgOverlayIcon, imgOverlayStatus, false)
        showStatusBox(overlayStatusBox, tvOverlayStatusMsg,
            "Sorry, we cannot work without the Overlay permission.", false)
        btnGrantOverlay.text = "Try Again"
    }

    private fun goToUsername() {
        startActivity(
            Intent(this, UsernameActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    private fun goToMain() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    private fun showStep(step: Int, animate: Boolean) {
        currentStep = step
        if (step == 1) {
            stepNotification.visibility = View.VISIBLE
            stepOverlay.visibility      = View.GONE
        } else {
            if (animate) {
                val out = AnimationUtils.loadAnimation(this, R.anim.slide_out_left)
                stepNotification.startAnimation(out)
                handler.postDelayed({
                    stepNotification.visibility = View.GONE
                    stepOverlay.visibility      = View.VISIBLE
                    val inAnim = AnimationUtils.loadAnimation(this, R.anim.slide_in_right)
                    stepOverlay.startAnimation(inAnim)
                }, 300)
            } else {
                stepNotification.visibility = View.GONE
                stepOverlay.visibility      = View.VISIBLE
            }
        }
    }

    private fun showStatusIcon(
        main   : android.widget.ImageView,
        status : android.widget.ImageView,
        success: Boolean
    ) {
        main.visibility = View.GONE
        status.setImageResource(if (success) R.drawable.ic_tick else R.drawable.ic_sad)
        status.visibility = View.VISIBLE
        status.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_in))
    }

    private fun showStatusBox(
        box    : LinearLayout,
        msg    : TextView,
        message: String,
        success: Boolean
    ) {
        msg.text = message
        msg.setTextColor(
            if (success) Color.parseColor("#2D6A4F")
            else         Color.parseColor("#EF4444")
        )
        box.setBackgroundResource(
            if (success) R.drawable.bg_status_success
            else         R.drawable.bg_status_error
        )
        box.visibility = View.VISIBLE
        box.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
    }

    private fun isNotifGranted(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        )
        return flat?.contains(packageName) == true
    }

    private fun isOverlayGranted() = Settings.canDrawOverlays(this)
}
