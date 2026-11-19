package io.github.rosymaro.familyweekly.theme

import io.github.rosymaro.familyweekly.domain.CancelReason
import io.github.rosymaro.familyweekly.layout.WeekLayoutResult
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** 편집기에서 다루는 토큰 종류. */
sealed interface TokenKind {
    data object Color : TokenKind
    data class Number(val min: Float, val max: Float, val step: Float, val integer: Boolean) : TokenKind
    data object Bool : TokenKind
    data class Choice(val options: List<String>) : TokenKind
    data object Text : TokenKind
    data object Font : TokenKind
    /** 키 → 값 묶음 (팔레트, 종이 색, 사유 아이콘 ...). [value]는 값 종류 */
    data class MapOf(val value: TokenKind) : TokenKind
    /** 색 목록 (그라데이션) */
    data object ColorList : TokenKind
    /** 있거나 없는 묶음 (그림자, 열 테두리 ...): 켜면 기본값으로 생기고 끄면 null */
    data object Presence : TokenKind
}

/** 토큰 하나. [path] = JSON 경로 (예: ["block","title","size"]). */
data class TokenSpec(val path: List<String>, val kind: TokenKind, val nullable: Boolean) {
    val key: String get() = path.joinToString(".")
    val group: String get() = path.first()
}

/**
 * WidgetTheme의 serializer 설명자를 따라가서 편집할 수 있는 토큰 목록을 만든다.
 * 토큰을 추가하면(WidgetTheme.kt) 편집기에 저절로 나타난다 — 화면 코드를 따로 고칠 필요 없음.
 */
@OptIn(ExperimentalSerializationApi::class)
object TokenSchema {

    /** 편집기에 보이지 않는 키 */
    private val SKIP = setOf("id", "name", "sizeClass")

    val all: List<TokenSpec> by lazy {
        val out = mutableListOf<TokenSpec>()
        walk(WidgetTheme.serializer().descriptor, emptyList(), out)
        out
    }

    val groups: List<String> by lazy { all.map { it.group }.distinct() }

    fun inGroup(group: String) = all.filter { it.group == group }

    /** 이 토큰을 품은 "있다/없다" 묶음 (그림자 등). 그 묶음이 꺼져 있으면 편집기에서 숨긴다. */
    fun presenceParent(spec: TokenSpec): TokenSpec? =
        all.lastOrNull { it.kind == TokenKind.Presence && it.path.size < spec.path.size && spec.path.take(it.path.size) == it.path }

    private fun walk(d: SerialDescriptor, path: List<String>, out: MutableList<TokenSpec>) {
        for (i in 0 until d.elementsCount) {
            val name = d.getElementName(i)
            if (path.isEmpty() && name in SKIP) continue
            val child = d.getElementDescriptor(i)
            val p = path + name
            val kind = kindOf(child, p)
            if (kind != null) out += TokenSpec(p, kind, child.isNullable)
            else if (child.kind == StructureKind.CLASS) {
                if (child.isNullable) out += TokenSpec(p, TokenKind.Presence, true)
                walk(child, p, out)
            }
        }
    }

    private fun kindOf(d: SerialDescriptor, path: List<String>): TokenKind? {
        val name = d.serialName.removeSuffix("?")
        if (name == ThemeColorSerializer.SERIAL_NAME) return TokenKind.Color
        if (path.last() == "font" && d.kind == PrimitiveKind.STRING) return TokenKind.Font
        return when (d.kind) {
            PrimitiveKind.BOOLEAN -> TokenKind.Bool
            PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> range(path, integer = false)
            PrimitiveKind.INT, PrimitiveKind.LONG -> range(path, integer = true)
            PrimitiveKind.STRING -> TokenKind.Text
            SerialKind.ENUM -> TokenKind.Choice((0 until d.elementsCount).map { d.getElementName(it) })
            StructureKind.MAP -> {
                val v = d.elementDescriptors.toList()[1]
                when {
                    v.serialName.removeSuffix("?") == ThemeColorSerializer.SERIAL_NAME -> TokenKind.MapOf(TokenKind.Color)
                    v.kind == PrimitiveKind.STRING -> TokenKind.MapOf(if (path.last() == "fonts") TokenKind.Font else TokenKind.Text)
                    else -> null
                }
            }
            StructureKind.LIST -> {
                val v = d.elementDescriptors.first()
                if (v.serialName.removeSuffix("?") == ThemeColorSerializer.SERIAL_NAME) TokenKind.ColorList else null
            }
            else -> null
        }
    }

    /** 이름으로 정한 슬라이더 범위 (단위: dp / sp / 0..1 / deg). */
    fun range(path: List<String>, integer: Boolean): TokenKind.Number {
        val last = path.last()
        val parent = path.getOrNull(path.size - 2)
        fun n(min: Float, max: Float, step: Float) = TokenKind.Number(min, max, step, integer)
        return when {
            last == "weight" -> n(100f, 900f, 100f)
            last == "titleMaxLines" -> n(1f, 4f, 1f)
            last == "showEveryNHours" -> n(1f, 3f, 1f)
            last == "scale" -> n(0.7f, 1.5f, 0.05f)
            last == "letterSpacing" -> n(-0.1f, 0.3f, 0.01f)
            last == "widthRatio" -> n(0.2f, 1f, 0.02f)
            last in setOf("opacity", "alpha", "tintAlpha", "pastDim", "imageAlpha", "tapeAlpha", "overlapOpacity") -> n(0f, 1f, 0.05f)
            last == "angle" -> n(0f, 360f, 5f)
            parent == "rotation" || last in setOf("patternAngle", "tapeAngle") -> n(-45f, 45f, 1f)
            last == "size" -> n(4f, 36f, 0.5f)
            last in setOf("emojiSize") -> n(8f, 36f, 1f)
            last == "hourHeight" -> n(20f, 120f, 2f)
            last in setOf("height", "allDayHeight", "minHeight") -> n(0f, 80f, 1f)
            last == "width" && parent in setOf("timeAxis") -> n(0f, 60f, 1f)
            last in setOf("dx", "dy", "x", "y") -> n(-12f, 12f, 0.5f)
            else -> n(0f, 40f, 0.5f) // 여백·모서리·선 굵기·간격 등 dp
        }
    }

    // ── 경로로 읽고 쓰기 (덮어쓰기 JSON은 바꾼 토큰만) ──

    fun get(obj: JsonObject?, path: List<String>): JsonElement? {
        var cur: JsonElement? = obj
        for (k in path) cur = (cur as? JsonObject)?.get(k) ?: return null
        return cur
    }

    fun set(obj: JsonObject, path: List<String>, value: JsonElement): JsonObject {
        val head = path.first()
        if (path.size == 1) return JsonObject(obj + (head to value))
        val child = obj[head] as? JsonObject ?: JsonObject(emptyMap())
        return JsonObject(obj + (head to set(child, path.drop(1), value)))
    }

    /** 지우고, 비게 된 상위 묶음도 지운다 ("기본값으로"). */
    fun remove(obj: JsonObject, path: List<String>): JsonObject {
        val head = path.first()
        if (path.size == 1) return JsonObject(obj - head)
        val child = obj[head] as? JsonObject ?: return obj
        val next = remove(child, path.drop(1))
        return if (next.isEmpty()) JsonObject(obj - head) else JsonObject(obj + (head to next))
    }

    /** 이 토큰(또는 그 아래)을 바꿨는지. */
    fun isOverridden(obj: JsonObject?, path: List<String>): Boolean = get(obj, path) != null

    /** 그룹 안에서 바꾼 토큰 수 */
    fun overriddenCount(obj: JsonObject?, group: String): Int =
        inGroup(group).count { isOverridden(obj, it.path) }

    val NULL: JsonElement = JsonNull
}

/** 그룹 이름 (한국어). */
object TokenLabels {
    val groups = mapOf(
        "fonts" to "글꼴",
        "scale" to "글자 배율",
        "canvas" to "바탕",
        "header" to "머리말 (아이 이름)",
        "dayHeader" to "요일 머리말",
        "today" to "오늘 강조",
        "timeAxis" to "시간 축",
        "grid" to "그리드",
        "block" to "일정 칸",
        "cancelled" to "취소 표시",
        "postIt" to "포스트잇",
        "nowLine" to "현재 시간선",
        "vacation" to "휴가",
        "emptyState" to "빈 화면",
    )

    private val words = mapOf(
        "background" to "배경색", "gradient" to "그라데이션", "type" to "종류", "colors" to "색들", "angle" to "각도",
        "backgroundImage" to "배경 이미지", "imageAlpha" to "이미지 투명도", "imageMode" to "이미지 방식",
        "cornerRadius" to "모서리 둥글기", "matchSystem" to "시스템 모서리 맞추기", "padding" to "안쪽 여백",
        "start" to "왼쪽", "top" to "위", "end" to "오른쪽", "bottom" to "아래",
        "border" to "테두리", "width" to "굵기/폭", "color" to "색", "style" to "선 모양", "opacity" to "불투명도",
        "visible" to "보이기", "height" to "높이", "title" to "제목 글자", "font" to "글꼴", "size" to "크기",
        "weight" to "굵기", "letterSpacing" to "자간", "showEmoji" to "이모지 보이기", "emojiSize" to "이모지 크기",
        "subtitle" to "주 범위", "format" to "형식", "align" to "정렬", "decoration" to "장식", "decorationColor" to "장식 색",
        "labelFormat" to "글자 형식", "saturdayColor" to "토요일 색", "sundayColor" to "일요일 색", "holidayColor" to "공휴일 색",
        "holidayNameVisible" to "공휴일 이름 보이기", "holidayNameFont" to "공휴일 이름 글자", "holidayMark" to "공휴일 표시",
        "headerStyle" to "요일 강조 방식", "headerColor" to "강조 색", "headerTextColor" to "강조 글자 색",
        "columnTint" to "열 배경", "columnBorder" to "열 테두리", "labelAlign" to "라벨 위치", "showEveryNHours" to "라벨 간격(시간)",
        "hourLine" to "1시간 선", "halfHourLine" to "30분 선", "columnDivider" to "요일 구분선",
        "alternateColumnTint" to "줄무늬 열", "weekendColumnTint" to "주말 열", "holidayColumnTint" to "공휴일 열",
        "hourHeight" to "1시간 높이(비우면 자동)", "columnGap" to "열 간격",
        "palette" to "색 팔레트", "fillStyle" to "채우기 방식", "tintAlpha" to "연하기", "shadow" to "그림자",
        "dx" to "가로 어긋남", "dy" to "세로 어긋남", "blur" to "번짐", "inset" to "칸 사이 여백",
        "titleMaxLines" to "제목 최대 줄", "textColor" to "글자 색", "showTime" to "시각 보이기", "time" to "시각 글자",
        "timeFormat" to "시각 형식", "showPlace" to "장소 보이기", "icon" to "아이콘", "minTextHeight" to "글자 최소 높이",
        "pastDim" to "지난 일정 흐리게", "clipIndicator" to "잘림 표시", "leftBarWidth" to "왼쪽 막대 폭",
        "fill" to "채우기 색", "keepPaletteHue" to "원래 색 살짝 남기기", "alpha" to "불투명도", "pattern" to "무늬",
        "patternColor" to "무늬 색", "patternSpacing" to "무늬 간격", "patternAngle" to "무늬 각도", "patternWidth" to "무늬 굵기",
        "strikeThrough" to "취소선", "badge" to "배지", "badgeText" to "배지 글자", "badgeFont" to "배지 글자 모양",
        "reasonIcons" to "취소 사유 아이콘", "papers" to "종이 색", "widthRatio" to "폭 비율", "offset" to "어긋남",
        "x" to "가로", "y" to "세로", "rotation" to "기울기", "min" to "최소", "max" to "최대",
        "foldCorner" to "접힌 모서리", "position" to "위치", "attach" to "붙이는 장식", "tapeColor" to "테이프/압정 색",
        "tapeAlpha" to "테이프 투명도", "tapeAngle" to "테이프 각도", "minHeight" to "최소 높이",
        "overlapOpacity" to "겹칠 때 불투명도", "underBadge" to "기존 유지/취소 태그", "underBadgeColors" to "태그 색",
        "underBadgeTexts" to "태그 글자", "underBadgeFont" to "태그 글자 모양", "allDayPlacement" to "하루 종일 위치",
        "allDayHeight" to "하루 종일 높이", "multiDayStack" to "겹칠 때 계단 간격", "span" to "길이", "head" to "앞 장식",
        "headSize" to "앞 장식 크기", "glow" to "빛 번짐", "aboveStickers" to "포스트잇 위에 그리기", "timeFont" to "시각 글자",
        "headerBand" to "휴가 띠", "text" to "글자", "scale" to "글자 배율", "fonts" to "글꼴 별칭",
    )

    fun group(g: String) = groups[g] ?: g

    /** 한국어 이름이 없는 경로 조각 (테스트에서 빠진 이름 찾기) */
    fun missingWords(): Set<String> = TokenSchema.all.flatMap { it.path.drop(1).ifEmpty { it.path } }.filterNot { it in words }.toSet()

    /** 한국어 이름이 없는 enum 값 */
    fun missingChoices(): Set<String> = TokenSchema.all.mapNotNull { it.kind as? TokenKind.Choice }.flatMap { it.options }.filterNot { it in choices }.toSet()

    /** 토큰 이름: 그룹을 뺀 경로를 한국어로 ("title.size" → "제목 글자 · 크기") */
    fun token(spec: TokenSpec): String =
        spec.path.drop(1).ifEmpty { spec.path }.joinToString(" · ") { words[it] ?: it }

    /** enum 값 한국어 (모르면 그대로). */
    fun choice(v: String): String = choices[v] ?: v

    private val choices = mapOf(
        "NONE" to "없음", "SOLID" to "실선/꽉 채움", "DASHED" to "점선(긴)", "DOTTED" to "점선",
        "START" to "왼쪽", "CENTER" to "가운데", "END" to "오른쪽",
        "ALWAYS" to "항상", "IF_FITS" to "들어가면", "NEVER" to "안 보임",
        "LINEAR" to "직선", "RADIAL" to "원형", "TILE" to "바둑판", "FILL" to "꽉 채움",
        "UNDERLINE" to "밑줄", "PILL" to "알약", "RIBBON" to "리본", "DOT" to "점", "BADGE" to "배지",
        "OUTLINE" to "테두리", "CIRCLE_DATE" to "동그라미",
        "TOP_OF_LINE" to "선 아래", "CENTER_ON_LINE" to "선 가운데",
        "TINT" to "연하게", "LEFT_BAR" to "왼쪽 막대", "GRADIENT" to "그라데이션", "EMOJI" to "이모지",
        "ARROW" to "화살표", "FADE" to "흐려짐", "HATCH" to "빗금", "DOTS" to "물방울", "CROSS" to "격자",
        "TEXT" to "글자", "ICON" to "아이콘", "BOTTOM_RIGHT" to "오른쪽 아래", "TOP_RIGHT" to "오른쪽 위",
        "TAPE" to "테이프", "PIN" to "압정", "CLIP" to "클립", "CORNER_TAG" to "모서리 태그",
        "HEADER_STRIP" to "요일 아래 줄", "TOP_OF_COLUMN" to "열 맨 위",
        "TODAY_COLUMN" to "오늘 열만", "ALL_COLUMNS" to "전체", "TRIANGLE" to "삼각형", "PILL_TIME" to "시각 말풍선",
    ) + CancelReason.entries.associate { it.name to it.name }

    /** 글꼴 선택지 (번들 폰트가 생기면 여기에 추가). */
    val fontChoices = listOf("display", "body", "hand", "system", "system-serif", "system-mono")
    fun fontLabel(f: String) = mapOf(
        "display" to "제목용(별칭)", "body" to "본문용(별칭)", "hand" to "손글씨용(별칭)",
        "system" to "기본 글꼴", "system-serif" to "명조 계열", "system-mono" to "고정폭",
    )[f] ?: f
}

/** 미리보기를 누른 곳 → 꾸밀 그룹 ("이 부분 꾸미기"). */
object DesignHitTest {
    fun groupAt(l: WeekLayoutResult, x: Float, y: Float): String {
        fun io.github.rosymaro.familyweekly.layout.Box.has() = x >= left && x <= right && y >= top && y <= bottom
        l.postIts.lastOrNull { it.box.has() }?.let { return "postIt" }
        l.nowLine?.let { if (kotlin.math.abs(y - it.y) < 6 * l.spec.density && x >= it.startX - 8 * l.spec.density && x <= it.endX) return "nowLine" }
        l.blocks.lastOrNull { it.box.has() }?.let {
            return if (it.occurrence.status == io.github.rosymaro.familyweekly.domain.OccurrenceStatus.CANCELLED) "cancelled" else "block"
        }
        l.vacationBands.firstOrNull { it.box.has() }?.let { return "vacation" }
        l.dayHeaders.firstOrNull { it.box.has() }?.let { return if (it.isToday) "today" else "dayHeader" }
        l.header?.let { if (it.box.has()) return "header" }
        if (l.timeAxis.has()) return "timeAxis"
        if (l.grid.has()) return "grid"
        return "canvas"
    }
}
