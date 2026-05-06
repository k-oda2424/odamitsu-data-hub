# 設計レビュー: 期首残 (Cluster G)

レビュー日: 2026-05-04
対象設計書: `claudedocs/design-supplier-opening-balance.md` (Phase 1 で逆生成)
レビュアー: Opus サブエージェント
対象ブランチ: `refactor/code-review-fixes`

---

## サマリー

- 総指摘件数: **Critical 2 / Major 5 / Minor 6**
- 承認状態: **Needs Revision**
- 総評: 機能としては期首残取込・手動補正・下流注入の責務分離が綺麗で、`@Generated` 列・`putIfAbsent` 先勝ちなど現場知見が反映された堅実な実装。一方で **下流 3 サービス間で opening 注入の取り扱いが非対称** (Critical-1) で、整合性レポートに opening が乗っていない。`opening_date` ハードコード 5 箇所と journal 判定ロジックの二重化も将来 fiscal year 跨ぎで顕在化する。

---

## Critical 指摘

### Critical-1: `AccountsPayableIntegrityService` に期首残が注入されていない (設計書 TODO #4 が "実装欠落" として確定)
- **箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java:71-77` (フィールド定義)、`AccountsPayableIntegrityService.java:147` (journal #1 除外のみ)
- **問題**: 設計書 §7.2 の表で「`getEffectiveBalanceMap` 注入箇所は **TODO: 確認**」と明記されている件の実態は **未注入** である。`MfOpeningBalanceService` も `MSupplierOpeningBalanceRepository` もフィールドに無い (`AccountsPayableIntegrityService.java:71-77`)。一方で MF 側は `isPayableOpeningJournal` で journal #1 を除外している (`:147`) ため、突合が **MF=opening 抜き / self=opening 抜き** の対称になっている (= 月次 per-row 比較は成立するが、fromMonth が opening 直後 (例: 2025-07-20) の場合の累積系項目は片寄せ可能性あり)。
- **影響**: 設計書 §7.2 の `SupplierBalancesService` (注入する) と `AccountsPayableIntegrityService` (除外のみ) で対称性が異なる。同じ「整合性検出」と称して画面に並ぶ機能で挙動が分岐し、今後 cumulative 系列を表示した時に「累積残一覧では opening 込み / 整合性レポートでは opening 抜き」となりユーザを混乱させる。
- **修正案**:
  1. (短期) 設計書 §7.2 の "TODO: 確認" を **「現状未注入。月次 per-period diff のみ意味を持ち、cumulative 系には opening 込みの `SupplierBalancesService` を別途参照すること」と明記** する。
  2. (中期) `AccountsPayableIntegrityService` で fromMonth が `OPENING_DATE` 期間内に重なる場合は opening を注入するか、明示的に「opening は別管理 (Cluster G 画面参照)」を画面に出す。
  3. レビューチェックリスト的には設計書 TODO #4 を "高優先度" → "実装タスク" に昇格し、`SupplierBalancesService` のように `MfOpeningBalanceService` を DI して扱いを統一する。

### Critical-2: `findOpeningJournal` と `isPayableOpeningJournal` のロジック乖離が将来 false negative/positive を生む
- **箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java:336-363` (取込時) / `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfJournalFetcher.java:180-194` (除外時)
- **問題**: 同じ「期首残高仕訳」を判定する 2 関数で条件が厳密に異なる。
  - `findOpeningJournal:347-350`: credit 側の `accountName=='買掛金' AND subAccountName != null` の **件数が最大** の journal を採用 (sub_account_name 必須)
  - `isPayableOpeningJournal:182-193`: debit 側に '買掛金' が **1 つでも** あれば false、credit 側に '買掛金' が **1 つでも** あれば true (sub_account_name のチェックなし)
  - そのため、credit に sub_account_name の無い '買掛金' branch が混じる "汚れた" opening journal は **取込側では拾わないが、除外側では除外する** という非対称が生じうる。
  - 逆に MF 側で支払消し込み仕訳 (debit に '買掛金' あり) が openingDate 翌日に登録されると、`findOpeningJournal` は同日のもう 1 つの journal を採用する一方、`isPayableOpeningJournal` はその支払仕訳を除外しない (= 累積側で二重計上回避が成立しない)。
- **影響**: 期首残を再取込してから ledger を見たときに「MF cumulative の数字が openingBalance と整合しない」ことが起こる。これは 2026-04-24 の初回取込時には 41 件全 sub_account_name 有り・他 journal 無しで顕在化していないだけで、**運用が進んだ翌期 (2026-06-21 切替時) には MF 側で 5/20 締め支払と期首残仕訳が同 transaction_date で登録されて事故になる確率が高い**。
- **修正案**:
  - 共通 util `MfOpeningJournalDetector` を新設し、両者で **同一の判定述語** を共有する (例: "`hasPayableCredit && !hasPayableDebit`" を base に、取込側のみ "最大件数選好" を adapter 化)。
  - 既存呼び出し 3 箇所 (MfOpeningBalanceService, MfSupplierLedgerService, AccountsPayableIntegrityService) を共通化 util 経由に置換。
  - ユニットテストを 4 ケース (純粋 opening / 支払のみ / 混在 / 空) で追加。設計書 TODO #1 を「中」→「高」に昇格。

---

## Major 指摘

### Major-1: `opening_date` ハードコードが 5 箇所に分散
- **箇所**:
  - `frontend/types/supplier-opening-balance.ts:85` `DEFAULT_OPENING_DATE = '2025-06-20'`
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfSupplierLedgerService.java:52` `OPENING_DATE = LocalDate.of(2025, 6, 20)`
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:56` `OPENING_DATE = LocalDate.of(2025, 6, 20)`
  - `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableBackfillTasklet.java:63` `EXPECTED_FROM_MONTH = LocalDate.of(2025, 6, 20)`
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfBalanceReconcileService.java:46` `FISCAL_OPENING_DATE = LocalDate.of(2025, 5, 20)` (しかも 5/20 で別の値)
- **問題**: 設計書 §8.1 が指摘済だが「3 箇所」と過小評価。実際は **5 箇所、しかも 6/20 と 5/20 が混在**。`MfBalanceReconcileService` の 5/20 は MF fiscal year 開始月境界 (≠ opening 仕訳日) で意味的には正しいが、命名が紛らわしい。
- **影響**: 翌期 (2026 年度開始時) に opening_date を 1 箇所変えても他で旧日付が使われ、ledger / supplier-balances / backfill が部分的にしか反映されない。
- **修正案**:
  - 共通 enum or `@ConfigurationProperties` クラス `FinancePeriodConfig` を `domain/service/finance` に新設し、`fiscalYearStartDate (=2025-06-21)` / `openingBucketDate (=2025-06-20)` / `mfPeriodStartDate (=2025-05-20)` を集約。
  - フロントは `/api/v1/finance/period-config` を新設して取得 (admin がフォームで上書き可能にする方が実運用上安全)。
  - 設計書 §8.1 を「3 箇所」→「5 箇所、命名が異なる 5/20 値を含む」に修正。

### Major-2: `getEffectiveBalanceMap` が下流から呼ばれるたびに DB を再 fetch (キャッシュ無し)
- **箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java:323-333`
- **問題**: 41 supplier × 1 行と小規模なので現状は性能問題化しないが、`SupplierBalancesService.generate` (`SupplierBalancesService.java:128-129`) と `MfSupplierLedgerService.getSupplierLedger` (`MfSupplierLedgerService.java:107-109`) が同じ呼び出しを毎リクエスト発行する。
- **影響**: 累積残一覧 (`SupplierBalancesService`) は journal cache hit 時 75ms と高速化されているのに、opening は毎回 DB 往復で潜在的にボトルネックを残す。supplier 数が増えれば更に悪化。
- **修正案**:
  - `@Cacheable("supplier-opening-balance")` を `getEffectiveBalanceMap` に付与、key=`shopNo + openingDate`。
  - `updateManualAdjustment` / `fetchFromMfJournalOne` で `@CacheEvict` 必須。
  - 既存 `MfJournalCacheService` 実装パターンに揃えるか、Spring Cache 標準に乗るか検討。

### Major-3: `effective_balance` GENERATED 列を `getEffectiveBalanceMap` で使っていない
- **箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java:329` `BigDecimal v = nz(e.getMfBalance()).add(nz(e.getManualAdjustment()));`
- **問題**: DB 側に GENERATED 列を用意した目的 (アプリ計算ミス防止、設計書 §3.3 に明記) を、map 構築側で破っている。`e.getEffectiveBalance()` を読めば 1 行で済む。`sumEffectiveBalance:389-396` も同様、`list:273` の `Row.effectiveBalance` も同様。
- **影響**: 万が一将来 GENERATED 式を変更 (例: 切捨て規則の変更) してもアプリが追従しないバグの温床。設計書 §8.4 で「書き込みは `mf_balance` または `manual_adjustment` 経由」と謳っているのに、読み取りは canonical な GENERATED 列を使わない一貫性崩れ。
- **修正案**: `MfOpeningBalanceService` 内の add 計算 3 箇所を `e.getEffectiveBalance()` に置換。`@Generated` の挙動 (insert/update 後に hibernate が refresh するか) を確認、必要なら `@Generated(event = {INSERT, UPDATE})` を明示。

### Major-4: 非 admin の GET 認可で他 shop 期首残が閲覧可能
- **箇所**: `backend/src/main/java/jp/co/oda32/api/finance/SupplierOpeningBalanceController.java:32-37` `list` エンドポイント
- **問題**: `@PreAuthorize("isAuthenticated()")` のみで、`shopNo` パラメータは検証されていない。プロジェクト共通パターンでは `CustomService` でショップ権限チェックが入るが、この読み取り API は素通しで `shopNo=2` を渡せば他 shop 期首残が見える。
- **影響**: shop=1 のユーザが shop=2 太幸の手動補正 (税理士確認情報) を覗ける。設計書 §8.6 が "閲覧可" と書いているが、shop 権限はかける必要あり。
- **修正案**:
  - `@PreAuthorize("authentication.principal.shopNo == 0 or authentication.principal.shopNo == #shopNo")` を `list` にも付与。
  - Controller 層で LoginUser から shopNo を取り出し、自 shop 以外は 403。

### Major-5: `updateManualAdjustment` の new 行で `addUserNo` のみセット、既存行に `entity.setManualAdjustment(adj)` しても `mfBalance` の保護が無い
- **箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java:230-245`
- **問題**: 既存 entity に `entity.setManualAdjustment(adj)` 等は更新するが、`mfBalance` には触れないので OK。一方、新規行の場合 `mfBalance: null` で insert するが、その後 MF 再取込が走ると `existing.isPresent()` 分岐に入り `entity.setMfBalance(value)` で **手動補正だけで作った行に MF 値が後から乗る** ことになる。これ自体は意図通りだが、設計書 §5.4 の「既存行ありの場合は manual_adjustment 常に保持」は、新規 manual 投入後に MF 取得してマージされる経路がドキュメントに無い。
- **影響**: shop=2 太幸のように "MF journal #1 に居ない" supplier を手動補正で投入した後、運用ミスで shopNo=1 で MF 再取込しても (太幸は shop=2 なので別 PK) 衝突しない。が、shop=1 で手動補正した supplier が後で MF #1 に追加された場合、自動マージされて mf+manual の二重計上が起こる。
- **修正案**:
  - 設計書 §5.4 にこのマージ動作を追記し、「manual のみで作った行に MF が後乗りする可能性」を運用上の注意として記載。
  - or `manualAdjustment` 投入時に `journal #1 未掲載の理由` を別フラグ `manual_only_flag` で持ち、MF 再取込でその supplier を skip する選択肢を出す (要件次第)。

---

## Minor 指摘

### Minor-1: `findById` ループによる N+1 (取込ループ内で 1 supplier ずつ select)
- **箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java:144` `Optional<MSupplierOpeningBalance> existing = repository.findById(pk);` (for-each 内)
- **問題**: 41 supplier 程度なら 41 回 select で実害は無いが、典型的な N+1。`findByPkShopNoAndPkOpeningDateAndDelFlg(shopNo, openingDate, "0")` を事前 1 回 fetch して `Map<PK, Entity>` を組めば 1 query で済む。
- **修正案**: ループ前に `Map<Integer, MSupplierOpeningBalance> existingByNo` を構築。

### Minor-2: `MSupplierOpeningBalance` Entity の `@Builder` + `@AllArgsConstructor` で `addDateTime` (insertable=false) が builder に出る
- **箇所**: `backend/src/main/java/jp/co/oda32/domain/model/finance/MSupplierOpeningBalance.java:72-73`
- **問題**: `addDateTime` は `insertable=false, updatable=false` で DB default 任せだが、Lombok `@Builder` には現れる。誤って `.addDateTime(...)` を builder で渡してもサイレントに無視される (バグ温床)。`MfOpeningBalanceService.java:158-169` の new 行 builder では渡していないので OK だが、将来追加した時に気付けない。
- **修正案**: `@Builder.Default` または builder から除外 (`@Setter(AccessLevel.NONE)` + 手動 builder)。少なくともコメントで「DB default に任せる、builder に渡しても無効」と注記。

### Minor-3: Repository に `findByPkShopNoAndDelFlg` があるが未使用
- **箇所**: `backend/src/main/java/jp/co/oda32/domain/repository/finance/MSupplierOpeningBalanceRepository.java:22`
- **問題**: Grep の通り呼び出し箇所無し。YAGNI 違反。
- **修正案**: 削除。将来必要になったら追加。

### Minor-4: `opening_date` 型を `LocalDate` のままハードコード文字列でフロントから受ける
- **箇所**: `frontend/types/supplier-opening-balance.ts:85` `DEFAULT_OPENING_DATE = '2025-06-20'` / `frontend/components/pages/finance/supplier-opening-balance.tsx:171` の `new Date(openingDate).getTime() + 86400000`
- **問題**: タイムゾーン依存。`new Date('2025-06-20')` は環境によって UTC 解釈になり、JST にすると 1 日ズレる可能性。`.toISOString().slice(0, 10)` で UTC 切り出しを使っているので shop_no=1 (JST) の運用では意図と合わない経路が紛れる。
- **修正案**: date-fns の `addDays` を使う (`addDays(parseISO(openingDate), 1)`)。プロジェクト共通パターン (`migration-guide.md` で date-fns 採用) に揃える。

### Minor-5: `unmatched` フラグの意味が画面と Service 層で異なる
- **箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java:269` `boolean unmatchedFlag = sup == null;` (DB に opening 行はあるが supplier マスタから消えた case) / `MfOpeningBalanceFetchResponse.UnmatchedBranch` (取込時、MF sub_account → supplier 解決失敗 case)
- **問題**: 同じ "unmatched" 用語が「supplier マスタ消滅」と「sub_account 未解決」の 2 種類を指している。フロント `supplier-opening-balance.tsx:223` の警告文は両者を混ぜて説明している。
- **修正案**: Row 側を `supplierDeleted: boolean` にリネーム or 別フラグ追加。設計書 §6.3 の用語定義を明確化。

### Minor-6: `claudedocs/_spike_mf_opening_*.json` が repo に残存
- **箇所**: 設計書 §8.7 で TODO #5 として既に指摘済 (優先度: 低)
- **問題**: spike 用 JSON サンプルが残っており .gitignore に入っていない可能性。レビュアーから見ると「実コードと連動してるのか?」の混乱を招く。
- **修正案**: 削除可否を本人に確認の上、不要なら削除コミット。

---

## 設計書 vs 実装の乖離

| # | 設計書 記述 | 実装 | 評価 |
|---|---|---|---|
| 1 | §7.2 表 「`AccountsPayableIntegrityService` で `getEffectiveBalanceMap` 注入 — TODO 確認」 | 未注入。journal #1 除外のみ (`AccountsPayableIntegrityService.java:147`) | **乖離 (Critical-1)**。設計書を「未注入が現状」に書き直す必要 |
| 2 | §8.1 「`OPENING_DATE` 固定が 3 箇所」 | 実際は 5 箇所、5/20 値を含む | **乖離 (Major-1)**。設計書修正必要 |
| 3 | §3.3 「`effective_balance` を GENERATED にしてアプリ側計算ミスを防ぐ」 | `getEffectiveBalanceMap` / `sumEffectiveBalance` / `list` でアプリ側 add 計算 | **乖離 (Major-3)**。実装を `getEffectiveBalance()` に統一すべき |
| 4 | §8.6 「一覧 GET は認証ユーザー全員が閲覧可」 | shop 権限チェック無し、他 shop が見える | **乖離 (Major-4)**。仕様か実装かどちらを正とするか要決定 |
| 5 | §5.4 表 「既存行あり: manual 常に保持」 | manual のみで作った行に後から MF 値がマージされる経路を未記述 | **記述不足 (Major-5)** |
| 6 | §7.4 「fallback 経路: opening のみ supplier」 | `SupplierBalancesService.java:192-204` で実装済、設計書と一致 | OK |
| 7 | §1.4 「shop_no=1, 41 supplier ¥14,705,639」 | `MfOpeningBalanceService.fetchFromMfJournalOne` の log で出る数字。コードからは検証不可 | OK (運用情報) |
| 8 | §3.1 表 `add_date_time` 共通監査 ◯ | `MSupplierOpeningBalance.java:72` で `insertable=false` (DB default 任せ) | OK だが builder 罠 (Minor-2) |
| 9 | §4.4 「`adjustmentReason` 最大 500 文字 / `manualAdjustment != 0` の時必須」 | DTO `@Size(max=500)` (`SupplierOpeningBalanceUpdateRequest.java:19`) と Service 層検証 (`MfOpeningBalanceService.java:215-218`) で実装一致 | OK |

---

## レビューチェックリスト結果

### Spring Boot 観点
- [x] **Layer 違反なし**: Controller (`SupplierOpeningBalanceController`) は service を呼ぶのみ、business logic は Service に集約。`@Valid` も適切。 ✓
- [ ] **@Transactional 適切**: `@Transactional(readOnly=true)` クラス + 書き込みメソッドに `@Transactional` 上書き。 **fetchFromMfJournalOne の MF API 呼び出しが `@Transactional` 内で行われている (`MfOpeningBalanceService.java:74-203`)** — long-running RPC をトランザクション内で行うアンチパターン。 **Major 級寄りだが、運用 41 件 + 1 trial_balance 呼び出しなので一旦 Minor 扱い**。修正案: MF fetch は別 method で行い、upsert 部分のみ `@Transactional` を切る。
- [x] **N+1**: 取込ループ内 `findById` あり (Minor-1)。重大ではないが要改善
- [x] **DI**: `@RequiredArgsConstructor` + final field、Lombok 統一。 ✓
- [x] **DTO 変換**: Entity を直接返さず `Row.builder()` 等で変換。 ✓
- [x] **Migration 安全性**: V028 は CREATE TABLE のみ、後方互換破壊なし。`generated column` は PG 12+ で利用可、PG 17 では問題なし。 ✓
- [x] **`@Generated` の hibernate 整合性**: `insertable=false, updatable=false` 付与済。値の refresh タイミングは `flush()` 直後に再 select (`fetchFromMfJournalOne:176-177`) で担保。 ✓ (ただし Major-3 で利用側未統一)

### 期首残固有観点
- [ ] **MF journal #1 ハードコード判定の保守性**: 構造判定で number に依存しない設計は良い。ただし 2 関数で乖離 (Critical-2) — 共通化必要
- [ ] **手動補正と MF 再取得のコンフリクト解決**: `manualAdjustment` 保持は実装済。ただし「手動 only 行が後から MF #1 にマージされる」経路は設計書未記述 (Major-5)
- [ ] **下流 3 サービスへの注入が一貫**: SupplierBalancesService と MfSupplierLedgerService は注入済、AccountsPayableIntegrityService は未注入 (Critical-1)
- [ ] **`opening_date` 3 箇所ハードコード対策**: 実態 5 箇所、設計書修正と config 化が必要 (Major-1)
- [x] **effective_balance 計算式の正しさ**: `COALESCE(mf, 0) + manual` で signed 補正可能。`mf=NULL` でも崩れない。 ✓
- [x] **shop_no 横断対応**: PK に shop_no 含めて分離。 ✓
- [x] **fallback 経路 (opening のみ supplier)**: `SupplierBalancesService:192-204` で実装。 ✓

### セキュリティ
- [x] 書き込み系 admin only ✓
- [ ] 読み取り系 shop 権限 (Major-4)
- [x] SQL injection なし (Spring Data JPA) ✓
- [x] `@Valid` + `@NotNull` 等の入力検証 ✓

### フロントエンド
- [x] React Query 適切利用 ✓
- [x] admin 判定 `user.shopNo === 0` 統一 ✓
- [x] sonner toast 統一 ✓
- [x] `enabled: shopNo !== undefined && !!openingDate` 初期検索なしパターン ✓
- [ ] date-fns 利用 (Minor-4)
- [x] SearchableSelect / Dialog / Badge など shadcn/ui 統一 ✓

### テスト
- [ ] **Service ユニットテスト**: 設計書 TODO #8 で「未確認」、実際にコードベースで `MfOpeningBalanceServiceTest` の存在を確認できず。`fetchFromMfJournalOne` の mock テスト (4 ケース: 通常 / journal #1 無し / unmatched 全件 / token 期限切れ) を追加すべき
- [ ] E2E (Playwright) テスト未確認

---

## 推奨アクション順

1. **(即時)** Critical-1: `AccountsPayableIntegrityService` の opening 注入方針を **「未注入で正しい / 注入する」のどちらかに決定** し、設計書 §7.2 を確定。「未注入で正しい」なら設計書記述を改め、「注入する」なら実装を追加
2. **(即時)** Critical-2: `findOpeningJournal` / `isPayableOpeningJournal` の共通化 + ユニットテスト追加 (来期切替前の事故予防)
3. **(短期)** Major-1: `FinancePeriodConfig` に集約、5 箇所のハードコード解消
4. **(短期)** Major-4: `list` エンドポイントに shop 権限チェック付与
5. **(中期)** Major-2/3: cache 化 + GENERATED 列の利用統一
6. **(中期)** Minor-1〜6: リファクタリング & 設計書修正
