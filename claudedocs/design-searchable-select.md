# 設計書: 仕入先・メーカープルダウンの検索対応（Select2化）

## 1. Background / Problem

### 課題
販売商品ワーク/マスタの「仕入先」プルダウンと、商品マスタの「メーカー」プルダウンは、標準の`Select`コンポーネント（Radix UI）を使用しており、テキスト検索機能がない。仕入先・メーカーの件数が増えると目的の選択肢を探すのが困難になる。

### 解決策
旧システム（stock-app）で使用していたSelect2相当の検索可能プルダウン（Combobox）をshadcn/uiベースで実装し、該当箇所をすべて置き換える。

## 2. Requirements

### Functional Requirements
- FR-1: テキスト入力で選択肢をフィルタリングできる
- FR-2: 選択済みの値をクリア（未選択に戻す）できる（検索フォームの場合）
- FR-3: 選択肢は名称のみ表示する（現行と同一。例: `山田商店`）。options生成は呼び出し側で制御可能
- FR-4: 選択済み項目にチェックマークを表示する
- FR-5: プルダウン開閉時にフォーカス管理が適切に動作する
- FR-6: disabled状態に対応する（商品マスタ登録の2段階目ロック等）

### Non-functional Requirements
- NFR-1: 既存のSelect利用箇所のAPI（value/onValueChange）と互換性のあるインターフェース
- NFR-2: shadcn/uiのデザイントークンに準拠した外観
- NFR-3: キーボード操作対応（矢印キー、Enter、Escape）

## 3. Constraints

- **技術制約**: shadcn/uiのComboboxパターン（Popover + Command）を採用。`cmdk`パッケージの追加が必要
- **UIの一貫性**: Select（検索不要な項目）とSearchableSelect（検索必要な項目）を使い分ける

## 4. Proposed Solution

### アーキテクチャ

```
新規追加コンポーネント:
  components/ui/popover.tsx    ← shadcn/ui Popover（Radix UI Popover）
  components/ui/command.tsx    ← shadcn/ui Command（cmdk ラッパー）
  components/features/common/SearchableSelect.tsx  ← 再利用可能な検索Selectコンポーネント
```

### SearchableSelect コンポーネント設計

```tsx
interface SearchableSelectProps {
  value: string                          // 選択値（文字列）。"" = 未選択
  onValueChange: (value: string) => void // 値変更コールバック。クリア時は "" を渡す
  options: { value: string; label: string }[] // 選択肢
  placeholder?: string                   // 未選択時のプレースホルダー
  searchPlaceholder?: string             // 検索入力のプレースホルダー
  emptyMessage?: string                  // 検索結果0件時のメッセージ
  disabled?: boolean                     // 無効状態
  clearable?: boolean                    // クリア可能（デフォルト: true）
}
```

**値の規約**: 現行の Radix Select と同じく `""` = 未選択。`clearable=true` の場合、✕ボタンで `onValueChange("")` を呼ぶ。これにより既存の `useState<string>('')` をそのまま利用可能。

### 変更対象ファイル一覧（9箇所 / 7ファイル）

| # | ファイル | フィールド | 種別 |
|---|---------|-----------|------|
| 1 | `sales-goods/create.tsx` | メーカー（検索フォーム） | clearable |
| 2 | `sales-goods/create.tsx` | 仕入先（登録フォーム） | required |
| 3 | `sales-goods/detail.tsx` | 仕入先（編集フォーム） | required |
| 4 | `sales-goods/work-list.tsx` | 仕入先（検索フォーム） | clearable |
| 5 | `sales-goods/master-list.tsx` | 仕入先（検索フォーム） | clearable |
| 6 | `goods/index.tsx` | メーカー（検索フォーム） | clearable |
| 7 | `goods/detail.tsx` | メーカー（編集フォーム） | optional |
| 8 | `goods/create.tsx` | メーカー（登録フォーム） | optional |
| 9 | `goods/create.tsx` | 仕入先（登録フォーム） | required |

## 5. Data Model / DB Changes

なし。バックエンドAPI変更なし。

## 6. API / UI Changes

### API変更
なし。既存の `/masters/makers` と `/masters/suppliers` をそのまま利用。

### UI変更

**Before（現状）:**
```
[選択してください    ▼]  ← クリックでドロップダウン表示、検索不可
```

**After（変更後）:**
```
[選択してください    ▼]  ← クリックでPopover表示
┌──────────────────────┐
│ 🔍 仕入先を検索...    │  ← テキスト入力でフィルタ
├──────────────────────┤
│ ✓ 101 - 山田商店      │
│   102 - 鈴木物産       │
│   103 - 佐藤食品       │
│   ...                  │
└──────────────────────┘
```

**clearable=true の場合（検索フォーム）:**
```
[山田商店          ✕ ▼]  ← ✕ で選択クリア
```

## 7. Edge Cases

- **選択肢0件**: emptyMessage を表示（デフォルト: 「見つかりません」）
- **初期値が選択肢に存在しない**: 値はそのまま保持、表示はプレースホルダー
- **フィルタで0件になった場合**: emptyMessage を表示
- **長い名称**: テキストは省略表示（text-overflow: ellipsis）
- **TabsContent内のPopover**: `collisionPadding` でポジショニング調整。cmdk + Radix Popover が ARIA 属性を自動付与
- **データ件数**: 仕入先・メーカーとも数十〜数百件想定。仮想化は不要（cmdk はDOM直接レンダリング）

## 8. Risks and Mitigations

| リスク | 影響 | 対策 |
|-------|------|------|
| `cmdk`パッケージ追加 | バンドルサイズ増 | cmdk は軽量（~5KB gzip）、影響小 |
| Popover のz-index衝突 | Dialog内で使用時に重なり問題 | shadcn/ui標準の z-50 で対応 |
| IME入力との相性 | 日本語入力中にフィルタが効かない | cmdk は compositionEnd でフィルタ（対応済み） |

## 9. Rollout Plan

フロントエンドのみの変更。バックエンド変更なし。
1. `cmdk` パッケージ追加
2. `popover.tsx`, `command.tsx` をshadcn/uiから追加
3. `SearchableSelect.tsx` 共通コンポーネント作成
4. 9箇所のSelect → SearchableSelect 置き換え
5. E2Eテストのセレクタ更新（必要に応じて）
