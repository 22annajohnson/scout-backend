# Repository CI

PRs to `develop`, pushes to `develop`, and manual runs use one required status:
`Repository validation`. It fails when detection or any selected check fails.
Intentional skips pass; failed/cancelled required jobs do not.

| Changed files | Documentation | YAML / CI | Java build / tests |
| --- | --- | --- | --- |
| Markdown only | Run | Skip | Skip |
| Java, Maven wrapper, pom.xml, source resources | Skip | Run if YAML | Run |
| YAML outside Java resources | Skip | Run | Skip |
| Mixed docs and Java | Run | As needed | Run |
| Shared detector or validation workflow | Run | Run | Run |
| CI guard tests | Skip | Run | Run |
| Other files | Skip | As needed | Skip |
| Manual run or unknown baseline | Run | Run | Run |

Markdown checks validate UTF-8, conflict markers and balanced fences. YAML checks
validate syntax and basic workflow shape, not every Actions expression. Validator
and configuration changes run their corresponding checks. Deleted/renamed paths
are included; PR comparisons use the merge commit against the base.

Java uses Temurin 21 and `./mvnw verify`, including a PostgreSQL 17 service and a
local JWKS test server. It needs no repository secrets or live Supabase account.
The database password in the workflow is only for the disposable CI service.

Actions are pinned, permissions are read-only, checkout credentials are not
persisted, and superseded runs are cancelled. Java has a 15-minute timeout;
other jobs have five minutes. Dependabot checks Actions weekly and Maven monthly,
grouping Maven minor/patch updates with two open PRs per ecosystem maximum.

## Local checks

```sh
./mvnw --batch-mode --no-transfer-progress verify
ruby .github/scripts/validate-markdown.rb
ruby .github/scripts/validate-yaml.rb
python3 -m unittest discover -s .github/tests -v
```

The PostgreSQL test skips unless `TEST_POSTGRES=true` plus database environment
variables are supplied. CI always enables it. See the [README](../README.md).
Review formats and label transitions are in [AI_REVIEW.md](AI_REVIEW.md).
No branch protection or deployment settings are changed by this workflow.
