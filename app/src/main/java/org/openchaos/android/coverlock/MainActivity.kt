package org.openchaos.android.coverlock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CompoundButton
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import org.openchaos.android.coverlock.service.CoverLockService
import org.openchaos.android.coverlock.service.LockAdmin


class MainActivity : FragmentActivity() {
    private val TAG = this.javaClass.simpleName

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponentName: ComponentName
    private lateinit var serviceIntent: Intent


    fun toggleAdmin(button: View) {
        Log.d(TAG, "toggleAdmin()")

        if ((button as CompoundButton).isChecked) {
            Log.d(TAG, "requesting device admin access")
            startActivityForResult(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName)
            }, 23)
        } else {
            Log.d(TAG, "removing device admin access")
            devicePolicyManager.removeActiveAdmin(adminComponentName)
            stopService(serviceIntent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate()")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponentName = ComponentName(this, LockAdmin::class.java)
        serviceIntent = Intent(this, CoverLockService::class.java)

        val adminActive = devicePolicyManager.isAdminActive(adminComponentName)
        Log.i(TAG, "device admin ${if (adminActive) "en" else "dis"}abled")

        findViewById<CompoundButton>(R.id.btnEnabled).apply {
            isChecked = adminActive
            isEnabled = true
        }

        if (adminActive) {
            startService(serviceIntent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult($requestCode, $resultCode")

        if (requestCode != 23)
            return

        if (resultCode == RESULT_OK) {
            Toast.makeText(this, R.string.adminEnabled, Toast.LENGTH_SHORT).show()
            startService(serviceIntent)
        } else {
            Toast.makeText(this, R.string.adminError, Toast.LENGTH_SHORT).show()
        }
    }
}
