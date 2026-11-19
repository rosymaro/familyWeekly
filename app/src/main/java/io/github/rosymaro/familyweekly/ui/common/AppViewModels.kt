package io.github.rosymaro.familyweekly.ui.common

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.rosymaro.familyweekly.FamilyWeeklyApp
import io.github.rosymaro.familyweekly.domain.AppData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Application을 받아 ViewModel을 만드는 팩토리 (DI 없음). */
inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: (app: FamilyWeeklyApp, handle: SavedStateHandle) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FamilyWeeklyApp
        create(app, createSavedStateHandle())
    }
}

/**
 * 저장은 앱 범위(IO)에서 한다: 저장하고 바로 화면을 닫아도 저장이 취소되지 않게.
 * 저장이 실패하면(더 새 버전 파일 등) [onError]를 메인 스레드에서 부른다.
 */
abstract class StoreViewModel(protected val app: FamilyWeeklyApp) : ViewModel() {
    val data = app.store.data

    protected fun update(onError: (Throwable) -> Unit = {}, transform: (AppData) -> AppData) {
        app.appScope.launch {
            runCatching { app.store.update(transform) }.onFailure { e ->
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }
}
