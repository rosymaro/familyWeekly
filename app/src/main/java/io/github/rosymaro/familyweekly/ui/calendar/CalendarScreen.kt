package io.github.rosymaro.familyweekly.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rosymaro.familyweekly.domain.AppData
import io.github.rosymaro.familyweekly.domain.CalendarDay
import io.github.rosymaro.familyweekly.domain.CalendarMonth
import io.github.rosymaro.familyweekly.domain.HolidayOps
import io.github.rosymaro.familyweekly.domain.OverlapAction
import io.github.rosymaro.familyweekly.domain.SpecialEvent
import io.github.rosymaro.familyweekly.domain.TimeStep
import io.github.rosymaro.familyweekly.theme.ThemeResolver
import io.github.rosymaro.familyweekly.ui.common.KoreanDate
import io.github.rosymaro.familyweekly.ui.common.korean
import io.github.rosymaro.familyweekly.ui.common.scheduleColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onAddEvent: (LocalDate) -> Unit,
    onEditEvent: (String) -> Unit,
    onAddVacation: (LocalDate) -> Unit,
    onEditVacation: (String) -> Unit,
    onManageHolidays: () -> Unit,
    vm: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory),
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val monthStr by vm.month.collectAsStateWithLifecycle()
    val selectedStr by vm.selected.collectAsStateWithLifecycle()
    val month = YearMonth.parse(monthStr)
    val selected = LocalDate.parse(selectedStr)
    val today = LocalDate.now()
    val dark = ThemeResolver.isDark(data.settings.darkMode, isSystemInDarkTheme())
    val holidays = remember(data.customHolidays) { vm.holidays() }
    val colors = remember(data.schedules, dark) { scheduleColors(vm.presets, data, dark) }
    val weeks = remember(month, data, holidays, today) { CalendarMonth.weeks(month, data, holidays, today) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("캘린더") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
                actions = {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "메뉴") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("공휴일 관리") }, onClick = { menu = false; onManageHolidays() })
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shiftMonth(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "지난달") }
                Text("${month.year}년 ${month.monthValue}월", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (month != YearMonth.now()) TextButton(onClick = vm::goToday) { Text("오늘") }
                IconButton(onClick = { vm.shiftMonth(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달") }
            }
            if (data.schedules.size > 1) ScheduleLegend(data, colors)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                DayOfWeek.entries.forEach { d ->
                    Text(
                        d.korean(),
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = weekdayColor(d, holiday = false),
                    )
                }
            }
            Column(Modifier.padding(horizontal = 8.dp)) {
                weeks.forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            DayCell(
                                day = day,
                                selected = day.date == selected,
                                colors = colors,
                                modifier = Modifier.weight(1f),
                                onClick = { vm.select(day.date) },
                            )
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            DayPanel(
                date = selected,
                day = weeks.flatten().firstOrNull { it.date == selected },
                data = data,
                colors = colors,
                holidayHidden = HolidayOps.isHidden(data, selected),
                isCustomHoliday = data.customHolidays.any { it.date == selected && !it.hidden },
                onAddEvent = { onAddEvent(selected) },
                onEditEvent = onEditEvent,
                onAddVacation = { onAddVacation(selected) },
                onEditVacation = onEditVacation,
                onHideHoliday = { vm.hideHoliday(selected) },
                onManageHolidays = onManageHolidays,
            )
        }
    }
}

@Composable
private fun weekdayColor(d: DayOfWeek, holiday: Boolean): Color = when {
    holiday || d == DayOfWeek.SUNDAY -> MaterialTheme.colorScheme.error
    d == DayOfWeek.SATURDAY -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun ScheduleLegend(data: AppData, colors: Map<String, Color>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        data.schedules.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(colors[s.id] ?: Color.Gray, CircleShape))
                Text(" ${s.name}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay, selected: Boolean, colors: Map<String, Color>, modifier: Modifier, onClick: () -> Unit) {
    val alpha = if (day.inMonth) 1f else 0.38f
    val desc = buildString {
        append(day.date.format(KoreanDate))
        day.holiday?.let { append(", ${it.name}") }
        if (day.vacations.isNotEmpty()) append(", 휴가 ${day.vacations.joinToString { it.title }}")
        if (day.events.isNotEmpty()) append(", 특이 일정 ${day.events.size}개")
    }
    Column(
        modifier
            .height(66.dp)
            .padding(1.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .then(if (day.isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${day.date.dayOfMonth}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            color = weekdayColor(day.date.dayOfWeek, day.holiday != null).copy(alpha = alpha),
        )
        day.holiday?.let {
            Text(it.name, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.error.copy(alpha = alpha))
        }
        Spacer(Modifier.weight(1f))
        if (day.vacations.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f * alpha), RoundedCornerShape(2.dp)))
            Spacer(Modifier.height(2.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            day.events.take(4).forEach { e ->
                Box(Modifier.size(6.dp).background((colors[e.scheduleIds.firstOrNull()] ?: MaterialTheme.colorScheme.primary).copy(alpha = alpha), CircleShape))
            }
        }
    }
}

@Composable
private fun DayPanel(
    date: LocalDate,
    day: CalendarDay?,
    data: AppData,
    colors: Map<String, Color>,
    holidayHidden: Boolean,
    isCustomHoliday: Boolean,
    onAddEvent: () -> Unit,
    onEditEvent: (String) -> Unit,
    onAddVacation: () -> Unit,
    onEditVacation: (String) -> Unit,
    onHideHoliday: () -> Unit,
    onManageHolidays: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(date.format(KoreanDate), style = MaterialTheme.typography.titleMedium)

        day?.holiday?.let { h ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎌 ${h.name}", color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                if (isCustomHoliday) TextButton(onClick = onManageHolidays) { Text("관리") }
                else TextButton(onClick = onHideHoliday) { Text("이 공휴일 숨기기") }
            }
        }
        if (day?.holiday == null && holidayHidden) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("숨긴 공휴일이 있는 날", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onManageHolidays) { Text("관리") }
            }
        }

        day?.vacations?.forEach { v ->
            Card(Modifier.fillMaxWidth().clickable { onEditVacation(v.id) }) {
                Column(Modifier.padding(12.dp)) {
                    Text("🏖 ${v.title}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${v.from.monthValue}/${v.from.dayOfMonth} ~ ${v.to.monthValue}/${v.to.dayOfMonth} · " +
                            v.scheduleIds.mapNotNull { data.schedule(it)?.name }.joinToString() +
                            if (v.blockIds == null) " · 모든 일정" else " · 일부 일정 ${v.blockIds.size}개",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        day?.events?.forEach { e -> EventRow(e, data, colors) { onEditEvent(e.id) } }

        if (day != null && day.events.isEmpty() && day.vacations.isEmpty() && day.holiday == null) {
            Text("이날은 특이 일정이 없어요", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAddEvent, enabled = data.schedules.isNotEmpty()) { Text("+ 특이 일정") }
            OutlinedButton(onClick = onAddVacation, enabled = data.schedules.isNotEmpty()) { Text("+ 휴가") }
        }
        if (data.schedules.isEmpty()) {
            Text("먼저 홈에서 스케줄을 추가해 주세요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EventRow(e: SpecialEvent, data: AppData, colors: Map<String, Color>, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "📌 ${e.title}  " + if (e.allDay) "하루 종일" else "${TimeStep.format(e.startMin)}~${TimeStep.format(e.endMin)}",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                e.scheduleIds.forEach { sid ->
                    val name = data.schedule(sid)?.name ?: return@forEach
                    val action = when (e.overlapActions[sid]) {
                        OverlapAction.CANCEL -> " (기존 취소)"
                        OverlapAction.KEEP -> " (기존 유지)"
                        null -> ""
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(colors[sid] ?: Color.Gray, CircleShape))
                        Text(" $name$action", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            e.memo?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
