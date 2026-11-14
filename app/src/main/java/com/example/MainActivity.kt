package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.LocaleListCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.AppLanguage
import com.example.data.LanguagePreferences
import com.example.ui.navigation.AppRoute
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LiveCameraScreen
import com.example.ui.screens.PhotoStudioScreen
import com.example.ui.theme.AIHairColorStudioTheme
import com.example.utils.CrashReporter
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

    private lateinit var languagePreferences: LanguagePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashReporter.install(applicationContext)
        languagePreferences = LanguagePreferences(applicationContext)
        applyPersistedOrDefaultLocale()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val activeLocale = LocalConfiguration.current.locales[0]
            val isPersian = activeLocale?.language.equals("fa", ignoreCase = true)
            val activeLanguage = if (isPersian) AppLanguage.PERSIAN else AppLanguage.ENGLISH
            val coroutineScope = rememberCoroutineScope()

            AIHairColorStudioTheme(isPersian = isPersian) {
                AIHairColorStudioNavHost(
                    activeLanguage = activeLanguage,
                    onSelectLanguage = { selectedLanguage ->
                        coroutineScope.launch {
                            languagePreferences.saveLanguage(selectedLanguage)
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(selectedLanguage.tag)
                            )
                        }
                    }
                )
            }
        }
    }

    private fun applyPersistedOrDefaultLocale() {
        val savedLanguage = runBlocking { languagePreferences.getSavedLanguage() }
        val targetLanguage = savedLanguage ?: AppLanguage.resolveDeviceDefault()
        val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val currentResolved = AppLanguage.fromTag(currentTags)
        if (currentResolved != targetLanguage) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(targetLanguage.tag)
            )
        }
    }
}

@Composable
private fun AIHairColorStudioNavHost(
    activeLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoute.Home,
        enterTransition = { fadeIn(animationSpec = tween(220)) },
        exitTransition = { fadeOut(animationSpec = tween(180)) }
    ) {
        composable<AppRoute.Home> {
            HomeScreen(
                activeLanguage = activeLanguage,
                onSelectLanguage = onSelectLanguage,
                onOpenLiveCamera = { navController.navigate(AppRoute.LiveCamera) },
                onOpenPhotoStudio = { navController.navigate(AppRoute.PhotoStudio) }
            )
        }

        composable<AppRoute.LiveCamera> {
            LiveCameraScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<AppRoute.PhotoStudio> {
            PhotoStudioScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
