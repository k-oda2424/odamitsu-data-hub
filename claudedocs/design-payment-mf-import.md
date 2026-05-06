# 買掛仕入MFインポート（Payment MF Import）設計書

作成日: 2026-04-14
ステータス: Draft
関連: `/finance/cashbook-import`（既存）、`/finance/accounts-payable`（既存）

## 1. 目的

経理担当者が作成する **振込み明細Excel**（月2回、5日/20日払い）から、MoneyForward仕訳インポート用CSV（買掛仕入MFインポートファイル_{yyyymmdd}.csv）を生成する。同時に既存の **買掛金一覧（`t_accounts_payable_summary`）** と突合し、金額差異・未登録仕入先を画面上でハイライトして検出する。

### 現行フロー（移行前）
- Excel の `MF変換` シート内の VLOOKUP/参照式で `支払い明細` シートから生成
- `変換MAP` シートで送り先名→MF補助科目をひきあて
- 手作業で CSV を csv 保存して MF にインポート

### 移行後
1. 画面で振込み明細 xlsx をアップロード
2. プレビューで 買掛金一覧との突合結果を確認（🟢一致/🟡金額差異/🔴買掛金なし）
3. 未登録送り先を画面上でマスタ登録・編集
4. CSVをダウンロード & 変換履歴を保存

---

## 2. 入力仕様（振込明細Excel）

月2回（5日払い・20日払い）で発行される2バリエーションの Excel を単一のパーサで処理する。ファイル間で**シート名・列レイアウトが異なる**ため、**ヘッダ行からの動的な列マップ構築**で差分を吸収する。

### 2.1 シート名の自動判別

変換対象シートは以下の優先順位で検出（最初にマッチしたもの）:
1. `支払い明細`（20日払い形式）
2. `振込明細`（5日払い形式）

`福通運賃明細` `MF変換` `変換MAP` は**除外**。

### 2.2 列マップ（ヘッダ行ベース）

2行目のヘッダ文字列で列位置を動的に特定する。必須キーは `送り先` / `請求額` / `送料相手` / `早払い`。

| ファイル | A | B | C | D | E | F | G | H | I | J |
|---|---|---|---|---|---|---|---|---|---|---|
| 20日払い（`支払い明細`） | (空) | 送り先 | 請求額 | 打込金額 | 振込金額 | 送料相手 | 値引 | **早払い** | 相殺 | 備考 |
| 5日払い（`振込明細`） | 仕入コード | 送り先 | 請求額 | 打込金額 | 振込金額 | 送料相手 | 自社 | 値引 | **早払い** | 相殺 |

- A列（仕入コード = 支払先コード）: 20日払いはヘッダ空でも数値が入る、5日払いはヘッダ明示
- G列の `自社` / `値引`, I列の `早払い` / `相殺` が**ファイル間で位置ずれ**するため、ヘッダ名ルックアップが必須

**送金日**: `E1`（両ファイル共通の固定位置）
**データ開始行**: 3行目から

### 2.3 仕訳対象外のメタ行スキップ

B列文字列の正規化完全一致 / 前方一致で除外（正規化 = 全角空白除去・trim）:
- 完全一致: `合計` / `小計` / `その他計` / `本社仕入 合計` / `請求額` / `打ち込み額`
- 前方一致: `20日払い振込手数料` / `5日払い振込手数料`
- または A・B・C 列すべて空
- 明細の**「合計」行**だけは特別扱い: スキップはするが、その行のF列（送料相手合計）・早払列合計をサマリー仕訳の生成元として保存

#### 2.3.1 セクション (5日払い / 20日払い) のモデル化 (G2-M3, 2026-05-06)

`PaymentMfExcelParser` は走査開始時 `currentSection = PaymentMfSection.PAYMENT_5TH` から始まり、合計行 (`合計` の B 列完全一致 + `isTotalRow` 補完) を踏むたびに以下を実施する:

1. 合計行の F (送料相手) / H (早払) / E (振込金額) / C (請求額) を `SectionSummary` として `ParsedExcel.summaries.put(currentSection, ...)` に格納
2. `currentSection` を `PAYMENT_5TH → PAYMENT_20TH` に遷移 (`PAYMENT_20TH` 以降は遷移しない = 同 section の最後の合計行で上書き)

各 `ParsedEntry` には `section` フィールドが設定され、`PaymentMfImportService` の自動降格判定 (PAYABLE → DIRECT_PURCHASE) や section 別整合性チェック (chk1/chk3) に利用される。

5日払いのみの月 (合計行 1 個) は `summaries` に `PAYMENT_5TH` のみ入り、`PAYMENT_20TH` は欠落 (空セクション扱い)。20日払いのみの月は運用上想定しない。

> **G2-M3 修正前の旧実装 (バグ)**: 旧 boolean `afterTotal` フラグは「最初の合計行を踏んだら以降全部 `afterTotal=true`」。`summaryCaptured` ガードで summary は最初の 1 個 (= 5日払い) しかキャプチャされず、20日払い summary は黙って捨てられていた。結果、整合性チェック (chk3) が「5日払い summary vs 5日払い+20日払い 両方の per-supplier sum」を比較する形に構造的にズレており、片側差額が偶然打ち消し合う Excel で OK と誤判定するリスクがあった。

### 2.4 特殊サマリー仕訳 (P1-03 案 D 適用後: **撤去**)

> **2026-05-04 更新**: 旧 SUMMARY 集約仕訳 (合計行 振込手数料値引・早払収益 2 行) は P1-03 案 D 採用で撤去された。代わりに per-supplier 行で `PAYABLE_FEE` / `PAYABLE_DISCOUNT` / `PAYABLE_EARLY` / `PAYABLE_OFFSET` 副行を仕入先別に展開する (§5.4 参照)。

旧仕様 (履歴):
- 「合計」行から振込手数料値引 / 早払収益の 2 行を生成
- supplier 単位の追跡が不能、銀行通帳の振込金額と乖離

新仕様 (案 D):
- per-supplier 行で送料相手 (列 F) / 値引 (列 G) / 早払 (列 H) / 相殺 (列 I) を読取り、副行に展開
- PAYABLE 主行の貸方金額は **振込金額 (列 E)** に切替 (= 銀行通帳と一致)
- 借方 買掛金 = 請求額 (列 C) 完全消込
- 詳細は §5.4 を参照

### 2.5 利用する列 (P1-03 案 D 適用後)

| 列 | 名称 | 役割 | per-supplier 利用 |
|---|---|---|---|
| A | 仕入コード | 支払先コード突合 | yes |
| B | 送り先 | supplier 名 | yes |
| C | 請求額 | 借方買掛金、消込判定 | yes |
| D | 打込金額 | 整合性検証用 (= 請求 - 値引 - 早払。CSV 出力には使わない) | yes (検証のみ) |
| E | 振込金額 | PAYABLE 主行の貸方金額 (= 銀行通帳と一致) | yes |
| F | 送料相手 | PAYABLE_FEE 副行 (仕入先負担の振込手数料) | yes |
| G | 値引 | PAYABLE_DISCOUNT 副行 | yes |
| H | 早払い | PAYABLE_EARLY 副行 (早払割引) | yes |
| I | 相殺 | PAYABLE_OFFSET 副行 | yes |
| J 以降 | 備考等 | 利用しない | no |

無視するシート: `福通運賃明細` (紙運用の参考資料)、`MF変換` / `変換MAP` (旧 VLOOKUP)。

---

## 3. 出力仕様（MFインポートCSV）

### フォーマット
- エンコーディング: **CP932（Shift_JIS）**（cashbook-import は UTF-8 BOM だが、MF買掛仕入の既存運用は CP932。過去CSVから検証済）
- 改行: **LF**（既存CSVに準拠。cashbookのCRLFとは異なる点に注意）
- 区切り: カンマ、囲み文字なし
- 金額後ろに半角スペース（`289027 ` のように出力。既存運用に合わせる）
- 日付書式: `YYYY/M/D`（ゼロパディングなし）

### 列構成（19列）
`取引No, 取引日, 借方勘定科目, 借方補助科目, 借方部門, 借方取引先, 借方税区分, 借方インボイス, 借方金額(円), 貸方勘定科目, 貸方補助科目, 貸方部門, 貸方取引先, 貸方税区分, 貸方インボイス, 貸方金額(円), 摘要, タグ, メモ`

### 行種別 (P1-03 案 D 適用後)

| 種別 | ruleKind | 借方 | 貸方 | 金額 | 条件 |
|---|---|---|---|---|---|
| 買掛金支払 (主行) | PAYABLE | 買掛金/{MF補助科目}/対象外 | 資金複合/対象外 | **振込金額 (列 E)** | 必須 |
| 振込手数料値引 (仕入先別) | PAYABLE_FEE | 買掛金/{MF補助科目}/対象外 | 仕入値引・戻し高/物販事業部/課税仕入-返還等10% | 送料相手 (列 F) | 列 F > 0 のみ |
| 値引 (仕入先別) | PAYABLE_DISCOUNT | 買掛金/{MF補助科目}/対象外 | 仕入値引・戻し高/物販事業部/課税仕入-返還等10% | 値引 (列 G) | 列 G > 0 のみ |
| 早払収益 (仕入先別) | PAYABLE_EARLY | 買掛金/{MF補助科目}/対象外 | 早払収益/物販事業部/非課税売上 | 早払 (列 H) | 列 H > 0 のみ |
| 相殺 (仕入先別) | PAYABLE_OFFSET | 買掛金/{MF補助科目}/対象外 | 仕入値引・戻し高/物販事業部/課税仕入-返還等10% **(暫定)** | 相殺 (列 I) | 列 I > 0 のみ |
| 運賃 (仕入紐付) | EXPENSE | 荷造運賃/物販事業部/課税仕入10% | 資金複合/対象外 | 請求額 | 送り先名ルール |
| 消耗品費 | EXPENSE | 消耗品費/対象外 | 資金複合/対象外 | 請求額 | 〃 |
| 車両費 | EXPENSE | 車両費/対象外 | 資金複合/対象外 | 請求額 | 〃 |
| 仕入高 (主行) | DIRECT_PURCHASE | 仕入高/課税仕入10% | 資金複合/対象外 | **振込金額 (列 E)** | 20日払いセクション (PAYABLE 自動降格 + 元 DIRECT_PURCHASE) |
| 振込手数料値引 (仕入先別/直接仕入高) | DIRECT_PURCHASE_FEE | 仕入高/課税仕入10% | 仕入値引・戻し高/物販事業部/課税仕入-返還等10% | 送料相手 (列 F) | 20日払い & 列 F > 0 のみ |
| 値引 (仕入先別/直接仕入高) | DIRECT_PURCHASE_DISCOUNT | 仕入高/課税仕入10% | 仕入値引・戻し高/物販事業部/課税仕入-返還等10% | 値引 (列 G) | 20日払い & 列 G > 0 のみ |
| 早払収益 (仕入先別/直接仕入高) | DIRECT_PURCHASE_EARLY | 仕入高/課税仕入10% | 早払収益/物販事業部/非課税売上 | 早払 (列 H) | 20日払い & 列 H > 0 のみ |
| 相殺 (仕入先別/直接仕入高) | DIRECT_PURCHASE_OFFSET | 仕入高/課税仕入10% | 仕入値引・戻し高/物販事業部/課税仕入-返還等10% **(暫定)** | 相殺 (列 I) | 20日払い & 列 I > 0 のみ |

- PAYABLE 主行 + 副行 (FEE/DISCOUNT/EARLY/OFFSET) の貸方合計 = 請求額 = 借方買掛金 → 完全消込。
- DIRECT_PURCHASE 主行 + 副行 (FEE/DISCOUNT/EARLY/OFFSET) の貸方合計 = 請求額 = 借方仕入高 → 5日払いと対称構造 (詳細は §5.5)。

> **TODO (税理士確認)**: 1) PAYABLE_OFFSET / DIRECT_PURCHASE_OFFSET の貸方科目は暫定で「仕入値引・戻し高 / 課税仕入-返還等 10%」。本来は売掛金との相殺仕訳 (借方 売掛金) を別途切る方が正しい可能性があるため、相殺発生時に税理士へ確認。 2) DIRECT_PURCHASE 系で「借方仕入高 + 貸方仕入値引・戻し高」(買って同時に値引) になる構造の妥当性。代替案: 借方仕入高 = 振込金額のみとして純額計上する方式 (詳細は §5.5.7)。

### 廃止済み行種別 (旧 SUMMARY)

P1-03 案 D 採用前に存在した「合計行から自動生成する SUMMARY 2 行」(振込手数料値引 / 早払収益) は撤去された。代わりに per-supplier 副行 (PAYABLE_FEE / PAYABLE_EARLY) で同等の会計表現になる。

旧 SUMMARY ruleKind を持つ aux_row レコード (`t_payment_mf_aux_row`) は 2026-05-04 以前のもののみ残存。検証済みCSV出力 (`exportVerifiedCsv`) では引き続き出力される (履歴互換)。

---

## 4. マスタ設計

### 方針: **1本に統合した `m_payment_mf_rule`**

既存cashbookが `m_mf_journal_rule`（ルール）+ `m_mf_client_mapping`（得意先名マッピング）の2本構成である理由は、cashbookは「摘要キーワード→複数ルール」「得意先名は都度人名/法人名が入る」構造だから。
対して買掛仕入MFは「送り先名 = 1:1 で仕訳ルールが決まる」ため、**送り先名をキーにした単一テーブル**で十分かつ運用が簡単。補助科目カラムを nullable にして買掛金行（MF補助科目必須）と固定費行（不要）を兼用する。

### テーブル定義

```sql
CREATE TABLE m_payment_mf_rule (
    id                      SERIAL PRIMARY KEY,
    source_name             VARCHAR(200) NOT NULL,   -- 支払い明細B列（送り先名）
    payment_supplier_code   VARCHAR(20),             -- m_payment_supplier.payment_supplier_code（NULLなら固定費行）
    rule_kind               VARCHAR(20)  NOT NULL,   -- 'PAYABLE' | 'EXPENSE' | 'DIRECT_PURCHASE'

    debit_account           VARCHAR(50)  NOT NULL,   -- 例: 買掛金 / 荷造運賃 / 消耗品費 / 車両費 / 仕入高
    debit_sub_account       VARCHAR(100),            -- MF補助科目（PAYABLEは必須、EXPENSEはNULL）
    debit_department        VARCHAR(50),             -- 例: 物販事業部
    debit_tax_category      VARCHAR(30)  NOT NULL,   -- 例: 対象外 / 課税仕入 10%

    credit_account          VARCHAR(50)  NOT NULL DEFAULT '資金複合',
    credit_sub_account      VARCHAR(100),
    credit_department       VARCHAR(50),
    credit_tax_category     VARCHAR(30)  NOT NULL DEFAULT '対象外',

    summary_template        VARCHAR(200) NOT NULL,   -- 摘要。{sub_account} / {source_name} プレースホルダ対応
    tag                     VARCHAR(100),
    priority                INTEGER      NOT NULL DEFAULT 100,

    del_flg          VARCHAR(1) NOT NULL DEFAULT '0',
    add_date_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    add_user_no      INTEGER,
    modify_date_time TIMESTAMP,
    modify_user_no   INTEGER
);
CREATE INDEX idx_payment_mf_rule_source ON m_payment_mf_rule(source_name) WHERE del_flg = '0';
CREATE INDEX idx_payment_mf_rule_code   ON m_payment_mf_rule(payment_supplier_code) WHERE del_flg = '0';
```

**ルックアップ優先順位**:
1. `payment_supplier_code` 一致（買掛金行のマッチ。支払先コードは振込明細Aに数値、m_payment_supplierは文字列のため型揃え要）
2. `source_name` 正規化一致（全角空格・半角空格・㈱/（株）を正規化して比較）
3. どちらもNG → 🔴未登録（画面で登録フローへ）

### シード（初期データ）

過去CSV（20260120, 20260205, 20260220）から抽出した変換MAP 40行弱 +

```sql
-- 固定費行（payment_supplier_code NULL）
INSERT INTO m_payment_mf_rule (source_name, rule_kind, debit_account, debit_department, debit_tax_category, credit_account, credit_department, credit_tax_category, summary_template, tag) VALUES
('福山通運',                'EXPENSE', '荷造運賃', '物販事業部', '課税仕入 10%', '資金複合', NULL, '対象外', '{source_name}', '物販部 運賃'),
('サクマ運輸㈱',            'EXPENSE', '荷造運賃', '物販事業部', '課税仕入 10%', '資金複合', NULL, '対象外', '{source_name}', '物販部 運賃'),
('ティックトランスポート㈲', 'EXPENSE', '荷造運賃', NULL,        '課税仕入 10%', '資金複合', NULL, '対象外', '{source_name}', '物販部 運賃'),
('ヨハネ印刷㈱',             'EXPENSE', '仕入高',   NULL,        '課税仕入 10%', '資金複合', NULL, '対象外', '{source_name}', '物販部 仕入 '),
('リコージャパン㈱',         'EXPENSE', '消耗品費', NULL,        '対象外',       '資金複合', NULL, '対象外', '{source_name}', '物販部 事務用品費'),
('広島トヨペット㈱',         'EXPENSE', '車両費',   NULL,        '対象外',       '資金複合', NULL, '対象外', '{source_name}', '車両費'),
-- 以下 ナカガワ/中国エンゼル/ビバ/ワタキューセイモア/ハウスホールドジャパン/奈良半商店/シルバー化成工業所 等
;
```

買掛金行は支払先コードでルックアップするため、`payment_supplier_code` セット + `debit_account='買掛金'` + `debit_sub_account='MF正式名'` のルールを `m_payment_supplier` からJOINして初期登録（マイグレーション1回）。

---

## 5. 突合ロジック（買掛金一覧との）

### 5.1 取引月の決定ルール（重要: 仕様変更あり）

送金日(`E1`)の日付から、以下で買掛金一覧の `transaction_month` を決定:

**現行ルール（`PaymentMfImportService#deriveTransactionMonth`）**:
5日払い・20日払いともに **前月20日締め** に統一する。

```java
transactionMonth = transferDate.minusMonths(1).withDayOfMonth(20)
```

| 送金日 | 突合対象の取引月 |
|---|---|
| 2026/2/5 | 2026-01-20（前月20日締め分） |
| 2026/2/20 | 2026-01-20（前月20日締め分） |

理由: 20日払いの振込明細は「前月20日締め分」の支払いであり、当月20日締めの集計はまだ
確定していないため。これにより集計タイミングと突合タイミングが整合する。

> **旧仕様 (〜2026-04-14)**[^old-rule]: 20日払いは「当月20日締め分」と突合していたが、集計確定前に突合が走るケースがあるため前月締めに統一した。本セクション本文 (上記表) が現行仕様 = 確定。

[^old-rule]: 過去の commit で残る Javadoc / コメントに「20日→当月20日」の記述が混在していた場合があるが、運用実態 (5日/20日とも前月20日締め) が正。`PaymentMfImportService#deriveTransactionMonth` の Javadoc も現行仕様に揃えて更新済 (SF-C04, 2026-05-04)。

### 5.2 突合処理

実装は **N+1 解消済（B-W11 レビュー対応, 2026-04-15）**。Excel 行ループ前に対象 `paymentSupplierCode` を一括収集し、`t_accounts_payable_summary` を 1 クエリで取得して `Map<code, List<summary>>` 形式の `payablesByCode` を構築する。

```
# Pass 1: 突合対象の supplier コードを集約（旧コード→正規化コード変換含む）
codesToReconcile = set()
for each 買掛金行 in 支払い明細:
    code = normalizePaymentSupplierCode(excelCode)
    if code: codesToReconcile.add(code)

# 一括ロード（IN クエリ 1 回）
payablesByCode = repository.findByShopNoAndTransactionMonthAndPaymentSupplierCodeIn(
    shopNo=1, transactionMonth=送金日の取引月, paymentSupplierCodes=codesToReconcile
).groupBy(paymentSupplierCode)

# Pass 2: 各行の判定（DB アクセスなし）
for each 買掛金行 in 支払い明細:
    payables = payablesByCode.get(code)
    if not payables:
        status = 🔴 '買掛金なし'
    else:
        verifiedAmount = sumVerifiedAmountForGroup(payables)  # 税率別 N 行集約
        if abs(verifiedAmount - 請求額) <= 100円:
            status = 🟢 '一致'
        else:
            status = 🟡 '金額差異'
            diff = verifiedAmount - 請求額
```

100円閾値は既存 `SmilePaymentVerifier` と同じ扱い (`FinanceConstants.PAYMENT_REPORT_MINOR_DIFFERENCE`)。固定費行（運賃等・20日払いセクションの仕入高行）は突合対象外で常に白表示。

### 5.3 行のセクション判定（統一ロジック）

20日払いファイルは本社仕入（買掛金）/ その他固定費 / 20日払い仕入高 の3セクションだが、**セクション境界を意識せず全行を統一ルックアップ**で処理する:

```
for each 明細行 (メタ行を除く):
    if 仕入コード(A列) が数値:
        ルール = m_payment_mf_rule.find_by_payment_supplier_code(code)
    else:
        ルール = m_payment_mf_rule.find_by_source_name_normalized(送り先名)

    if not ルール:
        status = 🔴 '未登録' / 仕訳なし
    else if ルール.rule_kind = 'PAYABLE':
        買掛金突合（§5.2）
    else:  # EXPENSE / DIRECT_PURCHASE
        突合スキップ・白表示
```

`rule_kind` は3値:
- `PAYABLE`: 買掛金仕訳（買掛金/補助科目/対象外 vs 資金複合/対象外）→ 突合対象
- `EXPENSE`: 費用計上（荷造運賃・消耗品費・車両費など）→ 突合対象外
- `DIRECT_PURCHASE`: 直接仕入高計上（20日払いセクションのワタキューセイモア等）→ 突合対象外

### 5.4 PAYABLE 行の supplier 別 attribute 展開 (P1-03 案 D, 2026-05-04 採用)

PAYABLE ルールがマッチした行 (合計行前のみ) は、Excel 列 D〜I を読取って **最大 5 行** に分解する。

#### 列読取り
| 列 | 役割 |
|---|---|
| C | 請求額 (借方買掛金額・消込金額) |
| D | 打込金額 (= 請求 - 値引 - 早払。整合性検証用、CSV には出さない派生値) |
| E | 振込金額 (PAYABLE 主行の貸方額) |
| F | 送料相手 (PAYABLE_FEE 副行) |
| G | 値引 (PAYABLE_DISCOUNT 副行) |
| H | 早払 (PAYABLE_EARLY 副行) |
| I | 相殺 (PAYABLE_OFFSET 副行) |

#### 出力ルール (per supplier)
| ruleKind | 借方 | 貸方 | 金額 | 条件 |
|---|---|---|---|---|
| `PAYABLE` | 買掛金/{MF補助科目}/対象外 | 資金複合/対象外 | **列 E** | 必須 (列 E NULL 時は 請求 - 控除合計 で派生) |
| `PAYABLE_FEE` | 買掛金/{MF補助科目}/対象外 | 仕入値引・戻し高/物販事業部/課税仕入-返還等 10% | 列 F | 列 F > 0 |
| `PAYABLE_DISCOUNT` | 買掛金/{MF補助科目}/対象外 | 仕入値引・戻し高/物販事業部/課税仕入-返還等 10% | 列 G | 列 G > 0 |
| `PAYABLE_EARLY` | 買掛金/{MF補助科目}/対象外 | 早払収益/物販事業部/非課税売上 | 列 H | 列 H > 0 |
| `PAYABLE_OFFSET` | 買掛金/{MF補助科目}/対象外 | 仕入値引・戻し高 (暫定) | 列 I | 列 I > 0 |

#### per-supplier 整合性チェック (1 円不許容)
列 E (振込金額) が読取れた行については以下を必ず満たす:

```
列 C (請求額) == 列 E (振込) + 列 F (送料相手) + 列 G (値引) + 列 H (早払) + 列 I (相殺)
```

違反行は `AmountReconciliation.perSupplierMismatches` に集約し、preview 画面で警告表示。

##### G2-M2 (2026-05-06) サーバー側ブロック + force 上書き

**問題**: 旧実装 (〜2026-05-05) は `perSupplierMismatches` を preview レスポンスに含めるだけで、`POST /finance/payment-mf/verify/{uploadId}` (買掛金一覧反映) と `POST /finance/payment-mf/convert/{uploadId}` (CSV ダウンロード) では **何のブロックも掛けていなかった**。フロント側にも警告 UI が無く、Excel 入力ミスがそのまま手動確定 + MF CSV に流れる事故があり得た。

**修正**:

| 経路 | 旧挙動 | 新挙動 (G2-M2) |
|---|---|---|
| `verify/{uploadId}` (買掛金一覧反映) | 警告なし反映 | mismatch 1 件以上で **422** 拒否、`force=true` 明示で突破可 |
| `convert/{uploadId}` (CSV ダウンロード) | 警告なし出力 | mismatch 1 件以上で **422** 拒否、`force` パラメータなし (Excel 修正が正しい運用) |
| `export-verified` (検証済 DB→CSV) | 影響なし | 影響なし (DB 確定済の数字を使うため不要) |

**API スペック**:

```
POST /api/v1/finance/payment-mf/verify/{uploadId}
Content-Type: application/json

{
  "force": false   // 既定。true で per-supplier 不一致を許容して反映
}
```

リクエストボディは `PaymentMfApplyRequest` (`backend/src/main/java/jp/co/oda32/dto/finance/paymentmf/PaymentMfApplyRequest.java`)。ボディ省略 (旧 client) は `force=false` 扱い。

**エラーレスポンス** (`force=false` で違反あり):

```
HTTP/1.1 422 Unprocessable Entity
Content-Type: application/json

{
  "message": "per-supplier 1 円整合性違反 N 件あり。プレビュー画面で詳細を確認の上、強制実行する場合は force=true を指定してください",
  "code": "PER_SUPPLIER_MISMATCH"
}
```

`FinanceExceptionHandler#handleFinanceBusiness` がエラーコード `PER_SUPPLIER_MISMATCH` を検出して 422 を返す (通常の `FinanceBusinessException` は 400)。エラーコードは `FinanceConstants.ERROR_CODE_PER_SUPPLIER_MISMATCH`。

**監査記録 (`force=true` 時)**:

`@AuditLog` aspect が記録する通常の after snapshot 行とは別に、`FinanceAuditWriter.write` を直接呼んで補足 row を 1 件追加する:

```
target_table  = t_accounts_payable_summary
operation     = payment_mf_apply_force
target_pk     = { uploadId, userNo, force=true, transferDate, transactionMonth, fileName }
before_values = null
after_values  = null
reason        = "FORCE_APPLIED: per-supplier mismatches count=N, details=[...]"
```

reason には先頭 50 件の mismatch 詳細を含む (`...(+M more)` で残数表示)。AOP 拡張せずに済むよう field injection で `FinanceAuditWriter` を Lazy 注入する案 a を採用 (案 b: `@AuditLog.reasonExpression` SpEL 拡張は将来必要になったら別途実施)。

**フロント UI** (`frontend/components/pages/finance/payment-mf-import.tsx`):

- preview レスポンスの `amountReconciliation.perSupplierMismatches` が空配列でない時:
  - 赤い `<Alert variant="destructive">` を preview 上部に表示 (mismatch 一覧 + 同意 checkbox)
  - 「買掛金一覧へ反映」ボタンは checkbox チェックまで disabled、ラベルが「強制反映 (force=true)」に変化
  - 「CSVダウンロード」ボタンは disabled (force 経路なし、Excel 修正必須)
  - `ConfirmDialog` のタイトル/説明/ラベルが force モード用に切替
  - 反映成功後 `forceAcknowledged` を false にリセット

**テスト**: `backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfImportServiceForceApplyTest.java` (8 テスト全 PASS)
- mismatch 空 + force=false → 通常成功
- mismatch 空 + force=true → 通常成功 (force は副作用なし)
- mismatch 非空 + force=false → 422 + `PER_SUPPLIER_MISMATCH`、payableService.save / auxRowRepository.saveAll 呼ばれない
- mismatch 非空 + force=true → 成功 + audit log 補足行に reason 記録
- `buildForceAppliedReason` 整形ロジック (空 / null / 少件数 / 50 件超) 4 パターン

#### 全体整合性チェック (1 円不許容)
- `Σ supplier 請求額 == 合計行 C` (既存 readMatched)
- `Σ supplier 振込金額 == 合計行 E` (新規 perSupplierTransferMatched)

#### aux_row テーブルとの関係 (Codex Critical C2 修正後, 2026-05-06)
- `PAYABLE_FEE/DISCOUNT/EARLY/OFFSET` 副行は **`t_payment_mf_aux_row` に保存する** (V038 で `chk_payment_mf_aux_rule_kind` 制約を拡張)
- 検証済みCSV出力 (`exportVerifiedCsv`、DB-only 経路) でも副行が `auxRowRepository.findByShopNoAndTransactionMonthOrderByTransferDateAscSequenceNoAsc` 経由で再構築される
- (旧記述) 「`PAYABLE_*` は aux 保存対象外、convert 経路でのみ MF に出る」は C2 修正前の挙動。修正後は applyVerification + exportVerifiedCsv 経路でも副行が MF CSV に乗る
- PAYABLE 主行のみ aux 保存対象外 (= `t_accounts_payable_summary` 由来のため重複排除)

### 5.5 DIRECT_PURCHASE 行の supplier 別 attribute 展開 + 自動降格 (P1-03 案 D-2 / P1-07 案 D, 2026-05-04 採用)

#### 5.5.1 自動降格ロジック (PAYABLE → DIRECT_PURCHASE)

**振込明細 Excel の 20日払いセクション (`section == PaymentMfSection.PAYMENT_20TH`) の PAYABLE ルールは自動で DIRECT_PURCHASE に降格される**。

業務的根拠:
- 20日払いセクションは「即払い (買掛金経由しない)」のため、買掛金で計上→消込ではなく直接「仕入高」計上が会計上正しい
- 同じ仕入先でも 5日払いセクション (本社仕入で買掛金計上→翌月 5日 で振込消込) と 20日払いセクション (即払いで仕入高直接計上) で会計フローが異なる

マスタ運用:
- `m_payment_mf_rule` で同一 supplier に対し PAYABLE / DIRECT_PURCHASE の二重登録は **不要**
- 通常の 5日払いセクションで使う PAYABLE ルール 1 本だけ登録すれば、20日払いセクションでも自動降格で DIRECT_PURCHASE 仕訳を生成
- 元から DIRECT_PURCHASE で登録されているルール (例: ワタキューセイモア等の 20日払い専用仕入先) は降格処理スルーで素通しになる

実装位置:
- `PaymentMfImportService#buildPreview` 内、`rule != null && e.section == PaymentMfSection.PAYMENT_20TH && "PAYABLE".equals(rule.getRuleKind())` 判定でルール builder を作り直す箇所 (元 PAYABLE → 借方仕入高 / 貸方資金複合 の DIRECT_PURCHASE に変換)
- 元ルールの `summaryTemplate` / `tag` / `creditDepartment` は継承 (SF-C07)
- 自動降格自体は P1-03 で既に実装済 → P1-07 では削除せず、設計書側で **明文化のみ** 実施
- **G2-M3 (2026-05-06)**: 旧 `e.afterTotal` ブール値フラグを {@link PaymentMfSection} 列挙型 (`PAYMENT_5TH` / `PAYMENT_20TH`) に置換。parser は合計行ごとに section 別 summary を保持し、合計行を踏むたびに次セクションへ遷移する。旧実装は最初の合計行 (= 5日払い summary) しか捕まえず、20日払い合計行を黙って捨てていたため整合性チェック (chk1/chk3) が「5日払い summary vs 5日払い+20日払い 両方の per-supplier sum」を比較する形に構造的にズレていた。

#### 5.5.2 P1-03 案 D-2 で 20日払いセクションも per-supplier attribute 展開

P1-03 案 D は 5日払い PAYABLE のみ supplier 別副行 (FEE/DISCOUNT/EARLY/OFFSET) を実装したが、20日払いセクションにも値引/早払/送料相手の入力は **頻繁にある** (旧 SUMMARY 集約 2 行で MF に出していた)。

P1-03 案 D-2 (P1-07 案 D で正式採用) では、5日払い PAYABLE と 20日払い DIRECT_PURCHASE (自動降格 + 元 DIRECT_PURCHASE 両方) の **両方** で supplier 別 attribute を展開する。判定は `needsSubRows = (PAYABLE or DIRECT_PURCHASE)`。

#### 5.5.3 出力ルール (per supplier, 20日払い DIRECT_PURCHASE)

| ruleKind | 借方 | 貸方 | 金額 | 条件 |
|---|---|---|---|---|
| `DIRECT_PURCHASE` | 仕入高/課税仕入 10% | 資金複合/対象外 | **列 E** (振込金額) | 必須 (列 E NULL 時は 請求 - 控除合計 で派生) |
| `DIRECT_PURCHASE_FEE` | 仕入高/課税仕入 10% | 仕入値引・戻し高/物販事業部/課税仕入-返還等 10% | 列 F (送料相手) | 列 F > 0 |
| `DIRECT_PURCHASE_DISCOUNT` | 仕入高/課税仕入 10% | 仕入値引・戻し高/物販事業部/課税仕入-返還等 10% | 列 G (値引) | 列 G > 0 |
| `DIRECT_PURCHASE_EARLY` | 仕入高/課税仕入 10% | 早払収益/物販事業部/非課税売上 | 列 H (早払) | 列 H > 0 |
| `DIRECT_PURCHASE_OFFSET` | 仕入高/課税仕入 10% | `m_offset_journal_rule` lookup (V041 seed: 仕入値引・戻し高/物販事業部/課税仕入-返還等 10%) | 列 I (相殺) | 列 I > 0 |

#### 5.5.4 5日払い PAYABLE / 20日払い DIRECT_PURCHASE の対称構造

例: 請求 ¥1,000 / 値引 ¥50 / 早払 ¥10 / 送料相手 ¥30 / 振込 ¥910

**5日払い PAYABLE**:
| ruleKind | 借方 | 貸方 | 金額 |
|---|---|---|---|
| PAYABLE | 買掛金 | 普通預金 | 910 |
| PAYABLE_FEE | 買掛金 | 仕入値引・戻し高 (課税仕入-返還等 10%) | 30 |
| PAYABLE_DISCOUNT | 買掛金 | 仕入値引・戻し高 (課税仕入-返還等 10%) | 50 |
| PAYABLE_EARLY | 買掛金 | 早払収益 (非課税売上) | 10 |
| PAYABLE_OFFSET | 買掛金 | `m_offset_journal_rule` lookup (V041 seed: 仕入値引・戻し高) | 相殺額 |

**20日払い DIRECT_PURCHASE**:
| ruleKind | 借方 | 貸方 | 金額 |
|---|---|---|---|
| DIRECT_PURCHASE | 仕入高 (課税仕入 10%) | 資金複合 (対象外) | 910 |
| DIRECT_PURCHASE_FEE | 仕入高 (課税仕入 10%) | 仕入値引・戻し高 (課税仕入-返還等 10%) | 30 |
| DIRECT_PURCHASE_DISCOUNT | 仕入高 (課税仕入 10%) | 仕入値引・戻し高 (課税仕入-返還等 10%) | 50 |
| DIRECT_PURCHASE_EARLY | 仕入高 (課税仕入 10%) | 早払収益 (非課税売上) | 10 |
| DIRECT_PURCHASE_OFFSET | 仕入高 (課税仕入 10%) | `m_offset_journal_rule` lookup (V041 seed: 仕入値引・戻し高) | 相殺額 |

合計借方仕入高 = 1,000 (請求額 = 仕入計上額)、合計貸方 = 振込 + 値引 + 早払 + 送料 = 1,000

#### 5.5.5 per-supplier 整合性チェック (1 円不許容, 5日払い + 20日払い 両セクション)

P1-03 案 D-2 で per-supplier 整合性チェックも 5日払い + 20日払い 両セクション対象に拡張:

```
列 C (請求額) == 列 E (振込) + 列 F (送料相手) + 列 G (値引) + 列 H (早払) + 列 I (相殺)
```

違反行は `AmountReconciliation.perSupplierMismatches` に集約し、preview 画面で警告表示。
mismatch メッセージには **section 表記 (`[5日払い]` / `[20日払い]`)** を付与してデバッグを容易にする。

> **G2-M2 (2026-05-06)**: 違反 1 件以上で `verify` / `convert` 両エンドポイントを **サーバー側でブロック** する (旧実装は警告のみで通っていた)。`verify` のみ `force=true` で突破可。詳細は §5.4 「G2-M2 サーバー側ブロック + force 上書き」を参照。

全体整合性チェック (G2-M3 で section 別判定に変更, 2026-05-06):
- **チェック1 (excelMatched)**: section ごとに `C - F - H == E` を判定。`excel5Diff == 0 && excel20Diff == 0` の AND で全体 OK 判定 (合算で偶然 0 でも片側非ゼロなら NG)。`AmountReconciliation.expectedTransferAmount / excelDifference` は UI 互換のため両 section 合算値を返す
- **チェック3 (perSupplierTransferMatched)**: section ごとに `Σ supplier 振込金額 == 合計行 E` を判定。`perSupplier5TransferDiff == 0 && perSupplier20TransferDiff == 0` の AND で全体 OK 判定。section 別違反は WARN ログのみで `perSupplierMismatches` には追加しない (= ブロック対象外)
- 旧実装は section 別差額が偶然打ち消し合う Excel で OK と誤判定するバグがあった (Codex G2-M3)。section 別判定で構造的に検出可能になる

#### 5.5.6 aux_row テーブルとの関係 (Codex Critical C2 修正後, 2026-05-06)

- `DIRECT_PURCHASE_FEE/DISCOUNT/EARLY/OFFSET` 副行は **`t_payment_mf_aux_row` に保存する** (V038 で `chk_payment_mf_aux_rule_kind` 制約を `'EXPENSE','SUMMARY','DIRECT_PURCHASE','PAYABLE_FEE','PAYABLE_DISCOUNT','PAYABLE_EARLY','PAYABLE_OFFSET','DIRECT_PURCHASE_FEE','DIRECT_PURCHASE_DISCOUNT','DIRECT_PURCHASE_EARLY','DIRECT_PURCHASE_OFFSET'` に拡張、`rule_kind` 列幅を `VARCHAR(20)` → `VARCHAR(30)` に拡張)
- `saveAuxRowsForVerification` のガード条件を簡素化: `"PAYABLE".equals(ruleKind)` のみ skip (PAYABLE 主行は `t_accounts_payable_summary` 由来で重複排除)。それ以外の主行・副行は全て aux 保存
- `DIRECT_PURCHASE` 主行・副行ともに aux 保存対象
- 検証済みCSV出力 (`exportVerifiedCsv`、DB-only 経路) でも副行が aux テーブルから再構築されるため、20日払いの値引/早払/送料相手も MF CSV に出力される (旧記述の「convert 経路でのみ MF に出る」は C2 修正前の挙動)
- aux 行のソート順は `transferDate ASC, sequenceNo ASC`。副行の `sequence_no` は preview 内出現順 (= Excel 順) を踏襲するため、副行は対応する主行近傍に並ぶ

#### 5.5.7 税理士確認 TODO

- **「借方 仕入高 + 貸方 仕入値引・戻し高」の不自然さ**: 同じ仕入先の同じ請求書に対し「仕入高 1,000 計上 + 同時に値引 50 を相殺戻し」という形になり、純額で見ると `仕入高 1,000 - 戻し 50 = 950` になるが、計上時に「買って同時に値引」というのは会計慣行上やや特殊
  - **代替案**: 借方仕入高 = 振込金額のみ (例: 910) として、値引/早払/送料相手は仕入額から控除済みの純額で計上する形 (= 仕入計上額が振込額と一致)
  - 5日払い PAYABLE 側との対称性を取るか、20日払いだけ純額方式にするか、要相談
- **相殺 (`PAYABLE_OFFSET` / `DIRECT_PURCHASE_OFFSET`) の貸方科目**:
  - **G2-M8 (2026-05-06, V041) でマスタ管理に移行済**: ハードコードしていた「仕入値引・戻し高 / 物販事業部 / 課税仕入-返還等 10%」を `m_offset_journal_rule` テーブルから lookup する形に切替。税理士確認結果が得られたら admin が `/admin/offset-journal-rule` 画面から値を変更できる (再 deploy 不要)
  - V041 の seed では従来ハードコード値と同一の値を投入しているため migration 適用直後は仕訳出力が不変
  - 業務実態確認済: OFFSET はほぼ全て仕入返品 (値引扱いで OK) — 税理士の最終確認が短期 (数週間) で取れる前提のためマスタ化のみで対応 (運用継続中の本番仕訳に毎月出続けるリスクを軽減)
  - 売掛金との相殺仕訳 (借方 売掛金) を別途切るパターンが正しい場合は admin 画面で `creditAccount`/`creditTaxCategory`/`summaryPrefix` を更新

#### 5.5.7.1 OFFSET 仕訳マスタ (m_offset_journal_rule, V041)

| 列 | 型 | 例 | 備考 |
|---|---|---|---|
| `id` | SERIAL PK | 1 | |
| `shop_no` | INTEGER | 1 | shop 単位の lookup キー |
| `credit_account` | VARCHAR(100) | 仕入値引・戻し高 | 貸方勘定科目 |
| `credit_sub_account` | VARCHAR(100) | NULL | 貸方補助 (現状未使用) |
| `credit_department` | VARCHAR(100) | 物販事業部 | 貸方部門 |
| `credit_tax_category` | VARCHAR(100) | 課税仕入-返還等 10% | 貸方税区分 |
| `summary_prefix` | VARCHAR(100) | `相殺／` | 摘要プレフィックス。`{prefix}{sourceName}` で組立 |
| `del_flg` | CHAR(1) | `0` | UNIQUE (shop_no, del_flg) で active 行は shop あたり最大 1 件 |

- API: `GET/POST/PUT/DELETE /api/v1/finance/offset-journal-rules` (admin 限定: `@loginUserSecurityBean.isAdmin()`)
- Service: `MOffsetJournalRuleService` (CRUD + `@AuditLog` で T2 監査証跡対象)
- Lookup: `PaymentMfImportService` 内 OFFSET 副行生成時に `findByShopNoAndDelFlg(ACCOUNTS_PAYABLE_SHOP_NO, "0")` を呼び出し。未登録時は `IllegalStateException` (V041 seed 済のため業務上発生しない)
- 関連テスト: `MOffsetJournalRuleServiceTest`, `PaymentMfImportServiceGoldenMasterTest` (mst モック注入で従来挙動と意味等価を保証)
- 残課題: `FEE` / `DISCOUNT` / `EARLY` 副行も同様にマスタ化は将来 task。現状は税理士確認待ちの優先度で OFFSET のみ先行

### 5.6 verified_amount の書込経路と挙動 (P1-09 案 D / Codex Critical C3 / G2-M1+M10 修正, 2026-05-06)

`t_accounts_payable_summary.verified_amount` (税込) は supplier × month の集約値であり、
書込経路によって税率別行 (tax_rate 違い) で同値か否かが異なる。

**G2-M1/M10 (V040)**: 旧実装は「金額パターン全行同値」推定 + 「verification_note 接頭辞」推定で
書込経路を識別していたが、(a) MANUAL で偶然全行同値 → 過少計上、(b) BULK 後の単行修正 → 過大計上、
(c) note 接頭辞偽装 → 保護外れ のリスクがあった。`verification_source` enum 列を追加し、
書込時に経路を明示記録する運用に切替。

#### 背景
- 振込明細 Excel には税率別内訳がない (supplier × month の請求総額のみ)
- DB は税率別行で持つ (仕入仕訳の借方仕入高を税率別に出力するため)
- → applyVerification 経由では集約値を全税率行に冗長保持する設計

#### 書込経路と verification_source 列 (V040 で追加)

| 書込経路 | `verification_source` | 税率別行の値 | 補足 |
|---|---|---|---|
| `PaymentMfImportService.applyVerification` (振込明細一括検証) | `BULK_VERIFICATION` | **全税率行で同値** (集約値) | 全行同値冗長保持の不変条件が成立 |
| `TAccountsPayableSummaryService.verify` (手動行単位 verify) | `MANUAL_VERIFICATION` | 単一 PK 行のみ更新 → 税率別に異なる | UI からの個別行 verify。他税率行は更新されない |
| `ConsistencyReviewService.applyMfOverride` (MF override 適用) | `MF_OVERRIDE` | 税率別按分 → 税率別に異なる | MF debit を税率別 change 比で按分書込 |
| 未検証 (verified_manually=false) | `NULL` | – | read 側は taxIncludedAmountChange にフォールバック |

#### 仕訳生成での扱い
| 仕訳種別 | フィールド | 税率別の差 |
|---|---|---|
| 振込仕訳 (借方買掛金/貸方普通預金) | verified_amount | 集約値 1 度のみ参照 (税率不要) |
| 仕入仕訳 (借方仕入高/貸方買掛金) | verified_amount_tax_excluded | 税率別に異なる (税率別逆算) |

#### read 側のフォールバック (G2-M1, V040 改修)
- `sumVerifiedAmountForGroup`: `verification_source` 列で経路判定
  - **全行 `BULK_VERIFICATION`**: 代表値 1 度 (冗長保持の集約値)
    - 念のため per-row 値を比較し、不一致なら WARN ログ + SUM フォールバック (DB 直接 UPDATE 等の異常検知)
  - **1 行でも `MANUAL_VERIFICATION` / `MF_OVERRIDE` / `NULL` 混在**: 税率別 SUM (本来の集約値)
- 書込側で invariant を強制するのではなく、read 側で経路明示判定 (推定ロジック廃止)

#### 再 upload 保護 (G2-M10, V040 改修)
- `applyVerification` は同 supplier × txMonth で 1 行でも `MANUAL_VERIFICATION` 由来があれば全体スキップ
- 旧実装は note 接頭辞 (`"振込明細検証 "`) で BULK/MANUAL 推定 → 偽装 note で保護外れリスクあり
- 新実装は `verification_source` 列で経路判定 → note 文字列依存を撤去

#### V040 backfill ルール
- `verification_note LIKE '振込明細検証 %'` → `BULK_VERIFICATION`
- `verified_manually = true` (上記以外) → `MANUAL_VERIFICATION`
- 上記以外 → `NULL`
- `MF_OVERRIDE` は backfill 対象外 (将来 applyMfOverride 実行時にのみ書込)
- backfill 時点の note 接頭辞は applyVerification がアプリコードで一括書込しているため信頼できる
- `VERIFICATION_NOTE_BULK_PREFIX` は `@Deprecated` 化 (note 表示生成のみで使用、判定には不使用)

#### CHECK 制約
```sql
CHECK (verification_source IS NULL OR verification_source IN
    ('BULK_VERIFICATION', 'MANUAL_VERIFICATION', 'MF_OVERRIDE'))
```

#### 既知の制限
- `ConsistencyReviewService.rollbackVerifiedAmounts`: `previous_verified_amounts` JSON snapshot に
  source 情報を含めていないため、MF_OVERRIDE 適用後にロールバックすると元の source (BULK/MANUAL/NULL) を
  厳密に復元できない。復元値が 0/null の場合のみ source=NULL に戻す best-effort 実装。
  非ゼロ復元時は source=`MF_OVERRIDE` のまま残るため、必要に応じてユーザが再 verify で上書き。

#### 関連
- P1-03 案 D-2: per-supplier 値引/早払/送料の MF 仕訳展開 (本トピックとは別、業務目的は達成済)
- P1-09 将来 案 E (未実施): 集約値テーブル + 税率別 breakdown テーブルへの分離 (業務要件発生時のみ)
- G2-M1/M10 関連テスト: `PaymentMfImportServiceVerifiedAmountTest`, `PaymentMfImportServiceManualLockTest`

---

## 6. 変換履歴

```sql
CREATE TABLE t_payment_mf_import_history (
    id                SERIAL PRIMARY KEY,
    shop_no           INTEGER     NOT NULL,
    transfer_date     DATE        NOT NULL,      -- 送金日（E1）
    source_filename   VARCHAR(255) NOT NULL,
    csv_filename      VARCHAR(255) NOT NULL,
    row_count         INTEGER     NOT NULL,
    total_amount      BIGINT      NOT NULL,
    unmatched_count   INTEGER     NOT NULL,      -- 🔴件数
    diff_count        INTEGER     NOT NULL,      -- 🟡件数
    csv_body          BYTEA,                     -- 再ダウンロード用
    add_date_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    add_user_no       INTEGER
);
```

履歴画面は今フェーズでは**一覧表示のみ**（再ダウンロードリンク付き）。

---

## 7. バックエンド

### エンドポイント

| Method | Path | 用途 |
|---|---|---|
| POST | `/api/v1/finance/payment-mf-import/preview` | xlsx アップロード → プレビュー返却（`uploadId` 含む） |
| GET  | `/api/v1/finance/payment-mf-import/preview/{uploadId}` | プレビュー再取得（マスタ編集後） |
| POST | `/api/v1/finance/payment-mf-import/convert/{uploadId}` | CSV生成 + 履歴保存 → CSV返却 |
| POST | `/api/v1/finance/payment-mf-import/bulk-verify/{uploadId}` | 買掛金一括検証（`verified_amount` / `verifiedManually=true` をセット） |
| GET  | `/api/v1/finance/payment-mf-import/history` | 履歴一覧 |
| GET  | `/api/v1/finance/payment-mf-import/history/{id}/csv` | 履歴からCSV再ダウンロード |
| GET/POST/PUT/DELETE | `/api/v1/finance/payment-mf-rules` | マスタCRUD（cashbookのmf-rulesと同様） |
| POST | `/api/v1/finance/payment-mf/rules/backfill-codes?dryRun=true\|false` | 支払先コード自動補完（admin） |

#### `payment_supplier_code` 自動補完（backfill-codes）

PAYABLE ルールで `payment_supplier_code` が未設定のものに対し、`source_name` を正規化して
`m_payment_supplier.payment_supplier_name` と名寄せマッチし、コードを自動補完する。

正規化ルール:
- NFKC 正規化
- 「株式会社」「㈱」「（株）」を除去
- 「支店」などの営業所記号（[20] 等）を除去
- 全/半角空白を除去

`dryRun=true` でプレビュー（対象件数・マッチ内訳を返却）、`dryRun=false` で実適用。
画面の「支払先コード自動補完」ボタン（admin、`/finance/payment-mf-rules` ヘッダー）から起動。

### 正規化・マッチングユーティリティ

- `normalizePaymentSupplierCode`: Excel 側の仕入コード（2-3桁、例: `12`）を DB 形式（6桁ゼロ埋め、×100、例: `001200`）に正規化
- `deriveTransactionMonth`: 5日払い・20日払いともに前月20日締めに統一（§5.1）
- reconcileCode fallback: Excel 側にコードが無い（送り先名のみ）行でも、バックフィルで埋めたルール側のコードを使って `t_accounts_payable_summary` と突合可能

### クラス
- `PaymentMfImportController` — API層
- `PaymentMfImportService` — Excel解析 + ルール適用 + 突合 + CSV生成
- `PaymentMfRuleService` / `PaymentMfRuleController` — マスタCRUD
- `MPaymentMfRule` / `TPaymentMfImportHistory` — Entity
- `PaymentMfImportPreviewResponse` — `{ uploadId, transferDate, rows: [{source, amount, rule?, status, matchedPayable?}], summary: {matched, diff, unmatched} }`

### uploadIdキャッシュ
cashbook-import と同じパターン: `ConcurrentHashMap<String, ParsedWorkbook>` + `@Scheduled` TTL 30分 + `enforceCacheLimit(100)`。

> **重要 (single-instance 前提)**: `uploadId キャッシュ` は JVM ヒープ上の `ConcurrentHashMap` で保持されるため、**single-instance 運用前提**。マルチノード構成にスケールアウトする場合は Redis / Postgres の共有ストアに寄せる必要がある（preview 後に別ノードで convert/verify する経路が壊れるため）。マルチノード化の設計判断は DEF-C01 で別途検討。

### 同時実行制御 (advisory lock)

`applyVerification` / `exportVerifiedCsv` は同一 `transactionMonth` を **直列化** する目的で `pg_advisory_xact_lock(transactionMonth.toEpochDay)` を取得する。これにより:

- 同一取引月への同時 `applyVerification`（5日 と 20日 を別ユーザが同時取込）でも `t_payment_mf_aux_row` の DELETE→INSERT 洗い替えが衝突しない。
- `applyVerification` 進行中の `exportVerifiedCsv` は完了を待つ（中途半端な aux 行で CSV が生成されない）。
- `pg_advisory_xact_lock` は **トランザクション境界で自動解放** されるため、early return / 例外時もロック残留リスクなし。

別 shop_no 展開時はキーに shop_no を含意する必要があるが、現状 shop_no=1 固定 (`FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO`) のため `transactionMonth.toEpochDay` 単独で十分（DEF-C02）。

### 依存
- Excel解析: 既存 Apache POI（cashbook-import と共通）
- CSV出力: `OutputStreamWriter(Charset.forName("MS932"))` + 明示 `\n` 改行

### 7.4 P1-08 再取込防止 (案 β L1 警告 + L2 確定保護, 2026-05-04)

業務ヒアリング (Q1〜Q3) を踏まえ、振込明細 Excel の再 upload に対し 2 段階のガードを導入する。

#### 動機
- 通常 Q1-a (誤再 upload): 稀
- 通常 Q1-b (修正版意図的再 upload): 月 1 回程度。請求書と再確認して修正版を取り込む運用がある
- 単一仕入先の **手動 verify (`VerifyDialog`)** で個別調整した行を、後続のバルク再 upload で silent に上書きされたくない (Q3-(ii))

#### L1: 同一ハッシュ検知 (preview 警告)
- `preview()` で取込元バイト列の SHA-256 を `computeSha256Hex()` で算出し `CachedUpload.sourceFileHash` に保存
- `convert` / `applyVerification` 時に history 行に `source_file_hash` を永続化
- 後続 preview で `findBySourceFileHashAndDelFlgOrderByAddDateTimeDesc` を引き、ヒットすれば `DuplicateWarning(previousUploadedAt, previousFilename, previousUploadedByUserNo)` を返却
- UI は amber Alert で「同一内容のファイルが既に取込済」を表示。CSV/反映ボタンは無効化しない (修正版意図的取込ケースを許容)

#### L2: 確定済 (`applied_at`) 警告 + 手動確定保護
- `applyVerification` 完了時に新規 history 行を 1 件追加し `applied_at = now()`, `applied_by_user_no = login user`, `source_file_hash` を記録
  - csv_filename は `applied_{yyyymmdd}.marker`、csv_body は NULL (CSV 生成フローではないため)
  - history 保存失敗は本体検証結果に影響させない (verified_manually=true は既に永続化済)
- 後続 preview で `findFirstByShopNoAndTransferDateAndAppliedAtNotNullAndDelFlgOrderByAppliedAtDesc(1, transferDate, "0")` を引き、ヒットすれば `AppliedWarning(appliedAt, appliedByUserNo, transactionMonth, transferDate)` を返却
- UI は destructive Alert で「この月は既に確定済」を表示。確定ボタンの ConfirmDialog 文言を「⚠️ 既に確定済の月を再確定」+「上書きして再確定する」に切替

#### Q3-(ii) `verified_manually=true` 行スキップ
- `applyVerification` の supplier ループで、対象 `t_accounts_payable_summary` 群に **`verified_manually=true` かつ `verification_note` が `VERIFICATION_NOTE_BULK_PREFIX ("振込明細検証 ")` で始まらない** 行が 1 つでもあれば supplier 全体を skip
  - これにより **bulk 由来の確定はバルク再取込で上書き可能**、**単一仕入先 `VerifyDialog` 由来の手動調整は保護** という分離が成立する
  - Cluster D `AccountsPayableVerificationReportTasklet` SF-02 と同じ防御パターン
- skip 件数は `VerifyResult.skippedManuallyVerifiedCount` に集計、UI で `toast.warning("手動確定済の N 件は保護のため上書きしませんでした")` 表示

#### マイグレーション (V034)
```sql
ALTER TABLE t_payment_mf_import_history
    ADD COLUMN IF NOT EXISTS source_file_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS applied_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS applied_by_user_no INTEGER NULL;

CREATE INDEX IF NOT EXISTS idx_payment_mf_import_history_hash
    ON t_payment_mf_import_history (source_file_hash);
CREATE INDEX IF NOT EXISTS idx_payment_mf_import_history_applied
    ON t_payment_mf_import_history (shop_no, transfer_date) WHERE applied_at IS NOT NULL;
```

#### 影響範囲
- Entity: `TPaymentMfImportHistory` に 3 フィールド追加 (`sourceFileHash`, `appliedAt`, `appliedByUserNo`)
- DTO 新規: `DuplicateWarning`, `AppliedWarning` (record)
- DTO 変更: `PaymentMfPreviewResponse` に warning 2 fields 追加、`PaymentMfImportService.VerifyResult` に `skippedManuallyVerifiedCount` 追加
- API 互換性: 既存クライアントは新 field を無視できる (additive change)

---

## 8. フロントエンド

### 画面構成

**`/finance/payment-mf-import`** — 3ステップ（cashbook-import踏襲）
1. **Step1 Upload**: xlsxドロップ + アップロード
2. **Step2 Preview**:
   - ヘッダ: 送金日、件数サマリ（🟢/🟡/🔴）、合計金額
   - テーブル: 送り先 / 支払先コード / 請求額 / 買掛金額 / 差額 / 仕訳 / ステータス
   - 🔴行は「マスタ登録」ボタン → ダイアログで `m_payment_mf_rule` 登録
   - 🟡行は買掛金一覧画面へのリンク（`/finance/accounts-payable?supplierNo=X&transactionMonth=Y`）
   - マスタ更新後は同 `uploadId` でプレビュー再取得
3. **Step3 Download**: CSVダウンロードボタン

#### BulkVerifyDialog（振込明細で一括検証）

プレビュー後、買掛金サマリへ一括検証する確認ダイアログ。強化機能:

- **未登録セクション**: 右上に「マスタマスタを確認」ボタン（`/finance/payment-mf-rules` へ遷移）
- 各未登録行の「マスタで検索」リンク: `?q=<送り先名>` で検索語プリフィル
- **買掛金なし行一覧**: 赤色ボックス、行ごとにコード・送り先名・金額を表示
- 実行時: `POST /api/v1/finance/payment-mf-import/bulk-verify/{uploadId}`
  - `PaymentMfImportService#applyVerification` が `t_accounts_payable_summary` に以下をセット
    - `verified_amount = Excel請求額`
    - `verified_manually = true`
    - `verification_note = 「振込明細 yyyy-MM-dd 一括検証」` 等
  - 税率別複数行には同一 `verified_amount` を同期セット
  - 一致判定（100円閾値）→ `verification_result`, `payment_difference` を更新

**`/finance/payment-mf-rules`** — マスタCRUD
cashbook の `/finance/mf-journal-rules` のUIを流用。強化機能:

- **ルール複製ボタン**: 各行に Copy アイコン。既存ルールを雛形に新規ルール作成
- **「支払先コード自動補完」ボタン**（admin、ヘッダー）: `POST /payment-mf/rules/backfill-codes`
  - `dryRun=true` でプレビュー（対象件数・マッチ内訳）
  - 一括適用で `dryRun=false`
- 会社名正規化検索（株式会社 / ㈱ / 空白ゆれを吸収して比較）
- `?q=<検索語>` URLパラメータで検索語プリフィル対応（未登録行リンクから誘導）

**`/finance/payment-mf-history`** — 変換履歴
日付降順一覧 + 再ダウンロード（後回し可）。

### サイドバー
「見積・財務」グループに「買掛仕入MF変換」「買掛仕入MFルール」追加。

---

## 9. テスト計画（概要）

- **ゴールデンマスタテスト**: 過去の振込み明細xlsx 3本以上（20260120 / 20260205 / 20260220）と対応CSV を固定ディレクトリに置き、Java版で再生成してバイト等価（CP932+LF）を検証。
- **ユニット**: Excel解析、メタ行スキップ、送り先名正規化、ルール適用優先順、突合判定（100円閾値・境界）
- **E2E**: アップロード→プレビュー→マスタ登録→CSVダウンロード→履歴確認

---

## 10. 実装順

1. マスタテーブル作成 + シード投入（過去CSVから抽出）
2. Entity / Repository / Service（Excel解析 + ルール適用）
3. Controller + DTO
4. ゴールデンマスタテスト（CI化）
5. Frontend（upload → preview → download）
6. 買掛金突合ロジック + ハイライト
7. マスタCRUD画面
8. 変換履歴

---

## 11. 決定事項 & 残論点

### 確定
- **シート構造差分**: 5日払い（`振込明細`）/ 20日払い（`支払い明細`）は**列レイアウトがずれる**（G列の自社/値引、H/I列の早払位置など）。→ ヘッダ名での列マップ動的構築で吸収（§2.2）。
- **取引月決定**: 5日払い・20日払いともに **前月20日締め分** と突合（§5.1, 仕様変更済み）。
- **合計行の判定**: B列文字列 `合計` でヒット。位置は固定しない（§2.3）。
- **supplier 別 attribute (P1-03 案 D, 2026-05-04)**: per-supplier の値引/早払/送料相手/相殺/振込金額を Excel から読取り、PAYABLE 主行 + 4 副行 (FEE/DISCOUNT/EARLY/OFFSET) に展開する。旧 SUMMARY 集約 (合計仕訳 2 行) は撤去。`§5.4` 参照。
- **新ルール種別**: `PAYABLE_FEE` / `PAYABLE_DISCOUNT` / `PAYABLE_EARLY` / `PAYABLE_OFFSET` は `PaymentMfPreviewRow` / CSV 出力でのみ使用 (aux_row テーブルには保存しない)。
- **シード出典**: MVP時点で **`変換MAP` シート（56件想定）全件**を `m_payment_mf_rule` に投入（PAYABLE扱い）。加えて過去3ヶ月CSVから固定費行（EXPENSE/DIRECT_PURCHASE）約15件を抽出して追加。
- **一括検証の永続化**: `t_accounts_payable_summary.verified_amount` + `verified_manually=true` に書込。税率別複数行には同一値を同期。
- **支払先コード正規化**: Excel 2-3桁コード → DB 6桁ゼロ埋め（×100）に `normalizePaymentSupplierCode` で変換。
- **reconcile fallback**: Excel 側にコードが無い行でも、バックフィル済ルールのコードで突合可能。
- **バックフィル運用**: `m_payment_mf_rule` PAYABLE 93件[^v011-seed] に `payment_supplier_code` を自動補完済み（2026-04-15）。

[^v011-seed]: V011 シード時点の実数 (2026-05-04 確認)。設計時 (2026-04-15) は 74 件想定だったが、その後の `変換MAP` シート追加分を含めて最終 93 件となった。

### 残論点（実装中に詰める）
- `m_payment_supplier.payment_supplier_code` のDB内実型（VARCHAR/INT）確認 → Excel A列との照合時の型変換。
- 過去の `変換MAP` シートで値が `None` の行（例: `中国鉄管継手㈱`）の扱い → `m_payment_mf_rule` に登録しない or 登録してMF補助科目NULL運用にするか。
- CSVファイル名規約 `買掛仕入MFインポートファイル_{yyyymmdd}.csv`（送金日ベース）で固定。
- **P1-03 案 D 残課題 (2026-05-04)**:
  - 新ゴールデンマスタ生成: 実 Excel `振込み明細08-4-20.xlsx` 等で取込→出力→検収→既存 fixture 置き換え。それまで `PaymentMfImportServiceGoldenMasterTest` / `PaymentMfImportServiceAuxRowTest` は `@Disabled`。
  - **PAYABLE_OFFSET 貸方科目 (列 I 相殺)** の税理士確認: 暫定で「仕入値引・戻し高 / 課税仕入-返還等 10%」だが、本来は売掛金との相殺仕訳が正しい可能性あり。
  - `exportVerifiedCsv` (DB-only 経路) は per-supplier attribute を持たないため、Excel 取込経由の `convert` とは出力構造が異なる。supplier 別の振込手数料値引/値引/早払/相殺の MF 仕訳は **Excel 取込フローを使うこと** (`/finance/payment-mf-import` 画面)。検証済みCSV出力 (`/finance/accounts-payable` の「検証済みMF出力」) は PAYABLE 主行のみ。
