# 再レビュー: 売掛金 (Cluster E) SF-E01〜SF-E21 適用後

レビュー日: 2026-05-04
対象: triage SF-E01〜SF-E21 適用後 (branch `refactor/code-review-fixes`、SF-E13 業務確認待ち除外)
レビュアー: Opus サブエージェント (round 2)
スコープ: SF-E パッチ群により導入されたバグ・整合性破壊の検証

## サマリー
- 新規発見: **Critical 0 / Major 1 / Minor 6**
- 既存修正の評価: **おおむね適切**。SF-E03 / SF-E05 / SF-E10 / SF-E11 / SF-E18 / SF-E20 は意図通り。
- 結論: **ほぼクリア**。Critical 級リグレッションなし。Major 1 件 (MA-E01) は SF-E15 副作用 + 既存 export-mf-csv の Content-Type 文字列ドリフト。次ループ判断は実運用影響次第で「不要〜軽微改修」レベル。

---

## SF-E03 (snake_case 修正): 動作影響評価

**結論: 完全に動作変化なし、リスクゼロ。**

`TAccountsReceivableSummaryPK` は `@IdClass(TAccountsReceivableSummaryPK.class)` で参照されており、**`@EmbeddedId` ではない**。`@IdClass` 形式では JPA は PK クラスの `@Column` アノテーションを **使用しない** — 実際のカラムマッピングは Entity (`TAccountsReceivableSummary`) 側の `@Id @Column(name = "transaction_month")` (line 38) で完結している (`TAccountsReceivableSummary.java:37-39` で正しい snake_case)。

PK class 側の `@Column` は飾りに近く、JPA spec § 11.1.21 でも `@IdClass` の場合 PK class フィールドは「name + type を Entity と一致させること」しか要求されない。Hibernate は実カラム名を Entity からのみ参照する。

**Specification / JPQL 経路**: `cb.equal(root.get("transactionMonth"), ...)` は entity 属性名 (= フィールド名) を参照するため、PK class の column annotation 値とは無関係。問題なし。

PhysicalNamingStrategy も適用されているはずだが、いずれにせよ entity 側で完全な snake_case が指定されているため上書きされる。

→ **SF-E03 は cosmetic change 100%**、ドキュメンテーション整合性向上のみで実害ゼロ。

---

## SF-E05 (BulkVerifyService 切出し): rollback 範囲評価

**結論: 1 tx 化は意図通り機能。部分コミット破綻は確実に解消。1 点だけ subtle な要注意あり。**

### tx 伝播マトリクス
- `BulkVerifyService.execute` = `@Transactional` (REQUIRED, Service 配下、proxy 経由)
- `cutoffReconciler.reconcile` = `@Transactional` (REQUIRED, 別 Bean なので proxy 経由) → **親 tx に join**
- `summaryService.findByShopAndDateRange` = no @Transactional → 親 tx 配下で SELECT
- `summaryService.saveAll` = `@Transactional` (REQUIRED) → 親 tx に join
- `invoiceVerifier.verify` = no @Transactional, in-place mutate

→ 全工程が親 tx に乗る。途中例外で全部 rollback されることが保証される。 **B-2 (旧 round1 指摘) は完全解消**。

### 微妙な懸念: `deleteAllInBatch` の persistence context との不整合
`AccountsReceivableCutoffReconciler.reconcile` は `summaryRepository.deleteAllInBatch(stale)` (line 182) で旧 AR 行を物理削除し、その直後に `summaryRepository.saveAll(newRows)` で新行を INSERT する。`deleteAllInBatch` は JPQL DELETE を直接発行し、persistence context (1st level cache) を **更新しない**。続く `BulkVerifyService.execute` で `findByShopAndDateRange` を再実行する際、削除済み旧行が PC に残っている可能性がある。

実害は限定的:
- 新規 INSERT 行は新インスタンスなので PC 衝突は起きにくい
- `findByShopAndDateRange` は Repository.findAll(Specification) → Hibernate は autoflush + 新クエリで DB から取り直す
- ただし「DB 上は削除済みだが PC 上は detached 状態の旧 instance」が同 tx 内で `saveAll` に紛れ込むと、不要な INSERT/UPDATE が走る理論上のリスクあり

**実装上は安全側に倒れている** (再ロード結果のみ saveAll 対象) ので Critical/Major ではない。ベストプラクティスとしては reconcile 直後に `entityManager.flush()` + `clear()` を入れる手もあるが、現状運用件数 (画面検索範囲、数百行) では無問題。 → **info レベル**。

### nested Result クラス
`AccountsReceivableBulkVerifyService.Result` は `@Getter` で公開、Controller が `getVerification()` / `getReconcile()` を呼んで API レスポンス組立。フロント API 契約 (`BulkVerifyResponse`) に渡すフィールドはすべて維持。**契約破壊なし**。

### 例外発生時のロールバック検証
1. `findByShopAndDateRange` 例外 → 何も書いていないので影響なし
2. `cutoffReconciler.reconcile` 例外 (内部 deleteAllInBatch 失敗等) → 親 tx rollback で AR 全体が元に戻る
3. `invoiceVerifier.verify` 例外 → in-place mutation は detached entity への書き込み (まだ saveAll してないため DB は無変化、rollback で OK)
4. `summaryService.saveAll` 例外 → reconcile 結果も含めて全 rollback

すべての分岐で整合性破壊なし。**SF-E05 は仕様通り**。

---

## Critical (新規発見、即修正必要)

なし。

---

## Major

### MA-E01: SF-E15 Content-Type 変更で `Shift_JIS` → `windows-31j` 文字列ドリフト
**ファイル**: `backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java:290`

旧コード:
```java
.contentType(MediaType.parseMediaType("text/csv; charset=Shift_JIS"))
```
新コード:
```java
.contentType(MediaType.parseMediaType("text/csv; charset=" + SalesJournalCsvService.CP932.name()))
// → "text/csv; charset=windows-31j"
```

`Charset.forName("windows-31j").name()` は `"windows-31j"` を返す。`Shift_JIS` と `windows-31j` (CP932) は **厳密には別 charset** (CP932 は MS-CP932 拡張で `〜` `①` 等を含む superset)。

#### 影響評価
- **CSV body の実体は SF-E04 適用前から `windows-31j`** (`Charset.forName("windows-31j")` を OutputStreamWriter で使用) — つまり旧 `charset=Shift_JIS` の方が **実体と不一致** だった
- SF-E15 の修正方向は **意味論的に正しい** (Content-Type と body charset を一致させる)
- ただし以下の互換性懸念:
  - **MS Excel for Windows**: 両方とも CP932 codec で開けるため実害なし
  - **Mac Excel / LibreOffice**: charset name から codec を選択する場合、`Shift_JIS` 指定の方が安全な選択になる場合がある (CP932 拡張字を表示できないが、JIS X 0208 範囲は読める)
  - **古いダウンロードマネージャ / メールゲートウェイ**: 未知の charset 名を warning ログに出すケースあり
- 売掛 CSV は MF (マネーフォワード) が直接消費するため、MF 側の codec 自動判定がどう動くかは未検証

#### 推奨
- 案 A (推奨): MF 動作確認後に問題なければ現状維持。設計書に「`charset=windows-31j` (CP932 完全表現) に統一」と明記
- 案 B: 過去互換重視で `Shift_JIS` リテラル指定に戻す (ただし body は依然として CP932 — Content-Type が不正確になる)
- 案 C: `MimeType` を組み立てる際に明示的に `Shift_JIS` ラベルを付与しつつ codec は CP932 (`OutputStreamWriter` のまま)

→ MF 側に CSV を実際に読み込ませて文字化けが無いかの **疎通確認 1 パスを推奨**。文字化けが無ければ案 A で確定。

---

## Minor

### MI-E01: 設計書 §5.5 の Reconciler シグネチャがコードと不一致
**ファイル**: `claudedocs/design-accounts-receivable-mf.md:226`

設計書追記:
```java
public ReconcileResult reconcile(Integer shopNo, LocalDate fromDate, LocalDate toDate);
```
実コード:
```java
public ReconcileResult reconcile(List<TAccountsReceivableSummary> summaries, LocalDate fromDate, LocalDate toDate)
```

shopNo は前段で `BulkVerifyService` が `findByShopAndDateRange(effectiveShopNo, ...)` で抽出した summaries に含まれる形。設計書側の表記を実装に合わせるべき。

→ ドキュメンテーション軽微修正。

---

### MI-E02: 設計書 §9.4 の `verificationNote` field 名が API と不一致
**ファイル**: `claudedocs/design-accounts-receivable-mf.md:540-547`, `backend/.../dto/finance/AccountsReceivableVerifyRequest.java:21`

設計書 §9.4 の Request 例:
```json
{ "verificationNote": "..." }
```
DTO field 名:
```java
private String note;
```

フロント (`accounts-receivable.tsx:143`) も `note` を送信している。 **コードが正、設計書が誤**。SF-E21 の更新範囲外だが、同一セクションの編集ついでに直すべき。

→ 同上。

---

### MI-E03: 設計書 §9.3 Response の reconcile 系フィールドが未記載
**ファイル**: `claudedocs/design-accounts-receivable-mf.md:528-535`

設計書 §9.3 の Response:
```json
{ "matchedCount": 35, "mismatchCount": 3, "notFoundCount": 4, "skippedManualCount": 0 }
```

実 API (`AccountsReceivableBulkVerifyResponse`):
- 上記 4 フィールド + `josamaOverwriteCount`, `quarterlySpecialCount`, `reconciledPartners`, `reconciledDeletedRows`, `reconciledInsertedRows`, `reconciledSkippedManualPartners`, `reconciledDetails`

SF-E21 で 5.5 章を追記したが、9.3 の Response 例は更新されていない。

→ ドキュメント補完。

---

### MI-E04: SF-E11 `findAllByPartnerNos` は soft-delete を無視 (旧 `getByPartnerNo` と同じ挙動)
**ファイル**: `backend/.../domain/service/master/MPartnerService.java:65-70`

```java
public List<MPartner> findAllByPartnerNos(Collection<Integer> partnerNos) {
    if (partnerNos == null || partnerNos.isEmpty()) return Collections.emptyList();
    return partnerRepository.findAllById(partnerNos);
}
```

`findAllById` は JpaRepository の素の find — `del_flg='1'` の論理削除パートナーも含む。ただし旧実装の `getByPartnerNo` (line 50-53) も `findById` 直で同じ挙動だったので、**SF-E11 で behavior は完全保持**。

ただ `MPartnerService.findAll()` (line 39-41) は `defaultFindAll` で del_flg 考慮しているのに、bulk fetch だけ del_flg 無視は一貫性が悪い。一覧画面で論理削除済みパートナーの行が表示される潜在リスクあり (実運用ではほぼ起きないが)。

#### 推奨
新規 Repository メソッド `findByPartnerNoInAndDelFlg(Collection<Integer>, String)` を追加して del_flg='0' フィルタ付きの bulk fetch にする。Service 層で N+1 解消とは独立に soft-delete 整合を改善できる。

→ behavior change なし、設計改善余地のみ。

---

### MI-E05: SF-E10 `CutoffType.fromCode` のテストが存在しない
**ファイル**: `backend/src/main/java/jp/co/oda32/domain/model/finance/CutoffType.java`, テストなし

新設 enum でロジック (null/blank → ALL fallback、未知 code → IAE) があるのに、`backend/src/test/java/jp/co/oda32/domain/model/finance/` 配下に `CutoffTypeTest.java` がない。

#### 推奨
- `fromCode(null)` → ALL
- `fromCode("")` → ALL
- `fromCode("all")` → ALL
- `fromCode("15")` → DAY_15
- `fromCode("invalid")` → IAE with メッセージ確認
の 5 ケース程度の単体テスト追加。

→ 動作問題なし、coverage gap。

---

### MI-E06: `paymentTypeLabel` が買掛側で未利用 (現状仕様通り、将来注意点)
**ファイル**: `frontend/lib/payment-type.ts`, `frontend/components/pages/finance/accounts-payable.tsx`

frontend agent の報告通り、買掛側 `accounts-payable.tsx` には `cutoffDate` 表示列が存在しない (Grep 検証済み: `paymentTypeLabel` の参照は accounts-receivable.tsx のみ)。買掛側に締め日列を追加する PR が来た時に `paymentTypeLabel` を再利用する旨のコメントが既に SF-E18 で書かれているため、将来再発はしにくい。

→ 現状 OK、設計コメントが適切。

---

## SF-E01〜E20 の個別評価

| ID | 内容 | 評価 | 備考 |
|----|------|------|------|
| SF-E01 | 0件 422 + saveAll 一括化 | OK | 422 は `ResponseStatusException` で `GlobalExceptionHandler.handleResponseStatus` が拾い `{ "message": "..." }` を返す。saveAll は @Transactional で 1 tx |
| SF-E02 | markExported null guard 改修 | OK | `*_change != null` の時に必ず `*_amount` 上書き。verifiedManually=true 行も同様だが、その場合 `*_change` はバッチで更新済みなので no-op に近い |
| SF-E03 | snake_case 修正 | OK (cosmetic) | 上記の通り `@IdClass` 形式で実害なし |
| SF-E04 | CP932 定数化 | OK | `SalesJournalCsvService.CP932` を public static final で公開、tasklet/controller 双方が参照 |
| SF-E05 | BulkVerifyService 切出し | OK | 上記 rollback 評価通り |
| SF-E06 | period 逆転 400 | OK | bulkVerify 冒頭で IAE → 400 |
| SF-E07 | (記載なし) | - | triage 元と要照合 |
| SF-E08 | IllegalArgumentException 統一 | OK | GlobalExceptionHandler.handleIllegalArgument で 400 + `{message}` |
| SF-E09 | shopNo null 時 assertShopAccess skip | OK | admin の null=全店舗を許容、非admin は resolveEffectiveShopNo で強制上書き |
| SF-E10 | CutoffType enum 化 | OK | Tasklet の `CUTOFF_TYPE_*` 定数完全削除確認、コメント (line 63-64) で言及 |
| SF-E11 | N+1 解消 | OK | bulk fetch 1 SELECT、Map 化、merge function `(a,b) -> a` で重複時の安全策あり |
| SF-E12 | tasklet 二重例外撤去 | OK | コメントで意図明示 |
| SF-E14 | mfExportEnabled field initializer 削除 | OK | Controller 側 null フォールバック一本化 |
| SF-E15 | Content-Type charset 整合 | MA-E01 参照 | 意味論は正、互換性確認推奨 |
| SF-E16 | defaultDateRange 境界 `<= 20` | OK | 当月20日に画面開いた時の体験改善 |
| SF-E17 | taxRate URL 正規化 (toFixed 2) | OK | scale 違い誤マッチ防止 |
| SF-E18 | paymentTypeLabel 集約 | OK | MI-E06 参照 |
| SF-E20 | encodeURIComponent ラップ | OK | 数値/Boolean は no-op だが防御的、partnerCode 異常文字対策 |
| SF-E21 | 設計書追記 | OK + Minor | MI-E01〜E03 参照 (細部不一致) |

---

## まとめ

- **Critical: 0 件**。SF-E パッチ群はトランザクション整合性 / 業務契約 / セキュリティ全体で **新規破壊なし**
- **Major: 1 件 (MA-E01)**。`Shift_JIS` → `windows-31j` の Content-Type 文字列ドリフトが MF 側の charset 自動判定にどう影響するか、実 CSV を 1 本通す疎通確認を推奨
- **Minor: 6 件**。設計書微妙ドリフト (MI-E01〜03)、soft-delete 整合改善余地 (MI-E04)、テスト coverage (MI-E05)、買掛側拡張時のメモ (MI-E06)
- **SF-E03 (snake_case)**: `@IdClass` の特性により完全に動作変化なし、cosmetic-only
- **SF-E05 (BulkVerifyService)**: rollback 範囲は完全 (reconcile + reload + verify + saveAll が 1 tx)。`deleteAllInBatch` と PC の subtle な不整合可能性のみ info レベルで注記

→ **次ループ判断: 「ほぼクリア」**。MA-E01 は本番投入前に MF への CSV 実投入で文字化け確認を 1 パス入れれば完了レベル。Minor 群は次回 sprint で巻き取れば足りる。
