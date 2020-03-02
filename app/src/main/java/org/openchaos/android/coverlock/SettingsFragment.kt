package org.openchaos.android.coverlock

import android.os.Bundle
import android.util.Log
import androidx.annotation.Keep
import androidx.preference.PreferenceFragmentCompat

@Keep // PreferenceFragments referenced in layout XML only
@Suppress("unused")
class SettingsFragment : PreferenceFragmentCompat() {
    private val TAG: String = this.javaClass.simpleName

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(TAG, "onCreatePreferences()")
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}
