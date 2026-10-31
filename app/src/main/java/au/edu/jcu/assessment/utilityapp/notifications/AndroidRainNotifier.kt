package au.edu.jcu.assessment.utilityapp.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import au.edu.jcu.assessment.utilityapp.MainActivity
import au.edu.jcu.assessment.utilityapp.R
import au.edu.jcu.assessment.utilityapp.domain.RainNotifier
import au.edu.jcu.assessment.utilityapp.domain.WeatherReport
import kotlin.math.roundToInt

class AndroidRainNotifier(context: Context) : RainNotifier {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(NotificationManager::class.java)
    private var lastForecastKey: String? = null

    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Umbrella reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Rain reminders after you refresh the weather"
        })
    }

    override fun canNotify(): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (manager.getNotificationChannel(CHANNEL_ID)?.importance ?: NotificationManager.IMPORTANCE_NONE) != NotificationManager.IMPORTANCE_NONE

    override fun showRainReminder(report: WeatherReport): Boolean {
        if (!canNotify()) return false
        // Repeated refreshes of the same forecast update must not repeatedly alert.
        val key = "${(report.location.latitude * 100).roundToInt()}:${(report.location.longitude * 100).roundToInt()}:${report.weather.observedAt}:${report.nearbyArea?.updatedAt}"
        if (key == lastForecastKey) return false
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = "The latest forecast suggests carrying an umbrella. Tap to check the weather."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_umbrella_notification)
            .setContentTitle("Bring an umbrella")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setWhen(report.fetchedAt.toEpochMilli())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        return try {
            manager.notify(NOTIFICATION_ID, notification)
            lastForecastKey = key
            true
        } catch (_: SecurityException) {
            false // Permission may have been revoked since canNotify().
        }
    }

    override fun cancel() {
        manager.cancel(NOTIFICATION_ID)
        lastForecastKey = null
    }

    companion object {
        const val CHANNEL_ID = "umbrella_reminders"
        const val NOTIFICATION_ID = 3406
    }
}
