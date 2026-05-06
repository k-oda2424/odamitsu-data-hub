OpenAI Codex v0.128.0 (research preview)
--------
workdir: C:\project\odamitsu-data-hub
model: gpt-5.5
provider: openai
approval: never
sandbox: read-only
reasoning effort: none
reasoning summaries: none
session id: 019df0ad-c7a4-7481-8026-d6a181265d84
--------
user
あなたは経理システムと SaaS インテグレーションの上級レビュアーです。Cluster A (請求機能 - Invoice Management) の批判的レビューを実施してください。

## プロジェクト
小田光データ連携基盤 (`C:/project/odamitsu-data-hub`)
- Java 21 / Spring Boot 3.3.x / PostgreSQL 17 / Next.js 16
- SMILE 基幹システムから請求実績 Excel を取り込み `t_invoice` に UPSERT
- 入金グループ (m_partner_group) と紐付けて得意先別表示

## レビュー対象 (Cluster A)
**Backend** (主要):
- `backend/src/main/java/jp/co/oda32/domain/service/finance/InvoiceImportService.java`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/TInvoiceService.java`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/MPartnerGroupService.java`
- `backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java`
- Entity: TInvoice, MPartnerGroup
- Specification: TInvoiceSpecification
- DTO: InvoiceResponse, InvoiceImportResult, PartnerGroupRequest/Response, BulkPaymentDateRequest, PaymentDateUpdateRequest
- Controller: 請求関連の Controller (api/finance 配下)

**Frontend**:
- `frontend/components/pages/finance/invoices.tsx`
- `frontend/components/pages/finance/InvoiceImportDialog.tsx`
- `frontend/components/pages/finance/PartnerGroupDialog.tsx`

## 既存レビュー (重複回避)
- `claudedocs/design-review-invoice-management.md` (Critical 3 / Major 7 / Minor 8)
- `claudedocs/code-review-invoice-management.md` (Critical 4 / Major 6 / Minor 7)
- `claudedocs/design-invoice-management.md`

既出 (重複させない):
- Migration 欠落、IDOR (bulk-payment-date)、特殊得意先ハードコード (上様/イズミ/クリーンラボ)、virtual TInvoice
- useMemo(columns, []) 空依存バグ、Excel 内同一 partnerCode 二重適用、@NotNull で 400、取込エンドポイント shopNo IDOR

## 批判的レビューの観点
Opus はコード品質と既知バグを網羅した。**根本的な問い直し**を:

1. **設計選択の前提**
   - 「SMILE 請求実績 Excel を取り込む」という処理形態は本当に正しいか (SMILE → DB 直接連携や API 連携でない理由)
   - virtual TInvoice の概念 (「Excel に存在しないが DB に残す」) の業務上の妥当性
   - 「上様」「イズミ」「クリーンラボ」のハードコード判定が表すドメインモデルの欠落
   - partner_group (入金グループ) の粒度 (得意先 1:1 or 1:N) の設計選択

2. **代替アーキテクチャ**
   - Excel パーサーの責務分離 (取込/検証/結果集計)
   - virtual TInvoice を別テーブル (t_invoice_carryover 等) で管理する案
   - 入金グループを階層構造 (組織 > 部門 > 担当) で扱う案

3. **エッジケース / 失敗モード**
   - SMILE 側で過去請求が修正された場合 (再 Excel 取込で旧データが残る挙動)
   - 1 行でも不正があると全件ロールバック → 既存行の取り扱い
   - 同一得意先で同月複数請求 (中間請求/月末請求) のキー一意性
   - partner_group の member 移動 (A 組から B 組へ) 時の過去請求帰属
   - 入金日一括更新で意図せず確定済みの過去請求を更新するリスク

4. **業務整合 / 監査**
   - 「請求書の数字」「Excel の数字」「MF の数字」の権威階層 (Cluster D と整合とるべき)
   - 入金消込が「いつ誰が確定したか」を完全追跡できるか
   - 監査時に「この月のこの請求はなぜ存在するか/なぜ消えたか」を説明できるか
   - GDPR/個人情報保護: 得意先名・得意先コードの取扱

## 出力フォーマット
Markdown、日本語、簡潔に (max 500 行)。各指摘:
- Severity: Critical / Major / Minor
- カテゴリ: 設計選択 / 代替案 / エッジケース / 業務整合
- 内容 (3-5 文)
- 推奨アクション (1-2 文)

最後に「Opus が見落としていた最重要 3 件」を明記。


codex
using-superpowers スキルで進め方を確認しつつ、レビュー対象と既存レビューを読んで重複を避けます。今回はコードレビューなので実装変更はせず、現物ベースで設計・業務リスクに絞ります。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path C:\\Users\\kazuki\\.agents\\skills\\using-superpowers\\SKILL.md" in C:\project\odamitsu-data-hub
 succeeded in 1544ms:
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

codex
requesting-code-review と PostgreSQL 観点を必要な範囲だけ使います。請求制度そのものの税務相談ではなく、システム設計レビューとして扱います。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path C:\\Users\\kazuki\\.agents\\skills\\requesting-code-review\\SKILL.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path C:\\Users\\kazuki\\.agents\\skills\\postgresql-code-review\\SKILL.md" in C:\project\odamitsu-data-hub
 succeeded in 1522ms:
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

 succeeded in 1565ms:
---
name: requesting-code-review
description: Use when completing tasks, implementing major features, or before merging to verify work meets requirements
---

# Requesting Code Review

Dispatch superpowers:code-reviewer subagent to catch issues before they cascade. The reviewer gets precisely crafted context for evaluation ? never your session's history. This keeps the reviewer focused on the work product, not your thought process, and preserves your own context for continued work.

**Core principle:** Review early, review often.

## When to Request Review

**Mandatory:**
- After each task in subagent-driven development
- After completing major feature
- Before merge to main

**Optional but valuable:**
- When stuck (fresh perspective)
- Before refactoring (baseline check)
- After fixing complex bug

## How to Request

**1. Get git SHAs:**
```bash
BASE_SHA=$(git rev-parse HEAD~1)  # or origin/main
HEAD_SHA=$(git rev-parse HEAD)
```

**2. Dispatch code-reviewer subagent:**

Use Task tool with superpowers:code-reviewer type, fill template at `code-reviewer.md`

**Placeholders:**
- `{WHAT_WAS_IMPLEMENTED}` - What you just built
- `{PLAN_OR_REQUIREMENTS}` - What it should do
- `{BASE_SHA}` - Starting commit
- `{HEAD_SHA}` - Ending commit
- `{DESCRIPTION}` - Brief summary

**3. Act on feedback:**
- Fix Critical issues immediately
- Fix Important issues before proceeding
- Note Minor issues for later
- Push back if reviewer is wrong (with reasoning)

## Example

```
[Just completed Task 2: Add verification function]

You: Let me request code review before proceeding.

BASE_SHA=$(git log --oneline | grep "Task 1" | head -1 | awk '{print $1}')
HEAD_SHA=$(git rev-parse HEAD)

[Dispatch superpowers:code-reviewer subagent]
  WHAT_WAS_IMPLEMENTED: Verification and repair functions for conversation index
  PLAN_OR_REQUIREMENTS: Task 2 from docs/superpowers/plans/deployment-plan.md
  BASE_SHA: a7981ec
  HEAD_SHA: 3df7661
  DESCRIPTION: Added verifyIndex() and repairIndex() with 4 issue types

[Subagent returns]:
  Strengths: Clean architecture, real tests
  Issues:
    Important: Missing progress indicators
    Minor: Magic number (100) for reporting interval
  Assessment: Ready to proceed

You: [Fix progress indicators]
[Continue to Task 3]
```

## Integration with Workflows

**Subagent-Driven Development:**
- Review after EACH task
- Catch issues before they compound
- Fix before moving to next task

**Executing Plans:**
- Review after each batch (3 tasks)
- Get feedback, apply, continue

**Ad-Hoc Development:**
- Review before merge
- Review when stuck

## Red Flags

**Never:**
- Skip review because "it's simple"
- Ignore Critical issues
- Proceed with unfixed Important issues
- Argue with valid technical feedback

**If reviewer wrong:**
- Push back with technical reasoning
- Show code/tests that prove it works
- Request clarification

See template at: requesting-code-review/code-reviewer.md
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
まず既存レビューの指摘リストを確認して、同じ論点を避けます。その後、実装から「設計上そう振る舞うしかない」箇所を拾います。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path claudedocs\\design-review-invoice-management.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path claudedocs\\code-review-invoice-management.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path claudedocs\\design-invoice-management.md" in C:\project\odamitsu-data-hub
 succeeded in 1648ms:
# 設計レビュー: 請求機能 (Cluster A)

レビュー日: 2026-05-04
対象設計書: `claudedocs/design-invoice-management.md` (Phase 1 で生成)
対象ブランチ: `refactor/code-review-fixes`
レビュアー: Opus サブエージェント

---

## サマリー

- 総指摘件数: **Critical 3 / Major 7 / Minor 8**
- 承認状態: **Needs Revision**

最も重大な点は、

1. `t_invoice` / `m_partner_group` / `m_partner_group_member` の Flyway マイグレーションが リポジトリ内に存在しない（V001?V029 のいずれにも DDL 無し）。`spring.jpa.hibernate.ddl-auto=none` のためクリーン環境ではアプリ起動後に SELECT/UPSERT が即落ちる。prod は `validate` 設定なので、既存 stock-app 由来の手動 DDL に依存している＝再現性ゼロ。
2. 入金日一括更新エンドポイントに `shopNo` の権限境界が一切無く、第1事業部ユーザーが第2事業部の `invoiceId` を任意に POST して入金消込を破壊できる（IDOR）。同じく `partner-groups` の CRUD も `shopNo` の正当性を検証していない。
3. UPSERT が `(shopNo, closingDate)` で既存行を全件 SELECT した後、Excel に存在しなくなった既存行を `del_flg` で消すロジックも履歴も残さない（再取込しても残骸が残る）。同時にトランザクション内で得意先名 1 件でも非数値だと全行ロールバックする（現場運用で 1 セルの汚れで全面停止するリスク）。

---

## Critical 指摘

### C-1: `t_invoice` / `m_partner_group` の Flyway マイグレーションが欠落

- **箇所**: `backend/src/main/resources/db/migration/` 配下 (`V001`?`V029`)、`backend/src/main/resources/config/application.yml:7` (`ddl-auto: none`)、`application-prod.yml:16` (`ddl-auto: validate`)
- **問題**: 設計書 §3 (`design-invoice-management.md:50-90`) に記載のある `t_invoice`, `m_partner_group`, `m_partner_group_member` の `CREATE TABLE` がリポジトリ全体を grep しても存在しない（NFKC インデックス追加 `V005__create_nfkc_indexes.sql:32-34` で参照されているのみ）。`ddl-auto=none` のため、新規環境でアプリ起動 → `/finance/invoices` を叩くと SQL レベルで失敗する。prod は `validate` のため、Entity に `nullable=false` を付けても DDL に同制約が無ければ起動時バリデーション失敗、付けなくても schema drift が検出されない。
- **影響**: クリーン環境再構築の手順が破綻。disaster recovery / 開発者オンボーディング不能。`@UniqueConstraint(columnNames = {"partner_code", "closing_date", "shop_no"})` (`TInvoice.java:22-24`) も DDL に無ければ UPSERT の冪等性を担保できない（並行取込で重複挿入が発生する）。
- **修正案**:
  1. `V030__create_t_invoice_and_m_partner_group.sql` を新規追加し、`TInvoice` / `MPartnerGroup` / `m_partner_group_member` の DDL とユニーク制約・FK・インデックスを正規化。
  2. 既存 prod は `flyway.baseline-version` を引き上げて該当マイグレーションをスキップ（`application.yml:24` のコメント方針に倣う）。
  3. 設計書 §3 の各テーブルに「DDL: `Vxxx__*.sql`」の参照リンクを追記。

### C-2: 入金日一括更新で `shopNo` 権限境界が未チェック (IDOR)

- **箇所**: `backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java:464-475`、`PUT /api/v1/finance/invoices/bulk-payment-date`
- **問題**: `BulkPaymentDateRequest#invoiceIds` を `tInvoiceService.findByIds(...)` で取得し、何の検証もせず `paymentDate` を上書きする。`@PreAuthorize("isAuthenticated()")` のみで shop / role の検証無し。第2事業部ユーザーが第1事業部の `invoiceId` を含めても、`InvoiceImportService` 以外の経路で `LoginUserUtil.resolveEffectiveShopNo` (`LoginUserUtil.java:28-38`) のような権限正規化が一切ない。
- **影響**: 異事業部ユーザーが他事業部の入金消込を破壊。経理データの IDOR (Insecure Direct Object Reference)。
- **修正案**:
  1. 一括更新の前に `LoginUserUtil.resolveEffectiveShopNo` で許容 `shopNo` を取得し、`invoices.stream().filter(inv -> permittedShopNo == null || permittedShopNo.equals(inv.getShopNo()))` で絞る。フィルタで除外した分は 403 ではなく `skippedCount` として返却して UX を保つ。
  2. 同様の権限ガードを `PUT /invoices/{invoiceId}/payment-date` (`FinanceController.java:434-445`) と `partner-groups` CRUD (`L479-503`) にも適用。
  3. partner-group の `shopNo` は `PartnerGroupRequest` の入力値ではなく、サーバ側でログインユーザの shopNo を強制（admin のみリクエスト値を許容）するのが安全。

### C-3: UPSERT に「Excel から消えた既存行」の処理規約が無い & 1 セル不正で全行ロールバック

- **箇所**: `InvoiceImportService.java:138-188` (Phase 2 UPSERT)、`L237-246` (`convertPartnerCode` で `NumberFormatException` → `IllegalArgumentException`)
- **問題**:
  1. `(shopNo, closingDate)` で既存行を SELECT し、Excel に存在する `partnerCode` のみ更新。Excel から外れた既存行（取消・誤登録）は **そのまま残る**。`del_flg` カラムも持たないため復活させる手段も無い (`design-invoice-management.md:74-77` でも明記)。再取込でデータが整合しなくなる。
  2. `@Transactional` (`L40`) なので、A 列に非数値 1 行が混入しただけで `convertPartnerCode` が `IllegalArgumentException` を投げ全件ロールバック → 「300 行のうち 1 行だけ汚い」場面で取込ゼロになる。
  3. その 1 行のスキップ判断（continue / break / abort）が DTO `InvoiceImportResult.errors` に詰める設計が現状空 (`InvoiceImportService.java:185`)。
- **影響**: SMILE 側の運用ミス 1 件で当月締の取込が完全停止する。再取込が冪等でない（取消行が残る）。
- **修正案**:
  1. パース異常は行単位で `errors.add("row=N, code=...")` に積み、最後に「処理対象 0 件 ⇒ 例外」「処理対象 > 0 件 ⇒ 部分成功」で返す。設計書 §5.1 / §8 T1 を更新。
  2. Excel から消えた既存行の取り扱いを「警告ログ + UI に `removedFromSourceCount` 表示」「もしくは別フラグで取消マーク」のいずれにするかを業務判断で確定し、設計書 §2 スコープに追記。今のままでは「請求一覧に表示されている行が SMILE 実態と乖離する」という運用バグになる。
  3. 同じファイルを 2 回取込しても `payment_date` が保持され、他列も idempotent であることをテストで保証 (`InvoiceImportServiceTest:234-275` の発想を `re-import twice` ケースまで拡張)。

---

## Major 指摘

### M-1: `MPartnerGroup#partnerCodes` を `EAGER` で持つ N+1 リスク

- **箇所**: `MPartnerGroup.java:31-38`、`MPartnerGroupService.java:19-23`
- **問題**: `@ElementCollection(fetch = FetchType.EAGER)` でグループ × member 子テーブルを EAGER 取得。`findByShopNoOrderByGroupNameAsc` がグループ件数分のサブ SELECT を発行する典型的 N+1。
- **影響**: グループ件数 100、member 平均 20 件のとき 100+1 クエリ。dev では実害が出にくいが prod の経理締日に集中アクセスすると詰まる。
- **修正案**: `LAZY` + `findAll` 後に `JOIN FETCH` で 1 クエリ化、または DTO Projection (`@Query` でグループ id のリスト → 子テーブルを別 SELECT 1 回 + Java 側 group by) に切替。設計書 §7-7 の「N+1 候補」TODO を実施に移す。

### M-2: `partner-groups` 削除時に「現在グループに紐付いている請求書がある」整合性チェック無し

- **箇所**: `MPartnerGroupService.java:50-56`
- **問題**: 物理削除のみ。子テーブル `m_partner_group_member` は `@ElementCollection` の cascade で消えるが、その削除が「経理担当が選択中の状態だった」ケースの UX 復旧策が無い。さらに、グループ削除と一括入金更新が並走したらどう振る舞うかが未定義。
- **影響**: 監査時に「どのグループに対して入金記録したか」が追跡不能（履歴が残らない）。
- **修正案**:
  1. 削除を論理削除 (`del_flg`) に変更、もしくは削除イベントを `audit_log` テーブルに記録。
  2. 設計書 §6.3 に「削除中に他ユーザが当該グループで一括反映を実行した場合の整合性」明記。最小実装としては `findByShopNo` で `del_flg='0'` を条件に追加。

### M-3: `TInvoice` に監査列が無く誰がいつ入金日を更新したか追跡不能

- **箇所**: `TInvoice.java:25-67`、`design-invoice-management.md:76` (「監査フィールドは持っていない」と明記)
- **問題**: `payment_date` は経理処理の最重要属性であり「いつ・誰が・どの値から」変更したかを残す要件は経理機能として必須。`add_user_no` / `modify_user_no` / `add_date_time` / `modify_date_time` も無い。
- **影響**: 入金消込の証跡欠如。MEMORY.md に既に「軸 F 監査証跡基盤」が保留タスクとして登録されているとおり、買掛側と同じ問題を売上請求側でも内包する。
- **修正案**:
  1. `t_invoice_payment_date_history` 子テーブル (invoice_id, old_payment_date, new_payment_date, changed_at, changed_by_user_no) を追加し、`PUT /invoices/{id}/payment-date` と一括更新エンドポイントから INSERT。
  2. 設計書 §7-9 の「監査列なし」を Critical 級の課題として §8 に格上げし、軸 F の対象に含める。

### M-4: `InvoiceVerifier` の特殊得意先がハードコード（マスタ化必須）

- **箇所**: `InvoiceVerifier.java:41-43` (`QUARTERLY_BILLING_PARTNER_CODE="000231"`, `CLEAN_LAB_PARTNER_CODE="301491"`, `JOSAMA_PARTNER_CODE="999999"`)、`L282` (四半期特殊月 `2/5/8/11`)
- **問題**: 法人マスタの追加・移動・統合（B-CART 事業部統合方針 = 2026-04-21 のような業務イベント）でロジック側に手が入る構造。設計書 §7-6 / §8 T11 でも認知済だが対策計画が空欄。
- **影響**: 業務側のマスタ追加に追従できず、例外的得意先が増えるたびに本番デプロイが必須。
- **修正案**:
  1. `m_invoice_special_rule (partner_code, shop_force, billing_cycle, quarterly_months)` のような「特殊扱いマスタ」を新設し、コード定数を排除。
  2. 上様判定はマスタの `is_walk_in` flag で表現。
  3. CLI ツールから JSON で投入できる initial-load を整備。
  4. これが大きい場合の最小スコープとして「定数を `application.yml` の `batch.invoice.*` に逃がす」だけでも可。設計書 §8 に実装計画として明記。

### M-5: `findQuarterlyInvoice` の virtual `TInvoice` が `invoiceId` 不整合を生む

- **箇所**: `InvoiceVerifier.java:234-266` (とくに `L257-263`)
- **問題**: 当月15日締め + 前月末締めを合算して virtual `TInvoice` を作る際、`invoiceId` を「最初の」、`closingDate` を「最後の」請求書から取り、`netSalesIncludingTax` だけ合算。後段の `applyMatched` (`L351`) で `summary.invoiceNo = invoice.invoiceId` として保存されるが、表示上の `closingDate` と保存される `invoiceNo` が別レコード由来。監査時に「請求書 ID から該当請求書を引いてきても締日が一致しない」状態になる。
- **影響**: 売掛金画面 → 請求書ジャンプの整合性破綻。設計書 §8 T5 に既出だが解決方針未記載。
- **修正案**:
  1. virtual ではなく、`t_invoice` 側に「四半期合算の親レコード」を生成する (`m_invoice_special_rule` と連動)。
  2. もしくは `summary.invoiceNo` に「合算の代表 ID」と明示するメタ列 `is_quarterly=true` を追加し、UI 側で「合算」と表示。

### M-6: `TInvoiceService` のデッドコードと重複メソッド

- **箇所**: `TInvoiceService.java:31-33` (`getAllInvoices`, Controller 未使用)、`L52-66` (`saveInvoice` と `insert` が完全同義)、`L133-135` (`findBySpecification` 未使用)
- **問題**: 設計書 §8 T3 / T4 で言及済。重複/未使用が残ると「どちらを使うべきか」の判断ノイズが増え、変更時の影響範囲解析が膨らむ。`saveInvoice` と `insert` のどちらがいつ使われるかドキュメントなし。
- **影響**: コードレビュー負荷増、リファクタ時の歪み。
- **修正案**:
  1. `getAllInvoices` / `findBySpecification` 削除。
  2. `insert` を削除し `saveInvoice` に統一（あるいは `save` に rename）。
  3. PR 単位で「どれが消されたか」を `MEMORY.md` の `next-session-tasks.md` に追記。

### M-7: `closingDate` を `String` で持つ運用上の罠

- **箇所**: `TInvoice.java:38-39`、`InvoiceImportService.java:195-229` (parser)、`InvoiceVerifier.java:220-228` (`formatClosingDateForSearch`)、設計書 §7-1, §8 T12
- **問題**: 「末」漢字リテラル + `YYYY/MM/DD` の混在。`/finance/invoices` の `closingDate` 検索が `likePrefixNormalized` (`TInvoiceSpecification.java:20-22`) のため、`yyyy/MM` での月絞り込みは出来るが、範囲（from-to 月）/ 直近 N ヶ月といった検索ができない。比較演算は事実上不可能。`InvoiceVerifier#formatClosingDateForSearch` が `YYYY/MM/末` か `YYYY/MM/DD` のいずれかを生成するため、文字列マッチがズレた瞬間（例: cutoffCode=0 が `MONTH_END` ではない別 PaymentType に分類された場合）に必ず NotFound。
- **影響**: 経理機能としての検索性が低い。マイグレーション時の文字コード事故 (`末` がデータ中で全角 / 漢字混在 など) でサイレント不一致。
- **修正案**:
  1. 中期: `closing_date` を `LocalDate` + `is_month_end:boolean` に分割し、文字列解釈ロジックを排除。
  2. 短期 (互換): DB 制約 `CHECK (closing_date ~ '^\d{4}/\d{2}/(末|\d{2})$')` を追加し、汚いデータが入らないようガード。設計書 §8 T12 に「短期 / 中期」の二段計画として明記。

---

## Minor 指摘

### m-1: 入金日一括更新が WARN ログだけで部分成功を許容している

- **箇所**: `FinanceController.java:464-475`
- **問題**: 要求 ID 中に存在しないものがあっても 200 で返却 (`L467-470`)、レスポンスに `notFoundIds` を含めない。
- **修正案**: レスポンスに `requestedCount`, `updatedCount`, `notFoundIds` を含め、フロントで toast 上に「N 件は反映できませんでした」を出す。

### m-2: `MPartnerGroupService#findByShopNo(null)` がクライアント任せに全件返す

- **箇所**: `MPartnerGroupService.java:19-23`
- **問題**: `shopNo == null` で `repository.findAll()` を呼ぶ。フロントは `effectiveShopNo` を必ず付けて呼ぶため (`invoices.tsx:136`) 通常は到達しないが、API 直叩きで「全店舗のグループ」を漏洩できる経路。
- **修正案**: `LoginUserUtil.resolveEffectiveShopNo(shopNo)` で正規化し、admin のみ全件可。

### m-3: ファイル名による shop_no 推定の脆弱性

- **箇所**: `InvoiceImportService.java:54-57`、`InvoiceImportDialog.tsx:71` (UI 説明文)
- **問題**: ファイル名に「松山」が含まれるかで shop_no を決定。命名規約変更で誤判定。設計書 §7-2 で既出だが具体策無し。
- **修正案**: UI に「事業部選択 (第1 / 第2)」プルダウンを必須項目として追加し、`shopNo` パラメータを常時送信。ファイル名判定はあくまで「省略時のヒント」とする (`api spec` も明確化)。

### m-4: `TInvoice.shopNo` が JPA 上 NULL 許容のままユニーク制約参加

- **箇所**: `TInvoice.java:62-63`、設計書 §8 T9
- **問題**: `@Column(name = "shop_no")` のみ。設計書も「実態 NOT NULL」と注記しつつ DDL 確認 TODO のまま。NULL を含むタプルはユニーク扱いされない (PostgreSQL のデフォルト挙動)。
- **修正案**: C-1 と一緒に `nullable = false` を Entity に追加 + DDL に `NOT NULL` を明記。

### m-5: `getCellBigDecimal` の `STRING` ケースで `NumberFormatException` を握りつぶし `0` を返す

- **箇所**: `InvoiceImportService.java:285-291`
- **問題**: 文字列セルがパースできない場合 `BigDecimal.ZERO` にフォールバック (silent)。`null` ではなく `0` で保存され、UI で「金額入力なし」と「0 円」が区別できない。
- **修正案**: `errors` リストに「row=N, column=K, value=...」を積む。設計書 §5.3 に明示。

### m-6: `InvoiceResponse` のシリアライズ時、`closingDate` が `String` のままフロント `Invoice.closingDate: string` と一致するが、型整合の保証コメントなし

- **箇所**: `InvoiceResponse.java:15`、`invoices.tsx:31`、設計書 §8 T6, T14
- **問題**: 命名と型整合は OK だが「`closingDate` が `String` であり LocalDate ではない」ことを setter / DTO の Javadoc に明示しないと将来 LocalDate 化時にフロントが壊れる。
- **修正案**: `InvoiceResponse#closingDate` の Javadoc に「YYYY/MM/末 or YYYY/MM/DD のフォーマット文字列。LocalDate 化は後続フェーズ」を明記。

### m-7: `@Transactional` 設定が読み専用に最適化されていない

- **箇所**: `TInvoiceService.java:31`, `L42`, `L100`, `L110`、`L114-117` のみ `readOnly = true`
- **問題**: 検索系メソッドの大半に `@Transactional(readOnly = true)` が無い。Hibernate のセッションで dirty check の不要なオーバーヘッド。
- **修正案**: 検索系には `@Transactional(readOnly = true)` を付与。クラスレベルに `@Transactional(readOnly = true)` を置き書き込み系のみ `@Transactional` で上書きするのが Spring 標準。

### m-8: フロントの `useMemo(columns, [])` が ESLint 抑止で副作用リスク

- **箇所**: `invoices.tsx:248-301`
- **問題**: `selectedIds` を依存に含めず、`SelectCell` 経由のクロージャで参照。`useMemo` の依存配列が空のため、`handlePaymentDateChange` のリファレンスが変わっても columns は再生成されず、内部のミューテーションが入った瞬間に表示が古くなる潜在バグ。設計書 §8 T7 で要検証。
- **修正案**: `handlePaymentDateChange` を `useCallback` で確定参照化 (済) し、columns 依存配列に `selectedIds` ではなく安定参照のみ含める or `Column` 定義を関数外に出して props としてだけ受ける構造にする。

---

## 設計書 vs 実装の乖離

| # | 観点 | 設計書記述 | 実装の実態 | 対応 |
|---|------|-----------|-----------|------|
| D-1 | DDL の所在 | §3.1?§3.2 でテーブル定義あり、参照ドキュメントとして `V005__create_nfkc_indexes.sql` のみ列挙 | DDL 本体が **どのマイグレーションにも無い** (Critical C-1) | C-1 修正 + 設計書に DDL ファイルパス追記 |
| D-2 | API 認可 | §4 共通プレフィックス `@PreAuthorize("isAuthenticated()")`、ロール制限なしと明記 (§7-8) | 同左 ＋ ロール制限が不在 (Critical C-2) | 認可方針を §4 ヘッダに明示し admin / shop ユーザの操作差を表化 |
| D-3 | 取込の冪等性 | §5.1 「all-or-nothing」と明記、`payment_date` 保持にも触れる | 行単位スキップ／部分成功の規約が欠落 (Critical C-3) | §5.1 に「行エラー時の挙動」「Excel 削除行の扱い」を追記 |
| D-4 | partner-group `shopNo` の信頼源 | §4.5 でリクエスト DTO に `shopNo` (`@NotNull`) | サーバ側で `LoginUserUtil` 経由の正規化なし (Critical C-2 / Minor m-2) | DTO の `shopNo` は admin のみ受理、非 admin はサーバ側で上書き |
| D-5 | `MPartnerGroup` の partner-code 重複 | 言及なし | `partnerCodes` リストに同一コードが入っても DB / Service 側で重複排除しない (`MPartnerGroupService.java:35-36, 45-46`) | §3.2 に「重複入力は許容しない / DB ユニーク」を追記し Service で `Set` 化 |
| D-6 | `getCellBigDecimal` のフォールバック | §5.3 「STRING / NUMERIC / FORMULA 全対応」 | 失敗時 `0` フォールバックに踏み込んでいない (Minor m-5) | §5.3 に「失敗時は 0 ではなく errors に記録」と明記 |
| D-7 | `findBySpecification` 経路 | §4.1「ソート: closingDate 降順」「`getAllInvoices()` 残存」 | `findBySpecification` (`TInvoiceService.java:133`) は Controller 未使用 (Major M-6) | 設計書 §8 T4 を Major 級と再分類、削除計画を明記 |
| D-8 | virtual TInvoice の意味 | §5.4 で「四半期特殊」「virtual TInvoice を生成」 | `invoiceId` / `closingDate` の不整合 (Major M-5) | §5.4 に「virtual の限界 = invoiceId は最古、closingDate は最新」「監査時の参照方法」明記 |

---

## レビューチェックリスト結果

### Spring Boot 観点

| 項目 | 結果 | コメント |
|------|------|---------|
| Layer 違反 (Controller に business logic) | OK | UPSERT は Service に閉じる (`InvoiceImportService.java:138-173`) |
| `@Transactional` 範囲 | NG | 検索系に readOnly 未付与 (m-7)、`InvoiceImportService#importFromExcel` が all-or-nothing (C-3 連動) |
| N+1 | NG | `MPartnerGroup` `EAGER` (M-1) |
| DI (`@RequiredArgsConstructor` / コンストラクタ injection) | OK | 全 Service / Controller |
| DTO 変換 (`from(entity)` factory) | OK | `InvoiceResponse.from`, `PartnerGroupResponse.from` |
| バリデーション (`@Valid` + Bean Validation) | 一部 NG | `BulkPaymentDateRequest`, `PaymentDateUpdateRequest`, `PartnerGroupRequest` には付与済 / `listInvoices` の `closingDate` フォーマット validation 無し |
| Migration 安全性 | NG | C-1 (`t_invoice` DDL 欠落) |
| 例外ハンドリング | 改善余地 | `InvoiceImportService` 内の `IllegalArgumentException` を Controller で 400 に変換 (`FinanceController.java:454-456`) は OK だが、行単位エラーが errors に乗らない |

### 請求機能固有観点

| 項目 | 結果 | コメント |
|------|------|---------|
| Excel 取込の異常系（空セル、想定外フォーマット、文字化け） | 部分 NG | `getCellBigDecimal` の silent zero fallback (m-5)、行単位エラー集約なし (C-3) |
| UPSERT 冪等性 (同 Excel 再取込) | 部分 NG | 既存行 `payment_date` 保持はテスト済 (`InvoiceImportServiceTest:234-275`) だが「Excel から消えた行」処理が未定義 (C-3) |
| 特殊得意先処理マスタ化 | NG | ハードコード (M-4) |
| 入金日一括更新の権限 | NG | `shopNo` ガード無し (C-2) |
| partner_group 削除の整合性 | 部分 NG | 物理削除 + 履歴なし (M-2) |
| virtual TInvoice の監査 | NG | invoiceId / closingDate 不整合 (M-5) |
| 認可 (admin only / shop user) | NG | 全エンドポイント `isAuthenticated()` のみ (C-2 / m-2) |
| 監査列 (誰が入金日更新したか) | NG | 監査列なし (M-3) |

### コード品質

| 項目 | 結果 | コメント |
|------|------|---------|
| デッドコード | NG | `getAllInvoices`, `findBySpecification`, `insert` (M-6) |
| 命名 | OK | DTO / Entity / Service とも統一 |
| ログ出力 | 改善余地 | スキップ理由 `log.debug` (`InvoiceImportService.java:106, 114`) は INFO で残した方が運用追跡しやすい |
| テスト | 部分 OK | `InvoiceImportServiceTest` で 16 ケース、ただし `InvoiceVerifier` の 999999 / 000231 / 301491 のテストは未確認 (§8 T10 のとおり) |
| 設定外出し | NG | 特殊得意先コードと四半期月をハードコード (M-4) |

---

## 推奨アクション順序

1. **C-1**: `V030__create_t_invoice_and_m_partner_group.sql` を最優先で追加（マイグレーション欠落は disaster recovery を阻害）。
2. **C-2**: `bulk-payment-date` / `payment-date` / `partner-groups` の shopNo ガードを追加。E2E テストで他事業部 `invoiceId` を含めて 403 / skip となることを確認。
3. **C-3**: 行単位エラー集約 + Excel 削除行の扱い決定 + 設計書 §5.1 / §8 T1 更新。
4. **M-3**: 入金日変更履歴テーブル追加（軸 F に組み込み、買掛側と同一基盤で実装）。
5. **M-4 / M-5**: 特殊得意先マスタ化 + virtual `TInvoice` 設計再考。
6. **M-1 / M-6 / M-7 / m-1?m-8**: コード品質改善 PR としてまとめてレビュー。
7. 設計書側の TODO (§8 T1?T14) を上記実装と同期して closeout / 残課題をリラベル。
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1786ms:
# コードレビュー: 請求機能 (Cluster A)

レビュー日: 2026-05-04
対象: `InvoiceImportService` / `TInvoiceService` / `MPartnerGroupService` / `InvoiceVerifier` / `TInvoice` / `MPartnerGroup` / `TInvoiceSpecification` / `TInvoiceRepository` / `MPartnerGroupRepository` / `InvoiceResponse` / `InvoiceImportResult` / `PartnerGroupRequest` / `PartnerGroupResponse` / `BulkPaymentDateRequest` / `PaymentDateUpdateRequest` / `FinanceController` 該当箇所 / `frontend/components/pages/finance/invoices.tsx` / `InvoiceImportDialog.tsx` / `PartnerGroupDialog.tsx`
レビュアー: Opus サブエージェント
前提: 設計レビュー (`claudedocs/design-review-invoice-management.md`) で既出の Critical 3 / Major 7 / Minor 8 は再掲しない。コード固有の問題に限定する。

---

## サマリー

- 総指摘件数: **Blocker 0 / Critical 4 / Major 6 / Minor 7**
- マージ可否: **Block** (Critical-1 = stale closure による選択トグル不動作 / Critical-2 = 入金日クリア時の 400 エラー / Critical-3 = 取込時の shopNo 認可欠如 / Critical-4 = Excel 内重複 partnerCode で更新行が二重カウント＋取込結果不整合)

特に重大な新規発見:

1. **C-N1 (Critical)**: `invoices.tsx:248-301` の `useMemo(columns, [])` が **本当に空依存** で固定されており、`SelectCell selectedIds={selectedIds}` の `selectedIds` 参照が初回レンダー時の空 Set に張り付く。チェックボックスを ON にしても見た目が更新されない (実 state は更新されるが表示が乖離)。eslint-disable コメントの解釈が誤り。
2. **C-N2 (Critical)**: `PaymentDateCell` の `onBlur` が空文字列を `paymentDate: '' || null = null` に変換して送るが、`PaymentDateUpdateRequest.paymentDate` は `@NotNull` のため 400 になる。「入金日のクリア」操作が UI 上は操作可能なのに必ず失敗する。
3. **C-N3 (Critical)**: `POST /finance/invoices/import` (`FinanceController.java:447-462`) が `shopNo` を `LoginUserUtil` で正規化していないため、第1事業部ユーザーが `shopNo=2` を送って第2事業部の請求実績を上書き取込できる。設計レビュー C-2 が触れたのは入金日 / partner-group 系のみで、**取込側の IDOR は未指摘**。

---

## Critical 指摘 (新規)

### C-N1: `invoices.tsx:248-301` の `useMemo(columns, [])` が stale closure で `selectedIds` を初回 Set に張り付かせる

- **箇所**: `frontend/components/pages/finance/invoices.tsx:248-301`
- **問題**: `useMemo(() => [...], [])` の配列リテラル中で生成される `render: (item) => <SelectCell selectedIds={selectedIds} ... />` のアロー関数は、配列が初回ビルドされた時点のクロージャに `selectedIds` を捕捉する。`useMemo` は依存配列が空なので 2 回目以降は同じ配列 (= 同じ render 関数) を返す。結果として:
  - `toggleSelect` で `selectedIds` を更新しても、`<DataTable>` に渡される `columns[0].render` は古い `selectedIds` (= 初回の空 Set) を参照したまま。
  - `<SelectCell>` の `checked={selectedIds.has(invoiceId)}` は常に `false`。「全選択」ボタンも見た目だけ ON にならない (内部 state は変わるが UI 反映なし)。
  - `eslint-disable-next-line react-hooks/exhaustive-deps` のコメントは「`SelectCell/PaymentDateCell` が props 経由で最新 state を読む」と書いているが、**props として渡している `selectedIds` 自体がクロージャでキャプチャされた古い参照**なので前提が成立しない。
- **影響**: 一括反映 UI の主機能 (チェックボックスで選択 → 一括入金日反映) がブラックホール化する。グループ選択経由の一括選択 (`handleGroupSelect`) は `selectedIds` を直接 set するので state 自体は更新されるが、各行の表示反映が崩れる。
- **修正案**: `selectedIds` を `columns` の依存に入れる、あるいは `SelectCell` 内で外部 state を読むのをやめて Context 化する。最小修正は依存追加:

```tsx
const columns: Column<Invoice>[] = useMemo(() => [
  {
    key: '_select',
    header: '',
    render: (item: Invoice) => (
      <SelectCell invoiceId={item.invoiceId} selectedIds={selectedIds} onToggle={toggleSelect} />
    ),
  },
  // ... 他列
], [selectedIds, handlePaymentDateChange])
```

`selectedIds` 変更で `columns` が再生成されるため再レンダーのコストはあるが、`SelectCell` 自体は React.memo で抑止可能。なお F1 コメントが「checkbox uses extracted SelectCell」と書いてあるが、SelectCell に `selectedIds` Set 全体を渡しているため SelectCell の memoization も効かない (Set 参照が毎回変わる)。`isSelected: boolean` だけ渡すよう変更すべき:

```tsx
function SelectCell({ checked, invoiceId, onToggle }: { checked: boolean; invoiceId: number; onToggle: (id: number) => void }) {
  return <Checkbox checked={checked} onCheckedChange={() => onToggle(invoiceId)} ... />
}
// 呼び出し
render: (item) => (
  <SelectCell checked={selectedIds.has(item.invoiceId)} invoiceId={item.invoiceId} onToggle={toggleSelect} />
)
```

### C-N2: 入金日クリア操作が必ず 400 を返す (バリデーション設計と UI の不整合)

- **箇所**:
  - `frontend/components/pages/finance/invoices.tsx:71-91` (`PaymentDateCell` の `onBlur`)
  - `frontend/components/pages/finance/invoices.tsx:168-170` (`handlePaymentDateChange` で `value || null` に変換)
  - `backend/src/main/java/jp/co/oda32/dto/finance/PaymentDateUpdateRequest.java:9-12` (`@NotNull(message = "入金日は必須です")`)
- **問題**: `<Input type="date">` のクリアは空文字 `''` を返す。`onBlur` で `newVal !== (paymentDate ?? '')` を満たすため発火し、`handlePaymentDateChange(invoiceId, '')` → `paymentDate: '' || null` で `null` を送る。サーバの `PaymentDateUpdateRequest` は `@NotNull` のため 400 BadRequest。フロント側は `onError: () => toast.error('入金日の更新に失敗しました')` で握り潰す → **入金日のクリア (誤登録の取り消し) が「失敗トースト」しか出ず実質不可能**。
- **影響**: 入金消込の誤登録を訂正するルートが消滅。実運用時に DB 更新を要するハンディングへ。
- **修正案**:
  - 業務として「入金日をクリアできるようにする」なら `@NotNull` を外し `private LocalDate paymentDate;` のままに。Service 側で `inv.setPaymentDate(req.getPaymentDate())` をそのまま通す。同時にエラーメッセージを「null = クリア」と明示。
  - 業務として「クリアは不可」なら、フロント側で空文字入力時にミューテーションを発火させない。

```java
// PaymentDateUpdateRequest.java (クリア許容案)
@Data
public class PaymentDateUpdateRequest {
    private LocalDate paymentDate; // null = クリア
}
```

```tsx
// invoices.tsx (クリア不許容案)
onBlur={(e) => {
  const newVal = e.target.value
  if (!newVal) return // クリア操作はサポートしない
  if (newVal !== (paymentDate ?? '')) onUpdate(invoiceId, newVal)
}}
```

### C-N3: 取込時に `shopNo` の権限境界が無く、他事業部の月次請求実績を上書き可能 (IDOR)

- **箇所**: `backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java:447-462` (`importInvoices`)、`InvoiceImportService.java:54-57`
- **問題**: 設計レビュー C-2 は `bulk-payment-date` / `payment-date` / `partner-groups` の IDOR を指摘済だが、**取込エンドポイントの shopNo 認可は未検証**。
  - `@RequestParam(required = false) Integer shopNo` をそのまま `InvoiceImportService.importFromExcel(file, shopNo)` に渡し、`shopNoParam != null ? shopNoParam : (...)` で採用される。
  - 第1事業部 (shopNo=1) のユーザーが偽の松山 Excel を `shopNo=2` で POST すれば、第2事業部の `(shopNo=2, closingDate=...)` 行を Phase 2 で SELECT → UPSERT で上書き。
  - 既存行が消えず残る件は設計 C-3 が指摘済だが、**「他事業部のデータを汚染できる」という権限漏れ自体は新規論点**。
- **影響**: 経理データの IDOR 取込。データロスではないが他事業部の請求書を上書き可能。
- **修正案**: Controller で認可、Service には正規化済みの `shopNo` を渡す。

```java
@PostMapping("/invoices/import")
public ResponseEntity<?> importInvoices(
        @RequestParam("file") MultipartFile file,
        @RequestParam(required = false) Integer shopNo) {
    Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(shopNo);
    // 非 admin が他 shop を要求してきたら 403
    if (effectiveShopNo != null && shopNo != null && !effectiveShopNo.equals(shopNo)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "他ショップの取込はできません");
    }
    try {
        InvoiceImportResult result = invoiceImportService.importFromExcel(file, effectiveShopNo);
        return ResponseEntity.ok(result);
    } catch (...) { ... }
}
```

加えて、`InvoiceImportDialog.tsx:71` の説明文「ファイル名に「松山」を含む場合は第2事業部として取り込みます」も誤解を招く。設計レビュー m-3 と合わせ、UI に事業部セレクトを追加して `shopNo` 必須にすべき。

### C-N4: Excel 内に同じ `partnerCode` が複数行ある場合、`updatedRows` が二重カウント＋既存エンティティが二度上書きされる

- **箇所**: `InvoiceImportService.java:138-170` (Phase 2 UPSERT ループ)
- **問題**: SMILE の出力でサプライヤーの請求行が複数行に分かれる稀なケース (例: 同一 `partnerCode` が「通常分」と「修正分」で 2 行発生) が来ると、
  - `parsedInvoices` には 2 件の TInvoice (同 partnerCode) が並ぶ
  - `existingMap.get(parsed.getPartnerCode())` が同じ `existing` を 2 回返し、
    - 1 回目: setPreviousBalance(A), setNetSales(A), ... → `toSave.add(existing)` / `updatedRows++`
    - 2 回目: setPreviousBalance(B), setNetSales(B), ... → 同じ `existing` を再度 setter → `toSave` に重複追加 / `updatedRows++`
  - 結果: DB には B のみが残る (1 回目の値は失われる) かつ `updatedRows = 2` (実態 1) かつ `toSave` に同一エンティティが 2 件 → JPA は冪等に処理するが DTO のメトリクスが破綻。
- **影響**:
  - 同一 partnerCode の業務データ保持ポリシー (合算 or 後勝ち) が暗黙化していて運用混乱を招く。
  - `result.totalRows / insertedRows / updatedRows / skippedRows` の整合 (`totalRows == inserted + updated + skipped`) が崩れる。`InvoiceImportDialog` の表示が「3件処理 / 4件更新」のように出る可能性。
- **修正案**: Phase 1 終了時に `parsedInvoices` を partnerCode で dedup し、衝突時は明示的な仕様 (= 後勝ち / 合算 / エラー) を選ぶ。最低でも警告ログ + dedup。

```java
// Phase 1 終了後に dedup
Map<String, TInvoice> dedupMap = new LinkedHashMap<>();
for (TInvoice inv : parsedInvoices) {
    TInvoice prev = dedupMap.put(inv.getPartnerCode(), inv);
    if (prev != null) {
        log.warn("Excel 内で partnerCode={} が重複。後行で上書きします", inv.getPartnerCode());
    }
}
List<TInvoice> deduped = new ArrayList<>(dedupMap.values());
// 以降 deduped を Phase 2 で利用
```

合算が業務的に正しい場合は `deduped` ではなく金額フィールドを `add` で集約。設計書で運用判断を確定すること。

---

## Major 指摘 (新規)

### M-N1: `bulkUpdatePaymentDate` が「指定 ID のうち見つからなかった件」をレスポンスに含めず、サイレント失敗する

- **箇所**: `FinanceController.java:464-475`
- **問題**: 設計レビュー m-1 が `notFoundIds` 等の不足を Minor で指摘しているが、コードレベルでさらに踏み込むと:
  - `findByIds` は存在する分だけを返し、見つからなかった ID は単に WARN ログのみ。
  - C-2 (IDOR) を修正した後も「他 shop に属するため除外された ID」と「DB に存在しない ID」を区別できない。
  - `Map.of("updatedCount", invoices.size())` だけ返すため、フロント側 `bulkPaymentDateMutation.onSuccess` も「N 件」しか出せず原因切り分けに困る。
- **影響**: 一括反映の透明性欠如。経理が「100 件選んだが反映は 95 件」になっても理由が解らない。
- **修正案**:

```java
@PutMapping("/invoices/bulk-payment-date")
public ResponseEntity<?> bulkUpdatePaymentDate(@Valid @RequestBody BulkPaymentDateRequest request) {
    Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(null); // C-2 と組み合わせ
    List<TInvoice> invoices = tInvoiceService.findByIds(request.getInvoiceIds());
    Set<Integer> foundIds = invoices.stream().map(TInvoice::getInvoiceId).collect(Collectors.toSet());
    List<Integer> notFoundIds = request.getInvoiceIds().stream()
            .filter(id -> !foundIds.contains(id)).collect(Collectors.toList());
    List<Integer> forbiddenIds = effectiveShopNo == null ? List.of()
            : invoices.stream()
                .filter(inv -> !effectiveShopNo.equals(inv.getShopNo()))
                .map(TInvoice::getInvoiceId).collect(Collectors.toList());
    invoices.removeIf(inv -> forbiddenIds.contains(inv.getInvoiceId()));
    invoices.forEach(inv -> inv.setPaymentDate(request.getPaymentDate()));
    tInvoiceService.saveAll(invoices);
    return ResponseEntity.ok(Map.of(
        "requestedCount", request.getInvoiceIds().size(),
        "updatedCount", invoices.size(),
        "notFoundIds", notFoundIds,
        "forbiddenIds", forbiddenIds
    ));
}
```

### M-N2: `MPartnerGroupService#update` の partnerCodes コピーが空文字 / null / 重複に無防備

- **箇所**: `MPartnerGroupService.java:32-47`、`PartnerGroupDialog.tsx:91-103`
- **問題**:
  - フロント側 `groupPartnerCodes.split(/[\n,\s]+/)` 後に `filter((c) => c.length > 0)` で空は除去するが、`map((c) => c.trim())` のみで重複除去はしない。
  - サーバ側 `request.getPartnerCodes()` をそのまま `.addAll()`。`m_partner_group_member` テーブルにユニーク制約が無い場合、`(partner_group_id, partner_code)` が重複登録される (設計 D-5 で指摘済だが、**コード上は service レイヤで Set 化していない**)。
  - 6桁ゼロ埋めも実施しないので「29」「000029」が両方登録されると `filterGroup.partnerCodes` は両方含む → invoice の `partnerCode = 000029` だけマッチ → グループ件数表示 (`groupName（${partnerCodes.length}件）`) が業務件数と乖離する。
- **影響**: グループ件数表示の信頼性低下、内部 join 時のパフォーマンス低下。
- **修正案**: Service 側で正規化 + dedup を強制。

```java
@Transactional
public MPartnerGroup create(PartnerGroupRequest request) {
    List<String> normalized = request.getPartnerCodes().stream()
            .filter(c -> c != null && !c.isBlank())
            .map(c -> c.trim())
            .map(MPartnerGroupService::normalizePartnerCode) // 6桁0埋め
            .distinct()
            .toList();
    MPartnerGroup group = MPartnerGroup.builder()
            .groupName(request.getGroupName().trim())
            .shopNo(request.getShopNo())
            .partnerCodes(new ArrayList<>(normalized))
            .build();
    return repository.save(group);
}

private static String normalizePartnerCode(String raw) {
    try { return String.format("%06d", Long.parseLong(raw.trim())); }
    catch (NumberFormatException e) { return raw.trim(); }
}
```

`update` 側も同じ正規化を経由させ、可能なら DB に `UNIQUE(partner_group_id, partner_code)` を追加 (設計 C-1 と組み合わせ)。

### M-N3: `TInvoiceService` の `findBySpecification` が field-level の `new TInvoiceSpecification()` を生成している

- **箇所**: `TInvoiceService.java:19` (`private final TInvoiceSpecification tInvoiceSpecification = new TInvoiceSpecification();`)
- **問題**: Specification は stateless な helper だが、Spring DI を使わず `new` で生成。テスト容易性が下がる (差し替え不可) し、プロジェクト規約 (`@RequiredArgsConstructor` + コンストラクタ injection) と一貫しない。同一クラス内の `findByDetailedSpecification` のみが利用しているので、`static` メソッドにするか、Specification を Spring Bean (`@Component`) にする。
- **影響**: コーディング規約違反、テスト時に Specification 生成ロジックを差し替え不能。
- **修正案**:

```java
// CommonSpecification 配下を static メソッド化するか、
// あるいは下記のように field 初期化のみ private static final にする
private static final TInvoiceSpecification SPEC = new TInvoiceSpecification();
```

または `@Component class TInvoiceSpecification ...` + コンストラクタ injection。

### M-N4: `getCellStringValue` の FORMULA 分岐が NUMERIC 時の小数表現で「100.0」を文字列化する罠

- **箇所**: `InvoiceImportService.java:259-273`
- **問題**: `case FORMULA -> { try { yield cell.getStringCellValue(); } catch (IllegalStateException e) { try { double val = cell.getNumericCellValue(); if (val == Math.floor(val)) yield String.valueOf((long) val); yield String.valueOf(val); } ... } }` というネスト。
  - FORMULA セルが文字列を返すなら `getStringCellValue()` は OK。
  - FORMULA セルが NUMERIC を返す場合、小数を持つと `String.valueOf(val)` → `"3.14"` という JS 風表記。これが直後 `convertPartnerCode` に流れると `Long.parseLong("3.14")` で `NumberFormatException` → IllegalArgumentException でロールバック (設計 C-3 と連動)。
  - 数式列 = 得意先コードという稀なシナリオだが「総合計」行検出 (`colE.contains("総合計")`) も `getCellStringValue` を経由するので、E 列が数式の場合に「総合計」検出が失敗する可能性。
- **影響**: 設計 C-3 に内包されるが、コード固有の「FORMULA 二重キャッチ」の入れ子は読みにくい上、`String.valueOf(double)` のリスクが残る。
- **修正案**: 共通の数値→文字列変換関数を切り出し、`%s/%d` 表記の一貫性を保つ。

```java
private static String formatNumeric(double val) {
    if (Double.isInfinite(val)) return null;
    if (val == Math.floor(val)) return String.valueOf((long) val);
    return new BigDecimal(val).toPlainString(); // "3.14" の "E" 表記回避
}

case FORMULA -> {
    try { yield cell.getStringCellValue(); }
    catch (IllegalStateException e) {
        try { yield formatNumeric(cell.getNumericCellValue()); }
        catch (Exception e2) { yield null; }
    }
}
```

### M-N5: `existingInvoices` を Specification で取得しているが Repository に専用 finder を持たせない

- **箇所**: `InvoiceImportService.java:139-143`、`TInvoiceRepository.java`
- **問題**:

```java
List<TInvoice> existingInvoices = tInvoiceRepository
        .findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("shopNo"), shopNo),
                cb.equal(root.get("closingDate"), closingDate)
        ));
```

Service が無名 Specification を直接組み立てており、Repository 規約 (`findByShopNoAndClosingDate` のような derived query) と一貫しない。`TInvoiceRepository` は他にも derived query (`findByPartnerCodeAndClosingDate` など) を提供しているのに、ここだけ Specification 直書き。CLAUDE.md (`Repository 直接注入`) や次セッションタスク B-8 と整合しない。

- **影響**: 同等の検索が複数の書き方で表現される → リファクタ時の grep 性が悪化。
- **修正案**:

```java
// Repository に追加
List<TInvoice> findByShopNoAndClosingDate(Integer shopNo, String closingDate);

// Service 側
List<TInvoice> existingInvoices = tInvoiceRepository.findByShopNoAndClosingDate(shopNo, closingDate);
```

### M-N6: `bulk-payment-date` が `findAllById` で巨大 IN 句を発行しうる (チャンク化なし)

- **箇所**: `FinanceController.java:466`、`TInvoiceService.java:114-117`
- **問題**: フロント `selectedIds` は `Set<number>` で UI 側に上限なし。`Array.from(selectedIds)` で渡す `invoiceIds` が 5000 件を超えるとPostgreSQL の `IN (...)` 句が肥大化し (実用上は数万まで動くが latency 増)、`saveAll` も全件 single transaction で処理。
  - admin が「全件選択 → 一括反映」を試した時に DB ロックを長く取る可能性。
- **影響**: 大量行投入時のロック / レイテンシ。
- **修正案**: バッチサイズ制限 (例: 2000 件) を `BulkPaymentDateRequest` に `@Size(max = 2000)` で付与し、フロントもページング前提で UX を作る。

```java
@Data
public class BulkPaymentDateRequest {
    @NotNull @NotEmpty
    @Size(max = 2000, message = "一括反映は2000件以下にしてください")
    private List<Integer> invoiceIds;
    @NotNull private LocalDate paymentDate;
}
```

---

## Minor 指摘 (新規)

### m-N1: `TInvoiceService#getInvoiceById` が `null` を返す (Optional を返さない)

- **箇所**: `TInvoiceService.java:110-112`
- **問題**: 他メソッド (`findByShopNoAndPartnerCodeAndClosingDate`) は Optional を返すのに、`getInvoiceById` だけ `orElse(null)`。Controller (`FinanceController.java:438-441`) でも `if (invoice == null)` 判定。プロジェクト規約 (`getReferenceById() / findById()`) と整合しない。
- **修正案**: `Optional<TInvoice> findById(Integer)` に統一し、Controller 側で `orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))` 等。

### m-N2: `InvoiceImportService#existingMap` の merge function `(a, b) -> a` がサイレント

- **箇所**: `InvoiceImportService.java:145-146`
- **問題**:

```java
Map<String, TInvoice> existingMap = existingInvoices.stream()
        .collect(Collectors.toMap(TInvoice::getPartnerCode, Function.identity(), (a, b) -> a));
```

ユニーク制約 `(partner_code, closing_date, shop_no)` が DB レベルにあれば衝突しないはずだが、設計 C-1 で DDL 欠落のためユニーク制約も保証なし。`(a, b) -> a` で先勝ちにしているため、もし DB に重複データがあった場合は **2 件目が黙殺**される。WARN ログを残すべき。
- **修正案**:

```java
.collect(Collectors.toMap(TInvoice::getPartnerCode, Function.identity(), (a, b) -> {
    log.warn("DB に partnerCode={} の重複あり (invoiceId={} と {} が同じ closingDate/shop)。先勝ちで処理",
        a.getPartnerCode(), a.getInvoiceId(), b.getInvoiceId());
    return a;
}));
```

### m-N3: `InvoiceImportResult.errors` が常に `List.of()` (空イミュータブル)

- **箇所**: `InvoiceImportService.java:185`、`InvoiceImportDialog.tsx:19` (`errors: string[]`)
- **問題**: 設計 C-3 でも触れられているが、フィールドだけ存在して常に空。フロント側も `errors` を表示しない。デッドフィールド。設計 C-3 を実装するまでの暫定としても、現状はミスリーディング。
- **修正案**: 設計 C-3 実装まで暫定で `result.errors` をフロント側で表示するか、フィールドを削除。

### m-N4: `InvoiceImportService` のフィールドアノテーション順 (`@Transactional` がメソッドレベルのみ)

- **箇所**: `InvoiceImportService.java:40` (メソッドだけに `@Transactional`)
- **問題**: 設計レビュー m-7 が `TInvoiceService` の readOnly 不足を指摘済だが、`InvoiceImportService` も write 専用 method 1 つしかないのにクラスレベルでなくメソッドレベル付与。OK だが将来 read メソッドを追加した時に readOnly を入れ忘れる温床。一貫性のため `@Transactional(readOnly = true)` をクラスレベル + 書込みメソッドのみ override に統一推奨。

### m-N5: `InvoiceVerifier#findQuarterlyInvoice` の四半期判定で年跨ぎが部分的にしか考慮されていない

- **箇所**: `InvoiceVerifier.java:271-286`
- **問題**: 設計レビュー M-4 / M-5 で論点はカバーされているが、コード上の細部:
  - `getSpecialPartnerClosingDates` の前月計算 (`previousMonth = month - 1`) は `month==1` で `previousMonth=12, previousYear--` を考慮しているが、`previousMonth==11` (= 当月 12 月の場合) も case に入る。当月 12 月 → 前月 11 月 (四半期月) → `年/11/末` を生成する。これは正しい。
  - しかし、ループ内で `tInvoiceService.findByShopNoAndPartnerCodeAndClosingDate(shopNo, partnerCode, closingDateStr)` を都度 `Optional<TInvoice>` で取得 → `combined` に `add` するため、closingDates.size() = 2 なら 2 クエリ発行。N+1 ではないが、partner_code に対するインデックスに依存するため、件数は少ないが統計的にはピンポイント検索が増える。`findByShopNoAndPartnerCodeAndClosingDateIn(...)` を Repository に追加して 1 クエリにできる。
- **修正案**: Repository に `IN` クエリを追加。

```java
List<TInvoice> findByShopNoAndPartnerCodeAndClosingDateIn(
    Integer shopNo, String partnerCode, Collection<String> closingDates);
```

### m-N6: `InvoiceImportDialog` がエラー時に `error.message` をそのまま toast 表示する (XSS / 過度な情報露出はないが UX 不親切)

- **箇所**: `InvoiceImportDialog.tsx:43-45`
- **問題**: `api-client` 経由のエラーメッセージは `Map.of("message", e.getMessage())` でサーバから返されるため、Stack trace 等は出ないが、`IllegalArgumentException` の message に「Row2のA列が空です。締日を解析できません」のような技術寄り日本語が直出。経理担当向けに「ファイルの2行目が空です。Excel の Row2 に締日が記載されているか確認してください」など補足したい。
- **修正案**: トースト表示を `message` ベースのテーブル lookup に。

### m-N7: `PartnerGroupDialog` の保存ミューテーション `body` が型 `unknown` で型安全性が薄い

- **箇所**: `PartnerGroupDialog.tsx:53-68`
- **問題**: `mutationFn` の `body` を `{ groupName, shopNo, partnerCodes }` で渡すが、リクエスト型 (`PartnerGroupRequest` ミラー) が定義されていない。`PartnerGroup` (response 型) で兼用。フロント / バックエンド DTO の対応関係が暗黙化。
- **修正案**: `types/partner-group.ts` に `PartnerGroupRequest` を切り出し、`saveMutation` の引数型を厳密化。

```ts
interface PartnerGroupRequest {
  groupName: string
  shopNo: number
  partnerCodes: string[]
}
```

---

## 設計レビューと重複していない新規発見の要約

| ID | Severity | 一行サマリ |
|----|----------|----------|
| C-N1 | Critical | `useMemo(columns, [])` の stale closure でチェックボックスが UI 反映されない |
| C-N2 | Critical | 入金日クリア時 `null` 送信 → `@NotNull` 違反で必ず 400、サイレント失敗 |
| C-N3 | Critical | 取込エンドポイント `/invoices/import` の `shopNo` 認可漏れ (IDOR) |
| C-N4 | Critical | Excel 内重複 `partnerCode` で existing が二重 setter 適用 + メトリクス破綻 |
| M-N1 | Major | `bulk-payment-date` レスポンスに not-found / forbidden ID を含めない |
| M-N2 | Major | partner-group の partnerCodes 正規化・重複除去なし |
| M-N3 | Major | `TInvoiceService` field-level `new TInvoiceSpecification()` で DI 規約違反 |
| M-N4 | Major | `getCellStringValue` FORMULA 分岐の NUMERIC fallback で `String.valueOf(double)` ハマり |
| M-N5 | Major | `InvoiceImportService` が無名 Specification で existing 取得 (Repository 規約違反) |
| M-N6 | Major | `bulk-payment-date` のチャンクサイズ制限なし (DB 圧迫リスク) |
| m-N1 | Minor | `getInvoiceById` のみ `Optional` でなく `null` を返す |
| m-N2 | Minor | `existingMap` の merge function がサイレント先勝ち |
| m-N3 | Minor | `InvoiceImportResult.errors` がデッドフィールド (常に `List.of()`) |
| m-N4 | Minor | `InvoiceImportService` の `@Transactional` クラスレベル化推奨 |
| m-N5 | Minor | `findQuarterlyInvoice` の閉じ日複数取得を IN クエリ 1 発に集約可 |
| m-N6 | Minor | 取込エラーの toast が技術寄り日本語直出 |
| m-N7 | Minor | フロントの `PartnerGroupRequest` 型未定義 (response 型と兼用) |

---

## レビューチェックリスト結果

### Spring Boot 観点

| 項目 | 結果 | コメント |
|------|------|---------|
| N+1 検出 | OK (新規範囲では) | `findQuarterlyInvoice` の N=2 は許容、設計 M-1 (EAGER) は別途 |
| `@Transactional` 適切性 | 改善余地 (m-N4) | `InvoiceImportService` クラスレベル化推奨。`@Transactional` 範囲は OK |
| LazyInitializationException | OK | EAGER 主体 (設計 M-1) のため逆に発生しない |
| DI 方式 (constructor) | NG (M-N3) | `TInvoiceService` が field-level `new` |
| Entity 露出 | OK | DTO factory 経由で露出していない |
| SQL Injection (`@Query` ネイティブ) | OK | `escapeLike` で `%`/`_`/`\` をエスケープ済 (`CommonSpecification.java:34`) |
| 例外処理 | NG (C-N4 関連) | 重複 partnerCode 黙殺 (m-N2)、`errors` 空 (m-N3) |
| Migration 安全性 | (設計 C-1 既出) | 再掲しない |

### Next.js / React 観点

| 項目 | 結果 | コメント |
|------|------|---------|
| TypeScript 厳密性 (any, as キャスト) | OK | `any` なし。`as` 限定 |
| Immutable 更新 | OK | `setSelectedIds` で `new Set(prev)` |
| TanStack Query 使用 | OK | `useQuery` / `useMutation` を一貫利用 |
| ローディング/エラー状態 | OK | `LoadingSpinner` / `ErrorMessage` ハンドリング |
| メモリリーク (useEffect cleanup) | OK | `useEffect` は `PartnerGroupDialog` の 1 箇所のみで cleanup 不要 |
| key 設定 | OK | groups.map / shops.map で `key={...}` 設定済 |
| useMemo 依存配列 | NG (C-N1) | `columns` の `useMemo([])` が stale closure |
| XSS | OK | `dangerouslySetInnerHTML` 不使用 |
| console.log 残存 | OK | 該当なし |
| 認可・型安全 | NG (C-N2 / m-N7) | クリア操作 / 型不足 |

### 請求機能固有のコード品質

| 項目 | 結果 | コメント |
|------|------|---------|
| Excel 取込のエッジケース (重複コード) | NG (C-N4) | 同一 partnerCode 複数行で更新二重 |
| Repository 規約 (derived query 優先) | NG (M-N5) | 無名 Specification 直接記述 |
| Optional vs null | NG (m-N1) | `getInvoiceById` のみ `null` 返却 |
| 認可 (取込 / 一括更新) | NG (C-N3) | 取込側に IDOR (設計 C-2 の延長) |
| メトリクス整合 | NG (C-N4) | `totalRows == inserted + updated + skipped` が崩れうる |
| 業務エラーメッセージ品質 | 改善余地 (m-N6) | toast に技術寄り日本語直出 |

---

## 推奨アクション順序

1. **C-N1 (UI 機能停止)**: 一括反映 UI の根幹を壊しているので即修正。`SelectCell` を `checked: boolean` プロップに変更し `useMemo` を `[selectedIds, handlePaymentDateChange]` 依存に。
2. **C-N2 (UI 操作不能)**: `PaymentDateUpdateRequest.@NotNull` 撤去 or フロント側ガード。業務判断必須 (クリア許容/不許容)。
3. **C-N3 (認可)**: 設計レビュー C-2 と同 PR でまとめて取込エンドポイントもガード。
4. **C-N4 (データ整合)**: Phase 1 終了後の dedup を追加。業務に「合算 / 後勝ち」の確認を依頼し設計書 §5.1 へ反映。
5. **M-N1 / M-N2 / M-N5 / M-N6 (透明性 + 規約)**: 設計レビュー C-2 / C-3 修正と同 PR。
6. **M-N3 / M-N4 / m-N1?m-N7**: コード品質 PR を別建てで。
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2772ms:
# 請求機能 設計書

作成日: 2026-05-04
対象ブランチ: refactor/code-review-fixes
種別: 逆生成 (現状コードから抽出)

参考既存ドキュメント:
- `claudedocs/spec-invoice.md` (旧仕様 / 一部現状と乖離)
- `claudedocs/spec-invoice-import.md` (取込スキル仕様)
- `~/.claude/skills/s-invoice-import/SKILL.md`

---

## 1. 目的と業務上の意味

SMILE 基幹システムから出力される **請求実績 Excel** (得意先×締日単位の集計済みデータ) を新基盤に取り込み、得意先ごとに **請求一覧** として閲覧・運用するための機能。

### 業務上の意味
- 得意先 × 締日 (例: `2025/11/末`, `2025/07/20`) 単位で「前回残高 / 入金合計 / 繰越残高 / 純売上 / 税額 / 税込売上 / 今回請求額」を一覧化する **得意先請求の社内台帳**。
- 経理担当者が **入金日 (`payment_date`)** を 1 件ずつ／一括 (グループ単位) で記録し、入金消込の進捗を管理する。
- 同じ請求実績は売掛金検証バッチ (`InvoiceVerifier`) からも参照され、`t_accounts_receivable_summary` との **金額突合** に用いられる (= 売掛金画面の検証ロジックの「正解値」)。
- 取引先には **「得意先 = `partner_code`」** という SMILE のコード体系をそのまま使い、第1事業部 (`shop_no=1`) と第2事業部 / 松山 (`shop_no=2`) の 2 軸で分離管理する。

### 旧システム対応
| 旧画面 | 旧 URL | 新 URL |
|--------|--------|--------|
| 請求書一覧 | `/finance/invoice/list` | `/finance/invoices` |
| 請求書詳細 | `/finance/invoice/detail/{id}` | 一覧内で完結 (Dialog なし、入金日インライン) |

---

## 2. スコープ

### 含むもの
- SMILE 請求実績 Excel (`Sheet1`) からの取込 (`POST /finance/invoices/import`)
- 締月 / ショップ / 得意先コード / 得意先名 を絞り込み軸とした検索 (`GET /finance/invoices`)
- 入金日の単件更新 (`PUT /finance/invoices/{id}/payment-date`) と一括更新 (`PUT /finance/invoices/bulk-payment-date`)
- 入金日一括更新を効率化する **入金グループ (Partner Group)** マスタ CRUD (`/finance/partner-groups`)
- 売掛金検証 (`InvoiceVerifier`) からの請求書参照 (Read-only)

### 含まないもの
- 請求書の発行・印刷・PDF 出力 (本機能は閲覧/取込のみ)
- 請求明細 (商品別) の取り込み ? Excel ソースに明細列なし、本機能は集計値のみ
- 売掛金集計 (`t_accounts_receivable_summary`) のメンテナンス UI ? `/finance/accounts-receivable` の責務
- マネーフォワード仕訳出力 ? `/finance/cashbook-import` 系の責務
- 請求実績の手動編集・新規作成 (UI 上は `paymentDate` のみ更新可。他列は Excel 取込専用)

---

## 3. データモデル

### 3.1 Entity: `TInvoice` (`t_invoice`)

`backend/src/main/java/jp/co/oda32/domain/model/finance/TInvoice.java:25`

| カラム | 型 | NULL | 説明 |
|--------|------|------|------|
| `invoice_id` | `Integer` (PK, IDENTITY) | NOT NULL | 請求 ID (自動採番) |
| `partner_code` | `String` | NOT NULL | 得意先コード (6 桁 0 詰め) |
| `partner_name` | `String` | NOT NULL | 得意先名 (松山 999999 → "上様") |
| `closing_date` | `String` | NOT NULL | 締め日 (`YYYY/MM/末` or `YYYY/MM/DD`) |
| `previous_balance` | `BigDecimal` | NULL | 前回請求残高 |
| `total_payment` | `BigDecimal` | NULL | 入金合計 |
| `carry_over_balance` | `BigDecimal` | NULL | 繰越残高 |
| `net_sales` | `BigDecimal` | NULL | 純売上 (税抜) |
| `tax_price` | `BigDecimal` | NULL | 消費税額 |
| `net_sales_including_tax` | `BigDecimal` | NULL | 純売上額 (税込) |
| `current_billing_amount` | `BigDecimal` | NULL | 今回請求額 |
| `shop_no` | `Integer` | NULL (実態 NOT NULL) | ショップ番号 (1=第1事業部, 2=松山) |
| `payment_date` | `LocalDate` | NULL | 入金日 (UI から設定) |

**ユニーク制約**: `(partner_code, closing_date, shop_no)` (`TInvoice.java:22-24`)

**設計上の注意**:
- `closing_date` は **String 型**。「末日」時は `YYYY/MM/末` (全角ではなく漢字 1 字)、それ以外の特定日締めは `YYYY/MM/DD`。NFKC インデックスが張られている (`V005__create_nfkc_indexes.sql`)
- 監査フィールド (`add_*` / `modify_*`) は **持っていない**。`del_flg` も無い (取込で物理 UPSERT)
- `payment_date` は取込時に上書きされない (既存値保持: `InvoiceImportService.java:163`)

### 3.2 Entity: `MPartnerGroup` (`m_partner_group`) + `m_partner_group_member`

`backend/src/main/java/jp/co/oda32/domain/model/finance/MPartnerGroup.java:18`

| カラム | 型 | 説明 |
|--------|------|------|
| `partner_group_id` | `Integer` (PK, IDENTITY) | グループ ID |
| `group_name` | `String` (NOT NULL) | グループ名 |
| `shop_no` | `Integer` (NOT NULL) | 所属事業部 |
| `partner_codes` | `List<String>` | 子テーブル `m_partner_group_member.partner_code` を `@ElementCollection(EAGER)` で保持 |

入金日一括更新時の **多得意先の一括選択ボタン** として機能 (例: 「イズミグループ」=000231, 000232, ...)。

### 3.3 関連テーブル / マスタ参照
| 種別 | 参照先 | 用途 |
|------|--------|------|
| 売掛金集計 | `t_accounts_receivable_summary` | `InvoiceVerifier` で `t_invoice` と突合 (read-only)。一致時に `verification_result=1` / `mfExportEnabled=true`、不一致は `mf_export_enabled=false` (`InvoiceVerifier.java:131-176`) |
| ショップマスタ | `m_shop` (経由 `useShops` フック) | 第1/第2事業部の選択肢 |

---

## 4. API 設計

エンドポイントはすべて `FinanceController` (`backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java`) に集約。共通プレフィックス `/api/v1/finance`、`@PreAuthorize("isAuthenticated()")`。

### 4.1 一覧検索: `GET /api/v1/finance/invoices`

`FinanceController.java:423-432`

| パラメータ | 型 | 必須 | 説明 |
|------------|------|------|------|
| `shopNo` | Integer | 任意 | 完全一致 |
| `partnerCode` | String | 任意 | **前方一致** (`TInvoiceSpecification#partnerCodeContains`, `likePrefixNormalized`) |
| `partnerName` | String | 任意 | 部分一致 NFKC (`likeNormalized`) |
| `closingDate` | String | 任意 | **前方一致** (`yyyy/MM` で締月指定可能) |

**Response**: `List<InvoiceResponse>` ? `TInvoice` を全列マッピング (`InvoiceResponse.from`, `InvoiceResponse.java:26`)

**ソート**: `closingDate` 降順 (`TInvoiceService.java:163`)

**所感**: 旧 `getAllInvoices()` は残存しているが Controller からは非経由 (`TInvoiceService.java:31`)

### 4.2 入金日 単件更新: `PUT /api/v1/finance/invoices/{invoiceId}/payment-date`

`FinanceController.java:434-445`

**Request Body**: `PaymentDateUpdateRequest`
```json
{ "paymentDate": "2026-04-30" }
```
- `paymentDate` は `@NotNull` (`PaymentDateUpdateRequest.java:11`)

**Response**: 更新後 `InvoiceResponse`、対象なしは 404。

### 4.3 入金日 一括更新: `PUT /api/v1/finance/invoices/bulk-payment-date`

`FinanceController.java:464-475`

**Request Body**: `BulkPaymentDateRequest`
```json
{ "invoiceIds": [101, 102, 103], "paymentDate": "2026-04-30" }
```
- `invoiceIds`: `@NotEmpty`
- `paymentDate`: `@NotNull`

**Response**: `{ "updatedCount": <int> }`
**特記**: 要求件数と取得件数が異なる場合は **WARN ログのみ** で 200 を返却 (部分成功を許容)。

### 4.4 取込: `POST /api/v1/finance/invoices/import`

`FinanceController.java:447-462`

**Content-Type**: `multipart/form-data`

| パラメータ | 型 | 必須 | 説明 |
|------------|------|------|------|
| `file` | MultipartFile (.xlsx) | 必須 | SMILE 請求実績 Excel |
| `shopNo` | Integer | 任意 | 未指定時はファイル名に「松山」が含まれれば 2、なければ 1 (`InvoiceImportService.java:55-57`) |

**Response (200)**: `InvoiceImportResult`
```json
{
  "closingDate": "2025/11/末",
  "shopNo": 1,
  "totalRows": 312,
  "insertedRows": 5,
  "updatedRows": 307,
  "skippedRows": 0,
  "errors": []
}
```
**Response (400)**: `{ "message": "<理由>" }` ? `IllegalArgumentException` (拡張子不正 / Sheet1 締日解析失敗 等)
**Response (500)**: `{ "message": "インポート処理中にエラーが発生しました: ..." }`

### 4.5 入金グループ CRUD: `/api/v1/finance/partner-groups`

| Method | Path | 説明 | DTO |
|--------|------|------|-----|
| GET | `/partner-groups?shopNo={n}` | 一覧 (グループ名昇順, `MPartnerGroupRepository.java:12`) | `List<PartnerGroupResponse>` |
| POST | `/partner-groups` | 新規作成 | `PartnerGroupRequest` |
| PUT | `/partner-groups/{id}` | 更新 (`partnerCodes` は **clear → addAll** で全置換, `MPartnerGroupService.java:45-46`) | `PartnerGroupRequest` |
| DELETE | `/partner-groups/{id}` | 削除 (存在しない場合 `IllegalArgumentException`) | 204 No Content |

**`PartnerGroupRequest`** (`PartnerGroupRequest.java`):
```json
{
  "groupName": "イズミグループ",  // @NotBlank
  "shopNo": 1,                       // @NotNull
  "partnerCodes": ["000231", "000232"]  // @NotNull, @Size(min=1)
}
```

---

## 5. インポート処理フロー

### 5.1 全体フロー (Service: `InvoiceImportService.importFromExcel`)

`InvoiceImportService.java:40-188`

```
[Excel受信] 
  └─ ファイル名/拡張子チェック (.xlsx 必須, MIME 任意)
  └─ shopNo 確定 (引数 ≧ ファイル名「松山」判定)
  └─ Workbook open (Sheet1 優先, 無ければ先頭)
[Phase 0] 締日抽出 (Row2, A 列)
  └─ NFKC 正規化 → /(\d{4})年\s*(\d{1,2})月\s*(\d{1,2})日締/ で年月日取得
  └─ 日 == 月末日 → "YYYY/MM/末"、それ以外 → "YYYY/MM/DD"
[Phase 1] 行パース (5 行目以降, index=4 から)
  └─ E 列に「総合計」を見たら break
  └─ A 列 (得意先コード) を 6 桁 0 詰めに正規化 (`<009896>` も剥がす)
  └─ 松山 (shop=2) かつ partnerCode=999999 はスキップ
  └─ B 列 (得意先名) が空かつ非 999999 はスキップ
  └─ 999999 で得意先名が空 → 「上様」固定
  └─ 5/6/8/9/10/11/12 列を BigDecimal (HALF_UP, scale=0) で取得
  └─ TInvoice.builder() → parsedInvoices に蓄積
[Phase 2] UPSERT
  └─ 既存行を (shopNo, closingDate) で一括 SELECT
  └─ partner_code → existing の Map を構築
  └─ 各 parsed について
       ├─ existing あり → 9 列上書き (paymentDate は保持)
       └─ existing なし → そのまま新規 insert
  └─ saveAll で一括コミット (@Transactional, all-or-nothing)
[Phase 3] InvoiceImportResult ビルド + INFO ログ
```

### 5.2 列マッピング (Excel → `TInvoice`)

| Excel 列 (0 始) | 内容 | TInvoice フィールド |
|----------------|------|---------------------|
| A (0) | 得意先コード | `partnerCode` (6 桁 0 詰め) |
| B (1) | 得意先名 | `partnerName` |
| E (4) | 「総合計」検出用 | (制御) |
| F (5) | 前回残高 | `previousBalance` |
| G (6) | 入金合計 | `totalPayment` |
| I (8) | 繰越残高 | `carryOverBalance` |
| J (9) | 純売上 | `netSales` |
| K (10) | 消費税 | `taxPrice` |
| L (11) | 税込純売上 | `netSalesIncludingTax` |
| M (12) | 今回請求額 | `currentBillingAmount` |

**注**: H 列 (index=7) はスキップ。元 Excel に何の列があるかは確認 TODO。

### 5.3 セル値読み出し
`getCellStringValue` / `getCellBigDecimal` (`InvoiceImportService.java:248-302`)
- `STRING` / `NUMERIC` / `FORMULA` 全対応 (FORMULA は文字列 → 数値の二段フォールバック)
- 数値は `setScale(0, HALF_UP)` で必ず整数化

### 5.4 突合ロジック: `InvoiceVerifier`

`backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java:39`

請求書を「期待される売上額」として、`TAccountsReceivableSummary` (税率別に複数行) と突き合わせる。**取込機能とは独立**だが、`t_invoice` の使用先として最重要。

#### 突合キー (`InvoiceValidationKey`)
- `shopNo` (クリーンラボ `301491` は **強制 1**, `InvoiceVerifier.java:206`)
- `partnerCode`
- `closingDateStr` ? 各 AR 行の `transactionMonth` から `formatClosingDateForSearch` で組み立て:
  - `MONTH_END` / `CASH_ON_DELIVERY` (cutoff=0/-1) → `YYYY/MM/末`
  - 特定日締め → `YYYY/MM/DD`

#### 判定ルール (`InvoiceVerifier.java:131-191`)
| 状態 | 判定 | フィールド更新 |
|------|------|----------------|
| 請求書見つからず | NotFound | `verificationResult=0`, `mfExportEnabled=false`, `invoiceAmount=null` |
| 上様 (999999) | 常に上書き | 請求書金額に按分 + `verificationResult=1` |
| 差額 0 | 一致 | `verificationResult=1`, `mfExportEnabled=true` |
| 差額 ≦ `batch.accounts-receivable.invoice-amount-tolerance` (default 3 円) | 許容範囲一致 | 一致扱い + 按分 + WARN ログ |
| 差額超過 | 不一致 | `verificationResult=0`, `mfExportEnabled=false` + ERROR ログ |
| `verifiedManually=true` | スキップ | 何もしない |

#### 特殊得意先処理
- **イズミ (000231)**: 当月 15 日締め + (前月が 2/5/8/11 月のとき) 前月末締め も合算 → `findQuarterlyInvoice` (`InvoiceVerifier.java:234-266`) が **仮想 TInvoice** を生成
- **クリーンラボ (301491)**: `shopNo` を強制 1
- **上様 (999999)**: 集計が 0 でなければ請求書金額に按分上書き

#### 按分ロジック (`allocateProportionallyWithRemainder`, `allocateInvoiceByArRatio`)
- 税込合計 → 請求書金額に揃えるため、各税率行に **税込比率で按分**
- 端数は最大金額行で吸収 (sum 合致を保証)
- AR 合計が 0 だが請求書あり → ERROR ログ + 全行 0 を返し `applyMismatch` 側で `mf_export_enabled=false`

---

## 6. 画面設計

### 6.1 一覧画面 `/finance/invoices`

`frontend/components/pages/finance/invoices.tsx`

#### レイアウト
```
+---------------------------------------------+
| PageHeader: 請求書一覧  [インポート]         |
+---------------------------------------------+
| SearchForm                                   |
|  - ショップ (admin のみ)                     |
|  - 締月 (input type=month)                   |
|  - 得意先コード                              |
|  - 得意先名                                  |
+---------------------------------------------+
| (検索後) Bulk action bar                     |
|  [□全選択] X件選択中 | [グループ▼] [+管理] |
|                  [入金日]  [一括反映(N件)]  |
+---------------------------------------------+
| (グループ選択時) サマリ: 税込売上 / 今回請求額 |
+---------------------------------------------+
| DataTable                                    |
|  | □ | 得意先 | 締日 | 前回残高/入金 |       |
|  | 繰越残高 | 税込売上 | 今回請求額 | 入金日 | |
+---------------------------------------------+
```

#### 主な状態
| state | 役割 |
|-------|------|
| `searchParams: Record<string,string> \| null` | `null` で初期検索抑制 (`enabled: searchParams !== null`) |
| `selectedIds: Set<number>` | 選択中 invoiceId |
| `bulkPaymentDate: string` | 一括反映用日付 |
| `filterGroupId: string \| null` | 現在絞り込み中のグループ |
| `importDialogOpen` / `groupDialogOpen` | ダイアログ可否 |

#### 振る舞い
- **初期表示で API を叩かない** (グローバル規約)。`setSearchParams({...})` で検索開始。
- 締月入力 (`yyyy-MM`) は送信時に `replaceAll('-', '/')` → API は `yyyy/MM` で前方一致。
- **入金日インライン編集**: `PaymentDateCell` が `onBlur` で値変化を検知し `PUT /payment-date`。失敗時 toast。
- **入金グループ絞込み**: グループ選択で `partnerCodes` の Set を用いてクライアントサイドフィルタ + 該当 invoiceId 全選択 → 「一括反映」で `PUT /bulk-payment-date`。
- **admin 判定**: `user.shopNo === 0` で店舗セレクト表示 (`useShops`)。
- セルレンダリングは `SelectCell` / `PaymentDateCell` を抽出して `useMemo` の依存を最小化 (パフォーマンス F1, F2 のコメント参照)。

#### 表示フォーマット
- 金額は `formatNumber` (3 桁カンマ)、null は `'-'` (`formatMoney`, `invoices.tsx:50`)
- 「今回請求額」のみ太字 (`font-bold`)
- 「前回残高 / 入金」は同セルに 2 段表示

### 6.2 取込ダイアログ `InvoiceImportDialog`

`frontend/components/pages/finance/InvoiceImportDialog.tsx`

- ファイル選択 (`accept=".xlsx"`) → 「取込実行」で `POST /invoices/import` (FormData)
- 完了時はダイアログ内に **結果サマリ** (締日 / 事業部 / 処理行数 / 新規 / 更新 / スキップ) を差し替え表示
- onSuccess で `queryClient.invalidateQueries(['invoices'])` → 一覧自動再フェッチ
- `shopNo` パラメータは送らず、サーバ側のファイル名判定に任せている (画面から事業部選択はしない)

### 6.3 入金グループダイアログ `PartnerGroupDialog`

`frontend/components/pages/finance/PartnerGroupDialog.tsx`

- 一覧 (`groups` props) + 編集フォーム (グループ名 / `partnerCodes` を改行 or カンマ区切り textarea)
- `partnerCodes` は `split(/[\n,\s]+/)` で正規化 (空要素除外)
- 削除は `AlertDialog` で確認 → `DELETE /partner-groups/{id}`
- 保存 (`POST` / `PUT`) 後 `['partner-groups']` を invalidate

---

## 7. 既知の制約・注意事項

1. **`closing_date` が String 型**: 日付演算ができないため、月次集計や範囲検索は前方一致 (`yyyy/MM`) で済ませる。LocalDate へのマイグレーションは互換性影響大。
2. **`shop_no` 判定がファイル名依存**: API 引数で明示しないと「松山」文字列マッチに依存。誤判定時は再アップロード必須。
3. **取込はトランザクション 1 個**: `@Transactional` で全行成功か全行失敗。1 行のパース異常 (例: 得意先コードが非数値) で `IllegalArgumentException` を投げ全ロールバック (`InvoiceImportService.java:244`)。
4. **`payment_date` は取込で更新されない**: 既存行に対して保持 (`InvoiceImportService.java:163`)。一度入金記録した行が再取込で消えない設計。
5. **入金日一括更新の部分成功**: 要求 ID 中に存在しないものがあっても **WARN ログのみで 200 成功**。整合性チェックは画面側責務。
6. **`InvoiceVerifier` の特殊得意先ハードコード**:
   - 999999 (上様), 000231 (イズミ), 301491 (クリーンラボ) ? マスタ化されておらずコード定数
   - 「四半期特殊月 = 2/5/8/11」もハードコード (`InvoiceVerifier.java:282`)
7. **`MPartnerGroup#partnerCodes` は `EAGER`**: グループ件数が増えると一覧 GET の N+1 リスク (現状 dev 環境では問題なし)。
8. **CSRF / 認証**: `@PreAuthorize("isAuthenticated()")` のみ。ロール (admin / user) による操作制限は未実装。誰でも取込・一括更新が可能。
9. **`TInvoice` に監査列が無い**: `add_user_no` / `modify_user_no` を持たないため、誰がいつ入金日を更新したかは追跡できない。
10. **`TInvoiceService` の重複メソッド**: `saveInvoice` / `insert` が同義 (両方 `repository.save`)。Controller 経由の更新パスは `saveInvoice` のみ使用。

---

## 8. 課題 / TODO (コード読解中に気づいたもの)

| # | 区分 | 内容 |
|---|------|------|
| T1 | 仕様確認 | Excel の H 列 (index=7) の意味 ? 取込でスキップしている。元データの仕様確認 TODO。 |
| T2 | 設計差分 | `spec-invoice.md` (旧仕様書) は「入金日表示・更新がない」と書かれているが、実装は完了している。古い仕様書のため整合させるか deprecate マーク要。 |
| T3 | コード品質 | `TInvoiceService#saveInvoice` と `#insert` が同一実装 (`TInvoiceService.java:53-66`)。どちらかへ統一。 |
| T4 | コード品質 | `TInvoiceService#getAllInvoices` (`L31`) と `#findBySpecification` (`L133`) は Controller から未使用 (デッドコード)。 |
| T5 | バグリスク | `InvoiceVerifier.findQuarterlyInvoice` で virtual TInvoice を作る際、`invoiceId` を「最初」、`closingDate` を「最後」の請求書から取っており、監査時に整合しないリスク (`InvoiceVerifier.java:259-263`)。 |
| T6 | UI | フロント `Invoice` 型に `paymentDate: string \| null` とあるが、サーバの `InvoiceResponse#paymentDate` は `LocalDate` (Jackson が ISO 文字列でシリアライズ前提)。タイムゾーン揺らぎは無いが型注釈の整合確認 TODO。 |
| T7 | UI | `invoices.tsx:301` の `eslint-disable react-hooks/exhaustive-deps` ? `selectedIds` を依存に含めず `SelectCell` 経由で参照。React 19 で正しく再描画されるかの最終検証 TODO。 |
| T8 | 認可 | 取込・一括更新を admin 限定にする要否。現状は authenticated 全員が実行可能。 |
| T9 | データ整合 | `TInvoice.shopNo` が JPA 上 NULL 許容になっている (`@Column(name="shop_no")` のみ, NOT NULL 制約なし) のに対し、ユニーク制約と Entity 用途上は NOT NULL 必須。DDL 側で NOT NULL を確認する必要あり (TODO: 確認)。 |
| T10 | テスト | `InvoiceVerifier` の上様/イズミ/クリーンラボ特殊処理に対する単体テスト網羅性は未確認。 |
| T11 | 設計 | `InvoiceVerifier` の特殊得意先 (999999/000231/301491) と「四半期特殊月」をマスタ化する余地。現状は法人マスタ追加時にコード変更が必要。 |
| T12 | i18n | `closing_date` の「末」漢字リテラル ? DB 検索文字列のキー要素。本番運用での移行困難性に注意。 |
| T13 | パフォーマンス | `MPartnerGroup` の `partnerCodes` `@ElementCollection(EAGER)` は N+1 候補。グループ件数増加時に LAZY 化検討。 |
| T14 | 命名 | DTO の `closingDate` フィールドが `String`。LocalDate との混在で型エラー検出が遅れる可能性。 |
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
既存レビューはかなりコード品質寄りなので、重複回避のため「権威データ、履歴、業務キー、グループの時間軸」に寄せます。次に対象コードの実際のキーや更新条件を確認します。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\InvoiceImportService.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\batch\\finance\\service\\InvoiceVerifier.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\TInvoice.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\MPartnerGroup.java" in C:\project\odamitsu-data-hub
 succeeded in 1746ms:
package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.TInvoice;
import jp.co.oda32.domain.repository.finance.TInvoiceRepository;
import jp.co.oda32.dto.finance.InvoiceImportResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceImportService {

    private final TInvoiceRepository tInvoiceRepository;

    private static final Pattern CLOSING_DATE_PATTERN =
            Pattern.compile("(\\d{4})年\\s*(\\d{1,2})月\\s*(\\d{1,2})日締");

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Transactional
    public InvoiceImportResult importFromExcel(MultipartFile file, Integer shopNoParam) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("ファイル名が取得できません");
        }
        if (!originalFilename.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("Excelファイル（.xlsx）のみ対応しています");
        }
        String contentType = file.getContentType();
        if (contentType != null && !XLSX_CONTENT_TYPE.equals(contentType)) {
            throw new IllegalArgumentException("Excelファイル（.xlsx）のみ対応しています");
        }

        // shopNoが指定されればそれを使用、未指定ならファイル名から推定
        int shopNo = shopNoParam != null ? shopNoParam
                : (originalFilename.contains("松山") ? 2 : 1);
        boolean isMatsuyama = shopNo == 2;

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheet("Sheet1");
            if (sheet == null) {
                log.warn("Sheet1が見つかりません。先頭シートを使用します: file={}", originalFilename);
                sheet = workbook.getSheetAt(0);
            }
            if (sheet == null) {
                throw new IllegalArgumentException("シートが見つかりません");
            }

            // Row2: 締日を導出
            String closingDate = parseClosingDate(sheet.getRow(1));

            // Phase 1: 全行パース → エンティティリストに蓄積
            List<TInvoice> parsedInvoices = new ArrayList<>();
            int skippedRows = 0;
            int totalRows = 0;

            for (int i = 4; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                // 総合計行チェック（E列 = index 4）
                String colE = getCellStringValue(row.getCell(4));
                if (colE != null && colE.contains("総合計")) {
                    break;
                }

                // 得意先コード（A列 = index 0）
                Cell codeCell = row.getCell(0);
                if (codeCell == null) {
                    continue;
                }
                String rawCode = getCellStringValue(codeCell);
                if (rawCode == null || rawCode.isBlank()) {
                    continue;
                }

                totalRows++;

                String partnerCode = convertPartnerCode(rawCode);

                // 松山の999999はスキップ
                if (isMatsuyama && "999999".equals(partnerCode)) {
                    skippedRows++;
                    log.debug("松山999999スキップ: row={}", i + 1);
                    continue;
                }

                // 得意先名（B列 = index 1）
                String partnerName = getCellStringValue(row.getCell(1));
                if ((partnerName == null || partnerName.isBlank()) && !"999999".equals(partnerCode)) {
                    skippedRows++;
                    log.debug("得意先名なしスキップ: row={}, code={}", i + 1, partnerCode);
                    continue;
                }
                if (partnerName == null || partnerName.isBlank()) {
                    partnerName = "上様";
                }

                TInvoice invoice = TInvoice.builder()
                        .partnerCode(partnerCode)
                        .partnerName(partnerName)
                        .closingDate(closingDate)
                        .previousBalance(getCellBigDecimal(row.getCell(5)))
                        .totalPayment(getCellBigDecimal(row.getCell(6)))
                        .carryOverBalance(getCellBigDecimal(row.getCell(8)))
                        .netSales(getCellBigDecimal(row.getCell(9)))
                        .taxPrice(getCellBigDecimal(row.getCell(10)))
                        .netSalesIncludingTax(getCellBigDecimal(row.getCell(11)))
                        .currentBillingAmount(getCellBigDecimal(row.getCell(12)))
                        .shopNo(shopNo)
                        .build();

                parsedInvoices.add(invoice);
            }

            // Phase 2: 既存レコードを一括取得してUPSERT
            List<TInvoice> existingInvoices = tInvoiceRepository
                    .findAll((root, query, cb) -> cb.and(
                            cb.equal(root.get("shopNo"), shopNo),
                            cb.equal(root.get("closingDate"), closingDate)
                    ));

            Map<String, TInvoice> existingMap = existingInvoices.stream()
                    .collect(Collectors.toMap(TInvoice::getPartnerCode, Function.identity(), (a, b) -> a));

            int insertedRows = 0;
            int updatedRows = 0;
            List<TInvoice> toSave = new ArrayList<>();

            for (TInvoice parsed : parsedInvoices) {
                TInvoice existing = existingMap.get(parsed.getPartnerCode());
                if (existing != null) {
                    existing.setPartnerName(parsed.getPartnerName());
                    existing.setPreviousBalance(parsed.getPreviousBalance());
                    existing.setTotalPayment(parsed.getTotalPayment());
                    existing.setCarryOverBalance(parsed.getCarryOverBalance());
                    existing.setNetSales(parsed.getNetSales());
                    existing.setTaxPrice(parsed.getTaxPrice());
                    existing.setNetSalesIncludingTax(parsed.getNetSalesIncludingTax());
                    existing.setCurrentBillingAmount(parsed.getCurrentBillingAmount());
                    // payment_date は保持（更新しない）
                    toSave.add(existing);
                    updatedRows++;
                } else {
                    toSave.add(parsed);
                    insertedRows++;
                }
            }

            // 一括永続化
            tInvoiceRepository.saveAll(toSave);

            log.info("請求実績インポート完了: closingDate={}, shopNo={}, total={}, inserted={}, updated={}, skipped={}",
                    closingDate, shopNo, totalRows, insertedRows, updatedRows, skippedRows);

            return InvoiceImportResult.builder()
                    .closingDate(closingDate)
                    .shopNo(shopNo)
                    .totalRows(totalRows)
                    .insertedRows(insertedRows)
                    .updatedRows(updatedRows)
                    .skippedRows(skippedRows)
                    .errors(List.of())
                    .build();
        }
    }

    /**
     * Row2から締日を導出する
     * 例: "2025年11月30日締 今回請求分" → "2025/11/末"
     * 例: "2025年 7月20日締 今回請求分" → "2025/07/20"
     */
    String parseClosingDate(Row row) {
        if (row == null) {
            throw new IllegalArgumentException("Row2が空です。締日を解析できません");
        }
        Cell cell = row.getCell(0);
        if (cell == null) {
            throw new IllegalArgumentException("Row2のA列が空です。締日を解析できません");
        }

        String raw = getCellStringValue(cell);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Row2のA列が空です。締日を解析できません");
        }

        // NFKC正規化（全角数字・全角スペース対策）
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC);

        Matcher matcher = CLOSING_DATE_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Row2から締日を解析できません: '" + raw + "'");
        }

        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int day = Integer.parseInt(matcher.group(3));

        int lastDayOfMonth = YearMonth.of(year, month).lengthOfMonth();

        String monthStr = String.format("%02d", month);
        if (day == lastDayOfMonth) {
            return year + "/" + monthStr + "/末";
        } else {
            return year + "/" + monthStr + "/" + String.format("%02d", day);
        }
    }

    /**
     * 得意先コードを6桁0埋めに変換する
     * - "<009896>" → "009896"
     * - 29 → "000029"
     * - "181" → "000181"
     */
    String convertPartnerCode(String rawCode) {
        String code = rawCode.replace("<", "").replace(">", "").trim();

        try {
            long numericCode = Long.parseLong(code);
            return String.format("%06d", numericCode);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("得意先コードが数値ではありません: '" + rawCode + "'");
        }
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (IllegalStateException e) {
                    try {
                        double val = cell.getNumericCellValue();
                        if (val == Math.floor(val) && !Double.isInfinite(val)) {
                            yield String.valueOf((long) val);
                        }
                        yield String.valueOf(val);
                    } catch (Exception e2) {
                        yield null;
                    }
                }
            }
            default -> null;
        };
    }

    private BigDecimal getCellBigDecimal(Cell cell) {
        if (cell == null) return BigDecimal.ZERO;
        return switch (cell.getCellType()) {
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                yield BigDecimal.valueOf(val).setScale(0, RoundingMode.HALF_UP);
            }
            case STRING -> {
                try {
                    yield new BigDecimal(cell.getStringCellValue().trim()).setScale(0, RoundingMode.HALF_UP);
                } catch (NumberFormatException e) {
                    yield BigDecimal.ZERO;
                }
            }
            case FORMULA -> {
                try {
                    double val = cell.getNumericCellValue();
                    yield BigDecimal.valueOf(val).setScale(0, RoundingMode.HALF_UP);
                } catch (Exception e) {
                    yield BigDecimal.ZERO;
                }
            }
            default -> BigDecimal.ZERO;
        };
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1807ms:
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

 succeeded in 2733ms:
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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "m_partner_group_member",
            joinColumns = @JoinColumn(name = "partner_group_id")
    )
    @Column(name = "partner_code")
    @Builder.Default
    private List<String> partnerCodes = new ArrayList<>();
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3738ms:
package jp.co.oda32.batch.finance.service;

import jp.co.oda32.batch.finance.model.InvoiceVerificationSummary;
import jp.co.oda32.constant.PaymentType;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.domain.model.finance.TInvoice;
import jp.co.oda32.domain.service.finance.TInvoiceService;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * SMILE請求書 (t_invoice) と売掛金集計 (TAccountsReceivableSummary) を突合する検証サービス。
 * <p>
 * 買掛側 {@link SmilePaymentVerifier} と対称。検証ロジックは
 * 元々 {@code TAccountsReceivableSummaryTasklet} に埋め込まれていたものを抽出した。
 * <ul>
 *   <li>差額0円 or 許容誤差内: 一致（請求書金額に按分）</li>
 *   <li>許容誤差超: 不一致、mf_export_enabled=false</li>
 *   <li>請求書なし: 不一致扱い、mf_export_enabled=false</li>
 *   <li>verified_manually=true: スキップ（再検証で上書きしない）</li>
 *   <li>「上様」(partnerCode "999999" or ?7桁): 請求書金額で常に上書き</li>
 *   <li>イズミ(得意先000231): 当月15日締め + 前月が四半期特殊月(2/5/8/11)なら前月末締めの請求書も合算</li>
 *   <li>クリーンラボ(得意先301491): 検索時の店舗番号を1に強制</li>
 * </ul>
 *
 * @author k_oda
 * @since 2026/04/17 (抽出元: {@code TAccountsReceivableSummaryTasklet})
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class InvoiceVerifier {

    private static final String QUARTERLY_BILLING_PARTNER_CODE = "000231";
    private static final String CLEAN_LAB_PARTNER_CODE = "301491";
    private static final String JOSAMA_PARTNER_CODE = "999999";

    private final TInvoiceService tInvoiceService;

    @Value("${batch.accounts-receivable.invoice-amount-tolerance:3}")
    private BigDecimal invoiceAmountTolerance;

    /**
     * 売掛金集計リストと SMILE 請求書を突合し、
     * {@code TAccountsReceivableSummary} の検証関連フィールドを更新する。
     * <p>
     * 更新対象フィールド: {@code verificationResult}, {@code mfExportEnabled},
     * {@code invoiceAmount}, {@code verificationDifference}, {@code invoiceNo},
     * 按分により {@code taxIncludedAmountChange}, {@code taxExcludedAmountChange}。
     * <p>
     * 本メソッドは save を呼ばない。呼び出し側で persist すること。
     *
     * <p><b>締め日突合キーの決定ロジック:</b>
     * 各 {@code summary.transactionMonth} がそのままその行の「締め日 (期間終了日)」を表すため、
     * 請求書検索は個別の {@code summary.transactionMonth} を基に行う。
     * {@code fallbackPeriodEndDate} は {@code transactionMonth} が null の救済用のみで、
     * 画面一括検証のように AR 行が複数月にまたがる場合でも各行が正しい月の請求書と突合される。
     *
     * @param summaries             対象の売掛金集計リスト（inplace 更新される）
     * @param fallbackPeriodEndDate {@code transactionMonth} が null の行のフォールバック期間終了日
     * @return 集計件数の結果DTO
     */
    public InvoiceVerificationSummary verify(
            List<TAccountsReceivableSummary> summaries,
            LocalDate fallbackPeriodEndDate) {

        int matchedCount = 0;
        int mismatchCount = 0;
        int notFoundCount = 0;
        int skippedManualCount = 0;
        int josamaOverwriteCount = 0;
        int quarterlySpecialCount = 0;

        // 検証対象から手動確定行を除外
        List<TAccountsReceivableSummary> targetSummaries = new ArrayList<>();
        for (TAccountsReceivableSummary summary : summaries) {
            if (Boolean.TRUE.equals(summary.getVerifiedManually())) {
                skippedManualCount++;
                log.debug("手動確定済みのため検証をスキップ: shopNo={}, partnerNo={}, transactionMonth={}, taxRate={}%",
                        summary.getShopNo(), summary.getPartnerNo(),
                        summary.getTransactionMonth(), summary.getTaxRate());
                continue;
            }
            targetSummaries.add(summary);
        }

        // 請求書検索キーでグループ化（各 summary の transactionMonth を個別に参照するため、
        // 複数月にまたがる AR リストでも行ごとに正しい請求書月と突合される）
        Map<InvoiceValidationKey, List<TAccountsReceivableSummary>> byKey = new LinkedHashMap<>();
        for (TAccountsReceivableSummary summary : targetSummaries) {
            InvoiceValidationKey key = buildInvoiceKey(summary, fallbackPeriodEndDate);
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(summary);
        }

        for (Map.Entry<InvoiceValidationKey, List<TAccountsReceivableSummary>> entry : byKey.entrySet()) {
            InvoiceValidationKey key = entry.getKey();
            List<TAccountsReceivableSummary> group = entry.getValue();

            // グループの集計税込金額
            BigDecimal totalTaxIncluded = group.stream()
                    .map(s -> s.getTaxIncludedAmountChange() != null ? s.getTaxIncludedAmountChange() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(0, RoundingMode.DOWN);

            // 請求書検索
            Optional<TInvoice> invoiceOpt;
            boolean isQuarterlySpecial = QUARTERLY_BILLING_PARTNER_CODE.equals(key.getPartnerCode());

            if (isQuarterlySpecial) {
                // 四半期特殊も group の transactionMonth を基準にする（group は同一締め日キーで集約済みのため
                // 先頭行の transactionMonth で代表させて問題ない）
                LocalDate groupPeriodEnd = group.get(0).getTransactionMonth() != null
                        ? group.get(0).getTransactionMonth()
                        : fallbackPeriodEndDate;
                invoiceOpt = findQuarterlyInvoice(key.getShopNo(), key.getPartnerCode(), groupPeriodEnd);
                if (invoiceOpt.isPresent()) quarterlySpecialCount++;
            } else {
                invoiceOpt = tInvoiceService.findByShopNoAndPartnerCodeAndClosingDate(
                        key.getShopNo(), key.getPartnerCode(), key.getClosingDateStr());
            }

            boolean isJosama = JOSAMA_PARTNER_CODE.equals(key.getPartnerCode());

            if (invoiceOpt.isEmpty()) {
                // 請求書なし
                notFoundCount++;
                applyNotFound(group);
                continue;
            }

            TInvoice invoice = invoiceOpt.get();
            BigDecimal invoiceAmount = invoice.getNetSalesIncludingTax() != null
                    ? invoice.getNetSalesIncludingTax().setScale(0, RoundingMode.DOWN)
                    : BigDecimal.ZERO;
            Integer invoiceId = invoice.getInvoiceId();
            BigDecimal diff = invoiceAmount.subtract(totalTaxIncluded);

            if (isJosama) {
                // 上様: 請求書金額で常に上書き
                josamaOverwriteCount++;
                if (totalTaxIncluded.compareTo(BigDecimal.ZERO) != 0) {
                    allocateProportionallyWithRemainder(group, invoiceAmount, totalTaxIncluded);
                } else {
                    log.error("上様分の集計金額が0のため按分不可: shopNo={}, partnerCode={}",
                            key.getShopNo(), key.getPartnerCode());
                }
                applyMatched(group, invoiceAmount, diff, invoiceId);
                matchedCount++;
                continue;
            }

            if (diff.compareTo(BigDecimal.ZERO) == 0) {
                // 完全一致
                applyMatched(group, invoiceAmount, diff, invoiceId);
                matchedCount++;
            } else if (diff.abs().compareTo(invoiceAmountTolerance) <= 0) {
                // 許容誤差内: 請求書金額に按分
                allocateProportionallyWithRemainder(group, invoiceAmount, totalTaxIncluded);
                applyMatched(group, invoiceAmount, diff, invoiceId);
                matchedCount++;
                log.warn("請求書との差額が{}円ありますが許容範囲内として一致扱いに調整: shopNo={}, partnerCode={}, 集計={}, 請求書={}",
                        diff.abs(), key.getShopNo(), key.getPartnerCode(), totalTaxIncluded, invoiceAmount);
            } else {
                // 不一致
                applyMismatch(group, invoiceAmount, diff, invoiceId);
                mismatchCount++;
                log.error("請求書金額不一致: shopNo={}, partnerCode={}, 集計={}, 請求書={}, 差額={}",
                        key.getShopNo(), key.getPartnerCode(), totalTaxIncluded, invoiceAmount, diff);
            }
        }

        log.info("検証完了: 一致={}, 不一致={}, 請求書なし={}, 手動スキップ={}, 上様上書き={}, 四半期特殊={}",
                matchedCount, mismatchCount, notFoundCount,
                skippedManualCount, josamaOverwriteCount, quarterlySpecialCount);

        return InvoiceVerificationSummary.builder()
                .matchedCount(matchedCount)
                .mismatchCount(mismatchCount)
                .notFoundCount(notFoundCount)
                .skippedManualCount(skippedManualCount)
                .josamaOverwriteCount(josamaOverwriteCount)
                .quarterlySpecialCount(quarterlySpecialCount)
                .build();
    }

    /**
     * 請求書検索キーを構築。
     * <ul>
     *   <li>締め日文字列は <b>各 summary 自身の {@code transactionMonth}</b> を基準に組み立てる
     *       （画面一括検証のように AR 行が複数月にまたがっても、行ごとに正しい月の請求書と突合される）。</li>
     *   <li>{@code transactionMonth} が null の場合のみ {@code fallbackPeriodEndDate} を使用。</li>
     *   <li>クリーンラボ 301491 は店舗番号 1 に強制。</li>
     * </ul>
     */
    private InvoiceValidationKey buildInvoiceKey(
            TAccountsReceivableSummary summary, LocalDate fallbackPeriodEndDate) {
        Integer shopNo = summary.getShopNo();
        String partnerCode = summary.getPartnerCode();
        if (CLEAN_LAB_PARTNER_CODE.equals(partnerCode)) {
            shopNo = 1;
        }
        LocalDate periodEnd = summary.getTransactionMonth() != null
                ? summary.getTransactionMonth()
                : fallbackPeriodEndDate;
        String closingDateStr = formatClosingDateForSearch(periodEnd, summary.getCutoffDate());
        return new InvoiceValidationKey(shopNo, partnerCode, closingDateStr);
    }

    /**
     * 請求書検索に使用する締め日文字列を生成する。
     * 都度現金払い・月末締め → "YYYY/MM/末"、特定日締め → "YYYY/MM/DD"。
     */
    private String formatClosingDateForSearch(LocalDate targetPeriodEndDate, Integer cutoffDate) {
        PaymentType paymentType = PaymentType.fromCutoffCode(cutoffDate);
        if (paymentType == PaymentType.CASH_ON_DELIVERY || paymentType == PaymentType.MONTH_END) {
            return String.format("%d/%02d/末", targetPeriodEndDate.getYear(), targetPeriodEndDate.getMonthValue());
        } else {
            return String.format("%d/%02d/%02d",
                    targetPeriodEndDate.getYear(), targetPeriodEndDate.getMonthValue(), cutoffDate);
        }
    }

    /**
     * イズミ(000231)の四半期特殊処理。
     * 当月15日締め + 前月が四半期特殊月(2/5/8/11)なら前月末締めも合算。
     */
    private Optional<TInvoice> findQuarterlyInvoice(
            Integer shopNo, String partnerCode, LocalDate targetPeriodEndDate) {
        List<String> closingDates = getSpecialPartnerClosingDates(targetPeriodEndDate);
        log.info("四半期特殊処理 partnerCode={}, 対象締め日={}", partnerCode, closingDates);

        List<TInvoice> combined = new ArrayList<>();
        for (String closingDateStr : closingDates) {
            tInvoiceService.findByShopNoAndPartnerCodeAndClosingDate(shopNo, partnerCode, closingDateStr)
                    .ifPresent(combined::add);
        }

        if (combined.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (TInvoice inv : combined) {
            if (inv.getNetSalesIncludingTax() != null) {
                totalAmount = totalAmount.add(inv.getNetSalesIncludingTax());
            }
        }

        // 代表の請求書IDは最初のものを使用（監査用）
        TInvoice representative = combined.get(0);
        TInvoice virtual = new TInvoice();
        virtual.setInvoiceId(representative.getInvoiceId());
        virtual.setShopNo(shopNo);
        virtual.setPartnerCode(partnerCode);
        virtual.setPartnerName(representative.getPartnerName());
        virtual.setClosingDate(combined.get(combined.size() - 1).getClosingDate());
        virtual.setNetSalesIncludingTax(totalAmount);
        return Optional.of(virtual);
    }

    /**
     * イズミの検索対象締め日リスト。当月15日締め + 前月が2/5/8/11月なら前月末締めも追加。
     */
    private List<String> getSpecialPartnerClosingDates(LocalDate targetPeriodEndDate) {
        List<String> closingDates = new ArrayList<>();
        int month = targetPeriodEndDate.getMonthValue();
        int year = targetPeriodEndDate.getYear();
        closingDates.add(String.format("%d/%02d/15", year, month));
        int previousMonth = month - 1;
        int previousYear = year;
        if (previousMonth == 0) {
            previousMonth = 12;
            previousYear--;
        }
        if (previousMonth == 2 || previousMonth == 5 || previousMonth == 8 || previousMonth == 11) {
            closingDates.add(String.format("%d/%02d/末", previousYear, previousMonth));
        }
        return closingDates;
    }

    /**
     * 集計サマリー群を請求書金額に合わせて按分する。
     * 単純に比率を掛けて DOWN で丸めると合計が請求書金額に届かないため、
     * 最大金額の行で残差を吸収する。税抜も同様に按分。
     */
    private void allocateProportionallyWithRemainder(
            List<TAccountsReceivableSummary> summaries,
            BigDecimal targetIncTotal,
            BigDecimal originalIncTotal) {
        if (summaries == null || summaries.isEmpty()) return;
        if (originalIncTotal == null || originalIncTotal.compareTo(BigDecimal.ZERO) == 0) return;

        BigDecimal ratio = targetIncTotal.divide(originalIncTotal, 10, RoundingMode.HALF_UP);

        TAccountsReceivableSummary largest = summaries.stream()
                .filter(s -> s.getTaxIncludedAmountChange() != null)
                .max(Comparator.comparing(TAccountsReceivableSummary::getTaxIncludedAmountChange))
                .orElse(null);

        BigDecimal incAllocated = BigDecimal.ZERO;
        BigDecimal excAllocated = BigDecimal.ZERO;
        BigDecimal targetExcTotal = summaries.stream()
                .filter(s -> s.getTaxExcludedAmountChange() != null)
                .map(TAccountsReceivableSummary::getTaxExcludedAmountChange)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(ratio)
                .setScale(0, RoundingMode.DOWN);

        for (TAccountsReceivableSummary s : summaries) {
            if (s == largest) continue;
            if (s.getTaxIncludedAmountChange() != null) {
                BigDecimal adjIncluded = s.getTaxIncludedAmountChange().multiply(ratio).setScale(0, RoundingMode.DOWN);
                s.setTaxIncludedAmountChange(adjIncluded);
                incAllocated = incAllocated.add(adjIncluded);
            }
            if (s.getTaxExcludedAmountChange() != null) {
                BigDecimal adjExcluded = s.getTaxExcludedAmountChange().multiply(ratio).setScale(0, RoundingMode.DOWN);
                s.setTaxExcludedAmountChange(adjExcluded);
                excAllocated = excAllocated.add(adjExcluded);
            }
        }

        if (largest != null) {
            largest.setTaxIncludedAmountChange(targetIncTotal.subtract(incAllocated));
            if (largest.getTaxExcludedAmountChange() != null) {
                largest.setTaxExcludedAmountChange(targetExcTotal.subtract(excAllocated));
            }
        }
    }

    private void applyMatched(List<TAccountsReceivableSummary> group,
                              BigDecimal invoiceAmount, BigDecimal diff, Integer invoiceId) {
        // 一致時は AR が請求書合算に按分済みなので、行の invoice_amount は行の taxIncludedAmountChange と一致する。
        // UI 表示で「請求書金額(税込)」列が行ごとに税率別で出るようにするため、行単位で設定する。
        Map<TAccountsReceivableSummary, BigDecimal> allocated = allocateInvoiceByArRatio(group, invoiceAmount);
        for (TAccountsReceivableSummary s : group) {
            s.setVerificationResult(1);
            s.setMfExportEnabled(Boolean.TRUE);
            s.setInvoiceAmount(allocated.get(s));
            // 行ごとの差額 = 行の按分請求書額 - 行の税込売掛 (一致時は 0)
            BigDecimal rowAr = s.getTaxIncludedAmountChange() != null
                    ? s.getTaxIncludedAmountChange() : BigDecimal.ZERO;
            s.setVerificationDifference(allocated.get(s).subtract(rowAr));
            s.setInvoiceNo(invoiceId);
            // 一致時のみ、按分後の金額を確定値としても反映（CSV出力はこちらを使用）
            s.setTaxIncludedAmount(s.getTaxIncludedAmountChange());
            s.setTaxExcludedAmount(s.getTaxExcludedAmountChange());
        }
    }

    private void applyMismatch(List<TAccountsReceivableSummary> group,
                               BigDecimal invoiceAmount, BigDecimal diff, Integer invoiceId) {
        // 不一致時も行単位に按分して表示する（ユーザーが税率別に原因を追えるように）。
        Map<TAccountsReceivableSummary, BigDecimal> allocated = allocateInvoiceByArRatio(group, invoiceAmount);
        for (TAccountsReceivableSummary s : group) {
            s.setVerificationResult(0);
            s.setMfExportEnabled(Boolean.FALSE);
            s.setInvoiceAmount(allocated.get(s));
            BigDecimal rowAr = s.getTaxIncludedAmountChange() != null
                    ? s.getTaxIncludedAmountChange() : BigDecimal.ZERO;
            s.setVerificationDifference(allocated.get(s).subtract(rowAr));
            s.setInvoiceNo(invoiceId);
            // 不一致時は確定金額を更新しない（手動確定が来るまで待つ）
        }
    }

    /**
     * 請求書合計額 ({@code invoiceAmount}) を各行の {@code taxIncludedAmountChange} 比率で按分する。
     * 端数は最大行で吸収して SUM が合計と一致することを保証する。
     * <p>一致時は group の taxIncludedAmountChange が既に按分済みのため、各行の按分額 = taxIncludedAmountChange になる。
     */
    private Map<TAccountsReceivableSummary, BigDecimal> allocateInvoiceByArRatio(
            List<TAccountsReceivableSummary> group, BigDecimal invoiceAmount) {
        Map<TAccountsReceivableSummary, BigDecimal> out = new java.util.LinkedHashMap<>();
        BigDecimal arTotal = group.stream()
                .map(s -> s.getTaxIncludedAmountChange() != null ? s.getTaxIncludedAmountChange() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (arTotal.compareTo(BigDecimal.ZERO) == 0) {
            // AR 合計が 0 なのに請求書金額が存在する = データ破損。
            // 均等割で invoice を分配すると 0 円 AR 行に請求書金額だけ付く歪な状態になり、
            // 後続の taxIncludedAmount = taxIncludedAmountChange で 0 円のまま保存されるため
            // 異常として applyMismatch 側で扱うのが正しい (B-W10)。
            // ここでは全行 0 を返し、呼び出し側の applyMismatch 側で mf_export_enabled=false に落とす。
            log.error("AR 税込合計が 0 だが請求書金額が存在する異常データ: invoice={} group={}件",
                    invoiceAmount, group.size());
            for (TAccountsReceivableSummary s : group) {
                out.put(s, BigDecimal.ZERO);
            }
            return out;
        }

        // 按分（税込 AR 比率）。残差は最大行で吸収。
        TAccountsReceivableSummary largest = group.stream()
                .max(Comparator.comparing(s -> s.getTaxIncludedAmountChange() != null
                        ? s.getTaxIncludedAmountChange() : BigDecimal.ZERO))
                .orElse(null);
        BigDecimal allocated = BigDecimal.ZERO;
        for (TAccountsReceivableSummary s : group) {
            if (s == largest) continue;
            BigDecimal rowAr = s.getTaxIncludedAmountChange() != null
                    ? s.getTaxIncludedAmountChange() : BigDecimal.ZERO;
            BigDecimal share = rowAr.multiply(invoiceAmount)
                    .divide(arTotal, 0, RoundingMode.DOWN);
            out.put(s, share);
            allocated = allocated.add(share);
        }
        if (largest != null) {
            out.put(largest, invoiceAmount.subtract(allocated));
        }
        return out;
    }

    private void applyNotFound(List<TAccountsReceivableSummary> group) {
        for (TAccountsReceivableSummary s : group) {
            s.setVerificationResult(0);
            s.setMfExportEnabled(Boolean.FALSE);
            s.setInvoiceAmount(null);
            s.setVerificationDifference(null);
            s.setInvoiceNo(null);
        }
    }

    @Getter
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    private static class InvoiceValidationKey {
        private final Integer shopNo;
        private final String partnerCode;
        private final String closingDateStr;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\TInvoiceService.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\MPartnerGroupService.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\api\\finance\\FinanceController.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path frontend\\components\\pages\\finance\\invoices.tsx" in C:\project\odamitsu-data-hub
 succeeded in 1726ms:
package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.TInvoice;
import jp.co.oda32.domain.repository.finance.TInvoiceRepository;
import jp.co.oda32.domain.specification.finance.TInvoiceSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class TInvoiceService {

    private final TInvoiceRepository tInvoiceRepository;
    private final TInvoiceSpecification tInvoiceSpecification = new TInvoiceSpecification();

    @Autowired
    public TInvoiceService(TInvoiceRepository tInvoiceRepository) {
        this.tInvoiceRepository = tInvoiceRepository;
    }

    /**
     * 請求データを全て取得
     *
     * @return 請求データのリスト
     */
    public List<TInvoice> getAllInvoices() {
        return tInvoiceRepository.findAll();
    }

    /**
     * 特定の請求データを取得
     *
     * @param partnerCode 得意先コード
     * @param closingDate 締め日
     * @return 該当する請求データのリスト
     */
    public List<TInvoice> getInvoicesByPartnerCodeAndClosingDate(String partnerCode, String closingDate) {
        return tInvoiceRepository.findByPartnerCodeAndClosingDate(partnerCode, closingDate);
    }

    /**
     * 新しい請求データを保存
     *
     * @param invoice 保存する請求データ
     * @return 保存された請求データ
     */
    @Transactional
    public TInvoice saveInvoice(TInvoice invoice) {
        return tInvoiceRepository.save(invoice);
    }

    /**
     * 請求データを新規登録または更新
     *
     * @param invoice 保存する請求データ
     * @return 保存された請求データ
     */
    @Transactional
    public TInvoice insert(TInvoice invoice) {
        return tInvoiceRepository.save(invoice);
    }

    /**
     * 請求データを削除
     *
     * @param invoiceId 削除する請求ID
     */
    @Transactional
    public void deleteInvoiceById(Integer invoiceId) {
        tInvoiceRepository.deleteById(invoiceId);
    }

    /**
     * ショップ番号、得意先コード、締め日で請求データを取得
     *
     * @param shopNo      ショップ番号
     * @param partnerCode 得意先コード
     * @param closingDate 締め日（文字列形式 "yyyy/MM/末" または "yyyy/MM/dd"）
     * @return 該当する請求データ（存在しない場合はEmpty）
     */
    public Optional<TInvoice> findByShopNoAndPartnerCodeAndClosingDate(Integer shopNo, String partnerCode, String closingDate) {
        return tInvoiceRepository.findByShopNoAndPartnerCodeAndClosingDate(shopNo, partnerCode, closingDate);
    }

    public boolean existsByPartnerCodeAndClosingDateAndShopNo(String partnerCode, String closingDate, Integer shopNo) {
        return tInvoiceRepository.existsByPartnerCodeAndClosingDateAndShopNo(partnerCode, closingDate, shopNo);
    }

    /**
     * 得意先コードで請求データを検索
     *
     * @param partnerCode 得意先コード
     * @return 該当する請求データのリスト
     */
    public List<TInvoice> findByPartnerCode(String partnerCode) {
        return tInvoiceRepository.findByPartnerCode(partnerCode);
    }

    /**
     * IDで請求データを1件取得
     *
     * @param invoiceId 請求ID
     * @return 該当する請求データ
     */
    public TInvoice getInvoiceById(Integer invoiceId) {
        return tInvoiceRepository.findById(invoiceId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<TInvoice> findByIds(List<Integer> invoiceIds) {
        return tInvoiceRepository.findAllById(invoiceIds);
    }

    @Transactional
    public List<TInvoice> saveAll(List<TInvoice> invoices) {
        return tInvoiceRepository.saveAll(invoices);
    }

    /**
     * 締月、ショップ番号、得意先コードで請求データを検索する
     * Specificationの代わりにStartingWithを使用した実装
     *
     * @param closingDate 締月（yyyy/MM形式で検索、例：2025/04）
     * @param shopNo      ショップ番号
     * @param partnerCode 得意先コード
     * @return 検索条件に合致する請求データのリスト
     */
    public List<TInvoice> findBySpecification(String closingDate, Integer shopNo, String partnerCode) {
        return findByDetailedSpecification(closingDate, shopNo, partnerCode, null, null, null);
    }

    /**
     * 詳細検索条件に基づいて請求データを検索する
     *
     * @param closingDate 締月（yyyy/MM形式で検索、例：2025/04）
     * @param shopNo      ショップ番号
     * @param partnerCode 得意先コード
     * @param partnerName 顧客名
     * @param minAmount   最小請求額
     * @param maxAmount   最大請求額
     * @return 検索条件に合致する請求データのリスト（締日降順）
     */
    public List<TInvoice> findByDetailedSpecification(
            String closingDate,
            Integer shopNo,
            String partnerCode,
            String partnerName,
            Double minAmount,
            Double maxAmount) {

        Specification<TInvoice> spec = Specification
                .where(tInvoiceSpecification.closingDateContains(closingDate))
                .and(tInvoiceSpecification.shopNoContains(shopNo))
                .and(tInvoiceSpecification.partnerCodeContains(partnerCode))
                .and(tInvoiceSpecification.partnerNameContains(partnerName))
                .and(tInvoiceSpecification.currentBillingAmountContains(minAmount, maxAmount));

        // 締日降順でソート
        return tInvoiceRepository.findAll(spec, org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Direction.DESC, "closingDate"
        ));
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1792ms:
package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.MPartnerGroup;
import jp.co.oda32.domain.repository.finance.MPartnerGroupRepository;
import jp.co.oda32.dto.finance.PartnerGroupRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MPartnerGroupService {

    private final MPartnerGroupRepository repository;

    @Transactional(readOnly = true)
    public List<MPartnerGroup> findByShopNo(Integer shopNo) {
        return shopNo != null
                ? repository.findByShopNoOrderByGroupNameAsc(shopNo)
                : repository.findAll();
    }

    @Transactional(readOnly = true)
    public MPartnerGroup findById(Integer id) {
        return repository.findById(id).orElse(null);
    }

    @Transactional
    public MPartnerGroup create(PartnerGroupRequest request) {
        MPartnerGroup group = MPartnerGroup.builder()
                .groupName(request.getGroupName())
                .shopNo(request.getShopNo())
                .partnerCodes(request.getPartnerCodes())
                .build();
        return repository.save(group);
    }

    @Transactional
    public MPartnerGroup update(Integer id, PartnerGroupRequest request) {
        MPartnerGroup group = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("グループが見つかりません: id=" + id));
        group.setGroupName(request.getGroupName());
        group.getPartnerCodes().clear();
        group.getPartnerCodes().addAll(request.getPartnerCodes());
        return repository.save(group);
    }

    @Transactional
    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("グループが見つかりません: id=" + id);
        }
        repository.deleteById(id);
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1860ms:
'use client'

import { useState, useMemo, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops } from '@/hooks/use-master-data'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchForm } from '@/components/features/common/SearchForm'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { formatNumber } from '@/lib/utils'
import { Upload, Plus } from 'lucide-react'
import { toast } from 'sonner'
import { InvoiceImportDialog } from './InvoiceImportDialog'
import { PartnerGroupDialog } from './PartnerGroupDialog'

// ==================== Types ====================

interface Invoice {
  invoiceId: number
  partnerCode: string
  partnerName: string
  closingDate: string
  previousBalance: number | null
  totalPayment: number | null
  carryOverBalance: number | null
  netSales: number | null
  taxPrice: number | null
  netSalesIncludingTax: number | null
  currentBillingAmount: number | null
  shopNo: number
  paymentDate: string | null
}

interface PartnerGroup {
  partnerGroupId: number
  groupName: string
  shopNo: number
  partnerCodes: string[]
}

function formatMoney(val: number | null): string {
  if (val == null) return '-'
  return formatNumber(val)
}

// F1: Checkbox cell extracted to avoid full-table re-render on toggle
function SelectCell({ invoiceId, selectedIds, onToggle }: {
  invoiceId: number
  selectedIds: Set<number>
  onToggle: (id: number) => void
}) {
  return (
    <Checkbox
      checked={selectedIds.has(invoiceId)}
      onCheckedChange={() => onToggle(invoiceId)}
      onClick={(e) => e.stopPropagation()}
    />
  )
}

// F2: Date cell with key to force remount on data refresh
function PaymentDateCell({ invoiceId, paymentDate, onUpdate }: {
  invoiceId: number
  paymentDate: string | null
  onUpdate: (id: number, val: string) => void
}) {
  return (
    <Input
      key={`${invoiceId}-${paymentDate ?? ''}`}
      type="date"
      defaultValue={paymentDate ?? ''}
      className="h-7 w-36 text-sm"
      onClick={(e) => e.stopPropagation()}
      onBlur={(e) => {
        const newVal = e.target.value
        if (newVal !== (paymentDate ?? '')) {
          onUpdate(invoiceId, newVal)
        }
      }}
    />
  )
}

// ==================== Main Component ====================

export function InvoiceListPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const isAdmin = user?.shopNo === 0
  const userShopNo = user?.shopNo ?? 1

  // Search state
  const [shopNo, setShopNo] = useState<string>(isAdmin ? '' : String(userShopNo))
  const [closingDate, setClosingDate] = useState('')
  const [partnerCode, setPartnerCode] = useState('')
  const [partnerName, setPartnerName] = useState('')
  const [searchParams, setSearchParams] = useState<Record<string, string> | null>(null)

  // Selection state
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())
  const [bulkPaymentDate, setBulkPaymentDate] = useState('')
  const [filterGroupId, setFilterGroupId] = useState<string | null>(null)

  // Dialog state
  const [importDialogOpen, setImportDialogOpen] = useState(false)
  const [groupDialogOpen, setGroupDialogOpen] = useState(false)

  const shopsQuery = useShops(isAdmin)

  const invoiceQuery = useQuery({
    queryKey: ['invoices', searchParams],
    queryFn: () => {
      const params = new URLSearchParams()
      if (searchParams?.shopNo) params.append('shopNo', searchParams.shopNo)
      if (searchParams?.closingDate) params.append('closingDate', searchParams.closingDate)
      if (searchParams?.partnerCode) params.append('partnerCode', searchParams.partnerCode)
      if (searchParams?.partnerName) params.append('partnerName', searchParams.partnerName)
      const qs = params.toString()
      return api.get<Invoice[]>(`/finance/invoices${qs ? `?${qs}` : ''}`)
    },
    enabled: searchParams !== null,
  })

  const effectiveShopNo = searchParams?.shopNo ? Number(searchParams.shopNo) : userShopNo
  const groupsQuery = useQuery({
    queryKey: ['partner-groups', effectiveShopNo],
    queryFn: () => api.get<PartnerGroup[]>(`/finance/partner-groups?shopNo=${effectiveShopNo}`),
  })

  // F6: use .mutate directly (stable ref per TanStack Query)
  const paymentDateMutation = useMutation({
    mutationFn: ({ invoiceId, paymentDate }: { invoiceId: number; paymentDate: string | null }) =>
      api.put<Invoice>(`/finance/invoices/${invoiceId}/payment-date`, { paymentDate }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
    },
    onError: () => {
      toast.error('入金日の更新に失敗しました')
    },
  })

  const bulkPaymentDateMutation = useMutation({
    mutationFn: (data: { invoiceIds: number[]; paymentDate: string }) =>
      api.put<{ updatedCount: number }>('/finance/invoices/bulk-payment-date', data),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
      setSelectedIds(new Set())
      setBulkPaymentDate('')
      setFilterGroupId(null)
      toast.success(`${result.updatedCount}件の入金日を更新しました`)
    },
    onError: () => {
      toast.error('一括更新に失敗しました')
    },
  })

  // ==================== Handlers ====================

  const handlePaymentDateChange = useCallback((invoiceId: number, value: string) => {
    paymentDateMutation.mutate({ invoiceId, paymentDate: value || null })
  }, [paymentDateMutation])

  const handleSearch = () => {
    setSelectedIds(new Set())
    setFilterGroupId(null)
    setSearchParams({
      shopNo,
      closingDate: closingDate ? closingDate.replaceAll('-', '/') : '',
      partnerCode,
      partnerName,
    })
  }

  const handleReset = () => {
    setClosingDate('')
    setPartnerCode('')
    setPartnerName('')
    setSearchParams(null)
    setSelectedIds(new Set())
    setFilterGroupId(null)
  }

  const toggleSelect = (invoiceId: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(invoiceId)) { next.delete(invoiceId) } else { next.add(invoiceId) }
      return next
    })
  }

  // F3: memoize filtered data with Set for O(1) lookup
  const allInvoices = invoiceQuery.data ?? []
  const filterGroup = filterGroupId
    ? (groupsQuery.data ?? []).find((g) => String(g.partnerGroupId) === filterGroupId)
    : null

  const invoices = useMemo(() => {
    if (!filterGroup) return allInvoices
    const codeSet = new Set(filterGroup.partnerCodes)
    return allInvoices.filter((inv) => codeSet.has(inv.partnerCode))
  }, [allInvoices, filterGroup])

  const allSelected = invoices.length > 0 && invoices.every((inv) => selectedIds.has(inv.invoiceId))

  // F5: toggleSelectAll operates on filtered invoices
  const toggleSelectAll = () => {
    if (allSelected) {
      setSelectedIds(new Set())
    } else {
      setSelectedIds(new Set(invoices.map((inv) => inv.invoiceId)))
    }
  }

  const handleGroupSelect = (groupId: string) => {
    if (groupId === '__none__') {
      setSelectedIds(new Set())
      setFilterGroupId(null)
      return
    }
    const group = (groupsQuery.data ?? []).find((g) => String(g.partnerGroupId) === groupId)
    if (!group) return
    setFilterGroupId(groupId)
    const codeSet = new Set(group.partnerCodes)
    const matchingIds = allInvoices
      .filter((inv) => codeSet.has(inv.partnerCode))
      .map((inv) => inv.invoiceId)
    setSelectedIds(new Set(matchingIds))
  }

  const handleBulkPaymentDate = () => {
    if (selectedIds.size === 0 || !bulkPaymentDate) return
    bulkPaymentDateMutation.mutate({
      invoiceIds: Array.from(selectedIds),
      paymentDate: bulkPaymentDate,
    })
  }

  // F1: columns without selectedIds dependency ? checkbox uses extracted SelectCell
  const columns: Column<Invoice>[] = useMemo(() => [
    {
      key: '_select',
      header: '',
      render: (item: Invoice) => (
        <SelectCell invoiceId={item.invoiceId} selectedIds={selectedIds} onToggle={toggleSelect} />
      ),
    },
    {
      key: 'partnerCode',
      header: '得意先',
      sortable: true,
      render: (item: Invoice) => (
        <div>
          <div className="text-xs text-muted-foreground">{item.partnerCode}</div>
          <div>{item.partnerName}</div>
        </div>
      ),
    },
    { key: 'closingDate', header: '締日', sortable: true },
    {
      key: 'previousBalance',
      header: '前回残高 / 入金',
      render: (item: Invoice) => (
        <div className="text-right tabular-nums">
          <div>{formatMoney(item.previousBalance)}</div>
          <div className="text-xs text-muted-foreground">{formatMoney(item.totalPayment)}</div>
        </div>
      ),
    },
    {
      key: 'carryOverBalance',
      header: '繰越残高',
      render: (item: Invoice) => <span className="block text-right tabular-nums">{formatMoney(item.carryOverBalance)}</span>,
    },
    {
      key: 'netSalesIncludingTax',
      header: '税込売上',
      render: (item: Invoice) => <span className="block text-right tabular-nums">{formatMoney(item.netSalesIncludingTax)}</span>,
    },
    {
      key: 'currentBillingAmount',
      header: '今回請求額',
      render: (item: Invoice) => <span className="block text-right tabular-nums font-bold">{formatMoney(item.currentBillingAmount)}</span>,
      sortable: true,
    },
    {
      key: 'paymentDate',
      header: '入金日',
      render: (item: Invoice) => (
        <PaymentDateCell invoiceId={item.invoiceId} paymentDate={item.paymentDate} onUpdate={handlePaymentDateChange} />
      ),
    },
  ], []) // eslint-disable-line react-hooks/exhaustive-deps -- render functions use closures that read latest state via SelectCell/PaymentDateCell props

  const hasSearched = searchParams !== null

  return (
    <div className="space-y-6">
      <PageHeader
        title="請求書一覧"
        actions={
          <Button variant="outline" onClick={() => setImportDialogOpen(true)}>
            <Upload className="mr-2 h-4 w-4" />
            インポート
          </Button>
        }
      />

      <SearchForm onSearch={handleSearch} onReset={handleReset}>
        {isAdmin && (
          <div className="space-y-2">
            <Label>ショップ</Label>
            <Select value={shopNo} onValueChange={setShopNo}>
              <SelectTrigger>
                <SelectValue placeholder="全て" />
              </SelectTrigger>
              <SelectContent>
                {(shopsQuery.data ?? []).map((shop) => (
                  <SelectItem key={shop.shopNo} value={String(shop.shopNo)}>
                    {shop.shopName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}
        <div className="space-y-2">
          <Label>締月</Label>
          <Input type="month" value={closingDate} onChange={(e) => setClosingDate(e.target.value)} />
        </div>
        <div className="space-y-2">
          <Label>得意先コード</Label>
          <Input placeholder="得意先コードを入力" value={partnerCode} onChange={(e) => setPartnerCode(e.target.value)} />
        </div>
        <div className="space-y-2">
          <Label>得意先名</Label>
          <Input placeholder="得意先名を入力" value={partnerName} onChange={(e) => setPartnerName(e.target.value)} />
        </div>
      </SearchForm>

      {/* Bulk action bar */}
      {hasSearched && invoices.length > 0 && (
        <div className="flex items-center gap-3 rounded-lg border bg-muted/30 px-4 py-3">
          <Checkbox
            checked={allSelected}
            onCheckedChange={toggleSelectAll}
            aria-label="全て選択"
          />
          <span className="text-sm text-muted-foreground">
            {selectedIds.size > 0 ? `${selectedIds.size}件選択中` : '全選択'}
          </span>
          <div className="mx-2 h-5 w-px bg-border" />
          <div className="w-72">
            <SearchableSelect
              value={filterGroupId ?? ''}
              onValueChange={(val) => handleGroupSelect(val || '__none__')}
              options={(groupsQuery.data ?? []).map((g) => ({
                value: String(g.partnerGroupId),
                label: `${g.groupName}（${g.partnerCodes.length}件）`,
              }))}
              placeholder="グループ選択"
              searchPlaceholder="グループ名を検索..."
              emptyMessage="グループが見つかりません"
            />
          </div>
          <Button variant="ghost" size="sm" onClick={() => setGroupDialogOpen(true)}>
            <Plus className="mr-1 h-3 w-3" />
            グループ管理
          </Button>
          <div className="ml-auto flex items-center gap-2">
            <Input
              type="date"
              value={bulkPaymentDate}
              onChange={(e) => setBulkPaymentDate(e.target.value)}
              className="h-8 w-40 text-sm"
            />
            <Button
              size="sm"
              onClick={handleBulkPaymentDate}
              disabled={selectedIds.size === 0 || !bulkPaymentDate || bulkPaymentDateMutation.isPending}
            >
              {bulkPaymentDateMutation.isPending ? '更新中...' : `一括反映（${selectedIds.size}件）`}
            </Button>
          </div>
        </div>
      )}

      {/* グループ合計 */}
      {filterGroup && invoices.length > 0 && (
        <div className="flex items-center gap-6 rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm dark:border-blue-900 dark:bg-blue-950">
          <span className="font-medium">{filterGroup.groupName} 合計（{invoices.length}件）</span>
          <div className="flex items-center gap-1">
            <span className="text-muted-foreground">税込売上:</span>
            <span className="tabular-nums font-medium">
              {formatMoney(invoices.reduce((sum, inv) => sum + (inv.netSalesIncludingTax ?? 0), 0))}
            </span>
          </div>
          <div className="flex items-center gap-1">
            <span className="text-muted-foreground">今回請求額:</span>
            <span className="tabular-nums font-bold">
              {formatMoney(invoices.reduce((sum, inv) => sum + (inv.currentBillingAmount ?? 0), 0))}
            </span>
          </div>
        </div>
      )}

      {!hasSearched ? (
        <div className="rounded-lg border p-8 text-center text-muted-foreground">
          検索条件を入力して「検索」ボタンを押してください
        </div>
      ) : invoiceQuery.isLoading ? (
        <LoadingSpinner />
      ) : invoiceQuery.isError ? (
        <ErrorMessage onRetry={() => invoiceQuery.refetch()} />
      ) : (
        <DataTable data={invoices} columns={columns} searchPlaceholder="テーブル内を検索..." />
      )}

      <InvoiceImportDialog open={importDialogOpen} onOpenChange={setImportDialogOpen} />
      <PartnerGroupDialog
        open={groupDialogOpen}
        onOpenChange={setGroupDialogOpen}
        groups={groupsQuery.data ?? []}
        shopNo={effectiveShopNo}
      />
    </div>
  )
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2872ms:
package jp.co.oda32.api.finance;

import jakarta.validation.Valid;
import jp.co.oda32.domain.model.finance.MPartnerGroup;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.finance.TInvoice;
import jp.co.oda32.domain.service.finance.AccountingStatusService;
import jp.co.oda32.domain.service.finance.AccountsPayableIntegrityService;
import jp.co.oda32.domain.service.finance.AccountsPayableLedgerService;
import jp.co.oda32.domain.service.finance.ConsistencyReviewService;
import jp.co.oda32.domain.service.finance.InvoiceImportService;
import jp.co.oda32.domain.service.finance.MPartnerGroupService;
import jp.co.oda32.domain.service.finance.MfHealthCheckService;
import jp.co.oda32.domain.service.finance.PurchaseJournalCsvService;
import jp.co.oda32.domain.service.finance.SupplierBalancesService;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import jp.co.oda32.domain.service.finance.TInvoiceService;
import jp.co.oda32.domain.service.finance.mf.MfJournalCacheService;
import jp.co.oda32.domain.service.finance.mf.MfReAuthRequiredException;
import jp.co.oda32.domain.service.finance.mf.MfScopeInsufficientException;
import jp.co.oda32.domain.service.finance.mf.MfSupplierLedgerService;
import jp.co.oda32.domain.service.util.LoginUserUtil;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import jp.co.oda32.dto.finance.AccountsPayableLedgerResponse;
import jp.co.oda32.dto.finance.AccountsPayableResponse;
import jp.co.oda32.dto.finance.AccountsPayableSummaryResponse;
import jp.co.oda32.dto.finance.IntegrityReportResponse;
import jp.co.oda32.dto.finance.MfHealthResponse;
import jp.co.oda32.dto.finance.MfSupplierLedgerResponse;
import jp.co.oda32.dto.finance.SupplierBalancesResponse;
import jp.co.oda32.dto.finance.AccountsPayableVerifyRequest;
import jp.co.oda32.dto.finance.BulkPaymentDateRequest;
import jp.co.oda32.dto.finance.ConsistencyReviewRequest;
import jp.co.oda32.dto.finance.ConsistencyReviewResponse;
import jp.co.oda32.dto.finance.MfExportToggleRequest;
import jp.co.oda32.dto.finance.InvoiceImportResult;
import jp.co.oda32.dto.finance.InvoiceResponse;
import jp.co.oda32.dto.finance.PartnerGroupRequest;
import jp.co.oda32.dto.finance.PartnerGroupResponse;
import jp.co.oda32.dto.finance.PaymentDateUpdateRequest;
import jp.co.oda32.dto.finance.PurchaseJournalExportPreviewResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;

@Slf4j
@RestController
@RequestMapping("/api/v1/finance")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class FinanceController {

    private static final Charset CP932 = Charset.forName("windows-31j");

    private final TAccountsPayableSummaryService accountsPayableSummaryService;
    private final TInvoiceService tInvoiceService;
    private final InvoiceImportService invoiceImportService;
    private final MPartnerGroupService partnerGroupService;
    private final AccountingStatusService accountingStatusService;
    private final MPaymentSupplierService mPaymentSupplierService;
    private final PurchaseJournalCsvService purchaseJournalCsvService;
    private final AccountsPayableLedgerService accountsPayableLedgerService;
    private final MfSupplierLedgerService mfSupplierLedgerService;
    private final AccountsPayableIntegrityService accountsPayableIntegrityService;
    private final SupplierBalancesService supplierBalancesService;
    private final MfHealthCheckService mfHealthCheckService;
    private final MfJournalCacheService mfJournalCacheService;
    private final ConsistencyReviewService consistencyReviewService;

    @GetMapping("/accounts-payable")
    public ResponseEntity<Page<AccountsPayableResponse>> listAccountsPayable(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) Integer supplierNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth,
            @RequestParam(required = false) String verificationFilter,
            @RequestParam(required = false) String include,
            @PageableDefault(size = 50, sort = "supplierCode", direction = Sort.Direction.ASC) Pageable pageable) {
        Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(shopNo);
        boolean includeBalance = include != null
                && java.util.Arrays.stream(include.split(","))
                        .map(String::trim)
                        .anyMatch("balance"::equalsIgnoreCase);
        Page<TAccountsPayableSummary> page = accountsPayableSummaryService.findPaged(
                effectiveShopNo, supplierNo, transactionMonth, verificationFilter, pageable);

        Set<Integer> supplierNos = page.getContent().stream()
                .map(TAccountsPayableSummary::getSupplierNo)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, MPaymentSupplier> psMap = mPaymentSupplierService.findAllByPaymentSupplierNos(supplierNos).stream()
                .collect(Collectors.toMap(MPaymentSupplier::getPaymentSupplierNo, p -> p, (a, b) -> a));

        return ResponseEntity.ok(page.map(ap -> AccountsPayableResponse.from(
                ap, psMap.get(ap.getSupplierNo()), includeBalance)));
    }

    @GetMapping("/accounts-payable/summary")
    public ResponseEntity<AccountsPayableSummaryResponse> getAccountsPayableSummary(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth) {
        Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(shopNo);
        return ResponseEntity.ok(accountsPayableSummaryService.summary(effectiveShopNo, transactionMonth));
    }

    @PutMapping("/accounts-payable/{shopNo}/{supplierNo}/{transactionMonth}/{taxRate}/verify")
    public ResponseEntity<AccountsPayableResponse> verifyAccountsPayable(
            @PathVariable Integer shopNo,
            @PathVariable Integer supplierNo,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth,
            @PathVariable BigDecimal taxRate,
            @Valid @RequestBody AccountsPayableVerifyRequest request) {
        assertShopAccess(shopNo);
        TAccountsPayableSummary updated = accountsPayableSummaryService.verify(
                shopNo, supplierNo, transactionMonth, taxRate,
                request.getVerifiedAmount(), request.getNote());
        MPaymentSupplier ps = mPaymentSupplierService.getByPaymentSupplierNo(supplierNo);
        return ResponseEntity.ok(AccountsPayableResponse.from(updated, ps));
    }

    @DeleteMapping("/accounts-payable/{shopNo}/{supplierNo}/{transactionMonth}/{taxRate}/manual-lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountsPayableResponse> releaseManualLock(
            @PathVariable Integer shopNo,
            @PathVariable Integer supplierNo,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth,
            @PathVariable BigDecimal taxRate) {
        assertShopAccess(shopNo);
        TAccountsPayableSummary updated = accountsPayableSummaryService.releaseManualLock(
                shopNo, supplierNo, transactionMonth, taxRate);
        MPaymentSupplier ps = mPaymentSupplierService.getByPaymentSupplierNo(supplierNo);
        return ResponseEntity.ok(AccountsPayableResponse.from(updated, ps));
    }

    @PatchMapping("/accounts-payable/{shopNo}/{supplierNo}/{transactionMonth}/{taxRate}/mf-export")
    public ResponseEntity<AccountsPayableResponse> toggleMfExport(
            @PathVariable Integer shopNo,
            @PathVariable Integer supplierNo,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth,
            @PathVariable BigDecimal taxRate,
            @Valid @RequestBody MfExportToggleRequest request) {
        assertShopAccess(shopNo);
        TAccountsPayableSummary updated = accountsPayableSummaryService.updateMfExport(
                shopNo, supplierNo, transactionMonth, taxRate, request.getEnabled());
        MPaymentSupplier ps = mPaymentSupplierService.getByPaymentSupplierNo(supplierNo);
        return ResponseEntity.ok(AccountsPayableResponse.from(updated, ps));
    }

    private void assertShopAccess(Integer shopNo) {
        Integer effective = LoginUserUtil.resolveEffectiveShopNo(shopNo);
        if (effective != null && !effective.equals(shopNo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "他ショップのデータにはアクセスできません");
        }
    }

    // -------- 買掛帳 (1 仕入先の月次推移) --------

    /**
     * 1 仕入先について指定期間 (最大 24 ヶ月) の月次 ledger を返す。
     * 設計書: claudedocs/design-accounts-payable-ledger.md §4.1
     *
     * @since 2026-04-22 (買掛帳画面)
     */
    @GetMapping("/accounts-payable/ledger")
    public ResponseEntity<AccountsPayableLedgerResponse> getAccountsPayableLedger(
            @RequestParam("shopNo") Integer shopNo,
            @RequestParam("supplierNo") Integer supplierNo,
            @RequestParam("fromMonth") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromMonth,
            @RequestParam("toMonth") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toMonth) {
        assertShopAccess(shopNo);
        AccountsPayableLedgerResponse res = accountsPayableLedgerService.getLedger(shopNo, supplierNo, fromMonth, toMonth);
        return ResponseEntity.ok(res);
    }

    /**
     * 1 仕入先について MF /journals を期間累積で取得し、月次 credit/debit delta を返す。
     * 買掛帳の「MF と比較」ボタンから明示的に呼ばれる想定。
     * 設計書: claudedocs/design-accounts-payable-ledger.md §4.2
     *
     * @since 2026-04-22 (買掛帳画面)
     */
    /**
     * 買掛帳 整合性検出 (軸 B + 軸 C): 全 supplier 一括診断。
     * 設計書: claudedocs/design-integrity-report.md
     *
     * @since 2026-04-22
     */
    @GetMapping("/accounts-payable/integrity-report")
    public ResponseEntity<?> getIntegrityReport(
            @RequestParam("shopNo") Integer shopNo,
            @RequestParam("fromMonth") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromMonth,
            @RequestParam("toMonth") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toMonth,
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {
        assertShopAccess(shopNo);
        try {
            IntegrityReportResponse res = accountsPayableIntegrityService.generate(shopNo, fromMonth, toMonth, refresh);
            return ResponseEntity.ok(res);
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (MfScopeInsufficientException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "message", e.getMessage(),
                    "requiredScope", e.getRequiredScope()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 軸 D: supplier 累積残一覧。
     * 期首 (2025-05-20) ? asOfMonth の全 supplier 累積残を自社 / MF で突合し、
     * MATCH / MINOR / MAJOR / MF_MISSING / SELF_MISSING で分類。
     *
     * @param shopNo     ショップ番号 (必須)
     * @param asOfMonth  基準月 (20日締め、省略時は最新月)
     * @param refresh    true で対象期間の MF journals キャッシュを discard → 再取得
     * @since 2026-04-23
     */
    @GetMapping("/accounts-payable/supplier-balances")
    public ResponseEntity<?> getSupplierBalances(
            @RequestParam("shopNo") Integer shopNo,
            @RequestParam(value = "asOfMonth", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfMonth,
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {
        assertShopAccess(shopNo);
        try {
            SupplierBalancesResponse res = supplierBalancesService.generate(shopNo, asOfMonth, refresh);
            return ResponseEntity.ok(res);
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (MfScopeInsufficientException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "message", e.getMessage(),
                    "requiredScope", e.getRequiredScope()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 差分確認 (案 X+Y): 整合性レポートの差分行に IGNORE / MF_APPLY を記録。
     * @since 2026-04-23
     */
    @PostMapping("/accounts-payable/integrity-report/reviews")
    public ResponseEntity<ConsistencyReviewResponse> saveConsistencyReview(
            @RequestBody @Valid ConsistencyReviewRequest req) throws Exception {
        assertShopAccess(req.getShopNo());
        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
        ConsistencyReviewResponse res = consistencyReviewService.upsert(req, userNo);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @DeleteMapping("/accounts-payable/integrity-report/reviews")
    public ResponseEntity<Void> deleteConsistencyReview(
            @RequestParam("shopNo") Integer shopNo,
            @RequestParam("entryType") String entryType,
            @RequestParam("entryKey") String entryKey,
            @RequestParam("transactionMonth")
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth) {
        assertShopAccess(shopNo);
        consistencyReviewService.delete(shopNo, entryType, entryKey, transactionMonth);
        return ResponseEntity.noContent().build();
    }

    /**
     * 軸 E: MF 連携ヘルスチェック。
     * MF OAuth 状態 / 買掛金サマリ集計 / anomaly / journals cache を 1 レスポンスで返す。
     */
    @GetMapping("/mf-health")
    public ResponseEntity<MfHealthResponse> getMfHealth(@RequestParam("shopNo") Integer shopNo) {
        assertShopAccess(shopNo);
        return ResponseEntity.ok(mfHealthCheckService.check(shopNo));
    }

    /**
     * 軸 E: MF journals キャッシュを shop 単位で全破棄。
     */
    @PostMapping("/mf-health/cache/invalidate")
    public ResponseEntity<Void> invalidateMfCache(@RequestParam("shopNo") Integer shopNo) {
        assertShopAccess(shopNo);
        mfJournalCacheService.invalidateAll(shopNo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/accounts-payable/ledger/mf")
    public ResponseEntity<?> getMfSupplierLedger(
            @RequestParam("shopNo") Integer shopNo,
            @RequestParam("supplierNo") Integer supplierNo,
            @RequestParam("fromMonth") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromMonth,
            @RequestParam("toMonth") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toMonth,
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {
        assertShopAccess(shopNo);
        try {
            MfSupplierLedgerResponse res = mfSupplierLedgerService.getSupplierLedger(shopNo, supplierNo, fromMonth, toMonth, refresh);
            return ResponseEntity.ok(res);
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (MfScopeInsufficientException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "message", e.getMessage(),
                    "requiredScope", e.getRequiredScope()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", e.getMessage()));
        }
    }

    // -------- 買掛→仕入仕訳 CSV（MF）--------

    /**
     * 指定取引月の買掛金サマリから、買掛→仕入仕訳 MF CSV をブラウザに直接ダウンロードさせる。
     * バッチ {@code purchaseJournalIntegration} と同じロジックだが、
     * サーバー上のファイル書き出しではなくレスポンスとして返す。
     * <p>出力行は supplier × taxRate で集約され、借方「仕入高」/ 貸方「買掛金」の仕訳になる。
     *
     * @param transactionMonth 対象取引月 (yyyy-MM-dd)
     * @param forceExport      true の場合 MF出力OFF の行も含めて出力（未検証含む）
     */
    @GetMapping("/accounts-payable/export-purchase-journal")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InputStreamResource> exportPurchaseJournalCsv(
            @RequestParam("transactionMonth") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth,
            @RequestParam(defaultValue = "false") boolean forceExport) throws Exception {
        List<TAccountsPayableSummary> summaries =
                accountsPayableSummaryService.findByTransactionMonth(transactionMonth);
        PurchaseJournalCsvService.FilterResult filtered =
                purchaseJournalCsvService.filter(summaries, forceExport);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PurchaseJournalCsvService.Result result;
        try (OutputStreamWriter w = new OutputStreamWriter(baos, CP932)) {
            result = purchaseJournalCsvService.writeCsv(filtered.exportable, transactionMonth, w, null);
        }

        if (result.rowCount == 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "出力対象のデータがありません（MF出力ON かつ 差額 0 円以外の買掛金が存在しない）");
        }

        // CSV 出力済みマーカー（tax_included_amount / tax_excluded_amount を *_change からコピー）
        purchaseJournalCsvService.markExported(filtered.exportable);
        accountsPayableSummaryService.saveAll(filtered.exportable);
        log.info("買掛→仕入仕訳CSV DL: transactionMonth={}, rows={}, total={}, skipped={}",
                transactionMonth, result.rowCount, result.totalAmount, result.skippedSuppliers.size());

        String yyyymmdd = transactionMonth.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String suffix = forceExport ? "_UNCHECKED" : "";
        String fileName = "accounts_payable_to_purchase_journal_" + yyyymmdd + suffix + ".csv";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        String skippedHeader = result.skippedSuppliers.stream()
                .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                .reduce((a, b) -> a + "|" + b)
                .orElse("");

        byte[] bytes = baos.toByteArray();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"purchase_journal.csv\"; filename*=UTF-8''" + encoded)
                .header("X-Row-Count", String.valueOf(result.rowCount))
                .header("X-Total-Amount", result.totalAmount.toPlainString())
                .header("X-Skipped-Count", String.valueOf(result.skippedSuppliers.size()))
                .header("X-Skipped-Suppliers", skippedHeader)
                .contentType(MediaType.parseMediaType("text/csv; charset=Shift_JIS"))
                .contentLength(bytes.length)
                .body(new InputStreamResource(new ByteArrayInputStream(bytes)));
    }

    /**
     * 買掛→仕入仕訳 CSV 出力のプレビュー。件数・合計金額・除外件数をダイアログで確認するため。
     * CSV 本体は生成するが捨てる（skippedSuppliers / rowCount を得るため）。
     */
    @GetMapping("/accounts-payable/export-purchase-journal/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PurchaseJournalExportPreviewResponse> exportPurchaseJournalPreview(
            @RequestParam("transactionMonth") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth,
            @RequestParam(defaultValue = "false") boolean forceExport) throws Exception {
        List<TAccountsPayableSummary> summaries =
                accountsPayableSummaryService.findByTransactionMonth(transactionMonth);
        PurchaseJournalCsvService.FilterResult filtered =
                purchaseJournalCsvService.filter(summaries, forceExport);

        PurchaseJournalCsvService.Result result;
        try (java.io.StringWriter sw = new java.io.StringWriter()) {
            result = purchaseJournalCsvService.writeCsv(filtered.exportable, transactionMonth, sw, null);
        }

        return ResponseEntity.ok(PurchaseJournalExportPreviewResponse.builder()
                .transactionMonth(transactionMonth)
                .rowCount(result.rowCount)
                .payableCount(filtered.exportable.size())
                .totalAmount(result.totalAmount)
                .nonExportableCount(filtered.nonExportableCount)
                .skippedSuppliers(result.skippedSuppliers)
                .build());
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<InvoiceResponse>> listInvoices(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) String partnerCode,
            @RequestParam(required = false) String partnerName,
            @RequestParam(required = false) String closingDate) {
        List<TInvoice> invoices = tInvoiceService.findByDetailedSpecification(
                closingDate, shopNo, partnerCode, partnerName, null, null);
        return ResponseEntity.ok(invoices.stream().map(InvoiceResponse::from).collect(Collectors.toList()));
    }

    @PutMapping("/invoices/{invoiceId}/payment-date")
    public ResponseEntity<?> updatePaymentDate(
            @PathVariable Integer invoiceId,
            @Valid @RequestBody PaymentDateUpdateRequest request) {
        TInvoice invoice = tInvoiceService.getInvoiceById(invoiceId);
        if (invoice == null) {
            return ResponseEntity.notFound().build();
        }
        invoice.setPaymentDate(request.getPaymentDate());
        tInvoiceService.saveInvoice(invoice);
        return ResponseEntity.ok(InvoiceResponse.from(invoice));
    }

    @PostMapping("/invoices/import")
    public ResponseEntity<?> importInvoices(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Integer shopNo) {
        try {
            InvoiceImportResult result = invoiceImportService.importFromExcel(file, shopNo);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("請求実績インポートエラー: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("請求実績インポート失敗", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "インポート処理中にエラーが発生しました: " + e.getMessage()));
        }
    }

    @PutMapping("/invoices/bulk-payment-date")
    public ResponseEntity<?> bulkUpdatePaymentDate(@Valid @RequestBody BulkPaymentDateRequest request) {
        List<TInvoice> invoices = tInvoiceService.findByIds(request.getInvoiceIds());
        if (invoices.size() != request.getInvoiceIds().size()) {
            log.warn("入金日一括更新: 要求{}件中{}件が見つかりました",
                    request.getInvoiceIds().size(), invoices.size());
        }
        invoices.forEach(inv -> inv.setPaymentDate(request.getPaymentDate()));
        tInvoiceService.saveAll(invoices);
        log.info("入金日一括更新: {}件, paymentDate={}", invoices.size(), request.getPaymentDate());
        return ResponseEntity.ok(Map.of("updatedCount", invoices.size()));
    }

    // ---- Partner Groups ----

    @GetMapping("/partner-groups")
    public ResponseEntity<List<PartnerGroupResponse>> listPartnerGroups(
            @RequestParam(required = false) Integer shopNo) {
        List<MPartnerGroup> groups = partnerGroupService.findByShopNo(shopNo);
        return ResponseEntity.ok(groups.stream().map(PartnerGroupResponse::from).collect(Collectors.toList()));
    }

    @PostMapping("/partner-groups")
    public ResponseEntity<PartnerGroupResponse> createPartnerGroup(
            @Valid @RequestBody PartnerGroupRequest request) {
        MPartnerGroup group = partnerGroupService.create(request);
        return ResponseEntity.ok(PartnerGroupResponse.from(group));
    }

    @PutMapping("/partner-groups/{id}")
    public ResponseEntity<PartnerGroupResponse> updatePartnerGroup(
            @PathVariable Integer id, @Valid @RequestBody PartnerGroupRequest request) {
        MPartnerGroup group = partnerGroupService.update(id, request);
        return ResponseEntity.ok(PartnerGroupResponse.from(group));
    }

    @DeleteMapping("/partner-groups/{id}")
    public ResponseEntity<?> deletePartnerGroup(@PathVariable Integer id) {
        partnerGroupService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/accounting-status")
    public ResponseEntity<Map<String, Object>> getAccountingStatus() {
        return ResponseEntity.ok(accountingStatusService.getStatus());
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
コード上では `t_invoice` が「Excel の現在スナップショット」と「入金消込台帳」と「売掛検証の正解値」を兼ねています。この多重責務が、既存レビューの個別バグより上位のリスクになっています。
2026-05-04T01:51:07.146342Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$i=1; Get-Content backend'"\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\InvoiceImportService.java | ForEach-Object { '{0,4}: {1}' -f "'$i, $_; $i++ }'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$i=1; Get-Content backend'"\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\InvoiceImportService.java | ForEach-Object { '{0,4}: {1}' -f "'$i, $_; $i++ }' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$i=1; Get-Content backend'"\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\InvoiceImportService.java | ForEach-Object { '{0,4}: {1}' -f "'$i, $_; $i++ }'` rejected: blocked by policy
2026-05-04T01:51:07.150457Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$i=1; Get-Content backend'"\\src\\main\\java\\jp\\co\\oda32\\batch\\finance\\service\\InvoiceVerifier.java | ForEach-Object { '{0,4}: {1}' -f "'$i, $_; $i++ }'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$i=1; Get-Content backend'"\\src\\main\\java\\jp\\co\\oda32\\batch\\finance\\service\\InvoiceVerifier.java | ForEach-Object { '{0,4}: {1}' -f "'$i, $_; $i++ }' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$i=1; Get-Content backend'"\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\TInvoice.java | ForEach-Object { '{0,4}: {1}' -f "'$i, $_; $i++ }' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$i=1; Get-Content backend'"\\src\\main\\java\\jp\\co\\oda32\\api\\finance\\FinanceController.java | ForEach-Object { if ("'$i -ge 420 -and $i -le 505) { '"'{0,4}: {1}' -f "'$i, $_ }; $i++ }' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$i=1; Get-Content backend'"\\src\\main\\java\\jp\\co\\oda32\\batch\\finance\\service\\InvoiceVerifier.java | ForEach-Object { '{0,4}: {1}' -f "'$i, $_; $i++ }'` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$i=1; Get-Content backend'"\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\TInvoice.java | ForEach-Object { '{0,4}: {1}' -f "'$i, $_; $i++ }'` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$i=1; Get-Content backend'"\\src\\main\\java\\jp\\co\\oda32\\api\\finance\\FinanceController.java | ForEach-Object { if ("'$i -ge 420 -and $i -le 505) { '"'{0,4}: {1}' -f "'$i, $_ }; $i++ }'` rejected: blocked by policy
2026-05-04T01:51:07.150970Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$i=1; Get-Content backend'"\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\TInvoice.java | ForEach-Object { '{0,4}: {1}' -f "'$i, $_; $i++ }'` rejected: blocked by policy
2026-05-04T01:51:07.151547Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$i=1; Get-Content backend'"\\src\\main\\java\\jp\\co\\oda32\\api\\finance\\FinanceController.java | ForEach-Object { if ("'$i -ge 420 -and $i -le 505) { '"'{0,4}: {1}' -f "'$i, $_ }; $i++ }'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'rg -n "shopNoParam|parseClosingDate|existingInvoices|existingMap|payment_date|saveAll|InvoiceImportResult|currentBillingAmount" backend/src/main/java/jp/co/oda32/domain/service/finance/InvoiceImportService.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "rg -n \"QUARTERLY|CLEAN_LAB|JOSAMA|verify\\(|buildInvoiceKey|findQuarterlyInvoice|setInvoiceNo|setMfExportEnabled|verifiedManually|invoiceAmountTolerance\" backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "rg -n \"GetMapping\\(\\\"/invoices|payment-date|importInvoices|bulkUpdatePaymentDate|partner-groups|listPartnerGroups|createPartnerGroup|updatePartnerGroup|deletePartnerGroup\" backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'rg -n "class TInvoice|uniqueConstraints|partnerCode|closingDate|previousBalance|currentBillingAmount|shopNo|paymentDate" backend/src/main/java/jp/co/oda32/domain/model/finance/TInvoice.java' in C:\project\odamitsu-data-hub
2026-05-04T01:51:15.690149Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "rg -n \"GetMapping\\(\\\"/invoices|payment-date|importInvoices|bulkUpdatePaymentDate|partner-groups|listPartnerGroups|createPartnerGroup|updatePartnerGroup|deletePartnerGroup\" backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java"` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "rg -n \"GetMapping\\(\\\"/invoices|payment-date|importInvoices|bulkUpdatePaymentDate|partner-groups|listPartnerGroups|createPartnerGroup|updatePartnerGroup|deletePartnerGroup\" backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java"` rejected: blocked by policy
2026-05-04T01:51:19.189712Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 3.5 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg -n "shopNoParam|parseClosingDate|existingInvoices|existingMap|paym 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 exited 1 in 3515ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg -n "shopNoParam|parseClosingDate|existingInvoices|existingMap|paym 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

2026-05-04T01:51:19.197667Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 3.5 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg -n "class TInvoice|uniqueConstraints|partnerCode|closingDate|previ 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 exited 1 in 3506ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg -n "class TInvoice|uniqueConstraints|partnerCode|closingDate|previ 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

2026-05-04T01:51:21.904069Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 6.2 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg -n "QUARTERLY|CLEAN_LAB|JOSAMA|verify\(|buildInvoiceKey|findQuarte 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 exited 1 in 6214ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg -n "QUARTERLY|CLEAN_LAB|JOSAMA|verify\(|buildInvoiceKey|findQuarte 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

2026-05-04T01:51:31.847958Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\InvoiceImportService.java -Pattern 'shopNoParam','parseClosingDate','existingInvoices','existingMap','saveAll','InvoiceImportResult','currentBillingAmount'"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\InvoiceImportService.java -Pattern 'shopNoParam','parseClosingDate','existingInvoices','existingMap','saveAll','InvoiceImportResult','currentBillingAmount'" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\batch\\finance\\service\\InvoiceVerifier.java -Pattern 'QUARTERLY','CLEAN_LAB','JOSAMA','verifiedManually','findQuarterlyInvoice','setInvoiceNo','setMfExportEnabled','invoiceAmountTolerance','buildInvoiceKey'" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\InvoiceImportService.java -Pattern 'shopNoParam','parseClosingDate','existingInvoices','existingMap','saveAll','InvoiceImportResult','currentBillingAmount'"` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\batch\\finance\\service\\InvoiceVerifier.java -Pattern 'QUARTERLY','CLEAN_LAB','JOSAMA','verifiedManually','findQuarterlyInvoice','setInvoiceNo','setMfExportEnabled','invoiceAmountTolerance','buildInvoiceKey'"` rejected: blocked by policy
2026-05-04T01:51:31.848502Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\batch\\finance\\service\\InvoiceVerifier.java -Pattern 'QUARTERLY','CLEAN_LAB','JOSAMA','verifiedManually','findQuarterlyInvoice','setInvoiceNo','setMfExportEnabled','invoiceAmountTolerance','buildInvoiceKey'"` rejected: blocked by policy
2026-05-04T01:51:31.857991Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\api\\finance\\FinanceController.java -Pattern '/invoices','payment-date','importInvoices','bulkUpdatePaymentDate','partner-groups','listPartnerGroups','createPartnerGroup','updatePartnerGroup','deletePartnerGroup'"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\api\\finance\\FinanceController.java -Pattern '/invoices','payment-date','importInvoices','bulkUpdatePaymentDate','partner-groups','listPartnerGroups','createPartnerGroup','updatePartnerGroup','deletePartnerGroup'" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\TInvoice.java -Pattern 'uniqueConstraints','partnerCode','closingDate','previousBalance','currentBillingAmount','shopNo','paymentDate'" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\api\\finance\\FinanceController.java -Pattern '/invoices','payment-date','importInvoices','bulkUpdatePaymentDate','partner-groups','listPartnerGroups','createPartnerGroup','updatePartnerGroup','deletePartnerGroup'"` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\TInvoice.java -Pattern 'uniqueConstraints','partnerCode','closingDate','previousBalance','currentBillingAmount','shopNo','paymentDate'"` rejected: blocked by policy
2026-05-04T01:51:31.858424Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\TInvoice.java -Pattern 'uniqueConstraints','partnerCode','closingDate','previousBalance','currentBillingAmount','shopNo','paymentDate'"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /c:\"shopNoParam\" /c:\"existingInvoices\" /c:\"existingMap\" /c:\"saveAll\" /c:\"InvoiceImportResult\" backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\InvoiceImportService.java" in C:\project\odamitsu-data-hub
 succeeded in 1276ms:
5:import jp.co.oda32.dto.finance.InvoiceImportResult;
41:    public InvoiceImportResult importFromExcel(MultipartFile file, Integer shopNoParam) throws IOException {
55:        int shopNo = shopNoParam != null ? shopNoParam
139:            List<TInvoice> existingInvoices = tInvoiceRepository
145:            Map<String, TInvoice> existingMap = existingInvoices.stream()
153:                TInvoice existing = existingMap.get(parsed.getPartnerCode());
173:            tInvoiceRepository.saveAll(toSave);
178:            return InvoiceImportResult.builder()
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /c:\"QUARTERLY\" /c:\"CLEAN_LAB\" /c:\"JOSAMA\" /c:\"verifiedManually\" /c:\"findQuarterlyInvoice\" /c:\"setInvoiceNo\" /c:\"setMfExportEnabled\" /c:\"invoiceAmountTolerance\" /c:\"buildInvoiceKey\" backend\\src\\main\\java\\jp\\co\\oda32\\batch\\finance\\service\\InvoiceVerifier.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /c:\"/invoices\" /c:\"payment-date\" /c:\"importInvoices\" /c:\"bulkUpdatePaymentDate\" /c:\"partner-groups\" /c:\"listPartnerGroups\" /c:\"createPartnerGroup\" /c:\"updatePartnerGroup\" /c:\"deletePartnerGroup\" backend\\src\\main\\java\\jp\\co\\oda32\\api\\finance\\FinanceController.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /c:\"uniqueConstraints\" /c:\"partnerCode\" /c:\"closingDate\" /c:\"previousBalance\" /c:\"currentBillingAmount\" /c:\"shopNo\" /c:\"paymentDate\" backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\TInvoice.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /c:\"partnerCodes\" /c:\"shopNo\" /c:\"delete\" /c:\"update\" backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\MPartnerGroupService.java backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\MPartnerGroup.java" in C:\project\odamitsu-data-hub
 succeeded in 1432ms:
41:    private static final String QUARTERLY_BILLING_PARTNER_CODE = "000231";
42:    private static final String CLEAN_LAB_PARTNER_CODE = "301491";
43:    private static final String JOSAMA_PARTNER_CODE = "999999";
48:    private BigDecimal invoiceAmountTolerance;
98:            InvoiceValidationKey key = buildInvoiceKey(summary, fallbackPeriodEndDate);
114:            boolean isQuarterlySpecial = QUARTERLY_BILLING_PARTNER_CODE.equals(key.getPartnerCode());
122:                invoiceOpt = findQuarterlyInvoice(key.getShopNo(), key.getPartnerCode(), groupPeriodEnd);
129:            boolean isJosama = JOSAMA_PARTNER_CODE.equals(key.getPartnerCode());
163:            } else if (diff.abs().compareTo(invoiceAmountTolerance) <= 0) {
202:    private InvoiceValidationKey buildInvoiceKey(
206:        if (CLEAN_LAB_PARTNER_CODE.equals(partnerCode)) {
234:    private Optional<TInvoice> findQuarterlyInvoice(
345:            s.setMfExportEnabled(Boolean.TRUE);
351:            s.setInvoiceNo(invoiceId);
364:            s.setMfExportEnabled(Boolean.FALSE);
369:            s.setInvoiceNo(invoiceId);
423:            s.setMfExportEnabled(Boolean.FALSE);
426:            s.setInvoiceNo(null);
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1516ms:
423:    @GetMapping("/invoices")
434:    @PutMapping("/invoices/{invoiceId}/payment-date")
447:    @PostMapping("/invoices/import")
448:    public ResponseEntity<?> importInvoices(
464:    @PutMapping("/invoices/bulk-payment-date")
465:    public ResponseEntity<?> bulkUpdatePaymentDate(@Valid @RequestBody BulkPaymentDateRequest request) {
479:    @GetMapping("/partner-groups")
480:    public ResponseEntity<List<PartnerGroupResponse>> listPartnerGroups(
486:    @PostMapping("/partner-groups")
487:    public ResponseEntity<PartnerGroupResponse> createPartnerGroup(
493:    @PutMapping("/partner-groups/{id}")
494:    public ResponseEntity<PartnerGroupResponse> updatePartnerGroup(
500:    @DeleteMapping("/partner-groups/{id}")
501:    public ResponseEntity<?> deletePartnerGroup(@PathVariable Integer id) {
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1523ms:
backend\src\main\java\jp\co\oda32\domain\service\finance\MPartnerGroupService.java:19:    public List<MPartnerGroup> findByShopNo(Integer shopNo) {
backend\src\main\java\jp\co\oda32\domain\service\finance\MPartnerGroupService.java:20:        return shopNo != null
backend\src\main\java\jp\co\oda32\domain\service\finance\MPartnerGroupService.java:21:                ? repository.findByShopNoOrderByGroupNameAsc(shopNo)
backend\src\main\java\jp\co\oda32\domain\service\finance\MPartnerGroupService.java:34:                .shopNo(request.getShopNo())
backend\src\main\java\jp\co\oda32\domain\service\finance\MPartnerGroupService.java:35:                .partnerCodes(request.getPartnerCodes())
backend\src\main\java\jp\co\oda32\domain\service\finance\MPartnerGroupService.java:41:    public MPartnerGroup update(Integer id, PartnerGroupRequest request) {
backend\src\main\java\jp\co\oda32\domain\service\finance\MPartnerGroupService.java:51:    public void delete(Integer id) {
backend\src\main\java\jp\co\oda32\domain\service\finance\MPartnerGroupService.java:55:        repository.deleteById(id);
backend\src\main\java\jp\co\oda32\domain\model\finance\MPartnerGroup.java:29:    private Integer shopNo;
backend\src\main\java\jp\co\oda32\domain\model\finance\MPartnerGroup.java:38:    private List<String> partnerCodes = new ArrayList<>();
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2250ms:
22:@Table(name = "t_invoice", uniqueConstraints = {
33:    private String partnerCode;  // 得意先コード
39:    private String closingDate;  // 締め日
42:    private BigDecimal previousBalance;  // 前回請求残高
60:    private BigDecimal currentBillingAmount;  // 今回請求額
63:    private Integer shopNo;  // ショップ番号
66:    private LocalDate paymentDate;  // 入金日
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\TAccountsReceivableSummary.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\dto\\finance\\InvoiceResponse.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path frontend\\components\\pages\\finance\\InvoiceImportDialog.tsx" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path frontend\\components\\pages\\finance\\PartnerGroupDialog.tsx" in C:\project\odamitsu-data-hub
 succeeded in 1750ms:
'use client'

import { useState, useEffect } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Trash2, Pencil } from 'lucide-react'
import { toast } from 'sonner'

interface PartnerGroup {
  partnerGroupId: number
  groupName: string
  shopNo: number
  partnerCodes: string[]
}

interface PartnerGroupDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  groups: PartnerGroup[]
  shopNo: number
  editingGroup?: PartnerGroup | null
}

export function PartnerGroupDialog({ open, onOpenChange, groups, shopNo, editingGroup: initialEditingGroup }: PartnerGroupDialogProps) {
  const queryClient = useQueryClient()
  const [editingGroup, setEditingGroup] = useState<PartnerGroup | null>(initialEditingGroup ?? null)
  const [groupName, setGroupName] = useState(initialEditingGroup?.groupName ?? '')
  const [groupPartnerCodes, setGroupPartnerCodes] = useState(initialEditingGroup?.partnerCodes.join('\n') ?? '')
  const [deleteTarget, setDeleteTarget] = useState<PartnerGroup | null>(null)

  useEffect(() => {
    if (open) {
      setEditingGroup(initialEditingGroup ?? null)
      setGroupName(initialEditingGroup?.groupName ?? '')
      setGroupPartnerCodes(initialEditingGroup?.partnerCodes.join('\n') ?? '')
    }
  }, [open, initialEditingGroup])

  const saveMutation = useMutation({
    mutationFn: (data: { id?: number; groupName: string; shopNo: number; partnerCodes: string[] }) => {
      const body = { groupName: data.groupName, shopNo: data.shopNo, partnerCodes: data.partnerCodes }
      return data.id
        ? api.put<PartnerGroup>(`/finance/partner-groups/${data.id}`, body)
        : api.post<PartnerGroup>('/finance/partner-groups', body)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['partner-groups'] })
      onOpenChange(false)
      toast.success('グループを保存しました')
    },
    onError: () => {
      toast.error('グループの保存に失敗しました')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/finance/partner-groups/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['partner-groups'] })
      setDeleteTarget(null)
      toast.success('グループを削除しました')
    },
  })

  const handleEdit = (group: PartnerGroup) => {
    setEditingGroup(group)
    setGroupName(group.groupName)
    setGroupPartnerCodes(group.partnerCodes.join('\n'))
  }

  const handleNewGroup = () => {
    setEditingGroup(null)
    setGroupName('')
    setGroupPartnerCodes('')
  }

  const handleSave = () => {
    if (!groupName.trim()) return
    const codes = groupPartnerCodes
      .split(/[\n,\s]+/)
      .map((c) => c.trim())
      .filter((c) => c.length > 0)
    saveMutation.mutate({
      id: editingGroup?.partnerGroupId,
      groupName: groupName.trim(),
      shopNo,
      partnerCodes: codes,
    })
  }

  const handleClose = () => {
    onOpenChange(false)
    setEditingGroup(null)
    setGroupName('')
    setGroupPartnerCodes('')
  }

  return (
    <>
      <Dialog open={open} onOpenChange={(o) => { if (!o) handleClose() }}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>入金グループ管理</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            {groups.length > 0 && (
              <div className="space-y-2">
                <Label>登録済みグループ</Label>
                <div className="max-h-40 overflow-auto rounded border divide-y">
                  {groups.map((g) => (
                    <div key={g.partnerGroupId} className="flex items-center justify-between px-3 py-2 text-sm">
                      <span>{g.groupName}（{g.partnerCodes.length}件）</span>
                      <div className="flex gap-1">
                        <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => handleEdit(g)}>
                          <Pencil className="h-3 w-3" />
                        </Button>
                        <Button variant="ghost" size="icon" className="h-7 w-7 text-destructive" onClick={() => setDeleteTarget(g)}>
                          <Trash2 className="h-3 w-3" />
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div className="border-t pt-4 space-y-3">
              <div className="flex items-center justify-between">
                <Label>{editingGroup ? 'グループ編集' : '新規グループ'}</Label>
                {editingGroup && (
                  <Button variant="ghost" size="sm" onClick={handleNewGroup}>新規作成に切替</Button>
                )}
              </div>
              <Input
                placeholder="グループ名（例: イズミグループ）"
                value={groupName}
                onChange={(e) => setGroupName(e.target.value)}
              />
              <div className="space-y-1">
                <Label className="text-xs text-muted-foreground">得意先コード（1行に1コード、またはカンマ区切り）</Label>
                <textarea
                  className="flex w-full rounded-md border bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  rows={5}
                  placeholder={"000231\n000232\n000233"}
                  value={groupPartnerCodes}
                  onChange={(e) => setGroupPartnerCodes(e.target.value)}
                />
              </div>
              <div className="flex justify-end gap-2">
                <Button variant="outline" onClick={handleClose}>キャンセル</Button>
                <Button onClick={handleSave} disabled={!groupName.trim() || saveMutation.isPending}>
                  {saveMutation.isPending ? '保存中...' : '保存'}
                </Button>
              </div>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <AlertDialog open={!!deleteTarget} onOpenChange={(o) => { if (!o) setDeleteTarget(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>グループ削除の確認</AlertDialogTitle>
            <AlertDialogDescription>
              「{deleteTarget?.groupName}」を削除しますか？この操作は取り消せません。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>キャンセル</AlertDialogCancel>
            <AlertDialogAction onClick={() => deleteTarget && deleteMutation.mutate(deleteTarget.partnerGroupId)}>
              削除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1822ms:
package jp.co.oda32.dto.finance;

import jp.co.oda32.domain.model.finance.TInvoice;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class InvoiceResponse {
    private Integer invoiceId;
    private String partnerCode;
    private String partnerName;
    private String closingDate;
    private BigDecimal previousBalance;
    private BigDecimal totalPayment;
    private BigDecimal carryOverBalance;
    private BigDecimal netSales;
    private BigDecimal taxPrice;
    private BigDecimal netSalesIncludingTax;
    private BigDecimal currentBillingAmount;
    private Integer shopNo;
    private LocalDate paymentDate;

    public static InvoiceResponse from(TInvoice inv) {
        return InvoiceResponse.builder()
                .invoiceId(inv.getInvoiceId())
                .partnerCode(inv.getPartnerCode())
                .partnerName(inv.getPartnerName())
                .closingDate(inv.getClosingDate())
                .previousBalance(inv.getPreviousBalance())
                .totalPayment(inv.getTotalPayment())
                .carryOverBalance(inv.getCarryOverBalance())
                .netSales(inv.getNetSales())
                .taxPrice(inv.getTaxPrice())
                .netSalesIncludingTax(inv.getNetSalesIncludingTax())
                .currentBillingAmount(inv.getCurrentBillingAmount())
                .shopNo(inv.getShopNo())
                .paymentDate(inv.getPaymentDate())
                .build();
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1823ms:
package jp.co.oda32.domain.model.finance;

import jp.co.oda32.domain.model.embeddable.TAccountsReceivableSummaryPK;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 20日締め売掛金テーブルのEntityクラス
 *
 * @author k_oda
 * @since 2024/08/31
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(name = "t_accounts_receivable_summary")
@IdClass(TAccountsReceivableSummaryPK.class)
public class TAccountsReceivableSummary {
    @Id
    @Column(name = "shop_no")
    private Integer shopNo;
    @Id
    @Column(name = "partner_no")
    private Integer partnerNo;
    @Column(name = "partner_code")
    private String partnerCode;
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

    @Id
    @Column(name = "is_otake_garbage_bag")
    private boolean isOtakeGarbageBag;

    @Column(name = "cutoff_date")
    private Integer cutoffDate;

    @Column(name = "order_no")
    private Integer orderNo;  // 都度現金払い用の注文番号

    // 検証結果のフラグ（1: 一致、0: 不一致、null: 検証なし）
    @Column(name = "verification_result")
    private Integer verificationResult;

    // マネーフォワードエクスポート可否フラグ
    @Builder.Default
    @Column(name = "mf_export_enabled", nullable = false)
    @ColumnDefault("false")
    private Boolean mfExportEnabled = false;

    // 手動確定フラグ: trueなら再集計・再検証バッチで上書きされない
    @Builder.Default
    @Column(name = "verified_manually", nullable = false)
    @ColumnDefault("false")
    private Boolean verifiedManually = false;

    // 検証時の備考（手動確定時の理由など）
    @Column(name = "verification_note")
    private String verificationNote;

    // 突合した請求書金額（税込, t_invoice.net_sales_including_tax）
    @Column(name = "invoice_amount")
    private BigDecimal invoiceAmount;

    // 差額（invoice_amount - tax_included_amount_change）
    @Column(name = "verification_difference")
    private BigDecimal verificationDifference;

    // 突合した請求書ID（t_invoice.invoice_id, 監査用）
    @Column(name = "invoice_no")
    private Integer invoiceNo;
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2763ms:
'use client'

import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { toast } from 'sonner'

interface InvoiceImportResult {
  closingDate: string
  shopNo: number
  totalRows: number
  insertedRows: number
  updatedRows: number
  skippedRows: number
  errors: string[]
}

interface InvoiceImportDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function InvoiceImportDialog({ open, onOpenChange }: InvoiceImportDialogProps) {
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [result, setResult] = useState<InvoiceImportResult | null>(null)

  const mutation = useMutation({
    mutationFn: async (f: File) => {
      const formData = new FormData()
      formData.append('file', f)
      return api.postForm<InvoiceImportResult>('/finance/invoices/import', formData)
    },
    onSuccess: (r) => {
      setResult(r)
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
      toast.success(`インポート完了: ${r.insertedRows}件追加, ${r.updatedRows}件更新`)
    },
    onError: (error: Error) => {
      toast.error(error.message)
    },
  })

  const handleClose = () => {
    onOpenChange(false)
    setFile(null)
    setResult(null)
  }

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) handleClose() }}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>請求実績インポート</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          {!result ? (
            <>
              <div className="space-y-2">
                <Label>Excelファイル（.xlsx）</Label>
                <Input
                  type="file"
                  accept=".xlsx"
                  onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                />
                <p className="text-xs text-muted-foreground">
                  SMILEの請求実績Excel（Sheet1）を選択してください。ファイル名に「松山」を含む場合は第2事業部として取り込みます。
                </p>
              </div>
              <div className="flex justify-end gap-2">
                <Button variant="outline" onClick={handleClose}>キャンセル</Button>
                <Button onClick={() => file && mutation.mutate(file)} disabled={!file || mutation.isPending}>
                  {mutation.isPending ? '取込中...' : '取込実行'}
                </Button>
              </div>
            </>
          ) : (
            <>
              <div className="rounded-lg border p-4 space-y-2 text-sm">
                <div className="flex justify-between"><span className="text-muted-foreground">締日</span><span className="font-medium">{result.closingDate}</span></div>
                <div className="flex justify-between"><span className="text-muted-foreground">事業部</span><span className="font-medium">{result.shopNo === 1 ? '第1事業部' : '第2事業部（松山）'}</span></div>
                <div className="flex justify-between"><span className="text-muted-foreground">処理行数</span><span className="font-medium">{result.totalRows}件</span></div>
                <div className="flex justify-between"><span className="text-muted-foreground">新規追加</span><span className="font-medium text-green-600">{result.insertedRows}件</span></div>
                <div className="flex justify-between"><span className="text-muted-foreground">更新</span><span className="font-medium text-blue-600">{result.updatedRows}件</span></div>
                <div className="flex justify-between"><span className="text-muted-foreground">スキップ</span><span className="font-medium text-gray-500">{result.skippedRows}件</span></div>
              </div>
              <div className="flex justify-end">
                <Button onClick={handleClose}>閉じる</Button>
              </div>
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
**Cluster A 批判的レビュー**

対象コードと既存レビューを確認したうえで、既出の個別バグは再掲せず、設計前提・業務整合・監査性に絞ります。

---

### 1. `t_invoice` が「取込スナップショット」「入金台帳」「検証の正解値」を兼ねている

- Severity: Critical
- カテゴリ: 設計選択 / 業務整合

`TInvoice` は Excel 由来の請求金額、画面で更新する `paymentDate`、`InvoiceVerifier` が MF 出力可否を決める正解値を同じ行に持っています（[TInvoice.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/TInvoice.java:22), [InvoiceVerifier.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java:345)）。このため「SMILE から来た事実」と「経理担当が後から付けた運用状態」と「MF 連携判断」が分離できません。監査時に、金額の由来と入金消込の由来を独立して説明しにくい構造です。

推奨アクション: `t_invoice_source` または `t_invoice_import_line` を原本スナップショット、`t_invoice_payment_status` を入金運用状態として分離してください。`t_invoice` を残す場合も、少なくとも import_run_id / source_hash / confirmed_status を持たせるべきです。

---

### 2. Excel 直 UPSERT は原本性と再現性を失う

- Severity: Critical
- カテゴリ: 設計選択 / 代替案

現状は Excel を読み、即 `t_invoice` に UPSERT しています（[InvoiceImportService.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/InvoiceImportService.java:139), [InvoiceImportService.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/InvoiceImportService.java:173)）。しかし「SMILE 請求実績 Excel」は人手で出力・選択・アップロードされる中間成果物であり、API や DB 連携より改ざん・取り違え・版違いに弱い媒体です。Excel 取込を正とするなら、少なくともファイル自体、シート名、行番号、セル値、取込者、取込時刻、ファイルハッシュを保存しないと、後から同じ結果を再現できません。

推奨アクション: 直接 UPSERT ではなく `stg_invoice_import_run` / `stg_invoice_import_line` に一度取り込み、検証結果を見てから本テーブルに反映する 2 段階方式にしてください。可能なら SMILE 側の DB/API 連携可否を先に評価し、Excel は暫定インターフェースとして扱うべきです。

---

### 3. 請求の一意キーが業務請求書を表していない

- Severity: Critical
- カテゴリ: エッジケース / 業務整合

一意性は `partner_code + closing_date + shop_no` です（[TInvoice.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/TInvoice.java:22)）。これは「得意先別締日集計」のキーであって、「請求書そのもの」のキーではありません。同一得意先・同一締日で中間請求、再発行、訂正請求、部門別請求が発生すると、業務上は複数請求なのに DB 上は 1 件に潰れます。`invoiceId` も自動採番なので SMILE 側の請求番号としては使えません。

推奨アクション: SMILE 側の請求番号、請求区分、訂正区分、発行回数、部門コードなど、業務請求書を識別するキーを取り込んでください。Excel に存在しないなら、この機能は「請求書管理」ではなく「得意先別請求集計管理」と明確に改名すべきです。

---

### 4. 過去請求の所属グループが再現できない

- Severity: Critical
- カテゴリ: エッジケース / 監査

`MPartnerGroup` は現在の `partnerCodes` リストだけを持ちます（[MPartnerGroup.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/MPartnerGroup.java:38)）。得意先が A 組から B 組へ移動すると、過去月を開いたときも現在のグループ定義で選択・集計されます。これは「当時どの入金グループとして処理したか」を説明できない設計です。グループ削除も物理削除なので、過去の一括処理単位が消えます（[MPartnerGroupService.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/MPartnerGroupService.java:55)）。

推奨アクション: `m_partner_group_member_history` に effective_from / effective_to を持たせ、請求締日または入金処理日時で所属を解決してください。一括入金更新時には group_id と member snapshot を履歴に残すべきです。

---

### 5. 入金日更新に「確定」「締め」「取消」の業務状態がない

- Severity: Major
- カテゴリ: 業務整合 / エッジケース

単件・一括とも `paymentDate` を直接上書きするだけです（[FinanceController.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java:434), [FinanceController.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java:464)）。そのため、確定済み月、MF 出力済み、手動確認済み、監査対象期間といった状態を守れません。過去月を検索して一括反映した場合、意図せず閉じた入金実績を書き換えるリスクがあります。

推奨アクション: `payment_status`、`locked_at`、`locked_by`、`confirmed_at`、`confirmed_by`、`unlock_reason` を導入してください。確定済み期間への更新は管理者承認または明示的な取消履歴を必須にすべきです。

---

### 6. SMILE 修正後の再取込ポリシーが未定義

- Severity: Major
- カテゴリ: エッジケース / 監査

過去月の SMILE 請求が修正され、再 Excel 取込された場合、現行設計では金額列だけ現在行に上書きされます（[InvoiceImportService.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/InvoiceImportService.java:153)）。修正前の金額、修正後の金額、どのファイルで変わったか、誰が承認したかが残りません。入金日だけ保持されるため、旧金額に対して付けた入金日が新金額へ無条件に引き継がれる点も危険です。

推奨アクション: 再取込時は差分プレビューを出し、金額変更・名称変更・消滅・新規を分類してください。金額変更がある既入金行は自動反映せず、再確認待ちに落とすべきです。

---

### 7. `InvoiceVerifier` が金額照合だけでなく会計出力可否まで決めている

- Severity: Major
- カテゴリ: 業務整合

`InvoiceVerifier` は `t_invoice` と売掛集計を突合し、`mfExportEnabled` や按分後金額まで更新します（[InvoiceVerifier.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java:345), [InvoiceVerifier.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java:351)）。つまり、Excel 取込値が MF 連携の実質的な権威になります。Cluster D 側で MF の数字、請求書の数字、SMILE Excel の数字の権威順位が定義されていないと、差額発生時にどちらへ寄せるべきかが実装任せになります。

推奨アクション: 「請求書原本 > SMILE 請求実績 > 売掛集計 > MF」などの権威階層を明文化してください。`InvoiceVerifier` は判定結果を記録するだけにし、MF 出力可否の最終決定は別の承認ステップに分けるのが安全です。

---

### 8. 特殊得意先は「例外処理」ではなく契約・請求ルールの欠落

- Severity: Major
- カテゴリ: 設計選択 / 代替案

`000231`、`301491`、`999999` のような得意先コード定数は、単なるコード品質問題ではなく、請求サイクル・店舗帰属・匿名客扱いというドメインモデルの欠落を示しています（[InvoiceVerifier.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java:41)）。このままでは得意先統合、部署移管、締日変更、請求周期変更にコード変更でしか追従できません。特に有効期間を持たないルールは、過去月再検証で現在ルールを誤適用します。

推奨アクション: `m_invoice_rule` を作り、partner_code、shop_no_override、billing_cycle、closing_policy、effective_from / effective_to を管理してください。過去検証では対象月時点のルールを引く必要があります。

---

### 9. virtual `TInvoice` は別概念として永続化すべき

- Severity: Major
- カテゴリ: 代替案 / 監査

四半期合算のように Excel の 1 行と一致しない請求概念を、メモリ上の `TInvoice` として作る設計は危険です（[InvoiceVerifier.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java:234)）。これは「請求書」ではなく「検証用の合算単位」です。監査時には、どの実請求行をどのルールで合算したかを説明する必要があります。

推奨アクション: `t_invoice_reconciliation_group` または `t_invoice_carryover` を作り、構成元 invoice_id のリストと合算理由を永続化してください。UI でも実請求と合算請求を別表示にすべきです。

---

### 10. Excel パーサー、検証、永続化、結果集計が 1 サービスに密結合している

- Severity: Major
- カテゴリ: 代替案

`InvoiceImportService` はファイル検証、締日解析、行パース、業務スキップ、UPSERT、結果 DTO 生成まで担当しています（[InvoiceImportService.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/InvoiceImportService.java:41)）。この構造では「取込前プレビュー」「行ごとの警告表示」「業務承認後に反映」「パーサーだけの差し替え」が難しくなります。Excel フォーマット変更時の影響も永続化処理まで波及します。

推奨アクション: `InvoiceExcelParser`、`InvoiceImportValidator`、`InvoiceImportPlanner`、`InvoiceImportApplier` に分離してください。最初の実装では parser が `List<ParsedInvoiceLine>` を返し、applier は検証済み plan だけを受け取る構造で十分です。

---

### 11. 個人情報・取引先情報の取り扱いが「認証済み全員」前提

- Severity: Minor
- カテゴリ: 業務整合 / 監査

`InvoiceResponse` は得意先コード・得意先名・金額・入金日をそのまま返します（[InvoiceResponse.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/dto/finance/InvoiceResponse.java:9)）。得意先名は法人中心でも、個人事業主名や個人名を含む可能性があります。現状の設計では閲覧目的、保持期間、削除・マスキング、アクセスログが定義されていません。

推奨アクション: 請求一覧の閲覧権限、操作ログ、CSV/画面出力の保持方針を明文化してください。少なくとも閲覧ログと shop 境界は監査対象に含めるべきです。

---

## Opus が見落としていた最重要 3 件

1. `t_invoice` が原本スナップショット、入金運用台帳、MF 連携判断の正解値を兼ねており、責務分離できていないこと。
2. `partner_group` が現在状態しか持たず、過去請求の所属グループと一括処理単位を再現できないこと。
3. `partner_code + closing_date + shop_no` は業務上の請求書キーではなく、同一得意先・同一締日の複数請求や訂正請求を表現できないこと。
tokens used
76,323
**Cluster A 批判的レビュー**

対象コードと既存レビューを確認したうえで、既出の個別バグは再掲せず、設計前提・業務整合・監査性に絞ります。

---

### 1. `t_invoice` が「取込スナップショット」「入金台帳」「検証の正解値」を兼ねている

- Severity: Critical
- カテゴリ: 設計選択 / 業務整合

`TInvoice` は Excel 由来の請求金額、画面で更新する `paymentDate`、`InvoiceVerifier` が MF 出力可否を決める正解値を同じ行に持っています（[TInvoice.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/TInvoice.java:22), [InvoiceVerifier.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java:345)）。このため「SMILE から来た事実」と「経理担当が後から付けた運用状態」と「MF 連携判断」が分離できません。監査時に、金額の由来と入金消込の由来を独立して説明しにくい構造です。

推奨アクション: `t_invoice_source` または `t_invoice_import_line` を原本スナップショット、`t_invoice_payment_status` を入金運用状態として分離してください。`t_invoice` を残す場合も、少なくとも import_run_id / source_hash / confirmed_status を持たせるべきです。

---

### 2. Excel 直 UPSERT は原本性と再現性を失う

- Severity: Critical
- カテゴリ: 設計選択 / 代替案

現状は Excel を読み、即 `t_invoice` に UPSERT しています（[InvoiceImportService.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/InvoiceImportService.java:139), [InvoiceImportService.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/InvoiceImportService.java:173)）。しかし「SMILE 請求実績 Excel」は人手で出力・選択・アップロードされる中間成果物であり、API や DB 連携より改ざん・取り違え・版違いに弱い媒体です。Excel 取込を正とするなら、少なくともファイル自体、シート名、行番号、セル値、取込者、取込時刻、ファイルハッシュを保存しないと、後から同じ結果を再現できません。

推奨アクション: 直接 UPSERT ではなく `stg_invoice_import_run` / `stg_invoice_import_line` に一度取り込み、検証結果を見てから本テーブルに反映する 2 段階方式にしてください。可能なら SMILE 側の DB/API 連携可否を先に評価し、Excel は暫定インターフェースとして扱うべきです。

---

### 3. 請求の一意キーが業務請求書を表していない

- Severity: Critical
- カテゴリ: エッジケース / 業務整合

一意性は `partner_code + closing_date + shop_no` です（[TInvoice.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/TInvoice.java:22)）。これは「得意先別締日集計」のキーであって、「請求書そのもの」のキーではありません。同一得意先・同一締日で中間請求、再発行、訂正請求、部門別請求が発生すると、業務上は複数請求なのに DB 上は 1 件に潰れます。`invoiceId` も自動採番なので SMILE 側の請求番号としては使えません。

推奨アクション: SMILE 側の請求番号、請求区分、訂正区分、発行回数、部門コードなど、業務請求書を識別するキーを取り込んでください。Excel に存在しないなら、この機能は「請求書管理」ではなく「得意先別請求集計管理」と明確に改名すべきです。

---

### 4. 過去請求の所属グループが再現できない

- Severity: Critical
- カテゴリ: エッジケース / 監査

`MPartnerGroup` は現在の `partnerCodes` リストだけを持ちます（[MPartnerGroup.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/MPartnerGroup.java:38)）。得意先が A 組から B 組へ移動すると、過去月を開いたときも現在のグループ定義で選択・集計されます。これは「当時どの入金グループとして処理したか」を説明できない設計です。グループ削除も物理削除なので、過去の一括処理単位が消えます（[MPartnerGroupService.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/MPartnerGroupService.java:55)）。

推奨アクション: `m_partner_group_member_history` に effective_from / effective_to を持たせ、請求締日または入金処理日時で所属を解決してください。一括入金更新時には group_id と member snapshot を履歴に残すべきです。

---

### 5. 入金日更新に「確定」「締め」「取消」の業務状態がない

- Severity: Major
- カテゴリ: 業務整合 / エッジケース

単件・一括とも `paymentDate` を直接上書きするだけです（[FinanceController.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java:434), [FinanceController.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java:464)）。そのため、確定済み月、MF 出力済み、手動確認済み、監査対象期間といった状態を守れません。過去月を検索して一括反映した場合、意図せず閉じた入金実績を書き換えるリスクがあります。

推奨アクション: `payment_status`、`locked_at`、`locked_by`、`confirmed_at`、`confirmed_by`、`unlock_reason` を導入してください。確定済み期間への更新は管理者承認または明示的な取消履歴を必須にすべきです。

---

### 6. SMILE 修正後の再取込ポリシーが未定義

- Severity: Major
- カテゴリ: エッジケース / 監査

過去月の SMILE 請求が修正され、再 Excel 取込された場合、現行設計では金額列だけ現在行に上書きされます（[InvoiceImportService.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/InvoiceImportService.java:153)）。修正前の金額、修正後の金額、どのファイルで変わったか、誰が承認したかが残りません。入金日だけ保持されるため、旧金額に対して付けた入金日が新金額へ無条件に引き継がれる点も危険です。

推奨アクション: 再取込時は差分プレビューを出し、金額変更・名称変更・消滅・新規を分類してください。金額変更がある既入金行は自動反映せず、再確認待ちに落とすべきです。

---

### 7. `InvoiceVerifier` が金額照合だけでなく会計出力可否まで決めている

- Severity: Major
- カテゴリ: 業務整合

`InvoiceVerifier` は `t_invoice` と売掛集計を突合し、`mfExportEnabled` や按分後金額まで更新します（[InvoiceVerifier.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java:345), [InvoiceVerifier.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java:351)）。つまり、Excel 取込値が MF 連携の実質的な権威になります。Cluster D 側で MF の数字、請求書の数字、SMILE Excel の数字の権威順位が定義されていないと、差額発生時にどちらへ寄せるべきかが実装任せになります。

推奨アクション: 「請求書原本 > SMILE 請求実績 > 売掛集計 > MF」などの権威階層を明文化してください。`InvoiceVerifier` は判定結果を記録するだけにし、MF 出力可否の最終決定は別の承認ステップに分けるのが安全です。

---

### 8. 特殊得意先は「例外処理」ではなく契約・請求ルールの欠落

- Severity: Major
- カテゴリ: 設計選択 / 代替案

`000231`、`301491`、`999999` のような得意先コード定数は、単なるコード品質問題ではなく、請求サイクル・店舗帰属・匿名客扱いというドメインモデルの欠落を示しています（[InvoiceVerifier.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java:41)）。このままでは得意先統合、部署移管、締日変更、請求周期変更にコード変更でしか追従できません。特に有効期間を持たないルールは、過去月再検証で現在ルールを誤適用します。

推奨アクション: `m_invoice_rule` を作り、partner_code、shop_no_override、billing_cycle、closing_policy、effective_from / effective_to を管理してください。過去検証では対象月時点のルールを引く必要があります。

---

### 9. virtual `TInvoice` は別概念として永続化すべき

- Severity: Major
- カテゴリ: 代替案 / 監査

四半期合算のように Excel の 1 行と一致しない請求概念を、メモリ上の `TInvoice` として作る設計は危険です（[InvoiceVerifier.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java:234)）。これは「請求書」ではなく「検証用の合算単位」です。監査時には、どの実請求行をどのルールで合算したかを説明する必要があります。

推奨アクション: `t_invoice_reconciliation_group` または `t_invoice_carryover` を作り、構成元 invoice_id のリストと合算理由を永続化してください。UI でも実請求と合算請求を別表示にすべきです。

---

### 10. Excel パーサー、検証、永続化、結果集計が 1 サービスに密結合している

- Severity: Major
- カテゴリ: 代替案

`InvoiceImportService` はファイル検証、締日解析、行パース、業務スキップ、UPSERT、結果 DTO 生成まで担当しています（[InvoiceImportService.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/InvoiceImportService.java:41)）。この構造では「取込前プレビュー」「行ごとの警告表示」「業務承認後に反映」「パーサーだけの差し替え」が難しくなります。Excel フォーマット変更時の影響も永続化処理まで波及します。

推奨アクション: `InvoiceExcelParser`、`InvoiceImportValidator`、`InvoiceImportPlanner`、`InvoiceImportApplier` に分離してください。最初の実装では parser が `List<ParsedInvoiceLine>` を返し、applier は検証済み plan だけを受け取る構造で十分です。

---

### 11. 個人情報・取引先情報の取り扱いが「認証済み全員」前提

- Severity: Minor
- カテゴリ: 業務整合 / 監査

`InvoiceResponse` は得意先コード・得意先名・金額・入金日をそのまま返します（[InvoiceResponse.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/dto/finance/InvoiceResponse.java:9)）。得意先名は法人中心でも、個人事業主名や個人名を含む可能性があります。現状の設計では閲覧目的、保持期間、削除・マスキング、アクセスログが定義されていません。

推奨アクション: 請求一覧の閲覧権限、操作ログ、CSV/画面出力の保持方針を明文化してください。少なくとも閲覧ログと shop 境界は監査対象に含めるべきです。

---

## Opus が見落としていた最重要 3 件

1. `t_invoice` が原本スナップショット、入金運用台帳、MF 連携判断の正解値を兼ねており、責務分離できていないこと。
2. `partner_group` が現在状態しか持たず、過去請求の所属グループと一括処理単位を再現できないこと。
3. `partner_code + closing_date + shop_no` は業務上の請求書キーではなく、同一得意先・同一締日の複数請求や訂正請求を表現できないこと。
