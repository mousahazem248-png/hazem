package com.ultradownloader.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultradownloader.app.data.connector.LinkAnalyzerService
import com.ultradownloader.app.data.download.DownloadManagerRepository
import com.ultradownloader.app.data.local.AppLanguage
import com.ultradownloader.app.data.local.ThemePreference
import com.ultradownloader.app.data.local.UserPreferencesRepository
import com.ultradownloader.app.data.local.UserSettings
import com.ultradownloader.app.domain.model.AppError
import com.ultradownloader.app.domain.model.DownloadTask
import com.ultradownloader.app.domain.model.ErrorCategory
import com.ultradownloader.app.domain.model.MediaAnalysisResult
import com.ultradownloader.app.domain.model.MediaFormat
import com.ultradownloader.app.domain.model.PlatformType
import com.ultradownloader.app.security.UrlSecurityValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val urlInput: String = "",
    val detectedPlatform: PlatformType = PlatformType.UNKNOWN,
    val isAnalyzing: Boolean = false,
    val latestAnalysis: MediaAnalysisResult? = null,
    val selectedFormat: MediaFormat? = null,
    val latestError: AppError? = null,
    val activePlayerTask: DownloadTask? = null
)

@HiltViewModel
class UltraMainViewModel @Inject constructor(
    private val urlSecurityValidator: UrlSecurityValidator,
    private val linkAnalyzerService: LinkAnalyzerService,
    private val downloadManagerRepository: DownloadManagerRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val settingsState: StateFlow<UserSettings> = userPreferencesRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    val activeDownloads: StateFlow<List<DownloadTask>> = downloadManagerRepository.activeTasksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedDownloads: StateFlow<List<DownloadTask>> = downloadManagerRepository.completedTasksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onUrlInputChanged(newInput: String) {
        val detected = if (newInput.isBlank()) {
            PlatformType.UNKNOWN
        } else {
            urlSecurityValidator.detectPlatform(newInput)
        }
        _uiState.update {
            it.copy(
                urlInput = newInput,
                detectedPlatform = detected,
                latestError = null
            )
        }
    }

    fun analyzeCurrentLink(onNavigateToPreview: () -> Unit, onNavigateToError: () -> Unit) {
        val currentUrl = _uiState.value.urlInput
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, latestError = null) }
            try {
                val result = linkAnalyzerService.analyzeUrl(currentUrl)
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        latestAnalysis = result,
                        selectedFormat = result.availableFormats.firstOrNull(),
                        detectedPlatform = result.platform
                    )
                }
                onNavigateToPreview()
            } catch (appErr: AppError) {
                _uiState.update {
                    it.copy(isAnalyzing = false, latestError = appErr)
                }
                onNavigateToError()
            } catch (e: Exception) {
                val fallbackErr = AppError(
                    category = ErrorCategory.SERVER_OR_PARSING_ERROR,
                    titleAr = "حدث خطأ أثناء معالجة الرابط",
                    titleEn = "Link Processing Error",
                    descriptionAr = e.message ?: "تعذر الاتصال بالخادم لتحليل الرابط.",
                    descriptionEn = e.message ?: "Unable to reach remote server to analyze the link.",
                    suggestedFixesAr = listOf(
                        "تحقق من اتصالك بالإنترنت وحاول مرة أخرى.",
                        "تأكد من أن الرابط عام ويعمل في المتصفح."
                    ),
                    suggestedFixesEn = listOf(
                        "Check your internet connection and try again.",
                        "Confirm that the link is public and accessible."
                    )
                )
                _uiState.update {
                    it.copy(isAnalyzing = false, latestError = fallbackErr)
                }
                onNavigateToError()
            }
        }
    }

    fun selectFormat(format: MediaFormat) {
        _uiState.update { it.copy(selectedFormat = format) }
    }

    fun startDownloadSelectedFormat(onQueued: () -> Unit) {
        val analysis = _uiState.value.latestAnalysis ?: return
        val format = _uiState.value.selectedFormat ?: return
        viewModelScope.launch {
            downloadManagerRepository.enqueueDownload(analysis, format)
            onQueued()
        }
    }

    fun pauseDownload(taskId: String) {
        viewModelScope.launch {
            downloadManagerRepository.pauseDownload(taskId)
        }
    }

    fun resumeOrRetryDownload(taskId: String) {
        viewModelScope.launch {
            downloadManagerRepository.resumeOrRetryDownload(taskId)
        }
    }

    fun deleteDownload(taskId: String) {
        viewModelScope.launch {
            downloadManagerRepository.cancelAndDeleteTask(taskId, deleteMediaFromDisk = true)
        }
    }

    fun openInLocalPlayer(task: DownloadTask, onNavigateToPlayer: () -> Unit) {
        _uiState.update { it.copy(activePlayerTask = task) }
        onNavigateToPlayer()
    }

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { userPreferencesRepository.setTheme(theme) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { userPreferencesRepository.setLanguage(language) }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setWifiOnly(enabled) }
    }

    fun setMaxConcurrentDownloads(limit: Int) {
        viewModelScope.launch { userPreferencesRepository.setMaxConcurrentDownloads(limit) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { userPreferencesRepository.setOnboardingCompleted(true) }
    }
}
