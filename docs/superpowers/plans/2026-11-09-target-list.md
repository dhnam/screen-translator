# Target List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 고정된 파파고/구글 선택을, 추가·제거할 수 있는 번역 앱 목록과 기본 앱 하나로 바꾼다.

**Architecture:** 목록 규칙은 문자열(`ComponentName.flattenToString()`)만 다루는 순수 Kotlin `TargetList`에 둔다. `Prefs`가 저장과 예전 설정 이어받기를 맡고, `TargetResolver`가 PackageManager 조회(화면 목록, 이름, 설치 여부)와 인텐트 생성을 맡는다. `SettingsActivity`는 목록 UI를 코드로 그린다.

**Tech Stack:** Kotlin, Android SDK 34, JUnit 4, Robolectric 4.13 (기존과 같음)

**Spec:** `docs/superpowers/specs/2026-11-09-screen-translator-design.md` 11절

## Global Constraints

- 파파고 컴포넌트: `com.naver.labs.translator/com.naver.labs.translator.ui.main.DeepLinkActivity`
- Google 렌즈 컴포넌트: `com.google.android.googlequicksearchbox/com.google.android.apps.search.lens.LensShareEntryPointActivity`
- 처음 설치: 목록 [파파고, Google 렌즈], 기본 앱 파파고
- `SharedPreferences` 파일 `settings`, 키 `targets`(줄바꿈으로 이은 목록), `default_target`, 예전 키 `target`(이어받은 뒤 지움), `opening_toast`(그대로)
- 공유 타입 `image/png`
- "여는 중…" 문구: `%s 여는 중…`
- 화면 이름이 앱 이름과 다르면 표시 이름은 `앱 이름 · 화면 이름`
- 개발 중 시험은 dev 앱(`dev.dhnam.screentranslator.dev`)으로 한다. 이 계획에서는 릴리스하지 않는다.
- 새 의존성은 추가하지 않는다.

## Review Focus

1. **예전 v0.1.0 설정이 있는 상태에서 업데이트**: 구글 렌즈를 쓰던 사용자는 업데이트 후에도 렌즈가 기본 앱이어야 한다. 테스트: Task 2 `prefs_migratesOldGoogle_andRemovesOldKey`.
2. **저장된 목록이 손상됨**(빈 줄, 기본 앱이 목록에 없음): 기본 앱은 항상 목록 안에 있어야 한다. 테스트: Task 1 `decode_normalizesBlankLinesAndStrayDefault`.
3. **목록을 모두 지운 뒤 홈버튼**: 앱이 죽지 않고 공유 메뉴가 떠야 한다. 테스트: Task 2 `finalIntent_withoutComponent_isChooser`.
4. **기본 앱이 지워진 뒤**: 설정 화면에 회색으로 나오고, 홈버튼을 누르면 공유 메뉴가 떠야 한다. 테스트: Task 2 `isAvailable_uninstalled_isFalse` + 기존 `chooser_whenTargetMissing_keepsGrantAndClip`.
5. **이 앱 자신이 추가 목록에 나옴**(dev와 릴리스가 서로를 보는 경우 포함): 자기 패키지는 빠져야 한다. dev 앱 입장에서 릴리스 앱은 이미지를 받지 않으므로 목록에 나오지 않는다. 테스트: Task 2 `shareTargets_excludesSelf`.

---

### Task 1: TargetList (목록 규칙)

**Files:**
- Create: `app/src/main/java/dev/dhnam/screentranslator/TargetList.kt`
- Test: `app/src/test/java/dev/dhnam/screentranslator/TargetListTest.kt`

**Interfaces:**
- Produces:
  - `data class TargetList(val items: List<String>, val default: String?)`
    - `fun add(c: String): TargetList`: 이미 있으면 그대로. 끝에 붙인다. `default`가 null이면 `c`가 기본이 된다.
    - `fun remove(c: String): TargetList`: 기본이었으면 남은 첫 번째(없으면 null)가 기본이 된다.
    - `fun setDefault(c: String): TargetList`: 목록에 없으면 그대로.
    - `fun encodeItems(): String`: `items.joinToString("\n")`
  - `companion object`
    - `const val PAPAGO`, `const val LENS`: Global Constraints의 컴포넌트 문자열
    - `val INITIAL = TargetList(listOf(PAPAGO, LENS), PAPAGO)`
    - `fun decode(items: String?, default: String?): TargetList`: 빈 줄과 중복을 버린다. 기본 앱이 목록에 없으면 첫 번째(없으면 null)로 고친다.
    - `fun migrate(oldTarget: String?): TargetList`: `"GOOGLE"`이면 `INITIAL.setDefault(LENS)`, 그 밖에는 `INITIAL`

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
@Test fun initial_hasPapagoAndLens_defaultPapago()  // INITIAL.items == [PAPAGO, LENS], default == PAPAGO
@Test fun add_appendsAndIgnoresDuplicate()          // INITIAL.add("a/b").items == [PAPAGO, LENS, "a/b"]; .add("a/b") 결과 동일
@Test fun add_toEmpty_becomesDefault()              // TargetList(emptyList(), null).add("a/b").default == "a/b"
@Test fun remove_default_fallsBackToFirst()         // INITIAL.remove(PAPAGO) → items [LENS], default LENS
@Test fun remove_all_leavesEmptyAndNoDefault()      // remove(PAPAGO).remove(LENS) → items [], default null
@Test fun setDefault_unknown_isIgnored()            // INITIAL.setDefault("x/y").default == PAPAGO
@Test fun decode_roundTrip()                        // decode(l.encodeItems(), l.default) == l
@Test fun decode_normalizesBlankLinesAndStrayDefault()  // decode("\na/b\n\na/b\nc/d", "z/z") == TargetList([a/b, c/d], "a/b")
@Test fun decode_null_isEmpty()                     // decode(null, null) == TargetList(emptyList(), null)
@Test fun migrate_google_defaultsToLens()
@Test fun migrate_papagoOrUnknownOrNull_defaultsToPapago()
```

- [ ] **Step 2: 실패 확인** — Run: `./gradlew :app:testDebugUnitTest --tests '*TargetListTest*'` / Expected: 컴파일 실패
- [ ] **Step 3: `TargetList` 구현**
- [ ] **Step 4: 통과 확인** — 같은 명령 / Expected: 11 tests PASS
- [ ] **Step 5: Commit** — `feat: add TargetList rules for translation app list`

---

### Task 2: Prefs·TargetResolver·AssistSession 전환

**Files:**
- Modify: `Prefs.kt`(`Target` enum과 `target` 속성 제거), `TargetResolver.kt`, `AssistSession.kt`, `SettingsActivity.kt`(라디오 버튼 연결 코드만 제거, 목록 UI는 Task 3), `res/layout/activity_settings.xml`(RadioGroup을 빈 `LinearLayout` `@+id/targets`로 교체), `res/values/strings.xml`(`opening_papago`, `opening_google`, `target_papago`, `target_google` 제거, `opening_format` = `%1$s 여는 중…` 추가)
- Test: `PrefsTest.kt`, `TargetResolverTest.kt` 수정

**Interfaces:**
- Consumes: `TargetList`, `TargetList.decode`, `TargetList.migrate`, `TargetList.INITIAL`
- Produces:
  - `Prefs.targetList: TargetList`
    - get: `targets` 키가 있으면 `decode(targets, default_target)`.
    - 없고 예전 키 `target`이 있으면 `migrate(target)`을 저장하고 `target`을 지운 뒤 돌려준다.
    - 둘 다 없으면 `INITIAL`.
  - `Prefs.targetList` set: `targets`, `default_target`에 저장
  - `class TargetResolver(pm: PackageManager, selfPackage: String)`
    - `fun shareTargets(): List<ComponentName>`: `SEND image/png`을 받는 화면 목록. `selfPackage`와 그 `.dev` 변형은 뺀다(패키지 이름이 `selfPackage`로 시작하는 것 모두).
    - `fun isAvailable(c: ComponentName): Boolean`: 컴포넌트를 지정한 `SEND image/png` 조회 결과가 비어 있지 않으면 true
    - `fun label(c: ComponentName): String`: 화면 라벨과 앱 라벨이 같으면 하나만, 다르면 `앱 · 화면`. 조회에 실패하면 패키지 이름
    - `fun icon(c: ComponentName): Drawable?`
    - `fun buildIntent(c: ComponentName?, uri: Uri): Intent`: 기존 `baseIntent`에 `component = c`
    - `fun finalIntent(intent: Intent): Intent`: 컴포넌트와 패키지가 둘 다 없거나 `resolveActivity`가 null이면 공유 메뉴로 감싼다(기존 감싸기 규칙 그대로).
  - `AssistSession`: 기본 앱은 `prefs.targetList.default?.let(ComponentName::unflattenFromString)`, 토스트는 `getString(R.string.opening_format, resolver.label(c))`. 목록이 비었으면 토스트 없이 공유 메뉴를 띄운다.

- [ ] **Step 1: 테스트 수정과 추가** (기존 `Target`·`pickLens` 테스트는 지운다)

```kotlin
// PrefsTest
@Test fun prefs_freshInstall_isInitial()
@Test fun prefs_roundTrip()                          // INITIAL.add("a/a.B")를 저장 → 다시 읽으면 같다
@Test fun prefs_migratesOldGoogle_andRemovesOldKey() // putString("target","GOOGLE") → default == LENS, sp.contains("target") == false
@Test fun openingToast_default_isOff(); openingToast_roundTrip()   // 그대로 유지

// TargetResolverTest (resolver = TargetResolver(pm, "dev.dhnam.screentranslator"))
@Test fun buildIntent_setsComponentTypeStreamClipAndFlags()
@Test fun shareTargets_excludesSelf()                // papago, lens, "dev.dhnam.screentranslator.dev/...Main" 설치 → 결과에 dev 없음
@Test fun isAvailable_installed_isTrue(); isAvailable_uninstalled_isFalse()
@Test fun label_activityDiffersFromApp_joinsWithDot()  // 앱 라벨 "Google", 화면 라벨 "Lens" → "Google · Lens"
@Test fun finalIntent_withoutComponent_isChooser()
@Test fun chooser_whenTargetMissing_keepsGrantAndClip(); finalIntent_whenResolvable_returnsSameIntent()  // 그대로 유지
```

- [ ] **Step 2: 실패 확인** — Run: `./gradlew :app:testDebugUnitTest` / Expected: 컴파일 실패
- [ ] **Step 3: 구현** (Interfaces 그대로)
- [ ] **Step 4: 통과 확인** — Run: `./gradlew testDebugUnitTest assembleDebug` / Expected: `BUILD SUCCESSFUL`, 모든 테스트 PASS
- [ ] **Step 5: Commit** — `refactor: component-based targets with migration from v0.1.0`

---

### Task 3: 설정 화면 목록 UI

**Files:**
- Modify: `SettingsActivity.kt`, `res/values/strings.xml`(`add_target` = "+ 앱 추가", `add_target_title` = "추가할 앱", `not_installed` = "(설치 안 됨)", `remove_target` = "제거", `no_more_targets` = "추가할 수 있는 앱이 없어요")
- Modify: `res/layout/activity_settings.xml`(`@+id/targets` 아래에 `Button @+id/add_target`)

**Interfaces:**
- Consumes: `Prefs.targetList`, `TargetList.add/remove/setDefault`, `TargetResolver.shareTargets/isAvailable/label/icon`

- [ ] **Step 1: 구현**
  - `renderTargets()`: `@+id/targets`를 비우고 목록의 각 항목마다 한 줄을 만든다. 한 줄은 가로 `LinearLayout`이고, 그 안에 `RadioButton`(기본 앱이면 체크), 32dp 아이콘, 이름, ✕ `ImageButton`(`android.R.drawable.ic_menu_close_clear_cancel`, contentDescription `remove_target`)을 둔다.
    - 설치되지 않은 항목은 `alpha = 0.4f`로 흐리게 하고, 이름 뒤에 `not_installed`를 붙이고, 줄을 눌러도 기본 앱으로 지정되지 않는다.
    - 줄을 누르면 `setDefault` → 저장 → 다시 그린다.
    - ✕를 누르면 `remove` → 저장 → 다시 그린다.
  - `+ 앱 추가` 버튼: `shareTargets()`에서 이미 목록에 있는 항목을 뺀 뒤, 아이콘과 이름이 있는 `ArrayAdapter`로 `AlertDialog.setAdapter`를 띄운다. 항목을 고르면 `add` → 저장 → 다시 그린다. 고를 것이 없으면 `no_more_targets` 토스트를 띄운다.
  - `onResume`에서도 `renderTargets()`를 호출한다(다른 앱을 설치하거나 지운 뒤 돌아온 경우).
- [ ] **Step 2: 빌드** — Run: `./gradlew testDebugUnitTest assembleDebug` / Expected: `BUILD SUCCESSFUL`
- [ ] **Step 3: 기기 확인 (dev 앱, 사용자와 함께)**
  1. 처음 열었을 때 [파파고(기본), Google 렌즈]가 보인다.
  2. "+ 앱 추가"에서 DeepL을 추가하고 기본 앱으로 지정 → 홈버튼 길게 누르기 → DeepL이 열린다.
  3. "여는 중…" 옵션을 켜면 "DeepL 여는 중…"이 뜬다.
  4. 기본 앱(DeepL)을 ✕로 제거 → 파파고가 기본 앱이 된다.
  5. 모두 제거 → 홈버튼 길게 누르기 → 공유 메뉴가 뜨고 앱이 죽지 않는다.
  6. 추가 목록에 "화면 번역"과 "화면 번역 (dev)"가 없다.
  7. `adb logcat -d | grep -iE 'AndroidRuntime|FATAL'`에 이 앱이 없다.
- [ ] **Step 4: Commit** — `feat: editable translation app list in settings`

---

### Task 4: 문서

**Files:**
- Modify: `README.md`(설치와 설정 5번: "번역 앱 목록에서 기본 앱을 고르고, 필요하면 + 앱 추가로 다른 앱을 넣습니다", 구조 표의 `TargetList` 추가), `TODO.md`("다른 번역 앱 등록", "선택한 번역 앱이 없을 때"를 완료로 표시하고 "다음 릴리스에 포함"을 붙임)

- [ ] **Step 1: 문서 수정**
- [ ] **Step 2: 전체 확인** — Run: `./gradlew clean testDebugUnitTest assembleDebug` / Expected: `BUILD SUCCESSFUL`
- [ ] **Step 3: Commit and push** — `docs: translation app list` → `git push origin master` (CI 통과 확인. 태그는 만들지 않음)
