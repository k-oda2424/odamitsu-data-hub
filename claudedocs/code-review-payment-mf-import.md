# コードレビュー: 買掛仕入 MF 変換 (Cluster C)

レビュー日: 2026-05-04
対象ブランチ: `refactor/code-review-fixes`
レビュアー: Opus サブエージェント
対象実装:
- Backend: `PaymentMfImportController` / `PaymentMfImportService` / `PaymentMfRuleService` / `PaymentMfExcelParser` / `PaymentMfCellReader` / `PaymentMfCsvWriter` / Entity 3 種 / Repository 3 種 / DTO 7 種
- Frontend: `payment-mf-import.tsx` / `payment-mf-rules.tsx` / `payment-mf-history.tsx` / `VerifiedCsvExportDialog.tsx` / `PaymentMfAuxRowsTable.tsx` / `types/payment-mf.ts`
- DB: `V011__create_payment_mf_tables.sql` / `V016__create_payment_mf_aux_row.sql`

前提: 設計レビュー (`claudedocs/design-review-payment-mf-import.md`, Critical 3 / Major 6 / Minor 7) で指摘済みの課題は再掲しない。コード固有の問題に絞る。

## サマリー

- 総指摘件数: **Blocker 0 / Critical 2 / Major 5 / Minor 7**
- 承認状態: **Needs Revision**

新発見トップ:
1. **C-CODE-1**: `applyVerification` で書込側の `verified_amount` (税込) と整合性チェック側の `payable` 集計が「振込明細の `invoice` 全額を税率行ごとに重複書込」する構造になっており、税率行が 2 行ある仕入先で `verified_amount` 合計 = 元の 2 倍になる（設計レビュー C-3 と関連するが、書込ロジック側の重複も伴う点は未指摘）。
2. **C-CODE-2**: `PaymentMfCsvWriter#fmtAmount` が金額末尾に常に半角スペースを付与する仕様だが、空 (`null`) のときは空文字列を返すため、行内で「金額カラムだけ末尾スペースなし」の差分が CSV 出力に混入し、MF 取込側の strict パーサで弾かれるリスクがある。

ファイル: `claudedocs/code-review-payment-mf-import.md`

---

## Critical 指摘

### C-CODE-1. `applyVerification` の `verified_amount` 書込が税率行ごとに重複し、合計値が二重計上される

**箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java:269-287`

```java
for (TAccountsPayableSummary s : list) {
    s.setVerificationResult(isMatched ? 1 : 0);
    s.setPaymentDifference(difference);
    s.setVerifiedManually(true);
    s.setMfExportEnabled(isMatched);
    s.setVerificationNote(adjustNote);
    s.setVerifiedAmount(invoice);                   // ← 全行に同じ invoice (税込総額) を書く
    BigDecimal taxRate = s.getTaxRate() != null ? s.getTaxRate() : BigDecimal.TEN;
    BigDecimal divisor = BigDecimal.valueOf(100).add(taxRate);
    BigDecimal invoiceTaxExcl = invoice.multiply(BigDecimal.valueOf(100))
            .divide(divisor, 0, java.math.RoundingMode.DOWN);
    s.setVerifiedAmountTaxExcluded(invoiceTaxExcl); // ← 全行に逆算同値
    s.setAutoAdjustedAmount(autoAdjusted);
    s.setMfTransferDate(cached.getTransferDate());
    payableService.save(s);
}
```

**問題**:
- `list` は同一 `(supplierCode, transactionMonth)` の税率別 N 行（10% / 8%軽 / 8%旧軽 など）。
- `invoice` は振込明細の請求額 1 件 (税込総額) で、ループ内で「全税率行に同値を書込」している。
- 後段 `exportVerifiedCsv` (L477-505) と `buildVerifiedExportPreview` (L613-639) は `sumVerifiedAmountForGroup` (L417-429) で「全行同値なら代表値、そうでなければ SUM」というヒューリスティックでこの構造を救っている（コメント L411-414 にも明記）が、これは **書込側のバグを読み取り側で吸収する** 構造で、不変条件として脆い:
  1. `verified_amount` の DB 値だけ見ると、税率 10% 行 ¥1,100 + 8% 行 ¥1,100 = ¥2,200 (二重計上された姿) で「合計」を出す他コード（外部レポート、SQL ad-hoc 集計、`/finance/accounts-payable-ledger` など）が誤った数字を出す。
  2. `verifiedAmount` を SUM したくない読者向けに `sumVerifiedAmountForGroup` を使うことを **強制する規約** がコードコメントにしか存在しない。
  3. 手動 verify は税率別に異なる値を入れる前提（コメント L411 「手動 verify では税率別に異なる値が入り得る」）なので、今後画面側で 1 supplier に複数税率行を一括 verify する UI を作ると `allSame=false` 経路に入って自動的に SUM になり、振込明細一括検証経路と判別できない (両者とも `verifiedManually=true`)。
- `verified_amount_tax_excluded` も同様に「全税率行で逆算同値」になり、税率 10% 行と 8% 行で同じ税抜額が入る（実際には 10% 行は ¥1,000 / 8% 行は ¥1,019 等で異なるべき）。`exportVerifiedCsv` 側 (L518-538) は「PAYABLE 1 行 = supplier 集約」しか出力しないため、CSV 上は税抜の按分情報が消える。

**影響**:
- **財務整合性**: `verified_amount` の DB SUM と `payment_mf` CSV 出力額が一致しない。経理が `t_accounts_payable_summary` を直接 SQL で集計したときの「検証済み総額」と、ダウンロード CSV の「合計額」(`X-Total-Amount`) が乖離する。
- **監査証跡**: `auto_adjusted_amount` も全行に同値が書かれるため、税率別の調整明細が DB から復元不能。
- 設計レビュー C-3 は「複数税率仕入先の精度問題」として既出だが、本指摘はそれに加えて **書込スキーマ自体が二重計上を内包している** 点を強調する。`sumVerifiedAmountForGroup` が無いと壊れる API が今後増えるたびに保守コストが上がる。

**修正案** (どれかを採用):
1. **代表行のみ書込**: `list.get(0)` のみ `verifiedAmount` を書き、他税率行は `verifiedAmount = null` (または 0) で残す。集計は常に「非 null 1 行のみ採用」で固定化する。`autoAdjustedAmount` / `verifiedAmountTaxExcluded` も代表行のみ。
2. **税率別按分書込**: 振込明細 Excel に税率内訳が無いため、DB 側の `taxIncludedAmountChange` または `taxIncludedAmount` の比率で `invoice` を按分し、各税率行に按分後の値を書く。これなら `verified_amount` の DB SUM = invoice で一意。`verified_amount_tax_excluded` も税率別に正しく逆算される。
3. （短期回避）コメント L258-263 の「全税率行に同値を書く」を `verified_amount_total` のような **集約値専用カラム** に切り出し、`verified_amount` は税率別に分けて書く。`sumVerifiedAmountForGroup` を捨てて `findVerifiedForMfExport` 側で sum する。

いずれにしても **書込仕様 = 読取仕様** の不変条件を JavaDoc / DB COMMENT に明示する必要あり。

### C-CODE-2. `PaymentMfCsvWriter#fmtAmount` の末尾スペース仕様が NULL 金額行で破綻する

**箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfCsvWriter.java:95-98`

```java
private static String fmtAmount(Long v) {
    if (v == null) return "";          // ← null は空文字（末尾スペースなし）
    return v + " ";                    // 金額後ろに半角スペース（既存運用CSVに合わせる）
}
```

**問題**:
- 既存運用 CSV は「金額カラムは必ず末尾半角スペース付き」というフォーマット契約 (コメント L97 で明示)。MF 取込側の strict パーサがこのスペースを期待してフィールド検出している場合、`debitAmount=null` の行で `,,` のように「スペース無しの空フィールド」が混入すると形式違反になる。
- 現状の呼び出し経路では `debitAmount` / `creditAmount` は常に非 null だが、`PaymentMfPreviewRow` の builder 経由で構築する際に Setter 経由で null をセットする経路 (例: `applyVerification` で逆算失敗時のフォールバック) があれば破綻する。
- `exportVerifiedCsv` (L518-538) で `amount = sumVerifiedAmountForGroup(group)` (L504) が `0L` を返したケースは `if (amount == 0L) continue;` (L505) でスキップされるが、`buildPreview` の SUMMARY 行 (L878-895) は `fee = cached.getSummarySourceFee() == null ? 0L : ...` で 0L を許容しているため CSV 出力時に「金額 0 + 末尾スペース」になる (これは仕様通り)。null 出力経路は DTO の null fallback 次第。
- 防御的にも「末尾スペース必須」が契約なら、null も `" "` (スペース 1 文字) または `"0 "` で統一すべき。

**影響**:
- 現状の経路では null は出ないが、フォーマット契約の不変条件をコードで強制していないため、DTO 経路追加時に CSV 形式違反の回帰が発生しうる。MF 取込でエラーが出たときに「どの行が壊しているか」を CSV を目視確認しないと分からない。

**修正案**:
```java
private static String fmtAmount(Long v) {
    long amount = v == null ? 0L : v;
    return amount + " ";
}
```
あるいは Javadoc で「`debitAmount` / `creditAmount` は非 null を前提」とし、`toCsvLine` 入口で `Objects.requireNonNull` で防御する。

---

## Major 指摘

### M-CODE-1. `PaymentMfImportController#convert` / `verify` が成功時に履歴 ID を返さず、UI から「いま CSV 化したファイルの履歴」を辿れない

**箇所**: `backend/src/main/java/jp/co/oda32/api/finance/PaymentMfImportController.java:66-97`, `PaymentMfImportService.java:159-173, 1063-1087`

```java
public byte[] convert(String uploadId, Integer userNo) {
    ...
    saveHistory(cached, preview, csv, userNo);   // ← History の id は捨てる
    return csv;
}
```

**問題**:
- `saveHistory` (L1063-1087) は `historyRepository.save(h)` の戻り値 `TPaymentMfImportHistory` を破棄しており、生成された `history.id` を呼び元に返さない。
- フロント (`payment-mf-import.tsx:86-107`) はダウンロード成功後に `toast.success('CSVをダウンロードしました')` を出すだけで、生成された変換履歴行へのリンク (例: `/finance/payment-mf-history?highlightId=42`) を提示できない。
- 同様に `verify` も `VerifyResult` に履歴 ID が含まれず、後追いでどの Excel を反映したかを辿るには `cached.getFileName()` を別画面で検索する必要がある。
- `saveHistory` は `try/catch (Exception)` で例外を握り潰すため (L1084-1086) 履歴保存失敗を呼び元が知る術もない。「CSV だけダウンロードできて履歴に残らない」状態が静かに発生し得る。

**影響**:
- 監査証跡として「いつ・誰が・どのファイルを変換したか」を MF 取込エラー発生時に追跡したいケースで UI 導線が無く、運用者が「履歴一覧画面」を別途開いて推測する必要がある。
- CLAUDE.md の「DTO 分離」原則とは逆に、Controller が `byte[]` を直接返しているため履歴 ID をヘッダー (`X-History-Id` 等) で返すか、DTO ラッパーへの変更が必要。

**修正案**:
- `saveHistory` の戻り値で `Integer historyId` を返し、Controller の Response Header `X-History-Id` に詰める (`exportVerified` の `X-Skipped-Suppliers` と同じパターン)。
- `saveHistory` の例外握り潰しは「履歴保存失敗」を `log.error` だけでなく `X-History-Saved: false` ヘッダーで UI に伝える。フロントで `toast.warning('履歴保存に失敗しました')` を出す。

### M-CODE-2. `applyVerification` 内で例外発生時に既に書込済みの `t_accounts_payable_summary` 行と `t_payment_mf_aux_row` 削除分がトランザクションロールバックされても、**advisory lock は次の試行で問題ないが、advisory lock が獲得後 Excel パース失敗するケース** で UX が悪い

**箇所**: `PaymentMfImportService.java:184-318`

```java
@Transactional
public VerifyResult applyVerification(String uploadId, Integer userNo) {
    CachedUpload cached = getCached(uploadId);   // ← IllegalArgumentException の可能性
    if (cached.getTransferDate() == null) {
        throw new IllegalArgumentException(...);  // ← lock 取得前に throw
    }
    LocalDate txMonth = deriveTransactionMonth(cached.getTransferDate());
    acquireAdvisoryLock(txMonth);                 // ← ここで lock 取得
    ...
    PaymentMfPreviewResponse preview = buildPreview(uploadId, cached);   // ← ここで例外が起きると lock は @Transactional 終了で解放されるが、
                                                                          //    payable 反映分は ロールバック、補助行 delete は反映済みになる
    saveAuxRowsForVerification(cached, preview, txMonth, userNo);
    ...
}
```

**問題**:
- L293 の `buildPreview` は uploadId のキャッシュが期限切れ (`cleanExpired` が同時に走っている等) で `IllegalArgumentException` を投げ得るが、その時点で L195-288 の payable 反映ループが終わって commit 待ちのため、`@Transactional` でロールバックされる。
- ただし `saveAuxRowsForVerification` の L332 `auxRowRepository.deleteByShopAndTransactionMonthAndTransferDate` は `flushAutomatically=true, clearAutomatically=true` (Repository L30) なので、DELETE は flush 済み。後続の `saveAll` で例外が起きると、ロールバックで DELETE はやり直されるが、advisory lock のおかげで次の試行が直列化されるため最終的に整合する。
- しかし **半反映状態** (payable は更新済み・aux row は空) が中間で見えるケースがあり、別 tx で並行リードしている `/finance/accounts-payable` 画面が「verified=true なのに補助行が無い」一瞬を観測する可能性がある (Read Committed のデフォルト分離レベル)。
- また `saveAuxRowsForVerification` の delete + insert の間で例外が起きると、ロールバックされても **advisory lock が tx 終了で解放され**、次に来た tx は「補助行が一時的に空の状態」を読まずに済む。整合性は守られるが、buildPreview の N+1 解消 (B-W11) のおかげで preview を `applyVerification` 内で再構築するコスト (L293) はそのまま残っている (設計レビュー C-3-1 で既出)。

**影響**:
- 直接の不具合は無い (advisory lock + @Transactional でカバー済み) が、`buildPreview` が `applyVerification` の commit 直前にも走るため、**preview 結果が rule マスタの最新版で再評価され、UI で見ていたプレビューと反映結果がズレるケース** がある。例: ユーザーが preview した直後、別の admin がルールを編集 → `applyVerification` 内 buildPreview で別のルールにヒット → UI 上は「PAYABLE で一致」と見えていた行が DB 上 EXPENSE に反映される。
- この race の防御策として `cached` (キャッシュ) のスナップショット時点でロックされた rules も保持する案が考えられるが、現状未実装。

**修正案**:
- `applyVerification` 入口で `final List<MPaymentMfRule> snapshotRules = ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0");` を取り、`buildPreview` にも同 snapshot を渡す signature を追加する。preview API と applyVerification API で同一 rule snapshot を使う。
- もしくは `buildPreview` を `applyVerification` 内では呼ばず、L195-288 の PAYABLE 反映ループ内で同時に補助行を組み立てるリファクタ (preview 構築は preview/convert 専用にする)。

### M-CODE-3. `buildPreview` の動的 DIRECT_PURCHASE 降格で `summaryTemplate="{source_name}"` 固定が、降格元 PAYABLE ルールの摘要設定を破棄する

**箇所**: `PaymentMfImportService.java:794-806`

```java
if (rule != null && e.afterTotal && "PAYABLE".equals(rule.getRuleKind())) {
    rule = MPaymentMfRule.builder()
            .sourceName(rule.getSourceName())
            .ruleKind("DIRECT_PURCHASE")
            .debitAccount("仕入高")
            .debitSubAccount(null)
            .debitDepartment(null)
            .debitTaxCategory("課税仕入 10%")
            .creditAccount("資金複合")
            .creditTaxCategory("対象外")
            .summaryTemplate("{source_name}")  // ← 元 rule の summaryTemplate 破棄
            .build();
}
```

**問題**:
- 設計レビュー M-1 で「`creditDepartment` 等が NULL のままで…」と指摘されているが、`summaryTemplate` の破棄も同種の見落とし。元 PAYABLE ルールが `summaryTemplate="{source_name} 仕入分"` のように追加情報を持っていた場合、20日払いセクションでは `{source_name}` のみになる。
- DIRECT_PURCHASE が 20日払いセクション (`afterTotal=true`) で実出力される頻度は低いが、ワタキューセイモアなど運用上明示的に DIRECT_PURCHASE 登録している supplier (設計レビュー M-1) と「降格して DIRECT_PURCHASE 化される supplier」で摘要書式が混在し、CSV を MF にインポートした後の検索性が悪くなる。
- `tag` フィールドも builder で指定されておらず、降格時に欠落する。

**影響**:
- MF 仕訳の摘要 / タグの不統一。経理側で MF 取込後に検索性が落ちる。
- 設計レビュー M-1 と合わせて「降格ロジックの責務分散」を解消する際、`tag` / `summaryTemplate` も含めた完全な降格ルール変換を `MPaymentMfRule#deriveDirectPurchaseRule()` に切り出すべき。

**修正案**:
```java
if (rule != null && e.afterTotal && "PAYABLE".equals(rule.getRuleKind())) {
    rule = MPaymentMfRule.builder()
            .sourceName(rule.getSourceName())
            .ruleKind("DIRECT_PURCHASE")
            .debitAccount("仕入高")
            .debitTaxCategory("課税仕入 10%")
            .creditAccount("資金複合")
            .creditTaxCategory("対象外")
            .summaryTemplate(rule.getSummaryTemplate() != null
                ? rule.getSummaryTemplate() : "{source_name}")
            .tag(rule.getTag())
            .build();
}
```

### M-CODE-4. `PaymentMfImportController#history` で shop_no=1 がハードコード、admin shop 切替に追従していない

**箇所**: `PaymentMfImportController.java:188-193`

```java
@GetMapping("/history")
public ResponseEntity<List<PaymentMfHistoryResponse>> history() {
    return ResponseEntity.ok(
            historyRepository.findByShopNoAndDelFlgOrderByTransferDateDescIdDesc(1, "0")
                    .stream().map(PaymentMfHistoryResponse::from).toList());
}
```

**問題**:
- 同 Controller の `convert` / `verify` / `exportVerified` は `FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO` (= 1) 経由で shop_no を取得しているのに対し、ここだけリテラル `1` がハードコード。
- 設計上「買掛仕入は shop=1 固定」(`FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO` Javadoc) なので動作上は問題ないが、将来 shop_no を変える / マルチショップ展開する際に grep で発見しづらい。
- フロント `payment-mf-history.tsx` は「shop 切替不要」「admin でも他 shop を見ない」前提で実装されているが、設計書 §11 等にも明示記述がない。
- `PaymentMfImportService#listAuxRows` (L695-703), `exportVerifiedCsv` (L466), `applyVerification` (L220) は全て `FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO` を使っており、Controller の history だけが落ちている。

**影響**:
- リテラル散在による grep 漏れリスク。直接の不具合は無い。

**修正案**:
- `1` → `FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO` に統一。

### M-CODE-5. `PaymentMfRulesPage` の検索フィルタが `paymentSupplierCode.includes(raw)` で大文字小文字未統一、`debitSubAccount` 検索もキー正規化なし

**箇所**: `frontend/components/pages/finance/payment-mf-rules.tsx:95-105`

```js
const filtered = useMemo(() => {
    const raw = search.toLowerCase()
    const q = normalizeName(raw)
    return rules.filter((r) => {
        if (!raw) return true
        const name = normalizeName(r.sourceName.toLowerCase())
        return name.includes(q)
            || (r.debitSubAccount ?? '').toLowerCase().includes(raw)
            || (r.paymentSupplierCode ?? '').includes(raw)   // ← case-insensitive じゃない
    })
}, [rules, search])
```

**問題**:
- `paymentSupplierCode` は数値 6 桁 + ゼロ埋めなので大文字小文字は実質関係ないが、`debitSubAccount` は `toLowerCase()` のみ正規化されており、`normalizeName` (株式会社/㈱ 除去等) を通していない。
- 振込明細未登録一覧 (`/finance/payment-mf-import` の「マッピングマスタを確認」ボタン → `?q=...`) から遷移するとき、`q` が「サラヤ ㈱」のような会社名フォーマットで来るのに対し、検索対象の `r.debitSubAccount` には会社名が入らない (借方補助科目は商品種別等) ため、`debitSubAccount` 部分の `includes` 判定はほぼ常に false で実害は無い。
- ただし `r.sourceName` 側は `normalizeName` 済みで OR 判定の最初の項で正規化通過しているのに、後段 OR 項目は素のテキスト比較で **挙動が分岐しているのに見た目で分からない**。

**影響**:
- ユーザーが借方補助で「カミイソ」と入れても何もマッチしない、というケース (実害は限定的)。
- 検索ロジックの保守時に「どの列が正規化対象か」が直感的でない。

**修正案**:
```js
const filtered = useMemo(() => {
    const raw = search.toLowerCase()
    const q = normalizeName(raw)
    return rules.filter((r) => {
        if (!raw) return true
        const name = normalizeName(r.sourceName.toLowerCase())
        const sub = normalizeName((r.debitSubAccount ?? '').toLowerCase())
        const code = (r.paymentSupplierCode ?? '').toLowerCase()
        return name.includes(q) || sub.includes(q) || code.includes(raw)
    })
}, [rules, search])
```

---

## Minor 指摘

### m-CODE-1. `PaymentMfPreviewRow` が `@Data` + `@Builder` + 19 フィールドで肥大化、検証ロジックなし

**箇所**: `backend/src/main/java/jp/co/oda32/dto/finance/paymentmf/PaymentMfPreviewRow.java:14-44`

- 19 個の `private` フィールドが羅列され、`PAYABLE` / `EXPENSE` / `DIRECT_PURCHASE` / `SUMMARY` / `UNREGISTERED` の 5 種類のレコードを 1 クラスで表現している。各種別で意味のあるフィールドが異なる (UNREGISTERED は `errorType` だけ意味があり、他は null) が、TypeScript 側 (`types/payment-mf.ts:4-31`) ですべて nullable union として漏れ伝わっている。
- `excelRowIndex: int` (primitive) なのに TypeScript 側で `excelRowIndex: number` で 0 が「無効」と区別できない (UNREGISTERED 行も 0 ではないため運用上 OK だが将来的にバグ温床)。

**修正案**: Sealed クラス階層 (`PreviewPayableRow` / `PreviewExpenseRow` / `PreviewSummaryRow` / `PreviewErrorRow`) に分け、フロント側でも discriminated union (`type: 'PAYABLE' | ...`) で型安全化。CLAUDE.md の「Small files (200-400 lines)」原則と合致。

### m-CODE-2. `PaymentMfRuleService#normalizeCompanyName` の正規表現コンパイルが毎呼び出しで走る

**箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfRuleService.java:185-201`

```java
s = s.replaceAll("\\[[^\\]]*\\]", "");
s = s.replaceAll("株式会社|有限会社|合同会社|合資会社|合名会社", "");
s = s.replaceAll("[松竹梅手]\\s*$", "");
s = s.replaceAll("[\\s\\u3000,.\\-・。、]", "");
```

- `String#replaceAll` は内部で `Pattern.compile` を毎回呼ぶ。`backfillPaymentSupplierCodes` (L114-169) で全 PAYABLE ルール × 全 `MPaymentSupplier` のマトリクスでこの関数が呼ばれるため、規模が大きくなると CPU を食う (現状 93 ルール × 数百 supplier ≒ 数万呼び出し)。

**修正案**: `static final Pattern` 4 本を切り出し、`p.matcher(s).replaceAll("")` で再利用。

### m-CODE-3. `PaymentMfHistoryResponse` が `csvBody` の有無 (`csv_body` は BLOB) を返さず、UI で「再 DL 可能か」事前判定不可

**箇所**: `backend/src/main/java/jp/co/oda32/dto/finance/paymentmf/PaymentMfHistoryResponse.java:16-44`, `payment-mf-history.tsx:62-68`

- 履歴一覧で全行に「再DL」ボタンを出しているが、`csvBody` が NULL のレコード (V011 から手動投入された旧データ等) があるとボタン押下時に 404 になる。事前に `hasCsv: boolean` を返してボタンを disable した方が UX が良い。
- 設計書 `design-payment-mf-import.md` ではこのフィールドは触れられていない。

**修正案**: `PaymentMfHistoryResponse.hasCsv = h.getCsvBody() != null && h.getCsvBody().length > 0` を追加し、フロントで `disabled={!h.hasCsv}` 制御。

### m-CODE-4. `PaymentMfImportService.cleanExpired` の `@Scheduled(fixedDelay)` がトランザクション外でログも吐かない

**箇所**: `PaymentMfImportService.java:1114-1118`

```java
@Scheduled(fixedDelay = 5 * 60 * 1000)
void cleanExpired() {
    long now = System.currentTimeMillis();
    cache.entrySet().removeIf(e -> e.getValue().getExpiresAt() < now);
}
```

- `cleanExpired` 実行時に何件削除されたかログが無いため、運用中の cache 規模把握が困難。
- `package-private` メソッドだが `@Scheduled` は public 不要 (Spring 5.0+) なので問題なし。

**修正案**:
```java
int before = cache.size();
cache.entrySet().removeIf(e -> e.getValue().getExpiresAt() < now);
int removed = before - cache.size();
if (removed > 0) log.debug("PaymentMf cache: {}件期限切れ削除 (残{}件)", removed, cache.size());
```

### m-CODE-5. `payment-mf-import.tsx` の `confirmVerify` ダイアログが `loadingState` を持たない

**箇所**: `frontend/components/pages/finance/payment-mf-import.tsx:404-411`

```jsx
<ConfirmDialog
    open={confirmVerify}
    onOpenChange={setConfirmVerify}
    ...
    onConfirm={() => preview && verifyMut.mutate(preview.uploadId)}
/>
```

- `ConfirmDialog` 自体に `loading` prop がないか、または UI で「反映中…」を表示しないため、verify ボタン (L226-231) は disabled 化されているが、ダイアログを **再度開いて再連打** することで `verifyMut.mutate` が複数回走るリスクがある。advisory lock で DB 整合は取られるが、一括検証を 2 回投げると 2 回目は冗長 (`verified_manually=true` の上書き)。
- 設計レビュー C-1 (admin 必須化) と合わせて、verify はワンショットボタンで多重実行ガードが必要。

**修正案**:
- `ConfirmDialog` の `onConfirm` 後に `setConfirmVerify(false)` を呼んでダイアログを閉じる。あるいはダイアログ開閉ロジックを `verifyMut.isPending` で disabled 化する。

### m-CODE-6. `PaymentMfAuxRowsTable` の `useEffect` が `query.data` の参照変化で発火するため、毎 refetch で `onCountChange` が呼ばれる

**箇所**: `frontend/components/pages/finance/PaymentMfAuxRowsTable.tsx:46-53`

```jsx
const onCountChangeRef = useRef(onCountChange)
useEffect(() => {
    onCountChangeRef.current = onCountChange
}, [onCountChange])

useEffect(() => {
    if (query.data) onCountChangeRef.current?.(query.data.length)
}, [query.data])
```

- TanStack Query は `staleTime` 内でも `data` の参照を維持する (`structuralSharing` デフォルト ON) ため、件数が同じなら ref 同値で再発火しないが、件数が変わったときだけでなく、refetch で配列内容のみ変わったときも参照が変わって発火する。`onCountChange` 自体は冪等なので実害は小さい。
- ただし `query.data` ではなく `query.data?.length` を依存配列に入れる方が意図が明確 (件数のみで再通知)。

**修正案**:
```js
useEffect(() => {
    if (query.data) onCountChangeRef.current?.(query.data.length)
}, [query.data?.length])
```

### m-CODE-7. `payment-mf-import.tsx` の `download` 関数が `transferDate` 不在時に `'unknown'` を CSV ファイル名に入れる

**箇所**: `frontend/components/pages/finance/payment-mf-import.tsx:86-107`

```js
const date = preview.transferDate?.replaceAll('-', '') ?? 'unknown'
const suggest = filename ?? `買掛仕入MFインポートファイル_${date}.csv`
```

- バックエンド側 `PaymentMfImportController#convert` (L71) は `payment_mf.csv` 固定でファイル名を返すため、フロント側のフォールバック `suggest` は使われない (filename は常に decode 成功して `買掛仕入MFインポートファイル.csv` が来る)。
- ただし `convert` が `_${yyyymmdd}` を返さない (設計レビュー m-1 で既出) ため、フロント fallback の `_${date}` パターンとも食い違っており、結果的に「日付付き」or「日付なし」のどちらが来るか保証されていない。
- バックエンド修正 (設計レビュー m-1 で `_${yyyymmdd}` 付与を提案) と合わせてフロント fallback も整合させる必要あり。

**修正案**:
- バックエンド `convert` の filename を `payment_mf_${yyyymmdd}.csv` に変更し、UTF-8 側を `買掛仕入MFインポートファイル_${yyyymmdd}.csv` に揃える (これは設計レビュー m-1 で既出)。
- フロント側は `filename ?? ...` の fallback 自体を削除し、「filename がレスポンスに無いケースは異常」として `throw` する方が defensive。

---

## 設計レビューに無いコード固有の発見まとめ

| # | 場所 | 内容 | Severity |
|---|---|---|---|
| C-CODE-1 | `PaymentMfImportService.java:269-287` | `verified_amount` 税率行二重書込 | Critical |
| C-CODE-2 | `PaymentMfCsvWriter.java:95-98` | `fmtAmount` の null 時に末尾スペース欠落 | Critical |
| M-CODE-1 | `PaymentMfImportController.java:66-97`, `PaymentMfImportService.java:1063-1087` | 履歴 ID を呼び元に返さない | Major |
| M-CODE-2 | `PaymentMfImportService.java:184-318` | `applyVerification` 内 buildPreview の rule snapshot 不整合 race | Major |
| M-CODE-3 | `PaymentMfImportService.java:794-806` | DIRECT_PURCHASE 動的降格で `summaryTemplate` / `tag` 欠落 | Major |
| M-CODE-4 | `PaymentMfImportController.java:188-193` | `history` で shop_no=1 リテラル | Major |
| M-CODE-5 | `payment-mf-rules.tsx:95-105` | 検索フィルタの正規化非統一 | Major |
| m-CODE-1 | `PaymentMfPreviewRow.java:14-44` | DTO の 5 種別 union が 1 クラス | Minor |
| m-CODE-2 | `PaymentMfRuleService.java:185-201` | 正規表現コンパイル都度実行 | Minor |
| m-CODE-3 | `PaymentMfHistoryResponse.java`, `payment-mf-history.tsx` | `hasCsv` フラグ未提供 | Minor |
| m-CODE-4 | `PaymentMfImportService.java:1114-1118` | `cleanExpired` ログ無し | Minor |
| m-CODE-5 | `payment-mf-import.tsx:404-411` | `ConfirmDialog` 多重実行ガード不足 | Minor |
| m-CODE-6 | `PaymentMfAuxRowsTable.tsx:46-53` | useEffect 依存が `query.data` で過剰発火 | Minor |
| m-CODE-7 | `payment-mf-import.tsx:86-107` | filename fallback が backend と食い違い | Minor |

---

## レビューチェックリスト結果

### Spring Boot 観点

| 項目 | 結果 | コメント |
|---|---|---|
| Layer 違反 | OK | Controller→Service→Repository の階層遵守 |
| `@Transactional` 配置 | OK | `applyVerification` / `exportVerifiedCsv` (read-write) / `buildVerifiedExportPreview` (`readOnly=true`) / `saveVerifiedExportHistory` (`REQUIRES_NEW`) / `listAuxRows` (`readOnly=true`) と適切 |
| 自己注入パターン | OK | `@Lazy PaymentMfImportService self` で REQUIRES_NEW proxy 確保 (L66-72)。コメントに意図記載あり |
| Constructor injection | OK | `@RequiredArgsConstructor` ではなく明示 constructor (L101-111)。`@Autowired @Lazy private` のみ field injection だが REQUIRES_NEW 用で必要悪 |
| N+1 解消 | OK | `findByShopNoAndSupplierCodeInAndTransactionMonth` で IN 化済 (`buildPreview` L770-774, `applyVerification` L217-222) |
| バリデーション | △ | `PaymentMfRuleRequest` の `ruleKind` が `@NotBlank` のみで値域 (`PAYABLE`/`EXPENSE`/`DIRECT_PURCHASE`) 未強制。`@Pattern` または `enum` 化推奨 (設計レビューでも既出) |
| Migration 安全性 | △ | V011 の `INSERT INTO m_payment_mf_rule` に `ON CONFLICT DO NOTHING` 無く、Flyway repair / 二重実行で重複混入リスク (設計レビューで既出) |
| トランザクション境界 | △ | `applyVerification` の advisory lock は `@Transactional` 内で取得、tx 終了で自動解放。設計レビュー M-3 (shop_no 非含意) も既出 |
| 例外設計 | △ | `saveHistory` (L1063-1087) と `saveVerifiedExportHistory` 呼び出し (L582-584) の例外握り潰しが「履歴保存失敗を呼び元に返さない」設計。M-CODE-1 と関連 |

### Next.js 観点

| 項目 | 結果 | コメント |
|---|---|---|
| 'use client' 配置 | OK | 全ページ先頭に適切に付与 |
| TanStack Query 使用 | OK | `staleTime` 設定済み (`payment-mf-rules.tsx:50` 60_000, `VerifiedCsvExportDialog.tsx:47` 10_000, `PaymentMfAuxRowsTable.tsx:41` 30_000) |
| TypeScript strict | OK | `null` / nullable の扱いは概ね適切 |
| useEffect 依存配列 | △ | `PaymentMfAuxRowsTable.tsx:53` の `[query.data]` は `[query.data?.length]` の方が意図的 (m-CODE-6) |
| エラーハンドリング | OK | `ApiError` instanceof 判定 + toast (`VerifiedCsvExportDialog.tsx:88-91`) |
| アクセシビリティ | △ | `payment-mf-import.tsx` の Badge / 絵文字 (🟢🟡🔴) は色覚多様性に配慮した文字併記済み |
| ファイル size | OK | `payment-mf-import.tsx` 467 行で CLAUDE.md 上限 800 ギリギリ。関数分割は将来検討 |

### Payment MF 固有観点

| 項目 | 結果 | コメント |
|---|---|---|
| CSV 形式 (CP932 + LF + 末尾半角スペース) | △ | `PaymentMfCsvWriter` で実装。null 時の末尾スペース欠落リスク (C-CODE-2) |
| afterTotal フラグ (PAYABLE→DIRECT_PURCHASE 降格) | △ | `summaryTemplate` / `tag` 欠落 (M-CODE-3)、設計レビュー M-1 と複合 |
| aux_row 洗い替え保存 | OK | `flushAutomatically=true, clearAutomatically=true` で DELETE→INSERT の順序保証 (Repository L30) |
| Excel パーサ異常系 | △ | `PaymentMfCellReader#readLongCell` の long overflow silent truncate (設計レビュー m-7 既出)。FORMULA セルでの string→numeric フォールバックは適切 (`readLongCell` L75-77) |
| 100円閾値判定 | OK | `FinanceConstants.PAYMENT_REPORT_MINOR_DIFFERENCE` で集約。`abs().compareTo(threshold) <= 0` (BigDecimal) と `Math.abs(diff) <= long_threshold` (long) の両方使用、scale 0 前提で問題なし |
| BigDecimal scale | △ | `toLongFloor` (L403-405) で `setScale(0, DOWN).longValueExact()` 呼んでおり、scale > 0 の値を切り捨て → 軽微な丸め差は発生しうるが運用想定範囲内 |
| advisory lock キー | △ | `transactionMonth.toEpochDay() & 0xFFFF_FFFFL` で shop_no を含まず (設計レビュー M-3 既出) |
| verified_amount の二重計上 | NG | C-CODE-1。書込スキーマ自体に欠陥 |

---

## 評価まとめ

設計レビューが指摘した課題群 (Critical 3 / Major 6 / Minor 7) と独立に、**コード固有の Critical 2 / Major 5 / Minor 7** を発見した。

特に **C-CODE-1 (verified_amount 二重計上)** は、設計レビュー C-3 で「税抜逆算の精度問題」として部分的に触れられているが、**書込スキーマ自体が二重計上を内包**しており、`sumVerifiedAmountForGroup` という読取側のヒューリスティックでしか整合性を保てない構造になっている点が本レビューでの新発見。マージ前に書込仕様を整理する必要がある。

**C-CODE-2 (CSV 末尾スペース欠落)** はフォーマット契約違反の潜在リスクで、現状の経路では発生しないが防御的修正を推奨。

Major 群は履歴 ID 返却 (M-CODE-1)、降格ルール完全性 (M-CODE-3)、shop_no リテラル (M-CODE-4) など、運用追跡性とリファクタリング耐性に関わる課題。

Minor 群は DTO 設計、UI 多重実行ガード、検索フィルタ等の品質改善で、時間が許す範囲で対応すれば良い。

設計レビュー Critical 群と本レビュー C-CODE-1/C-CODE-2 の解消をマージ前条件とし、Major 群は v2.1 ドットリリースで対応するのが現実的。
