package org.openchaos.android.coverlock

import android.os.Bundle
import android.text.InputType
import android.util.Log
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat


@Suppress("unused") // PreferenceFragments referenced in layout XML only
class SettingsFragment : PreferenceFragmentCompat() {
    private val TAG = this.javaClass.simpleName

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(TAG, "onCreatePreferences()")
        setPreferencesFromResource(R.xml.preferences, rootKey)

        listOf("LockDelay", "WakeDelay").forEach {
            findPreference<EditTextPreference>(it)?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
        }
    }
}
