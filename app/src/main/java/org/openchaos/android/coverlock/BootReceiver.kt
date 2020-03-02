package org.openchaos.android.coverlock

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

        // TODO: context or context.applicationContext?
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("StartOnBoot", false)) {
            context.startForegroundService(Intent(context, CoverLockService::class.java))
        }
    }
}
