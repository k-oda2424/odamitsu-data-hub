# コードレビュー: B-CART 変更点一覧・一括反映機能

レビュー日: 2026-05-01
ブランチ: refactor/code-review-fixes
レビュアー: code-reviewer subagent (Opus)

## 前提: 設計レビュー指摘の対応状況

設計レビュー (`design-review-bcart-pending-changes.md`) の Critical/Major 6 件は実装で全て解消済み。

| 設計ID | 内容 | 確認結果 |
|---|---|---|
| C-1 | @Transactional 別Bean化 | `BCartReflectTransactionService` で分離済み |
| C-2 | 同期API sleep 撤廃・件数上限200 | `BCartPendingChangesController:27` `MAX_REFLECT_BATCH=200` |
| M-1 | history id 保持・IN句限定UPDATE | `AggregatedChange` record + `markReflectedByIds` 済み |
| M-2 | 一覧主源泉を change_history に統一 | `BCartPendingChangesQueryService` で統一済み |
| M-3 | body() NPEリスク | `BCartProductSetsReflectService:107` null チェック済み |
| M-5 | Controller 分割 | `BCartProductSetsController` + `BCartPendingChangesController` |

---

## Critical

### C-impl-1: エンティティ直接返却
- **場所**: `backend/src/main/java/jp/co/oda32/api/bcart/BCartProductSetsController.java:31`
- **問題**: `ResponseEntity<BCartProductSets>` で JPA Entity をそのまま返却。`BCartProductSets` には `@ManyToOne(fetch=LAZY) bCartProducts`、`@OneToMany groupPrices/specialPrices`、DB保存用 JSON 文字列フィールド (`groupPrice`/`specialPrice`/`stockParent`) が含まれる。
- **影響**: Lazy 関連の `LazyInitializationException` リスク、内部表現の API 漏洩、CLAUDE.md「Entity を直接返さない」違反。
- **修正案**: `BCartProductSetPricingResponse` DTO (`id`, `unitPrice`, `shippingSize`, `bCartPriceReflected`, `updatedAt`) を作成し `Response.from(entity)` パターンへ変更。

---

## Minor

### F-impl-1: `dirty` 判定に IIFE
- **場所**: `frontend/components/pages/bcart/product-detail.tsx:26-30`
- **問題**: 即時実行関数 `(() => { ... })()` のためレンダリング毎に関数オブジェクト生成。
- **修正案**: `useMemo(...)` に置換。

### F-impl-2: `janCode` 列が pending-changes テーブルに未表示
- **場所**: `frontend/components/pages/bcart/pending-changes.tsx` thead/tbody
- **問題**: 型と設計書にあるが UI に列がない。
- **修正案**: 品番列の隣に JAN コード列を追加。

### F-impl-3: `BCartChangeHistory.beforeValue/afterValue` が null 非許容型
- **場所**: `frontend/types/bcart.ts:101-102` (元レビュー指摘行: 133-134 は誤、実位置は 101-102)
- **問題**: バックエンドのカラムは nullable だが TS は `string` 必須。truthy チェックで回避しているが型不一致。
- **修正案**: `beforeValue: string | null` / `afterValue: string | null` に変更。

---

## 対応表

| ID | 判定 | 対応 |
|---|---|---|
| C-impl-1 | Accept | DTO 作成 + Controller 戻り値変更 |
| F-impl-1 | Accept | useMemo に置換 |
| F-impl-2 | Accept | JAN コード列追加 |
| F-impl-3 | Accept | nullable に変更 |

**Approval status**: Needs Revision → 上記対応で **Approved 想定**
