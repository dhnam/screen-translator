package dev.dhnam.screentranslator

/** 설정 화면 업데이트 영역의 상태. `Idle`이면 저장된 마지막 결과로 "최신" 또는 "새 버전 있음"을 그린다. */
sealed interface UpdateUi {
    object Idle : UpdateUi
    object Checking : UpdateUi
    object CheckFailed : UpdateUi
    /** `pct`가 null이면 아직 데이터가 오기 전(다운로드 준비 중). */
    data class Downloading(val pct: Int?) : UpdateUi
    object AwaitingInstall : UpdateUi

    /** 허용된 전이만 일어난다. 그 밖의 사건은 상태를 바꾸지 않는다. (설계서 13절 B 표) */
    fun next(e: UpdateEvent): UpdateUi = when (this) {
        Idle, CheckFailed -> when (e) {
            UpdateEvent.CheckStarted -> Checking
            UpdateEvent.DownloadStarted -> Downloading(null)
            else -> this
        }
        Checking -> when (e) {
            UpdateEvent.CheckOk -> Idle
            UpdateEvent.CheckError -> CheckFailed
            else -> this
        }
        is Downloading -> when (e) {
            is UpdateEvent.Progress -> Downloading(e.pct)
            UpdateEvent.Committed -> AwaitingInstall
            UpdateEvent.Cancelled, UpdateEvent.DownloadError -> Idle
            else -> this
        }
        AwaitingInstall -> if (e == UpdateEvent.InstallFinished) Idle else this
    }
}

sealed interface UpdateEvent {
    object CheckStarted : UpdateEvent
    object CheckOk : UpdateEvent
    object CheckError : UpdateEvent
    object DownloadStarted : UpdateEvent
    data class Progress(val pct: Int) : UpdateEvent
    object Committed : UpdateEvent
    object Cancelled : UpdateEvent
    object DownloadError : UpdateEvent
    object InstallFinished : UpdateEvent
}
