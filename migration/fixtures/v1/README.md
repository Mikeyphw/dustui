# DustUI OV01 fixtures

OV01 freezes migration contracts and inventories only. It intentionally does not freeze known-bad production behavior as a permanent oracle.

Fixture policy:

- fixtures added here must describe valid legacy inputs or compatibility examples;
- failure behavior is documented in contracts and tests, not blessed as future engine behavior;
- Go production scan/state/mutation fixtures start in OV02/OV03 as the Go engine gains real authority.
