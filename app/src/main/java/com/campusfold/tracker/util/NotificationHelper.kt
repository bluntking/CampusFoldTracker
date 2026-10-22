package com.campusfold.tracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.campusfold.tracker.MainActivity
import com.campusfold.tracker.R
import com.campusfold.tracker.data.TransactionEntity
import java.text.NumberFormat
import java.util.Locale

object NotificationHelper {

    const val CHANNEL_ID = "campus_fold_transactions"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.channel_name)
            val descriptionText = context.getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showTransactionNotification(context: Context, transaction: TransactionEntity) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            transaction.timestamp.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        val formattedAmount = formatter.format(transaction.amount)
        val title = if (transaction.type == "CREDIT") {
            "Money Received: $formattedAmount"
        } else {
            "Expense Tracked: $formattedAmount"
        }
        val content = "${transaction.merchant} (${transaction.category}) via ${transaction.bank}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$content\n\"${transaction.rawBody}\""))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(transaction.timestamp.toInt(), notification)
    }
}
