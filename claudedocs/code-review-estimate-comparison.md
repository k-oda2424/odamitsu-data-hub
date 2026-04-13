# 比較見積機能 コードレビュー結果

**レビュー日:** 2026-04-09

## 対応結果

| 指摘ID | Severity | 内容 | 判定 | 対応 |
|--------|----------|------|------|------|
| C1 | Critical | `nowGoodsPrice` が常に `afterPrice` から取得され、価格変更予定がない商品は null になる | Accept | VEstimateGoods/VEstimateGoodsSpecial の具象型から `getNowGoodsPrice()` を取得するように修正 |
| C2 | Critical | `goodsNoList` のサイズ上限がない（DoS リスク） | Accept | 上限 50 を追加 |
| M1 | Major | N+1 クエリ（goodsNo ごとに最大4回の個別DB呼出） | Defer | 現状 MAX_ITEMS=10 で��大40クエリ。Phase 2 でバッチ取得メソッド追加を検討 |
| M2 | Major | 基準品が除外できない | Accept | 全商品に除外ボタンを表示するよう修正（基準品自動昇格は既存） |
| M4 | Major | `goodsNoList` をカンマ区切りで送信（Spring のデフォルト動作に依存） | Accept | `repeated params` 形式に変更 |
| M5 | Major | sessionStorage 復元が初回レンダー後に走り二重レンダー | Defer | 動作に影響なし、UX 改善は Phase 2 |
| m1 | Minor | 非admin にも purchasePrice が API レスポンスに含まれる | Defer | 既存見積APIと同じパターン。必要に応じてPhase 2で対応 |
| m2 | Minor | `pricePlanInfo` がサーバー側で整形済み文字列 | Defer | 既存パターンと一致 |
| m3 | Minor | `@Transactional(readOnly = true)` が欠落 | Accept | 追加済み |
| m4 | Minor | クラスレベル @PreAuthorize と重複する method-level アノテーション | Defer | 既存コードと一致、変更不要 |
| m5 | Minor | `handleRemove` の配列要素直接代入 | Defer | filter() 後の新配列なので安全 |
| m6 | Minor | `fmt`/`fmtRate` が form.tsx と comparison.tsx で重�� | Accept | `lib/estimate-calc.ts` に統合済み |
| m7 | Minor | DiffCell の方向仮定（higher = better） | Defer | 現用途では正しい |
| m8 | Minor | comparison.tsx が 551 行で大きめ | Defer | 800行以下なので許容範囲 |
| m9 | Minor | サイドバーの isMenuActive が正しく動作 | - | 問題なし |
