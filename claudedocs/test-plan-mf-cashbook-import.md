# テスト計画: 現金出納帳 → MoneyForward仕訳帳CSV変換

作成日: 2026-04-13
対象設計書: `claudedocs/design-mf-cashbook-import.md`
移植元: `C:\project\mf_import\mf_import\src\CashBookToMoneyForwardConverter.py`
ゴールデンCSV: `C:\project\mf_import\mf_import\output\cashbook\*.csv` (12ファイル)

## 1. テスト戦略

### 1.1 テストレベル
| レベル | ツール | 目的 |
|---|---|---|
| 単体 | JUnit 5 + Mockito | ルール評価・税区分リゾルバ・前処理ロジックの網羅 |
| 結合（ゴールデン） | JUnit 5 + @ParameterizedTest | 移植元Pythonツールとの意味等価性検証 |
| 統合 | @SpringBootTest + MockMvc | REST API・権限・キャッシュ挙動 |
| E2E | Playwright | 画面フロー・導線・ブラウザ表示 |

### 1.2 優先度定義
- **P0**: リリースブロッカー。仕訳金額・勘定科目・税区分の正当性に直結
- **P1**: 主要機能の異常系・権限制御
- **P2**: エッジケース・UI表示細部

### 1.3 カバレッジ目標
- ルール網羅: 17ルール全件 100%
- 税区分リゾルバ: 8種全件 100%
- サービス層ライン: 80%以上
- ゴールデンCSV: 12ファイル全件 意味等価一致

---

## 2. JUnit 単体テスト

### 2.1 CashBookConvertServiceTest

対象: `jp.co.oda32.domain.service.finance.CashBookConvertService`
参考: `backend/src/test/java/jp/co/oda32/domain/service/finance/InvoiceImportServiceTest.java`

#### 2.1.1 ルール評価テスト（@ParameterizedTest）

| TC ID | ルール# | 入力(desc_c / desc_d / income / payment) | 期待(借方科目 / 貸方科目 / 貸方補助 / 貸方部門 / 貸方税区分 / 借方税区分 / 摘要) | 優先度 |
|---|---|---|---|---|
| TC-UT-001 | 1 | `ｺﾞﾐ袋未収金` / `◯◯商店` / 1000 / 0 | 現金 / 未収入金 / `ゴミ袋／株式会社◯◯` (マッピング済) / - / 対象外 / 対象外 / `◯◯商店` | P0 |
| TC-UT-002 | 2 | `ゴミ袋未収金` / `◯◯商店` / 2000 / 0 | 現金 / 未収入金 / `ゴミ袋／株式会社◯◯` / - / 対象外 / 対象外 / `◯◯商店` | P0 |
| TC-UT-003 | 3 | `売掛金` / `㊤付き内容` / 500 / 0 | 現金 / 売掛金 / その他 / - / 対象外 / 対象外 / `㊤付き内容` | P0 |
| TC-UT-004 | 4 | `売掛金` / `◯◯商店 取引` / 700 / 0 | 現金 / 売掛金 / `株式会社◯◯` / - / 対象外 / 対象外 / `◯◯商店 取引` | P0 |
| TC-UT-005 | 5 | `売上` / `通常販売` / 3000 / 0 | 現金 / 売上高 / 物販売上高 / 物販事業部 / 課売 10% / 対象外 / `通常販売` | P0 |
| TC-UT-006 | 5軽減 | `売上` / `軽8% 弁当` / 1080 / 0 | 貸方税区分=`課売 (軽)8%` | P0 |
| TC-UT-007 | 6 | `雑収入` / `五光産業 段ボール` / 500 / 0 | 貸方=雑収入 / 段ボール処分 / - / 課税売上 10% | P0 |
| TC-UT-008 | 7 | `雑収入` / `その他収入` / 200 / 0 | 貸方=雑収入 / - / - / 課税売上 10% | P0 |
| TC-UT-009 | 8 | `普通預金` / - / 0 / 5000 | 借方=現金/小口現金, 貸方=現金, 摘要=`現金→普通預金入金　預金袋` | P0 |
| TC-UT-010 | 9 | `仕入` / `通常仕入` / 0 / 4000 | 借方=仕入高 / 物販事業部 / 課税仕入 10% | P0 |
| TC-UT-011 | 9軽減 | `仕入` / `軽8% 食材` / 0 / 4000 | 借方税区分=`課税仕入 (軽)8%` | P0 |
| TC-UT-012 | 10 | `旅費交通費` / `電車` / 0 / 500 | 借方=旅費交通費 / 物販事業部 / `課仕 10%` | P0 |
| TC-UT-013 | 11 | `通信費` / `電話代` / 0 / 3000 | 借方=通信費 / 物販事業部 / `課税仕入 10%` | P0 |
| TC-UT-014 | 12 | `運賃` / `ヤマト` / 0 / 1500 | 借方=荷造運賃 / 物販事業部 / `課税仕入 10%` | P0 |
| TC-UT-015 | 13 | `運  賃` (全角+半角空白混在) / `佐川` / 0 / 800 | 正規化で `運賃` 一致 → ルール12適用 | P0 |
| TC-UT-016 | 14 | `雑費` / `大竹市ﾘｻｲｸﾙｾﾝﾀｰ 処分費` / 0 / 2000 | 借方=雑費 / 物販事業部 / `対象外仕入` | P0 |
| TC-UT-017 | 15 | `雑費` / `文房具` / 0 / 1200 | 借方=消耗品費 / 物販事業部 / `課税仕入 10%` | P0 |
| TC-UT-018 | 15軽減 | `雑費` / `軽8% お茶` / 0 / 500 | 借方税区分=`課税仕入 (軽)8%` | P1 |
| TC-UT-019 | 16 | `租税公課` / `法務局 登記` / 0 / 600 | 借方=租税公課 / 印紙税 / 物販事業部 / 対象外 / 摘要=`法務局 登記 印紙税` | P0 |
| TC-UT-020 | 17 | `租税公課` / `宮島松大汽船 入島` / 0 / 100 | 借方=租税公課 / 物販事業部 / `対象外仕` / 摘要=`入島税 宮島松大汽船 入島` | P0 |
| TC-UT-021 | 18 | `租税公課` / `その他税金` / 0 / 500 | 借方=租税公課 / - / - / 対象外 | P0 |
| TC-UT-022 | 19 | `福利厚生費` / `お茶代` / 0 / 3000 | 借方=福利厚生費 / 物販事業部 / `課税仕入 10%` | P0 |

**前提**: `m_mf_journal_rule` に17ルールseed済み、`m_mf_client_mapping` に `◯◯商店→株式会社◯◯`, `五光産業→五光産業株式会社`, `法務局→広島法務局`, `宮島松大汽船→宮島松大汽船株式会社`, `大竹市ﾘｻｲｸﾙｾﾝﾀｰ→大竹市ﾘｻｲｸﾙｾﾝﾀｰ` 登録済み。

**手順**:
1. `@SpringBootTest` or `@DataJpaTest` + モックリポジトリで `CashBookConvertService#convert(List<ParsedRow>)` を呼び出し
2. 1行だけ含むリストを渡し、返却JournalRowを検証

**期待結果**: 全カラム一致。`assertEqualsAll` で借方科目/補助/部門/税区分、貸方科目/補助/部門/税区分、摘要、金額を検証。

#### 2.1.2 税区分リゾルバ単体テスト

| TC ID | リゾルバ | 入力D列 | 期待出力 | 優先度 |
|---|---|---|---|---|
| TC-UT-030 | OUTSIDE | 任意 | `対象外` | P0 |
| TC-UT-031 | OUTSIDE_PURCHASE_FULL | 任意 | `対象外仕入` | P0 |
| TC-UT-032 | OUTSIDE_PURCHASE_SHORT | 任意 | `対象外仕` | P0 |
| TC-UT-033 | SALES_10 | 任意 | `課税売上 10%` | P0 |
| TC-UT-034 | PURCHASE_10 | 任意 | `課税仕入 10%` | P0 |
| TC-UT-035 | PURCHASE_10_TRAVEL | 任意 | `課仕 10%` | P0 |
| TC-UT-036 | SALES_AUTO | `軽8%` 含む | `課売 (軽)8%` | P0 |
| TC-UT-037 | SALES_AUTO | 通常 | `課売 10%` | P0 |
| TC-UT-038 | PURCHASE_AUTO | `軽8%` 含む | `課税仕入 (軽)8%` | P0 |
| TC-UT-039 | PURCHASE_AUTO | `軽８％` (全角) 含む | `課税仕入 (軽)8%` | P0 |
| TC-UT-040 | PURCHASE_AUTO | 通常 | `課税仕入 10%` | P0 |

**手順**: `TaxResolver.resolve(String descD)` を全コードで呼び出し、戻り値検証。

#### 2.1.3 前処理テスト

| TC ID | 内容 | 入力 | 期待 | 優先度 |
|---|---|---|---|---|
| TC-UT-050 | 〃置換 | D列=`〃`、前行D=`ヤマト運輸` | `ヤマト運輸` に置換 | P0 |
| TC-UT-051 | 〃連続 | 3行連続で `〃` | 全行が最初の非〃値に置換 | P1 |
| TC-UT-052 | セブンイレブン次行結合 | D列=`セブンイレブン`、次行D=`コピー代` | 当行D=`セブンイレブン コピー代`、次行はスキップ | P0 |
| TC-UT-053 | セブンイレブン末尾 | 最終行がセブンイレブン単独 | 次行なし→例外なしで `セブンイレブン` のまま | P1 |
| TC-UT-054 | 年跨ぎ 12月→1月 | A1=2025、行1=12/31、行2=1/1 | 行2の取引日=2026/1/1 | P0 |
| TC-UT-055 | 年跨ぎ 月戻り | 11月→10月 | 年++ (同ルール適用) | P1 |
| TC-UT-056 | A列年更新 | A列=2026 (>=2000) | 以降の行に年=2026反映 | P0 |
| TC-UT-057 | 前行補完 月 | 行2 月=空、行1 月=4 | 行2の月=4 | P0 |
| TC-UT-058 | 前行補完 日 | 行2 日=空、行1 日=15 | 行2の日=15 | P0 |
| TC-UT-059 | 前行補完 C列 | 行2 C列=空、行1 C列=`売上` | 行2 C列=`売上` | P0 |
| TC-UT-060 | 前行補完 D列 | 行2 D列=空、行1 D列=`通常販売` | 行2 D列=`通常販売` | P0 |
| TC-UT-061 | description_c正規化 | `売 掛 金` (半角空白) | `売掛金` に正規化 | P0 |
| TC-UT-062 | description_c正規化 | `売　掛　金` (全角空白) | `売掛金` に正規化 | P0 |

**手順**: `CashBookExcelParser#preprocess(Sheet)` → 中間データリストを検証。

#### 2.1.4 エラー検出テスト

| TC ID | 内容 | 入力 | 期待エラー | 優先度 |
|---|---|---|---|---|
| TC-UT-070 | 未マッピング得意先 | ルール1、D列=`未登録商店` | `UNMAPPED_CLIENT`, value=`未登録商店` | P0 |
| TC-UT-071 | 未定義description_c | C列=`存在しない科目` | `UNKNOWN_DESCRIPTION_C` | P0 |
| TC-UT-072 | 金額0スキップ | income=0, payment=0 | 行が出力に含まれない | P0 |
| TC-UT-073 | C/D列両方空スキップ | C=空, D=空 (金額あり) | 行スキップ | P0 |
| TC-UT-074 | C列のみ空 + 金額 | C=空, D=`内容`, income=100 | スキップしない→`UNKNOWN_DESCRIPTION_C` or 前行補完後評価 | P1 |
| TC-UT-075 | priority順評価 | ルール6(雑収入+五光産業) と ルール7(雑収入) | 五光産業含む行→ルール6、他→ルール7 | P0 |

#### 2.1.5 正常系境界値

| TC ID | 内容 | 期待 | 優先度 |
|---|---|---|---|
| TC-UT-080 | 空シート | 0行返却、エラーなし | P1 |
| TC-UT-081 | 全行エラー | errors=全件、rows=空 | P1 |
| TC-UT-082 | 最大行数10000 | 正常処理 | P1 |
| TC-UT-083 | 10001行超過 | IllegalArgumentException | P1 |

---

### 2.2 CashBookGoldenMasterTest

対象: 移植元Pythonツール出力(12ファイル)との意味等価比較

**場所**: `backend/src/test/java/jp/co/oda32/domain/service/finance/CashBookGoldenMasterTest.java`
**リソース配置**: `backend/src/test/resources/cashbook/golden/*.csv`, `backend/src/test/resources/cashbook/input/*.xlsx`

#### 2.2.1 ゴールデンCSV回帰テスト (@ParameterizedTest + @CsvSource)

| TC ID | 入力Excel | 期待CSV | 優先度 |
|---|---|---|---|
| TC-GM-001 | `現金出納帳2-3.xlsx` | `現金出納帳2-3.csv` | P0 |
| TC-GM-002 | `現金出納帳3-4.xlsx` | `現金出納帳3-4.csv` | P0 |
| TC-GM-003 | `現金出納帳4-5.xlsx` | `現金出納帳4-5.csv` | P0 |
| TC-GM-004 | `現金出納帳5-6.xlsx` | `現金出納帳5-6.csv` | P0 |
| TC-GM-005 | `現金出納帳6-7.xlsx` | `現金出納帳6-7.csv` | P0 |
| TC-GM-006 | `現金出納帳7-8.xlsx` | `現金出納帳7-8.csv` | P0 |
| TC-GM-007 | `現金出納帳8-9.xlsx` | `現金出納帳8-9.csv` | P0 |
| TC-GM-008 | `現金出納帳9-10.xlsx` | `現金出納帳9-10.csv` | P0 |
| TC-GM-009 | `現金出納帳10-11.xlsx` | `現金出納帳10-11.csv` | P0 |
| TC-GM-010 | `現金出納帳11-12.xlsx` | `現金出納帳11-12.csv` | P0 |
| TC-GM-011 | `2025-26現金出納帳12-1_改造.xlsx` | `2025-26現金出納帳12-1_改造.csv` | P0 (年跨ぎ検証) |
| TC-GM-012 | `2026現金出納帳1-2_改造.xlsx` | `2026現金出納帳1-2_改造.csv` | P0 |

**前提**:
- seedされた `m_mf_journal_rule` (17件)、`m_mf_client_mapping` (Python `client_mapping.json` 由来70件)
- Python tool実行時の `client_mapping.json` を `@Sql` で再現

**手順**:
1. Excel ファイルを `CashBookConvertService#previewAndConvert` に渡す
2. 出力 `List<JournalRow>` をCSV化 (UTF-8 BOM, LF, 19列)
3. ゴールデンCSVを読み込みpandas出力差異(数値小数点、空欄表現)を吸収するnormalizer経由で行単位比較

**期待結果**:
- 行数一致
- 各行の `取引日, 借方勘定科目, 借方補助科目, 借方部門, 借方税区分, 借方金額, 貸方勘定科目, 貸方補助科目, 貸方部門, 貸方税区分, 貸方金額, 摘要` が完全一致
- 差分発生時はdiff最初の5行をassertメッセージに含める

**比較ロジック**:
```java
void assertSemanticEquals(List<String[]> actual, List<String[]> expected) {
    assertEquals(expected.size(), actual.size(), "row count");
    for (int i = 0; i < expected.size(); i++) {
        assertArrayEquals(normalize(expected.get(i)), normalize(actual.get(i)),
            "row " + (i+1) + " mismatch");
    }
}
// normalize: 金額を long 化、空文字を "" に統一、末尾空白trim
```

---

### 2.3 MfJournalRuleServiceTest

対象: `MMfJournalRuleService`

| TC ID | 操作 | 前提 | 手順 | 期待 | 優先度 |
|---|---|---|---|---|---|
| TC-RL-001 | findAll | 17件seed | `findAll(Pageable)` | priority ASC、del_flg='0' のみ | P1 |
| TC-RL-002 | findById | id=1存在 | `findById(1)` | 全フィールド取得 | P1 |
| TC-RL-003 | findById NotFound | id=999 | `findById(999)` | ResourceNotFoundException | P1 |
| TC-RL-004 | create | validリクエスト | `create(req)` | 監査フィールド設定、del_flg='0' | P1 |
| TC-RL-005 | create 重複priority | 既存priority=10 | priority=10で作成 | 許容 (priorityは同値可) | P2 |
| TC-RL-006 | update | 既存ID | debit_account変更 | modify_date_time更新 | P1 |
| TC-RL-007 | update NotFound | id=999 | - | ResourceNotFoundException | P2 |
| TC-RL-008 | delete (論理) | 既存ID | `delete(id)` | del_flg='1' 設定 | P1 |
| TC-RL-009 | findActiveRules | 一部del_flg='1' | - | 有効ルールのみ | P1 |

### 2.4 MfClientMappingServiceTest

対象: `MMfClientMappingService`

| TC ID | 操作 | 手順 | 期待 | 優先度 |
|---|---|---|---|---|
| TC-CM-001 | findAll | - | del_flg='0'のみ、alias ASC | P1 |
| TC-CM-002 | create | alias=`◯◯商店`, name=`株式会社◯◯` | 監査フィールド設定 | P1 |
| TC-CM-003 | create 重複alias | 既存alias | DataIntegrityViolationException (UNIQUE制約) | P1 |
| TC-CM-004 | update | 既存ID | 更新反映 | P1 |
| TC-CM-005 | delete | 既存ID | del_flg='1' | P1 |
| TC-CM-006 | findByAliasContaining | D列に`◯◯商店 購入`含む | 部分一致でヒット | P0 |
| TC-CM-007 | findByAliasContaining 未登録 | D列に登録alias含まず | Optional.empty | P0 |

---

### 2.5 セキュリティ・ファイル検証テスト

**場所**: `CashBookControllerSecurityTest` (@SpringBootTest + MockMvc)

| TC ID | 内容 | 手順 | 期待 | 優先度 |
|---|---|---|---|---|
| TC-SC-001 | xls拒否 | `.xls` ファイルで `/preview` POST | 400 Bad Request, `拡張子.xlsxのみ` | P0 |
| TC-SC-002 | xlsm拒否 | `.xlsm` ファイルで POST | 400 Bad Request | P0 |
| TC-SC-003 | 空ファイル | 0バイトファイル | 400 | P1 |
| TC-SC-004 | ContentType不正 | text/plain | 400 | P1 |
| TC-SC-005 | ZipBomb模擬 (サイズ超過) | 100MB超展開ファイル | POIException or IllegalArgumentException | P0 |
| TC-SC-006 | シート数超過 | 11シート | IllegalArgumentException | P1 |
| TC-SC-007 | データ行10001超過 | 10001行 | IllegalArgumentException | P1 |
| TC-SC-008 | 未認証アクセス | 認証なしで `/preview` | 401 Unauthorized | P0 |
| TC-SC-009 | 一般ユーザが mf-journal-rules POST | 一般権限で作成 | 403 Forbidden | P0 |
| TC-SC-010 | admin (shopNo=0) が mf-journal-rules POST | admin権限 | 201 Created | P0 |
| TC-SC-011 | 一般ユーザが mf-client-mappings POST | 一般権限 | 403 | P0 |
| TC-SC-012 | admin が mf-client-mappings POST | admin | 201 | P0 |
| TC-SC-013 | 読み取りGET | 一般認証ユーザ | 200 (参照可) | P1 |

---

### 2.6 キャッシュ挙動テスト

**場所**: `CashBookConvertCacheTest`

| TC ID | 内容 | 手順 | 期待 | 優先度 |
|---|---|---|---|---|
| TC-CH-001 | uploadId発行 | preview POST | UUID文字列返却 | P1 |
| TC-CH-002 | convert再適用 | preview後マッピング追加→convert | 追加済みマッピング反映 | P0 |
| TC-CH-003 | TTL超過 | 31分経過後convert | 404 Not Found | P2 |
| TC-CH-004 | エラー残存時convert | UNMAPPED_CLIENT残存 | 422 Unprocessable Entity | P0 |
| TC-CH-005 | 同一uploadId並列リクエスト | 2スレッド同時convert | 両方成功、データ破損なし | P2 |

---

## 3. Playwright E2E テスト

### 3.1 e2e/cashbook-import.spec.ts

前提: `e2e/helpers/mock-api.ts` に `/api/v1/finance/cashbook/*` モック追加、`e2e/helpers/auth.ts` 利用。

| TC ID | シナリオ | 手順 | 期待 | 優先度 |
|---|---|---|---|---|
| TC-E2E-001 | 全フロー正常 | ①ログイン→`/finance/cashbook-import`<br>②xlsxアップロード<br>③「プレビュー」クリック<br>④エラー行(UNMAPPED_CLIENT) 3件表示<br>⑤「alias→正式名」入力→「登録＆再プレビュー」<br>⑥エラー0件、CSVダウンロードボタン活性<br>⑦「CSVダウンロード」クリック | DL成功、ファイル名 `cashbook-YYYYMMDD-HHmmss.csv`、UTF-8 BOM、19列 | P0 |
| TC-E2E-002 | 不正ファイル拒否 | `.xls` ファイル選択→「プレビュー」 | トーストエラー `拡張子.xlsxのみ対応` | P0 |
| TC-E2E-003 | サイドバー導線 | 見積・財務グループ展開 | `現金出納帳取込 / MF得意先マッピング / MF仕訳ルール` の3項目表示、各リンクで正しいパス遷移、active状態正しい | P0 |
| TC-E2E-004 | プレビュー後ファイル差替 | プレビュー後別ファイル選択→再プレビュー | 新uploadIdで再取得、前エラーリセット | P1 |
| TC-E2E-005 | エラー行赤背景 | UNMAPPED_CLIENT含むプレビュー | 該当行に `bg-red-50` クラス付与 | P2 |
| TC-E2E-006 | 未定義description_c表示 | UNKNOWN_DESCRIPTION_C エラー | エラーパネルにC列値とrow番号表示 | P1 |
| TC-E2E-007 | CSVダウンロード前バリデーション | エラー残存時にDLボタン押下不可 | ボタン disabled、tooltip表示 | P1 |
| TC-E2E-008 | ログアウト→保護 | 未認証で `/finance/cashbook-import` 直接アクセス | ログインページへリダイレクト | P1 |

### 3.2 e2e/mf-client-mappings.spec.ts

| TC ID | 操作 | 手順 | 期待 | 優先度 |
|---|---|---|---|---|
| TC-E2E-101 | 一覧表示 | `/finance/mf-client-mappings` アクセス | alias/mf_client_name列のテーブル表示、70件seed | P1 |
| TC-E2E-102 | 検索 | alias検索ボックスに `◯◯` 入力 | フィルタ結果即時反映 | P1 |
| TC-E2E-103 | 新規作成 | 「新規追加」→Dialog→alias+name入力→保存 | トースト成功、テーブルに追加 | P0 |
| TC-E2E-104 | alias重複エラー | 既存aliasで新規作成 | エラートースト `aliasが既に存在` | P1 |
| TC-E2E-105 | 編集 | 行の「編集」→name変更→保存 | 更新反映 | P1 |
| TC-E2E-106 | 削除 | 「削除」→確認Dialog→OK | 論理削除、一覧から消失 | P1 |
| TC-E2E-107 | 一般ユーザ書込み不可 | 一般ユーザでアクセス | 「新規追加/編集/削除」ボタン非表示 or disabled | P0 |

### 3.3 e2e/mf-journal-rules.spec.ts

| TC ID | 操作 | 手順 | 期待 | 優先度 |
|---|---|---|---|---|
| TC-E2E-201 | 一覧表示 (admin) | admin ログイン→`/finance/mf-journal-rules` | 17件seedルール表示、priority ASC | P1 |
| TC-E2E-202 | 一般ユーザアクセス | 一般ユーザで同URLアクセス | 403画面 or リダイレクト | P0 |
| TC-E2E-203 | 一般ユーザ サイドバー非表示 | 一般ユーザでサイドバー確認 | `MF仕訳ルール` メニュー非表示 | P0 |
| TC-E2E-204 | 新規作成 (admin) | 全フィールド入力→保存 | 作成成功、一覧追加 | P1 |
| TC-E2E-205 | 必須バリデーション | description_c 空で保存 | フォームエラー | P1 |
| TC-E2E-206 | 税区分リゾルバ選択 | Select開く | 8種コード全件表示 | P1 |
| TC-E2E-207 | 編集 | priority変更→保存 | 一覧順序更新 | P1 |
| TC-E2E-208 | 削除 | 削除→確認→OK | 論理削除 | P1 |
| TC-E2E-209 | requires_client_mappingスイッチ | ON/OFF切替→保存 | 値永続化 | P2 |

---

## 4. テストデータ準備

### 4.1 seed SQL
- `backend/src/test/resources/sql/mf-journal-rule-seed.sql` : 17ルール
- `backend/src/test/resources/sql/mf-client-mapping-seed.sql` : Pythonの `client_mapping.json` 由来70件

### 4.2 ゴールデン資材
- `backend/src/test/resources/cashbook/input/` : 12 xlsx（`C:\project\mf_import\mf_import\input\` からコピー）
- `backend/src/test/resources/cashbook/golden/` : 12 csv（`C:\project\mf_import\mf_import\output\cashbook\` からコピー）

### 4.3 E2Eモック
- `e2e/fixtures/cashbook-sample.xlsx` : 小規模テスト用3行サンプル
- `e2e/fixtures/cashbook-sample-unmapped.xlsx` : 未マッピング含む

### 4.4 mock-api.ts追加
```ts
// /api/v1/finance/cashbook/preview
// /api/v1/finance/cashbook/convert/:uploadId
// /api/v1/finance/mf-client-mappings (GET/POST/PUT/DELETE)
// /api/v1/finance/mf-journal-rules (GET/POST/PUT/DELETE)
```

---

## 5. 実行・レポート

### 5.1 実行コマンド
```bash
# 単体・結合
cd backend && ./gradlew test --tests '*.CashBookConvertServiceTest'
cd backend && ./gradlew test --tests '*.CashBookGoldenMasterTest'
cd backend && ./gradlew test --tests '*.Mf*ServiceTest'
cd backend && ./gradlew test --tests '*.CashBookControllerSecurityTest'

# E2E
cd frontend && npx playwright test e2e/cashbook-import.spec.ts
cd frontend && npx playwright test e2e/mf-client-mappings.spec.ts
cd frontend && npx playwright test e2e/mf-journal-rules.spec.ts
```

### 5.2 合格基準
- P0: 100% pass 必須（リリースブロッカー）
- P1: 95%以上 pass
- P2: 80%以上 pass
- ゴールデンCSV: 12/12 一致
- カバレッジ: CashBookConvertService ライン 85%以上

### 5.3 リスクと緩和策
| リスク | 緩和策 |
|---|---|
| Python出力と意味等価だが文字列差分発生 (全半角空白・改行) | normalizer で NFKC正規化 + trim 適用 |
| 年跨ぎ判定のずれ | TC-GM-011/012 (12-1 ファイル) で検証必須 |
| セブンイレブン結合漏れ | TC-UT-052/053 + golden で間接検証 |
| リゾルバ定義とseed SQLの不整合 | seed SQL ロード後の assertion テストで検証 |
| pandasの小数点表記差 (1000.0 vs 1000) | 金額カラムをlong化後比較 |

---

## 6. スケジュールと担当

| タスク | 担当 | 工数目安 |
|---|---|---|
| seed SQL/テストデータ整備 | QA | 0.5d |
| 単体テスト実装 (2.1〜2.4) | BE | 2.0d |
| ゴールデンテスト実装 (2.2) | BE | 1.0d |
| セキュリティテスト (2.5) | BE | 0.5d |
| キャッシュテスト (2.6) | BE | 0.5d |
| E2E実装 (3.1〜3.3) | FE | 2.0d |
| 実行・バグ修正 | 全員 | 1.5d |
| 合計 | - | 8.0d |

---

## 付録A: ルール適用決定表

ルール評価順序 (priority ASC → description_d_keyword NOT NULL優先):

```
1. priority昇順にソート
2. description_c 正規化一致でフィルタ
3. description_d_keyword NULL でないルールを先に評価、D列部分一致ならヒット
4. NULL ルールを最後にフォールバック
5. ヒットなし → UNKNOWN_DESCRIPTION_C
```

## 付録B: CSV出力仕様 (19列)

| # | 列名 |
|---|---|
| 1 | 取引No |
| 2 | 取引日 |
| 3 | 借方勘定科目 |
| 4 | 借方補助科目 |
| 5 | 借方部門 |
| 6 | 借方取引先 |
| 7 | 借方税区分 |
| 8 | 借方インボイス |
| 9 | 借方金額 |
| 10 | 借方税額 |
| 11 | 貸方勘定科目 |
| 12 | 貸方補助科目 |
| 13 | 貸方部門 |
| 14 | 貸方取引先 |
| 15 | 貸方税区分 |
| 16 | 貸方インボイス |
| 17 | 貸方金額 |
| 18 | 貸方税額 |
| 19 | 摘要 |

- UTF-8 BOM付き、LF改行、QUOTE_MINIMAL相当、空値=空文字、数値=整数str
