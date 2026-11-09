# 화면 번역 (Screen Translator)

[English](README.md) | **한국어**

홈버튼을 길게 누르면 지금 화면을 **파파고 이미지 번역**(기본), **구글 렌즈**, 또는 직접 추가한 번역 앱(예: DeepL)으로 바로 보내는 Android 디지털 어시스턴트 앱입니다.
구글 어시스턴트가 제미니로 통합되면서 사라진 "화면 이미지 번역" 흐름을 대신합니다.

- 캡처는 앱 전용 캐시(`cache/shots/`)에만 저장되고 갤러리에는 남지 않습니다. 10분이 지난 캡처는 다음 실행 때 지워집니다.
- 확인한 기기: 갤럭시 노트20 (SM-N981N, Android 13)
- 언어: 한국어 폰에서는 한국어, 그 밖의 언어에서는 영어로 표시됩니다. Android 13 이상에서는 설정 > 일반 > 앱 언어에서 이 앱만 따로 바꿀 수 있습니다.

## 설치와 설정

1. [Releases](https://github.com/dhnam/screen-translator/releases)에서 최신 `screen-translator-vX.Y.Z.apk`를 받아 설치합니다. 직접 빌드해도 됩니다(아래 "빌드" 참고).
2. 앱 아이콘 **화면 번역**을 열고 **알림 권한을 허용**합니다. One UI는 알림 권한이 없는 앱의 안내 토스트를 막습니다.
3. **어시스턴트 설정 열기**를 누르거나, 설정 > 애플리케이션 > 기본 앱 선택 > **디지털 어시스턴트 앱**에서 **화면 번역**을 고릅니다.
4. 같은 화면에서 **화면의 텍스트 사용**과 **화면 이미지 사용**을 둘 다 켭니다.
5. 앱 화면의 번역 앱 목록에서 기본 앱을 고릅니다. 필요하면 **+ 앱 추가**로 이미지를 받을 수 있는 다른 앱을 넣고, ✕로 뺍니다. 설치되지 않은 앱은 회색으로 표시됩니다.

## 알아둘 점

- **업데이트**: 설정 화면에서 하루에 한 번(또는 **지금 확인**을 누를 때) GitHub Releases를 확인하고, 새 버전을 받아 설치할 수 있습니다. 처음 한 번은 이 앱이 앱을 설치하도록 허용해야 합니다. APK는 설치기로 바로 넘어가고 폰에 남지 않습니다.

- **"이 화면은 캡처할 수 없거나…" 안내가 뜰 때**: 은행 앱이나 DRM 영상처럼 캡처를 막는 화면이거나, 위 4번 설정이 꺼져 있는 경우입니다.
- **파파고가 늦게 뜰 때**: 최근 앱 목록에서 파파고를 지운 직후처럼 파파고가 완전히 꺼져 있으면, 파파고 자체가 뜨는 데 3~5초가 걸립니다. 이 앱의 문제가 아닙니다.
- **음성 입력**: 어시스턴트로 등록하려면 기능 없는 음성 인식 서비스가 필요해서 함께 들어 있습니다. 노트20에서는 시스템 음성 인식기가 바뀌지 않았지만, 기기에 따라 시스템 음성 인식을 쓰는 일부 앱에서 음성 입력이 안 될 수 있습니다.
- **원래 어시스턴트로 되돌리기**: 디지털 어시스턴트 앱 설정에서 Google(또는 원하는 앱)을 다시 고르면 됩니다.

## 빌드

필요한 것: JDK 17, Android SDK(`platforms;android-34`, `build-tools;34.0.0`, `platform-tools`).
`local.properties`에 `sdk.dir=<SDK 경로>`를 적은 뒤 아래를 실행합니다.

```bash
./gradlew testDebugUnitTest assembleDebug
```

설치(무선 디버깅):

```bash
adb pair <IP>:<페어링 포트> <코드>
adb connect <IP>:<포트>
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 릴리스

`v*` 태그를 push하면 GitHub Actions(`.github/workflows/release.yml`)가 테스트를 돌리고, 서명된 릴리스 APK를 빌드해 Release에 첨부합니다.

```bash
git tag v0.1.1 && git push origin v0.1.1
```

- 릴리스에 첨부하는 APK 이름은 `screen-translator-<tag>.apk`여야 합니다(작업 파일이 이렇게 만듭니다). 앱의 업데이트 기능은 이 이름만 인식합니다.
- 서명 키는 저장소 Secrets(`KEYSTORE_B64`, `KEYSTORE_PASSWORD`)에 있습니다. 로컬에서 릴리스 빌드를 하려면 `KEYSTORE_FILE`, `KEYSTORE_PASSWORD` 환경 변수를 지정합니다.
- 릴리스 인증서 SHA-256: `56:14:E0:DB:F9:DE:BE:DC:BE:17:7E:84:D9:AB:5A:C8:53:A3:90:E7:E9:35:12:2F:57:A2:C7:DE:F2:A0:41:9C`
- 직접 빌드한 디버그 APK는 "화면 번역 (dev)"(`dev.dhnam.screentranslator.dev`)라는 별도 앱으로 설치돼서 릴리스 앱과 함께 쓸 수 있습니다. 디지털 어시스턴트는 한 번에 하나만 지정할 수 있습니다.

## 구조

| 파일 | 역할 |
|---|---|
| `AssistService` / `AssistSessionService` | 디지털 어시스턴트 등록 |
| `StubRecognitionService` | 어시스턴트 등록에 필요한 빈 음성 인식 서비스 |
| `AssistSession` | 스크린샷을 받아 저장하고 번역 앱으로 공유. 2초 안에 스크린샷이 오지 않으면 안내 |
| `ShotStore` | 캡처 저장과 10분 지난 캡처 정리 |
| `TargetList` | 번역 앱 목록과 기본 앱 규칙(추가, 제거, 기본 지정, v0.1.0 설정 이어받기) |
| `TargetResolver` / `Prefs` | 이미지를 받는 화면 조회, 공유 인텐트 생성(대상이 없으면 공유 메뉴), 설정 저장 |
| `SessionMachine` | 어시스턴트 세션 상태 기계(마지막에 누른 것만 처리) |
| `UpdateUi` / `UpdateView` / `UpdateFlow` | 업데이트 영역 상태, 전이, 표시, 백그라운드 작업 |
| `SettingsActivity` | 설정 화면 |

설계: `docs/superpowers/specs/2026-11-09-screen-translator-design.md`

## 라이선스

MIT. `LICENSE` 파일을 참고하세요.
