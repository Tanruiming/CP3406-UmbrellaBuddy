package au.edu.jcu.assessment.utilityapp.notifications

import android.app.NotificationManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import au.edu.jcu.assessment.utilityapp.MainActivity
import au.edu.jcu.assessment.utilityapp.domain.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

// Explicit opt-in OS delivery check with a controlled rainy forecast; not a live weather claim.
@RunWith(AndroidJUnit4::class)
class NotificationDeliveryTest {
    @Test fun postsDeduplicatesOpensAndCancelsReminderAfterUserPermission() {
        assumeTrue("Opt-in notification delivery verification",
            InstrumentationRegistry.getArguments().getString("notificationDelivery") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val notifier = AndroidRainNotifier(context)
        assertTrue("The device user must allow notifications first", notifier.canNotify())
        val manager = context.getSystemService(NotificationManager::class.java)
        val now = Instant.now()
        val report = WeatherReport(Coordinates(1.315873, 103.875797),
            WeatherSnapshot(ZoneId.of("Asia/Singapore"), now, 28.0, 30.0, 61,
                listOf(HourlyRainForecast(now.minusSeconds(1800), now.plusSeconds(1800), 80))),
            null, RegionalForecastStatus.UNAVAILABLE, now)
        notifier.cancel()
        try {
            assertTrue(notifier.showRainReminder(report))
            val deadline = SystemClock.elapsedRealtime() + 3000
            while (manager.activeNotifications.none { it.id == AndroidRainNotifier.NOTIFICATION_ID } &&
                SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            val posted = manager.activeNotifications.single { it.id == AndroidRainNotifier.NOTIFICATION_ID }.notification
            assertEquals("Bring an umbrella", posted.extras.getString("android.title"))
            assertNotNull(posted.smallIcon)
            assertNotNull(posted.contentIntent)
            assertFalse("Same forecast must not alert twice", notifier.showRainReminder(report))
            val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
            try {
                posted.contentIntent.send()
                val opened = monitor.waitForActivityWithTimeout(5000)
                assertNotNull("Notification tap should open the app", opened)
                instrumentation.runOnMainSync { opened?.finish() }
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        } finally {
            notifier.cancel()
        }
        val deadline = SystemClock.elapsedRealtime() + 3000
        while (manager.activeNotifications.any { it.id == AndroidRainNotifier.NOTIFICATION_ID } &&
            SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertFalse(manager.activeNotifications.any { it.id == AndroidRainNotifier.NOTIFICATION_ID })
    }
}
