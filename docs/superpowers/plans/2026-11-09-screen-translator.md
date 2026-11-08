# Screen Translator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 홈버튼을 길게 누르면 현재 화면 스크린샷을 파파고(기본)나 구글 렌즈로 바로 넘기는 Android 디지털 어시스턴트 앱을 만든다.

**Architecture:** `VoiceInteractionService`로 기본 어시스턴트로 등록한다. 세션의 `onHandleScreenshot(Bitmap)`에서 받은 이미지를 앱 캐시에 PNG로 저장하고, `FileProvider` URI로 `ACTION_SEND` 인텐트를 만들어 대상 앱을 실행한 뒤 세션을 닫는다. 파일 처리(`ShotStore`)와 인텐트 생성(`TargetResolver`)은 Android 런타임과 분리해서 JVM과 Robolectric으로 테스트한다.

**Tech Stack:** Kotlin, Android Gradle Plugin 8.5.x, Gradle 8.7 (wrapper), JDK 17, Android SDK 34, JUnit 4, Robolectric 4.13

**Spec:** `docs/superpowers/specs/2026-11-09-screen-translator-design.md`

## Global Constraints

- 패키지 이름: `dev.dhnam.screentranslator`
- `minSdk 30`, `targetSdk 34`, `compileSdk 34`
- Android Studio는 쓰지 않는다. SDK는 `~/Android/Sdk`에 둔다.
- 캡처 저장 위치: `cacheDir/shots/`, 파일 이름: `shot-<timestamp>.png`. 공유된 파일은 10분이 지난 뒤에 지운다.
- 파파고 패키지 `com.naver.labs.translator`, 구글 패키지 `com.google.android.googlequicksearchbox`
- 기본 번역 앱: 파파고
- 스크린샷 대기 시간: 2초
- 설정 화면을 열 때 `Settings.ACTION_VOICE_INPUT_SETTINGS`를 먼저 시도하고, 안 되면 `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS`를 쓴다.
- 의존성은 최소로 둔다. 앱 코드에는 AndroidX를 쓰지 않는다. 테스트에만 JUnit과 Robolectric을 쓴다.
- 사용자에게 보이는 문구는 한국어로 쓴다.

## Review Focus

1. **캡처를 막는 화면(FLAG_SECURE)에서 실행**: 앱이 죽지 않고 안내 토스트를 띄운 뒤 창을 닫아야 한다. 테스트: Task 4 기기 확인 항목.
2. **홈버튼을 빠르게 두 번 누름**: 같은 밀리초에 저장해도 파일 이름이 겹쳐 덮어쓰지 않아야 한다. 테스트: Task 2 `save_twiceSameMillis_createsDistinctFiles`.
3. **파파고가 비활성화되었거나 지워짐**: 공유 메뉴로 대체되고, 대체 인텐트도 읽기 권한을 가져야 한다. 테스트: Task 3 `chooser_whenTargetMissing_keepsGrantAndClip`.
4. **설정을 한 번도 열지 않은 첫 실행**: 저장된 설정이 없으면 파파고로 가야 한다. 테스트: Task 3 `prefs_default_isPapago`.
5. **저장 공간 부족 등으로 파일 쓰기 실패**: 반쯤 쓰인 파일이 남지 않아야 하고, 세션은 토스트를 띄운 뒤 닫혀야 한다. 테스트: Task 2 `save_whenWriterThrows_leavesNoFile`.

---

### Task 1: 도구 설치, 프로젝트 뼈대, 기기 확인용 최소 앱

설계서 7절의 위험 요소를 먼저 기기에서 확인하는 작업이다. 여기서 만든 코드는 버리지 않고 Task 4에서 다듬는다.

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew`, `gradle/wrapper/*`, `.gitignore`, `local.properties` (git 제외)
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/assist_service.xml`, `app/src/main/res/xml/file_paths.xml`, `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/dev/dhnam/screentranslator/{AssistService, AssistSessionService, AssistSession, StubRecognitionService, SettingsActivity}.kt`

**Interfaces:**
- Produces:
  - 매니페스트에 선언된 서비스 클래스 이름 (Task 4, 5가 그대로 사용)
  - FileProvider authority `dev.dhnam.screentranslator.files`

- [ ] **Step 1: JDK 17 설치**

배포판 패키지 관리자로 설치한다(`dnf install java-17-openjdk-devel` 또는 `apt install openjdk-17-jdk`). 루트 권한 문제로 안 되면 Temurin 17 tar.gz를 `~/.local/jdk-17`에 풀고 `JAVA_HOME`을 지정한다.
Run: `java -version`
Expected: `17.` 로 시작하는 버전

- [ ] **Step 2: Android 명령줄 도구 설치**

Google의 commandlinetools-linux zip을 `~/Android/Sdk/cmdline-tools/latest`에 푼다. 그다음 실행: `sdkmanager --licenses` 후 `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"`
Run: `~/Android/Sdk/platform-tools/adb version`
Expected: `Android Debug Bridge version` 출력

- [ ] **Step 3: Gradle wrapper 생성**

Gradle 8.7 배포본을 `~/.local/gradle-8.7`에 받아서, 프로젝트 루트에서 `gradle wrapper --gradle-version 8.7`을 실행한다. `local.properties`에는 `sdk.dir=/home/dhnam/Android/Sdk`를 적는다.

- [ ] **Step 4: 매니페스트와 XML 작성**

- `AssistService`: `android:permission="android.permission.BIND_VOICE_INTERACTION"`, intent-filter `android.service.voice.VoiceInteractionService`, `<meta-data android:name="android.voice_interaction" android:resource="@xml/assist_service"/>`
- `AssistSessionService`: `android:permission="android.permission.BIND_VOICE_INTERACTION"`
- `StubRecognitionService`: intent-filter `android.speech.RecognitionService`, `<meta-data android:name="android.speech" .../>`는 넣지 않는다.
- `assist_service.xml`: `sessionService`, `recognitionService`, `supportsAssist="true"`, `supportsLaunchVoiceAssistFromKeyguard="false"`. `settingsActivity`는 넣지 않는다.
- `FileProvider`: authority `dev.dhnam.screentranslator.files`, `file_paths.xml`에 `<cache-path name="shots" path="shots/"/>`
- `<queries>`: 두 패키지 `<package>`와 `<intent>` `ACTION_SEND` `image/*`
- `SettingsActivity`: 런처 Activity

- [ ] **Step 5: 확인용 최소 동작 구현**

- `AssistSession.onHandleScreenshot(screenshot: Bitmap?)`: `cacheDir/shots/`에 PNG를 저장한다. 그다음 `setPackage("com.naver.labs.translator")`로 `ACTION_SEND` 인텐트를 만들어 `context.startActivity`로 실행하고 `hide()`를 부른다. `null`이면 토스트만 띄운다.
- `StubRecognitionService.onStartListening`: `callback.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)`
- `SettingsActivity`: 버튼 하나. 누르면 Global Constraints에 적힌 순서대로 어시스턴트 설정 화면을 연다.

- [ ] **Step 6: 빌드**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`, `app/build/outputs/apk/debug/app-debug.apk` 생성

- [ ] **Step 7: 무선 디버깅으로 기기 연결 (사용자가 폰에서 조작)**

사용자가 폰에서 개발자 옵션 > 무선 디버깅 > "페어링 코드로 기기 페어링"을 열고, 화면에 나온 IP:포트와 코드를 알려준다.
Run: `adb pair <ip>:<pair-port> <code>` 후 `adb connect <ip>:<port>`, 이어서 `adb devices`
Expected: 기기가 `device` 상태로 표시

- [ ] **Step 8: 기기 정보 확인과 설치**

Run: `adb shell getprop ro.build.version.release`, `adb shell cmd package query-activities -a android.intent.action.SEND -t image/png | grep -A2 googlequicksearchbox`
결과를 기록한다(Android 버전, 구글 앱의 공유 Activity 이름).
Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
Expected: `Success`

- [ ] **Step 9: 기기에서 위험 요소 확인 (사용자와 함께)**

1. 설정 > 애플리케이션 > 기본 앱 선택 > 디지털 어시스턴트 앱 목록에 이 앱이 뜨는지 (위험 요소 1, 필수 등록 요건)
2. 이 앱을 선택하고, "화면의 텍스트 사용"과 "화면 이미지 사용"을 켠 뒤 메뉴 위치를 기록 (위험 요소 4)
3. 아무 화면에서 홈버튼을 길게 눌렀을 때 파파고 이미지 번역이 열리는지 (외부 앱 실행 여부, `setPackage`로 직접 보내기)
4. `adb shell settings get secure voice_recognition_service`의 값을 기록 (위험 요소 5, 부작용 범위)

결과를 설계서 7절에 "확인됨" 또는 "막힘: <내용>"으로 적는다. 외부 앱 실행이 막히면 여기서 멈추고 사용자와 상의한다(투명 Activity 방식).

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "feat: scaffold assistant app and verify on device"
```

---

### Task 2: ShotStore (캡처 저장과 정리)

**Files:**
- Create: `app/src/main/java/dev/dhnam/screentranslator/ShotStore.kt`
- Test: `app/src/test/java/dev/dhnam/screentranslator/ShotStoreTest.kt`

**Interfaces:**
- Produces:
  - `class ShotStore(dir: File, clock: () -> Long = System::currentTimeMillis)`
  - `fun save(write: (OutputStream) -> Unit): File`: `dir`가 없으면 만든다. `shot-<millis>.png`가 이미 있으면 `shot-<millis>-<n>.png`로 저장한다. `write`가 예외를 던지면 파일을 지우고 예외를 다시 던진다.
  - `fun purgeOlderThan(maxAgeMs: Long): Int`: `lastModified`가 `clock() - maxAgeMs`보다 오래된 `shot-*.png`를 지우고, 지운 개수를 돌려준다.
  - `companion object { const val MAX_AGE_MS = 10 * 60 * 1000L }`
  - Bitmap 압축은 호출하는 쪽에서 `write` 안에 `bitmap.compress(PNG, 100, it)`로 한다. 그래야 JVM에서 테스트할 수 있다.

- [ ] **Step 1: 실패하는 테스트 작성** (JUnit 4 `TemporaryFolder`, 시계는 `var now`로 조작)

```kotlin
@Test fun save_writesPngNamedByTimestamp() {
    now = 1_000L
    val f = store.save { it.write(byteArrayOf(1, 2, 3)) }
    assertEquals("shot-1000.png", f.name)
    assertArrayEquals(byteArrayOf(1, 2, 3), f.readBytes())
}
@Test fun save_twiceSameMillis_createsDistinctFiles() {
    now = 1_000L
    val a = store.save { it.write(1) }; val b = store.save { it.write(2) }
    assertNotEquals(a, b); assertTrue(a.exists() && b.exists())
}
@Test fun save_whenWriterThrows_leavesNoFile() {
    assertThrows(IOException::class.java) { store.save { throw IOException("full") } }
    assertEquals(0, dir.listFiles()!!.size)
}
@Test fun purge_deletesOnlyFilesOlderThanMaxAge() {
    // old.lastModified = now - MAX_AGE_MS - 1 → 삭제, fresh.lastModified = now - MAX_AGE_MS → 유지
    assertEquals(1, store.purgeOlderThan(ShotStore.MAX_AGE_MS))
    assertFalse(old.exists()); assertTrue(fresh.exists())
}
@Test fun purge_ignoresNonShotFiles()  // "other.txt"는 오래돼도 유지
@Test fun purge_whenDirMissing_returnsZero()
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :app:testDebugUnitTest --tests '*ShotStoreTest*'`
Expected: 컴파일 실패 (`ShotStore` 없음)

- [ ] **Step 3: `ShotStore` 구현**

- [ ] **Step 4: 테스트가 통과하는지 확인**

Run: 위와 같은 명령
Expected: 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/dhnam/screentranslator/ShotStore.kt app/src/test/java/dev/dhnam/screentranslator/ShotStoreTest.kt
git commit -m "feat: add ShotStore for temp screenshot files"
```

---

### Task 3: Prefs와 TargetResolver (번역 앱 설정과 공유 인텐트)

**Files:**
- Create: `app/src/main/java/dev/dhnam/screentranslator/Prefs.kt`, `TargetResolver.kt`
- Modify: `app/build.gradle.kts` (테스트 의존성 `org.robolectric:robolectric:4.13`, `testOptions.unitTests.isIncludeAndroidResources = true`)
- Test: `app/src/test/java/dev/dhnam/screentranslator/{PrefsTest, TargetResolverTest}.kt`

**Interfaces:**
- Produces:
  - `enum class Target(val pkg: String) { PAPAGO("com.naver.labs.translator"), GOOGLE("com.google.android.googlequicksearchbox") }`
  - `class Prefs(context: Context)`: `var target: Target`. `SharedPreferences` 파일 `settings`, 키 `target`, 기본값 `PAPAGO`. 저장된 값을 알 수 없으면 `PAPAGO`.
  - `class TargetResolver(pm: PackageManager)`
    - `fun buildIntent(target: Target, uri: Uri): Intent`: `ACTION_SEND`, `type="image/png"`, `EXTRA_STREAM=uri`, `clipData=ClipData.newRawUri("", uri)`, 플래그 `FLAG_GRANT_READ_URI_PERMISSION or FLAG_ACTIVITY_NEW_TASK`. `PAPAGO`이면 `setPackage`. `GOOGLE`이면 `pickLens(...)` 결과가 있으면 `setComponent`, 없으면 `setPackage`.
    - `fun finalIntent(intent: Intent): Intent`: `pm.resolveActivity(intent, 0)`이 null이면 `Intent.createChooser`로 감싼다. 이때 감싼 인텐트에도 같은 `clipData`와 두 플래그를 넣고 `setPackage`/`component`는 지운다.
  - `companion fun pickLens(candidates: List<ComponentName>): ComponentName?`: 클래스 이름에 `"lens"`가 들어간 첫 항목(대소문자 무시)을 고르고, 없으면 null. 순수 함수라 JVM에서 테스트한다.

- [ ] **Step 1: 실패하는 테스트 작성** (Robolectric `@RunWith(RobolectricTestRunner::class)`, 설치된 패키지는 `shadowOf(pm).installPackage`/`addActivityIfNotPresent` + `addIntentFilterForActivity`로 구성)

```kotlin
@Test fun prefs_default_isPapago() = assertEquals(Target.PAPAGO, Prefs(ctx).target)
@Test fun prefs_roundTrip() { Prefs(ctx).target = Target.GOOGLE; assertEquals(Target.GOOGLE, Prefs(ctx).target) }
@Test fun prefs_unknownStoredValue_fallsBackToPapago()

@Test fun papago_setsPackageTypeStreamClipAndFlags() {
    val i = resolver.buildIntent(Target.PAPAGO, uri)
    assertEquals(Intent.ACTION_SEND, i.action); assertEquals("image/png", i.type)
    assertEquals("com.naver.labs.translator", i.`package`)
    assertEquals(uri, i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    assertEquals(uri, i.clipData!!.getItemAt(0).uri)
    assertTrue(i.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    assertTrue(i.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
}
@Test fun google_prefersLensComponent()   // 두 공유 Activity 중 "...LensShareEntryPointActivity"를 component로
@Test fun google_noLens_fallsBackToPackage()
@Test fun pickLens_caseInsensitive_firstMatch()
@Test fun pickLens_empty_returnsNull()
@Test fun chooser_whenTargetMissing_keepsGrantAndClip() {
    val out = resolver.finalIntent(resolver.buildIntent(Target.PAPAGO, uri))  // 파파고 미설치
    assertEquals(Intent.ACTION_CHOOSER, out.action)
    assertEquals(uri, out.clipData!!.getItemAt(0).uri)
    assertTrue(out.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
}
@Test fun finalIntent_whenResolvable_returnsSameIntent()
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :app:testDebugUnitTest --tests '*PrefsTest*' --tests '*TargetResolverTest*'`
Expected: 컴파일 실패

- [ ] **Step 3: `Target`, `Prefs`, `TargetResolver` 구현**

구글 후보 목록은 `pm.queryIntentActivities(Intent(ACTION_SEND).setType("image/png").setPackage(GOOGLE.pkg), 0)`에서 가져온다.

- [ ] **Step 4: 테스트가 통과하는지 확인**

Run: 위와 같은 명령
Expected: 10 tests PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add Prefs and TargetResolver"
```

---

### Task 4: AssistSession 완성 (타이머, 오류 처리, 연결)

**Files:**
- Modify: `app/src/main/java/dev/dhnam/screentranslator/AssistSession.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `ShotStore.save`, `ShotStore.purgeOlderThan`, `ShotStore.MAX_AGE_MS`, `Prefs.target`, `TargetResolver.buildIntent`, `TargetResolver.finalIntent`, FileProvider authority `dev.dhnam.screentranslator.files`

- [ ] **Step 1: 동작 구현**

- `onShow(args, showFlags)`: 메인 `Handler`로 2초 타이머를 건다. 시간이 다 되면 `R.string.no_screenshot` 토스트를 띄우고 `hide()`.
- `onHandleScreenshot(screenshot: Bitmap?)`: 먼저 타이머를 취소하고 한 번만 처리하도록 플래그를 둔다. `null`이면 `no_screenshot` 토스트를 띄우고 `hide()`. 아니면 백그라운드 스레드에서 다음을 순서대로 한다.
  1. `purgeOlderThan(MAX_AGE_MS)`
  2. `save { screenshot.compress(PNG, 100, it) }`
  3. `FileProvider.getUriForFile`
  4. 메인 스레드로 돌아와 `finalIntent(buildIntent(prefs.target, uri))`를 `context.startActivity`로 실행하고 `hide()`

  `IOException`이 나면 `R.string.save_failed` 토스트를 띄우고 `hide()`.
- `onCreateContentView()`는 만들지 않는다(보이는 UI 없음).
- 문구:
  - `no_screenshot` = "이 화면은 캡처할 수 없거나, 어시스턴트 설정의 '화면의 텍스트 사용'과 '화면 이미지 사용'이 꺼져 있어요"
  - `save_failed` = "캡처를 저장하지 못했어요"

- [ ] **Step 2: 빌드와 단위 테스트**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: `BUILD SUCCESSFUL`, 기존 16개 테스트 통과

- [ ] **Step 3: 기기에서 확인**

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
1. 일반 화면에서 홈버튼 길게 누르기 → 파파고 이미지 번역 열림
2. 은행 앱 등 FLAG_SECURE 화면에서 실행 → `no_screenshot` 토스트, 앱이 죽지 않음 (Review Focus 1)
3. "화면 이미지 사용"을 끄고 실행 → 같은 토스트
4. 빠르게 두 번 실행 → 둘 다 정상 동작
5. `adb shell run-as dev.dhnam.screentranslator ls -l cache/shots` → 10분 넘은 파일이 다음 실행 때 사라짐

Run: `adb logcat -d | grep -i screentranslator | grep -iE 'exception|fatal'`
Expected: 출력 없음

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: complete assistant session flow"
```

---

### Task 5: SettingsActivity 완성

**Files:**
- Modify: `app/src/main/java/dev/dhnam/screentranslator/SettingsActivity.kt`
- Create: `app/src/main/res/layout/activity_settings.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `Prefs.target`, `Target`, `ShotStore.purgeOlderThan`, `ShotStore.MAX_AGE_MS`

- [ ] **Step 1: 화면 구현** (기본 `android.app.Activity`, 레이아웃 XML)

- `RadioGroup`: "파파고" / "구글 렌즈". `Prefs.target`에 바로 저장한다.
- 상태 문구: `getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)`가 참이면 "기본 어시스턴트로 설정됨", 아니면 "기본 어시스턴트로 설정되지 않음". `onResume`마다 갱신한다.
- 버튼 "어시스턴트 설정 열기": Global Constraints의 순서대로 연다. 첫 번째 인텐트가 `ActivityNotFoundException`을 던지면 두 번째로 넘어간다.
- 안내 문구 두 줄
  - "어시스턴트 설정에서 '화면의 텍스트 사용'과 '화면 이미지 사용'을 둘 다 켜주세요."
  - "이 앱을 어시스턴트로 쓰는 동안, 시스템 음성 인식을 쓰는 일부 앱에서는 음성 입력이 안 될 수 있어요."
- `onCreate`에서 백그라운드 스레드로 `ShotStore(File(cacheDir, "shots")).purgeOlderThan(MAX_AGE_MS)`를 실행한다.

- [ ] **Step 2: 빌드**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 기기에서 확인**

1. 앱 아이콘 → 설정 화면이 열리고, 상태가 "설정됨"으로 표시
2. 구글 렌즈를 선택 → 홈버튼을 길게 누르면 렌즈가 열림. 렌즈가 아닌 선택 창이 뜨면 Task 1 Step 8에서 기록한 Activity 이름과 `pickLens` 규칙을 다시 맞춘다.
3. 앱을 죽였다 다시 열어도 선택이 유지됨
4. 다시 파파고로 바꿔 동작 확인
5. 버튼 → 어시스턴트 설정 화면이 열림

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: settings screen for target app and assistant status"
```

---

### Task 6: 마무리

**Files:**
- Create: `README.md` (설치 방법, 어시스턴트 지정 방법, 두 설정 켜기, 알려진 부작용, 빌드 명령)
- Modify: `docs/superpowers/specs/2026-11-09-screen-translator-design.md` (7절의 확인 결과 최종 기록, 상태를 "구현됨"으로)

- [ ] **Step 1: 문서 작성**
- [ ] **Step 2: 전체 확인**

Run: `./gradlew clean testDebugUnitTest assembleDebug`
Expected: `BUILD SUCCESSFUL`, 16 tests PASS

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "docs: README and final verification notes"
```
