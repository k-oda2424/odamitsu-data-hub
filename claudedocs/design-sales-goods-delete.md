# 設計書: 販売商品ワーク削除機能

## 1. 背景・課題

販売商品ワーク詳細画面に削除機能がない。不要なワークレコードを削除する手段がなく、ゴミデータが蓄積する。また、ワーク削除時に対応するマスタレコードも不要になるケースがあり、連動削除の確認が必要。

## 2. 物理削除 vs 論理削除

### 結論: **論理削除（del_flg='1'）を推奨**

| 観点 | 物理削除 | 論理削除（推奨） |
|------|---------|----------------|
| プロジェクト一貫性 | 既存パターンと不一致 | `CustomService.delete()` で統一済み |
| 参照整合性 | `t_order_detail`, `m_partner_goods`, `m_purchase_price` 等が goodsNo を参照 → FK違反リスク | 参照先は残るため安全 |
| 監査証跡 | 消失（modifyUserNo, modifyDateTime が残らない） | `CustomService.delete()` が自動設定 |
| 復旧可能性 | 不可逆 | del_flg='0' に戻すだけで復旧可能 |
| 既存の削除処理 | `reflectFromWork` も `del_flg='1'` を使用 | 既存パターンと同一 |
| ワーク特性 | ステージングデータなので物理削除も合理的 | だが既に`reflectFromWork`が論理削除パターンを採用済み |

**補足:** `CustomService` に `deletePermanently()` メソッドも存在するが、プロジェクト全体で使用実績がなく、今回もあえて使用する理由がない。

## 3. 機能要件

### 3.1 基本フロー

```
ユーザー操作: 販売商品ワーク詳細画面で「削除」ボタンをクリック
  ↓
確認ダイアログ表示:
  - メッセージ: 「この販売商品ワークを削除しますか？」
  - チェックボックス: 「連動して販売商品マスタも削除する」
    - マスタが存在しない場合: チェックボックス非表示
  ↓
「削除」ボタンクリック
  ↓
API呼び出し: DELETE /api/v1/sales-goods/work/{shopNo}/{goodsNo}?deleteMaster=true/false
  ↓
成功: トースト「削除しました」→ 一覧画面に戻る（検索状態保持）
失敗: トースト「削除に失敗しました」
```

### 3.2 削除パターン

| パターン | ワーク | マスタ | 説明 |
|---------|--------|--------|------|
| A. ワークのみ削除 | del_flg='1' | 変更なし | マスタは残す |
| B. ワーク+マスタ削除 | del_flg='1' | del_flg='1' | 両方論理削除 |

### 3.3 バリデーション

- ワークレコードが存在しない → 404
- ワークが既に削除済み（del_flg='1'）→ 404
- `deleteMaster=true` でマスタが存在しない → ワークのみ削除（エラーにしない）
- ショップ権限チェック → `CustomService.delete()` が自動実行

## 4. API設計

### 4.1 エンドポイント

既存の `DELETE /api/v1/sales-goods/work/{shopNo}/{goodsNo}` を拡張する。

```
DELETE /api/v1/sales-goods/work/{shopNo}/{goodsNo}?deleteMaster={boolean}
```

| パラメータ | 型 | 必須 | デフォルト | 説明 |
|-----------|-----|------|-----------|------|
| shopNo | Integer | path | - | 店舗番号 |
| goodsNo | Integer | path | - | 商品番号 |
| deleteMaster | boolean | query | false | マスタも削除するか |

**レスポンス:**
- 204 No Content: 削除成功
- 404 Not Found: ワークレコードが存在しない

### 4.2 マスタ存在確認エンドポイント（新規）

フロントエンドでチェックボックスの表示制御に使用。既存の `GET /master/{shopNo}/{goodsNo}` でカバー可能だが、詳細画面のクエリで既にワーク情報を取得しているため、レスポンスにマスタ存在フラグを追加する方が効率的。

**変更:** `GET /api/v1/sales-goods/work/{shopNo}/{goodsNo}` のレスポンスに `hasMaster: boolean` を追加。

## 5. バックエンド実装

### 5.1 SalesGoodsController 変更

```java
@DeleteMapping("/work/{shopNo}/{goodsNo}")
public ResponseEntity<Void> deleteWork(
        @PathVariable Integer shopNo,
        @PathVariable Integer goodsNo,
        @RequestParam(defaultValue = "false") boolean deleteMaster) throws Exception {
    WSalesGoods work = wSalesGoodsService.getByPK(shopNo, goodsNo);
    if (work == null || Flag.YES.getValue().equals(work.getDelFlg())) {
        return ResponseEntity.notFound().build();
    }
    // ワーク論理削除
    wSalesGoodsService.delete(wSalesGoodsService.getRepository(), work);
    // マスタ連動削除
    if (deleteMaster) {
        MSalesGoods master = mSalesGoodsService.getByPK(shopNo, goodsNo);
        if (master != null && Flag.NO.getValue().equals(master.getDelFlg())) {
            mSalesGoodsService.delete(mSalesGoodsService.getRepository(), master);
        }
    }
    return ResponseEntity.noContent().build();
}
```

### 5.2 SalesGoodsDetailResponse 変更

`hasMaster` フィールドを追加:

```java
private boolean hasMaster;

public static SalesGoodsDetailResponse from(ISalesGoods sg, boolean hasMaster) {
    // 既存のfromロジック + hasMaster設定
}
```

### 5.3 GET /work/{shopNo}/{goodsNo} 変更

```java
@GetMapping("/work/{shopNo}/{goodsNo}")
public ResponseEntity<SalesGoodsDetailResponse> getWork(...) {
    WSalesGoods work = wSalesGoodsService.getByPK(shopNo, goodsNo);
    // ...
    MSalesGoods master = mSalesGoodsService.getByPK(shopNo, goodsNo);
    boolean hasMaster = master != null && Flag.NO.getValue().equals(master.getDelFlg());
    return ResponseEntity.ok(SalesGoodsDetailResponse.from(work, hasMaster));
}
```

## 6. フロントエンド実装

### 6.1 型定義変更

`types/goods.ts` の `SalesGoodsDetailResponse` に追加:
```typescript
hasMaster?: boolean  // ワーク詳細時のみ使用
```

### 6.2 詳細画面（detail.tsx）変更

**削除ボタン追加:** ヘッダーアクションに追加（ワーク時のみ表示）

```tsx
{isWork && !isEditing && (
  <Button variant="destructive" onClick={() => setDeleteDialogOpen(true)}>
    <Trash2 className="mr-2 h-4 w-4" />
    削除
  </Button>
)}
```

**削除確認ダイアログ:**

```tsx
<AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
  <AlertDialogContent>
    <AlertDialogHeader>
      <AlertDialogTitle>販売商品ワークの削除</AlertDialogTitle>
      <AlertDialogDescription>
        「{data.goodsName}」（{data.goodsCode}）を削除しますか？
      </AlertDialogDescription>
    </AlertDialogHeader>
    {data.hasMaster && (
      <div className="flex items-center space-x-2 py-2">
        <Checkbox
          id="deleteMaster"
          checked={deleteMaster}
          onCheckedChange={(c) => setDeleteMaster(c === true)}
        />
        <Label htmlFor="deleteMaster">
          連動して販売商品マスタも削除する
        </Label>
      </div>
    )}
    <AlertDialogFooter>
      <AlertDialogCancel>キャンセル</AlertDialogCancel>
      <AlertDialogAction onClick={handleDelete}>
        削除
      </AlertDialogAction>
    </AlertDialogFooter>
  </AlertDialogContent>
</AlertDialog>
```

**削除 mutation:**

```tsx
const deleteMutation = useMutation({
  mutationFn: (params: { deleteMaster: boolean }) =>
    api.delete(`/sales-goods/work/${shopNo}/${goodsNo}?deleteMaster=${params.deleteMaster}`),
  onSuccess: () => {
    toast.success('削除しました')
    // 一覧に戻る（検索状態保持）
    if (window.history.length > 1) { router.back() } else { router.push(fallbackPath) }
  },
  onError: () => toast.error('削除に失敗しました'),
})
```

## 7. 変更対象ファイル

### バックエンド
| ファイル | 変更内容 |
|---------|---------|
| `SalesGoodsController.java` | DELETE エンドポイントに `deleteMaster` パラメータ追加、GET work にマスタ存在チェック追加 |
| `SalesGoodsDetailResponse.java` | `hasMaster` フィールド追加 |

### フロントエンド
| ファイル | 変更内容 |
|---------|---------|
| `types/goods.ts` | `SalesGoodsDetailResponse` に `hasMaster` 追加 |
| `components/pages/sales-goods/detail.tsx` | 削除ボタン・確認ダイアログ・mutation追加 |

## 8. テスト観点

| # | テストケース | 期待結果 |
|---|-------------|---------|
| 1 | ワークのみ削除（deleteMaster=false） | ワーク: del_flg='1', マスタ: 変更なし |
| 2 | ワーク+マスタ削除（deleteMaster=true） | ワーク: del_flg='1', マスタ: del_flg='1' |
| 3 | マスタ存在しない時にdeleteMaster=true | ワークのみ削除、エラーなし |
| 4 | 既に削除済みワークを削除 | 404 |
| 5 | 別ショップのワークを削除 | CustomService.delete() で例外 |
| 6 | 削除後に一覧に戻る | 検索条件・結果が保持されている |
| 7 | マスタ存在時のダイアログ | チェックボックスが表示される |
| 8 | マスタ非存在時のダイアログ | チェックボックスが非表示 |
