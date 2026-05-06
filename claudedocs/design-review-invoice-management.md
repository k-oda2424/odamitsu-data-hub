# 設計レビュー: 請求機能 (Cluster A)

レビュー日: 2026-05-04
対象設計書: `claudedocs/design-invoice-management.md` (Phase 1 で生成)
対象ブランチ: `refactor/code-review-fixes`
レビュアー: Opus サブエージェント

---

## サマリー

- 総指摘件数: **Critical 3 / Major 7 / Minor 8**
- 承認状態: **Needs Revision**

最も重大な点は、

1. `t_invoice` / `m_partner_group` / `m_partner_group_member` の Flyway マイグレーションが リポジトリ内に存在しない（V001〜V029 のいずれにも DDL 無し）。`spring.jpa.hibernate.ddl-auto=none` のためクリーン環境ではアプリ起動後に SELECT/UPSERT が即落ちる。prod は `validate` 設定なので、既存 stock-app 由来の手動 DDL に依存している＝再現性ゼロ。
2. 入金日一括更新エンドポイントに `shopNo` の権限境界が一切無く、第1事業部ユーザーが第2事業部の `invoiceId` を任意に POST して入金消込を破壊できる（IDOR）。同じく `partner-groups` の CRUD も `shopNo` の正当性を検証していない。
3. UPSERT が `(shopNo, closingDate)` で既存行を全件 SELECT した後、Excel に存在しなくなった既存行を `del_flg` で消すロジックも履歴も残さない（再取込しても残骸が残る）。同時にトランザクション内で得意先名 1 件でも非数値だと全行ロールバックする（現場運用で 1 セルの汚れで全面停止するリスク）。

---

## Critical 指摘

### C-1: `t_invoice` / `m_partner_group` の Flyway マイグレーションが欠落

- **箇所**: `backend/src/main/resources/db/migration/` 配下 (`V001`〜`V029`)、`backend/src/main/resources/config/application.yml:7` (`ddl-auto: none`)、`application-prod.yml:16` (`ddl-auto: validate`)
- **問題**: 設計書 §3 (`design-invoice-management.md:50-90`) に記載のある `t_invoice`, `m_partner_group`, `m_partner_group_member` の `CREATE TABLE` がリポジトリ全体を grep しても存在しない（NFKC インデックス追加 `V005__create_nfkc_indexes.sql:32-34` で参照されているのみ）。`ddl-auto=none` のため、新規環境でアプリ起動 → `/finance/invoices` を叩くと SQL レベルで失敗する。prod は `validate` のため、Entity に `nullable=false` を付けても DDL に同制約が無ければ起動時バリデーション失敗、付けなくても schema drift が検出されない。
- **影響**: クリーン環境再構築の手順が破綻。disaster recovery / 開発者オンボーディング不能。`@UniqueConstraint(columnNames = {"partner_code", "closing_date", "shop_no"})` (`TInvoice.java:22-24`) も DDL に無ければ UPSERT の冪等性を担保できない（並行取込で重複挿入が発生する）。
- **修正案**:
  1. `V030__create_t_invoice_and_m_partner_group.sql` を新規追加し、`TInvoice` / `MPartnerGroup` / `m_partner_group_member` の DDL とユニーク制約・FK・インデックスを正規化。
  2. 既存 prod は `flyway.baseline-version` を引き上げて該当マイグレーションをスキップ（`application.yml:24` のコメント方針に倣う）。
  3. 設計書 §3 の各テーブルに「DDL: `Vxxx__*.sql`」の参照リンクを追記。

### C-2: 入金日一括更新で `shopNo` 権限境界が未チェック (IDOR)

- **箇所**: `backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java:464-475`、`PUT /api/v1/finance/invoices/bulk-payment-date`
- **問題**: `BulkPaymentDateRequest#invoiceIds` を `tInvoiceService.findByIds(...)` で取得し、何の検証もせず `paymentDate` を上書きする。`@PreAuthorize("isAuthenticated()")` のみで shop / role の検証無し。第2事業部ユーザーが第1事業部の `invoiceId` を含めても、`InvoiceImportService` 以外の経路で `LoginUserUtil.resolveEffectiveShopNo` (`LoginUserUtil.java:28-38`) のような権限正規化が一切ない。
- **影響**: 異事業部ユーザーが他事業部の入金消込を破壊。経理データの IDOR (Insecure Direct Object Reference)。
- **修正案**:
  1. 一括更新の前に `LoginUserUtil.resolveEffectiveShopNo` で許容 `shopNo` を取得し、`invoices.stream().filter(inv -> permittedShopNo == null || permittedShopNo.equals(inv.getShopNo()))` で絞る。フィルタで除外した分は 403 ではなく `skippedCount` として返却して UX を保つ。
  2. 同様の権限ガードを `PUT /invoices/{invoiceId}/payment-date` (`FinanceController.java:434-445`) と `partner-groups` CRUD (`L479-503`) にも適用。
  3. partner-group の `shopNo` は `PartnerGroupRequest` の入力値ではなく、サーバ側でログインユーザの shopNo を強制（admin のみリクエスト値を許容）するのが安全。

### C-3: UPSERT に「Excel から消えた既存行」の処理規約が無い & 1 セル不正で全行ロールバック

- **箇所**: `InvoiceImportService.java:138-188` (Phase 2 UPSERT)、`L237-246` (`convertPartnerCode` で `NumberFormatException` → `IllegalArgumentException`)
- **問題**:
  1. `(shopNo, closingDate)` で既存行を SELECT し、Excel に存在する `partnerCode` のみ更新。Excel から外れた既存行（取消・誤登録）は **そのまま残る**。`del_flg` カラムも持たないため復活させる手段も無い (`design-invoice-management.md:74-77` でも明記)。再取込でデータが整合しなくなる。
  2. `@Transactional` (`L40`) なので、A 列に非数値 1 行が混入しただけで `convertPartnerCode` が `IllegalArgumentException` を投げ全件ロールバック → 「300 行のうち 1 行だけ汚い」場面で取込ゼロになる。
  3. その 1 行のスキップ判断（continue / break / abort）が DTO `InvoiceImportResult.errors` に詰める設計が現状空 (`InvoiceImportService.java:185`)。
- **影響**: SMILE 側の運用ミス 1 件で当月締の取込が完全停止する。再取込が冪等でない（取消行が残る）。
- **修正案**:
  1. パース異常は行単位で `errors.add("row=N, code=...")` に積み、最後に「処理対象 0 件 ⇒ 例外」「処理対象 > 0 件 ⇒ 部分成功」で返す。設計書 §5.1 / §8 T1 を更新。
  2. Excel から消えた既存行の取り扱いを「警告ログ + UI に `removedFromSourceCount` 表示」「もしくは別フラグで取消マーク」のいずれにするかを業務判断で確定し、設計書 §2 スコープに追記。今のままでは「請求一覧に表示されている行が SMILE 実態と乖離する」という運用バグになる。
  3. 同じファイルを 2 回取込しても `payment_date` が保持され、他列も idempotent であることをテストで保証 (`InvoiceImportServiceTest:234-275` の発想を `re-import twice` ケースまで拡張)。

---

## Major 指摘

### M-1: `MPartnerGroup#partnerCodes` を `EAGER` で持つ N+1 リスク

- **箇所**: `MPartnerGroup.java:31-38`、`MPartnerGroupService.java:19-23`
- **問題**: `@ElementCollection(fetch = FetchType.EAGER)` でグループ × member 子テーブルを EAGER 取得。`findByShopNoOrderByGroupNameAsc` がグループ件数分のサブ SELECT を発行する典型的 N+1。
- **影響**: グループ件数 100、member 平均 20 件のとき 100+1 クエリ。dev では実害が出にくいが prod の経理締日に集中アクセスすると詰まる。
- **修正案**: `LAZY` + `findAll` 後に `JOIN FETCH` で 1 クエリ化、または DTO Projection (`@Query` でグループ id のリスト → 子テーブルを別 SELECT 1 回 + Java 側 group by) に切替。設計書 §7-7 の「N+1 候補」TODO を実施に移す。

### M-2: `partner-groups` 削除時に「現在グループに紐付いている請求書がある」整合性チェック無し

- **箇所**: `MPartnerGroupService.java:50-56`
- **問題**: 物理削除のみ。子テーブル `m_partner_group_member` は `@ElementCollection` の cascade で消えるが、その削除が「経理担当が選択中の状態だった」ケースの UX 復旧策が無い。さらに、グループ削除と一括入金更新が並走したらどう振る舞うかが未定義。
- **影響**: 監査時に「どのグループに対して入金記録したか」が追跡不能（履歴が残らない）。
- **修正案**:
  1. 削除を論理削除 (`del_flg`) に変更、もしくは削除イベントを `audit_log` テーブルに記録。
  2. 設計書 §6.3 に「削除中に他ユーザが当該グループで一括反映を実行した場合の整合性」明記。最小実装としては `findByShopNo` で `del_flg='0'` を条件に追加。

### M-3: `TInvoice` に監査列が無く誰がいつ入金日を更新したか追跡不能

- **箇所**: `TInvoice.java:25-67`、`design-invoice-management.md:76` (「監査フィールドは持っていない」と明記)
- **問題**: `payment_date` は経理処理の最重要属性であり「いつ・誰が・どの値から」変更したかを残す要件は経理機能として必須。`add_user_no` / `modify_user_no` / `add_date_time` / `modify_date_time` も無い。
- **影響**: 入金消込の証跡欠如。MEMORY.md に既に「軸 F 監査証跡基盤」が保留タスクとして登録されているとおり、買掛側と同じ問題を売上請求側でも内包する。
- **修正案**:
  1. `t_invoice_payment_date_history` 子テーブル (invoice_id, old_payment_date, new_payment_date, changed_at, changed_by_user_no) を追加し、`PUT /invoices/{id}/payment-date` と一括更新エンドポイントから INSERT。
  2. 設計書 §7-9 の「監査列なし」を Critical 級の課題として §8 に格上げし、軸 F の対象に含める。

### M-4: `InvoiceVerifier` の特殊得意先がハードコード（マスタ化必須）

- **箇所**: `InvoiceVerifier.java:41-43` (`QUARTERLY_BILLING_PARTNER_CODE="000231"`, `CLEAN_LAB_PARTNER_CODE="301491"`, `JOSAMA_PARTNER_CODE="999999"`)、`L282` (四半期特殊月 `2/5/8/11`)
- **問題**: 法人マスタの追加・移動・統合（B-CART 事業部統合方針 = 2026-04-21 のような業務イベント）でロジック側に手が入る構造。設計書 §7-6 / §8 T11 でも認知済だが対策計画が空欄。
- **影響**: 業務側のマスタ追加に追従できず、例外的得意先が増えるたびに本番デプロイが必須。
- **修正案**:
  1. `m_invoice_special_rule (partner_code, shop_force, billing_cycle, quarterly_months)` のような「特殊扱いマスタ」を新設し、コード定数を排除。
  2. 上様判定はマスタの `is_walk_in` flag で表現。
  3. CLI ツールから JSON で投入できる initial-load を整備。
  4. これが大きい場合の最小スコープとして「定数を `application.yml` の `batch.invoice.*` に逃がす」だけでも可。設計書 §8 に実装計画として明記。

### M-5: `findQuarterlyInvoice` の virtual `TInvoice` が `invoiceId` 不整合を生む

- **箇所**: `InvoiceVerifier.java:234-266` (とくに `L257-263`)
- **問題**: 当月15日締め + 前月末締めを合算して virtual `TInvoice` を作る際、`invoiceId` を「最初の」、`closingDate` を「最後の」請求書から取り、`netSalesIncludingTax` だけ合算。後段の `applyMatched` (`L351`) で `summary.invoiceNo = invoice.invoiceId` として保存されるが、表示上の `closingDate` と保存される `invoiceNo` が別レコード由来。監査時に「請求書 ID から該当請求書を引いてきても締日が一致しない」状態になる。
- **影響**: 売掛金画面 → 請求書ジャンプの整合性破綻。設計書 §8 T5 に既出だが解決方針未記載。
- **修正案**:
  1. virtual ではなく、`t_invoice` 側に「四半期合算の親レコード」を生成する (`m_invoice_special_rule` と連動)。
  2. もしくは `summary.invoiceNo` に「合算の代表 ID」と明示するメタ列 `is_quarterly=true` を追加し、UI 側で「合算」と表示。

### M-6: `TInvoiceService` のデッドコードと重複メソッド

- **箇所**: `TInvoiceService.java:31-33` (`getAllInvoices`, Controller 未使用)、`L52-66` (`saveInvoice` と `insert` が完全同義)、`L133-135` (`findBySpecification` 未使用)
- **問題**: 設計書 §8 T3 / T4 で言及済。重複/未使用が残ると「どちらを使うべきか」の判断ノイズが増え、変更時の影響範囲解析が膨らむ。`saveInvoice` と `insert` のどちらがいつ使われるかドキュメントなし。
- **影響**: コードレビュー負荷増、リファクタ時の歪み。
- **修正案**:
  1. `getAllInvoices` / `findBySpecification` 削除。
  2. `insert` を削除し `saveInvoice` に統一（あるいは `save` に rename）。
  3. PR 単位で「どれが消されたか」を `MEMORY.md` の `next-session-tasks.md` に追記。

### M-7: `closingDate` を `String` で持つ運用上の罠

- **箇所**: `TInvoice.java:38-39`、`InvoiceImportService.java:195-229` (parser)、`InvoiceVerifier.java:220-228` (`formatClosingDateForSearch`)、設計書 §7-1, §8 T12
- **問題**: 「末」漢字リテラル + `YYYY/MM/DD` の混在。`/finance/invoices` の `closingDate` 検索が `likePrefixNormalized` (`TInvoiceSpecification.java:20-22`) のため、`yyyy/MM` での月絞り込みは出来るが、範囲（from-to 月）/ 直近 N ヶ月といった検索ができない。比較演算は事実上不可能。`InvoiceVerifier#formatClosingDateForSearch` が `YYYY/MM/末` か `YYYY/MM/DD` のいずれかを生成するため、文字列マッチがズレた瞬間（例: cutoffCode=0 が `MONTH_END` ではない別 PaymentType に分類された場合）に必ず NotFound。
- **影響**: 経理機能としての検索性が低い。マイグレーション時の文字コード事故 (`末` がデータ中で全角 / 漢字混在 など) でサイレント不一致。
- **修正案**:
  1. 中期: `closing_date` を `LocalDate` + `is_month_end:boolean` に分割し、文字列解釈ロジックを排除。
  2. 短期 (互換): DB 制約 `CHECK (closing_date ~ '^\d{4}/\d{2}/(末|\d{2})$')` を追加し、汚いデータが入らないようガード。設計書 §8 T12 に「短期 / 中期」の二段計画として明記。

---

## Minor 指摘

### m-1: 入金日一括更新が WARN ログだけで部分成功を許容している

- **箇所**: `FinanceController.java:464-475`
- **問題**: 要求 ID 中に存在しないものがあっても 200 で返却 (`L467-470`)、レスポンスに `notFoundIds` を含めない。
- **修正案**: レスポンスに `requestedCount`, `updatedCount`, `notFoundIds` を含め、フロントで toast 上に「N 件は反映できませんでした」を出す。

### m-2: `MPartnerGroupService#findByShopNo(null)` がクライアント任せに全件返す

- **箇所**: `MPartnerGroupService.java:19-23`
- **問題**: `shopNo == null` で `repository.findAll()` を呼ぶ。フロントは `effectiveShopNo` を必ず付けて呼ぶため (`invoices.tsx:136`) 通常は到達しないが、API 直叩きで「全店舗のグループ」を漏洩できる経路。
- **修正案**: `LoginUserUtil.resolveEffectiveShopNo(shopNo)` で正規化し、admin のみ全件可。

### m-3: ファイル名による shop_no 推定の脆弱性

- **箇所**: `InvoiceImportService.java:54-57`、`InvoiceImportDialog.tsx:71` (UI 説明文)
- **問題**: ファイル名に「松山」が含まれるかで shop_no を決定。命名規約変更で誤判定。設計書 §7-2 で既出だが具体策無し。
- **修正案**: UI に「事業部選択 (第1 / 第2)」プルダウンを必須項目として追加し、`shopNo` パラメータを常時送信。ファイル名判定はあくまで「省略時のヒント」とする (`api spec` も明確化)。

### m-4: `TInvoice.shopNo` が JPA 上 NULL 許容のままユニーク制約参加

- **箇所**: `TInvoice.java:62-63`、設計書 §8 T9
- **問題**: `@Column(name = "shop_no")` のみ。設計書も「実態 NOT NULL」と注記しつつ DDL 確認 TODO のまま。NULL を含むタプルはユニーク扱いされない (PostgreSQL のデフォルト挙動)。
- **修正案**: C-1 と一緒に `nullable = false` を Entity に追加 + DDL に `NOT NULL` を明記。

### m-5: `getCellBigDecimal` の `STRING` ケースで `NumberFormatException` を握りつぶし `0` を返す

- **箇所**: `InvoiceImportService.java:285-291`
- **問題**: 文字列セルがパースできない場合 `BigDecimal.ZERO` にフォールバック (silent)。`null` ではなく `0` で保存され、UI で「金額入力なし」と「0 円」が区別できない。
- **修正案**: `errors` リストに「row=N, column=K, value=...」を積む。設計書 §5.3 に明示。

### m-6: `InvoiceResponse` のシリアライズ時、`closingDate` が `String` のままフロント `Invoice.closingDate: string` と一致するが、型整合の保証コメントなし

- **箇所**: `InvoiceResponse.java:15`、`invoices.tsx:31`、設計書 §8 T6, T14
- **問題**: 命名と型整合は OK だが「`closingDate` が `String` であり LocalDate ではない」ことを setter / DTO の Javadoc に明示しないと将来 LocalDate 化時にフロントが壊れる。
- **修正案**: `InvoiceResponse#closingDate` の Javadoc に「YYYY/MM/末 or YYYY/MM/DD のフォーマット文字列。LocalDate 化は後続フェーズ」を明記。

### m-7: `@Transactional` 設定が読み専用に最適化されていない

- **箇所**: `TInvoiceService.java:31`, `L42`, `L100`, `L110`、`L114-117` のみ `readOnly = true`
- **問題**: 検索系メソッドの大半に `@Transactional(readOnly = true)` が無い。Hibernate のセッションで dirty check の不要なオーバーヘッド。
- **修正案**: 検索系には `@Transactional(readOnly = true)` を付与。クラスレベルに `@Transactional(readOnly = true)` を置き書き込み系のみ `@Transactional` で上書きするのが Spring 標準。

### m-8: フロントの `useMemo(columns, [])` が ESLint 抑止で副作用リスク

- **箇所**: `invoices.tsx:248-301`
- **問題**: `selectedIds` を依存に含めず、`SelectCell` 経由のクロージャで参照。`useMemo` の依存配列が空のため、`handlePaymentDateChange` のリファレンスが変わっても columns は再生成されず、内部のミューテーションが入った瞬間に表示が古くなる潜在バグ。設計書 §8 T7 で要検証。
- **修正案**: `handlePaymentDateChange` を `useCallback` で確定参照化 (済) し、columns 依存配列に `selectedIds` ではなく安定参照のみ含める or `Column` 定義を関数外に出して props としてだけ受ける構造にする。

---

## 設計書 vs 実装の乖離

| # | 観点 | 設計書記述 | 実装の実態 | 対応 |
|---|------|-----------|-----------|------|
| D-1 | DDL の所在 | §3.1〜§3.2 でテーブル定義あり、参照ドキュメントとして `V005__create_nfkc_indexes.sql` のみ列挙 | DDL 本体が **どのマイグレーションにも無い** (Critical C-1) | C-1 修正 + 設計書に DDL ファイルパス追記 |
| D-2 | API 認可 | §4 共通プレフィックス `@PreAuthorize("isAuthenticated()")`、ロール制限なしと明記 (§7-8) | 同左 ＋ ロール制限が不在 (Critical C-2) | 認可方針を §4 ヘッダに明示し admin / shop ユーザの操作差を表化 |
| D-3 | 取込の冪等性 | §5.1 「all-or-nothing」と明記、`payment_date` 保持にも触れる | 行単位スキップ／部分成功の規約が欠落 (Critical C-3) | §5.1 に「行エラー時の挙動」「Excel 削除行の扱い」を追記 |
| D-4 | partner-group `shopNo` の信頼源 | §4.5 でリクエスト DTO に `shopNo` (`@NotNull`) | サーバ側で `LoginUserUtil` 経由の正規化なし (Critical C-2 / Minor m-2) | DTO の `shopNo` は admin のみ受理、非 admin はサーバ側で上書き |
| D-5 | `MPartnerGroup` の partner-code 重複 | 言及なし | `partnerCodes` リストに同一コードが入っても DB / Service 側で重複排除しない (`MPartnerGroupService.java:35-36, 45-46`) | §3.2 に「重複入力は許容しない / DB ユニーク」を追記し Service で `Set` 化 |
| D-6 | `getCellBigDecimal` のフォールバック | §5.3 「STRING / NUMERIC / FORMULA 全対応」 | 失敗時 `0` フォールバックに踏み込んでいない (Minor m-5) | §5.3 に「失敗時は 0 ではなく errors に記録」と明記 |
| D-7 | `findBySpecification` 経路 | §4.1「ソート: closingDate 降順」「`getAllInvoices()` 残存」 | `findBySpecification` (`TInvoiceService.java:133`) は Controller 未使用 (Major M-6) | 設計書 §8 T4 を Major 級と再分類、削除計画を明記 |
| D-8 | virtual TInvoice の意味 | §5.4 で「四半期特殊」「virtual TInvoice を生成」 | `invoiceId` / `closingDate` の不整合 (Major M-5) | §5.4 に「virtual の限界 = invoiceId は最古、closingDate は最新」「監査時の参照方法」明記 |

---

## レビューチェックリスト結果

### Spring Boot 観点

| 項目 | 結果 | コメント |
|------|------|---------|
| Layer 違反 (Controller に business logic) | OK | UPSERT は Service に閉じる (`InvoiceImportService.java:138-173`) |
| `@Transactional` 範囲 | NG | 検索系に readOnly 未付与 (m-7)、`InvoiceImportService#importFromExcel` が all-or-nothing (C-3 連動) |
| N+1 | NG | `MPartnerGroup` `EAGER` (M-1) |
| DI (`@RequiredArgsConstructor` / コンストラクタ injection) | OK | 全 Service / Controller |
| DTO 変換 (`from(entity)` factory) | OK | `InvoiceResponse.from`, `PartnerGroupResponse.from` |
| バリデーション (`@Valid` + Bean Validation) | 一部 NG | `BulkPaymentDateRequest`, `PaymentDateUpdateRequest`, `PartnerGroupRequest` には付与済 / `listInvoices` の `closingDate` フォーマット validation 無し |
| Migration 安全性 | NG | C-1 (`t_invoice` DDL 欠落) |
| 例外ハンドリング | 改善余地 | `InvoiceImportService` 内の `IllegalArgumentException` を Controller で 400 に変換 (`FinanceController.java:454-456`) は OK だが、行単位エラーが errors に乗らない |

### 請求機能固有観点

| 項目 | 結果 | コメント |
|------|------|---------|
| Excel 取込の異常系（空セル、想定外フォーマット、文字化け） | 部分 NG | `getCellBigDecimal` の silent zero fallback (m-5)、行単位エラー集約なし (C-3) |
| UPSERT 冪等性 (同 Excel 再取込) | 部分 NG | 既存行 `payment_date` 保持はテスト済 (`InvoiceImportServiceTest:234-275`) だが「Excel から消えた行」処理が未定義 (C-3) |
| 特殊得意先処理マスタ化 | NG | ハードコード (M-4) |
| 入金日一括更新の権限 | NG | `shopNo` ガード無し (C-2) |
| partner_group 削除の整合性 | 部分 NG | 物理削除 + 履歴なし (M-2) |
| virtual TInvoice の監査 | NG | invoiceId / closingDate 不整合 (M-5) |
| 認可 (admin only / shop user) | NG | 全エンドポイント `isAuthenticated()` のみ (C-2 / m-2) |
| 監査列 (誰が入金日更新したか) | NG | 監査列なし (M-3) |

### コード品質

| 項目 | 結果 | コメント |
|------|------|---------|
| デッドコード | NG | `getAllInvoices`, `findBySpecification`, `insert` (M-6) |
| 命名 | OK | DTO / Entity / Service とも統一 |
| ログ出力 | 改善余地 | スキップ理由 `log.debug` (`InvoiceImportService.java:106, 114`) は INFO で残した方が運用追跡しやすい |
| テスト | 部分 OK | `InvoiceImportServiceTest` で 16 ケース、ただし `InvoiceVerifier` の 999999 / 000231 / 301491 のテストは未確認 (§8 T10 のとおり) |
| 設定外出し | NG | 特殊得意先コードと四半期月をハードコード (M-4) |

---

## 推奨アクション順序

1. **C-1**: `V030__create_t_invoice_and_m_partner_group.sql` を最優先で追加（マイグレーション欠落は disaster recovery を阻害）。
2. **C-2**: `bulk-payment-date` / `payment-date` / `partner-groups` の shopNo ガードを追加。E2E テストで他事業部 `invoiceId` を含めて 403 / skip となることを確認。
3. **C-3**: 行単位エラー集約 + Excel 削除行の扱い決定 + 設計書 §5.1 / §8 T1 更新。
4. **M-3**: 入金日変更履歴テーブル追加（軸 F に組み込み、買掛側と同一基盤で実装）。
5. **M-4 / M-5**: 特殊得意先マスタ化 + virtual `TInvoice` 設計再考。
6. **M-1 / M-6 / M-7 / m-1〜m-8**: コード品質改善 PR としてまとめてレビュー。
7. 設計書側の TODO (§8 T1〜T14) を上記実装と同期して closeout / 残課題をリラベル。
