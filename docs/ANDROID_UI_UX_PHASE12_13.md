> **AUD-FIX successor-aware:** This is a historical predecessor contract/evidence document. It is not current qualification or acceptance authority. Current truth is generated from `migration/qualification/v1/current_state.json`; current requirements are indexed by `migration/requirements/v2/requirements.json`.

# DustUI Android UI/UX Contract

DustUI is a rooted Android storage workbench. The UI must remain readable on phones before it adds another dial to the cockpit.

## Compact-screen rules

1. Every primary screen must remain usable at 360dp width.
2. Scrollable screens must include enough bottom padding for the final section to clear bottom navigation.
3. Last sections must use readable cards or metric tiles, not tiny trailing `ListItem` rows.
4. Horizontal chip rows must have end padding and must not use long clipped labels.
5. Scan presets must not appear accidentally clipped on phones.
6. Selection counters must be labeled with context, never shown as bare numbers.
7. List and Treemap must be presented as an explicit two-state choice.
8. Top actions must be contextual labels such as Actions, Export, Run, or Options, not a generic Menu label.

## Explorer rules

1. Explorer rows must keep name, path, size, and action access visible at compact width.
2. Current-folder search and whole-scan search must be distinct.
3. Long paths must be selectable and ellipsized only after a useful prefix or nearby label is visible.
4. Treemap must always keep an exact-size list fallback and labels on large blocks.
5. Root metadata must appear in a details sheet, not inside crowded rows.

## Typography rules

1. Use monospace styling only for paths, commands, contexts, and machine values.
2. Body copy must avoid exaggerated letter spacing.
3. Section headers should be short, practical labels.
4. Large summaries should use metric grids instead of tall label/value stacks.
5. Warning and blocker states must not rely on color alone.

## Navigation rules

1. Compact screens use bottom navigation for the five core destinations.
2. Wide screens use the navigation rail.
3. Overflow destinations live behind More only on compact screens.
4. Destination routing must use `DustDestination`, not ad-hoc boolean toggles.
