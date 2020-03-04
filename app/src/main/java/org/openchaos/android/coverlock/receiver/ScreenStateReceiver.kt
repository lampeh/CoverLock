package org.openchaos.android.coverlock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager
import org.openchaos.android.coverlock.service.CoverLockService

class ScreenStateReceiver : BroadcastReceiver() {
    private val TAG = this.javaClass.simpleName

    // ACTION_SCREEN_ON/OFF report "interactive" state, not display state. close enough

    // TODO: signal state to service via binder
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive()")

        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val serviceIntent = Intent(context.applicationContext, CoverLockService::class.java)

        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                if (CoverLockService.isRunning && !prefs.getBoolean("ActionWake", false)) {
                    context.stopService(serviceIntent)
                } else if (!CoverLockService.isRunning && prefs.getBoolean("ActionWake", false)) {
                    context.startForegroundService(serviceIntent)
                }
            }

            Intent.ACTION_SCREEN_ON -> {
                if (CoverLockService.isRunning && !prefs.getBoolean("ActionLock", false)) {
                    context.stopService(serviceIntent)
                } else if (!CoverLockService.isRunning && prefs.getBoolean("ActionLock", false)) {
                    context.startForegroundService(serviceIntent)
                }
            }

            else -> {
                Log.e(TAG, "Unknown intent")
            }
        }
    }
}
