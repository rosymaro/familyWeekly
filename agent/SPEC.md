# familyWeekly — SPEC

> Claude Code는 작업 전에 이 문서를 먼저 읽고, 지시받은 Phase만 진행한다.
> 각 Phase가 끝나면 맨 아래 "진행 상황" 체크박스를 갱신한다.
> 디자인(색·폰트·크기·장식)은 `agent/DESIGN.md`가 기준이다.

## 1. 개요

엄마가 **아이 여러 명의 서로 다른 주간 시간표**를 관리하는 안드로이드 앱 + 홈 화면 위젯.

- 아이마다 **스케줄**(주간 시간표) 하나. 스케줄은 대체로 매주 고정이다.
- 스케줄 하나 = 위젯 하나에 연결. 위젯은 여러 개 만들 수 있고 각각 다른 스케줄을 보여 준다.
- 캘린더에 **특이 일정**(병원, 체험학습, 보강 등)을 넣으면, 해당 주의 스케줄 위에 **포스트잇처럼 덧붙어** 보인다. 특이 일정 하나는 **여러 스케줄**(여러 아이)에 동시에 붙을 수 있다.
- 반복 일정은 **휴가**(기간)로 예외 처리할 수 있다.
- 일정마다 세분화된 **알림**(사전/정시/종료, 팝업·소리·진동). AlarmManager 중심.
- 디자인은 자주 바뀐다. 모든 시각 요소는 **테마 토큰**으로 빼고, **위젯마다 다른 디자인**을 줄 수 있다.

만들지 않는 것: 내보내기/가져오기, 기기 캘린더(Google 캘린더 등) 연동, 계정·동기화, 서버.

## 2. 기술 스택 (고정)

| 항목 | 선택 |
| --- | --- |
| 언어 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 내비게이션 | Navigation Compose (type-safe route, `@Serializable` 객체) |
| JSON | kotlinx.serialization |
| 상태 | ViewModel + StateFlow |
| DI | 쓰지 않음 (Application에서 `AppStore`, `AlarmScheduler`, `WidgetUpdater`를 하나씩 만들어 전달) |
| DB | 쓰지 않음. 앱 내부 저장소 JSON 파일 (`files/family_weekly.json`), 원자적 쓰기(임시 파일 → rename) |
| 위젯 | `AppWidgetProvider` + `RemoteViews`. 그림은 **Canvas로 비트맵 렌더링** 후 `ImageView`에 표시 (Glance 쓰지 않음 — 포스트잇 회전·겹침·그림자 등 자유로운 디자인 때문) |
| 알림 | `AlarmManager` (`setExactAndAllowWhileIdle`, 알람 타입은 `setAlarmClock`) + `BroadcastReceiver` + `NotificationManager`. WorkManager 쓰지 않음 |
| 시간 | `java.time` (minSdk 26이라 desugaring 불필요) |
| 공휴일 | 앱에 넣은 `assets/data/holidays_kr.json` + 사용자가 추가한 공휴일. 네트워크 쓰지 않음 |
| SDK | minSdk 26, compileSdk/targetSdk 35 |
| 모듈 | `app` 단일 모듈 |
| 패키지 | `io.github.rosymaro.familyweekly` |

Gradle 구성(version catalog, AGP·Kotlin·Compose BOM 버전, Java 17, gradle wrapper 8.11.1)은 어원 단어장 프로젝트와 같게 시작한다.

## 3. 프로젝트 구조

```
app/src/main/
  assets/
    data/holidays_kr.json          # 대한민국 공휴일 (대체공휴일 포함)
    themes/*.json                  # 기본 제공 테마 프리셋 (DESIGN.md 4절)
  res/font/                        # 번들 폰트 (OFL 등 재배포 가능한 것만)
  java/<package>/
    MainActivity.kt
    FamilyWeeklyApp.kt             # Application: AppStore, AlarmScheduler, WidgetUpdater 생성
    domain/                        # ── 순수 Kotlin (Android 의존성 없음) ──
      Models.kt                    # Schedule, Block, SpecialEvent, Vacation, Holiday, AlarmRule ...
      TimeStep.kt                  # 5분 스냅, 분<->LocalTime
      WeekRange.kt                 # 주 시작/끝, 표시 요일(월~금 / 월~일)
      OccurrenceResolver.kt        # 반복 블록 + 휴가 + 개별 취소 + 특이 일정 + 공휴일 → ResolvedWeek
      ScheduleOps.kt               # 복제, 이름 변경, 삭제 시 참조 정리
    data/
      AppStore.kt                  # JSON 로드/저장, StateFlow<AppData>, 마이그레이션
      HolidayRepository.kt
    layout/                        # ── 순수 Kotlin ──
      WeekLayout.kt                # ResolvedWeek + 크기 + 테마 → 사각형/텍스트 배치 결과
      LaneAssigner.kt              # 같은 요일 겹치는 블록 나란히 배치
      PostItPlacer.kt              # 포스트잇 위치·회전(시드 고정)
    theme/
      WidgetTheme.kt               # 디자인 토큰 (@Serializable), 기본값
      ThemeResolver.kt             # 프리셋 → 스케줄 기본 → 위젯 오버라이드 병합, 라이트/다크 선택
    render/
      WeekPainter.kt               # WeekLayout을 android.graphics.Canvas에 그림 (앱 미리보기와 위젯이 같이 씀)
    widget/
      ScheduleWidgetProvider.kt
      WidgetConfigActivity.kt      # 위젯 추가 시 스케줄·디자인 고르기
      WidgetUpdater.kt             # 렌더 + 갱신 예약
    alarm/
      planner/AlarmPlanner.kt      # ── 순수 Kotlin ── 다음 알림 시각 계산
      AlarmScheduler.kt            # AlarmManager 등록/해제
      AlarmReceiver.kt, BootReceiver.kt, TimeChangeReceiver.kt
      NotificationChannels.kt, AlarmActivity.kt (전체 화면 알람)
    ui/
      navigation/Routes.kt, AppNavHost.kt
      home/          # 스케줄(아이) 목록
      week/          # 주간 편집 화면 (드래그 이동)
      block/         # 블록 추가/수정 시트
      calendar/      # 월 캘린더: 특이 일정, 휴가, 공휴일
      event/         # 특이 일정 추가/수정
      alarm/         # 알림 설정
      design/        # 테마 편집기 (DESIGN.md 5절)
      settings/
      theme/Theme.kt # 앱 자체 Material 테마 (다크 모드)
app/src/test/                      # JVM 유닛 테스트
```

## 4. 데이터 모델

모든 id는 UUID 문자열. 시각은 **자정부터의 분(Int, 5의 배수)**, 날짜는 `LocalDate`(ISO 문자열로 직렬화).

### 저장 파일 `family_weekly.json`

```json
{
  "version": 1,
  "settings": { ... },
  "schedules": [ ... ],
  "specialEvents": [ ... ],
  "vacations": [ ... ],
  "customHolidays": [ ... ],
  "themes": [ ... ],
  "widgets": [ ... ]
}
```

### Schedule (아이 한 명의 주간 시간표)

| 필드 | 설명 |
| --- | --- |
| `id`, `name` | 이름 예: "첫째 하린" |
| `emoji` | 선택. 위젯 머리말에 표시 |
| `days` | `MON_FRI` 또는 `MON_SUN` (스케줄마다 다를 수 있음) |
| `timeRange` | `{ "mode": "AUTO" }` 또는 `{ "mode": "FIXED", "startMin": 480, "endMin": 1320 }` |
| `themeId` | 이 스케줄의 기본 테마 (위젯에서 덮어쓸 수 있음) |
| `alarmDefaults` | 이 스케줄 블록들의 기본 알림 규칙 (5절) |
| `alarmsEnabled` | 스케줄 전체 알림 켜기/끄기 |
| `holidayPolicy` | `SHOW_ONLY`(표시만) / `CANCEL_BLOCKS`(공휴일엔 반복 블록 취소 처리) |
| `blocks` | 반복 블록 목록 |
| `cancellations` | 개별 취소 목록 `{ blockId, date, reason? }` ("이번 주만 취소") |

`timeRange`:
- `AUTO`: 그 주에 보이는 모든 블록·특이 일정의 최소 시작 시각을 **정시로 내림**, 최대 종료 시각을 **정시로 올림**. 아무것도 없으면 09:00~18:00.
- `FIXED`: 사용자가 정한 시작·끝 시각 (정시 단위 권장, 5분 단위 허용). 범위를 벗어난 블록은 경계에 잘라 그리고, 잘렸다는 표시(▲/▼)를 둔다.

### Block (매주 반복되는 칸)

| 필드 | 설명 |
| --- | --- |
| `id`, `title` | 예: "피아노" |
| `day` | `DayOfWeek` |
| `startMin`, `endMin` | 5분 단위, `start < end`, 같은 날 안 (자정을 넘지 않음) |
| `colorKey` | 테마 팔레트 키 (`DESIGN.md` 1절 `palette`) 또는 `#RRGGBB` |
| `place`, `memo` | 선택 |
| `alarm` | `null`이면 스케줄 기본값 사용, 있으면 이 블록만 덮어씀 |
| `validFrom`, `validUntil` | 선택. 학기 바뀜 등 (없으면 무기한) |

한 블록을 여러 요일에 넣을 때는 UI에서 요일을 여러 개 고르되, 저장은 요일마다 블록 하나씩 (`groupId`로 묶어서 같이 수정 가능).

### Vacation (휴가 — 반복 일정 예외 처리)

| 필드 | 설명 |
| --- | --- |
| `id`, `title` | 예: "여름방학", "할머니댁" |
| `from`, `to` | 날짜 범위 (포함) |
| `scheduleIds` | 적용할 스케줄 (여러 개) |
| `blockIds` | 없으면(null) 대상 스케줄의 모든 블록, 있으면 그 블록들만 (예: 학원만 방학) |

휴가 기간 동안 해당 반복 블록은 **사라지지 않고 '취소' 상태로 표시**되고, 알림은 울리지 않는다.

### SpecialEvent (특이 일정 = 포스트잇)

| 필드 | 설명 |
| --- | --- |
| `id`, `title`, `memo` | |
| `date` | 날짜 |
| `startMin`, `endMin` | 5분 단위. `allDay: true`면 시각 없이 요일 머리말 쪽에 붙고, 그날 모든 블록과 겹친 것으로 본다 |
| `scheduleIds` | 붙일 스케줄 (**여러 개 가능**, 1개 이상) |
| `overlapActions` | 스케줄별 `{ scheduleId: KEEP | CANCEL }` — 시간이 겹치는 기존 블록을 유지할지 취소할지. 항목이 없는 스케줄은 KEEP으로 계산 |
| `paperKey` | 포스트잇 종이 색 키 (테마 `postIt.papers`) |
| `alarm` | 알림 규칙 (없으면 설정의 특이 일정 기본값) |

규칙:
- 포스트잇은 기존 블록을 **대체하지 않는다**. 블록은 그 자리에 그대로 그려지고, 포스트잇이 **그 위에 붙는다**.
- 특이 일정을 저장할 때 겹치는 블록이 있으면 스케줄마다 "기존 일정 유지 / 기존 일정 취소"를 **반드시 묻는다** (기본값 없음, 사용자가 고르게).
- `CANCEL`을 고르면 겹친 블록은 '취소' 상태(취소 색 + 취소 표시)가 되고 알림이 꺼진다. 포스트잇이 위에 덮여 있어도 **아래 블록이 취소됐는지 한눈에 보여야** 한다 (포스트잇 밖으로 보이는 부분 + 포스트잇 위 작은 배지, DESIGN.md 3절).
- 특이 일정을 삭제하거나 시간을 옮기면, 그로 인한 취소도 같이 풀린다 (취소는 특이 일정에서 **계산**하는 것이지 블록에 따로 저장하지 않는다).

### 상태 (OccurrenceResolver 결과)

특정 주의 특정 스케줄에 대해 블록 하나하나의 실제 상태를 계산한다:

| 상태 | 원인 | 표시 |
| --- | --- | --- |
| `ACTIVE` | 기본 | 활성 색 |
| `CANCELLED` | `VACATION` / `MANUAL`(개별 취소) / `EVENT`(특이 일정이 취소) / `HOLIDAY`(공휴일 정책) | 취소 색 + 취소 사유 아이콘 |
| `PAST` (선택 표시) | 오늘 기준 이미 끝난 블록 | 테마의 `pastDim`만큼 흐리게 (토큰으로 끌 수 있음) |

취소 사유가 여러 개면 모두 보관하고, 표시는 우선순위 `MANUAL > EVENT > VACATION > HOLIDAY`로 하나를 고른다.

### Holiday

- `assets/data/holidays_kr.json`: `{ "version":1, "holidays":[{ "date":"2026-09-25", "name":"추석" }, ...] }` — 2026~2030년, 대체공휴일 포함.
  음력 공휴일과 대체공휴일 날짜는 틀리기 쉬우니 **PR 설명에 "사람이 확인할 날짜 목록"으로 따로 적는다**.
- 사용자가 공휴일을 추가/삭제할 수 있다 (`customHolidays`, 예: 재량휴업일. "숨김" 플래그로 기본 공휴일 숨기기 가능).

### WidgetBinding

| 필드 | 설명 |
| --- | --- |
| `appWidgetId` | |
| `scheduleId` | 연결된 스케줄 (스케줄 하나 = 위젯 하나 기준. 같은 스케줄을 두 위젯에 거는 것도 막지는 않음) |
| `themeOverride` | `null`이면 스케줄 테마. 있으면 이 위젯만의 테마 id 또는 토큰 부분 덮어쓰기 |
| `darkMode` | `SYSTEM` / `LIGHT` / `DARK` |
| `weekOffset` | 기본 0 (이번 주). 위젯 화살표로 지난주/다음주 보기는 하지 않음 |

### 스케줄 관리 동작 (`ScheduleOps`)

- **추가**: 이름, 요일(월~금/월~일), 테마 고르기.
- **복제**: 블록·개별 취소·알림 설정·테마를 깊은 복사(새 id). 이름은 "○○ 복사본". 특이 일정·휴가는 복사하지 않고, 복제 대화상자에서 "특이 일정·휴가도 같이 붙이기" 체크박스로 선택 (켜면 기존 이벤트의 `scheduleIds`에 새 스케줄 추가).
- **이름 변경**.
- **삭제**: 확인 대화상자. 특이 일정·휴가의 `scheduleIds`에서 제거, 대상이 0개가 된 특이 일정·휴가는 같이 지울지 묻는다. 연결된 위젯은 "연결된 스케줄이 없어요 — 눌러서 선택" 상태로 그린다. 예약된 알림 해제.
- 순서 바꾸기 (홈 목록 순서).

## 5. 알림 (AlarmManager 중심)

### AlarmRule (세분화된 설정)

```json
{
  "triggers": [
    { "type": "BEFORE_START", "minutes": 10 },
    { "type": "AT_START" },
    { "type": "BEFORE_END",  "minutes": 5 },
    { "type": "AT_END" }
  ],
  "style": "HEADS_UP",          // SILENT_NOTIFICATION | NOTIFICATION | HEADS_UP | FULL_SCREEN_ALARM
  "sound": { "enabled": true, "uri": null, "kind": "NOTIFICATION" },   // kind: NOTIFICATION | ALARM, uri null=기본음
  "vibration": { "enabled": true, "pattern": "SHORT" },              // OFF | SHORT | LONG | DOUBLE | CUSTOM(ms 배열)
  "repeatUntilDismissed": false, // FULL_SCREEN_ALARM에서만: 끌 때까지 반복
  "snoozeMinutes": 5,           // 0이면 다시 알림 버튼 없음
  "skipOnCancelled": true       // 취소된 블록은 알림 없음 (항상 true, UI에는 안내만)
}
```

- 사전 알림 분: 0~180분, 5분 단위, **여러 개** 가능.
- 적용 계층: 앱 전체 기본값(설정) → 스케줄 기본값 → 블록/특이 일정 개별값. 하위가 있으면 하위가 이긴다 (필드 단위 병합이 아니라 규칙 통째로 교체 — 단순하게).
- 전역 설정:
  - 전체 알림 켜기/끄기, 스케줄별 켜기/끄기
  - **방해 금지 시간대** (예: 22:00~07:00) — 이 시간엔 무음 알림으로 낮춤 (`FULL_SCREEN_ALARM`은 예외 여부를 설정에서 선택)
  - 특이 일정 기본 알림 규칙
  - 알림 문구 형식: "{아이} · {일정} {N}분 전 ({장소})"

### 스타일 → Android 구현

| style | 구현 |
| --- | --- |
| `SILENT_NOTIFICATION` | IMPORTANCE_LOW 채널, 소리·진동 없음 |
| `NOTIFICATION` | IMPORTANCE_DEFAULT 채널 |
| `HEADS_UP` (팝업) | IMPORTANCE_HIGH 채널 (화면 위 팝업) |
| `FULL_SCREEN_ALARM` | `setAlarmClock` + 전체 화면 인텐트(`AlarmActivity`). Android 14+에서 `USE_FULL_SCREEN_INTENT`가 허용되지 않으면 HEADS_UP으로 대체하고 설정 화면에 안내 |

- 알림 채널은 만든 뒤 소리·진동을 앱에서 바꿀 수 없으므로, **(style, sound kind, uri, vibration pattern) 조합의 해시를 채널 id**로 써서 필요한 채널을 만들고, 안 쓰는 채널은 정리한다. 이 규칙은 `NotificationChannels`에 한 곳에만 둔다.
- 진동 `CUSTOM`과 `repeatUntilDismissed`는 채널 대신 `AlarmActivity`/수신기에서 `Vibrator`·`Ringtone`으로 직접 처리.

### 예약 방식

- `AlarmPlanner`(순수 Kotlin): 지금 시각 + AppData + 공휴일 → **앞으로 48시간 안의 알림 목록** (`PlannedAlarm(at, scheduleId, sourceId, triggerType, ruleHash)`), 시간순, 취소된 블록 제외, 방해 금지 반영.
- `AlarmScheduler`: 이전에 등록한 목록과 비교해 바뀐 것만 해제/등록. requestCode는 `PlannedAlarm`의 안정적인 키에서 만든다.
- 다시 계산하는 때: 데이터 저장 직후, 알림이 울린 직후, 부팅(`BOOT_COMPLETED`), 시간/시간대 변경(`TIME_SET`, `TIMEZONE_CHANGED`), 앱 업데이트(`MY_PACKAGE_REPLACED`), 매일 자정 직후 1회(비정확 알람으로 충분).
- 권한:
  - Android 13+ `POST_NOTIFICATIONS` — 처음 알림 켤 때 요청.
  - `SCHEDULE_EXACT_ALARM` (Android 12+) — `canScheduleExactAlarms()`가 false면 설정 화면으로 보내는 안내 카드를 띄우고, 그동안은 `setWindow`(±1분) 비정확 알람으로 대체.
  - 배터리 최적화 예외는 강제하지 않고, 알림이 늦는다는 사용자를 위해 설정에 안내 링크만.
- 알림 탭 → 앱의 해당 스케줄 주간 화면. 알림 버튼: "확인", "N분 뒤 다시".

## 6. 화면 기능

### 홈 (스케줄 목록)
- 아이별 카드: 이름·이모지, 오늘 남은 일정 요약, 미니 주간 미리보기(위젯과 같은 렌더러, 작게).
- 카드 메뉴: 열기, 이름 변경, 복제, 삭제, 디자인, 알림 켜기/끄기.
- 상단 메뉴: 캘린더(특이 일정·휴가·공휴일), 설정.
- 오른쪽 아래 + : 스케줄 추가.

### 주간 편집 화면 (스케줄 하나)
- 왼쪽 시간 축: **1시간 단위** 눈금·라벨. 그리드 이동/편집 단위는 **5분**.
- 위쪽: 요일 머리말 (월~금 또는 월~일, 스케줄 설정), 날짜, **오늘 강조**, **공휴일 표시**(이름 + 공휴일 색).
- **현재 시간 표시선**: 오늘 칸(또는 전체 폭 — 토큰으로 선택)에 가로선, 1분마다 갱신.
- 블록 탭 → 수정 시트 (제목, 요일 여러 개, 시작/끝 5분 단위 선택기, 색, 장소, 메모, 알림, "이번 주만 취소/취소 해제").
- 빈 칸 길게 누르기 → 그 위치(5분 스냅)에 1시간짜리 새 블록.
- **드래그 이동**: 블록을 길게 누른 뒤 끌면 위아래(시각, 5분 스냅)와 좌우(요일)로 이동. 끄는 동안 시작~끝 시각 말풍선, 놓을 위치 미리보기, 진동 틱(5분 칸 넘을 때마다 약하게). 다른 블록과 겹치면 허용하되 나란히 배치(lane)로 보여 줌. 놓은 뒤 스낵바 "되돌리기".
  - 드래그로 바꾸는 건 **반복 블록 자체**(매주)다. 이번 주만 옮기기는 하지 않는다.
- 주 이동: 좌우 화살표/스와이프로 지난주·다음주 (특이 일정, 휴가, 취소를 그 주 기준으로 보여 줌). "오늘" 버튼.
- 특이 일정 포스트잇이 블록 위에 겹쳐 보임. 포스트잇 탭 → 특이 일정 수정, 포스트잇 길게 누르기 → 잠깐 투명하게 해서 아래 블록 보기.

### 캘린더 (월)
- 월 달력: 날짜마다 특이 일정 점(스케줄 색), 휴가 기간 띠, 공휴일 빨간 날짜 + 이름.
- 날짜 탭 → 그날 특이 일정 목록 + 추가.
- 특이 일정 추가/수정: 제목, 날짜, 시작/끝(5분) 또는 하루 종일, **대상 스케줄 여러 개 선택(칩)**, 겹치는 기존 블록 목록과 스케줄별 "유지/취소" 선택(4절), 포스트잇 색, 메모, 알림.
- 휴가 추가/수정: 제목, 기간, 대상 스케줄 여러 개, 전체 블록 / 특정 블록만.
- 공휴일 관리: 기본 공휴일 숨기기, 사용자 공휴일 추가.

### 설정
- 다크 모드: 시스템 / 라이트 / 다크 (앱). 위젯은 위젯별 설정 (WidgetBinding.darkMode).
- 주 시작은 월요일 고정. 시각 표기 24시간/12시간(오전·오후).
- 알림 전역 설정 (5절), 권한 상태 카드.
- 테마 라이브러리 (디자인 편집기로 이동).

### 위젯
- 위젯 추가 시 `WidgetConfigActivity`: 스케줄 고르기 → 테마 고르기(스케줄 테마 그대로 / 다른 프리셋 / 이 위젯만 꾸미기) → 다크 모드 → 미리보기 → 완료.
- 위젯 길게 눌러 "재구성"(Android 12+ `reconfigurable`)으로 다시 열 수 있다.
- 표시: 이번 주, 머리말(아이 이름·이모지·주 범위), 요일 머리말(오늘 강조, 공휴일), 1시간 단위 시간 축, 블록(활성/취소 색), 포스트잇(덧붙임), 현재 시간선, 휴가 띠/배지.
- 크기에 따라 3단계 레이아웃 (`compact` / `medium` / `large`, DESIGN.md 2절): 작으면 블록 안 시각 텍스트 숨김, 포스트잇은 제목만 등.
- 탭 → 앱의 그 스케줄 주간 화면.
- 렌더링: `WeekLayout` → `WeekPainter` → 비트맵. 크기는 `AppWidgetManager.getAppWidgetOptions`의 dp 값 × density (가로/세로 모드 둘 다). RemoteViews 비트맵 메모리 한도를 넘지 않게 최대 픽셀 수를 제한하고 넘으면 배율을 낮춘다.
- 갱신: 데이터 저장 직후 / 위젯 크기 변경 / 자정 / 시간 변경 / 현재 시간선용으로 **비깨우기(RTC) 알람 5분 간격** (화면 꺼져 있으면 안 깨움) / 화면 켜짐 후 첫 갱신은 앱 실행 시.

## 7. Phase 계획

각 Phase = 세션 하나 = 브랜치 하나 = PR 하나.
모든 Phase 공통 완료 조건: `./gradlew assembleDebug testDebugUnitTest` 통과.
각 Phase에 바로 쓸 지시문은 `agent/PROMPTS.md`에 있다.

- **Phase 0 — 골격 + 서명**
  - Compose 빈 앱 생성 (Gradle wrapper 포함, version catalog 사용), 패키지 `io.github.rosymaro.familyweekly`
  - 고정 디버그 키스토어는 이미 커밋되어 있음 (`keystore/`, `gradle/debug-signing.gradle`) — 적용 줄만 넣는다. 재생성 금지
  - 홈 화면에 "familyWeekly" 수준만, 앱 Material 테마 라이트/다크
- **Phase 1 — 도메인 + 저장소**
  - `domain/` 모델, `TimeStep`, `WeekRange`(월~금/월~일), `OccurrenceResolver`, `ScheduleOps`(복제·이름 변경·삭제 참조 정리)
  - `AppStore`(JSON, 원자적 쓰기, version, 손상 파일이면 백업 후 빈 데이터 + 알림), 공휴일 JSON 로드
  - 테스트: 5분 스냅, 주 계산(연말·연초 걸친 주), 휴가 기간 경계, 특이 일정 KEEP/CANCEL, 취소 사유 우선순위, 공휴일 정책, 복제 깊은 복사, 삭제 시 참조 정리, 저장/로드 왕복
- **Phase 2 — 테마 토큰 + 레이아웃 엔진**
  - `WidgetTheme`(DESIGN.md 1절 토큰 전부, 기본값), 프리셋 JSON 3종(DESIGN.md 4절), `ThemeResolver`(병합·다크)
  - `WeekLayout`, `LaneAssigner`, `PostItPlacer`, timeRange AUTO/FIXED, 크기 단계(compact/medium/large)
  - `WeekPainter`(Canvas) + 앱 안 디버그용 미리보기 화면
  - 테스트: 레이아웃 좌표(5분 = 정확한 px), 겹침 lane, 범위 밖 잘림, 포스트잇 회전 시드 고정, 테마 병합, 프리셋 JSON 파싱
- **Phase 3 — 홈 + 스케줄 관리 + 주간 편집**
  - 홈 목록(미니 미리보기), 추가/복제/이름 변경/삭제/순서
  - 주간 편집 화면: 블록 추가·수정·삭제, 5분 선택기, **드래그 이동**(5분 스냅, 요일 이동, 되돌리기), 오늘 강조, 현재 시간선, 주 이동, 이번 주만 취소
  - 드래그 좌표 → 시각/요일 변환은 순수 함수로 빼서 테스트
- **Phase 4 — 캘린더: 특이 일정 + 휴가 + 공휴일**
  - 월 캘린더, 특이 일정 편집(여러 스케줄, 겹침 유지/취소 선택), 휴가 편집, 공휴일 관리, `holidays_kr.json`(2026~2030)
  - 주간 화면에 포스트잇·휴가·공휴일 반영
- **Phase 5 — 위젯**
  - Provider, ConfigActivity(스케줄·테마·다크 모드 선택, 미리보기), 재구성, 여러 위젯 각각 다른 스케줄/디자인
  - 크기 대응, 비트맵 메모리 한도, 갱신 예약(5절·6절), 스케줄 삭제 시 빈 상태
- **Phase 6 — 알림**
  - `AlarmPlanner` + 테스트(48시간 창, 취소 제외, 방해 금지, 여러 사전 알림, 자정 근처, 시간대 변경)
  - `AlarmScheduler` 차등 등록, 수신기들, 채널 해시, 전체 화면 알람, 다시 알림, 권한 흐름, 알림 설정 UI(전역/스케줄/블록/특이 일정)
- **Phase 7 — 디자인 편집기**
  - DESIGN.md 5절: 토큰 편집 UI(그룹별), 실시간 미리보기(위젯 크기 3종 × 라이트/다크), 프리셋 복제·이름 변경·삭제, 위젯별 오버라이드, 기본값으로 되돌리기
- **Phase 8 — 다듬기**
  - 앱 아이콘, 빈 상태, 긴 제목 줄임, 접근성(TalkBack 설명, 색 말고도 취소 표시), 성능(렌더 캐시), 위젯 미리보기 이미지(`previewLayout`)

## 8. 진행 상황

- [x] Phase 0
- [x] Phase 1
- [x] Phase 2
- [ ] Phase 3
- [ ] Phase 4
- [ ] Phase 5
- [ ] Phase 6
- [ ] Phase 7
- [ ] Phase 8
