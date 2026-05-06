# コードレビュー: 期首残 (Cluster G)

レビュー日: 2026-05-04
ブランチ: refactor/code-review-fixes
レビュアー: code-reviewer subagent (Opus)

## 前提: 設計レビュー指摘の対応状況

設計レビュー (`claudedocs/design-review-supplier-opening-balance.md`) の Critical 2 / Major 5 / Minor 6 は本コードレビュー時点 (commit `4ca40fb` ベース) で **未対応** (`refactor/code-review-fixes` ブランチに修正コミット無し)。重複指摘は以下の通り再掲しない:

| 設計ID | タイトル | 本コードレビューでの扱い |
|---|---|---|
| 設計 C-1 | `AccountsPayableIntegrityService` に opening 未注入 | 再掲しない (設計判断確定待ち) |
| 設計 C-2 | `findOpeningJournal` / `isPayableOpeningJournal` 乖離 | 再掲しない |
| 設計 M-1 | `OPENING_DATE` ハードコード 5 箇所 | 再掲しない |
| 設計 M-2 | `getEffectiveBalanceMap` キャッシュ無し | 再掲しない |
| 設計 M-3 | GENERATED 列を読まずアプリ側 add | 再掲しない (本レビューで **新観点 (G-impl-3)** を追加) |
| 設計 M-4 | 非 admin の GET で他 shop 閲覧可 | 再掲しない |
| 設計 M-5 | manual only 行に MF 後乗りマージ | 再掲しない |
| 設計 m-1〜6 | N+1 / `@Builder` 罠 / `findByPkShopNoAndDelFlg` 未使用 / date-fns / `unmatched` 用語混在 / spike json 残存 | 再掲しない |

本コードレビューでは **実装段階で初めて顕在化する欠陥** および **設計書には現れない実装 detail** を対象に新規指摘を行う。

---

## サマリー

- 総指摘件数 (新規): **Critical 1 / Major 4 / Minor 5**
- 承認状態: **Needs Revision**
- 総評: 設計レビューが指摘した範囲外で、**`SupplierBalancesService.accumulateMfJournals` だけが journal #1 を除外していない非対称** (G-impl-1, Critical) という重大な実装乖離が新たに発見された。設計書 §7.2 の表では「軸 D も除外する」前提でロジックが組まれているように読めるが、実装は「MF 側は #1 を含めて opening を吸収する」コメント (`SupplierBalancesService.java:233-236`) で意図的に分岐しており、ledger / integrity / balances の 3 サービスで journal #1 の扱いが **3 通り** に分かれている (除外+注入 / 除外のみ / 含める+注入)。これは設計レビュー Critical-1 とは別問題で、`SupplierBalancesService` が呼び出す `MfOpeningBalanceService.getEffectiveBalanceMap` の値と `accumulateMfJournals` 内部の journal #1 集計値が **両方** 同じ supplier に加算されてしまう **二重計上の温床** である。

その他、Hibernate 6 の `@Generated` デフォルト挙動 (INSERT のみ refresh) を考慮した設計になっておらず、`fetchFromMfJournalOne` 経路で UPDATE 後に `effective_balance` がメモリ上で stale になる構造的バグを潜在的に抱えている (G-impl-3)。

---

## Critical

### G-impl-1: `SupplierBalancesService.accumulateMfJournals` だけ journal #1 を除外せず、opening を二重計上する構造
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:233-258` (MF 集計、`isPayableOpeningJournal` skip 無し) / 同 `:128-129, 141-143, 173, 193-204` (opening 注入)
- **問題**:
  - `MfSupplierLedgerService.java:116` と `AccountsPayableIntegrityService.java:147` は両方とも `if (MfJournalFetcher.isPayableOpeningJournal(j)) continue;` で journal #1 を MF 集計から除外している。
  - 一方 `SupplierBalancesService.accumulateMfJournals` (新コード `:233-258`) では skip が無く、journal #1 の credit branch がそのまま `mfBySub[subAccountName].credit` に加算される。コメント (`:234-236`) は「MF 側は journal #1 を含めることで MF 側は opening + activity を保持する」と意図を述べている。
  - さらに `:128-129` で `openingMap = MfOpeningBalanceService.getEffectiveBalanceMap()` を取得し、`:141-143` で **self 側だけでなく self.opening / self.closing にも加算** する設計。
  - 両者を組み合わせると:
    - **self 側 closing** = `Σ summary.closingTaxIncluded` + `m_supplier_opening_balance.effective_balance`
    - **MF 側 closing** = `Σ MF cumulative` (journal #1 含む = 期首値 + activity)
    - 期待: 両者 ≒ 一致 (Φ=0 の時 MATCH)。
  - これは「`m_supplier_opening_balance.mf_balance` が journal #1 と完全一致している」という**強い前提**の上に成り立つ。前提が崩れる典型ケース:
    1. shop=2 太幸など journal #1 未掲載 supplier に手動補正を入れた場合: self 側に +manual、MF 側 0 → diff = +manual (期待通り)
    2. journal #1 取得後に MF 上で journal #1 を編集 (税理士修正等) → 自動再取込しない限り `mf_balance` は古い → self 側に古い値が乗り、MF 側に新しい値が乗る → diff = (旧-新)
    3. `mf_balance` 取得時に sub_account 解決失敗 (unmatched_branch) → self 側 opening に乗らない、MF 側には journal #1 credit branch が乗る → diff = -credit
  - 加えて `journalCache` は期間 (MF_PERIOD_START 〜 asOfMonth) で `getOrFetch` するため、journal #1 (transactionDate=2025-06-21、`monthKey = 2025-07-20` bucket) が `MF_PERIOD_START=2025-05-20` 〜 `asOfMonth` 範囲に **含まれる** ケースで顕在化する (実運用の通常パス)。
- **影響**: 累積残一覧で MAJOR フラグが意味的にノイズ化する。整合性レポート (`AccountsPayableIntegrityService.buildSupplierCumulativeDiffMap` `:543-560` から `SupplierBalancesService.generate` を呼ぶ経路) に diff が伝搬し、`reconciledAtPeriodEnd` 判定 (`:382-400`) が SupplierBalances の二重計上に引きずられて誤判定する可能性。既に運用中 (`MEMORY.md` 「Phase B''(light) 完了」記載) なので影響は実害寸前。
- **修正案**:
  1. **方針 A (推奨)**: `SupplierBalancesService.accumulateMfJournals` でも `if (MfJournalFetcher.isPayableOpeningJournal(j)) continue;` を追加し、`MfSupplierLedgerService` と挙動を統一。MF 側 opening は `m_supplier_opening_balance.mf_balance` を `mf.credit` に注入する経路に切り替える (= 軸 D の MF 側にも openingMap 注入)。
  2. **方針 B**: 現状コメントの意図 (「MF 側は journal #1 を含めることで自然に opening が乗る」) を維持するなら、self 側の `opening` 加算を **手動補正分のみ** (`manualAdjustment` のみ) にする。`mf_balance` 由来分は self に加算しない (MF 側 #1 と相殺)。`getEffectiveBalanceMap` を分割するか、別メソッド `getManualAdjustmentMap` を新設。
  3. ユニットテストを「journal #1 のみ + 月次 activity 0」「journal #1 + 月次 activity あり」「journal #1 未掲載 + manual のみ」の 3 ケースで diff=0 となることを assert する形で追加 (現在テスト無し、`MfOpeningBalanceServiceTest` も `SupplierBalancesServiceTest` も未確認)。

---

## Major

### G-impl-2: `fetchFromMfJournalOne` の MF API 呼び出しが `@Transactional` 内で実行 (long-running RPC + Tx 占有)
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java:74-203` (メソッド全体に `@Transactional` 付与、内部で `mfApiClient.listJournals` `:88-89` と `mfApiClient.getTrialBalanceBs` `:102-103` を直接呼ぶ)
- **問題**: 設計レビュー Spring Boot 観点の下半分 (`design-review` レビューチェックリスト L147) で「Minor 扱い」と評価された懸念だが、コードを精読すると:
  - MF API は外部 HTTP 往復 (typical 1〜5 秒、rate limit 時は 350ms × N + retry で最大 ≧ 10 秒)。`@Transactional` 内なので **その間 Postgres の DB connection を 1 本占有** + advisory lock 等は無いが connection pool を消耗。
  - validation 用 `getTrialBalanceBs` (`:102-103`) は **失敗しても warn ログのみで吸収** されるが、その例外は (HikariCP の) connection 待ちタイムアウトを巻き込む可能性。Tx 内で複数 RPC を順序実行する設計はアンチパターン。
  - 41 supplier の小規模 + admin 手動実行 (`SupplierOpeningBalanceController:41` `@PostMapping("/fetch-from-mf")`) なので頻度は低いが、batch 化して定期実行する将来要件 (設計書 §10 Future Work) では明確なリスク。
- **影響**: 通常運用ではすぐに事故化しないが、API 遅延時に他の DB 操作と競合し HikariCP の "Connection is not available" を誘発する潜在バグ。
- **修正案**:
  - メソッドを 2 段に分割:
    1. `fetchFromMfJournalOne(...)` 公開: `@Transactional` を **外し**、MF API 呼び出し + journal 解析 + map 構築までをやる。
    2. `private @Transactional upsertOpeningBalances(parsed_data, userNo)`: parsed_data を引数に取り upsert + flush + sumEffectiveBalance のみ Tx 内で実行。
  - これにより RPC 中の Tx 占有を解消、validation API 失敗のロールバック挙動も独立。

### G-impl-3: Hibernate 6 の `@Generated` デフォルトが INSERT のみ refresh で `effective_balance` が UPDATE 後に stale になる
- **場所**: `backend/src/main/java/jp/co/oda32/domain/model/finance/MSupplierOpeningBalance.java:48-51`
  ```java
  @Column(name = "effective_balance", insertable = false, updatable = false)
  @Generated
  private BigDecimal effectiveBalance;
  ```
- **問題**:
  - 設計レビュー Major-3 は「読み取り側でアプリ計算しているので GENERATED 列の利点が活きていない」を指摘するが、本指摘は別観点: **そもそも Hibernate 6 の `org.hibernate.annotations.Generated` のデフォルト `event` は `INSERT` のみ**。
  - `MfOpeningBalanceService.java:151` で `entity.setMfBalance(value)` を呼んだ後の `repository.save(entity)` は UPDATE 文を発行するが、`effective_balance` は DB 側で再計算されるものの、Hibernate は UPDATE 後に再 SELECT しないので **メモリ上の `entity.effectiveBalance` は古い値のまま**。
  - 現在は `getEffectiveBalanceMap:323-333` 等で `entity.getMfBalance() + entity.getManualAdjustment()` を都度計算しているため顕在化していないが、設計レビュー Major-3 修正で `entity.getEffectiveBalance()` に統一した瞬間にバグが発火する。
  - `repository.flush()` (`:176`) と `findByPkShopNoAndPkOpeningDateAndDelFlg` の再 SELECT (`sumEffectiveBalance:391-395` 経由) で結果的に DB 値を読み直しているのは偶然で、設計意図ではない。
- **影響**: 設計レビュー Major-3 を素直に修正したコミットが本指摘を顕在化させ、`fetchFromMfJournalOne` レスポンスの `totalEffective` が古い値を返す。テストが無いので CI で検出不可。
- **修正案**:
  ```java
  import org.hibernate.generator.EventType;
  @Generated(event = { EventType.INSERT, EventType.UPDATE })
  private BigDecimal effectiveBalance;
  ```
  または `@Formula("COALESCE(mf_balance, 0) + manual_adjustment")` で都度計算式に切替 (DB 側 GENERATED と二重に保ち、整合性は migration で担保)。設計レビュー Major-3 を修正する **前** に必須。

### G-impl-4: ソフト削除された行を MF 再取込が `del_flg='1'` のまま上書きする (silent zombie row)
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java:144-156` (`existing.isPresent()` 分岐で `delFlg` を `"0"` に戻していない)
- **問題**:
  - `repository.findById(pk)` (`:144`) は delFlg を区別しないため、過去に何らかの経路で `delFlg='1'` になった行 (現状 UI 経由では設定不能だが、運用で SQL 直叩きする可能性あり) を取得する。
  - `:147-156` で `mfBalance` / `sourceJournalNumber` 等は更新されるが、**`entity.setDelFlg("0")` が無いため論理削除状態のまま値だけ更新される**。
  - 結果、`getEffectiveBalanceMap` (`:325` `findByPkShopNoAndPkOpeningDateAndDelFlg(..., "0")`) は引き続き除外、UI の `list` (`:252`) も除外、しかし `repository.save` の `upserted++` は incrementされ MF 取得 response の `upsertedCount` は実態と乖離。
- **影響**: zombie row が DB に残り「MF 取得 OK と表示されたのに UI に行が出ない」というデバッグしづらい状態を作る。`preservedManualCount` も削除済み行をカウントしうる。
- **修正案**:
  - `:147` 直後に `entity.setDelFlg("0");` を追加して再有効化。
  - または `findById` の代わりに `findByPkAndDelFlg` を repository に追加して論理削除されていない行のみ対象にする (zombie 発生時は新規 insert 試行 → PK 衝突 → 例外で気付く)。
  - 仕様判断によるが、いずれにせよ **silent な zombie 状態は避ける**。

### G-impl-5: `updateManualAdjustment` の supplier shop 検証が `MPaymentSupplier.shopNo` 直接比較で複数 shop 共有 supplier を弾く
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java:220-225`
  ```java
  MPaymentSupplier supplier = paymentSupplierService.getByPaymentSupplierNo(req.supplierNo());
  if (supplier == null || !req.shopNo().equals(supplier.getShopNo())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ...);
  }
  ```
- **問題**:
  - `MPaymentSupplier` は `shop_no` を単一カラムで持つ前提。`MEMORY.md` 「B-CART 事業部統合方針」で「将来統合前提で shop=1 に寄せる」とあるが、現時点では複数 shop が同一 supplier_code を共有するケース (太幸 = shop=2 が代表例) が存在する。
  - 例えば `supplierNo` が shop=1 配下の MPaymentSupplier の主キーなのに、ユーザが「shop=2 の opening_date」に対して同じ supplierNo で manual を入れたい (= supplierNo は shop=2 マスタでも shop=1 マスタでもなく、別 PK で登録されているはず) ケースが扱えない。
  - 一方で `m_supplier_opening_balance` の PK は (shop_no, opening_date, supplier_no) で、supplier_no が shop_no と独立に存在しうる前提。コード側で「PK の shop_no と supplier の shop_no が一致」を強制すると、 PK の柔軟性を活かせない。
  - 太幸 shop=2 の例では `MPaymentSupplier` 側に shop=2 専用エントリ (paymentSupplierNo は shop=2 用に発番) があれば動くが、運用次第で shop=1 のレコードを代用したい場合に弾かれる。
- **影響**: 設計書 §1.4 で挙げている「shop=2 太幸 ¥742,720 を手動補正で吸収」シナリオが、 `MPaymentSupplier` の登録方法 (shop=2 専用に登録 vs shop=1 を流用) に依存して通る/通らないが分かれる。
- **修正案**:
  - 検証を「supplier が存在するか」のみに緩めるか、「`m_supplier_shop_mapping` 経由で shop に紐づくか」に変更。
  - または `MEMORY.md` の方針 (shop=1 統合) に従い「manual_adjustment は shop=1 のみ受け付ける」と明示し、エラーメッセージを「shop=2 の手動補正は別途 SQL で投入してください」のような運用 escape hatch を提示。
  - 設計書 §5.4 にもこの制約を追記。

---

## Minor

### G-impl-6: `MSupplierOpeningBalance` の `addDateTime` を `nullable = false` にしているが builder で渡されない経路が新規行の挿入を成功させるのは PostgreSQL `DEFAULT CURRENT_TIMESTAMP` に依存
- **場所**: `backend/src/main/java/jp/co/oda32/domain/model/finance/MSupplierOpeningBalance.java:72-73` (`@Column(nullable = false, insertable = false, updatable = false)`) / `MfOpeningBalanceService.java:158-169` (builder で `addDateTime` 未指定)
- **問題**:
  - `insertable=false` でアプリ側からは INSERT 文に含まれない → DB の `DEFAULT CURRENT_TIMESTAMP` (V028 `:24`) が効く。
  - `nullable=false` 制約は Hibernate validation (DDL 生成時) に効くが、Hibernate が INSERT 時に `addDateTime IS NULL` check を入れないので問題は起きない。ただし「nullable=false かつ insertable=false かつ entity 側 builder では未設定」の組み合わせは、Hibernate ddl-auto=validate (本番想定) でカラムの NOT NULL 制約を要求する一方、コード上は値を入れていないため、新規開発者が混乱する設計。
  - 設計レビュー Minor-2 (builder 罠) と同根だが、より構造的: 「DB default 任せ」を明示するなら `@Column(nullable = false, insertable = false, updatable = false)` でなく、entity から完全に外すか `@Generated` を付けて意図を明示すべき。
- **修正案**: `@Generated(event = EventType.INSERT)` を `addDateTime` にも付与し、INSERT 直後に DB から refresh される宣言にする。または builder の `@Builder.Default Instant addDateTime = Instant.now()` で application 側初期化に統一。

### G-impl-7: `findOpeningJournal` が `subAccountName == null` の payable credit branch を `payableCreditBranches` カウントに **含めない** ため、混在 journal で意図せず別 journal を選ぶ可能性
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java:344-360`
- **問題**:
  - `:347-350`: credit 側 payable で `subAccountName != null` のみカウント。`:351-353`: debit 側 payable は無条件カウント (sub_account_name 問わず)。
  - 設計レビュー Critical-2 で指摘された `isPayableOpeningJournal` (`MfJournalFetcher:180-194`) との乖離の **コードレベル詳細**: `isPayableOpeningJournal` は `subAccountName` の null チェックを **しない** ので、`subAccountName=null` の payable credit branch が混じる journal が、
    - `findOpeningJournal` 側: その branch は `payableCreditBranches` カウントに入らない → 比較で別 journal が選ばれる可能性
    - `isPayableOpeningJournal` 側: `hasPayableCredit=true` で除外対象判定
    という非対称が発生する。
  - 設計レビュー Critical-2 では「sub_account_name のチェックなし」と簡略に書かれているが、実装では `findOpeningJournal` だけに sub_account_name 必須条件があるという反対方向の非対称も存在する。
- **修正案**: 設計レビュー Critical-2 の修正案 (共通 util `MfOpeningJournalDetector`) で、判定の base 述語に `subAccountName != null` を含めるか含めないかを 1 箇所で決め、両者を統一する。

### G-impl-8: `tryGetMfTrialBalanceClosing` が `list()` (read-only API) から MF API を呼んでおり、毎回 GET で MF API を叩く
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java:251-320` (`list`) / `:398-409` (`tryGetMfTrialBalanceClosing`)
- **問題**:
  - `list()` は `@Transactional(readOnly=true)` の通常 GET (`SupplierOpeningBalanceController:32-37`) だが、**毎回 MF `/trial_balance/bs` を呼ぶ** (`:298` 経由)。
  - rate limit (350ms 待ち + 429 retry) を伴う外部 HTTP を GET 毎に発行するのは性能・コスト両面で問題。とくに UI が `useQuery` のキャッシュ戦略次第では同一データに対して何度もリクエストが飛ぶ。
  - validation 結果は `m_supplier_opening_balance.last_mf_fetched_at` の代わりに別カラム (`mf_trial_balance_closing` を Summary キャッシュ列にする) で保持してもよい設計。
- **修正案**:
  - 案 A: `list()` から MF API 呼び出しを削除し、validation 結果は `fetchFromMfJournalOne` 実行時に DB に保存 (例: `m_supplier_opening_balance_validation` テーブル新設) → `list()` は DB から読むだけ。
  - 案 B: Spring Cache `@Cacheable("mf-trial-balance")` で 5 分キャッシュ。MF UI で期首残修正後の即時反映は `fetchFromMfJournalOne` 経由で `@CacheEvict`。

### G-impl-9: フロント `submitEdit` の `manualAdjustment` 数値変換でコンマ・全角数字を考慮しない
- **場所**: `frontend/components/pages/finance/supplier-opening-balance.tsx:114-132`
- **問題**:
  - `Number(form.adj)` で `'1,000,000'` や `'１２３４'` (全角) は `NaN` になる。`type="number"` 入力フォーム (`:328`) で防がれているはずだが、ペースト時の挙動はブラウザ依存。
  - エラーメッセージは「補正額は数値で入力してください」のみで、コンマ含むペーストへの hint 無し。
  - `(editing.mfBalance ?? 0) + (Number(form.adj) || 0)` (`:336`) は `Number(form.adj)` が NaN だった時 0 にフォールバックするが、UI 上「実効値 = MF 値」と表示されてしまい、ユーザは「コンマを除けば反映される」と認識できない。
- **修正案**:
  - `parseAmount(form.adj)` ヘルパで `replace(/[,，]/g, '')` + 全角→半角変換してから Number。
  - JST 数字フォーマット `formatCurrency` の対称ペアとして `frontend/lib/utils.ts` 等に共通化。

### G-impl-10: `sumEffectiveBalance` が `repository.findByPkShopNoAndPkOpeningDateAndDelFlg` を呼ぶため、`fetchFromMfJournalOne` 内でループ後の集計クエリと、その直後の `list()` 呼び出しで同じクエリを 2 回発行する潜在
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java:177` (`sumEffectiveBalance`) / `:251-253` (`list` でも同一 query)
- **問題**:
  - `fetchFromMfJournalOne` の戻り値 `totalEffective` を計算するために `findByPkShopNo...` を呼び (`:391`)、フロントは続けて `list` API を呼ぶことで 2 回 select。
  - 設計レビュー Minor-1 (取込ループ内の `findById` N+1) と関連するが、レスポンス内に既に `totalEffective` が含まれている点を考慮すると、`MfOpeningBalanceFetchResponse` に `Row[]` を含めて 1 回で済ませる選択肢がある。
- **修正案**:
  - `fetchFromMfJournalOne` のレスポンスに `SupplierOpeningBalanceResponse.Row[]` を含め、フロント側 `onSuccess` で `queryClient.setQueryData(['supplier-opening-balance', ...], data)` する。`invalidateQueries` (`:77`) は不要に。

---

## 対応表

| ID | 判定 | 対応提案 |
|---|---|---|
| G-impl-1 | **Critical / Accept** | 方針 A or B のどちらかを設計者と確定後、`SupplierBalancesService.accumulateMfJournals` に skip 追加 + 単体テスト 3 ケース。設計書 §7.2 を「軸 D も isPayableOpeningJournal で除外、MF 側にも `m_supplier_opening_balance.mf_balance` を注入」に書き直す |
| G-impl-2 | **Major / Accept** | `fetchFromMfJournalOne` を 2 メソッドに分割 |
| G-impl-3 | **Major / Accept** | `@Generated(event = {INSERT, UPDATE})` 明示。設計レビュー Major-3 修正の **前提条件** |
| G-impl-4 | **Major / Accept** | `existing.isPresent()` 分岐で `setDelFlg("0")` 追加 |
| G-impl-5 | **Major / Conditional** | shop 検証ロジックの仕様確定後に修正 (太幸など複数 shop 共有 supplier の運用方針による) |
| G-impl-6 | Minor / Accept | `@Generated(event = INSERT)` を `addDateTime` に付与 |
| G-impl-7 | Minor / Accept (設計 Critical-2 の一部) | 共通 util に統合 |
| G-impl-8 | Minor / Accept | DB キャッシュ列 or `@Cacheable` で MF API 呼び出し抑制 |
| G-impl-9 | Minor / Accept | `parseAmount` ヘルパ共通化 |
| G-impl-10 | Minor / Accept | レスポンスに Row[] 追加で 1 RTT 化 |

**Approval status**: **Needs Revision** (Critical 1 + Major 4 のため merge 不可)。Critical G-impl-1 は実運用で diff のノイズ化が既に潜在的に発生している可能性が高く、即時対応推奨。

---

## レビュー観点別チェックリスト (新規確認)

### Spring Boot (新規)
- [ ] **Long-running RPC in @Transactional**: `fetchFromMfJournalOne` で MF API 2 回 + DB upsert を同一 Tx 内で実行 (G-impl-2)
- [ ] **Hibernate 6 `@Generated` semantics**: INSERT デフォルトで UPDATE 後 stale (G-impl-3)
- [ ] **Soft-delete / re-activation**: zombie row 上書き (G-impl-4)
- [x] **DTO 変換**: Entity を直接返さず `Row.builder()` ✓
- [x] **`@PreAuthorize` for write APIs**: `fetch-from-mf` / `manual-adjustment` 両方 admin only ✓ (`SupplierOpeningBalanceController:40, 51`)
- [x] **`@Valid` 入力検証**: `SupplierOpeningBalanceUpdateRequest` の `@NotNull` / `@Size(max=500)` ✓

### 期首残固有 (新規)
- [ ] **3 サービス間で journal #1 の扱い対称**: 不対称 (除外+注入 / 除外のみ / 含める+注入) (G-impl-1)
- [x] **`manual_adjustment` 保持**: re-fetch の `existing.isPresent()` 分岐で `setManualAdjustment` を呼んでいない ✓ (`MfOpeningBalanceService:151-156` 確認)
- [x] **shop 横断対応**: PK に shop_no 含めて分離 ✓ (但し manual 入力時の shop 検証ロジックに別問題あり: G-impl-5)

### フロントエンド (新規)
- [ ] **コンマ・全角数字の数値変換**: `Number(form.adj)` (G-impl-9)
- [x] **react-query invalidate**: `fetchMutation` / `updateMutation` の onSuccess で適切に invalidate ✓
- [x] **shop_no 切替時の fetch**: `useQuery` の `queryKey: [..., shopNo, openingDate]` で自動再取得 ✓

### テスト
- [ ] `MfOpeningBalanceServiceTest` 未確認 (Glob で検出されず)
- [ ] `SupplierBalancesServiceTest` 未確認 — G-impl-1 の二重計上を assert するテストが必須
- [ ] E2E (`supplier-opening-balance.spec.ts`) 未確認

---

## 推奨アクション順

1. **(即時)** G-impl-1: 方針 A/B を設計者確定 → `SupplierBalancesService.accumulateMfJournals` 修正 + 単体テスト追加
2. **(即時)** G-impl-3: `@Generated(event = {INSERT, UPDATE})` 修正 (設計レビュー Major-3 の修正前に必須)
3. **(短期)** G-impl-2: `fetchFromMfJournalOne` の Tx 分割
4. **(短期)** G-impl-4: zombie row 復活ロジック追加
5. **(中期)** G-impl-5: shop 検証ロジックの仕様確定
6. **(随時)** Minor 5 件をリファクタリングに含める

---

## 補足: G-impl-1 の数値シミュレーション (運用検証推奨ケース)

| シナリオ | self.opening | self.closing (期末) | MF.credit cumulative | MF.debit cumulative | self - mf | 期待 | 実装 |
|---|---|---|---|---|---|---|---|
| (a) journal #1 ¥100, 月次 activity 0 | 100 | 100 | 100 (#1) | 0 | 0 | 0 (MATCH) | 0 ✓ |
| (b) journal #1 ¥100, 月次 +50 | 100 | 100+50=150 | 100+50=150 | 0 | 0 | 0 (MATCH) | 0 ✓ |
| (c) journal #1 ¥100 (mf_balance=100, manual=0), 月次 -30 (支払) | 100 | 100-30=70 | 100 | 30 | 70-(100-30)=0 | 0 (MATCH) | 0 ✓ |
| (d) **manual のみ ¥50, journal #1 未掲載** | 50 | 50 | 0 | 0 | 50 | 50 (太幸を表現) | 50 ✓ |
| (e) **journal #1 ¥100 + 後日 MF UI で journal #1 編集 ¥120** (mf_balance は古い ¥100) | 100 | 100 | 120 (#1) | 0 | -20 | 0 想定 (再取込推奨) | -20 (MAJOR フラグ点灯) ⚠ |
| (f) **journal #1 unmatched_branch ¥80** (mf_balance に入らず) | 0 | 0 | 80 (#1, sub_account 一致なし) | 0 | -80 | 警告レベル | SELF_MISSING + diff=-80 ⚠ |

(a)〜(d) は正常動作。(e)(f) が G-impl-1 の症状で、**運用上「再取込忘れ」が起きると即 MAJOR が点灯する**。方針 A (skip + DB 注入) 採用なら (e) は次回 fetch まで diff=0 (期首は固定値で扱われ、journal の編集は activity として +20 月次で吸収される)、(f) は m_supplier_opening_balance に入らない supplier として SELF_MISSING ノイズが残るので別途 unmatched_branches 警告で運用補完。

---

## 補足: 出典ファイル一覧 (Cluster G スコープ)

### Backend
- `backend/src/main/java/jp/co/oda32/domain/model/finance/MSupplierOpeningBalance.java` (Entity, 86 行)
- `backend/src/main/java/jp/co/oda32/domain/model/embeddable/MSupplierOpeningBalancePK.java` (PK, 32 行)
- `backend/src/main/java/jp/co/oda32/domain/repository/finance/MSupplierOpeningBalanceRepository.java` (Repository, 24 行)
- `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java` (Service, 422 行)
- `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfJournalFetcher.java` (`isPayableOpeningJournal:180-194`, `toClosingMonthDay20:164-169`)
- `backend/src/main/java/jp/co/oda32/api/finance/SupplierOpeningBalanceController.java` (Controller, 60 行)
- `backend/src/main/java/jp/co/oda32/dto/finance/SupplierOpeningBalanceResponse.java` (Response DTO, 51 行)
- `backend/src/main/java/jp/co/oda32/dto/finance/SupplierOpeningBalanceUpdateRequest.java` (Request DTO, 21 行)
- `backend/src/main/java/jp/co/oda32/dto/finance/MfOpeningBalanceFetchResponse.java` (Fetch Response, 38 行)
- `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfSupplierLedgerService.java` (下流注入, `:107-109`)
- `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java` (下流注入 + G-impl-1, `:128-129, 141-143, 173, 193-204, 233-258`)
- `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java` (journal #1 除外のみ, `:147`)
- `backend/src/main/resources/db/migration/V028__create_supplier_opening_balance.sql` (DDL, 49 行)

### Frontend
- `frontend/app/(authenticated)/finance/supplier-opening-balance/page.tsx` (page entry, 6 行)
- `frontend/components/pages/finance/supplier-opening-balance.tsx` (Page component, 382 行)
- `frontend/types/supplier-opening-balance.ts` (型定義, 86 行)
