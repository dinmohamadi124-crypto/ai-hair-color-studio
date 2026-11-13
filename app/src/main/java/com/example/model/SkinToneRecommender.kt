package com.example.model

import androidx.annotation.StringRes
import com.example.R

enum class SkinDepth {
    FAIR,
    MEDIUM,
    DEEP
}

enum class SkinUndertone {
    WARM,
    COOL,
    NEUTRAL
}

data class ColorSuggestion(
    val hairColor: HairColor,
    @StringRes val reasonResId: Int
)

data class SkinAnalysisResult(
    val isFaceDetected: Boolean,
    @StringRes val skinToneLabelResId: Int,
    val suggestions: List<ColorSuggestion>
)

object SkinToneRecommender {

    fun fallbackSuggestions(): SkinAnalysisResult {
        return SkinAnalysisResult(
            isFaceDetected = false,
            skinToneLabelResId = R.string.skin_fallback_label,
            suggestions = listOf(
                ColorSuggestion(
                    hairColor = HairPalette.findById("chocolate_brown"),
                    reasonResId = R.string.reason_fallback_1
                ),
                ColorSuggestion(
                    hairColor = HairPalette.findById("chestnut"),
                    reasonResId = R.string.reason_fallback_2
                ),
                ColorSuggestion(
                    hairColor = HairPalette.findById("honey_blonde"),
                    reasonResId = R.string.reason_fallback_3
                )
            )
        )
    }

    fun recommendFromCheekRgb(avgRed: Float, avgGreen: Float, avgBlue: Float): SkinAnalysisResult {
        val r = (avgRed / 255f).coerceIn(0f, 1f)
        val g = (avgGreen / 255f).coerceIn(0f, 1f)
        val b = (avgBlue / 255f).coerceIn(0f, 1f)

        val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val depth = when {
            luminance > 0.62f -> SkinDepth.FAIR
            luminance > 0.38f -> SkinDepth.MEDIUM
            else -> SkinDepth.DEEP
        }

        val redMinusBlue = r - b
        val greenMinusBlue = g - b
        val redMinusGreen = r - g

        val undertone = when {
            greenMinusBlue > 0.11f && redMinusBlue > 0.16f && greenMinusBlue >= redMinusGreen * 0.85f -> SkinUndertone.WARM
            redMinusBlue < 0.10f || redMinusGreen > greenMinusBlue * 1.45f -> SkinUndertone.COOL
            else -> SkinUndertone.NEUTRAL
        }

        val labelResId = when (depth to undertone) {
            SkinDepth.FAIR to SkinUndertone.WARM -> R.string.skin_fair_warm
            SkinDepth.FAIR to SkinUndertone.COOL -> R.string.skin_fair_cool
            SkinDepth.FAIR to SkinUndertone.NEUTRAL -> R.string.skin_fair_neutral
            SkinDepth.MEDIUM to SkinUndertone.WARM -> R.string.skin_medium_warm
            SkinDepth.MEDIUM to SkinUndertone.COOL -> R.string.skin_medium_cool
            SkinDepth.MEDIUM to SkinUndertone.NEUTRAL -> R.string.skin_medium_neutral
            SkinDepth.DEEP to SkinUndertone.WARM -> R.string.skin_deep_warm
            SkinDepth.DEEP to SkinUndertone.COOL -> R.string.skin_deep_cool
            else -> R.string.skin_deep_neutral
        }

        val suggestions = when (depth to undertone) {
            SkinDepth.FAIR to SkinUndertone.WARM -> listOf(
                ColorSuggestion(HairPalette.findById("copper"), R.string.reason_fair_warm_1),
                ColorSuggestion(HairPalette.findById("honey_blonde"), R.string.reason_fair_warm_2),
                ColorSuggestion(HairPalette.findById("caramel"), R.string.reason_fair_warm_3)
            )
            SkinDepth.FAIR to SkinUndertone.COOL -> listOf(
                ColorSuggestion(HairPalette.findById("ash_blonde"), R.string.reason_fair_cool_1),
                ColorSuggestion(HairPalette.findById("rose_gold"), R.string.reason_fair_cool_2),
                ColorSuggestion(HairPalette.findById("platinum_blonde"), R.string.reason_fair_cool_3)
            )
            SkinDepth.FAIR to SkinUndertone.NEUTRAL -> listOf(
                ColorSuggestion(HairPalette.findById("light_brown"), R.string.reason_fair_neutral_1),
                ColorSuggestion(HairPalette.findById("golden_blonde"), R.string.reason_fair_neutral_2),
                ColorSuggestion(HairPalette.findById("auburn"), R.string.reason_fair_neutral_3)
            )
            SkinDepth.MEDIUM to SkinUndertone.WARM -> listOf(
                ColorSuggestion(HairPalette.findById("caramel"), R.string.reason_medium_warm_1),
                ColorSuggestion(HairPalette.findById("chestnut"), R.string.reason_medium_warm_2),
                ColorSuggestion(HairPalette.findById("golden_blonde"), R.string.reason_medium_warm_3)
            )
            SkinDepth.MEDIUM to SkinUndertone.COOL -> listOf(
                ColorSuggestion(HairPalette.findById("burgundy"), R.string.reason_medium_cool_1),
                ColorSuggestion(HairPalette.findById("dark_brown"), R.string.reason_medium_cool_2),
                ColorSuggestion(HairPalette.findById("ash_grey"), R.string.reason_medium_cool_3)
            )
            SkinDepth.MEDIUM to SkinUndertone.NEUTRAL -> listOf(
                ColorSuggestion(HairPalette.findById("chocolate_brown"), R.string.reason_medium_neutral_1),
                ColorSuggestion(HairPalette.findById("copper"), R.string.reason_medium_neutral_2),
                ColorSuggestion(HairPalette.findById("honey_blonde"), R.string.reason_medium_neutral_3)
            )
            SkinDepth.DEEP to SkinUndertone.WARM -> listOf(
                ColorSuggestion(HairPalette.findById("mahogany"), R.string.reason_deep_warm_1),
                ColorSuggestion(HairPalette.findById("chocolate_brown"), R.string.reason_deep_warm_2),
                ColorSuggestion(HairPalette.findById("auburn"), R.string.reason_deep_warm_3)
            )
            SkinDepth.DEEP to SkinUndertone.COOL -> listOf(
                ColorSuggestion(HairPalette.findById("jet_black"), R.string.reason_deep_cool_1),
                ColorSuggestion(HairPalette.findById("blue_black"), R.string.reason_deep_cool_2),
                ColorSuggestion(HairPalette.findById("cherry_red"), R.string.reason_deep_cool_3)
            )
            else -> listOf(
                ColorSuggestion(HairPalette.findById("soft_black"), R.string.reason_deep_neutral_1),
                ColorSuggestion(HairPalette.findById("burgundy"), R.string.reason_deep_neutral_2),
                ColorSuggestion(HairPalette.findById("chestnut"), R.string.reason_deep_neutral_3)
            )
        }

        return SkinAnalysisResult(
            isFaceDetected = true,
            skinToneLabelResId = labelResId,
            suggestions = suggestions
        )
    }
}
