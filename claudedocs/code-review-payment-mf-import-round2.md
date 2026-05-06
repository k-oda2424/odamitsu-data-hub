# 再レビュー: 買掛仕入 MF 変換 (Cluster C) 修正後

レビュー日: 2026-05-04
対象: triage SF-C01〜SF-C21 適用後 (backend 14 + frontend/docs 7)
ブランチ: refactor/code-review-fixes
レビュアー: Opus サブエージェント (round 2)

## サマリー
- 新規発見: **Critical 0 / Major 2 / Minor 4**
- 既存修正の評価: **概ね適切**。重大な silent break・データ整合性破壊なし。
- SF-C13 (Timestamp 型変更) は IEntity 規約上強制で、JSON シリアル化への波及なし (DTO 露出なし)。`getShopNo()=null` も CustomService.validateUpdateByShop で無害化されているが、**そもそも MPaymentMfRule は CustomService 系を経由しない** ためルート不到達 (zero-impact)。
- SF-C03 (CSV 取引日列の `transactionMonth` 統一) は `convert()` / `exportVerifiedCsv()` 両経路で締め日に収束。ゴールデンマスタ (convert 経由) は元々 transferDate=transactionMonth の運用なので無影響、`exportVerifiedCsv` 経由は後述 Major-2 で要追補。
- SF-C05 (`IllegalStateException` 一括汎用化) は **設計通りだが UX 退行を伴う** (既知トレードオフ、Major-1)。

判定: **ほぼクリア** (Major 2 件は次回機能改善ループで吸収可能、緊急対応不要)

---

## Critical 指摘
**なし。**

---

## Major 指摘

### M1: SF-C05 による `IllegalStateException` 汎用化で `exportVerifiedCsv` の操作員向けメッセージが消失
- **対象**:
  - `backend/src/main/java/jp/co/oda32/api/finance/FinanceExceptionHandler.java:52-57`
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java:484` ("対象取引月(...) に出力対象データがありません")
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java:579` ("CSV 出力可能な行がありません ... ルール未登録のみ・補助行なし")
- **問題**: `FinanceExceptionHandler#handleIllegalState` は MA-01 セキュリティ配慮で **メッセージを一律 "内部エラーが発生しました" に置換** する (L56)。`exportVerifiedCsv` の上記 2 メッセージは元々 **金額や行情報を含まないユーザーフレンドリな業務メッセージ** であり、汎用化メリット (情報漏えい防止) より UX 損失 (操作員が "内部エラー=システム障害" と誤認、サポート問い合わせ増) のほうが大きい。`convert()` L163 ("未登録の送り先があります") は preview ボタン disabled でルート不到達なので影響軽微だが、`exportVerifiedCsv` のほうは `buildVerifiedExportPreview` で 0 件確認できるとはいえ race / preview スキップ操作で容易に到達する。
- **推奨**: `IllegalArgumentException` (400) に格上げするか、**ビジネス例外専用クラス** (例: `FinancePreconditionException`) を導入し `FinanceExceptionHandler` で `ex.getMessage()` をそのまま返却する分岐を作る。最小修正は L484/L579 を `IllegalArgumentException` に変えるだけで `400 + 元メッセージ` に戻る (Controller L151-152 の `IllegalArgumentException` 分岐がカバー済)。
- **重大度**: Major (運用フィードバック損失)

### M2: SF-C03 検証 (`exportVerifiedCsv` 取引日列) のゴールデンマスタテストが存在しない
- **対象**:
  - `backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfImportServiceGoldenMasterTest.java:80` (convert 経由のみ)
  - `backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfImportServiceAuxRowTest.java:142` (applyVerification 経由のみ)
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java:528, 559` (`transactionDate(transactionMonth)`)
- **問題**: SF-C03 で **ロジックが変わったのは `exportVerifiedCsv` の transactionDate 設定のみ** (PAYABLE 行 L528 + aux 行 L559)。両テストは `convert()` / `applyVerification()` を叩くが `exportVerifiedCsv()` は **直接呼んでいない**。triage 報告の「ゴールデンマスタは convert 経由なので影響なし」は正しいが、その裏返しとして **SF-C03 を回帰検出する自動テストが現在 0 件**。次回 `exportVerifiedCsv` 改修時に取引日列の sf-c03 仕様が静かに壊れるリスクあり。
- **推奨**: `PaymentMfImportServiceAuxRowTest` か新規 `PaymentMfImportServiceVerifiedExportTest` に **`exportVerifiedCsv` の CSV bytes を assert する 1 ケース** を追加。`assertThat(new String(csv, "MS932")).contains("," + transactionMonth.format(yyyy/M/d) + ",")` レベルで十分。
- **重大度**: Major (回帰検出ギャップ)

---

## Minor 指摘

### m1: SF-C12 `safeToLong` の `d > Long.MAX_VALUE` 比較は double 精度の都合で誤解を招く
- **対象**: `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfCellReader.java:89-93`
- **問題**: `Long.MAX_VALUE` (9223372036854775807) は double に正確表現できず、double 化すると `9.223372036854776E18` (= 次の表現可能 double) に丸まる。よって `d > Long.MAX_VALUE` は **Long.MAX_VALUE 直上 1024 程度の double 値を取りこぼす**。実害は `Math.round(double)` 自体が saturating なので最終的に `Long.MAX_VALUE` を返し overflow にはならないが、コード意図と挙動が乖離。
- **推奨**: コメント追加か `if (d >= 0x1.0p63)` (= 2^63) 比較に統一。今は害なしのため Minor。

### m2: SF-C13 で `MPaymentMfRule.addDateTime/modifyDateTime` を `Timestamp` に変更したが、姉妹 Entity は `LocalDateTime` のまま
- **対象**:
  - `backend/src/main/java/jp/co/oda32/domain/model/finance/MPaymentMfRule.java:74, 80` (Timestamp)
  - `backend/src/main/java/jp/co/oda32/domain/model/finance/MMfClientMapping.java:34, 40` (LocalDateTime のまま)
  - `backend/src/main/java/jp/co/oda32/domain/model/finance/MMfJournalRule.java:81, 87` (LocalDateTime のまま)
- **問題**: SF-C13 は `IEntity.setAddDateTime(Timestamp)` 規約により強制 (`backend/.../IEntity.java:8`)。よって `MMfClientMapping` / `MMfJournalRule` も `IEntity` 化する場合は同じ強制が掛かる。Cluster C 範囲外だが、MF rule マスタ群で型が割れている状態は将来の混乱要因。Hibernate は両者を `timestamp without time zone` にマッピングするので DB 互換は保たれる。
- **推奨**: 別タスクで MF rule 系 3 Entity を `IEntity` 統一 or 全て `LocalDateTime` 統一する方針決定 (今回ループ範囲外)。

### m3: SF-C13 `getShopNo()=null` は CustomService 経路では安全だが、IEntity 規約とのギャップ
- **対象**: `backend/src/main/java/jp/co/oda32/domain/model/finance/MPaymentMfRule.java:90-92`
- **検証結果**: `CustomService#validateUpdateByShop` (`backend/.../CustomService.java:78-84`) は `entityShopNo == null` で **早期 return するため安全**。ただし MPaymentMfRule は `PaymentMfRuleService` 経由で `repository.save()` を直接呼んでおり (`PaymentMfRuleService.java:67, 90, 100`) **CustomService 経路は使っていない**。よって `getShopNo()=null` の挙動は **机上の安全性のみで実運用無影響**。
- **推奨**: なし。Javadoc (L86-88) の説明が十分明瞭で許容範囲。

### m4: SF-C20 ファイル名統一で履歴ダウンロードだけ "payment_mf.csv" 固定の非統一
- **対象**: `backend/src/main/java/jp/co/oda32/api/finance/PaymentMfImportController.java:206-217` (`historyCsv` メソッド)
- **問題**: SF-C20 で `convert` / `exportVerified` は `payment_mf_${yyyymmdd}.csv` に統一されたが、`historyCsv` (履歴一覧からの再ダウンロード) は ASCII filename が **依然 `payment_mf.csv` のまま**。日本語側は `買掛仕入MFインポートファイル.csv` (yyyymmdd 無し)。フロントエンド (`payment-mf-history.tsx`) で `filename*=UTF-8''` 抽出は機能するため日本語名が優先されるが、UTF-8 を読まないクライアント (古い curl 等) で名前衝突が発生する。
- **推奨**: 履歴の `transferDate` カラムから yyyymmdd を組み立て、`payment_mf_${yyyymmdd}.csv` に揃える。1 行修正。

---

## SF-CXX 別の確認結果

| SF-CXX | 適切に適用されたか | 備考 |
|---|---|---|
| SF-C01 (PreAuthorize 付与) | ✅ | `convert` L67 / `verify` L94 で `@PreAuthorize("hasRole('ADMIN')")` 確認 |
| SF-C02 (fmtAmount null 時末尾スペース) | ✅ | `PaymentMfCsvWriter.java:96-99` で null→0+" " 確認 |
| SF-C03 (`exportVerifiedCsv` 取引日 = transactionMonth) | ✅ | L528, L559 `transactionDate(transactionMonth)`。**Major-2 で回帰テスト追加推奨** |
| SF-C04 (設計書 §5.1 整合) | (docs) | docs 確認は今回スコープ外、未開封 |
| SF-C05 (Controller try/catch 削除) | ⚠️ | `convert` L86-90 / `exportVerified` L151-155 OK。**Major-1: 操作員メッセージ消失** |
| SF-C06 (history shop_no 定数化) | ✅ | L200 `FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO` |
| SF-C07 (DIRECT_PURCHASE 動的降格 継承) | ✅ | `PaymentMfImportService.java:803-818` で `summaryTemplate ?? "{source_name}"` + `tag(rule.getTag())` + `creditDepartment(rule.getCreditDepartment())` 確認 |
| SF-C08 (deriveTransactionMonth static) | ✅ | L1021 `static LocalDate deriveTransactionMonth` |
| SF-C09 (Pattern 切り出し) | ✅ | `PaymentMfRuleService.java:213-221` で 4 つの `static final Pattern`。正規表現は元と同一 (`\\[[^\\]]*\\]` / `株式会社\|...` / `[松竹梅手]\\s*$` / `[\\s\\u3000,.\\-・。、]`) |
| SF-C10 (cleanExpired ログ) | ✅ | L1124-1133 で削除件数ログ |
| SF-C11 (backfill dryRun 分割 + @Lazy self) | ✅ | `PaymentMfRuleService.java:38-40, 111-125`。dryRun=true → `backfillDryRun(@Transactional(readOnly=true))`、false → `backfillApply(@Transactional)`。AOP プロキシ経由で発火する self.backfillDryRun/Apply 経路 |
| SF-C12 (readLongCell ガード) | ✅ | `PaymentMfCellReader.java:65-93`。**Minor-1 でコメント改善余地** |
| SF-C13 (`MPaymentMfRule` IEntity + Timestamp) | ✅ | `MPaymentMfRule.java:18, 74, 80, 90-92`。`getShopNo()=null` は CustomService 経路安全 (Minor-3 参照)。Service 4 箇所 (L64, L88, L98, L185) で `new Timestamp(System.currentTimeMillis())` 確認 |
| SF-C14 (aux 物理削除方針 docs) | (docs) | スコープ外 |
| SF-C15 (設計書 N+1 docs) | (docs) | スコープ外 |
| SF-C16 (advisory lock docs) | (docs) | スコープ外 |
| SF-C17 (rules フィルタ正規化) | (frontend) | 未開封 (本ループは backend 中心) |
| SF-C18 (confirmVerify 二重起動防止) | ✅ | `payment-mf-import.tsx:404-415` で `setConfirmVerify(false)` 即時 + `!verifyMut.isPending` ガード |
| SF-C19 (useEffect 依存 length) | ✅ | `PaymentMfAuxRowsTable.tsx:51-54` で `[query.data?.length]`。length 変化のみ通知する設計意図と一致 |
| SF-C20 (ファイル名統一) | ⚠️ | `convert` L73-84 / `exportVerified` L127-143 で `payment_mf_${yyyymmdd}.csv` 統一。`api-client.ts:159` の `filename*=UTF-8''([^;]+)` 正規表現で日本語名抽出 OK。**Minor-4: `historyCsv` のみ固定名のまま** |
| SF-C21 (advisory lock 自動解放 docs) | (docs) | スコープ外 |

---

## 推奨アクション

優先度高 (次ループ着手):
1. **M1 解消**: `PaymentMfImportService:484, 579` を `IllegalArgumentException` に変更 (1 行修正、Controller 既存分岐が拾う)。または `FinancePreconditionException` 新設で API 仕様に意図を込める。
2. **M2 解消**: `PaymentMfImportServiceVerifiedExportTest` 新設、`exportVerifiedCsv` の CSV bytes に対して取引日列 = `transactionMonth.format(yyyy/M/d)` を assert する最小ケースを追加。

優先度中:
3. **m4 解消**: `historyCsv` (Controller L206-217) のファイル名を `transferDate` ベース yyyymmdd に揃える。

優先度低 (任意):
4. **m1**: `safeToLong` の比較 + コメント補正。
5. **m2**: MF rule 系 3 Entity の `IEntity` 統一方針を別タスクで決定。

実装スコープ外:
- 設計書 docs (SF-C04, C14, C15, C16, C21) は本ループ未開封。docs round で確認推奨。
- frontend rules フィルタ (SF-C17) は別レビューループで確認推奨。

---

## レビュー総括

Cluster C round 2 の SAFE-FIX は全 21 件とも **設計意図通り適用** されており、ゴールデンマスタが PASS している事実とも整合する。新規バグ・データ整合性破壊は確認できず、CSV フォーマット・ファイル名の silent break もない。

ただし SF-C05 の汎用例外化はセキュリティと UX の trade-off が若干 UX 不利に倒れており (Major-1)、SF-C03 の回帰テストが空白 (Major-2) の 2 点は次回ループでの追補が望ましい。これら 2 件は **緊急修正不要、機能改善ループ** で吸収可能なレベル。

判定: **ほぼクリア (continue without urgent fix)**
