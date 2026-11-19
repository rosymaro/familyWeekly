package io.github.rosymaro.familyweekly.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.rosymaro.familyweekly.domain.TimeStep
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

fun DayOfWeek.korean(): String = getDisplayName(TextStyle.SHORT, Locale.KOREAN)

/** 요일 여러 개 고르기. [enabled]가 false면 보기만. */
@Composable
fun DayChips(
    available: List<DayOfWeek>,
    selected: Set<DayOfWeek>,
    onChange: (Set<DayOfWeek>) -> Unit,
    enabled: Boolean = true,
    single: Boolean = false,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        available.forEach { d ->
            FilterChip(
                selected = d in selected,
                enabled = enabled,
                onClick = {
                    onChange(
                        when {
                            single -> setOf(d)
                            d in selected -> selected - d
                            else -> selected + d
                        },
                    )
                },
                label = { Text(d.korean()) },
            )
        }
    }
}

/** 5분 단위 시각 고르기: −1시간 −5분 [15:30] +5분 +1시간 */
@Composable
fun TimeStepper(
    label: String,
    minutes: Int,
    onChange: (Int) -> Unit,
    min: Int = 0,
    max: Int = TimeStep.MINUTES_PER_DAY,
) {
    fun set(v: Int) = onChange(v.coerceIn(min, max))
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SmallStep("−1시간") { set(minutes - 60) }
        SmallStep("−5") { set(minutes - TimeStep.STEP_MIN) }
        Text(
            TimeStep.format(minutes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        SmallStep("+5") { set(minutes + TimeStep.STEP_MIN) }
        SmallStep("+1시간") { set(minutes + 60) }
    }
}

@Composable
private fun SmallStep(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 36.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
    ) { Text(text, style = MaterialTheme.typography.labelMedium) }
}

/** 테마 팔레트 색 고르기 (동그라미). [colors]: 키 → 실제 색. */
@Composable
fun ColorKeyChips(colors: Map<String, Int>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { (key, argb) ->
            val isSel = key == selected
            Box(
                Modifier
                    .size(44.dp) // 손가락으로 누르기 쉬운 크기
                    .background(Color(argb), CircleShape)
                    .border(if (isSel) 3.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { onSelect(key) }
                    .semantics { contentDescription = "색 $key" + if (isSel) " (선택됨)" else "" },
            )
        }
    }
}
