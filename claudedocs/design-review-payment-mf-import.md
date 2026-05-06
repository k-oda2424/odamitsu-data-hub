# 設計レビュー: 買掛仕入 MF 変換 (Cluster C)

レビュー日: 2026-05-04
対象設計書: `claudedocs/design-payment-mf-import.md`, `claudedocs/design-payment-mf-aux-rows.md`
レビュアー: Opus サブエージェント

## サマリー

- 総指摘件数: **Critical 3 / Major 6 / Minor 7**
- 承認状態: **Needs Revision**

実装は設計の意図にほぼ沿っており、ゴールデンマスタテスト 2 本 PASS、N+1 解消や advisory lock など改善が積み上がっている。一方で「設計書 §5.1 と実装の食い違いが設計書側でも認知されているのに是正されていない」「PreAuthorize の付け方が `convert` で抜けている」「20日払いセクションの DIRECT_PURCHASE 降格が PAYABLE 突合・税抜逆算と整合していない」など、**運用整合性 / セキュリティ / データ品質** に関わる Critical/Major が複数存在する。マージ前に少なくとも Critical の解消と Major の方針確認が望ましい。

---

## Critical 指摘

### C-1. `PaymentMfImportController#convert` のロール権限が落ちている (admin だけのはずの CSV 生成が一般ユーザでも実行可能)

- **箇所**: `backend/src/main/java/jp/co/oda32/api/finance/PaymentMfImportController.java:66-83`
- **問題**:
  - `/convert/{uploadId}` は `@PreAuthorize("isAuthenticated()")` のみで、`hasRole('ADMIN')` が付いていない (クラス宣言 L33 の継承だけ)。
  - 同じ Controller 内の `/export-verified` (L109) / `/aux-rows` (L171) / `/rules` PUT/DELETE (L227, L236) / `/rules/backfill-codes` (L248) はすべて `hasRole('ADMIN')` 必須。
  - `verify` (L87) も同じく一般ユーザ実行可能で、買掛金一覧 `t_accounts_payable_summary` の `verified_amount` / `verified_manually=true` を一括書き込みする破壊的操作。
- **影響**: 一般ユーザがマスタ整備中の Excel を誤アップロードして convert/verify するだけで、本番の `t_accounts_payable_summary` (PAYABLE) と `t_payment_mf_aux_row` (補助行) が洗い替えされる。会計仕訳の根拠データを誰でも上書きできる状態。
- **修正案**:
  ```java
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/convert/{uploadId}")
  public ResponseEntity<?> convert(...)

  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/verify/{uploadId}")
  public ResponseEntity<?> verify(...)
  ```
  あわせて `/preview` (L43) と `/preview/{uploadId}` (L57) は閲覧目的なので `isAuthenticated()` 維持で OK だが、`POST` で副作用ありに見えるため `@PostMapping` → `@GetMapping` を再検討するか、Controller 上部に「副作用なし」コメントを追加すること。

### C-2. 設計書 §5.1 の送金日マッピング記述が運用実態と矛盾したまま放置されている

- **箇所**: `claudedocs/design-payment-mf-import.md:174-194` (§5.1) と `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java:996-1013` (`deriveTransactionMonth` の Javadoc)
- **問題**:
  - 設計書 §5.1 は「**現行ルール: 5日払い・20日払いともに前月20日締めに統一**」と書かれており、コードと一致しているように見える。しかし実装側 Javadoc は「※ design-payment-mf-import.md §5.1 の『20日→当月20日締め』記述は運用実態と異なるため**次回設計書更新で是正すること**」と明示しており、`MEMORY.md` にも「20日→当月20日記述は運用実態と異なる」と記録されている。
  - つまり「設計書の §5.1 表は最新化されている」のに「コード側 Javadoc は古い設計書の旧記述を是正対象として参照している」状態。設計書か Javadoc のどちらかが嘘をついており、レビュー時に「どちらが事実か」を読者が判別できない。
  - feedback 「設計書とコードが食い違う時は運用実態を優先」(MEMORY.md より) に従うべき箇所だが、運用実態 = 「両方とも前月20日締め」が事実なら、設計書 §5.1 の本文 (現行ルールセクション) はコードと一致するため、Javadoc の「§5.1 の記述は運用実態と異なる」コメントは古い情報を残しており、是正済みなのに「未是正」と読者に錯覚させる。
- **影響**: 新メンバーが「設計書が間違っているらしい → 自分で運用ヒアリングしないと信用できない」と判断し、二重確認コストが恒常化する。逆に Javadoc を信じて設計書を「直さなきゃ」と再修正する改ざんリスク。
- **修正案**:
  - **どちらが事実か確定** → 運用 (経理) に再確認 → 確定値で両方を統一。
  - Javadoc 側の「※ design-payment-mf-import.md §5.1 ... 次回設計書更新で是正すること」は、設計書本文が現行に合っているなら削除する。残す場合は「§5.1 旧記述 (rev1)」のように **どのリビジョンの記述に対するメモか** を明示する。
  - 設計書 §5.1 末尾の「（旧仕様では 20日払いは『当月20日締め分』と突合していたが…）」は履歴としては有用だが、これも残骸として誤読されやすいので「旧仕様 (〜2026-04-14)」のように日付を入れて archived マーク化する。

### C-3. `PaymentMfImportService#applyVerification` 内で `buildPreview` が 2 回走り、税率別集計の整合性も `payable` 集計と齟齬する

- **箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java:184-305` (特に L228-288 と L293-294)
- **問題**:
  1. **二重 buildPreview**: メソッド冒頭で `List<MPaymentMfRule> rules = ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0")` + `payablesByCode` を独自に組み立てて PAYABLE 反映ループを回す (L195-288)。直後の L293 で `PaymentMfPreviewResponse preview = buildPreview(uploadId, cached);` を呼び、補助行保存に渡している。`buildPreview` (L738-944) も内部でまったく同じ rules ロードと `payablesByCode` 構築をやり直す。コメント (L290-292) は「N+1 が 2 周していた」のを 1 周に減らした、と書かれているが、現状でも rules / payables の DB ヒットは 2 度発生している (`buildPreview` 用 + applyVerification 用)。
  2. **税抜逆算ロジックの精度問題**: L276-281 で `BigDecimal divisor = BigDecimal.valueOf(100).add(taxRate)` → `invoice * 100 / divisor (DOWN)`。`taxRate` が 10 (%) の場合 `100 / 110 ≒ 0.909...`。`invoice = 1100` なら 1000 円ぴったりになるが、複数税率行 (10% と 8%) が同一 supplier にある場合、両方とも `invoice` 全額 (110%税込) を税抜化するため、合算すると **元の税込総額より大きい税抜合計** が DB に残る。コメント L276 に「単一税率前提。複数税率の仕入先は手動調整で対応」と但し書きはあるが、UI でこの状態を検出する仕組みがなく、後段の税額計算で誤差が積もる。
  3. **payable 突合は `taxIncludedAmountChange ?? taxIncludedAmount` を税率行 SUM (L247-252) しているが、税抜逆算は税率行ごとに同じ `invoice` (税込総額) を投入** している。突合対象 (税込合計) と書込対象 (税率別税抜) のスケールが違う。突合判定 (`isMatched`) と書込が一致前提なので、運用実態として「単一税率行しかない」「同一税率の重複行のみ」に依存している。
- **影響**:
  - 1 は性能ロス (rules + payables × 2 ヒット)。実害は中。
  - 2/3 は買掛金集計の財務整合に直結。複数税率 supplier が出現した瞬間に `verified_amount_tax_excluded` が壊れ、検証済みCSV出力 (`exportVerifiedCsv`) で MF に渡る金額が誤る。コメント上は「手動調整」と書かれているが、UI 側でアラート表示も無いため気付けない。
- **修正案**:
  - 1: PAYABLE 反映ループに渡すのと、`saveAuxRowsForVerification` に渡すのを **同一の preview** にする。冒頭 `PaymentMfPreviewResponse preview = buildPreview(uploadId, cached);` を最初に 1 回だけ作り、L228 のループも `preview.getRows()` の PAYABLE 行に対して反映する形にリファクタすると DB ヒット数も理解難度も下がる。
  - 2/3: 複数税率 supplier の検出ロジックを追加し、検出時は警告ログ + UI 警告 (`VerifyResult` に `multiTaxRateSuppliers: List<String>` を追加) する。あるいは、税率別の請求額内訳を Excel 側で持っていない以上、複数税率 supplier は一括検証対象から除外して必ず手動 verify に回す方針を明示。

---

## Major 指摘

### M-1. 20日払いセクション (`afterTotal=true`) の `PAYABLE → DIRECT_PURCHASE` 降格処理が責務分散している

- **箇所**: `PaymentMfImportService.java:794-806` (`buildPreview` 内)
- **問題**:
  - 合計行以降の PAYABLE ヒットを「`MPaymentMfRule.builder()` で新しい DIRECT_PURCHASE ルールを動的生成」して差し替える、という処理を `buildPreview` 内に直書きしている。
  - 設計書 §5.3 では「ルックアップ統一」を謳っているが、実装は「ルックアップ後に書き換え」しており、設計と実装で責務の置き場所がズレている。
  - 動的生成ルールには `creditDepartment` 等が NULL のままで、もとの PAYABLE ルールに `creditDepartment` が設定されていた場合に消失する。現在のシードでは PAYABLE 側の `credit_department` は全部 NULL なので問題は表面化していないが、将来 PAYABLE ルール側に `credit_department` を追加した瞬間に DIRECT_PURCHASE 側で落ちる。
  - 同じ「降格」ロジックが `applyVerification` 側にはなく、`afterTotal` 行は L230 で `skipped++` で抜けるため、PAYABLE rule が存在する supplier でも `t_accounts_payable_summary` への一括検証反映から外れる。`buildPreview` だけが DIRECT_PURCHASE 化するため、CSV 出力結果と verified 状態 (DB) で挙動が異なる。
- **影響**:
  - DIRECT_PURCHASE で出力された supplier が買掛金一覧で「未検証」のまま残る。経理が後追いで気付くまで `t_accounts_payable_summary` のステータスが正しくない。
  - PAYABLE ルールに将来カラム追加すると DIRECT_PURCHASE の出力欠落が発生する潜在バグ。
- **修正案**:
  - 「`afterTotal=true` かつ PAYABLE ヒット → DIRECT_PURCHASE 降格」を `MPaymentMfRule#deriveDirectPurchaseRule()` などのメソッドに切り出し、buildPreview と applyVerification の両方から呼ぶ。
  - DIRECT_PURCHASE ルールが m_payment_mf_rule に明示登録されている supplier (例: ワタキューセイモア㈱) と、PAYABLE ルールから動的降格する supplier の **どちらが優先されるか** を設計書 §5.3 に追記。現状はコード上、`afterTotal=true` セクションのレコードがまず PAYABLE/EXPENSE/DIRECT_PURCHASE のいずれかを byCode/bySource で引き、PAYABLE のみ降格する仕様。

### M-2. `PaymentMfImportService#cache` がノードローカルでマルチインスタンス HA に弱い (設計書未記載)

- **箇所**: `PaymentMfImportService.java:99` (`Map<String, CachedUpload> cache`)
- **問題**:
  - コード側コメント (L93-98) に「single-instance 前提」「マルチ化するときは Redis 等に寄せること (B-W9)」と記載されているが、設計書 (`design-payment-mf-import.md` §7) には `uploadIdキャッシュ` の項に「cashbook-import と同じパターン」とだけあり、HA 制約は触れていない。
  - 5日払い Excel と 20日払い Excel の `applyVerification` を同時に行うケースで、Web ノードが 2 台あれば preview / convert / verify が別ノードに分散して 404 になる。
- **影響**: 将来 LB 配下に複数 Web ノードを置いた瞬間、誰も気付かないところで preview/convert が壊れる。
- **修正案**:
  - 設計書 §7 に「single-instance 前提」「将来マルチ化時は Redis/PostgreSQL に寄せる候補」を追記。
  - 当面の運用ガード: アプリ起動時のヘルスチェックで `cache.size() > 0` を Prometheus に出すか、Cookie sticky を nginx 側で確実にする運用注記を README に追加。

### M-3. `applyVerification` の advisory lock キー設計が shop_no を反映していない

- **箇所**: `PaymentMfImportService.java:86-87, 312-318`
- **問題**:
  - `ADVISORY_LOCK_CLASS = 0x7041_4D46` (高位32bit) + `transactionMonth.toEpochDay()` (低位32bit) という構造で、`shop_no` がキーに含まれていない。
  - 現状 shop_no = 1 固定 (`FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO`) なのでセーフだが、将来 shop_no=2 を追加した場合、別 shop の同じ取引月で待たされる。逆に同じ shop の同じ月では `transactionMonth` 一致で適切にシリアライズされる。
- **影響**: マルチショップ展開時に競合誤判定。`MEMORY.md` の B-CART 事業部統合方針で「将来統合前提」とあるが、買掛仕入は事業部別に運用され続ける可能性が残る。
- **修正案**:
  - 32bit 全部使うなら `transactionMonth.toEpochDay() & 0xFFFFFF` (24bit ≒ 4 万年) + `shop_no & 0xFF` (256 shop) など。シンプルには Long.hashCode((shopNo + ":" + transactionMonth).hashCode()) でも十分。
  - 設計書 §7 に advisory lock の存在自体が書かれていないため追記する (運用上「同時アップロードは直列化される」は重要な仕様)。

### M-4. `exportVerifiedCsv` の supplier 集約で「ルール未登録 / 非PAYABLE」を skip している間に `payment-supplier-code` 補完漏れが見逃される

- **箇所**: `PaymentMfImportService.java:495-502` (`exportVerifiedCsv`) および `buildVerifiedExportPreview` L627-634
- **問題**:
  - `t_accounts_payable_summary.supplier_code` で PAYABLE ルールを `byCode.get(s.getSupplierCode())` 引きするが、ルールに `payment_supplier_code` 未設定だと `byCode` に登録されない (L484-489)。
  - 結果として「PAYABLE ルールはあるが backfill-codes が走っていない supplier」は CSV から除外され、`skippedSuppliers` リストに「ルール未登録」と誤表示される (実際は「コード未補完」)。
  - `buildPreview` 側 (L851-857) は `rulesMissingSupplierCode` を別フィールドで返すように分岐しているのに、`exportVerifiedCsv` 側は同じ罠を踏んでいる。
- **影響**: 一括検証は PASS するが検証済みCSV出力で消える supplier が出る。経理が「ルール未登録」表示を信じてマスタ追加→重複ルールができる二次被害の可能性。
- **修正案**:
  - `exportVerifiedCsv` でも「PAYABLE ルールはあるが code 未補完」を別ステータスで `skippedSuppliers` に詰める (`"<code> (supplier_no=X) ルール `<source>` の payment_supplier_code 未補完。/finance/payment-mf-rules で『支払先コード自動補完』を実行してください")` 等)。
  - もしくは `bySource` ルックアップを fallback として追加し、source_name が一致するルールを採用する。ただし supplier 名が複数候補ある場合は誤マッチするので backfill 指示の方が安全。

### M-5. `applyVerification` の補助行洗い替えで「Excel 内の sequence_no」と「CSV 出力時のソート順」が一致しない可能性

- **箇所**: `PaymentMfImportService.java:340-372` (`saveAuxRowsForVerification`) と `PaymentMfPreviewResponse#getRows` の生成順 (`buildPreview` L788-895)
- **問題**:
  - `seq` は `preview.getRows()` を上から走査して PAYABLE / UNREGISTERED を skip しつつ +1 する。PAYABLE 行が間に挟まる Excel (本社仕入セクションに固定費送り先が混在するケース) では `sequence_no` が「0,1,5,8,10,...」のように飛ぶことはなく連番だが、**Excel 内の物理出現順 (rowIndex) とは一致しない**。設計書 §3.1 (claudedocs/design-payment-mf-aux-rows.md:54) では「Excel 内の出現順 (CSV出力順序維持)」と説明されているが、実装は「PAYABLE を除いた縮約順序」になっている。
  - CSV 出力時 (`exportVerifiedCsv` L545) は `transferDate ASC, sequenceNo ASC` で読むため、5日 Excel と 20日 Excel が混在する月の出力で、5日分の SUMMARY 2 行が EXPENSE 群より先に CSV に出てしまう (sequence_no が小さい順だが、SUMMARY は L878-895 の `rows.add` で末尾に追加されるため最大の seq になり、結果的に末尾に来る — これは現状動く)。ただし将来 SUMMARY 行を中間に置く Excel 形式変更があると壊れる。
- **影響**: 直近では運用通り。ただし設計書の「Excel 内の出現順」記述と乖離があり、Excel 形式の変化に脆い。
- **修正案**:
  - `seq` は `e.rowIndex` (PaymentMfExcelParser.ParsedEntry のフィールド L156) を採用する。SUMMARY 行は Excel に明細としては存在しないので、「合計行 rowIndex + 0.5 / +0.6」相当のソートキーを別途持たせる。
  - または「Excel 出現順を維持」を取り下げ、設計書 §4.3 の出力順序 (PAYABLE → 5日EXPENSE → 5日SUMMARY → 20日EXPENSE → 20日SUMMARY → DIRECT_PURCHASE) を明示する代わりに、`sequence_no` を `(ruleKind優先度, rowIndex)` の合成キーとして設計書に明示する。

### M-6. `MPaymentMfRule` Entity が `IEntity` 未実装で共通の `del_flg` 取扱いから外れている

- **箇所**: `backend/src/main/java/jp/co/oda32/domain/model/finance/MPaymentMfRule.java:11-83`
- **問題**:
  - プロジェクト規約 (`CLAUDE.md` 「論理削除: del_flg、IEntity インターフェース」) に従えば `IEntity` を implement すべきだが未実装。`TPaymentMfImportHistory` 同様。
  - `TPaymentMfAuxRow` (L143-174) は `IEntity` を意識的に実装せず物理削除運用と明記しているのに対し、`MPaymentMfRule` は `del_flg` を持っているのに `IEntity` 抜きで `delete()` 内で手動セット (`PaymentMfRuleService.java:82-89`)。
  - 監査フィールドも手動 (`addDateTime` / `addUserNo` / `modifyDateTime` / `modifyUserNo`)。`CustomService` を通せば共通化できる。
- **影響**: 規約違反。直接の不具合は無いが、将来「IEntity 経由で del_flg 検出」する横断機能 (例: `IGenericRepository` で論理削除フィルタ) を入れたときに漏れる。
- **修正案**:
  - `MPaymentMfRule implements IEntity` で `getDelFlg/setDelFlg` を実装し、`PaymentMfRuleService` を `CustomService` 経由に寄せる。`add_user_no` / `modify_user_no` の手書きを共通処理に委譲できる。
  - 既存テスト (Golden Master) はリフレクションで触っていないので互換維持可能。

---

## Minor 指摘

### m-1. `PaymentMfImportController#convert` で生成する CSV ファイル名が `payment_mf.csv` 固定で、HTTP ヘッダの `filename*=UTF-8''` 側だけ「買掛仕入MFインポートファイル.csv」を指している

- **箇所**: `PaymentMfImportController.java:71-77`
- **問題**: ASCII 互換 `filename` 部分が日付なし固定。`/export-verified` (L120) は `_yyyymmdd` 付きで揃っている。
- **修正案**: `convert` でも `cached.getTransferDate()` から `_yyyymmdd` を埋める。古いブラウザ向けに半角ファイル名も `payment_mf_${yyyymmdd}.csv` にする。

### m-2. `PaymentMfRuleService#backfillPaymentSupplierCodes` が dryRun=true でも `repository.findByDelFlgOrderByPriorityAscIdAsc("0")` を毎回実行

- **箇所**: `PaymentMfRuleService.java:97-178`
- **問題**: dryRun は admin が画面ボタンから繰り返し叩く想定。100件超のルール × MPaymentSupplier の名寄せを毎回 in-memory でやるのは妥当だが、ルール件数が増えると O(N×M) が無視できなくなる。
- **修正案**: 結果キャッシュは不要だが、`@Transactional(readOnly = true)` を dryRun=true 用に分けると read-only 最適化が効く (実装は `@Transactional` のみで read-write のまま)。

### m-3. `PaymentMfImportService#deriveTransactionMonth` が Service の package-private static でなくインスタンスメソッド

- **箇所**: `PaymentMfImportService.java:1011-1013`
- **問題**: `LocalDate` の純粋関数 (`transferDate.minusMonths(1).withDayOfMonth(20)`) で `this` 依存無し。テスト時に Service 全体をビルドする必要がある。
- **修正案**: `static LocalDate deriveTransactionMonth(LocalDate transferDate)` にする。`PaymentMfExcelParser` などの static utility 群と一貫する。

### m-4. `TPaymentMfAuxRow` の物理削除運用と `IEntity` 整合の説明が JavaDoc にしかない

- **箇所**: `TPaymentMfAuxRow.java:13-30`, `design-payment-mf-aux-rows.md:165-174`
- **問題**: 設計書 §3.2 Q2 に「論理削除を使わない理由」は書かれているが、`IEntity` の実装方針 (no-op del_flg) は設計書では言及されない。Entity 側 JavaDoc 経由でしか読み取れない。
- **修正案**: 設計書 §3.3 の Entity 説明に「`IEntity#getDelFlg` は常に `'0'` を返す no-op 実装。共通リポジトリのソフトデリートフィルタを通すためだけのスタブ」と追記。実装が `IEntity` を実装していないなら、その方針も明示。
- **追記**: 現状実装 (TPaymentMfAuxRow.java) は `IEntity` を **実装していない**。設計書 §3.3 のサンプルコードでは `implements IEntity` として書かれているが、L172-174 の `@Override` 付き no-op メソッドが現実装には無い。設計書サンプルと実装で差がある。

### m-5. `exportVerifiedCsv` の例外時に advisory lock を取得後 `auxRowRepository.find...` で空かつ DB 行も空のとき `IllegalStateException` (L471-475) を投げるが、advisory_xact_lock は tx 終了時に自動解放されるため OK だが Javadoc に明示無し

- **箇所**: `PaymentMfImportService.java:460-475`
- **問題**: advisory_xact_lock は @Transactional の境界で auto release という前提が JavaDoc にない (L312-318 にはある)。読者が安全性を確認するために L312 までスクロールする必要がある。
- **修正案**: `exportVerifiedCsv` の Javadoc にも「advisory lock は @Transactional 境界で自動解放されるため、early return しても解放漏れなし」を追記。

### m-6. PAYABLE ルールのシードが `payment_supplier_code = NULL` で 93 件投入され、運用直後の backfill 必須

- **箇所**: `backend/src/main/resources/db/migration/V011__create_payment_mf_tables.sql:55-153`
- **問題**: 設計書 §11 確定事項に「`m_payment_mf_rule` PAYABLE 74件に `payment_supplier_code` を自動補完済み (2026-04-15)」とある (実数 93 件と乖離があるが)。新規 deploy 環境ではシード直後 backfill 必須だが、この依存が migration の手順書に書かれていない。
- **修正案**: V012 等で「シード直後に `backfillPaymentSupplierCodes(dryRun=false)` 相当を実行する」ステップを SQL でやるか、起動時 ApplicationRunner で 1 回実行する仕組みを追加。少なくとも設計書 §10 実装順 と V011 ヘッダコメントに「migration 後に admin が backfill ボタンを押すこと」と明示。

### m-7. `PaymentMfCellReader#readLongCell` がオーバーフロー時に static long cast で silent truncate

- **箇所**: `PaymentMfCellReader.java:64-82`
- **問題**: `(long) cell.getNumericCellValue()` は double → long の暗黙キャストで、`Long.MAX_VALUE` を超える double は `Long.MAX_VALUE` に飽和する (Java 仕様)。Excel の請求額が `1e20` 等の異常値だった場合に検出できない。
- **修正案**: `if (Double.isNaN(d) || d > Long.MAX_VALUE || d < Long.MIN_VALUE) return null;` の上限ガード追加。あるいは `Math.round(d)` を使い、`AccountsPayable` 想定上限 (例: 100億円) でリジェクトする。

---

## 設計書 vs 実装の乖離

| # | 場所 | 設計書 | 実装 | 影響 |
|---|---|---|---|---|
| D-1 | §5.1 送金日マッピング | 「現行ルール: 5日/20日とも前月20日締め」(明示済) | コード Javadoc に「§5.1 の旧記述は是正対象」と残存 | C-2 で詳述。設計書とコードでどちらが最新か読み取れない |
| D-2 | §3.3 (`design-payment-mf-aux-rows.md`) | `TPaymentMfAuxRow implements IEntity { @Override no-op }` | `IEntity` 実装無し (L30 `@Table` 直書き) | m-4 / M-6。実装簡易化の判断は妥当だが設計書が古い |
| D-3 | §4.1 PAYABLE 反映ループ | `for (ParsedEntry e : cached.getEntries()) { /* PAYABLE 反映 */ }` の擬似コード | 実装は事前 codesToReconcile 集約 + payablesByCode 一括ロードで N+1 解消 | 設計書サンプルが N+1 ナイーブ実装になっている。 §4.1 を実装に合わせて更新するか「概念フロー」と注記 |
| D-4 | §11 確定事項 PAYABLE 74件 | `m_payment_mf_rule` PAYABLE 74件 | V011 シードは PAYABLE 93件 (シードに19件追加された?) | 設計書数値が古い。脚注で更新日付を明示 |
| D-5 | §7 uploadId キャッシュ | 「cashbook-import と同じパターン」 | 実装 L93-99 に「single-instance 前提」「マルチ化時は Redis 等」と明記 | M-2。設計書に HA 制約を追記 |
| D-6 | §7 advisory lock | 言及なし | `pg_advisory_xact_lock(transactionMonth.toEpochDay)` で applyVerification/exportVerifiedCsv を直列化 | M-3。設計書追記必要 |
| D-7 | §6.3 (`design-payment-mf-aux-rows.md`) 警告判定 | `transferDate 範囲 = transactionMonth + 1ヶ月の 5日 と 20日` | 実装 L662-681 は `PAYMENT_DATE_MIDMONTH_CUTOFF = 15` 境界の前半/後半判定 (土日振替対応) | 実装側が現実に合わせて改善されている。設計書を実装に追従させる |
| D-8 | §10.5 5日/20日整合性チェック | 「v2 では緩く (警告ログ) でスタート」 | 実装 L658-681 で警告メッセージ生成済み (緩い実装) | 設計通り |

**特筆**: §5.1 (D-1) は `MEMORY.md` の feedback「設計書とコードが食い違う時は運用実態を優先」「ゴールデンマスタが empty stub している箇所は PASS が正当性を保証しない」に直撃する案件。ゴールデンマスタテスト (`PaymentMfImportServiceGoldenMasterTest`) は `payableRepository.findByShopNoAndSupplierCodeAndTransactionMonth` を `Collections.emptyList()` で stub しているため、突合ロジックの妥当性は **テストでは検証されていない**。CSV のフォーマット等価性は確認できているが、`deriveTransactionMonth` のずれが起きても PASS する状態。手元の運用検証で「実 t_accounts_payable_summary との突合がきちんと動いているか」を別途確認することを強く推奨。

---

## レビューチェックリスト結果

### Spring Boot 観点

| 項目 | 結果 | コメント |
|---|---|---|
| Layer 違反 | OK | Controller→Service→Repository の階層遵守。`PaymentMfImportController` は薄い |
| `@Transactional` 配置 | 概ね OK / 1 NG | `applyVerification` / `exportVerifiedCsv` / `saveVerifiedExportHistory(REQUIRES_NEW)` 適切。`PaymentMfRuleService#findAll` (L30) は readOnly 指定無し → m-2 |
| N+1 解消 | OK | `payableRepository.findByShopNoAndSupplierCodeInAndTransactionMonth` で IN 化済み (L40)。コメント B-W11 に解消経緯記載 |
| DI | OK | constructor injection (`PaymentMfImportService` L101) + `@Lazy` 自己注入で REQUIRES_NEW proxy 確保 (L66-72) は妥当 |
| DTO 変換 | OK | `PaymentMfPreviewRow.builder()` / `PaymentMfRuleResponse.from(entity)` 等。Entity を直接返さない |
| バリデーション | △ | `PaymentMfRuleRequest` は `@NotBlank` 散発。`ruleKind` の値域 (PAYABLE/EXPENSE/DIRECT_PURCHASE) は `@Pattern` または ENUM 化が望ましい |
| Migration 安全性 | OK | V011 で `IF NOT EXISTS`。V016 補助行 migration も `IF NOT EXISTS` + 部分インデックス。ただし re-run 時の seed 重複は防げない (`INSERT INTO m_payment_mf_rule` に `ON CONFLICT DO NOTHING` 無し)。本番環境で V011 を 2 度流すと PAYABLE 93件 × 2 になる潜在リスク |

### Payment MF 固有観点

| 項目 | 結果 | コメント |
|---|---|---|
| CSV 形式 (CP932 + LF + 末尾半角スペース) | OK | `PaymentMfCsvWriter.java:29-30, 95-98` で実装済。Charset は `Charset.forName("MS932")` で例外時クラッシュ無し |
| afterTotal フラグ (PAYABLE→DIRECT_PURCHASE 降格) | △ | M-1 参照。`buildPreview` のみで降格、`applyVerification` は skip と非対称 |
| 送金日→取引月マッピング (前月20日締め固定) | OK / 設計書NG | 実装 (`deriveTransactionMonth` L1011) は `transferDate.minusMonths(1).withDayOfMonth(20)` で運用実態通り。C-2 / D-1 |
| 補助行洗い替え (UNIQUE 制約・再投入) | OK / △ | 物理 DELETE + saveAll で `(shop_no, transaction_month, transfer_date)` 単位の洗い替え。UNIQUE 制約は migration に無いが、洗い替え運用なので DB 層には不要。M-5 (sequence_no の Excel 順) は別問題 |
| 100円閾値突合 | OK | `FinanceConstants.PAYMENT_REPORT_MINOR_DIFFERENCE` (L75) で集約、コード散らばり無し |
| Excel パーサ異常系 | △ | `PaymentMfCellReader#readLongCell` で long overflow を silent truncate (m-7)。NaN/Inf もハンドル不十分 |
| 設計書とコードの食い違い | NG | D-1 〜 D-7 (特に D-1) |

---

## 評価まとめ

実装品質自体は高く、N+1 対応・advisory lock・self injection (REQUIRES_NEW)・cache TTL/サイズ制御など、本番運用を意識した防御コードが入っている。ゴールデンマスタテスト 2 本 PASS で CSV 出力の正確性も担保されている。

しかし以下の観点で **マージ前に対応すべき** 課題が残る:

1. **C-1 セキュリティ**: `convert` / `verify` の admin 必須化は最優先 (ロール権限抜け)
2. **C-2 / D-1 設計書整合**: §5.1 と Javadoc の矛盾解消。再発防止のため運用に再確認
3. **C-3 / M-5 データ整合**: 複数税率 supplier の検出、sequence_no の Excel 順整合

その他 Major は v2.1 ドット リリースで対応可能だが、特に **M-1 (DIRECT_PURCHASE 降格の責務分散)** は将来的なバグ温床なので近いリリースで整理推奨。Minor は時間が許す範囲で。

設計書側の更新項目 (D-2, D-4, D-5, D-6) は本レビュー後に実装担当者が一括反映すれば、後続レビュアーが「設計書 vs コード」の二重確認をしなくて済む。
