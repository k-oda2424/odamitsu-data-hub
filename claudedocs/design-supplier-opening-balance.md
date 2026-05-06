# 期首残 (supplier opening balance) 設計書

作成日: 2026-05-04
対象ブランチ: `refactor/code-review-fixes`
種別: 逆生成 (現状コードから抽出)

---

## 1. 目的と業務上の意味

### 1.1 概要
本機能は、マネーフォワード (MF) 会計の **期首残高仕訳 (opening balance journal)** から、
仕入先 (supplier) 単位の前期繰越残高を取り込み、自社 DB (`m_supplier_opening_balance`) に保持する。
取り込んだ残高は買掛金管理系の以下機能で **累積残の初期値 (opening)** として注入される。

- 買掛帳 (`/finance/accounts-payable-ledger`)
- supplier 累積残 (`/finance/accounts-payable-ledger/supplier-balances`)
- 整合性レポート (`/finance/accounts-payable-ledger/integrity`)
- MF 比較ヘルスチェック

### 1.2 なぜ期首残が必要か (業務背景)

`t_accounts_payable_summary` (買掛金月次集計テーブル) は **2025-06-21 以降の SMILE/B-CART 仕入実績**
から積み上げて生成される。一方、税理士が確定した **会計上の前期末残高** は MF 上に
journal #1 (transaction_date = 2025-06-21) として既に登録済みである。

これら 2 つの数値が一致しないと、買掛帳の月初累積残が常に「自社側 0 / MF 側 ¥XX,XXX,XXX」
のずれを抱え、整合性レポートで全 supplier が `MAJOR` ずれ判定になってしまう。

そこで以下の設計ポリシーを取った:

1. MF journal #1 の credit branch を supplier 単位に分解して `m_supplier_opening_balance` に保存
2. 自社 summary 側の累積残計算時に **基準日 (2025-06-20) 時点の opening として明示注入**
3. MF 側集計では journal #1 を **二重計上防止のため accumulation から除外** する
   (ただし軸 D の `SupplierBalancesService` の `accumulateMfJournals` は方針が異なる、§7 参照)

### 1.3 MF journal #1 を特別扱いする理由

通常の支払仕訳は `debit: 買掛金 / credit: 普通預金` の形を取る。
これに対し期首残高仕訳は `debit: 期首残高 / credit: 買掛金 (sub_account=supplier 名)` の
**全 credit branch が買掛金で debit 側に買掛金が一切現れない** 構造を持つ。

journal `number` は通常 1 だが、運用で訂正仕訳が入ると別 number になる可能性があるため、
**journal number ではなく構造で判定** する (`MfJournalFetcher#isPayableOpeningJournal`、§7.1)。

### 1.4 実績値 (2026-04-24 初回取込)
- shop_no=1, opening_date=2025-06-20 で 41 supplier、合計 ¥14,705,639 を取り込み
- shop_no=2 太幸など journal #1 に未登録の supplier は手動補正で個別投入

---

## 2. スコープ

### 2.1 対象 (In Scope)
- supplier 単位の期首残 CRUD (`m_supplier_opening_balance`)
- MF /api/v3/journals 経由の自動取得 (`POST /fetch-from-mf`)
- 手動補正 (`PUT /manual-adjustment`) — journal #1 未掲載 supplier や税理士確認差分の吸収
- MF /api/v3/trial_balance_bs を使った合計検証 (`MATCH / MINOR / MAJOR / UNKNOWN`)
- 下流サービス (`SupplierBalancesService`, `MfSupplierLedgerService`, `AccountsPayableIntegrityService`)
  への opening map 提供

### 2.2 対象外 (Out of Scope)
- 買掛金以外の勘定科目 (売掛金、未払金等) の期首残
- partner (得意先) 単位の期首残 (売掛金版)
- 期首残の改廃履歴 (audit trail) — 軸 F として `claudedocs/design-audit-trail-accounts-payable.md`
  で別途設計、未実装
- 複数 fiscal year の opening_date を切り替える UI (現状 `2025-06-20` 固定の `DEFAULT_OPENING_DATE` のみ)

---

## 3. データモデル

### 3.1 Entity: `m_supplier_opening_balance`

DDL: `backend/src/main/resources/db/migration/V028__create_supplier_opening_balance.sql:12-30`
Entity: `backend/src/main/java/jp/co/oda32/domain/model/finance/MSupplierOpeningBalance.java:32-86`

| カラム | 型 | NOT NULL | 説明 |
|---|---|---|---|
| `shop_no` | INTEGER | ◯ (PK) | 対象ショップ (B-CART は 1 固定) |
| `opening_date` | DATE | ◯ (PK) | 基準日 = 20日締めバケット日 (例: 2025-06-20)。journal #1 transactionDate の前日 |
| `supplier_no` | INTEGER | ◯ (PK) | `m_payment_supplier.payment_supplier_no` |
| `mf_balance` | NUMERIC(15,0) | × | MF journal #1 から取得した税込残 (NULL = 未取得) |
| `manual_adjustment` | NUMERIC(15,0) | ◯ (default 0) | 手動補正額 (税込、signed) |
| `effective_balance` | NUMERIC(15,0) | GENERATED ALWAYS AS STORED | `COALESCE(mf_balance, 0) + manual_adjustment` |
| `source_journal_number` | INTEGER | × | 出典 MF journal 番号 (期首残高仕訳は通常 1) |
| `source_sub_account_name` | VARCHAR(200) | × | MF 側 sub_account_name (creditSub)。再取得マッチング用 |
| `last_mf_fetched_at` | TIMESTAMP | × | 直近 MF 取得日時 |
| `adjustment_reason` | VARCHAR(500) | × | 手動補正理由 (`manual_adjustment != 0` 時必須) |
| `note` | VARCHAR(500) | × | 備考 |
| `add_date_time` / `add_user_no` / `modify_date_time` / `modify_user_no` / `del_flg` | 共通監査 | ◯ | プロジェクト共通 |

**インデックス**:
`idx_supplier_opening_balance_shop_date (shop_no, opening_date)`
— 下流サービスが (shop, openingDate) で `findByPkShopNoAndPkOpeningDateAndDelFlg` する。

### 3.2 複合主キー: `MSupplierOpeningBalancePK`
`backend/src/main/java/jp/co/oda32/domain/model/embeddable/MSupplierOpeningBalancePK.java:21-31`

```java
shopNo + openingDate + supplierNo
```

### 3.3 設計上のポイント

- **`effective_balance` を GENERATED 列にした理由**: MF 取込と手動補正の足し算が
  常に DB 側で一意に決まり、アプリ側の足し忘れバグ (例: NULL 加算で NPE) を構造的に防止できる。
  `@Generated` で hibernate に書き込みを抑制 (`insertable=false, updatable=false`)。
- **`mf_balance` を NULLABLE にした理由**: shop=2 太幸のように journal #1 に存在しない
  supplier も手動補正だけで登録できる。`unmatched` フラグや UI 上の `—` 表示と整合する。
- **複合 PK に `opening_date` を含めた理由**: 将来 fiscal year 跨ぎで複数 opening 行を
  保持する余地を残す (現状 `DEFAULT_OPENING_DATE = '2025-06-20'` 固定)。

### 3.4 MF journal #1 との関連

| MF 側構造 | DB マッピング |
|---|---|
| `journal.transaction_date` (2025-06-21) | `opening_date = transaction_date - 1 day` (2025-06-20) |
| `journal.number` (通常 1) | `source_journal_number` |
| `branch.creditor.sub_account_name` | `source_sub_account_name` (マッチングキー) |
| `branch.creditor.value` | `mf_balance` |

`sub_account_name → supplier_no` の解決には `mf_account_master`
(`account_name='買掛金' AND financial_statement_item='買掛金' AND search_key=supplier_code`)
を経由する。

---

## 4. API 設計

実装: `backend/src/main/java/jp/co/oda32/api/finance/SupplierOpeningBalanceController.java`

ベースパス: `/api/v1/finance/supplier-opening-balance`
全エンドポイント `@PreAuthorize("isAuthenticated()")`、書き込み系は admin (`shopNo == 0`) 限定。

### 4.1 エンドポイント一覧

| Method | Path | 認可 | 概要 |
|---|---|---|---|
| GET | `/` | 認証必須 | (shop, openingDate) の一覧取得 + summary |
| POST | `/fetch-from-mf` | admin only | MF journal #1 を取得して upsert (手動補正は保持) |
| PUT | `/manual-adjustment` | admin only | 手動補正額の更新 |

### 4.2 GET `/`
**Query**:
- `shopNo`: Integer (必須)
- `openingDate`: LocalDate ISO 形式 (必須)

**Response**: `SupplierOpeningBalanceResponse`
(`backend/src/main/java/jp/co/oda32/dto/finance/SupplierOpeningBalanceResponse.java:16-51`)

```jsonc
{
  "shopNo": 1,
  "openingDate": "2025-06-20",
  "rows": [
    {
      "supplierNo": 12345,
      "supplierCode": "S001",
      "supplierName": "○○商事",
      "mfBalance": 1234567,
      "manualAdjustment": 0,
      "effectiveBalance": 1234567,
      "sourceJournalNumber": 1,
      "sourceSubAccountName": "○○商事",
      "lastMfFetchedAt": "2026-04-24T04:10:00Z",
      "adjustmentReason": null,
      "note": null,
      "unmatched": false
    }
  ],
  "summary": {
    "totalRowCount": 41,
    "mfSourcedCount": 41,
    "manuallyAdjustedCount": 0,
    "unmatchedCount": 0,
    "totalMfBalance": 14705639,
    "totalManualAdjustment": 0,
    "totalEffectiveBalance": 14705639,
    "mfTrialBalanceClosing": 14705639,
    "validationDiff": 0,
    "validationLevel": "MATCH"
  }
}
```

行のソートは `effectiveBalance` 降順 (`MfOpeningBalanceService.java:295`)。

### 4.3 POST `/fetch-from-mf`
**Query**: `shopNo`, `openingDate` (必須)

**Response**: `MfOpeningBalanceFetchResponse`
(`backend/src/main/java/jp/co/oda32/dto/finance/MfOpeningBalanceFetchResponse.java:17-38`)

```jsonc
{
  "shopNo": 1,
  "openingDate": "2025-06-20",
  "journalTransactionDate": "2025-06-21",
  "journalNumber": 1,
  "journalCreditSum": 14705639,
  "branchCount": 41,
  "matchedCount": 41,
  "upsertedCount": 41,
  "preservedManualCount": 0,
  "unmatchedBranches": [
    { "subAccountName": "(sub_account_name なし)", "amount": 0 }
  ],
  "mfTrialBalanceClosing": 14705639,
  "validationDiff": 0,
  "validationLevel": "MATCH",
  "fetchedAt": "2026-04-24T04:10:00Z"
}
```

**エラー**:
- `400 BAD_REQUEST`: shopNo / openingDate / userNo の欠落
- `401 UNAUTHORIZED`: MF 認証エラー (front は再認証へ誘導)
- `403 FORBIDDEN`: admin 以外の実行
- `422 UNPROCESSABLE_ENTITY`: journal #1 が見つからない (MF UI で期首残高仕訳を登録要)
- `500 INTERNAL_SERVER_ERROR`: MF クライアント未登録 (`mfOauthService.findActiveClient` 失敗)

### 4.4 PUT `/manual-adjustment`
**Body**: `SupplierOpeningBalanceUpdateRequest`
(`backend/src/main/java/jp/co/oda32/dto/finance/SupplierOpeningBalanceUpdateRequest.java:14-21`)

```jsonc
{
  "shopNo": 2,
  "openingDate": "2025-06-20",
  "supplierNo": 99999,
  "manualAdjustment": 350000,
  "adjustmentReason": "太幸 shop=2 期首残 (journal #1 未掲載)",
  "note": "税理士確認済"
}
```

**バリデーション** (`MfOpeningBalanceService.updateManualAdjustment:210-248`):
- `shopNo / openingDate / supplierNo / manualAdjustment` は `@NotNull`
- `adjustmentReason / note` は最大 500 文字
- `manualAdjustment != 0` の場合は `adjustmentReason` 必須 (`400 BAD_REQUEST`)
- `supplier` が指定 `shopNo` 配下に存在しない場合は `404 NOT_FOUND`
- `userNo` が取れない (= 未認証) 場合は `401 UNAUTHORIZED`

**Response**: 204 No Content

---

## 5. MF API 連携フロー

実装: `MfOpeningBalanceService.fetchFromMfJournalOne`
(`backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java:74-203`)

### 5.1 シーケンス

```
[Frontend (admin)]
    │  POST /finance/supplier-opening-balance/fetch-from-mf
    ▼
[SupplierOpeningBalanceController]
    │
    ▼
[MfOpeningBalanceService.fetchFromMfJournalOne]
    │
    ├─(1) MfOauthService: active client + valid access token
    │
    ├─(2) MfApiClient.listJournals(transaction_date = openingDate + 1 day, per_page=1000)
    │     ─→ List<MfJournal>
    │
    ├─(3) findOpeningJournal(journals)
    │     全 credit branch が 買掛金 かつ debit 側に 買掛金 無し → opening と判定
    │     最大 payable credit 件数を持つものを採用
    │     見つからなければ 422
    │
    ├─(4) MfApiClient.getTrialBalanceBs(openingDate)
    │     ─→ 買掛金 closing balance (検証用、失敗時は warn でスキップ)
    │
    ├─(5) buildSubToCode(): mf_account_master から sub_account_name → supplier_code map
    │     buildCodeToSupplier(shopNo): supplier_code → MPaymentSupplier map
    │
    ├─(6) for each branch in openingJournal.branches:
    │       cr.accountName == "買掛金" のみ採用
    │       sub_account_name 空 → unmatched に追加
    │       supplier 解決失敗 → unmatched に追加
    │       既存行があれば mf_balance のみ上書き、manual_adjustment は保持
    │       無ければ新規 insert (manual_adjustment=0, del_flg='0')
    │
    ├─(7) repository.flush() → sumEffectiveBalance() 再集計
    │     diff = totalEffective - mfTrialBalanceClosing
    │     classifyValidation: |diff| <= 100 → MATCH / <= 1000 → MINOR / それ以外 → MAJOR
    │
    └─(8) MfOpeningBalanceFetchResponse を返却
```

### 5.2 Journal #1 判定ロジック
`MfOpeningBalanceService.findOpeningJournal:336-363`

- `creditor.accountName == "買掛金"` かつ `creditor.subAccountName != null` を `payableCreditBranches` カウント
- `debitor.accountName == "買掛金"` を `payableDebitBranches` カウント
- `payableCreditBranches > 0 && payableDebitBranches == 0` を満たす中で
  `payableCreditBranches` が最大の journal を採用
- ※ `MfJournalFetcher.isPayableOpeningJournal:180-194` の判定 (§7.1) は
  逆に「accumulation から除外する」用途で使われる類似ロジック。
  両者ロジックが乖離しないように **TODO: 確認** (将来統合候補)

### 5.3 supplier マッピング
`MfOpeningBalanceService.buildSubToCode:365-377`

- `mf_account_master` を全件 fetch (キャッシュなし)
- `account_name = '買掛金' AND financial_statement_item = '買掛金'` でフィルタ
- `sub_account_name → search_key (= supplier_code)` を作成
- 同一 `sub_account_name` に複数 `search_key` がある場合は **先勝ち** (`putIfAbsent`)
- 同様に `supplier_code → MPaymentSupplier` も先勝ち (`buildCodeToSupplier:379-387`)

### 5.4 手動補正の保持ルール

upsert 時 (`MfOpeningBalanceService.java:144-170`):

| 既存行 | manual_adjustment 動作 |
|---|---|
| なし | `0` で初期化 |
| あり | **常に保持** (上書きなし)。signum != 0 なら `preservedManualCount++` |

`mf_balance / source_journal_number / source_sub_account_name / last_mf_fetched_at` のみ更新する。
これにより、税理士確認後に手動補正したエントリが MF 再取得で消失することを防ぐ。

> **運用上の注意 (manual 先行 → MF 後乗り)**: 太幸 (shop=2) のように、当初 SQL/UI で
> `manual_adjustment` のみを入れた supplier に対し、後から MF journal #1 取込が走ると、
> その supplier の MF 側マッピングが `m_supplier_opening_balance.mf_balance` に書き込まれて
> `effective_balance = mf_balance + manual_adjustment` が二重計上に見える可能性がある。
> 通常 MF 側に取引履歴がない supplier は MF journal #1 にも現れないため発生しないが、
> マッピング追加直後等は監査で `effective_balance` の妥当性を確認すること。

### 5.5 検証レベル
`classifyValidation:411-417`

- `MATCH`: `|diff| <= 100` 円
- `MINOR`: `|diff| <= 1000` 円
- `MAJOR`: それ以上
- `UNKNOWN`: trial_balance 取得失敗 or token 未取得

`mfTrialBalanceClosing` の取得失敗 (token 期限切れ等) は `warn` ログだけ吐いてスキップし、
fetch 自体は成功扱いにする (validationLevel = UNKNOWN)。

---

## 6. 画面設計

実装: `frontend/components/pages/finance/supplier-opening-balance.tsx`
ルート: `/finance/supplier-opening-balance`
Sidebar 登録: `frontend/components/layout/Sidebar.tsx:94` (`adminOnly: true`)

### 6.1 検索バー
`supplier-opening-balance.tsx:148-188`

- `ショップ` SearchableSelect (admin のみ)
- `基準日 (20 日締め)`: `<Input type="date">`、デフォルト `DEFAULT_OPENING_DATE = '2025-06-20'`
- `MF から取得` ボタン (admin のみ): `fetchMutation` 起動

### 6.2 サマリタイル
`supplier-opening-balance.tsx:194-217`

5 列構成:
- 登録件数 (MF 取込 / 手動補正の内訳)
- MF 合計
- 手動補正 合計
- 実効合計 (強調)
- 整合検証バッジ (`MATCH / MINOR / MAJOR / UNKNOWN`) + MF trial_balance との diff

### 6.3 一覧テーブル
`supplier-opening-balance.tsx:230-294`

| 列 | 内容 |
|---|---|
| 仕入先 | `supplier_code + supplier_name`、別名のとき `MF: ○○` 副表示 |
| MF 取得値 | `mfBalance` (NULL は `—`) |
| 手動補正 | `manualAdjustment` (0 は `—`、+/− 表示) |
| 実効値 | `effectiveBalance` 強調 |
| 出典 | MF #1 バッジ + 補正バッジ |
| 備考 | `adjustmentReason` または `note` (truncate) |
| 操作 | 編集 (admin のみ) |

`unmatched=true` 行は背景 amber-50 強調。

### 6.4 編集ダイアログ
`supplier-opening-balance.tsx:308-368`

- MF 取得値 / 手動補正 (signed) / 実効値 (リアルタイム計算) を 3 カラム表示
- 補正額が 0 でない場合は `補正理由` 必須 (フロント側でも検証)
- 備考は Textarea

### 6.5 admin 判定
- `useAuth().user.shopNo === 0` で admin 判定
- 非 admin は ショップ選択 / `MF から取得` / 編集ボタン非表示
- そもそも Sidebar メニュー自体 `adminOnly: true` で非表示

---

## 7. 累積残/買掛帳/整合性レポートへの注入

期首残は以下 3 サービスから参照される。共通 API:
`MfOpeningBalanceService.getEffectiveBalanceMap(shopNo, openingDate): Map<Integer, BigDecimal>`
(`MfOpeningBalanceService.java:323-333`)

戻り値は `supplierNo → effectiveBalance` の map。`signum != 0` のエントリのみ含む。

### 7.1 `MfJournalFetcher.isPayableOpeningJournal`
`backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfJournalFetcher.java:180-194`

```java
public static boolean isPayableOpeningJournal(MfJournal j) {
    // 全 branch を走査:
    //   - debit に 買掛金 が 1 つでもあれば false (= 通常の支払仕訳)
    //   - credit に 買掛金 が 1 つでもあれば hasPayableCredit=true
    // 最終的に hasPayableCredit のみが true → opening 判定
}
```

**役割**:
- ledger / integrity の月次 accumulation で **journal #1 を二重計上から除外**
- `m_supplier_opening_balance` は別経路 (本機能 §5) で既に取り込み済みのため

### 7.2 注入箇所マッピング

| サービス | ファイル / 行 | 期首残の使い方 | journal #1 を accumulation で扱うか |
|---|---|---|---|
| `MfSupplierLedgerService` (買掛帳) | `MfSupplierLedgerService.java:107-116` | `openingBalance` を `cumulative` の起点に注入 | **除外** (`isPayableOpeningJournal` で skip) |
| `AccountsPayableIntegrityService` (整合性レポート) | `AccountsPayableIntegrityService.java:buildSupplierCumulativeDiffMap` | `SupplierBalancesService.generate()` への委譲経由で self 側 opening 注入 (§7.2.1)。per-month delta 比較自体には opening 不要 | **除外** |
| `SupplierBalancesService` (累積残) | `SupplierBalancesService.java:128-203, 233-258` | self 側 `opening` に加算。MF 側はあえて journal #1 を **含めて** accumulation する非対称設計 (§7.3) | **含める** |

### 7.2.1 AccountsPayableIntegrityService の opening 注入 (P1-02 / DD-BGH-01)

**意思決定**: A 案 (注入する、累積残一覧と統一) — 2026-05-04 確定。

`AccountsPayableIntegrityService.buildSupplierCumulativeDiffMap` も `SupplierBalancesService` と
同じく `m_supplier_opening_balance.effectiveBalance` を注入する。これにより:

- 整合性レポート (`/finance/accounts-payable-ledger/integrity`) と
  累積残一覧 (`/finance/supplier-balances`) で同 supplier の cumulative 値が一致
- self 側にのみ opening 注入、MF 側は journal #1 skip (SF-G01) — 累積残一覧と完全に同じパターン
- 期首残ありの 41 supplier (¥14,705,639) は両画面で同じ累積残として表示される

**実装**: 整合性レポートは独自に `MfOpeningBalanceService` を constructor injection しているが、
実際の cumulative 計算は `SupplierBalancesService.generate()` への委譲で行う。両サービスとも
内部で `openingBalanceService.getEffectiveBalanceMap(shopNo, SELF_BACKFILL_START)` を呼ぶため、
返却される `supplierCumulativeDiff` map の値は累積残一覧の `diff` 列と完全に一致する。
`MfOpeningBalanceService` の直接 inject は (a) 期首残注入の契約を依存グラフ上で表面化させ、
(b) 開始時に「opening 注入確認: supplier 数=N」をログ出力して運用観測性を確保する目的。

**判定タイミング**: `asOfMonth` (= integrity の `toMonth`) 時点での
`cumulative diff = self.closing - mfBalance` (両側に opening 加算済み)。
`diff == 0` の supplier は `reconciledAtPeriodEnd=true` 扱いで UI ノイズ抑制。

**整合性レポート本体の per-month delta 計算には影響なし**: `selfDelta` と `mfDelta` は
期間内の delta 比較のみを行うため opening は不要。期首残はあくまで「期末時点の累積差」を
累積残一覧と同じ式で算出するための補助情報として entry に併記される。

### 7.3 SupplierBalancesService の非対称設計
`SupplierBalancesService.java:233-258` のコメント:

> 累積残の視点では journal #1 (期首残高仕訳) を含めることで MF 側は opening + activity を保持する。
> 自社側は `t_accounts_payable_summary` に opening が欠落しているため、buildRow 前段で
> `m_supplier_opening_balance` を `self.opening` に加算して対称性を確保する。

つまり:
- MF 側: journal #1 を含める → MF cumulative = opening + 期間 activity
- 自社側: opening を別途加算 → self cumulative = opening + summary 集計

両方に opening が乗るので diff 比較が成立する。
他 2 サービス (ledger / integrity) は self 側の opening 注入と
MF 側 `isPayableOpeningJournal` 除外の組み合わせで同じ効果を出している。

### 7.4 fallback 経路 (opening のみある supplier)
`SupplierBalancesService.java:192-204`

- self 側 summary にも MF activity にも現れないが期首残だけ持つ supplier は
  `openingMap.entrySet()` の残余をループして **opening = closing** の row を生成
- shop=2 太幸 (手動補正のみで MF/SMILE 取引履歴なし) のケースに対応

---

## 8. 既知の制約・注意事項

### 8.1 opening_date の固定値
- 期首日 `2025-06-20` は **5 箇所**で参照 (命名は異なる):
  - フロント: `DEFAULT_OPENING_DATE = '2025-06-20'` (`frontend/types/supplier-opening-balance.ts:85`)
  - バックエンド (4 箇所、すべて意味的に同値):
    - `MfSupplierLedgerService.java:104-106` (`MfPeriodConstants.SELF_BACKFILL_START`)
    - `SupplierBalancesService.java:125` (`MfPeriodConstants.SELF_BACKFILL_START`)
    - `AccountsPayableBackfillTasklet.java:88` (`MfPeriodConstants.SELF_BACKFILL_START`)
    - `MfBalanceReconcileService.java:46` (`FISCAL_OPENING_DATE = LocalDate.of(2025, 5, 20)`、こちらは MF 試算表 BS の期首前月末。意味的に近接するが日付は別)
- バックエンドは Cluster D で {@link MfPeriodConstants} に集約済。フロントの初期値は SF-G05 で
  `FinancePeriodConfig.OPENING_DATE_DEFAULT` (新規) と整合させ、将来 admin 画面から上書き可能化する余地を残す
- 将来 fiscal year 切替時は DB / config テーブル化が必要 (§9 課題 #2)

### 8.2 MF API 依存
- MF OAuth 設定 (`MMfOauthClient`) が未登録だと `IllegalStateException`
- `mf_account_master` の search_key 設定がないと sub_account → supplier マッピングが unmatched
  になり opening が取り込めない
- trial_balance_bs API が落ちると validation = UNKNOWN になる (取込自体は成功)

### 8.3 sub_account 多重マッピングは先勝ち
`buildSubToCode:374` の `putIfAbsent` により、同一 sub_account_name に複数 supplier_code が
登録されている場合は先に hit したものだけ採用される。これは fetch / list の両方で同じ動作になる
よう揃えてある。

### 8.4 `effective_balance` 列は読み取り専用
`@Generated` + `insertable=false, updatable=false`。
アプリから書き込もうとすると hibernate で無視される (バグの温床)。
書き込みは必ず `mf_balance` または `manual_adjustment` 経由で行う。

### 8.5 二つの opening 判定ロジック
- `MfOpeningBalanceService.findOpeningJournal:336-363`: 取込時に「最大 payable credit 数」
- `MfJournalFetcher.isPayableOpeningJournal:180-194`: 累積計算除外用に「全 credit に payable + debit に payable 無し」

両者ロジックの厳密性が異なる。例えば `findOpeningJournal` は debit に買掛金が無いことを
確認するが credit 側に他科目が混じっても採用する一方、`isPayableOpeningJournal` は
credit に他科目があっても (buying 側に payable があれば) opening 判定する。
**TODO: 確認** — 共通 util への統合 / regression テスト

### 8.6 admin only 制約
- 取込・編集系は `@PreAuthorize("authentication.principal.shopNo == 0")` で admin 限定
- 一覧 (GET) は認証ユーザー全員が閲覧可

### 8.7 spike 痕跡
`claudedocs/_spike_mf_opening_0621.json` / `_spike_mf_opening_wide.json` が残存。
本機能設計検討時の MF レスポンスサンプル。**TODO: 確認** — 不要なら削除候補。

---

## 9. 課題 / TODO

| # | 種別 | 内容 | 優先度 |
|---|---|---|---|
| 1 | 整理 | `findOpeningJournal` と `isPayableOpeningJournal` のロジック統合 (§8.5) | 中 |
| 2 | 機能 | 複数 fiscal year 対応: opening_date を config / config テーブル化 (§8.1) | 低 (現状単一年度) |
| 3 | 機能 | 期首残の audit trail (履歴管理) — 軸 F の `design-audit-trail-accounts-payable.md` で別途設計済 | 中 |
| 4 | 完了 | `AccountsPayableIntegrityService` 側の opening 注入方針を A 案で確定 (§7.2.1)。`buildSupplierCumulativeDiffMap` は `SupplierBalancesService.generate()` への委譲経由で同値の cumulative diff を返却。`MfOpeningBalanceService` は明示依存として inject 済 (P1-02 / DD-BGH-01、2026-05-04) | — |
| 5 | 整理 | `claudedocs/_spike_mf_opening_*.json` の削除可否確認 (§8.7) | 低 |
| 6 | UX | 取込結果の `unmatchedBranches` を画面上で詳細表示 (現状 toast.warning のみ) | 低 |
| 7 | 機能 | partner (得意先) 単位の期首残 (売掛金版) — `MEMORY.md` の Phase C 相当に同期 | 中 |
| 8 | テスト | `fetchFromMfJournalOne` の単体テスト (mock MfApiClient) が未確認 | 中 |
| 9 | 運用 | `validationLevel != MATCH` 時のリリース判断ガイド (運用ドキュメント) | 中 |
