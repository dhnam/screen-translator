package dev.dhnam.screentranslator

import android.os.Handler
import android.os.Looper
import java.io.IOException

/**
 * 업데이트 영역의 상태([UpdateUi])와 백그라운드 작업을 프로세스에 하나만 둔다.
 * 화면이 새로 만들어져도(회전, 재진입) 상태와 진행률이 남고, 같은 작업이 두 번 시작되지 않는다.
 * `dispatch`와 리스너 호출은 메인 스레드에서 일어난다.
 */
object UpdateFlow {
    @Volatile var state: UpdateUi = UpdateUi.Idle
        private set

    /** 받는 중 취소 요청(설정 화면의 onStop). UpdateInstaller가 진행 중에 확인한다. */
    @Volatile var cancelRequested = false

    private var listener: ((UpdateUi) -> Unit)? = null

    internal var background: (Runnable) -> Unit = { Thread(it).start() }
    internal var main: (Runnable) -> Unit = { mainHandler.post(it) }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** 리스너는 하나. 등록하는 즉시 현재 상태로 한 번 부른다. */
    fun observe(l: (UpdateUi) -> Unit) {
        listener = l
        l(state)
    }

    fun stopObserving() {
        listener = null
    }

    fun dispatch(e: UpdateEvent) {
        val n = state.next(e)
        if (n == state) return
        state = n
        listener?.invoke(n)
    }

    /** 확정한 설치 세션 번호. "설치 확인 대기"일 때만 의미가 있다. */
    @Volatile var pendingSessionId: Int? = null
        private set

    /**
     * 설정 화면이 다시 보일 때 부른다. "설치 확인 대기"인데 그 세션이 이미 없으면(확인 창이 닫혔거나 뜨지 못함)
     * 기본 상태로 돌린다. 세션이 살아 있으면(설치 확인·Play 프로텍트 검사·설치 진행 중) 그대로 둔다.
     */
    fun onScreenReturned(isSessionAlive: (Int) -> Boolean) {
        if (state != UpdateUi.AwaitingInstall) return
        val id = pendingSessionId
        if (id == null || !isSessionAlive(id)) dispatch(UpdateEvent.InstallFinished)
    }

    /** 확인을 시작한다. 지금 상태에서 허용되지 않으면 false. `run`은 백그라운드에서 실행된다. */
    fun startCheck(run: () -> Unit): Boolean {
        if (state.next(UpdateEvent.CheckStarted) == state) return false
        dispatch(UpdateEvent.CheckStarted)
        background(Runnable {
            val ok = try { run(); true } catch (e: IOException) { false }
            main(Runnable { dispatch(if (ok) UpdateEvent.CheckOk else UpdateEvent.CheckError) })
        })
        return true
    }

    /**
     * 받기와 설치 세션 확정을 시작한다. `run`은 진행률 콜백을 받아 백그라운드에서 실행되고, 확정한 세션 번호를 돌려준다.
     * 취소가 아닌 실패는 `onError`로 알린다(메인 스레드).
     */
    fun startInstall(run: ((Int) -> Unit) -> Int, onError: (IOException) -> Unit): Boolean {
        if (state.next(UpdateEvent.DownloadStarted) == state) return false
        cancelRequested = false
        dispatch(UpdateEvent.DownloadStarted)
        background(Runnable {
            try {
                val sessionId = run { pct -> main(Runnable { dispatch(UpdateEvent.Progress(pct)) }) }
                main(Runnable {
                    pendingSessionId = sessionId
                    dispatch(UpdateEvent.Committed)
                })
            } catch (e: IOException) {
                val cancelled = e.message == UpdateInstaller.CANCELLED
                main(Runnable {
                    dispatch(if (cancelled) UpdateEvent.Cancelled else UpdateEvent.DownloadError)
                    if (!cancelled) onError(e)
                })
            }
        })
        return true
    }

    internal fun resetForTest() {
        state = UpdateUi.Idle
        cancelRequested = false
        pendingSessionId = null
        listener = null
        background = { Thread(it).start() }
        main = { mainHandler.post(it) }
    }
}
