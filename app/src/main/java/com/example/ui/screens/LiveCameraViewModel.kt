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
    val errorMessage: String? = null,
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

    fun reportCameraError(message: String?) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun processCameraFrame(imageProxy: ImageProxy) {
        if (!isFrameBusy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val frameBitmap = try {
            BitmapUtils.imageProxyToFrontBitmap(imageProxy, maxDimension = 540)
        } catch (_: Throwable) {
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
                        // Owned copies: the bitmaps handed to Compose must never share a
                        // pixel buffer with any pipeline intermediate (v1.3 recycled-bitmap
                        // crash happened when Compose drew a bitmap recycled elsewhere).
                        val frameCopy = ownedCopy(frameBitmap)
                        val recoloredCopy = ownedCopy(recolored)
                        _uiState.update { current ->
                            current.copy(
                                originalFrame = frameCopy,
                                recoloredFrame = recoloredCopy,
                                noHairDetected = false,
                                errorMessage = null,
                                skinAnalysis = updatedSkin ?: current.skinAnalysis
                            )
                        }
                    }

                    HairMaskResult.NoHairDetected -> {
                        val frameCopy = ownedCopy(frameBitmap)
                        _uiState.update { current ->
                            current.copy(
                                originalFrame = frameCopy,
                                recoloredFrame = frameCopy,
                                noHairDetected = true,
                                errorMessage = null,
                                skinAnalysis = updatedSkin ?: current.skinAnalysis
                            )
                        }
                    }

                    is HairMaskResult.Error -> {
                        val frameCopy = ownedCopy(frameBitmap)
                        _uiState.update { current ->
                            current.copy(
                                originalFrame = frameCopy,
                                recoloredFrame = frameCopy,
                                noHairDetected = false,
                                errorMessage = maskResult.message,
                                skinAnalysis = updatedSkin ?: current.skinAnalysis
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                val frameCopy = ownedCopy(frameBitmap)
                _uiState.update { current ->
                    current.copy(
                        originalFrame = frameCopy,
                        recoloredFrame = frameCopy,
                        errorMessage = t.message?.take(200) ?: t.javaClass.simpleName
                    )
                }
            } finally {
                isFrameBusy.set(false)
            }
        }
    }

    /** Returns an owned copy with its own pixel buffer, so no pipeline step can
     * invalidate the bitmap while Compose is drawing it. */
    private fun ownedCopy(bitmap: Bitmap): Bitmap =
        bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap

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
