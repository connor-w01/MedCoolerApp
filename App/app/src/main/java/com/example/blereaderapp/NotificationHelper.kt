package com.example.blereaderapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "temperature_alert_channel"
        const val NOTIFICATION_ID = 1
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Temperature Alerts"
            val descriptionText = "Alerts when temperature exceeds safe limits"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showTemperatureAlert(currentTemp: Float, isCelsius: Boolean, shouldAlert: Boolean) {
        val displayTemp = if (isCelsius) currentTemp else (currentTemp * 9f / 5f) + 32f
        val unit = if (isCelsius) "°C" else "°F"
        val tempString = "%.1f$unit".format(displayTemp)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("High Temperature Warning!")
            .setContentText("Temperature is $tempString")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true) // Prevents repeat alerts for updates

        if (shouldAlert) {
            // Only vibrate/sound for new alerts
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }
}