package dev.dhnam.screentranslator

import android.content.Context

/** 사용자 설정 저장. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /**
     * 번역 앱 목록과 기본 앱.
     * 저장된 목록이 없으면 v0.1.0의 `target` 값을 한 번 이어받고(그 키는 지움), 그것도 없으면 처음 설치 기본값.
     */
    var targetList: TargetList
        get() {
            if (sp.contains(KEY_TARGETS)) {
                return TargetList.decode(sp.getString(KEY_TARGETS, null), sp.getString(KEY_DEFAULT, null))
            }
            if (sp.contains(KEY_OLD_TARGET)) {
                val migrated = TargetList.migrate(sp.getString(KEY_OLD_TARGET, null))
                write(migrated, removeOld = true)
                return migrated
            }
            return TargetList.INITIAL
        }
        set(value) = write(value, removeOld = false)

    /** 번역 앱을 열 때 "여는 중…" 토스트를 띄울지. 기본값은 꺼짐. */
    var showOpeningToast: Boolean
        get() = sp.getBoolean(KEY_OPENING_TOAST, false)
        set(value) { sp.edit().putBoolean(KEY_OPENING_TOAST, value).apply() }

    /** 설정 화면 테마. 저장된 값이 없거나 알 수 없으면 시스템을 따른다. */
    var themeMode: ThemeMode
        get() = sp.getString(KEY_THEME_MODE, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.SYSTEM
        set(value) { sp.edit().putString(KEY_THEME_MODE, value.name).apply() }

    /** 마지막 업데이트 확인 시각(ms). 확인한 적이 없으면 0. */
    var updateCheckedAt: Long
        get() = sp.getLong(KEY_UPDATE_CHECKED_AT, 0L)
        set(value) { sp.edit().putLong(KEY_UPDATE_CHECKED_AT, value).apply() }

    /** 마지막 확인에서 찾은 새 버전. 새 버전이 없었으면 null. */
    var latestRelease: ReleaseInfo?
        get() {
            val tag = sp.getString(KEY_UPDATE_TAG, null) ?: return null
            val url = sp.getString(KEY_UPDATE_URL, null) ?: return null
            return ReleaseInfo(tag, url, sp.getLong(KEY_UPDATE_SIZE, 0L), sp.getString(KEY_UPDATE_SHA256, null))
        }
        set(value) {
            sp.edit().apply {
                if (value == null) {
                    remove(KEY_UPDATE_TAG); remove(KEY_UPDATE_URL); remove(KEY_UPDATE_SIZE); remove(KEY_UPDATE_SHA256)
                } else {
                    putString(KEY_UPDATE_TAG, value.tag)
                    putString(KEY_UPDATE_URL, value.url)
                    putLong(KEY_UPDATE_SIZE, value.size)
                    putString(KEY_UPDATE_SHA256, value.sha256)
                }
            }.commit()
        }

    private fun write(list: TargetList, removeOld: Boolean) {
        sp.edit().apply {
            putString(KEY_TARGETS, list.encodeItems())
            putString(KEY_DEFAULT, list.default)
            if (removeOld) remove(KEY_OLD_TARGET)
        }.commit()
    }

    private companion object {
        const val KEY_TARGETS = "targets"
        const val KEY_DEFAULT = "default_target"
        const val KEY_OLD_TARGET = "target"
        const val KEY_OPENING_TOAST = "opening_toast"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_UPDATE_CHECKED_AT = "update_checked_at"
        const val KEY_UPDATE_TAG = "update_tag"
        const val KEY_UPDATE_URL = "update_url"
        const val KEY_UPDATE_SIZE = "update_size"
        const val KEY_UPDATE_SHA256 = "update_sha256"
    }
}
