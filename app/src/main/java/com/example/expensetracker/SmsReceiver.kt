package com.example.expensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ExpenseTracker"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val bundle = intent.extras ?: return
        @Suppress("DEPRECATION")
        val pdus   = bundle.get("pdus") as? Array<*> ?: return
        val format = bundle.getString("format")

        for (pdu in pdus) {
            val sms    = SmsMessage.createFromPdu(pdu as ByteArray, format)
            val sender = sms.originatingAddress ?: continue
            val body   = sms.messageBody ?: continue

            Log.d(TAG, "SMS from [$sender]: ${body.take(80)}")

            // Stage 1-3: Shared debit filter
            if (!TransactionFilter.isLegitimateDebit(body)) continue

            // Parse amount
            val amount = AmountParser.parse(body)
            if (amount <= 0.0) {
                Log.d(TAG, "SMS amount unparseable — skipping")
                continue
            }

            // Dedup
            val dedup = TransactionDeduplicator(context)
            if (dedup.isDuplicate(amount, sender, body)) {
                Log.d(TAG, "SMS duplicate — skipping")
                continue
            }

            Log.d(TAG, "SMS payment confirmed — amount=$amount sender=$sender")

            val svc = Intent(context, OverlayService::class.java).apply {
                putExtra("amount",  amount)
                putExtra("source",  sender)
                putExtra("snippet", body.take(80))
                putExtra("channel", "sms")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(svc)
            else
                context.startService(svc)

            break
        }
    }
}
