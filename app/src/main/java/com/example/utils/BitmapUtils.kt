package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max

object BitmapUtils {

    fun loadOrientedBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1280
    ): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: return null

        val srcWidth = boundsOptions.outWidth
        val srcHeight = boundsOptions.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        var sampleSize = 1
        while (max(srcWidth / sampleSize, srcHeight / sampleSize) > maxDimension * 2) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

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
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        val longest = max(oriented.width, oriented.height)
        if (longest <= maxDimension) {
            return if (oriented.config == Bitmap.Config.ARGB_8888) {
                oriented
            } else {
                oriented.copy(Bitmap.Config.ARGB_8888, false).also { oriented.recycle() }
            }
        }

        val scale = maxDimension.toFloat() / longest.toFloat()
        val targetW = max(1, (oriented.width * scale).toInt())
        val targetH = max(1, (oriented.height * scale).toInt())
        return Bitmap.createScaledBitmap(oriented, targetW, targetH, true).also {
            if (it !== oriented) oriented.recycle()
        }
    }

    /**
     * Converts an RGBA_8888 CameraX ImageProxy from the front camera into an upright,
     * horizontally mirrored ARGB_8888 Bitmap scaled to at most [maxDimension].
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
            Bitmap.createBitmap(rawBitmap, 0, 0, width, height).also {
                rawBitmap.recycle()
            }
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
        ).also {
            if (it !== cropped) cropped.recycle()
        }

        val longest = max(rotatedAndMirrored.width, rotatedAndMirrored.height)
        if (longest <= maxDimension) {
            return rotatedAndMirrored
        }

        val scale = maxDimension.toFloat() / longest.toFloat()
        val targetW = max(1, (rotatedAndMirrored.width * scale).toInt())
        val targetH = max(1, (rotatedAndMirrored.height * scale).toInt())
        return Bitmap.createScaledBitmap(rotatedAndMirrored, targetW, targetH, true).also {
            if (it !== rotatedAndMirrored) rotatedAndMirrored.recycle()
        }
    }
}
