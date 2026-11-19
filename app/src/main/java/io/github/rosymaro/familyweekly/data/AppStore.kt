package io.github.rosymaro.familyweekly.data

import io.github.rosymaro.familyweekly.domain.AppData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 불러올 때 생긴 문제. UI가 안내를 띄운다. */
sealed interface LoadIssue {
    /** 파일이 깨져서 [backup]으로 옮기고 빈 데이터로 시작함. */
    data class Corrupted(val backup: File, val message: String) : LoadIssue

    /** 더 새 버전 앱이 만든 파일. 덮어쓰지 않으려고 저장을 막음. */
    data class NewerVersion(val fileVersion: Int) : LoadIssue
}

/**
 * 앱 데이터 저장소. `files/family_weekly.json` 하나에 전부 저장한다 (DB 없음).
 * - 저장은 임시 파일에 쓴 뒤 rename (쓰는 중에 앱이 죽어도 이전 파일이 남음)
 * - 저장할 때마다 [data]가 바뀌고, 앱 화면·위젯·알림이 이것을 구독한다
 * - Android 의존성 없음 (JVM 테스트에서 임시 폴더로 씀)
 */
class AppStore(private val dir: File) {

    private val file = File(dir, FILE_NAME)
    private val tmp = File(dir, "$FILE_NAME.tmp")

    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _loadIssue = MutableStateFlow<LoadIssue?>(null)
    val loadIssue: StateFlow<LoadIssue?> = _loadIssue.asStateFlow()

    /** 더 새 버전 파일을 읽었을 때 true — 저장하지 않는다. */
    private var readOnly = false

    init {
        load()
    }

    @Synchronized
    fun load() {
        readOnly = false
        _loadIssue.value = null
        if (!file.exists()) {
            _data.value = AppData()
            return
        }
        val text = file.readText()
        val obj = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            corrupted(e)
            return
        }
        val version = try {
            obj["version"]?.jsonPrimitive?.int ?: 1
        } catch (e: Exception) {
            corrupted(e)
            return
        }
        if (version > AppData.CURRENT_VERSION) {
            readOnly = true
            _loadIssue.value = LoadIssue.NewerVersion(version)
            _data.value = AppData()
            return
        }
        try {
            _data.value = json.decodeFromJsonElement(AppData.serializer(), Migrations.migrate(obj, version))
        } catch (e: Exception) {
            corrupted(e)
        }
    }

    /** 데이터를 바꾸고 바로 저장. 바뀐 데이터를 돌려준다. 메인 스레드가 아닌 곳에서 부른다. */
    @Synchronized
    fun update(transform: (AppData) -> AppData): AppData {
        val next = transform(_data.value).copy(version = AppData.CURRENT_VERSION)
        if (next == _data.value) return next
        write(next)
        _data.value = next
        return next
    }

    fun clearLoadIssue() {
        _loadIssue.value = null
    }

    private fun write(data: AppData) {
        check(!readOnly) { "더 새 버전 앱이 만든 데이터라 저장하지 않습니다." }
        dir.mkdirs()
        tmp.writeText(json.encodeToString(AppData.serializer(), data))
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** 깨진 파일은 지우지 않고 옆에 백업해 둔다. */
    private fun corrupted(e: Exception) {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val backup = File(dir, "family_weekly.corrupt-$stamp.json")
        file.renameTo(backup)
        _data.value = AppData()
        _loadIssue.value = LoadIssue.Corrupted(backup, e.message ?: e.javaClass.simpleName)
    }

    companion object {
        const val FILE_NAME = "family_weekly.json"

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
            explicitNulls = false
        }
    }
}

/** 저장 형식 버전 올리기. 버전을 올릴 때 여기에 `fromVersion → fromVersion+1` 단계를 추가하고 테스트도 넣는다. */
object Migrations {
    fun migrate(obj: JsonObject, fromVersion: Int): JsonObject {
        var current = obj
        var v = fromVersion
        while (v < AppData.CURRENT_VERSION) {
            current = when (v) {
                // 1 → 2: 특이 일정에 detachedFrom(포스트잇 뗀 스케줄)이 생김. 없으면 빈 목록이라 그대로 읽힌다
                1 -> current
                else -> error("알 수 없는 저장 형식 버전: $v")
            }
            v++
        }
        return current
    }
}
