package dev.dhnam.screentranslator

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.UiModeManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import android.widget.RadioGroup
import android.widget.Toast
import android.widget.TextView
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference

/** 앱 아이콘을 누르면 열리는 설정 화면: 번역 앱 선택, 어시스턴트 상태, 안내. */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var resolver: TargetResolver
    private lateinit var targetsView: LinearLayout
    private lateinit var updateStatus: TextView
    private lateinit var updateAction: Button
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)
        status = findViewById(R.id.status)

        findViewById<Button>(R.id.open_settings).setOnClickListener { openAssistSettings() }

        resolver = TargetResolver(packageManager, packageName)
        targetsView = findViewById(R.id.targets)
        findViewById<Button>(R.id.add_target).setOnClickListener { showAddDialog() }

        updateStatus = findViewById(R.id.update_status)
        updateAction = findViewById(R.id.update_action)

        setupTheme()

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
        if (BuildConfig.UPDATE_ENABLED) {
            current = WeakReference(this)
            if (busy) showBusy() else renderUpdate()
            if (!busy && Version.shouldAutoCheck(System.currentTimeMillis(), prefs.updateCheckedAt)) startCheck()
        } else {
            updateStatus.setText(R.string.update_disabled_dev)
            updateAction.visibility = View.GONE
        }
    }

    override fun onStop() {
        super.onStop()
        // 화면을 벗어나면(회전 포함) 받던 업데이트를 취소한다. 설치 확인 창이 뜰 수 없는 상태에서 세션이 남지 않게 한다.
        if (downloading) cancelDownload = true
    }

    // --- 업데이트 ---
    // 진행 상태는 Activity가 아니라 프로세스에 둔다. 회전으로 새 Activity가 생겨도 중복 실행되지 않게 한다.

    /** 저장된 마지막 결과로 업데이트 영역을 그린다. */
    private fun renderUpdate() {
        val checker = UpdateChecker(this)
        val latest = prefs.latestRelease?.takeIf { Version.isNewer(it.tag, checker.currentVersion()) }
        updateAction.isEnabled = true
        if (latest != null) {
            updateStatus.text = getString(R.string.update_available, latest.tag)
            updateAction.setText(R.string.update_install)
            updateAction.setOnClickListener { startInstall(latest) }
        } else {
            updateStatus.text = getString(R.string.update_latest, checker.currentVersion())
            updateAction.setText(R.string.update_check_now)
            updateAction.setOnClickListener { startCheck() }
        }
    }

    private fun showBusy() {
        updateStatus.setText(if (downloading) R.string.update_downloading_short else R.string.update_checking)
        updateAction.isEnabled = false
    }

    private fun showCheckFailed() {
        updateStatus.setText(R.string.update_failed)
        updateAction.isEnabled = true
        updateAction.setText(R.string.update_retry)
        updateAction.setOnClickListener { startCheck() }
    }

    private fun startCheck() {
        if (busy) return
        busy = true
        showBusy()
        val checker = UpdateChecker(applicationContext)
        Thread {
            val ok = try { checker.check(); true } catch (e: IOException) { false }
            main.post {
                busy = false
                val a = current?.get()?.takeIf { !it.isDestroyed } ?: return@post
                if (ok) a.renderUpdate() else a.showCheckFailed()
            }
        }.start()
    }

    private fun startInstall(info: ReleaseInfo) {
        if (busy) return
        if (!packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.update_permission_title)
                .setMessage(R.string.update_permission_message)
                .setPositiveButton(R.string.update_permission_open) { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        busy = true
        downloading = true
        cancelDownload = false
        updateAction.isEnabled = false
        updateStatus.text = getString(R.string.update_downloading, 0)
        val app = applicationContext
        val installer = UpdateInstaller(app)
        Thread {
            val error: Int? = try {
                var lastPct = -1
                installer.install(info, isCancelled = { cancelDownload }) { pct ->
                    if (pct != lastPct) {
                        lastPct = pct
                        main.post {
                            current?.get()?.takeIf { !it.isDestroyed }?.updateStatus?.text =
                                app.getString(R.string.update_downloading, pct)
                        }
                    }
                }
                null
            } catch (e: IOException) {
                when (e.message) {
                    UpdateInstaller.CANCELLED -> null
                    UpdateInstaller.DIGEST_MISMATCH -> R.string.update_corrupt
                    else -> R.string.update_failed
                }
            }
            main.post {
                busy = false
                downloading = false
                if (error != null) Toast.makeText(app, error, Toast.LENGTH_LONG).show()
                current?.get()?.takeIf { !it.isDestroyed }?.renderUpdate()
            }
        }.start()
    }

    private companion object {
        @Volatile var busy = false
        @Volatile var downloading = false
        @Volatile var cancelDownload = false
        var current: WeakReference<SettingsActivity>? = null
    }


    /** 테마 선택. 앱별 다크 모드는 Android 12(API 31)부터라 그 아래에서는 숨기고 시스템을 따른다. */
    private fun setupTheme() {
        val section = findViewById<View>(R.id.theme_section)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            section.visibility = View.GONE
            return
        }
        val ids = mapOf(ThemeMode.SYSTEM to R.id.theme_system, ThemeMode.LIGHT to R.id.theme_light, ThemeMode.DARK to R.id.theme_dark)
        val group = findViewById<RadioGroup>(R.id.theme_group)
        group.check(ids.getValue(prefs.themeMode))
        group.setOnCheckedChangeListener { _, id ->
            val mode = ids.entries.first { it.value == id }.key
            if (mode == prefs.themeMode) return@setOnCheckedChangeListener
            prefs.themeMode = mode
            // 시스템이 앱별로 기억하고, 바뀌면 화면을 다시 만든다.
            getSystemService(UiModeManager::class.java).setApplicationNightMode(mode.nightMode)
        }
    }

    /** 테마 글자색. 기본 아이콘을 어두운 배경에서도 보이게 칠할 때 쓴다. */
    private fun textColor(): ColorStateList? {
        val a = obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
        return try { a.getColorStateList(0) } finally { a.recycle() }
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
                imageTintList = textColor()
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
