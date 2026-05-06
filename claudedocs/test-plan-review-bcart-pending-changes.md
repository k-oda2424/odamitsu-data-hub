# テスト計画レビュー: B-CART 変更点一覧・一括反映機能

レビュー日: 2026-05-01
対象: `claudedocs/test-plan-bcart-pending-changes.md`
判定: **Needs Minor Revision**（Major 2 / Minor 3）

## Major

### MA-1: DTO レスポンス契約のテストが欠落
- 実装レビュー C-impl-1（エンティティ直接返却 → DTO 化）の挙動を保証するテストがない
- 対応: `PSC-I01` の `andExpect` に以下を追加
  - `jsonPath("$.groupPrices").doesNotExist()` — LAZY 関連が漏れていない
  - `jsonPath("$.stockParent").doesNotExist()` — 内部フィールドが漏れていない
  - `jsonPath("$.id|.unitPrice|.shippingSize|.bCartPriceReflected").exists()`
- タグ `[C-impl-1]` を付与

### MA-2: 不整合状態テスト不足
- `bCartPriceReflected=false` だが history 0 件のケース（設計書 §7 記載）の検証なし
- 対応: `REP-I07` を追加。期待動作: `findUnreflectedForProductSet` が空リスト

## Minor

### MI-1: E2E-10 の `succeeded=2` アサーション
- mock 件数依存。fixture を E2E-10 専用に切り出すか動的に計算

### MI-2: `truncated` フィールド位置
- 「ヘッダ or フィールド」が未決。`jsonPath("$.truncated", is(true))` に統一

### MI-3: curl コマンドの `Content-Type` 欠落
- 手順 3 以降で `-H "Content-Type: application/x-www-form-urlencoded"` 省略 → 415 のリスク

## 対応表
| ID | 判定 | 対応 |
|---|---|---|
| MA-1 | Accept | テスト計画に追記（PSC-I01 拡張 + Critical 追跡表追加） |
| MA-2 | Accept | REP-I07 追加 |
| MI-1 | Accept | fixture 設計を専用化 |
| MI-2 | Accept | 統一 |
| MI-3 | Accept | 全 curl に Content-Type 明記 |
