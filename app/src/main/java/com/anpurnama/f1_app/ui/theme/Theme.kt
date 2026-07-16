package com.anpurnama.f1_app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Dark-only. The app is dark-first by design; no light scheme or dynamic color.
private val F1ColorScheme = darkColorScheme(
    primary = F1Primary,
    onPrimary = OnPrimary,
    secondary = F1Secondary,
    onSecondary = OnSecondary,
    tertiary = F1Tertiary,
    onTertiary = OnTertiary,
    error = FLError,
    onError = OnError,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    outline = Outline,
    outlineVariant = OutlineVariant,
)

// Design corner radii: sm 2 / md 8 / lg 14 (default) / xl 16. The design's
// `full` 28 slot has no M3 Shapes role; use CircleShape at call sites for pills.
private val F1Shapes = Shapes(
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

// Design spacing scale (4–32dp). Use consistently per design do's/don'ts.
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val normal = 16.dp
    val semiLg = 20.dp
    val lg = 24.dp
    val xl = 28.dp
    val xxl = 32.dp
}

@Composable
fun F1appTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = F1ColorScheme,
        typography = Typography,
        shapes = F1Shapes,
        content = content,
    )
}
