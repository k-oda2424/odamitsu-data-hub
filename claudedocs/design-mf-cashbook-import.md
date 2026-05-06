# 現金出納帳 → MoneyForward仕訳帳CSV変換 設計書

作成日: 2026-04-13

## 概要
既存の `C:\project\mf_import` (Python製ツール) を本システムに移植する。
現金出納帳Excelをアップロードし、MoneyForward仕訳帳インポート用CSVに変換する。
得意先マッピング未登録行は画面で補正して再実行できる。

## 要件

### 機能
1. 現金出納帳Excel（`記入`シート）をアップロード
2. 15種類の仕訳パターンに従って借方/貸方を自動生成
3. 経理Excel上の略称を正式MF得意先名にマッピング
4. 未マッピング得意先は画面で一覧表示→1クリック登録→再変換
5. CSV（UTF-8 BOM付き、19列）をダウンロード

### 非機能
- shopNo=1 固定（第1事業部の個人売上含むため全社共通扱い）
- 既存Pythonツールと**意味等価**（行数・取引日・金額・勘定科目・補助科目・部門・税区分・摘要の文字列一致）。CSVバイト一致は目標外（pandas独自フォーマット差異のため）。Java側は固定CSVフォーマット（UTF-8 BOM、CRLF、QUOTE_MINIMAL相当、空値=空文字、数値=整数str）を自前Writerで実装。
  - **CRLF 採用理由**: Python `pandas.to_csv` の既定改行は OS 既定 (`os.linesep`) で、Windows 運用環境では CRLF が出力される。本ツールは MoneyForward 仕訳帳インポート向けの Excel 互換 CSV であり、MEMORY.md の `feature-mf-cashbook-import` (CRLF+BOM CSV) とも整合する。Java 移植版も CRLF 固定とし、Python 出力との意味等価を維持する。

## 新規テーブル

### `m_mf_journal_rule`（仕訳ルール）
Pythonの `if/elif` 分岐を1行1ルールでデータ化。

| カラム | 型 | 説明 |
|---|---|---|
| id | SERIAL PK | |
| description_c | VARCHAR(50) | 摘要C列（空白除去正規化値） |
| description_d_keyword | VARCHAR(100) NULL | 摘要D列の部分一致（サブ条件） |
| priority | INTEGER | 評価順（小さい＝先）、キーワード有>NULL |
| amount_source | VARCHAR(10) | `INCOME` / `PAYMENT` |
| debit_account | VARCHAR(50) | 借方勘定科目 |
| debit_sub_account | VARCHAR(100) | |
| debit_department | VARCHAR(50) | |
| debit_tax_resolver | VARCHAR(30) | 税区分リゾルバコード（下表） |
| credit_account | VARCHAR(50) | |
| credit_sub_account | VARCHAR(100) | |
| credit_sub_account_template | VARCHAR(100) | `{client}` プレースホルダ対応 |
| credit_department | VARCHAR(50) | |
| credit_tax_resolver | VARCHAR(30) | |
| summary_template | VARCHAR(200) | `{description_d}` 等プレースホルダ |
| requires_client_mapping | BOOLEAN | true=未マッピング時エラー |
| del_flg / 監査 | 共通 | |

### 税区分リゾルバ（enum・コード側）
Python全文を精査し、税区分語彙は `軽8%` / `軽８％` のみ（非課税/不課税/旧税率は出現しない）と確認済み。

| コード | 判定 | 出力 |
|---|---|---|
| OUTSIDE | 常に | `対象外` |
| OUTSIDE_PURCHASE_FULL | 常に | `対象外仕入`（大竹市ﾘｻｲｸﾙｾﾝﾀｰ専用） |
| OUTSIDE_PURCHASE_SHORT | 常に | `対象外仕`（宮島松大汽船専用） |
| SALES_10 | 常に | `課税売上 10%`（雑収入専用） |
| PURCHASE_10 | 常に | `課税仕入 10%`（通信費・運賃） |
| PURCHASE_10_TRAVEL | 常に | `課仕 10%`（旅費交通費専用） |
| SALES_AUTO | D列「軽8%」含む | `課売 (軽)8%` / else `課売 10%` |
| PURCHASE_AUTO | D列「軽8%」含む | `課税仕入 (軽)8%` / else `課税仕入 10%` |
| PURCHASE_AUTO_WIDE [^1] | D列「軽8%」「軽８％」含む | `課税仕入 (軽)8%` / else `課税仕入 10%` |

[^1]: `PURCHASE_AUTO_WIDE` は `PURCHASE_AUTO` と判定出力は同じだが、`軽８％` (全角％) のキーワードも `軽8%` (半角％) と同等扱いする点が異なる。経理が現金出納帳で雑費・福利厚生費の税区分を入力する際、ATOK の入力モード切替ミスで全角％が混入する事象が継続発生しており、この 2 つの勘定科目に限り全角％を吸収する運用となっている。仕入 (#9) は SMILE 取込済みで税区分が確定するため対象外、その他費目は半角％のみ受け付ける。

### `m_mf_client_mapping`（経理Excel表記→MF得意先名）
| カラム | 型 | 説明 |
|---|---|---|
| id | SERIAL PK | |
| alias | VARCHAR(200) UNIQUE | Excel表記 |
| mf_client_name | VARCHAR(200) | MF正式名 |
| del_flg / 監査 | 共通 | |

マッチは部分一致（Python踏襲）。`m_partner` とは完全分離。

## シード
既存 `client_mapping.json` から70件、Python全文精査で抽出した**18ルール**をseed SQL化。

### ルール一覧（全18件）

> 注: 表 #13 (`運  賃` の半角空白入り表記) は description_c 正規化 (全空白除去) によって #12 (`運賃`) に集約されるため、シード化不要。表は語彙整理用に保持するが SQL シードからは除外している。

| # | desc_c | d_keyword | amount | 借方 | 借方補助 | 借方部門 | 借方税 | 貸方 | 貸方補助(tmpl) | 貸方部門 | 貸方税 | summary | req_map |
|--|--|--|--|--|--|--|--|--|--|--|--|--|--|
| 1 | ｺﾞﾐ袋未収金 | - | INCOME | 現金 | - | - | OUTSIDE | 未収入金 | `ゴミ袋／{client}` | - | OUTSIDE | `{d}` | ✓ |
| 2 | ゴミ袋未収金 | - | INCOME | 現金 | - | - | OUTSIDE | 未収入金 | `ゴミ袋／{client}` | - | OUTSIDE | `{d}` | ✓ |
| 3 | 売掛金 | ㊤ | INCOME | 現金 | - | - | OUTSIDE | 売掛金 | その他 | - | OUTSIDE | `{d}` | - |
| 4 | 売掛金 | - | INCOME | 現金 | - | - | OUTSIDE | 売掛金 | `{client}` | - | OUTSIDE | `{d}` | ✓ |
| 5 | 売上 | - | INCOME | 現金 | - | - | OUTSIDE | 売上高 | 物販売上高 | 物販事業部 | SALES_AUTO | `{d}` | - |
| 6 | 雑収入 | 五光産業 | INCOME | 現金 | - | - | OUTSIDE | 雑収入 | 段ボール処分 | - | SALES_10 | `{d}` | - |
| 7 | 雑収入 | - | INCOME | 現金 | - | - | OUTSIDE | 雑収入 | - | - | SALES_10 | `{d}` | - |
| 8 | 普通預金 | - | PAYMENT | 現金 | 小口現金 | - | OUTSIDE | 現金 | - | - | OUTSIDE | `現金→普通預金入金　預金袋` | - |
| 9 | 仕入 | - | PAYMENT | 仕入高 | - | 物販事業部 | PURCHASE_AUTO | 現金 | - | - | OUTSIDE | `{d}` | - |
| 10 | 旅費交通費 | - | PAYMENT | 旅費交通費 | - | 物販事業部 | PURCHASE_10_TRAVEL | 現金 | - | - | OUTSIDE | `{d}` | - |
| 11 | 通信費 | - | PAYMENT | 通信費 | - | 物販事業部 | PURCHASE_10 | 現金 | - | - | OUTSIDE | `{d}` | - |
| 12 | 運賃 | - | PAYMENT | 荷造運賃 | - | 物販事業部 | PURCHASE_10 | 現金 | - | - | OUTSIDE | `{d}` | - |
| 13 | 運賃(全半混在"運  賃") | - | PAYMENT | 荷造運賃 | - | 物販事業部 | PURCHASE_10 | 現金 | - | - | OUTSIDE | `{d}` | - |
| 14 | 雑費 | 大竹市ﾘｻｲｸﾙｾﾝﾀｰ | PAYMENT | 雑費 | - | 物販事業部 | OUTSIDE_PURCHASE_FULL | 現金 | - | - | OUTSIDE | `{d}` | - |
| 15 | 雑費 | - | PAYMENT | 消耗品費 | - | 物販事業部 | PURCHASE_AUTO_WIDE | 現金 | - | - | OUTSIDE | `{d}` | - |
| 16 | 租税公課 | 法務局 | PAYMENT | 租税公課 | 印紙税 | 物販事業部 | OUTSIDE | 現金 | - | - | OUTSIDE | `{d} 印紙税` | - |
| 17 | 租税公課 | 宮島松大汽船 | PAYMENT | 租税公課 | - | 物販事業部 | OUTSIDE_PURCHASE_SHORT | 現金 | - | - | OUTSIDE | `入島税 {d}` | - |
| 18 | 租税公課 | - | PAYMENT | 租税公課 | - | - | OUTSIDE | 現金 | - | - | OUTSIDE | `{d}` | - |
| 19 | 福利厚生費 | - | PAYMENT | 福利厚生費 | - | 物販事業部 | PURCHASE_AUTO_WIDE | 現金 | - | - | OUTSIDE | `{d}` | - |

正規化: description_c は全空白除去後マッチ。`運  賃` は全空白除去で `運賃` と一致するため # 13 は # 12 と重複しシード化不要。半角空白入り表記の揺れは normalize (全角半角空白統一) で吸収する（**シード件数 18 件、ユニーク desc_c 10 種**）。

## バックエンド

### パッケージ構造
```
api/finance/
  CashBookController          ← /api/v1/finance/cashbook/*
  MfJournalRuleController     ← /api/v1/finance/mf-journal-rules/*
  MfClientMappingController   ← /api/v1/finance/mf-client-mappings/*
domain/model/finance/
  MMfJournalRule.java
  MMfClientMapping.java
domain/repository/finance/
  MMfJournalRuleRepository.java
  MMfClientMappingRepository.java
domain/service/finance/
  CashBookConvertService.java
  MMfJournalRuleService.java
  MMfClientMappingService.java
dto/finance/cashbook/
  CashBookPreviewResponse.java   (uploadId, rows[], errors[])
  CashBookPreviewRow.java
  CashBookConvertError.java
  MfJournalRuleRequest/Response.java
  MfClientMappingRequest/Response.java
```

### REST API
| Method | Path | 説明 |
|---|---|---|
| POST | `/api/v1/finance/cashbook/preview` | multipart Excel → JSON (uploadId, rows, errors) |
| POST | `/api/v1/finance/cashbook/convert/{uploadId}` | 全行OK時、CSV bytes返却 |
| GET/POST/PUT/DELETE | `/api/v1/finance/mf-client-mappings` | CRUD |
| GET/POST/PUT/DELETE | `/api/v1/finance/mf-journal-rules` | CRUD（admin想定） |

### uploadIdキャッシュ
- 実装: `ConcurrentHashMap<String, CachedUpload>` + ScheduledExecutorで30分TTL、プロセスローカル
- **保持内容**: パース済み**中間データ**（年/月/日/description_c_normalized/description_d/income/payment の行配列）のみ。ルール適用結果は保持しない
- `POST /preview` : 中間データ構築 → ルール＋マッピング適用 → `CashBookPreviewResponse` 返却
- `POST /convert/{uploadId}` : キャッシュから中間データ取得 → **ルール＋マッピング再適用** → エラー0件なら CSV bytes返却、エラー残存時は 422 + JSON
- これにより「マッピング追加 → 再プレビュー/再convert」で常に最新DB状態が反映される（フロントは同一uploadIdで再プレビュー可能）
- 単一インスタンス運用前提（将来水平展開する場合は `w_mf_cashbook_upload` 永続化テーブルへ移行可能）

### Excel解析ロジック（Python完全踏襲）
1. シート選択: `記入` 完全一致 → 部分一致 → `現金出納帳` 部分一致（除外: `MF` を含むシート名）
2. A1セル=年（数値／文字列「2000以上の数値が現れたら年更新」）
3. 3行目から:
   - 月/日の前行補完
   - 摘要D列 `〃` → 前行D列に置換
   - D列=`セブンイレブン` なら次行D列を結合（`セブンイレブン {next}`）
   - 年跨ぎ検出（月 < 前月 → 年++）
4. description_c を正規化（全空白除去）
5. `m_mf_journal_rule` を (description_c, priority ASC) で取得 → D列keyword部分一致で最初にヒットしたルール適用
6. `requires_client_mapping=true` かつ `credit_sub_account_template` に `{client}` 含む場合:
   - D列に含まれるaliasの最初のヒットで `mf_client_name` に置換
   - 未ヒットなら `CashBookConvertError(row=..., type=UNMAPPED_CLIENT, value=description_d)`
7. description_c が未定義なら `UNKNOWN_DESCRIPTION_C` エラー
8. `金額=0` の行はスキップ
9. C/D列両方空の行はスキップ

### CSV出力
- 19列固定ヘッダー、UTF-8 BOM付き、**CRLF 改行**
- `pandas.to_csv` 準拠（数値はstr化、空値は空文字、カンマクォート最小限）
- 改行コードは Python 版が Windows 環境で生成していた CRLF と揃える（§非機能 参照）

## フロントエンド

### ページ構成
| Path | 役割 |
|---|---|
| `/finance/cashbook-import` | 3ステップ: アップロード→プレビュー＋補正→CSVダウンロード |
| `/finance/mf-client-mappings` | 得意先マッピングCRUD |
| `/finance/mf-journal-rules` | 仕訳ルールCRUD |

### サイドバー
「見積・財務」グループに追加:
- 現金出納帳取込 (`/finance/cashbook-import`)
- MF得意先マッピング (`/finance/mf-client-mappings`)
- MF仕訳ルール (`/finance/mf-journal-rules`)

### cashbook-import 画面フロー
1. ファイル選択→「プレビュー」ボタン→`POST /preview`
2. テーブル表示（19列、エラー行は赤背景）
3. 未マッピング得意先パネル: alias→正式名入力→「登録＆再プレビュー」
4. エラー0ならば「CSVダウンロード」ボタン活性 → `POST /convert/{uploadId}`

## テスト

### JUnit（バックエンド）
- `CashBookConvertServiceTest` — ゴールデンCSV回帰テスト（12ファイル、バイト一致）
- `CashBookConvertServiceRuleTest` — 各ルールの単体テスト
- `MfJournalRuleServiceTest` / `MfClientMappingServiceTest` — CRUD

### Playwright E2E
- `e2e/cashbook-import.spec.ts` — 全フロー（アップロード→エラー→マッピング追加→CSVダウンロード）

## セキュリティ
- `cashbook/preview` / `cashbook/convert` : `@PreAuthorize("isAuthenticated()")`
- `mf-client-mappings` / `mf-journal-rules` の**書き込み系**（POST/PUT/DELETE）: `@PreAuthorize("hasAuthority('ADMIN') or authentication.principal.shopNo == 0")` — 会計データ改変直結のためadmin限定
- 読み取り系GETは認証済みユーザ可（プレビュー画面で得意先名参照のため）
- ファイル検証:
  - 拡張子 `.xlsx` のみ、ContentType `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` のみ
  - `ZipSecureFile.setMinInflateRatio(0.01)` / `setMaxEntrySize(100MB)` をPOI読込前に設定（Zip Bomb対策）
  - シート数上限10、データ行数上限10000（超過時IllegalArgumentException）
  - xls/xlsm 拒否
- パストラバーサル対策不要（ファイル保存しないため）
