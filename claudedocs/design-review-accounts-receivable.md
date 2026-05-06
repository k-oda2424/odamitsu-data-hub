# 設計レビュー: 売掛金 (Cluster E)

レビュー日: 2026-05-04
対象設計書: `claudedocs/design-accounts-receivable-mf.md`
レビュアー: Opus サブエージェント
対象ブランチ: `refactor/code-review-fixes`

## サマリー

- 総指摘件数: **Critical 2 / Major 7 / Minor 6**
- 承認状態: **Needs Revision**

最重要指摘トップ:
1. (Critical) `AccountsReceivableController#exportMfCsv` の CSV ダウンロードが「成功しても失敗しても無条件に `markExported`」しており、バッチ側 `AccountsReceivableToSalesJournalTasklet` の「失敗時にはマーカーをつけない」契約と非対称。CSV を受け取れなかった運用者からは確定金額が「出力済み」として固定され、再 DL や再検証で齟齬を生む。
2. (Critical) `TAccountsReceivableSummaryPK` の `transactionMonth` 列名が camelCase で誤宣言されている。`@IdClass` 経由のため現在は黙殺されているが、将来 `@EmbeddedId` への移行や Specification での Embedded path 参照が入った瞬間にデータ取得不能になる地雷。

---

## Critical 指摘

### C-1. CSV DL で「失敗時もマーカーを焼く」非対称
- **箇所**: `backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java:281-291`
- **問題**: `try (OutputStreamWriter w = ...)` の外で `markExported` → `summaryService.save()` を無条件に実行。例外時のロールバックも try/catch も無く、また `salesJournalCsvService.writeCsv` がレスポンス送信前に成功した時点で焼くため、ブラウザ側のダウンロードが中断 (ネットワーク切断・5xx 経由) してもサーバ側は CSV を確定済みにしてしまう。買掛側 `FinanceController#exportPurchaseJournalCsv` (`FinanceController.java:367-368`) も同様のパターンだが、買掛側は `result.rowCount == 0` の場合に 422 で abort してから markExported に進むのに対し、売掛側は `summaries.isEmpty()` のガードすら無い。
- **影響**: 運用者が「CSV を受け取り損ねた → もう一度 DL ボタン」を押しても、`tax_included_amount` が `*_change` で凍結された後なので、バッチ側で `*_change` が再集計されると **再 DL 用の金額と元金額が乖離**。会計連携の二重計上 / 連携漏れの基点になる。
- **修正案**:
  1. レスポンス本体を `StreamingResponseBody` で返却し、書き出し成功後の callback で `markExported` を呼ぶ (HTTP 200 が確定したことを保証)。
  2. それが重い場合は最低限「summaries が空なら 422 / マーク済みフラグを使った冪等化」をする。買掛側と動作を揃え、`FilterResult.exportable.size()==0` で 422 を返す。
  3. `markExported` ループは `summaryService.saveAll(summaries)` (1 tx) に置換し、N+1 個別 save を排除。

### C-2. Embeddable PK の列名 camelCase 誤宣言
- **箇所**: `backend/src/main/java/jp/co/oda32/domain/model/embeddable/TAccountsReceivableSummaryPK.java:30-31`
  ```java
  @Column(name = "transactionMonth")  // ← 実 DB 列は transaction_month
  private LocalDate transactionMonth;
  ```
- **問題**: 実テーブル定義 (`V018__alter_accounts_receivable_summary_add_verification_fields.sql:35` 他) では `transaction_month` (snake_case)。`TAccountsReceivableSummary` 本体 (`TAccountsReceivableSummary.java:38`) は正しく `name = "transaction_month"` を指定しているが、PK class が乖離。
- **影響**: 現在は `@IdClass(TAccountsReceivableSummaryPK.class)` 方式 (Entity の `@Id` フィールドが正本) のため `@Column(name)` は無視されて偶然動いている。が、
  - 将来 `@EmbeddedId` へ移行した瞬間に PostgreSQL で「column transactionMonth does not exist」になりロード不能。
  - `Specification` で `root.get("transactionMonth")` ではなく `root.get(TAccountsReceivableSummaryPK_.transactionMonth)` のように Embeddable 経由で参照すると同様に死ぬ。
  - JPA Metamodel 生成系・Querydsl 生成系で誤った列名が混入する可能性。
- **修正案**: 列名を `transaction_month` に修正、もしくは PK class の `@Column` を一律削除して `@IdClass` の挙動に任せる。`AccountsPayable` 側の PK class 命名も突き合わせて統一。

---

## Major 指摘

### M-1. `bulkVerify` の auto-reconcile が無防備に大量 DELETE+INSERT を走らせる
- **箇所**: `AccountsReceivableController.java:181-189` + `AccountsReceivableCutoffReconciler.java:86-208`
- **問題**: 一括検証ボタン押下のたびに `AccountsReceivableCutoffReconciler#reconcile` が走る (`@Transactional`)。期間が広いと、対象期間内の全 partner × 全月で `findInvoiceForPartner` (3 種類の closingDate を順次クエリ) → t_order_detail 再集計 → DELETE+INSERT を回す。N+1 (各 partner ごとに `tInvoiceService.find...` を 3 回 + `tOrderDetailService.findByPartnerNosAndDateRange` 1 回) かつ、UI からはユーザの意図しない裏で行われる。設計書 §5/§7 にこの「自動再集計」の存在自体が一切書かれておらず、設計書 vs 実装の乖離が大きい。
- **影響**:
  - 運用者から見ると「一括検証」を押したのに集計まで書き換わるため、想定外の差額発生・原因追跡困難。
  - 期間が長いと数千クエリになる。手動確定行のみを保護しているが、それ以外は新規 INSERT で `mfExportEnabled=false` にリセットされるため、すでに `mf_export_enabled=true` で運用にロックしていた行が黙って false に戻る (Reconciler は `mfExportEnabled(false)` で生成: `AccountsReceivableCutoffReconciler.java:371`)。
- **修正案**:
  1. 設計書に「auto-reconcile」セクションを追加し、UI 側で「自動再集計を含めて検証」「検証のみ」をユーザに選ばせる (`bulkVerify?autoReconcile=true|false`)。
  2. Reconciler が新行を作る際、削除対象 stale 行に `mfExportEnabled=true` が含まれていたら新行にも引き継ぐ (`anyExportEnabled` を見て propagate)。
  3. 期間内の Invoice をまとめて取得する `findByShopAndPartnerCodesAndClosingDateIn` を追加し、N+1 を解消。

### M-2. `bulkVerify` のトランザクション境界が壊れている
- **箇所**: `AccountsReceivableController.java:171-209`
- **問題**: Controller メソッドに `@Transactional` 無し。内部で
  - `cutoffReconciler.reconcile(...)` (`@Transactional` で別 tx)
  - `findByShopAndDateRange` 再ロード (再 tx open/close)
  - `invoiceVerifier.verify(...)` (in-memory 更新のみ)
  - `summaryService.saveAll(...)` (`@Transactional` で別 tx)

  という 3 つの独立トランザクションに分解される。Reconciler 中で commit したあとに `verify` → `saveAll` 中で例外が起きると、**Reconcile だけ commit されて検証結果は未保存**になる。Reconcile が走った得意先は `mf_export_enabled=false` の新行に置き換わっているため、運用上「一括検証」が「黙って MF 出力対象を全部 OFF にしただけ」の事態になる。
- **影響**: 部分コミット破綻。設計書 §5/§9.3 の「画面の検索条件範囲を対象に検証する」というシンプルな意味合いと食い違う。
- **修正案**: Controller 側に `@Transactional` を付ける、または `BulkVerifyService` を新設して全工程を 1 tx に閉じる。Reconciler 単独の `@Transactional` は外すか `Propagation.REQUIRED` (デフォルト) のままで親 tx に乗るよう保つ。

### M-3. `verify` (PUT) で `verificationResult=1` を強制し UI と乖離
- **箇所**: `TAccountsReceivableSummaryService.java:204-220`
- **問題**: 手動確定 API は確定金額・備考だけ受け取るが、内部で `verificationResult = 1` を**問答無用に**セット。設計書 §9.4 にも「verification_result = 1 を強制設定」と明記されているが、`AccountsReceivableVerifyRequest` (`AccountsReceivableVerifyRequest.java:13-25`) には検証結果フラグも diff も無いため、運用者が「不一致だが業務都合で MF には 12 万円で出す」のような確定をしても **検証バッジは緑「一致」になる**。トレーサビリティが死ぬ。
- **影響**: 監査時に「なぜこれが一致になっているのか」が判らなくなる。整合性レポート (Cluster D) は `verification_result` を信用しているため、手動確定の隠れた差額が永久にレポートに出てこない。
- **修正案**:
  1. リクエストに `verificationResult` (またはユーザ任意の確定理由カテゴリ) を追加し、`note` 必須化を `taxIncludedAmount != invoiceAmount` の場合に切り替える。
  2. `verifiedManually=true` のときは UI 側のバッジを「手動」(青) に分岐し、`verificationResult` の意味を「自動検証結果」に限定する。

### M-4. `updateMfExport` の副作用が暗黙すぎる
- **箇所**: `TAccountsReceivableSummaryService.java:241-260`
- **問題**: `mf_export_enabled=true` を立てるだけのつもりの API が、副作用で `verifiedManually=true` + `verificationResult=1` まで書き換える (クリーンラボ運用対応のコメントあり)。買掛側 `accountsPayableSummaryService.updateMfExport` には同等の副作用が無い (買掛側は `verified_manually` を触らない単純な toggle)。設計書 §9.6 のレスポンス仕様 (204 No Content) や §9.4 とも整合せず、設計書には記載が無い隠れ仕様。
- **影響**: 運用者が「MF 出力を試しに ON にしてみた」だけのつもりが手動確定扱いになり、次の自動再集計 (M-1 の挙動) で保護されてしまう。OFF→ON→OFF の往復で `verifiedManually` が `false` に戻らない (`updateMfExport(false)` が `verifiedManually` を触らないため) 状態の腐食。
- **修正案**: クリーンラボのような特殊 partner を **設定駆動** (例: `m_partner.always_export_to_mf = true`) で扱い、汎用 toggle API から特殊副作用を切り離す。設計書にもこの方針を明記。

### M-5. `applyMatched` の差額計算に丸め誤差混入
- **箇所**: `InvoiceVerifier.java:338-356` + `allocateInvoiceByArRatio` (`InvoiceVerifier.java:379-418`)
- **問題**: 完全一致でない「許容誤差内 (≤3円)」のとき、`allocateProportionallyWithRemainder` で `taxIncludedAmountChange` を **書き換えてから** `allocateInvoiceByArRatio` で再按分している。再按分は AR 比率 (= 既に按分後の値) を使うため、按分残差が最大行に集中して `verificationDifference = 按分後請求書額 − 按分後 AR` がほぼ常に 0 になる (= 一致時と区別がつかない)。`applyMatched` の `diff` 引数は受け取っているのに使っていない。
- **影響**: 3 円差額調整したことが行レベルの diff に表現されないため、UI 上「許容誤差で吸収しました」が運用者に見えない。差額調整の監査痕跡欠落。
- **修正案**: 調整前の元値 (`*_change` のオリジナル) を `applyMatched` まで持ち回り、`verificationDifference = invoiceAmountAllocated − originalChange` で記録する。または `verificationNote` に「許容誤差 -2円 自動吸収」のような自動メモを追記する。

### M-6. 上様 (999999) の按分が 0 円集計時に request 額を捨てる
- **箇所**: `InvoiceVerifier.java:145-157`
- **問題**: `totalTaxIncluded == 0` のとき `log.error` するだけで、`applyMatched` には進む。集計 0 円のまま `verificationResult=1, mfExportEnabled=true` で確定し、CSV には金額 0 円の上様行が現れる (もしくは `markExported` で 0 が `*_amount` にコピーされ、請求書金額分の売上が連携されない)。
- **影響**: 上様で「集計失敗 → 0」が起きた月は **請求書金額の売上が MF 連携されない**。検出は `log.error` のみで運用通知無し。
- **修正案**: `totalTaxIncluded == 0` の場合は `applyNotFound` 相当 (`mfExportEnabled=false`) に倒し、画面上「未検証 (集計0)」として顕在化させる。設計書 §7 の上様処理にこのエッジケースを追記。

### M-7. `Optional` 同期返却 + N+1: `mPartnerService.getByPartnerNo` をループ呼び
- **箇所**: `AccountsReceivableController.java:96-104`
- **問題**: 一覧 API で `partnerNos.stream().map(mPartnerService::getByPartnerNo)` と1件ずつ問い合わせ。50件/page のため最悪 50 回。買掛側は `mPaymentSupplierService.findAllByPaymentSupplierNos(supplierNos)` の bulk fetch (`FinanceController.java:118`) を使用しており非対称。
- **影響**: 一覧表示のたびに 50+1 クエリ。検索結果が多い admin (全店舗閲覧) でレスポンス劣化。
- **修正案**: `MPartnerService` に `findAllByPartnerNos(Set<Integer>)` を追加し、1 クエリで bulk 取得して Map 化する。

---

## Minor 指摘

### m-1. `summary()` が全件ロードしてストリーム集計
- **箇所**: `TAccountsReceivableSummaryService.java:84-105`
- **問題**: ページング無しで `findAll(spec)` してから Java で count/sum。月次数千行を毎回 in-memory 集計。
- **修正案**: JPQL/Criteria での `count(case when ... )` + `sum(case when ...)` クエリに置換。

### m-2. `verify` ハンドラが `verificationDifference` / `invoiceAmount` を再計算しない
- **箇所**: `TAccountsReceivableSummaryService.java:204-220`
- **問題**: 手動確定で確定金額が変わっても `invoiceAmount` (突合した請求書額) と `verificationDifference` は更新されない。`AccountsReceivableResponse.from` で旧値が表示され、運用者が「確定金額 12 万 / 請求書 10 万 / 差額 0 (古い)」のような不整合表示を見る。
- **修正案**: 手動確定時に `verificationDifference = (invoiceAmount or null) - taxIncludedAmount` を再計算、もしくは UI 側で表示を「手動確定済」に切り替えて差額列を `-` 表示にする。

### m-3. `AccountsReceivableSummaryResponse.summary()` の `partnerNo` が無視される
- **箇所**: `AccountsReceivableController.java:108-116` + `TAccountsReceivableSummaryService.java:85`
- **問題**: Controller の `summary` エンドポイントは `partnerCode` のみ受け取り、Service 側の `summary()` には `partnerNo=null` 固定で渡している。一覧 API は `partnerNo` を受けるため、検索条件と sumamry 表示の集計範囲が乖離。
- **修正案**: `summary` エンドポイントに `partnerNo` を追加するか、UI で `partnerNo` を選んだら summary も再計算するよう揃える。

### m-4. `applyMismatch` で `verificationDifference` が「按分後 AR」を使う
- **箇所**: `InvoiceVerifier.java:358-372`
- **問題**: 不一致のときも `allocateInvoiceByArRatio` で按分 → 行ごとの diff は「按分請求書 - 按分 AR」。AR 側は按分していないので diff 自体は元の差と一致するが、 multi-row の場合に行 diff の合算がグループ diff と一致するかの保証は弱い (端数集中で +1/-1 ずれる)。
- **修正案**: グループ diff のテストケースを `InvoiceVerifierTest` に追加。

### m-5. CSV エンコーディング指定が二重定義
- **箇所**: `AccountsReceivableController.java:68` (`Charset.forName("windows-31j")`) + `:297` (`charset=Shift_JIS`)
- **問題**: 実体は CP932 (windows-31j) なのに HTTP ヘッダは `Shift_JIS` を返す。MF 取り込みは現状動いているが、Shift_JIS と CP932 の差異 (NEC 特殊文字等) が出る可能性。
- **修正案**: ヘッダも `text/csv; charset=Windows-31J` に統一、買掛側 `FinanceController.java:CP932` 定義と一本化。

### m-6. 設計書 §3 / §11 で必須とした `InvoiceVerifierTest` が未実装
- **箇所**: 想定パス `backend/src/test/.../InvoiceVerifierTest.java`
- **問題**: ファイル無し。設計書 §11.1「単体テスト追加」が完了条件として残置。
- **修正案**: 一致 / 3円以内差額 / 3円超差額 / 請求書なし / 上様 / イズミ四半期 / 上様集計0 (M-6) / `verifiedManually` 保護 の 8 ケース最低限実装。

---

## 買掛金との対称性 / 非対称性

設計書 §1「買掛側との対称構造」表で対称性が明示されているにもかかわらず、実装では以下の非対称が確認された。

| # | 観点 | 買掛 (AP) | 売掛 (AR) | 評価 |
|---|------|-----------|-----------|------|
| S-1 | Controller 配置 | `FinanceController#listAccountsPayable` (1 ファイル巨大化) | `AccountsReceivableController` (独立) | **AR の方が綺麗**。AP も将来切り出すべき。 |
| S-2 | `@PreAuthorize` 粒度 | verify / mf-export は `isAuthenticated()` (ロール無し) | verify / mf-export / aggregate / bulk-verify / export-mf-csv が `hasRole('ADMIN')` | **意図的差異**ならドキュメント化が必要。AP 側も金銭操作なので ADMIN 化を検討。 |
| S-3 | bulk-verify | AP には bulk-verify が無い (AccountsPayableVerificationTasklet 一本) | AR は API 経由で bulk-verify 可 | 設計差。AP 側にもユーザ起動の検証ボタンが欲しいなら設計を統一。 |
| S-4 | CSV DL の filter ロジック | `PurchaseJournalCsvService.filter()` で `forceExport` (未検証含む) 切替 + `nonExportableCount` 集計 | AR は `findByDateRangeAndMfExportEnabled(true)` 一択、`forceExport` 概念無し | AR でも「未検証だけ強制出力」「検証 OK だけ」のオプションが欲しい。 |
| S-5 | CSV DL preview | AP は `/export-purchase-journal/preview` で件数・skipped を事前確認 | AR は preview 無し、いきなり DL | AR にも preview を実装。設計書 §6.7 の confirmDialog だけでは足りない。 |
| S-6 | CSV 0件時の挙動 | AP は 422 で abort | AR は空ファイル DL | AR を AP に揃える (空 CSV を運用に渡す価値が薄い)。 |
| S-7 | bulk fetch (master JOIN) | `mPaymentSupplierService.findAllByPaymentSupplierNos` で N+1 解消済 | `mPartnerService.getByPartnerNo` ループ (M-7) | 非対称。AR を AP に揃える。 |
| S-8 | CutoffReconciler | AP には存在しない (`m_payment_supplier` cutoff_date は 20 日固定運用) | AR は AccountsReceivableCutoffReconciler | AR 固有要件 (得意先ごとに 15/20/月末が混在) なので妥当。ただし bulkVerify 側で暗黙起動なのが M-1。 |
| S-9 | 検証許容誤差 | 設計書記載: 5円 | 設計書記載: 3円 / 実装: `@Value("${batch.accounts-receivable.invoice-amount-tolerance:3}")` で外部化 | AR の方が外部化されている。AP 側も外部化推奨。 |
| S-10 | verify request | `verifiedAmount` (税込のみ) | `taxIncludedAmount` + `taxExcludedAmount` 両方 | 売上は税抜分けて MF に出すため妥当。だが AP 側も税抜が必要な場面はあり、整合させる検討余地あり。 |
| S-11 | `@Transactional saveAll` 統一 | `accountsPayableSummaryService.saveAll(filtered.exportable)` (`FinanceController.java:368`) | AR の CSV DL は `for-each save` (`AccountsReceivableController.java:288-290`) | C-1 と関連。AR 側を `saveAll` に統一。 |
| S-12 | tasklet 内 検証ロジック抽出 | `SmilePaymentVerifier` Service 化済 | `InvoiceVerifier` Service 化済 (今回) | 対称達成。 |

---

## 設計書 vs 実装の乖離

### D-1. 設計書に Reconciler の記述が無い
設計書 §5.2/§9.3 では「`InvoiceVerifier` に検証ロジックを委譲」しか書かれておらず、`AccountsReceivableCutoffReconciler` の存在・目的・自動起動タイミング (`bulkVerify` から無条件呼び出し) が完全に未記載。Reconciler は ENEOS のような「マスタ cutoff_date が古い」運用に追従するための重要な機構なので、設計書に独立節を追加すべき (M-1 と関連)。

### D-2. `release-manual-lock` API が設計書 §9.5 で URL/method のみ記載で **挙動不足**
実装は `verifiedManually=false` のみ書き換える (`TAccountsReceivableSummaryService.java:226-235`)。`mf_export_enabled` や `verification_result` を巻き戻すかどうか、`*_amount` をどうするか (現状: そのまま残置 = 旧手動値が残る) が設計書に書かれておらず、再検証バッチが走るまでデータ状態が "宙に浮く"。

### D-3. 設計書 §5.3 の「`*_amount = *_change` コピー処理は維持」が CSV DL でも一致
設計書通りだが、`InvoiceVerifier#applyMatched` 中でも `s.setTaxIncludedAmount(s.getTaxIncludedAmountChange())` (`InvoiceVerifier.java:353`) と二重に焼く設計になっているため、`SalesJournalCsvService#markExported` (`SalesJournalCsvService.java:228`) の意味が薄れる (一致行は既に焼かれている)。設計意図 (検証一致時は確定 / CSV 出力時に再確定の二段) を §5.3 に明記すべき。

### D-4. 設計書 §10 の実装順序 「7. CSV生成ロジックを Service に抽出」 は完了だが、tasklet 側にもう一重 Tasklet が残存
`AccountsReceivableToSalesJournalTasklet` は今や Service に薄く委譲しているだけ。Job として残す価値は「CLI からの定期実行」だが、設計書 §2「定時バッチ設定: 含まないもの」と矛盾。tasklet を deprecate にするか、定時バッチを正式機能化するか方針決定が必要。

### D-5. 設計書 §13 リスク R2「CSV DL のメモリ使用量」未対処
実装は `ByteArrayOutputStream` 全量バッファ (`AccountsReceivableController.java:281`)。1 ヶ月分なら問題ないが、半年指定で N×6 倍。`StreamingResponseBody` 化は C-1 と併せて対処推奨。

---

## レビューチェックリスト結果

| # | 観点 | 結果 | 備考 |
|---|------|------|------|
| 1 | DTO と Entity 分離 | OK | `AccountsReceivableResponse.from(entity, partner)` ファクトリ |
| 2 | Bean Validation | △ | `AccountsReceivableVerifyRequest` の `note` `Size(max=500)` のみ。 `taxIncludedAmount >= taxExcludedAmount` のクロスフィールド検証なし |
| 3 | `@Transactional` 境界 | NG | M-2 (bulkVerify 部分コミット), C-1 (CSV DL) |
| 4 | N+1 クエリ | NG | M-7 (partner lookup), M-1 (Reconciler invoice lookup) |
| 5 | 例外ハンドリング | △ | CSV DL で `throws Exception`, save ループで catch ログだけして次行に進む (`TAccountsReceivableSummaryTasklet.java:271-275`) は集計失敗の運用通知が `log.error` のみ |
| 6 | ログ出力レベル | OK | 過不足無し、件数集計あり |
| 7 | 設計書遵守 | NG | D-1〜D-5 |
| 8 | テストカバレッジ | NG | `InvoiceVerifierTest` 未作成 (m-6) |
| 9 | 認可制御 | △ | S-2 で AP/AR 非対称 |
| 10 | 監査証跡 | NG | M-3 (verify で結果フラグ強制), m-2 (差額未更新) |
| 11 | 命名 / コメント | OK | 「20日締め売掛金テーブル」のクラスコメントは現状15/20/月末混在で不正確 (`TAccountsReceivableSummary.java:15`, `TAccountsReceivableSummaryRepository.java:14`) → minor |
| 12 | エンコーディング | △ | m-5 |
| 13 | 締め日 cutoff の論理 | OK 概ね | tasklet と reconciler でゴミ袋コードが異なる (`TAccountsReceivableSummaryTasklet.java:55-60` と `AccountsReceivableCutoffReconciler.java:60-64`) のは別問題で **要追加調査** (00100007/00100009/00100011 が tasklet 側に無い)|
| 14 | CSV フォーマット | OK | `MFJournalCsv` を共通利用 |

### 追加発見 (チェックリスト 13 関連): ゴミ袋 goods_code 定義の不整合
- **箇所**: `TAccountsReceivableSummaryTasklet.java:55-60` (`00100001/3/5/00100101/103/105`) vs `AccountsReceivableCutoffReconciler.java:60-64` (`00100001/3/5/00100007/9/11`)
- **影響**: Reconciler が走った得意先と走らなかった得意先で「ゴミ袋扱いになる goods_code」が変わる。同じ売上が AR 上で `is_otake_garbage_bag=true/false` の別 PK 行になり、CSV で売掛金/未収入金の振り分けが破綻。
- **重要度**: **Major (M-8 として追加)**。即修正が必要。どちらが正かを業務確認のうえ定数 class に切り出して共有。

---

## 総括

「対称構造」を謳った設計書通りには実装されておらず、AR 固有のリッチ機能 (Reconciler, ADMIN 認可, bulk verify) が後付けで増えた結果、買掛側と挙動が分岐している。Critical 2 件は両方とも **データ不整合に直結する** ため、merge 前に修正必須。Major のうち M-1 / M-2 / M-3 / M-8 は業務影響大、残りは技術負債として段階対応可。

承認には最低 C-1 / C-2 / M-2 / M-3 / M-8 の 5 件の修正が必要。
