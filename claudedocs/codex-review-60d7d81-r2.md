OpenAI Codex v0.128.0 (research preview)
--------
workdir: C:\project\odamitsu-data-hub
model: gpt-5.5
provider: openai
approval: never
sandbox: read-only
reasoning effort: none
reasoning summaries: none
session id: 019dffcf-977f-7092-839f-2ab10ec3ac19
--------
user
commit 60d7d81
2026-05-07T00:21:36.810856Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content C:/Users/kazuki/.agents/skills/using-superpowers/SKILL.md -TotalCount 120; git rev-parse --show-toplevel; git status --short; git show --stat --oneline --decorate --no-renames 60d7d81'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content C:/Users/kazuki/.agents/skills/using-superpowers/SKILL.md -TotalCount 120; git rev-parse --show-toplevel; git status --short; git show --stat --oneline --decorate --no-renames 60d7d81' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content C:/Users/kazuki/.agents/skills/using-superpowers/SKILL.md -TotalCount 120; git rev-parse --show-toplevel; git status --short; git show --stat --oneline --decorate --no-renames 60d7d81'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -LiteralPath 'C:\\Users\\kazuki\\.agents\\skills\\using-superpowers\\SKILL.md' -TotalCount 80" in C:\project\odamitsu-data-hub
 succeeded in 1886ms:
---
name: using-superpowers
description: Use when starting any conversation - establishes how to find and use skills, requiring Skill tool invocation before ANY response including clarifying questions
---

<SUBAGENT-STOP>
If you were dispatched as a subagent to execute a specific task, skip this skill.
</SUBAGENT-STOP>

<EXTREMELY-IMPORTANT>
If you think there is even a 1% chance a skill might apply to what you are doing, you ABSOLUTELY MUST invoke the skill.

IF A SKILL APPLIES TO YOUR TASK, YOU DO NOT HAVE A CHOICE. YOU MUST USE IT.

This is not negotiable. This is not optional. You cannot rationalize your way out of this.
</EXTREMELY-IMPORTANT>

## Instruction Priority

Superpowers skills override default system prompt behavior, but **user instructions always take precedence**:

1. **User's explicit instructions** (CLAUDE.md, GEMINI.md, AGENTS.md, direct requests) ? highest priority
2. **Superpowers skills** ? override default system behavior where they conflict
3. **Default system prompt** ? lowest priority

If CLAUDE.md, GEMINI.md, or AGENTS.md says "don't use TDD" and a skill says "always use TDD," follow the user's instructions. The user is in control.

## How to Access Skills

**In Claude Code:** Use the `Skill` tool. When you invoke a skill, its content is loaded and presented to you?follow it directly. Never use the Read tool on skill files.

**In Copilot CLI:** Use the `skill` tool. Skills are auto-discovered from installed plugins. The `skill` tool works the same as Claude Code's `Skill` tool.

**In Gemini CLI:** Skills activate via the `activate_skill` tool. Gemini loads skill metadata at session start and activates the full content on demand.

**In other environments:** Check your platform's documentation for how skills are loaded.

## Platform Adaptation

Skills use Claude Code tool names. Non-CC platforms: see `references/copilot-tools.md` (Copilot CLI), `references/codex-tools.md` (Codex) for tool equivalents. Gemini CLI users get the tool mapping loaded automatically via GEMINI.md.

# Using Skills

## The Rule

**Invoke relevant or requested skills BEFORE any response or action.** Even a 1% chance a skill might apply means that you should invoke the skill to check. If an invoked skill turns out to be wrong for the situation, you don't need to use it.

```dot
digraph skill_flow {
    "User message received" [shape=doublecircle];
    "About to EnterPlanMode?" [shape=doublecircle];
    "Already brainstormed?" [shape=diamond];
    "Invoke brainstorming skill" [shape=box];
    "Might any skill apply?" [shape=diamond];
    "Invoke Skill tool" [shape=box];
    "Announce: 'Using [skill] to [purpose]'" [shape=box];
    "Has checklist?" [shape=diamond];
    "Create TodoWrite todo per item" [shape=box];
    "Follow skill exactly" [shape=box];
    "Respond (including clarifications)" [shape=doublecircle];

    "About to EnterPlanMode?" -> "Already brainstormed?";
    "Already brainstormed?" -> "Invoke brainstorming skill" [label="no"];
    "Already brainstormed?" -> "Might any skill apply?" [label="yes"];
    "Invoke brainstorming skill" -> "Might any skill apply?";

    "User message received" -> "Might any skill apply?";
    "Might any skill apply?" -> "Invoke Skill tool" [label="yes, even 1%"];
    "Might any skill apply?" -> "Respond (including clarifications)" [label="definitely not"];
    "Invoke Skill tool" -> "Announce: 'Using [skill] to [purpose]'";
    "Announce: 'Using [skill] to [purpose]'" -> "Has checklist?";
    "Has checklist?" -> "Create TodoWrite todo per item" [label="yes"];
    "Has checklist?" -> "Follow skill exactly" [label="no"];
    "Create TodoWrite todo per item" -> "Follow skill exactly";
}
```

## Red Flags

These thoughts mean STOP?you're rationalizing:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

2026-05-07T00:21:45.622921Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git rev-parse --show-toplevel'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git rev-parse --show-toplevel' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git rev-parse --show-toplevel'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Force' in C:\project\odamitsu-data-hub
 succeeded in 1556ms:

    Directory: C:\project\odamitsu-data-hub

Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
d----          2026/05/07     9:10                .claude
d--h-          2026/05/07     9:18                .git
d----          2026/05/06    19:35                .github
d----          2026/04/21    19:52                .idea
d----          2026/05/07     9:00                backend
d----          2026/04/16    17:55                backup
d----          2026/05/07     9:21                claudedocs
d----          2026/03/31    19:46                C?projectodamitsu-data-hubclaudedocs
d----          2026/04/01     2:26                data
d----          2026/05/06    16:44                docs
d----          2026/05/01    16:31                frontend
d----          2026/02/24    18:31                frontend-vite-backup
d----          2026/04/30    15:20                SQL
d----          2026/04/13    18:17                test-results
-a---          2026/04/23    11:27           1591 _query_residual.sql
-a---          2026/04/21     8:55           1301 .env.local
-a---          2026/05/06    19:52           1072 .gitignore
-a---          2026/05/07     9:13           6395 CLAUDE.md
-a---          2026/05/06    19:36            953 README.md

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git status --short' in C:\project\odamitsu-data-hub
 succeeded in 1315ms:
 M .claude/settings.local.json
?? claudedocs/codex-review-60d7d81-r2.md
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
warning: unable to access 'C:\Users\kazuki/.config/git/ignore': Permission denied
warning: unable to access 'C:\Users\kazuki/.config/git/ignore': Permission denied

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -LiteralPath 'CLAUDE.md'" in C:\project\odamitsu-data-hub
 succeeded in 1770ms:
# 小田光データ連携基盤 (OdaMitsu Data Hub)

## 技術スタック
- **バックエンド**: Java 21, Spring Boot 3.3.x, Spring Batch 5.x, PostgreSQL 17, Gradle 8.x
- **フロントエンド**: Next.js 16, React 19, TypeScript strict, shadcn/ui, TanStack Query
- **主要連携**: SMILE（基幹）, B-CART（EC）, マネーフォワード（会計）
- **旧システム**: stock-app（Spring Boot 2.1.1 / C:\project\stock-app）? 並行運用中

## 開発コマンド
```bash
# バックエンド
cd backend && ./gradlew bootRun --args='--spring.profiles.active=web,dev'
cd backend && ./gradlew test

# フロントエンド
cd frontend && npm run dev     # localhost:3000
cd frontend && npx tsc --noEmit
cd frontend && npx playwright test

# バッチ（IntelliJ: BatchApplication, args: --spring.profiles.active=batch,dev --spring.batch.job.name=<job> shopNo=1）
```

## プロファイル
- `web` + `dev`: APIサーバー開発（port 8090）
- `batch` + `dev`: バッチ実行
- Next.js が `/api/*` → `localhost:8090/api/*` にリライト

## 重要な実装パターン

### バックエンド
- **Controller は薄く**: ビジネスロジックは Service 層に委譲
- **DTO分離**: Entity を直接返さない。`Response.from(entity)` ファクトリメソッド
- **バリデーション**: `@Valid` + Jakarta Bean Validation
- **論理削除**: `del_flg`（'0'=有効, '1'=削除）、`IEntity` インターフェース
- **CustomService**: `insert()`/`update()`/`delete()` で共通処理（監査フィールド、ショップ権限チェック）
- **バッチ**: `@EnableBatchProcessing` 不使用。Bean名 = ジョブ名 + "Job"。`@Value("#{jobParameters['shopNo']}")` でshopNo取得

### フロントエンド
- **ページ構成**: `app/(authenticated)/xxx/page.tsx` → `components/pages/xxx.tsx`
- **初期検索なし**: `searchParams` を `null` で初期化、`enabled: searchParams !== null`
- **admin判定**: `user.shopNo === 0` → ショップ選択を表示
- **検索プルダウン**: `SearchableSelect`（Popover + Command/cmdk）? `clearable` で必須/任意を切替
- **トースト**: sonner
- **shadcn/ui追加**: `npx shadcn@latest add <component>`

## エンティティ設計
- 複合主キー: `@Embeddable` クラス
- 共通フィールド: `company_no`, `shop_no`, `del_flg`, `add_date_time`, `add_user_no`, `modify_date_time`, `modify_user_no`
- ワークテーブル `w_*` → 本テーブル `t_*` / `m_*`

## 開発プロセス
- **増分レビュー必須**: 機能追加・バグ修正・デバッグコード投入のたびに `tsc --noEmit` + `./gradlew compileJava` + E2E (モック PASS だけでなく実バックエンド疎通を最低 1 パス) を実施。`/code-review` は常にブランチ差分全体を対象
- **デバッグ用コードはマーキング**: `detail` レスポンス、擬似 state、`null as unknown as X` などは投入時に TaskCreate で整理対象に登録、マージ前に再確認
- **JVM 再起動が必要な変更**（新 Bean / Repository method / @JsonProperty / Converter 等）を入れたらユーザーに再起動依頼を明示し、curl で疎通確認してから UI で検証

## 注意事項
- `javax.*` ではなく `jakarta.*`
- Spring Batch メタデータテーブルは 5.x スキーマ（`parameter_name` / `create_time`）
- PostgreSQL固有型: `hypersistence-utils-hibernate-63` で対応
- CORS: `localhost:5173,localhost:3000` を許可（dev）

# Review Guidelines

このリポジトリでは、AIレビュー時に以下を優先する。

## Repository Context

- 特定の技術スタックを固定前提にしない。
- 実際の言語、フレームワーク、DB、ライブラリ、実行環境は、リポジトリ内の設定ファイル、依存関係、ディレクトリ構成、既存コードから判断する。
- 不明な技術・仕様・業務ルールは断定せず、Open Questions として扱う。
- 検出した技術のバージョンで使えない機能を安易に提案しない。

## Review Priorities

1. データ破壊・データ不整合
2. 認証・認可の欠落
3. Injection / XSS / CSRF / 情報漏洩などのセキュリティリスク
4. 業務仕様・ドメインルールへの影響
5. トランザクション・排他制御・永続化処理
6. テスト不足
7. 保守性・可読性
8. パフォーマンス
9. UI / UX
10. 命名・軽微なスタイル

## Architecture Rules

- 画面、API、業務ロジック、永続化、外部連携の責務を混ぜすぎない。
- 業務ルールは追跡しやすい場所に置く。
- DB更新や外部連携ではトランザクション境界・失敗時の挙動を明確にする。
- 画面表示用のデータ構造と永続化用のデータ構造を不用意に混ぜない。
- フロントエンドでは、画面状態、APIレスポンス、フォーム入力の扱いを曖昧にしない。
- 検出した技術スタックに対して自然な設計を優先する。
- 特定の技術・バージョンで使えない機能を前提にしない。

## Severity

- P0: リリース不可。データ破壊、重大なセキュリティ、致命的障害、業務停止。
- P1: 修正必須。業務影響の大きいバグ、認証認可ミス、重大な回帰。
- P2: 修正推奨。保守性、テスト不足、性能劣化リスク。
- P3: 提案。軽微な可読性、命名、将来改善。

## Review Do Not

- 根拠のない推測を断定しない。
- 好みの問題を P1 以上にしない。
- 大量の細かい指摘で重大な問題を埋もれさせない。
- 既存仕様を無視して理想論だけで指摘しない。
- 存在しない技術スタックを前提にしない。
- 検出したバージョンで使えない機能を提案しない。
- AIレビューを最終判断にしない。最終判断は人間が行う。

## Done Means

- 主要なテストが通る。
- 変更理由が説明できる。
- 業務影響範囲が明確。
- エラー時の挙動が確認されている。
- DB変更がある場合、移行・ロールバック方針がある。
- 外部連携がある場合、失敗時・再試行・タイムアウトの扱いが明確。
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git show --stat --oneline --decorate --no-renames 60d7d81' in C:\project\odamitsu-data-hub
 succeeded in 1285ms:
60d7d81 (HEAD -> refactor/code-review-fixes) fix(codex-r2): Codex 第 2 弾批判レビュー対応 (Critical + P1 + 9 Major + 1 Minor)
 .claude/agents/architecture-reviewer.md            |    53 +
 .claude/agents/backend-reviewer.md                 |    52 +
 .claude/agents/database-reviewer.md                |    59 +
 .claude/agents/devils-advocate-reviewer.md         |    68 +
 .claude/agents/frontend-reviewer.md                |    52 +
 .claude/agents/maintainability-reviewer.md         |    45 +
 .claude/agents/meta-reviewer.md                    |    81 +
 .claude/agents/security-reviewer.md                |    46 +
 .claude/agents/test-reviewer.md                    |    49 +
 .claude/skills/app-review/SKILL.md                 |   253 +
 CLAUDE.md                                          |    60 +
 .../V033__reencrypt_mf_oauth_secrets.java          |   156 +-
 .../api/finance/PaymentMfImportController.java     |     8 +-
 .../java/jp/co/oda32/audit/FinanceAuditAspect.java |    23 +-
 .../java/jp/co/oda32/audit/FinanceAuditWriter.java |    16 +
 .../jp/co/oda32/constant/FinanceConstants.java     |     8 +
 .../oda32/domain/model/audit/FinanceAuditLog.java  |    11 +
 .../finance/MOffsetJournalRuleRepository.java      |     6 +
 .../finance/AccountsPayableIntegrityService.java   |    18 +
 .../service/finance/ConsistencyReviewService.java  |    20 +
 .../domain/service/finance/FinancePayableLock.java |    61 +
 .../service/finance/MOffsetJournalRuleService.java |    15 +
 .../service/finance/PaymentMfExcelParser.java      |    44 +-
 .../service/finance/PaymentMfImportService.java    |   184 +-
 .../service/finance/SupplierBalancesService.java   |    26 +
 .../finance/TAccountsPayableSummaryService.java    |    15 +
 .../finance/mf/MfOpeningBalanceService.java        |    13 +
 .../oda32/dto/finance/IntegrityReportResponse.java |    10 +
 .../dto/finance/SupplierBalancesResponse.java      |    10 +
 .../finance/paymentmf/PaymentMfApplyRequest.java   |    13 +
 .../migration/V043__add_force_mismatch_details.sql |    30 +
 .../db/migration/V044__verify_v040_backfill.sql    |    68 +
 .../java/db/migration/V033ReencryptionTest.java    |   291 +
 .../jp/co/oda32/audit/FinanceAuditAspectTest.java  |   107 +
 .../finance/MOffsetJournalRuleServiceTest.java     |    33 +
 .../finance/PaymentMfExcelParserSectionTest.java   |    77 +-
 .../PaymentMfImportServiceForceApplyTest.java      |    87 +-
 .../PaymentMfImportServiceOffsetFallbackTest.java  |   109 +
 .../PaymentMfImportServiceVerifiedAmountTest.java  |    36 +-
 .../finance/SupplierBalancesServiceGoldenTest.java |    57 +
 ...02\241\343\202\244\343\203\253_20260220_v3.csv" |    84 +-
 claudedocs/codex-review-1b812b7-mf.md              |  8898 +++++++++
 claudedocs/codex-review-65e80f6-audit.md           | 19603 +++++++++++++++++++
 claudedocs/codex-review-a0bbcac-finance.md         |   108 +
 claudedocs/design-finance-audit-log.md             |    68 +
 .../runbook-finance-recalc-impact-analysis.md      |    61 +
 claudedocs/runbook-mf-oauth-keys.md                |    30 +
 .../runbook-payment-mf-bulk-invariant-violation.md |    96 +
 claudedocs/runbook-payment-mf-force-apply.md       |   129 +
 .../components/pages/finance/payment-mf-import.tsx |   210 +-
 50 files changed, 31490 insertions(+), 167 deletions(-)
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -LiteralPath 'C:\\Users\\kazuki\\.agents\\skills\\spring-framework-patterns\\SKILL.md' -TotalCount 80; Get-Content -LiteralPath 'C:\\Users\\kazuki\\.agents\\skills\\typescript-react-reviewer\\SKILL.md' -TotalCount 80" in C:\project\odamitsu-data-hub
 succeeded in 1838ms:
---
name: spring-framework-patterns
description: Comprehensive Spring Framework and Spring Boot best practices including dependency injection patterns, bean lifecycle and scopes, REST API development, Spring Data JPA, service layer design, Spring Security, testing strategies, caching, AOP, async processing, error handling, and common anti-patterns. Essential reference for code reviews and Spring Boot application development.
allowed-tools: Read, Write, Grep, Glob
---

# Spring Framework Patterns

## Purpose

This skill provides comprehensive patterns and best practices for Spring Framework and Spring Boot development. It serves as a reference guide during code reviews to ensure Spring applications follow industry standards, are maintainable, scalable, and adhere to enterprise Java conventions.

**When to use this skill:**
- Conducting code reviews of Spring/Spring Boot applications
- Designing Spring Boot application architecture
- Writing new Spring components (controllers, services, repositories)
- Refactoring existing Spring applications
- Evaluating Spring configuration and setup
- Teaching Spring best practices to team members

## Context

Spring Framework is the de facto standard for enterprise Java applications. This skill documents production-ready patterns using Spring Boot 3.x+ and Spring 6.x+, emphasizing:

- **Modularity**: Clear separation of concerns with proper layering
- **Maintainability**: Code that's easy to understand and modify
- **Testability**: Components that can be easily tested
- **Performance**: Efficient use of Spring features
- **Security**: Secure-by-default patterns
- **Convention over Configuration**: Leveraging Spring Boot auto-configuration

This skill is designed to be referenced by the `uncle-duke-java` agent during code reviews and by developers when implementing Spring applications.

## Prerequisites

**Required Knowledge:**
- Java fundamentals (Java 17+)
- Object-oriented programming concepts
- Basic understanding of Spring concepts
- Maven or Gradle basics

**Required Tools:**
- JDK 17 or later
- Spring Boot 3.x+
- Maven or Gradle
- IDE (IntelliJ IDEA, Eclipse, VS Code)

**Expected Project Structure:**
```
spring-boot-app/
ДеДЯДЯ src/
Да   ДеДЯДЯ main/
Да   Да   ДеДЯДЯ java/
Да   Да   Да   ДдДЯДЯ com/example/app/
Да   Да   Да       ДеДЯДЯ Application.java
Да   Да   Да       ДеДЯДЯ config/
Да   Да   Да       ДеДЯДЯ controller/
Да   Да   Да       ДеДЯДЯ service/
Да   Да   Да       ДеДЯДЯ repository/
Да   Да   Да       ДеДЯДЯ model/
Да   Да   Да       ДеДЯДЯ dto/
Да   Да   Да       ДеДЯДЯ exception/
Да   Да   Да       ДдДЯДЯ security/
Да   Да   ДдДЯДЯ resources/
Да   Да       ДеДЯДЯ application.yml
Да   Да       ДеДЯДЯ application-dev.yml
Да   Да       ДеДЯДЯ application-prod.yml
Да   Да       ДдДЯДЯ db/migration/
Да   ДдДЯДЯ test/
Да       ДдДЯДЯ java/
ДеДЯДЯ pom.xml (or build.gradle)
ДдДЯДЯ README.md
```

---

## Instructions

### Task 1: Implement Dependency Injection Best Practices

---
name: typescript-react-reviewer
description: "Expert code reviewer for TypeScript + React 19 applications. Use when reviewing React code, identifying anti-patterns, evaluating state management, or assessing code maintainability. Triggers: code review requests, PR reviews, React architecture evaluation, identifying code smells, TypeScript type safety checks, useEffect abuse detection, state management review."
---

# TypeScript + React 19 Code Review Expert

Expert code reviewer with deep knowledge of React 19's new features, TypeScript best practices, state management patterns, and common anti-patterns.

## Review Priority Levels

### ?? Critical (Block Merge)

These issues cause bugs, memory leaks, or architectural problems:

| Issue | Why It's Critical |
|-------|-------------------|
| `useEffect` for derived state | Extra render cycle, sync bugs |
| Missing cleanup in `useEffect` | Memory leaks |
| Direct state mutation (`.push()`, `.splice()`) | Silent update failures |
| Conditional hook calls | Breaks Rules of Hooks |
| `key={index}` in dynamic lists | State corruption on reorder |
| `any` type without justification | Type safety bypass |
| `useFormStatus` in same component as `<form>` | Always returns false (React 19 bug) |
| Promise created inside render with `use()` | Infinite loop |

### ?? High Priority

| Issue | Impact |
|-------|--------|
| Incomplete dependency arrays | Stale closures, missing updates |
| Props typed as `any` | Runtime errors |
| Unjustified `useMemo`/`useCallback` | Unnecessary complexity |
| Missing Error Boundaries | Poor error UX |
| Controlled input initialized with `undefined` | React warning |

### ?? Architecture/Style

| Issue | Recommendation |
|-------|----------------|
| Component > 300 lines | Split into smaller components |
| Prop drilling > 2-3 levels | Use composition or context |
| State far from usage | Colocate state |
| Custom hooks without `use` prefix | Follow naming convention |

## Quick Detection Patterns

### useEffect Abuse (Most Common Anti-Pattern)

```typescript
// ? WRONG: Derived state in useEffect
const [firstName, setFirstName] = useState('');
const [fullName, setFullName] = useState('');
useEffect(() => {
  setFullName(firstName + ' ' + lastName);
}, [firstName, lastName]);

// ? CORRECT: Compute during render
const fullName = firstName + ' ' + lastName;
```

```typescript
// ? WRONG: Event logic in useEffect
useEffect(() => {
  if (product.isInCart) showNotification('Added!');
}, [product]);

// ? CORRECT: Logic in event handler
function handleAddToCart() {
  addToCart(product);
  showNotification('Added!');
}
```

### React 19 Hook Mistakes

```typescript
// ? WRONG: useFormStatus in form component (always returns false)
function Form() {
  const { pending } = useFormStatus();
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "git show --name-only --format='' 60d7d81" in C:\project\odamitsu-data-hub
 succeeded in 1451ms:
.claude/agents/architecture-reviewer.md
.claude/agents/backend-reviewer.md
.claude/agents/database-reviewer.md
.claude/agents/devils-advocate-reviewer.md
.claude/agents/frontend-reviewer.md
.claude/agents/maintainability-reviewer.md
.claude/agents/meta-reviewer.md
.claude/agents/security-reviewer.md
.claude/agents/test-reviewer.md
.claude/skills/app-review/SKILL.md
CLAUDE.md
backend/src/main/java/db/migration/V033__reencrypt_mf_oauth_secrets.java
backend/src/main/java/jp/co/oda32/api/finance/PaymentMfImportController.java
backend/src/main/java/jp/co/oda32/audit/FinanceAuditAspect.java
backend/src/main/java/jp/co/oda32/audit/FinanceAuditWriter.java
backend/src/main/java/jp/co/oda32/constant/FinanceConstants.java
backend/src/main/java/jp/co/oda32/domain/model/audit/FinanceAuditLog.java
backend/src/main/java/jp/co/oda32/domain/repository/finance/MOffsetJournalRuleRepository.java
backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java
backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java
backend/src/main/java/jp/co/oda32/domain/service/finance/FinancePayableLock.java
backend/src/main/java/jp/co/oda32/domain/service/finance/MOffsetJournalRuleService.java
backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParser.java
backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java
backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java
backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java
backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java
backend/src/main/java/jp/co/oda32/dto/finance/IntegrityReportResponse.java
backend/src/main/java/jp/co/oda32/dto/finance/SupplierBalancesResponse.java
backend/src/main/java/jp/co/oda32/dto/finance/paymentmf/PaymentMfApplyRequest.java
backend/src/main/resources/db/migration/V043__add_force_mismatch_details.sql
backend/src/main/resources/db/migration/V044__verify_v040_backfill.sql
backend/src/test/java/db/migration/V033ReencryptionTest.java
backend/src/test/java/jp/co/oda32/audit/FinanceAuditAspectTest.java
backend/src/test/java/jp/co/oda32/domain/service/finance/MOffsetJournalRuleServiceTest.java
backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParserSectionTest.java
backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfImportServiceForceApplyTest.java
backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfImportServiceOffsetFallbackTest.java
backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfImportServiceVerifiedAmountTest.java
backend/src/test/java/jp/co/oda32/domain/service/finance/SupplierBalancesServiceGoldenTest.java
"backend/src/test/resources/paymentmf/\350\262\267\346\216\233\344\273\225\345\205\245MF\343\202\244\343\203\263\343\203\235\343\203\274\343\203\210\343\203\225\343\202\241\343\202\244\343\203\253_20260220_v3.csv"
claudedocs/codex-review-1b812b7-mf.md
claudedocs/codex-review-65e80f6-audit.md
claudedocs/codex-review-a0bbcac-finance.md
claudedocs/design-finance-audit-log.md
claudedocs/runbook-finance-recalc-impact-analysis.md
claudedocs/runbook-mf-oauth-keys.md
claudedocs/runbook-payment-mf-bulk-invariant-violation.md
claudedocs/runbook-payment-mf-force-apply.md
frontend/components/pages/finance/payment-mf-import.tsx
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git show --no-ext-diff --unified=80 -- backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java backend/src/main/java/jp/co/oda32/domain/service/finance/FinancePayableLock.java backend/src/main/java/jp/co/oda32/api/finance/PaymentMfImportController.java backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParser.java frontend/components/pages/finance/payment-mf-import.tsx 60d7d81' in C:\project\odamitsu-data-hub
 succeeded in 1398ms:
commit 60d7d815f07409beb1375fc08c96bd6dcfc8d13a
Author: kazuki <k_oda@odamitsu.com>
Date:   Thu May 7 09:18:25 2026 +0900

    fix(codex-r2): Codex 第 2 弾批判レビュー対応 (Critical + P1 + 9 Major + 1 Minor)
    
    3 commit (audit/mf/finance) を Codex 批判レビューにかけ、12 件の指摘を修正。
    
    ## Critical / P1 (即修正)
    * Critical: BULK 不変条件違反時の SUM フォールバックを fail-closed (例外) 化
      - PaymentMfImportService.sumVerifiedAmountForGroup で全行 BULK 不一致時に
        FinanceInternalException 422、過大計上の MF CSV 流出を遮断
    * P1: V033 再暗号化が片側カラムのみで refresh_token が旧鍵残るバグ修正
      - reencryptMfTokens() で access + refresh を 1 UPDATE で同時再暗号化
      - decrypt 失敗時 skip + WARN ログで idempotent 強化 (recipherOrSkip)
    
    ## Major (9 件)
    * MF P2: V037 ordering note は runbook §6' に集約 (V037 は revert で checksum 復元)
    * Audit P2: FinanceAuditAspect で Loader 解決失敗時の captureReturn/Args フォールバック追加
    * G2-M2 Major: verification_source 3 経路 (BULK/MANUAL/MF_OVERRIDE) を共通 advisory lock で直列化
      - FinancePayableLock util 切り出し、computePayableLockKey + acquire
      - applyVerification / verify / applyMfOverride で同一 lock キー
    * G2-M3 Major: force audit 全件保存 (V043 finance_audit_log.force_mismatch_details JSONB)
      - 50 件切り詰めの reason と全件 JSON を分離、UI 全件確認可能化への土台
    * G2-M4 Major: forceReason 必須化 + reason 空 → FORCE_REASON_REQUIRED 400
    * G2-M5 Major: 20日払い専用 Excel が PAYMENT_5TH 誤分類されるバグ修正
      - PaymentMfExcelParser.determineInitialSection で送金日 day による初期 section 判定
    * G2-M5 Major: OFFSET マスタ欠落時の hardcoded default fallback + 最後の active 行削除禁止
    * G2-M6 Major: P1-02 opening 注入空時の警告 (SupplierBalances/IntegrityReport DTO)
    * Frontend Major: force apply UX 強化 (CSV download, reviewedAll checkbox, forceReason 必須)
    
    ## Minor
    * G2-M? Minor: V044 で V040 backfill 検査 (NOTICE のみ、自動修復なし)
      - 実行結果: 560 行で history 突合なし = false positive (P1-08 導入前の歴史データ)
    
    ## 新規 migration
    - V043 finance_audit_log.force_mismatch_details JSONB + GIN index
    - V044 V040 backfill 検査 (RAISE NOTICE のみ)
    
    ## 新規 runbook
    - runbook-payment-mf-bulk-invariant-violation.md (Critical fix の運用手順)
    - runbook-payment-mf-force-apply.md (force=true の二段認可運用)
    
    ## CLAUDE.md
    - Review Guidelines セクション追加 (Repository Context / Review Priorities /
      Architecture Rules) — AI レビュー時の優先順位を明示
    
    ## テスト
    - 既存 233 + 新規 ~10 件 PASS、frontend tsc PASS、リグレッション 0
    - 5 group 並列修正で衝突なし
    
    Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>

diff --git a/backend/src/main/java/jp/co/oda32/api/finance/PaymentMfImportController.java b/backend/src/main/java/jp/co/oda32/api/finance/PaymentMfImportController.java
index 9244e42..c3255df 100644
--- a/backend/src/main/java/jp/co/oda32/api/finance/PaymentMfImportController.java
+++ b/backend/src/main/java/jp/co/oda32/api/finance/PaymentMfImportController.java
@@ -34,169 +34,173 @@ import java.util.Map;
 @RequestMapping("/api/v1/finance/payment-mf")
 @PreAuthorize("isAuthenticated()")
 @RequiredArgsConstructor
 public class PaymentMfImportController {
 
     private final PaymentMfImportService importService;
     private final PaymentMfRuleService ruleService;
     private final TPaymentMfImportHistoryRepository historyRepository;
 
     // ---- インポート ----
 
     @PostMapping("/preview")
     public ResponseEntity<?> preview(@RequestParam("file") MultipartFile file) {
         try {
             PaymentMfPreviewResponse res = importService.preview(file);
             return ResponseEntity.ok(res);
         } catch (IllegalArgumentException e) {
             log.warn("買掛仕入MFプレビュー: {}", e.getMessage());
             return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
         } catch (IOException e) {
             log.error("買掛仕入MFプレビュー失敗", e);
             return ResponseEntity.internalServerError().body(Map.of("message", "ファイル解析中にエラーが発生しました"));
         }
     }
 
     @PostMapping("/preview/{uploadId}")
     public ResponseEntity<?> rePreview(@PathVariable String uploadId) {
         try {
             return ResponseEntity.ok(importService.rePreview(uploadId));
         } catch (IllegalArgumentException e) {
             return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
         }
     }
 
     @PreAuthorize("@loginUserSecurityBean.isAdmin()")
     @PostMapping("/convert/{uploadId}")
     public ResponseEntity<?> convert(@PathVariable String uploadId,
                                      @AuthenticationPrincipal LoginUser user) {
         try {
             // SF-C20: ファイル名は payment_mf_${yyyymmdd}.csv / 買掛仕入MFインポートファイル_${yyyymmdd}.csv
             // (cached.transferDate ベース) で /export-verified と統一する。
             java.time.LocalDate transferDate = importService.getCachedTransferDate(uploadId);
             byte[] csv = importService.convert(uploadId, user == null ? null : user.getUser().getLoginUserNo());
             String yyyymmdd = transferDate == null ? "unknown"
                     : transferDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
             String fileName = "買掛仕入MFインポートファイル_" + yyyymmdd + ".csv";
             String asciiName = "payment_mf_" + yyyymmdd + ".csv";
             String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
             return ResponseEntity.ok()
                     .contentType(MediaType.parseMediaType("text/csv; charset=Shift_JIS"))
                     .header(HttpHeaders.CONTENT_DISPOSITION,
                             "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encoded)
                     .body(csv);
         } catch (IllegalArgumentException e) {
             return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
         }
         // IllegalStateException は FinanceExceptionHandler#handleIllegalState で 422 + 汎用メッセージ統一
     }
 
     // ---- 買掛金一覧への一括検証反映 ----
 
     /**
      * 振込明細 Excel の検証結果を t_accounts_payable_summary に一括反映する。
      *
      * <p>G2-M2 (2026-05-06): リクエストボディで {@code force} フラグを受ける。
      * <ul>
      *   <li>ボディ省略 / {@code force=false}: per-supplier 1 円不一致が 1 件でもあれば
      *       422 + {@code PER_SUPPLIER_MISMATCH} で拒否 ({@link FinanceExceptionHandler})。
      *       client は preview で違反を確認し、Excel 修正 → 再アップロードする運用。</li>
      *   <li>{@code force=true}: 違反を許容して反映。{@code finance_audit_log} に
      *       {@code FORCE_APPLIED: per-supplier mismatches=...} の補足 row が記録される。</li>
      * </ul>
      */
     @PreAuthorize("@loginUserSecurityBean.isAdmin()")
     @PostMapping("/verify/{uploadId}")
     public ResponseEntity<?> verify(@PathVariable String uploadId,
                                     @RequestBody(required = false) PaymentMfApplyRequest request,
                                     @AuthenticationPrincipal LoginUser user) {
         try {
             boolean force = request != null && request.isForce();
+            // Codex Major #4 (2026-05-06): force=true 時は forceReason 必須。
+            // service 層で空文字 / null チェックして FinanceBusinessException を投げる。
+            String forceReason = request == null ? null : request.getForceReason();
             var result = importService.applyVerification(
                     uploadId,
                     user == null ? null : user.getUser().getLoginUserNo(),
-                    force);
+                    force,
+                    forceReason);
             return ResponseEntity.ok(result);
         } catch (IllegalArgumentException e) {
             return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
         }
-        // FinanceBusinessException (PER_SUPPLIER_MISMATCH 含む) は FinanceExceptionHandler で処理
+        // FinanceBusinessException (PER_SUPPLIER_MISMATCH / FORCE_REASON_REQUIRED 含む) は FinanceExceptionHandler で処理
     }
 
     // ---- 検証済み買掛金から MF CSV 出力（Excel 再アップロード不要）----
 
     /**
      * 検証済み(verificationResult=1 & mfExportEnabled=true)の買掛金サマリから
      * MF仕訳CSVを直接生成する。PAYABLE 行のみが含まれる点に注意。
      * <p>CSV 取引日列は 小田光締め日 = transactionMonth (前月20日) 固定。
      * 支払日は MF 側の銀行データ連携で自動付与されるため CSV には含めない。
      *
      * @param transactionMonth 対象取引月 (yyyy-MM-dd, 例 2026-01-20)
      */
     @PreAuthorize("@loginUserSecurityBean.isAdmin()")
     @GetMapping("/export-verified")
     public ResponseEntity<?> exportVerified(
             @RequestParam("transactionMonth") String transactionMonth,
             @AuthenticationPrincipal LoginUser user) {
         try {
             java.time.LocalDate txMonth = java.time.LocalDate.parse(transactionMonth);
             var result = importService.exportVerifiedCsv(
                     txMonth, user == null ? null : user.getUser().getLoginUserNo());
 
             String yyyymmdd = txMonth.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
             String fileName = "買掛仕入MFインポートファイル_" + yyyymmdd + ".csv";
             String asciiName = "payment_mf_" + yyyymmdd + ".csv"; // SF-C20: 日付付きで統一
             String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
             // X-Skipped-Suppliers: 各 supplier 名を個別に encodeURIComponent し "|" で連結する。
             // supplier 名に "," が含まれるケースでパース崩れを起こさないよう、区切り文字はカンマ以外にする。
             // フロント側は split("|") → decodeURIComponent でデコードする。
             List<String> suppliers = result.getSkippedSuppliers() == null ? List.of()
                     : result.getSkippedSuppliers();
             String skippedHeader = suppliers.stream()
                     .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                     .reduce((a, b) -> a + "|" + b)
                     .orElse("");
             return ResponseEntity.ok()
                     .contentType(MediaType.parseMediaType("text/csv; charset=Shift_JIS"))
                     .header(HttpHeaders.CONTENT_DISPOSITION,
                             "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encoded)
                     .header("X-Row-Count", String.valueOf(result.getRowCount()))
                     .header("X-Total-Amount", String.valueOf(result.getTotalAmount()))
                     .header("X-Skipped-Count", String.valueOf(suppliers.size()))
                     .header("X-Skipped-Suppliers", skippedHeader)
                     .body(result.getCsv());
         } catch (java.time.format.DateTimeParseException e) {
             return ResponseEntity.badRequest().body(Map.of("message", "日付形式が不正です (yyyy-MM-dd)"));
         } catch (IllegalArgumentException e) {
             return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
         }
         // IllegalStateException は FinanceExceptionHandler#handleIllegalState で 422 + 汎用メッセージ統一
     }
 
     /**
      * 検証済みCSV出力のプレビュー。ダイアログで件数確認 + 警告表示用。
      */
     @PreAuthorize("@loginUserSecurityBean.isAdmin()")
     @GetMapping("/export-verified/preview")
     public ResponseEntity<?> exportVerifiedPreview(
             @RequestParam("transactionMonth") String transactionMonth) {
         try {
             java.time.LocalDate txMonth = java.time.LocalDate.parse(transactionMonth);
             VerifiedExportPreviewResponse preview = importService.buildVerifiedExportPreview(txMonth);
             return ResponseEntity.ok(preview);
         } catch (java.time.format.DateTimeParseException e) {
             return ResponseEntity.badRequest().body(Map.of("message", "日付形式が不正です (yyyy-MM-dd)"));
         } catch (IllegalArgumentException e) {
             return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
         }
     }
 
     /**
      * 指定取引月の MF 補助行 (EXPENSE/SUMMARY/DIRECT_PURCHASE) 一覧を返す。
      * 買掛金一覧の「MF補助行」タブ表示用。
      */
     @PreAuthorize("@loginUserSecurityBean.isAdmin()")
     @GetMapping("/aux-rows")
     public ResponseEntity<?> auxRows(
             @RequestParam("transactionMonth") String transactionMonth) {
         try {
             java.time.LocalDate txMonth = java.time.LocalDate.parse(transactionMonth);
diff --git a/backend/src/main/java/jp/co/oda32/domain/service/finance/FinancePayableLock.java b/backend/src/main/java/jp/co/oda32/domain/service/finance/FinancePayableLock.java
new file mode 100644
index 0000000..3973712
--- /dev/null
+++ b/backend/src/main/java/jp/co/oda32/domain/service/finance/FinancePayableLock.java
@@ -0,0 +1,61 @@
+package jp.co.oda32.domain.service.finance;
+
+import jakarta.persistence.EntityManager;
+
+import java.time.LocalDate;
+
+/**
+ * G2-M2 fix (Codex Major #2, 2026-05-06): 買掛金の verified_amount / verification_source 書込経路
+ * (BULK / MANUAL / MF_OVERRIDE) を、(shop_no, transaction_month) 単位で直列化するための共通 util。
+ *
+ * <p>従来 {@code PaymentMfImportService.applyVerification} のみが advisory lock を取っていたが、
+ * <ul>
+ *   <li>{@link TAccountsPayableSummaryService#verify} (UI 手入力 → MANUAL_VERIFICATION)</li>
+ *   <li>{@link ConsistencyReviewService#applyMfOverride} (整合性レポート → MF_OVERRIDE)</li>
+ * </ul>
+ * は lock 不在で last-write-wins の race を起こし得た。
+ * 例: applyVerification (BULK) → applyMfOverride (MF_OVERRIDE) → applyVerification (BULK 再 upload)
+ * の並列実行で source 列が再度 BULK で上書きされ、整合性レビューの副作用が消失する等。
+ *
+ * <p>本 util を 3 経路すべての先頭で呼ぶことで、PostgreSQL {@code pg_advisory_xact_lock} により
+ * (shop_no, transaction_month) 単位で書込操作を直列化する。{@code pg_advisory_xact_lock} は
+ * トランザクション境界で自動解放されるため、解放漏れリスクは無い。
+ *
+ * <p>キー設計:
+ * <pre>{@code
+ * lockKey = ((long) shopNo << 32) | (transactionMonth.toEpochDay() & 0xFFFF_FFFFL)
+ * }</pre>
+ * shopNo は将来マルチテナント化時の競合回避マージン。{@code toEpochDay()} は 1970-01-01 起点で
+ * 約 1970-2061 年範囲なら 21 bit 程度なので、下位 32 bit には十分収まる。
+ */
+public final class FinancePayableLock {
+
+    private FinancePayableLock() {}
+
+    /**
+     * (shopNo, transactionMonth) ペアから 64bit advisory lock key を計算する。
+     * 同一ペアなら同一 key。同一 transactionMonth でも shopNo が違えば別 key となり、
+     * 第1事業部 (shop=1) と第2事業部 (将来) の処理は独立に並走できる。
+     */
+    public static long computePayableLockKey(int shopNo, LocalDate transactionMonth) {
+        if (transactionMonth == null) {
+            throw new IllegalArgumentException("transactionMonth must not be null");
+        }
+        return ((long) shopNo << 32) | (transactionMonth.toEpochDay() & 0xFFFF_FFFFL);
+    }
+
+    /**
+     * (shopNo, transactionMonth) 単位の advisory lock を取得する。
+     * 呼び出し元の {@link org.springframework.transaction.annotation.Transactional @Transactional} 境界で
+     * 自動解放されるため、解放呼び出しは不要。
+     *
+     * <p>同一 key を取った別 tx は先行 tx の commit/rollback 完了まで待たされる。
+     * これにより BULK / MANUAL / MF_OVERRIDE の 3 書込経路が 1 度に 1 経路ずつしか走らない。
+     */
+    public static void acquire(EntityManager entityManager, int shopNo, LocalDate transactionMonth) {
+        long lockKey = computePayableLockKey(shopNo, transactionMonth);
+        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:k)")
+                .setParameter("k", lockKey)
+                .getSingleResult();
+    }
+}
diff --git a/backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParser.java b/backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParser.java
index c259d26..2a9e4bd 100644
--- a/backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParser.java
+++ b/backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParser.java
@@ -1,276 +1,316 @@
 package jp.co.oda32.domain.service.finance;
 
 import org.apache.poi.ss.usermodel.Row;
 import org.apache.poi.ss.usermodel.Sheet;
 import org.apache.poi.ss.usermodel.Workbook;
+import org.slf4j.Logger;
+import org.slf4j.LoggerFactory;
 
 import java.time.LocalDate;
 import java.util.ArrayList;
 import java.util.EnumMap;
 import java.util.LinkedHashMap;
 import java.util.List;
 import java.util.Map;
 import java.util.Set;
 
 /**
  * 振込明細 Excel（支払い明細 / 振込明細シート）から明細行と合計行サマリを抽出する。
  *
  * <p>Excel 構造:
  * <ul>
  *   <li>1 行目: ヘッダ情報。E1 付近に送金日（LocalDate）が入る。</li>
  *   <li>2 行目: カラム名行（送り先 / 請求額 / 振込金額 / 送料相手 / 早払い 等）。</li>
  *   <li>3 行目以降: 5日払い 明細 → 5日払い 合計 → 20日払い 明細 → 20日払い 合計。</li>
  * </ul>
  *
  * <p>ステートレスな純粋関数ユーティリティ。Bean 化せず、呼び出し側は
  * {@link #selectSheet(Workbook)} / {@link #parseSheet(Sheet)} を直接呼ぶ。
  * 数値コードの 6 桁正規化は {@link PaymentMfImportService#normalizePaymentSupplierCode}
  * を利用する（PAYABLE 用のみ、同パッケージ内で共有）。
  *
  * <p>G2-M3 (2026-05-06): 旧 {@code afterTotal} ブール値モデルを {@link PaymentMfSection}
  * 列挙型に置き換え、合計行ごとに section 別 summary をキャプチャする。
  * 旧実装は最初の合計行 (= 5日払い summary) を 1 度だけ捕まえてフラグを立てるだけだったため、
  * 20日払いセクションの合計行が捨てられて整合性チェック (chk1/chk3) が
  * 「5日払い summary vs 5日払い+20日払い 両方の明細合計」を比較する形にズレていた。
  */
 final class PaymentMfExcelParser {
 
+    private static final Logger log = LoggerFactory.getLogger(PaymentMfExcelParser.class);
+
     private PaymentMfExcelParser() {}
 
+    /**
+     * Codex Major #5 (2026-05-06): 送金日 day で初期 section を判定する境界 (exclusive)。
+     * <p>day &lt; CUTOFF → {@link PaymentMfSection#PAYMENT_5TH} 開始 (= 5日払い相当)。
+     * <p>day &ge; CUTOFF → {@link PaymentMfSection#PAYMENT_20TH} 開始 (= 20日払い相当)。
+     * <p>{@code FinanceConstants.PAYMENT_DATE_MIDMONTH_CUTOFF} と同値 (15)。
+     * 振替で 5日 が 4日/6日/7日, 20日 が 19日/21日 等に前後しても包括的に正しい section を選べる。
+     */
+    private static final int SECTION_INITIAL_CUTOFF_DAY = 15;
+
     /** 合計/メタ行のB列判定（正規化後の完全一致） */
     private static final Set<String> META_EXACT = Set.of(
             "合計", "小計", "その他計", "本社仕入 合計", "請求額", "打ち込み額", "打込額",
             "本社仕入合計"
     );
     /** 前方一致でメタ行判定 */
     private static final List<String> META_PREFIX = List.of(
             "20日払い振込手数料", "5日払い振込手数料", "送金日"
     );
 
     /**
      * Workbook から対象シートを選択する。優先順位: "支払い明細" &gt; "振込明細" &gt; 部分一致フォールバック。
      * 変換MAP・MF 用シート・福通シートは除外する。
      *
      * @return 対象シート。見つからない場合は null。
      */
     static Sheet selectSheet(Workbook workbook) {
         Sheet byExactPayment = null;
         Sheet byExactTransfer = null;
         for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
             String n = workbook.getSheetName(i);
             if (n == null) continue;
             if (n.contains("MF") || n.contains("変換MAP") || n.contains("福通")) continue;
             if ("支払い明細".equals(n)) byExactPayment = workbook.getSheetAt(i);
             else if ("振込明細".equals(n)) byExactTransfer = workbook.getSheetAt(i);
         }
         if (byExactPayment != null) return byExactPayment;
         if (byExactTransfer != null) return byExactTransfer;
         // フォールバック
         for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
             String n = workbook.getSheetName(i);
             if (n != null && (n.contains("支払") || n.contains("振込"))
                     && !n.contains("MF") && !n.contains("変換") && !n.contains("福通")) {
                 return workbook.getSheetAt(i);
             }
         }
         return null;
     }
 
     /**
      * シートを走査し、送金日・合計行サマリ・明細エントリを抽出する。
      *
      * <p>section 遷移: 走査開始時 {@link PaymentMfSection#PAYMENT_5TH}。
      * 合計行を検出するたびに、現在 section の summary を {@link ParsedExcel#summaries} へ格納し、
      * 次の section ({@code PAYMENT_5TH → PAYMENT_20TH}) に切替える。
      * {@code PAYMENT_20TH} 以降にさらに合計行があれば、{@code PAYMENT_20TH} の summary を最新値で上書きする
      * (現実的には 5日払い + 20日払いの 2 セクション以外は出ない)。
      */
     static ParsedExcel parseSheet(Sheet sheet) {
         ParsedExcel out = new ParsedExcel();
 
         // 送金日: 1行目の日付セルをスキャン（通常はE1）
         Row r1 = sheet.getRow(0);
         if (r1 != null) {
             for (int c = 0; c <= 10; c++) {
                 LocalDate d = PaymentMfCellReader.readDateCell(r1.getCell(c));
                 if (d != null) { out.transferDate = d; break; }
             }
         }
 
         // ヘッダ行（2行目）から列マップ構築
         Row header = sheet.getRow(1);
         if (header == null) throw new IllegalArgumentException("ヘッダ行（2行目）が見つかりません");
         Map<String, Integer> colMap = buildColumnMap(header);
         // 「仕入コード」ヘッダが無い振込明細（20日払いなど）では、送り先列の直前（通常は A列）に数値コードが入る。
         Integer colCode = colMap.getOrDefault("仕入コード", null);
         Integer colSource = colMap.get("送り先");
         if (colCode == null && colSource != null && colSource > 0) {
             colCode = colSource - 1;
         }
         Integer colAmount = colMap.get("請求額");
         Integer colFee = colMap.get("送料相手");
         Integer colEarly = colMap.get("早払い");
         // 合計行の「振込金額」列を読み取り、請求額総計 - 値引 - 早払 との整合性チェックに使う
         Integer colTransfer = colMap.get("振込金額");
         // P1-03 案 D: per-supplier 行で値引/相殺/打込金額を読取り、supplier 別 attribute を追跡する。
         // colTransfer (列 E) は合計行サマリーと per-supplier の双方で使う。
         Integer colDiscount = colMap.get("値引");
         Integer colOffset = colMap.get("相殺");
         Integer colPayin = colMap.get("打込金額"); // 整合性検証用 (CSV には出さない派生値)
         if (colSource == null || colAmount == null) {
             List<String> missing = new ArrayList<>();
             if (colSource == null) missing.add("送り先");
             if (colAmount == null) missing.add("請求額");
             throw new IllegalArgumentException(
                     "ヘッダ『" + String.join("』『", missing) + "』が特定できません。"
                     + "2行目で見つかった列: " + colMap.keySet());
         }
 
         int last = sheet.getLastRowNum();
-        // G2-M3: section enum で 5日払い / 20日払い を明示的に区別する。走査開始時は 5日払い。
-        PaymentMfSection currentSection = PaymentMfSection.PAYMENT_5TH;
+        // G2-M3: section enum で 5日払い / 20日払い を明示的に区別する。
+        // Codex Major #5 (2026-05-06): 送金日 day で初期 section を判定する。
+        //   - day < 15 → PAYMENT_5TH 開始 (5日払い)
+        //   - day >= 15 → PAYMENT_20TH 開始 (20日払い)
+        //   - 送金日不明 → PAYMENT_5TH (デフォルト) + WARN ログ
+        // 旧実装は常に PAYMENT_5TH 開始だったため、20日払い専用 Excel が来ると最初の合計行で
+        // PAYMENT_5TH summary をキャプチャしてしまい、entries も 全て PAYMENT_5TH として扱われ、
+        // 整合性チェック・PAYABLE/DIRECT_PURCHASE 振り分け・aux 保存が誤動作していた。
+        PaymentMfSection currentSection = determineInitialSection(out.transferDate);
         for (int i = 2; i <= last; i++) {
             Row row = sheet.getRow(i);
             if (row == null) continue;
 
             String sourceName = PaymentMfCellReader.readStringCell(row.getCell(colSource));
             String sourceNorm = PaymentMfCellReader.normalize(sourceName);
             Long amount = PaymentMfCellReader.readLongCell(row.getCell(colAmount));
 
             // 合計行の処理（section 別 summary 抽出 + 次セクションへ遷移）
             if (sourceNorm != null && ("合計".equals(sourceNorm) || isTotalRow(row, colSource))) {
                 SectionSummary summary = new SectionSummary();
                 summary.sourceFee = colFee == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colFee));
                 summary.earlyPayment = colEarly == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colEarly));
                 summary.transferAmount = colTransfer == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colTransfer));
                 // 合計行の請求額列は colAmount をそのまま読む (旧 summaryInvoiceTotal と同義)。
                 summary.invoiceTotal = PaymentMfCellReader.readLongCell(row.getCell(colAmount));
                 // 同 section の合計行が複数あれば最後の値で上書き (現実的には起こらない)。
                 out.summaries.put(currentSection, summary);
 
                 // 次セクションへ遷移。PAYMENT_20TH の合計行を 2 度目以降に踏んだ場合は
                 // 状態は PAYMENT_20TH のまま (= 上書き) で、これ以上の section 拡張はしない。
                 if (currentSection == PaymentMfSection.PAYMENT_5TH) {
                     currentSection = PaymentMfSection.PAYMENT_20TH;
                 }
                 continue;
             }
             // メタ行スキップ
             if (isMetaRow(sourceNorm)) continue;
             // 金額ゼロ/未入力はスキップ
             if (amount == null || amount == 0L) continue;
             if (sourceName == null || sourceName.isBlank()) continue;
 
             String supplierCode = null;
             if (colCode != null && colCode >= 0) {
                 supplierCode = PaymentMfCellReader.readStringCell(row.getCell(colCode));
                 if (supplierCode != null) {
                     supplierCode = supplierCode.trim();
                     if (supplierCode.isEmpty() || !supplierCode.chars().allMatch(Character::isDigit)) {
                         supplierCode = null;
                     } else {
                         supplierCode = PaymentMfImportService.normalizePaymentSupplierCode(supplierCode);
                     }
                 }
             }
 
             ParsedEntry pe = new ParsedEntry();
             pe.rowIndex = i + 1;
             pe.supplierCode = supplierCode;
             pe.sourceName = sourceName.trim();
             pe.amount = amount;
             // G2-M3: 旧 boolean afterTotal は section enum に置換。
             pe.section = currentSection;
             // P1-03 案 D: per-supplier の値引/早払/送料相手/相殺/振込金額を読取る。
             // 合計行 (= section 別 summary に集約) ではなく、ここで個別 supplier ごとに保持する。
             pe.transferAmount = colTransfer == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colTransfer));
             pe.fee = colFee == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colFee));
             pe.discount = colDiscount == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colDiscount));
             pe.earlyPayment = colEarly == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colEarly));
             pe.offset = colOffset == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colOffset));
             pe.payinAmount = colPayin == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colPayin));
             out.entries.add(pe);
         }
         return out;
     }
 
     private static Map<String, Integer> buildColumnMap(Row header) {
         Map<String, Integer> map = new LinkedHashMap<>();
         for (int c = 0; c < header.getLastCellNum(); c++) {
             String v = PaymentMfCellReader.readStringCell(header.getCell(c));
             if (v == null) continue;
             String n = PaymentMfCellReader.normalize(v);
             if (n.isEmpty()) continue;
             map.putIfAbsent(n, c);
         }
         return map;
     }
 
     private static boolean isTotalRow(Row row, int colSource) {
         // A列やB列が「合計」という明示
         for (int c = 0; c < Math.min(row.getLastCellNum(), colSource + 2); c++) {
             String v = PaymentMfCellReader.normalize(PaymentMfCellReader.readStringCell(row.getCell(c)));
             if ("合計".equals(v)) return true;
         }
         return false;
     }
 
     private static boolean isMetaRow(String normalized) {
         if (normalized == null || normalized.isEmpty()) return true;
         if (META_EXACT.contains(normalized)) return true;
         for (String p : META_PREFIX) if (normalized.startsWith(p)) return true;
         return false;
     }
 
+    /**
+     * Codex Major #5 (2026-05-06): 送金日 day から初期 section を判定する (パッケージ可視: テスト用)。
+     * <ul>
+     *   <li>day &lt; {@link #SECTION_INITIAL_CUTOFF_DAY} (= 15) → {@link PaymentMfSection#PAYMENT_5TH}</li>
+     *   <li>day &ge; 15 → {@link PaymentMfSection#PAYMENT_20TH}</li>
+     *   <li>{@code transferDate == null} → {@link PaymentMfSection#PAYMENT_5TH} (デフォルト) + WARN ログ</li>
+     * </ul>
+     * 旧実装は常に PAYMENT_5TH 開始だったため、20日払い専用 Excel が来ると初期 section 誤分類を起こしていた。
+     */
+    static PaymentMfSection determineInitialSection(LocalDate transferDate) {
+        if (transferDate == null) {
+            log.warn("送金日が読み取れないため初期 section を PAYMENT_5TH にデフォルト設定します。"
+                    + "20日払い Excel の場合、整合性チェック・PAYABLE 振り分けが誤動作する可能性あり");
+            return PaymentMfSection.PAYMENT_5TH;
+        }
+        return transferDate.getDayOfMonth() < SECTION_INITIAL_CUTOFF_DAY
+                ? PaymentMfSection.PAYMENT_5TH
+                : PaymentMfSection.PAYMENT_20TH;
+    }
+
     /** Excel 明細1行の抽出結果（内部用 POJO）。 */
     static class ParsedEntry {
         int rowIndex;
         String supplierCode;
         String sourceName;
         Long amount;
         /**
          * G2-M3: この明細行が属するセクション (5日払い / 20日払い)。
          * 旧実装の {@code afterTotal} ブール値を置き換える。
          */
         PaymentMfSection section;
         // P1-03 案 D: per-supplier の付帯属性。NULL は該当列がヘッダに存在しないか空欄。
         /** 列 E: 振込金額 (= 打込金額 - 送料相手) */
         Long transferAmount;
         /** 列 F: 送料相手 (仕入先負担の振込手数料) */
         Long fee;
         /** 列 G: 値引 (通常の値引) */
         Long discount;
         /** 列 H: 早払い */
         Long earlyPayment;
         /** 列 I: 相殺 */
         Long offset;
         /** 列 D: 打込金額 (= 請求額 - 値引 - 早払。整合性検証用、CSV 出力には使わない派生値) */
         Long payinAmount;
     }
 
     /** 1 セクション (5日払い or 20日払い) の合計行 summary。 */
     static class SectionSummary {
         /** 列 F: 送料相手合計 */
         Long sourceFee;
         /** 列 H: 早払合計 */
         Long earlyPayment;
         /** 列 E: 振込金額合計 */
         Long transferAmount;
         /** 列 C: 請求額合計 */
         Long invoiceTotal;
     }
 
     /**
      * parseSheet の戻り値: 明細エントリ一覧 + section 別合計行サマリ + 送金日。
      *
      * <p>G2-M3 (2026-05-06): 旧 {@code summarySourceFee/EarlyPayment/TransferAmount/InvoiceTotal}
      * のフラットなフィールドを廃止し、{@code Map<PaymentMfSection, SectionSummary>} に格納する。
      * 5日払いのみの Excel では {@code summaries} に {@link PaymentMfSection#PAYMENT_5TH} のみ入り、
      * {@link PaymentMfSection#PAYMENT_20TH} は欠落する (空セクション許容)。
      */
     static class ParsedExcel {
         List<ParsedEntry> entries = new ArrayList<>();
         LocalDate transferDate;
         Map<PaymentMfSection, SectionSummary> summaries = new EnumMap<>(PaymentMfSection.class);
     }
 }
diff --git a/backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java b/backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java
index 8e035c9..56e8ddb 100644
--- a/backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java
+++ b/backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java
@@ -1,808 +1,881 @@
 package jp.co.oda32.domain.service.finance;
 
 import jakarta.annotation.PostConstruct;
 import jakarta.persistence.EntityManager;
 import jakarta.persistence.PersistenceContext;
 import com.fasterxml.jackson.databind.ObjectMapper;
 import jp.co.oda32.audit.AuditLog;
 import jp.co.oda32.audit.FinanceAuditWriter;
 import jp.co.oda32.constant.FinanceConstants;
 import jp.co.oda32.domain.model.finance.MOffsetJournalRule;
 import jp.co.oda32.domain.model.finance.MPaymentMfRule;
 import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
 import jp.co.oda32.domain.model.finance.TPaymentMfAuxRow;
 import jp.co.oda32.domain.model.finance.TPaymentMfImportHistory;
 import jp.co.oda32.domain.repository.finance.MOffsetJournalRuleRepository;
 import jp.co.oda32.domain.repository.finance.MPaymentMfRuleRepository;
 import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
 import jp.co.oda32.domain.repository.finance.TPaymentMfAuxRowRepository;
 import jp.co.oda32.domain.repository.finance.TPaymentMfImportHistoryRepository;
 import jp.co.oda32.dto.finance.paymentmf.AppliedWarning;
 import jp.co.oda32.dto.finance.paymentmf.DuplicateWarning;
 import jp.co.oda32.dto.finance.paymentmf.PaymentMfAuxRowResponse;
 import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewResponse;
 import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewRow;
 import jp.co.oda32.dto.finance.paymentmf.VerifiedExportPreviewResponse;
 import jp.co.oda32.exception.FinanceBusinessException;
+import jp.co.oda32.exception.FinanceInternalException;
 import lombok.Builder;
 import lombok.Data;
 import lombok.extern.slf4j.Slf4j;
 import org.apache.poi.openxml4j.util.ZipSecureFile;
 import org.apache.poi.ss.usermodel.Sheet;
 import org.apache.poi.ss.usermodel.Workbook;
 import org.apache.poi.xssf.usermodel.XSSFWorkbook;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.context.annotation.Lazy;
 import org.springframework.scheduling.annotation.Scheduled;
 import org.springframework.stereotype.Service;
 import org.springframework.transaction.annotation.Propagation;
 import org.springframework.transaction.annotation.Transactional;
 import org.springframework.web.multipart.MultipartFile;
 
 import java.io.IOException;
 import java.math.BigDecimal;
 import java.math.RoundingMode;
 import java.security.MessageDigest;
 import java.security.NoSuchAlgorithmException;
 import java.time.LocalDate;
 import java.time.LocalDateTime;
 import java.time.OffsetDateTime;
 import java.time.ZoneOffset;
 import java.time.format.DateTimeFormatter;
 import java.util.ArrayList;
 import java.util.Comparator;
 import java.util.LinkedHashMap;
 import java.util.List;
 import java.util.Map;
 import java.util.Set;
 import java.util.UUID;
 import java.util.concurrent.ConcurrentHashMap;
 import java.util.stream.Collectors;
 
 /**
  * 振込明細Excel → MoneyForward買掛仕入CSV 変換サービス
  */
 @Service
 @Slf4j
 public class PaymentMfImportService {
 
     private final MPaymentMfRuleRepository ruleRepository;
     private final TPaymentMfImportHistoryRepository historyRepository;
     private final TAccountsPayableSummaryRepository payableRepository;
     private final TAccountsPayableSummaryService payableService;
     private final TPaymentMfAuxRowRepository auxRowRepository;
     /**
      * G2-M8: OFFSET 副行貸方科目マスタ ({@code m_offset_journal_rule}) の Repository。
      *
      * <p>従来 {@code "仕入値引・戻し高" / "物販事業部" / "課税仕入-返還等 10%"} と
      * ハードコードされていた OFFSET 副行貸方を本テーブルから lookup することで、
      * 税理士確認後に admin が UI 経由で値を書き換えられるようにした。
      * V041 の seed で shop_no=1 のデフォルト値 (= 旧ハードコード値と同一) を投入済。
      */
     private final MOffsetJournalRuleRepository offsetJournalRuleRepository;
 
     @PersistenceContext
     private EntityManager entityManager;
 
     /**
      * 自己注入。{@code @Transactional(REQUIRES_NEW)} が同一クラス内呼び出しでも
      * Spring AOP プロキシ経由になるようにするため。{@code @Lazy} は循環依存回避。
      */
     @Autowired
     @Lazy
     private PaymentMfImportService self;
 
     /**
      * G2-M2 (2026-05-06): per-supplier 1 円不一致を {@code force=true} で許容して反映した際、
      * 既存 {@code @AuditLog} aspect の after snapshot 行とは別に、違反詳細を {@code reason} 列に
      * 持つ補足 audit 行を 1 件追加する。AOP 拡張せずに済むよう field injection で別 Bean を持つ。
      * <p>{@code @Lazy} は AOP / proxy のブートストラップ循環回避用 (writer 自体は内部で
      * {@code FinanceAuditLogRepository} を使うのみで本サービスへの依存は無いが、保守的に Lazy)。
      */
     @Autowired(required = false)
     @Lazy
     private FinanceAuditWriter financeAuditWriter;
 
     /** {@code force=true} 補足 audit 行の reason 値を組み立てる際の supplier mismatch 詳細表示上限。 */
     private static final int FORCE_AUDIT_MISMATCH_DETAIL_LIMIT = 50;
 
     // 差額一致閾値は FinanceConstants.PAYMENT_REPORT_MINOR_DIFFERENCE(_LONG) に集約。
     private static final int MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
     private static final int MAX_DATA_ROWS = 10000;
     private static final int MAX_CACHE_ENTRIES = 100;
     private static final long CACHE_TTL_MILLIS = 30L * 60 * 1000;
     private static final String XLSX_CONTENT_TYPE =
             "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
 
-    /**
-     * 取引月単位の applyVerification / exportVerifiedCsv を直列化する advisory lock 用キー。
-     * shop_no と transaction_month(epochDay) を混ぜて 64bit のキーに詰める。
-     */
-    private static final int ADVISORY_LOCK_CLASS = 0x7041_4D46; // 'pAMF'
+    // 取引月単位の advisory lock キー計算 / 取得は {@link FinancePayableLock} に共通化
+    // (G2-M2 Codex Major #2)。3 経路 (BULK / MANUAL / MF_OVERRIDE) で同一 util を使う。
 
     // CSV 生成ロジックは {@link PaymentMfCsvWriter} に分離（ステートレスユーティリティ）。
 
     // Excel 読み取り（selectSheet / parseSheet / メタ行判定 など）は
     // {@link PaymentMfExcelParser} に分離。ParsedEntry / ParsedExcel / SectionSummary も同クラスに移動。
 
     /**
      * アップロード済み Excel のパース結果をインメモリ保持する。
      * <p><b>node-local</b>: 複数 JVM 起動（HA 構成）では preview と convert/applyVerification が
      * 別インスタンスに到達すると 404 になる。本アプリは single-instance 前提の設計
      * （{@code MfOauthStateStore} と同じスコープ）。マルチ化するときは Redis 等に寄せること (B-W9)。
      */
     private final Map<String, CachedUpload> cache = new ConcurrentHashMap<>();
 
     public PaymentMfImportService(MPaymentMfRuleRepository ruleRepository,
                                   TPaymentMfImportHistoryRepository historyRepository,
                                   TAccountsPayableSummaryRepository payableRepository,
                                   TAccountsPayableSummaryService payableService,
                                   TPaymentMfAuxRowRepository auxRowRepository,
                                   MOffsetJournalRuleRepository offsetJournalRuleRepository) {
         this.ruleRepository = ruleRepository;
         this.historyRepository = historyRepository;
         this.payableRepository = payableRepository;
         this.payableService = payableService;
         this.auxRowRepository = auxRowRepository;
         this.offsetJournalRuleRepository = offsetJournalRuleRepository;
     }
 
     @PostConstruct
     void initPoiSecurity() {
         ZipSecureFile.setMinInflateRatio(0.01);
         ZipSecureFile.setMaxEntrySize(100L * 1024 * 1024);
     }
 
     // ===========================================================
     // Public API
     // ===========================================================
 
     public PaymentMfPreviewResponse preview(MultipartFile file) throws IOException {
         validateFile(file);
         // P1-08 Phase 1: ファイルハッシュを取得元バイト列から計算 (cache に保存)。
         // file.getBytes() は MAX_UPLOAD_BYTES (20MB) で上限済 (validateFile)。
         byte[] uploadedBytes = file.getBytes();
         String fileHash = computeSha256Hex(uploadedBytes);
         try (Workbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(uploadedBytes))) {
             Sheet sheet = PaymentMfExcelParser.selectSheet(workbook);
             if (sheet == null) {
                 throw new IllegalArgumentException("振込明細シート（支払い明細/振込明細）が見つかりません");
             }
             PaymentMfExcelParser.ParsedExcel parsed = PaymentMfExcelParser.parseSheet(sheet);
             if (parsed.entries.size() > MAX_DATA_ROWS) {
                 throw new IllegalArgumentException("データ行数上限を超過しています: " + parsed.entries.size());
             }
 
             String uploadId = UUID.randomUUID().toString();
             CachedUpload cached = CachedUpload.builder()
                     .entries(parsed.entries)
                     .transferDate(parsed.transferDate)
                     .summaries(parsed.summaries)
                     .fileName(PaymentMfCellReader.sanitize(file.getOriginalFilename()))
                     .sourceFileHash(fileHash)
                     .expiresAt(System.currentTimeMillis() + CACHE_TTL_MILLIS)
                     .build();
             putCacheAtomically(uploadId, cached);
             return buildPreview(uploadId, cached);
         }
     }
 
     public PaymentMfPreviewResponse rePreview(String uploadId) {
         CachedUpload cached = getCached(uploadId);
         return buildPreview(uploadId, cached);
     }
 
     /**
      * CSVバイト列を返す（CP932・LF・金額に末尾半角スペース）。未登録行があれば例外。
      *
      * <p>G2-M2 (2026-05-06): per-supplier 1 円不一致 ({@code perSupplierMismatches}) が
      * 1 件でもあれば 422 でブロックする。業務的には preview 画面で違反を確認してから
      * Excel を修正 → 再アップロードする運用なので、CSV ダウンロード経路には force 上書きを設けない。
      */
     public byte[] convert(String uploadId, Integer userNo) {
         CachedUpload cached = getCached(uploadId);
         PaymentMfPreviewResponse preview = buildPreview(uploadId, cached);
         if (preview.getErrorCount() > 0) {
             // T5: 業務メッセージ (ユーザーがマスタ登録すれば解決) なので FinanceBusinessException を使う。
             // 旧 IllegalStateException → FinanceExceptionHandler.handleIllegalState で 422 + 汎用化されてしまい
             // フロントに件数情報が届かない問題があった (Cluster C M1)。
             throw new FinanceBusinessException(
                     "未登録の送り先があります（" + preview.getErrorCount() + "件）。マスタ登録後に再試行してください");
         }
         // G2-M2: per-supplier 1 円不一致は CSV 出力経路では強制上書き不可 (Excel 修正が正しい運用)。
         List<String> mismatches = perSupplierMismatchesOf(preview);
         if (!mismatches.isEmpty()) {
             throw new FinanceBusinessException(
                     "per-supplier 1 円整合性違反 " + mismatches.size() + " 件あり。"
                             + "プレビュー画面で詳細を確認し、Excel を修正してから再アップロードしてください",
                     FinanceConstants.ERROR_CODE_PER_SUPPLIER_MISMATCH);
         }
         // CSV 取引日列は 小田光の締め日(前月20日 = transactionMonth) 固定。
         // 送金日(送金日≠取引日)は MF の銀行データ連携で自動付与されるため CSV には含めない。
         LocalDate txMonth = cached.getTransferDate() == null ? null
                 : deriveTransactionMonth(cached.getTransferDate());
         byte[] csv = PaymentMfCsvWriter.toCsvBytes(preview.getRows(), txMonth);
         saveHistory(cached, preview, csv, userNo);
         return csv;
     }
 
     /**
-     * 旧 API 後方互換用 ({@code force=false} 固定で {@link #applyVerification(String, Integer, boolean)} を呼ぶ)。
+     * 旧 API 後方互換用 ({@code force=false} 固定で {@link #applyVerification(String, Integer, boolean, String)} を呼ぶ)。
      * <p>テスト・既存 batch 経路から既にこの 2 引数版が直接呼ばれているため、シグネチャは維持する。
      * 本メソッド経由でも force false 経路で per-supplier 1 円不一致は 422 ブロックとなる。
      *
-     * @deprecated G2-M2 (2026-05-06) 以降、新コードは {@link #applyVerification(String, Integer, boolean)}
-     *             を使い、{@code force} の意図を明示すること。
+     * @deprecated G2-M2 (2026-05-06) 以降、新コードは {@link #applyVerification(String, Integer, boolean, String)}
+     *             を使い、{@code force} の意図と承認理由を明示すること。
      */
     @Deprecated
     public VerifyResult applyVerification(String uploadId, Integer userNo) {
-        return applyVerification(uploadId, userNo, false);
+        return applyVerification(uploadId, userNo, false, null);
+    }
+
+    /**
+     * 旧 3-引数 API 後方互換用 (Codex Major #4 で {@code forceReason} を追加した 4 引数版を呼ぶ)。
+     * 既存テストと旧 client が 3 引数で呼んでいるため、シグネチャは維持する。
+     * <p>{@code force=true} で本オーバーロードを呼ぶと {@code forceReason} が無いため
+     * {@link FinanceBusinessException} (FORCE_REASON_REQUIRED) が投げられる点に注意。
+     *
+     * @deprecated G2-M4 fix (2026-05-06) 以降、新コードは
+     *             {@link #applyVerification(String, Integer, boolean, String)} を使い、
+     *             {@code force=true} 時は forceReason も渡すこと。
+     */
+    @Deprecated
+    public VerifyResult applyVerification(String uploadId, Integer userNo, boolean force) {
+        return applyVerification(uploadId, userNo, force, null);
     }
 
     /**
      * アップロード済み振込明細を正として、買掛金一覧(t_accounts_payable_summary)に検証結果を一括反映する。
      * verified_manually=true で手動確定扱いにし、SMILE再検証バッチで上書きされないようにする。
      *
      * <p>並列呼び出しでの上書き競合を防ぐため、同一 (shop_no, transaction_month) に対しては
      * PostgreSQL advisory lock で直列化する。5日払い Excel と 20日払い Excel が別プロセス/
      * 別 UI から同時適用された場合でも、後着は先行 tx のコミット完了後に実行される。
      *
      * <p>G2-M2 (2026-05-06): {@code force} パラメータ追加。
      * <ul>
      *   <li>{@code force=false} (推奨デフォルト): per-supplier 1 円整合性違反
      *       ({@code AmountReconciliation#perSupplierMismatches}) が 1 件でもあれば
      *       {@link FinanceBusinessException} (422) でブロックする。
      *       業務的には preview で違反一覧を確認 → Excel を修正 → 再アップロード が正しい運用。</li>
      *   <li>{@code force=true}: 違反を許容して反映する。
      *       {@code finance_audit_log.reason} に {@code FORCE_APPLIED: per-supplier mismatches=...}
      *       で違反詳細を補足記録し、後追い監査を可能にする。</li>
      * </ul>
+     *
+     * <p>Codex Major #4 (2026-05-06): {@code forceReason} 必須化。
+     * <ul>
+     *   <li>{@code force=true} かつ {@code forceReason} が null / 空文字 →
+     *       {@link FinanceBusinessException} (FORCE_REASON_REQUIRED, 400) で拒否。</li>
+     *   <li>{@code force=false} の場合 {@code forceReason} は無視される。</li>
+     * </ul>
+     * 二段認可は実装スコープ過大のため運用 runbook (= 最低 2 名で内容確認) にて担保し、
+     * 実装は forceReason 必須化のみで「誰が」「なぜ」を audit に残せるようにする。
      */
     @Transactional
     @AuditLog(table = "t_accounts_payable_summary", operation = "payment_mf_apply",
             pkExpression = "{'uploadId': #a0, 'userNo': #a1, 'force': #a2}",
             captureArgsAsAfter = true, captureReturnAsAfter = true)
-    public VerifyResult applyVerification(String uploadId, Integer userNo, boolean force) {
+    public VerifyResult applyVerification(String uploadId, Integer userNo, boolean force, String forceReason) {
         CachedUpload cached = getCached(uploadId);
         if (cached.getTransferDate() == null) {
             throw new IllegalArgumentException("送金日が取得できていません。xlsxを再アップロードしてください");
         }
+        // Codex Major #4: force=true 時の forceReason 必須化 (advisory lock 取得前に bail-out)。
+        // null / 空文字は不可。空白のみ ("   ") も拒否し、運用上意味のある理由を強制する。
+        if (force && (forceReason == null || forceReason.isBlank())) {
+            throw new FinanceBusinessException(
+                    "force=true 指定時は forceReason (承認理由) が必須です。"
+                            + "承認者・確認者・業務理由を含めて入力してください",
+                    FinanceConstants.ERROR_CODE_FORCE_REASON_REQUIRED);
+        }
         LocalDate txMonth = deriveTransactionMonth(cached.getTransferDate());
         acquireAdvisoryLock(txMonth);
 
         // G2-M2: per-supplier 1 円整合性違反のサーバー側ブロック。
         // preview を一度だけ事前構築し、ブロック判定 (force) と aux 保存の両方で使い回す。
         // (このタイミングなら DB ロック取得後で他 tx の影響を受けにくい)。
         PaymentMfPreviewResponse blockingPreview = buildPreview(uploadId, cached);
         List<String> mismatches = perSupplierMismatchesOf(blockingPreview);
         if (!mismatches.isEmpty() && !force) {
             // 422 + コード PER_SUPPLIER_MISMATCH を返す。client 側で「force=true で再実行」UI 分岐に使う。
             // 件数のみ返却し、supplier 名は preview レスポンスから別途取得させる
             // (例外メッセージに長い detail を載せると i18n / log noise の原因になる)。
             throw new FinanceBusinessException(
                     "per-supplier 1 円整合性違反 " + mismatches.size() + " 件あり。"
                             + "プレビュー画面で詳細を確認の上、強制実行する場合は force=true を指定してください",
                     FinanceConstants.ERROR_CODE_PER_SUPPLIER_MISMATCH);
         }
 
         // G2-M10 (V040): note 接頭辞は UI 表示用にのみ利用 (BULK/MANUAL 判定には verification_source を使う)。
         @SuppressWarnings("deprecation")
         String bulkPrefix = FinanceConstants.VERIFICATION_NOTE_BULK_PREFIX;
         String note = bulkPrefix + cached.getFileName() + " " + cached.getTransferDate();
 
         List<MPaymentMfRule> rules = ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0");
         Map<String, MPaymentMfRule> byCode = new LinkedHashMap<>();
         Map<String, MPaymentMfRule> bySource = new LinkedHashMap<>();
         for (MPaymentMfRule r : rules) {
             if (r.getPaymentSupplierCode() != null && !r.getPaymentSupplierCode().isBlank())
                 byCode.putIfAbsent(r.getPaymentSupplierCode().trim(), r);
             bySource.putIfAbsent(normalize(r.getSourceName()), r);
         }
 
         // 事前パス: 当該 Excel に含まれる全 supplierCode を集め、t_accounts_payable_summary を一括取得する (N+1 解消)。
         // 5日払い (PAYMENT_5TH) のみが PAYABLE 突合対象 (20日払いセクションは DIRECT_PURCHASE 自動降格)。
         Set<String> codesToReconcile = new java.util.LinkedHashSet<>();
         for (PaymentMfExcelParser.ParsedEntry e : cached.getEntries()) {
             if (e.section == PaymentMfSection.PAYMENT_20TH) continue;
             MPaymentMfRule rule = null;
             if (e.supplierCode != null) rule = byCode.get(e.supplierCode);
             if (rule == null) rule = bySource.get(normalize(e.sourceName));
             if (rule == null || !"PAYABLE".equals(rule.getRuleKind())) continue;
             String reconcileCode = e.supplierCode != null ? e.supplierCode
                     : (rule.getPaymentSupplierCode() != null && !rule.getPaymentSupplierCode().isBlank()
                             ? rule.getPaymentSupplierCode() : null);
             if (reconcileCode != null) codesToReconcile.add(reconcileCode);
         }
         Map<String, List<TAccountsPayableSummary>> payablesByCode = codesToReconcile.isEmpty()
                 ? Map.of()
                 : payableRepository.findByShopNoAndSupplierCodeInAndTransactionMonth(
                         FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, codesToReconcile, txMonth)
                     .stream()
                     .collect(Collectors.groupingBy(TAccountsPayableSummary::getSupplierCode));
 
         int matched = 0, diff = 0, notFound = 0, skipped = 0, skippedManuallyVerified = 0;
         List<String> unmatchedSuppliers = new ArrayList<>();
         BigDecimal matchThreshold = FinanceConstants.PAYMENT_REPORT_MINOR_DIFFERENCE;
 
         for (PaymentMfExcelParser.ParsedEntry e : cached.getEntries()) {
             // PAYABLE行のみ対象（20日払いセクションは DIRECT_PURCHASE 自動降格扱いで対象外）
             if (e.section == PaymentMfSection.PAYMENT_20TH) { skipped++; continue; }
             MPaymentMfRule rule = null;
             if (e.supplierCode != null) rule = byCode.get(e.supplierCode);
             if (rule == null) rule = bySource.get(normalize(e.sourceName));
             if (rule == null || !"PAYABLE".equals(rule.getRuleKind())) { skipped++; continue; }
             String reconcileCode = e.supplierCode != null ? e.supplierCode
                     : (rule.getPaymentSupplierCode() != null && !rule.getPaymentSupplierCode().isBlank()
                             ? rule.getPaymentSupplierCode() : null);
             if (reconcileCode == null) { skipped++; continue; }
 
             List<TAccountsPayableSummary> list = payablesByCode.getOrDefault(reconcileCode, List.of());
             if (list.isEmpty()) {
                 notFound++;
                 unmatchedSuppliers.add(e.sourceName + "(" + reconcileCode + ")");
                 continue;
             }
 
             // P1-08 Q3-(ii) / Cluster D SF-02 同パターン (G2-M10, V040 で source 列ベースに改修):
             //   `verified_manually=true` かつ <b>UI 手入力 verify (MANUAL_VERIFICATION)</b> 由来の行は
             //   再 upload で上書きしない。BULK_VERIFICATION (再 upload) や MF_OVERRIDE は上書き対象。
             //   税率別複数行のうち 1 行でも MANUAL があれば supplier 全体を保護対象とする
             //   (税率別 verified_amount/note を分割保持していないため、混在状態を作らないこと優先)。
             //
             //   旧実装は verification_note 接頭辞文字列 (VERIFICATION_NOTE_BULK_PREFIX) で BULK/MANUAL を
             //   推定していたが、ユーザが偶然 "振込明細検証 " で始まる note を手入力すると保護が外れる
             //   リスクがあった。V040 で verification_source 列を追加し、書込経路を明示記録する運用に切替。
             boolean anyManuallyLocked = isAnyManuallyLocked(list);
             if (anyManuallyLocked) {
                 skippedManuallyVerified++;
                 log.info("verified_manually=true (単一 verify) 行をスキップ: supplier={}({}) txMonth={}",
                         e.sourceName, reconcileCode, txMonth);
                 continue;
             }
 
             BigDecimal payable = BigDecimal.ZERO;
             for (TAccountsPayableSummary s : list) {
                 BigDecimal v = s.getTaxIncludedAmountChange() != null
                         ? s.getTaxIncludedAmountChange() : s.getTaxIncludedAmount();
                 if (v != null) payable = payable.add(v);
             }
             BigDecimal invoice = BigDecimal.valueOf(e.amount);
             BigDecimal difference = payable.subtract(invoice);
             boolean isMatched = difference.abs().compareTo(matchThreshold) <= 0;
             if (isMatched) matched++; else diff++;
 
             // 振込明細の請求額は支払先単位で1件だが、DBは税率別に複数行ある場合がある。
             // UI 表示用途のため、全税率行に同じ verified_amount を書き込む
             // （税率別の請求額内訳は Excel 側に存在しないため、合計値を代表値として保持）。
             // V026: 税抜側 (verified_amount_tax_excluded) も税率別に逆算して書き込む
             // → MF CSV 出力の「仕入高」金額が仕入先請求書と一致する。
             // auto_adjusted_amount に自動調整額 (verified - 元の自社計算) を記録 (監査証跡)。
             BigDecimal autoAdjusted = isMatched ? invoice.subtract(payable) : BigDecimal.ZERO;
             String adjustNote = isMatched && autoAdjusted.signum() != 0
                     ? note + " | 自動調整: 元 ¥" + payable + " → ¥" + invoice
                       + " (" + (autoAdjusted.signum() > 0 ? "+" : "") + "¥" + autoAdjusted + ")"
                     : note;
             for (TAccountsPayableSummary s : list) {
                 s.setVerificationResult(isMatched ? 1 : 0);
                 s.setPaymentDifference(difference);
                 s.setVerifiedManually(true);
                 s.setMfExportEnabled(isMatched);
                 s.setVerificationNote(adjustNote);
                 s.setVerifiedAmount(invoice);
                 // V026: 税抜 verified を税率別に逆算 (単一税率前提。複数税率の仕入先は手動調整で対応)。
                 BigDecimal taxRate = s.getTaxRate() != null ? s.getTaxRate() : BigDecimal.TEN;
                 BigDecimal divisor = BigDecimal.valueOf(100).add(taxRate);
                 BigDecimal invoiceTaxExcl = invoice.multiply(BigDecimal.valueOf(100))
                         .divide(divisor, 0, java.math.RoundingMode.DOWN);
                 s.setVerifiedAmountTaxExcluded(invoiceTaxExcl);
                 s.setAutoAdjustedAmount(autoAdjusted);
                 // 5日払いセクション (PAYMENT_5TH) の PAYABLE のみここに到達する。
                 // Excel の送金日を CSV 取引日として記録する。
                 s.setMfTransferDate(cached.getTransferDate());
                 // G2-M1/M10 (V040): 書込経路を明示記録 (read 側 sumVerifiedAmountForGroup と
                 // 再 upload 保護判定で参照される)。
                 s.setVerificationSource(FinanceConstants.VERIFICATION_SOURCE_BULK);
                 payableService.save(s);
             }
         }
 
         // 補助行(EXPENSE/SUMMARY/DIRECT_PURCHASE) を (shop, 取引月, 送金日) 単位で洗い替え保存。
         // G2-M2: ブロック判定で構築した blockingPreview をそのまま使い回す
         // (従来は saveAuxRowsForVerification 内で再 buildPreview していたため N+1 が 2 周していた)。
         PaymentMfPreviewResponse preview = blockingPreview;
         saveAuxRowsForVerification(cached, preview, txMonth, userNo);
 
         // P1-08: 確定済マークを history に永続化 (preview L2 警告の根拠データ)。
         saveAppliedHistory(cached, preview, userNo);
 
         // G2-M2: force=true で per-supplier 不一致を許容して反映した場合、
         // 補足 audit 行を追加して reason に違反詳細を残す。
         // 既存 @AuditLog aspect が記録する after snapshot 行とは別 row として書く
         // (AOP 拡張せずに済む & 後で reason 列で grep できる)。
+        // Codex Major #4: forceReason (承認者・確認者・業務理由) も audit reason に含める。
         if (force && !mismatches.isEmpty()) {
-            writeForceAppliedAuditRow(uploadId, userNo, cached, txMonth, mismatches);
+            writeForceAppliedAuditRow(uploadId, userNo, cached, txMonth, mismatches, forceReason);
         }
 
         return VerifyResult.builder()
                 .transferDate(cached.getTransferDate())
                 .transactionMonth(txMonth)
                 .matchedCount(matched)
                 .diffCount(diff)
                 .notFoundCount(notFound)
                 .skippedCount(skipped)
                 .skippedManuallyVerifiedCount(skippedManuallyVerified)
                 .unmatchedSuppliers(unmatchedSuppliers)
                 .build();
     }
 
     /**
      * G2-M2 (2026-05-06): {@code force=true} で per-supplier 1 円違反を許容して反映した時の補足 audit 行。
-     * <p>{@link FinanceAuditWriter#write} を直接呼んで {@code reason} 列に
-     * {@code "FORCE_APPLIED: per-supplier mismatches count=N, details=[...]"} を記録する。
-     * 詳細リストは長くなりすぎないよう先頭 {@link #FORCE_AUDIT_MISMATCH_DETAIL_LIMIT} 件のみ。
+     * <p>{@link FinanceAuditWriter#write} を直接呼んで {@code reason} 列に件数 + 先頭
+     * {@link #FORCE_AUDIT_MISMATCH_DETAIL_LIMIT} 件の詳細サマリを記録する (容量配慮)。
+     * Codex Major #3 (2026-05-06): {@code force_mismatch_details} JSONB 列 (V043) に
+     * <b>全件</b>を構造化保存し、reason との乖離を防ぐ。
+     * <p>Codex Major #4 (2026-05-06): {@code forceReason} (承認者・確認者・業務理由) を reason 末尾に
+     * {@code reason="..."} 形式で連結する。null の場合は付加しない (旧 client 互換用、3 引数 deprecated 経路)。
      * <p>writer Bean が無い (test 環境など) や書込み失敗は本体反映を巻き戻さない (warn ログのみ)。
      */
     private void writeForceAppliedAuditRow(String uploadId, Integer userNo, CachedUpload cached,
-                                           LocalDate txMonth, List<String> mismatches) {
+                                           LocalDate txMonth, List<String> mismatches, String forceReason) {
         if (financeAuditWriter == null) {
             log.warn("FinanceAuditWriter Bean 不在のため force=true 補足 audit を skip: uploadId={}", uploadId);
             return;
         }
         try {
-            String reason = buildForceAppliedReason(mismatches);
+            String reason = buildForceAppliedReason(mismatches, forceReason);
             ObjectMapper mapper = new ObjectMapper();
             com.fasterxml.jackson.databind.JsonNode pk = mapper.createObjectNode()
                     .put("uploadId", uploadId)
                     .put("userNo", userNo == null ? null : userNo.toString())
                     .put("force", "true")
                     .put("transferDate", cached.getTransferDate() == null ? null
                             : cached.getTransferDate().toString())
                     .put("transactionMonth", txMonth == null ? null : txMonth.toString())
                     .put("fileName", cached.getFileName());
+            // Codex Major #3: 全件を JSONB 列に構造化保存 (reason 50 件切り詰めとの乖離防止)。
+            com.fasterxml.jackson.databind.node.ArrayNode detailsArr = mapper.createArrayNode();
+            for (String m : mismatches) {
+                detailsArr.add(mapper.createObjectNode().put("line", m));
+            }
             financeAuditWriter.write(
                     "t_accounts_payable_summary",
                     "payment_mf_apply_force",
                     userNo,
                     userNo == null ? "SYSTEM" : "USER",
                     pk,
                     null,
                     null,
                     reason,
                     null,
-                    null);
+                    null,
+                    detailsArr);
             log.warn("payment_mf_apply force=true で per-supplier 不一致 {} 件を許容: uploadId={} txMonth={}",
                     mismatches.size(), uploadId, txMonth);
         } catch (Exception ex) {
             log.error("force=true 補足 audit 行の書込に失敗 (本体反映は完了済): uploadId={} err={}",
                     uploadId, ex.toString());
         }
     }
 
+    /**
+     * G2-M2: 補足 audit 行の {@code reason} 文字列を組み立てる (旧 1 引数版、後方互換用)。
+     * <p>新規コードは {@link #buildForceAppliedReason(List, String)} で forceReason も渡すこと。
+     * @deprecated Codex Major #4 (2026-05-06) 以降。
+     */
+    @Deprecated
+    static String buildForceAppliedReason(List<String> mismatches) {
+        return buildForceAppliedReason(mismatches, null);
+    }
+
     /**
      * G2-M2: 補足 audit 行の {@code reason} 文字列を組み立てる。
-     * <p>形式: {@code "FORCE_APPLIED: per-supplier mismatches count=N, details=[item1, item2, ...]"}
+     * <p>形式 (forceReason あり): {@code "FORCE_APPLIED: per-supplier mismatches count=N, details=[...], reason=\"...\""}
+     * <p>形式 (forceReason なし): {@code "FORCE_APPLIED: per-supplier mismatches count=N, details=[...]"}
      * <p>{@link #FORCE_AUDIT_MISMATCH_DETAIL_LIMIT} 件超過時は {@code "...(+M more)"} で打ち切り。
+     * 全件は別途 {@code finance_audit_log.force_mismatch_details} JSONB 列に保存される (V043, Codex Major #3)。
+     * <p>Codex Major #4 (2026-05-06): {@code forceReason} (承認者・確認者・業務理由) を末尾に追記。
+     * null / 空文字なら省略 (旧 deprecated 経路互換)。
      */
-    static String buildForceAppliedReason(List<String> mismatches) {
-        if (mismatches == null || mismatches.isEmpty()) {
-            return "FORCE_APPLIED: per-supplier mismatches count=0";
-        }
-        int total = mismatches.size();
-        int show = Math.min(total, FORCE_AUDIT_MISMATCH_DETAIL_LIMIT);
-        StringBuilder sb = new StringBuilder("FORCE_APPLIED: per-supplier mismatches count=")
-                .append(total).append(", details=[");
-        for (int i = 0; i < show; i++) {
-            if (i > 0) sb.append(", ");
-            sb.append(mismatches.get(i));
+    static String buildForceAppliedReason(List<String> mismatches, String forceReason) {
+        StringBuilder sb = new StringBuilder("FORCE_APPLIED: per-supplier mismatches count=");
+        int total = mismatches == null ? 0 : mismatches.size();
+        sb.append(total);
+        if (total > 0) {
+            int show = Math.min(total, FORCE_AUDIT_MISMATCH_DETAIL_LIMIT);
+            sb.append(", details=[");
+            for (int i = 0; i < show; i++) {
+                if (i > 0) sb.append(", ");
+                sb.append(mismatches.get(i));
+            }
+            if (total > show) {
+                sb.append(", ...(+").append(total - show).append(" more)");
+            }
+            sb.append(']');
         }
-        if (total > show) {
-            sb.append(", ...(+").append(total - show).append(" more)");
+        if (forceReason != null && !forceReason.isBlank()) {
+            // 二重引用符の入れ子を避けるため、value 側の " は ' に置換。改行は半角スペースに正規化
+            // (audit log の grep 安定化)。
+            String sanitized = forceReason.replace('"', '\'').replace('\n', ' ').replace('\r', ' ');
+            sb.append(", reason=\"").append(sanitized).append('"');
         }
-        sb.append(']');
         return sb.toString();
     }
 
     /**
      * G2-M2: preview から per-supplier 1 円不一致リストを安全に取り出す。
      * {@code amountReconciliation} or {@code perSupplierMismatches} が null の場合は空リスト。
      *
      * <p>パッケージ可視 + non-static にしてテストから {@code Mockito.spy} で差し替え可能にする
      * (Excel fixture を mismatch 用に作るのが難しいため、テストフックとして残す)。
      */
     List<String> perSupplierMismatchesOf(PaymentMfPreviewResponse preview) {
         if (preview == null || preview.getAmountReconciliation() == null) return List.of();
         List<String> mm = preview.getAmountReconciliation().getPerSupplierMismatches();
         return mm == null ? List.of() : mm;
     }
 
     /**
      * P1-08: applyVerification 実行時に history 行を 1 件追加し、
      * {@code applied_at} / {@code applied_by_user_no} / {@code source_file_hash} を記録する。
      * これにより後続 preview で同 (shop, transferDate) の L2 警告 (確定済) が出る。
      *
      * <p>convert と異なり CSV bytes は持たない (このフローは MF CSV 出力ではなく買掛検証反映のため)。
      * csv_body は NULL、csv_filename は applied マーカーであることを示す名前にする。
      */
     private void saveAppliedHistory(CachedUpload cached, PaymentMfPreviewResponse preview, Integer userNo) {
         try {
             LocalDateTime now = LocalDateTime.now();
             String yyyymmdd = cached.getTransferDate() == null ? "unknown"
                     : cached.getTransferDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
             TPaymentMfImportHistory h = TPaymentMfImportHistory.builder()
                     .shopNo(FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO)
                     .transferDate(cached.getTransferDate())
                     .sourceFilename(cached.getFileName())
                     .csvFilename("applied_" + yyyymmdd + ".marker")
                     .rowCount(preview.getTotalRows())
                     .totalAmount(preview.getTotalAmount())
                     .matchedCount(preview.getMatchedCount())
                     .diffCount(preview.getDiffCount())
                     .unmatchedCount(preview.getUnmatchedCount())
                     .csvBody(null)
                     .sourceFileHash(cached.getSourceFileHash())
                     .appliedAt(now)
                     .appliedByUserNo(userNo)
                     .addDateTime(now)
                     .addUserNo(userNo)
                     .build();
             historyRepository.save(h);
         } catch (Exception ex) {
             // history 保存失敗は本体検証結果に影響させない (verified_manually=true は既に永続化済)。
             log.error("applyVerification 履歴の保存に失敗 (検証反映は正常完了): file={}", cached.getFileName(), ex);
         }
     }
 
     /**
      * 同一 (shop_no, transaction_month) に対する applyVerification / exportVerifiedCsv を
      * 直列化する。PostgreSQL の {@code pg_advisory_xact_lock} は現在のトランザクション終了時に
      * 自動解放されるため、解放漏れリスクが無い。
+     *
+     * <p>G2-M2 (Codex Major #2, 2026-05-06): キー計算と native query 発行を {@link FinancePayableLock}
+     * に切り出し、{@link TAccountsPayableSummaryService#verify} / {@link ConsistencyReviewService#applyMfOverride}
+     * の 3 書込経路で同一 lock を取れるようにした。shop_no は {@link FinanceConstants#ACCOUNTS_PAYABLE_SHOP_NO}
+     * 固定 (買掛金管理は shop=1 のみ運用)。
      */
     private void acquireAdvisoryLock(LocalDate transactionMonth) {
-        long lockKey = ((long) ADVISORY_LOCK_CLASS << 32)
-                | (transactionMonth.toEpochDay() & 0xFFFF_FFFFL);
-        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:k)")
-                .setParameter("k", lockKey)
-                .getSingleResult();
+        FinancePayableLock.acquire(entityManager,
+                FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth);
     }
 
     /**
      * applyVerification 時に EXPENSE / SUMMARY / DIRECT_PURCHASE 主行および
      * {@code PAYABLE_xxx} / {@code DIRECT_PURCHASE_xxx} 副行を
      * {@code t_payment_mf_aux_row} に洗い替え保存する。
      * PAYABLE 主行は {@code t_accounts_payable_summary} 側で管理するためここでは対象外。
      * UNREGISTERED 行は CSV に出ないため保存しない。
      *
      * <p>C2 (2026-05-06) 修正: 従来 {@code PAYABLE_xxx} / {@code DIRECT_PURCHASE_xxx} 副行を
      * skip していたため、exportVerifiedCsv (DB-only 経路) で副行が消失していた。本修正で副行も保存し、
      * V038 で {@code chk_payment_mf_aux_rule_kind} 制約を拡張した。
      *
      * @param preview applyVerification で既に構築済みの preview を使い回す
      *                （再 buildPreview で N+1 を再度走らせないため）。
      */
     private void saveAuxRowsForVerification(CachedUpload cached, PaymentMfPreviewResponse preview,
                                             LocalDate txMonth, Integer userNo) {
         LocalDate transferDate = cached.getTransferDate();
         int deleted = auxRowRepository.deleteByShopAndTransactionMonthAndTransferDate(
                 FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, txMonth, transferDate);
         if (deleted > 0) {
             log.info("補助行を洗い替え: shop={} txMonth={} transferDate={} 削除={}件",
                     FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, txMonth, transferDate, deleted);
         }
 
         LocalDateTime now = LocalDateTime.now();
         int seq = 0;
         List<TPaymentMfAuxRow> toSave = new ArrayList<>();
         for (PaymentMfPreviewRow row : preview.getRows()) {
             String ruleKind = row.getRuleKind();
             if (ruleKind == null) continue;
             // C2 (2026-05-06):
             //   - PAYABLE 主行: t_accounts_payable_summary 由来 → aux 保存対象外 (重複排除)。
             //   - PAYABLE_* 副行 (PAYABLE_FEE/DISCOUNT/EARLY/OFFSET): aux 保存対象。
             //     exportVerifiedCsv で副行を再構築するため (従来は消失していた)。
             //   - DIRECT_PURCHASE 主行・副行: 共に aux 保存対象。
             //   - EXPENSE / SUMMARY 主行: 従来どおり aux 保存対象。
             //   - UNREGISTERED 行: CSV に出ないため保存しない。
             // V038 で chk_payment_mf_aux_rule_kind 制約を拡張済。
             if ("PAYABLE".equals(ruleKind)) continue;
             if ("UNREGISTERED".equals(row.getErrorType())) continue;
             if (row.getAmount() == null) continue;
 
             toSave.add(TPaymentMfAuxRow.builder()
                     .shopNo(FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO)
                     .transactionMonth(txMonth)
                     .transferDate(transferDate)
                     .ruleKind(ruleKind)
                     .sequenceNo(seq++)
                     .sourceName(row.getSourceName())
                     .paymentSupplierCode(row.getPaymentSupplierCode())
                     .amount(BigDecimal.valueOf(row.getAmount()))
                     .debitAccount(row.getDebitAccount())
                     .debitSubAccount(row.getDebitSubAccount())
                     .debitDepartment(row.getDebitDepartment())
                     .debitTax(row.getDebitTax())
                     .creditAccount(row.getCreditAccount())
                     .creditSubAccount(row.getCreditSubAccount())
                     .creditDepartment(row.getCreditDepartment())
                     .creditTax(row.getCreditTax())
                     .summary(row.getSummary())
                     .tag(row.getTag())
                     .sourceFilename(cached.getFileName())
                     .addDateTime(now)
                     .addUserNo(userNo)
                     .build());
         }
         if (!toSave.isEmpty()) {
             auxRowRepository.saveAll(toSave);
         }
         log.info("補助行を保存: shop={} txMonth={} transferDate={} 追加={}件",
                 FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, txMonth, transferDate, toSave.size());
     }
 
     @Data
     @Builder
     public static class VerifyResult {
         private LocalDate transferDate;
         private LocalDate transactionMonth;
         private int matchedCount;
         private int diffCount;
         private int notFoundCount;
         private int skippedCount;
         /**
          * P1-08 Q3-(ii): verified_manually=true (単一 verify 由来) で保護されスキップされた supplier 数。
          * &gt;0 のとき UI で「N 件は手動確定保護のため上書きしませんでした」を表示する。
          */
         private int skippedManuallyVerifiedCount;
         private List<String> unmatchedSuppliers;
     }
 
     public byte[] getHistoryCsv(Integer id) {
         return historyRepository.findById(id)
                 .map(TPaymentMfImportHistory::getCsvBody)
                 .orElseThrow(() -> new IllegalArgumentException("履歴が見つかりません: " + id));
     }
 
     public java.util.Optional<LocalDate> getHistoryTransferDate(Integer id) {
         return historyRepository.findById(id)
                 .map(TPaymentMfImportHistory::getTransferDate);
     }
 
     /**
      * SF-C20: ダウンロード時のファイル名生成用に、指定 uploadId の送金日を返す。
      * 期限切れ・未登録の場合は {@link IllegalArgumentException}。
      */
     public LocalDate getCachedTransferDate(String uploadId) {
         return getCached(uploadId).getTransferDate();
     }
 
     /**
      * BigDecimal を long に変換する（小数点以下は切り捨て）。
      * 手入力由来の verifiedAmount などで scale&gt;0 が混入しても例外にならないよう
      * 明示的に {@link RoundingMode#DOWN} で丸める。
      */
     private static long toLongFloor(BigDecimal v) {
         return v.setScale(0, RoundingMode.DOWN).longValueExact();
     }
 
     /**
      * 同一 supplier_no に属する税率別行群から、MF出力用の合計額を算出する (G2-M1, V040 で source 列ベースに改修)。
      * <p>判定は {@code verification_source} 列で行う:
      * <ul>
      *   <li><b>全行 BULK_VERIFICATION</b>: 振込明細一括検証で全税率行に同値の集約値が冗長保持されるため、
      *       代表 1 行の値を採用する (SUM すると件数倍の重複になる)。
-     *       不変条件として全行同値のはずだが、念のため per-row 値を比較し、不一致なら WARN ログ +
-     *       SUM フォールバック (DB 直接 UPDATE などの異常検知)。</li>
+     *       不変条件として全行同値のはずだが、念のため per-row 値を比較し、不一致なら
+     *       <b>{@link FinanceInternalException} を throw して fail-closed</b> する
+     *       (Codex Critical #1, 2026-05-06)。</li>
      *   <li><b>1 行でも MANUAL_VERIFICATION / MF_OVERRIDE / NULL が混在</b>: 税率別に異なる値が入りうるため SUM。
      *       MANUAL は単一 PK 更新、MF_OVERRIDE は税率別按分で書込まれているため、SUM が正しい集約値となる。</li>
      * </ul>
      * <p>旧実装は「全行 verifiedAmount 一致 → 代表値、不一致 → SUM」と金額パターンで推定していたが、
      * (a) MANUAL で偶然全行同値の場合に過少計上、(b) BULK 後の単行修正で過大計上のリスクがあった。
      * V040 で書込経路を {@code verification_source} 列に明示記録する運用に切替。
      * <p>verifiedAmount が null の行は taxIncludedAmountChange にフォールバック。
+     *
+     * <p><b>Codex Critical #1 (2026-05-06)</b>: 旧版は BULK 不一致時に WARN ログ + SUM フォールバックとして
+     * いたが、SUM すると税率行数分に金額が膨らむ (= 過大計上で MF CSV に流出) ため、運用上は隔離すべき
+     * 異常として {@link FinanceInternalException} を throw する。client には 422 で「内部エラー」を返し、
+     * admin が DB 状態を点検 + 修復するまで CSV 生成を停止させる。
+     * 関連 runbook: {@code claudedocs/runbook-payment-mf-bulk-invariant-violation.md}
      */
     private static long sumVerifiedAmountForGroup(List<TAccountsPayableSummary> group) {
         if (group.isEmpty()) return 0L;
 
         List<Long> perRow = new ArrayList<>(group.size());
         for (TAccountsPayableSummary s : group) {
             BigDecimal v = s.getVerifiedAmount() != null ? s.getVerifiedAmount()
                     : s.getTaxIncludedAmountChange();
             perRow.add(v == null ? 0L : toLongFloor(v));
         }
 
         // 全行 BULK_VERIFICATION 由来かを source 列で判定 (G2-M1)。
         // null 行 (未検証) や MANUAL / MF_OVERRIDE が 1 行でも混じれば SUM。
         boolean allBulk = group.stream()
                 .allMatch(s -> FinanceConstants.VERIFICATION_SOURCE_BULK.equals(s.getVerificationSource()));
         if (allBulk) {
             long first = perRow.get(0);
             boolean allSame = perRow.stream().allMatch(x -> x == first);
             if (allSame) {
                 // 正常: 集約値冗長保持
                 return first;
             }
             // 異常: BULK_VERIFICATION の不変条件 (全行同値) が崩れている。
-            // DB 直接 UPDATE 等の運用異常を検知し、過大化警戒として SUM フォールバック。
-            log.warn("BULK_VERIFICATION 不変条件違反: 同 (shop, supplier, txMonth) で verified_amount 不一致"
-                    + " supplier_no={} txMonth={} perRow={}",
-                    group.get(0).getSupplierNo(), group.get(0).getTransactionMonth(), perRow);
-            return perRow.stream().mapToLong(Long::longValue).sum();
+            // 旧版は SUM フォールバックしていたが、SUM は税率行数分の過大計上になり MF CSV 出力に流出するため、
+            // Codex Critical #1 (2026-05-06) で fail-closed (例外 throw) に変更。
+            // FinanceInternalException → FinanceExceptionHandler が 422 + 汎用メッセージで応答し、
+            // CSV 生成 / verified export を停止させる。admin は runbook に従い DB 状態を点検して修復する。
+            Integer supplierNo = group.get(0).getSupplierNo();
+            LocalDate txMonth = group.get(0).getTransactionMonth();
+            throw new FinanceInternalException(
+                    "BULK_VERIFICATION 不変条件違反 (全税率行同値想定だが不一致): supplier_no="
+                            + supplierNo + " txMonth=" + txMonth + " perRow=" + perRow);
         }
 
         // MANUAL / MF_OVERRIDE / NULL 混在: 税率別 SUM (本来の集約値)
         return perRow.stream().mapToLong(Long::longValue).sum();
     }
 
     /** P1-09 テスト用: {@link #sumVerifiedAmountForGroup} を package 外から呼ぶためのフック。 */
     static long sumVerifiedAmountForGroupForTest(List<TAccountsPayableSummary> group) {
         return sumVerifiedAmountForGroup(group);
     }
 
     /**
      * G2-M10 (V040, 2026-05-06): 同 supplier × txMonth の税率別行群のうち、
      * 1 行でも MANUAL_VERIFICATION (UI 手入力 verify) があるかを判定する。
      * <p>true の場合、再 upload (applyVerification) は当該 supplier 全体を保護対象としてスキップする。
      * <p>判定は {@code verification_source} 列で行い、verification_note 接頭辞には依存しない
      * (旧実装は note 文字列推定で偽判定リスクがあった)。
      */
     static boolean isAnyManuallyLocked(List<TAccountsPayableSummary> group) {
         return group.stream().anyMatch(s ->
                 Boolean.TRUE.equals(s.getVerifiedManually())
                         && FinanceConstants.VERIFICATION_SOURCE_MANUAL.equals(s.getVerificationSource()));
     }
 
     // ===========================================================
     // 検証済み買掛金からの MF CSV 出力 (Excel 再アップロード不要)
     // ===========================================================
 
     @Data
     @Builder
     public static class VerifiedExportResult {
         private byte[] csv;
         private int rowCount;
         private int payableCount;
         private int auxCount;
         private long totalAmount;
         /** ルール未登録で CSV 行を生成できず除外した supplier_code + supplier_name */
         private List<String> skippedSuppliers;
         private LocalDate transactionMonth;
     }
 
     /**
      * {@code t_accounts_payable_summary} の「検証結果=一致 かつ MF出力=ON」行から、
      * Excel 再アップロード無しで MF 仕訳 CSV を生成する。
      * <p>生成される CSV は <b>PAYABLE(買掛金)行のみ</b>。振込明細 Excel 由来の
      * 費用仕訳 (EXPENSE) / 直接仕入高 (DIRECT_PURCHASE) / 振込手数料値引・早払収益 (SUMMARY)
      * は DB に保持されていないため含まれない。それらが必要な場合は Excel 取込フローを使うこと。
      * <p>CSV「取引日」列は 小田光の締め日 = {@code transactionMonth}(前月20日) 固定。
      * 支払日(送金日)は MF の銀行データ連携で自動付与されるため CSV には含めない。
      * <p>SF-C21: 同一 (shop_no, transactionMonth) に対する {@link #applyVerification} と本メソッドは
      * advisory lock {@code pg_advisory_xact_lock} で直列化される。advisory lock は
      * {@link Transactional @Transactional} 境界で自動解放されるため、対象 0 件等で early return
      * しても解放漏れはない (PostgreSQL 仕様)。
      *
      * @param transactionMonth 対象取引月 (例: 2026-01-20)。CSV 取引日列にも使用。
      * @param userNo           履歴保存用ユーザ番号
      */
     @Transactional
     public VerifiedExportResult exportVerifiedCsv(LocalDate transactionMonth, Integer userNo) {
         if (transactionMonth == null) throw new IllegalArgumentException("transactionMonth が未指定です");
         acquireAdvisoryLock(transactionMonth);
 
         List<TAccountsPayableSummary> dbRows = payableRepository.findVerifiedForMfExport(
                 FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth);
         List<TPaymentMfAuxRow> auxRows = auxRowRepository
                 .findByShopNoAndTransactionMonthOrderByTransferDateAscSequenceNoAsc(
                         FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth);
 
         if (dbRows.isEmpty() && auxRows.isEmpty()) {
             throw new IllegalArgumentException(
                     "対象取引月(" + transactionMonth + ")に出力対象データがありません"
                     + "（一致・MF出力ONの買掛金 0件、補助行 0件）");
         }
 
         // supplier_no 単位に集約する。verified_amount は振込明細一括検証時は税率別同値だが、
         // 手動 verify では税率別に異なる値が入り得るため、代表1行ではなく税率横断で SUM する。
         Map<Integer, List<TAccountsPayableSummary>> bySupplierNo = dbRows.stream()
                 .collect(Collectors.groupingBy(TAccountsPayableSummary::getSupplierNo, LinkedHashMap::new, Collectors.toList()));
 
         // ルールを payment_supplier_code で引けるように
@@ -1212,166 +1285,181 @@ public class PaymentMfImportService {
                 // 各 section の合計行 summary と独立に chk3 を判定する (G2-M3)。
                 long[] acc = perSection.get(e.section);
                 acc[0] += mainCreditAmount; // transfer
                 acc[1] += feeAmt;           // fee
                 acc[2] += discountAmt;      // discount
                 acc[3] += earlyAmt;         // early
                 acc[4] += offsetAmt;        // offset
                 acc[5] += e.amount;         // invoice (請求額)
             }
 
             b.ruleKind(rule.getRuleKind())
                     .debitAccount(rule.getDebitAccount())
                     .debitSubAccount(rule.getDebitSubAccount())
                     .debitDepartment(rule.getDebitDepartment())
                     .debitTax(rule.getDebitTaxCategory())
                     .debitAmount(e.amount)
                     .creditAccount(rule.getCreditAccount())
                     .creditSubAccount(rule.getCreditSubAccount())
                     .creditDepartment(rule.getCreditDepartment())
                     .creditTax(rule.getCreditTaxCategory())
                     .creditAmount(needsSubRows ? mainCreditAmount : e.amount)
                     .summary(renderSummary(rule, e))
                     .tag(rule.getTag());
 
             // 突合用コードは Excel 側を優先、無ければルール側にバックフィル済みのコードを使用
             String reconcileCode = e.supplierCode != null ? e.supplierCode
                     : (rule.getPaymentSupplierCode() != null && !rule.getPaymentSupplierCode().isBlank()
                             ? rule.getPaymentSupplierCode() : null);
             // PAYABLE ルール自体に payment_supplier_code が未設定なら、検証済みCSV出力 時に
             // t_accounts_payable_summary.supplier_code でルールを逆引き出来ず CSV 除外される。
             // 一括検証前に「支払先コード自動補完」でマスタ整備を促すため一覧化する。
             boolean ruleHasCode = rule.getPaymentSupplierCode() != null
                     && !rule.getPaymentSupplierCode().isBlank();
             if ("PAYABLE".equals(rule.getRuleKind()) && !ruleHasCode) {
                 String code = e.supplierCode != null ? e.supplierCode : "code未設定";
                 rulesMissingCode.add(code + " " + e.sourceName);
             }
             if ("PAYABLE".equals(rule.getRuleKind()) && txMonth != null && reconcileCode != null) {
                 // 事前取得済みの payablesByCode から参照 (B-W11 N+1 解消)。
                 ReconcileResult rr = reconcileFromPayables(
                         payablesByCode.get(reconcileCode), e.amount);
                 b.matchStatus(rr.status).payableAmount(rr.payableAmount)
                         .payableDiff(rr.diff).supplierNo(rr.supplierNo);
                 if ("MATCHED".equals(rr.status)) matched++;
                 else if ("DIFF".equals(rr.status)) diff++;
                 else unmatched++;
             } else {
                 b.matchStatus("NA");
             }
             rows.add(b.build());
 
             // P1-03 案 D-2 / P1-07 案 D: 5日払い PAYABLE / 20日払い DIRECT_PURCHASE 副行を amount>0 のとき追加。
             // PAYABLE 系: 借方=買掛金 (親 PAYABLE と同じ)、貸方=該当勘定
             // DIRECT_PURCHASE 系: 借方=仕入高 (親 DIRECT_PURCHASE と同じ)、貸方=該当勘定
             // supplier_code/sourceName は親と同じ。
             if (needsSubRows) {
                 String feeKind = isDirectPurchase ? "DIRECT_PURCHASE_FEE" : "PAYABLE_FEE";
                 String discountKind = isDirectPurchase ? "DIRECT_PURCHASE_DISCOUNT" : "PAYABLE_DISCOUNT";
                 String earlyKind = isDirectPurchase ? "DIRECT_PURCHASE_EARLY" : "PAYABLE_EARLY";
                 String offsetKind = isDirectPurchase ? "DIRECT_PURCHASE_OFFSET" : "PAYABLE_OFFSET";
                 if (feeAmt > 0L) {
                     rows.add(buildAttributeSubRow(rule, e, feeKind, feeAmt,
                             "仕入値引・戻し高", "物販事業部", "課税仕入-返還等 10%",
                             "振込手数料値引／" + e.sourceName));
                 }
                 if (discountAmt > 0L) {
                     rows.add(buildAttributeSubRow(rule, e, discountKind, discountAmt,
                             "仕入値引・戻し高", "物販事業部", "課税仕入-返還等 10%",
                             "値引／" + e.sourceName));
                 }
                 if (earlyAmt > 0L) {
                     rows.add(buildAttributeSubRow(rule, e, earlyKind, earlyAmt,
                             "早払収益", "物販事業部", "非課税売上",
                             "早払収益／" + e.sourceName));
                 }
                 if (offsetAmt > 0L) {
                     // G2-M8: OFFSET 副行貸方科目はマスタ管理に移行 (m_offset_journal_rule)。
                     // 税理士確認後に admin が UI から値を変更可能。
                     // V041 seed では従来ハードコード値 (仕入値引・戻し高 / 物販事業部 /
                     // 課税仕入-返還等 10%) と同一の値を投入しているため migration 適用直後は挙動不変。
+                    //
+                    // Codex Major fix: マスタ欠落 (将来 shop_no=2 追加 / admin UI で誤って論理削除)
+                    // による preview/apply ブロックを防ぐため、orElseThrow ではなく hardcoded default で
+                    // フォールバック (= V041 seed と同値) し、WARN ログで運用者に通知する。
+                    // 二段防御として MOffsetJournalRuleService.delete() で「最後の active 行」削除を禁止する。
                     MOffsetJournalRule offsetRule = offsetJournalRuleRepository
                             .findByShopNoAndDelFlg(FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, "0")
-                            .orElseThrow(() -> new IllegalStateException(
-                                    "m_offset_journal_rule の shop_no="
-                                            + FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO
-                                            + " (active) 行が未登録です"));
+                            .orElseGet(() -> {
+                                log.warn("m_offset_journal_rule の shop_no={} (active) 行が未登録のため"
+                                        + " hardcoded default (V041 seed と同値) で fallback します。"
+                                        + " admin UI から正規行を再投入してください。",
+                                        FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO);
+                                return MOffsetJournalRule.builder()
+                                        .shopNo(FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO)
+                                        .creditAccount("仕入値引・戻し高")
+                                        .creditDepartment("物販事業部")
+                                        .creditTaxCategory("課税仕入-返還等 10%")
+                                        .summaryPrefix("相殺／")
+                                        .delFlg("0")
+                                        .build();
+                            });
                     rows.add(buildAttributeSubRow(rule, e, offsetKind, offsetAmt,
                             offsetRule.getCreditAccount(),
                             offsetRule.getCreditDepartment(),
                             offsetRule.getCreditTaxCategory(),
                             offsetRule.getSummaryPrefix() + e.sourceName));
                 }
             }
         }
 
         // P1-03 案 D: 旧 SUMMARY 集約 (振込手数料値引/早払収益 合計仕訳 2 行) を撤去。
         // 代わりに supplier 別に展開された PAYABLE_FEE / PAYABLE_EARLY 行で同等の会計表現になる。
 
         // 整合性チェック (1 円も許容しない)
         // G2-M3: 旧実装は最初の合計行 (5日払い) のみ summary を捕まえて以後の合計行 (20日払い) を捨てていた。
         // 現在は section 別 summary を持つので、section ごとに chk1/chk3 を判定し、
         // mismatch を per-supplier 違反一覧に [5日払い] / [20日払い] 接頭辞付きで追記する。
         // UI/DTO 互換性のため、AmountReconciliation の summary* 系フィールドは
         // 両 section 合算値 (= 旧 v1 ParsedExcel 構造と同一意味) を返す。
         PaymentMfExcelParser.SectionSummary sum5 = summaryOf(cached, PaymentMfSection.PAYMENT_5TH);
         PaymentMfExcelParser.SectionSummary sum20 = summaryOf(cached, PaymentMfSection.PAYMENT_20TH);
 
         long summary5Fee = summaryLong(sum5, s -> s.sourceFee);
         long summary5Early = summaryLong(sum5, s -> s.earlyPayment);
         long summary5Transfer = summaryLong(sum5, s -> s.transferAmount);
         long summary5Invoice = summaryLong(sum5, s -> s.invoiceTotal);
         long summary20Fee = summaryLong(sum20, s -> s.sourceFee);
         long summary20Early = summaryLong(sum20, s -> s.earlyPayment);
         long summary20Transfer = summaryLong(sum20, s -> s.transferAmount);
         long summary20Invoice = summaryLong(sum20, s -> s.invoiceTotal);
 
         long summaryFee = summary5Fee + summary20Fee;
         long summaryEarly = summary5Early + summary20Early;
         long summaryTransfer = summary5Transfer + summary20Transfer;
         long summaryInvoice = summary5Invoice + summary20Invoice;
 
         // チェック1 (G2-M3: section 別判定): C(請求額) - F(振込手数料) - H(早払) == E(振込金額)
         // section が空 (= 5日払いのみの Excel で 20日払い summary なし) の場合は 0 + 0 で自動 match。
         long expected5Transfer = summary5Invoice - summary5Fee - summary5Early;
         long expected20Transfer = summary20Invoice - summary20Fee - summary20Early;
         long excel5Diff = sum5 == null ? 0L : (summary5Transfer - expected5Transfer);
         long excel20Diff = sum20 == null ? 0L : (summary20Transfer - expected20Transfer);
         // UI 互換: 旧フィールド (excelDifference/expectedTransferAmount) は両 section 合算値で返す。
         long expectedTransfer = expected5Transfer + expected20Transfer;
         long excelDifference = excel5Diff + excel20Diff;
         // section 別判定: 片方でも非ゼロなら NG (合算で偶然打ち消し合う旧 BUG を防ぐ)。
         boolean excelMatched = excel5Diff == 0 && excel20Diff == 0;
         if (sum5 != null && excel5Diff != 0) {
             log.warn("[5日払い] 合計行 列間整合違反: C(請求){} - F(送料相手){} - H(早払){} = {} != E(振込){} (差={})",
                     summary5Invoice, summary5Fee, summary5Early, expected5Transfer, summary5Transfer, excel5Diff);
         }
         if (sum20 != null && excel20Diff != 0) {
             log.warn("[20日払い] 合計行 列間整合違反: C(請求){} - F(送料相手){} - H(早払){} = {} != E(振込){} (差={})",
                     summary20Invoice, summary20Fee, summary20Early, expected20Transfer, summary20Transfer, excel20Diff);
         }
 
         // チェック2: 明細行の読取り整合 — sum(5日払い 明細 請求額) == 5日払い 合計行 C
         // (20日払い側は DIRECT_PURCHASE 扱いで preTotalAmount に含まれないため、
         //  preTotalAmount は 5日払い summary との突合のみで意味を持つ)。
         long readDifference = preTotalAmount - summary5Invoice;
         boolean readMatched = readDifference == 0;
 
         // P1-03 案 D チェック3 (G2-M3 で section 別化):
         // 5日払い per-supplier 振込金額合計 == 5日払い 合計行 E
         // 20日払い per-supplier 振込金額合計 == 20日払い 合計行 E
         // ※ EXPENSE rule の行は needsSubRows=false で per-supplier accumulator に入らないため、
         //   EXPENSE 主体の section では合算が summary より少なくなる (= 旧来からの既知の限界)。
         //   従来同様、ここでは boolean フラグだけ立てて perSupplierMismatches へは追加しない。
         long[] s5 = perSection.get(PaymentMfSection.PAYMENT_5TH);
         long[] s20 = perSection.get(PaymentMfSection.PAYMENT_20TH);
         long perSupplier5TransferDiff = sum5 == null ? 0L : (s5[0] - summary5Transfer);
         long perSupplier20TransferDiff = sum20 == null ? 0L : (s20[0] - summary20Transfer);
         long sumPerSupplierTransfer = s5[0] + s20[0];
         long sumPerSupplierFee = s5[1] + s20[1];
         long sumPerSupplierDiscount = s5[2] + s20[2];
         long sumPerSupplierEarly = s5[3] + s20[3];
         long sumPerSupplierOffset = s5[4] + s20[4];
         long perSupplierTransferDiff = perSupplier5TransferDiff + perSupplier20TransferDiff;
         boolean perSupplierTransferMatched = perSupplier5TransferDiff == 0 && perSupplier20TransferDiff == 0;
         if (sum5 != null && perSupplier5TransferDiff != 0) {
             log.warn("[5日払い] 全体振込整合違反: Σ supplier 振込{} != E(合計振込){} (差={})",
diff --git a/frontend/components/pages/finance/payment-mf-import.tsx b/frontend/components/pages/finance/payment-mf-import.tsx
index b73d9e1..71be900 100644
--- a/frontend/components/pages/finance/payment-mf-import.tsx
+++ b/frontend/components/pages/finance/payment-mf-import.tsx
@@ -1,182 +1,197 @@
 'use client'
 
 import { useState } from 'react'
 import { useMutation, useQueryClient } from '@tanstack/react-query'
 import { api } from '@/lib/api-client'
 import { handleApiError } from '@/lib/api-error-handler'
 import { Button } from '@/components/ui/button'
 import { Input } from '@/components/ui/input'
 import { Label } from '@/components/ui/label'
+import { Textarea } from '@/components/ui/textarea'
 import { PageHeader } from '@/components/features/common/PageHeader'
 import { ConfirmDialog } from '@/components/features/common/ConfirmDialog'
 import {
-  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
+  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogFooter,
 } from '@/components/ui/dialog'
 import {
   Select, SelectTrigger, SelectValue, SelectContent, SelectItem,
 } from '@/components/ui/select'
 import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
 import { Checkbox } from '@/components/ui/checkbox'
-import { Download, Upload, AlertCircle, AlertTriangle, Scale, History, CheckCheck, ShieldAlert } from 'lucide-react'
+import { Download, Upload, AlertCircle, AlertTriangle, Scale, History, CheckCheck, ShieldAlert, FileDown } from 'lucide-react'
 import Link from 'next/link'
 import { toast } from 'sonner'
 import type {
   PaymentMfPreviewResponse, PaymentMfPreviewRow,
   PaymentMfRule, PaymentMfRuleRequest, RuleKind,
   PaymentMfVerifyResult,
 } from '@/types/payment-mf'
 import { RULE_KINDS, TAX_CATEGORIES } from '@/types/payment-mf'
 import { AmountSourceTooltip } from '@/components/common/AmountSourceTooltip'
 
 type RuleDialogState = { sourceName: string; amount: number | null } | null
 
 export default function PaymentMfImportPage() {
   const queryClient = useQueryClient()
   const [file, setFile] = useState<File | null>(null)
   const [preview, setPreview] = useState<PaymentMfPreviewResponse | null>(null)
   const [ruleDialog, setRuleDialog] = useState<RuleDialogState>(null)
   const [form, setForm] = useState<PaymentMfRuleRequest>(blankRuleRequest())
   const [confirmVerify, setConfirmVerify] = useState(false)
   // G2-M2: per-supplier 1 円不一致がある時に「強制実行に同意」のチェック必須
   const [forceAcknowledged, setForceAcknowledged] = useState(false)
+  // G2-M2 (Frontend Major): force apply 専用ダイアログ。
+  // 100 件省略表示で承認内容と実処理が乖離するリスクを抑えるため、
+  // CSV ダウンロード + 全件確認 checkbox + 反映理由 textarea を必須化する。
+  const [forceDialogOpen, setForceDialogOpen] = useState(false)
+  const [reviewedAll, setReviewedAll] = useState(false)
+  const [forceReason, setForceReason] = useState('')
 
   const previewMut = useMutation({
     mutationFn: async (f: File) => {
       const fd = new FormData()
       fd.append('file', f)
       return api.postForm<PaymentMfPreviewResponse>('/finance/payment-mf/preview', fd)
     },
     onSuccess: (r) => {
       setPreview(r)
       if (r.errorCount === 0) toast.success(`プレビュー成功: ${r.totalRows}件`)
       else toast.warning(`マスタ未登録 ${r.errorCount} 件。登録後に再プレビューしてください`)
     },
     onError: (e) => handleApiError(e, { fallbackMessage: 'プレビュー失敗' }),
   })
 
   const rePreviewMut = useMutation({
     mutationFn: async (uploadId: string) =>
       api.post<PaymentMfPreviewResponse>(`/finance/payment-mf/preview/${uploadId}`),
     onSuccess: (r) => {
       setPreview(r)
       if (r.errorCount === 0) toast.success('すべてのエラーが解消されました')
     },
     onError: (e) => handleApiError(e, { fallbackMessage: '再プレビュー失敗' }),
   })
 
   const createRuleMut = useMutation({
     mutationFn: async (req: PaymentMfRuleRequest) =>
       api.post<PaymentMfRule>('/finance/payment-mf/rules', req),
     onSuccess: () => {
       queryClient.invalidateQueries({ queryKey: ['payment-mf-rules'] })
       toast.success('ルールを追加しました')
       setRuleDialog(null)
       setForm(blankRuleRequest())
       // 再プレビューが既に走っている場合はスキップ（連続ルール追加時のフリッカ防止）
       if (preview && !rePreviewMut.isPending) rePreviewMut.mutate(preview.uploadId)
     },
     onError: (e) => handleApiError(e, { fallbackMessage: 'ルール登録失敗' }),
   })
 
   const verifyMut = useMutation({
-    mutationFn: async (args: { uploadId: string; force: boolean }) =>
-      api.post<PaymentMfVerifyResult>(`/finance/payment-mf/verify/${args.uploadId}`, {
-        force: args.force,
-      }),
+    mutationFn: async (args: { uploadId: string; force: boolean; forceReason?: string }) => {
+      // backend が `forceReason` 未対応でも Spring の default Jackson 設定 (FAIL_ON_UNKNOWN_PROPERTIES=false) で無視される。
+      // 別エージェントが backend 側の必須化を進めている前提で、force=true 時は常に送る。
+      const body: { force: boolean; forceReason?: string } = { force: args.force }
+      if (args.force && args.forceReason) body.forceReason = args.forceReason
+      return api.post<PaymentMfVerifyResult>(
+        `/finance/payment-mf/verify/${args.uploadId}`,
+        body,
+      )
+    },
     onSuccess: (r, vars) => {
       const baseMsg =
         `買掛金一覧に反映しました（一致 ${r.matchedCount} / 差異 ${r.diffCount} / 買掛金なし ${r.notFoundCount}）`
       // P1-08 Q3-(ii): 手動確定保護でスキップされた supplier があれば別途通知
       if (r.skippedManuallyVerifiedCount > 0) {
         toast.success(baseMsg)
         toast.warning(
           `手動確定済の ${r.skippedManuallyVerifiedCount} 件は保護のため上書きしませんでした`
         )
       } else {
         toast.success(baseMsg)
       }
       if (vars.force) {
         toast.warning('per-supplier 1 円不一致を強制反映しました（audit log に記録済）')
       }
       // 反映成功後は force 同意状態をリセット
       setForceAcknowledged(false)
+      setReviewedAll(false)
+      setForceReason('')
     },
     // G3-M12: PER_SUPPLIER_MISMATCH 等の business code は handleApiError で個別誘導
     onError: (e) => handleApiError(e, { fallbackMessage: '反映失敗' }),
   })
 
   const download = async () => {
     if (!preview) return
     try {
       const { blob, filename } = await api.downloadPost(
         `/finance/payment-mf/convert/${preview.uploadId}`
       )
       const date = preview.transferDate?.replaceAll('-', '') ?? 'unknown'
       const suggest = filename ?? `買掛仕入MFインポートファイル_${date}.csv`
       const a = document.createElement('a')
       const url = URL.createObjectURL(blob)
       a.href = url
       a.download = suggest
       document.body.appendChild(a)
       a.click()
       document.body.removeChild(a)
       // revoke は非同期ダウンロード完了を待つ（同期 revoke は大容量 CSV で稀に失敗する）
       setTimeout(() => URL.revokeObjectURL(url), 1000)
       toast.success('CSVをダウンロードしました')
     } catch (e) {
       handleApiError(e, { fallbackMessage: 'ダウンロード失敗' })
     }
   }
 
   const openRuleDialog = (row: PaymentMfPreviewRow) => {
     setForm({
       ...blankRuleRequest(),
       sourceName: row.sourceName ?? '',
       paymentSupplierCode: row.paymentSupplierCode ?? undefined,
       ruleKind: row.paymentSupplierCode ? 'PAYABLE' : 'EXPENSE',
       debitAccount: row.paymentSupplierCode ? '買掛金' : '荷造運賃',
       debitTaxCategory: row.paymentSupplierCode ? '対象外' : '課税仕入 10%',
       summaryTemplate: '{source_name}',
     })
     setRuleDialog({ sourceName: row.sourceName ?? '', amount: row.amount })
   }
 
   return (
     <div className="space-y-4">
       <PageHeader
         title="買掛仕入MF変換"
         actions={
           <>
             <Button asChild variant="outline" size="sm">
               <Link href="/finance/payment-mf-rules">
                 <Scale className="mr-1 h-4 w-4" />
                 ルールマスタ
               </Link>
             </Button>
             <Button asChild variant="outline" size="sm">
               <Link href="/finance/payment-mf-history">
                 <History className="mr-1 h-4 w-4" />
                 変換履歴
               </Link>
             </Button>
           </>
         }
       />
 
       <div className="rounded border p-4 space-y-3">
         <Label>振込み明細Excelファイル（.xlsx）</Label>
         <div className="flex items-center gap-2">
           <Input
             type="file"
             accept=".xlsx"
             onChange={(e) => setFile(e.target.files?.[0] ?? null)}
             className="max-w-md"
           />
           <Button
             onClick={() => file && previewMut.mutate(file)}
             disabled={!file || previewMut.isPending}
           >
             <Upload className="mr-1 h-4 w-4" />
             {previewMut.isPending ? '解析中...' : 'プレビュー'}
           </Button>
         </div>
@@ -210,211 +225,239 @@ export default function PaymentMfImportPage() {
             <li>
               対象シート: 20日払い＝「支払い明細」 / 5日払い＝「振込明細」。shop_no=1 固定。
             </li>
             <li>
               同じ月を2回読み込むと <b>買掛金一覧への反映（手動確定）が上書き</b>されます。
               二重取込しないようご注意ください。
             </li>
           </ul>
         </div>
       </div>
 
       {preview && (
         <>
           {/* P1-08 L1: 同一ハッシュ Excel 過去取込の警告 */}
           {preview.duplicateWarning && (
             <Alert className="border-amber-500 bg-amber-50">
               <AlertTriangle className="h-4 w-4 text-amber-700" />
               <AlertTitle className="text-amber-800">
                 同一内容のファイルが既に取込済です
               </AlertTitle>
               <AlertDescription className="text-amber-900/90">
                 <div>
                   前回取込: {formatDateTime(preview.duplicateWarning.previousUploadedAt)}
                   {preview.duplicateWarning.previousFilename && (
                     <> （ファイル: <code className="rounded bg-white px-1">{preview.duplicateWarning.previousFilename}</code>）</>
                   )}
                 </div>
                 <div>
                   ハッシュが一致しているため <b>内容が完全に同じ</b> です。
                   修正版でない場合は重複取込の可能性があります。
                 </div>
               </AlertDescription>
             </Alert>
           )}
 
           {/* P1-08 L2: 同 (shop, transferDate) 確定済の警告 */}
           {preview.appliedWarning && (
             <Alert variant="destructive" className="border-red-400 bg-red-50">
               <AlertCircle className="h-4 w-4" />
               <AlertTitle>この月は既に確定済です</AlertTitle>
               <AlertDescription>
                 <div>
                   確定日時: {formatDateTime(preview.appliedWarning.appliedAt)}
                   （取引月: <b>{preview.appliedWarning.transactionMonth}</b> / 振込日: <b>{preview.appliedWarning.transferDate}</b>）
                 </div>
                 <div>
                   再確定すると <b>確定済の値を上書き</b> します。
                   ただし単一仕入先で <code>verified_manually=true</code> で個別確定された行は <b>保護のため上書きされません</b>。
                 </div>
                 <div>
                   続行する場合は下の「買掛金一覧へ反映」を確認した上で実行してください。
                 </div>
               </AlertDescription>
             </Alert>
           )}
 
           {/*
             G2-M2 (2026-05-06): per-supplier 1 円整合性違反の警告 + 強制実行同意 UI。
             違反 1 件以上で「買掛金一覧へ反映」「CSVダウンロード」をブロックし、
             checkbox にチェック後にのみ force=true で実行可能。
           */}
           {(preview.amountReconciliation?.perSupplierMismatches?.length ?? 0) > 0 && (
             <Alert variant="destructive" className="border-red-500 bg-red-50">
               <ShieldAlert className="h-4 w-4" />
               <AlertTitle>
                 per-supplier 1 円整合性違反 (
                 {preview.amountReconciliation!.perSupplierMismatches.length} 件)
               </AlertTitle>
               <AlertDescription>
                 <div className="mb-2">
                   以下の仕入先で <b>請求額 ≠ 振込 + 送料 + 値引 + 早払 + 相殺</b> となっています。
                   Excel 入力ミスの可能性が高いため、原則は経理に連絡し
                   <b>振込明細 Excel を修正してから再アップロード</b> してください。
                 </div>
                 <div className="max-h-48 overflow-auto rounded border border-red-200 bg-white p-2 font-mono text-xs leading-snug">
                   {preview.amountReconciliation!.perSupplierMismatches.slice(0, 100).map((m, i) => (
                     <div key={i} className="break-all">{m}</div>
                   ))}
                   {preview.amountReconciliation!.perSupplierMismatches.length > 100 && (
                     <div className="text-muted-foreground">
-                      ...（残り {preview.amountReconciliation!.perSupplierMismatches.length - 100} 件は省略）
+                      ...（残り {preview.amountReconciliation!.perSupplierMismatches.length - 100} 件は省略 / 下の CSV で全件確認可）
                     </div>
                   )}
                 </div>
+                {/*
+                  G2-M2 (Frontend Major): 100 件超は UI で省略するため、全件確認のための
+                  CSV ダウンロード経路を提供。force 反映前に Excel で目視レビュー可能。
+                */}
+                <div className="mt-2 flex flex-wrap items-center gap-2">
+                  <Button
+                    type="button"
+                    variant="outline"
+                    size="sm"
+                    onClick={() => downloadMismatchesCsv(preview.amountReconciliation!.perSupplierMismatches)}
+                  >
+                    <FileDown className="mr-1 h-4 w-4" />
+                    全 {preview.amountReconciliation!.perSupplierMismatches.length} 件 CSV ダウンロード
+                  </Button>
+                  <span className="text-xs text-muted-foreground">
+                    強制反映時は全件 audit log に保存されます。
+                  </span>
+                </div>
                 <div className="mt-3 flex items-start gap-2 rounded border border-red-300 bg-red-100 p-2 text-xs">
                   <Checkbox
                     id="force-acknowledge"
                     checked={forceAcknowledged}
                     onCheckedChange={(v) => setForceAcknowledged(v === true)}
                     className="mt-0.5"
                   />
                   <Label htmlFor="force-acknowledge" className="cursor-pointer">
                     Excel 修正不能/業務承認済 のため <b>強制反映</b> する。
                     実行内容と全違反明細は <code>finance_audit_log</code> に記録されます。
                   </Label>
                 </div>
               </AlertDescription>
             </Alert>
           )}
 
           <div className="flex flex-wrap items-center justify-between gap-4 rounded border bg-card p-4">
             <div className="space-y-1 text-sm">
               <div>ファイル: <span className="font-medium">{preview.fileName}</span></div>
               <div>
                 送金日: <span className="font-medium">{preview.transferDate ?? '-'}</span>
                 {preview.transactionMonth && (
                   <> / 対応取引月: <span className="font-medium">{preview.transactionMonth}</span></>
                 )}
               </div>
               <div>
                 合計: <span className="font-medium">{preview.totalAmount.toLocaleString()}</span> 円
                 / 行数: {preview.totalRows}
               </div>
               <div className="flex gap-3 text-xs">
                 <span className="text-green-700">一致 {preview.matchedCount}</span>
                 <span className="text-amber-700">差異 {preview.diffCount}</span>
                 <span className="text-red-600">買掛金なし {preview.unmatchedCount}</span>
                 {preview.errorCount > 0 && (
                   <span className="text-red-600 font-semibold">未登録 {preview.errorCount}</span>
                 )}
               </div>
             </div>
             <div className="flex gap-2">
               <Button
                 variant={hasPerSupplierMismatch(preview) ? 'destructive' : 'outline'}
                 disabled={
                   preview.errorCount > 0
                   || verifyMut.isPending
                   || (hasPerSupplierMismatch(preview) && !forceAcknowledged)
                 }
-                onClick={() => setConfirmVerify(true)}
+                onClick={() => {
+                  // G2-M2 (Frontend Major): force 反映時は CSV download + 反映理由 + 全件確認 checkbox
+                  // を持つ専用ダイアログへ。通常確定 / 再確定は従来の ConfirmDialog を使う。
+                  if (hasPerSupplierMismatch(preview)) {
+                    setReviewedAll(false)
+                    setForceReason('')
+                    setForceDialogOpen(true)
+                  } else {
+                    setConfirmVerify(true)
+                  }
+                }}
               >
                 <CheckCheck className="mr-1 h-4 w-4" />
                 {verifyMut.isPending
                   ? '反映中...'
                   : hasPerSupplierMismatch(preview)
                     ? '強制反映 (force=true)'
                     : '買掛金一覧へ反映'}
               </Button>
               {/*
                 G2-M2: CSV ダウンロード経路には force 上書きを設けない (Excel 修正が正しい運用)。
                 per-supplier 不一致がある間は無効化する。
               */}
               <Button
                 onClick={download}
                 disabled={preview.errorCount > 0 || hasPerSupplierMismatch(preview)}
                 title={
                   hasPerSupplierMismatch(preview)
                     ? 'per-supplier 1 円不一致のため CSV 出力は不可。Excel を修正してください'
                     : undefined
                 }
               >
                 <Download className="mr-1 h-4 w-4" />
                 CSVダウンロード
               </Button>
             </div>
           </div>
 
           {preview.unregisteredSources.length > 0 && (
             <div className="rounded border border-orange-300 bg-orange-50 p-4">
               <div className="mb-2 flex items-center justify-between gap-2">
                 <div className="flex items-center gap-2 text-sm font-medium text-orange-700">
                   <AlertCircle className="h-4 w-4" />
                   マスタ未登録の送り先 ({preview.unregisteredSources.length})
                 </div>
                 <Button asChild size="sm" variant="outline">
                   <Link href="/finance/payment-mf-rules">
                     <Scale className="mr-1 h-4 w-4" />
                     マッピングマスタを確認
                   </Link>
                 </Button>
               </div>
               <p className="mb-2 text-xs text-orange-800">
                 略称違い（例: 「カミイソ ㈱」⇔「カミイソ産商 ㈱」）で未登録扱いになる場合があります。
                 まずは <Link href="/finance/payment-mf-rules" className="underline font-medium">マッピングマスタ</Link> で類似名を検索し、既存ルールの送り先名を Excel 表記に合わせて修正してください。
                 該当がなければ「ルール追加」で新規登録できます。
               </p>
               <div className="space-y-1">
                 {preview.unregisteredSources.map((c) => (
                   <div key={c} className="flex items-center justify-between gap-2 text-sm">
                     <code className="rounded bg-white px-2 py-0.5">{c}</code>
                     <div className="flex gap-2">
                       <Button asChild size="sm" variant="ghost">
                         <Link href={`/finance/payment-mf-rules?q=${encodeURIComponent(c)}`}>
                           マスタで検索
                         </Link>
                       </Button>
                       <Button
                         size="sm"
                         variant="outline"
                         onClick={() => {
                           const row = preview.rows.find((r) => r.sourceName === c)
                           if (row) openRuleDialog(row)
                         }}
                       >
                         ルール追加
                       </Button>
                     </div>
                   </div>
                 ))}
               </div>
             </div>
           )}
 
           <div className="overflow-auto rounded border">
             <table className="min-w-full text-xs">
               <thead className="sticky top-0 bg-muted">
                 <tr>
                   <th className="p-1">行</th>
                   <th className="p-1">コード</th>
                   <th className="p-1">送り先</th>
@@ -453,199 +496,304 @@ export default function PaymentMfImportPage() {
                     </td>
                     <td className="p-1">{r.debitAccount ?? ''}</td>
                     <td className="p-1">{r.debitSubAccount ?? ''}</td>
                     <td className="p-1">{r.debitTax ?? ''}</td>
                     <td className="p-1">{r.creditAccount ?? ''}</td>
                     <td className="p-1">{r.summary ?? ''}</td>
                     <td className="p-1">{r.tag ?? ''}</td>
                   </tr>
                 ))}
               </tbody>
             </table>
           </div>
         </>
       )}
 
       <Dialog open={ruleDialog !== null} onOpenChange={(o) => { if (!o) setRuleDialog(null) }}>
         <DialogContent className="max-w-2xl">
           <DialogHeader>
             <DialogTitle>マスタへルール追加</DialogTitle>
           </DialogHeader>
           <div className="grid grid-cols-2 gap-3">
             <Field label="送り先名（Excel B列）">
               <Input value={form.sourceName}
                 onChange={(e) => setForm({ ...form, sourceName: e.target.value })} />
             </Field>
             <Field label="支払先コード（買掛金の場合）">
               <Input value={form.paymentSupplierCode ?? ''}
                 onChange={(e) => setForm({ ...form, paymentSupplierCode: e.target.value })} />
             </Field>
             <Field label="種別">
               <Select value={form.ruleKind}
                 onValueChange={(v) => setForm({ ...form, ruleKind: v as RuleKind })}>
                 <SelectTrigger><SelectValue /></SelectTrigger>
                 <SelectContent>
                   {RULE_KINDS.map((k) => <SelectItem key={k} value={k}>{k}</SelectItem>)}
                 </SelectContent>
               </Select>
             </Field>
             <Field label="借方勘定">
               <Input value={form.debitAccount}
                 onChange={(e) => setForm({ ...form, debitAccount: e.target.value })} />
             </Field>
             <Field label="借方補助">
               <Input value={form.debitSubAccount ?? ''}
                 onChange={(e) => setForm({ ...form, debitSubAccount: e.target.value })} />
             </Field>
             <Field label="借方部門">
               <Input value={form.debitDepartment ?? ''}
                 onChange={(e) => setForm({ ...form, debitDepartment: e.target.value })} />
             </Field>
             <Field label="借方税区分">
               <Select value={form.debitTaxCategory}
                 onValueChange={(v) => setForm({ ...form, debitTaxCategory: v })}>
                 <SelectTrigger><SelectValue /></SelectTrigger>
                 <SelectContent>
                   {TAX_CATEGORIES.map((k) => <SelectItem key={k} value={k}>{k}</SelectItem>)}
                 </SelectContent>
               </Select>
             </Field>
             <Field label="摘要テンプレ ({sub_account} / {source_name})">
               <Input value={form.summaryTemplate}
                 onChange={(e) => setForm({ ...form, summaryTemplate: e.target.value })} />
             </Field>
             <Field label="タグ">
               <Input value={form.tag ?? ''}
                 onChange={(e) => setForm({ ...form, tag: e.target.value })} />
             </Field>
           </div>
           <DialogFooter>
             <Button variant="outline" onClick={() => setRuleDialog(null)}>キャンセル</Button>
             <Button
               disabled={!form.sourceName || !form.debitAccount || createRuleMut.isPending}
               onClick={() => createRuleMut.mutate(form)}
             >
               {createRuleMut.isPending ? '登録中...' : '登録＆再プレビュー'}
             </Button>
           </DialogFooter>
         </DialogContent>
       </Dialog>
 
+      {/*
+        通常確定 / 再確定ダイアログ。
+        force=true 経路は下の専用 Dialog (forceDialogOpen) で扱うため、ここでは扱わない。
+      */}
       <ConfirmDialog
         open={confirmVerify}
         onOpenChange={setConfirmVerify}
-        title={
-          preview && hasPerSupplierMismatch(preview)
-            ? '強制反映 (force=true) を実行'
-            : preview?.appliedWarning
-              ? '既に確定済の月を再確定'
-              : '買掛金一覧へ反映'
-        }
+        title={preview?.appliedWarning ? '既に確定済の月を再確定' : '買掛金一覧へ反映'}
         description={
-          preview && hasPerSupplierMismatch(preview)
-            ? `per-supplier 1 円整合性違反 ${preview.amountReconciliation!.perSupplierMismatches.length} 件を許容して反映します。違反詳細はサーバー側 audit log に記録されます。実行してよろしいですか？`
-            : preview?.appliedWarning
-              ? `この月は ${formatDateTime(preview.appliedWarning.appliedAt)} に既に確定済です。再確定すると確定済の値を上書きします（手動確定 verified_manually=true 行は保護されます）。続行しますか？`
-              : 'この突合結果で買掛金一覧を検証確定します。よろしいですか？（verified_manually=true として手動確定扱いになります）'
-        }
-        confirmLabel={
-          preview && hasPerSupplierMismatch(preview)
-            ? '強制反映する (force=true)'
-            : preview?.appliedWarning
-              ? '上書きして再確定する'
-              : '反映する'
+          preview?.appliedWarning
+            ? `この月は ${formatDateTime(preview.appliedWarning.appliedAt)} に既に確定済です。再確定すると確定済の値を上書きします（手動確定 verified_manually=true 行は保護されます）。続行しますか？`
+            : 'この突合結果で買掛金一覧を検証確定します。よろしいですか？（verified_manually=true として手動確定扱いになります）'
         }
+        confirmLabel={preview?.appliedWarning ? '上書きして再確定する' : '反映する'}
         onConfirm={() => {
           // ダイアログを即座に閉じることで二重起動を防止する（mutation 完了前の連打対策）。
           setConfirmVerify(false)
           if (preview && !verifyMut.isPending) {
-            verifyMut.mutate({
-              uploadId: preview.uploadId,
-              force: hasPerSupplierMismatch(preview) && forceAcknowledged,
-            })
+            verifyMut.mutate({ uploadId: preview.uploadId, force: false })
           }
         }}
       />
+
+      {/*
+        G2-M2 (Frontend Major, 2026-05-06): 強制反映 (force=true) 専用ダイアログ。
+        100 件省略表示で承認内容と実処理が乖離するリスクを抑えるため、
+        - 全 mismatch CSV ダウンロード
+        - 全件確認 checkbox (reviewedAll)
+        - 反映理由 textarea (forceReason, audit log に記録予定)
+        の3点を必須化する。`!reviewedAll || !forceReason.trim()` の間は実行不可。
+      */}
+      <Dialog open={forceDialogOpen} onOpenChange={setForceDialogOpen}>
+        <DialogContent className="max-w-2xl">
+          <DialogHeader>
+            <DialogTitle>強制反映 (force=true) の確認</DialogTitle>
+            <DialogDescription>
+              per-supplier 1 円整合性違反を許容して買掛金一覧へ反映します。
+              全違反明細は <code>finance_audit_log.force_mismatch_details</code> (JSONB) に記録されます。
+            </DialogDescription>
+          </DialogHeader>
+          {preview && hasPerSupplierMismatch(preview) && (
+            <div className="space-y-4">
+              <Alert variant="destructive">
+                <AlertTriangle className="h-4 w-4" />
+                <AlertTitle>
+                  per-supplier 整合性違反 {preview.amountReconciliation!.perSupplierMismatches.length} 件
+                </AlertTitle>
+                <AlertDescription>
+                  <div className="mb-2 text-xs">
+                    1 円単位の不一致があります。force=true で反映すると、誤った金額が
+                    買掛金一覧および MF CSV に反映される可能性があります。
+                    まず CSV を全件ダウンロードし、内容を確認してください。
+                  </div>
+                  <Button
+                    type="button"
+                    variant="outline"
+                    size="sm"
+                    onClick={() => downloadMismatchesCsv(preview.amountReconciliation!.perSupplierMismatches)}
+                  >
+                    <FileDown className="mr-1 h-4 w-4" />
+                    全 {preview.amountReconciliation!.perSupplierMismatches.length} 件 CSV ダウンロード
+                  </Button>
+                </AlertDescription>
+              </Alert>
+
+              <div className="space-y-1">
+                <Label htmlFor="force-reason" className="text-xs">
+                  反映理由 (audit log に記録されます) <span className="text-red-600">*</span>
+                </Label>
+                <Textarea
+                  id="force-reason"
+                  value={forceReason}
+                  onChange={(e) => setForceReason(e.target.value)}
+                  placeholder="例: 期末締めのため端数許容で反映 / supplier XX の手数料調整など"
+                  rows={3}
+                />
+              </div>
+
+              <div className="flex items-start gap-2 rounded border border-red-300 bg-red-50 p-2 text-xs">
+                <Checkbox
+                  id="reviewed-all"
+                  checked={reviewedAll}
+                  onCheckedChange={(v) => setReviewedAll(v === true)}
+                  className="mt-0.5"
+                />
+                <Label htmlFor="reviewed-all" className="cursor-pointer">
+                  全 {preview.amountReconciliation!.perSupplierMismatches.length} 件の違反内容を確認しました
+                  （CSV ダウンロード or 上のリストで全件レビュー済）
+                </Label>
+              </div>
+            </div>
+          )}
+          <DialogFooter>
+            <Button variant="outline" onClick={() => setForceDialogOpen(false)}>
+              キャンセル
+            </Button>
+            <Button
+              variant="destructive"
+              disabled={!reviewedAll || !forceReason.trim() || verifyMut.isPending}
+              onClick={() => {
+                // 二重起動防止のため即座に閉じる。
+                setForceDialogOpen(false)
+                if (preview && hasPerSupplierMismatch(preview) && !verifyMut.isPending) {
+                  verifyMut.mutate({
+                    uploadId: preview.uploadId,
+                    force: true,
+                    forceReason: forceReason.trim(),
+                  })
+                }
+              }}
+            >
+              強制反映する (force=true)
+            </Button>
+          </DialogFooter>
+        </DialogContent>
+      </Dialog>
     </div>
   )
 }
 
 /**
  * P1-08: ISO 8601 OffsetDateTime を `yyyy-MM-dd HH:mm` (JST 表示) にフォーマットする。
  * パース失敗時は元文字列をそのまま返す (UX を壊さない)。
  */
 function formatDateTime(iso: string | null | undefined): string {
   if (!iso) return '-'
   const d = new Date(iso)
   if (Number.isNaN(d.getTime())) return iso
   const pad = (n: number) => n.toString().padStart(2, '0')
   return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
 }
 
 /**
  * G2-M2: per-supplier 1 円整合性違反が 1 件でもあるか判定する。
  * preview / amountReconciliation / perSupplierMismatches のいずれかが null なら false。
  */
 function hasPerSupplierMismatch(preview: PaymentMfPreviewResponse | null): boolean {
   if (!preview || !preview.amountReconciliation) return false
   const mm = preview.amountReconciliation.perSupplierMismatches
   return Array.isArray(mm) && mm.length > 0
 }
 
+/**
+ * G2-M2 (Frontend Major, 2026-05-06): per-supplier mismatch を CSV 化してダウンロードする。
+ *
+ * <p>UI 側は性能維持のため `slice(0, 100)` で表示を省略しているが、force 反映前にユーザーが
+ * 全件を目視確認できる経路として CSV ダウンロードを提供する。Excel で開いて検索/並び替えが可能。
+ *
+ * <p>BOM (`﻿`) を先頭に付与して Excel での文字化けを回避。double quote は `""` で escape。
+ */
+function downloadMismatchesCsv(mismatches: string[]): void {
+  const header = 'no,detail'
+  const lines = mismatches.map((m, i) => `${i + 1},"${m.replace(/"/g, '""')}"`)
+  const csv = [header, ...lines].join('\r\n')
+  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' })
+  const url = URL.createObjectURL(blob)
+  const a = document.createElement('a')
+  a.href = url
+  const today = new Date().toISOString().slice(0, 10)
+  a.download = `payment-mf-mismatches-${today}.csv`
+  document.body.appendChild(a)
+  a.click()
+  document.body.removeChild(a)
+  setTimeout(() => URL.revokeObjectURL(url), 1000)
+}
+
 function blankRuleRequest(): PaymentMfRuleRequest {
   return {
     sourceName: '',
     paymentSupplierCode: '',
     ruleKind: 'PAYABLE',
     debitAccount: '買掛金',
     debitSubAccount: '',
     debitDepartment: '',
     debitTaxCategory: '対象外',
     creditAccount: '資金複合',
     creditTaxCategory: '対象外',
     summaryTemplate: '{source_name}',
     tag: '',
     priority: 100,
   }
 }
 
 function rowBgClass(r: PaymentMfPreviewRow): string {
   if (r.errorType === 'UNREGISTERED') return 'bg-red-50'
   if (r.ruleKind === 'SUMMARY') return 'bg-slate-50'
   // P1-03 案 D / P1-07 案 D: 副行 (PAYABLE_*/DIRECT_PURCHASE_* FEE/DISCOUNT/EARLY/OFFSET) は
   // 薄い indigo で親 (PAYABLE/DIRECT_PURCHASE) と区別。5日払い・20日払いとも同色で構造を強調。
   if (
     r.ruleKind &&
     (r.ruleKind.startsWith('PAYABLE_') || r.ruleKind.startsWith('DIRECT_PURCHASE_'))
   )
     return 'bg-indigo-50'
   if (r.matchStatus === 'DIFF') return 'bg-amber-50'
   if (r.matchStatus === 'UNMATCHED' && r.ruleKind === 'PAYABLE') return 'bg-red-50'
   return ''
 }
 
 function Field({ label, children }: { label: string; children: React.ReactNode }) {
   return (
     <div className="space-y-1">
       <Label className="text-xs">{label}</Label>
       {children}
     </div>
   )
 }
 
 function MatchBadge({
   status, supplierNo, txMonth,
 }: { status: string | null; supplierNo: number | null; txMonth: string | null }) {
   if (status === 'MATCHED') return <span className="text-green-700">🟢 一致</span>
   if (status === 'DIFF')
     return supplierNo && txMonth ? (
       <Link
         className="text-amber-700 underline"
         href={`/finance/accounts-payable?supplierNo=${supplierNo}&transactionMonth=${txMonth}`}
       >
         🟡 差異
       </Link>
     ) : <span className="text-amber-700">🟡 差異</span>
   if (status === 'UNMATCHED') return <span className="text-red-600">🔴 買掛金なし</span>
   if (status === 'NA') return <span className="text-muted-foreground">対象外</span>
   return <span className="text-muted-foreground">–</span>
 }
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git show --no-ext-diff --unified=40 60d7d81 -- backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java' in C:\project\odamitsu-data-hub
 succeeded in 1365ms:
commit 60d7d815f07409beb1375fc08c96bd6dcfc8d13a
Author: kazuki <k_oda@odamitsu.com>
Date:   Thu May 7 09:18:25 2026 +0900

    fix(codex-r2): Codex 第 2 弾批判レビュー対応 (Critical + P1 + 9 Major + 1 Minor)
    
    3 commit (audit/mf/finance) を Codex 批判レビューにかけ、12 件の指摘を修正。
    
    ## Critical / P1 (即修正)
    * Critical: BULK 不変条件違反時の SUM フォールバックを fail-closed (例外) 化
      - PaymentMfImportService.sumVerifiedAmountForGroup で全行 BULK 不一致時に
        FinanceInternalException 422、過大計上の MF CSV 流出を遮断
    * P1: V033 再暗号化が片側カラムのみで refresh_token が旧鍵残るバグ修正
      - reencryptMfTokens() で access + refresh を 1 UPDATE で同時再暗号化
      - decrypt 失敗時 skip + WARN ログで idempotent 強化 (recipherOrSkip)
    
    ## Major (9 件)
    * MF P2: V037 ordering note は runbook §6' に集約 (V037 は revert で checksum 復元)
    * Audit P2: FinanceAuditAspect で Loader 解決失敗時の captureReturn/Args フォールバック追加
    * G2-M2 Major: verification_source 3 経路 (BULK/MANUAL/MF_OVERRIDE) を共通 advisory lock で直列化
      - FinancePayableLock util 切り出し、computePayableLockKey + acquire
      - applyVerification / verify / applyMfOverride で同一 lock キー
    * G2-M3 Major: force audit 全件保存 (V043 finance_audit_log.force_mismatch_details JSONB)
      - 50 件切り詰めの reason と全件 JSON を分離、UI 全件確認可能化への土台
    * G2-M4 Major: forceReason 必須化 + reason 空 → FORCE_REASON_REQUIRED 400
    * G2-M5 Major: 20日払い専用 Excel が PAYMENT_5TH 誤分類されるバグ修正
      - PaymentMfExcelParser.determineInitialSection で送金日 day による初期 section 判定
    * G2-M5 Major: OFFSET マスタ欠落時の hardcoded default fallback + 最後の active 行削除禁止
    * G2-M6 Major: P1-02 opening 注入空時の警告 (SupplierBalances/IntegrityReport DTO)
    * Frontend Major: force apply UX 強化 (CSV download, reviewedAll checkbox, forceReason 必須)
    
    ## Minor
    * G2-M? Minor: V044 で V040 backfill 検査 (NOTICE のみ、自動修復なし)
      - 実行結果: 560 行で history 突合なし = false positive (P1-08 導入前の歴史データ)
    
    ## 新規 migration
    - V043 finance_audit_log.force_mismatch_details JSONB + GIN index
    - V044 V040 backfill 検査 (RAISE NOTICE のみ)
    
    ## 新規 runbook
    - runbook-payment-mf-bulk-invariant-violation.md (Critical fix の運用手順)
    - runbook-payment-mf-force-apply.md (force=true の二段認可運用)
    
    ## CLAUDE.md
    - Review Guidelines セクション追加 (Repository Context / Review Priorities /
      Architecture Rules) — AI レビュー時の優先順位を明示
    
    ## テスト
    - 既存 233 + 新規 ~10 件 PASS、frontend tsc PASS、リグレッション 0
    - 5 group 並列修正で衝突なし
    
    Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>

diff --git a/backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java b/backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java
index 8e035c9..56e8ddb 100644
--- a/backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java
+++ b/backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java
@@ -1,66 +1,67 @@
 package jp.co.oda32.domain.service.finance;
 
 import jakarta.annotation.PostConstruct;
 import jakarta.persistence.EntityManager;
 import jakarta.persistence.PersistenceContext;
 import com.fasterxml.jackson.databind.ObjectMapper;
 import jp.co.oda32.audit.AuditLog;
 import jp.co.oda32.audit.FinanceAuditWriter;
 import jp.co.oda32.constant.FinanceConstants;
 import jp.co.oda32.domain.model.finance.MOffsetJournalRule;
 import jp.co.oda32.domain.model.finance.MPaymentMfRule;
 import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
 import jp.co.oda32.domain.model.finance.TPaymentMfAuxRow;
 import jp.co.oda32.domain.model.finance.TPaymentMfImportHistory;
 import jp.co.oda32.domain.repository.finance.MOffsetJournalRuleRepository;
 import jp.co.oda32.domain.repository.finance.MPaymentMfRuleRepository;
 import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
 import jp.co.oda32.domain.repository.finance.TPaymentMfAuxRowRepository;
 import jp.co.oda32.domain.repository.finance.TPaymentMfImportHistoryRepository;
 import jp.co.oda32.dto.finance.paymentmf.AppliedWarning;
 import jp.co.oda32.dto.finance.paymentmf.DuplicateWarning;
 import jp.co.oda32.dto.finance.paymentmf.PaymentMfAuxRowResponse;
 import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewResponse;
 import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewRow;
 import jp.co.oda32.dto.finance.paymentmf.VerifiedExportPreviewResponse;
 import jp.co.oda32.exception.FinanceBusinessException;
+import jp.co.oda32.exception.FinanceInternalException;
 import lombok.Builder;
 import lombok.Data;
 import lombok.extern.slf4j.Slf4j;
 import org.apache.poi.openxml4j.util.ZipSecureFile;
 import org.apache.poi.ss.usermodel.Sheet;
 import org.apache.poi.ss.usermodel.Workbook;
 import org.apache.poi.xssf.usermodel.XSSFWorkbook;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.context.annotation.Lazy;
 import org.springframework.scheduling.annotation.Scheduled;
 import org.springframework.stereotype.Service;
 import org.springframework.transaction.annotation.Propagation;
 import org.springframework.transaction.annotation.Transactional;
 import org.springframework.web.multipart.MultipartFile;
 
 import java.io.IOException;
 import java.math.BigDecimal;
 import java.math.RoundingMode;
 import java.security.MessageDigest;
 import java.security.NoSuchAlgorithmException;
 import java.time.LocalDate;
 import java.time.LocalDateTime;
 import java.time.OffsetDateTime;
 import java.time.ZoneOffset;
 import java.time.format.DateTimeFormatter;
 import java.util.ArrayList;
 import java.util.Comparator;
 import java.util.LinkedHashMap;
 import java.util.List;
 import java.util.Map;
 import java.util.Set;
 import java.util.UUID;
 import java.util.concurrent.ConcurrentHashMap;
 import java.util.stream.Collectors;
 
 /**
  * 振込明細Excel → MoneyForward買掛仕入CSV 変換サービス
  */
 @Service
 @Slf4j
@@ -77,85 +78,82 @@ public class PaymentMfImportService {
      * <p>従来 {@code "仕入値引・戻し高" / "物販事業部" / "課税仕入-返還等 10%"} と
      * ハードコードされていた OFFSET 副行貸方を本テーブルから lookup することで、
      * 税理士確認後に admin が UI 経由で値を書き換えられるようにした。
      * V041 の seed で shop_no=1 のデフォルト値 (= 旧ハードコード値と同一) を投入済。
      */
     private final MOffsetJournalRuleRepository offsetJournalRuleRepository;
 
     @PersistenceContext
     private EntityManager entityManager;
 
     /**
      * 自己注入。{@code @Transactional(REQUIRES_NEW)} が同一クラス内呼び出しでも
      * Spring AOP プロキシ経由になるようにするため。{@code @Lazy} は循環依存回避。
      */
     @Autowired
     @Lazy
     private PaymentMfImportService self;
 
     /**
      * G2-M2 (2026-05-06): per-supplier 1 円不一致を {@code force=true} で許容して反映した際、
      * 既存 {@code @AuditLog} aspect の after snapshot 行とは別に、違反詳細を {@code reason} 列に
      * 持つ補足 audit 行を 1 件追加する。AOP 拡張せずに済むよう field injection で別 Bean を持つ。
      * <p>{@code @Lazy} は AOP / proxy のブートストラップ循環回避用 (writer 自体は内部で
      * {@code FinanceAuditLogRepository} を使うのみで本サービスへの依存は無いが、保守的に Lazy)。
      */
     @Autowired(required = false)
     @Lazy
     private FinanceAuditWriter financeAuditWriter;
 
     /** {@code force=true} 補足 audit 行の reason 値を組み立てる際の supplier mismatch 詳細表示上限。 */
     private static final int FORCE_AUDIT_MISMATCH_DETAIL_LIMIT = 50;
 
     // 差額一致閾値は FinanceConstants.PAYMENT_REPORT_MINOR_DIFFERENCE(_LONG) に集約。
     private static final int MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
     private static final int MAX_DATA_ROWS = 10000;
     private static final int MAX_CACHE_ENTRIES = 100;
     private static final long CACHE_TTL_MILLIS = 30L * 60 * 1000;
     private static final String XLSX_CONTENT_TYPE =
             "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
 
-    /**
-     * 取引月単位の applyVerification / exportVerifiedCsv を直列化する advisory lock 用キー。
-     * shop_no と transaction_month(epochDay) を混ぜて 64bit のキーに詰める。
-     */
-    private static final int ADVISORY_LOCK_CLASS = 0x7041_4D46; // 'pAMF'
+    // 取引月単位の advisory lock キー計算 / 取得は {@link FinancePayableLock} に共通化
+    // (G2-M2 Codex Major #2)。3 経路 (BULK / MANUAL / MF_OVERRIDE) で同一 util を使う。
 
     // CSV 生成ロジックは {@link PaymentMfCsvWriter} に分離（ステートレスユーティリティ）。
 
     // Excel 読み取り（selectSheet / parseSheet / メタ行判定 など）は
     // {@link PaymentMfExcelParser} に分離。ParsedEntry / ParsedExcel / SectionSummary も同クラスに移動。
 
     /**
      * アップロード済み Excel のパース結果をインメモリ保持する。
      * <p><b>node-local</b>: 複数 JVM 起動（HA 構成）では preview と convert/applyVerification が
      * 別インスタンスに到達すると 404 になる。本アプリは single-instance 前提の設計
      * （{@code MfOauthStateStore} と同じスコープ）。マルチ化するときは Redis 等に寄せること (B-W9)。
      */
     private final Map<String, CachedUpload> cache = new ConcurrentHashMap<>();
 
     public PaymentMfImportService(MPaymentMfRuleRepository ruleRepository,
                                   TPaymentMfImportHistoryRepository historyRepository,
                                   TAccountsPayableSummaryRepository payableRepository,
                                   TAccountsPayableSummaryService payableService,
                                   TPaymentMfAuxRowRepository auxRowRepository,
                                   MOffsetJournalRuleRepository offsetJournalRuleRepository) {
         this.ruleRepository = ruleRepository;
         this.historyRepository = historyRepository;
         this.payableRepository = payableRepository;
         this.payableService = payableService;
         this.auxRowRepository = auxRowRepository;
         this.offsetJournalRuleRepository = offsetJournalRuleRepository;
     }
 
     @PostConstruct
     void initPoiSecurity() {
         ZipSecureFile.setMinInflateRatio(0.01);
         ZipSecureFile.setMaxEntrySize(100L * 1024 * 1024);
     }
 
     // ===========================================================
     // Public API
     // ===========================================================
 
     public PaymentMfPreviewResponse preview(MultipartFile file) throws IOException {
         validateFile(file);
@@ -190,120 +188,152 @@ public class PaymentMfImportService {
     public PaymentMfPreviewResponse rePreview(String uploadId) {
         CachedUpload cached = getCached(uploadId);
         return buildPreview(uploadId, cached);
     }
 
     /**
      * CSVバイト列を返す（CP932・LF・金額に末尾半角スペース）。未登録行があれば例外。
      *
      * <p>G2-M2 (2026-05-06): per-supplier 1 円不一致 ({@code perSupplierMismatches}) が
      * 1 件でもあれば 422 でブロックする。業務的には preview 画面で違反を確認してから
      * Excel を修正 → 再アップロードする運用なので、CSV ダウンロード経路には force 上書きを設けない。
      */
     public byte[] convert(String uploadId, Integer userNo) {
         CachedUpload cached = getCached(uploadId);
         PaymentMfPreviewResponse preview = buildPreview(uploadId, cached);
         if (preview.getErrorCount() > 0) {
             // T5: 業務メッセージ (ユーザーがマスタ登録すれば解決) なので FinanceBusinessException を使う。
             // 旧 IllegalStateException → FinanceExceptionHandler.handleIllegalState で 422 + 汎用化されてしまい
             // フロントに件数情報が届かない問題があった (Cluster C M1)。
             throw new FinanceBusinessException(
                     "未登録の送り先があります（" + preview.getErrorCount() + "件）。マスタ登録後に再試行してください");
         }
         // G2-M2: per-supplier 1 円不一致は CSV 出力経路では強制上書き不可 (Excel 修正が正しい運用)。
         List<String> mismatches = perSupplierMismatchesOf(preview);
         if (!mismatches.isEmpty()) {
             throw new FinanceBusinessException(
                     "per-supplier 1 円整合性違反 " + mismatches.size() + " 件あり。"
                             + "プレビュー画面で詳細を確認し、Excel を修正してから再アップロードしてください",
                     FinanceConstants.ERROR_CODE_PER_SUPPLIER_MISMATCH);
         }
         // CSV 取引日列は 小田光の締め日(前月20日 = transactionMonth) 固定。
         // 送金日(送金日≠取引日)は MF の銀行データ連携で自動付与されるため CSV には含めない。
         LocalDate txMonth = cached.getTransferDate() == null ? null
                 : deriveTransactionMonth(cached.getTransferDate());
         byte[] csv = PaymentMfCsvWriter.toCsvBytes(preview.getRows(), txMonth);
         saveHistory(cached, preview, csv, userNo);
         return csv;
     }
 
     /**
-     * 旧 API 後方互換用 ({@code force=false} 固定で {@link #applyVerification(String, Integer, boolean)} を呼ぶ)。
+     * 旧 API 後方互換用 ({@code force=false} 固定で {@link #applyVerification(String, Integer, boolean, String)} を呼ぶ)。
      * <p>テスト・既存 batch 経路から既にこの 2 引数版が直接呼ばれているため、シグネチャは維持する。
      * 本メソッド経由でも force false 経路で per-supplier 1 円不一致は 422 ブロックとなる。
      *
-     * @deprecated G2-M2 (2026-05-06) 以降、新コードは {@link #applyVerification(String, Integer, boolean)}
-     *             を使い、{@code force} の意図を明示すること。
+     * @deprecated G2-M2 (2026-05-06) 以降、新コードは {@link #applyVerification(String, Integer, boolean, String)}
+     *             を使い、{@code force} の意図と承認理由を明示すること。
      */
     @Deprecated
     public VerifyResult applyVerification(String uploadId, Integer userNo) {
-        return applyVerification(uploadId, userNo, false);
+        return applyVerification(uploadId, userNo, false, null);
+    }
+
+    /**
+     * 旧 3-引数 API 後方互換用 (Codex Major #4 で {@code forceReason} を追加した 4 引数版を呼ぶ)。
+     * 既存テストと旧 client が 3 引数で呼んでいるため、シグネチャは維持する。
+     * <p>{@code force=true} で本オーバーロードを呼ぶと {@code forceReason} が無いため
+     * {@link FinanceBusinessException} (FORCE_REASON_REQUIRED) が投げられる点に注意。
+     *
+     * @deprecated G2-M4 fix (2026-05-06) 以降、新コードは
+     *             {@link #applyVerification(String, Integer, boolean, String)} を使い、
+     *             {@code force=true} 時は forceReason も渡すこと。
+     */
+    @Deprecated
+    public VerifyResult applyVerification(String uploadId, Integer userNo, boolean force) {
+        return applyVerification(uploadId, userNo, force, null);
     }
 
     /**
      * アップロード済み振込明細を正として、買掛金一覧(t_accounts_payable_summary)に検証結果を一括反映する。
      * verified_manually=true で手動確定扱いにし、SMILE再検証バッチで上書きされないようにする。
      *
      * <p>並列呼び出しでの上書き競合を防ぐため、同一 (shop_no, transaction_month) に対しては
      * PostgreSQL advisory lock で直列化する。5日払い Excel と 20日払い Excel が別プロセス/
      * 別 UI から同時適用された場合でも、後着は先行 tx のコミット完了後に実行される。
      *
      * <p>G2-M2 (2026-05-06): {@code force} パラメータ追加。
      * <ul>
      *   <li>{@code force=false} (推奨デフォルト): per-supplier 1 円整合性違反
      *       ({@code AmountReconciliation#perSupplierMismatches}) が 1 件でもあれば
      *       {@link FinanceBusinessException} (422) でブロックする。
      *       業務的には preview で違反一覧を確認 → Excel を修正 → 再アップロード が正しい運用。</li>
      *   <li>{@code force=true}: 違反を許容して反映する。
      *       {@code finance_audit_log.reason} に {@code FORCE_APPLIED: per-supplier mismatches=...}
      *       で違反詳細を補足記録し、後追い監査を可能にする。</li>
      * </ul>
+     *
+     * <p>Codex Major #4 (2026-05-06): {@code forceReason} 必須化。
+     * <ul>
+     *   <li>{@code force=true} かつ {@code forceReason} が null / 空文字 →
+     *       {@link FinanceBusinessException} (FORCE_REASON_REQUIRED, 400) で拒否。</li>
+     *   <li>{@code force=false} の場合 {@code forceReason} は無視される。</li>
+     * </ul>
+     * 二段認可は実装スコープ過大のため運用 runbook (= 最低 2 名で内容確認) にて担保し、
+     * 実装は forceReason 必須化のみで「誰が」「なぜ」を audit に残せるようにする。
      */
     @Transactional
     @AuditLog(table = "t_accounts_payable_summary", operation = "payment_mf_apply",
             pkExpression = "{'uploadId': #a0, 'userNo': #a1, 'force': #a2}",
             captureArgsAsAfter = true, captureReturnAsAfter = true)
-    public VerifyResult applyVerification(String uploadId, Integer userNo, boolean force) {
+    public VerifyResult applyVerification(String uploadId, Integer userNo, boolean force, String forceReason) {
         CachedUpload cached = getCached(uploadId);
         if (cached.getTransferDate() == null) {
             throw new IllegalArgumentException("送金日が取得できていません。xlsxを再アップロードしてください");
         }
+        // Codex Major #4: force=true 時の forceReason 必須化 (advisory lock 取得前に bail-out)。
+        // null / 空文字は不可。空白のみ ("   ") も拒否し、運用上意味のある理由を強制する。
+        if (force && (forceReason == null || forceReason.isBlank())) {
+            throw new FinanceBusinessException(
+                    "force=true 指定時は forceReason (承認理由) が必須です。"
+                            + "承認者・確認者・業務理由を含めて入力してください",
+                    FinanceConstants.ERROR_CODE_FORCE_REASON_REQUIRED);
+        }
         LocalDate txMonth = deriveTransactionMonth(cached.getTransferDate());
         acquireAdvisoryLock(txMonth);
 
         // G2-M2: per-supplier 1 円整合性違反のサーバー側ブロック。
         // preview を一度だけ事前構築し、ブロック判定 (force) と aux 保存の両方で使い回す。
         // (このタイミングなら DB ロック取得後で他 tx の影響を受けにくい)。
         PaymentMfPreviewResponse blockingPreview = buildPreview(uploadId, cached);
         List<String> mismatches = perSupplierMismatchesOf(blockingPreview);
         if (!mismatches.isEmpty() && !force) {
             // 422 + コード PER_SUPPLIER_MISMATCH を返す。client 側で「force=true で再実行」UI 分岐に使う。
             // 件数のみ返却し、supplier 名は preview レスポンスから別途取得させる
             // (例外メッセージに長い detail を載せると i18n / log noise の原因になる)。
             throw new FinanceBusinessException(
                     "per-supplier 1 円整合性違反 " + mismatches.size() + " 件あり。"
                             + "プレビュー画面で詳細を確認の上、強制実行する場合は force=true を指定してください",
                     FinanceConstants.ERROR_CODE_PER_SUPPLIER_MISMATCH);
         }
 
         // G2-M10 (V040): note 接頭辞は UI 表示用にのみ利用 (BULK/MANUAL 判定には verification_source を使う)。
         @SuppressWarnings("deprecation")
         String bulkPrefix = FinanceConstants.VERIFICATION_NOTE_BULK_PREFIX;
         String note = bulkPrefix + cached.getFileName() + " " + cached.getTransferDate();
 
         List<MPaymentMfRule> rules = ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0");
         Map<String, MPaymentMfRule> byCode = new LinkedHashMap<>();
         Map<String, MPaymentMfRule> bySource = new LinkedHashMap<>();
         for (MPaymentMfRule r : rules) {
             if (r.getPaymentSupplierCode() != null && !r.getPaymentSupplierCode().isBlank())
                 byCode.putIfAbsent(r.getPaymentSupplierCode().trim(), r);
             bySource.putIfAbsent(normalize(r.getSourceName()), r);
         }
 
         // 事前パス: 当該 Excel に含まれる全 supplierCode を集め、t_accounts_payable_summary を一括取得する (N+1 解消)。
         // 5日払い (PAYMENT_5TH) のみが PAYABLE 突合対象 (20日払いセクションは DIRECT_PURCHASE 自動降格)。
         Set<String> codesToReconcile = new java.util.LinkedHashSet<>();
         for (PaymentMfExcelParser.ParsedEntry e : cached.getEntries()) {
             if (e.section == PaymentMfSection.PAYMENT_20TH) continue;
             MPaymentMfRule rule = null;
             if (e.supplierCode != null) rule = byCode.get(e.supplierCode);
             if (rule == null) rule = bySource.get(normalize(e.sourceName));
@@ -382,224 +412,256 @@ public class PaymentMfImportService {
                     ? note + " | 自動調整: 元 ¥" + payable + " → ¥" + invoice
                       + " (" + (autoAdjusted.signum() > 0 ? "+" : "") + "¥" + autoAdjusted + ")"
                     : note;
             for (TAccountsPayableSummary s : list) {
                 s.setVerificationResult(isMatched ? 1 : 0);
                 s.setPaymentDifference(difference);
                 s.setVerifiedManually(true);
                 s.setMfExportEnabled(isMatched);
                 s.setVerificationNote(adjustNote);
                 s.setVerifiedAmount(invoice);
                 // V026: 税抜 verified を税率別に逆算 (単一税率前提。複数税率の仕入先は手動調整で対応)。
                 BigDecimal taxRate = s.getTaxRate() != null ? s.getTaxRate() : BigDecimal.TEN;
                 BigDecimal divisor = BigDecimal.valueOf(100).add(taxRate);
                 BigDecimal invoiceTaxExcl = invoice.multiply(BigDecimal.valueOf(100))
                         .divide(divisor, 0, java.math.RoundingMode.DOWN);
                 s.setVerifiedAmountTaxExcluded(invoiceTaxExcl);
                 s.setAutoAdjustedAmount(autoAdjusted);
                 // 5日払いセクション (PAYMENT_5TH) の PAYABLE のみここに到達する。
                 // Excel の送金日を CSV 取引日として記録する。
                 s.setMfTransferDate(cached.getTransferDate());
                 // G2-M1/M10 (V040): 書込経路を明示記録 (read 側 sumVerifiedAmountForGroup と
                 // 再 upload 保護判定で参照される)。
                 s.setVerificationSource(FinanceConstants.VERIFICATION_SOURCE_BULK);
                 payableService.save(s);
             }
         }
 
         // 補助行(EXPENSE/SUMMARY/DIRECT_PURCHASE) を (shop, 取引月, 送金日) 単位で洗い替え保存。
         // G2-M2: ブロック判定で構築した blockingPreview をそのまま使い回す
         // (従来は saveAuxRowsForVerification 内で再 buildPreview していたため N+1 が 2 周していた)。
         PaymentMfPreviewResponse preview = blockingPreview;
         saveAuxRowsForVerification(cached, preview, txMonth, userNo);
 
         // P1-08: 確定済マークを history に永続化 (preview L2 警告の根拠データ)。
         saveAppliedHistory(cached, preview, userNo);
 
         // G2-M2: force=true で per-supplier 不一致を許容して反映した場合、
         // 補足 audit 行を追加して reason に違反詳細を残す。
         // 既存 @AuditLog aspect が記録する after snapshot 行とは別 row として書く
         // (AOP 拡張せずに済む & 後で reason 列で grep できる)。
+        // Codex Major #4: forceReason (承認者・確認者・業務理由) も audit reason に含める。
         if (force && !mismatches.isEmpty()) {
-            writeForceAppliedAuditRow(uploadId, userNo, cached, txMonth, mismatches);
+            writeForceAppliedAuditRow(uploadId, userNo, cached, txMonth, mismatches, forceReason);
         }
 
         return VerifyResult.builder()
                 .transferDate(cached.getTransferDate())
                 .transactionMonth(txMonth)
                 .matchedCount(matched)
                 .diffCount(diff)
                 .notFoundCount(notFound)
                 .skippedCount(skipped)
                 .skippedManuallyVerifiedCount(skippedManuallyVerified)
                 .unmatchedSuppliers(unmatchedSuppliers)
                 .build();
     }
 
     /**
      * G2-M2 (2026-05-06): {@code force=true} で per-supplier 1 円違反を許容して反映した時の補足 audit 行。
-     * <p>{@link FinanceAuditWriter#write} を直接呼んで {@code reason} 列に
-     * {@code "FORCE_APPLIED: per-supplier mismatches count=N, details=[...]"} を記録する。
-     * 詳細リストは長くなりすぎないよう先頭 {@link #FORCE_AUDIT_MISMATCH_DETAIL_LIMIT} 件のみ。
+     * <p>{@link FinanceAuditWriter#write} を直接呼んで {@code reason} 列に件数 + 先頭
+     * {@link #FORCE_AUDIT_MISMATCH_DETAIL_LIMIT} 件の詳細サマリを記録する (容量配慮)。
+     * Codex Major #3 (2026-05-06): {@code force_mismatch_details} JSONB 列 (V043) に
+     * <b>全件</b>を構造化保存し、reason との乖離を防ぐ。
+     * <p>Codex Major #4 (2026-05-06): {@code forceReason} (承認者・確認者・業務理由) を reason 末尾に
+     * {@code reason="..."} 形式で連結する。null の場合は付加しない (旧 client 互換用、3 引数 deprecated 経路)。
      * <p>writer Bean が無い (test 環境など) や書込み失敗は本体反映を巻き戻さない (warn ログのみ)。
      */
     private void writeForceAppliedAuditRow(String uploadId, Integer userNo, CachedUpload cached,
-                                           LocalDate txMonth, List<String> mismatches) {
+                                           LocalDate txMonth, List<String> mismatches, String forceReason) {
         if (financeAuditWriter == null) {
             log.warn("FinanceAuditWriter Bean 不在のため force=true 補足 audit を skip: uploadId={}", uploadId);
             return;
         }
         try {
-            String reason = buildForceAppliedReason(mismatches);
+            String reason = buildForceAppliedReason(mismatches, forceReason);
             ObjectMapper mapper = new ObjectMapper();
             com.fasterxml.jackson.databind.JsonNode pk = mapper.createObjectNode()
                     .put("uploadId", uploadId)
                     .put("userNo", userNo == null ? null : userNo.toString())
                     .put("force", "true")
                     .put("transferDate", cached.getTransferDate() == null ? null
                             : cached.getTransferDate().toString())
                     .put("transactionMonth", txMonth == null ? null : txMonth.toString())
                     .put("fileName", cached.getFileName());
+            // Codex Major #3: 全件を JSONB 列に構造化保存 (reason 50 件切り詰めとの乖離防止)。
+            com.fasterxml.jackson.databind.node.ArrayNode detailsArr = mapper.createArrayNode();
+            for (String m : mismatches) {
+                detailsArr.add(mapper.createObjectNode().put("line", m));
+            }
             financeAuditWriter.write(
                     "t_accounts_payable_summary",
                     "payment_mf_apply_force",
                     userNo,
                     userNo == null ? "SYSTEM" : "USER",
                     pk,
                     null,
                     null,
                     reason,
                     null,
-                    null);
+                    null,
+                    detailsArr);
             log.warn("payment_mf_apply force=true で per-supplier 不一致 {} 件を許容: uploadId={} txMonth={}",
                     mismatches.size(), uploadId, txMonth);
         } catch (Exception ex) {
             log.error("force=true 補足 audit 行の書込に失敗 (本体反映は完了済): uploadId={} err={}",
                     uploadId, ex.toString());
         }
     }
 
+    /**
+     * G2-M2: 補足 audit 行の {@code reason} 文字列を組み立てる (旧 1 引数版、後方互換用)。
+     * <p>新規コードは {@link #buildForceAppliedReason(List, String)} で forceReason も渡すこと。
+     * @deprecated Codex Major #4 (2026-05-06) 以降。
+     */
+    @Deprecated
+    static String buildForceAppliedReason(List<String> mismatches) {
+        return buildForceAppliedReason(mismatches, null);
+    }
+
     /**
      * G2-M2: 補足 audit 行の {@code reason} 文字列を組み立てる。
-     * <p>形式: {@code "FORCE_APPLIED: per-supplier mismatches count=N, details=[item1, item2, ...]"}
+     * <p>形式 (forceReason あり): {@code "FORCE_APPLIED: per-supplier mismatches count=N, details=[...], reason=\"...\""}
+     * <p>形式 (forceReason なし): {@code "FORCE_APPLIED: per-supplier mismatches count=N, details=[...]"}
      * <p>{@link #FORCE_AUDIT_MISMATCH_DETAIL_LIMIT} 件超過時は {@code "...(+M more)"} で打ち切り。
+     * 全件は別途 {@code finance_audit_log.force_mismatch_details} JSONB 列に保存される (V043, Codex Major #3)。
+     * <p>Codex Major #4 (2026-05-06): {@code forceReason} (承認者・確認者・業務理由) を末尾に追記。
+     * null / 空文字なら省略 (旧 deprecated 経路互換)。
      */
-    static String buildForceAppliedReason(List<String> mismatches) {
-        if (mismatches == null || mismatches.isEmpty()) {
-            return "FORCE_APPLIED: per-supplier mismatches count=0";
-        }
-        int total = mismatches.size();
-        int show = Math.min(total, FORCE_AUDIT_MISMATCH_DETAIL_LIMIT);
-        StringBuilder sb = new StringBuilder("FORCE_APPLIED: per-supplier mismatches count=")
-                .append(total).append(", details=[");
-        for (int i = 0; i < show; i++) {
-            if (i > 0) sb.append(", ");
-            sb.append(mismatches.get(i));
+    static String buildForceAppliedReason(List<String> mismatches, String forceReason) {
+        StringBuilder sb = new StringBuilder("FORCE_APPLIED: per-supplier mismatches count=");
+        int total = mismatches == null ? 0 : mismatches.size();
+        sb.append(total);
+        if (total > 0) {
+            int show = Math.min(total, FORCE_AUDIT_MISMATCH_DETAIL_LIMIT);
+            sb.append(", details=[");
+            for (int i = 0; i < show; i++) {
+                if (i > 0) sb.append(", ");
+                sb.append(mismatches.get(i));
+            }
+            if (total > show) {
+                sb.append(", ...(+").append(total - show).append(" more)");
+            }
+            sb.append(']');
         }
-        if (total > show) {
-            sb.append(", ...(+").append(total - show).append(" more)");
+        if (forceReason != null && !forceReason.isBlank()) {
+            // 二重引用符の入れ子を避けるため、value 側の " は ' に置換。改行は半角スペースに正規化
+            // (audit log の grep 安定化)。
+            String sanitized = forceReason.replace('"', '\'').replace('\n', ' ').replace('\r', ' ');
+            sb.append(", reason=\"").append(sanitized).append('"');
         }
-        sb.append(']');
         return sb.toString();
     }
 
     /**
      * G2-M2: preview から per-supplier 1 円不一致リストを安全に取り出す。
      * {@code amountReconciliation} or {@code perSupplierMismatches} が null の場合は空リスト。
      *
      * <p>パッケージ可視 + non-static にしてテストから {@code Mockito.spy} で差し替え可能にする
      * (Excel fixture を mismatch 用に作るのが難しいため、テストフックとして残す)。
      */
     List<String> perSupplierMismatchesOf(PaymentMfPreviewResponse preview) {
         if (preview == null || preview.getAmountReconciliation() == null) return List.of();
         List<String> mm = preview.getAmountReconciliation().getPerSupplierMismatches();
         return mm == null ? List.of() : mm;
     }
 
     /**
      * P1-08: applyVerification 実行時に history 行を 1 件追加し、
      * {@code applied_at} / {@code applied_by_user_no} / {@code source_file_hash} を記録する。
      * これにより後続 preview で同 (shop, transferDate) の L2 警告 (確定済) が出る。
      *
      * <p>convert と異なり CSV bytes は持たない (このフローは MF CSV 出力ではなく買掛検証反映のため)。
      * csv_body は NULL、csv_filename は applied マーカーであることを示す名前にする。
      */
     private void saveAppliedHistory(CachedUpload cached, PaymentMfPreviewResponse preview, Integer userNo) {
         try {
             LocalDateTime now = LocalDateTime.now();
             String yyyymmdd = cached.getTransferDate() == null ? "unknown"
                     : cached.getTransferDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
             TPaymentMfImportHistory h = TPaymentMfImportHistory.builder()
                     .shopNo(FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO)
                     .transferDate(cached.getTransferDate())
                     .sourceFilename(cached.getFileName())
                     .csvFilename("applied_" + yyyymmdd + ".marker")
                     .rowCount(preview.getTotalRows())
                     .totalAmount(preview.getTotalAmount())
                     .matchedCount(preview.getMatchedCount())
                     .diffCount(preview.getDiffCount())
                     .unmatchedCount(preview.getUnmatchedCount())
                     .csvBody(null)
                     .sourceFileHash(cached.getSourceFileHash())
                     .appliedAt(now)
                     .appliedByUserNo(userNo)
                     .addDateTime(now)
                     .addUserNo(userNo)
                     .build();
             historyRepository.save(h);
         } catch (Exception ex) {
             // history 保存失敗は本体検証結果に影響させない (verified_manually=true は既に永続化済)。
             log.error("applyVerification 履歴の保存に失敗 (検証反映は正常完了): file={}", cached.getFileName(), ex);
         }
     }
 
     /**
      * 同一 (shop_no, transaction_month) に対する applyVerification / exportVerifiedCsv を
      * 直列化する。PostgreSQL の {@code pg_advisory_xact_lock} は現在のトランザクション終了時に
      * 自動解放されるため、解放漏れリスクが無い。
+     *
+     * <p>G2-M2 (Codex Major #2, 2026-05-06): キー計算と native query 発行を {@link FinancePayableLock}
+     * に切り出し、{@link TAccountsPayableSummaryService#verify} / {@link ConsistencyReviewService#applyMfOverride}
+     * の 3 書込経路で同一 lock を取れるようにした。shop_no は {@link FinanceConstants#ACCOUNTS_PAYABLE_SHOP_NO}
+     * 固定 (買掛金管理は shop=1 のみ運用)。
      */
     private void acquireAdvisoryLock(LocalDate transactionMonth) {
-        long lockKey = ((long) ADVISORY_LOCK_CLASS << 32)
-                | (transactionMonth.toEpochDay() & 0xFFFF_FFFFL);
-        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:k)")
-                .setParameter("k", lockKey)
-                .getSingleResult();
+        FinancePayableLock.acquire(entityManager,
+                FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth);
     }
 
     /**
      * applyVerification 時に EXPENSE / SUMMARY / DIRECT_PURCHASE 主行および
      * {@code PAYABLE_xxx} / {@code DIRECT_PURCHASE_xxx} 副行を
      * {@code t_payment_mf_aux_row} に洗い替え保存する。
      * PAYABLE 主行は {@code t_accounts_payable_summary} 側で管理するためここでは対象外。
      * UNREGISTERED 行は CSV に出ないため保存しない。
      *
      * <p>C2 (2026-05-06) 修正: 従来 {@code PAYABLE_xxx} / {@code DIRECT_PURCHASE_xxx} 副行を
      * skip していたため、exportVerifiedCsv (DB-only 経路) で副行が消失していた。本修正で副行も保存し、
      * V038 で {@code chk_payment_mf_aux_rule_kind} 制約を拡張した。
      *
      * @param preview applyVerification で既に構築済みの preview を使い回す
      *                （再 buildPreview で N+1 を再度走らせないため）。
      */
     private void saveAuxRowsForVerification(CachedUpload cached, PaymentMfPreviewResponse preview,
                                             LocalDate txMonth, Integer userNo) {
         LocalDate transferDate = cached.getTransferDate();
         int deleted = auxRowRepository.deleteByShopAndTransactionMonthAndTransferDate(
                 FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, txMonth, transferDate);
         if (deleted > 0) {
             log.info("補助行を洗い替え: shop={} txMonth={} transferDate={} 削除={}件",
                     FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, txMonth, transferDate, deleted);
         }
 
         LocalDateTime now = LocalDateTime.now();
         int seq = 0;
         List<TPaymentMfAuxRow> toSave = new ArrayList<>();
         for (PaymentMfPreviewRow row : preview.getRows()) {
             String ruleKind = row.getRuleKind();
             if (ruleKind == null) continue;
             // C2 (2026-05-06):
             //   - PAYABLE 主行: t_accounts_payable_summary 由来 → aux 保存対象外 (重複排除)。
             //   - PAYABLE_* 副行 (PAYABLE_FEE/DISCOUNT/EARLY/OFFSET): aux 保存対象。
             //     exportVerifiedCsv で副行を再構築するため (従来は消失していた)。
             //   - DIRECT_PURCHASE 主行・副行: 共に aux 保存対象。
             //   - EXPENSE / SUMMARY 主行: 従来どおり aux 保存対象。
             //   - UNREGISTERED 行: CSV に出ないため保存しない。
             // V038 で chk_payment_mf_aux_rule_kind 制約を拡張済。
@@ -652,117 +714,128 @@ public class PaymentMfImportService {
          * &gt;0 のとき UI で「N 件は手動確定保護のため上書きしませんでした」を表示する。
          */
         private int skippedManuallyVerifiedCount;
         private List<String> unmatchedSuppliers;
     }
 
     public byte[] getHistoryCsv(Integer id) {
         return historyRepository.findById(id)
                 .map(TPaymentMfImportHistory::getCsvBody)
                 .orElseThrow(() -> new IllegalArgumentException("履歴が見つかりません: " + id));
     }
 
     public java.util.Optional<LocalDate> getHistoryTransferDate(Integer id) {
         return historyRepository.findById(id)
                 .map(TPaymentMfImportHistory::getTransferDate);
     }
 
     /**
      * SF-C20: ダウンロード時のファイル名生成用に、指定 uploadId の送金日を返す。
      * 期限切れ・未登録の場合は {@link IllegalArgumentException}。
      */
     public LocalDate getCachedTransferDate(String uploadId) {
         return getCached(uploadId).getTransferDate();
     }
 
     /**
      * BigDecimal を long に変換する（小数点以下は切り捨て）。
      * 手入力由来の verifiedAmount などで scale&gt;0 が混入しても例外にならないよう
      * 明示的に {@link RoundingMode#DOWN} で丸める。
      */
     private static long toLongFloor(BigDecimal v) {
         return v.setScale(0, RoundingMode.DOWN).longValueExact();
     }
 
     /**
      * 同一 supplier_no に属する税率別行群から、MF出力用の合計額を算出する (G2-M1, V040 で source 列ベースに改修)。
      * <p>判定は {@code verification_source} 列で行う:
      * <ul>
      *   <li><b>全行 BULK_VERIFICATION</b>: 振込明細一括検証で全税率行に同値の集約値が冗長保持されるため、
      *       代表 1 行の値を採用する (SUM すると件数倍の重複になる)。
-     *       不変条件として全行同値のはずだが、念のため per-row 値を比較し、不一致なら WARN ログ +
-     *       SUM フォールバック (DB 直接 UPDATE などの異常検知)。</li>
+     *       不変条件として全行同値のはずだが、念のため per-row 値を比較し、不一致なら
+     *       <b>{@link FinanceInternalException} を throw して fail-closed</b> する
+     *       (Codex Critical #1, 2026-05-06)。</li>
      *   <li><b>1 行でも MANUAL_VERIFICATION / MF_OVERRIDE / NULL が混在</b>: 税率別に異なる値が入りうるため SUM。
      *       MANUAL は単一 PK 更新、MF_OVERRIDE は税率別按分で書込まれているため、SUM が正しい集約値となる。</li>
      * </ul>
      * <p>旧実装は「全行 verifiedAmount 一致 → 代表値、不一致 → SUM」と金額パターンで推定していたが、
      * (a) MANUAL で偶然全行同値の場合に過少計上、(b) BULK 後の単行修正で過大計上のリスクがあった。
      * V040 で書込経路を {@code verification_source} 列に明示記録する運用に切替。
      * <p>verifiedAmount が null の行は taxIncludedAmountChange にフォールバック。
+     *
+     * <p><b>Codex Critical #1 (2026-05-06)</b>: 旧版は BULK 不一致時に WARN ログ + SUM フォールバックとして
+     * いたが、SUM すると税率行数分に金額が膨らむ (= 過大計上で MF CSV に流出) ため、運用上は隔離すべき
+     * 異常として {@link FinanceInternalException} を throw する。client には 422 で「内部エラー」を返し、
+     * admin が DB 状態を点検 + 修復するまで CSV 生成を停止させる。
+     * 関連 runbook: {@code claudedocs/runbook-payment-mf-bulk-invariant-violation.md}
      */
     private static long sumVerifiedAmountForGroup(List<TAccountsPayableSummary> group) {
         if (group.isEmpty()) return 0L;
 
         List<Long> perRow = new ArrayList<>(group.size());
         for (TAccountsPayableSummary s : group) {
             BigDecimal v = s.getVerifiedAmount() != null ? s.getVerifiedAmount()
                     : s.getTaxIncludedAmountChange();
             perRow.add(v == null ? 0L : toLongFloor(v));
         }
 
         // 全行 BULK_VERIFICATION 由来かを source 列で判定 (G2-M1)。
         // null 行 (未検証) や MANUAL / MF_OVERRIDE が 1 行でも混じれば SUM。
         boolean allBulk = group.stream()
                 .allMatch(s -> FinanceConstants.VERIFICATION_SOURCE_BULK.equals(s.getVerificationSource()));
         if (allBulk) {
             long first = perRow.get(0);
             boolean allSame = perRow.stream().allMatch(x -> x == first);
             if (allSame) {
                 // 正常: 集約値冗長保持
                 return first;
             }
             // 異常: BULK_VERIFICATION の不変条件 (全行同値) が崩れている。
-            // DB 直接 UPDATE 等の運用異常を検知し、過大化警戒として SUM フォールバック。
-            log.warn("BULK_VERIFICATION 不変条件違反: 同 (shop, supplier, txMonth) で verified_amount 不一致"
-                    + " supplier_no={} txMonth={} perRow={}",
-                    group.get(0).getSupplierNo(), group.get(0).getTransactionMonth(), perRow);
-            return perRow.stream().mapToLong(Long::longValue).sum();
+            // 旧版は SUM フォールバックしていたが、SUM は税率行数分の過大計上になり MF CSV 出力に流出するため、
+            // Codex Critical #1 (2026-05-06) で fail-closed (例外 throw) に変更。
+            // FinanceInternalException → FinanceExceptionHandler が 422 + 汎用メッセージで応答し、
+            // CSV 生成 / verified export を停止させる。admin は runbook に従い DB 状態を点検して修復する。
+            Integer supplierNo = group.get(0).getSupplierNo();
+            LocalDate txMonth = group.get(0).getTransactionMonth();
+            throw new FinanceInternalException(
+                    "BULK_VERIFICATION 不変条件違反 (全税率行同値想定だが不一致): supplier_no="
+                            + supplierNo + " txMonth=" + txMonth + " perRow=" + perRow);
         }
 
         // MANUAL / MF_OVERRIDE / NULL 混在: 税率別 SUM (本来の集約値)
         return perRow.stream().mapToLong(Long::longValue).sum();
     }
 
     /** P1-09 テスト用: {@link #sumVerifiedAmountForGroup} を package 外から呼ぶためのフック。 */
     static long sumVerifiedAmountForGroupForTest(List<TAccountsPayableSummary> group) {
         return sumVerifiedAmountForGroup(group);
     }
 
     /**
      * G2-M10 (V040, 2026-05-06): 同 supplier × txMonth の税率別行群のうち、
      * 1 行でも MANUAL_VERIFICATION (UI 手入力 verify) があるかを判定する。
      * <p>true の場合、再 upload (applyVerification) は当該 supplier 全体を保護対象としてスキップする。
      * <p>判定は {@code verification_source} 列で行い、verification_note 接頭辞には依存しない
      * (旧実装は note 文字列推定で偽判定リスクがあった)。
      */
     static boolean isAnyManuallyLocked(List<TAccountsPayableSummary> group) {
         return group.stream().anyMatch(s ->
                 Boolean.TRUE.equals(s.getVerifiedManually())
                         && FinanceConstants.VERIFICATION_SOURCE_MANUAL.equals(s.getVerificationSource()));
     }
 
     // ===========================================================
     // 検証済み買掛金からの MF CSV 出力 (Excel 再アップロード不要)
     // ===========================================================
 
     @Data
     @Builder
     public static class VerifiedExportResult {
         private byte[] csv;
         private int rowCount;
         private int payableCount;
         private int auxCount;
         private long totalAmount;
         /** ルール未登録で CSV 行を生成できず除外した supplier_code + supplier_name */
         private List<String> skippedSuppliers;
         private LocalDate transactionMonth;
     }
@@ -1252,86 +1325,101 @@ public class PaymentMfImportService {
                         payablesByCode.get(reconcileCode), e.amount);
                 b.matchStatus(rr.status).payableAmount(rr.payableAmount)
                         .payableDiff(rr.diff).supplierNo(rr.supplierNo);
                 if ("MATCHED".equals(rr.status)) matched++;
                 else if ("DIFF".equals(rr.status)) diff++;
                 else unmatched++;
             } else {
                 b.matchStatus("NA");
             }
             rows.add(b.build());
 
             // P1-03 案 D-2 / P1-07 案 D: 5日払い PAYABLE / 20日払い DIRECT_PURCHASE 副行を amount>0 のとき追加。
             // PAYABLE 系: 借方=買掛金 (親 PAYABLE と同じ)、貸方=該当勘定
             // DIRECT_PURCHASE 系: 借方=仕入高 (親 DIRECT_PURCHASE と同じ)、貸方=該当勘定
             // supplier_code/sourceName は親と同じ。
             if (needsSubRows) {
                 String feeKind = isDirectPurchase ? "DIRECT_PURCHASE_FEE" : "PAYABLE_FEE";
                 String discountKind = isDirectPurchase ? "DIRECT_PURCHASE_DISCOUNT" : "PAYABLE_DISCOUNT";
                 String earlyKind = isDirectPurchase ? "DIRECT_PURCHASE_EARLY" : "PAYABLE_EARLY";
                 String offsetKind = isDirectPurchase ? "DIRECT_PURCHASE_OFFSET" : "PAYABLE_OFFSET";
                 if (feeAmt > 0L) {
                     rows.add(buildAttributeSubRow(rule, e, feeKind, feeAmt,
                             "仕入値引・戻し高", "物販事業部", "課税仕入-返還等 10%",
                             "振込手数料値引／" + e.sourceName));
                 }
                 if (discountAmt > 0L) {
                     rows.add(buildAttributeSubRow(rule, e, discountKind, discountAmt,
                             "仕入値引・戻し高", "物販事業部", "課税仕入-返還等 10%",
                             "値引／" + e.sourceName));
                 }
                 if (earlyAmt > 0L) {
                     rows.add(buildAttributeSubRow(rule, e, earlyKind, earlyAmt,
                             "早払収益", "物販事業部", "非課税売上",
                             "早払収益／" + e.sourceName));
                 }
                 if (offsetAmt > 0L) {
                     // G2-M8: OFFSET 副行貸方科目はマスタ管理に移行 (m_offset_journal_rule)。
                     // 税理士確認後に admin が UI から値を変更可能。
                     // V041 seed では従来ハードコード値 (仕入値引・戻し高 / 物販事業部 /
                     // 課税仕入-返還等 10%) と同一の値を投入しているため migration 適用直後は挙動不変。
+                    //
+                    // Codex Major fix: マスタ欠落 (将来 shop_no=2 追加 / admin UI で誤って論理削除)
+                    // による preview/apply ブロックを防ぐため、orElseThrow ではなく hardcoded default で
+                    // フォールバック (= V041 seed と同値) し、WARN ログで運用者に通知する。
+                    // 二段防御として MOffsetJournalRuleService.delete() で「最後の active 行」削除を禁止する。
                     MOffsetJournalRule offsetRule = offsetJournalRuleRepository
                             .findByShopNoAndDelFlg(FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, "0")
-                            .orElseThrow(() -> new IllegalStateException(
-                                    "m_offset_journal_rule の shop_no="
-                                            + FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO
-                                            + " (active) 行が未登録です"));
+                            .orElseGet(() -> {
+                                log.warn("m_offset_journal_rule の shop_no={} (active) 行が未登録のため"
+                                        + " hardcoded default (V041 seed と同値) で fallback します。"
+                                        + " admin UI から正規行を再投入してください。",
+                                        FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO);
+                                return MOffsetJournalRule.builder()
+                                        .shopNo(FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO)
+                                        .creditAccount("仕入値引・戻し高")
+                                        .creditDepartment("物販事業部")
+                                        .creditTaxCategory("課税仕入-返還等 10%")
+                                        .summaryPrefix("相殺／")
+                                        .delFlg("0")
+                                        .build();
+                            });
                     rows.add(buildAttributeSubRow(rule, e, offsetKind, offsetAmt,
                             offsetRule.getCreditAccount(),
                             offsetRule.getCreditDepartment(),
                             offsetRule.getCreditTaxCategory(),
                             offsetRule.getSummaryPrefix() + e.sourceName));
                 }
             }
         }
 
         // P1-03 案 D: 旧 SUMMARY 集約 (振込手数料値引/早払収益 合計仕訳 2 行) を撤去。
         // 代わりに supplier 別に展開された PAYABLE_FEE / PAYABLE_EARLY 行で同等の会計表現になる。
 
         // 整合性チェック (1 円も許容しない)
         // G2-M3: 旧実装は最初の合計行 (5日払い) のみ summary を捕まえて以後の合計行 (20日払い) を捨てていた。
         // 現在は section 別 summary を持つので、section ごとに chk1/chk3 を判定し、
         // mismatch を per-supplier 違反一覧に [5日払い] / [20日払い] 接頭辞付きで追記する。
         // UI/DTO 互換性のため、AmountReconciliation の summary* 系フィールドは
         // 両 section 合算値 (= 旧 v1 ParsedExcel 構造と同一意味) を返す。
         PaymentMfExcelParser.SectionSummary sum5 = summaryOf(cached, PaymentMfSection.PAYMENT_5TH);
         PaymentMfExcelParser.SectionSummary sum20 = summaryOf(cached, PaymentMfSection.PAYMENT_20TH);
 
         long summary5Fee = summaryLong(sum5, s -> s.sourceFee);
         long summary5Early = summaryLong(sum5, s -> s.earlyPayment);
         long summary5Transfer = summaryLong(sum5, s -> s.transferAmount);
         long summary5Invoice = summaryLong(sum5, s -> s.invoiceTotal);
         long summary20Fee = summaryLong(sum20, s -> s.sourceFee);
         long summary20Early = summaryLong(sum20, s -> s.earlyPayment);
         long summary20Transfer = summaryLong(sum20, s -> s.transferAmount);
         long summary20Invoice = summaryLong(sum20, s -> s.invoiceTotal);
 
         long summaryFee = summary5Fee + summary20Fee;
         long summaryEarly = summary5Early + summary20Early;
         long summaryTransfer = summary5Transfer + summary20Transfer;
         long summaryInvoice = summary5Invoice + summary20Invoice;
 
         // チェック1 (G2-M3: section 別判定): C(請求額) - F(振込手数料) - H(早払) == E(振込金額)
         // section が空 (= 5日払いのみの Excel で 20日払い summary なし) の場合は 0 + 0 で自動 match。
         long expected5Transfer = summary5Invoice - summary5Fee - summary5Early;
         long expected20Transfer = summary20Invoice - summary20Fee - summary20Early;
         long excel5Diff = sum5 == null ? 0L : (summary5Transfer - expected5Transfer);
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

2026-05-07T00:22:41.379285Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$p='"'backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java'; "'$lines=Get-Content -LiteralPath $p; for($i=230;$i -le 430;$i++){ '"'{0,5}: {1}' -f "'$i,$lines[$i-1] }'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$p='"'backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java'; "'$lines=Get-Content -LiteralPath $p; for($i=230;$i -le 430;$i++){ '"'{0,5}: {1}' -f "'$i,$lines[$i-1] }' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$p='"'backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java'; "'$lines=Get-Content -LiteralPath $p; for($i=230;$i -le 430;$i++){ '"'{0,5}: {1}' -f "'$i,$lines[$i-1] }'` rejected: blocked by policy
2026-05-07T00:22:49.177338Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "@'
from pathlib import Path
p=Path('backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java')
lines=p.read_text(encoding='utf-8').splitlines()
for i in range(230,431):
    print(f'{i:5}: {lines[i-1]}')
'@ | python -"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "@'
from pathlib import Path
p=Path('backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java')
lines=p.read_text(encoding='utf-8').splitlines()
for i in range(230,431):
    print(f'{i:5}: {lines[i-1]}')
'@ | python -" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "@'
from pathlib import Path
p=Path('backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java')
lines=p.read_text(encoding='utf-8').splitlines()
for i in range(230,431):
    print(f'{i:5}: {lines[i-1]}')
'@ | python -"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -LiteralPath 'backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java' | Select-Object -Skip 229 -First 201" in C:\project\odamitsu-data-hub
 succeeded in 1635ms:
     * 本メソッド経由でも force false 経路で per-supplier 1 円不一致は 422 ブロックとなる。
     *
     * @deprecated G2-M2 (2026-05-06) 以降、新コードは {@link #applyVerification(String, Integer, boolean, String)}
     *             を使い、{@code force} の意図と承認理由を明示すること。
     */
    @Deprecated
    public VerifyResult applyVerification(String uploadId, Integer userNo) {
        return applyVerification(uploadId, userNo, false, null);
    }

    /**
     * 旧 3-引数 API 後方互換用 (Codex Major #4 で {@code forceReason} を追加した 4 引数版を呼ぶ)。
     * 既存テストと旧 client が 3 引数で呼んでいるため、シグネチャは維持する。
     * <p>{@code force=true} で本オーバーロードを呼ぶと {@code forceReason} が無いため
     * {@link FinanceBusinessException} (FORCE_REASON_REQUIRED) が投げられる点に注意。
     *
     * @deprecated G2-M4 fix (2026-05-06) 以降、新コードは
     *             {@link #applyVerification(String, Integer, boolean, String)} を使い、
     *             {@code force=true} 時は forceReason も渡すこと。
     */
    @Deprecated
    public VerifyResult applyVerification(String uploadId, Integer userNo, boolean force) {
        return applyVerification(uploadId, userNo, force, null);
    }

    /**
     * アップロード済み振込明細を正として、買掛金一覧(t_accounts_payable_summary)に検証結果を一括反映する。
     * verified_manually=true で手動確定扱いにし、SMILE再検証バッチで上書きされないようにする。
     *
     * <p>並列呼び出しでの上書き競合を防ぐため、同一 (shop_no, transaction_month) に対しては
     * PostgreSQL advisory lock で直列化する。5日払い Excel と 20日払い Excel が別プロセス/
     * 別 UI から同時適用された場合でも、後着は先行 tx のコミット完了後に実行される。
     *
     * <p>G2-M2 (2026-05-06): {@code force} パラメータ追加。
     * <ul>
     *   <li>{@code force=false} (推奨デフォルト): per-supplier 1 円整合性違反
     *       ({@code AmountReconciliation#perSupplierMismatches}) が 1 件でもあれば
     *       {@link FinanceBusinessException} (422) でブロックする。
     *       業務的には preview で違反一覧を確認 → Excel を修正 → 再アップロード が正しい運用。</li>
     *   <li>{@code force=true}: 違反を許容して反映する。
     *       {@code finance_audit_log.reason} に {@code FORCE_APPLIED: per-supplier mismatches=...}
     *       で違反詳細を補足記録し、後追い監査を可能にする。</li>
     * </ul>
     *
     * <p>Codex Major #4 (2026-05-06): {@code forceReason} 必須化。
     * <ul>
     *   <li>{@code force=true} かつ {@code forceReason} が null / 空文字 →
     *       {@link FinanceBusinessException} (FORCE_REASON_REQUIRED, 400) で拒否。</li>
     *   <li>{@code force=false} の場合 {@code forceReason} は無視される。</li>
     * </ul>
     * 二段認可は実装スコープ過大のため運用 runbook (= 最低 2 名で内容確認) にて担保し、
     * 実装は forceReason 必須化のみで「誰が」「なぜ」を audit に残せるようにする。
     */
    @Transactional
    @AuditLog(table = "t_accounts_payable_summary", operation = "payment_mf_apply",
            pkExpression = "{'uploadId': #a0, 'userNo': #a1, 'force': #a2}",
            captureArgsAsAfter = true, captureReturnAsAfter = true)
    public VerifyResult applyVerification(String uploadId, Integer userNo, boolean force, String forceReason) {
        CachedUpload cached = getCached(uploadId);
        if (cached.getTransferDate() == null) {
            throw new IllegalArgumentException("送金日が取得できていません。xlsxを再アップロードしてください");
        }
        // Codex Major #4: force=true 時の forceReason 必須化 (advisory lock 取得前に bail-out)。
        // null / 空文字は不可。空白のみ ("   ") も拒否し、運用上意味のある理由を強制する。
        if (force && (forceReason == null || forceReason.isBlank())) {
            throw new FinanceBusinessException(
                    "force=true 指定時は forceReason (承認理由) が必須です。"
                            + "承認者・確認者・業務理由を含めて入力してください",
                    FinanceConstants.ERROR_CODE_FORCE_REASON_REQUIRED);
        }
        LocalDate txMonth = deriveTransactionMonth(cached.getTransferDate());
        acquireAdvisoryLock(txMonth);

        // G2-M2: per-supplier 1 円整合性違反のサーバー側ブロック。
        // preview を一度だけ事前構築し、ブロック判定 (force) と aux 保存の両方で使い回す。
        // (このタイミングなら DB ロック取得後で他 tx の影響を受けにくい)。
        PaymentMfPreviewResponse blockingPreview = buildPreview(uploadId, cached);
        List<String> mismatches = perSupplierMismatchesOf(blockingPreview);
        if (!mismatches.isEmpty() && !force) {
            // 422 + コード PER_SUPPLIER_MISMATCH を返す。client 側で「force=true で再実行」UI 分岐に使う。
            // 件数のみ返却し、supplier 名は preview レスポンスから別途取得させる
            // (例外メッセージに長い detail を載せると i18n / log noise の原因になる)。
            throw new FinanceBusinessException(
                    "per-supplier 1 円整合性違反 " + mismatches.size() + " 件あり。"
                            + "プレビュー画面で詳細を確認の上、強制実行する場合は force=true を指定してください",
                    FinanceConstants.ERROR_CODE_PER_SUPPLIER_MISMATCH);
        }

        // G2-M10 (V040): note 接頭辞は UI 表示用にのみ利用 (BULK/MANUAL 判定には verification_source を使う)。
        @SuppressWarnings("deprecation")
        String bulkPrefix = FinanceConstants.VERIFICATION_NOTE_BULK_PREFIX;
        String note = bulkPrefix + cached.getFileName() + " " + cached.getTransferDate();

        List<MPaymentMfRule> rules = ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0");
        Map<String, MPaymentMfRule> byCode = new LinkedHashMap<>();
        Map<String, MPaymentMfRule> bySource = new LinkedHashMap<>();
        for (MPaymentMfRule r : rules) {
            if (r.getPaymentSupplierCode() != null && !r.getPaymentSupplierCode().isBlank())
                byCode.putIfAbsent(r.getPaymentSupplierCode().trim(), r);
            bySource.putIfAbsent(normalize(r.getSourceName()), r);
        }

        // 事前パス: 当該 Excel に含まれる全 supplierCode を集め、t_accounts_payable_summary を一括取得する (N+1 解消)。
        // 5日払い (PAYMENT_5TH) のみが PAYABLE 突合対象 (20日払いセクションは DIRECT_PURCHASE 自動降格)。
        Set<String> codesToReconcile = new java.util.LinkedHashSet<>();
        for (PaymentMfExcelParser.ParsedEntry e : cached.getEntries()) {
            if (e.section == PaymentMfSection.PAYMENT_20TH) continue;
            MPaymentMfRule rule = null;
            if (e.supplierCode != null) rule = byCode.get(e.supplierCode);
            if (rule == null) rule = bySource.get(normalize(e.sourceName));
            if (rule == null || !"PAYABLE".equals(rule.getRuleKind())) continue;
            String reconcileCode = e.supplierCode != null ? e.supplierCode
                    : (rule.getPaymentSupplierCode() != null && !rule.getPaymentSupplierCode().isBlank()
                            ? rule.getPaymentSupplierCode() : null);
            if (reconcileCode != null) codesToReconcile.add(reconcileCode);
        }
        Map<String, List<TAccountsPayableSummary>> payablesByCode = codesToReconcile.isEmpty()
                ? Map.of()
                : payableRepository.findByShopNoAndSupplierCodeInAndTransactionMonth(
                        FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, codesToReconcile, txMonth)
                    .stream()
                    .collect(Collectors.groupingBy(TAccountsPayableSummary::getSupplierCode));

        int matched = 0, diff = 0, notFound = 0, skipped = 0, skippedManuallyVerified = 0;
        List<String> unmatchedSuppliers = new ArrayList<>();
        BigDecimal matchThreshold = FinanceConstants.PAYMENT_REPORT_MINOR_DIFFERENCE;

        for (PaymentMfExcelParser.ParsedEntry e : cached.getEntries()) {
            // PAYABLE行のみ対象（20日払いセクションは DIRECT_PURCHASE 自動降格扱いで対象外）
            if (e.section == PaymentMfSection.PAYMENT_20TH) { skipped++; continue; }
            MPaymentMfRule rule = null;
            if (e.supplierCode != null) rule = byCode.get(e.supplierCode);
            if (rule == null) rule = bySource.get(normalize(e.sourceName));
            if (rule == null || !"PAYABLE".equals(rule.getRuleKind())) { skipped++; continue; }
            String reconcileCode = e.supplierCode != null ? e.supplierCode
                    : (rule.getPaymentSupplierCode() != null && !rule.getPaymentSupplierCode().isBlank()
                            ? rule.getPaymentSupplierCode() : null);
            if (reconcileCode == null) { skipped++; continue; }

            List<TAccountsPayableSummary> list = payablesByCode.getOrDefault(reconcileCode, List.of());
            if (list.isEmpty()) {
                notFound++;
                unmatchedSuppliers.add(e.sourceName + "(" + reconcileCode + ")");
                continue;
            }

            // P1-08 Q3-(ii) / Cluster D SF-02 同パターン (G2-M10, V040 で source 列ベースに改修):
            //   `verified_manually=true` かつ <b>UI 手入力 verify (MANUAL_VERIFICATION)</b> 由来の行は
            //   再 upload で上書きしない。BULK_VERIFICATION (再 upload) や MF_OVERRIDE は上書き対象。
            //   税率別複数行のうち 1 行でも MANUAL があれば supplier 全体を保護対象とする
            //   (税率別 verified_amount/note を分割保持していないため、混在状態を作らないこと優先)。
            //
            //   旧実装は verification_note 接頭辞文字列 (VERIFICATION_NOTE_BULK_PREFIX) で BULK/MANUAL を
            //   推定していたが、ユーザが偶然 "振込明細検証 " で始まる note を手入力すると保護が外れる
            //   リスクがあった。V040 で verification_source 列を追加し、書込経路を明示記録する運用に切替。
            boolean anyManuallyLocked = isAnyManuallyLocked(list);
            if (anyManuallyLocked) {
                skippedManuallyVerified++;
                log.info("verified_manually=true (単一 verify) 行をスキップ: supplier={}({}) txMonth={}",
                        e.sourceName, reconcileCode, txMonth);
                continue;
            }

            BigDecimal payable = BigDecimal.ZERO;
            for (TAccountsPayableSummary s : list) {
                BigDecimal v = s.getTaxIncludedAmountChange() != null
                        ? s.getTaxIncludedAmountChange() : s.getTaxIncludedAmount();
                if (v != null) payable = payable.add(v);
            }
            BigDecimal invoice = BigDecimal.valueOf(e.amount);
            BigDecimal difference = payable.subtract(invoice);
            boolean isMatched = difference.abs().compareTo(matchThreshold) <= 0;
            if (isMatched) matched++; else diff++;

            // 振込明細の請求額は支払先単位で1件だが、DBは税率別に複数行ある場合がある。
            // UI 表示用途のため、全税率行に同じ verified_amount を書き込む
            // （税率別の請求額内訳は Excel 側に存在しないため、合計値を代表値として保持）。
            // V026: 税抜側 (verified_amount_tax_excluded) も税率別に逆算して書き込む
            // → MF CSV 出力の「仕入高」金額が仕入先請求書と一致する。
            // auto_adjusted_amount に自動調整額 (verified - 元の自社計算) を記録 (監査証跡)。
            BigDecimal autoAdjusted = isMatched ? invoice.subtract(payable) : BigDecimal.ZERO;
            String adjustNote = isMatched && autoAdjusted.signum() != 0
                    ? note + " | 自動調整: 元 \" + payable + " → \" + invoice
                      + " (" + (autoAdjusted.signum() > 0 ? "+" : "") + "\" + autoAdjusted + ")"
                    : note;
            for (TAccountsPayableSummary s : list) {
                s.setVerificationResult(isMatched ? 1 : 0);
                s.setPaymentDifference(difference);
                s.setVerifiedManually(true);
                s.setMfExportEnabled(isMatched);
                s.setVerificationNote(adjustNote);
                s.setVerifiedAmount(invoice);
                // V026: 税抜 verified を税率別に逆算 (単一税率前提。複数税率の仕入先は手動調整で対応)。
                BigDecimal taxRate = s.getTaxRate() != null ? s.getTaxRate() : BigDecimal.TEN;
                BigDecimal divisor = BigDecimal.valueOf(100).add(taxRate);
                BigDecimal invoiceTaxExcl = invoice.multiply(BigDecimal.valueOf(100))
                        .divide(divisor, 0, java.math.RoundingMode.DOWN);
                s.setVerifiedAmountTaxExcluded(invoiceTaxExcl);
                s.setAutoAdjustedAmount(autoAdjusted);
                // 5日払いセクション (PAYMENT_5TH) の PAYABLE のみここに到達する。
                // Excel の送金日を CSV 取引日として記録する。
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -LiteralPath 'backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java' | Select-Object -Skip 430 -First 160" in C:\project\odamitsu-data-hub
 succeeded in 1568ms:
                s.setMfTransferDate(cached.getTransferDate());
                // G2-M1/M10 (V040): 書込経路を明示記録 (read 側 sumVerifiedAmountForGroup と
                // 再 upload 保護判定で参照される)。
                s.setVerificationSource(FinanceConstants.VERIFICATION_SOURCE_BULK);
                payableService.save(s);
            }
        }

        // 補助行(EXPENSE/SUMMARY/DIRECT_PURCHASE) を (shop, 取引月, 送金日) 単位で洗い替え保存。
        // G2-M2: ブロック判定で構築した blockingPreview をそのまま使い回す
        // (従来は saveAuxRowsForVerification 内で再 buildPreview していたため N+1 が 2 周していた)。
        PaymentMfPreviewResponse preview = blockingPreview;
        saveAuxRowsForVerification(cached, preview, txMonth, userNo);

        // P1-08: 確定済マークを history に永続化 (preview L2 警告の根拠データ)。
        saveAppliedHistory(cached, preview, userNo);

        // G2-M2: force=true で per-supplier 不一致を許容して反映した場合、
        // 補足 audit 行を追加して reason に違反詳細を残す。
        // 既存 @AuditLog aspect が記録する after snapshot 行とは別 row として書く
        // (AOP 拡張せずに済む & 後で reason 列で grep できる)。
        // Codex Major #4: forceReason (承認者・確認者・業務理由) も audit reason に含める。
        if (force && !mismatches.isEmpty()) {
            writeForceAppliedAuditRow(uploadId, userNo, cached, txMonth, mismatches, forceReason);
        }

        return VerifyResult.builder()
                .transferDate(cached.getTransferDate())
                .transactionMonth(txMonth)
                .matchedCount(matched)
                .diffCount(diff)
                .notFoundCount(notFound)
                .skippedCount(skipped)
                .skippedManuallyVerifiedCount(skippedManuallyVerified)
                .unmatchedSuppliers(unmatchedSuppliers)
                .build();
    }

    /**
     * G2-M2 (2026-05-06): {@code force=true} で per-supplier 1 円違反を許容して反映した時の補足 audit 行。
     * <p>{@link FinanceAuditWriter#write} を直接呼んで {@code reason} 列に件数 + 先頭
     * {@link #FORCE_AUDIT_MISMATCH_DETAIL_LIMIT} 件の詳細サマリを記録する (容量配慮)。
     * Codex Major #3 (2026-05-06): {@code force_mismatch_details} JSONB 列 (V043) に
     * <b>全件</b>を構造化保存し、reason との乖離を防ぐ。
     * <p>Codex Major #4 (2026-05-06): {@code forceReason} (承認者・確認者・業務理由) を reason 末尾に
     * {@code reason="..."} 形式で連結する。null の場合は付加しない (旧 client 互換用、3 引数 deprecated 経路)。
     * <p>writer Bean が無い (test 環境など) や書込み失敗は本体反映を巻き戻さない (warn ログのみ)。
     */
    private void writeForceAppliedAuditRow(String uploadId, Integer userNo, CachedUpload cached,
                                           LocalDate txMonth, List<String> mismatches, String forceReason) {
        if (financeAuditWriter == null) {
            log.warn("FinanceAuditWriter Bean 不在のため force=true 補足 audit を skip: uploadId={}", uploadId);
            return;
        }
        try {
            String reason = buildForceAppliedReason(mismatches, forceReason);
            ObjectMapper mapper = new ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode pk = mapper.createObjectNode()
                    .put("uploadId", uploadId)
                    .put("userNo", userNo == null ? null : userNo.toString())
                    .put("force", "true")
                    .put("transferDate", cached.getTransferDate() == null ? null
                            : cached.getTransferDate().toString())
                    .put("transactionMonth", txMonth == null ? null : txMonth.toString())
                    .put("fileName", cached.getFileName());
            // Codex Major #3: 全件を JSONB 列に構造化保存 (reason 50 件切り詰めとの乖離防止)。
            com.fasterxml.jackson.databind.node.ArrayNode detailsArr = mapper.createArrayNode();
            for (String m : mismatches) {
                detailsArr.add(mapper.createObjectNode().put("line", m));
            }
            financeAuditWriter.write(
                    "t_accounts_payable_summary",
                    "payment_mf_apply_force",
                    userNo,
                    userNo == null ? "SYSTEM" : "USER",
                    pk,
                    null,
                    null,
                    reason,
                    null,
                    null,
                    detailsArr);
            log.warn("payment_mf_apply force=true で per-supplier 不一致 {} 件を許容: uploadId={} txMonth={}",
                    mismatches.size(), uploadId, txMonth);
        } catch (Exception ex) {
            log.error("force=true 補足 audit 行の書込に失敗 (本体反映は完了済): uploadId={} err={}",
                    uploadId, ex.toString());
        }
    }

    /**
     * G2-M2: 補足 audit 行の {@code reason} 文字列を組み立てる (旧 1 引数版、後方互換用)。
     * <p>新規コードは {@link #buildForceAppliedReason(List, String)} で forceReason も渡すこと。
     * @deprecated Codex Major #4 (2026-05-06) 以降。
     */
    @Deprecated
    static String buildForceAppliedReason(List<String> mismatches) {
        return buildForceAppliedReason(mismatches, null);
    }

    /**
     * G2-M2: 補足 audit 行の {@code reason} 文字列を組み立てる。
     * <p>形式 (forceReason あり): {@code "FORCE_APPLIED: per-supplier mismatches count=N, details=[...], reason=\"...\""}
     * <p>形式 (forceReason なし): {@code "FORCE_APPLIED: per-supplier mismatches count=N, details=[...]"}
     * <p>{@link #FORCE_AUDIT_MISMATCH_DETAIL_LIMIT} 件超過時は {@code "...(+M more)"} で打ち切り。
     * 全件は別途 {@code finance_audit_log.force_mismatch_details} JSONB 列に保存される (V043, Codex Major #3)。
     * <p>Codex Major #4 (2026-05-06): {@code forceReason} (承認者・確認者・業務理由) を末尾に追記。
     * null / 空文字なら省略 (旧 deprecated 経路互換)。
     */
    static String buildForceAppliedReason(List<String> mismatches, String forceReason) {
        StringBuilder sb = new StringBuilder("FORCE_APPLIED: per-supplier mismatches count=");
        int total = mismatches == null ? 0 : mismatches.size();
        sb.append(total);
        if (total > 0) {
            int show = Math.min(total, FORCE_AUDIT_MISMATCH_DETAIL_LIMIT);
            sb.append(", details=[");
            for (int i = 0; i < show; i++) {
                if (i > 0) sb.append(", ");
                sb.append(mismatches.get(i));
            }
            if (total > show) {
                sb.append(", ...(+").append(total - show).append(" more)");
            }
            sb.append(']');
        }
        if (forceReason != null && !forceReason.isBlank()) {
            // 二重引用符の入れ子を避けるため、value 側の " は ' に置換。改行は半角スペースに正規化
            // (audit log の grep 安定化)。
            String sanitized = forceReason.replace('"', '\'').replace('\n', ' ').replace('\r', ' ');
            sb.append(", reason=\"").append(sanitized).append('"');
        }
        return sb.toString();
    }

    /**
     * G2-M2: preview から per-supplier 1 円不一致リストを安全に取り出す。
     * {@code amountReconciliation} or {@code perSupplierMismatches} が null の場合は空リスト。
     *
     * <p>パッケージ可視 + non-static にしてテストから {@code Mockito.spy} で差し替え可能にする
     * (Excel fixture を mismatch 用に作るのが難しいため、テストフックとして残す)。
     */
    List<String> perSupplierMismatchesOf(PaymentMfPreviewResponse preview) {
        if (preview == null || preview.getAmountReconciliation() == null) return List.of();
        List<String> mm = preview.getAmountReconciliation().getPerSupplierMismatches();
        return mm == null ? List.of() : mm;
    }

    /**
     * P1-08: applyVerification 実行時に history 行を 1 件追加し、
     * {@code applied_at} / {@code applied_by_user_no} / {@code source_file_hash} を記録する。
     * これにより後続 preview で同 (shop, transferDate) の L2 警告 (確定済) が出る。
     *
     * <p>convert と異なり CSV bytes は持たない (このフローは MF CSV 出力ではなく買掛検証反映のため)。
     * csv_body は NULL、csv_filename は applied マーカーであることを示す名前にする。
     */
    private void saveAppliedHistory(CachedUpload cached, PaymentMfPreviewResponse preview, Integer userNo) {
        try {
            LocalDateTime now = LocalDateTime.now();
            String yyyymmdd = cached.getTransferDate() == null ? "unknown"
                    : cached.getTransferDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git show --no-ext-diff --unified=50 60d7d81 -- backend/src/main/java/jp/co/oda32/audit/FinanceAuditWriter.java backend/src/main/java/jp/co/oda32/audit/FinanceAuditAspect.java backend/src/main/java/jp/co/oda32/domain/model/audit/FinanceAuditLog.java backend/src/main/resources/db/migration/V043__add_force_mismatch_details.sql' in C:\project\odamitsu-data-hub
 succeeded in 1246ms:
commit 60d7d815f07409beb1375fc08c96bd6dcfc8d13a
Author: kazuki <k_oda@odamitsu.com>
Date:   Thu May 7 09:18:25 2026 +0900

    fix(codex-r2): Codex 第 2 弾批判レビュー対応 (Critical + P1 + 9 Major + 1 Minor)
    
    3 commit (audit/mf/finance) を Codex 批判レビューにかけ、12 件の指摘を修正。
    
    ## Critical / P1 (即修正)
    * Critical: BULK 不変条件違反時の SUM フォールバックを fail-closed (例外) 化
      - PaymentMfImportService.sumVerifiedAmountForGroup で全行 BULK 不一致時に
        FinanceInternalException 422、過大計上の MF CSV 流出を遮断
    * P1: V033 再暗号化が片側カラムのみで refresh_token が旧鍵残るバグ修正
      - reencryptMfTokens() で access + refresh を 1 UPDATE で同時再暗号化
      - decrypt 失敗時 skip + WARN ログで idempotent 強化 (recipherOrSkip)
    
    ## Major (9 件)
    * MF P2: V037 ordering note は runbook §6' に集約 (V037 は revert で checksum 復元)
    * Audit P2: FinanceAuditAspect で Loader 解決失敗時の captureReturn/Args フォールバック追加
    * G2-M2 Major: verification_source 3 経路 (BULK/MANUAL/MF_OVERRIDE) を共通 advisory lock で直列化
      - FinancePayableLock util 切り出し、computePayableLockKey + acquire
      - applyVerification / verify / applyMfOverride で同一 lock キー
    * G2-M3 Major: force audit 全件保存 (V043 finance_audit_log.force_mismatch_details JSONB)
      - 50 件切り詰めの reason と全件 JSON を分離、UI 全件確認可能化への土台
    * G2-M4 Major: forceReason 必須化 + reason 空 → FORCE_REASON_REQUIRED 400
    * G2-M5 Major: 20日払い専用 Excel が PAYMENT_5TH 誤分類されるバグ修正
      - PaymentMfExcelParser.determineInitialSection で送金日 day による初期 section 判定
    * G2-M5 Major: OFFSET マスタ欠落時の hardcoded default fallback + 最後の active 行削除禁止
    * G2-M6 Major: P1-02 opening 注入空時の警告 (SupplierBalances/IntegrityReport DTO)
    * Frontend Major: force apply UX 強化 (CSV download, reviewedAll checkbox, forceReason 必須)
    
    ## Minor
    * G2-M? Minor: V044 で V040 backfill 検査 (NOTICE のみ、自動修復なし)
      - 実行結果: 560 行で history 突合なし = false positive (P1-08 導入前の歴史データ)
    
    ## 新規 migration
    - V043 finance_audit_log.force_mismatch_details JSONB + GIN index
    - V044 V040 backfill 検査 (RAISE NOTICE のみ)
    
    ## 新規 runbook
    - runbook-payment-mf-bulk-invariant-violation.md (Critical fix の運用手順)
    - runbook-payment-mf-force-apply.md (force=true の二段認可運用)
    
    ## CLAUDE.md
    - Review Guidelines セクション追加 (Repository Context / Review Priorities /
      Architecture Rules) — AI レビュー時の優先順位を明示
    
    ## テスト
    - 既存 233 + 新規 ~10 件 PASS、frontend tsc PASS、リグレッション 0
    - 5 group 並列修正で衝突なし
    
    Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>

diff --git a/backend/src/main/java/jp/co/oda32/audit/FinanceAuditAspect.java b/backend/src/main/java/jp/co/oda32/audit/FinanceAuditAspect.java
index 82c5633..1be5c2e 100644
--- a/backend/src/main/java/jp/co/oda32/audit/FinanceAuditAspect.java
+++ b/backend/src/main/java/jp/co/oda32/audit/FinanceAuditAspect.java
@@ -60,104 +60,114 @@ public class FinanceAuditAspect {
     public FinanceAuditAspect(
             FinanceAuditWriter writer,
             ObjectProvider<HttpServletRequest> httpRequestProvider,
             AuditEntityLoaderRegistry loaderRegistry) {
         this.writer = writer;
         this.httpRequestProvider = httpRequestProvider;
         this.loaderRegistry = loaderRegistry;
         // 専用 ObjectMapper: JavaTime対応 + 循環参照例外を出さず空に倒す
         // (@AuditExclude は @JsonIgnore を継承しているため、この mapper でも自動除外される)
         this.auditMapper = new ObjectMapper()
                 .registerModule(new JavaTimeModule())
                 .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                 .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                 .disable(SerializationFeature.FAIL_ON_SELF_REFERENCES);
     }
 
     @Around("@annotation(auditLog)")
     public Object record(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
         JsonNode pkJson = computePk(pjp, auditLog);
 
         Optional<AuditEntityLoader> loaderOpt = loaderRegistry.findByTable(auditLog.table());
 
         // C5: before snapshot を loader 経由で実 DB row から取得
         JsonNode beforeJson = loadSnapshot(loaderOpt, pkJson, "before");
 
         AuthInfo authInfo = resolveAuth();
         ReqInfo reqInfo = resolveRequest();
         String argsAux = buildArgsAux(pjp, auditLog);
 
         Object result;
         try {
             result = pjp.proceed();
         } catch (Throwable ex) {
             try {
                 String reason = composeReason("FAILED: " + ex.getClass().getSimpleName() + ": "
                         + truncate(ex.getMessage(), 600), argsAux);
                 writer.write(auditLog.table(), auditLog.operation(),
                         authInfo.userNo, authInfo.actorType,
                         pkJson, beforeJson, null,
                         reason, reqInfo.ip, reqInfo.userAgent);
             } catch (Exception logEx) {
                 log.error("[finance-audit] failed-path 監査ログ記録に失敗 (元例外を優先 throw): {}",
                         logEx.toString());
             }
             throw ex;
         }
 
         // C5: after snapshot を loader 経由で再 fetch (DELETE 後は null になり得る)
         JsonNode afterJson = loadSnapshot(loaderOpt, pkJson, "after");
 
-        // Loader が無い table で returnAsAfter が true の場合のフォールバック
-        if (afterJson == null && loaderOpt.isEmpty() && auditLog.captureReturnAsAfter()) {
+        // G3-M12-fix: after が null の場合、Loader 未登録 / PK shape mismatch の両方で
+        // captureReturnAsAfter / captureArgsAsAfter フォールバックを許容する。
+        // 例: PaymentMfImportService.applyVerification の pkExpression は
+        // {uploadId, userNo, force} だが、TAccountsPayableSummaryAuditLoader の PK は
+        // {shopNo, supplierNo, transactionMonth, taxRate}。Loader 登録ありでも loadByPk が
+        // 解決できず null を返すため、戻り値 (VerifyResult) を after に詰めて記録漏れを防ぐ。
+        // captureReturnAsAfter で null になる (戻り値が null など) 場合は captureArgsAsAfter
+        // を 2 段目のフォールバックとして使い、最低限引数 JSON が after に入るようにする。
+        if (afterJson == null && auditLog.captureReturnAsAfter()) {
             afterJson = serialize(result);
         }
+        if (afterJson == null && auditLog.captureArgsAsAfter()) {
+            afterJson = serializeArgs(pjp.getArgs());
+        }
 
         try {
             String reason = composeReason(null, argsAux);
             writer.write(auditLog.table(), auditLog.operation(),
                     authInfo.userNo, authInfo.actorType,
                     pkJson, beforeJson, afterJson,
                     reason, reqInfo.ip, reqInfo.userAgent);
         } catch (Exception logEx) {
             // 監査ログ書込み失敗は業務 tx の成功を巻き戻さない (記録漏れだけ警告)。
             log.error("[finance-audit] success-path 監査ログ記録に失敗: target={}, op={}, err={}",
                     auditLog.table(), auditLog.operation(), logEx.toString());
         }
 
         return result;
     }
 
     /**
      * C4: pkExpression (SpEL) または pkArgIndex から PK の JSON を構築する。
      */
     private JsonNode computePk(ProceedingJoinPoint pjp, AuditLog auditLog) {
         // 1) pkExpression が指定されていればそれを最優先 (複合 PK 用)
         String expression = auditLog.pkExpression();
         if (expression != null && !expression.isBlank()) {
             try {
                 Object value = evaluatePkExpression(pjp, expression);
                 JsonNode json = serialize(value);
                 if (json != null) return json;
             } catch (Exception ex) {
                 log.warn("[finance-audit] pkExpression 評価に失敗: expr='{}', method={}#{}, err={}",
                         expression,
                         pjp.getSignature().getDeclaringTypeName(),
                         pjp.getSignature().getName(),
                         ex.toString());
             }
             return auditMapper.createObjectNode().put("_pkExpressionError", expression);
         }
 
         // 2) pkArgIndex が >= 0 なら従来挙動 (単一引数 PK)
         int idx = auditLog.pkArgIndex();
         if (idx >= 0) {
             Object[] args = pjp.getArgs();
             Object pkArg = (args != null && args.length > idx) ? args[idx] : null;
             JsonNode json = serialize(pkArg);
             if (json != null) return json;
         }
 
         // 3) フォールバック: 空 PK (不明操作 / INSERT 想定)
         return auditMapper.createObjectNode();
     }
 
@@ -196,78 +206,87 @@ public class FinanceAuditAspect {
         try {
             return loaderOpt.get().loadByPk(pkJson)
                     .map(this::serialize)
                     .orElse(null);
         } catch (Exception ex) {
             log.warn("[finance-audit] {} snapshot 取得に失敗: loader={}, err={}",
                     label, loaderOpt.get().getClass().getSimpleName(), ex.toString());
             return null;
         }
     }
 
     /**
      * captureArgsAsAfter=true の時に reason 末尾へ補助 args JSON を残す。
      * before/after は実 Entity を入れたいので、この補助情報は reason 列にだけ書く。
      */
     private String buildArgsAux(ProceedingJoinPoint pjp, AuditLog auditLog) {
         if (!auditLog.captureArgsAsAfter()) return null;
         Object[] args = pjp.getArgs();
         if (args == null || args.length == 0) return null;
         JsonNode json = serialize(args);
         if (json == null) return null;
         return "args=" + truncate(json.toString(), 1500);
     }
 
     private String composeReason(String head, String argsAux) {
         if (head == null && argsAux == null) return null;
         if (head == null) return argsAux;
         if (argsAux == null) return head;
         return head + " | " + argsAux;
     }
 
     private AuthInfo resolveAuth() {
         Authentication auth = SecurityContextHolder.getContext().getAuthentication();
         if (auth != null && auth.isAuthenticated()
                 && auth.getPrincipal() instanceof LoginUser loginUser) {
             return new AuthInfo(loginUser.getUser().getLoginUserNo(), "USER");
         }
         return new AuthInfo(null, "SYSTEM");
     }
 
     private ReqInfo resolveRequest() {
         try {
             HttpServletRequest req = httpRequestProvider.getIfAvailable();
             if (req == null) return new ReqInfo(null, null);
             return new ReqInfo(clientIp(req), truncate(req.getHeader("User-Agent"), 500));
         } catch (Exception ignored) {
             return new ReqInfo(null, null);
         }
     }
 
+    /**
+     * G3-M12-fix: captureArgsAsAfter フォールバックで使う、引数列の JSON 化。
+     * 0 件 / null は null を返し、`afterJson` を null のままにすることで誤誘導を避ける。
+     */
+    private JsonNode serializeArgs(Object[] args) {
+        if (args == null || args.length == 0) return null;
+        return serialize(args);
+    }
+
     private JsonNode serialize(Object obj) {
         if (obj == null) return null;
         try {
             return auditMapper.valueToTree(obj);
         } catch (Exception e) {
             log.warn("[finance-audit] JSON シリアライズに失敗: type={}, err={}",
                     obj.getClass().getName(), e.toString());
             return auditMapper.createObjectNode().put("_serializeError", e.toString());
         }
     }
 
     private String clientIp(HttpServletRequest req) {
         String forwarded = req.getHeader("X-Forwarded-For");
         if (forwarded != null && !forwarded.isBlank()) {
             int comma = forwarded.indexOf(',');
             return truncate(comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim(), 45);
         }
         return truncate(req.getRemoteAddr(), 45);
     }
 
     private String truncate(String s, int max) {
         if (s == null) return null;
         return s.length() <= max ? s : s.substring(0, max);
     }
 
     private record AuthInfo(Integer userNo, String actorType) {}
     private record ReqInfo(String ip, String userAgent) {}
 }
diff --git a/backend/src/main/java/jp/co/oda32/audit/FinanceAuditWriter.java b/backend/src/main/java/jp/co/oda32/audit/FinanceAuditWriter.java
index 08a31a5..1010bac 100644
--- a/backend/src/main/java/jp/co/oda32/audit/FinanceAuditWriter.java
+++ b/backend/src/main/java/jp/co/oda32/audit/FinanceAuditWriter.java
@@ -1,48 +1,64 @@
 package jp.co.oda32.audit;
 
 import com.fasterxml.jackson.databind.JsonNode;
 import jp.co.oda32.domain.model.audit.FinanceAuditLog;
 import jp.co.oda32.domain.repository.audit.FinanceAuditLogRepository;
 import lombok.RequiredArgsConstructor;
 import org.springframework.stereotype.Component;
 import org.springframework.transaction.annotation.Propagation;
 import org.springframework.transaction.annotation.Transactional;
 
 import java.time.LocalDateTime;
 
 /**
  * T2: 監査ログを別 tx ({@code REQUIRES_NEW}) で書き込む writer。
  * <p>
  * Aspect 自身に {@code @Transactional} を付けても同クラス内 self-call では
  * proxy を通らず {@code REQUIRES_NEW} が効かないため、別 Bean に切り出す。
  * <p>
  * 別 tx にしておくことで、業務 tx が rollback しても "失敗操作" の監査ログだけは残せる。
  *
  * @since 2026-05-04 (T2)
  */
 @Component
 @RequiredArgsConstructor
 public class FinanceAuditWriter {
 
     private final FinanceAuditLogRepository repository;
 
     @Transactional(propagation = Propagation.REQUIRES_NEW)
     public void write(String table, String operation, Integer userNo, String actorType,
                       JsonNode targetPk, JsonNode beforeValues, JsonNode afterValues,
                       String reason, String sourceIp, String userAgent) {
+        write(table, operation, userNo, actorType, targetPk, beforeValues, afterValues,
+                reason, sourceIp, userAgent, null);
+    }
+
+    /**
+     * G2-M2 fix (Codex Major #3, V043, 2026-05-06): force=true 時の per-supplier mismatch
+     * 全件 JSON を {@code force_mismatch_details} 列に保存する版。
+     * <p>{@code reason} 列の 50 件切り詰め (容量節約) と乖離させないため、監査追跡用に
+     * 全件を構造化保存する。{@code forceMismatchDetails} が null の場合は従来挙動と等価。
+     */
+    @Transactional(propagation = Propagation.REQUIRES_NEW)
+    public void write(String table, String operation, Integer userNo, String actorType,
+                      JsonNode targetPk, JsonNode beforeValues, JsonNode afterValues,
+                      String reason, String sourceIp, String userAgent,
+                      JsonNode forceMismatchDetails) {
         FinanceAuditLog entry = FinanceAuditLog.builder()
                 .occurredAt(LocalDateTime.now())
                 .actorUserNo(userNo)
                 .actorType(actorType)
                 .operation(operation)
                 .targetTable(table)
                 .targetPk(targetPk)
                 .beforeValues(beforeValues)
                 .afterValues(afterValues)
                 .reason(reason)
                 .sourceIp(sourceIp)
                 .userAgent(userAgent)
+                .forceMismatchDetails(forceMismatchDetails)
                 .build();
         repository.save(entry);
     }
 }
diff --git a/backend/src/main/java/jp/co/oda32/domain/model/audit/FinanceAuditLog.java b/backend/src/main/java/jp/co/oda32/domain/model/audit/FinanceAuditLog.java
index 950b5a1..83ec0e4 100644
--- a/backend/src/main/java/jp/co/oda32/domain/model/audit/FinanceAuditLog.java
+++ b/backend/src/main/java/jp/co/oda32/domain/model/audit/FinanceAuditLog.java
@@ -27,51 +27,62 @@ import java.time.LocalDateTime;
 @Entity
 @Table(name = "finance_audit_log")
 @Data
 @NoArgsConstructor
 @AllArgsConstructor
 @Builder
 public class FinanceAuditLog {
 
     @Id
     @GeneratedValue(strategy = GenerationType.IDENTITY)
     @Column(name = "id")
     private Long id;
 
     @Column(name = "occurred_at", nullable = false)
     private LocalDateTime occurredAt;
 
     /** 操作実行者 (m_login_user.login_user_no)。NULL = SYSTEM/BATCH。 */
     @Column(name = "actor_user_no")
     private Integer actorUserNo;
 
     /** USER / SYSTEM / BATCH。 */
     @Column(name = "actor_type", nullable = false, length = 20)
     private String actorType;
 
     @Column(name = "operation", nullable = false, length = 50)
     private String operation;
 
     @Column(name = "target_table", nullable = false, length = 100)
     private String targetTable;
 
     @Type(JsonBinaryType.class)
     @Column(name = "target_pk", nullable = false, columnDefinition = "jsonb")
     private JsonNode targetPk;
 
     @Type(JsonBinaryType.class)
     @Column(name = "before_values", columnDefinition = "jsonb")
     private JsonNode beforeValues;
 
     @Type(JsonBinaryType.class)
     @Column(name = "after_values", columnDefinition = "jsonb")
     private JsonNode afterValues;
 
     @Column(name = "reason", columnDefinition = "TEXT")
     private String reason;
 
     @Column(name = "source_ip", length = 45)
     private String sourceIp;
 
     @Column(name = "user_agent", length = 500)
     private String userAgent;
+
+    /**
+     * G2-M2 fix (Codex Major #3, V043, 2026-05-06): force=true 時の per-supplier mismatch 全件 JSON。
+     * <p>{@code reason} 列の 50 件切り詰め (容量節約) との乖離防止のため、
+     * 監査追跡用に全件を構造化保存する。force=false の通常 audit 行や、
+     * force=true でも mismatch 0 件の場合は NULL のまま。
+     * <p>形式: {@code [{"line": "[5日払い] supplier=10001 ..."}, ...]} 等の文字列 entries 配列。
+     */
+    @Type(JsonBinaryType.class)
+    @Column(name = "force_mismatch_details", columnDefinition = "jsonb")
+    private JsonNode forceMismatchDetails;
 }
diff --git a/backend/src/main/resources/db/migration/V043__add_force_mismatch_details.sql b/backend/src/main/resources/db/migration/V043__add_force_mismatch_details.sql
new file mode 100644
index 0000000..f96f4ea
--- /dev/null
+++ b/backend/src/main/resources/db/migration/V043__add_force_mismatch_details.sql
@@ -0,0 +1,30 @@
+-- G2-M2 fix (Codex Major #3, 2026-05-06): force=true 時の per-supplier mismatch を全件 JSONB で保存する。
+--
+-- 背景:
+--   PaymentMfImportService.writeForceAppliedAuditRow が finance_audit_log.reason 列に
+--   FORCE_APPLIED: per-supplier mismatches count=N, details=[...50件...] を組み立てて記録するが、
+--   先頭 50 件で切り詰めていたため、UI の 100 件省略表示と合わせて
+--   「承認 (force=true) 内容」「実際の処理対象 (= 全件)」「監査証跡 (= 50 件)」の 3 者が乖離していた。
+--   税務監査・内部監査で「結局どの supplier で何円ずれたのか」を後追いで再現できなくなる。
+--
+-- 対応:
+--   reason 列は件数表示用 (varchar 容量節約) のままにし、
+--   force_mismatch_details JSONB 列を追加して全件を構造化保存する。
+--   GIN index で「特定 supplier_no が含まれる force apply ログ」の検索に備える。
+--
+-- 関連:
+--   PaymentMfImportService.writeForceAppliedAuditRow / buildForceAppliedReason
+--   設計書: claudedocs/design-payment-mf-import.md §5.6 (G2-M2)
+--   runbook: claudedocs/runbook-payment-mf-force-apply.md (運用手順、本対応で詳細列を確認できる旨追記)
+
+ALTER TABLE finance_audit_log
+    ADD COLUMN IF NOT EXISTS force_mismatch_details JSONB;
+
+CREATE INDEX IF NOT EXISTS idx_finance_audit_log_force_details
+    ON finance_audit_log USING GIN (force_mismatch_details);
+
+COMMENT ON COLUMN finance_audit_log.force_mismatch_details IS
+    'G2-M2 fix (Codex Major #3): force=true 時の per-supplier mismatch 全件 JSON 配列。'
+    'reason 列の 50 件切り詰め (容量配慮) との乖離を防ぐため、監査追跡用に全件構造化保存する。'
+    '形式: [{"line": "[5日払い] supplier=10001 ..."}, ...] 等の string entries 配列。'
+    'force=false の通常 audit 行や、force=true でも mismatch 0 件の行では NULL のまま。';
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git show --no-ext-diff --unified=80 60d7d81 -- backend/src/main/java/db/migration/V033__reencrypt_mf_oauth_secrets.java' in C:\project\odamitsu-data-hub
 succeeded in 1530ms:
commit 60d7d815f07409beb1375fc08c96bd6dcfc8d13a
Author: kazuki <k_oda@odamitsu.com>
Date:   Thu May 7 09:18:25 2026 +0900

    fix(codex-r2): Codex 第 2 弾批判レビュー対応 (Critical + P1 + 9 Major + 1 Minor)
    
    3 commit (audit/mf/finance) を Codex 批判レビューにかけ、12 件の指摘を修正。
    
    ## Critical / P1 (即修正)
    * Critical: BULK 不変条件違反時の SUM フォールバックを fail-closed (例外) 化
      - PaymentMfImportService.sumVerifiedAmountForGroup で全行 BULK 不一致時に
        FinanceInternalException 422、過大計上の MF CSV 流出を遮断
    * P1: V033 再暗号化が片側カラムのみで refresh_token が旧鍵残るバグ修正
      - reencryptMfTokens() で access + refresh を 1 UPDATE で同時再暗号化
      - decrypt 失敗時 skip + WARN ログで idempotent 強化 (recipherOrSkip)
    
    ## Major (9 件)
    * MF P2: V037 ordering note は runbook §6' に集約 (V037 は revert で checksum 復元)
    * Audit P2: FinanceAuditAspect で Loader 解決失敗時の captureReturn/Args フォールバック追加
    * G2-M2 Major: verification_source 3 経路 (BULK/MANUAL/MF_OVERRIDE) を共通 advisory lock で直列化
      - FinancePayableLock util 切り出し、computePayableLockKey + acquire
      - applyVerification / verify / applyMfOverride で同一 lock キー
    * G2-M3 Major: force audit 全件保存 (V043 finance_audit_log.force_mismatch_details JSONB)
      - 50 件切り詰めの reason と全件 JSON を分離、UI 全件確認可能化への土台
    * G2-M4 Major: forceReason 必須化 + reason 空 → FORCE_REASON_REQUIRED 400
    * G2-M5 Major: 20日払い専用 Excel が PAYMENT_5TH 誤分類されるバグ修正
      - PaymentMfExcelParser.determineInitialSection で送金日 day による初期 section 判定
    * G2-M5 Major: OFFSET マスタ欠落時の hardcoded default fallback + 最後の active 行削除禁止
    * G2-M6 Major: P1-02 opening 注入空時の警告 (SupplierBalances/IntegrityReport DTO)
    * Frontend Major: force apply UX 強化 (CSV download, reviewedAll checkbox, forceReason 必須)
    
    ## Minor
    * G2-M? Minor: V044 で V040 backfill 検査 (NOTICE のみ、自動修復なし)
      - 実行結果: 560 行で history 突合なし = false positive (P1-08 導入前の歴史データ)
    
    ## 新規 migration
    - V043 finance_audit_log.force_mismatch_details JSONB + GIN index
    - V044 V040 backfill 検査 (RAISE NOTICE のみ)
    
    ## 新規 runbook
    - runbook-payment-mf-bulk-invariant-violation.md (Critical fix の運用手順)
    - runbook-payment-mf-force-apply.md (force=true の二段認可運用)
    
    ## CLAUDE.md
    - Review Guidelines セクション追加 (Repository Context / Review Priorities /
      Architecture Rules) — AI レビュー時の優先順位を明示
    
    ## テスト
    - 既存 233 + 新規 ~10 件 PASS、frontend tsc PASS、リグレッション 0
    - 5 group 並列修正で衝突なし
    
    Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>

diff --git a/backend/src/main/java/db/migration/V033__reencrypt_mf_oauth_secrets.java b/backend/src/main/java/db/migration/V033__reencrypt_mf_oauth_secrets.java
index 0873b8e..95ecd31 100644
--- a/backend/src/main/java/db/migration/V033__reencrypt_mf_oauth_secrets.java
+++ b/backend/src/main/java/db/migration/V033__reencrypt_mf_oauth_secrets.java
@@ -1,172 +1,296 @@
 package db.migration;
 
 import org.flywaydb.core.api.migration.BaseJavaMigration;
 import org.flywaydb.core.api.migration.Context;
 import org.springframework.security.crypto.encrypt.Encryptors;
 import org.springframework.security.crypto.encrypt.TextEncryptor;
 
 import java.sql.Connection;
 import java.sql.DatabaseMetaData;
 import java.sql.PreparedStatement;
 import java.sql.ResultSet;
 import java.sql.Statement;
 
 /**
  * P1-05 案 C.3: MF OAuth 関連 _enc カラムを <b>OAuth 専用鍵</b> で再暗号化する Java migration。
  * <p>
  * 旧鍵 ({@code APP_CRYPTO_KEY} / {@code APP_CRYPTO_SALT}) で暗号化されている下記 3 カラムを
  * 新鍵 ({@code APP_CRYPTO_OAUTH_KEY} / {@code APP_CRYPTO_OAUTH_SALT}) で再暗号化する。
  * <ul>
  *   <li>{@code m_mf_oauth_client.client_secret_enc}</li>
  *   <li>{@code t_mf_oauth_token.access_token_enc}</li>
  *   <li>{@code t_mf_oauth_token.refresh_token_enc}</li>
  * </ul>
  *
- * <p>動作 (idempotent 化済 / C1 fix):
+ * <p>動作 (idempotent 化済 / C1 fix + Codex P1 fix):
  * <ol>
  *   <li>環境変数から旧鍵 (未設定なら application-dev.yml の dev fallback) と新鍵を取得</li>
  *   <li>新鍵 (oauth-key/salt) が未設定なら fail-fast (本 migration をスキップさせない)</li>
  *   <li>{@code oauth_encryption_version} 列が存在すれば、{@code version=1} 行のみ対象に絞る
  *       (V037 適用後の挙動)。version 列が無ければ全行対象 (V037 適用前の旧環境)</li>
  *   <li>1 行 SELECT → 旧鍵 decrypt → 新鍵 encrypt → version=2 と共に UPDATE → 即 commit</li>
  *   <li>途中で失敗しても、それまで update 済みの行は version=2 で永続化されているため
  *       次回再実行時に skip される (idempotent)</li>
  * </ol>
  *
+ * <p><b>Codex P1 fix (2026-05-06)</b>: {@code t_mf_oauth_token} は access_token_enc と
+ * refresh_token_enc の 2 カラムを持つため、旧実装は同テーブルを 2 回 reencrypt 呼び出ししていた。
+ * しかし 1 回目の呼び出しが {@code version=1 → 2} にマークしてしまうため、2 回目の呼び出し
+ * (= refresh_token_enc 用) は WHERE {@code version=1} で 0 件になり、refresh_token_enc が
+ * 旧鍵のまま残る致命バグがあった。修正: {@link #reencryptMfTokens} で両カラムを 1 つの UPDATE で
+ * アトミックに再暗号化する。
+ *
+ * <p><b>Codex P1 fix #2 (2026-05-06)</b>: 旧実装は decrypt 失敗で即 fail-fast していたが、
+ * version 列なし環境で部分 commit 後に再実行されると「既に新鍵化済の行」を旧鍵で decrypt 試行 →
+ * 失敗 → migration 全体停止になる。修正: decrypt 失敗時は WARN ログ出して skip し、
+ * (新鍵で暗号化済 = 既に再暗号化完了済) と仮定して次行へ進む。
+ *
  * <p>autoCommit OFF + 全件まとめて commit していた旧実装は、Flyway 履歴記録前にプロセス停止すると
  * 「暗号文は新鍵化済み・migration 履歴は未適用」のミスマッチ状態になり復旧困難だった。
  * 本実装は行単位 commit + version マーカーで部分再開を可能にする。
  *
  * <p>セットアップ手順 / トラブルシューティングは {@code claudedocs/runbook-mf-oauth-keys.md} を参照。
  *
  * @since 2026/05/04
  */
 public class V033__reencrypt_mf_oauth_secrets extends BaseJavaMigration {
 
     /**
      * application-dev.yml に書かれている dev fallback と完全一致させる。
      * 環境変数 APP_CRYPTO_KEY/SALT が未設定の dev 環境でも、Spring 起動時に CryptoUtil が
      * これらの値を使って暗号化済みなので、本 migration もこの値で旧データを復号する。
      */
     private static final String DEV_FALLBACK_KEY = "dev-odamitsu-data-hub-crypto-key-2026";
     private static final String DEV_FALLBACK_SALT = "3f6a2d8c9e1b4a7d5c0f8e2a9b6d1c3f";
 
     private static final String VERSION_COLUMN = "oauth_encryption_version";
 
     @Override
     public void migrate(Context context) throws Exception {
         String oldKey = envOrDefault("APP_CRYPTO_KEY", DEV_FALLBACK_KEY);
         String oldSalt = envOrDefault("APP_CRYPTO_SALT", DEV_FALLBACK_SALT);
         String newKey = System.getenv("APP_CRYPTO_OAUTH_KEY");
         String newSalt = System.getenv("APP_CRYPTO_OAUTH_SALT");
 
         if (newKey == null || newKey.isBlank() || newSalt == null || newSalt.isBlank()) {
             throw new IllegalStateException(
                     "[V033] APP_CRYPTO_OAUTH_KEY と APP_CRYPTO_OAUTH_SALT は必須です。"
                             + " backend/scripts/gen-oauth-key.ps1 で鍵を生成し、env var を設定してください。"
                             + " 詳細は claudedocs/runbook-mf-oauth-keys.md (Step 1-2) を参照。");
         }
 
         TextEncryptor oldCipher = Encryptors.delux(oldKey, oldSalt);
         TextEncryptor newCipher = Encryptors.delux(newKey, newSalt);
 
         Connection conn = context.getConnection();
         // 行単位 commit (idempotent 再開) を成立させるため autoCommit を ON 化。
         // Flyway は本 migration 完了後に独自トランザクションで履歴を記録する。
         boolean originalAutoCommit = conn.getAutoCommit();
         conn.setAutoCommit(true);
         try {
             boolean clientHasVersion = columnExists(conn, "m_mf_oauth_client", VERSION_COLUMN);
             boolean tokenHasVersion = columnExists(conn, "t_mf_oauth_token", VERSION_COLUMN);
 
             int clientCount = reencrypt(conn, "m_mf_oauth_client", "id",
                     "client_secret_enc", oldCipher, newCipher, clientHasVersion);
-            int accessCount = reencrypt(conn, "t_mf_oauth_token", "id",
-                    "access_token_enc", oldCipher, newCipher, tokenHasVersion);
-            int refreshCount = reencrypt(conn, "t_mf_oauth_token", "id",
-                    "refresh_token_enc", oldCipher, newCipher, tokenHasVersion);
+            // Codex P1 fix: t_mf_oauth_token は access + refresh を 1 UPDATE で同時更新する。
+            // 旧実装は reencrypt() を 2 回呼んでいたが、1 回目で version=2 にマーク → 2 回目で
+            // WHERE version=1 がヒット 0 件になり refresh_token_enc が旧鍵のまま残るバグがあった。
+            int tokenCount = reencryptMfTokens(conn, oldCipher, newCipher, tokenHasVersion);
             System.out.printf(
-                    "[V033] OAuth secrets re-encrypted: client_secret=%d, access_token=%d, refresh_token=%d"
+                    "[V033] OAuth secrets re-encrypted: client_secret=%d, t_mf_oauth_token rows=%d"
                             + " (clientHasVersion=%b, tokenHasVersion=%b)%n",
-                    clientCount, accessCount, refreshCount, clientHasVersion, tokenHasVersion);
+                    clientCount, tokenCount, clientHasVersion, tokenHasVersion);
         } catch (Exception e) {
             // 行単位 commit 済みのため rollback では戻らない。
             // 例外をそのまま伝播 → Flyway が migration 失敗として記録 → 次回再起動時に
             // version=2 マーク済み行は自動 skip され、未処理行から再開できる。
             throw new IllegalStateException(
                     "[V033] 再暗号化に失敗しました。version=2 マーク済み行は新鍵化完了。"
                             + " 残り行は次回再実行で自動再開されます。"
                             + " 旧鍵 (APP_CRYPTO_KEY/SALT) が変わっている可能性があるので env を要確認。"
                             + " 詳細は claudedocs/runbook-mf-oauth-keys.md の鍵紛失復旧手順を参照。"
                             + " 元例外: " + e.getMessage(), e);
         } finally {
             conn.setAutoCommit(originalAutoCommit);
         }
     }
 
     /**
-     * 指定テーブルの暗号化カラムを「旧鍵で decrypt → 新鍵で encrypt」して UPDATE する。
+     * 単一カラム再暗号化ヘルパー (m_mf_oauth_client.client_secret_enc 用)。
      * <p>
      * {@code hasVersionColumn=true} のときは {@code oauth_encryption_version=1} の行のみ処理し、
      * UPDATE で同時に {@code version=2} へ進める。version 列が無い旧環境では NULL 行以外を全件処理する
      * (V037 適用前のため version 概念が未導入)。
      * <p>
      * autoCommit ON 前提: 1 行 UPDATE = 即 commit。途中で例外発生してもそれまでの行は確定済み。
+     * <p>
+     * <b>decrypt 失敗時 (Codex P1 fix #2)</b>: WARN ログ出して skip。version 列なし環境で部分 commit
+     * 後に再実行された場合、新鍵で暗号化済の行は旧鍵 decrypt できず例外になるため、それを「既に
+     * 再暗号化完了済」と判定して次行へ進む。
      *
-     * @return 再暗号化した行数
+     * @return 再暗号化した行数 (skip した行は含まない)
      */
-    private static int reencrypt(Connection conn, String table, String pkColumn, String column,
-                                 TextEncryptor oldCipher, TextEncryptor newCipher,
-                                 boolean hasVersionColumn) throws Exception {
+    static int reencrypt(Connection conn, String table, String pkColumn, String column,
+                         TextEncryptor oldCipher, TextEncryptor newCipher,
+                         boolean hasVersionColumn) throws Exception {
         String whereVersion = hasVersionColumn
                 ? " AND " + VERSION_COLUMN + " = 1"
                 : "";
         String selectSql = "SELECT " + pkColumn + ", " + column + " FROM " + table
                 + " WHERE " + column + " IS NOT NULL" + whereVersion;
         String updateSql = hasVersionColumn
                 ? "UPDATE " + table + " SET " + column + " = ?, " + VERSION_COLUMN + " = 2"
                         + " WHERE " + pkColumn + " = ? AND " + VERSION_COLUMN + " = 1"
                 : "UPDATE " + table + " SET " + column + " = ? WHERE " + pkColumn + " = ?";
         int count = 0;
+        int skipped = 0;
         try (Statement select = conn.createStatement();
              ResultSet rs = select.executeQuery(selectSql);
              PreparedStatement update = conn.prepareStatement(updateSql)) {
             while (rs.next()) {
                 int pk = rs.getInt(1);
                 String oldEnc = rs.getString(2);
                 String plain;
                 try {
                     plain = oldCipher.decrypt(oldEnc);
                 } catch (Exception e) {
-                    throw new IllegalStateException(
-                            "[V033] " + table + "." + column + " (" + pkColumn + "=" + pk + ") の復号に失敗。"
-                                    + " 旧鍵が一致していません: " + e.getMessage(), e);
+                    // Codex P1 fix #2: 旧鍵で decrypt 失敗 = 既に新鍵化済の可能性 (version 列なし環境の
+                    // 部分 commit 再実行時等)。fail-fast せずに skip + WARN ログ。
+                    System.err.printf(
+                            "[V033] WARN: %s.%s (%s=%d) は旧鍵で復号できず skip (新鍵化済の可能性): %s%n",
+                            table, column, pkColumn, pk, e.getMessage());
+                    skipped++;
+                    continue;
                 }
                 String newEnc = newCipher.encrypt(plain);
                 update.setString(1, newEnc);
                 update.setInt(2, pk);
                 // 行単位 commit (autoCommit=true) → executeBatch ではなく即時 executeUpdate
                 update.executeUpdate();
                 count++;
             }
         }
+        if (skipped > 0) {
+            System.err.printf("[V033] %s.%s: %d 行を decrypt 失敗で skip しました (新鍵化済と仮定)%n",
+                    table, column, skipped);
+        }
         return count;
     }
 
+    /**
+     * t_mf_oauth_token 専用ヘルパー: access_token_enc + refresh_token_enc を 1 つの UPDATE で
+     * 同時に再暗号化する。
+     * <p>
+     * <b>Codex P1 fix の根幹</b>: 旧実装は {@link #reencrypt} を access_token / refresh_token に対して
+     * 順番に 2 回呼んでいたが、1 回目で {@code version=2} にマーク → 2 回目では {@code WHERE version=1}
+     * がヒット 0 件になり refresh_token_enc が旧鍵のまま残ってしまっていた。
+     * 本メソッドは 1 SELECT で両カラムを取得 → 1 UPDATE で両カラムをアトミックに更新する。
+     * <p>
+     * decrypt 失敗時の skip ロジックは {@link #reencrypt} と同じ (両カラム独立に try/catch、
+     * 両方 skip した行のみ "完全 skip" 扱い)。
+     *
+     * @return 1 行でも実際に UPDATE が走った行数
+     */
+    static int reencryptMfTokens(Connection conn, TextEncryptor oldCipher, TextEncryptor newCipher,
+                                 boolean hasVersionColumn) throws Exception {
+        String whereVersion = hasVersionColumn
+                ? " WHERE " + VERSION_COLUMN + " = 1"
+                : "";
+        // access_token_enc / refresh_token_enc とも NULL 許容 (= MF 連携未設定の row が存在し得る) なので
+        // どちらか NOT NULL の行を全部拾う。両方 NULL の行は処理対象外。
+        String selectSql = "SELECT id, access_token_enc, refresh_token_enc FROM t_mf_oauth_token"
+                + whereVersion;
+        String updateSql = hasVersionColumn
+                ? "UPDATE t_mf_oauth_token SET access_token_enc = ?, refresh_token_enc = ?,"
+                        + " " + VERSION_COLUMN + " = 2"
+                        + " WHERE id = ? AND " + VERSION_COLUMN + " = 1"
+                : "UPDATE t_mf_oauth_token SET access_token_enc = ?, refresh_token_enc = ? WHERE id = ?";
+        int count = 0;
+        int skipped = 0;
+        try (Statement select = conn.createStatement();
+             ResultSet rs = select.executeQuery(selectSql);
+             PreparedStatement update = conn.prepareStatement(updateSql)) {
+            while (rs.next()) {
+                int id = rs.getInt(1);
+                String oldAccess = rs.getString(2);
+                String oldRefresh = rs.getString(3);
+
+                // 両カラム独立に decrypt 試行 (片方だけ NULL の row もあり得る前提)
+                String newAccess = recipherOrSkip(oldAccess, oldCipher, newCipher,
+                        "t_mf_oauth_token.access_token_enc", id);
+                String newRefresh = recipherOrSkip(oldRefresh, oldCipher, newCipher,
+                        "t_mf_oauth_token.refresh_token_enc", id);
+
+                // 両カラムとも NULL or skip → row 単位で skip (DB は更新しない)。
+                // version=2 にマークしないので次回再実行で再評価される。
+                if (newAccess == null && newRefresh == null
+                        && oldAccess == null && oldRefresh == null) {
+                    // 両方とも元から NULL → version マーカーは進めるが値は変えない
+                    if (hasVersionColumn) {
+                        update.setNull(1, java.sql.Types.VARCHAR);
+                        update.setNull(2, java.sql.Types.VARCHAR);
+                        update.setInt(3, id);
+                        update.executeUpdate();
+                        count++;
+                    }
+                    continue;
+                }
+
+                // skip した側は既存値を「そのまま温存」: 元 NULL なら NULL のまま、元 enc なら元 enc のまま。
+                // recipherOrSkip は decrypt 失敗時に oldEnc をそのまま返すので、上書きしても値は変わらない。
+                update.setString(1, newAccess);
+                update.setString(2, newRefresh);
+                update.setInt(3, id);
+                update.executeUpdate();
+                count++;
+            }
+        }
+        if (skipped > 0) {
+            System.err.printf("[V033] t_mf_oauth_token: %d カラムを skip しました (新鍵化済と仮定)%n",
+                    skipped);
+        }
+        return count;
+    }
+
+    /**
+     * 値が null なら null、decrypt 成功なら新鍵 encrypt、decrypt 失敗なら元値をそのまま返す
+     * (= 既に新鍵化済の可能性が高いので壊さず温存)。
+     */
+    private static String recipherOrSkip(String oldEnc, TextEncryptor oldCipher,
+                                         TextEncryptor newCipher, String fieldLabel, int pk) {
+        if (oldEnc == null) {
+            return null;
+        }
+        try {
+            String plain = oldCipher.decrypt(oldEnc);
+            return newCipher.encrypt(plain);
+        } catch (Exception e) {
+            System.err.printf(
+                    "[V033] WARN: %s (id=%d) は旧鍵で復号できず温存 (新鍵化済の可能性): %s%n",
+                    fieldLabel, pk, e.getMessage());
+            return oldEnc;
+        }
+    }
+
     /** 指定テーブルに指定カラムが存在するか判定 (大文字小文字無視)。 */
-    private static boolean columnExists(Connection conn, String table, String column) throws Exception {
+    static boolean columnExists(Connection conn, String table, String column) throws Exception {
         DatabaseMetaData md = conn.getMetaData();
         try (ResultSet rs = md.getColumns(null, null, table, column)) {
             if (rs.next()) return true;
         }
         // PostgreSQL identifier は通常 lower-case 格納。明示的に lower でも試す。
         try (ResultSet rs = md.getColumns(null, null, table.toLowerCase(), column.toLowerCase())) {
+            if (rs.next()) return true;
+        }
+        // H2 (test 用) は識別子を upper-case で格納する。テスト用に upper も試行。
+        try (ResultSet rs = md.getColumns(null, null, table.toUpperCase(), column.toUpperCase())) {
             return rs.next();
         }
     }
 
     private static String envOrDefault(String name, String defaultValue) {
         String v = System.getenv(name);
         return (v == null || v.isBlank()) ? defaultValue : v;
     }
 }
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git show --no-ext-diff --unified=60 60d7d81 -- backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParser.java' in C:\project\odamitsu-data-hub
 succeeded in 1252ms:
commit 60d7d815f07409beb1375fc08c96bd6dcfc8d13a
Author: kazuki <k_oda@odamitsu.com>
Date:   Thu May 7 09:18:25 2026 +0900

    fix(codex-r2): Codex 第 2 弾批判レビュー対応 (Critical + P1 + 9 Major + 1 Minor)
    
    3 commit (audit/mf/finance) を Codex 批判レビューにかけ、12 件の指摘を修正。
    
    ## Critical / P1 (即修正)
    * Critical: BULK 不変条件違反時の SUM フォールバックを fail-closed (例外) 化
      - PaymentMfImportService.sumVerifiedAmountForGroup で全行 BULK 不一致時に
        FinanceInternalException 422、過大計上の MF CSV 流出を遮断
    * P1: V033 再暗号化が片側カラムのみで refresh_token が旧鍵残るバグ修正
      - reencryptMfTokens() で access + refresh を 1 UPDATE で同時再暗号化
      - decrypt 失敗時 skip + WARN ログで idempotent 強化 (recipherOrSkip)
    
    ## Major (9 件)
    * MF P2: V037 ordering note は runbook §6' に集約 (V037 は revert で checksum 復元)
    * Audit P2: FinanceAuditAspect で Loader 解決失敗時の captureReturn/Args フォールバック追加
    * G2-M2 Major: verification_source 3 経路 (BULK/MANUAL/MF_OVERRIDE) を共通 advisory lock で直列化
      - FinancePayableLock util 切り出し、computePayableLockKey + acquire
      - applyVerification / verify / applyMfOverride で同一 lock キー
    * G2-M3 Major: force audit 全件保存 (V043 finance_audit_log.force_mismatch_details JSONB)
      - 50 件切り詰めの reason と全件 JSON を分離、UI 全件確認可能化への土台
    * G2-M4 Major: forceReason 必須化 + reason 空 → FORCE_REASON_REQUIRED 400
    * G2-M5 Major: 20日払い専用 Excel が PAYMENT_5TH 誤分類されるバグ修正
      - PaymentMfExcelParser.determineInitialSection で送金日 day による初期 section 判定
    * G2-M5 Major: OFFSET マスタ欠落時の hardcoded default fallback + 最後の active 行削除禁止
    * G2-M6 Major: P1-02 opening 注入空時の警告 (SupplierBalances/IntegrityReport DTO)
    * Frontend Major: force apply UX 強化 (CSV download, reviewedAll checkbox, forceReason 必須)
    
    ## Minor
    * G2-M? Minor: V044 で V040 backfill 検査 (NOTICE のみ、自動修復なし)
      - 実行結果: 560 行で history 突合なし = false positive (P1-08 導入前の歴史データ)
    
    ## 新規 migration
    - V043 finance_audit_log.force_mismatch_details JSONB + GIN index
    - V044 V040 backfill 検査 (RAISE NOTICE のみ)
    
    ## 新規 runbook
    - runbook-payment-mf-bulk-invariant-violation.md (Critical fix の運用手順)
    - runbook-payment-mf-force-apply.md (force=true の二段認可運用)
    
    ## CLAUDE.md
    - Review Guidelines セクション追加 (Repository Context / Review Priorities /
      Architecture Rules) — AI レビュー時の優先順位を明示
    
    ## テスト
    - 既存 233 + 新規 ~10 件 PASS、frontend tsc PASS、リグレッション 0
    - 5 group 並列修正で衝突なし
    
    Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>

diff --git a/backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParser.java b/backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParser.java
index c259d26..2a9e4bd 100644
--- a/backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParser.java
+++ b/backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParser.java
@@ -1,276 +1,316 @@
 package jp.co.oda32.domain.service.finance;
 
 import org.apache.poi.ss.usermodel.Row;
 import org.apache.poi.ss.usermodel.Sheet;
 import org.apache.poi.ss.usermodel.Workbook;
+import org.slf4j.Logger;
+import org.slf4j.LoggerFactory;
 
 import java.time.LocalDate;
 import java.util.ArrayList;
 import java.util.EnumMap;
 import java.util.LinkedHashMap;
 import java.util.List;
 import java.util.Map;
 import java.util.Set;
 
 /**
  * 振込明細 Excel（支払い明細 / 振込明細シート）から明細行と合計行サマリを抽出する。
  *
  * <p>Excel 構造:
  * <ul>
  *   <li>1 行目: ヘッダ情報。E1 付近に送金日（LocalDate）が入る。</li>
  *   <li>2 行目: カラム名行（送り先 / 請求額 / 振込金額 / 送料相手 / 早払い 等）。</li>
  *   <li>3 行目以降: 5日払い 明細 → 5日払い 合計 → 20日払い 明細 → 20日払い 合計。</li>
  * </ul>
  *
  * <p>ステートレスな純粋関数ユーティリティ。Bean 化せず、呼び出し側は
  * {@link #selectSheet(Workbook)} / {@link #parseSheet(Sheet)} を直接呼ぶ。
  * 数値コードの 6 桁正規化は {@link PaymentMfImportService#normalizePaymentSupplierCode}
  * を利用する（PAYABLE 用のみ、同パッケージ内で共有）。
  *
  * <p>G2-M3 (2026-05-06): 旧 {@code afterTotal} ブール値モデルを {@link PaymentMfSection}
  * 列挙型に置き換え、合計行ごとに section 別 summary をキャプチャする。
  * 旧実装は最初の合計行 (= 5日払い summary) を 1 度だけ捕まえてフラグを立てるだけだったため、
  * 20日払いセクションの合計行が捨てられて整合性チェック (chk1/chk3) が
  * 「5日払い summary vs 5日払い+20日払い 両方の明細合計」を比較する形にズレていた。
  */
 final class PaymentMfExcelParser {
 
+    private static final Logger log = LoggerFactory.getLogger(PaymentMfExcelParser.class);
+
     private PaymentMfExcelParser() {}
 
+    /**
+     * Codex Major #5 (2026-05-06): 送金日 day で初期 section を判定する境界 (exclusive)。
+     * <p>day &lt; CUTOFF → {@link PaymentMfSection#PAYMENT_5TH} 開始 (= 5日払い相当)。
+     * <p>day &ge; CUTOFF → {@link PaymentMfSection#PAYMENT_20TH} 開始 (= 20日払い相当)。
+     * <p>{@code FinanceConstants.PAYMENT_DATE_MIDMONTH_CUTOFF} と同値 (15)。
+     * 振替で 5日 が 4日/6日/7日, 20日 が 19日/21日 等に前後しても包括的に正しい section を選べる。
+     */
+    private static final int SECTION_INITIAL_CUTOFF_DAY = 15;
+
     /** 合計/メタ行のB列判定（正規化後の完全一致） */
     private static final Set<String> META_EXACT = Set.of(
             "合計", "小計", "その他計", "本社仕入 合計", "請求額", "打ち込み額", "打込額",
             "本社仕入合計"
     );
     /** 前方一致でメタ行判定 */
     private static final List<String> META_PREFIX = List.of(
             "20日払い振込手数料", "5日払い振込手数料", "送金日"
     );
 
     /**
      * Workbook から対象シートを選択する。優先順位: "支払い明細" &gt; "振込明細" &gt; 部分一致フォールバック。
      * 変換MAP・MF 用シート・福通シートは除外する。
      *
      * @return 対象シート。見つからない場合は null。
      */
     static Sheet selectSheet(Workbook workbook) {
         Sheet byExactPayment = null;
         Sheet byExactTransfer = null;
         for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
             String n = workbook.getSheetName(i);
             if (n == null) continue;
             if (n.contains("MF") || n.contains("変換MAP") || n.contains("福通")) continue;
             if ("支払い明細".equals(n)) byExactPayment = workbook.getSheetAt(i);
             else if ("振込明細".equals(n)) byExactTransfer = workbook.getSheetAt(i);
         }
         if (byExactPayment != null) return byExactPayment;
         if (byExactTransfer != null) return byExactTransfer;
         // フォールバック
         for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
             String n = workbook.getSheetName(i);
             if (n != null && (n.contains("支払") || n.contains("振込"))
                     && !n.contains("MF") && !n.contains("変換") && !n.contains("福通")) {
                 return workbook.getSheetAt(i);
             }
         }
         return null;
     }
 
     /**
      * シートを走査し、送金日・合計行サマリ・明細エントリを抽出する。
      *
      * <p>section 遷移: 走査開始時 {@link PaymentMfSection#PAYMENT_5TH}。
      * 合計行を検出するたびに、現在 section の summary を {@link ParsedExcel#summaries} へ格納し、
      * 次の section ({@code PAYMENT_5TH → PAYMENT_20TH}) に切替える。
      * {@code PAYMENT_20TH} 以降にさらに合計行があれば、{@code PAYMENT_20TH} の summary を最新値で上書きする
      * (現実的には 5日払い + 20日払いの 2 セクション以外は出ない)。
      */
     static ParsedExcel parseSheet(Sheet sheet) {
         ParsedExcel out = new ParsedExcel();
 
         // 送金日: 1行目の日付セルをスキャン（通常はE1）
         Row r1 = sheet.getRow(0);
         if (r1 != null) {
             for (int c = 0; c <= 10; c++) {
                 LocalDate d = PaymentMfCellReader.readDateCell(r1.getCell(c));
                 if (d != null) { out.transferDate = d; break; }
             }
         }
 
         // ヘッダ行（2行目）から列マップ構築
         Row header = sheet.getRow(1);
         if (header == null) throw new IllegalArgumentException("ヘッダ行（2行目）が見つかりません");
         Map<String, Integer> colMap = buildColumnMap(header);
         // 「仕入コード」ヘッダが無い振込明細（20日払いなど）では、送り先列の直前（通常は A列）に数値コードが入る。
         Integer colCode = colMap.getOrDefault("仕入コード", null);
         Integer colSource = colMap.get("送り先");
         if (colCode == null && colSource != null && colSource > 0) {
             colCode = colSource - 1;
         }
         Integer colAmount = colMap.get("請求額");
         Integer colFee = colMap.get("送料相手");
         Integer colEarly = colMap.get("早払い");
         // 合計行の「振込金額」列を読み取り、請求額総計 - 値引 - 早払 との整合性チェックに使う
         Integer colTransfer = colMap.get("振込金額");
         // P1-03 案 D: per-supplier 行で値引/相殺/打込金額を読取り、supplier 別 attribute を追跡する。
         // colTransfer (列 E) は合計行サマリーと per-supplier の双方で使う。
         Integer colDiscount = colMap.get("値引");
         Integer colOffset = colMap.get("相殺");
         Integer colPayin = colMap.get("打込金額"); // 整合性検証用 (CSV には出さない派生値)
         if (colSource == null || colAmount == null) {
             List<String> missing = new ArrayList<>();
             if (colSource == null) missing.add("送り先");
             if (colAmount == null) missing.add("請求額");
             throw new IllegalArgumentException(
                     "ヘッダ『" + String.join("』『", missing) + "』が特定できません。"
                     + "2行目で見つかった列: " + colMap.keySet());
         }
 
         int last = sheet.getLastRowNum();
-        // G2-M3: section enum で 5日払い / 20日払い を明示的に区別する。走査開始時は 5日払い。
-        PaymentMfSection currentSection = PaymentMfSection.PAYMENT_5TH;
+        // G2-M3: section enum で 5日払い / 20日払い を明示的に区別する。
+        // Codex Major #5 (2026-05-06): 送金日 day で初期 section を判定する。
+        //   - day < 15 → PAYMENT_5TH 開始 (5日払い)
+        //   - day >= 15 → PAYMENT_20TH 開始 (20日払い)
+        //   - 送金日不明 → PAYMENT_5TH (デフォルト) + WARN ログ
+        // 旧実装は常に PAYMENT_5TH 開始だったため、20日払い専用 Excel が来ると最初の合計行で
+        // PAYMENT_5TH summary をキャプチャしてしまい、entries も 全て PAYMENT_5TH として扱われ、
+        // 整合性チェック・PAYABLE/DIRECT_PURCHASE 振り分け・aux 保存が誤動作していた。
+        PaymentMfSection currentSection = determineInitialSection(out.transferDate);
         for (int i = 2; i <= last; i++) {
             Row row = sheet.getRow(i);
             if (row == null) continue;
 
             String sourceName = PaymentMfCellReader.readStringCell(row.getCell(colSource));
             String sourceNorm = PaymentMfCellReader.normalize(sourceName);
             Long amount = PaymentMfCellReader.readLongCell(row.getCell(colAmount));
 
             // 合計行の処理（section 別 summary 抽出 + 次セクションへ遷移）
             if (sourceNorm != null && ("合計".equals(sourceNorm) || isTotalRow(row, colSource))) {
                 SectionSummary summary = new SectionSummary();
                 summary.sourceFee = colFee == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colFee));
                 summary.earlyPayment = colEarly == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colEarly));
                 summary.transferAmount = colTransfer == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colTransfer));
                 // 合計行の請求額列は colAmount をそのまま読む (旧 summaryInvoiceTotal と同義)。
                 summary.invoiceTotal = PaymentMfCellReader.readLongCell(row.getCell(colAmount));
                 // 同 section の合計行が複数あれば最後の値で上書き (現実的には起こらない)。
                 out.summaries.put(currentSection, summary);
 
                 // 次セクションへ遷移。PAYMENT_20TH の合計行を 2 度目以降に踏んだ場合は
                 // 状態は PAYMENT_20TH のまま (= 上書き) で、これ以上の section 拡張はしない。
                 if (currentSection == PaymentMfSection.PAYMENT_5TH) {
                     currentSection = PaymentMfSection.PAYMENT_20TH;
                 }
                 continue;
             }
             // メタ行スキップ
             if (isMetaRow(sourceNorm)) continue;
             // 金額ゼロ/未入力はスキップ
             if (amount == null || amount == 0L) continue;
             if (sourceName == null || sourceName.isBlank()) continue;
 
             String supplierCode = null;
             if (colCode != null && colCode >= 0) {
                 supplierCode = PaymentMfCellReader.readStringCell(row.getCell(colCode));
                 if (supplierCode != null) {
                     supplierCode = supplierCode.trim();
                     if (supplierCode.isEmpty() || !supplierCode.chars().allMatch(Character::isDigit)) {
                         supplierCode = null;
                     } else {
                         supplierCode = PaymentMfImportService.normalizePaymentSupplierCode(supplierCode);
                     }
                 }
             }
 
             ParsedEntry pe = new ParsedEntry();
             pe.rowIndex = i + 1;
             pe.supplierCode = supplierCode;
             pe.sourceName = sourceName.trim();
             pe.amount = amount;
             // G2-M3: 旧 boolean afterTotal は section enum に置換。
             pe.section = currentSection;
             // P1-03 案 D: per-supplier の値引/早払/送料相手/相殺/振込金額を読取る。
             // 合計行 (= section 別 summary に集約) ではなく、ここで個別 supplier ごとに保持する。
             pe.transferAmount = colTransfer == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colTransfer));
             pe.fee = colFee == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colFee));
             pe.discount = colDiscount == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colDiscount));
             pe.earlyPayment = colEarly == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colEarly));
             pe.offset = colOffset == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colOffset));
             pe.payinAmount = colPayin == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colPayin));
             out.entries.add(pe);
         }
         return out;
     }
 
     private static Map<String, Integer> buildColumnMap(Row header) {
         Map<String, Integer> map = new LinkedHashMap<>();
         for (int c = 0; c < header.getLastCellNum(); c++) {
             String v = PaymentMfCellReader.readStringCell(header.getCell(c));
             if (v == null) continue;
             String n = PaymentMfCellReader.normalize(v);
             if (n.isEmpty()) continue;
             map.putIfAbsent(n, c);
         }
         return map;
     }
 
     private static boolean isTotalRow(Row row, int colSource) {
         // A列やB列が「合計」という明示
         for (int c = 0; c < Math.min(row.getLastCellNum(), colSource + 2); c++) {
             String v = PaymentMfCellReader.normalize(PaymentMfCellReader.readStringCell(row.getCell(c)));
             if ("合計".equals(v)) return true;
         }
         return false;
     }
 
     private static boolean isMetaRow(String normalized) {
         if (normalized == null || normalized.isEmpty()) return true;
         if (META_EXACT.contains(normalized)) return true;
         for (String p : META_PREFIX) if (normalized.startsWith(p)) return true;
         return false;
     }
 
+    /**
+     * Codex Major #5 (2026-05-06): 送金日 day から初期 section を判定する (パッケージ可視: テスト用)。
+     * <ul>
+     *   <li>day &lt; {@link #SECTION_INITIAL_CUTOFF_DAY} (= 15) → {@link PaymentMfSection#PAYMENT_5TH}</li>
+     *   <li>day &ge; 15 → {@link PaymentMfSection#PAYMENT_20TH}</li>
+     *   <li>{@code transferDate == null} → {@link PaymentMfSection#PAYMENT_5TH} (デフォルト) + WARN ログ</li>
+     * </ul>
+     * 旧実装は常に PAYMENT_5TH 開始だったため、20日払い専用 Excel が来ると初期 section 誤分類を起こしていた。
+     */
+    static PaymentMfSection determineInitialSection(LocalDate transferDate) {
+        if (transferDate == null) {
+            log.warn("送金日が読み取れないため初期 section を PAYMENT_5TH にデフォルト設定します。"
+                    + "20日払い Excel の場合、整合性チェック・PAYABLE 振り分けが誤動作する可能性あり");
+            return PaymentMfSection.PAYMENT_5TH;
+        }
+        return transferDate.getDayOfMonth() < SECTION_INITIAL_CUTOFF_DAY
+                ? PaymentMfSection.PAYMENT_5TH
+                : PaymentMfSection.PAYMENT_20TH;
+    }
+
     /** Excel 明細1行の抽出結果（内部用 POJO）。 */
     static class ParsedEntry {
         int rowIndex;
         String supplierCode;
         String sourceName;
         Long amount;
         /**
          * G2-M3: この明細行が属するセクション (5日払い / 20日払い)。
          * 旧実装の {@code afterTotal} ブール値を置き換える。
          */
         PaymentMfSection section;
         // P1-03 案 D: per-supplier の付帯属性。NULL は該当列がヘッダに存在しないか空欄。
         /** 列 E: 振込金額 (= 打込金額 - 送料相手) */
         Long transferAmount;
         /** 列 F: 送料相手 (仕入先負担の振込手数料) */
         Long fee;
         /** 列 G: 値引 (通常の値引) */
         Long discount;
         /** 列 H: 早払い */
         Long earlyPayment;
         /** 列 I: 相殺 */
         Long offset;
         /** 列 D: 打込金額 (= 請求額 - 値引 - 早払。整合性検証用、CSV 出力には使わない派生値) */
         Long payinAmount;
     }
 
     /** 1 セクション (5日払い or 20日払い) の合計行 summary。 */
     static class SectionSummary {
         /** 列 F: 送料相手合計 */
         Long sourceFee;
         /** 列 H: 早払合計 */
         Long earlyPayment;
         /** 列 E: 振込金額合計 */
         Long transferAmount;
         /** 列 C: 請求額合計 */
         Long invoiceTotal;
     }
 
     /**
      * parseSheet の戻り値: 明細エントリ一覧 + section 別合計行サマリ + 送金日。
      *
      * <p>G2-M3 (2026-05-06): 旧 {@code summarySourceFee/EarlyPayment/TransferAmount/InvoiceTotal}
      * のフラットなフィールドを廃止し、{@code Map<PaymentMfSection, SectionSummary>} に格納する。
      * 5日払いのみの Excel では {@code summaries} に {@link PaymentMfSection#PAYMENT_5TH} のみ入り、
      * {@link PaymentMfSection#PAYMENT_20TH} は欠落する (空セクション許容)。
      */
     static class ParsedExcel {
         List<ParsedEntry> entries = new ArrayList<>();
         LocalDate transferDate;
         Map<PaymentMfSection, SectionSummary> summaries = new EnumMap<>(PaymentMfSection.class);
     }
 }
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -LiteralPath 'backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParserSectionTest.java'" in C:\project\odamitsu-data-hub
 succeeded in 1595ms:
package jp.co.oda32.domain.service.finance;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G2-M3 (2026-05-06) 回帰テスト: {@link PaymentMfExcelParser} の section 別 summary キャプチャ。
 *
 * <p>旧実装は最初の合計行 (= 5日払い summary) しかキャプチャせず、その後の合計行を黙って捨てていた。
 * 結果、5日払い + 20日払い 両セクションがある Excel で 20日払い summary が失われ、
 * 整合性チェック (chk1/chk3) が「5日払い summary vs 5日払い+20日払い 両方の per-supplier sum」を
 * 比較する形で構造的にズレていた。
 *
 * <p>本テストは在庫物 fixture (.xlsx) ではなく POI で in-memory に Excel を構築する
 * (= ステートレス、外部依存なしで section 遷移を直接検証できる)。
 */
class PaymentMfExcelParserSectionTest {

    /**
     * Case 1: 5日払い + 20日払い 両セクションがある Excel。
     * 旧実装は 5日払い summary のみ捕まえて 20日払い summary を取り逃していた。
     */
    @Test
    void parseSheet_両セクション_summary_両方キャプチャ() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("支払い明細");
            writeHeader(sheet, LocalDate.of(2026, 2, 5));

            // 5日払い 明細 (2 行)
            writeEntry(sheet, 2, "100001", "AAA㈱", 100000L, 99000L, 1000L, 0L, 0L, 0L);
            writeEntry(sheet, 3, "100002", "BBB㈱", 50000L,  49500L, 500L,  0L, 0L, 0L);
            // 5日払い 合計 (B 列 "合計"、C=請求, E=振込, F=送料, H=早払)
            writeTotalRow(sheet, 4, 150000L, 148500L, 1500L, 0L);

            // 20日払い 明細 (1 行)
            writeEntry(sheet, 5, "200001", "CCC㈱", 30000L, 29800L, 200L, 0L, 0L, 0L);
            // 20日払い 合計
            writeTotalRow(sheet, 6, 30000L, 29800L, 200L, 0L);

            PaymentMfExcelParser.ParsedExcel parsed = PaymentMfExcelParser.parseSheet(sheet);

            // 送金日が読めている
            assertThat(parsed.transferDate).isEqualTo(LocalDate.of(2026, 2, 5));

            // 両 section の summary がキャプチャされている (旧実装は 5日払い のみだった)
            assertThat(parsed.summaries).containsKeys(PaymentMfSection.PAYMENT_5TH, PaymentMfSection.PAYMENT_20TH);

            PaymentMfExcelParser.SectionSummary s5 = parsed.summaries.get(PaymentMfSection.PAYMENT_5TH);
            assertThat(s5.invoiceTotal).isEqualTo(150000L);
            assertThat(s5.transferAmount).isEqualTo(148500L);
            assertThat(s5.sourceFee).isEqualTo(1500L);
            assertThat(s5.earlyPayment).isEqualTo(0L);

            PaymentMfExcelParser.SectionSummary s20 = parsed.summaries.get(PaymentMfSection.PAYMENT_20TH);
            assertThat(s20.invoiceTotal).isEqualTo(30000L);
            assertThat(s20.transferAmount).isEqualTo(29800L);
            assertThat(s20.sourceFee).isEqualTo(200L);
            assertThat(s20.earlyPayment).isEqualTo(0L);

            // entries の section が正しく振られている (合計行で遷移)
            assertThat(parsed.entries).hasSize(3);
            assertThat(parsed.entries.get(0).section).isEqualTo(PaymentMfSection.PAYMENT_5TH);
            assertThat(parsed.entries.get(1).section).isEqualTo(PaymentMfSection.PAYMENT_5TH);
            assertThat(parsed.entries.get(2).section).isEqualTo(PaymentMfSection.PAYMENT_20TH);
        }
    }

    /**
     * Case 2: 5日払いのみの Excel (合計行 1 個)。
     * 20日払い section は空のまま許容され、summaries には PAYMENT_5TH のみ入る。
     */
    @Test
    void parseSheet_5日払いのみ_PAYMENT20TH_は空セクション() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("支払い明細");
            writeHeader(sheet, LocalDate.of(2026, 2, 5));

            writeEntry(sheet, 2, "100001", "AAA㈱", 100000L, 99000L, 1000L, 0L, 0L, 0L);
            writeEntry(sheet, 3, "100002", "BBB㈱",  50000L, 49500L,  500L, 0L, 0L, 0L);
            writeTotalRow(sheet, 4, 150000L, 148500L, 1500L, 0L);
            // 20日払い 明細・合計なし

            PaymentMfExcelParser.ParsedExcel parsed = PaymentMfExcelParser.parseSheet(sheet);

            assertThat(parsed.summaries).containsKey(PaymentMfSection.PAYMENT_5TH);
            assertThat(parsed.summaries).doesNotContainKey(PaymentMfSection.PAYMENT_20TH);

            assertThat(parsed.entries).hasSize(2);
            assertThat(parsed.entries).allMatch(e -> e.section == PaymentMfSection.PAYMENT_5TH);
        }
    }

    /**
     * Case 3: 5日払い summary が捕まった後、20日払い 明細が PAYMENT_20TH に振られていることを
     * 詳細に検証 (chk3 で section 別 sum を独立比較できる前提を担保)。
     * このテストは旧実装でも entries の数と sourceName は同じだが、
     * section ラベル付与が新規挙動であることを示す。
     *
     * <p>Codex Major #5 (2026-05-06): 送金日 day で初期 section を決めるようになったため、
     * 5日払い + 20日払い 両セクションを含む Excel は実運用と同じく送金日=5日 (day &lt; 15) で開始する。
     */
    @Test
    void parseSheet_合計行を境に_section_遷移する() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("支払い明細");
            // Codex Major #5: 両セクションを含む Excel は 5日払い (5日送金) で開始する想定。
            writeHeader(sheet, LocalDate.of(2026, 2, 5));

            // 5日払い 3 行 + 合計 + 20日払い 2 行 + 合計
            writeEntry(sheet, 2, "100001", "FIVE-A", 10000L, 10000L, 0L, 0L, 0L, 0L);
            writeEntry(sheet, 3, "100002", "FIVE-B", 20000L, 20000L, 0L, 0L, 0L, 0L);
            writeEntry(sheet, 4, "100003", "FIVE-C", 30000L, 30000L, 0L, 0L, 0L, 0L);
            writeTotalRow(sheet, 5, 60000L, 60000L, 0L, 0L);
            writeEntry(sheet, 6, "200001", "TWENTY-A", 5000L, 5000L, 0L, 0L, 0L, 0L);
            writeEntry(sheet, 7, "200002", "TWENTY-B", 7000L, 7000L, 0L, 0L, 0L, 0L);
            writeTotalRow(sheet, 8, 12000L, 12000L, 0L, 0L);

            PaymentMfExcelParser.ParsedExcel parsed = PaymentMfExcelParser.parseSheet(sheet);

            assertThat(parsed.entries).hasSize(5);
            // section ラベルが合計行を境に遷移している
            assertThat(parsed.entries.get(0).section).isEqualTo(PaymentMfSection.PAYMENT_5TH);
            assertThat(parsed.entries.get(1).section).isEqualTo(PaymentMfSection.PAYMENT_5TH);
            assertThat(parsed.entries.get(2).section).isEqualTo(PaymentMfSection.PAYMENT_5TH);
            assertThat(parsed.entries.get(3).section).isEqualTo(PaymentMfSection.PAYMENT_20TH);
            assertThat(parsed.entries.get(4).section).isEqualTo(PaymentMfSection.PAYMENT_20TH);

            // section 別 summary が独立に取れている (chk1/chk3 を section 別判定する基盤)
            assertThat(parsed.summaries.get(PaymentMfSection.PAYMENT_5TH).invoiceTotal).isEqualTo(60000L);
            assertThat(parsed.summaries.get(PaymentMfSection.PAYMENT_20TH).invoiceTotal).isEqualTo(12000L);
        }
    }

    /**
     * Codex Major #5 (2026-05-06): 20日払い専用 Excel (送金日 day &gt;= 15) は
     * PAYMENT_20TH 開始でなければならない。旧実装は常に PAYMENT_5TH 開始だったため、
     * 20日払い Excel の 全 entries が誤って PAYMENT_5TH 扱いされていた
     * (= PAYABLE 主行突合・整合性チェック・aux 保存が誤動作)。
     */
    @Test
    void parseSheet_20日払い専用_Excel_は_PAYMENT_20TH_開始() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("支払い明細");
            // 送金日 = 20日 (day=20 >= 15) → 20日払い相当
            writeHeader(sheet, LocalDate.of(2026, 2, 20));

            // 20日払い 明細のみ + 合計 1 個
            writeEntry(sheet, 2, "200001", "DDD㈱", 40000L, 39800L, 200L, 0L, 0L, 0L);
            writeEntry(sheet, 3, "200002", "EEE㈱", 25000L, 24800L, 200L, 0L, 0L, 0L);
            writeTotalRow(sheet, 4, 65000L, 64600L, 400L, 0L);

            PaymentMfExcelParser.ParsedExcel parsed = PaymentMfExcelParser.parseSheet(sheet);

            // summary は PAYMENT_20TH のみキャプチャされる (PAYMENT_5TH は空)
            assertThat(parsed.summaries).containsKey(PaymentMfSection.PAYMENT_20TH);
            assertThat(parsed.summaries).doesNotContainKey(PaymentMfSection.PAYMENT_5TH);

            // 全 entries が PAYMENT_20TH 扱い
            assertThat(parsed.entries).hasSize(2);
            assertThat(parsed.entries).allMatch(e -> e.section == PaymentMfSection.PAYMENT_20TH);

            // summary 値も PAYMENT_20TH 側に入る
            PaymentMfExcelParser.SectionSummary s20 = parsed.summaries.get(PaymentMfSection.PAYMENT_20TH);
            assertThat(s20.invoiceTotal).isEqualTo(65000L);
            assertThat(s20.transferAmount).isEqualTo(64600L);
        }
    }

    /**
     * Codex Major #5 (2026-05-06): 送金日不明時は WARN ログ + デフォルトで PAYMENT_5TH 開始。
     * {@link PaymentMfExcelParser#determineInitialSection} の単体テスト。
     */
    @Test
    void determineInitialSection_送金日不明はPAYMENT_5TH_デフォルト() {
        assertThat(PaymentMfExcelParser.determineInitialSection(null))
                .isEqualTo(PaymentMfSection.PAYMENT_5TH);
    }

    /**
     * Codex Major #5: 送金日 day &lt; 15 → PAYMENT_5TH (5日払い相当、振替で前後しても OK)。
     */
    @Test
    void determineInitialSection_前半は_PAYMENT_5TH() {
        for (int day : new int[]{1, 4, 5, 6, 7, 14}) {
            assertThat(PaymentMfExcelParser.determineInitialSection(LocalDate.of(2026, 2, day)))
                    .as("day=%d", day)
                    .isEqualTo(PaymentMfSection.PAYMENT_5TH);
        }
    }

    /**
     * Codex Major #5: 送金日 day &gt;= 15 → PAYMENT_20TH (20日払い相当、振替で前後しても OK)。
     */
    @Test
    void determineInitialSection_後半は_PAYMENT_20TH() {
        for (int day : new int[]{15, 19, 20, 21, 25, 31}) {
            // 31日は月末まである月のみ。2026-1 (1月) は 31 日まである。
            LocalDate d = day == 31 ? LocalDate.of(2026, 1, day) : LocalDate.of(2026, 2, day);
            assertThat(PaymentMfExcelParser.determineInitialSection(d))
                    .as("day=%d", day)
                    .isEqualTo(PaymentMfSection.PAYMENT_20TH);
        }
    }

    // ===========================================================
    // Helpers: 振込明細 Excel と同形のヘッダ・行を構築する
    // ===========================================================

    /** 行 0 に送金日 (E1)、行 1 に列名行を書き込む。 */
    private static void writeHeader(Sheet sheet, LocalDate transferDate) {
        Workbook wb = sheet.getWorkbook();
        CreationHelper ch = wb.getCreationHelper();
        CellStyle dateStyle = wb.createCellStyle();
        dateStyle.setDataFormat(ch.createDataFormat().getFormat("yyyy/m/d"));
        Row r0 = sheet.createRow(0);
        Cell c4 = r0.createCell(4);
        c4.setCellStyle(dateStyle);
        c4.setCellValue(java.sql.Date.valueOf(transferDate));
        // 列名行 (parser は normalize 済 文字列で照合する)
        Row r1 = sheet.createRow(1);
        // A=仕入コード, B=送り先, C=請求額, D=打ち込み額, E=振込金額, F=送料相手, G=値引, H=早払い, I=相殺
        r1.createCell(0).setCellValue("仕入コード");
        r1.createCell(1).setCellValue("送り先");
        r1.createCell(2).setCellValue("請求額");
        r1.createCell(3).setCellValue("打ち込み額");
        r1.createCell(4).setCellValue("振込金額");
        r1.createCell(5).setCellValue("送料相手");
        r1.createCell(6).setCellValue("値引");
        r1.createCell(7).setCellValue("早払い");
        r1.createCell(8).setCellValue("相殺");
    }

    private static void writeEntry(Sheet sheet, int rowIdx, String code, String name,
                                    long invoice, long transfer, long fee,
                                    long discount, long early, long offset) {
        Row r = sheet.createRow(rowIdx);
        r.createCell(0).setCellValue(Long.parseLong(code));
        r.createCell(1).setCellValue(name);
        r.createCell(2).setCellValue(invoice);
        r.createCell(3).setCellValue(invoice - discount - early); // 打ち込み額 (派生)
        r.createCell(4).setCellValue(transfer);
        r.createCell(5).setCellValue(fee);
        r.createCell(6).setCellValue(discount);
        r.createCell(7).setCellValue(early);
        r.createCell(8).setCellValue(offset);
    }

    /** 合計行: B 列に "合計"、C=請求合計, E=振込合計, F=送料合計, H=早払合計 を書く。 */
    private static void writeTotalRow(Sheet sheet, int rowIdx,
                                       long invoice, long transfer, long fee, long early) {
        Row r = sheet.createRow(rowIdx);
        r.createCell(1).setCellValue("合計");
        r.createCell(2).setCellValue(invoice);
        r.createCell(3).setCellValue(invoice - early); // 打ち込み額
        r.createCell(4).setCellValue(transfer);
        r.createCell(5).setCellValue(fee);
        r.createCell(7).setCellValue(early);
    }

}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git show --no-ext-diff --unified=60 60d7d81 -- backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java' in C:\project\odamitsu-data-hub
 succeeded in 1344ms:
commit 60d7d815f07409beb1375fc08c96bd6dcfc8d13a
Author: kazuki <k_oda@odamitsu.com>
Date:   Thu May 7 09:18:25 2026 +0900

    fix(codex-r2): Codex 第 2 弾批判レビュー対応 (Critical + P1 + 9 Major + 1 Minor)
    
    3 commit (audit/mf/finance) を Codex 批判レビューにかけ、12 件の指摘を修正。
    
    ## Critical / P1 (即修正)
    * Critical: BULK 不変条件違反時の SUM フォールバックを fail-closed (例外) 化
      - PaymentMfImportService.sumVerifiedAmountForGroup で全行 BULK 不一致時に
        FinanceInternalException 422、過大計上の MF CSV 流出を遮断
    * P1: V033 再暗号化が片側カラムのみで refresh_token が旧鍵残るバグ修正
      - reencryptMfTokens() で access + refresh を 1 UPDATE で同時再暗号化
      - decrypt 失敗時 skip + WARN ログで idempotent 強化 (recipherOrSkip)
    
    ## Major (9 件)
    * MF P2: V037 ordering note は runbook §6' に集約 (V037 は revert で checksum 復元)
    * Audit P2: FinanceAuditAspect で Loader 解決失敗時の captureReturn/Args フォールバック追加
    * G2-M2 Major: verification_source 3 経路 (BULK/MANUAL/MF_OVERRIDE) を共通 advisory lock で直列化
      - FinancePayableLock util 切り出し、computePayableLockKey + acquire
      - applyVerification / verify / applyMfOverride で同一 lock キー
    * G2-M3 Major: force audit 全件保存 (V043 finance_audit_log.force_mismatch_details JSONB)
      - 50 件切り詰めの reason と全件 JSON を分離、UI 全件確認可能化への土台
    * G2-M4 Major: forceReason 必須化 + reason 空 → FORCE_REASON_REQUIRED 400
    * G2-M5 Major: 20日払い専用 Excel が PAYMENT_5TH 誤分類されるバグ修正
      - PaymentMfExcelParser.determineInitialSection で送金日 day による初期 section 判定
    * G2-M5 Major: OFFSET マスタ欠落時の hardcoded default fallback + 最後の active 行削除禁止
    * G2-M6 Major: P1-02 opening 注入空時の警告 (SupplierBalances/IntegrityReport DTO)
    * Frontend Major: force apply UX 強化 (CSV download, reviewedAll checkbox, forceReason 必須)
    
    ## Minor
    * G2-M? Minor: V044 で V040 backfill 検査 (NOTICE のみ、自動修復なし)
      - 実行結果: 560 行で history 突合なし = false positive (P1-08 導入前の歴史データ)
    
    ## 新規 migration
    - V043 finance_audit_log.force_mismatch_details JSONB + GIN index
    - V044 V040 backfill 検査 (RAISE NOTICE のみ)
    
    ## 新規 runbook
    - runbook-payment-mf-bulk-invariant-violation.md (Critical fix の運用手順)
    - runbook-payment-mf-force-apply.md (force=true の二段認可運用)
    
    ## CLAUDE.md
    - Review Guidelines セクション追加 (Repository Context / Review Priorities /
      Architecture Rules) — AI レビュー時の優先順位を明示
    
    ## テスト
    - 既存 233 + 新規 ~10 件 PASS、frontend tsc PASS、リグレッション 0
    - 5 group 並列修正で衝突なし
    
    Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>

diff --git a/backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java b/backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java
index 4f0da48..2f3ea01 100644
--- a/backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java
+++ b/backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java
@@ -1,164 +1,184 @@
 package jp.co.oda32.domain.service.finance;
 
+import jakarta.persistence.EntityManager;
+import jakarta.persistence.PersistenceContext;
 import jp.co.oda32.audit.AuditLog;
 import jp.co.oda32.constant.FinanceConstants;
 import jp.co.oda32.domain.model.embeddable.TConsistencyReviewPK;
 import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
 import jp.co.oda32.domain.model.finance.TConsistencyReview;
 import jp.co.oda32.domain.model.master.MLoginUser;
 import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
 import jp.co.oda32.domain.repository.finance.TConsistencyReviewRepository;
 import jp.co.oda32.domain.repository.master.LoginUserRepository;
 import jp.co.oda32.dto.finance.ConsistencyReviewRequest;
 import jp.co.oda32.dto.finance.ConsistencyReviewResponse;
 import lombok.RequiredArgsConstructor;
 import lombok.extern.log4j.Log4j2;
 import org.springframework.http.HttpStatus;
 import org.springframework.stereotype.Service;
 import org.springframework.transaction.annotation.Transactional;
 import org.springframework.web.server.ResponseStatusException;
 
 import java.math.BigDecimal;
 import java.math.RoundingMode;
 import java.time.Instant;
 import java.time.LocalDate;
 import java.util.HashMap;
 import java.util.List;
 import java.util.Map;
 import java.util.Optional;
 
 /**
  * 整合性レポート 差分確認機能 Service (案 X + Y)。
  * <p>
  * IGNORE: review 作成のみ、副作用なし。
  * MF_APPLY: 対象 summary 行の verified_amount を MF 金額に合わせる。税率別に按分。
  * DELETE / IGNORE 切替: MF_APPLY で書き換えた verified_amount を previous から復元。
  * <p>
  * 設計書: claudedocs/design-consistency-review.md
  *
  * @since 2026-04-23
  */
 @Service
 @Log4j2
 @RequiredArgsConstructor
 @Transactional
 public class ConsistencyReviewService {
 
     private static final String ENTRY_MF_ONLY = "mfOnly";
     private static final String ENTRY_SELF_ONLY = "selfOnly";
     private static final String ENTRY_AMOUNT_MISMATCH = "amountMismatch";
     private static final String ACTION_IGNORE = "IGNORE";
     private static final String ACTION_MF_APPLY = "MF_APPLY";
 
     private final TConsistencyReviewRepository reviewRepository;
     private final TAccountsPayableSummaryRepository summaryRepository;
     private final LoginUserRepository loginUserRepository;
 
+    /**
+     * Codex Major #2 (2026-05-06): {@link #applyMfOverride} / {@link #rollbackVerifiedAmounts} で
+     * {@link FinancePayableLock} を取得する用。BULK / MANUAL / MF_OVERRIDE の 3 経路で
+     * 同一 (shop, transaction_month) lock を共有する。
+     */
+    @PersistenceContext
+    private EntityManager entityManager;
+
     @AuditLog(table = "t_consistency_review", operation = "upsert",
             pkExpression = "{'shopNo': #a0.shopNo, 'entryType': #a0.entryType, 'entryKey': #a0.entryKey, 'transactionMonth': #a0.transactionMonth}",
             captureArgsAsAfter = true)
     public ConsistencyReviewResponse upsert(ConsistencyReviewRequest req, Integer userNo) {
         validateRequest(req);
 
+        // Codex Major #2 (2026-05-06): BULK / MANUAL / MF_OVERRIDE の 3 書込経路で同一 advisory lock を取り、
+        // last-write-wins race を排除する。upsert 内で rollback (= 副作用剥がし) と
+        // applyMfOverride (= MF_OVERRIDE 書込) を順に行うため、最初にまとめて lock を取る。
+        // shopNo / transactionMonth は req から取れるため、ここで取得して保存処理全体を直列化する。
+        FinancePayableLock.acquire(entityManager, req.getShopNo(), req.getTransactionMonth());
+
         TConsistencyReviewPK pk = new TConsistencyReviewPK(
                 req.getShopNo(), req.getEntryType(), req.getEntryKey(), req.getTransactionMonth());
 
         Optional<TConsistencyReview> existingOpt = reviewRepository.findById(pk);
         // 旧 review に previous snapshot が残っていれば先にロールバック (副作用を剥がす)。
         // SF-04: action 種別ではなく snapshot 有無で判定 (IGNORE で previous 残置時もロールバック可能)。
         if (existingOpt.isPresent()) {
             TConsistencyReview old = existingOpt.get();
             if (old.getPreviousVerifiedAmounts() != null && !old.getPreviousVerifiedAmounts().isEmpty()) {
                 rollbackVerifiedAmounts(req.getShopNo(), req.getEntryKey(),
                         req.getTransactionMonth(), old.getPreviousVerifiedAmounts());
             }
         }
 
         // SF-04 補足: IGNORE 経路では previous=null を明示的に保存し、過去の MF_APPLY snapshot を完全クリア。
         // (上の rollback で副作用は剥がしてあるので、再ロールバックを誘発しないよう null で上書き)
         Map<String, BigDecimal> previous = null;
         boolean verifiedUpdated = false;
         if (ACTION_MF_APPLY.equals(req.getActionType())) {
             previous = applyMfOverride(req);
             verifiedUpdated = true;
         }
 
         TConsistencyReview review = TConsistencyReview.builder()
                 .pk(pk)
                 .actionType(req.getActionType())
                 .selfSnapshot(req.getSelfSnapshot())
                 .mfSnapshot(req.getMfSnapshot())
                 .previousVerifiedAmounts(previous) // IGNORE 時は null (snapshot クリア)
                 .reviewedBy(userNo)
                 .reviewedAt(Instant.now())
                 .note(req.getNote())
                 .build();
         reviewRepository.save(review);
 
         return buildResponse(review, verifiedUpdated);
     }
 
     @AuditLog(table = "t_consistency_review", operation = "delete",
             pkExpression = "{'shopNo': #a0, 'entryType': #a1, 'entryKey': #a2, 'transactionMonth': #a3}",
             captureArgsAsAfter = true)
     public void delete(Integer shopNo, String entryType, String entryKey, LocalDate transactionMonth) {
+        // Codex Major #2 (2026-05-06): rollbackVerifiedAmounts も MF_OVERRIDE 経路の書込なので
+        // 同一 advisory lock を取得する。
+        FinancePayableLock.acquire(entityManager, shopNo, transactionMonth);
+
         TConsistencyReviewPK pk = new TConsistencyReviewPK(shopNo, entryType, entryKey, transactionMonth);
         Optional<TConsistencyReview> existing = reviewRepository.findById(pk);
         if (existing.isEmpty()) return;
         TConsistencyReview r = existing.get();
         // SF-04: action 種別ではなく snapshot 有無で判定 (IGNORE で previous 残置時もロールバック可能)。
         if (r.getPreviousVerifiedAmounts() != null && !r.getPreviousVerifiedAmounts().isEmpty()) {
             rollbackVerifiedAmounts(shopNo, entryKey, transactionMonth, r.getPreviousVerifiedAmounts());
         }
         reviewRepository.deleteById(pk);
     }
 
     /** 整合性レポートサービスから呼ばれる: 期間内 review を PK Map で返す。reviewer 名も付与。 */
     @Transactional(readOnly = true)
     public Map<ReviewKey, ReviewInfo> findForPeriod(Integer shopNo, LocalDate fromMonth, LocalDate toMonth) {
         List<Object[]> rows = reviewRepository.findWithReviewerNameForPeriod(shopNo, fromMonth, toMonth);
         Map<ReviewKey, ReviewInfo> map = new HashMap<>();
         for (Object[] row : rows) {
             TConsistencyReview r = (TConsistencyReview) row[0];
             String reviewerName = (String) row[1];
             ReviewKey key = new ReviewKey(r.getPk().getEntryType(), r.getPk().getEntryKey(), r.getPk().getTransactionMonth());
             map.put(key, new ReviewInfo(
                     r.getActionType(),
                     r.getSelfSnapshot(),
                     r.getMfSnapshot(),
                     r.getReviewedBy(),
                     reviewerName,
                     r.getReviewedAt(),
                     r.getNote()));
         }
         return map;
     }
 
     // ==================== private helpers ====================
 
     private void validateRequest(ConsistencyReviewRequest req) {
         if (!ACTION_IGNORE.equals(req.getActionType()) && !ACTION_MF_APPLY.equals(req.getActionType())) {
             throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                     "actionType は IGNORE か MF_APPLY のみ");
         }
         if (!ENTRY_MF_ONLY.equals(req.getEntryType())
                 && !ENTRY_SELF_ONLY.equals(req.getEntryType())
                 && !ENTRY_AMOUNT_MISMATCH.equals(req.getEntryType())) {
             throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                     "entryType は mfOnly / selfOnly / amountMismatch");
         }
         if (ENTRY_MF_ONLY.equals(req.getEntryType()) && ACTION_MF_APPLY.equals(req.getActionType())) {
             throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                     "mfOnly の MF_APPLY は未対応 (自社側 supplier 行がないため)");
         }
     }
 
     /**
      * MF_APPLY の副作用: 対象 summary 行の verified_amount を MF 金額に合わせる。
      * <ul>
      *   <li>selfOnly: 全税率行 verified_amount=0 (自社取消)</li>
      *   <li>amountMismatch: 税率別 change 比で target = mfSnapshot+payment_settled を按分</li>
      * </ul>
      *
      * @return 更新前の verified_amount 退避 (税率 → 金額 Map)
      */
diff --git a/backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java b/backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java
index f0fe7da..a240fc0 100644
--- a/backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java
+++ b/backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java
@@ -1,181 +1,196 @@
 package jp.co.oda32.domain.service.finance;
 
+import jakarta.persistence.EntityManager;
+import jakarta.persistence.PersistenceContext;
 import jakarta.persistence.criteria.Predicate;
 import jp.co.oda32.annotation.SkipShopCheck;
 import jp.co.oda32.audit.AuditLog;
 import jp.co.oda32.constant.FinanceConstants;
 import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
 import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
 import jp.co.oda32.dto.finance.AccountsPayableSummaryProjection;
 import jp.co.oda32.dto.finance.AccountsPayableSummaryResponse;
 import jp.co.oda32.exception.FinanceInternalException;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.data.domain.Page;
 import org.springframework.data.domain.Pageable;
 import org.springframework.data.jpa.domain.Specification;
 import org.springframework.stereotype.Service;
 import org.springframework.transaction.annotation.Transactional;
 
 import java.math.BigDecimal;
 import java.math.RoundingMode;
 import java.time.LocalDate;
 import java.util.ArrayList;
 import java.util.List;
 
 /**
  * 20日締め買掛金テーブルのサービスクラス
  *
  * @author k_oda
  * @since 2024/09/10
  */
 @Service
 public class TAccountsPayableSummaryService {
 
     private final TAccountsPayableSummaryRepository repository;
 
+    /**
+     * Codex Major #2 (2026-05-06): {@link #verify} で {@link FinancePayableLock} を取得する用。
+     * applyVerification (BULK) / applyMfOverride (MF_OVERRIDE) と同一 (shop, transaction_month) lock を
+     * 共有するため、advisory lock 発行用 EntityManager を直接保持する。
+     */
+    @PersistenceContext
+    private EntityManager entityManager;
+
     @Autowired
     public TAccountsPayableSummaryService(TAccountsPayableSummaryRepository repository) {
         this.repository = repository;
     }
 
     @SkipShopCheck
     public Page<TAccountsPayableSummary> findPaged(
             Integer shopNo,
             Integer supplierNo,
             LocalDate transactionMonth,
             String verificationFilter,
             Pageable pageable) {
         Specification<TAccountsPayableSummary> spec = buildSpec(shopNo, supplierNo, transactionMonth, verificationFilter);
         return repository.findAll(spec, pageable);
     }
 
     @SkipShopCheck
     public AccountsPayableSummaryResponse summary(Integer shopNo, LocalDate transactionMonth) {
         // SF-24: 旧実装 (findAll(spec) → アプリ側集計) を JPQL aggregate に置換。
         // 行数増加に対する性能劣化を回避し、互換 Response 構造を維持する。
         AccountsPayableSummaryProjection p = repository.aggregateSummary(shopNo, transactionMonth);
         long total = p != null && p.totalCount() != null ? p.totalCount() : 0L;
         long unverified = p != null && p.unverifiedCount() != null ? p.unverifiedCount() : 0L;
         long unmatched = p != null && p.unmatchedCount() != null ? p.unmatchedCount() : 0L;
         long matched = p != null && p.matchedCount() != null ? p.matchedCount() : 0L;
         BigDecimal diffSum = p != null && p.unmatchedDifferenceSum() != null
                 ? p.unmatchedDifferenceSum() : BigDecimal.ZERO;
         return AccountsPayableSummaryResponse.builder()
                 .transactionMonth(transactionMonth)
                 .totalCount(total)
                 .unverifiedCount(unverified)
                 .unmatchedCount(unmatched)
                 .matchedCount(matched)
                 .unmatchedDifferenceSum(diffSum)
                 .build();
     }
 
     private Specification<TAccountsPayableSummary> buildSpec(
             Integer shopNo, Integer supplierNo, LocalDate transactionMonth, String verificationFilter) {
         return (root, query, cb) -> {
             List<Predicate> preds = new ArrayList<>();
             if (shopNo != null) {
                 preds.add(cb.equal(root.get("shopNo"), shopNo));
             }
             if (supplierNo != null) {
                 preds.add(cb.equal(root.get("supplierNo"), supplierNo));
             }
             if (transactionMonth != null) {
                 preds.add(cb.equal(root.get("transactionMonth"), transactionMonth));
             }
             if (verificationFilter != null) {
                 switch (verificationFilter) {
                     case "unverified":
                         preds.add(cb.isNull(root.get("verificationResult")));
                         break;
                     case "unmatched":
                         preds.add(cb.equal(root.get("verificationResult"), 0));
                         break;
                     case "matched":
                         preds.add(cb.equal(root.get("verificationResult"), 1));
                         break;
                     default:
                         // all: no filter
                 }
             }
             return cb.and(preds.toArray(new Predicate[0]));
         };
     }
 
     public TAccountsPayableSummary getByPK(int shopNo, int supplierNo, LocalDate transactionMonth, BigDecimal taxRate) {
         // PostgreSQL の numeric 比較は scale 非依存だが、念のため保存フォーマット(scale=2)で問い合わせる
         BigDecimal rate = taxRate != null ? taxRate.setScale(2, RoundingMode.HALF_UP) : null;
         return repository.getByShopNoAndSupplierNoAndTransactionMonthAndTaxRate(shopNo, supplierNo, transactionMonth, rate);
     }
 
     /**
      * 手動で支払額を検証し、差額計算と一致判定を行います。
      * verifiedManually=true をセットし、次回 SMILE 再検証バッチで上書きされないようにします。
      */
     @Transactional
     @AuditLog(table = "t_accounts_payable_summary", operation = "verify",
             pkExpression = "{'shopNo': #a0, 'supplierNo': #a1, 'transactionMonth': #a2, 'taxRate': #a3}",
             captureArgsAsAfter = true)
     public TAccountsPayableSummary verify(
             int shopNo, int supplierNo, LocalDate transactionMonth, BigDecimal taxRate,
             BigDecimal verifiedAmount, String note) {
+        // Codex Major #2 (2026-05-06): BULK / MANUAL / MF_OVERRIDE の 3 書込経路で同一 advisory lock を取り、
+        // last-write-wins race を排除する。同一 (shop, transactionMonth) で applyVerification と
+        // applyMfOverride が同時に走ると、後着の verification_source が前者を上書きしてしまうため。
+        FinancePayableLock.acquire(entityManager, shopNo, transactionMonth);
+
         TAccountsPayableSummary summary = getByPK(shopNo, supplierNo, transactionMonth, taxRate);
         if (summary == null) {
             throw new IllegalArgumentException("対象の買掛金集計が見つかりません");
         }
         applyVerification(summary, verifiedAmount);
         summary.setVerifiedManually(Boolean.TRUE);
         summary.setVerificationNote(note);
         // G2-M1/M10 (V040): 書込経路を MANUAL_VERIFICATION として明示記録。
         // 再 upload 保護判定 (PaymentMfImportService.applyVerification) と
         // sumVerifiedAmountForGroup の SUM 経路選択で参照される。
         summary.setVerificationSource(FinanceConstants.VERIFICATION_SOURCE_MANUAL);
         // 振込明細一括検証と同じ挙動: 一致なら MF出力=ON、不一致なら MF出力=OFF
         // ユーザーが後で Switch で明示的に上書き可能
         summary.setMfExportEnabled(Integer.valueOf(1).equals(summary.getVerificationResult()));
         return save(summary);
     }
 
     /**
      * 手動確定を解除します。次回 SMILE 再検証バッチで上書きされるようになります。
      */
     @Transactional
     @AuditLog(table = "t_accounts_payable_summary", operation = "release_manual_lock",
             pkExpression = "{'shopNo': #a0, 'supplierNo': #a1, 'transactionMonth': #a2, 'taxRate': #a3}",
             captureArgsAsAfter = true)
     public TAccountsPayableSummary releaseManualLock(
             int shopNo, int supplierNo, LocalDate transactionMonth, BigDecimal taxRate) {
         TAccountsPayableSummary summary = getByPK(shopNo, supplierNo, transactionMonth, taxRate);
         if (summary == null) {
             throw new IllegalArgumentException("対象の買掛金集計が見つかりません");
         }
         summary.setVerifiedManually(Boolean.FALSE);
         // G2-M1/M10 (V040): 手動確定解除時は source 列も NULL に戻す
         // (verified_manually=false 行は read 側で taxIncludedAmountChange にフォールバックされるため)。
         summary.setVerificationSource(null);
         return repository.save(summary);
     }
 
     @Transactional
     @AuditLog(table = "t_accounts_payable_summary", operation = "mf_export_toggle",
             pkExpression = "{'shopNo': #a0, 'supplierNo': #a1, 'transactionMonth': #a2, 'taxRate': #a3}",
             captureArgsAsAfter = true)
     public TAccountsPayableSummary updateMfExport(
             int shopNo, int supplierNo, LocalDate transactionMonth, BigDecimal taxRate, boolean enabled) {
         TAccountsPayableSummary summary = getByPK(shopNo, supplierNo, transactionMonth, taxRate);
         if (summary == null) {
             throw new IllegalArgumentException("対象の買掛金集計が見つかりません");
         }
         summary.setMfExportEnabled(enabled);
         return repository.save(summary);
     }
 
     private void applyVerification(TAccountsPayableSummary summary, BigDecimal verifiedAmount) {
         // 請求額（振込明細 or 手入力）を専用列に保存。tax_included_amount は
         // MF出力スナップショットとして別管理するため、検証時はここに保存する。
         summary.setVerifiedAmount(verifiedAmount);
         summary.setTaxIncludedAmount(verifiedAmount);
 
         // 税率は集計時に必ず入っている前提。null の場合はデータ不整合として fail fast し、
         // 誤った税抜額で上書きすることを避ける（非課税0%と10%が取り違えられる等）。
         if (summary.getTaxRate() == null) {
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git diff 60d7d81''^ 60d7d81 -- frontend/components/pages/finance/payment-mf-import.tsx | Select-Object -First 220' in C:\project\odamitsu-data-hub
 succeeded in 1528ms:
diff --git a/frontend/components/pages/finance/payment-mf-import.tsx b/frontend/components/pages/finance/payment-mf-import.tsx
index b73d9e1..71be900 100644
--- a/frontend/components/pages/finance/payment-mf-import.tsx
+++ b/frontend/components/pages/finance/payment-mf-import.tsx
@@ -7,17 +7,18 @@ import { handleApiError } from '@/lib/api-error-handler'
 import { Button } from '@/components/ui/button'
 import { Input } from '@/components/ui/input'
 import { Label } from '@/components/ui/label'
+import { Textarea } from '@/components/ui/textarea'
 import { PageHeader } from '@/components/features/common/PageHeader'
 import { ConfirmDialog } from '@/components/features/common/ConfirmDialog'
 import {
-  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
+  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogFooter,
 } from '@/components/ui/dialog'
 import {
   Select, SelectTrigger, SelectValue, SelectContent, SelectItem,
 } from '@/components/ui/select'
 import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
 import { Checkbox } from '@/components/ui/checkbox'
-import { Download, Upload, AlertCircle, AlertTriangle, Scale, History, CheckCheck, ShieldAlert } from 'lucide-react'
+import { Download, Upload, AlertCircle, AlertTriangle, Scale, History, CheckCheck, ShieldAlert, FileDown } from 'lucide-react'
 import Link from 'next/link'
 import { toast } from 'sonner'
 import type {
@@ -39,6 +40,12 @@ export default function PaymentMfImportPage() {
   const [confirmVerify, setConfirmVerify] = useState(false)
   // G2-M2: per-supplier 1 冁E��一致がある時に「強制実行に同意」�EチェチE��忁E��E   const [forceAcknowledged, setForceAcknowledged] = useState(false)
+  // G2-M2 (Frontend Major): force apply 専用ダイアログ、E+  // 100 件省略表示で承認�E容と実�E琁E��乖離するリスクを抑えるため、E+  // CSV ダウンローチE+ 全件確誁Echeckbox + 反映琁E�� textarea を忁E��化する、E+  const [forceDialogOpen, setForceDialogOpen] = useState(false)
+  const [reviewedAll, setReviewedAll] = useState(false)
+  const [forceReason, setForceReason] = useState('')
 
   const previewMut = useMutation({
     mutationFn: async (f: File) => {
@@ -79,10 +86,16 @@ export default function PaymentMfImportPage() {
   })
 
   const verifyMut = useMutation({
-    mutationFn: async (args: { uploadId: string; force: boolean }) =>
-      api.post<PaymentMfVerifyResult>(`/finance/payment-mf/verify/${args.uploadId}`, {
-        force: args.force,
-      }),
+    mutationFn: async (args: { uploadId: string; force: boolean; forceReason?: string }) => {
+      // backend ぁE`forceReason` 未対応でめESpring の default Jackson 設宁E(FAIL_ON_UNKNOWN_PROPERTIES=false) で無視される、E+      // 別エージェントが backend 側の忁E��化を進めてぁE��前提で、force=true 時�E常に送る、E+      const body: { force: boolean; forceReason?: string } = { force: args.force }
+      if (args.force && args.forceReason) body.forceReason = args.forceReason
+      return api.post<PaymentMfVerifyResult>(
+        `/finance/payment-mf/verify/${args.uploadId}`,
+        body,
+      )
+    },
     onSuccess: (r, vars) => {
       const baseMsg =
         `買掛��一覧に反映しました�E�一致 ${r.matchedCount} / 差異 ${r.diffCount} / 買掛��なぁE${r.notFoundCount}�E�`
@@ -100,6 +113,8 @@ export default function PaymentMfImportPage() {
       }
       // 反映成功後�E force 同意状態をリセチE��
       setForceAcknowledged(false)
+      setReviewedAll(false)
+      setForceReason('')
     },
     // G3-M12: PER_SUPPLIER_MISMATCH 等�E business code は handleApiError で個別誘封E     onError: (e) => handleApiError(e, { fallbackMessage: '反映失敁E }),
@@ -287,10 +302,28 @@ export default function PaymentMfImportPage() {
                   ))}
                   {preview.amountReconciliation!.perSupplierMismatches.length > 100 && (
                     <div className="text-muted-foreground">
-                      ...�E�残り {preview.amountReconciliation!.perSupplierMismatches.length - 100} 件は省略�E�E+                      ...�E�残り {preview.amountReconciliation!.perSupplierMismatches.length - 100} 件は省略 / 下�E CSV で全件確認可�E�E                     </div>
                   )}
                 </div>
+                {/*
+                  G2-M2 (Frontend Major): 100 件趁E�E UI で省略するため、�E件確認�Eための
+                  CSV ダウンロード経路を提供。force 反映前に Excel で目視レビュー可能、E+                */}
+                <div className="mt-2 flex flex-wrap items-center gap-2">
+                  <Button
+                    type="button"
+                    variant="outline"
+                    size="sm"
+                    onClick={() => downloadMismatchesCsv(preview.amountReconciliation!.perSupplierMismatches)}
+                  >
+                    <FileDown className="mr-1 h-4 w-4" />
+                    全 {preview.amountReconciliation!.perSupplierMismatches.length} 件 CSV ダウンローチE+                  </Button>
+                  <span className="text-xs text-muted-foreground">
+                    強制反映時�E全件 audit log に保存されます、E+                  </span>
+                </div>
                 <div className="mt-3 flex items-start gap-2 rounded border border-red-300 bg-red-100 p-2 text-xs">
                   <Checkbox
                     id="force-acknowledge"
@@ -337,7 +370,17 @@ export default function PaymentMfImportPage() {
                   || verifyMut.isPending
                   || (hasPerSupplierMismatch(preview) && !forceAcknowledged)
                 }
-                onClick={() => setConfirmVerify(true)}
+                onClick={() => {
+                  // G2-M2 (Frontend Major): force 反映時�E CSV download + 反映琁E�� + 全件確誁Echeckbox
+                  // を持つ専用ダイアログへ。通常確宁E/ 再確定�E従来の ConfirmDialog を使ぁE��E+                  if (hasPerSupplierMismatch(preview)) {
+                    setReviewedAll(false)
+                    setForceReason('')
+                    setForceDialogOpen(true)
+                  } else {
+                    setConfirmVerify(true)
+                  }
+                }}
               >
                 <CheckCheck className="mr-1 h-4 w-4" />
                 {verifyMut.isPending
@@ -530,41 +573,122 @@ export default function PaymentMfImportPage() {
         </DialogContent>
       </Dialog>
 
+      {/*
+        通常確宁E/ 再確定ダイアログ、E+        force=true 経路は下�E専用 Dialog (forceDialogOpen) で扱ぁE��め、ここでは扱わなぁE��E+      */}
       <ConfirmDialog
         open={confirmVerify}
         onOpenChange={setConfirmVerify}
-        title={
-          preview && hasPerSupplierMismatch(preview)
-            ? '強制反映 (force=true) を実衁E
-            : preview?.appliedWarning
-              ? '既に確定済�E月を再確宁E
-              : '買掛��一覧へ反映'
-        }
+        title={preview?.appliedWarning ? '既に確定済�E月を再確宁E : '買掛��一覧へ反映'}
         description={
-          preview && hasPerSupplierMismatch(preview)
-            ? `per-supplier 1 冁E��合性違反 ${preview.amountReconciliation!.perSupplierMismatches.length} 件を許容して反映します。違反詳細はサーバ�E側 audit log に記録されます。実行してよろしいですか�E�`
-            : preview?.appliedWarning
-              ? `こ�E月�E ${formatDateTime(preview.appliedWarning.appliedAt)} に既に確定済です。�E確定すると確定済�E値を上書きします（手動確宁Everified_manually=true 行�E保護されます）。続行しますか�E�`
-              : 'こ�E突合結果で買掛��一覧を検証確定します。よろしぁE��すか�E�！Eerified_manually=true として手動確定扱ぁE��なります！E
-        }
-        confirmLabel={
-          preview && hasPerSupplierMismatch(preview)
-            ? '強制反映する (force=true)'
-            : preview?.appliedWarning
-              ? '上書きして再確定すめE
-              : '反映する'
+          preview?.appliedWarning
+            ? `こ�E月�E ${formatDateTime(preview.appliedWarning.appliedAt)} に既に確定済です。�E確定すると確定済�E値を上書きします（手動確宁Everified_manually=true 行�E保護されます）。続行しますか�E�`
+            : 'こ�E突合結果で買掛��一覧を検証確定します。よろしぁE��すか�E�！Eerified_manually=true として手動確定扱ぁE��なります！E
         }
+        confirmLabel={preview?.appliedWarning ? '上書きして再確定すめE : '反映する'}
         onConfirm={() => {
           // ダイアログを即座に閉じることで二重起動を防止する�E�Eutation 完亁E��の連打対策）、E           setConfirmVerify(false)
           if (preview && !verifyMut.isPending) {
-            verifyMut.mutate({
-              uploadId: preview.uploadId,
-              force: hasPerSupplierMismatch(preview) && forceAcknowledged,
-            })
+            verifyMut.mutate({ uploadId: preview.uploadId, force: false })
           }
         }}
       />
+
+      {/*
+        G2-M2 (Frontend Major, 2026-05-06): 強制反映 (force=true) 専用ダイアログ、E+        100 件省略表示で承認�E容と実�E琁E��乖離するリスクを抑えるため、E+        - 全 mismatch CSV ダウンローチE+        - 全件確誁Echeckbox (reviewedAll)
+        - 反映琁E�� textarea (forceReason, audit log に記録予宁E
+        の3点を忁E��化する。`!reviewedAll || !forceReason.trim()` の間�E実行不可、E+      */}
+      <Dialog open={forceDialogOpen} onOpenChange={setForceDialogOpen}>
+        <DialogContent className="max-w-2xl">
+          <DialogHeader>
+            <DialogTitle>強制反映 (force=true) の確誁E/DialogTitle>
+            <DialogDescription>
+              per-supplier 1 冁E��合性違反を許容して買掛��一覧へ反映します、E+              全違反明細は <code>finance_audit_log.force_mismatch_details</code> (JSONB) に記録されます、E+            </DialogDescription>
+          </DialogHeader>
+          {preview && hasPerSupplierMismatch(preview) && (
+            <div className="space-y-4">
+              <Alert variant="destructive">
+                <AlertTriangle className="h-4 w-4" />
+                <AlertTitle>
+                  per-supplier 整合性違反 {preview.amountReconciliation!.perSupplierMismatches.length} 件
+                </AlertTitle>
+                <AlertDescription>
+                  <div className="mb-2 text-xs">
+                    1 冁E��位�E不一致があります。force=true で反映すると、誤った��額が
+                    買掛��一覧および MF CSV に反映される可能性があります、E+                    まぁECSV を�E件ダウンロードし、�E容を確認してください、E+                  </div>
+                  <Button
+                    type="button"
+                    variant="outline"
+                    size="sm"
+                    onClick={() => downloadMismatchesCsv(preview.amountReconciliation!.perSupplierMismatches)}
+                  >
+                    <FileDown className="mr-1 h-4 w-4" />
+                    全 {preview.amountReconciliation!.perSupplierMismatches.length} 件 CSV ダウンローチE+                  </Button>
+                </AlertDescription>
+              </Alert>
+
+              <div className="space-y-1">
+                <Label htmlFor="force-reason" className="text-xs">
+                  反映琁E�� (audit log に記録されまぁE <span className="text-red-600">*</span>
+                </Label>
+                <Textarea
+                  id="force-reason"
+                  value={forceReason}
+                  onChange={(e) => setForceReason(e.target.value)}
+                  placeholder="侁E 期末締め�Eため端数許容で反映 / supplier XX の手数料調整など"
+                  rows={3}
+                />
+              </div>
+
+              <div className="flex items-start gap-2 rounded border border-red-300 bg-red-50 p-2 text-xs">
+                <Checkbox
+                  id="reviewed-all"
+                  checked={reviewedAll}
+                  onCheckedChange={(v) => setReviewedAll(v === true)}
+                  className="mt-0.5"
+                />
+                <Label htmlFor="reviewed-all" className="cursor-pointer">
+                  全 {preview.amountReconciliation!.perSupplierMismatches.length} 件の違反冁E��を確認しました
+                  �E�ESV ダウンローチEor 上�Eリストで全件レビュー済！E+                </Label>
+              </div>
+            </div>
+          )}
+          <DialogFooter>
+            <Button variant="outline" onClick={() => setForceDialogOpen(false)}>
+              キャンセル
+            </Button>
+            <Button
+              variant="destructive"
+              disabled={!reviewedAll || !forceReason.trim() || verifyMut.isPending}
+              onClick={() => {
+                // 二重起動防止のため即座に閉じる、E+                setForceDialogOpen(false)
+                if (preview && hasPerSupplierMismatch(preview) && !verifyMut.isPending) {
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git diff 60d7d81''^ 60d7d81 -- frontend/components/pages/finance/payment-mf-import.tsx | Select-Object -Skip 220 -First 100' in C:\project\odamitsu-data-hub
 succeeded in 1535ms:
+                  verifyMut.mutate({
+                    uploadId: preview.uploadId,
+                    force: true,
+                    forceReason: forceReason.trim(),
+                  })
+                }
+              }}
+            >
+              蠑ｷ蛻ｶ蜿肴丐縺吶ｋ (force=true)
+            </Button>
+          </DialogFooter>
+        </DialogContent>
+      </Dialog>
     </div>
   )
 }
@@ -591,6 +715,30 @@ function hasPerSupplierMismatch(preview: PaymentMfPreviewResponse | null): boole
   return Array.isArray(mm) && mm.length > 0
 }
 
+/**
+ * G2-M2 (Frontend Major, 2026-05-06): per-supplier mismatch 繧・CSV 蛹悶＠縺ｦ繝繧ｦ繝ｳ繝ｭ繝ｼ繝峨☆繧九・+ *
+ * <p>UI 蛛ｴ縺ｯ諤ｧ閭ｽ邯ｭ謖√・縺溘ａ `slice(0, 100)` 縺ｧ陦ｨ遉ｺ繧堤怐逡･縺励※縺・ｋ縺後’orce 蜿肴丐蜑阪↓繝ｦ繝ｼ繧ｶ繝ｼ縺・+ * 蜈ｨ莉ｶ繧堤岼隕也｢ｺ隱阪〒縺阪ｋ邨瑚ｷｯ縺ｨ縺励※ CSV 繝繧ｦ繝ｳ繝ｭ繝ｼ繝峨ｒ謠蝉ｾ帙☆繧九・xcel 縺ｧ髢九＞縺ｦ讀懃ｴ｢/荳ｦ縺ｳ譖ｿ縺医′蜿ｯ閭ｽ縲・+ *
+ * <p>BOM (`・ｿ`) 繧貞・鬆ｭ縺ｫ莉倅ｸ弱＠縺ｦ Excel 縺ｧ縺ｮ譁・ｭ怜喧縺代ｒ蝗樣∩縲Ｅouble quote 縺ｯ `""` 縺ｧ escape縲・+ */
+function downloadMismatchesCsv(mismatches: string[]): void {
+  const header = 'no,detail'
+  const lines = mismatches.map((m, i) => `${i + 1},"${m.replace(/"/g, '""')}"`)
+  const csv = [header, ...lines].join('\r\n')
+  const blob = new Blob(['・ｿ' + csv], { type: 'text/csv;charset=utf-8' })
+  const url = URL.createObjectURL(blob)
+  const a = document.createElement('a')
+  a.href = url
+  const today = new Date().toISOString().slice(0, 10)
+  a.download = `payment-mf-mismatches-${today}.csv`
+  document.body.appendChild(a)
+  a.click()
+  document.body.removeChild(a)
+  setTimeout(() => URL.revokeObjectURL(url), 1000)
+}
+
 function blankRuleRequest(): PaymentMfRuleRequest {
   return {
     sourceName: '',
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git show --no-ext-diff --unified=80 60d7d81 -- backend/src/main/java/jp/co/oda32/dto/finance/paymentmf/PaymentMfApplyRequest.java backend/src/main/java/jp/co/oda32/constant/FinanceConstants.java' in C:\project\odamitsu-data-hub
 succeeded in 1230ms:
commit 60d7d815f07409beb1375fc08c96bd6dcfc8d13a
Author: kazuki <k_oda@odamitsu.com>
Date:   Thu May 7 09:18:25 2026 +0900

    fix(codex-r2): Codex 第 2 弾批判レビュー対応 (Critical + P1 + 9 Major + 1 Minor)
    
    3 commit (audit/mf/finance) を Codex 批判レビューにかけ、12 件の指摘を修正。
    
    ## Critical / P1 (即修正)
    * Critical: BULK 不変条件違反時の SUM フォールバックを fail-closed (例外) 化
      - PaymentMfImportService.sumVerifiedAmountForGroup で全行 BULK 不一致時に
        FinanceInternalException 422、過大計上の MF CSV 流出を遮断
    * P1: V033 再暗号化が片側カラムのみで refresh_token が旧鍵残るバグ修正
      - reencryptMfTokens() で access + refresh を 1 UPDATE で同時再暗号化
      - decrypt 失敗時 skip + WARN ログで idempotent 強化 (recipherOrSkip)
    
    ## Major (9 件)
    * MF P2: V037 ordering note は runbook §6' に集約 (V037 は revert で checksum 復元)
    * Audit P2: FinanceAuditAspect で Loader 解決失敗時の captureReturn/Args フォールバック追加
    * G2-M2 Major: verification_source 3 経路 (BULK/MANUAL/MF_OVERRIDE) を共通 advisory lock で直列化
      - FinancePayableLock util 切り出し、computePayableLockKey + acquire
      - applyVerification / verify / applyMfOverride で同一 lock キー
    * G2-M3 Major: force audit 全件保存 (V043 finance_audit_log.force_mismatch_details JSONB)
      - 50 件切り詰めの reason と全件 JSON を分離、UI 全件確認可能化への土台
    * G2-M4 Major: forceReason 必須化 + reason 空 → FORCE_REASON_REQUIRED 400
    * G2-M5 Major: 20日払い専用 Excel が PAYMENT_5TH 誤分類されるバグ修正
      - PaymentMfExcelParser.determineInitialSection で送金日 day による初期 section 判定
    * G2-M5 Major: OFFSET マスタ欠落時の hardcoded default fallback + 最後の active 行削除禁止
    * G2-M6 Major: P1-02 opening 注入空時の警告 (SupplierBalances/IntegrityReport DTO)
    * Frontend Major: force apply UX 強化 (CSV download, reviewedAll checkbox, forceReason 必須)
    
    ## Minor
    * G2-M? Minor: V044 で V040 backfill 検査 (NOTICE のみ、自動修復なし)
      - 実行結果: 560 行で history 突合なし = false positive (P1-08 導入前の歴史データ)
    
    ## 新規 migration
    - V043 finance_audit_log.force_mismatch_details JSONB + GIN index
    - V044 V040 backfill 検査 (RAISE NOTICE のみ)
    
    ## 新規 runbook
    - runbook-payment-mf-bulk-invariant-violation.md (Critical fix の運用手順)
    - runbook-payment-mf-force-apply.md (force=true の二段認可運用)
    
    ## CLAUDE.md
    - Review Guidelines セクション追加 (Repository Context / Review Priorities /
      Architecture Rules) — AI レビュー時の優先順位を明示
    
    ## テスト
    - 既存 233 + 新規 ~10 件 PASS、frontend tsc PASS、リグレッション 0
    - 5 group 並列修正で衝突なし
    
    Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>

diff --git a/backend/src/main/java/jp/co/oda32/constant/FinanceConstants.java b/backend/src/main/java/jp/co/oda32/constant/FinanceConstants.java
index 1b9d08f..3967642 100644
--- a/backend/src/main/java/jp/co/oda32/constant/FinanceConstants.java
+++ b/backend/src/main/java/jp/co/oda32/constant/FinanceConstants.java
@@ -25,119 +25,127 @@ public final class FinanceConstants {
      * 買掛金は shop_no=1 の仕入分のみを集計対象とする。shop_no=2(第2事業部)は
      * 会計系統が別で、買掛管理は本システム外（別運用）で行うため除外する。
      * 将来的にマルチテナントで買掛管理対象が増える場合は Set 型に拡張すること。
      */
     public static final int ACCOUNTS_PAYABLE_SHOP_NO = 1;
 
     /**
      * 第2事業部の月次集約仕入を表す商品コード。
      * <p>SMILE では第1事業部と第2事業部がシステム分離されており、第2事業部の
      * 個別仕入は別系統で第1事業部DBに取り込まれる。加えて運用上、各仕入先ごとに
      * 第2事業部分の「1ヶ月分合計」を本DBに手入力するケースがあり、その際に
      * 以下の商品コードが使われる:
      * <ul>
      *   <li>{@code 00000021} — 第2事業部 10%課税商品の月次集約</li>
      *   <li>{@code 00000023} — 第2事業部 8%課税商品の月次集約</li>
      * </ul>
      * これらは個別仕入と重複する（ダブルカウント）ため、買掛金集計・仕入集計の
      * 両方で除外する必要がある。
      */
     public static final Set<String> DIVISION2_AGGREGATE_GOODS_CODES =
             Set.of("00000021", "00000023");
 
     /**
      * 検証済みCSV出力プレビューで「5日払い相当 / 20日払い相当 Excel が未取込か」を
      * 判定する際の月内境界日 (exclusive)。翌月 {@code [1, CUTOFF)} を 5日払い相当、
      * {@code [CUTOFF, 月末]} を 20日払い相当とみなす。
      * <p>土日祝による振替で 5日 が 4日/6日/7日、20日 が 19日/21日 等に前後するため、
      * 単純に日付一致で判定せず「前半/後半」で包括的にカバーする。
      */
     public static final int PAYMENT_DATE_MIDMONTH_CUTOFF = 15;
 
     /**
      * 買掛金一覧における一括検証由来の備考接頭辞 (UI 表示用)。
      * <p>{@code PaymentMfImportService#applyVerification} が書き込む備考の先頭文字列。
      *
      * @deprecated G2-M10 (2026-05-06): 「BULK か MANUAL か」の判定には本接頭辞ではなく
      *     {@code t_accounts_payable_summary.verification_source} 列
      *     ({@link #VERIFICATION_SOURCE_BULK} / {@link #VERIFICATION_SOURCE_MANUAL}) を使うこと。
      *     ユーザが偶然この接頭辞で始まる note を手入力すると bulk と誤判定され、
      *     再 upload による上書き保護が外れるリスクがある (V040 で source 列を追加済)。
      *     本定数は note の <b>表示用接頭辞生成</b>のみに使い、判定には使用しないこと。
      */
     @Deprecated
     public static final String VERIFICATION_NOTE_BULK_PREFIX = "振込明細検証 ";
 
     // -------------------------------------------------------------
     // G2-M1 / G2-M10 (2026-05-06): 検証値の書込経路 enum (V040 で列追加)
     // -------------------------------------------------------------
 
     /**
      * 検証値 (verified_amount / verified_amount_tax_excluded) の書込経路: 振込明細 Excel 一括検証由来。
      * <p>{@code PaymentMfImportService#applyVerification} が書込時にセット。
      * 同 (shop, supplier, transactionMonth) の全税率行に同値の集約値が冗長保持される。
      * read 側 ({@code sumVerifiedAmountForGroup}) で「全行 BULK → 代表値 1 度」と扱う。
      */
     public static final String VERIFICATION_SOURCE_BULK = "BULK_VERIFICATION";
 
     /**
      * 検証値の書込経路: UI 手入力単一 PK 更新由来。
      * <p>{@code TAccountsPayableSummaryService#verify} が書込時にセット。
      * 単一 (shop, supplier, txMonth, taxRate) 行のみ更新するため、税率別に異なる値が入りうる。
      * read 側で SUM 集計する。1 行でも本値を含むグループは bulk 集約値とみなさない。
      */
     public static final String VERIFICATION_SOURCE_MANUAL = "MANUAL_VERIFICATION";
 
     /**
      * 検証値の書込経路: 整合性レポート MF override 由来 (税率別按分)。
      * <p>{@code ConsistencyReviewService#applyMfOverride} が書込時にセット。
      * 税率別 change 比で按分するため税率別に異なる値が入る。read 側で SUM 集計する。
      */
     public static final String VERIFICATION_SOURCE_MF_OVERRIDE = "MF_OVERRIDE";
 
     /**
      * G2-M2 (2026-05-06): 買掛仕入 MF 振込明細の per-supplier 1 円整合性違反を示すエラーコード。
      * <p>{@code FinanceBusinessException(message, code)} で投げ、
      * {@code FinanceExceptionHandler} がこのコードを検出して 422 + 業務メッセージで応答する。
      * <p>クライアント側はこのコードで「force=true で再実行」UI 分岐を表示する。
      */
     public static final String ERROR_CODE_PER_SUPPLIER_MISMATCH = "PER_SUPPLIER_MISMATCH";
 
+    /**
+     * Codex Major #4 (2026-05-06): {@code force=true} 指定時の業務理由が未指定であることを示すエラーコード。
+     * <p>強制反映は破壊的操作のため、{@code PaymentMfApplyRequest.forceReason} が
+     * 空文字 / null の場合は {@code FinanceBusinessException(code=FORCE_REASON_REQUIRED)} を投げて 400 拒否する。
+     * <p>クライアント側はこのコードを検出して「強制反映の理由を入力してください」ダイアログを表示する。
+     */
+    public static final String ERROR_CODE_FORCE_REASON_REQUIRED = "FORCE_REASON_REQUIRED";
+
     /**
      * 買掛金 vs SMILE 支払額の差額が「自動一致」とみなせる円未満の境界 (exclusive)。
      * <p>これより小さい絶対値の差額は、丸め誤差・手数料調整等の誤差範囲として
      * 「一致」判定し、SMILE 支払額に自動で合わせる。
      */
     public static final BigDecimal PAYMENT_VERIFICATION_TOLERANCE = new BigDecimal(5);
 
     /**
      * 買掛金照合レポートで「軽微な差額」として強調表示する境界 (inclusive)。
      * <p>買掛金一覧の手入力検証・一括検証の一致判定閾値もこの値と共通。値を変えると
      * Excel 一括検証 / 手入力 / レポートの判定がまとめて変わる点に注意。
      */
     public static final BigDecimal PAYMENT_REPORT_MINOR_DIFFERENCE = new BigDecimal(100);
 
     /** {@link #PAYMENT_REPORT_MINOR_DIFFERENCE} の long 版 (long 比較したい箇所向け)。 */
     public static final long PAYMENT_REPORT_MINOR_DIFFERENCE_LONG = PAYMENT_REPORT_MINOR_DIFFERENCE.longValueExact();
 
     /**
      * 買掛金照合レポートで「中程度の差額」として強調表示する境界 (inclusive)。
      */
     public static final BigDecimal PAYMENT_REPORT_MEDIUM_DIFFERENCE = new BigDecimal(1000);
 
     /**
      * 買掛 / 売掛 verified_amount 突合の許容差 (税込円)。
      * <p>設計書 D §3.6 / 整合性 §3.2 で定義。これより大きい絶対値の差額は「不一致」と判定し、
      * verification_result=0 とする。{@link #PAYMENT_REPORT_MINOR_DIFFERENCE} と同値だが
      * 用途 (照合判定 vs UI 強調表示) が異なるため別名で公開。
      */
     public static final BigDecimal MATCH_TOLERANCE = BigDecimal.valueOf(100);
 
     /**
      * 売掛金 / 請求書 (t_invoice) 突合の許容誤差 (税込円) のデフォルト値 (SF-E07)。
      * <p>{@code application.yml} の {@code batch.accounts-receivable.invoice-amount-tolerance} で
      * 個別環境では上書きでき、未指定時は本値 (3 円) が採用される
      * ({@code InvoiceVerifier#invoiceAmountTolerance} の {@code @Value} デフォルト参照先)。
      * <p>{@link #MATCH_TOLERANCE} (買掛 100 円) と用途・閾値が異なるため別名で公開。
      */
     public static final BigDecimal INVOICE_AMOUNT_TOLERANCE_DEFAULT = BigDecimal.valueOf(3);
 }
diff --git a/backend/src/main/java/jp/co/oda32/dto/finance/paymentmf/PaymentMfApplyRequest.java b/backend/src/main/java/jp/co/oda32/dto/finance/paymentmf/PaymentMfApplyRequest.java
index a793d96..ec00a2c 100644
--- a/backend/src/main/java/jp/co/oda32/dto/finance/paymentmf/PaymentMfApplyRequest.java
+++ b/backend/src/main/java/jp/co/oda32/dto/finance/paymentmf/PaymentMfApplyRequest.java
@@ -1,42 +1,55 @@
 package jp.co.oda32.dto.finance.paymentmf;
 
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
 import lombok.NoArgsConstructor;
 
 /**
  * G2-M2 (2026-05-06): 振込明細 Excel の検証反映 ({@code applyVerification}) リクエスト DTO。
  *
  * <p>従来 {@code POST /api/v1/finance/payment-mf/verify/{uploadId}} はボディ無しで呼べたが、
  * per-supplier 1 円整合性違反 ({@code perSupplierMismatches}) が出ていてもサーバー側で
  * ブロックしていなかったため、Excel 入力ミスがそのまま手動確定 + MF CSV に流れる事故が
  * あり得た。
  *
  * <p>本 DTO により以下の運用に変更:
  * <ul>
  *   <li>{@code force=false} (既定): per-supplier 1 円不一致が 1 件でもあれば 422 でブロック。
  *       UI 側は preview 画面で違反一覧を確認し、Excel を修正して再アップロードする。</li>
  *   <li>{@code force=true}: 違反を許容して反映。{@code finance_audit_log.reason} に
  *       {@code FORCE_APPLIED: per-supplier mismatches=...} を補足記録する。</li>
  * </ul>
  *
  * <p>ボディ省略 (旧 client) の場合は {@code force=false} 扱いとなる
  * ({@code @RequestBody(required=false)} で受ける)。
  */
 @Data
 @Builder
 @AllArgsConstructor
 @NoArgsConstructor
 public class PaymentMfApplyRequest {
 
     /**
      * per-supplier 1 円不一致を許容して反映するかどうか。
      * <ul>
      *   <li>{@code false} (既定): 違反 1 件以上で 422 ブロック</li>
      *   <li>{@code true}: 違反を許容して反映 + audit log に reason 記録</li>
      * </ul>
      */
     @Builder.Default
     private boolean force = false;
+
+    /**
+     * Codex Major #4 (2026-05-06): {@code force=true} 指定時の<b>必須</b>業務理由文字列。
+     * <p>強制反映は per-supplier 1 円整合性違反を許容する破壊的操作のため、
+     * audit log に「なぜ承認したか」を残す目的で必須化する。空文字 / null で
+     * {@code force=true} を送ると {@link jp.co.oda32.exception.FinanceBusinessException}
+     * (code=FORCE_REASON_REQUIRED) で 400 拒否される。
+     * <p>{@code force=false} の場合は無視される。
+     * <p>運用 runbook (= 二段認可の代替): 承認前に最低 2 名で内容確認のうえ、
+     * 担当者名 + 承認者名 + 業務上の理由をこの文字列に含める
+     * (例: {@code "承認: tanaka, 確認: yamada, 理由: 仕入先X側の請求書送付遅延、振込済"} )。
+     */
+    private String forceReason;
 }
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Path backend/src/main/java -Recurse -Filter '*FinanceExceptionHandler*' | Select-Object -ExpandProperty FullName" in C:\project\odamitsu-data-hub
 succeeded in 1650ms:
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -LiteralPath 'backend/src/main/java/jp/co/oda32/api/finance/FinanceExceptionHandler.java'" in C:\project\odamitsu-data-hub
 succeeded in 1605ms:
package jp.co.oda32.api.finance;

import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.service.finance.mf.MfReAuthRequiredException;
import jp.co.oda32.domain.service.finance.mf.MfScopeInsufficientException;
import jp.co.oda32.domain.service.finance.mf.MfTenantBindingFailedException;
import jp.co.oda32.domain.service.finance.mf.MfTenantMismatchException;
import jp.co.oda32.dto.finance.ErrorResponse;
import jp.co.oda32.exception.FinanceBusinessException;
import jp.co.oda32.exception.FinanceInternalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Finance パッケージ ({@code jp.co.oda32.api.finance}) 内の Controller でのみ有効な例外ハンドラ。
 * <p>
 * グローバルな {@link jp.co.oda32.exception.GlobalExceptionHandler} より優先して評価される
 * （Spring の {@code @ControllerAdvice} は basePackages 一致が優先されるため）。
 * <p>
 * 旧来 try/catch で各 endpoint が個別に組み立てていた MF 認証/権限/計算失敗系のレスポンスを集約する。
 *
 * @since 2026-05-04 (SF-25)
 */
@Slf4j
@RestControllerAdvice(basePackages = "jp.co.oda32.api.finance")
public class FinanceExceptionHandler {

    /** MF refresh_token 失効など、ユーザー再認可が必要な状態 → 401 */
    @ExceptionHandler(MfReAuthRequiredException.class)
    public ResponseEntity<ErrorResponse> handleMfReAuthRequired(MfReAuthRequiredException ex) {
        log.warn("MF re-auth required: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(ex.getMessage(), "MF_REAUTH_REQUIRED"));
    }

    /** MF API 認可スコープ不足 → 403 */
    @ExceptionHandler(MfScopeInsufficientException.class)
    public ResponseEntity<ErrorResponse> handleMfScopeInsufficient(MfScopeInsufficientException ex) {
        log.warn("MF scope insufficient (requiredScope={}): {}", ex.getRequiredScope(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.withScope(ex.getMessage(), "MF_SCOPE_INSUFFICIENT", ex.getRequiredScope()));
    }

    /**
     * MF tenant binding 不一致 (P1-01 / DD-F-04) → 409 CONFLICT。
     * <p>
     * 別会社 MF を誤って認可した場合などに発生する。再認可するだけでは復旧せず、
     * 旧連携を一旦「切断」してから再認可する必要があるため、{@link MfReAuthRequiredException}
     * (401) と区別して 409 を返す。client にはバインド済 tenant id / 観測 tenant id も
     * 含むメッセージを返す (運用者が状況把握できるよう)。
     */
    @ExceptionHandler(MfTenantMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMfTenantMismatch(MfTenantMismatchException ex) {
        log.error("MF tenant mismatch detected: bound={} observed={}",
                ex.getBoundTenantId(), ex.getObservedTenantId());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getMessage(), "MF_TENANT_MISMATCH"));
    }

    /**
     * MF tenant 強制バインド失敗 (G1-M3) → 503 SERVICE_UNAVAILABLE。
     * <p>
     * P1-01 導入前から認可済みの client (mf_tenant_id IS NULL) に対して
     * {@link jp.co.oda32.domain.service.finance.mf.MfOauthService#getValidAccessToken()}
     * が強制 binding を試行したが、{@code /v2/tenant} の fetch に失敗したケース。
     * MF API が一時的に応答しない / scope 不足等が想定要因のため、業務 API 自体は
     * 短時間で再試行可能 = 503 が適切 ({@link MfTenantMismatchException} の 409 と区別)。
     * client には汎用メッセージのみ返し、詳細は server log に記録する。
     */
    @ExceptionHandler(MfTenantBindingFailedException.class)
    public ResponseEntity<ErrorResponse> handleMfTenantBindingFailed(MfTenantBindingFailedException ex) {
        log.warn("MF tenant binding failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(
                        "MF テナント情報の取得に失敗しました。MF 連携設定を確認してください。",
                        "MF_TENANT_BINDING_FAILED"));
    }

    /**
     * 整合性レポート等、計算ロジック内部で発生した状態異常 → 422 (UNPROCESSABLE_ENTITY)。
     * <p>
     * 旧 endpoint との互換性 (status code drift 防止) のため 422 を返す。
     * 500 にすると client 側のエラーハンドリング (リトライ判定等) が変わってしまうため注意。
     * <p>
     * <strong>セキュリティ注意 (MA-01)</strong>: Finance パッケージ内 Service の {@link IllegalStateException}
     * メッセージには金額や CSV 行情報など内部詳細が含まれることがあるため、client には汎用メッセージのみ返却し、
     * 元のメッセージは log のみに記録する。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.warn("Finance IllegalStateException: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("内部エラーが発生しました", "INTERNAL_ERROR"));
    }

    /**
     * Finance 業務例外 → 400 Bad Request。
     * <p>
     * message はユーザー向けに作られているのでそのまま client に返す。
     * code は呼び出し元が指定していれば使い、未指定なら "BUSINESS_ERROR"。
     *
     * <p>G2-M2 (2026-05-06): {@link FinanceConstants#ERROR_CODE_PER_SUPPLIER_MISMATCH}
     * (買掛仕入 MF 振込明細の per-supplier 1 円不一致) は
     * 「クライアントが {@code force=true} を明示すれば突破できる業務不整合」のため、
     * 検証データの状態異常を示す 422 Unprocessable Entity にマップする。
     * その他の業務例外は従来通り 400 のまま。
     *
     * @since 2026-05-04 (T5)
     */
    @ExceptionHandler(FinanceBusinessException.class)
    public ResponseEntity<ErrorResponse> handleFinanceBusiness(FinanceBusinessException ex) {
        log.info("Finance business exception: code={}, message={}", ex.getErrorCode(), ex.getMessage());
        String code = ex.getErrorCode() != null ? ex.getErrorCode() : "BUSINESS_ERROR";
        HttpStatus status = FinanceConstants.ERROR_CODE_PER_SUPPLIER_MISMATCH.equals(code)
                ? HttpStatus.UNPROCESSABLE_ENTITY
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(ex.getMessage(), code));
    }

    /**
     * Finance 内部例外 → 422 Unprocessable Entity。
     * <p>
     * 機微情報を含む可能性があるため client には汎用化メッセージのみ返す。
     * 詳細メッセージはサーバーログのみ記録 (情報漏洩防止)。
     *
     * @since 2026-05-04 (T5)
     */
    @ExceptionHandler(FinanceInternalException.class)
    public ResponseEntity<ErrorResponse> handleFinanceInternal(FinanceInternalException ex) {
        log.warn("Finance internal exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("内部エラーが発生しました", "INTERNAL_ERROR"));
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n \"reencryptMfTokens\" \"backend\\src\\main\\java\\db\\migration\\V033__reencrypt_mf_oauth_secrets.java\"" in C:\project\odamitsu-data-hub
 succeeded in 1267ms:
40: * 旧鍵のまま残る致命バグがあった。修正: {@link #reencryptMfTokens} で両カラムを 1 つの UPDATE で
99:            int tokenCount = reencryptMfTokens(conn, oldCipher, newCipher, tokenHasVersion);
195:    static int reencryptMfTokens(Connection conn, TextEncryptor oldCipher, TextEncryptor newCipher,
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git show --no-ext-diff --unified=60 60d7d81 -- backend/src/main/java/jp/co/oda32/domain/service/finance/MOffsetJournalRuleService.java backend/src/main/java/jp/co/oda32/domain/repository/finance/MOffsetJournalRuleRepository.java' in C:\project\odamitsu-data-hub
 succeeded in 1263ms:
commit 60d7d815f07409beb1375fc08c96bd6dcfc8d13a
Author: kazuki <k_oda@odamitsu.com>
Date:   Thu May 7 09:18:25 2026 +0900

    fix(codex-r2): Codex 第 2 弾批判レビュー対応 (Critical + P1 + 9 Major + 1 Minor)
    
    3 commit (audit/mf/finance) を Codex 批判レビューにかけ、12 件の指摘を修正。
    
    ## Critical / P1 (即修正)
    * Critical: BULK 不変条件違反時の SUM フォールバックを fail-closed (例外) 化
      - PaymentMfImportService.sumVerifiedAmountForGroup で全行 BULK 不一致時に
        FinanceInternalException 422、過大計上の MF CSV 流出を遮断
    * P1: V033 再暗号化が片側カラムのみで refresh_token が旧鍵残るバグ修正
      - reencryptMfTokens() で access + refresh を 1 UPDATE で同時再暗号化
      - decrypt 失敗時 skip + WARN ログで idempotent 強化 (recipherOrSkip)
    
    ## Major (9 件)
    * MF P2: V037 ordering note は runbook §6' に集約 (V037 は revert で checksum 復元)
    * Audit P2: FinanceAuditAspect で Loader 解決失敗時の captureReturn/Args フォールバック追加
    * G2-M2 Major: verification_source 3 経路 (BULK/MANUAL/MF_OVERRIDE) を共通 advisory lock で直列化
      - FinancePayableLock util 切り出し、computePayableLockKey + acquire
      - applyVerification / verify / applyMfOverride で同一 lock キー
    * G2-M3 Major: force audit 全件保存 (V043 finance_audit_log.force_mismatch_details JSONB)
      - 50 件切り詰めの reason と全件 JSON を分離、UI 全件確認可能化への土台
    * G2-M4 Major: forceReason 必須化 + reason 空 → FORCE_REASON_REQUIRED 400
    * G2-M5 Major: 20日払い専用 Excel が PAYMENT_5TH 誤分類されるバグ修正
      - PaymentMfExcelParser.determineInitialSection で送金日 day による初期 section 判定
    * G2-M5 Major: OFFSET マスタ欠落時の hardcoded default fallback + 最後の active 行削除禁止
    * G2-M6 Major: P1-02 opening 注入空時の警告 (SupplierBalances/IntegrityReport DTO)
    * Frontend Major: force apply UX 強化 (CSV download, reviewedAll checkbox, forceReason 必須)
    
    ## Minor
    * G2-M? Minor: V044 で V040 backfill 検査 (NOTICE のみ、自動修復なし)
      - 実行結果: 560 行で history 突合なし = false positive (P1-08 導入前の歴史データ)
    
    ## 新規 migration
    - V043 finance_audit_log.force_mismatch_details JSONB + GIN index
    - V044 V040 backfill 検査 (RAISE NOTICE のみ)
    
    ## 新規 runbook
    - runbook-payment-mf-bulk-invariant-violation.md (Critical fix の運用手順)
    - runbook-payment-mf-force-apply.md (force=true の二段認可運用)
    
    ## CLAUDE.md
    - Review Guidelines セクション追加 (Repository Context / Review Priorities /
      Architecture Rules) — AI レビュー時の優先順位を明示
    
    ## テスト
    - 既存 233 + 新規 ~10 件 PASS、frontend tsc PASS、リグレッション 0
    - 5 group 並列修正で衝突なし
    
    Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>

diff --git a/backend/src/main/java/jp/co/oda32/domain/repository/finance/MOffsetJournalRuleRepository.java b/backend/src/main/java/jp/co/oda32/domain/repository/finance/MOffsetJournalRuleRepository.java
index ada145e..0ff92c5 100644
--- a/backend/src/main/java/jp/co/oda32/domain/repository/finance/MOffsetJournalRuleRepository.java
+++ b/backend/src/main/java/jp/co/oda32/domain/repository/finance/MOffsetJournalRuleRepository.java
@@ -1,24 +1,30 @@
 package jp.co.oda32.domain.repository.finance;
 
 import jp.co.oda32.domain.model.finance.MOffsetJournalRule;
 import org.springframework.data.jpa.repository.JpaRepository;
 import org.springframework.stereotype.Repository;
 
 import java.util.List;
 import java.util.Optional;
 
 /**
  * G2-M8: {@link MOffsetJournalRule} の Repository。
  *
  * <p>shop_no + del_flg='0' の UNIQUE 制約があるため、active 行は最大 1 件。
  * lookup は {@link #findByShopNoAndDelFlg(Integer, String)} を使う。
  */
 @Repository
 public interface MOffsetJournalRuleRepository extends JpaRepository<MOffsetJournalRule, Integer> {
 
     /** shop_no + del_flg で active 行 (高々 1 件) を取得。 */
     Optional<MOffsetJournalRule> findByShopNoAndDelFlg(Integer shopNo, String delFlg);
 
     /** Admin 一覧画面用: del_flg='0' を shop_no 昇順で全件取得。 */
     List<MOffsetJournalRule> findByDelFlgOrderByShopNoAsc(String delFlg);
+
+    /**
+     * Codex Major fix: shop_no 別 active 行件数。
+     * delete() での「最後の active 行は削除禁止」バリデーションに使用する。
+     */
+    long countByShopNoAndDelFlg(Integer shopNo, String delFlg);
 }
diff --git a/backend/src/main/java/jp/co/oda32/domain/service/finance/MOffsetJournalRuleService.java b/backend/src/main/java/jp/co/oda32/domain/service/finance/MOffsetJournalRuleService.java
index ec986e7..b259f8a 100644
--- a/backend/src/main/java/jp/co/oda32/domain/service/finance/MOffsetJournalRuleService.java
+++ b/backend/src/main/java/jp/co/oda32/domain/service/finance/MOffsetJournalRuleService.java
@@ -1,106 +1,121 @@
 package jp.co.oda32.domain.service.finance;
 
 import jp.co.oda32.audit.AuditLog;
 import jp.co.oda32.domain.model.finance.MOffsetJournalRule;
 import jp.co.oda32.domain.repository.finance.MOffsetJournalRuleRepository;
 import jp.co.oda32.domain.service.util.LoginUserUtil;
 import jp.co.oda32.dto.finance.OffsetJournalRuleRequest;
+import jp.co.oda32.exception.FinanceBusinessException;
 import lombok.RequiredArgsConstructor;
 import org.springframework.stereotype.Service;
 import org.springframework.transaction.annotation.Transactional;
 
 import java.time.LocalDateTime;
 import java.util.List;
 
 /**
  * G2-M8: PaymentMfImport の OFFSET 副行貸方科目マスタ ({@link MOffsetJournalRule}) を管理する Service。
  *
  * <p>shop_no + del_flg='0' で UNIQUE のため active 行は shop あたり最大 1 件。
  * Admin UI から create / update / delete を行い、税理士確認結果を再 deploy なしで反映する。
  *
  * <p>{@link AuditLog} で T2 監査証跡対象。
  */
 @Service
 @RequiredArgsConstructor
 @Transactional(readOnly = true)
 public class MOffsetJournalRuleService {
 
     private final MOffsetJournalRuleRepository repository;
 
     private Integer currentUserNo() {
         try {
             return LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
         } catch (Exception e) {
             return null;
         }
     }
 
     public List<MOffsetJournalRule> findAll() {
         return repository.findByDelFlgOrderByShopNoAsc("0");
     }
 
     public MOffsetJournalRule findByShopNo(Integer shopNo) {
         return repository.findByShopNoAndDelFlg(shopNo, "0").orElse(null);
     }
 
     @Transactional
     @AuditLog(table = "m_offset_journal_rule", operation = "INSERT",
             pkExpression = "{'shopNo': #a0.shopNo}",
             captureArgsAsAfter = true)
     public MOffsetJournalRule create(OffsetJournalRuleRequest req) {
         Integer userNo = currentUserNo();
         LocalDateTime now = LocalDateTime.now();
         MOffsetJournalRule e = MOffsetJournalRule.builder()
                 .shopNo(req.getShopNo())
                 .creditAccount(req.getCreditAccount())
                 .creditSubAccount(emptyToNull(req.getCreditSubAccount()))
                 .creditDepartment(emptyToNull(req.getCreditDepartment()))
                 .creditTaxCategory(req.getCreditTaxCategory())
                 .summaryPrefix(req.getSummaryPrefix() == null || req.getSummaryPrefix().isEmpty()
                         ? "相殺／" : req.getSummaryPrefix())
                 .delFlg("0")
                 .addDateTime(now)
                 .addUserNo(userNo)
                 .modifyDateTime(now)
                 .modifyUserNo(userNo)
                 .build();
         return repository.save(e);
     }
 
     @Transactional
     @AuditLog(table = "m_offset_journal_rule", operation = "UPDATE",
             pkExpression = "{'id': #a0}",
             captureArgsAsAfter = true)
     public MOffsetJournalRule update(Integer id, OffsetJournalRuleRequest req) {
         MOffsetJournalRule e = repository.findById(id)
                 .orElseThrow(() -> new IllegalArgumentException("OFFSET 仕訳マスタが見つかりません: id=" + id));
         e.setShopNo(req.getShopNo());
         e.setCreditAccount(req.getCreditAccount());
         e.setCreditSubAccount(emptyToNull(req.getCreditSubAccount()));
         e.setCreditDepartment(emptyToNull(req.getCreditDepartment()));
         e.setCreditTaxCategory(req.getCreditTaxCategory());
         if (req.getSummaryPrefix() != null && !req.getSummaryPrefix().isEmpty()) {
             e.setSummaryPrefix(req.getSummaryPrefix());
         }
         e.setModifyDateTime(LocalDateTime.now());
         e.setModifyUserNo(currentUserNo());
         return repository.save(e);
     }
 
     @Transactional
     @AuditLog(table = "m_offset_journal_rule", operation = "DELETE",
             pkExpression = "{'id': #a0}",
             captureArgsAsAfter = true)
     public void delete(Integer id) {
         MOffsetJournalRule e = repository.findById(id)
                 .orElseThrow(() -> new IllegalArgumentException("OFFSET 仕訳マスタが見つかりません: id=" + id));
+        // Codex Major fix: 二段防御の (b) 削除制約。
+        // shop_no 単位で最後の active 行を削除すると PaymentMfImportService の OFFSET 副行生成で
+        // hardcoded default fallback に強制される (= 業務的に「税理士確認反映なし」状態に逆戻り)。
+        // active 行が消えるリスクを admin UI 側で防ぐ。
+        // 既に del_flg='1' の zombie 行を再削除した場合はスキップ (idempotent 動作維持)。
+        if ("0".equals(e.getDelFlg())) {
+            long activeCount = repository.countByShopNoAndDelFlg(e.getShopNo(), "0");
+            if (activeCount <= 1L) {
+                throw new FinanceBusinessException(
+                        "shop_no=" + e.getShopNo() + " の最後の active 行は削除できません。"
+                                + "別の active 行を作成してから削除してください。",
+                        "OFFSET_RULE_LAST_ACTIVE");
+            }
+        }
         e.setDelFlg("1");
         e.setModifyDateTime(LocalDateTime.now());
         e.setModifyUserNo(currentUserNo());
         repository.save(e);
     }
 
     private static String emptyToNull(String s) {
         return (s == null || s.isEmpty()) ? null : s;
     }
 }
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -LiteralPath 'backend/src/main/resources/db/migration/V044__verify_v040_backfill.sql'" in C:\project\odamitsu-data-hub
 succeeded in 1508ms:
-- Codex Major fix (2026-05-06): V040 backfill 検査用診断 SQL。
--
-- 背景:
--   V040 で verification_source 列を導入した際、過去の verified_amount/verified_manually の
--   書込経路を推定するため verification_note の文字列接頭辞 "振込明細検証 %" で
--   BULK_VERIFICATION に backfill した。
--   しかし note は手入力可能なため、ユーザーが偶然 "振込明細検証 ..." で始まる note を
--   入力していた行は MANUAL_VERIFICATION を期待していたのに BULK_VERIFICATION に
--   誤判定される可能性がある。
--
-- 対応方針:
--   本 migration は **検査のみ** を実施し、自動修復は行わない。
--   note 接頭辞で BULK_VERIFICATION 判定された行のうち、
--   t_payment_mf_import_history (applied_at != NULL) に対応する一括検証実行記録が
--   存在しない (= 突合が取れない) 行を NOTICE で報告する。
--
--   突合キーの考え方:
--     t_accounts_payable_summary.transaction_month と
--     t_payment_mf_import_history.transfer_date は
--     PaymentMfImportService.deriveTransactionMonth により
--     transaction_month = transfer_date - 1 month (day=20) の関係。
--     一致する shop_no + (transfer_date - 1 month, day=20) の applied 履歴があれば
--     正規 BULK_VERIFICATION とみなす。
--
-- 検査結果:
--   - suspect_count = 0: 全 BULK 行が一括検証履歴と整合 (V040 backfill OK)
--   - suspect_count > 0: 業務担当者の手動確認が必要 (= note 接頭辞偽装の疑い)
--     自動修復は業務判断 (verified_amount の値取扱いが BULK と MANUAL で異なる) を
--     伴うため運用側で個別確認 → UPDATE する。
--
-- 関連:
--   - V040__add_verification_source.sql (backfill 本体)
--   - claudedocs/design-payment-mf-import.md §5.6 (verification_source 設計)

DO $$
DECLARE
    suspect_count   INTEGER;
    bulk_total      INTEGER;
BEGIN
    SELECT COUNT(*) INTO bulk_total
    FROM t_accounts_payable_summary
    WHERE verification_source = 'BULK_VERIFICATION'
      AND verification_note LIKE '振込明細検証 %';

    SELECT COUNT(*) INTO suspect_count
    FROM t_accounts_payable_summary aps
    WHERE aps.verification_source = 'BULK_VERIFICATION'
      AND aps.verification_note LIKE '振込明細検証 %'
      AND NOT EXISTS (
          SELECT 1
          FROM t_payment_mf_import_history hist
          WHERE hist.shop_no = aps.shop_no
            AND hist.applied_at IS NOT NULL
            AND hist.del_flg = '0'
            -- transaction_month = transfer_date - 1 month (day=20)
            AND aps.transaction_month = (date_trunc('month', hist.transfer_date) - INTERVAL '1 month' + INTERVAL '19 days')::date
      );

    IF suspect_count > 0 THEN
        RAISE NOTICE 'V044: V040 backfill 検査 NG: BULK_VERIFICATION % 行のうち % 行に一括検証履歴 (applied_at != NULL) なし。手動確認推奨。',
            bulk_total, suspect_count;
        RAISE NOTICE 'V044: 確認 SQL: SELECT shop_no, supplier_no, transaction_month, tax_rate, verification_note FROM t_accounts_payable_summary aps WHERE verification_source = ''BULK_VERIFICATION'' AND verification_note LIKE ''振込明細検証 %%'' AND NOT EXISTS (SELECT 1 FROM t_payment_mf_import_history h WHERE h.shop_no = aps.shop_no AND h.applied_at IS NOT NULL AND h.del_flg = ''0'' AND aps.transaction_month = (date_trunc(''month'', h.transfer_date) - INTERVAL ''1 month'' + INTERVAL ''19 days'')::date) ORDER BY shop_no, supplier_no, transaction_month;';
        RAISE NOTICE 'V044: 修復は業務判断 (BULK / MANUAL で verified_amount 取扱いが異なる) のため自動 UPDATE せず。';
    ELSE
        RAISE NOTICE 'V044: V040 backfill 検査 OK: BULK_VERIFICATION % 行は全て一括検証履歴 (applied_at != NULL) と整合。',
            bulk_total;
    END IF;
END $$;
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git show --no-ext-diff --unified=50 60d7d81 -- backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java backend/src/main/java/jp/co/oda32/dto/finance/SupplierBalancesResponse.java backend/src/main/java/jp/co/oda32/dto/finance/IntegrityReportResponse.java backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java' in C:\project\odamitsu-data-hub
 succeeded in 1267ms:
commit 60d7d815f07409beb1375fc08c96bd6dcfc8d13a
Author: kazuki <k_oda@odamitsu.com>
Date:   Thu May 7 09:18:25 2026 +0900

    fix(codex-r2): Codex 第 2 弾批判レビュー対応 (Critical + P1 + 9 Major + 1 Minor)
    
    3 commit (audit/mf/finance) を Codex 批判レビューにかけ、12 件の指摘を修正。
    
    ## Critical / P1 (即修正)
    * Critical: BULK 不変条件違反時の SUM フォールバックを fail-closed (例外) 化
      - PaymentMfImportService.sumVerifiedAmountForGroup で全行 BULK 不一致時に
        FinanceInternalException 422、過大計上の MF CSV 流出を遮断
    * P1: V033 再暗号化が片側カラムのみで refresh_token が旧鍵残るバグ修正
      - reencryptMfTokens() で access + refresh を 1 UPDATE で同時再暗号化
      - decrypt 失敗時 skip + WARN ログで idempotent 強化 (recipherOrSkip)
    
    ## Major (9 件)
    * MF P2: V037 ordering note は runbook §6' に集約 (V037 は revert で checksum 復元)
    * Audit P2: FinanceAuditAspect で Loader 解決失敗時の captureReturn/Args フォールバック追加
    * G2-M2 Major: verification_source 3 経路 (BULK/MANUAL/MF_OVERRIDE) を共通 advisory lock で直列化
      - FinancePayableLock util 切り出し、computePayableLockKey + acquire
      - applyVerification / verify / applyMfOverride で同一 lock キー
    * G2-M3 Major: force audit 全件保存 (V043 finance_audit_log.force_mismatch_details JSONB)
      - 50 件切り詰めの reason と全件 JSON を分離、UI 全件確認可能化への土台
    * G2-M4 Major: forceReason 必須化 + reason 空 → FORCE_REASON_REQUIRED 400
    * G2-M5 Major: 20日払い専用 Excel が PAYMENT_5TH 誤分類されるバグ修正
      - PaymentMfExcelParser.determineInitialSection で送金日 day による初期 section 判定
    * G2-M5 Major: OFFSET マスタ欠落時の hardcoded default fallback + 最後の active 行削除禁止
    * G2-M6 Major: P1-02 opening 注入空時の警告 (SupplierBalances/IntegrityReport DTO)
    * Frontend Major: force apply UX 強化 (CSV download, reviewedAll checkbox, forceReason 必須)
    
    ## Minor
    * G2-M? Minor: V044 で V040 backfill 検査 (NOTICE のみ、自動修復なし)
      - 実行結果: 560 行で history 突合なし = false positive (P1-08 導入前の歴史データ)
    
    ## 新規 migration
    - V043 finance_audit_log.force_mismatch_details JSONB + GIN index
    - V044 V040 backfill 検査 (RAISE NOTICE のみ)
    
    ## 新規 runbook
    - runbook-payment-mf-bulk-invariant-violation.md (Critical fix の運用手順)
    - runbook-payment-mf-force-apply.md (force=true の二段認可運用)
    
    ## CLAUDE.md
    - Review Guidelines セクション追加 (Repository Context / Review Priorities /
      Architecture Rules) — AI レビュー時の優先順位を明示
    
    ## テスト
    - 既存 233 + 新規 ~10 件 PASS、frontend tsc PASS、リグレッション 0
    - 5 group 並列修正で衝突なし
    
    Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>

diff --git a/backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java b/backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java
index f025340..9ea1779 100644
--- a/backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java
+++ b/backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java
@@ -411,112 +411,130 @@ public class AccountsPayableIntegrityService {
 
         // ---- 差分確認履歴 (案 X+Y) を各 entry に付与。snapshotStale は ±¥100 閾値で判定 ----
         Map<ConsistencyReviewService.ReviewKey, ConsistencyReviewService.ReviewInfo> reviewMap =
                 consistencyReviewService.findForPeriod(shopNo, fromMonth, toMonth);
         attachReviewToMfOnly(mfOnly, reviewMap);
         attachReviewToSelfOnly(selfOnly, reviewMap);
         attachReviewToAmountMismatch(amountMismatch, reviewMap);
 
         // ---- 日付 (transactionMonth) 昇順ソート、同月内は supplier/subAccount 名で安定化 ----
         mfOnly.sort(java.util.Comparator
                 .comparing(MfOnlyEntry::getTransactionMonth)
                 .thenComparing(e -> e.getSubAccountName() == null ? "" : e.getSubAccountName()));
         selfOnly.sort(java.util.Comparator
                 .comparing(SelfOnlyEntry::getTransactionMonth)
                 .thenComparing(e -> e.getSupplierNo() == null ? 0 : e.getSupplierNo()));
         amountMismatch.sort(java.util.Comparator
                 .comparing(AmountMismatchEntry::getTransactionMonth)
                 .thenComparing(e -> e.getSupplierNo() == null ? 0 : e.getSupplierNo()));
         final int finalReconciledCount = reconciledCount;
 
         // ---- Summary 集計 ----
         BigDecimal totalMfOnly = mfOnly.stream()
                 .map(MfOnlyEntry::getPeriodDelta)
                 .map(BigDecimal::abs)
                 .reduce(BigDecimal.ZERO, BigDecimal::add);
         BigDecimal totalSelfOnly = selfOnly.stream()
                 .map(SelfOnlyEntry::getSelfDelta)
                 .map(BigDecimal::abs)
                 .reduce(BigDecimal.ZERO, BigDecimal::add);
         BigDecimal totalMismatch = amountMismatch.stream()
                 .map(AmountMismatchEntry::getDiff)
                 .map(BigDecimal::abs)
                 .reduce(BigDecimal.ZERO, BigDecimal::add);
 
         Set<Integer> supplierUnion = new HashSet<>(selfSupplierNos);
         for (MfOnlyEntry en : mfOnly) {
             if (en.getGuessedSupplierNo() != null) supplierUnion.add(en.getGuessedSupplierNo());
         }
 
         Summary summary = Summary.builder()
                 .mfOnlyCount(mfOnly.size())
                 .selfOnlyCount(selfOnly.size())
                 .amountMismatchCount(amountMismatch.size())
                 .unmatchedSupplierCount(unmatchedSuppliers.size())
                 .reconciledAtPeriodEndCount(finalReconciledCount)
                 .totalMfOnlyAmount(totalMfOnly)
                 .totalSelfOnlyAmount(totalSelfOnly)
                 .totalMismatchAmount(totalMismatch)
                 .build();
 
+        // Codex Major fix (P1-02): 整合性レポートの supplierCumulativeDiff は
+        // SupplierBalancesService と共有の opening balance を使用するため、
+        // m_supplier_opening_balance が空だと累積差分が silent に誤値になる。
+        // 累積残一覧と同じ警告を整合性レポート側にも露出する。
+        boolean openingMissing = !openingBalanceService.isOpeningBalanceLoaded(
+                shopNo, MfPeriodConstants.SELF_BACKFILL_START);
+        String openingWarning = openingMissing
+                ? "MF 期首残 (m_supplier_opening_balance) が未投入です。初回運用では"
+                        + " /finance/supplier-opening-balance/mf-fetch を先に実行してください。"
+                        + "現状の累積差は期首残 0 で計算されているため、reconciledAtPeriodEnd"
+                        + " 判定および supplierCumulativeDiff は信頼できません。"
+                : null;
+        if (openingMissing) {
+            log.warn("[integrity] shopNo={} opening 未投入: cumulativeDiff/reconciledAtPeriodEnd は期首残 0 で計算されました", shopNo);
+        }
+
         return IntegrityReportResponse.builder()
                 .shopNo(shopNo)
                 .fromMonth(fromMonth)
                 .toMonth(toMonth)
                 .fetchedAt(fetchedAt != null ? fetchedAt : Instant.now())
                 .totalJournalCount(allJournals.size())
                 .supplierCount(supplierUnion.size())
                 .mfOnly(mfOnly)
                 .selfOnly(selfOnly)
                 .amountMismatch(amountMismatch)
                 .unmatchedSuppliers(unmatchedSuppliers)
                 .summary(summary)
+                .openingBalanceMissing(openingMissing)
+                .openingBalanceWarning(openingWarning)
                 .build();
     }
 
     private static BigDecimal nz(BigDecimal v) {
         return v == null ? BigDecimal.ZERO : v;
     }
 
     // ==================== 差分確認履歴の付与 (案 X+Y) ====================
 
     private void attachReviewToMfOnly(List<MfOnlyEntry> list,
             Map<ConsistencyReviewService.ReviewKey, ConsistencyReviewService.ReviewInfo> reviewMap) {
         for (MfOnlyEntry e : list) {
             String key = e.getGuessedSupplierNo() != null
                     ? e.getGuessedSupplierNo().toString()
                     : e.getSubAccountName();
             var info = reviewMap.get(new ConsistencyReviewService.ReviewKey("mfOnly", key, e.getTransactionMonth()));
             if (info != null) {
                 e.setReviewStatus(info.reviewStatus());
                 e.setReviewedAt(info.reviewedAt());
                 e.setReviewedByName(info.reviewedByName());
                 e.setReviewNote(info.note());
                 BigDecimal currentMf = nz(e.getPeriodDelta());
                 BigDecimal diff = currentMf.subtract(nz(info.mfSnapshot())).abs();
                 e.setSnapshotStale(diff.compareTo(FinanceConstants.MATCH_TOLERANCE) > 0);
             } else {
                 e.setSnapshotStale(false);
             }
         }
     }
 
     private void attachReviewToSelfOnly(List<SelfOnlyEntry> list,
             Map<ConsistencyReviewService.ReviewKey, ConsistencyReviewService.ReviewInfo> reviewMap) {
         for (SelfOnlyEntry e : list) {
             if (e.getSupplierNo() == null) continue;
             var info = reviewMap.get(new ConsistencyReviewService.ReviewKey(
                     "selfOnly", e.getSupplierNo().toString(), e.getTransactionMonth()));
             if (info != null) {
                 e.setReviewStatus(info.reviewStatus());
                 e.setReviewedAt(info.reviewedAt());
                 e.setReviewedByName(info.reviewedByName());
                 e.setReviewNote(info.note());
                 BigDecimal diff = nz(e.getSelfDelta()).subtract(nz(info.selfSnapshot())).abs();
                 e.setSnapshotStale(diff.compareTo(FinanceConstants.MATCH_TOLERANCE) > 0);
             } else {
                 e.setSnapshotStale(false);
             }
         }
     }
 
     private void attachReviewToAmountMismatch(List<AmountMismatchEntry> list,
diff --git a/backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java b/backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java
index e5b445d..29c2f5d 100644
--- a/backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java
+++ b/backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java
@@ -157,108 +157,125 @@ public class SupplierBalancesService {
             Set<String> codes = mfSubToCodes.getOrDefault(subName, Set.of());
             Integer guessedNo = null;
             String guessedCode = null;
             String guessedName = subName;
             for (String code : codes) {
                 MPaymentSupplier s = supplierByCode.get(code);
                 if (s != null) {
                     guessedNo = s.getPaymentSupplierNo();
                     guessedCode = code;
                     guessedName = s.getPaymentSupplierName();
                     break;
                 }
             }
             // guessedNo が解決されていれば opening をここでも注入 (self 未登録だが期首残を持つ supplier)
             BigDecimal opening = guessedNo == null ? BigDecimal.ZERO : nz(openingMap.remove(guessedNo));
             rows.add(SupplierBalanceRow.builder()
                     .supplierNo(guessedNo)
                     .supplierCode(guessedCode)
                     .supplierName(guessedName)
                     .selfBalance(opening)
                     .mfBalance(mf.credit.subtract(mf.debit))
                     .diff(opening.subtract(mf.credit.subtract(mf.debit)))
                     .status("SELF_MISSING")
                     .masterRegistered(!codes.isEmpty())
                     .selfOpening(opening)
                     .selfChangeCumulative(BigDecimal.ZERO)
                     .selfPaymentCumulative(BigDecimal.ZERO)
                     .mfCreditCumulative(mf.credit)
                     .mfDebitCumulative(mf.debit)
                     .mfSubAccountNames(List.of(subName))
                     .build());
         }
 
         // 3) opening のみある supplier (self 未登録 かつ MF 側 activity なし) を残り処理
         for (Map.Entry<Integer, BigDecimal> e : openingMap.entrySet()) {
             Integer supplierNo = e.getKey();
             BigDecimal opening = e.getValue();
             if (opening.signum() == 0) continue;
             MPaymentSupplier sup = supplierByNo.get(supplierNo);
             if (sup == null) continue;
             SelfAccum self = new SelfAccum();
             self.opening = opening;
             self.closing = opening;
             Set<String> matchedSubs = resolveMatchedSubs(mfSubToCodes, sup.getPaymentSupplierCode(), mfBySub.keySet());
             rows.add(buildRow(supplierNo, sup, self, new MfAccum(), matchedSubs, !matchedSubs.isEmpty()));
         }
 
         rows.sort((a, b) -> b.getDiff().abs().compareTo(a.getDiff().abs()));
         Summary summary = buildSummary(rows);
 
+        // Codex Major fix (P1-02): m_supplier_opening_balance が空のときは silent に
+        // 累積残が誤値 (期首残 0) になるため、明示警告を response に含める。
+        // 行数判定 (isOpeningBalanceLoaded) は signum 関係なく行の有無で判定し、
+        // 「未投入」と「全 0 で投入済」を区別する。
+        boolean openingMissing = !openingBalanceService.isOpeningBalanceLoaded(
+                shopNo, MfPeriodConstants.SELF_BACKFILL_START);
+        String openingWarning = openingMissing
+                ? "MF 期首残 (m_supplier_opening_balance) が未投入です。初回運用では"
+                        + " /finance/supplier-opening-balance/mf-fetch を先に実行してください。"
+                        + "現状の累積残は期首残 0 で計算されているため、累積差は信頼できません。"
+                : null;
+        if (openingMissing) {
+            log.warn("[supplier-balances] shopNo={} opening 未投入: 累積残は期首残 0 で計算されました", shopNo);
+        }
+
         return SupplierBalancesResponse.builder()
                 .shopNo(shopNo)
                 .asOfMonth(resolvedMonth)
                 .mfStartDate(MfPeriodConstants.MF_JOURNALS_FETCH_FROM)
                 .fetchedAt(fetchedAt != null ? fetchedAt : Instant.now())
                 .totalJournalCount(allJournals.size())
                 .rows(rows)
                 .summary(summary)
+                .openingBalanceMissing(openingMissing)
+                .openingBalanceWarning(openingWarning)
                 .build();
     }
 
     private Map<String, Set<String>> buildMfSubToCodes() {
         List<MfAccountMaster> all = mfAccountMasterRepository.findAll();
         Map<String, Set<String>> m = new HashMap<>();
         for (MfAccountMaster r : all) {
             if (!MF_ACCOUNT_PAYABLE.equals(r.getAccountName())) continue;
             if (!MF_ACCOUNT_PAYABLE.equals(r.getFinancialStatementItem())) continue;
             if (r.getSubAccountName() == null || r.getSubAccountName().isBlank()) continue;
             if (r.getSearchKey() == null || r.getSearchKey().isBlank()) continue;
             m.computeIfAbsent(r.getSubAccountName(), k -> new HashSet<>()).add(r.getSearchKey());
         }
         return m;
     }
 
     private Map<String, MfAccum> accumulateMfJournals(List<MfJournal> journals, LocalDate asOfMonth) {
         // SF-G01: 期首残高仕訳 (journal #1) は m_supplier_opening_balance 経由で別途注入するため
         // ここでも除外する。これにより MfSupplierLedgerService / AccountsPayableIntegrityService /
         // SupplierBalancesService の 3 サービスで journal #1 除外を統一し、二重計上を防止する。
         // opening は SupplierBalancesService.generate() の openingMap (effectiveBalanceMap) で
         // self.opening + buildRow への注入として加算される。
         Map<String, MfAccum> map = new TreeMap<>();
         for (MfJournal j : journals) {
             if (j.transactionDate() == null || j.branches() == null) continue;
             if (MfJournalFetcher.isPayableOpeningJournal(j)) continue; // 期首残高仕訳は除外 (前期繰越として別管理)
             LocalDate monthKey = MfJournalFetcher.toClosingMonthDay20(j.transactionDate());
             if (monthKey.isAfter(asOfMonth) || monthKey.isBefore(MfPeriodConstants.MF_JOURNALS_FETCH_FROM)) continue;
             for (MfJournal.MfBranch br : j.branches()) {
                 var cr = br.creditor();
                 var de = br.debitor();
                 if (cr != null && MF_ACCOUNT_PAYABLE.equals(cr.accountName())
                         && cr.subAccountName() != null) {
                     MfAccum accum = map.computeIfAbsent(cr.subAccountName(), k -> new MfAccum());
                     accum.credit = accum.credit.add(nz(cr.value()));
                 }
                 if (de != null && MF_ACCOUNT_PAYABLE.equals(de.accountName())
                         && de.subAccountName() != null) {
                     MfAccum accum = map.computeIfAbsent(de.subAccountName(), k -> new MfAccum());
                     accum.debit = accum.debit.add(nz(de.value()));
                 }
             }
         }
         return map;
     }
 
     private SelfAccum accumulateSelf(List<TAccountsPayableSummary> rows, LocalDate asOfMonth) {
         // CR-G01: opening の取得先は m_supplier_opening_balance.effectiveBalance に統一。
         // 旧実装は transactionMonth == MF_JOURNALS_FETCH_FROM (=2025-05-20) 行から
         // opening_balance_tax_included を累積していたが、generate() 側の openingMap 注入と
@@ -323,83 +340,92 @@ public class SupplierBalancesService {
                 .selfPaymentCumulative(self.payment)
                 .mfCreditCumulative(mf.credit)
                 .mfDebitCumulative(mf.debit)
                 .mfSubAccountNames(new ArrayList<>(matchedSubs))
                 .build();
     }
 
     private String classify(SelfAccum self, MfAccum mf, BigDecimal diff, boolean masterRegistered) {
         boolean selfActive = self.closing.signum() != 0 || self.change.signum() != 0 || self.payment.signum() != 0;
         boolean mfActive = mf.credit.signum() != 0 || mf.debit.signum() != 0;
         if (selfActive && !mfActive) return "MF_MISSING";
         if (!selfActive && mfActive) return "SELF_MISSING";
         if (!selfActive && !mfActive) return "MATCH";
         BigDecimal abs = diff.abs();
         if (abs.compareTo(FinanceConstants.MATCH_TOLERANCE) <= 0) return "MATCH";
         if (abs.compareTo(MINOR_UPPER) <= 0) return "MINOR";
         return "MAJOR";
     }
 
     private Summary buildSummary(List<SupplierBalanceRow> rows) {
         int matched = 0, minor = 0, major = 0, mfMissing = 0, selfMissing = 0;
         BigDecimal totalSelf = BigDecimal.ZERO;
         BigDecimal totalMf = BigDecimal.ZERO;
         BigDecimal totalDiff = BigDecimal.ZERO;
         for (SupplierBalanceRow r : rows) {
             switch (r.getStatus()) {
                 case "MATCH" -> matched++;
                 case "MINOR" -> minor++;
                 case "MAJOR" -> major++;
                 case "MF_MISSING" -> mfMissing++;
                 case "SELF_MISSING" -> selfMissing++;
             }
             totalSelf = totalSelf.add(r.getSelfBalance());
             totalMf = totalMf.add(r.getMfBalance());
             totalDiff = totalDiff.add(r.getDiff());
         }
         return Summary.builder()
                 .totalSuppliers(rows.size())
                 .matchedCount(matched)
                 .minorCount(minor)
                 .majorCount(major)
                 .mfMissingCount(mfMissing)
                 .selfMissingCount(selfMissing)
                 .totalSelfBalance(totalSelf)
                 .totalMfBalance(totalMf)
                 .totalDiff(totalDiff)
                 .build();
     }
 
     private SupplierBalancesResponse emptyResponse(Integer shopNo) {
+        // Codex Major fix (P1-02): self summary が 1 件もない場合でも
+        // opening 未投入なら警告を表示する (= 完全な初回起動状態の検出)。
+        boolean openingMissing = !openingBalanceService.isOpeningBalanceLoaded(
+                shopNo, MfPeriodConstants.SELF_BACKFILL_START);
         return SupplierBalancesResponse.builder()
                 .shopNo(shopNo)
                 .asOfMonth(null)
                 .mfStartDate(MfPeriodConstants.MF_JOURNALS_FETCH_FROM)
                 .fetchedAt(Instant.now())
                 .totalJournalCount(0)
                 .rows(List.of())
                 .summary(Summary.builder()
                         .totalSuppliers(0).matchedCount(0).minorCount(0).majorCount(0)
                         .mfMissingCount(0).selfMissingCount(0)
                         .totalSelfBalance(BigDecimal.ZERO)
                         .totalMfBalance(BigDecimal.ZERO)
                         .totalDiff(BigDecimal.ZERO)
                         .build())
+                .openingBalanceMissing(openingMissing)
+                .openingBalanceWarning(openingMissing
+                        ? "MF 期首残 (m_supplier_opening_balance) が未投入です。"
+                                + "初回運用では /finance/supplier-opening-balance/mf-fetch を先に実行してください。"
+                        : null)
                 .build();
     }
 
     private static BigDecimal nz(BigDecimal v) {
         return v == null ? BigDecimal.ZERO : v;
     }
 
     private static class SelfAccum {
         BigDecimal opening = BigDecimal.ZERO;
         BigDecimal change = BigDecimal.ZERO;
         BigDecimal payment = BigDecimal.ZERO;
         BigDecimal closing = BigDecimal.ZERO;
     }
 
     private static class MfAccum {
         BigDecimal credit = BigDecimal.ZERO;
         BigDecimal debit = BigDecimal.ZERO;
     }
 }
diff --git a/backend/src/main/java/jp/co/oda32/dto/finance/IntegrityReportResponse.java b/backend/src/main/java/jp/co/oda32/dto/finance/IntegrityReportResponse.java
index ffbcc7f..638b5b0 100644
--- a/backend/src/main/java/jp/co/oda32/dto/finance/IntegrityReportResponse.java
+++ b/backend/src/main/java/jp/co/oda32/dto/finance/IntegrityReportResponse.java
@@ -1,87 +1,97 @@
 package jp.co.oda32.dto.finance;
 
 import lombok.Builder;
 import lombok.Data;
 
 import java.math.BigDecimal;
 import java.time.Instant;
 import java.time.LocalDate;
 import java.util.List;
 
 /**
  * 買掛帳 整合性検出機能 (軸 B + 軸 C) のレスポンス。
  * <p>
  * 期間内の全 supplier を一括診断し、mfOnly / selfOnly / amountMismatch / unmatchedSuppliers
  * の 4 カテゴリに分類する。
  * <p>
  * 設計書: claudedocs/design-integrity-report.md §7
  *
  * @since 2026-04-22
  */
 @Data
 @Builder
 public class IntegrityReportResponse {
     private Integer shopNo;
     private LocalDate fromMonth;
     private LocalDate toMonth;
     private Instant fetchedAt;
     /** MF から取得した全仕訳件数 (全 supplier 合計、supplier 別ではない)。 */
     private Integer totalJournalCount;
     /** 期間内に self or MF どちらかに出現した supplier 数 (unmatched supplier も含む union、R10 反映)。 */
     private Integer supplierCount;
 
     private List<MfOnlyEntry> mfOnly;
     private List<SelfOnlyEntry> selfOnly;
     private List<AmountMismatchEntry> amountMismatch;
     private List<UnmatchedSupplierEntry> unmatchedSuppliers;
     private Summary summary;
+    /**
+     * Codex Major fix (P1-02): {@code m_supplier_opening_balance} が空のときの警告フラグ。
+     * <p>
+     * 整合性レポートの cumulative diff は累積残一覧と同じ opening balance を注入しており、
+     * opening 未投入だと {@code supplierCumulativeDiff} が誤値になる。
+     * UI 側でバナー表示し、{@code /finance/supplier-opening-balance/mf-fetch} の事前実行を促す。
+     */
+    private Boolean openingBalanceMissing;
+    /** {@code openingBalanceMissing=true} の場合のユーザー向け警告メッセージ。 */
+    private String openingBalanceWarning;
 
     /** MF にあって自社に無い (supplier × 月 単位)。SELF_MISSING。 */
     @Data
     @Builder
     public static class MfOnlyEntry {
         private LocalDate transactionMonth;
         private String subAccountName;
         private BigDecimal creditAmount;
         private BigDecimal debitAmount;
         private BigDecimal periodDelta;          // credit - debit
         private Integer branchCount;
         /** mf_account_master.search_key → supplier_code 逆引きで解決した supplierNo (null なら未登録)。 */
         private Integer guessedSupplierNo;
         private String guessedSupplierCode;
         private String reason;
         /** MF /journals の number (取引番号) リスト。自社取込漏れ疑いのときの確認用。昇順・重複排除。 */
         private List<Integer> journalNumbers;
         /** toMonth 時点での supplier 累積残が MATCH かどうか。true なら期末で自社と MF が一致 = ノイズとして除外可。 */
         private Boolean reconciledAtPeriodEnd;
         /** toMonth 時点での supplier 単位累積残 diff (= selfBalance - mfBalance)。null なら軸 D 計算不可。 */
         private BigDecimal supplierCumulativeDiff;
         /** 差分確認状態: null | IGNORED | MF_APPLIED (案 X+Y)。 */
         private String reviewStatus;
         private Instant reviewedAt;
         private String reviewedByName;
         private String reviewNote;
         /** 現在値と snapshot が ±¥100 超で乖離していれば true (再確認必要)。 */
         private Boolean snapshotStale;
     }
 
     /** 自社にあって MF に無い (supplier × 月 単位)。MF_MISSING。 */
     @Data
     @Builder
     public static class SelfOnlyEntry {
         private LocalDate transactionMonth;
         private Integer supplierNo;
         private String supplierCode;
         private String supplierName;
         private BigDecimal selfDelta;            // change - payment_settled
         private BigDecimal changeTaxIncluded;
         private BigDecimal paymentSettledTaxIncluded;
         private Integer taxRateRowCount;
         private String reason;
         /** toMonth 時点での supplier 累積残が MATCH かどうか。true なら期末で解消済 = ノイズ。 */
         private Boolean reconciledAtPeriodEnd;
         /** toMonth 時点での supplier 単位累積残 diff (= selfBalance - mfBalance)。 */
         private BigDecimal supplierCumulativeDiff;
         /** 差分確認状態: null | IGNORED | MF_APPLIED (案 X+Y)。 */
         private String reviewStatus;
         private Instant reviewedAt;
diff --git a/backend/src/main/java/jp/co/oda32/dto/finance/SupplierBalancesResponse.java b/backend/src/main/java/jp/co/oda32/dto/finance/SupplierBalancesResponse.java
index e100474..e269a2d 100644
--- a/backend/src/main/java/jp/co/oda32/dto/finance/SupplierBalancesResponse.java
+++ b/backend/src/main/java/jp/co/oda32/dto/finance/SupplierBalancesResponse.java
@@ -1,65 +1,75 @@
 package jp.co.oda32.dto.finance;
 
 import lombok.Builder;
 import lombok.Data;
 
 import java.math.BigDecimal;
 import java.time.Instant;
 import java.time.LocalDate;
 import java.util.List;
 
 /**
  * 買掛 supplier 累積残一覧 レスポンス (軸 D)。
  * <p>
  * asOfMonth 時点での全 supplier の自社 / MF 累積残を突合し、MATCH / MINOR / MAJOR /
  * MF_MISSING / SELF_MISSING で分類する。
  * <p>
  * 設計書: claudedocs/design-supplier-balances-health.md §3
  *
  * @since 2026-04-23
  */
 @Data
 @Builder
 public class SupplierBalancesResponse {
     private Integer shopNo;
     private LocalDate asOfMonth;
     private LocalDate mfStartDate;
     private Instant fetchedAt;
     private Integer totalJournalCount;
     private List<SupplierBalanceRow> rows;
     private Summary summary;
+    /**
+     * Codex Major fix (P1-02): {@code m_supplier_opening_balance} が空のときの警告フラグ。
+     * <p>
+     * MF journal #1 (期首残高仕訳) を {@link jp.co.oda32.domain.service.finance.mf.MfOpeningBalanceService#fetchFromMfJournalOne}
+     * で取込んでいない初回運用では opening が silent に 0 になり、累積残が誤って計算される。
+     * UI 側でバナー表示し、{@code /finance/supplier-opening-balance/mf-fetch} の事前実行を促す。
+     */
+    private Boolean openingBalanceMissing;
+    /** {@code openingBalanceMissing=true} の場合のユーザー向け警告メッセージ。 */
+    private String openingBalanceWarning;
 
     @Data
     @Builder
     public static class SupplierBalanceRow {
         private Integer supplierNo;
         private String supplierCode;
         private String supplierName;
         private BigDecimal selfBalance;
         private BigDecimal mfBalance;
         private BigDecimal diff;
         /** MATCH / MINOR / MAJOR / MF_MISSING / SELF_MISSING */
         private String status;
         private Boolean masterRegistered;
         private BigDecimal selfOpening;
         private BigDecimal selfChangeCumulative;
         private BigDecimal selfPaymentCumulative;
         private BigDecimal mfCreditCumulative;
         private BigDecimal mfDebitCumulative;
         private List<String> mfSubAccountNames;
     }
 
     @Data
     @Builder
     public static class Summary {
         private Integer totalSuppliers;
         private Integer matchedCount;
         private Integer minorCount;
         private Integer majorCount;
         private Integer mfMissingCount;
         private Integer selfMissingCount;
         private BigDecimal totalSelfBalance;
         private BigDecimal totalMfBalance;
         private BigDecimal totalDiff;
     }
 }
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

2026-05-07T00:24:58.482886Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path 'backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java' -Pattern 'acquireAdvisoryLock|exportVerifiedCsv' -Context 2,3"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path 'backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java' -Pattern 'acquireAdvisoryLock|exportVerifiedCsv' -Context 2,3" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path 'backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java' -Pattern 'acquireAdvisoryLock|exportVerifiedCsv' -Context 2,3"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n \"acquireAdvisoryLock exportVerifiedCsv\" \"backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java\"" in C:\project\odamitsu-data-hub
 succeeded in 1278ms:
301:        acquireAdvisoryLock(txMonth);
616:     * 同一 (shop_no, transaction_month) に対する applyVerification / exportVerifiedCsv を
625:    private void acquireAdvisoryLock(LocalDate transactionMonth) {
663:            //     exportVerifiedCsv で副行を再構築するため (従来は消失していた)。
860:    public VerifiedExportResult exportVerifiedCsv(LocalDate transactionMonth, Integer userNo) {
862:        acquireAdvisoryLock(transactionMonth);
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -LiteralPath 'backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java' | Select-Object -Skip 830 -First 80" in C:\project\odamitsu-data-hub
 succeeded in 1620ms:
    @Builder
    public static class VerifiedExportResult {
        private byte[] csv;
        private int rowCount;
        private int payableCount;
        private int auxCount;
        private long totalAmount;
        /** ルール未登録で CSV 行を生成できず除外した supplier_code + supplier_name */
        private List<String> skippedSuppliers;
        private LocalDate transactionMonth;
    }

    /**
     * {@code t_accounts_payable_summary} の「検証結果=一致 かつ MF出力=ON」行から、
     * Excel 再アップロード無しで MF 仕訳 CSV を生成する。
     * <p>生成される CSV は <b>PAYABLE(買掛金)行のみ</b>。振込明細 Excel 由来の
     * 費用仕訳 (EXPENSE) / 直接仕入高 (DIRECT_PURCHASE) / 振込手数料値引・早払収益 (SUMMARY)
     * は DB に保持されていないため含まれない。それらが必要な場合は Excel 取込フローを使うこと。
     * <p>CSV「取引日」列は 小田光の締め日 = {@code transactionMonth}(前月20日) 固定。
     * 支払日(送金日)は MF の銀行データ連携で自動付与されるため CSV には含めない。
     * <p>SF-C21: 同一 (shop_no, transactionMonth) に対する {@link #applyVerification} と本メソッドは
     * advisory lock {@code pg_advisory_xact_lock} で直列化される。advisory lock は
     * {@link Transactional @Transactional} 境界で自動解放されるため、対象 0 件等で early return
     * しても解放漏れはない (PostgreSQL 仕様)。
     *
     * @param transactionMonth 対象取引月 (例: 2026-01-20)。CSV 取引日列にも使用。
     * @param userNo           履歴保存用ユーザ番号
     */
    @Transactional
    public VerifiedExportResult exportVerifiedCsv(LocalDate transactionMonth, Integer userNo) {
        if (transactionMonth == null) throw new IllegalArgumentException("transactionMonth が未指定です");
        acquireAdvisoryLock(transactionMonth);

        List<TAccountsPayableSummary> dbRows = payableRepository.findVerifiedForMfExport(
                FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth);
        List<TPaymentMfAuxRow> auxRows = auxRowRepository
                .findByShopNoAndTransactionMonthOrderByTransferDateAscSequenceNoAsc(
                        FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth);

        if (dbRows.isEmpty() && auxRows.isEmpty()) {
            throw new IllegalArgumentException(
                    "対象取引月(" + transactionMonth + ")に出力対象データがありません"
                    + "（一致・MF出力ONの買掛金 0件、補助行 0件）");
        }

        // supplier_no 単位に集約する。verified_amount は振込明細一括検証時は税率別同値だが、
        // 手動 verify では税率別に異なる値が入り得るため、代表1行ではなく税率横断で SUM する。
        Map<Integer, List<TAccountsPayableSummary>> bySupplierNo = dbRows.stream()
                .collect(Collectors.groupingBy(TAccountsPayableSummary::getSupplierNo, LinkedHashMap::new, Collectors.toList()));

        // ルールを payment_supplier_code で引けるように
        List<MPaymentMfRule> rules = ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0");
        Map<String, MPaymentMfRule> byCode = new LinkedHashMap<>();
        for (MPaymentMfRule r : rules) {
            if (r.getPaymentSupplierCode() != null && !r.getPaymentSupplierCode().isBlank()) {
                byCode.putIfAbsent(r.getPaymentSupplierCode().trim(), r);
            }
        }

        List<PaymentMfPreviewRow> csvRows = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        long totalAmount = 0L;

        for (List<TAccountsPayableSummary> group : bySupplierNo.values()) {
            TAccountsPayableSummary s = group.get(0);
            MPaymentMfRule rule = s.getSupplierCode() == null ? null : byCode.get(s.getSupplierCode());
            if (rule == null || !"PAYABLE".equals(rule.getRuleKind())) {
                skipped.add(s.getSupplierCode() + " (supplier_no=" + s.getSupplierNo()
                        + (rule == null ? " ルール未登録" : " 非PAYABLE=" + rule.getRuleKind()) + ")");
                continue;
            }

            long amount = sumVerifiedAmountForGroup(group);
            if (amount == 0L) continue;
            totalAmount += amount;

            // SF-C03: CSV「取引日」列は 小田光の締め日 = transactionMonth (前月20日) 固定。
            // mf_transfer_date (Excel 振込明細の送金日) は監査用 DB 列としてのみ保持し、
            // CSV には載せない (送金日は MF 銀行データ連携で自動付与されるため)。
            String sourceName = rule.getSourceName();
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n \"forceAcknowledged setForceAcknowledged\" \"frontend\\components\\pages\\finance\\payment-mf-import.tsx\"" in C:\project\odamitsu-data-hub
 succeeded in 1266ms:
42:  const [forceAcknowledged, setForceAcknowledged] = useState(false)
115:      setForceAcknowledged(false)
330:                    checked={forceAcknowledged}
331:                    onCheckedChange={(v) => setForceAcknowledged(v === true)}
371:                  || (hasPerSupplierMismatch(preview) && !forceAcknowledged)
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -LiteralPath 'frontend/components/pages/finance/payment-mf-import.tsx' | Select-Object -Skip 310 -First 80" in C:\project\odamitsu-data-hub
 succeeded in 1551ms:
                  CSV ダウンロード経路を提供。force 反映前に Excel で目視レビュー可能。
                */}
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => downloadMismatchesCsv(preview.amountReconciliation!.perSupplierMismatches)}
                  >
                    <FileDown className="mr-1 h-4 w-4" />
                    全 {preview.amountReconciliation!.perSupplierMismatches.length} 件 CSV ダウンロード
                  </Button>
                  <span className="text-xs text-muted-foreground">
                    強制反映時は全件 audit log に保存されます。
                  </span>
                </div>
                <div className="mt-3 flex items-start gap-2 rounded border border-red-300 bg-red-100 p-2 text-xs">
                  <Checkbox
                    id="force-acknowledge"
                    checked={forceAcknowledged}
                    onCheckedChange={(v) => setForceAcknowledged(v === true)}
                    className="mt-0.5"
                  />
                  <Label htmlFor="force-acknowledge" className="cursor-pointer">
                    Excel 修正不能/業務承認済 のため <b>強制反映</b> する。
                    実行内容と全違反明細は <code>finance_audit_log</code> に記録されます。
                  </Label>
                </div>
              </AlertDescription>
            </Alert>
          )}

          <div className="flex flex-wrap items-center justify-between gap-4 rounded border bg-card p-4">
            <div className="space-y-1 text-sm">
              <div>ファイル: <span className="font-medium">{preview.fileName}</span></div>
              <div>
                送金日: <span className="font-medium">{preview.transferDate ?? '-'}</span>
                {preview.transactionMonth && (
                  <> / 対応取引月: <span className="font-medium">{preview.transactionMonth}</span></>
                )}
              </div>
              <div>
                合計: <span className="font-medium">{preview.totalAmount.toLocaleString()}</span> 円
                / 行数: {preview.totalRows}
              </div>
              <div className="flex gap-3 text-xs">
                <span className="text-green-700">一致 {preview.matchedCount}</span>
                <span className="text-amber-700">差異 {preview.diffCount}</span>
                <span className="text-red-600">買掛金なし {preview.unmatchedCount}</span>
                {preview.errorCount > 0 && (
                  <span className="text-red-600 font-semibold">未登録 {preview.errorCount}</span>
                )}
              </div>
            </div>
            <div className="flex gap-2">
              <Button
                variant={hasPerSupplierMismatch(preview) ? 'destructive' : 'outline'}
                disabled={
                  preview.errorCount > 0
                  || verifyMut.isPending
                  || (hasPerSupplierMismatch(preview) && !forceAcknowledged)
                }
                onClick={() => {
                  // G2-M2 (Frontend Major): force 反映時は CSV download + 反映理由 + 全件確認 checkbox
                  // を持つ専用ダイアログへ。通常確定 / 再確定は従来の ConfirmDialog を使う。
                  if (hasPerSupplierMismatch(preview)) {
                    setReviewedAll(false)
                    setForceReason('')
                    setForceDialogOpen(true)
                  } else {
                    setConfirmVerify(true)
                  }
                }}
              >
                <CheckCheck className="mr-1 h-4 w-4" />
                {verifyMut.isPending
                  ? '反映中...'
                  : hasPerSupplierMismatch(preview)
                    ? '強制反映 (force=true)'
                    : '買掛金一覧へ反映'}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
The OAuth token re-encryption fix is placed in an already-versioned migration, so existing databases that already applied V033 will not receive the intended repair.

Review comment:

- [P1] Add a forward migration for the V033 token fix — C:\project\odamitsu-data-hub\backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:99-99
  When a database has already run V033, this edited migration code will not be invoked again because Flyway records the versioned migration as applied and proceeds only with later versions. That is the scenario this fix is meant to repair: the old V033 could mark `t_mf_oauth_token` as version 2 after re-encrypting only `access_token_enc`, leaving `refresh_token_enc` under the old key. Deploying this commit to such an environment runs V043/V044 but leaves those tokens broken, so the repair needs to be in a new migration or an explicit repair step rather than only modifying V033.
The OAuth token re-encryption fix is placed in an already-versioned migration, so existing databases that already applied V033 will not receive the intended repair.

Review comment:

- [P1] Add a forward migration for the V033 token fix — C:\project\odamitsu-data-hub\backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:99-99
  When a database has already run V033, this edited migration code will not be invoked again because Flyway records the versioned migration as applied and proceeds only with later versions. That is the scenario this fix is meant to repair: the old V033 could mark `t_mf_oauth_token` as version 2 after re-encrypting only `access_token_enc`, leaving `refresh_token_enc` under the old key. Deploying this commit to such an environment runs V043/V044 but leaves those tokens broken, so the repair needs to be in a new migration or an explicit repair step rather than only modifying V033.
