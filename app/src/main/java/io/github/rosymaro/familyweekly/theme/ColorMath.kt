package io.github.rosymaro.familyweekly.theme

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** ARGB Int 색 계산. android.graphics.Color 없이 (JVM 테스트용). */
object ColorMath {

    /** "#RGB", "#RRGGBB", "#AARRGGBB" → ARGB Int. */
    fun parse(hex: String): Int {
        val h = hex.trim().removePrefix("#")
        val full = when (h.length) {
            3 -> "FF" + h.map { "$it$it" }.joinToString("")
            6 -> "FF$h"
            8 -> h
            else -> throw IllegalArgumentException("색 형식이 틀렸어요: $hex (예: #FFAA00, #80FFAA00)")
        }
        return full.toLong(16).toInt()
    }

    fun toHex(argb: Int): String = "#%08X".format(argb)

    fun alpha(c: Int) = (c ushr 24) and 0xFF
    fun red(c: Int) = (c shr 16) and 0xFF
    fun green(c: Int) = (c shr 8) and 0xFF
    fun blue(c: Int) = c and 0xFF

    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** 알파에 [factor]를 곱함. */
    fun withAlphaFactor(c: Int, factor: Float): Int =
        argb((alpha(c) * factor.coerceIn(0f, 1f)).roundToInt(), red(c), green(c), blue(c))

    fun withAlpha(c: Int, alpha01: Float): Int = argb((alpha01.coerceIn(0f, 1f) * 255).roundToInt(), red(c), green(c), blue(c))

    /** [fg]를 불투명한 [bg] 위에 올린 결과 (불투명). */
    fun over(fg: Int, bg: Int): Int {
        val a = alpha(fg) / 255f
        return argb(
            255,
            (red(fg) * a + red(bg) * (1 - a)).roundToInt(),
            (green(fg) * a + green(bg) * (1 - a)).roundToInt(),
            (blue(fg) * a + blue(bg) * (1 - a)).roundToInt(),
        )
    }

    /** 두 색 섞기. t=0이면 a, 1이면 b. */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).roundToInt()
        return argb(ch(alpha(a), alpha(b)), ch(red(a), red(b)), ch(green(a), green(b)), ch(blue(a), blue(b)))
    }

    /** WCAG 상대 휘도 0..1. */
    fun luminance(c: Int): Double {
        fun lin(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin(red(c)) + 0.7152 * lin(green(c)) + 0.0722 * lin(blue(c))
    }

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a) + 0.05
        val lb = luminance(b) + 0.05
        return max(la, lb) / min(la, lb)
    }

    /** 배경 위에서 잘 읽히는 글자색 (진한 회색 또는 흰색). */
    fun readableOn(bg: Int): Int {
        val dark = 0xFF1F1B16.toInt()
        val light = 0xFFFFFFFF.toInt()
        return if (contrast(bg, dark) >= contrast(bg, light)) dark else light
    }

    /** 진하게 (테두리 "auto"용). */
    fun darken(c: Int, amount: Float = 0.25f): Int {
        val (h, s, l) = toHsl(c)
        return fromHsl(h, s, (l * (1 - amount)).coerceIn(0f, 1f), alpha(c))
    }

    /**
     * 다크 모드 색 자동 생성: 명도 반전 (색상·채도 유지, 알파 유지).
     * 아주 진한 색 → 아주 밝게가 되지 않도록 0.08..0.92로 제한.
     */
    fun autoDark(c: Int): Int {
        val (h, s, l) = toHsl(c)
        return fromHsl(h, s, (1f - l).coerceIn(0.08f, 0.92f), alpha(c))
    }

    fun toHsl(c: Int): Triple<Float, Float, Float> {
        val r = red(c) / 255f
        val g = green(c) / 255f
        val b = blue(c) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2
        if (mx == mn) return Triple(0f, 0f, l)
        val d = mx - mn
        val s = if (l > 0.5f) d / (2 - mx - mn) else d / (mx + mn)
        val h = when (mx) {
            r -> ((g - b) / d + (if (g < b) 6 else 0))
            g -> ((b - r) / d + 2)
            else -> ((r - g) / d + 4)
        } * 60f
        return Triple(h, s, l)
    }

    fun fromHsl(h: Float, s: Float, l: Float, alpha: Int = 255): Int {
        val c = (1 - abs(2 * l - 1)) * s
        val hp = (h % 360f) / 60f
        val x = c * (1 - abs(hp % 2 - 1))
        val (r1, g1, b1) = when {
            hp < 1 -> Triple(c, x, 0f)
            hp < 2 -> Triple(x, c, 0f)
            hp < 3 -> Triple(0f, c, x)
            hp < 4 -> Triple(0f, x, c)
            hp < 5 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2
        return argb(alpha, ((r1 + m) * 255).roundToInt(), ((g1 + m) * 255).roundToInt(), ((b1 + m) * 255).roundToInt())
    }
}
