# テスト計画: 作成済み見積から比較見積を作成する機能

- **対象機能**: 見積詳細画面 → 比較見積画面への引継ぎ
- **設計書**: `claudedocs/design-estimate-to-compare.md`
- **作成日**: 2026-04-10
- **作成者**: kazuki + Claude (QA)
- **ブランチ**: `feat/quote-import-and-nfkc-search`

---

## 1. テスト方針

- **E2E (Playwright) 中心** — 実際のユーザー導線（見積詳細 → 比較画面）がフロント完結のため、統合的な挙動確認が最も費用対効果が高い
- **純粋関数 `buildCompareHandoffFromEstimate` の単体検証** — フロントに vitest 等の単体テストフレームワークは未導入のため、Playwright の `page.evaluate()` を用いて import 済み関数をブラウザコンテキストで呼び出し、入出力を assert する方式で代替する。または E2E シナリオからの間接検証で境界ケースを網羅する
- **バックエンド変更なし** → API テスト／JUnit テストは本計画の対象外
- **既存リグレッション** — 比較見積画面の既存フロー（`/estimates/compare` 単体起動）は `estimate-comparison.spec.ts` で既にカバー済み。本計画ではリグレッション観点で壊れていないことを最小限確認するが、フルリグレッションは既存 spec に委ねる

### テスト実装ファイル
- **新規 spec**: `frontend/e2e/estimate-to-compare.spec.ts`
- **既存 spec の最小修正**: `frontend/e2e/estimate-comparison.spec.ts` — sessionStorage の `estimate-compare` キーを beforeEach でクリアする afterEach フック追加（handoff テスト汚染防止）

---

## 2. テストスコープ

### In Scope
- 見積詳細画面の「比較見積へ」ボタン表示／非表示（ステータス条件）
- ボタンクリック → `sessionStorage['estimate-compare-handoff']` への書き込み
- 比較見積画面マウント時の handoff 読込 → state 反映
- 引継ぎ元バッジの表示／クリック遷移／× クリアボタン
- 確認ダイアログ（EC-7 5 シナリオ）
  1. 置き換え実行
  2. キャンセル
  3. 保留バー操作（今すぐ切替／破棄）
  4. リロード後の復元
  5. items 0 件時の即時適用（確認ダイアログスキップ）
- 明細 11 件以上の切り詰め + 警告トースト
- 未登録商品（`goodsNo == null`）の引継ぎ
- 登録済み商品の `/compare-goods` API enrich との相互作用
- `buildCompareHandoffFromEstimate` 純粋関数の入出力
- `sessionStorage` 失敗時のエラートースト（モックで `setItem` を例外化）

### Out of Scope
- 比較見積画面の既存機能全般（基準品切替、シミュレーション計算、印刷、得意先向け表示）
- 見積詳細画面の既存機能全般（修正、削除、PDF、印刷）
- バックエンド API (`/estimates/{no}`, `/compare-goods`) の応答内容そのもの
- 比較画面の印刷レイアウト（バッジが `print:hidden` であることは DOM クラスで軽く確認する程度）
- 複数ユーザー並行／CSRF／認可の検証（既存認可で担保）

---

## 3. 前提条件 / テストデータ

### モック拡張（`helpers/mock-api.ts`）

既存 `MOCK_ESTIMATES[0]` (estimateNo=570, status=20) と `MOCK_ESTIMATES[1]` (estimateNo=2341, status=00) に加え、テスト用見積詳細オブジェクトを `MOCK_ESTIMATE_DETAILS_*` として追加する。現在 `GET /estimates/{no}` は `MOCK_ESTIMATES[0]` を返しており `details` フィールドが存在しないため、`details` 配列を含むモックの上書きが必要。

**テスト側で使う 4 パターン**（spec 内で `page.route()` を上書きする想定）:

| パターン | estimateNo | status | details 件数 | 備考 |
|---|---|---|---|---|
| `EST_STATUS_00_5ITEMS` | 9001 | `00` | 5件 (登録3+未登録2) | 標準ケース |
| `EST_STATUS_20_1ITEM` | 9002 | `20` | 1件 | 修正ステータスの最小ケース |
| `EST_STATUS_10_3ITEMS` | 9003 | `10` | 3件 | 提出済 — ボタン非表示確認 |
| `EST_12ITEMS` | 9004 | `00` | 12件 | 11件超過切り詰め確認 |

### 見積明細 (`EstimateDetailResponse`) のテストデータ要素

- `goodsNo`: 登録商品は `1, 2` (MOCK_COMPARE_GOODS と一致)、未登録商品は `null`
- `goodsPrice`: 1000〜3000 の整数（`simulatedPrice` 初期値検証用）
- `purchasePrice`: 500〜1500 の整数
- `containNum`: 12 or 24
- `displayOrder`: **シャッフル順で格納し、昇順ソート検証**（例: 3, 1, 5, 2, 4）
- `goodsPrice == null` を少なくとも 1 行含める（EC-10 検証用）

### 既存モックとの整合性
- `MOCK_COMPARE_GOODS` に `goodsNo=1,2` が存在するため enrich 検証可能
- 未登録商品行は `/compare-goods` の goodsNoList に含まれない → enrich されないことを確認

---

## 4. テストケース一覧

### 4-1. ボタン表示/非表示（FR-2）

| ID | 前提 | 操作 | 期待結果 | 優先度 |
|---|---|---|---|---|
| TC-01 | ステータス `00` の見積詳細を開く | 画面表示 | 「比較見積へ」ボタンが表示される（アイコン `BarChart3`、variant=outline） | High |
| TC-02 | ステータス `20` の見積詳細を開く | 画面表示 | 「比較見積へ」ボタンが表示される | High |
| TC-03 | ステータス `10`（提出済）の見積詳細を開く | 画面表示 | 「比較見積へ」ボタンが **表示されない** | High |
| TC-04 | ステータス `00` の見積詳細 | ボタン配置順を確認 | `[修正][削除][印刷][PDF][比較見積へ][戻る]` の順序 | Medium |

### 4-2. 純粋関数 `buildCompareHandoffFromEstimate`（FR-5, FR-6, FR-7, FR-9, EC-10）

**方針決定**: フロントに vitest 等の単体テストフレームワークは未導入であり、ブラウザ側で `@/` alias 解決もできないため、**純粋関数の単体テストは本計画のスコープから外す**。すべての入出力仕様は E2E シナリオ（TC-10〜TC-26, TC-36, TC-37）で間接的に検証する。対応表は下記の通り。

| 純粋関数の仕様 | 間接検証するテスト |
|---|---|
| details 空 → items 空 | TC-34 (EC-1) |
| displayOrder 昇順ソート | TC-26 |
| 12件 → 10件切り詰め + truncated | TC-24, TC-25, TC-26 |
| goodsPrice → simulatedPrice | TC-14 |
| goodsPrice=null → simulatedPrice=null | TC-37 |
| goodsNo=null のそのまま引継ぎ | TC-22 |

> 将来 vitest 導入時に `lib/estimate-handoff.test.ts` として移植する。

### 4-3. ボタンクリック → handoff 書き込み + 遷移（FR-1, FR-3, FR-4, FR-5, FR-9, EC-6）

| ID | 前提 | 操作 | 期待結果 | 優先度 |
|---|---|---|---|---|
| TC-10 | status=00, 5件明細、比較画面の既存 session なし | 「比較見積へ」クリック | `/estimates/compare` に遷移、URL 確認 | High |
| TC-11 | 同上、**既存 `sessionStorage['estimate-compare']` の items 配列が 0 件 / または session キー自体が無い** | 遷移直後 | `sessionStorage['estimate-compare-handoff']` が **削除済み**（即時適用のため）、確認ダイアログは表示されない | High |
| TC-11b | 既存 items 2件あり | 遷移直後 | `sessionStorage['estimate-compare-handoff']` は **残存**（確認ダイアログ待機中のため） | High |
| TC-12 | 同上 | 遷移直後 | 比較画面に 5 件表示される（`比較商品 (5/10)` テキスト） | High |
| TC-13 | 同上 | 遷移直後 | 先頭行が基準品（`基準品` バッジ 1 つだけ表示） | High |
| TC-14 | 同上 | `simulatedPrice` 初期値を DOM から確認 | 見積の `goodsPrice` 値が販売単価入力欄に入っている | High |
| TC-15 | 同上 | 店舗・得意先・配送先を確認 | 引継ぎ元見積の値がセレクトに反映 | Medium |
| TC-16 | 同上 | 引継ぎ元バッジを確認 | `見積 #9001 から引継ぎ` が表示される | High |
| TC-17 | `sessionStorage.setItem` を例外化（`page.addInitScript` で override） | クリック | `toast.error` が表示され、`/estimates/compare` に遷移**しない** | Medium |

### 4-4. 引継ぎ元バッジ（FR-10）

| ID | 前提 | 操作 | 期待結果 | 優先度 |
|---|---|---|---|---|
| TC-18 | TC-10 の状態 | バッジクリック | `/estimates/9001` に遷移する | High |
| TC-19 | TC-10 の状態 | バッジ右の `×` ボタンをクリック | バッジが消える、items は保持（`比較商品 (5/10)` のまま） | High |
| TC-20 | TC-10 の状態 | バッジ要素のクラスを確認 | `print:hidden` 相当のクラスが含まれる | Medium |

### 4-5. 登録済み/未登録商品の扱い（FR-7, FR-8, EC-3, EC-4）

| ID | 前提 | 操作 | 期待結果 | 優先度 |
|---|---|---|---|---|
| TC-21 | 登録3 + 未登録2 の見積から遷移 | `/compare-goods` リクエストを watch | リクエストの `goodsNoList` パラメータに登録商品の 2 件のみ含まれる | High |
| TC-22 | 同上 | 表示確認 | 登録商品は仕入先名が表示、未登録商品は仕入先欄が `-` 表示 | High |
| TC-23 | 全明細が未登録商品のみの見積 | 遷移 | `/compare-goods` API が **呼ばれない**（`registeredGoodsNos.length === 0` で `enabled: false`） | Medium |

### 4-6. 11件以上の切り詰め（FR-6, EC-2）

| ID | 前提 | 操作 | 期待結果 | 優先度 |
|---|---|---|---|---|
| TC-24 | 12件の明細を持つ見積 (`EST_12ITEMS`) | 「比較見積へ」クリック | `toast.warning` が表示され、文言に `先頭10件` と `残り2件` が **両方含まれる**（数字 `2` を正規表現で明示的に検証: `/残り2件/`） | High |
| TC-25 | 同上 | 比較画面遷移後 | `比較商品 (10/10)` が表示される（切り詰め後 10 件） | High |
| TC-26 | 同上 | 明細の順序を確認 | `displayOrder` 昇順の先頭 10 件で構成されている | High |

### 4-7. 既存 items あり時の確認ダイアログ（EC-7, FR-11）

すべて **High 優先度**。このブロックが本機能の肝。

#### 前提セットアップ（各テスト共通）
1. `/estimates/compare` に直接訪問して商品検索で 2 件追加 → `sessionStorage['estimate-compare']` に items 2件が保存された状態を作る
2. `/estimates/9001` に遷移
3. 「比較見積へ」クリック

| ID | シナリオ | 操作 | 期待結果 |
|---|---|---|---|
| TC-27 | 既存 items 2件 + handoff → 確認ダイアログ表示 | 遷移直後 | `AlertDialog` が開く、文言に **数字 `2` と `5` が両方含まれる**（件数表示が正しい） |
| TC-28 | 確認ダイアログ「置き換える」 | クリック | ダイアログ閉じる、items が見積の 5 件に差し替わる、`sessionStorage['estimate-compare-handoff']` が削除、引継ぎ元バッジ表示 |
| TC-29 | 確認ダイアログ「キャンセル」 | クリック | ダイアログ閉じる、items は既存の 2 件のまま、引継ぎ保留バー表示（`見積 #9001 からの引継ぎが保留されています`）、**`sessionStorage['estimate-compare-handoff']` は残存** |
| TC-30 | 保留バー「今すぐ切替」 | クリック | 最終的に items が 5 件に差し替わる（ダイアログ再表示 or 即適用は経路問わず）、**最終状態で `sessionStorage['estimate-compare-handoff']` が削除済み**、**引継ぎ元バッジ `見積 #9001 から引継ぎ` が表示される**（`sourceEstimateNo` がセットされたことの確認） |
| TC-31 | 保留バー「破棄」 | クリック | 保留バー消える、`sessionStorage['estimate-compare-handoff']` が削除、items は既存の 2 件のまま |
| TC-32 | キャンセル後にページリロード | `page.reload()` | **再び確認ダイアログが表示される**（`HandoffState` は React state のため reload で消失。mount useEffect が走り、既存 items > 0 + handoff あり → `pending` 状態に再セット → ダイアログ再表示）。items は既存の 2 件のまま。設計書 §6-2-4 の「同じフローに入る」= pending → ダイアログ から再開する仕様 |
| TC-33 | items 0件 + handoff あり | 直接遷移 | 確認ダイアログは出ず、即時適用される（TC-10 と同じ挙動） |
| TC-33b | 確認ダイアログを ESC or オーバーレイクリックで閉じる | 操作 | キャンセル扱い or 無視のどちらか（実装側ポリシーを確認）。少なくとも state 不整合で画面が壊れない | Medium |

### 4-8. エッジケース

| ID | シナリオ | 期待結果 | 優先度 |
|---|---|---|---|
| TC-34 | EC-1: 明細 0 件の見積からボタンクリック | 遷移成功、比較画面は `比較商品 (0/10)` 表示、引継ぎ元バッジは表示される | Low |
| TC-35 | EC-8: `sessionStorage['estimate-compare-handoff']` に不正 JSON を直接セット → `/estimates/compare` 訪問 | JSON パース失敗で handoff は破棄され、通常の self-restore にフォールバックする（エラーで画面が落ちない） | Medium |
| TC-36 | EC-9: `destinationNo=null` の見積から遷移 | 配送先セレクトが空欄のまま | Low |
| TC-36b | FR-3 ポジティブケース: `destinationNo=1` の見積から遷移 | 配送先セレクトが指定値で表示される | Medium |
| TC-37 | EC-10: `goodsPrice=null` の明細を含む見積 | 販売単価欄が空（プレースホルダ表示）、crash しない | Low |
| TC-37b | EC-11: 登録済み商品が DB から削除されたケース（`/compare-goods` モックで該当 goodsNo を返さない） | 該当行は enrich されず handoff 時の元情報だけで grid に表示される、画面は crash しない | Medium |
| TC-37c | 連続シナリオ: 見積詳細 → 比較画面 → ブラウザ戻るで詳細に戻る → 再度「比較見積へ」クリック | handoff が再生成されて 2 回目も正常に遷移・反映される | Medium |

### 4-9. リグレッション最小確認

| ID | シナリオ | 期待結果 | 優先度 |
|---|---|---|---|
| TC-38 | handoff なしで `/estimates/compare` を開く | 既存の self-restore が従来どおり動く（`estimate-compare` session 復元） | High |
| TC-39 | 比較 → 見積作成フロー（`estimate-prefill` sessionStorage） | handoff キーと干渉せず動作 | Medium |

---

## 5. 想定セレクタ（テストで使う想定）

| 用途 | セレクタ | data-testid 必要性 |
|---|---|---|
| 「比較見積へ」ボタン | `getByRole('button', { name: '比較見積へ', exact: true })` | 不要（テキストで特定可） |
| 引継ぎ元バッジ本体 | `getByTestId('handoff-source-badge')` | **必要** |
| 引継ぎ元バッジ × ボタン | `getByTestId('handoff-source-badge-clear')` | **必要** |
| 確認ダイアログ | `getByRole('alertdialog')` | 不要 |
| 確認ダイアログ「置き換える」 | `alertdialog.getByRole('button', { name: '置き換える' })` | 不要 |
| 確認ダイアログ「キャンセル」 | `alertdialog.getByRole('button', { name: 'キャンセル' })` | 不要 |
| 引継ぎ保留バー | `getByTestId('handoff-deferred-bar')` | **必要** |
| 保留バー「今すぐ切替」 | `getByTestId('handoff-deferred-bar').getByRole('button', { name: '今すぐ切替' })` | 不要（親 testid 経由） |
| 保留バー「破棄」 | `getByTestId('handoff-deferred-bar').getByRole('button', { name: '破棄' })` | 不要（親 testid 経由） |
| 比較商品件数 | `getByText(/比較商品 \(\d+\/10\)/)` | 不要 |

---

## 6. テスト実装上の注意点

### 6-1. sessionStorage の扱い
- **テスト間の汚染防止**: 各テストの `beforeEach` で `page.addInitScript(() => sessionStorage.clear())` を呼ぶ
- **TC-11 の handoff 削除確認**: 遷移後 `page.evaluate(() => sessionStorage.getItem('estimate-compare-handoff'))` で `null` を確認
- **TC-17 の sessionStorage 失敗**: `page.addInitScript` で `Storage.prototype.setItem` を例外化する

### 6-2. API モック上書き
既存の `mockAllApis(page)` はデフォルト応答を返すため、本 spec では `await mockAllApis(page)` の後に追加で `page.route('/api/v1/estimates/9001', ...)` 等を上書きする。`MOCK_ESTIMATE_WITH_DETAILS_*` 定数は `mock-api.ts` にエクスポートを追加して spec 側から import する（既存 `MOCK_ESTIMATES` と同じ流儀）。

### 6-3. 確認ダイアログのタイミング
`useEffect` マウント時にダイアログを出す実装のため、`page.goto` 直後の `await expect(page.getByRole('alertdialog')).toBeVisible()` で安定して拾えるはずだが、念のため `waitFor` タイムアウトを 5s 程度設定する。

### 6-4. トースト（sonner）アサーション
`toast.warning` / `toast.error` は `<li>` として DOM に挿入される。既存 spec 同様 `getByText(/先頭10件/)` で拾う。

### 6-5. Next.js 16 の hydration 待機
遷移後すぐにバッジや items をアサートする前に `await expect(page.getByText('比較見積')).toBeVisible()` で hydration 完了を待つ。

---

## 7. 既知のリスク・スキップ理由

| # | リスク | 対応 |
|---|---|---|
| SR-1 | 純粋関数の単体テストフレームワーク不在 | TC-05〜09 は E2E 経由で間接検証に寄せる。将来 vitest 導入時に移植 |
| SR-2 | 確認ダイアログのテキスト（件数）は実装側のテンプレート文言に依存 | アサートは `正規表現（2件と5件の数字が含まれる）` で緩く確認する |
| SR-3 | トースト文言の完全一致アサートは壊れやすい | 部分一致（`/先頭10件/`, `/残り2件/`）でアサート |
| SR-4 | 既存 `estimate-comparison.spec.ts` は `sessionStorage` クリアを実施していないため、本 spec 実行後に状態が残る可能性 | 本 spec の `afterEach` で `sessionStorage.clear()` を実行し、次テストに影響を残さない |
| SR-5 | 「今すぐ切替」の実装が「ダイアログ再表示」か「即適用」かは実装側の選択 | TC-30 のアサートは「最終的に items が差し替わる」ことまでで留め、経由経路は問わない |

---

## 8. カバレッジマトリクス（設計書 Phase 9 動作確認項目との対応）

| 設計書の動作確認項目 | 対応テストケース |
|---|---|
| ステータス `00`（作成）の見積でボタンが表示される | TC-01 |
| ステータス `20`（修正）の見積でボタンが表示される | TC-02 |
| ステータス `10`（提出済）の見積でボタンが非表示 | TC-03 |
| ボタン配置順 `[修正][削除][印刷][PDF][比較見積へ][戻る]` | TC-04 |
| ボタンクリック → 比較画面遷移（既存 items 0件） | TC-10, TC-12 |
| 各明細の `simulatedPrice` に `goodsPrice` がセット | TC-14 |
| 先頭明細が基準品 | TC-13 |
| 登録済み商品は `/compare-goods` で enrich | TC-21, TC-22 |
| 未登録商品は仕入情報が `-` | TC-22 |
| 11 件以上で警告トースト + 10 件切り詰め | TC-24, TC-25, TC-26 |
| 販売単価変更 → 粗利差分表示 | **既存 spec リグレッション委譲** |
| 「見積 #xxx から引継ぎ」バッジ表示 | TC-16 |
| バッジクリック → 元の見積詳細に遷移 | TC-18 |
| バッジ × ボタンで `sourceEstimateNo` のみクリア | TC-19 |
| **EC-7: 確認ダイアログ表示** | TC-27 |
| **EC-7: 置き換える** | TC-28 |
| **EC-7: キャンセル → 保留バー** | TC-29 |
| **EC-7: 保留バー「今すぐ切替」** | TC-30 |
| **EC-7: 保留バー「破棄」** | TC-31 |
| **EC-7: リロード後復元** | TC-32 |
| items 0件で handoff → 即適用 | TC-33 |
| sessionStorage 失敗時エラー | TC-17 |
| EC-11: 登録商品が DB 削除 | TC-37b |

---

## 9. 実装側に依頼する `data-testid` 一覧

並列実装中の UI 開発者が反映すべき `data-testid` を以下にまとめる。これらは E2E テストの安定化と shadcn/ui コンポーネントのラッパー判別のため必須。

| # | 要素 | `data-testid` | 設置ファイル |
|---|---|---|---|
| 1 | 見積詳細の「比較見積へ」ボタン | `estimate-to-compare-button` | `components/pages/estimate/detail.tsx` |
| 2 | 引継ぎ元バッジ（クリック領域全体） | `handoff-source-badge` | `components/pages/estimate/comparison.tsx` |
| 3 | 引継ぎ元バッジの × ボタン | `handoff-source-badge-clear` | `components/pages/estimate/comparison.tsx` |
| 4 | 引継ぎ保留バー | `handoff-deferred-bar` | `components/pages/estimate/comparison.tsx` |
| 5 | 確認ダイアログ（AlertDialog root、shadcn/ui のデフォルト `role="alertdialog"` で特定可だが他のダイアログとの衝突回避のため推奨） | `handoff-confirm-dialog` | `components/pages/estimate/comparison.tsx` |

> #1 はテキスト `比較見積へ` でも特定可能だが、将来のラベル変更に対する堅牢性のため付与推奨。
> #5 は 他の AlertDialog（削除確認など）と同時表示されるシナリオが無いため必須ではないが、テストの明確性のため推奨。

---

## 10. 実行計画

### 実行順
1. 実装側が `data-testid` を反映（依頼事項、§9 参照）
2. `helpers/mock-api.ts` に `MOCK_ESTIMATE_WITH_DETAILS_*` 4 パターン追加（エクスポート付き）
3. `frontend/e2e/estimate-comparison.spec.ts` の beforeEach に `page.addInitScript(() => sessionStorage.clear())` を追加して本 spec からの汚染を防止（既存 spec 側の副作用修正）
4. `frontend/e2e/estimate-to-compare.spec.ts` を新規作成 (TC-01〜TC-39)
5. `npx playwright test estimate-to-compare.spec.ts` でローカル実行
6. 既存の `estimate-comparison.spec.ts` と `estimate-list.spec.ts` を再実行してリグレッション確認

### 想定実行時間
- 新規 spec: 約 35 ケース × 2s = 70s + bootstrap = **2〜3 分**
- 既存 spec リグレッション: 既存と同等（1〜2 分）

### 成功判定
- 全 TC グリーン
- 既存 spec も全グリーン（リグレッションなし）

---

## 11. 自己レビュー結果サマリ

初版テスト計画に対して別観点で見直した結果、以下の指摘を反映済み。

| # | 指摘 | 重要度 | 対応 |
|---|---|---|---|
| Q-1 | EC-11（登録済み商品が DB 削除されたケース）が未カバー | High | TC-37b を追加 |
| Q-2 | TC-11 が「即削除」前提で書かれており、items>0 ケース（保留中は削除しない）が対比ケースとして無い | High | TC-11b を追加 |
| Q-3 | TC-30（保留バー「今すぐ切替」）で sessionStorage の handoff キー削除タイミング検証が抜けていた | High | TC-30 の期待結果に「最終状態で handoff 削除済み」を追記 |
| Q-4 | TC-27 の確認ダイアログ件数アサートが曖昧 → 件数ロジック（既存 N 件 / 引継ぎ M 件）はバグりやすい | High | 「数字 2 と 5 が両方含まれる」を明記 |
| Q-5 | 純粋関数 TC-05〜09 は vitest 不在で実効性低 → 残したままだと実装側が混乱 | Medium | テーブル削除し、間接検証マッピング表に置き換え |
| Q-6 | 既存 `estimate-comparison.spec.ts` の beforeEach に `sessionStorage.clear()` が無く、本 spec と相互汚染のリスク | Medium | 実行順 Step 3 として既存 spec 修正を明記 |
| Q-7 | FR-3 で `destinationNo` 引継ぎのポジティブケース（null 以外）が明示的な TC になっていなかった | Medium | TC-36b を追加 |
| Q-8 | 往復シナリオ（遷移 → 戻る → 再度クリック）が漏れていた | Medium | TC-37c を追加 |
| Q-9 | 確認ダイアログの ESC / オーバーレイクリック時の挙動が未定義 | Medium | TC-33b を追加（実装側ポリシー確認を促す） |
| Q-10 | TC-20（print:hidden）が Low 優先度だったが、設計書本文に明記された動作確認項目 | Low | Medium に昇格 |
| Q-11 | 純粋関数テストが `@/` alias 未解決で browser では動かないという技術的根拠の明確化 | Low | 方針決定として明文化 |

### カバレッジ
- 設計書 FR-1〜FR-11: **全カバー**
- 設計書 EC-1〜EC-11: **全カバー**（EC-11 追加で漏れ解消）
- 設計書 Phase 9 動作確認項目: §8 マトリクスで全行マッピング

### 総ケース数
- 初版: 39 ケース → レビュー後: **TC-01〜TC-39 + 追加 6 件（TC-11b, TC-20 昇格, TC-33b, TC-36b, TC-37b, TC-37c）= 計 42 ケース**
- うち High 優先度: 約 22 ケース（本機能のコアパスを集中的にガード）
- うち Medium 優先度: 約 14 ケース
- うち Low 優先度: 約 6 ケース

### 残リスク
- **純粋関数の単体テスト不在** → vitest 導入までは E2E の間接検証で代替。将来の単体テスト移植を推奨
- **sonner トーストの文言完全一致は避け、部分一致でアサート** → 実装側で文言変更があっても壊れにくい
- **確認ダイアログの件数表示フォーマット** → 実装側のテンプレート次第でアサートが壊れる可能性。「数字が含まれる」程度の緩いアサートで許容範囲とする
