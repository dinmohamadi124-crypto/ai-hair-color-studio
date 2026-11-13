package com.example

import com.example.data.AppLanguage
import com.example.model.HairPalette
import com.example.model.SkinToneRecommender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class HairPaletteAndRecommenderTest {

    @Test
    fun paletteContainsExact30ColorsWithEnglishNames() {
        assertEquals(30, HairPalette.allColors.size)
        val jetBlack = HairPalette.findById("jet_black")
        assertEquals("Jet Black", jetBlack.nameEn)
        assertEquals("#1A1A1A", jetBlack.hex)
        val fireOrange = HairPalette.findById("fire_orange")
        assertEquals("Fire Orange", fireOrange.nameEn)
        assertEquals("#FF5F1F", fireOrange.hex)
    }

    @Test
    fun fallbackRecommenderReturns3PopularNaturalShades() {
        val fallback = SkinToneRecommender.fallbackSuggestions()
        assertFalse(fallback.isFaceDetected)
        assertEquals(3, fallback.suggestions.size)
        assertEquals("chocolate_brown", fallback.suggestions[0].hairColor.id)
    }

    @Test
    fun skinToneRecommenderDetectsWarmMediumAndReturns3Suggestions() {
        val result = SkinToneRecommender.recommendFromCheekRgb(
            avgRed = 210f,
            avgGreen = 165f,
            avgBlue = 120f
        )
        assertTrue(result.isFaceDetected)
        assertEquals(3, result.suggestions.size)
    }

    @Test
    fun deviceLocaleDefaultResolvesPersianForFaAndEnglishOtherwise() {
        assertEquals(AppLanguage.PERSIAN, AppLanguage.resolveDeviceDefault(Locale.forLanguageTag("fa")))
        assertEquals(AppLanguage.PERSIAN, AppLanguage.resolveDeviceDefault(Locale.forLanguageTag("fa-IR")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolveDeviceDefault(Locale.US))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolveDeviceDefault(Locale.FRENCH))
    }
}
