package io.github.rosymaro.familyweekly.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rosymaro.familyweekly.FamilyWeeklyApp
import io.github.rosymaro.familyweekly.theme.DesignTarget
import io.github.rosymaro.familyweekly.theme.ThemeEntry
import io.github.rosymaro.familyweekly.theme.ThemeOps
import io.github.rosymaro.familyweekly.ui.common.StoreViewModel
import io.github.rosymaro.familyweekly.ui.common.appViewModelFactory

class DesignLibraryViewModel(app: FamilyWeeklyApp) : StoreViewModel(app) {
    val library = app.themes
    private val presets = app.presets
    fun duplicate(id: String, name: String) = update { ThemeOps.duplicate(it, id, name).first }
    fun rename(id: String, name: String) = update { ThemeOps.rename(it, id, name, presets) }
    fun delete(id: String) = update { ThemeOps.delete(it, id) }
    fun clearOverride(target: DesignTarget) = update { ThemeOps.saveOverride(it, target, io.github.rosymaro.familyweekly.theme.ThemeLibrary.EMPTY, presets) }

    companion object {
        val Factory = appViewModelFactory { app, _ -> DesignLibraryViewModel(app) }
    }
}

private sealed interface LibDialog {
    data class Duplicate(val e: ThemeEntry) : LibDialog
    data class Rename(val e: ThemeEntry) : LibDialog
    data class Delete(val e: ThemeEntry) : LibDialog
}

/** 디자인 목록: 프리셋·내 디자인 꾸미기/복제/이름/삭제, 아이별·위젯별 따로 꾸미기. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignLibraryScreen(
    onBack: () -> Unit,
    onEdit: (DesignTarget) -> Unit,
    vm: DesignLibraryViewModel = viewModel(factory = DesignLibraryViewModel.Factory),
) {
    val data by vm.data.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<LibDialog?>(null) }
    val entries = vm.library.entries(data)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("디자인") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(
                    "디자인을 고치면 그 디자인을 쓰는 모든 아이·위젯이 바뀌어요. 한 아이나 한 위젯만 다르게 하려면 아래 '따로 꾸미기'를 쓰세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Text("디자인", style = MaterialTheme.typography.titleSmall) }
            items(entries, key = { "t" + it.id }) { e ->
                val usage = ThemeOps.usage(data, e.id) + if (e.id == "soft-paper") data.schedules.count { it.themeId == null } else 0
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(
                                    if (e.isPreset) "기본 디자인" else "내 디자인 (${vm.library.name(data, e.baseId)} 바탕)",
                                    if (e.isPreset && e.isEdited) "고침" else null,
                                    if (usage > 0) "${usage}곳에서 사용" else null,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onEdit(DesignTarget.Theme(e.id)) }) { Text("꾸미기") }
                        Box {
                            var menu by remember { mutableStateOf(false) }
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "${e.name} 메뉴") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("복제") }, onClick = { menu = false; dialog = LibDialog.Duplicate(e) })
                                DropdownMenuItem(text = { Text("이름 변경") }, onClick = { menu = false; dialog = LibDialog.Rename(e) })
                                if (!e.isPreset) DropdownMenuItem(text = { Text("삭제") }, onClick = { menu = false; dialog = LibDialog.Delete(e) })
                                if (e.isPreset && e.isEdited) DropdownMenuItem(text = { Text("처음 상태로") }, onClick = { menu = false; dialog = LibDialog.Delete(e) })
                            }
                        }
                    }
                }
            }

            if (data.schedules.isNotEmpty()) {
                item { Text("아이별로 따로 꾸미기", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                items(data.schedules, key = { "s" + it.id }) { s ->
                    OverrideRow(
                        title = listOfNotNull(s.emoji, s.name).joinToString(" "),
                        subtitle = "디자인: ${vm.library.name(data, s.themeId)}" + if (s.themeOverride != null) " + 따로 꾸밈" else "",
                        hasOverride = s.themeOverride != null,
                        onEdit = { onEdit(DesignTarget.ScheduleOnly(s.id)) },
                        onClear = { vm.clearOverride(DesignTarget.ScheduleOnly(s.id)) },
                    )
                }
            }
            if (data.widgets.isNotEmpty()) {
                item { Text("위젯별로 따로 꾸미기", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                items(data.widgets, key = { "w" + it.appWidgetId }) { w ->
                    val s = w.scheduleId?.let { data.schedule(it) }
                    OverrideRow(
                        title = "위젯 · ${s?.name ?: "연결 없음"}",
                        subtitle = "디자인: ${vm.library.name(data, w.themeId ?: s?.themeId)}" + if (w.themeOverride != null) " + 따로 꾸밈" else "",
                        hasOverride = w.themeOverride != null,
                        onEdit = { onEdit(DesignTarget.WidgetOnly(w.appWidgetId)) },
                        onClear = { vm.clearOverride(DesignTarget.WidgetOnly(w.appWidgetId)) },
                    )
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is LibDialog.Duplicate -> NameDialog("디자인 복제", "${d.e.name} 복사본", { dialog = null }) { vm.duplicate(d.e.id, it); dialog = null }
        is LibDialog.Rename -> NameDialog("이름 변경", d.e.name, { dialog = null }) { vm.rename(d.e.id, it); dialog = null }
        is LibDialog.Delete -> {
            val usage = ThemeOps.usage(data, d.e.id)
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(if (d.e.isPreset) "'${d.e.name}' 처음 상태로" else "'${d.e.name}' 삭제") },
                text = {
                    Text(
                        if (d.e.isPreset) "고친 내용이 모두 사라지고 앱에 들어 있던 모양으로 돌아가요."
                        else if (usage > 0) "${usage}곳(아이·위젯)에서 쓰고 있어요. 지우면 그곳은 기본 디자인으로 바뀌어요."
                        else "되돌릴 수 없어요.",
                    )
                },
                confirmButton = { TextButton(onClick = { vm.delete(d.e.id); dialog = null }) { Text(if (d.e.isPreset) "처음 상태로" else "삭제", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("취소") } },
            )
        }
    }
}

@Composable
private fun OverrideRow(title: String, subtitle: String, hasOverride: Boolean, onEdit: () -> Unit, onClear: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onEdit) { Text("꾸미기") }
            if (hasOverride) TextButton(onClick = onClear) { Text("지우기") }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onOk(name) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
