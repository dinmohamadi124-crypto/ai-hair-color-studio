package com.example.ml

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object HairRecolorProcessor {

    /**
     * Recolors only pixels inside [mask] while preserving original luminance / lightness and strand detail:
     * converts pixel to HSL, replaces Hue & Saturation with the target color's Hue & Saturation,
     * preserves original Lightness, and blends using (maskAlpha * intensity).
     */
    fun recolorHair(
        source: Bitmap,
        mask: FloatArray,
        targetColorInt: Int,
        intensity: Float
    ): Bitmap {
        val width = source.width
        val height = source.height
        val total = width * height
        if (mask.size != total || intensity <= 0.004f) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val clampedIntensity = intensity.coerceIn(0f, 1f)
        val tr = ((targetColorInt ushr 16) and 0xFF) / 255f
        val tg = ((targetColorInt ushr 8) and 0xFF) / 255f
        val tb = (targetColorInt and 0xFF) / 255f

        val targetMax = max(tr, max(tg, tb))
        val targetMin = min(tr, min(tg, tb))
        val targetL = (targetMax + targetMin) * 0.5f
        val targetDelta = targetMax - targetMin

        val targetS: Float
        val targetH: Float
        if (targetDelta < 1e-5f) {
            targetS = 0f
            targetH = 0f
        } else {
            targetS = if (targetL > 0.5f) {
                targetDelta / (2f - targetMax - targetMin)
            } else {
                targetDelta / (targetMax + targetMin)
            }
            var h = when (targetMax) {
                tr -> (tg - tb) / targetDelta + (if (tg < tb) 6f else 0f)
                tg -> (tb - tr) / targetDelta + 2f
                else -> (tr - tg) / targetDelta + 4f
            }
            h /= 6f
            targetH = h
        }

        val pixels = IntArray(total)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in 0 until total) {
            val blend = mask[i] * clampedIntensity
            if (blend <= 0.004f) continue

            val px = pixels[i]
            val a = px ushr 24 and 0xFF
            val r = (px ushr 16 and 0xFF) / 255f
            val g = (px ushr 8 and 0xFF) / 255f
            val b = (px and 0xFF) / 255f

            val origMax = max(r, max(g, b))
            val origMin = min(r, min(g, b))
            val origL = (origMax + origMin) * 0.5f

            val newR: Float
            val newG: Float
            val newB: Float

            if (targetS < 1e-5f) {
                newR = origL
                newG = origL
                newB = origL
            } else {
                val q = if (origL < 0.5f) {
                    origL * (1f + targetS)
                } else {
                    origL + targetS - origL * targetS
                }
                val p = 2f * origL - q
                newR = hueToRgb(p, q, targetH + 1f / 3f)
                newG = hueToRgb(p, q, targetH)
                newB = hueToRgb(p, q, targetH - 1f / 3f)
            }

            val outR = ((r + (newR - r) * blend) * 255f + 0.5f).toInt().coerceIn(0, 255)
            val outG = ((g + (newG - g) * blend) * 255f + 0.5f).toInt().coerceIn(0, 255)
            val outB = ((b + (newB - b) * blend) * 255f + 0.5f).toInt().coerceIn(0, 255)

            pixels[i] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun hueToRgb(p: Float, q: Float, tRaw: Float): Float {
        var t = tRaw
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 0.5f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
}
