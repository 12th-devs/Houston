package com.astroboii47.commander.secondbrain.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Second-brain bottom bar. Same floating translucent geometry (content keeps
 * scrolling behind it with the existing bottom clearance). Icon-only with
 * content descriptions keeps the bar compact; labels stay on the enum for
 * TalkBack and are available via [showLabels].
 *
 * The caller positions this ([androidx.compose.foundation.layout.BoxScope.align]
 * bottom-center); only the bar chrome lives here.
 */
@Composable
fun SbBottomBar(
    selected: SbDestination,
    onSelect: (SbDestination) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = false,
) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 8.dp, bottom = 12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
            ) {
                for (destination in SbDestination.bar) {
                    NavigationBarItem(
                        selected = destination == selected,
                        onClick = { onSelect(destination) },
                        icon = { Icon(destination.icon, destination.label) },
                        label = if (showLabels) {
                            { Text(destination.label) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}
