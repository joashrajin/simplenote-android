package com.automattic.simplenote

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.preference.Preference
import com.automattic.simplenote.utils.DisplayUtils.disableScreenshotsIfLocked
import com.automattic.simplenote.utils.SystemBarUtils
import dagger.hilt.android.AndroidEntryPoint
import org.wordpress.passcodelock.PasscodePreferenceFragment
import org.wordpress.passcodelock.PasscodePreferenceFragmentCompat

@AndroidEntryPoint
class PreferencesActivity : ThemedAppCompatActivity() {
    private lateinit var passcodePreferenceFragment: PasscodePreferenceFragmentCompat
    private lateinit var preferencesFragment: PreferencesFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_preferences)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        setTitle(R.string.settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val preferencesTag = "tag_preferences"
        val passcodeTag = "tag_passcode"
        if (savedInstanceState == null) {
            val passcodeArgs = Bundle()
            passcodeArgs.putBoolean(PasscodePreferenceFragment.KEY_SHOULD_INFLATE, false)
            passcodePreferenceFragment = PasscodePreferenceFragmentCompat()
            passcodePreferenceFragment.arguments = passcodeArgs

            preferencesFragment = PreferencesFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.preferences_container, preferencesFragment, preferencesTag)
                .add(R.id.preferences_container, passcodePreferenceFragment, passcodeTag)
                .commit()
        } else {
            val fragmentManager = supportFragmentManager
            preferencesFragment = fragmentManager.findFragmentByTag(preferencesTag) as PreferencesFragment
            passcodePreferenceFragment =
                fragmentManager.findFragmentByTag(passcodeTag) as PasscodePreferenceFragmentCompat
        }

        // Edge-to-edge comes from ThemedAppCompatActivity; wire this screen's insets.
        SystemBarUtils.applyInsets(
            findViewById<View>(R.id.main_parent_view),
            toolbar,
            findViewById<View>(R.id.preferences_container)
        )
    }

    public override fun onStart() {
        super.onStart()

        val togglePref: Preference? =
            preferencesFragment.findPreference(getString(R.string.pref_key_passcode_toggle))
        val changePref: Preference? =
            preferencesFragment.findPreference(getString(R.string.pref_key_change_passcode))

        if (togglePref != null && changePref != null) {
            passcodePreferenceFragment.setPreferences(togglePref, changePref)
        }
    }

    override fun onResume() {
        super.onResume()
        disableScreenshotsIfLocked(this)
    }
}
