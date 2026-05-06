# Runbook: CI / CD (GitHub Actions)

作成日: 2026-05-06
対象: `.github/workflows/ci.yml`
関連: Codex Major G4-M1 (CI 整備), `runbook-finance-recalc-impact-analysis.md`

---

## 1. CI workflow 概要

`.github/workflows/ci.yml` で以下 3 jobs を並列実行する.

| Job | Trigger | 目的 | 実行時間目安 |
|---|---|---|---|
| `migration-check` | PR + main push | 全 Flyway migration を本番同等の PostgreSQL 17 に適用. SQL syntax / Java migration (V033) / 鍵 fail-fast / baseline 設定の事故を検出 | ~3-5 分 |
| `backend-test` | PR + main push | `./gradlew test` (31 test files, ゴールデンマスタ含む) | ~3-4 分 |
| `frontend-typecheck` | PR + main push | `npx tsc --noEmit` + `npm run build` (Next.js smoke test) | ~2-3 分 |

### Trigger

- `pull_request` (base = `main`)
- `push` to `main` (= merge 後の確認用)
- 同一 ref の重複起動は `concurrency` で自動キャンセル

### 並列性

3 jobs は依存関係を持たず並列実行する. 1 job が fail しても他 jobs は続行 (= 一度の PR で全エラー把握可能).

---

## 2. 各 job の実装ポイント

### 2.1 migration-check

**実装方式**: bootRun + log marker polling (案 A)

選択理由:
- V033 (Java migration `db.migration.V033__reencrypt_mf_oauth_secrets`) が `APP_CRYPTO_OAUTH_KEY` 等の env を読むため, Spring Boot context 経由での Flyway 起動が最も簡潔.
- `org.flywaydb.flyway` Gradle plugin を導入する案も検討したが, V033 を classpath に含める追加 plumbing が必要なため採用見送り.
- Spring Boot Actuator は依存に含まれないので, `Started WebApplication` ログ出現を起動完了マーカーとする.

**手順**:
1. PostgreSQL 17 service container を起動 (port 55544 = dev 構成と同じ)
2. `./gradlew bootRun --args='--spring.profiles.active=web,dev'` を background 起動
3. `build/bootRun.log` を 1 秒ごと polling し以下を判定:
   - `Started WebApplication` 出現 → 成功 (= Flyway 完了 + Spring 起動完了)
   - `APPLICATION FAILED TO START` 出現 → 失敗 (詳細は log 出力)
   - 180 秒 timeout → 失敗
4. `psql` で `flyway_schema_history` を直接確認, `success = false` 行があれば fail
5. プロセスを TERM → 30 秒 graceful shutdown 待ち → KILL

**環境変数**:

| Var | 用途 | CI 値 |
|---|---|---|
| `APP_CRYPTO_OAUTH_KEY` | V033 で必須. dev fallback 無し | `ci-dummy-oauth-key-for-flyway-check-2026` |
| `APP_CRYPTO_OAUTH_SALT` | V033 で必須. dev fallback 無し | `0123456789abcdef0123456789abcdef` |
| `APP_CRYPTO_KEY` / `APP_CRYPTO_SALT` | 汎用暗号鍵 | application-dev.yml の dev fallback 使用 |
| `JWT_SECRET` | 認証 | application-dev.yml の dev fallback 使用 |
| `BCART_ACCESS_TOKEN` | B-CART API | application-dev.yml の dev fallback 使用 (= 失効済の旧 token, 起動には影響しない) |

CI 用ダミー鍵は **production 鍵では絶対に無い**. CI 用 PostgreSQL は service container で毎回破棄される一過性 DB のため, 鍵漏洩リスクは無い.

**失敗時の artifact**: `bootRun-log` が `backend/build/bootRun.log` (180 秒分) を保存. GitHub Actions 上から DL 可.

### 2.2 backend-test

`TestApplication` (`backend/src/test/java/jp/co/oda32/TestApplication.java`) で `DataSourceAutoConfiguration` 等を exclude しており, JVM 内で完結する純粋 unit test 構成. DB 不要, H2 も使用していない.

ゴールデンマスタテスト (`*GoldenMasterTest`, `*GoldenTest`) はファイル比較 + 計算ロジック検証のみで外部依存無し.

**artifact**: `backend-test-report` が `build/reports/tests/test/` (HTML レポート) と `build/test-results/test/` (JUnit XML) を保存.

### 2.3 frontend-typecheck

- Node.js 20 + npm cache (`package-lock.json` ベース)
- `npm ci` (lockfile 厳格)
- `npx tsc --noEmit` (TypeScript strict mode)
- `npm run build` (Next.js 16 build smoke test, `NEXT_TELEMETRY_DISABLED=1`)

E2E テスト (Playwright) は将来段階対応 (= §6 参照).

---

## 3. Branch protection 設定 (GitHub UI 作業)

CI が整備されただけでは強制力が無い. main branch の保護を以下手順で有効化する (= owner / admin 権限必要).

1. リポジトリ → **Settings** → **Branches**
2. **Branch protection rules** → **Add rule**
3. **Branch name pattern**: `main`
4. 以下にチェック:
   - [x] **Require a pull request before merging**
     - [x] Require approvals: 1 以上
     - [x] Dismiss stale pull request approvals when new commits are pushed
   - [x] **Require status checks to pass before merging**
     - [x] Require branches to be up to date before merging
     - **Required checks** に追加:
       - `Flyway Migration Check`
       - `Backend Unit Tests`
       - `Frontend TypeScript Check`
   - [x] **Require conversation resolution before merging**
   - [x] **Do not allow bypassing the above settings** (admin も含む)
5. **Save changes**

> 注: `Required checks` は CI が **少なくとも 1 回成功** していないと候補に出てこない. 本 workflow を main に merge した直後の build 完了を待ってから設定する.

---

## 4. Secrets 設定

**現時点では secrets 設定は不要**.

- `APP_CRYPTO_OAUTH_KEY` 等は CI yml にハードコードされた **CI 専用ダミー値** で完結する (production 鍵ではない).
- production の鍵は別系統 (デプロイサーバの env, secret manager 等) で管理されており, CI には流入しない.
- 将来 GitHub Actions から本番デプロイを行う場合は, GitHub Secrets に production 鍵を登録すること.

---

## 5. 失敗時の通知

現状: 失敗は GitHub Actions の UI と PR check 一覧でのみ確認可能.

将来検討:
- Slack 通知 (Slack incoming webhook + GitHub Actions `slackapi/slack-github-action`)
- メール通知 (= GitHub の個人通知設定で対応可能)

---

## 6. ローカルで CI 互換チェックを実行

PR 提出前にローカルで同等のチェックを走らせる手順:

```bash
# Backend unit test (= migration-check + backend-test 相当)
cd backend && ./gradlew test

# Frontend typecheck (= frontend-typecheck 相当)
cd frontend && npx tsc --noEmit

# Frontend build smoke (= frontend-typecheck の build step 相当)
cd frontend && npm run build
```

migration-check のフル相当をローカルで再現するには, 既存の dev DB (oda32-postgres17, port 55544) に対して `./gradlew bootRun --args='--spring.profiles.active=web,dev'` を実行する (= 通常開発フローと同じ). dev DB を空にしてからの clean migration を確認したい場合は別途新規 DB を立てて DB URL を上書きする.

---

## 7. 既存 runbook との整合性

`runbook-finance-recalc-impact-analysis.md` (集計ロジック改修時) には:

- `CI レベルで検知` (L13)
- `CI で常時実行` (L147)
- `CI で全テスト PASS` (L283)

の記述があり, 本 CI workflow 整備でこれらが正規化された (= 旧記述は「将来予定」だったのが「実装済み」になった).

具体的な検出機構:
- `PayableMonthlyAggregatorGoldenTest`, `SupplierBalancesServiceGoldenTest`, `PaymentMfImportServiceGoldenMasterTest`, `CashBookConvertServiceGoldenMasterTest` などのゴールデンマスタテストは `backend-test` job で毎 PR 自動実行される.
- 集計ロジック改修で過去確定月の値が変動すると, ゴールデンマスタとの差分で CI が fail する.

---

## 8. 将来の拡張

| 項目 | 備考 |
|---|---|
| E2E (Playwright) | 別 job として追加 (= backend を docker-compose で起動 + frontend を build + Playwright 実行). 実行時間が伸びる (~10 分) ので, schedule トリガ or label 駆動に分離すること検討 |
| Code coverage | JaCoCo + Codecov / Coveralls 連携 |
| Slack 通知 | §5 参照 |
| Dependabot 自動マージ | minor/patch のみ自動 merge |
| Lint (`./gradlew check`, `npm run lint`) | 現状 `./gradlew test` で includesQA は十分だが将来追加検討 |
| 本番デプロイ workflow | `.github/workflows/deploy.yml` 別ファイルで分離 |

---

## 9. トラブルシュート

### migration-check が timeout する
- `bootRun.log` artifact を確認. 大抵以下のいずれか:
  - PostgreSQL service container が起動していない (= ports / health check 設定確認)
  - V033 の env 不足 (= `APP_CRYPTO_OAUTH_KEY` 設定確認)
  - 新規 migration の SQL syntax error (= log の `Migration V0XX__... failed` を確認)

### backend-test が `OutOfMemoryError`
- `JAVA_TOOL_OPTIONS` で heap 拡張 (例: `-Xmx2g`).
- 単体テストでは滅多に発生しないが, ゴールデンマスタの巨大データロードで起きる場合は test fixture の見直し.

### frontend-typecheck の `npm ci` が失敗
- `package-lock.json` と `package.json` の不整合. ローカルで `npm install` 後 lockfile を commit する.

### CI が走らない (= GitHub Actions tab で何も表示されない)
- `.github/workflows/ci.yml` の YAML syntax error. ローカルで `yamllint .github/workflows/ci.yml` 等で確認.
- リポジトリ設定で Actions が無効化されている (= Settings → Actions → General).
