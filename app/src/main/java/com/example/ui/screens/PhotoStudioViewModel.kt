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
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    loadError = false,
                    noHairDetected = false
                )
            }

            val loadedBitmap = kotlinx.coroutines.withContext(Dispatchers.IO) {
                BitmapUtils.loadOrientedBitmap(getApplication(), uri, maxDimension = 1280)
            }

            if (loadedBitmap == null) {
                cachedMask = null
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        loadError = true
                    )
                }
                return@launch
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
                    _uiState.update {
                        it.copy(
                            originalBitmap = loadedBitmap,
                            recoloredBitmap = recolored,
                            hasMask = true,
                            noHairDetected = false,
                            isProcessing = false,
                            skinAnalysis = skinAnalysis
                        )
                    }
                }

                HairMaskResult.NoHairDetected -> {
                    cachedMask = null
                    _uiState.update {
                        it.copy(
                            originalBitmap = loadedBitmap,
                            recoloredBitmap = loadedBitmap,
                            hasMask = false,
                            noHairDetected = true,
                            isProcessing = false,
                            skinAnalysis = skinAnalysis
                        )
                    }
                }
            }
        }
    }

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
