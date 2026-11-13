package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import io.alvaroedo.meshconnect.R
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Every popup menu in the app: the ⋮ overflow, the Nodes sort/filter menu and a chat's
 * long-press menu share one look, the long-press card's (rounded 20 dp, raised, a step up
 * from the surface). The overflow menus used the stock 4 dp corners until 1.1, which read
 * as a different control from the card right beside them.
 */
@Composable
fun MeshDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier.widthIn(min = 200.dp),
        content = content,
    )
}

/** A section title inside a sort/filter menu (Nodes, Map). */
@Composable
fun MenuHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** The tick on a sort/filter menu's selected or switched-on row. */
@Composable
fun SelectedTick() {
    Icon(painterResource(R.drawable.ic_check), null, modifier = Modifier.size(18.dp))
}
