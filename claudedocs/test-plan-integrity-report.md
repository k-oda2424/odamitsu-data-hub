# 買掛帳 整合性検出機能 (軸 B + 軸 C) テスト計画

作成日: 2026-04-22
対象ブランチ: `refactor/code-review-fixes`
対象設計書: `claudedocs/design-integrity-report.md`
関連設計書: `claudedocs/design-accounts-payable-ledger.md`

---

## §1 対象スコープ

### 1.1 テスト対象

**バックエンド**
- `AccountsPayableIntegrityService.generate()` — 4 カテゴリ判定 (MF_ONLY / SELF_ONLY / AMOUNT_DIFF / UNMATCHED_SUPPLIER)
- `MfJournalFetcher` — 共通 helper (候補生成、fiscal year fallback、締め月変換)
- `MfSupplierLedgerService` — fetcher 委譲後のリグレッション確認
- `FinanceController#getIntegrityReport` — endpoint 入出力、エラーマッピング
- `TAccountsPayableSummaryRepository#findByShopNoAndTransactionMonthBetweenOrderBySupplierNoAscTransactionMonthAscTaxRateAsc` — 期間全 row 取得

**フロントエンド**
- `computeMfMatchStatus()` (`types/integrity-report.ts`) — MATCH / MFA_MINOR / MFA_MAJOR / MFM_SELF / MFM_MF の分岐
- `/finance/accounts-payable-ledger/integrity` 画面 — サマリカード + 4 タブ + 行遷移
- `accounts-payable-ledger.tsx` — MFA / MFM バッジ、整合性レポートリンク

### 1.2 テスト対象外 (OUT-OF-SCOPE)

- 仕訳レベルの 1:1 ペアマッチング (設計で除外済み)
- CSV / Excel エクスポート
- MF API の実通信 (統合テストでは MfApiClient モック、smoke で実疎通)
- `MfJournalReconcileService`, `MfBalanceReconcileService` の挙動 (無改修)

### 1.3 テストピラミッド

```
E2E (Playwright)         ~6 シナリオ   ← 画面操作 + バッジ表示
Integration (SpringBoot) ~8 ケース      ← endpoint 入出力
Unit (JUnit5 + Jest)     ~30 ケース     ← ロジック・分岐網羅
```

---

## §2 ユニットテストケース

### 2.1 `AccountsPayableIntegrityServiceTest` (JUnit5 + Mockito + AssertJ)

ファイル: `backend/src/test/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityServiceTest.java`

方針:
- `@ExtendWith(MockitoExtension.class)` + `@MockitoSettings(strictness = LENIENT)`
- 依存は全て `@Mock`: `MfOauthService`, `MfJournalFetcher`, `MfAccountMasterRepository`, `TAccountsPayableSummaryRepository`, `MPaymentSupplierService`
- AssertJ の `assertThat(...).extracting(...).containsExactly(...)` でエントリ内容を検証
- fixture ファクトリ (§7 参照) を `@BeforeEach` で構築

| ID | 対象 | 入力 | 期待 | 優先度 |
|---|---|---|---|---|
| U-IS-01 | `generate()` 入力検証 | shopNo=null | `ResponseStatusException` (400, message="shopNo / fromMonth / toMonth は必須です") | High |
| U-IS-02 | 入力検証 | fromMonth=null | 400 同文言 | High |
| U-IS-03 | 入力検証 | toMonth=null | 400 同文言 | High |
| U-IS-04 | 入力検証 | fromMonth=2026-03-20, toMonth=2026-02-20 | 400 "fromMonth は toMonth 以前..." | High |
| U-IS-05 | 入力検証 (20日固定) | fromMonth=2026-03-15 | 400 "20 日締め日..." | High |
| U-IS-06 | 入力検証 (20日固定) | toMonth=2026-03-21 | 400 "20 日締め日..." | High |
| U-IS-07 | 入力検証 (12ヶ月上限) | from=2025-01-20, to=2026-03-20 (14ヶ月) | 400 "期間は最大 12 ヶ月です" | High |
| U-IS-07a | 入力検証 (13ヶ月境界) | from=2025-02-20, to=2026-03-20 (13ヶ月) | 400 "期間は最大 12 ヶ月です" | High |
| U-IS-08 | 入力検証 (境界: ちょうど12ヶ月) | from=2025-03-20, to=2026-03-20 | 正常実行 (PASS) | High |
| U-IS-09 | MF クライアント未登録 | `mfOauthService.findActiveClient()` = Optional.empty() | `IllegalStateException("MF クライアント設定が未登録...")` | Medium |
| U-IS-10 | **MATCH (±¥100 以下)** | self_delta=100,000 / mf_delta=100,050 (diff=50) | mfOnly/selfOnly/amountMismatch 全て空、summary.*Count=0 | **High** |
| U-IS-11 | MATCH 境界 (diff=100) | self=100,000 / mf=100,100 | Entry 無し (丸め誤差) | High |
| U-IS-12 | **AMOUNT_DIFF_MINOR** | self=100,000 / mf=100,500 (diff=500) | amountMismatch に severity="MINOR" 1 件 | **High** |
| U-IS-13 | AMOUNT_DIFF MINOR 境界 (diff=1000) | diff=1000 | severity="MINOR" | High |
| U-IS-14 | **AMOUNT_DIFF_MAJOR** | self=100,000 / mf=105,000 (diff=5,000) | amountMismatch に severity="MAJOR" 1 件 | **High** |
| U-IS-15 | AMOUNT_DIFF MAJOR 境界 (diff=1001) | diff=1001 | severity="MAJOR" | High |
| U-IS-16 | **MF_MISSING → SelfOnlyEntry** | self_rows>0 (change>0), mf_branches=0 | selfOnly 1 件、reason="MF CSV 出力漏れ or 未反映" | **High** |
| U-IS-17 | **SELF_MISSING → MfOnlyEntry (self ループ経由)** | selfIndex に supplier 含まれるが rows activity 無し / MF branch あり | mfOnly 1 件、guessedSupplierNo 解決済 | **High** |
| U-IS-17a | **SELF_MISSING + supplierMfUnmatched 防御 guard** | supplier が MF 未登録 (matchedSubNames.isEmpty()) AND self rows あるが activity 0 AND MF branch あり | self 側ループでは skip (実装 L287-290 の isEmpty guard)、第 2 ループで mfOnly として処理される | **High** |
| U-IS-18 | **SELF_MISSING → MfOnlyEntry (MF 側ループ経由)** | self 側に supplier 完全に不在 / MF branch あり | mfOnly 1 件、matched subAccount 経由で guessed が入る | High |
| U-IS-18a | **SELF_MISSING → MfOnlyEntry (guessedSupplierNo=null)** | MF branch あり AND mf_account_master 未登録 sub_account | mfOnly 1 件、guessedSupplierNo=null、reason='MF 手入力または未登録 supplier' | High |
| U-IS-19 | **UNMATCHED_SUPPLIER** | mf_account_master に supplierCode 登録なし、self に rows あり | unmatchedSuppliers 1 件、reason="mf_account_master に『買掛金』sub_account として未登録"、かつ self activity あれば selfOnly も併発 | **High** |
| U-IS-20 | **NO_ACTIVITY スキップ (R1)** | self_rows あるが effectiveChange=0 AND paymentSettled=0、mf_branches=0 | Entry 生成されず (summary カウント外) | **High** |
| U-IS-21 | NO_ACTIVITY 部分 | self activity 無し AND mf branchあるが credit/debit ともに 0 | Entry 無し | Medium |
| U-IS-22 | 複数税率合算 | 10% row + 軽8% row 同月、self_delta は合算 | selfDelta = 両 row の合算、taxRateRowCount=2 | High |
| U-IS-23 | 複数月 | 3ヶ月で supplier A に MATCH / MINOR / MAJOR が 1 件ずつ | amountMismatch=2 件 (MATCH はスキップ)、severity 配列で検証 | High |
| U-IS-24 | 複数 sub_account 集約 | 同 supplier_code が MF 上で複数 sub_account に分裂 | mf_delta は全 sub_account の credit/debit を合算、branchCount も合算 | Medium |
| U-IS-25 | `processedSubNames` 重複回避 | self ループで処理した (sub,月) は MF 側ループで除外 | mfOnly に重複生成されないこと | High |
| U-IS-26 | Summary 集計 | mfOnly(+1000) x2, selfOnly(+2000) x1, mismatch(-500, +1500) (**前提: mismatch entries は全て diff > 100。MATCH は entries に入らない**) | totalMfOnly=2000, totalSelfOnly=2000, totalMismatch=2000 (絶対値) | High |
| U-IS-27 | supplierCount union | self に A,B / mfOnly.guessedSupplierNo に C → 3 | supplierCount=3 | Medium |
| U-IS-28 | supplierCount: guessed null は含めない | mfOnly の guessedSupplierNo=null | union に追加されない | Medium |
| U-IS-29 | 期間外 journals フィルタ | 取得 journals に fromMonth 前 / toMonth 後の行混入 | 期間外は bucket から除外 | High |
| U-IS-30 | MF transactionDate が月初 (day=1) | 例: 2026-03-01 → closingMonth 2026-03-20 | 3月 bucket に集計 | Medium |
| U-IS-31 | MF transactionDate が 21日 | 例: 2026-03-21 → closingMonth 2026-04-20 | 4月 bucket に集計 | Medium |
| U-IS-32 | MfBranch creditor/debitor 両方に買掛金 | 振替仕訳のような両側登場 | credit / debit それぞれ加算、branchCount +1 | Low |
| U-IS-33 | subAccountName=null の branch | 対象外 (skip) | bucket に反映されない | Medium |
| U-IS-34 | **手動確定行 (selfDelta 算出)** | verified_manually=true, change=0, verifiedAmount=100,000, paymentSettled=0 | selfDelta=100,000 (effectiveChange-paymentSettled、`PayableBalanceCalculator.effectiveChangeTaxIncluded` が verifiedAmount 優先) | **High** |
| U-IS-35 | **手動確定行 (selfHasActivity 判定)** | change=0, verifiedAmount=100,000 | selfHasActivity=true (effectiveChange 基準なので activity あり判定、NO_ACTIVITY にならない) | **High** |
| U-IS-36 | **手動確定行 verifiedAmount=null fallback** | verified_manually=true, verifiedAmount=null, change=50,000 | `PayableBalanceCalculator.effectiveChangeTaxIncluded` 仕様により taxIncludedAmountChange (50,000) へ fallback、通常行と同等に扱われる | High |

### 2.2 `MfJournalFetcherTest` (JUnit5 + AssertJ)

ファイル: `backend/src/test/java/jp/co/oda32/domain/service/finance/mf/MfJournalFetcherTest.java`

| ID | 対象 | 入力 | 期待 | 優先度 |
|---|---|---|---|---|
| U-JF-01 | `buildStartDateCandidates()` 先頭候補 | from=2026-03-20, to=2026-03-20 | 先頭が 2026-02-21 (前月21日) | High |
| U-JF-02 | 2 番目以降の候補 | 同上 | 2番目=from 自身 (2026-03-20)、3番目=from+1 (2026-03-21) | High |
| U-JF-03 | ユニーク性 (LinkedHashSet) | 重複生成されうる入力 | 返り値内に重複なし | High |
| U-JF-04 | 30 候補上限 | from=2025-03-20, to=2026-03-20 (12ヶ月) | size <= 30 + 1 (safeguard break) | Medium |
| U-JF-05 | 月初/21日の両方生成 | 1ヶ月期間 | 候補に少なくとも `ym.atDay(1)` と `ym.atDay(21)` を含む | Medium |
| U-JF-06 | `toClosingMonthDay20(day=1)` | 2026-03-01 | 2026-03-20 | **High** |
| U-JF-07 | `toClosingMonthDay20(day=20)` | 2026-03-20 | 2026-03-20 | **High** |
| U-JF-08 | `toClosingMonthDay20(day=21)` | 2026-03-21 | 2026-04-20 | **High** |
| U-JF-09 | `toClosingMonthDay20(day=31)` | 2026-03-31 | 2026-04-20 | High |
| U-JF-10 | `toClosingMonthDay20(2月28)` | 2024-02-28 | 2024-03-20 (day=28>20 なので翌月20日) | Medium |
| U-JF-10a | `toClosingMonthDay20(閏年2月29)` | 2024-02-29 | 2024-03-20 (閏年、day=29>20 なので翌月20日) | Medium |
| U-JF-11 | `toClosingMonthDay20(年跨ぎ)` | 2026-12-21 | 2027-01-20 | Medium |
| U-JF-12 | `fetchJournalsForPeriod()` 1 候補目成功 | MfApiClient モックが 1 回で 200 返す | 1 回呼び出しで結果返却、FetchResult.actualStart=候補1 | High |
| U-JF-13 | fiscal year fallback | 1 候補目が 400 "accounting periods" → 2 候補目が 200 | 最終結果は 2 候補目で、1 候補目の 400 は飲み込まれる | High |
| U-JF-14 | 400 だが message に "accounting periods" 含まない | 再 throw (HttpClientErrorException) | 例外伝播 | High |
| U-JF-15 | 全候補で 400 | 全候補失敗 | `IllegalStateException("MF fiscal year 境界エラー...")` | High |
| U-JF-16 | 429 指数バックオフ | 1 回目 429 → 2 回目 200 | 2 回 listJournals 呼び出し、sleep 呼ばれる | Medium |
| U-JF-16a | **候補 1 で 429 retry 成功** | 候補 1: 1 回目 429 → 2 回目 200 | 候補 1 で結果返却、候補 2 は呼ばれない | Medium |
| U-JF-16b | **候補 1 で 400 → 候補 2 で 429 retry 成功** | 候補 1: 400 "accounting periods" / 候補 2: 1 回目 429 → 2 回目 200 | 候補 2 で結果返却、FetchResult.actualStart=候補 2 | Medium |
| U-JF-17 | 429 が retry 上限超過 | 4 回連続 429 | HttpClientErrorException 伝播 | Medium |
| U-JF-18 | pagination safeguard | items.size()==PER_PAGE で 51 ページ到達 | `IllegalStateException("...50 pages")` | Medium |
| U-JF-19 | pagination 1 ページで終了 | items.size() < PER_PAGE | 1 ページで終了、2 ページ目呼ばない | Medium |

### 2.3 `computeMfMatchStatus` (TypeScript)

前提: 現在プロジェクトに Jest が導入されていない (`NO_JEST_DIR`)。
- オプション A (推奨): Vitest を `frontend/` に追加して `types/__tests__/integrity-report.test.ts` を作成 (Next.js 16 との整合性あり)
- オプション B: Jest 既設時に限り `types/integrity-report.test.ts` を追記
- 現時点の決定: **Vitest 導入を前提にケース表を用意し、導入までは E2E でカバー**

ファイル候補: `frontend/types/__tests__/integrity-report.test.ts`

| ID | 入力 (selfDelta, mfDelta, selfHasActivity, mfHasActivity) | 期待 code / label | 優先度 |
|---|---|---|---|
| U-FE-01 | (0, null, false, false) | code=null, label='' | High |
| U-FE-02 | (1000, null, true, true) | code=null, label='' (mfDelta null ガード) | High |
| U-FE-03 | (1000, 0, true, false) | MFM_SELF, 'MFM' | **High** |
| U-FE-04 | (0, 1000, false, true) | MFM_MF, 'MFM' | **High** |
| U-FE-05 | (100000, 100050, true, true) | MATCH, '' | **High** |
| U-FE-06 | (100000, 100100, true, true) | MATCH (境界 diff=100) | High |
| U-FE-07 | (100000, 100500, true, true) | MFA_MINOR, 'MFA' | **High** |
| U-FE-08 | (100000, 101000, true, true) | MFA_MINOR (境界 diff=1000) | High |
| U-FE-09 | (100000, 101001, true, true) | MFA_MAJOR, 'MFA!' | **High** |
| U-FE-10 | (100000, 95000, true, true) | MFA_MAJOR (負方向 diff) | High |
| U-FE-11 | className 検証 | MFA_MINOR | 'border-amber-500 bg-amber-50 text-amber-700' | Medium |
| U-FE-12 | className 検証 | MFA_MAJOR | 'border-red-600 bg-red-100 text-red-800' | Medium |

### 2.4 `MfSupplierLedgerServiceTest` リグレッション

ファイル: `backend/src/test/java/jp/co/oda32/domain/service/finance/mf/MfSupplierLedgerServiceTest.java` (新規 or 既存追記)

fetcher 委譲後に挙動が変わっていないことを確認する smoke 的 unit。

**Snapshot 比較方針 (U-SL-01, U-SL-02)**:
既存 `/ledger/mf` レスポンス JSON を fixture 化し、`assertThat(actual).usingRecursiveComparison().isEqualTo(expected)` で比較する。
もしくは **§5.2 の curl snapshot 比較に置換** (手動 smoke 段階で `jq` 出力を commit 済み golden file と diff)。どちらの方針を採るかは実装時に決定し、本 test plan に追記する。

| ID | 対象 | 入力 | 期待 | 優先度 |
|---|---|---|---|---|
| U-SL-01 | getSupplierLedger() 基本 | MfJournalFetcher モックが journals 3 件返却 | 月次バケットが既存 /ledger/mf 仕様どおり (usingRecursiveComparison で golden JSON と一致) | **High** |
| U-SL-02 | MfJournalFetcher が fallback した場合 | FetchResult.actualStart が 2 候補目 | レスポンスは actualStart 透過、journals 正常処理 (usingRecursiveComparison で golden JSON と一致) | Medium |

---

## §3 統合テストケース

### 3.1 `FinanceIntegrityReportIntegrationTest` (@SpringBootTest)

ファイル: `backend/src/test/java/jp/co/oda32/api/finance/FinanceIntegrityReportIntegrationTest.java`

方針:
- `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`
- 実 DB (H2 or dev PostgreSQL) + `@MockBean AccountsPayableIntegrityService` で service レイヤーをモック
  - サービス単体の挙動は Unit でカバー済み、ここでは Controller の I/O だけ検証
- `@WithMockUser` or `@WithUserDetails("k_oda")` で認証制御
- 認可: `assertShopAccess()` が admin 以外は shopNo=0 以外を拒否 → 非 admin + 他 shop で 403

| ID | 対象 | 入力 | 期待 | 優先度 |
|---|---|---|---|---|
| I-01 | 200 OK 正常 | admin, shopNo=1, from=2026-02-20, to=2026-03-20 | 200, JSON body に summary / mfOnly / selfOnly / amountMismatch / unmatchedSuppliers キー | **High** |
| I-02 | 400 shopNo 欠落 | shopNo=null | 400 Bad Request, body に Spring デフォルト "Required parameter 'shopNo' is not present" を含む (**service レベルの検証メッセージ "shopNo / fromMonth / toMonth は必須です" は unit test のみで検証**) | High |
| I-03 | 400 fromMonth フォーマット | fromMonth="invalid" | 400 Bad Request (Spring バインディング) | High |
| I-04 | 401 未認証 | 認証なし | 401 (or 302 redirect login) | High |
| I-05 | 403 非 admin 他 shop | 一般ユーザー shopNo=2, request shopNo=1 | 403 | **High** |
| I-06 | 422 Service の IllegalStateException | service が throw | 422 Unprocessable Entity, body.message 含む | High |
| I-07 | 401 MfReAuthRequired | service が MfReAuthRequiredException throw | 401, body.message 含む | High |
| I-08 | 403 MfScopeInsufficient | service が MfScopeInsufficientException throw | 403, body.message + body.requiredScope | High |
| I-09 | 400 期間不正 (service のバリデーション到達) | fromMonth > toMonth (Spring をすり抜けた場合) | 400 (service の ResponseStatusException) | Medium |
| I-10 | Response serialization | summary の totalMfOnlyAmount が BigDecimal | JSON では数値 (string でない) | Medium |

### 3.2 `TAccountsPayableSummaryRepositoryTest` (@DataJpaTest)

ファイル: `backend/src/test/java/jp/co/oda32/domain/repository/finance/TAccountsPayableSummaryRepositoryTest.java`

| ID | 対象 | 入力 | 期待 | 優先度 |
|---|---|---|---|---|
| I-R-01 | `findByShopNoAndTransactionMonthBetween...` 範囲取得 | shop=1, from=2026-02-20, to=2026-03-20、外側の月混在 | 期間内のみ、supplierNo→month→taxRate 昇順 | High |
| I-R-02 | 空返却 | 期間に該当行無し | 空 List | Medium |
| I-R-03 | ショップ境界 | shop=1 の期間に shop=2 の行も DB にあり | shop=1 のみ返る | High |

---

## §4 E2E テストシナリオ (Playwright)

### 4.1 ファイル配置

- 新規: `frontend/e2e/integrity-report.spec.ts` (整合性レポート画面 + リンク遷移)
- 既存追記: `frontend/e2e/accounts-payable-ledger.spec.ts` (MFA / MFM バッジ) — 既存が無ければ新規作成

### 4.2 共通 mock ヘルパー

`e2e/helpers/mock-api.ts` に以下を追加 (既存パターンに合わせる):

```ts
// /api/v1/finance/accounts-payable/integrity-report をモック
export async function mockIntegrityReport(page: Page, body: IntegrityReportResponse) {
  await page.route(
    (url) => url.pathname === '/api/v1/finance/accounts-payable/integrity-report',
    (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  )
}
```

### 4.3 シナリオ一覧

| ID | シナリオ | 操作 | 期待 | 優先度 |
|---|---|---|---|---|
| E-01 | 整合性レポート初期表示 | `/finance/accounts-payable-ledger/integrity?shopNo=1&fromMonth=2025-10-20&toMonth=2026-03-20` 直接遷移 | サマリカード 4 項目 (MF 側のみ/自社側のみ/金額差/MF 未登録) 表示、タブ 4 つ | **High** |
| E-02 | タブ切替: MF 側のみ | 「MF 側のみ」タブ click | テーブル列: 月 / sub_account / credit / debit / delta / 推定 supplier / 備考 | **High** |
| E-03 | タブ切替: 自社側のみ | 「自社側のみ」タブ click | 列: 月 / 仕入先コード / 仕入先名 / self_delta / 税率行数 / 備考 | High |
| E-04 | タブ切替: 金額差 | 「金額差」タブ click | 列: 月 / 仕入先 / self / MF / diff / severity バッジ (MINOR amber / MAJOR red) | **High** |
| E-05 | タブ切替: MF 未登録 | 「MF 未登録」タブ click | 列: supplier コード / 仕入先名 / 備考 | Medium |
| E-06 | 行クリック → 買掛帳遷移 | 「自社側のみ」タブで任意行 click | URL が `/finance/accounts-payable-ledger?shopNo=1&supplierNo=X` (or 同等の pre-fill) に遷移 | **High** |
| E-07 | admin ショップ切替 | shopNo セレクトを 2 に変更 → 再取得 | /integrity-report?shopNo=2 が呼ばれる | High |
| E-08 | 期間変更バリデーション | fromMonth > toMonth | クライアント側でエラー表示 or submit 無効 | Medium |
| E-09 | 買掛帳: MATCH バッジ無し | mock で MATCH データ | row にバッジ表示されない | **High** |
| E-10 | 買掛帳: MFA_MINOR バッジ | diff=500 mock | amber ボーダー + "MFA" label、hover で tooltip | **High** |
| E-11 | 買掛帳: MFA_MAJOR バッジ | diff=5000 mock | red ボーダー + "MFA!" label | **High** |
| E-12 | 買掛帳: MFM_SELF バッジ | self>0 / mf=0 mock | red + "MFM"、tooltip に「自社にあって MF に無い」文言 | **High** |
| E-13 | 買掛帳: MFM_MF バッジ | self=0 / mf>0 mock | red + "MFM"、tooltip に「MF にあって自社に無い」文言 | High |
| E-14 | 買掛帳 → 整合性レポートリンク | サマリカードの「整合性レポートへ →」click | URL が `/finance/accounts-payable-ledger/integrity?...` に遷移、shopNo/期間 が引き継がれる | **High** |
| E-15 | 「MF と比較」後にリンク表示 | 買掛帳で MF と比較ボタン click → mfLedger 受信 | サマリ欄に整合性レポートリンク可視化 | High |
| E-16 | 401 (MF 再認証) ハンドリング | mock で 401 + message 返却 | ページに再認証バナー表示、リトライボタン | Medium |
| E-17 | 422 ハンドリング | mock で 422 返却 | エラートースト (sonner) 表示 | Medium |

### 4.4 Playwright 記述上の注意 (MEMORY.md 準拠)

- `getByText('MFA', { exact: true })` を使い 'MFA!' との partial マッチを回避
- shadcn Tabs は `role="tab"` を持つので `getByRole('tab', { name: '金額差', exact: true })` を使用
- データテーブルは `[data-testid="integrity-table"]` を component 側で付与 (今回の実装で未付与なら追加対応)
- mock 順序: **catch-all を先に登録** → 個別 route を後に (LIFO なので catch-all は最後に評価)

---

## §5 手動 smoke test

### 5.1 事前条件

1. バックエンド起動: `cd backend && ./gradlew bootRun --args='--spring.profiles.active=web,dev'` (port 8090)
2. フロントエンド起動: `cd frontend && npm run dev` (port 3000)
3. 認証済み token 取得 (dev: k_oda / asdfasdf)
4. MF OAuth: `m_mf_oauth_client` に active client あり、access_token 有効

### 5.2 curl 実バックエンド疎通

```bash
# 1. login して cookie 取得 (既存の dev パターン)
curl -s -c /tmp/cookie.txt -X POST http://localhost:8090/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"userId":"k_oda","password":"asdfasdf"}'

# 2. 整合性レポート取得 (6ヶ月)
curl -s -b /tmp/cookie.txt \
  'http://localhost:8090/api/v1/finance/accounts-payable/integrity-report?shopNo=1&fromMonth=2025-10-20&toMonth=2026-03-20' \
  | jq '.summary, (.mfOnly | length), (.selfOnly | length), (.amountMismatch | length), (.unmatchedSuppliers | length)'

# 3. 期間不正 (400 期待)
curl -s -b /tmp/cookie.txt -o /dev/null -w '%{http_code}\n' \
  'http://localhost:8090/api/v1/finance/accounts-payable/integrity-report?shopNo=1&fromMonth=2026-03-20&toMonth=2025-10-20'
# → 400

# 4. 非20日 (400 期待)
curl -s -b /tmp/cookie.txt -o /dev/null -w '%{http_code}\n' \
  'http://localhost:8090/api/v1/finance/accounts-payable/integrity-report?shopNo=1&fromMonth=2025-10-15&toMonth=2026-03-20'
# → 400

# 5. 12ヶ月超 (400 期待)
curl -s -b /tmp/cookie.txt -o /dev/null -w '%{http_code}\n' \
  'http://localhost:8090/api/v1/finance/accounts-payable/integrity-report?shopNo=1&fromMonth=2024-10-20&toMonth=2026-03-20'
# → 400

# 6. 既存 /ledger/mf が壊れていないこと (リグレッション)
curl -s -b /tmp/cookie.txt \
  'http://localhost:8090/api/v1/finance/accounts-payable/ledger/mf?shopNo=1&supplierNo=11001&fromMonth=2025-10-20&toMonth=2026-03-20' \
  | jq '.byMonth | length'
```

### 5.3 UI 操作シナリオ (admin = k_oda で shopNo=0)

1. `/finance/accounts-payable-ledger/integrity` にアクセス
2. ショップセレクト: 「1: ○○事業部」を選択
3. 期間: fromMonth=2025-10-20, toMonth=2026-03-20 (6ヶ月)
4. 「再取得」ボタン click
5. サマリカード 4 項目 (MF 側のみ / 自社側のみ / 金額差 / MF 未登録) の数字が表示されることを確認
6. 「MF 側のみ」タブ: 太幸 / やしき 等が列挙されることを確認 (memory: 過去検証データ)
7. 「自社側のみ」タブ: カミ商事以外の supplier_code が正しく表示
8. 「金額差」タブ: severity=MINOR は amber, MAJOR は red
9. 「MF 未登録」タブ: mf_account_master に無い supplier が表示
10. 「金額差」タブの任意行 click → `/finance/accounts-payable-ledger?shopNo=1&supplierNo=X` に遷移し、その supplier の買掛帳が開く
11. 買掛帳画面で「MF と比較」ボタン click → 月別 row に MFA / MFM バッジ表示、MATCH 行にはバッジ無し
12. 買掛帳サマリカードに「整合性レポートへ →」リンク → 元の整合性レポートに戻れる

### 5.4 エラー系 smoke

1. `shopNo` 空: submit → クライアント側バリデーション or 400
2. 非 admin ユーザーで `?shopNo=2` 直アクセス → 403 表示
3. MF access_token 意図的失効 (DB で更新) → 401 + 再認証バナー表示

---

## §6 リグレッション確認項目

### 6.1 既存 `/ledger/mf` 無影響確認

- `MfSupplierLedgerService.getSupplierLedger()` の内部 helper が `MfJournalFetcher` 委譲に変わっているため、以下を確認:
  - [ ] 既存 E2E `finance-accounts-payable.spec.ts` / `real-backend-accounts-payable.spec.ts` が全 PASS
  - [ ] 手動: カミ商事 (shopNo=1, supplierNo=既知) で `/ledger/mf?fromMonth=2025-10-20&toMonth=2026-03-20` 呼び、byMonth の credit/debit/delta が **2026-04-22 の実装前と同じ** であること (diff スナップショット)
  - [ ] `buildStartDateCandidates` / `toClosingMonthDay20` のロジックが元と bit-for-bit 一致
  - [ ] 429 retry 挙動 / 50 pages safeguard / 350ms sleep が変わっていない

### 6.2 MF 関連既存機能

- [ ] `MfJournalReconcileService.reconcile()` — 変更なし
- [ ] `MfBalanceReconcileService` — 変更なし
- [ ] `MfAccountSyncService` — 変更なし、既存 unit test PASS
- [ ] `MfSupplierLedgerService` の公開 API シグネチャ (`getSupplierLedger`, `getSupplierOpeningBalance` 等) 不変

### 6.3 買掛金一覧画面

- [ ] `/finance/accounts-payable` 画面が整合性検出機能追加後も全 column 表示・検証済み行編集が機能すること
- [ ] 手動保護 (`verified_manually`) の行の集計への影響無し

### 6.4 CSV 出力関連

- [ ] 検証済み CSV 出力 (`/export-verified`) のゴールデンマスタ CSV 差分なし
- [ ] MF cashbook / payment MF import のゴールデンマスタ (12 本 + 2 本) PASS

### 6.5 ゴールデンマスタ実行

```bash
cd backend && ./gradlew test --tests '*GoldenMaster*' --info
```

期待: 全 PASS (既存 ~14 ケース)。

---

## §7 テストデータ準備 (fixture 例)

### 7.1 Java fixture ファクトリ (`AccountsPayableIntegrityServiceTest` 用)

```java
// 月×税率別 summary row (通常行)
private TAccountsPayableSummary apSummary(int supplierNo, LocalDate month,
                                          BigDecimal taxRate, BigDecimal changeTaxIncl,
                                          BigDecimal paymentSettled) {
    TAccountsPayableSummary r = new TAccountsPayableSummary();
    r.setShopNo(1);
    r.setSupplierNo(supplierNo);
    r.setTransactionMonth(month);
    r.setTaxRate(taxRate);
    r.setTaxIncludedAmountChange(changeTaxIncl);
    r.setPaymentAmountSettledTaxIncluded(paymentSettled);
    r.setVerifiedManually(false);
    return r;
}

// 手動確定行 overload (U-IS-34/35/36 用)
// verifiedAmount が非 null のとき PayableBalanceCalculator.effectiveChangeTaxIncluded が
// taxIncludedAmountChange の代わりに verifiedAmount を採用する。
private TAccountsPayableSummary apSummary(int supplierNo, LocalDate month,
                                          BigDecimal taxRate, BigDecimal changeTaxIncl,
                                          BigDecimal paymentSettled,
                                          boolean verifiedManually, BigDecimal verifiedAmount) {
    TAccountsPayableSummary r = apSummary(supplierNo, month, taxRate, changeTaxIncl, paymentSettled);
    r.setVerifiedManually(verifiedManually);
    r.setVerifiedAmount(verifiedAmount);
    return r;
}

// MF journal (creditor side)
private MfJournal mfJournalCredit(LocalDate txDate, String subAccountName, BigDecimal amount) {
    MfJournal.MfAccountSide creditor = new MfJournal.MfAccountSide(
            "acc1", "買掛金", subAccountName, "subId", amount, null);
    MfJournal.MfBranch branch = new MfJournal.MfBranch(creditor, /*debitor*/null, amount);
    return new MfJournal("j1", txDate, "仕入", List.of(branch));
}

// MF journal (debitor side = payment)
private MfJournal mfJournalDebit(LocalDate txDate, String subAccountName, BigDecimal amount) {
    MfJournal.MfAccountSide debitor = new MfJournal.MfAccountSide(
            "acc1", "買掛金", subAccountName, "subId", amount, null);
    MfJournal.MfBranch branch = new MfJournal.MfBranch(null, debitor, amount);
    return new MfJournal("j2", txDate, "支払", List.of(branch));
}

private MfAccountMaster mfAccount(String subName, String searchKey) {
    MfAccountMaster m = new MfAccountMaster();
    m.setAccountName("買掛金");
    m.setFinancialStatementItem("買掛金");
    m.setSubAccountName(subName);
    m.setSearchKey(searchKey);
    return m;
}

private MPaymentSupplier supplier(int no, String code, String name) {
    return MPaymentSupplier.builder()
            .shopNo(1).paymentSupplierNo(no)
            .paymentSupplierCode(code).paymentSupplierName(name).build();
}
```

### 7.2 3 supplier × 3 月 の代表シナリオ

| supplier | code | mf_account_master | 2026-01 | 2026-02 | 2026-03 | 期待分類 |
|---|---|---|---|---|---|---|
| 1001 カミ商事 | S1001 | あり | self=100k, mf=100k | self=200k, mf=200,050 | self=300k, mf=301k | MATCH / MATCH / MINOR |
| 1002 太幸 | S1002 | あり | self=0, mf=50k | — | — | MfOnly (SELF_MISSING) |
| 1003 やしき | S1003 | なし (unmatched) | self=30k, mf=0 | — | — | SelfOnly + UnmatchedSupplier |
| 1004 モノタロウ | S1004 | あり | self=500k, mf=508k | — | — | AMOUNT_DIFF MAJOR (diff=8000) |
| 1005 無し支払 | S1005 | あり | self=0, mf=0 | — | — | NO_ACTIVITY (skip) |

期待 summary:
- mfOnlyCount = 1 (太幸 1 月)
- selfOnlyCount = 1 (やしき 1 月)
- amountMismatchCount = 2 (カミ商事 3 月 MINOR + モノタロウ 1 月 MAJOR)
- unmatchedSupplierCount = 1 (やしき)
- supplierCount = 5 (1005 は NO_ACTIVITY でエントリ生成されないが、self ループで `selfSupplierNos.add(...)` に追加されるため union に含まれる)
  - 実装の `selfSupplierNos.add(...)` は selfRows の有無で判定するため、1005 に NO_ACTIVITY でも row があれば union に含まれる点に留意

### 7.3 E2E mock fixture

```ts
const MOCK_REPORT: IntegrityReportResponse = {
  shopNo: 1,
  fromMonth: '2025-10-20',
  toMonth: '2026-03-20',
  fetchedAt: '2026-04-22T12:00:00Z',
  totalJournalCount: 3200,
  supplierCount: 45,
  mfOnly: [
    { transactionMonth: '2026-01-20', subAccountName: '太幸', creditAmount: 50000, debitAmount: 0,
      periodDelta: 50000, branchCount: 1, guessedSupplierNo: 1002, guessedSupplierCode: 'S1002',
      reason: 'MF にあって自社に無い (自社取込漏れ疑い)' },
  ],
  selfOnly: [
    { transactionMonth: '2026-01-20', supplierNo: 1003, supplierCode: 'S1003', supplierName: 'やしき',
      selfDelta: 30000, changeTaxIncluded: 30000, paymentSettledTaxIncluded: 0, taxRateRowCount: 1,
      reason: 'MF 未登録 supplier / MF CSV 出力漏れ' },
  ],
  amountMismatch: [
    { transactionMonth: '2026-03-20', supplierNo: 1001, supplierCode: 'S1001', supplierName: 'カミ商事',
      selfDelta: 300000, mfDelta: 301000, diff: -1000, severity: 'MINOR' },
    { transactionMonth: '2026-01-20', supplierNo: 1004, supplierCode: 'S1004', supplierName: 'モノタロウ',
      selfDelta: 500000, mfDelta: 508000, diff: -8000, severity: 'MAJOR' },
  ],
  unmatchedSuppliers: [
    { supplierNo: 1003, supplierCode: 'S1003', supplierName: 'やしき',
      reason: 'mf_account_master に『買掛金』sub_account として未登録' },
  ],
  summary: {
    mfOnlyCount: 1, selfOnlyCount: 1, amountMismatchCount: 2, unmatchedSupplierCount: 1,
    totalMfOnlyAmount: 50000, totalSelfOnlyAmount: 30000, totalMismatchAmount: 9000,
  },
}
```

---

## §8 実行順序とゲート条件

### 8.1 開発時 (実装 → 検証のインクリメンタルループ)

```
[Stage 0] ファイル編集 -------------------+
    |                                     |
    v                                     |
[Stage 1] tsc --noEmit (frontend)         |  ← 即時
    cd frontend && npx tsc --noEmit       |
    |                                     |
    v                                     |
[Stage 2] compileJava (backend)           |
    cd backend && ./gradlew compileJava   |
    |                                     |
    v                                     |
[Stage 3] Unit test (差分)                |
    ./gradlew test --tests '*IntegrityService*' \
                       --tests '*MfJournalFetcher*'
    |                                     |
    v                                     |
[Stage 4] Unit test (全体)                |
    ./gradlew test                        |
    |                                     |
    v                                     |
[Stage 5] バックエンド再起動 + curl 疎通   | ← JVM 再起動必須 (新 Bean 追加)
    §5.2 の curl 1-6 実行                 |
    |                                     |
    v                                     |
[Stage 6] Playwright E2E (差分 spec)      |
    npx playwright test integrity-report.spec.ts \
                       accounts-payable-ledger.spec.ts
    |                                     |
    v                                     |
[Stage 7] Playwright E2E (全体)           |
    npx playwright test                   |
    |                                     |
    v                                     |
[Stage 8] 手動 smoke (UI §5.3)            |
    |                                     |
    v                                     |
[Stage 9] リグレッション §6                |
    |                                     |
    v                                     |
[Stage 10] code-review → commit -----------+
```

### 8.2 ゲート条件

| Gate | 条件 | 失敗時の対処 |
|---|---|---|
| G1 (Stage 1-2) | `tsc --noEmit` exit 0 AND `compileJava` BUILD SUCCESSFUL | 型エラー修正、Stage 0 に戻る |
| G2 (Stage 3) | 新規 unit 全 PASS、優先度 High ケースは必須 | red テスト修正 |
| G3 (Stage 4) | 既存 unit リグレッション 0 | MfSupplierLedgerServiceTest が落ちたら helper 委譲を疑う |
| G4 (Stage 5) | curl 1 (200 OK) + curl 3/4/5 (400) + curl 6 (/ledger/mf 従来通り) | 実バックエンド疎通失敗 → spring context / Bean 依存を再確認 |
| G5 (Stage 6) | 新規 E2E 全 PASS、優先度 High シナリオは必須 | mock API route の LIFO 順序確認 |
| G6 (Stage 7) | 既存 E2E リグレッション 0 | accounts-payable / payment-mf-import が落ちていないか確認 |
| G7 (Stage 8) | §5.3 全手順完了、目視で期待画面一致 | UI レンダリング不具合 → component を再確認 |
| G8 (Stage 9) | §6 チェックリスト全項目 ✓、ゴールデンマスタ全 PASS | 新 service 導入が既存 fetcher 経路に副作用を出していないか調査 |
| G9 (Stage 10) | `/code-review` で重大指摘 0 | High 指摘は修正後に Stage 0 に戻って再回帰 |

### 8.3 CI 相当のミニマム実行

PR マージ前に必須:

```bash
# バックエンド
cd backend && ./gradlew clean test

# フロントエンド
cd frontend && npx tsc --noEmit && npx playwright test

# リグレッション smoke
curl -s -b /tmp/cookie.txt \
  'http://localhost:8090/api/v1/finance/accounts-payable/ledger/mf?shopNo=1&supplierNo=11001&fromMonth=2025-10-20&toMonth=2026-03-20' \
  | jq '.byMonth | length'
```

### 8.4 優先度凡例

- **High**: リリース前に必ず PASS しなければならないケース (ビジネス正当性に直結)
- **Medium**: 代表値は必須、エッジケースは時間があれば
- **Low**: nice-to-have。追加コードカバレッジ向上用

---

## 付録 A: カバレッジ目標

| レイヤー | カバレッジ目標 | 測定方法 |
|---|---|---|
| `AccountsPayableIntegrityService` | 行 85% / 分岐 80% | JaCoCo |
| `MfJournalFetcher` | 行 90% / 分岐 85% | JaCoCo |
| `FinanceController.getIntegrityReport` | 行 100% (全 catch 経路網羅) | JaCoCo |
| `computeMfMatchStatus` | 分岐 100% | Vitest (導入後) |
| 画面 (integrity-report.tsx) | E2E シナリオで High 全通し | Playwright trace |

## 付録 B: 既知の制約・メモ

- MF API の fiscal year 境界制約: 期間跨ぎで start_date が前 fiscal year に入ると 400。既存の多段 fallback でカバー (U-JF-13)
- 6/20 bucket は MF 側取得不可 (既存既知制約、integrity-report でも再現、NO_ACTIVITY 扱い可)
- `MfAccountMaster` に同 sub_account_name で複数 supplier_code が紐付く可能性 (表記揺れ) → `Set<String>` で保持済み
- 大量データ (12 ヶ月 × 5000 journals) の性能は現場で 10 秒以内を目標値として監視
