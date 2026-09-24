# DustUI OV03 Termux fixture portability hotfix

OV03 Gate A scanner tests must run on Termux/Android filesystems that may deny
hard-link creation inside temporary directories. That denial is an environment
capability limitation, not scanner-authority failure.

The hard-link assertion remains active when the filesystem accepts the fixture.
When `os.Link` fails with a permission/invalid-operation error, the test logs the
fixture limitation and continues to assert sparse logical/allocated size truth,
scanner identity, integrity, symlink behavior when supported, and ScanAuthority
capabilities.

This keeps Gate A portable without weakening production scan semantics.
