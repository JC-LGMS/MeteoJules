package com.meteojules.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Reçoit l'alarme quotidienne : affiche la notification météo
 * puis reprogramme la prochaine occurrence (jour suivant activé).
 */
class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread {
            try {
                WeatherNotificationScheduler.showNotification(context)
            } catch (_: Exception) {
                // Ne jamais crasher depuis un receiver
            } finally {
                WeatherNotificationScheduler.scheduleNext(context)
                pendingResult.finish()
            }
        }.start()
    }
}

/** Reprogramme la notification après un redémarrage de l'appareil. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            WeatherNotificationScheduler.scheduleNext(context)
        }
    }
}
