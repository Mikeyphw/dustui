# Frozen Python/Textual parity oracle

This directory contains the retired Python/Textual DustUI host implementation retained **only** as a migration and parity oracle during the Go cutover.

It is explicitly non-production and is not a production entry point, not the repository default, not installed by the root install workflow, and not packaged into the supported DustUI host product. `dust-tui` and `dust-space` are now Go-host compatibility commands.

The oracle remains available for historical behavior comparison until the physical-retirement stage (AUD-FIX-16). Tests in this directory may be run explicitly from here when parity investigation requires them; they are not production qualification authority.

`LEGACY_README.md` preserves the predecessor host's historical usage documentation.
