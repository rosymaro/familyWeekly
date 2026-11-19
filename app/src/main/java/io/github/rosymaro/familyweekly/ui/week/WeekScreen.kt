package io.github.rosymaro.familyweekly.ui.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rosymaro.familyweekly.domain.AppData
import io.github.rosymaro.familyweekly.domain.BlockDraft
import io.github.rosymaro.familyweekly.domain.BlockOps
import io.github.rosymaro.familyweekly.domain.HolidayCalendar
import io.github.rosymaro.familyweekly.domain.OccurrenceResolver
import io.github.rosymaro.familyweekly.domain.SpecialEvent
import io.github.rosymaro.familyweekly.domain.TimeStep
import io.github.rosymaro.familyweekly.domain.WeekRange
import io.github.rosymaro.familyweekly.layout.BlockLayout
import io.github.rosymaro.familyweekly.layout.DragTarget
import io.github.rosymaro.familyweekly.layout.HitTest
import io.github.rosymaro.familyweekly.layout.RenderSpec
import io.github.rosymaro.familyweekly.layout.WeekLayout
import io.github.rosymaro.familyweekly.layout.WeekLayoutResult
import io.github.rosymaro.familyweekly.render.WeekPainter
import io.github.rosymaro.familyweekly.theme.ResolvedTheme
import io.github.rosymaro.familyweekly.theme.SizeClass
import io.github.rosymaro.familyweekly.theme.ThemeResolver
import io.github.rosymaro.familyweekly.ui.common.korean
import io.github.rosymaro.familyweekly.ui.common.resolveScheduleTheme
import io.github.rosymaro.familyweekly.ui.home.rememberMinuteTicker
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** 드래그 중 상태. [origin]은 드래그 시작 때의 블록 위치. */
private data class DragState(val origin: BlockLayout, val dx: Float, val dy: Float, val target: DragTarget)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(
    onBack: () -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
    vm: WeekViewModel = viewModel(factory = WeekViewModel.Factory),
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val offset by vm.weekOffset.collectAsStateWithLifecycle()
    val schedule = data.schedule(vm.scheduleId)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val now = rememberMinuteTicker()
    val dark = ThemeResolver.isDark(data.settings.darkMode, isSystemInDarkTheme())
    val holidayUpdates by vm.holidayUpdates.collectAsStateWithLifecycle()
    val holidays = remember(data.customHolidays, holidayUpdates) { vm.holidays() }
    val week = WeekRange.of(now.toLocalDate()).shift(offset)

    var editing by remember { mutableStateOf<BlockEditState?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var postItMenu by remember { mutableStateOf<SpecialEvent?>(null) }
    var showDetached by remember { mutableStateOf(false) }
    val detached = remember(data, vm.scheduleId, week, holidays) {
        OccurrenceResolver.resolve(data, vm.scheduleId, week, holidays)?.detachedEvents.orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(schedule?.let { listOfNotNull(it.emoji, it.name).joinToString(" ") } ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
                actions = {
                    if (schedule != null) IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, contentDescription = "스케줄 설정") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (schedule == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("스케줄이 없어요") }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            WeekNavBar(week, isThisWeek = offset == 0L, onPrev = { vm.shiftWeek(-1) }, onNext = { vm.shiftWeek(1) }, onToday = vm::goToday)
            if (detached.isNotEmpty()) {
                AssistChip(
                    onClick = { showDetached = true },
                    label = { Text("뗀 포스트잇 ${detached.size}개 · 다시 붙이기") },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            WeekEditor(
                data = data,
                scheduleId = schedule.id,
                week = week,
                holidays = holidays,
                now = now,
                dark = dark,
                vm = vm,
                onSwipeWeek = { vm.shiftWeek(it) },
                onEditBlock = { editing = it },
                onEventTap = { postItMenu = it },
                onMoved = { title, from, to ->
                    scope.launch {
                        val res = snackbar.showSnackbar(
                            message = "$title → ${schedule.days.days[to.dayIndex].korean()} ${TimeStep.format(to.startMin)}",
                            actionLabel = "되돌리기",
                            duration = SnackbarDuration.Short,
                        )
                        if (res == SnackbarResult.ActionPerformed) from()
                    }
                },
            )
        }
    }

    editing?.let { st ->
        val palette = remember(schedule?.themeId, dark) {
            resolveScheduleTheme(vm.presets, schedule?.themeId, SizeClass.LARGE, dark).let { rt ->
                rt.theme.block.palette.mapValues { rt.color(it.value) }
            }
        }
        BlockEditSheet(
            state = st,
            availableDays = schedule?.days?.days.orEmpty(),
            palette = palette,
            inheritedAlarm = schedule?.alarmDefaults ?: data.settings.defaultAlarm,
            onDismiss = { editing = null },
            onSave = { draft, applyToGroup -> vm.saveBlock(draft, st.blockId, applyToGroup); editing = null },
            onDelete = { whole -> st.blockId?.let { vm.deleteBlock(it, whole) }; editing = null },
            onToggleCancel = {
                val id = st.blockId
                val date = st.date
                if (id != null && date != null) vm.toggleCancellation(id, date)
                editing = null
            },
        )
    }

    // 포스트잇 탭: 수정 / 떼기
    postItMenu?.let { ev ->
        AlertDialog(
            onDismissRequest = { postItMenu = null },
            title = { Text("📌 ${ev.title}") },
            text = {
                Text(
                    (if (ev.allDay) "하루 종일" else "${TimeStep.format(ev.startMin)}~${TimeStep.format(ev.endMin)}") +
                        "\n\n떼면 이 시간표(위젯 포함)에서만 안 보여요. 특이 일정은 캘린더에 그대로 남고, 기존 일정 유지/취소도 그대로예요.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setDetached(ev.id, true)
                    postItMenu = null
                    scope.launch {
                        val res = snackbar.showSnackbar("'${ev.title}' 포스트잇을 뗐어요", actionLabel = "되돌리기", duration = SnackbarDuration.Short)
                        if (res == SnackbarResult.ActionPerformed) vm.setDetached(ev.id, false)
                    }
                }) { Text("떼기") }
            },
            dismissButton = {
                TextButton(onClick = { postItMenu = null; onOpenEvent(ev.id) }) { Text("수정") }
            },
        )
    }

    if (showDetached) {
        AlertDialog(
            onDismissRequest = { showDetached = false },
            title = { Text("뗀 포스트잇") },
            text = {
                Column {
                    if (detached.isEmpty()) Text("이번 주에 뗀 포스트잇이 없어요")
                    detached.forEach { ev ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${ev.date.dayOfWeek.korean()} ${ev.title}" + if (ev.allDay) " (하루 종일)" else " ${TimeStep.format(ev.startMin)}",
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { vm.setDetached(ev.id, false) }) { Text("다시 붙이기") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDetached = false }) { Text("닫기") } },
        )
    }

    if (showSettings && schedule != null) {
        ScheduleSettingsDialog(schedule, data.settings.defaultAlarm, { showSettings = false }) { updated ->
            vm.updateSchedule { updated }
            showSettings = false
        }
    }

}

@Composable
private fun WeekNavBar(week: WeekRange, isThisWeek: Boolean, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    val f = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "지난주") }
        Text(
            "${week.monday.format(f)} ~ ${week.sunday.format(f)}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        if (!isThisWeek) TextButton(onClick = onToday) { Text("오늘") }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 주") }
    }
}

@Composable
private fun WeekEditor(
    data: AppData,
    scheduleId: String,
    week: WeekRange,
    holidays: HolidayCalendar,
    now: LocalDateTime,
    dark: Boolean,
    vm: WeekViewModel,
    onSwipeWeek: (Long) -> Unit,
    onEditBlock: (BlockEditState) -> Unit,
    onEventTap: (SpecialEvent) -> Unit,
    onMoved: (title: String, undo: () -> Unit, to: DragTarget) -> Unit,
) {
    val schedule = data.schedule(scheduleId) ?: return
    val density = LocalDensity.current.density
    val fontScale = LocalConfiguration.current.fontScale
    val haptic = LocalHapticFeedback.current
    val painter = remember { WeekPainter() }
    val rt: ResolvedTheme = remember(schedule.themeId, dark) {
        resolveScheduleTheme(vm.presets, schedule.themeId, SizeClass.LARGE, dark, EditorTheme.override)
    }

    var drag by remember { mutableStateOf<DragState?>(null) }
    var peek by remember { mutableStateOf(false) }

    // 드래그 중에는 옮긴 상태로 미리 계산해서 보여 준다 (겹치면 lane으로 나란히)
    val shownData = drag?.let { d ->
        BlockOps.move(data, scheduleId, d.origin.occurrence.block.id, schedule.days.days[d.target.dayIndex], d.target.startMin)
    } ?: data

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = (maxWidth.value * density).roundToInt().coerceAtLeast(1)
        val resolved = remember(shownData, scheduleId, week, holidays, now) {
            OccurrenceResolver.resolve(shownData, scheduleId, week, holidays, now)?.let(EditorTheme::widenRange)
        } ?: return@BoxWithConstraints
        val layout = remember(resolved, rt, widthPx, density, fontScale) {
            val tall = RenderSpec(widthPx, 100_000, density, fontScale)
            val first = WeekLayout.compute(resolved, rt.theme, tall, now, SizeClass.LARGE)
            val heightPx = (first.grid.bottom + rt.theme.canvas.padding.bottom * density).roundToInt()
            WeekLayout.compute(resolved, rt.theme, tall.copy(heightPx = heightPx), now, SizeClass.LARGE)
        }
        val layoutState = rememberUpdatedState(layout)
        val dataState = rememberUpdatedState(data)
        val heightDp = (layout.spec.heightPx / density).dp
        val scroll = rememberScrollState()

        // 처음 열면 지금 시각(또는 첫 일정) 근처로 스크롤
        LaunchedEffect(scheduleId) {
            val target = layout.yOf(maxOf(layout.displayStartMin, TimeStep.fromLocalTime(now.toLocalTime()) - 90))
            scroll.scrollTo((target - layout.grid.top).roundToInt().coerceAtLeast(0))
        }

        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(heightDp)) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(heightDp)
                        .semantics { contentDescription = "${schedule.name} 주간 시간표. 일정을 누르면 수정, 길게 눌러 끌면 이동, 빈 칸을 길게 누르면 새 일정" }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { /* 길게 누르기는 아래 드래그 감지에서 */ },
                                onTap = { pos ->
                                    val l = layoutState.value
                                    val post = HitTest.postItAt(l, pos.x, pos.y)
                                    if (post != null) {
                                        onEventTap(post.event.event)
                                        return@detectTapGestures
                                    }
                                    HitTest.blockAt(l, pos.x, pos.y)?.let { b ->
                                        onEditBlock(editStateFor(dataState.value, scheduleId, b))
                                    }
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            var pendingNew: Pair<Int, Int>? = null
                            detectDragGesturesAfterLongPress(
                                onDragStart = { pos ->
                                    val l = layoutState.value
                                    pendingNew = null
                                    when {
                                        HitTest.postItAt(l, pos.x, pos.y) != null -> peek = true
                                        else -> {
                                            val b = HitTest.blockAt(l, pos.x, pos.y)
                                            if (b != null) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                drag = DragState(b, 0f, 0f, HitTest.dragTarget(l, b, 0f, 0f))
                                            } else {
                                                pendingNew = HitTest.newBlockAt(l, pos.x, pos.y)
                                            }
                                        }
                                    }
                                },
                                onDrag = { change, amount ->
                                    val d = drag ?: return@detectDragGesturesAfterLongPress
                                    change.consume()
                                    val nx = d.dx + amount.x
                                    val ny = d.dy + amount.y
                                    val t = HitTest.dragTarget(layoutState.value, d.origin, nx, ny)
                                    if (t != d.target) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    drag = d.copy(dx = nx, dy = ny, target = t)
                                },
                                onDragEnd = {
                                    peek = false
                                    drag?.let { d ->
                                        drag = null
                                        val b = d.origin.occurrence.block
                                        val to = d.target
                                        val days = schedule.days.days
                                        val fromIndex = days.indexOf(b.day)
                                        if (to.dayIndex != fromIndex || to.startMin != b.startMin) {
                                            vm.moveBlock(b.id, days[to.dayIndex], to.startMin)
                                            onMoved(b.title, { vm.moveBlock(b.id, b.day, b.startMin) }, to)
                                        }
                                    }
                                    pendingNew?.let { (dayIdx, start) ->
                                        pendingNew = null
                                        onEditBlock(newBlockState(schedule.days.days[dayIdx], start))
                                    }
                                },
                                onDragCancel = {
                                    peek = false
                                    drag = null
                                    pendingNew = null
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onHorizontalDrag = { _, dx -> if (drag == null) total += dx },
                                onDragEnd = {
                                    val threshold = 96.dp.toPx()
                                    if (drag == null && abs(total) > threshold) onSwipeWeek(if (total < 0) 1 else -1)
                                },
                            )
                        },
                ) {
                    val shown: WeekLayoutResult = if (peek) layout.copy(postIts = emptyList()) else layout
                    drawIntoCanvas { painter.draw(it.nativeCanvas, shown, rt) }
                    drag?.let { d ->
                        val box = HitTest.targetBox(layout, d.target)
                        val accent = Color(rt.color(rt.theme.nowLine.color))
                        drawRoundRect(
                            color = accent,
                            topLeft = Offset(box.left, box.top),
                            size = Size(box.width, box.height),
                            cornerRadius = CornerRadius(6.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }

                // 드래그 중 시각 말풍선
                drag?.let { d ->
                    val box = HitTest.targetBox(layout, d.target)
                    val bubbleY = (box.top / density - 28).coerceAtLeast(0f)
                    Text(
                        "${TimeStep.format(d.target.startMin)}~${TimeStep.format(d.target.endMin)}",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .offset { IntOffset(box.left.roundToInt(), (bubbleY * density).roundToInt()) }
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        if (peek) {
            Text(
                "포스트잇을 잠시 숨겼어요",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                    .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}

private fun editStateFor(data: AppData, scheduleId: String, b: BlockLayout): BlockEditState {
    val schedule = data.schedule(scheduleId)!!
    val block = schedule.blocks.firstOrNull { it.id == b.occurrence.block.id } ?: b.occurrence.block
    val group = BlockOps.groupOf(schedule, block.id)
    return BlockEditState(
        blockId = block.id,
        date = b.occurrence.date,
        draft = BlockDraft(
            title = block.title,
            days = group.map { it.day }.toSet().ifEmpty { setOf(block.day) },
            startMin = block.startMin,
            endMin = block.endMin,
            colorKey = block.colorKey,
            place = block.place,
            memo = block.memo,
            alarm = block.alarm,
        ).let { d -> if (group.size > 1) d else d.copy(days = setOf(block.day)) },
        groupSize = group.size,
        cancelledThisWeek = BlockOps.isCancelled(schedule, block.id, b.occurrence.date),
    ).let { st ->
        // 시트에서 "이 요일만"으로 바꾸면 첫 요일이 이 블록 요일이 되도록 순서를 맞춤
        if (st.groupSize > 1) st.copy(draft = st.draft.copy(days = linkedSetOf(block.day) + st.draft.days)) else st
    }
}

private fun newBlockState(day: java.time.DayOfWeek, startMin: Int) = BlockEditState(
    blockId = null,
    date = null,
    draft = BlockDraft(title = "", days = setOf(day), startMin = startMin, endMin = (startMin + 60).coerceAtMost(TimeStep.MINUTES_PER_DAY)),
    groupSize = 0,
    cancelledThisWeek = false,
)
