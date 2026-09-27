# DustUI Gate-E qualification reuse v2 overlay archive

This post-Gate-E infrastructure overlay replaces commit-ID validity with canonical source-projection validity for Gate E while preserving Git commit/tree identifiers as provenance.

It is rebased specifically onto `dustui-20260927-023015-518392` **after** `patch_dustui_post_gate_e_closure.py` has been applied. It must fail closed against the pre-closure tree.

## Purpose

- make `source_projection_sha256` the authoritative Gate-E qualification identity;
- preserve Git HEAD/tree only as validation provenance;
- fingerprint the expensive Gate-E execution nodes independently;
- reuse prior executed evidence when the corresponding node fingerprint is unchanged;
- always rerun cheap policy/contract truth before composing a successor qualification;
- refuse reconciliation when any material execution fingerprint changed;
- keep generic execution caching/resume ownership in Devtool rather than duplicating it in DustUI.

## Apply

```bash
devtool -r "$HOME/Code/dustui" --target dustui_android apply overlay \
  /sdcard/Download/dustui_gate_e_qualification_reuse_v2.zip \
  --no-validate --commit "DustUI Gate-E qualification identity and reuse v2"
```

## Operator flow

After this overlay is applied and committed:

```bash
./devtoolw gate-e-plan
./devtoolw gate-e-reconcile
```

For the intended baseline (successful 48-stage Gate-E qualification, then the post-Gate-E closure patch, then this overlay), the plan is expected to classify the tree as `policy-only-requalification-possible`: the expensive execution evidence remains reusable and only policy/seal truth is rerun.

If `gate-e-plan` reports `material-execution-changed`, do **not** force reuse. Run the canonical fresh path instead:

```bash
./devtoolw gate-e-release
./devtoolw aud-fix-17
```

Once v2 qualification evidence is committed, later evidence-only commits keep the same source projection and `gate-e-plan` should report `qualified-projection-unchanged` without requiring another release run or reconcile.
