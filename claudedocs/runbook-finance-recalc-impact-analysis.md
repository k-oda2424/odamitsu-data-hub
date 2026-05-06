# Runbook: Finance 集計ロジック改修 影響範囲分析

作成日: 2026-05-06
対象: 経理関連の集計 Service / Calculator / Tasklet 改修時 (T3 案 D'' = T3 DEFER + 開発運用ガイドライン + ゴールデンマスタ拡充)
関連: T3 設計検討、P1-03 案 D-2 (PaymentMfImport supplier 別 attribute 展開)

---

## 1. 目的

集計ロジックの改修・試行錯誤で **過去確定月の集計値が意図せず変動する問題** を防ぐ。

具体的には、以下のような事故を CI workflow `.github/workflows/ci.yml` (`pull_request` 時に自動実行) で検知し、merge 前に手戻りさせる:

- `PayableMonthlyAggregator.applyPaymentSettled` の按分式変更で過去 6 ヶ月の `payment_amount_settled_*` が変わる
- `SupplierBalancesService.accumulateMfJournals` の journal フィルタ変更で全 supplier の MF debit 累積が ±10〜30 円ずれる
- `PaymentMfImportService` の振込手数料値引按分ロジック変更で過去 CSV と差分が出る
- `PayableBalanceCalculator.effectiveChangeTaxIncluded` の手動確定行優先ロジック変更で過去月の closing が変動する

CI 構成詳細・branch protection 設定・トラブルシュートは `claudedocs/runbook-ci-cd.md` を参照。

---

## 2. T3 (マスタ履歴化) との関係

| 観点 | T3 (マスタ履歴化) | 本 runbook |
|---|---|---|
| 解決対象 | マスタ変更 (税率/勘定科目/仕入先紐付け 等) による過去月再集計の不整合 | **改修ロジック変更** による過去月再集計の不整合 |
| 必要性 | 実需 (マスタの過去変更頻度) が低く DEFER | 改修頻度が高いため即時必要 |
| 実装方式 | マスタテーブルへの SCD Type 2 + as-of 引き当て | 開発運用ガイドライン + ゴールデンマスタ単体テスト |
| 本プロジェクト方針 | DEFER (実需発生まで保留) | **本 runbook で代替** |

両者は独立した問題。本プロジェクトでは T3 は DEFER し、本 runbook で対処する。マスタ変更起因の集計ずれが実運用で観測されたら T3 を再検討する。

---

## 3. 改修時の標準手順

### Step 1: 改修対象の特定

改修対象の Service / Calculator / Tasklet を列挙:

- 計算式 (合計、按分、税率逆算、累積)
- 集計範囲 (期間、フィルタ条件、`isPaymentOnly` 等のマーカー)
- 出力形式 (DTO 構造、フィールド)

代表的な対象:

| 種別 | クラス |
|---|---|
| Aggregator | `PayableMonthlyAggregator`, `MfPaymentAggregator` |
| Calculator | `PayableBalanceCalculator`, `MfTaxResolver` |
| CSV Writer | `PaymentMfCsvWriter`, `CashBookConvertService`, `PurchaseJournalCsvService` |
| Service (集計表示) | `SupplierBalancesService`, `AccountsPayableLedgerService`, `AccountsPayableIntegrityService`, `MfSupplierLedgerService` |
| Tasklet | `AccountsPayableAggregationTasklet`, `AccountsPayableBackfillTasklet` |
| Import Service | `PaymentMfImportService`, `InvoiceImportService`, `CashBookConvertService` |

### Step 2: 影響を受ける Table / CSV / 画面の特定

| 種別 | 候補 |
|---|---|
| 書き込み先 Table | `t_accounts_payable_summary`, `t_accounts_receivable_summary`, `t_payment_mf_import_history`, `t_payment_mf_aux_row`, `t_consistency_review` |
| 出力 CSV | MF 仕入仕訳, MF 振込仕訳, MF 出納帳, MF 売上仕訳 |
| 画面表示 | 累積残一覧, 整合性レポート, 買掛帳, 累積残 supplier 詳細, MF ヘルスチェック |

### Step 3: 改修前のスナップショット取得 (main branch)

```bash
# main branch でテスト環境を起動
git checkout main
cd backend && ./gradlew bootRun --args='--spring.profiles.active=web,dev'

# 主要 API の出力を JSON で保存 (Authorization は dev 環境の cookie でも可)
# 例: 買掛帳 (1 supplier ÷ 5 ヶ月) と累積残一覧
curl -H "Cookie: SESSION=..." \
  "http://localhost:8090/api/v1/finance/accounts-payable-ledger?shopNo=1&supplierNo=22&fromMonth=2025-12-20&toMonth=2026-04-20" \
  > snapshot-before-ledger-22.json

curl -H "Cookie: SESSION=..." \
  "http://localhost:8090/api/v1/finance/supplier-balances?shopNo=1&asOfMonth=2026-04-20" \
  > snapshot-before-balances.json

curl -H "Cookie: SESSION=..." \
  "http://localhost:8090/api/v1/finance/integrity-report?shopNo=1&asOfMonth=2026-04-20" \
  > snapshot-before-integrity.json

# t_accounts_payable_summary の現状を CSV ダンプ (psql 直接)
psql -h localhost -p 55544 -U postgres -d odamitsu \
  -c "\copy (SELECT shop_no, supplier_no, transaction_month, tax_rate, opening_balance_tax_included, tax_included_amount_change, payment_amount_settled_tax_included, verified_amount, is_payment_only, verified_manually FROM t_accounts_payable_summary WHERE shop_no=1 ORDER BY supplier_no, transaction_month, tax_rate) TO 'snapshot-before-summary.csv' CSV HEADER"
```

### Step 4: 改修実施 (feature branch)

```bash
git checkout -b feature/improve-xxx
# 改修コミット (compileJava + 関連 test PASS まで)
cd backend && ./gradlew compileJava test --tests '*Aggregator*' --tests '*Balances*' --tests '*PaymentMf*' --tests '*CashBook*'
```

### Step 5: 改修後のスナップショット取得

Step 3 と **同じ API / SQL** を実行、結果を `snapshot-after-*.json` / `snapshot-after-summary.csv` に保存。

> NOTE: 改修内容によっては再集計バッチの実行が必要 (`AccountsPayableAggregationTasklet` / `AccountsPayableBackfillTasklet`)。
> その場合は **再集計後の snapshot を取る前に**、t_accounts_payable_summary を pg_dump でバックアップしておく。

### Step 6: 差分分析

```bash
# JSON を整形して diff
diff <(jq -S . snapshot-before-ledger-22.json) <(jq -S . snapshot-after-ledger-22.json) > diff-ledger-22.txt

# CSV の差分行数 / 影響 supplier 数を集計
diff snapshot-before-summary.csv snapshot-after-summary.csv > diff-summary.txt
wc -l diff-summary.txt
grep -E "^[<>]" diff-summary.txt | awk -F',' '{print $2}' | sort -u | wc -l   # 影響 supplier 数
```

### Step 7: 差分の妥当性判定

| 差分の種類 | 判定 | 対応 |
|---|---|---|
| 意図通りの変化 (改修目的) | OK | そのまま merge (PR 説明に明記) |
| 過去月への副作用 (= 確定済月への影響) | NG | 改修見直し or 旧ロジック条件分岐保持 |
| 期間外への波及 | 要確認 | 設計レビュー |
| 手動確定行 (`verified_manually=true`) の値変動 | NG (必ず保護) | 改修見直し (上書き分岐の漏れ) |
| 売上 / 売掛側への波及 (買掛改修なのに) | NG | 影響範囲特定の見直し |

### Step 8: 過去確定月の保護ルール

**原則**: 締め月以前の集計値は不変であること。

具体的判定:

- `transaction_month <= 直近確定締め月` の行で値が変わる → **NG**
- 期首前 (`m_supplier_opening_balance`) の取扱変化 → 要レビュー
- 手動確定行 (`verified_manually=true`) の値変動 → **NG** (必ず保護)
- payment-only 行 (`is_payment_only=true`) の生成 / 削除条件変更 → 要確認 (生成タイミング合意済みか)

直近確定締め月の判定基準:

- 経理締めが完了し MF への CSV 出力 (検証済 CSV / paymentmf-import) 済の月は不変
- `t_payment_mf_aux_row` に補助行が保存済の (transaction_month, transfer_date) は不変

---

## 4. ゴールデンマスタテストの位置付け

CI workflow `.github/workflows/ci.yml` の `backend-test` job で常時実行されるゴールデンマスタテストで機械的に検出する。

### 対象テスト

| テスト名 | 対象 Service | fixture | 状態 |
|---|---|---|---|
| `CashBookConvertServiceGoldenMasterTest` | `CashBookConvertService` | `src/test/resources/cashbook/*.xlsx` + `*.csv` (12 本) | enabled |
| `PaymentMfImportServiceGoldenMasterTest` | `PaymentMfImportService.convert()` | `src/test/resources/paymentmf/*.xlsx` + `*_v3.csv` (2 本) | **enabled** (2026-05-06) |
| `PaymentMfImportServiceAuxRowTest` | `PaymentMfImportService.applyVerification()` | 同 xlsx (CSV 不要、純 Java unit test) | **enabled** (2026-05-06) |
| `PaymentMfImportServiceFixtureGenerator` | 上記 v3 CSV 再生成用ワンショット | xlsx 入力のみ | **@Disabled** (再生成時のみ手動有効化) |
| `PayableMonthlyAggregatorGoldenTest` | `PayableMonthlyAggregator` | 純 Java builder fixture (`backend/src/test/java/.../PayableMonthlyAggregatorGoldenTest.java`、14 ケース) | enabled |
| `SupplierBalancesServiceGoldenTest` | `SupplierBalancesService` | 純 Java builder fixture (`backend/src/test/java/.../SupplierBalancesServiceGoldenTest.java`、8 ケース) | enabled |

> NOTE (2026-05-06): PaymentMfImport 系の `@Disabled` は本日解除。
> - **GoldenMasterTest**: v3 fixture (`買掛仕入MFインポートファイル_20260205_v3.csv`, `_20260220_v3.csv`) を採用。
>   旧 v1/v2 CSV は revert 用に残置。
> - **AuxRowTest**: 旧「SUMMARY=2 件」assertion を「SUMMARY=0 件 + PAYABLE_FEE/EARLY ≥ 1 件」に書き換え。
>   V038 で aux CHECK 制約が `PAYABLE_*`/`DIRECT_PURCHASE_*` まで拡張されたことに整合。
> - **FixtureGenerator**: agent 環境で v3 CSV を機械的に再生成するための新規 helper。
>   `PaymentMfImportService.convert()` の出力を `_v3.csv` として書き出すワンショット test。
>   通常は `@Disabled`。`./gradlew test --tests '*FixtureGenerator*'` を流したいときだけ一時的に外す。

### 改修時のフロー

1. 改修開始 (feature branch)
2. `./gradlew test` でゴールデンマスタが fail するか確認
3. fail した場合の判断:
   - **意図通り** → fixture 更新 (§5 / §6 参照) → commit
   - **意図外** → 改修見直し (過去確定月への副作用)
4. 全 PASS まで繰り返し
5. PR に「改修影響範囲」セクションを記載

### Fixture 更新の原則

- fixture 更新は **必ず user 環境** で実 Excel / 実 DB 経由で再生成する (agent 環境では実 Excel / MF 接続不可)
- 更新前に旧 fixture を git に残す (revert 用バックアップ)
- PR 説明に「fixture 更新理由 + 差分件数 + 検収ログ」を記載

---

## 5. PaymentMfImport ゴールデンマスタ Fixture 更新手順

### 方法 A: agent (純 Java) で再生成 (**推奨**, 2026-05-06 で導入)

xlsx fixture が `src/test/resources/paymentmf/` に存在する月については、`PaymentMfImportServiceFixtureGenerator`
(test class) を 1 度実行すれば CSV が機械的に再生成される。DB / MF API / UI 一切不要。

1. `backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfImportServiceFixtureGenerator.java`
   から `@Disabled` を一時的に外す
2. 対象 xlsx を追加した場合は `generate_v3_fixtures()` 内に `generateOne(...)` 呼び出しを追加
3. `./gradlew test --tests '*PaymentMfImportServiceFixtureGenerator*'` を実行
4. 出力: `src/test/resources/paymentmf/買掛仕入MFインポートファイル_YYYYMMDD_v3.csv`
5. `@Disabled` を戻す
6. `PaymentMfImportServiceGoldenMasterTest` の `@CsvSource` で新 fixture を参照
7. `./gradlew test --tests '*PaymentMfImportServiceGoldenMasterTest*' --tests '*PaymentMfImportServiceAuxRowTest*'` で PASS 確認

これは Mockito + seed SQL から rules を構築して `service.convert()` を叩くだけなので
PaymentMfImportService 本体の出力構造変更があっても (旧 v1/v2 とは別に) 安定再生成できる。

### 方法 B: user 環境 (実 UI 経由) で再生成

新規 xlsx fixture を追加するときや、Excel 形式自体を変更したいときはこちら。

#### 前提条件

- backend が起動 (web,dev profile、port 8090)
- admin login 済み
- 振込明細 Excel が手元に存在する (例: `H:\Dropbox\自分用\小田光\マネーフォワード用\買掛処理用\振込み明細08-4-20.xlsx`)
- `m_payment_mf_rule` が seed SQL (`V011__create_payment_mf_tables.sql`) で初期化済み

#### 手順

1. user 環境で対象月の振込明細 Excel を準備
2. UI で `/finance/payment-mf-import` を開く
3. Excel をアップロード (preview 取得、`uploadId` 確認)
4. 内容を画面で検収 (PAYABLE / PAYABLE_FEE / PAYABLE_DISCOUNT / PAYABLE_EARLY / PAYABLE_OFFSET / DIRECT_PURCHASE_* / EXPENSE / SUMMARY 行を目視確認)
5. 「検証実行」ボタンで `applyVerification` 実行 (補助行が `t_payment_mf_aux_row` に保存される)
6. 「CSV ダウンロード」で MF CSV 取得
7. CSV を `backend/src/test/resources/paymentmf/買掛仕入MFインポートファイル_YYYYMMDD_v3.csv` に保存
   (既存命名規則踏襲、案 D 適用後は `_v3` で区別)
8. xlsx 自体も `src/test/resources/paymentmf/振込み明細YY-M-D.xlsx` に保存
9. `PaymentMfImportServiceGoldenMasterTest` の `@CsvSource` を更新:
   ```java
   "振込み明細08-4-20.xlsx, 買掛仕入MFインポートファイル_20260420_v3.csv"
   ```
10. `./gradlew test --tests '*PaymentMfImportServiceGoldenMasterTest*'` で PASS 確認

### 注意事項

- CSV エンコーディング: **CP932 + LF** (cashbook の UTF-8 BOM+CRLF と異なる)
- 金額末尾に半角スペース付与
- 取引日列 = 締め日 (`transactionMonth`、前月20日) で固定 (送金日ではない、2026-04-15 統一)
- supplier 別 attribute 展開後、SUMMARY 行は出力されない (`PAYABLE_FEE` / `PAYABLE_DISCOUNT` / `PAYABLE_EARLY` / `PAYABLE_OFFSET` で代替)

---

## 6. PayableMonthlyAggregator / SupplierBalancesService Fixture 更新手順

### PayableMonthlyAggregator

純 Java unit test。fixture は test code 内 builder で表現:

1. `backend/src/test/java/jp/co/oda32/batch/finance/service/PayableMonthlyAggregatorGoldenTest.java` を編集
   - 入力: `row(supplierNo, month, taxRate)` builder で各 row を作成
   - 期待値: `assertThat(...).isEqualByComparingTo("...")` で BigDecimal 比較
2. シナリオ追加時は `@Nested class ScenarioN_xxx` を追加
3. `./gradlew test --tests '*PayableMonthlyAggregatorGoldenTest*'` で PASS 確認

### SupplierBalancesService

1. `backend/src/test/java/jp/co/oda32/domain/service/finance/SupplierBalancesServiceGoldenTest.java` を編集
   - 入力: `row(supplierNo, month, taxRate)` + `payableJournal(date, subAccount, credit, debit)` で構築
   - master / opening / MF account はそれぞれ `stubMaster` / `stubOpeningEmpty` / `stubMfAccount` helper で stub
2. `./gradlew test --tests '*SupplierBalancesServiceGoldenTest*'` で PASS 確認

### 注意事項

- 純 Java builder fixture なので **DB 接続も MF 接続も不要** (agent 環境でも編集可)
- BigDecimal の比較は必ず `isEqualByComparingTo("...")` (scale 違いで isEqualTo は失敗する)
- LocalDate は `LocalDate.of(2026, 4, 20)` か定数 `AS_OF` / `MARCH_20` を使用
- `payableJournal` helper は **debit 買掛金 ダミー branch** を含めて isOpeningCandidate=false を保証
  ({@link MfOpeningJournalDetector#isOpeningCandidate} は credit-only な買掛金仕訳を opening と誤判定するため)

---

## 7. 改修担当者向けチェックリスト

PR 作成時に以下を満たしていることを確認:

- [ ] 改修対象の Service / Calculator を列挙した
- [ ] 影響を受ける Table / CSV / 画面を列挙した
- [ ] main branch でのスナップショット取得済 (§3 Step 3)
- [ ] feature branch でのスナップショット取得済 (§3 Step 5)
- [ ] 差分分析を実施し、過去確定月への副作用がないことを確認した (§3 Step 7-8)
- [ ] ゴールデンマスタテスト (`CashBookConvert*`, `PaymentMfImport*`, `*Aggregator*`, `*Balances*`) が PASS する
- [ ] 手動確定行 (`verified_manually=true`) が改修で上書きされないことを確認した
- [ ] CI (`.github/workflows/ci.yml`) の 3 jobs (migration-check / backend-test / frontend-typecheck) が全 PASS
- [ ] PR 説明に以下のセクションを記載:
  - 改修影響範囲 (Service / Table / CSV / 画面)
  - 過去月への影響 (なし / あり + 理由)
  - fixture 更新の有無 (あり/なし、ある場合は理由)
  - スナップショット差分件数 (supplier 数 / 行数 / 金額レンジ)

---

## 8. 関連ドキュメント

- `claudedocs/design-payment-mf-import.md` — PaymentMfImport 仕様 (P1-03 案 D-2 適用後の構造)
- `claudedocs/design-supplier-balances-health.md` — SupplierBalancesService の設計 (軸 D+E)
- `claudedocs/design-integrity-report.md` — AccountsPayableIntegrityService の設計 (軸 B+C)
- `claudedocs/design-phase-b-prime-payment-settled.md` — PayableMonthlyAggregator の設計 (Phase B')
- `claudedocs/design-source-of-truth-hierarchy.md` — verified_amount 不変条件
- `MEMORY.md` (project_residual_overcount.md 参照) — 旧バッチ起因の過去月過剰計上事例
