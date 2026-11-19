# familyWeekly — DESIGN

> 디자인은 자주 바뀐다. 그래서 **코드는 그리는 방법만 알고, 무엇을 어떻게 보일지는 전부 테마 토큰이 정한다.**
> 디자이너는 앱 안 디자인 편집기나 `assets/themes/*.json`만 고쳐서 위젯 모양을 바꿀 수 있어야 한다.
> 코드에 색·크기·폰트를 직접 쓰는 것은 금지 (`agent/CLAUDE.md`).

## 0. 원칙

1. **하나의 렌더러**: 앱 주간 화면 미리보기, 홈 미니 미리보기, 위젯, 디자인 편집기 미리보기는 모두 같은 `WeekLayout` + `WeekPainter`를 쓴다. 편집기에서 본 것이 위젯과 똑같아야 한다 (WYSIWYG).
   - 주간 편집 화면은 Compose로 상호작용(드래그)을 처리하지만, 배경 그리드·블록·포스트잇 그림은 같은 레이아웃 결과와 같은 토큰으로 그린다.
2. **테마 = 토큰 묶음 (JSON)**. 프리셋 → 스케줄 테마 → 위젯 오버라이드 순서로 **토큰 단위 병합**. 위젯마다 완전히 다른 디자인이 가능하다.
3. **라이트/다크 쌍**: 색 토큰은 모두 `{ "light": "#...", "dark": "#..." }`. dark가 비면 자동 생성(명도 반전 규칙, `ThemeResolver`)하되 디자이너가 덮어쓸 수 있다.
4. **크기 단위는 dp/sp**, 비율 값은 0~1. 렌더러가 위젯 크기·밀도에 맞춰 px로 바꾼다.
5. **상태는 색만으로 구분하지 않는다**: 취소는 색 + 무늬/취소선/배지 중 하나 이상 (접근성).
6. 토큰을 추가하면 이 문서 1절 표, `WidgetTheme.kt` 기본값, 프리셋 JSON 파싱 테스트를 같이 고친다. 모르는 토큰은 무시하고 읽는다(하위 호환).

## 1. 디자인 토큰

표기: `color` = 라이트/다크 색쌍(알파 포함 `#AARRGGBB` 허용), `dp`, `sp`, `0..1`, `enum`, `font` = 폰트 참조(2-1).

### 1-1. canvas (위젯 바탕)
| 토큰 | 타입 | 설명 |
|---|---|---|
| `canvas.background` | color | 바탕 색 |
| `canvas.gradient` | `{type: NONE/LINEAR/RADIAL, colors[], angle}` | 바탕 그라데이션 |
| `canvas.backgroundImage` | asset 경로 / null | 종이 질감 등 타일 이미지, `imageAlpha`, `imageMode: TILE/FILL` |
| `canvas.cornerRadius` | dp | 위젯 모서리 (Android 12+ 시스템 반경과 맞추기 옵션 `matchSystem`) |
| `canvas.padding` | dp ×4 | 안쪽 여백 |
| `canvas.border` | `{width dp, color, style SOLID/DASHED}` | 테두리 |
| `canvas.opacity` | 0..1 | 위젯 전체 투명도 (배경화면 비침) |

### 1-2. header (머리말: 아이 이름, 주 범위)
| 토큰 | 타입 | 설명 |
|---|---|---|
| `header.visible` | bool | |
| `header.height` | dp | |
| `header.title.font` / `size` / `weight` / `color` / `letterSpacing` | font/sp/int/color/em | 아이 이름 |
| `header.showEmoji`, `header.emojiSize` | bool, sp | |
| `header.subtitle.format` | 문자열 | 예 `"M월 d일 ~ M월 d일"`, `"M월 W주차"` |
| `header.subtitle.*` | 폰트 묶음 | |
| `header.align` | START/CENTER/END | |
| `header.background` | color / null | 띠 배경 |
| `header.decoration` | NONE/UNDERLINE/PILL/RIBBON | 장식 |

### 1-3. dayHeader (요일 머리말)
| 토큰 | 타입 | 설명 |
|---|---|---|
| `dayHeader.height` | dp | |
| `dayHeader.labelFormat` | 문자열 | `"E"`(월), `"E d"`(월 17), `"d\nE"` |
| `dayHeader.font` 묶음 | | 평일 글자 |
| `dayHeader.saturdayColor`, `sundayColor` | color | |
| `dayHeader.holidayColor` | color | 공휴일 글자 색 |
| `dayHeader.holidayNameVisible` | bool | 공휴일 이름 작게 표시 |
| `dayHeader.holidayNameFont` 묶음 | | |
| `dayHeader.holidayMark` | NONE/DOT/UNDERLINE/BADGE | 공휴일 표시 방식 |
| `dayHeader.background` | color | |

### 1-4. today (오늘 강조)
| 토큰 | 타입 | 설명 |
|---|---|---|
| `today.headerStyle` | NONE/FILL/OUTLINE/PILL/UNDERLINE/CIRCLE_DATE | 요일 머리말 강조 |
| `today.headerColor`, `today.headerTextColor` | color | |
| `today.columnTint` | color (알파) | 오늘 열 전체 배경 틴트 |
| `today.columnBorder` | `{width, color}` | 오늘 열 테두리 |

### 1-5. timeAxis (왼쪽 시간 축, 1시간 단위)
| 토큰 | 타입 | 설명 |
|---|---|---|
| `timeAxis.width` | dp | |
| `timeAxis.labelFormat` | `H` / `HH:mm` / `a h시` / `h a` | |
| `timeAxis.font` 묶음 | | |
| `timeAxis.labelAlign` | TOP_OF_LINE/CENTER_ON_LINE | 라벨을 선 위에 / 선 가운데 |
| `timeAxis.background` | color | |
| `timeAxis.showEveryNHours` | 1/2/3 | 작은 위젯에서 라벨 솎기 (선은 1시간마다) |

### 1-6. grid (그리드)
| 토큰 | 타입 | 설명 |
|---|---|---|
| `grid.hourLine` | `{width, color, style SOLID/DASHED/DOTTED}` | 1시간 선 |
| `grid.halfHourLine` | 같음 + `visible` | 30분 보조선 (기본 끔) |
| `grid.columnDivider` | 같음 + `visible` | 요일 구분선 |
| `grid.alternateColumnTint` | color / null | 줄무늬 열 |
| `grid.weekendColumnTint` | color / null | 토·일 열 (월~일일 때) |
| `grid.holidayColumnTint` | color / null | 공휴일 열 |
| `grid.hourHeight` | dp 또는 `AUTO` | AUTO면 위젯 높이 ÷ 표시 시간 수 |
| `grid.columnGap` | dp | |

### 1-7. block (반복 일정 칸)
| 토큰 | 타입 | 설명 |
|---|---|---|
| `block.palette` | `{key: color}` | 색 키 목록 (`mint`, `peach`, `sky` ...) — 블록은 키로 색을 고름. 테마 바꾸면 팔레트도 바뀜 |
| `block.fillStyle` | SOLID/TINT/OUTLINE/LEFT_BAR/GRADIENT | 채우기 방식 |
| `block.tintAlpha` | 0..1 | TINT일 때 |
| `block.cornerRadius` | dp | |
| `block.border` | `{width, color 또는 "auto"(팔레트 색 진하게)}` | |
| `block.shadow` | `{dx, dy, blur, color}` / null | |
| `block.inset` | dp | 칸 사이 여백 |
| `block.padding` | dp ×4 | |
| `block.title.font` 묶음, `maxLines` | | |
| `block.textColor` | color 또는 `"auto"`(배경 대비 자동) | |
| `block.showTime` | ALWAYS/IF_FITS/NEVER | 블록 안 시각 표시 |
| `block.time.font` 묶음, `format` | | `"H:mm"`, `"H:mm–H:mm"` |
| `block.showPlace` | ALWAYS/IF_FITS/NEVER | |
| `block.icon` | NONE/EMOJI/DOT | 제목 앞 |
| `block.minTextHeight` | dp | 이보다 낮으면 텍스트 생략 |
| `block.pastDim` | 0..1 | 지난 블록 흐리게 (0이면 안 함) |
| `block.clipIndicator` | NONE/ARROW/FADE | 시간 범위 밖 잘림 표시 |

### 1-8. cancelled (취소 상태) — 활성과 확실히 구분
| 토큰 | 타입 | 설명 |
|---|---|---|
| `cancelled.fill` | color | 취소 칸 채우기 (기본: 회색 틴트) |
| `cancelled.keepPaletteHue` | bool | 원래 색을 흐리게 남길지 |
| `cancelled.alpha` | 0..1 | |
| `cancelled.pattern` | NONE/HATCH/DOTS/CROSS | 무늬 |
| `cancelled.patternColor`, `patternSpacing`, `patternAngle` | color, dp, deg | |
| `cancelled.strikeThrough` | bool | 제목 취소선 |
| `cancelled.badge` | NONE/TEXT/ICON | "취소" 글자 또는 사유 아이콘 |
| `cancelled.badgeText` | 문자열 | 기본 "취소" |
| `cancelled.reasonIcons` | `{MANUAL, EVENT, VACATION, HOLIDAY: 이모지/아이콘 키}` | 사유별 아이콘 |
| `cancelled.border` | `{width, color, style}` | 점선 테두리 등 |

### 1-9. postIt (특이 일정 — 블록 위에 붙이는 느낌)
| 토큰 | 타입 | 설명 |
|---|---|---|
| `postIt.papers` | `{key: color}` | 종이 색 (`yellow`, `pink`, `blue`, `green`) |
| `postIt.widthRatio` | 0..1 | 요일 칸 폭 대비 (1보다 작게 해서 아래 블록이 보이게, 기본 0.82) |
| `postIt.offset` | `{x dp, y dp}` | 시각 위치 기준 어긋남 (붙인 느낌) |
| `postIt.rotation` | `{min deg, max deg}` | 일정 id로 시드 고정한 무작위 기울기 (매번 같은 각도) |
| `postIt.cornerRadius` | dp | |
| `postIt.shadow` | `{dx, dy, blur, color}` | 종이 그림자 |
| `postIt.foldCorner` | NONE/BOTTOM_RIGHT/TOP_RIGHT + `size dp` | 접힌 모서리 |
| `postIt.attach` | NONE/TAPE/PIN/CLIP | 테이프·압정·클립 장식 |
| `postIt.tapeColor`, `tapeAlpha`, `tapeAngle` | color, 0..1, deg | |
| `postIt.font` 묶음 | | 손글씨 폰트 추천 |
| `postIt.showTime` | ALWAYS/IF_FITS/NEVER | |
| `postIt.overlapOpacity` | 0..1 | 아래 블록과 겹칠 때 포스트잇 투명도 |
| `postIt.underBadge` | NONE/CORNER_TAG | 아래 블록이 **취소됐는지/유지되는지** 포스트잇 모서리 작은 태그로 표시 ("기존 취소" / "기존 유지") |
| `postIt.underBadgeColors` | `{KEEP: color, CANCEL: color}` | |
| `postIt.allDayPlacement` | HEADER_STRIP/TOP_OF_COLUMN | 하루 종일 일정 위치 |
| `postIt.multiDayStack` | dp | 같은 시간에 여러 포스트잇이 겹칠 때 계단식 어긋남 |

### 1-10. nowLine (현재 시간 표시선)
| 토큰 | 타입 | 설명 |
|---|---|---|
| `nowLine.visible` | bool | |
| `nowLine.span` | TODAY_COLUMN/ALL_COLUMNS | |
| `nowLine.color`, `width`, `style` | | |
| `nowLine.head` | NONE/DOT/TRIANGLE/PILL_TIME | 왼쪽 끝 장식 (PILL_TIME은 "14:35" 말풍선) |
| `nowLine.glow` | `{blur, color}` / null | |
| `nowLine.aboveStickers` | bool | 포스트잇 위에 그릴지 (기본 true) |

### 1-11. vacation (휴가)
| 토큰 | 타입 | 설명 |
|---|---|---|
| `vacation.headerBand` | `{visible, color, font 묶음}` | 요일 머리말 아래 "여름방학" 띠 |
| `vacation.columnTint` | color / null | 휴가인 날 열 틴트 |

### 1-12. typography / misc
| 토큰 | 타입 | 설명 |
|---|---|---|
| `fonts` | 2-1절 | 테마에서 쓸 폰트 별칭 (`display`, `body`, `hand`) |
| `scale` | 0.7..1.5 | 테마 전체 글자 배율 |
| `emptyState.text`, `font` 묶음 | | "일정이 없어요" |
| `sizeClass.overrides` | `{compact: {...}, medium: {...}, large: {...}}` | 크기 단계별 토큰 덮어쓰기 (2절) |

### 2-1. 폰트
- `res/font/`에 넣는 폰트는 재배포 가능한 라이선스(OFL 등)만. 라이선스 파일을 `app/src/main/assets/licenses/`에 같이 둔다.
- 폰트 참조: `"system"`, `"system-serif"`, 또는 번들 폰트 이름. 편집기에서 목록으로 고름.
- 처음 넣을 후보(디자이너 확정 전 임시): 본문 Pretendard, 손글씨 나눔손글씨 계열 1종. **폰트 확정은 디자이너 요청 항목** (6절).

## 2. 크기 단계 (위젯)

| 단계 | 기준 (위젯 칸 수 대략) | 기본 동작 |
|---|---|---|
| `compact` | 4×2 이하 | 머리말 한 줄, 시간 라벨 2시간마다, 블록 시각 숨김, 포스트잇 제목만 |
| `medium` | 4×3 ~ 4×4 | 기본 |
| `large` | 5×4 이상 | 장소·메모 한 줄, 포스트잇 시각 표시 |

- 단계 판정은 dp 크기로 (`layout/` 순수 함수, 테스트).
- 테마의 `sizeClass.overrides`가 기본 동작을 덮어씀.

## 3. 상태 표현 규칙 (반드시 지킬 것)

| 경우 | 보여야 하는 것 |
|---|---|
| 활성 블록 | 팔레트 색, `block.*` |
| 취소 블록 (휴가/개별/공휴일) | `cancelled.*` — 색 + 무늬/취소선/배지 중 1개 이상 + 사유 아이콘 |
| 특이 일정이 덮은 활성 블록 (KEEP) | 블록 정상 + 포스트잇 위에 붙음 + `underBadge`에 "기존 유지" |
| 특이 일정이 취소시킨 블록 (CANCEL) | 블록이 `cancelled.*`로 + 포스트잇 + `underBadge` "기존 취소". 포스트잇 폭이 `widthRatio` < 1이라 아래 취소 무늬가 옆으로 보여야 함 |
| 오늘 | `today.*` |
| 공휴일 | `dayHeader.holiday*`, `grid.holidayColumnTint` |
| 지금 시각 | `nowLine.*` (표시 범위 밖이면 그리지 않음) |

그리는 순서(z): canvas → 열 틴트(주말/공휴일/휴가/오늘) → 그리드 → 블록(활성·취소) → 시간 축·요일 머리말 → 포스트잇 → 현재 시간선 → 머리말.
(현재 시간선을 포스트잇 위로 올릴지는 `nowLine.aboveStickers` bool 토큰.)

## 4. 기본 프리셋 (Phase 2에서 JSON으로)

| id | 느낌 | 핵심 |
|---|---|---|
| `soft-paper` (기본) | 따뜻한 종이 | 아이보리 바탕, TINT 블록, 노란 포스트잇 + 테이프, 기울기 −3~3° |
| `clean-grid` | 깔끔한 정보형 | 흰/검정, LEFT_BAR 블록, 포스트잇 기울기 0, 그림자 약하게 |
| `playful` | 아이 방 | 큰 둥근 모서리, 진한 팔레트, 압정 포스트잇, 손글씨 폰트 |

세 프리셋 모두 라이트/다크 색을 직접 지정한다(자동 생성에 맡기지 않음).

## 5. 디자인 편집기 (Phase 7)

- 화면 위: **실시간 미리보기** — 크기(compact/medium/large) 탭, 라이트/다크 토글, 샘플 데이터(활성·취소·특이 KEEP·특이 CANCEL·공휴일·오늘·현재 시간선이 모두 보이는 고정 샘플 주) / 실제 스케줄 데이터 전환.
- 화면 아래: 1절 그룹별 접이식 패널. 토큰 타입별 편집기:
  - color → 색상환 + HEX 입력 + 알파 슬라이더, 라이트/다크 따로
  - dp/sp/0..1/deg → 슬라이더 + 숫자 입력
  - enum → 세그먼트 버튼
  - font → 폰트 목록 미리보기
- 미리보기 블록/포스트잇을 탭하면 해당 토큰 그룹으로 바로 이동 ("이 부분 꾸미기").
- 저장 대상 선택: 프리셋 자체 / 이 스케줄 테마 / **이 위젯만** (오버라이드는 바꾼 토큰만 저장).
- 토큰별 "기본값으로" 버튼, 테마 전체 되돌리기(직전 저장본), 테마 복제·이름 변경·삭제(사용 중이면 경고).
- 바꾼 테마를 쓰는 위젯은 저장 즉시 다시 그린다.

## 6. 디자이너에게 요청할 것 (디자인 브리프)

Claude Code는 디자이너 결과물이 오기 전까지 4절 프리셋을 임시값으로 쓴다. 결과물이 오면 토큰 값만 바꾸고 코드는 건드리지 않는 것이 목표다.
디자이너에게는 아래를 요청한다 (형식: Figma 등 + **토큰 표 값** — 1절 토큰 이름을 그대로 써 달라고 요청):

1. **프리셋 3종 확정** (4절): 각 프리셋의 1절 토큰 전체 값, 라이트·다크 둘 다.
2. **위젯 시안**: 크기 3단계 × 라이트/다크 × 프리셋 3종. 각 시안에 아래 상황이 모두 들어간 샘플 주를 사용:
   - 월~금 스케줄과 월~일 스케줄 각각
   - 활성 블록, 짧은 블록(15분), 긴 제목, 같은 시간 겹친 블록 2개
   - 휴가로 취소된 블록, 개별 취소된 블록, 공휴일
   - 특이 일정: 기존 유지(KEEP) 1개, 기존 취소(CANCEL) 1개, 하루 종일 1개, 같은 시간대 포스트잇 2개
   - 오늘 강조 + 현재 시간선
3. **취소 표현**: 색맹 사용자도 구분되는 무늬/배지 안. 사유 아이콘 4종(개별·특이·휴가·공휴일).
4. **포스트잇**: 종이 색 4종 이상, 붙이는 장식(테이프/압정/클립) 그림 — 벡터(SVG → VectorDrawable) 또는 9-patch 없이 코드로 그릴 수 있는 형태 설명, 기울기 범위, 그림자 값.
5. **폰트**: 본문·숫자·손글씨용 폰트와 라이선스 확인 (번들 가능한 것만).
6. **팔레트**: 블록 색 키 8~12개 (이름 + 라이트/다크 값), 글자 대비 WCAG AA 이상.
7. **앱 화면 시안**: 홈(아이 카드), 주간 편집(드래그 중 상태 포함), 월 캘린더, 특이 일정 편집(겹침 유지/취소 선택 UI), 알림 설정, 디자인 편집기.
8. **앱 아이콘** (적응형 아이콘 전경/배경, 단색 아이콘).
9. **위젯 미리보기 이미지** (위젯 선택 목록에 보이는 그림).

디자이너 결과물을 반영하는 작업은 별도 작업으로 지시한다: "DESIGN.md 6절 결과물 반영" — 이때도 토큰 값/에셋만 바꾸고, 새 표현이 필요하면 토큰을 추가한다.
