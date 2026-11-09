package dev.dhnam.screentranslator

import androidx.annotation.StringRes

enum class UpdateButton { CHECK, INSTALL, RETRY, REOPEN, NONE }

/** 업데이트 영역에 보일 문구와 버튼. 상태만 보고 정한다. */
data class UpdateView(@StringRes val text: Int, val args: List<Any>, val button: UpdateButton, val enabled: Boolean) {
    companion object {
        /** `canReopen`: 설치 확인 창을 다시 띄울 수 있는지(확인 창 인텐트를 기억하고 있는지). */
        fun of(state: UpdateUi, latest: ReleaseInfo?, current: String, canReopen: Boolean = false): UpdateView = when (state) {
            UpdateUi.Idle ->
                if (latest != null && Version.isNewer(latest.tag, current)) {
                    UpdateView(R.string.update_available, listOf(latest.tag), UpdateButton.INSTALL, true)
                } else {
                    UpdateView(R.string.update_latest, listOf(current), UpdateButton.CHECK, true)
                }
            UpdateUi.Checking -> UpdateView(R.string.update_checking, emptyList(), UpdateButton.NONE, false)
            UpdateUi.CheckFailed -> UpdateView(R.string.update_failed, emptyList(), UpdateButton.RETRY, true)
            is UpdateUi.Downloading ->
                if (state.pct == null) UpdateView(R.string.update_preparing, emptyList(), UpdateButton.NONE, false)
                else UpdateView(R.string.update_downloading, listOf(state.pct), UpdateButton.NONE, false)
            UpdateUi.AwaitingInstall ->
                if (canReopen) UpdateView(R.string.update_awaiting_install, emptyList(), UpdateButton.REOPEN, true)
                else UpdateView(R.string.update_awaiting_install, emptyList(), UpdateButton.NONE, false)
        }
    }
}
