package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import kotlin.math.max

object BitmapUtils {

    data class BitmapLoadResult(
        val bitmap: Bitmap?,
        /** Short diagnostic reason code when [bitmap] is null (e.g. "decode-null"). */
        val reason: String?
    )

    /**
     * Loads a gallery photo as an upright ARGB_8888 bitmap scaled to at most [maxDimension].
     *
     * Reads the content-URI stream ONCE (some providers fail on repeated opens) and decodes
     * from the same byte array. Prefers ImageDecoder on API 28+ (handles HEIC/WebP) with a
     * BitmapFactory fallback. Never recycles any bitmap: intermediates may share pixel
     * buffers with the returned bitmap, and recycling one would invalidate the other
     * (this caused the v1.3 "Canvas: trying to use a recycled bitmap" crash).
     */
    fun loadOrientedBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1280
    ): BitmapLoadResult {
        val bytes: ByteArray = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (t: Throwable) {
            return BitmapLoadResult(null, "stream-fail: ${t.javaClass.simpleName}")
        } ?: return BitmapLoadResult(null, "stream-null")
        if (bytes.isEmpty()) return BitmapLoadResult(null, "empty-bytes")

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
        val srcWidth = boundsOptions.outWidth
        val srcHeight = boundsOptions.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return BitmapLoadResult(null, "bounds-invalid")

        var sampleSize = 1
        while (max(srcWidth / sampleSize, srcHeight / sampleSize) > maxDimension * 2) {
            sampleSize *= 2
        }

        // Non-null declared type: the Elvis return guarantees decodeBytes() was non-null.
        val decoded: Bitmap = decodeBytes(bytes, sampleSize)
            ?: return BitmapLoadResult(null, "decode-null")

        // EXIF orientation from the same bytes; failure is non-fatal (defaults to normal).
        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
        }

        val oriented = if (!matrix.isIdentity) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else {
            decoded
        }

        val longest = max(oriented.width, oriented.height)
        if (longest <= maxDimension) {
            return if (oriented.config == Bitmap.Config.ARGB_8888) {
                BitmapLoadResult(oriented, null)
            } else {
                BitmapLoadResult(oriented.copy(Bitmap.Config.ARGB_8888, false), null)
            }
        }

        val scale = maxDimension.toFloat() / longest.toFloat()
        val targetW = max(1, (oriented.width * scale).toInt())
        val targetH = max(1, (oriented.height * scale).toInt())
        return BitmapLoadResult(
            Bitmap.createScaledBitmap(oriented, targetW, targetH, true),
            null
        )
    }

    /**
     * Decodes [bytes] to a software ARGB_8888 bitmap. ImageDecoder first on API 28+
     * (HEIC/WebP support), BitmapFactory as fallback. Returns null only if both fail.
     */
    private fun decodeBytes(bytes: ByteArray, sampleSize: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(bytes)
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    val longest = max(info.size.width, info.size.height)
                    if (longest > 2560) {
                        val s = 2560f / longest.toFloat()
                        decoder.setTargetSize(
                            max(1, (info.size.width * s).toInt()),
                            max(1, (info.size.height * s).toInt())
                        )
                    }
                }
            } catch (_: Throwable) {
                // Fall through to BitmapFactory below.
            }
        }
        return try {
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Converts an RGBA_8888 CameraX ImageProxy from the front camera into an upright,
     * horizontally mirrored ARGB_8888 Bitmap scaled to at most [maxDimension].
     *
     * NOTE: no intermediate bitmap is recycled here. Bitmap.createBitmap(src, x, y, w, h)
     * without a matrix can return a subset bitmap SHARING the source's pixel buffer, so
     * recycling the source would invalidate the returned frame and crash Compose's draw
     * pass with "Canvas: trying to use a recycled bitmap". GC reclaims the intermediates.
     */
    fun imageProxyToFrontBitmap(
        imageProxy: ImageProxy,
        maxDimension: Int = 540
    ): Bitmap {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        buffer.rewind()

        val width = imageProxy.width
        val height = imageProxy.height
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val rawBitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        rawBitmap.copyPixelsFromBuffer(buffer)

        val cropped = if (rowPadding > 0) {
            Bitmap.createBitmap(rawBitmap, 0, 0, width, height)
        } else {
            rawBitmap
        }

        val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
        val matrix = Matrix().apply {
            if (rotation != 0f) {
                postRotate(rotation)
            }
            // Mirror horizontally for natural front-facing selfie preview
            postScale(-1f, 1f)
        }

        val rotatedAndMirrored = Bitmap.createBitmap(
            cropped,
            0,
            0,
            cropped.width,
            cropped.height,
            matrix,
            true
        )

        val longest = max(rotatedAndMirrored.width, rotatedAndMirrored.height)
        if (longest <= maxDimension) {
            return rotatedAndMirrored
        }

        val scale = maxDimension.toFloat() / longest.toFloat()
        val targetW = max(1, (rotatedAndMirrored.width * scale).toInt())
        val targetH = max(1, (rotatedAndMirrored.height * scale).toInt())
        return Bitmap.createScaledBitmap(rotatedAndMirrored, targetW, targetH, true)
    }
}
