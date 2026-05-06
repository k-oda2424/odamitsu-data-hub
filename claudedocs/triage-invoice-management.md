# Triage: 請求機能 (Cluster A) 修正対応

triage 日: 2026-05-04
対象指摘総数: 約 46 件 (設計 18 + コード 17 + Codex 11)
出典: design-review-invoice-management.md / code-review-invoice-management.md / codex-adversarial-invoice-management.md
レビュー判定: **Block** (コードレビュー C-N1 / C-N2 / C-N3 / C-N4)

## サマリー
| 分類 | 件数 |
|---|---|
| SAFE-FIX | 24 件 |
| DESIGN-DECISION | 14 件 |
| DEFER | 7 件 |
| ALREADY-RESOLVED | 1 件 |

優先度概観:
- **Block 解除に必須な Critical SAFE-FIX**: 6 件 (SF-01 / SF-02 / SF-03 / SF-04 / SF-05 / SF-06) — Migration 欠落、IDOR 3 経路、stale closure、入金日クリア 400、Excel 内重複 dedup
- **DESIGN-DECISION** は Codex 由来 (`t_invoice` 責務分離 / Excel staging / 業務請求書キー / partner-group 履歴 / 入金状態機械 / 権威階層 / virtual TInvoice / 特殊得意先マスタ等) と Opus 設計レビュー M-3 (監査列) / M-4 (特殊得意先マスタ) / M-5 (virtual TInvoice) の中規模スキーマ判断
- **DEFER** は監査ログ・PII 取扱・パーサー責務分離など別 Sprint 課題
- **新規 Migration 必要**: ✅ V031 (`t_invoice` / `m_partner_group` / `m_partner_group_member` DDL 追加)

---

## SAFE-FIX (即適用)

### SF-01: V031 migration: `t_invoice` / `m_partner_group` / `m_partner_group_member` DDL 追加 (BLOCKER)
- **元レビュー**: design-review C-1 / code-review m-N2 (重複検出に必要なユニーク制約) / 設計 m-4
- **対象ファイル** (新規): `backend/src/main/resources/db/migration/V031__create_t_invoice_and_m_partner_group.sql`
- **修正内容**:
  - `CREATE TABLE t_invoice` (`TInvoice.java:25-67` を正規化、`shop_no NOT NULL`、`UNIQUE(partner_code, closing_date, shop_no)`、`CHECK (closing_date ~ '^\d{4}/\d{2}/(末|\d{2})$')` (設計 M-7 短期対応))
  - `CREATE TABLE m_partner_group` (`group_id` PK、`group_name`、`shop_no NOT NULL`、`del_flg`)
  - `CREATE TABLE m_partner_group_member` (`partner_group_id` FK、`partner_code`、`UNIQUE(partner_group_id, partner_code)`)
  - prod baseline 既存スキーマとの整合のため `application.yml:24` の `flyway.baseline-version` 引き上げ手順を README/コメントで明示
- **想定影響範囲**: 新規 1 ファイル + `application.yml` baseline コメント
- **テスト確認**: `./gradlew flywayMigrate` (dev) → `./gradlew compileJava` → 起動確認 (`@UniqueConstraint` 一致)
- **依存関係**: なし。SF-04 の `m_partner_group_member` UNIQUE 制約は本タスクで生成
- **担当推奨**: 1 サブエージェント (グループ A "Block 解除", 先頭タスク)

### SF-02: 取込エンドポイント `POST /finance/invoices/import` の shopNo 認可ガード (Critical, IDOR)
- **元レビュー**: code-review C-N3
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java:447-462`
- **修正内容**: `LoginUserUtil.resolveEffectiveShopNo(shopNo)` で正規化、非 admin が他 shop を要求した場合は `ResponseStatusException(HttpStatus.FORBIDDEN)`。`InvoiceImportService.importFromExcel(file, effectiveShopNo)` に正規化済み値を渡す
- **想定影響範囲**: 1 Controller method + フロント `InvoiceImportDialog.tsx:71` の説明文修正 (省略時のヒントである旨を明示)
- **テスト確認**: E2E で第1事業部ユーザーが `shopNo=2` を送ると 403、admin は許容
- **依存関係**: なし。SF-03 と同じセキュリティグループで一括対応推奨
- **担当推奨**: 1 サブエージェント (グループ A)

### SF-03: `bulk-payment-date` / `payment-date` / `partner-groups` の shopNo 認可ガード (Critical, IDOR)
- **元レビュー**: design-review C-2 / code-review M-N1 (response 整形と統合)
- **対象ファイル**: `FinanceController.java:434-475` (3 endpoint) + `MPartnerGroupService.java:19-23` (m-2)
- **修正内容**:
  1. `bulkUpdatePaymentDate`: `LoginUserUtil.resolveEffectiveShopNo(null)` で許容 shop を取得し、`invoices.removeIf(inv -> !permitted(inv.getShopNo()))`。レスポンスに `requestedCount` / `updatedCount` / `notFoundIds` / `forbiddenIds` を追加 (M-N1 統合)
  2. `updatePaymentDate (single)`: 取得後の `invoice.shopNo` が `effectiveShopNo` 境界外なら 403
  3. `partner-groups` CRUD: `request.shopNo` を非 admin はサーバ側で `effectiveShopNo` に上書き (m-2 同時解決)
- **想定影響範囲**: 1 Controller (3 method) + 1 Service (`MPartnerGroupService`) + 既存フロント挙動互換 (`forbiddenIds` 表示は別 PR でも可)
- **テスト確認**: E2E で異 shop の `invoiceId` を含めた一括更新が `forbiddenIds` に分類されることを確認
- **依存関係**: なし
- **担当推奨**: SF-02 と同一サブエージェント (グループ A)

### SF-04: `MPartnerGroupService` の partnerCodes 正規化 + dedup
- **元レビュー**: code-review M-N2 / 設計 D-5
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/MPartnerGroupService.java:32-47` + `PartnerGroupDialog.tsx:91-103`
- **修正内容**: Service `create` / `update` で `partnerCodes` を `trim` → `normalizePartnerCode (6桁0埋め)` → `distinct()` → `List` 化。`UNIQUE(partner_group_id, partner_code)` 制約 (SF-01 で追加) と一致させる
- **想定影響範囲**: 1 Service
- **テスト確認**: `./gradlew compileJava` + 既存 partner-group CRUD で「29」と「000029」が同一扱いになることを確認
- **依存関係**: SF-01 (UNIQUE 制約)
- **担当推奨**: 1 サブエージェント (グループ B "コード品質")

### SF-05: フロント `useMemo(columns, [])` の stale closure 修正 (Critical, UI 機能停止)
- **元レビュー**: code-review C-N1 / design m-8 (設計側 TODO)
- **対象ファイル**: `frontend/components/pages/finance/invoices.tsx:71-91, 248-301`
- **修正内容**:
  1. `SelectCell` を `{ checked: boolean; invoiceId: number; onToggle }` のシグネチャに変更 (Set 全体ではなく boolean のみ受ける)
  2. `useMemo` 依存配列に `[selectedIds, handlePaymentDateChange]` を追加 (`eslint-disable` コメントは削除)
  3. `SelectCell` を `React.memo` で wrap してチェック ON/OFF 時の再レンダー範囲を抑制
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit` + Playwright で個別チェック / 全選択 / グループ選択の表示反映を確認
- **依存関係**: なし
- **担当推奨**: 1 サブエージェント (グループ E "フロント")

### SF-06: 入金日クリア時の `@NotNull` 制約撤去 + 仕様明記 (Critical, UI 操作不能)
- **元レビュー**: code-review C-N2
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/dto/finance/PaymentDateUpdateRequest.java:9-12` + `frontend/components/pages/finance/invoices.tsx:71-91, 168-170`
- **修正内容**:
  - `@NotNull` を撤去し `private LocalDate paymentDate;` (null = クリア許容)。Javadoc に「null = 入金日クリア」明示
  - フロント `handlePaymentDateChange` の `value || null` のままで OK (バックエンド側で受理可になる)
  - `BulkPaymentDateRequest.paymentDate` も同方針 (一括クリア許容、業務確認後)
  - エラートーストを「入金日のクリアに失敗しました」等に分岐
- **想定影響範囲**: 1 DTO + 1 フロントファイル + (一括 DTO 同方針なら 1 DTO 追加)
- **テスト確認**: E2E で空文字入力 → 200 → DB の `payment_date=NULL` を確認
- **依存関係**: 業務確認 (クリア許容/不許容) — クリア不許容なら fronnt-only ガードに切替
- **担当推奨**: 1 サブエージェント (グループ A)

### SF-07: Excel 内重複 `partnerCode` の dedup + 警告 (Critical, データ整合)
- **元レビュー**: code-review C-N4
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/InvoiceImportService.java:138-170`
- **修正内容**: Phase 1 終了後に `LinkedHashMap<String, TInvoice>` で `partnerCode` dedup。衝突時は `log.warn` + `result.errors.add(...)` で記録、後勝ちで採用 (業務確認後に「合算」へ切替の余地)
- **想定影響範囲**: 1 Service
- **テスト確認**: 単体テスト追加 (`InvoiceImportServiceTest` に「重複 partnerCode で後勝ち + warn」ケース)
- **依存関係**: なし。SF-13 (errors 集約) と同タスクで連続実装可
- **担当推奨**: 1 サブエージェント (グループ A)

### SF-08: `existingMap` の merge function に WARN ログ追加
- **元レビュー**: code-review m-N2
- **対象ファイル**: `InvoiceImportService.java:145-146`
- **修正内容**: `(a, b) -> a` の代わりに lambda で `log.warn("DB に partnerCode={} の重複あり ...", a.getPartnerCode(), a.getInvoiceId(), b.getInvoiceId())` を出力
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: なし。SF-07 と同一エージェント
- **担当推奨**: SF-07 と同一サブエージェント

### SF-09: `TInvoiceService#getInvoiceById` を `Optional` に統一
- **元レビュー**: code-review m-N1
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/domain/service/finance/TInvoiceService.java:110-112` + `FinanceController.java:438-441`
- **修正内容**: 戻り値を `Optional<TInvoice>` に変更、Controller 側で `.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))`
- **想定影響範囲**: 1 Service + 1 Controller method
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: なし
- **担当推奨**: 1 サブエージェント (グループ B)

### SF-10: `TInvoiceService` のデッドコード削除 (`getAllInvoices` / `findBySpecification` / `insert`)
- **元レビュー**: design-review M-6
- **対象ファイル**: `TInvoiceService.java:31-33, 52-66, 133-135`
- **修正内容**: 未使用メソッド削除。`saveInvoice` を `save` に rename (任意)
- **想定影響範囲**: 1 Service
- **テスト確認**: `./gradlew compileJava` で参照エラーがないこと
- **依存関係**: なし
- **担当推奨**: SF-09 と同一サブエージェント

### SF-11: `TInvoiceSpecification` の field-level `new` を Spring Bean 化
- **元レビュー**: code-review M-N3
- **対象ファイル**: `TInvoiceService.java:19` + `TInvoiceSpecification.java`
- **修正内容**: `TInvoiceSpecification` に `@Component`、`TInvoiceService` でコンストラクタ injection。または `private static final` に変更
- **想定影響範囲**: 1 Service + 1 Specification class
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: なし
- **担当推奨**: SF-09 と同一サブエージェント

### SF-12: `InvoiceImportService` の `existingInvoices` 取得を Repository derived query 化
- **元レビュー**: code-review M-N5
- **対象ファイル**: `InvoiceImportService.java:139-143` + `TInvoiceRepository.java`
- **修正内容**: `findByShopNoAndClosingDate(Integer shopNo, String closingDate)` を Repository に追加し、無名 Specification を置換
- **想定影響範囲**: 1 Service + 1 Repository
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: なし
- **担当推奨**: SF-09 と同一サブエージェント

### SF-13: `InvoiceImportResult.errors` を行単位エラー集約に活用 + フロント表示
- **元レビュー**: design-review C-3 (部分) / code-review m-N3
- **対象ファイル**: `InvoiceImportService.java:54-188` + `InvoiceImportResult.java` + `frontend/components/pages/finance/InvoiceImportDialog.tsx`
- **修正内容**:
  1. パース異常 (`convertPartnerCode` の `IllegalArgumentException`、`getCellBigDecimal` の数値変換失敗) を `result.errors.add("row=N, column=K, value=...")` で記録し continue
  2. Phase 1 完了時に「処理対象 0 件 ⇒ throw IllegalArgumentException」「> 0 件 ⇒ 部分成功で返却」の判定を追加
  3. フロント `InvoiceImportDialog` で `result.errors.length > 0` を warning 風にリスト表示
- **想定影響範囲**: 1 Service + 1 DTO (errors はもう存在) + 1 フロント
- **テスト確認**: `InvoiceImportServiceTest` に「1 行不正 + 残り正常」ケースを追加
- **依存関係**: SF-07 と相性良し
- **担当推奨**: SF-07 と同一サブエージェント (グループ A 内 "取込整合性")

### SF-14: `getCellStringValue` の FORMULA 分岐を共通 `formatNumeric` ヘルパに整理
- **元レビュー**: code-review M-N4
- **対象ファイル**: `InvoiceImportService.java:259-291`
- **修正内容**: `private static String formatNumeric(double val)` を切り出し、`String.valueOf(double)` の "3.14" 表記問題を `BigDecimal#toPlainString` で回避。FORMULA 分岐とその他 NUMERIC 系で共有
- **想定影響範囲**: 1 Service
- **テスト確認**: `./gradlew compileJava` + 単体テストで小数 / 整数の双方を確認
- **依存関係**: SF-13 (エラー収集) と相性良し
- **担当推奨**: SF-13 と同一サブエージェント

### SF-15: `getCellBigDecimal` の STRING ケースで silent zero フォールバック撤去
- **元レビュー**: design-review m-5
- **対象ファイル**: `InvoiceImportService.java:285-291`
- **修正内容**: 文字列セルがパース失敗時、`BigDecimal.ZERO` ではなく `errors` に記録 + null を返す。後段の `parsedInvoice.setNetSales` 等が null 受理できるよう Entity を確認
- **想定影響範囲**: 1 Service
- **テスト確認**: 「金額セルに『-』」ケースで 0 ではなく warning が出ることを確認
- **依存関係**: SF-13 (errors 集約)
- **担当推奨**: SF-13 と同一サブエージェント

### SF-16: `BulkPaymentDateRequest` に `@Size(max = 2000)` 追加
- **元レビュー**: code-review M-N6
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/dto/finance/BulkPaymentDateRequest.java`
- **修正内容**: `@Size(max = 2000, message = "一括反映は2000件以下にしてください")` を `invoiceIds` に追加
- **想定影響範囲**: 1 DTO
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: なし
- **担当推奨**: 1 サブエージェント (グループ B)

### SF-17: `listInvoices` の `closingDate` フォーマットバリデーション
- **元レビュー**: design-review チェックリスト「listInvoices の closingDate format validation 無し」
- **対象ファイル**: `FinanceController.java` (検索 endpoint) + 新規 DTO or `@Pattern` 直書き
- **修正内容**: `@RequestParam` または検索 DTO に `@Pattern(regexp = "^\\d{4}/\\d{2}(/(末|\\d{2}))?$")` を追加
- **想定影響範囲**: 1 Controller method
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: SF-01 の DDL `CHECK` 制約と整合
- **担当推奨**: SF-16 と同一サブエージェント

### SF-18: 検索系メソッドに `@Transactional(readOnly = true)` 追加
- **元レビュー**: design-review m-7 / code-review m-N4
- **対象ファイル**: `TInvoiceService.java`, `MPartnerGroupService.java`, `InvoiceImportService.java`
- **修正内容**: クラスレベルに `@Transactional(readOnly = true)` を付与し、書込みメソッドは `@Transactional` で override
- **想定影響範囲**: 3 Service
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: なし
- **担当推奨**: SF-16 と同一サブエージェント

### SF-19: `MPartnerGroup` `EAGER` → `LAZY` + `JOIN FETCH` (N+1 解消)
- **元レビュー**: design-review M-1
- **対象ファイル**: `MPartnerGroup.java:31-38` + `MPartnerGroupRepository.java` + `MPartnerGroupService.java:19-23`
- **修正内容**: `@ElementCollection(fetch = FetchType.LAZY)` に変更、`@Query("SELECT g FROM MPartnerGroup g LEFT JOIN FETCH g.partnerCodes WHERE g.shopNo = :shopNo ORDER BY g.groupName")` で 1 クエリ化
- **想定影響範囲**: 1 Entity + 1 Repository + 1 Service
- **テスト確認**: `./gradlew compileJava` + partner-group 一覧画面で SQL 数が 1 つに収束することを確認 (Hibernate logger)
- **依存関係**: なし
- **担当推奨**: 1 サブエージェント (グループ C "性能")

### SF-20: `MPartnerGroupService.findByShopNo` で `del_flg='0'` 絞り込み (削除整合性 part)
- **元レビュー**: design-review M-2 (部分) / m-2 (部分)
- **対象ファイル**: `MPartnerGroupRepository.java` + `MPartnerGroupService.java:19-23`
- **修正内容**: SF-01 で `del_flg` カラムを追加した前提で、`findByShopNoAndDelFlgOrderByGroupNameAsc(shopNo, "0")` に置換。物理削除を論理削除化 (`delete()` を `setDelFlg("1")` + save に変更) は M-2 を SAFE 側で実現
- **想定影響範囲**: 1 Repository + 1 Service
- **テスト確認**: `./gradlew compileJava` + 削除した group が一覧に出ないこと
- **依存関係**: SF-01 (`del_flg` カラム生成)
- **担当推奨**: SF-04 と同一サブエージェント

### SF-21: `findQuarterlyInvoice` のクエリ集約 (`IN`)
- **元レビュー**: code-review m-N5
- **対象ファイル**: `InvoiceVerifier.java:271-286` + `TInvoiceRepository.java`
- **修正内容**: `findByShopNoAndPartnerCodeAndClosingDateIn(shopNo, partnerCode, closingDates)` を Repository に追加。Service 側で 1 クエリに集約
- **想定影響範囲**: 1 Repository + 1 Verifier
- **テスト確認**: `./gradlew compileJava` + Hibernate ログで 1 SQL になることを確認
- **依存関係**: なし
- **担当推奨**: SF-19 と同一サブエージェント

### SF-22: フロント `PartnerGroupRequest` 型を切り出し
- **元レビュー**: code-review m-N7
- **対象ファイル** (新規): `frontend/types/partner-group.ts` + `PartnerGroupDialog.tsx:53-68`
- **修正内容**: `interface PartnerGroupRequest { groupName: string; shopNo: number; partnerCodes: string[] }` を新設し、`saveMutation` の引数型を厳密化
- **想定影響範囲**: 1 新規型 + 1 Dialog
- **テスト確認**: `npx tsc --noEmit`
- **依存関係**: なし
- **担当推奨**: SF-05 と同一サブエージェント (グループ E)

### SF-23: `InvoiceImportDialog` のエラートーストを業務向けメッセージに lookup 化
- **元レビュー**: code-review m-N6
- **対象ファイル**: `InvoiceImportDialog.tsx:43-45`
- **修正内容**: サーバ `message` をキーに業務向け補足文を表示 (例: "Row2 の A 列が空です" → "Excel の 2 行目に締日が記載されているか確認してください")
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `npx tsc --noEmit`
- **依存関係**: SF-13 (errors 集約) と相性良し
- **担当推奨**: SF-22 と同一サブエージェント (グループ E)

### SF-24: `InvoiceResponse#closingDate` の Javadoc に format 明記
- **元レビュー**: design-review m-6
- **対象ファイル**: `backend/src/main/java/jp/co/oda32/dto/finance/InvoiceResponse.java:15`
- **修正内容**: Javadoc に「YYYY/MM/末 or YYYY/MM/DD のフォーマット文字列。LocalDate 化は後続フェーズ」と明記
- **想定影響範囲**: 1 ファイル
- **テスト確認**: `./gradlew compileJava`
- **依存関係**: なし
- **担当推奨**: SF-16 と同一サブエージェント

---

## DESIGN-DECISION (要ユーザー判断)

### DD-01: `t_invoice` 責務分離 (取込スナップショット / 入金台帳 / 検証正解値) (Codex 1)
- **元レビュー**: codex-adversarial 1
- **論点**: 1 行に「Excel 由来金額」「経理が後付けする paymentDate」「MF 出力可否判定の正解値」を持たせており監査時に出所説明不能
- **選択肢**:
  - A: 完全分離 — `t_invoice_source` (原本) / `t_invoice_payment_status` (運用) / `t_invoice_verification` (検証結果)
  - B: 軽量化 — `t_invoice` に `import_run_id` / `source_hash` / `confirmed_status` を追加するのみ (Codex 妥協案)
  - C: 現状維持
- **影響範囲**: A は大規模スキーマ改造 + 全 Service refactor / B は V032 migration + Service 軽改修
- **推奨**: **B (Codex 妥協案)** を Phase 1、A は中期 backlog (DEF-04 と統合)

### DD-02: Excel 直 UPSERT vs ステージング 2 段階方式 (Codex 2)
- **元レビュー**: codex-adversarial 2
- **論点**: Excel は人手介在の中間成果物で原本性なし。ファイルハッシュ・取込者・取込時刻・行番号未保存
- **選択肢**:
  - A: `stg_invoice_import_run` + `stg_invoice_import_line` を新設し、本テーブル反映前に検証/承認ステップ
  - B: SMILE 側 DB/API 連携可否を先に評価 (Excel は暫定 IF 扱い、設計書に明記)
  - C: 現状維持 + import_run_id だけ持たせる (DD-01 B と統合)
- **影響範囲**: A は大規模 / B は調査タスク / C は軽量
- **推奨**: **C (DD-01 B と統合)** を Phase 1、A は中期、B は並行調査タスク

### DD-03: 業務請求書キーの導入 (Codex 3)
- **元レビュー**: codex-adversarial 3
- **論点**: `partner_code + closing_date + shop_no` は集計キーであり、中間請求 / 再発行 / 訂正請求 / 部門別請求を表現不能
- **選択肢**:
  - A: SMILE 請求番号 / 請求区分 / 訂正区分 / 発行回数 / 部門コード を Excel から取り込めるか SMILE と協議
  - B: 機能名を「請求書管理」→「得意先別請求集計管理」に改名 (Codex 妥協案)
  - C: 現状維持
- **影響範囲**: A は SMILE Excel フォーマット拡張依頼 / B は doc + UI ラベルのみ
- **推奨**: **B (短期) + A の調査開始 (中期)**。B は SAFE-FIX 寄りだが業務インパクトのため DESIGN-DECISION に分類

### DD-04: `m_partner_group` の履歴モデル (Codex 4)
- **元レビュー**: codex-adversarial 4 / design-review M-2
- **論点**: 過去月を開いても現在グループ定義で集計され、当時の所属関係が再現できない。物理削除で過去の一括処理単位も消失
- **選択肢**:
  - A: `m_partner_group_member_history` に `effective_from` / `effective_to` を持たせ、請求締日基準で所属解決
  - B: 削除を論理削除化 (SF-20 で解決済) + 一括入金時に `partner_group_snapshot_at_apply` を残すのみ (Codex 妥協案)
  - C: 現状維持
- **影響範囲**: A は schema + 全集計ロジック改修 / B は SF-20 + 1 列追加で軽量
- **推奨**: **B (Phase 1)**、A は監査要件発生時に再評価

### DD-05: 入金日に「確定」「締め」「取消」の業務状態を導入 (Codex 5)
- **元レビュー**: codex-adversarial 5
- **論点**: 確定済月、MF 出力済、監査対象期間の保護がなく、過去月への一括反映で閉じた実績を破壊できる
- **選択肢**:
  - A: `payment_status` / `locked_at` / `locked_by` / `confirmed_at` / `confirmed_by` / `unlock_reason` をフルセット導入
  - B: `locked_at` のみ導入し「locked 行は更新不可」のみ強制 (Codex 妥協案)
  - C: 現状維持 (運用慣行で対処)
- **影響範囲**: A は schema + Service + UI / B は schema + Service ガード
- **推奨**: **B (Phase 1)**、A は軸 F (audit trail) と統合

### DD-06: SMILE 修正後の再取込ポリシー (Codex 6)
- **元レビュー**: codex-adversarial 6
- **論点**: 過去月の再取込で金額列のみ上書き、`payment_date` は無条件に旧値が引き継がれる。修正前後の差分・承認証跡なし
- **選択肢**:
  - A: 再取込時に差分プレビュー (金額変更 / 名称変更 / 消滅 / 新規) を表示し、金額変更行は `payment_date` を null リセット + 再確認待ち
  - B: 設計書 §5.1 に「再取込は同 closingDate を完全置換」「payment_date は手動再付与」と明記し、UI は警告 dialog のみ
  - C: 現状維持
- **影響範囲**: A は preview UI + Service / B は doc + 1 警告 dialog
- **推奨**: **B (Phase 1)**、A は DD-05 と統合して Phase 2

### DD-07: `InvoiceVerifier` の MF 出力可否判定を別承認ステップに分離 (Codex 7)
- **元レビュー**: codex-adversarial 7
- **論点**: `InvoiceVerifier` が金額照合 + `mfExportEnabled` + 按分まで実施。Excel 取込値が MF 連携の実質的権威に
- **選択肢**:
  - A: Verifier は判定結果記録のみ。MF 出力可否は人手承認 (`/finance/invoices/approve-mf-export`) で別途
  - B: 権威階層 (請求書原本 > SMILE 請求実績 > 売掛集計 > MF) を design doc に明文化のみ (Codex 妥協案)
  - C: 現状維持
- **影響範囲**: A は新 endpoint + UI / B は doc のみ
- **推奨**: **B (Phase 1)** + Cluster D の DD-02 (権威階層 doc) と統合

### DD-08: 特殊得意先の `m_invoice_rule` マスタ化 + 有効期間 (Codex 8 / 設計 M-4)
- **元レビュー**: codex-adversarial 8 / design-review M-4
- **論点**: `000231` / `301491` / `999999` の定数 + 四半期月 `2/5/8/11` がハードコード。得意先統合・部署移管・締日変更に追従不能
- **選択肢**:
  - A: `m_invoice_rule (partner_code, shop_no_override, billing_cycle, closing_policy, effective_from, effective_to)` を新設 + Verifier から定数排除
  - B: `application.yml` の `batch.invoice.*` に逃がすだけの最小スコープ (Opus M-4 の最小案)
  - C: 現状維持
- **影響範囲**: A は migration + マスタ画面 + Verifier 大改修 / B は config + Verifier 小改修
- **推奨**: **B (Phase 1, SAFE 化可能)** → **A (Phase 2, 業務側マスタ運用が固まったら)**

### DD-09: virtual `TInvoice` を `t_invoice_reconciliation_group` に永続化 (Codex 9 / 設計 M-5)
- **元レビュー**: codex-adversarial 9 / design-review M-5
- **論点**: `findQuarterlyInvoice` の virtual `TInvoice` で `invoiceId` (最古) と `closingDate` (最新) が異レコード由来 → 監査時に「請求書 ID から該当請求書を引いてきても締日が一致しない」状態
- **選択肢**:
  - A: `t_invoice_reconciliation_group` を新設し合算理由 + 構成元 `invoice_id` リストを永続化。UI で「合算」と表示
  - B: virtual のまま `summary.invoiceNo` に `is_quarterly=true` メタ列を追加し UI 側で「合算」と表示 (Opus M-5 の妥協案)
  - C: 現状維持
- **影響範囲**: A は migration + Verifier + UI / B は 1 列 + UI ラベル
- **推奨**: **B (Phase 1)** → **A (Phase 2, DD-08 A と同タイミング)**

### DD-10: 請求機能の認可方針 (admin / shop user の操作差) (設計 D-2)
- **元レビュー**: design-review D-2 / m-3
- **論点**: 全 endpoint `@PreAuthorize("isAuthenticated()")` のみ。admin と shop ユーザーで操作可能範囲が定義されていない
- **選択肢**:
  - A: 設計書 §4 に「admin: 全 shop / shop user: 自 shop のみ」のロール権限表を追加。SF-02 / SF-03 で実装済の境界を明記
  - B: ロール別 endpoint 分離 (`/finance/invoices/admin/...` 等)
  - C: 現状維持 (SF-02 / SF-03 の shopNo フィルタのみ)
- **影響範囲**: A は doc のみ / B は API 全面再設計
- **推奨**: **A (Phase 1)**

### DD-11: ファイル名による shop_no 推定 → 必須 UI セレクト化 (設計 m-3)
- **元レビュー**: design-review m-3
- **論点**: ファイル名に「松山」が含まれるかで shop_no を決定。命名規約変更で誤判定
- **選択肢**:
  - A: UI に「事業部選択」プルダウン (第1 / 第2) を必須項目化、ファイル名判定はヒントのみ
  - B: 現状維持 + 警告 dialog 「ファイル名に「松山」を含まない場合は第1事業部として取込みます」表示
- **影響範囲**: A は 1 Dialog 改修 / B は 1 警告 dialog
- **推奨**: **A** (SAFE-FIX 寄りだが業務 UX 確認のため DESIGN-DECISION 分類)

### DD-12: 入金日変更履歴テーブル (`t_invoice_payment_date_history`) (設計 M-3)
- **元レビュー**: design-review M-3
- **論点**: 入金日 (経理処理の最重要属性) の変更履歴 (誰が・いつ・どの値から) なし
- **選択肢**:
  - A: 子テーブル `t_invoice_payment_date_history` (invoice_id, old_payment_date, new_payment_date, changed_at, changed_by_user_no) 新設 + Controller から INSERT
  - B: 軸 F 監査証跡基盤 (買掛側と同一) に統合し別 Sprint で実装
  - C: 現状維持
- **影響範囲**: A は 1 migration + 2 Controller method 改修 / B は別 Sprint
- **推奨**: **B (軸 F と統合)**。MEMORY.md の "Next Session Pickup 軸 F" に「請求書側も対象」と追記

### DD-13: `closingDate` の `LocalDate + is_month_end:boolean` 化 (設計 M-7)
- **元レビュー**: design-review M-7
- **論点**: 「末」漢字リテラル + `YYYY/MM/DD` 混在で範囲検索 / 比較演算が事実上不可能
- **選択肢**:
  - A: 中期 — `closing_date_value LocalDate` + `is_month_end boolean` に分割し文字列解釈ロジック排除
  - B: 短期 (互換) — DB 制約 `CHECK (closing_date ~ '^\\d{4}/\\d{2}/(末|\\d{2})$')` のみ追加 (SF-01 で実施済)
- **影響範囲**: A は migration + 全 Service / Verifier 改修 / B は SF-01 内
- **推奨**: **B 完了 → A は中期 backlog (DEF-05)**

### DD-14: 旧 stock-app の virtual `TInvoice` 廃止判断
- **元レビュー**: 設計書 §8 T5 + Codex 9
- **論点**: DD-09 と関連。virtual の限界が監査時に露呈する前に廃止判断必要
- **選択肢**: DD-09 と統合
- **推奨**: **DD-09 に集約**

---

## DEFER (将来課題)

### DEF-01: パーサー / 検証 / 永続化の責務分離 (Codex 10)
- **元レビュー**: codex-adversarial 10
- **理由**: `InvoiceImportService` を `InvoiceExcelParser` / `InvoiceImportValidator` / `InvoiceImportPlanner` / `InvoiceImportApplier` に分離する大規模 refactor。SF-13 (errors 集約) で部分的に「parser → applier」の境界が見えるようになる。本格実装は Phase 2 として別 Sprint

### DEF-02: PII / 取引先情報の取扱方針明文化 (Codex 11)
- **元レビュー**: codex-adversarial 11
- **理由**: 閲覧目的・保持期間・削除/マスキング・アクセスログの全社方針が必要。請求機能単独で決められず

### DEF-03: 監査証跡基盤 (軸 F) との統合 (DD-12 実装側)
- **元レビュー**: design-review M-3 / Codex 5
- **理由**: 既に `claudedocs/design-audit-trail-accounts-payable.md` ドラフト済。買掛側と同一基盤で構築する方針のため別 Sprint

### DEF-04: `t_invoice` 責務完全分離 (DD-01 A 案実装)
- **元レビュー**: codex-adversarial 1
- **理由**: 中期スキーマ改造。DD-01 B で凌いだ後に再評価

### DEF-05: `closingDate` 中期型化 (DD-13 A 案実装)
- **元レビュー**: design-review M-7
- **理由**: schema 大改造。短期 CHECK 制約で凌げる

### DEF-06: ステージング 2 段階方式 (DD-02 A 案実装) + SMILE DB/API 連携可否調査
- **元レビュー**: codex-adversarial 2
- **理由**: SMILE 側調査が必要。DD-01 B で `import_run_id` を持たせれば暫定的に追跡可

### DEF-07: 業務請求書キー (DD-03 A 案実装) — SMILE フォーマット拡張依頼
- **元レビュー**: codex-adversarial 3
- **理由**: SMILE 側出力フォーマット拡張が必要。短期は DD-03 B で機能名改名

---

## ALREADY-RESOLVED

### AR-01: 設計レビュー チェックリスト「DTO 変換 (`from(entity)` factory)」
- **解消経緯**: `InvoiceResponse.from`, `PartnerGroupResponse.from` で実装済。本 triage で取り扱う必要なし

---

## 適用順序提案

SAFE-FIX を以下の順序で適用すると依存関係が綺麗:

1. **SF-01** (V031 migration) — 後続全ての schema 依存タスクの前提、最優先
2. **SF-02 / SF-03 / SF-06** (Critical IDOR / 入金日クリア) — Block 解除
3. **SF-04 / SF-20** (partner-group 正規化 + 論理削除) — SF-01 後
4. **SF-05** (フロント stale closure) — Block 解除、独立
5. **SF-07 / SF-13 / SF-14 / SF-15** (取込整合性 + errors 集約) — グループ内で順序あり
6. **SF-08 / SF-09 / SF-10 / SF-11 / SF-12 / SF-16 / SF-17 / SF-18 / SF-24** (コード品質) — 並列可
7. **SF-19 / SF-21** (性能) — 独立
8. **SF-22 / SF-23** (フロント追加) — 独立

## 並列実行プラン

| グループ | 担当タスク | サブエージェント | 依存 |
|---|---|---|---|
| **A: Block 解除 (Critical)** | SF-01 / SF-02 / SF-03 / SF-06 / SF-07 / SF-13 / SF-14 / SF-15 | 1 | SF-01 → 残り並列、SF-13 系は順序内あり |
| **B: コード品質** | SF-08 / SF-09 / SF-10 / SF-11 / SF-12 / SF-16 / SF-17 / SF-18 / SF-24 | 1 | なし |
| **C: 性能** | SF-19 / SF-21 | 1 | なし |
| **D: partner-group 整理** | SF-04 / SF-20 | 1 | SF-01 後 |
| **E: フロント** | SF-05 / SF-22 / SF-23 | 1 | なし |

並列グループ数: 5

## Block 判定解除に必要な Critical SAFE-FIX

| ID | 内容 | 出典 |
|---|---|---|
| SF-01 | V031 migration (DDL 欠落) | design C-1 |
| SF-02 | 取込 endpoint shopNo IDOR | code C-N3 |
| SF-03 | bulk-payment-date / payment-date / partner-groups IDOR | design C-2 |
| SF-04 | partner-group 正規化 + dedup | code M-N2 |
| SF-05 | useMemo stale closure (UI 機能停止) | code C-N1 |
| SF-06 | 入金日クリア時の `@NotNull` 撤去 (UI 操作不能) | code C-N2 |
| SF-07 | Excel 内重複 partnerCode dedup (データ整合) | code C-N4 |

**計 7 件** (Critical 4 系 = code C-N1〜C-N4 / 設計 C-1, C-2, C-3 のうち SF-13 で部分対応)。
**設計 C-3 (Excel 削除行の扱い)** の業務判断部分は DD-06 (SMILE 修正後の再取込ポリシー) に統合し DESIGN-DECISION 扱い。

## Migration 新規追加

✅ **必要**: `V031__create_t_invoice_and_m_partner_group.sql` (SF-01)
- `t_invoice` (`UNIQUE(partner_code, closing_date, shop_no)` + `CHECK closing_date format` + `shop_no NOT NULL`)
- `m_partner_group` (`del_flg` 含む、SF-20 の論理削除化用)
- `m_partner_group_member` (`UNIQUE(partner_group_id, partner_code)` で SF-04 の Service 側 dedup と一致)
- prod baseline は既存スキーマと衝突するため `flyway.baseline-version` 引き上げの運用手順を README + コメントに明記

## 推定総工数

| グループ | 推定 | 内訳 |
|---|---|---|
| A | 6 時間 | SF-01 (1.5h) + SF-02/SF-03 (1.5h) + SF-06 (30min) + SF-07 (1h) + SF-13/SF-14/SF-15 (1.5h) |
| B | 3 時間 | 9 タスク × 平均 20 分 |
| C | 1.5 時間 | SF-19 (1h) + SF-21 (30min) |
| D | 1 時間 | SF-04 (40min) + SF-20 (20min) |
| E | 2.5 時間 | SF-05 (1.5h) + SF-22/SF-23 (1h) |

**並列実行時の wallclock**: 最遅グループ A の 6 時間
**直列実行時の累積**: 14 時間 (1 サブエージェント逐次)

DESIGN-DECISION 14 件 + DEFER 7 件は別途ユーザー判断・別 Sprint で消化。

## 出力ファイルパス

`C:\project\odamitsu-data-hub\claudedocs\triage-invoice-management.md`
