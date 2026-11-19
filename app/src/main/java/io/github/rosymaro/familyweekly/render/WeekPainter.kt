package io.github.rosymaro.familyweekly.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import io.github.rosymaro.familyweekly.domain.OccurrenceStatus
import io.github.rosymaro.familyweekly.domain.UnderState
import io.github.rosymaro.familyweekly.layout.BlockLayout
import io.github.rosymaro.familyweekly.layout.Box
import io.github.rosymaro.familyweekly.layout.DayHeaderCell
import io.github.rosymaro.familyweekly.layout.PostItLayout
import io.github.rosymaro.familyweekly.layout.TintKind
import io.github.rosymaro.familyweekly.layout.WeekLayout
import io.github.rosymaro.familyweekly.layout.WeekLayoutResult
import io.github.rosymaro.familyweekly.theme.Align
import io.github.rosymaro.familyweekly.theme.Attach
import io.github.rosymaro.familyweekly.theme.Badge
import io.github.rosymaro.familyweekly.theme.BlockIcon
import io.github.rosymaro.familyweekly.theme.Border
import io.github.rosymaro.familyweekly.theme.ClipIndicator
import io.github.rosymaro.familyweekly.theme.ColorMath
import io.github.rosymaro.familyweekly.theme.FillStyle
import io.github.rosymaro.familyweekly.theme.FoldPosition
import io.github.rosymaro.familyweekly.theme.FontSpec
import io.github.rosymaro.familyweekly.theme.GradientType
import io.github.rosymaro.familyweekly.theme.HeaderDecoration
import io.github.rosymaro.familyweekly.theme.HolidayMark
import io.github.rosymaro.familyweekly.theme.ImageMode
import io.github.rosymaro.familyweekly.theme.LabelAlign
import io.github.rosymaro.familyweekly.theme.LineSpec
import io.github.rosymaro.familyweekly.theme.LineStyle
import io.github.rosymaro.familyweekly.theme.NowHead
import io.github.rosymaro.familyweekly.theme.Pattern
import io.github.rosymaro.familyweekly.theme.ResolvedTheme
import io.github.rosymaro.familyweekly.theme.Shadow
import io.github.rosymaro.familyweekly.theme.ThemeColor
import io.github.rosymaro.familyweekly.theme.TodayHeaderStyle
import io.github.rosymaro.familyweekly.theme.UnderBadge
import io.github.rosymaro.familyweekly.domain.TimeStep
import java.time.DayOfWeek
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 폰트·이미지 가져오기. 앱/위젯이 넘겨 준다. */
class PainterResources(
    /** 폰트 참조("system", "system-serif", 번들 폰트 이름) → Typeface. */
    val typeface: (ref: String) -> Typeface = ::systemTypeface,
    /** assets 이미지 경로 → Bitmap. */
    val image: (path: String) -> Bitmap? = { null },
) {
    companion object {
        fun systemTypeface(ref: String): Typeface = when (ref) {
            "system-serif" -> Typeface.SERIF
            "system-mono" -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
    }
}

/**
 * WeekLayoutResult를 Canvas에 그린다. 앱 미리보기와 위젯이 같이 쓴다 (DESIGN.md 0절 WYSIWYG).
 * 위치는 전부 레이아웃이 정하고, 여기서는 테마 토큰으로 칠하기만 한다. 디자인 값을 여기에 직접 쓰지 않는다.
 * 그리는 순서는 DESIGN.md 3절.
 */
class WeekPainter(private val res: PainterResources = PainterResources()) {

    fun render(layout: WeekLayoutResult, rt: ResolvedTheme): Bitmap {
        val bmp = Bitmap.createBitmap(layout.spec.widthPx, layout.spec.heightPx, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), layout, rt)
        return bmp
    }

    fun draw(canvas: Canvas, layout: WeekLayoutResult, rt: ResolvedTheme) {
        Session(canvas, layout, rt).drawAll()
    }

    /**
     * 안내 글만 있는 그림 (위젯에 연결된 스케줄이 없을 때 등). 바탕·모서리·글자는 테마 토큰
     * (canvas.background, canvas.cornerRadius, emptyState.font)을 그대로 쓴다.
     */
    fun renderMessage(spec: io.github.rosymaro.familyweekly.layout.RenderSpec, rt: ResolvedTheme, text: String): Bitmap {
        val t = rt.theme
        val bmp = Bitmap.createBitmap(spec.widthPx, spec.heightPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val d = spec.density
        val r = RectF(0f, 0f, spec.widthPx.toFloat(), spec.heightPx.toFloat())
        val radius = d * (if (t.canvas.matchSystem && spec.systemCornerRadiusDp != null) spec.systemCornerRadiusDp else t.canvas.cornerRadius)
        c.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rt.color(t.canvas.background) })
        val f = t.emptyState.font
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = f.size * d * spec.fontScale * t.scale
            color = if (f.color.isAuto) ColorMath.readableOn(rt.color(t.canvas.background)) else rt.color(f.color)
            typeface = res.typeface(t.fonts[f.font] ?: f.font)
        }
        val width = (r.width() - 24 * d).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
        c.save()
        c.translate(12 * d, (r.height() - layout.height) / 2)
        layout.draw(c)
        c.restore()
        return bmp
    }

    private inner class Session(val c: Canvas, val l: WeekLayoutResult, val rt: ResolvedTheme) {
        val t = rt.theme
        val density = l.spec.density
        fun dp(v: Float) = v * density
        fun sp(v: Float) = v * density * l.spec.fontScale * t.scale
        fun col(tc: ThemeColor) = rt.color(tc)
        val canvasBg = col(t.canvas.background)

        fun drawAll() {
            val outer = RectF(0f, 0f, l.canvas.width, l.canvas.height)
            val clip = Path().apply { addRoundRect(outer, l.cornerRadius, l.cornerRadius, Path.Direction.CW) }
            c.save()
            c.clipPath(clip)
            if (t.canvas.opacity < 1f) c.saveLayerAlpha(outer, (t.canvas.opacity.coerceIn(0f, 1f) * 255).toInt())

            drawBackground(outer)
            drawHeader()
            drawDayHeaders()
            drawVacationBands()
            t.timeAxis.background?.let { fillRect(l.timeAxis.rect(), col(it)) }
            drawColumnTints()
            drawGridLines()
            drawHourLabels()
            l.blocks.forEach(::drawBlock)
            if (!t.nowLine.aboveStickers) drawNowLine()
            l.postIts.forEach(::drawPostIt)
            if (t.nowLine.aboveStickers) drawNowLine()
            drawEmptyState()

            if (t.canvas.opacity < 1f) c.restore()
            c.restore()
            drawBorder(RectF(outer), l.cornerRadius, t.canvas.border)
        }

        // ── 바탕 ──

        fun drawBackground(r: RectF) {
            fillRect(r, canvasBg)
            val g = t.canvas.gradient
            if (g.type != GradientType.NONE && g.colors.size >= 2) {
                val colors = g.colors.map(::col).toIntArray()
                val shader = when (g.type) {
                    GradientType.LINEAR -> {
                        val rad = Math.toRadians(g.angle.toDouble())
                        val half = max(r.width(), r.height()) / 2
                        val dx = (sin(rad) * half).toFloat()
                        val dy = (cos(rad) * half).toFloat()
                        LinearGradient(r.centerX() - dx, r.centerY() - dy, r.centerX() + dx, r.centerY() + dy, colors, null, Shader.TileMode.CLAMP)
                    }
                    else -> RadialGradient(r.centerX(), r.centerY(), max(r.width(), r.height()) / 2, colors, null, Shader.TileMode.CLAMP)
                }
                c.drawRect(r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
            }
            t.canvas.backgroundImage?.let(res.image)?.let { bmp ->
                val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = (t.canvas.imageAlpha * 255).toInt() }
                if (t.canvas.imageMode == ImageMode.TILE) {
                    p.shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                    c.drawRect(r, p)
                } else {
                    c.drawBitmap(bmp, null, r, p)
                }
            }
        }

        // ── 머리말 ──

        fun drawHeader() {
            val h = l.header ?: return
            val ht = t.header
            val r = h.box.rect()
            ht.background?.let { fillRect(r, col(it)) }

            val titlePaint = textPaint(ht.title)
            val subPaint = textPaint(ht.subtitle.font)
            val gap = dp(6f)
            val titleW = titlePaint.measureText(h.title)
            val subW = h.subtitle?.let { subPaint.measureText(it) } ?: 0f
            val rowW = min(titleW + (if (h.subtitle != null) gap + subW else 0f), r.width())
            val x0 = when (ht.align) {
                Align.START -> r.left
                Align.CENTER -> r.centerX() - rowW / 2
                Align.END -> r.right - rowW
            }
            val baseline = centerBaseline(r.centerY(), titlePaint)

            when (ht.decoration) {
                HeaderDecoration.NONE -> Unit
                HeaderDecoration.UNDERLINE -> c.drawRect(x0, r.bottom - dp(4f), x0 + rowW, r.bottom - dp(2f), fill(col(ht.decorationColor)))
                HeaderDecoration.PILL -> {
                    val pr = RectF(x0 - dp(8f), r.top + dp(2f), x0 + rowW + dp(8f), r.bottom - dp(2f))
                    c.drawRoundRect(pr, pr.height() / 2, pr.height() / 2, fill(col(ht.decorationColor)))
                }
                HeaderDecoration.RIBBON -> {
                    val path = Path().apply {
                        moveTo(r.left, r.top + dp(2f)); lineTo(x0 + rowW + dp(16f), r.top + dp(2f))
                        lineTo(x0 + rowW + dp(8f), r.centerY()); lineTo(x0 + rowW + dp(16f), r.bottom - dp(2f))
                        lineTo(r.left, r.bottom - dp(2f)); close()
                    }
                    c.drawPath(path, fill(col(ht.decorationColor)))
                }
            }
            val title = TextUtils.ellipsize(h.title, titlePaint, r.width(), TextUtils.TruncateAt.END).toString()
            c.drawText(title, x0, baseline, titlePaint)
            if (h.subtitle != null && titleW + gap + subW <= r.width()) {
                c.drawText(h.subtitle, x0 + titleW + gap, baseline, subPaint)
            }
        }

        // ── 요일 머리말 ──

        fun drawDayHeaders() {
            val dh = t.dayHeader
            dh.background?.let { bg ->
                l.dayHeaders.firstOrNull()?.let { first ->
                    fillRect(RectF(l.canvas.left, first.box.top, l.canvas.right, first.box.bottom), col(bg))
                }
            }
            l.dayHeaders.forEach(::drawDayHeader)
        }

        fun drawDayHeader(cell: DayHeaderCell) {
            val dh = t.dayHeader
            val td = t.today
            val r = cell.box.rect()
            val baseColor = when {
                cell.holidayName != null -> col(dh.holidayColor)
                cell.dayOfWeek == DayOfWeek.SUNDAY -> col(dh.sundayColor)
                cell.dayOfWeek == DayOfWeek.SATURDAY -> col(dh.saturdayColor)
                else -> col(dh.font.color)
            }
            val paint = textPaint(dh.font, baseColor).apply { textAlign = Paint.Align.CENTER }
            val lines = cell.label.split("\n")
            val lineH = paint.fontSpacing
            val showName = dh.holidayNameVisible && cell.holidayName != null
            val namePaint = textPaint(dh.holidayNameFont).apply { textAlign = Paint.Align.CENTER }
            val nameH = if (showName) namePaint.fontSpacing else 0f
            val blockH = lineH * lines.size + nameH
            val labelTop = r.centerY() - blockH / 2
            val labelW = lines.maxOf { paint.measureText(it) }
            val labelRect = RectF(r.centerX() - labelW / 2, labelTop, r.centerX() + labelW / 2, labelTop + lineH * lines.size)

            if (cell.isToday) {
                val hc = col(td.headerColor)
                when (td.headerStyle) {
                    TodayHeaderStyle.NONE -> Unit
                    TodayHeaderStyle.FILL -> fillRect(r, hc)
                    TodayHeaderStyle.OUTLINE -> c.drawRoundRect(r.insetBy(dp(1f)), dp(4f), dp(4f), stroke(hc, dp(1.2f)))
                    TodayHeaderStyle.PILL -> {
                        val pr = RectF(labelRect.left - dp(6f), labelRect.top - dp(1f), labelRect.right + dp(6f), labelRect.bottom + dp(1f))
                        c.drawRoundRect(pr, pr.height() / 2, pr.height() / 2, fill(hc))
                    }
                    TodayHeaderStyle.UNDERLINE -> c.drawRect(labelRect.left, labelRect.bottom, labelRect.right, labelRect.bottom + dp(2f), fill(hc))
                    TodayHeaderStyle.CIRCLE_DATE -> {
                        val rad = max(labelRect.width(), labelRect.height()) / 2 + dp(3f)
                        c.drawCircle(labelRect.centerX(), labelRect.centerY(), min(rad, r.height() / 2), fill(hc))
                    }
                }
                if (td.headerStyle in setOf(TodayHeaderStyle.FILL, TodayHeaderStyle.PILL, TodayHeaderStyle.CIRCLE_DATE)) {
                    paint.color = col(td.headerTextColor)
                }
            }

            lines.forEachIndexed { i, line ->
                val top = labelTop + i * lineH
                c.drawText(line, r.centerX(), centerBaseline(top + lineH / 2, paint), paint)
            }

            if (cell.holidayName != null) {
                val hcol = col(dh.holidayColor)
                when (dh.holidayMark) {
                    HolidayMark.NONE -> Unit
                    HolidayMark.DOT -> c.drawCircle(labelRect.right + dp(3f), labelRect.top + dp(2f), dp(1.8f), fill(hcol))
                    HolidayMark.UNDERLINE -> c.drawRect(labelRect.left, labelRect.bottom - dp(0.5f), labelRect.right, labelRect.bottom + dp(0.7f), fill(hcol))
                    HolidayMark.BADGE -> if (showName) {
                        val w = min(namePaint.measureText(cell.holidayName) + dp(6f), r.width())
                        val br = RectF(r.centerX() - w / 2, labelRect.bottom, r.centerX() + w / 2, labelRect.bottom + nameH)
                        c.drawRoundRect(br, br.height() / 2, br.height() / 2, fill(ColorMath.withAlphaFactor(hcol, 0.15f)))
                    }
                }
                if (showName) {
                    val name = TextUtils.ellipsize(cell.holidayName, namePaint, r.width(), TextUtils.TruncateAt.END).toString()
                    c.drawText(name, r.centerX(), centerBaseline(labelRect.bottom + nameH / 2, namePaint), namePaint)
                }
            }
        }

        // ── 휴가 띠 ──

        fun drawVacationBands() {
            val band = t.vacation.headerBand
            l.vacationBands.forEach { vb ->
                val r = vb.box.rect().insetBy(dp(1f), dp(1f))
                c.drawRoundRect(r, dp(band.cornerRadius), dp(band.cornerRadius), fill(col(band.color)))
                val p = textPaint(band.font).apply { textAlign = Paint.Align.CENTER }
                val text = TextUtils.ellipsize(vb.vacation.title, p, r.width() - dp(4f), TextUtils.TruncateAt.END).toString()
                c.drawText(text, r.centerX(), centerBaseline(r.centerY(), p), p)
            }
        }

        // ── 열 틴트 / 그리드 ──

        fun drawColumnTints() {
            l.columnTints.forEach { tint ->
                val r = tint.box.rect()
                when (tint.kind) {
                    TintKind.ALTERNATE -> t.grid.alternateColumnTint?.let { fillRect(r, col(it)) }
                    TintKind.WEEKEND -> t.grid.weekendColumnTint?.let { fillRect(r, col(it)) }
                    TintKind.HOLIDAY -> t.grid.holidayColumnTint?.let { fillRect(r, col(it)) }
                    TintKind.VACATION -> t.vacation.columnTint?.let { fillRect(r, col(it)) }
                    TintKind.TODAY -> {
                        t.today.columnTint?.let { fillRect(r, col(it)) }
                        t.today.columnBorder?.let { drawBorder(r.insetBy(dp(it.width) / 2), dp(3f), it) }
                    }
                }
            }
        }

        fun drawGridLines() {
            val g = t.grid
            val left = l.grid.left
            val right = l.grid.right
            if (g.halfHourLine.visible) l.halfHourLines.forEach { y -> line(left, y, right, y, g.halfHourLine) }
            if (g.hourLine.visible) l.hourLines.forEach { y -> line(left, y, right, y, g.hourLine) }
            if (g.columnDivider.visible) l.columnDividers.forEach { x -> line(x, l.grid.top, x, l.grid.bottom, g.columnDivider) }
        }

        fun drawHourLabels() {
            val ta = t.timeAxis
            val p = textPaint(ta.font).apply { textAlign = Paint.Align.RIGHT }
            val x = l.timeAxis.right - dp(3f)
            l.hourLabels.forEach { hl ->
                val baseline = when (ta.labelAlign) {
                    LabelAlign.TOP_OF_LINE -> hl.y - p.fontMetrics.ascent + dp(1f)
                    LabelAlign.CENTER_ON_LINE -> centerBaseline(hl.y, p)
                }
                if (baseline - p.textSize < l.grid.bottom) c.drawText(hl.text, x, baseline, p)
            }
        }

        // ── 블록 ──

        fun paletteColor(key: String): Int {
            val pal = t.block.palette
            val tc = pal[key]
                ?: if (key.startsWith("#")) ThemeColor(key) else null
                    ?: pal["default"] ?: pal.values.firstOrNull() ?: ThemeColor("#FF9E9E9E")
            return col(tc)
        }

        fun drawBlock(b: BlockLayout) {
            val bt = t.block
            val ct = t.cancelled
            val occ = b.occurrence
            val cancelled = occ.status == OccurrenceStatus.CANCELLED
            val r = b.box.rect()
            if (r.width() <= 0 || r.height() <= 0) return
            val radius = min(dp(bt.cornerRadius), min(r.width(), r.height()) / 2)
            val base = paletteColor(occ.block.colorKey)
            val dim = if (occ.isPast && bt.pastDim > 0f) 1f - bt.pastDim.coerceIn(0f, 1f) else 1f
            if (dim < 1f) c.saveLayerAlpha(r.insetBy(-dp(4f)), (dim * 255).toInt())

            val shape = Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) }
            val textColor: Int
            if (!cancelled) {
                val fillColor = when (bt.fillStyle) {
                    FillStyle.SOLID, FillStyle.GRADIENT -> base
                    FillStyle.TINT, FillStyle.LEFT_BAR -> ColorMath.withAlphaFactor(base, bt.tintAlpha)
                    FillStyle.OUTLINE -> 0
                }
                val fp = fill(fillColor).also { p -> bt.shadow?.let { applyShadow(p, it) } }
                if (bt.fillStyle == FillStyle.GRADIENT) {
                    fp.shader = LinearGradient(0f, r.top, 0f, r.bottom, base, ColorMath.mix(base, 0xFFFFFFFF.toInt(), 0.35f), Shader.TileMode.CLAMP)
                }
                if (fillColor != 0 || bt.fillStyle == FillStyle.GRADIENT) c.drawPath(shape, fp)
                if (bt.fillStyle == FillStyle.LEFT_BAR) {
                    c.save(); c.clipPath(shape)
                    c.drawRect(r.left, r.top, r.left + dp(bt.leftBarWidth), r.bottom, fill(base))
                    c.restore()
                }
                if (bt.fillStyle == FillStyle.OUTLINE) c.drawPath(shape, stroke(base, dp(1.5f)))
                if (bt.border.width > 0f) {
                    val bc = if (bt.border.color.isAuto) ColorMath.darken(base) else col(bt.border.color)
                    c.drawPath(shape, stroke(bc, dp(bt.border.width), bt.border.style))
                }
                val effective = if (bt.fillStyle == FillStyle.OUTLINE) canvasBg else ColorMath.over(fillColor, canvasBg)
                textColor = when {
                    !bt.textColor.isAuto -> col(bt.textColor)
                    !bt.title.color.isAuto -> col(bt.title.color)
                    else -> ColorMath.readableOn(effective)
                }
            } else {
                var fillColor = col(ct.fill)
                if (ct.keepPaletteHue) fillColor = ColorMath.mix(fillColor, base, 0.22f)
                fillColor = ColorMath.withAlphaFactor(fillColor, ct.alpha)
                c.drawPath(shape, fill(fillColor))
                drawPattern(shape, r)
                if (ct.border.width > 0f) {
                    val bc = if (ct.border.color.isAuto) ColorMath.darken(fillColor) else col(ct.border.color)
                    c.drawPath(shape, stroke(bc, dp(ct.border.width), ct.border.style))
                }
                textColor = col(ct.textColor)
            }

            drawBlockText(b, r, textColor, cancelled, base)
            if (cancelled) drawCancelBadge(b, r)
            drawClipIndicators(b, r, textColor)
            if (dim < 1f) c.restore()
        }

        fun drawPattern(shape: Path, r: RectF) {
            val ct = t.cancelled
            if (ct.pattern == Pattern.NONE) return
            val p = stroke(col(ct.patternColor), dp(ct.patternWidth))
            val spacing = dp(ct.patternSpacing).coerceAtLeast(dp(2f))
            c.save()
            c.clipPath(shape)
            when (ct.pattern) {
                Pattern.HATCH, Pattern.CROSS -> {
                    hatch(r, spacing, ct.patternAngle, p)
                    if (ct.pattern == Pattern.CROSS) hatch(r, spacing, -ct.patternAngle, p)
                }
                Pattern.DOTS -> {
                    val dot = fill(col(ct.patternColor))
                    var y = r.top + spacing / 2
                    var row = 0
                    while (y < r.bottom) {
                        var x = r.left + spacing / 2 + if (row % 2 == 1) spacing / 2 else 0f
                        while (x < r.right) { c.drawCircle(x, y, dp(ct.patternWidth) * 0.8f, dot); x += spacing }
                        y += spacing; row++
                    }
                }
                Pattern.NONE -> Unit
            }
            c.restore()
        }

        fun hatch(r: RectF, spacing: Float, angleDeg: Float, p: Paint) {
            val diag = sqrt(r.width() * r.width() + r.height() * r.height())
            c.save()
            c.rotate(angleDeg, r.centerX(), r.centerY())
            var x = r.centerX() - diag
            while (x < r.centerX() + diag) {
                c.drawLine(x, r.centerY() - diag, x, r.centerY() + diag, p)
                x += spacing
            }
            c.restore()
        }

        fun drawBlockText(b: BlockLayout, r: RectF, color: Int, cancelled: Boolean, base: Int) {
            if (!b.showTitle) return
            val bt = t.block
            val padL = dp(bt.padding.start) + if (bt.fillStyle == FillStyle.LEFT_BAR && !cancelled) dp(bt.leftBarWidth) else 0f
            val left = r.left + padL
            val right = r.right - dp(bt.padding.end)
            var top = r.top + dp(bt.padding.top)
            val width = right - left
            if (width <= dp(4f)) return

            var titleLeft = left
            if (bt.icon == BlockIcon.DOT) {
                val rad = dp(2.5f)
                val tp = textPaint(bt.title)
                c.drawCircle(left + rad, top + tp.fontSpacing / 2, rad, fill(if (cancelled) color else ColorMath.darken(base, 0.1f)))
                titleLeft += rad * 2 + dp(3f)
            }
            val titlePaint = textPaint(bt.title, color).apply { isStrikeThruText = cancelled && t.cancelled.strikeThrough }
            val badgeSpace = if (cancelled && t.cancelled.badge != Badge.NONE) dp(10f) else 0f
            top += drawMultiline(occTitle(b), titlePaint, titleLeft, top, right - titleLeft - badgeSpace, b.titleMaxLines)
            if (b.showTime) {
                val tp = textPaint(bt.time, color)
                val text = WeekLayout.formatTimeRange(bt.timeFormat, b.occurrence.startMin, b.occurrence.endMin)
                top += drawMultiline(text, tp, left, top, width, 1)
            }
            if (b.showPlace) {
                val tp = textPaint(bt.time, color)
                drawMultiline(b.occurrence.block.place.orEmpty(), tp, left, top, width, 1)
            }
        }

        fun occTitle(b: BlockLayout) = b.occurrence.block.title

        fun drawCancelBadge(b: BlockLayout, r: RectF) {
            val ct = t.cancelled
            val p = textPaint(ct.badgeFont).apply { textAlign = Paint.Align.RIGHT }
            val text = when (ct.badge) {
                Badge.NONE -> return
                Badge.TEXT -> ct.badgeText
                Badge.ICON -> b.occurrence.displayReason?.let { ct.reasonIcons[it.name] } ?: ct.badgeText
            }
            if (r.height() < p.fontSpacing) return
            c.drawText(text, r.right - dp(2f), r.top + dp(1f) - p.fontMetrics.ascent, p)
        }

        fun drawClipIndicators(b: BlockLayout, r: RectF, color: Int) {
            when (t.block.clipIndicator) {
                ClipIndicator.NONE -> Unit
                ClipIndicator.ARROW -> {
                    val s = dp(3f)
                    val p = fill(ColorMath.withAlphaFactor(color, 0.7f))
                    if (b.clippedTop) c.drawPath(triangle(r.centerX(), r.top + dp(1f), s, up = true), p)
                    if (b.clippedBottom) c.drawPath(triangle(r.centerX(), r.bottom - dp(1f), s, up = false), p)
                }
                ClipIndicator.FADE -> {
                    val h = dp(8f)
                    if (b.clippedTop) c.drawRect(r.left, r.top, r.right, r.top + h, Paint().apply {
                        shader = LinearGradient(0f, r.top, 0f, r.top + h, canvasBg, ColorMath.withAlpha(canvasBg, 0f), Shader.TileMode.CLAMP)
                    })
                    if (b.clippedBottom) c.drawRect(r.left, r.bottom - h, r.right, r.bottom, Paint().apply {
                        shader = LinearGradient(0f, r.bottom, 0f, r.bottom - h, canvasBg, ColorMath.withAlpha(canvasBg, 0f), Shader.TileMode.CLAMP)
                    })
                }
            }
        }

        fun triangle(cx: Float, tipY: Float, s: Float, up: Boolean) = Path().apply {
            val dir = if (up) 1 else -1
            moveTo(cx, tipY); lineTo(cx - s, tipY + dir * s); lineTo(cx + s, tipY + dir * s); close()
        }

        // ── 포스트잇 ──

        fun drawPostIt(pl: PostItLayout) {
            val pt = t.postIt
            val ev = pl.event
            val r = pl.box.rect()
            val paper = col(pt.papers[ev.event.paperKey] ?: pt.papers.values.firstOrNull() ?: ThemeColor("#FFFFE98A"))
            val radius = dp(pt.cornerRadius)
            val fold = if (pt.foldCorner.position != FoldPosition.NONE) min(dp(pt.foldCorner.size), min(r.width(), r.height()) / 3) else 0f

            c.save()
            c.rotate(pl.rotationDeg, r.centerX(), r.centerY())
            val alpha = if (ev.underState != UnderState.NONE) pt.overlapOpacity else 1f
            if (alpha < 1f) c.saveLayerAlpha(r.insetBy(-dp(8f)), (alpha * 255).toInt())

            val shape = paperPath(r, radius, fold, pt.foldCorner.position)
            val pp = fill(paper).also { p -> pt.shadow?.let { applyShadow(p, it) } }
            c.drawPath(shape, pp)
            if (fold > 0f) c.drawPath(foldPath(r, fold, pt.foldCorner.position), fill(ColorMath.darken(paper, 0.12f)))

            // 글자
            val left = r.left + dp(pt.padding.start)
            val right = r.right - dp(pt.padding.end) - if (pt.foldCorner.position == FoldPosition.TOP_RIGHT) fold else 0f
            var top = r.top + dp(pt.padding.top) + if (pt.attach == Attach.TAPE || pt.attach == Attach.PIN) dp(2f) else 0f
            val bottom = r.bottom - dp(pt.padding.bottom)
            val titlePaint = textPaint(pt.font)
            val timeH = if (pl.showTime) textPaint(pt.time).fontSpacing else 0f
            val lines = max(1, ((bottom - top - timeH) / titlePaint.fontSpacing).toInt())
            top += drawMultiline(ev.event.title, titlePaint, left, top, right - left, lines)
            if (pl.showTime) {
                val text = WeekLayout.formatTimeRange(t.block.timeFormat, ev.event.startMin, ev.event.endMin)
                drawMultiline(text, textPaint(pt.time), left, top, right - left, 1)
            }

            drawAttach(r)
            if (alpha < 1f) c.restore()
            drawUnderBadge(pl, r)
            c.restore()
        }

        fun paperPath(r: RectF, radius: Float, fold: Float, pos: FoldPosition) = Path().apply {
            if (fold <= 0f) { addRoundRect(r, radius, radius, Path.Direction.CW); return@apply }
            when (pos) {
                FoldPosition.BOTTOM_RIGHT -> {
                    moveTo(r.left, r.top); lineTo(r.right, r.top); lineTo(r.right, r.bottom - fold)
                    lineTo(r.right - fold, r.bottom); lineTo(r.left, r.bottom); close()
                }
                FoldPosition.TOP_RIGHT -> {
                    moveTo(r.left, r.top); lineTo(r.right - fold, r.top); lineTo(r.right, r.top + fold)
                    lineTo(r.right, r.bottom); lineTo(r.left, r.bottom); close()
                }
                FoldPosition.NONE -> addRect(r, Path.Direction.CW)
            }
        }

        fun foldPath(r: RectF, fold: Float, pos: FoldPosition) = Path().apply {
            when (pos) {
                FoldPosition.BOTTOM_RIGHT -> { moveTo(r.right, r.bottom - fold); lineTo(r.right - fold, r.bottom - fold); lineTo(r.right - fold, r.bottom); close() }
                FoldPosition.TOP_RIGHT -> { moveTo(r.right - fold, r.top); lineTo(r.right - fold, r.top + fold); lineTo(r.right, r.top + fold); close() }
                FoldPosition.NONE -> Unit
            }
        }

        fun drawAttach(r: RectF) {
            val pt = t.postIt
            val color = ColorMath.withAlphaFactor(col(pt.tapeColor), pt.tapeAlpha)
            when (pt.attach) {
                Attach.NONE -> Unit
                Attach.TAPE -> {
                    val w = min(r.width() * 0.45f, dp(28f))
                    val h = dp(6f)
                    c.save()
                    c.rotate(pt.tapeAngle, r.centerX(), r.top)
                    c.drawRect(r.centerX() - w / 2, r.top - h / 2, r.centerX() + w / 2, r.top + h / 2, fill(color))
                    c.restore()
                }
                Attach.PIN -> {
                    val rad = dp(3f)
                    val pin = col(pt.tapeColor)
                    c.drawCircle(r.centerX(), r.top + rad * 0.6f, rad, fill(pin).also { applyShadow(it, Shadow(0f, 1f, 1.5f)) })
                    c.drawCircle(r.centerX() - rad * 0.3f, r.top + rad * 0.3f, rad * 0.35f, fill(ColorMath.withAlphaFactor(0xFFFFFFFF.toInt(), 0.7f)))
                }
                Attach.CLIP -> {
                    val x = r.left + dp(6f)
                    val clip = RectF(x - dp(2.5f), r.top - dp(4f), x + dp(2.5f), r.top + dp(7f))
                    c.drawRoundRect(clip, dp(2.5f), dp(2.5f), stroke(ColorMath.darken(col(pt.tapeColor), 0.4f), dp(1.2f)))
                }
            }
        }

        fun drawUnderBadge(pl: PostItLayout, r: RectF) {
            val pt = t.postIt
            if (pt.underBadge == UnderBadge.NONE) return
            val key = when (pl.event.underState) {
                UnderState.NONE -> return
                UnderState.KEEP -> "KEEP"
                UnderState.CANCEL -> "CANCEL"
            }
            val text = pt.underBadgeTexts[key] ?: return
            val bg = pt.underBadgeColors[key]?.let(::col) ?: return
            val p = textPaint(pt.underBadgeFont).apply { textAlign = Paint.Align.CENTER }
            val w = p.measureText(text) + dp(6f)
            val h = p.fontSpacing + dp(1f)
            val br = RectF(r.right - w + dp(3f), r.top - h / 2, r.right + dp(3f), r.top + h / 2)
            c.drawRoundRect(br, h / 2, h / 2, fill(bg))
            c.drawText(text, br.centerX(), centerBaseline(br.centerY(), p), p)
        }

        // ── 현재 시간선 ──

        fun drawNowLine() {
            val nl = l.nowLine ?: return
            val tk = t.nowLine
            val color = col(tk.color)
            val p = stroke(color, dp(tk.width), tk.style).also { paint -> tk.glow?.let { applyShadow(paint, it) } }
            c.drawLine(nl.startX, nl.y, nl.endX, nl.y, p)
            val s = dp(tk.headSize)
            when (tk.head) {
                NowHead.NONE -> Unit
                NowHead.DOT -> c.drawCircle(nl.startX, nl.y, s, fill(color))
                NowHead.TRIANGLE -> c.drawPath(Path().apply {
                    moveTo(nl.startX, nl.y - s); lineTo(nl.startX + s * 1.4f, nl.y); lineTo(nl.startX, nl.y + s); close()
                }, fill(color))
                NowHead.PILL_TIME -> {
                    val tp = textPaint(tk.timeFont).apply { textAlign = Paint.Align.CENTER }
                    val text = TimeStep.format(nl.minuteOfDay)
                    val w = tp.measureText(text) + dp(6f)
                    val h = tp.fontSpacing + dp(1f)
                    val pr = RectF(nl.startX - w, nl.y - h / 2, nl.startX, nl.y + h / 2)
                    c.drawRoundRect(pr, h / 2, h / 2, fill(color))
                    c.drawText(text, pr.centerX(), centerBaseline(pr.centerY(), tp), tp)
                }
            }
        }

        fun drawEmptyState() {
            val box = l.emptyState ?: return
            val es = t.emptyState
            val p = textPaint(es.font).apply { textAlign = Paint.Align.CENTER }
            c.drawText(es.text, box.centerX, centerBaseline(box.centerY, p), p)
        }

        // ── 도구 ──

        fun Box.rect() = RectF(left, top, right, bottom)
        fun RectF.insetBy(d: Float) = RectF(left + d, top + d, right - d, bottom - d)
        fun RectF.insetBy(dx: Float, dy: Float) = RectF(left + dx, top + dy, right - dx, bottom - dy)

        fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

        fun stroke(color: Int, width: Float, style: LineStyle = LineStyle.SOLID) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.style = Paint.Style.STROKE
            strokeWidth = width
            when (style) {
                LineStyle.SOLID -> Unit
                LineStyle.DASHED -> pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(3f)), 0f)
                LineStyle.DOTTED -> {
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(0.1f, max(dp(2.5f), width * 2.5f)), 0f)
                }
            }
        }

        fun fillRect(r: RectF, color: Int) = c.drawRect(r, fill(color))

        fun line(x1: Float, y1: Float, x2: Float, y2: Float, spec: LineSpec) {
            c.drawLine(x1, y1, x2, y2, stroke(col(spec.color), dp(spec.width), spec.style))
        }

        fun drawBorder(r: RectF, radius: Float, border: Border) {
            if (border.width <= 0f || border.color.isAuto) return
            val w = dp(border.width)
            val rr = RectF(r.left + w / 2, r.top + w / 2, r.right - w / 2, r.bottom - w / 2)
            c.drawRoundRect(rr, radius, radius, stroke(col(border.color), w, border.style))
        }

        fun applyShadow(p: Paint, s: Shadow) {
            p.setShadowLayer(dp(s.blur).coerceAtLeast(0.1f), dp(s.dx), dp(s.dy), col(s.color))
        }

        fun textPaint(f: FontSpec, colorOverride: Int? = null) = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = typefaceFor(f)
            textSize = sp(f.size)
            color = colorOverride ?: if (f.color.isAuto) ColorMath.readableOn(canvasBg) else col(f.color)
            letterSpacing = f.letterSpacing
            if (Build.VERSION.SDK_INT < 28 && f.weight >= 600) isFakeBoldText = typeface?.isBold != true
        }

        fun typefaceFor(f: FontSpec): Typeface {
            val ref = t.fonts[f.font] ?: f.font
            val base = res.typeface(ref)
            return if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, f.weight.coerceIn(1, 1000), false)
            else Typeface.create(base, if (f.weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        }

        fun centerBaseline(cy: Float, p: Paint): Float {
            val fm = p.fontMetrics
            return cy - (fm.ascent + fm.descent) / 2
        }

        /** 여러 줄 글자 (말줄임). 쓴 높이를 돌려준다. */
        fun drawMultiline(text: String, p: TextPaint, x: Float, y: Float, width: Float, maxLines: Int): Float {
            if (width < 1f || text.isEmpty()) return 0f
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(maxLines.coerceAtLeast(1))
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build()
            c.save()
            c.translate(x, y)
            layout.draw(c)
            c.restore()
            return layout.height.toFloat()
        }
    }
}
