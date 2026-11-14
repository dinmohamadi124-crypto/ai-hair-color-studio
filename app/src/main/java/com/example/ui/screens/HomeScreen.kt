package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppLanguage
import com.example.ui.theme.VazirmatnFontFamily
import com.example.utils.CrashReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    activeLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onOpenLiveCamera: () -> Unit,
    onOpenPhotoStudio: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var crashInfo by remember { mutableStateOf<CrashReporter.CrashInfo?>(null) }

    // Show the previous run's crash report (if any) right after launch.
    LaunchedEffect(Unit) {
        crashInfo = withContext(Dispatchers.IO) { CrashReporter.readCrashInfo(context) }
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF150C1D),
            MaterialTheme.colorScheme.background,
            Color(0xFF0A060E)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 620.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top Bar with In-App Language Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.language_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LanguageSegmentedToggle(
                    activeLanguage = activeLanguage,
                    onSelectLanguage = onSelectLanguage
                )
            }

            // Editorial Hero Banner Card
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(216.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_salon),
                        contentDescription = stringResource(R.string.app_name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x330E0913),
                                        Color(0xB80E0913),
                                        Color(0xF5140D1B)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.home_subtitle),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.home_tagline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Primary Action 1: Live Camera
            StudioModeCard(
                title = stringResource(R.string.home_live_camera_title),
                description = stringResource(R.string.home_live_camera_desc),
                icon = Icons.Rounded.CameraAlt,
                isPrimaryAccent = true,
                onClick = onOpenLiveCamera,
                modifier = Modifier.testTag("home_live_camera_button")
            )

            // Primary Action 2: Photo Studio
            StudioModeCard(
                title = stringResource(R.string.home_photo_studio_title),
                description = stringResource(R.string.home_photo_studio_desc),
                icon = Icons.Rounded.PhotoLibrary,
                isPrimaryAccent = false,
                onClick = onOpenPhotoStudio,
                modifier = Modifier.testTag("home_photo_studio_button")
            )

            // Feature Highlights
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StudioFeatureBadge(
                    icon = Icons.Rounded.Palette,
                    label = stringResource(R.string.home_badge_colors)
                )
                StudioFeatureBadge(
                    icon = Icons.Rounded.AutoAwesome,
                    label = stringResource(R.string.home_badge_skin)
                )
                StudioFeatureBadge(
                    icon = Icons.Rounded.Lock,
                    label = stringResource(R.string.home_badge_offline)
                )
            }
        }

        crashInfo?.let { info ->
            CrashReportDialog(
                info = info,
                onCopy = {
                    info.reportText?.let { text ->
                        clipboardManager.setText(AnnotatedString(text))
                        Toast.makeText(
                            context,
                            context.getString(R.string.crash_report_copied_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onClose = {
                    crashInfo = null
                    CrashReporter.deleteCrashReport(context)
                    CrashReporter.clearLastScreen(context)
                }
            )
        }
    }
}

@Composable
private fun CrashReportDialog(
    info: CrashReporter.CrashInfo,
    onCopy: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(text = stringResource(R.string.crash_dialog_title)) },
        text = {
            if (info.reportText != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = info.reportText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                Text(text = stringResource(R.string.crash_native_hint))
            }
        },
        confirmButton = {
            if (info.reportText != null) {
                TextButton(onClick = onCopy) {
                    Text(text = stringResource(R.string.crash_dialog_copy))
                }
            } else {
                TextButton(onClick = onClose) {
                    Text(text = stringResource(R.string.crash_dialog_close))
                }
            }
        },
        dismissButton = if (info.reportText != null) {
            {
                TextButton(onClick = onClose) {
                    Text(text = stringResource(R.string.crash_dialog_close))
                }
            }
        } else {
            null
        }
    )
}

@Composable
private fun LanguageSegmentedToggle(
    activeLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LanguageSegmentButton(
                label = stringResource(R.string.language_persian),
                selected = activeLanguage == AppLanguage.PERSIAN,
                fontFamily = VazirmatnFontFamily,
                onClick = { onSelectLanguage(AppLanguage.PERSIAN) },
                modifier = Modifier.testTag("language_toggle_fa")
            )
            LanguageSegmentButton(
                label = stringResource(R.string.language_english),
                selected = activeLanguage == AppLanguage.ENGLISH,
                fontFamily = FontFamily.Default,
                onClick = { onSelectLanguage(AppLanguage.ENGLISH) },
                modifier = Modifier.testTag("language_toggle_en")
            )
        }
    }
}

@Composable
private fun LanguageSegmentButton(
    label: String,
    selected: Boolean,
    fontFamily: FontFamily,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                }
            )
            .clickable(enabled = !selected, onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = fontFamily,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun StudioModeCard(
    title: String,
    description: String,
    icon: ImageVector,
    isPrimaryAccent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerBrush = if (isPrimaryAccent) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF4F2B24),
                Color(0xFF3A1F2B)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF281B36),
                Color(0xFF1D1428)
            )
        )
    }

    val borderColor = if (isPrimaryAccent) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = BorderStroke(1.2.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .semantics(mergeDescendants = true) {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerBrush)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPrimaryAccent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isPrimaryAccent) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun StudioFeatureBadge(
    icon: ImageVector,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
