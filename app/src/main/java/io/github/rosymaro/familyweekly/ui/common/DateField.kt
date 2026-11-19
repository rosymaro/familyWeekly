package io.github.rosymaro.familyweekly.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.rosymaro.familyweekly.domain.AppData
import io.github.rosymaro.familyweekly.theme.ThemeLibrary
import io.github.rosymaro.familyweekly.theme.ThemeResolver
import io.github.rosymaro.familyweekly.theme.SizeClass
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

val KoreanDate: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
val KoreanShortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

/** 누르면 날짜 고르기 창이 뜨는 버튼. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier.fillMaxWidth()) {
        Text("$label  ${date.format(KoreanDate)}")
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("취소") } },
        ) { DatePicker(state = state) }
    }
}

/**
 * 스케줄마다 구분 색 (달력 점, 칩). 기본 테마 팔레트를 스케줄 순서대로 돌려 쓴다.
 * 앱 화면용 표시이므로 위젯 디자인 토큰을 그대로 빌려 쓴다.
 */
fun scheduleColors(library: ThemeLibrary, data: AppData, dark: Boolean): Map<String, Color> {
    val rt = library.resolve(data, null, SizeClass.MEDIUM, dark)
    val colors = rt.theme.block.palette.filterKeys { it != "default" }.values.map { Color(rt.color(it)) }
    if (colors.isEmpty()) return emptyMap()
    return data.schedules.mapIndexed { i, s -> s.id to colors[i % colors.size] }.toMap()
}
