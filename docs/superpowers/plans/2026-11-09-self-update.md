# Self Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 앱이 GitHub Releases에서 새 버전을 확인하고, 받아서 `PackageInstaller`로 설치한다(APK 파일이 남지 않음).

**Architecture:** 버전 비교·자동 확인 판단·해시 복사는 순수 Kotlin(`Version`, `HashingCopy`)으로 JVM에서 테스트한다. GitHub 응답 해석(`ReleaseInfo.parse`)은 `org.json`을 쓰므로 Robolectric으로 테스트한다. 네트워크와 설치(`UpdateChecker`, `UpdateInstaller`, `InstallResultReceiver`)는 얇게 유지하고 기기에서 확인한다.

**Tech Stack:** Kotlin, Android SDK 34, `HttpURLConnection`, `org.json`, `PackageInstaller`, JUnit 4, Robolectric 4.13. 새 의존성 없음.

**Spec:** `docs/superpowers/specs/2026-11-09-screen-translator-design.md` 12절

## Global Constraints

- API: `https://api.github.com/repos/dhnam/screen-translator/releases/latest`, 헤더 `Accept: application/vnd.github+json`, 연결과 응답 대기 시간은 각 10초
- APK 첨부 이름: `screen-translator-`로 시작하고 `.apk`로 끝나는 첫 항목
- `digest` 형식: `sha256:<소문자 hex>`, 없을 수 있음
- 자동 확인 간격: 24시간(`24 * 60 * 60 * 1000L`)
- 저장 키(`settings` 파일): `update_checked_at`, `update_tag`, `update_url`, `update_size`, `update_sha256`
- `BuildConfig.UPDATE_ENABLED`: release는 true, debug(dev 앱)는 false
- 모든 새 문구는 `values/strings.xml`(영어)과 `values-ko/strings.xml`(한국어)에 함께 넣는다(`StringsParityTest`가 확인함).
- 이 계획에서는 릴리스 태그를 만들지 않는다.

## Review Focus

1. **네트워크가 없거나 GitHub가 느림**: 설정 화면이 멈추지 않고 "확인하지 못했어요"로 끝나야 한다. 조회는 백그라운드 스레드에서 하고, 대기 시간 제한이 있어야 한다. 테스트: 기기 확인(비행기 모드).
2. **GitHub API 사용량 제한(403)이나 예상과 다른 JSON**: 앱이 죽지 않고 실패로 처리해야 한다. 테스트: Task 2 `parse_malformed_returnsNull`, `parse_noApkAsset_returnsNull`.
3. **받는 도중 화면을 나가거나 끊김**: 설치 세션을 버려야 하고, 남은 세션이나 파일이 없어야 한다. 테스트: Task 3 `HashingCopy` 예외 전파 테스트 + 기기 확인.
4. **해시가 다른 파일**: 설치하지 않아야 한다. 테스트: Task 3 `hashesMatch_mismatch_isFalse`, `hashesMatch_missingExpected_isTrue`.
5. **dev 앱**: 네트워크를 전혀 쓰지 않아야 한다. 테스트: Task 4 기기 확인(dev 앱 화면 문구) + 코드에서 `UPDATE_ENABLED` 확인.

---

### Task 1: Version과 자동 확인 판단

**Files:**
- Create: `app/src/main/java/dev/dhnam/screentranslator/Version.kt`
- Test: `app/src/test/java/dev/dhnam/screentranslator/VersionTest.kt`

**Interfaces:**
- Produces:
  - `data class Version(val parts: List<Int>) : Comparable<Version>`: 빠진 자리는 0으로 비교
  - `companion fun parse(s: String?): Version?`: 앞의 `v`/`V`와 공백은 무시, 점으로 나눈 각 부분이 정수가 아니면 null
  - `fun isNewer(latest: String?, current: String?): Boolean`: 둘 중 하나라도 해석할 수 없으면 false
  - `fun shouldAutoCheck(now: Long, lastCheckedAt: Long, intervalMs: Long = DAY_MS): Boolean`: `lastCheckedAt == 0` 또는 `now - lastCheckedAt >= intervalMs` 또는 `now < lastCheckedAt`(시계가 거꾸로 감)이면 true
  - `const val DAY_MS = 24 * 60 * 60 * 1000L`

- [ ] **Step 1: 실패하는 테스트**

```kotlin
@Test fun parse_ignoresVPrefix() = assertEquals(Version.parse("0.2.0"), Version.parse("v0.2.0"))
@Test fun compare_numericNotLexical() = assertTrue(Version.parse("0.10.0")!! > Version.parse("0.9.0")!!)
@Test fun compare_missingPartsAreZero() = assertEquals(0, Version.parse("0.2")!!.compareTo(Version.parse("0.2.0")!!))
@Test fun compare_major() = assertTrue(Version.parse("1.0.0")!! > Version.parse("0.99.99")!!)
@Test fun parse_garbage_isNull()        // "abc", "1.x", "", null → null
@Test fun isNewer_cases()               // ("v0.3.0","0.2.0") true, ("v0.2.0","0.2.0") false, ("v0.1.0","0.2.0") false, ("bad","0.2.0") false
@Test fun shouldAutoCheck_cases()       // (now, 0) true; (now, now - DAY_MS + 1) false; (now, now - DAY_MS) true; (now, now + 1000) true
```

- [ ] **Step 2: 실패 확인** — `./gradlew :app:testDebugUnitTest --tests '*VersionTest*'` / Expected: 컴파일 실패
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — 같은 명령 / Expected: 7 tests PASS
- [ ] **Step 5: Commit** — `feat: version comparison for update check`

---

### Task 2: ReleaseInfo 해석과 저장, 빌드 설정

**Files:**
- Create: `app/src/main/java/dev/dhnam/screentranslator/ReleaseInfo.kt`
- Modify: `Prefs.kt`, `app/build.gradle.kts`(`buildFeatures { buildConfig = true }`, release와 debug에 `buildConfigField("boolean", "UPDATE_ENABLED", ...)`)
- Test: `ReleaseInfoTest.kt`(Robolectric), `PrefsTest.kt` 추가

**Interfaces:**
- Consumes: 없음
- Produces:
  - `data class ReleaseInfo(val tag: String, val url: String, val size: Long, val sha256: String?)`
  - `companion fun parse(json: String): ReleaseInfo?`: Global Constraints 규칙으로 APK 첨부를 고른다. `digest`가 `sha256:`로 시작하면 뒤를 소문자로, 아니면 null. 해석에 실패하면(JSON 오류, 필드 없음) null
  - `Prefs.updateCheckedAt: Long`(기본값 0)
  - `Prefs.latestRelease: ReleaseInfo?`: 저장할 때 null이면 네 키를 지운다.

- [ ] **Step 1: 실패하는 테스트**

```kotlin
// ReleaseInfoTest (샘플 JSON은 테스트 안의 문자열 상수)
@Test fun parse_picksApkAsset()            // assets: [notes.txt, screen-translator-v0.3.0.apk(size 4323071, digest "sha256:ABCD")] → tag v0.3.0, url, size, sha256 "abcd"
@Test fun parse_noDigest_sha256Null()
@Test fun parse_noApkAsset_returnsNull()   // assets에 다른 이름만 있음
@Test fun parse_malformed_returnsNull()    // "{", "{\"message\":\"API rate limit exceeded\"}"
// PrefsTest
@Test fun latestRelease_roundTripAndClear()
@Test fun updateCheckedAt_defaultZero()
```

- [ ] **Step 2: 실패 확인** — `./gradlew :app:testDebugUnitTest` / Expected: 컴파일 실패
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — `./gradlew testDebugUnitTest assembleDebug` / Expected: 모든 테스트 PASS
- [ ] **Step 5: Commit** — `feat: parse GitHub release info and cache last check`

---

### Task 3: 확인·받기·설치

**Files:**
- Create: `HashingCopy.kt`, `UpdateChecker.kt`, `UpdateInstaller.kt`, `InstallResultReceiver.kt`
- Modify: `AndroidManifest.xml`(`INTERNET`, `REQUEST_INSTALL_PACKAGES`, `<receiver android:name=".InstallResultReceiver" android:exported="false"/>`), 두 `strings.xml`(`install_failed` = "Couldn't install the update (%1$s)" / "업데이트를 설치하지 못했어요 (%1$s)")
- Test: `HashingCopyTest.kt`(JVM)

**Interfaces:**
- Consumes: `ReleaseInfo.parse`, `Version.isNewer`, `Prefs.latestRelease`, `Prefs.updateCheckedAt`
- Produces:
  - `object HashingCopy`
    - `fun copy(input: InputStream, output: OutputStream, onProgress: (Long) -> Unit): String`: SHA-256 소문자 hex를 돌려준다. 입출력 예외는 그대로 던진다.
    - `fun hashesMatch(expected: String?, actual: String): Boolean`: expected가 null이면 true, 아니면 대소문자 무시 비교
  - `class UpdateChecker(context: Context)`
    - `fun check(): ReleaseInfo?`(백그라운드 스레드용): 조회 → `parse` → `isNewer(tag, 현재 versionName)`이면 그 정보, 아니면 null. 저장(`updateCheckedAt`, `latestRelease`)도 여기서 한다. 네트워크·HTTP 오류는 `IOException`으로 던진다. HTTP 200이 아니면 `IOException("HTTP <code>")`
  - `class UpdateInstaller(context: Context)`
    - `fun install(info: ReleaseInfo, onProgress: (Int) -> Unit)`(백그라운드 스레드용): 세션을 만들고 받으면서 쓰고 해시를 확인한 뒤 `commit`한다. 예외가 나거나 해시가 다르면 `abandon`하고 `IOException`을 던진다(해시가 다르면 메시지 `"digest mismatch"`).
    - commit의 `IntentSender`는 `InstallResultReceiver`로 가는 `PendingIntent.getBroadcast(..., FLAG_MUTABLE or FLAG_UPDATE_CURRENT)`
  - `class InstallResultReceiver : BroadcastReceiver`: `EXTRA_STATUS`가 `STATUS_PENDING_USER_ACTION`이면 `Intent.EXTRA_INTENT`를 `FLAG_ACTIVITY_NEW_TASK`로 실행한다. `STATUS_SUCCESS`이면 아무것도 하지 않는다. 그 밖이면 `install_failed` 토스트(`EXTRA_STATUS_MESSAGE`)를 띄운다.

- [ ] **Step 1: 실패하는 테스트**

```kotlin
@Test fun copy_knownInput_sha256AndBytes()   // "hello" → "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", 출력 바이트 동일
@Test fun copy_reportsFinalProgressAsTotal() // 100_000바이트 → 마지막 onProgress 값 100_000
@Test fun copy_propagatesIoError()           // read에서 IOException을 던지는 InputStream → assertThrows
@Test fun hashesMatch_mismatch_isFalse()
@Test fun hashesMatch_missingExpected_isTrue()
@Test fun hashesMatch_caseInsensitive()
```

- [ ] **Step 2: 실패 확인** — `./gradlew :app:testDebugUnitTest --tests '*HashingCopyTest*'` / Expected: 컴파일 실패
- [ ] **Step 3: `HashingCopy` 구현 → 테스트 통과 확인**
- [ ] **Step 4: `UpdateChecker`, `UpdateInstaller`, `InstallResultReceiver`, 매니페스트 구현**
- [ ] **Step 5: 빌드** — `./gradlew testDebugUnitTest assembleDebug` / Expected: `BUILD SUCCESSFUL`
- [ ] **Step 6: Commit** — `feat: check, download and install updates via PackageInstaller`

---

### Task 4: 설정 화면 업데이트 영역과 기기 확인

**Files:**
- Modify: `res/layout/activity_settings.xml`(맨 아래에 제목 `update_title`, 상태 `TextView @+id/update_status`, `Button @+id/update_action`), `SettingsActivity.kt`, 두 `strings.xml`

**문구 (영어 / 한국어)**
- `update_title`: "Updates" / "업데이트"
- `update_latest`: "You're up to date (v%1$s)" / "최신 버전이에요 (v%1$s)"
- `update_available`: "Version %1$s is available" / "새 버전 %1$s이 있어요"
- `update_checking`: "Checking…" / "확인 중…"
- `update_downloading`: "Downloading… %1$d%%" / "받는 중… %1$d%%"
- `update_failed`: "Couldn't check for updates" / "업데이트를 확인하지 못했어요"
- `update_corrupt`: "The downloaded file is corrupted" / "받은 파일이 손상됐어요"
- `update_disabled_dev`: "Update check is off in the dev app" / "개발용 앱은 업데이트 확인을 하지 않아요"
- `update_check_now`: "Check now" / "지금 확인"
- `update_install`: "Update" / "업데이트"
- `update_retry`: "Retry" / "다시 시도"
- `update_permission_title`: "Allow app installs" / "앱 설치 허용"
- `update_permission_message`: "To install updates, allow this app to install apps." / "업데이트를 설치하려면 이 앱이 앱을 설치할 수 있도록 허용해야 해요."
- `update_permission_open`: "Open settings" / "설정 열기"

**Interfaces:**
- Consumes: `UpdateChecker.check`, `UpdateInstaller.install`, `Prefs.latestRelease`, `Prefs.updateCheckedAt`, `Version.shouldAutoCheck`, `BuildConfig.UPDATE_ENABLED`

- [ ] **Step 1: 구현**
  - `BuildConfig.UPDATE_ENABLED`가 false면 `update_disabled_dev`만 보여 주고 버튼은 숨긴다. 그 외 코드는 실행하지 않는다.
  - `renderUpdate()`: 저장된 `latestRelease`가 있고 현재보다 새 버전이면 "새 버전"+[업데이트], 아니면 "최신"+[지금 확인]
  - `onResume`: `shouldAutoCheck`이면 `startCheck()`
  - `startCheck()`: "확인 중…"을 표시하고 백그라운드 스레드에서 `check()`를 실행한다. 끝나면 메인 스레드에서 `renderUpdate()`. `IOException`이면 "확인하지 못했어요"+[다시 시도]. 이미 진행 중이면 무시한다.
  - [업데이트]: `canRequestPackageInstalls()`가 false면 권한 안내 창을 띄운다. true면 백그라운드에서 `install`을 실행하고 진행률을 "받는 중… N%"로 갱신한다. 해시가 다르면 `update_corrupt`, 다른 실패는 `update_failed` 토스트를 띄우고 `renderUpdate()`
- [ ] **Step 2: 빌드** — `./gradlew testDebugUnitTest assembleDebug` / Expected: `BUILD SUCCESSFUL`
- [ ] **Step 3: dev 앱 확인** — dev 앱 설치 후 업데이트 영역에 `update_disabled_dev`만 보이는지
- [ ] **Step 4: 전체 흐름 확인 (사용자와 함께)**
  - 로컬 키로 `VERSION_NAME=0.1.9 VERSION_CODE=<v0.2.0의 versionCode, 2> ./gradlew assembleRelease`를 실행하고 `adb install -r`로 설치한다(같은 서명, 같은 버전 코드라 업데이트로 설치됨).
  1. 설정 화면에 "새 버전 v0.2.0이 있어요"가 보인다.
  2. [업데이트]를 누르면 권한 안내가 뜨고, 허용 화면으로 간다.
  3. 허용한 뒤 다시 누르면 "받는 중… N%"가 보인다.
  4. 설치 확인 창이 뜨고, 설치 후 설정 화면에 "최신 버전이에요 (v0.2.0)"가 보인다.
  5. `adb shell run-as`는 릴리스 빌드에서 쓸 수 없으므로, APK가 남지 않는다는 것은 코드 구조(파일을 쓰지 않음)로 확인한다.
  6. 비행기 모드에서 [지금 확인] → 약 10초 안에 "확인하지 못했어요"가 뜨고 화면이 멈추지 않는다.
- [ ] **Step 5: Commit** — `feat: update section in settings`

---

### Task 5: 문서

- Modify: `README.md`, `README.ko.md`(업데이트 안내 한 줄: 설정 화면에서 업데이트를 확인하고 설치하며, 처음 한 번 "앱 설치 허용"이 필요함), `TODO.md`(업데이트 확인 완료 표시, "다음 릴리스에 포함")
- [ ] **Step 1: 문서 수정**
- [ ] **Step 2: 전체 확인** — `./gradlew clean testDebugUnitTest assembleDebug` / Expected: `BUILD SUCCESSFUL`
- [ ] **Step 3: Commit** — `docs: self update` (push는 최종 검토 후)
