package cz.audioknihy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cz.audioknihy.R

/** Barvy jednoho vzhledu: knihařské plátno, papírový text, zlatá nit. */
data class Palette(
    val ground: Color,
    val surface: Color,
    val raised: Color,
    val high: Color,
    val highest: Color,
    val thread: Color,
    val outlineVariant: Color,
    val paper: Color,
    val paperDim: Color,
    val gold: Color,
    val goldDeep: Color,
    val onGold: Color,
    val onGoldDeep: Color,
    val error: Color,
    val dark: Boolean,
)

enum class AppTheme(val label: Int, val palette: Palette) {
    CLOTH(
        R.string.theme_cloth,
        Palette(
            ground = Color(0xFF0F1714), surface = Color(0xFF14201C), raised = Color(0xFF1A2823), high = Color(0xFF213029),
            highest = Color(0xFF283A32), thread = Color(0xFF34463E), outlineVariant = Color(0xFF26352F),
            paper = Color(0xFFECE6D8), paperDim = Color(0xFFA9B4AD), gold = Color(0xFFD9B45A), goldDeep = Color(0xFF3A2E10),
            onGold = Color(0xFF1C1505), onGoldDeep = Color(0xFFF3DFA8), error = Color(0xFFE4796A), dark = true,
        ),
    ),
    MIDNIGHT(
        R.string.theme_midnight,
        Palette(
            ground = Color(0xFF000000), surface = Color(0xFF0A0C0F), raised = Color(0xFF111419), high = Color(0xFF181C22),
            highest = Color(0xFF1F242B), thread = Color(0xFF2A3038), outlineVariant = Color(0xFF181C22),
            paper = Color(0xFFD6D1C5), paperDim = Color(0xFF8C9299), gold = Color(0xFFC49C47), goldDeep = Color(0xFF2A210E),
            onGold = Color(0xFF140F03), onGoldDeep = Color(0xFFE6D09A), error = Color(0xFFD9705F), dark = true,
        ),
    ),
    OXBLOOD(
        R.string.theme_oxblood,
        Palette(
            ground = Color(0xFF170C0E), surface = Color(0xFF201215), raised = Color(0xFF2A171B), high = Color(0xFF331D22),
            highest = Color(0xFF3D242A), thread = Color(0xFF4A2D33), outlineVariant = Color(0xFF2E1A1E),
            paper = Color(0xFFEFE5DA), paperDim = Color(0xFFBBA8A4), gold = Color(0xFFE0B866), goldDeep = Color(0xFF3D2B10),
            onGold = Color(0xFF1F1605), onGoldDeep = Color(0xFFF5E0AE), error = Color(0xFFF08A78), dark = true,
        ),
    ),
    NAVY(
        R.string.theme_navy,
        Palette(
            ground = Color(0xFF0C1220), surface = Color(0xFF111A2B), raised = Color(0xFF172236), high = Color(0xFF1E2B42),
            highest = Color(0xFF26354F), thread = Color(0xFF33435F), outlineVariant = Color(0xFF1C2740),
            paper = Color(0xFFE6E8EF), paperDim = Color(0xFFA4ACBD), gold = Color(0xFFE3BC62), goldDeep = Color(0xFF3A2D0F),
            onGold = Color(0xFF1E1604), onGoldDeep = Color(0xFFF5E1AC), error = Color(0xFFEE8878), dark = true,
        ),
    ),
    LINEN(
        R.string.theme_linen,
        Palette(
            ground = Color(0xFFE6E1D5), surface = Color(0xFFEEE9DE), raised = Color(0xFFE0DACC), high = Color(0xFFD8D1C1),
            highest = Color(0xFFCFC7B5), thread = Color(0xFFB3AA96), outlineVariant = Color(0xFFCCC4B2),
            paper = Color(0xFF1E2420), paperDim = Color(0xFF545C56), gold = Color(0xFF7D5F12), goldDeep = Color(0xFFEBD9A6),
            onGold = Color(0xFFFFFFFF), onGoldDeep = Color(0xFF3A2C06), error = Color(0xFFB0352A), dark = false,
        ),
    );

    companion object {
        fun from(name: String?): AppTheme = entries.firstOrNull { it.name == name } ?: CLOTH
    }
}

/** Paleta aktuálního vzhledu (pro kreslení stužek apod.). */
val LocalPalette = staticCompositionLocalOf { AppTheme.CLOTH.palette }

/** Hedvábné barvy stužek; každá kniha má svou, stálou podle identity knihy. */
object Silk {
    val all = listOf(
        Color(0xFFC23B4E), // karmín
        Color(0xFFD9B45A), // zlato
        Color(0xFF3FA197), // tyrkys
        Color(0xFF9A5A8C), // švestka
        Color(0xFF5577C9), // kobalt
        Color(0xFFC9703C), // rez
        Color(0xFF7FA04F), // mech
    )

    fun forBook(bookId: String): Color = all[Math.floorMod(bookId.hashCode(), all.size)]
}

private fun Palette.scheme() = if (dark) darkColorScheme(
    primary = gold, onPrimary = onGold, primaryContainer = goldDeep, onPrimaryContainer = onGoldDeep,
    secondary = paperDim, onSecondary = ground, secondaryContainer = high, onSecondaryContainer = paper,
    tertiary = Silk.all[2], background = ground, onBackground = paper, surface = ground, onSurface = paper,
    surfaceVariant = high, onSurfaceVariant = paperDim, surfaceContainerLowest = ground, surfaceContainerLow = surface,
    surfaceContainer = surface, surfaceContainerHigh = raised, surfaceContainerHighest = high, surfaceBright = highest,
    surfaceDim = ground, outline = thread, outlineVariant = outlineVariant, error = error, onError = ground,
    inverseSurface = paper, inverseOnSurface = ground, inversePrimary = goldDeep, scrim = Color.Black,
) else lightColorScheme(
    primary = gold, onPrimary = onGold, primaryContainer = goldDeep, onPrimaryContainer = onGoldDeep,
    secondary = paperDim, onSecondary = ground, secondaryContainer = high, onSecondaryContainer = paper,
    tertiary = Silk.all[2], background = ground, onBackground = paper, surface = ground, onSurface = paper,
    surfaceVariant = high, onSurfaceVariant = paperDim, surfaceContainerLowest = surface, surfaceContainerLow = surface,
    surfaceContainer = surface, surfaceContainerHigh = raised, surfaceContainerHighest = high, surfaceBright = surface,
    surfaceDim = high, outline = thread, outlineVariant = outlineVariant, error = error, onError = Color.White,
    inverseSurface = paper, inverseOnSurface = ground, inversePrimary = goldDeep, scrim = Color.Black,
)

private val base = Typography()
private val type = base.copy(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun AudioknihyTheme(theme: AppTheme = AppTheme.CLOTH, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides theme.palette) {
        MaterialTheme(colorScheme = theme.palette.scheme(), typography = type, content = content)
    }
}
