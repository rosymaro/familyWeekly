package io.github.rosymaro.familyweekly.quality

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 안드로이드 정규식(ICU)은 JVM보다 엄격해서, JVM 테스트는 통과해도 폰에서 오류가 날 수 있다
 * (실제로 설정 화면이 꺼지던 원인). 앱 코드의 Regex(...)에 이스케이프하지 않은 { } 가 없는지 검사한다.
 */
class AndroidRegexSafetyTest {
    @Test
    fun noUnescapedBracesInRegexLiterals() {
        val bad = mutableListOf<String>()
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            Regex("""Regex\("((?:[^"\\]|\\.)*)"""").findAll(f.readText()).forEach { m ->
                val pattern = m.groupValues[1].replace("\\\\", "\\")
                // 이스케이프된 \{ \} 와 문자열 템플릿 ${...} 은 빼고, 남은 { } 가 있으면 위험
                val stripped = pattern.replace("\\{", "").replace("\\}", "").replace(Regex("""\$\{[^}]*}"""), "")
                if ('{' in stripped || '}' in stripped) bad += "${f.name}: ${m.value}"
            }
        }
        assertTrue("안드로이드에서 오류 날 수 있는 정규식: $bad", bad.isEmpty())
    }
}
