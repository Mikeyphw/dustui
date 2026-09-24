# OV06 privileged Go runtime protocol

OV06 creates the root/privileged admission boundary before any production destructive execution moves to Go.

The privileged runtime uses the same Go domain implementation but runs behind a separate root namespace:

- protocol: `dustui.privileged.runtime.v1`
- root namespace: `dustui.root.v1`
- runtime identity: `dustui-go-root-runtime`
- kernel protocol must still match `dustui.kernel.v1`

Every privileged request is fail-closed against:

- protocol version;
- strict request ID syntax;
- operation allowlist;
- JSON field allowlist at the top request level and payload level;
- capability class;
- target scope;
- root namespace;
- runtime identity;
- bounded request payload size;
- bounded response frame size;
- bounded event frame size.

Supported OV06 operations are intentionally narrow:

- `root.readiness`
- `root.status`
- `root.reconcile`
- `cleanup.preview`
- `cleanup.execute.dry_run`

`cleanup.execute.dry_run` is a destructive-class admission simulator only. It exists to prove global single-flight, queue exhaustion, durable admission, and reconcile semantics before OV08. It does not permit production mutation.

There is no generic shell operation, shell console, arbitrary command execution, or broad path mutation API.
