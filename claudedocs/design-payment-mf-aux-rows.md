# 検証済み買掛金CSV出力 — 補助行（EXPENSE/SUMMARY/DIRECT_PURCHASE）の永続化設計

作成日: 2026-04-15
ステータス: **Approved (2026-04-15)**
関連: `design-payment-mf-import.md`, `design-accounts-payable.md`
対象機能: `/finance/accounts-payable` の「検証済みCSV出力」拡張

## 承認事項（2026-04-15 ユーザー確認済み）
1. タブ順序: 1=買掛金一覧、2=MF補助行
2. タブ名横に件数バッジ表示（例: `MF補助行 (10)`）
3. 補助行は表示のみ（編集・削除なし）
4. 空状態文言: §6.3 末尾の案内文を採用
5. 補助行タブには transactionMonth/取引日列は表示しない（CSV 出力時のみ使用）

---

## 1. 背景・目的

### 既存の「検証済みCSV出力」機能（v1）の制約
`t_accounts_payable_summary` の verified 行から MF仕訳CSVを直接生成する機能を実装したが、**PAYABLE（買掛金）行のみ**しか出力できない。実運用の MF インポートCSVには以下の **補助行** が含まれており、これらは振込明細Excelからしか取得できないため Excel 再アップロードが必須になっている。

### 補助行の種別と取得元
| 行種別 | 例 | 出処（Excel 内） | DB 保持 |
|---|---|---|---|
| **PAYABLE** | 買掛金/竹の子の里㈱ → 資金複合 | 明細行（合計行より前） | ✅ `t_accounts_payable_summary` |
| **EXPENSE** | 荷造運賃/福山通運、消耗品費/リコージャパン㈱、車両費/広島トヨペット㈱ | 明細行（合計行より前、ルール=`EXPENSE`） | ❌ |
| **DIRECT_PURCHASE** | 仕入高/ワタキューセイモア（20日払いのみ） | 合計行**後**のセクション | ❌ |
| **SUMMARY 振込手数料値引** | 資金複合→仕入値引・戻し高 | 合計行 F列（送料相手合計） | ❌ |
| **SUMMARY 早払収益** | 資金複合→早払収益 | 合計行 H列（早払合計） | ❌ |

### ゴール
**5日払いExcel と 20日払いExcel の両方を「振込明細で一括検証」した後**、検証済みCSV出力 1 操作で **全行種をマージした統合CSV** を出力できるようにする。これにより:
- 経理担当者が 5日 と 20日 の Excel を別々に CSV 化して手動結合する作業がゼロになる
- 既存の手動結合工程で発生していた漏れ・重複ミスを排除できる
- マスタ修正（payment_mf_rule の借方/貸方変更）後に**Excel再アップロード不要で再生成**可能

---

## 2. 現状フロー vs 設計後フロー

### 現状（v1）
```
[5日Excel] → applyVerification → t_accounts_payable_summary 更新（PAYABLE のみ）
[5日Excel] → convert            → CSV (5日分: PAYABLE+EXPENSE+SUMMARY+DIRECT_PURCHASE) — 履歴保存
[20日Excel] → applyVerification → t_accounts_payable_summary 更新（PAYABLE のみ）
[20日Excel] → convert           → CSV (20日分) — 履歴保存
                ↓ 経理担当が手動マージ
        買掛仕入MFインポートファイル_{締め日}.csv
```

検証済みCSV出力（v1）:
```
取引月指定 → t_accounts_payable_summary（PAYABLE のみ）→ CSV (PAYABLE のみ)
```

### 設計後（v2）
```
[5日Excel] → applyVerification → t_accounts_payable_summary 更新（PAYABLE）
                              + t_payment_mf_aux_row 更新（EXPENSE/SUMMARY/DIRECT_PURCHASE）
                              + t_payment_mf_import_history 履歴保存（既存）
[20日Excel] → applyVerification → 同上（5日分と並存して保存）
```

検証済みCSV出力（v2）:
```
取引月指定 → t_accounts_payable_summary（PAYABLE）
          + t_payment_mf_aux_row（EXPENSE/SUMMARY/DIRECT_PURCHASE × 5日 + 20日）
          → 統合CSV（手動マージ不要）
```

---

## 3. データモデル

### 3.1 新規テーブル `t_payment_mf_aux_row`

```sql
CREATE TABLE t_payment_mf_aux_row (
    aux_row_id          BIGSERIAL PRIMARY KEY,
    shop_no             INTEGER NOT NULL DEFAULT 1,           -- FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO
    transaction_month   DATE    NOT NULL,                     -- 締め日 (前月20日)。CSV 取引日列
    transfer_date       DATE    NOT NULL,                     -- 出処Excelの送金日 (5日 or 20日)
    rule_kind           VARCHAR(20) NOT NULL,                 -- 'EXPENSE' / 'SUMMARY' / 'DIRECT_PURCHASE'
    sequence_no         INTEGER NOT NULL,                     -- Excel内の出現順 (CSV出力順序維持)
    source_name         VARCHAR(255) NOT NULL,                -- Excel B列の送り先名 (SUMMARYは "振込手数料値引" 等)
    payment_supplier_code VARCHAR(20),                        -- Excel A列 (EXPENSEで参照、SUMMARYはNULL)
    amount              NUMERIC NOT NULL,
    debit_account       VARCHAR(50)  NOT NULL,
    debit_sub_account   VARCHAR(50),
    debit_department    VARCHAR(50),
    debit_tax           VARCHAR(30)  NOT NULL,
    credit_account      VARCHAR(50)  NOT NULL,
    credit_sub_account  VARCHAR(50),
    credit_department   VARCHAR(50),
    credit_tax          VARCHAR(30)  NOT NULL,
    summary             VARCHAR(255),                         -- 摘要欄
    tag                 VARCHAR(50),
    source_filename     VARCHAR(255),                         -- 出処 Excel ファイル名 (トレーサビリティ用)
    add_date_time       TIMESTAMP    NOT NULL,
    add_user_no         INTEGER,
    modify_date_time    TIMESTAMP,
    modify_user_no      INTEGER,

    -- 同一 (shop_no, transaction_month, transfer_date) で再アップロードされたら洗い替えする
    -- 物理キーは aux_row_id だが、再アップロード時の重複を防ぐため
    -- (shop_no, transaction_month, transfer_date) で DELETE → INSERT
    CONSTRAINT chk_rule_kind CHECK (rule_kind IN ('EXPENSE','SUMMARY','DIRECT_PURCHASE'))
);

CREATE INDEX idx_payment_mf_aux_tx_month
    ON t_payment_mf_aux_row(shop_no, transaction_month);

CREATE INDEX idx_payment_mf_aux_transfer
    ON t_payment_mf_aux_row(shop_no, transaction_month, transfer_date);
```

### 3.2 設計上の判断ポイント

#### Q1. なぜ `t_accounts_payable_summary` に統合せず別テーブルか？
- `t_accounts_payable_summary` は **仕入明細から集計された買掛金** が本質。EXPENSE/SUMMARY/DIRECT_PURCHASE はそもそも仕入明細から導出されない（経費・補正項目）
- 集計バッチ（`AccountsPayableSummaryCalculator`）の責務と混在する
- PK が (shop_no, supplier_no, transaction_month, tax_rate) で、SUMMARY 行（supplier 概念がない）には合わない

#### Q2. なぜ `del_flg` を使わず物理 DELETE → INSERT で洗い替え？
- 補助行は「Excel スナップショットの再現」用途で、過去履歴の保全は `t_payment_mf_import_history.csv_body` で別管理
- 論理削除すると洗い替え時のクエリが複雑化（`del_flg='0'` フィルタ + 重複対応）
- 同一送金日Excel を再アップロードしたら最新の値で完全置換、というシンプルな運用

#### Q3. なぜ verified_manually 相当のフラグを持たないか？
- 補助行は **Excel 由来のみ** で、UI から手入力する経路を作らない（v2 スコープ外）
- v3 で必要になれば後付けで `is_manual` 等を追加できる

#### Q4. (shop_no, transaction_month, transfer_date) で重複防止する理由
- 同じ送金日の Excel を再アップロードしたら：旧データ削除 → 新データ挿入で完全置換
- 5日 と 20日 は別 transfer_date で並存（両方が aux_row として保存される）
- v3 で「片方の Excel だけ修正したい」需要が出たときに、もう片方を巻き込まずに更新可能

### 3.3 Entity / Repository

```java
@Entity @Data @Builder
@Table(name = "t_payment_mf_aux_row")
public class TPaymentMfAuxRow implements IEntity {
    @Id @GeneratedValue(strategy = IDENTITY) @Column(name = "aux_row_id")
    private Long auxRowId;

    private Integer shopNo;
    private LocalDate transactionMonth;
    private LocalDate transferDate;
    private String ruleKind;
    private Integer sequenceNo;
    private String sourceName;
    private String paymentSupplierCode;
    private BigDecimal amount;
    private String debitAccount;
    private String debitSubAccount;
    private String debitDepartment;
    private String debitTax;
    private String creditAccount;
    private String creditSubAccount;
    private String creditDepartment;
    private String creditTax;
    private String summary;
    private String tag;
    private String sourceFilename;
    private LocalDateTime addDateTime;
    private Integer addUserNo;
    private LocalDateTime modifyDateTime;
    private Integer modifyUserNo;

    // del_flg 持たない（物理削除運用）
    @Override public String getDelFlg() { return "0"; }
    @Override public void setDelFlg(String s) { /* no-op */ }
}
```

```java
public interface TPaymentMfAuxRowRepository extends JpaRepository<TPaymentMfAuxRow, Long> {
    @Modifying
    @Query("DELETE FROM TPaymentMfAuxRow r WHERE r.shopNo = :shopNo " +
           "AND r.transactionMonth = :txMonth AND r.transferDate = :transferDate")
    int deleteByShopNoAndTransactionMonthAndTransferDate(
            @Param("shopNo") Integer shopNo,
            @Param("txMonth") LocalDate transactionMonth,
            @Param("transferDate") LocalDate transferDate);

    // 検証済みCSV出力用: 取引月の全送金日分を出力順で取得
    List<TPaymentMfAuxRow> findByShopNoAndTransactionMonthOrderByTransferDateAscSequenceNoAsc(
            Integer shopNo, LocalDate transactionMonth);
}
```

---

## 4. 処理フロー

### 4.1 書き込み: `applyVerification` 拡張

`PaymentMfImportService.applyVerification(uploadId, userNo)` を以下のように拡張:

```java
@Transactional
public VerifyResult applyVerification(String uploadId, Integer userNo) {
    CachedUpload cached = getCached(uploadId);
    LocalDate txMonth = deriveTransactionMonth(cached.getTransferDate());
    LocalDate transferDate = cached.getTransferDate();

    // 1. 既存処理: PAYABLE → t_accounts_payable_summary 更新（変更なし）
    PaymentMfPreviewResponse preview = buildPreview(uploadId, cached);
    for (ParsedEntry e : cached.getEntries()) {
        // ... PAYABLE 反映処理 ...
    }

    // 2. NEW: 補助行（EXPENSE/SUMMARY/DIRECT_PURCHASE）→ t_payment_mf_aux_row 洗い替え
    auxRepository.deleteByShopNoAndTransactionMonthAndTransferDate(
            ACCOUNTS_PAYABLE_SHOP_NO, txMonth, transferDate);

    int seq = 0;
    for (PaymentMfPreviewRow row : preview.getRows()) {
        if ("PAYABLE".equals(row.getRuleKind())) continue;        // PAYABLE は別管理
        if ("UNREGISTERED".equals(row.getErrorType())) continue;  // ルール未登録はスキップ

        auxRepository.save(TPaymentMfAuxRow.builder()
            .shopNo(ACCOUNTS_PAYABLE_SHOP_NO)
            .transactionMonth(txMonth)
            .transferDate(transferDate)
            .ruleKind(row.getRuleKind())   // EXPENSE / SUMMARY / DIRECT_PURCHASE
            .sequenceNo(seq++)
            .sourceName(row.getSourceName())
            .paymentSupplierCode(row.getPaymentSupplierCode())
            .amount(BigDecimal.valueOf(row.getAmount()))
            .debitAccount(row.getDebitAccount())
            .debitSubAccount(row.getDebitSubAccount())
            .debitDepartment(row.getDebitDepartment())
            .debitTax(row.getDebitTax())
            .creditAccount(row.getCreditAccount())
            .creditSubAccount(row.getCreditSubAccount())
            .creditDepartment(row.getCreditDepartment())
            .creditTax(row.getCreditTax())
            .summary(row.getSummary())
            .tag(row.getTag())
            .sourceFilename(cached.getFileName())
            .addDateTime(LocalDateTime.now())
            .addUserNo(userNo)
            .build());
    }

    return ...;
}
```

**重要な不変条件**:
- `applyVerification` は同一トランザクション内で PAYABLE 反映と aux 洗い替えを行う（部分失敗で aux だけ更新されることはない）
- 補助行の DELETE → INSERT は `(shop_no, transaction_month, transfer_date)` 単位。5日Excel をアップロードしても 20日分は影響を受けない

### 4.2 読み出し: `exportVerifiedCsv` 拡張

```java
public VerifiedExportResult exportVerifiedCsv(LocalDate transactionMonth, Integer userNo) {
    // 1. 既存: PAYABLE 行を t_accounts_payable_summary から構築
    List<PaymentMfPreviewRow> payableRows = buildPayableRowsFromSummary(transactionMonth);

    // 2. NEW: 補助行を t_payment_mf_aux_row から取得、PaymentMfPreviewRow に変換
    List<TPaymentMfAuxRow> auxRows = auxRepository
        .findByShopNoAndTransactionMonthOrderByTransferDateAscSequenceNoAsc(
            ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth);
    List<PaymentMfPreviewRow> auxConverted = auxRows.stream()
        .map(this::auxToPreviewRow).toList();

    // 3. 結合: PAYABLE → AUX (5日分EXPENSE → 5日分SUMMARY → 20日分EXPENSE → 20日分SUMMARY → DIRECT_PURCHASE)
    List<PaymentMfPreviewRow> all = new ArrayList<>();
    all.addAll(payableRows);
    all.addAll(auxConverted);

    byte[] csv = toCsvBytes(all, transactionMonth);
    saveVerifiedExportHistory(transactionMonth, all.size(), totalAmount, csv, userNo);

    return VerifiedExportResult.builder()
        .csv(csv)
        .rowCount(all.size())
        .auxRowCount(auxConverted.size())
        .payableRowCount(payableRows.size())
        ...
        .build();
}
```

### 4.3 CSV 出力順序

既存運用 CSV と完全一致させるため:

1. **PAYABLE**（買掛金）— `t_accounts_payable_summary` の supplier_no 順
2. **EXPENSE**（5日分）→ **EXPENSE**（20日分） — Excel 内の出現順 (`sequence_no`)
3. **SUMMARY 振込手数料値引**（5日分）→ **SUMMARY 早払収益**（5日分）
4. **SUMMARY**（20日分） — 同上
5. **DIRECT_PURCHASE**（20日分） — Excel 末尾セクション

`transferDate ASC, sequence_no ASC` ソートで自然と上記順序になる。EXPENSE と SUMMARY の混在順序は Excel 内出現順を保つ。

### 4.4 取引日列の扱い

CSV の取引日列は **全行 `transactionMonth`（前月20日）固定**。
- v1 で既存 Excel フローも統一済み → 補助行も同じ扱いで矛盾なし

---

## 5. API 設計

### 5.1 既存エンドポイント変更なし
- `POST /finance/payment-mf/verify/{uploadId}` — 内部処理で aux 行も保存するが、レスポンス互換維持

### 5.2 既存 `GET /finance/payment-mf/export-verified` レスポンス拡張

```jsonc
// 既存（HTTPヘッダ）
X-Row-Count: 30           // 全行数
X-Total-Amount: 1234567   // 全行合計

// NEW（追加ヘッダ）
X-Payable-Count: 24       // PAYABLE 行数
X-Aux-Count: 6            // 補助行数（EXPENSE+SUMMARY+DIRECT_PURCHASE）
X-Missing-Aux-Months: ""  // 補助行が見つからない transferDate (例: "5日Excelが未取込")
```

### 5.3 補助行一覧API（v2 で実装する）

タブ切り替え UI（§6.2）から呼び出される。

```
GET /finance/payment-mf/aux-rows?transactionMonth=2026-01-20
→ 200 OK
[
  {
    "auxRowId": 123,
    "transferDate": "2026-02-05",
    "ruleKind": "EXPENSE",
    "sequenceNo": 0,
    "sourceName": "福山通運",
    "paymentSupplierCode": "054200",
    "amount": 110506,
    "debitAccount": "荷造運賃",
    "debitSubAccount": null,
    "debitDepartment": "物販事業部",
    "debitTax": "課税仕入 10%",
    "creditAccount": "資金複合",
    ...
    "summary": "物販部 運賃",
    "tag": null,
    "sourceFilename": "振込み明細08-2-5.xlsx"
  },
  ...
]
```

ソート: `transferDate ASC, sequenceNo ASC`（CSV出力順序と一致）

### 5.4 補助行削除API（v3 候補）
- `DELETE /finance/payment-mf/aux-rows?transactionMonth=...&transferDate=...` — 手動削除
- v2 では不要（再アップロードで洗い替えされるため）

---

## 6. UI 変更

### 6.1 `/finance/accounts-payable` ページのタブ切り替え化（メイン変更）

**ご要望**: DB 保存だけだと中身が見えないので、買掛金一覧と同じページに補助行を別タブで可視化する。

**現状（v1）の構成**:
```
[/finance/accounts-payable]
  PageHeader (検索条件 + アクションボタン群)
  ├ 振込明細で一括検証 ボタン
  ├ 検証済みCSV出力 ボタン (admin)
  ├ 仕入明細取込/再集計/再検証(SMILE) ボタン (admin)
  サマリアラート (未検証/不一致件数)
  DataTable (買掛金一覧 = PAYABLE)
```

**変更後（v2）の構成**:
```
[/finance/accounts-payable]
  PageHeader (検索条件 + アクションボタン群) — 既存と同じ
  サマリアラート — 既存と同じ

  ┌─ Tabs (取引月・ショップで両タブ共通フィルタ) ─────────┐
  │ [買掛金一覧]  [MF補助行 (n件)]                        │
  ├──────────────────────────────────────────────────────┤
  │                                                       │
  │  選択中のタブに応じた DataTable                       │
  │                                                       │
  └──────────────────────────────────────────────────────┘
```

### 6.2 タブ1: 買掛金一覧（既存と同じ内容）

現状の DataTable をそのまま表示。既存機能（行クリック→検証ダイアログ、MF出力 Switch、仕入先名→/purchases drilldown 等）に変更なし。

タブラベル横に件数バッジ:
```
[買掛金一覧 (24)]   24 = 表示中ページの全件数 or 取引月の総件数
```

### 6.3 タブ2: MF補助行（新規）

`GET /finance/payment-mf/aux-rows?transactionMonth=...` の結果を DataTable で表示:

| 送金日 | 種別 | 送り先 | 借方 | 借方部門 | 借方税区分 | 金額 | 貸方 | 貸方税区分 | 摘要 | ソース |
|---|---|---|---|---|---|---|---|---|---|---|
| 2026-02-05 | EXPENSE | 福山通運 | 荷造運賃 | 物販事業部 | 課税仕入 10% | ¥110,506 | 資金複合 | 対象外 | 物販部 運賃 | 振込み明細08-2-5.xlsx |
| 2026-02-05 | EXPENSE | サクマ運輸㈱ | 荷造運賃 | 物販事業部 | 課税仕入 10% | ¥74,294 | 資金複合 | 対象外 | 物販部 運賃 | 〃 |
| 2026-02-05 | SUMMARY | 振込手数料値引 | 資金複合 | - | 対象外 | ¥17,160 | 仕入値引・戻し高 | 課税仕入-返還等 10% | 振込手数料値引／5日払い分 | 〃 |
| 2026-02-05 | SUMMARY | 早払収益 | 資金複合 | - | 対象外 | ¥32,614 | 早払収益 | 非課税売上 | 早払収益／5日払い分 | 〃 |
| 2026-02-20 | EXPENSE | 広島トヨペット㈱ | 車両費 | - | 対象外 | ¥159,489 | 資金複合 | 対象外 | 車両費 | 振込み明細08-2-20.xlsx |
| 2026-02-20 | DIRECT_PURCHASE | ワタキューセイモア | 仕入高 | - | 課税仕入 10% | ¥250,000 | 資金複合 | 対象外 | ワタキューセイモア | 〃 |
| ... |

**特徴**:
- 種別は色付き Badge（EXPENSE=青、SUMMARY=黄、DIRECT_PURCHASE=紫）
- 送金日（5日 / 20日）でグルーピング表示（DataTable のソート維持）
- ソース列でどの Excel 由来か追跡可能（誤取込時のリカバリ）
- 件数 0 件時の空状態:
  ```
  ⚠️ この取引月の補助行は登録されていません。
  振込明細Excel を「振込明細で一括検証」からアップロードすると、
  EXPENSE/SUMMARY/DIRECT_PURCHASE 行が自動的に保存されます。
  ```

**操作**:
- v2: 表示のみ（編集・削除 UI は無し）
- v3: 不要な行の手動削除、ルール再適用ボタン等を検討

### 6.4 検証済みCSV出力ダイアログの拡張

タブ表示で内訳が見えるためダイアログは軽量のままでも OK。ただし「DLボタン押す前に件数確認したい」需要に応えるため、サマリ表示は追加:

```
[ダイアログ: 検証済みCSV出力]
  対象取引月: 2026-01-20

  内訳:
    買掛金 (一致 + MF出力ON)        24件
    補助行 (5日Excel由来)            5件 (EXPENSE 3 + SUMMARY 2)
    補助行 (20日Excel由来)           5件 (EXPENSE 2 + SUMMARY 2 + DIRECT_PURCHASE 1)
    ----
    CSV 行数合計                    34件

  ⚠️ 5日払いExcel の補助行が見つかりません
  → 「振込明細で一括検証」から 5日払い Excel をアップロードしてください
  ※警告は片方/両方のExcelが未取込の場合に表示

  [キャンセル]  [CSVダウンロード]
```

### 6.5 タブ実装方針

- shadcn/ui の `Tabs` コンポーネント (`@/components/ui/tabs`) を使用（既に使用例あり）
- タブ状態は URL クエリパラメータ `?tab=payable` or `?tab=aux` で永続化（リロード耐性）
- 取引月・ショップ選択は両タブで共通の検索条件として保持
- 補助行タブは「取引月のみ」をフィルタ（shop_no=1 固定なのでショップ選択は無視）

### 6.6 補助行プレビュー API（CSV ダイアログ用、追加）

```
GET /finance/payment-mf/export-verified/preview?transactionMonth=...
→ {
    payableCount: 24,
    payableTotal: 1234567,
    auxBreakdown: [
      { transferDate: "2026-02-05", ruleKind: "EXPENSE",  count: 3 },
      { transferDate: "2026-02-05", ruleKind: "SUMMARY",  count: 2 },
      { transferDate: "2026-02-20", ruleKind: "EXPENSE",  count: 2 },
      { transferDate: "2026-02-20", ruleKind: "SUMMARY",  count: 2 },
      { transferDate: "2026-02-20", ruleKind: "DIRECT_PURCHASE", count: 1 },
    ],
    warnings: ["5日払い Excel(2026-02-05) の補助行が見つかりません"]
  }
```

警告判定ロジック:
- 取引月の transferDate 範囲 = `transactionMonth + 1ヶ月` の 5日 と 20日
- 補助行に該当 transferDate が無い → 警告

実装優先度: 中（先にタブ表示と CSV 出力を実装し、プレビュー API は後付けでも可）

---

## 7. テスト方針

### 7.1 ユニットテスト（新規）

`PaymentMfImportServiceAuxRowTest`:
- T1: applyVerification 後 aux テーブルに EXPENSE/SUMMARY/DIRECT_PURCHASE が保存される
- T2: 同一 (txMonth, transferDate) で再 applyVerification → 古い aux 行が削除され新規挿入のみ残る
- T3: 5日 Excel 後に 20日 Excel を applyVerification → 5日分は維持、20日分が追加
- T4: PAYABLE 行は aux テーブルに入らない（責務分離）
- T5: UNREGISTERED 行は aux に入らない（CSV に出ないため）

### 7.2 結合テスト（新規）

`VerifiedCsvExportIntegrationTest`:
- I1: 5日Excel + 20日Excel 両方 applyVerification → 検証済みCSV出力 → 行数・順序・金額が手動結合 CSV と一致
- I2: 5日Excel のみ applyVerification → 検証済みCSV出力 → PAYABLE + 5日分補助行のみ、警告ヘッダに「20日Excel未取込」

### 7.3 既存ゴールデンマスタテスト
- `PaymentMfImportServiceGoldenMasterTest`: 影響なし（applyVerification を呼ばず convert のみ実施）
- 念のため aux Repository を Mock 注入

### 7.4 マイグレーションテスト
- 既存 t_accounts_payable_summary に検証済み行があるが aux テーブルが空 → 検証済みCSV出力 → PAYABLE のみ出力（v1 互換動作）
- → 既存ユーザは Excel を再 applyVerification するまで補助行が付与されない（明示）

---

## 8. 移行・互換性

### 8.1 DB マイグレーション
1. `create_payment_mf_aux_row.sql` — 新規テーブル + インデックス作成
2. 既存データへの遡及書き込みは **しない**（過去履歴の補助行を再現できないため、再アップロードしない限り PAYABLE のみ）

### 8.2 既存機能との互換性
| 機能 | v1 挙動 | v2 挙動 |
|---|---|---|
| `convert` (Excel→CSV) | 全行種を含む CSV | 変更なし |
| `applyVerification` | PAYABLE のみ DB 反映 | PAYABLE + aux 行を DB 反映 |
| `exportVerifiedCsv` | PAYABLE のみ CSV | PAYABLE + aux 行を結合 CSV |
| 履歴ダウンロード | 過去 CSV を再DL | 変更なし |

### 8.3 ロールバック手順
- aux テーブル DROP のみ
- `applyVerification` のコード差分を revert
- `exportVerifiedCsv` の差分を revert
- 既存 `t_accounts_payable_summary` には影響なし

---

## 9. 影響範囲（修正対象ファイル一覧）

### Backend
- **新規**:
  - `backend/src/main/resources/sql/create_payment_mf_aux_row.sql`
  - `backend/src/main/java/jp/co/oda32/domain/model/finance/TPaymentMfAuxRow.java`
  - `backend/src/main/java/jp/co/oda32/domain/repository/finance/TPaymentMfAuxRowRepository.java`
- **変更**:
  - `PaymentMfImportService.java`
    - `applyVerification`: aux 行洗い替え追加
    - `exportVerifiedCsv`: aux 行マージ追加
    - `auxToPreviewRow` private 変換ヘルパ追加
- **テスト**:
  - `PaymentMfImportServiceAuxRowTest`（新規）
  - `VerifiedCsvExportIntegrationTest`（新規）

### Frontend
- **変更**:
  - `components/pages/finance/accounts-payable.tsx`
    - **タブ切り替え追加**（Tabs コンポーネント導入、タブ1=既存買掛金一覧、タブ2=新規 MF補助行）
    - `?tab=...` URL クエリでタブ状態永続化
    - `VerifiedCsvExportDialog`: 内訳プレビュー表示追加
    - 警告メッセージ（補助行欠落時）
  - `types/payment-mf.ts`
    - `PaymentMfAuxRow` 型定義追加
    - `VerifiedExportPreview` 型定義追加（プレビュー API レスポンス）
- **新規**:
  - `components/pages/finance/PaymentMfAuxRowsTable.tsx`（タブ2 の中身。DataTable + 種別Badge）
- **任意（UX 向上）**:
  - 補助行が空のときの空状態コンポーネント

---

## 10. 未確定事項・今後の検討

### 10.1 補助行を手入力で追加する経路は v2 で作るか
- **不要**（Excel 経由が現実的、UI で直接編集する需要は薄い）
- v3 で「ルール変更後に再生成」需要が出たときに検討

### 10.2 取引月をまたぐ補助行
- 通常運用では補助行は (締め日 = 20日) と 1:1 だが、誤って違う送金日の Excel を別取引月扱いで applyVerification されたら？
- → `deriveTransactionMonth(transferDate)` で締め日が一意に決まるので、Excel 内容が同じなら矛盾は起きない
- → 異常系として「予期しない transferDate がある場合の警告」を出すかは実装時判断

### 10.3 履歴テーブルとの関係
- `t_payment_mf_import_history` には Excel 取込時の CSV body が保存される（既存）
- 検証済みCSV出力時も「verified-export_*」名で履歴に残す（v1 で実装済）
- aux 行から再構成した CSV と Excel 由来 CSV が並存する状態になるが、ファイル名で識別可能

### 10.4 ルールマスタ修正の反映
- `m_payment_mf_rule` を変更しても、既に保存済みの `t_payment_mf_aux_row` には反映されない（スナップショット保存のため）
- 反映したい場合: 振込明細 Excel を再 applyVerification する
- これは仕様として明記し、UI 側で「補助行は Excel 取込時点のスナップショット」と注記

### 10.5 5日払い・20日払いの整合性チェック
- 5日Excel と 20日Excel の transferDate が同じ取引月に紐づくこと（締め日が同じ）を Insert 時に検証するか？
- 厳密にやるなら applyVerification の引数として「期待 transactionMonth」を要求
- 緩く運用するなら警告ログのみ
- → v2 では緩く（警告ログ）でスタート、誤検知が出たら厳密化

---

## 11. 実装サイズ見積もり

| カテゴリ | 工数感 |
|---|---|
| DB マイグレーション + Entity + Repository | S |
| Service `applyVerification` 拡張 | S |
| Service `exportVerifiedCsv` 拡張 | M |
| 補助行一覧API (`GET /aux-rows`) | S |
| 補助行プレビュー API (`GET /export-verified/preview`) | S |
| ユニットテスト 5本 | M |
| 結合テスト 2本 | M |
| Frontend タブ切り替え + 補助行 DataTable | M |
| Frontend ダイアログ内訳プレビュー | S |
| **合計** | **M〜L (2〜3日相当)** |

---

## 12. レビュー観点

レビュー時に特に確認していただきたい点:

1. **テーブル名/カラム名**: `t_payment_mf_aux_row` で良いか。命名規約に沿うか
2. **物理削除運用**: 論理削除ではなく洗い替え方式で問題ないか
3. **CSV 出力順序**: §4.3 の順序は既存運用 CSV と一致するか
4. **補助行プレビュー API**: 必須か任意か
5. **警告表示**: 5日/20日 片方の Excel が未取込の場合の挙動
6. **ルールマスタ修正の反映方針**: §10.4 の運用ルール
