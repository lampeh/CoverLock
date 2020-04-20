package org.openchaos.android.coverlock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager
import org.openchaos.android.coverlock.service.CoverLockService


class BootReceiver : BroadcastReceiver() {
    private val TAG = this.javaClass.simpleName

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive()")

        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            Log.e(TAG, "called with invalid intent")
            return
        }

        if (PreferenceManager.getDefaultSharedPreferences(context.applicationContext).getBoolean("StartOnBoot", false)) {
            Log.d(TAG, "starting service")
            context.startForegroundService(Intent(context.applicationContext, CoverLockService::class.java))
        }
    }
}
