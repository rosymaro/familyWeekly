package io.github.rosymaro.familyweekly.ui.calendar

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewModelScope
import io.github.rosymaro.familyweekly.data.HolidayCache
import io.github.rosymaro.familyweekly.data.HolidaySource
import io.github.rosymaro.familyweekly.data.KasiHolidayApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    private val repo = app.holidays
    val holidayUpdates = repo.updates
    val apiKey = app.holidayKey.key

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun builtIn() = repo.builtIn
    fun sourceOf(year: Int) = repo.sourceOf(year)

    /** 인증키 저장 후 바로 받아 보기 (키 확인 겸). */
    fun saveKeyAndSync(key: String) {
        app.holidayKey.set(key)
        sync()
    }

    fun removeKey() {
        app.holidayKey.set(null)
        _message.value = "인증키를 지웠어요. 받아 둔 공휴일은 그대로 써요."
    }

    fun sync() {
        val key = apiKey.value ?: return
        if (_syncing.value) return
        _syncing.value = true
        _message.value = null
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { app.holidaySyncer.sync(key) }
            _syncing.value = false
            _message.value = when {
                r.error == null -> "${r.fetchedYears.joinToString("·")}년 공휴일 ${r.count}개를 받았어요"
                r.fetchedYears.isNotEmpty() -> "${r.fetchedYears.joinToString("·")}년은 받았지만 일부 실패: ${r.error}"
                else -> r.error
            }
        }
    }

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
    val updates by vm.holidayUpdates.collectAsStateWithLifecycle()
    var year by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    var adding by remember { mutableStateOf(false) }
    val all = remember(updates) { vm.builtIn() }
    val builtIn = all.filter { it.date.year == year }
    val custom = data.customHolidays.filter { !it.hidden && it.date.year == year }
    val source = remember(updates, year) { vm.sourceOf(year) }

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
            item { SyncCard(vm, updates) }
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
            item {
                Text(
                    "공휴일 · " + when (source) {
                        HolidaySource.FETCHED -> "한국천문연구원에서 받은 자료"
                        HolidaySource.BUNDLED -> "앱에 들어 있는 자료 (자동 받기를 켜면 공식 자료로 바뀌어요)"
                        HolidaySource.NONE -> "자료 없음"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (source == HolidaySource.NONE) {
                item { Text("${year}년 공휴일 자료가 아직 없어요. 자동 받기를 켜거나 필요한 날을 + 로 추가해 주세요.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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

/** 공휴일 자동 받기 (한국천문연구원 특일 정보, 공공데이터포털 인증키). */
@Composable
private fun SyncCard(vm: HolidaysViewModel, cache: HolidayCache) {
    val key by vm.apiKey.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editingKey by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("공휴일 자동 받기", style = MaterialTheme.typography.titleSmall)
            Text(
                "한국천문연구원 공식 자료에서 올해·내년 공휴일(대체공휴일·임시공휴일·선거일 포함)을 받아 한 달마다 새로 고쳐요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (key == null || editingKey) {
                Text(
                    "① 공공데이터포털에 로그인 → '한국천문연구원_특일 정보' 활용신청 (무료, 자동 승인)\n" +
                        "② 마이페이지의 일반 인증키(Decoding)를 복사해 아래에 붙여 넣기\n" +
                        "   발급 직후에는 1~2시간 뒤부터 쓸 수 있어요",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KasiHolidayApi.GUIDE_URL))) }
                }) { Text("공공데이터포털 열기") }
                OutlinedTextField(input, { input = it }, label = { Text("인증키") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = input.isNotBlank() && !syncing, onClick = { vm.saveKeyAndSync(input); editingKey = false; input = "" }) { Text("저장하고 받기") }
                    if (key != null) TextButton(onClick = { editingKey = false; input = "" }) { Text("취소") }
                }
            } else {
                val last = cache.fetchedInstant?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREAN))
                Text(
                    if (last != null) "마지막으로 받음: $last · ${cache.yearsSorted().joinToString("·")}년" else "아직 받지 않았어요",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(enabled = !syncing, onClick = vm::sync) { Text(if (syncing) "받는 중…" else "지금 받기") }
                    TextButton(onClick = { editingKey = true }) { Text("인증키 바꾸기") }
                    TextButton(onClick = vm::removeKey) { Text("지우기") }
                }
            }
            if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
