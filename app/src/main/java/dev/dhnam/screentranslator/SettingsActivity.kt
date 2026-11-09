package dev.dhnam.screentranslator

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Toast
import android.widget.TextView
import java.io.File

/** 앱 아이콘을 누르면 열리는 설정 화면: 번역 앱 선택, 어시스턴트 상태, 안내. */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var resolver: TargetResolver
    private lateinit var targetsView: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)
        status = findViewById(R.id.status)

        findViewById<Button>(R.id.open_settings).setOnClickListener { openAssistSettings() }

        resolver = TargetResolver(packageManager, packageName)
        targetsView = findViewById(R.id.targets)
        findViewById<Button>(R.id.add_target).setOnClickListener { showAddDialog() }

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
        renderTargets()
    }

    private fun update(change: (TargetList) -> TargetList) {
        prefs.targetList = change(prefs.targetList)
        renderTargets()
    }

    /** 번역 앱 목록을 다시 그린다: [라디오] [아이콘] 이름 [✕] */
    private fun renderTargets() {
        targetsView.removeAllViews()
        val list = prefs.targetList
        for (flat in list.items) {
            val c = ComponentName.unflattenFromString(flat) ?: continue
            val available = resolver.isAvailable(c)
            val name = resolver.label(c) + if (available) "" else " " + getString(R.string.not_installed)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                alpha = if (available) 1f else 0.4f
                if (available) setOnClickListener { update { it.setDefault(flat) } }
            }
            row.addView(RadioButton(this).apply {
                isChecked = flat == list.default
                isEnabled = available
                isClickable = false
                isFocusable = false
            })
            row.addView(ImageView(this).apply {
                setImageDrawable(resolver.icon(c))
            }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(12) })
            row.addView(TextView(this).apply {
                text = name
                textSize = 16f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                background = null
                contentDescription = getString(R.string.remove_target)
                setOnClickListener { update { it.remove(flat) } }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            targetsView.addView(row)
        }
    }

    /** 이미지를 받을 수 있는 앱 중 목록에 없는 것을 스크롤 선택 창으로 보여 준다. */
    private fun showAddDialog() {
        val existing = prefs.targetList.items.toSet()
        val candidates = resolver.shareTargets()
            .filterNot { it.flattenToString() in existing }
            .map { it to resolver.label(it) }
            .sortedBy { it.second.lowercase() }
        if (candidates.isEmpty()) {
            Toast.makeText(this, R.string.no_more_targets, Toast.LENGTH_SHORT).show()
            return
        }
        val adapter = object : ArrayAdapter<Pair<ComponentName, String>>(this, 0, candidates) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val (c, label) = getItem(position)!!
                val icon = resolver.icon(c)?.mutate()?.apply { setBounds(0, 0, dp(32), dp(32)) }
                return ((convertView as? TextView) ?: TextView(context)).apply {
                    text = label
                    textSize = 16f
                    gravity = Gravity.CENTER_VERTICAL
                    minHeight = dp(56)
                    setPadding(dp(24), 0, dp(24), 0)
                    compoundDrawablePadding = dp(16)
                    setCompoundDrawablesRelative(icon, null, null, null)
                }
            }
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.add_target_title)
            .setAdapter(adapter) { _, which -> update { it.add(candidates[which].first.flattenToString()) } }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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
