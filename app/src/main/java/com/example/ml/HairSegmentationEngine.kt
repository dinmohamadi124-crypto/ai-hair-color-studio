package com.example.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.example.model.SkinAnalysisResult
import com.example.model.SkinToneRecommender
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import kotlin.math.max
import kotlin.math.min

sealed interface HairMaskResult {
    data class Success(
        val mask: FloatArray,
        val width: Int,
        val height: Int
    ) : HairMaskResult

    data object NoHairDetected : HairMaskResult

    data class Error(val message: String) : HairMaskResult
}

class HairSegmentationEngine(context: Context) {

    private val appContext = context.applicationContext
    private val segmenterLock = Any()
    private val faceDetectorLock = Any()

    @Volatile
    private var imageSegmenter: ImageSegmenter? = null

    @Volatile
    private var faceDetector: FaceDetector? = null

    private fun getOrCreateSegmenter(): ImageSegmenter {
        imageSegmenter?.let { return it }
        synchronized(segmenterLock) {
            imageSegmenter?.let { return it }
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(HAIR_MODEL_ASSET)
                .build()
            val options = ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setOutputConfidenceMasks(true)
                .setOutputCategoryMask(false)
                .build()
            return ImageSegmenter.createFromOptions(appContext, options).also {
                imageSegmenter = it
            }
        }
    }

    private fun getOrCreateFaceDetector(): FaceDetector {
        faceDetector?.let { return it }
        synchronized(faceDetectorLock) {
            faceDetector?.let { return it }
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(FACE_MODEL_ASSET)
                .build()
            val options = FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setMinDetectionConfidence(0.5f)
                .build()
            return FaceDetector.createFromOptions(appContext, options).also {
                faceDetector = it
            }
        }
    }

    fun segmentHair(source: Bitmap): HairMaskResult {
        return try {
            segmentHairInternal(source)
        } catch (t: Throwable) {
            HairMaskResult.Error(shortErrorMessage(t))
        }
    }

    private fun segmentHairInternal(source: Bitmap): HairMaskResult {
        val targetWidth = source.width
        val targetHeight = source.height
        if (targetWidth <= 0 || targetHeight <= 0) return HairMaskResult.NoHairDetected

        val argbSource = if (source.config == Bitmap.Config.ARGB_8888) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, false)
        }

        // Mandatory: run segmentation on a downscaled 256x256 copy
        val scaled256 = Bitmap.createScaledBitmap(argbSource, SEGMENTATION_SIZE, SEGMENTATION_SIZE, true)
        val mpImage = BitmapImageBuilder(scaled256).build()

        val rawMask: FloatArray
        val maskWidth: Int
        val maskHeight: Int

        try {
            val result = synchronized(segmenterLock) {
                getOrCreateSegmenter().segment(mpImage)
            }
            val masksOptional = result.confidenceMasks()
            if (!masksOptional.isPresent) return HairMaskResult.NoHairDetected
            val masks = masksOptional.get()
            try {
                if (masks.isEmpty()) return HairMaskResult.NoHairDetected

                // Official hair_segmentation.tflite outputs [background, hair]
                val hairMaskImage = if (masks.size >= 2) masks[1] else masks[0]
                maskWidth = hairMaskImage.width
                maskHeight = hairMaskImage.height
                val floatBuffer = ByteBufferExtractor.extract(hairMaskImage).asFloatBuffer()
                floatBuffer.rewind()
                rawMask = FloatArray(maskWidth * maskHeight)
                floatBuffer.get(rawMask)
            } finally {
                masks.forEach { it.close() }
            }
        } finally {
            mpImage.close()
            if (scaled256 !== argbSource) {
                scaled256.recycle()
            }
            if (argbSource !== source) {
                argbSource.recycle()
            }
        }

        // Check whether hair is actually present (empty mask check)
        var confidentPixels = 0
        var maxConfidence = 0f
        for (i in rawMask.indices) {
            val v = rawMask[i]
            if (v > maxConfidence) maxConfidence = v
            if (v >= 0.35f) confidentPixels++
        }

        val hairCoverage = confidentPixels.toFloat() / rawMask.size.toFloat()
        if (maxConfidence < 0.40f || hairCoverage < 0.008f) {
            return HairMaskResult.NoHairDetected
        }

        // Clean background/skin noise with smoothstep before upscaling
        for (i in rawMask.indices) {
            rawMask[i] = smoothStep(0.18f, 0.58f, rawMask[i])
        }

        // Upscale mask to target resolution with bilinear filtering
        val upscaled = bilinearUpscale(rawMask, maskWidth, maskHeight, targetWidth, targetHeight)

        // Feather mask edges with Gaussian blur (radius = 2px)
        val feathered = gaussianBlurRadius2(upscaled, targetWidth, targetHeight)

        return HairMaskResult.Success(
            mask = feathered,
            width = targetWidth,
            height = targetHeight
        )
    }

    fun analyzeSkinTone(source: Bitmap): SkinAnalysisResult {
        return try {
            analyzeSkinToneInternal(source)
        } catch (_: Throwable) {
            SkinToneRecommender.fallbackSuggestions()
        }
    }

    private fun analyzeSkinToneInternal(source: Bitmap): SkinAnalysisResult {
        if (source.width <= 0 || source.height <= 0) {
            return SkinToneRecommender.fallbackSuggestions()
        }

        val argbSource = if (source.config == Bitmap.Config.ARGB_8888) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val mpImage = BitmapImageBuilder(argbSource).build()
        try {
            val result = synchronized(faceDetectorLock) {
                getOrCreateFaceDetector().detect(mpImage)
            }
            val detections = result.detections()
            if (detections.isEmpty()) {
                return SkinToneRecommender.fallbackSuggestions()
            }

            val primaryFace = detections.maxByOrNull { detection ->
                val box = detection.boundingBox()
                box.width() * box.height()
            } ?: return SkinToneRecommender.fallbackSuggestions()

            val box = primaryFace.boundingBox()
            val sampledRgb = sampleCheekRegions(argbSource, box)
                ?: return SkinToneRecommender.fallbackSuggestions()

            return SkinToneRecommender.recommendFromCheekRgb(
                avgRed = sampledRgb[0],
                avgGreen = sampledRgb[1],
                avgBlue = sampledRgb[2]
            )
        } catch (_: Throwable) {
            return SkinToneRecommender.fallbackSuggestions()
        } finally {
            mpImage.close()
            if (argbSource !== source) {
                argbSource.recycle()
            }
        }
    }

    private fun sampleCheekRegions(bitmap: Bitmap, box: RectF): FloatArray? {
        val w = bitmap.width
        val h = bitmap.height
        val boxW = box.width()
        val boxH = box.height()
        if (boxW < 12f || boxH < 12f) return null

        val yStart = (box.top + boxH * 0.48f).toInt().coerceIn(0, h - 1)
        val yEnd = (box.top + boxH * 0.68f).toInt().coerceIn(yStart + 1, h)

        val leftXStart = (box.left + boxW * 0.18f).toInt().coerceIn(0, w - 1)
        val leftXEnd = (box.left + boxW * 0.36f).toInt().coerceIn(leftXStart + 1, w)

        val rightXStart = (box.left + boxW * 0.64f).toInt().coerceIn(0, w - 1)
        val rightXEnd = (box.left + boxW * 0.82f).toInt().coerceIn(rightXStart + 1, w)

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0L

        val stepX = max(1, (leftXEnd - leftXStart) / 8)
        val stepY = max(1, (yEnd - yStart) / 8)

        for (y in yStart until yEnd step stepY) {
            for (x in leftXStart until leftXEnd step stepX) {
                val pixel = bitmap.getPixel(x, y)
                sumR += (pixel shr 16) and 0xFF
                sumG += (pixel shr 8) and 0xFF
                sumB += pixel and 0xFF
                count++
            }
            for (x in rightXStart until rightXEnd step stepX) {
                val pixel = bitmap.getPixel(x, y)
                sumR += (pixel shr 16) and 0xFF
                sumG += (pixel shr 8) and 0xFF
                sumB += pixel and 0xFF
                count++
            }
        }

        if (count == 0L) return null
        return floatArrayOf(
            sumR.toFloat() / count,
            sumG.toFloat() / count,
            sumB.toFloat() / count
        )
    }

    private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun bilinearUpscale(
        src: FloatArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int
    ): FloatArray {
        if (srcW == dstW && srcH == dstH) return src.copyOf()

        val dst = FloatArray(dstW * dstH)
        val scaleX = if (dstW > 1) (srcW - 1).toFloat() / (dstW - 1).toFloat() else 0f
        val scaleY = if (dstH > 1) (srcH - 1).toFloat() / (dstH - 1).toFloat() else 0f

        for (y in 0 until dstH) {
            val fy = y * scaleY
            val y0 = fy.toInt().coerceIn(0, srcH - 1)
            val y1 = min(y0 + 1, srcH - 1)
            val wy = fy - y0
            val row0 = y0 * srcW
            val row1 = y1 * srcW
            val dstRow = y * dstW

            for (x in 0 until dstW) {
                val fx = x * scaleX
                val x0 = fx.toInt().coerceIn(0, srcW - 1)
                val x1 = min(x0 + 1, srcW - 1)
                val wx = fx - x0

                val v00 = src[row0 + x0]
                val v10 = src[row0 + x1]
                val v01 = src[row1 + x0]
                val v11 = src[row1 + x1]

                val top = v00 + (v10 - v00) * wx
                val bottom = v01 + (v11 - v01) * wx
                dst[dstRow + x] = top + (bottom - top) * wy
            }
        }
        return dst
    }

    private fun gaussianBlurRadius2(src: FloatArray, width: Int, height: Int): FloatArray {
        val temp = FloatArray(width * height)
        val dst = FloatArray(width * height)

        // 5-tap separable Gaussian kernel (radius = 2px, sigma ~ 1.2)
        val k0 = 0.06136f
        val k1 = 0.24477f
        val k2 = 0.38774f

        // Horizontal pass
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val xM2 = max(0, x - 2)
                val xM1 = max(0, x - 1)
                val xP1 = min(width - 1, x + 1)
                val xP2 = min(width - 1, x + 2)
                temp[row + x] =
                    src[row + xM2] * k0 +
                    src[row + xM1] * k1 +
                    src[row + x] * k2 +
                    src[row + xP1] * k1 +
                    src[row + xP2] * k0
            }
        }

        // Vertical pass
        for (y in 0 until height) {
            val yM2 = max(0, y - 2) * width
            val yM1 = max(0, y - 1) * width
            val y0 = y * width
            val yP1 = min(height - 1, y + 1) * width
            val yP2 = min(height - 1, y + 2) * width

            for (x in 0 until width) {
                dst[y0 + x] = (
                    temp[yM2 + x] * k0 +
                    temp[yM1 + x] * k1 +
                    temp[y0 + x] * k2 +
                    temp[yP1 + x] * k1 +
                    temp[yP2 + x] * k0
                ).coerceIn(0f, 1f)
            }
        }

        return dst
    }

    private fun shortErrorMessage(t: Throwable): String {
        val name = t.javaClass.simpleName
        val detail = t.message?.take(160)?.ifBlank { null }
        return (if (detail == null) name else "$name: $detail").take(200)
    }

    fun close() {
        synchronized(segmenterLock) {
            imageSegmenter?.close()
            imageSegmenter = null
        }
        synchronized(faceDetectorLock) {
            faceDetector?.close()
            faceDetector = null
        }
    }

    companion object {
        private const val SEGMENTATION_SIZE = 256
        private const val HAIR_MODEL_ASSET = "hair_segmentation.tflite"
        private const val FACE_MODEL_ASSET = "blaze_face_short_range.tflite"
    }
}
