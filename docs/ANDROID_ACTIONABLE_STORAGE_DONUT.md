# Android actionable storage doughnut

This overlay turns the Overview storage-composition doughnut from a visual summary into a safe file-workbench entry point.

## Interaction model

- Tapping a doughnut segment opens a bottom sheet containing the exact indexed files represented by that slice.
- Tapping an accessible legend row selects or clears the category and updates the doughnut center with category, bytes, percentage, and file count.
- A selected category exposes **View files**, **Select all**, and **Plan deletion**.
- Every file in the category sheet uses the shared actionable path card, so details, Explorer reveal, selection, and single-item deletion planning behave the same as elsewhere in DustUI.

## Aggregate Other behavior

The compact chart keeps the largest named categories visible. Remaining categories are folded into one unique **Other** slice. `ChartSlice.sourceLabels` preserves the exact labels that contributed to the aggregate.

Selecting Other displays each source category with its own byte and file totals. Users can open an individual source category or operate on the complete aggregate. A literal file category named Other is folded into the same aggregate rather than producing duplicate, indistinguishable legend rows.

## Deletion safety

Chart actions do not bypass cleanup policy:

1. category membership is resolved from the active persisted scan;
2. directories are excluded from file-category batch selection;
3. selected paths feed `reviewPaths` and the existing reviewed permanent-deletion pipeline;
4. protected, changed, missing, symlinked, outside-root, or filesystem-crossing paths remain blocked by permanent-deletion review;
5. execution remains bound to the reviewed scan, root, operation, and fingerprints.

After a successful or partial deletion, the persisted scan is stale. The chart remains available for read-only inspection, but category selection and deletion actions are disabled until a new scan refreshes the data.

## Accessibility

The Canvas announces that an accessible category list follows it. Each legend row has a minimum 48dp target and exposes button role, selected state, category, bytes, percentage, and file count. The category sheet provides the same explicit file actions without requiring geometric chart interaction.
