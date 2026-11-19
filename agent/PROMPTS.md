# Claude Code 작업 지시문

Phase마다 **새 세션**을 열고(브랜치 하나, PR 하나) 아래 지시문을 그대로 붙여 넣는다.
공통 규칙은 `CLAUDE.md` / `agent/CLAUDE.md`에 있으므로 지시문은 짧게 유지한다.

## 진행 흐름

```
세션 시작 → SPEC.md(+DESIGN.md) 읽기 → 해당 Phase 구현 + JVM 테스트
→ ./gradlew assembleDebug testDebugUnitTest 통과 → SPEC.md 진행 상황 체크
→ PR (한 일 / 가정한 것 / 폰 확인 체크리스트) → CI 통과 시 main에 merge commit
→ CI가 debug-latest 릴리스에 APK 올림 → 폰에 덮어 설치해서 체크리스트 확인
→ 고칠 점은 같은 Phase 후속 작업으로 지시
```

---

### Phase 0
```
agent/SPEC.md를 읽고 Phase 0만 진행해줘.
키스토어와 gradle/debug-signing.gradle은 이미 있으니 재생성하지 말고 적용 줄만 넣어.
```

### Phase 1
```
agent/SPEC.md를 읽고 Phase 1(도메인 + 저장소)만 진행해줘.
4절 모델과 상태 규칙을 순수 Kotlin으로 만들고, Phase 1에 적힌 테스트를 전부 넣어줘.
휴가·특이 일정 취소는 저장하지 말고 계산으로 판정해.
```

### Phase 2
```
agent/SPEC.md와 agent/DESIGN.md를 읽고 Phase 2(테마 토큰 + 레이아웃 엔진)만 진행해줘.
DESIGN.md 1절 토큰을 빠짐없이 WidgetTheme에 넣고, 4절 프리셋 3종을 JSON으로 만들어줘.
코드에 디자인 값을 직접 쓰지 마. 앱에 디버그용 미리보기 화면(DESIGN.md 5절 샘플 주)을 하나 만들어줘.
```

### Phase 3
```
agent/SPEC.md와 agent/DESIGN.md를 읽고 Phase 3(홈 + 스케줄 관리 + 주간 편집)만 진행해줘.
드래그 이동은 5분 스냅·요일 이동·되돌리기까지. 좌표→시각 변환은 순수 함수로 빼서 테스트해줘.
```

### Phase 4
```
agent/SPEC.md와 agent/DESIGN.md를 읽고 Phase 4(캘린더: 특이 일정 + 휴가 + 공휴일)만 진행해줘.
특이 일정은 여러 스케줄에 붙고, 겹치는 기존 블록은 스케줄별로 유지/취소를 반드시 고르게 해줘.
holidays_kr.json의 음력·대체공휴일 날짜는 PR 설명에 확인 목록으로 적어줘.
```

### Phase 5
```
agent/SPEC.md와 agent/DESIGN.md를 읽고 Phase 5(위젯)만 진행해줘.
위젯은 Phase 2 렌더러를 그대로 쓰고, 위젯마다 스케줄·테마·다크 모드를 따로 고를 수 있게 해줘.
```

### Phase 6
```
agent/SPEC.md를 읽고 Phase 6(알림)만 진행해줘.
AlarmPlanner는 순수 Kotlin + 테스트, 등록은 AlarmManager로 차등 등록. 권한이 없을 때 대체 동작과 안내까지 넣어줘.
```

### Phase 7
```
agent/SPEC.md와 agent/DESIGN.md를 읽고 Phase 7(디자인 편집기)만 진행해줘.
DESIGN.md 5절대로, 프리셋/스케줄/이 위젯만 저장 대상을 고를 수 있게 해줘.
```

### Phase 8
```
agent/SPEC.md를 읽고 Phase 8(다듬기)만 진행해줘.
```

---

## 단발 작업 예시

- 디자인 반영: `agent/DESIGN.md 6절 디자이너 결과물을 반영해줘. 첨부: <토큰 표/에셋>. 토큰 값과 에셋만 바꾸고 필요하면 토큰을 추가해.`
- 버그: `폰에서 <증상>. 재현 조건 <...>. 원인을 찾아 테스트로 재현한 뒤 고쳐줘.`
- 스펙 변경: `SPEC.md <절>을 <이렇게> 바꾸고 구현해줘. 저장 형식이 바뀌면 마이그레이션 넣어.`
