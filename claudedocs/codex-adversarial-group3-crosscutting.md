OpenAI Codex v0.128.0 (research preview)
--------
workdir: C:\project\odamitsu-data-hub
model: gpt-5.5
provider: openai
approval: never
sandbox: read-only
reasoning effort: none
reasoning summaries: none
session id: 019dfbc8-9f13-7120-92bf-0c6fd1b37b86
--------
user
あなたは Spring Boot / 認可・監査・例外設計の上級レビュアーです。
小田光データ連携基盤 (`C:/project/odamitsu-data-hub`) で実装された **横断改善系 4 件** を批判的にレビューしてください。

## レビュー対象 (Group 3: 横断改善)

### T1: 数字の権威階層 (UI ツールチップ)
- 新規: `claudedocs/design-source-of-truth-hierarchy.md` (9 章)
- 新規: `frontend/components/common/AmountSourceTooltip.tsx` (10 種 source enum)
- 改修: 8 finance 画面 / 33 列ヘッダーにツールチップ追加

### T2: finance_audit_log 基盤 (案 B AOP)
- 新規: V036 migration + `FinanceAuditLog`, `FinanceAuditLogRepository`, `AuditLog` annotation, `AuditExclude`, `FinanceAuditAspect`, `FinanceAuditWriter`
- 改修: 9 Service / 16 メソッドに `@AuditLog` 適用
- 5 機密フィールドに `@AuditExclude`
- 新規: `AuditLogResponse`, `AuditLogQueryService`, `AuditLogController`, frontend types + admin/audit-log page
- 新規: `claudedocs/design-finance-audit-log.md`

### T4: 認可マトリクス統一 (LoginUser.isAdmin())
- 改修: `LoginUser.java` に `isAdmin()` 追加
- 新規: `LoginUserSecurityBean.java` (`@loginUserSecurityBean.isAdmin()` SpEL)
- 改修: 13 Controller / 47 アノテーション統一 (`hasRole('ADMIN')` / `principal.shopNo == 0` → `@loginUserSecurityBean.isAdmin()`)
- 新規: `LoginUserAdminTest`, `LoginUserSecurityBeanTest` (10 テスト)
- 新規: `claudedocs/design-auth-matrix.md`

### T5: 例外ハンドリング統一
- 新規: `FinanceBusinessException` (400 + 元メッセージ + errorCode), `FinanceInternalException` (422 + 汎用メッセージ)
- 改修: `FinanceExceptionHandler.java` (新 handler 2 種追加、既存 handleIllegalState 維持)
- 改修: 3 箇所を FinanceBusinessException 化、7 箇所を FinanceInternalException 化、ResponseStatusException 2 箇所も置換
- 新規: `FinanceExceptionHandlerTest` (5 テスト)
- 新規: `claudedocs/design-exception-hierarchy.md`

## 批判的レビュー観点

### T1 (権威階層) - 動作変更なし、ドキュメント+UI のみ
1. AmountSourceTooltip の 10 種 source enum で網羅できる? (将来の数字源追加時の拡張性)
2. Tooltip の表示位置がソート列クリックを誤発火させる対策 (`<span onClick stopPropagation>`) は 100% 効くか?
3. 設計書 §5 の「各画面の参照元」表が実装と乖離した時の同期戦略

### T2 (audit log) - 大規模実装、最重点レビュー
4. **AOP のオーバーヘッド +5-30ms** の主張は妥当? Jackson valueToTree のパフォーマンス測定実証済?
5. **REQUIRES_NEW** での書込み: 業務 tx rollback でも証跡が残る → 失敗操作の証跡として正しいが、パフォーマンス影響は?
6. **書込み失敗時 WARN ログのみ** で業務処理継続 → audit 漏れが silent に発生するリスク。アラート機能なくて OK?
7. **before snapshot の自動 fetch 未実装** (`captureArgsAsAfter` のみ) → before/after diff 表示が機能不全な箇所はないか?
8. **バッチ経由操作の補足不可** (Service AOP 限定) → 集計バッチが silent UPDATE する場面で audit 漏れ
9. **JSON シリアライズ循環参照** 対策は十分? 大規模 Entity (例: TInvoice with parent group) で StackOverflow リスク
10. **5 機密フィールド @AuditExclude** で漏れている機密フィールドはないか? (例: m_user.password_enc, t_smile_payment.account_number 等)
11. **target_pk の JSONB 化** で JPA Embeddable 複合 PK が正しくシリアライズされるか?
12. admin 限定 audit-log 画面の認可チェック (`@PreAuthorize("@loginUserSecurityBean.isAdmin()")`) と、IP / UA 表示の個人情報統制
13. **finance_audit_log の保存期間** (パーティション・保存ポリシー未実装) → 数年で巨大化、検索性能劣化

### T4 (認可マトリクス) - 中規模実装
14. **「shopNo=0 = ROLE_ADMIN は常に同期」前提**で fail-safe (両方 OR ではなく AND) → 同期が崩れた場合に一律 false で「admin がアクセスできなくなる」災害復旧シナリオは大丈夫?
15. **旧 SpEL `principal.shopNo == 0` が動いていなかった可能性** という発見 → セキュリティ上の Critical: 過去にこの判定が常に false 返していたなら、admin 限定機能が誰でもアクセス可能だった?
16. `LoginUserSecurityBean` の SecurityContextHolder 経由 → MultiTenantHolder や RequestContextHolder との干渉
17. `@WithMockUser` テストが現プロジェクトには 0 件だが、将来追加時の互換性ガイダンスは設計書 §7 にあるが十分か?
18. T1/T2 後の admin 判定影響範囲 (整合性レポート、累積残一覧、audit-log) で UX が変わった機能は?

### T5 (例外ハンドリング) - 中規模実装
19. **Frontend ApiError に body フィールドなし** → toast.error が raw JSON 表示になる残課題は重大バグでは? (フロント UX 退化)
20. **既存 IllegalStateException 6 箇所維持** で「内部 assertion 系」の判定基準が曖昧 → 将来の改修者が混乱しないか?
21. FinanceBusinessException の errorCode が optional (default BUSINESS_ERROR) → 一貫した code 命名規約なしで将来の i18n / 分岐が困難
22. ResponseStatusException 5 箇所維持 (FORBIDDEN x3, NOT_FOUND x2) → これらも統一すべき? (例: FinanceForbiddenException, FinanceNotFoundException)
23. FinanceExceptionHandler が basePackages = jp.co.oda32.api.finance に絞られている → finance 配下以外で例外を投げた時の挙動

### 横断的観点
24. T1/T2/T4/T5 の **設計書間の整合性** (例: T2 で「IllegalStateException 維持」とあるが T5 で「新規コードでは IllegalStateException 回避」とある) → 開発ガイドラインの矛盾
25. P1-04 banner / T6 banner / 将来 audit log 警告 banner などの **banner スパム** リスク。ユーザーが多数 banner で麻痺する
26. T2 audit log と P1-08 import history の **機能重複** (= P1-08 で applied_at 記録、T2 でも apply 操作 audit) → どちらが正典?

## 出力フォーマット

Markdown、日本語、簡潔に (max 700 行、横断テーマで広いため少し長め可)。各指摘は:
- Severity: Critical / Major / Minor
- カテゴリ: セキュリティ / 監査 / 認可 / 例外 / UX / 設計
- 内容 (3-5 文)
- 推奨アクション (1-2 文)

最後に「Opus 実装で見落としていた最重要 5 件」を明記 (横断テーマで広いため 5 件)。


codex
`using-superpowers` と、今回のレビュー内容に合わせて Spring/React/PostgreSQL 系のレビュー技能を最小限読みます。その後、実装差分と設計書を確認して、指摘を重要度順にまとめます。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path C:\\Users\\kazuki\\.agents\\skills\\using-superpowers\\SKILL.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path C:\\Users\\kazuki\\.agents\\skills\\postgresql-code-review\\SKILL.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path C:\\Users\\kazuki\\.agents\\skills\\spring-framework-patterns\\SKILL.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path C:\\Users\\kazuki\\.agents\\skills\\typescript-react-reviewer\\SKILL.md" in C:\project\odamitsu-data-hub
 succeeded in 2174ms:
---
name: postgresql-code-review
description: 'PostgreSQL-specific code review assistant focusing on PostgreSQL best practices, anti-patterns, and unique quality standards. Covers JSONB operations, array usage, custom types, schema design, function optimization, and PostgreSQL-exclusive security features like Row Level Security (RLS).'
---

# PostgreSQL Code Review Assistant

Expert PostgreSQL code review for ${selection} (or entire project if no selection). Focus on PostgreSQL-specific best practices, anti-patterns, and quality standards that are unique to PostgreSQL.

## ?? PostgreSQL-Specific Review Areas

### JSONB Best Practices
```sql
-- ? BAD: Inefficient JSONB usage
SELECT * FROM orders WHERE data->>'status' = 'shipped';  -- No index support

-- ? GOOD: Indexable JSONB queries
CREATE INDEX idx_orders_status ON orders USING gin((data->'status'));
SELECT * FROM orders WHERE data @> '{"status": "shipped"}';

-- ? BAD: Deep nesting without consideration
UPDATE orders SET data = data || '{"shipping":{"tracking":{"number":"123"}}}';

-- ? GOOD: Structured JSONB with validation
ALTER TABLE orders ADD CONSTRAINT valid_status 
CHECK (data->>'status' IN ('pending', 'shipped', 'delivered'));
```

### Array Operations Review
```sql
-- ? BAD: Inefficient array operations
SELECT * FROM products WHERE 'electronics' = ANY(categories);  -- No index

-- ? GOOD: GIN indexed array queries
CREATE INDEX idx_products_categories ON products USING gin(categories);
SELECT * FROM products WHERE categories @> ARRAY['electronics'];

-- ? BAD: Array concatenation in loops
-- This would be inefficient in a function/procedure

-- ? GOOD: Bulk array operations
UPDATE products SET categories = categories || ARRAY['new_category']
WHERE id IN (SELECT id FROM products WHERE condition);
```

### PostgreSQL Schema Design Review
```sql
-- ? BAD: Not using PostgreSQL features
CREATE TABLE users (
    id INTEGER,
    email VARCHAR(255),
    created_at TIMESTAMP
);

-- ? GOOD: PostgreSQL-optimized schema
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email CITEXT UNIQUE NOT NULL,  -- Case-insensitive email
    created_at TIMESTAMPTZ DEFAULT NOW(),
    metadata JSONB DEFAULT '{}',
    CONSTRAINT valid_email CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
);

-- Add JSONB GIN index for metadata queries
CREATE INDEX idx_users_metadata ON users USING gin(metadata);
```

### Custom Types and Domains
```sql
-- ? BAD: Using generic types for specific data
CREATE TABLE transactions (
    amount DECIMAL(10,2),
    currency VARCHAR(3),
    status VARCHAR(20)
);

-- ? GOOD: PostgreSQL custom types
CREATE TYPE currency_code AS ENUM ('USD', 'EUR', 'GBP', 'JPY');
CREATE TYPE transaction_status AS ENUM ('pending', 'completed', 'failed', 'cancelled');
CREATE DOMAIN positive_amount AS DECIMAL(10,2) CHECK (VALUE > 0);

CREATE TABLE transactions (
    amount positive_amount NOT NULL,
    currency currency_code NOT NULL,
    status transaction_status DEFAULT 'pending'
);
```

## ?? PostgreSQL-Specific Anti-Patterns

### Performance Anti-Patterns
- **Avoiding PostgreSQL-specific indexes**: Not using GIN/GiST for appropriate data types
- **Misusing JSONB**: Treating JSONB like a simple string field
- **Ignoring array operators**: Using inefficient array operations
- **Poor partition key selection**: Not leveraging PostgreSQL partitioning effectively

### Schema Design Issues
- **Not using ENUM types**: Using VARCHAR for limited value sets
- **Ignoring constraints**: Missing CHECK constraints for data validation
- **Wrong data types**: Using VARCHAR instead of TEXT or CITEXT
- **Missing JSONB structure**: Unstructured JSONB without validation

### Function and Trigger Issues
```sql
-- ? BAD: Inefficient trigger function
CREATE OR REPLACE FUNCTION update_modified_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();  -- Should use TIMESTAMPTZ
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ? GOOD: Optimized trigger function
CREATE OR REPLACE FUNCTION update_modified_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Set trigger to fire only when needed
CREATE TRIGGER update_modified_time_trigger
    BEFORE UPDATE ON table_name
    FOR EACH ROW
    WHEN (OLD.* IS DISTINCT FROM NEW.*)
    EXECUTE FUNCTION update_modified_time();
```

## ?? PostgreSQL Extension Usage Review

### Extension Best Practices
```sql
-- ? Check if extension exists before creating
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ? Use extensions appropriately
-- UUID generation
SELECT uuid_generate_v4();

-- Password hashing
SELECT crypt('password', gen_salt('bf'));

-- Fuzzy text matching
SELECT word_similarity('postgres', 'postgre');
```

## ??? PostgreSQL Security Review

### Row Level Security (RLS)
```sql
-- ? GOOD: Implementing RLS
ALTER TABLE sensitive_data ENABLE ROW LEVEL SECURITY;

CREATE POLICY user_data_policy ON sensitive_data
    FOR ALL TO application_role
    USING (user_id = current_setting('app.current_user_id')::INTEGER);
```

### Privilege Management
```sql
-- ? BAD: Overly broad permissions
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO app_user;

-- ? GOOD: Granular permissions
GRANT SELECT, INSERT, UPDATE ON specific_table TO app_user;
GRANT USAGE ON SEQUENCE specific_table_id_seq TO app_user;
```

## ?? PostgreSQL Code Quality Checklist

### Schema Design
- [ ] Using appropriate PostgreSQL data types (CITEXT, JSONB, arrays)
- [ ] Leveraging ENUM types for constrained values
- [ ] Implementing proper CHECK constraints
- [ ] Using TIMESTAMPTZ instead of TIMESTAMP
- [ ] Defining custom domains for reusable constraints

### Performance Considerations
- [ ] Appropriate index types (GIN for JSONB/arrays, GiST for ranges)
- [ ] JSONB queries using containment operators (@>, ?)
- [ ] Array operations using PostgreSQL-specific operators
- [ ] Proper use of window functions and CTEs
- [ ] Efficient use of PostgreSQL-specific functions

### PostgreSQL Features Utilization
- [ ] Using extensions where appropriate
- [ ] Implementing stored procedures in PL/pgSQL when beneficial
- [ ] Leveraging PostgreSQL's advanced SQL features
- [ ] Using PostgreSQL-specific optimization techniques
- [ ] Implementing proper error handling in functions

### Security and Compliance
- [ ] Row Level Security (RLS) implementation where needed
- [ ] Proper role and privilege management
- [ ] Using PostgreSQL's built-in encryption functions
- [ ] Implementing audit trails with PostgreSQL features

## ?? PostgreSQL-Specific Review Guidelines

1. **Data Type Optimization**: Ensure PostgreSQL-specific types are used appropriately
2. **Index Strategy**: Review index types and ensure PostgreSQL-specific indexes are utilized
3. **JSONB Structure**: Validate JSONB schema design and query patterns
4. **Function Quality**: Review PL/pgSQL functions for efficiency and best practices
5. **Extension Usage**: Verify appropriate use of PostgreSQL extensions
6. **Performance Features**: Check utilization of PostgreSQL's advanced features
7. **Security Implementation**: Review PostgreSQL-specific security features

Focus on PostgreSQL's unique capabilities and ensure the code leverages what makes PostgreSQL special rather than treating it as a generic SQL database.
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2242ms:
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

| Thought | Reality |
|---------|---------|
| "This is just a simple question" | Questions are tasks. Check for skills. |
| "I need more context first" | Skill check comes BEFORE clarifying questions. |
| "Let me explore the codebase first" | Skills tell you HOW to explore. Check first. |
| "I can check git/files quickly" | Files lack conversation context. Check for skills. |
| "Let me gather information first" | Skills tell you HOW to gather information. |
| "This doesn't need a formal skill" | If a skill exists, use it. |
| "I remember this skill" | Skills evolve. Read current version. |
| "This doesn't count as a task" | Action = task. Check for skills. |
| "The skill is overkill" | Simple things become complex. Use it. |
| "I'll just do this one thing first" | Check BEFORE doing anything. |
| "This feels productive" | Undisciplined action wastes time. Skills prevent this. |
| "I know what that means" | Knowing the concept БВ using the skill. Invoke it. |

## Skill Priority

When multiple skills could apply, use this order:

1. **Process skills first** (brainstorming, debugging) - these determine HOW to approach the task
2. **Implementation skills second** (frontend-design, mcp-builder) - these guide execution

"Let's build X" Би brainstorming first, then implementation skills.
"Fix this bug" Би debugging first, then domain-specific skills.

## Skill Types

**Rigid** (TDD, debugging): Follow exactly. Don't adapt away discipline.

**Flexible** (patterns): Adapt principles to context.

The skill itself tells you which.

## User Instructions

Instructions say WHAT, not HOW. "Add X" or "Fix Y" doesn't mean skip workflows.
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3210ms:
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
  return <form action={submit}><button disabled={pending}>Send</button></form>;
}

// ? CORRECT: useFormStatus in child component
function SubmitButton() {
  const { pending } = useFormStatus();
  return <button type="submit" disabled={pending}>Send</button>;
}
function Form() {
  return <form action={submit}><SubmitButton /></form>;
}
```

```typescript
// ? WRONG: Promise created in render (infinite loop)
function Component() {
  const data = use(fetch('/api/data')); // New promise every render!
}

// ? CORRECT: Promise from props or state
function Component({ dataPromise }: { dataPromise: Promise<Data> }) {
  const data = use(dataPromise);
}
```

### State Mutation Detection

```typescript
// ? WRONG: Mutations (no re-render)
items.push(newItem);
setItems(items);

arr[i] = newValue;
setArr(arr);

// ? CORRECT: Immutable updates
setItems([...items, newItem]);
setArr(arr.map((x, idx) => idx === i ? newValue : x));
```

### TypeScript Red Flags

```typescript
// ? Red flags to catch
const data: any = response;           // Unsafe any
const items = arr[10];                // Missing undefined check
const App: React.FC<Props> = () => {}; // Discouraged pattern

// ? Preferred patterns
const data: ResponseType = response;
const items = arr[10]; // with noUncheckedIndexedAccess
const App = ({ prop }: Props) => {};  // Explicit props
```

## Review Workflow

1. **Scan for critical issues first** - Check for the patterns in "Critical (Block Merge)" section
2. **Check React 19 usage** - See [react19-patterns.md](references/react19-patterns.md) for new API patterns
3. **Evaluate state management** - Is state colocated? Server state vs client state separation?
4. **Assess TypeScript safety** - Generic components, discriminated unions, strict config
5. **Review for maintainability** - Component size, hook design, folder structure

## Reference Documents

For detailed patterns and examples:

- **[react19-patterns.md](references/react19-patterns.md)** - React 19 new hooks (useActionState, useOptimistic, use), Server/Client Component boundaries
- **[antipatterns.md](references/antipatterns.md)** - Comprehensive anti-pattern catalog with fixes
- **[checklist.md](references/checklist.md)** - Full code review checklist for thorough reviews

## State Management Quick Guide

| Data Type | Solution |
|-----------|----------|
| Server/async data | TanStack Query (never copy to local state) |
| Simple global UI state | Zustand (~1KB, no Provider) |
| Fine-grained derived state | Jotai (~2.4KB) |
| Component-local state | useState/useReducer |
| Form state | React 19 useActionState |

### TanStack Query Anti-Pattern

```typescript
// ? NEVER copy server data to local state
const { data } = useQuery({ queryKey: ['todos'], queryFn: fetchTodos });
const [todos, setTodos] = useState([]);
useEffect(() => setTodos(data), [data]);

// ? Query IS the source of truth
const { data: todos } = useQuery({ queryKey: ['todos'], queryFn: fetchTodos });
```

## TypeScript Config Recommendations

```json
{
  "compilerOptions": {
    "strict": true,
    "noUncheckedIndexedAccess": true,
    "noImplicitReturns": true,
    "exactOptionalPropertyTypes": true
  }
}
```

`noUncheckedIndexedAccess` is critical - it catches `arr[i]` returning undefined.

## Immediate Red Flags

When reviewing, flag these immediately:

| Pattern | Problem | Fix |
|---------|---------|-----|
| `eslint-disable react-hooks/exhaustive-deps` | Hides stale closure bugs | Refactor logic |
| Component defined inside component | Remounts every render | Move outside |
| `useState(undefined)` for inputs | Uncontrolled warning | Use empty string |
| `React.FC` with generics | Generic inference breaks | Use explicit props |
| Barrel files (`index.ts`) in app code | Bundle bloat, circular deps | Direct imports |
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 4452ms:
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

#### 1.1 Constructor Injection (Preferred)

**Rule:** ALWAYS use constructor injection for required dependencies. Never use field injection in production code.

? **Good:**
```java
@Service
public class UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    // Constructor injection - preferred approach
    public UserService(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    public User createUser(UserDTO dto) {
        User user = new User(dto.getEmail());
        User savedUser = userRepository.save(user);
        emailService.sendWelcomeEmail(savedUser);
        return savedUser;
    }
}
```

**Why good:**
- Dependencies are immutable (final fields)
- Dependencies are mandatory - cannot create instance without them
- Easy to test - can inject mocks in tests
- No reflection needed in tests
- Constructor clearly documents all dependencies

? **Bad:**
```java
@Service
public class UserService {

    // Field injection - DON'T DO THIS
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    public User createUser(UserDTO dto) {
        User user = new User(dto.getEmail());
        User savedUser = userRepository.save(user);
        emailService.sendWelcomeEmail(savedUser);
        return savedUser;
    }
}
```

**Why bad:**
- Cannot be final - mutable dependencies
- Can create instance without dependencies (NullPointerException risk)
- Hard to test - requires reflection or Spring context in tests
- Hides dependencies - not clear what's required
- Violates encapsulation

**With Lombok (Acceptable):**
```java
@Service
@RequiredArgsConstructor  // Generates constructor for final fields
public class UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    public User createUser(UserDTO dto) {
        User user = new User(dto.getEmail());
        User savedUser = userRepository.save(user);
        emailService.sendWelcomeEmail(savedUser);
        return savedUser;
    }
}
```

#### 1.2 Optional Dependencies with Setter Injection

**Rule:** Use setter injection ONLY for optional dependencies.

? **Good:**
```java
@Service
public class NotificationService {

    private final EmailService emailService;  // Required
    private SmsService smsService;  // Optional

    public NotificationService(EmailService emailService) {
        this.emailService = emailService;
    }

    @Autowired(required = false)
    public void setSmsService(SmsService smsService) {
        this.smsService = smsService;
    }

    public void notify(User user, String message) {
        emailService.send(user.getEmail(), message);

        if (smsService != null && user.getPhoneNumber() != null) {
            smsService.send(user.getPhoneNumber(), message);
        }
    }
}
```

#### 1.3 Avoiding Circular Dependencies

**Rule:** Circular dependencies indicate design problems. Refactor instead of using @Lazy.

? **Bad:**
```java
@Service
public class OrderService {
    @Autowired
    @Lazy  // Band-aid solution
    private PaymentService paymentService;
}

@Service
public class PaymentService {
    @Autowired
    @Lazy  // Band-aid solution
    private OrderService orderService;
}
```

? **Good:**
```java
// Extract common logic to a new service
@Service
public class OrderProcessingService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public OrderProcessingService(OrderRepository orderRepository,
                                  PaymentRepository paymentRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    public void processOrder(Order order, Payment payment) {
        order.setStatus(OrderStatus.PROCESSING);
        orderRepository.save(order);

        payment.setOrderId(order.getId());
        paymentRepository.save(payment);
    }
}

@Service
public class OrderService {
    private final OrderProcessingService processingService;
    // ...
}

@Service
public class PaymentService {
    private final OrderProcessingService processingService;
    // ...
}
```

### Task 2: Understand Bean Lifecycle and Scopes

#### 2.1 Bean Scopes

**Available Scopes:**
- `singleton` (default): One instance per Spring container
- `prototype`: New instance each time bean is requested
- `request`: One instance per HTTP request (web applications)
- `session`: One instance per HTTP session (web applications)
- `application`: One instance per ServletContext (web applications)

? **Good:**
```java
// Singleton (default) - stateless services
@Service
@Scope("singleton")  // Can omit - it's default
public class UserService {
    // Stateless - safe to share
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
}

// Prototype - stateful beans
@Component
@Scope("prototype")
public class ReportGenerator {
    // Stateful - each user gets their own instance
    private final List<String> reportLines = new ArrayList<>();

    public void addLine(String line) {
        reportLines.add(line);
    }

    public String generate() {
        return String.join("\n", reportLines);
    }
}

// Request scope - web layer
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestContext {
    private String requestId;
    private String userId;

    // Getters and setters
}
```

? **Bad:**
```java
// Singleton with mutable state - THREAD UNSAFE
@Service
public class UserService {

    private User currentUser;  // Shared across all requests - BAD!

    public void processUser(User user) {
        this.currentUser = user;  // Race condition!
        // Process...
    }
}
```

#### 2.2 Bean Lifecycle Callbacks

**Rule:** Use @PostConstruct for initialization, @PreDestroy for cleanup.

? **Good:**
```java
@Service
public class CacheService {

    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void initialize() {
        System.out.println("Initializing cache service...");
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::cleanupExpiredEntries, 1, 1, TimeUnit.HOURS);
    }

    @PreDestroy
    public void cleanup() {
        System.out.println("Cleaning up cache service...");
        cache.clear();
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private void cleanupExpiredEntries() {
        // Cleanup logic
    }
}
```

#### 2.3 Component Stereotypes

**Rule:** Use the most specific stereotype annotation.

```java
@Component   // Generic Spring-managed component
@Service     // Business logic layer
@Repository  // Data access layer (adds exception translation)
@Controller  // MVC controller (returns views)
@RestController  // REST API controller (returns data)
@Configuration  // Configuration class
```

? **Good:**
```java
@Repository  // Data access - enables exception translation
public interface UserRepository extends JpaRepository<User, Long> {
}

@Service  // Business logic
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
}

@RestController  // REST API
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }
}

@Configuration  // Configuration
public class SecurityConfig {
    // Configuration beans
}
```

### Task 3: Design REST API Controllers

#### 3.1 Controller Structure

**Rule:** Keep controllers thin - delegate business logic to services.

? **Good:**
```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<Page<UserDTO>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy));
        Page<UserDTO> users = userService.findAll(pageable);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        return userService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserDTO created = userService.createUser(request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {

        return userService.updateUser(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
```

**Why good:**
- RESTful URL structure
- Proper HTTP methods and status codes
- Pagination and sorting support
- Validation with @Valid
- Location header for created resources
- Delegates logic to service layer

? **Bad:**
```java
@RestController
public class UserController {

    @Autowired
    private UserRepository userRepository;  // Controller accessing repository directly!

    @GetMapping("/getUsers")  // Non-RESTful URL
    public List<User> getUsers() {  // Returns entities, not DTOs
        return userRepository.findAll();  // Business logic in controller
    }

    @PostMapping("/createUser")  // Non-RESTful URL
    public User createUser(@RequestBody User user) {  // No validation
        // Business logic in controller - BAD!
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new RuntimeException("Email exists");  // Poor error handling
        }
        return userRepository.save(user);  // Returns 200 instead of 201
    }
}
```

#### 3.2 Request/Response Patterns

**Rule:** Use DTOs for API contracts. Never expose entities directly.

? **Good:**
```java
// Request DTOs
public class CreateUserRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    // Getters and setters
}

// Response DTOs
public class UserDTO {

    private Long id;
    private String email;
    private String name;
    private LocalDateTime createdAt;
    private boolean active;

    // No password field - security
    // Getters and setters
}

// Service layer
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserDTO createUser(CreateUserRequest request) {
        // Validate
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        // Map DTO to entity
        User user = new User();
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());

        // Save
        User saved = userRepository.save(user);

        // Map entity to DTO
        return userMapper.toDTO(saved);
    }
}
```

#### 3.3 HTTP Status Codes

**Rule:** Use correct HTTP status codes.

```java
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // 200 OK - Successful GET/PUT
    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> getOrder(@PathVariable Long id) {
        return orderService.findById(id)
                .map(ResponseEntity::ok)  // 200 OK
                .orElse(ResponseEntity.notFound().build());  // 404 Not Found
    }

    // 201 Created - Successful POST
    @PostMapping
    public ResponseEntity<OrderDTO> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderDTO created = orderService.createOrder(request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);  // 201 Created
    }

    // 204 No Content - Successful DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();  // 204 No Content
    }

    // 202 Accepted - Async processing
    @PostMapping("/{id}/process")
    public ResponseEntity<Void> processOrder(@PathVariable Long id) {
        orderService.processOrderAsync(id);
        return ResponseEntity.accepted().build();  // 202 Accepted
    }

    // 400 Bad Request - Validation failures (handled by @Valid)
    // 401 Unauthorized - Not authenticated (handled by Security)
    // 403 Forbidden - Not authorized (handled by Security)
    // 404 Not Found - Resource doesn't exist
    // 409 Conflict - Business rule violation
    // 500 Internal Server Error - Unexpected errors
}
```

### Task 4: Implement Data Access Layer with Spring Data JPA

#### 4.1 Repository Interfaces

**Rule:** Extend appropriate Spring Data interface based on needs.

? **Good:**
```java
// Simple CRUD - JpaRepository
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Query methods - Spring Data generates implementation
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByActiveTrue();

    // Custom query
    @Query("SELECT u FROM User u WHERE u.createdAt > :date")
    List<User> findRecentUsers(@Param("date") LocalDateTime date);

    // Native query
    @Query(value = "SELECT * FROM users WHERE email LIKE %:domain", nativeQuery = true)
    List<User> findByEmailDomain(@Param("domain") String domain);

    // Modifying query
    @Modifying
    @Query("UPDATE User u SET u.active = false WHERE u.lastLoginAt < :date")
    int deactivateInactiveUsers(@Param("date") LocalDateTime date);
}
```

**Repository Hierarchy:**
- `Repository<T, ID>` - Marker interface, no methods
- `CrudRepository<T, ID>` - Basic CRUD operations
- `PagingAndSortingRepository<T, ID>` - Adds pagination and sorting
- `JpaRepository<T, ID>` - JPA-specific features (flush, batch operations)

#### 4.2 Custom Queries with Specifications

**Rule:** Use Specifications for dynamic queries instead of building query strings.

? **Good:**
```java
// Specification
public class UserSpecifications {

    public static Specification<User> hasEmail(String email) {
        return (root, query, cb) ->
            email == null ? null : cb.equal(root.get("email"), email);
    }

    public static Specification<User> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<User> createdAfter(LocalDateTime date) {
        return (root, query, cb) ->
            date == null ? null : cb.greaterThan(root.get("createdAt"), date);
    }

    public static Specification<User> nameLike(String name) {
        return (root, query, cb) ->
            name == null ? null : cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }
}

// Repository
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
}

// Service
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public Page<User> searchUsers(UserSearchCriteria criteria, Pageable pageable) {
        Specification<User> spec = Specification.where(null);

        if (criteria.getEmail() != null) {
            spec = spec.and(UserSpecifications.hasEmail(criteria.getEmail()));
        }

        if (criteria.isActiveOnly()) {
            spec = spec.and(UserSpecifications.isActive());
        }

        if (criteria.getCreatedAfter() != null) {
            spec = spec.and(UserSpecifications.createdAfter(criteria.getCreatedAfter()));
        }

        if (criteria.getName() != null) {
            spec = spec.and(UserSpecifications.nameLike(criteria.getName()));
        }

        return userRepository.findAll(spec, pageable);
    }
}
```

#### 4.3 Pagination and Sorting

**Rule:** Always support pagination for list endpoints.

? **Good:**
```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<Page<UserDTO>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id,desc") String[] sort) {

        // Parse sort parameters
        List<Sort.Order> orders = Arrays.stream(sort)
                .map(s -> {
                    String[] parts = s.split(",");
                    String property = parts[0];
                    Sort.Direction direction = parts.length > 1 && parts[1].equalsIgnoreCase("desc")
                            ? Sort.Direction.DESC
                            : Sort.Direction.ASC;
                    return new Sort.Order(direction, property);
                })
                .toList();

        Pageable pageable = PageRequest.of(page, size, Sort.by(orders));
        Page<UserDTO> users = userService.findAll(pageable);

        return ResponseEntity.ok(users);
    }
}
```

#### 4.4 Transaction Management

**Rule:** Use @Transactional on service methods, not repository methods.

? **Good:**
```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final EmailService emailService;

    @Transactional
    public OrderDTO createOrder(CreateOrderRequest request) {
        // Create order
        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setStatus(OrderStatus.PENDING);
        Order savedOrder = orderRepository.save(order);

        // Create payment
        Payment payment = new Payment();
        payment.setOrderId(savedOrder.getId());
        payment.setAmount(request.getAmount());
        paymentRepository.save(payment);

        // If email fails, transaction rolls back
        emailService.sendOrderConfirmation(savedOrder);

        return mapToDTO(savedOrder);
    }

    @Transactional(readOnly = true)  // Optimization for read-only operations
    public Optional<OrderDTO> findById(Long id) {
        return orderRepository.findById(id)
                .map(this::mapToDTO);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)  // For critical operations
    public void processPayment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // Process payment with highest isolation level
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);
    }
}
```

**Transaction Propagation:**
```java
@Service
public class UserService {

    @Transactional(propagation = Propagation.REQUIRED)  // Default - join existing or create new
    public void updateUser(User user) {
        // Uses existing transaction or creates new
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)  // Always create new transaction
    public void auditLog(String action) {
        // Independent transaction - commits even if parent rolls back
    }

    @Transactional(propagation = Propagation.MANDATORY)  // Must have existing transaction
    public void criticalOperation() {
        // Throws exception if no transaction exists
    }
}
```

### Task 5: Design Service Layer

#### 5.1 Service Boundaries

**Rule:** Services should represent business capabilities, not data access.

? **Good:**
```java
// Good service boundaries
@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserDTO register(RegistrationRequest request) {
        // Validate
        validateEmailNotExists(request.getEmail());

        // Create user
        User user = createUser(request);
        User saved = userRepository.save(user);

        // Send welcome email
        emailService.sendWelcomeEmail(saved.getEmail());

        return mapToDTO(saved);
    }

    private void validateEmailNotExists(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
    }

    private User createUser(RegistrationRequest request) {
        User user = new User();
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        return user;
    }
}

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Optional<UserDTO> findById(Long id) {
        return userRepository.findById(id).map(this::mapToDTO);
    }

    @Transactional(readOnly = true)
    public Page<UserDTO> findAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::mapToDTO);
    }
}
```

#### 5.2 DTO Mapping

**Rule:** Keep entity-to-DTO mapping logic in one place.

? **Good with MapStruct:**
```java
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDTO toDTO(User user);

    List<UserDTO> toDTOs(List<User> users);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    User toEntity(CreateUserRequest request);
}

// Usage in service
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserDTO createUser(CreateUserRequest request) {
        User user = userMapper.toEntity(request);
        User saved = userRepository.save(user);
        return userMapper.toDTO(saved);
    }
}
```

? **Good without MapStruct:**
```java
@Service
public class UserService {

    private final UserRepository userRepository;

    private UserDTO mapToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setName(user.getName());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setActive(user.isActive());
        return dto;
    }

    private User mapToEntity(CreateUserRequest request) {
        User user = new User();
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        return user;
    }
}
```

### Task 6: Implement Global Exception Handling

#### 6.1 Custom Exceptions

**Rule:** Create domain-specific exceptions for business errors.

? **Good:**
```java
// Base exception
public abstract class BusinessException extends RuntimeException {

    private final String errorCode;

    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

// Specific exceptions
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resourceName, Long id) {
        super(String.format("%s with id %d not found", resourceName, id), "RESOURCE_NOT_FOUND");
    }
}

public class EmailAlreadyExistsException extends BusinessException {

    public EmailAlreadyExistsException(String email) {
        super(String.format("Email %s already exists", email), "EMAIL_EXISTS");
    }
}

public class InsufficientBalanceException extends BusinessException {

    public InsufficientBalanceException(Long accountId) {
        super(String.format("Insufficient balance in account %d", accountId), "INSUFFICIENT_BALANCE");
    }
}
```

#### 6.2 Global Exception Handler with @ControllerAdvice

**Rule:** Centralize exception handling in @ControllerAdvice.

? **Good:**
```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        log.warn("Email already exists: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );

        ValidationErrorResponse response = ValidationErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Invalid request parameters")
                .fieldErrors(errors)
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

// Error response DTOs
@Data
@Builder
public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String errorCode;
}

@Data
@Builder
public class ValidationErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private Map<String, String> fieldErrors;
}
```

### Task 7: Implement Spring Security

#### 7.1 Security Configuration

**Rule:** Use security configuration classes for centralized security setup.

? **Good (Spring Security 6+):**
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/users/**").hasAnyRole("USER", "ADMIN")
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .httpBasic(Customizer.withDefaults())
            .formLogin(form -> form.disable())
            .logout(logout -> logout
                .logoutUrl("/api/v1/auth/logout")
                .logoutSuccessHandler((request, response, authentication) ->
                    response.setStatus(HttpServletResponse.SC_OK))
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
```

#### 7.2 JWT Implementation

**Rule:** Implement JWT for stateless authentication in REST APIs.

? **Good:**
```java
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:3600000}")  // 1 hour default
    private long expiration;

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(secret).parseClaimsJws(token);
            return !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }

    private boolean isTokenExpired(String token) {
        Date expiration = getClaimsFromToken(token).getExpiration();
        return expiration.before(new Date());
    }
}

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {

        String token = getTokenFromRequest(request);

        if (token != null && tokenProvider.validateToken(token)) {
            String username = tokenProvider.getUsernameFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

#### 7.3 Method Security

**Rule:** Use method security for fine-grained authorization.

? **Good:**
```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDTO> findAll() {
        return userRepository.findAll().stream()
                .map(this::mapToDTO)
                .toList();
    }

    @PreAuthorize("hasRole('USER') and #userId == authentication.principal.id")
    public UserDTO findById(Long userId) {
        return userRepository.findById(userId)
                .map(this::mapToDTO)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @PostAuthorize("returnObject.email == authentication.principal.username")
    public UserDTO updateUser(Long id, UpdateUserRequest request) {
        // Implementation
    }

    @Secured("ROLE_ADMIN")
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}
```

### Task 8: Implement Testing Strategies

#### 8.1 Unit Testing Services

**Rule:** Test services in isolation with mocked dependencies.

? **Good:**
```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService userService;

    @Test
    void createUser_WithValidData_ShouldCreateUser() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        User user = new User();
        user.setId(1L);
        user.setEmail(request.getEmail());

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        // Act
        UserDTO result = userService.createUser(request);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("test@example.com", result.getEmail());

        verify(userRepository).existsByEmail(request.getEmail());
        verify(passwordEncoder).encode(request.getPassword());
        verify(userRepository).save(any(User.class));
        verify(emailService).sendWelcomeEmail(any(User.class));
    }

    @Test
    void createUser_WithExistingEmail_ShouldThrowException() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("existing@example.com");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        // Act & Assert
        assertThrows(EmailAlreadyExistsException.class, () ->
            userService.createUser(request));

        verify(userRepository).existsByEmail(request.getEmail());
        verify(userRepository, never()).save(any(User.class));
    }
}
```

#### 8.2 Integration Testing with @SpringBootTest

**Rule:** Use @SpringBootTest for integration tests that need full context.

? **Good:**
```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional  // Rollback after each test
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void createUser_WithValidData_ShouldReturn201() throws Exception {
        // Arrange
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("test@example.com");
        request.setName("Test User");
        request.setPassword("password123");

        // Act & Assert
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.name").value("Test User"))
                .andExpect(jsonPath("$.id").exists());

        // Verify database
        assertEquals(1, userRepository.count());
    }

    @Test
    void createUser_WithInvalidEmail_ShouldReturn400() throws Exception {
        // Arrange
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("invalid-email");
        request.setName("Test User");
        request.setPassword("password123");

        // Act & Assert
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }
}
```

#### 8.3 Testing Repositories with @DataJpaTest

**Rule:** Use @DataJpaTest for repository tests with in-memory database.

? **Good:**
```java
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByEmail_WithExistingEmail_ShouldReturnUser() {
        // Arrange
        User user = new User();
        user.setEmail("test@example.com");
        user.setName("Test User");
        entityManager.persist(user);
        entityManager.flush();

        // Act
        Optional<User> found = userRepository.findByEmail("test@example.com");

        // Assert
        assertTrue(found.isPresent());
        assertEquals("test@example.com", found.get().getEmail());
    }

    @Test
    void findByEmail_WithNonExistingEmail_ShouldReturnEmpty() {
        // Act
        Optional<User> found = userRepository.findByEmail("nonexistent@example.com");

        // Assert
        assertFalse(found.isPresent());
    }
}
```

#### 8.4 Testing Controllers with @WebMvcTest

**Rule:** Use @WebMvcTest for controller tests without full context.

? **Good:**
```java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getUserById_WithExistingId_ShouldReturnUser() throws Exception {
        // Arrange
        UserDTO user = new UserDTO();
        user.setId(1L);
        user.setEmail("test@example.com");

        when(userService.findById(1L)).thenReturn(Optional.of(user));

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("test@example.com"));

        verify(userService).findById(1L);
    }

    @Test
    void getUserById_WithNonExistingId_ShouldReturn404() throws Exception {
        // Arrange
        when(userService.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/999"))
                .andExpect(status().isNotFound());
    }
}
```

### Task 9: Implement Configuration Management

#### 9.1 External Configuration

**Rule:** Use application.yml for configuration, support multiple profiles.

? **Good application.yml:**
```yaml
spring:
  application:
    name: my-spring-app

  profiles:
    active: dev

  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/myapp}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:password}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect

  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=500,expireAfterAccess=600s

server:
  port: 8080
  error:
    include-message: always
    include-binding-errors: always
    include-stacktrace: on_param
    include-exception: false

logging:
  level:
    root: INFO
    com.example.app: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"

# Custom application properties
app:
  jwt:
    secret: ${JWT_SECRET:default-secret-change-in-production}
    expiration: 3600000
  email:
    from: noreply@example.com
  features:
    new-ui: false
```

**application-dev.yml:**
```yaml
spring:
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: update

logging:
  level:
    com.example.app: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE

app:
  features:
    new-ui: true
```

**application-prod.yml:**
```yaml
spring:
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: validate

logging:
  level:
    root: WARN
    com.example.app: INFO

app:
  jwt:
    expiration: 7200000  # 2 hours in production
```

#### 9.2 @ConfigurationProperties

**Rule:** Use @ConfigurationProperties for type-safe configuration.

? **Good:**
```java
@ConfigurationProperties(prefix = "app")
@Validated
public class AppProperties {

    @NotNull
    private Jwt jwt;

    @NotNull
    private Email email;

    @NotNull
    private Features features;

    @Data
    public static class Jwt {
        @NotBlank
        private String secret;

        @Min(60000)  // At least 1 minute
        private long expiration;
    }

    @Data
    public static class Email {
        @Email
        private String from;
    }

    @Data
    public static class Features {
        private boolean newUi;
    }

    // Getters and setters
}

// Enable configuration properties
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {
}

// Usage
@Service
@RequiredArgsConstructor
public class EmailService {

    private final AppProperties appProperties;

    public void sendEmail(String to, String subject, String body) {
        String from = appProperties.getEmail().getFrom();
        // Send email logic
    }
}
```

### Task 10: Implement Caching

#### 10.1 Enable Caching

**Rule:** Use Spring Cache abstraction for declarative caching.

? **Good:**
```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "users", "products", "orders"
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats());
        return cacheManager;
    }
}

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Cacheable(value = "users", key = "#id")
    public Optional<UserDTO> findById(Long id) {
        return userRepository.findById(id)
                .map(this::mapToDTO);
    }

    @CachePut(value = "users", key = "#result.id")
    public UserDTO updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        user.setName(request.getName());
        User updated = userRepository.save(user);
        return mapToDTO(updated);
    }

    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    @CacheEvict(value = "users", allEntries = true)
    public void clearUserCache() {
        // Cache cleared
    }
}
```

#### 10.2 Cache Key Strategies

**Rule:** Use explicit cache keys for complex scenarios.

? **Good:**
```java
@Service
public class ProductService {

    // Simple key
    @Cacheable(value = "products", key = "#id")
    public Product findById(Long id) {
        // ...
    }

    // Composite key
    @Cacheable(value = "products", key = "#category + '-' + #priceRange")
    public List<Product> findByCategoryAndPrice(String category, String priceRange) {
        // ...
    }

    // Custom KeyGenerator
    @Cacheable(value = "products", keyGenerator = "customKeyGenerator")
    public List<Product> search(ProductSearchCriteria criteria) {
        // ...
    }
}

@Component("customKeyGenerator")
public class CustomKeyGenerator implements org.springframework.cache.interceptor.KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        return target.getClass().getSimpleName() + "_"
             + method.getName() + "_"
             + Arrays.stream(params).map(String::valueOf).collect(Collectors.joining("_"));
    }
}
```

### Task 11: Implement Async Processing

#### 11.1 Enable Async

**Rule:** Use @Async for non-blocking operations.

? **Good:**
```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}

@Service
@RequiredArgsConstructor
public class EmailService {

    @Async("taskExecutor")
    public void sendWelcomeEmail(User user) {
        // Runs asynchronously
        System.out.println("Sending email to: " + user.getEmail());
        // Email sending logic
    }

    @Async("taskExecutor")
    public CompletableFuture<EmailStatus> sendEmailWithResult(String to, String subject, String body) {
        // Async with result
        try {
            // Send email
            return CompletableFuture.completedFuture(EmailStatus.SENT);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(EmailStatus.FAILED);
        }
    }
}
```

#### 11.2 Event-Driven Architecture

**Rule:** Use Spring Events for decoupling components.

? **Good:**
```java
// Event
public class UserRegisteredEvent {
    private final User user;

    public UserRegisteredEvent(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }
}

// Publisher
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UserDTO register(RegistrationRequest request) {
        User user = new User();
        user.setEmail(request.getEmail());
        User saved = userRepository.save(user);

        // Publish event
        eventPublisher.publishEvent(new UserRegisteredEvent(saved));

        return mapToDTO(saved);
    }
}

// Listeners
@Component
@Slf4j
public class UserRegistrationEventListener {

    @Async
    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("User registered: {}", event.getUser().getEmail());
        // Send welcome email
    }
}

@Component
public class AuditEventListener {

    @EventListener
    public void handleUserRegistered(UserRegisteredEvent event) {
        // Log to audit trail
    }
}
```

### Task 12: Implement AOP

#### 12.1 Logging Aspect

**Rule:** Use AOP for cross-cutting concerns like logging, auditing, and performance monitoring.

? **Good:**
```java
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    @Around("execution(* com.example.app.service.*.*(..))")
    public Object logServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        log.debug("Entering {}.{}", className, methodName);

        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            log.debug("Exiting {}.{} - Duration: {}ms", className, methodName, duration);

            return result;
        } catch (Exception e) {
            log.error("Exception in {}.{}: {}", className, methodName, e.getMessage());
            throw e;
        }
    }

    @Before("@annotation(com.example.app.annotation.Audit)")
    public void auditMethod(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        log.info("Audit: Method {} called with args: {}", methodName, Arrays.toString(args));
    }
}

// Custom annotation
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {
}

// Usage
@Service
public class UserService {

    @Audit
    public UserDTO createUser(CreateUserRequest request) {
        // Method will be audited
    }
}
```

---

## Common Anti-Patterns

### Anti-Pattern 1: Field Injection

? **Bad:**
```java
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;  // Field injection
}
```

? **Good:**
```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;  // Constructor injection
}
```

### Anti-Pattern 2: Service Layer Bypassing

? **Bad:**
```java
@RestController
public class UserController {
    @Autowired
    private UserRepository userRepository;  // Controller accessing repository directly

    @GetMapping("/users")
    public List<User> getUsers() {
        return userRepository.findAll();  // Business logic in controller
    }
}
```

? **Good:**
```java
@RestController
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;  // Access through service layer

    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> getUsers() {
        return ResponseEntity.ok(userService.findAll());
    }
}
```

### Anti-Pattern 3: Exposing Entities Directly

? **Bad:**
```java
@RestController
public class UserController {

    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.findById(id);  // Exposing entity
    }
}
```

? **Good:**
```java
@RestController
public class UserController {

    @GetMapping("/users/{id}")
    public ResponseEntity<UserDTO> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));  // Return DTO
    }
}
```

### Anti-Pattern 4: @Transactional on Repository Methods

? **Bad:**
```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Transactional  // Don't put @Transactional on repository
    @Query("UPDATE User u SET u.active = false WHERE u.id = :id")
    void deactivate(@Param("id") Long id);
}
```

? **Good:**
```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional  // Put @Transactional on service methods
    public void deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        user.setActive(false);
        userRepository.save(user);
    }
}
```

### Anti-Pattern 5: Not Using Connection Pooling

? **Bad:**
```yaml
# No connection pooling configuration
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/myapp
```

? **Good:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/myapp
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### Anti-Pattern 6: Too Many Responsibilities in Controllers

? **Bad:**
```java
@RestController
public class UserController {

    @PostMapping("/users")
    public User createUser(@RequestBody User user) {
        // Validation in controller
        if (user.getEmail() == null) {
            throw new RuntimeException("Email required");
        }

        // Business logic in controller
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email exists");
        }

        // Data access in controller
        User saved = userRepository.save(user);

        // Email sending in controller
        emailService.send(user.getEmail(), "Welcome!");

        return saved;
    }
}
```

? **Good:**
```java
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/users")
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserDTO created = userService.createUser(request);
        return ResponseEntity.created(location).body(created);
    }
}

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    @Transactional
    public UserDTO createUser(CreateUserRequest request) {
        // All business logic in service
        validateEmail(request.getEmail());
        User user = createUserEntity(request);
        User saved = userRepository.save(user);
        emailService.sendWelcomeEmail(saved);
        return mapToDTO(saved);
    }
}
```

### Anti-Pattern 7: Storing Business Logic in Entities

? **Bad:**
```java
@Entity
public class Order {

    @Autowired  // Don't inject dependencies in entities!
    private PaymentService paymentService;

    public void process() {
        // Business logic in entity - BAD!
        if (this.total > 1000) {
            paymentService.processLargeOrder(this);
        } else {
            paymentService.processSmallOrder(this);
        }
    }
}
```

? **Good:**
```java
@Entity
public class Order {
    // Pure data model - no business logic
    private Long id;
    private BigDecimal total;
    private OrderStatus status;
    // Getters and setters
}

@Service
@RequiredArgsConstructor
public class OrderService {

    private final PaymentService paymentService;
    private final OrderRepository orderRepository;

    @Transactional
    public void processOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getTotal().compareTo(BigDecimal.valueOf(1000)) > 0) {
            paymentService.processLargeOrder(order);
        } else {
            paymentService.processSmallOrder(order);
        }
    }
}
```

---

## Checklist

Use this checklist during code reviews:

### Dependency Injection
- [ ] Constructor injection used for required dependencies
- [ ] Fields are final where possible
- [ ] No field injection (@Autowired on fields)
- [ ] No circular dependencies
- [ ] Setter injection only for optional dependencies

### Component Structure
- [ ] Correct stereotype annotations (@Service, @Repository, @Controller, @RestController)
- [ ] Controllers are thin (delegate to services)
- [ ] Business logic in service layer
- [ ] Data access through repositories only
- [ ] Proper layering (Controller Би Service Би Repository)

### REST API Design
- [ ] RESTful URL structure
- [ ] Correct HTTP methods (GET, POST, PUT, DELETE)
- [ ] Correct HTTP status codes
- [ ] DTOs used for requests/responses (not entities)
- [ ] @Valid used for request validation
- [ ] Pagination supported for list endpoints
- [ ] Location header for created resources

### Data Access
- [ ] Appropriate repository interface (JpaRepository, etc.)
- [ ] Query methods follow Spring Data naming conventions
- [ ] @Transactional on service methods (not repositories)
- [ ] readOnly=true for read operations
- [ ] Connection pooling configured (HikariCP)
- [ ] No N+1 query problems

### Configuration
- [ ] External configuration in application.yml
- [ ] Profile-specific configuration files
- [ ] @ConfigurationProperties for type-safe config
- [ ] Sensitive data in environment variables
- [ ] Proper defaults for all properties

### Exception Handling
- [ ] Custom exceptions for business errors
- [ ] @RestControllerAdvice for global exception handling
- [ ] Proper HTTP status codes for different exceptions
- [ ] Validation errors handled properly
- [ ] No generic Exception catching without re-throwing

### Security
- [ ] Security configuration in @Configuration class
- [ ] Password encoder configured (BCrypt)
- [ ] Method security enabled where needed
- [ ] CSRF protection enabled (or disabled with justification)
- [ ] JWT properly implemented for stateless auth

### Testing
- [ ] Unit tests for services with mocked dependencies
- [ ] Integration tests with @SpringBootTest
- [ ] Repository tests with @DataJpaTest
- [ ] Controller tests with @WebMvcTest
- [ ] Test coverage > 80%

### Performance
- [ ] Caching enabled where appropriate
- [ ] Async processing for long-running operations
- [ ] Lazy loading configured properly
- [ ] Database indexes on frequently queried fields

### Code Quality
- [ ] No code duplication
- [ ] Meaningful names for classes, methods, variables
- [ ] Methods are focused and not too long
- [ ] Proper logging (not System.out.println)
- [ ] No commented-out code

---

## Related Skills

- **uncle-duke-java**: Java code review agent that uses this skill as reference

---

## References

### Official Documentation
- [Spring Framework Documentation](https://spring.io/projects/spring-framework)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Data JPA Documentation](https://spring.io/projects/spring-data-jpa)
- [Spring Security Documentation](https://spring.io/projects/spring-security)

### Best Practices Guides
- [Baeldung Spring Tutorials](https://www.baeldung.com/spring-tutorial)
- [Spring Boot Best Practices](https://www.baeldung.com/spring-boot-best-practices)
- [RESTful API Design Best Practices](https://www.baeldung.com/rest-api-design-best-practices)

---

**Version:** 1.0
**Last Updated:** 2025-12-24
**Maintainer:** Development Team
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
使用スキル: `spring-framework-patterns`, `typescript-react-reviewer`, `postgresql-code-review`。まず対象ファイルの実在位置と差分の形を拾い、特に AOP 監査、認可 SpEL、例外 DTO、フロント API エラー処理を重点的に読みます。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'rg --files' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git status --short' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "rg -n \"AuditLog|AuditExclude|FinanceAudit|finance_audit_log|LoginUserSecurityBean|isAdmin\\(|FinanceBusinessException|FinanceInternalException|AmountSourceTooltip|ApiError|ResponseStatusException|IllegalStateException\"" in C:\project\odamitsu-data-hub
2026-05-06T05:35:49.078618Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 6.6 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg --files
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 exited 1 in 6600ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg --files
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 succeeded in 7531ms:
 M .claude/settings.local.json
 M backend/src/main/java/jp/co/oda32/api/batch/BatchController.java
 M backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java
 M backend/src/main/java/jp/co/oda32/api/finance/CashBookController.java
 M backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java
 M backend/src/main/java/jp/co/oda32/api/finance/MfClientMappingController.java
 M backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java
 M backend/src/main/java/jp/co/oda32/api/finance/MfJournalRuleController.java
 M backend/src/main/java/jp/co/oda32/api/finance/PaymentMfImportController.java
 M backend/src/main/java/jp/co/oda32/api/master/MasterController.java
 M backend/src/main/java/jp/co/oda32/api/order/OrderController.java
 M backend/src/main/java/jp/co/oda32/api/purchase/QuoteImportController.java
 M backend/src/main/java/jp/co/oda32/api/user/UserController.java
 D backend/src/main/java/jp/co/oda32/batch/bcart/BCartGoodsPriceTableUpdateTasklet.java
 M backend/src/main/java/jp/co/oda32/batch/bcart/BCartOrderConvertSmileOrderFileTasklet.java
 M backend/src/main/java/jp/co/oda32/batch/bcart/SmileDestinationFileOutPutTasklet.java
 M backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableBackfillTasklet.java
 M backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableSummaryInitTasklet.java
 M backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableVerificationReportTasklet.java
 M backend/src/main/java/jp/co/oda32/batch/finance/AccountsReceivableToSalesJournalTasklet.java
 M backend/src/main/java/jp/co/oda32/batch/finance/TAccountsReceivableSummaryTasklet.java
 M backend/src/main/java/jp/co/oda32/batch/finance/config/AccountsPayableAggregationConfig.java
 M backend/src/main/java/jp/co/oda32/batch/finance/config/AccountsPayableSummaryConfig.java
 M backend/src/main/java/jp/co/oda32/batch/finance/config/AccountsPayableVerificationConfig.java
 M backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java
 M backend/src/main/java/jp/co/oda32/batch/finance/service/PayableMonthlyAggregator.java
 M backend/src/main/java/jp/co/oda32/batch/purchase/PurchaseFileProcessor.java
 M backend/src/main/java/jp/co/oda32/batch/purchase/PurchaseFileReader.java
 M backend/src/main/java/jp/co/oda32/batch/purchase/PurchaseFileWriter.java
 M backend/src/main/java/jp/co/oda32/batch/smile/NewSmilePurchaseProcessor.java
 M backend/src/main/java/jp/co/oda32/batch/smile/SmilePurchaseImportService.java
 M backend/src/main/java/jp/co/oda32/batch/smile/SmilePurchaseUpdateService.java
 M backend/src/main/java/jp/co/oda32/batch/smile/UpdateSmilePurchaseProcessor.java
 M backend/src/main/java/jp/co/oda32/constant/FinanceConstants.java
 M backend/src/main/java/jp/co/oda32/domain/model/embeddable/TAccountsReceivableSummaryPK.java
 M backend/src/main/java/jp/co/oda32/domain/model/finance/MMfOauthClient.java
 M backend/src/main/java/jp/co/oda32/domain/model/finance/MPartnerGroup.java
 M backend/src/main/java/jp/co/oda32/domain/model/finance/MPaymentMfRule.java
 M backend/src/main/java/jp/co/oda32/domain/model/finance/TAccountsPayableSummary.java
 M backend/src/main/java/jp/co/oda32/domain/model/finance/TCashbookImportHistory.java
 M backend/src/main/java/jp/co/oda32/domain/model/finance/TMfOauthToken.java
 M backend/src/main/java/jp/co/oda32/domain/model/finance/TPaymentMfImportHistory.java
 M backend/src/main/java/jp/co/oda32/domain/repository/bcart/BCartChangeHistoryRepository.java
 M backend/src/main/java/jp/co/oda32/domain/repository/bcart/DeliveryMappingRepository.java
 M backend/src/main/java/jp/co/oda32/domain/repository/finance/MPartnerGroupRepository.java
 M backend/src/main/java/jp/co/oda32/domain/repository/finance/TAccountsPayableSummaryRepository.java
 M backend/src/main/java/jp/co/oda32/domain/repository/finance/TInvoiceRepository.java
 M backend/src/main/java/jp/co/oda32/domain/repository/finance/TMfOauthStateRepository.java
 M backend/src/main/java/jp/co/oda32/domain/repository/finance/TPaymentMfImportHistoryRepository.java
 M backend/src/main/java/jp/co/oda32/domain/repository/smile/WSmilePurchaseOutputFileRepository.java
 M backend/src/main/java/jp/co/oda32/domain/service/bcart/DeliveryMappingService.java
 M backend/src/main/java/jp/co/oda32/domain/service/data/LoginUser.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/AccountingStatusService.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableLedgerService.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/CashBookConvertService.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/InvoiceImportService.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/MPartnerGroupService.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/MfHealthCheckService.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/MfTaxResolver.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfCellReader.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfCsvWriter.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParser.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfRuleService.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/PurchaseJournalCsvService.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/SalesJournalCsvService.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsReceivableSummaryService.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/TInvoiceService.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfAccount.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfAccountsResponse.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfJournalFetcher.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthStateStore.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfPaymentAggregator.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfSupplierLedgerService.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfTokenResponse.java
 M backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfTokenStatus.java
 M backend/src/main/java/jp/co/oda32/domain/service/master/MPartnerService.java
 M backend/src/main/java/jp/co/oda32/domain/service/util/LoginUserUtil.java
 M backend/src/main/java/jp/co/oda32/dto/bcart/BCartProductResponse.java
 M backend/src/main/java/jp/co/oda32/dto/estimate/EstimateResponse.java
 M backend/src/main/java/jp/co/oda32/dto/finance/AccountsPayableVerifyRequest.java
 M backend/src/main/java/jp/co/oda32/dto/finance/AccountsReceivableVerifyRequest.java
 M backend/src/main/java/jp/co/oda32/dto/finance/BulkPaymentDateRequest.java
 M backend/src/main/java/jp/co/oda32/dto/finance/InvoiceResponse.java
 M backend/src/main/java/jp/co/oda32/dto/finance/MfHealthResponse.java
 M backend/src/main/java/jp/co/oda32/dto/finance/MfSupplierLedgerResponse.java
 M backend/src/main/java/jp/co/oda32/dto/finance/PaymentDateUpdateRequest.java
 M backend/src/main/java/jp/co/oda32/dto/finance/paymentmf/PaymentMfPreviewResponse.java
 M backend/src/main/java/jp/co/oda32/dto/finance/paymentmf/PaymentMfPreviewRow.java
 M backend/src/main/resources/config/application-dev.yml
 M backend/src/main/resources/config/application-prod.yml
 M backend/src/main/resources/db/migration/V008__create_mf_cashbook_tables.sql
 M backend/src/test/java/jp/co/oda32/TestApplication.java
 M backend/src/test/java/jp/co/oda32/domain/service/finance/InvoiceImportServiceTest.java
 M backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfImportServiceAuxRowTest.java
 M backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfImportServiceGoldenMasterTest.java
 M backend/src/test/resources/config/application-test.yml
 M claudedocs/design-accounts-receivable-mf.md
 M claudedocs/design-mf-cashbook-import.md
 M claudedocs/design-mf-integration-status.md
 M claudedocs/design-payment-mf-aux-rows.md
 M claudedocs/design-payment-mf-import.md
 M docs/08_bcart_integration.md
 M frontend/components/features/common/DataTable.tsx
 M frontend/components/layout/AppLayout.tsx
 M frontend/components/layout/Sidebar.tsx
 M frontend/components/pages/bcart/product-detail.tsx
 M frontend/components/pages/bcart/products.tsx
 M frontend/components/pages/estimate-comparison/ComparisonGroupForm.tsx
 M frontend/components/pages/estimate/EstimateHeaderForm.tsx
 M frontend/components/pages/estimate/detail.tsx
 M frontend/components/pages/estimate/form.tsx
 M frontend/components/pages/finance/AccountsReceivableVerifyDialog.tsx
 M frontend/components/pages/finance/InvoiceImportDialog.tsx
 M frontend/components/pages/finance/PartnerGroupDialog.tsx
 M frontend/components/pages/finance/PaymentMfAuxRowsTable.tsx
 M frontend/components/pages/finance/accounting-workflow.tsx
 M frontend/components/pages/finance/accounts-payable-ledger.tsx
 M frontend/components/pages/finance/accounts-payable.tsx
 M frontend/components/pages/finance/accounts-receivable.tsx
 M frontend/components/pages/finance/cashbook-import.tsx
 M frontend/components/pages/finance/integrity-report.tsx
 M frontend/components/pages/finance/invoices.tsx
 M frontend/components/pages/finance/mf-client-mappings.tsx
 M frontend/components/pages/finance/mf-health.tsx
 M frontend/components/pages/finance/mf-integration-callback.tsx
 M frontend/components/pages/finance/mf-integration.tsx
 M frontend/components/pages/finance/mf-journal-rules.tsx
 M frontend/components/pages/finance/payment-mf-import.tsx
 M frontend/components/pages/finance/payment-mf-rules.tsx
 M frontend/components/pages/finance/supplier-balances.tsx
 M frontend/components/pages/order/index.tsx
 M frontend/e2e/estimate-form.spec.ts
 M frontend/e2e/finance-workflow.spec.ts
 M frontend/hooks/use-print-with-status-update.ts
 M frontend/lib/utils.ts
 M frontend/types/accounts-payable-ledger.ts
 M frontend/types/accounts-payable.ts
 M frontend/types/accounts-receivable.ts
 M frontend/types/bcart.ts
 M frontend/types/estimate.ts
 M frontend/types/mf-cashbook.ts
 M frontend/types/mf-health.ts
 M frontend/types/mf-integration.ts
 M frontend/types/payment-mf.ts
?? SQL/
?? _query_residual.sql
?? backend/_int.json
?? backend/_int11m.json
?? backend/_int6m.json
?? backend/_int_after.json
?? backend/_mfj_6445.json
?? backend/_sb_2510.json
?? backend/scripts/
?? backend/src/main/java/db/
?? backend/src/main/java/jp/co/oda32/api/admin/
?? backend/src/main/java/jp/co/oda32/api/bcart/BCartPendingChangesController.java
?? backend/src/main/java/jp/co/oda32/api/bcart/BCartProductSetsController.java
?? backend/src/main/java/jp/co/oda32/api/finance/FinanceExceptionHandler.java
?? backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationDebugController.java
?? backend/src/main/java/jp/co/oda32/api/finance/SupplierOpeningBalanceController.java
?? backend/src/main/java/jp/co/oda32/audit/
?? backend/src/main/java/jp/co/oda32/batch/BatchJobCatalog.java
?? backend/src/main/java/jp/co/oda32/constant/FinancePeriodConfig.java
?? backend/src/main/java/jp/co/oda32/domain/model/audit/
?? backend/src/main/java/jp/co/oda32/domain/model/embeddable/MSupplierOpeningBalancePK.java
?? backend/src/main/java/jp/co/oda32/domain/model/finance/CutoffType.java
?? backend/src/main/java/jp/co/oda32/domain/model/finance/MMfEnumTranslation.java
?? backend/src/main/java/jp/co/oda32/domain/model/finance/MSupplierOpeningBalance.java
?? backend/src/main/java/jp/co/oda32/domain/repository/audit/
?? backend/src/main/java/jp/co/oda32/domain/repository/finance/MMfEnumTranslationRepository.java
?? backend/src/main/java/jp/co/oda32/domain/repository/finance/MSupplierOpeningBalanceRepository.java
?? backend/src/main/java/jp/co/oda32/domain/service/audit/
?? backend/src/main/java/jp/co/oda32/domain/service/bcart/BCartPendingChangesQueryService.java
?? backend/src/main/java/jp/co/oda32/domain/service/bcart/BCartProductSetsPricingService.java
?? backend/src/main/java/jp/co/oda32/domain/service/bcart/BCartProductSetsReflectService.java
?? backend/src/main/java/jp/co/oda32/domain/service/bcart/BCartReflectTransactionService.java
?? backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsReceivableBulkVerifyService.java
?? backend/src/main/java/jp/co/oda32/domain/service/finance/MfPeriodConstants.java
?? backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfAccountSyncService.java
?? backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfDebugApiClient.java
?? backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfEnumTranslationService.java
?? backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfHttpClientConfig.java
?? backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfJournal.java
?? backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfJournalReconcileService.java
?? backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfJournalsResponse.java
?? backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningBalanceService.java
?? backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningJournalDetector.java
?? backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfScopeConstants.java
?? backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfTax.java
?? backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfTaxesResponse.java
?? backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfTenantMismatchException.java
?? backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfTenantResponse.java
?? backend/src/main/java/jp/co/oda32/dto/audit/
?? backend/src/main/java/jp/co/oda32/dto/bcart/BCartPendingChangeResponse.java
?? backend/src/main/java/jp/co/oda32/dto/bcart/BCartProductSetPricingRequest.java
?? backend/src/main/java/jp/co/oda32/dto/bcart/BCartProductSetPricingResponse.java
?? backend/src/main/java/jp/co/oda32/dto/bcart/BCartReflectRequest.java
?? backend/src/main/java/jp/co/oda32/dto/bcart/BCartReflectResult.java
?? backend/src/main/java/jp/co/oda32/dto/finance/AccountingStatusResponse.java
?? backend/src/main/java/jp/co/oda32/dto/finance/AccountsPayableSummaryProjection.java
?? backend/src/main/java/jp/co/oda32/dto/finance/ErrorResponse.java
?? backend/src/main/java/jp/co/oda32/dto/finance/MfEnumTranslationRequest.java
?? backend/src/main/java/jp/co/oda32/dto/finance/MfEnumTranslationResponse.java
?? backend/src/main/java/jp/co/oda32/dto/finance/MfOpeningBalanceFetchResponse.java
?? backend/src/main/java/jp/co/oda32/dto/finance/SupplierOpeningBalanceResponse.java
?? backend/src/main/java/jp/co/oda32/dto/finance/SupplierOpeningBalanceUpdateRequest.java
?? backend/src/main/java/jp/co/oda32/dto/finance/paymentmf/AppliedWarning.java
?? backend/src/main/java/jp/co/oda32/dto/finance/paymentmf/DuplicateWarning.java
?? backend/src/main/java/jp/co/oda32/exception/FinanceBusinessException.java
?? backend/src/main/java/jp/co/oda32/exception/FinanceInternalException.java
?? backend/src/main/java/jp/co/oda32/security/
?? backend/src/main/java/jp/co/oda32/util/OauthCryptoUtil.java
?? backend/src/main/java/jp/co/oda32/util/SensitiveLogMasker.java
?? backend/src/main/resources/db/migration/V022__create_mf_enum_translation.sql
?? backend/src/main/resources/db/migration/V023__alter_accounts_payable_summary_add_mf_transfer_date.sql
?? backend/src/main/resources/db/migration/V028__create_supplier_opening_balance.sql
?? backend/src/main/resources/db/migration/V029__fix_delivery_mapping_smile_code.sql
?? backend/src/main/resources/db/migration/V030__deprecate_old_payable_summary_jobs.sql
?? backend/src/main/resources/db/migration/V031__create_t_invoice_and_m_partner_group.sql
?? backend/src/main/resources/db/migration/V032__add_mf_tenant_binding.sql
?? backend/src/main/resources/db/migration/V034__payment_mf_import_history_protection.sql
?? backend/src/main/resources/db/migration/V035__document_verified_amount_invariant.sql
?? backend/src/main/resources/db/migration/V036__finance_audit_log.sql
?? backend/src/test/java/jp/co/oda32/api/finance/
?? backend/src/test/java/jp/co/oda32/audit/
?? backend/src/test/java/jp/co/oda32/batch/
?? backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfImportServiceVerifiedAmountTest.java
?? backend/src/test/java/jp/co/oda32/domain/service/finance/SupplierBalancesServiceGoldenTest.java
?? backend/src/test/java/jp/co/oda32/domain/service/finance/mf/
?? backend/src/test/java/jp/co/oda32/security/
?? claudedocs/.codex-prompt-A.txt
?? claudedocs/.codex-prompt-C.txt
?? claudedocs/.codex-prompt-D.txt
?? claudedocs/.codex-prompt-E.txt
?? claudedocs/.codex-prompt-F.txt
?? claudedocs/.codex-prompt-group1-mf.txt
?? claudedocs/.codex-prompt-group2-business.txt
?? claudedocs/.codex-prompt-group3-crosscutting.txt
?? claudedocs/.codex-prompt-group4-test.txt
?? claudedocs/DESIGN-DECISIONS-aggregated.md
?? claudedocs/REVIEW-SUMMARY-finance-clusters.md
?? claudedocs/REVIEW-SUMMARY-finance-fixes.md
?? claudedocs/_spike_mf_opening_0621.json
?? claudedocs/_spike_mf_opening_wide.json
?? claudedocs/code-review-accounting-workflow.md
?? claudedocs/code-review-accounts-payable-family-round2.md
?? claudedocs/code-review-accounts-payable-family.md
?? claudedocs/code-review-accounts-receivable-round2.md
?? claudedocs/code-review-accounts-receivable.md
?? claudedocs/code-review-bcart-pending-changes.md
?? claudedocs/code-review-bgh-clusters-round2.md
?? claudedocs/code-review-invoice-management-round2.md
?? claudedocs/code-review-invoice-management.md
?? claudedocs/code-review-mf-cashbook-import.md
?? claudedocs/code-review-mf-integration-round2.md
?? claudedocs/code-review-mf-integration.md
?? claudedocs/code-review-payment-mf-import-round2.md
?? claudedocs/code-review-payment-mf-import.md
?? claudedocs/code-review-supplier-opening-balance.md
?? claudedocs/codex-adversarial-accounts-payable-family.md
?? claudedocs/codex-adversarial-accounts-receivable.md
?? claudedocs/codex-adversarial-group1-mf.md
?? claudedocs/codex-adversarial-group2-business.md
?? claudedocs/codex-adversarial-group3-crosscutting.md
?? claudedocs/codex-adversarial-group4-test.md
?? claudedocs/codex-adversarial-invoice-management.md
?? claudedocs/codex-adversarial-mf-integration.md
?? claudedocs/codex-adversarial-payment-mf-import.md
?? claudedocs/design-accounting-workflow.md
?? claudedocs/design-audit-trail-accounts-payable.md
?? claudedocs/design-auth-matrix.md
?? claudedocs/design-bcart-pending-changes.md
?? claudedocs/design-delivery-code-mapping-fix.md
?? claudedocs/design-estimate-destination-preselect.md
?? claudedocs/design-exception-hierarchy.md
?? claudedocs/design-finance-audit-log.md
?? claudedocs/design-invoice-management.md
?? claudedocs/design-review-accounting-workflow.md
?? claudedocs/design-review-accounts-payable-family.md
?? claudedocs/design-review-accounts-receivable.md
?? claudedocs/design-review-bcart-pending-changes.md
?? claudedocs/design-review-invoice-management.md
?? claudedocs/design-review-mf-cashbook-import.md
?? claudedocs/design-review-mf-integration.md
?? claudedocs/design-review-payment-mf-import.md
?? claudedocs/design-review-supplier-opening-balance.md
?? claudedocs/design-source-of-truth-hierarchy.md
?? claudedocs/design-supplier-opening-balance.md
?? claudedocs/handover-buying-integrity.md
?? claudedocs/runbook-finance-recalc-impact-analysis.md
?? claudedocs/runbook-mf-oauth-keys.md
?? claudedocs/runbook-v031-baseline.md
?? claudedocs/test-plan-bcart-pending-changes.md
?? claudedocs/test-plan-delivery-code-mapping-fix.md
?? claudedocs/test-plan-review-bcart-pending-changes.md
?? claudedocs/testplan-estimate-destination-preselect.md
?? claudedocs/triage-accounts-payable-family.md
?? claudedocs/triage-accounts-receivable.md
?? claudedocs/triage-bgh-clusters.md
?? claudedocs/triage-invoice-management.md
?? claudedocs/triage-mf-integration.md
?? claudedocs/triage-payment-mf-import.md
?? frontend/_int_rec.json
?? frontend/_mf_202505.json
?? frontend/_mf_202508.json
?? frontend/_mf_202511.json
?? frontend/_mfj.json
?? frontend/_mfj_2419.json
?? frontend/_mfj_4700.json
?? frontend/_mfj_5757.json
?? frontend/_review_post.json
?? frontend/app/(authenticated)/admin/
?? frontend/app/(authenticated)/bcart/pending-changes/
?? frontend/app/(authenticated)/finance/supplier-opening-balance/
?? frontend/components/common/
?? frontend/components/pages/admin/
?? frontend/components/pages/bcart/pending-changes.tsx
?? frontend/components/pages/finance/ConsistencyReviewDialog.tsx
?? frontend/components/pages/finance/MfAccountSyncTab.tsx
?? frontend/components/pages/finance/MfEnumTranslationTab.tsx
?? frontend/components/pages/finance/MfReconcileTab.tsx
?? frontend/components/pages/finance/supplier-opening-balance.tsx
?? frontend/components/ui/alert.tsx
?? frontend/e2e/mf-integration.spec.ts
?? frontend/lib/payment-type.ts
?? frontend/types/audit-log.ts
?? frontend/types/partner-group.ts
?? frontend/types/supplier-opening-balance.ts
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
warning: unable to access 'C:\Users\kazuki/.config/git/ignore': Permission denied
warning: unable to access 'C:\Users\kazuki/.config/git/ignore': Permission denied

2026-05-06T05:35:52.724242Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 10.2 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg -n "AuditLog|AuditExclude|FinanceAudit|finance_audit_log|LoginUser 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 exited 1 in 10231ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg -n "AuditLog|AuditExclude|FinanceAudit|finance_audit_log|LoginUser 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

codex
この環境では `rg` がありませんでした。PowerShell の `Get-ChildItem` / `Select-String` に切り替えて、対象ファイルだけに絞って読みます。
2026-05-06T05:36:03.942411Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File -Path backend/src/main/java/jp/co/oda32/audit,backend/src/main/java/jp/co/oda32/domain/model/audit,backend/src/main/java/jp/co/oda32/domain/repository/audit,backend/src/main/java/jp/co/oda32/domain/service/audit,backend/src/main/java/jp/co/oda32/dto/audit,backend/src/main/java/jp/co/oda32/security,backend/src/main/java/jp/co/oda32/exception,backend/src/main/java/jp/co/oda32/api/admin,frontend/components/common,frontend/components/pages/admin,frontend/types | Select-Object -ExpandProperty FullName'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File -Path backend/src/main/java/jp/co/oda32/audit,backend/src/main/java/jp/co/oda32/domain/model/audit,backend/src/main/java/jp/co/oda32/domain/repository/audit,backend/src/main/java/jp/co/oda32/domain/service/audit,backend/src/main/java/jp/co/oda32/dto/audit,backend/src/main/java/jp/co/oda32/security,backend/src/main/java/jp/co/oda32/exception,backend/src/main/java/jp/co/oda32/api/admin,frontend/components/common,frontend/components/pages/admin,frontend/types | Select-Object -ExpandProperty FullName' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File -Path backend/src/main/resources/db/migration | Where-Object { $_.Name -like '"'V036*' } | ForEach-Object { "'$_.FullName }' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File -Path backend/src/main/java/jp/co/oda32/audit,backend/src/main/java/jp/co/oda32/domain/model/audit,backend/src/main/java/jp/co/oda32/domain/repository/audit,backend/src/main/java/jp/co/oda32/domain/service/audit,backend/src/main/java/jp/co/oda32/dto/audit,backend/src/main/java/jp/co/oda32/security,backend/src/main/java/jp/co/oda32/exception,backend/src/main/java/jp/co/oda32/api/admin,frontend/components/common,frontend/components/pages/admin,frontend/types | Select-Object -ExpandProperty FullName'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File -Path claudedocs | Where-Object { $_.Name -in @('"'design-source-of-truth-hierarchy.md','design-finance-audit-log.md','design-auth-matrix.md','design-exception-hierarchy.md') } | Select-Object -ExpandProperty FullName" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File -Path backend/src/main/resources/db/migration | Where-Object { $_.Name -like '"'V036*' } | ForEach-Object { "'$_.FullName }'` rejected: blocked by policy
2026-05-06T05:36:03.943870Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File -Path backend/src/main/resources/db/migration | Where-Object { $_.Name -like '"'V036*' } | ForEach-Object { "'$_.FullName }'` rejected: blocked by policy
2026-05-06T05:36:03.944599Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File -Path claudedocs | Where-Object { $_.Name -in @('"'design-source-of-truth-hierarchy.md','design-finance-audit-log.md','design-auth-matrix.md','design-exception-hierarchy.md') } | Select-Object -ExpandProperty FullName"` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File -Path claudedocs | Where-Object { $_.Name -in @('"'design-source-of-truth-hierarchy.md','design-finance-audit-log.md','design-auth-matrix.md','design-exception-hierarchy.md') } | Select-Object -ExpandProperty FullName"` rejected: blocked by policy
2026-05-06T05:36:03.947167Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Recurse -File -Path backend/src/main/java,frontend | Select-String -Pattern 'AuditLog|AuditExclude|loginUserSecurityBean|hasRole\\(''ADMIN''\\)|principal\\.shopNo|FinanceBusinessException|FinanceInternalException|ResponseStatusException|IllegalStateException|AmountSourceTooltip|ApiError' | Select-Object Path,LineNumber,Line | Format-Table -AutoSize -Wrap"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Recurse -File -Path backend/src/main/java,frontend | Select-String -Pattern 'AuditLog|AuditExclude|loginUserSecurityBean|hasRole\\(''ADMIN''\\)|principal\\.shopNo|FinanceBusinessException|FinanceInternalException|ResponseStatusException|IllegalStateException|AmountSourceTooltip|ApiError' | Select-Object Path,LineNumber,Line | Format-Table -AutoSize -Wrap" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Recurse -File -Path backend/src/main/java,frontend | Select-String -Pattern 'AuditLog|AuditExclude|loginUserSecurityBean|hasRole\\(''ADMIN''\\)|principal\\.shopNo|FinanceBusinessException|FinanceInternalException|ResponseStatusException|IllegalStateException|AmountSourceTooltip|ApiError' | Select-Object Path,LineNumber,Line | Format-Table -AutoSize -Wrap"` rejected: blocked by policy
codex
並列ラッパー側のポリシーで PowerShell コマンドが弾かれたため、単発の短いコマンドに分けます。まず監査・認可・例外の主要ファイルを直接開きます。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/audit/FinanceAuditAspect.java' in C:\project\odamitsu-data-hub
 succeeded in 3342ms:
package jp.co.oda32.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jp.co.oda32.annotation.ApplicationType;
import jp.co.oda32.domain.service.data.LoginUser;
import lombok.extern.log4j.Log4j2;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * T2: {@link AuditLog} 付き Service メソッドを横断し、呼び出し前後の引数・戻り値を
 * {@code finance_audit_log} に JSONB で記録する Aspect。
 * <p>
 * <b>web プロファイル限定</b>: バッチ実行では SecurityContext / HttpServletRequest が
 * 取れないため、本 Aspect は web プロファイルのみで起動する (バッチ操作の監査は
 * 将来 Step Listener 等で別途対応)。
 * <p>
 * <b>Transactional 分離</b>: 実書き込みは {@link FinanceAuditWriter#write} に委譲し
 * REQUIRES_NEW で別 tx 化。これにより業務 tx rollback でも失敗操作の証跡が残る。
 *
 * @since 2026-05-04 (T2)
 */
@Aspect
@Component
@Log4j2
@ApplicationType("web")
public class FinanceAuditAspect {

    private final FinanceAuditWriter writer;
    private final ObjectProvider<HttpServletRequest> httpRequestProvider;
    private final ObjectMapper auditMapper;

    public FinanceAuditAspect(
            FinanceAuditWriter writer,
            ObjectProvider<HttpServletRequest> httpRequestProvider) {
        this.writer = writer;
        this.httpRequestProvider = httpRequestProvider;
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
        Object[] args = pjp.getArgs();
        Object pkArg = (auditLog.pkArgIndex() >= 0 && args.length > auditLog.pkArgIndex())
                ? args[auditLog.pkArgIndex()]
                : null;

        JsonNode pkJson = serialize(pkArg);
        if (pkJson == null) {
            // PK が null でも "what was called with null" を残すために空オブジェクトを書く
            pkJson = auditMapper.createObjectNode();
        }

        JsonNode argsJson = auditLog.captureArgsAsAfter() ? serialize(args) : null;
        AuthInfo authInfo = resolveAuth();
        ReqInfo reqInfo = resolveRequest();

        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable ex) {
            try {
                writer.write(auditLog.table(), auditLog.operation(),
                        authInfo.userNo, authInfo.actorType,
                        pkJson, argsJson, null,
                        "FAILED: " + ex.getClass().getSimpleName() + ": "
                                + truncate(ex.getMessage(), 800),
                        reqInfo.ip, reqInfo.userAgent);
            } catch (Exception logEx) {
                log.error("[finance-audit] failed-path 監査ログ記録に失敗 (元例外を優先 throw): {}",
                        logEx.toString());
            }
            throw ex;
        }

        JsonNode afterJson = null;
        if (auditLog.captureReturnAsAfter()) {
            afterJson = serialize(result);
        } else if (auditLog.captureArgsAsAfter()) {
            afterJson = argsJson;
        }

        try {
            writer.write(auditLog.table(), auditLog.operation(),
                    authInfo.userNo, authInfo.actorType,
                    pkJson, argsJson, afterJson,
                    null, reqInfo.ip, reqInfo.userAgent);
        } catch (Exception logEx) {
            // 監査ログ書込み失敗は業務 tx の成功を巻き戻さない (記録漏れだけ警告)。
            log.error("[finance-audit] success-path 監査ログ記録に失敗: target={}, op={}, err={}",
                    auditLog.table(), auditLog.operation(), logEx.toString());
        }

        return result;
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
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/audit/AuditLog.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/audit/AuditExclude.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/audit/FinanceAuditWriter.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/audit/FinanceAuditLog.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/resources/db/migration/V036__finance_audit_log.sql' in C:\project\odamitsu-data-hub
 succeeded in 2196ms:
package jp.co.oda32.audit;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * T2: 監査ログ JSON シリアライズから除外するフィールド。
 * <p>
 * トークン文字列・暗号化済み秘密情報・大きい blob (CSV 本体, Excel バイト列) などに付与すると
 * {@link FinanceAuditAspect} が JSONB 書き込み時に値を出力しない。
 * <p>
 * 内部的には Jackson の {@link JsonIgnore} を再エクスポートしているため、
 * 通常の REST レスポンス用 ObjectMapper でも値が外れる点に留意 (今回は finance Entity に
 * 直接付けるため、API 経由でも返却されない -- これは意図した挙動)。
 *
 * @since 2026-05-04 (T2)
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonIgnore
public @interface AuditExclude {}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2236ms:
package jp.co.oda32.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * T2: finance Service 層メソッドの監査ログ記録マーカー。
 * <p>
 * 付与すると {@link FinanceAuditAspect} が呼び出し前後の Entity スナップショットを
 * {@code finance_audit_log} に JSONB で書き込む。
 * <p>
 * <b>制約</b>:
 * <ul>
 *   <li>Service の public メソッドにのみ付与する (Spring AOP proxy 経由のみ AOP が起動するため)</li>
 *   <li>同一クラス内 self 呼び出し (this.method()) では起動しない。Controller / 別 Service 経由必須</li>
 *   <li>{@link #pkArgIndex()} の引数が {@code null} の場合、before snapshot は空 (INSERT 想定)</li>
 *   <li>before snapshot のために PK で再 fetch する場合は別途 {@code AuditEntityLoader} を実装すること</li>
 * </ul>
 *
 * @since 2026-05-04 (T2)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 監査対象テーブル名 (例: "t_accounts_payable_summary"). */
    String table();

    /**
     * 操作種別 (例: "verify", "mf_apply", "import", "INSERT", "UPDATE", "DELETE").
     * 監査ログ画面のフィルタ・分析で使う。
     */
    String operation();

    /**
     * Service メソッドの何番目の引数を PK として記録するか。
     * 既定 0 (第 1 引数)。
     * 引数が単一型 (例: Integer id) の場合はそのまま JSONB シリアライズ。
     * 引数が DTO の場合は DTO の全フィールド (もしくは @JsonProperty 指定) を JSONB 化。
     */
    int pkArgIndex() default 0;

    /**
     * 戻り値を after snapshot として記録するか。
     * <ul>
     *   <li>true: メソッドが返す Entity / DTO を after_values に保存</li>
     *   <li>false: after は記録しない (failed/void 操作向け)</li>
     * </ul>
     */
    boolean captureReturnAsAfter() default true;

    /**
     * 全引数を before/after の補助情報として記録するか。
     * true の場合、target_pk と after_values に args の JSON 表現も含む。
     * 単純な ID 削除等で「何の値で呼ばれたか」を残したい時に使う。
     */
    boolean captureArgsAsAfter() default false;
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2308ms:
package jp.co.oda32.domain.model.audit;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

/**
 * T2: finance Service 層の監査証跡。
 * <p>
 * {@code @AuditLog} アノテーション付き Service メソッド呼び出しごとに 1 行記録される。
 * before/after はそれぞれ JSONB で保存し、@AuditExclude フィールドは出力されない。
 *
 * @since 2026-05-04 (T2)
 */
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
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2307ms:
-- T2: 監査証跡基盤
-- 設計書: claudedocs/design-finance-audit-log.md (T1 と組み合わせて運用)
-- @AuditLog アノテーション付き Service メソッドの呼び出し前後を記録する。
-- AOP (FinanceAuditAspect) が before/after の Entity スナップショットを JSONB で保存する。

CREATE TABLE finance_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    occurred_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_user_no   INTEGER,                                  -- NULL = SYSTEM/BATCH
    actor_type      VARCHAR(20) NOT NULL DEFAULT 'USER',      -- USER / SYSTEM / BATCH
    operation       VARCHAR(50) NOT NULL,                     -- INSERT / UPDATE / DELETE / verify / mf_apply ...
    target_table    VARCHAR(100) NOT NULL,
    target_pk       JSONB NOT NULL,
    before_values   JSONB,
    after_values    JSONB,
    reason          TEXT,
    source_ip       VARCHAR(45),
    user_agent      VARCHAR(500)
);

CREATE INDEX idx_finance_audit_log_target ON finance_audit_log (target_table, occurred_at DESC);
CREATE INDEX idx_finance_audit_log_actor ON finance_audit_log (actor_user_no, occurred_at DESC);
CREATE INDEX idx_finance_audit_log_occurred ON finance_audit_log (occurred_at DESC);

COMMENT ON TABLE finance_audit_log IS
    'T2: finance Service 層の監査証跡。@AuditLog アノテーション付きメソッドが自動記録。';
COMMENT ON COLUMN finance_audit_log.actor_user_no IS
    '操作実行者 (m_login_user.login_user_no)。NULL = SYSTEM/BATCH';
COMMENT ON COLUMN finance_audit_log.actor_type IS
    'USER / SYSTEM / BATCH。AOP は SecurityContext 取得可否で USER/SYSTEM を判定する';
COMMENT ON COLUMN finance_audit_log.target_pk IS
    '複合 PK 対応 JSONB (例: {"shopNo":1,"supplierNo":123,"transactionMonth":"2026-04-20","taxRate":10})';
COMMENT ON COLUMN finance_audit_log.before_values IS
    'UPDATE/DELETE 時の変更前値 JSONB (大きなフィールド・@AuditExclude 付与フィールドは除外)';
COMMENT ON COLUMN finance_audit_log.after_values IS
    'INSERT/UPDATE 時の変更後値 JSONB';
COMMENT ON COLUMN finance_audit_log.reason IS
    '失敗 (FAILED:<msg>) または操作理由 (note 等)';
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2320ms:
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
                .build();
        repository.save(entry);
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/data/LoginUser.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/security/LoginUserSecurityBean.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/api/finance/FinanceExceptionHandler.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/exception/FinanceBusinessException.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/exception/FinanceInternalException.java' in C:\project\odamitsu-data-hub
 succeeded in 1808ms:
package jp.co.oda32.api.finance;

import jp.co.oda32.domain.service.finance.mf.MfReAuthRequiredException;
import jp.co.oda32.domain.service.finance.mf.MfScopeInsufficientException;
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
     * @since 2026-05-04 (T5)
     */
    @ExceptionHandler(FinanceBusinessException.class)
    public ResponseEntity<ErrorResponse> handleFinanceBusiness(FinanceBusinessException ex) {
        log.info("Finance business exception: code={}, message={}", ex.getErrorCode(), ex.getMessage());
        String code = ex.getErrorCode() != null ? ex.getErrorCode() : "BUSINESS_ERROR";
        return ResponseEntity.badRequest()
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

 succeeded in 1889ms:
package jp.co.oda32.exception;

/**
 * Finance 内部例外: 機微情報を含む可能性のある内部状態異常。
 *
 * <p>HTTP 422 Unprocessable Entity にマップされ、message は <strong>client には汎用化されて返る</strong>
 * (情報漏洩防止)。詳細メッセージはサーバーログのみ記録。
 *
 * <p>例:
 * <ul>
 *   <li>「DB に予期しない null が含まれています: supplier_no=123」(= supplier 内部 ID 露出防止)</li>
 *   <li>「集計バッチの内部状態が不整合: ...」(= バッチ実装詳細露出防止)</li>
 *   <li>「MF API レスポンスのパース失敗: {raw body}」(= API キー含む可能性)</li>
 * </ul>
 *
 * <p>業務メッセージをユーザーに伝えたい場合は {@link FinanceBusinessException} を使うこと。
 *
 * @since 2026-05-04 (T5)
 */
public class FinanceInternalException extends RuntimeException {

    public FinanceInternalException(String message) {
        super(message);
    }

    public FinanceInternalException(String message, Throwable cause) {
        super(message, cause);
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2800ms:
package jp.co.oda32.security;

import jp.co.oda32.domain.service.data.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 認可マトリクス統一 (T4) の SpEL 用 Bean。
 *
 * <p>{@code @PreAuthorize("@loginUserSecurityBean.isAdmin()")} の形で全 Controller
 * から admin 判定を統一的に呼び出す。
 *
 * <p>従来は以下 3 系統が並存していた:
 * <ol>
 *   <li>{@code @PreAuthorize("hasRole('ADMIN')")} ? Spring Security Role 経由</li>
 *   <li>{@code @PreAuthorize("authentication.principal.shopNo == 0")} ? shopNo 直接</li>
 *   <li>service 層での {@code LoginUserUtil.resolveEffectiveShopNo(shopNo)} 呼出</li>
 * </ol>
 * 本 Bean は (1)(2) を {@link LoginUser#isAdmin()} の単一定義に集約する。
 * (3) は別目的 (shop user の自 shop ガード) であり継続使用。
 *
 * @see LoginUser#isAdmin()
 */
@Component("loginUserSecurityBean")
public class LoginUserSecurityBean {

    /**
     * 現在の認証 principal が admin (shopNo=0 かつ ROLE_ADMIN 保有) かを返す。
     *
     * <p>未認証 / principal が {@link LoginUser} でない場合は false (fail-safe)。
     */
    public boolean isAdmin() {
        LoginUser loginUser = currentLoginUser();
        return loginUser != null && loginUser.isAdmin();
    }

    /**
     * 認証済かつ admin でないこと (= shop user / partner user) を返す。
     *
     * <p>未認証 / principal が {@link LoginUser} でない場合は false。
     */
    public boolean isShopUser() {
        LoginUser loginUser = currentLoginUser();
        if (loginUser == null || loginUser.getUser() == null) return false;
        Integer shopNo = loginUser.getUser().getShopNo();
        return shopNo != null && shopNo != 0;
    }

    private LoginUser currentLoginUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3736ms:
package jp.co.oda32.exception;

/**
 * Finance 業務例外: ユーザーに伝えるべき業務メッセージを保持する例外。
 *
 * <p>HTTP 400 Bad Request にマップされ、message はそのまま client に返る。
 * 例: 「対象取引月にデータがありません」「請求金額が 0 円です」「未登録の支払先です」等
 *
 * <p>機微情報を含むメッセージには使わないこと (= {@link FinanceInternalException} を使う)。
 *
 * <p>関連:
 * <ul>
 *   <li>T5 (本クラス): 例外ハンドリング統一</li>
 *   <li>{@link FinanceInternalException}: 内部エラー (機微情報含む可能性、汎用化される)</li>
 *   <li>{@code FinanceExceptionHandler}: HTTP マッピング</li>
 * </ul>
 *
 * @since 2026-05-04 (T5)
 */
public class FinanceBusinessException extends RuntimeException {

    private final String errorCode;

    public FinanceBusinessException(String message) {
        super(message);
        this.errorCode = null;
    }

    public FinanceBusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public FinanceBusinessException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 4744ms:
package jp.co.oda32.domain.service.data;

import jp.co.oda32.constant.CompanyType;
import jp.co.oda32.domain.model.master.MLoginUser;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.ArrayList;
import java.util.List;

public class LoginUser extends org.springframework.security.core.userdetails.User {
    private final MLoginUser loginUser;

    public LoginUser(MLoginUser user) {
        super(user.getLoginId(), user.getPassword(),
                AuthorityUtils.createAuthorityList(resolveRoles(user.getCompanyType())));
        this.loginUser = user;
    }

    private static String[] resolveRoles(String companyType) {
        List<String> roles = new ArrayList<>();
        roles.add("ROLE_USER");
        CompanyType type = CompanyType.purse(companyType);
        if (type != null) {
            switch (type) {
                case ADMIN -> roles.add("ROLE_ADMIN");
                case SHOP -> roles.add("ROLE_SHOP");
                case PARTNER -> roles.add("ROLE_PARTNER");
            }
        }
        return roles.toArray(new String[0]);
    }

    public MLoginUser getUser() {
        return this.loginUser;
    }

    /**
     * Admin 判定 (T4): shopNo == 0 かつ Role に ADMIN を含む。
     * 両者が乖離する場合 (DB 設定不整合等) は false を返す (fail-safe)。
     *
     * <p>用途:
     * <ul>
     *   <li>{@code @PreAuthorize("@loginUserSecurityBean.isAdmin()")} で全 Controller 統一</li>
     *   <li>service 層での admin 分岐 (例: 全 shop データへのアクセス)</li>
     * </ul>
     *
     * <p>shopNo は {@link MLoginUser#getShopNo()} 経由で解決する
     * (CompanyType=ADMIN なら 0、SHOP/PARTNER なら所属 shop_no)。
     */
    public boolean isAdmin() {
        if (loginUser == null) return false;
        Integer shopNo = loginUser.getShopNo();
        if (shopNo == null || shopNo != 0) return false;
        if (getAuthorities() == null) return false;
        return getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content frontend/components/common/AmountSourceTooltip.tsx' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content frontend/types/audit-log.ts' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content frontend/components/pages/admin/audit-log.tsx' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/api/admin/AuditLogController.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/audit/AuditLogQueryService.java' in C:\project\odamitsu-data-hub
 succeeded in 1952ms:
'use client'

import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { Info } from 'lucide-react'

/**
 * AmountSource: 金額表示列の「数字の出どころ」を表す enum。
 * 設計書: claudedocs/design-source-of-truth-hierarchy.md (DESIGN-DECISION T1)
 *
 * 各画面のテーブルヘッダーに <AmountSourceTooltip source="..." /> を添えることで
 * 「この列は何由来か」を説明可能にする。経理担当・税理士監査時の説明資料を兼ねる。
 */
export type AmountSource =
  | 'INVOICE'           // 仕入先請求書 (原本)
  | 'SMILE'             // SMILE 仕入実績 (t_purchase / t_purchase_detail)
  | 'PAYABLE_SUMMARY'   // 自社買掛集計 (t_accounts_payable_summary.tax_included_amount_change)
  | 'VERIFIED_AMOUNT'   // 振込確定額 (t_accounts_payable_summary.verified_amount)
  | 'MF_JOURNAL'        // MF 仕訳 (MF API /journals)
  | 'MF_TRIAL_BALANCE'  // MF 試算表 (MF API /trial_balance/bs, 検証用)
  | 'OPENING_BALANCE'   // 期首残 (m_supplier_opening_balance)
  | 'CLOSING_CALC'      // 累積残 (closing = opening + change ? payment_settled, 派生値)
  | 'AR_INVOICE'        // 売掛 請求 (t_invoice)
  | 'AR_VERIFIED'       // 売掛 検証済 (t_accounts_receivable_summary.verified_amount)

const SOURCE_LABEL: Record<AmountSource, { title: string; description: string }> = {
  INVOICE: {
    title: '仕入先請求書 (原本)',
    description: '仕入先から届いた請求書 (原本) の金額。取引の最終権威 (★★★)。',
  },
  SMILE: {
    title: 'SMILE 仕入実績',
    description: '自社基幹システム SMILE で計算した仕入額。t_purchase / t_purchase_detail から集計。',
  },
  PAYABLE_SUMMARY: {
    title: '自社買掛集計 (税込)',
    description: '月次バッチで supplier × 税率に集計した買掛金額。t_accounts_payable_summary.tax_included_amount_change 由来。',
  },
  VERIFIED_AMOUNT: {
    title: '振込確定額',
    description: '振込明細 Excel または手動から確定した実際の振込額。t_accounts_payable_summary.verified_amount 由来。銀行通帳と一致するべき値。',
  },
  MF_JOURNAL: {
    title: 'MF 仕訳',
    description: 'マネーフォワード会計に登録された仕訳金額。MF API /journals から取得 (credit ? debit を月次集計)。',
  },
  MF_TRIAL_BALANCE: {
    title: 'MF 試算表',
    description: 'マネーフォワード上の月末残高。MF API /trial_balance/bs から取得 (検証用、自社 DB の書込先ではない)。',
  },
  OPENING_BALANCE: {
    title: '期首残',
    description: 'MF 期首残高仕訳 (#1) から取得した期首残 + 手動補正。m_supplier_opening_balance.effective_balance 由来。',
  },
  CLOSING_CALC: {
    title: '累積残 (派生値)',
    description: '計算式: opening + change ? payment_settled。自社 T 勘定の月末残高定義。',
  },
  AR_INVOICE: {
    title: '請求 (売掛)',
    description: 't_invoice 由来の請求書金額 (SMILE 請求実績 Excel から取込)。',
  },
  AR_VERIFIED: {
    title: '入金確定額 (売掛)',
    description: 't_accounts_receivable_summary.verified_amount 由来の入金確定額。',
  },
}

interface AmountSourceTooltipProps {
  source: AmountSource
}

export function AmountSourceTooltip({ source }: AmountSourceTooltipProps) {
  const { title, description } = SOURCE_LABEL[source]
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <span
            className="inline-flex"
            // ヘッダーがソート可能な列の場合、Info ホバーで sort をトリガしないよう伝播停止
            onClick={(e) => e.stopPropagation()}
          >
            <Info
              className="ml-1 h-3 w-3 text-muted-foreground cursor-help"
              aria-label={`数字源: ${title}`}
            />
          </span>
        </TooltipTrigger>
        <TooltipContent className="max-w-xs">
          <div className="font-medium">{title}</div>
          <div className="text-xs opacity-80 mt-1">{description}</div>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2046ms:
package jp.co.oda32.domain.service.audit;

import jakarta.persistence.criteria.Predicate;
import jp.co.oda32.annotation.SkipShopCheck;
import jp.co.oda32.domain.model.audit.FinanceAuditLog;
import jp.co.oda32.domain.model.master.MLoginUser;
import jp.co.oda32.domain.repository.audit.FinanceAuditLogRepository;
import jp.co.oda32.domain.repository.master.LoginUserRepository;
import jp.co.oda32.dto.audit.AuditLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * T2: 監査ログ閲覧 (admin only) Service。
 * <p>
 * Specification で動的フィルタ (actor / operation / target_table / 日付範囲) を組み、
 * actor_user_no に対応する m_login_user.user_name を batch JOIN で解決する (N+1 回避)。
 *
 * @since 2026-05-04 (T2)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogQueryService {

    private final FinanceAuditLogRepository repository;
    private final LoginUserRepository loginUserRepository;

    @SkipShopCheck
    public Page<AuditLogResponse> search(
            Integer actorUserNo,
            String operation,
            String targetTable,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Pageable pageable) {
        Specification<FinanceAuditLog> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (actorUserNo != null) preds.add(cb.equal(root.get("actorUserNo"), actorUserNo));
            if (operation != null && !operation.isBlank()) preds.add(cb.equal(root.get("operation"), operation));
            if (targetTable != null && !targetTable.isBlank()) preds.add(cb.equal(root.get("targetTable"), targetTable));
            if (fromDate != null) preds.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), fromDate));
            if (toDate != null) preds.add(cb.lessThanOrEqualTo(root.get("occurredAt"), toDate));
            return cb.and(preds.toArray(new Predicate[0]));
        };
        Page<FinanceAuditLog> page = repository.findAll(spec, pageable);
        Map<Integer, String> nameMap = resolveUserNames(page.getContent());
        return page.map(e -> AuditLogResponse.summaryFrom(e, nameMap.get(e.getActorUserNo())));
    }

    @SkipShopCheck
    public Optional<AuditLogResponse> findDetail(Long id) {
        return repository.findById(id).map(e -> {
            String name = e.getActorUserNo() != null
                    ? loginUserRepository.findById(e.getActorUserNo()).map(MLoginUser::getUserName).orElse(null)
                    : null;
            return AuditLogResponse.from(e, name);
        });
    }

    /** distinct な target_table 一覧 (フィルタ UI セレクトボックス用)。 */
    @SkipShopCheck
    public List<String> distinctTargetTables() {
        // PostgreSQL 17 でも DISTINCT で十分速い (idx_finance_audit_log_target が効く)
        return repository.findAll().stream()
                .map(FinanceAuditLog::getTargetTable)
                .distinct()
                .sorted()
                .toList();
    }

    /** distinct な operation 一覧 (フィルタ UI セレクトボックス用)。 */
    @SkipShopCheck
    public List<String> distinctOperations() {
        return repository.findAll().stream()
                .map(FinanceAuditLog::getOperation)
                .distinct()
                .sorted()
                .toList();
    }

    private Map<Integer, String> resolveUserNames(List<FinanceAuditLog> rows) {
        Set<Integer> userNos = rows.stream()
                .map(FinanceAuditLog::getActorUserNo)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (userNos.isEmpty()) return Map.of();
        Map<Integer, String> map = new HashMap<>();
        loginUserRepository.findAllById(userNos)
                .forEach(u -> map.put(u.getLoginUserNo(), u.getUserName()));
        return map;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2047ms:
'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { PageHeader } from '@/components/features/common/PageHeader'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Loader2, Search, RefreshCw } from 'lucide-react'
import type { Paginated } from '@/types/paginated'
import type { AuditLogResponse } from '@/types/audit-log'

interface SearchParams {
  operation: string
  targetTable: string
  fromDate: string
  toDate: string
  page: number
  size: number
}

const PAGE_SIZE = 50

function buildQuery(p: SearchParams): string {
  const usp = new URLSearchParams()
  if (p.operation) usp.set('operation', p.operation)
  if (p.targetTable) usp.set('targetTable', p.targetTable)
  if (p.fromDate) usp.set('fromDate', `${p.fromDate}T00:00:00`)
  if (p.toDate) usp.set('toDate', `${p.toDate}T23:59:59`)
  usp.set('page', String(p.page))
  usp.set('size', String(p.size))
  return usp.toString()
}

function actorTypeBadge(actorType: string): React.ReactNode {
  if (actorType === 'USER') return <Badge variant="secondary">USER</Badge>
  if (actorType === 'SYSTEM') return <Badge variant="outline">SYSTEM</Badge>
  if (actorType === 'BATCH') return <Badge variant="outline">BATCH</Badge>
  return <Badge variant="outline">{actorType}</Badge>
}

function formatJson(value: unknown): string {
  if (value === null || value === undefined) return '(なし)'
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

export function AuditLogPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const [filterForm, setFilterForm] = useState<SearchParams>({
    operation: '',
    targetTable: '',
    fromDate: '',
    toDate: '',
    page: 0,
    size: PAGE_SIZE,
  })
  const [searchParams, setSearchParams] = useState<SearchParams | null>(null)
  const [detailId, setDetailId] = useState<number | null>(null)

  const tablesQuery = useQuery({
    queryKey: ['audit-log', 'tables'],
    queryFn: () => api.get<string[]>('/admin/audit-log/tables'),
    enabled: isAdmin,
  })

  const operationsQuery = useQuery({
    queryKey: ['audit-log', 'operations'],
    queryFn: () => api.get<string[]>('/admin/audit-log/operations'),
    enabled: isAdmin,
  })

  const searchQuery = useQuery({
    queryKey: ['audit-log', 'search', searchParams],
    queryFn: () => api.get<Paginated<AuditLogResponse>>(
      `/admin/audit-log/search?${buildQuery(searchParams!)}`,
    ),
    enabled: isAdmin && searchParams !== null,
  })

  const detailQuery = useQuery({
    queryKey: ['audit-log', 'detail', detailId],
    queryFn: () => api.get<AuditLogResponse>(`/admin/audit-log/${detailId}`),
    enabled: detailId !== null,
  })

  if (!isAdmin) {
    return (
      <div className="space-y-4">
        <PageHeader title="監査ログ" />
        <Card>
          <CardContent className="pt-4">
            <p className="text-sm text-muted-foreground">
              この画面は管理者のみが閲覧できます。
            </p>
          </CardContent>
        </Card>
      </div>
    )
  }

  const tableOptions = (tablesQuery.data ?? []).map((t) => ({ value: t, label: t }))
  const opOptions = (operationsQuery.data ?? []).map((o) => ({ value: o, label: o }))

  const onSearch = () => {
    setSearchParams({ ...filterForm, page: 0 })
  }

  const onPageChange = (next: number) => {
    if (!searchParams) return
    setSearchParams({ ...searchParams, page: next })
  }

  const detail = detailQuery.data

  return (
    <div className="space-y-4">
      <PageHeader
        title="監査ログ"
        description="finance Service 層の操作履歴 (T2 監査証跡基盤)"
      />

      <Card>
        <CardContent className="pt-4 grid grid-cols-1 md:grid-cols-5 gap-3 items-end">
          <div className="space-y-1">
            <Label className="text-xs">対象テーブル</Label>
            <SearchableSelect
              value={filterForm.targetTable}
              onValueChange={(v) => setFilterForm({ ...filterForm, targetTable: v ?? '' })}
              options={tableOptions}
              placeholder="(すべて)"
              clearable
            />
          </div>
          <div className="space-y-1">
            <Label className="text-xs">操作</Label>
            <SearchableSelect
              value={filterForm.operation}
              onValueChange={(v) => setFilterForm({ ...filterForm, operation: v ?? '' })}
              options={opOptions}
              placeholder="(すべて)"
              clearable
            />
          </div>
          <div className="space-y-1">
            <Label className="text-xs">From</Label>
            <Input
              type="date"
              value={filterForm.fromDate}
              onChange={(e) => setFilterForm({ ...filterForm, fromDate: e.target.value })}
            />
          </div>
          <div className="space-y-1">
            <Label className="text-xs">To</Label>
            <Input
              type="date"
              value={filterForm.toDate}
              onChange={(e) => setFilterForm({ ...filterForm, toDate: e.target.value })}
            />
          </div>
          <div className="flex gap-2">
            <Button size="sm" onClick={onSearch} disabled={searchQuery.isFetching}>
              {searchQuery.isFetching
                ? <Loader2 className="mr-1 h-3 w-3 animate-spin" />
                : <Search className="mr-1 h-3 w-3" />}
              検索
            </Button>
            {searchParams && (
              <Button
                size="sm"
                variant="outline"
                onClick={() => searchQuery.refetch()}
                disabled={searchQuery.isFetching}
              >
                <RefreshCw className="mr-1 h-3 w-3" />
                再読込
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="pt-4">
          {searchParams === null ? (
            <p className="text-sm text-muted-foreground">
              フィルタを指定して検索ボタンを押してください。
            </p>
          ) : searchQuery.isLoading ? (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          ) : searchQuery.data && searchQuery.data.content.length > 0 ? (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-[160px]">日時</TableHead>
                    <TableHead className="w-[80px]">種別</TableHead>
                    <TableHead className="w-[140px]">実行者</TableHead>
                    <TableHead className="w-[120px]">操作</TableHead>
                    <TableHead className="w-[200px]">対象テーブル</TableHead>
                    <TableHead>PK</TableHead>
                    <TableHead className="w-[100px]"></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {searchQuery.data.content.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell className="text-xs font-mono">
                        {new Date(row.occurredAt).toLocaleString('ja-JP')}
                      </TableCell>
                      <TableCell>{actorTypeBadge(row.actorType)}</TableCell>
                      <TableCell className="text-xs">
                        {row.actorUserName ?? (row.actorUserNo ? `#${row.actorUserNo}` : '-')}
                      </TableCell>
                      <TableCell className="text-xs font-mono">{row.operation}</TableCell>
                      <TableCell className="text-xs font-mono">{row.targetTable}</TableCell>
                      <TableCell className="text-xs font-mono truncate max-w-[300px]">
                        {row.targetPk ? JSON.stringify(row.targetPk) : '-'}
                      </TableCell>
                      <TableCell>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setDetailId(row.id)}
                        >
                          詳細
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <div className="mt-3 flex items-center justify-between text-xs text-muted-foreground">
                <span>
                  全 {searchQuery.data.totalElements} 件 / page {searchQuery.data.number + 1}/
                  {Math.max(1, searchQuery.data.totalPages)}
                </span>
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={searchQuery.data.first}
                    onClick={() => onPageChange(searchQuery.data!.number - 1)}
                  >
                    前へ
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={searchQuery.data.last}
                    onClick={() => onPageChange(searchQuery.data!.number + 1)}
                  >
                    次へ
                  </Button>
                </div>
              </div>
            </>
          ) : (
            <p className="text-sm text-muted-foreground">該当する監査ログはありません。</p>
          )}
        </CardContent>
      </Card>

      <Dialog open={detailId !== null} onOpenChange={(open) => !open && setDetailId(null)}>
        <DialogContent className="max-w-5xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>監査ログ詳細 #{detailId}</DialogTitle>
            <DialogDescription>
              {detail
                ? `${new Date(detail.occurredAt).toLocaleString('ja-JP')} ・ ${detail.targetTable} ・ ${detail.operation}`
                : 'loading...'}
            </DialogDescription>
          </DialogHeader>
          {detailQuery.isLoading || !detail ? (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          ) : (
            <div className="space-y-4 text-sm">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label className="text-xs">実行者</Label>
                  <div>
                    {actorTypeBadge(detail.actorType)}{' '}
                    <span className="ml-2">
                      {detail.actorUserName ?? (detail.actorUserNo ? `#${detail.actorUserNo}` : '-')}
                    </span>
                  </div>
                </div>
                <div>
                  <Label className="text-xs">送信元 IP / UA</Label>
                  <div className="text-xs font-mono break-all">
                    {detail.sourceIp ?? '-'} / {detail.userAgent ?? '-'}
                  </div>
                </div>
              </div>

              {detail.reason && (
                <div className="rounded border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-900">
                  <strong>reason:</strong> {detail.reason}
                </div>
              )}

              <div>
                <Label className="text-xs">PK</Label>
                <pre className="mt-1 rounded bg-muted p-2 text-[11px] font-mono overflow-x-auto">
                  {formatJson(detail.targetPk)}
                </pre>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <div>
                  <Label className="text-xs">Before</Label>
                  <pre className="mt-1 max-h-[40vh] overflow-auto rounded bg-muted p-2 text-[11px] font-mono">
                    {formatJson(detail.beforeValues)}
                  </pre>
                </div>
                <div>
                  <Label className="text-xs">After</Label>
                  <pre className="mt-1 max-h-[40vh] overflow-auto rounded bg-muted p-2 text-[11px] font-mono">
                    {formatJson(detail.afterValues)}
                  </pre>
                </div>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2148ms:
/**
 * T2: 監査ログ閲覧 型定義。
 * 設計書: claudedocs/design-finance-audit-log.md
 */

export type AuditActorType = 'USER' | 'SYSTEM' | 'BATCH'

export interface AuditLogResponse {
  id: number
  occurredAt: string
  actorUserNo: number | null
  actorUserName: string | null
  actorType: AuditActorType
  operation: string
  targetTable: string
  /** 一覧 (summaryFrom) では PK のみ。詳細 (from) では before/after も付く。 */
  targetPk: unknown
  beforeValues?: unknown
  afterValues?: unknown
  reason?: string | null
  sourceIp?: string | null
  userAgent?: string | null
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3189ms:
package jp.co.oda32.api.admin;

import jp.co.oda32.domain.service.audit.AuditLogQueryService;
import jp.co.oda32.dto.audit.AuditLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * T2: 監査ログ閲覧 (admin only)。
 * <p>
 * - {@code GET /api/v1/admin/audit-log/search} : 検索 (ページング)
 * - {@code GET /api/v1/admin/audit-log/{id}}   : 詳細 (before/after JSONB 含む)
 * - {@code GET /api/v1/admin/audit-log/operations} / {@code .../tables} : フィルタ用 distinct
 *
 * @since 2026-05-04 (T2)
 */
@RestController
@RequestMapping("/api/v1/admin/audit-log")
@PreAuthorize("@loginUserSecurityBean.isAdmin()")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogQueryService service;

    @GetMapping("/search")
    public ResponseEntity<Page<AuditLogResponse>> search(
            @RequestParam(required = false) Integer actorUserNo,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String targetTable,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                service.search(actorUserNo, operation, targetTable, fromDate, toDate, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogResponse> detail(@PathVariable Long id) {
        return service.findDetail(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/operations")
    public ResponseEntity<List<String>> distinctOperations() {
        return ResponseEntity.ok(service.distinctOperations());
    }

    @GetMapping("/tables")
    public ResponseEntity<List<String>> distinctTables() {
        return ResponseEntity.ok(service.distinctTargetTables());
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
ここまでで、監査ログの設計コメントと実装の実態にズレが見えています。特に `beforeValues` に実引数が入る命名不整合、distinct フィルタの全件ロード、監査書込み失敗の検知不能、IP/UA のそのまま表示がレビュー候補です。
2026-05-06T05:36:44.711148Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Recurse -File backend/src/main/java | Select-String -Pattern '@AuditLog|@AuditExclude|loginUserSecurityBean|ResponseStatusException|IllegalStateException|FinanceBusinessException|FinanceInternalException' | Select-Object Path,LineNumber,Line | Format-List"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Recurse -File backend/src/main/java | Select-String -Pattern '@AuditLog|@AuditExclude|loginUserSecurityBean|ResponseStatusException|IllegalStateException|FinanceBusinessException|FinanceInternalException' | Select-Object Path,LineNumber,Line | Format-List" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Recurse -File backend/src/main/java | Select-String -Pattern '@AuditLog|@AuditExclude|loginUserSecurityBean|ResponseStatusException|IllegalStateException|FinanceBusinessException|FinanceInternalException' | Select-Object Path,LineNumber,Line | Format-List"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /S /N /C:\"@AuditLog\" /C:\"@AuditExclude\" /C:\"loginUserSecurityBean\" /C:\"ResponseStatusException\" /C:\"IllegalStateException\" /C:\"FinanceBusinessException\" /C:\"FinanceInternalException\" backend\\src\\main\\java\\*.java" in C:\project\odamitsu-data-hub
 succeeded in 1410ms:
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:54:            throw new IllegalStateException(
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:76:            throw new IllegalStateException(
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:107:                    throw new IllegalStateException(
backend\src\main\java\jp\co\oda32\api\admin\AuditLogController.java:33:@PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\batch\BatchController.java:106:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\batch\BatchController.java:128:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\batch\BatchController.java:158:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\AccountsReceivableController.java:21:import jp.co.oda32.exception.FinanceBusinessException;
backend\src\main\java\jp\co\oda32\api\finance\AccountsReceivableController.java:22:import jp.co.oda32.exception.FinanceInternalException;
backend\src\main\java\jp\co\oda32\api\finance\AccountsReceivableController.java:43:import org.springframework.web.server.ResponseStatusException;
backend\src\main\java\jp\co\oda32\api\finance\AccountsReceivableController.java:120:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\AccountsReceivableController.java:136:            // T5: 内部 Bean 不在 (config 異常) → FinanceInternalException で 422 + 汎用化。
backend\src\main\java\jp\co\oda32\api\finance\AccountsReceivableController.java:137:            throw new FinanceInternalException("集計ジョブが見つかりません: " + beanName);
backend\src\main\java\jp\co\oda32\api\finance\AccountsReceivableController.java:166:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\AccountsReceivableController.java:203:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\AccountsReceivableController.java:222:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\AccountsReceivableController.java:237:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\AccountsReceivableController.java:255:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\AccountsReceivableController.java:272:        // T5: 業務メッセージなので FinanceBusinessException → 400 + 元メッセージで client へ。
backend\src\main\java\jp\co\oda32\api\finance\AccountsReceivableController.java:274:            throw new FinanceBusinessException(
backend\src\main\java\jp\co\oda32\api\finance\AccountsReceivableController.java:309:            // ResponseStatusException(FORBIDDEN) は GlobalExceptionHandler が 403 で処理する。
backend\src\main\java\jp\co\oda32\api\finance\AccountsReceivableController.java:310:            // 認可境界はあえて Spring 標準 ResponseStatusException を維持 (通常の業務エラーと区別)。
backend\src\main\java\jp\co\oda32\api\finance\AccountsReceivableController.java:311:            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "他ショップのデータにはアクセスできません");
backend\src\main\java\jp\co\oda32\api\finance\CashBookController.java:62:            // MJ-B01: IllegalStateException の local catch を撤去。
backend\src\main\java\jp\co\oda32\api\finance\FinanceController.java:41:import jp.co.oda32.exception.FinanceBusinessException;
backend\src\main\java\jp\co\oda32\api\finance\FinanceController.java:57:import org.springframework.web.server.ResponseStatusException;
backend\src\main\java\jp\co\oda32\api\finance\FinanceController.java:151:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\FinanceController.java:181:            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "他ショップのデータにはアクセスできません");
backend\src\main\java\jp\co\oda32\api\finance\FinanceController.java:321:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\FinanceController.java:337:            // T5: 業務メッセージ (運用者にデータ条件を伝える) → FinanceBusinessException で 400 + 元メッセージ。
backend\src\main\java\jp\co\oda32\api\finance\FinanceController.java:338:            throw new FinanceBusinessException(
backend\src\main\java\jp\co\oda32\api\finance\FinanceController.java:375:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\FinanceController.java:449:                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
backend\src\main\java\jp\co\oda32\api\finance\FinanceController.java:454:        } catch (ResponseStatusException e) {
backend\src\main\java\jp\co\oda32\api\finance\FinanceController.java:535:            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "グループが見つかりません: id=" + id);
backend\src\main\java\jp\co\oda32\api\finance\FinanceController.java:552:            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "グループが見つかりません: id=" + id);
backend\src\main\java\jp\co\oda32\api\finance\FinanceController.java:565:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:7:import jp.co.oda32.exception.FinanceBusinessException;
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:8:import jp.co.oda32.exception.FinanceInternalException;
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:67:     * <strong>セキュリティ注意 (MA-01)</strong>: Finance パッケージ内 Service の {@link IllegalStateException}
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:71:    @ExceptionHandler(IllegalStateException.class)
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:72:    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:73:        log.warn("Finance IllegalStateException: {}", ex.getMessage(), ex);
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:86:    @ExceptionHandler(FinanceBusinessException.class)
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:87:    public ResponseEntity<ErrorResponse> handleFinanceBusiness(FinanceBusinessException ex) {
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:102:    @ExceptionHandler(FinanceInternalException.class)
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:103:    public ResponseEntity<ErrorResponse> handleFinanceInternal(FinanceInternalException ex) {
backend\src\main\java\jp\co\oda32\api\finance\MfClientMappingController.java:27:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\MfClientMappingController.java:36:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\MfClientMappingController.java:42:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationController.java:43:@PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationDebugController.java:43:@PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationDebugController.java:121:                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationDebugController.java:166:                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationDebugController.java:245:                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationDebugController.java:327:                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
backend\src\main\java\jp\co\oda32\api\finance\MfJournalRuleController.java:27:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\MfJournalRuleController.java:33:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\MfJournalRuleController.java:39:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\PaymentMfImportController.java:67:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\PaymentMfImportController.java:89:        // IllegalStateException は FinanceExceptionHandler#handleIllegalState で 422 + 汎用メッセージ統一
backend\src\main\java\jp\co\oda32\api\finance\PaymentMfImportController.java:94:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\PaymentMfImportController.java:117:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\PaymentMfImportController.java:154:        // IllegalStateException は FinanceExceptionHandler#handleIllegalState で 422 + 汎用メッセージ統一
backend\src\main\java\jp\co\oda32\api\finance\PaymentMfImportController.java:160:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\PaymentMfImportController.java:179:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\PaymentMfImportController.java:239:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\PaymentMfImportController.java:248:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\PaymentMfImportController.java:260:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\SupplierOpeningBalanceController.java:48:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\finance\SupplierOpeningBalanceController.java:59:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\master\MasterController.java:129:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\master\MasterController.java:162:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\master\MasterController.java:172:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\master\MasterController.java:183:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\master\MasterController.java:194:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\master\MasterController.java:204:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\master\MasterController.java:216:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\master\MasterController.java:227:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\master\MasterController.java:240:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\master\MasterController.java:254:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\master\MasterController.java:265:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\master\MasterController.java:278:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\master\MasterController.java:292:    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\purchase\QuoteImportController.java:22:@PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\api\user\UserController.java:24:@PreAuthorize("@loginUserSecurityBean.isAdmin()")
backend\src\main\java\jp\co\oda32\audit\FinanceAuditAspect.java:48:        // (@AuditExclude は @JsonIgnore を継承しているため、この mapper でも自動除外される)
backend\src\main\java\jp\co\oda32\batch\bcart\BCartOrderConvertSmileOrderFileTasklet.java:541:            throw new IllegalStateException(String.format(
backend\src\main\java\jp\co\oda32\batch\finance\helper\TaxCalculationHelper.java:66:            throw new IllegalStateException("計算された税込金額の合計がnullです");
backend\src\main\java\jp\co\oda32\batch\smile\PartnerFileReader.java:50:            throw new IllegalStateException("Readerは適切に初期化されていません。FileReaderがnullです。");
backend\src\main\java\jp\co\oda32\constant\BCartApiConfig.java:34:            throw new IllegalStateException(
backend\src\main\java\jp\co\oda32\constant\BCartApiConfig.java:47:            throw new IllegalStateException(
backend\src\main\java\jp\co\oda32\domain\model\audit\FinanceAuditLog.java:22: * {@code @AuditLog} アノテーション付き Service メソッド呼び出しごとに 1 行記録される。
backend\src\main\java\jp\co\oda32\domain\model\finance\MMfOauthClient.java:33:    @AuditExclude
backend\src\main\java\jp\co\oda32\domain\model\finance\TCashbookImportHistory.java:38:    @AuditExclude
backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java:34:    @AuditExclude
backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java:38:    @AuditExclude
backend\src\main\java\jp\co\oda32\domain\model\finance\TPaymentMfImportHistory.java:55:    @AuditExclude
backend\src\main\java\jp\co\oda32\domain\service\data\LoginUser.java:43:     *   <li>{@code @PreAuthorize("@loginUserSecurityBean.isAdmin()")} で全 Controller 統一</li>
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountingStatusService.java:12:import jp.co.oda32.exception.FinanceInternalException;
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountingStatusService.java:147:                throw new FinanceInternalException("経理ステータス SQL の実行に失敗しました", e);
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:31:import org.springframework.web.server.ResponseStatusException;
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:91:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:95:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:99:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:104:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:127:                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableLedgerService.java:18:import org.springframework.web.server.ResponseStatusException;
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableLedgerService.java:61:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableLedgerService.java:65:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableLedgerService.java:70:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableLedgerService.java:75:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableLedgerService.java:82:            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableLedgerService.java:133:            throw new IllegalStateException("aggregateMonth: empty group");
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsReceivableBulkVerifyService.java:47:    @AuditLog(table = "t_accounts_receivable_summary", operation = "bulk_verify",
backend\src\main\java\jp\co\oda32\domain\service\finance\CashBookConvertService.java:142:     * MfTaxResolver 等の内部設定異常 ({@link IllegalStateException}) は
backend\src\main\java\jp\co\oda32\domain\service\finance\CashBookConvertService.java:673:                catch (IllegalStateException e) {
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:18:import org.springframework.web.server.ResponseStatusException;
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:56:    @AuditLog(table = "t_consistency_review", operation = "upsert", captureArgsAsAfter = true)
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:98:    @AuditLog(table = "t_consistency_review", operation = "delete", captureArgsAsAfter = true)
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:136:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:142:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:146:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:163:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:170:            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
backend\src\main\java\jp\co\oda32\domain\service\finance\InvoiceImportService.java:60:    @AuditLog(table = "t_invoice", operation = "import", pkArgIndex = 1, captureArgsAsAfter = false,
backend\src\main\java\jp\co\oda32\domain\service\finance\InvoiceImportService.java:322:                } catch (IllegalStateException e) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfAccountSyncService.java:86:                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:238:                    throw new IllegalStateException("MF /v2/tenant が tenant id を返却しませんでした");
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:298:                throw new IllegalStateException("MF token エンドポイントから不正なレスポンス (access_token なし)");
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfBalanceReconcileService.java:54:                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfEnumTranslationService.java:112:                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfJournalFetcher.java:4:import jp.co.oda32.exception.FinanceBusinessException;
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfJournalFetcher.java:5:import jp.co.oda32.exception.FinanceInternalException;
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfJournalFetcher.java:52:     * 全候補で失敗したら {@link IllegalStateException}。
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfJournalFetcher.java:90:            throw new FinanceBusinessException(
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfJournalFetcher.java:161:                throw new FinanceInternalException("MF journals ページング safeguard を超過しました (50 pages)");
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfJournalReconcileService.java:93:                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:115:                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:132:    @AuditLog(table = "t_mf_oauth_token", operation = "auth_callback", captureArgsAsAfter = true)
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:155:                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:164:                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が消えています: " + clientId));
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:181:                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が消えています: " + clientId));
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:250:                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が消えています: " + clientId));
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:265:                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が消えています: " + clientId));
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:324:    @AuditLog(table = "t_mf_oauth_token", operation = "revoke", captureArgsAsAfter = true)
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:353:            throw new IllegalStateException("MF tenant id を取得できませんでした");
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:405:                    .orElseThrow(() -> new IllegalStateException(
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthStateStore.java:112:            throw new IllegalStateException("SHA-256 が利用できません", e);
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningBalanceService.java:23:import org.springframework.web.server.ResponseStatusException;
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningBalanceService.java:75:    @AuditLog(table = "m_supplier_opening_balance", operation = "mf_fetch", captureArgsAsAfter = true)
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningBalanceService.java:79:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningBalanceService.java:85:                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningBalanceService.java:95:            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningBalanceService.java:224:    @AuditLog(table = "m_supplier_opening_balance", operation = "manual_adjust",
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningBalanceService.java:228:            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "userNo が取得できません");
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningBalanceService.java:232:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningBalanceService.java:239:            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfSupplierLedgerService.java:16:import org.springframework.web.server.ResponseStatusException;
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfSupplierLedgerService.java:62:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfSupplierLedgerService.java:66:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfSupplierLedgerService.java:70:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfSupplierLedgerService.java:75:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfSupplierLedgerService.java:81:            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfSupplierLedgerService.java:91:                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
backend\src\main\java\jp\co\oda32\domain\service\finance\MfTaxResolver.java:28:            // SF-B10: マスタ起因の構造異常は IllegalStateException 化し、
backend\src\main\java\jp\co\oda32\domain\service\finance\MfTaxResolver.java:30:            default -> throw new IllegalStateException("未知の税区分リゾルバ: " + resolverCode);
backend\src\main\java\jp\co\oda32\domain\service\finance\MPartnerGroupService.java:45:    @AuditLog(table = "m_partner_group", operation = "INSERT", captureArgsAsAfter = true)
backend\src\main\java\jp\co\oda32\domain\service\finance\MPartnerGroupService.java:57:    @AuditLog(table = "m_partner_group", operation = "UPDATE", captureArgsAsAfter = true)
backend\src\main\java\jp\co\oda32\domain\service\finance\MPartnerGroupService.java:76:    @AuditLog(table = "m_partner_group", operation = "DELETE", captureArgsAsAfter = true)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:4:import jp.co.oda32.exception.FinanceInternalException;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:68:            throw new FinanceInternalException("CSV出力に失敗しました", e);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:22:import jp.co.oda32.exception.FinanceBusinessException;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:176:            // T5: 業務メッセージ (ユーザーがマスタ登録すれば解決) なので FinanceBusinessException を使う。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:177:            // 旧 IllegalStateException → FinanceExceptionHandler.handleIllegalState で 422 + 汎用化されてしまい
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:179:            throw new FinanceBusinessException(
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:200:    @AuditLog(table = "t_accounts_payable_summary", operation = "payment_mf_apply",
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1441:            throw new IllegalStateException("SHA-256 unavailable", e);
backend\src\main\java\jp\co\oda32\domain\service\finance\PurchaseJournalCsvService.java:6:import jp.co.oda32.exception.FinanceInternalException;
backend\src\main\java\jp\co\oda32\domain\service\finance\PurchaseJournalCsvService.java:195:            throw new FinanceInternalException("金額に小数点以下が含まれています: " + amount);
backend\src\main\java\jp\co\oda32\domain\service\finance\SalesJournalCsvService.java:6:import jp.co.oda32.exception.FinanceInternalException;
backend\src\main\java\jp\co\oda32\domain\service\finance\SalesJournalCsvService.java:72:     * 従来は master 未登録で IllegalStateException を投げて fail-fast だったが、買掛側の
backend\src\main\java\jp\co\oda32\domain\service\finance\SalesJournalCsvService.java:203:                throw new FinanceInternalException(String.format(
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:24:import org.springframework.web.server.ResponseStatusException;
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:66:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shopNo は必須です");
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:77:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:81:            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:97:                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:11:import jp.co.oda32.exception.FinanceInternalException;
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:116:    @AuditLog(table = "t_accounts_payable_summary", operation = "verify", captureArgsAsAfter = true)
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:137:    @AuditLog(table = "t_accounts_payable_summary", operation = "release_manual_lock",
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:150:    @AuditLog(table = "t_accounts_payable_summary", operation = "mf_export_toggle",
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:171:            // T5: 内部 ID (supplier_no) を含むため FinanceInternalException で client には汎用化。
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:173:            throw new FinanceInternalException(
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsReceivableSummaryService.java:205:    @AuditLog(table = "t_accounts_receivable_summary", operation = "verify", captureArgsAsAfter = true)
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsReceivableSummaryService.java:228:    @AuditLog(table = "t_accounts_receivable_summary", operation = "release_manual_lock",
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsReceivableSummaryService.java:245:    @AuditLog(table = "t_accounts_receivable_summary", operation = "mf_export_toggle",
backend\src\main\java\jp\co\oda32\exception\FinanceBusinessException.java:9: * <p>機微情報を含むメッセージには使わないこと (= {@link FinanceInternalException} を使う)。
backend\src\main\java\jp\co\oda32\exception\FinanceBusinessException.java:14: *   <li>{@link FinanceInternalException}: 内部エラー (機微情報含む可能性、汎用化される)</li>
backend\src\main\java\jp\co\oda32\exception\FinanceBusinessException.java:20:public class FinanceBusinessException extends RuntimeException {
backend\src\main\java\jp\co\oda32\exception\FinanceBusinessException.java:24:    public FinanceBusinessException(String message) {
backend\src\main\java\jp\co\oda32\exception\FinanceBusinessException.java:29:    public FinanceBusinessException(String message, String errorCode) {
backend\src\main\java\jp\co\oda32\exception\FinanceBusinessException.java:34:    public FinanceBusinessException(String message, Throwable cause) {
backend\src\main\java\jp\co\oda32\exception\FinanceInternalException.java:16: * <p>業務メッセージをユーザーに伝えたい場合は {@link FinanceBusinessException} を使うこと。
backend\src\main\java\jp\co\oda32\exception\FinanceInternalException.java:20:public class FinanceInternalException extends RuntimeException {
backend\src\main\java\jp\co\oda32\exception\FinanceInternalException.java:22:    public FinanceInternalException(String message) {
backend\src\main\java\jp\co\oda32\exception\FinanceInternalException.java:26:    public FinanceInternalException(String message, Throwable cause) {
backend\src\main\java\jp\co\oda32\exception\GlobalExceptionHandler.java:15:import org.springframework.web.server.ResponseStatusException;
backend\src\main\java\jp\co\oda32\exception\GlobalExceptionHandler.java:86:    @ExceptionHandler(ResponseStatusException.class)
backend\src\main\java\jp\co\oda32\exception\GlobalExceptionHandler.java:87:    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
backend\src\main\java\jp\co\oda32\exception\GlobalExceptionHandler.java:91:            log.error("ResponseStatusException: {}", ex.getMessage(), ex);
backend\src\main\java\jp\co\oda32\exception\GlobalExceptionHandler.java:93:            log.warn("ResponseStatusException: {} - {}", status, ex.getReason());
backend\src\main\java\jp\co\oda32\security\LoginUserSecurityBean.java:11: * <p>{@code @PreAuthorize("@loginUserSecurityBean.isAdmin()")} の形で全 Controller
backend\src\main\java\jp\co\oda32\security\LoginUserSecurityBean.java:25:@Component("loginUserSecurityBean")
backend\src\main\java\jp\co\oda32\util\CryptoUtil.java:46:            throw new IllegalStateException(
backend\src\main\java\jp\co\oda32\util\CryptoUtil.java:50:            throw new IllegalStateException(
backend\src\main\java\jp\co\oda32\util\CryptoUtil.java:56:            throw new IllegalStateException(
backend\src\main\java\jp\co\oda32\util\CryptoUtil.java:60:            throw new IllegalStateException(
backend\src\main\java\jp\co\oda32\util\OauthCryptoUtil.java:47:            throw new IllegalStateException(
backend\src\main\java\jp\co\oda32\util\OauthCryptoUtil.java:53:            throw new IllegalStateException(
backend\src\main\java\jp\co\oda32\util\OauthCryptoUtil.java:59:            throw new IllegalStateException(
backend\src\main\java\jp\co\oda32\util\OauthCryptoUtil.java:63:            throw new IllegalStateException(
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java -TotalCount 260' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/MPartnerGroupService.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java -TotalCount 360' in C:\project\odamitsu-data-hub
 succeeded in 1931ms:
package jp.co.oda32.domain.service.finance;

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
    @AuditLog(table = "t_accounts_payable_summary", operation = "verify", captureArgsAsAfter = true)
    public TAccountsPayableSummary verify(
            int shopNo, int supplierNo, LocalDate transactionMonth, BigDecimal taxRate,
            BigDecimal verifiedAmount, String note) {
        TAccountsPayableSummary summary = getByPK(shopNo, supplierNo, transactionMonth, taxRate);
        if (summary == null) {
            throw new IllegalArgumentException("対象の買掛金集計が見つかりません");
        }
        applyVerification(summary, verifiedAmount);
        summary.setVerifiedManually(Boolean.TRUE);
        summary.setVerificationNote(note);
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
            captureArgsAsAfter = true)
    public TAccountsPayableSummary releaseManualLock(
            int shopNo, int supplierNo, LocalDate transactionMonth, BigDecimal taxRate) {
        TAccountsPayableSummary summary = getByPK(shopNo, supplierNo, transactionMonth, taxRate);
        if (summary == null) {
            throw new IllegalArgumentException("対象の買掛金集計が見つかりません");
        }
        summary.setVerifiedManually(Boolean.FALSE);
        return repository.save(summary);
    }

    @Transactional
    @AuditLog(table = "t_accounts_payable_summary", operation = "mf_export_toggle",
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
            // T5: 内部 ID (supplier_no) を含むため FinanceInternalException で client には汎用化。
            // 詳細メッセージはサーバーログのみ記録 (情報漏洩防止)。
            throw new FinanceInternalException(
                    "買掛金集計の taxRate が null です。集計バッチを再実行してください: shopNo="
                            + summary.getShopNo() + ", supplierNo=" + summary.getSupplierNo()
                            + ", transactionMonth=" + summary.getTransactionMonth());
        }
        BigDecimal rate = summary.getTaxRate();
        BigDecimal divisor = BigDecimal.ONE.add(rate.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        BigDecimal taxExcluded = verifiedAmount.divide(divisor, 0, RoundingMode.DOWN);
        summary.setTaxExcludedAmount(taxExcluded);

        BigDecimal baseTaxIncluded = summary.getTaxIncludedAmountChange() != null
                ? summary.getTaxIncludedAmountChange()
                : BigDecimal.ZERO;
        BigDecimal difference = verifiedAmount.subtract(baseTaxIncluded);
        summary.setPaymentDifference(difference);

        boolean matched = difference.abs().compareTo(FinanceConstants.MATCH_TOLERANCE) <= 0;
        summary.setVerificationResult(matched ? 1 : 0);
    }

    public TAccountsPayableSummary save(TAccountsPayableSummary summary) {
        // 金額の小数点以下を切り捨て
        if (summary.getTaxIncludedAmount() != null) {
            summary.setTaxIncludedAmount(summary.getTaxIncludedAmount().setScale(0, RoundingMode.DOWN));
        }
        if (summary.getTaxExcludedAmount() != null) {
            summary.setTaxExcludedAmount(summary.getTaxExcludedAmount().setScale(0, RoundingMode.DOWN));
        }
        if (summary.getTaxIncludedAmountChange() != null) {
            summary.setTaxIncludedAmountChange(summary.getTaxIncludedAmountChange().setScale(0, RoundingMode.DOWN));
        }
        if (summary.getTaxExcludedAmountChange() != null) {
            summary.setTaxExcludedAmountChange(summary.getTaxExcludedAmountChange().setScale(0, RoundingMode.DOWN));
        }
        if (summary.getPaymentDifference() != null) {
            summary.setPaymentDifference(summary.getPaymentDifference().setScale(0, RoundingMode.DOWN));
        }
        return repository.save(summary);
    }

    public List<TAccountsPayableSummary> saveAll(List<TAccountsPayableSummary> summaries) {
        for (TAccountsPayableSummary summary : summaries) {
            if (summary.getTaxIncludedAmount() != null) {
                summary.setTaxIncludedAmount(summary.getTaxIncludedAmount().setScale(0, RoundingMode.DOWN));
            }
            if (summary.getTaxExcludedAmount() != null) {
                summary.setTaxExcludedAmount(summary.getTaxExcludedAmount().setScale(0, RoundingMode.DOWN));
            }
            if (summary.getTaxIncludedAmountChange() != null) {
                summary.setTaxIncludedAmountChange(summary.getTaxIncludedAmountChange().setScale(0, RoundingMode.DOWN));
            }
            if (summary.getTaxExcludedAmountChange() != null) {
                summary.setTaxExcludedAmountChange(summary.getTaxExcludedAmountChange().setScale(0, RoundingMode.DOWN));
            }
            if (summary.getPaymentDifference() != null) {
                summary.setPaymentDifference(summary.getPaymentDifference().setScale(0, RoundingMode.DOWN));
            }
        }
        return repository.saveAll(summaries);
    }

    public List<TAccountsPayableSummary> findByTransactionMonth(LocalDate transactionMonth) {
        return repository.findByTransactionMonth(transactionMonth);
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1985ms:
package jp.co.oda32.domain.service.finance;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jp.co.oda32.audit.AuditLog;
import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.model.finance.MPaymentMfRule;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.finance.TPaymentMfAuxRow;
import jp.co.oda32.domain.model.finance.TPaymentMfImportHistory;
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

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 自己注入。{@code @Transactional(REQUIRES_NEW)} が同一クラス内呼び出しでも
     * Spring AOP プロキシ経由になるようにするため。{@code @Lazy} は循環依存回避。
     */
    @Autowired
    @Lazy
    private PaymentMfImportService self;

    // 差額一致閾値は FinanceConstants.PAYMENT_REPORT_MINOR_DIFFERENCE(_LONG) に集約。
    private static final int MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
    private static final int MAX_DATA_ROWS = 10000;
    private static final int MAX_CACHE_ENTRIES = 100;
    private static final long CACHE_TTL_MILLIS = 30L * 60 * 1000;
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /**
     * 取引月単位の applyVerification / exportVerifiedCsv を直列化する advisory lock 用キー。
     * shop_no と transaction_month(epochDay) を混ぜて 64bit のキーに詰める。
     */
    private static final int ADVISORY_LOCK_CLASS = 0x7041_4D46; // 'pAMF'

    // CSV 生成ロジックは {@link PaymentMfCsvWriter} に分離（ステートレスユーティリティ）。

    // Excel 読み取り（selectSheet / parseSheet / メタ行判定 など）は
    // {@link PaymentMfExcelParser} に分離。ParsedEntry / ParsedWorkbook も同クラスに移動。

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
                                  TPaymentMfAuxRowRepository auxRowRepository) {
        this.ruleRepository = ruleRepository;
        this.historyRepository = historyRepository;
        this.payableRepository = payableRepository;
        this.payableService = payableService;
        this.auxRowRepository = auxRowRepository;
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
            PaymentMfExcelParser.ParsedWorkbook parsed = PaymentMfExcelParser.parseSheet(sheet);
            if (parsed.entries.size() > MAX_DATA_ROWS) {
                throw new IllegalArgumentException("データ行数上限を超過しています: " + parsed.entries.size());
            }

            String uploadId = UUID.randomUUID().toString();
            CachedUpload cached = CachedUpload.builder()
                    .entries(parsed.entries)
                    .transferDate(parsed.transferDate)
                    .summarySourceFee(parsed.summarySourceFee)
                    .summaryEarlyPayment(parsed.summaryEarlyPayment)
                    .summaryTransferAmount(parsed.summaryTransferAmount)
                    .summaryInvoiceTotal(parsed.summaryInvoiceTotal)
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
        // CSV 取引日列は 小田光の締め日(前月20日 = transactionMonth) 固定。
        // 送金日(送金日≠取引日)は MF の銀行データ連携で自動付与されるため CSV には含めない。
        LocalDate txMonth = cached.getTransferDate() == null ? null
                : deriveTransactionMonth(cached.getTransferDate());
        byte[] csv = PaymentMfCsvWriter.toCsvBytes(preview.getRows(), txMonth);
        saveHistory(cached, preview, csv, userNo);
        return csv;
    }

    /**
     * アップロード済み振込明細を正として、買掛金一覧(t_accounts_payable_summary)に検証結果を一括反映する。
     * verified_manually=true で手動確定扱いにし、SMILE再検証バッチで上書きされないようにする。
     *
     * <p>並列呼び出しでの上書き競合を防ぐため、同一 (shop_no, transaction_month) に対しては
     * PostgreSQL advisory lock で直列化する。5日払い Excel と 20日払い Excel が別プロセス/
     * 別 UI から同時適用された場合でも、後着は先行 tx のコミット完了後に実行される。
     */
    @Transactional
    @AuditLog(table = "t_accounts_payable_summary", operation = "payment_mf_apply",
            captureArgsAsAfter = true, captureReturnAsAfter = true)
    public VerifyResult applyVerification(String uploadId, Integer userNo) {
        CachedUpload cached = getCached(uploadId);
        if (cached.getTransferDate() == null) {
            throw new IllegalArgumentException("送金日が取得できていません。xlsxを再アップロードしてください");
        }
        LocalDate txMonth = deriveTransactionMonth(cached.getTransferDate());
        acquireAdvisoryLock(txMonth);

        String note = FinanceConstants.VERIFICATION_NOTE_BULK_PREFIX
                + cached.getFileName() + " " + cached.getTransferDate();

        List<MPaymentMfRule> rules = ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0");
        Map<String, MPaymentMfRule> byCode = new LinkedHashMap<>();
        Map<String, MPaymentMfRule> bySource = new LinkedHashMap<>();
        for (MPaymentMfRule r : rules) {
            if (r.getPaymentSupplierCode() != null && !r.getPaymentSupplierCode().isBlank())
                byCode.putIfAbsent(r.getPaymentSupplierCode().trim(), r);
            bySource.putIfAbsent(normalize(r.getSourceName()), r);
        }

        // 事前パス: 当該 Excel に含まれる全 supplierCode を集め、t_accounts_payable_summary を一括取得する (N+1 解消)。
        Set<String> codesToReconcile = new java.util.LinkedHashSet<>();
        for (PaymentMfExcelParser.ParsedEntry e : cached.getEntries()) {
            if (e.afterTotal) continue;
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
            // PAYABLE行のみ対象（合計後の20日払いセクションは対象外）
            if (e.afterTotal) { skipped++; continue; }
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
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2006ms:
package jp.co.oda32.domain.service.finance;

import jp.co.oda32.audit.AuditLog;
import jp.co.oda32.domain.model.finance.MPartnerGroup;
import jp.co.oda32.domain.repository.finance.MPartnerGroupRepository;
import jp.co.oda32.dto.finance.PartnerGroupRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * パートナーグループ Service。
 *
 * <ul>
 *   <li>SF-04: partnerCodes を 6 桁 0 埋め正規化 + dedup</li>
 *   <li>SF-18: クラスレベル {@code @Transactional(readOnly=true)} + 書込みは個別 override</li>
 *   <li>SF-19: 取得系は JOIN FETCH 経由で N+1 を回避</li>
 *   <li>SF-20: 削除を論理削除化 (del_flg='1')</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MPartnerGroupService {

    private final MPartnerGroupRepository repository;

    public List<MPartnerGroup> findByShopNo(Integer shopNo) {
        // SF-19: JOIN FETCH で N+1 回避 / SF-20: del_flg='0' のみ
        return shopNo != null
                ? repository.findActiveByShopNoFetchMembers(shopNo)
                : repository.findAllActiveFetchMembers();
    }

    public MPartnerGroup findById(Integer id) {
        return repository.findById(id)
                .filter(g -> "0".equals(g.getDelFlg()))
                .orElse(null);
    }

    @Transactional
    @AuditLog(table = "m_partner_group", operation = "INSERT", captureArgsAsAfter = true)
    public MPartnerGroup create(PartnerGroupRequest request) {
        MPartnerGroup group = MPartnerGroup.builder()
                .groupName(request.getGroupName())
                .shopNo(request.getShopNo())
                .delFlg("0")
                .partnerCodes(normalizeAndDedup(request.getPartnerCodes()))
                .build();
        return repository.save(group);
    }

    @Transactional
    @AuditLog(table = "m_partner_group", operation = "UPDATE", captureArgsAsAfter = true)
    public MPartnerGroup update(Integer id, PartnerGroupRequest request) {
        MPartnerGroup group = repository.findById(id)
                .filter(g -> "0".equals(g.getDelFlg()))
                .orElseThrow(() -> new IllegalArgumentException("グループが見つかりません: id=" + id));
        group.setGroupName(request.getGroupName());
        // 非 admin の shopNo 強制上書きは Controller 側で実施済 (SF-03)。
        // ここでは渡された shopNo を信頼して反映 (admin の他 shop 移管を許容)。
        if (request.getShopNo() != null) {
            group.setShopNo(request.getShopNo());
        }
        // SF-04: dedup + 正規化済 list を再代入 (clear/addAll で要素入れ替え)
        List<String> normalized = normalizeAndDedup(request.getPartnerCodes());
        group.getPartnerCodes().clear();
        group.getPartnerCodes().addAll(normalized);
        return repository.save(group);
    }

    @Transactional
    @AuditLog(table = "m_partner_group", operation = "DELETE", captureArgsAsAfter = true)
    public void delete(Integer id) {
        // SF-20: 物理削除 → 論理削除化
        MPartnerGroup group = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("グループが見つかりません: id=" + id));
        if (!"0".equals(group.getDelFlg())) {
            // 既に削除済 → 冪等で OK
            return;
        }
        group.setDelFlg("1");
        repository.save(group);
    }

    /**
     * SF-04: partnerCode リストを 6 桁 0 埋めに正規化 + dedup (LinkedHashSet で順序保持)。
     *
     * <p>例: ["29", "  000029  ", "<181>", "181"] → ["000029", "000181"]
     */
    private List<String> normalizeAndDedup(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String code : raw) {
            String normalized = normalizePartnerCode(code);
            if (normalized != null) {
                seen.add(normalized);
            }
        }
        return List.copyOf(seen);
    }

    /**
     * 得意先コードを 6 桁 0 埋めに正規化する。
     * - "<009896>" → "009896"
     * - "29" → "000029"
     * - " 181 " → "000181"
     * - 数値変換不能 / null / 空文字 → null (caller でスキップ)
     *
     * <p>SF-04: {@link InvoiceImportService#convertPartnerCode(String)} と同等のルール。
     */
    private String normalizePartnerCode(String raw) {
        if (raw == null) return null;
        String code = raw.replace("<", "").replace(">", "").trim();
        if (code.isEmpty()) return null;
        try {
            long numericCode = Long.parseLong(code);
            return String.format("%06d", numericCode);
        } catch (NumberFormatException e) {
            // 非数値 partnerCode は dedup 対象から除外する (Excel 側でも convertPartnerCode が throw する)
            return null;
        }
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2996ms:
package jp.co.oda32.domain.service.finance.mf;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jp.co.oda32.audit.AuditLog;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.TMfOauthToken;
import jp.co.oda32.domain.repository.finance.MMfOauthClientRepository;
import jp.co.oda32.domain.repository.finance.TMfOauthTokenRepository;
import jp.co.oda32.util.OauthCryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * MF OAuth2 クライアント設定・トークンライフサイクル管理。
 * <p>
 * {@link MfApiClient} を使って MF と通信し、暗号化済みトークンを DB に永続化する。
 * Controller はこの Service を介して「接続状態」「認可 URL」「callback 処理」「失効」
 * を操作する。
 *
 * @since 2026/04/20
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class MfOauthService {

    /** access_token 有効期限の何秒前から自動 refresh するか。 */
    private static final long REFRESH_MARGIN_SECONDS = 300L;

    /**
     * redirect_uri ホワイトリスト (B-4)。
     * 登録時に完全一致でチェックし、任意 URL への誘導を防ぐ。運用上追加が必要なら環境変数化も検討。
     */
    private static final Set<String> ALLOWED_REDIRECT_URIS = Set.of(
            "http://localhost:3000/finance/mf-integration/callback",
            "https://odamitsu-data-hub.local/finance/mf-integration/callback"
    );

    /** advisory lock 用キー (refresh_token を同時 refresh しないための直列化)。 */
    private static final long REFRESH_LOCK_KEY = 0x4D46_5245_4641_4331L; // "MFREFAC1"

    private final MMfOauthClientRepository clientRepository;
    private final TMfOauthTokenRepository tokenRepository;
    private final MfApiClient mfApiClient;
    private final OauthCryptoUtil cryptoUtil;
    private final MfOauthStateStore stateStore;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 自己注入。{@code @Transactional(REQUIRES_NEW)} が同一クラス内呼び出しでも
     * Spring AOP proxy 経由になるように。{@code @Lazy} は循環依存回避。
     */
    @Autowired
    @Lazy
    private MfOauthService self;

    /** 登録済みの OAuth クライアントを返す。未設定なら空。 */
    public Optional<MMfOauthClient> findActiveClient() {
        return clientRepository.findFirstByDelFlgOrderByIdDesc("0");
    }

    /** 画面から Client ID / Client Secret 等を登録/更新する（洗い替え）。 */
    @Transactional
    public MMfOauthClient upsertClient(MMfOauthClient requested, String plainClientSecret, Integer userNo) {
        // redirect_uri はホワイトリスト完全一致 (B-4)。ADMIN でも任意 URL 設定は不可。
        validateRedirectUri(requested.getRedirectUri());
        Timestamp now = Timestamp.from(Instant.now());
        MMfOauthClient existing = clientRepository.findFirstByDelFlgOrderByIdDesc("0").orElse(null);
        if (existing != null) {
            existing.setClientId(requested.getClientId());
            if (plainClientSecret != null && !plainClientSecret.isBlank()) {
                existing.setClientSecretEnc(cryptoUtil.encrypt(plainClientSecret));
            }
            existing.setRedirectUri(requested.getRedirectUri());
            existing.setScope(requested.getScope());
            existing.setAuthorizeUrl(requested.getAuthorizeUrl());
            existing.setTokenUrl(requested.getTokenUrl());
            existing.setApiBaseUrl(requested.getApiBaseUrl());
            existing.setModifyDateTime(now);
            existing.setModifyUserNo(userNo);
            return clientRepository.save(existing);
        }
        if (plainClientSecret == null || plainClientSecret.isBlank()) {
            throw new IllegalArgumentException("新規登録時は client_secret が必要です");
        }
        requested.setClientSecretEnc(cryptoUtil.encrypt(plainClientSecret));
        requested.setDelFlg("0");
        requested.setAddDateTime(now);
        requested.setAddUserNo(userNo);
        return clientRepository.save(requested);
    }

    /**
     * 認可 URL (MF ログインページ) を組み立てて返す。
     * 内部で state + PKCE code_verifier を発行し DB 永続化する。
     */
    public AuthorizeUrl buildAuthorizeUrl(Integer userNo) {
        MMfOauthClient client = findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        MfOauthStateStore.Issued issued = stateStore.issue(userNo);
        String url = mfApiClient.buildAuthorizeUrl(client, issued.state(), issued.codeChallenge());
        return new AuthorizeUrl(url, issued.state());
    }

    /**
     * MF からの callback を処理し、トークンを DB 保存して接続状態にする。state 検証 + PKCE 込み。
     *
     * <p>SF-20: HTTP 呼び出しを tx 外に分離する 3 段構成。
     * <ol>
     *   <li>read-only tx: state 消費 + client 取得 ({@link #consumeStateAndLoadClient})</li>
     *   <li>tx 外: MF token endpoint への HTTP (= 数秒?数十秒) を呼ぶ</li>
     *   <li>REQUIRES_NEW tx: token 永続化 ({@link #persistTokenInNewTx})</li>
     * </ol>
     * これで MF API のレイテンシ中に DB connection を占有しない (B-W6 パターンに統一)。
     */
    @AuditLog(table = "t_mf_oauth_token", operation = "auth_callback", captureArgsAsAfter = true)
    public void handleCallback(String code, String state, Integer userNo) {
        // 1) state 消費 + client 読み取り (短い tx)
        CallbackContext ctx = self.consumeStateAndLoadClient(state, userNo);
        // 2) tx 外で HTTP (token 交換)
        MfTokenResponse token = mfApiClient.exchangeCodeForToken(
                ctx.client(), ctx.plainSecret(), code, ctx.codeVerifier());
        // 3) tx 外で HTTP (P1-01: tenant binding 検証)
        //    別会社 MF を誤認可した場合に、ここで MfTenantMismatchException を throw して停止する。
        //    既存 client.mfTenantId が NULL (旧データ互換) なら初回 binding として確定する。
        MfTenantResponse tenant = mfApiClient.getTenant(ctx.client(), token.accessToken());
        // 4) 永続化のみ別 tx (token + tenant binding)
        self.persistTokenAndTenantInNewTx(ctx.client().getId(), token, tenant, userNo);
    }

    /** SF-20: state 消費 + client 取得を 1 つの短い tx で行う。 */
    @Transactional
    public CallbackContext consumeStateAndLoadClient(String state, Integer userNo) {
        Optional<String> verifierOpt = stateStore.verifyAndConsume(state, userNo);
        if (verifierOpt.isEmpty()) {
            throw new IllegalArgumentException("MF OAuth state 検証失敗 (期限切れ or 改ざん)");
        }
        MMfOauthClient client = findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        String plainSecret = cryptoUtil.decrypt(client.getClientSecretEnc());
        return new CallbackContext(client, plainSecret, verifierOpt.get());
    }

    /** SF-20: token 永続化のみ REQUIRES_NEW で実行 (HTTP の長い tx を避ける)。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistTokenInNewTx(Integer clientId, MfTokenResponse token, Integer userNo) {
        MMfOauthClient client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が消えています: " + clientId));
        persistToken(client, token, userNo);
    }

    /**
     * P1-01: token 永続化 + tenant binding を 1 つの tx でまとめて実行。
     * <p>
     * 既存 client に {@code mf_tenant_id} が保存されている場合は一致確認し、
     * 不一致なら {@link MfTenantMismatchException} を throw して tx を rollback する
     * (= token も保存しない)。これにより別会社 MF への誤認可で業務データを汚染しない。
     * <p>
     * 一致 or 初回認可 (mf_tenant_id IS NULL) は client の tenant 列を更新して save する。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistTokenAndTenantInNewTx(
            Integer clientId, MfTokenResponse token, MfTenantResponse tenant, Integer userNo) {
        MMfOauthClient client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が消えています: " + clientId));
        bindOrVerifyTenant(client, tenant, userNo);
        persistToken(client, token, userNo);
    }

    /** {@link #consumeStateAndLoadClient} 戻り値。 */
    public record CallbackContext(MMfOauthClient client, String plainSecret, String codeVerifier) {
        @Override
        public String toString() {
            // SF-02 同様: plainSecret/codeVerifier はマスキング
            return "CallbackContext[clientId=" + (client != null ? client.getId() : null)
                    + ", plainSecret=***, codeVerifier=***]";
        }
    }

    /**
     * 有効な access_token を返す（期限切れなら refresh を試みる）。突合 Service が利用。
     * <p>HTTP refresh とDB永続化を同一 tx で行うと、MF API のレイテンシ中に DB コネクション占有する
     * ため (B-W6)、refresh の HTTP は tx 外で実行し、永続化のみ別 tx に切り出す。
     * <p>同時 refresh による token 上書き race を防ぐため、refresh 経路では PostgreSQL の
     * advisory transaction lock で直列化する。
     */
    public String getValidAccessToken() {
        // 1) 既存 token の読み取り (短い read-only tx)
        TokenSnapshot snap = self.loadActiveTokenSnapshot();
        if (snap.isFresh(REFRESH_MARGIN_SECONDS)) {
            return snap.accessToken;
        }

        // 2) refresh 必要。tx 外で HTTP を呼ぶ。
        log.info("MF access_token を refresh します（expiresAt={}）", snap.expiresAt);
        MfTokenResponse refreshed = mfApiClient.refreshToken(snap.client, snap.plainSecret, snap.plainRefresh);

        // 3) P1-01: refresh 後の tenant 一致検証 (別会社 client_secret に差し替えられた等を検知)。
        //    通常 access_token 利用時は呼ばず、refresh 経路でだけ確認することで負荷を抑える。
        //    旧データ (mf_tenant_id IS NULL) の場合は初回 binding として確定する。
        MfTenantResponse tenant = mfApiClient.getTenant(snap.client, refreshed.accessToken());

        // 4) 永続化のみ REQUIRES_NEW で直列化 (tenant binding も同 tx で確定)
        self.persistRefreshedTokenAndTenant(snap.client.getId(), refreshed, tenant);
        return refreshed.accessToken();
    }

    /** access_token 取得のための既存 token スナップショット。短い read-only tx で取る。 */
    @Transactional(readOnly = true)
    public TokenSnapshot loadActiveTokenSnapshot() {
        MMfOauthClient client = findActiveClient()
                .orElseThrow(() -> new MfReAuthRequiredException("MF クライアント設定が未登録です"));
        TMfOauthToken token = tokenRepository
                .findFirstByClientIdAndDelFlgOrderByIdDesc(client.getId(), "0")
                .orElseThrow(() -> new MfReAuthRequiredException("MF 接続がされていません。認可を実施してください。"));
        return new TokenSnapshot(
                client,
                cryptoUtil.decrypt(client.getClientSecretEnc()),
                cryptoUtil.decrypt(token.getAccessTokenEnc()),
                cryptoUtil.decrypt(token.getRefreshTokenEnc()),
                token.getExpiresAt().toInstant());
    }

    /**
     * refresh 済み token を新 tx で永続化。
     * advisory lock で同時 refresh の race を防ぐ。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistRefreshedToken(Integer clientId, MfTokenResponse refreshed) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:k)")
                .setParameter("k", REFRESH_LOCK_KEY)
                .getSingleResult();
        MMfOauthClient client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が消えています: " + clientId));
        persistToken(client, refreshed, null);
    }

    /**
     * P1-01: refresh 後の token + tenant binding 検証を 1 つの tx でまとめて永続化。
     * tenant 不一致なら {@link MfTenantMismatchException} で rollback (= 古い token のまま残す)。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistRefreshedTokenAndTenant(
            Integer clientId, MfTokenResponse refreshed, MfTenantResponse tenant) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:k)")
                .setParameter("k", REFRESH_LOCK_KEY)
                .getSingleResult();
        MMfOauthClient client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が消えています: " + clientId));
        bindOrVerifyTenant(client, tenant, null);
        persistToken(client, refreshed, null);
    }

    /** MF refresh_token の公称寿命 (P1-04 案 α: rotation 想定で現 active token から起算)。 */
    private static final long REFRESH_TOKEN_LIFETIME_DAYS = 540L;

    public MfTokenStatus getStatus() {
        Optional<MMfOauthClient> client = findActiveClient();
        if (client.isEmpty()) {
            // T6: 未設定時は scope 解析対象なし (= scopeOk=true で banner 非表示)
            return new MfTokenStatus(false, false, null, null, null, false,
                    null, null, null, null, null,
                    List.of(), List.of(), true);
        }
        MMfOauthClient c = client.get();
        Instant boundAt = c.getTenantBoundAt() != null ? c.getTenantBoundAt().toInstant() : null;
        // T6: client.scope (admin が画面で編集できる「次回認可で要求する scope」) を解析。
        // 現 active token の scope (t.getScope()) ではなく client 側を見るのは、
        // admin が必須 scope を削除した時点で (再認可前でも) 構成ドリフトを検知するため。
        MfScopeConstants.ScopeAnalysis scopeAnalysis = MfScopeConstants.analyze(c.getScope());
        boolean scopeOk = scopeAnalysis.missing().isEmpty();
        Optional<TMfOauthToken> token = tokenRepository
                .findFirstByClientIdAndDelFlgOrderByIdDesc(c.getId(), "0");
        if (token.isEmpty()) {
            return new MfTokenStatus(true, false, null, null, null, false,
                    c.getMfTenantId(), c.getMfTenantName(), boundAt, null, null,
                    scopeAnalysis.missing(), scopeAnalysis.extra(), scopeOk);
        }
        TMfOauthToken t = token.get();
        Instant exp = t.getExpiresAt().toInstant();
        Instant lastRefreshed = t.getModifyDateTime() != null
                ? t.getModifyDateTime().toInstant()
                : t.getAddDateTime().toInstant();
        // P1-04 (案 α): rotation で新 token を insert するたびに add_date_time が
        // 更新される前提 (= refresh_token も連動して再発行される)。
        // rotation OFF の場合でも「early warning が早めに出るだけ」で誤検知にはならない。
        Instant refreshTokenIssuedAt = t.getAddDateTime() != null ? t.getAddDateTime().toInstant() : null;
        Integer daysUntilReauth = null;
        boolean reAuthRequired = false;
        if (refreshTokenIssuedAt != null) {
            long elapsedDays = ChronoUnit.DAYS.between(refreshTokenIssuedAt, Instant.now());
            long remaining = REFRESH_TOKEN_LIFETIME_DAYS - elapsedDays;
            // int 安全キャスト (540 日基準で int の範囲を逸脱しない)
            daysUntilReauth = (int) Math.max(Math.min(remaining, Integer.MAX_VALUE), Integer.MIN_VALUE);
            reAuthRequired = daysUntilReauth <= 0;
        }
        return new MfTokenStatus(true, true, exp, t.getScope(), lastRefreshed, reAuthRequired,
                c.getMfTenantId(), c.getMfTenantName(), boundAt,
                refreshTokenIssuedAt, daysUntilReauth,
                scopeAnalysis.missing(), scopeAnalysis.extra(), scopeOk);
    }

    /**
     * 接続切断。DB トークンを論理削除し、tenant binding もクリアする
     * (P1-01: 別 MF tenant への再認可を許可するため)。MF 側 revoke は現時点でベストエフォート省略。
     */
    @Transactional
    @AuditLog(table = "t_mf_oauth_token", operation = "revoke", captureArgsAsAfter = true)
    public void revoke(Integer userNo) {
        MMfOauthClient client = findActiveClient().orElse(null);
        if (client == null) return;
        Timestamp now = Timestamp.from(Instant.now());
        tokenRepository.softDeleteActiveTokens(client.getId(), now, userNo);
        // P1-01: tenant binding をクリアし、次回 callback で別 tenant に bind 可能とする。
        if (client.getMfTenantId() != null) {
            log.info("MF revoke: tenant binding をクリアします clientId={} previousTenantId={} previousTenantName={}",
                    client.getId(), client.getMfTenantId(), client.getMfTenantName());
            client.setMfTenantId(null);
            client.setMfTenantName(null);
            client.setTenantBoundAt(null);
            client.setModifyDateTime(now);
            client.setModifyUserNo(userNo);
            clientRepository.save(client);
        }
    }

    /**
     * P1-01: tenant binding の検証 + 必要なら確定。
     * <ul>
     *   <li>既存 {@code mf_tenant_id} が NULL → 初回 binding として確定 (旧データ互換)</li>
     *   <li>既存 {@code mf_tenant_id} が一致 → tenant_name のみ更新 (改名追従)</li>
     *   <li>既存 {@code mf_tenant_id} が不一致 → {@link MfTenantMismatchException} を throw</li>
     * </ul>
     */
    private void bindOrVerifyTenant(MMfOauthClient client, MfTenantResponse tenant, Integer userNo) {
        if (tenant == null || tenant.id() == null || tenant.id().isBlank()) {
            throw new IllegalStateException("MF tenant id を取得できませんでした");
        }
        String observedId = tenant.id();
        String observedName = tenant.name();
        Timestamp now = Timestamp.from(Instant.now());

        if (client.getMfTenantId() == null) {
            // 初回 binding (旧データ互換 or 初回認可)
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content claudedocs/design-finance-audit-log.md' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content claudedocs/design-auth-matrix.md' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content claudedocs/design-exception-hierarchy.md' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content claudedocs/design-source-of-truth-hierarchy.md' in C:\project\odamitsu-data-hub
 succeeded in 3203ms:
# T2: finance 監査証跡基盤 (Audit Log) 設計書

作成日: 2026-05-04
対象ブランチ: `refactor/code-review-fixes`
実装方式: 案 B (Service 層 AOP)
旧ドラフト: `claudedocs/design-audit-trail-accounts-payable.md` (買掛側専用、Hibernate `@EntityListeners` 想定)。本書はそれを全 finance に拡張し、AOP 実装に切替えた版。

---

## 1. 概要

T1 (権威階層 / 不変条件文書化) で「どの値がどの操作で書き換わるか」は明文化済み。
本 T2 は「**いつ・誰が・何を変更したか**」を補完する。

### 1.1 採用方式: AOP (案 B)

`@AuditLog` アノテーションを付与した Service メソッドを Aspect で横断し、呼び出し前後の引数・戻り値を `finance_audit_log` (JSONB) に書き込む。

| 比較項目 | 案 A: Entity Listener | **案 B: Service AOP** |
|---|---|---|
| 設置箇所 | Entity 単位 (`@EntityListeners`) | Service メソッド単位 (`@AuditLog`) |
| 監視粒度 | 全 INSERT/UPDATE/DELETE 自動 | 明示マーク必須 |
| ビジネス操作の意味付け | 困難 (verify と batch update の区別不可) | `operation="verify"` で明示 |
| バッチ経由 SQL UPDATE 補足 | できる | できない (Service 経由のみ) |
| 将来の業務拡張時の手間 | 自動 | 都度 `@AuditLog` 追加が必要 |

ビジネス操作の意味 (verify / mf_apply / import 等) を残せる方が監査ログの実用性が高いため案 B を採用。バッチ経由の集計上書きは別途 `t_payment_mf_import_history` 等に既存履歴があり、本基盤の対象外とする。

---

## 2. インフラ

### 2.1 テーブル: `finance_audit_log` (V036)

```sql
CREATE TABLE finance_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    occurred_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_user_no   INTEGER,                              -- NULL = SYSTEM/BATCH
    actor_type      VARCHAR(20) NOT NULL DEFAULT 'USER',  -- USER / SYSTEM / BATCH
    operation       VARCHAR(50) NOT NULL,
    target_table    VARCHAR(100) NOT NULL,
    target_pk       JSONB NOT NULL,
    before_values   JSONB,
    after_values    JSONB,
    reason          TEXT,
    source_ip       VARCHAR(45),
    user_agent      VARCHAR(500)
);
```

3 つの index: `(target_table, occurred_at DESC)` / `(actor_user_no, occurred_at DESC)` / `(occurred_at DESC)`。

### 2.2 アノテーション

- `@AuditLog(table="...", operation="...", captureArgsAsAfter=true|false, captureReturnAsAfter=true|false)`
  - Service の public メソッドに付与
  - `captureArgsAsAfter`: 引数 JSON を after に保存 (DELETE / 単純更新用)
  - `captureReturnAsAfter`: 戻り値 JSON を after に保存 (返り値が Entity の場合)
- `@AuditExclude` (フィールド): 機密 / 大きい blob を JSON 出力から除外。`@JacksonAnnotationsInside @JsonIgnore` のメタアノテーションなので REST API レスポンスからも自動除外される (Entity を直接 API で返している箇所がないことを事前確認のうえ採用)

### 2.3 AOP

- `FinanceAuditAspect`: `@Around("@annotation(auditLog)")` で前後フック
  - SecurityContext から `LoginUser` を取得し `actor_user_no` 解決 (取れなければ SYSTEM)
  - HttpServletRequest の `X-Forwarded-For` / `User-Agent` を IP/UA 列に記録
  - 専用 ObjectMapper (JavaTimeModule + FAIL_ON_SELF_REFERENCES disabled) で循環参照を回避
  - `@ApplicationType("web")` で web プロファイル限定 (バッチでは起動しない)
- `FinanceAuditWriter`: 別 Bean で `@Transactional(REQUIRES_NEW)`。業務 tx の rollback でも失敗操作の証跡を残す
- 例外時: `reason="FAILED:<exception>"` で記録してから rethrow
- 監査ログ書込み失敗: 業務 tx 成功は巻き戻さず WARN ログのみ (P1 = 業務優先)

---

## 3. 適用 Service (Phase 2)

| Service | メソッド | table | operation |
|---|---|---|---|
| `TAccountsPayableSummaryService` | `verify` | `t_accounts_payable_summary` | `verify` |
| 〃 | `releaseManualLock` | 〃 | `release_manual_lock` |
| 〃 | `updateMfExport` | 〃 | `mf_export_toggle` |
| `TAccountsReceivableSummaryService` | `verify` | `t_accounts_receivable_summary` | `verify` |
| 〃 | `releaseManualLock` | 〃 | `release_manual_lock` |
| 〃 | `updateMfExport` | 〃 | `mf_export_toggle` |
| `AccountsReceivableBulkVerifyService` | `execute` | `t_accounts_receivable_summary` | `bulk_verify` |
| `ConsistencyReviewService` | `upsert` | `t_consistency_review` | `upsert` |
| 〃 | `delete` | 〃 | `delete` |
| `PaymentMfImportService` | `applyVerification` | `t_accounts_payable_summary` | `payment_mf_apply` |
| `MfOauthService` | `handleCallback` | `t_mf_oauth_token` | `auth_callback` |
| 〃 | `revoke` | 〃 | `revoke` |
| `MfOpeningBalanceService` | `fetchFromMfJournalOne` | `m_supplier_opening_balance` | `mf_fetch` |
| 〃 | `updateManualAdjustment` | 〃 | `manual_adjust` |
| `InvoiceImportService` | `importFromExcel` | `t_invoice` | `import` |
| `MPartnerGroupService` | `create` / `update` / `delete` | `m_partner_group` | `INSERT` / `UPDATE` / `DELETE` |

Phase 2 = 16 メソッド・8 Service。

---

## 4. 機密フィールド除外 (Phase 3)

`@AuditExclude` 付与:
- `MMfOauthClient.clientSecretEnc`
- `TMfOauthToken.accessTokenEnc` / `refreshTokenEnc`
- `TPaymentMfImportHistory.csvBody`
- `TCashbookImportHistory.csvContent`

(計 5 フィールド)

これらは API 経由で返却されないことを事前確認のうえ `@JsonIgnore` 同等の挙動を許容。

---

## 5. 閲覧画面 (Phase 4)

### 5.1 Backend API

`AuditLogController` (`/api/v1/admin/audit-log`、`@PreAuthorize("@loginUserSecurityBean.isAdmin()")`)
- `GET /search` : フィルタ (actor_user_no / operation / target_table / 日付範囲) + ページング
- `GET /{id}` : 詳細 (before/after JSONB 含む)
- `GET /operations` / `GET /tables` : フィルタ用 distinct 値

`AuditLogQueryService` で actor 名の batch JOIN (N+1 回避)。

### 5.2 Frontend

- ルート: `/admin/audit-log` (admin 限定 + サイドバー adminOnly メニュー)
- フィルタ: 対象テーブル / 操作 / From / To
- 一覧: 日時 / 種別 (USER/SYSTEM/BATCH) / 実行者 / 操作 / 対象 / PK / [詳細]
- 詳細 Dialog: PK + before/after を左右並列で `JSON.stringify(value, null, 2)` 表示。reason があれば警告バー表示

---

## 6. AOP オーバーヘッド

| 想定値 | 内訳 |
|---|---|
| 1 操作あたり | +5?30 ms |
| 内訳 | Jackson `valueToTree` (引数/戻り値の JSON 化) + `repository.save(FinanceAuditLog)` (1 INSERT) |
| 業務影響 | 経理操作は秒単位の手動操作が多く許容範囲 |
| 例外パス | +5 ms 程度 (Jackson + 1 INSERT は同じ) |

ボトルネックになりうるケース:
- `payment_mf_apply` (1 操作で 100+ supplier 上書き) → 戻り値 `VerifyResult` 1 つのシリアライズなので問題なし。Aspect は呼び出し回数ベース (1 回) で課金される
- `bulk_verify` (得意先全件) → 同上、引数は (shopNo, fromDate, toDate) のみで軽量

---

## 7. 残課題 / 将来拡張

| # | 項目 | 優先度 |
|---|---|---|
| F1 | バッチ経由の操作 (集計バッチ等) を補足する Step Listener / Job Listener (T2 案 C 相当) | 中 |
| F2 | 案 A (Entity Listener) との併用で 直接 SQL UPDATE も網羅 | 低 (現状アプリ経由のみ想定) |
| F3 | finance_audit_log のパーティション / アーカイブ (7 年保存要件) | 中 (ストレージ試算後) |
| F4 | 閲覧履歴 (`*_access_log`) ? 個人情報アクセス証跡 | 低 (内部統制要件次第) |
| F5 | 月次決算締めテーブル `t_accounting_closure` で締め後 UPDATE を 409 化 | 中 (旧 M7 設計を踏襲) |
| F6 | before snapshot を PK で再 fetch する `AuditEntityLoader` 追加 (現状 `captureArgsAsAfter` のみ) | 中 |

---

## 8. 関連設計書

- `design-audit-trail-accounts-payable.md` (旧ドラフト、買掛側のみ)
- `design-accounts-payable.md` / `design-accounts-payable-ledger.md`
- `design-consistency-review.md`
- `design-payment-mf-import.md`
- `design-mf-integration.md`

---

## 9. リリースチェックリスト

- [x] V036 migration 適用
- [x] backend compileJava + test 全 PASS
- [x] frontend tsc PASS
- [ ] 実 DB に対する E2E (実際にログが書き込まれることを確認、推奨 1 ケース)
- [ ] admin login で `/admin/audit-log` UI 表示・フィルタ・詳細 dialog 動作確認
- [ ] 非 admin でアクセスして 403 が返ることを確認
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 4148ms:
# Finance 例外階層 (T5)

## 1. 設計方針

Finance パッケージ内 (`jp.co.oda32.api.finance.*`, `jp.co.oda32.domain.service.finance.*`) で発生する
業務エラー / 内部状態異常を、意図に応じて型レベルで明確化する。

| 意図 | 型 | HTTP | client への message |
|---|---|---|---|
| ユーザーに伝える業務エラー | `FinanceBusinessException` | 400 | 元メッセージそのまま |
| 内部状態異常 (機微情報含む可能性) | `FinanceInternalException` | 422 | 「内部エラーが発生しました」固定 |
| 引数バリデーション (null / 形式不正) | `IllegalArgumentException` | 400 | 元メッセージそのまま (GlobalExceptionHandler) |
| プログラマバグ (assertion 違反) | `IllegalStateException` | 422 | 「内部エラーが発生しました」固定 (FinanceExceptionHandler) |
| 認可違反 (他 shop / 他事業部) | `ResponseStatusException(FORBIDDEN)` | 403 | 元メッセージ |
| リソース不在 | `ResponseStatusException(NOT_FOUND)` | 404 | 元メッセージ |

## 2. 既存 Handler との関係

```
HTTP request
    │
    ▼
FinanceExceptionHandler (basePackages = jp.co.oda32.api.finance) ← 優先
    ├─ MfReAuthRequiredException → 401
    ├─ MfScopeInsufficientException → 403
    ├─ MfTenantMismatchException → 409
    ├─ FinanceBusinessException → 400 (NEW T5)
    ├─ FinanceInternalException → 422 (NEW T5)
    └─ IllegalStateException → 422 (汎用化、後方互換)
            │
            ▼
GlobalExceptionHandler (Finance package 外でも有効)
    ├─ MethodArgumentNotValidException → 400 + field errors
    ├─ BadCredentialsException → 401
    ├─ MaxUploadSizeExceededException → 400
    ├─ IllegalArgumentException → 400 + message
    ├─ AccessDeniedException → 403
    ├─ ResponseStatusException → 元 status + reason
    └─ Exception → 500 + 「システムエラーが発生しました」
```

## 3. 開発ガイドライン

### 新規コードでの選択

- **業務メッセージを伝えたい** → `FinanceBusinessException`
  - 例: 「未登録の送り先があります（3件）」「指定期間に MF 出力対象の売掛金がありません」
- **内部 state 異常 (機微情報含む可能性)** → `FinanceInternalException`
  - 例: 「買掛金集計の taxRate が null です: shopNo=1, supplierNo=123」「経理ステータス SQL の実行に失敗しました」
- **引数バリデーション** → `IllegalArgumentException`
  - 例: 「fromDate は toDate 以前を指定してください」「targetDate は yyyyMMdd 形式」
- **プログラマバグ (内部 assertion)** → `IllegalStateException` 維持
  - 例: 「未知の税区分リゾルバ: ...」「aggregateMonth: empty group」「SHA-256 unavailable」

`IllegalStateException` の **新規使用は避ける**。
理由: ハンドラで「内部エラーが発生しました」に汎用化されるため、ユーザーに伝えたい業務メッセージが消失する。

### 機微情報判定の指針

以下を含むメッセージは `FinanceInternalException` (汎用化される):
- 内部 ID (supplier_no, partner_no, user_no 等)
- DB カラム名 / SQL 断片
- API キー / トークン / レスポンス raw body
- ファイルパス / スタックトレース風情報

以下を含むメッセージは `FinanceBusinessException` (そのまま返す):
- ユーザーが画面で操作可能な事項 (「マスタ登録後に再試行」等)
- 件数 / 期間情報 (「3件」「指定期間」等)
- ユーザーが認知している取引先名 / 支払先名 (内部 ID ではない自然名)

## 4. 既存コードからの移行 (T5 で実施)

Phase 別に段階適用:

### Phase 3 (置換) 実施分 (2026-05-04 T5)

**FinanceBusinessException 化** (4 箇所):
- `PaymentMfImportService.java:175` 「未登録の送り先があります」(Cluster C M1 既知バグ修正)
- `MfJournalFetcher.java:86` 「MF fiscal year 境界エラー」
- `AccountsReceivableController.java:271` 「指定期間に MF 出力対象の売掛金がありません」 (旧 ResponseStatusException)
- `FinanceController.java:336` 「出力対象のデータがありません」 (旧 ResponseStatusException)

**FinanceInternalException 化** (6 箇所):
- `AccountingStatusService.java:145` 「経理ステータス SQL の実行に失敗しました」
- `PaymentMfCsvWriter.java:66` 「CSV出力に失敗しました」
- `SalesJournalCsvService.java:201` 「アカウントマスタで重複キー」
- `PurchaseJournalCsvService.java:193` 「金額に小数点以下が含まれています」
- `TAccountsPayableSummaryService.java:170` 「買掛金集計の taxRate が null」
- `MfJournalFetcher.java:156` 「MF journals ページング safeguard を超過」
- `AccountsReceivableController.java:135` 「集計ジョブが見つかりません」

**維持** (内部 assertion / プログラマバグ系):
- `AccountsPayableLedgerService.java:133` "aggregateMonth: empty group"
- `MfTaxResolver.java:30` "未知の税区分リゾルバ"
- `MfApiClient.java:238, 298` MF API レスポンス protocol 違反
- `PaymentMfImportService.java:1437` "SHA-256 unavailable"
- `MfOauthService.java:344` "MF tenant id を取得できませんでした"
- `MfOauthStateStore.java:112` "SHA-256 が利用できません"

**維持** (ResponseStatusException FORBIDDEN/NOT_FOUND):
- `AccountsReceivableController.java:306` "他ショップ" (FORBIDDEN)
- `FinanceController.java:180, 447` "他ショップ/他事業部" (FORBIDDEN)
- `FinanceController.java:533, 550` "グループが見つかりません" (NOT_FOUND)

## 5. Frontend 対応

### 現状調査結果 (Phase 0)

`frontend/lib/api-client.ts`:
- `class ApiError extends Error { status: number }` のみ。`body` フィールド無し。
- 非 ok レスポンス時は `throw new ApiError(status, errorBody as text)`。
  body 全体が `e.message` に入るため、JSON エラーボディの場合は raw JSON 文字列がそのまま入る。
- 例外: `uploadForm` (postForm) のみ `errorBody.message` を取り出す。
- `downloadBlob` は `body.message` を抽出する。

### 主要 page のエラー処理パターン

ほぼ全ページが `onError: (e: Error) => toast.error(e.message)` を採用。
status code 分岐は `accounts-payable-ledger.tsx` (401/403 のみ判別) など僅か。
JSON.parse(e.message) で `body.message` を取り出すのは `bcart/shipping.tsx` のみ。

### 業務メッセージ消失影響

- 旧: `IllegalStateException("未登録の送り先があります（3件）")` → 422 + body `{message: "内部エラーが発生しました"}` →
  toast に「内部エラーが発生しました」と表示され業務情報が消失。
- 新: `FinanceBusinessException("未登録の送り先があります（3件）")` → 400 + body `{message: "未登録の送り先があります（3件）", code: "BUSINESS_ERROR", ...}` →
  `e.message` には raw JSON 文字列全体が入る。

### Frontend 側の追加対応 (将来課題、本 T5 では未実施)

`api-client.ts` で非 ok レスポンスの JSON parse + `body.message` 抽出を一元化することで、
`postForm` 以外の `get/post/put/patch/delete` でも `e.message` に業務メッセージが入るようになる。
ただしこれは互換性影響が広いため別タスク (T5 後続) で扱う。

当面は:
- `bcart/shipping.tsx` 同様、各 page で `JSON.parse(e.message)` する pattern が必要なケースは個別対応
- もしくは `api-client.ts` 側で raw text→JSON 抽出 fallback を入れる

将来の拡張: ErrorResponse の `code` フィールドで案件別分岐可能 (i18n 不要なので当面不要)。

## 6. 関連 DESIGN-DECISION

- T5 (本ドキュメント): 例外ハンドリング統一
- Cluster F SF-25: FinanceExceptionHandler 導入
- Cluster C M1: IllegalStateException → IllegalArgumentException 格上げ実例 (PaymentMfImportService の既知バグ報告)
- MA-01 (FinanceExceptionHandler 内コメント): IllegalStateException メッセージは内部詳細を含むため client には汎用化

## 7. テスト

`FinanceExceptionHandlerTest` (新規):
- `FinanceBusinessException` → 400 + 元メッセージ + 指定 code
- `FinanceBusinessException` (code 未指定) → 400 + "BUSINESS_ERROR"
- `FinanceInternalException` → 422 + 「内部エラーが発生しました」 + "INTERNAL_ERROR"
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 5109ms:
# 数字の権威階層 (Source of Truth Hierarchy)

作成日: 2026-05-06
対象: 経理・財務管理の全機能 (買掛金・売掛金・MF 連携)
DESIGN-DECISION: T1
ステータス: 確定 (案 A 採用)

## 1. 目的と背景

経理処理では同じ取引について複数の「金額」が存在する。例えば 1 件の仕入には:

- **請求書 (原本)**: 仕入先からの請求金額
- **SMILE 仕入実績**: 自社基幹で計算した仕入額
- **自社買掛集計**: 月次バッチで集計した買掛金額
- **振込確定額**: 振込明細 Excel から取り込んだ実際の振込額
- **MF 仕訳**: マネーフォワード会計に登録された仕訳金額

これらは **本来一致するべき** だが、入力ズレ・按分丸め・値引/早払/送料/相殺・経過月差などで微妙に不一致になる。

画面ごとに参照する数字が異なる場合、

- 経理担当: 「どっちが正しいの?」と混乱
- 税理士監査時: 「この数字とその数字、なぜ違う?」と説明できない
- 開発: 「どの数字を信用してロジックを組むか」が曖昧

これを防ぐため、**権威階層 (どの数字が "正" か)** を明文化する。

## 2. 数字源の一覧

| 数字源 | 物理ストレージ | 業務的意味 | 権威 |
|---|---|---|---|
| 仕入先請求書 (原本) | 紙 / PDF | 仕入先からの請求金額の最終権威 | ★★★ |
| SMILE 仕入実績 | `t_purchase` / `t_purchase_detail` | 自社基幹で計算した仕入額 (経理が SMILE 入力) | ★★ |
| 自社買掛集計 (税込) | `t_accounts_payable_summary.tax_included_amount_change` | 月次バッチで supplier × 税率に集計 | ★ |
| 振込確定額 | `t_accounts_payable_summary.verified_amount` | 振込明細 Excel or 手動から確定 | ★★ |
| MF 仕訳 | MF API `/journals` | マネーフォワード会計に登録された仕訳 | ★★ |
| MF 試算表 | MF API `/trial_balance/bs` | MF 上の月末残高 (検証用のみ) | ★★ |
| 期首残 (買掛) | `m_supplier_opening_balance` | MF 期首残高仕訳 (#1) + 手動補正 | ★★ |
| 累積残 (closing) | 計算: `opening + change ? payment_settled` | 自社 T 勘定定義 | ★ (派生) |
| 売掛 請求 | `t_invoice` | SMILE 請求実績 Excel から取込 | ★★ |
| 売掛 検証済 | `t_accounts_receivable_summary.verified_amount` | 入金確定額 | ★★ |
| 銀行通帳 | (MF API 経由 or 紙) | 実際の出金・入金 | ★★★ |

★★★ = 取引の実体 (これが事実)
★★ = 実体を写す一次データ (人または基幹システムが入力した一次情報)
★ = 一次データから派生する集計値 (バッチ計算結果)

## 3. 権威階層

### 3.1 請求金額 (買掛)

```
1. 仕入先請求書 (原本)              ← 最終権威 (取引の実体)
   ↓ 経理が SMILE に手入力 / Excel 取込
2. SMILE 仕入実績 (t_purchase)
   ↓ 月次バッチ aggregation で supplier × 税率に集計
3. 自社買掛集計 (t_accounts_payable_summary.tax_included_amount_change)
   ↓ MF CSV 出力
4. MF 仕入仕訳 (借方 仕入高)
```

「上位 = 真」。下位で不一致が出たら **下位を上位に合わせる** (ボトムアップで上書きしない)。

### 3.2 振込金額 (買掛)

```
1. 銀行通帳 (実際の出金)            ← 最終権威 (取引の実体)
   ↓ MF 銀行データ連携 or 振込明細 Excel
2. t_accounts_payable_summary.verified_amount
   ↓ MF CSV 出力 (verified-export, 検証済のみ)
3. MF 振込仕訳 (貸方 普通預金)
```

### 3.3 残高 (累積)

```
期首残 (opening):
  m_supplier_opening_balance.effective_balance
  = MF journal #1 (期首残高仕訳) + 手動補正
  → 累積残一覧 / 整合性レポート / 買掛帳 で **同一参照** (P1-02 で統一)

月中残 (change):
  自社計算  = opening + Σ tax_included_amount_change ? Σ payment_settled
  MF       = MF /journals の credit ? debit を月次集計

月末残 (closing):
  closing = opening + change ? payment_settled
  ※ 自社 T 勘定の定義。MF 試算表 (/trial_balance/bs) と突合する
```

### 3.4 売掛 (構造は買掛と対称)

```
1. SMILE 請求実績 Excel (得意先別請求書)
   ↓ Excel 取込
2. t_invoice.current_billing_amount
   ↓ 月次集計
3. t_accounts_receivable_summary.tax_included_amount_change
   ↓ MF CSV 出力
4. MF 売掛仕訳 (借方 売掛金)

入金:
1. 銀行通帳 (入金実績)
2. t_accounts_receivable_summary.verified_amount
3. MF 入金仕訳 (貸方 売掛金)
```

## 4. 不一致時のルール

### 4.1 請求書 vs SMILE 仕入実績

| 状況 | 対応 |
|---|---|
| SMILE > 請求書 | 経理担当が SMILE を修正 (請求書を正とする) |
| SMILE < 請求書 | 同上 |
| 修正後 | 月次集計バッチ (`accountsPayableAggregation`) で `t_accounts_payable_summary` に反映 |

→ **業務手順**: 月初に請求書チェック → SMILE 修正 → 集計バッチ

### 4.2 SMILE 仕入実績 vs 振込実績

| 状況 | 対応 |
|---|---|
| 一致 (差額 ? \100) | `verification_result=1` で確定 |
| 差額あり | `t_accounts_payable_summary.verification_note` に差額理由を記録 |
| 値引/早払/送料/相殺 | per-supplier に attribute (P1-03 案 D-2 実装済)、税理士監査時に説明可能 |
| `verified_amount` の計算過程 | `auto_adjusted_amount` (V026) に税抜逆算 + 差額記録 |

→ **画面**: `/finance/accounts-payable` の「振込明細で一括検証」または個別の「検証」ダイアログ

### 4.3 自社買掛集計 vs MF

| 状況 | 対応 |
|---|---|
| 一致 (累積差 = 0) | OK |
| 金額差 (MINOR ? \1,000) | 整合性レポート (`/finance/accounts-payable-ledger/integrity`) で diff 検出、軽量 |
| 金額差 (MAJOR > \1,000) | 同上、要対応 |
| MF 側のみ | 自社で取り込み漏れ → 手動仕訳追加 or `MF_APPLY` で自社に書き戻し |
| 自社側のみ | MF への CSV 出力漏れ → CSV 再出力 or 手動仕訳 |

**操作 (`t_consistency_review`)**:
- `IGNORE`: 監査済み・差額許容として記録 (例: MF 側の取引を自社に取り込まない判断)
- `MF_APPLY`: MF 値を自社の `verified_amount` に書き戻し (差額理由を `verification_note` に記録)

snapshot 比較で金額が再変動した場合は自動的に再表示 (V027)。

### 4.4 累積残一覧 vs 整合性レポート

P1-02 で **同一参照源** に統一済:

- `PayableBalanceCalculator` を 1 箇所に集約
- `m_supplier_opening_balance` を opening 注入として両画面で同一参照
- 両画面の cumulative diff は同値で表示される

→ 不一致が起きないことを保証。

## 5. 各画面での参照元一覧

| 画面 | 列 / フィールド | 参照する数字源 | 権威階層上の位置 |
|---|---|---|---|
| `/finance/accounts-payable` (買掛金一覧) | 買掛金額(税込) | `tax_included_amount_change` | ★ PAYABLE_SUMMARY |
| | 振込明細額 | `verified_amount` | ★★ VERIFIED_AMOUNT |
| | 差額 | `payment_difference` (派生) | (派生) |
| `/finance/accounts-payable-ledger` (買掛帳) | 前月繰越 | `opening_balance_tax_included` (期首残注入) | ★★ OPENING_BALANCE |
| | 仕入 | `change_tax_included` | ★ PAYABLE_SUMMARY |
| | 検証額 | `verified_amount` | ★★ VERIFIED_AMOUNT |
| | 支払反映 | `payment_settled_tax_included` (派生) | (派生) |
| | 当月残 | `closing_balance_tax_included` (派生) | (派生) CLOSING_CALC |
| | MF delta | MF `/journals` credit ? debit | ★★ MF_JOURNAL |
| | MF 累積残 | MF `/trial_balance/bs` (or journals 累積) | ★★ MF_TRIAL_BALANCE |
| `/finance/accounts-payable/supplier-balances` (累積残一覧) | self 残 | `closing` (派生) | (派生) CLOSING_CALC |
| | MF 残 | MF `/journals` 累積 | ★★ MF_JOURNAL |
| | opening | `m_supplier_opening_balance` | ★★ OPENING_BALANCE |
| `/finance/accounts-payable-ledger/integrity` (整合性レポート) | 自社差額 | `selfDelta` (派生) | (派生) |
| | MF 差額 | MF `/journals` (派生) | ★★ MF_JOURNAL |
| | 累積差 | self vs MF (派生) | (派生) |
| `/finance/accounts-receivable` (売掛金一覧) | 税込金額 | `tax_included_amount_change` | ★ PAYABLE_SUMMARY (AR 版) |
| | 請求書金額 | `invoice_amount` | ★★ AR_INVOICE |
| `/finance/payment-mf-import` (買掛仕入 MF 変換) | 請求額 | Excel 取込値 (= 請求書) | ★★★ INVOICE |
| | 買掛金 | `t_accounts_payable_summary` | ★ PAYABLE_SUMMARY |
| `/finance/invoices` (請求書一覧) | 今回請求額 | `t_invoice.current_billing_amount` | ★★ AR_INVOICE |
| `/finance/supplier-opening-balance` (期首残管理) | 実効値 | `m_supplier_opening_balance.effective_balance` | ★★ OPENING_BALANCE |
| | MF 取得値 | MF journal #1 | ★★ MF_JOURNAL |
| `/finance/cashbook-import` (現金出納帳取込) | 借方/貸方金額 | Excel 取込値 (= 出納帳原本) | ★★★ (file source) |
| `/finance/mf-health` (MF 連携ヘルスチェック) | OAuth / cache | (検証用 metric, 金額表示なし) | n/a |

## 6. 税理士・監査人への説明資料

年次決算時にこのドキュメントを提示することで:

1. **各画面の数字の由来を説明可能**
   - 「買掛金一覧の `振込明細額` は、振込明細 Excel から取り込んだ実際の出金額です。銀行通帳と一致します。」
2. **不一致発生時の対応プロセスを説明可能**
   - 「MF と自社で差額が出た場合、`/finance/accounts-payable-ledger/integrity` で diff を検出し、`MF_APPLY` で MF を正として書き戻すか、`IGNORE` で監査済みマークします。これらは `t_consistency_review` に履歴が残ります。」
3. **権威階層を提示可能**
   - 「請求書 (原本) > SMILE 入力 > 自社集計 > MF 仕訳 の順で正しさを定義しています。下位で問題が出たら上位を確認します。」

## 7. UI 上の表現 (案 A)

各画面のテーブル **ヘッダー** に `<AmountSourceTooltip source="...">` を表示:

- ホバーで「この列は何由来か」「物理ストレージ」「業務的意味」を表示
- ヘッダーのみに付与 (各セルに付けるとうるさい)
- 権威階層の説明はこのドキュメント (DESIGN-DECISION T1) に集約

実装:

- `frontend/components/common/AmountSourceTooltip.tsx`
- shadcn/ui Tooltip (radix-ui ベース) を使用
- AmountSource enum で 10 種を定義 (INVOICE / SMILE / PAYABLE_SUMMARY / VERIFIED_AMOUNT / MF_JOURNAL / MF_TRIAL_BALANCE / OPENING_BALANCE / CLOSING_CALC / AR_INVOICE / AR_VERIFIED)

## 8. 関連 DESIGN-DECISION

| ID | 内容 | 関連 |
|---|---|---|
| T1 | 数字の権威階層 (本ドキュメント) | UI 表現 + 監査説明資料 |
| T2 | 監査証跡基盤 (誰がいつ何を変更したか) | 未着手 (`design-audit-trail-accounts-payable.md` ドラフト済) |
| P1-02 | opening 注入方針確定 (累積残/整合性 統一) | `m_supplier_opening_balance` を両画面で同一参照 |
| P1-03 案 D-2 | per-supplier 値引/早払 attribute | `verification_note` + `auto_adjusted_amount` |
| P1-09 案 D | `verified_amount` 不変条件文書化 | 手動確定行は集計バッチで上書きしない |
| V026 | 自動調整 (`auto_adjusted_amount`) | 税抜逆算 + 差額記録 |
| V027 | `t_consistency_review` (案 X+Y) | IGNORE / MF_APPLY 履歴 |

## 9. 補足: 「業務実態」と「設計書」の優先順位

設計書 (`claudedocs/design-*.md`) と運用実態が食い違う時は **運用実態を優先** する。
特にゴールデンマスタが empty stub している箇所は PASS が正当性を保証しない。

参考: `MEMORY.md feedback_design_doc_vs_operations`
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 6129ms:
# 認可マトリクス (T4)

## 1. 統一方針
全 Controller の admin 判定を `@PreAuthorize("@loginUserSecurityBean.isAdmin()")` で統一。
`hasRole('ADMIN')` / `authentication.principal.shopNo == 0` の 2 系統並存を撤廃。

## 2. LoginUser.isAdmin() 仕様

判定式: `MLoginUser.getShopNo() == 0` かつ `Authorities` に `ROLE_ADMIN` を含む。
両者が乖離する場合 (DB 設定不整合等) は `false` (fail-safe)。

### shopNo の解決
`MLoginUser.getShopNo()` は CompanyType を見て返す:
- ADMIN: `OfficeShopNo.ADMIN.getValue()` = 0 固定
- SHOP: `m_company.shop_no`
- PARTNER: `m_company.m_partner.shop_no`
- 該当なし: -1

### Authority の付与
`LoginUser.resolveRoles()` で CompanyType に応じて付与:
- ADMIN → ROLE_USER + ROLE_ADMIN
- SHOP → ROLE_USER + ROLE_SHOP
- PARTNER → ROLE_USER + ROLE_PARTNER

ユーザー判断 (Q1=(a)) により shopNo=0 と ROLE_ADMIN は常に同期する前提。
不整合発生時は fail-safe で admin 権限を拒否。

## 3. 認可レベル

| レベル | アノテーション | 用途 |
|---|---|---|
| 認証なし | (なし、SecurityFilterChain で制御) | `/api/v1/auth/login` |
| 認証あり | `@PreAuthorize("isAuthenticated()")` | 一般 endpoint (class level) |
| admin 限定 | `@PreAuthorize("@loginUserSecurityBean.isAdmin()")` | マスタ更新、バッチ実行、MF 連携、財務処理など |
| shop user 自 shop 限定 | `LoginUserUtil.resolveEffectiveShopNo()` (service 層) | 個別 endpoint で shop_no を要求するクエリ |

`isAdmin()` と `resolveEffectiveShopNo()` は別目的:
- `isAdmin()`: admin 専用画面/操作のゲート
- `resolveEffectiveShopNo()`: 認証済 shop user が他 shop データを覗けないようにする IDOR ガード

両者は併用可能 (例: admin endpoint 内で更に shop 単位の絞り込み、または認証済全員が自 shop データのみ閲覧)。

## 4. SpEL Bean 仕様

```java
@Component("loginUserSecurityBean")
public class LoginUserSecurityBean {
    public boolean isAdmin();      // SpEL: @loginUserSecurityBean.isAdmin()
    public boolean isShopUser();   // SpEL: @loginUserSecurityBean.isShopUser()
}
```

未認証 / principal が `LoginUser` でない場合は両方 false (fail-safe)。

## 5. 移行履歴

### T4 (2026-05-06): 認可マトリクス統一
**Before** (3 系統並存):
1. `@PreAuthorize("hasRole('ADMIN')")` ? 29 箇所
2. `@PreAuthorize("authentication.principal.shopNo == 0")` ? 8 箇所
3. service 層 `LoginUserUtil.resolveEffectiveShopNo()` ? 個別

**After** (1+2 を統一):
- `@PreAuthorize("@loginUserSecurityBean.isAdmin()")` ? 47 箇所 (Finance + その他 13 ファイル)
- `LoginUserUtil.resolveEffectiveShopNo()` は別目的のため継続

### 関連 fix
- P1-01 (Cluster F): MF tenant binding (admin 限定)
- P1-05 (Cluster F): OAuth 鍵分離 (admin 限定)
- Cluster A round 2 CR-N5: `LoginUserUtil` fail-closed 化

## 6. 移行影響

### 既存の SpEL `principal.shopNo == 0` の問題
`LoginUser` クラスは `org.springframework.security.core.userdetails.User` を継承し、
`shopNo` プロパティを持たない。SpEL `principal.shopNo` は `LoginUser.getShopNo()` を期待するが、
実際には NoSuchPropertyError か silent null 評価される。
T4 で本 SpEL を撤廃し、`isAdmin()` 経由で `MLoginUser.getShopNo()` を参照することで意図通りに動作。

### 既存の `hasRole('ADMIN')` との等価性
ユーザー判断 (Q1=(a)) により shopNo=0 ⇔ ROLE_ADMIN が常に同期する前提下で、
`isAdmin()` (両方判定) は `hasRole('ADMIN')` (片方判定) と挙動等価。
不整合時は `isAdmin()` の方が厳格 (両方 true でないと拒否)。

## 7. テスト戦略

### 単体テスト
- `LoginUserAdminTest` (5 ケース): isAdmin() 境界条件
  - shopNo=0 + ROLE_ADMIN → true
  - shopNo=0 + ROLE_SHOP → false (fail-safe)
  - 不正 CompanyType (shopNo=-1) → false
  - shopNo null (companyType=null) → false
  - admin の isShopUser 判定補助
- `LoginUserSecurityBeanTest` (5 ケース): SpEL Bean 経由判定
  - admin principal → isAdmin=true / isShopUser=false
  - shop principal → isAdmin=false / isShopUser=true
  - 未認証 → 両方 false
  - anonymous authentication → 両方 false
  - principal が LoginUser 以外 → 両方 false

### Controller integration test
本プロジェクトでは `@WithMockUser` を使った Controller integration test は未実装
(2026-05-06 時点)。今後追加する場合は `@WithUserDetails` で `LoginUser` を直接 mock
するか、`SecurityContextHolder` 経由で `LoginUser` を組み立てる必要がある
(`@WithMockUser(roles="ADMIN")` は principal が `User` になるため `isAdmin()` が
fail-safe で false を返してしまう)。

## 8. 関連 DESIGN-DECISION
- T4 (本ドキュメント): 認可マトリクス統一
- T1: 数字の権威階層 (admin = 全画面 / 全 shop データ閲覧可能)
- P1-01: MF tenant binding (admin 限定)
- P1-05: OAuth 鍵分離 (admin 限定)
- C-N5 round 2: `LoginUserUtil` fail-closed (anonymous → AccessDeniedException)
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content frontend/lib/api-client.ts' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/exception/GlobalExceptionHandler.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/dto/finance/ErrorResponse.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/dto/audit/AuditLogResponse.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/repository/audit/FinanceAuditLogRepository.java' in C:\project\odamitsu-data-hub
 succeeded in 1871ms:
package jp.co.oda32.dto.finance;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

/**
 * Finance 系 API の標準エラーレスポンス。
 * <ul>
 *   <li>{@code message} : エンドユーザー向けメッセージ（既存応答と同じフィールド名で互換）</li>
 *   <li>{@code code}    : クライアントが分岐に使う安定識別子（例: MF_REAUTH_REQUIRED）</li>
 *   <li>{@code timestamp} : サーバー時刻（OffsetDateTime, ISO-8601）</li>
 *   <li>{@code requiredScope} : MF scope 不足時の必要 scope (旧 endpoint 互換、Optional/null 可)</li>
 * </ul>
 *
 * @since 2026-05-04 (SF-25)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String message, String code, OffsetDateTime timestamp, String requiredScope) {

    public static ErrorResponse of(String message, String code) {
        return new ErrorResponse(message, code, OffsetDateTime.now(), null);
    }

    public static ErrorResponse withScope(String message, String code, String requiredScope) {
        return new ErrorResponse(message, code, OffsetDateTime.now(), requiredScope);
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1975ms:
package jp.co.oda32.dto.audit;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.oda32.domain.model.audit.FinanceAuditLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * T2: 監査ログ閲覧 API のレスポンス DTO。
 *
 * @since 2026-05-04 (T2)
 */
@Data
@Builder
public class AuditLogResponse {
    private Long id;
    private LocalDateTime occurredAt;
    private Integer actorUserNo;
    /** m_login_user.user_name から JOIN 解決 (NULL = SYSTEM/BATCH もしくは退職等で削除済 user)。 */
    private String actorUserName;
    private String actorType;
    private String operation;
    private String targetTable;
    private JsonNode targetPk;
    private JsonNode beforeValues;
    private JsonNode afterValues;
    private String reason;
    private String sourceIp;
    private String userAgent;

    public static AuditLogResponse from(FinanceAuditLog entity, String actorUserName) {
        return AuditLogResponse.builder()
                .id(entity.getId())
                .occurredAt(entity.getOccurredAt())
                .actorUserNo(entity.getActorUserNo())
                .actorUserName(actorUserName)
                .actorType(entity.getActorType())
                .operation(entity.getOperation())
                .targetTable(entity.getTargetTable())
                .targetPk(entity.getTargetPk())
                .beforeValues(entity.getBeforeValues())
                .afterValues(entity.getAfterValues())
                .reason(entity.getReason())
                .sourceIp(entity.getSourceIp())
                .userAgent(entity.getUserAgent())
                .build();
    }

    /** 一覧表示用 (before/after を除外して payload を軽く)。 */
    public static AuditLogResponse summaryFrom(FinanceAuditLog entity, String actorUserName) {
        return AuditLogResponse.builder()
                .id(entity.getId())
                .occurredAt(entity.getOccurredAt())
                .actorUserNo(entity.getActorUserNo())
                .actorUserName(actorUserName)
                .actorType(entity.getActorType())
                .operation(entity.getOperation())
                .targetTable(entity.getTargetTable())
                .targetPk(entity.getTargetPk())
                .reason(entity.getReason())
                .sourceIp(entity.getSourceIp())
                .build();
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1977ms:
const API_BASE = '/api/v1'

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
}

class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

function getToken(): string | null {
  if (typeof window === 'undefined') return null
  return localStorage.getItem('token')
}

async function request<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
  const token = getToken()
  const { body, headers: customHeaders, ...rest } = options

  const headers: Record<string, string> = {}

  if (customHeaders) {
    if (customHeaders instanceof Headers) {
      customHeaders.forEach((value, key) => {
        headers[key] = value
      })
    } else if (typeof customHeaders === 'object') {
      Object.assign(headers, customHeaders)
    }
  }

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...rest,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (response.status === 401) {
    if (!endpoint.startsWith('/auth/login')) {
      if (typeof window !== 'undefined') {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        window.location.href = '/login'
      }
    }
    throw new ApiError(401, 'Unauthorized')
  }

  if (!response.ok) {
    const errorBody = await response.text()
    throw new ApiError(response.status, errorBody || response.statusText)
  }

  // 204 No Content、または Content-Length=0 / ボディ空 のときは undefined を返す。
  // Spring の ResponseEntity.ok().build() は HTTP 200 + 空ボディを返すため、
  // 200 かつ空を明示的にハンドルしないと response.json() が parse エラーを throw する。
  //
  // 注意: 非 void 型ジェネリクス (例: `api.get<User>('/foo')`) を呼ぶ側は、
  // 空ボディを受ける可能性がある API では `api.get<User | undefined>(...)` を明示するか、
  // DELETE 等の空応答を期待する API 用の `api.delete` を使うこと。
  // 型では undefined を保証できないため、ランタイムで undefined 返却される。
  if (response.status === 204) {
    return undefined as unknown as T
  }
  const contentLength = response.headers.get('Content-Length')
  if (contentLength === '0') {
    return undefined as unknown as T
  }
  const text = await response.text()
  if (text.length === 0) {
    return undefined as unknown as T
  }
  return JSON.parse(text) as T
}

async function uploadForm<T>(endpoint: string, formData: FormData): Promise<T> {
  const token = getToken()
  const headers: Record<string, string> = {}
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  // Content-Type は fetch が multipart boundary を自動設定するため指定しない
  const response = await fetch(`${API_BASE}${endpoint}`, {
    method: 'POST',
    headers,
    body: formData,
  })

  if (response.status === 401) {
    if (typeof window !== 'undefined') {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
    }
    throw new ApiError(401, 'Unauthorized')
  }

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({ message: response.statusText }))
    throw new ApiError(response.status, errorBody.message || response.statusText)
  }

  return response.json()
}

/**
 * バイナリレスポンスを取得する（PDF, Excel 等）。
 * Content-Disposition からファイル名を抽出して { blob, filename } を返す。
 */
async function downloadBlob(endpoint: string, method: 'GET' | 'POST' = 'GET'): Promise<{ blob: Blob; filename: string | null; headers: Headers }> {
  const token = getToken()
  const headers: Record<string, string> = {}
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch(`${API_BASE}${endpoint}`, { method, headers })

  if (response.status === 401) {
    if (typeof window !== 'undefined') {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
    }
    throw new ApiError(401, 'Unauthorized')
  }

  if (!response.ok) {
    // JSON エラー本文があれば message を取り出す
    let message = response.statusText
    try {
      const body = await response.clone().json()
      if (body && typeof body.message === 'string') message = body.message
    } catch {
      // ignore
    }
    throw new ApiError(response.status, message)
  }

  const blob = await response.blob()

  // Content-Disposition から filename を抽出（filename*= 優先, fallback で filename=）
  const disposition = response.headers.get('Content-Disposition')
  let filename: string | null = null
  if (disposition) {
    const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/)
    if (utf8Match) {
      filename = decodeURIComponent(utf8Match[1])
    } else {
      const plainMatch = disposition.match(/filename="?([^";]+)"?/)
      if (plainMatch) {
        filename = plainMatch[1]
      }
    }
  }

  return { blob, filename, headers: response.headers }
}

export const api = {
  get: <T>(endpoint: string) => request<T>(endpoint),
  post: <T>(endpoint: string, body?: unknown) => request<T>(endpoint, { method: 'POST', body }),
  put: <T>(endpoint: string, body?: unknown) => request<T>(endpoint, { method: 'PUT', body }),
  patch: <T>(endpoint: string, body?: unknown) => request<T>(endpoint, { method: 'PATCH', body }),
  delete: (endpoint: string) => request<void>(endpoint, { method: 'DELETE' }),
  deleteWithResponse: <T>(endpoint: string) => request<T>(endpoint, { method: 'DELETE' }),
  postForm: <T>(endpoint: string, formData: FormData) => uploadForm<T>(endpoint, formData),
  download: (endpoint: string) => downloadBlob(endpoint),
  downloadPost: (endpoint: string) => downloadBlob(endpoint, 'POST'),
}

export { ApiError }
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1987ms:
package jp.co.oda32.domain.repository.audit;

import jp.co.oda32.domain.model.audit.FinanceAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * T2: 監査証跡 Repository。
 * <p>
 * 検索系は {@link JpaSpecificationExecutor} で動的フィルタ (actor / operation / target_table / 日付範囲) を組む。
 *
 * @since 2026-05-04 (T2)
 */
@Repository
public interface FinanceAuditLogRepository extends JpaRepository<FinanceAuditLog, Long>,
        JpaSpecificationExecutor<FinanceAuditLog> {
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2060ms:
package jp.co.oda32.exception;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * true の場合、500 レスポンスに例外クラス名/メッセージを含める。
     * 開発・検証環境のみ true にすること。本番は false（または未設定）。
     * dev プロファイル以外で true が設定されていた場合は起動時に強制 false 化する。
     */
    @Value("${app.expose-exception-detail:false}")
    private boolean exposeExceptionDetail;

    private final Environment environment;

    public GlobalExceptionHandler(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void guardExposeExceptionDetail() {
        if (!exposeExceptionDetail) return;
        boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        if (!isDev) {
            log.warn("app.expose-exception-detail=true が dev 以外で設定されています。安全のため無効化します (activeProfiles={})",
                    Arrays.toString(environment.getActiveProfiles()));
            exposeExceptionDetail = false;
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("message", "バリデーションエラー");
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "ログインIDまたはパスワードが正しくありません"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", "ファイルサイズが上限（10MB）を超えています"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : "アクセス権限がありません"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (status.is5xxServerError()) {
            log.error("ResponseStatusException: {}", ex.getMessage(), ex);
        } else {
            log.warn("ResponseStatusException: {} - {}", status, ex.getReason());
        }
        String reason = ex.getReason();
        return ResponseEntity.status(status)
                .body(Map.of("message", reason != null ? reason : status.getReasonPhrase()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        Map<String, String> body = new HashMap<>();
        body.put("message", "システムエラーが発生しました");
        if (exposeExceptionDetail) {
            body.put("detail", ex.getClass().getSimpleName() + ": " + (ex.getMessage() != null ? ex.getMessage() : ""));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /S /N /C:\"AmountSourceTooltip\" frontend\\components\\pages\\finance\\*.tsx frontend\\components\\common\\*.tsx" in C:\project\odamitsu-data-hub
 succeeded in 1256ms:
frontend\components\pages\finance\accounts-payable-ledger.tsx:33:import { AmountSourceTooltip } from '@/components/common/AmountSourceTooltip'
frontend\components\pages\finance\accounts-payable-ledger.tsx:349:                    <th className="py-2 text-right">前月繰越<AmountSourceTooltip source="OPENING_BALANCE" /></th>
frontend\components\pages\finance\accounts-payable-ledger.tsx:350:                    <th className="py-2 text-right">仕入<AmountSourceTooltip source="PAYABLE_SUMMARY" /></th>
frontend\components\pages\finance\accounts-payable-ledger.tsx:351:                    <th className="py-2 text-right">検証額<AmountSourceTooltip source="VERIFIED_AMOUNT" /></th>
frontend\components\pages\finance\accounts-payable-ledger.tsx:352:                    <th className="py-2 text-right">支払反映<AmountSourceTooltip source="VERIFIED_AMOUNT" /></th>
frontend\components\pages\finance\accounts-payable-ledger.tsx:353:                    <th className="py-2 text-right">当月残<AmountSourceTooltip source="CLOSING_CALC" /></th>
frontend\components\pages\finance\accounts-payable-ledger.tsx:356:                        <th className="py-2 text-right">MF delta<AmountSourceTooltip source="MF_JOURNAL" /></th>
frontend\components\pages\finance\accounts-payable-ledger.tsx:358:                        <th className="py-2 text-right">MF 累積残<AmountSourceTooltip source="MF_JOURNAL" /></th>
frontend\components\pages\finance\accounts-payable.tsx:31:import { AmountSourceTooltip } from '@/components/common/AmountSourceTooltip'
frontend\components\pages\finance\accounts-payable.tsx:373:      header: <>前月繰越<AmountSourceTooltip source="OPENING_BALANCE" /></>,
frontend\components\pages\finance\accounts-payable.tsx:383:      header: <>当月支払<AmountSourceTooltip source="VERIFIED_AMOUNT" /></>,
frontend\components\pages\finance\accounts-payable.tsx:400:      header: <>累積残<AmountSourceTooltip source="CLOSING_CALC" /></>,
frontend\components\pages\finance\accounts-payable.tsx:486:      header: <>買掛金額(税込)<AmountSourceTooltip source="PAYABLE_SUMMARY" /></>,
frontend\components\pages\finance\accounts-payable.tsx:491:      header: <>振込明細額<AmountSourceTooltip source="VERIFIED_AMOUNT" /></>,
frontend\components\pages\finance\accounts-receivable.tsx:47:import { AmountSourceTooltip } from '@/components/common/AmountSourceTooltip'
frontend\components\pages\finance\accounts-receivable.tsx:299:      header: <>税込金額<AmountSourceTooltip source="PAYABLE_SUMMARY" /></>,
frontend\components\pages\finance\accounts-receivable.tsx:304:      header: <>税抜金額<AmountSourceTooltip source="PAYABLE_SUMMARY" /></>,
frontend\components\pages\finance\accounts-receivable.tsx:309:      header: <>請求書金額<AmountSourceTooltip source="AR_INVOICE" /></>,
frontend\components\pages\finance\integrity-report.tsx:27:import { AmountSourceTooltip } from '@/components/common/AmountSourceTooltip'
frontend\components\pages\finance\integrity-report.tsx:337:                      <th className="py-2 text-right">貸方<AmountSourceTooltip source="MF_JOURNAL" /></th>
frontend\components\pages\finance\integrity-report.tsx:338:                      <th className="py-2 text-right">借方<AmountSourceTooltip source="MF_JOURNAL" /></th>
frontend\components\pages\finance\integrity-report.tsx:339:                      <th className="py-2 text-right">差額<AmountSourceTooltip source="MF_JOURNAL" /></th>
frontend\components\pages\finance\integrity-report.tsx:389:                      <th className="py-2 text-right">自社差額<AmountSourceTooltip source="CLOSING_CALC" /></th>
frontend\components\pages\finance\integrity-report.tsx:390:                      <th className="py-2 text-right">仕入<AmountSourceTooltip source="PAYABLE_SUMMARY" /></th>
frontend\components\pages\finance\integrity-report.tsx:391:                      <th className="py-2 text-right">支払反映<AmountSourceTooltip source="VERIFIED_AMOUNT" /></th>
frontend\components\pages\finance\integrity-report.tsx:432:                      <th className="py-2 text-right">自社差額<AmountSourceTooltip source="CLOSING_CALC" /></th>
frontend\components\pages\finance\integrity-report.tsx:433:                      <th className="py-2 text-right">MF 差額<AmountSourceTooltip source="MF_JOURNAL" /></th>
frontend\components\pages\finance\invoices.tsx:25:import { AmountSourceTooltip } from '@/components/common/AmountSourceTooltip'
frontend\components\pages\finance\invoices.tsx:293:      header: <>今回請求額<AmountSourceTooltip source="AR_INVOICE" /></>,
frontend\components\pages\finance\payment-mf-import.tsx:27:import { AmountSourceTooltip } from '@/components/common/AmountSourceTooltip'
frontend\components\pages\finance\payment-mf-import.tsx:345:                  <th className="p-1 text-right">請求額<AmountSourceTooltip source="INVOICE" /></th>
frontend\components\pages\finance\payment-mf-import.tsx:346:                  <th className="p-1 text-right">買掛金<AmountSourceTooltip source="PAYABLE_SUMMARY" /></th>
frontend\components\pages\finance\supplier-balances.tsx:26:import { AmountSourceTooltip } from '@/components/common/AmountSourceTooltip'
frontend\components\pages\finance\supplier-balances.tsx:193:                  <th className="py-2 text-right">self 残<AmountSourceTooltip source="CLOSING_CALC" /></th>
frontend\components\pages\finance\supplier-balances.tsx:194:                  <th className="py-2 text-right">MF 残<AmountSourceTooltip source="MF_JOURNAL" /></th>
frontend\components\pages\finance\supplier-balances.tsx:197:                  <th className="py-2 text-right">opening<AmountSourceTooltip source="OPENING_BALANCE" /></th>
frontend\components\pages\finance\supplier-balances.tsx:198:                  <th className="py-2 text-right">Σchange<AmountSourceTooltip source="PAYABLE_SUMMARY" /></th>
frontend\components\pages\finance\supplier-balances.tsx:199:                  <th className="py-2 text-right">Σpayment<AmountSourceTooltip source="VERIFIED_AMOUNT" /></th>
frontend\components\pages\finance\supplier-opening-balance.tsx:31:import { AmountSourceTooltip } from '@/components/common/AmountSourceTooltip'
frontend\components\pages\finance\supplier-opening-balance.tsx:239:                    <th className="py-2 text-right">MF 取得値<AmountSourceTooltip source="MF_JOURNAL" /></th>
frontend\components\pages\finance\supplier-opening-balance.tsx:241:                    <th className="py-2 text-right">実効値<AmountSourceTooltip source="OPENING_BALANCE" /></th>
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content frontend/components/features/common/DataTable.tsx' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content frontend/components/ui/tooltip.tsx' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content frontend/components/pages/finance/accounts-payable.tsx -TotalCount 540' in C:\project\odamitsu-data-hub
 succeeded in 1692ms:
'use client'

import { useState, useMemo, useEffect } from 'react'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight, ArrowUpDown, Search } from 'lucide-react'
import { normalizeForSearch } from '@/lib/utils'

export interface Column<T> {
  key: string
  /**
   * 列ヘッダー。文字列または ReactNode を受け取る。
   * AmountSourceTooltip 等のアイコン併記時は ReactNode 形式 (<>...</>) で渡す。
   */
  header: React.ReactNode
  render?: (item: T) => React.ReactNode
  sortable?: boolean
}

export interface ServerPagination {
  page: number          // 0-based
  pageSize: number
  totalElements: number
  totalPages: number
  onPageChange: (page: number) => void
}

interface DataTableProps<T> {
  data: T[]
  columns: Column<T>[]
  pageSize?: number
  searchPlaceholder?: string
  onRowClick?: (item: T) => void
  rowKey?: (item: T, index: number) => string | number
  defaultSortKey?: string
  defaultSortDir?: 'asc' | 'desc'
  /**
   * 指定時はサーバーサイドページング。data はすでに現ページ分に絞られた内容。
   * クライアント側のフィルタ/ソート/ページングは無効になる。
   */
  serverPagination?: ServerPagination
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function DataTable<T extends Record<string, any>>({
  data,
  columns,
  pageSize = 20,
  searchPlaceholder = 'テーブル内を検索...',
  onRowClick,
  rowKey,
  defaultSortKey,
  defaultSortDir = 'asc',
  serverPagination,
}: DataTableProps<T>) {
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const isServerMode = serverPagination != null

  // データが変わったらページを先頭に戻す（クライアントモードのみ）
  useEffect(() => { if (!isServerMode) setPage(0) }, [data, isServerMode])
  const [sortKey, setSortKey] = useState<string | null>(defaultSortKey ?? null)
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>(defaultSortDir)

  const filtered = useMemo(() => {
    if (isServerMode) return data
    if (search === '') return data
    const normalizedSearch = normalizeForSearch(search.toLowerCase())
    return data.filter((item) =>
      Object.values(item).some((v) =>
        normalizeForSearch(String(v ?? '').toLowerCase()).includes(normalizedSearch)
      )
    )
  }, [data, search, isServerMode])

  const sorted = useMemo(() => {
    if (isServerMode || !sortKey) return filtered
    return [...filtered].sort((a, b) => {
      const aRaw = a[sortKey]
      const bRaw = b[sortKey]
      if (typeof aRaw === 'number' || typeof bRaw === 'number') {
        const aNum = Number(aRaw ?? 0)
        const bNum = Number(bRaw ?? 0)
        return sortDir === 'asc' ? aNum - bNum : bNum - aNum
      }
      const aVal = String(aRaw ?? '')
      const bVal = String(bRaw ?? '')
      return sortDir === 'asc' ? aVal.localeCompare(bVal) : bVal.localeCompare(aVal)
    })
  }, [filtered, sortKey, sortDir, isServerMode])

  const effectivePageSize = serverPagination ? serverPagination.pageSize : pageSize
  const effectiveTotal = serverPagination ? serverPagination.totalElements : sorted.length
  const totalPages = serverPagination
    ? Math.max(1, serverPagination.totalPages)
    : Math.max(1, Math.ceil(sorted.length / pageSize))
  const currentPage = serverPagination ? serverPagination.page : page
  const paged = serverPagination ? sorted : sorted.slice(page * pageSize, (page + 1) * pageSize)
  const goToPage = (n: number) => {
    if (serverPagination) serverPagination.onPageChange(n)
    else setPage(n)
  }

  const handleSort = (key: string) => {
    if (sortKey === key) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc')
    } else {
      setSortKey(key)
      setSortDir('asc')
    }
  }

  return (
    <div className="space-y-3">
      {/* Search bar (クライアントモードのみ) */}
      {!isServerMode && (
        <div className="relative max-w-sm">
          <Search className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder={searchPlaceholder}
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0) }}
            className="h-9 pl-8 text-sm"
          />
        </div>
      )}

      {/* Table */}
      <div className="rounded-lg border shadow-sm">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/40 hover:bg-muted/40">
              {columns.map((col) => (
                <TableHead
                  key={col.key}
                  className={`text-xs font-semibold uppercase tracking-wider text-muted-foreground ${col.sortable ? 'cursor-pointer select-none hover:text-foreground transition-colors' : ''}`}
                  onClick={() => col.sortable && handleSort(col.key)}
                >
                  <div className="flex items-center gap-1">
                    {col.header}
                    {col.sortable && (
                      <ArrowUpDown className={`h-3 w-3 ${sortKey === col.key ? 'text-foreground' : 'opacity-40'}`} />
                    )}
                  </div>
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {paged.length === 0 ? (
              <TableRow>
                <TableCell colSpan={columns.length} className="text-center text-muted-foreground py-12">
                  データがありません
                </TableCell>
              </TableRow>
            ) : (
              paged.map((item, i) => (
                <TableRow
                  key={rowKey ? rowKey(item, i) : i}
                  className={`text-sm ${onRowClick ? 'cursor-pointer' : ''} ${i % 2 === 1 ? 'bg-muted/20' : ''}`}
                  onClick={() => onRowClick?.(item)}
                >
                  {columns.map((col) => (
                    <TableCell key={col.key} className="py-2.5">
                      {col.render ? col.render(item) : String(item[col.key] ?? '')}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      <div className="flex items-center justify-between text-sm">
        <span className="text-muted-foreground">
          全 {effectiveTotal} 件中 {effectiveTotal > 0 ? currentPage * effectivePageSize + 1 : 0}?{Math.min((currentPage + 1) * effectivePageSize, effectiveTotal)} 件
        </span>
        <div className="flex items-center gap-1">
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => goToPage(0)} disabled={currentPage === 0} aria-label="最初のページ">
            <ChevronsLeft className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => goToPage(currentPage - 1)} disabled={currentPage === 0} aria-label="前のページ">
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="min-w-[4rem] text-center text-muted-foreground">
            {currentPage + 1} / {totalPages}
          </span>
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => goToPage(currentPage + 1)} disabled={currentPage >= totalPages - 1} aria-label="次のページ">
            <ChevronRight className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => goToPage(totalPages - 1)} disabled={currentPage >= totalPages - 1} aria-label="最後のページ">
            <ChevronsRight className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>
  )
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1761ms:
'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops, usePaymentSuppliers } from '@/hooks/use-master-data'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { ConfirmDialog } from '@/components/features/common/ConfirmDialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatCurrency } from '@/lib/utils'
import { emptyPage, type Paginated } from '@/types/paginated'
import { AlertCircle, CheckCircle2, Download, FileDown, Loader2, RefreshCw, ShieldCheck, Upload, XCircle } from 'lucide-react'
import { toast } from 'sonner'
import { useRouter, useSearchParams } from 'next/navigation'
import { BulkVerifyDialog } from './BulkVerifyDialog'
import { PaymentMfAuxRowsTable } from './PaymentMfAuxRowsTable'
import { VerifyDialog } from './VerifyDialog'
import { VerifiedCsvExportDialog } from './VerifiedCsvExportDialog'
import { PurchaseJournalCsvExportDialog } from './PurchaseJournalCsvExportDialog'
import { AmountSourceTooltip } from '@/components/common/AmountSourceTooltip'
import {
  type AccountsPayable,
  type AccountsPayableSummary,
  type AccountsPayableWithBalance,
  type BalanceFilter,
  type VerificationFilter,
  BALANCE_FILTER_LABELS,
  VERIFICATION_FILTER_LABELS,
  defaultTransactionMonth,
  fromMonthInput,
  hasBalance,
  toMonthInput,
} from '@/types/accounts-payable'

const PAGE_SIZE = 50

// モジュールスコープ（毎レンダー再生成を防ぎ、useEffect 依存配列から除外可能にする）
const BATCH_JOBS = {
  AGGREGATION: 'accountsPayableAggregation',
  VERIFICATION: 'accountsPayableVerification',
  PURCHASE_IMPORT: 'purchaseFileImport',
} as const
type BatchJobName = (typeof BATCH_JOBS)[keyof typeof BATCH_JOBS]
const BATCH_JOB_LABELS: Record<BatchJobName, string> = {
  [BATCH_JOBS.AGGREGATION]: '再集計',
  [BATCH_JOBS.VERIFICATION]: '再検証(SMILE)',
  [BATCH_JOBS.PURCHASE_IMPORT]: '仕入明細取込(SMILE)',
}

type JobStatus = 'COMPLETED' | 'FAILED' | 'STARTED' | 'STARTING' | 'STOPPED' | 'ABANDONED' | 'UNKNOWN'
interface BatchStatusPayload {
  status: JobStatus | string
  startTime?: string
  exitMessage?: string
}

const toYyyyMmDd = (isoDate: string) => isoDate.replaceAll('-', '')

/**
 * 20日締め取引月から対応する仕入期間を算出する。
 * transactionMonth=2026-02-20 → fromDate=2026-01-21, toDate=2026-02-20
 * Date は local TZ で生成し YYYY-MM-DD にフォーマット（UTC 変換は挟まないので TZ 非依存）。
 */
function purchaseDateRange(transactionMonth: string): { fromDate: string; toDate: string } {
  const [y, m, d] = transactionMonth.split('-').map(Number)
  const to = new Date(y, m - 1, d)       // 当月20日（20日締め）
  const from = new Date(y, m - 2, d + 1) // 前月21日: m-2 = 1月なら前年12月へ自動繰り下がる
  const fmt = (x: Date) =>
    `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`
  return { fromDate: fmt(from), toDate: fmt(to) }
}

interface SearchParams {
  shopNo?: number
  supplierNo?: number
  transactionMonth: string // yyyy-MM-dd
  verificationFilter: VerificationFilter
  /** 累積残の符号フィルタ (showBalance=true の時のみ反映)。クライアント側フィルタ。 */
  balanceFilter: BalanceFilter
}

/**
 * バッチ実行ボタン（実行中ローダー＋完了/失敗バッジ付き）。
 * 親コンポーネントが pollingJobs 配列で進行状態を持つ。
 */
function BatchButton({
  job, label, icon, running, status, onClick,
}: {
  job: BatchJobName
  label: string
  icon: React.ReactNode
  running: boolean
  status: JobStatus | string | undefined
  onClick: () => void
}) {
  return (
    <div className="flex items-center gap-1">
      <Button
        variant="outline"
        size="sm"
        onClick={onClick}
        disabled={running}
        data-testid={`batch-button-${job}`}
      >
        {running ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : icon}
        {running ? '実行中...' : label}
      </Button>
      {!running && status === 'COMPLETED' && (
        <span className="flex items-center gap-0.5 text-xs text-emerald-600" title={`${job} 完了`}>
          <CheckCircle2 className="h-3.5 w-3.5" />
        </span>
      )}
      {!running && status === 'FAILED' && (
        <span className="flex items-center gap-0.5 text-xs text-destructive" title={`${job} 失敗`}>
          <XCircle className="h-3.5 w-3.5" />
        </span>
      )}
    </div>
  )
}

function VerificationBadge({ row }: { row: AccountsPayable }) {
  // 「手動」バッジは手入力検証 (verificationSource=MANUAL) の時だけ表示。
  // 振込明細一括検証 (BULK) は verifiedManually=true だが手動操作ではないため表示しない。
  // verificationSource=null (移行期旧データ等) で verifiedManually=true の場合は、
  // 安全側に倒して手動扱い（BULK 以外は手動）とする。
  const showManualBadge =
    row.verifiedManually === true && row.verificationSource !== 'BULK'
  // V026: 自動調整 (振込明細金額と自社計算の差を合わせた痕跡) を ±\N で表示
  const adj = row.autoAdjustedAmount ?? 0
  const showAdjBadge = adj !== 0
  const adjClass = Math.abs(adj) > 100
    ? 'border-red-500 text-red-700'
    : 'border-amber-500 text-amber-700'
  const AdjBadge = showAdjBadge ? (
    <Badge variant="outline" className={`text-xs ${adjClass}`} title={`振込明細取込で ${adj > 0 ? '+' : ''}\${adj.toLocaleString('ja-JP')} 自動調整`}>
      調整 {adj > 0 ? '+' : ''}\{Math.abs(adj).toLocaleString('ja-JP')}
    </Badge>
  ) : null
  if (row.verificationResult === 1) {
    return (
      <div className="flex flex-wrap items-center gap-1">
        <Badge className="bg-green-600 hover:bg-green-700">一致</Badge>
        {showManualBadge && <Badge variant="outline" className="text-xs">手動</Badge>}
        {AdjBadge}
      </div>
    )
  }
  if (row.verificationResult === 0) {
    return (
      <div className="flex flex-wrap items-center gap-1">
        <Badge variant="destructive">不一致</Badge>
        {showManualBadge && <Badge variant="outline" className="text-xs">手動</Badge>}
        {AdjBadge}
      </div>
    )
  }
  return <Badge variant="secondary">未検証</Badge>
}

export function AccountsPayablePage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const queryClient = useQueryClient()

  const [params, setParams] = useState<SearchParams>(() => ({
    shopNo: isAdmin ? undefined : user?.shopNo,
    transactionMonth: defaultTransactionMonth(),
    verificationFilter: 'all',
    balanceFilter: 'all',
  }))
  /**
   * 累積残 (opening/closing) を表示するかどうか。OFF 時は API に include=balance を付けず
   * ペイロードを抑える。日常運用は OFF、月次突合時のみ ON にする想定。
   * 設計書: design-supplier-partner-ledger-balance.md §4.5
   */
  const [showBalance, setShowBalance] = useState(false)
  const [page, setPage] = useState(0)
  const [dialogRow, setDialogRow] = useState<AccountsPayable | null>(null)
  const [bulkDialog, setBulkDialog] = useState(false)
  const [verifiedExportDialog, setVerifiedExportDialog] = useState(false)
  const [purchaseJournalExportDialog, setPurchaseJournalExportDialog] = useState(false)

  // タブ状態は URL ?tab=payable|aux で永続化
  const router = useRouter()
  const urlSearchParams = useSearchParams()
  const tabParam = urlSearchParams.get('tab')
  const currentTab: 'payable' | 'aux' = tabParam === 'aux' ? 'aux' : 'payable'
  const setCurrentTab = (tab: 'payable' | 'aux') => {
    const sp = new URLSearchParams(urlSearchParams.toString())
    if (tab === 'payable') sp.delete('tab')
    else sp.set('tab', tab)
    const qs = sp.toString()
    const nextUrl = qs ? `?${qs}` : window.location.pathname
    router.replace(nextUrl, { scroll: false })
  }
  const [auxCount, setAuxCount] = useState<number | null>(null)

  const shopsQuery = useShops(isAdmin)
  const paymentSuppliersQuery = usePaymentSuppliers(params.shopNo ?? (isAdmin ? undefined : user?.shopNo))

  const queryString = useMemo(() => {
    const sp = new URLSearchParams()
    sp.set('page', String(page))
    sp.set('size', String(PAGE_SIZE))
    sp.set('transactionMonth', params.transactionMonth)
    if (params.shopNo !== undefined) sp.set('shopNo', String(params.shopNo))
    if (params.supplierNo !== undefined) sp.set('supplierNo', String(params.supplierNo))
    if (params.verificationFilter !== 'all') sp.set('verificationFilter', params.verificationFilter)
    if (showBalance) sp.set('include', 'balance')
    return sp.toString()
  }, [page, params.transactionMonth, params.shopNo, params.supplierNo, params.verificationFilter, showBalance])

  const apQuery = useQuery({
    queryKey: ['accounts-payable', queryString],
    queryFn: () => api.get<Paginated<AccountsPayable>>(`/finance/accounts-payable?${queryString}`),
  })

  const summaryQuery = useQuery({
    queryKey: ['accounts-payable-summary', params.transactionMonth, params.shopNo ?? null],
    queryFn: () => {
      const sp = new URLSearchParams()
      sp.set('transactionMonth', params.transactionMonth)
      if (params.shopNo !== undefined) sp.set('shopNo', String(params.shopNo))
      return api.get<AccountsPayableSummary>(`/finance/accounts-payable/summary?${sp.toString()}`)
    },
  })

  const invalidate = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['accounts-payable'] })
    queryClient.invalidateQueries({ queryKey: ['accounts-payable-summary'] })
    queryClient.invalidateQueries({ queryKey: ['payment-mf-aux-rows'] })
    queryClient.invalidateQueries({ queryKey: ['verified-export-preview'] })
  }, [queryClient])

  const verifyMutation = useMutation({
    mutationFn: async ({ row, verifiedAmount, note }: { row: AccountsPayable; verifiedAmount: number; note: string }) => {
      const path = `/finance/accounts-payable/${row.shopNo}/${row.supplierNo}/${row.transactionMonth}/${row.taxRate}/verify`
      return api.put<AccountsPayable>(path, { verifiedAmount, note: note || null })
    },
    onSuccess: () => {
      toast.success('検証結果を更新しました')
      setDialogRow(null)
      invalidate()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const releaseManualMutation = useMutation({
    mutationFn: async (row: AccountsPayable) => {
      const path = `/finance/accounts-payable/${row.shopNo}/${row.supplierNo}/${row.transactionMonth}/${row.taxRate}/manual-lock`
      return api.deleteWithResponse<AccountsPayable>(path)
    },
    onSuccess: () => {
      toast.success('手動確定を解除しました')
      setDialogRow(null)
      invalidate()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const mfExportMutation = useMutation({
    mutationFn: async ({ row, enabled }: { row: AccountsPayable; enabled: boolean }) => {
      const path = `/finance/accounts-payable/${row.shopNo}/${row.supplierNo}/${row.transactionMonth}/${row.taxRate}/mf-export`
      return api.patch<AccountsPayable>(path, { enabled })
    },
    onSuccess: () => {
      toast.success('MF出力可否を更新しました')
      invalidate()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const [pollingJobs, setPollingJobs] = useState<Set<BatchJobName>>(new Set())
  // 起動時刻は「古い COMPLETED 誤検知」防止のガードにのみ使うので Ref で十分（再レンダー不要）
  const launchedAtRef = useRef<Record<string, string>>({})
  // 購入明細取込のConfirmDialog制御
  const [confirmPurchaseImport, setConfirmPurchaseImport] = useState(false)

  // 実行中ジョブのステータスを5秒間隔でポーリング
  // queryKey は Set の挿入順を排除するため sort して安定化
  const sortedPolling = useMemo(() => Array.from(pollingJobs).sort(), [pollingJobs])
  const batchStatusQuery = useQuery({
    queryKey: ['ap-batch-status', sortedPolling.join(',')],
    queryFn: async () => {
      const entries = await Promise.all(
        sortedPolling.map(async (jobName) => {
          const data = await api.get<BatchStatusPayload>(`/batch/status/${jobName}`)
          return [jobName, data] as const
        }),
      )
      return Object.fromEntries(entries) as Record<string, BatchStatusPayload>
    },
    enabled: sortedPolling.length > 0,
    refetchInterval: sortedPolling.length > 0 ? 5000 : false,
    staleTime: 0,
  })

  useEffect(() => {
    if (!batchStatusQuery.data) return
    const finished: BatchJobName[] = []
    for (const [jobName, status] of Object.entries(batchStatusQuery.data)) {
      const launched = launchedAtRef.current[jobName]
      if (launched && status.startTime && new Date(status.startTime).getTime() < new Date(launched).getTime()) continue
      const label = BATCH_JOB_LABELS[jobName as BatchJobName] ?? jobName
      if (status.status === 'COMPLETED') {
        toast.success(`${label} が完了しました`)
        finished.push(jobName as BatchJobName)
      } else if (status.status === 'FAILED') {
        toast.error(`${label} が失敗しました${status.exitMessage ? ': ' + status.exitMessage : ''}`)
        finished.push(jobName as BatchJobName)
      }
    }
    if (finished.length === 0) return
    setPollingJobs((prev) => {
      const next = new Set(prev)
      finished.forEach((j) => next.delete(j))
      return next
    })
    // 集計/検証ジョブ完了時は一覧を再取得
    if (finished.some((j) => j !== BATCH_JOBS.PURCHASE_IMPORT)) invalidate()
  }, [batchStatusQuery.data, invalidate])

  const runBatchMutation = useMutation({
    mutationFn: async (jobName: BatchJobName) => {
      const sp = new URLSearchParams()
      if (jobName !== BATCH_JOBS.PURCHASE_IMPORT) {
        sp.set('targetDate', toYyyyMmDd(params.transactionMonth))
      }
      const qs = sp.toString()
      return api.post<{ message: string }>(`/batch/execute/${jobName}${qs ? `?${qs}` : ''}`)
    },
    onMutate: (jobName) => {
      launchedAtRef.current[jobName] = new Date().toISOString()
      // ポーリング対象に即時追加（まだジョブレコード未生成でも status 応答は UNKNOWN として扱われ、launched 時刻以前の古い COMPLETED は ref ガードで無視される）
      setPollingJobs((prev) => new Set(prev).add(jobName))
    },
    onSuccess: (res, jobName) => {
      toast.info(res?.message ?? `${BATCH_JOB_LABELS[jobName]} を起動しました`)
    },
    onError: (e: Error, jobName) => {
      setPollingJobs((prev) => {
        const next = new Set(prev)
        next.delete(jobName)
        return next
      })
      if (e instanceof ApiError && e.status === 429) {
        toast.error('他のバッチが実行中です。しばらく待ってから再実行してください')
      } else {
        toast.error(e.message)
      }
    },
  })

  const isRunning = (job: BatchJobName) => pollingJobs.has(job) || runBatchMutation.isPending
  const lastStatus = (job: BatchJobName): JobStatus | string | undefined =>
    batchStatusQuery.data?.[job]?.status

  const balanceColumns: Column<AccountsPayable>[] = [
    {
      key: 'openingBalanceTaxIncluded',
      header: <>前月繰越<AmountSourceTooltip source="OPENING_BALANCE" /></>,
      render: (r) => {
        if (!hasBalance(r)) return <span className="text-muted-foreground">-</span>
        const v = r.openingBalanceTaxIncluded
        const cls = v < 0 ? 'text-amber-700 tabular-nums' : 'tabular-nums'
        return <span className={cls}>{formatCurrency(v)}</span>
      },
    },
    {
      key: 'paymentSettledTaxIncluded',
      header: <>当月支払<AmountSourceTooltip source="VERIFIED_AMOUNT" /></>,
      render: (r) => {
        if (!hasBalance(r)) return <span className="text-muted-foreground">-</span>
        const v = r.paymentSettledTaxIncluded
        if (v === 0) return <span className="text-muted-foreground">?</span>
        return (
          <div className="flex items-center gap-1">
            <span className="tabular-nums">{formatCurrency(v)}</span>
            {r.isPaymentOnly && (
              <Badge variant="outline" className="text-slate-600 text-xs">支払のみ</Badge>
            )}
          </div>
        )
      },
    },
    {
      key: 'closingBalanceTaxIncluded',
      header: <>累積残<AmountSourceTooltip source="CLOSING_CALC" /></>,
      render: (r) => {
        if (!hasBalance(r)) return <span className="text-muted-foreground">-</span>
        const v = r.closingBalanceTaxIncluded
        if (v < 0) {
          // バッジ分岐 (設計書 §6.2): payment-only なら「支払超過」、それ以外は「値引繰越」
          const label = r.isPaymentOnly ? '支払超過' : '値引繰越'
          return (
            <div className="flex items-center gap-1">
              <span className="text-amber-700 font-medium tabular-nums">{formatCurrency(v)}</span>
              <Badge variant="outline" className="border-amber-500 text-amber-700 text-xs">{label}</Badge>
            </div>
          )
        }
        return <span className="tabular-nums">{formatCurrency(v)}</span>
      },
    },
  ]

  const baseColumns: Column<AccountsPayable>[] = [
    {
      key: 'supplierCode',
      header: '仕入先コード',
      sortable: true,
      render: (r) => {
        // supplierCode 列: 仕入一覧への遷移 (既存導線を保持)
        const { fromDate, toDate } = purchaseDateRange(r.transactionMonth)
        const sp = new URLSearchParams()
        sp.set('shopNo', '1')
        sp.set('paymentSupplierNo', String(r.supplierNo))
        sp.set('fromDate', fromDate)
        sp.set('toDate', toDate)
        sp.set('transactionMonth', r.transactionMonth)
        if (r.supplierName) sp.set('supplierName', r.supplierName)
        return (
          <a
            href={`/purchases?${sp.toString()}`}
            target="_blank"
            rel="noreferrer"
            className="text-blue-600 hover:underline"
            title="仕入一覧を新しいタブで開く"
          >
            {r.supplierCode ?? '-'}
          </a>
        )
      },
    },
    {
      key: 'supplierName',
      header: '仕入先名',
      sortable: true,
      render: (r) => {
        // supplierName 列: 買掛帳 (月次推移) への遷移
        const sp = new URLSearchParams()
        sp.set('shopNo', String(r.shopNo))
        sp.set('supplierNo', String(r.supplierNo))
        return (
          <a
            href={`/finance/accounts-payable-ledger?${sp.toString()}`}
            target="_blank"
            rel="noreferrer"
            className="text-blue-600 hover:underline"
            title="買掛帳 (月次推移) を新しいタブで開く"
          >
            {r.supplierName ?? '不明'}
          </a>
        )
      },
    },
    { key: 'taxRate', header: '税率', render: (r) => `${r.taxRate}%` },
    {
      key: 'taxExcludedAndTaxAmount',
      header: '税抜 / 消費税',
      render: (r) => {
        const inc = r.taxIncludedAmountChange ?? 0
        const exc = r.taxExcludedAmountChange ?? 0
        return (
          <div className="flex flex-col tabular-nums leading-tight">
            <span>{formatCurrency(exc)}</span>
            <span className="text-xs text-muted-foreground">{formatCurrency(inc - exc)}</span>
          </div>
        )
      },
    },
    {
      key: 'taxIncludedAmountChange',
      header: <>買掛金額(税込)<AmountSourceTooltip source="PAYABLE_SUMMARY" /></>,
      render: (r) => <span className="tabular-nums">{formatCurrency(r.taxIncludedAmountChange ?? 0)}</span>,
    },
    {
      key: 'verifiedAmount',
      header: <>振込明細額<AmountSourceTooltip source="VERIFIED_AMOUNT" /></>,
      render: (r) => (
        <span className="tabular-nums">
          {r.verifiedAmount == null ? '-' : formatCurrency(r.verifiedAmount)}
        </span>
      ),
    },
    {
      key: 'paymentDifference',
      header: '差額',
      render: (r) => {
        if (r.paymentDifference == null) return '-'
        const cls = r.verificationResult === 0 ? 'text-red-600 font-medium tabular-nums' : 'tabular-nums'
        return <span className={cls}>{formatCurrency(r.paymentDifference)}</span>
      },
    },
    { key: 'verificationResult', header: '検証状態', render: (r) => <VerificationBadge row={r} /> },
    {
      key: 'mfTransferDate',
      header: '支払予定日',
      render: (r) => (
        <span className="tabular-nums text-xs">
          {r.mfTransferDate ?? '-'}
        </span>
      ),
    },
    {
      key: 'mfExportEnabled',
      header: 'MF出力',
      render: (r) => (
        <Switch
          checked={!!r.mfExportEnabled}
          onCheckedChange={(checked) => mfExportMutation.mutate({ row: r, enabled: checked })}
          disabled={mfExportMutation.isPending}
          aria-label="MF出力可否"
        />
      ),
    },
    {
      key: 'actions',
      header: '操作',
      render: (r) => (
        <Button size="sm" variant="outline" onClick={() => setDialogRow(r)}>
          {r.verificationResult == null ? '検証' : '詳細'}
        </Button>
      ),
    },
  ]

  // 残高表示 ON 時は「支払予定日」の前に opening/closing 列を挿入 (支払予定日と操作は右端維持)
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2731ms:
import * as React from "react"
import { Tooltip as TooltipPrimitive } from "radix-ui"

import { cn } from "@/lib/utils"

function TooltipProvider({
  delayDuration = 0,
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Provider>) {
  return (
    <TooltipPrimitive.Provider
      data-slot="tooltip-provider"
      delayDuration={delayDuration}
      {...props}
    />
  )
}

function Tooltip({
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Root>) {
  return <TooltipPrimitive.Root data-slot="tooltip" {...props} />
}

function TooltipTrigger({
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Trigger>) {
  return <TooltipPrimitive.Trigger data-slot="tooltip-trigger" {...props} />
}

function TooltipContent({
  className,
  sideOffset = 0,
  children,
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Content>) {
  return (
    <TooltipPrimitive.Portal>
      <TooltipPrimitive.Content
        data-slot="tooltip-content"
        sideOffset={sideOffset}
        className={cn(
          "bg-foreground text-background animate-in fade-in-0 zoom-in-95 data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2 z-50 w-fit origin-(--radix-tooltip-content-transform-origin) rounded-md px-3 py-1.5 text-xs text-balance",
          className
        )}
        {...props}
      >
        {children}
        <TooltipPrimitive.Arrow className="bg-foreground fill-foreground z-50 size-2.5 translate-y-[calc(-50%_-_2px)] rotate-45 rounded-[2px]" />
      </TooltipPrimitive.Content>
    </TooltipPrimitive.Portal>
  )
}

export { Tooltip, TooltipTrigger, TooltipContent, TooltipProvider }
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/MMfOauthClient.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/TMfOauthToken.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/TPaymentMfImportHistory.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /S /N /I \"password secret token account_number bank branch iban swift csvBody csvContent clientSecret accessToken refreshToken\" backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\*.java backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\*.java backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\master\\*.java" in C:\project\odamitsu-data-hub
 succeeded in 1954ms:
package jp.co.oda32.domain.model.finance;

import jakarta.persistence.*;
import jp.co.oda32.audit.AuditExclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * マネーフォワードクラウド会計 API OAuth2 クライアント設定。
 * 通常 1 レコードのみ運用（del_flg=0 が有効）。
 */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "m_mf_oauth_client")
public class MMfOauthClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    /** AES-256 暗号化済み Client Secret。OauthCryptoUtil で復号する (P1-05 案 C.3 で CryptoUtil から分離)。 */
    @AuditExclude
    @Column(name = "client_secret_enc", nullable = false)
    private String clientSecretEnc;

    @Column(name = "redirect_uri", nullable = false)
    private String redirectUri;

    @Column(name = "scope", nullable = false)
    private String scope;

    @Column(name = "authorize_url", nullable = false)
    private String authorizeUrl;

    @Column(name = "token_url", nullable = false)
    private String tokenUrl;

    @Column(name = "api_base_url", nullable = false)
    private String apiBaseUrl;

    @Column(name = "del_flg", nullable = false)
    @Builder.Default
    private String delFlg = "0";

    /**
     * MF tenant API (/v2/tenant) で取得した tenant id (P1-01 / DD-F-04)。
     * callback 直後に保存し、以後 access_token 利用時 (refresh 後等) に一致検証する。
     * NULL は未バインド (旧データ互換) を示し、初回 callback で確定する。
     */
    @Column(name = "mf_tenant_id")
    private String mfTenantId;

    /** MF tenant 名 (UI 表示用)。 */
    @Column(name = "mf_tenant_name")
    private String mfTenantName;

    /** tenant binding 確定タイムスタンプ。NULL なら未バインド。 */
    @Column(name = "tenant_bound_at")
    private Timestamp tenantBoundAt;

    @Column(name = "add_date_time", nullable = false)
    private Timestamp addDateTime;

    @Column(name = "add_user_no")
    private Integer addUserNo;

    @Column(name = "modify_date_time")
    private Timestamp modifyDateTime;

    @Column(name = "modify_user_no")
    private Integer modifyUserNo;

    /**
     * JPA ライフサイクルで監査フィールドを自動補完 (B-W3)。
     * サービス層で明示 set した値があればそちらを優先する。
     */
    @PrePersist
    void prePersist() {
        if (addDateTime == null) addDateTime = Timestamp.from(java.time.Instant.now());
    }

    @PreUpdate
    void preUpdate() {
        modifyDateTime = Timestamp.from(java.time.Instant.now());
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2858ms:
backend\src\main\java\jp\co\oda32\domain\model\finance\MMfOauthClient.java:32:    /** AES-256 暗号化済み Client Secret。OauthCryptoUtil で復号する (P1-05 案 C.3 で CryptoUtil から分離)。 */
backend\src\main\java\jp\co\oda32\domain\model\finance\MMfOauthClient.java:34:    @Column(name = "client_secret_enc", nullable = false)
backend\src\main\java\jp\co\oda32\domain\model\finance\MMfOauthClient.java:35:    private String clientSecretEnc;
backend\src\main\java\jp\co\oda32\domain\model\finance\MMfOauthClient.java:46:    @Column(name = "token_url", nullable = false)
backend\src\main\java\jp\co\oda32\domain\model\finance\MMfOauthClient.java:47:    private String tokenUrl;
backend\src\main\java\jp\co\oda32\domain\model\finance\MMfOauthClient.java:58:     * callback 直後に保存し、以後 access_token 利用時 (refresh 後等) に一致検証する。
backend\src\main\java\jp\co\oda32\domain\model\finance\MSupplierOpeningBalance.java:20: * MF journal #1 の credit branch から抽出した opening balance を保持し、
backend\src\main\java\jp\co\oda32\domain\model\finance\TCashbookImportHistory.java:40:    private String csvContent;
backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java:23:@Table(name = "t_mf_oauth_token")
backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java:24:public class TMfOauthToken {
backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java:35:    @Column(name = "access_token_enc", nullable = false)
backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java:36:    private String accessTokenEnc;
backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java:39:    @Column(name = "refresh_token_enc", nullable = false)
backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java:40:    private String refreshTokenEnc;
backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java:42:    @Column(name = "token_type", nullable = false)
backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java:44:    private String tokenType = "Bearer";
backend\src\main\java\jp\co\oda32\domain\model\finance\TPaymentMfImportHistory.java:57:    private byte[] csvBody;
backend\src\main\java\jp\co\oda32\domain\model\master\MLoginUser.java:38:    @Column(name = "password")
backend\src\main\java\jp\co\oda32\domain\model\master\MLoginUser.java:39:    private String password;
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3879ms:
package jp.co.oda32.domain.model.finance;

import jakarta.persistence.*;
import jp.co.oda32.audit.AuditExclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_payment_mf_import_history")
public class TPaymentMfImportHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "shop_no", nullable = false)
    private Integer shopNo;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    @Column(name = "source_filename", nullable = false)
    private String sourceFilename;

    @Column(name = "csv_filename", nullable = false)
    private String csvFilename;

    @Column(name = "row_count", nullable = false)
    private Integer rowCount;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "matched_count", nullable = false)
    @Builder.Default
    private Integer matchedCount = 0;

    @Column(name = "diff_count", nullable = false)
    @Builder.Default
    private Integer diffCount = 0;

    @Column(name = "unmatched_count", nullable = false)
    @Builder.Default
    private Integer unmatchedCount = 0;

    @AuditExclude
    @Column(name = "csv_body")
    private byte[] csvBody;

    /** P1-08: 取込元 Excel の SHA-256 (hex)。同一 hash 再取込時に preview で L1 警告。 */
    @Column(name = "source_file_hash", length = 64)
    private String sourceFileHash;

    /** P1-08: applyVerification 実行タイムスタンプ。NULL=未確定。non-NULL=確定済 (L2 警告対象)。 */
    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    /** P1-08: applyVerification 実行ユーザー (m_user.user_no)。 */
    @Column(name = "applied_by_user_no")
    private Integer appliedByUserNo;

    @Column(name = "del_flg", nullable = false, length = 1)
    @Builder.Default
    private String delFlg = "0";

    @Column(name = "add_date_time")
    private LocalDateTime addDateTime;

    @Column(name = "add_user_no")
    private Integer addUserNo;

    @Column(name = "modify_date_time")
    private LocalDateTime modifyDateTime;

    @Column(name = "modify_user_no")
    private Integer modifyUserNo;
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 4860ms:
package jp.co.oda32.domain.model.finance;

import jakarta.persistence.*;
import jp.co.oda32.audit.AuditExclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * マネーフォワードクラウド会計 API OAuth2 トークン永続化。
 * <p>
 * 同一 client_id で del_flg='0' は 1 レコードのみ active。
 * 新規 refresh 時は旧レコードを del_flg='1' 化して新規 insert する。
 */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_mf_oauth_token")
public class TMfOauthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Integer clientId;

    @AuditExclude
    @Column(name = "access_token_enc", nullable = false)
    private String accessTokenEnc;

    @AuditExclude
    @Column(name = "refresh_token_enc", nullable = false)
    private String refreshTokenEnc;

    @Column(name = "token_type", nullable = false)
    @Builder.Default
    private String tokenType = "Bearer";

    @Column(name = "expires_at", nullable = false)
    private Timestamp expiresAt;

    @Column(name = "scope")
    private String scope;

    @Column(name = "del_flg", nullable = false)
    @Builder.Default
    private String delFlg = "0";

    @Column(name = "add_date_time", nullable = false)
    private Timestamp addDateTime;

    @Column(name = "add_user_no")
    private Integer addUserNo;

    @Column(name = "modify_date_time")
    private Timestamp modifyDateTime;

    @Column(name = "modify_user_no")
    private Integer modifyUserNo;

    /** JPA ライフサイクルで監査フィールドを自動補完 (B-W3)。 */
    @PrePersist
    void prePersist() {
        if (addDateTime == null) addDateTime = Timestamp.from(java.time.Instant.now());
    }

    @PreUpdate
    void preUpdate() {
        modifyDateTime = Timestamp.from(java.time.Instant.now());
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /S /N /I \"account_number accountNumber bank_account bankAccount password_enc passwordEnc payment\" backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\*.java backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\*\\*.java" in C:\project\odamitsu-data-hub
 succeeded in 1460ms:
backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartMember.java:156:    @SerializedName("payment")
backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartMember.java:157:    @Column(name = "payment")
backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartMember.java:158:    private String payment;
backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartMember.java:200:    @SerializedName("payment_month")
backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartMember.java:201:    @Column(name = "payment_month")
backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartMember.java:202:    private String paymentMonth;
backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartMember.java:204:    @SerializedName("payment_date")
backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartMember.java:205:    @Column(name = "payment_date")
backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartMember.java:206:    private String paymentDate;
backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartMember.java:212:    @SerializedName("default_payment")
backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartMember.java:213:    @Column(name = "default_payment")
backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartMember.java:214:    private String defaultPayment;
backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartOrder.java:82:    @Column(name = "payment")
backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartOrder.java:83:    @SerializedName("payment")
backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartOrder.java:84:    private String payment; // 決済方法 (文字列, 最大255桁)
backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartOrder.java:85:    @Column(name = "payment_at")
backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartOrder.java:86:    @SerializedName("payment_at")
backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartOrder.java:87:    private LocalDate paymentAt; // 決済確定日 (日付, 10桁, Y-m-d)
backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartOrderProduct.java:111:    private String itemType; // 商品区分 (文字列, 最大255文字, 'product', 'shipping', 'payment_fees', 'point')
backend\src\main\java\jp\co\oda32\domain\model\finance\MPaymentMfRule.java:17:@Table(name = "m_payment_mf_rule")
backend\src\main\java\jp\co\oda32\domain\model\finance\MPaymentMfRule.java:18:public class MPaymentMfRule implements IEntity {
backend\src\main\java\jp\co\oda32\domain\model\finance\MPaymentMfRule.java:27:    @Column(name = "payment_supplier_code")
backend\src\main\java\jp\co\oda32\domain\model\finance\MPaymentMfRule.java:28:    private String paymentSupplierCode;
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:65:    @Column(name = "payment_difference")
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:66:    private BigDecimal paymentDifference;
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:89:     * 単一行のみを SQL UPDATE で変更すると {@code PaymentMfImportService.sumVerifiedAmountForGroup}
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:164:     * 設計書: claudedocs/design-phase-b-prime-payment-settled.md §2.2
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:165:     * Phase B': closing = opening + change - payment_settled の算出要素。
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:168:    @Column(name = "payment_amount_settled_tax_included", nullable = false)
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:170:    private BigDecimal paymentAmountSettledTaxIncluded = BigDecimal.ZERO;
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:176:    @Column(name = "payment_amount_settled_tax_excluded", nullable = false)
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:178:    private BigDecimal paymentAmountSettledTaxExcluded = BigDecimal.ZERO;
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:181:     * payment-only 行フラグ。
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:186:    @Column(name = "is_payment_only", nullable = false)
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:188:    private Boolean isPaymentOnly = false;
backend\src\main\java\jp\co\oda32\domain\model\finance\TCashbookImportHistory.java:35:    @Column(name = "total_payment", nullable = false)
backend\src\main\java\jp\co\oda32\domain\model\finance\TCashbookImportHistory.java:36:    private int totalPayment;
backend\src\main\java\jp\co\oda32\domain\model\finance\TInvoice.java:44:    @Column(name = "total_payment")
backend\src\main\java\jp\co\oda32\domain\model\finance\TInvoice.java:45:    private BigDecimal totalPayment;  // 入金合計
backend\src\main\java\jp\co\oda32\domain\model\finance\TInvoice.java:65:    @Column(name = "payment_date")
backend\src\main\java\jp\co\oda32\domain\model\finance\TInvoice.java:66:    private LocalDate paymentDate;  // 入金日
backend\src\main\java\jp\co\oda32\domain\model\finance\TPaymentMfAuxRow.java:22: * 設計書: {@code claudedocs/design-payment-mf-aux-rows.md}
backend\src\main\java\jp\co\oda32\domain\model\finance\TPaymentMfAuxRow.java:29:@Table(name = "t_payment_mf_aux_row")
backend\src\main\java\jp\co\oda32\domain\model\finance\TPaymentMfAuxRow.java:30:public class TPaymentMfAuxRow {
backend\src\main\java\jp\co\oda32\domain\model\finance\TPaymentMfAuxRow.java:59:    @Column(name = "payment_supplier_code", length = 20)
backend\src\main\java\jp\co\oda32\domain\model\finance\TPaymentMfAuxRow.java:60:    private String paymentSupplierCode;
backend\src\main\java\jp\co\oda32\domain\model\finance\TPaymentMfImportHistory.java:18:@Table(name = "t_payment_mf_import_history")
backend\src\main\java\jp\co\oda32\domain\model\finance\TPaymentMfImportHistory.java:19:public class TPaymentMfImportHistory {
backend\src\main\java\jp\co\oda32\domain\model\master\MPartner.java:4:import jp.co.oda32.constant.PaymentType;
backend\src\main\java\jp\co\oda32\domain\model\master\MPartner.java:86:    public PaymentType getPaymentType() {
backend\src\main\java\jp\co\oda32\domain\model\master\MPartner.java:87:        return PaymentType.fromCutoffCode(this.cutoffDate);
backend\src\main\java\jp\co\oda32\domain\model\master\MPaymentSupplier.java:25:@Table(name = "m_payment_supplier")
backend\src\main\java\jp\co\oda32\domain\model\master\MPaymentSupplier.java:27:public class MPaymentSupplier implements IEntity {
backend\src\main\java\jp\co\oda32\domain\model\master\MPaymentSupplier.java:29:    @Column(name = "payment_supplier_no")
backend\src\main\java\jp\co\oda32\domain\model\master\MPaymentSupplier.java:30:    @SequenceGenerator(name = "m_payment_supplier_payment_supplier_no_seq_gen", sequenceName = "m_payment_supplier_payment_supplier_no_seq", allocationSize = 1)
backend\src\main\java\jp\co\oda32\domain\model\master\MPaymentSupplier.java:31:    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_payment_supplier_payment_supplier_no_seq_gen")
backend\src\main\java\jp\co\oda32\domain\model\master\MPaymentSupplier.java:32:    private Integer paymentSupplierNo;
backend\src\main\java\jp\co\oda32\domain\model\master\MPaymentSupplier.java:33:    @Column(name = "payment_supplier_code")
backend\src\main\java\jp\co\oda32\domain\model\master\MPaymentSupplier.java:34:    private String paymentSupplierCode;
backend\src\main\java\jp\co\oda32\domain\model\master\MPaymentSupplier.java:35:    @Column(name = "payment_supplier_name")
backend\src\main\java\jp\co\oda32\domain\model\master\MPaymentSupplier.java:36:    private String paymentSupplierName;
backend\src\main\java\jp\co\oda32\domain\model\master\MSupplier.java:52:    @Column(name = "payment_supplier_no")
backend\src\main\java\jp\co\oda32\domain\model\master\MSupplier.java:53:    private Integer paymentSupplierNo;
backend\src\main\java\jp\co\oda32\domain\model\master\MSupplier.java:56:    @JoinColumn(name = "payment_supplier_no", insertable = false, updatable = false)
backend\src\main\java\jp\co\oda32\domain\model\master\MSupplier.java:57:    private MPaymentSupplier paymentSupplier;
backend\src\main\java\jp\co\oda32\domain\model\order\TOrder.java:55:    @Column(name = "payment_method")
backend\src\main\java\jp\co\oda32\domain\model\order\TOrder.java:56:    private String paymentMethod;
backend\src\main\java\jp\co\oda32\domain\model\smile\TSmilePayment.java:24:@Table(name = "t_smile_payment")
backend\src\main\java\jp\co\oda32\domain\model\smile\TSmilePayment.java:25:@IdClass(TSmilePayment.TSmilePaymentId.class)
backend\src\main\java\jp\co\oda32\domain\model\smile\TSmilePayment.java:26:public class TSmilePayment {
backend\src\main\java\jp\co\oda32\domain\model\smile\TSmilePayment.java:129:    @Column(name = "payment_amount")
backend\src\main\java\jp\co\oda32\domain\model\smile\TSmilePayment.java:130:    private BigDecimal paymentAmount; // 支払額
backend\src\main\java\jp\co\oda32\domain\model\smile\TSmilePayment.java:171:    public static class TSmilePaymentId implements Serializable {
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:24:@Table(name = "w_smile_payment")
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:25:@IdClass(WSmilePayment.WSmilePaymentId.class)
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:26:public class WSmilePayment {
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:129:    @Column(name = "payment_amount")
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:130:    private BigDecimal paymentAmount; // 支払額
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:166:     * WSmilePaymentからTSmilePaymentに変換するメソッド
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:168:     * @return 変換されたTSmilePaymentオブジェクト
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:170:    public TSmilePayment toTSmilePayment() {
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:171:        TSmilePayment payment = new TSmilePayment();
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:172:        payment.setProcessingSerialNumber(this.processingSerialNumber);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:173:        payment.setLineNo(this.lineNo);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:174:        payment.setVoucherDate(this.voucherDate);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:175:        payment.setYearMonth(this.yearMonth);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:176:        payment.setVoucherNo(this.voucherNo);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:177:        payment.setSupplierCode(this.supplierCode);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:178:        payment.setSupplierName1(this.supplierName1);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:179:        payment.setSupplierName2(this.supplierName2);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:180:        payment.setSupplierNameAbbr(this.supplierNameAbbr);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:181:        payment.setOfficeCode(this.officeCode);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:182:        payment.setOfficeName(this.officeName);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:183:        payment.setDepartmentCode(this.departmentCode);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:184:        payment.setDepartmentName(this.departmentName);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:185:        payment.setAreaCode(this.areaCode);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:186:        payment.setAreaName(this.areaName);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:187:        payment.setIndustryCode(this.industryCode);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:188:        payment.setIndustryName(this.industryName);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:189:        payment.setSupplierClass4Code(this.supplierClass4Code);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:190:        payment.setSupplierClass4Name(this.supplierClass4Name);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:191:        payment.setSupplierClass5Code(this.supplierClass5Code);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:192:        payment.setSupplierClass5Name(this.supplierClass5Name);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:193:        payment.setSupplierClass6Code(this.supplierClass6Code);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:194:        payment.setSupplierClass6Name(this.supplierClass6Name);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:195:        payment.setSupplierClass7Code(this.supplierClass7Code);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:196:        payment.setSupplierClass7Name(this.supplierClass7Name);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:197:        payment.setSupplierClass8Code(this.supplierClass8Code);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:198:        payment.setSupplierClass8Name(this.supplierClass8Name);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:199:        payment.setSupplierClass9Code(this.supplierClass9Code);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:200:        payment.setSupplierClass9Name(this.supplierClass9Name);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:201:        payment.setTransactionType(this.transactionType);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:202:        payment.setTransactionTypeName(this.transactionTypeName);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:203:        payment.setTransactionTypeAttribute(this.transactionTypeAttribute);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:204:        payment.setTransactionTypeAttributeName(this.transactionTypeAttributeName);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:205:        payment.setPaymentAmount(this.paymentAmount);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:206:        payment.setSettlementDueDate(this.settlementDueDate);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:207:        payment.setNoteCode(this.noteCode);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:208:        payment.setNote(this.note);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:209:        payment.setLoginId(this.loginId);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:210:        payment.setLoginName(this.loginName);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:211:        payment.setOperationDate(this.operationDate);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:212:        payment.setDataOccurrenceType(this.dataOccurrenceType);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:213:        payment.setCounterProcessingSerialNumber(this.counterProcessingSerialNumber);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:214:        payment.setCheckmarkType(this.checkmarkType);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:215:        payment.setCheckmarkTypeName(this.checkmarkTypeName);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:216:        payment.setImportDate(this.importDate);
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:217:        return payment;
backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java:226:    public static class WSmilePaymentId implements Serializable {
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/TInvoice.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/MPartnerGroup.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/TAccountsPayableSummary.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/embeddable/TAccountsReceivableSummaryPK.java' in C:\project\odamitsu-data-hub
 succeeded in 1829ms:
package jp.co.oda32.domain.model.finance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SMILEの請求実績を登録したテーブルのEntityクラス
 *
 * @since 2024/10/29
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_invoice", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"partner_code", "closing_date", "shop_no"})
})
public class TInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invoice_id")
    private Integer invoiceId;  // 請求ID

    @Column(name = "partner_code", nullable = false)
    private String partnerCode;  // 得意先コード

    @Column(name = "partner_name", nullable = false)
    private String partnerName;  // 顧客名

    @Column(name = "closing_date", nullable = false)
    private String closingDate;  // 締め日

    @Column(name = "previous_balance")
    private BigDecimal previousBalance;  // 前回請求残高

    @Column(name = "total_payment")
    private BigDecimal totalPayment;  // 入金合計

    @Column(name = "carry_over_balance")
    private BigDecimal carryOverBalance;  // 繰越残高

    @Column(name = "net_sales")
    private BigDecimal netSales;  // 純売上

    @Column(name = "tax_price")
    private BigDecimal taxPrice;  // 消費税額

    @Column(name = "net_sales_including_tax")
    private BigDecimal netSalesIncludingTax;  // 純売上額（税込）

    @Column(name = "current_billing_amount")
    private BigDecimal currentBillingAmount;  // 今回請求額

    @Column(name = "shop_no")
    private Integer shopNo;  // ショップ番号

    @Column(name = "payment_date")
    private LocalDate paymentDate;  // 入金日
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1868ms:
package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 20日締め売掛金テーブルのPKカラム設定
 *
 * @author k_oda
 * @since 2024/08/31
 */
@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class TAccountsReceivableSummaryPK implements Serializable {
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "partner_no")
    private Integer partnerNo;
    @Column(name = "transaction_month")
    private LocalDate transactionMonth;
    @Column(name = "tax_rate")
    private BigDecimal taxRate;
    @Column(name = "is_otake_garbage_bag")
    private boolean isOtakeGarbageBag;
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1888ms:
package jp.co.oda32.domain.model.finance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_partner_group")
public class MPartnerGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "partner_group_id")
    private Integer partnerGroupId;

    @Column(name = "group_name", nullable = false)
    private String groupName;

    @Column(name = "shop_no", nullable = false)
    private Integer shopNo;

    /**
     * 論理削除フラグ ('0'=有効, '1'=削除)。
     * SF-20: 物理削除を論理削除化するため V031 で追加。
     */
    @Column(name = "del_flg", nullable = false, length = 1)
    @Builder.Default
    private String delFlg = "0";

    /**
     * 所属パートナーコード。
     * SF-04 + SF-19: dedup と LAZY 化対応。
     * 取得時は {@link jp.co.oda32.domain.repository.finance.MPartnerGroupRepository}
     * の JOIN FETCH 経由で N+1 を回避すること。
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "m_partner_group_member",
            joinColumns = @JoinColumn(name = "partner_group_id")
    )
    @Column(name = "partner_code")
    @Builder.Default
    private List<String> partnerCodes = new ArrayList<>();
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1887ms:
package jp.co.oda32.domain.model.finance;

import jp.co.oda32.domain.model.embeddable.TAccountsPayableSummaryPK;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 20日締め買掛金テーブルのEntityクラス
 *
 * @author k_oda
 * @since 2024/09/10
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(name = "t_accounts_payable_summary")
@IdClass(TAccountsPayableSummaryPK.class)
public class TAccountsPayableSummary {
    @Id
    @Column(name = "shop_no")
    private Integer shopNo;

    @Id
    @Column(name = "supplier_no")
    private Integer supplierNo;

    @Column(name = "supplier_code")
    private String supplierCode;

    @Id
    @Column(name = "transaction_month")
    private LocalDate transactionMonth;

    @Id
    @Column(name = "tax_rate")
    private BigDecimal taxRate;

    @Column(name = "tax_included_amount")
    private BigDecimal taxIncludedAmount;

    @Column(name = "tax_excluded_amount")
    private BigDecimal taxExcludedAmount;

    @Column(name = "tax_included_amount_change")
    private BigDecimal taxIncludedAmountChange;

    @Column(name = "tax_excluded_amount_change")
    private BigDecimal taxExcludedAmountChange;

    // 検証結果のフラグを追加（1: 一致、0: 不一致、null: 検証なし）
    @Column(name = "verification_result")
    private Integer verificationResult;

    // SMILE支払額との差額
    @Column(name = "payment_difference")
    private BigDecimal paymentDifference;

    // マネーフォワードエクスポート可否フラグを追加
    @Column(name = "mf_export_enabled")
    private Boolean mfExportEnabled; // デフォルトではエクスポート可能

    // 手入力保護: trueならSMILE再検証バッチで上書きされない
    @Builder.Default
    @Column(name = "verified_manually", nullable = false)
    @ColumnDefault("false")
    private Boolean verifiedManually = false;

    // 検証時の備考（請求書番号・確認経緯など）
    @Column(name = "verification_note")
    private String verificationNote;

    /**
     * 振込実績で確定した集約金額 (税込)。
     * supplier × transaction_month の集約値で、同 supplier × month の税率別行
     * (taxRate 違い) には同一値が書き込まれる
     * (税率別の請求書内訳は 振込明細 Excel に存在しないため)。
     *
     * <p><b>不変条件</b>: 同 (shop, supplier, transactionMonth) の全税率行で本フィールドは同値であること。
     * 単一行のみを SQL UPDATE で変更すると {@code PaymentMfImportService.sumVerifiedAmountForGroup}
     * の重複カウント回避が効かなくなり、過大計上の原因となる。手動修正は必ず supplier × txMonth の
     * 全行同時に行うこと。
     *
     * <p>仕訳生成での扱い:
     * <ul>
     *   <li><b>振込仕訳</b> (借方買掛金 / 貸方普通預金): 集約値として 1 度だけ参照 (税率不要、両方「対象外」)</li>
     *   <li><b>仕入仕訳</b> (借方仕入高 / 貸方買掛金): 税率別の {@link #verifiedAmountTaxExcluded} を税率ごとに参照</li>
     * </ul>
     *
     * <p>関連:
     * <ul>
     *   <li>P1-03 案 D-2: 振込明細から per-supplier の値引/早払/送料を抽出して MF 仕訳に展開 (実装済)</li>
     *   <li>P1-09 案 D: 本フィールドの不変条件文書化 (本 Javadoc)</li>
     *   <li>P1-09 将来 案 E (未実施): supplier × month の集約値テーブルと税率別 breakdown テーブルへの分離</li>
     * </ul>
     */
    @Column(name = "verified_amount")
    private BigDecimal verifiedAmount;

    /**
     * 振込実績で確定した税抜金額 (税率別逆算)。
     * V026 (2026-04-23) で追加。{@link #verifiedAmount} (税込) を税率別に逆算した値で、
     * 同 supplier × month の税率別行で <b>異なる値</b> が入る (各行の {@code taxRate} で逆算)。
     *
     * <p>用途: MF CSV 仕入仕訳 (借方仕入高) の金額として使用。
     * {@code PayableBalanceCalculator.effectiveChangeTaxExcluded} 経由で 集計時に参照される。
     *
     * <p>計算式: {@code verifiedAmountTaxExcluded = verifiedAmount × 100 / (100 + taxRate)} (端数切捨)
     *
     * <p>注意: {@link #verifiedAmount} は同値だが、本フィールドは税率別に異なる。
     */
    @Column(name = "verified_amount_tax_excluded")
    private BigDecimal verifiedAmountTaxExcluded;

    /**
     * 振込明細 Excel 取込時の自動調整額 (= verifiedAmount - taxIncludedAmountChange、符号あり)。
     * 消費税丸め差等で ±100 円以内に自動合わせ込みされた金額。0 なら調整なし。
     * V026 で追加 (2026-04-23)。
     */
    @Builder.Default
    @Column(name = "auto_adjusted_amount", nullable = false)
    @ColumnDefault("0")
    private BigDecimal autoAdjustedAmount = BigDecimal.ZERO;

    /**
     * MF CSV 出力時の送金日 (CSV 取引日列に使う)。
     * Excel 振込明細取込 (applyVerification) で、行が属するセクションの送金日を記録する。
     * 5日払いセクション hit → 当月 5日。NULL 時は transactionMonth (締め日) にフォールバック。
     */
    @Column(name = "mf_transfer_date")
    private LocalDate mfTransferDate;

    /**
     * 前月末時点の累積残 (税込・符号あり)。
     * closing_balance = opening + effectiveChange は Entity には持たず DTO 層で算出。
     * 手動確定行でも常にバッチで上書きされる (change 列は保護、opening 列は繰越が絶対条件のため)。
     * 設計書: claudedocs/design-supplier-partner-ledger-balance.md §4.2
     */
    @Builder.Default
    @Column(name = "opening_balance_tax_included", nullable = false)
    @ColumnDefault("0")
    private BigDecimal openingBalanceTaxIncluded = BigDecimal.ZERO;

    /**
     * 前月末時点の累積残 (税抜・符号あり)。
     */
    @Builder.Default
    @Column(name = "opening_balance_tax_excluded", nullable = false)
    @ColumnDefault("0")
    private BigDecimal openingBalanceTaxExcluded = BigDecimal.ZERO;

    /**
     * 当月完了した支払額 (税込)。
     * supplier 単位支払を税率別 change 比で按分。
     * 設計書: claudedocs/design-phase-b-prime-payment-settled.md §2.2
     * Phase B': closing = opening + change - payment_settled の算出要素。
     */
    @Builder.Default
    @Column(name = "payment_amount_settled_tax_included", nullable = false)
    @ColumnDefault("0")
    private BigDecimal paymentAmountSettledTaxIncluded = BigDecimal.ZERO;

    /**
     * 当月完了した支払額 (税抜)。change_excl 比で按分。
     */
    @Builder.Default
    @Column(name = "payment_amount_settled_tax_excluded", nullable = false)
    @ColumnDefault("0")
    private BigDecimal paymentAmountSettledTaxExcluded = BigDecimal.ZERO;

    /**
     * payment-only 行フラグ。
     * 当月 change=0 だが前月支払があった supplier のために生成された行で、
     * stale-delete 対象から除外するための目印。
     */
    @Builder.Default
    @Column(name = "is_payment_only", nullable = false)
    @ColumnDefault("false")
    private Boolean isPaymentOnly = false;
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
**レビュー結果**

1. Severity: **Critical** / カテゴリ: **監査**
内容: `@AuditLog.pkArgIndex()` が単一引数しか `target_pk` に入れないため、複合 PK の主要操作で証跡の主キーが壊れます。例えば [TAccountsPayableSummaryService.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java:116) の `verify(shopNo, supplierNo, transactionMonth, taxRate, ...)` は `target_pk=shopNo` だけになります。V036 のコメントは `{"shopNo":...,"supplierNo":...}` を想定していますが、実装は一致していません。
推奨アクション: `pkExpression` または `pkArgIndexes` を導入し、複合 PK DTO を明示生成してください。最低限、16 メソッド分の実ログを E2E で検証してください。

2. Severity: **Critical** / カテゴリ: **監査**
内容: `before_values` が実際には before snapshot ではなく、`captureArgsAsAfter` の引数 JSON です。[FinanceAuditAspect.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/audit/FinanceAuditAspect.java:96) で `writer.write(..., pkJson, argsJson, afterJson, ...)` としており、更新前 DB 状態は取得していません。監査画面の Before/After diff は、更新差分の根拠として使うと誤認を生みます。
推奨アクション: 表示名を「Request/After」に変えるか、`AuditEntityLoader` を先に実装して true before を保存してください。

3. Severity: **Major** / カテゴリ: **監査 / 性能**
内容: AOP オーバーヘッド `+5-30ms` は未実証です。[design-finance-audit-log.md](C:/project/odamitsu-data-hub/claudedocs/design-finance-audit-log.md:102) は Jackson `valueToTree` と INSERT の概算ですが、`REQUIRES_NEW` は既存 tx を suspend し別 connection を取りに行くため、プール逼迫時に跳ねます。失敗時にも監査 INSERT が走るため、障害時の tail latency も増えます。
推奨アクション: `payment_mf_apply`, `bulk_verify`, `invoice import` の実データで p50/p95 を測定し、設計書を測定値に置換してください。

4. Severity: **Major** / カテゴリ: **監査 / 運用**
内容: 監査ログ書込み失敗は業務継続されますが、検知がログ依存です。[FinanceAuditAspect.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/audit/FinanceAuditAspect.java:101) では error log のみで、メトリクス・アラート・再送キューがありません。監査漏れが silent に長期間続くリスクがあります。
推奨アクション: `finance_audit_write_failed_total` のようなメトリクスとアラートを追加してください。監査必須操作は fail-open でよいか業務判断を明文化してください。

5. Severity: **Major** / カテゴリ: **監査 / 性能**
内容: 監査ログのフィルタ候補取得が全件ロードです。[AuditLogQueryService.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/audit/AuditLogQueryService.java:76) と [同:84](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/audit/AuditLogQueryService.java:84) で `repository.findAll().stream()` しています。数年保存すると `/admin/audit-log` 初期表示だけで全 audit row を JVM に載せます。
推奨アクション: repository に `SELECT DISTINCT target_table` / `operation` の JPQL or native query を追加してください。

6. Severity: **Major** / カテゴリ: **監査 / DB**
内容: `finance_audit_log` に保存期間・パーティション・アーカイブがありません。[V036__finance_audit_log.sql](C:/project/odamitsu-data-hub/backend/src/main/resources/db/migration/V036__finance_audit_log.sql:4) は通常テーブル + B-tree index のみです。検索画面も日付範囲任意なので、数百万行化すると index 膨張と VACUUM コストが問題になります。
推奨アクション: 月次パーティション、7 年保存、古い partition detach/export の運用を先に決めてください。

7. Severity: **Major** / カテゴリ: **セキュリティ / 監査**
内容: IP / User-Agent が admin 画面に生表示されます。[AuditLogPage](C:/project/odamitsu-data-hub/frontend/components/pages/admin/audit-log.tsx:257) は詳細ダイアログで IP/UA をそのまま出します。内部 admin 限定でも個人情報・端末識別情報に近く、閲覧履歴やマスキング方針がありません。
推奨アクション: IP は必要時のみ表示、UA は折りたたみ、監査ログ閲覧自体のアクセスログを検討してください。

8. Severity: **Major** / カテゴリ: **監査**
内容: バッチ経由の silent UPDATE は設計上対象外ですが、財務系ではむしろバッチ更新が重要です。[FinanceAuditAspect.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/audit/FinanceAuditAspect.java:34) は web 限定で、集計・再検証・インポート tasklet の更新は漏れます。`t_payment_mf_import_history` と audit log のどちらが正典かも曖昧です。
推奨アクション: バッチは Job/Step listener で `BATCH` actor の summary audit を残し、import history は「ファイル取込履歴」、audit log は「操作証跡」と役割を分けてください。

9. Severity: **Major** / カテゴリ: **セキュリティ / 監査**
内容: JSON シリアライズの循環参照対策は十分ではありません。[FinanceAuditAspect.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/audit/FinanceAuditAspect.java:55) は `FAIL_ON_SELF_REFERENCES` を disable していますが、複雑な双方向参照や Hibernate proxy の全展開を制御しません。`serialize()` も `Exception` しか捕まえないため、`StackOverflowError` は業務処理ごと落ちます。
推奨アクション: Entity 全体ではなく audit DTO を保存してください。少なくとも `JsonMappingException` と payload size、lazy proxy 展開のテストを追加してください。

10. Severity: **Major** / カテゴリ: **認可**
内容: `isAdmin()` は `shopNo=0 AND ROLE_ADMIN` で fail-safe ですが、DB 不整合時に全 admin 機能が停止します。[LoginUser.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/data/LoginUser.java:49) の挙動自体は安全側ですが、復旧用の break-glass 手順がありません。運用障害としては大きいです。
推奨アクション: 起動時またはログイン時に `shopNo=0` と `ROLE_ADMIN` の不整合を検知し、管理者向けアラートを出してください。

11. Severity: **Minor** / カテゴリ: **認可 / セキュリティ**
内容: 旧 `principal.shopNo == 0` が「誰でも通る」可能性は低く、通常は SpEL 評価失敗または false 寄りで 403 になります。[design-auth-matrix.md](C:/project/odamitsu-data-hub/claudedocs/design-auth-matrix.md:55) の記述は発見として重要ですが、Critical な権限昇格というより admin 機能の不可用リスクです。ただし実ログ・過去バージョンで確認しないと断定はできません。
推奨アクション: 過去の Spring Security access denied ログ、該当 endpoint のアクセスログを確認し、影響を「不可用」か「認可バイパス」か切り分けてください。

12. Severity: **Major** / カテゴリ: **UX / 例外**
内容: `ApiError` が JSON body を parse しないため、T5 の改善が画面では raw JSON toast になります。[api-client.ts](C:/project/odamitsu-data-hub/frontend/lib/api-client.ts:50) は `response.text()` をそのまま message にしています。`FinanceBusinessException` の「業務メッセージを返す」意図が UI で崩れます。
推奨アクション: `request()` 側でも `Content-Type: application/json` を parse し、`body.message` と `body.code` を `ApiError` に保持してください。

13. Severity: **Major** / カテゴリ: **例外 / 設計**
内容: 例外階層はまだ半分統一です。設計書では新規 `IllegalStateException` を避けるとしつつ、finance 配下には `ResponseStatusException(BAD_REQUEST)` や `IllegalStateException` が多数残っています。`FinanceExceptionHandler` も [basePackages が api.finance 限定](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/api/finance/FinanceExceptionHandler.java:35) なので、admin/batch controller 経由の finance 例外は Global 側へ流れます。
推奨アクション: `FinanceNotFoundException`, `FinanceForbiddenException`, `FinanceValidationException` まで含めるか、残す型の基準を lint/checklist 化してください。

14. Severity: **Minor** / カテゴリ: **UX / 設計**
内容: `AmountSource` の 10 種 enum は既に設計書の数字源を網羅していません。設計書には「銀行通帳」「現金出納帳 Excel」がありますが、[AmountSourceTooltip.tsx](C:/project/odamitsu-data-hub/frontend/components/common/AmountSourceTooltip.tsx:13) にはありません。また売掛の `tax_included_amount_change` に `PAYABLE_SUMMARY` を流用しており、意味がぼやけます。
推奨アクション: `BANK_STATEMENT`, `CASHBOOK_FILE`, `AR_SUMMARY` を追加し、enum と設計書 §5 の同期を型・テストで担保してください。

15. Severity: **Minor** / カテゴリ: **UX**
内容: Tooltip の click propagation 対策は通常の mouse click には効きますが、100% ではありません。[AmountSourceTooltip.tsx](C:/project/odamitsu-data-hub/frontend/components/common/AmountSourceTooltip.tsx:86) は `onClick` のみで、将来 trigger が button 化された場合の keyboard activation や pointer event までは考慮していません。現 DataTable は `<th onClick>` なので今は実害は小さいです。
推奨アクション: tooltip trigger に `onPointerDown/e.preventDefault` か、DataTable 側で sort button と header label を分離してください。

16. Severity: **Minor** / カテゴリ: **設計**
内容: 設計書間に矛盾があります。T2 は `IllegalStateException` 維持前提、T5 は新規使用回避を掲げています。また T1 §5 の「各画面の参照元」は実装変更に追随する仕組みがなく、すぐ古くなります。
推奨アクション: `claudedocs/DESIGN-DECISIONS-aggregated.md` に最新版の優先順位を集約し、画面列定義から参照元一覧を生成する方向に寄せてください。

**Opus 実装で見落としていた最重要 5 件**

1. 複合 PK が `target_pk` に入らず、監査対象行を一意特定できない。
2. `before_values` が before snapshot ではなく引数 JSON で、diff 監査として成立していない。
3. audit-log フィルタ候補 API が `findAll()` 全件ロードで、保存期間が延びるほど破綻する。
4. `ApiError` 未改修により、T5 の業務メッセージ改善がフロントでは raw JSON 表示になる。
5. バッチ更新が audit log から漏れ、import history との正典関係も未定義。
tokens used
116,866
**レビュー結果**

1. Severity: **Critical** / カテゴリ: **監査**
内容: `@AuditLog.pkArgIndex()` が単一引数しか `target_pk` に入れないため、複合 PK の主要操作で証跡の主キーが壊れます。例えば [TAccountsPayableSummaryService.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java:116) の `verify(shopNo, supplierNo, transactionMonth, taxRate, ...)` は `target_pk=shopNo` だけになります。V036 のコメントは `{"shopNo":...,"supplierNo":...}` を想定していますが、実装は一致していません。
推奨アクション: `pkExpression` または `pkArgIndexes` を導入し、複合 PK DTO を明示生成してください。最低限、16 メソッド分の実ログを E2E で検証してください。

2. Severity: **Critical** / カテゴリ: **監査**
内容: `before_values` が実際には before snapshot ではなく、`captureArgsAsAfter` の引数 JSON です。[FinanceAuditAspect.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/audit/FinanceAuditAspect.java:96) で `writer.write(..., pkJson, argsJson, afterJson, ...)` としており、更新前 DB 状態は取得していません。監査画面の Before/After diff は、更新差分の根拠として使うと誤認を生みます。
推奨アクション: 表示名を「Request/After」に変えるか、`AuditEntityLoader` を先に実装して true before を保存してください。

3. Severity: **Major** / カテゴリ: **監査 / 性能**
内容: AOP オーバーヘッド `+5-30ms` は未実証です。[design-finance-audit-log.md](C:/project/odamitsu-data-hub/claudedocs/design-finance-audit-log.md:102) は Jackson `valueToTree` と INSERT の概算ですが、`REQUIRES_NEW` は既存 tx を suspend し別 connection を取りに行くため、プール逼迫時に跳ねます。失敗時にも監査 INSERT が走るため、障害時の tail latency も増えます。
推奨アクション: `payment_mf_apply`, `bulk_verify`, `invoice import` の実データで p50/p95 を測定し、設計書を測定値に置換してください。

4. Severity: **Major** / カテゴリ: **監査 / 運用**
内容: 監査ログ書込み失敗は業務継続されますが、検知がログ依存です。[FinanceAuditAspect.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/audit/FinanceAuditAspect.java:101) では error log のみで、メトリクス・アラート・再送キューがありません。監査漏れが silent に長期間続くリスクがあります。
推奨アクション: `finance_audit_write_failed_total` のようなメトリクスとアラートを追加してください。監査必須操作は fail-open でよいか業務判断を明文化してください。

5. Severity: **Major** / カテゴリ: **監査 / 性能**
内容: 監査ログのフィルタ候補取得が全件ロードです。[AuditLogQueryService.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/audit/AuditLogQueryService.java:76) と [同:84](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/audit/AuditLogQueryService.java:84) で `repository.findAll().stream()` しています。数年保存すると `/admin/audit-log` 初期表示だけで全 audit row を JVM に載せます。
推奨アクション: repository に `SELECT DISTINCT target_table` / `operation` の JPQL or native query を追加してください。

6. Severity: **Major** / カテゴリ: **監査 / DB**
内容: `finance_audit_log` に保存期間・パーティション・アーカイブがありません。[V036__finance_audit_log.sql](C:/project/odamitsu-data-hub/backend/src/main/resources/db/migration/V036__finance_audit_log.sql:4) は通常テーブル + B-tree index のみです。検索画面も日付範囲任意なので、数百万行化すると index 膨張と VACUUM コストが問題になります。
推奨アクション: 月次パーティション、7 年保存、古い partition detach/export の運用を先に決めてください。

7. Severity: **Major** / カテゴリ: **セキュリティ / 監査**
内容: IP / User-Agent が admin 画面に生表示されます。[AuditLogPage](C:/project/odamitsu-data-hub/frontend/components/pages/admin/audit-log.tsx:257) は詳細ダイアログで IP/UA をそのまま出します。内部 admin 限定でも個人情報・端末識別情報に近く、閲覧履歴やマスキング方針がありません。
推奨アクション: IP は必要時のみ表示、UA は折りたたみ、監査ログ閲覧自体のアクセスログを検討してください。

8. Severity: **Major** / カテゴリ: **監査**
内容: バッチ経由の silent UPDATE は設計上対象外ですが、財務系ではむしろバッチ更新が重要です。[FinanceAuditAspect.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/audit/FinanceAuditAspect.java:34) は web 限定で、集計・再検証・インポート tasklet の更新は漏れます。`t_payment_mf_import_history` と audit log のどちらが正典かも曖昧です。
推奨アクション: バッチは Job/Step listener で `BATCH` actor の summary audit を残し、import history は「ファイル取込履歴」、audit log は「操作証跡」と役割を分けてください。

9. Severity: **Major** / カテゴリ: **セキュリティ / 監査**
内容: JSON シリアライズの循環参照対策は十分ではありません。[FinanceAuditAspect.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/audit/FinanceAuditAspect.java:55) は `FAIL_ON_SELF_REFERENCES` を disable していますが、複雑な双方向参照や Hibernate proxy の全展開を制御しません。`serialize()` も `Exception` しか捕まえないため、`StackOverflowError` は業務処理ごと落ちます。
推奨アクション: Entity 全体ではなく audit DTO を保存してください。少なくとも `JsonMappingException` と payload size、lazy proxy 展開のテストを追加してください。

10. Severity: **Major** / カテゴリ: **認可**
内容: `isAdmin()` は `shopNo=0 AND ROLE_ADMIN` で fail-safe ですが、DB 不整合時に全 admin 機能が停止します。[LoginUser.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/data/LoginUser.java:49) の挙動自体は安全側ですが、復旧用の break-glass 手順がありません。運用障害としては大きいです。
推奨アクション: 起動時またはログイン時に `shopNo=0` と `ROLE_ADMIN` の不整合を検知し、管理者向けアラートを出してください。

11. Severity: **Minor** / カテゴリ: **認可 / セキュリティ**
内容: 旧 `principal.shopNo == 0` が「誰でも通る」可能性は低く、通常は SpEL 評価失敗または false 寄りで 403 になります。[design-auth-matrix.md](C:/project/odamitsu-data-hub/claudedocs/design-auth-matrix.md:55) の記述は発見として重要ですが、Critical な権限昇格というより admin 機能の不可用リスクです。ただし実ログ・過去バージョンで確認しないと断定はできません。
推奨アクション: 過去の Spring Security access denied ログ、該当 endpoint のアクセスログを確認し、影響を「不可用」か「認可バイパス」か切り分けてください。

12. Severity: **Major** / カテゴリ: **UX / 例外**
内容: `ApiError` が JSON body を parse しないため、T5 の改善が画面では raw JSON toast になります。[api-client.ts](C:/project/odamitsu-data-hub/frontend/lib/api-client.ts:50) は `response.text()` をそのまま message にしています。`FinanceBusinessException` の「業務メッセージを返す」意図が UI で崩れます。
推奨アクション: `request()` 側でも `Content-Type: application/json` を parse し、`body.message` と `body.code` を `ApiError` に保持してください。

13. Severity: **Major** / カテゴリ: **例外 / 設計**
内容: 例外階層はまだ半分統一です。設計書では新規 `IllegalStateException` を避けるとしつつ、finance 配下には `ResponseStatusException(BAD_REQUEST)` や `IllegalStateException` が多数残っています。`FinanceExceptionHandler` も [basePackages が api.finance 限定](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/api/finance/FinanceExceptionHandler.java:35) なので、admin/batch controller 経由の finance 例外は Global 側へ流れます。
推奨アクション: `FinanceNotFoundException`, `FinanceForbiddenException`, `FinanceValidationException` まで含めるか、残す型の基準を lint/checklist 化してください。

14. Severity: **Minor** / カテゴリ: **UX / 設計**
内容: `AmountSource` の 10 種 enum は既に設計書の数字源を網羅していません。設計書には「銀行通帳」「現金出納帳 Excel」がありますが、[AmountSourceTooltip.tsx](C:/project/odamitsu-data-hub/frontend/components/common/AmountSourceTooltip.tsx:13) にはありません。また売掛の `tax_included_amount_change` に `PAYABLE_SUMMARY` を流用しており、意味がぼやけます。
推奨アクション: `BANK_STATEMENT`, `CASHBOOK_FILE`, `AR_SUMMARY` を追加し、enum と設計書 §5 の同期を型・テストで担保してください。

15. Severity: **Minor** / カテゴリ: **UX**
内容: Tooltip の click propagation 対策は通常の mouse click には効きますが、100% ではありません。[AmountSourceTooltip.tsx](C:/project/odamitsu-data-hub/frontend/components/common/AmountSourceTooltip.tsx:86) は `onClick` のみで、将来 trigger が button 化された場合の keyboard activation や pointer event までは考慮していません。現 DataTable は `<th onClick>` なので今は実害は小さいです。
推奨アクション: tooltip trigger に `onPointerDown/e.preventDefault` か、DataTable 側で sort button と header label を分離してください。

16. Severity: **Minor** / カテゴリ: **設計**
内容: 設計書間に矛盾があります。T2 は `IllegalStateException` 維持前提、T5 は新規使用回避を掲げています。また T1 §5 の「各画面の参照元」は実装変更に追随する仕組みがなく、すぐ古くなります。
推奨アクション: `claudedocs/DESIGN-DECISIONS-aggregated.md` に最新版の優先順位を集約し、画面列定義から参照元一覧を生成する方向に寄せてください。

**Opus 実装で見落としていた最重要 5 件**

1. 複合 PK が `target_pk` に入らず、監査対象行を一意特定できない。
2. `before_values` が before snapshot ではなく引数 JSON で、diff 監査として成立していない。
3. audit-log フィルタ候補 API が `findAll()` 全件ロードで、保存期間が延びるほど破綻する。
4. `ApiError` 未改修により、T5 の業務メッセージ改善がフロントでは raw JSON 表示になる。
5. バッチ更新が audit log から漏れ、import history との正典関係も未定義。
