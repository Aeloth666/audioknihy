package cz.audioknihy.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import cz.audioknihy.R

/** Rozvržení aplikace; každé jde kombinovat s libovolným barevným vzhledem. */
enum class AppDesign(val label: Int, val description: Int) {
    RIBBON(R.string.design_ribbon, R.string.design_ribbon_desc),
    SHELF(R.string.design_shelf, R.string.design_shelf_desc),
    COMPACT(R.string.design_compact, R.string.design_compact_desc);

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: RIBBON
    }
}

val LocalDesign = staticCompositionLocalOf { AppDesign.RIBBON }
