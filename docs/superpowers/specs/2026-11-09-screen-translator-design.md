# Screen Translator — 설계서

- 날짜: 2026-11-09
- 상태: 구현됨 (2026-11-09, 기기 확인 완료)

## 1. 목적

갤럭시 노트20에서 홈버튼을 길게 누르면 실행되는 **디지털 어시스턴트 앱**. 기능은 "지금 화면을 번역 앱으로 보내기" 하나뿐이다.
업데이트 후 구글 어시스턴트가 제미니로 통합되면서 사라진 "화면 이미지 번역" 흐름을 대신한다.

### 성공 기준

- 홈버튼을 길게 누르면 1~2초 안에 현재 화면이 **파파고 이미지 번역**으로 열린다.
- 캡처 이미지가 갤러리나 공용 저장소에 남지 않는다.
- 앱 아이콘을 눌러 여는 설정 화면에서 번역 앱을 **구글 렌즈**로 바꿀 수 있다.

### 범위 밖

- 앱 안에서 하는 OCR이나 번역, LLM 연동, 번역문을 화면 위에 덮어 그리는 기능
- 자르기나 미리보기 화면 (캡처는 화면 전체를 그대로 보낸다)
- 음성 입력 등 일반 어시스턴트 기능

## 2. 환경

- 기기: 갤럭시 노트20 (One UI, Android 13 예상, 설치 전에 확인)
- 파파고(`com.naver.labs.translator`)와 구글 앱(`com.google.android.googlequicksearchbox`)이 설치돼 있다고 사용자가 확인함
- 빌드: Linux PC에서 JDK 17, Android 명령줄 도구(`~/Android/Sdk`), Gradle Wrapper 사용. Android Studio는 쓰지 않음
- 언어: Kotlin. `minSdk 30`, `targetSdk 34`
- 설치: 같은 Wi-Fi에서 무선 디버깅(`adb pair` / `adb connect`), 안 되면 USB 케이블

## 3. 접근 방식

**선택한 방식: `VoiceInteractionService`로 등록하고 `VoiceInteractionSession.onHandleScreenshot(Bitmap)`으로 스크린샷을 받는다.**

- 기본 디지털 어시스턴트로 지정된 앱은 실행될 때마다 OS가 현재 화면의 스크린샷을 넘겨준다. 단, "화면 이미지 사용" 설정이 켜져 있어야 한다.
- 화면 녹화 권한(MediaProjection)도, 실행할 때마다 뜨는 허용 팝업도 필요 없다.

검토 후 제외한 방식:

- MediaProjection 캡처: 실행할 때마다 허용 팝업이 떠서 느리다.
- 접근성 서비스 `takeScreenshot`: 권한 범위가 너무 넓고, 홈버튼 길게 누르기와 따로 연결해야 한다.

## 4. 동작 흐름

1. 사용자가 홈버튼을 길게 누른다.
2. OS가 `AssistSession`을 띄우고 `onHandleScreenshot(bitmap)`을 호출한다.
3. `ShotStore`가 10분 넘은 이전 캡처를 지우고, bitmap을 `cacheDir/shots/shot-<timestamp>.png`로 저장한다. 파일은 바로 지우지 않는다. 번역 앱이 아직 읽는 중일 수 있어서 10분은 남겨 둔다.
4. `TargetResolver`가 설정값(파파고 또는 구글)을 읽어 공유 인텐트를 만든다.
   - `ACTION_SEND`, `type=image/png`, `EXTRA_STREAM`은 `FileProvider` URI
   - `clipData`에도 URI를 넣고 `FLAG_GRANT_READ_URI_PERMISSION`, `FLAG_ACTIVITY_NEW_TASK`를 붙인다.
   - 대상 지정
     - 파파고: `setPackage`로 지정
     - 구글: 패키지 하나에 공유 대상이 여러 개일 수 있다. 그래서 `queryIntentActivities`로 그 패키지에서 `ACTION_SEND image/*`를 받는 Activity 목록을 가져온다. 이름에 `Lens`가 들어간 것(예상: `LensShareEntryPointActivity`, 기기에서 확인)을 `setComponent`로 지정하고, 없으면 `setPackage`로 대체한다.
5. 세션에서 그 인텐트를 일반 `context.startActivity`로 실행한다. 이때 세션 창이 화면에 보이는 상태라 백그라운드 실행 제한에 걸리지 않을 것으로 본다. `startAssistantActivity`는 쓰지 않는다. 파파고가 어시스턴트 유형 Activity로 열리기 때문이다. 실행한 직후 `hide()`로 닫는다. 세션은 보이는 UI를 그리지 않는다.
6. `onShow`에서 타이머(약 2초)를 건다. 그 안에 스크린샷 콜백이 안 오면 안내 토스트를 띄우고 세션을 닫는다.

## 5. 구성 요소

| 단위 | 하는 일 | 의존 |
|---|---|---|
| `AssistService` (`VoiceInteractionService`) | 어시스턴트 목록에 뜨기 위한 등록용. 로직 없음 | — |
| `StubRecognitionService` (`RecognitionService`) | **필수** 등록용 껍데기. 호출되면 `ERROR_RECOGNIZER_BUSY`(또는 그에 맞는 오류)를 바로 돌려줌 | — |
| `AssistSessionService` / `AssistSession` | 스크린샷을 받아 저장하고, 공유한 뒤 닫음. 오류가 나면 안내 | `ShotStore`, `TargetResolver` |
| `ShotStore` | 캡처 PNG 저장, 오래된 파일 정리 (`saveBitmap`, `purgeOlderThan`) | 파일 시스템 (테스트에서 디렉터리를 주입) |
| `TargetResolver` | 설정값과 URI로 공유 인텐트 생성. 대상 앱이 받을 수 없으면 일반 공유 메뉴로 대체 | `PackageManager` |
| `Prefs` | 번역 앱 설정 저장. 값은 `PAPAGO`(기본) / `GOOGLE` | `SharedPreferences` |
| `SettingsActivity` (런처) | 번역 앱 선택, 기본 어시스턴트 상태 표시, 설정 화면으로 이동하는 버튼, "화면의 텍스트 사용"과 "화면 이미지 사용"을 둘 다 켜라는 안내. 열릴 때 `ShotStore.purgeOlderThan(10분)` 실행 | `Prefs`, `ShotStore` |

필수 매니페스트와 리소스:

- `BIND_VOICE_INTERACTION` 권한이 걸린 서비스 선언
- `res/xml/assist_service.xml` (`sessionService`, `recognitionService`, `supportsAssist="true"`, `supportsLaunchVoiceAssistFromKeyguard="false"`)
  - `recognitionService`와 `sessionService`는 **필수**다. 하나라도 빠지면 이 XML 해석이 실패하고, 앱이 어시스턴트 후보에서 빠진다.
  - `settingsActivity`는 넣지 않는다.
- `StubRecognitionService`: `android.speech.RecognitionService` 인텐트 필터와 `BIND_RECOGNITION_SERVICE`(또는 문서에 맞는 권한)로 선언
- `FileProvider` (`cache-path name="shots" path="shots/"`)
- Android 11 이상의 패키지 조회 제한 때문에 `<queries>`에 두 가지를 선언
  - 파파고와 구글 패키지
  - `ACTION_SEND` `image/*` `<intent>` (공유 메뉴로 대체할 때와 구글 Activity 조회에 필요)
- 기본 어시스턴트 지정은 `RoleManager`로 요청할 수 없다. 설정 화면으로 보내는 버튼만 둔다. `Settings.ACTION_VOICE_INPUT_SETTINGS`를 먼저 시도하고, 안 되면 `ACTION_MANAGE_DEFAULT_APPS_SETTINGS`를 연다. One UI 기준 위치는 설정 > 애플리케이션 > 기본 앱 선택 > 디지털 어시스턴트 앱.

### 알려진 부작용

이 앱을 기본 어시스턴트로 지정하면 시스템 기본 음성 인식기도 `StubRecognitionService`로 바뀔 수 있다. 그러면 `SpeechRecognizer` API로 음성 인식을 쓰는 일부 앱에서 음성 입력이 실패한다. Gboard나 삼성 키보드의 마이크처럼 자체 인식기를 쓰는 기능은 영향이 없다. 설정 화면에 한 줄로 안내한다.

## 6. 오류 처리

| 상황 | 동작 |
|---|---|
| `onHandleScreenshot`에 `null`이 들어오거나, 2초 안에 콜백이 안 옴 | 원인이 둘 중 하나다. 캡처를 막는 화면(FLAG_SECURE: 은행 앱, DRM 영상 등)이거나, 어시스턴트 설정("화면의 텍스트 사용", "화면 이미지 사용")이 꺼져 있다. "이 화면은 캡처할 수 없거나, 어시스턴트 설정의 '화면의 텍스트 사용'과 '화면 이미지 사용'이 꺼져 있어요" 토스트를 띄우고 세션을 닫는다. 설정 화면은 자동으로 열지 않는다. 설정 안내는 `SettingsActivity`에서만 한다. |
| 대상 앱이 없거나 이미지를 받지 못함 (`resolveActivity`가 `null`) | `Intent.createChooser`로 일반 공유 메뉴를 띄운다. 이때 대체 인텐트에도 `clipData`와 `FLAG_GRANT_READ_URI_PERMISSION`을 명시한다. |
| 파일 저장 실패 | 오류 토스트를 띄우고 세션을 닫는다. |
| 어시스턴트 세션에서 다른 앱 실행이 막힘 | 가능성은 낮다고 본다. 구현 첫 단계에서 확인하고, 막히면 투명 Activity를 거쳐 실행한다. |

## 7. 위험 요소와 먼저 확인할 것

1. ~~One UI에서 세션이 외부 앱을 띄울 수 있는지.~~ **확인됨 (2026-11-09, SM-N981N, Android 13).** 디지털 어시스턴트 앱 목록에 표시되고, 세션에서 `context.startActivity` + `setPackage`로 파파고 이미지 번역이 열렸다. 투명 Activity는 필요 없다.
2. ~~파파고가 `ACTION_SEND` 이미지를 받아 이미지 번역 화면으로 여는지.~~ **확인됨 (2026-11-09).** 사용자가 기기의 공유 메뉴로 이미지를 파파고에 보냈더니 바로 이미지 번역 화면이 열렸다. 남은 확인 사항은 공유 메뉴를 거치지 않고 `setPackage`로 직접 보낼 때도 같은 화면이 열리는지뿐이다.
3. **구글 앱에 이미지를 공유하면 렌즈로 열리는지.** 그리고 렌즈 공유 Activity의 실제 이름. `adb shell cmd package query-activities -a android.intent.action.SEND -t image/png`로 확인한다.
4. **"화면의 텍스트 사용"과 "화면 이미지 사용" 설정 위치.** 기기에서 확인한 뒤 설정 화면 안내 문구에 반영한다.
   - **확인 결과**: 구글 앱에서 `ACTION_SEND image/png`를 받는 Activity는 2개다. `com.google.android.apps.search.lens.LensShareEntryPointActivity`와 `...saves.savefeature.sharereceiver.SavesShareReceiverWithImageType`이다. 그래서 렌즈 컴포넌트를 직접 지정해야 한다.
5. ~~음성 인식 껍데기로 생기는 부작용의 실제 범위.~~ **확인됨.** 이 앱을 어시스턴트로 지정한 뒤에도 `voice_recognition_service`는 Google TTS 인식기 그대로였다. 이 기기에서는 부작용이 없다.

검토 메모: 위 1~5 중 일부 판단은 AOSP 소스 지식을 근거로 했고, 문서를 직접 확인하지는 않았다. 첫 시험 앱에서 실제 동작으로 확정한다.

구현 계획의 첫 작업은 위 1~3을 확인하는 최소 앱(어시스턴트 등록, 스크린샷 수신, 파파고로 공유)을 기기에 설치해 보는 것이다.

## 8. 테스트

- **JVM 단위 테스트**
  - `ShotStore`: 저장 경로와 파일 이름, `purgeOlderThan` 경계값
  - `TargetResolver`: 설정값별 인텐트(패키지 또는 컴포넌트, 타입, `clipData`, 플래그, 구글 렌즈 Activity 선택 규칙, 공유 메뉴 대체). 필요하면 Robolectric 사용
- **기기 수동 테스트**
  - 홈버튼을 길게 눌렀을 때 파파고, 구글 렌즈 각각에서 번역이 열리는지
  - "화면 이미지 사용"을 껐을 때 안내가 뜨는지
  - 캐시 폴더 정리 (`adb shell run-as ... ls cache/shots`)
  - 갤러리에 캡처가 남지 않는지

## 9. 프로젝트 구조

```
screen-translator/
  settings.gradle.kts, build.gradle.kts, gradle/ (wrapper)
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/java/.../{AssistService, AssistSessionService, AssistSession,
                       StubRecognitionService, ShotStore, TargetResolver,
                       Prefs, SettingsActivity}.kt
    src/main/res/xml/{assist_service.xml, file_paths.xml}
    src/test/java/.../{ShotStoreTest, TargetResolverTest}.kt
  docs/superpowers/specs/
```

- 패키지 이름: `dev.dhnam.screentranslator`

## 10. 구현 중 바뀐 점

- 안내는 토스트로 띄운다. One UI는 알림 권한이 없고 화면 앞에 있지 않은 앱의 토스트를 막는다. 그래서 `POST_NOTIFICATIONS`를 선언하고 `SettingsActivity`에서 요청한다. 세션 창 안에 안내를 띄우는 방식도 시험했지만, 떠 있는 동안 화면 입력을 막아서 쓰지 않는다.
- "화면 이미지 사용"이 꺼져 있으면 `onHandleScreenshot`에 `null`이 오는 게 아니라 아예 호출되지 않는다. 그래서 2초 타이머가 안내를 맡는다.
- `FileProvider`는 AndroidX(`androidx.core`)에만 있어서 이 의존성 하나는 추가했다.
- 파파고는 EXIF를 읽을 때 `?requireOriginal=1`을 붙인 URI로 접근하는데, 이 접근은 권한 거부된다. 번역에는 영향이 없다.

## 11. 번역 앱 목록 (v0.2.0 추가, 2026-11-09)

### 목적

번역 앱을 파파고와 구글 렌즈 두 개로 고정하지 않고, 이미지를 받을 수 있는 아무 앱이나 목록에 추가하거나 제거할 수 있게 한다. 홈버튼을 길게 누르면 지금처럼 **기본 앱 하나로 바로** 보낸다.

### 화면

- 설정 화면의 "번역 앱" 영역은 목록이다. 한 줄은 `[라디오] [아이콘] 이름 [✕]`이다.
  - 줄을 누르면 그 앱이 기본 앱이 된다.
  - ✕를 누르면 목록에서 제거한다.
- 목록 아래에 "+ 앱 추가" 버튼이 있다. 누르면 이미지를 받을 수 있는 화면 목록이 스크롤되는 선택 창(`AlertDialog`)으로 뜬다. 각 항목은 아이콘과 이름이다.
  - 이미 목록에 있는 화면과 이 앱 자신은 선택 창에서 빠진다.
  - 한 패키지에 공유 화면이 여러 개면 화면마다 따로 나온다. 이름은 화면 이름(라벨)을 쓰고, 화면 이름이 앱 이름과 다르면 "앱 이름 · 화면 이름"으로 표시한다.
- 설치되지 않았거나 비활성화된 화면은 회색으로 "(설치 안 됨)"을 붙여 표시하고, 기본 앱으로 고를 수 없다. 제거는 할 수 있다.
- "여는 중…" 토스트 문구는 "%s 여는 중…"이고, %s에는 기본 앱의 표시 이름이 들어간다.

### 규칙

- 번역 앱 하나는 `ComponentName`(패키지 + Activity)으로 식별한다.
- 같은 화면은 두 번 추가되지 않는다. 추가한 화면은 목록 맨 끝에 붙는다.
- 목록이 비어 있지 않으면 기본 앱은 항상 목록 안에 있다.
  - 기본 앱을 제거하면 남은 목록의 첫 번째가 기본 앱이 된다.
  - 기본 앱이 없는 상태에서 앱을 추가하면 그 앱이 기본 앱이 된다.
- 목록이 비었거나 기본 앱이 설치돼 있지 않으면, 홈버튼을 눌렀을 때 일반 공유 메뉴를 띄운다(기존 `finalIntent` 동작).
- **처음 설치**: 목록은 [파파고 `com.naver.labs.translator/.ui.main.DeepLinkActivity`, Google 렌즈 `com.google.android.googlequicksearchbox/com.google.android.apps.search.lens.LensShareEntryPointActivity`]이고, 기본 앱은 파파고다.
- **v0.1.0에서 업데이트**: 예전 키 `target`이 있으면 같은 기본 목록을 만들고, 값이 `GOOGLE`이면 Google 렌즈를, 그 밖의 값이면 파파고를 기본 앱으로 정한다. 그다음 예전 키는 지운다.

### 저장

- `SharedPreferences` 파일 `settings`를 쓴다.
  - `targets`: `ComponentName.flattenToString()` 값들을 줄바꿈으로 이어 붙인 문자열(순서 유지)
  - `default_target`: 기본 앱의 `flattenToString()` 값
- 예전 키 `target`은 위의 이어받기 규칙에 따라 한 번만 읽는다.

### 구성 요소 변경

| 단위 | 변경 |
|---|---|
| `Target` enum | 제거 |
| `TargetList` (새로 추가) | 목록과 기본 앱의 순수 로직: `add`, `remove`, `setDefault`, 기본값, 예전 설정 이어받기. 문자열 목록만 다뤄서 JVM에서 테스트할 수 있다. |
| `Prefs` | `targetList` 읽기/쓰기(예전 설정 이어받기 포함). `showOpeningToast`는 그대로 |
| `TargetResolver` | `shareTargets()`: 이미지를 받는 화면 목록(이 앱 제외). `isAvailable(component)`. `label(component)`. `buildIntent(component, uri)`: `setComponent`. `finalIntent` 그대로. `pickLens` 제거 |
| `SettingsActivity` | 목록 UI, 추가 선택 창 |
| `AssistSession` | 기본 앱 `ComponentName`으로 공유. 목록이 비었으면 공유 메뉴 |

### 테스트

- `TargetList` (JVM): 처음 설치 기본값, 추가, 중복 추가 무시, 제거, 기본 앱을 제거하면 첫 번째가 기본이 됨, 모두 제거하면 빈 목록과 기본 앱 없음, 빈 목록에 추가하면 기본 앱이 됨, 목록에 없는 앱은 기본으로 지정되지 않음, 예전 `GOOGLE`/`PAPAGO`/알 수 없는 값 이어받기
- `Prefs` (Robolectric): 저장 후 다시 읽기, 예전 키를 이어받은 뒤 그 키가 지워지는지
- `TargetResolver` (Robolectric): `shareTargets`에서 이 앱이 빠지는지, `buildIntent`가 컴포넌트를 정확히 지정하는지, 설치 안 된 컴포넌트를 `isAvailable`이 false로 판별하는지, `finalIntent`가 그대로 동작하는지
- 기기(dev 앱): DeepL을 추가해 기본 앱으로 지정하고 보내기, 제거, 기본 앱 제거 시 다음 앱으로 넘어가는지, 목록을 다 비웠을 때 공유 메뉴가 뜨는지

## 12. 업데이트 확인과 설치 (2026-11-09)

### 목적

앱이 GitHub Releases에서 새 버전을 직접 확인하고, 사용자가 누르면 받아서 설치까지 한다. APK 파일은 폰에 남지 않는다.

### 화면

- 설정 화면 맨 아래에 "업데이트" 영역을 둔다. 상태 한 줄과 버튼 하나로 구성한다.

| 상태 | 문구 | 버튼 |
|---|---|---|
| 최신 | 최신 버전이에요 (v현재) | 지금 확인 |
| 새 버전 있음 | 새 버전 v최신이 있어요 | 업데이트 |
| 확인 중 | 확인 중… | (비활성) |
| 받는 중 | 받는 중… N% | (비활성) |
| 확인 실패 | 업데이트를 확인하지 못했어요 | 다시 시도 |
| dev 빌드 | 개발용 앱은 업데이트 확인을 하지 않아요 | (없음) |

- 설정 화면이 보일 때(`onResume`) 마지막 확인에서 24시간이 지났으면 자동으로 확인한다. 마지막 결과는 저장해 두고, 화면을 열면 먼저 그 결과를 보여 준다.
- [업데이트]를 눌렀을 때 `packageManager.canRequestPackageInstalls()`가 false면 안내 창("업데이트를 설치하려면 이 앱이 앱을 설치할 수 있도록 허용해야 해요")을 띄우고 `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`(`package:<패키지>`)로 보낸다.

### 흐름

1. **조회**: `GET https://api.github.com/repos/dhnam/screen-translator/releases/latest`
   - 헤더는 `Accept: application/vnd.github+json`
   - 연결과 응답 대기 시간은 각 10초
   - 응답에서 `tag_name`을 읽고, `assets` 중 이름이 `screen-translator-`로 시작하고 `.apk`로 끝나는 첫 항목의 `browser_download_url`, `size`, `digest`를 읽는다. `digest`는 `sha256:<hex>` 형식이고 없을 수 있다.
   - APK 첨부가 없으면 "새 버전 없음"으로 본다.
2. **비교**: 태그와 현재 `versionName`을 `Version`으로 해석해 비교한다.
   - 앞의 `v`는 무시하고, 점으로 나눈 숫자를 차례로 비교한다. 빠진 자리는 0으로 본다.
   - 해석할 수 없으면 새 버전이 아닌 것으로 본다.
3. **받기와 설치**: `PackageInstaller` 세션을 만든다(`MODE_FULL_INSTALL`, `appPackageName`은 이 앱).
   - APK를 받으면서 세션 출력 스트림에 쓰고, 동시에 SHA-256을 계산한다. 진행률은 `size` 기준이다.
   - `digest`가 있고 계산한 값과 다르면 세션을 `abandon`하고 "받은 파일이 손상됐어요"를 안내한다.
   - 맞으면 `commit`한다. 결과는 `InstallResultReceiver`로 받는다.
     - `STATUS_PENDING_USER_ACTION`: 함께 온 확인 인텐트를 실행한다.
     - `STATUS_SUCCESS`: 앱이 새 버전으로 다시 시작된다.
     - 그 밖: "업데이트를 설치하지 못했어요 (<메시지>)" 토스트
4. **보안**: Android는 서명이 다른 APK로 이 앱을 업데이트하지 않는다. 해시 확인은 전송 중 손상을 잡기 위한 추가 장치다. 통신은 HTTPS만 쓴다.

### 저장

`settings` 파일에 다섯 가지를 저장한다. `update_checked_at`(마지막 확인 시각, ms), `update_tag`, `update_url`, `update_size`, `update_sha256`. 마지막 확인에서 새 버전이 없었으면 tag/url/size/sha256은 지운다.

### 구성 요소

| 단위 | 하는 일 |
|---|---|
| `Version` | 버전 해석과 비교, 24시간 자동 확인 판단(`shouldAutoCheck`). 순수 로직 |
| `ReleaseInfo` | `tag`, `url`, `size`, `sha256?`. GitHub 응답 JSON을 해석한다(`ReleaseInfo.parse`) |
| `UpdateChecker` | 조회와 비교 |
| `UpdateInstaller` | 받기, 해시 확인, 설치 세션 |
| `HashingCopy` | 스트림을 복사하면서 SHA-256과 진행률을 계산한다. 순수 로직 |
| `InstallResultReceiver` | 설치 결과 처리 |
| `Prefs` | 마지막 확인 시각과 마지막 결과 |

- 매니페스트: `INTERNET`, `REQUEST_INSTALL_PACKAGES`, `InstallResultReceiver`(exported=false)
- 빌드: `buildConfig` 사용. `BuildConfig.UPDATE_ENABLED`는 release에서 true, debug에서 false

### 테스트

- **JVM**
  - `Version`: `v0.2.0` == `0.2.0`, `0.10.0` > `0.9.0`, `0.2` == `0.2.0`, `1.0.0` > `0.99.99`, 해석 불가는 null
  - `isNewer`: 해석 불가면 false
  - `shouldAutoCheck`: 처음(0), 24시간 미만, 이상, 시계가 거꾸로 간 경우
  - `HashingCopy`: 알려진 입력의 SHA-256, 진행률 콜백 마지막 값이 전체 크기
- **Robolectric**: `ReleaseInfo.parse` — 정상, APK 첨부 없음 → null, `digest` 없음 → sha256 null, 다른 이름의 첨부만 있음 → null
- **기기**: 같은 키로 서명하고 versionName만 `0.1.9`(versionCode는 v0.2.0과 같게)로 낮춘 릴리스 빌드를 업데이트로 설치한다. 그다음 아래를 확인한다.
  1. "새 버전 v0.2.0이 있어요"가 표시되는지
  2. 권한 안내가 뜨고 허용 화면으로 가는지
  3. 받는 중 진행률이 보이는지
  4. 설치 확인 창이 뜨고, 설치 후 v0.2.0으로 돌아오는지
  5. 앱 캐시에 APK가 남지 않는지
