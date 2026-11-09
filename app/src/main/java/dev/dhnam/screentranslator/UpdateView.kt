package dev.dhnam.screentranslator

import androidx.annotation.StringRes

enum class UpdateButton { CHECK, INSTALL, RETRY, NONE }

/** 업데이트 영역에 보일 문구와 버튼. 상태만 보고 정한다. */
data class UpdateView(@StringRes val text: Int, val args: List<Any>, val button: UpdateButton, val enabled: Boolean) {
    companion object {
        fun of(state: UpdateUi, latest: ReleaseInfo?, current: String): UpdateView = when (state) {
            UpdateUi.Idle ->
                if (latest != null && Version.isNewer(latest.tag, current)) {
                    UpdateView(R.string.update_available, listOf(latest.tag), UpdateButton.INSTALL, true)
                } else {
                    UpdateView(R.string.update_latest, listOf(current), UpdateButton.CHECK, true)
                }
            UpdateUi.Checking -> UpdateView(R.string.update_checking, emptyList(), UpdateButton.NONE, false)
            UpdateUi.CheckFailed -> UpdateView(R.string.update_failed, emptyList(), UpdateButton.RETRY, true)
            is UpdateUi.Downloading -> UpdateView(R.string.update_downloading, listOf(state.pct), UpdateButton.NONE, false)
            UpdateUi.AwaitingInstall -> UpdateView(R.string.update_awaiting_install, emptyList(), UpdateButton.NONE, false)
        }
    }
}
