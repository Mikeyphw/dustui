# OV04 legacy reachability

## Newly made unreachable as product truth

- UI-side full-list filtering for full-scan search is not allowed for Go-owned features.
- UI-side ad-hoc summary/treemap/growth calculations are not allowed as future production authority.
- Room/legacy history comparison cannot define comparability independently from Go `ScanAuthority` compatibility.

## Still reachable but quarantined

- Existing Kotlin/Room/Python history/search surfaces may remain in the tree while Android is suspended, but only as legacy reference paths.
- Legacy Android explorer routes are not proof that the app is buildable or production-ready.
- Devtool Android build/test/lint may be captured as diagnostic evidence only in OV04.

## Safety invariant

OV04 adds read/query APIs only. It must not make any old or new destructive executor newly reachable.
