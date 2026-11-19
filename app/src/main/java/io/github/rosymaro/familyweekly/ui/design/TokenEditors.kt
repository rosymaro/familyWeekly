package io.github.rosymaro.familyweekly.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rosymaro.familyweekly.theme.ColorMath
import io.github.rosymaro.familyweekly.theme.ThemeColor
import io.github.rosymaro.familyweekly.theme.ThemeResolver
import io.github.rosymaro.familyweekly.theme.TokenKind
import io.github.rosymaro.familyweekly.theme.TokenLabels
import io.github.rosymaro.familyweekly.theme.TokenSchema
import io.github.rosymaro.familyweekly.theme.TokenSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlin.math.roundToInt

/** 토큰 한 줄: 이름 + 값 편집기 + (바꿨으면) "기본값으로". */
@Composable
fun TokenRow(
    spec: TokenSpec,
    value: JsonElement?,
    overridden: Boolean,
    onSet: (List<String>, JsonElement) -> Unit,
    onReset: (List<String>) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                TokenLabels.token(spec),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (overridden) FontWeight.Bold else FontWeight.Normal,
                color = if (overridden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (overridden) TextButton(onClick = { onReset(spec.path) }) { Text("기본값으로") }
        }
        val set: (JsonElement) -> Unit = { onSet(spec.path, it) }
        when (val k = spec.kind) {
            TokenKind.Color -> ColorValue(value, spec.nullable, allowAuto = value.isAutoColor(), onSet = set)
            is TokenKind.Number -> NumberValue(k, value, spec.nullable, set)
            TokenKind.Bool -> Switch((value as? JsonPrimitive)?.booleanOrNull ?: false, { set(JsonPrimitive(it)) })
            is TokenKind.Choice -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val cur = (value as? JsonPrimitive)?.content
                k.options.forEach { o -> FilterChip(cur == o, { set(JsonPrimitive(o)) }, label = { Text(TokenLabels.choice(o)) }) }
            }
            TokenKind.Font -> FontValue((value as? JsonPrimitive)?.content, set)
            TokenKind.Text -> TextValue((value as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty(), spec.nullable, set)
            is TokenKind.MapOf -> MapValue(spec, k, value as? JsonObject, onSet)
            TokenKind.ColorList -> ColorListValue(value as? JsonArray, set)
            TokenKind.Presence -> Switch(value != null && value !is JsonNull, { on -> set(if (on) JsonObject(emptyMap()) else JsonNull) })
        }
    }
}

private fun JsonElement?.isAutoColor(): Boolean = (this as? JsonPrimitive)?.content.equals("auto", true) ||
    ((this as? JsonObject)?.get("light") as? JsonPrimitive)?.content.equals("auto", true)

private fun JsonElement?.toThemeColor(): ThemeColor? = when (this) {
    null, is JsonNull -> null
    else -> runCatching { ThemeResolver.json.decodeFromJsonElement(ThemeColor.serializer(), this) }.getOrNull()
}

private fun ThemeColor.toJson(): JsonElement = ThemeResolver.json.encodeToJsonElement(ThemeColor.serializer(), this)

@Composable
private fun Swatch(argb: Int?, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.size(36.dp)
                .background(if (argb != null) Color(argb) else Color.Transparent, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { if (argb == null) Text("–") }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** 색: 라이트/다크 견본 → 누르면 편집 창. "자동"·"없음" 지원. */
@Composable
private fun ColorValue(value: JsonElement?, nullable: Boolean, allowAuto: Boolean, onSet: (JsonElement) -> Unit) {
    val tc = value.toThemeColor()
    var editingDark by remember { mutableStateOf<Boolean?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            tc == null -> Text("없음", color = MaterialTheme.colorScheme.onSurfaceVariant)
            tc.isAuto -> Text("자동 (배경에 맞춤)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                Swatch(runCatching { tc.resolve(false) }.getOrNull(), "라이트") { editingDark = false }
                Swatch(runCatching { tc.resolve(true) }.getOrNull(), if (tc.dark == null) "다크(자동)" else "다크") { editingDark = true }
            }
        }
        if (tc == null || tc.isAuto) TextButton(onClick = { onSet(ThemeColor("#FF888888", "#FF888888").toJson()) }) { Text("색 정하기") }
        if (allowAuto && tc?.isAuto != true) TextButton(onClick = { onSet(JsonPrimitive("auto")) }) { Text("자동") }
        if (nullable && tc != null) TextButton(onClick = { onSet(JsonNull) }) { Text("없음") }
    }
    editingDark?.let { dark ->
        val current = tc ?: ThemeColor("#FF888888")
        ColorDialog(
            title = if (dark) "다크 모드 색" else "라이트 모드 색",
            initial = runCatching { current.resolve(dark) }.getOrDefault(0xFF888888.toInt()),
            onDismiss = { editingDark = null },
            onPick = { argb ->
                val hex = ColorMath.toHex(argb)
                val next = if (dark) current.copy(dark = hex) else current.copy(light = hex)
                onSet(next.toJson())
                editingDark = null
            },
            onAutoDark = if (dark) ({ onSet(current.copy(dark = null).toJson()); editingDark = null }) else null,
        )
    }
}

/** 색 고르기: HEX 입력 + 색상·채도·밝기·불투명도 슬라이더 + 자주 쓰는 색 */
@Composable
fun ColorDialog(title: String, initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit, onAutoDark: (() -> Unit)? = null) {
    val (h0, s0, l0) = remember { ColorMath.toHsl(initial) }
    var h by remember { mutableFloatStateOf(h0) }
    var s by remember { mutableFloatStateOf(s0) }
    var l by remember { mutableFloatStateOf(l0) }
    var a by remember { mutableFloatStateOf(ColorMath.alpha(initial) / 255f) }
    val argb = ColorMath.fromHsl(h, s, l, (a * 255).roundToInt())
    var hex by remember(argb) { mutableStateOf(ColorMath.toHex(argb)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().size(48.dp).background(Color(argb), RoundedCornerShape(8.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)))
                OutlinedTextField(hex, { t ->
                    hex = t
                    runCatching { ColorMath.parse(t) }.getOrNull()?.let { c ->
                        val (nh, ns, nl) = ColorMath.toHsl(c); h = nh; s = ns; l = nl; a = ColorMath.alpha(c) / 255f
                    }
                }, label = { Text("HEX (#AARRGGBB 또는 #RRGGBB)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("색상 ${h.roundToInt()}°", style = MaterialTheme.typography.labelMedium)
                Slider(h, { h = it }, valueRange = 0f..359f)
                Text("채도 ${(s * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(s, { s = it })
                Text("밝기 ${(l * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(l, { l = it })
                Text("불투명도 ${(a * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(a, { a = it })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QUICK.forEach { q ->
                        val c = ColorMath.parse(q)
                        Box(Modifier.size(28.dp).background(Color(c), RoundedCornerShape(6.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)).clickable {
                            val (nh, ns, nl) = ColorMath.toHsl(c); h = nh; s = ns; l = nl; a = 1f
                        })
                    }
                }
                onAutoDark?.let { TextButton(onClick = it) { Text("다크 색은 자동으로 (라이트 색에서 만들기)") } }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(argb) }) { Text("적용") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private val QUICK = listOf(
    "#FFFFFFFF", "#FF000000", "#FF3B3127", "#FFFBF3E6", "#FFF2A65A", "#FF6FC2A5", "#FF6FA8DC", "#FFB39DDB",
    "#FFE88AA0", "#FFE6C84F", "#FFE5533D", "#FF9DB58A", "#FFFFE98A", "#FFFFC7D9", "#FFC4E4FF", "#FFCDEFC1",
)

@Composable
private fun NumberValue(k: TokenKind.Number, value: JsonElement?, nullable: Boolean, onSet: (JsonElement) -> Unit) {
    val v = (value as? JsonPrimitive)?.takeIf { it !is JsonNull }?.floatOrNull
    if (nullable) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (v == null) "자동" else "직접", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Switch(v != null, { on -> onSet(if (on) JsonPrimitive(((k.min + k.max) / 2).let { if (k.integer) it.roundToInt().toFloat() else it }) else JsonNull) })
        }
        if (v == null) return
    }
    var local by remember(v) { mutableFloatStateOf(v ?: k.min) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = local.coerceIn(k.min, k.max),
            onValueChange = { local = snap(it, k) },
            onValueChangeFinished = { onSet(if (k.integer) JsonPrimitive(local.roundToInt()) else JsonPrimitive(local)) },
            valueRange = k.min..k.max,
            modifier = Modifier.weight(1f),
        )
        Text(if (k.integer) local.roundToInt().toString() else "%.2f".format(local).trimEnd('0').trimEnd('.'), modifier = Modifier.padding(start = 8.dp))
    }
}

private fun snap(v: Float, k: TokenKind.Number): Float = (Math.round(v / k.step) * k.step).coerceIn(k.min, k.max)

@Composable
private fun FontValue(current: String?, onSet: (JsonElement) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TokenLabels.fontChoices.forEach { f ->
            FilterChip(current == f, { onSet(JsonPrimitive(f)) }, label = {
                Text(
                    TokenLabels.fontLabel(f),
                    fontFamily = when (f) {
                        "system-serif" -> FontFamily.Serif
                        "system-mono" -> FontFamily.Monospace
                        else -> FontFamily.Default
                    },
                )
            })
        }
    }
}

@Composable
private fun TextValue(current: String, nullable: Boolean, onSet: (JsonElement) -> Unit) {
    var t by remember(current) { mutableStateOf(current) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(t, { t = it }, singleLine = true, modifier = Modifier.weight(1f))
        if (t != current) TextButton(onClick = { onSet(if (nullable && t.isBlank()) JsonNull else JsonPrimitive(t)) }) { Text("적용") }
    }
}

/** 팔레트·종이 색·사유 아이콘 같은 묶음: 키마다 편집 */
@Composable
private fun MapValue(spec: TokenSpec, k: TokenKind.MapOf, value: JsonObject?, onSet: (List<String>, JsonElement) -> Unit) {
    val entries = value?.entries?.toList().orEmpty()
    Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { (key, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(key, style = MaterialTheme.typography.labelMedium, modifier = Modifier.size(width = 84.dp, height = 20.dp))
                when (k.value) {
                    TokenKind.Color -> ColorValue(v, nullable = false, allowAuto = false) { onSet(spec.path + key, it) }
                    TokenKind.Font -> FontValue((v as? JsonPrimitive)?.content) { onSet(spec.path + key, it) }
                    else -> TextValue((v as? JsonPrimitive)?.content.orEmpty(), false) { onSet(spec.path + key, it) }
                }
            }
        }
    }
}

/** 그라데이션 색 목록: 색마다 바꾸기, 추가·빼기 */
@Composable
private fun ColorListValue(value: JsonArray?, onSet: (JsonElement) -> Unit) {
    val list = value?.toList().orEmpty()
    var editing by remember { mutableStateOf<Int?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        list.forEachIndexed { i, el ->
            Swatch(el.toThemeColor()?.let { runCatching { it.resolve(false) }.getOrNull() }, "${i + 1}") { editing = i }
        }
        TextButton(onClick = { onSet(JsonArray(list + ThemeColor("#FFFFFFFF").toJson())) }) { Text("+ 색") }
        if (list.isNotEmpty()) TextButton(onClick = { onSet(JsonArray(list.dropLast(1))) }) { Text("− 색") }
    }
    editing?.let { i ->
        val c = list[i].toThemeColor() ?: ThemeColor("#FFFFFFFF")
        ColorDialog("그라데이션 ${i + 1}번 색 (라이트)", runCatching { c.resolve(false) }.getOrDefault(-1), { editing = null }, { argb ->
            onSet(JsonArray(list.toMutableList().also { it[i] = c.copy(light = ColorMath.toHex(argb)).toJson() }))
            editing = null
        })
    }
}

/** 이 토큰을 지금 보여 줄지 (꺼진 "있다/없다" 묶음 아래면 숨김) */
fun visible(spec: TokenSpec, effective: JsonObject): Boolean {
    val parent = TokenSchema.presenceParent(spec) ?: return true
    val v = TokenSchema.get(effective, parent.path)
    return v != null && v !is JsonNull
}
