# 買掛金一覧画面 テスト計画

作成日: 2026-04-14
対象設計書: `claudedocs/design-accounts-payable.md`
対象ブランチ: `feat/accounts-payable`
参照テスト例: `frontend/e2e/bcart-shipping-input.spec.ts`, `frontend/e2e/cashbook-import.spec.ts`, `frontend/e2e/helpers/{mock-api,auth}.ts`

## 0. テスト方針

- バックエンドは JUnit5 + Mockito + `@SpringBootTest(webEnvironment=MOCK)` 系。Controller は `MockMvc`、Service は純粋単体（Mockito）、バッチ単体は Tasklet/Reader/Writer の単独テスト。
- フロントは Playwright Chromium headless、原則 `mockAllApis(page)` → `loginAndGoto(page, path)` パターン。実バックエンド疎通ケースは 1 本だけモック無効で実施（既存ルール）。
- 手入力保護（`verified_manually`）と多重実行ガード（`JobExplorer` RUNNING→409）はそれぞれ独立セクションで網羅。

---

## 1. バックエンド単体テスト（JUnit5）

### 1.1 `TAccountsPayableSummaryServiceTest`

| # | ケース名 | 対象メソッド | Given / When / Then |
|---|---|---|---|
| BE-S-01 | `findPaged_shopNoのみ指定_対象月全件返る` | `findPaged` | Given: shopNo=1 の 3 件 / When: shopNoのみ / Then: 3件、sort=`supplierCode ASC` |
| BE-S-02 | `findPaged_transactionMonthで絞込` | `findPaged` | Given: 2026-03-20 と 2026-02-20 を混在 / When: transactionMonth=2026-03-20 / Then: 3月分のみ |
| BE-S-03 | `findPaged_verificationResult_unverifiedはNULLのみ` | `findPaged` | Given: 0/1/null 各1件 / When: filter=unverified / Then: nullのみ返る |
| BE-S-04 | `findPaged_verificationResult_unmatchedは0のみ` | `findPaged` | 同上 / filter=unmatched / Then: 0のみ |
| BE-S-05 | `findPaged_supplierNo組み合わせ絞込` | `findPaged` | 4条件全指定 / Then: Specification が AND で連結 |
| BE-S-06 | `verify_差額100円未満で一致判定` | `verify` | Given: taxIncludedAmountChange=1000000, verifiedAmount=1000050 / Then: verificationResult=1, paymentDifference=50, verifiedManually=true |
| BE-S-07 | `verify_差額101円以上で不一致` | `verify` | 差額=101 / Then: verificationResult=0 |
| BE-S-08 | `verify_税抜額を税率から逆算して保存` | `verify` | 税率=10, verifiedAmount=1100 / Then: taxExcludedAmount=1000 |
| BE-S-09 | `verify_軽減税率8%で逆算` | `verify` | 税率=8, verifiedAmount=1080 / Then: taxExcludedAmount=1000 |
| BE-S-10 | `verify_備考500字まで保存_501字でバリデーション例外` | `verify` | Given: 501字 / Then: `ConstraintViolationException` |
| BE-S-11 | `verify_既存verificationNoteを上書き` | `verify` | Given: 既存 note="旧" / When: note="新" / Then: DB に "新" |
| BE-S-12 | `releaseManualLock_フラグをfalseに戻す` | `releaseManualLock` | Given: verifiedManually=true / Then: false、note と amount は保持 |
| BE-S-13 | `releaseManualLock_PK不存在でEntityNotFound` | `releaseManualLock` | 存在しないPK / Then: 404相当 |
| BE-S-14 | `summary_件数と差額合計を返す` | `summary` | Given: 一致1/不一致2(差額-1000,-2000)/未検証2 / Then: total=5, unverified=2, unmatched=2, sum=-3000 |
| BE-S-15 | `summary_該当月データ無し_ゼロ返却` | `summary` | Then: 全カウント=0、例外出ない |
| BE-S-16 | `updateMfExport_フラグ切替` | `updateMfExport` | enabled=false / Then: mfExportEnabled=false |

### 1.2 `SmilePaymentVerifierTest`（手入力保護の核）

| # | ケース名 | Given / When / Then |
|---|---|---|
| BE-V-01 | `verify_verifiedManuallyがtrueの行をスキップ` | Given: 3 行中 1 行が verifiedManually=true / When: バッチ実行 / Then: 2 行のみ更新、手動行は既存値維持 |
| BE-V-02 | `verify_スキップ件数をINFOログに出力` | 上記 / Then: logspy で "skipped manually verified: 1" を検出 |
| BE-V-03 | `verify_verifiedManually=falseは通常通り上書き` | Given: false の行 / Then: SMILE 支払額で上書き、verificationResult 再計算 |
| BE-V-04 | `verify_verifiedManuallyがnullでもtrue扱いしない` | null=false 互換 / Then: 上書き対象 |

### 1.3 `FinanceControllerTest`（MockMvc）

| # | ケース名 | エンドポイント | Given / When / Then |
|---|---|---|---|
| BE-C-01 | `GET_list_200_ページネーション形式` | `GET /api/v1/finance/accounts-payable` | 認証済 / Then: `Paginated<AccountsPayableResponse>` 形状、supplierCode/Name 含む |
| BE-C-02 | `GET_list_未認証401` | 同上 | 未ログイン / Then: 401 |
| BE-C-03 | `GET_summary_200` | `GET /summary?transactionMonth=2026-03-20` | Then: 5フィールド返却 |
| BE-C-04 | `PUT_verify_200_DTO返却` | `PUT /{shop}/{sup}/{month}/{rate}/verify` | Body: verifiedAmount+note / Then: 更新後 DTO |
| BE-C-05 | `PUT_verify_備考501字_400` | 同上 | note=501字 / Then: 400 + バリデーションメッセージ |
| BE-C-06 | `PUT_verify_負数金額_400` | 同上 | verifiedAmount=-1 / Then: 400 |
| BE-C-07 | `DELETE_manualLock_admin_204` | `DELETE /.../manual-lock` | shopNo=0 / Then: 204 |
| BE-C-08 | `DELETE_manualLock_非admin_403` | 同上 | shopNo=1 ユーザー / Then: 403 |
| BE-C-09 | `PATCH_mfExport_200` | `PATCH /.../mf-export` | `{enabled:false}` / Then: 200 |
| BE-C-10 | `POST_recalculate_admin_200_集計件数返却` | `POST /recalculate` | admin / Then: 200 + summary |
| BE-C-11 | `POST_recalculate_非admin_403` | 同上 | shopNo=1 / Then: 403 |
| BE-C-12 | `POST_recalculate_RUNNING中_409Conflict` | 同上 | `JobExplorer#findRunningJobExecutions` が非空をモック / Then: 409 |
| BE-C-13 | `POST_reverify_RUNNING中_409Conflict` | `POST /reverify` | 同上 / Then: 409 |
| BE-C-14 | `POST_recalculate_from_to必須_400` | `POST /recalculate` | body なし / Then: 400 |

### 1.4 多重実行ガード単体 `AccountsPayableBatchLauncherTest`

| # | ケース名 | Given / When / Then |
|---|---|---|
| BE-G-01 | `launch_RUNNING無し_JobLauncherに委譲` | JobExplorer → 空 Set / Then: `jobLauncher.run()` 1回呼出 |
| BE-G-02 | `launch_RUNNINGあり_ConflictException` | JobExplorer → 1件 / Then: `ConflictException`（HTTP 409にマップ） |
| BE-G-03 | `launch_jobNameごとに独立判定` | summaryJob RUNNING中にverificationJob起動 / Then: verification は通る |

---

## 2. フロントエンド E2E（Playwright）

ファイル: `frontend/e2e/accounts-payable.spec.ts`
共通前提:
```ts
await mockAllApis(page)
// 以下、個別 route を LIFO で上書き
await loginAndGoto(page, '/finance/accounts-payable')
```
必要モック追加:
- `GET /api/v1/finance/accounts-payable*` → ページングJSON
- `GET /api/v1/finance/accounts-payable/summary*` → サマリJSON
- `PUT|DELETE|PATCH|POST /api/v1/finance/accounts-payable/**` → 成功系/409
- `GET /api/v1/masters/suppliers` → `MOCK_SUPPLIERS`

### 2.1 表示・検索

| # | ケース | 前提モック | 操作 | 期待 | セレクタ |
|---|---|---|---|---|---|
| FE-01 | 初期表示で当月20日が自動セットされ検索される | 当月20日のデータ3件 | ページロード | 月 input に `yyyy-MM`、テーブル3行 | `input[type="month"]`, `table tbody tr` |
| FE-02 | 取引月を変更→再検索 | 別月のデータ1件 | `fill('2026-02')` → 検索 | 1行、URLクエリ反映 | `input[type="month"]`, `getByRole('button',{name:'検索'})` |
| FE-03 | 検証ステータス=未検証でフィルタ | verificationResult=null 2件 | Select で「未検証」 | 2行、全て未検証バッジ | `getByRole('combobox',{name:/検証/})` |
| FE-04 | admin は店舗セレクト表示 | MOCK_USER (shopNo=0) | - | ショップ Select 表示 | `getByLabel('店舗')` |
| FE-05 | 非admin は店舗セレクト非表示 | `/auth/me` を shopNo=1 で上書き | - | 店舗 Select 無し | 同上 not visible |

### 2.2 バッジ・サマリ

| # | ケース | 前提モック | 操作 | 期待 |
|---|---|---|---|---|
| FE-06 | 一致/不一致/未検証のBadge色分け | 各1件 | - | Badge 3種、不一致差額が赤テキスト |
| FE-07 | 手動確定行に「手動」サブバッジ | verifiedManually=true | - | 「手動」Badge が「一致」の隣 |
| FE-08 | サマリアラート件数と差額合計表示 | summary API: 未検証5/不一致2/差額-3240 | - | `getByRole('alert')` にテキスト含む |
| FE-09 | 未検証=0,不一致=0 でアラート非表示 | summary ゼロ | - | alert 要素なし |

### 2.3 検証ダイアログ（詳細/入力）

| # | ケース | 前提 | 操作 | 期待 |
|---|---|---|---|---|
| FE-10 | 未検証行の「検証」ボタン→ダイアログ開く | - | row の 検証ボタン click | Dialog title「買掛金検証」、初期値に taxIncludedAmountChange |
| FE-11 | 検証金額＋備考入力→更新→一致バッジに変化 | PUT verify mock → verificationResult=1, verifiedManually=true | 金額入力、備考「請求書A-001」、更新 click | toast 成功、dialog 閉じる、行の Badge「一致」+「手動」 |
| FE-12 | 検証済行を再度開くと前回備考が表示される | 行の verificationNote="前回メモ" | 詳細 click | textarea に "前回メモ" |
| FE-13 | 備考501字でバリデーションエラー | - | 501字入力、更新 | 「500字以内」エラー、PUT 呼ばれない |
| FE-14 | 更新失敗（500）でtoastエラー+ダイアログ残る | PUT→500 | 更新 click | error toast、dialog 開いたまま |
| FE-15 | 二重クリック防止 | PUT を遅延mock | 更新 click ×2 | PUT 1回のみ、ボタン disabled |

### 2.4 手入力保護 / 手動確定解除

| # | ケース | 前提 | 操作 | 期待 |
|---|---|---|---|---|
| FE-16 | admin のみ「手動確定解除」ボタン表示 | admin + verifiedManually=true 行 | dialog 開く | 「手動確定解除」ボタン見える |
| FE-17 | 非admin は解除ボタン非表示 | shopNo=1 + 上と同じ行 | dialog 開く | 解除ボタン無し |
| FE-18 | 解除→再検証でSMILE値に上書きされる | DELETE manual-lock 成功、その後 POST reverify 成功→一覧refetchで SMILE 値反映 | 解除 click → 再検証 click | Badge 変化、verifiedManually バッジ消える |
| FE-19 | verifiedManually=true 行は再検証でも Badge変わらず | reverify 実行後の mock で手動行は不変 | 再検証 | 手動行の Badge 保持 |

### 2.5 再集計 / 再検証（多重実行ガード）

| # | ケース | 前提 | 操作 | 期待 |
|---|---|---|---|---|
| FE-20 | admin のみ再集計/再検証ボタン表示 | admin | - | ヘッダーに2ボタン |
| FE-21 | 非admin 非表示 | shopNo=1 | - | ボタン無し |
| FE-22 | 再集計成功→summary toast | POST recalculate→200 `{updated:10}` | click → 確認 OK | toast「再集計完了 10件」 |
| FE-23 | 再集計連打で409 Conflict→エラーtoast | POST→409 | click | error toast「別の処理が実行中」、ボタン再活性 |
| FE-24 | 処理中ボタン disabled（ガード） | POST を遅延 | click → 直後に再click | POST 1回のみ |
| FE-25 | 再検証も同様に409で警告表示 | POST reverify→409 | click | error toast |

### 2.6 MF出力トグル

| # | ケース | 前提 | 操作 | 期待 |
|---|---|---|---|---|
| FE-26 | Switch ON→OFFでPATCH発行 | PATCH mf-export→200 | Switch click | PATCH body `{enabled:false}`、行の Switch OFF |
| FE-27 | PATCH失敗でSwitch元に戻る | PATCH→500 | Switch click | toast エラー、Switch は元のまま |

### 2.7 エッジ

| # | ケース | 前提 | 期待 |
|---|---|---|---|
| FE-28 | 取引月にデータ無し | 一覧空、summary ゼロ | 空テーブルメッセージ表示、エラー無し |
| FE-29 | supplierNameなし（不明） | supplierName=null | 「不明」表示 |

### 2.8 実バックエンド疎通（モック無効、1本のみ）

| # | ケース | 前提 | 操作 | 期待 |
|---|---|---|---|---|
| FE-REAL-01 | `実バックエンド_買掛金一覧GET疎通` | バックエンド起動 (`web,dev` port 8090)、t_accounts_payable_summary に最低1行、`mockAllApis` 呼ばない。`loginWithRealBackend` ヘルパで実ログイン | `/finance/accounts-payable` を開き、当月20日で検索 | HTTP 200、テーブル行が 1件以上、エラートーストなし。スクリーンショット保存。失敗時は "手順: ./gradlew bootRun --args='--spring.profiles.active=web,dev'" を出力 |

---

## 3. テストデータ / フィクスチャ

- バックエンド: `@Sql` で `t_accounts_payable_summary` に8レコード（一致3/不一致2/未検証2/手動確定1）投入
- `m_payment_supplier` / `m_supplier` に最低2件（code "0001"/"0002"）
- フロント: `e2e/helpers/mock-api.ts` に `MOCK_ACCOUNTS_PAYABLE` / `MOCK_AP_SUMMARY` を追加し `mockAllApis` から配信

## 4. カバレッジ目標 / 完了条件

- Service: 行 90%+ / `verify()` は分岐 100%（税率、閾値、備考長、既存note上書き）
- Controller: 各エンドポイント 200/400/403/409 を網羅
- E2E: 上記 29 + 実疎通 1 = 30 ケース全 PASS
- `npx tsc --noEmit` / `./gradlew compileJava test` グリーン
- 手入力保護（BE-V-01..04, FE-16..19）と多重実行ガード（BE-C-12/13, BE-G-01..03, FE-23..25）の全ケース PASS

## 5. 実行順序

1. BE Service/Verifier 単体（1.1, 1.2）
2. BE Controller + Launcher（1.3, 1.4）
3. FE E2E モック系（FE-01..29）
4. 実バックエンド疎通（FE-REAL-01）
5. `/code-review` 実行 → 差分の再検証
