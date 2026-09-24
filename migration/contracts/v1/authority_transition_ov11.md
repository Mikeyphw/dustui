# DustUI OV11 authority transition

Automation and Activity ownership move to Go under `dustui.automation.activity.v1`. Android WorkManager, Tasker receivers, notification actions, and Compose Activity screens must not decide terminal success locally. They submit or observe operation IDs and render durable terminal truth from Go.

Legacy launch semantics are no longer authoritative: launch, accept, queue, and running are lifecycle states only. The only success/failure/cancel/recovery facts are terminal operation states.
