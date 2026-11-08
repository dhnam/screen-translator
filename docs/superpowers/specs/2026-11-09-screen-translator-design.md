# Screen Translator — 설계서

- 날짜: 2026-11-09
- 상태: 검토 대기

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
3. `ShotStore`가 10분 넘은 이전 캡처를 지우고, bitmap을 `cacheDir/shots/shot-<timestamp>.png`로 저장한다.
4. `TargetResolver`가 설정값(파파고 또는 구글)을 읽어 공유 인텐트를 만든다.
   - `ACTION_SEND`, `type=image/png`, `EXTRA_STREAM`은 `FileProvider` URI
   - `FLAG_GRANT_READ_URI_PERMISSION`, `FLAG_ACTIVITY_NEW_TASK`를 붙이고 `setPackage(<대상 패키지>)` 지정
5. 세션에서 그 인텐트를 실행한 뒤 `hide()` / `finish()`로 바로 닫는다. 세션은 보이는 UI를 그리지 않는다.

## 5. 구성 요소

| 단위 | 하는 일 | 의존 |
|---|---|---|
| `AssistService` (`VoiceInteractionService`) | 어시스턴트 목록에 뜨기 위한 등록용. 로직 없음 | — |
| `AssistSessionService` / `AssistSession` | 스크린샷을 받아 저장하고, 공유한 뒤 닫음. 오류가 나면 안내 | `ShotStore`, `TargetResolver` |
| `ShotStore` | 캡처 PNG 저장, 오래된 파일 정리 (`saveBitmap`, `purgeOlderThan`) | 파일 시스템 (테스트에서 디렉터리를 주입) |
| `TargetResolver` | 설정값과 URI로 공유 인텐트 생성. 대상 앱이 받을 수 없으면 일반 공유 메뉴로 대체 | `PackageManager` |
| `Prefs` | 번역 앱 설정 저장. 값은 `PAPAGO`(기본) / `GOOGLE` | `SharedPreferences` |
| `SettingsActivity` (런처) | 번역 앱 선택, 기본 어시스턴트 상태 표시, 설정 화면으로 이동하는 버튼 | `Prefs` |

필수 매니페스트와 리소스:

- `BIND_VOICE_INTERACTION` 권한이 걸린 서비스 선언
- `res/xml/assist_service.xml` (`sessionService`, `recognitionService`, `supportsAssist="true"`, `supportsLaunchVoiceAssistFromKeyguard="false"`)
- 목록에 뜨려면 최소한의 `RecognitionService` 껍데기가 필요할 수 있음. 구현할 때 확인
- `FileProvider` (`cache-path name="shots" path="shots/"`)
- Android 11 이상의 패키지 조회 제한 때문에 파파고와 구글 패키지를 `<queries>`에 선언

## 6. 오류 처리

| 상황 | 동작 |
|---|---|
| `onHandleScreenshot`에 `null`이 들어옴 ("화면 이미지 사용"이 꺼져 있음) | "설정에서 '화면 이미지 사용'을 켜주세요" 토스트를 띄우고 어시스턴트 설정 화면(`Settings.ACTION_VOICE_INPUT_SETTINGS`)을 연 뒤 세션 종료 |
| 대상 앱이 없거나 이미지를 받지 못함 (`resolveActivity`가 `null`) | `Intent.createChooser`로 일반 공유 메뉴를 띄움 |
| 파일 저장 실패 | 오류 토스트를 띄우고 세션 종료 |
| 어시스턴트 세션에서 다른 앱 실행이 막힘 | 구현 첫 단계에서 실험으로 확인. 막히면 투명 Activity를 거쳐 실행 |

## 7. 위험 요소와 먼저 확인할 것

1. **One UI에서 세션이 외부 앱을 띄울 수 있는지.** 막히면 투명 Activity를 거쳐 실행한다.
2. **파파고가 `ACTION_SEND` 이미지를 받아 이미지 번역 화면으로 여는지.** 안 되면 파파고 쪽 이미지 공유 진입점을 조사하고, 그래도 없으면 구글 렌즈를 기본값으로 쓰자고 제안한다.
3. **구글 앱에 이미지를 공유하면 렌즈로 열리는지.**
4. **"화면 이미지 사용" 설정 위치.** 기기에서 확인한 뒤 설정 화면 안내 문구에 반영한다.

구현 계획의 첫 작업은 위 1~3을 확인하는 최소 앱(어시스턴트 등록, 스크린샷 수신, 파파고로 공유)을 기기에 설치해 보는 것이다.

## 8. 테스트

- **JVM 단위 테스트**
  - `ShotStore`: 저장 경로와 파일 이름, `purgeOlderThan` 경계값
  - `TargetResolver`: 설정값별 인텐트(패키지, 타입, 플래그, 대체 동작). 필요하면 Robolectric 사용
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
                       ShotStore, TargetResolver, Prefs, SettingsActivity}.kt
    src/main/res/xml/{assist_service.xml, file_paths.xml}
    src/test/java/.../{ShotStoreTest, TargetResolverTest}.kt
  docs/superpowers/specs/
```

- 패키지 이름: `dev.dhnam.screentranslator`
