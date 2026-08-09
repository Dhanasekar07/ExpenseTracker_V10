package com.example.expensetracker

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class PaymentNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "ExpenseTracker"

        private val SMS_APPS = setOf(
            "com.truecaller.android","com.android.mms","com.android.messaging",
            "com.samsung.android.messaging","com.oneplus.mms","com.oneplus.message",
            "com.miui.sms","com.coloros.mms","com.vivo.mms","com.realme.mms",
            "com.asus.message","com.nokia.messaging","com.motorola.messaging",
            "com.textra","com.handcent.nextsms","com.moez.QKSMS",
            "com.klinker.android.evolve_sms","com.dice.truemessenger","com.google.android.apps.messaging"
        )

        private val PAYMENT_APPS = setOf(
            "com.google.android.apps.nbu.paisa.user","com.google.android.apps.walletnfcrel",
            "com.phonepe.app","net.one97.paytm","in.org.npci.upiapp",
            "in.amazon.mShop.android.shopping","com.csam.icici.bank.imobile",
            "com.sbi.lotusintouch","com.snapwork.hdfc","com.axis.mobile",
            "com.msf.kbank.mobile","com.fss.indianbankMobile","com.IndianBank.MobileBanking",
            "com.jio.jiopay","com.freecharge.android","com.mobikwik_new"
        )

        private val IGNORE_PACKAGES = setOf(
            "com.android.systemui","com.whatsapp","com.whatsapp.w4b",
            "com.facebook.katana","com.facebook.orca","com.instagram.android",
            "com.twitter.android","com.snapchat.android","com.linkedin.android",
            "com.spotify.music","com.netflix.mediaclient","com.google.android.youtube",
            "com.swiggy.android","com.zomato.android","com.flipkart.android",
            // Gmail and Messages — too noisy, real payment SMS handled by SmsReceiver
            "com.google.android.gm",
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        if (pkg in IGNORE_PACKAGES) {
            Log.d(TAG, "Notification from $pkg — ignored package")
            return
        }

        val extras  = sbn.notification?.extras ?: return
        val title   = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()    ?: ""
        val text    = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()     ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val fullOriginal = listOf(title, text, bigText).filter { it.isNotBlank() }.joinToString(" ")

        // Quick check: is this a payment/SMS app with relevant content?
        val isSmsPkg     = pkg in SMS_APPS
        val isPaymentPkg = pkg in PAYMENT_APPS
        val fullLow      = fullOriginal.lowercase()

        // For non-payment, non-SMS apps: require banking keywords to avoid noise
        val hasBankKeyword = listOf("a/c","ac no","acct","avl bal","upi","neft","imps","rtgs","your account","your a/c")
            .any { fullLow.contains(it) }

        if (!isSmsPkg && !isPaymentPkg && !hasBankKeyword) {
            Log.d(TAG, "Notification from $pkg — no payment context, skipping")
            return
        }

        // Stage 1-3: Shared debit filter
        if (!TransactionFilter.isLegitimateDebit(fullOriginal)) {
            Log.d(TAG, "Notification from $pkg — failed debit filter")
            return
        }

        // Parse amount
        val amount = AmountParser.parse(text).takeIf { it > 0.0 }
            ?: AmountParser.parse(bigText).takeIf { it > 0.0 }
            ?: AmountParser.parse(fullOriginal)

        if (amount <= 0.0) {
            Log.d(TAG, "Notification from $pkg — amount unparseable, skipping")
            return
        }

        Log.d(TAG, "Payment detected: ₹$amount from $pkg")

        // Threshold check
        val minAmount = AppPreferences.getMinAmount(this)
        val maxAmount = AppPreferences.getMaxAmount(this)

        if (amount < minAmount) {
            Log.d(TAG, "Amount ₹$amount below min ₹$minAmount — skipped")
            return
        }
        if (maxAmount > 0 && amount > maxAmount) {
            Log.d(TAG, "Amount ₹$amount above max ₹$maxAmount — skipped")
            return
        }

        // Dedup
        val dedup = TransactionDeduplicator(this)
        if (dedup.isDuplicate(amount, pkg, fullOriginal)) {
            Log.d(TAG, "Duplicate notification — skipping")
            return
        }

        // Fire overlay
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra("amount",  amount)
            putExtra("source",  friendlySource(pkg, title))
            putExtra("snippet", text.ifBlank { title })
            putExtra("channel", if (isSmsPkg) "sms_notification" else "notification")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(intent)
        else
            startService(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed — requesting rebind")
        try {
            requestRebind(ComponentName(this, PaymentNotificationListener::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Rebind failed", e)
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun friendlySource(pkg: String, title: String): String {
        if (pkg in SMS_APPS && title.isNotBlank()) return title
        return when (pkg) {
            "com.google.android.apps.nbu.paisa.user" -> "GPay"
            "com.phonepe.app"   -> "PhonePe"
            "net.one97.paytm"   -> "Paytm"
            "com.snapwork.hdfc" -> "HDFC Bank"
            "com.sbi.lotusintouch" -> "SBI YONO"
            "com.csam.icici.bank.imobile" -> "ICICI Bank"
            "com.axis.mobile"   -> "Axis Bank"
            "com.fss.indianbankMobile" -> "Indian Bank"
            else -> pkg
        }
    }
}
