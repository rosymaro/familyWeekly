# CLAUDE.md

## 고정 디버그 서명 — 수정 금지

디버그 APK는 저장소에 커밋된 고정 키스토어로 서명한다. 그래야 CI나 어느 PC에서 빌드하든 서명이 같아서, 폰에 이미 설치된 앱 위에 새 빌드를 지우지 않고 덮어 설치할 수 있다.

다음은 사용자가 명시적으로 요청하지 않는 한 **절대 수정, 삭제, 재생성하지 않는다**:

- `keystore/debug.keystore` — 키스토어를 다시 만들거나 교체하지 않는다.
- `keystore/debug.sha256` — 키스토어 인증서의 SHA-256 지문. CI가 APK 서명과 비교한다.
- `gradle/debug-signing.gradle` — `signingConfigs.debug` 설정(경로, 비밀번호, alias, storeType)을 바꾸지 않는다.
- `app/build.gradle(.kts)`의 `gradle/debug-signing.gradle` 적용 줄 — 지우지 않고, 다른 곳에서 `signingConfigs.debug`나 `buildTypes.debug.signingConfig`를 덮어쓰지 않는다.
- `.gitignore`의 `!keystore/debug.keystore` 예외.
- `.github/workflows/android-debug.yml`의 "Verify signing key (fixed debug keystore)" 단계.

빌드나 CI가 서명 때문에 실패하면 위 파일을 고치지 말고, 원인을 사용자에게 알린다.

키스토어 정보 (디버그 전용, 공개되어도 되는 값):

| 항목 | 값 |
|---|---|
| 파일 | `keystore/debug.keystore` (PKCS12) |
| store/key 비밀번호 | `android` |
| alias | `androiddebugkey` |
| 인증서 SHA-256 | `7c7e2ea443dce95dd52694c9fd01501a7a3778b6e3a13e34518e523fc85b09f5` |

Android 프로젝트를 추가할 때 `app/build.gradle(.kts)`에 android 플러그인 다음으로 아래 줄을 넣는다:

```kotlin
apply(from = rootProject.file("gradle/debug-signing.gradle"))
```

이 키스토어는 디버그 빌드에만 쓴다. 릴리스 서명에는 절대 쓰지 않는다.

프로젝트 규칙과 스펙은 `agent/` 폴더에 있다.

@agent/CLAUDE.md
