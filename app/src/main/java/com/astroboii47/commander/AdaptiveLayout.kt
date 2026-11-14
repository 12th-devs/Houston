package com.astroboii47.commander

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Small-screen guardrails for the overlay.
 *
 * Repeated alpha fixes were for rows, tabs, and action menus clipping on
 * compact phones (360dp width, 640dp height, font-scale 1.3x). Use these
 * helpers instead of hard-coding heights so new screens inherit the same
 * budget.
 */
object AdaptiveLayout {
    /** True on compact phones where action menus previously clipped. */
    @Composable
    fun isCompact(): Boolean {
        val config = LocalConfiguration.current
        return config.screenWidthDp <= 380 || config.screenHeightDp <= 680
    }

    /** Max card height: 70% of screen on regular phones, 62% on compact. */
    @Composable
    fun cardMaxHeight(fractionRegular: Float = 0.70f, fractionCompact: Float = 0.62f): Dp {
        val config = LocalConfiguration.current
        val fraction = if (config.screenWidthDp <= 380 || config.screenHeightDp <= 680) {
            fractionCompact
        } else {
            fractionRegular
        }
        return (config.screenHeightDp * fraction).dp
    }

    /** Visible row count scaled down on compact screens. */
    fun visibleRows(requested: Int, compact: Boolean): Int =
        if (compact) requested.coerceAtMost(5) else requested
}
