OpenAI Codex v0.128.0 (research preview)
--------
workdir: C:\project\odamitsu-data-hub
model: gpt-5.5
provider: openai
approval: never
sandbox: read-only
reasoning effort: none
reasoning summaries: none
session id: 019dfbc8-9792-7a51-9a7b-79951e29f331
--------
user
あなたは経理・会計連携の上級レビュアーです。
小田光データ連携基盤 (`C:/project/odamitsu-data-hub`) で実装された **経理ビジネスロジック系 4 件** を批判的にレビューしてください。

## レビュー対象 (Group 2: 経理ビジネスロジック)

### P1-02: 整合性レポート opening 注入
- 改修: `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java`
- 改修: `claudedocs/design-supplier-opening-balance.md` §7.2

実装方針: `MfOpeningBalanceService` を constructor inject + ログで「opening 注入確認」を表面化。実際の cumulative 計算は `SupplierBalancesService.generate()` への委譲を維持 (両者が同じ `getEffectiveBalanceMap` を呼ぶため数値は完全一致)。

### P1-03 案 D-2 + P1-07 案 D: per-supplier 値引/早払/送料 attribute (5日払い+20日払い両方)
- 改修: `PaymentMfExcelParser.java` (per-row 振込/値引/早払/送料/相殺 読取)
- 改修: `PaymentMfImportService.java` (PAYABLE/DIRECT_PURCHASE 両方で per-supplier sub-row 展開)
- 改修: `PaymentMfPreviewResponse.java` (`AmountReconciliation` 拡張)
- 改修: `frontend/types/payment-mf.ts` (RuleKind に PAYABLE_*/DIRECT_PURCHASE_* 追加)
- 改修: `claudedocs/design-payment-mf-import.md` §5.5 (新セクション)

### P1-08: 再取込防止 (警告 + 確定保護 + verified_manually 保護)
- 新規: `backend/src/main/resources/db/migration/V034__payment_mf_import_history_protection.sql`
- 新規: `dto/finance/paymentmf/DuplicateWarning.java`, `AppliedWarning.java`
- 改修: `TPaymentMfImportHistory.java` (3 列追加), `TPaymentMfImportHistoryRepository.java`
- 改修: `PaymentMfImportService.java` (SHA-256 hash 計算 + 警告生成 + applyVerification で applied_at 記録 + verified_manually skip)
- 改修: `frontend/types/payment-mf.ts`, `frontend/components/pages/finance/payment-mf-import.tsx`

### P1-09 案 D: verified_amount 不変条件文書化
- 改修: `TAccountsPayableSummary.java` (Javadoc 強化)
- 新規: `backend/src/main/resources/db/migration/V035__document_verified_amount_invariant.sql`
- 新規: `PaymentMfImportServiceVerifiedAmountTest.java` (3 テスト)
- 改修: `claudedocs/design-payment-mf-import.md` §5.6

## 批判的レビュー観点

### 業務整合性 (重点)
1. P1-02: AccountsPayableIntegrityService が SupplierBalancesService に委譲する形 → 両 service の責務分離は維持されているか? circular dependency リスクは?
2. P1-03 D-2: PAYABLE と DIRECT_PURCHASE の副行構造 (借方買掛/借方仕入高 + 貸方仕入値引/早払収益) は会計仕訳として妥当か?
3. P1-03 D-2: 自動降格 (PAYABLE→DIRECT_PURCHASE) で per-supplier attribute が破綻しないか? 元 PAYABLE rule の summaryTemplate/tag/creditDepartment 継承は完全か?
4. P1-03 D-2: 「請求 - 値引 - 早払 - 送料 - 相殺 = 振込」の per-supplier 整合性チェックの 1 円不許容判定で、edge case (= NULL 列、合計行に値があるが per-supplier に値なし、相殺の特殊運用) はどう扱われるか?
5. P1-08: applyVerification で verified_manually=true 行の skip 判定 (`!VERIFICATION_NOTE_BULK_PREFIX で始まる`) で偽陽性/偽陰性なし?
6. P1-09: verified_amount の不変条件「supplier × month で全税率行同値」が崩れる経路 (manual SQL UPDATE 以外) はあるか?

### スキーマ・migration 系
7. V032 (P1-01)、V033 (P1-05)、V034 (P1-08)、V035 (P1-09)、V036 (T2) の連番管理に問題なし? 並行 feature branch で衝突するリスク
8. V034 の `applied_at` index は partial (`WHERE applied_at IS NOT NULL`) → 適用率の高くなる本番で効率的か?
9. P1-08 の applied_at が NULL の状態で AppliedWarning 検出ロジックが意図通り動くか?

### MF 仕訳構造
10. DIRECT_PURCHASE 主行の貸方が「資金複合」になっているが、これは 5日払い PAYABLE 主行の貸方「普通預金」と整合するか?
11. DIRECT_PURCHASE_* 副行の貸方科目選定 (仕入値引・戻し高 / 早払収益) は税理士監査時に説明可能か? (借方仕入高 + 貸方仕入値引・戻し高 = 同時に買って同時に値引、不自然?)
12. PAYABLE_OFFSET / DIRECT_PURCHASE_OFFSET の貸方が「仕入値引・戻し高」暫定 → 相殺仕訳の本来の貸方科目は?

### per-supplier 整合性
13. per-supplier 1 円不許容のチェック失敗時、preview に warning 表示するが、強制 block していない → ユーザーが確認なしで apply 可能? UX 的にはこれで OK?
14. 「20日払いセクション」の判定が「合計行以降」だが、Excel に 5日払い合計行 + 20日払い合計行が両方ある場合の挙動?

### 監査・運用
15. P1-08 hash 警告で「同一ハッシュ」の判定が SHA-256 → ファイル先頭 1 byte 変えただけで別 batch 扱い。この粒度は適切か? (= 例: meta info 変更だけでも別扱い)
16. P1-09 の Javadoc 不変条件は「手動 SQL UPDATE する人が読む」前提だが、admin tool / 緊急 fix 時の操作で気付きづらい仕組みになっていないか?

### Codex DDC-01 関連 (P1-09 で議論されたが結論 D で済ませた)
17. verified_amount を税率別 N 行に同値書込 (= 集約値の冗長保持) で本当に「動作上問題ない」か? 副作用の見落としは?

## 出力フォーマット

Markdown、日本語、簡潔に (max 600 行)。各指摘は:
- Severity: Critical / Major / Minor
- カテゴリ: 業務整合 / スキーマ / 仕訳構造 / 監査 / 設計
- 内容 (3-5 文)
- 推奨アクション (1-2 文)

最後に「Opus 実装で見落としていた最重要 3 件」を明記。


codex
`using-superpowers` と、レビュー依頼なので関連するレビュー系スキルを読みます。まず実装差分と対象ファイルを確認して、指摘はコード行に根拠を置きます。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content C:\\Users\\kazuki\\.agents\\skills\\using-superpowers\\SKILL.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content C:\\Users\\kazuki\\.agents\\skills\\postgresql-code-review\\SKILL.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content C:\\Users\\kazuki\\.agents\\skills\\requesting-code-review\\SKILL.md" in C:\project\odamitsu-data-hub
 succeeded in 4317ms:
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

 succeeded in 5264ms:
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

 succeeded in 6249ms:
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

codex
対象が広いので、まず変更ファイルと migration の実体を横断して、業務ロジックと DB 制約の穴を分けて見ます。今回は実装変更はせず、レビュー観点でリスクを絞ります。
  → 対象ファイルと差分範囲を確認
  • 経理ロジックと仕訳構造をレビュー
  • migration・監査保護をレビュー
  • 指摘を severity 付きで整理
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git status --short' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git diff --stat' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'rg --files backend/src/main/java backend/src/main/resources/db/migration backend/src/test frontend claudedocs | rg "AccountsPayableIntegrityService|PaymentMfExcelParser|PaymentMfImportService|PaymentMfPreviewResponse|DuplicateWarning|AppliedWarning|TPaymentMfImportHistory|TPaymentMfImportHistoryRepository|TAccountsPayableSummary|PaymentMfImportServiceVerifiedAmountTest|payment-mf|V03[2-6]|design-payment-mf-import|design-supplier-opening-balance"' in C:\project\odamitsu-data-hub
 succeeded in 8984ms:
 .claude/settings.local.json                        |  32 +-
 .../jp/co/oda32/api/batch/BatchController.java     |  47 +-
 .../api/finance/AccountsReceivableController.java  | 110 ++--
 .../co/oda32/api/finance/CashBookController.java   |   7 +-
 .../jp/co/oda32/api/finance/FinanceController.java | 173 ++++---
 .../api/finance/MfClientMappingController.java     |   9 +-
 .../oda32/api/finance/MfIntegrationController.java | 506 ++-----------------
 .../oda32/api/finance/MfJournalRuleController.java |   6 +-
 .../api/finance/PaymentMfImportController.java     |  44 +-
 .../jp/co/oda32/api/master/MasterController.java   |  26 +-
 .../jp/co/oda32/api/order/OrderController.java     |   2 +-
 .../oda32/api/purchase/QuoteImportController.java  |   2 +-
 .../java/jp/co/oda32/api/user/UserController.java  |   2 +-
 .../bcart/BCartGoodsPriceTableUpdateTasklet.java   |  40 --
 .../BCartOrderConvertSmileOrderFileTasklet.java    |  53 +-
 .../bcart/SmileDestinationFileOutPutTasklet.java   |   5 +-
 .../finance/AccountsPayableBackfillTasklet.java    |  20 +-
 .../finance/AccountsPayableSummaryInitTasklet.java |  10 +-
 .../AccountsPayableVerificationReportTasklet.java  |   6 +
 .../AccountsReceivableToSalesJournalTasklet.java   |  10 +-
 .../finance/TAccountsReceivableSummaryTasklet.java |  54 +-
 .../config/AccountsPayableAggregationConfig.java   |   8 +-
 .../config/AccountsPayableSummaryConfig.java       |   9 +
 .../config/AccountsPayableVerificationConfig.java  |   9 +
 .../batch/finance/service/InvoiceVerifier.java     |  13 +-
 .../finance/service/PayableMonthlyAggregator.java  |  52 +-
 .../batch/purchase/PurchaseFileProcessor.java      |  23 +-
 .../oda32/batch/purchase/PurchaseFileReader.java   |  33 +-
 .../oda32/batch/purchase/PurchaseFileWriter.java   |   3 +-
 .../batch/smile/NewSmilePurchaseProcessor.java     |  13 +-
 .../batch/smile/SmilePurchaseImportService.java    |  23 +-
 .../batch/smile/SmilePurchaseUpdateService.java    |  78 ++-
 .../batch/smile/UpdateSmilePurchaseProcessor.java  |  14 +-
 .../jp/co/oda32/constant/FinanceConstants.java     |  17 +
 .../embeddable/TAccountsReceivableSummaryPK.java   |   2 +-
 .../oda32/domain/model/finance/MMfOauthClient.java |  20 +-
 .../oda32/domain/model/finance/MPartnerGroup.java  |  16 +-
 .../oda32/domain/model/finance/MPaymentMfRule.java |  18 +-
 .../model/finance/TAccountsPayableSummary.java     |  39 +-
 .../model/finance/TCashbookImportHistory.java      |   2 +
 .../oda32/domain/model/finance/TMfOauthToken.java  |   3 +
 .../model/finance/TPaymentMfImportHistory.java     |  14 +
 .../bcart/BCartChangeHistoryRepository.java        |  35 ++
 .../bcart/DeliveryMappingRepository.java           |  14 -
 .../finance/MPartnerGroupRepository.java           |  21 +-
 .../finance/TAccountsPayableSummaryRepository.java |  24 +
 .../repository/finance/TInvoiceRepository.java     |  29 ++
 .../finance/TMfOauthStateRepository.java           |  15 +
 .../finance/TPaymentMfImportHistoryRepository.java |  15 +
 .../smile/WSmilePurchaseOutputFileRepository.java  |  29 +-
 .../service/bcart/DeliveryMappingService.java      |  46 --
 .../jp/co/oda32/domain/service/data/LoginUser.java |  22 +
 .../service/finance/AccountingStatusService.java   | 196 +++++---
 .../finance/AccountsPayableIntegrityService.java   |  65 ++-
 .../finance/AccountsPayableLedgerService.java      |   5 +-
 .../service/finance/CashBookConvertService.java    |  63 ++-
 .../service/finance/ConsistencyReviewService.java  |  42 +-
 .../service/finance/InvoiceImportService.java      | 190 +++++--
 .../service/finance/MPartnerGroupService.java      |  92 +++-
 .../service/finance/MfHealthCheckService.java      |  33 ++
 .../domain/service/finance/MfTaxResolver.java      |   4 +-
 .../service/finance/PaymentMfCellReader.java       |  15 +-
 .../domain/service/finance/PaymentMfCsvWriter.java |  24 +-
 .../service/finance/PaymentMfExcelParser.java      |  26 +
 .../service/finance/PaymentMfImportService.java    | 394 +++++++++++++--
 .../service/finance/PaymentMfRuleService.java      |  67 ++-
 .../service/finance/PurchaseJournalCsvService.java |   4 +-
 .../service/finance/SalesJournalCsvService.java    |  25 +-
 .../service/finance/SupplierBalancesService.java   |  76 ++-
 .../finance/TAccountsPayableSummaryService.java    |  40 +-
 .../finance/TAccountsReceivableSummaryService.java |   6 +
 .../domain/service/finance/TInvoiceService.java    | 134 ++---
 .../oda32/domain/service/finance/mf/MfAccount.java |   5 +-
 .../service/finance/mf/MfAccountsResponse.java     |  11 +-
 .../domain/service/finance/mf/MfApiClient.java     | 360 ++++++++-----
 .../service/finance/mf/MfJournalFetcher.java       |  67 ++-
 .../domain/service/finance/mf/MfOauthService.java  | 233 ++++++++-
 .../service/finance/mf/MfOauthStateStore.java      |  29 +-
 .../service/finance/mf/MfPaymentAggregator.java    |  75 ++-
 .../finance/mf/MfSupplierLedgerService.java        |  28 +-
 .../domain/service/finance/mf/MfTokenResponse.java |  11 +-
 .../domain/service/finance/mf/MfTokenStatus.java   |  27 +-
 .../domain/service/master/MPartnerService.java     |  19 +
 .../oda32/domain/service/util/LoginUserUtil.java   |  27 +-
 .../co/oda32/dto/bcart/BCartProductResponse.java   |   2 +
 .../jp/co/oda32/dto/estimate/EstimateResponse.java |   4 +
 .../dto/finance/AccountsPayableVerifyRequest.java  |   2 +
 .../finance/AccountsReceivableVerifyRequest.java   |  10 +-
 .../oda32/dto/finance/BulkPaymentDateRequest.java  |  14 +-
 .../jp/co/oda32/dto/finance/InvoiceResponse.java   |   7 +
 .../jp/co/oda32/dto/finance/MfHealthResponse.java  |   6 +
 .../dto/finance/MfSupplierLedgerResponse.java      |   7 +
 .../dto/finance/PaymentDateUpdateRequest.java      |  16 +-
 .../paymentmf/PaymentMfPreviewResponse.java        |  34 ++
 .../dto/finance/paymentmf/PaymentMfPreviewRow.java |   5 +
 .../src/main/resources/config/application-dev.yml  |  18 +-
 .../src/main/resources/config/application-prod.yml |   7 +-
 .../migration/V008__create_mf_cashbook_tables.sql  |   3 +-
 .../src/test/java/jp/co/oda32/TestApplication.java |   3 +-
 .../service/finance/InvoiceImportServiceTest.java  |  12 +-
 .../finance/PaymentMfImportServiceAuxRowTest.java  |  23 +
 .../PaymentMfImportServiceGoldenMasterTest.java    |  30 ++
 .../src/test/resources/config/application-test.yml |   7 +-
 claudedocs/design-accounts-receivable-mf.md        |  51 +-
 claudedocs/design-mf-cashbook-import.md            |  23 +-
 claudedocs/design-mf-integration-status.md         |  83 +++
 claudedocs/design-payment-mf-aux-rows.md           |   8 +-
 claudedocs/design-payment-mf-import.md             | 340 +++++++++++--
 docs/08_bcart_integration.md                       |  30 +-
 frontend/components/features/common/DataTable.tsx  |   6 +-
 frontend/components/layout/AppLayout.tsx           |  12 +
 frontend/components/layout/Sidebar.tsx             |  14 +-
 frontend/components/pages/bcart/product-detail.tsx | 109 +++-
 frontend/components/pages/bcart/products.tsx       |  67 ++-
 .../estimate-comparison/ComparisonGroupForm.tsx    |  18 +-
 .../pages/estimate/EstimateHeaderForm.tsx          |  29 +-
 frontend/components/pages/estimate/detail.tsx      |   6 +-
 frontend/components/pages/estimate/form.tsx        |  36 +-
 .../finance/AccountsReceivableVerifyDialog.tsx     |  61 ++-
 .../pages/finance/InvoiceImportDialog.tsx          |  52 +-
 .../pages/finance/PartnerGroupDialog.tsx           |  16 +-
 .../pages/finance/PaymentMfAuxRowsTable.tsx        |   3 +-
 .../pages/finance/accounting-workflow.tsx          | 557 +++++++++++++--------
 .../pages/finance/accounts-payable-ledger.tsx      |  34 +-
 .../components/pages/finance/accounts-payable.tsx  |  19 +-
 .../pages/finance/accounts-receivable.tsx          |  16 +-
 .../components/pages/finance/cashbook-import.tsx   |  30 +-
 .../components/pages/finance/integrity-report.tsx  | 208 +++++---
 frontend/components/pages/finance/invoices.tsx     |  45 +-
 .../pages/finance/mf-client-mappings.tsx           |  24 +-
 frontend/components/pages/finance/mf-health.tsx    |  36 +-
 .../pages/finance/mf-integration-callback.tsx      |  29 +-
 .../components/pages/finance/mf-integration.tsx    | 111 ++--
 .../components/pages/finance/mf-journal-rules.tsx  |   8 +-
 .../components/pages/finance/payment-mf-import.tsx |  98 +++-
 .../components/pages/finance/payment-mf-rules.tsx  |   8 +-
 .../components/pages/finance/supplier-balances.tsx |  15 +-
 frontend/components/pages/order/index.tsx          |  33 +-
 frontend/e2e/estimate-form.spec.ts                 | 103 ++++
 frontend/e2e/finance-workflow.spec.ts              |   6 +-
 frontend/hooks/use-print-with-status-update.ts     |   4 +
 frontend/lib/utils.ts                              |  21 +
 frontend/types/accounts-payable-ledger.ts          |   6 +
 frontend/types/accounts-payable.ts                 |   8 +-
 frontend/types/accounts-receivable.ts              |  27 +-
 frontend/types/bcart.ts                            |  37 +-
 frontend/types/estimate.ts                         |   1 +
 frontend/types/mf-cashbook.ts                      |  41 +-
 frontend/types/mf-health.ts                        |  14 +-
 frontend/types/mf-integration.ts                   |  45 +-
 frontend/types/payment-mf.ts                       |  84 +++-
 151 files changed, 4947 insertions(+), 2111 deletions(-)
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
warning: in the working copy of 'claudedocs/design-mf-integration-status.md', LF will be replaced by CRLF the next time Git touches it

2026-05-06T05:35:59.572103Z ERROR codex_core::tools::router: error=Exit code: 124
Wall time: 10.6 seconds
Output:
command timed out after 10583 milliseconds
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 exited 124 in 10583ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

2026-05-06T05:35:59.607789Z ERROR codex_core::tools::router: error=Exit code: 124
Wall time: 10.6 seconds
Output:
command timed out after 10622 milliseconds

 exited 124 in 10622ms:
codex
作業ツリーはかなり広く汚れており、対象外の変更も多数あります。レビューではユーザー指定の Group 2 ファイルに限定し、周辺依存は必要な範囲だけ追います。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'rg -n "class AccountsPayableIntegrityService|MfOpeningBalanceService|SupplierBalancesService|getEffectiveBalanceMap|opening" backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'rg -n "class PaymentMfExcelParser|discount|early|shipping|offset|total|section|20|5|reconciliation|transfer" backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParser.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'rg -n "PAYABLE|DIRECT_PURCHASE|OFFSET|DISCOUNT|EARLY|SHIPPING|verified_manually|DuplicateWarning|AppliedWarning|hash|applied_at|AmountReconciliation|VERIFICATION_NOTE_BULK_PREFIX|applyVerification|verified_amount|summaryTemplate|creditDepartment|tag" backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem backend/src/main/resources/db/migration | Sort-Object Name | Select-Object -ExpandProperty Name | Select-String -Pattern "V03[2-6]"' in C:\project\odamitsu-data-hub
2026-05-06T05:36:09.059127Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem backend/src/main/resources/db/migration | Sort-Object Name | Select-Object -ExpandProperty Name | Select-String -Pattern "V03[2-6]"'` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem backend/src/main/resources/db/migration | Sort-Object Name | Select-Object -ExpandProperty Name | Select-String -Pattern "V03[2-6]"'` rejected: blocked by policy
2026-05-06T05:36:13.667173Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 4.6 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg -n "class PaymentMfExcelParser|discount|early|shipping|offset|tota 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 exited 1 in 4623ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg -n "class PaymentMfExcelParser|discount|early|shipping|offset|tota 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

2026-05-06T05:36:16.302071Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 7.3 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg -n "class AccountsPayableIntegrityService|MfOpeningBalanceService| 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 exited 1 in 7259ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg -n "class AccountsPayableIntegrityService|MfOpeningBalanceService| 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

2026-05-06T05:36:19.545914Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 10.5 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg -n "PAYABLE|DIRECT_PURCHASE|OFFSET|DISCOUNT|EARLY|SHIPPING|verifie 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 exited 1 in 10488ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg -n "PAYABLE|DIRECT_PURCHASE|OFFSET|DISCOUNT|EARLY|SHIPPING|verifie 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

codex
この環境では `rg` が使えないため、PowerShell の `Select-String` に切り替えます。読み取り専用 sandbox なので、確認のみ進めます。
2026-05-06T05:36:32.785501Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java,backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java -Pattern 'class AccountsPayableIntegrityService','MfOpeningBalanceService','SupplierBalancesService','getEffectiveBalanceMap','opening' -CaseSensitive:"'$false'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java,backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java -Pattern 'class AccountsPayableIntegrityService','MfOpeningBalanceService','SupplierBalancesService','getEffectiveBalanceMap','opening' -CaseSensitive:"'$false' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParser.java -Pattern 'class PaymentMfExcelParser','discount','early','shipping','offset','total','section','20','5','reconciliation','transfer','actual' -CaseSensitive:"'$false' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java -Pattern 'PAYABLE','DIRECT_PURCHASE','OFFSET','DISCOUNT','EARLY','SHIPPING','verified_manually','DuplicateWarning','AppliedWarning','hash','applied_at','AmountReconciliation','VERIFICATION_NOTE_BULK_PREFIX','applyVerification','verified_amount','summaryTemplate','creditDepartment','tag' -CaseSensitive:"'$false' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem backend/src/main/resources/db/migration | Sort-Object Name | Where-Object { $_.Name -match '"'V03[2-6]' } | Select-Object -ExpandProperty Name" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java,backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java -Pattern 'class AccountsPayableIntegrityService','MfOpeningBalanceService','SupplierBalancesService','getEffectiveBalanceMap','opening' -CaseSensitive:"'$false'` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParser.java -Pattern 'class PaymentMfExcelParser','discount','early','shipping','offset','total','section','20','5','reconciliation','transfer','actual' -CaseSensitive:"'$false'` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java -Pattern 'PAYABLE','DIRECT_PURCHASE','OFFSET','DISCOUNT','EARLY','SHIPPING','verified_manually','DuplicateWarning','AppliedWarning','hash','applied_at','AmountReconciliation','VERIFICATION_NOTE_BULK_PREFIX','applyVerification','verified_amount','summaryTemplate','creditDepartment','tag' -CaseSensitive:"'$false'` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem backend/src/main/resources/db/migration | Sort-Object Name | Where-Object { $_.Name -match '"'V03[2-6]' } | Select-Object -ExpandProperty Name"` rejected: blocked by policy
2026-05-06T05:36:32.786074Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParser.java -Pattern 'class PaymentMfExcelParser','discount','early','shipping','offset','total','section','20','5','reconciliation','transfer','actual' -CaseSensitive:"'$false'` rejected: blocked by policy
2026-05-06T05:36:32.786745Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java -Pattern 'PAYABLE','DIRECT_PURCHASE','OFFSET','DISCOUNT','EARLY','SHIPPING','verified_manually','DuplicateWarning','AppliedWarning','hash','applied_at','AmountReconciliation','VERIFICATION_NOTE_BULK_PREFIX','applyVerification','verified_amount','summaryTemplate','creditDepartment','tag' -CaseSensitive:"'$false'` rejected: blocked by policy
2026-05-06T05:36:32.787429Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem backend/src/main/resources/db/migration | Sort-Object Name | Where-Object { $_.Name -match '"'V03[2-6]' } | Select-Object -ExpandProperty Name"` rejected: blocked by policy
2026-05-06T05:36:41.696707Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Select-String -Path backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java,backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java -Pattern "MfOpeningBalanceService|SupplierBalancesService|getEffectiveBalanceMap|opening|class AccountsPayableIntegrityService"'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Select-String -Path backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java,backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java -Pattern "MfOpeningBalanceService|SupplierBalancesService|getEffectiveBalanceMap|opening|class AccountsPayableIntegrityService"' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Select-String -Path backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java,backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java -Pattern "MfOpeningBalanceService|SupplierBalancesService|getEffectiveBalanceMap|opening|class AccountsPayableIntegrityService"'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Select-String -Path backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParser.java -Pattern "discount|early|shipping|offset|total|section|reconciliation|transfer|actual|20|5"' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Select-String -Path backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java -Pattern "PAYABLE|DIRECT_PURCHASE|OFFSET|DISCOUNT|EARLY|SHIPPING|verified_manually|DuplicateWarning|AppliedWarning|hash|applied_at|AmountReconciliation|VERIFICATION_NOTE_BULK_PREFIX|applyVerification|verified_amount|summaryTemplate|creditDepartment|tag"' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem backend/src/main/resources/db/migration | Sort-Object Name | Select-Object -ExpandProperty Name | Select-String -Pattern "V03[2-6]"' in C:\project\odamitsu-data-hub
2026-05-06T05:36:41.701531Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem backend/src/main/resources/db/migration | Sort-Object Name | Select-Object -ExpandProperty Name | Select-String -Pattern "V03[2-6]"'` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem backend/src/main/resources/db/migration | Sort-Object Name | Select-Object -ExpandProperty Name | Select-String -Pattern "V03[2-6]"'` rejected: blocked by policy
 succeeded in 1621ms:

backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:21: *   <li>3 行目以降: 明細行 → 合
計行 → 明細行（20日払いセクション）。</li>
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:40:            "20日払い振込手数料",
 "5日払い振込手数料", "送金日"
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:51:        Sheet byExactTransfer = n
ull;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:57:            else if ("振込明細".e
quals(n)) byExactTransfer = workbook.getSheetAt(i);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:60:        if (byExactTransfer != nu
ll) return byExactTransfer;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:74:     * 合計行の前は {@code afterT
otal=false}、合計行以降は {@code afterTotal=true}（= 20日払いセクション）。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:85:                if (d != null) { 
out.transferDate = d; break; }
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:93:        // 「仕入コード」ヘッダが
無い振込明細（20日払いなど）では、送り先列の直前（通常は A列）に数値コードが入る。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:101:        Integer colEarly = colMa
p.get("早払い");
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:103:        Integer colTransfer = co
lMap.get("振込金額");
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:105:        // colTransfer (列 E) は
合計行サマリーと per-supplier の双方で使う。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:106:        Integer colDiscount = co
lMap.get("値引");
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:107:        Integer colOffset = colM
ap.get("相殺");
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:120:        boolean afterTotal = fal
se;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:130:            if (sourceNorm != nu
ll && ("合計".equals(sourceNorm) || isTotalRow(row, colSource))) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:133:                    out.summaryE
arlyPayment = colEarly == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colEarly));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:134:                    out.summaryT
ransferAmount = colTransfer == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colTransfer));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:135:                    out.summaryI
nvoiceTotal = colAmount == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colAmount));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:137:                    afterTotal =
 true;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:165:            pe.afterTotal = afte
rTotal;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:167:            // 合計行 (afterTota
l=true で集約) ではなく、ここで個別 supplier ごとに保持する。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:168:            pe.transferAmount = 
colTransfer == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colTransfer));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:170:            pe.discount = colDis
count == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colDiscount));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:171:            pe.earlyPayment = co
lEarly == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colEarly));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:172:            pe.offset = colOffse
t == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colOffset));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:191:    private static boolean isTot
alRow(Row row, int colSource) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:213:        /** 合計行より後の行 (= 
20日払いセクション) に含まれる明細か */
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:214:        boolean afterTotal;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:217:        Long transferAmount;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:221:        Long discount;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:223:        Long earlyPayment;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:225:        Long offset;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:233:        LocalDate transferDate;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:235:        Long summaryEarlyPayment
;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:236:        Long summaryTransferAmou
nt;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java:237:        Long summaryInvoiceTotal
;

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1714ms:

backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:9:import jp.co.oda32.domain.model.
finance.TAccountsPayableSummary;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:13:import jp.co.oda32.domain.repos
itory.finance.TAccountsPayableSummaryRepository;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:16:import jp.co.oda32.dto.finance.
paymentmf.AppliedWarning;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:17:import jp.co.oda32.dto.finance.
paymentmf.DuplicateWarning;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:45:import java.time.OffsetDateTime
;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:46:import java.time.ZoneOffset;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:50:import java.util.LinkedHashMap;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:55:import java.util.concurrent.Con
currentHashMap;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:67:    private final TAccountsPaya
bleSummaryRepository payableRepository;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:68:    private final TAccountsPaya
bleSummaryService payableService;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:91:     * 取引月単位の applyVerifi
cation / exportVerifiedCsv を直列化する advisory lock 用キー。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:103:     * <p><b>node-local</b>: 
複数 JVM 起動（HA 構成）では preview と convert/applyVerification が
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:107:    private final Map<String, 
CachedUpload> cache = new ConcurrentHashMap<>();
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:111:                              
    TAccountsPayableSummaryRepository payableRepository,
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:112:                              
    TAccountsPayableSummaryService payableService,
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:116:        this.payableRepository
 = payableRepository;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:117:        this.payableService = 
payableService;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:136:        String fileHash = comp
uteSha256Hex(uploadedBytes);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:152:                    .summaryEa
rlyPayment(parsed.summaryEarlyPayment)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:156:                    .sourceFil
eHash(fileHash)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:192:     * アップロード済み振込明
細を正として、買掛金一覧(t_accounts_payable_summary)に検証結果を一括反映する。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:193:     * verified_manually=true 
で手動確定扱いにし、SMILE再検証バッチで上書きされないようにする。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:200:    @AuditLog(table = "t_accou
nts_payable_summary", operation = "payment_mf_apply",
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:202:    public VerifyResult applyV
erification(String uploadId, Integer userNo) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:210:        String note = FinanceC
onstants.VERIFICATION_NOTE_BULK_PREFIX
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:214:        Map<String, MPaymentMf
Rule> byCode = new LinkedHashMap<>();
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:215:        Map<String, MPaymentMf
Rule> bySource = new LinkedHashMap<>();
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:222:        // 事前パス: 当該 Exce
l に含まれる全 supplierCode を集め、t_accounts_payable_summary を一括取得する (N+1 解消)。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:223:        Set<String> codesToRec
oncile = new java.util.LinkedHashSet<>();
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:229:            if (rule == null |
| !"PAYABLE".equals(rule.getRuleKind())) continue;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:235:        Map<String, List<TAcco
untsPayableSummary>> payablesByCode = codesToReconcile.isEmpty()
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:237:                : payableRepos
itory.findByShopNoAndSupplierCodeInAndTransactionMonth(
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:238:                        Financ
eConstants.ACCOUNTS_PAYABLE_SHOP_NO, codesToReconcile, txMonth)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:240:                    .collect(C
ollectors.groupingBy(TAccountsPayableSummary::getSupplierCode));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:247:            // PAYABLE行のみ対
象（合計後の20日払いセクションは対象外）
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:252:            if (rule == null |
| !"PAYABLE".equals(rule.getRuleKind())) { skipped++; continue; }
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:258:            List<TAccountsPaya
bleSummary> list = payablesByCode.getOrDefault(reconcileCode, List.of());
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:266:            //   `verified_man
ually=true` かつ単一仕入先 verify (VerifyDialog) 由来の行は再 upload で上書きしない。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:267:            //   ただし bulk v
erify 由来 (verification_note が VERIFICATION_NOTE_BULK_PREFIX 始まり) は
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:270:            // (税率別 verifie
d_amount/note を分割保持していないため、混在状態を作らないこと優先)。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:275:                              
          FinanceConstants.VERIFICATION_NOTE_BULK_PREFIX)));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:278:                log.info("veri
fied_manually=true (単一 verify) 行をスキップ: supplier={}({}) txMonth={}",
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:283:            BigDecimal payable
 = BigDecimal.ZERO;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:284:            for (TAccountsPaya
bleSummary s : list) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:287:                if (v != null)
 payable = payable.add(v);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:290:            BigDecimal differe
nce = payable.subtract(invoice);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:295:            // UI 表示用途のた
め、全税率行に同じ verified_amount を書き込む
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:297:            // V026: 税抜側 (v
erified_amount_tax_excluded) も税率別に逆算して書き込む
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:300:            BigDecimal autoAdj
usted = isMatched ? invoice.subtract(payable) : BigDecimal.ZERO;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:302:                    ? note + "
 | 自動調整: 元 \" + payable + " → \" + invoice
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:305:            for (TAccountsPaya
bleSummary s : list) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:319:                // 5日払いセク
ション hit (afterTotal=false) の PAYABLE のみここに到達する。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:322:                payableService
.save(s);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:326:        // 補助行(EXPENSE/SUMM
ARY/DIRECT_PURCHASE) を (shop, 取引月, 送金日) 単位で洗い替え保存。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:327:        // preview を applyVer
ification 内で 1 回だけ構築し、aux row 生成にそのまま使い回す
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:348:     * P1-08: applyVerificatio
n 実行時に history 行を 1 件追加し、
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:349:     * {@code applied_at} / {@
code applied_by_user_no} / {@code source_file_hash} を記録する。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:361:                    .shopNo(Fi
nanceConstants.ACCOUNTS_PAYABLE_SHOP_NO)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:371:                    .sourceFil
eHash(cached.getSourceFileHash())
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:379:            // history 保存失
敗は本体検証結果に影響させない (verified_manually=true は既に永続化済)。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:380:            log.error("applyVe
rification 履歴の保存に失敗 (検証反映は正常完了): file={}", cached.getFileName(), ex);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:385:     * 同一 (shop_no, transact
ion_month) に対する applyVerification / exportVerifiedCsv を
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:398:     * applyVerification 時に 
EXPENSE/SUMMARY/DIRECT_PURCHASE 行を
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:400:     * PAYABLE 行は {@code t_a
ccounts_payable_summary} 側で管理するためここでは対象外。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:403:     * @param preview applyVer
ification で既に構築済みの preview を使い回す
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:410:                FinanceConstan
ts.ACCOUNTS_PAYABLE_SHOP_NO, txMonth, transferDate);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:413:                    FinanceCon
stants.ACCOUNTS_PAYABLE_SHOP_NO, txMonth, transferDate, deleted);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:422:            if ("PAYABLE".equa
ls(ruleKind)) continue;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:424:            //   - PAYABLE 副
行 (PAYABLE_FEE/DISCOUNT/EARLY/OFFSET) は aux 保存対象外
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:425:            //     (chk_paymen
t_mf_aux_rule_kind 制約 = EXPENSE/SUMMARY/DIRECT_PURCHASE のみ)。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:426:            //     supplier 別
の請求額消込は親 PAYABLE 行と t_accounts_payable_summary 側で完結する。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:427:            //   - DIRECT_PURC
HASE 副行 (DIRECT_PURCHASE_FEE/DISCOUNT/EARLY/OFFSET) も同様に aux 保存対象外。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:430:            //   - DIRECT_PURC
HASE 主行 (副行ではない単独行) は引き続き aux 保存対象 (既存仕様)。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:431:            if (ruleKind.start
sWith("PAYABLE_")) continue;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:432:            if (ruleKind.start
sWith("DIRECT_PURCHASE_")) continue;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:437:                    .shopNo(Fi
nanceConstants.ACCOUNTS_PAYABLE_SHOP_NO)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:451:                    .creditDep
artment(row.getCreditDepartment())
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:454:                    .tag(row.g
etTag())
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:464:                FinanceConstan
ts.ACCOUNTS_PAYABLE_SHOP_NO, txMonth, transferDate, toSave.size());
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:477:         * P1-08 Q3-(ii): veri
fied_manually=true (単一 verify 由来) で保護されスキップされた supplier 数。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:515:     *   <li>振込明細一括検証
では全行に同値の verified_amount が書き込まれるため、
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:522:    private static long sumVer
ifiedAmountForGroup(List<TAccountsPayableSummary> group) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:525:        for (TAccountsPayableS
ummary s : group) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:537:    static long sumVerifiedAmo
untForGroupForTest(List<TAccountsPayableSummary> group) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:550:        private int payableCou
nt;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:559:     * {@code t_accounts_payab
le_summary} の「検証結果=一致 かつ MF出力=ON」行から、
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:561:     * <p>生成される CSV は <b
>PAYABLE(買掛金)行のみ</b>。振込明細 Excel 由来の
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:562:     * 費用仕訳 (EXPENSE) / 直
接仕入高 (DIRECT_PURCHASE) / 振込手数料値引・早払収益 (SUMMARY)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:566:     * <p>SF-C21: 同一 (shop_n
o, transactionMonth) に対する {@link #applyVerification} と本メソッドは
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:568:     * {@link Transactional @T
ransactional} 境界で自動解放されるため、対象 0 件等で early return
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:579:        List<TAccountsPayableS
ummary> dbRows = payableRepository.findVerifiedForMfExport(
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:580:                FinanceConstan
ts.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:583:                        Financ
eConstants.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:591:        // supplier_no 単位に
集約する。verified_amount は振込明細一括検証時は税率別同値だが、
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:593:        Map<Integer, List<TAcc
ountsPayableSummary>> bySupplierNo = dbRows.stream()
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:594:                .collect(Colle
ctors.groupingBy(TAccountsPayableSummary::getSupplierNo, LinkedHashMap::new, Collectors.toList()));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:598:        Map<String, MPaymentMf
Rule> byCode = new LinkedHashMap<>();
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:609:        for (List<TAccountsPay
ableSummary> group : bySupplierNo.values()) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:610:            TAccountsPayableSu
mmary s = group.get(0);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:612:            if (rule == null |
| !"PAYABLE".equals(rule.getRuleKind())) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:614:                        + (rul
e == null ? " ルール未登録" : " 非PAYABLE=" + rule.getRuleKind()) + ")");
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:639:                    .creditDep
artment(rule.getCreditDepartment())
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:643:                    .tag(rule.
getTag())
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:649:        int payableCount = csv
Rows.size();
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:651:        // 補助行 (EXPENSE/SUM
MARY/DIRECT_PURCHASE) を末尾に追加 (transferDate ASC, sequenceNo ASC)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:670:                    .creditDep
artment(aux.getCreditDepartment())
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:674:                    .tag(aux.g
etTag())
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:678:        int auxCount = csvRows
.size() - payableCount;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:698:                .payableCount(
payableCount)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:714:        List<TAccountsPayableS
ummary> dbRows = payableRepository.findVerifiedForMfExport(
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:715:                FinanceConstan
ts.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:718:                        Financ
eConstants.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:720:        // supplier_no 単位に
集約。税率別複数行は verified_amount を SUM する
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:722:        Map<Integer, List<TAcc
ountsPayableSummary>> bySupplierNo = dbRows.stream()
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:723:                .collect(Colle
ctors.groupingBy(TAccountsPayableSummary::getSupplierNo, LinkedHashMap::new, Collectors.toList()));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:726:        Map<String, MPaymentMf
Rule> byCode = new LinkedHashMap<>();
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:733:        int payableCount = 0;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:734:        long payableTotal = 0L
;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:736:        for (List<TAccountsPay
ableSummary> group : bySupplierNo.values()) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:737:            TAccountsPayableSu
mmary s = group.get(0);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:739:            if (rule == null |
| !"PAYABLE".equals(rule.getRuleKind())) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:741:                        + (rul
e == null ? " ルール未登録" : " 非PAYABLE=" + rule.getRuleKind()) + ")");
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:746:            payableCount++;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:747:            payableTotal += am
ount;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:751:        Map<String, VerifiedEx
portPreviewResponse.AuxBreakdownItem> breakdown = new LinkedHashMap<>();
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:752:        Set<LocalDate> seenTra
nsferDates = new java.util.LinkedHashSet<>();
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:794:                .payableCount(
payableCount)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:795:                .payableTotalA
mount(payableTotal)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:808:                        Financ
eConstants.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:825:                .shopNo(Financ
eConstants.ACCOUNTS_PAYABLE_SHOP_NO)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:849:        Map<String, MPaymentMf
Rule> byCode = new LinkedHashMap<>();
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:850:        Map<String, MPaymentMf
Rule> bySource = new LinkedHashMap<>();
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:861:        // N+1 解消 (B-W11): 
エントリ全走査より先に reconcileCode を集め、対象月の payable を一括ロード。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:863:        Map<String, List<TAcco
untsPayableSummary>> payablesByCode = java.util.Collections.emptyMap();
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:865:            Set<String> reconc
ileCodes = new java.util.LinkedHashSet<>();
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:871:                if (e.afterTot
al && "PAYABLE".equals(rule.getRuleKind())) continue; // DIRECT_PURCHASE 扱い
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:872:                if (!"PAYABLE"
.equals(rule.getRuleKind())) continue;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:879:                payablesByCode
 = payableRepository
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:881:                              
  FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, reconcileCodes, txMonth)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:883:                        .colle
ct(Collectors.groupingBy(TAccountsPayableSummary::getSupplierCode));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:888:        Set<String> unregister
ed = new java.util.LinkedHashSet<>();
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:889:        // PAYABLE ルールがマ
ッチしたが payment_supplier_code が未設定の送り先。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:891:        Set<String> rulesMissi
ngCode = new java.util.LinkedHashSet<>();
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:894:        long preTotalAmount = 
0L;   // 合計行前（PAYABLE+EXPENSE）の請求額合計
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:895:        long directPurchaseTot
al = 0L; // 合計行後（DIRECT_PURCHASE）の請求額合計
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:899:        long sumPerSupplierDis
count = 0L;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:900:        long sumPerSupplierEar
ly = 0L;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:901:        long sumPerSupplierOff
set = 0L;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:910:            // 合計行以降(20日
払いセクション) は買掛金(PAYABLE)ではなく仕入高(DIRECT_PURCHASE)扱い
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:911:            // SF-C07: 元ルー
ルの summaryTemplate / tag / creditDepartment を継承する
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:913:            if (rule != null &
& e.afterTotal && "PAYABLE".equals(rule.getRuleKind())) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:916:                        .ruleK
ind("DIRECT_PURCHASE")
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:922:                        .credi
tDepartment(rule.getCreditDepartment())
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:924:                        .summa
ryTemplate(rule.getSummaryTemplate() != null
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:925:                              
  ? rule.getSummaryTemplate() : "{source_name}")
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:926:                        .tag(r
ule.getTag())
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:953:            // P1-03 案 D-2 / 
P1-07 案 D: 5日払い PAYABLE および 20日払い DIRECT_PURCHASE
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:954:            // (自動降格 + 元 
DIRECT_PURCHASE 両方含む) で supplier 別 attribute
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:959:            //   - PAYABLE 主
行: 借方=買掛金 / 貸方=普通預金 (= 振込)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:960:            //   - DIRECT_PURC
HASE 主行: 借方=仕入高 / 貸方=資金複合 (= 振込)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:961:            // 副行 (PAYABLE_*
/DIRECT_PURCHASE_* FEE/DISCOUNT/EARLY/OFFSET) は
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:962:            //   - PAYABLE 系:
 借方=買掛金、貸方=該当勘定で残額を消込
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:963:            //   - DIRECT_PURC
HASE 系: 借方=仕入高、貸方=該当勘定 (買掛金経由しない即払いのため)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:965:            boolean needsSubRo
ws = "PAYABLE".equals(rule.getRuleKind())
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:966:                    || "DIRECT
_PURCHASE".equals(rule.getRuleKind());
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:967:            boolean isDirectPu
rchase = "DIRECT_PURCHASE".equals(rule.getRuleKind());
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:970:            long feeAmt = 0L, 
discountAmt = 0L, earlyAmt = 0L, offsetAmt = 0L;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:973:                discountAmt = 
e.discount == null ? 0L : Math.max(0L, e.discount);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:974:                earlyAmt = e.e
arlyPayment == null ? 0L : Math.max(0L, e.earlyPayment);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:975:                offsetAmt = e.
offset == null ? 0L : Math.max(0L, e.offset);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:976:                long deduction
 = feeAmt + discountAmt + earlyAmt + offsetAmt;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:989:                            + 
" + 送料" + feeAmt + " + 値引" + discountAmt
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:990:                            + 
" + 早払" + earlyAmt + " + 相殺" + offsetAmt
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:998:                sumPerSupplier
Discount += discountAmt;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:999:                sumPerSupplier
Early += earlyAmt;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1000:                sumPerSupplie
rOffset += offsetAmt;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1011:                    .creditDe
partment(rule.getCreditDepartment())
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1015:                    .tag(rule
.getTag());
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1021:            // PAYABLE ルール
自体に payment_supplier_code が未設定なら、検証済みCSV出力 時に
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1022:            // t_accounts_pay
able_summary.supplier_code でルールを逆引き出来ず CSV 除外される。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1026:            if ("PAYABLE".equ
als(rule.getRuleKind()) && !ruleHasCode) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1030:            if ("PAYABLE".equ
als(rule.getRuleKind()) && txMonth != null && reconcileCode != null) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1031:                // 事前取得済
みの payablesByCode から参照 (B-W11 N+1 解消)。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1032:                ReconcileResu
lt rr = reconcileFromPayables(
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1033:                        payab
lesByCode.get(reconcileCode), e.amount);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1034:                b.matchStatus
(rr.status).payableAmount(rr.payableAmount)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1035:                        .paya
bleDiff(rr.diff).supplierNo(rr.supplierNo);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1044:            // P1-03 案 D-2 /
 P1-07 案 D: 5日払い PAYABLE / 20日払い DIRECT_PURCHASE 副行を amount>0 のとき追加。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1045:            // PAYABLE 系: 借
方=買掛金 (親 PAYABLE と同じ)、貸方=該当勘定
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1046:            // DIRECT_PURCHAS
E 系: 借方=仕入高 (親 DIRECT_PURCHASE と同じ)、貸方=該当勘定
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1049:                String feeKin
d = isDirectPurchase ? "DIRECT_PURCHASE_FEE" : "PAYABLE_FEE";
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1050:                String discou
ntKind = isDirectPurchase ? "DIRECT_PURCHASE_DISCOUNT" : "PAYABLE_DISCOUNT";
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1051:                String earlyK
ind = isDirectPurchase ? "DIRECT_PURCHASE_EARLY" : "PAYABLE_EARLY";
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1052:                String offset
Kind = isDirectPurchase ? "DIRECT_PURCHASE_OFFSET" : "PAYABLE_OFFSET";
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1058:                if (discountA
mt > 0L) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1059:                    rows.add(
buildAttributeSubRow(rule, e, discountKind, discountAmt,
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1063:                if (earlyAmt 
> 0L) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1064:                    rows.add(
buildAttributeSubRow(rule, e, earlyKind, earlyAmt,
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1068:                if (offsetAmt
 > 0L) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1070:                    rows.add(
buildAttributeSubRow(rule, e, offsetKind, offsetAmt,
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1078:        // 代わりに supplier 
別に展開された PAYABLE_FEE / PAYABLE_EARLY 行で同等の会計表現になる。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1082:        long summaryEarly = c
ached.getSummaryEarlyPayment() == null ? 0L : cached.getSummaryEarlyPayment();
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1087:        long expectedTransfer
 = summaryInvoice - summaryFee - summaryEarly;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1099:        PaymentMfPreviewRespo
nse.AmountReconciliation recon =
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1100:                PaymentMfPrev
iewResponse.AmountReconciliation.builder()
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1103:                        .summ
aryEarly(summaryEarly)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1116:                        .perS
upplierDiscountSum(sumPerSupplierDiscount)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1117:                        .perS
upplierEarlySum(sumPerSupplierEarly)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1118:                        .perS
upplierOffsetSum(sumPerSupplierOffset)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1123:        DuplicateWarning dupl
icateWarning = buildDuplicateWarning(cached.getSourceFileHash());
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1124:        AppliedWarning applie
dWarning = buildAppliedWarning(cached.getTransferDate());
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1140:                .amountReconc
iliation(recon)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1141:                .duplicateWar
ning(duplicateWarning)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1142:                .appliedWarni
ng(appliedWarning)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1148:     * fileHash が null (旧 c
ache 残存) の場合は null。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1150:    private DuplicateWarning 
buildDuplicateWarning(String fileHash) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1151:        if (fileHash == null 
|| fileHash.isBlank()) return null;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1153:                .findBySource
FileHashAndDelFlgOrderByAddDateTimeDesc(fileHash, "0")
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1156:                .map(h -> new
 DuplicateWarning(
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1158:                             
   : h.getAddDateTime().atOffset(ZoneOffset.ofHours(9)),
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1165:     * P1-08 L2: 同 (shop=1, 
transferDate) で applyVerification 済 (applied_at != NULL) の最新行があれば返す。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1168:    private AppliedWarning bu
ildAppliedWarning(LocalDate transferDate) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1172:                        Finan
ceConstants.ACCOUNTS_PAYABLE_SHOP_NO, transferDate, "0")
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1174:                    OffsetDat
eTime appliedAt = h.getAppliedAt() == null ? null
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1175:                            :
 h.getAppliedAt().atOffset(ZoneOffset.ofHours(9));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1177:                    return ne
w AppliedWarning(appliedAt, h.getAppliedByUserNo(), txMonth, transferDate);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1183:     * P1-03 案 D-2 / P1-07 
案 D: PAYABLE / DIRECT_PURCHASE 副行 (FEE/DISCOUNT/EARLY/OFFSET) を生成する。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1186:     *   <li>PAYABLE 系 (5日
払い): 借方=買掛金/{MF補助科目}/対象外、貸方=該当勘定</li>
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1187:     *   <li>DIRECT_PURCHASE 
系 (20日払い・自動降格含む): 借方=仕入高/課税仕入 10%、貸方=該当勘定</li>
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1197:                             
                        String creditDepartment,
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1206:                // 借方 親ル
ール継承 (PAYABLE→買掛金 / DIRECT_PURCHASE→仕入高)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1214:                .creditDepart
ment(creditDepartment)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1218:                .tag(rule.get
Tag())
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1228:        String tpl = rule.get
SummaryTemplate();
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1291:        String status; Long p
ayableAmount; Long diff; Integer supplierNo;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1295:     * 事前取得した payable 
リストから突合判定する (N+1 解消版)。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1298:    private ReconcileResult r
econcileFromPayables(List<TAccountsPayableSummary> list, Long invoiceAmount) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1304:        long payable = 0L;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1306:        for (TAccountsPayable
Summary s : list) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1310:            if (v != null) pa
yable += toLongFloor(v);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1313:        r.payableAmount = pay
able;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1314:        r.diff = payable - in
voiceAmount;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1345:                    .shopNo(F
inanceConstants.ACCOUNTS_PAYABLE_SHOP_NO)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1355:                    // P1-08:
 convert 経由でも sourceFileHash を残す → 後続 preview の L1 警告で同一 Excel 検知。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1356:                    .sourceFi
leHash(cached.getSourceFileHash())
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1431:     * 同一バイト列なら同一 h
ash → preview L1 警告で重複取込検知に使う。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1436:            byte[] hash = md.
digest(data);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1437:            StringBuilder sb 
= new StringBuilder(hash.length * 2);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1438:            for (byte b : has
h) sb.append(String.format("%02x", b));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1455:        private Long summaryE
arlyPayment;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1461:        /** P1-08: 取込元 Exc
el の SHA-256 (hex)。convert / applyVerification で history 行に永続化。 */
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1462:        private String source
FileHash;

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /i \"MfOpeningBalanceService SupplierBalancesService getEffectiveBalanceMap opening\" backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\AccountsPayableIntegrityService.java backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\SupplierBalancesService.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /i \"source_file_hash applied_at verified_manually verification_note index constraint\" backend\\src\\main\\resources\\db\\migration\\V034__payment_mf_import_history_protection.sql backend\\src\\main\\resources\\db\\migration\\V035__document_verified_amount_invariant.sql backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\TPaymentMfImportHistory.java backend\\src\\main\\java\\jp\\co\\oda32\\domain\\repository\\finance\\TPaymentMfImportHistoryRepository.java backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\TAccountsPayableSummary.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "dir /b backend\\src\\main\\resources\\db\\migration\\V03*.sql" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /i \"RuleKind PAYABLE DIRECT_PURCHASE duplicateWarning appliedWarning amountReconciliation\" frontend\\types\\payment-mf.ts frontend\\components\\pages\\finance\\payment-mf-import.tsx backend\\src\\main\\java\\jp\\co\\oda32\\dto\\finance\\paymentmf\\PaymentMfPreviewResponse.java" in C:\project\odamitsu-data-hub
 succeeded in 3337ms:
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:14:import jp.co.oda32.domain.service.finance.mf.MfOpeningBalanceService;
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:78:    private final SupplierBalancesService supplierBalancesService;
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:81:     * P1-02 (DD-BGH-01): 期首残 (m_supplier_opening_balance) を整合性レポートの cumulative diff にも
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:82:     * 反映させるための明示依存。実値の注入は {@link SupplierBalancesService#generate} 経由
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:83:     * (両サービスで同じ {@code openingBalanceService.getEffectiveBalanceMap} を呼び出すため diff 値が一致) で行うが、
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:84:     * 整合性レポートが期首残を「使っている」契約を本依存で表面化させ、検証ログに opening 件数を出力する。
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:86:    private final MfOpeningBalanceService openingBalanceService;
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:156:            if (MfJournalFetcher.isPayableOpeningJournal(j)) continue; // 期首残高仕訳は除外 (前期繰越として別管理)
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:544:     * 軸 D ({@link SupplierBalancesService}) を呼び、toMonth 時点での supplier 単位累積 diff (self.closing - mfBalance) を
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:549:     * {@link SupplierBalancesService#generate} は内部で {@link MfOpeningBalanceService#getEffectiveBalanceMap}
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:550:     * (shopNo, {@code SELF_BACKFILL_START}=2025-06-20) を呼び self.opening / self.closing に加算する。
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:554:     *   <li>self 側: {@code m_supplier_opening_balance.effectiveBalance} を期首残として注入</li>
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:555:     *   <li>MF 側: journal #1 (期首残高仕訳) は {@link MfJournalFetcher#isPayableOpeningJournal} で
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:559:     * opening は不要だが、reconciledAtPeriodEnd 判定と UI 併記用途で本メソッドの累積 diff を使用する。
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:566:            // 実際の self.closing への加算は SupplierBalancesService.generate() 内部で行われる
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:567:            // (両サービスで openingBalanceService.getEffectiveBalanceMap を呼ぶため値は一致する)。
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:568:            Map<Integer, BigDecimal> openingMap = openingBalanceService.getEffectiveBalanceMap(
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:570:            log.info("[integrity] opening 注入確認: shopNo={} openingDate={} supplier 数={}",
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:571:                    shopNo, MfPeriodConstants.SELF_BACKFILL_START, openingMap.size());
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java:573:            SupplierBalancesResponse bal = supplierBalancesService.generate(shopNo, toMonth, refresh);
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:14:import jp.co.oda32.domain.service.finance.mf.MfOpeningBalanceService;
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:52:public class SupplierBalancesService {
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:62:    private final MfOpeningBalanceService openingBalanceService;
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:121:        // --- 前期繰越 (supplier 単位期首残) — 自社側 opening/closing に加算 ---
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:123:        // self/MF 両側ともに opening は m_supplier_opening_balance.effectiveBalance 経由で注入する。
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:124:        // self 側のみ openingMap 注入を行い、t_accounts_payable_summary の transactionMonth=2025-05-20 行は
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:125:        // accumulateSelf() 内で opening_balance_tax_included を読まない (CR-G01 二重計上防止)。
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:126:        Map<Integer, BigDecimal> openingMap = new HashMap<>(
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:127:                openingBalanceService.getEffectiveBalanceMap(shopNo, MfPeriodConstants.SELF_BACKFILL_START));
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:139:            BigDecimal opening = nz(openingMap.remove(supplierNo));
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:140:            self.opening = self.opening.add(opening);
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:141:            self.closing = self.closing.add(opening);
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:170:            // guessedNo が解決されていれば opening をここでも注入 (self 未登録だが期首残を持つ supplier)
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:171:            BigDecimal opening = guessedNo == null ? BigDecimal.ZERO : nz(openingMap.remove(guessedNo));
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:176:                    .selfBalance(opening)
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:178:                    .diff(opening.subtract(mf.credit.subtract(mf.debit)))
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:181:                    .selfOpening(opening)
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:190:        // 3) opening のみある supplier (self 未登録 かつ MF 側 activity なし) を残り処理
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:191:        for (Map.Entry<Integer, BigDecimal> e : openingMap.entrySet()) {
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:193:            BigDecimal opening = e.getValue();
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:194:            if (opening.signum() == 0) continue;
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:198:            self.opening = opening;
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:199:            self.closing = opening;
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:232:        // SF-G01: 期首残高仕訳 (journal #1) は m_supplier_opening_balance 経由で別途注入するため
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:234:        // SupplierBalancesService の 3 サービスで journal #1 除外を統一し、二重計上を防止する。
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:235:        // opening は SupplierBalancesService.generate() の openingMap (effectiveBalanceMap) で
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:236:        // self.opening + buildRow への注入として加算される。
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:240:            if (MfJournalFetcher.isPayableOpeningJournal(j)) continue; // 期首残高仕訳は除外 (前期繰越として別管理)
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:262:        // CR-G01: opening の取得先は m_supplier_opening_balance.effectiveBalance に統一。
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:264:        // opening_balance_tax_included を累積していたが、generate() 側の openingMap 注入と
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:321:                .selfOpening(self.opening)
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:395:        BigDecimal opening = BigDecimal.ZERO;
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 4076ms:
frontend\types\payment-mf.ts:3: * - PAYABLE: 買掛金 (主行)。借方=買掛金、貸方=普通預金 (= 振込金額)。
frontend\types\payment-mf.ts:5: * - DIRECT_PURCHASE: 合計行以降の仕入高 (20日払いセクション)。
frontend\types\payment-mf.ts:6: *   合計行以降の PAYABLE ルールは即払い (買掛金経由しない) のため自動で DIRECT_PURCHASE に降格される。
frontend\types\payment-mf.ts:8: * - PAYABLE_FEE / PAYABLE_DISCOUNT / PAYABLE_EARLY / PAYABLE_OFFSET:
frontend\types\payment-mf.ts:9: *   P1-03 案 D で追加。5日払い PAYABLE の supplier 別 振込手数料値引/値引/早払/相殺 副行。
frontend\types\payment-mf.ts:11: * - DIRECT_PURCHASE_FEE / DIRECT_PURCHASE_DISCOUNT / DIRECT_PURCHASE_EARLY / DIRECT_PURCHASE_OFFSET:
frontend\types\payment-mf.ts:12: *   P1-07 案 D で追加。20日払い DIRECT_PURCHASE (自動降格 + 元 DIRECT_PURCHASE) の supplier 別副行。
frontend\types\payment-mf.ts:16:export type RuleKind =
frontend\types\payment-mf.ts:17:  | 'PAYABLE'
frontend\types\payment-mf.ts:19:  | 'DIRECT_PURCHASE'
frontend\types\payment-mf.ts:21:  | 'PAYABLE_FEE'
frontend\types\payment-mf.ts:22:  | 'PAYABLE_DISCOUNT'
frontend\types\payment-mf.ts:23:  | 'PAYABLE_EARLY'
frontend\types\payment-mf.ts:24:  | 'PAYABLE_OFFSET'
frontend\types\payment-mf.ts:25:  | 'DIRECT_PURCHASE_FEE'
frontend\types\payment-mf.ts:26:  | 'DIRECT_PURCHASE_DISCOUNT'
frontend\types\payment-mf.ts:27:  | 'DIRECT_PURCHASE_EARLY'
frontend\types\payment-mf.ts:28:  | 'DIRECT_PURCHASE_OFFSET'
frontend\types\payment-mf.ts:32:/** ruleKind の日本語ラベル (preview / 一覧表示用)。 */
frontend\types\payment-mf.ts:33:export const RULE_KIND_LABEL: Record<RuleKind, string> = {
frontend\types\payment-mf.ts:34:  PAYABLE: '買掛金',
frontend\types\payment-mf.ts:36:  DIRECT_PURCHASE: '直接仕入高',
frontend\types\payment-mf.ts:38:  PAYABLE_FEE: '振込手数料値引(仕入先別)',
frontend\types\payment-mf.ts:39:  PAYABLE_DISCOUNT: '値引(仕入先別)',
frontend\types\payment-mf.ts:40:  PAYABLE_EARLY: '早払収益(仕入先別)',
frontend\types\payment-mf.ts:41:  PAYABLE_OFFSET: '相殺(仕入先別)',
frontend\types\payment-mf.ts:42:  DIRECT_PURCHASE_FEE: '振込手数料値引(仕入先別/直接仕入高)',
frontend\types\payment-mf.ts:43:  DIRECT_PURCHASE_DISCOUNT: '値引(仕入先別/直接仕入高)',
frontend\types\payment-mf.ts:44:  DIRECT_PURCHASE_EARLY: '早払収益(仕入先別/直接仕入高)',
frontend\types\payment-mf.ts:45:  DIRECT_PURCHASE_OFFSET: '相殺(仕入先別/直接仕入高)',
frontend\types\payment-mf.ts:54:  ruleKind: RuleKind | null
frontend\types\payment-mf.ts:69:  payableAmount: number | null
frontend\types\payment-mf.ts:70:  payableDiff: number | null
frontend\types\payment-mf.ts:81:export interface DuplicateWarning {
frontend\types\payment-mf.ts:91:export interface AppliedWarning {
frontend\types\payment-mf.ts:113:  /** PAYABLE ルールがマッチしたが payment_supplier_code 未設定の送り先（CSV除外予備軍）。 */
frontend\types\payment-mf.ts:116:  amountReconciliation: AmountReconciliation | null
frontend\types\payment-mf.ts:118:  duplicateWarning: DuplicateWarning | null
frontend\types\payment-mf.ts:120:  appliedWarning: AppliedWarning | null
frontend\types\payment-mf.ts:123:export interface AmountReconciliation {
frontend\types\payment-mf.ts:156:  ruleKind: RuleKind
frontend\types\payment-mf.ts:173:  ruleKind: RuleKind
frontend\types\payment-mf.ts:233:/** 買掛仕入MF 補助行（EXPENSE/SUMMARY/DIRECT_PURCHASE）1 件。 */
frontend\types\payment-mf.ts:238:  ruleKind: 'EXPENSE' | 'SUMMARY' | 'DIRECT_PURCHASE'
frontend\types\payment-mf.ts:256:export type AuxRuleKind = PaymentMfAuxRow['ruleKind']
frontend\types\payment-mf.ts:261:  payableCount: number
frontend\types\payment-mf.ts:262:  payableTotalAmount: number
frontend\types\payment-mf.ts:265:    ruleKind: AuxRuleKind
frontend\types\payment-mf.ts:273:export const RULE_KINDS: RuleKind[] = ['PAYABLE', 'EXPENSE', 'DIRECT_PURCHASE']
frontend\components\pages\finance\payment-mf-import.tsx:23:  PaymentMfRule, PaymentMfRuleRequest, RuleKind,
frontend\components\pages\finance\payment-mf-import.tsx:124:      ruleKind: row.paymentSupplierCode ? 'PAYABLE' : 'EXPENSE',
frontend\components\pages\finance\payment-mf-import.tsx:212:          {preview.duplicateWarning && (
frontend\components\pages\finance\payment-mf-import.tsx:220:                  前回取込: {formatDateTime(preview.duplicateWarning.previousUploadedAt)}
frontend\components\pages\finance\payment-mf-import.tsx:221:                  {preview.duplicateWarning.previousFilename && (
frontend\components\pages\finance\payment-mf-import.tsx:222:                    <> （ファイル: <code className="rounded bg-white px-1">{preview.duplicateWarning.previousFilename}</code>）</>
frontend\components\pages\finance\payment-mf-import.tsx:234:          {preview.appliedWarning && (
frontend\components\pages\finance\payment-mf-import.tsx:240:                  確定日時: {formatDateTime(preview.appliedWarning.appliedAt)}
frontend\components\pages\finance\payment-mf-import.tsx:241:                  （取引月: <b>{preview.appliedWarning.transactionMonth}</b> / 振込日: <b>{preview.appliedWarning.transferDate}</b>）
frontend\components\pages\finance\payment-mf-import.tsx:346:                  <th className="p-1 text-right">買掛金<AmountSourceTooltip source="PAYABLE_SUMMARY" /></th>
frontend\components\pages\finance\payment-mf-import.tsx:364:                    <td className="p-1 text-right">{r.payableAmount?.toLocaleString() ?? ''}</td>
frontend\components\pages\finance\payment-mf-import.tsx:366:                      {r.payableDiff !== null && r.payableDiff !== 0
frontend\components\pages\finance\payment-mf-import.tsx:367:                        ? r.payableDiff.toLocaleString()
frontend\components\pages\finance\payment-mf-import.tsx:407:              <Select value={form.ruleKind}
frontend\components\pages\finance\payment-mf-import.tsx:408:                onValueChange={(v) => setForm({ ...form, ruleKind: v as RuleKind })}>
frontend\components\pages\finance\payment-mf-import.tsx:460:        title={preview?.appliedWarning ? '⚠️ 既に確定済の月を再確定' : '買掛金一覧へ反映'}
frontend\components\pages\finance\payment-mf-import.tsx:462:          preview?.appliedWarning
frontend\components\pages\finance\payment-mf-import.tsx:463:            ? `この月は ${formatDateTime(preview.appliedWarning.appliedAt)} に既に確定済です。再確定すると確定済の値を上書きします（手動確定 verified_manually=true 行は保護されます）。続行しますか？`
frontend\components\pages\finance\payment-mf-import.tsx:466:        confirmLabel={preview?.appliedWarning ? '上書きして再確定する' : '反映する'}
frontend\components\pages\finance\payment-mf-import.tsx:493:    ruleKind: 'PAYABLE',
frontend\components\pages\finance\payment-mf-import.tsx:508:  if (r.ruleKind === 'SUMMARY') return 'bg-slate-50'
frontend\components\pages\finance\payment-mf-import.tsx:509:  // P1-03 案 D / P1-07 案 D: 副行 (PAYABLE_*/DIRECT_PURCHASE_* FEE/DISCOUNT/EARLY/OFFSET) は
frontend\components\pages\finance\payment-mf-import.tsx:510:  // 薄い indigo で親 (PAYABLE/DIRECT_PURCHASE) と区別。5日払い・20日払いとも同色で構造を強調。
frontend\components\pages\finance\payment-mf-import.tsx:512:    r.ruleKind &&
frontend\components\pages\finance\payment-mf-import.tsx:513:    (r.ruleKind.startsWith('PAYABLE_') || r.ruleKind.startsWith('DIRECT_PURCHASE_'))
frontend\components\pages\finance\payment-mf-import.tsx:517:  if (r.matchStatus === 'UNMATCHED' && r.ruleKind === 'PAYABLE') return 'bg-red-50'
frontend\components\pages\finance\payment-mf-import.tsx:538:        href={`/finance/accounts-payable?supplierNo=${supplierNo}&transactionMonth=${txMonth}`}
backend\src\main\java\jp\co\oda32\dto\finance\paymentmf\PaymentMfPreviewResponse.java:32:     * PAYABLE ルールが sourceName マッチはしたが {@code payment_supplier_code} が未設定のため、
backend\src\main\java\jp\co\oda32\dto\finance\paymentmf\PaymentMfPreviewResponse.java:39:    private AmountReconciliation amountReconciliation;
backend\src\main\java\jp\co\oda32\dto\finance\paymentmf\PaymentMfPreviewResponse.java:45:    private DuplicateWarning duplicateWarning;
backend\src\main\java\jp\co\oda32\dto\finance\paymentmf\PaymentMfPreviewResponse.java:51:    private AppliedWarning appliedWarning;
backend\src\main\java\jp\co\oda32\dto\finance\paymentmf\PaymentMfPreviewResponse.java:57:    public static class AmountReconciliation {
backend\src\main\java\jp\co\oda32\dto\finance\paymentmf\PaymentMfPreviewResponse.java:82:        /** DIRECT_PURCHASE / 別振込 の請求額合計（合計行後セクション。参考）。 */
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

2026-05-06T05:37:00.563111Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 5.1 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
Get-ChildItem: 
Line |
   2 |  dir /b backend\src\main\resources\db\migration\V03*.sql
     |  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     | Cannot find path 'C:\b' because it does not exist.

 exited 1 in 5070ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
Get-ChildItem: 
Line |
   2 |  dir /b backend\src\main\resources\db\migration\V03*.sql
     |  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     | Cannot find path 'C:\b' because it does not exist.

 succeeded in 5786ms:
backend\src\main\resources\db\migration\V034__payment_mf_import_history_protection.sql:5:    ADD COLUMN IF NOT EXISTS source_file_hash VARCHAR(64),
backend\src\main\resources\db\migration\V034__payment_mf_import_history_protection.sql:6:    ADD COLUMN IF NOT EXISTS applied_at TIMESTAMP NULL,
backend\src\main\resources\db\migration\V034__payment_mf_import_history_protection.sql:9:CREATE INDEX IF NOT EXISTS idx_payment_mf_import_history_hash
backend\src\main\resources\db\migration\V034__payment_mf_import_history_protection.sql:10:    ON t_payment_mf_import_history (source_file_hash);
backend\src\main\resources\db\migration\V034__payment_mf_import_history_protection.sql:12:CREATE INDEX IF NOT EXISTS idx_payment_mf_import_history_applied
backend\src\main\resources\db\migration\V034__payment_mf_import_history_protection.sql:13:    ON t_payment_mf_import_history (shop_no, transfer_date) WHERE applied_at IS NOT NULL;
backend\src\main\resources\db\migration\V034__payment_mf_import_history_protection.sql:15:COMMENT ON COLUMN t_payment_mf_import_history.source_file_hash
backend\src\main\resources\db\migration\V034__payment_mf_import_history_protection.sql:17:COMMENT ON COLUMN t_payment_mf_import_history.applied_at
backend\src\main\java\jp\co\oda32\domain\model\finance\TPaymentMfImportHistory.java:60:    @Column(name = "source_file_hash", length = 64)
backend\src\main\java\jp\co\oda32\domain\model\finance\TPaymentMfImportHistory.java:64:    @Column(name = "applied_at")
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:74:    @Column(name = "verified_manually", nullable = false)
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:79:    @Column(name = "verification_note")
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java | Select-Object -Skip 880 -First 260' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java | Select-Object -Skip 200 -First 190' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java | Select-Object -Skip 1140 -First 110' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParser.java | Select-Object -Skip 90 -First 120' in C:\project\odamitsu-data-hub
 succeeded in 1759ms:
                .duplicateWarning(duplicateWarning)
                .appliedWarning(appliedWarning)
                .build();
    }

    /**
     * P1-08 L1: 同一 SHA-256 ハッシュの過去取込があれば最新を返す。
     * fileHash が null (旧 cache 残存) の場合は null。
     */
    private DuplicateWarning buildDuplicateWarning(String fileHash) {
        if (fileHash == null || fileHash.isBlank()) return null;
        return historyRepository
                .findBySourceFileHashAndDelFlgOrderByAddDateTimeDesc(fileHash, "0")
                .stream()
                .findFirst()
                .map(h -> new DuplicateWarning(
                        h.getAddDateTime() == null ? null
                                : h.getAddDateTime().atOffset(ZoneOffset.ofHours(9)),
                        h.getSourceFilename(),
                        h.getAddUserNo()))
                .orElse(null);
    }

    /**
     * P1-08 L2: 同 (shop=1, transferDate) で applyVerification 済 (applied_at != NULL) の最新行があれば返す。
     * transferDate が null (Excel から取れない異常ケース) の場合は警告対象外。
     */
    private AppliedWarning buildAppliedWarning(LocalDate transferDate) {
        if (transferDate == null) return null;
        return historyRepository
                .findFirstByShopNoAndTransferDateAndAppliedAtNotNullAndDelFlgOrderByAppliedAtDesc(
                        FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, transferDate, "0")
                .map(h -> {
                    OffsetDateTime appliedAt = h.getAppliedAt() == null ? null
                            : h.getAppliedAt().atOffset(ZoneOffset.ofHours(9));
                    LocalDate txMonth = deriveTransactionMonth(transferDate);
                    return new AppliedWarning(appliedAt, h.getAppliedByUserNo(), txMonth, transferDate);
                })
                .orElse(null);
    }

    /**
     * P1-03 案 D-2 / P1-07 案 D: PAYABLE / DIRECT_PURCHASE 副行 (FEE/DISCOUNT/EARLY/OFFSET) を生成する。
     * <p>借方は親 (買掛金 or 仕入高) を継承、貸方は属性別の勘定。
     * <ul>
     *   <li>PAYABLE 系 (5日払い): 借方=買掛金/{MF補助科目}/対象外、貸方=該当勘定</li>
     *   <li>DIRECT_PURCHASE 系 (20日払い・自動降格含む): 借方=仕入高/課税仕入 10%、貸方=該当勘定</li>
     * </ul>
     * supplier_code / source_name は親と同じ (突合履歴の supplier 紐付け維持のため)。
     * matchStatus は "NA" 固定 (副行は買掛金一覧との突合対象外。請求額消込は親で管理)。
     */
    private PaymentMfPreviewRow buildAttributeSubRow(MPaymentMfRule rule,
                                                     PaymentMfExcelParser.ParsedEntry e,
                                                     String subRuleKind,
                                                     long amount,
                                                     String creditAccount,
                                                     String creditDepartment,
                                                     String creditTax,
                                                     String summary) {
        return PaymentMfPreviewRow.builder()
                .excelRowIndex(e.rowIndex)
                .paymentSupplierCode(e.supplierCode)
                .sourceName(e.sourceName)
                .amount(amount)
                .ruleKind(subRuleKind)
                // 借方 親ルール継承 (PAYABLE→買掛金 / DIRECT_PURCHASE→仕入高)
                .debitAccount(rule.getDebitAccount())
                .debitSubAccount(rule.getDebitSubAccount())
                .debitDepartment(rule.getDebitDepartment())
                .debitTax(rule.getDebitTaxCategory())
                .debitAmount(amount)
                // 貸方 属性別勘定
                .creditAccount(creditAccount)
                .creditDepartment(creditDepartment)
                .creditTax(creditTax)
                .creditAmount(amount)
                .summary(summary)
                .tag(rule.getTag())
                .matchStatus("NA")
                .build();
    }

    private String renderSummary(MPaymentMfRule rule, PaymentMfExcelParser.ParsedEntry e) {
        return renderSummary(rule, e.sourceName);
    }

    private String renderSummary(MPaymentMfRule rule, String sourceName) {
        String tpl = rule.getSummaryTemplate();
        if (tpl == null || tpl.isEmpty()) return sourceName;
        String sub = rule.getDebitSubAccount() != null ? rule.getDebitSubAccount() : sourceName;
        return tpl.replace("{sub_account}", sub)
                  .replace("{source_name}", sourceName);
    }

    /**
     * 振込明細 Excel 上の「仕入コード」(2?3桁の数値) を、DB の
     * {@code m_payment_supplier.payment_supplier_code}（6桁ゼロ埋め）形式に正規化する。
     *
     * <p>運用ルール:
     * <ul>
     *   <li>Excel の仕入コードは "親支払先番号" のみを示し、DB 側は `コード×100` を 6桁ゼロ埋めで保持する。
     *       1?4桁の数値は×100してから6桁ゼロ埋め
     *       （例: Excel "8" → "000800", "81" → "008100", "101" → "010100", "1234" → "123400"）。</li>
     *   <li>5桁以上の値は既に DB 形式（支払先コード形式）とみなし、×100せず6桁ゼロ埋めのみ行う
     *       （例: Excel "12345" → "012345"）。</li>
     *   <li>境界ケース:
     *     <ul>
     *       <li>先頭ゼロ付き入力（例: "0081"）は {@code Long.parseLong} で 81 になり、
     *           4桁以下として×100 される（"008100"）。つまり桁数判定は「数値としての長さ」ではなく
     *           「入力文字列の長さ」で行う点に注意。</li>
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1826ms:
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
                unmatchedSuppliers.add(e.sourceName + "(" + reconcileCode + ")");
                continue;
            }

            // P1-08 Q3-(ii) / Cluster D SF-02 同パターン:
            //   `verified_manually=true` かつ単一仕入先 verify (VerifyDialog) 由来の行は再 upload で上書きしない。
            //   ただし bulk verify 由来 (verification_note が VERIFICATION_NOTE_BULK_PREFIX 始まり) は
            //   再 upload による上書き対象 = 通常の修正版 Excel 取込フローを壊さない。
            // 税率別複数行のうち 1 行でも単一 verify があれば supplier 全体を保護対象とする
            // (税率別 verified_amount/note を分割保持していないため、混在状態を作らないこと優先)。
            boolean anyManuallyLocked = list.stream().anyMatch(s ->
                    Boolean.TRUE.equals(s.getVerifiedManually())
                            && (s.getVerificationNote() == null
                                || !s.getVerificationNote().startsWith(
                                        FinanceConstants.VERIFICATION_NOTE_BULK_PREFIX)));
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
                // 5日払いセクション hit (afterTotal=false) の PAYABLE のみここに到達する。
                // Excel の送金日を CSV 取引日として記録する。
                s.setMfTransferDate(cached.getTransferDate());
                payableService.save(s);
            }
        }

        // 補助行(EXPENSE/SUMMARY/DIRECT_PURCHASE) を (shop, 取引月, 送金日) 単位で洗い替え保存。
        // preview を applyVerification 内で 1 回だけ構築し、aux row 生成にそのまま使い回す
        // (従来は saveAuxRowsForVerification 内で再 buildPreview していたため N+1 が 2 周していた)。
        PaymentMfPreviewResponse preview = buildPreview(uploadId, cached);
        saveAuxRowsForVerification(cached, preview, txMonth, userNo);

        // P1-08: 確定済マークを history に永続化 (preview L2 警告の根拠データ)。
        saveAppliedHistory(cached, preview, userNo);

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
     */
    private void acquireAdvisoryLock(LocalDate transactionMonth) {
        long lockKey = ((long) ADVISORY_LOCK_CLASS << 32)
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2883ms:
                                FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, reconcileCodes, txMonth)
                        .stream()
                        .collect(Collectors.groupingBy(TAccountsPayableSummary::getSupplierCode));
            }
        }

        List<PaymentMfPreviewRow> rows = new ArrayList<>();
        Set<String> unregistered = new java.util.LinkedHashSet<>();
        // PAYABLE ルールがマッチしたが payment_supplier_code が未設定の送り先。
        // 検証済みCSV出力で CSV 除外されるため、一括検証前に補完を促す。
        Set<String> rulesMissingCode = new java.util.LinkedHashSet<>();
        int matched = 0, diff = 0, unmatched = 0, errors = 0;
        long totalAmount = 0L;
        long preTotalAmount = 0L;   // 合計行前（PAYABLE+EXPENSE）の請求額合計
        long directPurchaseTotal = 0L; // 合計行後（DIRECT_PURCHASE）の請求額合計
        // P1-03 案 D: per-supplier の振込/控除合計を蓄積し、Excel 合計行と突合する。
        long sumPerSupplierTransfer = 0L;
        long sumPerSupplierFee = 0L;
        long sumPerSupplierDiscount = 0L;
        long sumPerSupplierEarly = 0L;
        long sumPerSupplierOffset = 0L;
        // per-supplier 1 円不一致 (請求 != 振込 + 控除合計) の行を集約。Excel 入力ミス検知用。
        List<String> perSupplierMismatch = new ArrayList<>();

        for (PaymentMfExcelParser.ParsedEntry e : cached.getEntries()) {
            MPaymentMfRule rule = null;
            if (e.supplierCode != null) rule = byCode.get(e.supplierCode);
            if (rule == null) rule = bySource.get(normalize(e.sourceName));

            // 合計行以降(20日払いセクション) は買掛金(PAYABLE)ではなく仕入高(DIRECT_PURCHASE)扱い
            // SF-C07: 元ルールの summaryTemplate / tag / creditDepartment を継承する
            // (継承漏れによる摘要・タグ・部門欠落の防止)。
            if (rule != null && e.afterTotal && "PAYABLE".equals(rule.getRuleKind())) {
                rule = MPaymentMfRule.builder()
                        .sourceName(rule.getSourceName())
                        .ruleKind("DIRECT_PURCHASE")
                        .debitAccount("仕入高")
                        .debitSubAccount(null)
                        .debitDepartment(null)
                        .debitTaxCategory("課税仕入 10%")
                        .creditAccount("資金複合")
                        .creditDepartment(rule.getCreditDepartment())
                        .creditTaxCategory("対象外")
                        .summaryTemplate(rule.getSummaryTemplate() != null
                                ? rule.getSummaryTemplate() : "{source_name}")
                        .tag(rule.getTag())
                        .build();
            }

            PaymentMfPreviewRow.PaymentMfPreviewRowBuilder b = PaymentMfPreviewRow.builder()
                    .excelRowIndex(e.rowIndex)
                    .paymentSupplierCode(e.supplierCode)
                    .sourceName(e.sourceName)
                    .amount(e.amount);

            if (rule == null) {
                unregistered.add(e.sourceName);
                errors++;
                rows.add(b.errorType("UNREGISTERED")
                        .errorMessage("マスタに未登録: " + e.sourceName)
                        .matchStatus("UNMATCHED")
                        .build());
                unmatched++;
                continue;
            }

            totalAmount += e.amount;
            if (e.afterTotal) {
                directPurchaseTotal += e.amount;
            } else {
                preTotalAmount += e.amount;
            }
            // P1-03 案 D-2 / P1-07 案 D: 5日払い PAYABLE および 20日払い DIRECT_PURCHASE
            // (自動降格 + 元 DIRECT_PURCHASE 両方含む) で supplier 別 attribute
            // (送料相手/値引/早払/相殺) を反映する。
            //
            // 主行の貸方金額は「振込金額」(= 請求 - 送料相手 - 値引 - 早払 - 相殺) に切替えて
            // 銀行通帳の振込金額と一致させる。
            //   - PAYABLE 主行: 借方=買掛金 / 貸方=普通預金 (= 振込)
            //   - DIRECT_PURCHASE 主行: 借方=仕入高 / 貸方=資金複合 (= 振込)
            // 副行 (PAYABLE_*/DIRECT_PURCHASE_* FEE/DISCOUNT/EARLY/OFFSET) は
            //   - PAYABLE 系: 借方=買掛金、貸方=該当勘定で残額を消込
            //   - DIRECT_PURCHASE 系: 借方=仕入高、貸方=該当勘定 (買掛金経由しない即払いのため)
            // amount>0 のときのみ生成。
            boolean needsSubRows = "PAYABLE".equals(rule.getRuleKind())
                    || "DIRECT_PURCHASE".equals(rule.getRuleKind());
            boolean isDirectPurchase = "DIRECT_PURCHASE".equals(rule.getRuleKind());
            String section = e.afterTotal ? "20日払い" : "5日払い";
            long mainCreditAmount = e.amount;
            long feeAmt = 0L, discountAmt = 0L, earlyAmt = 0L, offsetAmt = 0L;
            if (needsSubRows) {
                feeAmt = e.fee == null ? 0L : Math.max(0L, e.fee);
                discountAmt = e.discount == null ? 0L : Math.max(0L, e.discount);
                earlyAmt = e.earlyPayment == null ? 0L : Math.max(0L, e.earlyPayment);
                offsetAmt = e.offset == null ? 0L : Math.max(0L, e.offset);
                long deduction = feeAmt + discountAmt + earlyAmt + offsetAmt;
                // Excel 列 E (振込金額) を優先。NULL なら派生計算 (請求 - 控除合計)。
                if (e.transferAmount != null) {
                    mainCreditAmount = e.transferAmount;
                } else {
                    mainCreditAmount = e.amount - deduction;
                }
                // per-supplier 整合性チェック (1 円不許容): 請求額 = 振込 + 送料相手 + 値引 + 早払 + 相殺
                long expected = mainCreditAmount + deduction;
                if (e.transferAmount != null && expected != e.amount) {
                    String msg = "[" + section + "] " + e.sourceName
                            + "(row=" + e.rowIndex + ", code=" + e.supplierCode
                            + "): 請求=" + e.amount + " != 振込" + mainCreditAmount
                            + " + 送料" + feeAmt + " + 値引" + discountAmt
                            + " + 早払" + earlyAmt + " + 相殺" + offsetAmt
                            + " (差=" + (e.amount - expected) + ")";
                    log.warn("per-supplier 整合性不一致 {}", msg);
                    perSupplierMismatch.add(msg);
                }
                // per-supplier 集計 (合計行突合用) ? 5日払い + 20日払い 両セクション合算
                sumPerSupplierTransfer += mainCreditAmount;
                sumPerSupplierFee += feeAmt;
                sumPerSupplierDiscount += discountAmt;
                sumPerSupplierEarly += earlyAmt;
                sumPerSupplierOffset += offsetAmt;
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
                    // TODO: 相殺の貸方科目は税理士確認 (暫定: 仕入値引・戻し高 / 課税仕入-返還等 10%)
                    rows.add(buildAttributeSubRow(rule, e, offsetKind, offsetAmt,
                            "仕入値引・戻し高", "物販事業部", "課税仕入-返還等 10%",
                            "相殺／" + e.sourceName));
                }
            }
        }

        // P1-03 案 D: 旧 SUMMARY 集約 (振込手数料値引/早払収益 合計仕訳 2 行) を撤去。
        // 代わりに supplier 別に展開された PAYABLE_FEE / PAYABLE_EARLY 行で同等の会計表現になる。

        // 整合性チェック (1 円も許容しない)
        long summaryFee = cached.getSummarySourceFee() == null ? 0L : cached.getSummarySourceFee();
        long summaryEarly = cached.getSummaryEarlyPayment() == null ? 0L : cached.getSummaryEarlyPayment();
        long summaryTransfer = cached.getSummaryTransferAmount() == null ? 0L : cached.getSummaryTransferAmount();
        long summaryInvoice = cached.getSummaryInvoiceTotal() == null ? 0L : cached.getSummaryInvoiceTotal();

        // チェック1: Excel 合計行の列間整合 ? C(請求額) - F(振込手数料) - H(早払) == E(振込金額)
        long expectedTransfer = summaryInvoice - summaryFee - summaryEarly;
        long excelDifference = summaryTransfer - expectedTransfer;
        boolean excelMatched = excelDifference == 0;

        // チェック2: 明細行の読取り整合 ? sum(合計行前 明細 請求額) == C(合計行 請求額)
        long readDifference = preTotalAmount - summaryInvoice;
        boolean readMatched = readDifference == 0;

        // P1-03 案 D チェック3: per-supplier 振込金額合計 == E(合計行 振込金額)
        long perSupplierTransferDiff = sumPerSupplierTransfer - summaryTransfer;
        boolean perSupplierTransferMatched = perSupplierTransferDiff == 0;

        PaymentMfPreviewResponse.AmountReconciliation recon =
                PaymentMfPreviewResponse.AmountReconciliation.builder()
                        .summaryInvoiceTotal(summaryInvoice)
                        .summaryFee(summaryFee)
                        .summaryEarly(summaryEarly)
                        .summaryTransferAmount(summaryTransfer)
                        .expectedTransferAmount(expectedTransfer)
                        .excelDifference(excelDifference)
                        .excelMatched(excelMatched)
                        .preTotalInvoiceSum(preTotalAmount)
                        .readDifference(readDifference)
                        .readMatched(readMatched)
                        .directPurchaseTotal(directPurchaseTotal)
                        .perSupplierTransferSum(sumPerSupplierTransfer)
                        .perSupplierTransferDiff(perSupplierTransferDiff)
                        .perSupplierTransferMatched(perSupplierTransferMatched)
                        .perSupplierFeeSum(sumPerSupplierFee)
                        .perSupplierDiscountSum(sumPerSupplierDiscount)
                        .perSupplierEarlySum(sumPerSupplierEarly)
                        .perSupplierOffsetSum(sumPerSupplierOffset)
                        .perSupplierMismatches(perSupplierMismatch)
                        .build();

        // P1-08 Phase 1: L1 (重複ハッシュ) + L2 (確定済) 警告生成
        DuplicateWarning duplicateWarning = buildDuplicateWarning(cached.getSourceFileHash());
        AppliedWarning appliedWarning = buildAppliedWarning(cached.getTransferDate());

        return PaymentMfPreviewResponse.builder()
                .uploadId(uploadId)
                .fileName(cached.getFileName())
                .transferDate(cached.getTransferDate())
                .transactionMonth(txMonth)
                .totalRows(rows.size())
                .totalAmount(totalAmount)
                .matchedCount(matched)
                .diffCount(diff)
                .unmatchedCount(unmatched)
                .errorCount(errors)
                .rows(rows)
                .unregisteredSources(new ArrayList<>(unregistered))
                .rulesMissingSupplierCode(new ArrayList<>(rulesMissingCode))
                .amountReconciliation(recon)
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3893ms:
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
        boolean summaryCaptured = false;
        boolean afterTotal = false;
        for (int i = 2; i <= last; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String sourceName = PaymentMfCellReader.readStringCell(row.getCell(colSource));
            String sourceNorm = PaymentMfCellReader.normalize(sourceName);
            Long amount = PaymentMfCellReader.readLongCell(row.getCell(colAmount));

            // 合計行の処理（サマリー抽出 + セクション切替）
            if (sourceNorm != null && ("合計".equals(sourceNorm) || isTotalRow(row, colSource))) {
                if (!summaryCaptured) {
                    out.summarySourceFee = colFee == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colFee));
                    out.summaryEarlyPayment = colEarly == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colEarly));
                    out.summaryTransferAmount = colTransfer == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colTransfer));
                    out.summaryInvoiceTotal = colAmount == null ? null : PaymentMfCellReader.readLongCell(row.getCell(colAmount));
                    summaryCaptured = true;
                    afterTotal = true;
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
            pe.afterTotal = afterTotal;
            // P1-03 案 D: per-supplier の値引/早払/送料相手/相殺/振込金額を読取る。
            // 合計行 (afterTotal=true で集約) ではなく、ここで個別 supplier ごとに保持する。
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

    /** Excel 明細1行の抽出結果（内部用 POJO）。 */
    static class ParsedEntry {
        int rowIndex;
        String supplierCode;
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /i \"buildCsvRows buildAttributeSubRow saveAuxRowsForVerification exportVerifiedCsv creditAccount 資金複合 普通預金 仕入値引 早払収益\" backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfCsvWriter.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /i \"findBySourceFileHash findFirstByShopNoAndTransferDate appliedAt sourceFileHash\" backend\\src\\main\\java\\jp\\co\\oda32\\domain\\repository\\finance\\TPaymentMfImportHistoryRepository.java backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\TPaymentMfImportHistory.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /i \"verified_amount verifiedAmount invariant CHECK comment\" backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\TAccountsPayableSummary.java backend\\src\\main\\resources\\db\\migration\\V035__document_verified_amount_invariant.sql backend\\src\\test\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportServiceVerifiedAmountTest.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Name backend/src/main/resources/db/migration/V03*.sql' in C:\project\odamitsu-data-hub
 succeeded in 1524ms:
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:89:     * 単一行のみを SQL UPDATE で変更すると {@code PaymentMfImportService.sumVerifiedAmountForGroup}
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:96:     *   <li><b>仕入仕訳</b> (借方仕入高 / 貸方買掛金): 税率別の {@link #verifiedAmountTaxExcluded} を税率ごとに参照</li>
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:106:    @Column(name = "verified_amount")
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:107:    private BigDecimal verifiedAmount;
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:111:     * V026 (2026-04-23) で追加。{@link #verifiedAmount} (税込) を税率別に逆算した値で、
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:117:     * <p>計算式: {@code verifiedAmountTaxExcluded = verifiedAmount × 100 / (100 + taxRate)} (端数切捨)
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:119:     * <p>注意: {@link #verifiedAmount} は同値だが、本フィールドは税率別に異なる。
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:121:    @Column(name = "verified_amount_tax_excluded")
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:122:    private BigDecimal verifiedAmountTaxExcluded;
backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java:125:     * 振込明細 Excel 取込時の自動調整額 (= verifiedAmount - taxIncludedAmountChange、符号あり)。
backend\src\main\resources\db\migration\V035__document_verified_amount_invariant.sql:1:-- P1-09 案 D: verified_amount の不変条件を DB レベルで明示
backend\src\main\resources\db\migration\V035__document_verified_amount_invariant.sql:2:COMMENT ON COLUMN t_accounts_payable_summary.verified_amount IS
backend\src\main\resources\db\migration\V035__document_verified_amount_invariant.sql:5:'単一行のみ SQL UPDATE すると read 側 sumVerifiedAmountForGroup の重複カウント回避が崩れ過大計上の原因となる。' ||
backend\src\main\resources\db\migration\V035__document_verified_amount_invariant.sql:7:'仕訳生成: 振込仕訳(借方買掛/貸方普通預金、対象外)では集約値として1度参照、仕入仕訳(借方仕入高)では verified_amount_tax_excluded を税率別参照。';
backend\src\main\resources\db\migration\V035__document_verified_amount_invariant.sql:9:COMMENT ON COLUMN t_accounts_payable_summary.verified_amount_tax_excluded IS
backend\src\main\resources\db\migration\V035__document_verified_amount_invariant.sql:12:'計算式: verified_amount × 100 / (100 + tax_rate) 端数切捨。';
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceVerifiedAmountTest.java:12: * P1-09 案 D: verified_amount 不変条件の保証テスト。
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceVerifiedAmountTest.java:14: * 不変条件: supplier × transaction_month の全税率行で verifiedAmount は同値であること。
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceVerifiedAmountTest.java:16: * 本テストは {@code sumVerifiedAmountForGroup} の挙動を package-private で検証し、
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceVerifiedAmountTest.java:19:class PaymentMfImportServiceVerifiedAmountTest {
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceVerifiedAmountTest.java:23:        // supplier × month で 3 税率行、全て verified_amount = 1,000,000
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceVerifiedAmountTest.java:30:        long result = PaymentMfImportService.sumVerifiedAmountForGroupForTest(group);
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceVerifiedAmountTest.java:45:        long result = PaymentMfImportService.sumVerifiedAmountForGroupForTest(group);
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceVerifiedAmountTest.java:54:    void verifiedAmount_null時はtaxIncludedAmountChangeフォールバック() {
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceVerifiedAmountTest.java:60:        long result = PaymentMfImportService.sumVerifiedAmountForGroupForTest(group);
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceVerifiedAmountTest.java:66:    private TAccountsPayableSummary buildRow(BigDecimal taxRate, BigDecimal verifiedAmount) {
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceVerifiedAmountTest.java:67:        return buildRow(taxRate, verifiedAmount, null);
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceVerifiedAmountTest.java:70:    private TAccountsPayableSummary buildRow(BigDecimal taxRate, BigDecimal verifiedAmount, BigDecimal change) {
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceVerifiedAmountTest.java:73:        s.setVerifiedAmount(verifiedAmount);
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1597ms:
backend\src\main\java\jp\co\oda32\domain\repository\finance\TPaymentMfImportHistoryRepository.java:19:    List<TPaymentMfImportHistory> findBySourceFileHashAndDelFlgOrderByAddDateTimeDesc(
backend\src\main\java\jp\co\oda32\domain\repository\finance\TPaymentMfImportHistoryRepository.java:20:            String sourceFileHash, String delFlg);
backend\src\main\java\jp\co\oda32\domain\repository\finance\TPaymentMfImportHistoryRepository.java:26:    Optional<TPaymentMfImportHistory> findFirstByShopNoAndTransferDateAndAppliedAtNotNullAndDelFlgOrderByAppliedAtDesc(
backend\src\main\java\jp\co\oda32\domain\model\finance\TPaymentMfImportHistory.java:61:    private String sourceFileHash;
backend\src\main\java\jp\co\oda32\domain\model\finance\TPaymentMfImportHistory.java:65:    private LocalDateTime appliedAt;
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1600ms:
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:91:     * 取引月単位の applyVerification / exportVerifiedCsv を直列化する advisory lock 用キー。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:328:        // (従来は saveAuxRowsForVerification 内で再 buildPreview していたため N+1 が 2 周していた)。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:330:        saveAuxRowsForVerification(cached, preview, txMonth, userNo);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:385:     * 同一 (shop_no, transaction_month) に対する applyVerification / exportVerifiedCsv を
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:406:    private void saveAuxRowsForVerification(CachedUpload cached, PaymentMfPreviewResponse preview,
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:449:                    .creditAccount(row.getCreditAccount())
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:575:    public VerifiedExportResult exportVerifiedCsv(LocalDate transactionMonth, Integer userNo) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:637:                    .creditAccount(rule.getCreditAccount())
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:668:                    .creditAccount(aux.getCreditAccount())
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:921:                        .creditAccount("資金複合")
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1009:                    .creditAccount(rule.getCreditAccount())
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1054:                    rows.add(buildAttributeSubRow(rule, e, feeKind, feeAmt,
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1059:                    rows.add(buildAttributeSubRow(rule, e, discountKind, discountAmt,
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1064:                    rows.add(buildAttributeSubRow(rule, e, earlyKind, earlyAmt,
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1070:                    rows.add(buildAttributeSubRow(rule, e, offsetKind, offsetAmt,
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1192:    private PaymentMfPreviewRow buildAttributeSubRow(MPaymentMfRule rule,
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1196:                                                     String creditAccount,
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1213:                .creditAccount(creditAccount)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:84:        cols.add(safe(r.getCreditAccount()));
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1817ms:
V030__deprecate_old_payable_summary_jobs.sql
V031__create_t_invoice_and_m_partner_group.sql
V032__add_mf_tenant_binding.sql
V034__payment_mf_import_history_protection.sql
V035__document_verified_amount_invariant.sql
V036__finance_audit_log.sql
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
いくつか重要な兆候が出ています。特に migration 連番は `V033` がこの作業ツリー上に見当たらず、P1-08/P1-09 の「警告」も確定ブロックではなく確認ダイアログ止まりです。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java | Select-Object -Skip 560 -First 125' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java | Select-Object -Skip 500 -First 45' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java | Select-Object -Skip 400 -First 70' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/resources/db/migration/V034__payment_mf_import_history_protection.sql; Get-Content backend/src/main/resources/db/migration/V035__document_verified_amount_invariant.sql' in C:\project\odamitsu-data-hub
 succeeded in 1856ms:
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
     * 同一 supplier_no に属する税率別行群から、MF出力用の合計額を算出する。
     * <ul>
     *   <li>振込明細一括検証では全行に同値の verified_amount が書き込まれるため、
     *       SUM すると件数倍の重複になる。このケースは代表1行の値を採用する。</li>
     *   <li>手動 verify では税率別に異なる値が入り得るため、税率行の SUM を採用する。</li>
     * </ul>
     * 判定は verifiedAmount が全行一致なら代表値、そうでなければ SUM。
     * verifiedAmount が null の行は taxIncludedAmountChange にフォールバック。
     */
    private static long sumVerifiedAmountForGroup(List<TAccountsPayableSummary> group) {
        if (group.isEmpty()) return 0L;
        List<Long> perRow = new ArrayList<>(group.size());
        for (TAccountsPayableSummary s : group) {
            BigDecimal v = s.getVerifiedAmount() != null ? s.getVerifiedAmount()
                    : s.getTaxIncludedAmountChange();
            perRow.add(v == null ? 0L : toLongFloor(v));
        }
        long first = perRow.get(0);
        boolean allSame = perRow.stream().allMatch(x -> x == first);
        if (allSame) return first;
        return perRow.stream().mapToLong(Long::longValue).sum();
    }

    /** P1-09 テスト用: {@link #sumVerifiedAmountForGroup} を package 外から呼ぶためのフック。 */
    static long sumVerifiedAmountForGroupForTest(List<TAccountsPayableSummary> group) {
        return sumVerifiedAmountForGroup(group);
    }

    // ===========================================================
    // 検証済み買掛金からの MF CSV 出力 (Excel 再アップロード不要)
    // ===========================================================

    @Data
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1921ms:
     * UNREGISTERED 行は CSV に出ないため保存しない。
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
            if ("PAYABLE".equals(ruleKind)) continue;
            // P1-03 案 D / P1-07 案 D:
            //   - PAYABLE 副行 (PAYABLE_FEE/DISCOUNT/EARLY/OFFSET) は aux 保存対象外
            //     (chk_payment_mf_aux_rule_kind 制約 = EXPENSE/SUMMARY/DIRECT_PURCHASE のみ)。
            //     supplier 別の請求額消込は親 PAYABLE 行と t_accounts_payable_summary 側で完結する。
            //   - DIRECT_PURCHASE 副行 (DIRECT_PURCHASE_FEE/DISCOUNT/EARLY/OFFSET) も同様に aux 保存対象外。
            //     検証済みCSV出力 (DB-only 経路) では副行を再現できないため、20日払いの値引/早払/送料相手は
            //     Excel→CSV ダイレクト変換 (convert) 経路でのみ MF に出る (運用上 OK)。
            //   - DIRECT_PURCHASE 主行 (副行ではない単独行) は引き続き aux 保存対象 (既存仕様)。
            if (ruleKind.startsWith("PAYABLE_")) continue;
            if (ruleKind.startsWith("DIRECT_PURCHASE_")) continue;
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
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2948ms:
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
            PaymentMfPreviewRow row = PaymentMfPreviewRow.builder()
                    .paymentSupplierCode(s.getSupplierCode())
                    .sourceName(sourceName)
                    .amount(amount)
                    .transactionDate(transactionMonth)
                    .ruleKind(rule.getRuleKind())
                    .debitAccount(rule.getDebitAccount())
                    .debitSubAccount(rule.getDebitSubAccount())
                    .debitDepartment(rule.getDebitDepartment())
                    .debitTax(rule.getDebitTaxCategory())
                    .debitAmount(amount)
                    .creditAccount(rule.getCreditAccount())
                    .creditSubAccount(rule.getCreditSubAccount())
                    .creditDepartment(rule.getCreditDepartment())
                    .creditTax(rule.getCreditTaxCategory())
                    .creditAmount(amount)
                    .summary(renderSummary(rule, sourceName))
                    .tag(rule.getTag())
                    .matchStatus("MATCHED")
                    .build();
            csvRows.add(row);
        }

        int payableCount = csvRows.size();

        // 補助行 (EXPENSE/SUMMARY/DIRECT_PURCHASE) を末尾に追加 (transferDate ASC, sequenceNo ASC)
        // SF-C03: 取引日は CSV 列としては transactionMonth (締め日) 固定。aux.transferDate (送金日) は
        // DB 監査列として保持しソート用途にのみ使用する。
        for (TPaymentMfAuxRow aux : auxRows) {
            long amount = aux.getAmount() == null ? 0L : toLongFloor(aux.getAmount());
            totalAmount += amount;
            csvRows.add(PaymentMfPreviewRow.builder()
                    .paymentSupplierCode(aux.getPaymentSupplierCode())
                    .sourceName(aux.getSourceName())
                    .amount(amount)
                    .transactionDate(transactionMonth)
                    .ruleKind(aux.getRuleKind())
                    .debitAccount(aux.getDebitAccount())
                    .debitSubAccount(aux.getDebitSubAccount())
                    .debitDepartment(aux.getDebitDepartment())
                    .debitTax(aux.getDebitTax())
                    .debitAmount(amount)
                    .creditAccount(aux.getCreditAccount())
                    .creditSubAccount(aux.getCreditSubAccount())
                    .creditDepartment(aux.getCreditDepartment())
                    .creditTax(aux.getCreditTax())
                    .creditAmount(amount)
                    .summary(aux.getSummary())
                    .tag(aux.getTag())
                    .matchStatus("NA")
                    .build());
        }
        int auxCount = csvRows.size() - payableCount;

        if (csvRows.isEmpty()) {
            throw new IllegalArgumentException("CSV 出力可能な行がありません（ルール未登録のみ・補助行なし）: " + skipped);
        }

        // CSV 取引日列は 締め日 = transactionMonth を使用
        byte[] csv = PaymentMfCsvWriter.toCsvBytes(csvRows, transactionMonth);
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3869ms:
-- P1-08: 再取込防止 (警告 + 確定保護) のため 3 列追加
-- L1: 同一ハッシュの過去取込検知用 (preview 警告)
-- L2: applyVerification 実行済み判定用 (preview 警告 + 手動確定行保護)
ALTER TABLE t_payment_mf_import_history
    ADD COLUMN IF NOT EXISTS source_file_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS applied_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS applied_by_user_no INTEGER NULL;

CREATE INDEX IF NOT EXISTS idx_payment_mf_import_history_hash
    ON t_payment_mf_import_history (source_file_hash);

CREATE INDEX IF NOT EXISTS idx_payment_mf_import_history_applied
    ON t_payment_mf_import_history (shop_no, transfer_date) WHERE applied_at IS NOT NULL;

COMMENT ON COLUMN t_payment_mf_import_history.source_file_hash
    IS 'P1-08: 取込元 Excel の SHA-256 (hex)。同一 hash 再取込時に preview 警告';
COMMENT ON COLUMN t_payment_mf_import_history.applied_at
    IS 'P1-08: applyVerification 実行タイムスタンプ。NULL=未確定';
COMMENT ON COLUMN t_payment_mf_import_history.applied_by_user_no
    IS 'P1-08: applyVerification 実行ユーザー (m_user.user_no)';
-- P1-09 案 D: verified_amount の不変条件を DB レベルで明示
COMMENT ON COLUMN t_accounts_payable_summary.verified_amount IS
'振込実績の集約値(税込)。supplier × transaction_month の集約値で、税率別行(tax_rate違い)には同一値が書き込まれる。' ||
'不変条件: 同 (shop, supplier, transactionMonth) の全税率行で本値は同値であること。' ||
'単一行のみ SQL UPDATE すると read 側 sumVerifiedAmountForGroup の重複カウント回避が崩れ過大計上の原因となる。' ||
'手動修正は必ず supplier × txMonth の全行同時に行うこと。' ||
'仕訳生成: 振込仕訳(借方買掛/貸方普通預金、対象外)では集約値として1度参照、仕入仕訳(借方仕入高)では verified_amount_tax_excluded を税率別参照。';

COMMENT ON COLUMN t_accounts_payable_summary.verified_amount_tax_excluded IS
'振込実績の税抜金額(税率別逆算)。V026 (2026-04-23) 追加。verified_amount(税込)を各行の tax_rate で逆算した値で、同 supplier × month の税率別行で異なる値が入る。' ||
'用途: MF CSV 仕入仕訳(借方仕入高)の金額として使用。' ||
'計算式: verified_amount × 100 / (100 + tax_rate) 端数切捨。';
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /i \"public .*convert convertToCsv buildPreview toCsvBytes saveHistory sourceFileHash\" backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content frontend/components/pages/finance/payment-mf-import.tsx | Select-Object -Skip 190 -First 290' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content frontend/types/payment-mf.ts | Select-Object -Skip 120 -First 60' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /i \"V033\" backend\\src\\main\\resources\\db\\migration\\*.sql" in C:\project\odamitsu-data-hub
 succeeded in 1529ms:
63:public class PaymentMfImportService {
109:    public PaymentMfImportService(MPaymentMfRuleRepository ruleRepository,
128:    // Public API
131:    public PaymentMfPreviewResponse preview(MultipartFile file) throws IOException {
156:                    .sourceFileHash(fileHash)
160:            return buildPreview(uploadId, cached);
164:    public PaymentMfPreviewResponse rePreview(String uploadId) {
166:        return buildPreview(uploadId, cached);
172:    public byte[] convert(String uploadId, Integer userNo) {
174:        PaymentMfPreviewResponse preview = buildPreview(uploadId, cached);
186:        byte[] csv = PaymentMfCsvWriter.toCsvBytes(preview.getRows(), txMonth);
187:        saveHistory(cached, preview, csv, userNo);
202:    public VerifyResult applyVerification(String uploadId, Integer userNo) {
328:        // (従来は saveAuxRowsForVerification 内で再 buildPreview していたため N+1 が 2 周していた)。
329:        PaymentMfPreviewResponse preview = buildPreview(uploadId, cached);
371:                    .sourceFileHash(cached.getSourceFileHash())
404:     *                （再 buildPreview で N+1 を再度走らせないため）。
469:    public static class VerifyResult {
484:    public byte[] getHistoryCsv(Integer id) {
490:    public java.util.Optional<LocalDate> getHistoryTransferDate(Integer id) {
499:    public LocalDate getCachedTransferDate(String uploadId) {
547:    public static class VerifiedExportResult {
575:    public VerifiedExportResult exportVerifiedCsv(LocalDate transactionMonth, Integer userNo) {
685:        byte[] csv = PaymentMfCsvWriter.toCsvBytes(csvRows, transactionMonth);
711:    public VerifiedExportPreviewResponse buildVerifiedExportPreview(LocalDate transactionMonth) {
804:    public List<PaymentMfAuxRowResponse> listAuxRows(LocalDate transactionMonth) {
819:    public void saveVerifiedExportHistory(LocalDate transactionMonth,
847:    private PaymentMfPreviewResponse buildPreview(String uploadId, CachedUpload cached) {
1123:        DuplicateWarning duplicateWarning = buildDuplicateWarning(cached.getSourceFileHash());
1153:                .findBySourceFileHashAndDelFlgOrderByAddDateTimeDesc(fileHash, "0")
1338:    private void saveHistory(CachedUpload cached, PaymentMfPreviewResponse preview,
1355:                    // P1-08: convert 経由でも sourceFileHash を残す → 後続 preview の L1 警告で同一 Excel 検知。
1356:                    .sourceFileHash(cached.getSourceFileHash())
1462:        private String sourceFileHash;
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

2026-05-06T05:38:01.590400Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 1.6 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 exited 1 in 1594ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1857ms:
}

export interface AmountReconciliation {
  // チェック1: Excel 合計行の列間整合 (C - F - H = E)
  summaryInvoiceTotal: number   // 合計行 C列
  summaryFee: number            // 合計行 F列
  summaryEarly: number          // 合計行 H列
  summaryTransferAmount: number // 合計行 E列
  expectedTransferAmount: number // C - F - H
  excelDifference: number       // E - (C - F - H)
  excelMatched: boolean

  // チェック2: 明細行 読取り整合 (sum明細 = C合計行)
  preTotalInvoiceSum: number    // 合計行前の明細 請求額合計
  readDifference: number        // sum明細 - C合計行
  readMatched: boolean

  directPurchaseTotal: number   // 合計行後セクション（参考）

  // P1-03 案 D チェック3: per-supplier 振込金額合計 == E(合計行 振込金額)
  perSupplierTransferSum: number
  perSupplierTransferDiff: number
  perSupplierTransferMatched: boolean
  perSupplierFeeSum: number
  perSupplierDiscountSum: number
  perSupplierEarlySum: number
  perSupplierOffsetSum: number
  /** per-supplier 1 円整合性違反行のメッセージ一覧。空なら全行 OK。 */
  perSupplierMismatches: string[]
}

export interface PaymentMfRule {
  id: number
  sourceName: string
  paymentSupplierCode: string | null
  ruleKind: RuleKind
  debitAccount: string
  debitSubAccount: string | null
  debitDepartment: string | null
  debitTaxCategory: string
  creditAccount: string
  creditSubAccount: string | null
  creditDepartment: string | null
  creditTaxCategory: string
  summaryTemplate: string
  tag: string | null
  priority: number
}

export interface PaymentMfRuleRequest {
  sourceName: string
  paymentSupplierCode?: string | null
  ruleKind: RuleKind
  debitAccount: string
  debitSubAccount?: string | null
  debitDepartment?: string | null
  debitTaxCategory: string
  creditAccount?: string
  creditSubAccount?: string | null
  creditDepartment?: string | null
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1924ms:
                <li>例: 2026/1/20 支払 → 取引月 <b>2025-12-20</b></li>
                <li>例: 2026/2/5 支払 → 取引月 <b>2026-01-20</b></li>
                <li>例: 2026/2/20 支払 → 取引月 <b>2026-01-20</b></li>
              </ul>
              ※ 送金日は Excel の <code>E1</code> セルから自動取得します。プレビュー上部の
              「送金日 / 対応取引月」で必ず確認してください。
            </li>
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
                variant="outline"
                disabled={preview.errorCount > 0 || verifyMut.isPending}
                onClick={() => setConfirmVerify(true)}
              >
                <CheckCheck className="mr-1 h-4 w-4" />
                {verifyMut.isPending ? '反映中...' : '買掛金一覧へ反映'}
              </Button>
              <Button onClick={download} disabled={preview.errorCount > 0}>
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
                  <th className="p-1 text-right">請求額<AmountSourceTooltip source="INVOICE" /></th>
                  <th className="p-1 text-right">買掛金<AmountSourceTooltip source="PAYABLE_SUMMARY" /></th>
                  <th className="p-1 text-right">差額</th>
                  <th className="p-1">状態</th>
                  <th className="p-1">借方</th>
                  <th className="p-1">補助</th>
                  <th className="p-1">税区分</th>
                  <th className="p-1">貸方</th>
                  <th className="p-1">摘要</th>
                  <th className="p-1">タグ</th>
                </tr>
              </thead>
              <tbody>
                {preview.rows.map((r, i) => (
                  <tr key={`${r.excelRowIndex ?? 'x'}-${r.paymentSupplierCode ?? 'x'}-${i}`} className={rowBgClass(r)}>
                    <td className="p-1 text-center">{r.excelRowIndex || ''}</td>
                    <td className="p-1 text-center">{r.paymentSupplierCode ?? ''}</td>
                    <td className="p-1">{r.sourceName ?? ''}</td>
                    <td className="p-1 text-right">{r.amount?.toLocaleString() ?? ''}</td>
                    <td className="p-1 text-right">{r.payableAmount?.toLocaleString() ?? ''}</td>
                    <td className="p-1 text-right">
                      {r.payableDiff !== null && r.payableDiff !== 0
                        ? r.payableDiff.toLocaleString()
                        : ''}
                    </td>
                    <td className="p-1 text-center">
                      {r.errorType === 'UNREGISTERED' ? (
                        <span className="text-red-600">未登録</span>
                      ) : (
                        <MatchBadge status={r.matchStatus} supplierNo={r.supplierNo}
                          txMonth={preview.transactionMonth} />
                      )}
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

      <ConfirmDialog
        open={confirmVerify}
        onOpenChange={setConfirmVerify}
        title={preview?.appliedWarning ? '?? 既に確定済の月を再確定' : '買掛金一覧へ反映'}
        description={
          preview?.appliedWarning
            ? `この月は ${formatDateTime(preview.appliedWarning.appliedAt)} に既に確定済です。再確定すると確定済の値を上書きします（手動確定 verified_manually=true 行は保護されます）。続行しますか？`
            : 'この突合結果で買掛金一覧を検証確定します。よろしいですか？（verified_manually=true として手動確定扱いになります）'
        }
        confirmLabel={preview?.appliedWarning ? '上書きして再確定する' : '反映する'}
        onConfirm={() => {
          // ダイアログを即座に閉じることで二重起動を防止する（mutation 完了前の連打対策）。
          setConfirmVerify(false)
          if (preview && !verifyMut.isPending) verifyMut.mutate(preview.uploadId)
        }}
      />
    </div>
  )
}

/**
 * P1-08: ISO 8601 OffsetDateTime を `yyyy-MM-dd HH:mm` (JST 表示) にフォーマットする。
 * パース失敗時は元文字列をそのまま返す (UX を壊さない)。
 */
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /i \"perSupplier excelMatched readMatched amountReconciliation 整合 差額\" frontend\\components\\pages\\finance\\payment-mf-import.tsx" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java | Select-Object -Skip 165 -First 35' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java | Select-Object -Skip 1330 -First 45' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfCsvWriter.java | Select-Object -First 110' in C:\project\odamitsu-data-hub
 succeeded in 1768ms:
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
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1830ms:
package jp.co.oda32.domain.service.finance;

import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewRow;
import jp.co.oda32.exception.FinanceInternalException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 買掛仕入 MF 仕訳 CSV の生成ロジック。
 *
 * <p>文字コードは CP932 (MS932)、改行は LF、金額末尾に半角スペースを付与する
 * 既存運用フォーマットに合わせる。取引日 (transactionDate) は CSV 全行共通で、
 * 小田光の締め日 (= 前月20日, transactionMonth) を渡す運用。送金日は MF の
 * 銀行データ連携側で自動付与されるため CSV には含めない。
 *
 * <p>ステートレスな純粋ユーティリティ。Bean 化せず static メソッドで提供する。
 */
final class PaymentMfCsvWriter {

    private PaymentMfCsvWriter() {}

    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("yyyy/M/d");
    private static final Charset CSV_CHARSET = Charset.forName("MS932");
    private static final String CSV_LINE_END = "\n";

    private static final List<String> CSV_HEADERS = List.of(
            "取引No", "取引日", "借方勘定科目", "借方補助科目", "借方部門", "借方取引先",
            "借方税区分", "借方インボイス", "借方金額(円)",
            "貸方勘定科目", "貸方補助科目", "貸方部門", "貸方取引先",
            "貸方税区分", "貸方インボイス", "貸方金額(円)",
            "摘要", "タグ", "メモ"
    );

    /**
     * 仕訳行リストを CP932 (MS932) + LF の CSV バイト列に変換する。
     * {@code errorType=UNREGISTERED} の行はスキップ。
     *
     * <p>取引日は行ごとに {@link PaymentMfPreviewRow#getTransactionDate()} を優先し、
     * NULL のときは引数の fallback ({@code fallbackTransactionDate}) を使う。
     *
     * @param rows                    出力対象の仕訳行
     * @param fallbackTransactionDate 行に transactionDate が無い場合のフォールバック
     *                                （通常は締め日 = transactionMonth）
     * @return CSV バイト列（CP932 エンコード）
     */
    static byte[] toCsvBytes(List<PaymentMfPreviewRow> rows, LocalDate fallbackTransactionDate) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStreamWriter w = new OutputStreamWriter(baos, CSV_CHARSET)) {
            w.write(String.join(",", CSV_HEADERS));
            w.write(CSV_LINE_END);
            String fallback = fallbackTransactionDate == null ? "" : fallbackTransactionDate.format(CSV_DATE);
            for (PaymentMfPreviewRow r : rows) {
                if ("UNREGISTERED".equals(r.getErrorType())) continue;
                String date = r.getTransactionDate() != null
                        ? r.getTransactionDate().format(CSV_DATE) : fallback;
                w.write(toCsvLine(r, date));
                w.write(CSV_LINE_END);
            }
        } catch (IOException e) {
            // T5: I/O エラー (内部) を専用例外に置換。
            throw new FinanceInternalException("CSV出力に失敗しました", e);
        }
        return baos.toByteArray();
    }

    private static String toCsvLine(PaymentMfPreviewRow r, String date) {
        List<String> cols = new ArrayList<>(19);
        cols.add("");                                // 取引No
        cols.add(date);                              // 取引日
        cols.add(safe(r.getDebitAccount()));
        cols.add(safe(r.getDebitSubAccount()));
        cols.add(safe(r.getDebitDepartment()));
        cols.add("");                                // 借方取引先
        cols.add(safe(r.getDebitTax()));
        cols.add("");                                // 借方インボイス
        cols.add(fmtAmount(r.getDebitAmount()));
        cols.add(safe(r.getCreditAccount()));
        cols.add(safe(r.getCreditSubAccount()));
        cols.add(safe(r.getCreditDepartment()));
        cols.add("");                                // 貸方取引先
        cols.add(safe(r.getCreditTax()));
        cols.add("");                                // 貸方インボイス
        cols.add(fmtAmount(r.getCreditAmount()));
        cols.add(safe(r.getSummary()));
        cols.add(safe(r.getTag()));
        cols.add("");                                // メモ
        return String.join(",", cols);
    }

    private static String fmtAmount(Long v) {
        // 金額末尾の半角スペースは既存運用 CSV の不変条件。null 入力でも 0 + " " を返し、
        // 「列のうち1セルだけスペース欠落」が起きないようにする (SF-C02)。
        long amount = v == null ? 0L : v;
        return amount + " ";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2859ms:
     * 締め日(= 前月20日, transactionMonth)を渡す運用。送金日ではない点に注意
     * （送金日は MF の銀行データ連携側で自動付与される）。
     */
    // ===========================================================
    // History
    // ===========================================================

    private void saveHistory(CachedUpload cached, PaymentMfPreviewResponse preview,
                             byte[] csv, Integer userNo) {
        try {
            String yyyymmdd = cached.getTransferDate() == null ? "unknown"
                    : cached.getTransferDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String csvFile = "買掛仕入MFインポートファイル_" + yyyymmdd + ".csv";
            TPaymentMfImportHistory h = TPaymentMfImportHistory.builder()
                    .shopNo(FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO)
                    .transferDate(cached.getTransferDate())
                    .sourceFilename(cached.getFileName())
                    .csvFilename(csvFile)
                    .rowCount(preview.getTotalRows())
                    .totalAmount(preview.getTotalAmount())
                    .matchedCount(preview.getMatchedCount())
                    .diffCount(preview.getDiffCount())
                    .unmatchedCount(preview.getUnmatchedCount())
                    .csvBody(csv)
                    // P1-08: convert 経由でも sourceFileHash を残す → 後続 preview の L1 警告で同一 Excel 検知。
                    .sourceFileHash(cached.getSourceFileHash())
                    .addDateTime(LocalDateTime.now())
                    .addUserNo(userNo)
                    .build();
            historyRepository.save(h);
        } catch (Exception e) {
            log.error("変換履歴の保存に失敗（CSV出力は正常完了）", e);
        }
    }

    // ===========================================================
    // Helpers
    // ===========================================================

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("ファイルが空です");
        if (file.getSize() > MAX_UPLOAD_BYTES)
            throw new IllegalArgumentException("ファイルサイズ上限(20MB)を超過しています");
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".xlsx"))
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

2026-05-06T05:38:15.872637Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 3.6 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 exited 1 in 3605ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /i \"applyVerification convert preview\" backend\\src\\main\\java\\jp\\co\\oda32\\api\\finance\\PaymentMfImportController.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/api/finance/PaymentMfImportController.java | Select-Object -First 140' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /i \"PaymentMfAuxRow rule_kind chk_payment_mf_aux_rule_kind DIRECT_PURCHASE_FEE PAYABLE_FEE\" backend\\src\\main\\resources\\db\\migration\\*.sql backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\TPaymentMfAuxRow.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /i \"VERIFICATION_NOTE_BULK_PREFIX\" backend\\src\\main\\java\\jp\\co\\oda32\\constant\\FinanceConstants.java backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\*.java" in C:\project\odamitsu-data-hub
 succeeded in 1666ms:
10:import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewResponse;
13:import jp.co.oda32.dto.finance.paymentmf.VerifiedExportPreviewResponse;
44:    @PostMapping("/preview")
45:    public ResponseEntity<?> preview(@RequestParam("file") MultipartFile file) {
47:            PaymentMfPreviewResponse res = importService.preview(file);
58:    @PostMapping("/preview/{uploadId}")
59:    public ResponseEntity<?> rePreview(@PathVariable String uploadId) {
61:            return ResponseEntity.ok(importService.rePreview(uploadId));
68:    @PostMapping("/convert/{uploadId}")
69:    public ResponseEntity<?> convert(@PathVariable String uploadId,
75:            byte[] csv = importService.convert(uploadId, user == null ? null : user.getUser().getLoginUserNo());
99:            var result = importService.applyVerification(
161:    @GetMapping("/export-verified/preview")
162:    public ResponseEntity<?> exportVerifiedPreview(
166:            VerifiedExportPreviewResponse preview = importService.buildVerifiedExportPreview(txMonth);
167:            return ResponseEntity.ok(preview);
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1993ms:
package jp.co.oda32.api.finance;

import jakarta.validation.Valid;
import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.service.data.LoginUser;
import jp.co.oda32.domain.service.finance.PaymentMfImportService;
import jp.co.oda32.domain.service.finance.PaymentMfRuleService;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfAuxRowResponse;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfHistoryResponse;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewResponse;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfRuleRequest;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfRuleResponse;
import jp.co.oda32.dto.finance.paymentmf.VerifiedExportPreviewResponse;
import jp.co.oda32.domain.repository.finance.TPaymentMfImportHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
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

    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PostMapping("/verify/{uploadId}")
    public ResponseEntity<?> verify(@PathVariable String uploadId,
                                    @AuthenticationPrincipal LoginUser user) {
        try {
            var result = importService.applyVerification(
                    uploadId, user == null ? null : user.getUser().getLoginUserNo());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
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
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2794ms:
backend\src\main\java\jp\co\oda32\constant\FinanceConstants.java:61:    public static final String VERIFICATION_NOTE_BULK_PREFIX = "振込明細検証 ";
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:210:        String note = FinanceConstants.VERIFICATION_NOTE_BULK_PREFIX
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:267:            //   ただし bulk verify 由来 (verification_note が VERIFICATION_NOTE_BULK_PREFIX 始まり) は
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:275:                                        FinanceConstants.VERIFICATION_NOTE_BULK_PREFIX)));
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3614ms:
backend\src\main\resources\db\migration\V011__create_payment_mf_tables.sql:8:    rule_kind               VARCHAR(20)  NOT NULL,   -- 'PAYABLE' | 'EXPENSE' | 'DIRECT_PURCHASE'
backend\src\main\resources\db\migration\V011__create_payment_mf_tables.sql:58:    (source_name, payment_supplier_code, rule_kind, debit_account, debit_sub_account, debit_department, debit_tax_category,
backend\src\main\resources\db\migration\V011__create_payment_mf_tables.sql:159:    (source_name, payment_supplier_code, rule_kind, debit_account, debit_sub_account, debit_department, debit_tax_category,
backend\src\main\resources\db\migration\V016__create_payment_mf_aux_row.sql:12:    rule_kind             VARCHAR(20)  NOT NULL,
backend\src\main\resources\db\migration\V016__create_payment_mf_aux_row.sql:32:    CONSTRAINT chk_payment_mf_aux_rule_kind
backend\src\main\resources\db\migration\V016__create_payment_mf_aux_row.sql:33:        CHECK (rule_kind IN ('EXPENSE','SUMMARY','DIRECT_PURCHASE'))
backend\src\main\resources\db\migration\V016__create_payment_mf_aux_row.sql:45:COMMENT ON COLUMN t_payment_mf_aux_row.rule_kind         IS 'EXPENSE / SUMMARY / DIRECT_PURCHASE';
backend\src\main\java\jp\co\oda32\domain\model\finance\TPaymentMfAuxRow.java:30:public class TPaymentMfAuxRow {
backend\src\main\java\jp\co\oda32\domain\model\finance\TPaymentMfAuxRow.java:49:    @Column(name = "rule_kind", nullable = false, length = 20)
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java | Select-Object -First 105' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/AccountsPayableIntegrityService.java | Select-Object -Skip 535 -First 55' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /i \"AccountsPayableIntegrityService\" backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\SupplierBalancesService.java backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\mf\\MfOpeningBalanceService.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/TAccountsPayableSummary.java | Select-Object -Skip 80 -First 50' in C:\project\odamitsu-data-hub
 succeeded in 1517ms:
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:233:        // ここでも除外する。これにより MfSupplierLedgerService / AccountsPayableIntegrityService /
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1780ms:
package jp.co.oda32.domain.service.finance;

import jp.co.oda32.constant.FinanceConstants;
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
import jp.co.oda32.domain.service.finance.mf.MfReAuthRequiredException;
import jp.co.oda32.domain.service.finance.mf.MfScopeInsufficientException;
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
    /** MINOR 上限: 100 < diff ? 1000 → MINOR、1000 超 → MAJOR。 */
    private static final BigDecimal MINOR_UPPER = BigDecimal.valueOf(1000);

    private final MfOauthService mfOauthService;
    private final MfJournalCacheService journalCache;
    private final MfAccountMasterRepository mfAccountMasterRepository;
    private final TAccountsPayableSummaryRepository summaryRepository;
    private final MPaymentSupplierService paymentSupplierService;
    private final SupplierBalancesService supplierBalancesService;
    private final ConsistencyReviewService consistencyReviewService;
    /**
     * P1-02 (DD-BGH-01): 期首残 (m_supplier_opening_balance) を整合性レポートの cumulative diff にも
     * 反映させるための明示依存。実値の注入は {@link SupplierBalancesService#generate} 経由
     * (両サービスで同じ {@code openingBalanceService.getEffectiveBalanceMap} を呼び出すため diff 値が一致) で行うが、
     * 整合性レポートが期首残を「使っている」契約を本依存で表面化させ、検証ログに opening 件数を出力する。
     */
    private final MfOpeningBalanceService openingBalanceService;

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
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1875ms:
                                || mfDiff.compareTo(FinanceConstants.MATCH_TOLERANCE) > 0);
            } else {
                e.setSnapshotStale(false);
            }
        }
    }

    /**
     * 軸 D ({@link SupplierBalancesService}) を呼び、toMonth 時点での supplier 単位累積 diff (self.closing - mfBalance) を
     * map で返す。このマップは各 entry に supplierCumulativeDiff として併記され、UI に「累積差: \25」のように表示される。
     * 加えて diff == 0 の supplier は reconciledAtPeriodEnd=true 扱い (ノイズ抑制)。
     * <p>
     * <b>P1-02 (DD-BGH-01) 期首残注入方針:</b>
     * {@link SupplierBalancesService#generate} は内部で {@link MfOpeningBalanceService#getEffectiveBalanceMap}
     * (shopNo, {@code SELF_BACKFILL_START}=2025-06-20) を呼び self.opening / self.closing に加算する。
     * したがって本メソッドが返す cumulative diff は <strong>累積残一覧 ({@code /finance/supplier-balances})
     * と同じ値</strong>になる (整合性レポートと累積残一覧で同 supplier の累積差が一致することを保証)。
     * <ul>
     *   <li>self 側: {@code m_supplier_opening_balance.effectiveBalance} を期首残として注入</li>
     *   <li>MF 側: journal #1 (期首残高仕訳) は {@link MfJournalFetcher#isPayableOpeningJournal} で
     *       accumulation から除外 (SF-G01)。opening 二重計上を防止</li>
     * </ul>
     * 整合性レポート本体の per-month delta 比較 (selfDelta vs mfDelta) は期間内の差分のみを評価するため
     * opening は不要だが、reconciledAtPeriodEnd 判定と UI 併記用途で本メソッドの累積 diff を使用する。
     * <p>
     * MF /journals は {@link MfJournalCacheService} 共有のため、既に integrity 側で取得済み期間と重複する部分はキャッシュヒット。
     */
    private Map<Integer, BigDecimal> buildSupplierCumulativeDiffMap(Integer shopNo, LocalDate toMonth, boolean refresh) {
        try {
            // P1-02: 期首残ありの supplier 数を事前ログ出力 (注入経路の可視化)。
            // 実際の self.closing への加算は SupplierBalancesService.generate() 内部で行われる
            // (両サービスで openingBalanceService.getEffectiveBalanceMap を呼ぶため値は一致する)。
            Map<Integer, BigDecimal> openingMap = openingBalanceService.getEffectiveBalanceMap(
                    shopNo, MfPeriodConstants.SELF_BACKFILL_START);
            log.info("[integrity] opening 注入確認: shopNo={} openingDate={} supplier 数={}",
                    shopNo, MfPeriodConstants.SELF_BACKFILL_START, openingMap.size());

            SupplierBalancesResponse bal = supplierBalancesService.generate(shopNo, toMonth, refresh);
            Map<Integer, BigDecimal> map = new HashMap<>();
            for (SupplierBalanceRow r : bal.getRows()) {
                if (r.getSupplierNo() != null && r.getDiff() != null) {
                    map.put(r.getSupplierNo(), r.getDiff());
                }
            }
            long reconciled = map.values().stream().filter(d -> d.signum() == 0).count();
            log.info("[integrity] cumulativeDiff @ toMonth={} : {} supplier, うち diff=0 が {} 件 (累積残一覧と同値)",
                    toMonth, map.size(), reconciled);
            return map;
        } catch (MfReAuthRequiredException | MfScopeInsufficientException e) {
            // MF 認可系は上位 (FinanceExceptionHandler) で 401/403 に変換させるため握りつぶさない (SF-27)
            throw e;
        } catch (Exception e) {
            log.warn("[integrity] 期末累積残判定スキップ (計算失敗): {}", e.getMessage());
            return Map.of();
        }
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2910ms:

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
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /i \"setVerifiedAmount setVerifiedAmountTaxExcluded verified_amount\" backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\*.java backend\\src\\main\\java\\jp\\co\\oda32\\batch\\finance\\*.java backend\\src\\main\\java\\jp\\co\\oda32\\api\\finance\\*.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /i \"verifiedAmount\" backend\\src\\main\\java\\jp\\co\\oda32\\dto\\finance\\*.java backend\\src\\main\\java\\jp\\co\\oda32\\api\\finance\\*.java backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\TAccountsPayableSummaryService.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n /i \"paymentDifference verificationNote verifiedManually mfExportEnabled verifiedAmount\" backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\TAccountsPayableSummaryService.java backend\\src\\main\\java\\jp\\co\\oda32\\api\\finance\\FinanceController.java backend\\src\\main\\java\\jp\\co\\oda32\\dto\\finance\\AccountsPayableVerifyRequest.java" in C:\project\odamitsu-data-hub
 succeeded in 1491ms:
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableLedgerService.java:36: * verified_amount 集約は Phase B' の「全行同値なら代表値、不一致なら SUM」準拠。
backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableLedgerService.java:204:     * verified_amount 集約: 全行同値なら代表値、不一致なら SUM (Phase B' 準拠)。
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:33: * MF_APPLY: 対象 summary 行の verified_amount を MF 金額に合わせる。税率別に按分。
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:34: * DELETE / IGNORE 切替: MF_APPLY で書き換えた verified_amount を previous から復元。
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:152:     * MF_APPLY の副作用: 対象 summary 行の verified_amount を MF 金額に合わせる。
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:154:     *   <li>selfOnly: 全税率行 verified_amount=0 (自社取消)</li>
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:158:     * @return 更新前の verified_amount 退避 (税率 → 金額 Map)
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:184:                r.setVerifiedAmount(BigDecimal.ZERO);
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:185:                r.setVerifiedAmountTaxExcluded(BigDecimal.ZERO);
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:224:                r.setVerifiedAmount(allocated);
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:226:                r.setVerifiedAmountTaxExcluded(
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:238:                largest.setVerifiedAmount(nz(largest.getVerifiedAmount()).add(diff));
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:240:                largest.setVerifiedAmountTaxExcluded(
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:269:                r.setVerifiedAmount(prev);
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:273:                    r.setVerifiedAmountTaxExcluded(
backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java:276:                    r.setVerifiedAmountTaxExcluded(null);
backend\src\main\java\jp\co\oda32\domain\service\finance\PayableBalanceCalculator.java:20: * effectiveChange: 手動確定行 (verified_manually=true) なら verified_amount 優先、
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:270:            // (税率別 verified_amount/note を分割保持していないため、混在状態を作らないこと優先)。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:295:            // UI 表示用途のため、全税率行に同じ verified_amount を書き込む
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:297:            // V026: 税抜側 (verified_amount_tax_excluded) も税率別に逆算して書き込む
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:311:                s.setVerifiedAmount(invoice);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:317:                s.setVerifiedAmountTaxExcluded(invoiceTaxExcl);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:515:     *   <li>振込明細一括検証では全行に同値の verified_amount が書き込まれるため、
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:720:        // supplier_no 単位に集約。税率別複数行は verified_amount を SUM する
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:165:        summary.setVerifiedAmount(verifiedAmount);
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1498ms:
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:113:     * verifiedManually=true をセットし、次回 SMILE 再検証バッチで上書きされないようにします。
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:119:            BigDecimal verifiedAmount, String note) {
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:124:        applyVerification(summary, verifiedAmount);
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:125:        summary.setVerifiedManually(Boolean.TRUE);
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:126:        summary.setVerificationNote(note);
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:129:        summary.setMfExportEnabled(Integer.valueOf(1).equals(summary.getVerificationResult()));
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:145:        summary.setVerifiedManually(Boolean.FALSE);
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:158:        summary.setMfExportEnabled(enabled);
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:162:    private void applyVerification(TAccountsPayableSummary summary, BigDecimal verifiedAmount) {
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:165:        summary.setVerifiedAmount(verifiedAmount);
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:166:        summary.setTaxIncludedAmount(verifiedAmount);
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:180:        BigDecimal taxExcluded = verifiedAmount.divide(divisor, 0, RoundingMode.DOWN);
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:186:        BigDecimal difference = verifiedAmount.subtract(baseTaxIncluded);
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:187:        summary.setPaymentDifference(difference);
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:207:        if (summary.getPaymentDifference() != null) {
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:208:            summary.setPaymentDifference(summary.getPaymentDifference().setScale(0, RoundingMode.DOWN));
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:227:            if (summary.getPaymentDifference() != null) {
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:228:                summary.setPaymentDifference(summary.getPaymentDifference().setScale(0, RoundingMode.DOWN));
backend\src\main\java\jp\co\oda32\api\finance\FinanceController.java:145:                request.getVerifiedAmount(), request.getNote());
backend\src\main\java\jp\co\oda32\dto\finance\AccountsPayableVerifyRequest.java:14:    private BigDecimal verifiedAmount;
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1591ms:
backend\src\main\java\jp\co\oda32\dto\finance\AccountsPayableLedgerResponse.java:47:        private BigDecimal verifiedAmount;
backend\src\main\java\jp\co\oda32\dto\finance\AccountsPayableResponse.java:31:    private BigDecimal verifiedAmount;
backend\src\main\java\jp\co\oda32\dto\finance\AccountsPayableResponse.java:33:    private BigDecimal verifiedAmountTaxExcluded;
backend\src\main\java\jp\co\oda32\dto\finance\AccountsPayableResponse.java:67:     * effectiveChange は verifiedManually=true なら verifiedAmount、それ以外は taxIncludedAmountChange。
backend\src\main\java\jp\co\oda32\dto\finance\AccountsPayableResponse.java:115:                .verifiedAmount(ap.getVerifiedAmount())
backend\src\main\java\jp\co\oda32\dto\finance\AccountsPayableResponse.java:116:                .verifiedAmountTaxExcluded(ap.getVerifiedAmountTaxExcluded())
backend\src\main\java\jp\co\oda32\dto\finance\AccountsPayableVerifyRequest.java:14:    private BigDecimal verifiedAmount;
backend\src\main\java\jp\co\oda32\dto\finance\ConsistencyReviewResponse.java:33:    private Boolean verifiedAmountUpdated;
backend\src\main\java\jp\co\oda32\api\finance\FinanceController.java:145:                request.getVerifiedAmount(), request.getNote());
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:119:            BigDecimal verifiedAmount, String note) {
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:124:        applyVerification(summary, verifiedAmount);
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:162:    private void applyVerification(TAccountsPayableSummary summary, BigDecimal verifiedAmount) {
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:165:        summary.setVerifiedAmount(verifiedAmount);
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:166:        summary.setTaxIncludedAmount(verifiedAmount);
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:180:        BigDecimal taxExcluded = verifiedAmount.divide(divisor, 0, RoundingMode.DOWN);
backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java:186:        BigDecimal difference = verifiedAmount.subtract(baseTaxIncluded);
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsPayableSummaryService.java | Select-Object -Skip 100 -First 95' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/ConsistencyReviewService.java | Select-Object -Skip 145 -First 105' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java | Select-Object -Skip 705 -First 45' in C:\project\odamitsu-data-hub
 succeeded in 1773ms:
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
                // SF-03: V026 列の振込明細 Excel 由来 stale 値をクリア (UI バッジ誤表示防止)。
                r.setAutoAdjustedAmount(BigDecimal.ZERO);
                r.setMfTransferDate(null);
                // paymentDifference = verifiedAmount(=0) - taxIncludedAmountChange
                r.setPaymentDifference(BigDecimal.ZERO.subtract(nz(r.getTaxIncludedAmountChange())));
            }
            // SF-15: ループ後に saveAll で 1 回だけ永続化
            summaryRepository.saveAll(rows);
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
                // SF-03: V026 列の振込明細 Excel 由来 stale 値をクリア (UI バッジ誤表示防止)。
                r.setAutoAdjustedAmount(BigDecimal.ZERO);
                r.setMfTransferDate(null);
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
            // SF-03: paymentDifference を上書き後の verifiedAmount で再計算
            // (式は TAccountsPayableSummaryService と同一: verifiedAmount - taxIncludedAmountChange)
            for (TAccountsPayableSummary r : rows) {
                r.setPaymentDifference(nz(r.getVerifiedAmount()).subtract(nz(r.getTaxIncludedAmountChange())));
            }
            // SF-15: ループ後に saveAll で 1 回だけ永続化 (端数吸収も含めて漏れなし)
            summaryRepository.saveAll(rows);
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1832ms:
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
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1830ms:
    /**
     * 検証済みCSV出力ダイアログのプレビュー情報を返す（ダウンロード前の件数確認用）。
     * 5日/20日 片方の振込明細が未取込の場合は警告文字列を含める。
     */
    @Transactional(readOnly = true)
    public VerifiedExportPreviewResponse buildVerifiedExportPreview(LocalDate transactionMonth) {
        if (transactionMonth == null) throw new IllegalArgumentException("transactionMonth が未指定です");

        List<TAccountsPayableSummary> dbRows = payableRepository.findVerifiedForMfExport(
                FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth);
        List<TPaymentMfAuxRow> auxRows = auxRowRepository
                .findByShopNoAndTransactionMonthOrderByTransferDateAscSequenceNoAsc(
                        FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth);

        // supplier_no 単位に集約。税率別複数行は verified_amount を SUM する
        // （手動 verify で税率別に異なる値が入り得るため、代表1行採用では乖離する）。
        Map<Integer, List<TAccountsPayableSummary>> bySupplierNo = dbRows.stream()
                .collect(Collectors.groupingBy(TAccountsPayableSummary::getSupplierNo, LinkedHashMap::new, Collectors.toList()));

        List<MPaymentMfRule> rules = ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0");
        Map<String, MPaymentMfRule> byCode = new LinkedHashMap<>();
        for (MPaymentMfRule r : rules) {
            if (r.getPaymentSupplierCode() != null && !r.getPaymentSupplierCode().isBlank()) {
                byCode.putIfAbsent(r.getPaymentSupplierCode().trim(), r);
            }
        }

        int payableCount = 0;
        long payableTotal = 0L;
        List<String> skipped = new ArrayList<>();
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
            payableCount++;
            payableTotal += amount;
        }

        // 補助行の内訳を (transferDate, ruleKind) でグルーピング
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

  ✓ 対象ファイルと差分範囲を確認
  ✓ 経理ロジックと仕訳構造をレビュー
  ✓ migration・監査保護をレビュー
  → 指摘を severity 付きで整理
  ✓ 対象ファイルと差分範囲を確認
  ✓ 経理ロジックと仕訳構造をレビュー
  ✓ migration・監査保護をレビュー
  ✓ 指摘を severity 付きで整理
codex
以下、Group 2 対象の批判的レビューです。実装変更・テスト実行はしていません。

## 指摘

### 1. DIRECT_PURCHASE_* 副行が確定後の DB-only CSV 出力で消える
- Severity: Critical
- カテゴリ: 業務整合 / 仕訳構造
- 内容: `convert(uploadId)` は `preview.getRows()` をそのまま CSV 化するため `DIRECT_PURCHASE_FEE/DISCOUNT/EARLY/OFFSET` が出ます。一方、`applyVerification()` 後の `exportVerifiedCsv()` 経路では `saveAuxRowsForVerification()` が `DIRECT_PURCHASE_*` を保存対象外にしており、DB から再出力すると 20日払いの送料・値引・早払・相殺副行が消えます。つまり「Excel→CSV」と「Excel→確定→検証済CSV」で会計仕訳が一致しません。
- 推奨アクション: `t_payment_mf_aux_row` の制約・型を拡張して `DIRECT_PURCHASE_*` を保存し、`exportVerifiedCsv()` でも再現してください。保存しない設計なら、確定後 CSV 出力を機能上禁止または明示的に不完全扱いにすべきです。

### 2. verified_amount 不変条件は実装上すでに崩れる経路がある
- Severity: Critical
- カテゴリ: 業務整合 / 設計
- 内容: P1-09 は「supplier × month の全税率行で `verified_amount` 同値」を不変条件として文書化していますが、`TAccountsPayableSummaryService.verify()` は PK 1 行だけを更新します。また `ConsistencyReviewService.applyMfOverride()` は税率別に按分して `verifiedAmount` を別値にします。つまり manual SQL UPDATE 以外にも、正規アプリ経路で不変条件が崩れます。
- 推奨アクション: 不変条件を「一括振込明細検証由来に限る」に修正するか、集約値テーブルへ分離してください。現状の Javadoc/COMMENT は実装と矛盾しています。

### 3. `sumVerifiedAmountForGroup()` の全行同値判定はデータ由来を見ていない
- Severity: Major
- カテゴリ: 業務整合 / 設計
- 内容: `verified_amount` が全行同値なら代表値、異なれば SUM という推定ロジックです。偶然、手動 verify の税率別金額が同額になった場合は過少出力します。逆に一括検証行のうち 1 行だけ後から修正されると SUM になり、過大出力します。
- 推奨アクション: `verified_amount_mode` や `verification_source` のような明示フラグで「集約値」か「税率別値」かを判定してください。金額パターンから推定するのは会計出力には弱いです。

### 4. per-supplier 整合性不一致が UI で実質見えない
- Severity: Major
- カテゴリ: 業務整合 / UX
- 内容: `AmountReconciliation.perSupplierMismatches` は DTO/type には追加されていますが、`payment-mf-import.tsx` 側で `amountReconciliation` を表示している箇所が見当たりません。さらに `convert()` / `applyVerification()` は `perSupplierMismatches` をブロック条件にしていません。「請求 - 値引 - 早払 - 送料 - 相殺 = 振込」の 1 円不一致があっても、ユーザーが気づかず CSV 出力・反映できます。
- 推奨アクション: preview 上に赤警告を出し、少なくとも `convert()` はサーバー側で不一致時 422 にしてください。apply は業務判断で上書き可能にする場合でも明示確認フラグを API に持たせるべきです。

### 5. 5日払い合計行と20日払い合計行の扱いが破綻しやすい
- Severity: Major
- カテゴリ: 業務整合
- 内容: parser は最初の合計行だけを `summaryCaptured` として保持し、その後はすべて `afterTotal=true` にします。Excel に 5日払い合計行と20日払い合計行が両方ある場合、2つ目の合計は無視され、20日払い明細は `sumPerSupplierTransfer` に合算されます。その一方で比較先の `summaryTransfer` は最初の合計行だけなので、チェック3は構造的にズレます。
- 推奨アクション: セクションを 5日払い・20日払いで明示モデル化し、それぞれの合計行を別々に保持・照合してください。「合計行以降」だけで20日払い判定するのは脆いです。

### 6. 合計行に値があり per-supplier 列が NULL のケースを検知できない
- Severity: Major
- カテゴリ: 業務整合
- 内容: per-supplier 列が NULL の場合は 0 扱いされ、`transferAmount` が NULL なら `amount - deduction` で主行金額を派生します。この場合、合計行に値引・早払・送料が入っていても supplier 別内訳がない状態を正しくエラー化できません。旧 SUMMARY 集約行も撤去されているため、内訳欠落時に控除仕訳自体が落ちる可能性があります。
- 推奨アクション: 合計行の控除合計が非ゼロなのに per-supplier 合計が一致しない場合はブロックしてください。NULL と 0 は区別して警告に出すべきです。

### 7. PAYABLE→DIRECT_PURCHASE 自動降格で rule の一部情報が落ちる
- Severity: Major
- カテゴリ: 業務整合 / 設計
- 内容: 自動降格時に `summaryTemplate/tag/creditDepartment` は継承していますが、`paymentSupplierCode`、`creditSubAccount`、`priority`、`id` などは落ちます。preview 表示・CSV 出力だけなら見えにくいですが、supplier 紐付けや将来の保存処理では追跡性が弱くなります。特に `paymentSupplierCode` は親 row builder では Excel 側 `e.supplierCode` に依存します。
- 推奨アクション: 降格用 DTO/コピー関数を作り、継承する項目と意図的に捨てる項目を明文化してください。少なくとも `paymentSupplierCode` は保持すべきです。

### 8. DIRECT_PURCHASE 主行の貸方「資金複合」は5日払いの普通預金と運用差が大きい
- Severity: Major
- カテゴリ: 仕訳構造
- 内容: 5日払い PAYABLE 主行は貸方「普通預金」ですが、20日払い DIRECT_PURCHASE 主行は貸方「資金複合」です。銀行データ連携前提なら普通預金を直接立てない意図は理解できますが、5日払い側との説明が揃っていません。MF 上で資金複合を消し込む後続処理が明示されていないと、監査時に残高説明が難しくなります。
- 推奨アクション: 5日払い・20日払いで貸方科目が異なる理由と、MF 側での消込手順を設計書に明記してください。可能なら支払系の貸方科目を統一してください。

### 9. DIRECT_PURCHASE の値引・早払副行は会計表現として不自然
- Severity: Major
- カテゴリ: 仕訳構造
- 内容: DIRECT_PURCHASE_* は借方「仕入高」、貸方「仕入値引・戻し高」または「早払収益」です。同一取引内で「仕入高を増やしながら値引を貸方計上」する形は総額主義として説明可能な余地はありますが、即払い仕入で実質値引後の純額仕入と見る運用とはズレます。税区分も「課税仕入 10%」と「課税仕入-返還等 10%」が同時発生するため、税理士確認なしでは危険です。
- 推奨アクション: 税理士確認済みの仕訳パターンとして文書化してください。未確認なら DIRECT_PURCHASE は純額仕入または買掛経由に寄せる方が監査説明しやすいです。

### 10. OFFSET の貸方「仕入値引・戻し高」は暫定のまま本番仕訳に出る
- Severity: Major
- カテゴリ: 仕訳構造
- 内容: コード上も TODO で「相殺の貸方科目は税理士確認」とされていますが、実際には `PAYABLE_OFFSET` / `DIRECT_PURCHASE_OFFSET` として CSV に出力されます。相殺は値引ではなく、未収金、立替金、売掛金、仮払金、仕入先負担金など相手科目が異なる可能性があります。暫定科目で自動仕訳化するのは会計リスクが高いです。
- 推奨アクション: OFFSET は科目未確定ならブロックまたは専用マスタで相手科目を選択させてください。最低限、値引と同じ税区分でよいかを確認済みにする必要があります。

### 11. P1-08 は「再取込防止」ではなく「警告」に留まっている
- Severity: Major
- カテゴリ: 監査 / UX
- 内容: duplicate hash も applied warning も preview 表示のみで、`convert()` / `applyVerification()` のサーバー側確定保護はありません。フロントの ConfirmDialog で再確定は確認されますが、API 直叩きや二重操作は止まりません。名称が「防止」なら実装強度が不足しています。
- 推奨アクション: applied 済みの場合は `force=true` など明示パラメータなしでは 409/422 にしてください。duplicate hash も再取込許可理由を履歴に残すのが望ましいです。

### 12. `verified_manually` 保護判定は note 文字列依存で偽判定があり得る
- Severity: Major
- カテゴリ: 監査 / 設計
- 内容: bulk 由来かどうかを `verification_note.startsWith(VERIFICATION_NOTE_BULK_PREFIX)` で判定しています。ユーザーが個別 verify の note に同じ接頭辞を入力すると bulk 扱いで上書きされます。逆に過去 bulk の note フォーマット変更・NULL 化があると単一 verify 扱いで保護されます。
- 推奨アクション: `verification_source` や `verified_by_flow` の列を追加し、文字列ではなく enum 的に判定してください。note は監査説明文であり制御フラグに使うべきではありません。

### 13. V034 の history 保存失敗を握りつぶすため applied warning が欠落し得る
- Severity: Major
- カテゴリ: 監査
- 内容: `saveAppliedHistory()` は例外をログだけにして本体成功扱いにします。この場合、買掛金は上書き済みなのに `applied_at` 履歴がなく、次回 preview の AppliedWarning が出ません。監査・再取込防止の根拠データとしては弱いです。
- 推奨アクション: applied history 保存は同一トランザクションで必須成功にしてください。失敗時に本体反映もロールバックするのが自然です。

### 14. migration 連番に `V033` 欠番がある
- Severity: Major
- カテゴリ: スキーマ
- 内容: 現在の `backend/src/main/resources/db/migration` では `V030, V031, V032, V034, V035, V036` が見え、`V033` がありません。ユーザー説明では V033 が P1-05 として存在する前提なので、作業ツリーの欠落またはブランチ衝突が疑われます。Flyway は欠番自体は許容しますが、並行 feature branch では番号衝突・適用漏れの温床です。
- 推奨アクション: V033 の所在を確認し、main 取り込み順を整理してください。未適用なら migration 番号を振り直すか、明示的に欠番理由を残してください。

### 15. V034 の index は実クエリに十分合っていない
- Severity: Minor
- カテゴリ: スキーマ
- 内容: `idx_payment_mf_import_history_applied` は `(shop_no, transfer_date) WHERE applied_at IS NOT NULL` ですが、repository は `ORDER BY applied_at DESC LIMIT 1` 相当です。適用済み履歴が増えるほど同一振込日の複数行を並べ替える可能性があります。また hash index は `source_file_hash` 単独で NULL も含むため、実データが増えると無駄があります。
- 推奨アクション: applied は `(shop_no, transfer_date, applied_at DESC) WHERE applied_at IS NOT NULL`、hash は `WHERE source_file_hash IS NOT NULL` を検討してください。

### 16. `applied_at IS NULL` の履歴は AppliedWarning 対象外で、convert 済みは検知できない
- Severity: Minor
- カテゴリ: 監査
- 内容: convert 経由の履歴は `source_file_hash` は残りますが `applied_at` は NULL のため AppliedWarning には出ません。これは「買掛金一覧へ反映済み」の警告としては正しい一方、「MF CSV をすでに作った/取り込んだ可能性」は L1 hash が一致する場合にしか出ません。ファイルを少し編集した再出力は検知できません。
- 推奨アクション: 「CSV 生成済」と「買掛反映済」を別警告に分け、transferDate/totalAmount/rowCount など内容 fingerprint でも検知してください。

### 17. SHA-256 の粒度は業務重複検知として粗い
- Severity: Minor
- カテゴリ: 監査
- 内容: ファイル全バイト SHA-256 は完全一致検知には強いですが、Excel のメタ情報や保存形式変更だけで別 batch になります。逆に業務内容が同じでも hash は変わります。再取込防止という業務目的には「行内容 fingerprint」も必要です。
- 推奨アクション: 正規化した明細行、送金日、請求額、振込額、supplier code の digest を別途保存してください。

### 18. P1-02 の opening 依存は循環リスクは低いが責務表現がやや歪んでいる
- Severity: Minor
- カテゴリ: 設計
- 内容: 現状、`AccountsPayableIntegrityService -> SupplierBalancesService` と両者から `MfOpeningBalanceService` への依存で、直接の circular dependency は見当たりません。ただし integrity 側の `openingBalanceService.getEffectiveBalanceMap()` はログ件数確認だけで、計算結果は `SupplierBalancesService.generate()` 側に委譲されています。実計算に使わない依存を constructor inject するのは「契約の可視化」としては少し不自然です。
- 推奨アクション: opening 注入の責務は `SupplierBalancesService` に閉じ、integrity 側は `SupplierBalancesResponse` に opening 注入済みメタ情報を返してログ出力する方が分離が明確です。

### 19. P1-02 は委譲により責務分離は概ね維持されている
- Severity: Minor
- カテゴリ: 設計
- 内容: cumulative diff を `SupplierBalancesService.generate()` に委譲する方針自体は妥当です。整合性レポートが月次 delta、累積残一覧が期首込み closing という責務分離も保たれています。ただし integrity 側が opening service を直接知る必要は薄いです。
- 推奨アクション: 依存削減または DTO メタ情報化で、計算責務の所在をさらに明確にしてください。

## Opus 実装で見落としていた最重要 3 件

1. `DIRECT_PURCHASE_*` 副行が `applyVerification()` 後の DB-only CSV 出力で消えるため、Excel→CSV と 確定→CSV の仕訳が一致しない。
2. `verified_amount` の「全税率行同値」不変条件は、単一 verify と `ConsistencyReviewService` の正規経路で崩れる。
3. per-supplier 1 円不一致は DTO に載るだけで、UI 表示・サーバーブロックがなく、誤った振込/控除内訳のまま出力可能。
tokens used
111,873
以下、Group 2 対象の批判的レビューです。実装変更・テスト実行はしていません。

## 指摘

### 1. DIRECT_PURCHASE_* 副行が確定後の DB-only CSV 出力で消える
- Severity: Critical
- カテゴリ: 業務整合 / 仕訳構造
- 内容: `convert(uploadId)` は `preview.getRows()` をそのまま CSV 化するため `DIRECT_PURCHASE_FEE/DISCOUNT/EARLY/OFFSET` が出ます。一方、`applyVerification()` 後の `exportVerifiedCsv()` 経路では `saveAuxRowsForVerification()` が `DIRECT_PURCHASE_*` を保存対象外にしており、DB から再出力すると 20日払いの送料・値引・早払・相殺副行が消えます。つまり「Excel→CSV」と「Excel→確定→検証済CSV」で会計仕訳が一致しません。
- 推奨アクション: `t_payment_mf_aux_row` の制約・型を拡張して `DIRECT_PURCHASE_*` を保存し、`exportVerifiedCsv()` でも再現してください。保存しない設計なら、確定後 CSV 出力を機能上禁止または明示的に不完全扱いにすべきです。

### 2. verified_amount 不変条件は実装上すでに崩れる経路がある
- Severity: Critical
- カテゴリ: 業務整合 / 設計
- 内容: P1-09 は「supplier × month の全税率行で `verified_amount` 同値」を不変条件として文書化していますが、`TAccountsPayableSummaryService.verify()` は PK 1 行だけを更新します。また `ConsistencyReviewService.applyMfOverride()` は税率別に按分して `verifiedAmount` を別値にします。つまり manual SQL UPDATE 以外にも、正規アプリ経路で不変条件が崩れます。
- 推奨アクション: 不変条件を「一括振込明細検証由来に限る」に修正するか、集約値テーブルへ分離してください。現状の Javadoc/COMMENT は実装と矛盾しています。

### 3. `sumVerifiedAmountForGroup()` の全行同値判定はデータ由来を見ていない
- Severity: Major
- カテゴリ: 業務整合 / 設計
- 内容: `verified_amount` が全行同値なら代表値、異なれば SUM という推定ロジックです。偶然、手動 verify の税率別金額が同額になった場合は過少出力します。逆に一括検証行のうち 1 行だけ後から修正されると SUM になり、過大出力します。
- 推奨アクション: `verified_amount_mode` や `verification_source` のような明示フラグで「集約値」か「税率別値」かを判定してください。金額パターンから推定するのは会計出力には弱いです。

### 4. per-supplier 整合性不一致が UI で実質見えない
- Severity: Major
- カテゴリ: 業務整合 / UX
- 内容: `AmountReconciliation.perSupplierMismatches` は DTO/type には追加されていますが、`payment-mf-import.tsx` 側で `amountReconciliation` を表示している箇所が見当たりません。さらに `convert()` / `applyVerification()` は `perSupplierMismatches` をブロック条件にしていません。「請求 - 値引 - 早払 - 送料 - 相殺 = 振込」の 1 円不一致があっても、ユーザーが気づかず CSV 出力・反映できます。
- 推奨アクション: preview 上に赤警告を出し、少なくとも `convert()` はサーバー側で不一致時 422 にしてください。apply は業務判断で上書き可能にする場合でも明示確認フラグを API に持たせるべきです。

### 5. 5日払い合計行と20日払い合計行の扱いが破綻しやすい
- Severity: Major
- カテゴリ: 業務整合
- 内容: parser は最初の合計行だけを `summaryCaptured` として保持し、その後はすべて `afterTotal=true` にします。Excel に 5日払い合計行と20日払い合計行が両方ある場合、2つ目の合計は無視され、20日払い明細は `sumPerSupplierTransfer` に合算されます。その一方で比較先の `summaryTransfer` は最初の合計行だけなので、チェック3は構造的にズレます。
- 推奨アクション: セクションを 5日払い・20日払いで明示モデル化し、それぞれの合計行を別々に保持・照合してください。「合計行以降」だけで20日払い判定するのは脆いです。

### 6. 合計行に値があり per-supplier 列が NULL のケースを検知できない
- Severity: Major
- カテゴリ: 業務整合
- 内容: per-supplier 列が NULL の場合は 0 扱いされ、`transferAmount` が NULL なら `amount - deduction` で主行金額を派生します。この場合、合計行に値引・早払・送料が入っていても supplier 別内訳がない状態を正しくエラー化できません。旧 SUMMARY 集約行も撤去されているため、内訳欠落時に控除仕訳自体が落ちる可能性があります。
- 推奨アクション: 合計行の控除合計が非ゼロなのに per-supplier 合計が一致しない場合はブロックしてください。NULL と 0 は区別して警告に出すべきです。

### 7. PAYABLE→DIRECT_PURCHASE 自動降格で rule の一部情報が落ちる
- Severity: Major
- カテゴリ: 業務整合 / 設計
- 内容: 自動降格時に `summaryTemplate/tag/creditDepartment` は継承していますが、`paymentSupplierCode`、`creditSubAccount`、`priority`、`id` などは落ちます。preview 表示・CSV 出力だけなら見えにくいですが、supplier 紐付けや将来の保存処理では追跡性が弱くなります。特に `paymentSupplierCode` は親 row builder では Excel 側 `e.supplierCode` に依存します。
- 推奨アクション: 降格用 DTO/コピー関数を作り、継承する項目と意図的に捨てる項目を明文化してください。少なくとも `paymentSupplierCode` は保持すべきです。

### 8. DIRECT_PURCHASE 主行の貸方「資金複合」は5日払いの普通預金と運用差が大きい
- Severity: Major
- カテゴリ: 仕訳構造
- 内容: 5日払い PAYABLE 主行は貸方「普通預金」ですが、20日払い DIRECT_PURCHASE 主行は貸方「資金複合」です。銀行データ連携前提なら普通預金を直接立てない意図は理解できますが、5日払い側との説明が揃っていません。MF 上で資金複合を消し込む後続処理が明示されていないと、監査時に残高説明が難しくなります。
- 推奨アクション: 5日払い・20日払いで貸方科目が異なる理由と、MF 側での消込手順を設計書に明記してください。可能なら支払系の貸方科目を統一してください。

### 9. DIRECT_PURCHASE の値引・早払副行は会計表現として不自然
- Severity: Major
- カテゴリ: 仕訳構造
- 内容: DIRECT_PURCHASE_* は借方「仕入高」、貸方「仕入値引・戻し高」または「早払収益」です。同一取引内で「仕入高を増やしながら値引を貸方計上」する形は総額主義として説明可能な余地はありますが、即払い仕入で実質値引後の純額仕入と見る運用とはズレます。税区分も「課税仕入 10%」と「課税仕入-返還等 10%」が同時発生するため、税理士確認なしでは危険です。
- 推奨アクション: 税理士確認済みの仕訳パターンとして文書化してください。未確認なら DIRECT_PURCHASE は純額仕入または買掛経由に寄せる方が監査説明しやすいです。

### 10. OFFSET の貸方「仕入値引・戻し高」は暫定のまま本番仕訳に出る
- Severity: Major
- カテゴリ: 仕訳構造
- 内容: コード上も TODO で「相殺の貸方科目は税理士確認」とされていますが、実際には `PAYABLE_OFFSET` / `DIRECT_PURCHASE_OFFSET` として CSV に出力されます。相殺は値引ではなく、未収金、立替金、売掛金、仮払金、仕入先負担金など相手科目が異なる可能性があります。暫定科目で自動仕訳化するのは会計リスクが高いです。
- 推奨アクション: OFFSET は科目未確定ならブロックまたは専用マスタで相手科目を選択させてください。最低限、値引と同じ税区分でよいかを確認済みにする必要があります。

### 11. P1-08 は「再取込防止」ではなく「警告」に留まっている
- Severity: Major
- カテゴリ: 監査 / UX
- 内容: duplicate hash も applied warning も preview 表示のみで、`convert()` / `applyVerification()` のサーバー側確定保護はありません。フロントの ConfirmDialog で再確定は確認されますが、API 直叩きや二重操作は止まりません。名称が「防止」なら実装強度が不足しています。
- 推奨アクション: applied 済みの場合は `force=true` など明示パラメータなしでは 409/422 にしてください。duplicate hash も再取込許可理由を履歴に残すのが望ましいです。

### 12. `verified_manually` 保護判定は note 文字列依存で偽判定があり得る
- Severity: Major
- カテゴリ: 監査 / 設計
- 内容: bulk 由来かどうかを `verification_note.startsWith(VERIFICATION_NOTE_BULK_PREFIX)` で判定しています。ユーザーが個別 verify の note に同じ接頭辞を入力すると bulk 扱いで上書きされます。逆に過去 bulk の note フォーマット変更・NULL 化があると単一 verify 扱いで保護されます。
- 推奨アクション: `verification_source` や `verified_by_flow` の列を追加し、文字列ではなく enum 的に判定してください。note は監査説明文であり制御フラグに使うべきではありません。

### 13. V034 の history 保存失敗を握りつぶすため applied warning が欠落し得る
- Severity: Major
- カテゴリ: 監査
- 内容: `saveAppliedHistory()` は例外をログだけにして本体成功扱いにします。この場合、買掛金は上書き済みなのに `applied_at` 履歴がなく、次回 preview の AppliedWarning が出ません。監査・再取込防止の根拠データとしては弱いです。
- 推奨アクション: applied history 保存は同一トランザクションで必須成功にしてください。失敗時に本体反映もロールバックするのが自然です。

### 14. migration 連番に `V033` 欠番がある
- Severity: Major
- カテゴリ: スキーマ
- 内容: 現在の `backend/src/main/resources/db/migration` では `V030, V031, V032, V034, V035, V036` が見え、`V033` がありません。ユーザー説明では V033 が P1-05 として存在する前提なので、作業ツリーの欠落またはブランチ衝突が疑われます。Flyway は欠番自体は許容しますが、並行 feature branch では番号衝突・適用漏れの温床です。
- 推奨アクション: V033 の所在を確認し、main 取り込み順を整理してください。未適用なら migration 番号を振り直すか、明示的に欠番理由を残してください。

### 15. V034 の index は実クエリに十分合っていない
- Severity: Minor
- カテゴリ: スキーマ
- 内容: `idx_payment_mf_import_history_applied` は `(shop_no, transfer_date) WHERE applied_at IS NOT NULL` ですが、repository は `ORDER BY applied_at DESC LIMIT 1` 相当です。適用済み履歴が増えるほど同一振込日の複数行を並べ替える可能性があります。また hash index は `source_file_hash` 単独で NULL も含むため、実データが増えると無駄があります。
- 推奨アクション: applied は `(shop_no, transfer_date, applied_at DESC) WHERE applied_at IS NOT NULL`、hash は `WHERE source_file_hash IS NOT NULL` を検討してください。

### 16. `applied_at IS NULL` の履歴は AppliedWarning 対象外で、convert 済みは検知できない
- Severity: Minor
- カテゴリ: 監査
- 内容: convert 経由の履歴は `source_file_hash` は残りますが `applied_at` は NULL のため AppliedWarning には出ません。これは「買掛金一覧へ反映済み」の警告としては正しい一方、「MF CSV をすでに作った/取り込んだ可能性」は L1 hash が一致する場合にしか出ません。ファイルを少し編集した再出力は検知できません。
- 推奨アクション: 「CSV 生成済」と「買掛反映済」を別警告に分け、transferDate/totalAmount/rowCount など内容 fingerprint でも検知してください。

### 17. SHA-256 の粒度は業務重複検知として粗い
- Severity: Minor
- カテゴリ: 監査
- 内容: ファイル全バイト SHA-256 は完全一致検知には強いですが、Excel のメタ情報や保存形式変更だけで別 batch になります。逆に業務内容が同じでも hash は変わります。再取込防止という業務目的には「行内容 fingerprint」も必要です。
- 推奨アクション: 正規化した明細行、送金日、請求額、振込額、supplier code の digest を別途保存してください。

### 18. P1-02 の opening 依存は循環リスクは低いが責務表現がやや歪んでいる
- Severity: Minor
- カテゴリ: 設計
- 内容: 現状、`AccountsPayableIntegrityService -> SupplierBalancesService` と両者から `MfOpeningBalanceService` への依存で、直接の circular dependency は見当たりません。ただし integrity 側の `openingBalanceService.getEffectiveBalanceMap()` はログ件数確認だけで、計算結果は `SupplierBalancesService.generate()` 側に委譲されています。実計算に使わない依存を constructor inject するのは「契約の可視化」としては少し不自然です。
- 推奨アクション: opening 注入の責務は `SupplierBalancesService` に閉じ、integrity 側は `SupplierBalancesResponse` に opening 注入済みメタ情報を返してログ出力する方が分離が明確です。

### 19. P1-02 は委譲により責務分離は概ね維持されている
- Severity: Minor
- カテゴリ: 設計
- 内容: cumulative diff を `SupplierBalancesService.generate()` に委譲する方針自体は妥当です。整合性レポートが月次 delta、累積残一覧が期首込み closing という責務分離も保たれています。ただし integrity 側が opening service を直接知る必要は薄いです。
- 推奨アクション: 依存削減または DTO メタ情報化で、計算責務の所在をさらに明確にしてください。

## Opus 実装で見落としていた最重要 3 件

1. `DIRECT_PURCHASE_*` 副行が `applyVerification()` 後の DB-only CSV 出力で消えるため、Excel→CSV と 確定→CSV の仕訳が一致しない。
2. `verified_amount` の「全税率行同値」不変条件は、単一 verify と `ConsistencyReviewService` の正規経路で崩れる。
3. per-supplier 1 円不一致は DTO に載るだけで、UI 表示・サーバーブロックがなく、誤った振込/控除内訳のまま出力可能。
