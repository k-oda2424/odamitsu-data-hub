# テスト計画: 仕入先・メーカープルダウンの検索対応（Select2化）

## 対象設計書

`claudedocs/design-searchable-select.md`

## テスト環境

- Playwright (Chromium headless)
- API モック: `e2e/helpers/mock-api.ts` の `mockAllApis(page)`
- 認証ヘルパー: `e2e/helpers/auth.ts` の `loginAndGoto(page, '/path')`
- パターン: `await mockAllApis(page)` -> `await loginAndGoto(page, '/path')`

## モックデータ（既存）

- `MOCK_MAKERS`: メーカーA, メーカーB, メーカーC（3件）
- `MOCK_SUPPLIERS`: 仕入先A, 仕入先B（2件）

## モックデータ追加提案

テスト用に検索フィルタリングを検証するため、mock-api.ts に以下を追加する:

```typescript
export const MOCK_MAKERS_EXTENDED = [
  { makerNo: 1, makerName: 'メーカーA' },
  { makerNo: 2, makerName: 'メーカーB' },
  { makerNo: 3, makerName: 'メーカーC' },
  { makerNo: 4, makerName: '山田食品工業' },
  { makerNo: 5, makerName: '鈴木製菓' },
]

export const MOCK_SUPPLIERS_EXTENDED = [
  { supplierNo: 1, supplierName: '仕入先A' },
  { supplierNo: 2, supplierName: '仕入先B' },
  { supplierNo: 3, supplierName: '山田商店' },
  { supplierNo: 4, supplierName: '鈴木物産' },
]
```

## セレクタ規約

SearchableSelect は shadcn/ui の Popover + Command（cmdk）パターンで構成される。以下のセレクタを使用する:

| 要素 | セレクタ |
|------|---------|
| トリガーボタン | `button[role="combobox"]` |
| 検索入力 | `input[cmdk-input]` または `[role="combobox"] input`（Popover 内） |
| 選択肢リスト | `[cmdk-list]` |
| 個別選択肢 | `[cmdk-item]` |
| 空メッセージ | `[cmdk-empty]` |
| クリアボタン | トリガー内の `×` ボタン（`button` with accessible name） |
| チェックマーク | 選択済み項目内の `Check` アイコン |

特定のフィールドを区別するには、ラベルからの相対位置で特定する:

```typescript
// メーカー SearchableSelect のトリガー
const makerCombobox = page.getByLabel('メーカー').locator('..').locator('button[role="combobox"]')
// または data-testid を付与する場合
const makerCombobox = page.locator('[data-testid="maker-select"]')
```

## テストケース一覧

### Category 1: SearchableSelect コンポーネント共通動作

これらのテストは任意の1画面（`/goods` の メーカー選択）で検証する。コンポーネント単体の機能をカバーする。

| TestID | Category | Test Case | Steps | Expected Result | Priority |
|--------|----------|-----------|-------|-----------------|----------|
| SS-001 | コンポーネント/表示 | Popover が開閉する | 1. `mockAllApis(page)` -> `loginAndGoto(page, '/goods')` <br> 2. メーカーの `button[role="combobox"]` をクリック <br> 3. Popover 内に `input[cmdk-input]` が表示されることを確認 <br> 4. 選択肢一覧（`[cmdk-item]`）が表示されることを確認 <br> 5. Popover 外をクリック <br> 6. Popover が閉じることを確認 | Popover が正常に開閉する。開いた状態で検索入力と選択肢リストが表示される | P0 |
| SS-002 | コンポーネント/検索 | テキスト入力で選択肢がフィルタされる | 1. メーカー combobox をクリックして Popover を開く <br> 2. 検索入力に `メーカーA` と入力 <br> 3. `[cmdk-item]` の数を確認 | `メーカーA` のみが表示され、他の選択肢は非表示になる | P0 |
| SS-003 | コンポーネント/検索 | 検索結果が0件の場合に空メッセージが表示される | 1. メーカー combobox を開く <br> 2. 検索入力に `存在しないメーカー` と入力 | `[cmdk-empty]` に「見つかりません」等のメッセージが表示される | P1 |
| SS-004 | コンポーネント/選択 | 選択肢をクリックすると値がセットされる | 1. メーカー combobox を開く <br> 2. `メーカーB` の `[cmdk-item]` をクリック | Popover が閉じ、トリガーボタンに「メーカーB」が表示される | P0 |
| SS-005 | コンポーネント/選択 | 選択済み項目にチェックマークが表示される | 1. メーカー combobox で `メーカーA` を選択 <br> 2. 再度 combobox を開く <br> 3. `メーカーA` の行を確認 | `メーカーA` の行に Check アイコン（`svg`）が表示されている | P1 |
| SS-006 | コンポーネント/クリア | clearable=true の場合、クリアボタンで値をリセットできる | 1. メーカー combobox で `メーカーA` を選択 <br> 2. トリガー内のクリア（`×`）ボタンをクリック | 値がクリアされ、プレースホルダーテキスト（「選択してください」）が表示される | P0 |
| SS-007 | コンポーネント/キーボード | 矢印キーで選択肢を移動し Enter で選択できる | 1. メーカー combobox を開く <br> 2. `ArrowDown` キーを2回押す <br> 3. `Enter` キーを押す | 2番目の選択肢が選択され、Popover が閉じる | P1 |
| SS-008 | コンポーネント/キーボード | Escape で Popover を閉じる | 1. メーカー combobox を開く <br> 2. `Escape` キーを押す | Popover が閉じ、値は変更されない | P1 |
| SS-009 | コンポーネント/disabled | disabled 状態ではクリック不可 | 1. 商品マスタ詳細画面（`/goods/{id}`）に遷移（閲覧モード） <br> 2. メーカーの combobox トリガーを確認 | `button[role="combobox"]` に `disabled` 属性があり、クリックしても Popover が開かない | P1 |
| SS-010 | コンポーネント/検索 | 検索テキストをクリアすると全選択肢が再表示される | 1. メーカー combobox を開く <br> 2. 検索入力に `A` と入力（フィルタされる） <br> 3. 検索入力をクリアする（全文削除） | すべての選択肢が再度表示される | P1 |
| SS-011 | コンポーネント/プレースホルダー | 未選択時にプレースホルダーが表示される | 1. 画面ロード後、メーカー combobox を確認 | トリガーボタンに「選択してください」等のプレースホルダーが表示される | P2 |

### Category 2: 各画面への組み込み検証（9箇所）

各画面で SearchableSelect が正しく組み込まれ、Select から置き換えられていることを検証する。

| TestID | Category | Test Case | Steps | Expected Result | Priority |
|--------|----------|-----------|-------|-----------------|----------|
| INT-001 | sales-goods/create Step1 | メーカー検索フォームが SearchableSelect になっている | 1. `mockAllApis(page)` -> `loginAndGoto(page, '/sales-goods/create')` <br> 2. 検索フォーム内の「メーカー」ラベルの横にある `button[role="combobox"]` を確認 <br> 3. クリックして Popover が開くことを確認 <br> 4. メーカーA, B, C が選択肢に表示されることを確認 | SearchableSelect として動作し、検索・選択が可能 | P0 |
| INT-002 | sales-goods/create Step1 | メーカー検索フォームで clearable が動作する | 1. `/sales-goods/create` に遷移 <br> 2. メーカー combobox で `メーカーA` を選択 <br> 3. クリア（`×`）ボタンをクリック | 値がクリアされプレースホルダーに戻る | P1 |
| INT-003 | sales-goods/create Step2 | 仕入先登録フォームが SearchableSelect になっている | 1. `/sales-goods/create` に遷移 <br> 2. テーブルの最初の商品行をクリックして Step2 に遷移 <br> 3. 「仕入先」ラベルの横にある `button[role="combobox"]` を確認 <br> 4. クリックして仕入先A, 仕入先B が表示されることを確認 | SearchableSelect として動作し、仕入先を検索・選択できる | P0 |
| INT-004 | sales-goods/create Step2 | 仕入先を選択してワーク保存できる | 1. Step2 に遷移 <br> 2. 必須項目（商品コード等）を入力 <br> 3. 仕入先 combobox で `仕入先A` を選択 <br> 4. 「ワークに保存」をクリック | バリデーション通過し、POST リクエストに `supplierNo: 1` が含まれる | P0 |
| INT-005 | sales-goods/detail (work) | 仕入先編集フォームが SearchableSelect になっている | 1. `mockAllApis(page)` -> `loginAndGoto(page, '/sales-goods/work')` <br> 2. 最初の行をクリックして詳細画面に遷移 <br> 3. 「編集」ボタンをクリック <br> 4. 「仕入先」フィールドが `button[role="combobox"]` であることを確認 <br> 5. クリックして仕入先が表示されることを確認 | 編集モードで SearchableSelect として動作する | P0 |
| INT-006 | sales-goods/detail (work) | 仕入先の初期値が正しく表示される | 1. ワーク詳細画面に遷移 <br> 2. 「編集」ボタンをクリック <br> 3. 仕入先 combobox のトリガーテキストを確認 | `仕入先A`（MOCK_SALES_GOODS_WORK_DETAIL の supplierNo=1 に対応）が表示される | P0 |
| INT-007 | sales-goods/detail (master) | 仕入先編集フォームが SearchableSelect になっている | 1. `mockAllApis(page)` -> `loginAndGoto(page, '/sales-goods')` <br> 2. 最初の行をクリックして詳細画面に遷移 <br> 3. 「編集」ボタンをクリック <br> 4. 「仕入先」フィールドが `button[role="combobox"]` であることを確認 | 編集モードで SearchableSelect として動作する | P1 |
| INT-008 | sales-goods/work-list | 仕入先検索フォームが SearchableSelect になっている | 1. `mockAllApis(page)` -> `loginAndGoto(page, '/sales-goods/work')` <br> 2. 検索フォーム内の「仕入先」ラベルの横にある `button[role="combobox"]` を確認 <br> 3. クリックして Popover が開くことを確認 <br> 4. 仕入先A, 仕入先B が選択肢に表示されることを確認 | SearchableSelect として動作する | P0 |
| INT-009 | sales-goods/work-list | 仕入先検索で絞り込みが動作する | 1. `/sales-goods/work` に遷移 <br> 2. 仕入先 combobox で `仕入先A` を選択 <br> 3. 「検索」ボタンをクリック | 検索リクエストに supplierNo パラメータが含まれ、ページがクラッシュしない | P1 |
| INT-010 | sales-goods/work-list | 仕入先検索フォームで clearable が動作する | 1. `/sales-goods/work` に遷移 <br> 2. 仕入先 combobox で `仕入先A` を選択 <br> 3. クリア（`×`）ボタンをクリック | 値がクリアされプレースホルダーに戻る | P1 |
| INT-011 | sales-goods/master-list | 仕入先検索フォームが SearchableSelect になっている | 1. `mockAllApis(page)` -> `loginAndGoto(page, '/sales-goods')` <br> 2. 検索フォーム内の「仕入先」ラベルの横にある `button[role="combobox"]` を確認 <br> 3. クリックして Popover が開き、仕入先一覧が表示されることを確認 | SearchableSelect として動作する | P0 |
| INT-012 | sales-goods/master-list | 仕入先検索フォームで clearable が動作する | 1. `/sales-goods` に遷移 <br> 2. 仕入先 combobox で値を選択後、クリア | 値がクリアされる | P1 |
| INT-013 | goods/index | メーカー検索フォームが SearchableSelect になっている | 1. `mockAllApis(page)` -> `loginAndGoto(page, '/goods')` <br> 2. 検索フォーム内の「メーカー」ラベルの横にある `button[role="combobox"]` を確認 <br> 3. クリックして Popover が開き、メーカー一覧が表示されることを確認 | SearchableSelect として動作する | P0 |
| INT-014 | goods/index | メーカー検索フォームで clearable が動作する | 1. `/goods` に遷移 <br> 2. メーカー combobox で `メーカーA` を選択 <br> 3. クリア（`×`）ボタンをクリック | 値がクリアされる | P1 |
| INT-015 | goods/index | メーカー選択後に検索が動作する | 1. `/goods` に遷移 <br> 2. メーカー combobox で `メーカーA` を選択 <br> 3. 「検索」ボタンをクリック | 検索リクエストに makerNo パラメータが含まれ、ページがクラッシュしない | P1 |
| INT-016 | goods/detail | メーカー編集フォームが SearchableSelect になっている | 1. `mockAllApis(page)` -> `loginAndGoto(page, '/goods')` <br> 2. 最初の行をクリックして詳細画面に遷移 <br> 3. 「編集」ボタンをクリック <br> 4. 「メーカー」フィールドが `button[role="combobox"]` であることを確認 | 編集モードで SearchableSelect として動作する | P0 |
| INT-017 | goods/detail | メーカーの初期値が正しく表示される | 1. 商品マスタ詳細画面に遷移（goodsNo=1） <br> 2. 「編集」ボタンをクリック <br> 3. メーカー combobox のトリガーテキストを確認 | `メーカーA`（MOCK_GOODS_LIST[0] の makerNo=1 に対応）が表示される | P0 |
| INT-018 | goods/detail | メーカーを変更して保存できる | 1. 商品マスタ詳細画面に遷移 <br> 2. 「編集」ボタンをクリック <br> 3. メーカー combobox で `メーカーB` を選択 <br> 4. 「保存」ボタンをクリック | PUT リクエストに `makerNo: 2` が含まれる | P1 |
| INT-019 | goods/detail | 閲覧モードではメーカーが disabled/表示のみ | 1. 商品マスタ詳細画面に遷移 <br> 2. 編集ボタンを押す前の状態で確認 | メーカーの値がテキスト表示され、combobox として操作できない | P1 |
| INT-020 | goods/create | メーカー登録フォームが SearchableSelect になっている | 1. `mockAllApis(page)` -> `loginAndGoto(page, '/goods/create')` <br> 2. 「メーカー」フィールドが `button[role="combobox"]` であることを確認 <br> 3. クリックして Popover が開くことを確認 | SearchableSelect として動作する | P0 |
| INT-021 | goods/create | 仕入先登録フォームが SearchableSelect になっている | 1. `/goods/create` に遷移 <br> 2. 「仕入先」フィールドが `button[role="combobox"]` であることを確認 <br> 3. クリックして仕入先一覧が表示されることを確認 | SearchableSelect として動作する | P0 |
| INT-022 | goods/create | メーカー・仕入先を選択して商品登録できる | 1. `/goods/create` に遷移 <br> 2. 必須項目を入力 <br> 3. メーカー combobox で `メーカーA` を選択 <br> 4. 仕入先 combobox で `仕入先A` を選択 <br> 5. 送信ボタンをクリック | POST リクエストに `makerNo: 1`, `supplierNo: 1` が含まれる | P0 |

### Category 3: 回帰テスト（既存機能の動作確認）

SearchableSelect への置き換えにより、既存の検索・登録・編集フローが壊れていないことを確認する。

| TestID | Category | Test Case | Steps | Expected Result | Priority |
|--------|----------|-----------|-------|-----------------|----------|
| REG-001 | 回帰/sales-goods/work-list | 検索フォームのリセットで SearchableSelect もクリアされる | 1. `/sales-goods/work` に遷移 <br> 2. 商品名に `テスト商品` を入力 <br> 3. 仕入先 combobox で `仕入先A` を選択 <br> 4. 「リセット」ボタンをクリック | 商品名入力がクリアされ、仕入先 combobox もプレースホルダーに戻る | P0 |
| REG-002 | 回帰/sales-goods/master-list | 検索フォームのリセットで SearchableSelect もクリアされる | 1. `/sales-goods` に遷移 <br> 2. 仕入先 combobox で値を選択 <br> 3. 「リセット」ボタンをクリック | 仕入先 combobox がプレースホルダーに戻る | P0 |
| REG-003 | 回帰/goods/index | 検索フォームのリセットで SearchableSelect もクリアされる | 1. `/goods` に遷移 <br> 2. メーカー combobox で `メーカーA` を選択 <br> 3. 「リセット」ボタンをクリック | メーカー combobox がプレースホルダーに戻る | P0 |
| REG-004 | 回帰/sales-goods/create | Step2 の仕入先未選択でバリデーションエラー | 1. `/sales-goods/create` に遷移 <br> 2. Step1 で商品を選択して Step2 に遷移 <br> 3. 仕入先を選択せずに「ワークに保存」をクリック | 仕入先が必須であるバリデーションエラーが表示される | P0 |
| REG-005 | 回帰/sales-goods/create | Step1 のメーカー選択後に検索が動作する | 1. `/sales-goods/create` に遷移 <br> 2. メーカー combobox で `メーカーA` を選択 <br> 3. 「検索」ボタンをクリック | ページがクラッシュせず、検索結果が表示される | P1 |
| REG-006 | 回帰/sales-goods/detail | 仕入先変更後に保存が動作する | 1. ワーク詳細画面に遷移 <br> 2. 「編集」ボタンをクリック <br> 3. 仕入先を `仕入先B` に変更 <br> 4. 「保存」ボタンをクリック | PUT リクエストに `supplierNo: 2` が含まれ、保存成功 | P1 |
| REG-007 | 回帰/sales-goods/detail | 編集キャンセルで仕入先が元の値に戻る | 1. ワーク詳細画面に遷移 <br> 2. 「編集」ボタンをクリック <br> 3. 仕入先を `仕入先B` に変更 <br> 4. 「キャンセル」ボタンをクリック <br> 5. 再度「編集」ボタンをクリック | 仕入先が元の値（`仕入先A`）に戻っている | P1 |
| REG-008 | 回帰/goods/detail | 編集キャンセルでメーカーが元の値に戻る | 1. 商品マスタ詳細画面に遷移 <br> 2. 「編集」ボタンをクリック <br> 3. メーカーを `メーカーC` に変更 <br> 4. 「キャンセル」ボタンをクリック <br> 5. 再度「編集」ボタンをクリック | メーカーが元の値（`メーカーA`）に戻っている | P1 |
| REG-009 | 回帰/goods/index | テーブル行クリックで詳細画面に遷移する | 1. `/goods` に遷移 <br> 2. テーブルの最初の行をクリック | `/goods/{goodsNo}` に遷移する（SearchableSelect 追加でイベント伝播が壊れていない） | P1 |
| REG-010 | 回帰/sales-goods/work-list | テーブル行クリックでワーク詳細に遷移する | 1. `/sales-goods/work` に遷移 <br> 2. テーブルの最初の行をクリック | `/sales-goods/work/{shopNo}/{goodsNo}` に遷移する | P1 |
| REG-011 | 回帰/sales-goods/master-list | テーブル行クリックでマスタ詳細に遷移する | 1. `/sales-goods` に遷移 <br> 2. テーブルの最初の行をクリック | `/sales-goods/{shopNo}/{goodsNo}` に遷移する | P1 |
| REG-012 | 回帰/全画面 | 旧 Select コンポーネントが残っていないこと | 対象7ファイルのソースを grep で確認: <br> `SelectTrigger`, `SelectContent`, `SelectItem` が仕入先/メーカー箇所に残っていないこと | 該当9箇所すべてが SearchableSelect に置き換わっている（コードレビューで確認） | P0 |

## テストファイル構成案

```
frontend/e2e/
  searchable-select-component.spec.ts   # SS-001 ~ SS-011（コンポーネント共通動作）
  searchable-select-integration.spec.ts # INT-001 ~ INT-022（各画面組み込み）
  searchable-select-regression.spec.ts  # REG-001 ~ REG-012（回帰テスト）
```

## 優先度サマリ

| Priority | 件数 | 説明 |
|----------|------|------|
| P0 | 17 | リリースブロッカー。SearchableSelect の基本動作、各画面での組み込み、リセット連動 |
| P1 | 22 | 重要。キーボード操作、クリア、初期値、保存フロー、回帰テスト |
| P2 | 1 | 低優先度。プレースホルダー表示 |

## 実装時の注意事項

1. **セレクタの安定性**: `button[role="combobox"]` は cmdk/Radix が自動付与する ARIA 属性に依存する。SearchableSelect 実装時に `data-testid` を付与することを推奨（例: `data-testid="maker-select"`, `data-testid="supplier-select"`）
2. **Popover の待機**: Popover のアニメーション完了を待つ必要がある場合は `await expect(popover).toBeVisible()` で対応
3. **IME 対応**: 日本語入力テストは Playwright の `page.keyboard.type()` では IME をシミュレートできないため、`fill()` を使用する
4. **既存テストへの影響**: 既存の `sales-goods-list.spec.ts`, `sales-goods-create.spec.ts`, `goods-detail.spec.ts` で Select 操作をしている箇所があればセレクタ更新が必要（現在の既存テストでは Select 操作自体は行っていないため影響は限定的）
