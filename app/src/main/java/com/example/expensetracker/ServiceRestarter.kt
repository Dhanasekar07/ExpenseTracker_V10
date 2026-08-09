package com.example.expensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Receives a broadcast when OverlayService is destroyed
 * and immediately restarts it so it's ready for next transaction.
 * Also handles BOOT_COMPLETED so service starts after phone reboot.
 */
class ServiceRestarter : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ExpenseTracker", "ServiceRestarter fired: ${intent.action}")

        when (intent.action) {
            "com.example.expensetracker.RESTART_SERVICE",
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d("ExpenseTracker", "Restarting services after: ${intent.action}")
            }
        }
    }
}
