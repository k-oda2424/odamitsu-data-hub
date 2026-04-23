# 買掛帳 整合性検出機能 (軸 B + 軸 C 統合) 設計書

作成日: 2026-04-22
対象ブランチ: `refactor/code-review-fixes`
関連設計書:
- `design-accounts-payable-ledger.md` (買掛帳画面)
- `design-supplier-partner-ledger-balance.md` (累積残管理)
- `design-phase-b-prime-payment-settled.md` (Phase B' T 勘定化)

---

## 1. 背景と目的

既存の買掛帳画面 (`/finance/accounts-payable-ledger`) で 1 仕入先の月次推移 + MF 比較は見えるが、以下 3 点が一画面で答えられない:

1. **「MF 側の買掛帳と自社が合っているか」**: 金額一致の可視化
2. **「連携漏れはないか」**: MF にあって自社に無い (or 逆) の特定
3. **「どの月・どの仕入先でズレたか」**: 全 supplier 分の diff 一覧

### 既存の関連実装
- `MfJournalReconcileService.reconcile()`: 当月 1 日分の仕訳を PURCHASE/SALES/PAYMENT で集計、件数・金額 diff は返すが仕訳レベルのペアマッチングはしない
- `MfSupplierLedgerService.getSupplierLedger()`: 1 supplier 単位で期間の journals 取得、月次 credit/debit/delta を返す
- `AccountsPayableLedgerService.getLedger()`: 自社 summary を supplier×月 で集約、anomaly (UNVERIFIED, VERIFY_DIFF, NEGATIVE_CLOSING, MONTH_GAP 等) 検出

### 目的
- **軸 B**: 買掛帳の既存 row に「MF 比較結果の一致状態バッジ」を追加
- **軸 C**: 期間+shopNo で全 supplier を一括診断する `/integrity-report` endpoint + 専用ページ新設

---

## 2. スコープ

### 2.1 MUST (今回実装)
1. `GET /api/v1/finance/accounts-payable/integrity-report?shopNo=X&fromMonth=Y&toMonth=Z`
   - 期間内の全 supplier を一括診断し、mfOnly / selfOnly / amountMismatch の 3 カテゴリに分類
   - 1 回の MF /journals 取得 (約 5,000 件) で全 supplier を分類、重複 fetch 回避
2. 新ページ `/finance/accounts-payable-ledger/integrity?shopNo=X&fromMonth=Y&toMonth=Z`
   - サマリカード + 3 タブ切替 (MF 側のみ / 自社側のみ / 金額差)
3. 既存買掛帳画面の anomaly バッジ拡張
   - 既存 MFX を細分化: 🟢 (一致) / 🟡 MFA (金額差、丸め超) / 🔴 MFM (件数差 = 連携漏れ) / ⚫ MFN (MF 不明)
   - サマリ部分に「連携漏れ: MF 側 N 件 / 自社側 M 件」一行表示 + /integrity ページへのリンク

### 2.2 SHOULD (優先度中、今回可能な範囲)
- サーバー側キャッシュなし (ユーザー却下済)
- 期間デフォルト 6 ヶ月、最大 12 ヶ月 (24 ヶ月は重すぎる)
- 既存 `/ledger/mf` と内部で journals 取得ロジックを共有 (コード重複を避ける)

### 2.3 OUT-OF-SCOPE
- 仕訳レベル journal-to-journal の 1:1 ペアマッチング (同月同 supplier の複数仕訳を 1 本ずつ対応付ける複雑アルゴリズム)
  - 代わりに supplier × 月 単位での金額差・件数差で判定
- CSV/Excel エクスポート
- 自動修正 (MF 側手動入力仕訳の削除等)

---

## 3. 判定ロジック (ultrathink で検討)

### 3.1 基本単位: (supplierNo, transactionMonth) per pair

既存実装の制約:
- 自社側: `t_accounts_payable_summary` は (shop, supplier, transaction_month, tax_rate) を PK。月×supplier で集約可能
- MF 側: journals を取得し、branch.accountName=="買掛金" で supplier を sub_account_name で特定、月で bucketing
- **1:1 仕訳ペアリングは非現実的** (自社側は集約値で per-journal の情報を持たないため)

→ **月 × supplier 単位での delta 比較** を採用

### 3.2 per (supplier, month) cell の判定 (レビュー R1, R2, R5, R7, R11 反映)

```
self_delta = change_tax_included - payment_settled_tax_included (税率別合算、Phase B' 定義)
mf_delta   = credit_in_month - debit_in_month (MfSupplierLedgerService の logic 再利用)
self_rows  = 当該 supplier×月 の t_accounts_payable_summary 行数 (税率別)
mf_branches = 当該 supplier×月 の MF branch 数 (買掛金 account で matched sub_account)

判定優先度:
0. NO_ACTIVITY   : self_rows == 0 AND mf_branches == 0 → スキップ (Entry 出さず、Summary カウント外) (R1 反映)
1. ⚫ MF_UNMATCHED: supplier 単位で mf_account_master に登録無し (供給側で MF 側 sub_account 解決できない) → UnmatchedSupplierEntry に格納 (supplier 単位、row バッジではなく画面上部バナー / R11 反映)
2. 🔴 MF_MISSING  : self_rows > 0 AND mf_branches == 0 → SelfOnlyEntry
                   (Phase B' の payment-only 行で change=0/paymentSettled>0 ケースも含む、R2 反映: "行が存在する" ベースで判定)
3. 🔴 SELF_MISSING: self_rows == 0 AND mf_branches > 0 → MfOnlyEntry
                   (MF 側のみ branch あり、R2 反映: "branch が存在する" ベース)
4. 🔴 AMOUNT_DIFF_MAJOR: self_rows>0 AND mf_branches>0 AND |self_delta - mf_delta| > ¥1,000 → AmountMismatchEntry (severity=MAJOR, color=red)
5. 🟡 AMOUNT_DIFF_MINOR: self_rows>0 AND mf_branches>0 AND ¥100 < |self_delta - mf_delta| ≤ ¥1,000 → AmountMismatchEntry (severity=MINOR, color=amber)
6. 🟢 MATCH          : self_rows>0 AND mf_branches>0 AND |self_delta - mf_delta| ≤ ¥100 (Entry 出さず、Summary カウント外)

閾値 (R5 反映):
- ¥100 以下: MATCH (丸め誤差、既存 SmilePaymentVerifier 準拠)
- ¥100 < diff ≤ ¥1,000: AMOUNT_DIFF_MINOR (軽微、要確認)
- ¥1,000 < diff: AMOUNT_DIFF_MAJOR (重大、要対応)

UI バッジ (R7 反映):
- MFA (amber): MINOR
- MFA! (red): MAJOR
- MFM (red): MF_MISSING / SELF_MISSING 共通 (連携漏れ)
- MFN: row バッジから削除、supplier 単位バナーで表示 (R11 反映)

符号 (R10 反映):
- totalSelfOnlyAmount, totalMfOnlyAmount, totalMismatchAmount は絶対値で集計
```

### 3.3 期間トータル の集約 Summary

```
supplierCount        : 期間中に self or MF どちらかに出現した supplier 数
mfOnlyCount          : SELF_MISSING と判定された (supplier, month) ペア数
selfOnlyCount        : MF_MISSING と判定された (supplier, month) ペア数
amountMismatchCount  : AMOUNT_DIFF (|diff| > 100) のペア数
totalMfOnlyAmount    : Σ SELF_MISSING の mf_delta
totalSelfOnlyAmount  : Σ MF_MISSING の self_delta
totalMismatchAmount  : Σ AMOUNT_DIFF の |diff|
```

### 3.4 MfSupplierLedgerService との差分 (ultrathink)

既存 `/ledger/mf` は単一 supplier 版。`/integrity-report` は全 supplier 版。

共通化ポイント:
- `fetchAllJournals()`, `buildStartDateCandidates()`, `resolveSubAccountNames()`, `toClosingMonthDay20()` は共有
- 既存 service に「全 supplier 版」method を追加 or 新 service `AccountsPayableIntegrityService` を切り出し

→ **新 service `AccountsPayableIntegrityService`** を切り出す (責務分離、既存 service の可読性維持)。既存 helper は package-private に昇格して共有。

---

## 4. Backend API 設計

### 4.1 `GET /api/v1/finance/accounts-payable/integrity-report`

**Query**:
- `shopNo: Integer` (必須, assertShopAccess)
- `fromMonth: yyyy-MM-dd` (必須, 20 日締め日)
- `toMonth: yyyy-MM-dd` (必須, 20 日締め日)

**期間制限**: 最大 **12 ヶ月** (24 ヶ月は journals 10,000 件超の可能性、通常運用で不要)

**Response**: `IntegrityReportResponse` (下記 §7)

**エラー**:
- 401: 未認証
- 403: 非 admin の他 shop 指定
- 400: 期間不正 / 12 ヶ月超
- 500 系: MF 認証切れ / scope 不足 (MfReAuthRequiredException / MfScopeInsufficientException を 401/403 に変換)

### 4.2 パフォーマンス設計

- MF journals 取得: 1 回 (期間 6 ヶ月で 2,500 件、12 ヶ月で 5,000 件)
- supplier 解決: `mf_account_master.findAll()` 1 回 (数百件)
- 自社 summary 取得: 期間内の全 row 1 回
- 処理: メモリ内で supplier × 月 cell を構築

全体 10 秒以内の所要時間目安。既存 `/ledger/mf` の fiscal year fallback + 350ms sleep + 429 retry は全て流用。

---

## 5. Service 設計

### 5.1 新規 `AccountsPayableIntegrityService`

```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountsPayableIntegrityService {
    private static final int MAX_PERIOD_MONTHS = 12;
    private static final BigDecimal MATCH_TOLERANCE = BigDecimal.valueOf(100);

    private final MfSupplierLedgerService mfSupplierLedgerService;  // helper 共有
    private final MPaymentSupplierService paymentSupplierService;
    private final TAccountsPayableSummaryService summaryService;
    private final MfOauthService mfOauthService;
    private final MfApiClient mfApiClient;
    private final MfAccountMasterRepository mfAccountMasterRepository;

    public IntegrityReportResponse generate(Integer shopNo, LocalDate fromMonth, LocalDate toMonth) {
        // 1. 入力検証 (20 日固定 + 期間制限 12 ヶ月)
        // 2. 自社 summary を期間で取得、supplier × 月 に index
        // 3. MF /journals を期間で取得 (全 supplier 分) — MfSupplierLedgerService の helper 共有
        // 4. mf_account_master から supplier_code → sub_account_name の逆引きマップ構築
        // 5. 各 MF branch を走査して supplier × 月 の buying delta を算出
        // 6. 全 supplier × 全月 の cell を一巡:
        //    - self_delta / mf_delta を計算
        //    - 判定ルール (§3.2) に従って mfOnly/selfOnly/amountMismatch に分類
        // 7. Summary を集計して Response 返却
    }
}
```

### 5.2 共通 helper を独立クラスに切り出し (R3 反映: package-private 昇格方針を取り下げ)

既存 `MfSupplierLedgerService` の private helper (`buildStartDateCandidates`, `fetchAllJournals`, `callListJournalsWithRetry`, `toClosingMonthDay20`, `RATE_LIMIT_SLEEP_MS` 等) を**新 package-private クラス**に切り出す:

```java
// backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfJournalFetcher.java
@Component
@RequiredArgsConstructor
class MfJournalFetcher {
    private final MfApiClient mfApiClient;

    /**
     * 期間 (start_date 〜 end_date) の MF /journals を全件取得。
     * fiscal year 境界の 400 は多段 fallback、429 は指数バックオフで吸収。
     */
    List<MfJournal> fetchJournalsForPeriod(MMfOauthClient client, String accessToken,
                                              LocalDate fromMonth, LocalDate toMonth) {
        // 現行 MfSupplierLedgerService の logic をそのまま移植
    }

    /** 20 日締め月への bucket key 変換 (公開) */
    static LocalDate toClosingMonthDay20(LocalDate date) { ... }
}
```

- `MfSupplierLedgerService` は `MfJournalFetcher` を注入して使う (ロジック変更なし、呼出し先差し替えのみ)
- 新 `AccountsPayableIntegrityService` も `MfJournalFetcher` を注入
- **既存 /ledger/mf の動作を一切変更しない**

### 5.3 既存 /ledger/mf との非干渉
- `MfSupplierLedgerService.getSupplierLedger()` は helper 移譲のみで動作無改修
- 期首残扱い (sub_account 粒度無し) は現状維持

---

## 6. Controller

`FinanceController` に 1 endpoint 追加:

```java
@GetMapping("/accounts-payable/integrity-report")
public ResponseEntity<?> getIntegrityReport(
        @RequestParam("shopNo") Integer shopNo,
        @RequestParam("fromMonth") @DateTimeFormat(iso=ISO.DATE) LocalDate fromMonth,
        @RequestParam("toMonth") @DateTimeFormat(iso=ISO.DATE) LocalDate toMonth) {
    assertShopAccess(shopNo);
    try {
        return ResponseEntity.ok(integrityService.generate(shopNo, fromMonth, toMonth));
    } catch (MfReAuthRequiredException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
    } catch (MfScopeInsufficientException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage(), "requiredScope", e.getRequiredScope()));
    } catch (IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", e.getMessage()));
    }
}
```

---

## 7. DTO

```java
@Data @Builder
public class IntegrityReportResponse {
    private Integer shopNo;
    private LocalDate fromMonth;
    private LocalDate toMonth;
    private Instant fetchedAt;
    private Integer totalJournalCount;   // MF から取得した仕訳総数
    private Integer supplierCount;        // 期間内に登場した supplier 数
    private List<MfOnlyEntry> mfOnly;
    private List<SelfOnlyEntry> selfOnly;
    private List<AmountMismatchEntry> amountMismatch;
    private List<UnmatchedSupplierEntry> unmatchedSuppliers;  // MF sub_account 解決不可
    private Summary summary;

    @Data @Builder
    public static class MfOnlyEntry {
        private LocalDate transactionMonth;
        private String subAccountName;          // MF 側の仕入先名
        private BigDecimal creditAmount;         // 当月 credit 合計
        private BigDecimal debitAmount;          // 当月 debit 合計
        private BigDecimal periodDelta;          // credit - debit
        private Integer branchCount;             // 当月対応 branch 数
        private Integer guessedSupplierNo;       // mf_account_master からの逆引き (nullable)
        private String reason;                   // "MF 側手入力 or 自社取込漏れ"
    }

    @Data @Builder
    public static class SelfOnlyEntry {
        private LocalDate transactionMonth;
        private Integer supplierNo;
        private String supplierCode;
        private String supplierName;
        private BigDecimal selfDelta;            // change - payment_settled
        private Integer taxRateRowCount;
        private String reason;                   // "MF CSV 出力漏れ or 未反映"
    }

    @Data @Builder
    public static class AmountMismatchEntry {
        private LocalDate transactionMonth;
        private Integer supplierNo;
        private String supplierCode;
        private String supplierName;
        private BigDecimal selfDelta;
        private BigDecimal mfDelta;
        private BigDecimal diff;                 // self - mf
        private String severity;                 // MINOR (<=1000), MAJOR (>1000)
    }

    @Data @Builder
    public static class UnmatchedSupplierEntry {
        private Integer supplierNo;
        private String supplierCode;
        private String supplierName;
        private String reason;                   // "mf_account_master に登録なし"
    }

    @Data @Builder
    public static class Summary {
        private Integer mfOnlyCount;
        private Integer selfOnlyCount;
        private Integer amountMismatchCount;
        private Integer unmatchedSupplierCount;
        private BigDecimal totalMfOnlyAmount;
        private BigDecimal totalSelfOnlyAmount;
        private BigDecimal totalMismatchAmount;
    }
}
```

---

## 8. 軸 B: 買掛帳画面の一致状態バッジ拡張

### 8.1 現状 UI (accounts-payable-ledger.tsx)
既に `mfLedger` を別 fetch して `mfDeltaByMonth` でマージ表示。`mfMismatch` (|diff| > 10,000) のとき MFX バッジ。

### 8.2 変更点
`mfMismatch` 判定を 4 段階に細分化:

```typescript
function mfMatchStatus(selfDelta: number, mfDelta: number | null, matchedSubAccountsEmpty: boolean): {
  code: 'MATCH' | 'AMOUNT_DIFF' | 'MF_MISSING' | 'SELF_MISSING' | 'MF_UNMATCHED' | null;
  label: string;
  color: 'emerald' | 'amber' | 'red' | 'slate';
} {
  if (matchedSubAccountsEmpty && selfDelta !== 0) {
    return { code: 'MF_UNMATCHED', label: 'MFN', color: 'slate' };
  }
  if (mfDelta === null) return { code: null, label: '', color: 'slate' };
  if (selfDelta === 0 && mfDelta !== 0) {
    return { code: 'SELF_MISSING', label: 'MFM', color: 'red' };
  }
  if (selfDelta !== 0 && mfDelta === 0) {
    return { code: 'MF_MISSING', label: 'MFM', color: 'red' };
  }
  const diff = Math.abs(selfDelta - mfDelta);
  if (diff <= 100) return { code: 'MATCH', label: '', color: 'emerald' };  // 一致
  if (diff <= 1000) return { code: 'AMOUNT_DIFF', label: 'MFA', color: 'amber' };
  return { code: 'AMOUNT_DIFF', label: 'MFA!', color: 'amber' };  // Major
}
```

### 8.3 サマリヘッダに追加
既存サマリ card に「連携漏れ」列を追加:
```
| MF 側のみ N 件 | 自社側のみ M 件 | 金額差 K 件 | [整合性レポートへ →] |
```

`/finance/accounts-payable-ledger/integrity?shopNo=...&fromMonth=...&toMonth=...` に遷移するリンク。

---

## 9. 軸 C: 整合性レポート画面

### 9.1 新ページ `/finance/accounts-payable-ledger/integrity`

ファイル:
- `frontend/app/(authenticated)/finance/accounts-payable-ledger/integrity/page.tsx`
- `frontend/components/pages/finance/integrity-report.tsx`

### 9.2 UI 構成
```
PageHeader: 「買掛帳 整合性レポート」

検索フォーム:
  [ショップ] [開始月] [終了月] [再取得ボタン]
  (デフォルト: 当月含む過去 6 ヶ月、最大 12 ヶ月)

サマリカード:
  MF 側のみ: N 件 (金額 ¥X)
  自社側のみ: M 件 (金額 ¥Y)
  金額差: K 件 (合計差 ¥Z)
  MF 未登録 supplier: L 件

タブ (shadcn Tabs):
  [MF 側のみ] [自社側のみ] [金額差] [MF 未登録]

各タブで DataTable (shadcn):
  - MF 側のみ: 月 | sub_account | credit | debit | delta | 推定 supplier | 備考
  - 自社側のみ: 月 | supplier コード | 仕入先名 | self_delta | 税率行数 | 備考
  - 金額差: 月 | 仕入先 | self | MF | diff | severity
  - MF 未登録: supplier コード | 仕入先名 | 備考

各 row クリック → 買掛帳画面に遷移 (?supplierNo=X で pre-fill)
```

### 9.3 既存買掛帳画面に link 追加
サマリ card 内に「整合性レポートへ →」ボタン。

---

## 10. 未確定事項 (設計で握る)

| # | 論点 | 決定 |
|---|---|---|
| 1 | MF MISSING / SELF MISSING の閾値 (self_delta == 0 の境界) | `BigDecimal.ZERO.compareTo() == 0` で厳密に判定 (¥1 以内は一致扱い) |
| 2 | unmatchedSuppliers をどこに出すか | 別リスト `unmatchedSuppliers` として Response に追加、UI で別タブ |
| 3 | mfOnly の supplier 特定 (sub_account_name のみでは supplierNo 不明) | `mf_account_master.search_key` からの逆引きで推定 supplierNo を提供 (guessedSupplierNo) |
| 4 | 期間デフォルト | 過去 6 ヶ月 (今月含む) |
| 5 | 大量データ時のタイムアウト | サーバー側で 30 秒以内想定、MF journals 取得は既存 safeguard 50 pages / 429 retry 流用 |
| 6 | 既存の `/ledger/mf` 画面との使い分け | `/ledger/mf` = 1 supplier 詳細、`/integrity-report` = 全 supplier 俯瞰。補完関係で両方残す |

---

## 11. 実装順序 (レビュー R3, R9 反映)

1. **DTO 新設**: `IntegrityReportResponse.java` (+ 内部 record 群)
2. **Repository method 新設** (R9): `TAccountsPayableSummaryRepository.findByShopNoAndTransactionMonthBetween...` (supplier 指定無し、期間全 row)
3. **共通 helper 切り出し** (R3): `MfJournalFetcher` 新設、`MfSupplierLedgerService` から移譲
4. **Service 新設**: `AccountsPayableIntegrityService.java`
   - `MfJournalFetcher` を DI
   - supplier×月 cell 構築 + 判定ロジック (§3.2)
   - mf_account_master 逆引き map 構築 (§5.1 step 4)
5. **Controller endpoint 追加**
6. **コンパイル確認** (+ /ledger/mf の動作が壊れていないこと確認)
7. **Frontend types**: `integrity-report.ts`
8. **買掛帳画面拡張**: mfMatchStatus 判定、サマリヘッダリンク、MFN バナー化
9. **新ページ**: `/finance/accounts-payable-ledger/integrity`
10. **typecheck 確認**
11. **バックエンド再起動** → curl で動作確認 (カミ商事 MATCH / 太幸 SELF_MISSING / やしき SELF_MISSING 等)
12. **整合性レポート動作** を browser で目視
13. **DOC 更新 + commit**

---

## 12. テスト観点

### Unit (AccountsPayableIntegrityService)
- 自社 only supplier → SelfOnlyEntry に分類
- MF only supplier (例: 太幸) → MfOnlyEntry に分類、guessedSupplierNo 解決済
- ペアあり差額 100 円以下 → MATCH (Entry 無し)
- ペアあり差額 100〜1000 → AMOUNT_DIFF MINOR
- ペアあり差額 1000 超 → AMOUNT_DIFF MAJOR
- mf_account_master に登録無し supplier → UnmatchedSupplierEntry
- supplier 数 = 自社 + MF ペア の and-合計

### Integration
- 期間 12 ヶ月超で 400
- 非 admin の他 shop → 403
- MF 認証切れ → 401

### E2E (Playwright)
- /integrity ページ表示、タブ切替
- 各 row → 買掛帳画面遷移
- 既存買掛帳の MFA/MFM バッジ表示

---

## 13. リスクと軽減

| リスク | 軽減策 |
|---|---|
| 12 ヶ月処理の所要時間 | 既存 /ledger/mf と同じ 8-15 秒程度、UI は Skeleton |
| MF journals が 10,000 超 (期間長・取引多) | 既存 50 pages safeguard |
| mfOnly の supplier 特定失敗 | guessedSupplierNo null でフロント表示 |
| 既存 /ledger/mf 動作への影響 | package-private helper 昇格のみ、ロジック変更なし |

---

## 14. 既存機能への影響

| 機能 | 影響 | 対処 |
|---|---|---|
| `/ledger` | 無改修 | - |
| `/ledger/mf` | helper 共有のため package-private 化 | 動作変更なし |
| `MfJournalReconcileService` | 無改修 | - |
| `MfBalanceReconcileService` | 無改修 | - |
| 買掛帳 UI | mfMatchStatus 判定細分化 | 既存 bucket に追加バッジ |
| 買掛金一覧画面 | 無影響 | - |
