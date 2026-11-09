package dev.dhnam.screentranslator

import android.app.UiModeManager

/** 설정 화면 테마. Android 12 이상에서 `UiModeManager.setApplicationNightMode`로 적용한다. */
enum class ThemeMode(val nightMode: Int) {
    SYSTEM(UiModeManager.MODE_NIGHT_AUTO),
    LIGHT(UiModeManager.MODE_NIGHT_NO),
    DARK(UiModeManager.MODE_NIGHT_YES),
}
