# 設計書: 見積編集画面の納品先プリセレクト不具合修正

**日付**: 2026-05-01
**ブランチ**: refactor/code-review-fixes
**関連ファイル**:
- backend: `EstimateResponse.java`
- frontend: `types/estimate.ts`, `components/pages/estimate/form.tsx`, `components/pages/estimate/EstimateHeaderForm.tsx`

## 1. 課題

見積修正画面（`/estimates/{no}/edit`）および見積作成画面（`/estimates/create` の prefill 経由）で、見積に保存済みの納品先（destinationNo）が SearchableSelect の選択状態として表示されない。

ユーザー要件:
> 見積作成、修正のとき、主に修正の時になると思うが、納品先がselect2で指定されている状態で表示するように修正してください。

## 2. 根本原因

### 現状コード

`form.tsx` (行 117) で state は正しく設定される:
```tsx
setDestinationNo(est.destinationNo ? String(est.destinationNo) : '')
```

しかし `EstimateHeaderForm.tsx` の SearchableSelect は `destinationsQuery.data` 内の option を参照して label をルックアップする:
```tsx
options={(destinationsQuery.data ?? []).map((d) => ({
  value: String(d.destinationNo),
  label: `${d.destinationCode ?? ''} ${d.destinationName}`,
}))}
```

そして `SearchableSelect.tsx` (行 44):
```tsx
const selectedLabel = options.find((opt) => opt.value === value)?.label
```

`destinationsQuery` が呼ぶ `GET /api/v1/masters/destinations?partnerNo=X` は backend (`MasterController#listDestinations`, 行 155) で `del_flg='0'` のみ返却する。

### 失敗シナリオ

| シナリオ | 状況 | 表示結果 |
|---------|------|---------|
| A. 削除済み納品先 | 見積保存後に納品先が `del_flg='1'` に変更 | options に含まれず placeholder 表示 |
| B. partner 紐づけ変更 | 納品先の partner_no が後から変更 | partnerNo フィルタで除外されplaceholder |
| C. 非同期ロード中 | partnerNo 設定直後で destinations API レスポンス待ち | 短時間 placeholder（その後 label 出る） |

シナリオ A/B は永続的に表示されない実害がある。シナリオ C は一時的だが UX 的にも好ましくない。

## 3. 設計方針

EstimateResponse 自体が保持する `destinationName` と新規追加する `destinationCode` を用い、`destinationsQuery.data` に含まれない場合でも fallback option として SearchableSelect の options に注入する。

### 設計判断

| 項目 | 判断 | 理由 |
|------|------|------|
| destinationCode を EstimateResponse に追加 | YES | label `${code} ${name}` の整合性を保つ |
| Fallback option の表示形式 | `${code} ${name}`（既存と同形式） | UI 一貫性。削除済みサフィックスは v2 で検討 |
| Fallback option は選択可能か | YES（option として有効） | 既存値の保持を優先 |
| ユーザーが partner 変更で destination リセットされる動作 | 維持 | 既存仕様。fallback はあくまで初期表示の補完 |
| 「削除済み」マーキング | しない（v1） | YAGNI。実害があれば後対応 |

### 補足: prefill 経由の作成画面も対象

`/estimates/create` で `sessionStorage('estimate-prefill')` から destination を読み込むケースもあるが、prefill JSON は `destinationNo` しか持たないため fallback option を作れない。
→ 別途 destination 詳細 API を叩くか、prefill 側で destinationName/Code も保存するかの選択。

**v1 方針**: prefill 経由は対象外（既存通り destinations API ヒット時のみ表示）。理由: prefill flow は既に何らかのソース画面（partner-goods 等）で destination を選択した直後の遷移であり、destinations API キャッシュもヒットしやすい。修正は edit mode に限定。

## 4. 変更内容

### Backend

**`backend/src/main/java/jp/co/oda32/dto/estimate/EstimateResponse.java`**
- `private String destinationCode;` フィールド追加
- `from()` で `e.getMDeliveryDestination().getDestinationCode()` を取り出して setter (builder) に設定
- `fromWithDetails()` は `from()` をデリゲートしているため個別修正不要（builder 経由で自動伝播）
- `EstimatePdfService` も `from()` を呼ぶが PDF 生成には destinationCode を使わないため副作用なし

### Frontend

**`frontend/types/estimate.ts`**
- `EstimateResponse` に `destinationCode: string | null` 追加

**`frontend/components/pages/estimate/EstimateHeaderForm.tsx`**
- 新 prop: `destinationFallback?: { destinationNo: number; destinationName: string | null; destinationCode: string | null } | null`
- options 生成ロジック: useMemo でラップ。**dedup 必須**: destinationsQuery.data に fallback と同一 destinationNo が含まれる場合は prepend しない
- 実装シグネチャ:
  ```tsx
  const mergedOptions = useMemo(() => {
    const base = (destinationsQuery.data ?? []).map((d) => ({
      value: String(d.destinationNo),
      label: `${d.destinationCode ?? ''} ${d.destinationName}`,
    }))
    if (!destinationFallback) return base
    const fallbackValue = String(destinationFallback.destinationNo)
    if (base.some((opt) => opt.value === fallbackValue)) return base // dedup
    return [
      {
        value: fallbackValue,
        label: `${destinationFallback.destinationCode ?? ''} ${destinationFallback.destinationName ?? ''}`.trim(),
      },
      ...base,
    ]
  }, [destinationsQuery.data, destinationFallback])
  ```

**`frontend/components/pages/estimate/form.tsx`**
- `EstimateHeaderForm` 呼び出しに `destinationFallback` を追加
- estimateQuery.data から `{ destinationNo, destinationName, destinationCode }` を組み立てて渡す
- **必須ガード**: `destinationNo` が `null` または `0` の場合は `null` を渡す（DB に `destination_no=0` が「未設定」として入っているレコードに対応）
  ```tsx
  destinationFallback={
    estimateQuery.data && (estimateQuery.data.destinationNo ?? 0) > 0
      ? {
          destinationNo: estimateQuery.data.destinationNo!,
          destinationName: estimateQuery.data.destinationName,
          destinationCode: estimateQuery.data.destinationCode,
        }
      : null
  }
  ```

### E2E

**`frontend/e2e/helpers/mock-api.ts`**
- `MOCK_DESTINATIONS` の各エントリに既存の `destinationCode` あり → 変更なし
- 既存 MOCK_ESTIMATES に destinationCode 追加（削除されない destination 用）
- 削除済み destination のテスト用に新規 MOCK_ESTIMATE エントリ追加

**`frontend/e2e/estimate-form.spec.ts`**
- 新ケース F-09: 編集モードで保存済み納品先がプリセレクト表示される
- 新ケース F-10: 削除済み納品先（destinations API に含まれない）でも fallback option で表示される

## 5. 後方互換性

- `EstimateResponse.destinationCode` は新規フィールドのみ（既存フィールド削除なし）→ JSON 後方互換
- フロントエンド `destinationCode` は optional 型 → MOCK で省略しても TypeScript エラーなし（ただし全モックに付与する）
- 既存 E2E ケース F-08 の挙動は変更なし

## 6. 実装順

1. Backend: EstimateResponse 修正
2. Frontend: types/estimate.ts 修正
3. Frontend: EstimateHeaderForm 修正（destinationFallback prop 追加）
4. Frontend: form.tsx 修正（destinationFallback を渡す）
5. E2E mock 拡充
6. E2E F-09/F-10 ケース追加
7. tsc / compileJava / Playwright 実行

## 7. リスク・注意

- バックエンド再起動が必要（DTO 変更）→ コミット時にユーザーに再起動依頼を明示
- 既存の MOCK_ESTIMATES の destinationNo は 0（destination なし）なので fallback ロジックは発動しない → 新ケースで明示的に検証
