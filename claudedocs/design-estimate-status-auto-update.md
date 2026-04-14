# 設計書: 見積ステータス自動更新（印刷時→提出済 / 改定日到来→価格反映済）

- **作成日**: 2026-04-10
- **作成者**: kazuki + Claude
- **ステータス**: 実装完了

---

## 1. Background / Problem

### 現状の課題

新システム（odamitsu-data-hub）では、見積ステータスの自動遷移が **2箇所欠落** している:

1. **印刷時の「提出済」自動更新がない**
   - 旧システム: 「このページを印刷する」クリック → `window.print()` 実行 + `/notifiedEstimate` にナビゲーション → ステータスを `10`(提出済) or `30`(修正後提出済) に自動更新
   - 新システム: 「印刷」ボタンは `window.print()` のみ。「PDF」ボタンは PDF ダウンロードのみ。**ステータスは変わらない**
   - 結果: ユーザーが手動で Select ドロップダウンから「提出済」を選ぶ必要がある

2. **「価格反映済」バッチは存在するが、前提条件を満たせない**
   - バッチ `PartnerPriceChangeReflectTasklet` は `priceChangeDate <= 今日` かつ `status IN (10, 30, 40)` の見積を検索
   - 印刷時にステータスが `10`/`30` に変わらないため、バッチの検索条件に引っかからない
   - 結果: `priceChangeDate` を過ぎても見積は `00`(作成) のまま放置される

### 影響

- 得意先に提出した見積が「作成」ステータスのまま残り、運用上の管理が困難
- 価格改定日が来ても `m_partner_goods` への価格反映が自動実行されない
- 旧システムと異なる挙動のため、ユーザーの作業フローが崩れる

---

## 2. 旧システム（stock-app）の挙動

### 2-1. 印刷時のステータス遷移

```
[印刷ボタンクリック]
  │
  ├── confirm("印刷しますか？")
  │     ├── キャンセル → 何もしない
  │     └── OK → window.print() + ページ遷移
  │
  └── GET /notifiedEstimate?estimateNo={no}
        │
        └── EstimateUtil.getNotifiedStatus(currentStatus)
              │
              ├── 00 (作成)           → 10 (提出済)
              ├── 20 (修正)           → 30 (修正後提出済)
              ├── 30 (修正後提出済)    → 30 (変化なし)
              └── その他              → 10 (提出済)
```

**実装箇所（旧）:**
- テンプレート: `estimate_detail_list.html` line 162 — `<a>` の href が `/notifiedEstimate`
- JS: `estimateDetailList.js` — `window.print()` 後にブラウザがリンク先に遷移（`preventDefault` なし）
- Controller: `EstimateInputController.notifiedEstimate()` lines 214-228
- ユーティリティ: `EstimateUtil.getNotifiedStatus()` lines 174-186

### 2-2. 価格反映済バッチ

```
[日次バッチ: PartnerPriceChangePlanCreateBatch / OneDayCloseBatch]
  │
  └── Step 5: PartnerPriceChangeReflectTasklet
        │
        ├── 検索条件:
        │     priceChangeDate <= today
        │     AND estimateStatus IN ('10', '30', '40')
        │     AND delFlg = '0'
        │
        ├── 処理:
        │     1. 見積明細の価格を m_partner_goods に反映
        │     2. 見積ステータスを '70' (価格反映済) に更新
        │
        └── 結果: 提出済の見積が改定日到来で自動的に価格反映済に
```

**実装箇所（旧・新共通）:**
- バッチ: `PartnerPriceChangeReflectTasklet.java`
- 検索: `TEstimateService.findReflectEstimate()` → `priceChangeDatePastContains` + `estimateStatusIn`
- 新システムでもバッチ自体は存在するが、ステータスが `10`/`30`/`40` にならないため実質的に動かない

### 2-3. 他同グループ提出済 (40)

- 親見積作成/更新時に子見積のステータスを `40` に自動設定
- バッチ `ParentEstimateCreatedTasklet` でも同期
- **新システムでも同じロジックが実装済み** — 変更不要

---

## 3. Requirements

### 機能要件

| # | 要件 | 旧システム準拠 |
|---|---|---|
| R-1 | 見積の「印刷」ボタンクリック時にステータスを「提出済」(10) / 「修正後提出済」(30) に自動更新する | ✓ |
| R-2 | 見積の「PDF」ダウンロード時にも同様にステータスを自動更新する | ✗（新規追加） |
| R-3 | ステータス遷移ルール: 00→10, 20→30, 30→30（変化なし）, その他→10 | ✓ |
| R-4 | 印刷/PDF前に確認ダイアログを表示する | ✓ |
| R-5 | 比較見積の印刷時にも同様のステータス自動更新を適用する | ✗（新規追加） |

### 非機能要件
- 印刷/PDF ダウンロードの UX を損なわないこと（ステータス更新は非同期で可）
- ステータス更新に失敗しても印刷/PDF 自体は完了すること

---

## 4. Proposed Solution

### 4-1. 見積詳細画面 — 印刷ボタン

**現在の挙動:**
```tsx
<Button variant="outline" onClick={() => window.print()}>印刷</Button>
```

**変更後:**
```tsx
const handlePrint = async () => {
  if (!confirm('印刷しますか？ステータスが「提出済」に更新されます。')) return
  window.print()
  // 非同期でステータス更新（印刷は先に実行される）
  try {
    const notifiedStatus = getNotifiedStatus(est.estimateStatus)
    if (notifiedStatus !== est.estimateStatus) {
      await api.put(`/estimates/${estimateNo}/status`, { estimateStatus: notifiedStatus })
      queryClient.invalidateQueries({ queryKey: ['estimate', estimateNo] })
      toast.success(`ステータスを「${getEstimateStatusLabel(notifiedStatus)}」に更新しました`)
    }
  } catch {
    toast.error('ステータスの更新に失敗しました')
  }
}
```

### 4-2. 見積詳細画面 — PDFダウンロードボタン

**変更後:**
```tsx
const handleDownloadPdf = async () => {
  if (!confirm('PDFをダウンロードしますか？ステータスが「提出済」に更新されます。')) return
  // PDF ダウンロード（既存ロジック）
  try {
    const { blob, filename } = await api.download(`/estimates/${estimateNo}/pdf?userName=${...}`)
    // ... download logic ...
  } catch {
    toast.error('PDFのダウンロードに失敗しました')
    return
  }
  // ステータス更新
  try {
    const notifiedStatus = getNotifiedStatus(est.estimateStatus)
    if (notifiedStatus !== est.estimateStatus) {
      await api.put(`/estimates/${estimateNo}/status`, { estimateStatus: notifiedStatus })
      queryClient.invalidateQueries({ queryKey: ['estimate', estimateNo] })
      toast.success(`ステータスを「${getEstimateStatusLabel(notifiedStatus)}」に更新しました`)
    }
  } catch {
    toast.error('ステータスの更新に失敗しました')
  }
}
```

### 4-3. ステータス遷移関数（フロントエンド共通ユーティリティ）

`types/estimate.ts` に追加:

```ts
/**
 * 印刷/PDF出力時のステータス自動遷移ルール。
 * 旧システム EstimateUtil.getNotifiedStatus() と同等。
 */
export function getNotifiedStatus(currentStatus: string | null): string {
  switch (currentStatus) {
    case '20': // 修正
    case '30': // 修正後提出済
      return '30' // → 修正後提出済
    default:
      return '10' // → 提出済
  }
}
```

### 4-4. 比較見積詳細画面

比較見積の印刷ボタンにも同じロジックを適用:
- `PUT /estimate-comparisons/{no}/status` で `{ comparisonStatus: getNotifiedStatus(current) }`
- 確認ダイアログ + 非同期ステータス更新

### 4-5. 既存バッチ（変更不要）

`PartnerPriceChangeReflectTasklet` は既に正しく動作する:
- 印刷時にステータスが `10`/`30` に更新されるようになれば、改定日到来時にバッチが検知して `70` に更新する
- **コード変更不要**

---

## 5. 変更箇所

| ファイル | 変更内容 |
|---|---|
| `frontend/types/estimate.ts` | `getNotifiedStatus()` 関数を追加 |
| `frontend/components/pages/estimate/detail.tsx` | 印刷・PDFボタンにステータス自動更新 + 確認ダイアログ追加 |
| `frontend/components/pages/estimate-comparison/detail.tsx` | 印刷ボタンにステータス自動更新 + 確認ダイアログ追加 |

**バックエンド変更: なし**（既存の `PUT /estimates/{no}/status` エンドポイントをそのまま利用）

---

## 6. ステータス遷移図（全体）

```
                    [新規作成]
                        │
                        ▼
                   ┌──────────┐
                   │ 00 作成  │
                   └────┬─────┘
                        │ 印刷/PDF
                        ▼
                   ┌──────────┐     修正して再印刷    ┌──────────────┐
                   │ 10 提出済│ ──────────────────→ │ 20 修正      │
                   └────┬─────┘                     └────┬─────────┘
                        │                                │ 印刷/PDF
                        │                                ▼
                        │                          ┌──────────────┐
                        │                          │ 30 修正後提出│
                        │                          └────┬─────────┘
                        │                               │
                        ├───────────────────────────────┘
                        │ priceChangeDate 到来（バッチ自動）
                        ▼
                   ┌──────────────┐
                   │ 70 価格反映済│
                   └──────────────┘

    ※ 40 (他同グループ提出済) は親見積作成時に子見積が自動設定される
    ※ 50, 60, 90, 99 は手動設定のみ
```

---

## 7. Edge Cases

| # | シナリオ | 挙動 |
|---|---|---|
| EC-1 | 既に「提出済」(10) の見積を再度印刷 | `getNotifiedStatus('10')` → `'10'`（変化なし）。API コールしない |
| EC-2 | 「修正後提出済」(30) の見積を再度印刷 | `getNotifiedStatus('30')` → `'30'`（変化なし）。API コールしない |
| EC-3 | 「価格反映済」(70) の見積を印刷 | `getNotifiedStatus('70')` → `'10'`。確認ダイアログで「提出済に更新されます」と表示 |
| EC-4 | 「削除」(50) の見積を印刷 | 詳細画面に印刷ボタンは表示されるが、`getNotifiedStatus('50')` → `'10'` でステータスが変わる。問題ないが、要確認 |
| EC-5 | ステータス更新 API が失敗した場合 | 印刷/PDF は完了している。トーストでエラー通知のみ。手動で Select から変更可能 |
| EC-6 | confirm でキャンセルした場合 | 印刷もステータス更新も実行しない |

### EC-3, EC-4 への対応方針

旧システムでも同じ挙動（`70`→`10`, `50`→`10`）だが、実運用では印刷が必要なのは `00`/`20` のケースがほとんど。安全策として:

**Option A（旧システム互換）:** 全ステータスで遷移ルール適用 ← 推奨
**Option B（制限版）:** ステータスが `00`/`20` の場合のみ自動更新。それ以外は印刷のみ実行しステータスは変えない

→ **Option A を採用**（旧システムと同じ挙動。ユーザーが混乱しない）

---

## 8. UI/UX 設計

### 確認ダイアログ

```
┌──────────────────────────────────────┐
│                                      │
│  印刷しますか？                       │
│  ステータスが「提出済」に更新されます。│
│                                      │
│              [キャンセル] [OK]         │
└──────────────────────────────────────┘
```

- 現在のステータスが `20`(修正) の場合: 「ステータスが「修正後提出済」に更新されます。」
- 現在のステータスが既に `10`/`30` の場合: ダイアログなし（ステータス変化がないため直接印刷）

### PDFダウンロード時

```
┌──────────────────────────────────────────┐
│                                          │
│  PDFをダウンロードしますか？              │
│  ステータスが「提出済」に更新されます。    │
│                                          │
│              [キャンセル] [OK]             │
└──────────────────────────────────────────┘
```

### トースト通知

- 成功: `ステータスを「提出済」に更新しました`
- 失敗: `ステータスの更新に失敗しました`
- ステータス変化なし: トースト表示なし

---

## 9. テスト計画

| # | テストケース | 期待結果 |
|---|---|---|
| T-1 | ステータス 00 の見積で印刷ボタンクリック → OK | 印刷実行 + ステータスが 10 に更新 |
| T-2 | ステータス 20 の見積で印刷ボタンクリック → OK | 印刷実行 + ステータスが 30 に更新 |
| T-3 | ステータス 10 の見積で印刷ボタンクリック | 確認ダイアログなし + 印刷のみ実行 |
| T-4 | ステータス 00 の見積で PDF ボタンクリック → OK | PDF ダウンロード + ステータスが 10 に更新 |
| T-5 | 印刷ボタンクリック → キャンセル | 何も実行されない |
| T-6 | ステータス更新 API が 500 を返した場合 | 印刷は完了、エラートースト表示 |
| T-7 | 比較見積ステータス 00 で印刷 → OK | 印刷実行 + ステータスが 10 に更新 |
| T-8 | `getNotifiedStatus('00')` → `'10'` | ✓ |
| T-9 | `getNotifiedStatus('20')` → `'30'` | ✓ |
| T-10 | `getNotifiedStatus('30')` → `'30'` | ✓ |
| T-11 | `getNotifiedStatus('70')` → `'10'` | ✓ |

---

## 10. Rollout Plan

### Phase 1: フロントエンド実装
1. `getNotifiedStatus()` を `types/estimate.ts` に追加
2. 見積詳細画面の印刷・PDF ボタンにステータス自動更新ロジック追加
3. 比較見積詳細画面の印刷ボタンにステータス自動更新ロジック追加
4. E2E テスト追加

### Phase 2: 動作確認
- 旧システムと並行稼働中のため、同一見積で新旧の挙動を比較確認
- バッチ `PartnerPriceChangeReflectTasklet` が `priceChangeDate` 到来時に正しく `70` に更新されることを確認

### ロールバック
- フロントエンドのみの変更のため、ボタンのハンドラを元に戻すだけで即時ロールバック可能
