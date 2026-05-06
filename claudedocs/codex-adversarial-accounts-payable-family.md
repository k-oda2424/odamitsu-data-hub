OpenAI Codex v0.128.0 (research preview)
--------
workdir: C:\project\odamitsu-data-hub
model: gpt-5.5
provider: openai
approval: never
sandbox: read-only
reasoning effort: none
reasoning summaries: none
session id: 019df0a1-8afd-75b3-835f-61000d2a6e08
--------
user
あなたは経理システムの上級レビュアーです。Cluster D (買掛金ファミリー) の批判的レビューを実施してください。

## プロジェクト
小田光データ連携基盤 (`C:/project/odamitsu-data-hub`)
- Java 21 / Spring Boot 3.3.x / PostgreSQL 17 / Next.js 16
- 経理: 買掛金集計→検証→マネーフォワード仕訳連携

## レビュー対象 (Cluster D)
**Backend** (主要):
- `backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java`
- `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableSummaryTasklet.java`
- `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableVerificationTasklet.java`
- `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableBackfillTasklet.java`
- `backend/src/main/java/jp/co/oda32/batch/finance/service/SmilePaymentVerifier.java`
- `backend/src/main/java/jp/co/oda32/batch/finance/service/MfPaymentAggregator.java` (or PayableMonthlyAggregator)

**Frontend**:
- `frontend/components/pages/finance/accounts-payable.tsx`
- `frontend/components/pages/finance/accounts-payable-ledger.tsx`
- `frontend/components/pages/finance/integrity-report.tsx`
- `frontend/components/pages/finance/supplier-balances.tsx`

## 既存レビュー (重複回避)
以下を必ず読んで、**重複しない指摘** に絞ること:
- `claudedocs/design-review-accounts-payable-family.md` (Opus 設計レビュー、Critical 4 / Major 9 / Minor 12)
- `claudedocs/code-review-accounts-payable-family.md` (Opus コードレビュー、Blocker 1 / Critical 5 / Major 10 / Minor 12)
- `claudedocs/design-accounts-payable.md` ほか設計書 6 本 (D 関連)

## 批判的レビューの観点
Opus はコード品質・セキュリティ・規約違反を網羅した。**あなたの仕事は別の角度から問い直すこと**:

1. **設計選択の根本前提への異議**
   - 「20日締め固定」「supplier_no=303 除外」「shop_no=1 固定」など、業務ハードコード前提は本当に正しいか？
   - 累積残＝期首残＋月次入出金 のモデルは MF と完全に一致しているか？ MF が訂正仕訳を入れた時の挙動は？
   - 「税率別に行を持つ」設計は MF の集計単位と整合しているか？

2. **代替アーキテクチャの提案**
   - 集計を Tasklet (バッチ) で持つ vs ストリーム/イベント駆動 vs マテリアライズドビュー
   - ConsistencyReview の actionType (IGNORE/MF_APPLY) を増やすべき場合 (PARTIAL_APPLY/REVIEW_LATER 等)
   - paymentSettled 算出を MF debit から取る (案 A) は他の経理連携との一貫性で問題ないか

3. **エッジケース / 失敗モード**
   - MF API 障害中に Tasklet が走った場合
   - supplier の合併・分割・コード変更が起きた場合
   - 部分支払 (請求 100 万に対し 60 万入金、残 40 万持越) の扱い
   - 月跨ぎの値引・返品 (相殺仕訳) と累積残の整合
   - shop_no を将来増やした時の再集計コスト

4. **ビジネス整合性**
   - 経理担当者が「画面で見た数字」と「MF の数字」と「請求書の数字」のどれを信じるべきか、設計上明確か
   - 監査時に「いつ誰が何を変更したか」が追跡可能か (audit trail の網羅性)

## 出力フォーマット
Markdown、日本語、簡潔に (max 800 行)。各指摘は:
- Severity: Critical / Major / Minor
- カテゴリ: 設計選択 / 代替案 / エッジケース / ビジネス整合
- 内容 (3-5 文)
- 推奨アクション (1-2 文)

最後に「Opus が見落としていた最重要 3 件」を明記。


codex
`using-superpowers` とレビュー対象に合う Spring/PostgreSQL/React 系のスキルを最小限で確認します。まず既存レビューと対象コードを読んで、重複を避けるために既出論点を抽出します。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content C:\\Users\\kazuki\\.agents\\skills\\using-superpowers\\SKILL.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content C:\\Users\\kazuki\\.agents\\skills\\postgresql-code-review\\SKILL.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content C:\\Users\\kazuki\\.agents\\skills\\typescript-react-reviewer\\SKILL.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content C:\\Users\\kazuki\\.agents\\skills\\spring-framework-patterns\\SKILL.md" in C:\project\odamitsu-data-hub
 succeeded in 4141ms:
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
The specified drive root "C:\Users\kazuki\AppData\Local\Temp\" either does not exist, or it is not a folder.
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 4217ms:
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
The specified drive root "C:\Users\kazuki\AppData\Local\Temp\" either does not exist, or it is not a folder.
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 4288ms:
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
The specified drive root "C:\Users\kazuki\AppData\Local\Temp\" either does not exist, or it is not a folder.
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 4582ms:
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
The specified drive root "C:\Users\kazuki\AppData\Local\Temp\" either does not exist, or it is not a folder.
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
Spring/PostgreSQL/React のレビューチェック観点を参照しました。次に、既存 Opus レビューと D 関連設計書から「既出の指摘」を拾い、今回のレビュー対象から別角度の論点だけを組み立てます。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content claudedocs\\design-review-accounts-payable-family.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content claudedocs\\code-review-accounts-payable-family.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'rg --files claudedocs | rg "accounts-payable|payable|supplier|integrity|consistency"' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'rg -n "20日|20|303|shop_no|tax|税率|partial|部分|audit|監査|MF_APPLY|IGNORE|paymentSettled|settled|累積|期首|訂正|返品|値引" claudedocs' in C:\project\odamitsu-data-hub
 succeeded in 1992ms:
# 設計レビュー: 買掛金ファミリー (Cluster D)

レビュー日: 2026-05-04
対象設計書: 6 本
- `claudedocs/design-accounts-payable.md`
- `claudedocs/design-accounts-payable-ledger.md`
- `claudedocs/design-integrity-report.md`
- `claudedocs/design-supplier-balances-health.md`
- `claudedocs/design-consistency-review.md`
- `claudedocs/design-phase-b-prime-payment-settled.md`

レビュアー: Opus サブエージェント
対象ブランチ: `refactor/code-review-fixes`

---

## サマリー

- 総指摘件数: **Critical 4 / Major 9 / Minor 12** (計 25 件)
- 承認状態: **Needs Revision** (Critical 修正後に再レビュー推奨)

最重要 (要対応):
1. **C-1 期首日のズレ**: 設計書は「MF 期首 = 2025-06-21」と記載するが実装は `MF_PERIOD_START = 2025-05-20` 固定。`MfPaymentAggregator.MF_FIRST_BUCKET = 2025-07-20` とも不整合。
2. **C-2 旧 `AccountsPayableSummaryTasklet` がまだ Bean 登録されており Phase A/B' を経由しない並行集計バッチとして残存**。本テーブルへ書き込み可能でリグレッション源。
3. **C-3 supplier_no=303 の除外が `AccountsPayableSummaryCalculator` のみで適用** されており、整合性レポート / 累積残一覧 / 買掛帳 ではフィルタしないため "MfOnly" や "SELF_MISSING" の偽陽性ノイズの源。
4. **C-4 `ConsistencyReviewService.upsert` が IGNORE → MF_APPLY 切替時にロールバック条件分岐が誤る** ? 旧 review が IGNORE (副作用なし) でも `previousVerifiedAmounts != null` ならロールバックを呼ぶべきだが、現在は `actionType == MF_APPLY` のみで判定。逆方向 (MF_APPLY → IGNORE) は OK だが、IGNORE で previous が消えるとロールバック手段を失う。

---

## Critical 指摘

### C-1 期首日と MF fiscal year 境界の三重定義不整合 (CRITICAL)

**該当**:
- `claudedocs/design-supplier-balances-health.md:124` ? 「fromMonth は **MF 会計期首 (2025-05-20)** 固定」
- `claudedocs/design-phase-b-prime-payment-settled.md:179-186` ? 「Backfill の起点は `2025-06-20`、その前月 `2025-05-20` 以前の累積 ≒ 期首買掛金残」
- `MEMORY.md` ? 「MF journal #1 (2025-06-21, 41 supplier \14,705,639)」「期首 (2025-07-20 bucket 未満) は verified_amount で fallback」
- 実装:
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:54` `MF_PERIOD_START = 2025-05-20`
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:56` `OPENING_DATE = 2025-06-20`
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfPaymentAggregator.java:38-43` `MF_PERIOD_START = 2025-05-20`、`MF_FIRST_BUCKET = 2025-07-20`
  - `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableBackfillTasklet.java:63` `EXPECTED_FROM_MONTH = 2025-06-20`

**問題**:
4 つの異なる「期首日」が混在する。
1. `2025-05-20` (MF_PERIOD_START、MF /journals fetch 開始日) ? 設計書 D で「MF 会計期首」と明記
2. `2025-06-20` (OPENING_DATE / EXPECTED_FROM_MONTH、自社 backfill 起点) ? 設計書 B' で起点
3. `2025-07-20` (MF_FIRST_BUCKET、MF debit 上書きを開始する閾値) ? `MfPaymentAggregator` のみ
4. `2025-06-21` (実 MF fiscal year 開始日、MEMORY.md より) ? どこにも定数化されていない

3 と 4 の関係は「MF API は会計年度跨ぎの取引が含まれず、2025-06-21 以降の取引が `toClosingMonthDay20` で 2025-07-20 bucket に入る」と推察できるが、設計書 B' / D / Phase B''(light) いずれにも明記なし。

**影響**:
- `SupplierBalancesService.accumulateMfJournals` は MF_PERIOD_START (2025-05-20) 以降の monthKey を採用するが、実際 MF fiscal year は 2025-06-21 開始のため bucket 2025-05-20 / 2025-06-20 は常に空 ? レスポンス上 `mfStartDate=2025-05-20` と返るが意味のないラベル。
- `MfPaymentAggregator.getMfDebitBySupplierForMonth` は transactionMonth が 2025-07-20 未満なら空 Map を返し fallback、それ以上なら MF debit で上書きする。バッチで 2025-06-20 / 2025-07-20 月を再集計したとき、両月の payment_settled が verified_amount fallback になるか MF debit になるかが暗黙ルール。コメントは「期首前 (2025-07-20 bucket 未満) は verified_amount で fallback」だが MEMORY.md 以外に文書化がない。
- 設計書 D §3.4 に「期首月 opening が 0 でない supplier (既存繰越あり) は ... 期首月の単月比較では差が出る」と書くが、そもそも「期首月」の定義が 2025-05-20 / 06-20 / 06-21 / 07-20 のどれか不明確。

**修正案**:
1. 共通定数クラス `FinanceConstants` (or 新 `MfPeriodConstants`) に以下を集約:
   - `MF_FISCAL_YEAR_START = LocalDate.of(2025, 6, 21)` ? MF fiscal year 開始 (取引 1 件目)
   - `SELF_BACKFILL_START = LocalDate.of(2025, 6, 20)` ? 自社 backfill 起点 (前月 20 日締め日)
   - `FIRST_PAYABLE_BUCKET = LocalDate.of(2025, 7, 20)` ? MF debit 上書きの初回 bucket
   - `MF_JOURNALS_FETCH_FROM = LocalDate.of(2025, 5, 20)` ? fetch 開始日 (fiscal year 跨ぎ fallback 含)
2. 設計書 6 本で同名の用語を使い、「期首」「期首月」「fiscal year 開始」を明確に区別する。
3. SupplierBalancesService / MfPaymentAggregator / MfSupplierLedgerService 各 service の `MF_PERIOD_START` private const を削除し共通定数に集約。

### C-2 旧 `AccountsPayableSummaryTasklet` が残存 (CRITICAL)

**該当**:
- `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableSummaryTasklet.java:33` ? `@Component @StepScope` で Bean 登録されたまま
- `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableAggregationTasklet.java:43` ? Phase B' 対応の新規 tasklet
- `backend/src/main/java/jp/co/oda32/batch/finance/config/AccountsPayableSummaryConfig.java` (確認推奨) ? job 定義
- 設計書 `claudedocs/design-accounts-payable.md:34` ? 「`AccountsPayableSummaryTasklet` / `AccountsPayableVerificationTasklet` は既存のまま」
- 設計書 `claudedocs/design-accounts-payable.md:451` ? 「再集計ジョブ `accountsPayableAggregation`(軽量版、Init+集計の2ステップ)に変更」

**問題**:
旧 tasklet は `opening_balance` / `payment_settled` / `is_payment_only` / `PayableMonthlyAggregator` を一切経由せず `t_accounts_payable_summary` を save する。設計書 D / B' / 整合性で前提とする「すべての書き込みは Phase B' 計算経路を通る」が崩れる。

`AccountsPayableSummaryConfig` 配下で `accountsPayableSummaryJob` Bean が今も残っている場合 (設計書 §6.5 では "既存" とのみ記載)、誤って起動すると:
- 既存行の opening_balance が触られないため Phase A 不変条件は守られるが、change のみ更新で payment_settled 不整合
- 新規 supplier 行の場合 opening_balance / payment_settled が NULL→DEFAULT 0 になり「期首前繰越のないクリーンな集計」と区別不能

**影響**:
- `/code-review` の対象差分には旧 tasklet がそのまま残っているが、設計書には「旧版 deprecation」の記述なし。
- 既存運用で `accountsPayableSummary` ジョブを誤起動すると Phase B' との整合が壊れ、整合性レポート / 累積残一覧で診断不能な diff が発生する。

**修正案**:
- 旧 `AccountsPayableSummaryTasklet` / `AccountsPayableVerificationTasklet` の役割を整理:
  - Phase B' 後は `AccountsPayableAggregationTasklet` + `AccountsPayableBackfillTasklet` + `AccountsPayableVerificationReportTasklet` が正規ルート
  - 旧 tasklet 2 本を `@Deprecated` + Job 削除 or `@ConditionalOnProperty` で禁止フラグ
- `AccountsPayableSummaryConfig` (現存ならば) のジョブ Bean を `accountsPayableAggregationJob` 1 本に統合する設計を設計書 D に追記する。

### C-3 `supplier_no=303` 除外が集計レイヤのみで適用 (CRITICAL)

**該当**:
- `backend/src/main/java/jp/co/oda32/batch/finance/service/AccountsPayableSummaryCalculator.java:89` `EXCLUDED_SUPPLIER_NO = 303` で集計時除外
- `MEMORY.md` 仕入一覧: 「supplierNo=303 除外 (買掛集計と一致)」
- 整合性レポート `AccountsPayableIntegrityService.java:200-333` ? supplier_no=303 のフィルタなし
- 累積残一覧 `SupplierBalancesService.java:117-204` ? 同上
- 買掛帳 `AccountsPayableLedgerService.java` ? 同上

**問題**:
集計バッチで supplier_no=303 を除外しても、過去に集計済みで残っている行 (もしくは shop_no=2 経由で混入する手打ち行) は累積残突合・整合性レポートに登場する。MF 側にも対応 sub_account がない可能性大で常時 `SELF_MISSING` 偽陽性。

**影響**:
- 累積残一覧 (軸 D) のサマリ `selfMissingCount` が偽カウント
- 整合性レポート画面が「無視可能」雑音で埋まる → 案 X+Y (IGNORE 操作) で消す運用になり、本来検出したい diff の発見性が下がる
- 設計書には「在庫表手打ち supplier の扱い」記述ゼロ

**修正案**:
1. 共通 util `PayableExclusionFilter.isExcludedSupplier(supplierNo)` を新設して全 service で適用
2. 設計書 D / 整合性 / 買掛帳 §2 / §3 の「対象データ」に「supplier_no=303 (在庫表手打ち) は除外」を明記
3. もしくは Repository 層に `findByShopNoAndTransactionMonthBetweenExcludingSupplierIn` のような専用 method を追加し service 横断で一律フィルタ

### C-4 `ConsistencyReviewService.upsert` のロールバック判定が不完全 (CRITICAL)

**該当**:
- `backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java:62-68`

```java
existingOpt.ifPresent(old -> {
    if (ACTION_MF_APPLY.equals(old.getActionType())) {
        rollbackVerifiedAmounts(req.getShopNo(), req.getEntryKey(),
                req.getTransactionMonth(), old.getPreviousVerifiedAmounts());
    }
});
```

- 設計書 `claudedocs/design-consistency-review.md:75` ? 「IGNORE 既存 review が MF_APPLY だった場合は **verified_amount を previous から復元**」
- 設計書 `claudedocs/design-consistency-review.md:243-260` (§8 フロー) ? 「old.actionType == MF_APPLY」前提

**問題**:
1. 旧 review が `MF_APPLY` で previous_verified_amounts を持つ → 新 review が `MF_APPLY` の場合、ロールバックしてから applyMfOverride。これは OK。
2. 旧 review が `MF_APPLY` → 新 review が `IGNORE` に切替。ロールバック → review 上書き保存。これも OK。
3. **問題ケース**: 旧 review が `IGNORE` (previous = null) で、なんらかの理由で previous_verified_amounts が NULL でない (manual SQL 編集等) 場合、現在実装はロールバックを **スキップ** してしまう。

加えて設計書 §8 と実装に乖離あり:
- 設計書: `previous = captureCurrentVerifiedAmounts(); applyMfOverride(req); save review(req, previous)` ? `previous` は MF_APPLY 時に毎回キャプチャ
- 実装: `applyMfOverride` 内で `previous` を return し、IGNORE 時は `previous = null` のまま review に保存

これは本質的に問題ないが、**`Map<String, BigDecimal>` を JSONB 列に書く際 IGNORE 時に明示的に NULL 化されていないと、PUT 連打で既存の MF_APPLY 由来の previous が JSONB に残り続けるリスク**がある (Hibernate の `@DynamicUpdate` 無し / merge 時の動作)。

**影響**:
- DELETE 時のロールバック対象判定 (`r.getActionType() == MF_APPLY`) は同じ条件で判断するため、IGNORE 上書きの後 DELETE → ロールバックしないが verified_amount は変わったまま、になる可能性。

**修正案**:
1. ロールバック判定を `old.getPreviousVerifiedAmounts() != null && !old.getPreviousVerifiedAmounts().isEmpty()` にする (action 種別ではなく snapshot 有無で判定)
2. `IGNORE` 上書き時 / `DELETE` 時に明示的に `setPreviousVerifiedAmounts(null)` してデータ整合
3. 設計書 §8 のフローも「`old.previousVerifiedAmounts != null` ならロールバック」に書き換え

---

## Major 指摘

### M-1 `MfPaymentAggregator.overrideWithMfDebit` が手動確定行を上書きしてしまう (Major)

**該当**: `backend/src/main/java/jp/co/oda32/batch/finance/service/PayableMonthlyAggregator.java:165-228`

設計書 B' §2.5 「**payment_settled は常に上書き**」とあるが、**案 A (MF debit 由来切替)** の `overrideWithMfDebit` は手動確定行 (`verified_manually=true`) も区別せずに MF debit を税率別に按分して payment_settled に書き込む。change=0 の supplier では「代表行に全額」+ 税率逆算という設計書 B' §2.2 §4.3 (R4) の方針 (「税抜逆算は使わない」) を破る経路がある (line 190-198)。

**影響**:
- 手動確定済みの supplier 月で、MF debit が verified_amount より優先される。MEMORY.md には「期首前は verified_amount で fallback」とあるが「期首後でも手動確定行を保護する」記述はなく、設計書にも案 A の挙動が文書化されていない。
- change=0 行に税率逆算を行う fallback コードは payment_settled が分からない supplier (MF にしかない) で発火するが、`r.getTaxRate()` が null や 0 の supplier (在庫表 / phantom 行) では NPE / DivideByZero (`divisor` は 100 以上だが) のリスク。

**修正案**:
1. 設計書 B' に "案 A (2026-04-23 追加)" として overrideWithMfDebit のセクション追加
2. 手動確定行は MF debit 上書き対象外 (verified_amount で固定) とする (R5 と一貫)
3. change=0 fallback を payment-only 行生成ロジックに統一する (overrideWithMfDebit から removeする)

### M-2 `summary` API がページングされていない supplier 集計をフルロードする (Major)

**該当**: `backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java:55-74`

```java
public AccountsPayableSummaryResponse summary(Integer shopNo, LocalDate transactionMonth) {
    Specification<TAccountsPayableSummary> spec = buildSpec(shopNo, null, transactionMonth, "all");
    List<TAccountsPayableSummary> list = repository.findAll(spec);
    long total = list.size();
    ...
```

**問題**:
- `findAll(spec)` で対象月の全行をロードしてカウント。COUNT クエリではない
- shop_no が null (admin) の場合は全 shop の全 supplier 取得 (現状 shop=1 のみだが将来的にスケールしない)
- 設計書 `claudedocs/design-accounts-payable.md` §5.2 では SQL レベルで集計する想定 (Response 構造から想像)

**影響**: 行数 200-300 程度のうちは問題ないが、「最新月時点で全 supplier × 税率」で 1000 行超えたとき heavy。

**修正案**:
- Repository に `@Query("SELECT COUNT(s), SUM(CASE WHEN s.verificationResult IS NULL THEN 1 ELSE 0 END), ... FROM TAccountsPayableSummary s WHERE ... ")` 集計クエリを 1 本追加

### M-3 設計書 D の「supplier_balances 初回 12.98s → cache hit 75ms」と実装に乖離 (Major)

**該当**:
- `MEMORY.md` 「supplier 累積残 + MF ヘルスチェック (軸 D+E): 初回 12.98s、cache hit 75ms」
- `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:140-150` (1) ループで `accumulateSelf` を? supplier 呼ぶ
- `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:106-114` (`paymentSupplierService.findByShopNo` を fetch 後に各 supplier ごとに `resolveMatchedSubs` で `mfSubToCodes` を full-scan)

**問題**:
- `resolveMatchedSubs` は `mfSubToCodes` (`Map<String, Set<String>>`) を 2 回 (line 279-287) 走査。supplier 数 N に対して O(N × M)、M は MF master 件数 (~1000)
- 設計書 D §3.6 「supplier 集計は `subAccountName → MonthBucket` の Map 事前構築で O(1) lookup」と書いているが、**実装では sub_account_name → supplier_code の逆引き map がないため supplier_code → matched sub_account_names の lookup は O(M)**

**影響**: 75ms はキャッシュ hit 時の MF 取得回避効果。実コード上は逆引き不足で支払い先 100 件 × MF master 1000 件 = 10 万回比較。今は気にならないが MEMORY.md に記された性能改善の出処が不明。

**修正案**:
- 設計書 D §3.6 にロジック詳細を追記
- `Map<String, Set<String>> codeToSubNames = invertMfSubToCodes()` を service 起動時 1 回構築

### M-4 整合性レポートの「処理済 sub_account 重複検出」が transient state (Major)

**該当**: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java:203, 266, 353`

```java
processedSubNames.add(sn + "|" + month);
...
if (processedSubNames.contains(subName + "|" + month)) continue;
```

**問題**:
- `processedSubNames` は self 側ループで「supplier × matchedSubName × month」を全埋め
- 1 supplier の supplier_code が複数の sub_account に対応する場合 (carded 表記揺れ) 全て processed として登録
- MF 側の "self にない sub_account" 列挙 (line 336-372) で漏れなく "MF only" として出してしまう

具体例: 同一 supplier に MF master が「カミ商事」「ｶﾐ 商事」両方登録されている場合、self 側ループで両方に処理マーク → MF 側は 0 件出力。これは正しい動作だが、`subName + "|" + month` キーの単純連結なので `|` を含む sub_account 名 (理論上ありうる) でキー衝突リスク。

**修正案**: `Map<String, Set<LocalDate>> processed` に変更 + key を `Pair<String, LocalDate>` レコードに切替

### M-5 旧 `AccountsPayableVerificationReportTasklet` が `verified_manually` 行も flag 上書き (Major)

**該当**: `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableVerificationReportTasklet.java:115-122, 130-141, 156-166`

**問題**:
- 「差額が null のデータ」「差額が 5 円未満」「verificationResult=1」分岐すべてで `summary.setVerificationResult` / `summary.setMfExportEnabled` を呼ぶ
- `verifiedManually=true` の行を **スキップしていない**
- 設計書 `claudedocs/design-accounts-payable.md:317-321` 「`SmilePaymentVerifier` の改修」では「`verified_manually = true` の行は**スキップ**」だが、Report tasklet には同じ保護がない

**影響**:
- 手動確定行が verificationReport ジョブ実行で mfExportEnabled=true / false に強制更新される (運用で `accountsPayableVerification` ジョブが走った後に `accountsPayableVerificationReport` も走る場合)
- 「差額 5 円未満で MF出力ON」を強制する処理は手動確定行の経理判断を覆す

**修正案**: ReportTasklet 内 3 箇所のループに `if (Boolean.TRUE.equals(summary.getVerifiedManually())) continue;` を追加

### M-6 `MfHealthCheckService` の anomaly 集計が 0 固定残存 (Major)

**該当**:
- `backend/src/main/java/jp/co/oda32/domain/service/finance/MfHealthCheckService.java:91-107`
- 設計書 `claudedocs/design-supplier-balances-health.md:188-192` ? 「共通 util `PayableAnomalyCounter.count(shopNo)` を新設」
- 同 `MEMORY.md` Next Session Pickup ? 「shop 単位の anomaly 集計 util 化 (`PayableAnomalyCounter`) ? ヘルスチェック画面の `verifyDiffCount/continuityBreakCount/monthGapCount` を 0 固定から実装へ」

**問題**:
- 設計書 D §4.2 に「PayableAnomalyCounter で集計」と書いてあるが、現状 `verifyDiffCount` / `continuityBreakCount` / `monthGapCount` は **0 固定**
- `claudedocs/design-supplier-balances-health.md:196-200` のヘルス判定ロジック (??/??/??) は anomaly 合計 > 0/10 でしきい値判断するため、**機能していない**
- TODO コメントが残っているのは率直だが、設計書ではこれを「実装する」と明記

**修正案**:
- 設計書側を「v1: anomaly 詳細は 0 固定。v2 で `PayableAnomalyCounter` 実装」と明示する or 実装する
- Sprint 単位での "未着手" 状態をユーザーに見せる UI バッジ追加 (UI 側で `verifyDiffCount === 0 && continuityBreakCount === 0 ? '集計未対応' : ...`)

### M-7 `AccountsPayableIntegrityService.buildSupplierCumulativeDiffMap` が SupplierBalancesService の重い処理を毎回呼ぶ (Major)

**該当**: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java:543-560`

`/integrity-report` API 呼び出しのたびに `supplierBalancesService.generate(shopNo, toMonth, refresh)` を呼ぶ。設計書 §11 に "12 ヶ月処理の所要時間 8-15s" と記述されているが、その 8-15s には軸 D の generate コストも含まれるか不明。

**影響**:
- `/integrity-report` のレスポンス時間が `/supplier-balances` と同じレベルになる
- キャッシュ hit 時 75ms だが、refresh=true で 13 秒級になる
- 設計書 整合性 §11 のリスク表に記載なし

**修正案**:
- 設計書に「軸 D 連動で supplier_cumulative_diff を併記する」を §3 / §11 に明記
- パフォーマンス見積もりを「キャッシュ hit 時 ~200ms / cold 13s」に更新

### M-8 設計書 B' §6.2 の説明と実装の `closing` 列構成が違う (Major)

**該当**:
- 設計書 `claudedocs/design-phase-b-prime-payment-settled.md:432-438` ? UI 列構成 `| 前月繰越(opening) | 当月支払(payment_settled) | 累積残(closing) |`
- 実際の `frontend/components/pages/finance/accounts-payable.tsx` (確認推奨) と `AccountsPayableLedgerResponse` 構造

設計書では balance トグル ON で 3 列追加だが、`AccountsPayableResponse.from` (line 121-130) の構造を見ると 6 列 (opening_incl/excl, payment_settled_incl/excl, closing_incl/excl) すべて返す。**設計書の記述粒度と実装の不整合**。

**影響**: フロント実装担当者が見るとき「結局何列出すの?」が不明確。

**修正案**: 設計書 B' §6.2 を実装に合わせて「税込/税抜の 2 段表記で 3 列 (opening/payment_settled/closing) × 2 = 6 セル」に修正。

### M-9 案 A の MF debit 上書き経路が `AccountsPayableBackfillTasklet` と `AccountsPayableAggregationTasklet` で重複定義 (Major)

**該当**:
- `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableAggregationTasklet.java:107-111`
- `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableBackfillTasklet.java:127-130`

両方とも `monthlyAggregator.overrideWithMfDebit(rows, mfPaymentAggregator.getMfDebitBySupplierForMonth(shop, month), month)` を呼ぶ。同じ 5 行のシーケンス。

**影響**: 修正時に片方忘れリスク (例: shop_no をパラメータ化したいとき)。

**修正案**:
- `PayableMonthlyAggregator` に `applyAllPipelines(rows, prev, shopNo, month)` を追加し opening / payment_settled / mf_debit / payment_only を 1 メソッドに集約。
- `monthlyAggregator.applyOpenings` / `applyPaymentSettled` / `overrideWithMfDebit` / `generatePaymentOnlyRows` の呼び出し順は実装的に意味があるためコメントで明示。

---

## Minor 指摘

### m-1 `TAccountsPayableSummaryService.findAll()` が DB 全件 (Minor)

`backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java:39-41` ? どこから呼ばれているか不明だが Service 公開 method として残存。使用箇所がなければ削除推奨。

### m-2 `findPaged` の `verificationFilter` が `String` で型安全性なし (Minor)

`TAccountsPayableSummaryService.java:44-52, 89-103` ? `"unverified"|"unmatched"|"matched"|"all"` の文字列マッチ。設計書 §5.1 の `verificationResult` パラメータ (`null|0|1|all`) と命名不一致。 enum 化 + `@Pattern` バリデーション推奨。

### m-3 `AccountsPayableVerifyRequest` に `verifiedAmount` の正値バリデーションなし (Minor)

`backend/src/main/java/jp/co/oda32/dto/finance/AccountsPayableVerifyRequest.java:11-12` ? `@NotNull` のみ。負値や 0 が来ると差額計算で予期せぬ挙動 (例: `applyVerification` 内で taxExcluded に負値が入る)。設計書 `design-accounts-payable.md` §5.3 にも値域記述なし。`@PositiveOrZero` 推奨。

### m-4 設計書「振込明細MF一括検証 (`PaymentMfImportService#applyVerification`)」の参照名と実コードに齟齬 (Minor)

設計書 `claudedocs/design-accounts-payable.md:289-291` 「振込明細MF一括検証 (`PaymentMfImportService#applyVerification`)」 vs 実装 `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java:184` `applyVerification(String uploadId, Integer userNo)` ? 引数情報がなく、設計書から API 把握しにくい。

### m-5 設計書 D §3.4「期首月 opening が 0 でない supplier」の記述が支離滅裂 (Minor)

`claudedocs/design-supplier-balances-health.md:115-117` ? 「期首月 opening が 0 でない supplier (既存繰越あり) は、その opening が self 側のみに現れるため期首月の単月比較では差が出るが、本 endpoint は asOfMonth 時点の累積差のみを返す」が、**実際は m_supplier_opening_balance による期首注入で MF も含めて両者対称になるよう修正済**。設計書がこの修正を反映していない。

### m-6 `PayableMonthlyAggregator.SupplierAgg` 内 `closingExclTotal` が未使用 (Minor)

`backend/src/main/java/jp/co/oda32/batch/finance/service/PayableMonthlyAggregator.java:401-412` ? record fields のうち `closingExclTotal` は payment-only 行生成時の `setOpeningBalanceTaxExcluded(agg.closingExclTotal())` (line 347) でのみ使用。これは正しいが、`SupplierAgg` の全体構造のうち `payment-only` ルート以外で参照される様子なし。設計書 B' §2.2 疑似コードに合わせれば OK。

### m-7 `MfJournalCacheService` に永続化 / TTL なし (Minor)

`backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfJournalCacheService.java:21-30` ? JVM 再起動でキャッシュ全破棄。設計書 `design-supplier-balances-health.md:351` 「キャッシュの永続化 (DB 化) は不要」と明示されているため設計書通りだが、運用でバックエンド再起動が頻繁なら 8-15s レスポンスが復帰時に頻発する。invalidate API はあるが「再起動で勝手にクリア」の挙動を運用ドキュメントに残すこと推奨。

### m-8 設計書 整合性 §10 の 「決定」表に欠落あり (Minor)

`claudedocs/design-integrity-report.md:404-413` ? 6 行の決定表に「supplier_no=303 除外の有無」「shop_no=1 固定 vs マルチ」「期首期間以前の扱い」「mf_account_master 重複時の挙動」が含まれていない。

### m-9 `ConsistencyReviewService.applyMfOverride` 端数吸収後に再 save (Minor)

`backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java:200-227` ? for ループ内で `summaryRepository.save(r)` を呼んだ後、端数吸収で `largest` を更新するが **再 save なし**。Entity が同じ tx 内で managed 状態なので merge 時に flush で反映されるはず (Hibernate dirty check) だが、**`largest` の `verifiedAmountTaxExcluded` 更新を `save` 経由で永続化していないため、cascade / @Transactional の動作に依存**。明示 save 推奨。

### m-10 `AccountsPayableLedgerService.aggregateMonth` の autoAdjustedAmount 平均化ロジック (Minor)

`backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableLedgerService.java:181-184` ? 「税率別複数行で autoAdjusted が重複計上される」ため行数で割って代表値化。これは振込明細の "全税率行に同額書き込み" の前提に依存する。手動 verify では税率別に異なる autoAdjusted が入る可能性あるが、その場合の挙動は未定義 (平均で歪む)。

### m-11 buildSupplierCumulativeDiffMap の例外処理が雑 (Minor)

`AccountsPayableIntegrityService.java:556` ? `catch (Exception e)` で全飲み込み + WARN ログ + 空 Map 返却。MF 認証切れも握りつぶされ、UI には「期末累積残判定スキップ (計算失敗)」程度のログのみ。MfReAuthRequiredException / MfScopeInsufficientException は再 throw して上位の handleException に委譲推奨。

### m-12 V026 / V027 / V028 migration が同一ブランチで増殖 (Minor)

`backend/src/main/resources/db/migration/V025-V029` ? 5 個のミグレーションが同じブランチで連続。各ファイル内コメントは充実しているが、ブランチマージ時の version 競合リスク。Liquibase でなく Flyway の場合、別ブランチから V028 が来るとマージで衝突。`feature/payment-settled` `feature/consistency-review` 等、機能粒度のブランチに切る運用推奨 (設計プロセス側の問題)。

---

## 設計書間の整合性 (6 本横断レビュー)

### 用語統一の問題

| 用語 | 設計書 D | 設計書 B' | 設計書 整合性 | 設計書 買掛帳 | コード |
|---|---|---|---|---|---|
| 期首日 | 2025-05-20 | 2025-06-20 | (記述なし) | (記述なし) | 2025-05-20 / 06-20 / 06-21 / 07-20 |
| 「supplier」 | payment_supplier | payment_supplier | payment_supplier | payment_supplier | OK |
| 累積残 | "selfBalance" / "mfBalance" | "closing" | (該当なし) | "closingBalanceTaxIncluded" | 不統一 |
| 「期首前」 | (asOfMonth が MF_PERIOD_START 以前は 400) | (Backfill 対象外) | (記述なし) | (continuity チェックの起点) | 4 種類混在 |
| MATCH 閾値 | \100 | (記述なし) | \100 (MATCH_TOLERANCE) | \100 (VERIFY_DIFF_THRESHOLD) | 3 箇所定数別定義 |

### 重複定義

- `MATCH_TOLERANCE = 100` が以下 4 箇所で重複:
  - `TAccountsPayableSummaryService.MATCH_THRESHOLD = 100`
  - `AccountsPayableIntegrityService.MATCH_TOLERANCE = 100`
  - `AccountsPayableLedgerService.VERIFY_DIFF_THRESHOLD = 100`
  - `SupplierBalancesService.MATCH_TOLERANCE = 100`
  - `ConsistencyReviewService.STALE_TOLERANCE = 100` (実は同じ意図)

設計書 D §3.6 / 整合性 §3.2 / B' / consistency-review §C3 すべてが「MATCH_TOLERANCE と揃える」と書いているが、実装は **共通定数化されていない**。`FinanceConstants` への集約推奨。

### スコープの食い違い

- 設計書 `design-accounts-payable-ledger.md:564` 「MF 比較は明示ボタン」 vs 設計書 `design-integrity-report.md:39` 「buying ledger に MF 比較バッジ追加」 ? どちらが正?
- 設計書 D §3.4 「fromMonth は **MF 会計期首 (2025-05-20)** 固定」 vs 設計書 B' §1.4 「期首残 ? \14.7M は既知差として UI に明示」 ? D は期首注入後の世界を前提、B' は期首注入前の世界を前提 ? 両者の前後関係が時系列で書いていない
- 設計書 整合性 §2.3 「サーバー側キャッシュなし (ユーザー却下済)」 vs `MfJournalCacheService` 実装 ? 整合性レポート自体はキャッシュしないが MF /journals はキャッシュ。ユーザー指摘の意図と乖離してないか確認推奨

### バージョン管理の不整合

- 設計書 D の作成日 2026-04-23、整合性 2026-04-22、B' 2026-04-22、累積残 2026-04-23 ? 1 日ずれの依存関係 (D は整合性+B' に依存)
- 設計書 D §1 の関連設計書リストに `design-supplier-partner-ledger-balance.md` (Phase A の母体) が出てくるが本レビュー対象 6 本に含まれない ? 暗黙の前提が大きい

---

## 設計書 vs 実装の乖離

| # | 設計書 | 実装 | 乖離内容 |
|---|---|---|---|
| 1 | `design-supplier-balances-health.md:188-192` PayableAnomalyCounter 新設 | `MfHealthCheckService.java:97-106` | 未実装、0 固定 |
| 2 | `design-supplier-balances-health.md:124` MF 期首 2025-05-20 固定 | `MfPaymentAggregator.java:43` MF_FIRST_BUCKET 2025-07-20 別経路 | 三重定義 (C-1) |
| 3 | `design-accounts-payable.md:34` 旧 tasklet そのまま | `AccountsPayableSummaryTasklet.java` 残存、新 tasklet と並行 | 役割整理なし (C-2) |
| 4 | `design-consistency-review.md:75` IGNORE 既存 review が MF_APPLY だった場合は復元 | `ConsistencyReviewService.java:64-68` | actionType でしか判定せず previous でない (C-4) |
| 5 | `design-accounts-payable.md:317-321` SmilePaymentVerifier の手動確定スキップ | `AccountsPayableVerificationReportTasklet.java:115-141` | スキップ漏れ (M-5) |
| 6 | `design-phase-b-prime-payment-settled.md:432-438` UI 3 列 | `AccountsPayableResponse.java` で 6 セル相当 | 列数の乖離 (M-8) |
| 7 | `design-supplier-balances-health.md:115-117` 期首月 opening 自社のみ | `SupplierBalancesService.java:128-129` openingMap 経由 MF も同期 | 設計書が古い (m-5) |
| 8 | `design-integrity-report.md:411` 30 秒以内想定 | `AccountsPayableIntegrityService.java:543` SupplierBalancesService.generate 重重複呼び出し | 性能リスク見積もり不足 (M-7) |
| 9 | `design-accounts-payable.md:312-313` SmilePaymentVerifier 改修「verified_manually 行スキップ」 | `SmilePaymentVerifier.java:155-159, 374-378` で実装済 | これは OK |
| 10 | `design-supplier-partner-ledger-balance.md` (本レビュー外) | `PayableBalanceCalculator` 新設 | 設計書間の依存連鎖が長く、6 本だけでは閉じない |

---

## レビューチェックリスト結果

### Spring Boot 観点

| 項目 | 結果 |
|---|---|
| Layer 違反 (Controller→Repository 直接) | OK ? Controller→Service→Repository の構造維持 |
| @Transactional 配置 | OK 概ね ? `ConsistencyReviewService.upsert` が class-level @Transactional + readOnly でない (write を含むため正しい) |
| N+1 | 部分的に未解消 ? `SupplierBalancesService` の supplier 逆引きで O(N×M)、`TAccountsPayableSummaryService.summary` で行全件 fetch (M-2/3) |
| DI | OK ? constructor injection / `@RequiredArgsConstructor` |
| DTO 変換 | OK ? `AccountsPayableResponse.from` factory method パターン |
| バリデーション | 部分的 ? `AccountsPayableVerifyRequest` は @NotNull のみ (m-3)、`ConsistencyReviewRequest` には設計書 §3.1 の note @Size(max=500) があるか確認推奨 |
| Migration 安全性 | OK ? V025 / V026 / V027 はすべて metadata-only or 安全 |

### Accounts Payable 固有観点

| 項目 | 結果 |
|---|---|
| 累積残計算 (期首残 + 月次入出金 = 当月残) | OK ? `PayableBalanceCalculator` で集約、4 箇所で一貫 |
| MfPaymentAggregator の supplier 集計 | 設計書外の挙動あり ? 手動確定行も上書き、change=0 fallback (M-1) |
| 期首前 fallback | コードと設計書で日付の定義不一致 (C-1) |
| `verified_manually` 保護 | 集計バッチ・SmilePaymentVerifier では OK、ReportTasklet で漏れ (M-5) |
| 100 円閾値 | 4 箇所で個別定義、共通化されていない (整合性 §) |
| V026/V027 migration 安全性 | OK |
| 設計書 6 本の整合性 | 用語ブレ・期首日定義の三重化・スコープ食い違いあり (上記参照) |
| shop_no=1 固定 vs マルチ | 設計書で「現在 shop=1」明記、実装で `FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO=1` 集約。OK だが将来拡張時の TODO コメントなし |
| 整合性 4 カテゴリ網羅 | OK ? mfOnly / selfOnly / amountMismatch / unmatchedSuppliers (実装は 4 カテゴリで設計書 §3.2 と一致) |
| キャッシュ invalidate | OK ? `MfJournalCacheService.invalidateAll(shopNo)` あり、UI から呼べる |

---

## 推奨アクション (優先度順)

1. **[Critical]** C-1 の期首日定義を `FinanceConstants` に集約し、設計書 6 本で用語統一
2. **[Critical]** C-2 旧 `AccountsPayableSummaryTasklet` / `AccountsPayableVerificationTasklet` を `@Deprecated` 化、新ジョブを正規ルートに
3. **[Critical]** C-3 supplier_no=303 除外を全 service で適用、設計書に明記
4. **[Critical]** C-4 `ConsistencyReviewService` のロールバック判定を `previous != null` ベースに修正
5. **[Major]** M-1 `overrideWithMfDebit` の手動確定行スキップ、case 分岐の設計書追記
6. **[Major]** M-2 `summary()` API を JPQL 集計に置換
7. **[Major]** M-5 `AccountsPayableVerificationReportTasklet` の `verified_manually` スキップ追加
8. **[Major]** M-6 `PayableAnomalyCounter` 実装 or 設計書側で v2 に明示繰越
9. **[Major]** M-8 設計書 B' §6.2 の UI 列構成を実装に合わせて修正
10. **[Minor]** 100 円閾値を `FinanceConstants.MATCH_TOLERANCE` で集約、5 箇所参照を一本化
11. **[Minor]** 設計書 D §3.4 / B' §2.4 の期首残扱いを「現在は期首注入済み」記述に更新

---

## 終わりに

買掛金ファミリー (Cluster D) は本プロジェクトで最も複雑かつ業務クリティカルな機能群。設計書は 6 本構成で十分網羅的だが、**Phase A → B → B' → 案 A → 整合性 → consistency-review → 累積残 → ヘルスチェック** と短期間で連続増分されたため、**期首日・閾値・除外 supplier・旧 tasklet 残存** の 4 軸で技術的負債が顕在化している。

機能要件は 80% 以上満たしているが、**運用安定化フェーズ**として上記 Critical 4 件 + Major 9 件を 1?2 スプリント程度で消化し、設計書を「実装の現在地」に再同期することを推奨する。
The specified drive root "C:\Users\kazuki\AppData\Local\Temp\" either does not exist, or it is not a folder.
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2183ms:
# コードレビュー: 買掛金ファミリー (Cluster D)

レビュー日: 2026-05-04
ブランチ: refactor/code-review-fixes
レビュアー: Opus サブエージェント
対象設計レビュー: `claudedocs/design-review-accounts-payable-family.md` (Critical 4 / Major 9 / Minor 12)

## 前提

設計レビューと重複する指摘 (期首日の三重定義 / 旧 `AccountsPayableSummaryTasklet` 残存 / supplier_no=303 除外漏れ / `ConsistencyReview` ロールバック判定 等) は再掲しない。コード固有の不具合・実装品質・Spring Boot / Next.js 観点に絞る。

設計レビューは「役割整理 / 設計と実装の乖離」を扱い、本コードレビューは「実装上の Bug・性能・トランザクション・型安全性・UI 品質」を扱う。

---

## サマリー

| Severity | 件数 |
|---|---|
| Blocker | **1** |
| Critical | **5** |
| Major | **10** |
| Minor | **12** |

承認状態: **Needs Revision** (Blocker / Critical 修正後再レビュー必須)

最重要 (Blocker / Critical):
1. **B-1 重複 Bean 定義**: `accountsPayableSummaryInitStep` Bean が `AccountsPayableSummaryConfig` と `AccountsPayableAggregationConfig` の両方で定義されており、Spring Boot 起動時 `BeanDefinitionOverrideException` 必発。`spring.main.allow-bean-definition-overriding=true` で隠れている可能性あり (要 application.yml 確認)。
2. **C-impl-1 旧 ReportTasklet が `verified_manually` 行も `verification_result` / `mfExportEnabled` を踏み潰す** (設計レビュー M-5 とは独立に再発見、3 ループに保護なし)
3. **C-impl-2 `applyMfOverride` 内の `summaryRepository.save` が手動確定 (V026 列) を一切触らない**: 既存 `verifiedAmountTaxExcluded` / `autoAdjustedAmount` / `mfTransferDate` の挙動が未定義 (上書きされない or stale のまま)
4. **C-impl-3 `ConsistencyReviewService` の DB 操作と JPA dirty checking 依存**: `applyMfOverride` 後に `largest` 行の端数吸収を `summaryRepository.save(r)` ループ後に行うが、再 save なしで Hibernate の auto-flush 任せ (`@Transactional` 内なので動作するが意図不明瞭、テスト時の脆弱性源)
5. **C-impl-4 `MfPaymentAggregator.getMfDebitBySupplierForMonth` が `paymentSupplierService.findByShopNo()` を毎月 fetch**: backfill で 12 ヶ月分処理時に最大 12 回 N+1 (cache miss 時 36 ms × 12)
6. **C-impl-5 `SupplierBalancesService.accumulateMfJournals` の Map 二重 lookup バグ**: `map.computeIfAbsent(...).credit = map.get(...).credit.add(...)` パターンで dead-store (`computeIfAbsent` が返した Map value への参照を捨てて再 lookup)。動作はするがロジック明確性を著しく損なう

---

## Blocker

### B-1: `accountsPayableSummaryInitStep` Bean の重複定義
- **場所**:
  - `backend/src/main/java/jp/co/oda32/batch/finance/config/AccountsPayableSummaryConfig.java:83-87`
  - `backend/src/main/java/jp/co/oda32/batch/finance/config/AccountsPayableAggregationConfig.java:66-70`
- **現状**:
  ```java
  // AccountsPayableSummaryConfig.java:83
  @Bean
  public Step accountsPayableSummaryInitStep() {
      return new StepBuilder("accountsPayableSummaryInitStep", jobRepository)
              .tasklet(accountsPayableSummaryInitTasklet, transactionManager)
              .build();
  }

  // AccountsPayableAggregationConfig.java:66
  @Bean
  public Step accountsPayableSummaryInitStep() {     // ← 同名 Bean
      return new StepBuilder("accountsPayableSummaryInitStep", jobRepository)
              .tasklet(accountsPayableSummaryInitTasklet, transactionManager)
              .build();
  }
  ```
- **問題**: 同一 Bean 名 (メソッド名) `accountsPayableSummaryInitStep` を 2 つの `@Configuration` で公開。Spring Boot 2.1+ デフォルトでは `BeanDefinitionOverrideException` で起動失敗。設計レビュー C-2 で「旧 tasklet を deprecation」とされているが、旧 Config を残したままで Bean 名衝突が放置されている。
- **影響**: 起動失敗 or `allow-bean-definition-overriding=true` 設定で片方が静かに上書きされ、誤った Step (旧 tasklet 系) が `accountsPayableAggregationJob` に組み込まれるリスク。後者の場合 Phase B' 経路が壊れていることに気付かない。
- **修正案**:
  1. `AccountsPayableAggregationConfig` で Bean 名を `accountsPayableAggregationInitStep` に rename し、Step 名 (1st arg of `StepBuilder`) も別名にする
  2. もしくは `AccountsPayableSummaryConfig` を `@Deprecated` + `@ConditionalOnProperty` で無効化
  3. `application.yml` に `spring.main.allow-bean-definition-overriding` の指定があれば即座に削除し、起動時にエラーが出る状態に戻す

---

## Critical

### C-impl-1: `AccountsPayableVerificationReportTasklet` が `verified_manually` 行を保護していない (再発見)
- **場所**: `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableVerificationReportTasklet.java:115-122, 130-141, 156-166`
- **現状** (3 箇所すべて):
  ```java
  // L115-122: 差額 null 行
  for (TAccountsPayableSummary summary : supplierSummaries) {
      summary.setVerificationResult(0);
      summary.setMfExportEnabled(false);
      tAccountsPayableSummaryService.save(summary);
      fixedCount++;
  }

  // L131-141: 差額 5 円未満
  if (... < FinanceConstants.PAYMENT_VERIFICATION_TOLERANCE) {
      summary.setVerificationResult(1);
      summary.setMfExportEnabled(true);
      tAccountsPayableSummaryService.save(summary);
      ...
  }

  // L157-166: 検証一致なら mfExportEnabled=true
  if (summary.getVerificationResult() == 1) {
      if (summary.getMfExportEnabled() == null || !summary.getMfExportEnabled()) {
          summary.setMfExportEnabled(true);
          tAccountsPayableSummaryService.save(summary);
          ...
      }
  }
  ```
- **問題**: `SmilePaymentVerifier` (line 155-159, 374-378) と `AccountsPayableSummaryInitTasklet` (line 53-57) は `verified_manually` 行を明示的にスキップしているが、本 ReportTasklet 3 箇所には同等の保護がない。`accountsPayableVerification` ジョブが走った後に `accountsPayableVerificationReport` も走る運用 (`AccountsPayableVerificationConfig.java:62-71` の job 定義) で、手動確定行が `verificationResult` / `mfExportEnabled` を上書きされる。
- **影響**: 経理 (k_oda) が UI から `verify` API 経由で MF 出力を `false` に手動セットしても、次回バッチで `true` に戻され MF へ二重出力される。設計レビュー M-5 と同じ問題だがコード上は独立に再発見しているため Critical で扱う (Major 越え)。
- **修正案**: 3 箇所の `for` ループ先頭に `if (Boolean.TRUE.equals(summary.getVerifiedManually())) continue;` を追加。

### C-impl-2: `ConsistencyReviewService.applyMfOverride` が V026 列 (verifiedAmountTaxExcluded / autoAdjustedAmount / mfTransferDate) の整合性を取らない
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java:173-181, 200-217`
- **現状**:
  ```java
  // selfOnly: 自社取消
  for (TAccountsPayableSummary r : rows) {
      r.setVerifiedAmount(BigDecimal.ZERO);
      r.setVerifiedAmountTaxExcluded(BigDecimal.ZERO);
      r.setVerifiedManually(true);
      r.setVerificationResult(1);
      r.setMfExportEnabled(false);
      summaryRepository.save(r);
  }

  // amountMismatch
  for (TAccountsPayableSummary r : rows) {
      ...
      r.setVerifiedAmount(allocated);
      BigDecimal divisor = BigDecimal.valueOf(100).add(nz(r.getTaxRate()));
      r.setVerifiedAmountTaxExcluded(...);
      r.setVerifiedManually(true);
      r.setVerificationResult(1);
      r.setMfExportEnabled(true);
  }
  ```
- **問題**:
  1. `autoAdjustedAmount` (V026) が更新されない: 振込明細 Excel 取込で記録された自動調整額が古い値のまま残り、UI バッジ (`AccountsPayablePage:140-143` `showAdjBadge`) が誤った金額を表示し続ける。`MF_APPLY` で MF 金額に上書きする以上、自動調整額は意味を失うため `null` or `0` に戻すべき。
  2. `mfTransferDate` (V026) が更新されない: MF_APPLY 後に MF CSV 出力すると古い送金日 (前回 Excel 取込時の値) が使われる。`MF_APPLY` 後は `null` (= transactionMonth fallback) に戻すのが安全。
  3. `paymentDifference` が更新されない: `verifiedAmount` を変えたのに差額が古い値のまま (UI 列で混乱)。
- **影響**: 「整合性レポート画面で MF_APPLY」→「買掛金一覧で当月 detail を見ると、調整バッジ (auto_adjusted_amount) が古い金額表示」「mf_transfer_date が前回 Excel 由来のまま」という UX 不整合。会計監査時に説明不能な差異の元。
- **修正案**: amountMismatch ブロック (line 200-227) と selfOnly ブロック (line 174-182) で以下を追加:
  ```java
  r.setAutoAdjustedAmount(BigDecimal.ZERO);
  r.setMfTransferDate(null);
  r.setPaymentDifference(allocated.subtract(nz(r.getTaxIncludedAmountChange())));
  ```

### C-impl-3: `ConsistencyReviewService.applyMfOverride` の端数吸収後 save 漏れ (設計レビュー m-9 の発展)
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java:200-227`
- **現状**:
  ```java
  for (TAccountsPayableSummary r : rows) { ... }      // line 200: 各行を allocate して save 済
  // 端数誤差は最大行で吸収
  BigDecimal diff = target.subtract(assigned);
  if (diff.signum() != 0 && largest != null) {
      largest.setVerifiedAmount(nz(largest.getVerifiedAmount()).add(diff));
      ...
      largest.setVerifiedAmountTaxExcluded(...);
  }
  for (TAccountsPayableSummary r : rows) summaryRepository.save(r);   // line 227: 全件再 save
  ```
- **問題**:
  - 設計レビュー m-9 は「端数吸収後の `largest` を再 save なしで Hibernate dirty check 任せ」と指摘したが、コード上は `for (... rows) summaryRepository.save(r)` を最後に再ループしているため一見問題なし。
  - **本当の問題**: 同 method 内で `summaryRepository.save(r)` を 2 回呼ぶ (allocation 時 + 最後に再 save)。これは Hibernate の `merge` を 2 回起動し、selectAfterUpdate / autoflush の挙動次第で UPDATE が 2 回発行される。性能ロス + ログ汚染 + (まれに) Optimistic locking 例外の温床。
  - 加えて `for (...rows) {...}` 内の各 `summaryRepository.save(r)` 呼び出しは `r` 自身が既に session 内 managed entity で merge 不要 (Spring Data JPA は managed の場合 `EntityManager.merge` ではなく直接 dirty check)。冗長。
- **影響**: 機能は動くが、再 save が 2 重で UPDATE が 2 回発行される。`@Transactional` 内なので結果は正しいが、log4j で audit trail を取ると 2 回 INSERT/UPDATE のように見える。
- **修正案**:
  1. allocation ループ内の `summaryRepository.save(r)` を削除 (entity は既に managed なので不要)
  2. 端数吸収後に `summaryRepository.saveAll(rows)` で 1 回だけ flush
  3. もしくは `repository.save` 呼び出しを完全削除し、tx commit 時の auto-flush に任せる (JpaRepository の挙動は merge/persist trigger 用なので明示 save が「正解」だが冗長度を下げる場合)

### C-impl-4: `MfPaymentAggregator.getMfDebitBySupplierForMonth` が毎呼び出しで master full-load
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfPaymentAggregator.java:55-128`
- **現状**:
  ```java
  public Map<Integer, BigDecimal> getMfDebitBySupplierForMonth(
          Integer shopNo, LocalDate transactionMonth) {
      ...
      Map<String, Set<String>> subToCodes = buildMfSubToCodes();         // L85: master 全 fetch
      Collection<MPaymentSupplier> suppliers = paymentSupplierService.findByShopNo(shopNo);  // L88: supplier 全 fetch
      ...
  }
  ```
- **問題**:
  - `AccountsPayableBackfillTasklet.processOneMonth` (line 128-130) で 12 ヶ月分回す場合、毎月 `mfAccountMasterRepository.findAll()` (~1000 行) と `paymentSupplierService.findByShopNo(1)` (~100 行) を再 fetch
  - 同様に `AccountsPayableAggregationTasklet.execute` (line 108-111) でも毎回 fetch
  - `MfHealthCheckService` `SupplierBalancesService` `AccountsPayableIntegrityService` で同等の `buildMfSubToCodes()` 重複実装あり (4 箇所)
- **影響**:
  - backfill 12 ヶ月で master 12 回 + supplier 12 回 fetch = 24 回の DB クエリ。各 50ms とすると 1.2s 超
  - 統合テストで N+1 検出器に引っかかる
  - 設計レビュー M-3 と同じ「逆引き map の重複構築」問題
- **修正案**:
  1. `MfAccountMasterReverseIndexService` を新設し `buildMfSubToCodes()` 結果を `@Cacheable("mfSubToCodes")` で共有
  2. もしくは `getMfDebitBySupplierForRange(shopNo, fromMonth, toMonth)` API に切替え、内部で全期間 1 回 fetch + 月単位 group by に変更
  3. `paymentSupplierService.findByShopNo` も同様に呼び出し側で 1 回取得して引数で渡す

### C-impl-5: `SupplierBalancesService.accumulateMfJournals` の `computeIfAbsent` パターン誤用
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:247-253`
- **現状**:
  ```java
  if (cr != null && MF_ACCOUNT_PAYABLE.equals(cr.accountName())
          && cr.subAccountName() != null) {
      map.computeIfAbsent(cr.subAccountName(), k -> new MfAccum()).credit =
              map.get(cr.subAccountName()).credit.add(nz(cr.value()));
  }
  ```
- **問題**:
  - `computeIfAbsent(...)` の戻り値 `MfAccum` を捨てて、すぐに `map.get(...)` で再 lookup。`computeIfAbsent` が無意味化
  - `map.get(...).credit.add(...)` の結果を `map.get(...).credit` ではなく `map.computeIfAbsent(...).credit` に代入しており、書き込み先と読み取り先が**同じインスタンスを指している**ため動作するが、コード可読性 0
  - 誤読 (i.e., 別 instance に書き込んでいる) で「集計が 0 になる」というバグレポートが将来発生する可能性大
- **影響**: パフォーマンス的には Hash lookup を 2 回するため微妙に遅い (~10%)。可読性は破滅的。
- **修正案**:
  ```java
  MfAccum accum = map.computeIfAbsent(cr.subAccountName(), k -> new MfAccum());
  accum.credit = accum.credit.add(nz(cr.value()));
  ```

---

## Major

### M-impl-1: `TAccountsPayableSummaryService.findAll()` が dead code
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java:39-41`
- **現状**:
  ```java
  public List<TAccountsPayableSummary> findAll() {
      return repository.findAll();
  }
  ```
- **問題**: 全 codebase で呼び出しなし (Grep 確認)。残しておくと外部から呼ばれて全件 fetch する温床。
- **修正案**: 削除。

### M-impl-2: `verify` API の `applyVerification` が opening_balance / payment_settled を再計算しない
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java:160-187`
- **現状**: `verify` で `taxIncludedAmount` / `taxExcludedAmount` / `paymentDifference` / `verificationResult` を更新するが、`closing = opening + effectiveChange - payment_settled` の再計算を通らない。
- **問題**: `effectiveChange` 算出式 (`PayableBalanceCalculator.effectiveChangeTaxIncluded`) は手動確定時 `verifiedAmount` 優先のため、verify でセットした金額が次回 closing 計算時に使われる。**ただし current month の `closing_balance_tax_included` カラム自体は更新されない** (`closing` は DTO 層で都度算出のため OK)。
  - **真の問題**: 翌月 `AccountsPayableAggregationTasklet` 実行時の `applyOpenings` (line 137-153) で前月 closing を取得する際、`PayableBalanceCalculator.closingTaxIncluded(p)` を呼ぶ。手動 verify 後、`opening_balance_tax_included` カラム (DB) が古い値のままだが、計算は in-memory で都度行うため**1 回でも翌月集計を回せば伝搬する**。逆に翌月集計を回さないと次月以降の opening が古い。
  - 設計書 `design-phase-b-prime-payment-settled.md` には「verify 後に翌月の `accountsPayableAggregation` を再実行する運用」が前提のはずだが、UI 上の動線がない。
- **影響**: 経理が手動 verify を繰り返した後、翌月集計を実行し忘れると累積残が ±\N ズレる。設計書 §2.2 の「常に上書き」原則と運用手順の乖離。
- **修正案**:
  1. `verify` API 内で「翌月以降の opening を再計算する必要がある」warning toast を返す (Response に `requiresRebackfill: true` 含める)
  2. もしくは `verify` 内で内部的に `AccountsPayableBackfillTasklet.processOneMonth` を呼んで翌月以降を即更新 (REQUIRES_NEW tx)

### M-impl-3: 整合性レポート Controller の例外ハンドリング型がレスポンス DTO と不整合
- **場所**: `backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java:215-234`
- **現状**:
  ```java
  @GetMapping("/accounts-payable/integrity-report")
  public ResponseEntity<?> getIntegrityReport(...) {
      ...
      try {
          IntegrityReportResponse res = accountsPayableIntegrityService.generate(...);
          return ResponseEntity.ok(res);
      } catch (MfReAuthRequiredException e) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
      } catch (...) { ... }
  }
  ```
- **問題**:
  - 戻り型が `ResponseEntity<?>` (raw wildcard)。OpenAPI 自動生成 / TypeScript 型生成が不可能
  - エラー時 `Map.of("message", ...)` を返すため、フロント側 `IntegrityReportResponse` への暗黙キャストでクラッシュリスク
  - 同パターンが `getMfSupplierLedger` (L312)、`getSupplierBalances` (L246) でも 3 箇所重複
- **影響**: 型安全性低下。Controller Advice で統一すべき。
- **修正案**:
  1. `@RestControllerAdvice` で `MfReAuthRequiredException` / `MfScopeInsufficientException` / `IllegalStateException` をハンドル
  2. Controller の戻り型を `ResponseEntity<IntegrityReportResponse>` に絞る
  3. エラー response body 用 `ErrorResponse` DTO を作成

### M-impl-4: `AccountsPayableLedgerService.aggregateMonth` の `autoAdjustedAmount` 平均化ロジックが不正確
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableLedgerService.java:181-184`
- **現状**:
  ```java
  // 税率別複数行で autoAdjusted が重複計上される (applyVerification は全税率行に同額書き込み)。
  // 代表値として 1 行分に戻す: 行数で割る。単一税率なら変わらない。
  BigDecimal autoAdjustedAvg = breakdown.isEmpty()
          ? BigDecimal.ZERO
          : autoAdjusted.divide(BigDecimal.valueOf(breakdown.size()), 0, java.math.RoundingMode.DOWN);
  ```
- **問題**:
  - 設計レビュー m-10 で指摘済の通り、**振込明細経由で全税率行に同額書き込みされる前提**に依存
  - V026 + Phase B' 後の経路 (PaymentMfImportService の applyVerification) で「税率別に異なる auto_adjusted を入れる」変更が将来入ると、この平均化ロジックが破綻 (e.g., 8% 行 +\10、10% 行 -\5 → 平均 \2 と表示)
  - 設計書 `design-accounts-payable-ledger.md` には平均化に関する記述がなく、なぜ平均化するかの根拠が code comment にしかない
- **影響**: 将来 `applyVerification` が税率別に違う調整を行うように変更された場合、買掛帳画面の表示が誤る (即座に bug にはならないが、数値の意味が変わる)
- **修正案**:
  1. `PaymentMfImportService.applyVerification` の挙動を「全税率行に同額」固定とコメントで宣言する
  2. もしくは `LedgerRow.autoAdjustedAmount` を `Map<BigDecimal, BigDecimal>` (税率→額) に変更し UI で税率別表示
  3. 当面は `autoAdjusted.divide(...)` ではなく **first row 採用** (`group.get(0).getAutoAdjustedAmount()`) で「全行同値」前提を明示する

### M-impl-5: `AccountsPayableIntegrityService.processedSubNames` の key 衝突リスク
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java:203, 266, 353`
- **現状**:
  ```java
  Set<String> processedSubNames = new HashSet<>();
  ...
  processedSubNames.add(sn + "|" + month);   // L266
  ...
  if (processedSubNames.contains(subName + "|" + month)) continue;  // L353
  ```
- **問題**:
  - `subName` (sub_account_name) が `|` を含む場合 (例: 「東京|株式会社」) key が衝突
  - MF master の sub_account_name は事実上「企業名」だが、theoretical には任意文字列許容
  - 設計レビュー M-4 で同等指摘済 (但し実装の record 化案は本コードレビューの修正案として独立)
- **影響**: low (現実的な supplier 名で `|` を含むケースは稀) だが、将来 master 取込時に exception で fail-fast すべき
- **修正案**:
  ```java
  record SubNameMonthKey(String subName, LocalDate month) {}
  Set<SubNameMonthKey> processed = new HashSet<>();
  ```

### M-impl-6: `AccountsPayableBackfillTasklet.toSave.contains(po)` が O(N2)
- **場所**: `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableBackfillTasklet.java:136-139`
- **現状**:
  ```java
  List<TAccountsPayableSummary> toSave = new ArrayList<>(current);
  for (TAccountsPayableSummary po : generated) {
      if (!toSave.contains(po)) toSave.add(po);
  }
  ```
- **問題**:
  - `List.contains(Object)` は O(N) で `equals` 比較。`@Data` の Lombok 生成 `equals` は全フィールド比較なので 1 比較自体も重い
  - 当月行が 100 件 / generated が 50 件で 100*50 = 5000 回比較
  - 加えて Entity の `equals` が IdClass フィールド (shopNo / supplierNo / transactionMonth / taxRate) だけでなく全フィールド (verifiedAmount 等) を含むため、in-memory 編集後に「同じ PK だが equals false」で重複追加される可能性大
- **影響**: backfill 12 ヶ月で 100ms オーダーの遅延。さらに「同 PK が 2 件 saveAll」→ Hibernate `EntityExistsException` のリスク
- **修正案**:
  ```java
  Set<String> existingKeys = current.stream()
      .map(PayableMonthlyAggregator::rowKey).collect(toSet());
  for (TAccountsPayableSummary po : generated) {
      if (existingKeys.add(PayableMonthlyAggregator.rowKey(po))) toSave.add(po);
  }
  ```

### M-impl-7: `AccountsPayableAggregationTasklet` の payment-only 上書き判定が説明と挙動で齟齬
- **場所**: `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableAggregationTasklet.java:115-125`
- **現状**:
  ```java
  for (TAccountsPayableSummary po : paymentOnlyRows) {
      String k = PayableMonthlyAggregator.rowKey(po);
      if (!savedRowKeys.contains(k)) {
          allCurrRows.add(po);
          savedRowKeys.add(k);
      }
      // currMap の既存 row を payment-only 上書きした場合は既に allCurrRows に含まれる
  }
  ```
- **問題**:
  - コメントは「currMap の既存 row を payment-only 上書きした場合は既に allCurrRows に含まれる」だが、実際には `generatePaymentOnlyRows` (line 332-345) で `currMap.get(k)` から既存 row を取り出して in-place 編集している。同 row が `allCurrRows` 内にも存在 → `allCurrRows` 経由で `saveAll` される
  - **しかし**: payment-only 行の `verifiedAmount=null` / `verifiedManually=false` セット (line 352-353) が、`allCurrRows` 内の preservedManual (verified_manually=true) 行に当たった場合、**手動確定が剥がされる**
  - 具体的シナリオ: 経理が当月手動 verify 済 supplier (manual=true, change=0) があると、generatePaymentOnlyRows で「当月 change 合計 = 0」と判定されて payment-only として上書きされ、verified_manually=false / verifiedAmount=null になる
- **影響**: 「change=0 かつ 手動確定済 かつ 前月 paid>0」の supplier (= 経理が当月分を 0 円と確定) で手動確定が消える。低頻度シナリオだが発生したら検出困難
- **修正案**:
  - `generatePaymentOnlyRows` 内 (line 332) で `currMap.get(k)` した row が `verifiedManually=true` ならスキップ:
  ```java
  TAccountsPayableSummary row = currMap.get(k);
  if (row != null && Boolean.TRUE.equals(row.getVerifiedManually())) continue;
  ```

### M-impl-8: `accumulateMfJournals` (SupplierBalancesService) と `accumulateMfJournals` (AccountsPayableIntegrityService) のロジック重複
- **場所**:
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:233-258`
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java:142-176`
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfPaymentAggregator.java:97-110`
- **問題**: MF journal の `branches[].creditor/debitor` の「買掛金 sub_account 抽出 + sub_account_name 単位集計」ロジックが 3 service に重複。それぞれ集計粒度 (期間累計 / 月単位 / 当月のみ) と filter (期首仕訳除外有無) が微妙に違うため、リファクタリング時の乖離源。
- **影響**: 「期首仕訳の扱い」を変えたい時、3 箇所修正が必要。設計レビュー M-1 と関連。
- **修正案**: `MfPayableJournalAggregator` (新設) に `aggregate(journals, fromMonth, toMonth, includeOpening)` を集約し、3 service から呼ぶ。

### M-impl-9: `Frontend integrity-report.tsx` の `window.prompt` 使用
- **場所**: `frontend/components/pages/finance/integrity-report.tsx:111`
- **現状**:
  ```tsx
  const note = window.prompt(`${actionLabel}\n備考 (任意):`, '')
  if (note === null) return
  reviewMutation.mutate({ ...args, note })
  ```
- **問題**:
  - `window.prompt` は HTML5 標準だが UX 最悪 (モバイル対応不可、スタイリング不可、validation 不可)
  - 設計書 `design-consistency-review.md` では「note (max 500)」と記述されているが prompt は length 制限不可
  - shadcn/ui の Dialog + Form パターンが既存 (BulkVerifyDialog 等) なのに統一されていない
- **影響**: UX 一貫性の欠如、モバイル UI 破綻
- **修正案**: `ConsistencyReviewDialog.tsx` を新設し、shadcn/ui Dialog + Textarea + 文字数 counter で統一。

### M-impl-10: `Frontend supplier-balances.tsx` の MF 期首日が hardcode
- **場所**: `frontend/components/pages/finance/supplier-balances.tsx:151`
- **現状**:
  ```tsx
  期首 (2025-05-20) ? 基準月 の全 supplier 累積残を自社 / MF で突合。
  ```
- **問題**:
  - 設計書 / バックエンド (SupplierBalancesService.MF_PERIOD_START) と独立に hardcode
  - レスポンス内に `mfStartDate` フィールドがあるのに使われていない (L101 の `gotoLedger` 内では `report.mfStartDate` を使っているが、ヘッダ表示文では hardcode)
  - 設計レビュー C-1 で期首日定義の不整合を指摘済だが、**フロントにも 4 つ目の hardcode** が存在する (B-1 の三重定義に加えて 4 重化)
- **影響**: バックエンド側で期首日を変更しても UI 表示が古い日付のまま。
- **修正案**: ヘッダ表示文を `期首 ({report?.mfStartDate ?? '...'}) ? 基準月 ...` に変更。

---

## Minor

### m-impl-1: `AccountsPayableSummaryInitTasklet` の SQL リテラル中の shop_no=1 が hardcode
- **場所**: `backend/src/main/java/jp/co/oda32/batch/finance/AccountsPayableSummaryInitTasklet.java:53-57`
- **現状**:
  ```java
  String sql = "UPDATE t_accounts_payable_summary " +
          "SET payment_difference = NULL, verification_result = NULL, mf_export_enabled = FALSE " +
          "WHERE transaction_month = ? " +
          "  AND shop_no = 1 " +     // ← hardcode
          "  AND (verified_manually IS NULL OR verified_manually = FALSE)";
  ```
- **問題**: `FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO` を使うべき。設計レビュー C-3 と関連。
- **修正案**:
  ```java
  String sql = "... AND shop_no = ? AND (verified_manually IS NULL OR verified_manually = FALSE)";
  jdbcTemplate.update(sql, java.sql.Date.valueOf(periodEndDate), FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO);
  ```

### m-impl-2: `AccountsPayableLedgerResponse.LedgerRow` の `Closing < 0` 判定で `BigDecimal.signum()` を使うべき
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableLedgerService.java:246`
- **現状**:
  ```java
  if (closing.signum() < 0) { ... }
  ```
- 実は OK。`compareTo` ではなく `signum` を使っている良い例。レビュー観点としては code-base 全体で `compareTo(BigDecimal.ZERO) < 0` が散在している (例: SmilePaymentVerifier line 478) ので、`signum()` 統一を全体ガイドラインに追加推奨。

### m-impl-3: `AccountsPayableLedgerService.detectAnomalies` の `verified.signum() != 0` 判定で 0 円 verify が無視される
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableLedgerService.java:234`
- **現状**:
  ```java
  if (verified.signum() != 0) {
      BigDecimal diff = change.subtract(verified).abs();
      if (diff.compareTo(VERIFY_DIFF_THRESHOLD) > 0) { ... VERIFY_DIFF }
  }
  ```
- **問題**: 経理が「当月分は 0 円で確定 (例: 全部相殺)」と verifiedAmount=0 を入力した場合、anomaly 検出が走らない。change=10000 / verified=0 のケースで `change != verified` だが anomaly 出ない。
- **影響**: low (実運用ではほぼ発生しないが、エッジケース)
- **修正案**: `if (verified != null)` (Optional 化) や `verifiedManually` フラグでガード:
  ```java
  if (Boolean.TRUE.equals(hasVerifiedManually) || verified.signum() != 0) { ... }
  ```

### m-impl-4: `MfPaymentAggregator.getMfDebitBySupplierForMonth` で `break` 後の重複検出ログなし
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfPaymentAggregator.java:117-123`
- **現状**:
  ```java
  for (String code : codes) {
      Integer no = codeToSupplierNo.get(code);
      if (no != null) {
          result.merge(no, e.getValue(), BigDecimal::add);
          break; // 複数 supplier_code が同一 sub_account にマッピングされる稀ケースは先頭採用
      }
  }
  ```
- **問題**: `codes.size() > 1` で複数 supplier_no にヒットする場合、先頭採用で残り無視。warn ログがないため検出困難。
- **影響**: 設計書 §3 で「mf_account_master に複数 supplier_code 登録ケースは想定外」と書いてあれば OK だが、検出ログだけでも残すべき
- **修正案**:
  ```java
  if (codes.size() > 1) {
      log.warn("[mf-payment] sub_account {} に複数 supplier_code: {} (先頭採用)", e.getKey(), codes);
  }
  ```

### m-impl-5: `AccountsPayableResponse.from(...)` の overload 3 つに対し `@Deprecated` 整理がない
- **場所**: `backend/src/main/java/jp/co/oda32/dto/finance/AccountsPayableResponse.java:78-91`
- **現状**: `from(ap, ps)`, `from(ap)`, `from(ap, ps, includeBalance)` の 3 つ。最初 2 つは 3rd version への delegate。
- **問題**: 整合性は取れているが、呼び出し側 (FinanceController L122, 145, 158, 172) で 2 つ目を使っており、長期的には `from(ap, ps, false)` に統一して overload を削減推奨。
- **修正案**: `@Deprecated` を 2-arg / 1-arg overload に付与し、新規呼び出し禁止のシグナル化。

### m-impl-6: `Frontend accounts-payable.tsx` の `purchaseDateRange` ロジックが Date オブジェクト経由で TZ 依存リスク
- **場所**: `frontend/components/pages/finance/accounts-payable.tsx:74-81`
- **現状**:
  ```tsx
  const to = new Date(y, m - 1, d)       // 当月20日
  const from = new Date(y, m - 2, d + 1) // 前月21日
  const fmt = (x: Date) => `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`
  ```
- **問題**: コメントに「local TZ で生成し UTC 変換は挟まないので TZ 非依存」とあるが、`new Date(y, m-1, d)` 自体は local TZ で生成されるため、サーバーが UTC で動く環境 (Vercel) で `getMonth()` / `getDate()` が UTC ではなく local 解釈で返るのが期待動作。実際は問題ないが、`m-2` の月跨ぎ (例: m=1 → m-2 = -1) で前年 12 月にロールオーバーするのは Date コンストラクタの仕様で OK。
- **影響**: low (現状は正しく動作するが、`m=1, d=20` 起算で `from = new Date(y, -1, 21)` の挙動を頼っており、未来の Date 仕様変更で破綻リスク)
- **修正案**: `date-fns` の `subMonths` + `startOfDay` を使う:
  ```tsx
  import { subMonths, format } from 'date-fns'
  const to = new Date(`${transactionMonth}T00:00:00`)
  const from = subMonths(to, 1)
  // から from を 21 日に...
  ```

### m-impl-7: `Frontend integrity-report.tsx` の `runMutation.mutate(false)` を `onSuccess` で 2 度呼ぶ
- **場所**: `frontend/components/pages/finance/integrity-report.tsx:88, 105`
- **現状**: review 保存 / delete の `onSuccess` 内で `runMutation.mutate(false)` を呼んでいる。これは「review 操作後に整合性レポートを再 fetch」用だが、TanStack Query の `invalidateQueries` で済む。
- **問題**:
  - mutation chain は anti-pattern
  - `runMutation.isPending` が一時的に `false` (chain 中の race condition)、UI で「整合性チェック中」のスピナーが消える瞬間がある
- **修正案**: `useMutation` の `onSuccess` で `queryClient.invalidateQueries({ queryKey: ['integrity-report'] })` し、整合性レポート取得を `useQuery` に変える。

### m-impl-8: `Frontend supplier-balances.tsx` の row key が脆い
- **場所**: `frontend/components/pages/finance/supplier-balances.tsx:208`
- **現状**:
  ```tsx
  <tr key={`${r.supplierNo ?? 'null'}-${r.mfSubAccountNames[0] ?? ''}`}
  ```
- **問題**:
  - `mfSubAccountNames[0]` 依存。同じ supplier_no で 2 行返る (例: SELF_MISSING + 通常) ケースで衝突
  - `mfSubAccountNames` が空配列の場合 `[0]` は `undefined`、key 重複で React warning
- **修正案**:
  ```tsx
  <tr key={`${r.supplierNo ?? 'null'}-${r.status}-${r.mfSubAccountNames.join('|')}`}>
  ```

### m-impl-9: `Frontend accounts-payable.tsx` の BatchButton で `data-testid` がない
- **場所**: `frontend/components/pages/finance/accounts-payable.tsx:96-124`
- **問題**: E2E テストで再集計ボタンを特定する際、`getByText('再集計')` で当てる必要があり、文言変更で fragility 高い。
- **修正案**: `data-testid={\`batch-button-${job}\`}` を追加。

### m-impl-10: `frontend types/accounts-payable.ts` の `BalanceFilter` enum と `BALANCE_FILTER_LABELS` が同一ファイルでなく分散
- **場所**: import statement L37-43
- **問題**: `BalanceFilter` `VerificationFilter` が type alias の場合、union 型を切り取って label map と分離している。命名上は合うが、追加時にどちらかを忘れるリスク。
- **修正案**: `as const` const assertion + `keyof typeof` で型導出に統一:
  ```ts
  export const BALANCE_FILTER_LABELS = { all: '全て', positive: '残あり', negative: '値引繰越' } as const
  export type BalanceFilter = keyof typeof BALANCE_FILTER_LABELS
  ```

### m-impl-11: `ConsistencyReviewService.upsert` の `existingOpt.ifPresent(...)` 内で `req.shopNo` を closure キャプチャ
- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java:62-68`
- **問題**: lambda 内で `req` を closure キャプチャ。読みやすさ低下。
- **修正案**: 通常の `if (existingOpt.isPresent())` ブロックに変更すれば `req` を直接参照しているのが明確。

### m-impl-12: V025-V029 migration ファイル名規約の連番が機能名で分離されていない
- **場所**: `backend/src/main/resources/db/migration/`
- **設計レビュー m-12 と同等**だが、コード視点で追記:
  - `V025__opening_balance_columns.sql` `V026__verified_amount_tax_excluded.sql` `V027__t_consistency_review.sql` `V028__t_payment_mf_aux_row.sql` `V029__delivery_code_mapping_fix.sql` (推測)
  - 並び順 OK だが、別ブランチ (B-CART pending changes 等) で V030+ が衝突する温床
- **修正案**: Flyway 5+ の Repeatable migration (`R__`) を活用 or branch ごとに `V025_01_` `V025_02_` といったサブ番号運用へ移行 (チーム規約として `claudedocs/migration-naming.md` に明文化)

---

## Spring Boot 観点チェックリスト

| 項目 | 結果 | 備考 |
|---|---|---|
| Layer 違反 (Controller→Repository 直接) | OK | Controller→Service→Repository |
| @Transactional 配置 | 概ね OK | ConsistencyReviewService class-level write (line 42) + readOnly (L104 で findForPeriod) は適切 |
| AccountsPayableBackfillTasklet の REQUIRES_NEW tx | OK | `TransactionTemplate` で self-invocation 制約回避 (line 73-75) |
| N+1 | 部分的 NG | C-impl-4 master/supplier 毎月 fetch、設計レビュー M-2 の `summary()` 全件 fetch |
| バリデーション | 部分的 NG | `AccountsPayableVerifyRequest` `@PositiveOrZero` 不足 (設計レビュー m-3)、`ConsistencyReviewRequest` は `@NotBlank` + `@Size(max=500)` で OK |
| Migration 安全性 | OK | V025-V027 metadata-only 中心 |
| DTO Entity 漏洩 | OK | 全 endpoint で `Response.from()` 経由 |
| @PreAuthorize | 部分的 OK | `hasRole('ADMIN')` の使い分け (mf-export 系は逆に admin 限定外、要設計確認) |
| BigDecimal compareTo vs equals | OK | `compareTo(BigDecimal.ZERO) == 0` / `signum()` 適切に使い分け、`equals` 誤用なし |
| Bean 重複 | NG | B-1 |
| TX境界の bulk save | 部分的 NG | C-impl-3 二重 save |

## Next.js / React 観点チェックリスト

| 項目 | 結果 | 備考 |
|---|---|---|
| use(params) (Next.js 16 async params) | N/A | dynamic route なし |
| URL 永続化 | OK | accounts-payable.tsx の `?tab=` (L188-200)、ledger の committed pattern (L64-71) |
| TanStack Query invalidate | 部分的 NG | m-impl-7 mutation chain anti-pattern |
| key の安定性 | NG | m-impl-8 supplier-balances row key |
| useMemo 依存配列 | OK | accounts-payable.tsx L216 の queryString は適切 |
| useEffect cleanup | OK | polling cleanup (L319-323) 適切 |
| sonner toast | OK | 統一されている |
| shadcn/ui コンポーネント | OK | 統一 |
| window.prompt 使用 | NG | M-impl-9 |
| client component "use client" | OK | 全 page で適切 |

## Accounts Payable 固有観点

| 項目 | 結果 | 備考 |
|---|---|---|
| BigDecimal scale | OK | `setScale(0, RoundingMode.DOWN)` 一貫適用 |
| supplier_no=303 除外の一貫性 | NG | 設計レビュー C-3 (集計のみ適用) |
| バッチ再実行時の冪等性 | 概ね OK | InitTasklet で reset → AggregationTasklet で stale-delete 流れ。但し M-impl-7 の手動確定保護に穴 |
| 旧 SummaryTasklet 残存 | NG | 設計レビュー C-2 + 本レビュー B-1 |
| ConsistencyReview ロールバック | NG | 設計レビュー C-4、本レビュー C-impl-2 (V026 列の整合性) |
| Cache invalidate タイミング | OK | `MfJournalCacheService.invalidateAll(shopNo)` 提供、UI から呼べる |
| bulkVerify トランザクション境界 | OK | `PaymentMfImportService.applyVerification` で `@Transactional` 確認済 (本レビュー対象外) |
| Tasklet 内 State 持ち回し | OK | chunk 不使用 (Tasklet) なので部分失敗時のロールバック単位が tasklet 全体で明確 |

---

## 推奨対応優先度

| Priority | ID | 内容 | 工数目安 |
|---|---|---|---|
| 1 | **B-1** | accountsPayableSummaryInitStep Bean 衝突解消 | 30 分 |
| 2 | **C-impl-1** | ReportTasklet の verified_manually スキップ追加 | 1 時間 |
| 3 | **C-impl-2** | applyMfOverride で V026 列リセット | 2 時間 (テスト含) |
| 4 | C-impl-3 | save 二重呼び出し整理 | 1 時間 |
| 5 | C-impl-5 | accumulateMfJournals computeIfAbsent 修正 | 30 分 |
| 6 | M-impl-7 | payment-only 上書きで手動確定保護 | 1 時間 |
| 7 | M-impl-9 | window.prompt → Dialog 化 | 3 時間 |
| 8 | C-impl-4 | master/supplier 共通キャッシュ化 | 4 時間 |
| 9 | M-impl-3 | Controller exception を Advice 化 | 2 時間 |
| 10 | M-impl-6 | toSave.contains O(N2) → Set 化 | 30 分 |

## 終わりに

Cluster D は Phase A → B → B' → 案 A → 整合性 → consistency-review → 累積残 → ヘルスチェック と短期間に層を重ねた結果、**コード上の重複定義 (B-1) / 旧 ReportTasklet の保護漏れ (C-impl-1) / V026 列の整合性 (C-impl-2)** が露呈している。設計レビューで指摘された「期首日の三重定義」「supplier_no=303 除外」「旧 tasklet 残存」と組み合わせて、Critical 9 件 (設計 4 + コード 5) を 1 sprint で解消することを推奨。

Blocker 1 件 (B-1) は **本番起動失敗 or 静かな旧経路上書き** のため最優先。本番環境の `application.yml` に `spring.main.allow-bean-definition-overriding=true` がある場合、現在は問題なく動作しているように見えるが、accountsPayableAggregationJob の Init step が旧 Config 経由で組まれているか新 Config 経由か未確認のため、テスト環境で起動ログ確認 + Bean 走査を運用フェーズに入る前に必ず実施。

V026 列 (autoAdjustedAmount, mfTransferDate, verifiedAmountTaxExcluded) と consistency-review の整合性は、会計監査の「説明責任」という観点で今後の業務拡大時に重要となるため、C-impl-2 は数値以上の意味を持つ。

実運用フェーズ (2026-04-23?) の業務フィードバックと並行して、本レビュー Critical を 2 sprint 以内に解消し、設計書 6 本 + コードを「現在地」に再同期することで、Cluster D は安定運用フェーズに移行できる見込み。
The specified drive root "C:\Users\kazuki\AppData\Local\Temp\" either does not exist, or it is not a folder.
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

2026-05-04T01:37:23.747883Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 3.9 seconds
Output:
The specified drive root "C:\Users\kazuki\AppData\Local\Temp\" either does not exist, or it is not a folder.
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg -n "20日|20|303|shop_no|tax|税率|partial|部分|audit|監査|MF_APPLY|IGNORE| …
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 exited 1 in 3878ms:
The specified drive root "C:\Users\kazuki\AppData\Local\Temp\" either does not exist, or it is not a folder.
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg -n "20日|20|303|shop_no|tax|税率|partial|部分|audit|監査|MF_APPLY|IGNORE| …
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 exited 1 in 3886ms:
The specified drive root "C:\Users\kazuki\AppData\Local\Temp\" either does not exist, or it is not a folder.
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg --files claudedocs | rg "accounts-payable|payable|supplier|integri 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

2026-05-04T01:37:23.749168Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 3.9 seconds
Output:
The specified drive root "C:\Users\kazuki\AppData\Local\Temp\" either does not exist, or it is not a folder.
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg --files claudedocs | rg "accounts-payable|payable|supplier|integri 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

codex
`rg` がこの環境では使えないため、PowerShell の `Get-ChildItem` / `Select-String` で続けます。Opus 側は既に期首日、303 除外、旧 Tasklet、ConsistencyReview のロールバック、手動確定保護、Bean 衝突などをかなり広く扱っているので、そこを外して業務モデルと失敗モードを掘ります。
2026-05-04T01:37:32.941668Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem claudedocs -Filter *.md | Select-String -Pattern 'accounts-payable|payable|supplier|integrity|consistency|買掛|支払|累積|期首|MF_APPLY|IGNORE|部分|監査|audit|訂正|返品|値引|shop_no|303' | Select-Object Path,LineNumber,Line"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem claudedocs -Filter *.md | Select-String -Pattern 'accounts-payable|payable|supplier|integrity|consistency|買掛|支払|累積|期首|MF_APPLY|IGNORE|部分|監査|audit|訂正|返品|値引|shop_no|303' | Select-Object Path,LineNumber,Line" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem claudedocs -Filter *.md | Select-String -Pattern 'accounts-payable|payable|supplier|integrity|consistency|買掛|支払|累積|期首|MF_APPLY|IGNORE|部分|監査|audit|訂正|返品|値引|shop_no|303' | Select-Object Path,LineNumber,Line"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content claudedocs\\design-accounts-payable.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content claudedocs\\design-accounts-payable-ledger.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content claudedocs\\design-integrity-report.md" in C:\project\odamitsu-data-hub
 succeeded in 1748ms:
# 買掛金一覧画面 設計書

作成日: 2026-04-14
対象ブランチ: feat/bcart-shipping-input（派生予定: feat/accounts-payable）
旧システム参照: `C:\project\stock-app\src\main\resources\templates\finance\accountsPayable\`

## 1. 目的と業務上の意味

日々の仕入データ（`t_purchase` / `t_purchase_detail`）から、各**支払先**（`m_payment_supplier`）への**買掛帳**を月次で集計・更新し、支払先から届いた**請求書の金額と突合**して一致/不一致を確認する画面。

### 突合キー（支払先単位）
- `m_supplier`（商品ごとの仕入先）と `m_payment_supplier`（実際の支払先）は多:1 で紐づく（`m_supplier.payment_supplier_no`）
- 買掛は支払先単位で合算して `t_accounts_payable_summary` に格納（集計バッチが親子を解決）
- `t_accounts_payable_summary.supplier_no` / `supplier_code` は **実質 `payment_supplier_no` / `payment_supplier_code`**（カラム名は既存DBスキーマ踏襲）
- 請求書も支払先単位で届くため、突合キーは支払先コード
- 画面の「仕入先」列・プルダウンはすべて支払先を指す（Frontend は `usePaymentSuppliers` = `/masters/payment-suppliers` を使用）

- 買掛帳 = 「その月に、この仕入先にいくら払うか」を確定させる社内台帳
- 突合対象 = 仕入先から送られてくる請求書の金額（現状は SMILE 基幹に登録された支払データ `t_smile_payment` と照合）
- 不一致があれば経理担当者が原因を調査し、検証済み金額を手入力で確定 → マネーフォワードへ仕訳連携

現状の画面（`frontend/components/pages/finance/accounts-payable.tsx`）はページネーション一覧のみで、**業務上必要な「突合状況の把握」「検証入力」「再集計トリガ」が欠落**している。本設計でこれらを補う。

## 2. スコープ

### 本設計に含むもの
- 買掛金一覧画面の再設計（月次ビュー、検証ステータス表示、未検証/不一致件数サマリ）
- 詳細/検証入力ダイアログ（または詳細ページ）
- 必要な API の追加・変更
- 再集計バッチ起動ボタン（月次の買掛帳更新）

### 含まないもの
- バッチ本体のロジック変更（`AccountsPayableSummaryTasklet` / `AccountsPayableVerificationTasklet` は既存のまま）
- マネーフォワード連携仕訳出力（別画面 `/finance/cashbook-import` 系の責務）
- 売掛金（`t_invoice`）の機能

## 3. 現状分析

### 既存アセット（再利用）

**バックエンド**
- Entity: `TAccountsPayableSummary` (PK: `shopNo`, `supplierNo`, `transactionMonth`, `taxRate`)
  - `taxIncludedAmountChange` / `taxExcludedAmountChange` = 社内買掛金額（仕入データから算出）
  - `taxIncludedAmount` / `taxExcludedAmount` = 検証済み支払額（SMILE / 手入力）
  - `paymentDifference` = 差額
  - `verificationResult` = 1:一致 / 0:不一致 / null:未検証
  - `mfExportEnabled` = MF 連携可否
- Service: `TAccountsPayableSummaryService`（`findPaged` / `getByPK` / `save` / `findByTransactionMonth` あり）
- Repository: `TAccountsPayableSummaryRepository`
- Controller: `FinanceController#listAccountsPayable`（一覧 GET のみ）
- バッチ:
  - `AccountsPayableSummaryJob` ? `t_purchase_detail` 集計 → `t_accounts_payable_summary` UPSERT
  - `AccountsPayableVerificationJob` ? `t_smile_payment` と突合 → `verificationResult` / `paymentDifference` 更新
  - `AccountsPayableAggregationJob` / `PurchaseJournalIntegrationJob` ? MF 連携用

**フロントエンド**
- 既存の `accounts-payable.tsx`: DataTable + ページネーション + `formatCurrency`
- 共通: `PageHeader`, `SearchableSelect`, `Dialog`, `Badge`, `sonner`, `TanStack Query`

### ギャップ

| # | 項目 | 現状 | あるべき姿 |
|---|---|---|---|
| G1 | 取引月の指定 | 不可（全期間が混ざる） | `month` picker で指定、デフォルト=当月20日締め |
| G2 | 検証ステータス表示 | 未表示 | Badge（一致/不一致/未検証） + 差額の色分け |
| G3 | 突合サマリ | 無し | 未検証件数・不一致件数のアラート |
| G4 | 仕入先名 | `supplierName` を DTO に含んでいる前提だが、Response に無い | `supplierName` / `supplierCode` を `AccountsPayableResponse` に追加 |
| G5 | 支払額手動入力 | 無し | 詳細ダイアログで検証済み金額＆備考入力 → PUT |
| G6 | 再集計・再検証トリガ | 無し | 2 つのバッチ起動ボタン（管理者のみ） |
| G7 | MF 出力可否切替 | 無し | `mfExportEnabled` の ON/OFF トグル |
| G8 | 一覧 API の絞り込み | `shopNo`, `supplierNo` のみ | `transactionMonth`, `verificationResult` を追加 |

## 4. 画面設計

### 4.1 ルート
- 一覧: `/finance/accounts-payable`
- 既存 `app/(authenticated)/finance/accounts-payable/page.tsx` を再利用
- 詳細はダイアログ（モーダル）で表示。別ルートは切らない（URL 共有性より、一覧スクロール状態保持を優先）

### 4.2 ページレイアウト

```
┌──────────────────────────────────────────────────────────┐
│ 買掛金一覧                          [再集計] [再検証]      │ PageHeader + actions
├──────────────────────────────────────────────────────────┤
│ 取引月 [2026-03 ▼] 仕入先 [すべて ▼] 検証 [すべて/未検証/不一致] [検索] │
├──────────────────────────────────────────────────────────┤
│ ? 未検証 5件 / 不一致 2件（差額合計 -3,240円）                │ サマリアラート
├──────────────────────────────────────────────────────────┤
│ 仕入先Code 仕入先名      税率  買掛(税込) SMILE支払 差額   状態   MF  操作  │
│ 0001      ○○商事      10%   1,234,000 1,234,000   0   [一致] ?  [詳細]│
│ 0002      △△工業      10%     543,210   540,000  3,210 [不一致]?  [詳細]│
│ 0003      □□商店       8%     108,000      null  null  [未検証]?  [検証]│
│ …                                                                        │
├──────────────────────────────────────────────────────────┤
│ ページネーション                                                          │
└──────────────────────────────────────────────────────────┘
```

### 4.3 検索条件

| 項目 | 型 | 必須 | デフォルト |
|---|---|---|---|
| 取引月 | `<input type="month">` | ○ | 当月20日（20日未満なら前月20日） |
| 仕入先 | `SearchableSelect` | - | すべて |
| 検証ステータス | `Select` (all/unverified/unmatched/matched) | - | すべて |

admin (`shopNo=0`) の場合はショップセレクトも表示（既存ルールに従う）。

### 4.4 サマリアラート

取引月内で集計:
- 未検証件数（`verificationResult IS NULL`）
- 不一致件数（`verificationResult = 0`）
- 不一致の差額合計

未検証 or 不一致が 0 件以上ならオレンジ（警告）系 Alert で表示。

### 4.5 一覧カラム

| カラム | ソート | 備考 |
|---|---|---|
| 仕入先コード | ○ | `supplierCode`（支払先 = `MPaymentSupplier`）。デフォルトソート ASC |
| 仕入先名 | ○ | `supplierName` |
| 税率 | - | `10%` / `8%` |
| 税抜 / 消費税 | - | 1列2段表示（上:税抜通常、下:消費税薄色小文字） |
| 振込明細額 | - | `verifiedAmount`（振込明細Excel or 手動検証で入力された税込請求額）、null は「-」 |
| SMILE支払額 | - | `taxIncludedAmount`、null は「-」 |
| 差額 | - | `paymentDifference`、不一致は赤 |
| 検証状態 | - | Badge: 一致(緑) / 不一致(赤) / 未検証(グレー)。`verified_manually=true` なら「手動」サブバッジ付与 |
| MF出力 | - | Switch: on/off（`mfExportEnabled`） |
| 操作 | - | 未検証→「検証」／検証済→「詳細」ボタン |

サーバサイドページネーション（既存 `Paginated<T>` 再利用）。1ページ 50件。
デフォルトソート: `supplierCode ASC`（`PageableDefault` をバックエンド Controller に設定）。

### 4.6 詳細 / 検証ダイアログ

クリックで Dialog を開く。以下を表示・編集:

**表示（読取専用）**
- 仕入先コード / 仕入先名
- 取引月 / 税率
- 買掛金額（税込 / 税抜）
- SMILE 支払額（税込 / 税抜）
- 差額
- 前回備考（`verification_note`、あれば）
- 手動確定状態 Badge（`verified_manually = true` のとき「手動確定済」緑表示）

**入力**
- 検証済み支払額（税込） `number`（初期値: `taxIncludedAmount ?? taxIncludedAmountChange`）
- 備考 `textarea`（500字、DB永続化）

**ボタン**
- 「更新」: PUT → 一致判定（差額 ? 100円 で一致） → `verified_manually=true` をセット → 一覧 refetch、toast
- 「手動確定解除」: admin のみ表示。`verified_manually=false` に戻し、次回 SMILE 再検証バッチで上書き可能にする
- 「キャンセル」

判定ロジックは旧 stock-app と同じ（税抜金額を税率から逆算、差額 abs ? 100 で一致）。

### 4.7 アクションボタン

`PageHeader` の actions に 3 つ（admin のみ表示）:
- **仕入明細取込(SMILE)**: `POST /api/v1/batch/execute/purchaseFileImport`
  - `m_shop_linked_file` 登録の全 CSV を取込（`purchase_import.csv` + `purchase_import2_*.csv`）
  - `inputFile` / `shopNo` / `targetDate` パラメータ不要
- **再集計**: `POST /api/v1/batch/execute/accountsPayableAggregation?targetDate=yyyyMMdd`
  - 集計のみの軽量ジョブ（従来の `accountsPayableSummary` から変更）。SMILE支払取込・検証は含まない
  - Init ステップ（検証フラグリセット）→ 集計ステップの2ステップ構成
- **再検証(SMILE)**: `POST /api/v1/batch/execute/accountsPayableVerification?targetDate=yyyyMMdd`
  - SMILE支払取込ステップを含むため `inputFile` 自動付与（`input/smile_payment_import.csv`）
  - `verified_manually = true` の行は突合対象からスキップされる

#### バッチ起動状態の可視化

ボタン押下後:
1. 3秒後にステータスポーリングを開始（`GET /api/v1/batch/status/{jobName}`、5秒間隔）
2. 実行中: `Loader2` スピナー + ボタン disabled
3. 完了: `CheckCircle2` 緑。一覧を自動再取得（TanStack Query invalidate）
4. FAILED: `XCircle` 赤 + `exitMessage` をエラートーストで表示

バッチエンドポイントは既存 `BatchController#execute/{jobName}` を流用。
多重実行時は `ThreadPoolTaskExecutor` + `AbortPolicy` により 429 Too Many Requests が返り、
フロントで「他のバッチが実行中」トーストを表示。

## 5. API 設計

### 5.1 一覧取得（変更）

`GET /api/v1/finance/accounts-payable`

| パラメータ | 型 | 必須 | 説明 |
|---|---|---|---|
| `shopNo` | Integer | - | admin のみ指定可 |
| `transactionMonth` | `yyyy-MM-dd` | - | 未指定時は当月20日 |
| `supplierNo` | Integer | - | 支払先 |
| `verificationResult` | `null\|0\|1\|all` | - | `unverified`/`unmatched`/`matched`/`all` |
| `page` / `size` / `sort` | - | - | 既存 |

`AccountsPayableResponse` に以下を追加:
- `supplierCode: String`
- `supplierName: String`
- `verificationResult: Integer` (既存 Entity には有るが DTO に無い)
- `mfExportEnabled: Boolean`

### 5.2 サマリ取得（新規）

`GET /api/v1/finance/accounts-payable/summary?transactionMonth=yyyy-MM-dd`

Response:
```json
{
  "transactionMonth": "2026-03-20",
  "totalCount": 42,
  "unverifiedCount": 5,
  "unmatchedCount": 2,
  "unmatchedDifferenceSum": -3240
}
```

### 5.3 検証更新（新規）

`PUT /api/v1/finance/accounts-payable/{shopNo}/{supplierNo}/{transactionMonth}/{taxRate}/verify`

Request:
```json
{
  "verifiedAmount": 1234000,
  "note": "請求書No.A-001で確認"
}
```

Response: 更新後の `AccountsPayableResponse`。
- 差額・`taxExcludedAmount`・`verificationResult` をサーバ側で算出（旧ロジック踏襲、閾値100円）
- `verification_note` に保存、`verified_manually=true` をセット
- `@Transactional`

### 5.3.1 手動確定解除（新規）

`DELETE /api/v1/finance/accounts-payable/{shopNo}/{supplierNo}/{transactionMonth}/{taxRate}/manual-lock`

- admin のみ (`hasRole('ADMIN')`)
- `verified_manually = false` に戻す（次回 SMILE 再検証バッチの上書き対象に戻る）
- `verification_note` / `taxIncludedAmount` などの値は保持（必要なら手動で `/verify` をやり直す）

### 5.4 MF出力可否トグル（新規）

`PATCH /api/v1/finance/accounts-payable/{shopNo}/{supplierNo}/{transactionMonth}/{taxRate}/mf-export`

Request: `{ "enabled": true }`

### 5.5 再集計 / 再検証

既存 `BatchController`（`api/batch/BatchController.java`）の汎用バッチ起動基盤を再利用する。専用エンドポイントは設ける必要がない。

- `POST /api/v1/batch/execute/accountsPayableSummary` ? 買掛金集計バッチ起動
- `POST /api/v1/batch/execute/accountsPayableVerification` ? SMILE再検証バッチ起動

方針:
- 既存実装で `@PreAuthorize("hasRole('ADMIN')")` 済み
- **非同期実行**: `ThreadPoolTaskExecutor`（corePool=1, maxPool=2, queue=5）で即座に `202 Accepted` を返す
- **多重実行ガード**: `RejectedExecutionHandler.AbortPolicy` によりキュー溢れ時に `429 Too Many Requests` + 「同時実行数の上限に達しています」メッセージ
- ステータス確認: `GET /api/v1/batch/status/{jobName}` で最新実行の `COMPLETED`/`FAILED` を取得可能
- フロントは 429 を ApiError として捕捉し、toast で「他のバッチが実行中」メッセージを表示

## 6. バックエンド変更点

### 6.1 DB スキーマ（Liquibase）

`t_accounts_payable_summary` に 3 カラム追加:

```sql
ALTER TABLE t_accounts_payable_summary
  ADD COLUMN verified_manually BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN verification_note VARCHAR(500),
  ADD COLUMN verified_amount   NUMERIC;
```

`TAccountsPayableSummary` Entity に対応フィールド追加:
- `@Column(name = "verified_manually", nullable = false) @ColumnDefault("false") private Boolean verifiedManually;`
- `@Column(name = "verification_note") private String verificationNote;`
- `@Column(name = "verified_amount") private BigDecimal verifiedAmount;`

**`verifiedAmount` の用途**:
- 振込明細Excel / 手動検証で提示された税込請求額を保持する
- `tax_included_amount`（MF出力用スナップショット）とは独立管理
- 再集計バッチでは上書きされない（手動/Excel検証の結果を保持するため）
- 100円閾値一致判定は `verifiedAmount` と `taxIncludedAmountChange` を比較
- 手動検証 (`TAccountsPayableSummaryService#applyVerification`) と
  振込明細MF一括検証 (`PaymentMfImportService#applyVerification`) の
  どちらも同一カラムに書込（税率別の複数行には同一値を同期セット）

### 6.2 DTO
- `AccountsPayableResponse` に `supplierCode`, `supplierName`, `verificationResult`, `mfExportEnabled`, `verifiedManually`, `verificationNote` 追加
  - `MSupplier` は `paymentSupplier` 経由で取得（`supplier_code` と `supplier_name` は `MPaymentSupplier` 側）
  - 一覧取得時に N+1 を避けるため、Service で supplier をバルク fetch して Map 渡し
- 新規 `AccountsPayableVerifyRequest`（`verifiedAmount`, `note` 最大500字）
- 新規 `AccountsPayableSummaryResponse`（5.2節）
- 新規 `AccountsPayableRecalcRequest` / `AccountsPayableReverifyRequest`

### 6.3 Service
`TAccountsPayableSummaryService` に以下を追加:
- `findPaged(shopNo, supplierNo, transactionMonth, verificationResult, pageable)` ? Specification を拡張
- `summary(shopNo, transactionMonth)` ? カウントと差額合計
- `verify(pk, verifiedAmount, note)` ? 差額計算＆判定 + `verifiedManually=true` + `verificationNote` 保存
- `releaseManualLock(pk)` ? `verifiedManually=false`
- `updateMfExport(pk, enabled)`

### 6.4 Controller
`FinanceController` に 6 エンドポイント追加（一覧は既存を拡張）。再集計/再検証は `BatchController` に逃がす選択肢もあるが、経理ドメインの画面操作なので `FinanceController` に置く。同期起動時の多重実行ガードも Service 層で実装。

### 6.5 バッチ呼び出し & 手入力保護
再集計/再検証は既存 `BatchController#execute(jobName)` を流用。専用 Controller 追加なし。
- ジョブ Bean 名: `accountsPayableSummaryJob`, `accountsPayableVerificationJob`（既存）
- 多重実行ガード: `ThreadPoolTaskExecutor` + `AbortPolicy` → 429 Too Many Requests（既存）

**`SmilePaymentVerifier` の改修**:
- SMILE 支払額との突合対象を取得する際、`verified_manually = true` の行は**スキップ**
- スキップ件数は処理ログに `INFO` で出力（運用時の確認用）
- 既存の `AccountsPayableSummaryTasklet` 側は `taxIncludedAmountChange` / `taxExcludedAmountChange` のみ更新するため、手入力保護と独立（集計値は常に最新）

## 7. フロントエンド変更点

### 7.1 ファイル構成
```
frontend/
├── app/(authenticated)/finance/accounts-payable/page.tsx      (既存、変更なし)
├── components/pages/finance/
│   ├── accounts-payable.tsx                                    (リファクタ)
│   └── accounts-payable/
│       ├── SearchForm.tsx
│       ├── SummaryAlert.tsx
│       ├── VerifyDialog.tsx
│       └── ActionButtons.tsx
└── types/accounts-payable.ts                                   (新規)
```

### 7.2 主要型
```ts
// types/accounts-payable.ts
export interface AccountsPayable {
  shopNo: number
  supplierNo: number
  supplierCode: string
  supplierName: string
  transactionMonth: string          // yyyy-MM-dd
  taxRate: number
  taxIncludedAmountChange: number
  taxExcludedAmountChange: number
  taxIncludedAmount: number | null  // SMILE 支払額（MF出力スナップショット）
  taxExcludedAmount: number | null
  verifiedAmount: number | null     // 振込明細/手入力の税込請求額（再集計で上書きされない）
  paymentDifference: number | null
  verificationResult: 0 | 1 | null
  mfExportEnabled: boolean
  verifiedManually: boolean
  verificationNote: string | null
}

export interface AccountsPayableSummary {
  transactionMonth: string
  totalCount: number
  unverifiedCount: number
  unmatchedCount: number
  unmatchedDifferenceSum: number
}

export const VERIFICATION_FILTER = {
  all: 'すべて',
  unverified: '未検証',
  unmatched: '不一致',
  matched: '一致',
} as const
```

### 7.3 TanStack Query
- `['accounts-payable', params]`: 一覧
- `['accounts-payable-summary', transactionMonth, shopNo]`: サマリ（並列）
- 変更系 mutation は `invalidateQueries` で両方 refetch

### 7.4 初期検索制御
`searchParams` を `null` 初期化せず、デフォルト（当月20日）をクライアント側で計算して即検索。
理由: 買掛金一覧は「開いたらすぐ今月の状況を見たい」UX。一覧が空でも OK。

## 8. セキュリティ / 権限

- 一覧・詳細・検証入力・MF トグル: 認証済ユーザー（既存 `@PreAuthorize("isAuthenticated()")`）
- 再集計・再検証: admin (`shopNo=0`) のみ。Controller で `hasRole('ADMIN')`、フロントでも admin 以外はボタン非表示

shop フィルタは `LoginUserUtil.resolveEffectiveShopNo` を使用（既存）。

## 9. エッジケース

| ケース | 動作 |
|---|---|
| 取引月にデータ無し | 空のテーブル + サマリは 0 件。エラーにしない |
| `taxIncludedAmountChange = 0` なのに SMILE 支払あり | 差額は SMILE 額そのまま。不一致として表示 |
| 差額 ? 100円 | 一致（端数吸収、旧ロジック踏襲） |
| 手入力検証後、バッチで再検証を走らせた | `verified_manually=true` の行は SMILE 再検証からスキップされ保護される。解除は admin が「手動確定解除」ボタンで実行 |
| 再集計/再検証を連打 | `JobExplorer` が RUNNING を検知し 409 Conflict。フロントは toast で通知 |
| 税率が `null` のデータ | 既存エンティティ上は PK なので null 不可のはず。万一 null ならスキップ |
| supplierNo に紐づく MSupplier が無い | `supplierName` は「不明」と表示（旧 HTML と同様） |

## 10. テスト計画（概要）

### バックエンド
- Service: Specification ビルド（4パラメータの組み合わせ）、`verify()` の差額計算・税抜逆算・100円閾値・`verifiedManually` セット・`verificationNote` 保存
- `releaseManualLock` で `verifiedManually=false` に戻ることの確認
- `SmilePaymentVerifier` が `verified_manually=true` の行をスキップすること（単体テスト）
- Controller: 各エンドポイントの 200/400/403（admin 判定）/ 409（多重実行）
- バッチ起動エンドポイント: `JobExplorer` のモックで RUNNING 判定検証

### フロントエンド E2E (Playwright)
- `e2e/accounts-payable.spec.ts`
  1. 一覧表示（モック）
  2. 取引月切替で再検索
  3. 未検証→検証ダイアログ→備考入力＆更新→状態が「一致」+「手動」バッジに変わる
  4. 検証済み行を再度開くと前回備考が表示される
  5. 不一致の差額が赤字
  6. サマリアラートの件数
  7. admin のみ再集計ボタン & 手動確定解除ボタン表示
  8. 手動確定解除 → 再検証 → SMILE 値で上書きされることを確認
  9. MF 出力トグル
  10. 再集計連打で 409 エラー表示
  11. 実バックエンド疎通1パス（既存ルール遵守）

## 11. 実装順序

1. Liquibase: `verified_manually` + `verification_note` カラム追加、Entity 反映
2. DTO 拡張 + 新規 Request/Response 追加
3. Service に `verify()` / `releaseManualLock()` / `summary()` / Spec 拡張
4. `SmilePaymentVerifier` に手入力保護スキップロジック追加 + 単体テスト
5. Controller: `/summary`, `/verify`, `/manual-lock`, `/mf-export`, `/recalculate`, `/reverify` 追加（多重実行ガード含む）
6. フロント: 型定義 + 一覧リファクタ + サマリ表示 + 検証ダイアログ + 手動確定解除 + 再集計/再検証ボタン
7. E2E + 実バックエンド疎通確認
8. 実データでバッチ実測 → 5秒超なら非同期化検討
9. `/code-review` → マージ

## 12. 決定事項サマリ

| 項目 | 決定 |
|---|---|
| 締め日 | 20日固定（旧システム踏襲） |
| 買掛管理ショップ | 第1事業部(shop_no=1)に集約。shop_no=2 の仕入も shop_no=1 の買掛に合算（`FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO`） |
| 手入力保護 | `verified_manually` フラグでバッチ上書きをブロック、admin のみ解除可。`SmilePaymentVerifier` は true の行をスキップ |
| 備考 | `verification_note` (VARCHAR 500) で DB 永続化 |
| 検証済請求額 | `verified_amount` カラムを追加。振込明細MF一括検証・手動検証ともに同一カラムに書込 |
| バッチ起動方式 | 非同期実行 (`ThreadPoolTaskExecutor`) + `AbortPolicy` → 429 Too Many Requests。3秒後ポーリング5秒間隔で状態可視化 |
| 再集計ジョブ | `accountsPayableAggregation`（軽量版、Init+集計の2ステップ）に変更。CSV不要 |
| 仕入取込ボタン | 「仕入明細取込(SMILE)」で `purchaseFileImport` 起動。`m_shop_linked_file` 登録全ファイル対応 |
The specified drive root "C:\Users\kazuki\AppData\Local\Temp\" either does not exist, or it is not a folder.
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1837ms:
# 買掛帳 整合性検出機能 (軸 B + 軸 C 統合) 設計書

作成日: 2026-04-22
対象ブランチ: `refactor/code-review-fixes`
関連設計書:
- `design-accounts-payable-ledger.md` (買掛帳画面)
- `design-supplier-partner-ledger-balance.md` (累積残管理)
- `design-phase-b-prime-payment-settled.md` (Phase B' T 勘定化)

---

## 1. 背景と目的

既存の買掛帳画面 (`/finance/accounts-payable-ledger`) で 1 仕入先の月次推移 + MF 比較は見えるが、以下 3 点が一画面で答えられない:

1. **「MF 側の買掛帳と自社が合っているか」**: 金額一致の可視化
2. **「連携漏れはないか」**: MF にあって自社に無い (or 逆) の特定
3. **「どの月・どの仕入先でズレたか」**: 全 supplier 分の diff 一覧

### 既存の関連実装
- `MfJournalReconcileService.reconcile()`: 当月 1 日分の仕訳を PURCHASE/SALES/PAYMENT で集計、件数・金額 diff は返すが仕訳レベルのペアマッチングはしない
- `MfSupplierLedgerService.getSupplierLedger()`: 1 supplier 単位で期間の journals 取得、月次 credit/debit/delta を返す
- `AccountsPayableLedgerService.getLedger()`: 自社 summary を supplier×月 で集約、anomaly (UNVERIFIED, VERIFY_DIFF, NEGATIVE_CLOSING, MONTH_GAP 等) 検出

### 目的
- **軸 B**: 買掛帳の既存 row に「MF 比較結果の一致状態バッジ」を追加
- **軸 C**: 期間+shopNo で全 supplier を一括診断する `/integrity-report` endpoint + 専用ページ新設

---

## 2. スコープ

### 2.1 MUST (今回実装)
1. `GET /api/v1/finance/accounts-payable/integrity-report?shopNo=X&fromMonth=Y&toMonth=Z`
   - 期間内の全 supplier を一括診断し、mfOnly / selfOnly / amountMismatch の 3 カテゴリに分類
   - 1 回の MF /journals 取得 (約 5,000 件) で全 supplier を分類、重複 fetch 回避
2. 新ページ `/finance/accounts-payable-ledger/integrity?shopNo=X&fromMonth=Y&toMonth=Z`
   - サマリカード + 3 タブ切替 (MF 側のみ / 自社側のみ / 金額差)
3. 既存買掛帳画面の anomaly バッジ拡張
   - 既存 MFX を細分化: ?? (一致) / ?? MFA (金額差、丸め超) / ?? MFM (件数差 = 連携漏れ) / ? MFN (MF 不明)
   - サマリ部分に「連携漏れ: MF 側 N 件 / 自社側 M 件」一行表示 + /integrity ページへのリンク

### 2.2 SHOULD (優先度中、今回可能な範囲)
- サーバー側キャッシュなし (ユーザー却下済)
- 期間デフォルト 6 ヶ月、最大 12 ヶ月 (24 ヶ月は重すぎる)
- 既存 `/ledger/mf` と内部で journals 取得ロジックを共有 (コード重複を避ける)

### 2.3 OUT-OF-SCOPE
- 仕訳レベル journal-to-journal の 1:1 ペアマッチング (同月同 supplier の複数仕訳を 1 本ずつ対応付ける複雑アルゴリズム)
  - 代わりに supplier × 月 単位での金額差・件数差で判定
- CSV/Excel エクスポート
- 自動修正 (MF 側手動入力仕訳の削除等)

---

## 3. 判定ロジック (ultrathink で検討)

### 3.1 基本単位: (supplierNo, transactionMonth) per pair

既存実装の制約:
- 自社側: `t_accounts_payable_summary` は (shop, supplier, transaction_month, tax_rate) を PK。月×supplier で集約可能
- MF 側: journals を取得し、branch.accountName=="買掛金" で supplier を sub_account_name で特定、月で bucketing
- **1:1 仕訳ペアリングは非現実的** (自社側は集約値で per-journal の情報を持たないため)

→ **月 × supplier 単位での delta 比較** を採用

### 3.2 per (supplier, month) cell の判定 (レビュー R1, R2, R5, R7, R11 反映)

```
self_delta = change_tax_included - payment_settled_tax_included (税率別合算、Phase B' 定義)
mf_delta   = credit_in_month - debit_in_month (MfSupplierLedgerService の logic 再利用)
self_rows  = 当該 supplier×月 の t_accounts_payable_summary 行数 (税率別)
mf_branches = 当該 supplier×月 の MF branch 数 (買掛金 account で matched sub_account)

判定優先度:
0. NO_ACTIVITY   : self_rows == 0 AND mf_branches == 0 → スキップ (Entry 出さず、Summary カウント外) (R1 反映)
1. ? MF_UNMATCHED: supplier 単位で mf_account_master に登録無し (供給側で MF 側 sub_account 解決できない) → UnmatchedSupplierEntry に格納 (supplier 単位、row バッジではなく画面上部バナー / R11 反映)
2. ?? MF_MISSING  : self_rows > 0 AND mf_branches == 0 → SelfOnlyEntry
                   (Phase B' の payment-only 行で change=0/paymentSettled>0 ケースも含む、R2 反映: "行が存在する" ベースで判定)
3. ?? SELF_MISSING: self_rows == 0 AND mf_branches > 0 → MfOnlyEntry
                   (MF 側のみ branch あり、R2 反映: "branch が存在する" ベース)
4. ?? AMOUNT_DIFF_MAJOR: self_rows>0 AND mf_branches>0 AND |self_delta - mf_delta| > \1,000 → AmountMismatchEntry (severity=MAJOR, color=red)
5. ?? AMOUNT_DIFF_MINOR: self_rows>0 AND mf_branches>0 AND \100 < |self_delta - mf_delta| ? \1,000 → AmountMismatchEntry (severity=MINOR, color=amber)
6. ?? MATCH          : self_rows>0 AND mf_branches>0 AND |self_delta - mf_delta| ? \100 (Entry 出さず、Summary カウント外)

閾値 (R5 反映):
- \100 以下: MATCH (丸め誤差、既存 SmilePaymentVerifier 準拠)
- \100 < diff ? \1,000: AMOUNT_DIFF_MINOR (軽微、要確認)
- \1,000 < diff: AMOUNT_DIFF_MAJOR (重大、要対応)

UI バッジ (R7 反映):
- MFA (amber): MINOR
- MFA! (red): MAJOR
- MFM (red): MF_MISSING / SELF_MISSING 共通 (連携漏れ)
- MFN: row バッジから削除、supplier 単位バナーで表示 (R11 反映)

符号 (R10 反映):
- totalSelfOnlyAmount, totalMfOnlyAmount, totalMismatchAmount は絶対値で集計
```

### 3.3 期間トータル の集約 Summary

```
supplierCount        : 期間中に self or MF どちらかに出現した supplier 数
mfOnlyCount          : SELF_MISSING と判定された (supplier, month) ペア数
selfOnlyCount        : MF_MISSING と判定された (supplier, month) ペア数
amountMismatchCount  : AMOUNT_DIFF (|diff| > 100) のペア数
totalMfOnlyAmount    : Σ SELF_MISSING の mf_delta
totalSelfOnlyAmount  : Σ MF_MISSING の self_delta
totalMismatchAmount  : Σ AMOUNT_DIFF の |diff|
```

### 3.4 MfSupplierLedgerService との差分 (ultrathink)

既存 `/ledger/mf` は単一 supplier 版。`/integrity-report` は全 supplier 版。

共通化ポイント:
- `fetchAllJournals()`, `buildStartDateCandidates()`, `resolveSubAccountNames()`, `toClosingMonthDay20()` は共有
- 既存 service に「全 supplier 版」method を追加 or 新 service `AccountsPayableIntegrityService` を切り出し

→ **新 service `AccountsPayableIntegrityService`** を切り出す (責務分離、既存 service の可読性維持)。既存 helper は package-private に昇格して共有。

---

## 4. Backend API 設計

### 4.1 `GET /api/v1/finance/accounts-payable/integrity-report`

**Query**:
- `shopNo: Integer` (必須, assertShopAccess)
- `fromMonth: yyyy-MM-dd` (必須, 20 日締め日)
- `toMonth: yyyy-MM-dd` (必須, 20 日締め日)

**期間制限**: 最大 **12 ヶ月** (24 ヶ月は journals 10,000 件超の可能性、通常運用で不要)

**Response**: `IntegrityReportResponse` (下記 §7)

**エラー**:
- 401: 未認証
- 403: 非 admin の他 shop 指定
- 400: 期間不正 / 12 ヶ月超
- 500 系: MF 認証切れ / scope 不足 (MfReAuthRequiredException / MfScopeInsufficientException を 401/403 に変換)

### 4.2 パフォーマンス設計

- MF journals 取得: 1 回 (期間 6 ヶ月で 2,500 件、12 ヶ月で 5,000 件)
- supplier 解決: `mf_account_master.findAll()` 1 回 (数百件)
- 自社 summary 取得: 期間内の全 row 1 回
- 処理: メモリ内で supplier × 月 cell を構築

全体 10 秒以内の所要時間目安。既存 `/ledger/mf` の fiscal year fallback + 350ms sleep + 429 retry は全て流用。

---

## 5. Service 設計

### 5.1 新規 `AccountsPayableIntegrityService`

```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountsPayableIntegrityService {
    private static final int MAX_PERIOD_MONTHS = 12;
    private static final BigDecimal MATCH_TOLERANCE = BigDecimal.valueOf(100);

    private final MfSupplierLedgerService mfSupplierLedgerService;  // helper 共有
    private final MPaymentSupplierService paymentSupplierService;
    private final TAccountsPayableSummaryService summaryService;
    private final MfOauthService mfOauthService;
    private final MfApiClient mfApiClient;
    private final MfAccountMasterRepository mfAccountMasterRepository;

    public IntegrityReportResponse generate(Integer shopNo, LocalDate fromMonth, LocalDate toMonth) {
        // 1. 入力検証 (20 日固定 + 期間制限 12 ヶ月)
        // 2. 自社 summary を期間で取得、supplier × 月 に index
        // 3. MF /journals を期間で取得 (全 supplier 分) ? MfSupplierLedgerService の helper 共有
        // 4. mf_account_master から supplier_code → sub_account_name の逆引きマップ構築
        // 5. 各 MF branch を走査して supplier × 月 の buying delta を算出
        // 6. 全 supplier × 全月 の cell を一巡:
        //    - self_delta / mf_delta を計算
        //    - 判定ルール (§3.2) に従って mfOnly/selfOnly/amountMismatch に分類
        // 7. Summary を集計して Response 返却
    }
}
```

### 5.2 共通 helper を独立クラスに切り出し (R3 反映: package-private 昇格方針を取り下げ)

既存 `MfSupplierLedgerService` の private helper (`buildStartDateCandidates`, `fetchAllJournals`, `callListJournalsWithRetry`, `toClosingMonthDay20`, `RATE_LIMIT_SLEEP_MS` 等) を**新 package-private クラス**に切り出す:

```java
// backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfJournalFetcher.java
@Component
@RequiredArgsConstructor
class MfJournalFetcher {
    private final MfApiClient mfApiClient;

    /**
     * 期間 (start_date ? end_date) の MF /journals を全件取得。
     * fiscal year 境界の 400 は多段 fallback、429 は指数バックオフで吸収。
     */
    List<MfJournal> fetchJournalsForPeriod(MMfOauthClient client, String accessToken,
                                              LocalDate fromMonth, LocalDate toMonth) {
        // 現行 MfSupplierLedgerService の logic をそのまま移植
    }

    /** 20 日締め月への bucket key 変換 (公開) */
    static LocalDate toClosingMonthDay20(LocalDate date) { ... }
}
```

- `MfSupplierLedgerService` は `MfJournalFetcher` を注入して使う (ロジック変更なし、呼出し先差し替えのみ)
- 新 `AccountsPayableIntegrityService` も `MfJournalFetcher` を注入
- **既存 /ledger/mf の動作を一切変更しない**

### 5.3 既存 /ledger/mf との非干渉
- `MfSupplierLedgerService.getSupplierLedger()` は helper 移譲のみで動作無改修
- 期首残扱い (sub_account 粒度無し) は現状維持

---

## 6. Controller

`FinanceController` に 1 endpoint 追加:

```java
@GetMapping("/accounts-payable/integrity-report")
public ResponseEntity<?> getIntegrityReport(
        @RequestParam("shopNo") Integer shopNo,
        @RequestParam("fromMonth") @DateTimeFormat(iso=ISO.DATE) LocalDate fromMonth,
        @RequestParam("toMonth") @DateTimeFormat(iso=ISO.DATE) LocalDate toMonth) {
    assertShopAccess(shopNo);
    try {
        return ResponseEntity.ok(integrityService.generate(shopNo, fromMonth, toMonth));
    } catch (MfReAuthRequiredException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
    } catch (MfScopeInsufficientException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage(), "requiredScope", e.getRequiredScope()));
    } catch (IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", e.getMessage()));
    }
}
```

---

## 7. DTO

```java
@Data @Builder
public class IntegrityReportResponse {
    private Integer shopNo;
    private LocalDate fromMonth;
    private LocalDate toMonth;
    private Instant fetchedAt;
    private Integer totalJournalCount;   // MF から取得した仕訳総数
    private Integer supplierCount;        // 期間内に登場した supplier 数
    private List<MfOnlyEntry> mfOnly;
    private List<SelfOnlyEntry> selfOnly;
    private List<AmountMismatchEntry> amountMismatch;
    private List<UnmatchedSupplierEntry> unmatchedSuppliers;  // MF sub_account 解決不可
    private Summary summary;

    @Data @Builder
    public static class MfOnlyEntry {
        private LocalDate transactionMonth;
        private String subAccountName;          // MF 側の仕入先名
        private BigDecimal creditAmount;         // 当月 credit 合計
        private BigDecimal debitAmount;          // 当月 debit 合計
        private BigDecimal periodDelta;          // credit - debit
        private Integer branchCount;             // 当月対応 branch 数
        private Integer guessedSupplierNo;       // mf_account_master からの逆引き (nullable)
        private String reason;                   // "MF 側手入力 or 自社取込漏れ"
    }

    @Data @Builder
    public static class SelfOnlyEntry {
        private LocalDate transactionMonth;
        private Integer supplierNo;
        private String supplierCode;
        private String supplierName;
        private BigDecimal selfDelta;            // change - payment_settled
        private Integer taxRateRowCount;
        private String reason;                   // "MF CSV 出力漏れ or 未反映"
    }

    @Data @Builder
    public static class AmountMismatchEntry {
        private LocalDate transactionMonth;
        private Integer supplierNo;
        private String supplierCode;
        private String supplierName;
        private BigDecimal selfDelta;
        private BigDecimal mfDelta;
        private BigDecimal diff;                 // self - mf
        private String severity;                 // MINOR (<=1000), MAJOR (>1000)
    }

    @Data @Builder
    public static class UnmatchedSupplierEntry {
        private Integer supplierNo;
        private String supplierCode;
        private String supplierName;
        private String reason;                   // "mf_account_master に登録なし"
    }

    @Data @Builder
    public static class Summary {
        private Integer mfOnlyCount;
        private Integer selfOnlyCount;
        private Integer amountMismatchCount;
        private Integer unmatchedSupplierCount;
        private BigDecimal totalMfOnlyAmount;
        private BigDecimal totalSelfOnlyAmount;
        private BigDecimal totalMismatchAmount;
    }
}
```

---

## 8. 軸 B: 買掛帳画面の一致状態バッジ拡張

### 8.1 現状 UI (accounts-payable-ledger.tsx)
既に `mfLedger` を別 fetch して `mfDeltaByMonth` でマージ表示。`mfMismatch` (|diff| > 10,000) のとき MFX バッジ。

### 8.2 変更点
`mfMismatch` 判定を 4 段階に細分化:

```typescript
function mfMatchStatus(selfDelta: number, mfDelta: number | null, matchedSubAccountsEmpty: boolean): {
  code: 'MATCH' | 'AMOUNT_DIFF' | 'MF_MISSING' | 'SELF_MISSING' | 'MF_UNMATCHED' | null;
  label: string;
  color: 'emerald' | 'amber' | 'red' | 'slate';
} {
  if (matchedSubAccountsEmpty && selfDelta !== 0) {
    return { code: 'MF_UNMATCHED', label: 'MFN', color: 'slate' };
  }
  if (mfDelta === null) return { code: null, label: '', color: 'slate' };
  if (selfDelta === 0 && mfDelta !== 0) {
    return { code: 'SELF_MISSING', label: 'MFM', color: 'red' };
  }
  if (selfDelta !== 0 && mfDelta === 0) {
    return { code: 'MF_MISSING', label: 'MFM', color: 'red' };
  }
  const diff = Math.abs(selfDelta - mfDelta);
  if (diff <= 100) return { code: 'MATCH', label: '', color: 'emerald' };  // 一致
  if (diff <= 1000) return { code: 'AMOUNT_DIFF', label: 'MFA', color: 'amber' };
  return { code: 'AMOUNT_DIFF', label: 'MFA!', color: 'amber' };  // Major
}
```

### 8.3 サマリヘッダに追加
既存サマリ card に「連携漏れ」列を追加:
```
| MF 側のみ N 件 | 自社側のみ M 件 | 金額差 K 件 | [整合性レポートへ →] |
```

`/finance/accounts-payable-ledger/integrity?shopNo=...&fromMonth=...&toMonth=...` に遷移するリンク。

---

## 9. 軸 C: 整合性レポート画面

### 9.1 新ページ `/finance/accounts-payable-ledger/integrity`

ファイル:
- `frontend/app/(authenticated)/finance/accounts-payable-ledger/integrity/page.tsx`
- `frontend/components/pages/finance/integrity-report.tsx`

### 9.2 UI 構成
```
PageHeader: 「買掛帳 整合性レポート」

検索フォーム:
  [ショップ] [開始月] [終了月] [再取得ボタン]
  (デフォルト: 当月含む過去 6 ヶ月、最大 12 ヶ月)

サマリカード:
  MF 側のみ: N 件 (金額 \X)
  自社側のみ: M 件 (金額 \Y)
  金額差: K 件 (合計差 \Z)
  MF 未登録 supplier: L 件

タブ (shadcn Tabs):
  [MF 側のみ] [自社側のみ] [金額差] [MF 未登録]

各タブで DataTable (shadcn):
  - MF 側のみ: 月 | sub_account | credit | debit | delta | 推定 supplier | 備考
  - 自社側のみ: 月 | supplier コード | 仕入先名 | self_delta | 税率行数 | 備考
  - 金額差: 月 | 仕入先 | self | MF | diff | severity
  - MF 未登録: supplier コード | 仕入先名 | 備考

各 row クリック → 買掛帳画面に遷移 (?supplierNo=X で pre-fill)
```

### 9.3 既存買掛帳画面に link 追加
サマリ card 内に「整合性レポートへ →」ボタン。

---

## 10. 未確定事項 (設計で握る)

| # | 論点 | 決定 |
|---|---|---|
| 1 | MF MISSING / SELF MISSING の閾値 (self_delta == 0 の境界) | `BigDecimal.ZERO.compareTo() == 0` で厳密に判定 (\1 以内は一致扱い) |
| 2 | unmatchedSuppliers をどこに出すか | 別リスト `unmatchedSuppliers` として Response に追加、UI で別タブ |
| 3 | mfOnly の supplier 特定 (sub_account_name のみでは supplierNo 不明) | `mf_account_master.search_key` からの逆引きで推定 supplierNo を提供 (guessedSupplierNo) |
| 4 | 期間デフォルト | 過去 6 ヶ月 (今月含む) |
| 5 | 大量データ時のタイムアウト | サーバー側で 30 秒以内想定、MF journals 取得は既存 safeguard 50 pages / 429 retry 流用 |
| 6 | 既存の `/ledger/mf` 画面との使い分け | `/ledger/mf` = 1 supplier 詳細、`/integrity-report` = 全 supplier 俯瞰。補完関係で両方残す |

---

## 11. 実装順序 (レビュー R3, R9 反映)

1. **DTO 新設**: `IntegrityReportResponse.java` (+ 内部 record 群)
2. **Repository method 新設** (R9): `TAccountsPayableSummaryRepository.findByShopNoAndTransactionMonthBetween...` (supplier 指定無し、期間全 row)
3. **共通 helper 切り出し** (R3): `MfJournalFetcher` 新設、`MfSupplierLedgerService` から移譲
4. **Service 新設**: `AccountsPayableIntegrityService.java`
   - `MfJournalFetcher` を DI
   - supplier×月 cell 構築 + 判定ロジック (§3.2)
   - mf_account_master 逆引き map 構築 (§5.1 step 4)
5. **Controller endpoint 追加**
6. **コンパイル確認** (+ /ledger/mf の動作が壊れていないこと確認)
7. **Frontend types**: `integrity-report.ts`
8. **買掛帳画面拡張**: mfMatchStatus 判定、サマリヘッダリンク、MFN バナー化
9. **新ページ**: `/finance/accounts-payable-ledger/integrity`
10. **typecheck 確認**
11. **バックエンド再起動** → curl で動作確認 (カミ商事 MATCH / 太幸 SELF_MISSING / やしき SELF_MISSING 等)
12. **整合性レポート動作** を browser で目視
13. **DOC 更新 + commit**

---

## 12. テスト観点

### Unit (AccountsPayableIntegrityService)
- 自社 only supplier → SelfOnlyEntry に分類
- MF only supplier (例: 太幸) → MfOnlyEntry に分類、guessedSupplierNo 解決済
- ペアあり差額 100 円以下 → MATCH (Entry 無し)
- ペアあり差額 100?1000 → AMOUNT_DIFF MINOR
- ペアあり差額 1000 超 → AMOUNT_DIFF MAJOR
- mf_account_master に登録無し supplier → UnmatchedSupplierEntry
- supplier 数 = 自社 + MF ペア の and-合計

### Integration
- 期間 12 ヶ月超で 400
- 非 admin の他 shop → 403
- MF 認証切れ → 401

### E2E (Playwright)
- /integrity ページ表示、タブ切替
- 各 row → 買掛帳画面遷移
- 既存買掛帳の MFA/MFM バッジ表示

---

## 13. リスクと軽減

| リスク | 軽減策 |
|---|---|
| 12 ヶ月処理の所要時間 | 既存 /ledger/mf と同じ 8-15 秒程度、UI は Skeleton |
| MF journals が 10,000 超 (期間長・取引多) | 既存 50 pages safeguard |
| mfOnly の supplier 特定失敗 | guessedSupplierNo null でフロント表示 |
| 既存 /ledger/mf 動作への影響 | package-private helper 昇格のみ、ロジック変更なし |

---

## 14. 既存機能への影響

| 機能 | 影響 | 対処 |
|---|---|---|
| `/ledger` | 無改修 | - |
| `/ledger/mf` | helper 共有のため package-private 化 | 動作変更なし |
| `MfJournalReconcileService` | 無改修 | - |
| `MfBalanceReconcileService` | 無改修 | - |
| 買掛帳 UI | mfMatchStatus 判定細分化 | 既存 bucket に追加バッジ |
| 買掛金一覧画面 | 無影響 | - |
The specified drive root "C:\Users\kazuki\AppData\Local\Temp\" either does not exist, or it is not a folder.
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1887ms:
# 買掛帳画面 (Accounts Payable Ledger) 設計書

作成日: 2026-04-22
対象ブランチ: `refactor/code-review-fixes`
関連設計書: `design-supplier-partner-ledger-balance.md`, `design-phase-b-prime-payment-settled.md`

---

## 1. 背景と目的

### 既存機能の限界

`/finance/accounts-payable` (買掛金一覧) は **1 取引月の全仕入先一覧** で、1 仕入先の月次推移を追う動線が無い。残高突合で diff が出ても、**どの月にずれが生まれたか** を特定する手段が不足している。

具体的な運用課題:
- 竹の子の里が 2/20 に値引繰越 → 3/20 に繰り越された流れが見えない
- カミ商事の 2025-06 support データ (verified=0) がいつから欠落したか不明
- MF 側と自社が乖離する時点が特定できない

### 目的

**1 仕入先について月次推移を時系列で並べて「いつ・何が」ずれたかを一目で把握できる**画面を新設。

### 用語

- 自社 closing: `opening + effectiveChange - payment_settled` (Phase B' T 勘定)
- MF closing: `/journals` 累積で算出した仕入先別累積残 (sub_account ベース)
- effectiveChange: 手動確定時は `verified_amount` 優先、それ以外は `tax_included_amount_change`

---

## 2. スコープ

### 必須スコープ (第 1 版)

| # | 項目 | 優先度 |
|---|---|---|
| S1 | 新 API `/accounts-payable/ledger`: 自社 DB のみの月次推移取得 | 必須 |
| S2 | 新 API `/accounts-payable/ledger/mf`: MF /journals 累積で仕入先 closing 取得 (オプション) | 必須 |
| S3 | 新ページ `/finance/accounts-payable-ledger`: テーブル表示 + anomaly ハイライト | 必須 |
| S4 | 既存 `/finance/accounts-payable` 仕入先名リンク → 本画面遷移 | 必須 |
| S5 | サイドバー menu 追加 | 必須 |

### スコープ外 (次フェーズ候補)

- 税率別の詳細内訳 expand 表示 (第 1 版は合算表示、UI 余裕があれば後続)
- 仕入先名のあいまいマッチング (MF sub_account 名と自社 payment_supplier 名の表記揺れ対応)
- CSV/Excel 出力
- 複数仕入先の一括比較

---

## 3. データ構造 (既存 Phase B' モデル再利用)

### 集約単位

PK `(shop_no, supplier_no, transaction_month, tax_rate)` → **supplier 単位に税率別を合算** してレンダリング。

税率別の内訳を取ることも可能だが、第 1 版は集約値のみ表示する。

### anomaly 検出ルール（レビュー R5, R10 反映）

| コード | 条件 | Severity | 表示色 |
|---|---|---|---|
| UNVERIFIED | `Σchange > 0` AND `Σverified == 0` (税率別に全て null/0) | WARN | red |
| VERIFY_DIFF | `|Σchange - Σverified| > 100` AND `Σverified > 0` | WARN | orange |
| NEGATIVE_CLOSING | `closing < 0` AND `!hasPaymentOnly` | INFO | amber |
| PAYMENT_OVER | `closing < 0` AND `hasPaymentOnly` | INFO | amber |
| CONTINUITY_BREAK | 前月 closing ≠ 当月 opening (許容差 ±1 円、丸め) / 前月行が rows 内 or 期間外前月に存在する場合のみ判定 | CRITICAL | red |
| MONTH_GAP | 前月 row が rows 内にも期間外前月にも存在しないとき (summary 欠落、集計バッチ未実行疑い) | WARN | orange |
| MF_DELTA_MISMATCH | MF 比較 ON 時、`|selfPeriodDelta ? mfPeriodDelta| > 10,000`（月次 credit?debit vs change?payment_settled の期間差比較、R8 反映） | WARN | amber |

※ **PAYMENT_ONLY はレビュー R10 反映で anomaly 列から除外**。row.hasPaymentOnly (boolean) で UI に「支払のみ」badge を表示する。

※ 閾値 100 円 (VERIFY_DIFF) / 10,000 円 (MF_DELTA_MISMATCH) は `application.yml` の `odamitsu.ledger.*` で将来外出し可能（第 1 版ハードコード、レビュー R11 の軽減策）。

### 前月との連続性チェック（レビュー R5, R6 反映）

自社 opening を「前月 closing と一致すべき」というチェック。Phase A/B' のバッチが正常動作していれば自動的に一致。不整合検出は backfill 未実行や bug の早期発見に役立つ。

**前月特定ルール**:
1. 当月 M のレコード走査時、M-1 月が `rows` に含まれていれば、そこの closing と比較
2. M-1 月が `rows` に無い場合 (fromMonth=M のとき、fromMonth 直前月): **Repository で期間外直前月を 1 回追加取得**して比較 (R6 反映)
3. 期間外直前月にも summary が無い場合 (真の起点): CONTINUITY_BREAK スキップ
4. M-1 月が存在するはずだが欠落している場合 (月抜け): **CONTINUITY_BREAK ではなく MONTH_GAP anomaly を出す** (R5 反映)

**実装**: `AccountsPayableLedgerService` は期間内 rows に加えて fromMonth 直前月 (= fromMonth.minusMonths(1)) の summary を事前取得し、起点月の比較に使う。

---

## 4. Backend API 設計

### 4.1 GET /api/v1/finance/accounts-payable/ledger

**Query**:
- `shopNo: Integer` (admin 以外は ignore されて自 shop 固定)
- `supplierNo: Integer` (必須)
- `fromMonth: yyyy-MM-dd` (必須、20日締め日)
- `toMonth: yyyy-MM-dd` (必須、20日締め日、from <= to)

**期間制限**: `toMonth - fromMonth` は最大 **24 ヶ月** (DoS 対策 + UI 負荷抑制)。デフォルト 12 ヶ月、根拠: MF /journals per_page=1000 × 24 ≒ 24000 件で 10 秒級（R15 反映）

**セキュリティ** (R7 反映):
- 非 admin が自 shop 以外の shopNo を指定 → `FinanceController#assertShopAccess` で **403** (既存パターン踏襲、silent override しない)
- 非 admin が自 shop の supplier_no 以外 (他 shop の supplier) を指定 → supplier 存在検証時に **404** (権限漏洩回避、存在を暴露しない)

**エラー** (R4 反映):
- supplier が存在しない (or 自 shop 外) → **404**
- supplier 存在・期間内 summary 0 件 → **200 + rows=[], summary 数値フィールド 0, finalClosing=0**
- fromMonth > toMonth → 400
- 期間 24 ヶ月超 → 400
- 未認証 → 401

**Response**:
```jsonc
{
  "supplier": {
    "shopNo": 1,
    "supplierNo": 32,
    "supplierCode": "010100",
    "supplierName": "ｶﾐ 商事(株) [5]"
  },
  "fromMonth": "2025-06-20",
  "toMonth": "2026-03-20",
  "rows": [
    {
      "transactionMonth": "2025-06-20",
      "openingBalanceTaxIncluded": 0,
      "changeTaxIncluded": 2301732,
      "verifiedAmount": 0,
      "paymentSettledTaxIncluded": 2462618,
      "closingBalanceTaxIncluded": -160886,
      "taxRateCount": 1,
      "rowKeys": [
        { "taxRate": 10, "verifiedManually": false, "verificationResult": null, "isPaymentOnly": false, "mfExportEnabled": true, "mfTransferDate": null }
      ],
      "hasPaymentOnly": false,
      "hasVerifiedManually": false,
      "anomalies": [
        { "code": "UNVERIFIED", "severity": "WARN", "message": "当月仕入あり・検証金額なし" },
        { "code": "NEGATIVE_CLOSING", "severity": "INFO", "message": "累積残が負 (値引繰越)" }
      ],
      "continuityOk": true
    }
    // ... 各月の行
  ],
  "summary": {
    "totalChangeTaxIncluded": 20000000,
    "totalVerified": 15000000,
    "totalPaymentSettled": 14000000,
    "finalClosing": 6000000,
    "unverifiedMonthCount": 2,
    "continuityBreakCount": 0,
    "negativeClosingMonthCount": 1,
    "paymentOnlyMonthCount": 0
  }
}
```

### 4.2 GET /api/v1/finance/accounts-payable/ledger/mf (レビュー R1, R2, R8, R13 反映)

**Query**: 同上 (shopNo, supplierNo, fromMonth, toMonth)

**処理**:
1. `payment_supplier` 取得 (supplier_no / supplier_code / supplier_name)
2. **supplier_code を key に `mf_account_master.search_key == supplier_code` から対応する `sub_account_name` を解決** (R1: 既存 `MfJournalReconcileService.buildMfSubAccountMap` と同じマッチング方式)。存在しない場合は payment_supplier_name にフォールバックし、unmatched 候補として UI に返す。
3. MF `/journals?start_date=fromMonth&end_date=toMonth` をページングで全件取得 (R2: `per_page=1000` 固定、items が per_page 未満になるまでループ)
4. 各 branch で:
   - `creditor.accountName == "買掛金" AND creditor.subAccountName ∈ matchedSubAccountNames` → credit 側として +value
   - `debitor.accountName == "買掛金" AND debitor.subAccountName ∈ matchedSubAccountNames` → debit 側として +value
   - (R13: debitor / creditor 両側検査)
5. 月毎に credit / debit をバケット分け + 累積 running total
6. Response: 月毎の `mfCreditInMonth` / `mfDebitInMonth` / `mfPeriodDelta = credit - debit`

**期間差比較方式** (R8 反映):
- 第 1 版は月次 **delta (= credit - debit) のみ返す** (closing 累積は出さない)
- 自社側も月次 delta (= effectiveChange - payment_settled) を比較
- diff = `|self.periodDelta - mf.periodDelta|` が閾値超なら MF_DELTA_MISMATCH
- closing 累積同士の比較は期首残差が常時点灯するためやめる (Phase B'' light と同じ判断)

**マッチング** (R1 反映):
- 主: `mf_account_master.search_key == supplier_code` で `sub_account_name` を解決
- 補: supplier_name での exact match も試行 (後方互換)
- どちらも 0 件なら `unmatchedCandidates` に supplier_name を入れて UI に警告
- 既存 `MfJournalReconcileService.java:buildMfSubAccountMap("買掛金", "買掛金")` 相当のヘルパーを Service 内に実装

**Response** (R8, R19 反映: 期間 echo 追加 + closing 列削除し delta のみ返す):
```jsonc
{
  "shopNo": 1,
  "supplierNo": 32,
  "supplierName": "ｶﾐ 商事(株) [5]",
  "fromMonth": "2025-06-20",
  "toMonth": "2026-03-20",
  "matchedSubAccountNames": ["カミ商事（株）"],
  "unmatchedCandidates": [],
  "rows": [
    {
      "transactionMonth": "2025-06-20",
      "mfCreditInMonth": 2301732,
      "mfDebitInMonth": 0,
      "mfPeriodDelta": 2301732    // = credit - debit (当月純増減、closing 累積ではない)
    }
    // ...
  ],
  "fetchedAt": "2026-04-22T10:00:00Z",
  "totalJournalCount": 54
}
```

**エラー**:
- MF scope 不足 (403) → `MfScopeInsufficientException` 経由で UI にメッセージ
- MF 認証切れ (401) → `MfReAuthRequiredException` → UI トースト

**タイムアウト/負荷**:
- 24 ヶ月 × 1000 件/月 ≒ 24000 件の journals 走査
- 数秒?10 秒程度の所要を想定
- UI は Skeleton + progress

---

## 5. Service 設計

### 5.1 AccountsPayableLedgerService (新規)

**責務**: 自社 DB から月次推移を取得し、anomaly 検出を含めた DTO を生成。

**主要メソッド**:
```java
public LedgerResponse getLedger(Integer shopNo, Integer supplierNo,
                                 LocalDate fromMonth, LocalDate toMonth) {
    // 1. supplier 存在確認 (m_payment_supplier)
    // 2. 期間内の summary 行取得
    //    → 新 Repository method: findByShopNoAndSupplierNoAndTransactionMonthBetween
    // 3. 月毎に税率別を集約 (supplier 単位)
    // 4. anomaly 検出 + 前月継続チェック
    // 5. summary 統計算出
    // 6. Response を返す
}
```

**依存**:
- `TAccountsPayableSummaryRepository` (新 method 追加)
- `MPaymentSupplierService` (supplier 名取得)

**集約ロジック (supplier 単位で税率別合算)** ? R3 反映:

closing は **既存 `PayableBalanceCalculator.closingTaxIncluded()` を税率別に呼び、supplier 単位で SUM** する (再実装せず Phase B' のロジックに委譲)。change / opening / payment_settled は単純和。

```java
// verified_amount は Phase B' の「全行同値なら代表値、不一致なら SUM」 ロジック準拠
// (既存 PaymentMfImportService.sumVerifiedAmountForGroup 相当)
BigDecimal aggregateVerified(List<TAccountsPayableSummary> group) {
    List<BigDecimal> perRow = group.stream()
        .map(r -> r.getVerifiedAmount() != null ? r.getVerifiedAmount() : BigDecimal.ZERO)
        .toList();
    BigDecimal first = perRow.get(0);
    boolean allSame = perRow.stream().allMatch(v -> v.compareTo(first) == 0);
    return allSame ? first : perRow.stream().reduce(ZERO, BigDecimal::add);
}

// closing は PayableBalanceCalculator を呼ぶ (R3 反映)
BigDecimal aggregateClosing(List<TAccountsPayableSummary> group) {
    return group.stream()
        .map(PayableBalanceCalculator::closingTaxIncluded)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
}
```

### 5.2 MfSupplierLedgerService (新規)

**責務**: MF /journals を期間累積で取得し、仕入先別の月末 closing を算出。

**主要メソッド**:
```java
public MfLedgerResponse getSupplierLedger(Integer shopNo, Integer supplierNo,
                                            LocalDate fromMonth, LocalDate toMonth) {
    // 1. supplier 取得 (MPaymentSupplierService)
    // 2. sub_account_name 候補を解決 (R1: mf_account_master.search_key == supplierCode)
    //    - 無ければ supplierName を unmatchedCandidates として返却
    // 3. MF /journals を per_page=1000 ループで全件取得 (R2: pagination safeguard 50 pages)
    // 4. 各 branch の debitor / creditor 両側検査 (R13):
    //    - accountName == "買掛金" AND subAccountName ∈ matchedSubAccountNames
    //    - creditor 側 → 月の credit bucket に +value
    //    - debitor  側 → 月の debit  bucket に +value
    // 5. transactionDate → 20日締め月への bucketing (date<=20: 当月20日、>20: 翌月20日)
    // 6. 月次 credit / debit / periodDelta を返す (closing 累積は返さない、R8)
}
```

**依存**:
- `MfOauthService`, `MfApiClient` (既存)
- `MPaymentSupplierService`
- `MfAccountMasterRepository` (R1 追加、sub_account_name 解決用)

**ページング** (R2 反映):
```java
int page = 1;
final int perPage = 1000;
while (true) {
    var res = mfApiClient.listJournals(client, token, startDate, endDate, page, perPage);
    all.addAll(res.items());
    if (res.items().size() < perPage) break;
    page++;
    if (page > 50) throw new IllegalStateException("pagination safeguard");
}
```

**両側検査** (R13 反映):
```java
for (branch in journal.branches):
    BigDecimal credit = ZERO, debit = ZERO;
    if (branch.creditor != null
        && "買掛金".equals(branch.creditor.accountName)
        && matchedSubAccountNames.contains(branch.creditor.subAccountName)) {
        credit = nz(branch.creditor.value);
    }
    if (branch.debitor != null
        && "買掛金".equals(branch.debitor.accountName)
        && matchedSubAccountNames.contains(branch.debitor.subAccountName)) {
        debit = nz(branch.debitor.value);
    }
    if (credit.signum() != 0 || debit.signum() != 0) {
        bucket[monthKey(journal.transactionDate)].credit += credit;
        bucket[monthKey(journal.transactionDate)].debit  += debit;
    }
```

**20日締め月への bucketing**:
- `date.getDayOfMonth() <= 20` → `YearMonth.from(date).atDay(20)`
- `date.getDayOfMonth() > 20` → `YearMonth.from(date).plusMonths(1).atDay(20)`
- 2025-07-15 → 2025-07-20、2025-07-25 → 2025-08-20

**マッチング** (R1 反映、既出):
- `mf_account_master.search_key == supplier_code` で `sub_account_name` を解決
- 複数返ればすべて matchedSubAccountNames に含めて合算
- 見つからなければ supplier_name をフォールバックで試し、それも 0 件なら unmatchedCandidates に返却

---

## 6. Repository 変更 (R17 反映: derived query 採用)

`TAccountsPayableSummaryRepository` に以下 method 追加:

```java
/**
 * 指定 supplier の期間内 summary を取得 (買掛帳 API 用)。tax_rate 昇順で返却、月単位の集約は Service 側で実施。
 */
List<TAccountsPayableSummary> findByShopNoAndSupplierNoAndTransactionMonthBetweenOrderByTransactionMonthAscTaxRateAsc(
    Integer shopNo, Integer supplierNo, LocalDate fromMonth, LocalDate toMonth);

/**
 * 起点月直前月の summary を取得 (R6: CONTINUITY_BREAK 判定の期間外前月参照)。
 * 同月内に存在する全税率行を返す。
 */
List<TAccountsPayableSummary> findByShopNoAndSupplierNoAndTransactionMonth(
    Integer shopNo, Integer supplierNo, LocalDate transactionMonth);
```

---

## 7. DTO 設計

`AccountsPayableLedgerResponse` (新):
```java
@Data @Builder
public class AccountsPayableLedgerResponse {
    private SupplierInfo supplier;
    private LocalDate fromMonth;
    private LocalDate toMonth;
    private List<LedgerRow> rows;
    private LedgerSummary summary;

    @Data @Builder
    public static class SupplierInfo {
        private Integer shopNo;
        private Integer supplierNo;
        private String supplierCode;
        private String supplierName;
    }

    @Data @Builder
    public static class LedgerRow {
        private LocalDate transactionMonth;
        private BigDecimal openingBalanceTaxIncluded;
        private BigDecimal changeTaxIncluded;
        private BigDecimal verifiedAmount;
        private BigDecimal paymentSettledTaxIncluded;
        private BigDecimal closingBalanceTaxIncluded;
        private Integer taxRateCount;
        private List<TaxRateInfo> rowKeys;
        private boolean hasPaymentOnly;
        private boolean hasVerifiedManually;
        private List<Anomaly> anomalies;
        private boolean continuityOk;
    }

    @Data @Builder
    public static class TaxRateInfo {
        private BigDecimal taxRate;
        private Boolean verifiedManually;
        private Integer verificationResult;
        private Boolean isPaymentOnly;
        private Boolean mfExportEnabled;
        private LocalDate mfTransferDate;
    }

    @Data @Builder
    public static class Anomaly {
        private String code;      // UNVERIFIED, VERIFY_DIFF, NEGATIVE_CLOSING, etc.
        private String severity;  // CRITICAL, WARN, INFO
        private String message;
    }

    @Data @Builder
    public static class LedgerSummary {
        private BigDecimal totalChangeTaxIncluded;
        private BigDecimal totalVerified;
        private BigDecimal totalPaymentSettled;
        private BigDecimal finalClosing;
        private Integer unverifiedMonthCount;
        private Integer continuityBreakCount;
        private Integer negativeClosingMonthCount;
        private Integer paymentOnlyMonthCount;
    }
}
```

`MfSupplierLedgerResponse` (新、R8/R19 反映):
```java
@Data @Builder
public class MfSupplierLedgerResponse {
    private Integer shopNo;
    private Integer supplierNo;
    private String supplierName;
    private LocalDate fromMonth;
    private LocalDate toMonth;
    private List<String> matchedSubAccountNames;
    private List<String> unmatchedCandidates;
    private List<MfLedgerRow> rows;
    private Instant fetchedAt;
    private Integer totalJournalCount;

    @Data @Builder
    public static class MfLedgerRow {
        private LocalDate transactionMonth;
        private BigDecimal mfCreditInMonth;
        private BigDecimal mfDebitInMonth;
        private BigDecimal mfPeriodDelta;  // = credit - debit (R8: closing 累積は出さない)
    }
}
```

---

## 8. Frontend 設計

### 8.1 新規ファイル

```
frontend/
  app/(authenticated)/finance/accounts-payable-ledger/
    page.tsx
  components/pages/finance/
    accounts-payable-ledger.tsx
  types/
    accounts-payable-ledger.ts
```

### 8.2 ページ構造

**URL**: `/finance/accounts-payable-ledger?shopNo=1&supplierNo=32&fromMonth=2025-06-20&toMonth=2026-03-20`

URL クエリで状態保持 (既存 accounts-payable と同じパターン)。

**UI 構成** (R14, R18 反映: MF は明示ボタン、URL クエリは supplierNo 必須):
```tsx
<PageHeader title="買掛帳" />

<Card> {/* 検索フォーム */}
  <SearchableSelect>仕入先</SearchableSelect>
  <Input type=month>開始月</Input>  {/* デフォルト 12 ヶ月前 */}
  <Input type=month>終了月</Input>
  <Button>検索</Button>
</Card>

{/* supplierNo 未選択時はここで終わり */}

<Card> {/* MF 比較トリガ (自社 ledger fetch 後に表示) */}
  <Button>MF と比較を取得</Button>  {/* 押下で /ledger/mf fetch、loading 中は disabled + Skeleton */}
</Card>

<Card> {/* 仕入先情報 */}
  {supplier.supplierCode} {supplier.supplierName}
</Card>

<Card> {/* サマリ */}
  期間累計: 仕入 \X / 検証 \Y / 支払反映 \Z
  最終残: \W
  警告: 未検証 N 件、値引繰越 M 件、継続不整合 K 件
</Card>

<DataTable> {/* 月次明細 */}
  月 | 前月繰越 | 仕入 | 検証額 | 支払反映 | 当月残 | ステータス
  [MF 比較 ON 時追加列] | MF 残 | 差
  row_bg: anomaly.severity に応じて
</DataTable>
```

### 8.3 ハイライト実装 (R9 反映: 複数バッジ並列)

- 行背景色: `anomalies` の **最高 severity 優先**
  - CRITICAL 含む → red 薄背景
  - WARN のみ → orange 薄背景
  - INFO のみ → amber 薄背景
- ステータス列: **複数 anomaly を並列バッジ表示** (severity 別色 + 短ラベル)
  - UNV (red): UNVERIFIED
  - VDF (orange): VERIFY_DIFF
  - NEG (amber): NEGATIVE_CLOSING
  - POV (amber): PAYMENT_OVER
  - BRK (red): CONTINUITY_BREAK
  - GAP (orange): MONTH_GAP
  - MFX (amber): MF_DELTA_MISMATCH (MF 比較結果あり時のみ)
- `hasPaymentOnly=true` は anomaly とは別に「支払のみ」badge (slate、R10 反映)
- tooltip で anomaly.message を全文表示 (Radix UI の Tooltip 利用)

### 8.4 型定義

```typescript
// types/accounts-payable-ledger.ts
export interface SupplierInfo { /* ... */ }
export interface LedgerRow { /* ... */ }
export type AnomalyCode = 'UNVERIFIED' | 'VERIFY_DIFF' | 'NEGATIVE_CLOSING'
                        | 'PAYMENT_OVER' | 'PAYMENT_ONLY' | 'CONTINUITY_BREAK' | 'MF_MISMATCH'
export type AnomalySeverity = 'CRITICAL' | 'WARN' | 'INFO'
export interface Anomaly {
  code: AnomalyCode
  severity: AnomalySeverity
  message: string
}
export interface LedgerSummary { /* ... */ }
export interface AccountsPayableLedgerResponse {
  supplier: SupplierInfo
  fromMonth: string
  toMonth: string
  rows: LedgerRow[]
  summary: LedgerSummary
}
export interface MfLedgerRow { /* ... */ }
export interface MfSupplierLedgerResponse { /* ... */ }
```

### 8.5 既存 `/finance/accounts-payable` の仕入先名リンク追加 (R12 反映)

現状 `accounts-payable.tsx` の supplierName 列は `/purchases` にリンクしている (既存 E2E が href をアサートする可能性あり)。

変更方針:
- **supplierCode 列を `/purchases` リンク** に移動 (仕入明細への既存導線を維持)
- **supplierName 列を `/finance/accounts-payable-ledger` リンク** に変更 (買掛帳への新規導線)
- 両方とも新タブで開く (`target="_blank"`)
- E2E 影響: `/purchases` アサートは supplierCode 列側に書き換え必要 (既存 accounts-payable.spec.ts を確認)

### 8.6 Sidebar menu

```
経理 >
  ...
  買掛金 (/finance/accounts-payable)
  買掛帳 (/finance/accounts-payable-ledger)   ← 新設 (アイコン: BookOpen)
  ...
```

---

## 9. anomaly 検出実装詳細

```java
List<Anomaly> detectAnomalies(LedgerRow current, LedgerRow previousMonth) {
    List<Anomaly> out = new ArrayList<>();

    // UNVERIFIED
    if (current.changeTaxIncluded.signum() > 0 && current.verifiedAmount.signum() == 0) {
        out.add(Anomaly.of("UNVERIFIED", "WARN", "当月仕入あり・検証金額なし"));
    }

    // VERIFY_DIFF (verified > 0 の場合のみ)
    if (current.verifiedAmount.signum() != 0) {
        BigDecimal diff = current.changeTaxIncluded.subtract(current.verifiedAmount).abs();
        if (diff.compareTo(BigDecimal.valueOf(100)) > 0) {
            out.add(Anomaly.of("VERIFY_DIFF", "WARN", "仕入と検証額に差: \" + diff));
        }
    }

    // NEGATIVE_CLOSING (値引繰越 or 支払超過)
    if (current.closingBalanceTaxIncluded.signum() < 0) {
        if (current.hasPaymentOnly) {
            out.add(Anomaly.of("PAYMENT_OVER", "INFO", "支払超過 (payment-only 行で残高負)"));
        } else {
            out.add(Anomaly.of("NEGATIVE_CLOSING", "INFO", "累積残が負 (値引繰越)"));
        }
    }

    // PAYMENT_ONLY (badge 用)
    if (current.hasPaymentOnly) {
        out.add(Anomaly.of("PAYMENT_ONLY", "INFO", "当月支払のみ (仕入無し)"));
    }

    // CONTINUITY_BREAK
    if (previousMonth != null) {
        BigDecimal diff = current.openingBalanceTaxIncluded
            .subtract(previousMonth.closingBalanceTaxIncluded).abs();
        if (diff.compareTo(BigDecimal.ONE) > 0) { // ±1 円許容
            out.add(Anomaly.of("CONTINUITY_BREAK", "CRITICAL",
                "前月 closing との継続不整合: 前月末 \" + previousMonth.closingBalanceTaxIncluded
                + " ≠ 当月 opening \" + current.openingBalanceTaxIncluded));
        }
    }

    return out;
}
```

MF 比較 ON 時は UI 側で:
```typescript
if (Math.abs(self.closing - mf.closing) > 10000) {
    anomalies.push({ code: 'MF_MISMATCH', severity: 'WARN', ... })
}
```

---

## 10. テスト観点

### Backend

- supplier が存在しない → 404 / 空 response
- fromMonth > toMonth → 400
- 期間が 24 ヶ月超 → 400
- 非 admin が他 shop 指定 → 自 shop に強制 (assertShopAccess)
- 期間内に summary が 0 件 → 空 rows, summary 0
- 期間の途中月に summary が欠落 (月抜け) → その月は rows に含まれない (表示 skip)
- 税率別が複数 → 合算・代表値ロジック動作
- is_payment_only=true 行 → hasPaymentOnly=true, taxRateCount=1
- CONTINUITY_BREAK 検出: 人為的に opening/closing を崩して確認
- MF Service: sub_account 名一致・不一致両パターン
- MF Service: MF 認証切れ → 401
- MF Service: scope 不足 → 403

### Frontend E2E

- 仕入先選択 → テーブル表示
- URL クエリパラメータ保持
- MF 比較 ON → 追加列表示 + loading + 結果表示
- MF 比較 OFF 切替 → 列消滅、リロードなし
- anomaly バッジ表示色 (UNVERIFIED=red, NEGATIVE_CLOSING=amber など)
- /accounts-payable からの遷移リンクが動作
- sidebar menu 追加が admin/非admin でそれぞれ可視

---

## 11. 実装順序

1. Repository に `findBySupplierAndMonthRange` 追加
2. DTO 2 本 新設 (`AccountsPayableLedgerResponse`, `MfSupplierLedgerResponse`)
3. Service 新設 (`AccountsPayableLedgerService`)
4. Service 新設 (`MfSupplierLedgerService`)
5. FinanceController に 2 endpoint 追加
6. Frontend types 新設
7. Frontend page 新設 (`/finance/accounts-payable-ledger`)
8. Sidebar menu 追加
9. 既存 `/finance/accounts-payable` の supplierName リンク差し替え
10. コンパイル確認
11. 動作確認 (自動バッチ再起動 → curl + UI 確認)

---

## 12. リスクと軽減

| リスク | 軽減策 |
|---|---|
| MF /journals 取得が遅い | fromMonth/toMonth 必須、期間 24 ヶ月上限、UI は任意取得 |
| sub_account 名の表記揺れ (カミ商事 vs ｶﾐ 商事) | exact match のみ第 1 版、不一致時 UI で候補提示 |
| DoS: 大量 supplier × 長期間 | API 期間制限 + per request 1 supplier |
| 既存 accounts-payable のリンク変更で E2E 壊れる | テキストリンクは変えずクリック先の URL 変更、テストはアサート緩い |
| 非 admin が他 shop の買掛帳見る | `assertShopAccess` 流用 |
| MF /journals の pagination 失敗 | 既存 `MfApiClient.listJournals` の per_page=1000 + ループ |

---

## 13. 既存機能への影響

| 機能 | 影響 | 対処 |
|---|---|---|
| `/finance/accounts-payable` 一覧 | supplierName リンクの遷移先変更 | 既存機能 (値引繰越表示等) は維持 |
| Phase B' payment_settled 算出 | 無改修 (API は DB から読むだけ) | ? |
| MfBalanceReconcileService | 無改修 (独立 Service 追加) | ? |
| MfJournalReconcileService | 無改修 (別 endpoint) | ? |
| `SmilePaymentVerifier` | 無改修 | ? |

---

## 14. 将来拡張候補

- **税率別 expand**: row 右端のアイコンで税率別内訳を展開
- **仕入先名のあいまいマッチング**: Levenshtein or 正規化比較
- **CSV/Excel エクスポート**: 買掛帳として保存
- **複数仕入先比較**: multi-select で複数 supplier の推移を横並び
- **グラフ表示**: 月次残の推移チャート
- **annotation**: 異常月にコメントを残せる機能
The specified drive root "C:\Users\kazuki\AppData\Local\Temp\" either does not exist, or it is not a folder.
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content claudedocs\\design-supplier-balances-health.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content claudedocs\\design-consistency-review.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content claudedocs\\design-phase-b-prime-payment-settled.md" in C:\project\odamitsu-data-hub
2026-05-04T01:37:43.715059Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance,backend\\src\\main\\java\\jp\\co\\oda32\\batch\\finance,backend\\src\\main\\java\\jp\\co\\oda32\\batch\\finance\\service,backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\mf -Filter *.java | Select-String -Pattern '20|303|shopNo|shop_no|paymentSettled|payment_settled|MF_APPLY|IGNORE|verified|opening|closing|taxRate|税率|audit|createdBy|updatedBy|partial|debit|credit|refund|discount|discount|値引|返品|merge|supplierCode|supplierNo' | Select-Object Path,LineNumber,Line"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance,backend\\src\\main\\java\\jp\\co\\oda32\\batch\\finance,backend\\src\\main\\java\\jp\\co\\oda32\\batch\\finance\\service,backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\mf -Filter *.java | Select-String -Pattern '20|303|shopNo|shop_no|paymentSettled|payment_settled|MF_APPLY|IGNORE|verified|opening|closing|taxRate|税率|audit|createdBy|updatedBy|partial|debit|credit|refund|discount|discount|値引|返品|merge|supplierCode|supplierNo' | Select-Object Path,LineNumber,Line" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance,backend\\src\\main\\java\\jp\\co\\oda32\\batch\\finance,backend\\src\\main\\java\\jp\\co\\oda32\\batch\\finance\\service,backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\mf -Filter *.java | Select-String -Pattern '20|303|shopNo|shop_no|paymentSettled|payment_settled|MF_APPLY|IGNORE|verified|opening|closing|taxRate|税率|audit|createdBy|updatedBy|partial|debit|credit|refund|discount|discount|値引|返品|merge|supplierCode|supplierNo' | Select-Object Path,LineNumber,Line"` rejected: blocked by policy
 succeeded in 1676ms:
# 軸 D+E: supplier 累積残一覧 + MF 連携ヘルスチェック 設計書

作成日: 2026-04-23
対象ブランチ: `refactor/code-review-fixes` (継続)
関連設計書:
- `design-supplier-partner-ledger-balance.md` (Phase A/B/B''(light) 累積残基盤)
- `design-accounts-payable-ledger.md` (買掛帳画面)
- `design-integrity-report.md` (整合性検出、軸 B+C)

---

## 1. 背景と目的

### 軸 D: supplier 累積残一覧
整合性レポート (軸 B+C) は期間 delta の突合のみで、「**現時点で supplier ごとの買掛金累積残がいくらで、MF 累積残と合っているか**」を 1 画面で見られない。
- 既存: 1 supplier の月次推移は買掛帳画面で見られる
- 既存: 全 supplier の期間 delta は整合性レポートで見られる
- **欠落**: 全 supplier の累積残 (opening + Σ change ? Σ payment_settled) の一覧

### 軸 E: MF 連携ヘルスチェック
MF 連携の状態を確認するのに 4 画面 (MF 連携状況 / 整合性レポート / 買掛金一覧 / 買掛帳) を行き来する必要がある。
- 管理者が朝一で「今 MF 連携は健全か?」を 1 画面で把握できるダッシュボードが欲しい

---

## 2. スコープ

### 軸 D: `/finance/accounts-payable-ledger/supplier-balances`
- 全 supplier の (selfBalance, mfBalance, diff, status) を 1 行で一覧表示
- `asOfMonth` パラメータで「選択月の 20 日締め時点」を指定可能 (デフォルト: 最新月)
- 行クリック → 買掛帳画面 drill-down
- MF journals キャッシュ共有で 2 回目以降は高速

### 軸 E: `/finance/mf-health`
- MF OAuth 状態 / token 期限
- 最新の整合性サマリ (mfOnly / selfOnly / mismatch)
- 未検証サマリ件数 / アノマリー内訳
- MF journals キャッシュ状態 (shop × 月数 × 最古取得時刻)

---

## 3. 軸 D: 詳細仕様

### 3.1 API
```
GET /api/v1/finance/accounts-payable/supplier-balances
  ?shopNo=1
  &asOfMonth=2026-03-20     (省略時は最新月)
  &refresh=false
```

**Response**:
```json
{
  "shopNo": 1,
  "asOfMonth": "2026-03-20",
  "fetchedAt": "2026-04-23T10:00:00Z",
  "totalJournalCount": 8500,
  "rows": [
    {
      "supplierNo": 10,
      "supplierCode": "007000",
      "supplierName": "泉製紙(株)",
      "selfBalance": 2884795,
      "mfBalance": 2884794,
      "diff": 1,
      "status": "MATCH",
      "selfOpening": 1500000,
      "selfChangeCumulative": 5000000,
      "selfPaymentCumulative": 3615205,
      "mfCreditCumulative": 5000001,
      "mfDebitCumulative": 2115207,
      "mfSubAccountNames": ["泉製紙(株)"]
    }
  ],
  "summary": {
    "totalSuppliers": 51,
    "matchedCount": 30,
    "minorCount": 15,
    "majorCount": 6,
    "mfMissingCount": 0,
    "selfMissingCount": 0,
    "totalSelfBalance": 15448920,
    "totalMfBalance": 15448920,
    "totalDiff": 0
  }
}
```

### 3.2 判定ロジック (整合性レポートと同じ閾値・同じ label)
- `MATCH`: |diff| ? \100
- `MINOR`: \100 < |diff| ? \1,000
- `MAJOR`: |diff| > \1,000
- `MF_MISSING`: self あり MF なし
  - `masterRegistered=false`: mf_account_master 未登録
  - `masterRegistered=true`: master 登録あるが期間内 journals 0
  - UI 側で `masterRegistered` フラグにより文言を分岐
- `SELF_MISSING`: MF あり self なし (自社 supplier 未登録 or summary 未計上)

**金額単位**: 全て **税込・整数円・BigDecimal (scale=0, RoundingMode.HALF_UP)**。

### 3.3 データソース
- **selfBalance** (asOfMonth 時点の税込累積残):
  - `t_accounts_payable_summary` WHERE shop_no = X AND transaction_month = asOfMonth
  - 税率別行を supplier × asOfMonth で合算、各行は `PayableBalanceCalculator.closingBalanceTaxIncluded(row)` で算出 (既存ロジック流用、手動確定行は verifiedAmount 優先)
  - 存在しない supplier は selfBalance=0 として、MF 側のみ行として `SELF_MISSING` 分類
- **selfOpening / selfChangeCumulative / selfPaymentCumulative** (診断値、期間分解):
  - `selfOpening = 期首月 (2025-05-20) の openingBalanceTaxIncluded` (supplier 単位)
  - `selfChangeCumulative = Σ PayableBalanceCalculator.effectiveChangeTaxIncluded(row)` (期首?asOfMonth、supplier 単位)
  - `selfPaymentCumulative = Σ paymentAmountSettledTaxIncluded` (期首?asOfMonth、supplier 単位)
  - 恒等式: `selfBalance == selfOpening + selfChangeCumulative ? selfPaymentCumulative`
- **mfBalance** (asOfMonth 時点の supplier 単位累積 delta):
  - MF /journals を「期首 (2025-05-20) ? asOfMonth」で取得 (`MfJournalCacheService.getOrFetch` 経由)
  - 買掛金 sub_account を supplier 単位に集約、`Σ credit ? Σ debit` の cumsum を返す
  - **制約**: MF /journals には期首前 (2025-05-20 以前) の残高情報が含まれない (trial_balance_bs は sub_account 粒度無し、Phase 0 スパイクで確定)。supplier 単位の「期首前繰越」は取得不可
  - **対称性**: 自社側も期首月 openingBalance を個別に加算するため、**期首以降の delta (期首月以降のΣ) のみで突合**される。期首前の繰越差は diff として表出しない
  - 期首月 opening が 0 でない supplier (既存繰越あり) は、その opening が self 側のみに現れるため期首月の単月比較では差が出るが、本 endpoint は asOfMonth 時点の累積差のみを返す。詳細は buying ledger 画面の月次推移で確認
- **supplier 紐付け**:
  - `mf_account_master.search_key == payment_supplier_code` (account_name="買掛金", financial_statement_item="買掛金" に限定)
  - 既存 `AccountsPayableIntegrityService` と同じロジック、helper を切り出して共有化

### 3.4 期間
- fromMonth は **MF 会計期首 (2025-05-20)** 固定
- toMonth = asOfMonth
- fiscal year 境界は `MfJournalFetcher.buildStartDateCandidates` (30 候補 fallback) に一任
- Response に `mfStartDate`: `MfJournalFetcher.FetchResult.actualStart` を含め、実採用開始日を UI に返す (透明性確保)

### 3.5 asOfMonth 省略時の挙動
- `findLatestMonth(shopNo)` で `t_accounts_payable_summary` の最大 transaction_month を取得
- summary が空の shop の場合: 200 で `{rows: [], asOfMonth: null, summary: {totalSuppliers: 0, ...}}` を返す (404 ではない、UI で「データがありません」表示)
- asOfMonth が 20 日締めでない場合: 400 返却 (`dayOfMonth != 20` バリデーション)

### 3.6 性能見積もり (supplier 集計込み)
- 初回 (期首?現時点 11 ヶ月): journals ~8,500 件、5-10 秒 (大半は MF API fetch)
- 2 回目 (キャッシュ hit): **< 500ms** (MF API 通信なし + supplier × 月 の Map index 化 O(journals + supplier × subAccount) で集計)
- supplier 集計は `subAccountName → MonthBucket` の Map 事前構築で O(1) lookup にする (既存 `AccountsPayableIntegrityService` と同構造)

---

## 4. 軸 E: 詳細仕様

### 4.1 API
```
GET /api/v1/finance/mf-health?shopNo=1
```

**Response**:
```json
{
  "checkedAt": "2026-04-23T10:00:00Z",
  "mfOauth": {
    "connected": true,
    "tokenExpiresAt": "2026-04-30T12:00:00Z",
    "scope": "mfc/accounting/data.read mfc/accounting/report.read",
    "expiresInHours": 168
  },
  "summary": {
    "latestMonth": "2026-03-20",
    "totalCount": 1800,
    "verifiedCount": 1650,
    "unverifiedCount": 150,
    "mfExportEnabledCount": 1600,
    "shopNo": 1
  },
  "anomalies": {
    "negativeClosingCount": 2,
    "unverifiedCount": 150,
    "verifyDiffCount": 5,
    "continuityBreakCount": 0,
    "monthGapCount": 0
  },
  "cache": {
    "cachedShops": [
      {
        "shopNo": 1,
        "monthsCount": 12,
        "oldestFetchedAt": "2026-04-23T09:55:00Z",
        "newestFetchedAt": "2026-04-23T10:00:00Z"
      }
    ]
  }
}
```

### 4.2 集計ロジック
- **mfOauth**: `MMfOauthClient` から token expires_at 計算 (既存 `MfOauthService` から取得)
- **summary**: `t_accounts_payable_summary` 最新月の集計 (shop 指定)
- **anomalies**: 既存 `PayableLedgerService` の anomaly 検出と**重複実装しない**。共通 util `PayableAnomalyCounter.count(shopNo)` を新設 (既存買掛帳画面ロジックを静的呼び出し可能な形に切り出し)、集計結果を以下の形で返す:
  - `negativeClosingCount`: closing < 0 の月数
  - `unverifiedCount`: 最新月で verification_result = 0 の行数
  - `verifyDiffCount`: VERIFY_DIFF anomaly 数 (買掛帳と同じ閾値)
  - `continuityBreakCount`, `monthGapCount`: 既存 anomaly code 準拠
- **cache**: `MfJournalCacheService.getStats()` (non-synchronized、ConcurrentHashMap snapshot read) → `List<ShopStats>` を返す

### 4.3 ヘルス判定 (UI 側)
- ?? 要対応: token 期限切れ OR negativeClosingCount > 0 OR anomaly 合計 > 10
- ?? 注意: token 残り 24 時間以内 OR unverifiedCount > 0 OR anomaly 合計 > 0
- ?? 健全: 上記以外 (token 有効 AND anomaly=0 AND unverified=0)

※ unverified は当月分を運用上「確定前」と見なすため軽い警告扱い、negative は計算エラー / 値引超過の可能性があるため重度警告扱い。

---

## 5. バックエンド実装

### 5.1 新規 Service
- `backend/.../domain/service/finance/SupplierBalancesService.java`
  - `generate(shopNo, asOfMonth, refresh) : SupplierBalancesResponse`
  - 自社 summary 集計 + MF /journals 累積 + 突合 + 判定
- `backend/.../domain/service/finance/MfHealthCheckService.java`
  - `check(shopNo) : MfHealthResponse`
  - 上記集計を 1 メソッドにまとめる

### 5.2 MfJournalCacheService 拡張
```java
public record CacheStats(List<ShopStats> shops) {}
public record ShopStats(Integer shopNo, int monthsCount, Instant oldest, Instant newest) {}
public CacheStats getStats() { ... }
```

### 5.3 DTO
- `backend/.../dto/finance/SupplierBalancesResponse.java`
  - `SupplierBalanceRow`, `SupplierBalancesSummary` inner class
- `backend/.../dto/finance/MfHealthResponse.java`
  - `MfOauthStatus`, `SummaryStats`, `AnomalyStats`, `CacheStats` inner class

### 5.4 Repository 追加メソッド
```java
// TAccountsPayableSummaryRepository
List<TAccountsPayableSummary> findByShopNoAndTransactionMonth(Integer shopNo, LocalDate month);
@Query("SELECT COUNT(s) FROM TAccountsPayableSummary s WHERE s.shopNo = :shopNo AND s.closingBalanceTaxIncluded < 0")
long countNegativeClosings(@Param("shopNo") Integer shopNo);
@Query("SELECT COUNT(s) FROM TAccountsPayableSummary s WHERE s.shopNo = :shopNo AND s.verificationResult = 0")
long countUnverified(@Param("shopNo") Integer shopNo);
@Query("SELECT MAX(s.transactionMonth) FROM TAccountsPayableSummary s WHERE s.shopNo = :shopNo")
Optional<LocalDate> findLatestMonth(@Param("shopNo") Integer shopNo);
```

### 5.5 Controller
```java
@GetMapping("/accounts-payable/supplier-balances")
public ResponseEntity<?> getSupplierBalances(
    @RequestParam Integer shopNo,
    @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate asOfMonth,
    @RequestParam(defaultValue = "false") boolean refresh) {
  assertShopAccess(shopNo);  // shop 越境防御
  // asOfMonth != null なら day != 20 で 400、null なら findLatestMonth() で決定
  // 401/403/422 エラーハンドリングは既存 endpoint と同じ
}

@GetMapping("/mf-health")
public ResponseEntity<?> getMfHealth(@RequestParam Integer shopNo) {
  assertShopAccess(shopNo);
  // MF OAuth 状態、集計、anomaly、cache stats
}

@PostMapping("/mf-health/cache/invalidate")
public ResponseEntity<Void> invalidateCache(@RequestParam Integer shopNo) {
  assertShopAccess(shopNo);
  mfJournalCacheService.invalidateAll(shopNo);
  return ResponseEntity.noContent().build();
}
```

---

## 6. フロントエンド実装

### 6.1 新規 Page
- `app/(authenticated)/finance/accounts-payable-ledger/supplier-balances/page.tsx`
- `app/(authenticated)/finance/mf-health/page.tsx`

### 6.2 新規 Component
- `components/pages/finance/supplier-balances.tsx` (一覧 + フィルタ + ソート)
- `components/pages/finance/mf-health.tsx` (ダッシュボード)

### 6.3 新規 Type
- `types/supplier-balances.ts`
- `types/mf-health.ts`

### 6.4 UI 仕様
**軸 D (supplier-balances.tsx)**:
- 検索フォーム: ショップ / asOfMonth (month input) / [整合性チェック] [最新取得] ボタン
- Summary カード: 5 タイル (MATCH / MINOR / MAJOR / MF_MISSING / SELF_MISSING) + totalSelfBalance / totalMfBalance / totalDiff
- テーブル: supplier | selfBalance | mfBalance | diff | status badge | 詳細列
- 行クリック → 買掛帳画面 drill-down (supplier=X, fromMonth=asOfMonth-12months, toMonth=asOfMonth)
- status フィルタ (MATCH 除外トグル / MAJOR のみトグル等)

**軸 E (mf-health.tsx)**:
- 全体ヘルス判定 (??/??/??) を大きく表示
- 4 カード並列:
  - MF OAuth: 接続状態 / scope / 有効期限 (残り時間)
  - サマリ: total / verified / unverified / mfExportEnabled
  - アノマリー: negativeClosing / verifyDiff / continuityBreak / monthGap
  - キャッシュ: shop × 月数 × 最古 fetchedAt / キャッシュクリアボタン
- リンク: 各指標から詳細画面 (整合性レポート / 買掛金一覧 / 買掛帳) に遷移

### 6.5 Sidebar 追加
`components/layout/Sidebar.tsx` の親メニュー「買掛帳」(`/finance/accounts-payable-ledger`) **配下の子メニュー**として以下を追加:
- `累積残一覧` → `/finance/accounts-payable-ledger/supplier-balances`
- `整合性レポート` → `/finance/accounts-payable-ledger/integrity` (既存、親子整理のため明記)

別階層 (MF 系メニュー配下) に:
- `MF 連携ヘルスチェック` → `/finance/mf-health`

**注意**: Sidebar の `isMenuActive` ロジック (親子 path 衝突対応) に準拠。`/finance/accounts-payable-ledger` と `/finance/accounts-payable-ledger/supplier-balances` の両方を開いても、現在画面に対応する子メニューだけが active になるよう検証する (MEMORY.md の Sidebar isMenuActive Logic 参照)。

---

## 7. テスト計画 (要点のみ)

### 7.1 バックエンド unit test
- `SupplierBalancesService.generate()`:
  - self のみ存在 → MFM_SELF
  - MF のみ存在 → MFM_MF
  - 両方存在・MATCH / MINOR / MAJOR 各判定
  - asOfMonth が最新月以降 → `findLatestMonth` fallback
- `MfHealthCheckService.check()`:
  - OAuth 未接続状態
  - anomaly 検出精度
  - cache stats 集計

### 7.2 統合テスト (curl smoke)
- `/supplier-balances` 200 + summary 内容整合
- `/mf-health` 200 + 各フィールド存在
- `refresh=true` でキャッシュ再取得
- 401/403 エラーハンドリング

### 7.3 E2E (Playwright)
- `/supplier-balances` 画面で行クリック → 買掛帳遷移
- `/mf-health` 画面で健全性アイコン表示
- 「キャッシュクリア」ボタン → 再整合性チェックで API 通信発生

---

## 8. 実装順序

1. **軸 D バックエンド** (Service + Controller + DTO + Repository) ? 3h
2. **軸 D フロント** (Page + Component + Type + Sidebar) ? 2h
3. **軸 E バックエンド** (MfJournalCacheService 拡張 + Service + Controller + DTO) ? 2h
4. **軸 E フロント** (ダッシュボード + Sidebar) ? 2h
5. **コードレビュー (subagent)** + 修正 ? 1h
6. **smoke test (curl + UI)** ? 30min
7. **commit** ? 1 つにまとめる

---

## 9. 非スコープ (明示的除外)
- 売掛金 (軸 D' = Phase C) は対象外
- キャッシュの永続化 (DB 化) は不要
- 軸 E のアラート通知 (Slack / email) は不要
- 過去の整合性レポート履歴保存は不要 (毎回 on-demand)

---

## 10. 設計上の決定事項
- MF /journals の fromMonth は常に期首 (2025-05-20) 固定
- 軸 D は asOfMonth 時点での closing で比較 (累積差の時点スナップショット)
- 軸 E は shopNo 1 スコープ (現在 B-CART は shop=1 計上)
- ダッシュボードの自動更新は手動リロードのみ (WebSocket / polling は不要)
The specified drive root "C:\Users\kazuki\AppData\Local\Temp\" either does not exist, or it is not a folder.
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1730ms:
# 整合性レポート 差分確認機能 (案 X + Y) 設計書 v2

作成日: 2026-04-23 (v2 = 設計レビュー B1-B3, C1-C4, M1-M5 反映)
ブランチ: `refactor/code-review-fixes` 継続
関連: `design-integrity-report.md` / `design-supplier-balances-health.md` / `feature-integrity-report.md`

---

## 1. 背景と目的

### 課題
整合性レポートで検出された差分について、MF or 自社を修正しても次回再表示される。「対応済み」「未対応」を区別したい。

### ゴール
差分行に 2 つの操作を提供:
1. **IGNORE** ? 確認済みマーク、非表示化 (MF 側を手修正済み等の場合もこれで代替)
2. **MF_APPLY** ? MF 金額で自社 verified_amount を上書き → 次回 MATCH 化

snapshot 比較で金額再変動時は自動再表示。DELETE でロールバック可能。

**※ v1 の SELF_APPLY は廃止** (IGNORE と副作用が同じため M1 指摘で統合)。

---

## 2. スコープ

### 対象カテゴリ
整合性レポート 3 カテゴリ (mfOnly / selfOnly / amountMismatch)。

### 対象単位 (B1 対応)
mfOnly は `supplier_no=null` ケース (MF 手入力仕訳) があるため、PK は supplier_no/sub_account_name を統合した `entry_key` を使用:

- `selfOnly`, `amountMismatch`: `entry_key = supplier_no.toString()`
- `mfOnly`: `entry_key = guessedSupplierNo?.toString() ?? sub_account_name`

### 非スコープ
- **mfOnly の MF_APPLY**: 自社に supplier 行がないケースで summary 新規作成は複雑。初版は **UI で MF_APPLY ボタン無効化** (M4)
- bulk 操作 (後続改修)
- MF journal の API 経由自動修正 (危険)

---

## 3. API 設計

### 3.1 POST (新規/更新 Upsert)
```
POST /api/v1/finance/accounts-payable/integrity-report/reviews
```
**Request**:
```json
{
  "shopNo": 1,
  "entryType": "mfOnly",          // mfOnly | selfOnly | amountMismatch
  "entryKey": "73",                // supplier_no 文字列 or sub_account_name
  "transactionMonth": "2025-08-20",
  "actionType": "IGNORE",          // IGNORE | MF_APPLY (SELF_APPLY 廃止)
  "selfSnapshot": 0,               // 下記 §7 の定義通り
  "mfSnapshot": 1089,
  "note": "MF #2419 削除済み"
}
```

**バリデーション** (m4 対応):
- note: `@Size(max = 500)`
- entryType: enum validation
- actionType: enum validation
- mfOnly + actionType=MF_APPLY → 400 Bad Request (B1/M4 対応)

### 3.2 アクション別処理と副作用 (B2, C1 対応)

#### IGNORE
- review upsert のみ、副作用なし
- 既存 review が MF_APPLY だった場合は **verified_amount を previous から復元** (C1)

#### MF_APPLY
| entryType | 副作用 |
|---|---|
| `selfOnly` | 該当 supplier × 月 の全税率行で `verified_amount=0, verified_amount_tax_excluded=0, verified_manually=true, verification_result=1, mf_export_enabled=false` に更新 (自社取消) |
| `amountMismatch` | 全税率行の合計 `target_included = mfSnapshot + payment_settled`。既存 `tax_included_amount_change` の比率で**税率別に按分して verified_amount を個別セット** (B2) |
| `mfOnly` | **許可しない** (UI で無効化、Backend で 400) |

**税率別按分の具体式** (amountMismatch、既存 applyVerification 準拠):
```
target = mfSnapshot + sum(payment_settled_tax_included)
changeSumIncl = sum(tax_included_amount_change)
for each rowが税率別:
  ratio = row.tax_included_amount_change / changeSumIncl
  row.verified_amount = target * ratio  (1 円未満切り捨て)
  row.verified_amount_tax_excluded = row.verified_amount * 100 / (100 + row.tax_rate) (切り捨て)
# 端数誤差は最大金額の行で吸収
```

### 3.3 ロールバック用スナップショット (C1)
`t_consistency_review` に `previous_verified_amounts JSONB` 列追加:
- MF_APPLY 時、対象行の既存 verified_amount を `{"10.00": 123, "8.00": 456}` 形式で保存
- DELETE or IGNORE 上書き時に復元

### 3.4 DELETE
```
DELETE /api/v1/finance/accounts-payable/integrity-report/reviews
  ?shopNo=1&entryType=mfOnly&entryKey=73&transactionMonth=2025-08-20
```
MF_APPLY で書き換えた verified_amount は `previous_verified_amounts` から復元。

---

## 4. DB 設計 (V027 migration)

```sql
CREATE TABLE t_consistency_review (
    shop_no                      INTEGER      NOT NULL,
    entry_type                   VARCHAR(20)  NOT NULL,  -- mfOnly | selfOnly | amountMismatch
    entry_key                    VARCHAR(255) NOT NULL,  -- supplier_no or sub_account_name
    transaction_month            DATE         NOT NULL,
    action_type                  VARCHAR(20)  NOT NULL,  -- IGNORE | MF_APPLY
    self_snapshot                NUMERIC,
    mf_snapshot                  NUMERIC,
    previous_verified_amounts    JSONB,                  -- MF_APPLY ロールバック用
    reviewed_by                  INTEGER      NOT NULL,
    reviewed_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),  -- m6 対応
    note                         VARCHAR(500),
    PRIMARY KEY (shop_no, entry_type, entry_key, transaction_month)
);

CREATE INDEX idx_consistency_review_shop_month_type
    ON t_consistency_review (shop_no, transaction_month, entry_type);  -- C4 対応

COMMENT ON TABLE t_consistency_review IS
    '整合性レポート 差分確認履歴。PK の entry_key は selfOnly/amountMismatch では supplier_no 文字列、mfOnly では guessedSupplierNo または sub_account_name。';
```

---

## 5. Backend 実装

### 5.1 Entity
- `TConsistencyReview` (Embedded PK: `TConsistencyReviewPK`)
- JSONB は `@Type(JsonType.class)` (hypersistence-utils) で `Map<String, BigDecimal>` として扱う

### 5.2 Repository
```java
public interface TConsistencyReviewRepository
        extends JpaRepository<TConsistencyReview, TConsistencyReviewPK> {

    @Query("SELECT r, u.userName FROM TConsistencyReview r " +
           "LEFT JOIN MLoginUser u ON u.loginUserNo = r.reviewedBy " +
           "WHERE r.shopNo = :shopNo " +
           "  AND r.transactionMonth BETWEEN :fromMonth AND :toMonth")
    List<Object[]> findWithReviewerName(
        @Param("shopNo") Integer shopNo,
        @Param("fromMonth") LocalDate fromMonth,
        @Param("toMonth") LocalDate toMonth);  // M3 対応 (N+1 回避)

    void deleteByShopNoAndEntryTypeAndEntryKeyAndTransactionMonth(
        Integer shopNo, String entryType, String entryKey, LocalDate transactionMonth);
}
```

### 5.3 Service
- `ConsistencyReviewService`
  - `upsert(req, userNo)`:
    - 既存 review 取得 → 旧 actionType が MF_APPLY なら先にロールバック
    - 新 actionType が MF_APPLY なら対象行の verified_amount 退避 + 上書き (B2 按分)
    - review insert/update
  - `delete(key)`: ロールバック + review 削除
  - `findForPeriod(shopNo, fromMonth, toMonth)`: `Map<ReviewKey, ReviewDto>` 返却 (M3 join 済み)

### 5.4 整合性レポート拡張
`AccountsPayableIntegrityService.generate()` で各 entry に以下付与:
- `reviewStatus`: null / `IGNORED` / `MF_APPLIED` (m1 対応: reviewStatus は過去形、action_type は動詞で分離)
- `reviewedAt`, `reviewedByName`, `reviewNote`
- `snapshotStale`: 現在値と snapshot の差が **±\100 超** なら true (C3 対応、MATCH_TOLERANCE と揃える)

### 5.5 Controller
```java
@PostMapping("/accounts-payable/integrity-report/reviews")
public ResponseEntity<?> saveReview(@RequestBody @Valid ConsistencyReviewRequest req) {
    assertShopAccess(req.getShopNo());  // M2 対応
    if ("mfOnly".equals(req.getEntryType()) && "MF_APPLY".equals(req.getActionType())) {
        throw new ResponseStatusException(BAD_REQUEST, "mfOnly の MF_APPLY は未対応");  // M4
    }
    return ResponseEntity.status(201).body(consistencyReviewService.upsert(req, currentUserNo()));
}

@DeleteMapping("/accounts-payable/integrity-report/reviews")
public ResponseEntity<Void> deleteReview(
        @RequestParam Integer shopNo, @RequestParam String entryType,
        @RequestParam String entryKey,
        @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate transactionMonth) {
    assertShopAccess(shopNo);
    consistencyReviewService.delete(shopNo, entryType, entryKey, transactionMonth);
    return ResponseEntity.noContent().build();
}
```

---

## 6. Frontend 実装

### 6.1 UI 変更
各 entry 行に 2 ボタン:
- `? 確認済み` (IGNORE)
- `→ MF で確定` (MF_APPLY, mfOnly では無効化)

Confirm dialog + note input 必須。

**DELETE 操作**: 確認済み行の「取り消し」リンクで review 削除 + verified_amount 復元。

### 6.2 表示制御
- デフォルト: `reviewStatus != null AND snapshotStale=false` は非表示
- トグル「確認済みを含む」で全件表示
- `snapshotStale=true` は確認済みでも再表示 (金額変動検出)

### 6.3 Type
```typescript
interface ReviewInfo {
  reviewStatus: 'IGNORED' | 'MF_APPLIED' | null
  reviewedAt: string | null
  reviewedByName: string | null
  reviewNote: string | null
  snapshotStale: boolean
}
// 既存 MfOnlyEntry / SelfOnlyEntry / AmountMismatchEntry に埋め込み
```

---

## 7. Snapshot 比較ロジック (B3 明確化)

各 entry の snapshot 保存値と現在値の定義:

| entryType | self_snapshot | mf_snapshot | 現在値 self / mf | stale 判定 |
|---|---|---|---|---|
| mfOnly | 0 | periodDelta (= credit ? debit) | 0 / credit ? debit | `|mf_current ? mf_snapshot| > \100` |
| selfOnly | selfDelta (= change ? payment) | 0 | selfDelta / 0 | `|self_current ? self_snapshot| > \100` |
| amountMismatch | selfDelta | mfDelta | selfDelta / mfDelta | `|self_current ? self_snapshot| > \100 OR |mf_current ? mf_snapshot| > \100` |

**stale 閾値**: MATCH_TOLERANCE (±\100) と一致 (C3)。

---

## 8. Upsert/Delete 副作用フロー (C2 明確化)

```
upsert(req):
  old = findByPK
  if old != null AND old.actionType == MF_APPLY:
    rollbackVerifiedAmount(old.previous_verified_amounts)  # ロールバック先行
  if req.actionType == MF_APPLY:
    previous = captureCurrentVerifiedAmounts()  # JSONB に保存
    applyMfOverride(req)
  save review(req, previous)

delete(pk):
  old = findByPK
  if old != null AND old.actionType == MF_APPLY:
    rollbackVerifiedAmount(old.previous_verified_amounts)
  delete review
```

---

## 9. パフォーマンス (C4)
- review 取得は `findWithReviewerName` で 1 クエリ (JOIN fetch)
- 現状 /integrity-report 8-15s のうち review join は < 100ms 想定
- review テーブル想定規模: 数百 ? 数千行。index `(shop_no, transaction_month, entry_type)` で期間 + カテゴリ絞り込み O(log n)

---

## 10. テスト計画

### Unit
- `upsert`:
  - IGNORE 新規 → review insert、verified_amount 無変更
  - MF_APPLY (selfOnly) → 全税率行 verified_amount=0、previous 保存
  - MF_APPLY (amountMismatch) → 税率別按分 verified_amount 設定
  - mfOnly + MF_APPLY → 400
  - IGNORE → MF_APPLY 切替 → verified_amount 上書き
  - MF_APPLY → IGNORE 切替 → ロールバック
- `delete`: MF_APPLY 行を削除 → ロールバック

### Integration
- POST 201 + DB insert 確認
- DELETE 204 + 行消失 + verified_amount 復元

### E2E
- 整合性レポートで [確認済み] ボタン → 非表示化
- 金額変動シミュレート (summary 手動更新) → 再表示確認
- [MF で確定] → 該当 supplier 月が次回レポートで消える

---

## 11. 実装順序

1. Migration V027 + Entity + PK + Repository (40 min)
2. ConsistencyReviewService (upsert / delete / rollback / 按分ロジック) (1.5 h)
3. Controller (POST / DELETE) (20 min)
4. 整合性レポートサービスに review 取得 + snapshotStale 判定組込み (1 h)
5. Frontend types + 2 ボタン + confirm + 表示制御 (1.5 h)
6. smoke (curl + UI 動作) (30 min)
7. commit (10 min)

**合計工数**: 約 5.5 時間 (レビュー指摘の按分/ロールバック/join fetch を反映して v1 の 4h から増加)

---

## 12. レビュー反映サマリ

| 指摘 | 対応 |
|---|---|
| B1 mfOnly supplier_no NULL | entry_key VARCHAR に統一、supplier_no/sub_account_name どちらかを格納 |
| B2 税率別按分 | amountMismatch で change 比按分、selfOnly で全行 0、mfOnly は MF_APPLY 不可 |
| B3 snapshot 定義 | §7 表で各 entryType の self/mf snapshot を明記、example JSON も合わせる |
| C1 DELETE ロールバック | previous_verified_amounts JSONB 列、DELETE/IGNORE 切替時に復元 |
| C2 upsert 副作用 | §8 フロー明示 |
| C3 ±\0 閾値 | ±\100 (MATCH_TOLERANCE) に変更 |
| C4 パフォーマンス | `(shop_no, transaction_month, entry_type)` index 追加 |
| M1 SELF_APPLY 廃止 | IGNORE に統合、note に「MF修正済」記載で代替 |
| M2 shop 越境 | assertShopAccess を DELETE でも呼ぶ、Repository query に shopNo 必須 |
| M3 N+1 | findWithReviewerName で JOIN fetch |
| M4 mfOnly MF_APPLY | UI 無効化 + Backend 400 |
| m1 命名 | reviewStatus は過去形 (IGNORED/MF_APPLIED)、action_type は動詞 |
| m4 note サイズ | `@Size(max=500)` |
| m6 タイムゾーン | TIMESTAMPTZ |
The specified drive root "C:\Users\kazuki\AppData\Local\Temp\" either does not exist, or it is not a folder.
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1791ms:
# Phase B' ? 買掛金残の正確な管理（payment_settled 列追加）設計書

作成日: 2026-04-22
対象ブランチ: `refactor/code-review-fixes`（Phase A の後続、同一ブランチで継続）
関連設計書: `design-supplier-partner-ledger-balance.md`（Phase 0/A/B の母体）

---

## 1. 背景と問題（真因の特定）

### Phase A 完了後の残課題

Phase A (2026-04-22 コミット `567f34d`) で `t_accounts_payable_summary` に `opening_balance_tax_included/excluded` を追加、バッチで前月 closing を繰越、フィルタ緩和 (`> 0 → != 0`) で値引超過行も保持するようにした。

仕訳突合 (`MfJournalReconcileService`) の 2026-03-20 PURCHASE diff は `+\2,973,089 → +\10` まで収束。

ただし Phase B の**残高突合** (`MfBalanceReconcileTab`) で重大な乖離が発覚:

| 指標 | 値 | 意味 |
|---|---|---|
| MF 買掛金 closing @ 2026-03-20 | \41,085,601 | **残高** (期首 + 仕入 ? 支払) |
| 自社累積残 (MF 突合対象) | \178,861,005 | **仕入累計** (opening + 仕入 change のみ) |
| 差分 | **?\137,775,404** | ≒ 期間中の支払累計 ? 期首買掛金残 |

### 根本原因

`t_accounts_payable_summary.tax_included_amount_change` は `t_purchase_detail` から集計した**仕入のみ**。支払取崩 (買掛金 debit) は DB に入っていない。

Phase A で追加した `opening = 前月 closing` のコピーも、前月 closing 自体が「仕入累計」なので、繰越しても「仕入累計」のまま。

**設計書 `design-supplier-partner-ledger-balance.md` §4 の closing 定義**:
```
closing = opening + effectiveChange   (effectiveChange は仕入のみ)
```
これが**買掛金残ではなく仕入累計を表していた**。MF 買掛金 account と直接突合できない。

### Phase B' の目的

**買掛金の T 勘定定義** (`closing = opening + 仕入 ? 支払`) を自社 summary に実装する。payment_settled 列を追加し、当月取崩額 (前月の確定請求額 `verified_amount`) を保持する。これにより MF 買掛金 closing との直接突合が可能になる。

---

## 2. 設計方針（ultrathink で検討）

### 2.1 選択肢評価

| 案 | 概要 | 評価 |
|---|---|---|
| **A. payment_settled 列を追加 (既存 summary 拡張)** | `t_accounts_payable_summary` に `payment_amount_settled_tax_{included,excluded}` を追加し、バッチで前月 `verified_amount` をコピー | **採用** ? 既存 PK とマッチ、税率別管理が自然、最小変更 |
| B. 別テーブル `t_accounts_payable_payment` 新設 | supplier 単位で支払を別管理、JOIN で残高算出 | 税率別管理ができない、JOIN コスト、既存 UI/Service への統合コストが高い |
| C. t_smile_payment を直接参照 (query-time) | View 経由で毎回 JOIN | パフォーマンス不安、voucher_date と transaction_month の対応が複雑 |
| D. 買掛帳 (取引元帳) フル実装 | journal 的な取引明細テーブル | 既存影響大、Phase A 設計で見送り済 |

→ **A 案採用**。既存 `verified_amount` を最大活用、税率別単位でそのまま対応可能。

### 2.2 payment_settled の算出ルール（レビュー R1 反映）

#### 既存 `verified_amount` の重要な性質

`PaymentMfImportService.applyVerification` は振込明細 Excel の **invoice (supplier 単位の合計値)** を、その supplier の**全税率行に同じ値として書き込む**実装 (L258-272):

> 振込明細の請求額は支払先単位で1件だが、DBは税率別に複数行ある場合がある。
> UI 表示用途のため、全税率行に同じ verified_amount を書き込む
> （税率別の請求額内訳は Excel 側に存在しないため、合計値を代表値として保持）。

既存 `sumVerifiedAmountForGroup` (L402-414) はこの事実を前提に **「全行一致なら代表値、不一致なら SUM」** で MF 出力金額を算出している。

**naive に tax_rate 別コピーすると税率数倍に過大計上** (R1 Blocker)。対処必須。

#### 基本ルール（memory/feature-payment-mf-import.md 準拠）

振込明細 Excel の運用:
- 5日送金・20日送金とも**前月 20日締め**の買掛を支払う
- `transferDate.minusMonths(1).withDayOfMonth(20)` = その送金で充当される締め月

→ **当月行 (transaction_month=M) の `payment_settled` 合計** = **前月 supplier 単位の支払額 (= `sumVerifiedAmountForGroup` 相当)**

#### 算出フロー（supplier 単位 → 税率比で按分）

```
1. 前月 M?1 の行を (shop, supplier) でグルーピング
2. 各 supplier について前月の支払合計を算出 (既存 sumVerifiedAmountForGroup ロジック準拠):
   - 全税率行の verified_amount が全て一致 → 代表値 (BULK パターン)
   - 不一致 → SUM (MANUAL パターン)
   - 全て null → supplier_paid = 0
3. 当月 M の行を (shop, supplier) でグルーピング
4. 各 supplier の当月 tax_included_amount_change 合計 (supplier_change[curr]) を算出
5. 当月各 tax_rate 行へ **change 比で按分**:
   
   if supplier_change[curr] > 0:
       row.payment_settled_tax_included = supplier_paid × (row.change / supplier_change[curr])
       row.payment_settled_tax_excluded = supplier_paid_excl × (row.change_excl / supplier_change_excl[curr])
   else:
       # 当月仕入ゼロ → payment-only 行で処理 (§2.2 後段参照)
```

**税抜側の算出**: supplier 内の change 比で按分するので `tax_rate/100` 逆算は不要。税抜 payment_settled も同様に supplier の change_excl 比で按分。これで R4 (丸め誤差) も解消 (既存税抜集計値の比で配分のみ)。

#### ゼロ除算・端数処理

- `supplier_change[curr] = 0` → payment-only 行ルートに切替 (§2.2 後段)
- 端数は `RoundingMode.DOWN`、supplier 内の **最後の行で差分調整** (Σ が supplier_paid に合うよう丸めを吸収)

#### 疑似コード

```java
// 前月 supplier 単位の支払合計を構築
Map<SupplierKey, BigDecimal> prevPaidPerSupplier = buildPrevPaidMap(prevList);
//   key: (shop, supplier)
//   value: sumVerifiedAmountForGroup 準拠で算出

// 当月 supplier 単位の change 合計を構築
Map<SupplierKey, BigDecimal> currChangePerSupplier = buildCurrChangeMap(currentList);

// 按分適用
for (SupplierKey key : currentList の supplier set) {
    BigDecimal paid = prevPaidPerSupplier.getOrDefault(key, ZERO);
    BigDecimal changeTotal = currChangePerSupplier.get(key);
    if (paid.signum() == 0) continue; // 前月支払なし
    if (changeTotal.signum() == 0) continue; // payment-only ルートへ
    List<TAccountsPayableSummary> supplierRows = groupBy(key, currentList);
    distributeByChangeRatio(supplierRows, paid); // 最終行で端数吸収
}
```

#### 手動確定行の扱い

手動確定行 (verified_manually=true) の **change 列は保護** (既存 Phase A ポリシー)、**payment_settled は上書き** (opening と同じポリシー)。

#### payment-only 行の生成（R1 + R2 反映）

**ケース**: 前月 supplier で verified_amount > 0 だが、当月 change 合計 = 0 (当月は仕入無し、支払のみ発生)。

この supplier には当月の行が (supplier, tax_rate) 単位で存在しない or 存在しても change=0。

**対処**: `is_payment_only=true` 列（新規）を持つ**単一行**を当月に生成:

```
前月 supplier で verified_amount > 0 かつ 当月 supplier の change 合計 = 0 の場合:
    tax_rate = 前月でその supplier が使った最大 tax_rate (tiebreak: 高い方)
    insert payment-only row:
        shop_no, supplier_no, supplier_code = 前月からコピー
        transaction_month = 当月
        tax_rate = 上記
        tax_included_amount_change = 0
        tax_excluded_amount_change = 0
        payment_settled_tax_included = prev supplier_paid
        payment_settled_tax_excluded = prev supplier_paid_excl (最終計算値を使用)
        opening_balance_tax_{included,excluded} = 前月 closing
        is_payment_only = true
        mf_export_enabled = true
        verified_manually = 前月 verified_manually (引継ぎ)
        verification_result = null
```

**stale-delete ガード**: `is_payment_only=true` の行は stale-delete 対象から除外。

**R1/R4 の解消**: supplier 単位で支払合計を持ち、単一行で保持するため税率重複なし。税抜は supplier の change_excl 比でなく、payment-only 行では税抜も「税込みを税率で逆算」ではなく **前月行で計算済みの税抜 closing 差分** を使う:
```
paymentSettledTaxExcluded (payment-only) = prevRow の sumVerifiedAmountExclFromChangeRatio と同等の方法で事前計算
```

簡素化のため payment-only 行の税抜/税込比は、前月 change 合計の税抜/税込比と同じで近似 (\1?数円の丸め差を許容、既存 verified_amount の精度と同程度)。

### 2.3 closing の再定義

```
effectiveChange = (verified_manually && verifiedAmount ≠ null) ? verifiedAmount : tax_included_amount_change
closing_tax_included = opening_balance_tax_included + effectiveChange - payment_settled_tax_included
closing_tax_excluded = opening_balance_tax_excluded + tax_excluded_amount_change - payment_settled_tax_excluded
```

Entity には持たない（Phase A と同じ方針）。DTO 層 (`AccountsPayableResponse.from(_, _, includeBalance=true)`) で算出。

### 2.4 期首残の扱い

Backfill の起点は `2025-06-20`。その前月 `2025-05-20` 以前の累積 ≒ 期首買掛金残は **自社 DB に存在しない**。

MF trial_balance_bs 2025-05-20 closing ≒ 期首残 (∑ 全 supplier 買掛)。Phase 0 で見た 2026-03-20 の opening_balance (API レスポンスの期首固定値) = 14,705,639 円。

**Phase B' のスコープ判断**: 期首残の自動注入は**本 Phase では行わない**。以下の理由:
1. MF 期首残は supplier×tax_rate に分解できない (account 単位のみ)
2. Phantom row (supplier=0 の特別行) を作ると既存フィルタ・検証・UI が全て影響を受ける
3. 期首残と仕入累積支払のネット差 (約 14.7M) は「既知残差」として UI に明示すれば運用上耐えられる
4. 厳密な期首注入は Phase B'' として後続対応可能

→ **残差 ? \14.7M は既知差**として UI に表示。残差の大半は期首残。

### 2.5 手動確定行 (`verified_manually=true`) との整合

**change 列は保護** (Phase A から継続): `tax_included_amount_change` / `verified_amount` はバッチ上書き禁止。

**payment_settled は常に上書き**: opening と同じポリシー。手動確定行でも前月 verified_amount を引けば payment_settled が算出できる。

→ closing = opening + effectiveChange - payment_settled は手動確定行でも正しく動作。

### 2.6 既存機能への影響

| 機能 | 影響 | 対処 |
|---|---|---|
| 既存 `MfJournalReconcileService` | なし (change 列無改修) | そのまま |
| 買掛金 CSV 出力 (MF 買掛支払 CSV) | なし (verified_amount ベースで継続) | そのまま |
| 仕入仕訳 CSV 出力 | なし (仕入データ源泉は変わらない) | そのまま |
| `SmilePaymentVerifier` | なし (既存 verify 動作継続) | そのまま |
| Phase A 値引繰越バッジ | 維持される (closing 負値で継続) | そのまま |
| `accountsPayableBackfillJob` | payment_settled も再計算要 | backfill Tasklet に同じロジック追加 |

---

## 3. DB 変更

### 3.1 V025 migration（R2 反映: is_payment_only 列追加）

```sql
-- V025__alter_accounts_payable_summary_add_payment_settled.sql
ALTER TABLE t_accounts_payable_summary
    ADD COLUMN IF NOT EXISTS payment_amount_settled_tax_included NUMERIC NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS payment_amount_settled_tax_excluded NUMERIC NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS is_payment_only BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN t_accounts_payable_summary.payment_amount_settled_tax_included IS
    '当月完了した支払額 (税込、supplier 単位支払を税率別 change 比で按分)。5日/20日送金は前月20日締めに充てる運用。closing = opening + change - payment_settled の算出要素。';
COMMENT ON COLUMN t_accounts_payable_summary.payment_amount_settled_tax_excluded IS
    '当月完了した支払額 (税抜、同上 change_excl 比で按分)。';
COMMENT ON COLUMN t_accounts_payable_summary.is_payment_only IS
    'payment-only 行フラグ。当月 change=0 だが前月支払があった supplier のために生成された行。stale-delete 対象から除外する目印。';
```

既存 PK 非破壊、既存 index 再構築不要 (ADD COLUMN NOT NULL DEFAULT は PG 11+ で metadata-only、瞬時完了 / R12)。

### 3.2 Entity 拡張

```java
// TAccountsPayableSummary.java
@Builder.Default
@Column(name = "payment_amount_settled_tax_included", nullable = false)
@ColumnDefault("0")
private BigDecimal paymentAmountSettledTaxIncluded = BigDecimal.ZERO;

@Builder.Default
@Column(name = "payment_amount_settled_tax_excluded", nullable = false)
@ColumnDefault("0")
private BigDecimal paymentAmountSettledTaxExcluded = BigDecimal.ZERO;

@Builder.Default
@Column(name = "is_payment_only", nullable = false)
@ColumnDefault("false")
private Boolean isPaymentOnly = false;
```

---

## 4. バッチ改修

### 4.1 AccountsPayableAggregationTasklet

既存の「前月 closing → 当月 opening 繰越」ロジックに、以下を追加:

1. **payment_settled 計算**: 前月行の `verified_amount` を元に、当月行の payment_settled を set
2. **payment-only 行の生成**: 前月で支払があったが当月は仕入が無い (shop, supplier, tax_rate) について、当月に空行 + payment_settled を作る

#### 疑似コード

```java
// build prev month map with verified_amount
LocalDate prev = periodEndDate.minusMonths(1);
List<TAccountsPayableSummary> prevList = service.findByTransactionMonth(prev);
Map<Key, PrevRow> prevMap = new HashMap<>();
for (TAccountsPayableSummary p : prevList) {
    prevMap.put(key(p), new PrevRow(
        closing(p),           // opening + effectiveChange - payment_settled (既に Phase B' 後定義)
        p.getVerifiedAmount() // 当月の payment_settled source
    ));
}

// apply to current month rows
for (TAccountsPayableSummary row : toSave + preservedManual) {
    PrevRow prev = prevMap.get(key(row));
    // opening: 既存ロジック (Phase A)
    row.setOpeningBalance...(prev != null ? prev.closing().taxIncl() : 0);
    // payment_settled: 新規
    BigDecimal paymentIncl = (prev != null && prev.verifiedAmount() != null)
        ? prev.verifiedAmount() : BigDecimal.ZERO;
    BigDecimal paymentExcl = TaxCalculationHelper.toExcluded(paymentIncl, row.getTaxRate());
    row.setPaymentAmountSettledTaxIncluded(paymentIncl);
    row.setPaymentAmountSettledTaxExcluded(paymentExcl);
}

// payment-only 行生成
Set<Key> currentKeys = current 行の keys;
for (TAccountsPayableSummary p : prevList) {
    if (p.getVerifiedAmount() == null || p.getVerifiedAmount().signum() == 0) continue;
    if (currentKeys.contains(key(p))) continue;
    // payment-only 行を生成
    TAccountsPayableSummary paymentOnly = TAccountsPayableSummary.builder()
        .shopNo(p.getShopNo()).supplierNo(p.getSupplierNo())
        .supplierCode(p.getSupplierCode())
        .transactionMonth(periodEndDate)
        .taxRate(p.getTaxRate())
        .taxIncludedAmountChange(BigDecimal.ZERO)
        .taxExcludedAmountChange(BigDecimal.ZERO)
        .openingBalanceTaxIncluded(closing(p).taxIncl())
        .openingBalanceTaxExcluded(closing(p).taxExcl())
        .paymentAmountSettledTaxIncluded(p.getVerifiedAmount())
        .paymentAmountSettledTaxExcluded(toExcluded(...))
        // 検証関連は null/default
        .build();
    paymentOnlyList.add(paymentOnly);
}
service.saveAll(paymentOnlyList);
```

#### 既存 stale-delete ロジックへの影響

payment-only 行は新たに作られるので、stale-delete 判定で「集計対象外」と誤認されないよう注意。実装上は `saveAll(toSave + paymentOnlyList)` した後に stale 計算するため、savedKeys に payment-only 行の key も含めることで保護される。

### 4.2 AccountsPayableBackfillTasklet（R5 反映: 起点強制 + 途中再開禁止）

同じロジックを `processOneMonth` に適用。月単位 REQUIRES_NEW tx で fromMonth → toMonth を順次処理。

**R5 対処 (途中再開時の opening 不整合防止)**:

Phase A 時代の backfill は `fromMonth` を任意に指定できたが、Phase B' では「途中月から再開」すると、その月の前月 closing が **Phase A 旧式 (payment_settled 未反映) のまま** → 繰越 opening が過大になる。

**Controller バリデーション追加**:
- `/batch/execute/accountsPayableBackfill` で `fromMonth != 2025-06-20` の場合は **warning パラメータ `allowPartialResume=true` 必須**
- デフォルト (`allowPartialResume=false`) では fromMonth は 2025-06-20 固定強制
- UI は通常の再集計で `fromMonth=2025-06-20`、`toMonth=2026-03-20` (または最新月) を送る
- ad-hoc 再開は admin が意図的に allowPartialResume=true を付ける場合のみ許可

**tasklet 側のフォールバック**: `fromMonth != 2025-06-20` の場合 WARN ログを出し、既存 opening を信頼せず **前月行の payment_settled も最新ロジックで再計算しながら進む**。すなわち `processOneMonth(M)` が先に `processOneMonth(M-1)` を参照するとき、M-1 の closing を「その時 DB に残っている値から再計算 (on-the-fly)」する。

### 4.3 TaxCalculationHelper の扱い（R4 反映）

R4 で指摘の通り、税込→税抜の逆算は既存 `calculateTaxAmount` (税抜→税込 DOWN) と**可逆でない** (±1 円誤差)。

**Phase B' の設計判断**: 税抜 payment_settled は **前月 row の `tax_excluded_amount_change` 比で按分** することで取得する (§2.2)。

```
supplier_paid_excl = prev supplier の tax_excluded_amount_change 合計 に対する ratio を適用:
  = supplier_paid_incl × (Σ prev.tax_excluded_amount_change / Σ prev.tax_included_amount_change)
```

これにより:
- 税込→税抜の直接逆算は不要 → 新 helper `toExcluded` 追加不要
- 既存 `TaxCalculationHelper.calculateTaxAmount` (税抜→税込 DOWN) と丸め整合
- ±1 円の既知誤差は「Σ が supplier_paid 合計に合うよう最終行で端数吸収」で UI 見え方は問題なし

#### R3 (税抜/税込の非対称) 対処

手動確定行で `effectiveChange` は税込のみ `verifiedAmount` 優先、税抜は `tax_excluded_amount_change` (自動集計値) のまま。この非対称は Phase A から継承しているため Phase B' でも維持。

closing 表示は **税込が正** (MF 突合指標は税込)。税抜 closing は**参考値**として `MfBalanceReconcileTab` に注記:
> 税抜 closing は手動確定時に税込み closing と税額整合が微小ズレ可。突合は税込ベースで行う。

---

## 5. DTO 変更（R8/R9 反映: closing 算出を共通 util に集約）

### 5.1 共通 util `PayableBalanceCalculator` を新設

複数箇所で closing 算出するので共通化:

```java
// backend/src/main/java/jp/co/oda32/domain/service/finance/PayableBalanceCalculator.java
public final class PayableBalanceCalculator {
    private PayableBalanceCalculator() {}

    public static BigDecimal effectiveChangeTaxIncluded(TAccountsPayableSummary r) {
        boolean manual = Boolean.TRUE.equals(r.getVerifiedManually());
        return manual && r.getVerifiedAmount() != null
                ? r.getVerifiedAmount()
                : nz(r.getTaxIncludedAmountChange());
    }

    public static BigDecimal closingTaxIncluded(TAccountsPayableSummary r) {
        return nz(r.getOpeningBalanceTaxIncluded())
                .add(effectiveChangeTaxIncluded(r))
                .subtract(nz(r.getPaymentAmountSettledTaxIncluded()));
    }

    public static BigDecimal closingTaxExcluded(TAccountsPayableSummary r) {
        return nz(r.getOpeningBalanceTaxExcluded())
                .add(nz(r.getTaxExcludedAmountChange()))
                .subtract(nz(r.getPaymentAmountSettledTaxExcluded()));
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
```

### 5.2 呼び出し側の更新（R9 反映: 一括更新対象 4 ヶ所）

- `AccountsPayableResponse.from(_, _, includeBalance)` → `PayableBalanceCalculator.closingTaxIncluded/Excluded` を使用
- `AccountsPayableAggregationTasklet.buildPrevClosingMap` → 同上 (前月 closing 計算)
- `AccountsPayableBackfillTasklet.buildPrevClosingMap` → 同上
- `MfBalanceReconcileService.closingOf` → 同上

4 ヶ所全て同じ計算式に統一されることを **テスト計画 UT-07 / UT-30 / UT-33 で確認**。

### 5.3 `AccountsPayableResponse` 構造

```java
// include=balance 時のみ set
private BigDecimal openingBalanceTaxIncluded;       // 既存 (Phase A)
private BigDecimal openingBalanceTaxExcluded;       // 既存
private BigDecimal paymentSettledTaxIncluded;       // 新規 (Phase B')
private BigDecimal paymentSettledTaxExcluded;       // 新規
private BigDecimal closingBalanceTaxIncluded;       // PayableBalanceCalculator 使用
private BigDecimal closingBalanceTaxExcluded;       // PayableBalanceCalculator 使用
private Boolean isPaymentOnly;                      // 新規 (UI でバッジ表示用)
```

---

## 6. フロントエンド変更

### 6.1 `types/accounts-payable.ts`

```typescript
export interface AccountsPayableBalance {
  openingBalanceTaxIncluded: number
  openingBalanceTaxExcluded: number
  paymentSettledTaxIncluded: number      // 新規
  paymentSettledTaxExcluded: number      // 新規
  closingBalanceTaxIncluded: number      // 算出式変更後
  closingBalanceTaxExcluded: number
}
```

### 6.2 `accounts-payable.tsx`（R13 反映: 横幅緩和）

balance 列は既存の 2 段ヘッダに準じて 3 列構成 (balance トグル ON のみ表示):
```
| 前月繰越(opening) | 当月支払(payment_settled) | 累積残(closing) |
```

**横幅緩和策**:
- 当月支払列は「取崩済」バッジ + 金額のコンパクト表示 (`\1,234,567 取崩済`)
- payment_settled=0 の行は金額省略 (`?`)
- balance トグル OFF (既定) では既存と同じ幅

**payment-only 行の表示**:
- `is_payment_only=true` の行には「支払のみ」Badge (outline, slate 色)
- 他の列 (仕入税抜・税込・振込明細額) は `?` 表示
- balance トグル ON 時のみ見える位置に挿入 (OFF 時は balance 列と一緒に隠す想定だが、UI 一貫性のため display フィルタを設ける)

**バッジ条件の修正 (R17 反映)**:
- 「値引繰越」バッジ: `closing < 0 AND NOT is_payment_only` (従来の値引超過)
- 「支払超過」バッジ (新): `closing < 0 AND is_payment_only` (支払完了後の過払い状態)

### 6.3 `MfBalanceReconcileTab.tsx`

残差に「期首残 ? \14.7M は既知差 (Phase B' スコープ外、期首注入は別 Phase)」の注記を追加。

---

## 7. 実装順序

1. V025 migration 作成
2. Entity に 2 列追加 (`@Builder.Default = ZERO`)
3. `TaxCalculationHelper` に `toExcluded` メソッド追加 (既存なければ)
4. DTO `AccountsPayableResponse` に balance 4 列 + closing 算出式変更
5. `MfBalanceReconcileService` の closingOf 算出式変更
6. `AccountsPayableAggregationTasklet` 改修 (前月 verified_amount → payment_settled, payment-only 行生成)
7. `AccountsPayableBackfillTasklet` 同じ改修
8. フロント型分離拡張
9. UI 列追加 (accounts-payable.tsx)
10. MfBalanceReconcileTab に期首残注記
11. コンパイル確認 (backend/frontend)
12. DB migration 適用 (Flyway 自動 on JVM restart)
13. 10 ヶ月再集計 (accountsPayableAggregation 2025-06 ? 2026-03)
14. backfill 再実行 (payment_settled を反映)
15. MfBalanceReconcileTab で 2026-03-20 の差が ? \14.7M に収束することを確認

---

## 8. 検証シナリオ

### 8.1 機能検証

| # | シナリオ | 期待結果 |
|---|---|---|
| V1 | V025 migration 適用後、既存行の payment_settled は DEFAULT 0 | NULL 無し、全行 0 |
| V2 | 10 ヶ月再集計後、1/20 行の payment_settled に 12/20 verified_amount がコピーされている | (shop, supplier, taxRate) 単位で一致 |
| V3 | 2/20 で当月仕入無しの supplier だが 1/20 に verified_amount > 0 だった場合、payment-only 行が 2/20 に生成される | change=0, payment_settled>0, opening=前月 closing |
| V4 | 手動確定行 (verified_manually=true) の change 列は保護、payment_settled は上書きされる | change 維持、payment_settled 更新 |
| V5 | 2026-03-20 の MfBalanceReconcileTab で diff が ? \14.7M (期首残) まで収束 | \178M → \15M 前後 |
| V6 | Phase A 機能 (竹の子の里の値引繰越) は継続動作 | closing 負値バッジ表示 |
| V7 | `MfJournalReconcileService` 3/20 PURCHASE diff は変わらず \10 | 仕入 change は無改修なので影響なし |

### 8.2 エッジケース

| # | ケース | 期待動作 |
|---|---|---|
| E1 | 1/20 verified_amount = null の行 → 2/20 で payment_settled=0 | WARN ログ、動作継続 |
| E2 | 起点 2025-06-20 は前月行なし → payment_settled=0 全行 | WARN ログ、既存 Phase A と同じ動作 |
| E3 | 税率 0% の行 (竹の子の里 etc) の payment_settled は taxExcluded=taxIncluded | 税抜=税込 |
| E4 | payment-only 行の supplier_code は前月からコピーされる | null でない |
| E5 | backfill 途中失敗 → 次回再実行は同じ月から再開 | month-level REQUIRES_NEW tx で cleanly resumable |

---

## 9. リスクと軽減策

| リスク | 影響 | 軽減策 |
|---|---|---|
| payment-only 行大量生成による DB 肥大化 | 中 | 1 supplier あたり月 1 行程度。supplier 数 ×12 ヶ月 = 数百行程度で問題なし |
| 期首残非注入による常時 ?\14.7M 差 | 低 (UI で既知差を明示) | Phase B'' で期首注入実装、もしくは運用で受容 |
| verified_amount の取込漏れで payment_settled=0 のまま | 中 | WARN ログ + UI の未検証 orange banner で可視化 (既存機能流用) |
| 税抜計算の丸めで closing 税抜合計が微小ずれ | 低 | RoundingMode.DOWN 統一で許容 (既存 TaxCalculationHelper と整合) |
| 既存 MfJournalReconcileService 影響 | 低 | 仕入 change 無改修のため影響なし |
| backfill 再実行中に UI でアクセスして不整合を見る | 低 | 月単位 REQUIRES_NEW tx で整合、UI は include=balance のみ影響 |

---

## 10. スコープ外（Phase B'' 候補）

- 期首残 (MF 2025-05-20 買掛金 closing) の phantom 行注入 → 完全一致を目指す
- **過去 7 ヶ月 (2025-06?2025-12) の `verified_amount` 遡及充填** (`t_smile_payment` から集計、2025-06?2025-12 は振込明細 Excel 機能実装前のため空)
- MfBalanceReconcileTab の仕入先別ドリルダウン (MF journals 累積 fallback)
- 売掛金残高突合 (Phase C の PK 5 列問題と合わせて)
- `t_smile_payment` と `verified_amount` の整合性監査 (現状 verified_amount 優先)
- 早払収益・振込手数料値引の細部差調整 (差が数万円規模のため後続で対応)

## 11. 実装・検証結果 (2026-04-22 完了)

### 実装確定

設計レビュー (Blocker 2 件 + Critical 3 件) を 1 ループで解消し、コードレビュー (Blocker 2 件 + Critical 3 件) も 1 ループで解消。実装は設計書通り完成。

**主な実装決定**:
- 共通 util `PayableBalanceCalculator` (closing 算出 4 箇所に集約)
- 共通 Service `PayableMonthlyAggregator` (Aggregation / Backfill 両方から利用)
- Backfill は `TransactionTemplate` で月単位 REQUIRES_NEW tx を明示制御 (self-invocation 制約回避)
- payment-only 行は `is_payment_only=true`, `verified_amount=null`, `verifiedManually=false` で生成 → 次月 paid 計算に影響させない

### 実測結果 (2026-03-20 時点)

| 項目 | 値 |
|---|---|
| 自社 累積残 (MF 対象) | \153,831,971 (45 行 内 payment-only 6 行) |
| MF 買掛金 closing | \41,085,601 |
| 差分 | \-112,746,370 |
| 期待 (期首残 ?\14.7M) | 期待差 ?\14.7M |

### 残差 \-112M の原因と判断

月別分析で **2025-06?2025-12 (7 ヶ月) の `verified_amount = 0`** が判明。これは振込明細 Excel 取込機能 (memory: 2026-04-14 実装) より前の期間で、自社 DB に支払記録が存在しない。

- 2026-01-20: 初めて verified_amount 記録 (\12,317,204)
- 2026-02-20: payment_settled 機能開始 (\12,249,644 が 1/20 verified から反映)
- 2026-03-20: payment_settled \14,595,570 (2/20 verified から反映)

**Phase B' 実装は正常動作** ? 2026-02-20 以降の残高突合が機能する。2025-06?2025-12 の仕入累計 (?\100M) が取崩されていないため、期首残 \14.7M に加えて差に乗っている。

### 完了判断と次フェーズ

Phase B' の完了基準は「payment_settled 列と T 勘定化の動作確認」。これは満たす。
**過去データ遡及 (2025-06?2025-12 verified_amount 充填) は別設計課題として Phase B'' に繰越**。

payment-only 行で実在検出した仕入先 (6 件): 三興化学、西日本衛材、大阪包装社、ﾄｯｸﾌﾞﾗﾝｼｭ、ハラプレックス、信越ファインテック。いずれも適切に opening→closing 繰越が機能している。

---

## 11. 参考資料

- `claudedocs/design-supplier-partner-ledger-balance.md` (Phase 0/A の母体)
- `memory/project_ap_ar_cumulative_balance.md` (Phase A 完了状況)
- `memory/feature-accounts-payable.md` (買掛金一覧機能)
- `memory/feature-payment-mf-import.md` (振込明細 Excel 取込)
- Commit `567f34d` (Phase A 実装)
The specified drive root "C:\Users\kazuki\AppData\Local\Temp\" either does not exist, or it is not a folder.
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
設計書側では、Phase B' の実測で「2025-06〜12 の支払記録欠落」が別課題として残っている一方、後続の累積残一覧は期首以降の差がきれいに比較できる前提で進んでいます。この前提のズレは Opus の実装品質指摘とは別に、業務上の信頼境界としてかなり重要です。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\TAccountsPayableSummaryService.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\AccountsPayableIntegrityService.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\SupplierBalancesService.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\ConsistencyReviewService.java" in C:\project\odamitsu-data-hub
 succeeded in 1755ms:
package jp.co.oda32.domain.service.finance;

import jakarta.persistence.criteria.Predicate;
import jp.co.oda32.annotation.SkipShopCheck;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.dto.finance.AccountsPayableSummaryResponse;
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

    private static final BigDecimal MATCH_THRESHOLD = new BigDecimal("100");

    private final TAccountsPayableSummaryRepository repository;

    @Autowired
    public TAccountsPayableSummaryService(TAccountsPayableSummaryRepository repository) {
        this.repository = repository;
    }

    public List<TAccountsPayableSummary> findAll() {
        return repository.findAll();
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
        Specification<TAccountsPayableSummary> spec = buildSpec(shopNo, null, transactionMonth, "all");
        List<TAccountsPayableSummary> list = repository.findAll(spec);
        long total = list.size();
        long unverified = list.stream().filter(s -> s.getVerificationResult() == null).count();
        long unmatched = list.stream().filter(s -> s.getVerificationResult() != null && s.getVerificationResult() == 0).count();
        long matched = list.stream().filter(s -> s.getVerificationResult() != null && s.getVerificationResult() == 1).count();
        BigDecimal diffSum = list.stream()
                .filter(s -> s.getVerificationResult() != null && s.getVerificationResult() == 0)
                .map(s -> s.getPaymentDifference() != null ? s.getPaymentDifference() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
            throw new IllegalStateException(
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

        boolean matched = difference.abs().compareTo(MATCH_THRESHOLD) <= 0;
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

 succeeded in 1907ms:
package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.repository.finance.MfAccountMasterRepository;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.service.finance.mf.MfJournal;
import jp.co.oda32.domain.service.finance.mf.MfJournalCacheService;
import jp.co.oda32.domain.service.finance.mf.MfJournalFetcher;
import jp.co.oda32.domain.service.finance.mf.MfOauthService;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import jp.co.oda32.dto.finance.IntegrityReportResponse;
import jp.co.oda32.dto.finance.IntegrityReportResponse.AmountMismatchEntry;
import jp.co.oda32.dto.finance.IntegrityReportResponse.MfOnlyEntry;
import jp.co.oda32.dto.finance.IntegrityReportResponse.SelfOnlyEntry;
import jp.co.oda32.dto.finance.IntegrityReportResponse.Summary;
import jp.co.oda32.dto.finance.IntegrityReportResponse.UnmatchedSupplierEntry;
import jp.co.oda32.dto.finance.SupplierBalancesResponse;
import jp.co.oda32.dto.finance.SupplierBalancesResponse.SupplierBalanceRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 買掛帳 整合性検出機能 (軸 B + 軸 C)。
 * <p>
 * 期間内の全 supplier を一括診断し、self (自社 summary) と MF (/journals) を
 * supplier × 月 単位で突合して 4 カテゴリに分類:
 * <ul>
 *   <li>MF_ONLY (MfOnlyEntry): MF にあって self に無い (= CSV 出力漏れ疑い)</li>
 *   <li>SELF_ONLY (SelfOnlyEntry): self にあって MF に無い (= CSV 取込漏れ or MF 手入力漏れ)</li>
 *   <li>AMOUNT_DIFF (AmountMismatchEntry): ペアあり金額差 (MINOR: 100<diff?1000, MAJOR: diff>1000)</li>
 *   <li>UNMATCHED_SUPPLIER (UnmatchedSupplierEntry): mf_account_master に登録無し supplier (supplier 単位)</li>
 * </ul>
 * <p>
 * 設計書: claudedocs/design-integrity-report.md §3, §5
 *
 * @since 2026-04-22
 */
@Service
@Log4j2
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountsPayableIntegrityService {

    private static final int MAX_PERIOD_MONTHS = 12;
    private static final String MF_ACCOUNT_PAYABLE = "買掛金";
    /** MATCH 許容差: ±\100 以下は一致とみなす (既存 SmilePaymentVerifier 準拠)。 */
    private static final BigDecimal MATCH_TOLERANCE = BigDecimal.valueOf(100);
    /** MINOR 上限: 100 < diff ? 1000 → MINOR、1000 超 → MAJOR。 */
    private static final BigDecimal MINOR_UPPER = BigDecimal.valueOf(1000);

    private final MfOauthService mfOauthService;
    private final MfJournalCacheService journalCache;
    private final MfAccountMasterRepository mfAccountMasterRepository;
    private final TAccountsPayableSummaryRepository summaryRepository;
    private final MPaymentSupplierService paymentSupplierService;
    private final SupplierBalancesService supplierBalancesService;
    private final ConsistencyReviewService consistencyReviewService;

    public IntegrityReportResponse generate(Integer shopNo, LocalDate fromMonth, LocalDate toMonth, boolean refresh) {
        // --- 入力検証 ---
        if (shopNo == null || fromMonth == null || toMonth == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "shopNo / fromMonth / toMonth は必須です");
        }
        if (fromMonth.isAfter(toMonth)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fromMonth は toMonth 以前である必要があります");
        }
        if (fromMonth.getDayOfMonth() != 20 || toMonth.getDayOfMonth() != 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fromMonth / toMonth は 20 日締め日 (yyyy-MM-20) で指定してください");
        }
        long months = java.time.temporal.ChronoUnit.MONTHS.between(fromMonth, toMonth);
        if (months > MAX_PERIOD_MONTHS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "期間は最大 " + MAX_PERIOD_MONTHS + " ヶ月です");
        }

        // --- 自社 summary を期間で一括取得 ---
        List<TAccountsPayableSummary> selfRows = summaryRepository
                .findByShopNoAndTransactionMonthBetweenOrderBySupplierNoAscTransactionMonthAscTaxRateAsc(
                        shopNo, fromMonth, toMonth);

        // supplier 単位 + 月単位で index 化
        // key: supplierNo × month → List of summary rows
        Map<Integer, Map<LocalDate, List<TAccountsPayableSummary>>> selfIndex = new HashMap<>();
        Set<Integer> selfSupplierNos = new HashSet<>();
        for (TAccountsPayableSummary r : selfRows) {
            selfIndex
                    .computeIfAbsent(r.getSupplierNo(), k -> new TreeMap<>())
                    .computeIfAbsent(r.getTransactionMonth(), k -> new ArrayList<>())
                    .add(r);
            selfSupplierNos.add(r.getSupplierNo());
        }

        // --- MF /journals 取得 (キャッシュ経由, 差分 fetch) ---
        MMfOauthClient client = mfOauthService.findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        String accessToken = mfOauthService.getValidAccessToken();
        MfJournalCacheService.CachedResult cached = journalCache.getOrFetch(
                shopNo, client, accessToken, fromMonth, toMonth, refresh);
        List<MfJournal> allJournals = cached.journals();
        Instant fetchedAt = cached.oldestFetchedAt();
        log.info("[integrity] shopNo={}, 期間 {}?{}, journals {} 件, fetchedAt={}",
                shopNo, fromMonth, toMonth, allJournals.size(), fetchedAt);

        // --- mf_account_master 逆引き map 構築 (sub_account_name → supplier_code(search_key)) ---
        // 同じ sub_account_name が複数 supplier_code に対応する場合は set で保持 (表記揺れ検出用)
        List<MfAccountMaster> allMasters = mfAccountMasterRepository.findAll();
        Map<String, Set<String>> mfSubToCodes = new HashMap<>();
        Set<String> matchedSupplierCodes = new HashSet<>();
        for (MfAccountMaster r : allMasters) {
            if (!MF_ACCOUNT_PAYABLE.equals(r.getAccountName())) continue;
            if (!MF_ACCOUNT_PAYABLE.equals(r.getFinancialStatementItem())) continue;
            if (r.getSubAccountName() == null || r.getSubAccountName().isBlank()) continue;
            if (r.getSearchKey() == null || r.getSearchKey().isBlank()) continue;
            mfSubToCodes.computeIfAbsent(r.getSubAccountName(), k -> new HashSet<>())
                    .add(r.getSearchKey());
            matchedSupplierCodes.add(r.getSearchKey());
        }

        // --- MF journals を supplier × 月 の bucket に集約 ---
        // key: subAccountName × 20日締め月
        Map<String, Map<LocalDate, MonthBucket>> mfBySubAccount = new HashMap<>();
        for (MfJournal j : allJournals) {
            if (j.transactionDate() == null || j.branches() == null) continue;
            if (MfJournalFetcher.isPayableOpeningJournal(j)) continue; // 期首残高仕訳は除外 (前期繰越として別管理)
            LocalDate monthKey = MfJournalFetcher.toClosingMonthDay20(j.transactionDate());
            if (monthKey.isBefore(fromMonth) || monthKey.isAfter(toMonth)) continue;

            for (MfJournal.MfBranch br : j.branches()) {
                BigDecimal credit = BigDecimal.ZERO;
                BigDecimal debit = BigDecimal.ZERO;
                String subName = null;
                if (br.creditor() != null
                        && MF_ACCOUNT_PAYABLE.equals(br.creditor().accountName())
                        && br.creditor().subAccountName() != null) {
                    credit = nz(br.creditor().value());
                    subName = br.creditor().subAccountName();
                }
                if (br.debitor() != null
                        && MF_ACCOUNT_PAYABLE.equals(br.debitor().accountName())
                        && br.debitor().subAccountName() != null) {
                    debit = nz(br.debitor().value());
                    if (subName == null) subName = br.debitor().subAccountName();
                }
                if (subName == null) continue;
                MonthBucket b = mfBySubAccount
                        .computeIfAbsent(subName, k -> new TreeMap<>())
                        .computeIfAbsent(monthKey, k -> new MonthBucket());
                b.credit = b.credit.add(credit);
                b.debit = b.debit.add(debit);
                b.branchCount++;
                if (j.number() != null) b.journalNumbers.add(j.number());
            }
        }

        // --- supplier 情報を一括取得 ---
        // self 側 supplier + MF 逆引きで supplier_code 判明した supplier を union
        Set<String> mfSupplierCodes = new HashSet<>();
        for (Map.Entry<String, Set<String>> e : mfSubToCodes.entrySet()) {
            if (mfBySubAccount.containsKey(e.getKey())) {
                mfSupplierCodes.addAll(e.getValue());
            }
        }
        Collection<MPaymentSupplier> allSuppliers = paymentSupplierService.findByShopNo(shopNo);
        Map<Integer, MPaymentSupplier> supplierByNo = new HashMap<>();
        Map<String, MPaymentSupplier> supplierByCode = new HashMap<>();
        for (MPaymentSupplier s : allSuppliers) {
            supplierByNo.put(s.getPaymentSupplierNo(), s);
            if (s.getPaymentSupplierCode() != null) {
                supplierByCode.put(s.getPaymentSupplierCode(), s);
            }
        }

        // --- 判定ループ ---
        List<MfOnlyEntry> mfOnly = new ArrayList<>();
        List<SelfOnlyEntry> selfOnly = new ArrayList<>();
        List<AmountMismatchEntry> amountMismatch = new ArrayList<>();
        List<UnmatchedSupplierEntry> unmatchedSuppliers = new ArrayList<>();

        // ---- 1) self 側 supplier × 月 を走査 ----
        Set<String> processedSubNames = new HashSet<>();
        for (Integer supplierNo : selfSupplierNos) {
            MPaymentSupplier sup = supplierByNo.get(supplierNo);
            if (sup == null) continue;
            String supplierCode = sup.getPaymentSupplierCode();
            String supplierName = sup.getPaymentSupplierName();

            // MF 側の sub_account_name を逆引き: supplier_code で mf_account_master にあるか
            Set<String> matchedSubNames = new HashSet<>();
            for (Map.Entry<String, Set<String>> e : mfSubToCodes.entrySet()) {
                if (e.getValue().contains(supplierCode)) {
                    matchedSubNames.add(e.getKey());
                }
            }

            boolean supplierMfUnmatched = matchedSubNames.isEmpty()
                    && !matchedSupplierCodes.contains(supplierCode);

            if (supplierMfUnmatched) {
                // この supplier は mf_account_master に買掛金 sub_account として未登録
                unmatchedSuppliers.add(UnmatchedSupplierEntry.builder()
                        .supplierNo(supplierNo)
                        .supplierCode(supplierCode)
                        .supplierName(supplierName)
                        .reason("mf_account_master に『買掛金』sub_account として未登録")
                        .build());
                // unmatched でも月ごとの突合は続行 (MF 側 0 として扱う)
            }

            // 月ごとに self_delta / mf_delta を計算して判定
            Map<LocalDate, List<TAccountsPayableSummary>> monthMap = selfIndex.get(supplierNo);
            if (monthMap == null) continue;
            for (Map.Entry<LocalDate, List<TAccountsPayableSummary>> me : monthMap.entrySet()) {
                LocalDate month = me.getKey();
                List<TAccountsPayableSummary> rows = me.getValue();
                // self_delta = Σ (effectiveChange - payment_settled)
                BigDecimal selfDelta = BigDecimal.ZERO;
                BigDecimal effectiveChange = BigDecimal.ZERO;
                BigDecimal changeTaxIncluded = BigDecimal.ZERO;
                BigDecimal paymentSettled = BigDecimal.ZERO;
                for (TAccountsPayableSummary row : rows) {
                    BigDecimal eff = PayableBalanceCalculator.effectiveChangeTaxIncluded(row);
                    selfDelta = selfDelta.add(eff)
                            .subtract(nz(row.getPaymentAmountSettledTaxIncluded()));
                    effectiveChange = effectiveChange.add(eff);
                    changeTaxIncluded = changeTaxIncluded.add(nz(row.getTaxIncludedAmountChange()));
                    paymentSettled = paymentSettled.add(nz(row.getPaymentAmountSettledTaxIncluded()));
                }

                // MF 側 bucket から月分を集計 (matchedSubNames すべての sum)
                BigDecimal mfCredit = BigDecimal.ZERO;
                BigDecimal mfDebit = BigDecimal.ZERO;
                int mfBranchCount = 0;
                Set<Integer> mfJournalNumbers = new java.util.TreeSet<>();
                for (String sn : matchedSubNames) {
                    Map<LocalDate, MonthBucket> mfMap = mfBySubAccount.get(sn);
                    if (mfMap == null) continue;
                    MonthBucket b = mfMap.get(month);
                    if (b == null) continue;
                    mfCredit = mfCredit.add(b.credit);
                    mfDebit = mfDebit.add(b.debit);
                    mfBranchCount += b.branchCount;
                    mfJournalNumbers.addAll(b.journalNumbers);
                    processedSubNames.add(sn + "|" + month);
                }
                BigDecimal mfDelta = mfCredit.subtract(mfDebit);
                boolean selfHasActivity = !rows.isEmpty()
                        && (effectiveChange.signum() != 0 || paymentSettled.signum() != 0);
                boolean mfHasActivity = mfBranchCount > 0 && (mfCredit.signum() != 0 || mfDebit.signum() != 0);

                // 判定 (§3.2)
                if (!selfHasActivity && !mfHasActivity) {
                    // NO_ACTIVITY (R1 反映): スキップ
                    continue;
                }
                if (selfHasActivity && !mfHasActivity) {
                    // MF_MISSING → SelfOnlyEntry
                    selfOnly.add(SelfOnlyEntry.builder()
                            .transactionMonth(month)
                            .supplierNo(supplierNo)
                            .supplierCode(supplierCode)
                            .supplierName(supplierName)
                            .selfDelta(selfDelta)
                            .changeTaxIncluded(changeTaxIncluded)
                            .paymentSettledTaxIncluded(paymentSettled)
                            .taxRateRowCount(rows.size())
                            .reason(supplierMfUnmatched
                                    ? "MF 未登録 supplier / MF CSV 出力漏れ"
                                    : "MF CSV 出力漏れ or 未反映")
                            .build());
                    continue;
                }
                if (!selfHasActivity && mfHasActivity) {
                    // SELF_MISSING → MfOnlyEntry (self 側に行あるが activity なし、稀ケース)
                    // matchedSubNames が空 (supplierMfUnmatched) のときは 2) MF 側ループに委ねる
                    if (matchedSubNames.isEmpty()) {
                        continue;
                    }
                    mfOnly.add(MfOnlyEntry.builder()
                            .transactionMonth(month)
                            .subAccountName(matchedSubNames.iterator().next())
                            .creditAmount(mfCredit)
                            .debitAmount(mfDebit)
                            .periodDelta(mfDelta)
                            .branchCount(mfBranchCount)
                            .guessedSupplierNo(supplierNo)
                            .guessedSupplierCode(supplierCode)
                            .reason("MF 側手入力 or 自社取込漏れ")
                            .journalNumbers(new ArrayList<>(mfJournalNumbers))
                            .build());
                    continue;
                }
                // 両方 activity あり: 金額比較
                BigDecimal diff = selfDelta.subtract(mfDelta);
                BigDecimal diffAbs = diff.abs();
                if (diffAbs.compareTo(MATCH_TOLERANCE) <= 0) {
                    continue; // MATCH (Entry 無し)
                }
                String severity = diffAbs.compareTo(MINOR_UPPER) <= 0 ? "MINOR" : "MAJOR";
                amountMismatch.add(AmountMismatchEntry.builder()
                        .transactionMonth(month)
                        .supplierNo(supplierNo)
                        .supplierCode(supplierCode)
                        .supplierName(supplierName)
                        .selfDelta(selfDelta)
                        .mfDelta(mfDelta)
                        .diff(diff)
                        .severity(severity)
                        .build());
            }
        }

        // ---- 2) MF 側 subAccount × 月 で self にないものを列挙 ----
        for (Map.Entry<String, Map<LocalDate, MonthBucket>> e : mfBySubAccount.entrySet()) {
            String subName = e.getKey();
            Set<String> codes = mfSubToCodes.getOrDefault(subName, Collections.emptySet());
            // この sub_account に対応する supplier を 1 件決める (複数なら最初)
            Integer guessedNo = null;
            String guessedCode = null;
            for (String code : codes) {
                MPaymentSupplier s = supplierByCode.get(code);
                if (s != null) {
                    guessedNo = s.getPaymentSupplierNo();
                    guessedCode = code;
                    break;
                }
            }
            for (Map.Entry<LocalDate, MonthBucket> me : e.getValue().entrySet()) {
                LocalDate month = me.getKey();
                // 既に self 側ループで処理されたなら skip
                if (processedSubNames.contains(subName + "|" + month)) continue;
                MonthBucket b = me.getValue();
                BigDecimal mfDelta = b.credit.subtract(b.debit);
                if (b.branchCount == 0 || (b.credit.signum() == 0 && b.debit.signum() == 0)) continue;
                mfOnly.add(MfOnlyEntry.builder()
                        .transactionMonth(month)
                        .subAccountName(subName)
                        .creditAmount(b.credit)
                        .debitAmount(b.debit)
                        .periodDelta(mfDelta)
                        .branchCount(b.branchCount)
                        .guessedSupplierNo(guessedNo)
                        .guessedSupplierCode(guessedCode)
                        .journalNumbers(new ArrayList<>(new java.util.TreeSet<>(b.journalNumbers)))
                        .reason(guessedNo != null
                                ? "MF にあって自社に無い (自社取込漏れ疑い)"
                                : "MF 手入力または未登録 supplier")
                        .build());
            }
        }

        // ---- 期末累積残判定 (軸 D 連動): supplier ごとに toMonth 時点の累積残が ±\0 一致なら
        //     reconciledAtPeriodEnd=true。加えて累積 diff 値も各エントリに併記 ----
        Map<Integer, BigDecimal> supplierCumulativeDiff = buildSupplierCumulativeDiffMap(shopNo, toMonth, refresh);
        int reconciledCount = 0;
        for (MfOnlyEntry e : mfOnly) {
            BigDecimal cd = e.getGuessedSupplierNo() != null
                    ? supplierCumulativeDiff.get(e.getGuessedSupplierNo()) : null;
            e.setSupplierCumulativeDiff(cd);
            boolean reconciled = cd != null && cd.signum() == 0;
            e.setReconciledAtPeriodEnd(reconciled);
            if (reconciled) reconciledCount++;
        }
        for (SelfOnlyEntry e : selfOnly) {
            BigDecimal cd = e.getSupplierNo() != null
                    ? supplierCumulativeDiff.get(e.getSupplierNo()) : null;
            e.setSupplierCumulativeDiff(cd);
            boolean reconciled = cd != null && cd.signum() == 0;
            e.setReconciledAtPeriodEnd(reconciled);
            if (reconciled) reconciledCount++;
        }
        for (AmountMismatchEntry e : amountMismatch) {
            BigDecimal cd = e.getSupplierNo() != null
                    ? supplierCumulativeDiff.get(e.getSupplierNo()) : null;
            e.setSupplierCumulativeDiff(cd);
            boolean reconciled = cd != null && cd.signum() == 0;
            e.setReconciledAtPeriodEnd(reconciled);
            if (reconciled) reconciledCount++;
        }

        // ---- 差分確認履歴 (案 X+Y) を各 entry に付与。snapshotStale は ±\100 閾値で判定 ----
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
                .build();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    // ==================== 差分確認履歴の付与 (案 X+Y) ====================

    private static final BigDecimal STALE_TOLERANCE = BigDecimal.valueOf(100);

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
                e.setSnapshotStale(diff.compareTo(STALE_TOLERANCE) > 0);
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
                e.setSnapshotStale(diff.compareTo(STALE_TOLERANCE) > 0);
            } else {
                e.setSnapshotStale(false);
            }
        }
    }

    private void attachReviewToAmountMismatch(List<AmountMismatchEntry> list,
            Map<ConsistencyReviewService.ReviewKey, ConsistencyReviewService.ReviewInfo> reviewMap) {
        for (AmountMismatchEntry e : list) {
            if (e.getSupplierNo() == null) continue;
            var info = reviewMap.get(new ConsistencyReviewService.ReviewKey(
                    "amountMismatch", e.getSupplierNo().toString(), e.getTransactionMonth()));
            if (info != null) {
                e.setReviewStatus(info.reviewStatus());
                e.setReviewedAt(info.reviewedAt());
                e.setReviewedByName(info.reviewedByName());
                e.setReviewNote(info.note());
                BigDecimal selfDiff = nz(e.getSelfDelta()).subtract(nz(info.selfSnapshot())).abs();
                BigDecimal mfDiff = nz(e.getMfDelta()).subtract(nz(info.mfSnapshot())).abs();
                e.setSnapshotStale(selfDiff.compareTo(STALE_TOLERANCE) > 0
                                || mfDiff.compareTo(STALE_TOLERANCE) > 0);
            } else {
                e.setSnapshotStale(false);
            }
        }
    }

    /**
     * 軸 D (SupplierBalancesService) を呼び、toMonth 時点での supplier 単位累積 diff (self - mf) を map で返す。
     * このマップは各 entry に supplierCumulativeDiff として併記され、UI に「累積差: \25」のように表示される。
     * 加えて diff == 0 の supplier は reconciledAtPeriodEnd=true 扱い (ノイズ抑制)。
     * <p>
     * MF /journals は {@link MfJournalCacheService} 共有のため、既に integrity 側で取得済み期間と重複する部分はキャッシュヒット。
     */
    private Map<Integer, BigDecimal> buildSupplierCumulativeDiffMap(Integer shopNo, LocalDate toMonth, boolean refresh) {
        try {
            SupplierBalancesResponse bal = supplierBalancesService.generate(shopNo, toMonth, refresh);
            Map<Integer, BigDecimal> map = new HashMap<>();
            for (SupplierBalanceRow r : bal.getRows()) {
                if (r.getSupplierNo() != null && r.getDiff() != null) {
                    map.put(r.getSupplierNo(), r.getDiff());
                }
            }
            long reconciled = map.values().stream().filter(d -> d.signum() == 0).count();
            log.info("[integrity] cumulativeDiff @ toMonth={} : {} supplier, うち diff=0 が {} 件",
                    toMonth, map.size(), reconciled);
            return map;
        } catch (Exception e) {
            log.warn("[integrity] 期末累積残判定スキップ (計算失敗): {}", e.getMessage());
            return Map.of();
        }
    }

    private static class MonthBucket {
        BigDecimal credit = BigDecimal.ZERO;
        BigDecimal debit = BigDecimal.ZERO;
        int branchCount = 0;
        final List<Integer> journalNumbers = new ArrayList<>();
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2931ms:
package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.repository.finance.MfAccountMasterRepository;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.service.finance.mf.MfJournal;
import jp.co.oda32.domain.service.finance.mf.MfJournalCacheService;
import jp.co.oda32.domain.service.finance.mf.MfJournalFetcher;
import jp.co.oda32.domain.service.finance.mf.MfOauthService;
import jp.co.oda32.domain.service.finance.mf.MfOpeningBalanceService;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import jp.co.oda32.dto.finance.SupplierBalancesResponse;
import jp.co.oda32.dto.finance.SupplierBalancesResponse.SupplierBalanceRow;
import jp.co.oda32.dto.finance.SupplierBalancesResponse.Summary;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 買掛 supplier 累積残一覧サービス (軸 D)。
 * <p>
 * 期首 (2025-05-20) ? asOfMonth の全 supplier 累積残を自社 summary と MF /journals で突合。
 * MATCH / MINOR / MAJOR / MF_MISSING / SELF_MISSING で分類。
 * <p>
 * 設計書: claudedocs/design-supplier-balances-health.md §3, §5
 *
 * @since 2026-04-23
 */
@Service
@Log4j2
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierBalancesService {

    /** MF 会計期首 (全期間累積の起点)。 */
    private static final LocalDate MF_PERIOD_START = LocalDate.of(2025, 5, 20);
    /** 前期繰越の基準日 (m_supplier_opening_balance との join キー)。MF fiscal year 直前日。 */
    private static final LocalDate OPENING_DATE = LocalDate.of(2025, 6, 20);
    private static final String MF_ACCOUNT_PAYABLE = "買掛金";
    private static final BigDecimal MATCH_TOLERANCE = BigDecimal.valueOf(100);
    private static final BigDecimal MINOR_UPPER = BigDecimal.valueOf(1000);

    private final MfOauthService mfOauthService;
    private final MfJournalCacheService journalCache;
    private final MfAccountMasterRepository mfAccountMasterRepository;
    private final TAccountsPayableSummaryRepository summaryRepository;
    private final MPaymentSupplierService paymentSupplierService;
    private final MfOpeningBalanceService openingBalanceService;

    public SupplierBalancesResponse generate(Integer shopNo, LocalDate asOfMonth, boolean refresh) {
        if (shopNo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shopNo は必須です");
        }

        // asOfMonth 省略時は findLatestTransactionMonth、summary が空の場合は空レスポンス
        LocalDate resolvedMonth = asOfMonth != null
                ? asOfMonth
                : summaryRepository.findLatestTransactionMonth(shopNo).orElse(null);
        if (resolvedMonth == null) {
            return emptyResponse(shopNo);
        }
        if (resolvedMonth.getDayOfMonth() != 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "asOfMonth は 20 日締め日 (yyyy-MM-20) で指定してください");
        }
        if (resolvedMonth.isBefore(MF_PERIOD_START)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "asOfMonth は MF 会計期首 " + MF_PERIOD_START + " 以降を指定してください");
        }

        // --- 自社 summary 期間取得 (期首?asOfMonth) ---
        List<TAccountsPayableSummary> selfRows = summaryRepository
                .findByShopNoAndTransactionMonthBetweenOrderBySupplierNoAscTransactionMonthAscTaxRateAsc(
                        shopNo, MF_PERIOD_START, resolvedMonth);

        Map<Integer, List<TAccountsPayableSummary>> selfBySupplier = new HashMap<>();
        for (TAccountsPayableSummary r : selfRows) {
            selfBySupplier.computeIfAbsent(r.getSupplierNo(), k -> new ArrayList<>()).add(r);
        }

        // --- MF journals 取得 (キャッシュ経由) ---
        MMfOauthClient client = mfOauthService.findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        String accessToken = mfOauthService.getValidAccessToken();
        MfJournalCacheService.CachedResult cached = journalCache.getOrFetch(
                shopNo, client, accessToken, MF_PERIOD_START, resolvedMonth, refresh);
        List<MfJournal> allJournals = cached.journals();
        Instant fetchedAt = cached.oldestFetchedAt();
        log.info("[supplier-balances] shopNo={} asOfMonth={} journals={} fetchedAt={}",
                shopNo, resolvedMonth, allJournals.size(), fetchedAt);

        // --- mf_account_master 逆引き map (sub_account_name → supplier_code set) ---
        Map<String, Set<String>> mfSubToCodes = buildMfSubToCodes();

        // --- MF journals を sub_account 単位 credit/debit 累積 ---
        Map<String, MfAccum> mfBySub = accumulateMfJournals(allJournals, resolvedMonth);

        // --- supplier 情報取得 ---
        Collection<MPaymentSupplier> allSuppliers = paymentSupplierService.findByShopNo(shopNo);
        Map<Integer, MPaymentSupplier> supplierByNo = new HashMap<>();
        Map<String, MPaymentSupplier> supplierByCode = new HashMap<>();
        for (MPaymentSupplier s : allSuppliers) {
            supplierByNo.put(s.getPaymentSupplierNo(), s);
            if (s.getPaymentSupplierCode() != null) supplierByCode.put(s.getPaymentSupplierCode(), s);
        }

        // --- 前期繰越 (supplier 単位期首残) ? 自社側 opening/closing に加算 ---
        // MF 側は journal #1 が accumulateMfJournals に含まれるため opening 相当が既に mf.credit 累積に入っている。
        // 自社側は t_accounts_payable_summary に期首残が未投入なので service 層で注入する。
        Map<Integer, BigDecimal> openingMap = new HashMap<>(
                openingBalanceService.getEffectiveBalanceMap(shopNo, OPENING_DATE));

        // --- 突合 ---
        List<SupplierBalanceRow> rows = new ArrayList<>();
        Set<String> processedSubs = new HashSet<>();

        // 1) self side (supplier が自社 summary にある)
        for (Map.Entry<Integer, List<TAccountsPayableSummary>> e : selfBySupplier.entrySet()) {
            Integer supplierNo = e.getKey();
            MPaymentSupplier sup = supplierByNo.get(supplierNo);
            if (sup == null) continue;
            SelfAccum self = accumulateSelf(e.getValue(), resolvedMonth);
            BigDecimal opening = nz(openingMap.remove(supplierNo));
            self.opening = self.opening.add(opening);
            self.closing = self.closing.add(opening);

            Set<String> matchedSubs = resolveMatchedSubs(mfSubToCodes, sup.getPaymentSupplierCode(), mfBySub.keySet());
            MfAccum mf = sumMfFor(mfBySub, matchedSubs, processedSubs);
            boolean masterRegistered = !matchedSubs.isEmpty();

            rows.add(buildRow(supplierNo, sup, self, mf, matchedSubs, masterRegistered));
        }

        // 2) MF side (sub_account が self 未登録)
        for (Map.Entry<String, MfAccum> e : mfBySub.entrySet()) {
            String subName = e.getKey();
            if (processedSubs.contains(subName)) continue;
            MfAccum mf = e.getValue();
            if (mf.credit.signum() == 0 && mf.debit.signum() == 0) continue;

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

        return SupplierBalancesResponse.builder()
                .shopNo(shopNo)
                .asOfMonth(resolvedMonth)
                .mfStartDate(MF_PERIOD_START)
                .fetchedAt(fetchedAt != null ? fetchedAt : Instant.now())
                .totalJournalCount(allJournals.size())
                .rows(rows)
                .summary(summary)
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
        // 累積残の視点では journal #1 (期首残高仕訳) を含めることで MF 側は opening + activity を保持する。
        // 自社側は t_accounts_payable_summary に opening が欠落しているため、buildRow 前段で
        // m_supplier_opening_balance を self.opening に加算して対称性を確保する。
        Map<String, MfAccum> map = new TreeMap<>();
        for (MfJournal j : journals) {
            if (j.transactionDate() == null || j.branches() == null) continue;
            LocalDate monthKey = MfJournalFetcher.toClosingMonthDay20(j.transactionDate());
            if (monthKey.isAfter(asOfMonth) || monthKey.isBefore(MF_PERIOD_START)) continue;
            for (MfJournal.MfBranch br : j.branches()) {
                var cr = br.creditor();
                var de = br.debitor();
                if (cr != null && MF_ACCOUNT_PAYABLE.equals(cr.accountName())
                        && cr.subAccountName() != null) {
                    map.computeIfAbsent(cr.subAccountName(), k -> new MfAccum()).credit =
                            map.get(cr.subAccountName()).credit.add(nz(cr.value()));
                }
                if (de != null && MF_ACCOUNT_PAYABLE.equals(de.accountName())
                        && de.subAccountName() != null) {
                    map.computeIfAbsent(de.subAccountName(), k -> new MfAccum()).debit =
                            map.get(de.subAccountName()).debit.add(nz(de.value()));
                }
            }
        }
        return map;
    }

    private SelfAccum accumulateSelf(List<TAccountsPayableSummary> rows, LocalDate asOfMonth) {
        SelfAccum a = new SelfAccum();
        for (TAccountsPayableSummary r : rows) {
            if (MF_PERIOD_START.equals(r.getTransactionMonth())) {
                a.opening = a.opening.add(nz(r.getOpeningBalanceTaxIncluded()));
            }
            a.change = a.change.add(PayableBalanceCalculator.effectiveChangeTaxIncluded(r));
            a.payment = a.payment.add(nz(r.getPaymentAmountSettledTaxIncluded()));
            if (asOfMonth.equals(r.getTransactionMonth())) {
                a.closing = a.closing.add(PayableBalanceCalculator.closingTaxIncluded(r));
            }
        }
        return a;
    }

    private Set<String> resolveMatchedSubs(Map<String, Set<String>> mfSubToCodes,
                                           String supplierCode, Set<String> mfSubKeys) {
        Set<String> matched = new HashSet<>();
        if (supplierCode == null) return matched;
        for (Map.Entry<String, Set<String>> e : mfSubToCodes.entrySet()) {
            if (e.getValue().contains(supplierCode) && mfSubKeys.contains(e.getKey())) {
                matched.add(e.getKey());
            }
        }
        // master 登録あるが activity 0 のケースも含めて返す
        for (Map.Entry<String, Set<String>> e : mfSubToCodes.entrySet()) {
            if (e.getValue().contains(supplierCode)) matched.add(e.getKey());
        }
        return matched;
    }

    private MfAccum sumMfFor(Map<String, MfAccum> mfBySub, Set<String> matchedSubs, Set<String> processed) {
        MfAccum sum = new MfAccum();
        for (String sn : matchedSubs) {
            MfAccum a = mfBySub.get(sn);
            if (a != null) {
                sum.credit = sum.credit.add(a.credit);
                sum.debit = sum.debit.add(a.debit);
                processed.add(sn);
            }
        }
        return sum;
    }

    private SupplierBalanceRow buildRow(Integer supplierNo, MPaymentSupplier sup,
                                        SelfAccum self, MfAccum mf, Set<String> matchedSubs,
                                        boolean masterRegistered) {
        BigDecimal mfBalance = mf.credit.subtract(mf.debit);
        BigDecimal diff = self.closing.subtract(mfBalance);
        String status = classify(self, mf, diff, masterRegistered);
        return SupplierBalanceRow.builder()
                .supplierNo(supplierNo)
                .supplierCode(sup.getPaymentSupplierCode())
                .supplierName(sup.getPaymentSupplierName())
                .selfBalance(self.closing)
                .mfBalance(mfBalance)
                .diff(diff)
                .status(status)
                .masterRegistered(masterRegistered)
                .selfOpening(self.opening)
                .selfChangeCumulative(self.change)
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
        if (abs.compareTo(MATCH_TOLERANCE) <= 0) return "MATCH";
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
        return SupplierBalancesResponse.builder()
                .shopNo(shopNo)
                .asOfMonth(null)
                .mfStartDate(MF_PERIOD_START)
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
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 4029ms:
package jp.co.oda32.domain.service.finance;

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

    public ConsistencyReviewResponse upsert(ConsistencyReviewRequest req, Integer userNo) {
        validateRequest(req);

        TConsistencyReviewPK pk = new TConsistencyReviewPK(
                req.getShopNo(), req.getEntryType(), req.getEntryKey(), req.getTransactionMonth());

        Optional<TConsistencyReview> existingOpt = reviewRepository.findById(pk);
        // 旧 review が MF_APPLY なら先にロールバック (副作用を剥がす)
        existingOpt.ifPresent(old -> {
            if (ACTION_MF_APPLY.equals(old.getActionType())) {
                rollbackVerifiedAmounts(req.getShopNo(), req.getEntryKey(),
                        req.getTransactionMonth(), old.getPreviousVerifiedAmounts());
            }
        });

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
                .previousVerifiedAmounts(previous)
                .reviewedBy(userNo)
                .reviewedAt(Instant.now())
                .note(req.getNote())
                .build();
        reviewRepository.save(review);

        return buildResponse(review, verifiedUpdated);
    }

    public void delete(Integer shopNo, String entryType, String entryKey, LocalDate transactionMonth) {
        TConsistencyReviewPK pk = new TConsistencyReviewPK(shopNo, entryType, entryKey, transactionMonth);
        Optional<TConsistencyReview> existing = reviewRepository.findById(pk);
        if (existing.isEmpty()) return;
        TConsistencyReview r = existing.get();
        if (ACTION_MF_APPLY.equals(r.getActionType())) {
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
    private Map<String, BigDecimal> applyMfOverride(ConsistencyReviewRequest req) {
        Integer supplierNo = parseSupplierNo(req.getEntryKey());
        if (supplierNo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "entryKey が supplier_no 数値ではありません: " + req.getEntryKey());
        }
        List<TAccountsPayableSummary> rows = summaryRepository
                .findByShopNoAndSupplierNoAndTransactionMonthBetweenOrderByTransactionMonthAscTaxRateAsc(
                        req.getShopNo(), supplierNo, req.getTransactionMonth(), req.getTransactionMonth());
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "対象 summary 行が見つかりません: shopNo=" + req.getShopNo()
                            + ", supplierNo=" + supplierNo + ", month=" + req.getTransactionMonth());
        }

        Map<String, BigDecimal> previous = new HashMap<>();
        for (TAccountsPayableSummary r : rows) {
            previous.put(r.getTaxRate().toPlainString(),
                    r.getVerifiedAmount() != null ? r.getVerifiedAmount() : BigDecimal.ZERO);
        }

        if (ENTRY_SELF_ONLY.equals(req.getEntryType())) {
            // 自社取消: 全税率行を 0 に
            for (TAccountsPayableSummary r : rows) {
                r.setVerifiedAmount(BigDecimal.ZERO);
                r.setVerifiedAmountTaxExcluded(BigDecimal.ZERO);
                r.setVerifiedManually(true);
                r.setVerificationResult(1);
                r.setMfExportEnabled(false);
                summaryRepository.save(r);
            }
        } else if (ENTRY_AMOUNT_MISMATCH.equals(req.getEntryType())) {
            // 税率別 change 比で target 按分
            BigDecimal paymentSettled = rows.stream()
                    .map(r -> nz(r.getPaymentAmountSettledTaxIncluded()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal target = nz(req.getMfSnapshot()).add(paymentSettled);
            BigDecimal changeSum = rows.stream()
                    .map(r -> nz(r.getTaxIncludedAmountChange()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal assigned = BigDecimal.ZERO;
            TAccountsPayableSummary largest = null;
            BigDecimal largestChange = BigDecimal.valueOf(Long.MIN_VALUE);
            for (TAccountsPayableSummary r : rows) {
                BigDecimal ch = nz(r.getTaxIncludedAmountChange());
                if (ch.compareTo(largestChange) > 0) { largestChange = ch; largest = r; }
            }
            for (TAccountsPayableSummary r : rows) {
                BigDecimal allocated;
                if (changeSum.signum() == 0) {
                    // 全 change 0 の場合は代表行に一括
                    allocated = r == largest ? target : BigDecimal.ZERO;
                } else {
                    BigDecimal ch = nz(r.getTaxIncludedAmountChange());
                    allocated = target.multiply(ch).divide(changeSum, 0, RoundingMode.DOWN);
                }
                assigned = assigned.add(allocated);
                r.setVerifiedAmount(allocated);
                BigDecimal divisor = BigDecimal.valueOf(100).add(nz(r.getTaxRate()));
                r.setVerifiedAmountTaxExcluded(
                        allocated.multiply(BigDecimal.valueOf(100)).divide(divisor, 0, RoundingMode.DOWN));
                r.setVerifiedManually(true);
                r.setVerificationResult(1);
                r.setMfExportEnabled(true);
            }
            // 端数誤差は最大行で吸収
            BigDecimal diff = target.subtract(assigned);
            if (diff.signum() != 0 && largest != null) {
                largest.setVerifiedAmount(nz(largest.getVerifiedAmount()).add(diff));
                BigDecimal divisor = BigDecimal.valueOf(100).add(nz(largest.getTaxRate()));
                largest.setVerifiedAmountTaxExcluded(
                        largest.getVerifiedAmount().multiply(BigDecimal.valueOf(100))
                                .divide(divisor, 0, RoundingMode.DOWN));
            }
            for (TAccountsPayableSummary r : rows) summaryRepository.save(r);
        }
        log.info("[consistency-review] MF_APPLY shopNo={} supplier={} month={} type={} target={}",
                req.getShopNo(), supplierNo, req.getTransactionMonth(), req.getEntryType(), req.getMfSnapshot());
        return previous;
    }

    private void rollbackVerifiedAmounts(Integer shopNo, String entryKey, LocalDate month,
                                          Map<String, BigDecimal> previous) {
        if (previous == null || previous.isEmpty()) return;
        Integer supplierNo = parseSupplierNo(entryKey);
        if (supplierNo == null) return;
        List<TAccountsPayableSummary> rows = summaryRepository
                .findByShopNoAndSupplierNoAndTransactionMonthBetweenOrderByTransactionMonthAscTaxRateAsc(
                        shopNo, supplierNo, month, month);
        for (TAccountsPayableSummary r : rows) {
            String taxKey = r.getTaxRate().toPlainString();
            if (previous.containsKey(taxKey)) {
                BigDecimal prev = previous.get(taxKey);
                r.setVerifiedAmount(prev);
                // 税抜は逆算で復元 (元値を持っていないため)
                if (prev != null && prev.signum() != 0) {
                    BigDecimal divisor = BigDecimal.valueOf(100).add(nz(r.getTaxRate()));
                    r.setVerifiedAmountTaxExcluded(
                            prev.multiply(BigDecimal.valueOf(100)).divide(divisor, 0, RoundingMode.DOWN));
                } else {
                    r.setVerifiedAmountTaxExcluded(null);
                }
                summaryRepository.save(r);
            }
        }
        log.info("[consistency-review] ROLLBACK shopNo={} supplier={} month={} rows={}",
                shopNo, supplierNo, month, rows.size());
    }

    private Integer parseSupplierNo(String entryKey) {
        try { return Integer.parseInt(entryKey); } catch (NumberFormatException e) { return null; }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private ConsistencyReviewResponse buildResponse(TConsistencyReview r, boolean verifiedUpdated) {
        String reviewerName = loginUserRepository.findById(r.getReviewedBy())
                .map(MLoginUser::getUserName).orElse(null);
        return ConsistencyReviewResponse.builder()
                .shopNo(r.getPk().getShopNo())
                .entryType(r.getPk().getEntryType())
                .entryKey(r.getPk().getEntryKey())
                .transactionMonth(r.getPk().getTransactionMonth())
                .actionType(r.getActionType())
                .reviewStatus(ACTION_IGNORE.equals(r.getActionType()) ? "IGNORED" : "MF_APPLIED")
                .selfSnapshot(r.getSelfSnapshot())
                .mfSnapshot(r.getMfSnapshot())
                .reviewedBy(r.getReviewedBy())
                .reviewedByName(reviewerName)
                .reviewedAt(r.getReviewedAt())
                .note(r.getNote())
                .verifiedAmountUpdated(verifiedUpdated)
                .build();
    }

    public record ReviewKey(String entryType, String entryKey, LocalDate transactionMonth) {}

    public record ReviewInfo(String actionType, BigDecimal selfSnapshot, BigDecimal mfSnapshot,
                              Integer reviewedBy, String reviewedByName,
                              Instant reviewedAt, String note) {
        public String reviewStatus() {
            return "IGNORE".equals(actionType) ? "IGNORED" : "MF_APPLIED";
        }
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\batch\\finance\\AccountsPayableSummaryTasklet.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\batch\\finance\\AccountsPayableVerificationTasklet.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\batch\\finance\\AccountsPayableBackfillTasklet.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\batch\\finance\\service\\SmilePaymentVerifier.java" in C:\project\odamitsu-data-hub
 succeeded in 1883ms:
package jp.co.oda32.batch.finance;

import jp.co.oda32.batch.finance.model.VerificationResult;
import jp.co.oda32.batch.finance.service.AccountsPayableSummaryCalculator;
import jp.co.oda32.batch.finance.service.SmilePaymentVerifier;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.smile.TSmilePayment;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import jp.co.oda32.domain.service.smile.TSmilePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 20日締め買掛金額を集計し、TAccountsPayableSummaryテーブルに登録するTasklet
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class AccountsPayableSummaryTasklet implements Tasklet {

    private final TAccountsPayableSummaryService tAccountsPayableSummaryService;
    private final TSmilePaymentService tSmilePaymentService;
    private final AccountsPayableSummaryCalculator summaryCalculator;
    private final SmilePaymentVerifier smilePaymentVerifier;

    // バッチジョブの引数として渡された targetDate を取得
    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        try {
            // targetDate を LocalDate に変換
            LocalDate startDate = LocalDate.parse(targetDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

            // 期間を設定（前月21日から当月20日まで）
            LocalDate periodStartDate = YearMonth.from(startDate).minusMonths(1).atDay(21); // 前月21日
            LocalDate periodEndDate = YearMonth.from(startDate).atDay(20); // 当月20日

            // データ集計処理（買掛金額の合計を計算）
            List<TAccountsPayableSummary> summaries = summaryCalculator.calculatePayableSummaries(periodStartDate, periodEndDate);

            // 集計結果をTAccountsPayableSummaryテーブルに一括登録
            if (!summaries.isEmpty()) {
                // SMILE支払情報の取得
                List<TSmilePayment> smilePayments = tSmilePaymentService.findByYearMonth(
                        YearMonth.from(periodEndDate).plusMonths(1).format(DateTimeFormatter.ofPattern("yyyyMM")));

                // SMILE支払情報との照合結果を取得（仕入先コードごとの総額比較）
                // 注意: verifyWithSmilePaymentメソッド内で、差額が5円以内の場合は
                // summariesオブジェクトのtaxIncludedAmountChangeがSMILE支払額に合わせて更新されます
                Map<String, VerificationResult> verificationResults = smilePaymentVerifier.verifyWithSmilePayment(summaries, smilePayments);

                // 一旦全ての調整されたsummariesを保存（トランザクション内で確実に更新するため）
                log.info("買掛金額の調整結果をデータベースに一括保存します。");
                this.tAccountsPayableSummaryService.saveAll(summaries);

                for (TAccountsPayableSummary summary : summaries) {
                    // 検証結果の取得
                    VerificationResult result = verificationResults.get(summary.getSupplierCode());

                    // 既存のレコードを検索
                    TAccountsPayableSummary exist = this.tAccountsPayableSummaryService.getByPK(
                            summary.getShopNo(),
                            summary.getSupplierNo(),
                            summary.getTransactionMonth(),
                            summary.getTaxRate()
                    );

                    if (exist != null) {
                        // 既存レコードの更新
                        // 注意: summaryオブジェクトのtaxIncludedAmountChangeは
                        // 差額が5円以内の場合はSMILE支払額に合わせて更新されている可能性があります
                        log.debug("既存レコード更新: 仕入先コード={}, 税率={}%, 元の金額={}円, 更新後金額={}円",
                                summary.getSupplierCode(), summary.getTaxRate(),
                                exist.getTaxIncludedAmountChange(), summary.getTaxIncludedAmountChange());

                        // 重要：調整後のtaxIncludedAmountChangeを確実に反映
                        exist.setTaxIncludedAmountChange(summary.getTaxIncludedAmountChange());
                        exist.setTaxExcludedAmountChange(summary.getTaxExcludedAmountChange());

                        // 検証結果がある場合は検証結果フラグと差額を設定
                        if (result != null) {
                            // 調整済みの場合や差額が5円未満の場合は常に「一致」とする
                            if (result.isAdjustedToSmilePayment() ||
                                    (result.getDifference() != null &&
                                            result.getDifference().abs().compareTo(new java.math.BigDecimal(5)) < 0)) {
                                exist.setVerificationResult(1); // 一致
                                log.debug("差額が5円未満または調整済みのため「一致」に設定: 仕入先コード={}, 差額={}円",
                                        summary.getSupplierCode(), result.getDifference());
                            } else {
                                exist.setVerificationResult(result.isMatched() ? 1 : 0);
                            }
                            exist.setPaymentDifference(result.getDifference()); // 差額を設定
                        }

                        this.tAccountsPayableSummaryService.save(exist);
                    } else {
                        // 新規レコードの作成
                        if (result != null) {
                            // 調整済みの場合や差額が5円未満の場合は常に「一致」とする
                            if (result.isAdjustedToSmilePayment() ||
                                    (result.getDifference() != null &&
                                            result.getDifference().abs().compareTo(new java.math.BigDecimal(5)) < 0)) {
                                summary.setVerificationResult(1); // 一致
                                log.debug("差額が5円未満または調整済みのため「一致」に設定: 仕入先コード={}, 差額={}円",
                                        summary.getSupplierCode(), result.getDifference());
                            } else {
                                summary.setVerificationResult(result.isMatched() ? 1 : 0);
                            }
                            summary.setPaymentDifference(result.getDifference()); // 差額を設定
                        }

                        this.tAccountsPayableSummaryService.save(summary);
                    }
                }
                log.info("買掛金額の集計結果をデータベースに保存しました。件数: {}", summaries.size());
            } else {
                log.info("新しい買掛金額の集計結果はありません。");
            }
        } catch (Exception e) {
            log.error("買掛金額の集計中にエラーが発生しました。", e);
            throw e; // バッチを失敗としてマーク
        }

        return RepeatStatus.FINISHED;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2028ms:
package jp.co.oda32.batch.finance;

import jp.co.oda32.batch.finance.model.VerificationResult;
import jp.co.oda32.batch.finance.service.SmilePaymentVerifier;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.smile.TSmilePayment;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import jp.co.oda32.domain.service.smile.TSmilePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 買掛金額とSMILE支払情報との照合を行うTasklet
 * （チェック処理のみ、集計処理は別タスクレットで実行済み）
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class AccountsPayableVerificationTasklet implements Tasklet {

    private final TAccountsPayableSummaryService tAccountsPayableSummaryService;
    private final TSmilePaymentService tSmilePaymentService;
    private final SmilePaymentVerifier smilePaymentVerifier;

    // バッチジョブの引数として渡された targetDate を取得
    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        try {
            // targetDate を LocalDate に変換
            LocalDate startDate = LocalDate.parse(targetDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

            // 期間を設定（前月21日から当月20日まで）
            LocalDate periodEndDate = YearMonth.from(startDate).atDay(20); // 当月20日

            log.info("買掛金額とSMILE支払情報の照合を開始します。対象月: {}", periodEndDate);

            // 既存の買掛金集計データを取得
            List<TAccountsPayableSummary> summaries = tAccountsPayableSummaryService.findByTransactionMonth(periodEndDate);

            if (!summaries.isEmpty()) {
                // SMILE支払情報の取得（翌月の支払情報を取得）
                List<TSmilePayment> smilePayments = tSmilePaymentService.findByYearMonth(
                        YearMonth.from(periodEndDate).plusMonths(1).format(DateTimeFormatter.ofPattern("yyyyMM")));

                log.info("照合対象: 買掛金集計データ {}件, SMILE支払情報 {}件", summaries.size(), smilePayments.size());

                // SMILE支払情報との照合結果を取得（仕入先コードごとの総額比較）
                // 注意: verifyWithSmilePaymentメソッド内で、差額が5円以内の場合は
                // summariesオブジェクトのtaxIncludedAmountChangeがSMILE支払額に合わせて更新されます
                Map<String, VerificationResult> verificationResults = smilePaymentVerifier.verifyWithSmilePayment(summaries, smilePayments);

                // 一旦全ての調整されたsummariesを保存（トランザクション内で確実に更新するため）
                log.info("買掛金額の調整結果をデータベースに一括保存します。");
                this.tAccountsPayableSummaryService.saveAll(summaries);

                // 検証結果を既存レコードに反映
                int updatedCount = 0;
                for (TAccountsPayableSummary summary : summaries) {
                    // 検証結果の取得
                    VerificationResult result = verificationResults.get(summary.getSupplierCode());

                    // 既存のレコードを検索（確実に最新のデータを取得）
                    TAccountsPayableSummary exist = this.tAccountsPayableSummaryService.getByPK(
                            summary.getShopNo(),
                            summary.getSupplierNo(),
                            summary.getTransactionMonth(),
                            summary.getTaxRate()
                    );

                    if (exist != null) {
                        // 検証結果がある場合は検証結果フラグと差額を設定
                        if (result != null) {
                            // 調整済みの場合や差額が5円未満の場合は常に「一致」とする
                            if (result.isAdjustedToSmilePayment() ||
                                    (result.getDifference() != null &&
                                            result.getDifference().abs().compareTo(new java.math.BigDecimal(5)) < 0)) {
                                exist.setVerificationResult(1); // 一致
                                log.debug("差額が5円未満または調整済みのため「一致」に設定: 仕入先コード={}, 差額={}円",
                                        summary.getSupplierCode(), result.getDifference());
                            } else {
                                exist.setVerificationResult(result.isMatched() ? 1 : 0);
                            }
                            exist.setPaymentDifference(result.getDifference()); // 差額を設定
                        } else {
                            // 検証結果がない場合（SMILE支払情報がない場合）
                            exist.setVerificationResult(0); // 不一致
                            exist.setPaymentDifference(null);
                            log.warn("SMILE支払情報が存在しないため「不一致」に設定: 仕入先コード={}", summary.getSupplierCode());
                        }

                        // 重要：調整後のtaxIncludedAmountChangeを確実に反映
                        exist.setTaxIncludedAmountChange(summary.getTaxIncludedAmountChange());
                        exist.setTaxExcludedAmountChange(summary.getTaxExcludedAmountChange());

                        this.tAccountsPayableSummaryService.save(exist);
                        updatedCount++;
                    }
                }
                log.info("買掛金額の検証結果をデータベースに反映しました。更新件数: {}", updatedCount);
            } else {
                log.info("照合対象の買掛金集計データが存在しません。");
            }
        } catch (Exception e) {
            log.error("買掛金額の照合中にエラーが発生しました。", e);
            throw e; // バッチを失敗としてマーク
        }

        return RepeatStatus.FINISHED;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2023ms:
package jp.co.oda32.batch.finance;

import jp.co.oda32.batch.finance.service.PayableMonthlyAggregator;
import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import jp.co.oda32.domain.service.finance.mf.MfPaymentAggregator;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 買掛金累積残 opening / payment_settled の過去データ再集計 Tasklet。
 * <p>
 * Phase A: opening 繰越。Phase B': payment_settled 按分 + payment-only 行生成。
 * <p>
 * <b>Tx 設計 (レビュー R1 対応)</b>:
 * 月単位の tx を明示的に制御するため {@link TransactionTemplate} を使用。
 * `@Transactional(REQUIRES_NEW)` は Spring AOP の self-invocation 制約で無効化されるため不採用。
 * <p>
 * <b>起点強制 (R5)</b>:
 * {@code fromMonth != 2025-06-20} の場合は {@code allowPartialResume=true} 必須。
 * Phase A 旧式 closing が残る可能性があるため、admin が明示同意した場合のみ許可。
 * <p>
 * 設計書: claudedocs/design-phase-b-prime-payment-settled.md §4.2
 */
@Component
@Log4j2
@StepScope
public class AccountsPayableBackfillTasklet implements Tasklet {

    private final TAccountsPayableSummaryService summaryService;
    private final PayableMonthlyAggregator monthlyAggregator;
    private final MfPaymentAggregator mfPaymentAggregator;
    private final TransactionTemplate monthTxTemplate;

    @Value("#{jobParameters['fromMonth']}")
    private String fromMonth;

    @Value("#{jobParameters['toMonth']}")
    private String toMonth;

    @Value("#{jobParameters['allowPartialResume'] ?: 'false'}")
    private String allowPartialResumeParam;

    private static final LocalDate EXPECTED_FROM_MONTH = LocalDate.of(2025, 6, 20);

    public AccountsPayableBackfillTasklet(
            TAccountsPayableSummaryService summaryService,
            PayableMonthlyAggregator monthlyAggregator,
            MfPaymentAggregator mfPaymentAggregator,
            PlatformTransactionManager txManager) {
        this.summaryService = summaryService;
        this.monthlyAggregator = monthlyAggregator;
        this.mfPaymentAggregator = mfPaymentAggregator;
        // 月単位 REQUIRES_NEW tx
        this.monthTxTemplate = new TransactionTemplate(txManager);
        this.monthTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate from = LocalDate.parse(fromMonth, DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDate to = LocalDate.parse(toMonth, DateTimeFormatter.ISO_LOCAL_DATE);
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "fromMonth (" + fromMonth + ") が toMonth (" + toMonth + ") より後です");
        }
        boolean allowPartial = "true".equalsIgnoreCase(allowPartialResumeParam);
        if (!from.equals(EXPECTED_FROM_MONTH) && !allowPartial) {
            throw new IllegalArgumentException(
                    "fromMonth は " + EXPECTED_FROM_MONTH + " 固定です (Phase B' 整合性)。"
                            + "途中月から再開する場合は allowPartialResume=true を指定してください。");
        }
        if (allowPartial && !from.equals(EXPECTED_FROM_MONTH)) {
            log.warn("[backfill] 途中再開モード: fromMonth={}, 前月 closing が Phase A 旧式の可能性あり。"
                    + " 実データ検証後に再実行を推奨します。", fromMonth);
        }

        int processedMonths = 0;
        int totalUpdated = 0;
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            LocalDate monthEnd = cursor;
            // 月単位 REQUIRES_NEW tx で独立 commit (R1 fix: @Transactional self-invocation 制約回避)
            Integer updated = monthTxTemplate.execute(status -> processOneMonth(monthEnd));
            totalUpdated += (updated != null ? updated : 0);
            processedMonths++;
            log.info("[backfill] {} ? 行更新 {} 件", monthEnd, updated);
            YearMonth nextYm = YearMonth.from(monthEnd).plusMonths(1);
            cursor = nextYm.atDay(20);
        }
        log.info("[backfill] 完了: 処理月数={}, 更新行合計={}", processedMonths, totalUpdated);
        return RepeatStatus.FINISHED;
    }

    /** 1 ヶ月分の opening + payment_settled + payment-only 行を処理。TransactionTemplate で外側から tx 境界。 */
    int processOneMonth(LocalDate periodEndDate) {
        List<TAccountsPayableSummary> current = summaryService.findByTransactionMonth(periodEndDate);

        PayableMonthlyAggregator.PrevMonthData prev = monthlyAggregator.buildPrevMonthData(periodEndDate);

        // 既存行を currMap に格納 (payment-only 既存 row の上書き用)
        Map<String, TAccountsPayableSummary> currMap = new HashMap<>();
        for (TAccountsPayableSummary r : current) currMap.put(PayableMonthlyAggregator.rowKey(r), r);

        // opening + payment_settled 適用
        monthlyAggregator.applyOpenings(current, prev);
        monthlyAggregator.applyPaymentSettled(current, prev);
        // 案 A (2026-04-23): MF 期首以降は MF debit で上書き
        Map<Integer, BigDecimal> mfDebit = mfPaymentAggregator.getMfDebitBySupplierForMonth(
                FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, periodEndDate);
        monthlyAggregator.overrideWithMfDebit(current, mfDebit, periodEndDate);

        // payment-only 行生成 (支plier 単位 change 判定)
        List<TAccountsPayableSummary> generated = monthlyAggregator.generatePaymentOnlyRows(
                prev, current, periodEndDate, currMap);

        List<TAccountsPayableSummary> toSave = new ArrayList<>(current);
        for (TAccountsPayableSummary po : generated) {
            if (!toSave.contains(po)) toSave.add(po);
        }

        if (!toSave.isEmpty()) {
            summaryService.saveAll(toSave);
        }
        return toSave.size();
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2114ms:
package jp.co.oda32.batch.finance.service;

import jp.co.oda32.batch.finance.helper.TaxCalculationHelper;
import jp.co.oda32.batch.finance.model.TaxBreakdown;
import jp.co.oda32.batch.finance.model.VerificationResult;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.model.master.MSupplierShopMapping;
import jp.co.oda32.domain.model.smile.TSmilePayment;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import jp.co.oda32.domain.service.master.MSupplierShopMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SMILE支払情報との照合を行うサービスクラス
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class SmilePaymentVerifier {

    private final MSupplierShopMappingService mSupplierShopMappingService;
    private final MPaymentSupplierService mPaymentSupplierService;
    private final TAccountsPayableSummaryService tAccountsPayableSummaryService;

    /**
     * SMILE支払情報との照合を行い、検証結果を返します。
     * 仕入先ごとに買掛金額の総額とSMILE支払額の総額を比較します。
     * 差額が5円未満の場合はSMILE支払額に合わせます。
     *
     * @param summaries     買掛金集計結果
     * @param smilePayments SMILE支払情報
     * @return 仕入先コードをキーとした検証結果のマップ
     */
    public Map<String, VerificationResult> verifyWithSmilePayment(
            List<TAccountsPayableSummary> summaries,
            List<TSmilePayment> smilePayments) {

        Map<String, VerificationResult> results = new HashMap<>();

        // SMILE支払情報を仕入先コードでグループ化して合計額を算出
        Map<String, BigDecimal> smilePaymentMap = smilePayments.stream()
                .collect(Collectors.groupingBy(
                        TSmilePayment::getSupplierCode,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                TSmilePayment::getPaymentAmount,
                                BigDecimal::add
                        )
                ));

        // 仕入先名情報を保持するマップ
        Map<String, String> supplierNameMap = new HashMap<>();

        // SMILE支払情報から仕入先名を収集
        for (TSmilePayment payment : smilePayments) {
            String supplierCode = payment.getSupplierCode();
            if (!supplierNameMap.containsKey(supplierCode) && payment.getSupplierName1() != null) {
                supplierNameMap.put(supplierCode, payment.getSupplierName1());
            }
        }

        // shop_no:2の買掛データをshop_no:1の仕入先コードにマッピング
        Map<String, List<String>> shop2ToShop1SupplierMap = new HashMap<>();

        for (TAccountsPayableSummary summary : summaries) {
            if (summary.getShopNo() == 2) {
                // shop_no:2の仕入先コードに対応するshop_no:1の仕入先コードを検索
                Optional<MSupplierShopMapping> mapping = mSupplierShopMappingService
                        .findBySourceShopNoAndSupplierCode(2, summary.getSupplierCode());

                if (mapping.isPresent()) {
                    String targetSupplierCode = mapping.get().getTargetSupplierCode();

                    // マッピング情報をマップに追加
                    shop2ToShop1SupplierMap
                            .computeIfAbsent(targetSupplierCode, k -> new ArrayList<>())
                            .add(summary.getSupplierCode());
                }
            }

            // 仕入先名を取得（まだマップに含まれていない場合のみ）
            if (!supplierNameMap.containsKey(summary.getSupplierCode())) {
                // 支払先サービスから仕入先名を取得
                try {
                    MPaymentSupplier paymentSupplier = mPaymentSupplierService.getByPaymentSupplierCode(
                            summary.getShopNo(), summary.getSupplierCode());
                    if (paymentSupplier != null) {
                        supplierNameMap.put(summary.getSupplierCode(), paymentSupplier.getPaymentSupplierName());
                    } else {
                        supplierNameMap.put(summary.getSupplierCode(), "不明");
                    }
                } catch (Exception e) {
                    log.warn("仕入先名の取得に失敗しました: {}", summary.getSupplierCode(), e);
                    supplierNameMap.put(summary.getSupplierCode(), "不明");
                }
            }
        }

        // 仕入先コードごとに買掛金額を合計（全税率分を合算）- 税込金額を使用
        Map<String, BigDecimal> supplierTotalIncTaxMap = summaries.stream()
                .collect(Collectors.groupingBy(
                        TAccountsPayableSummary::getSupplierCode,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                TAccountsPayableSummary::getTaxIncludedAmountChange, // 税込金額を使用して集計
                                BigDecimal::add
                        )
                ));

        // 仕入先コードごとに買掛金額を合計（全税率分を合算）- 税抜金額を使用
        Map<String, BigDecimal> supplierTotalExcTaxMap = summaries.stream()
                .collect(Collectors.groupingBy(
                        TAccountsPayableSummary::getSupplierCode,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                TAccountsPayableSummary::getTaxExcludedAmountChange, // 税抜金額を使用して集計
                                BigDecimal::add
                        )
                ));

        // 支払情報が存在しない場合のログ出力と特別処理
        List<String> supplierCodesWithPayable = new ArrayList<>(supplierTotalIncTaxMap.keySet());
        supplierCodesWithPayable.removeAll(smilePaymentMap.keySet());

        if (!supplierCodesWithPayable.isEmpty()) {
            log.warn("以下の仕入先コードはSMILE支払情報が存在しませんが、買掛金データが存在します: {}",
                    String.join(", ", supplierCodesWithPayable));

            // 支払情報がない仕入先について詳細なログを出力
            for (String supplierCode : supplierCodesWithPayable) {
                BigDecimal payableAmount = supplierTotalIncTaxMap.get(supplierCode);
                String supplierName = supplierNameMap.getOrDefault(supplierCode, "不明");

                log.warn("支払情報なし: 仕入先コード={}, 仕入先名={}, 買掛金額={}円",
                        supplierCode, supplierName, payableAmount);

                // 差額を明示的に設定（支払情報がない場合は差額は買掛金額の負値）
                BigDecimal difference = BigDecimal.ZERO.subtract(payableAmount);

                // 支払情報がないことを明示的に記録するために検証結果を「不一致」と設定
                updateVerificationFlags(summaries, supplierCode, false, difference);

                // マネーフォワードエクスポート不可のフラグを設定
                for (TAccountsPayableSummary summary : summaries) {
                    if (summary.getSupplierCode().equals(supplierCode)) {
                        if (Boolean.TRUE.equals(summary.getVerifiedManually())) {
                            log.info("手動確定済みのためMFエクスポートフラグ更新をスキップ: 仕入先コード={}, 税率={}%",
                                    supplierCode, summary.getTaxRate());
                            continue;
                        }
                        summary.setMfExportEnabled(false);
                        log.info("仕入先コード={}のマネーフォワードエクスポートを無効に設定しました", supplierCode);
                    }
                }

                // 結果をマップに保存
                VerificationResult result = new VerificationResult(
                        supplierCode,
                        null,
                        payableAmount,
                        BigDecimal.ZERO, // 支払情報なし
                        difference,
                        false
                );
                results.put(supplierCode, result);
            }
        }

        // 仕入先コードと税率ごとの明細を保持するマップ
        Map<String, Map<BigDecimal, TaxBreakdown>> supplierTaxBreakdownMap = new HashMap<>();

        // 仕入先ごとの税率別内訳を計算
        for (TAccountsPayableSummary summary : summaries) {
            String supplierCode = summary.getSupplierCode();
            BigDecimal taxRate = summary.getTaxRate();
            BigDecimal taxIncludedAmount = summary.getTaxIncludedAmountChange();
            BigDecimal taxExcludedAmount = summary.getTaxExcludedAmountChange();

            // 仕入先コードごとのマップを取得または作成
            Map<BigDecimal, TaxBreakdown> taxBreakdownMap = supplierTaxBreakdownMap
                    .computeIfAbsent(supplierCode, k -> new HashMap<>());

            // 税率ごとの内訳を取得または作成
            TaxBreakdown breakdown = taxBreakdownMap
                    .computeIfAbsent(taxRate, k -> new TaxBreakdown(BigDecimal.ZERO, BigDecimal.ZERO));

            // 金額を加算
            breakdown.addTaxExcludedAmount(taxExcludedAmount);
            breakdown.addTaxIncludedAmount(taxIncludedAmount);
        }

        // 各仕入先コードに対する処理
        for (String supplierCode : supplierTotalIncTaxMap.keySet()) {
            BigDecimal totalAccountsPayableIncTax = supplierTotalIncTaxMap.get(supplierCode);
            BigDecimal totalAccountsPayableExcTax = supplierTotalExcTaxMap.getOrDefault(supplierCode, BigDecimal.ZERO);

            // マッピングされている仕入先コードのリストを取得
            List<String> mappedCodes = shop2ToShop1SupplierMap.getOrDefault(supplierCode, new ArrayList<>());

            // SMILE支払情報から支払額の総額を取得
            BigDecimal smilePaymentAmount = smilePaymentMap.getOrDefault(supplierCode, BigDecimal.ZERO);

            // マッピングされた仕入先コードの支払額も合算
            for (String mappedCode : mappedCodes) {
                smilePaymentAmount = smilePaymentAmount.add(
                        smilePaymentMap.getOrDefault(mappedCode, BigDecimal.ZERO));
            }

            // 検証結果を作成（仕入先コードごとの総額比較）
            if (totalAccountsPayableIncTax.compareTo(BigDecimal.ZERO) > 0) {
                // 小数点以下を切り捨て
                BigDecimal truncatedTotalAccountsPayableExcTax = totalAccountsPayableExcTax.setScale(0, RoundingMode.DOWN);
                BigDecimal truncatedTotalAccountsPayableIncTax = totalAccountsPayableIncTax.setScale(0, RoundingMode.DOWN);

                // 税率ごとの内訳を取得
                Map<BigDecimal, TaxBreakdown> taxBreakdownMap = supplierTaxBreakdownMap.getOrDefault(supplierCode, new HashMap<>());

                // 税抜金額から正確に計算した税込金額（比較用）
                BigDecimal recalculatedTaxIncluded = TaxCalculationHelper.calculateTaxIncludedAmount(taxBreakdownMap);
                BigDecimal truncatedRecalculatedTaxIncluded = recalculatedTaxIncluded.setScale(0, RoundingMode.DOWN);

                BigDecimal truncatedSmilePaymentAmount = smilePaymentAmount.setScale(0, RoundingMode.DOWN);
                // 再計算した税込金額とSMILE支払額の差額
                BigDecimal truncatedDifference = truncatedSmilePaymentAmount.subtract(truncatedRecalculatedTaxIncluded);

                // 仕入先名を取得
                String supplierName = supplierNameMap.getOrDefault(supplierCode, "不明");

                // 重要：差額が5円未満の場合、SMILE支払額に合わせる
                boolean adjustToSmilePayment = truncatedDifference.abs().compareTo(jp.co.oda32.constant.FinanceConstants.PAYMENT_VERIFICATION_TOLERANCE) < 0
                        && truncatedDifference.compareTo(BigDecimal.ZERO) != 0;

                // 税込金額を指定（差額が5円未満ならSMILE支払額に合わせる）
                BigDecimal finalTaxIncludedAmount = adjustToSmilePayment ?
                        truncatedSmilePaymentAmount : truncatedRecalculatedTaxIncluded;

                // 検証結果を生成（adjustToSmilePaymentフラグも設定）
                VerificationResult result = new VerificationResult(
                        supplierCode,
                        null,  // 税率はnull（全税率の合計を比較するため）
                        finalTaxIncludedAmount, // 調整後の買掛金額（税込）
                        truncatedSmilePaymentAmount, // SMILE支払額（税込）
                        truncatedDifference,     // 差額
                        adjustToSmilePayment     // SMILE支払額に調整したかどうか
                );

                // 差額が5円未満または0円なら常に「一致」のフラグを設定
                boolean isMatched = truncatedDifference.abs().compareTo(jp.co.oda32.constant.FinanceConstants.PAYMENT_VERIFICATION_TOLERANCE) < 0
                        || truncatedDifference.compareTo(BigDecimal.ZERO) == 0;

                // 対象のサマリーデータの検証結果フラグを更新
                updateVerificationFlags(summaries, supplierCode, isMatched, truncatedDifference);

                // 検証結果が「一致」の場合のみマネーフォワードエクスポート可能に設定
                for (TAccountsPayableSummary summary : summaries) {
                    if (summary.getSupplierCode().equals(supplierCode)) {
                        if (Boolean.TRUE.equals(summary.getVerifiedManually())) {
                            log.info("手動確定済みのためMFエクスポートフラグ更新をスキップ: 仕入先コード={}, 税率={}%",
                                    supplierCode, summary.getTaxRate());
                            continue;
                        }
                        if (summary.getVerificationResult() != null && summary.getVerificationResult() == 1) {
                            summary.setMfExportEnabled(true);
                            log.debug("検証結果「一致」のためマネーフォワードエクスポート可能に設定: 仕入先コード={}, 税率={}%",
                                    supplierCode, summary.getTaxRate());
                        } else {
                            summary.setMfExportEnabled(false);
                            log.debug("検証結果「不一致」のためマネーフォワードエクスポート不可に設定: 仕入先コード={}, 税率={}%",
                                    supplierCode, summary.getTaxRate());
                        }
                    }
                }

                // 結果をマップに保存
                results.put(supplierCode, result);

                // 税率ごとの内訳をログ出力
                StringBuilder taxBreakdownLog = new StringBuilder();
                taxBreakdownLog.append("税率別内訳: ");

                List<BigDecimal> sortedTaxRates = new ArrayList<>(taxBreakdownMap.keySet());
                sortedTaxRates.sort(Comparator.naturalOrder());

                for (BigDecimal taxRate : sortedTaxRates) {
                    TaxBreakdown breakdown = taxBreakdownMap.get(taxRate);
                    BigDecimal taxExcluded = breakdown.getTaxExcludedAmount();

                    // 正確に計算した税込金額
                    BigDecimal taxAmount = taxExcluded.multiply(taxRate)
                            .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
                    BigDecimal correctTaxIncluded = taxExcluded.add(taxAmount);

                    taxBreakdownLog.append(String.format("【%s%%】税抜=%s円,税込=%s円 ",
                            taxRate,
                            taxExcluded.setScale(0, RoundingMode.DOWN),
                            correctTaxIncluded.setScale(0, RoundingMode.DOWN)));
                }

                // メインログ出力
                if (adjustToSmilePayment) {
                    // 差額が5円未満の場合、特別なログを出力
                    log.info("仕入先[{}:{}], 買掛金額合計: 税抜={}円, 買掛集計税込={}円, SMILE支払総額: {}円, 差額: {}円あります（請求書金額に合わせます）, {}",
                            supplierCode,
                            supplierName,
                            truncatedTotalAccountsPayableExcTax,
                            truncatedRecalculatedTaxIncluded,
                            truncatedSmilePaymentAmount,
                            truncatedDifference,
                            taxBreakdownLog.toString());

                    // 仕入明細のtaxIncludedAmountChangeを更新（差額が5円未満の場合）
                    updateAccountsPayableSummaries(summaries, supplierCode, truncatedSmilePaymentAmount, truncatedRecalculatedTaxIncluded);

                    // 調整後のデータをデータベースに確実に保存
                    List<TAccountsPayableSummary> targetSummaries = summaries.stream()
                            .filter(s -> s.getSupplierCode().equals(supplierCode))
                            .collect(Collectors.toList());

                    for (TAccountsPayableSummary summary : targetSummaries) {
                        log.info("調整後のデータを確実に保存: 仕入先コード={}, 税率={}%, 調整後税込金額={}円",
                                supplierCode, summary.getTaxRate(), summary.getTaxIncludedAmountChange());
                        TAccountsPayableSummary saved = tAccountsPayableSummaryService.save(summary);
                        // 保存後の値も確認
                        log.info("保存後の値を確認: 仕入先コード={}, 税率={}%, 税込金額={}円",
                                saved.getSupplierCode(), saved.getTaxRate(), saved.getTaxIncludedAmountChange());
                    }
                } else {
                    // 通常のログ出力
                    log.info("仕入先[{}:{}], 買掛金額合計: 税抜={}円, 買掛集計税込={}円, SMILE支払総額: {}円, 差額: {}円, {}",
                            supplierCode,
                            supplierName,
                            truncatedTotalAccountsPayableExcTax,
                            truncatedRecalculatedTaxIncluded,
                            truncatedSmilePaymentAmount,
                            truncatedDifference,
                            taxBreakdownLog.toString());
                }
            }
        }

        return results;
    }

    /**
     * 対象の仕入先の検証結果フラグを更新します。
     *
     * @param summaries    対象の買掛金集計リスト
     * @param supplierCode 仕入先コード
     * @param isMatched    一致しているかどうか（true: 一致, false: 不一致）
     * @param difference   差額
     */
    private void updateVerificationFlags(List<TAccountsPayableSummary> summaries,
                                         String supplierCode,
                                         boolean isMatched,
                                         BigDecimal difference) {
        // 対象の仕入先のサマリーデータを抽出
        List<TAccountsPayableSummary> targetSummaries = summaries.stream()
                .filter(s -> s.getSupplierCode().equals(supplierCode))
                .collect(Collectors.toList());

        // ロギング用に元の状態を確認
        boolean anyChanged = false;

        for (TAccountsPayableSummary summary : targetSummaries) {
            // 手動確定済み行はSMILE再検証で上書きしない
            if (Boolean.TRUE.equals(summary.getVerifiedManually())) {
                log.info("手動確定済みのため検証結果更新をスキップ: 仕入先コード={}, 税率={}%",
                        supplierCode, summary.getTaxRate());
                continue;
            }

            // 元の値を保存
            Integer originalVerificationResult = summary.getVerificationResult();

            // 検証結果フラグを設定（1: 一致, 0: 不一致）
            // 差額が0円または絶対値が5円未満の場合は常に「一致」とする
            boolean finalMatch = isMatched ||
                    (difference != null && difference.abs().compareTo(BigDecimal.ZERO) == 0) ||
                    (difference != null && difference.abs().compareTo(jp.co.oda32.constant.FinanceConstants.PAYMENT_VERIFICATION_TOLERANCE) < 0);

            summary.setVerificationResult(finalMatch ? 1 : 0);
            // 差額も設定
            summary.setPaymentDifference(difference);

            // マネーフォワードエクスポートフラグを設定（一致時のみtrue）
            summary.setMfExportEnabled(finalMatch);

            // 変更を保存
            tAccountsPayableSummaryService.save(summary);

            // 値が変更された場合にログ出力
            if (originalVerificationResult == null || originalVerificationResult != summary.getVerificationResult()) {
                anyChanged = true;
                log.debug("検証結果を{}に設定: 仕入先コード={}, 税率={}%, 差額={}円, マネーフォワードエクスポート={}",
                        finalMatch ? "「一致」" : "「不一致」",
                        supplierCode, summary.getTaxRate(), difference, finalMatch ? "可" : "不可");
            }
        }

        if (anyChanged) {
            log.info("仕入先コード={}の検証結果を{}に設定しました（差額={}円）",
                    supplierCode,
                    isMatched ? "「一致」" : "「不一致」",
                    difference);
        }
    }

    /**
     * 買掛金額をSMILE支払額に合わせて更新します。
     * 最も金額の大きい税率の金額を優先的に調整します。
     *
     * @param summaries           更新対象の買掛金集計リスト
     * @param supplierCode        仕入先コード
     * @param smilePaymentAmount  SMILE支払額
     * @param originalTaxIncluded 元の買掛金額合計
     */
    public void updateAccountsPayableSummaries(List<TAccountsPayableSummary> summaries,
                                               String supplierCode,
                                               BigDecimal smilePaymentAmount,
                                               BigDecimal originalTaxIncluded) {

        // 対象の仕入先の買掛データを抽出（手動確定済みは除外）
        List<TAccountsPayableSummary> targetSummaries = summaries.stream()
                .filter(s -> s.getSupplierCode().equals(supplierCode))
                .filter(s -> !Boolean.TRUE.equals(s.getVerifiedManually()))
                .collect(Collectors.toList());

        long manualCount = summaries.stream()
                .filter(s -> s.getSupplierCode().equals(supplierCode))
                .filter(s -> Boolean.TRUE.equals(s.getVerifiedManually()))
                .count();
        if (manualCount > 0) {
            log.info("手動確定済み {}件 を買掛金額調整から除外: 仕入先コード={}", manualCount, supplierCode);
        }

        if (targetSummaries.isEmpty() || originalTaxIncluded.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        // 差額を計算
        BigDecimal difference = smilePaymentAmount.subtract(originalTaxIncluded);
        log.info("買掛金額調整: 仕入先コード={}, 買掛金額={}, SMILE支払額={}, 差額={}円",
                supplierCode, originalTaxIncluded, smilePaymentAmount, difference);

        // 税込金額の大きい順にソート
        targetSummaries.sort(Comparator.comparing(s ->
                        s.getTaxIncludedAmountChange() != null ? s.getTaxIncludedAmountChange() : BigDecimal.ZERO,
                Comparator.reverseOrder()));

        // 最大金額のサマリーを選択
        TAccountsPayableSummary largestSummary = targetSummaries.get(0);

        // 元の税込金額（null は ZERO 扱い）
        BigDecimal originalAmount = largestSummary.getTaxIncludedAmountChange() != null
                ? largestSummary.getTaxIncludedAmountChange() : BigDecimal.ZERO;

        // 差額を加算して調整（SMILE支払額に合わせる）
        // SMILE支払額から他のレコードの合計を引いて最大レコードの値を算出する
        // null は ZERO 扱いで NPE を防ぐ
        BigDecimal otherSummariesTotal = targetSummaries.stream()
                .filter(s -> !s.equals(largestSummary))
                .map(s -> s.getTaxIncludedAmountChange() != null ? s.getTaxIncludedAmountChange() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 最大レコードの金額は、SMILE支払額から他のレコード合計を引いた値にする
        BigDecimal adjustedAmount = smilePaymentAmount.subtract(otherSummariesTotal).setScale(0, RoundingMode.DOWN);

        // 金額がマイナスになる場合は調整方法を変更
        if (adjustedAmount.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("最大金額への差額調整でマイナスになるため、差額のみ記録し、金額は調整しません");
            // 差額を記録するだけで、金額は調整しない
            for (TAccountsPayableSummary summary : targetSummaries) {
                // 差額を設定するだけ
                summary.setPaymentDifference(difference);
                // 変更を保存
                tAccountsPayableSummaryService.save(summary);
            }
        } else {
            // 最大金額のレコードのみで調整
            log.info("最大金額のレコードのみで調整: 仕入先コード={}, 税率={}%, 元の金額={}円, 調整後金額={}円",
                    supplierCode, largestSummary.getTaxRate(), originalAmount, adjustedAmount);

            // 金額を更新
            largestSummary.setTaxIncludedAmountChange(adjustedAmount);

            // オリジナルのsummariesリスト内の対応するオブジェクトも直接更新
            for (TAccountsPayableSummary originalSummary : summaries) {
                if (originalSummary.getShopNo().equals(largestSummary.getShopNo()) &&
                        originalSummary.getSupplierNo().equals(largestSummary.getSupplierNo()) &&
                        originalSummary.getTransactionMonth().equals(largestSummary.getTransactionMonth()) &&
                        originalSummary.getTaxRate().equals(largestSummary.getTaxRate())) {
                    originalSummary.setTaxIncludedAmountChange(adjustedAmount);
                    break;
                }
            }

            // 変更を保存
            TAccountsPayableSummary saved = tAccountsPayableSummaryService.save(largestSummary);
            log.info("調整後のデータを保存しました: 仕入先コード={}, 税率={}%, 税込金額={}円",
                    saved.getSupplierCode(), saved.getTaxRate(), saved.getTaxIncludedAmountChange());
        }

        // 最終確認：合計金額がSMILE支払額と一致することを確認
        BigDecimal finalTotal = targetSummaries.stream()
                .map(TAccountsPayableSummary::getTaxIncludedAmountChange)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal finalDifference = smilePaymentAmount.subtract(finalTotal);

        // 最終差額が0でない場合は警告ログを出力
        if (finalDifference.compareTo(BigDecimal.ZERO) != 0) {
            log.warn("調整後も差額が残っています: {}円", finalDifference);
        }

        log.info("買掛金額調整結果: 仕入先コード={}, 調整前合計={}円, 調整後合計={}円, SMILE支払額={}円, 残差額={}円",
                supplierCode,
                originalTaxIncluded,
                finalTotal,
                smilePaymentAmount,
                smilePaymentAmount.subtract(finalTotal));
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\mf\\MfPaymentAggregator.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\batch\\finance\\service\\PayableMonthlyAggregator.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\batch\\finance\\AccountsPayableVerificationTasklet.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\batch\\finance\\AccountsPayableSummaryTasklet.java" in C:\project\odamitsu-data-hub
 succeeded in 1727ms:
package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.repository.finance.MfAccountMasterRepository;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MF 買掛金 debit (支払取崩) を supplier × 月 単位に集計して返す。
 * <p>
 * 案 A (2026-04-23): paymentSettled のソースを「前月 verified_amount」から
 * 「MF journal の当月 debit (買掛金取崩)」に切り替えるための service。
 * <p>
 * MF 期首 (2025-06-21) 以降の bucket でのみデータが取れるため、期首前は空 Map を返し
 * 呼び出し側で verified_amount ベースに fallback する。
 *
 * @since 2026-04-23
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class MfPaymentAggregator {

    /** MF 会計期首 (全期間累積の起点、軸 D と揃え)。 */
    private static final LocalDate MF_PERIOD_START = LocalDate.of(2025, 5, 20);
    /**
     * MF journal debit が取得可能になる最初の 20日締め bucket。
     * fiscal year 2025-06-21 以降の取引 → toClosingMonthDay20 で 2025-07-20 bucket。
     */
    private static final LocalDate MF_FIRST_BUCKET = LocalDate.of(2025, 7, 20);
    private static final String MF_ACCOUNT_PAYABLE = "買掛金";

    private final MfOauthService mfOauthService;
    private final MfJournalCacheService journalCache;
    private final MfAccountMasterRepository mfAccountMasterRepository;
    private final MPaymentSupplierService paymentSupplierService;

    /**
     * 指定 shop × transactionMonth の MF debit を supplier 単位で集計。
     * 期首前 bucket or MF client 未設定 or token 取得失敗時は空 Map を返す (呼び出し側で fallback)。
     */
    public Map<Integer, BigDecimal> getMfDebitBySupplierForMonth(
            Integer shopNo, LocalDate transactionMonth) {
        if (transactionMonth == null || transactionMonth.isBefore(MF_FIRST_BUCKET)) {
            return Map.of();
        }

        MMfOauthClient client = mfOauthService.findActiveClient().orElse(null);
        if (client == null) {
            log.warn("[mf-payment] MF client 未設定。paymentSettled は verified_amount で fallback");
            return Map.of();
        }
        String accessToken;
        try {
            accessToken = mfOauthService.getValidAccessToken();
        } catch (Exception e) {
            log.warn("[mf-payment] access_token 取得失敗。paymentSettled は verified_amount で fallback: {}", e.getMessage());
            return Map.of();
        }

        MfJournalCacheService.CachedResult cached;
        try {
            cached = journalCache.getOrFetch(
                    shopNo, client, accessToken, MF_PERIOD_START, transactionMonth, false);
        } catch (Exception e) {
            log.warn("[mf-payment] journals 取得失敗。paymentSettled は verified_amount で fallback: {}", e.getMessage());
            return Map.of();
        }
        List<MfJournal> allJournals = cached.journals();

        // sub_account_name → supplier_code 逆引き
        Map<String, Set<String>> subToCodes = buildMfSubToCodes();

        // supplier_code → supplier_no
        Collection<MPaymentSupplier> suppliers = paymentSupplierService.findByShopNo(shopNo);
        Map<String, Integer> codeToSupplierNo = new HashMap<>();
        for (MPaymentSupplier s : suppliers) {
            if (s.getPaymentSupplierCode() != null) {
                codeToSupplierNo.put(s.getPaymentSupplierCode(), s.getPaymentSupplierNo());
            }
        }

        // transactionMonth bucket の買掛金 debit を sub_account 単位に集計
        Map<String, BigDecimal> debitBySub = new HashMap<>();
        for (MfJournal j : allJournals) {
            if (j.transactionDate() == null || j.branches() == null) continue;
            LocalDate monthKey = MfJournalFetcher.toClosingMonthDay20(j.transactionDate());
            if (!monthKey.equals(transactionMonth)) continue;

            for (MfJournal.MfBranch br : j.branches()) {
                var de = br.debitor();
                if (de != null && MF_ACCOUNT_PAYABLE.equals(de.accountName())
                        && de.subAccountName() != null) {
                    debitBySub.merge(de.subAccountName(), nz(de.value()), BigDecimal::add);
                }
            }
        }

        // sub_account_name → supplier_no に変換
        Map<Integer, BigDecimal> result = new HashMap<>();
        for (Map.Entry<String, BigDecimal> e : debitBySub.entrySet()) {
            Set<String> codes = subToCodes.get(e.getKey());
            if (codes == null) continue;
            for (String code : codes) {
                Integer no = codeToSupplierNo.get(code);
                if (no != null) {
                    result.merge(no, e.getValue(), BigDecimal::add);
                    break; // 複数 supplier_code が同一 sub_account にマッピングされる稀ケースは先頭採用
                }
            }
        }
        log.info("[mf-payment] shopNo={} month={} MF debit supplier 数={}, journals {} 件",
                shopNo, transactionMonth, result.size(), allJournals.size());
        return result;
    }

    private Map<String, Set<String>> buildMfSubToCodes() {
        List<MfAccountMaster> all = mfAccountMasterRepository.findAll();
        Map<String, Set<String>> m = new HashMap<>();
        for (MfAccountMaster r : all) {
            if (!MF_ACCOUNT_PAYABLE.equals(r.getAccountName())) continue;
            if (!MF_ACCOUNT_PAYABLE.equals(r.getFinancialStatementItem())) continue;
            if (r.getSubAccountName() == null || r.getSubAccountName().isBlank()) continue;
            if (r.getSearchKey() == null || r.getSearchKey().isBlank()) continue;
            m.computeIfAbsent(r.getSubAccountName(), k -> new HashSet<>())
                    .add(r.getSearchKey());
        }
        return m;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1892ms:
package jp.co.oda32.batch.finance.service;

import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.service.finance.PayableBalanceCalculator;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase B' 買掛金月次集計の共通ロジック。
 * <p>
 * {@link jp.co.oda32.batch.finance.AccountsPayableAggregationTasklet} と
 * {@link jp.co.oda32.batch.finance.AccountsPayableBackfillTasklet} 両方から呼ばれる。
 * 設計レビュー R7 (ロジック二重化)・R2 (PK 衝突) を解消するため集約。
 * <p>
 * 責務:
 * <ul>
 *   <li>前月 data 構築 (row 単位 closing + supplier 単位 agg)</li>
 *   <li>当月行への opening 繰越</li>
 *   <li>当月行への payment_settled 按分 (supplier 単位 → 税率別)</li>
 *   <li>payment-only 行の生成 (前月 paid>0 かつ 当月 supplier 仕入無し)</li>
 * </ul>
 * <p>
 * payment-only 行の属性 (R4 反映):
 * <ul>
 *   <li>verifiedAmount = null (次月 paid 計算に影響させない)</li>
 *   <li>verifiedManually = false</li>
 *   <li>verificationNote = "[payment-only] {period}"</li>
 *   <li>verificationResult = null</li>
 *   <li>isPaymentOnly = true</li>
 * </ul>
 *
 * @since 2026-04-22 (Phase B')
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class PayableMonthlyAggregator {

    private final TAccountsPayableSummaryService summaryService;

    /** 前月 data 構築: 行単位 closing + supplier 単位集計。 */
    public PrevMonthData buildPrevMonthData(LocalDate periodEndDate) {
        LocalDate prevMonthEnd = periodEndDate.minusMonths(1);
        List<TAccountsPayableSummary> prev = summaryService.findByTransactionMonth(prevMonthEnd);

        Map<String, BigDecimal[]> closingPerRow = new HashMap<>();
        Map<String, List<TAccountsPayableSummary>> bySupplier = new HashMap<>();

        for (TAccountsPayableSummary p : prev) {
            BigDecimal closingIncl = PayableBalanceCalculator.closingTaxIncluded(p);
            BigDecimal closingExcl = PayableBalanceCalculator.closingTaxExcluded(p);
            closingPerRow.put(prevRowKey(p), new BigDecimal[]{closingIncl, closingExcl});
            bySupplier.computeIfAbsent(supplierKey(p), k -> new ArrayList<>()).add(p);
        }

        Map<String, SupplierAgg> supplierAgg = new HashMap<>();
        for (Map.Entry<String, List<TAccountsPayableSummary>> entry : bySupplier.entrySet()) {
            supplierAgg.put(entry.getKey(), computeSupplierAgg(entry.getValue()));
        }
        return new PrevMonthData(closingPerRow, supplierAgg);
    }

    /**
     * supplier 単位の paid / change / closing 集計。
     * paid: verified_amount が「全行同値なら代表値、不一致なら SUM」
     *       (既存 PaymentMfImportService.sumVerifiedAmountForGroup 準拠、ただし null は 0 扱い)
     * is_payment_only=true 行は自分が前月に支払計上済みなので paid 計算からスキップ (次月巻込防止)。
     */
    static SupplierAgg computeSupplierAgg(List<TAccountsPayableSummary> group) {
        // payment-only 行は paid 算出から除外 (R4 防御)
        List<TAccountsPayableSummary> paidRelevant = group.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsPaymentOnly()))
                .toList();

        BigDecimal paidIncl;
        if (paidRelevant.isEmpty()) {
            paidIncl = BigDecimal.ZERO;
        } else {
            List<BigDecimal> perRowVerified = new ArrayList<>(paidRelevant.size());
            for (TAccountsPayableSummary r : paidRelevant) {
                perRowVerified.add(r.getVerifiedAmount() != null ? r.getVerifiedAmount() : BigDecimal.ZERO);
            }
            BigDecimal first = perRowVerified.get(0);
            boolean allSame = perRowVerified.stream().allMatch(v -> v.compareTo(first) == 0);
            paidIncl = allSame ? first
                    : perRowVerified.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal changeInclTotal = BigDecimal.ZERO;
        BigDecimal changeExclTotal = BigDecimal.ZERO;
        BigDecimal closingInclTotal = BigDecimal.ZERO;
        BigDecimal closingExclTotal = BigDecimal.ZERO;
        BigDecimal maxTaxRate = BigDecimal.ZERO;
        String supplierCode = null;
        Integer supplierNo = null;
        Integer shopNo = null;
        for (TAccountsPayableSummary r : group) {
            changeInclTotal = changeInclTotal.add(nz(r.getTaxIncludedAmountChange()));
            changeExclTotal = changeExclTotal.add(nz(r.getTaxExcludedAmountChange()));
            closingInclTotal = closingInclTotal.add(PayableBalanceCalculator.closingTaxIncluded(r));
            closingExclTotal = closingExclTotal.add(PayableBalanceCalculator.closingTaxExcluded(r));
            BigDecimal tr = r.getTaxRate() != null ? r.getTaxRate() : BigDecimal.ZERO;
            if (tr.compareTo(maxTaxRate) > 0) maxTaxRate = tr;
            if (supplierCode == null) supplierCode = r.getSupplierCode();
            if (supplierNo == null) supplierNo = r.getSupplierNo();
            if (shopNo == null) shopNo = r.getShopNo();
        }

        BigDecimal paidExcl;
        if (paidIncl.signum() == 0) {
            paidExcl = BigDecimal.ZERO;
        } else if (changeInclTotal.signum() != 0) {
            // 2 段階切り捨て (supplier 合計 → 行按分) は最終行で吸収
            paidExcl = paidIncl.multiply(changeExclTotal)
                    .divide(changeInclTotal, 0, RoundingMode.DOWN);
        } else {
            paidExcl = paidIncl;
        }

        return new SupplierAgg(shopNo, supplierNo, supplierCode, paidIncl, paidExcl,
                changeInclTotal, changeExclTotal, closingInclTotal, closingExclTotal, maxTaxRate);
    }

    /** 各行に前月 closing から opening を set。 */
    public void applyOpenings(List<TAccountsPayableSummary> rows, PrevMonthData prev) {
        int warnCount = 0;
        for (TAccountsPayableSummary row : rows) {
            BigDecimal[] closing = prev.closingPerRow().get(prevRowKey(row));
            if (closing != null) {
                row.setOpeningBalanceTaxIncluded(closing[0]);
                row.setOpeningBalanceTaxExcluded(closing[1]);
            } else {
                row.setOpeningBalanceTaxIncluded(BigDecimal.ZERO);
                row.setOpeningBalanceTaxExcluded(BigDecimal.ZERO);
                warnCount++;
            }
        }
        if (warnCount > 0) {
            log.info("[opening] 前月 closing なし (期首/新規 supplier): {} 行", warnCount);
        }
    }

    /**
     * MF debit を supplier 単位で当月 payment_settled に上書き (案 A、2026-04-23)。
     * <p>
     * MF 期首 (2025-07-20 bucket) 以降の月で、{@link MfPaymentAggregator} から取れた supplier は
     * MF debit 値を採用。取れなかった supplier は従来の verified_amount ベースの値を維持 (fallback)。
     *
     * @param rows           対象月の summary rows (既に applyPaymentSettled 済み)
     * @param mfDebitMap     supplier_no → MF debit (税込) の map。空 or null なら何もしない
     * @param currentMonth   ログ表示用
     */
    public void overrideWithMfDebit(List<TAccountsPayableSummary> rows,
                                     Map<Integer, BigDecimal> mfDebitMap,
                                     LocalDate currentMonth) {
        if (mfDebitMap == null || mfDebitMap.isEmpty()) return;
        Map<String, List<TAccountsPayableSummary>> bySupplier = new HashMap<>();
        for (TAccountsPayableSummary r : rows) {
            bySupplier.computeIfAbsent(supplierKey(r), k -> new ArrayList<>()).add(r);
        }
        int overridden = 0;
        for (Map.Entry<String, List<TAccountsPayableSummary>> e : bySupplier.entrySet()) {
            List<TAccountsPayableSummary> group = e.getValue();
            Integer supplierNo = group.get(0).getSupplierNo();
            BigDecimal mfDebit = mfDebitMap.get(supplierNo);
            if (mfDebit == null) continue;

            // 当月 change 比で按分 (applyPaymentSettled と同じロジック)
            group.sort(Comparator.comparing(r -> r.getTaxRate() != null ? r.getTaxRate() : BigDecimal.ZERO));
            BigDecimal changeInclTotal = BigDecimal.ZERO;
            BigDecimal changeExclTotal = BigDecimal.ZERO;
            for (TAccountsPayableSummary r : group) {
                changeInclTotal = changeInclTotal.add(nz(r.getTaxIncludedAmountChange()));
                changeExclTotal = changeExclTotal.add(nz(r.getTaxExcludedAmountChange()));
            }
            // change=0 supplier は payment-only 行で処理 (既存ロジックに委ねる)
            // ただし当月 payment 発生のみで change=0 のケースもあるため、代表行に全額入れる fallback
            if (changeInclTotal.signum() == 0) {
                TAccountsPayableSummary representative = group.get(group.size() - 1);
                representative.setPaymentAmountSettledTaxIncluded(mfDebit);
                // 税抜は税率逆算 (ratio 情報無いため)
                BigDecimal divisor = BigDecimal.valueOf(100).add(nz(representative.getTaxRate()));
                representative.setPaymentAmountSettledTaxExcluded(
                        mfDebit.multiply(BigDecimal.valueOf(100)).divide(divisor, 0, RoundingMode.DOWN));
                overridden++;
                continue;
            }

            // 税抜の合計は MF debit × 比率の累積。最終行で端数吸収
            BigDecimal cumIncl = BigDecimal.ZERO;
            BigDecimal cumExcl = BigDecimal.ZERO;
            BigDecimal mfDebitExcl = mfDebit.multiply(changeExclTotal)
                    .divide(changeInclTotal, 0, RoundingMode.DOWN);
            for (int i = 0; i < group.size(); i++) {
                TAccountsPayableSummary r = group.get(i);
                boolean isLast = (i == group.size() - 1);
                BigDecimal paidInclRow, paidExclRow;
                if (isLast) {
                    paidInclRow = mfDebit.subtract(cumIncl);
                    paidExclRow = mfDebitExcl.subtract(cumExcl);
                } else {
                    BigDecimal changeIncl = nz(r.getTaxIncludedAmountChange());
                    BigDecimal changeExcl = nz(r.getTaxExcludedAmountChange());
                    paidInclRow = mfDebit.multiply(changeIncl).divide(changeInclTotal, 0, RoundingMode.DOWN);
                    paidExclRow = changeExclTotal.signum() == 0 ? BigDecimal.ZERO
                            : mfDebitExcl.multiply(changeExcl).divide(changeExclTotal, 0, RoundingMode.DOWN);
                    cumIncl = cumIncl.add(paidInclRow);
                    cumExcl = cumExcl.add(paidExclRow);
                }
                r.setPaymentAmountSettledTaxIncluded(paidInclRow);
                r.setPaymentAmountSettledTaxExcluded(paidExclRow);
            }
            overridden++;
        }
        log.info("[payment_settled MF] month={} MF debit 上書き supplier 数={}", currentMonth, overridden);
    }

    /**
     * supplier 内の当月 change 比で payment_settled を按分。
     * 決定論化のため group を tax_rate 昇順でソートして最終行 (最高税率) で端数吸収 (R6 対応)。
     * change 合計=0 の supplier は payment-only 行側で処理するため skip。
     */
    public void applyPaymentSettled(List<TAccountsPayableSummary> rows, PrevMonthData prev) {
        Map<String, List<TAccountsPayableSummary>> bySupplier = new HashMap<>();
        for (TAccountsPayableSummary r : rows) {
            bySupplier.computeIfAbsent(supplierKey(r), k -> new ArrayList<>()).add(r);
        }
        int skippedNoPaid = 0;
        int skippedZeroChange = 0;
        int applied = 0;
        for (Map.Entry<String, List<TAccountsPayableSummary>> entry : bySupplier.entrySet()) {
            SupplierAgg agg = prev.supplierAgg().get(entry.getKey());
            List<TAccountsPayableSummary> group = new ArrayList<>(entry.getValue());
            group.sort(Comparator.comparing(
                    r -> r.getTaxRate() != null ? r.getTaxRate() : BigDecimal.ZERO));

            // まず 0 クリア (再集計時の安全策)
            for (TAccountsPayableSummary r : group) {
                r.setPaymentAmountSettledTaxIncluded(BigDecimal.ZERO);
                r.setPaymentAmountSettledTaxExcluded(BigDecimal.ZERO);
            }
            if (agg == null || agg.paidIncl().signum() == 0) {
                if (agg != null) skippedNoPaid++;
                continue;
            }

            BigDecimal changeInclTotal = BigDecimal.ZERO;
            BigDecimal changeExclTotal = BigDecimal.ZERO;
            for (TAccountsPayableSummary r : group) {
                changeInclTotal = changeInclTotal.add(nz(r.getTaxIncludedAmountChange()));
                changeExclTotal = changeExclTotal.add(nz(r.getTaxExcludedAmountChange()));
            }
            if (changeInclTotal.signum() == 0) {
                skippedZeroChange++;
                continue; // payment-only 行側で処理
            }

            BigDecimal cumIncl = BigDecimal.ZERO;
            BigDecimal cumExcl = BigDecimal.ZERO;
            for (int i = 0; i < group.size(); i++) {
                TAccountsPayableSummary r = group.get(i);
                boolean isLast = (i == group.size() - 1);
                BigDecimal paidInclRow;
                BigDecimal paidExclRow;
                if (isLast) {
                    paidInclRow = agg.paidIncl().subtract(cumIncl);
                    paidExclRow = agg.paidExcl().subtract(cumExcl);
                } else {
                    BigDecimal changeIncl = nz(r.getTaxIncludedAmountChange());
                    BigDecimal changeExcl = nz(r.getTaxExcludedAmountChange());
                    paidInclRow = agg.paidIncl().multiply(changeIncl)
                            .divide(changeInclTotal, 0, RoundingMode.DOWN);
                    paidExclRow = changeExclTotal.signum() == 0 ? BigDecimal.ZERO
                            : agg.paidExcl().multiply(changeExcl)
                                    .divide(changeExclTotal, 0, RoundingMode.DOWN);
                    cumIncl = cumIncl.add(paidInclRow);
                    cumExcl = cumExcl.add(paidExclRow);
                }
                r.setPaymentAmountSettledTaxIncluded(paidInclRow);
                r.setPaymentAmountSettledTaxExcluded(paidExclRow);
            }
            applied++;
        }
        if (skippedNoPaid > 0 || skippedZeroChange > 0 || applied > 0) {
            log.info("[payment_settled] 適用 supplier={}, 支払なしで skip={}, change=0 supplier (payment-only 対象)={}",
                    applied, skippedNoPaid, skippedZeroChange);
        }
    }

    /**
     * 前月 paid>0 かつ 当月 supplier 仕入無し (OR change 合計=0) の supplier に payment-only 行を生成。
     * 既存 payment-only 行があれば上書き、無ければ新規作成。
     * 判定基準: activeSupplierKeys (当月行の supplier 集合) + supplierChangeIncl (supplier の当月 change 合計)。
     * 設計レビュー R2 修正: activeSupplierKeys のみでなく change 合計も確認。
     *
     * @param currRows  当月の全行 (toSave + preservedManual などマージ済み)
     * @param currMap   key=(shop, supplier, tx, tax_rate) の Map (upsert 先 lookup 用)
     */
    public List<TAccountsPayableSummary> generatePaymentOnlyRows(
            PrevMonthData prev,
            List<TAccountsPayableSummary> currRows,
            LocalDate periodEndDate,
            Map<String, TAccountsPayableSummary> currMap) {
        // 当月 supplier 単位の change 合計
        Map<String, BigDecimal> supplierChangeIncl = new HashMap<>();
        for (TAccountsPayableSummary r : currRows) {
            supplierChangeIncl.merge(supplierKey(r), nz(r.getTaxIncludedAmountChange()), BigDecimal::add);
        }

        List<TAccountsPayableSummary> out = new ArrayList<>();
        int paidButNoChange = 0;
        for (Map.Entry<String, SupplierAgg> e : prev.supplierAgg().entrySet()) {
            SupplierAgg agg = e.getValue();
            if (agg.paidIncl().signum() == 0) continue;
            BigDecimal changeTotal = supplierChangeIncl.getOrDefault(e.getKey(), BigDecimal.ZERO);
            if (changeTotal.signum() != 0) continue; // 通常按分で処理済

            String k = buildRowKey(agg.shopNo(), agg.supplierNo(), periodEndDate, agg.maxTaxRate());
            TAccountsPayableSummary row = currMap.get(k);
            if (row == null) {
                row = TAccountsPayableSummary.builder()
                        .shopNo(agg.shopNo())
                        .supplierNo(agg.supplierNo())
                        .supplierCode(agg.supplierCode())
                        .transactionMonth(periodEndDate)
                        .taxRate(agg.maxTaxRate())
                        .taxIncludedAmountChange(BigDecimal.ZERO)
                        .taxExcludedAmountChange(BigDecimal.ZERO)
                        .build();
            } else {
                row.setTaxIncludedAmountChange(BigDecimal.ZERO);
                row.setTaxExcludedAmountChange(BigDecimal.ZERO);
            }
            row.setOpeningBalanceTaxIncluded(agg.closingInclTotal());
            row.setOpeningBalanceTaxExcluded(agg.closingExclTotal());
            row.setPaymentAmountSettledTaxIncluded(agg.paidIncl());
            row.setPaymentAmountSettledTaxExcluded(agg.paidExcl());
            // payment-only マーカー群 (R4)
            row.setIsPaymentOnly(true);
            row.setVerifiedAmount(null);
            row.setVerifiedManually(false);
            row.setVerificationNote("[payment-only] " + periodEndDate);
            row.setVerificationResult(null);
            row.setPaymentDifference(null);
            if (row.getMfExportEnabled() == null) row.setMfExportEnabled(true);
            out.add(row);
            paidButNoChange++;
        }
        if (paidButNoChange > 0) {
            log.info("[payment-only] 生成: {} 件 (前月 paid>0 / 当月 change=0 supplier)", paidButNoChange);
        }
        return out;
    }

    // ================================================================
    // キー util
    // ================================================================

    public static String rowKey(TAccountsPayableSummary r) {
        return buildRowKey(r.getShopNo(), r.getSupplierNo(), r.getTransactionMonth(), r.getTaxRate());
    }

    public static String buildRowKey(Integer shopNo, Integer supplierNo, LocalDate tm, BigDecimal taxRate) {
        return shopNo + "|" + supplierNo + "|" + tm + "|" + (taxRate != null ? taxRate.toPlainString() : "null");
    }

    public static String supplierKey(TAccountsPayableSummary r) {
        return r.getShopNo() + "|" + r.getSupplierNo();
    }

    public static String prevRowKey(TAccountsPayableSummary r) {
        return r.getShopNo() + "|" + r.getSupplierNo() + "|"
                + (r.getTaxRate() != null ? r.getTaxRate().toPlainString() : "null");
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    // ================================================================
    // 公開 records
    // ================================================================

    public record PrevMonthData(
            Map<String, BigDecimal[]> closingPerRow,
            Map<String, SupplierAgg> supplierAgg
    ) {}

    public record SupplierAgg(
            Integer shopNo,
            Integer supplierNo,
            String supplierCode,
            BigDecimal paidIncl,
            BigDecimal paidExcl,
            BigDecimal changeInclTotal,
            BigDecimal changeExclTotal,
            BigDecimal closingInclTotal,
            BigDecimal closingExclTotal,
            BigDecimal maxTaxRate
    ) {}
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2856ms:
package jp.co.oda32.batch.finance;

import jp.co.oda32.batch.finance.model.VerificationResult;
import jp.co.oda32.batch.finance.service.SmilePaymentVerifier;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.smile.TSmilePayment;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import jp.co.oda32.domain.service.smile.TSmilePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 買掛金額とSMILE支払情報との照合を行うTasklet
 * （チェック処理のみ、集計処理は別タスクレットで実行済み）
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class AccountsPayableVerificationTasklet implements Tasklet {

    private final TAccountsPayableSummaryService tAccountsPayableSummaryService;
    private final TSmilePaymentService tSmilePaymentService;
    private final SmilePaymentVerifier smilePaymentVerifier;

    // バッチジョブの引数として渡された targetDate を取得
    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        try {
            // targetDate を LocalDate に変換
            LocalDate startDate = LocalDate.parse(targetDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

            // 期間を設定（前月21日から当月20日まで）
            LocalDate periodEndDate = YearMonth.from(startDate).atDay(20); // 当月20日

            log.info("買掛金額とSMILE支払情報の照合を開始します。対象月: {}", periodEndDate);

            // 既存の買掛金集計データを取得
            List<TAccountsPayableSummary> summaries = tAccountsPayableSummaryService.findByTransactionMonth(periodEndDate);

            if (!summaries.isEmpty()) {
                // SMILE支払情報の取得（翌月の支払情報を取得）
                List<TSmilePayment> smilePayments = tSmilePaymentService.findByYearMonth(
                        YearMonth.from(periodEndDate).plusMonths(1).format(DateTimeFormatter.ofPattern("yyyyMM")));

                log.info("照合対象: 買掛金集計データ {}件, SMILE支払情報 {}件", summaries.size(), smilePayments.size());

                // SMILE支払情報との照合結果を取得（仕入先コードごとの総額比較）
                // 注意: verifyWithSmilePaymentメソッド内で、差額が5円以内の場合は
                // summariesオブジェクトのtaxIncludedAmountChangeがSMILE支払額に合わせて更新されます
                Map<String, VerificationResult> verificationResults = smilePaymentVerifier.verifyWithSmilePayment(summaries, smilePayments);

                // 一旦全ての調整されたsummariesを保存（トランザクション内で確実に更新するため）
                log.info("買掛金額の調整結果をデータベースに一括保存します。");
                this.tAccountsPayableSummaryService.saveAll(summaries);

                // 検証結果を既存レコードに反映
                int updatedCount = 0;
                for (TAccountsPayableSummary summary : summaries) {
                    // 検証結果の取得
                    VerificationResult result = verificationResults.get(summary.getSupplierCode());

                    // 既存のレコードを検索（確実に最新のデータを取得）
                    TAccountsPayableSummary exist = this.tAccountsPayableSummaryService.getByPK(
                            summary.getShopNo(),
                            summary.getSupplierNo(),
                            summary.getTransactionMonth(),
                            summary.getTaxRate()
                    );

                    if (exist != null) {
                        // 検証結果がある場合は検証結果フラグと差額を設定
                        if (result != null) {
                            // 調整済みの場合や差額が5円未満の場合は常に「一致」とする
                            if (result.isAdjustedToSmilePayment() ||
                                    (result.getDifference() != null &&
                                            result.getDifference().abs().compareTo(new java.math.BigDecimal(5)) < 0)) {
                                exist.setVerificationResult(1); // 一致
                                log.debug("差額が5円未満または調整済みのため「一致」に設定: 仕入先コード={}, 差額={}円",
                                        summary.getSupplierCode(), result.getDifference());
                            } else {
                                exist.setVerificationResult(result.isMatched() ? 1 : 0);
                            }
                            exist.setPaymentDifference(result.getDifference()); // 差額を設定
                        } else {
                            // 検証結果がない場合（SMILE支払情報がない場合）
                            exist.setVerificationResult(0); // 不一致
                            exist.setPaymentDifference(null);
                            log.warn("SMILE支払情報が存在しないため「不一致」に設定: 仕入先コード={}", summary.getSupplierCode());
                        }

                        // 重要：調整後のtaxIncludedAmountChangeを確実に反映
                        exist.setTaxIncludedAmountChange(summary.getTaxIncludedAmountChange());
                        exist.setTaxExcludedAmountChange(summary.getTaxExcludedAmountChange());

                        this.tAccountsPayableSummaryService.save(exist);
                        updatedCount++;
                    }
                }
                log.info("買掛金額の検証結果をデータベースに反映しました。更新件数: {}", updatedCount);
            } else {
                log.info("照合対象の買掛金集計データが存在しません。");
            }
        } catch (Exception e) {
            log.error("買掛金額の照合中にエラーが発生しました。", e);
            throw e; // バッチを失敗としてマーク
        }

        return RepeatStatus.FINISHED;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3844ms:
package jp.co.oda32.batch.finance;

import jp.co.oda32.batch.finance.model.VerificationResult;
import jp.co.oda32.batch.finance.service.AccountsPayableSummaryCalculator;
import jp.co.oda32.batch.finance.service.SmilePaymentVerifier;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.smile.TSmilePayment;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import jp.co.oda32.domain.service.smile.TSmilePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 20日締め買掛金額を集計し、TAccountsPayableSummaryテーブルに登録するTasklet
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class AccountsPayableSummaryTasklet implements Tasklet {

    private final TAccountsPayableSummaryService tAccountsPayableSummaryService;
    private final TSmilePaymentService tSmilePaymentService;
    private final AccountsPayableSummaryCalculator summaryCalculator;
    private final SmilePaymentVerifier smilePaymentVerifier;

    // バッチジョブの引数として渡された targetDate を取得
    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        try {
            // targetDate を LocalDate に変換
            LocalDate startDate = LocalDate.parse(targetDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

            // 期間を設定（前月21日から当月20日まで）
            LocalDate periodStartDate = YearMonth.from(startDate).minusMonths(1).atDay(21); // 前月21日
            LocalDate periodEndDate = YearMonth.from(startDate).atDay(20); // 当月20日

            // データ集計処理（買掛金額の合計を計算）
            List<TAccountsPayableSummary> summaries = summaryCalculator.calculatePayableSummaries(periodStartDate, periodEndDate);

            // 集計結果をTAccountsPayableSummaryテーブルに一括登録
            if (!summaries.isEmpty()) {
                // SMILE支払情報の取得
                List<TSmilePayment> smilePayments = tSmilePaymentService.findByYearMonth(
                        YearMonth.from(periodEndDate).plusMonths(1).format(DateTimeFormatter.ofPattern("yyyyMM")));

                // SMILE支払情報との照合結果を取得（仕入先コードごとの総額比較）
                // 注意: verifyWithSmilePaymentメソッド内で、差額が5円以内の場合は
                // summariesオブジェクトのtaxIncludedAmountChangeがSMILE支払額に合わせて更新されます
                Map<String, VerificationResult> verificationResults = smilePaymentVerifier.verifyWithSmilePayment(summaries, smilePayments);

                // 一旦全ての調整されたsummariesを保存（トランザクション内で確実に更新するため）
                log.info("買掛金額の調整結果をデータベースに一括保存します。");
                this.tAccountsPayableSummaryService.saveAll(summaries);

                for (TAccountsPayableSummary summary : summaries) {
                    // 検証結果の取得
                    VerificationResult result = verificationResults.get(summary.getSupplierCode());

                    // 既存のレコードを検索
                    TAccountsPayableSummary exist = this.tAccountsPayableSummaryService.getByPK(
                            summary.getShopNo(),
                            summary.getSupplierNo(),
                            summary.getTransactionMonth(),
                            summary.getTaxRate()
                    );

                    if (exist != null) {
                        // 既存レコードの更新
                        // 注意: summaryオブジェクトのtaxIncludedAmountChangeは
                        // 差額が5円以内の場合はSMILE支払額に合わせて更新されている可能性があります
                        log.debug("既存レコード更新: 仕入先コード={}, 税率={}%, 元の金額={}円, 更新後金額={}円",
                                summary.getSupplierCode(), summary.getTaxRate(),
                                exist.getTaxIncludedAmountChange(), summary.getTaxIncludedAmountChange());

                        // 重要：調整後のtaxIncludedAmountChangeを確実に反映
                        exist.setTaxIncludedAmountChange(summary.getTaxIncludedAmountChange());
                        exist.setTaxExcludedAmountChange(summary.getTaxExcludedAmountChange());

                        // 検証結果がある場合は検証結果フラグと差額を設定
                        if (result != null) {
                            // 調整済みの場合や差額が5円未満の場合は常に「一致」とする
                            if (result.isAdjustedToSmilePayment() ||
                                    (result.getDifference() != null &&
                                            result.getDifference().abs().compareTo(new java.math.BigDecimal(5)) < 0)) {
                                exist.setVerificationResult(1); // 一致
                                log.debug("差額が5円未満または調整済みのため「一致」に設定: 仕入先コード={}, 差額={}円",
                                        summary.getSupplierCode(), result.getDifference());
                            } else {
                                exist.setVerificationResult(result.isMatched() ? 1 : 0);
                            }
                            exist.setPaymentDifference(result.getDifference()); // 差額を設定
                        }

                        this.tAccountsPayableSummaryService.save(exist);
                    } else {
                        // 新規レコードの作成
                        if (result != null) {
                            // 調整済みの場合や差額が5円未満の場合は常に「一致」とする
                            if (result.isAdjustedToSmilePayment() ||
                                    (result.getDifference() != null &&
                                            result.getDifference().abs().compareTo(new java.math.BigDecimal(5)) < 0)) {
                                summary.setVerificationResult(1); // 一致
                                log.debug("差額が5円未満または調整済みのため「一致」に設定: 仕入先コード={}, 差額={}円",
                                        summary.getSupplierCode(), result.getDifference());
                            } else {
                                summary.setVerificationResult(result.isMatched() ? 1 : 0);
                            }
                            summary.setPaymentDifference(result.getDifference()); // 差額を設定
                        }

                        this.tAccountsPayableSummaryService.save(summary);
                    }
                }
                log.info("買掛金額の集計結果をデータベースに保存しました。件数: {}", summaries.size());
            } else {
                log.info("新しい買掛金額の集計結果はありません。");
            }
        } catch (Exception e) {
            log.error("買掛金額の集計中にエラーが発生しました。", e);
            throw e; // バッチを失敗としてマーク
        }

        return RepeatStatus.FINISHED;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content frontend\\components\\pages\\finance\\accounts-payable.tsx" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content frontend\\components\\pages\\finance\\accounts-payable-ledger.tsx" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content frontend\\components\\pages\\finance\\integrity-report.tsx" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content frontend\\components\\pages\\finance\\supplier-balances.tsx" in C:\project\odamitsu-data-hub
 succeeded in 1846ms:
'use client'

import { useCallback, useMemo, useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { useRouter, useSearchParams } from 'next/navigation'
import { api, ApiError } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { usePaymentSuppliers, useShops } from '@/hooks/use-master-data'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { formatCurrency } from '@/lib/utils'
import { AlertCircle, CheckCircle2, Loader2, Play, RefreshCw, Search } from 'lucide-react'
import { toast } from 'sonner'
import {
  ANOMALY_BADGE_CLASS,
  ANOMALY_SHORT_LABEL,
  type AccountsPayableLedgerResponse,
  type MfSupplierLedgerResponse,
  defaultFromMonth,
  defaultToMonth,
  highestSeverity,
  rowBgClass,
} from '@/types/accounts-payable-ledger'
import { computeMfMatchStatus } from '@/types/integrity-report'
import Link from 'next/link'

/**
 * 買掛帳: 1 仕入先の月次推移画面。
 * 設計書: claudedocs/design-accounts-payable-ledger.md §8
 */
export function AccountsPayableLedgerPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const router = useRouter()
  const urlParams = useSearchParams()

  const initialToMonth = defaultToMonth()
  const initialFromMonth = defaultFromMonth(initialToMonth)

  // 入力値 (pending) と確定値 (committed) を分離 (M1 反映)
  const [shopNo, setShopNo] = useState<number | undefined>(
    urlParams.get('shopNo') ? Number(urlParams.get('shopNo'))
      : isAdmin ? undefined : user?.shopNo,
  )
  const [supplierNo, setSupplierNo] = useState<number | undefined>(
    urlParams.get('supplierNo') ? Number(urlParams.get('supplierNo')) : undefined,
  )
  const [fromMonth, setFromMonth] = useState<string>(
    urlParams.get('fromMonth') || initialFromMonth,
  )
  const [toMonth, setToMonth] = useState<string>(
    urlParams.get('toMonth') || initialToMonth,
  )

  // 確定値 (検索ボタン押下 or URL 初期値)
  const [committed, setCommitted] = useState<{
    shopNo?: number; supplierNo?: number; fromMonth: string; toMonth: string
  }>(() => ({
    shopNo: urlParams.get('shopNo') ? Number(urlParams.get('shopNo')) : (isAdmin ? undefined : user?.shopNo),
    supplierNo: urlParams.get('supplierNo') ? Number(urlParams.get('supplierNo')) : undefined,
    fromMonth: urlParams.get('fromMonth') || initialFromMonth,
    toMonth: urlParams.get('toMonth') || initialToMonth,
  }))

  // URL 同期
  const updateUrl = useCallback((params: {
    shopNo?: number; supplierNo?: number; fromMonth?: string; toMonth?: string
  }) => {
    const sp = new URLSearchParams()
    if (params.shopNo !== undefined) sp.set('shopNo', String(params.shopNo))
    if (params.supplierNo !== undefined) sp.set('supplierNo', String(params.supplierNo))
    if (params.fromMonth) sp.set('fromMonth', params.fromMonth)
    if (params.toMonth) sp.set('toMonth', params.toMonth)
    const qs = sp.toString()
    router.replace(qs ? `?${qs}` : window.location.pathname, { scroll: false })
  }, [router])

  const shopsQuery = useShops(isAdmin)
  const paymentSuppliersQuery = usePaymentSuppliers(shopNo)

  const shopOptions = (shopsQuery.data ?? []).map((s) => ({
    value: String(s.shopNo),
    label: `${s.shopNo}: ${s.shopName}`,
  }))
  const supplierOptions = (paymentSuppliersQuery.data ?? []).map((s) => ({
    value: String(s.paymentSupplierNo),
    label: `${s.paymentSupplierCode ?? ''} ${s.paymentSupplierName}`.trim(),
  }))

  const enabled = committed.supplierNo !== undefined && committed.shopNo !== undefined
  const ledgerQuery = useQuery({
    queryKey: ['accounts-payable-ledger', committed.shopNo, committed.supplierNo, committed.fromMonth, committed.toMonth],
    queryFn: () => {
      const sp = new URLSearchParams()
      sp.set('shopNo', String(committed.shopNo))
      sp.set('supplierNo', String(committed.supplierNo))
      sp.set('fromMonth', committed.fromMonth)
      sp.set('toMonth', committed.toMonth)
      return api.get<AccountsPayableLedgerResponse>(`/finance/accounts-payable/ledger?${sp.toString()}`)
    },
    enabled,
  })

  const [mfLedger, setMfLedger] = useState<MfSupplierLedgerResponse | null>(null)
  const mfMutation = useMutation({
    mutationFn: async (refresh: boolean = false) => {
      const sp = new URLSearchParams()
      sp.set('shopNo', String(committed.shopNo))
      sp.set('supplierNo', String(committed.supplierNo))
      sp.set('fromMonth', committed.fromMonth)
      sp.set('toMonth', committed.toMonth)
      if (refresh) sp.set('refresh', 'true')
      return api.get<MfSupplierLedgerResponse>(`/finance/accounts-payable/ledger/mf?${sp.toString()}`)
    },
    onSuccess: (res, refresh) => {
      setMfLedger(res)
      const prefix = refresh ? 'MF API 再取得: ' : ''
      if (res.matchedSubAccountNames.length === 0) {
        toast.warning(`${prefix}MF 側で対応する sub_account が見つかりませんでした`)
      } else {
        toast.success(`${prefix}MF 取得完了 (journals ${res.totalJournalCount} 件)`)
      }
    },
    onError: (e: Error) => {
      if (e instanceof ApiError && e.status === 401) {
        toast.error('MF 認証エラー。「MF連携状況」画面で再認証してください。')
      } else if (e instanceof ApiError && e.status === 403) {
        toast.error('MF scope 不足です。「MF連携状況」画面で scope 更新 + 再認証してください。')
      } else {
        toast.error(e.message)
      }
    },
  })

  const handleSearch = () => {
    if (!shopNo || !supplierNo) {
      toast.warning('仕入先を選択してください')
      return
    }
    if (new Date(fromMonth) > new Date(toMonth)) {
      toast.error('開始月は終了月以前である必要があります')
      return
    }
    setCommitted({ shopNo, supplierNo, fromMonth, toMonth })
    updateUrl({ shopNo, supplierNo, fromMonth, toMonth })
    setMfLedger(null) // 検索条件変化時は MF 結果クリア
  }

  // MF delta を月キーで lookup
  const mfDeltaByMonth = useMemo(() => {
    const m = new Map<string, number>()
    for (const r of mfLedger?.rows ?? []) m.set(r.transactionMonth, r.mfPeriodDelta)
    return m
  }, [mfLedger])

  // MF 累積残を月キーで lookup
  const mfCumulativeByMonth = useMemo(() => {
    const m = new Map<string, number>()
    for (const r of mfLedger?.rows ?? []) m.set(r.transactionMonth, r.mfCumulativeBalance ?? 0)
    return m
  }, [mfLedger])

  const data = ledgerQuery.data

  return (
    <div className="space-y-4">
      <PageHeader title="買掛帳" />

      {/* 検索フォーム */}
      <Card>
        <CardContent className="pt-4 space-y-3">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-5">
            {isAdmin && (
              <div>
                <Label>ショップ</Label>
                <SearchableSelect
                  value={shopNo !== undefined ? String(shopNo) : ''}
                  onValueChange={(v) => {
                    setShopNo(v ? Number(v) : undefined)
                    setSupplierNo(undefined)
                  }}
                  options={shopOptions}
                  placeholder="選択してください"
                  clearable
                />
              </div>
            )}
            <div className={isAdmin ? '' : 'md:col-span-2'}>
              <Label>仕入先 *</Label>
              <SearchableSelect
                value={supplierNo !== undefined ? String(supplierNo) : ''}
                onValueChange={(v) => setSupplierNo(v ? Number(v) : undefined)}
                options={supplierOptions}
                placeholder="選択してください"
                clearable
              />
            </div>
            <div>
              <Label htmlFor="from-month">開始月</Label>
              <Input
                id="from-month"
                type="month"
                value={fromMonth.slice(0, 7)}
                onChange={(e) => setFromMonth(`${e.target.value}-20`)}
              />
            </div>
            <div>
              <Label htmlFor="to-month">終了月</Label>
              <Input
                id="to-month"
                type="month"
                value={toMonth.slice(0, 7)}
                onChange={(e) => setToMonth(`${e.target.value}-20`)}
              />
            </div>
            <div className="flex items-end">
              <Button onClick={handleSearch} disabled={!supplierNo}>
                <Search className="mr-1 h-4 w-4" />
                検索
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {ledgerQuery.isLoading && enabled && <LoadingSpinner />}
      {ledgerQuery.isError && <ErrorMessage onRetry={() => ledgerQuery.refetch()} />}

      {data && (
        <>
          {/* 仕入先情報 + サマリ */}
          <Card>
            <CardContent className="pt-4 space-y-2">
              <div className="flex items-center gap-3">
                <span className="text-muted-foreground text-xs">仕入先</span>
                <span className="font-semibold">
                  {data.supplier.supplierCode} {data.supplier.supplierName}
                </span>
              </div>
              <div className="grid grid-cols-2 gap-2 text-sm md:grid-cols-6 tabular-nums">
                <InfoTile
                  label={`期間開始 opening (${data.fromMonth})`}
                  value={data.rows[0]?.openingBalanceTaxIncluded ?? 0}
                  hint="期首残 (DB backfill で算出)"
                />
                {mfLedger?.openingBalance !== undefined && mfLedger.openingBalance !== 0 && (
                  <InfoTile
                    label="前期繰越 (MF journal #1)"
                    value={mfLedger.openingBalance}
                    hint="MF 期首残高仕訳 + 手動補正 (m_supplier_opening_balance)"
                  />
                )}
                <InfoTile label="期間累計 仕入" value={data.summary.totalChangeTaxIncluded} />
                <InfoTile label="期間累計 検証" value={data.summary.totalVerified} />
                <InfoTile label="期間累計 支払反映" value={data.summary.totalPaymentSettled} />
                <InfoTile label="最終残 (closing)" value={data.summary.finalClosing} emphasize />
                <div className="flex flex-col gap-0.5">
                  <span className="text-xs text-muted-foreground">警告</span>
                  <span className="text-xs">
                    未検証 {data.summary.unverifiedMonthCount}件 / 値引繰越 {data.summary.negativeClosingMonthCount}件
                    {data.summary.continuityBreakCount > 0 && (
                      <span className="text-red-600"> / 不整合 {data.summary.continuityBreakCount}件</span>
                    )}
                    {data.summary.monthGapCount > 0 && (
                      <span className="text-orange-600"> / 月抜け {data.summary.monthGapCount}件</span>
                    )}
                  </span>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* MF 比較トリガ + 整合性レポートリンク */}
          <Card>
            <CardContent className="pt-4 flex flex-wrap items-center gap-3">
              <Button
                variant="outline"
                onClick={() => mfMutation.mutate(false)}
                disabled={mfMutation.isPending}
              >
                {mfMutation.isPending ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : <Play className="mr-1 h-4 w-4" />}
                MF と比較を取得
              </Button>
              {mfLedger && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    if (window.confirm('MF API から最新データを再取得します。続行しますか?')) {
                      mfMutation.mutate(true)
                    }
                  }}
                  disabled={mfMutation.isPending}
                  title="MF API から再取得 (キャッシュ無視)"
                >
                  <RefreshCw className="mr-1 h-3 w-3" /> 最新取得
                </Button>
              )}
              {mfLedger && (
                <div className="text-xs text-muted-foreground space-y-0.5">
                  <div>
                    matched: {mfLedger.matchedSubAccountNames.join(', ') || '(なし)'} / journals {mfLedger.totalJournalCount} 件
                    {mfLedger.fetchedAt && (
                      <span className="ml-2">
                        取得: {new Date(mfLedger.fetchedAt).toLocaleString('ja-JP', { dateStyle: 'short', timeStyle: 'short' })}
                      </span>
                    )}
                  </div>
                  {mfLedger.mfStartDate && mfLedger.mfEndDate && (
                    <div>
                      <span className="font-semibold">MF 取得期間:</span> {mfLedger.mfStartDate} ? {mfLedger.mfEndDate}
                      <span className="ml-2 text-amber-700">※ 実際は MF 会計期首 (fiscal year 境界) 以降のみ取得。期首前の累積残は反映されず、累積残の序盤は実態とズレます。</span>
                    </div>
                  )}
                </div>
              )}
              {mfLedger && mfLedger.unmatchedCandidates.length > 0 && (
                <span className="flex items-center gap-1 text-xs text-amber-700">
                  <AlertCircle className="h-3 w-3" /> MF 側で {mfLedger.unmatchedCandidates.join(', ')} が見つかりません
                </span>
              )}
              <div className="ml-auto">
                <Link
                  href={`/finance/accounts-payable-ledger/integrity?shopNo=${committed.shopNo ?? ''}&fromMonth=${committed.fromMonth}&toMonth=${committed.toMonth}`}
                  className="text-xs text-blue-600 hover:underline"
                >
                  整合性レポート (全仕入先) →
                </Link>
              </div>
            </CardContent>
          </Card>

          {/* 月次明細テーブル */}
          <Card>
            <CardContent className="pt-4 overflow-x-auto">
              <table className="w-full text-sm tabular-nums">
                <thead>
                  <tr className="border-b text-left">
                    <th className="py-2">月</th>
                    <th className="py-2 text-right">前月繰越</th>
                    <th className="py-2 text-right">仕入</th>
                    <th className="py-2 text-right">検証額</th>
                    <th className="py-2 text-right">支払反映</th>
                    <th className="py-2 text-right">当月残</th>
                    {mfLedger && (
                      <>
                        <th className="py-2 text-right">MF delta</th>
                        <th className="py-2 text-right">Δ(自社-MF)</th>
                        <th className="py-2 text-right">MF 累積残</th>
                        <th className="py-2 text-right">累積差</th>
                      </>
                    )}
                    <th className="py-2">ステータス</th>
                  </tr>
                </thead>
                <tbody>
                  {data.rows.length === 0 && (
                    <tr>
                      <td colSpan={mfLedger ? 11 : 7} className="py-6 text-center text-muted-foreground">
                        期間内にデータがありません
                      </td>
                    </tr>
                  )}
                  {data.rows.map((row) => {
                    const sev = highestSeverity(row.anomalies)
                    const bg = rowBgClass(sev)
                    const mfDelta = mfLedger ? mfDeltaByMonth.get(row.transactionMonth) ?? 0 : null
                    // C1 反映: バックエンド AccountsPayableIntegrityService と整合させるため effectiveChange を使用
                    // (手動確定行 verified_manually=1 の月で誤発火を防ぐ)
                    const selfDelta = row.effectiveChangeTaxIncluded - row.paymentSettledTaxIncluded
                    const diff = mfDelta !== null ? selfDelta - mfDelta : null
                    // R7 反映: MFA (MINOR amber) / MFA! (MAJOR red) / MFM (MF 側欠落 or 自社欠落) に細分化
                    const selfHasActivity = row.effectiveChangeTaxIncluded !== 0 || row.paymentSettledTaxIncluded !== 0
                    const mfHasActivity = mfDelta !== null && mfDelta !== 0
                    const mfStatus = mfDelta === null
                      ? { code: null, label: '', className: '' }
                      : computeMfMatchStatus(selfDelta, mfDelta, selfHasActivity, mfHasActivity)
                    const mfMismatch = mfStatus.code !== null && mfStatus.code !== 'MATCH'
                    return (
                      <tr key={row.transactionMonth} className={`border-b ${bg}`}>
                        <td className="py-2">{row.transactionMonth}</td>
                        <td className={`py-2 text-right ${row.openingBalanceTaxIncluded < 0 ? 'text-amber-700' : ''}`}>
                          {formatCurrency(row.openingBalanceTaxIncluded)}
                        </td>
                        <td className="py-2 text-right">{formatCurrency(row.changeTaxIncluded)}</td>
                        <td className={`py-2 text-right ${row.verifiedAmount === 0 && row.changeTaxIncluded > 0 ? 'text-red-600' : ''}`}>
                          {row.verifiedAmount === 0 ? '?' : formatCurrency(row.verifiedAmount)}
                        </td>
                        <td className="py-2 text-right">
                          {row.paymentSettledTaxIncluded === 0 ? '?' : formatCurrency(row.paymentSettledTaxIncluded)}
                        </td>
                        <td className={`py-2 text-right font-medium ${row.closingBalanceTaxIncluded < 0 ? 'text-amber-700' : ''}`}>
                          {formatCurrency(row.closingBalanceTaxIncluded)}
                        </td>
                        {mfLedger && (() => {
                          const mfCum = mfCumulativeByMonth.get(row.transactionMonth) ?? 0
                          // 前期繰越 を self 側に加算して cumDiff を対称化。
                          // MF 側 mfCum は既に opening を含むので、自社 closing (DB 由来、opening 欠落) に足す。
                          const opening = mfLedger.openingBalance ?? 0
                          const cumDiff = (row.closingBalanceTaxIncluded + opening) - mfCum
                          const cumClass =
                            cumDiff === 0 ? 'text-green-700'
                            : Math.abs(cumDiff) > 1000 ? 'text-red-700 font-medium'
                            : Math.abs(cumDiff) > 100 ? 'text-amber-700 font-medium'
                            : 'text-muted-foreground'
                          return (
                          <>
                            <td className="py-2 text-right">{formatCurrency(mfDelta ?? 0)}</td>
                            <td className={`py-2 text-right ${
                              mfStatus.code === 'MFA_MAJOR' || mfStatus.code === 'MFM_SELF' || mfStatus.code === 'MFM_MF'
                                ? 'text-red-700 font-medium'
                                : mfStatus.code === 'MFA_MINOR'
                                ? 'text-amber-700 font-medium'
                                : 'text-muted-foreground'
                            }`}>
                              {formatCurrency(diff ?? 0)}
                            </td>
                            <td className="py-2 text-right">{formatCurrency(mfCum)}</td>
                            <td className={`py-2 text-right ${cumClass}`}>{formatCurrency(cumDiff)}</td>
                          </>
                          )
                        })()}
                        <td className="py-2">
                          <div className="flex flex-wrap items-center gap-1">
                            {row.hasPaymentOnly && (
                              <Badge variant="outline" className="text-slate-600 text-xs">支払のみ</Badge>
                            )}
                            {row.hasVerifiedManually && (
                              <Badge variant="outline" className="text-xs">手動</Badge>
                            )}
                            {row.autoAdjustedAmount !== undefined && row.autoAdjustedAmount !== 0 && (
                              <Badge variant="outline"
                                className={`text-xs ${Math.abs(row.autoAdjustedAmount) > 100 ? 'border-red-500 text-red-700' : 'border-amber-500 text-amber-700'}`}
                                title={`振込明細取込で ${row.autoAdjustedAmount > 0 ? '+' : ''}\${row.autoAdjustedAmount.toLocaleString('ja-JP')} 自動調整`}>
                                調整 {row.autoAdjustedAmount > 0 ? '+' : ''}\{Math.abs(row.autoAdjustedAmount).toLocaleString('ja-JP')}
                              </Badge>
                            )}
                            <TooltipProvider>
                              {row.anomalies.map((a) => (
                                <Tooltip key={a.code}>
                                  <TooltipTrigger asChild>
                                    <Badge variant="outline" className={`text-xs cursor-help ${ANOMALY_BADGE_CLASS[a.code]}`}>
                                      {ANOMALY_SHORT_LABEL[a.code]}
                                    </Badge>
                                  </TooltipTrigger>
                                  <TooltipContent>
                                    <p className="text-xs">{a.message}</p>
                                  </TooltipContent>
                                </Tooltip>
                              ))}
                            </TooltipProvider>
                            {mfMismatch && mfStatus.code !== null && (
                              <TooltipProvider>
                                <Tooltip>
                                  <TooltipTrigger asChild>
                                    <Badge variant="outline" className={`text-xs cursor-help ${mfStatus.className}`}>
                                      {mfStatus.label}
                                    </Badge>
                                  </TooltipTrigger>
                                  <TooltipContent>
                                    <p className="text-xs">
                                      {mfStatus.code === 'MFM_SELF' && '自社にあって MF に無い (MF CSV 出力漏れ疑い)'}
                                      {mfStatus.code === 'MFM_MF' && 'MF にあって自社に無い (自社取込漏れ疑い)'}
                                      {(mfStatus.code === 'MFA_MINOR' || mfStatus.code === 'MFA_MAJOR') && (
                                        <>自社 delta {formatCurrency(selfDelta)} ? MF delta {formatCurrency(mfDelta ?? 0)} = {formatCurrency(diff ?? 0)}</>
                                      )}
                                    </p>
                                  </TooltipContent>
                                </Tooltip>
                              </TooltipProvider>
                            )}
                            {row.anomalies.length === 0 && !row.hasPaymentOnly && !mfMismatch && (
                              <span className="flex items-center gap-1 text-xs text-emerald-600">
                                <CheckCircle2 className="h-3 w-3" /> OK
                              </span>
                            )}
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </CardContent>
          </Card>

          <p className="text-xs text-muted-foreground">
            <b>closing</b> = opening + effectiveChange ? payment_settled の T 勘定定義 (Phase B')。
            手動確定月は effectiveChange = verified_amount、それ以外は taxIncludedAmountChange を使用。
            検証額は振込明細 Excel 取込 or 手動確定で記録された金額。
            支払反映は「前月 supplier の検証額を当月 change 比で按分」したもの (Phase B')。
          </p>
          <p className="text-xs text-muted-foreground">
            <b>前期繰越</b>: MF journal #1 (期首残高仕訳) から supplier 別に取得した値 ({'/finance/supplier-opening-balance'} で管理)。
            journal #1 本体は月次 accumulation から除外され、累積残の初期値として注入される。
            未登録 supplier は 0 のままなので、{'/finance/supplier-opening-balance'} で「MF から取得」実行をご検討ください。
          </p>
          <p className="text-xs text-muted-foreground">
            <b>MF 比較</b>: 月次 delta (= credit ? debit) で比較する方式。
            自社 delta (= change ? payment_settled) との月次差が MFX バッジ発火 (閾値 \10,000)。
            累積差列は <code>(自社 closing + 前期繰越) ? MF 累積残</code> で計算され、整合的な supplier では ±\100 以内に収まる。
          </p>
        </>
      )}
    </div>
  )
}

function InfoTile({ label, value, emphasize, hint }: { label: string; value: number; emphasize?: boolean; hint?: string }) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-xs text-muted-foreground" title={hint}>{label}</span>
      <span className={`tabular-nums ${emphasize ? 'font-semibold' : ''} ${value < 0 ? 'text-amber-700' : ''}`}>
        {formatCurrency(value)}
      </span>
    </div>
  )
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1872ms:
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
      <Button variant="outline" size="sm" onClick={onClick} disabled={running}>
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
      header: '前月繰越',
      render: (r) => {
        if (!hasBalance(r)) return <span className="text-muted-foreground">-</span>
        const v = r.openingBalanceTaxIncluded
        const cls = v < 0 ? 'text-amber-700 tabular-nums' : 'tabular-nums'
        return <span className={cls}>{formatCurrency(v)}</span>
      },
    },
    {
      key: 'paymentSettledTaxIncluded',
      header: '当月支払',
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
      header: '累積残',
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
      header: '買掛金額(税込)',
      render: (r) => <span className="tabular-nums">{formatCurrency(r.taxIncludedAmountChange ?? 0)}</span>,
    },
    {
      key: 'verifiedAmount',
      header: '振込明細額',
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
  const columns: Column<AccountsPayable>[] = showBalance
    ? (() => {
        const insertAt = baseColumns.findIndex((c) => c.key === 'mfTransferDate')
        return insertAt < 0
          ? [...baseColumns, ...balanceColumns]
          : [...baseColumns.slice(0, insertAt), ...balanceColumns, ...baseColumns.slice(insertAt)]
      })()
    : baseColumns

  if (apQuery.isLoading && !apQuery.data) return <LoadingSpinner />
  if (apQuery.isError) return <ErrorMessage onRetry={() => apQuery.refetch()} />

  const p = apQuery.data ?? emptyPage<AccountsPayable>(PAGE_SIZE)
  const summary = summaryQuery.data

  // 累積残フィルタ (クライアント側、現ページ内のみ)。balanceFilter=all or showBalance=false の時は素通し
  const filteredContent: AccountsPayable[] =
    showBalance && params.balanceFilter !== 'all'
      ? p.content.filter((r) => {
          if (!hasBalance(r)) return false
          return params.balanceFilter === 'negative'
            ? r.closingBalanceTaxIncluded < 0
            : r.closingBalanceTaxIncluded >= 0
        })
      : p.content
  // 現ページ内の負残件数 (banner 用、payment-only 区別)
  const negativeOnPage = showBalance
    ? p.content.filter((r): r is AccountsPayableWithBalance =>
        hasBalance(r) && r.closingBalanceTaxIncluded < 0,
      )
    : []
  const negativeSum = negativeOnPage.reduce((s, r) => s + r.closingBalanceTaxIncluded, 0)
  const negativePaymentOnlyCount = negativeOnPage.filter((r) => r.isPaymentOnly).length

  const shopOptions = (shopsQuery.data ?? []).map((s) => ({
    value: String(s.shopNo),
    label: `${s.shopNo}: ${s.shopName}`,
  }))
  const supplierOptions = (paymentSuppliersQuery.data ?? []).map((s) => ({
    value: String(s.paymentSupplierNo),
    label: `${s.paymentSupplierCode ?? ''} ${s.paymentSupplierName}`.trim(),
  }))

  return (
    <div className="space-y-4">
      <PageHeader
        title="買掛金一覧"
        actions={
          <>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setBulkDialog(true)}
            >
              <Upload className="mr-1 h-4 w-4" />
              振込明細で一括検証
            </Button>
            {isAdmin && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setVerifiedExportDialog(true)}
              >
                <FileDown className="mr-1 h-4 w-4" />
                買掛支払CSV出力（MF）
              </Button>
            )}
            {isAdmin && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPurchaseJournalExportDialog(true)}
              >
                <FileDown className="mr-1 h-4 w-4" />
                仕入仕訳CSV出力（MF）
              </Button>
            )}
            {isAdmin && (
              <>
                <BatchButton
                  job={BATCH_JOBS.PURCHASE_IMPORT}
                  label="仕入明細取込(SMILE)"
                  icon={<Download className="mr-1 h-4 w-4" />}
                  running={isRunning(BATCH_JOBS.PURCHASE_IMPORT)}
                  status={lastStatus(BATCH_JOBS.PURCHASE_IMPORT)}
                  onClick={() => setConfirmPurchaseImport(true)}
                />
                <BatchButton
                  job={BATCH_JOBS.AGGREGATION}
                  label="再集計"
                  icon={<RefreshCw className="mr-1 h-4 w-4" />}
                  running={isRunning(BATCH_JOBS.AGGREGATION)}
                  status={lastStatus(BATCH_JOBS.AGGREGATION)}
                  onClick={() => runBatchMutation.mutate(BATCH_JOBS.AGGREGATION)}
                />
                <BatchButton
                  job={BATCH_JOBS.VERIFICATION}
                  label="再検証(SMILE)"
                  icon={<ShieldCheck className="mr-1 h-4 w-4" />}
                  running={isRunning(BATCH_JOBS.VERIFICATION)}
                  status={lastStatus(BATCH_JOBS.VERIFICATION)}
                  onClick={() => runBatchMutation.mutate(BATCH_JOBS.VERIFICATION)}
                />
              </>
            )}
          </>
        }
      />

      <div className="rounded border p-4 space-y-3">
        <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
          <div>
            <Label htmlFor="ap-month">取引月</Label>
            <Input
              id="ap-month"
              type="month"
              value={toMonthInput(params.transactionMonth)}
              onChange={(e) => {
                setParams((prev) => ({ ...prev, transactionMonth: fromMonthInput(e.target.value) }))
                setPage(0)
              }}
            />
          </div>
          {isAdmin && (
            <div>
              <Label>ショップ</Label>
              <SearchableSelect
                value={params.shopNo !== undefined ? String(params.shopNo) : ''}
                onValueChange={(v) => {
                  setParams((prev) => ({ ...prev, shopNo: v ? Number(v) : undefined, supplierNo: undefined }))
                  setPage(0)
                }}
                options={shopOptions}
                placeholder="すべて"
                clearable
              />
            </div>
          )}
          <div>
            <Label>仕入先</Label>
            <SearchableSelect
              value={params.supplierNo !== undefined ? String(params.supplierNo) : ''}
              onValueChange={(v) => {
                setParams((prev) => ({ ...prev, supplierNo: v ? Number(v) : undefined }))
                setPage(0)
              }}
              options={supplierOptions}
              placeholder="すべて"
              clearable
            />
          </div>
          <div>
            <Label>検証状態</Label>
            <Select
              value={params.verificationFilter}
              onValueChange={(v) => {
                setParams((prev) => ({ ...prev, verificationFilter: v as VerificationFilter }))
                setPage(0)
              }}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {(Object.keys(VERIFICATION_FILTER_LABELS) as VerificationFilter[]).map((k) => (
                  <SelectItem key={k} value={k}>
                    {VERIFICATION_FILTER_LABELS[k]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
        <div className="flex items-center gap-4 pt-1">
          <div className="flex items-center gap-2">
            <Switch
              id="ap-show-balance"
              checked={showBalance}
              onCheckedChange={(checked) => {
                setShowBalance(checked)
                // OFF → 残高フィルタも自動 all へ戻す
                if (!checked) {
                  setParams((prev) => ({ ...prev, balanceFilter: 'all' }))
                }
              }}
            />
            <Label htmlFor="ap-show-balance" className="cursor-pointer">累積残を表示</Label>
          </div>
          {showBalance && (
            <div className="flex items-center gap-2">
              <Label className="text-muted-foreground text-xs">累積残フィルタ</Label>
              <Select
                value={params.balanceFilter}
                onValueChange={(v) => {
                  setParams((prev) => ({ ...prev, balanceFilter: v as BalanceFilter }))
                }}
              >
                <SelectTrigger className="h-8 w-40">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {(Object.keys(BALANCE_FILTER_LABELS) as BalanceFilter[]).map((k) => (
                    <SelectItem key={k} value={k}>
                      {BALANCE_FILTER_LABELS[k]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}
        </div>
      </div>

      {summary && (summary.unverifiedCount > 0 || summary.unmatchedCount > 0) && (
        <div
          role="alert"
          className="rounded border border-orange-300 bg-orange-50 p-3 text-sm text-orange-800"
        >
          <div className="flex items-center gap-2 font-medium">
            <AlertCircle className="h-4 w-4" />
            要対応: 未検証 {summary.unverifiedCount}件 / 不一致 {summary.unmatchedCount}件
            {summary.unmatchedCount > 0 && (
              <span>（差額合計 {formatCurrency(summary.unmatchedDifferenceSum)}）</span>
            )}
          </div>
        </div>
      )}

      {showBalance && negativeOnPage.length > 0 && (
        <div
          role="alert"
          className="rounded border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800"
        >
          <div className="flex items-center gap-2 font-medium">
            <AlertCircle className="h-4 w-4" />
            累積負残: {negativeOnPage.length}件
            {negativePaymentOnlyCount > 0 && (
              <span className="text-xs">（うち支払超過 {negativePaymentOnlyCount}件）</span>
            )}
            （合計 {formatCurrency(negativeSum)}）※ 値引/支払超過による繰越負残。現在のページ内集計
          </div>
        </div>
      )}

      <Tabs value={currentTab} onValueChange={(v) => setCurrentTab(v as 'payable' | 'aux')}>
        <TabsList>
          <TabsTrigger value="payable">
            買掛金一覧
            {p.totalElements > 0 && (
              <span className="ml-2 text-xs text-muted-foreground">({p.totalElements})</span>
            )}
          </TabsTrigger>
          <TabsTrigger value="aux">
            MF補助行
            {auxCount !== null && (
              <span className="ml-2 text-xs text-muted-foreground">({auxCount})</span>
            )}
          </TabsTrigger>
        </TabsList>
        <TabsContent value="payable" className="mt-3">
          <DataTable
            data={filteredContent}
            columns={columns}
            serverPagination={{
              page: p.number,
              pageSize: p.size,
              totalElements: p.totalElements,
              totalPages: p.totalPages,
              onPageChange: setPage,
            }}
          />
        </TabsContent>
        <TabsContent value="aux" className="mt-3">
          <PaymentMfAuxRowsTable
            transactionMonth={params.transactionMonth}
            onCountChange={setAuxCount}
          />
        </TabsContent>
      </Tabs>

      <VerifyDialog
        key={dialogRow
          ? `${dialogRow.shopNo}-${dialogRow.supplierNo}-${dialogRow.transactionMonth}-${dialogRow.taxRate}`
          : 'closed'}
        row={dialogRow}
        onClose={() => setDialogRow(null)}
        onSubmit={(verifiedAmount, note) =>
          dialogRow && verifyMutation.mutate({ row: dialogRow, verifiedAmount, note })
        }
        onReleaseManualLock={() => dialogRow && releaseManualMutation.mutate(dialogRow)}
        submitting={verifyMutation.isPending}
        releasing={releaseManualMutation.isPending}
        isAdmin={isAdmin}
      />

      <BulkVerifyDialog
        open={bulkDialog}
        onOpenChange={setBulkDialog}
        onApplied={invalidate}
      />

      <VerifiedCsvExportDialog
        open={verifiedExportDialog}
        onOpenChange={setVerifiedExportDialog}
        transactionMonth={params.transactionMonth}
      />

      <PurchaseJournalCsvExportDialog
        open={purchaseJournalExportDialog}
        onOpenChange={setPurchaseJournalExportDialog}
        transactionMonth={params.transactionMonth}
      />

      <ConfirmDialog
        open={confirmPurchaseImport}
        onOpenChange={setConfirmPurchaseImport}
        title="仕入明細取込(SMILE)"
        description="SMILEから仕入明細を取り込みます。よろしいですか？（取込後は「再集計」で買掛金に反映してください）"
        confirmLabel="取込実行"
        onConfirm={() => runBatchMutation.mutate(BATCH_JOBS.PURCHASE_IMPORT)}
      />
    </div>
  )
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2849ms:
'use client'

import { useCallback, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useRouter, useSearchParams } from 'next/navigation'
import { api, ApiError } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops } from '@/hooks/use-master-data'
import { PageHeader } from '@/components/features/common/PageHeader'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { formatCurrency } from '@/lib/utils'
import { Loader2, RefreshCw, Search } from 'lucide-react'
import { toast } from 'sonner'
import {
  STATUS_CLASS,
  STATUS_LABEL,
  type SupplierBalancesResponse,
  type SupplierBalanceStatus,
} from '@/types/supplier-balances'
import { defaultToMonth } from '@/types/accounts-payable-ledger'

export function SupplierBalancesPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const router = useRouter()
  const urlParams = useSearchParams()

  const [shopNo, setShopNo] = useState<number | undefined>(
    urlParams.get('shopNo') ? Number(urlParams.get('shopNo'))
      : isAdmin ? undefined : user?.shopNo,
  )
  const [asOfMonth, setAsOfMonth] = useState<string>(urlParams.get('asOfMonth') || defaultToMonth())
  const [report, setReport] = useState<SupplierBalancesResponse | null>(null)
  const [statusFilter, setStatusFilter] = useState<Set<SupplierBalanceStatus>>(
    new Set(['MINOR', 'MAJOR', 'MF_MISSING', 'SELF_MISSING']),
  )

  const shopsQuery = useShops(isAdmin)
  const shopOptions = (shopsQuery.data ?? []).map((s) => ({
    value: String(s.shopNo),
    label: `${s.shopNo}: ${s.shopName}`,
  }))

  const updateUrl = useCallback(() => {
    const sp = new URLSearchParams()
    if (shopNo !== undefined) sp.set('shopNo', String(shopNo))
    sp.set('asOfMonth', asOfMonth)
    router.replace(`?${sp.toString()}`, { scroll: false })
  }, [router, shopNo, asOfMonth])

  const runMutation = useMutation({
    mutationFn: async (refresh: boolean = false) => {
      if (shopNo === undefined) throw new Error('ショップを選択してください')
      const sp = new URLSearchParams()
      sp.set('shopNo', String(shopNo))
      sp.set('asOfMonth', asOfMonth)
      if (refresh) sp.set('refresh', 'true')
      return api.get<SupplierBalancesResponse>(`/finance/accounts-payable/supplier-balances?${sp.toString()}`)
    },
    onSuccess: (res, refresh) => {
      setReport(res)
      const s = res.summary
      const issues = s.minorCount + s.majorCount + s.mfMissingCount + s.selfMissingCount
      const prefix = refresh ? 'MF API 再取得: ' : ''
      if (issues === 0) toast.success(`${prefix}全 ${s.totalSuppliers} supplier 一致。`)
      else toast.warning(`${prefix}MAJOR ${s.majorCount} / MINOR ${s.minorCount} / MF欠 ${s.mfMissingCount} / 自社欠 ${s.selfMissingCount}`)
    },
    onError: (e: Error) => {
      if (e instanceof ApiError && e.status === 401) toast.error('MF 認証エラー。「MF 連携状況」画面で再認証してください。')
      else if (e instanceof ApiError && e.status === 403) toast.error('MF scope 不足です。「MF 連携状況」で scope 更新してください。')
      else if (e instanceof ApiError && e.status === 400) toast.error(`入力エラー: ${e.message}`)
      else toast.error(e.message)
    },
  })

  const handleSearch = (refresh = false) => {
    if (shopNo === undefined) {
      toast.warning('ショップを選択してください')
      return
    }
    updateUrl()
    runMutation.mutate(refresh)
  }

  const handleRefresh = () => {
    if (!window.confirm('MF API から最新データを再取得します。10?15 秒かかる場合があります。')) return
    handleSearch(true)
  }

  const gotoLedger = (supplierNo: number | null) => {
    if (!supplierNo || shopNo === undefined || !report?.asOfMonth) return
    const sp = new URLSearchParams()
    sp.set('shopNo', String(shopNo))
    sp.set('supplierNo', String(supplierNo))
    // 過去 12 ヶ月
    sp.set('fromMonth', report.mfStartDate)
    sp.set('toMonth', report.asOfMonth)
    window.open(`/finance/accounts-payable-ledger?${sp.toString()}`, '_blank')
  }

  const toggleFilter = (status: SupplierBalanceStatus) => {
    const next = new Set(statusFilter)
    if (next.has(status)) next.delete(status)
    else next.add(status)
    setStatusFilter(next)
  }

  const visibleRows = (report?.rows ?? []).filter((r) => statusFilter.has(r.status))

  return (
    <div className="space-y-4">
      <PageHeader title="買掛 supplier 累積残一覧" />

      <Card>
        <CardContent className="pt-4 space-y-3">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
            {isAdmin && (
              <div>
                <Label>ショップ *</Label>
                <SearchableSelect
                  value={shopNo !== undefined ? String(shopNo) : ''}
                  onValueChange={(v) => setShopNo(v ? Number(v) : undefined)}
                  options={shopOptions}
                  placeholder="選択してください"
                  clearable
                />
              </div>
            )}
            <div>
              <Label htmlFor="as-of-month">基準月 (20日締め)</Label>
              <Input id="as-of-month" type="month" value={asOfMonth.slice(0, 7)}
                     onChange={(e) => setAsOfMonth(`${e.target.value}-20`)} />
            </div>
            <div className="flex items-end gap-2">
              <Button onClick={() => handleSearch(false)} disabled={runMutation.isPending}>
                {runMutation.isPending ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : <Search className="mr-1 h-4 w-4" />}
                残高チェック
              </Button>
              <Button variant="outline" onClick={handleRefresh} disabled={runMutation.isPending}
                      title="MF API から再取得 (キャッシュ無視)">
                <RefreshCw className="mr-1 h-4 w-4" /> 最新取得
              </Button>
            </div>
          </div>
          <p className="text-xs text-muted-foreground">
            期首 (2025-05-20) ? 基準月 の全 supplier 累積残を自社 / MF で突合。MF /journals はキャッシュ共有のため 2 回目以降は高速。
          </p>
          {report?.fetchedAt && (
            <p className="text-xs text-muted-foreground">
              取得日時: {new Date(report.fetchedAt).toLocaleString('ja-JP')}
              {' / '} journals: {report.totalJournalCount} 件
              {' / '} MF 採用開始日: {report.mfStartDate}
            </p>
          )}
        </CardContent>
      </Card>

      {report && (
        <>
          <Card>
            <CardContent className="pt-4">
              <div className="grid grid-cols-2 gap-2 text-sm md:grid-cols-5">
                <SummaryTile label="一致" count={report.summary.matchedCount} color="green"
                             selected={statusFilter.has('MATCH')} onClick={() => toggleFilter('MATCH')} />
                <SummaryTile label="金額差 (軽)" count={report.summary.minorCount} color="amber"
                             selected={statusFilter.has('MINOR')} onClick={() => toggleFilter('MINOR')} />
                <SummaryTile label="金額差 (重)" count={report.summary.majorCount} color="red"
                             selected={statusFilter.has('MAJOR')} onClick={() => toggleFilter('MAJOR')} />
                <SummaryTile label="MF 未計上" count={report.summary.mfMissingCount} color="red"
                             selected={statusFilter.has('MF_MISSING')} onClick={() => toggleFilter('MF_MISSING')} />
                <SummaryTile label="自社未計上" count={report.summary.selfMissingCount} color="red"
                             selected={statusFilter.has('SELF_MISSING')} onClick={() => toggleFilter('SELF_MISSING')} />
              </div>
              <div className="mt-3 grid grid-cols-3 gap-2 text-xs text-muted-foreground">
                <div>self 合計: <span className="font-mono text-foreground">{formatCurrency(report.summary.totalSelfBalance)}</span></div>
                <div>MF 合計: <span className="font-mono text-foreground">{formatCurrency(report.summary.totalMfBalance)}</span></div>
                <div>diff 合計: <span className={`font-mono ${Math.abs(report.summary.totalDiff) > 100 ? 'text-red-700' : 'text-foreground'}`}>{formatCurrency(report.summary.totalDiff)}</span></div>
              </div>
            </CardContent>
          </Card>

          <Card><CardContent className="pt-4 overflow-x-auto">
            <table className="w-full text-sm tabular-nums">
              <thead>
                <tr className="border-b text-left">
                  <th className="py-2">supplier</th>
                  <th className="py-2 text-right">self 残</th>
                  <th className="py-2 text-right">MF 残</th>
                  <th className="py-2 text-right">diff</th>
                  <th className="py-2">status</th>
                  <th className="py-2 text-right">opening</th>
                  <th className="py-2 text-right">Σchange</th>
                  <th className="py-2 text-right">Σpayment</th>
                </tr>
              </thead>
              <tbody>
                {visibleRows.length === 0 && (
                  <tr>
                    <td colSpan={8} className="py-6 text-center text-muted-foreground">該当 supplier なし</td>
                  </tr>
                )}
                {visibleRows.map((r) => (
                  <tr key={`${r.supplierNo ?? 'null'}-${r.mfSubAccountNames[0] ?? ''}`}
                      className="border-b hover:bg-accent cursor-pointer"
                      onClick={() => gotoLedger(r.supplierNo)}>
                    <td className="py-2">
                      <div>{r.supplierName}</div>
                      {r.supplierCode && <div className="text-xs text-muted-foreground">{r.supplierCode}</div>}
                    </td>
                    <td className="py-2 text-right">{formatCurrency(r.selfBalance)}</td>
                    <td className="py-2 text-right">{formatCurrency(r.mfBalance)}</td>
                    <td className={`py-2 text-right font-medium ${Math.abs(r.diff) > 1000 ? 'text-red-700' : Math.abs(r.diff) > 100 ? 'text-amber-700' : 'text-muted-foreground'}`}>
                      {formatCurrency(r.diff)}
                    </td>
                    <td className="py-2">
                      <Badge variant="outline" className={STATUS_CLASS[r.status]}>
                        {STATUS_LABEL[r.status]}
                      </Badge>
                      {r.status === 'MF_MISSING' && !r.masterRegistered && (
                        <span className="ml-1 text-xs text-red-600">(master 未登録)</span>
                      )}
                    </td>
                    <td className="py-2 text-right text-muted-foreground">{formatCurrency(r.selfOpening)}</td>
                    <td className="py-2 text-right text-muted-foreground">{formatCurrency(r.selfChangeCumulative)}</td>
                    <td className="py-2 text-right text-muted-foreground">{formatCurrency(r.selfPaymentCumulative)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </CardContent></Card>
        </>
      )}
    </div>
  )
}

function SummaryTile({
  label, count, color, selected, onClick,
}: { label: string; count: number; color: 'red' | 'amber' | 'slate' | 'green'; selected?: boolean; onClick?: () => void }) {
  const colorClass = {
    red: 'border-red-600 text-red-700',
    amber: 'border-amber-500 text-amber-700',
    slate: 'border-slate-400 text-slate-700',
    green: 'border-green-500 text-green-700',
  }[color]
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex flex-col items-start rounded border px-3 py-2 text-left transition ${colorClass} ${selected ? 'bg-accent' : 'opacity-50 hover:opacity-100'}`}
    >
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="text-lg font-semibold">{count}</span>
    </button>
  )
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3845ms:
'use client'

import { useCallback, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useRouter, useSearchParams } from 'next/navigation'
import Link from 'next/link'
import { api, ApiError } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops } from '@/hooks/use-master-data'
import { PageHeader } from '@/components/features/common/PageHeader'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatCurrency } from '@/lib/utils'
import { AlertCircle, Loader2, RefreshCw, Search } from 'lucide-react'
import { toast } from 'sonner'
import type { IntegrityReportResponse } from '@/types/integrity-report'
import { MISMATCH_SEVERITY_CLASS, MISMATCH_SEVERITY_LABEL } from '@/types/integrity-report'
import { defaultFromMonth, defaultToMonth } from '@/types/accounts-payable-ledger'

/**
 * 買掛帳 整合性レポート画面 (軸 B + 軸 C)。
 * 期間内の全 supplier を一括診断し、MF 側のみ / 自社側のみ / 金額差 / MF 未登録 を 4 タブで表示。
 * 設計書: claudedocs/design-integrity-report.md §9
 */
export function IntegrityReportPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const router = useRouter()
  const urlParams = useSearchParams()

  // 整合性レポートは期間を短めに (デフォルト 6 ヶ月、最大 12 ヶ月)
  const initialToMonth = urlParams.get('toMonth') || defaultToMonth()
  const initialFromMonth = urlParams.get('fromMonth') || shortDefaultFromMonth(initialToMonth)

  const [shopNo, setShopNo] = useState<number | undefined>(
    urlParams.get('shopNo') ? Number(urlParams.get('shopNo'))
      : isAdmin ? undefined : user?.shopNo,
  )
  const [fromMonth, setFromMonth] = useState<string>(initialFromMonth)
  const [toMonth, setToMonth] = useState<string>(initialToMonth)
  const [report, setReport] = useState<IntegrityReportResponse | null>(null)
  const [hideReconciled, setHideReconciled] = useState<boolean>(true)
  const [hideReviewed, setHideReviewed] = useState<boolean>(true)

  const shopsQuery = useShops(isAdmin)
  const shopOptions = (shopsQuery.data ?? []).map((s) => ({
    value: String(s.shopNo),
    label: `${s.shopNo}: ${s.shopName}`,
  }))

  const updateUrl = useCallback(() => {
    const sp = new URLSearchParams()
    if (shopNo !== undefined) sp.set('shopNo', String(shopNo))
    sp.set('fromMonth', fromMonth)
    sp.set('toMonth', toMonth)
    router.replace(`?${sp.toString()}`, { scroll: false })
  }, [router, shopNo, fromMonth, toMonth])

  // 差分確認 POST / DELETE (案 X+Y)
  const reviewMutation = useMutation({
    mutationFn: async (args: {
      entryType: 'mfOnly' | 'selfOnly' | 'amountMismatch'
      entryKey: string
      transactionMonth: string
      actionType: 'IGNORE' | 'MF_APPLY'
      selfSnapshot: number
      mfSnapshot: number
      note?: string
    }) => {
      return api.post(`/finance/accounts-payable/integrity-report/reviews`, {
        shopNo,
        entryType: args.entryType,
        entryKey: args.entryKey,
        transactionMonth: args.transactionMonth,
        actionType: args.actionType,
        selfSnapshot: args.selfSnapshot,
        mfSnapshot: args.mfSnapshot,
        note: args.note ?? null,
      })
    },
    onSuccess: (_, v) => {
      toast.success(`${v.actionType === 'MF_APPLY' ? 'MF 金額で自社確定' : '確認済みマーク'}を保存しました`)
      runMutation.mutate(false)
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const reviewDeleteMutation = useMutation({
    mutationFn: async (args: { entryType: string; entryKey: string; transactionMonth: string }) => {
      const sp = new URLSearchParams()
      sp.set('shopNo', String(shopNo))
      sp.set('entryType', args.entryType)
      sp.set('entryKey', args.entryKey)
      sp.set('transactionMonth', args.transactionMonth)
      return api.delete(`/finance/accounts-payable/integrity-report/reviews?${sp.toString()}`)
    },
    onSuccess: () => {
      toast.success('確認履歴を取り消しました')
      runMutation.mutate(false)
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const confirmReview = (args: Parameters<typeof reviewMutation.mutate>[0]) => {
    const actionLabel = args.actionType === 'MF_APPLY' ? 'MF 金額で自社 verified_amount を上書き' : '確認済みとしてマーク (副作用なし)'
    const note = window.prompt(`${actionLabel}\n備考 (任意):`, '')
    if (note === null) return
    reviewMutation.mutate({ ...args, note })
  }

  const runMutation = useMutation({
    mutationFn: async (refresh: boolean = false) => {
      if (shopNo === undefined) {
        throw new Error('ショップを選択してください')
      }
      const sp = new URLSearchParams()
      sp.set('shopNo', String(shopNo))
      sp.set('fromMonth', fromMonth)
      sp.set('toMonth', toMonth)
      if (refresh) sp.set('refresh', 'true')
      return api.get<IntegrityReportResponse>(`/finance/accounts-payable/integrity-report?${sp.toString()}`)
    },
    onSuccess: (res, refresh) => {
      setReport(res)
      const s = res.summary
      const total = s.mfOnlyCount + s.selfOnlyCount + s.amountMismatchCount
      const prefix = refresh ? 'MF API から再取得しました。' : ''
      if (total === 0 && s.unmatchedSupplierCount === 0) {
        toast.success(`${prefix}全 supplier で MF と整合しました。`)
      } else {
        toast.warning(`${prefix}MF 側のみ ${s.mfOnlyCount} / 自社側のみ ${s.selfOnlyCount} / 金額差 ${s.amountMismatchCount} 件、要確認。`)
      }
    },
    onError: (e: Error) => {
      if (e instanceof ApiError && e.status === 401) {
        toast.error('MF 認証エラー。「MF 連携状況」画面で再認証してください。')
      } else if (e instanceof ApiError && e.status === 403) {
        toast.error('MF scope 不足です。「MF 連携状況」で scope 更新 + 再認証してください。')
      } else if (e instanceof ApiError && e.status === 400) {
        toast.error(`入力エラー: ${e.message}`)
      } else {
        toast.error(e.message)
      }
    },
  })

  const handleSearch = (refresh = false) => {
    if (shopNo === undefined) {
      toast.warning('ショップを選択してください')
      return
    }
    updateUrl()
    runMutation.mutate(refresh)
  }

  const handleRefresh = () => {
    if (!window.confirm('MF API から最新データを再取得します。10?15 秒かかる場合があります。続行しますか?')) return
    handleSearch(true)
  }

  const gotoLedger = (supplierNo?: number | null) => {
    if (!supplierNo || shopNo === undefined) return
    const sp = new URLSearchParams()
    sp.set('shopNo', String(shopNo))
    sp.set('supplierNo', String(supplierNo))
    sp.set('fromMonth', fromMonth)
    sp.set('toMonth', toMonth)
    window.open(`/finance/accounts-payable-ledger?${sp.toString()}`, '_blank')
  }

  return (
    <div className="space-y-4">
      <PageHeader title="買掛帳 整合性レポート (全仕入先)" />

      <Card>
        <CardContent className="pt-4 space-y-3">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
            {isAdmin && (
              <div>
                <Label>ショップ *</Label>
                <SearchableSelect
                  value={shopNo !== undefined ? String(shopNo) : ''}
                  onValueChange={(v) => setShopNo(v ? Number(v) : undefined)}
                  options={shopOptions}
                  placeholder="選択してください"
                  clearable
                />
              </div>
            )}
            <div>
              <Label htmlFor="from-month">開始月</Label>
              <Input id="from-month" type="month" value={fromMonth.slice(0, 7)}
                     onChange={(e) => setFromMonth(`${e.target.value}-20`)} />
            </div>
            <div>
              <Label htmlFor="to-month">終了月</Label>
              <Input id="to-month" type="month" value={toMonth.slice(0, 7)}
                     onChange={(e) => setToMonth(`${e.target.value}-20`)} />
            </div>
            <div className="flex items-end gap-2">
              <Button onClick={() => handleSearch(false)} disabled={runMutation.isPending}>
                {runMutation.isPending
                  ? <Loader2 className="mr-1 h-4 w-4 animate-spin" />
                  : <Search className="mr-1 h-4 w-4" />}
                整合性チェック
              </Button>
              <Button variant="outline" onClick={handleRefresh} disabled={runMutation.isPending}
                      title="MF API から再取得 (キャッシュ無視)">
                <RefreshCw className="mr-1 h-4 w-4" />
                最新取得
              </Button>
            </div>
          </div>
          <p className="text-xs text-muted-foreground">
            通常は月単位キャッシュから表示 (初回のみ MF API 通信)。MF 側で仕訳を変更した後は「最新取得」で再取得してください。
          </p>
          {report?.fetchedAt && (
            <p className="text-xs text-muted-foreground">
              取得日時: {new Date(report.fetchedAt).toLocaleString('ja-JP', { dateStyle: 'short', timeStyle: 'short' })}
              <span className="ml-2">(期間内の最古キャッシュ時刻)</span>
            </p>
          )}
        </CardContent>
      </Card>

      {report && (() => {
        const filterFn = <T extends { reconciledAtPeriodEnd?: boolean; reviewStatus?: string | null; snapshotStale?: boolean }>(arr: T[]) =>
          arr.filter((e) => {
            // 期末解消済みをデフォルト非表示
            if (hideReconciled && e.reconciledAtPeriodEnd) return false
            // 確認済み (review 付与かつ stale でない) をデフォルト非表示
            if (hideReviewed && e.reviewStatus && !e.snapshotStale) return false
            return true
          })
        const mfOnlyView = filterFn(report.mfOnly)
        const selfOnlyView = filterFn(report.selfOnly)
        const mismatchView = filterFn(report.amountMismatch)
        const reconciledTotal = report.summary.reconciledAtPeriodEndCount ?? 0
        return (
        <>
          <Card>
            <CardContent className="pt-4 space-y-3">
              <div className="grid grid-cols-2 gap-2 text-sm md:grid-cols-5">
                <SummaryTile label="MF 側のみ" count={report.summary.mfOnlyCount} amount={report.summary.totalMfOnlyAmount} color="red" />
                <SummaryTile label="自社側のみ" count={report.summary.selfOnlyCount} amount={report.summary.totalSelfOnlyAmount} color="red" />
                <SummaryTile label="金額差" count={report.summary.amountMismatchCount} amount={report.summary.totalMismatchAmount} color="amber" />
                <SummaryTile label="MF 未登録 supplier" count={report.summary.unmatchedSupplierCount} color="slate" />
                <div className="flex flex-col justify-center text-xs text-muted-foreground">
                  journals: {report.totalJournalCount} 件 / supplier: {report.supplierCount}
                </div>
              </div>
              <div className="flex flex-wrap items-center gap-4 pt-2 border-t">
                <label className="flex items-center gap-2 text-xs cursor-pointer">
                  <input type="checkbox" checked={hideReconciled}
                         onChange={(e) => setHideReconciled(e.target.checked)} />
                  期末累積残で解消済み ({reconciledTotal} 件) を隠す
                </label>
                <label className="flex items-center gap-2 text-xs cursor-pointer">
                  <input type="checkbox" checked={hideReviewed}
                         onChange={(e) => setHideReviewed(e.target.checked)} />
                  確認済み (IGNORED / MF_APPLIED、金額不変) を隠す
                </label>
              </div>
            </CardContent>
          </Card>

          <Tabs defaultValue="mfOnly">
            <TabsList>
              <TabsTrigger value="mfOnly">MF 側のみ ({mfOnlyView.length}{hideReconciled && mfOnlyView.length !== report.mfOnly.length ? `/${report.mfOnly.length}` : ''})</TabsTrigger>
              <TabsTrigger value="selfOnly">自社側のみ ({selfOnlyView.length}{hideReconciled && selfOnlyView.length !== report.selfOnly.length ? `/${report.selfOnly.length}` : ''})</TabsTrigger>
              <TabsTrigger value="mismatch">金額差 ({mismatchView.length}{hideReconciled && mismatchView.length !== report.amountMismatch.length ? `/${report.amountMismatch.length}` : ''})</TabsTrigger>
              <TabsTrigger value="unmatched">MF 未登録 ({report.unmatchedSuppliers.length})</TabsTrigger>
            </TabsList>

            <TabsContent value="mfOnly" className="mt-3">
              <Card><CardContent className="pt-4 overflow-x-auto">
                <table className="w-full text-sm tabular-nums">
                  <thead>
                    <tr className="border-b text-left">
                      <th className="py-2">月</th>
                      <th className="py-2">MF 補助科目</th>
                      <th className="py-2 text-right">貸方</th>
                      <th className="py-2 text-right">借方</th>
                      <th className="py-2 text-right">差額</th>
                      <th className="py-2 pr-4 text-right">明細数</th>
                      <th className="py-2 pl-2">MF 取引番号</th>
                      <th className="py-2">推定仕入先</th>
                      <th className="py-2 text-right">累積差 ({report.toMonth})</th>
                      <th className="py-2">備考</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mfOnlyView.length === 0 && <tr><td colSpan={10} className="py-6 text-center text-muted-foreground">該当なし</td></tr>}
                    {mfOnlyView.map((e, i) => (
                      <tr key={i} className={`border-b cursor-pointer ${e.reconciledAtPeriodEnd ? 'opacity-60 hover:bg-slate-50' : 'hover:bg-red-50'}`}
                          onClick={() => gotoLedger(e.guessedSupplierNo)}>
                        <td className="py-2">{e.transactionMonth}{e.reconciledAtPeriodEnd && <span className="ml-1 text-[10px] text-green-700">[解消済]</span>}</td>
                        <td className="py-2">{e.subAccountName}</td>
                        <td className="py-2 text-right">{formatCurrency(e.creditAmount)}</td>
                        <td className="py-2 text-right">{formatCurrency(e.debitAmount)}</td>
                        <td className="py-2 text-right font-medium">{formatCurrency(e.periodDelta)}</td>
                        <td className="py-2 pr-4 text-right">{e.branchCount}</td>
                        <td className="py-2 pl-2 text-xs font-mono text-muted-foreground">
                          {e.journalNumbers && e.journalNumbers.length > 0
                            ? e.journalNumbers.length <= 5
                              ? e.journalNumbers.join(', ')
                              : `${e.journalNumbers.slice(0, 5).join(', ')} 他 ${e.journalNumbers.length - 5} 件`
                            : '-'}
                        </td>
                        <td className="py-2">{e.guessedSupplierCode ?? '-'}</td>
                        <td className={`py-2 text-right font-medium ${
                          e.supplierCumulativeDiff == null ? 'text-muted-foreground'
                          : e.supplierCumulativeDiff === 0 ? 'text-green-700'
                          : Math.abs(e.supplierCumulativeDiff) > 1000 ? 'text-red-700' : 'text-amber-700'
                        }`}>
                          {e.supplierCumulativeDiff == null ? '-' : formatCurrency(e.supplierCumulativeDiff)}
                        </td>
                        <td className="py-2 text-xs text-muted-foreground">{e.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </CardContent></Card>
            </TabsContent>

            <TabsContent value="selfOnly" className="mt-3">
              <Card><CardContent className="pt-4 overflow-x-auto">
                <table className="w-full text-sm tabular-nums">
                  <thead>
                    <tr className="border-b text-left">
                      <th className="py-2">月</th>
                      <th className="py-2">仕入先コード</th>
                      <th className="py-2">仕入先名</th>
                      <th className="py-2 text-right">自社差額</th>
                      <th className="py-2 text-right">仕入</th>
                      <th className="py-2 text-right">支払反映</th>
                      <th className="py-2 text-right">税率行数</th>
                      <th className="py-2 text-right">累積差 ({report.toMonth})</th>
                      <th className="py-2">備考</th>
                    </tr>
                  </thead>
                  <tbody>
                    {selfOnlyView.length === 0 && <tr><td colSpan={9} className="py-6 text-center text-muted-foreground">該当なし</td></tr>}
                    {selfOnlyView.map((e, i) => (
                      <tr key={i} className={`border-b cursor-pointer ${e.reconciledAtPeriodEnd ? 'opacity-60 hover:bg-slate-50' : 'hover:bg-red-50'}`}
                          onClick={() => gotoLedger(e.supplierNo)}>
                        <td className="py-2">{e.transactionMonth}{e.reconciledAtPeriodEnd && <span className="ml-1 text-[10px] text-green-700">[解消済]</span>}</td>
                        <td className="py-2">{e.supplierCode}</td>
                        <td className="py-2 text-blue-600 hover:underline">{e.supplierName}</td>
                        <td className="py-2 text-right font-medium">{formatCurrency(e.selfDelta)}</td>
                        <td className="py-2 text-right">{formatCurrency(e.changeTaxIncluded)}</td>
                        <td className="py-2 text-right">{formatCurrency(e.paymentSettledTaxIncluded)}</td>
                        <td className="py-2 text-right">{e.taxRateRowCount}</td>
                        <td className={`py-2 text-right font-medium ${
                          e.supplierCumulativeDiff == null ? 'text-muted-foreground'
                          : e.supplierCumulativeDiff === 0 ? 'text-green-700'
                          : Math.abs(e.supplierCumulativeDiff) > 1000 ? 'text-red-700' : 'text-amber-700'
                        }`}>
                          {e.supplierCumulativeDiff == null ? '-' : formatCurrency(e.supplierCumulativeDiff)}
                        </td>
                        <td className="py-2 text-xs text-muted-foreground">{e.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </CardContent></Card>
            </TabsContent>

            <TabsContent value="mismatch" className="mt-3">
              <Card><CardContent className="pt-4 overflow-x-auto">
                <table className="w-full text-sm tabular-nums">
                  <thead>
                    <tr className="border-b text-left">
                      <th className="py-2">月</th>
                      <th className="py-2">仕入先コード</th>
                      <th className="py-2">仕入先名</th>
                      <th className="py-2 text-right">自社差額</th>
                      <th className="py-2 text-right">MF 差額</th>
                      <th className="py-2 text-right">差 (自社 ? MF)</th>
                      <th className="py-2 text-right">累積差 ({report.toMonth})</th>
                      <th className="py-2">重大度</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mismatchView.length === 0 && <tr><td colSpan={8} className="py-6 text-center text-muted-foreground">該当なし</td></tr>}
                    {mismatchView.map((e, i) => (
                      <tr key={i} className={`border-b cursor-pointer ${e.reconciledAtPeriodEnd ? 'opacity-60 hover:bg-slate-50' : 'hover:bg-amber-50'}`}
                          onClick={() => gotoLedger(e.supplierNo)}>
                        <td className="py-2">{e.transactionMonth}{e.reconciledAtPeriodEnd && <span className="ml-1 text-[10px] text-green-700">[解消済]</span>}</td>
                        <td className="py-2">{e.supplierCode}</td>
                        <td className="py-2 text-blue-600 hover:underline">{e.supplierName}</td>
                        <td className="py-2 text-right">{formatCurrency(e.selfDelta)}</td>
                        <td className="py-2 text-right">{formatCurrency(e.mfDelta)}</td>
                        <td className="py-2 text-right font-medium">{formatCurrency(e.diff)}</td>
                        <td className={`py-2 text-right font-medium ${
                          e.supplierCumulativeDiff == null
                            ? 'text-muted-foreground'
                            : e.supplierCumulativeDiff === 0
                            ? 'text-green-700'
                            : Math.abs(e.supplierCumulativeDiff) > 1000
                            ? 'text-red-700'
                            : 'text-amber-700'
                        }`}>
                          {e.supplierCumulativeDiff == null ? '-' : formatCurrency(e.supplierCumulativeDiff)}
                        </td>
                        <td className="py-2">
                          <div className="flex flex-wrap items-center gap-1">
                            <Badge variant="outline" className={`text-xs ${MISMATCH_SEVERITY_CLASS[e.severity]}`}>
                              {MISMATCH_SEVERITY_LABEL[e.severity]} ({e.severity})
                            </Badge>
                            {e.reviewStatus && (
                              <Badge variant="outline" className={`text-xs ${e.snapshotStale ? 'border-amber-500 text-amber-700' : 'border-green-500 text-green-700'}`}
                                     title={`${e.reviewedByName ?? ''} ${e.reviewedAt ?? ''} ${e.reviewNote ?? ''}${e.snapshotStale ? ' (金額変動 ? 要再確認)' : ''}`}>
                                {e.reviewStatus === 'MF_APPLIED' ? '?MF確定' : '?確認'}{e.snapshotStale ? '?' : ''}
                              </Badge>
                            )}
                            {!e.reviewStatus && e.supplierNo && (
                              <div className="flex gap-1" onClick={(ev) => ev.stopPropagation()}>
                                <button className="text-[10px] text-blue-600 hover:underline"
                                  onClick={() => confirmReview({
                                    entryType: 'amountMismatch', entryKey: String(e.supplierNo),
                                    transactionMonth: e.transactionMonth, actionType: 'IGNORE',
                                    selfSnapshot: e.selfDelta, mfSnapshot: e.mfDelta,
                                  })}>確認済</button>
                                <button className="text-[10px] text-red-600 hover:underline"
                                  onClick={() => confirmReview({
                                    entryType: 'amountMismatch', entryKey: String(e.supplierNo),
                                    transactionMonth: e.transactionMonth, actionType: 'MF_APPLY',
                                    selfSnapshot: e.selfDelta, mfSnapshot: e.mfDelta,
                                  })}>MF確定</button>
                              </div>
                            )}
                            {e.reviewStatus && e.supplierNo && (
                              <button className="text-[10px] text-slate-500 hover:underline"
                                onClick={(ev) => {
                                  ev.stopPropagation()
                                  if (window.confirm('確認履歴を取り消しますか?')) {
                                    reviewDeleteMutation.mutate({
                                      entryType: 'amountMismatch',
                                      entryKey: String(e.supplierNo),
                                      transactionMonth: e.transactionMonth,
                                    })
                                  }
                                }}>取消</button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </CardContent></Card>
            </TabsContent>

            <TabsContent value="unmatched" className="mt-3">
              <Card><CardContent className="pt-4 overflow-x-auto">
                <p className="text-xs text-muted-foreground mb-2">
                  mf_account_master の「買掛金」sub_account に未登録の仕入先です。MF 側で補助科目登録するか、別 sub_account で運用中か確認してください。
                </p>
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b text-left">
                      <th className="py-2">仕入先コード</th>
                      <th className="py-2">仕入先名</th>
                      <th className="py-2">備考</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.unmatchedSuppliers.length === 0 && <tr><td colSpan={3} className="py-6 text-center text-muted-foreground">該当なし</td></tr>}
                    {report.unmatchedSuppliers.map((e, i) => (
                      <tr key={i} className="border-b hover:bg-slate-50 cursor-pointer"
                          onClick={() => gotoLedger(e.supplierNo)}>
                        <td className="py-2">{e.supplierCode}</td>
                        <td className="py-2 text-blue-600 hover:underline">{e.supplierName}</td>
                        <td className="py-2 text-xs text-muted-foreground">{e.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </CardContent></Card>
            </TabsContent>
          </Tabs>

          <p className="text-xs text-muted-foreground">
            row クリックで該当仕入先の買掛帳画面を新規タブで開きます。
          </p>
        </>
        )
      })()}
    </div>
  )
}

function SummaryTile({ label, count, amount, color }: {
  label: string; count: number; amount?: number; color: 'red' | 'amber' | 'slate'
}) {
  const textClass = color === 'red' ? 'text-red-700' : color === 'amber' ? 'text-amber-700' : 'text-slate-600'
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-xs text-muted-foreground">{label}</span>
      <div className="flex items-baseline gap-2">
        <span className={`font-semibold ${count > 0 ? textClass : ''}`}>{count} 件</span>
        {amount !== undefined && <span className="text-xs tabular-nums text-muted-foreground">\{amount.toLocaleString('ja-JP')}</span>}
      </div>
    </div>
  )
}

/** 整合性レポートのデフォルト開始月: toMonth の 6 ヶ月前。 */
function shortDefaultFromMonth(toMonthIso: string): string {
  const [y, m] = toMonthIso.split('-').map(Number)
  const d = new Date(y, m - 1 - 6, 20)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-20`
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
既存レビュー 2 本と D 関連設計書 6 本、対象コードを読んだ上で、Opus の既出指摘を避けて「業務モデルの前提」「会計上の信頼境界」「失敗モード」に絞ります。

## 追加指摘

### 1. MF を支払取崩の入力源にすると、突合が自己参照になる
- Severity: Critical
- カテゴリ: 設計選択
- 内容: `MfPaymentAggregator` により `payment_settled` を MF debit から上書きする案 A は、MF との整合性レポートの片側を MF で作る構造になっている。つまり「自社台帳が MF と合っているか」を検証したいのに、支払側は既に MF の数字を自社台帳へ注入している。これにより支払漏れ・二重支払・MF 側の誤仕訳が、差分ではなく自社台帳の正しい値として固定される可能性がある。
- 推奨アクション: `payment_settled_source = VERIFIED_AMOUNT | MF_DEBIT | MANUAL | BACKFILL` のような出所を保持し、整合性レポートでは MF 由来の自社値を別扱いにする。理想は支払事実の主系を SMILE/銀行/振込明細に置き、MF は照合先に留める。

### 2. 「画面の数字」「MF の数字」「請求書の数字」の優先順位が未定義
- Severity: Critical
- カテゴリ: ビジネス整合
- 内容: 買掛金一覧は請求書/SMILE/振込明細の検証額を扱い、整合性レポートは MF を基準に `MF_APPLY` で自社を上書きできる。どの画面で見た数字が会計上の正なのか、設計上の権威が分離されていない。経理担当者が請求書と MF に差がある場合、MF を正として押すべきか、請求書を正として MF を直すべきか判断できない。
- 推奨アクション: 金額ごとに source of truth を明文化する。例: 請求額は請求書/SMILE、支払実績は銀行/振込明細、会計反映結果は MF、という階層に分け、UI に「この数字の根拠」を出す。

### 3. 部分支払を supplier×月 合計でしか表現できない
- Severity: Critical
- カテゴリ: エッジケース
- 内容: 現行モデルは supplier×月×税率の集計値で、請求 100 万に対して 60 万だけ支払った場合の「どの請求・どの税率・どの仕入に 60 万を充当したか」を保持しない。`payment_settled` は月次 debit 合計を当月 change 比で按分するため、残 40 万の年齢や税率別残が推定値になる。買掛残高としては合って見えても、滞留債務・支払予定・税率別内訳の説明性が落ちる。
- 推奨アクション: 部分支払を扱うなら、最低限 `payable_settlement_allocation` のような支払充当テーブルを別に持つ。初版で集計モデルを継続する場合も、部分支払は「残高は概算、明細追跡不可」と UI/設計に明記する。

### 4. 税率別行と MF の集計単位が本質的に一致していない
- Severity: Major
- カテゴリ: 設計選択
- 内容: 自社は税率別行を持つが、MF の買掛金 debit は通常 supplier/sub_account 単位で、税率情報を持たない。現行は当月 change 比で税率別に配賦するが、支払は通常「前月以前の請求」に対するものであり、当月仕入の税率構成とは一致しない。結果として、税込総額は合っても税抜・消費税・税率別残の意味が薄くなる。
- 推奨アクション: MF debit 由来の `payment_settled_tax_excluded` は参考値扱いにするか、支払対象月/請求書の税率構成で配賦する。税率別残を監査対象にするなら、請求書単位の内訳を保持する設計へ寄せる。

### 5. MF 訂正仕訳・取消仕訳の反映タイミングが設計されていない
- Severity: Critical
- カテゴリ: エッジケース
- 内容: MF で過去月の訂正仕訳、取消、逆仕訳が入ると、`payment_settled` を保存済みの自社 summary と、現在の MF `/journals` から作る整合性レポートが別時点のデータになる。再取得・再集計すれば変わるが、どの月を再 backfill すべきか、いつの MF スナップショットで確定したかが残らない。会計締め後の訂正に対して、過去月の自社台帳が静かに書き換わる運用になり得る。
- 推奨アクション: MF 取得結果に `fetched_at`, `journal_hash`, `source_version` を持たせ、summary に反映した MF スナップショットを記録する。締め済月は自動上書きせず、訂正差分として別レビューに回すべき。

### 6. MF API 障害時の fallback が「正常な旧方式」と区別できない
- Severity: Major
- カテゴリ: エッジケース
- 内容: `MfPaymentAggregator` は MF client/token/journals 取得失敗時に空 Map を返し、呼び出し側は verified_amount ベースの値を維持する。これは可用性としては良いが、同じ月を後日再実行すると MF debit ベースに変わり、過去の残高が変動する。画面上も「この月は MF 取得失敗により fallback」という説明が残らない。
- 推奨アクション: fallback した月・supplier を明示的に記録する。バッチ結果にも `mfPaymentSource=FALLBACK` 件数を出し、UI では再取得推奨として表示する。

### 7. supplier の合併・分割・コード変更に耐える履歴モデルがない
- Severity: Critical
- カテゴリ: エッジケース
- 内容: MF 紐付けは現在の `payment_supplier_code` と `mf_account_master.search_key` を見ているため、過去月の supplier コード変更や支払先統合が起きると、履歴残高が現在の master で再解釈される。合併前の 2 supplier を合併後 1 supplier に寄せる、または分割するケースでは、過去の MF sub_account と自社 supplier の対応が一意でなくなる。これは買掛残高の監査説明で大きな問題になる。
- 推奨アクション: supplier-code/sub_account の対応に有効開始日・終了日を持たせる。過去月の再集計は「当時有効だった mapping」で行うべき。

### 8. ConsistencyReview の action が実務の解決パターンに足りない
- Severity: Major
- カテゴリ: 代替案
- 内容: `IGNORE` と `MF_APPLY` だけだと、部分適用、後日確認、supplier 紐付け修正、別 supplier への移管、MF 修正待ちを表現できない。実務では「60 万だけ適用」「この MF 仕訳は別 supplier に紐付け」「月次締め後に確認」などが頻出する。現状は `IGNORE` の note に意味を押し込むことになり、集計や監査で機械判定できない。
- 推奨アクション: `PARTIAL_APPLY`, `REVIEW_LATER`, `LINK_SUPPLIER`, `MF_FIX_PENDING`, `SPLIT_APPLY` を検討する。少なくとも `IGNORE` に reason enum を追加し、自由記述だけにしない。

### 9. 値引・返品・相殺仕訳を delta だけで扱うと原因分類を失う
- Severity: Major
- カテゴリ: エッジケース
- 内容: 月跨ぎの値引、返品、相殺は、買掛金の credit/debit としては delta に吸収できるが、業務上は「仕入減額」「支払」「相殺」「誤計上取消」で意味が違う。整合性レポートは `credit - debit` と `change - payment_settled` の比較に寄せているため、金額一致しても分類誤りを見逃す。MF の借方買掛金が支払ではなく返品相殺だった場合でも `payment_settled` と同じ扱いになり得る。
- 推奨アクション: MF branch の相手科目で debit の意味を分類する。買掛金 debit は `BANK_PAYMENT`, `PURCHASE_RETURN`, `DISCOUNT`, `OFFSET`, `REVERSAL` などに分け、支払取崩に使ってよい科目を限定する。

### 10. shop_no 拡張時に MF master と supplier code の衝突が起きる
- Severity: Major
- カテゴリ: エッジケース
- 内容: Opus は `shop_no=1` 固定自体を指摘しているが、より根本的には MF 補助科目 master が shop 次元を持っていない。将来 shop が増えた場合、同じ supplier_code が別 shop で別支払先を指す、または同じ MF sub_account を複数 shop で共有する可能性がある。現在の `search_key -> supplier` 解決は shop 分離の前提を持たない。
- 推奨アクション: MF account master に shop scope か company/book scope を持たせる。少なくとも `shop_no`, `mf_company_id`, `effective_from/to` を mapping のキーに含める。

### 11. 集計を Tasklet の保存済み snapshot として持つ設計は、説明責任に弱い
- Severity: Major
- カテゴリ: 代替案
- 内容: 現在の `t_accounts_payable_summary` は集計結果と検証結果と支払反映結果が同じテーブルに混在している。再集計・再検証・MF_APPLY・backfill が同じ行を上書きするため、最終値は残るが「どの入力から、どのバッチで、なぜこの値になったか」が追いにくい。会計システムとしては、集計 snapshot よりも元帳イベントと派生ビューの分離が向いている。
- 推奨アクション: 代替案として、`payable_ledger_event` に仕入・請求検証・支払・MF反映・調整をイベント保存し、月次 summary は materialized view または再生成可能な projection にする。短期対応なら summary に `calculation_run_id` と `source_hash` を追加する。

### 12. audit trail が「レビュー操作」中心で、金額変更全体を覆っていない
- Severity: Critical
- カテゴリ: ビジネス整合
- 内容: `t_consistency_review` は誰が review したかを持つが、手動検証、MF 出力切替、再集計、backfill、MF debit 上書き、SMILE 照合による自動調整の旧値/新値が統一的に追えない。経理監査では「誰が MF_APPLY したか」だけでなく、「いつのバッチがどの入力ファイル/API結果で金額を変えたか」が必要になる。現状はログ依存が多く、DB 上の永続監査証跡としては不足している。
- 推奨アクション: `finance_audit_log` を新設し、対象テーブル/PK/旧値/新値/source/action/user/run_id を JSONB で保存する。バッチも system user と run_id で監査ログを残す。

### 13. 「期末累積差が 0 なら解消済み」で月中差分を隠すのは危険
- Severity: Major
- カテゴリ: ビジネス整合
- 内容: 整合性レポート UI は `reconciledAtPeriodEnd` をデフォルト非表示にできるが、期末で差が解消していても、月中に誤計上と逆仕訳があった事実は監査上重要な場合がある。特に締め済月に誤仕訳が入り翌月訂正された場合、「期末で合っているから問題なし」とは言えない。期間 delta の異常と期末 balance の一致は別のシグナルとして扱うべき。
- 推奨アクション: 「期末解消済み」は非表示ではなく別ステータスにし、初期表示でも件数は残す。監査モードでは必ず表示する。

## 代替アーキテクチャ提案

- 短期: 現行 Tasklet 継続。ただし全 summary 更新に `calculation_run_id`, `source_type`, `source_fetched_at`, `fallback_reason` を追加する。
- 中期: `payable_ledger_event` を作り、仕入・検証・支払・調整・MF確認を immutable event として保存する。`t_accounts_payable_summary` は projection として再生成可能にする。
- 長期: MF 連携は「入力源」ではなく「外部会計帳簿との照合先」に戻し、支払実績は銀行/振込明細/SMILE 由来で確定する。MF 訂正は reconciliation event としてレビューに回す。

## Opus が見落としていた最重要 3 件

1. **MF debit を自社 payment_settled に入れることで、MF 突合が自己参照になること。**
2. **部分支払・税率別配賦・請求単位残高を supplier×月 集計だけでは説明できないこと。**
3. **監査証跡が review 操作に偏っており、バッチ・手動検証・MF/API 由来変更の旧値/新値を永続的に追えないこと。**
tokens used
156,682
既存レビュー 2 本と D 関連設計書 6 本、対象コードを読んだ上で、Opus の既出指摘を避けて「業務モデルの前提」「会計上の信頼境界」「失敗モード」に絞ります。

## 追加指摘

### 1. MF を支払取崩の入力源にすると、突合が自己参照になる
- Severity: Critical
- カテゴリ: 設計選択
- 内容: `MfPaymentAggregator` により `payment_settled` を MF debit から上書きする案 A は、MF との整合性レポートの片側を MF で作る構造になっている。つまり「自社台帳が MF と合っているか」を検証したいのに、支払側は既に MF の数字を自社台帳へ注入している。これにより支払漏れ・二重支払・MF 側の誤仕訳が、差分ではなく自社台帳の正しい値として固定される可能性がある。
- 推奨アクション: `payment_settled_source = VERIFIED_AMOUNT | MF_DEBIT | MANUAL | BACKFILL` のような出所を保持し、整合性レポートでは MF 由来の自社値を別扱いにする。理想は支払事実の主系を SMILE/銀行/振込明細に置き、MF は照合先に留める。

### 2. 「画面の数字」「MF の数字」「請求書の数字」の優先順位が未定義
- Severity: Critical
- カテゴリ: ビジネス整合
- 内容: 買掛金一覧は請求書/SMILE/振込明細の検証額を扱い、整合性レポートは MF を基準に `MF_APPLY` で自社を上書きできる。どの画面で見た数字が会計上の正なのか、設計上の権威が分離されていない。経理担当者が請求書と MF に差がある場合、MF を正として押すべきか、請求書を正として MF を直すべきか判断できない。
- 推奨アクション: 金額ごとに source of truth を明文化する。例: 請求額は請求書/SMILE、支払実績は銀行/振込明細、会計反映結果は MF、という階層に分け、UI に「この数字の根拠」を出す。

### 3. 部分支払を supplier×月 合計でしか表現できない
- Severity: Critical
- カテゴリ: エッジケース
- 内容: 現行モデルは supplier×月×税率の集計値で、請求 100 万に対して 60 万だけ支払った場合の「どの請求・どの税率・どの仕入に 60 万を充当したか」を保持しない。`payment_settled` は月次 debit 合計を当月 change 比で按分するため、残 40 万の年齢や税率別残が推定値になる。買掛残高としては合って見えても、滞留債務・支払予定・税率別内訳の説明性が落ちる。
- 推奨アクション: 部分支払を扱うなら、最低限 `payable_settlement_allocation` のような支払充当テーブルを別に持つ。初版で集計モデルを継続する場合も、部分支払は「残高は概算、明細追跡不可」と UI/設計に明記する。

### 4. 税率別行と MF の集計単位が本質的に一致していない
- Severity: Major
- カテゴリ: 設計選択
- 内容: 自社は税率別行を持つが、MF の買掛金 debit は通常 supplier/sub_account 単位で、税率情報を持たない。現行は当月 change 比で税率別に配賦するが、支払は通常「前月以前の請求」に対するものであり、当月仕入の税率構成とは一致しない。結果として、税込総額は合っても税抜・消費税・税率別残の意味が薄くなる。
- 推奨アクション: MF debit 由来の `payment_settled_tax_excluded` は参考値扱いにするか、支払対象月/請求書の税率構成で配賦する。税率別残を監査対象にするなら、請求書単位の内訳を保持する設計へ寄せる。

### 5. MF 訂正仕訳・取消仕訳の反映タイミングが設計されていない
- Severity: Critical
- カテゴリ: エッジケース
- 内容: MF で過去月の訂正仕訳、取消、逆仕訳が入ると、`payment_settled` を保存済みの自社 summary と、現在の MF `/journals` から作る整合性レポートが別時点のデータになる。再取得・再集計すれば変わるが、どの月を再 backfill すべきか、いつの MF スナップショットで確定したかが残らない。会計締め後の訂正に対して、過去月の自社台帳が静かに書き換わる運用になり得る。
- 推奨アクション: MF 取得結果に `fetched_at`, `journal_hash`, `source_version` を持たせ、summary に反映した MF スナップショットを記録する。締め済月は自動上書きせず、訂正差分として別レビューに回すべき。

### 6. MF API 障害時の fallback が「正常な旧方式」と区別できない
- Severity: Major
- カテゴリ: エッジケース
- 内容: `MfPaymentAggregator` は MF client/token/journals 取得失敗時に空 Map を返し、呼び出し側は verified_amount ベースの値を維持する。これは可用性としては良いが、同じ月を後日再実行すると MF debit ベースに変わり、過去の残高が変動する。画面上も「この月は MF 取得失敗により fallback」という説明が残らない。
- 推奨アクション: fallback した月・supplier を明示的に記録する。バッチ結果にも `mfPaymentSource=FALLBACK` 件数を出し、UI では再取得推奨として表示する。

### 7. supplier の合併・分割・コード変更に耐える履歴モデルがない
- Severity: Critical
- カテゴリ: エッジケース
- 内容: MF 紐付けは現在の `payment_supplier_code` と `mf_account_master.search_key` を見ているため、過去月の supplier コード変更や支払先統合が起きると、履歴残高が現在の master で再解釈される。合併前の 2 supplier を合併後 1 supplier に寄せる、または分割するケースでは、過去の MF sub_account と自社 supplier の対応が一意でなくなる。これは買掛残高の監査説明で大きな問題になる。
- 推奨アクション: supplier-code/sub_account の対応に有効開始日・終了日を持たせる。過去月の再集計は「当時有効だった mapping」で行うべき。

### 8. ConsistencyReview の action が実務の解決パターンに足りない
- Severity: Major
- カテゴリ: 代替案
- 内容: `IGNORE` と `MF_APPLY` だけだと、部分適用、後日確認、supplier 紐付け修正、別 supplier への移管、MF 修正待ちを表現できない。実務では「60 万だけ適用」「この MF 仕訳は別 supplier に紐付け」「月次締め後に確認」などが頻出する。現状は `IGNORE` の note に意味を押し込むことになり、集計や監査で機械判定できない。
- 推奨アクション: `PARTIAL_APPLY`, `REVIEW_LATER`, `LINK_SUPPLIER`, `MF_FIX_PENDING`, `SPLIT_APPLY` を検討する。少なくとも `IGNORE` に reason enum を追加し、自由記述だけにしない。

### 9. 値引・返品・相殺仕訳を delta だけで扱うと原因分類を失う
- Severity: Major
- カテゴリ: エッジケース
- 内容: 月跨ぎの値引、返品、相殺は、買掛金の credit/debit としては delta に吸収できるが、業務上は「仕入減額」「支払」「相殺」「誤計上取消」で意味が違う。整合性レポートは `credit - debit` と `change - payment_settled` の比較に寄せているため、金額一致しても分類誤りを見逃す。MF の借方買掛金が支払ではなく返品相殺だった場合でも `payment_settled` と同じ扱いになり得る。
- 推奨アクション: MF branch の相手科目で debit の意味を分類する。買掛金 debit は `BANK_PAYMENT`, `PURCHASE_RETURN`, `DISCOUNT`, `OFFSET`, `REVERSAL` などに分け、支払取崩に使ってよい科目を限定する。

### 10. shop_no 拡張時に MF master と supplier code の衝突が起きる
- Severity: Major
- カテゴリ: エッジケース
- 内容: Opus は `shop_no=1` 固定自体を指摘しているが、より根本的には MF 補助科目 master が shop 次元を持っていない。将来 shop が増えた場合、同じ supplier_code が別 shop で別支払先を指す、または同じ MF sub_account を複数 shop で共有する可能性がある。現在の `search_key -> supplier` 解決は shop 分離の前提を持たない。
- 推奨アクション: MF account master に shop scope か company/book scope を持たせる。少なくとも `shop_no`, `mf_company_id`, `effective_from/to` を mapping のキーに含める。

### 11. 集計を Tasklet の保存済み snapshot として持つ設計は、説明責任に弱い
- Severity: Major
- カテゴリ: 代替案
- 内容: 現在の `t_accounts_payable_summary` は集計結果と検証結果と支払反映結果が同じテーブルに混在している。再集計・再検証・MF_APPLY・backfill が同じ行を上書きするため、最終値は残るが「どの入力から、どのバッチで、なぜこの値になったか」が追いにくい。会計システムとしては、集計 snapshot よりも元帳イベントと派生ビューの分離が向いている。
- 推奨アクション: 代替案として、`payable_ledger_event` に仕入・請求検証・支払・MF反映・調整をイベント保存し、月次 summary は materialized view または再生成可能な projection にする。短期対応なら summary に `calculation_run_id` と `source_hash` を追加する。

### 12. audit trail が「レビュー操作」中心で、金額変更全体を覆っていない
- Severity: Critical
- カテゴリ: ビジネス整合
- 内容: `t_consistency_review` は誰が review したかを持つが、手動検証、MF 出力切替、再集計、backfill、MF debit 上書き、SMILE 照合による自動調整の旧値/新値が統一的に追えない。経理監査では「誰が MF_APPLY したか」だけでなく、「いつのバッチがどの入力ファイル/API結果で金額を変えたか」が必要になる。現状はログ依存が多く、DB 上の永続監査証跡としては不足している。
- 推奨アクション: `finance_audit_log` を新設し、対象テーブル/PK/旧値/新値/source/action/user/run_id を JSONB で保存する。バッチも system user と run_id で監査ログを残す。

### 13. 「期末累積差が 0 なら解消済み」で月中差分を隠すのは危険
- Severity: Major
- カテゴリ: ビジネス整合
- 内容: 整合性レポート UI は `reconciledAtPeriodEnd` をデフォルト非表示にできるが、期末で差が解消していても、月中に誤計上と逆仕訳があった事実は監査上重要な場合がある。特に締め済月に誤仕訳が入り翌月訂正された場合、「期末で合っているから問題なし」とは言えない。期間 delta の異常と期末 balance の一致は別のシグナルとして扱うべき。
- 推奨アクション: 「期末解消済み」は非表示ではなく別ステータスにし、初期表示でも件数は残す。監査モードでは必ず表示する。

## 代替アーキテクチャ提案

- 短期: 現行 Tasklet 継続。ただし全 summary 更新に `calculation_run_id`, `source_type`, `source_fetched_at`, `fallback_reason` を追加する。
- 中期: `payable_ledger_event` を作り、仕入・検証・支払・調整・MF確認を immutable event として保存する。`t_accounts_payable_summary` は projection として再生成可能にする。
- 長期: MF 連携は「入力源」ではなく「外部会計帳簿との照合先」に戻し、支払実績は銀行/振込明細/SMILE 由来で確定する。MF 訂正は reconciliation event としてレビューに回す。

## Opus が見落としていた最重要 3 件

1. **MF debit を自社 payment_settled に入れることで、MF 突合が自己参照になること。**
2. **部分支払・税率別配賦・請求単位残高を supplier×月 集計だけでは説明できないこと。**
3. **監査証跡が review 操作に偏っており、バッチ・手動検証・MF/API 由来変更の旧値/新値を永続的に追えないこと。**
