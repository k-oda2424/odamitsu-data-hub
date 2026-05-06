# コードレビュー: 経理ワークフロー (Cluster H)

レビュー日: 2026-05-04
ブランチ: `refactor/code-review-fixes`
レビュアー: Opus サブエージェント (code-review subagent)
対象設計レビュー: `claudedocs/design-review-accounting-workflow.md`

---

## サマリー

- 新規指摘件数: **Blocker 0 / Critical 1 / Major 4 / Minor 6** (合計 11 件)
- 承認状態: **Needs Revision**

設計レビュー (Critical 2 / Major 7 / Minor 6) で挙がった事項は**再掲しない**。本レビューはコード固有の追加事項のみ。

特に新発見:

1. **(Critical) C-impl-1**: `BatchController#getJobStatus` (`BatchController.java:142-170`) の `JobExplorer` 経由ロジックと、`AccountingStatusService#queryBatchJobs` の NativeQuery 経由ロジックは**同一テーブルを別経路で読みに行っている**。設計レビュー C-2 が指摘した「ジョブ名定義の3重化」に加え、実装にも**読み取り経路の2重化**が存在する (相関サブクエリ vs JobExplorer)。同一機能が二度実装されている。
2. **(Major) M-impl-1**: 設計レビュー D-2 で指摘した「設計書の `accountsReceivableSummary` 不在」と整合的に、**`accountsReceivableSummary` (`BatchController.java:50` に登録済)** がワークフロー画面ステップ4「売掛金MF連携」 (`accounting-workflow.tsx:174-190`) では `salesJournalIntegration` のみ表示で、**売掛金集計バッチの状態が画面に出ない**。「売掛金集計→売上仕訳CSV」の前段が不可視。

---

## Blocker
該当なし。

## Critical

### C-impl-1: バッチ状態取得が `BatchController` と `AccountingStatusService` で二重実装
- **箇所 (1)**: `backend/src/main/java/jp/co/oda32/api/batch/BatchController.java:142-170` (`getJobStatus`)
  ```java
  var instances = jobExplorer.findJobInstancesByJobName(jobName, 0, 1);
  ...
  var latest = executions.stream()
          .sorted(Comparator.comparing(JobExecution::getCreateTime, ...).reversed())
          .findFirst().orElse(executions.get(0));
  ```
- **箇所 (2)**: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountingStatusService.java:81-92` (`queryBatchJobs`)
  ```java
  entityManager.createNativeQuery(
      "SELECT ji.job_name, je.status, je.exit_code, je.start_time, je.end_time " +
      "FROM batch_job_execution je " +
      "JOIN batch_job_instance ji ... " +
      "WHERE ji.job_name IN ('purchaseFileImport', ..., 'salesJournalIntegration') " +
      "AND je.create_time = (SELECT MAX(je2.create_time) FROM ... WHERE ji2.job_name = ji.job_name)")
  ```
- **問題**: 「最新の `JobExecution` を取得する」という同じ責務を、片や Spring Batch 標準の `JobExplorer` で実装、片や Native SQL の相関サブクエリで実装している。両者で:
  - **NULL handling が異なる** (`BatchController` は `nullsFirst`、Native SQL は `MAX` で NULL 値を無視)。`create_time` が NULL の execution があると **同じ jobName に対して結果が食い違う**
  - **status/exitCode の意味が異なる** (`BatchController` は `BatchStatus#toString()` (例: `STARTED`)、Service は `je.status` 列の文字列直読み — Hibernate/PostgreSQL の Enum 列マッピングは同等だが、将来 Spring Batch 6 で列名/型が変わると Service だけ壊れる)
  - **メタデータスキーマ変更耐性が無い** — Spring Batch の internal table はバージョン間で変わる (5.0 で `parameter_name` が追加された等)。`JobExplorer` API は安定保証されている
- **影響**: 経理担当が `/finance/workflow` で見るチップと `/batch` 画面で見るチップが乖離する可能性。Spring Batch 6 への移行時に Native SQL 側だけサイレントに壊れる
- **修正案**: `AccountingStatusService` から Native SQL を撤去し、`JobExplorer.findJobInstancesByJobName(jobName, 0, 1)` を 7 ジョブ分ループする実装に統一。設計レビュー M-5 (Window 関数化提案) は不要になり、設計レビュー C-2 (3重同期) と C-impl-1 が同じリファクタで解消できる

---

## Major

### M-impl-1: ステップ4「売掛金MF連携」に `accountsReceivableSummary` が含まれていない
- **箇所**: `frontend/components/pages/finance/accounting-workflow.tsx:186-189`
  ```tsx
  statusRenderer: (s) => {
    const job = s.batchJobs.find(j => j.jobName === 'salesJournalIntegration')
    return job ? <BatchChip job={job} /> : <StatusChip label="売上仕訳CSV" value={null} />
  },
  ```
- **問題**: `BatchController.JOB_DEFINITIONS` (`BatchController.java:50`) には `accountsReceivableSummary` (売掛金サマリ) が登録されており、業務上は「売掛金集計 → 売上仕訳CSV」の順で実行する。しかしワークフロー画面のステップ4は CSV 出力ジョブのみ表示しており、**前段の集計バッチが完了したかどうか画面から分からない**。設計レビュー D-2 (「§3.2 監視対象7ジョブに `accountsReceivableSummary` 不在」) は設計書の不備として記載されているが、実装画面でも欠落している
- **影響**: 「ステップ4 完了」マークが (将来実装されたとして) 売掛金集計の完了を保証しない。買掛側 (ステップ5) は `accountsPayableAggregation`/`Verification`/`Summary` を全て監視しているのに、売掛側だけ片手落ちで対称性が崩れる
- **修正案**:
  1. `AccountingStatusService.java:85-87` の IN-list に `accountsReceivableSummary` を追加
  2. `BatchChip.names` (`accounting-workflow.tsx:60-68`) に `accountsReceivableSummary: '売掛集計'` を追加
  3. ステップ4の `statusRenderer` に `.filter(j => ['accountsReceivableSummary','salesJournalIntegration'].includes(j.jobName))` を適用
  4. (設計レビュー C-2 の `BatchJobCatalog` 集約と一緒にやれば1箇所で完了)

### M-impl-2: フロントの TypeScript 型と Backend `Object` 戻り値が暗黙連動
- **箇所**: `frontend/components/pages/finance/accounting-workflow.tsx:22-38` vs `AccountingStatusService.java:21`
  ```ts
  interface CashbookHistory {
    rowCount: number
    totalIncome: number
    totalPayment: number
  }
  ```
  Backend 側は `m.put("rowCount", row[3])` (生の `Object` を put、`BigInteger` の可能性)、`m.put("totalIncome", row[4])` (`BigDecimal`)
- **問題**: 設計レビュー M-1 は「DTO 化が必要」と指摘済みだがフロント側の型ミスマッチに踏み込んでいない。`BigDecimal` は Jackson のデフォルトで `12345678.00` のように小数付き string/number で出る可能性があり、TypeScript `number` 受けで `0.00` の小数点が表示崩れする。`row[3]` は PostgreSQL の `count(*)` 由来で BigInt → JSON では `12345` の数値リテラルになるが、JDBC 実装次第で Long/Integer/BigInteger が混在する
- **影響**: テスト時 (`finance-workflow.spec.ts:11-13`) は `rowCount: 42` の数値で渡しているのでモックでは問題が顕在化しない。本番で BigDecimal が `"500000.00"` の文字列で来た場合、`(${h.rowCount}件)` 表示は問題ないが、合計金額表示を将来追加した時に fractional 桁が出る
- **修正案**:
  - 設計レビュー M-1 の DTO 化 (`AccountingStatusResponse` record) と同時に、`@JsonFormat(shape = JsonFormat.Shape.NUMBER)` + `BigDecimal#stripTrailingZeros()` または `Long` への明示変換を行う
  - フロント `types/finance.ts` に共通型を切り出し、テストモックでも同型を使うようにする (mock-api.ts の typed fixture 化)

### M-impl-3: `useQuery` に loading/error UI 分岐が無く、初回フェッチ失敗が黙殺される
- **箇所**: `frontend/components/pages/finance/accounting-workflow.tsx:262-268, 326`
  ```tsx
  const statusQuery = useQuery({...})
  const status = statusQuery.data
  ...
  {status && s.statusRenderer && (...)}
  ```
- **問題**: `statusQuery.isError` / `statusQuery.isPending` の分岐が無い。`status` が `undefined` のときは「statusRenderer 自体を呼ばない」だけなので、ステップ枠は描画されるが**チップが何も出ない状態が永続化**する。エラー時にユーザは「未実行なのか取得失敗なのか」を判別できない (設計レビュー M-4 で Backend の silent failure を指摘済みだが、フロント側にも silent failure ある)
- **影響**: バックエンドが 500 を返した場合、画面は「全ステップ未実行」と等価な見た目になり、経理担当が再実行操作を始める誤誘導につながる。さらに refresh ボタン (設計レビュー M-6) が無いので、エラーから自動回復もできない
- **修正案**:
  ```tsx
  if (statusQuery.isError) {
    return <Alert variant="destructive">...再試行ボタン</Alert>
  }
  // 各 statusRenderer 内で status が null のときは <Skeleton /> を出す
  ```
  または PageHeader 右側に `statusQuery.isError && <RefreshCw />` を出す

### M-impl-4: `getAccountingStatus` エンドポイントに `@PreAuthorize` 個別宣言が無い
- **箇所**: `backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java:506-509` (クラス: `:77` `@PreAuthorize("isAuthenticated()")`)
  ```java
  @GetMapping("/accounting-status")
  public ResponseEntity<Map<String, Object>> getAccountingStatus() {
      return ResponseEntity.ok(accountingStatusService.getStatus());
  }
  ```
- **問題**: 同じ Controller 内でも `BatchController` 系は `@PreAuthorize("hasRole('ADMIN')")` で保護されている (`BatchController.java:126, 143, 173`)。一方、経理ワークフロー API はクラス継承の `isAuthenticated()` のみで、**ログイン済みなら全店舗ユーザが他店舗の最新データを取得可能**。設計レビュー m-6 と方向性は同じだが、Controller 既存パターンとの不一致 (Spring Batch 系は ADMIN 限定なのに、それを覗き見る側は限定なし) は実装固有の論点
- **影響**: ステップ2「SMILE仕入取込」(全店舗の `MAX(denpyou_hizuke)`)、ステップ5「買掛金サマリ最新月」 (全店舗の `MAX(transaction_month)`) などが店舗ユーザに漏洩。MEMORY.md `feature-accounts-payable.md` で言う「経理担当 = 本社 admin (shopNo=0)」想定と合致しない
- **修正案**:
  ```java
  @GetMapping("/accounting-status")
  @PreAuthorize("hasRole('ADMIN')")  // BatchController と同等
  public ResponseEntity<Map<String, Object>> getAccountingStatus() { ... }
  ```
  または `principal.shopNo == 0` を条件追加

---

## Minor

### m-impl-1: `useMemo` で `makeSteps()` をラップする意味が無い
- **箇所**: `frontend/components/pages/finance/accounting-workflow.tsx:260`
  ```tsx
  const workflowSteps = useMemo(() => makeSteps(), [])
  ```
- **問題**: `makeSteps()` は引数を取らず JSX `icon` を持つ静的な配列。依存配列が空なのでマウント時 1 回だけ実行され、毎レンダー再計算は発生しない。ただし JSX (`<BookOpen />` 等) を含むため`makeSteps` 自体をモジュールトップレベルの `const` にできず `useMemo` 化している、というのは **過度な微小最適化** で意図不明
- **影響**: コード読み手に「なぜここで memo するのか?」と考えさせる。`useMemo` は単独では負荷を生じないが、依存忘れリスクのほうが大きい
- **修正案**: `makeSteps()` を呼び出さず、`const workflowSteps = makeSteps()` をコンポーネント関数の冒頭で直接呼ぶ。あるいは設計レビュー m-5 に従いファイル分割するときに `getWorkflowSteps()` をモジュール定数化

### m-impl-2: `key` に index を使っている
- **箇所**: `frontend/components/pages/finance/accounting-workflow.tsx:117-123`
  ```tsx
  {s.cashbookHistory.map((h, i) => (
    <StatusChip key={i} ... />
  ))}
  ```
- **問題**: 履歴 3 件は `processedAt` で順序が変わるので、index key だと React の reconciliation が壊れる可能性がある
- **修正案**: `key={h.processedAt ?? i}` または `key={h.fileName ?? h.periodLabel ?? i}`

### m-impl-3: `processedAt.split('T')[0].split('.')[0]` の二重 split
- **箇所**: `accounting-workflow.tsx:121`
  ```tsx
  value={h.processedAt ? h.processedAt.split('T')[0].split('.')[0] + ` (${h.rowCount}件)` : null}
  ```
- **問題**: `split('T')[0]` で日付部分のみ取った後さらに `split('.')[0]` するのは二度手間 (日付部分にドットは入らない)。書き手が「`2026-04-05T10:00:00.123` の milliseconds を落とそうとしたが、その前に T で split している」コピペミスと推測
- **影響**: 動作上の問題は無い (no-op) が、コードの意図が読めない
- **修正案**: `format(parseISO(h.processedAt), 'yyyy-MM-dd')` (date-fns は既に依存済み)

### m-impl-4: `WorkflowStep` interface の `color/bgLight/bgIcon/borderColor` が Tailwind クラス文字列直書き
- **箇所**: `accounting-workflow.tsx:83-96, 107-110, 137-139, 158-161, 184-185, 203-205`
- **問題**: ステップごとに 4 種類の Tailwind クラスを `'text-amber-700'` / `'bg-amber-50'` / `'bg-amber-100'` / `'border-amber-200'` のように手動展開。ステップ追加 (設計レビュー m-1: payment-mf-import) のたびに 4 文字列セットをコピーする必要がある
- **影響**: タイポしても TypeScript で気付けない (`'text-ambr-700'` でも any 文字列 OK)。Tailwind の JIT がステップごとに別クラスとして生成されるため bundle が膨らむ
- **修正案**: `colorTheme: 'amber' | 'sky' | 'violet' | 'emerald' | 'rose' | 'blue'` 型に絞り、ヘルパー関数 `getStepColors(theme)` で展開する。または `cva` (class-variance-authority) を使う

### m-impl-5: NativeQuery が Repository 命名規約と不整合 (Service 内に SQL リテラル)
- **箇所**: `AccountingStatusService.java:25-28, 37-40, 60-63, 81-91`
- **問題**: 設計レビュー M-2/M-3 で Repository 経由化を提案済みだが、実装の追加観点として**SQL リテラルを `String` 連結で組み立てている** (`AccountingStatusService.java:81-91`、9 行に渡る `+` 連結)。CLAUDE.md の Java 21 環境では Text Block (`"""`) が使えるため可読性が改善できる
- **影響**: 文字列リテラルの保守性 (改行、IN-list 修正)
- **修正案**: Repository 化が本筋だが、暫定対応として Text Block 化:
  ```java
  String sql = """
      SELECT ji.job_name, je.status, je.exit_code, je.start_time, je.end_time
      FROM batch_job_execution je
      JOIN batch_job_instance ji ON je.job_instance_id = ji.job_instance_id
      WHERE ji.job_name IN (:jobNames)
        AND je.create_time = (SELECT MAX(je2.create_time) ...)
      """;
  ```
  さらに IN-list は `setParameter("jobNames", ...)` でパラメータ化 (現状ハードコード文字列のためパラメータ化されていない — SQL Injection はパラメータ無しで安全だが、将来「shopNo フィルタ追加」時に文字列連結をしないようにテンプレート化しておく)

### m-impl-6: テスト fixture (`MOCK_STATUS`) が型注釈なしで Backend 実装と乖離検知できない
- **箇所**: `frontend/e2e/finance-workflow.spec.ts:5-24`
  ```ts
  const MOCK_STATUS = {
    cashbookHistory: [...],
    invoiceLatest: [{ shopNo: 1, closingDate: '2026-03-31', count: 15 }],
    ...
  }
  ```
- **問題**: `MOCK_STATUS` に型注釈がないため、`AccountingStatus` interface (`accounting-workflow.tsx:31-38`) を変更しても E2E テストが落ちず、コンパイル時にズレが検出できない
- **影響**: 設計レビュー C-1 を修正して `invoiceLatest` を「ショップ別 MAX」構造に変えたとき、E2E は古い fixture のまま PASS してしまう (MEMORY.md `feedback_incremental_review` で挙がっている「モック PASS だけでなく実バックエンド疎通を最低 1 パス」と整合)
- **修正案**: `frontend/types/finance.ts` に `AccountingStatus` を切り出し、`const MOCK_STATUS: AccountingStatus = {...}` と型付け。Backend DTO 化 (設計 M-1) と一緒にやれば openapi-typescript で自動生成も可能

---

## 設計レビュー指摘との対応マトリクス

設計レビュー指摘のうち、コード固有実装でさらに深堀りする論点:

| 設計レビュー ID | 本レビューでの追加発見 |
|---|---|
| C-1 (invoice MAX バグ) | E2E モック (`MOCK_STATUS.invoiceLatest`) が単一ショップのみで、修正時の回帰検知不可 → m-impl-6 |
| C-2 (3重ジョブ名同期) | さらに `BatchController.getJobStatus` (`JobExplorer` 経由) と `AccountingStatusService` (Native SQL 経由) で**読み取り経路まで2重化** → C-impl-1 |
| M-1 (DTO 未化) | フロント側 `interface AccountingStatus` に `BigDecimal` の数値表示問題が連鎖 → M-impl-2 |
| M-2/M-3 (Repository 未活用) | Service 内 SQL がリテラル連結で Java 21 Text Block 未活用 → m-impl-5 |
| M-4 (silent failure backend) | フロント側にも `useQuery` の error/loading 分岐欠落 (silent failure) → M-impl-3 |
| M-6 (refresh ボタン無し) | エラー時の手動回復手段が完全にゼロ → M-impl-3 で同時解決 |
| m-6 (ショップフィルタ無し) | Controller 内で `BatchController` が ADMIN 限定なのに、それを覗くこの API が isAuthenticated 止まり (パターン不一致) → M-impl-4 |
| D-2 (`accountsReceivableSummary` 不在) | 画面ステップ4にも未追加で前段集計が不可視 → M-impl-1 |

---

## レビューチェックリスト結果 (Workflow 固有)

| 項目 | 結果 | 備考 |
|---|---|---|
| NativeQuery SQL Injection | OK | パラメータ無し定数 SQL のみ。設計レビューで OK 判定済み |
| `querySingle` silent failure | NG | 設計レビュー M-4 で指摘済み、本レビュー追加なし |
| `@Transactional` 配置 | NG | 設計レビュー M-7 で指摘済み |
| 相関サブクエリパフォーマンス | NG | 設計レビュー M-5 で指摘済み + 本レビュー C-impl-1 (JobExplorer 化で同時解決) |
| ショップフィルタ実装漏れ | NG | 設計レビュー C-1/m-6 + 本レビュー M-impl-4 (PreAuthorize) |
| `BatchChip` ジョブ名定義整合性 | NG | 設計レビュー C-2 + 本レビュー M-impl-1 (`accountsReceivableSummary` 漏れ) |

| 項目 (Spring Boot) | 結果 | 備考 |
|---|---|---|
| `JobExplorer` API 利用 | NG | C-impl-1 (BatchController と二重実装) |
| Java 21 Text Block 活用 | NG | m-impl-5 |
| `@PreAuthorize` 一貫性 | NG | M-impl-4 (BatchController と非対称) |
| Entity Listener / Audit | N/A | 読み取り専用 API |

| 項目 (Next.js) | 結果 | 備考 |
|---|---|---|
| `useQuery` error/pending 分岐 | NG | M-impl-3 |
| `useMemo` 適切性 | 要改善 | m-impl-1 |
| `key` の安定性 | 要改善 | m-impl-2 |
| TypeScript 型注釈 | NG | M-impl-2 + m-impl-6 (E2E 型なし) |
| Tailwind クラス安全性 | 要改善 | m-impl-4 |
| date 整形 | 要改善 | m-impl-3 (date-fns 未使用) |

---

## 推奨対応順序 (本レビュー追加分のみ)

設計レビュー対応と並行して以下を実施するのが効率的:

1. **(Critical) C-impl-1 + 設計 C-2 + 設計 M-5**: `BatchJobCatalog` enum + `JobExplorer` 経由化を一括実装。Native SQL 撤去。3つの Critical/Major が同時解消 (推定: 2-3時間)
2. **(Major) M-impl-1**: `accountsReceivableSummary` をワークフローステップ4に追加。`BatchJobCatalog` 修正と一緒に (30分)
3. **(Major) M-impl-3**: `useQuery` error/pending 分岐 + 設計 M-6 (refresh ボタン) を同時実装 (1時間)
4. **(Major) M-impl-4**: `@PreAuthorize("hasRole('ADMIN')")` 追加 (5分)
5. **(Major) M-impl-2 + 設計 M-1**: DTO 化 + フロント `types/finance.ts` 切り出し (半日)
6. **m-impl-1〜6**: 設計 m-5 (ファイル分割) のリファクタ機会で同時対応

---

## 付録: 引用元ファイル一覧

- 設計レビュー: `claudedocs/design-review-accounting-workflow.md`
- Service: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountingStatusService.java` (120 行)
- Controller (経理ワークフロー): `backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java:506-509` (クラス `:74-79`)
- Controller (バッチ・ジョブ名第3ソース): `backend/src/main/java/jp/co/oda32/api/batch/BatchController.java:34-54, 142-170`
- Frontend Page: `frontend/app/(authenticated)/finance/workflow/page.tsx`
- Frontend Component: `frontend/components/pages/finance/accounting-workflow.tsx` (363 行)
- Frontend E2E: `frontend/e2e/finance-workflow.spec.ts`
- 既存 Repository (経由化候補):
  - `backend/src/main/java/jp/co/oda32/domain/repository/finance/TCashbookImportHistoryRepository.java` (`findFirstByOrderByProcessedAtDesc` あり、3件版を追加で済む)
  - `backend/src/main/java/jp/co/oda32/domain/repository/finance/TAccountsPayableSummaryRepository.java:80-81` (`findLatestTransactionMonth(shopNo)` ショップ別取得済み)
