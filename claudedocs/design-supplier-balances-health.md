# 軸 D+E: supplier 累積残一覧 + MF 連携ヘルスチェック 設計書

作成日: 2026-04-23
対象ブランチ: `refactor/code-review-fixes` (継続)
関連設計書:
- `design-supplier-partner-ledger-balance.md` (Phase A/B/B''(light) 累積残基盤)
- `design-accounts-payable-ledger.md` (買掛帳画面)
- `design-integrity-report.md` (整合性検出、軸 B+C)

---

## 1. 背景と目的

### 軸 D: supplier 累積残一覧
整合性レポート (軸 B+C) は期間 delta の突合のみで、「**現時点で supplier ごとの買掛金累積残がいくらで、MF 累積残と合っているか**」を 1 画面で見られない。
- 既存: 1 supplier の月次推移は買掛帳画面で見られる
- 既存: 全 supplier の期間 delta は整合性レポートで見られる
- **欠落**: 全 supplier の累積残 (opening + Σ change − Σ payment_settled) の一覧

### 軸 E: MF 連携ヘルスチェック
MF 連携の状態を確認するのに 4 画面 (MF 連携状況 / 整合性レポート / 買掛金一覧 / 買掛帳) を行き来する必要がある。
- 管理者が朝一で「今 MF 連携は健全か?」を 1 画面で把握できるダッシュボードが欲しい

---

## 2. スコープ

### 軸 D: `/finance/accounts-payable-ledger/supplier-balances`
- 全 supplier の (selfBalance, mfBalance, diff, status) を 1 行で一覧表示
- `asOfMonth` パラメータで「選択月の 20 日締め時点」を指定可能 (デフォルト: 最新月)
- 行クリック → 買掛帳画面 drill-down
- MF journals キャッシュ共有で 2 回目以降は高速

### 軸 E: `/finance/mf-health`
- MF OAuth 状態 / token 期限
- 最新の整合性サマリ (mfOnly / selfOnly / mismatch)
- 未検証サマリ件数 / アノマリー内訳
- MF journals キャッシュ状態 (shop × 月数 × 最古取得時刻)

---

## 3. 軸 D: 詳細仕様

### 3.1 API
```
GET /api/v1/finance/accounts-payable/supplier-balances
  ?shopNo=1
  &asOfMonth=2026-03-20     (省略時は最新月)
  &refresh=false
```

**Response**:
```json
{
  "shopNo": 1,
  "asOfMonth": "2026-03-20",
  "fetchedAt": "2026-04-23T10:00:00Z",
  "totalJournalCount": 8500,
  "rows": [
    {
      "supplierNo": 10,
      "supplierCode": "007000",
      "supplierName": "泉製紙(株)",
      "selfBalance": 2884795,
      "mfBalance": 2884794,
      "diff": 1,
      "status": "MATCH",
      "selfOpening": 1500000,
      "selfChangeCumulative": 5000000,
      "selfPaymentCumulative": 3615205,
      "mfCreditCumulative": 5000001,
      "mfDebitCumulative": 2115207,
      "mfSubAccountNames": ["泉製紙(株)"]
    }
  ],
  "summary": {
    "totalSuppliers": 51,
    "matchedCount": 30,
    "minorCount": 15,
    "majorCount": 6,
    "mfMissingCount": 0,
    "selfMissingCount": 0,
    "totalSelfBalance": 15448920,
    "totalMfBalance": 15448920,
    "totalDiff": 0
  }
}
```

### 3.2 判定ロジック (整合性レポートと同じ閾値・同じ label)
- `MATCH`: |diff| ≤ ¥100
- `MINOR`: ¥100 < |diff| ≤ ¥1,000
- `MAJOR`: |diff| > ¥1,000
- `MF_MISSING`: self あり MF なし
  - `masterRegistered=false`: mf_account_master 未登録
  - `masterRegistered=true`: master 登録あるが期間内 journals 0
  - UI 側で `masterRegistered` フラグにより文言を分岐
- `SELF_MISSING`: MF あり self なし (自社 supplier 未登録 or summary 未計上)

**金額単位**: 全て **税込・整数円・BigDecimal (scale=0, RoundingMode.HALF_UP)**。

### 3.3 データソース
- **selfBalance** (asOfMonth 時点の税込累積残):
  - `t_accounts_payable_summary` WHERE shop_no = X AND transaction_month = asOfMonth
  - 税率別行を supplier × asOfMonth で合算、各行は `PayableBalanceCalculator.closingBalanceTaxIncluded(row)` で算出 (既存ロジック流用、手動確定行は verifiedAmount 優先)
  - 存在しない supplier は selfBalance=0 として、MF 側のみ行として `SELF_MISSING` 分類
- **selfOpening / selfChangeCumulative / selfPaymentCumulative** (診断値、期間分解):
  - `selfOpening = 期首月 (2025-05-20) の openingBalanceTaxIncluded` (supplier 単位)
  - `selfChangeCumulative = Σ PayableBalanceCalculator.effectiveChangeTaxIncluded(row)` (期首〜asOfMonth、supplier 単位)
  - `selfPaymentCumulative = Σ paymentAmountSettledTaxIncluded` (期首〜asOfMonth、supplier 単位)
  - 恒等式: `selfBalance == selfOpening + selfChangeCumulative − selfPaymentCumulative`
- **mfBalance** (asOfMonth 時点の supplier 単位累積 delta):
  - MF /journals を「期首 (2025-05-20) 〜 asOfMonth」で取得 (`MfJournalCacheService.getOrFetch` 経由)
  - 買掛金 sub_account を supplier 単位に集約、`Σ credit − Σ debit` の cumsum を返す
  - **制約**: MF /journals には期首前 (2025-05-20 以前) の残高情報が含まれない (trial_balance_bs は sub_account 粒度無し、Phase 0 スパイクで確定)。supplier 単位の「期首前繰越」は取得不可
  - **対称性**: 自社側も期首月 openingBalance を個別に加算するため、**期首以降の delta (期首月以降のΣ) のみで突合**される。期首前の繰越差は diff として表出しない
  - 期首月 opening が 0 でない supplier (既存繰越あり) は、その opening が self 側のみに現れるため期首月の単月比較では差が出るが、本 endpoint は asOfMonth 時点の累積差のみを返す。詳細は buying ledger 画面の月次推移で確認
- **supplier 紐付け**:
  - `mf_account_master.search_key == payment_supplier_code` (account_name="買掛金", financial_statement_item="買掛金" に限定)
  - 既存 `AccountsPayableIntegrityService` と同じロジック、helper を切り出して共有化

### 3.4 期間
- fromMonth は **MF 会計期首 (2025-05-20)** 固定
- toMonth = asOfMonth
- fiscal year 境界は `MfJournalFetcher.buildStartDateCandidates` (30 候補 fallback) に一任
- Response に `mfStartDate`: `MfJournalFetcher.FetchResult.actualStart` を含め、実採用開始日を UI に返す (透明性確保)

### 3.5 asOfMonth 省略時の挙動
- `findLatestMonth(shopNo)` で `t_accounts_payable_summary` の最大 transaction_month を取得
- summary が空の shop の場合: 200 で `{rows: [], asOfMonth: null, summary: {totalSuppliers: 0, ...}}` を返す (404 ではない、UI で「データがありません」表示)
- asOfMonth が 20 日締めでない場合: 400 返却 (`dayOfMonth != 20` バリデーション)

### 3.6 性能見積もり (supplier 集計込み)
- 初回 (期首〜現時点 11 ヶ月): journals ~8,500 件、5-10 秒 (大半は MF API fetch)
- 2 回目 (キャッシュ hit): **< 500ms** (MF API 通信なし + supplier × 月 の Map index 化 O(journals + supplier × subAccount) で集計)
- supplier 集計は `subAccountName → MonthBucket` の Map 事前構築で O(1) lookup にする (既存 `AccountsPayableIntegrityService` と同構造)

---

## 4. 軸 E: 詳細仕様

### 4.1 API
```
GET /api/v1/finance/mf-health?shopNo=1
```

**Response**:
```json
{
  "checkedAt": "2026-04-23T10:00:00Z",
  "mfOauth": {
    "connected": true,
    "tokenExpiresAt": "2026-04-30T12:00:00Z",
    "scope": "mfc/accounting/data.read mfc/accounting/report.read",
    "expiresInHours": 168
  },
  "summary": {
    "latestMonth": "2026-03-20",
    "totalCount": 1800,
    "verifiedCount": 1650,
    "unverifiedCount": 150,
    "mfExportEnabledCount": 1600,
    "shopNo": 1
  },
  "anomalies": {
    "negativeClosingCount": 2,
    "unverifiedCount": 150,
    "verifyDiffCount": 5,
    "continuityBreakCount": 0,
    "monthGapCount": 0
  },
  "cache": {
    "cachedShops": [
      {
        "shopNo": 1,
        "monthsCount": 12,
        "oldestFetchedAt": "2026-04-23T09:55:00Z",
        "newestFetchedAt": "2026-04-23T10:00:00Z"
      }
    ]
  }
}
```

### 4.2 集計ロジック
- **mfOauth**: `MMfOauthClient` から token expires_at 計算 (既存 `MfOauthService` から取得)
- **summary**: `t_accounts_payable_summary` 最新月の集計 (shop 指定)
- **anomalies**: 既存 `PayableLedgerService` の anomaly 検出と**重複実装しない**。共通 util `PayableAnomalyCounter.count(shopNo)` を新設 (既存買掛帳画面ロジックを静的呼び出し可能な形に切り出し)、集計結果を以下の形で返す:
  - `negativeClosingCount`: closing < 0 の月数
  - `unverifiedCount`: 最新月で verification_result = 0 の行数
  - `verifyDiffCount`: VERIFY_DIFF anomaly 数 (買掛帳と同じ閾値)
  - `continuityBreakCount`, `monthGapCount`: 既存 anomaly code 準拠
- **cache**: `MfJournalCacheService.getStats()` (non-synchronized、ConcurrentHashMap snapshot read) → `List<ShopStats>` を返す

### 4.3 ヘルス判定 (UI 側)
- 🔴 要対応: token 期限切れ OR negativeClosingCount > 0 OR anomaly 合計 > 10
- 🟡 注意: token 残り 24 時間以内 OR unverifiedCount > 0 OR anomaly 合計 > 0
- 🟢 健全: 上記以外 (token 有効 AND anomaly=0 AND unverified=0)

※ unverified は当月分を運用上「確定前」と見なすため軽い警告扱い、negative は計算エラー / 値引超過の可能性があるため重度警告扱い。

---

## 5. バックエンド実装

### 5.1 新規 Service
- `backend/.../domain/service/finance/SupplierBalancesService.java`
  - `generate(shopNo, asOfMonth, refresh) : SupplierBalancesResponse`
  - 自社 summary 集計 + MF /journals 累積 + 突合 + 判定
- `backend/.../domain/service/finance/MfHealthCheckService.java`
  - `check(shopNo) : MfHealthResponse`
  - 上記集計を 1 メソッドにまとめる

### 5.2 MfJournalCacheService 拡張
```java
public record CacheStats(List<ShopStats> shops) {}
public record ShopStats(Integer shopNo, int monthsCount, Instant oldest, Instant newest) {}
public CacheStats getStats() { ... }
```

### 5.3 DTO
- `backend/.../dto/finance/SupplierBalancesResponse.java`
  - `SupplierBalanceRow`, `SupplierBalancesSummary` inner class
- `backend/.../dto/finance/MfHealthResponse.java`
  - `MfOauthStatus`, `SummaryStats`, `AnomalyStats`, `CacheStats` inner class

### 5.4 Repository 追加メソッド
```java
// TAccountsPayableSummaryRepository
List<TAccountsPayableSummary> findByShopNoAndTransactionMonth(Integer shopNo, LocalDate month);
@Query("SELECT COUNT(s) FROM TAccountsPayableSummary s WHERE s.shopNo = :shopNo AND s.closingBalanceTaxIncluded < 0")
long countNegativeClosings(@Param("shopNo") Integer shopNo);
@Query("SELECT COUNT(s) FROM TAccountsPayableSummary s WHERE s.shopNo = :shopNo AND s.verificationResult = 0")
long countUnverified(@Param("shopNo") Integer shopNo);
@Query("SELECT MAX(s.transactionMonth) FROM TAccountsPayableSummary s WHERE s.shopNo = :shopNo")
Optional<LocalDate> findLatestMonth(@Param("shopNo") Integer shopNo);
```

### 5.5 Controller
```java
@GetMapping("/accounts-payable/supplier-balances")
public ResponseEntity<?> getSupplierBalances(
    @RequestParam Integer shopNo,
    @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate asOfMonth,
    @RequestParam(defaultValue = "false") boolean refresh) {
  assertShopAccess(shopNo);  // shop 越境防御
  // asOfMonth != null なら day != 20 で 400、null なら findLatestMonth() で決定
  // 401/403/422 エラーハンドリングは既存 endpoint と同じ
}

@GetMapping("/mf-health")
public ResponseEntity<?> getMfHealth(@RequestParam Integer shopNo) {
  assertShopAccess(shopNo);
  // MF OAuth 状態、集計、anomaly、cache stats
}

@PostMapping("/mf-health/cache/invalidate")
public ResponseEntity<Void> invalidateCache(@RequestParam Integer shopNo) {
  assertShopAccess(shopNo);
  mfJournalCacheService.invalidateAll(shopNo);
  return ResponseEntity.noContent().build();
}
```

---

## 6. フロントエンド実装

### 6.1 新規 Page
- `app/(authenticated)/finance/accounts-payable-ledger/supplier-balances/page.tsx`
- `app/(authenticated)/finance/mf-health/page.tsx`

### 6.2 新規 Component
- `components/pages/finance/supplier-balances.tsx` (一覧 + フィルタ + ソート)
- `components/pages/finance/mf-health.tsx` (ダッシュボード)

### 6.3 新規 Type
- `types/supplier-balances.ts`
- `types/mf-health.ts`

### 6.4 UI 仕様
**軸 D (supplier-balances.tsx)**:
- 検索フォーム: ショップ / asOfMonth (month input) / [整合性チェック] [最新取得] ボタン
- Summary カード: 5 タイル (MATCH / MINOR / MAJOR / MF_MISSING / SELF_MISSING) + totalSelfBalance / totalMfBalance / totalDiff
- テーブル: supplier | selfBalance | mfBalance | diff | status badge | 詳細列
- 行クリック → 買掛帳画面 drill-down (supplier=X, fromMonth=asOfMonth-12months, toMonth=asOfMonth)
- status フィルタ (MATCH 除外トグル / MAJOR のみトグル等)

**軸 E (mf-health.tsx)**:
- 全体ヘルス判定 (🟢/🟡/🔴) を大きく表示
- 4 カード並列:
  - MF OAuth: 接続状態 / scope / 有効期限 (残り時間)
  - サマリ: total / verified / unverified / mfExportEnabled
  - アノマリー: negativeClosing / verifyDiff / continuityBreak / monthGap
  - キャッシュ: shop × 月数 × 最古 fetchedAt / キャッシュクリアボタン
- リンク: 各指標から詳細画面 (整合性レポート / 買掛金一覧 / 買掛帳) に遷移

### 6.5 Sidebar 追加
`components/layout/Sidebar.tsx` の親メニュー「買掛帳」(`/finance/accounts-payable-ledger`) **配下の子メニュー**として以下を追加:
- `累積残一覧` → `/finance/accounts-payable-ledger/supplier-balances`
- `整合性レポート` → `/finance/accounts-payable-ledger/integrity` (既存、親子整理のため明記)

別階層 (MF 系メニュー配下) に:
- `MF 連携ヘルスチェック` → `/finance/mf-health`

**注意**: Sidebar の `isMenuActive` ロジック (親子 path 衝突対応) に準拠。`/finance/accounts-payable-ledger` と `/finance/accounts-payable-ledger/supplier-balances` の両方を開いても、現在画面に対応する子メニューだけが active になるよう検証する (MEMORY.md の Sidebar isMenuActive Logic 参照)。

---

## 7. テスト計画 (要点のみ)

### 7.1 バックエンド unit test
- `SupplierBalancesService.generate()`:
  - self のみ存在 → MFM_SELF
  - MF のみ存在 → MFM_MF
  - 両方存在・MATCH / MINOR / MAJOR 各判定
  - asOfMonth が最新月以降 → `findLatestMonth` fallback
- `MfHealthCheckService.check()`:
  - OAuth 未接続状態
  - anomaly 検出精度
  - cache stats 集計

### 7.2 統合テスト (curl smoke)
- `/supplier-balances` 200 + summary 内容整合
- `/mf-health` 200 + 各フィールド存在
- `refresh=true` でキャッシュ再取得
- 401/403 エラーハンドリング

### 7.3 E2E (Playwright)
- `/supplier-balances` 画面で行クリック → 買掛帳遷移
- `/mf-health` 画面で健全性アイコン表示
- 「キャッシュクリア」ボタン → 再整合性チェックで API 通信発生

---

## 8. 実装順序

1. **軸 D バックエンド** (Service + Controller + DTO + Repository) — 3h
2. **軸 D フロント** (Page + Component + Type + Sidebar) — 2h
3. **軸 E バックエンド** (MfJournalCacheService 拡張 + Service + Controller + DTO) — 2h
4. **軸 E フロント** (ダッシュボード + Sidebar) — 2h
5. **コードレビュー (subagent)** + 修正 — 1h
6. **smoke test (curl + UI)** — 30min
7. **commit** — 1 つにまとめる

---

## 9. 非スコープ (明示的除外)
- 売掛金 (軸 D' = Phase C) は対象外
- キャッシュの永続化 (DB 化) は不要
- 軸 E のアラート通知 (Slack / email) は不要
- 過去の整合性レポート履歴保存は不要 (毎回 on-demand)

---

## 10. 設計上の決定事項
- MF /journals の fromMonth は常に期首 (2025-05-20) 固定
- 軸 D は asOfMonth 時点での closing で比較 (累積差の時点スナップショット)
- 軸 E は shopNo 1 スコープ (現在 B-CART は shop=1 計上)
- ダッシュボードの自動更新は手動リロードのみ (WebSocket / polling は不要)
