package com.example

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.ml.HairRecolorProcessor
import com.example.model.HairPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun readAppNameFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AI Hair Color Studio", appName)
    }

    @Test
    fun localizedStringsExistInBothEnglishAndPersian() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()

        val enConfig = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        val enContext = baseContext.createConfigurationContext(enConfig)

        val faConfig = Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("fa"))
        }
        val faContext = baseContext.createConfigurationContext(faConfig)

        assertEquals("Live Camera", enContext.getString(R.string.home_live_camera_title))
        assertEquals("دوربین زنده", faContext.getString(R.string.home_live_camera_title))

        HairPalette.allColors.forEach { color ->
            assertEquals(color.nameEn, enContext.getString(color.nameResId))
            assertNotEquals("", faContext.getString(color.nameResId))
        }
    }

    @Test
    fun recolorOnlyModifiesMaskedPixelsAndPreservesUnmaskedSkin() {
        val bitmap = Bitmap.createBitmap(4, 1, Bitmap.Config.ARGB_8888)
        val skinColor = Color.rgb(220, 175, 145)
        val hairColor = Color.rgb(120, 90, 60)
        bitmap.setPixel(0, 0, skinColor)
        bitmap.setPixel(1, 0, hairColor)
        bitmap.setPixel(2, 0, hairColor)
        bitmap.setPixel(3, 0, skinColor)

        val mask = floatArrayOf(0f, 1f, 0.5f, 0f)
        val result = HairRecolorProcessor.recolorHair(
            source = bitmap,
            mask = mask,
            targetColorInt = Color.parseColor("#800020"),
            intensity = 1f
        )

        assertEquals(skinColor, result.getPixel(0, 0))
        assertEquals(skinColor, result.getPixel(3, 0))
        assertNotEquals(hairColor, result.getPixel(1, 0))
        assertNotEquals(hairColor, result.getPixel(2, 0))
    }
}
