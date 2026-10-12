package com.lifetrack.app.ui.all

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifetrack.app.ui.components.ScreenHeader
import com.lifetrack.app.ui.components.ScreenPadding
import com.lifetrack.app.ui.settings.Feature
import com.lifetrack.app.ui.settings.featureLocked
import com.lifetrack.app.ui.theme.Space

/** One way into a section from the All tab. */
data class SectionLink(
    val route: String,
    val label: String,
    val hint: String,
    val icon: ImageVector,
    val accent: Color,
    /** The access-dependent part this section is, if any; greyed out while it is switched off. */
    val feature: Feature? = null
)

/**
 * The second tab: every section as a round button, so the bottom bar can stay at two items
 * and nothing has to fight for a slot in it.
 */
@Composable
fun AllScreen(sections: List<SectionLink>, onOpen: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = Space.xxl),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.xl)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenHeader(title = "All", subtitle = "Where to?")
        }
        items(sections, key = { it.route }) { section ->
            SectionButton(section, onClick = { onOpen(section.route) })
        }
    }
}

@Composable
private fun SectionButton(section: SectionLink, onClick: () -> Unit) {
    // Still opens when switched off: the section itself says what to turn on.
    val locked = section.feature?.let { featureLocked(it) } == true
    val tint = if (locked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else section.accent
    Column(
        Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(vertical = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = if (locked) 0.10f else 0.18f),
                modifier = Modifier.size(84.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(section.icon, contentDescription = null, tint = tint, modifier = Modifier.size(38.dp))
                }
            }
            if (locked) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(28.dp).align(Alignment.BottomEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Lock, contentDescription = "Switched off",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.sm))
        Text(section.label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Text(
            if (locked) "Off - needs access" else section.hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

