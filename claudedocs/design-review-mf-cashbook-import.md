# 設計レビュー: MF Cashbook Import (Cluster B)

レビュー日: 2026-05-04
対象設計書: `claudedocs/design-mf-cashbook-import.md`
レビュアー: Opus サブエージェント
対象ブランチ: `refactor/code-review-fixes`

---

## サマリー
- 総指摘件数: **Critical 1 件 / Major 6 件 / Minor 5 件**
- 承認状態: **Needs Revision**
- ゴールデンマスタ12本 PASS により出力意味等価は担保されているが、設計書とコード/シードの記述齟齬、CSV 改行 / セキュリティチェックの仕様乖離、認可仕様の意図と実装の食い違いがある。回帰テスト範囲も「シード経路」を経由していないため設計書記載の `priority` 解釈などには未通過パスがある。

---

## Critical 指摘

### C-1. `mf-client-mappings` 認可仕様が設計書と乖離 (誰でもCRUD全操作可ではないが、create だけは一般ユーザに開放されている)
- **箇所**: `backend/src/main/java/jp/co/oda32/api/finance/MfClientMappingController.java:27-31`、設計書 §セキュリティ
- **問題**:
  - 設計書 §セキュリティでは「`mf-client-mappings` / `mf-journal-rules` の**書き込み系**（POST/PUT/DELETE）」を `hasAuthority('ADMIN') or shopNo == 0` 限定としている。
  - しかし `MfClientMappingController#create` は `// 一般ユーザでも追加可（現金出納帳取込のマッピング補正UX）` のコメント付きで `@PreAuthorize` を付けず、クラス側 `isAuthenticated()` のみで動作している。
  - 結果: 認証済みのどのユーザも `m_mf_client_mapping` に任意エイリアス → 任意 MF 得意先名を **無制限に追加** できる。alias 重複制約はあるが、未登録 alias を狙って `mf_client_name = "別の正規取引先"` を仕込めば、後続の現金出納帳取込が誤った得意先で MF にアップロードされる。
  - これは**仕訳金額の取引先誤付け**（売掛金 / 未収入金の補助科目改ざん）に直結し、会計データ汚染の入口になる。
- **影響**: 仕訳の取引先が改ざんされ、月次決算の信頼性を損なう。監査でも追跡困難（`add_user_no` のみ。MF 側に流れた CSV からは取り消しが手動）。
- **修正案**:
  - 案A (設計書通りに厳格化): `create` も `@PreAuthorize("authentication.principal.shopNo == 0")` を付与。一般ユーザは未マッピング得意先を**起票**だけし、admin が承認するワークフローに変更。
  - 案B (運用 UX 優先): create を一般ユーザ可のまま残すなら、設計書を更新し、かつ `m_mf_client_mapping` に「pending / approved」状態カラムを追加。`approved` のものだけ `CashBookConvertService#resolveClient` でヒット対象にする。
  - 最低限: 設計書とコードのどちらかを必ず一致させる。現状は仕様書と実装が矛盾している状態。

---

## Major 指摘

### M-1. CSV 改行コードが設計書と実装で食い違い (LF 設計 vs CRLF 実装)
- **箇所**:
  - 設計書: §非機能 / §CSV出力 「Java側は固定CSVフォーマット（UTF-8 BOM、**LF**、QUOTE_MINIMAL相当...）」
  - 実装: `CashBookConvertService.java:556, 580` で `"\r\n"` を出力、Cluster A (`PaymentMfCsvWriter`) との対比でも MEMORY.md 記載の「CP932 + LF」と差別化されている。
- **問題**: 設計書は LF と明記しているが実装は CRLF。`MEMORY.md` の MF Cashbook 記載「Python版と意味等価（**CRLF+BOM CSV**）」と整合するのは実装側。設計書側が古い。
- **影響**: 設計書を信じて改行を変えるとゴールデンマスタが破壊される。逆に設計書を真とした人が「LF に戻すべきだ」と直してしまうとリグレッションする。
- **修正案**: 設計書 §非機能 / §CSV出力 を「CRLF」に修正し、決定理由（Python `pandas.to_csv` 出力との意味等価維持）を残す。

### M-2. 設計書 §シード「19件」と実際のシード18件の乖離 (No.13 が落ちている)
- **箇所**:
  - 設計書 §シード: 「ルール一覧（全17件）」と本文中で言いつつ表は #1–#19 まで存在し、注記で「**19件**中 ユニーク desc_c 10種」と記載
  - 実装: `backend/src/main/resources/db/migration/V008__create_mf_cashbook_tables.sql:43` 「**19件**、priority小=高優先度」とコメントしながら `INSERT` は **18 タプル**（#13「運賃(全半混在)」が欠落）
- **問題**:
  - design テーブル #13「運賃(全半混在 "運  賃")」は normalize で `運賃` と一致するため #12 と機能重複。設計書注記でもそう述べているが、シードにも回帰テストの `parseRules` 経由 (`ruleRepository` をモック) でもこのレコードは存在しない。「全半混在表記の揺れ吸収」の本旨は `normalize()` 側で実現されているので機能上は問題ないが、**設計書が「19件」と「17件」と「19件中ユニーク10種」を混在させており、コメントも実体18件と矛盾**している。
  - シード SQL コメント `(19件、priority小=高優先度)` も実体と不一致。
- **影響**: 後続の保守者が「ルール何件あるはず？」を一意に判断できず、ルール追加/削除時の妥当性チェックが不能。
- **修正案**:
  - 設計書: 表ヘッダの「全17件」「（実質ルール19件中 ユニーク desc_c 10種）」を削除し、「正規化により全半空白入り表記は同一ルールに集約されるため、シードは18件」と統一。
  - シード SQL `V008__create_mf_cashbook_tables.sql:43` コメントを `(18件, priority小=高優先度)` に修正。

### M-3. 設計書税リゾルバ表に `PURCHASE_AUTO_WIDE` が欠落、シードでは利用中
- **箇所**:
  - 設計書 §税区分リゾルバ表 (`design-mf-cashbook-import.md:48-60`): `OUTSIDE` ～ `PURCHASE_AUTO` の8種のみ記載
  - 実装: `MfTaxResolver.java:26-28` に `PURCHASE_AUTO_WIDE` 実装あり。`V008__create_mf_cashbook_tables.sql:61, 65` で `雑費` / `福利厚生費` の2ルールが `PURCHASE_AUTO_WIDE` を使用
  - `MEMORY.md` には「雑費/福利厚生費は `PURCHASE_AUTO_WIDE`（軽8%+軽８％）」と運用注意あり
- **問題**:
  - 設計書 §シードのルール表 #15 (雑費 NULL) と #19 (福利厚生費) は `PURCHASE_AUTO` と書かれており、実装の `PURCHASE_AUTO_WIDE` と一致しない。
  - 9 つ目の resolver コードが設計書からのみ漏れているため、新規ルールを追加する開発者は「全角 ％ を含む摘要では 10% 課税扱いになる」というバグを再導入する可能性が高い。
- **影響**: 仕訳ルール改修時にコピペで `PURCHASE_AUTO` を使ってしまうと、経理担当が `軽８％` (全角％) と書いた行が誤って `課税仕入 10%` で集計される。消費税申告書ベースで誤額になる。
- **修正案**:
  - 設計書 §税区分リゾルバ表に `PURCHASE_AUTO_WIDE`（D列「軽8%」「軽８％」含む / `課税仕入 (軽)8%` / else `課税仕入 10%`）行を追加。
  - §シード表 #15, #19 の「借方税」を `PURCHASE_AUTO_WIDE` に修正。
  - §税区分リゾルバ冒頭の「Python全文を精査し、税区分語彙は `軽8%` / `軽８％` のみ」記述と、リゾルバ実装が分かれている事実（雑費/福利厚生のみ全角％吸収）の根拠を脚注で明示。

### M-4. POI XSSFWorkbook のメモリ消費と Zip Bomb 防御の前後関係
- **箇所**: `CashBookConvertService.java:60-63 (initPoiSecurity)` と `:70 (new XSSFWorkbook(file.getInputStream()))`
- **問題**:
  - `ZipSecureFile.setMinInflateRatio` / `setMaxEntrySize` は `@PostConstruct` で1回設定しているので Bean 初期化後は有効。実装方針は妥当。
  - ただし `XSSFWorkbook(InputStream)` は**全シートをメモリに展開**する。設計書 §セキュリティ「シート数上限10、データ行数上限10000」は POI が読み終わったあとの post-validation であり、上限突破ファイルでも一旦メモリには載る。20MB の `.xlsx` でも展開後 200MB 超になる現金出納帳は実在し得る（多数年分・重い書式）。
  - 単一インスタンスかつ同時アップロード数も制限なし。`enforceCacheLimit` も100件、メモリ的には危うい。
- **影響**: 同時アップロード数がスパイクすると OOM → backend 全停止。 cashbook と関係ない API も巻き込まれる。
- **修正案**:
  - `OPCPackage.open(InputStream)` + `XSSFReader` で SAX 風ストリーミング解析に変更（行数上限を**読みながら**判定、上限超過で即時 abort）。
  - もしくは preview API に同時実行ガード（Semaphore (permit=2~3) など）と、 `MAX_UPLOAD_BYTES` の縮小（cashbook xlsx は実態 1～2MB なので 5MB で十分）。
  - 設計書 §セキュリティに「同時実行制御」「ストリーミング/インメモリ方針」を明記。

### M-5. CSV ダウンロード後の `csv_content` 永続化に **PII / 取引先名 / 金額** がそのまま入る
- **箇所**: `CashBookConvertService.java:137, 142-171 (saveHistory)`、`TCashbookImportHistory.java:37-38 (csv_content TEXT)`
- **問題**:
  - 生成 CSV 全文を `t_cashbook_import_history.csv_content` に **plain text 保存**。CSV には取引先名・金額・摘要（個人名「畑田 和彦」等）が含まれる。
  - `t_cashbook_import_history` には RBAC が一切無く、Repository に `findById` / `findFirstByOrderByProcessedAtDesc` がある。今後この履歴を参照する API ができた場合、一般ユーザでも他事業部の個人情報含む現金出納帳全文にアクセス可能になりかねない。
  - 設計書 §バックエンド §uploadIdキャッシュには「**保持内容**: パース済み中間データのみ。ルール適用結果は保持しない」と明記されているが、実装は **CSV 全文を DB に永続化**しており、設計書の保持方針と矛盾。
- **影響**: 機微情報の保管範囲が設計より広く、GDPR / 個人情報保護法的にも見直し対象。再 DL 機能を提供する場合も認可設計が抜けている。
- **修正案**:
  - 案A: `csv_content` カラムを廃止し、**ハッシュ (SHA-256)** とサマリ (件数、合計金額、期間) のみ保存。再生成は uploadId キャッシュ or 再アップロードで対応。
  - 案B: `csv_content` を残すなら `t_cashbook_import_history` に `shop_no` を追加し、参照 API を `shopNo == 0` 限定にする。設計書にも「取込 CSV 履歴を保持する」旨と保持期間ポリシーを記載。
  - いずれにせよ設計書 §uploadIdキャッシュの「ルール適用結果は保持しない」記述と整合させる。

### M-6. `findRule` の amountSource ガードがゴールデンマスタテストで未通過 (income と payment が両方 >0 の行のみ発火する補助条件)
- **箇所**: `CashBookConvertService.java:511-513`
- **問題**:
  - `findRule` は同一 `descriptionC` で `INCOME` ルールと `PAYMENT` ルールが共存する場合、 `income > 0` の行に `PAYMENT` ルールがマッチしないようガードしている。
  - しかし現行シードに同一 `description_c` で INCOME と PAYMENT が両方あるルール集合は存在しない（`ｺﾞﾐ袋未収金` `売掛金` `売上` `雑収入` は INCOME 系、それ以外は PAYMENT 系）。
  - したがってこの分岐はゴールデンマスタテストで**1度も実行されない**。設計書にもこの分岐の意図は記載されていない。意図せぬデッドコードか、将来の保険か曖昧。
- **影響**:
  - 仕様未文書化のため、新ルール追加時に「INCOME/PAYMENT 同名ルール」を作ると暗黙の選別が走り、想定外の組み合わせで PAYMENT ルールがスキップされる。
  - 例: 現金出納帳に `売上` (income) で大量返品の `payment > 0` 行が現れたら、現状のロジックでは PAYMENT 側ルールが無いので `UNKNOWN_DESCRIPTION_C` を経由せず無視される（`income == 0 && payment != 0` の行は INCOME ルール側でも `amount = income = 0` で行かれてしまうケースもあり得る）。
- **修正案**:
  - 設計書 §Excel解析ロジック step 5 に「INCOME/PAYMENT amount_source は当該行の値で 0 でない側のみ採用」と明記。
  - 単体テストを `CashBookConvertServiceRuleTest`（設計書 §テスト記載だが**未実装**、後述 m-3）に追加し、INCOME/PAYMENT 両側ルールが共存するケースを最低 2 ケース通す。
  - もしくは現状デッドコードであることを明示するコメントを残す。

---

## Minor 指摘

### m-1. `MfTaxResolver` が default で `IllegalArgumentException` 投げるが Controller で 500 にしか変換されない
- **箇所**: `MfTaxResolver.java:28`、`CashBookController.java:33-39`
- **問題**: 未知の `tax_resolver` コードがマスタに紛れた場合、 preview 中に `IllegalArgumentException` → catch されて `400 Bad Request` ではなく、`buildPreview` 経路は `try-catch` 範囲内なので 400 になる。しかし「マスタ起因のシステムエラー」を `400 Bad Request` で返すのは誤解を招く（クライアントが対応すべきものではない）。
- **修正案**: `MfTaxResolver` の例外は `IllegalStateException` （マスタ不正）に変え、Controller 側で 500 と「管理者にルール確認を依頼してください」を返す。

### m-2. `CashBookController` クラスに `@PreAuthorize("isAuthenticated()")` を付けているが、デフォルト Spring Security 設定で全 API 認証必須なら冗長
- **箇所**: `CashBookController.java:22`、`MfClientMappingController.java:16`、`MfJournalRuleController.java:16`
- **問題**: SecurityFilterChain で `anyRequest().authenticated()` 設定済みであれば、クラス `@PreAuthorize("isAuthenticated()")` は無効効。逆に、メソッド単位の `@PreAuthorize("authentication.principal.shopNo == 0")` を付けたメソッドではクラス側の `isAuthenticated` が**上書き**されて見える可能性があり、混乱の元。
- **修正案**: クラスの `isAuthenticated()` は外し、書き込み系メソッドのみメソッドレベル `@PreAuthorize` を付ける。読み取り系は SecurityFilterChain に任せる。

### m-3. 設計書 §テスト記載の `CashBookConvertServiceRuleTest`、`MfJournalRuleServiceTest`、`MfClientMappingServiceTest`、Playwright `cashbook-import.spec.ts` の存在状況が一致していない
- **箇所**: 設計書 §テスト
- **現状確認**:
  - 存在する: `CashBookConvertServiceGoldenMasterTest.java`、`frontend/e2e/cashbook-import.spec.ts`
  - **不在**: `CashBookConvertServiceRuleTest`、`MfJournalRuleServiceTest`、`MfClientMappingServiceTest`
- **影響**: ルール CRUD のバリデーション（`emptyToNull` 変換、alias 重複検出、 update 時の alias 変更時のみ重複チェック）が回帰保証されない。
- **修正案**: 不在の 3 テストを実装するか、設計書 §テストから削除して `CashBookConvertServiceGoldenMasterTest` のみ記載に揃える。

### m-4. 内部 DTO `ParsedRow` / `CachedUpload` が `@Data` (mutable Lombok) かつ Service クラスの static inner class
- **箇所**: `CashBookConvertService.java:680-705`
- **問題**: グローバル CLAUDE.md 「Immutability: always create new objects, never mutate」に反する。`ParsedRow` は parse 後に変更されないので record で十分。また Service 内 inner class はテスト時の組み立てが煩雑（テストコード `parseRules` も `MMfJournalRule.builder()` だけで完結している）。
- **修正案**: `ParsedRow` / `CachedUpload` を Java record 化し、 Service 外（同パッケージ）に切り出す。

### m-5. 設計書 §アップロード TTL 30分とコード5分間隔のクリーンアップで「TTL残ったまま」が最大35分続く
- **箇所**: `CashBookConvertService.java:55, 173-177`
- **問題**: 設計書 §uploadIdキャッシュ「30分TTL」は `getCached` 経由のアクセス時 expiresAt 判定で守られているので体感には影響しないが、Scheduled cleanup と TTL の関係を設計書に書いておくと運用 (memory profile) で迷わない。
- **修正案**: 設計書注記に「ScheduledExecutor で 5 分間隔の lazy cleanup。同期 evict は `enforceCacheLimit()` で 100 件超過時のみ」と追記。

---

## 設計書 vs 実装の乖離一覧

| # | 設計書記載 | 実装 / 実体 | Severity |
|---|---|---|---|
| 1 | `mf-client-mappings` 書き込み系は admin 限定 | `create` のみ一般ユーザ可（コメント残） | Critical (C-1) |
| 2 | CSV 改行 LF | `\r\n` (CRLF) 出力 | Major (M-1) |
| 3 | シード 17件 / 19件 (本文と表で異なる) | INSERT は 18 タプル、SQL コメントは「19件」 | Major (M-2) |
| 4 | 税リゾルバ 8種、シード #15/#19 は `PURCHASE_AUTO` | resolver 9種 (`PURCHASE_AUTO_WIDE` 追加)、シードは `PURCHASE_AUTO_WIDE` | Major (M-3) |
| 5 | uploadId キャッシュは「中間データのみ保持、ルール適用結果は保持しない」 | CSV 全文を `t_cashbook_import_history.csv_content` に永続化 | Major (M-5) |
| 6 | テスト: Rule / Service / E2E | RuleTest と CRUD Service Test 不在 | Minor (m-3) |
| 7 | 期間ラベル抽出ロジック (Sheet2 R4 から `~` 含む文字列) | `extractPeriodLabel` 実装あり | (設計書未記載) Minor |
| 8 | 右半分カラム (列14-21) のパース | `parseSheet` で `hasRightHalf` 判定 + `parseHalf` 二回呼び | (設計書未記載) Minor |
| 9 | 取込履歴 `t_cashbook_import_history` テーブル | 設計書 §新規テーブルに記載なし、Entity 実装あり | Major (設計書欠落) |

「設計書未記載だが実装存在」項目（#7・#8・#9）は、設計書 §Excel解析ロジック / §新規テーブル に追記すべき。特に #9 (取込履歴テーブル) はデータ保持・PII 観点 (M-5) と表裏一体なので必須。

---

## レビューチェックリスト結果

### Spring Boot 観点
- [x] **Layer 違反なし** — Controller は薄く、ビジネスロジックは Service に委譲できている
- [x] **DI: constructor injection** — `@RequiredArgsConstructor` 適用、 reflection 系なし
- [ ] **@Transactional スコープ** — `MMfClientMappingService` / `MMfJournalRuleService` は readOnly / 書込 を明示しているが、`CashBookConvertService#convert` は `saveHistory` を含むのに `@Transactional` 無し。`historyRepository.save` が単一行ながら、 read-modify-write (`findByPeriodLabel` → `save`) で並行アップロード時の race window あり。`@Transactional` + `UNIQUE (period_label)` への upsert ハンドリングが必要 (Major 候補だがゴールデンマスタ では同名 period の同時アップロードは想定外なので Minor 寄り)
- [x] **DTO 変換** — Entity 直接 JSON 露出はない (`Response.from(entity)` パターン遵守)
- [x] **Bean Validation** — `MfClientMappingRequest` / `MfJournalRuleRequest` で `@NotBlank` / `@NotNull` 適用済み
- [ ] **Validation 不足** — `MfJournalRuleRequest.amountSource`、`debitTaxResolver` / `creditTaxResolver` は `@NotBlank` のみで、enum 値検証なし。任意文字列を投入されると `MfTaxResolver.resolve` が `IllegalArgumentException` を後段で投げる（m-1 参照）。`@Pattern(regexp = "INCOME|PAYMENT")` / カスタム `@MfTaxResolverCode` バリデータ追加推奨
- [x] **N+1 / EntityGraph** — preview 1 回あたり ルール / マッピングを各1回 fetch、N+1 無し
- [x] **Migration 安全性** — V008 は `IF NOT EXISTS` 付きで idempotent。 down は無いが seed 入りの再実行は重複 INSERT になる点は認識すべき
- [ ] **Migration 注意点** — V008 の seed は idempotent でない (`INSERT INTO ... VALUES ...` のみ、`ON CONFLICT DO NOTHING` 無し)。Flyway は再実行しないので問題は起きないが、後発 migration で seed を変更したい場合の方針 (`UPDATE` か新 migration で diff を当てる) を `migration-guide.md` に追記推奨

### MF Cashbook 固有観点
- [ ] **CSV 出力形式** — 設計書 LF と実装 CRLF が乖離 (M-1)。19列ヘッダー、UTF-8 BOM、QUOTE_MINIMAL 風 (`csvEscape`) は実装と整合
- [ ] **税リゾルバの正確性** — `PURCHASE_AUTO_WIDE` 設計書欠落 (M-3)。`SALES_AUTO` は `軽8%` 半角％のみ判定で全角％無し。これは設計書通り (Python 実装も同様らしい) で、雑収入は実運用上 全角％ 不在のため OK
- [x] **m_mf_journal_rule 運用と更新フロー** — admin 限定で安全、 priority 順 + キーワードフィルタの設計は妥当
- [ ] **取込履歴 (`TCashbookImportHistory`) の重複チェック** — `period_label` UNIQUE で同期間2回目はUPDATE 動作。設計書には未記載 (M-5 / 乖離 #9)
- [ ] **設計書とコードの食い違い** — Critical 1 / Major 6 件、特に PII 永続化 (M-5) と認可矛盾 (C-1) は merge 前に判断必須

### コード品質 (Global Rules)
- [x] No hardcoded secrets
- [x] Parameterized queries (JPA 経由のみ)
- [ ] **Immutability** — `ParsedRow` / `CachedUpload` が mutable Lombok `@Data` (m-4)
- [x] **Small file** — `CashBookConvertService` 707 行、上限 800 行内 (CLAUDE.md 規定) だが、CSV 出力 / Excel パース / cache 管理 / history 保存 が混在しており分割推奨 (Minor)
- [x] **Small function** — 50行超は `parseHalf` (101 行) / `buildPreview` (75 行) のみ。`parseHalf` は state machine 性質上やむなしだが、月日取得 / 摘要取得 を private method に切り出すと読みやすい (Minor)

---

## 推奨アクション (merge 前)

1. **Critical C-1** を意思決定: `mf-client-mappings#create` を admin 限定にするか、設計書を「補正 UX のため一般ユーザ可」に書き換えるか。
2. **Major M-1, M-2, M-3, M-5** の設計書/コード整合：
   - 設計書を実装に追従 (LF→CRLF、17/19件→18件、税リゾルバ表に WIDE 追加、取込履歴テーブル追記)
   - M-5 のみコード側修正 (csv_content 廃止 or RBAC) を検討
3. **Major M-4** ストリーミング解析 / 同時実行ガード を別 PR で計画。
4. **Minor m-3** 不在テストの実装 or 設計書記述削除を選択。
5. ゴールデンマスタ12本 PASS は維持しつつ、 INCOME/PAYMENT amount_source ガードのテストケース (M-6) を追加。

---

## 参考: 確認したファイル
- `claudedocs/design-mf-cashbook-import.md`
- `backend/src/main/java/jp/co/oda32/api/finance/CashBookController.java`
- `backend/src/main/java/jp/co/oda32/api/finance/MfClientMappingController.java`
- `backend/src/main/java/jp/co/oda32/api/finance/MfJournalRuleController.java`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/CashBookConvertService.java`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/MfTaxResolver.java`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/MMfClientMappingService.java`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/MMfJournalRuleService.java`
- `backend/src/main/java/jp/co/oda32/domain/model/finance/MMfJournalRule.java`
- `backend/src/main/java/jp/co/oda32/domain/model/finance/MMfClientMapping.java`
- `backend/src/main/java/jp/co/oda32/domain/model/finance/TCashbookImportHistory.java`
- `backend/src/main/java/jp/co/oda32/domain/repository/finance/MMfJournalRuleRepository.java`
- `backend/src/main/java/jp/co/oda32/domain/repository/finance/MMfClientMappingRepository.java`
- `backend/src/main/java/jp/co/oda32/domain/repository/finance/TCashbookImportHistoryRepository.java`
- `backend/src/main/java/jp/co/oda32/dto/finance/cashbook/*.java`
- `backend/src/main/resources/db/migration/V008__create_mf_cashbook_tables.sql`
- `backend/src/test/java/jp/co/oda32/domain/service/finance/CashBookConvertServiceGoldenMasterTest.java`
- `backend/src/test/resources/cashbook/` (12 ゴールデンマスタ + 補助 fixture)
- `frontend/components/pages/finance/cashbook-import.tsx`
