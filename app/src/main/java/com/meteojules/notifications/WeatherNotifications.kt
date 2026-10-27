package com.meteojules.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.meteojules.MainActivity
import com.meteojules.R
import com.meteojules.WeatherRepository
import com.meteojules.model.ClothingAdvice
import com.meteojules.model.WeatherIcons
import com.meteojules.settings.getAdviceImageUri
import com.meteojules.settings.getLastLocation
import com.meteojules.settings.getNotificationDayEnabled
import com.meteojules.settings.getNotificationHour
import com.meteojules.settings.getNotificationMinute
import kotlinx.coroutines.runBlocking
import java.time.ZonedDateTime

/**
 * Réglage d'un jour de notification.
 * day = 1 (lundi) … 7 (dimanche), conformément à java.time.DayOfWeek.value.
 */
data class DayNotification(
    val day: Int,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int
)

/**
 * Planification de la notification météo quotidienne.
 * Chaque jour de la semaine a son horaire ; on programme toujours la prochaine
 * occurrence parmi les jours activés, et on reprogramme après chaque déclenchement.
 */
object WeatherNotificationScheduler {

    const val CHANNEL_ID = "daily_weather"
    private const val REQUEST_CODE = 2001
    private const val NOTIFICATION_ID = 2002

    /** Crée (idempotent) le canal de notification. */
    fun createChannel(context: Context) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Météo quotidienne",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Météo du jour et conseil vestimentaire, à l'horaire de votre choix"
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Programme la prochaine occurrence parmi les jours activés.
     * Si aucun jour n'est activé, annule l'alarme existante.
     */
    fun scheduleNext(context: Context) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context)
        val next = findNextOccurrence(context)
        if (next == null) {
            alarmManager.cancel(pendingIntent)
            return
        }
        val canExact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent)
        } else {
            // Pas de permission d'alarme exacte : alarme inexacte (peut être décalée de quelques minutes)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent)
        }
    }

    /** Annule l'alarme programmée. */
    fun cancel(context: Context) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(context))
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Prochain déclenchement (epoch ms) parmi les jours activés, ou null. */
    private fun findNextOccurrence(context: Context): Long? {
        val now = ZonedDateTime.now()
        for (addDays in 0..7) {
            val date = now.toLocalDate().plusDays(addDays.toLong())
            val day = date.dayOfWeek.value // 1 = lundi … 7 = dimanche
            if (!context.getNotificationDayEnabled(day)) continue
            val scheduled = date
                .atTime(context.getNotificationHour(day), context.getNotificationMinute(day))
                .atZone(now.zone)
            if (scheduled.isAfter(now)) return scheduled.toInstant().toEpochMilli()
        }
        return null
    }

    /** Récupère la météo des dernières coordonnées et affiche la notification. */
    fun showNotification(context: Context) {
        createChannel(context)
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationManager.areNotificationsEnabled()
        ) {
            return
        }

        val (lat, lon) = context.getLastLocation()
        val weather = runBlocking {
            runCatching { WeatherRepository().fetchWeather(lat, lon) }.getOrNull()
        } ?: return
        val current = weather.current_weather ?: return

        val temp = current.temperature
        val advice = ClothingAdvice.getAdvice(temp)
        val icon = WeatherIcons.iconFor(current.weathercode)
        val rainProbability = weather.daily?.precipitation_probability_max?.firstOrNull()

        val text = buildString {
            append(advice)
            if (rainProbability != null && rainProbability >= 40) {
                append("\n🌧️ Pluie probable ($rainProbability %)")
            }
        }

        // Photo personnalisée de la tenue du jour en grande icône (si définie)
        val largeIcon: Bitmap? =
            context.getAdviceImageUri(ClothingAdvice.getCategoryIndex(temp))
                ?.let { decodeSampled(context, it, 256) }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${"%.1f".format(temp)} °C $icon")
            .setContentText(advice)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setLargeIcon(largeIcon)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission POST_NOTIFICATIONS révoquée
        }
    }

    /** Décode une image URI en petit bitmap (pour la grande icône de notification). */
    private fun decodeSampled(context: Context, uri: String, target: Int): Bitmap? = try {
        val source = Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target ||
            bounds.outHeight / (sample * 2) >= target
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    } catch (_: Exception) {
        null
    }
}
