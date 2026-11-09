package dev.dhnam.screentranslator

import androidx.annotation.StringRes

/** 어시스턴트 세션의 상태. `gen`은 홈버튼을 누를 때마다(`show`) 1씩 늘어나는 세대 번호다. */
sealed interface SessionState {
    object Idle : SessionState
    data class Waiting(val gen: Int) : SessionState
    data class Saving(val gen: Int) : SessionState
    object Done : SessionState
}

/** 전이의 결과로 세션이 해야 할 일. */
sealed interface SessionEffect {
    data class StartTimer(val gen: Int) : SessionEffect
    data class Save(val gen: Int) : SessionEffect
    object Launch : SessionEffect
    data class Message(@StringRes val res: Int) : SessionEffect
    object None : SessionEffect
}

/**
 * 스크린샷 대기 → 저장 → 실행의 상태 기계. 지난 세대의 타이머·저장 결과는 무시하므로,
 * 연달아 누르면 마지막 것 하나만 번역 앱으로 간다. Android에 의존하지 않는다.
 */
class SessionMachine {
    var state: SessionState = SessionState.Idle
        private set
    private var gen = 0

    fun show(): SessionEffect {
        gen++
        state = SessionState.Waiting(gen)
        return SessionEffect.StartTimer(gen)
    }

    fun screenshot(present: Boolean): SessionEffect {
        val s = state as? SessionState.Waiting ?: return SessionEffect.None
        return if (present) {
            state = SessionState.Saving(s.gen)
            SessionEffect.Save(s.gen)
        } else {
            state = SessionState.Done
            SessionEffect.Message(R.string.no_screenshot)
        }
    }

    fun timeout(gen: Int): SessionEffect {
        if (state != SessionState.Waiting(gen)) return SessionEffect.None
        state = SessionState.Done
        return SessionEffect.Message(R.string.no_screenshot)
    }

    fun saved(gen: Int, ok: Boolean): SessionEffect {
        if (state != SessionState.Saving(gen)) return SessionEffect.None
        state = SessionState.Done
        return if (ok) SessionEffect.Launch else SessionEffect.Message(R.string.save_failed)
    }
}
