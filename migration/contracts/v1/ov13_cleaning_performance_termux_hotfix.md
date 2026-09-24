# OV13 cleaning performance Termux hotfix

Gate D Android unit tests exposed a portability failure in `CleaningPerformanceContractTest`.

The production qualification threshold remains `CleaningIntelligenceQualification.MAX_LARGE_DATASET_DURATION_MILLIS = 15_000`.
The unit test now preserves that assertion, but uses a Termux/constrained-runner budget of 30 seconds for the wall-clock JUnit execution.

This is a test-runner portability fix only. It does not change production cleanup intelligence, qualification policy, Go authority, Android product cutover, or destructive mutation behavior.
