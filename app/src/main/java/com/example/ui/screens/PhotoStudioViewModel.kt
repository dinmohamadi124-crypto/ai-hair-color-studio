package com.example.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhotoStudioUiState(
    val selectedColor: HairColor = HairPalette.defaultSelected,
    val intensity: Float = 0.78f,
    val originalBitmap: Bitmap? = null,
    val recoloredBitmap: Bitmap? = null,
    val hasMask: Boolean = false,
    val noHairDetected: Boolean = false,
    val isProcessing: Boolean = false,
    val isSaving: Boolean = false,
    val loadError: Boolean = false,
    val errorMessage: String? = null,
    val skinAnalysis: SkinAnalysisResult = SkinToneRecommender.fallbackSuggestions()
)

sealed interface PhotoStudioEvent {
    data class SaveCompleted(val success: Boolean) : PhotoStudioEvent
}

class PhotoStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val segmentationEngine = HairSegmentationEngine(application)
    private var cachedMask: FloatArray? = null
    private var recolorJob: Job? = null

    private val _uiState = MutableStateFlow(PhotoStudioUiState())
    val uiState: StateFlow<PhotoStudioUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PhotoStudioEvent>()
    val events: SharedFlow<PhotoStudioEvent> = _events.asSharedFlow()

    fun loadImageFromUri(uri: Uri) {
        recolorJob?.cancel()
        viewModelScope.launch {
            try {
                loadImageInternal(uri)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        loadError = true,
                        errorMessage = e.message?.take(200) ?: e.javaClass.simpleName
                    )
                }
            }
        }
    }

    private suspend fun loadImageInternal(uri: Uri) {
        _uiState.update {
            it.copy(
                isProcessing = true,
                loadError = false,
                errorMessage = null,
                noHairDetected = false
            )
        }

        val loadResult = kotlinx.coroutines.withContext(Dispatchers.IO) {
            BitmapUtils.loadOrientedBitmap(getApplication(), uri, maxDimension = 1280)
        }
        val loadedBitmap = loadResult.bitmap

        if (loadedBitmap == null) {
            cachedMask = null
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    loadError = true,
                    // Diagnostic reason code (e.g. "diag: decode-null") shown in the red
                    // banner so the user can paste it back and we learn WHY the load failed.
                    errorMessage = "diag: ${loadResult.reason ?: "unknown"}"
                )
            }
            return
        }

        val (maskResult, skinAnalysis) = kotlinx.coroutines.withContext(Dispatchers.Default) {
            val mask = segmentationEngine.segmentHair(loadedBitmap)
            val skin = segmentationEngine.analyzeSkinTone(loadedBitmap)
            mask to skin
        }

        when (maskResult) {
            is HairMaskResult.Success -> {
                cachedMask = maskResult.mask
                val snapshot = _uiState.value
                val recolored = kotlinx.coroutines.withContext(Dispatchers.Default) {
                    HairRecolorProcessor.recolorHair(
                        source = loadedBitmap,
                        mask = maskResult.mask,
                        targetColorInt = snapshot.selectedColor.colorInt,
                        intensity = snapshot.intensity
                    )
                }
                // Owned copies: bitmaps handed to Compose must never share a pixel buffer
                // with any pipeline intermediate (v1.3 recycled-bitmap crash).
                val originalCopy = ownedCopy(loadedBitmap)
                val recoloredCopy = ownedCopy(recolored)
                _uiState.update {
                    it.copy(
                        originalBitmap = originalCopy,
                        recoloredBitmap = recoloredCopy,
                        hasMask = true,
                        noHairDetected = false,
                        isProcessing = false,
                        errorMessage = null,
                        skinAnalysis = skinAnalysis
                    )
                }
            }

            HairMaskResult.NoHairDetected -> {
                cachedMask = null
                val originalCopy = ownedCopy(loadedBitmap)
                _uiState.update {
                    it.copy(
                        originalBitmap = originalCopy,
                        recoloredBitmap = originalCopy,
                        hasMask = false,
                        noHairDetected = true,
                        isProcessing = false,
                        errorMessage = null,
                        skinAnalysis = skinAnalysis
                    )
                }
            }

            is HairMaskResult.Error -> {
                cachedMask = null
                val originalCopy = ownedCopy(loadedBitmap)
                _uiState.update {
                    it.copy(
                        originalBitmap = originalCopy,
                        recoloredBitmap = originalCopy,
                        hasMask = false,
                        noHairDetected = false,
                        isProcessing = false,
                        errorMessage = maskResult.message,
                        skinAnalysis = skinAnalysis
                    )
                }
            }
        }
    }

    /** Returns an owned copy with its own pixel buffer, so no pipeline step can
     * invalidate the bitmap while Compose is drawing it. */
    private fun ownedCopy(bitmap: Bitmap): Bitmap =
        bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap

    fun selectColor(color: HairColor) {
        _uiState.update { it.copy(selectedColor = color) }
        reapplyColor()
    }

    fun setIntensity(value: Float) {
        _uiState.update { it.copy(intensity = value.coerceIn(0.1f, 1f)) }
        reapplyColor()
    }

    private fun reapplyColor() {
        val original = _uiState.value.originalBitmap ?: return
        val mask = cachedMask ?: return
        val selectedColor = _uiState.value.selectedColor
        val intensity = _uiState.value.intensity

        recolorJob?.cancel()
        recolorJob = viewModelScope.launch(Dispatchers.Default) {
            val recolored = HairRecolorProcessor.recolorHair(
                source = original,
                mask = mask,
                targetColorInt = selectedColor.colorInt,
                intensity = intensity
            )
            _uiState.update { it.copy(recoloredBitmap = recolored) }
        }
    }

    fun saveRecoloredPng() {
        val snapshot = _uiState.value
        val bitmapToSave = if (snapshot.hasMask) {
            snapshot.recoloredBitmap
        } else {
            snapshot.originalBitmap
        } ?: return

        if (snapshot.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val success = MediaStoreSaver.savePngToPictures(
                context = getApplication(),
                bitmap = bitmapToSave,
                prefix = "PhotoStudioHair"
            )
            _uiState.update { it.copy(isSaving = false) }
            _events.emit(PhotoStudioEvent.SaveCompleted(success))
        }
    }

    override fun onCleared() {
        super.onCleared()
        segmentationEngine.close()
    }
}
