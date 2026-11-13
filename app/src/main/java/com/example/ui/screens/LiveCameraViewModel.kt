package com.example.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ml.HairMaskResult
import com.example.ml.HairRecolorProcessor
import com.example.ml.HairSegmentationEngine
import com.example.model.HairColor
import com.example.model.HairPalette
import com.example.model.SkinAnalysisResult
import com.example.model.SkinToneRecommender
import com.example.utils.BitmapUtils
import com.example.utils.MediaStoreSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class LiveCameraUiState(
    val selectedColor: HairColor = HairPalette.defaultSelected,
    val intensity: Float = 0.72f,
    val originalFrame: Bitmap? = null,
    val recoloredFrame: Bitmap? = null,
    val noHairDetected: Boolean = false,
    val isShowingOriginal: Boolean = false,
    val isSaving: Boolean = false,
    val skinAnalysis: SkinAnalysisResult = SkinToneRecommender.fallbackSuggestions()
)

sealed interface LiveCameraEvent {
    data class SaveCompleted(val success: Boolean) : LiveCameraEvent
}

class LiveCameraViewModel(application: Application) : AndroidViewModel(application) {

    private val segmentationEngine = HairSegmentationEngine(application)
    private val isFrameBusy = AtomicBoolean(false)
    private var lastSkinAnalysisTimestamp = 0L

    private val _uiState = MutableStateFlow(LiveCameraUiState())
    val uiState: StateFlow<LiveCameraUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LiveCameraEvent>()
    val events: SharedFlow<LiveCameraEvent> = _events.asSharedFlow()

    fun selectColor(color: HairColor) {
        _uiState.update { it.copy(selectedColor = color) }
    }

    fun setIntensity(value: Float) {
        _uiState.update { it.copy(intensity = value.coerceIn(0.1f, 1f)) }
    }

    fun setShowingOriginal(showing: Boolean) {
        _uiState.update { it.copy(isShowingOriginal = showing) }
    }

    fun processCameraFrame(imageProxy: ImageProxy) {
        if (!isFrameBusy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val frameBitmap = try {
            BitmapUtils.imageProxyToFrontBitmap(imageProxy, maxDimension = 540)
        } catch (_: Exception) {
            imageProxy.close()
            isFrameBusy.set(false)
            return
        }
        imageProxy.close()

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val snapshot = _uiState.value
                val maskResult = segmentationEngine.segmentHair(frameBitmap)
                val now = SystemClock.elapsedRealtime()
                val updatedSkin = if (now - lastSkinAnalysisTimestamp >= SKIN_ANALYSIS_INTERVAL_MS) {
                    lastSkinAnalysisTimestamp = now
                    segmentationEngine.analyzeSkinTone(frameBitmap)
                } else {
                    null
                }

                when (maskResult) {
                    is HairMaskResult.Success -> {
                        val recolored = HairRecolorProcessor.recolorHair(
                            source = frameBitmap,
                            mask = maskResult.mask,
                            targetColorInt = snapshot.selectedColor.colorInt,
                            intensity = snapshot.intensity
                        )
                        _uiState.update { current ->
                            current.copy(
                                originalFrame = frameBitmap,
                                recoloredFrame = recolored,
                                noHairDetected = false,
                                skinAnalysis = updatedSkin ?: current.skinAnalysis
                            )
                        }
                    }

                    HairMaskResult.NoHairDetected -> {
                        _uiState.update { current ->
                            current.copy(
                                originalFrame = frameBitmap,
                                recoloredFrame = frameBitmap,
                                noHairDetected = true,
                                skinAnalysis = updatedSkin ?: current.skinAnalysis
                            )
                        }
                    }
                }
            } finally {
                isFrameBusy.set(false)
            }
        }
    }

    fun captureCurrentFrame() {
        val snapshot = _uiState.value
        val bitmapToSave = if (snapshot.noHairDetected) {
            snapshot.originalFrame
        } else {
            snapshot.recoloredFrame ?: snapshot.originalFrame
        } ?: return

        if (snapshot.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val success = MediaStoreSaver.savePngToPictures(
                context = getApplication(),
                bitmap = bitmapToSave,
                prefix = "LiveHairColor"
            )
            _uiState.update { it.copy(isSaving = false) }
            _events.emit(LiveCameraEvent.SaveCompleted(success))
        }
    }

    override fun onCleared() {
        super.onCleared()
        segmentationEngine.close()
    }

    companion object {
        private const val SKIN_ANALYSIS_INTERVAL_MS = 1800L
    }
}
