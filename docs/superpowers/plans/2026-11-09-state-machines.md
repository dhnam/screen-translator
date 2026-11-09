# State Machines Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 어시스턴트 세션과 업데이트 영역의 암시적 상태를 명시적 상태와 순수 전이 함수로 바꾸고, 그로 인한 네 가지 문제(연달아 누르기, 확인 실패 표시 사라짐, 설치 확인 중 버튼, 진행률 사라짐)를 고친다.

**Architecture:** 전이 규칙은 Android에 의존하지 않는 Kotlin(`SessionMachine`, `UpdateUi`, `UpdateView`)에 두고 JVM에서 테스트한다. Android 쪽(`AssistSession`, `UpdateFlow`, `SettingsActivity`, `InstallResultReceiver`)은 사건을 넘기고 결과대로 실행만 한다.

**Tech Stack:** Kotlin, JUnit 4 (기존과 같음). 새 의존성 없음.

**Spec:** `docs/superpowers/specs/2026-11-09-screen-translator-design.md` 13절

## Global Constraints

- 스크린샷 대기 시간은 2초, 자동 확인 간격은 24시간(기존과 같음)
- 받는 중 화면을 벗어나면(`onStop`) 취소한다(기존과 같음).
- 새 문구 `update_awaiting_install`("Waiting for install confirmation…" / "설치 확인 중…"), `update_download_failed`("Couldn't download the update" / "업데이트를 받지 못했어요")는 영어와 한국어에 함께 넣는다.
- 지난 세대의 캡처 파일은 지우지 않는다(10분 정리에 맡김).
- 이 계획에서는 릴리스 태그를 만들지 않는다.

## Review Focus

1. **저장 중에 홈버튼을 다시 길게 누름**: 번역 앱은 마지막 것으로 한 번만 열려야 한다. 테스트: Task 1 `saved_staleGeneration_isIgnored`.
2. **설치 확인 창에서 취소**: [업데이트]가 다시 눌려야 하고 실패 토스트는 없어야 한다. 테스트: Task 2 `awaitingInstall_installFinished_isIdle` + 기기 확인.
3. **화면을 다시 만들 때(회전, 재진입) 상태 유지**: 확인 실패 표시와 진행률이 남아야 한다. 테스트: Task 3 리스너가 등록되자마자 현재 상태를 받는지 + 기기 확인.
4. **설정 화면이 없는 동안 작업이 끝남**: 앱이 죽지 않고, 다음에 화면을 열면 결과 상태가 보여야 한다. 테스트: Task 3 리스너가 없을 때 상태만 바뀌는지.
5. **허용되지 않은 사건**: 예를 들어 받는 중에 확인을 시작하는 경우 상태가 바뀌지 않아야 한다. 테스트: Task 2 `disallowed_events_keepState`.

---

### Task 1: SessionMachine과 AssistSession 연결

**Files:**
- Create: `app/src/main/java/dev/dhnam/screentranslator/SessionMachine.kt`
- Modify: `AssistSession.kt`
- Test: `app/src/test/java/dev/dhnam/screentranslator/SessionMachineTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface SessionState { object Idle; data class Waiting(val gen: Int); data class Saving(val gen: Int); object Done }`
  - `sealed interface SessionEffect { data class StartTimer(val gen: Int); data class Save(val gen: Int); object Launch; data class Message(val res: Int); object None }`. `Message`의 res는 `R.string.no_screenshot` / `save_failed`
  - `class SessionMachine`
    - `var state: SessionState` (읽기 전용 공개)
    - `fun show(): SessionEffect`: `StartTimer(newGen)`
    - `fun screenshot(present: Boolean): SessionEffect`: `Waiting(g)`일 때만. present면 `Saving(g)` + `Save(g)`, 아니면 `Done` + `Message(no_screenshot)`
    - `fun timeout(gen: Int): SessionEffect`: `Waiting(gen)`일 때만 `Done` + `Message(no_screenshot)`
    - `fun saved(gen: Int, ok: Boolean): SessionEffect`: `Saving(gen)`일 때만 `Done` + (`Launch` 또는 `Message(save_failed)`)
    - 조건에 맞지 않으면 상태를 그대로 두고 `None`
  - `AssistSession`: `handled`를 지우고, 각 콜백에서 기계를 부른 뒤 Effect를 실행한다.
    - `Save(g)`: 백그라운드에서 저장하고, 끝나면 메인 스레드에서 `saved(g, uri != null)`을 부른다. `Launch`이면 그 uri로 실행한다.
    - `onHide`에서는 타이머만 지운다.

- [ ] **Step 1: 실패하는 테스트**

```kotlin
@Test fun happyPath()                      // show→StartTimer(1); screenshot(true)→Save(1); saved(1,true)→Launch; state Done
@Test fun nullScreenshot_messagesAndDone() // screenshot(false)→Message(no_screenshot)
@Test fun timeout_whileWaiting_messages()
@Test fun timeout_staleGeneration_isIgnored()  // show; show; timeout(1)→None, state Waiting(2)
@Test fun saved_staleGeneration_isIgnored()    // show; screenshot(true)[Saving(1)]; show[Waiting(2)]; saved(1,true)→None; screenshot(true); saved(2,true)→Launch
@Test fun screenshot_afterDone_isIgnored()
@Test fun saved_failure_messages()         // saved(g,false)→Message(save_failed)
```

- [ ] **Step 2: 실패 확인** — `./gradlew :app:testDebugUnitTest --tests '*SessionMachineTest*'` / Expected: 컴파일 실패
- [ ] **Step 3: `SessionMachine` 구현 → 통과 확인** (7 tests PASS)
- [ ] **Step 4: `AssistSession` 연결 → `./gradlew testDebugUnitTest assembleDebug`** / Expected: `BUILD SUCCESSFUL`
- [ ] **Step 5: Commit** — `refactor: explicit assistant session state machine`

---

### Task 2: UpdateUi 전이와 UpdateView

**Files:**
- Create: `UpdateUi.kt`(상태, 사건, `next`), `UpdateView.kt`
- Test: `UpdateUiTest.kt`, `UpdateViewTest.kt`
- Modify: 두 `strings.xml`(Global Constraints의 문구 2개)

**Interfaces:**
- Produces:
  - `sealed interface UpdateUi { object Idle; object Checking; object CheckFailed; data class Downloading(val pct: Int); object AwaitingInstall }`
  - `sealed interface UpdateEvent { object CheckStarted; object CheckOk; object CheckError; object DownloadStarted; data class Progress(val pct: Int); object Committed; object Cancelled; object DownloadError; object InstallFinished }`
  - `fun UpdateUi.next(e: UpdateEvent): UpdateUi`: 명세 13절 B의 표 그대로. 그 밖은 `this`
  - `enum class UpdateButton { CHECK, INSTALL, RETRY, NONE }`
  - `data class UpdateView(val text: Int, val args: List<Any>, val button: UpdateButton, val enabled: Boolean)`
  - `companion fun of(state: UpdateUi, latest: ReleaseInfo?, current: String): UpdateView`: 명세 13절 B의 목록 그대로. `Idle`에서 `latest`가 `Version.isNewer(latest.tag, current)`이면 새 버전, 아니면 최신(args는 `[current]`)

- [ ] **Step 1: 실패하는 테스트**

```kotlin
// UpdateUiTest
@Test fun check_flow()                 // Idle→CheckStarted→Checking→CheckOk→Idle; Checking→CheckError→CheckFailed; CheckFailed→CheckStarted→Checking
@Test fun download_flow()              // Idle→DownloadStarted→Downloading(0)→Progress(40)→Downloading(40)→Committed→AwaitingInstall
@Test fun awaitingInstall_installFinished_isIdle()
@Test fun download_cancelOrError_isIdle()
@Test fun disallowed_events_keepState() // Downloading(5)+CheckStarted, AwaitingInstall+DownloadStarted, Idle+Progress(3), Checking+DownloadStarted
// UpdateViewTest
@Test fun idle_newer_showsInstall()     // latest v0.4.0, current 0.3.0 → update_available, ["v0.4.0"], INSTALL, enabled
@Test fun idle_notNewer_showsCheck()    // latest null → update_latest, ["0.3.0"], CHECK, enabled
@Test fun busyStates_disableButton()    // Checking, Downloading(42) (args [42]), AwaitingInstall → NONE or disabled
@Test fun checkFailed_showsRetry()
```

- [ ] **Step 2: 실패 확인** — `./gradlew :app:testDebugUnitTest --tests '*UpdateUiTest*' --tests '*UpdateViewTest*'` / Expected: 컴파일 실패
- [ ] **Step 3: 구현 → 통과 확인**
- [ ] **Step 4: Commit** — `feat: explicit update UI state and view model`

---

### Task 3: UpdateFlow와 화면·수신기 연결

**Files:**
- Create: `UpdateFlow.kt`
- Modify: `SettingsActivity.kt`(companion의 `busy`/`downloading`/`current`와 `showBusy`/`showCheckFailed`를 지우고 `UpdateView`로 그린다), `InstallResultReceiver.kt`
- Test: `UpdateFlowTest.kt`(JVM, 백그라운드 실행기와 메인 실행기를 주입해 즉시 실행)

**Interfaces:**
- Consumes: `UpdateUi`, `UpdateEvent`, `UpdateChecker.check`, `UpdateInstaller.install(info, isCancelled, onProgress)`
- Produces:
  - `object UpdateFlow`
    - `val state: UpdateUi`
    - `fun observe(l: (UpdateUi) -> Unit)` / `fun stopObserving()`: 리스너는 하나. 등록하는 즉시 현재 상태로 한 번 부른다.
    - `fun dispatch(e: UpdateEvent)`: `next`로 바꾸고, 바뀌면 리스너를 부른다(메인 스레드).
    - `fun startCheck(run: () -> Unit)`, `fun startInstall(run: ((Int) -> Unit) -> Unit)`: 시작 사건이 허용될 때만 백그라운드에서 `run`을 실행하고 결과 사건을 보낸다. 실행기는 테스트에서 바꿀 수 있게 `internal var background: (Runnable) -> Unit`, `internal var main: (Runnable) -> Unit`
    - `@Volatile var cancelRequested: Boolean`
  - `InstallResultReceiver`: `STATUS_PENDING_USER_ACTION`이 아니면 `UpdateFlow.dispatch(InstallFinished)`. `STATUS_FAILURE_ABORTED`이면 토스트를 띄우지 않는다.
  - `SettingsActivity`: `onResume`에서 `UpdateFlow.observe { render(it) }`, `onPause`에서 `stopObserving()`. `render`는 `UpdateView.of(state, prefs.latestRelease, currentVersion)`로 문구와 버튼을 정한다. 받기 실패는 `update_download_failed`, 해시 불일치는 `update_corrupt` 토스트

- [ ] **Step 1: 실패하는 테스트**

```kotlin
@Test fun observe_receivesCurrentStateImmediately()
@Test fun startCheck_success_goesCheckingThenIdle()      // 리스너가 [Idle, Checking, Idle]을 받음
@Test fun startCheck_ioError_goesCheckFailed()
@Test fun startCheck_whileDownloading_isIgnored()        // run이 불리지 않음
@Test fun noObserver_stateStillAdvances()                // stopObserving 후 startCheck → state Idle, 예외 없음
@Test fun startInstall_success_awaitingInstall()         // run 안에서 progress(50) → Downloading(50) → AwaitingInstall
```

(각 테스트 전에 `UpdateFlow`를 초기 상태로 되돌리는 `internal fun resetForTest()`를 둔다.)

- [ ] **Step 2: 실패 확인** — Expected: 컴파일 실패
- [ ] **Step 3: `UpdateFlow` 구현 → 통과 확인**
- [ ] **Step 4: 화면과 수신기 연결 → `./gradlew testDebugUnitTest assembleDebug`** / Expected: `BUILD SUCCESSFUL`, 모든 테스트 PASS
- [ ] **Step 5: Commit** — `refactor: drive update section from UpdateFlow state`

---

### Task 4: 기기 확인과 문서

- [ ] **Step 1: dev 앱 확인 (어시스턴트를 dev 앱으로)** — 홈버튼을 짧은 간격으로 두 번 길게 누르기 → 번역 앱이 한 번만 열림
- [ ] **Step 2: 업데이트 흐름 확인** — 같은 키로 서명하고 versionName `0.2.9`, versionCode는 v0.3.0과 같은 `3`으로 둔 릴리스 빌드를 설치한다.
  1. 비행기 모드에서 [지금 확인] → "확인하지 못했어요" → 앱을 나갔다 들어와도 그대로
  2. 비행기 모드 해제 → [다시 시도] → "새 버전 v0.3.0이 있어요"
  3. [업데이트] → 받는 중 → 설치 확인 창에서 **취소** → [업데이트]가 다시 켜지고 실패 토스트 없음
  4. 다시 [업데이트] → 설치 → "최신 버전이에요 (v0.3.0)"
- [ ] **Step 3: 문서** — `TODO.md`에서 해결된 항목(연달아 누르기, 설치 확인 중 버튼, 받기 실패 문구)을 완료로 표시하고 "다음 릴리스에 포함"을 붙인다. README 구조 표에 `SessionMachine`, `UpdateFlow`를 추가한다.
- [ ] **Step 4: 전체 확인** — `./gradlew clean testDebugUnitTest assembleDebug` / Expected: `BUILD SUCCESSFUL`
- [ ] **Step 5: Commit** — `docs: state machines` (push는 최종 검토 후)
