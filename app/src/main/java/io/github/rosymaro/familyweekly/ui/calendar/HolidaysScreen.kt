package io.github.rosymaro.familyweekly.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rosymaro.familyweekly.FamilyWeeklyApp
import io.github.rosymaro.familyweekly.domain.HolidayOps
import io.github.rosymaro.familyweekly.ui.common.DateField
import io.github.rosymaro.familyweekly.ui.common.KoreanShortDate
import io.github.rosymaro.familyweekly.ui.common.StoreViewModel
import io.github.rosymaro.familyweekly.ui.common.appViewModelFactory
import java.time.LocalDate

class HolidaysViewModel(app: FamilyWeeklyApp) : StoreViewModel(app) {
    val builtIn = app.holidays.builtIn
    fun hide(date: LocalDate) = update { HolidayOps.hideBuiltIn(it, date) }
    fun removeCustom(date: LocalDate) = update { HolidayOps.removeCustom(it, date) }
    fun add(date: LocalDate, name: String) = update { HolidayOps.add(it, date, name) }

    companion object {
        val Factory = appViewModelFactory { app, _ -> HolidaysViewModel(app) }
    }
}

/** 공휴일 관리: 기본 공휴일 숨기기/보이기, 재량휴업일 같은 날 직접 추가. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidaysScreen(onBack: () -> Unit, vm: HolidaysViewModel = viewModel(factory = HolidaysViewModel.Factory)) {
    val data by vm.data.collectAsStateWithLifecycle()
    var year by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    var adding by remember { mutableStateOf(false) }
    val builtIn = vm.builtIn.filter { it.date.year == year }
    val custom = data.customHolidays.filter { !it.hidden && it.date.year == year }
    val builtInYears = vm.builtIn.map { it.date.year }.toSet()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("공휴일 관리") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, contentDescription = "쉬는 날 추가") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { year-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 해") }
                    Text("${year}년", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { year++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 해") }
                }
            }
            if (custom.isNotEmpty()) {
                item { Text("직접 추가한 쉬는 날", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp)) }
                items(custom, key = { "c" + it.date }) { h ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(h.name)
                            Text(h.date.format(KoreanShortDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { vm.removeCustom(h.date) }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            item { Text("기본 공휴일", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp)) }
            if (year !in builtInYears) {
                item { Text("${year}년 기본 공휴일 자료가 아직 없어요. 필요한 날은 + 로 추가해 주세요.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(builtIn, key = { "b" + it.date }) { h ->
                val hidden = HolidayOps.isHidden(data, h.date)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(h.name, color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        Text(h.date.format(KoreanShortDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = !hidden, onCheckedChange = { on -> if (on) vm.removeCustom(h.date) else vm.hide(h.date) })
                }
            }
        }
    }

    if (adding) {
        var date by remember { mutableStateOf(LocalDate.now()) }
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("쉬는 날 추가") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("이름 (예: 재량휴업일)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    DateField("날짜", date, { date = it })
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.add(date, name); adding = false; year = date.year }) { Text("추가") } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("취소") } },
        )
    }
}
