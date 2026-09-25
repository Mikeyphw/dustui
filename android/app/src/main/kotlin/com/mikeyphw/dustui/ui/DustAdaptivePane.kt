package com.mikeyphw.dustui.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun DustAdaptiveMasterPane(
    layout: DustAdaptiveLayoutState,
    selection: DustRouteSelection,
    items: List<DustAdaptiveMasterItem>,
    onNavigate: (DustSemanticRoute, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!layout.showsMasterPane || items.isEmpty()) return
    Surface(
        modifier = modifier
            .width(layout.masterPaneWidthDp.dp)
            .fillMaxHeight()
            .semantics {
                isTraversalGroup = true
                traversalIndex = 1f
                contentDescription = "${selection.domain.label} master pane. Semantic route selection remains unchanged when the window resizes."
            },
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxHeight()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(selection.domain.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    when (layout.paneMode) {
                        DustPaneMode.MasterDetail -> "Master/detail"
                        DustPaneMode.Supporting -> "Supporting pane"
                        DustPaneMode.Single -> "Single pane"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxHeight()) {
                items(items, key = { it.stableKey }) { item ->
                    val current = item.isSelected(selection)
                    ListItem(
                        headlineContent = { Text(item.title, maxLines = 1) },
                        supportingContent = { Text(item.detail, maxLines = 2) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onNavigate(item.route, item.entityId) }
                            .semantics {
                                role = Role.Button
                                selected = current
                                contentDescription = buildString {
                                    append(item.title)
                                    append(". ")
                                    append(item.detail)
                                    if (current) append(". Current detail")
                                }
                            },
                        tonalElevation = if (current) 2.dp else 0.dp,
                    )
                }
            }
        }
    }
}

fun Modifier.dustPrimaryNavigationTraversal(): Modifier = semantics {
    isTraversalGroup = true
    traversalIndex = 0f
}

fun Modifier.dustDetailTraversal(): Modifier = semantics {
    isTraversalGroup = true
    traversalIndex = 2f
}

@Preview(name = "AUD-FIX-12 phone", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun DustAdaptivePhonePreview() {
    DustTheme {
        val selection = DustRouteSelection(DustSemanticRoute.ActivityRun, "op-preview", 7)
        val layout = DustAdaptiveLayoutPolicy.resolve(412f, 915f, selection)
        Surface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${layout.windowClass.wireName} · ${layout.paneMode.name.lowercase()}")
                Text("activity/run · op-preview")
                Text("Compact chrome preserves the same semantic detail identity.")
            }
        }
    }
}

@Preview(name = "AUD-FIX-12 tablet", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun DustAdaptiveTabletPreview() {
    DustTheme {
        val selection = DustRouteSelection(DustSemanticRoute.SystemDiagnostics, generation = 7)
        val layout = DustAdaptiveLayoutPolicy.resolve(1280f, 800f, selection)
        DustAdaptiveMasterPane(
            layout = layout,
            selection = selection,
            items = DustAdaptiveMasterModel.systemItems(selection),
            onNavigate = { _, _ -> },
        )
    }
}
