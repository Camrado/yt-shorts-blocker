package com.example.ytshortsblocker.ui.theme

import androidx.compose.ui.graphics.Color

// Brand — a calm indigo, deliberately not YouTube red so the app reads as "yours", not theirs.
val Indigo80 = Color(0xFFB9C0FF)
val Indigo40 = Color(0xFF4A54C4)
val IndigoContainerDark = Color(0xFF2B3170)
val IndigoContainerLight = Color(0xFFE0E2FF)

val Slate80 = Color(0xFFC6C5D6)
val Slate40 = Color(0xFF5C5B71)

val Coral80 = Color(0xFFFFB4A6)
val Coral40 = Color(0xFF9C4331)

val OnIndigoDark = Color(0xFF1A1B3A)
val TextDark = Color(0xFFE6E6F0)
val CardLightVariant = Color(0xFFEEEDF5)

val SurfaceDark = Color(0xFF121218)
val SurfaceLight = Color(0xFFFBFAFF)
val CardDark = Color(0xFF1D1D26)
val CardLight = Color(0xFFFFFFFF)

/** Budget status colours, used by the usage ring and the stats bars. */
val StatusCalm = Color(0xFF3DBE8B)
val StatusWarn = Color(0xFFE0A83D)
val StatusOver = Color(0xFFE05A4E)

/**
 * Ring/bar colour for how much of the budget is gone: green while there is room, amber as it
 * gets close, red once it is spent.
 */
fun statusColorFor(fraction: Float, exhausted: Boolean): Color = when {
    exhausted -> StatusOver
    fraction >= 0.85f -> StatusWarn
    else -> StatusCalm
}
