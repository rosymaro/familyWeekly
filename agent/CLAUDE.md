# CLAUDE.md

엄마가 아이 여러 명의 주간 시간표를 관리하는 안드로이드 위젯 앱 (familyWeekly).
**작업 전 반드시 `agent/SPEC.md`를 읽을 것.** 화면·위젯·테마를 건드리는 작업이면 `agent/DESIGN.md`도 읽을 것.

## 작업 방식
- 지시받은 Phase(또는 작업)만 한다. 다음 Phase를 미리 구현하지 않는다.
- 스펙이 모호하면 추측해서 구현하지 말고, 가정한 내용을 PR 설명에 명시한다.
- 끝나면 `agent/SPEC.md`의 "진행 상황" 체크박스를 갱신한다.
- PR 설명에는: 한 일, 가정한 것, 폰에서 확인할 항목(체크리스트)을 쓴다.
- 혼자 하는 프로젝트다. PR을 만든 뒤 사용자 승인을 기다리지 말고, CI가 통과하면 바로 `main`에 머지까지 끝낸다 (merge commit 방식).
  CI가 실패하거나 충돌이 나면 머지하지 말고 원인을 사용자에게 알린다.
- 디자인 값(색, 크기, 폰트, 모서리, 그림자 등)을 코드에 직접 쓰지 않는다. 반드시 `WidgetTheme` 토큰을 거친다 (`agent/DESIGN.md` 1절). 새 디자인 요소가 필요하면 토큰을 추가하고 DESIGN.md 토큰 표도 갱신한다.

## 빌드 / 테스트
```
./gradlew assembleDebug testDebugUnitTest
```
- 커밋 전에 반드시 위 명령이 통과해야 한다.
- 에뮬레이터는 없다. UI·위젯·알림은 폰에서 사람이 확인한다. 로직은 JVM 유닛 테스트로 검증한다.
- ANDROID_HOME=/opt/android-sdk

## 절대 건드리지 말 것
- `keystore/debug.keystore`, `keystore/debug.sha256`, `signingConfigs.debug` 설정
  (바꾸면 폰에서 업데이트 설치가 깨진다. 재생성 금지.)
- `.github/workflows/` (사람이 직접 관리한다)

## 코드 규칙
- 기술 스택은 agent/SPEC.md 2절 고정. 라이브러리 추가가 필요하면 PR 설명에 이유를 쓴다.
- Hilt, Room, Retrofit, WorkManager, Glance는 쓰지 않는다. (위젯은 Canvas 비트맵 렌더링, 알림은 AlarmManager — SPEC 2절)
- 로직(반복 일정 펼치기, 휴가·취소·특이 일정 판정, 레이아웃 계산, 알림 시각 계산, 테마 병합)은 Android 의존성 없는 순수 Kotlin(`domain/`, `layout/`, `alarm/planner`)으로 만들어 테스트한다.
- 시간은 `java.time`(LocalDate, LocalTime, DayOfWeek, ZoneId.systemDefault())만 쓴다. 분 단위 값은 항상 5의 배수 (`TimeStep.snap()`으로 보정).
- 저장 파일 형식을 바꾸면 `version`을 올리고 이전 버전 마이그레이션과 테스트를 같이 넣는다. 사용자 데이터를 잃는 변경은 금지.
- UI 문자열은 한국어.
