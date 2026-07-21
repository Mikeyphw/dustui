# Root Storage Explorer and Treemap

This epic promotes the scanned hierarchy into a first-class root storage explorer.

## Explorer

- Keeps navigation bounded to the current root scan.
- Supports breadcrumbs, search, sorting, type/category filters, multi-select and cleanup review.
- Adds live privileged inspection through the fixed RootService interface.
- Reports inode, UID/GID, POSIX permissions, SELinux context, symlink target, mount source, filesystem, read-only state, and bind/overlay indicators.
- Provides a bounded 16 KiB text preview. Binary files are identified and are never decoded as arbitrary text.
- Does not expose arbitrary shell execution or unrestricted file mutation.

## Treemap

- Switches any directory between list and treemap views.
- Uses persisted root scan sizes, not a second traversal.
- Displays up to the 80 largest positive-size children.
- Tapping a rectangle opens the matching directory or file details.
- Includes an exact-size textual legend and accessibility description.
- Uses a deterministic alternating-axis layout covered by unit tests.
