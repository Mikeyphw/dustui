# OV13 Devtool wrapper validator hotfix

The original OV13 wrapper/EXO validator proved the static TOML contract but used
a brittle dynamic parser for Devtool wrapper/jobs/plan output. The installed
Devtool shows the expected wrapper commands, target-local jobs, and Gate D plan,
but its human/Rich table output and JSON shape do not match the first parser.

This hotfix keeps the static contract strict and broadens only the dynamic CLI
validation so it accepts either JSON fields or the Rich/text representations
shown by the installed Devtool.
