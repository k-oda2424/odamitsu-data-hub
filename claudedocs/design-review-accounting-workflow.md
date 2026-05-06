# 設計レビュー: 経理ワークフロー (Cluster H)

レビュー日: 2026-05-04
対象設計書: `claudedocs/design-accounting-workflow.md` (Phase 1 で逆生成)
対象ブランチ: `refactor/code-review-fixes`
レビュアー: Opus サブエージェント

---

## サマリー

- 総指摘件数: **Critical 2 / Major 7 / Minor 6** (合計 15 件)
- 承認状態: **Needs Revision**

設計書 Phase 1 サマリで指摘された 5 論点 (NativeQuery 直叩き / ジョブ名手動同期 / payment-mf-import 欠落 / 自動判定不在 / staleTime+refresh ボタン無し) はいずれも妥当な指摘。本レビューでは設計書未指摘の追加事項として、以下 3 点を Critical/Major で抽出した:

1. **(Critical)** `t_invoice` の NativeQuery が `closing_date` 全テーブル MAX を使うため、過去月の単一店舗実行が他店舗を覆い隠す業務バグの可能性
2. **(Critical)** `BatchController#JOB_DEFINITIONS` (ジョブ名の第3の真実) と `AccountingStatusService` の IN-list と Frontend の `BatchChip.names` が **3重の手動同期** になっており、設計書は「2重」としか把握していない
3. **(Major)** 既存の `TCashbookImportHistoryRepository` / `TAccountsPayableSummaryRepository#findLatestTransactionMonth` が利用可能なのに NativeQuery を再実装している (DRY/Layer 違反)

---

## Critical 指摘

### C-1: invoice 最新締日 SQL がショップを跨いで MAX を取るため業務バグの可能性

- **箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountingStatusService.java:60-63`
  ```sql
  SELECT shop_no, closing_date, COUNT(*) as cnt FROM t_invoice
  WHERE closing_date = (SELECT MAX(closing_date) FROM t_invoice)
  GROUP BY shop_no, closing_date ORDER BY shop_no
  ```
- **問題**: 「全テーブルの MAX(closing_date)」と一致するショップしか出力されない。例えばショップ1が 2026-04-30 締めまで取込済 / ショップ2 が 2026-04-20 締めまでしか取込んでいない状況で、画面には**ショップ1のみ表示され、ショップ2は配列から消える**。経理担当はステップ3の Chip を見て「全店舗最新まで取込済」と誤認するリスクがある (画面側 `accounting-workflow.tsx:164-170` は「配列が空なら未実行、空でなければ件数表示」しかしないため、欠落を検知できない)。
- **影響**: 設計書 §3.2 / §5.4 では `invoiceLatest` を「ショップ別の最新締日」と説明しているが、実装は「全店舗共通の最新締日に該当するショップ別件数」であり**設計書と実装の意味が乖離**している。月跨ぎの取込抜けが Chip からは判別できない。
- **修正案**:
  ```sql
  SELECT shop_no, MAX(closing_date) as latest, COUNT(*) FILTER (WHERE closing_date = MAX...)
  FROM t_invoice GROUP BY shop_no
  ```
  または相関サブクエリで「ショップ毎の MAX」を取り、件数は最新締日のレコードのみを数える。最終的に `TInvoiceRepository` に `@Query` で集約メソッドを追加し、`Map<Integer, InvoiceLatestRow>` を返す DTO 化を推奨。

### C-2: ジョブ名が**3箇所**で手動同期 (設計書は2箇所までしか把握していない)

- **箇所**:
  1. `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountingStatusService.java:85-87` (NativeQuery `IN ('purchaseFileImport',...,'salesJournalIntegration')` 7件)
  2. `frontend/components/pages/finance/accounting-workflow.tsx:60-68` (`BatchChip.names` ラベルマップ 7件)
  3. `backend/src/main/java/jp/co/oda32/api/batch/BatchController.java:34-54` (`JOB_DEFINITIONS` 19件、`category`/`description`/`requiresShopNo` 付き)
- **問題**: 設計書 §7.5 は「**完全に手動同期**」と書いているが、対象として挙げているのは (1) と (2) のみ。実際には `BatchController` の `JOB_DEFINITIONS` にも同名・同責務のメタデータが書かれており、3箇所で重複している。さらに `BatchController` は `description` (例: 「SMILE仕入ファイル取込」) を持っているのに、Frontend `BatchChip.names` は独自の短縮ラベル (「仕入ファイル」) を再定義している。
- **影響**: 新ジョブ追加時に 3 ファイルを手動更新する必要があり、漏れが発生しやすい。実例として `accountsReceivableSummary` / `accountsPayableBackfill` / `goodsFileImport` などは `BatchController` には登録されているがワークフロー監視対象には入っていない (設計書 §3.3 注記の通り)。組織的に「監視対象に追加すべきジョブ」を判断する単一ソースが存在しない。
- **修正案**:
  1. `AccountingStatusService` の IN-list を撤去し、`BatchController.JOB_DEFINITIONS` を共通の `BatchJobCatalog` に切り出し (enum or `@ConfigurationProperties`)。
  2. `BatchJobCatalog` のエントリに `monitoredInWorkflow: boolean` と `workflowStep: int` と `shortLabel: String` を持たせ、`AccountingStatusService` はそこから IN-list を生成。
  3. Frontend は `/api/v1/batch/job-catalog` で取得 (現状 `BatchController` には list 系エンドポイントがあるはず — 既存の活用)。

---

## Major 指摘

### M-1: `Map<String, Object>` 戻り値による型安全性欠如

- **箇所**: `AccountingStatusService.java:21` (`public Map<String, Object> getStatus()`), `FinanceController.java:506-509`
- **問題**: 戻り値が `LinkedHashMap`、内部の `cashbookHistory` も `List<Map<String,Object>>`。`row[3] (rowCount)` は JDBC ドライバ依存で `BigInteger` / `Long` / `Integer` になり、`row[4]/row[5] (totalIncome/totalPayment)` は `BigDecimal`。Frontend `accounting-workflow.tsx:26-28` は `number` で受けているため、`BigDecimal.toString()` が `"12345678.00"` のような小数形式で来た場合に表示崩れする可能性がある。
- **影響**: 型ズレが Backend のテストで検出されず、Frontend 表示時に初めて顕在化する。OpenAPI/Swagger ドキュメンテーションも `Map` のまま生成されて型情報が欠落する。
- **修正案**: 専用 DTO `AccountingStatusResponse` (record) + 子レコード `CashbookHistoryRow`, `InvoiceLatestRow`, `BatchJobStatus` を作成。`@Schema` で OpenAPI 用注釈も付与。

### M-2: `EntityManager` 直叩き — Layer 違反

- **箇所**: `AccountingStatusService.java:17-18`, `34-106`
- **問題**: CLAUDE.md 規約「Controller は薄く、ビジネスロジックは Service 層に委譲」に対して Service が永続層を直接呼んでいる。さらに既存の Repository (`TCashbookImportHistoryRepository`, `TAccountsPayableSummaryRepository`, `TInvoiceRepository`) を経由していない。
- **影響**: 既存 Repository に集約クエリを足す動機が失われ、似たような NativeQuery が複数 Service に散らばるリスク。テストでも `EntityManager` のモックが必要になり、`@DataJpaTest` の Repository テストパターンから外れる。
- **修正案**: 各テーブル参照は対応する Spring Data JPA Repository の `@Query` に集約し、Service はそれを束ねるだけにする。詳細は M-3 / M-7 参照。

### M-3: 既存 Repository メソッドを再実装 (DRY 違反)

- **箇所**: `AccountingStatusService.java:25-28` (NativeQuery 4本) vs 既存メソッド
- **問題**: 以下の通り、既に Repository に同じ責務のメソッドがある:
  - `TCashbookImportHistoryRepository.java:10` `findFirstByOrderByProcessedAtDesc()` — 1件しか返さないので 3 件版を追加する程度で済む
  - `TAccountsPayableSummaryRepository.java:81` `findLatestTransactionMonth(Integer shopNo)` — **ショップ別**で取得可能。NativeQuery 版はショップ無視で全店舗 MAX を取っている
- **影響**: ショップ別最新月を取る機能が既に提供されているにもかかわらず、ワークフロー画面は全店舗 MAX のみ表示。ショップ毎の進捗が見えない。
- **修正案**: 既存 Repository を `@RequiredArgsConstructor` で注入し、必要なら以下を追加:
  - `TCashbookImportHistoryRepository#findTop3ByOrderByProcessedAtDesc()`
  - `TInvoiceRepository#findLatestClosingDatePerShop()` (`@Query` で集約)
  - `BatchJobExecutionRepository#findLatestPerJobName(Set<String>)` (Spring Batch JPA があれば)

### M-4: `try / catch (Exception)` フェイルセーフが障害を隠蔽する

- **箇所**: `AccountingStatusService.java:51-54, 72-75, 102-105, 112-114`
- **問題**: テーブル不在や SQL エラー時に WARN ログを出して空 List / null を返す。設計書 §3.1 では「フェイルセーフ設計」と肯定的に書かれているが、これは:
  - DB 接続障害でも 200 OK が返る (画面は「未実行」表示で正常に見える)
  - スキーマ変更による SQL 破綻が監視画面で気付けない
  - `querySingle` 内 `catch (Exception e)` (`L:112`) はログすら出さずに null を返す (silent failure)
- **影響**: 経理担当が「ステップ未実行」と誤認して再実行しても、実際は SQL バグなら何度実行しても表示されない。本番障害時の MTTR が伸びる。
- **修正案**: `querySingle` の catch も `log.warn` + 例外詳細を出す。SQLException と SQLGrammarException など想定外を区別し、本番では `@Profile("!prod")` のときだけ吞む。または `@Retryable` + サーキットブレーカー。

### M-5: バッチジョブ最新取得 SQL のパフォーマンス問題

- **箇所**: `AccountingStatusService.java:81-92`
- **問題**: 監視対象 7 ジョブに対し、`WHERE je.create_time = (SELECT MAX(je2.create_time) FROM ... WHERE ji2.job_name = ji.job_name)` という相関サブクエリを発行。`batch_job_execution` の規模が大きくなると `O(N²)` 的に劣化する可能性がある (`(job_name, create_time)` の複合インデックスがあれば緩和されるが、Spring Batch 5.x の標準スキーマには通常無い)。
- **影響**: 日次バッチの実行履歴が数万件溜まると、ワークフロー画面の表示が遅くなる。Backend で staleTime: Infinity にしているのも、これが裏の理由かもしれない。
- **修正案**: `ROW_NUMBER() OVER (PARTITION BY ji.job_name ORDER BY je.create_time DESC)` の Window 関数で書き直し (PostgreSQL のため OK)。あるいは Spring Batch の `JobExplorer.getJobInstances(jobName, 0, 1)` を 7 回呼ぶ方が、メタデータ用に index 最適化されており高速かもしれない。

### M-6: `staleTime: Infinity` + 手動 refresh 不在は業務影響大

- **箇所**: `accounting-workflow.tsx:265`
- **問題**: 設計書 §7.3 / §8.1 で TODO として書かれているが、Severity 評価が無い。経理担当は「画面開きっぱなし → バッチ起動 → 完了確認」のフローで使うため、refresh が無いと**今やった操作の結果が見えない**。タブを閉じて再度開く運用になり、操作ストレスが高い。
- **影響**: ステップ完了の自動判定 (M-9) が無いため、経理担当がワークフローを進める唯一の手段は「Chip を見る」ことなのに、その Chip が古いまま固定される。
- **修正案**:
  - `useQuery` の `staleTime` を 30 秒程度にし、`refetchOnWindowFocus: true` を追加
  - 手動 refresh ボタンを `PageHeader` 右側に配置 (`queryClient.invalidateQueries(['accounting-status'])`)
  - バッチ起動後に `/finance/workflow` に戻ったとき自動で refetch (router 遷移検知)

### M-7: `@Transactional(readOnly = true)` の宣言位置がメソッド単位

- **箇所**: `AccountingStatusService.java:20`
- **問題**: メソッドアノテーションは正しいが、内部でフェイルセーフ catch しているため、テーブル不在での `EntityNotFoundException` が発生したとき**トランザクション境界の rollback-only 設定**が走り、その後の `querySingle` 系も影響を受ける可能性がある (RuntimeException でない例外なら回避可能だが、JPA は通常 `PersistenceException`/`SQLGrammarException` を投げるためグレーゾーン)。
- **影響**: 1本目の SQL がエラーになると、それ以降の SQL も rollback マークされたトランザクションで実行され、結果が一斉に空になるリスク。
- **修正案**: クラスレベルに `@Transactional(readOnly = true)` を付け、各 private メソッドを `@Transactional(propagation = REQUIRES_NEW, readOnly = true)` で隔離するか、もしくは Repository 経由に切り替えて Repository 側のデフォルト readOnly トランザクションに任せる。

---

## Minor 指摘

### m-1: `payment-mf-import` がワークフローに未組み込み

- **箇所**: 設計書 §6.1 / §8.1, `accounting-workflow.tsx:98-215`
- **問題**: 設計書も TODO として認識しているが、5 ステップのうちステップ 5「買掛金集計・連携」に**含めるか別ステップ 6 にするか**の方針が未決定。MEMORY.md `feature-payment-mf-import.md` によれば 5日払い・20日払いの実運用で使う重要工程。
- **影響**: 5日払いの振込明細処理が画面から完全に見えない。
- **修正案**: ステップ 5 の前段に「ステップ 4.5: 振込明細 Excel 取込 (5日/20日払い)」を新設。`/finance/payment-mf-import` へのリンクと、`t_payment_mf_import_history` (既に Repository あり) ベースの状態 Chip を表示。

### m-2: `BatchChip` の `endTime` 未利用

- **箇所**: `accounting-workflow.tsx:59-81`
- **問題**: `endTime` を Backend から返しているのに UI で表示していない。
- **影響**: 失敗ジョブが `STARTED` のまま固まっているのか、`FAILED` で終了したのか判別できない。
- **修正案**: `endTime` がある場合は `(時:分)` 表示、無い場合は `(進行中)` 表示。FAILED の場合は `endTime` のみ表示。

### m-3: `StatusChip` の `warn` プロパティが死にコード

- **箇所**: `accounting-workflow.tsx:40-57`
- **問題**: `warn?: boolean` を受け取って琥珀色を出せる設計だが、`warn={true}` で呼んでいる箇所が無い (設計書 §5.5 でも「将来用フック」と記載)。
- **影響**: YAGNI 違反。読み手に「何かしら未実装の判定ロジックがあるはず」と誤読させる。
- **修正案**: 即座に削除するか、ステップ完了の自動判定 (M-9) と一緒に実装する。

### m-4: ジョブ名・ステップ・スケジュールがフロント全ハードコード

- **箇所**: `accounting-workflow.tsx:98-256` (`makeSteps()` 5件、`schedule[]` 4件)
- **問題**: 締日 (15日 / 20日 / 月末) や実施日 (22日頃 / 27日頃) の業務知識がフロントに埋め込まれている。
- **影響**: 締日変更や新店舗追加時にフロント修正・再デプロイが必要。
- **修正案**: 中期的には `m_accounting_schedule` マスタ化 (設計書 §8.2 にも記載)。短期的には `frontend/lib/finance/workflow-schedule.ts` に分離して責務を分ける。

### m-5: `accounting-workflow.tsx` 363行 — CLAUDE.md「200-400行」上限の上端

- **箇所**: `accounting-workflow.tsx` (全 363 行)
- **問題**: グローバル規約の上限内だが、`StatusChip` / `BatchChip` / `WorkflowStep` interface / `makeSteps()` / `schedule` / 本体コンポーネントが 1 ファイルに同居。
- **影響**: 今後 m-1 でステップ追加、M-6 で refresh ボタン追加、M-9 で完了判定追加するとあっという間に 400 行超え。
- **修正案**: `components/pages/finance/workflow/` ディレクトリに `WorkflowStepCard.tsx` / `ScheduleCard.tsx` / `chips.tsx` / `steps.ts` / `schedule.ts` を分割。

### m-6: 認可がショップ単位フィルタを持たない

- **箇所**: `FinanceController.java:77` クラス `@PreAuthorize("isAuthenticated()")`, `FinanceController.java:506-509`
- **問題**: 経理担当は通常本社 (shopNo=0 admin) のみだが、各店舗ユーザでもこのエンドポイントは叩け、他店舗の最新仕入日まで見える。
- **影響**: 設計書 §7.2 で「全ショップの最新状態が見える」ことは認識されているが、Severity 評価が無い。情報統制上のリスク。
- **修正案**: `@PreAuthorize("hasAuthority('ROLE_ADMIN') or principal.shopNo == 0")` を `getAccountingStatus` に追加。または `?shopNo=` クエリを受けて自分のショップ分のみ返す。

---

## 設計書 vs 実装の乖離

| # | 設計書記述 | 実装の実態 | 重要度 |
|---|---|---|---|
| D-1 | §3.2 `invoiceLatest` を「ショップ別の最新締日」と表現 | 実装は「全店舗共通の最新締日 (`SELECT MAX(closing_date) FROM t_invoice` のサブクエリ) に該当するショップ別件数」 (`AccountingStatusService.java:60-63`)。**ショップ別では無い** | Critical (C-1) |
| D-2 | §7.5 ジョブ名同期は「完全に手動同期」(2箇所のみ言及) | 実態は `BatchController.JOB_DEFINITIONS` を含む **3 箇所同期** (`BatchController.java:34-54` が第 3 の真実) | Critical (C-2) |
| D-3 | §7.1 「テーブル不在時も例外を呑んでログだけ出す」 | `querySingle` (`AccountingStatusService.java:108-115`) は **ログすら出さない silent failure** | Major (M-4) |
| D-4 | §3.1 「JPA Entity は経由せず…純粋なリードオンリー集約」 | `TCashbookImportHistoryRepository` 等は既に存在 (`backend/src/main/java/jp/co/oda32/domain/repository/finance/`)。「経由しない」ではなく「経由する選択肢を見送った」が正確 | Minor |
| D-5 | §3.2 監視対象7ジョブに `accountsReceivableSummary` 不在 | `BatchController.java:50` には登録済み。設計書 §8.3 の「TODO: 確認」事項だが、現状 「売掛金集計」 ジョブ自体は存在 | Minor |
| D-6 | §4.1 「`@PreAuthorize("isAuthenticated()")` (クラス継承)」 | クラスアノテーション (`FinanceController.java:77`) は確認済みだが、ショップ別フィルタ無し (m-6) の Severity が未評価 | Minor |

---

## レビューチェックリスト結果

### Spring Boot 観点

| 項目 | 結果 | 備考 |
|---|---|---|
| SQL Injection リスク (NativeQuery) | OK | パラメータ無しの定数 SQL のみ。文字列連結に外部入力は混じらない |
| EntityManager 直叩き | NG | M-2 / M-3 (既存 Repository の活用未) |
| DTO 化 | NG | M-1 (`Map<String, Object>` のまま) |
| Layer 違反 | NG | Service が永続層責務を持っている |
| @Transactional 設定 | 要改善 | M-7 (rollback 境界が catch と相性悪) |
| DI / コンストラクタ注入 | OK | `@PersistenceContext` は標準。他は `@RequiredArgsConstructor` |
| Migration | N/A | 本機能は読み取り専用、新規 Migration なし |
| 例外ハンドリング | NG | M-4 (silent failure) |
| パフォーマンス (相関サブクエリ) | 要改善 | M-5 |
| OpenAPI スキーマ | NG | DTO 未化のため `Map` がそのまま出る |

### ワークフロー固有観点

| 項目 | 結果 | 備考 |
|---|---|---|
| ステップ網羅性 (5 ステップ) | NG | m-1 (`payment-mf-import` 欠落) |
| 自動完了判定 | NG | 設計書 §3.4 / §8.1 で TODO。Chip は最終実行日時のみで完了/未完了判定なし。経理担当の人的チェックに完全依存 |
| ジョブ名同期保守性 | NG | C-2 (3 重同期) |
| 月度クローズ機能 | NG | 設計書 §7.4 で TODO。月次完了マークが無いため「2026年4月度は完了したか」を画面から判定不能 |
| staleTime + refresh ボタン | NG | M-6 (画面開きっぱなしで最新化されない) |
| `payment-mf-import` 組み込み | NG | m-1 |
| ショップ別フィルタ | NG | m-6 (情報統制) + C-1 (店舗ごとの取込抜け検知不能) |
| サブコンポーネント分離 | 要改善 | m-5 (363 行で上端) |

### Frontend 観点

| 項目 | 結果 | 備考 |
|---|---|---|
| TypeScript 型定義 | 要改善 | M-1 連動。Backend が DTO 化されれば自動生成可能 |
| useQuery 設定 | NG | M-6 (staleTime: Infinity) |
| 死にコード | NG | m-3 (`warn` プロパティ未使用) |
| ハードコード | 要改善 | m-4 (スケジュール・締日) |
| アクセシビリティ | OK | `lucide-react` アイコン + テキストラベル併用 |

---

## 推奨対応順序

優先度順に実装すれば本画面は短期的に運用可能なレベルに到達する:

1. **(Critical) C-1**: `invoiceLatest` をショップ別 `MAX(closing_date)` に修正 — 1〜2時間。バグ修正
2. **(Critical) C-2 + Major M-1/M-2/M-3**: `BatchJobCatalog` 集約 + `AccountingStatusResponse` DTO 化 + Repository 経由化 — 半日。リファクタ
3. **(Major) M-6**: refresh ボタン追加 + `staleTime: 30000` + `refetchOnWindowFocus` — 30分
4. **(Major) M-4**: `querySingle` の log と例外区別 — 30分
5. **(Minor) m-1**: `payment-mf-import` をステップ 4.5 として組み込み — 半日
6. **(Major) M-5**: バッチ最新取得を Window 関数 or `JobExplorer` 化 — 1時間 (パフォーマンス問題が顕在化したとき)
7. その他 m-2 〜 m-6 は機会あり次第

月度クローズ・ステップ完了自動判定 (設計書 §8.1 TODO) は設計合意が必要なため、本レビューでは Severity 評価のみとし対応順序からは除外。

---

## 付録: 引用元ファイル一覧

- 設計書: `claudedocs/design-accounting-workflow.md`
- Service: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountingStatusService.java` (120 行)
- Controller: `backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java:506-509`
- Batch Controller: `backend/src/main/java/jp/co/oda32/api/batch/BatchController.java:34-54` (ジョブ名第3ソース)
- Frontend Page: `frontend/app/(authenticated)/finance/workflow/page.tsx`
- Frontend Component: `frontend/components/pages/finance/accounting-workflow.tsx` (363 行)
- 既存 Repository:
  - `backend/src/main/java/jp/co/oda32/domain/repository/finance/TCashbookImportHistoryRepository.java`
  - `backend/src/main/java/jp/co/oda32/domain/repository/finance/TAccountsPayableSummaryRepository.java:81`
  - `backend/src/main/java/jp/co/oda32/domain/repository/finance/TInvoiceRepository.java`
  - `backend/src/main/java/jp/co/oda32/domain/repository/finance/TPaymentMfImportHistoryRepository.java`
