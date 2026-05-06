# odamitsu-data-hub

小田光社内システム

<!--
  CI バッジ. リポジトリ owner/name は実環境の URL に合わせて手動更新が必要.
  例: github.com/odamitsu/odamitsu-data-hub なら下記の <owner>/<repo> を odamitsu/odamitsu-data-hub に置き換える.
-->
[![CI](https://github.com/OWNER/odamitsu-data-hub/actions/workflows/ci.yml/badge.svg)](https://github.com/OWNER/odamitsu-data-hub/actions/workflows/ci.yml)

## CI / CD

`.github/workflows/ci.yml` で `pull_request` (target=main) と `push` (main) 時に以下 3 jobs を自動実行:

| Job | 内容 |
|---|---|
| `migration-check` | PostgreSQL 17 service container に対し全 Flyway migration (V001..V042 + Java migration V033) を適用 |
| `backend-test` | `./gradlew test` (JUnit 5, 31 test files) |
| `frontend-typecheck` | `npx tsc --noEmit` + `npm run build` smoke test |

詳細・branch protection 設定手順は `claudedocs/runbook-ci-cd.md` を参照.
