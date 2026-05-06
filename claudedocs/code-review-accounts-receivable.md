# コードレビュー: 売掛金 (Cluster E)

レビュー日: 2026-05-04
ブランチ: `refactor/code-review-fixes`
レビュアー: code-reviewer subagent (Opus)
対象設計レビュー: `claudedocs/design-review-accounts-receivable.md` (Critical 2 / Major 8 / Minor 6)

## 前提

設計レビュー指摘 (C-1, C-2, M-1〜M-8, m-1〜m-6, S-1〜S-12, D-1〜D-5) と重複する内容は本レビューで再掲しない。
本レビューはコード固有 (実装パターン、ライブラリ用法、AOP 連携、フロント TS、エンコーディング、Tasklet 仕様の不整合) に絞る。

## サマリー

- 総指摘件数: **Blocker 0 / Critical 3 / Major 5 / Minor 8**
- 承認状態: **Needs Revision**
- 新発見トップ:
  1. (C-impl-1) `markExported` が「`taxIncludedAmount` が null のときだけコピー」の片側更新で、**1度焼かれた行を再 DL すると `*_change` が変わっていても古い値で CSV 出力される**。設計レビュー C-1 の「失敗時マーカー焼き」とは別軸の、**経年バグ** (verify→DL→aggregate→DL の二回目 DL でサイレント不一致)。
  2. (C-impl-2) `AccountsReceivableCutoffReconciler` が cash-on-delivery (`cutoffDate=-1`) のレコードを再集計対象に含める設計だが、再集計後の `cutoffDate` を `30` (月末) で上書きしてしまう。**運用者から見ると「都度現金払い」だった行が黙って「月末締め」表示に化け**、後続の `InvoiceVerifier#formatClosingDateForSearch` が "末" を引くため検索は通るが、UI 表示と実マスタが乖離する。
  3. (C-impl-3) `AccountsReceivableToSalesJournalTasklet` の `FileWriter` がプラットフォームデフォルトエンコーディングで CSV を書く。Controller 側は `CP932` 指定なのに **バッチ起動経由の CSV だけ Windows 環境では MS932、Linux 環境では UTF-8 で出力される**ため、本番環境次第で MF 取込が文字化け。

---

## Blocker

なし。

---

## Critical

### C-impl-1: `markExported` が片側更新で再 DL 時に古い値を凍結する
- **箇所**:
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/SalesJournalCsvService.java:227-236`
  - `backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java:287-290`
- **コード**:
  ```java
  // SalesJournalCsvService.java:228-235
  public void markExported(List<TAccountsReceivableSummary> summaries) {
      for (TAccountsReceivableSummary s : summaries) {
          if (s.getTaxIncludedAmount() == null && s.getTaxIncludedAmountChange() != null) {
              s.setTaxIncludedAmount(s.getTaxIncludedAmountChange());
          }
          if (s.getTaxExcludedAmount() == null && s.getTaxExcludedAmountChange() != null) {
              s.setTaxExcludedAmount(s.getTaxExcludedAmountChange());
          }
      }
  }
  ```
  ```java
  // SalesJournalCsvService.java:87-89 (writeCsv)
  BigDecimal taxIncluded = summary.getTaxIncludedAmount() != null
          ? summary.getTaxIncludedAmount()
          : summary.getTaxIncludedAmountChange();
  ```
- **問題**:
  1. `applyMatched` (`InvoiceVerifier.java:353-354`) で一致行は既に `taxIncludedAmount = taxIncludedAmountChange` 焼き済み。markExported は **null guard のため no-op**。
  2. 運用者が `aggregate` を再実行 → tasklet (`TAccountsReceivableSummaryTasklet.java:248-249`) で `taxIncludedAmountChange` が新値に更新。**`taxIncludedAmount` は据え置き** (verifiedManually でなくても)。
  3. 再度 CSV を DL → `writeCsv` は古い `taxIncludedAmount` を使う。**MF に出る金額と AR テーブルの最新集計が乖離**。
  4. 設計レビュー C-1 (失敗時マーカー焼き) と組み合わせると、ネットワーク切断 → 半分焼き → 再集計 → 再 DL の経路で「半端な凍結値で MF 連携」が確定。
- **影響**: 会計連携の金額不整合。設計レビュー M-5 が指摘した「按分後の `*_change` を上書き」する仕様と組み合わせ、行の値が二重に書き換わる。
- **修正案**:
  - `markExported` を「**常に上書き**」に変更し、null チェックを撤去。あるいは、
  - `applyMatched` で `taxIncludedAmount` を焼かないよう変更し (`InvoiceVerifier.java:353-354` を削除)、CSV DL 時にのみ markExported で焼くワンパス化。設計書 §5.3 の「二段焼き」設計意図と整合させる。

### C-impl-2: Reconciler が cash-on-delivery 行の `cutoffDate=-1` を `30` に黙って上書き
- **箇所**: `backend/src/main/java/jp/co/oda32/batch/finance/service/AccountsReceivableCutoffReconciler.java:236-246, 360-373`
- **コード**:
  ```java
  // parseClosingDate "末" 解釈 (line 236-240)
  if ("末".equals(dayPart)) {
      LocalDate end = ym.atEndOfMonth();
      LocalDate start = ym.atDay(1);
      return Optional.of(new ClosingDateInfo(end, 30, start, end)); // cutoffCode=30 固定
  }
  ```
  ```java
  // aggregateForPartner (line 361-373)
  result.add(TAccountsReceivableSummary.builder()
          .cutoffDate(closing.cutoffCode)   // ← cash-on-delivery の -1 が消える
          ...
          .build());
  ```
- **問題**:
  1. `isExcludedPartner` (`AccountsReceivableCutoffReconciler.java:283-289`) は partnerCode (上様/Clean Lab/≥7桁) のみ除外する。`PaymentType.CASH_ON_DELIVERY` (`m_partner.cutoff_date = -1`) の partner は除外されず、Reconciler の対象に入る。
  2. 該当 partner の AR は `transaction_month=月末日` で保存済 (tasklet `processMonthEndCutoffPartners`)。Reconciler は同じく "末" 請求書を引いてきて `expectedTxMonth=月末日` を作る → AR の transaction_month と一致 → スキップされる。**通常運用ではバグが顕在化しない**。
  3. 一度でも transaction_month がズレた行 (例: 旧バッチで月初日に保存されていたデータ移行直後) が混じると Reconciler が走り、新行を `cutoffDate=30` で INSERT。**元の `-1`（都度現金払い）情報がロスト**し、UI 表示は「月末」になる。
  4. 後続の verify 経路で `InvoiceVerifier#formatClosingDateForSearch` が `cutoffDate=30` を `MONTH_END` 扱い ("末" 検索) するため動作はするが、運用者から見ると「マスタ上は -1 の partner なのに AR の `cutoff_date` 列が 30」となり、トレーサビリティ破綻。
- **影響**: マスタ vs AR の定義不整合。集計区分のレポート (`processMonthEndCutoffPartners` で都度現金払いの件数を別カウントするロジックに依拠する集計) が崩れる。
- **修正案**:
  - Reconciler が新行を作る際、対象 partner の元のマスタ `cutoff_date` を保持して書き戻す (`PartnerIndex` から `m_partner.getCutoffDate()` を引く)。
  - もしくは `isExcludedPartner` に `PaymentType.fromCutoffCode(partnerCutoffDate) == CASH_ON_DELIVERY` 判定を加え、cash-on-delivery を Reconciler 対象から除外。設計書 (`§AccountsReceivableCutoffReconciler` クラスコメント `line 50` `「都度現金払い (master cutoff_date = -1): 元から月次集約なので再集計不要」`) と実装が乖離している。

### C-impl-3: バッチ Tasklet の `FileWriter` がプラットフォーム既定エンコーディング
- **箇所**: `backend/src/main/java/jp/co/oda32/batch/finance/AccountsReceivableToSalesJournalTasklet.java:74,81`
- **コード**:
  ```java
  try (FileWriter writer = new FileWriter(fileName)) {
      salesJournalCsvService.writeCsv(summaries, writer, initialTransactionNo);
  }
  ```
- **問題**:
  - `FileWriter(String)` (Java 21) はプラットフォーム既定 charset を使用 (Windows 11 = `MS932`、Linux = `UTF-8`)。
  - Controller 経由 (`AccountsReceivableController.java:282`) は `OutputStreamWriter(baos, CP932)` で明示。**バッチ vs Controller で CSV のエンコーディングが分岐**。
  - 本番が Linux (推測) なら UTF-8 出力 → MF 取込で日本語列 (摘要・補助科目) が文字化け。
- **影響**: バッチを cron 実行している環境で MF 取込失敗。手動 DL 経由は通るが運用者は気付きにくい。
- **修正案**:
  - `new OutputStreamWriter(new FileOutputStream(fileName), Charset.forName("windows-31j"))` に置換。CP932 定数を `SalesJournalCsvService` または共通 util に集約 (現状 `AccountsReceivableController.java:68` のみ定義、買掛側 `FinanceController.java` も別定義)。

---

## Major

### M-impl-1: Controller `@RequestMapping("/api/v1/finance/accounts-receivable")` が JobLauncher を直接持ち、責務肥大
- **箇所**: `backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java:75-79, 120-167`
- **問題**:
  - Controller が `JobLauncher` / `ApplicationContext` / `@Qualifier("batchTaskExecutor") ThreadPoolTaskExecutor` を直接 DI して `aggregate` ハンドラ内で 30 行のジョブ起動コード (Bean lookup → JobParametersBuilder → executor.submit) を組み立てている。
  - 同パターンが買掛側 `FinanceController` にも散見され、本来は `BatchJobLauncherService` 等の共通 Service に集約すべき (CLAUDE.md `Controller は薄く` ルール違反)。
  - ApplicationContext からの Bean lookup (`applicationContext.getBean(beanName)`) はテスタビリティを下げる。
- **修正案**:
  - `BatchJobLauncherService.launchAccountsReceivableSummary(targetDate, cutoffType)` を新設し、Bean 解決と非同期 submit をカプセル化。Controller は薄く委譲。

### M-impl-2: `aggregate` の `cutoffType` 検証ロジックがマジックリテラル
- **箇所**: `backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java:130-137`
- **コード**:
  ```java
  if (!Set.of("all", "15", "20", "month_end").contains(cutoffType)) { ... }
  ```
- **問題**:
  - tasklet 側に `TAccountsReceivableSummaryTasklet.CUTOFF_TYPE_*` 定数が既に存在 (line 63-66) しているのに、Controller では文字列リテラル列挙。タイポ・追加忘れの温床。
  - `AccountsReceivableAggregateRequest.java:18` の Javadoc にも `"all" | "15" | "20" | "month_end"` がコメント記述のみで型保証なし。
- **修正案**:
  - `enum CutoffType { ALL("all"), DAY_15("15"), DAY_20("20"), MONTH_END("month_end") }` を定義し、Request DTO の `@Pattern` または Spring `Converter` で受ける。tasklet 側の文字列定数も同 enum に統合。

### M-impl-3: PathVariable で `boolean` を受けるエンドポイント設計
- **箇所**: `backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java:213, 232, 247`
- **コード**:
  ```java
  @PutMapping("/{shopNo}/{partnerNo}/{transactionMonth}/{taxRate}/{isOtakeGarbageBag}/verify")
  public ResponseEntity<AccountsReceivableResponse> verify(
          @PathVariable boolean isOtakeGarbageBag, ...) { ... }
  ```
- **問題**:
  - URL に boolean が混じる REST 設計はアンチパターン。`true`/`false` 以外の任意の文字列 (`yes`, `1`, `True`) で挙動がブレる (Spring の `Boolean.parseBoolean` は `"true"` 以外を全て `false`)。
  - PK が 5 要素 (shopNo / partnerNo / transactionMonth / taxRate / isOtakeGarbageBag) で、URL 階層が深すぎ (5 階層) て可読性も悪い。
  - 買掛側 `AccountsPayableController` は同様パターンか確認すべき (対称性の観点で)。
- **修正案**:
  - クエリパラメータ `?isOtakeGarbageBag=true` に変更、PK を `shopNo/partnerNo/transactionMonth/taxRate` の 4 階層に減らす。
  - もしくは、PK を `partnerCode-yyyymmdd-taxRate-otake` のような単一複合キー文字列にエンコードし、URL `/verify/{compositeKey}` 形式へ。

### M-impl-4: Reconciler が `MPartner.cutoffDate` を `Integer` で扱い null を `null` のまま `PartnerKey` 解決
- **箇所**: `backend/src/main/java/jp/co/oda32/batch/finance/service/AccountsReceivableCutoffReconciler.java:217-225`
- **コード**:
  ```java
  private Optional<TInvoice> findInvoiceForPartner(Integer shopNo, String partnerCode, YearMonth ym) {
      String yyyymm = String.format("%d/%02d", ym.getYear(), ym.getMonthValue());
      String[] candidates = {yyyymm + "/末", yyyymm + "/20", yyyymm + "/15"};
      for (String cd : candidates) {
          Optional<TInvoice> inv = tInvoiceService.findByShopNoAndPartnerCodeAndClosingDate(shopNo, partnerCode, cd);
          if (inv.isPresent()) return inv;
      }
      return Optional.empty();
  }
  ```
- **問題**:
  - 検索を 3 種類順次実行 (N+1 ではないが 3×N partner クエリ)。M-1 で指摘されたパフォーマンス劣化の追加証跡。
  - **3 候補とも見つかった場合、最初にヒットした "末" を採用する**。マスタ `cutoff_date` が "20" なのに偶然 "末" 請求書も登録されていたら誤マッチ。順序の根拠が無い。
  - クリーンラボ (`partnerCode = "301491"`) の特殊 shop=1 強制が `InvoiceVerifier.java:206-208` にあるのに、Reconciler は `isExcludedPartner` で除外しているだけで「shop=1 で再検索」処理が欠落。Clean Lab を Reconciler の対象に入れたくなった瞬間にバグになる潜在課題 (現状は OK)。
- **修正案**:
  - master `cutoff_date` を `PartnerIndex` に持っておき、優先順位を「マスタの値 → 末 → DD」に変更。あるいは、3 候補が全部ヒットしたら `log.warn` で運用通知。

### M-impl-5: Tasklet の `try { ... } catch (Exception e) { contribution.setExitStatus(FAILED); throw new RuntimeException(...) }` 二重通知
- **箇所**: `backend/src/main/java/jp/co/oda32/batch/finance/TAccountsReceivableSummaryTasklet.java:112-116`
- **コード**:
  ```java
  } catch (Exception e) {
      log.error("売掛金集計バッチ処理中に致命的なエラーが発生しました。", e);
      contribution.setExitStatus(org.springframework.batch.core.ExitStatus.FAILED);
      throw new RuntimeException("売掛金集計バッチ処理がエラーで終了しました。", e);
  }
  ```
- **問題**:
  - Spring Batch では `tasklet` から例外 throw すれば自動的に `ExitStatus.FAILED` になる。`setExitStatus` の手動呼び出しは冗長で、かつ `setExitStatus` 後に throw すると **手動 set した方が後続 listener で一旦反映され、その後 `JobExecutionException` の handler で再上書き** される。
  - `RuntimeException` でラップすることで stack trace が二重 (`Caused by: ...`) になり、ログ可読性が低下。
  - 買掛側 `AccountsPayableSummaryTasklet` の例外ハンドリングと比較して非対称。
- **修正案**:
  - `try/catch` を撤去し、tasklet 内の例外をそのまま throw。Spring Batch の framework に任せる。
  - 失敗通知を独自に追加したい場合は `@OnProcessError` listener を別 Bean で定義。

---

## Minor

### m-impl-1: `AccountsReceivableVerifyRequest` に `mfExportEnabled = Boolean.TRUE` の field initializer
- **箇所**: `backend/src/main/java/jp/co/oda32/dto/finance/AccountsReceivableVerifyRequest.java:24`
- **コード**: `private Boolean mfExportEnabled = Boolean.TRUE;`
- **問題**: Jackson は `@Data` 経由の setter を呼ぶため、リクエスト本文に `mfExportEnabled` フィールドが省略された場合の挙動は **Jackson のデフォルト初期化 → Java 初期化値 = TRUE**。一見良さそうだが、JSON 上 `"mfExportEnabled": null` が来たら **TRUE** にも **null** にも倒れる (Jackson のバージョンに依存)。Controller (line 227) では `request.getMfExportEnabled() != null ? ... : true` で再度 fallback しているため二重ガード。
- **修正案**:
  - DTO の field initializer を撤去し、Controller の null fallback だけ残す。明示的な「省略時 true」を Javadoc に書く。

### m-impl-2: `pkToPath` で `taxRate` を文字列補間 (URL 経路でロケール依存の可能性)
- **箇所**: `frontend/types/accounts-receivable.ts:117`
- **コード**: `return ${ar.shopNo}/${ar.partnerNo}/${ar.transactionMonth}/${ar.taxRate}/${ar.isOtakeGarbageBag}`
- **問題**:
  - JS の `Number.toString()` は常に英数字なのでロケール依存はないが、`taxRate` が `8.00` のような小数で来た場合 `8` に丸まる ([Number(8.00) === 8](https://developer.mozilla.org/))。
  - バックエンドの `BigDecimal taxRate` は `8.00` で persist されているため、URL `/8` が PathVariable `BigDecimal taxRate` にバインドされて `8` として解決され、DB の `8.00` と `compareTo` 一致。動作は OK だが、**`scale` が違う行が複数ある場合に最初の 1 件しか取れない**。
- **修正案**:
  - `String(ar.taxRate)` ではなく、明示的に `Number(ar.taxRate).toFixed(2)` でゼロパディング。

### m-impl-3: `defaultDateRange` の境界判定が「< 20」で 20 日当日が前期間扱い
- **箇所**: `frontend/types/accounts-receivable.ts:97-103`
- **コード**:
  ```ts
  if (d.getDate() < 20) {
      toM -= 1
      ...
  }
  const to = new Date(toY, toM, 20)
  const from = new Date(toY, toM - 1, 21)
  ```
- **問題**:
  - 20 日当日に画面を開くと「当月 21日〜翌月 20日」が表示される。「当日締め」を見たいケース (毎月 20日に運用が回る) で 1 ヶ月先の空期間が表示されて違和感。
  - 設計書のデフォルト挙動と仕様確認が必要。
- **修正案**:
  - `< 20` を `<= 20` に変更し、20 日当日は当月締め期間を表示。あるいは Tooltip で挙動を明示。

### m-impl-4: `accounts-receivable.tsx` の `tableTotals` が `taxIncludedAmountChange` を集計、CSV 出力金額 (`taxIncludedAmount`) と乖離
- **箇所**: `frontend/components/pages/finance/accounts-receivable.tsx:277-285`
- **コード**:
  ```ts
  inc += Number(r.taxIncludedAmountChange ?? 0)
  ```
- **問題**:
  - 画面右上の「税込合計」は `*_change` (集計値) ベース。MF CSV は `taxIncludedAmount` (確定値) ベース。**運用者が画面の税込合計と CSV の合計を突合しようとすると、検証済み行で値が乖離して困惑**する可能性。
- **修正案**:
  - 「税込合計 (集計)」「税込合計 (確定/CSV出力時)」の 2 列を表示する。あるいは Tooltip で「集計値表示中、CSV 出力は確定値」を明示。

### m-impl-5: `AccountsReceivableVerifyDialog` の `defaultInc` フォールバック順序が `invoiceAmount` 優先で操作意図が変わる
- **箇所**: `frontend/components/pages/finance/AccountsReceivableVerifyDialog.tsx:58-61`
- **コード**:
  ```ts
  const defaultInc = row.invoiceAmount ?? row.taxIncludedAmount ?? row.taxIncludedAmountChange ?? 0
  ```
- **問題**:
  - 「請求書金額があればそれを初期値」のコメント通りだが、**請求書金額==null かつ既に手動確定済みの行を再オープンすると、`taxIncludedAmount` (前回の手動値) が表示**される。意図は OK だが、UI ラベル「集計金額」「請求書金額」「確定金額」が並ぶダイアログで、確定金額入力欄に何の値が入っているのかユーザに分からない。
  - 入力欄の placeholder/helper text に「初期値の出典 (集計値 or 請求書 or 前回確定値)」を表示すべき。

### m-impl-6: `summaryService.findAll(spec)` を `summary()` で全件ロード (設計レビュー m-1) のテスタビリティ
- **箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsReceivableSummaryService.java:84-105`
- **問題**: 設計レビュー m-1 で指摘済の集計クエリ未最適化に加え、**unit test が無い**ため将来の最適化リファクタで挙動退行を検出できない。
- **修正案**: m-1 修正と合わせて `TAccountsReceivableSummaryServiceTest` を新設。

### m-impl-7: `cutoffDateLabel` の `cutoff===0` が「月末」、`cutoff===null` が「-」、`cutoff===-1` が「都度現金」 — マジックナンバー
- **箇所**: `frontend/components/pages/finance/accounts-receivable.tsx:576-581`
- **問題**:
  - フロントに `0=月末`/`-1=都度現金` のセマンティクスをハードコード。バックエンド `PaymentType` enum (`MONTH_END(0)`, `CASH_ON_DELIVERY(-1)`) との二重定義。
  - 買掛側 (`accounts-payable.tsx`) と挙動同じか要確認。
- **修正案**:
  - `frontend/lib/payment-type.ts` 等に `paymentTypeLabel(cutoff: number | null): string` を集約し、買掛/売掛で共有。

### m-impl-8: `TAccountsReceivableSummary` が `IEntity` を実装していないため AOP `ShopCheck` の対象外
- **箇所**: `backend/src/main/java/jp/co/oda32/domain/model/finance/TAccountsReceivableSummary.java:28`
- **コード**: `public class TAccountsReceivableSummary { ... }` (`implements IEntity` 無し)
- **問題**:
  - `ShopCheckAop#validateFind` は `List<? extends IEntity>` のみフィルタ (`ShopCheckAop.java:53`)。
  - `findByDateRange(LocalDate, LocalDate)` (`TAccountsReceivableSummaryService.java:175-177`) は **shopNo を引数に取らず** AOP も効かないため、もし誰かが新規 Controller でこのメソッドを直接呼ぶと **全店舗のデータが漏出**する。
  - 現状の呼び出し元 (Tasklet, `findByDateRangeAndMfExportEnabled` 経由の admin 限定 export) は問題ないが、ガードレールが弱い。
- **修正案**:
  - `TAccountsReceivableSummary implements IEntity` (`getShopNo()` は既存 getter で互換) を追加。`@SkipShopCheck` を必要な箇所に明示。
  - もしくは `findByDateRange` を `findByShopNoAndDateRange(Integer shopNo, ...)` のオーバーロードに置換し、引数 null で呼ぶ際の挙動を明文化。

---

## 設計レビューと重複しない追加観点

### A-1. `verify` API 経由で `verifiedManually` をフラグ立てるが解除 API は別 (releaseManualLock)
- 設計レビュー D-2 で「解除 API の挙動不足」は指摘済。実装上の追加問題として、`verify(...)` (PUT) は `verifiedManually=true` を一律 set だが、`updateMfExport(true)` (PATCH) でも内部で `verifiedManually=true` を set する (`TAccountsReceivableSummaryService.java:253-258`)。
- 一方で `updateMfExport(false)` は `verifiedManually` を触らない (M-4 で指摘済) ため、**ON→OFF→ON で verifiedManually が `true` のまま固定化**。`releaseManualLock` を別ボタンで叩かないと解除できない。
- フロント (`AccountsReceivableVerifyDialog.tsx:164-174`) は `verifiedManually=true` のときだけ「手動確定解除」ボタンを表示。ON→OFF→ON フローで「OFF にしたつもりが解除ボタンが消えない」UX 問題。

### A-2. `aggregate` API のレスポンス body が `Map.of("status", "STARTED", ...)` で型なし
- **箇所**: `AccountsReceivableController.java:162-166`
- 同 Controller 内で `AccountsReceivableBulkVerifyResponse` のような専用 DTO を定義しているのに、aggregate だけ `Map<String, Object>`。フロント側 (`accounts-receivable.tsx:181-183`) は `AggregateResponse` 型を期待 (`status / targetDate / cutoffType`) しており、バックエンドのキー追加・削除に追従しない。

### A-3. `aggregate` の非同期 submit で `batchExecutor.submit` の Future を破棄
- **箇所**: `AccountsReceivableController.java:153-160`
- ジョブ起動エラーは `log.error` のみ通知。フロント側にも 200 系で「STARTED」を返すため、起動失敗のユーザ通知パスが無い。
- 設計レビュー D-1 (Reconciler の暗黙起動) と同じ「裏で何が起きたか分からない UX」問題。

### A-4. `bulkVerify` の `assertShopAccess` 相当チェック欠落
- **箇所**: `AccountsReceivableController.java:171-209`
- `verify`/`releaseManualLock`/`toggleMfExport` は `assertShopAccess(shopNo)` を呼ぶが、`bulkVerify` は呼ばない。`@PreAuthorize("hasRole('ADMIN')")` で守られているため admin 限定だが、admin が `request.shopNo=null` を投げると `effectiveShopNo=null` (admin の `loginShopNo=0` ルート) → 全店舗対象に reconcile + verify が走る。意図的かもしれないが、設計書 §9.3 に明記なし。

### A-5. `releaseManualLock` API のレスポンスが古い `verificationDifference`/`invoiceAmount` を含む
- **箇所**: `TAccountsReceivableSummaryService.java:226-235`
- `verifiedManually=false` を更新するだけで、`verificationDifference` 等は据え置き。直後に `AccountsReceivableResponse.from(...)` で旧値を返却。**「手動確定解除しても画面の差額表示が変わらない」UX**。設計レビュー D-2 と関連。

### A-6. Reconciler の `monthsInRange` が `from > to` を防御していない
- **箇所**: `AccountsReceivableCutoffReconciler.java:429-438`
- ```java
  while (!cur.isAfter(end)) {
      out.add(cur);
      cur = cur.plusMonths(1);
  }
  ```
- `from > to` のリクエストが来た場合、while ループは初回で抜けて空 list を返すため無限ループは無いが、Controller 側 (`bulkVerify`) で `fromDate > toDate` の検証も無い (`AccountsReceivableController.java:171-209`)。

### A-7. `getByPK` の `taxRate.setScale(2, HALF_UP)` 強制
- **箇所**: `TAccountsReceivableSummaryService.java:165-170`
- DB の `tax_rate NUMERIC(5,2)` に合わせて scale 2 に強制。**フロントから `8` (整数) を渡しても `8.00` に正規化されて検索される**ため動く。が、scale 1 の `8.0` も同様の正規化が必要で、引数が scale 0/1/2/3 のどれが来ても正常に動くかの **テストが無い**。

### A-8. `accounts-receivable.tsx` の `verifyMutation.mutate` が PathVariable に `transactionMonth` を URL エンコード無しで埋め込む
- **箇所**: `frontend/types/accounts-receivable.ts:117`, `frontend/components/pages/finance/accounts-receivable.tsx:138`
- `transactionMonth` は `yyyy-MM-dd` 形式なので URL 安全だが、もし将来 `transactionMonth` の形式が `yyyy/MM/dd` に変わったり JSON 文字列化で何か混入したらインジェクションリスク。
- 修正案: `encodeURIComponent` でラップ。

---

## レビューチェックリスト結果 (コード固有)

| # | 観点 | 結果 | 備考 |
|---|------|------|------|
| 1 | DTO と Entity 分離 | OK | 設計レビューでも OK |
| 2 | Bean Validation | △ | A-2 (aggregate response 型なし)、m-impl-1 (mfExportEnabled 二重 fallback) |
| 3 | `@Transactional` 境界 | NG | 設計レビュー M-2 と同じ。本レビュー追加は無し |
| 4 | N+1 クエリ | △ | 設計レビュー M-1, M-7 と同じ。M-impl-4 で 3×N 追加発見 |
| 5 | エンコーディング | NG | C-impl-3 (FileWriter platform default) |
| 6 | エラー伝播 | NG | M-impl-5 (Tasklet 二重 catch)、A-3 (非同期 submit エラー黙殺) |
| 7 | 認可 | △ | A-4 (bulkVerify shop check 無し)、m-impl-8 (IEntity 未実装) |
| 8 | 命名 / マジック | NG | M-impl-2 (cutoffType 文字列リテラル)、m-impl-7 (cutoff 0/-1 ハードコード) |
| 9 | データ整合性 | NG | C-impl-1 (markExported 片側更新)、C-impl-2 (cutoffDate 上書き) |
| 10 | テストカバレッジ | NG | 設計レビュー m-6 と同じ。`AccountsReceivableCutoffReconcilerTest` も未作成 |
| 11 | URL 設計 | △ | M-impl-3 (boolean PathVariable)、A-8 (encode 漏れ) |
| 12 | UI/UX 整合性 | △ | m-impl-4 (table total vs CSV)、m-impl-5 (defaultInc 出典)、A-1 (verifiedManually 固定化) |

---

## 対応表

| ID | 重要度 | 推奨対応 |
|---|---|---|
| C-impl-1 | Critical | `markExported` を unconditional 上書きに変更 + `applyMatched` の `setTaxIncludedAmount` を撤去 (二段焼き解消) |
| C-impl-2 | Critical | Reconciler の cash-on-delivery 除外 or `cutoffDate` 引き継ぎ |
| C-impl-3 | Critical | `FileWriter` → `OutputStreamWriter(CP932)` |
| M-impl-1 | Major | `BatchJobLauncherService` 新設、Controller 薄化 |
| M-impl-2 | Major | `CutoffType` enum 化 |
| M-impl-3 | Major | `boolean` PathVariable をクエリパラメータ化 (買掛側と統一) |
| M-impl-4 | Major | `findInvoiceForPartner` の優先順序を master `cutoff_date` 起点に変更 |
| M-impl-5 | Major | tasklet の二重例外ハンドリング撤去 |
| m-impl-1 | Minor | DTO field initializer 撤去 |
| m-impl-2 | Minor | `taxRate.toFixed(2)` |
| m-impl-3 | Minor | `defaultDateRange` の `< 20` を `<= 20` に変更 |
| m-impl-4 | Minor | テーブル合計の出典明示 |
| m-impl-5 | Minor | Verify ダイアログの初期値出典表示 |
| m-impl-6 | Minor | Reconciler / Service unit test 追加 |
| m-impl-7 | Minor | `paymentTypeLabel` ユーティリティ集約 |
| m-impl-8 | Minor | `IEntity` 実装 or `findByDateRange` から shopNo 必須化 |
| A-1 | Minor | `updateMfExport(false)` で `verifiedManually=false` 引き戻し |
| A-2 | Minor | `aggregate` レスポンスを DTO 化 |
| A-3 | Minor | submit エラーを SSE / 通知 channel で伝達 |
| A-4 | Minor | `bulkVerify` で admin 全店舗対象を明示確認 (UI ガードまたは `shopNo` 必須) |
| A-5 | Minor | `releaseManualLock` で関連 verification フィールドをリセット |
| A-6 | Minor | `bulkVerify` で `fromDate > toDate` を 400 で弾く |
| A-7 | Minor | scale 異常系の test 追加 |
| A-8 | Minor | `encodeURIComponent` でラップ |

**Approval status**: **Needs Revision** → Critical 3 件 + 設計レビュー C-1/C-2/M-2/M-3/M-8 の修正で **Approved 想定**

---

## 総括

設計レビューで未検出だったコード固有バグとして、**markExported の片側更新 (C-impl-1)** と **Reconciler の cutoffDate 黙示的上書き (C-impl-2)** を発見。前者は MF 連携の金額不整合に直結する経年バグ、後者はマスタとの定義乖離で UI 表示と実データの一貫性を崩す。

**バッチ vs Controller のエンコーディング不整合 (C-impl-3)** は本番環境次第で MF 取込が文字化けする恐れがあり、Linux 本番環境であれば即時修正必須。

Major では Spring 観点 (Controller の薄化 M-impl-1、boolean PathVariable M-impl-3、tasklet の例外ハンドリング M-impl-5) と Reconciler の検索順序 (M-impl-4) が技術負債として残る。Minor 8 件は段階対応可能。

設計レビュー C-1 / C-2 / M-2 / M-3 / M-8 + 本レビュー C-impl-1 / C-impl-2 / C-impl-3 の **計 8 件** が merge 前修正必須。
