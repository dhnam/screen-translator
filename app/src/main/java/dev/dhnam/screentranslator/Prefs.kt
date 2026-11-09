package dev.dhnam.screentranslator

import android.content.Context
import androidx.annotation.StringRes

/** 캡처를 넘길 번역 앱. */
enum class Target(val pkg: String, @StringRes val openingMessage: Int) {
    PAPAGO("com.naver.labs.translator", R.string.opening_papago),
    GOOGLE("com.google.android.googlequicksearchbox", R.string.opening_google),
}

/** 사용자 설정 저장. 저장된 값이 없거나 알 수 없으면 파파고. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var target: Target
        get() = sp.getString(KEY_TARGET, null)
            ?.let { name -> Target.entries.firstOrNull { it.name == name } }
            ?: Target.PAPAGO
        set(value) { sp.edit().putString(KEY_TARGET, value.name).apply() }

    /** 번역 앱을 열 때 "여는 중…" 토스트를 띄울지. 기본값은 꺼짐. */
    var showOpeningToast: Boolean
        get() = sp.getBoolean(KEY_OPENING_TOAST, false)
        set(value) { sp.edit().putBoolean(KEY_OPENING_TOAST, value).apply() }

    private companion object {
        const val KEY_TARGET = "target"
        const val KEY_OPENING_TOAST = "opening_toast"
    }
}
