package org.openchaos.android.coverlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.openchaos.android.coverlock.service.CoverLockService

class BootReceiver : BroadcastReceiver() {
    private val TAG = this.javaClass.simpleName

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "BootReceiver.onReceive()")

        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            Log.e(TAG, "BootReceiver called with invalid intent")
            return
        }

        context.startService(Intent(context, CoverLockService::class.java))
    }
}
