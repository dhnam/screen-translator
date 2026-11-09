package dev.dhnam.screentranslator

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.TextView
import java.io.File

/** 앱 아이콘을 누르면 열리는 설정 화면: 번역 앱 선택, 어시스턴트 상태, 안내. */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)
        status = findViewById(R.id.status)

        findViewById<Button>(R.id.open_settings).setOnClickListener { openAssistSettings() }

        val group = findViewById<RadioGroup>(R.id.target)
        group.check(if (prefs.target == Target.GOOGLE) R.id.target_google else R.id.target_papago)
        group.setOnCheckedChangeListener { _, id ->
            prefs.target = if (id == R.id.target_google) Target.GOOGLE else Target.PAPAGO
        }

        findViewById<CheckBox>(R.id.opening_toast).apply {
            isChecked = prefs.showOpeningToast
            setOnCheckedChangeListener { _, checked -> prefs.showOpeningToast = checked }
        }

        val shots = File(cacheDir, "shots")
        Thread { ShotStore(shots).purgeOlderThan(ShotStore.MAX_AGE_MS) }.start()

        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        val held = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)
        status.setText(if (held) R.string.status_set else R.string.status_not_set)
    }

    private fun openAssistSettings() {
        try {
            startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    /** One UI는 알림 권한이 없는 앱의 토스트를 막는다. 어시스턴트 안내 토스트를 위해 요청한다. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
