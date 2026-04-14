---
name: code-review
description: ブランチ全体のコードレビュー。フロントエンド・バックエンドを並行サブエージェントでレビューし、Severity付き統合レポートを出力する。Use when user says "コードレビュー", "code review", "レビューして", or after implementation is complete.
argument-hint: "[base-branch]"
metadata:
  author: odamitsu
  version: 2.0.0
---

# Code Review — ブランチ全体レビュー

## Goal
現在のブランチの変更内容を、バックエンド・フロントエンド並行でレビューし、Severity 付き統合レポートを出力する。

## Arguments
- `$ARGUMENTS`: 比較対象ブランチ（デフォルト: `main`）

## Principles

**毎回ブランチ差分全体をレビュー対象にする**（直近の変更「だけ」ではない）。過去レビュー済みと思っていた箇所でも、後続コミットで退行している可能性があるため、累積差分を必ず見る。

**モック E2E の PASS を鵜呑みにしない**。文字列・セレクタは実装と一緒にレビュー対象。実バックエンド経由の疎通確認が 1 ケースでもあれば品質が上がる。

**デバッグ用コード（`detail` フィールド、擬似 state、`TODO` コメント）を見つけたら必ず指摘**。リリース前に残ると情報漏洩・誤動作の原因。

## Method

### Step 1: 変更内容の把握

比較対象ブランチは `$ARGUMENTS`（未指定時は `main`）。

以下を並行実行して全体像を把握する:
```bash
git diff {base}...HEAD --stat
git log {base}..HEAD --oneline
```

未コミットの変更がある場合は `git diff` と `git status` も確認する。

### Step 2: 並行レビュー

Agent ツールで2つのサブエージェント（subagent_type=general-purpose）を**同時に**起動する。
各サブエージェントには以下の専門スキルを事前にロードさせ、ベストプラクティスに基づくレビューを行う。

#### バックエンドレビュー

**専門スキル参照**: `spring-boot-expert`, `spring-framework-patterns`, `java-spring-development`

プロンプトに含めること:
- `git diff {base}...HEAD -- backend/` で差分取得
- 変更された主要ファイルを Read で読み込み
- **レビュー前に** Skill ツールで `spring-boot-expert` と `spring-framework-patterns` をロードし、ベストプラクティスを把握
- レビュー観点:
  1. **バグ・ロジックエラー**: NPE、不整合、N+1クエリ、race condition
  2. **セキュリティ**: SQLインジェクション、入力検証、認可漏れ、`@PreAuthorize` の一貫性
  3. **パフォーマンス**: 不要なDB呼び出し、インデックス漏れ、一括処理の欠如、Lazy/Eager 戦略
  4. **設計・アーキテクチャ**: レイヤー違反（Controller に業務ロジック）、SOLID原則、コード重複
  5. **Spring Boot ベストプラクティス**:
     - `@Transactional` の正確性（読み取り専用、伝搬レベル、private メソッドへの誤用）
     - `@Valid` + Bean Validation の一貫性
     - DTOパターン（Entity を直接返していないか）
     - 例外処理（`@ControllerAdvice` / `GlobalExceptionHandler` との整合性）
     - Repository メソッド命名規約
  6. **Spring Security**: `@PreAuthorize` がクラス/メソッドレベルで適切か、shopNo アクセス制御の一貫性
  7. **JPA/Hibernate**: `@OneToMany` の fetch 戦略、`@Where` vs getter フィルタ、orphanRemoval
  8. **Spring Batch**: Job/Step の Bean 名重複、`@StepScope` の適切性、チャンクサイズ
- 各指摘: Severity（🔴 Critical / 🟡 Warning / 🔵 Info）+ ファイル:行番号 + 修正案
- 日本語 Markdown 出力

#### フロントエンドレビュー

**専門スキル参照**: `typescript-react-reviewer`, `vercel-react-best-practices`

プロンプトに含めること:
- `git diff {base}...HEAD -- frontend/` で差分取得
- 変更された主要ファイルを Read で読み込み
- **レビュー前に** Skill ツールで `typescript-react-reviewer` をロードし、ベストプラクティスを把握
- レビュー観点:
  1. **バグ・ロジックエラー**: state管理ミス、race condition、古いクロージャ（stale closure）
  2. **セキュリティ**: XSS（dangerouslySetInnerHTML）、認証情報の直接アクセス
  3. **パフォーマンス（React 19 + Next.js 16）**:
     - `useMemo`/`useCallback` の過剰使用 or 不足
     - 不要な再レンダー（props の参照安定性）
     - 関数型更新パターン（`setState(prev => ...)` vs `setState(newValue)`）
     - TanStack Query のキャッシュ戦略（`staleTime`、`queryKey` の一貫性）
  4. **型安全性**:
     - `any` / `unknown` 使用
     - `as` 型アサーション（型注釈で回避できないか）
     - API レスポンス型とバックエンド DTO の整合性
     - `null` / `undefined` の扱い（`??` vs `||`、optional chaining の過剰使用）
  5. **React ベストプラクティス**:
     - hooks 規則（条件分岐内の hooks 呼び出し）
     - `key` props（配列レンダリング時の安定したキー）
     - `useEffect` 依存配列の正確性（ESLint exhaustive-deps 相当）
     - エラーバウンダリ、ローディング状態、空状態の網羅
  6. **Next.js 16 固有**:
     - `use(params)` パターン（async params）
     - `'use client'` ディレクティブの適切性
     - Server/Client Component の境界
  7. **shadcn/ui パターン**:
     - コンポーネントの一貫した使用（`Select` / `SearchableSelect` / `Dialog` 等）
     - `clearable` / `disabled` の props 一貫性
  8. **E2E テスト品質**:
     - `waitForTimeout` の使用（flaky テストのリスク）
     - セレクタの安定性（`data-testid` vs テキストマッチ）
     - モックの網羅性
- 各指摘: Severity（🔴 Critical / 🟡 Warning / 🔵 Info）+ ファイル:行番号 + 修正案
- 日本語 Markdown 出力

### Step 3: 統合レポート

2つのサブエージェント結果を以下の形式で統合して**ユーザーに直接提示**する:

```
# コードレビュー報告書

**ブランチ:** {branch} vs {base}
**変更規模:** {files}ファイル、+{add}行 / -{del}行

## バックエンド
### 🔴 Critical（n件）
| # | ファイル | 問題 |
### 🟡 Warning（n件）
| # | カテゴリ | ファイル | 問題 |

## フロントエンド
### 🔴 Critical（n件）
### 🟡 Warning（n件）

## 優先対応リスト
1. ...（データ不整合リスク大）
2. ...
```

### Step 4: 修正実行（ユーザー確認後）

ユーザーが「対応してください」と指示した場合のみ:
1. 優先度順にタスクを作成（TaskCreate）
2. 順番に修正を実施
3. ビルド確認: `./gradlew compileJava` + `npx tsc --noEmit`

## レビュー対象外
- `claudedocs/` 配下のドキュメント
- `data/` 配下のデータファイル
- `.claude/` 配下の設定ファイル
- テストファイルは「テスト品質」の観点でレビュー（本体コードと同等の品質を求めない）

## Examples

**User says:** "コードレビューして"
→ `main` との差分を並行レビュー、統合レポート出力

**User says:** "/code-review develop"
→ `develop` ブランチとの差分を並行レビュー
