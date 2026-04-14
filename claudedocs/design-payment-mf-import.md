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

### 2.4 特殊サマリー仕訳

「合計」行から下記の2行を必ず生成する（値が 0 でも生成。既存運用に合わせる）。

| 項目 | 借方 | 貸方 | 摘要 |
|---|---|---|---|
| 振込手数料値引 | 資金複合 / 対象外 | 仕入値引・戻し高 / 物販事業部 / 課税仕入-返還等10% | `振込手数料値引／{D}日払い分` |
| 早払収益 | 資金複合 / 対象外 | 早払収益 / 物販事業部 / 非課税売上 | `早払収益／{D}日払い分` |

- `{D}` = 送金日(`E1`)の**日部分**（5 or 20）
- 金額は合計行の送料相手列 / 早払い列（列マップでヘッダ名参照）

### 2.5 無視する列

以下は仕訳には利用しない（プレビュー画面でも表示しない）:
- `打込金額` / `振込金額`（仕訳金額は`請求額`のみ使用）
- `自社` / `値引` / `相殺` / `備考`
- `福通運賃明細` シート（紙運用の参考資料）

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

### 行種別

| 種別 | 借方 | 貸方 | 備考 |
|---|---|---|---|
| 買掛金支払 | 買掛金/{MF補助科目}/対象外 | 資金複合/対象外 | 支払先コードあり |
| 運賃（仕入紐付） | 荷造運賃/物販事業部/課税仕入10% | 資金複合/対象外 | 送り先名固定ルール |
| 消耗品費 | 消耗品費/対象外 | 資金複合/対象外 | 〃 |
| 車両費 | 車両費/対象外 | 資金複合/対象外 | 〃 |
| 仕入高 | 仕入高/課税仕入10% | 資金複合/対象外 | 〃（20日払いセクション含む） |
| 振込手数料値引 | 資金複合/対象外 | 仕入値引・戻し高/物販事業部/課税仕入-返還等10% | サマリー行F列 |
| 早払収益 | 資金複合/対象外 | 早払収益/物販事業部/非課税売上 | サマリー行H列 |

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

### 5.1 取引月の決定ルール

送金日(`E1`)の日付から、以下で買掛金一覧の `transaction_month` を決定:

| 送金日の日 | 突合対象の取引月 | 根拠 |
|---|---|---|
| 5日 | **前月20日締め分** = `前月の年-月-20` | 20日締め → 翌月5日払い運用 |
| 20日 | **当月20日締め分** = `当月の年-月-20` | 20日締め → 当月20日払い運用 |
| その他 | エラー or 警告（想定外） | |

例:
- 送金日 2026/2/5 → 取引月 2026-01-20
- 送金日 2026/2/20 → 取引月 2026-02-20

### 5.2 突合処理

```
for each 買掛金行 in 支払い明細:
    payableSummary = find(t_accounts_payable_summary,
        shopNo=1, paymentSupplierCode=excelCode, transactionMonth=送金日の取引月)
    if not payableSummary:
        status = 🔴 '買掛金なし'
    else if abs(payableSummary.verified_amount - 請求額) <= 100円:
        status = 🟢 '一致'
    else:
        status = 🟡 '金額差異'
        diff = payableSummary.verified_amount - 請求額
```

100円閾値は既存 `SmilePaymentVerifier` と同じ扱い。固定費行（運賃等・20日払いセクションの仕入高行）は突合対象外で常に白表示。

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
| GET  | `/api/v1/finance/payment-mf-import/history` | 履歴一覧 |
| GET  | `/api/v1/finance/payment-mf-import/history/{id}/csv` | 履歴からCSV再ダウンロード |
| GET/POST/PUT/DELETE | `/api/v1/finance/payment-mf-rules` | マスタCRUD（cashbookのmf-rulesと同様） |

### クラス
- `PaymentMfImportController` — API層
- `PaymentMfImportService` — Excel解析 + ルール適用 + 突合 + CSV生成
- `PaymentMfRuleService` / `PaymentMfRuleController` — マスタCRUD
- `MPaymentMfRule` / `TPaymentMfImportHistory` — Entity
- `PaymentMfImportPreviewResponse` — `{ uploadId, transferDate, rows: [{source, amount, rule?, status, matchedPayable?}], summary: {matched, diff, unmatched} }`

### uploadIdキャッシュ
cashbook-import と同じパターン: `ConcurrentHashMap<String, ParsedWorkbook>` + `@Scheduled` TTL 30分 + `enforceCacheLimit(100)`。

### 依存
- Excel解析: 既存 Apache POI（cashbook-import と共通）
- CSV出力: `OutputStreamWriter(Charset.forName("MS932"))` + 明示 `\n` 改行

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

**`/finance/payment-mf-rules`** — マスタCRUD
cashbook の `/finance/mf-journal-rules` のUIを流用。

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
- **取引月決定**: 送金日が5日=前月20日締め / 20日=当月20日締めと突合（§5.1）。
- **合計行の判定**: B列文字列 `合計` でヒット。位置は固定しない（§2.3）。
- **無視する列**: 自社 / 値引 / 相殺 / 備考 / 打込金額 / 振込金額（§2.5）。
- **シード出典**: MVP時点で **`変換MAP` シート（56件想定）全件**を `m_payment_mf_rule` に投入（PAYABLE扱い）。加えて過去3ヶ月CSVから固定費行（EXPENSE/DIRECT_PURCHASE）約15件を抽出して追加。

### 残論点（実装中に詰める）
- `m_payment_supplier.payment_supplier_code` のDB内実型（VARCHAR/INT）確認 → Excel A列との照合時の型変換。
- 過去の `変換MAP` シートで値が `None` の行（例: `中国鉄管継手㈱`）の扱い → `m_payment_mf_rule` に登録しない or 登録してMF補助科目NULL運用にするか。
- CSVファイル名規約 `買掛仕入MFインポートファイル_{yyyymmdd}.csv`（送金日ベース）で固定。
