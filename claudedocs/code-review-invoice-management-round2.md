# 再レビュー: 請求機能 (Cluster A) 修正後

レビュー日: 2026-05-04
対象: triage SF-01〜SF-24 適用後 (branch `refactor/code-review-fixes`)
レビュアー: Opus サブエージェント (round 2)
ビルド/テスト確認: `./gradlew compileJava` PASS / `InvoiceImportServiceTest` 22件 PASS (failures=0)

## サマリー
- 新規発見: Critical 1 / Major 6 / Minor 5
- Block 判定解除: **ほぼ解除済み**。ただし C-N5 (V031 prod 適用時の UNIQUE 衝突)、および認可基盤 `LoginUserUtil.resolveEffectiveShopNo` の例外フォールスルーが残存
- 既存修正の評価: Critical 7 件すべて意図通り適用、SF-19/SF-20/SF-21 性能改善も妥当。SF-13 errors 集約は良質。一方で防御層の最深部 (`LoginUserUtil`) の throws→silent fallback と V031 の SET shop_no=1 自動補正に runtime 衝突リスクが残る

## Block 判定解除確認

| Critical | 解消 | 備考 |
|---|---|---|
| C-N1 useMemo stale closure | OK | `invoices.tsx:246-303` で `useMemo` 依存配列に `[selectedIds, toggleSelect, handlePaymentDateChange]` 追加。`SelectCell` が `React.memo({checked, invoiceId, onToggle})` 化済 (`invoices.tsx:52-64`) |
| C-N2 入金日クリア (`@NotNull` 撤去) | OK | `PaymentDateUpdateRequest.java:23` に `@NotNull` 無し / `BulkPaymentDateRequest.java:29` に Javadoc で「null = 一括クリア」明示。`FinanceController.java:429,493` で `setPaymentDate(null)` 経路実装済 |
| C-N3 取込 IDOR (`POST /finance/invoices/import`) | OK (要観察) | `FinanceController.java:443-449` で `LoginUserUtil.resolveEffectiveShopNo` 後に「明示 shopNo ≠ effective」を 403 化。但し下記 C-N5 参照 |
| C-N4 Excel 内重複 partnerCode dedup | OK | `InvoiceImportService.java:181-190` で `LinkedHashMap.put()` 戻り値で衝突検出 + `errors` 集約 + WARN ログ |
| design C-1 V031 Migration | 部分 | DDL 自体は冪等 + `IF NOT EXISTS` で良質。但し prod の `shop_no=NULL` 自動補正経路 (line 60) で UNIQUE 衝突が起こり得る (Major M-N5) |
| design C-2 IDOR (bulk-payment-date / payment-date / partner-groups) | OK | `FinanceController.java:464-502` (bulk) `:417-432` (single) `:506-555` (groups) で全 3 経路に `assertShopAccess` / `resolveEffectiveShopNo` ガード実装。レスポンスに `forbiddenIds` も含まれる |
| design C-3 一意性 / Excel 削除行扱い | 部分 | UNIQUE 制約は SF-01 で解決。Excel 削除行 (= 取込時に消滅した既存 row) 判定は DD-06 (DESIGN-DECISION) として deferred。triage 通り |

## Critical (新規発見)

### C-N5: `LoginUserUtil.resolveEffectiveShopNo` の例外スワロー → IDOR バイパス余地

**ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/util/LoginUserUtil.java:28-38`

```java
public static Integer resolveEffectiveShopNo(Integer requested) {
    try {
        Integer loginShopNo = getLoginUserInfo().getUser().getShopNo();
        if (loginShopNo == null || loginShopNo == 0) {
            return requested;        // ← admin: 要求値そのまま
        }
        return loginShopNo;
    } catch (Exception e) {
        return requested;             // ← 認可情報取得失敗 → 要求値そのまま
    }
}
```

**問題**: `getLoginUserInfo()` が throws した場合 (= 認証情報が壊れている / principal が `LoginUser` でない異常状態) も**呼び出し元が要求した shopNo をそのまま返す**。これは `@PreAuthorize("isAuthenticated()")` 通過後の前提が壊れた状態で、本来は SecurityException で 401/500 にすべき。

**SF-02/SF-03 への影響**:
- `FinanceController.importInvoices` (line 446-449) は `effectiveShopNo` と `shopNo` を比較するが、両方とも要求値の場合 `equals` が必ず true → 403 にならない
- `bulkUpdatePaymentDate` (line 470) では `permittedShopNo = resolveEffectiveShopNo(null)`。例外発生時は `null` が返り、ガード `if (permittedShopNo != null)` 自体がスキップされて**全 invoice の入金日更新が成立**
- `listPartnerGroups` / `createPartnerGroup` も同様に `effectiveShopNo` が要求値そのまま

**深刻度**: Critical。通常運用では起きないが、`SecurityContextHolder.getContext().getAuthentication().getPrincipal()` が `String "anonymousUser"` を返すケース (Spring Security の standard) で `instanceof LoginUser` が false → throw → 要求値スルー、というシナリオで全認可が無効化される。

**推奨修正**: `LoginUserUtil.resolveEffectiveShopNo` で catch した例外は `throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ...)` に変更。または Controller 側で `Objects.requireNonNull(LoginUserUtil.getLoginUserInfo())` を上で実行して Spring の認可前提を確実にする。

(本指摘は SF-02/SF-03 適用前から既存だが、IDOR ガードが `resolveEffectiveShopNo` を防御の中核に据えた今回の修正で「クリティカルパス」に昇格した。)

## Major (新規発見)

### M-N1: V031 `UPDATE t_invoice SET shop_no = 1 WHERE shop_no IS NULL` で UNIQUE 衝突リスク

**ファイル**: `backend/src/main/resources/db/migration/V031__create_t_invoice_and_m_partner_group.sql:60-72`

**問題**:
1. line 60 で `shop_no=NULL` 行を一律 1 に補正
2. line 70-72 で `UNIQUE (partner_code, closing_date, shop_no)` 制約を追加

事前重複チェック (line 27-29) は `WHERE shop_no IS NOT NULL` でフィルタしている。NULL 同士の `(partner_code, closing_date)` 重複は検出されないため:
- 旧 stock-app 由来で松山分が `shop_no=NULL` のまま投入され、本社分も `shop_no=NULL` で同一 (partner_code, closing_date) に重複している場合
- line 60 で両方 `shop_no=1` に書き換わり
- line 70-72 で UNIQUE 衝突 → migration 失敗

**runbook の懸念**: `runbook-v031-baseline.md:58-67` で `shop_no=NULL` 行に言及はあるが、NULL→1 補正後の UNIQUE 衝突を assert する SQL が無い。

**推奨修正**: V031 内で `UPDATE` 直前に NULL 同士の重複を fail-fast チェック:
```sql
DO $$
DECLARE dup INTEGER;
BEGIN
    SELECT COUNT(*) INTO dup FROM (
        SELECT partner_code, closing_date FROM t_invoice
         WHERE shop_no IS NULL
         GROUP BY 1,2 HAVING COUNT(*) > 1
    ) t;
    IF dup > 0 THEN
        RAISE EXCEPTION 'V031: shop_no=NULL 行に (partner_code, closing_date) 重複あり (% 件)。手動 dedup 後に再実行してください。', dup;
    END IF;
END $$;
```
または auto-fill を撤去し、手動補正必須の運用にする。

### M-N2: `TInvoice` Entity と V031 DDL の `shop_no` nullability 不一致

**ファイル**: `backend/src/main/java/jp/co/oda32/domain/model/finance/TInvoice.java:62-63`

```java
@Column(name = "shop_no")
private Integer shopNo;  // ← nullable=true (default)
```

V031 では `ALTER COLUMN shop_no SET NOT NULL` (line 61) で NOT NULL 化。Entity 側は `nullable = true` のまま。Hibernate `ddl-auto=validate` が prod で発火しても両者 NULL 解釈が異なる場合に起動失敗する可能性は低い (validate は型/カラム名/長さは見るが nullable 不一致は warn 止まり) が、Entity 側で誤って `setShopNo(null)` した場合に DB レイヤーまで例外が遅延伝播する。

**推奨修正**: `@Column(name = "shop_no", nullable = false)` に統一。

### M-N3: `MPartnerGroupService.delete()` の物理削除フォールバック未対応 (idempotency)

**ファイル**: `backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java:545-555` + `MPartnerGroupService.java:73-83`

Controller `deletePartnerGroup` は `partnerGroupService.findById(id)` で existence check するが、`findById` は `del_flg='0'` でフィルタ済 (`MPartnerGroupService.java:38-41`)。
このため:
- 1 回目 `DELETE` → `findById` で取得成功 → 論理削除 → 204
- 2 回目 `DELETE` → `findById` が null → 404

REST DELETE の idempotency 慣習 (2回目も 204) を破る。Service 内 `delete()` は `if (!"0".equals(group.getDelFlg())) return;` で冪等だが、Controller 層で `findById` フィルタが先に走るため意味がない。

**推奨修正**: Controller で `findById` ではなく `repository.findById()` (フィルタなし) を直接呼ぶ Service 経由メソッド (例: `findByIdIncludingDeleted`) を追加し、shop 認可は `getShopNo()` から取得する。または DELETE 二重リクエストは 404 で許容する旨 API 仕様に明記。

### M-N4: `FinanceController.bulkUpdatePaymentDate` の `forbiddenIds` 計算順序ロジック揺れ

**ファイル**: `FinanceController.java:482-484`

```java
List<Integer> notFoundIds = new java.util.ArrayList<>(request.getInvoiceIds());
invoices.forEach(inv -> notFoundIds.remove(inv.getInvoiceId()));
notFoundIds.removeAll(forbiddenIds);
```

このロジックは:
1. 全要求 ID を `notFoundIds` に積む
2. `invoices` (= shop ガード後に**既に除外済の**残存 invoice) の ID を引く
3. `forbiddenIds` (権限外で除外した ID) を引く

順序的には正しいが、step 2 の `invoices.forEach` は SF-03 の `removeIf` で既に `forbiddenIds` 対象が除外された後の `invoices` を見ている。よって `notFoundIds` には「DB に元々存在しない ID」+「権限外の ID」両方が一旦含まれる。step 3 の `removeAll(forbiddenIds)` で権限外を引いて純粋な notFound を残す。**最終結果は正しい**が、

- レスポンスの semantics: `notFoundIds` = "本当に DB に存在しない ID" / `forbiddenIds` = "存在するが他 shop"
- `tInvoiceService.findByIds` は `findAllById(invoiceIds)` (line 77) を呼んでおり、admin が他 shop の ID を含めると全部返す。非 admin が他 shop の ID 1個 + 自 shop ID 1個を投げると、`invoices` 取得後 `removeIf` で他 shop 分が除外、forbiddenIds に追加。step 2 で自 shop ID が除外、step 3 で forbiddenIds が引かれる → notFoundIds = [] (正しい)

機能的に正しいが、step 2 の `forEach + remove(Integer)` は `Integer` の `equals` で動くため $O(N \cdot M)$ になる。`@Size(max=2000)` に制約されるが、最大 2000 件 × 2000 件 = 4M 比較。性能上問題ないが見通しは悪い。

**推奨修正**: `Set<Integer> dbExistingIds = invoices.stream().map(TInvoice::getInvoiceId).collect(toSet());` に変更し `notFoundIds = request.getInvoiceIds().stream().filter(id -> !dbExistingIds.contains(id) && !forbiddenIds.contains(id)).toList();` に整理。

### M-N5: SF-04 `normalizeAndDedup` で `Long.parseLong` overflow → silent skip

**ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/MPartnerGroupService.java:113-124`

```java
try {
    long numericCode = Long.parseLong(code);
    return String.format("%06d", numericCode);
} catch (NumberFormatException e) {
    return null;  // ← caller でスキップ
}
```

UI 側で 20 桁の数字を投げられた場合 (typo / 攻撃) `Long.MAX_VALUE` (9223372036854775807, 19 桁) を超えると `NumberFormatException` で**サイレントに dedup 対象外**。`PartnerGroupRequest.partnerCodes` には個々の `@Pattern`/`@Size` バリデーションが無い (`PartnerGroupRequest.java:18-20`)。

結果: `partnerCodes = ["000231", "9999999999999999999999"]` を投げると 1 件だけ登録されて Service ログに痕跡なし (`normalizePartnerCode` はログを出さない)。ユーザーは「登録できた」と思うが片方が消える。

**推奨修正**:
1. `PartnerGroupRequest.partnerCodes` に `@Size(max=20)` `@Pattern(regexp="^[0-9<>\\s]+$")` を element-level で追加 (`@Valid` 経由)
2. または `normalizePartnerCode` 内 catch で `log.warn("partner_code 正規化失敗: '{}'", code)` を出力

`InvoiceImportService.convertPartnerCode` (line 300-309) は `IllegalArgumentException` を throw して呼び出し元で `errors` 集約しているのと整合がとれていない (Service の dedup は silent ignore、Excel parser は fail)。

### M-N6: SF-15 数値変換失敗時の `BigDecimal.ZERO` 補正は依然 silent zero 扱い

**ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/InvoiceImportService.java:382-395`

```java
private BigDecimal getCellBigDecimalOrError(...) {
    BigDecimal v = getCellBigDecimal(cell);
    if (v == null) {
        ...
        errors.add(msg);
        log.warn(msg);
        return BigDecimal.ZERO;  // ← Entity 永続化時には 0 として通る
    }
    return v;
}
```

`triage SF-15` の意図は「silent zero フォールバック撤去」だが、実装は「errors にメッセージを書きつつ 0 で返す」になっている。`InvoiceImportResult.errors` がフロントで表示されない限り、ユーザーには「全行成功 + 該当金額が 0」に見える。`InvoiceImportDialog.tsx:113-127` のサマリ表示にも `errors` が表示されていない (success path で skip 件数のみ)。

**推奨修正**:
1. `InvoiceImportDialog.tsx` の result 表示パネル (line 115-122) に `result.errors.length > 0` の警告セクションを追加
2. または `getCellBigDecimalOrError` の戻り値を `null` のままにし、対応する Entity フィールドを `BigDecimal` (nullable) にしてエラー時は明示的に NULL を保存
3. 短期は (1) で十分。triage SF-13 で「`InvoiceImportDialog` で `result.errors.length > 0` を warning 風にリスト表示」と明記されているのに**フロント実装が抜けている**

### M-N7: `closingDate` 検証で月数値 (例 13) を許容

**ファイル**: `InvoiceImportService.java:46-48`, `FinanceController.java:404-407`, `V031:85`

正規表現 `^\d{4}/\d{2}/(末|\d{2})$` は月部分が `00`〜`99` を受理。`2024/13/01` のような不正月や `2024/02/30` のような無効日も DB に通る。

ただし `parseClosingDate` (line 258-292) は `YearMonth.of(year, month)` で month 範囲外を `DateTimeException` 化するため Excel 取込経路では fail-fast する。**SF-17 の listInvoices `@Pattern` 経由で `closingDate=2024/13` を投げた場合は弾けない**。Service の `findByDetailedSpecification` 内では文字列前方一致のため例外なくクエリ実行されて空結果。

実害は限定的だが SF-01 の DDL CHECK と同じ正規表現を共有しているため、検索 API でも `2024/13/01` のような不正値を受理してしまう。

**推奨修正**: 中期は DD-13 の `LocalDate` 化 (DEF-05)。短期は listInvoices で `LocalDate.parse` を試みる Bean Validator を追加 (例: ConstraintValidator) — 但し SAFE-FIX scope を超えるため受容可。

## Minor (新規発見)

### m-N1: `MPartnerGroupRepository` に `JpaSpecificationExecutor` 削除されていない (TInvoiceRepository も)
- `TInvoiceRepository.java:12` は依然 `JpaSpecificationExecutor<TInvoice>` を継承し SF-12 の derived query 移行後も `findByDetailedSpecification` (TInvoiceService:99-117) で Specification 使用継続。意図通りだが、SF-10 「デッドコード削除」の流れで `JpaSpecificationExecutor` も将来削減検討。Specification は検索 API 1 個だけのため。

### m-N2: `InvoiceImportService.parseClosingDate` の throw が `errors` 集約と非整合
- line 91-96 で `closingDate` が CHECK 制約違反なら `throw IllegalArgumentException`。Phase 1 (line 98-191) の行単位 `errors` 集約と方針が異なる (SF-13 の意図は「行単位 errors」)。Row2 が壊れていれば全件アボートが妥当ではあるので、これは設計判断として OK (但し Javadoc に「Row2 fail = 全件アボート」と明記推奨)。

### m-N3: `MPartnerGroupService.findById(null)` は IllegalArgumentException
- `Controller.updatePartnerGroup` (line 531) `Controller.deletePartnerGroup` (line 548) は path variable 経由で `id != null` 保証されるが、Service の `findById(Integer id)` 直接呼び出しは null safety 無し。

### m-N4: `InvoiceImportResult.shopNo` の Javadoc 欠落
- `dto/finance/InvoiceImportResult.java:13` に Javadoc 無し。`shopNo == 0` の admin 取込 (CRITICAL: 現状 admin が `shopNo=0` 指定すると `effectiveShopNo=0` が `int` で Service の `int shopNo = ...0` になるが `shopNo=0` の Excel UPSERT は実質 NG。SF-02 修正は admin が省略時のみファイル名推定にフォールバックする実装で、admin が明示 `shopNo=0` を送ると t_invoice に shop_no=0 で挿入される) — これは別 Critical 候補だが、UI フロントは admin 用に `0` 送出しない設計のため受容範囲。Javadoc 化推奨。

### m-N5: SF-23 `ERROR_MESSAGE_HINTS` の正規表現マッチ順序
- `InvoiceImportDialog.tsx:29-50`: `Array.find` で先頭マッチを採用。`/締日|closingDate/i` が `/sheet|シート/i` より先 → 「シート 1 の締日が...」のメッセージは「締日」マッチ。「Sheet1 が無い」message も「締日」マッチに引っかかる可能性は低いが、優先度の意図 (specific → general) を comment で明記推奨。

## SF-XX 別の確認結果

| SF | 状態 | 備考 |
|---|---|---|
| SF-01 | OK + Major | DDL は冪等で良質、但し M-N1 (NULL→1 衝突) 残存 |
| SF-02 | OK + Critical 依存 | C-N5 の `LoginUserUtil` 例外スワロー次第で IDOR バイパス可 |
| SF-03 | OK + Critical 依存 | 同上。bulk-payment-date は `forbiddenIds` 含めたレスポンス追加実装済 (line 497-501) |
| SF-04 | OK + Major | M-N5 (overflow silent skip) 残存。`normalizeAndDedup` のロジック自体は意図通り |
| SF-05 | OK | `useMemo` deps 完全。`SelectCell`/`PaymentDateCell` の `React.memo` 化済 |
| SF-06 | OK | `@NotNull` 撤去 + Javadoc 明示。`PaymentDateCell.onBlur` で `value=''` → `null` 送信 (`invoices.tsx:79-84`) |
| SF-07 | OK | `LinkedHashMap.put()` 戻り値で衝突検出、後勝ち + warn |
| SF-08 | OK | `existingMap.put()` 衝突時 WARN ログ実装 (`InvoiceImportService.java:202-208`) |
| SF-09 | OK | `Optional<TInvoice>` 化、Controller で `.isEmpty()` → 404 |
| SF-10 | OK | `getAllInvoices` / `findBySpecification` / `insert` / `deleteInvoiceById` は全削除 (grep 確認済) |
| SF-11 | OK | `private static final TInvoiceSpecification SPEC` (TInvoiceService:29) |
| SF-12 | OK | `findByShopNoAndClosingDate` derived query で無名 Specification 撤去 |
| SF-13 | 部分 | バックエンド errors 集約は OK、フロント表示が未実装 (M-N6 参照) |
| SF-14 | OK | `formatNumeric(double)` static helper、`BigDecimal.toPlainString` 採用 |
| SF-15 | 部分 | silent zero 撤去は意図 OK だが errors 表示が UI まで届いていない (M-N6) |
| SF-16 | OK | `@Size(max=2000)` 追加 (BulkPaymentDateRequest.java:25) |
| SF-17 | OK + Minor | `@Pattern` で listInvoices 検証、但し月数値 `13` を許容 (M-N7) |
| SF-18 | OK | 3 Service 全てクラスレベル `readOnly=true` + 書込みは `@Transactional` override |
| SF-19 | OK | `findActiveByShopNoFetchMembers` / `findAllActiveFetchMembers` で `LEFT JOIN FETCH`、`fetch=LAZY` |
| SF-20 | OK | `del_flg='0'` 絞り込み + `delete()` を論理削除化、但し M-N3 (idempotency) 余地 |
| SF-21 | OK | `findByShopNoAndPartnerCodeAndClosingDateIn` 1 クエリ集約、`InvoiceVerifier.findQuarterlyInvoice` で利用 (line 240-241) |
| SF-22 | OK | `frontend/types/partner-group.ts` に `PartnerGroupRequest` / `PartnerGroupSavePayload` 切り出し済 |
| SF-23 | OK + Minor | `buildErrorMessage` の正規表現 5 種マッピング正常、優先度コメント推奨 (m-N5) |
| SF-24 | OK | `InvoiceResponse.closingDate` の Javadoc に DD-13 / DEF-05 言及あり |

## 既存修正との整合性

- **InvoiceImportServiceTest** (22 テスト): 全 PASS。Mockito mock を `findByShopNoAndClosingDate` (SF-12) に書き換え済で、テスト意図は維持されている (新規追加: 「重複 partnerCode で後勝ち」「partial errors」の専用テストは未追加 — triage SF-07/SF-13 の「テスト確認」項に明記されているが、必須ではない)
- **AccountsReceivableCutoffReconciler** (line 221) も `findByShopNoAndPartnerCodeAndClosingDate` 経由で SF-09 Optional 化と整合
- **InvoiceVerifier** (line 240-241) は SF-21 の derived query を直接利用、`tInvoiceService.findByShopNoAndPartnerCodeAndClosingDateIn` 経由

## 推奨アクション

### 即修正 (Block 解除に必要)
1. **C-N5**: `LoginUserUtil.resolveEffectiveShopNo` の例外スワローを除去 → 401/403 throw に変更。これが解消されないと SF-02/SF-03 全体の防御が形骸化する
2. **M-N1**: V031 に NULL→1 補正前の重複 fail-fast SQL を追加 (またはコメントで強制要否を明示)。runbook も同期
3. **M-N6**: `InvoiceImportDialog.tsx` の result panel に `errors` 表示を追加 (SF-13 の triage に明記された未実装項目)

### 次ループ (DESIGN-DECISION 寄り)
4. **M-N2**: `TInvoice.shopNo` Entity を `nullable=false` に統一
5. **M-N3**: 論理削除の二重削除 idempotency 方針を決定 (404 受容 / 204 idempotent)
6. **M-N5**: `PartnerGroupRequest.partnerCodes` の element-level バリデーション追加 (`@Size(max=20)` 等)
7. **M-N4**: `bulkUpdatePaymentDate` の `forbiddenIds` 計算を Set 化で見通し改善

### 受容 (Minor / SAFE-FIX scope 外)
8. m-N1 〜 m-N5: 命名/Javadoc/コメント改善で次回 refactor PR で対応可
9. M-N7 (`closingDate=2024/13` 許容): DD-13 の `LocalDate` 化 (DEF-05) で根治、当面は受容

## 結論

**ほぼクリア** (Block 解除条件 7 件はすべて意図通り適用済) だが、

- C-N5 の `LoginUserUtil` 例外スワローは SF-02/SF-03 の認可基盤の中核に残存しており、防御層が想定する前提を破る潜在欠陥。**次ループで必修正**
- M-N1 の V031 prod 適用時の UNIQUE 衝突は運用 incident につながるため、runbook 更新 + DDL に fail-fast を追加することを強く推奨
- M-N6 の SF-13 errors UI 未実装はユーザー認知バグ (silent zero とほぼ等価)

3 件の即修正を加えれば Cluster A は確実にマージ可能。

## 出力ファイルパス

`C:\project\odamitsu-data-hub\claudedocs\code-review-invoice-management-round2.md`
