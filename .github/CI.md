# Repository CI

Adapted from the merged scout-ios CI and review conventions.

## What runs

| Changed files | Documentation checks | YAML and CI checks |
| --- | --- | --- |
| Markdown only | Run | Skip |
| YAML only | Skip | Run |
| Markdown and YAML | Run | Run |
| Validator, shared detector, or validation workflow | Run when Markdown validation is affected | Run |
| Other files only | Skip | Skip |
| Manual run or missing baseline | Run | Run |

Markdown checks validate UTF-8, merge-conflict markers, and balanced code fences.
YAML checks validate syntax and basic GitHub workflow/job structure, including
YAML outside `.github`. These are not full Actions expression or shell lint checks.
Changes to the Markdown configuration also run its validator.

A small change-detection job and final `Repository validation` status always run.
The final status fails if detection fails or a selected check fails; intentional
skips pass. PRs compare the merge commit to its base. Pushes compare the previous
and new commits. Deleted/renamed paths and unusual filenames are handled.

Workflows run for PRs to `develop`, pushes to `develop`, and manual dispatch.
Actions use immutable pins and read-only permissions. Checkout credentials are
not persisted. Jobs have five-minute timeouts; superseded runs are cancelled.
Dependabot checks GitHub Actions weekly against `develop`, with two open PRs max.
No branch-protection settings or deployment workflows are changed.

## Local validation

From the repository root:

```sh
ruby .github/scripts/validate-markdown.rb
ruby .github/scripts/validate-yaml.rb
python3 -m unittest discover -s .github/tests -v
```

Tests cover filtering, deleted/renamed files, missing baselines, GitHub outputs,
and valid/invalid Markdown and YAML. Review formats and label transitions live
in [AI_REVIEW.md](AI_REVIEW.md). Label updates are manual, not automated.

## Backend coverage

The repository currently has no Java sources, Maven/Gradle manifest, wrapper, or
test suite. This workflow validates documentation and configuration only. A green
`Repository validation` status is not a successful backend build or test run.

The story that adds the backend project must also add its actual build/test CI,
using that project's chosen JDK and wrapper. Run it for source, tests, resources,
build/dependency configuration, and its workflow; skip it for documentation-only
changes. Keep documentation checks independent. Add dependency updates for the
selected build tool then. No iOS, Supabase, or deployment jobs are included here.
