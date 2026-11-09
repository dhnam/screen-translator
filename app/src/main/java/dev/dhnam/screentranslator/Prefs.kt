package dev.dhnam.screentranslator

import android.content.Context

/** 캡처를 넘길 번역 앱. */
enum class Target(val pkg: String) {
    PAPAGO("com.naver.labs.translator"),
    GOOGLE("com.google.android.googlequicksearchbox"),
}

/** 사용자 설정 저장. 저장된 값이 없거나 알 수 없으면 파파고. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var target: Target
        get() = sp.getString(KEY_TARGET, null)
            ?.let { name -> Target.entries.firstOrNull { it.name == name } }
            ?: Target.PAPAGO
        set(value) { sp.edit().putString(KEY_TARGET, value.name).apply() }

    private companion object {
        const val KEY_TARGET = "target"
    }
}
