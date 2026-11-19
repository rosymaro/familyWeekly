package io.github.rosymaro.familyweekly.theme

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// 위젯 디자인 토큰. agent/DESIGN.md 1절과 1:1로 맞춘다.
// 토큰을 추가하면: 이 파일 기본값 + DESIGN.md 표 + 프리셋 JSON 테스트를 같이 고친다.
// 크기 단위: dp, 글자 sp, 비율 0..1, 각도 deg. 기본값은 프리셋 soft-paper와 같다.

// ───────────── 기본 타입 ─────────────

/**
 * 라이트/다크 색쌍. JSON에서는 `{ "light": "#..", "dark": "#.." }` 또는 문자열 하나("#..")로 쓸 수 있다.
 * dark가 없으면 라이트 색에서 자동 생성 (ColorMath.autoDark).
 * light가 "auto"면 상황에 맞게 계산 (글자색: 배경 대비, 테두리: 칸 색 진하게).
 */
@Serializable(with = ThemeColorSerializer::class)
data class ThemeColor(val light: String, val dark: String? = null) {
    val isAuto: Boolean get() = light.equals(AUTO, ignoreCase = true)

    /** ARGB Int. isAuto면 호출하지 말 것. */
    fun resolve(isDark: Boolean): Int {
        val base = ColorMath.parse(light)
        if (!isDark) return base
        return dark?.let(ColorMath::parse) ?: ColorMath.autoDark(base)
    }

    companion object {
        const val AUTO = "auto"
        val Auto = ThemeColor(AUTO, AUTO)
        fun of(light: String, dark: String) = ThemeColor(light, dark)
    }
}

object ThemeColorSerializer : KSerializer<ThemeColor> {
    /** 디자인 편집기(TokenSchema)가 색 토큰을 알아보도록 이름이 있는 설명자 */
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(SERIAL_NAME) {
        element<String>("light")
        element<String?>("dark", isOptional = true)
    }
    const val SERIAL_NAME = "familyweekly.ThemeColor"

    override fun serialize(encoder: Encoder, value: ThemeColor) {
        val json = encoder as JsonEncoder
        json.encodeJsonElement(buildJsonObject {
            put("light", value.light)
            value.dark?.let { put("dark", it) }
        })
    }

    override fun deserialize(decoder: Decoder): ThemeColor {
        val el = (decoder as JsonDecoder).decodeJsonElement()
        if (el is JsonPrimitive) return ThemeColor(el.content)
        val obj = el.jsonObject
        return ThemeColor(
            light = obj.getValue("light").jsonPrimitive.content,
            dark = obj["dark"]?.jsonPrimitive?.content,
        )
    }
}

@Serializable enum class LineStyle { SOLID, DASHED, DOTTED }
@Serializable enum class Align { START, CENTER, END }
@Serializable enum class Visibility { ALWAYS, IF_FITS, NEVER }

@Serializable
data class FontSpec(
    /** `fonts` 별칭(display/body/hand) 또는 직접 참조("system", "system-serif", 번들 폰트 이름). */
    val font: String = "body",
    val size: Float = 12f,
    /** 100..900 */
    val weight: Int = 400,
    val color: ThemeColor = ThemeColor.of("#3B3127", "#EDE3D6"),
    /** em */
    val letterSpacing: Float = 0f,
)

@Serializable
data class LineSpec(
    val visible: Boolean = true,
    val width: Float = 1f,
    val color: ThemeColor = ThemeColor.of("#1F3B3127", "#24EDE3D6"),
    val style: LineStyle = LineStyle.SOLID,
)

@Serializable
data class Border(
    val width: Float = 0f,
    val color: ThemeColor = ThemeColor.Auto,
    val style: LineStyle = LineStyle.SOLID,
)

@Serializable
data class Shadow(
    val dx: Float = 0f,
    val dy: Float = 1f,
    val blur: Float = 2f,
    val color: ThemeColor = ThemeColor.of("#33000000", "#66000000"),
)

@Serializable
data class Insets(
    val start: Float = 0f,
    val top: Float = 0f,
    val end: Float = 0f,
    val bottom: Float = 0f,
) {
    companion object {
        fun all(v: Float) = Insets(v, v, v, v)
    }
}

@Serializable
data class Offset(val x: Float = 0f, val y: Float = 0f)

@Serializable
data class FloatRange(val min: Float = 0f, val max: Float = 0f)

// ───────────── 1-1 canvas ─────────────

@Serializable enum class GradientType { NONE, LINEAR, RADIAL }
@Serializable enum class ImageMode { TILE, FILL }

@Serializable
data class Gradient(
    val type: GradientType = GradientType.NONE,
    val colors: List<ThemeColor> = emptyList(),
    /** LINEAR 방향 (0 = 위→아래, 90 = 왼→오) */
    val angle: Float = 0f,
)

@Serializable
data class CanvasTokens(
    val background: ThemeColor = ThemeColor.of("#FFFBF3E6", "#FF26211B"),
    val gradient: Gradient = Gradient(),
    /** assets 경로 (예: "textures/paper.png"). null이면 없음. */
    val backgroundImage: String? = null,
    val imageAlpha: Float = 1f,
    val imageMode: ImageMode = ImageMode.TILE,
    val cornerRadius: Float = 20f,
    /** Android 12+ 시스템 위젯 모서리 반경을 쓸지. */
    val matchSystem: Boolean = true,
    val padding: Insets = Insets.all(10f),
    val border: Border = Border(0f, ThemeColor.of("#1F3B3127", "#24EDE3D6")),
    val opacity: Float = 1f,
)

// ───────────── 1-2 header ─────────────

@Serializable enum class HeaderDecoration { NONE, UNDERLINE, PILL, RIBBON }

@Serializable
data class SubtitleTokens(
    val visible: Boolean = true,
    /** java.time 패턴. "~"가 있으면 왼쪽은 첫날, 오른쪽은 마지막 날. 예 "M월 d일 ~ M월 d일", "M월 W주차" */
    val format: String = "M월 d일 ~ M월 d일",
    val font: FontSpec = FontSpec(size = 11f, color = ThemeColor.of("#998A7B6B", "#99CDBFAF")),
)

@Serializable
data class HeaderTokens(
    val visible: Boolean = true,
    val height: Float = 28f,
    val title: FontSpec = FontSpec(font = "display", size = 16f, weight = 700),
    val showEmoji: Boolean = true,
    val emojiSize: Float = 16f,
    val subtitle: SubtitleTokens = SubtitleTokens(),
    val align: Align = Align.START,
    val background: ThemeColor? = null,
    val decoration: HeaderDecoration = HeaderDecoration.NONE,
    val decorationColor: ThemeColor = ThemeColor.of("#FFF2A65A", "#FFB8742F"),
)

// ───────────── 1-3 dayHeader ─────────────

@Serializable enum class HolidayMark { NONE, DOT, UNDERLINE, BADGE }

@Serializable
data class DayHeaderTokens(
    val height: Float = 26f,
    /** java.time 패턴(한국어). "E" → 월, "E d" → 월 17, "d\nE" → 두 줄 */
    val labelFormat: String = "E d",
    val font: FontSpec = FontSpec(size = 11f, weight = 600),
    val saturdayColor: ThemeColor = ThemeColor.of("#FF3E6FB0", "#FF8FB4E6"),
    val sundayColor: ThemeColor = ThemeColor.of("#FFD0473A", "#FFF08A80"),
    val holidayColor: ThemeColor = ThemeColor.of("#FFD0473A", "#FFF08A80"),
    val holidayNameVisible: Boolean = true,
    val holidayNameFont: FontSpec = FontSpec(size = 8f, color = ThemeColor.of("#FFD0473A", "#FFF08A80")),
    val holidayMark: HolidayMark = HolidayMark.DOT,
    val background: ThemeColor? = null,
)

// ───────────── 1-4 today ─────────────

@Serializable enum class TodayHeaderStyle { NONE, FILL, OUTLINE, PILL, UNDERLINE, CIRCLE_DATE }

@Serializable
data class TodayTokens(
    val headerStyle: TodayHeaderStyle = TodayHeaderStyle.PILL,
    val headerColor: ThemeColor = ThemeColor.of("#FFF2A65A", "#FFB8742F"),
    val headerTextColor: ThemeColor = ThemeColor.of("#FFFFFFFF", "#FF1E1B16"),
    val columnTint: ThemeColor? = ThemeColor.of("#14F2A65A", "#1FF2A65A"),
    val columnBorder: Border? = null,
)

// ───────────── 1-5 timeAxis ─────────────

@Serializable enum class LabelAlign { TOP_OF_LINE, CENTER_ON_LINE }

@Serializable
data class TimeAxisTokens(
    val width: Float = 22f,
    /** "H", "HH:mm", "a h시", "h a" (java.time, 한국어) */
    val labelFormat: String = "H",
    val font: FontSpec = FontSpec(size = 9f, color = ThemeColor.of("#998A7B6B", "#99CDBFAF")),
    val labelAlign: LabelAlign = LabelAlign.TOP_OF_LINE,
    val background: ThemeColor? = null,
    /** 라벨만 N시간마다 (선은 1시간마다). */
    val showEveryNHours: Int = 1,
)

// ───────────── 1-6 grid ─────────────

@Serializable
data class GridTokens(
    val hourLine: LineSpec = LineSpec(),
    val halfHourLine: LineSpec = LineSpec(visible = false, width = 0.5f, style = LineStyle.DOTTED),
    val columnDivider: LineSpec = LineSpec(visible = false),
    val alternateColumnTint: ThemeColor? = null,
    val weekendColumnTint: ThemeColor? = ThemeColor.of("#0A3E6FB0", "#0F8FB4E6"),
    val holidayColumnTint: ThemeColor? = ThemeColor.of("#0FD0473A", "#14F08A80"),
    /** 1시간 높이 dp. null이면 AUTO (위젯 높이 ÷ 표시 시간 수). */
    val hourHeight: Float? = null,
    val columnGap: Float = 2f,
)

// ───────────── 1-7 block ─────────────

@Serializable enum class FillStyle { SOLID, TINT, OUTLINE, LEFT_BAR, GRADIENT }
@Serializable enum class BlockIcon { NONE, EMOJI, DOT }
@Serializable enum class ClipIndicator { NONE, ARROW, FADE }

@Serializable
data class BlockTokens(
    /** 블록 colorKey → 색. "default"는 키가 없을 때 쓰는 색. */
    val palette: Map<String, ThemeColor> = DefaultPalettes.softPaper,
    val fillStyle: FillStyle = FillStyle.TINT,
    val tintAlpha: Float = 0.35f,
    val cornerRadius: Float = 6f,
    val border: Border = Border(width = 0f),
    val shadow: Shadow? = null,
    val inset: Float = 1f,
    val padding: Insets = Insets(4f, 3f, 4f, 3f),
    val title: FontSpec = FontSpec(size = 10f, weight = 600, color = ThemeColor.Auto),
    val titleMaxLines: Int = 2,
    /** "auto"면 칸 색에 맞춰 대비 높은 색. */
    val textColor: ThemeColor = ThemeColor.Auto,
    val showTime: Visibility = Visibility.IF_FITS,
    val time: FontSpec = FontSpec(size = 8f, color = ThemeColor.Auto),
    /** "H:mm" 또는 "H:mm–H:mm" (– 또는 ~ 로 시작~끝) */
    val timeFormat: String = "H:mm",
    val showPlace: Visibility = Visibility.NEVER,
    val icon: BlockIcon = BlockIcon.NONE,
    val minTextHeight: Float = 12f,
    val pastDim: Float = 0f,
    val clipIndicator: ClipIndicator = ClipIndicator.ARROW,
    /** LEFT_BAR일 때 막대 폭. */
    val leftBarWidth: Float = 3f,
)

// ───────────── 1-8 cancelled ─────────────

@Serializable enum class Pattern { NONE, HATCH, DOTS, CROSS }
@Serializable enum class Badge { NONE, TEXT, ICON }

@Serializable
data class CancelledTokens(
    val fill: ThemeColor = ThemeColor.of("#FFE4DED6", "#FF3A342D"),
    val keepPaletteHue: Boolean = true,
    val alpha: Float = 0.9f,
    val pattern: Pattern = Pattern.HATCH,
    val patternColor: ThemeColor = ThemeColor.of("#40776655", "#40CDBFAF"),
    val patternSpacing: Float = 5f,
    val patternAngle: Float = 45f,
    val patternWidth: Float = 1f,
    val strikeThrough: Boolean = true,
    val textColor: ThemeColor = ThemeColor.of("#FF8A7B6B", "#FF9E8E81"),
    val badge: Badge = Badge.ICON,
    val badgeText: String = "취소",
    val badgeFont: FontSpec = FontSpec(size = 8f, weight = 700, color = ThemeColor.of("#FF8A7B6B", "#FF9E8E81")),
    /** 사유(MANUAL, EVENT, VACATION, HOLIDAY) → 이모지 */
    val reasonIcons: Map<String, String> = mapOf(
        "MANUAL" to "✕",
        "EVENT" to "📌",
        "VACATION" to "🏖",
        "HOLIDAY" to "🎌",
    ),
    val border: Border = Border(1f, ThemeColor.of("#59776655", "#59CDBFAF"), LineStyle.DASHED),
)

// ───────────── 1-9 postIt ─────────────

@Serializable enum class FoldPosition { NONE, BOTTOM_RIGHT, TOP_RIGHT }
@Serializable enum class Attach { NONE, TAPE, PIN, CLIP }
@Serializable enum class UnderBadge { NONE, CORNER_TAG }
@Serializable enum class AllDayPlacement { HEADER_STRIP, TOP_OF_COLUMN }

@Serializable
data class FoldCorner(val position: FoldPosition = FoldPosition.BOTTOM_RIGHT, val size: Float = 7f)

@Serializable
data class PostItTokens(
    val papers: Map<String, ThemeColor> = mapOf(
        "yellow" to ThemeColor.of("#FFFFE98A", "#FF6B5F22"),
        "pink" to ThemeColor.of("#FFFFC7D9", "#FF6B3447"),
        "blue" to ThemeColor.of("#FFC4E4FF", "#FF2D4A63"),
        "green" to ThemeColor.of("#FFCDEFC1", "#FF34522B"),
    ),
    /** 요일 칸 폭 대비. 1보다 작게 해서 아래 블록이 옆으로 보이게. */
    val widthRatio: Float = 0.82f,
    val offset: Offset = Offset(2f, 2f),
    val rotation: FloatRange = FloatRange(-3f, 3f),
    val cornerRadius: Float = 2f,
    val shadow: Shadow? = Shadow(dx = 1f, dy = 2f, blur = 3f),
    val foldCorner: FoldCorner = FoldCorner(),
    val attach: Attach = Attach.TAPE,
    val tapeColor: ThemeColor = ThemeColor.of("#FFFFFFFF", "#FFCDBFAF"),
    val tapeAlpha: Float = 0.55f,
    val tapeAngle: Float = -4f,
    val font: FontSpec = FontSpec(font = "hand", size = 10f, weight = 600, color = ThemeColor.of("#FF3B3127", "#FFF5ECDF")),
    val showTime: Visibility = Visibility.IF_FITS,
    val time: FontSpec = FontSpec(font = "hand", size = 8f, color = ThemeColor.of("#CC3B3127", "#CCF5ECDF")),
    val padding: Insets = Insets.all(4f),
    /** 짧은 일정도 글자가 보이도록 최소 높이. */
    val minHeight: Float = 22f,
    val overlapOpacity: Float = 0.96f,
    val underBadge: UnderBadge = UnderBadge.CORNER_TAG,
    /** KEEP / CANCEL → 태그 색 */
    val underBadgeColors: Map<String, ThemeColor> = mapOf(
        "KEEP" to ThemeColor.of("#FF4F8A5B", "#FF7DBB89"),
        "CANCEL" to ThemeColor.of("#FFC0503F", "#FFE88A7C"),
    ),
    val underBadgeTexts: Map<String, String> = mapOf("KEEP" to "기존 유지", "CANCEL" to "기존 취소"),
    val underBadgeFont: FontSpec = FontSpec(size = 7f, weight = 700, color = ThemeColor.of("#FFFFFFFF", "#FF1E1B16")),
    val allDayPlacement: AllDayPlacement = AllDayPlacement.HEADER_STRIP,
    /** 하루 종일 포스트잇 높이. */
    val allDayHeight: Float = 18f,
    /** 같은 시간대 포스트잇이 겹칠 때 계단식 어긋남. */
    val multiDayStack: Float = 5f,
)

// ───────────── 1-10 nowLine ─────────────

@Serializable enum class NowSpan { TODAY_COLUMN, ALL_COLUMNS }
@Serializable enum class NowHead { NONE, DOT, TRIANGLE, PILL_TIME }

@Serializable
data class NowLineTokens(
    val visible: Boolean = true,
    val span: NowSpan = NowSpan.TODAY_COLUMN,
    val color: ThemeColor = ThemeColor.of("#FFE5533D", "#FFFF7A66"),
    val width: Float = 1.5f,
    val style: LineStyle = LineStyle.SOLID,
    val head: NowHead = NowHead.DOT,
    val headSize: Float = 3.5f,
    val glow: Shadow? = null,
    val aboveStickers: Boolean = true,
    val timeFont: FontSpec = FontSpec(size = 7f, weight = 700, color = ThemeColor.of("#FFFFFFFF", "#FF1E1B16")),
)

// ───────────── 1-11 vacation ─────────────

@Serializable
data class BandTokens(
    val visible: Boolean = true,
    val height: Float = 12f,
    val color: ThemeColor = ThemeColor.of("#3366B2A8", "#4466B2A8"),
    val cornerRadius: Float = 6f,
    val font: FontSpec = FontSpec(size = 8f, weight = 600, color = ThemeColor.of("#FF2F6B64", "#FFA6DED7")),
)

@Serializable
data class VacationTokens(
    val headerBand: BandTokens = BandTokens(),
    val columnTint: ThemeColor? = ThemeColor.of("#0F66B2A8", "#1466B2A8"),
)

// ───────────── 1-12 typography / misc ─────────────

@Serializable
data class EmptyStateTokens(
    val text: String = "일정이 없어요",
    val font: FontSpec = FontSpec(size = 11f, color = ThemeColor.of("#998A7B6B", "#99CDBFAF")),
)

@Serializable
data class WidgetTheme(
    val id: String = "soft-paper",
    val name: String = "따뜻한 종이",
    /** 별칭 → 폰트 참조. */
    val fonts: Map<String, String> = mapOf("display" to "system", "body" to "system", "hand" to "system"),
    /** 글자 전체 배율 0.7..1.5 */
    val scale: Float = 1f,
    val canvas: CanvasTokens = CanvasTokens(),
    val header: HeaderTokens = HeaderTokens(),
    val dayHeader: DayHeaderTokens = DayHeaderTokens(),
    val today: TodayTokens = TodayTokens(),
    val timeAxis: TimeAxisTokens = TimeAxisTokens(),
    val grid: GridTokens = GridTokens(),
    val block: BlockTokens = BlockTokens(),
    val cancelled: CancelledTokens = CancelledTokens(),
    val postIt: PostItTokens = PostItTokens(),
    val nowLine: NowLineTokens = NowLineTokens(),
    val vacation: VacationTokens = VacationTokens(),
    val emptyState: EmptyStateTokens = EmptyStateTokens(),
    /** 크기 단계("compact"/"medium"/"large") → 덮어쓸 토큰 (부분 JSON). */
    val sizeClass: Map<String, JsonObject> = emptyMap(),
)

/** 기본 팔레트. 키는 블록 colorKey로 쓰인다. */
object DefaultPalettes {
    val softPaper: Map<String, ThemeColor> = linkedMapOf(
        "default" to ThemeColor.of("#FFB8A58F", "#FF8C7A66"),
        "peach" to ThemeColor.of("#FFF2A65A", "#FFC9803A"),
        "mint" to ThemeColor.of("#FF6FC2A5", "#FF4F9C82"),
        "sky" to ThemeColor.of("#FF6FA8DC", "#FF4F86BA"),
        "lilac" to ThemeColor.of("#FFB39DDB", "#FF8E79B8"),
        "rose" to ThemeColor.of("#FFE88AA0", "#FFC4687E"),
        "lemon" to ThemeColor.of("#FFE6C84F", "#FFBFA236"),
        "sage" to ThemeColor.of("#FF9DB58A", "#FF7A9168"),
        "coral" to ThemeColor.of("#FFE87A62", "#FFC45B45"),
    )
}
