# 設計レビュー: B-CART商品 変更点一覧・一括反映機能

レビュー実施日: 2026-05-01
対象設計書: `claudedocs/design-bcart-pending-changes.md`
レビュアー: code-reviewer subagent (Opus)
判定: **Needs Revision**（Critical 2 / Major 5 / Minor 4）

---

## Critical

### C-1: `@Transactional` を private メソッドに付与しても機能しない
- **指摘**: §6-1「処理フロー」擬似コードで `@Transactional private void markReflected()` と記載。Spring の `@Transactional` は CGLIB プロキシ経由のため private 呼出には適用されない。
- **影響**: PATCH 成功後の DB 更新失敗時にロールバックされず、§3 で謳う「PATCH 成功 → DB 更新の順序厳守」を満たせない（B-CART 反映済 / DB 未反映の不整合）。
- **修正案**: `markReflected` を public メソッドとして別 Bean (`BCartChangeHistoryService` 既存活用) に切り出すか、`ReflectService` 内に `public @Transactional` メソッドを置きトランザクション境界を確保。

### C-2: 同期 API 内の `Thread.sleep(5 分)` がサーバースレッドを枯渇させる
- **指摘**: §2 非機能要件で「250件で5分待機」を同期 REST エンドポイントに組込む案。Tomcat の `max-threads`（運用デフォルト 200）を 5 分間占有すると同時接続キューが詰まる。
- **影響**: 他 API リクエストがすべてタイムアウトする可能性。
- **修正案**: 同期 API ではレート制限到達時に sleep せず 422/400 を返す。件数上限を明示（例: 最大 200 件）。超過時はバッチ運用への誘導 or 分割実行を促す。

---

## Major

### M-1: `aggregate` と `markReflected` の間に競合ウィンドウ
- **指摘**: ループ内で取得した履歴 ID リストを保持せず "未反映条件" で UPDATE すると、その間に追加された新規履歴も `b_cart_reflected=true` に誤マークされる。
- **影響**: 新規編集が反映済扱いになり消失。
- **修正案**: aggregate 時に対象 history `id` を List で保持し、UPDATE は `WHERE id IN (...)` 限定で実行。

### M-2: `b_cart_price_reflected` と履歴の二重フラグの整合性
- **指摘**: 一覧 SQL が `ps.b_cart_price_reflected=false` で絞ると、フラグだけ true で履歴未反映の不整合行が表示されない。
- **影響**: 履歴が残っているのに UI に出ず反映されないケース。
- **修正案**: 一覧の主源泉を `b_cart_change_history` (target_type='PRODUCT_SET' AND b_cart_reflected=false) に統一し、`b_cart_price_reflected` フラグは「補助マーカー」または **廃止検討**。

### M-3: `response.body().string()` の NPE リスク
- **指摘**: 設計書擬似コードに null チェックなし。OkHttp は body() = null を返すことがある。
- **影響**: B-CART がボディ無しエラーを返すと NPE。
- **修正案**: 既存 `BCartProductDescriptionUpdateTasklet:132` パターン (`res.body() != null ? res.body().string() : ""`) を踏襲。設計書擬似コードに明記。

### M-4: `before_value` の意味定義不明確（同期バッチ後）
- **指摘**: §7 のエッジケース「編集後に B-CART 同期が走る」シナリオで、`before_value` が「編集直前の DB 値」なのか「B-CART の現在値」なのかが曖昧。
- **影響**: 「最古 before でロールバックすれば元に戻る」期待が崩れる。
- **修正案**: 設計書に「`before_value` = 編集直前のローカル DB 値であり、B-CART 上の現在値を保証しない」と明記。

### M-5: Controller URL マッピングの分離が未定
- **指摘**: 既存 `BCartProductController` は `@RequestMapping("/api/v1/bcart/products")` 固定。`/bcart/product-sets/{id}/pricing` や `/bcart/pending-changes` をここに追加するとパスが `/products/...` 配下に潜る。
- **影響**: 設計書の URL とバックエンド実 URL が不一致 → 404。
- **修正案**: `BCartProductSetsController`（`/api/v1/bcart/product-sets`）と `BCartPendingChangesController`（`/api/v1/bcart/pending-changes`）を新規作成。設計書のアーキ図とロールアウト計画も合わせて更新。

---

## Minor

### m-1: `BCartProductSet` (TS型) に `shippingSize` がない
- **修正案**: `frontend/types/bcart.ts` に `shippingSize: number | null` 追加。バックエンド `BCartProductResponse` の DTO に同フィールドが含まれているか実装時に確認。

### m-2: サイドバーアイコン `AlertTriangle` の意味が不適切
- **修正案**: `ListOrdered` または `Diff` 等、変更一覧らしいアイコンに変更。

### m-3: 一覧 GET のページネーション戦略が未定
- **修正案**: 初期実装で `LIMIT 500`、超過時は件数のみ返すか別画面誘導。

### m-4: スタブ `BCartGoodsPriceTableUpdateTasklet` 削除前の参照確認
- **修正案**: ロールアウト計画に「`grep -r BCartGoodsPriceTableUpdate` で全参照を確認」を明記。

---

## 対応表

| ID | 判定 | 対応 |
|---|---|---|
| C-1 | Accept | 設計書を修正：`markReflected` を public + `@Transactional` 別 Bean 化 |
| C-2 | Accept | 同期 API から sleep を撤廃、件数上限 200 件で 400 を返す |
| M-1 | Accept | aggregate 時に history id list 保持、UPDATE は IN 句限定 |
| M-2 | Accept | 一覧 SQL の主源泉を `b_cart_change_history` に統一 |
| M-3 | Accept | 既存パターン踏襲（null チェック追加） |
| M-4 | Accept | before_value の意味を設計書に明記 |
| M-5 | Accept | 新規コントローラ 2 つに分割（`BCartProductSetsController` + `BCartPendingChangesController`） |
| m-1 | Accept | ロールアウト計画に型定義拡張を追記 |
| m-2 | Accept | アイコンを `Diff` に変更 |
| m-3 | Accept | LIMIT 500 を SQL に明記 |
| m-4 | Accept | 削除前 grep 確認を Phase 3-A.1 に追記 |
