package io.music_assistant.client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ---- Light ----
val primaryLight = Color(0xFF00658E)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFCBE6FF)
val onPrimaryContainerLight = Color(0xFF001E2E)
val secondaryLight = Color(0xFF4E616D)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFD2E5F4)
val onSecondaryContainerLight = Color(0xFF0B1D28)
val tertiaryLight = Color(0xFF615A75)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFE7DEFF)
val onTertiaryContainerLight = Color(0xFF1D1730)
val errorLight = Color(0xFFBA1A1A)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFDAD6)
val onErrorContainerLight = Color(0xFF410002)
val backgroundLight = Color(0xFFF6FAFE)
val onBackgroundLight = Color(0xFF171C1F)
val surfaceLight = Color(0xFFF6FAFE)
val onSurfaceLight = Color(0xFF171C1F)
val surfaceVariantLight = Color(0xFFDCE3E9)
val onSurfaceVariantLight = Color(0xFF41474D)
val outlineLight = Color(0xFF71787E)
val outlineVariantLight = Color(0xFFC0C7CD)
val scrimLight = Color(0xFF000000)
val inverseSurfaceLight = Color(0xFF2C3134)
val inverseOnSurfaceLight = Color(0xFFEDF1F5)
val inversePrimaryLight = Color(0xFF8DCDFF)
val surfaceDimLight = Color(0xFFD7DADF)
val surfaceBrightLight = Color(0xFFF6FAFE)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFF1F4F8)
val surfaceContainerLight = Color(0xFFEBEEF3)
val surfaceContainerHighLight = Color(0xFFE5E9ED)
val surfaceContainerHighestLight = Color(0xFFDFE3E7)

// ---- Dark (Openstream OLED Near-Black Retheme) ----
val primaryDark = Color(0xFFFFFFFF)
val onPrimaryDark = Color(0xFF09090B)
val primaryContainerDark = Color(0xFF27272A)
val onPrimaryContainerDark = Color(0xFFF4F4F5)
val secondaryDark = Color(0xFFA1A1AA)
val onSecondaryDark = Color(0xFF09090B)
val secondaryContainerDark = Color(0xFF18181B)
val onSecondaryContainerDark = Color(0xFFE4E4E7)
val tertiaryDark = Color(0xFFF43F5E)
val onTertiaryDark = Color(0xFFFFFFFF)
val tertiaryContainerDark = Color(0xFF4C0519)
val onTertiaryContainerDark = Color(0xFFFFE4E6)
val errorDark = Color(0xFFEF4444)
val onErrorDark = Color(0xFF09090B)
val errorContainerDark = Color(0xFF7F1D1D)
val onErrorContainerDark = Color(0xFFFEE2E2)
val backgroundDark = Color(0xFF09090B)
val onBackgroundDark = Color(0xFFF4F4F5)
val surfaceDark = Color(0xFF09090B)
val onSurfaceDark = Color(0xFFF4F4F5)
val surfaceVariantDark = Color(0xFF18181B)
val onSurfaceVariantDark = Color(0xFFA1A1AA)
val outlineDark = Color(0xFF27272A)
val outlineVariantDark = Color(0xFF18181B)
val scrimDark = Color(0xFF000000)
val inverseSurfaceDark = Color(0xFFF4F4F5)
val inverseOnSurfaceDark = Color(0xFF18181B)
val inversePrimaryDark = Color(0xFF18181B)
val surfaceDimDark = Color(0xFF09090B)
val surfaceBrightDark = Color(0xFF27272A)
val surfaceContainerLowestDark = Color(0xFF000000)
val surfaceContainerLowDark = Color(0xFF121214)
val surfaceContainerDark = Color(0xFF141416)
val surfaceContainerHighDark = Color(0xFF18181B)
val surfaceContainerHighestDark = Color(0xFF222226)

/**
 * Accent used for the "favorite" heart indicator across the app.
 * Bound to the [androidx.compose.material3.ColorScheme.tertiary] role so it harmonizes with
 * the scheme and tracks light/dark automatically.
 */
val favoriteTint: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.tertiary
