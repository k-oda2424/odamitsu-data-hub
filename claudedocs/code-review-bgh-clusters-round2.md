# 再レビュー: 残 3 クラスター B/G/H 修正後

レビュー日: 2026-05-04
対象: triage SF-B/G/H* 適用後 (35 件 = Backend 20 + Docs 6 + Frontend 9)
レビュアー: Opus サブエージェント (round 2)
ベースライン: `./gradlew compileJava` PASS (BUILD SUCCESSFUL, 1s)

## サマリー (3 クラスター合算)
- 新規発見: **Critical 2 / Major 7 / Minor 8**
- 既存修正の評価: 大半は triage 通り適用されているが、**SF-G01 の opening 注入が二重計上を発生させ得る論点**と **SF-B01 の認可化に伴う UI 側 silent 403 UX 退化**は要対応。
- API Breaking change (SF-H06) のフロント影響: 大きな問題なし (Frontend agent が `AccountingStatus` を `accounting-workflow.tsx` から re-export しており E2E mock も型付け済)
- `@Generated(event=INSERT,UPDATE)` (SF-G02): Hibernate 6.5.3 で正しく動作する書き方。SF-G07 の前提として成立。
- 依然要動作確認: SF-G01 の数値挙動 (実 DB)、SF-H01 の複数店舗動作。

## クラスター別サマリー
| Cluster | Critical | Major | Minor |
|---|---|---|---|
| B | 1 | 2 | 3 |
| G | 1 | 3 | 2 |
| H | 0 | 2 | 3 |

---

## Critical (新規発見、即修正必要)

### CR-B01: SF-B01 認可化に伴う `cashbook-import.tsx` の silent 403 UX 退化
- 元 SAFE-FIX: SF-B01 + (本来同期すべきだった UI 修正)
- 場所:
  - 修正済: `backend/src/main/java/jp/co/oda32/api/finance/MfClientMappingController.java:27-34` (POST に `@PreAuthorize("authentication.principal.shopNo == 0")`)
  - **未修正**: `frontend/components/pages/finance/cashbook-import.tsx:159` の「マッピング追加」ボタン (admin gating なし)
- 問題:
  - 旧仕様 (一般ユーザでも追加可) は cashbook 取込フローのインライン UX として設計されていた。SF-B01 で backend を admin 限定化したが、`cashbook-import.tsx` 側の「マッピング追加」ボタン (preview の `unmappedClients` 行) は誰でも見える/押せる状態のまま残っている。
  - 非 admin がボタンを押す → POST 失敗 (403) → `addMappingMutation.onError` で `toast.error(e.message)` のみ。ボタンは消えず、ユーザは「何度押しても失敗する」状態に陥る。
  - triage SF-B01 の依存欄に「cashbook-import.tsx の "マッピング追加" ボタンが admin 以外で動作しなくなる影響をフロント側でも UX 確認」と明記されていたが、実装側で取りこぼされている。
- 影響:
  - 経理運用上、現金出納帳取込は店舗担当者 (非 admin) が実施する想定。マッピング不在を発見しても admin に依頼する導線がなく、業務がブロックされる。
- 修正案 (推奨度順):
  - (A) `cashbook-import.tsx` で `useAuth()` から `isAdmin = user?.shopNo === 0` を導出し、「マッピング追加」ボタンを `{isAdmin && ...}` でラップ。非 admin には「未マッピング得意先があります。管理者に追加を依頼してください: {alias}」のようなメッセージに切替。
  - (B) DD-BGH-04 の B 案 (pending/approved status) を採用し、一般ユーザは pending 起票のみ可能化 (中期)。
- 重大度: **Critical** (運用導線断絶)。最小修正なら 1 ファイル 5 行。

### CR-G01: SF-G01 で opening が「self に注入され MF 側で除外される」非対称が二重計上の可能性
- 元 SAFE-FIX: SF-G01 (`SupplierBalancesService.accumulateMfJournals` で journal #1 skip) + 既存の opening 注入ロジック
- 場所: `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:121-146, 229-257, 259-272`
- 問題:
  - SF-G01 適用後、MF 側 `accumulateMfJournals` は journal #1 (期首残高仕訳) を skip する。これにより MF 累積残は「fiscal year 開始 (2025-06-21) 以降の取引のみ」を反映。
  - self 側は line 137-139 で `openingMap` (`getEffectiveBalanceMap(shopNo, SELF_BACKFILL_START=2025-06-20)`) を `self.opening` / `self.closing` に加算注入。
  - **加えて**、`accumulateSelf` (line 259-272) は `r.transactionMonth == MF_JOURNALS_FETCH_FROM (=2025-05-20)` 行から `r.opening_balance_tax_included` を累積。つまり以下 2 ソースから opening が読まれる:
    - `m_supplier_opening_balance.effectiveBalance` (注入)
    - `t_accounts_payable_summary.opening_balance_tax_included` for transactionMonth=2025-05-20 (蓄積)
  - 両方が populated されている supplier は `self.opening` 二重計上され、`diff = self.closing - mfBalance` が「opening 1 個分の正方向ズレ」を発生する。
  - コード line 122-123 のコメントは古い (SF-G01 前提): 「MF 側は journal #1 が accumulateMfJournals に含まれるため opening 相当が既に mf.credit 累積に入っている」と書かれているが、SF-G01 で skip されたため mf.credit に opening は入らなくなった。それなのに self 側だけ opening を 2 ソースから加算しているのは整合が取れない。
- 影響:
  - 累積残一覧 UI (`/finance/supplier-balances`) で、opening 持ち supplier (= 期首残ありの 41 supplier ¥14,705,639) が MAJOR フラグで点灯する可能性。
  - triage SF-G01 の検証ガイダンス「数値は一致のはず」が崩れる前提条件 = `t_accounts_payable_summary.opening_balance_tax_included` for 2025-05-20 が空であること。MEMORY.md の "Phase B (cumulative opening_balance) — opening_balance per row" 実装後の挙動次第。
- 修正案 (推奨度順):
  - (A) `accumulateSelf` を `MF_JOURNALS_FETCH_FROM` 等と無関係化し、opening の取得先を **`m_supplier_opening_balance` 一本** に統一。`r.opening_balance_tax_included` を加算しない。
  - (B) `openingMap` 注入ロジックを撤去し、self の opening 起点は `t_accounts_payable_summary` のみとする。MF 側は journal #1 を含める (= SF-G01 を revert)。
  - どちらも採用前に **実 DB で diff が 0 近くに収束するか確認必須**。MEMORY.md `feedback_incremental_review` ルールに従い、curl で以下を測定:
    ```
    GET /api/v1/finance/supplier-balances?shopNo=1&asOfMonth=2026-04-20
    ```
    `summary.totalDiff` の絶対値が改修前後で大きく動かないこと。
  - (C) コード line 122-123 のコメントを SF-G01 整合に修正 (最低限のドキュメント整合)。
- 重大度: **Critical** (会計値の整合性が画面で MAJOR 多発する可能性)。

---

## Major

### MJ-B01: SF-B10 `MfTaxResolver.IllegalStateException` 化が CashBookController の local catch で 422 generic にならない
- 元 SAFE-FIX: SF-B10
- 場所:
  - 修正済: `backend/src/main/java/jp/co/oda32/domain/service/finance/MfTaxResolver.java:30` (`throw new IllegalStateException`)
  - 影響: `backend/src/main/java/jp/co/oda32/api/finance/CashBookController.java:62-67` (local try/catch)
  - `backend/src/main/java/jp/co/oda32/api/finance/FinanceExceptionHandler.java:52-57`
- 問題:
  - SF-B10 の意図は「未知の `tax_resolver` コードを `IllegalStateException` 化 → `FinanceExceptionHandler#handleIllegalState` が 422 + 汎用メッセージに翻訳」。
  - しかし `CashBookController#convert` は **local で `IllegalStateException` を catch して `e.getMessage()` を返す** (line 62-63: `return ResponseEntity.unprocessableEntity().body(Map.of("message", e.getMessage()))`)。Spring の `@RestControllerAdvice` は「未捕捉の例外」のみを処理するため、local catch が優先され、advisor の 「内部詳細を隠蔽する 汎用メッセージ」セキュリティ意図 (MA-01) が **失効**。
  - 一方 `CashBookController#preview` (line 28-40) は `IllegalStateException` を catch していないため、`MfTaxResolver` 経由で発生したら advisor の 422 generic に流れる → エンドポイント間で挙動が非対称。
- 影響:
  - `convert` で「未知の税区分リゾルバ: XYZ」のような **マスタ内部詳細が client に露出**する。`mf_journal_rule` マスタは admin のみ編集可能だが、API レスポンスから resolver code が見えるのは情報統制ポリシーに反する。
- 修正案:
  - (A) `CashBookController#convert` の `IllegalStateException` catch を撤去 → advisor 経由で統一された 422 generic 化 (但し「エラーが残存しています…N 件」の actionable message も generic 化されるため UX 後退あり、要確認)。
  - (B) `convert` の catch を残しつつ `e.getMessage()` ではなく特定パターン (`"エラーが残存しています"` プレフィックス) のみ通過、それ以外は generic 化。
  - (C) `MfTaxResolver` 専用例外 `MfMasterMisconfigurationException` を新設し、advisor で個別マッピング (推奨)。
- 重大度: Major (情報露出リスク + behavior asymmetry)。

### MJ-B02: SF-B12 `cleanExpired` の `cache.isEmpty()` 早期 return が synchronized 外
- 元 SAFE-FIX: SF-B12
- 場所: `backend/src/main/java/jp/co/oda32/domain/service/finance/CashBookConvertService.java:191-199`
- 問題:
  - `cleanExpired` 冒頭で `if (cache.isEmpty()) return;` を **synchronized ブロック外** で実行している。`cache` は `ConcurrentHashMap` なので `isEmpty()` 自体は安全だが、SF-B12 の本来の目的「`enforceCacheLimit` と同一ロックで保護」と表現として整合しない。
  - 別スレッドが `enforceCacheLimit` で put → `cleanExpired` が同時実行で entry を削除しようとする TOCTOU は、early return を「ロック外で見て空なら戻る」状態にしている限り完全には消えない (ロック取得から削除完了まで 0 件遷移を見落とす)。
  - 実害は cache 1 個分の delay でしかなく、機能的には問題ないが **コメント (`// SF-B12: ... 同一ロックで保護し add/evict の TOCTOU を回避`) が現実と乖離** (= 早期 return がロック外なので回避しきれていない)。
- 影響: 軽微 (5 分間 cache の clean delay が 1 サイクル遅れる程度)。
- 修正案:
  - (A) `if (cache.isEmpty()) return;` を `synchronized` ブロック内に移動。
  - (B) コメントを「最適化のため早期 return をロック外で行う」と現実に合わせ書換。
- 重大度: Major (コードコメントと実装の意図不整合 = 将来読み手の混乱)。

### MJ-G01: SF-G05 `FinancePeriodConfig.OPENING_DATE_DEFAULT` が完全に未参照、純粋なデッドコード
- 元 SAFE-FIX: SF-G05
- 場所: `backend/src/main/java/jp/co/oda32/constant/FinancePeriodConfig.java:31`
- 問題:
  - 新設された `FinancePeriodConfig.OPENING_DATE_DEFAULT` は **コードベース内で 0 箇所参照** (Grep 結果: claudedocs/* と本クラスの宣言のみ)。
  - triage SF-G05 の本文では「5 箇所統合」と書かれていたが、Goal 文章では「5 箇所統合は未着手 (重複定義のまま並行維持、Javadoc に一本化方針記載)」と明示されており、このギャップは認識されている。
  - しかし「並行維持」も実態は **`MfPeriodConstants.SELF_BACKFILL_START` だけが使われている / `FinancePeriodConfig.OPENING_DATE_DEFAULT` は誰も読まない**。並行ですらない。
  - フロント (`frontend/types/supplier-opening-balance.ts:85` の `DEFAULT_OPENING_DATE`) も新定数を使っていない (フロントは API endpoint がまだ無いので独立)。
- 影響:
  - 新規 import で間違った定数 (これ) を使う可能性。Javadoc は丁寧だが、IDE の auto-complete でユーザを誘導する可能性あり。
- 修正案:
  - (A) **削除** (YAGNI)。将来必要になった時点で導入。
  - (B) Javadoc を「現時点では未使用、将来の admin 上書き機能の予約定数」と明記し残す。
  - (C) `MfPeriodConstants.SELF_BACKFILL_START` を `FinancePeriodConfig.OPENING_DATE_DEFAULT` から派生させる (`SELF_BACKFILL_START = FinancePeriodConfig.OPENING_DATE_DEFAULT` 等) — これだと完全な並行維持になる。
- 重大度: Major (デッドコード = 将来の混乱源)。

### MJ-G02: SF-G01 適用後の `SupplierBalancesService` line 122-123 コメントが旧前提のまま
- 元 SAFE-FIX: SF-G01
- 場所: `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:121-126`
- 問題:
  - コメント:
    > "MF 側は journal #1 が accumulateMfJournals に含まれるため opening 相当が既に mf.credit 累積に入っている。"
  - これは SF-G01 で journal #1 を skip した **前** の状態を説明している。SF-G01 後は MF 側に opening は含まれない。
  - 同コメント line 230-234 (`accumulateMfJournals` 内) は SF-G01 修正と整合 (「期首残高仕訳 (journal #1) は m_supplier_opening_balance 経由で別途注入する」)。
- 影響: 将来読み手の混乱。CR-G01 の根本診断にも影響。
- 修正案: コメントを「SF-G01 で journal #1 を MF 側 accumulation から除外。opening は self/MF 両側で `m_supplier_opening_balance.effectiveBalance` 経由で注入する必要がある (但し本実装では self 側のみ注入、MF 側の opening 注入は未実装 = CR-G01 課題)」と書換。
- 重大度: Major (Critical CR-G01 の理解を妨げる)。

### MJ-G03: SF-G04 `SupplierOpeningBalanceController#list` の admin ロジックが他 Controller と非対称
- 元 SAFE-FIX: SF-G04
- 場所: `backend/src/main/java/jp/co/oda32/api/finance/SupplierOpeningBalanceController.java:34-45` + `LoginUserUtil.java:40-55`
- 問題:
  - SF-G04 で適用された判定: `if (effective != null && !effective.equals(shopNo)) throw AccessDeniedException`。
  - `LoginUserUtil.resolveEffectiveShopNo(shopNo)` は admin の場合 **リクエスト値をそのまま返す** (line 50-52)。なので admin が `shopNo=2` を投げると `effective=2 == requested=2` → アクセス許可。これは設計通り。
  - **しかし** 同 Controller の `fetchFromMf` (line 49) と `updateManualAdjustment` (line 60) は `@PreAuthorize("authentication.principal.shopNo == 0")` で **shopNo フィールド** を見る別パターン。つまり同一 Controller 内で:
    - `list`: admin は `LoginUserUtil` 経由で任意 shop OK
    - `fetchFromMf` / `updateManualAdjustment`: principal.shopNo == 0 の admin のみ
  - 一方 `BatchController` 等は `hasRole('ADMIN')` (`CompanyType.ADMIN` 由来)。この 3 系列の admin 判定 (`shopNo==0` vs `hasRole('ADMIN')` vs `LoginUserUtil`) が同一プロジェクト内で並存。
- 影響:
  - shop=0 と CompanyType=ADMIN が常に同期しているなら問題なし。しかし master データで両者がズレるケース (例: shop_no=1 のユーザに companyType=admin を付ける) で非対称な挙動を発生し得る。
- 修正案:
  - (A) admin 判定を 1 つに統一。`LoginUser` に `isAdmin()` メソッドを生やし、全 Controller で `@PreAuthorize("@loginUser.isAdmin()")` 化 (中期)。
  - (B) 短期は SF-G04 の判定を `hasRole('ADMIN')` ベースに揃える。`SupplierOpeningBalanceController#list` の non-admin 経路で `shopNo` 一致を検証。
- 重大度: Major (認可仕様の二系統並存 = 将来の権限事故リスク)。

### MJ-H01: SF-H08 `findLatestClosingDatePerShop()` Repository メソッド未追加 (NativeQuery 残存)
- 元 SAFE-FIX: SF-H08
- 場所:
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountingStatusService.java:83-105` (`queryInvoiceLatest` で NativeQuery 残存)
  - `backend/src/main/java/jp/co/oda32/domain/repository/finance/TInvoiceRepository.java` (新メソッド未追加)
- 問題:
  - triage SF-H08 は「`TInvoiceRepository#findLatestClosingDatePerShop()` を `@Query` で集約メソッドとして新設」と明示。
  - 実装は cashbook 側 (`TCashbookImportHistoryRepository.findFirstByOrderByProcessedAtDesc()` + 既存の `findAll(PageRequest)`) のみ Repository 化。invoice 側は **NativeQuery 直叩きが残ったまま**。
  - また `findFirstByOrderByProcessedAtDesc` は単件取得用なのに `queryCashbookHistory` は 3 件取得しており、結果として `findAll(PageRequest)` を直接 Service から呼んでいる。triage の「`findTop3ByOrderByProcessedAtDesc()`」とも乖離。
- 影響:
  - DRY 違反解消が中途半端。SF-H08 完了として記録されているが実態は半分。次回 SQL 修正時 (例: window 関数化) に Service 側を直す必要があり、管理コスト残存。
- 修正案:
  - (A) `TInvoiceRepository` に以下を追加:
    ```java
    @Query(nativeQuery = true, value = """
        SELECT i.shop_no, i.closing_date, COUNT(*) AS cnt
        FROM t_invoice i
        WHERE i.closing_date = (SELECT MAX(i2.closing_date) FROM t_invoice i2 WHERE i2.shop_no = i.shop_no)
        GROUP BY i.shop_no, i.closing_date
        ORDER BY i.shop_no
        """)
    List<Object[]> findLatestClosingDatePerShop();
    ```
  - (B) `TCashbookImportHistoryRepository#findTop3ByOrderByProcessedAtDesc()` を追加し `findAll(PageRequest)` 直叩きを置換。
- 重大度: Major (triage 不完全実装、コードレビュー記録の信頼性に影響)。

### MJ-H02: SF-H05 `BatchJobCatalog` が Bean ではなく `static final List` のため admin が画面で編集できない (将来の柔軟性後退)
- 元 SAFE-FIX: SF-H05
- 場所: `backend/src/main/java/jp/co/oda32/batch/BatchJobCatalog.java:40-60`
- 問題:
  - triage SF-H05 は「`BatchJobCatalog` enum」と書かれていたが、実装は record + `static final List<Entry>` (確かに enum より柔軟、これは Goal 1 で正解と確認済)。
  - **ただし**、List of Record にしたメリット (動的編集) は活かされていない。`@ConfigurationProperties` でも `@Bean` でもないため、application.yml からの上書きや admin UI 編集は不可能。
  - 「19 ジョブの追加/削除/属性変更が頻繁」という Goal 文章のニーズに対しては「コード変更 + 再起動」しか手段がない (= enum と等価)。
  - フロント側 `JOB_LABELS` (`accounting-workflow.tsx:61-70`) も依然ハードコードで、SF-H05 が言及していた「`/api/v1/batch/job-catalog` 取得」は未実装。3 重同期は 2 重同期に減ったが完全解消ではない。
- 影響: 中期。短期実害なし。
- 修正案:
  - (A) `BatchJobCatalog` を `@Component` 化し `application.yml` の `batch.jobs` プロパティから build (admin が直接編集する場合は `m_batch_job_catalog` テーブル化)。
  - (B) `GET /api/v1/batch/job-catalog` を新設し、フロントは `useQuery` で取得。`accounting-workflow.tsx` と `accounts-payable-ledger.tsx` の `JOB_LABELS` ハードコードを排除。
  - (C) 短期は現状維持 + Javadoc に「動的編集が必要になった時点で `@ConfigurationProperties` 化」と明記。
- 重大度: Major (Goal 1 の要件「頻繁な変更」を技術的に satisfy していない、ただし短期実害なし)。

---

## Minor

### MN-B01: SF-B11 の comment が POI 例外階層を誤認している可能性
- 元 SAFE-FIX: SF-B11
- 場所: `backend/src/main/java/jp/co/oda32/domain/service/finance/CashBookConvertService.java:71-81`
- 問題:
  - コメント line 72: 「POI 5.x: NotOfficeXmlFileException extends IllegalArgumentException、OpenXML4JRuntimeException extends RuntimeException として throw されるため」と記述。
  - 実際の POI 5.x では `NotOfficeXmlFileException` の継承階層は版によって変動する。POI 5.2.x では `extends OfficeXmlFileException extends IllegalArgumentException` で正しい。
  - catch 句は `IllegalArgumentException | OpenXML4JRuntimeException | ZipException` の 3 つを並列 catch しており、これは正しい。
- 影響: なし (動作は正しい、コメントの記述スタイルが軽い)。
- 修正案: コメントを「POI 5.2.x 環境で `IllegalArgumentException` (`NotOfficeXmlFileException` 含む) / `OpenXML4JRuntimeException` / `ZipException` を発生し得るためまとめて 400 翻訳」と簡潔化。
- 重大度: Minor (情報品質)。

### MN-B02: SF-B08 の Comparator は等価動作だが「同 priority + kw 不一致」の安定順序が暗黙
- 元 SAFE-FIX: SF-B08
- 場所: `backend/src/main/java/jp/co/oda32/domain/service/finance/CashBookConvertService.java:543-548`
- 問題:
  - 旧実装 `priority * 2 + kwBonus` は priority×2 がドミナント、kw bonus が tie-break。SF-B08 の `Comparator.comparingInt(priority).thenComparingInt(kwBonus)` は等価。
  - **ただし**、同 priority + 同 kw の場合、Java Stream の `min()` は「先勝ち」だが順序保証は List の入力順 (= `findByDelFlgOrderByDescriptionCAscPriorityAsc` の SQL ORDER BY)。secondary tie-break が暗黙。
  - 旧実装も同じ非決定性を持っていたので regression なし。だが Comparator 化したタイミングで `.thenComparing(MMfJournalRule::getId)` 等の明示的 tertiary を入れる機会はあった。
- 影響: 将来 `m_mf_journal_rule` に priority + descriptionD 重複行が入ると別ルールが選ばれる可能性 (現状シードでは発生しない)。
- 修正案: `.thenComparingInt(r -> r.getId())` を追加し決定的順序化。
- 重大度: Minor (現状実害なし)。

### MN-B03: Untracked debug JSON ファイルが repo に残存
- 元 SAFE-FIX: 範囲外
- 場所: `frontend/_int_rec.json`, `frontend/_mf_*.json`, `frontend/_mfj*.json`, `frontend/_review_post.json` (計 9 ファイル)
- 問題: 機密情報 (取引先名・金額) を含む可能性のある debug 用 fixture が untracked だが repo 内に存在。`.gitignore` に追加して remove するべき。
- 影響: 本人の確認後に削除可否判断、別タスク (DEF-BGH-08 と同根)。
- 修正案: `.gitignore` に `frontend/_*.json` を追加。
- 重大度: Minor (clean-up)。

### MN-G01: SF-G09 `parseAmount` の長音符 `ー` (U+30FC) を minus 扱いするのは過剰
- 元 SAFE-FIX: SF-G09
- 場所: `frontend/lib/utils.ts:51`
- 問題:
  - `.replace(/[－−ー]/g, '-')` で full-width minus (`－` U+FF0D) と minus sign (`−` U+2212) と長音符 (`ー` U+30FC) を半角ハイフンに置換。
  - 長音符 `ー` は本来「カタカナ語尾の伸ばし棒」であり、minus として入力されることは経理運用ではほぼ無い。誤判定リスク (例: ユーザが「ー1000円」と書いたつもりが本当は伸ばし棒のメモ) は低いが、対応の妥当性を Javadoc で言及していない。
  - また `parseAmount('1.5')` → 1.5 (float) を返す。supplier_opening_balance は yen-integer (`NUMERIC(15,0)`) なので、float が DB に渡ると round 扱いに依存。`Math.floor` / `Math.round` ガードがあると堅い。
- 影響: Minor (現実的な誤入力パターンなし、float→int は Hibernate 側で動作するが暗黙)。
- 修正案:
  - (A) `[ー]` を除外、`[－−]` のみ minus 扱い化。
  - (B) `Math.round(n)` を返り値に挟む (yen 整数化)。
- 重大度: Minor (堅牢性)。

### MN-G02: SF-G03 zombie 復活時に `addUserNo` / `addDateTime` 監査が更新されない
- 元 SAFE-FIX: SF-G03
- 場所: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java:158-168`
- 問題:
  - zombie 復活処理で `setDelFlg("0")` した後、`modify_user_no` / `modify_date_time` のみ更新。`add_user_no` / `add_date_time` は元のまま。
  - 設計的にはこれが正しい (元の作成者を保持) が、論理削除 → 復活した場合、誰がいつ復活させたか追跡したい場合は別カラム (例: `restored_at`, `restored_user_no`) が必要。
  - MEMORY.md `feature-supplier-opening-balance` には監査トレイル追加方針なし。軸 F (audit trail) が未着手なので保留が妥当。
- 影響: 監査要件次第。現状は問題なし。
- 修正案: 軸 F audit trail 実装時に `m_supplier_opening_balance_history` を新設し、del_flg 変化を記録。
- 重大度: Minor (将来の監査要件)。

### MN-H01: SF-H09 `slice(0, 10)` での timezone 取扱いが UTC 文字列前提
- 元 SAFE-FIX: SF-H09
- 場所: `frontend/components/pages/finance/accounting-workflow.tsx:128-132`
- 問題:
  - 関数 `isoDate(iso)` は `iso.slice(0, 10)` で `YYYY-MM-DD` を抽出。
  - backend `AccountingStatusResponse` の `processedAt` / `startTime` / `endTime` は `LocalDateTime.toString()` (= `2026-04-05T10:00:00.123` ISO local) で返される。timezone offset は付かないので `slice(0, 10)` で `2026-04-05` が抽出される (= 期待動作)。
  - しかし将来 backend が `Instant` (= `2026-04-05T01:00:00.000Z` UTC) を返すように変更すると、JST 経理担当が見たい日付 (2026-04-05 JST = 2026-04-04T16:00:00Z UTC) との 1 日ズレが発生。
  - Goal 4 で「date-fns 未インストールのため `slice(0, 10)` で代替」と認識されており、長期は date-fns 導入が前提。
- 影響: 現状実害なし (LocalDateTime 文字列固定)。将来 Instant 化したらバグ。
- 修正案: コメントに「`LocalDateTime.toString()` 形式 (timezone なし) 前提。Instant に変えたら `formatInTimeZone(parseISO(s), 'Asia/Tokyo', 'yyyy-MM-dd')` 化必須」と明記。
- 重大度: Minor (将来の retro 用)。

### MN-H02: SF-H06 `AccountingStatusResponse` の型は `frontend/types/finance.ts` ではなく component から re-export
- 元 SAFE-FIX: SF-H06
- 場所:
  - `frontend/components/pages/finance/accounting-workflow.tsx:515` (`export type { AccountingStatus, ... }`)
  - `frontend/e2e/finance-workflow.spec.ts:7` (`import type { AccountingStatus } from '@/components/pages/finance/accounting-workflow'`)
- 問題:
  - triage SF-H06 は「`frontend/types/finance.ts` (新規 / 既存に `AccountingStatus` 型切り出し)」と明示。
  - 実装は component ファイル (`accounting-workflow.tsx`) に型を inline 定義 + re-export。E2E spec は component 経由で import。
  - これは動作上は OK だが、Component file がデータ型のソース・オブ・トゥルースになっており、依存関係が逆転 (一般に type は components から独立した `types/` 配下に置く)。
  - また backend と frontend の型同期は手動。OpenAPI 自動生成は未導入。
- 影響: 軽微 (機能的には等価)。
- 修正案: 別途 `frontend/types/finance.ts` に切り出し、`accounting-workflow.tsx` は import するだけに変更。E2E も同経路で import。
- 重大度: Minor (構造)。

### MN-H03: SF-H05 `JOB_LABELS` のフロント側ハードコードと `BatchJobCatalog.shortLabel` の二重定義
- 元 SAFE-FIX: SF-H05
- 場所:
  - `frontend/components/pages/finance/accounting-workflow.tsx:61-70` (`JOB_LABELS`)
  - `backend/src/main/java/jp/co/oda32/batch/BatchJobCatalog.java:37` (`shortLabel`)
- 問題:
  - `BatchJobCatalog.Entry.shortLabel` は backend 側で定義済 (例: `accountsReceivableSummary` → `"売掛集計"`)。
  - フロント `JOB_LABELS` は同等のマップを独立に定義 (`accountsReceivableSummary: '売掛集計'`)。
  - 表記が一致している点は良いが、変更時の同期が手動。MJ-H02 の `/api/v1/batch/job-catalog` 配信エンドポイントが導入されればフロントは backend 値を取得して同期可能。
- 影響: 軽微 (両方とも 8 行のマップ、目視同期は可能)。
- 修正案: MJ-H02 の (B) 案と同時対応。
- 重大度: Minor (DRY 違反、SF-H05 の本来意図と整合)。

---

## SF-XXX 別の確認結果

### Cluster B
| SF | 状態 | 備考 |
|---|---|---|
| SF-B01 | △ 部分適用 | backend admin 化済、frontend `cashbook-import.tsx` の追加ボタン未対応 → CR-B01 |
| SF-B02 | ✅ 完全適用 | `mf-client-mappings.tsx:143-156` 確認 |
| SF-B03 | ✅ 完全適用 | `design-mf-cashbook-import.md:21-22, 167-169` CRLF 記載確認 |
| SF-B04 | ✅ 完全適用 | `design-mf-cashbook-import.md` 18 件統一、`V008__create_mf_cashbook_tables.sql:43` コメント修正 |
| SF-B05 | ✅ 完全適用 | `PURCHASE_AUTO_WIDE` 行追加、シード表 #15 / #19 修正 |
| SF-B06 | ✅ 完全適用 | `〜` U+301C 対応 + period 不在 saveHistory skip |
| SF-B07 | ✅ 完全適用 | `selectSheet` フォールバック撤去 |
| SF-B08 | ✅ 完全適用 | Comparator 化 (MN-B02 で軽い決定性指摘) |
| SF-B09 | ✅ 完全適用 | `cashbook-import.tsx:51-66` 死 invalidate 削除 + isPending guard |
| SF-B10 | △ MJ-B01 | `IllegalStateException` 化済、CashBookController local catch で advisor 経由化されていない |
| SF-B11 | ✅ 完全適用 | XSSF 例外 400 翻訳 (MN-B01 はコメント記述の軽微な指摘のみ) |
| SF-B12 | △ MJ-B02 | 適用されているが `cache.isEmpty()` early return がロック外 |
| SF-B13 | ✅ 完全適用 | `TaxResolver` literal union 型導入確認 |

### Cluster G
| SF | 状態 | 備考 |
|---|---|---|
| SF-G01 | △ CR-G01 | skip 追加済、ただし self/MF 非対称な opening 注入と既存 `accumulateSelf.opening` 加算の重複が懸念 |
| SF-G02 | ✅ 完全適用 | `@Generated(event = INSERT, UPDATE)` + EventType import 確認 (Hibernate 6.5.3 で動作) |
| SF-G03 | ✅ 完全適用 | zombie 復活 (`setDelFlg("0")`) + bulk fetch (SF-G06 と同 PR) |
| SF-G04 | ✅ 完全適用 (MJ-G03 補足) | shop 権限ガード fail-closed、ただし同 Controller 内で 2 系統認可並存 |
| SF-G05 | △ MJ-G01 | `FinancePeriodConfig` 新設のみ、参照 0 箇所 |
| SF-G06 | ✅ 完全適用 | `findByPkShopNoAndPkOpeningDate` (delFlg 無視) で N+1 解消 |
| SF-G07 | ✅ 完全適用 | `getEffectiveBalance()` 統一 3 箇所 (`list` / `getEffectiveBalanceMap` / `sumEffectiveBalance`) |
| SF-G08 | ✅ 完全適用 | `MfOpeningJournalDetector` util 新設、3 サービスから `MfJournalFetcher.isPayableOpeningJournal` 経由で参照 |
| SF-G09 | ✅ 完全適用 (MN-G01 補足) | `parseAmount` ヘルパ (`lib/utils.ts:43`) + 適用 (`supplier-opening-balance.tsx:115, 134`)、長音符吸収は過剰気味 |
| SF-G10 | ✅ 完全適用 | 設計書 `design-supplier-opening-balance.md` 整合 |

### Cluster H
| SF | 状態 | 備考 |
|---|---|---|
| SF-H01 | ✅ 完全適用 | 相関サブクエリでショップ別 MAX 化 (NativeQuery 残存は MJ-H01) |
| SF-H02 | ✅ 完全適用 | backend `BatchJobCatalog` で `accountsReceivableSummary` `monitoredInWorkflow=true workflowStep=4`、frontend `RECEIVABLE_JOB_NAMES` に追加 |
| SF-H03 | ✅ 完全適用 | `useQuery` error/pending 分岐 + refresh ボタン (`accounting-workflow.tsx:369-415`) |
| SF-H04 | ✅ 完全適用 (MJ-G03 関連) | `@PreAuthorize("hasRole('ADMIN')")` 適用、ただし shopNo==0 系統との非対称残存 |
| SF-H05 | △ MJ-H02 | `BatchJobCatalog` 新設 + `BatchController.JOB_DEFINITIONS` 撤去、ただし「動的変更可能化」は未達 |
| SF-H06 | ✅ 完全適用 (MN-H02 補足) | `AccountingStatusResponse` record 新設 + 戻り型変更、frontend 型同期は component re-export |
| SF-H07 | ✅ 完全適用 | `SQLGrammarException` 再 throw + `IllegalStateException` 化 |
| SF-H08 | △ MJ-H01 | cashbook 側のみ Repository 化、invoice 側 NativeQuery 残存 |
| SF-H09 | ✅ 完全適用 (MN-H01 補足) | `slice(0, 10)` 代替実装、useMemo 削除、key 改善 |
| SF-H10 | ✅ 完全適用 | E2E `MOCK_STATUS: AccountingStatus` 型付け |
| SF-H11 | ✅ 完全適用 (MN-H03 関連) | `THEME_TOKENS` map で Tailwind 動的クラス回避 |
| SF-H12 | ✅ 完全適用 (確認のみ) | 設計書 `design-accounting-workflow.md` 修正済 (差分は別途確認) |

---

## 横断的観点の評価

### API Breaking change (SF-H06) のフロント影響
- **影響: 軽微で対処済**。Frontend agent が `AccountingStatus` 型を `accounting-workflow.tsx` から `export type` で公開し、E2E spec も `import type { AccountingStatus } from '@/components/pages/finance/accounting-workflow'` で型同期している。
- 残課題は MN-H02 の「型を `frontend/types/finance.ts` に切り出すべき (構造)」のみ。
- backend → frontend の同期は依然手動。OpenAPI 自動生成はプロジェクト全体の中期課題。

### `@Generated(event = INSERT, UPDATE)` (SF-G02) の動作評価
- **動作前提: 成立する見込み (Hibernate 6.5.3 + PostgreSQL 17)**。
  - Hibernate 6 系の `@Generated(event = ...)` は内部的に INSERT / UPDATE 文の `RETURNING` 句に対象列を追加し、戻り値を entity にロードする。PostgreSQL は `RETURNING` をネイティブサポートしているため遅延・タイミング問題は起こらない。
  - `effective_balance` 列は `GENERATED ALWAYS AS (COALESCE(mf_balance, 0) + manual_adjustment) STORED` で、INSERT/UPDATE 後に DB が即座に再計算 → `RETURNING` で戻り値取得 → entity reflectoin → 後続の `e.getEffectiveBalance()` で fresh value を返す。
  - SF-G07 の前提 (「UPDATE 後 `getEffectiveBalance()` が stale を返さない」) は成立。triage Goal 文章の懸念「5 秒遅延」は不要 (RETURNING は同期)。
- ただし**実 DB / 起動時の動作確認は必須**: 単体テストで `repository.save(entity)` → `entity.getEffectiveBalance()` を assert する unit test 推奨 (MEMORY.md feedback ルール)。

### SF-G05 の重複管理評価
- `FinancePeriodConfig` 新設は **形だけ**。実態は `MfPeriodConstants` が引き続き全箇所で使われており、新定数は import 0 箇所のデッドコード (MJ-G01)。
- triage Goal 4 で「重複定義のまま並行維持、Javadoc に一本化方針記載」と認識されているが、**並行ですらない (片側 0 参照)**。

### 3 新規 service / DTO ファイルの lifecycle
- `BatchJobCatalog`: `static final List<Entry>` で Bean 化なし → DI 不要、Spring container に登録されない。テスト容易性は OK。
- `MfOpeningJournalDetector`: `final class` + `private constructor` の static util → 同上。
- `AccountingStatusResponse`: record + 子 record 3 つ。Jackson は record をネイティブサポート (Spring Boot 3.x) するため JSON 出力は `{cashbookHistory: [...], smilePurchaseLatestDate: "...", ...}` の camelCase。Test 環境影響なし。

### CompanyType vs shopNo の admin 二系統 (横断的)
- 既存の認可仕様乱立。SF-G04 / SF-H04 / SF-B01 はそれぞれ別パターン (`LoginUserUtil`, `hasRole('ADMIN')`, `authentication.principal.shopNo == 0`) を採用。
- 本クラスタ修正で新たに導入されたわけではない (既存の inconsistency)。整理は別 sprint。MJ-G03 で言及。

---

## 推奨アクション

### 即時対応 (本 sprint 内)
1. **CR-B01 (Critical)**: `cashbook-import.tsx:159` 「マッピング追加」ボタンを `{isAdmin && ...}` でラップ。最小修正 5 行。
2. **CR-G01 (Critical)**: 実 DB で `/finance/supplier-balances?shopNo=1&asOfMonth=2026-04-20` の `summary.totalDiff` を測定。改修前後で大きく動いていれば修正案 (A) または (B) を採択。動かなければ MJ-G02 のコメント書換のみで FIX。
3. **MJ-G02**: `SupplierBalancesService.java:121-126` のコメントを SF-G01 整合に書換 (5 分作業)。

### 中期対応 (次 sprint)
4. **MJ-B01**: `MfMasterMisconfigurationException` 専用例外 + advisor 個別マッピング。
5. **MJ-B02**: `cleanExpired` の `cache.isEmpty()` 早期 return をロック内に移動 (1 行修正)。
6. **MJ-G01**: `FinancePeriodConfig.OPENING_DATE_DEFAULT` を削除 (YAGNI) または `MfPeriodConstants.SELF_BACKFILL_START` から派生化。
7. **MJ-H01**: `TInvoiceRepository#findLatestClosingDatePerShop()` 追加 + `queryInvoiceLatest` を Repository 経由化。
8. **MJ-H02 (B)**: `/api/v1/batch/job-catalog` endpoint 新設 + フロント `JOB_LABELS` 排除。MN-H03 と同時解消。

### 長期対応 (DD-BGH-* 以降の sprint)
9. **MJ-G03**: admin 認可判定を `LoginUser#isAdmin()` 等に統一。
10. **MN-G02**: 軸 F audit trail 実装と連動。
11. **MN-H01**: date-fns 導入後に `slice(0, 10)` を `formatInTimeZone` 化。

### ドキュメント整備
12. **MN-B01**: SF-B11 のコメント簡潔化。
13. **MN-B03**: `frontend/_*.json` debug fixture を `.gitignore` 追加 + `git rm`。
14. **MN-G01**: `parseAmount` Javadoc に「長音符吸収は過剰気味」 / `Math.round` 推奨を追記。

### Goal 達成度
| 重点レビュー項目 | 達成度 |
|---|---|
| Cluster B 認可整合 (B01/B02) | 部分達成 (CR-B01) |
| POI 例外階層整合 (B06/B07/B08/B11/B12) | 達成 (MJ-B02 軽微) |
| `MfTaxResolver` 例外型 (B10) | 部分達成 (MJ-B01) |
| literal union 型 (B13) | 達成 |
| journal #1 skip 統一 (G01) | 部分達成 (CR-G01) |
| `@Generated` event 拡張 (G02) | 達成 |
| zombie 復活 (G03) | 達成 |
| fail-closed (G04) | 達成 (MJ-G03 軽微) |
| N+1 解消 (G06) | 達成 |
| `getEffectiveBalance()` 統一 (G07) | 達成 |
| `MfOpeningJournalDetector` 集約 (G08) | 達成 |
| `parseAmount` ヘルパ (G09) | 達成 (MN-G01 軽微) |
| `invoiceLatest` SQL 修正 (H01) | 達成 (MJ-H01 軽微) |
| `accountsReceivableSummary` 追加 (H02) | 達成 |
| useQuery error/pending (H03) | 達成 |
| `getAccountingStatus` admin (H04) | 達成 |
| `BatchJobCatalog` 集約 (H05) | 部分達成 (MJ-H02) |
| `AccountingStatusResponse` record (H06) | 達成 (MN-H02 軽微) |
| silent failure 修正 (H07) | 達成 |
| Repository 経由化 (H08) | 部分達成 (MJ-H01) |
| date-fns 代替 (H09) | 達成 (MN-H01 軽微) |
| `MOCK_STATUS` 型付け (H10) | 達成 |
| Tailwind theme (H11) | 達成 |

**総合**: 35 件中 27 件完全達成、6 件部分達成、2 件方針確定後対応 (DD-BGH-01)。Critical 2 件 + Major 7 件は本 sprint 内に修正推奨。
