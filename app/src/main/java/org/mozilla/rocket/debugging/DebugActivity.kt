package org.mozilla.rocket.debugging

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import org.json.JSONArray
import org.mozilla.focus.databinding.ActivityDebugBinding
import org.mozilla.focus.utils.FirebaseHelper
import org.mozilla.focus.utils.Settings
import org.mozilla.rocket.preference.stringLiveData
import java.util.concurrent.TimeUnit

class DebugActivity : AppCompatActivity() {

    private lateinit var preference: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityDebugBinding.inflate(layoutInflater).also { setContentView(it.root) }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        preference = getSharedPreferences(PREF_NAME_DEBUG, Context.MODE_PRIVATE)
        initDebugLocale(binding)
        initDebugMissionReminderNotification(binding)
        initDebugServerPush(binding)
    }

    private fun initDebugServerPush(binding: ActivityDebugBinding) {
        binding.switchDisableServerPush.isChecked = Settings.getInstance(this).isServerPushDebugging
        binding.switchDisableServerPush.setOnCheckedChangeListener { _, isChecked ->
            Settings.getInstance(this).isServerPushDebugging = isChecked
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun initDebugLocale(binding: ActivityDebugBinding) {
        val debugLocales = getDebugLocales()
        getDebugLocaleLiveData().observe(this) {
            binding.debugLocaleText.text = it
        }
        binding.debugLocaleLayout.setOnClickListener {
            showDropDownListDialog(debugLocales)
        }
        binding.debugFirebaseId.setOnClickListener {
            val firebaseId = FirebaseHelper.getFirebase().getInstanceId() ?: "null"
            copyToClipboard("firebaseId", firebaseId)
            Toast.makeText(this, "$firebaseId copied", Toast.LENGTH_LONG).show()
        }
        binding.debugFirebaseRegisterToken.setOnClickListener {
            FirebaseHelper.getFirebase().getRegisterToekn {
                val token = it ?: ""
                copyToClipboard("firebaseRegisterToken", token)
                Toast.makeText(this, "$token copied", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getDebugLocales(): List<String> =
        parseDebugLocalesJson(FirebaseHelper.getFirebase().getRcString(STR_RC_DEBUG_LOCALES))

    private fun getDebugLocaleLiveData(): LiveData<String> =
        preference.stringLiveData(SHARED_PREF_KEY_DEBUG_LOCALE, DEBUG_DEFAULT_LOCALE)

    private fun saveDebugLocale(locale: String) {
        preference.edit().putString(SHARED_PREF_KEY_DEBUG_LOCALE, locale).apply()
    }

    private fun showDropDownListDialog(data: List<String>) {
        val appContext = applicationContext
        val adapter = ArrayAdapter<String>(this, android.R.layout.select_dialog_singlechoice, data)
        AlertDialog.Builder(this)
            .setAdapter(adapter) { _, which ->
                val selectedLocale = data[which]
                FirebaseHelper.setUserProperty(appContext, "debug_locale", selectedLocale)
                saveDebugLocale(selectedLocale)
                refreshAppConfigs()
            }
            .create().show()
    }

    private fun refreshAppConfigs() {
        FirebaseHelper.refreshRemoteConfig { isSuccess, exception ->
            if (isSuccess) {
                Toast.makeText(this, "Refresh successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Refresh failed: $exception", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initDebugMissionReminderNotification(binding: ActivityDebugBinding) {
        binding.debugMissionReminder.setOnClickListener {
            isMissionReminderDebugEnabled = true
            Toast.makeText(this, "Repeat interval has been set to 15 minutes", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        ContextCompat.getSystemService(applicationContext, ClipboardManager::class.java)?.run {
            val clip = ClipData.newPlainText(label, text)
            this.setPrimaryClip(clip)
        }
    }

    companion object {
        private const val STR_RC_DEBUG_LOCALES = "str_debug_locales"

        private const val PREF_NAME_DEBUG = "debug_pref"
        private const val SHARED_PREF_KEY_DEBUG_LOCALE = "shared_pref_key_debug_locale"
        private const val DEBUG_DEFAULT_LOCALE = "Default"

        var isMissionReminderDebugEnabled = false
        val MISSION_REMINDER_DEBUG_REPEAT_INTERVAL = TimeUnit.MINUTES to 15L

        fun getStartIntent(context: Context) = Intent(context, DebugActivity::class.java)
    }
}

private fun parseDebugLocalesJson(jsonStr: String): List<String> {
    if (jsonStr.isEmpty()) {
        return emptyList()
    }
    val jsonArray = JSONArray(jsonStr)
    return (0 until jsonArray.length())
        .map { jsonArray.getJSONObject(it).getString("country") }
}
