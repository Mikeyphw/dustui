# OV06 root boundary and reconcile contract

The OV06 root boundary is bounded by design:

- bounded worker count;
- bounded queue depth;
- globally single-flight destructive class;
- explicit admission rejection for queue exhaustion;
- typed rejection for unknown operation, unknown field, bad capability, bad scope, bad namespace, bad runtime identity, and oversized frames;
- durable status lookup by operation ID;
- lost transport moves the operation to `reconcile_required`;
- process/root-runtime restart forbids blind replay and returns `restart_blocked_no_replay`;
- boot identity and process identity are recorded with each admission.

A caller that loses Binder/session transport must perform durable lookup/reconcile before taking another action. Restart is never interpreted as permission to replay a root operation.

OV06 does not make Go the production destructive authority. Mutation cutover remains scheduled for OV08 / Gate C.
