OpenAI Codex v0.128.0 (research preview)
--------
workdir: C:\project\odamitsu-data-hub
model: gpt-5.5
provider: openai
approval: never
sandbox: read-only
reasoning effort: none
reasoning summaries: none
session id: 019df0b1-4536-7ae3-837a-ab721582d865
--------
user
あなたは経理システムの上級レビュアーです。Cluster E (売掛金 - Accounts Receivable) の批判的レビューを実施してください。

## プロジェクト
小田光データ連携基盤 (`C:/project/odamitsu-data-hub`)
- Java 21 / Spring Boot 3.3.x / PostgreSQL 17 / Next.js 16
- 売掛金集計→検証→マネーフォワード仕訳連携

## レビュー対象 (Cluster E)
**Backend** (主要):
- Entity: `TAccountsReceivableSummary.java`
- Repository: `TAccountsReceivableSummaryRepository.java`
- Service: `TAccountsReceivableSummaryService.java`
- Batch: `AccountsReceivableToSalesJournalTasklet.java`, `TAccountsReceivableSummaryTasklet.java`
- Helper: `batch/finance/service/AccountsReceivableCutoffReconciler.java`
- DTO: `AccountsReceivableResponse.java`, `AccountsReceivableSummaryResponse.java`, `AccountsReceivableVerifyRequest.java`, `AccountsReceivableAggregateRequest.java`, `AccountsReceivableBulkVerifyRequest.java`
- Controller: AccountsReceivable 関連

**Frontend**:
- `frontend/components/pages/finance/accounts-receivable.tsx`
- `frontend/components/pages/finance/AccountsReceivableVerifyDialog.tsx`
- `frontend/components/pages/finance/AccountsReceivableAggregateDialog.tsx`

## 既存レビュー (重複回避)
- `claudedocs/design-review-accounts-receivable.md` (Critical 2 / Major 8 / Minor 6)
- `claudedocs/code-review-accounts-receivable.md` (Critical 3 / Major 5 / Minor 8)
- `claudedocs/design-accounts-receivable-mf.md`

既出 (重複させない):
- markExported 焼き込みで CSV DL 失敗時に古い値固定 (二重計上リスク)
- bulkVerify 3 分割 tx で reconcile 後例外時に mfExportEnabled が黙って全 OFF
- ゴミ袋 goods_code 定義の tasklet vs reconciler 不整合
- CutoffReconciler 暗黙起動 / `cutoffDate=-1` 黙示上書き

## 批判的レビューの観点
Opus はコード品質と既知バグを網羅した。**根本的な問い直し**を:

1. **設計選択の前提**
   - 「売掛金一覧が買掛金とほぼ対称な構造」という設計選択は本当に正しいか
   - 「partner (得意先)」と Cluster A の `t_invoice` `m_partner_group` との関係 (重複/分担/階層)
   - 締め日 cutoff が得意先別に 15/20/月末で混在する設計の妥当性
   - `mfExportEnabled` の判定責務がどこに集約されているか

2. **代替アーキテクチャ**
   - Sales Journal CSV を DB から再生成可能にする設計
   - bulkVerify を非同期ジョブ化する案
   - CutoffReconciler を独立した「締め確定」操作として明示的に呼ぶ案 (現在は bulkVerify から暗黙起動)

3. **エッジケース / 失敗モード**
   - 月次締め後に得意先側から訂正請求が来た場合
   - 締め日変更 (15日締め→20日締め) が起きた場合の過去月再集計
   - 入金額が複数の請求 (請求書) に按分される場合の追跡可能性
   - キャンセル / 返品 / 値引が同月内に発生した場合の累積
   - 第1事業部得意先の B-CART 注文が shop=1 統合される (memory に記載) 影響

4. **業務整合 / 監査**
   - 売掛金の「権威」(Excel 取込/SMILE/MF/手入力) の階層
   - 入金消込のトレーサビリティ (誰がいつどの根拠で消込したか)
   - 監査時に「この月のこの売掛金はなぜ MF 出力された/されていないか」を説明できるか
   - 買掛金 (Cluster D) と売掛金で命名・運用ルールが揃っているか

## 出力フォーマット
Markdown、日本語、簡潔に (max 500 行)。各指摘:
- Severity: Critical / Major / Minor
- カテゴリ: 設計選択 / 代替案 / エッジケース / 業務整合
- 内容 (3-5 文)
- 推奨アクション (1-2 文)

最後に「Opus が見落としていた最重要 3 件」を明記。


codex
`using-superpowers` と、レビューなので `requesting-code-review`、PostgreSQL観点も含むため `postgresql-code-review` を読みます。既存レビューとの差分を出すため、まず対象ファイルと既存レビュー文書の実物を確認します。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content C:/Users/kazuki/.agents/skills/requesting-code-review/SKILL.md' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content C:/Users/kazuki/.agents/skills/using-superpowers/SKILL.md' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content C:/Users/kazuki/.agents/skills/postgresql-code-review/SKILL.md' in C:\project\odamitsu-data-hub
 succeeded in 1843ms:
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

 succeeded in 1929ms:
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

 succeeded in 2854ms:
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
既存レビューの指摘セットを先に棚卸しして、同じ論点を避けます。そのうえで実装上の責務分布とデータモデルを追って、設計前提の弱い箇所に絞ります。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content claudedocs/design-review-accounts-receivable.md' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content claudedocs/code-review-accounts-receivable.md' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content claudedocs/design-accounts-receivable-mf.md' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'rg --files | rg "(AccountsReceivable|accounts-receivable|TAccountsReceivable|receivable|invoice|partner|payable)"' in C:\project\odamitsu-data-hub
 succeeded in 1763ms:
# 設計レビュー: 売掛金 (Cluster E)

レビュー日: 2026-05-04
対象設計書: `claudedocs/design-accounts-receivable-mf.md`
レビュアー: Opus サブエージェント
対象ブランチ: `refactor/code-review-fixes`

## サマリー

- 総指摘件数: **Critical 2 / Major 7 / Minor 6**
- 承認状態: **Needs Revision**

最重要指摘トップ:
1. (Critical) `AccountsReceivableController#exportMfCsv` の CSV ダウンロードが「成功しても失敗しても無条件に `markExported`」しており、バッチ側 `AccountsReceivableToSalesJournalTasklet` の「失敗時にはマーカーをつけない」契約と非対称。CSV を受け取れなかった運用者からは確定金額が「出力済み」として固定され、再 DL や再検証で齟齬を生む。
2. (Critical) `TAccountsReceivableSummaryPK` の `transactionMonth` 列名が camelCase で誤宣言されている。`@IdClass` 経由のため現在は黙殺されているが、将来 `@EmbeddedId` への移行や Specification での Embedded path 参照が入った瞬間にデータ取得不能になる地雷。

---

## Critical 指摘

### C-1. CSV DL で「失敗時もマーカーを焼く」非対称
- **箇所**: `backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java:281-291`
- **問題**: `try (OutputStreamWriter w = ...)` の外で `markExported` → `summaryService.save()` を無条件に実行。例外時のロールバックも try/catch も無く、また `salesJournalCsvService.writeCsv` がレスポンス送信前に成功した時点で焼くため、ブラウザ側のダウンロードが中断 (ネットワーク切断・5xx 経由) してもサーバ側は CSV を確定済みにしてしまう。買掛側 `FinanceController#exportPurchaseJournalCsv` (`FinanceController.java:367-368`) も同様のパターンだが、買掛側は `result.rowCount == 0` の場合に 422 で abort してから markExported に進むのに対し、売掛側は `summaries.isEmpty()` のガードすら無い。
- **影響**: 運用者が「CSV を受け取り損ねた → もう一度 DL ボタン」を押しても、`tax_included_amount` が `*_change` で凍結された後なので、バッチ側で `*_change` が再集計されると **再 DL 用の金額と元金額が乖離**。会計連携の二重計上 / 連携漏れの基点になる。
- **修正案**:
  1. レスポンス本体を `StreamingResponseBody` で返却し、書き出し成功後の callback で `markExported` を呼ぶ (HTTP 200 が確定したことを保証)。
  2. それが重い場合は最低限「summaries が空なら 422 / マーク済みフラグを使った冪等化」をする。買掛側と動作を揃え、`FilterResult.exportable.size()==0` で 422 を返す。
  3. `markExported` ループは `summaryService.saveAll(summaries)` (1 tx) に置換し、N+1 個別 save を排除。

### C-2. Embeddable PK の列名 camelCase 誤宣言
- **箇所**: `backend/src/main/java/jp/co/oda32/domain/model/embeddable/TAccountsReceivableSummaryPK.java:30-31`
  ```java
  @Column(name = "transactionMonth")  // ← 実 DB 列は transaction_month
  private LocalDate transactionMonth;
  ```
- **問題**: 実テーブル定義 (`V018__alter_accounts_receivable_summary_add_verification_fields.sql:35` 他) では `transaction_month` (snake_case)。`TAccountsReceivableSummary` 本体 (`TAccountsReceivableSummary.java:38`) は正しく `name = "transaction_month"` を指定しているが、PK class が乖離。
- **影響**: 現在は `@IdClass(TAccountsReceivableSummaryPK.class)` 方式 (Entity の `@Id` フィールドが正本) のため `@Column(name)` は無視されて偶然動いている。が、
  - 将来 `@EmbeddedId` へ移行した瞬間に PostgreSQL で「column transactionMonth does not exist」になりロード不能。
  - `Specification` で `root.get("transactionMonth")` ではなく `root.get(TAccountsReceivableSummaryPK_.transactionMonth)` のように Embeddable 経由で参照すると同様に死ぬ。
  - JPA Metamodel 生成系・Querydsl 生成系で誤った列名が混入する可能性。
- **修正案**: 列名を `transaction_month` に修正、もしくは PK class の `@Column` を一律削除して `@IdClass` の挙動に任せる。`AccountsPayable` 側の PK class 命名も突き合わせて統一。

---

## Major 指摘

### M-1. `bulkVerify` の auto-reconcile が無防備に大量 DELETE+INSERT を走らせる
- **箇所**: `AccountsReceivableController.java:181-189` + `AccountsReceivableCutoffReconciler.java:86-208`
- **問題**: 一括検証ボタン押下のたびに `AccountsReceivableCutoffReconciler#reconcile` が走る (`@Transactional`)。期間が広いと、対象期間内の全 partner × 全月で `findInvoiceForPartner` (3 種類の closingDate を順次クエリ) → t_order_detail 再集計 → DELETE+INSERT を回す。N+1 (各 partner ごとに `tInvoiceService.find...` を 3 回 + `tOrderDetailService.findByPartnerNosAndDateRange` 1 回) かつ、UI からはユーザの意図しない裏で行われる。設計書 §5/§7 にこの「自動再集計」の存在自体が一切書かれておらず、設計書 vs 実装の乖離が大きい。
- **影響**:
  - 運用者から見ると「一括検証」を押したのに集計まで書き換わるため、想定外の差額発生・原因追跡困難。
  - 期間が長いと数千クエリになる。手動確定行のみを保護しているが、それ以外は新規 INSERT で `mfExportEnabled=false` にリセットされるため、すでに `mf_export_enabled=true` で運用にロックしていた行が黙って false に戻る (Reconciler は `mfExportEnabled(false)` で生成: `AccountsReceivableCutoffReconciler.java:371`)。
- **修正案**:
  1. 設計書に「auto-reconcile」セクションを追加し、UI 側で「自動再集計を含めて検証」「検証のみ」をユーザに選ばせる (`bulkVerify?autoReconcile=true|false`)。
  2. Reconciler が新行を作る際、削除対象 stale 行に `mfExportEnabled=true` が含まれていたら新行にも引き継ぐ (`anyExportEnabled` を見て propagate)。
  3. 期間内の Invoice をまとめて取得する `findByShopAndPartnerCodesAndClosingDateIn` を追加し、N+1 を解消。

### M-2. `bulkVerify` のトランザクション境界が壊れている
- **箇所**: `AccountsReceivableController.java:171-209`
- **問題**: Controller メソッドに `@Transactional` 無し。内部で
  - `cutoffReconciler.reconcile(...)` (`@Transactional` で別 tx)
  - `findByShopAndDateRange` 再ロード (再 tx open/close)
  - `invoiceVerifier.verify(...)` (in-memory 更新のみ)
  - `summaryService.saveAll(...)` (`@Transactional` で別 tx)

  という 3 つの独立トランザクションに分解される。Reconciler 中で commit したあとに `verify` → `saveAll` 中で例外が起きると、**Reconcile だけ commit されて検証結果は未保存**になる。Reconcile が走った得意先は `mf_export_enabled=false` の新行に置き換わっているため、運用上「一括検証」が「黙って MF 出力対象を全部 OFF にしただけ」の事態になる。
- **影響**: 部分コミット破綻。設計書 §5/§9.3 の「画面の検索条件範囲を対象に検証する」というシンプルな意味合いと食い違う。
- **修正案**: Controller 側に `@Transactional` を付ける、または `BulkVerifyService` を新設して全工程を 1 tx に閉じる。Reconciler 単独の `@Transactional` は外すか `Propagation.REQUIRED` (デフォルト) のままで親 tx に乗るよう保つ。

### M-3. `verify` (PUT) で `verificationResult=1` を強制し UI と乖離
- **箇所**: `TAccountsReceivableSummaryService.java:204-220`
- **問題**: 手動確定 API は確定金額・備考だけ受け取るが、内部で `verificationResult = 1` を**問答無用に**セット。設計書 §9.4 にも「verification_result = 1 を強制設定」と明記されているが、`AccountsReceivableVerifyRequest` (`AccountsReceivableVerifyRequest.java:13-25`) には検証結果フラグも diff も無いため、運用者が「不一致だが業務都合で MF には 12 万円で出す」のような確定をしても **検証バッジは緑「一致」になる**。トレーサビリティが死ぬ。
- **影響**: 監査時に「なぜこれが一致になっているのか」が判らなくなる。整合性レポート (Cluster D) は `verification_result` を信用しているため、手動確定の隠れた差額が永久にレポートに出てこない。
- **修正案**:
  1. リクエストに `verificationResult` (またはユーザ任意の確定理由カテゴリ) を追加し、`note` 必須化を `taxIncludedAmount != invoiceAmount` の場合に切り替える。
  2. `verifiedManually=true` のときは UI 側のバッジを「手動」(青) に分岐し、`verificationResult` の意味を「自動検証結果」に限定する。

### M-4. `updateMfExport` の副作用が暗黙すぎる
- **箇所**: `TAccountsReceivableSummaryService.java:241-260`
- **問題**: `mf_export_enabled=true` を立てるだけのつもりの API が、副作用で `verifiedManually=true` + `verificationResult=1` まで書き換える (クリーンラボ運用対応のコメントあり)。買掛側 `accountsPayableSummaryService.updateMfExport` には同等の副作用が無い (買掛側は `verified_manually` を触らない単純な toggle)。設計書 §9.6 のレスポンス仕様 (204 No Content) や §9.4 とも整合せず、設計書には記載が無い隠れ仕様。
- **影響**: 運用者が「MF 出力を試しに ON にしてみた」だけのつもりが手動確定扱いになり、次の自動再集計 (M-1 の挙動) で保護されてしまう。OFF→ON→OFF の往復で `verifiedManually` が `false` に戻らない (`updateMfExport(false)` が `verifiedManually` を触らないため) 状態の腐食。
- **修正案**: クリーンラボのような特殊 partner を **設定駆動** (例: `m_partner.always_export_to_mf = true`) で扱い、汎用 toggle API から特殊副作用を切り離す。設計書にもこの方針を明記。

### M-5. `applyMatched` の差額計算に丸め誤差混入
- **箇所**: `InvoiceVerifier.java:338-356` + `allocateInvoiceByArRatio` (`InvoiceVerifier.java:379-418`)
- **問題**: 完全一致でない「許容誤差内 (?3円)」のとき、`allocateProportionallyWithRemainder` で `taxIncludedAmountChange` を **書き換えてから** `allocateInvoiceByArRatio` で再按分している。再按分は AR 比率 (= 既に按分後の値) を使うため、按分残差が最大行に集中して `verificationDifference = 按分後請求書額 ? 按分後 AR` がほぼ常に 0 になる (= 一致時と区別がつかない)。`applyMatched` の `diff` 引数は受け取っているのに使っていない。
- **影響**: 3 円差額調整したことが行レベルの diff に表現されないため、UI 上「許容誤差で吸収しました」が運用者に見えない。差額調整の監査痕跡欠落。
- **修正案**: 調整前の元値 (`*_change` のオリジナル) を `applyMatched` まで持ち回り、`verificationDifference = invoiceAmountAllocated ? originalChange` で記録する。または `verificationNote` に「許容誤差 -2円 自動吸収」のような自動メモを追記する。

### M-6. 上様 (999999) の按分が 0 円集計時に request 額を捨てる
- **箇所**: `InvoiceVerifier.java:145-157`
- **問題**: `totalTaxIncluded == 0` のとき `log.error` するだけで、`applyMatched` には進む。集計 0 円のまま `verificationResult=1, mfExportEnabled=true` で確定し、CSV には金額 0 円の上様行が現れる (もしくは `markExported` で 0 が `*_amount` にコピーされ、請求書金額分の売上が連携されない)。
- **影響**: 上様で「集計失敗 → 0」が起きた月は **請求書金額の売上が MF 連携されない**。検出は `log.error` のみで運用通知無し。
- **修正案**: `totalTaxIncluded == 0` の場合は `applyNotFound` 相当 (`mfExportEnabled=false`) に倒し、画面上「未検証 (集計0)」として顕在化させる。設計書 §7 の上様処理にこのエッジケースを追記。

### M-7. `Optional` 同期返却 + N+1: `mPartnerService.getByPartnerNo` をループ呼び
- **箇所**: `AccountsReceivableController.java:96-104`
- **問題**: 一覧 API で `partnerNos.stream().map(mPartnerService::getByPartnerNo)` と1件ずつ問い合わせ。50件/page のため最悪 50 回。買掛側は `mPaymentSupplierService.findAllByPaymentSupplierNos(supplierNos)` の bulk fetch (`FinanceController.java:118`) を使用しており非対称。
- **影響**: 一覧表示のたびに 50+1 クエリ。検索結果が多い admin (全店舗閲覧) でレスポンス劣化。
- **修正案**: `MPartnerService` に `findAllByPartnerNos(Set<Integer>)` を追加し、1 クエリで bulk 取得して Map 化する。

---

## Minor 指摘

### m-1. `summary()` が全件ロードしてストリーム集計
- **箇所**: `TAccountsReceivableSummaryService.java:84-105`
- **問題**: ページング無しで `findAll(spec)` してから Java で count/sum。月次数千行を毎回 in-memory 集計。
- **修正案**: JPQL/Criteria での `count(case when ... )` + `sum(case when ...)` クエリに置換。

### m-2. `verify` ハンドラが `verificationDifference` / `invoiceAmount` を再計算しない
- **箇所**: `TAccountsReceivableSummaryService.java:204-220`
- **問題**: 手動確定で確定金額が変わっても `invoiceAmount` (突合した請求書額) と `verificationDifference` は更新されない。`AccountsReceivableResponse.from` で旧値が表示され、運用者が「確定金額 12 万 / 請求書 10 万 / 差額 0 (古い)」のような不整合表示を見る。
- **修正案**: 手動確定時に `verificationDifference = (invoiceAmount or null) - taxIncludedAmount` を再計算、もしくは UI 側で表示を「手動確定済」に切り替えて差額列を `-` 表示にする。

### m-3. `AccountsReceivableSummaryResponse.summary()` の `partnerNo` が無視される
- **箇所**: `AccountsReceivableController.java:108-116` + `TAccountsReceivableSummaryService.java:85`
- **問題**: Controller の `summary` エンドポイントは `partnerCode` のみ受け取り、Service 側の `summary()` には `partnerNo=null` 固定で渡している。一覧 API は `partnerNo` を受けるため、検索条件と sumamry 表示の集計範囲が乖離。
- **修正案**: `summary` エンドポイントに `partnerNo` を追加するか、UI で `partnerNo` を選んだら summary も再計算するよう揃える。

### m-4. `applyMismatch` で `verificationDifference` が「按分後 AR」を使う
- **箇所**: `InvoiceVerifier.java:358-372`
- **問題**: 不一致のときも `allocateInvoiceByArRatio` で按分 → 行ごとの diff は「按分請求書 - 按分 AR」。AR 側は按分していないので diff 自体は元の差と一致するが、 multi-row の場合に行 diff の合算がグループ diff と一致するかの保証は弱い (端数集中で +1/-1 ずれる)。
- **修正案**: グループ diff のテストケースを `InvoiceVerifierTest` に追加。

### m-5. CSV エンコーディング指定が二重定義
- **箇所**: `AccountsReceivableController.java:68` (`Charset.forName("windows-31j")`) + `:297` (`charset=Shift_JIS`)
- **問題**: 実体は CP932 (windows-31j) なのに HTTP ヘッダは `Shift_JIS` を返す。MF 取り込みは現状動いているが、Shift_JIS と CP932 の差異 (NEC 特殊文字等) が出る可能性。
- **修正案**: ヘッダも `text/csv; charset=Windows-31J` に統一、買掛側 `FinanceController.java:CP932` 定義と一本化。

### m-6. 設計書 §3 / §11 で必須とした `InvoiceVerifierTest` が未実装
- **箇所**: 想定パス `backend/src/test/.../InvoiceVerifierTest.java`
- **問題**: ファイル無し。設計書 §11.1「単体テスト追加」が完了条件として残置。
- **修正案**: 一致 / 3円以内差額 / 3円超差額 / 請求書なし / 上様 / イズミ四半期 / 上様集計0 (M-6) / `verifiedManually` 保護 の 8 ケース最低限実装。

---

## 買掛金との対称性 / 非対称性

設計書 §1「買掛側との対称構造」表で対称性が明示されているにもかかわらず、実装では以下の非対称が確認された。

| # | 観点 | 買掛 (AP) | 売掛 (AR) | 評価 |
|---|------|-----------|-----------|------|
| S-1 | Controller 配置 | `FinanceController#listAccountsPayable` (1 ファイル巨大化) | `AccountsReceivableController` (独立) | **AR の方が綺麗**。AP も将来切り出すべき。 |
| S-2 | `@PreAuthorize` 粒度 | verify / mf-export は `isAuthenticated()` (ロール無し) | verify / mf-export / aggregate / bulk-verify / export-mf-csv が `hasRole('ADMIN')` | **意図的差異**ならドキュメント化が必要。AP 側も金銭操作なので ADMIN 化を検討。 |
| S-3 | bulk-verify | AP には bulk-verify が無い (AccountsPayableVerificationTasklet 一本) | AR は API 経由で bulk-verify 可 | 設計差。AP 側にもユーザ起動の検証ボタンが欲しいなら設計を統一。 |
| S-4 | CSV DL の filter ロジック | `PurchaseJournalCsvService.filter()` で `forceExport` (未検証含む) 切替 + `nonExportableCount` 集計 | AR は `findByDateRangeAndMfExportEnabled(true)` 一択、`forceExport` 概念無し | AR でも「未検証だけ強制出力」「検証 OK だけ」のオプションが欲しい。 |
| S-5 | CSV DL preview | AP は `/export-purchase-journal/preview` で件数・skipped を事前確認 | AR は preview 無し、いきなり DL | AR にも preview を実装。設計書 §6.7 の confirmDialog だけでは足りない。 |
| S-6 | CSV 0件時の挙動 | AP は 422 で abort | AR は空ファイル DL | AR を AP に揃える (空 CSV を運用に渡す価値が薄い)。 |
| S-7 | bulk fetch (master JOIN) | `mPaymentSupplierService.findAllByPaymentSupplierNos` で N+1 解消済 | `mPartnerService.getByPartnerNo` ループ (M-7) | 非対称。AR を AP に揃える。 |
| S-8 | CutoffReconciler | AP には存在しない (`m_payment_supplier` cutoff_date は 20 日固定運用) | AR は AccountsReceivableCutoffReconciler | AR 固有要件 (得意先ごとに 15/20/月末が混在) なので妥当。ただし bulkVerify 側で暗黙起動なのが M-1。 |
| S-9 | 検証許容誤差 | 設計書記載: 5円 | 設計書記載: 3円 / 実装: `@Value("${batch.accounts-receivable.invoice-amount-tolerance:3}")` で外部化 | AR の方が外部化されている。AP 側も外部化推奨。 |
| S-10 | verify request | `verifiedAmount` (税込のみ) | `taxIncludedAmount` + `taxExcludedAmount` 両方 | 売上は税抜分けて MF に出すため妥当。だが AP 側も税抜が必要な場面はあり、整合させる検討余地あり。 |
| S-11 | `@Transactional saveAll` 統一 | `accountsPayableSummaryService.saveAll(filtered.exportable)` (`FinanceController.java:368`) | AR の CSV DL は `for-each save` (`AccountsReceivableController.java:288-290`) | C-1 と関連。AR 側を `saveAll` に統一。 |
| S-12 | tasklet 内 検証ロジック抽出 | `SmilePaymentVerifier` Service 化済 | `InvoiceVerifier` Service 化済 (今回) | 対称達成。 |

---

## 設計書 vs 実装の乖離

### D-1. 設計書に Reconciler の記述が無い
設計書 §5.2/§9.3 では「`InvoiceVerifier` に検証ロジックを委譲」しか書かれておらず、`AccountsReceivableCutoffReconciler` の存在・目的・自動起動タイミング (`bulkVerify` から無条件呼び出し) が完全に未記載。Reconciler は ENEOS のような「マスタ cutoff_date が古い」運用に追従するための重要な機構なので、設計書に独立節を追加すべき (M-1 と関連)。

### D-2. `release-manual-lock` API が設計書 §9.5 で URL/method のみ記載で **挙動不足**
実装は `verifiedManually=false` のみ書き換える (`TAccountsReceivableSummaryService.java:226-235`)。`mf_export_enabled` や `verification_result` を巻き戻すかどうか、`*_amount` をどうするか (現状: そのまま残置 = 旧手動値が残る) が設計書に書かれておらず、再検証バッチが走るまでデータ状態が "宙に浮く"。

### D-3. 設計書 §5.3 の「`*_amount = *_change` コピー処理は維持」が CSV DL でも一致
設計書通りだが、`InvoiceVerifier#applyMatched` 中でも `s.setTaxIncludedAmount(s.getTaxIncludedAmountChange())` (`InvoiceVerifier.java:353`) と二重に焼く設計になっているため、`SalesJournalCsvService#markExported` (`SalesJournalCsvService.java:228`) の意味が薄れる (一致行は既に焼かれている)。設計意図 (検証一致時は確定 / CSV 出力時に再確定の二段) を §5.3 に明記すべき。

### D-4. 設計書 §10 の実装順序 「7. CSV生成ロジックを Service に抽出」 は完了だが、tasklet 側にもう一重 Tasklet が残存
`AccountsReceivableToSalesJournalTasklet` は今や Service に薄く委譲しているだけ。Job として残す価値は「CLI からの定期実行」だが、設計書 §2「定時バッチ設定: 含まないもの」と矛盾。tasklet を deprecate にするか、定時バッチを正式機能化するか方針決定が必要。

### D-5. 設計書 §13 リスク R2「CSV DL のメモリ使用量」未対処
実装は `ByteArrayOutputStream` 全量バッファ (`AccountsReceivableController.java:281`)。1 ヶ月分なら問題ないが、半年指定で N×6 倍。`StreamingResponseBody` 化は C-1 と併せて対処推奨。

---

## レビューチェックリスト結果

| # | 観点 | 結果 | 備考 |
|---|------|------|------|
| 1 | DTO と Entity 分離 | OK | `AccountsReceivableResponse.from(entity, partner)` ファクトリ |
| 2 | Bean Validation | △ | `AccountsReceivableVerifyRequest` の `note` `Size(max=500)` のみ。 `taxIncludedAmount >= taxExcludedAmount` のクロスフィールド検証なし |
| 3 | `@Transactional` 境界 | NG | M-2 (bulkVerify 部分コミット), C-1 (CSV DL) |
| 4 | N+1 クエリ | NG | M-7 (partner lookup), M-1 (Reconciler invoice lookup) |
| 5 | 例外ハンドリング | △ | CSV DL で `throws Exception`, save ループで catch ログだけして次行に進む (`TAccountsReceivableSummaryTasklet.java:271-275`) は集計失敗の運用通知が `log.error` のみ |
| 6 | ログ出力レベル | OK | 過不足無し、件数集計あり |
| 7 | 設計書遵守 | NG | D-1?D-5 |
| 8 | テストカバレッジ | NG | `InvoiceVerifierTest` 未作成 (m-6) |
| 9 | 認可制御 | △ | S-2 で AP/AR 非対称 |
| 10 | 監査証跡 | NG | M-3 (verify で結果フラグ強制), m-2 (差額未更新) |
| 11 | 命名 / コメント | OK | 「20日締め売掛金テーブル」のクラスコメントは現状15/20/月末混在で不正確 (`TAccountsReceivableSummary.java:15`, `TAccountsReceivableSummaryRepository.java:14`) → minor |
| 12 | エンコーディング | △ | m-5 |
| 13 | 締め日 cutoff の論理 | OK 概ね | tasklet と reconciler でゴミ袋コードが異なる (`TAccountsReceivableSummaryTasklet.java:55-60` と `AccountsReceivableCutoffReconciler.java:60-64`) のは別問題で **要追加調査** (00100007/00100009/00100011 が tasklet 側に無い)|
| 14 | CSV フォーマット | OK | `MFJournalCsv` を共通利用 |

### 追加発見 (チェックリスト 13 関連): ゴミ袋 goods_code 定義の不整合
- **箇所**: `TAccountsReceivableSummaryTasklet.java:55-60` (`00100001/3/5/00100101/103/105`) vs `AccountsReceivableCutoffReconciler.java:60-64` (`00100001/3/5/00100007/9/11`)
- **影響**: Reconciler が走った得意先と走らなかった得意先で「ゴミ袋扱いになる goods_code」が変わる。同じ売上が AR 上で `is_otake_garbage_bag=true/false` の別 PK 行になり、CSV で売掛金/未収入金の振り分けが破綻。
- **重要度**: **Major (M-8 として追加)**。即修正が必要。どちらが正かを業務確認のうえ定数 class に切り出して共有。

---

## 総括

「対称構造」を謳った設計書通りには実装されておらず、AR 固有のリッチ機能 (Reconciler, ADMIN 認可, bulk verify) が後付けで増えた結果、買掛側と挙動が分岐している。Critical 2 件は両方とも **データ不整合に直結する** ため、merge 前に修正必須。Major のうち M-1 / M-2 / M-3 / M-8 は業務影響大、残りは技術負債として段階対応可。

承認には最低 C-1 / C-2 / M-2 / M-3 / M-8 の 5 件の修正が必要。
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1923ms:
# 売掛金MF連携（売上帳） 設計書

作成日: 2026-04-17
対象ブランチ: feat/accounts-receivable-mf（新設予定）
関連対称機能: `/finance/accounts-payable`（買掛金一覧） + `/finance/payment-mf-import`（買掛仕入MF変換）

## 1. 目的と業務上の意味

日々のSMILE受注連携で取得した `t_order_detail` から、**請求先**（`m_partner.invoice_partner_code` でまとめた単位）への**売上帳**を月次で集計し、SMILE から出力した**請求書一覧と突合**して一致確認したうえで、マネーフォワードクラウド会計向けの仕訳CSV（売掛金/売上高）を出力する画面。

### 突合キー
- 集計は「店舗 × `m_partner.invoice_partner_code` × 税率 × ゴミ袋フラグ × (都度現金時のみ注文番号)」
- 検証は「店舗 × 請求先コード × 締め日文字列（`YYYY/MM/末` or `YYYY/MM/DD`）」で `t_invoice` と突合
- 差額 ? 3円: 一致扱い（請求書金額に按分）、差額 > 3円: 不一致
- 「上様」(999999, 得意先コード7桁以上) は請求書金額で常に上書き
- イズミ(得意先000231): 当月15日締め + 前月が四半期特殊月(2/5/8/11月)なら前月末締めの請求書も検証対象に合算

### 買掛側との対称構造

| 要素 | 買掛側 | 売掛側 |
|---|---|---|
| 集計元 | `t_purchase_detail` | `t_order_detail` |
| サマリ | `t_accounts_payable_summary` | `t_accounts_receivable_summary` |
| 突合先 | `t_smile_payment`（SMILE支払情報） | `t_invoice`（SMILE請求書） |
| 集計単位 | `m_payment_supplier`（支払先） | `m_partner.invoice_partner_code`（請求先） |
| 検証サービス | `SmilePaymentVerifier` | **`InvoiceVerifier`**（新設） |
| 許容誤差 | 5円 | 3円（既存維持） |
| 画面 | `/finance/accounts-payable` | **`/finance/accounts-receivable`**（新設） |
| 仕訳出力 | 買掛金/仕入（PurchaseJournalIntegration） | 売掛金/売上（SalesJournalIntegration 既存流用） |
| 仕訳CSV DL | 画面からブラウザDL（v2） | 画面からブラウザDL（新設） |

## 2. スコープ

### 本設計に含むもの

- `t_accounts_receivable_summary` への運用系カラム追加（7カラム）
- `InvoiceVerifier` Service 新設（既存 Tasklet から検証ロジック抽出）
- `TAccountsReceivableSummaryTasklet` 改修
  - 締め日タイプ指定パラメータ追加（15日/20日/月末/すべて）
  - 検証NG行も保存（現状は破棄）
  - 手動確定行の保護
- `salesJournalIntegrationJob` 既存流用（`mf_export_enabled=true` のみ対象に改修）
- `/finance/accounts-receivable` 画面新設
  - 一覧表示（検証結果バッジ、差額、請求書金額）
  - 手動確定ダイアログ（行単位）
  - 再集計ボタン（締め日選択）
  - 請求書アップロードボタン（既存 `InvoiceImportDialog` 再利用）
  - 一括検証ボタン
  - 検証済みMF CSV出力ボタン（ブラウザDL）
- Controller `AccountsReceivableController` 新設
- E2E テスト

### 含まないもの

- **定時バッチ設定**: 画面からの手動起動のみ（Q13-2 (d) で確定）
- **売上値引 / 雑所得**: Phase 1 では扱わず、将来の入金処理画面で実装（⑭ 案 D で確定）
- **MF API/MCP 照合機能**: Phase 2 以降で `t_mf_export_lot` と併せて実装
- **請求書CSVインポート機能の変更**: 現状維持（`/finance/invoices` 画面 + 新規に `/finance/accounts-receivable` 画面からも起動可能にするのみ）
- **買掛側・現金出納帳側のロット管理**: Phase 2 で全体同時に対応

## 3. 現状分析

### 既存アセット（再利用）

**バックエンド**
- Entity: `TAccountsReceivableSummary` (PK: `shopNo`, `partnerNo`, `transactionMonth`, `taxRate`, `isOtakeGarbageBag`)
  - `taxIncludedAmountChange` / `taxExcludedAmountChange` = 集計計算値（tmp）
  - `taxIncludedAmount` / `taxExcludedAmount` = CSV出力済み確定値（現行は CSV 出力時に change からコピー）
  - `cutoffDate` = 実際の締め日
  - `orderNo` = 都度現金払い時の注文番号
- Service: `TAccountsReceivableSummaryService`（`findByDateRange`, `save` 等）
- Repository: `TAccountsReceivableSummaryRepository`
- バッチ:
  - `TAccountsReceivableSummaryTasklet` / `AccountsReceivableSummaryConfig` - 集計（`t_order_detail` → `t_accounts_receivable_summary`）
  - `AccountsReceivableToSalesJournalTasklet` / `SalesJournalIntegrationConfig` - CSV出力（Job名: `salesJournalIntegration`）
- 請求書: `TInvoice` Entity, `TInvoiceService.findByShopNoAndPartnerCodeAndClosingDate`
- 勘定科目マスタ: `MfAccountMaster` / `MfAccountMasterService.findByFinancialStatementItemAndAccountName`
- 請求書インポート: `InvoiceImportService`, `InvoiceImportDialog` (Frontend)

**フロントエンド**
- 請求書一覧: `/finance/invoices`（`invoices.tsx`, `InvoiceImportDialog.tsx`）
- 買掛側参考: `accounts-payable.tsx`, `payment-mf-import.tsx`, `BulkVerifyDialog.tsx`, `VerifyDialog.tsx`
- 共通: `PageHeader`, `SearchableSelect`, `DataTable`, `ConfirmDialog`, `sonner`, `TanStack Query`

### ギャップ（買掛側との差分）

| # | 項目 | 現状 | あるべき姿 |
|---|---|---|---|
| G1 | 売掛金一覧画面 | 未実装 | 新設 |
| G2 | 検証結果永続化 | 検証NGは保存されない（ログのみ） | 検証NGも保存、`verification_result` で記録 |
| G3 | 手動確定 | 無し | 行単位の手動確定ダイアログ |
| G4 | 備考 | 無し | `verification_note` で永続化 |
| G5 | MF出力可否切替 | 無し | `mf_export_enabled` トグル |
| G6 | 締め日指定の再集計 | 1引数で全グループ実行 | 締め日タイプ指定で個別実行可能 |
| G7 | 画面からCSV出力 | IntelliJ 手動起動のみ | ボタン1クリックでブラウザDL |
| G8 | 検証ロジック分離 | Tasklet 内 inline | `InvoiceVerifier` Service に抽出 |

## 4. データモデル変更

### 4.1 追加カラム

```sql
ALTER TABLE t_accounts_receivable_summary
  ADD COLUMN verification_result     INTEGER,              -- 1:一致, 0:不一致, NULL:未検証
  ADD COLUMN mf_export_enabled       BOOLEAN DEFAULT FALSE,-- MF出力対象
  ADD COLUMN verified_manually       BOOLEAN DEFAULT FALSE,-- 手動確定フラグ
  ADD COLUMN verification_note       TEXT,                 -- 備考
  ADD COLUMN invoice_amount          NUMERIC(15, 2),       -- 請求書金額（税込）
  ADD COLUMN verification_difference NUMERIC(15, 2),       -- 差額
  ADD COLUMN invoice_no              BIGINT;               -- 突合した請求書No
```

SQL ファイル: `backend/src/main/resources/sql/alter_accounts_receivable_summary_add_verification_fields.sql`

### 4.2 既存カラムの意味整理（(c) 案確定）

| カラム | 意味 |
|---|---|
| `tax_included_amount_change` | 毎回の集計結果（=計算値） |
| `tax_included_amount` | **検証/手動確定済みの連携金額**（CSV出力の元値） |
| `tax_excluded_amount_change` | 同上（税抜） |
| `tax_excluded_amount` | 同上（税抜） |

- 再集計時: `*_change` を上書き、`*_amount` は手動確定行のみ保持（非手動は上書き）
- 手動確定時: `*_amount` = `*_change` として確定、`verified_manually=true`
- CSV出力時: `mf_export_enabled=true` 行の `*_amount` を使用

## 5. バックエンド設計

### 5.1 `InvoiceVerifier` Service（新設）

```java
package jp.co.oda32.batch.finance.service;

@Service
public class InvoiceVerifier {
    /**
     * 売掛金集計結果と請求書を突合して検証結果を返します。
     * 差額が3円未満の場合は請求書金額に按分、3円以上の場合は不一致扱い。
     * 手動確定行 (verified_manually=true) はスキップ。
     * 「上様」(999999) は請求書金額で常に上書き。
     * イズミ(000231) の四半期特殊月は当月15日締め+前月末締めの請求書を合算。
     */
    public Map<InvoiceValidationKey, InvoiceVerificationResult> verify(
        List<TAccountsReceivableSummary> summaries,
        Map<InvoiceValidationKey, TInvoice> invoiceMap,
        LocalDate targetPeriodEndDate
    );
}
```

- 責務: 突合ロジックのみ
- DB保存は呼び出し元（Tasklet or Controller）
- 買掛側 `SmilePaymentVerifier` のインターフェースと対称（`verifyWithSmilePayment` 相当）

### 5.2 `TAccountsReceivableSummaryTasklet` 改修

#### ジョブパラメータ追加
```java
@Value("#{jobParameters['targetDate']}")
private String targetDate;

@Value("#{jobParameters['cutoffType']}")
private String cutoffType; // "15" | "20" | "month_end" | "all"（省略時は "all"）
```

#### 処理フロー改修
1. `cutoffType` に応じて処理グループを絞る
   - `"15"`: `process15thCutoffPartners` のみ
   - `"20"`: `process20thCutoffPartners` のみ
   - `"month_end"`: `processMonthEndCutoffPartners` のみ（都度現金含む）
   - `"all"`: 全グループ（現行動作）
2. 検証ロジックは `InvoiceVerifier` に委譲
3. **検証結果を全件保存**:
   - 一致 → `verification_result=1`, `mf_export_enabled=true`
   - 不一致 → `verification_result=0`, `mf_export_enabled=false`
   - 請求書なし → `verification_result=0`, `mf_export_enabled=false`, `invoice_no=null`
4. **手動確定行の保護**:
   - `verified_manually=true` の行は `*_amount`, `*_change`, 検証結果を上書きしない
   - ただし、請求書との突合情報（`invoice_no`, `invoice_amount`, `verification_difference`）は更新してログ表示する

### 5.3 `AccountsReceivableToSalesJournalTasklet` 改修

```java
// 既存: 全件取得
List<TAccountsReceivableSummary> summaries = tAccountsReceivableSummaryService.findByDateRange(from, to);

// 改修: mf_export_enabled=true のみ
List<TAccountsReceivableSummary> summaries = 
    tAccountsReceivableSummaryService.findByDateRangeAndMfExportEnabled(from, to, true);
```

- CSV出力後の `setTaxIncludedAmount(summary.getTaxIncludedAmountChange())` コピー処理は維持（CSV出力済みマーカーの意味合い）

### 5.4 `AccountsReceivableController`（新設）

エンドポイント（買掛側 `FinanceController#listAccountsPayable` 系と対称）:

| Method | Path | 用途 |
|---|---|---|
| GET | `/api/v1/finance/accounts-receivable` | 一覧取得（ページング、検索条件フィルタ） |
| GET | `/api/v1/finance/accounts-receivable/summary` | サマリ（未検証・不一致件数、差額合計） |
| POST | `/api/v1/finance/accounts-receivable/aggregate` | 再集計バッチ起動（`cutoffType`, `targetDate`） |
| POST | `/api/v1/finance/accounts-receivable/bulk-verify` | 一括検証（画面の検索条件範囲） |
| PUT | `/api/v1/finance/accounts-receivable/{pk}/verify` | 手動確定（行単位） |
| DELETE | `/api/v1/finance/accounts-receivable/{pk}/manual-lock` | 手動確定解除 |
| PATCH | `/api/v1/finance/accounts-receivable/{pk}/mf-export` | MF出力フラグON/OFF |
| GET | `/api/v1/finance/accounts-receivable/export-mf-csv` | CSV ダウンロード |

PK は `shopNo/partnerNo/transactionMonth/taxRate/isOtakeGarbageBag` の5要素複合。URL は `/{shopNo}/{partnerNo}/{transactionMonth}/{taxRate}/{isOtakeGarbageBag}` 形式。

#### CSV DL エンドポイント仕様
```
GET /api/v1/finance/accounts-receivable/export-mf-csv
  ?shopNo={shopNo}
  &fromDate={yyyyMMdd}
  &toDate={yyyyMMdd}
  &initialTransactionNo={number, optional default=1001}
  
Response:
  Content-Type: text/csv; charset=Shift_JIS (CP932)
  Content-Disposition: attachment; filename="accounts_receivable_to_sales_journal_{yyyyMMdd}_{yyyyMMdd}.csv"
  Body: MFJournalCsv ヘッダ + 売掛金/売上仕訳行（`mf_export_enabled=true` のみ）
```

CSV生成ロジックは既存 `AccountsReceivableToSalesJournalTasklet#writeToFile` のロジックを Service メソッドに抽出して再利用（Job からもController からも呼べるように）。

## 6. フロントエンド設計

### 6.1 ルート

- 一覧: `/finance/accounts-receivable`
- ページファイル: `app/(authenticated)/finance/accounts-receivable/page.tsx`
- コンポーネント: `components/pages/finance/accounts-receivable.tsx`

### 6.2 ページレイアウト

```
┌──────────────────────────────────────────────────────────────────────┐
│ 売掛金一覧                       [再集計][請求書取込][一括検証][MF CSV出力] │ PageHeader + actions
├──────────────────────────────────────────────────────────────────────┤
│ 店舗 [▼] 期間 [2026-03-21]?[2026-04-20] 得意先 [▼] 検証 [すべて/未検証/不一致] │
│ [検索]                                                                  │
├──────────────────────────────────────────────────────────────────────┤
│ ? 未検証 3件 / 不一致 1件（差額合計 +1,200円）                            │ サマリアラート
├──────────────────────────────────────────────────────────────────────┤
│ 検証 | 店舗 | 得意先Code | 得意先名 | 締め日 | 取引日 | 税率 |            │
│      税込金額 | 税抜金額 | 請求書金額 | 差額 | 手動 | MF | 備考 | 請求書No │
│ [一致]  1  000231  イズミ     15日  2026/04/15 10% 543,000 493,636 543,000 0  ◯ ? -       #1234  │
│ [不一致] 1  301491  クリーンラボ 月末 2026/04/30 10% 120,000 109,090 123,240 -3,240 - - -  #1240  │
│ [未検証] 1  000100  ○○商会   20日  2026/04/20 10% 87,500 79,545 -         -    -  - -      -   │
├──────────────────────────────────────────────────────────────────────┤
│ ページネーション                                                         │
└──────────────────────────────────────────────────────────────────────┘
```

### 6.3 再集計ダイアログ（案 P: ラジオボタン）

```
┌──────────────────────────────────────┐
│ 再集計                                │
├──────────────────────────────────────┤
│ 対象日: [2026-04-20] (yyyy-MM-dd)     │
│                                        │
│ 締め日タイプ:                          │
│   ● すべて                            │
│   ○ 15日締め                          │
│   ○ 20日締め                          │
│   ○ 月末締め                          │
│                                        │
│ ※ 手動確定済みの行は上書きされません    │
│                                        │
│ [キャンセル] [再集計実行]              │
└──────────────────────────────────────┘
```

### 6.4 手動確定ダイアログ（買掛側 `VerifyDialog` 参考・行単位）

```
┌──────────────────────────────────────────────┐
│ 検証確定                                      │
├──────────────────────────────────────────────┤
│ 店舗: 1   得意先: 301491 クリーンラボ         │
│ 税率: 10%  取引月: 2026/04/30                 │
│                                                │
│ 集計金額（税込）: 120,000                      │
│ 請求書金額（税込）: 123,240                   │
│ 差額: -3,240                                  │
│                                                │
│ 確定金額（税込）: [123,240]                   │
│ 確定金額（税抜）: [112,036]                   │
│ 備考:                                         │
│ [____________________________________]       │
│ [____________________________________]       │
│                                                │
│ ? MF出力対象にする                            │
│                                                │
│ [キャンセル] [確定]                            │
└──────────────────────────────────────────────┘
```

### 6.5 一括検証ボタン

- 画面の検索条件範囲 (`shopNo`, `fromDate`, `toDate`) で `POST /bulk-verify` を呼び出し
- レスポンス: `{ matchedCount, mismatchCount, notFoundCount }`
- toast で結果通知 + 一覧再取得

### 6.6 請求書取込ボタン

- 既存 `InvoiceImportDialog` を再利用（import）
- 取込完了後に一覧を invalidate → 再取得

### 6.7 MF CSV出力ボタン

- ConfirmDialog で「現在の検索条件範囲の検証済み売掛金をCSV出力します。よろしいですか？」
- `window.location.href = '/api/v1/finance/accounts-receivable/export-mf-csv?...'`
- ブラウザの標準DL動作で CP932 CSV が保存される

### 6.8 サイドバー追加

`components/layout/Sidebar.tsx` の財務会計セクションに追加:
```
財務会計
├ 買掛金一覧
├ 売掛金一覧 ← NEW
├ 買掛仕入MF変換
├ 現金出納帳MF変換
├ MF仕訳ルール
├ MF取引先マッピング
└ 請求書一覧
```

## 7. 検証ロジック詳細

```
for each 集計結果 (shopNo, invoicePartnerCode, closingDateStr):
  if verified_manually == true:
    continue  # スキップ
    
  invoice = t_invoice.find(shopNo, invoicePartnerCode, closingDateStr)
  
  # 特殊処理
  if invoicePartnerCode == "301491":
    invoice = t_invoice.find(1, "301491", ...)  # クリーンラボは店舗番号1で検索
    
  if invoicePartnerCode == "000231":
    # イズミ四半期特殊処理
    invoices = getSpecialPartnerClosingDates(targetPeriodEndDate)
    invoice = 合計の仮想請求書
    
  if invoicePartnerCode.startsWith("999999") or len(invoicePartnerCode) >= 7:
    # 上様: 請求書金額で常に上書き（按分）
    比率按分で金額調整
    verification_result = 1
    mf_export_enabled = true
    invoice_amount = invoice.netSalesIncludingTax
    continue
    
  if invoice == null:
    verification_result = 0
    mf_export_enabled = false
    invoice_amount = null
    verification_difference = null
    continue
    
  diff = invoice.netSalesIncludingTax - 集計税込金額
  
  if diff == 0:
    verification_result = 1
    mf_export_enabled = true
  elif abs(diff) <= 3:
    # 3円未満許容 → 請求書金額に按分
    比率按分で金額調整
    verification_result = 1
    mf_export_enabled = true
  else:
    verification_result = 0
    mf_export_enabled = false
    
  invoice_amount = invoice.netSalesIncludingTax
  verification_difference = diff
  invoice_no = invoice.invoiceId
```

## 8. 画面カラム構成

| # | 列 | 幅 | 備考 |
|---|---|---|---|
| 1 | 検証結果バッジ | 90px | 一致(緑) / 不一致(赤) / 未検証(灰) |
| 2 | 店舗 | 50px | |
| 3 | 得意先コード | 90px | `partnerCode` |
| 4 | 得意先名 | 180px | `m_partner` から JOIN |
| 5 | 締め日 | 80px | 月末/15日/20日/都度現金 |
| 6 | 取引日 | 90px | `transactionMonth` |
| 7 | 税率 | 60px | 10% / 8% / 0% |
| 8 | 税込金額 | right, 100px | `taxIncludedAmountChange` |
| 9 | 税抜金額 | right, 100px | `taxExcludedAmountChange` |
| 10 | 請求書金額 | right, 100px | `invoiceAmount` |
| 11 | 差額 | right, 80px | `verificationDifference`（赤字表示） |
| 12 | 手動 | center, 50px | `verifiedManually` ?/- |
| 13 | MF | center, 50px | `mfExportEnabled` ?/- |
| 14 | 備考 | 150px | `verificationNote`（省略表示） |
| 15 | 請求書No | 80px | `invoiceNo`（クリックで `/finance/invoices?id=` ） |
| 16 | 操作 | 80px | [詳細] ボタン |

注文番号 (`orderNo`) は都度現金払いのみ保持されるため、詳細ダイアログで表示。

## 9. API仕様詳細

### 9.1 GET /accounts-receivable

**Request (query params)**
- `shopNo`: Integer, required (admin は 0 で全店舗)
- `fromDate`: yyyy-MM-dd, optional
- `toDate`: yyyy-MM-dd, optional
- `partnerCode`: String, optional
- `verificationResult`: 0 | 1 | null, optional（ALL と未検証の区別）
- `page`: Integer (default 0)
- `size`: Integer (default 50)
- `sort`: String (default `transactionMonth,desc`)

**Response**
```json
{
  "content": [
    {
      "shopNo": 1,
      "partnerNo": 12345,
      "partnerCode": "000231",
      "partnerName": "イズミ",
      "transactionMonth": "2026-04-15",
      "taxRate": 10.00,
      "isOtakeGarbageBag": false,
      "cutoffDate": 15,
      "orderNo": null,
      "taxIncludedAmountChange": 543000,
      "taxExcludedAmountChange": 493636,
      "taxIncludedAmount": 543000,
      "taxExcludedAmount": 493636,
      "invoiceAmount": 543000,
      "verificationDifference": 0,
      "verificationResult": 1,
      "mfExportEnabled": true,
      "verifiedManually": false,
      "verificationNote": null,
      "invoiceNo": 1234
    }
  ],
  "totalElements": 42,
  "totalPages": 1,
  "number": 0
}
```

### 9.2 POST /accounts-receivable/aggregate

**Request**
```json
{
  "targetDate": "20260420",
  "cutoffType": "all"   // "15" | "20" | "month_end" | "all"
}
```

**Response**
```json
{
  "jobExecutionId": 12345,
  "status": "STARTED"
}
```

- 既存 `BatchController#execute` と同様に 429 多重実行ガード
- 内部で `JobLauncher.run(accountsReceivableSummaryJob, params)` を呼ぶ

### 9.3 POST /accounts-receivable/bulk-verify

**Request** (body)
```json
{
  "shopNo": 1,
  "fromDate": "2026-03-21",
  "toDate": "2026-04-20"
}
```

**Response**
```json
{
  "matchedCount": 35,
  "mismatchCount": 3,
  "notFoundCount": 4,
  "skippedManualCount": 0
}
```

### 9.4 PUT /accounts-receivable/{pk}/verify

**Request**
```json
{
  "taxIncludedAmount": 123240,
  "taxExcludedAmount": 112036,
  "verificationNote": "振込手数料調整分として確認",
  "mfExportEnabled": true
}
```

**Response**: 204 No Content
- `verified_manually = true` で保存
- `verification_result = 1` を強制設定
- `*_amount` を指定値で確定
- `*_change` は現在値を保持

### 9.5 DELETE /accounts-receivable/{pk}/manual-lock

**Response**: 204 No Content
- `verified_manually = false` にする
- 次回の一括検証対象に戻る

### 9.6 PATCH /accounts-receivable/{pk}/mf-export

**Request**
```json
{ "enabled": true }
```

**Response**: 204 No Content

## 10. 実装順序

| # | 作業 | 影響範囲 | 完了条件 |
|---|---|---|---|
| 1 | 設計書作成 | `claudedocs/` | 本書のレビュー完了 |
| 2 | DDL `alter_accounts_receivable_summary_add_verification_fields.sql` | `backend/src/main/resources/sql/` | ローカルDBで ALTER 実行成功 |
| 3 | Entity 拡張 `TAccountsReceivableSummary` | `backend/.../domain/model/finance/` | `./gradlew compileJava` 通過 |
| 4 | Repository / Service メソッド追加 | `backend/.../domain/repository,service/finance/` | `findByDateRangeAndMfExportEnabled` 等 |
| 5 | `InvoiceVerifier` 新設 | `backend/.../batch/finance/service/` | 単体テスト追加 |
| 6 | `TAccountsReceivableSummaryTasklet` 改修 | `backend/.../batch/finance/` | 既存Job 手動実行で検証NGも保存されることを確認 |
| 7 | CSV生成ロジックを Service に抽出 | `backend/.../domain/service/finance/SalesJournalCsvService.java`（新設） | `AccountsReceivableToSalesJournalTasklet` が Service を利用する形に |
| 8 | `AccountsReceivableController` 新設 | `backend/.../api/finance/` | 各エンドポイントで curl 疎通 |
| 9 | Frontend `accounts-receivable.tsx` 実装 | `frontend/components/pages/finance/` | 画面表示・各ボタン動作確認 |
| 10 | ページ追加 `app/(authenticated)/finance/accounts-receivable/page.tsx` | `frontend/app/` | ルート正常 |
| 11 | Sidebar リンク追加 | `frontend/components/layout/Sidebar.tsx` | ナビゲーション確認 |
| 12 | E2E テスト `accounts-receivable.spec.ts` | `frontend/e2e/` | 全ケース PASS |
| 13 | `tsc --noEmit` + `./gradlew compileJava` + 実バックエンド疎通 | - | CLAUDE.md 準拠の増分レビュー |

## 11. テスト方針

### 11.1 単体テスト
- `InvoiceVerifierTest`: 一致/3円以内差額/3円超差額/請求書なし/上様/イズミ四半期 のケース
- 既存 `TAccountsReceivableSummaryTaskletTest`（あれば）を回帰確認

### 11.2 E2E
- `accounts-receivable.spec.ts`:
  - 一覧表示
  - 検索フィルタ
  - 再集計ボタン（締め日選択ダイアログ）
  - 請求書取込ボタン（既存 InvoiceImportDialog 連携）
  - 一括検証ボタン
  - 手動確定ダイアログ
  - MF CSV出力（DL ヘッダ確認）
  - Sidebar リンク

### 11.3 実バックエンド疎通
CLAUDE.md の「実バックエンド疎通を最低1パス」要件に従い:
1. `./gradlew bootRun --args='--spring.profiles.active=web,dev'` で起動
2. curl で各エンドポイント疎通
3. ブラウザで実画面の各ボタン動作を1パス確認

## 12. 今後の Phase

### Phase 2（後続）
- **ロット管理**: `t_mf_export_lot` 新設、CSV 出力履歴の統一管理（買掛・現金出納帳・売掛）
- **MF API 照合**: MCP or REST で MF 会計から仕訳取得 → ロット別の照合マーク
  - `t_mf_journal_snapshot` キャッシュ
  - `mf_journal_id` / `mf_match_status` をサマリテーブルに追加
- 当面は Claude Code から MCP 経由の月次手動チェックで代替可能

### Phase 3（将来）
- `/finance/invoices` 請求書一覧画面を**入金処理画面**へリフォーム
  - 銀行連携データ（MFからは取得不可、別経路で取り込み想定）と請求書の突合
  - 売上値引 / 雑所得の入力・仕訳展開
  - CSV: 入金処理仕訳（預金/売掛金、売上値引/売掛金、売掛金/雑所得 等）

## 13. 前提・リスク・確認事項

### 前提
- SMILE請求書インポート (`InvoiceImportDialog` → `t_invoice`) の運用は継続
- MF勘定科目マスタ (`MfAccountMaster`) は既にメンテされている（売掛金/売上高/クリーンラボ売上高 等）
- 特殊得意先 301491（クリーンラボ）, 000231（イズミ）, 999999/7桁以上（上様）の取扱いは既存ロジック踏襲

### リスク
- **R1**: 既存 Tasklet の検証ロジックを Service に抽出する際、四半期特殊処理のエッジケース見落とし → 対策: 既存テストデータでの回帰確認、該当期の実データで疎通
- **R2**: CSV DL エンドポイントのメモリ使用量（大量行の場合） → 対策: StreamingResponseBody or fromDate/toDate 必須化
- **R3**: 手動確定行の `*_change` 上書きポリシー（現状案では上書きしない）が業務要件と合うか → 確認: ユーザ運用想定のレビュー

### 確認事項（実装後）
- 締め日タイプ切替の UX 確認
- 一括検証ボタンの性能（画面一覧の検索条件範囲で数千行?）
- CSV ファイル名・ヘッダ・エンコーディングが既存バッチと完全一致しているか

---

**承認後、実装順序1（本書）→2（DDL）→... と順次着手します。**
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

2026-05-04T01:54:25.497769Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 4.6 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg --files | rg "(AccountsReceivable|accounts-receivable|TAccountsRec 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 exited 1 in 4561ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg --files | rg "(AccountsReceivable|accounts-receivable|TAccountsRec 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 succeeded in 5583ms:
# コードレビュー: 売掛金 (Cluster E)

レビュー日: 2026-05-04
ブランチ: `refactor/code-review-fixes`
レビュアー: code-reviewer subagent (Opus)
対象設計レビュー: `claudedocs/design-review-accounts-receivable.md` (Critical 2 / Major 8 / Minor 6)

## 前提

設計レビュー指摘 (C-1, C-2, M-1?M-8, m-1?m-6, S-1?S-12, D-1?D-5) と重複する内容は本レビューで再掲しない。
本レビューはコード固有 (実装パターン、ライブラリ用法、AOP 連携、フロント TS、エンコーディング、Tasklet 仕様の不整合) に絞る。

## サマリー

- 総指摘件数: **Blocker 0 / Critical 3 / Major 5 / Minor 8**
- 承認状態: **Needs Revision**
- 新発見トップ:
  1. (C-impl-1) `markExported` が「`taxIncludedAmount` が null のときだけコピー」の片側更新で、**1度焼かれた行を再 DL すると `*_change` が変わっていても古い値で CSV 出力される**。設計レビュー C-1 の「失敗時マーカー焼き」とは別軸の、**経年バグ** (verify→DL→aggregate→DL の二回目 DL でサイレント不一致)。
  2. (C-impl-2) `AccountsReceivableCutoffReconciler` が cash-on-delivery (`cutoffDate=-1`) のレコードを再集計対象に含める設計だが、再集計後の `cutoffDate` を `30` (月末) で上書きしてしまう。**運用者から見ると「都度現金払い」だった行が黙って「月末締め」表示に化け**、後続の `InvoiceVerifier#formatClosingDateForSearch` が "末" を引くため検索は通るが、UI 表示と実マスタが乖離する。
  3. (C-impl-3) `AccountsReceivableToSalesJournalTasklet` の `FileWriter` がプラットフォームデフォルトエンコーディングで CSV を書く。Controller 側は `CP932` 指定なのに **バッチ起動経由の CSV だけ Windows 環境では MS932、Linux 環境では UTF-8 で出力される**ため、本番環境次第で MF 取込が文字化け。

---

## Blocker

なし。

---

## Critical

### C-impl-1: `markExported` が片側更新で再 DL 時に古い値を凍結する
- **箇所**:
  - `backend/src/main/java/jp/co/oda32/domain/service/finance/SalesJournalCsvService.java:227-236`
  - `backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java:287-290`
- **コード**:
  ```java
  // SalesJournalCsvService.java:228-235
  public void markExported(List<TAccountsReceivableSummary> summaries) {
      for (TAccountsReceivableSummary s : summaries) {
          if (s.getTaxIncludedAmount() == null && s.getTaxIncludedAmountChange() != null) {
              s.setTaxIncludedAmount(s.getTaxIncludedAmountChange());
          }
          if (s.getTaxExcludedAmount() == null && s.getTaxExcludedAmountChange() != null) {
              s.setTaxExcludedAmount(s.getTaxExcludedAmountChange());
          }
      }
  }
  ```
  ```java
  // SalesJournalCsvService.java:87-89 (writeCsv)
  BigDecimal taxIncluded = summary.getTaxIncludedAmount() != null
          ? summary.getTaxIncludedAmount()
          : summary.getTaxIncludedAmountChange();
  ```
- **問題**:
  1. `applyMatched` (`InvoiceVerifier.java:353-354`) で一致行は既に `taxIncludedAmount = taxIncludedAmountChange` 焼き済み。markExported は **null guard のため no-op**。
  2. 運用者が `aggregate` を再実行 → tasklet (`TAccountsReceivableSummaryTasklet.java:248-249`) で `taxIncludedAmountChange` が新値に更新。**`taxIncludedAmount` は据え置き** (verifiedManually でなくても)。
  3. 再度 CSV を DL → `writeCsv` は古い `taxIncludedAmount` を使う。**MF に出る金額と AR テーブルの最新集計が乖離**。
  4. 設計レビュー C-1 (失敗時マーカー焼き) と組み合わせると、ネットワーク切断 → 半分焼き → 再集計 → 再 DL の経路で「半端な凍結値で MF 連携」が確定。
- **影響**: 会計連携の金額不整合。設計レビュー M-5 が指摘した「按分後の `*_change` を上書き」する仕様と組み合わせ、行の値が二重に書き換わる。
- **修正案**:
  - `markExported` を「**常に上書き**」に変更し、null チェックを撤去。あるいは、
  - `applyMatched` で `taxIncludedAmount` を焼かないよう変更し (`InvoiceVerifier.java:353-354` を削除)、CSV DL 時にのみ markExported で焼くワンパス化。設計書 §5.3 の「二段焼き」設計意図と整合させる。

### C-impl-2: Reconciler が cash-on-delivery 行の `cutoffDate=-1` を `30` に黙って上書き
- **箇所**: `backend/src/main/java/jp/co/oda32/batch/finance/service/AccountsReceivableCutoffReconciler.java:236-246, 360-373`
- **コード**:
  ```java
  // parseClosingDate "末" 解釈 (line 236-240)
  if ("末".equals(dayPart)) {
      LocalDate end = ym.atEndOfMonth();
      LocalDate start = ym.atDay(1);
      return Optional.of(new ClosingDateInfo(end, 30, start, end)); // cutoffCode=30 固定
  }
  ```
  ```java
  // aggregateForPartner (line 361-373)
  result.add(TAccountsReceivableSummary.builder()
          .cutoffDate(closing.cutoffCode)   // ← cash-on-delivery の -1 が消える
          ...
          .build());
  ```
- **問題**:
  1. `isExcludedPartner` (`AccountsReceivableCutoffReconciler.java:283-289`) は partnerCode (上様/Clean Lab/?7桁) のみ除外する。`PaymentType.CASH_ON_DELIVERY` (`m_partner.cutoff_date = -1`) の partner は除外されず、Reconciler の対象に入る。
  2. 該当 partner の AR は `transaction_month=月末日` で保存済 (tasklet `processMonthEndCutoffPartners`)。Reconciler は同じく "末" 請求書を引いてきて `expectedTxMonth=月末日` を作る → AR の transaction_month と一致 → スキップされる。**通常運用ではバグが顕在化しない**。
  3. 一度でも transaction_month がズレた行 (例: 旧バッチで月初日に保存されていたデータ移行直後) が混じると Reconciler が走り、新行を `cutoffDate=30` で INSERT。**元の `-1`（都度現金払い）情報がロスト**し、UI 表示は「月末」になる。
  4. 後続の verify 経路で `InvoiceVerifier#formatClosingDateForSearch` が `cutoffDate=30` を `MONTH_END` 扱い ("末" 検索) するため動作はするが、運用者から見ると「マスタ上は -1 の partner なのに AR の `cutoff_date` 列が 30」となり、トレーサビリティ破綻。
- **影響**: マスタ vs AR の定義不整合。集計区分のレポート (`processMonthEndCutoffPartners` で都度現金払いの件数を別カウントするロジックに依拠する集計) が崩れる。
- **修正案**:
  - Reconciler が新行を作る際、対象 partner の元のマスタ `cutoff_date` を保持して書き戻す (`PartnerIndex` から `m_partner.getCutoffDate()` を引く)。
  - もしくは `isExcludedPartner` に `PaymentType.fromCutoffCode(partnerCutoffDate) == CASH_ON_DELIVERY` 判定を加え、cash-on-delivery を Reconciler 対象から除外。設計書 (`§AccountsReceivableCutoffReconciler` クラスコメント `line 50` `「都度現金払い (master cutoff_date = -1): 元から月次集約なので再集計不要」`) と実装が乖離している。

### C-impl-3: バッチ Tasklet の `FileWriter` がプラットフォーム既定エンコーディング
- **箇所**: `backend/src/main/java/jp/co/oda32/batch/finance/AccountsReceivableToSalesJournalTasklet.java:74,81`
- **コード**:
  ```java
  try (FileWriter writer = new FileWriter(fileName)) {
      salesJournalCsvService.writeCsv(summaries, writer, initialTransactionNo);
  }
  ```
- **問題**:
  - `FileWriter(String)` (Java 21) はプラットフォーム既定 charset を使用 (Windows 11 = `MS932`、Linux = `UTF-8`)。
  - Controller 経由 (`AccountsReceivableController.java:282`) は `OutputStreamWriter(baos, CP932)` で明示。**バッチ vs Controller で CSV のエンコーディングが分岐**。
  - 本番が Linux (推測) なら UTF-8 出力 → MF 取込で日本語列 (摘要・補助科目) が文字化け。
- **影響**: バッチを cron 実行している環境で MF 取込失敗。手動 DL 経由は通るが運用者は気付きにくい。
- **修正案**:
  - `new OutputStreamWriter(new FileOutputStream(fileName), Charset.forName("windows-31j"))` に置換。CP932 定数を `SalesJournalCsvService` または共通 util に集約 (現状 `AccountsReceivableController.java:68` のみ定義、買掛側 `FinanceController.java` も別定義)。

---

## Major

### M-impl-1: Controller `@RequestMapping("/api/v1/finance/accounts-receivable")` が JobLauncher を直接持ち、責務肥大
- **箇所**: `backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java:75-79, 120-167`
- **問題**:
  - Controller が `JobLauncher` / `ApplicationContext` / `@Qualifier("batchTaskExecutor") ThreadPoolTaskExecutor` を直接 DI して `aggregate` ハンドラ内で 30 行のジョブ起動コード (Bean lookup → JobParametersBuilder → executor.submit) を組み立てている。
  - 同パターンが買掛側 `FinanceController` にも散見され、本来は `BatchJobLauncherService` 等の共通 Service に集約すべき (CLAUDE.md `Controller は薄く` ルール違反)。
  - ApplicationContext からの Bean lookup (`applicationContext.getBean(beanName)`) はテスタビリティを下げる。
- **修正案**:
  - `BatchJobLauncherService.launchAccountsReceivableSummary(targetDate, cutoffType)` を新設し、Bean 解決と非同期 submit をカプセル化。Controller は薄く委譲。

### M-impl-2: `aggregate` の `cutoffType` 検証ロジックがマジックリテラル
- **箇所**: `backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java:130-137`
- **コード**:
  ```java
  if (!Set.of("all", "15", "20", "month_end").contains(cutoffType)) { ... }
  ```
- **問題**:
  - tasklet 側に `TAccountsReceivableSummaryTasklet.CUTOFF_TYPE_*` 定数が既に存在 (line 63-66) しているのに、Controller では文字列リテラル列挙。タイポ・追加忘れの温床。
  - `AccountsReceivableAggregateRequest.java:18` の Javadoc にも `"all" | "15" | "20" | "month_end"` がコメント記述のみで型保証なし。
- **修正案**:
  - `enum CutoffType { ALL("all"), DAY_15("15"), DAY_20("20"), MONTH_END("month_end") }` を定義し、Request DTO の `@Pattern` または Spring `Converter` で受ける。tasklet 側の文字列定数も同 enum に統合。

### M-impl-3: PathVariable で `boolean` を受けるエンドポイント設計
- **箇所**: `backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java:213, 232, 247`
- **コード**:
  ```java
  @PutMapping("/{shopNo}/{partnerNo}/{transactionMonth}/{taxRate}/{isOtakeGarbageBag}/verify")
  public ResponseEntity<AccountsReceivableResponse> verify(
          @PathVariable boolean isOtakeGarbageBag, ...) { ... }
  ```
- **問題**:
  - URL に boolean が混じる REST 設計はアンチパターン。`true`/`false` 以外の任意の文字列 (`yes`, `1`, `True`) で挙動がブレる (Spring の `Boolean.parseBoolean` は `"true"` 以外を全て `false`)。
  - PK が 5 要素 (shopNo / partnerNo / transactionMonth / taxRate / isOtakeGarbageBag) で、URL 階層が深すぎ (5 階層) て可読性も悪い。
  - 買掛側 `AccountsPayableController` は同様パターンか確認すべき (対称性の観点で)。
- **修正案**:
  - クエリパラメータ `?isOtakeGarbageBag=true` に変更、PK を `shopNo/partnerNo/transactionMonth/taxRate` の 4 階層に減らす。
  - もしくは、PK を `partnerCode-yyyymmdd-taxRate-otake` のような単一複合キー文字列にエンコードし、URL `/verify/{compositeKey}` 形式へ。

### M-impl-4: Reconciler が `MPartner.cutoffDate` を `Integer` で扱い null を `null` のまま `PartnerKey` 解決
- **箇所**: `backend/src/main/java/jp/co/oda32/batch/finance/service/AccountsReceivableCutoffReconciler.java:217-225`
- **コード**:
  ```java
  private Optional<TInvoice> findInvoiceForPartner(Integer shopNo, String partnerCode, YearMonth ym) {
      String yyyymm = String.format("%d/%02d", ym.getYear(), ym.getMonthValue());
      String[] candidates = {yyyymm + "/末", yyyymm + "/20", yyyymm + "/15"};
      for (String cd : candidates) {
          Optional<TInvoice> inv = tInvoiceService.findByShopNoAndPartnerCodeAndClosingDate(shopNo, partnerCode, cd);
          if (inv.isPresent()) return inv;
      }
      return Optional.empty();
  }
  ```
- **問題**:
  - 検索を 3 種類順次実行 (N+1 ではないが 3×N partner クエリ)。M-1 で指摘されたパフォーマンス劣化の追加証跡。
  - **3 候補とも見つかった場合、最初にヒットした "末" を採用する**。マスタ `cutoff_date` が "20" なのに偶然 "末" 請求書も登録されていたら誤マッチ。順序の根拠が無い。
  - クリーンラボ (`partnerCode = "301491"`) の特殊 shop=1 強制が `InvoiceVerifier.java:206-208` にあるのに、Reconciler は `isExcludedPartner` で除外しているだけで「shop=1 で再検索」処理が欠落。Clean Lab を Reconciler の対象に入れたくなった瞬間にバグになる潜在課題 (現状は OK)。
- **修正案**:
  - master `cutoff_date` を `PartnerIndex` に持っておき、優先順位を「マスタの値 → 末 → DD」に変更。あるいは、3 候補が全部ヒットしたら `log.warn` で運用通知。

### M-impl-5: Tasklet の `try { ... } catch (Exception e) { contribution.setExitStatus(FAILED); throw new RuntimeException(...) }` 二重通知
- **箇所**: `backend/src/main/java/jp/co/oda32/batch/finance/TAccountsReceivableSummaryTasklet.java:112-116`
- **コード**:
  ```java
  } catch (Exception e) {
      log.error("売掛金集計バッチ処理中に致命的なエラーが発生しました。", e);
      contribution.setExitStatus(org.springframework.batch.core.ExitStatus.FAILED);
      throw new RuntimeException("売掛金集計バッチ処理がエラーで終了しました。", e);
  }
  ```
- **問題**:
  - Spring Batch では `tasklet` から例外 throw すれば自動的に `ExitStatus.FAILED` になる。`setExitStatus` の手動呼び出しは冗長で、かつ `setExitStatus` 後に throw すると **手動 set した方が後続 listener で一旦反映され、その後 `JobExecutionException` の handler で再上書き** される。
  - `RuntimeException` でラップすることで stack trace が二重 (`Caused by: ...`) になり、ログ可読性が低下。
  - 買掛側 `AccountsPayableSummaryTasklet` の例外ハンドリングと比較して非対称。
- **修正案**:
  - `try/catch` を撤去し、tasklet 内の例外をそのまま throw。Spring Batch の framework に任せる。
  - 失敗通知を独自に追加したい場合は `@OnProcessError` listener を別 Bean で定義。

---

## Minor

### m-impl-1: `AccountsReceivableVerifyRequest` に `mfExportEnabled = Boolean.TRUE` の field initializer
- **箇所**: `backend/src/main/java/jp/co/oda32/dto/finance/AccountsReceivableVerifyRequest.java:24`
- **コード**: `private Boolean mfExportEnabled = Boolean.TRUE;`
- **問題**: Jackson は `@Data` 経由の setter を呼ぶため、リクエスト本文に `mfExportEnabled` フィールドが省略された場合の挙動は **Jackson のデフォルト初期化 → Java 初期化値 = TRUE**。一見良さそうだが、JSON 上 `"mfExportEnabled": null` が来たら **TRUE** にも **null** にも倒れる (Jackson のバージョンに依存)。Controller (line 227) では `request.getMfExportEnabled() != null ? ... : true` で再度 fallback しているため二重ガード。
- **修正案**:
  - DTO の field initializer を撤去し、Controller の null fallback だけ残す。明示的な「省略時 true」を Javadoc に書く。

### m-impl-2: `pkToPath` で `taxRate` を文字列補間 (URL 経路でロケール依存の可能性)
- **箇所**: `frontend/types/accounts-receivable.ts:117`
- **コード**: `return ${ar.shopNo}/${ar.partnerNo}/${ar.transactionMonth}/${ar.taxRate}/${ar.isOtakeGarbageBag}`
- **問題**:
  - JS の `Number.toString()` は常に英数字なのでロケール依存はないが、`taxRate` が `8.00` のような小数で来た場合 `8` に丸まる ([Number(8.00) === 8](https://developer.mozilla.org/))。
  - バックエンドの `BigDecimal taxRate` は `8.00` で persist されているため、URL `/8` が PathVariable `BigDecimal taxRate` にバインドされて `8` として解決され、DB の `8.00` と `compareTo` 一致。動作は OK だが、**`scale` が違う行が複数ある場合に最初の 1 件しか取れない**。
- **修正案**:
  - `String(ar.taxRate)` ではなく、明示的に `Number(ar.taxRate).toFixed(2)` でゼロパディング。

### m-impl-3: `defaultDateRange` の境界判定が「< 20」で 20 日当日が前期間扱い
- **箇所**: `frontend/types/accounts-receivable.ts:97-103`
- **コード**:
  ```ts
  if (d.getDate() < 20) {
      toM -= 1
      ...
  }
  const to = new Date(toY, toM, 20)
  const from = new Date(toY, toM - 1, 21)
  ```
- **問題**:
  - 20 日当日に画面を開くと「当月 21日?翌月 20日」が表示される。「当日締め」を見たいケース (毎月 20日に運用が回る) で 1 ヶ月先の空期間が表示されて違和感。
  - 設計書のデフォルト挙動と仕様確認が必要。
- **修正案**:
  - `< 20` を `<= 20` に変更し、20 日当日は当月締め期間を表示。あるいは Tooltip で挙動を明示。

### m-impl-4: `accounts-receivable.tsx` の `tableTotals` が `taxIncludedAmountChange` を集計、CSV 出力金額 (`taxIncludedAmount`) と乖離
- **箇所**: `frontend/components/pages/finance/accounts-receivable.tsx:277-285`
- **コード**:
  ```ts
  inc += Number(r.taxIncludedAmountChange ?? 0)
  ```
- **問題**:
  - 画面右上の「税込合計」は `*_change` (集計値) ベース。MF CSV は `taxIncludedAmount` (確定値) ベース。**運用者が画面の税込合計と CSV の合計を突合しようとすると、検証済み行で値が乖離して困惑**する可能性。
- **修正案**:
  - 「税込合計 (集計)」「税込合計 (確定/CSV出力時)」の 2 列を表示する。あるいは Tooltip で「集計値表示中、CSV 出力は確定値」を明示。

### m-impl-5: `AccountsReceivableVerifyDialog` の `defaultInc` フォールバック順序が `invoiceAmount` 優先で操作意図が変わる
- **箇所**: `frontend/components/pages/finance/AccountsReceivableVerifyDialog.tsx:58-61`
- **コード**:
  ```ts
  const defaultInc = row.invoiceAmount ?? row.taxIncludedAmount ?? row.taxIncludedAmountChange ?? 0
  ```
- **問題**:
  - 「請求書金額があればそれを初期値」のコメント通りだが、**請求書金額==null かつ既に手動確定済みの行を再オープンすると、`taxIncludedAmount` (前回の手動値) が表示**される。意図は OK だが、UI ラベル「集計金額」「請求書金額」「確定金額」が並ぶダイアログで、確定金額入力欄に何の値が入っているのかユーザに分からない。
  - 入力欄の placeholder/helper text に「初期値の出典 (集計値 or 請求書 or 前回確定値)」を表示すべき。

### m-impl-6: `summaryService.findAll(spec)` を `summary()` で全件ロード (設計レビュー m-1) のテスタビリティ
- **箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsReceivableSummaryService.java:84-105`
- **問題**: 設計レビュー m-1 で指摘済の集計クエリ未最適化に加え、**unit test が無い**ため将来の最適化リファクタで挙動退行を検出できない。
- **修正案**: m-1 修正と合わせて `TAccountsReceivableSummaryServiceTest` を新設。

### m-impl-7: `cutoffDateLabel` の `cutoff===0` が「月末」、`cutoff===null` が「-」、`cutoff===-1` が「都度現金」 ? マジックナンバー
- **箇所**: `frontend/components/pages/finance/accounts-receivable.tsx:576-581`
- **問題**:
  - フロントに `0=月末`/`-1=都度現金` のセマンティクスをハードコード。バックエンド `PaymentType` enum (`MONTH_END(0)`, `CASH_ON_DELIVERY(-1)`) との二重定義。
  - 買掛側 (`accounts-payable.tsx`) と挙動同じか要確認。
- **修正案**:
  - `frontend/lib/payment-type.ts` 等に `paymentTypeLabel(cutoff: number | null): string` を集約し、買掛/売掛で共有。

### m-impl-8: `TAccountsReceivableSummary` が `IEntity` を実装していないため AOP `ShopCheck` の対象外
- **箇所**: `backend/src/main/java/jp/co/oda32/domain/model/finance/TAccountsReceivableSummary.java:28`
- **コード**: `public class TAccountsReceivableSummary { ... }` (`implements IEntity` 無し)
- **問題**:
  - `ShopCheckAop#validateFind` は `List<? extends IEntity>` のみフィルタ (`ShopCheckAop.java:53`)。
  - `findByDateRange(LocalDate, LocalDate)` (`TAccountsReceivableSummaryService.java:175-177`) は **shopNo を引数に取らず** AOP も効かないため、もし誰かが新規 Controller でこのメソッドを直接呼ぶと **全店舗のデータが漏出**する。
  - 現状の呼び出し元 (Tasklet, `findByDateRangeAndMfExportEnabled` 経由の admin 限定 export) は問題ないが、ガードレールが弱い。
- **修正案**:
  - `TAccountsReceivableSummary implements IEntity` (`getShopNo()` は既存 getter で互換) を追加。`@SkipShopCheck` を必要な箇所に明示。
  - もしくは `findByDateRange` を `findByShopNoAndDateRange(Integer shopNo, ...)` のオーバーロードに置換し、引数 null で呼ぶ際の挙動を明文化。

---

## 設計レビューと重複しない追加観点

### A-1. `verify` API 経由で `verifiedManually` をフラグ立てるが解除 API は別 (releaseManualLock)
- 設計レビュー D-2 で「解除 API の挙動不足」は指摘済。実装上の追加問題として、`verify(...)` (PUT) は `verifiedManually=true` を一律 set だが、`updateMfExport(true)` (PATCH) でも内部で `verifiedManually=true` を set する (`TAccountsReceivableSummaryService.java:253-258`)。
- 一方で `updateMfExport(false)` は `verifiedManually` を触らない (M-4 で指摘済) ため、**ON→OFF→ON で verifiedManually が `true` のまま固定化**。`releaseManualLock` を別ボタンで叩かないと解除できない。
- フロント (`AccountsReceivableVerifyDialog.tsx:164-174`) は `verifiedManually=true` のときだけ「手動確定解除」ボタンを表示。ON→OFF→ON フローで「OFF にしたつもりが解除ボタンが消えない」UX 問題。

### A-2. `aggregate` API のレスポンス body が `Map.of("status", "STARTED", ...)` で型なし
- **箇所**: `AccountsReceivableController.java:162-166`
- 同 Controller 内で `AccountsReceivableBulkVerifyResponse` のような専用 DTO を定義しているのに、aggregate だけ `Map<String, Object>`。フロント側 (`accounts-receivable.tsx:181-183`) は `AggregateResponse` 型を期待 (`status / targetDate / cutoffType`) しており、バックエンドのキー追加・削除に追従しない。

### A-3. `aggregate` の非同期 submit で `batchExecutor.submit` の Future を破棄
- **箇所**: `AccountsReceivableController.java:153-160`
- ジョブ起動エラーは `log.error` のみ通知。フロント側にも 200 系で「STARTED」を返すため、起動失敗のユーザ通知パスが無い。
- 設計レビュー D-1 (Reconciler の暗黙起動) と同じ「裏で何が起きたか分からない UX」問題。

### A-4. `bulkVerify` の `assertShopAccess` 相当チェック欠落
- **箇所**: `AccountsReceivableController.java:171-209`
- `verify`/`releaseManualLock`/`toggleMfExport` は `assertShopAccess(shopNo)` を呼ぶが、`bulkVerify` は呼ばない。`@PreAuthorize("hasRole('ADMIN')")` で守られているため admin 限定だが、admin が `request.shopNo=null` を投げると `effectiveShopNo=null` (admin の `loginShopNo=0` ルート) → 全店舗対象に reconcile + verify が走る。意図的かもしれないが、設計書 §9.3 に明記なし。

### A-5. `releaseManualLock` API のレスポンスが古い `verificationDifference`/`invoiceAmount` を含む
- **箇所**: `TAccountsReceivableSummaryService.java:226-235`
- `verifiedManually=false` を更新するだけで、`verificationDifference` 等は据え置き。直後に `AccountsReceivableResponse.from(...)` で旧値を返却。**「手動確定解除しても画面の差額表示が変わらない」UX**。設計レビュー D-2 と関連。

### A-6. Reconciler の `monthsInRange` が `from > to` を防御していない
- **箇所**: `AccountsReceivableCutoffReconciler.java:429-438`
- ```java
  while (!cur.isAfter(end)) {
      out.add(cur);
      cur = cur.plusMonths(1);
  }
  ```
- `from > to` のリクエストが来た場合、while ループは初回で抜けて空 list を返すため無限ループは無いが、Controller 側 (`bulkVerify`) で `fromDate > toDate` の検証も無い (`AccountsReceivableController.java:171-209`)。

### A-7. `getByPK` の `taxRate.setScale(2, HALF_UP)` 強制
- **箇所**: `TAccountsReceivableSummaryService.java:165-170`
- DB の `tax_rate NUMERIC(5,2)` に合わせて scale 2 に強制。**フロントから `8` (整数) を渡しても `8.00` に正規化されて検索される**ため動く。が、scale 1 の `8.0` も同様の正規化が必要で、引数が scale 0/1/2/3 のどれが来ても正常に動くかの **テストが無い**。

### A-8. `accounts-receivable.tsx` の `verifyMutation.mutate` が PathVariable に `transactionMonth` を URL エンコード無しで埋め込む
- **箇所**: `frontend/types/accounts-receivable.ts:117`, `frontend/components/pages/finance/accounts-receivable.tsx:138`
- `transactionMonth` は `yyyy-MM-dd` 形式なので URL 安全だが、もし将来 `transactionMonth` の形式が `yyyy/MM/dd` に変わったり JSON 文字列化で何か混入したらインジェクションリスク。
- 修正案: `encodeURIComponent` でラップ。

---

## レビューチェックリスト結果 (コード固有)

| # | 観点 | 結果 | 備考 |
|---|------|------|------|
| 1 | DTO と Entity 分離 | OK | 設計レビューでも OK |
| 2 | Bean Validation | △ | A-2 (aggregate response 型なし)、m-impl-1 (mfExportEnabled 二重 fallback) |
| 3 | `@Transactional` 境界 | NG | 設計レビュー M-2 と同じ。本レビュー追加は無し |
| 4 | N+1 クエリ | △ | 設計レビュー M-1, M-7 と同じ。M-impl-4 で 3×N 追加発見 |
| 5 | エンコーディング | NG | C-impl-3 (FileWriter platform default) |
| 6 | エラー伝播 | NG | M-impl-5 (Tasklet 二重 catch)、A-3 (非同期 submit エラー黙殺) |
| 7 | 認可 | △ | A-4 (bulkVerify shop check 無し)、m-impl-8 (IEntity 未実装) |
| 8 | 命名 / マジック | NG | M-impl-2 (cutoffType 文字列リテラル)、m-impl-7 (cutoff 0/-1 ハードコード) |
| 9 | データ整合性 | NG | C-impl-1 (markExported 片側更新)、C-impl-2 (cutoffDate 上書き) |
| 10 | テストカバレッジ | NG | 設計レビュー m-6 と同じ。`AccountsReceivableCutoffReconcilerTest` も未作成 |
| 11 | URL 設計 | △ | M-impl-3 (boolean PathVariable)、A-8 (encode 漏れ) |
| 12 | UI/UX 整合性 | △ | m-impl-4 (table total vs CSV)、m-impl-5 (defaultInc 出典)、A-1 (verifiedManually 固定化) |

---

## 対応表

| ID | 重要度 | 推奨対応 |
|---|---|---|
| C-impl-1 | Critical | `markExported` を unconditional 上書きに変更 + `applyMatched` の `setTaxIncludedAmount` を撤去 (二段焼き解消) |
| C-impl-2 | Critical | Reconciler の cash-on-delivery 除外 or `cutoffDate` 引き継ぎ |
| C-impl-3 | Critical | `FileWriter` → `OutputStreamWriter(CP932)` |
| M-impl-1 | Major | `BatchJobLauncherService` 新設、Controller 薄化 |
| M-impl-2 | Major | `CutoffType` enum 化 |
| M-impl-3 | Major | `boolean` PathVariable をクエリパラメータ化 (買掛側と統一) |
| M-impl-4 | Major | `findInvoiceForPartner` の優先順序を master `cutoff_date` 起点に変更 |
| M-impl-5 | Major | tasklet の二重例外ハンドリング撤去 |
| m-impl-1 | Minor | DTO field initializer 撤去 |
| m-impl-2 | Minor | `taxRate.toFixed(2)` |
| m-impl-3 | Minor | `defaultDateRange` の `< 20` を `<= 20` に変更 |
| m-impl-4 | Minor | テーブル合計の出典明示 |
| m-impl-5 | Minor | Verify ダイアログの初期値出典表示 |
| m-impl-6 | Minor | Reconciler / Service unit test 追加 |
| m-impl-7 | Minor | `paymentTypeLabel` ユーティリティ集約 |
| m-impl-8 | Minor | `IEntity` 実装 or `findByDateRange` から shopNo 必須化 |
| A-1 | Minor | `updateMfExport(false)` で `verifiedManually=false` 引き戻し |
| A-2 | Minor | `aggregate` レスポンスを DTO 化 |
| A-3 | Minor | submit エラーを SSE / 通知 channel で伝達 |
| A-4 | Minor | `bulkVerify` で admin 全店舗対象を明示確認 (UI ガードまたは `shopNo` 必須) |
| A-5 | Minor | `releaseManualLock` で関連 verification フィールドをリセット |
| A-6 | Minor | `bulkVerify` で `fromDate > toDate` を 400 で弾く |
| A-7 | Minor | scale 異常系の test 追加 |
| A-8 | Minor | `encodeURIComponent` でラップ |

**Approval status**: **Needs Revision** → Critical 3 件 + 設計レビュー C-1/C-2/M-2/M-3/M-8 の修正で **Approved 想定**

---

## 総括

設計レビューで未検出だったコード固有バグとして、**markExported の片側更新 (C-impl-1)** と **Reconciler の cutoffDate 黙示的上書き (C-impl-2)** を発見。前者は MF 連携の金額不整合に直結する経年バグ、後者はマスタとの定義乖離で UI 表示と実データの一貫性を崩す。

**バッチ vs Controller のエンコーディング不整合 (C-impl-3)** は本番環境次第で MF 取込が文字化けする恐れがあり、Linux 本番環境であれば即時修正必須。

Major では Spring 観点 (Controller の薄化 M-impl-1、boolean PathVariable M-impl-3、tasklet の例外ハンドリング M-impl-5) と Reconciler の検索順序 (M-impl-4) が技術負債として残る。Minor 8 件は段階対応可能。

設計レビュー C-1 / C-2 / M-2 / M-3 / M-8 + 本レビュー C-impl-1 / C-impl-2 / C-impl-3 の **計 8 件** が merge 前修正必須。
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
`rg` が使えない環境なので PowerShell のファイル列挙で続けます。既存レビューはかなり具体的な実装バグまで押さえているため、今回は「台帳として説明可能か」「権威データの境界が壊れていないか」に寄せて見ます。
2026-05-04T01:54:35.044138Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path backend -Recurse -File | Where-Object { $_.Name -match '"'AccountsReceivable|TAccountsReceivable|Invoice|Partner|Payable|FinanceController|SalesJournal|SmilePayment' } | Select-Object -ExpandProperty FullName"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path backend -Recurse -File | Where-Object { $_.Name -match '"'AccountsReceivable|TAccountsReceivable|Invoice|Partner|Payable|FinanceController|SalesJournal|SmilePayment' } | Select-Object -ExpandProperty FullName" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path backend -Recurse -File | Where-Object { $_.Name -match '"'AccountsReceivable|TAccountsReceivable|Invoice|Partner|Payable|FinanceController|SalesJournal|SmilePayment' } | Select-Object -ExpandProperty FullName"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path frontend -Recurse -File | Where-Object { $_.Name -match '"'accounts-receivable|AccountsReceivable|accounts-payable|BulkVerify|VerifyDialog|invoice|partner' } | Select-Object -ExpandProperty FullName" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path backend/src/main/resources -Recurse -File | Where-Object { $_.Name -match '"'receivable|invoice|partner|payable|summary|mf|sales' } | Select-Object -ExpandProperty FullName" in C:\project\odamitsu-data-hub
2026-05-04T01:54:35.049220Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path frontend -Recurse -File | Where-Object { $_.Name -match '"'accounts-receivable|AccountsReceivable|accounts-payable|BulkVerify|VerifyDialog|invoice|partner' } | Select-Object -ExpandProperty FullName"` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path frontend -Recurse -File | Where-Object { $_.Name -match '"'accounts-receivable|AccountsReceivable|accounts-payable|BulkVerify|VerifyDialog|invoice|partner' } | Select-Object -ExpandProperty FullName"` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path backend/src/main/resources -Recurse -File | Where-Object { $_.Name -match '"'receivable|invoice|partner|payable|summary|mf|sales' } | Select-Object -ExpandProperty FullName"` rejected: blocked by policy
2026-05-04T01:54:35.049871Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path backend/src/main/resources -Recurse -File | Where-Object { $_.Name -match '"'receivable|invoice|partner|payable|summary|mf|sales' } | Select-Object -ExpandProperty FullName"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem backend -Recurse -File -Filter *AccountsReceivable* | Select-Object -ExpandProperty FullName' in C:\project\odamitsu-data-hub
 succeeded in 1720ms:
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\api\finance\AccountsReceivableController.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\batch\finance\AccountsReceivableToSalesJournalTasklet.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\batch\finance\TAccountsReceivableSummaryTasklet.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\batch\finance\TAccountsReceivableSummaryTasklet$1.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\batch\finance\TAccountsReceivableSummaryTasklet$PartnerBillingInfo.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\batch\finance\TAccountsReceivableSummaryTasklet$ShopInvoiceKey.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\batch\finance\TAccountsReceivableSummaryTasklet$SummaryKey.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\batch\finance\config\AccountsReceivableSummaryConfig.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\batch\finance\service\AccountsReceivableCutoffReconciler.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\batch\finance\service\AccountsReceivableCutoffReconciler$1.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\batch\finance\service\AccountsReceivableCutoffReconciler$AggregationKey.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\batch\finance\service\AccountsReceivableCutoffReconciler$ClosingDateInfo.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\batch\finance\service\AccountsReceivableCutoffReconciler$PartnerIndex.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\batch\finance\service\AccountsReceivableCutoffReconciler$PartnerKey.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\batch\finance\service\AccountsReceivableCutoffReconciler$PartnerResolution.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\batch\finance\service\AccountsReceivableCutoffReconciler$ReconcileResult.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\batch\finance\service\AccountsReceivableCutoffReconciler$ReconcileResult$ReconcileResultBuilder.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\constant\SmileAccountsReceivableType.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\domain\model\embeddable\TAccountsReceivableSummaryPK.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\domain\model\embeddable\TAccountsReceivableSummaryPK$TAccountsReceivableSummaryPKBuilder.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\domain\model\finance\TAccountsReceivableSummary.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\domain\model\finance\TAccountsReceivableSummary$TAccountsReceivableSummaryBuilder.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\domain\repository\finance\TAccountsReceivableSummaryRepository.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\domain\service\finance\TAccountsReceivableSummaryService.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\dto\finance\AccountsReceivableAggregateRequest.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\dto\finance\AccountsReceivableBulkVerifyRequest.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\dto\finance\AccountsReceivableBulkVerifyResponse.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\dto\finance\AccountsReceivableBulkVerifyResponse$AccountsReceivableBulkVerifyResponseBuilder.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\dto\finance\AccountsReceivableResponse.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\dto\finance\AccountsReceivableResponse$AccountsReceivableResponseBuilder.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\dto\finance\AccountsReceivableSummaryResponse.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\dto\finance\AccountsReceivableSummaryResponse$AccountsReceivableSummaryResponseBuilder.class
C:\project\odamitsu-data-hub\backend\build\classes\java\main\jp\co\oda32\dto\finance\AccountsReceivableVerifyRequest.class
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\finance\AccountsReceivableController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\AccountsReceivableToSalesJournalTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\TAccountsReceivableSummaryTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\config\AccountsReceivableSummaryConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\service\AccountsReceivableCutoffReconciler.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\SmileAccountsReceivableType.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\TAccountsReceivableSummaryPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsReceivableSummary.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance\TAccountsReceivableSummaryRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsReceivableSummaryService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\AccountsReceivableAggregateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\AccountsReceivableBulkVerifyRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\AccountsReceivableBulkVerifyResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\AccountsReceivableResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\AccountsReceivableSummaryResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\AccountsReceivableVerifyRequest.java
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/TAccountsReceivableSummary.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/repository/finance/TAccountsReceivableSummaryRepository.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsReceivableSummaryService.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java' in C:\project\odamitsu-data-hub
 succeeded in 2079ms:
package jp.co.oda32.api.finance;

import jakarta.validation.Valid;
import jp.co.oda32.batch.finance.model.InvoiceVerificationSummary;
import jp.co.oda32.batch.finance.service.AccountsReceivableCutoffReconciler;
import jp.co.oda32.batch.finance.service.InvoiceVerifier;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.service.finance.SalesJournalCsvService;
import jp.co.oda32.domain.service.finance.TAccountsReceivableSummaryService;
import jp.co.oda32.domain.service.master.MPartnerService;
import jp.co.oda32.domain.service.util.LoginUserUtil;
import jp.co.oda32.dto.finance.AccountsReceivableAggregateRequest;
import jp.co.oda32.dto.finance.AccountsReceivableBulkVerifyRequest;
import jp.co.oda32.dto.finance.AccountsReceivableBulkVerifyResponse;
import jp.co.oda32.dto.finance.AccountsReceivableResponse;
import jp.co.oda32.dto.finance.AccountsReceivableSummaryResponse;
import jp.co.oda32.dto.finance.AccountsReceivableVerifyRequest;
import jp.co.oda32.dto.finance.MfExportToggleRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 売掛金一覧画面のAPIコントローラ。
 * <p>
 * 設計: {@code claudedocs/design-accounts-receivable-mf.md}
 * 買掛側 {@link FinanceController} の {@code /accounts-payable} 系と対称構造。
 *
 * @since 2026/04/17
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/finance/accounts-receivable")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class AccountsReceivableController {

    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Charset CP932 = Charset.forName("windows-31j");

    private final TAccountsReceivableSummaryService summaryService;
    private final MPartnerService mPartnerService;
    private final InvoiceVerifier invoiceVerifier;
    private final AccountsReceivableCutoffReconciler cutoffReconciler;
    private final SalesJournalCsvService salesJournalCsvService;
    private final JobLauncher jobLauncher;
    private final ApplicationContext applicationContext;

    @Qualifier("batchTaskExecutor")
    private final ThreadPoolTaskExecutor batchExecutor;

    // -------- 一覧 --------

    @GetMapping
    public ResponseEntity<Page<AccountsReceivableResponse>> list(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) Integer partnerNo,
            @RequestParam(required = false) String partnerCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String verificationFilter,
            @PageableDefault(size = 50, sort = "transactionMonth", direction = Sort.Direction.DESC) Pageable pageable) {
        Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(shopNo);
        Page<TAccountsReceivableSummary> page = summaryService.findPaged(
                effectiveShopNo, partnerNo, partnerCode, fromDate, toDate, verificationFilter, pageable);

        Set<Integer> partnerNos = page.getContent().stream()
                .map(TAccountsReceivableSummary::getPartnerNo)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, MPartner> partnerMap = partnerNos.stream()
                .map(mPartnerService::getByPartnerNo)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(MPartner::getPartnerNo, p -> p, (a, b) -> a));

        return ResponseEntity.ok(page.map(ar -> AccountsReceivableResponse.from(ar, partnerMap.get(ar.getPartnerNo()))));
    }

    @GetMapping("/summary")
    public ResponseEntity<AccountsReceivableSummaryResponse> summary(
            @RequestParam(required = false) Integer shopNo,
            @RequestParam(required = false) String partnerCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(shopNo);
        return ResponseEntity.ok(summaryService.summary(effectiveShopNo, partnerCode, fromDate, toDate));
    }

    // -------- 再集計 --------

    @PostMapping("/aggregate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> aggregate(
            @Valid @RequestBody AccountsReceivableAggregateRequest request) {
        try {
            LocalDate.parse(request.getTargetDate(), YYYY_MM_DD);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "targetDate は yyyyMMdd 形式で指定してください"));
        }
        String cutoffType = request.getCutoffType();
        if (cutoffType == null || cutoffType.isBlank()) {
            cutoffType = "all";
        }
        if (!Set.of("all", "15", "20", "month_end").contains(cutoffType)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "cutoffType は all / 15 / 20 / month_end のいずれかを指定してください"));
        }

        String beanName = "accountsReceivableSummaryJob";
        if (!applicationContext.containsBean(beanName)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "集計ジョブが見つかりません"));
        }
        Job job = (Job) applicationContext.getBean(beanName);

        JobParametersBuilder params = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("targetDate", request.getTargetDate())
                .addString("cutoffType", cutoffType);

        var jobParams = params.toJobParameters();
        final String cutoffTypeCapture = cutoffType;
        batchExecutor.submit(() -> {
            try {
                jobLauncher.run(job, jobParams);
            } catch (Exception e) {
                log.error("売掛金集計ジョブの非同期実行に失敗: targetDate={}, cutoffType={}",
                        request.getTargetDate(), cutoffTypeCapture, e);
            }
        });

        return ResponseEntity.accepted()
                .body(Map.of(
                        "status", "STARTED",
                        "targetDate", request.getTargetDate(),
                        "cutoffType", cutoffType));
    }

    // -------- 一括検証 --------

    @PostMapping("/bulk-verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountsReceivableBulkVerifyResponse> bulkVerify(
            @Valid @RequestBody AccountsReceivableBulkVerifyRequest request) {
        Integer effectiveShopNo = LoginUserUtil.resolveEffectiveShopNo(request.getShopNo());
        List<TAccountsReceivableSummary> summaries = summaryService
                .findByShopAndDateRange(effectiveShopNo, request.getFromDate(), request.getToDate());

        // 事前パス: 請求書の closing_date と AR の transaction_month が食い違う得意先を
        // 自動で再集計する（マスタ cutoff_date の更新が追いついていないケースへの自動追従）。
        AccountsReceivableCutoffReconciler.ReconcileResult reconcile =
                cutoffReconciler.reconcile(summaries, request.getFromDate(), request.getToDate());
        if (reconcile.getReconciledPartners() > 0) {
            // 再集計で AR が変わっているので再ロード
            summaries = summaryService
                    .findByShopAndDateRange(effectiveShopNo, request.getFromDate(), request.getToDate());
            log.info("bulkVerify: 自動再集計 partners={} inserted={} deleted={}",
                    reconcile.getReconciledPartners(), reconcile.getInsertedRows(), reconcile.getDeletedRows());
        }

        InvoiceVerificationSummary result = invoiceVerifier.verify(summaries, request.getToDate());

        // 1 tx で saveAll し、途中例外で部分コミットにならないようにする (B-2)
        summaryService.saveAll(summaries);

        return ResponseEntity.ok(AccountsReceivableBulkVerifyResponse.builder()
                .matchedCount(result.getMatchedCount())
                .mismatchCount(result.getMismatchCount())
                .notFoundCount(result.getNotFoundCount())
                .skippedManualCount(result.getSkippedManualCount())
                .josamaOverwriteCount(result.getJosamaOverwriteCount())
                .quarterlySpecialCount(result.getQuarterlySpecialCount())
                .reconciledPartners(reconcile.getReconciledPartners())
                .reconciledDeletedRows(reconcile.getDeletedRows())
                .reconciledInsertedRows(reconcile.getInsertedRows())
                .reconciledSkippedManualPartners(reconcile.getSkippedManualPartners())
                .reconciledDetails(reconcile.getReconciledDetails())
                .build());
    }

    // -------- 手動確定 --------

    @PutMapping("/{shopNo}/{partnerNo}/{transactionMonth}/{taxRate}/{isOtakeGarbageBag}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountsReceivableResponse> verify(
            @PathVariable Integer shopNo,
            @PathVariable Integer partnerNo,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth,
            @PathVariable BigDecimal taxRate,
            @PathVariable boolean isOtakeGarbageBag,
            @Valid @RequestBody AccountsReceivableVerifyRequest request) {
        assertShopAccess(shopNo);
        TAccountsReceivableSummary updated = summaryService.verify(
                shopNo, partnerNo, transactionMonth, taxRate, isOtakeGarbageBag,
                request.getTaxIncludedAmount(), request.getTaxExcludedAmount(),
                request.getNote(),
                request.getMfExportEnabled() != null ? request.getMfExportEnabled() : true);
        MPartner partner = mPartnerService.getByPartnerNo(partnerNo);
        return ResponseEntity.ok(AccountsReceivableResponse.from(updated, partner));
    }

    @DeleteMapping("/{shopNo}/{partnerNo}/{transactionMonth}/{taxRate}/{isOtakeGarbageBag}/manual-lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountsReceivableResponse> releaseManualLock(
            @PathVariable Integer shopNo,
            @PathVariable Integer partnerNo,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth,
            @PathVariable BigDecimal taxRate,
            @PathVariable boolean isOtakeGarbageBag) {
        assertShopAccess(shopNo);
        TAccountsReceivableSummary updated = summaryService.releaseManualLock(
                shopNo, partnerNo, transactionMonth, taxRate, isOtakeGarbageBag);
        MPartner partner = mPartnerService.getByPartnerNo(partnerNo);
        return ResponseEntity.ok(AccountsReceivableResponse.from(updated, partner));
    }

    @PatchMapping("/{shopNo}/{partnerNo}/{transactionMonth}/{taxRate}/{isOtakeGarbageBag}/mf-export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountsReceivableResponse> toggleMfExport(
            @PathVariable Integer shopNo,
            @PathVariable Integer partnerNo,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionMonth,
            @PathVariable BigDecimal taxRate,
            @PathVariable boolean isOtakeGarbageBag,
            @Valid @RequestBody MfExportToggleRequest request) {
        assertShopAccess(shopNo);
        TAccountsReceivableSummary updated = summaryService.updateMfExport(
                shopNo, partnerNo, transactionMonth, taxRate, isOtakeGarbageBag, request.getEnabled());
        MPartner partner = mPartnerService.getByPartnerNo(partnerNo);
        return ResponseEntity.ok(AccountsReceivableResponse.from(updated, partner));
    }

    // -------- 検証済みCSV DL --------

    @GetMapping("/export-mf-csv")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InputStreamResource> exportMfCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long initialTransactionNo) throws Exception {
        if (fromDate == null || toDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate と toDate は必須です");
        }
        if (fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate は toDate 以前を指定してください");
        }
        List<TAccountsReceivableSummary> summaries = summaryService
                .findByDateRangeAndMfExportEnabled(fromDate, toDate, true);
        log.info("売掛金→売上仕訳CSV DL: fromDate={}, toDate={}, 件数={}", fromDate, toDate, summaries.size());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStreamWriter w = new OutputStreamWriter(baos, CP932)) {
            salesJournalCsvService.writeCsv(summaries, w, initialTransactionNo);
        }

        // CSV出力済みマーカーを付ける（買掛と同じ仕様）
        salesJournalCsvService.markExported(summaries);
        for (TAccountsReceivableSummary s : summaries) {
            summaryService.save(s);
        }

        String filename = generateFileName(fromDate, toDate);
        byte[] bytes = baos.toByteArray();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("text/csv; charset=Shift_JIS"))
                .contentLength(bytes.length)
                .body(new InputStreamResource(new ByteArrayInputStream(bytes)));
    }

    private String generateFileName(LocalDate from, LocalDate to) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyyMMdd");
        if (from.equals(to)) {
            return "accounts_receivable_to_sales_journal_" + from.format(f) + ".csv";
        }
        return "accounts_receivable_to_sales_journal_" + from.format(f) + "_" + to.format(f) + ".csv";
    }

    private void assertShopAccess(Integer shopNo) {
        Integer effective = LoginUserUtil.resolveEffectiveShopNo(shopNo);
        if (effective != null && !effective.equals(shopNo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "他ショップのデータにはアクセスできません");
        }
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2152ms:
package jp.co.oda32.domain.service.finance;

import jakarta.persistence.criteria.Predicate;
import jp.co.oda32.annotation.SkipShopCheck;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.domain.repository.finance.TAccountsReceivableSummaryRepository;
import jp.co.oda32.dto.finance.AccountsReceivableSummaryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 20日締め売掛金テーブルのサービスクラス
 *
 * @author k_oda
 * @since 2024/08/31
 * @modified 2025/04/30 - 指定年月の締め月データを取得するメソッドを追加
 * @modified 2026/04/17 - ページング・検証フィルタ・手動確定/解除/MF出力切替を追加（設計書 design-accounts-receivable-mf.md）
 */
@Service
public class TAccountsReceivableSummaryService {

    private final TAccountsReceivableSummaryRepository repository;

    @Autowired
    public TAccountsReceivableSummaryService(TAccountsReceivableSummaryRepository repository) {
        this.repository = repository;
    }

    public List<TAccountsReceivableSummary> findAll() {
        return repository.findAll();
    }

    public TAccountsReceivableSummary save(TAccountsReceivableSummary summary) {
        return repository.save(summary);
    }

    /**
     * 複数の AR サマリを 1 トランザクションで一括保存する。
     * {@code bulkVerify} の最終保存で使用し、途中例外時の部分コミット破綻を防ぐ。
     */
    @Transactional
    public List<TAccountsReceivableSummary> saveAll(List<TAccountsReceivableSummary> summaries) {
        return repository.saveAll(summaries);
    }

    /**
     * ページング検索。画面一覧のフィルタで使用。
     *
     * @param shopNo             店舗番号（nullなら全店舗）
     * @param partnerNo          得意先番号（nullならすべて）
     * @param fromDate           取引月開始（nullなら制限なし）
     * @param toDate             取引月終了（nullなら制限なし）
     * @param verificationFilter "all" / "unverified" / "unmatched" / "matched"
     * @param pageable           ページ指定
     */
    @SkipShopCheck
    public Page<TAccountsReceivableSummary> findPaged(
            Integer shopNo,
            Integer partnerNo,
            String partnerCode,
            LocalDate fromDate,
            LocalDate toDate,
            String verificationFilter,
            Pageable pageable) {
        Specification<TAccountsReceivableSummary> spec = buildSpec(shopNo, partnerNo, partnerCode, fromDate, toDate, verificationFilter);
        return repository.findAll(spec, pageable);
    }

    /**
     * 画面表示用のサマリカウント（未検証/不一致/一致 件数と差額合計）。
     */
    @SkipShopCheck
    public AccountsReceivableSummaryResponse summary(Integer shopNo, String partnerCode, LocalDate fromDate, LocalDate toDate) {
        Specification<TAccountsReceivableSummary> spec = buildSpec(shopNo, null, partnerCode, fromDate, toDate, "all");
        List<TAccountsReceivableSummary> list = repository.findAll(spec);
        long total = list.size();
        long unverified = list.stream().filter(s -> s.getVerificationResult() == null).count();
        long unmatched = list.stream().filter(s -> s.getVerificationResult() != null && s.getVerificationResult() == 0).count();
        long matched = list.stream().filter(s -> s.getVerificationResult() != null && s.getVerificationResult() == 1).count();
        BigDecimal diffSum = list.stream()
                .filter(s -> s.getVerificationResult() != null && s.getVerificationResult() == 0)
                .map(s -> s.getVerificationDifference() != null ? s.getVerificationDifference() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return AccountsReceivableSummaryResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .totalCount(total)
                .unverifiedCount(unverified)
                .unmatchedCount(unmatched)
                .matchedCount(matched)
                .unmatchedDifferenceSum(diffSum)
                .build();
    }

    private Specification<TAccountsReceivableSummary> buildSpec(
            Integer shopNo, Integer partnerNo, String partnerCode,
            LocalDate fromDate, LocalDate toDate,
            String verificationFilter) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (shopNo != null) {
                preds.add(cb.equal(root.get("shopNo"), shopNo));
            }
            if (partnerNo != null) {
                preds.add(cb.equal(root.get("partnerNo"), partnerNo));
            }
            if (partnerCode != null && !partnerCode.isBlank()) {
                // 部分一致でも絞り込める方が使いやすい (前方一致)。
                preds.add(cb.like(root.get("partnerCode"), partnerCode.trim() + "%"));
            }
            if (fromDate != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("transactionMonth"), fromDate));
            }
            if (toDate != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("transactionMonth"), toDate));
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

    /**
     * 指定された取引月に対応するTAccountsReceivableSummaryのリストを取得します。
     */
    public List<TAccountsReceivableSummary> findByTransactionMonth(LocalDate transactionMonth) {
        return repository.findByTransactionMonth(transactionMonth);
    }

    /**
     * 指定された年月で締め月が該当する売掛金サマリーを取得します。
     */
    public List<TAccountsReceivableSummary> findByCutoffYearMonth(YearMonth yearMonth) {
        LocalDate endOfMonth = yearMonth.atEndOfMonth();
        LocalDate day15 = yearMonth.atDay(15);
        LocalDate day20 = yearMonth.atDay(20);
        return repository.findByTransactionMonthIn(Arrays.asList(day15, day20, endOfMonth));
    }

    public TAccountsReceivableSummary getByPK(int shopNo, int partnerNo, LocalDate transactionMonth,
                                              BigDecimal taxRate, boolean isOtakeGarbageBag) {
        BigDecimal rate = taxRate != null ? taxRate.setScale(2, RoundingMode.HALF_UP) : null;
        return repository.getByShopNoAndPartnerNoAndTransactionMonthAndTaxRateAndIsOtakeGarbageBag(
                shopNo, partnerNo, transactionMonth, rate, isOtakeGarbageBag);
    }

    /**
     * 指定された日付範囲内の売掛金サマリーを取得します。
     */
    public List<TAccountsReceivableSummary> findByDateRange(LocalDate fromDate, LocalDate toDate) {
        return repository.findByTransactionMonthBetween(fromDate, toDate);
    }

    /**
     * 指定された日付範囲内かつ MF出力可否フラグが一致する売掛金サマリーを取得します。
     * 検証済みCSV出力で使用。
     */
    public List<TAccountsReceivableSummary> findByDateRangeAndMfExportEnabled(
            LocalDate fromDate, LocalDate toDate, boolean mfExportEnabled) {
        return repository.findByTransactionMonthBetweenAndMfExportEnabled(fromDate, toDate, mfExportEnabled);
    }

    /**
     * 店舗＋期間で検索（一括検証用）。
     */
    public List<TAccountsReceivableSummary> findByShopAndDateRange(
            Integer shopNo, LocalDate fromDate, LocalDate toDate) {
        if (shopNo == null) {
            return repository.findByTransactionMonthBetween(fromDate, toDate);
        }
        return repository.findByShopNoAndTransactionMonthBetween(shopNo, fromDate, toDate);
    }

    /**
     * 手動で売掛金額を確定します。
     * verified_manually=true をセットし、次回再集計・再検証バッチで上書きされないようにします。
     */
    @Transactional
    public TAccountsReceivableSummary verify(
            int shopNo, int partnerNo, LocalDate transactionMonth, BigDecimal taxRate, boolean isOtakeGarbageBag,
            BigDecimal taxIncludedAmount, BigDecimal taxExcludedAmount, String note, boolean mfExportEnabled) {
        TAccountsReceivableSummary summary = getByPK(shopNo, partnerNo, transactionMonth, taxRate, isOtakeGarbageBag);
        if (summary == null) {
            throw new IllegalArgumentException("対象の売掛金集計が見つかりません");
        }
        // 手動確定値を *_amount に確定。*_change は集計結果のまま保持。
        summary.setTaxIncludedAmount(taxIncludedAmount);
        summary.setTaxExcludedAmount(taxExcludedAmount);
        summary.setVerifiedManually(Boolean.TRUE);
        summary.setVerificationNote(note);
        // 手動確定は「一致」扱い
        summary.setVerificationResult(1);
        summary.setMfExportEnabled(mfExportEnabled);
        return save(summary);
    }

    /**
     * 手動確定を解除します。次回再検証バッチで上書きされるようになります。
     */
    @Transactional
    public TAccountsReceivableSummary releaseManualLock(
            int shopNo, int partnerNo, LocalDate transactionMonth,
            BigDecimal taxRate, boolean isOtakeGarbageBag) {
        TAccountsReceivableSummary summary = getByPK(shopNo, partnerNo, transactionMonth, taxRate, isOtakeGarbageBag);
        if (summary == null) {
            throw new IllegalArgumentException("対象の売掛金集計が見つかりません");
        }
        summary.setVerifiedManually(Boolean.FALSE);
        return repository.save(summary);
    }

    /**
     * MF出力フラグを更新します。
     */
    @Transactional
    public TAccountsReceivableSummary updateMfExport(
            int shopNo, int partnerNo, LocalDate transactionMonth,
            BigDecimal taxRate, boolean isOtakeGarbageBag, boolean enabled) {
        TAccountsReceivableSummary summary = getByPK(shopNo, partnerNo, transactionMonth, taxRate, isOtakeGarbageBag);
        if (summary == null) {
            throw new IllegalArgumentException("対象の売掛金集計が見つかりません");
        }
        summary.setMfExportEnabled(enabled);
        // MF 出力対象に "する" 意思表示は「運用者がこの行を CSV に含めると確定した」ことを意味する。
        // バッチの一括検証が applyMismatch/applyNotFound で mf_export_enabled を false に戻すのを防ぐため、
        // verified_manually=true を立てておく (既に true なら触らない)。
        // 例) 301491 クリーンラボのように隔月請求で一致判定できないが CSV には含めたい行の運用対応。
        if (enabled && !Boolean.TRUE.equals(summary.getVerifiedManually())) {
            summary.setVerifiedManually(Boolean.TRUE);
            if (summary.getVerificationResult() == null || summary.getVerificationResult() != 1) {
                summary.setVerificationResult(1);
            }
        }
        return repository.save(summary);
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3026ms:
package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.embeddable.TAccountsReceivableSummaryPK;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 20日締め売掛金テーブルのリポジトリクラス
 *
 * @author k_oda
 * @since 2024/08/31
 * @modified 2025/04/30 - 年月に該当する締め日データを取得するメソッドを追加
 * @modified 2026/04/17 - 検証フィルタ対応のため Specification 実行を追加
 */
@Repository
public interface TAccountsReceivableSummaryRepository
        extends JpaRepository<TAccountsReceivableSummary, TAccountsReceivableSummaryPK>,
                JpaSpecificationExecutor<TAccountsReceivableSummary> {
    /**
     * 指定された取引月に対応するTAccountsReceivableSummaryのリストを取得します。
     *
     * @param transactionMonth 取引月
     * @return 取引月に対応するTAccountsReceivableSummaryのリスト
     */
    List<TAccountsReceivableSummary> findByTransactionMonth(LocalDate transactionMonth);

    /**
     * 指定された取引月リストのいずれかに一致する売掛金サマリーを取得します。
     *
     * @param transactionMonths 取引月のリスト
     * @return 売掛金サマリーのリスト
     */
    List<TAccountsReceivableSummary> findByTransactionMonthIn(List<LocalDate> transactionMonths);

    /**
     * 指定された日付リストのいずれかに一致し、金額が変更されている売掛金サマリーを取得します。
     *
     * @param transactionMonths 取引日のリスト
     * @return 金額が変更されている売掛金サマリーのリスト
     */
    List<TAccountsReceivableSummary> findByTransactionMonthInAndTaxIncludedAmountChangeIsNotNull(List<LocalDate> transactionMonths);

    TAccountsReceivableSummary getByShopNoAndPartnerNoAndTransactionMonthAndTaxRateAndIsOtakeGarbageBag(
            int shopNo, int partnerNo, LocalDate transactionMonth, BigDecimal taxRate, boolean isOtakeGarbageBag);

    /**
     * 指定された日付範囲内の売掛金サマリーを取得します。
     *
     * @param fromDate 開始日（この日を含む）
     * @param toDate 終了日（この日を含む）
     * @return 売掛金サマリーのリスト
     */
    List<TAccountsReceivableSummary> findByTransactionMonthBetween(LocalDate fromDate, LocalDate toDate);

    /**
     * 指定された日付範囲内かつ MF出力可否フラグが一致する売掛金サマリーを取得します。
     *
     * @param fromDate        開始日（この日を含む）
     * @param toDate          終了日（この日を含む）
     * @param mfExportEnabled MF出力可否フラグ
     * @return 売掛金サマリーのリスト
     */
    List<TAccountsReceivableSummary> findByTransactionMonthBetweenAndMfExportEnabled(
            LocalDate fromDate, LocalDate toDate, Boolean mfExportEnabled);

    /**
     * 指定された店舗・日付範囲内の売掛金サマリーを取得します。
     */
    List<TAccountsReceivableSummary> findByShopNoAndTransactionMonthBetween(
            Integer shopNo, LocalDate fromDate, LocalDate toDate);
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3972ms:
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

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/batch/finance/TAccountsReceivableSummaryTasklet.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/batch/finance/service/AccountsReceivableCutoffReconciler.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/SalesJournalCsvService.java' in C:\project\odamitsu-data-hub
 succeeded in 1768ms:
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

 succeeded in 1847ms:
package jp.co.oda32.batch.finance.service;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.constant.TaxType;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.domain.model.finance.TInvoice;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.model.order.TOrderDetail;
import jp.co.oda32.domain.repository.finance.TAccountsReceivableSummaryRepository;
import jp.co.oda32.domain.service.finance.TInvoiceService;
import jp.co.oda32.domain.service.master.MPartnerService;
import jp.co.oda32.domain.service.order.TOrderDetailService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 売掛金の締め日を <b>請求書 (t_invoice) の {@code closing_date}</b> に合わせて自動再集計する。
 *
 * <p>目的: マスタ {@code m_partner.cutoff_date} と実際の SMILE 請求書の締め日が食い違う
 * ケース（例: ENEOS は 20 日締めから月末締めに変更済だがマスタ未反映）に自動追従する。
 *
 * <p>挙動:
 * <ol>
 *   <li>対象期間の売掛金サマリを partner_code + shop_no でグルーピング</li>
 *   <li>各得意先の請求書 (closing_date が 末/20/15) を検索</li>
 *   <li>請求書の closing_date に対応する transaction_month と AR 行が一致していなければ再集計対象</li>
 *   <li>旧 AR 行を削除（{@code verified_manually=true} 行は保護）</li>
 *   <li>請求書の締め日に応じた期間で t_order_detail を再集計し、新 AR 行を INSERT</li>
 * </ol>
 *
 * <p>スコープ外（マスタと請求書の整合性を単純に判定できないため従来通り）:
 * <ul>
 *   <li>上様 partner_code = {@code "999999"} / partner_no = {@code -999999}</li>
 *   <li>イズミ四半期特殊 (partner_code {@code "000231"})</li>
 *   <li>クリーンラボ (partner_code {@code "301491"})</li>
 *   <li>都度現金払い (master cutoff_date = -1): 元から月次集約なので再集計不要</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountsReceivableCutoffReconciler {

    /** 大竹市のゴミ袋のgoods_code (tasklet と同一定義)。 */
    private static final Set<String> OTAKE_GARBAGE_BAG_GOODS_CODES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "00100001", "00100003", "00100005",
                    "00100007", "00100009", "00100011"
            ))
    );

    private static final String JOSAMA_PARTNER_CODE = "999999";
    private static final String QUARTERLY_BILLING_PARTNER_CODE = "000231";
    private static final String CLEAN_LAB_PARTNER_CODE = "301491";

    private static final Pattern CLOSING_DATE_PATTERN = Pattern.compile("^(\\d{4})/(\\d{2})/(末|\\d{2})$");

    private final TInvoiceService tInvoiceService;
    private final TOrderDetailService tOrderDetailService;
    private final MPartnerService mPartnerService;
    private final TAccountsReceivableSummaryRepository summaryRepository;

    /**
     * {@code summaries} をスキャンし、各得意先について対応する請求書の締め日を調べ、
     * AR の {@code transaction_month} と食い違っていれば当該得意先の AR 行を再集計する。
     *
     * @param summaries 現在の AR サマリ (検索結果)
     * @param fromDate  検索期間の開始日 (UI で指定された {@code fromDate})
     * @param toDate    検索期間の終了日
     * @return 実行結果サマリ
     */
    @Transactional
    public ReconcileResult reconcile(
            List<TAccountsReceivableSummary> summaries, LocalDate fromDate, LocalDate toDate) {

        // 全 MPartner を 1 回ロードしてインデックス構築
        PartnerIndex index = buildPartnerIndex();

        // (shopNo, partner_code) 単位で集約して走査
        Map<PartnerKey, List<TAccountsReceivableSummary>> byPartner = summaries.stream()
                .filter(s -> !isExcludedPartner(s.getPartnerCode()))
                .collect(Collectors.groupingBy(
                        s -> new PartnerKey(s.getShopNo(), s.getPartnerCode())));

        int reconciledPartners = 0;
        int deletedRows = 0;
        int insertedRows = 0;
        int skippedManualPartners = 0;
        List<String> reconciledPartnerCodes = new ArrayList<>();

        for (Map.Entry<PartnerKey, List<TAccountsReceivableSummary>> entry : byPartner.entrySet()) {
            PartnerKey pk = entry.getKey();
            List<TAccountsReceivableSummary> partnerSummaries = entry.getValue();

            // 対象月ごとに請求書を検索 (fromDate?toDate にまたがる月を列挙)
            for (YearMonth ym : monthsInRange(fromDate, toDate)) {
                Optional<TInvoice> invoiceOpt = findInvoiceForPartner(pk.shopNo, pk.partnerCode, ym);
                if (invoiceOpt.isEmpty()) continue;

                ClosingDateInfo closing = parseClosingDate(invoiceOpt.get().getClosingDate()).orElse(null);
                if (closing == null) continue;

                // Izumi (000231) 四半期特殊: 前月が四半期月 (2/5/8/11) なら集計期間を
                // 「当月 1日?15日」に短縮する（前月 16?末日分は四半期締めで別計上済みのため）。
                boolean izumiShortPeriod = isIzumiQuarterlyShortPeriod(pk.partnerCode, ym);
                if (izumiShortPeriod) {
                    closing = new ClosingDateInfo(
                            closing.transactionMonth,
                            closing.cutoffCode,
                            ym.atDay(1),                // ← 前月16日 ではなく 当月1日
                            closing.periodEnd);
                }

                // この月に請求書がある → AR 側が一致する transaction_month を持っているか確認。
                // Izumi 四半期は transaction_month が同じでも期間が違う (金額が狂う) ため常に再集計する。
                LocalDate expectedTxMonth = closing.transactionMonth;
                if (!izumiShortPeriod) {
                    boolean alreadyMatched = partnerSummaries.stream()
                            .anyMatch(s -> expectedTxMonth.equals(s.getTransactionMonth()));
                    if (alreadyMatched) continue; // 既に正しい締め日で集計されている
                }

                // 旧 AR 行を検出。
                // - 非 Izumi: その月に属する全行 (transaction_month の YearMonth が一致)
                // - Izumi 四半期: 期待 transaction_month と完全一致する行のみ（他月と混ざる可能性は無いがコード上の意図明確化）
                List<TAccountsReceivableSummary> stale = izumiShortPeriod
                        ? partnerSummaries.stream()
                            .filter(s -> expectedTxMonth.equals(s.getTransactionMonth()))
                            .toList()
                        : partnerSummaries.stream()
                            .filter(s -> YearMonth.from(s.getTransactionMonth()).equals(ym))
                            .toList();

                // 手動確定が 1 件でもあれば、運用者の判断を尊重してスキップ
                boolean anyManual = stale.stream()
                        .anyMatch(s -> Boolean.TRUE.equals(s.getVerifiedManually()));
                if (anyManual) {
                    skippedManualPartners++;
                    log.warn("再集計をスキップ (手動確定済み行あり): shop={} partner_code={} month={}",
                            pk.shopNo, pk.partnerCode, ym);
                    continue;
                }

                // 再集計対象の partner_no を解決: billing partner 本人 + 同じ invoice_partner_code を持つ子得意先
                PartnerResolution resolution = resolveBillingPartner(index, pk.shopNo, pk.partnerCode);
                if (resolution == null) {
                    log.warn("billing partner が見つかりません: shop={} partner_code={}", pk.shopNo, pk.partnerCode);
                    continue;
                }

                // 新 AR 集計 (まだ保存しない。差分判定のため)
                List<TAccountsReceivableSummary> newRows = aggregateForPartner(
                        pk.shopNo,
                        resolution,
                        closing,
                        pk.partnerCode);

                // Izumi 四半期: 常に再集計を走らせるため、既存行と同じ金額なら
                // DELETE+INSERT せずスキップ（無駄な書き込み・reconcile カウント回避）。
                // NOTE: 以前は delete を先に実行していたが、sameTotals=true の分岐が後段にあり
                // 「削除のみ実行して insert しない」データ消失バグがあった (B-1)。差分判定→delete→insert の順に統一。
                if (izumiShortPeriod && sameTotals(stale, newRows)) {
                    continue;
                }

                // 旧 AR 削除
                if (!stale.isEmpty()) {
                    summaryRepository.deleteAllInBatch(stale);
                    deletedRows += stale.size();
                }

                if (!newRows.isEmpty()) {
                    summaryRepository.saveAll(newRows);
                    insertedRows += newRows.size();
                }

                reconciledPartners++;
                String label = izumiShortPeriod ? "Izumi四半期(1日?15日)" : closing.asLabel();
                reconciledPartnerCodes.add(pk.partnerCode + " (" + ym + ": " + label + ")");
                log.info("再集計: shop={} partner_code={} month={} 旧{}件→新{}件 (cutoff→{}{})",
                        pk.shopNo, pk.partnerCode, ym,
                        stale.size(), newRows.size(), closing.cutoffCode,
                        izumiShortPeriod ? " [Izumi四半期]" : "");
            }
        }

        return ReconcileResult.builder()
                .reconciledPartners(reconciledPartners)
                .deletedRows(deletedRows)
                .insertedRows(insertedRows)
                .skippedManualPartners(skippedManualPartners)
                .reconciledDetails(reconciledPartnerCodes)
                .build();
    }

    // ===========================================================
    // 請求書→締め日情報
    // ===========================================================

    /**
     * 指定 (shop, partner_code, 年月) の請求書を "YYYY/MM/末", "YYYY/MM/20", "YYYY/MM/15" の順で検索。
     */
    private Optional<TInvoice> findInvoiceForPartner(Integer shopNo, String partnerCode, YearMonth ym) {
        String yyyymm = String.format("%d/%02d", ym.getYear(), ym.getMonthValue());
        String[] candidates = {yyyymm + "/末", yyyymm + "/20", yyyymm + "/15"};
        for (String cd : candidates) {
            Optional<TInvoice> inv = tInvoiceService.findByShopNoAndPartnerCodeAndClosingDate(shopNo, partnerCode, cd);
            if (inv.isPresent()) return inv;
        }
        return Optional.empty();
    }

    /** "2026/03/末" → transactionMonth=2026-03-31, cutoffCode=30, 期間=(2026-03-01, 2026-03-31) */
    static Optional<ClosingDateInfo> parseClosingDate(String closingDate) {
        if (closingDate == null) return Optional.empty();
        Matcher m = CLOSING_DATE_PATTERN.matcher(closingDate);
        if (!m.matches()) return Optional.empty();
        int year = Integer.parseInt(m.group(1));
        int month = Integer.parseInt(m.group(2));
        String dayPart = m.group(3);
        YearMonth ym = YearMonth.of(year, month);
        if ("末".equals(dayPart)) {
            LocalDate end = ym.atEndOfMonth();
            LocalDate start = ym.atDay(1);
            return Optional.of(new ClosingDateInfo(end, 30, start, end));
        }
        int day = Integer.parseInt(dayPart);
        LocalDate end = ym.atDay(day);
        // "前月(day+1)"?"当月 day"
        LocalDate start = ym.minusMonths(1).atDay(day + 1);
        return Optional.of(new ClosingDateInfo(end, day, start, end));
    }

    // ===========================================================
    // Partner 解決
    // ===========================================================

    private PartnerIndex buildPartnerIndex() {
        List<MPartner> all = mPartnerService.findAll();
        Map<Integer, MPartner> byPartnerNo = new HashMap<>();
        // invoice_partner_code (effective) + shop → billing partner_no
        Map<String, Integer> billingPartnerNoByKey = new HashMap<>();
        // invoice_partner_code (effective) + shop → 配下 raw partner_no リスト
        Map<String, List<Integer>> rawPartnerNosByKey = new HashMap<>();
        for (MPartner p : all) {
            byPartnerNo.put(p.getPartnerNo(), p);
            String effective = (p.getInvoicePartnerCode() == null || p.getInvoicePartnerCode().isEmpty())
                    ? p.getPartnerCode() : p.getInvoicePartnerCode();
            String key = p.getShopNo() + "|" + effective;
            rawPartnerNosByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(p.getPartnerNo());
            // billing partner: partner_code == effective である MPartner
            if (Objects.equals(p.getPartnerCode(), effective)) {
                billingPartnerNoByKey.putIfAbsent(key, p.getPartnerNo());
            }
        }
        return new PartnerIndex(byPartnerNo, billingPartnerNoByKey, rawPartnerNosByKey);
    }

    private PartnerResolution resolveBillingPartner(PartnerIndex index, Integer shopNo, String billingPartnerCode) {
        String key = shopNo + "|" + billingPartnerCode;
        Integer billingPartnerNo = index.billingPartnerNoByKey.get(key);
        List<Integer> rawPartnerNos = index.rawPartnerNosByKey.get(key);
        if (billingPartnerNo == null || rawPartnerNos == null || rawPartnerNos.isEmpty()) {
            return null;
        }
        return new PartnerResolution(billingPartnerNo, rawPartnerNos);
    }

    private static boolean isExcludedPartner(String partnerCode) {
        if (partnerCode == null) return true;
        if (partnerCode.length() >= 7) return true; // 上様系 (長コード)
        return JOSAMA_PARTNER_CODE.equals(partnerCode)
                || CLEAN_LAB_PARTNER_CODE.equals(partnerCode);
        // NOTE: Izumi (000231) はここで除外せず、reconcile() 内で四半期判定して特別扱い。
    }

    /**
     * Izumi (000231) の四半期特殊処理判定。
     * 四半期月 (2/5/8/11 月) の末日に締めが走り、その直後の 15日締め請求書は
     * <b>1日?15日</b> だけをカバーする。通常の「前月16日?当月15日」で集計すると
     * 前月 16?末日分がダブルカウントになるため、期間を短縮する必要がある。
     *
     * @param targetMonth 対象年月（請求書の締め日から導いた transaction_month の年月）
     * @return {@code true} なら当月 1日 から集計すべき
     */
    private static boolean isIzumiQuarterlyShortPeriod(String partnerCode, YearMonth targetMonth) {
        if (!QUARTERLY_BILLING_PARTNER_CODE.equals(partnerCode)) return false;
        int prev = targetMonth.getMonthValue() - 1;
        if (prev == 0) prev = 12;
        return prev == 2 || prev == 5 || prev == 8 || prev == 11;
    }

    // ===========================================================
    // 再集計（単一 billing partner 向け簡易版）
    // ===========================================================

    /**
     * {@code billing partner} に紐づく raw partner_no の注文を期間内で集計して
     * {@link TAccountsReceivableSummary} を生成する。税率 × ゴミ袋フラグ で行を分ける。
     */
    private List<TAccountsReceivableSummary> aggregateForPartner(
            Integer shopNo,
            PartnerResolution resolution,
            ClosingDateInfo closing,
            String billingPartnerCode) {

        List<TOrderDetail> details = tOrderDetailService.findByPartnerNosAndDateRange(
                resolution.rawPartnerNos, closing.periodStart, closing.periodEnd, Flag.NO);
        if (details.isEmpty()) return List.of();

        Map<AggregationKey, List<TOrderDetail>> groups = details.stream()
                .filter(d -> shopNo.equals(d.getShopNo()))
                .collect(Collectors.groupingBy(d -> new AggregationKey(
                        d.getTaxRate() != null ? d.getTaxRate() : BigDecimal.ZERO,
                        OTAKE_GARBAGE_BAG_GOODS_CODES.contains(d.getGoodsCode()))));

        List<TAccountsReceivableSummary> result = new ArrayList<>();
        for (Map.Entry<AggregationKey, List<TOrderDetail>> e : groups.entrySet()) {
            AggregationKey k = e.getKey();
            List<TOrderDetail> group = e.getValue();

            BigDecimal totalExcludingTax = BigDecimal.ZERO;
            BigDecimal totalOriginal = BigDecimal.ZERO;
            boolean allTaxableInclude = true;

            for (TOrderDetail d : group) {
                totalExcludingTax = totalExcludingTax.add(calculateAmountExcludingTax(d));
                BigDecimal original = d.getTotalAmount();
                if (original != null) totalOriginal = totalOriginal.add(original);
                if (!TaxType.TAXABLE_INCLUDE.equals(TaxType.purse(d.getTaxType()))) {
                    allTaxableInclude = false;
                }
            }

            BigDecimal taxAmount = BigDecimal.ZERO;
            if (k.taxRate.compareTo(BigDecimal.ZERO) > 0) {
                taxAmount = totalExcludingTax
                        .multiply(k.taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.DOWN))
                        .setScale(0, RoundingMode.DOWN);
            }

            BigDecimal taxExcludedTrunc = totalExcludingTax.setScale(0, RoundingMode.DOWN);
            BigDecimal taxIncludedTrunc = allTaxableInclude
                    ? totalOriginal.setScale(0, RoundingMode.DOWN)
                    : taxExcludedTrunc.add(taxAmount.setScale(0, RoundingMode.DOWN));

            result.add(TAccountsReceivableSummary.builder()
                    .shopNo(shopNo)
                    .partnerNo(resolution.billingPartnerNo)
                    .partnerCode(billingPartnerCode)
                    .transactionMonth(closing.transactionMonth)
                    .taxRate(k.taxRate)
                    .isOtakeGarbageBag(k.isOtakeGarbageBag)
                    .taxIncludedAmountChange(taxIncludedTrunc)
                    .taxExcludedAmountChange(taxExcludedTrunc)
                    .cutoffDate(closing.cutoffCode)
                    .mfExportEnabled(false)
                    .verifiedManually(false)
                    .build());
        }
        return result;
    }

    /**
     * 旧 AR 群と新 AR 群が「税率×ゴミ袋フラグ単位」で同一の税込金額を持つかを判定。
     * Izumi 四半期のように「常に再集計する」パスで、差分がないのに DELETE+INSERT を走らせないようにする。
     */
    private static boolean sameTotals(List<TAccountsReceivableSummary> stale,
                                      List<TAccountsReceivableSummary> fresh) {
        Map<String, BigDecimal> staleMap = totalsByGroupKey(stale);
        Map<String, BigDecimal> freshMap = totalsByGroupKey(fresh);
        if (!staleMap.keySet().equals(freshMap.keySet())) return false;
        for (Map.Entry<String, BigDecimal> e : staleMap.entrySet()) {
            BigDecimal a = e.getValue();
            BigDecimal b = freshMap.get(e.getKey());
            if (a == null || b == null) return false;
            if (a.compareTo(b) != 0) return false;
        }
        return true;
    }

    private static Map<String, BigDecimal> totalsByGroupKey(List<TAccountsReceivableSummary> rows) {
        Map<String, BigDecimal> out = new HashMap<>();
        for (TAccountsReceivableSummary s : rows) {
            String key = (s.getTaxRate() != null ? s.getTaxRate().stripTrailingZeros().toPlainString() : "null")
                    + "|" + s.isOtakeGarbageBag();
            BigDecimal v = s.getTaxIncludedAmountChange() != null
                    ? s.getTaxIncludedAmountChange()
                    : BigDecimal.ZERO;
            out.merge(key, v, BigDecimal::add);
        }
        return out;
    }

    private BigDecimal calculateAmountExcludingTax(TOrderDetail detail) {
        BigDecimal totalAmount = detail.getTotalAmount();
        TaxType taxType = TaxType.purse(detail.getTaxType());
        BigDecimal taxRate = detail.getTaxRate();
        if (totalAmount == null) return BigDecimal.ZERO;
        if (taxRate == null) taxRate = BigDecimal.ZERO;
        switch (Objects.requireNonNull(taxType, "TaxType が決定できません: " + detail.getTaxType())) {
            case TAX_EXCLUDE:
            case TAX_FREE:
                return totalAmount;
            case TAXABLE_INCLUDE:
                if (taxRate.compareTo(BigDecimal.ZERO) == 0) return totalAmount;
                BigDecimal divider = BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.DOWN));
                return totalAmount.divide(divider, 10, RoundingMode.DOWN);
            default:
                log.error("未知の税区分: OrderDetail ID={}, TaxType={}", detail.getOrderDetailNo(), taxType);
                return totalAmount;
        }
    }

    private static List<YearMonth> monthsInRange(LocalDate from, LocalDate to) {
        List<YearMonth> out = new ArrayList<>();
        YearMonth cur = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        while (!cur.isAfter(end)) {
            out.add(cur);
            cur = cur.plusMonths(1);
        }
        return out;
    }

    // ===========================================================
    // Data classes
    // ===========================================================

    @Data
    @Builder
    public static class ReconcileResult {
        private int reconciledPartners;
        private int deletedRows;
        private int insertedRows;
        private int skippedManualPartners;
        private List<String> reconciledDetails;
    }

    @Value
    static class ClosingDateInfo {
        LocalDate transactionMonth; // AR に入れる transaction_month
        int cutoffCode;             // 15, 20, 30 (月末)
        LocalDate periodStart;      // 集計開始
        LocalDate periodEnd;        // 集計終了 (= transactionMonth)

        String asLabel() {
            return cutoffCode == 30 ? "月末" : cutoffCode + "日";
        }
    }

    @Value
    static class PartnerKey {
        Integer shopNo;
        String partnerCode;
    }

    @Value
    static class AggregationKey {
        BigDecimal taxRate;
        boolean isOtakeGarbageBag;
    }

    @Value
    private static class PartnerIndex {
        Map<Integer, MPartner> byPartnerNo;
        Map<String, Integer> billingPartnerNoByKey;      // (shop|invoice_partner_code) → billing partner_no
        Map<String, List<Integer>> rawPartnerNosByKey;   // (shop|invoice_partner_code) → 配下 partner_no
    }

    @Value
    private static class PartnerResolution {
        Integer billingPartnerNo;
        List<Integer> rawPartnerNos;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2772ms:
package jp.co.oda32.domain.service.finance;

import jp.co.oda32.batch.finance.MFJournalCsv;
import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 売掛金から売上仕訳CSV（マネーフォワードクラウド会計向け）を生成する Service。
 * <p>
 * 元々 {@code AccountsReceivableToSalesJournalTasklet} に埋め込まれていた CSV 生成ロジックを
 * Controller (ブラウザ DL) からも再利用できるように抽出。
 *
 * @since 2026/04/17 (設計書 design-accounts-receivable-mf.md §5.3)
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class SalesJournalCsvService {

    private static final String CLEAN_LAB_PARTNER_CODE = "301491";
    private static final long DEFAULT_INITIAL_TRANSACTION_NO = 1001L;

    private final MfAccountMasterService mfAccountMasterService;

    /**
     * 指定された売掛金サマリーリストを MF 仕訳CSV形式で writer に書き出す。
     *
     * @param summaries            出力対象（呼び出し側で mf_export_enabled 等でフィルタ済みであること）
     * @param writer               出力先
     * @param initialTransactionNo 取引番号初期値（null なら 1001）
     * @return 書き込み行数
     * @throws IOException 書き込み失敗時
     */
    public int writeCsv(List<TAccountsReceivableSummary> summaries,
                        Writer writer,
                        Long initialTransactionNo) throws IOException {
        return writeCsv(summaries, writer, initialTransactionNo, new ArrayList<>());
    }

    /**
     * master 未登録行を skipped リストに積みながら CSV を書き出す (B-W13)。
     * 従来は master 未登録で IllegalStateException を投げて fail-fast だったが、買掛側の
     * {@link PurchaseJournalCsvService} と挙動が非対称だった。skipped を列挙して呼び出し側で
     * UI 警告表示する運用に統一する。
     */
    public int writeCsv(List<TAccountsReceivableSummary> summaries,
                        Writer writer,
                        Long initialTransactionNo,
                        List<String> skipped) throws IOException {
        writer.write(MFJournalCsv.CSV_HEADER + "\n");
        if (summaries == null || summaries.isEmpty()) {
            return 0;
        }

        Map<String, MfAccountMaster> accountMasterMap = createAccountMasterMap();
        Map<String, MfAccountMaster> garbageBagAccountMasterMap = createGarbageBagAccountMasterMap();

        List<TAccountsReceivableSummary> sorted = summaries.stream()
                .sorted(Comparator.comparing(TAccountsReceivableSummary::getTransactionMonth)
                        .thenComparing(TAccountsReceivableSummary::getShopNo)
                        .thenComparing(TAccountsReceivableSummary::getPartnerCode)
                        .thenComparing(TAccountsReceivableSummary::getTaxRate, Comparator.reverseOrder()))
                .collect(Collectors.toList());

        long currentTransactionNo = initialTransactionNo != null ? initialTransactionNo : DEFAULT_INITIAL_TRANSACTION_NO;
        int written = 0;

        for (TAccountsReceivableSummary summary : sorted) {
            // CSV出力で使用する金額は tax_included_amount（確定済み）
            // フォールバックとして _change を使う（旧データ互換）
            BigDecimal taxIncluded = summary.getTaxIncludedAmount() != null
                    ? summary.getTaxIncludedAmount()
                    : summary.getTaxIncludedAmountChange();
            if (taxIncluded == null) {
                log.warn("税込金額が null のためスキップ: shopNo={}, partnerNo={}, month={}",
                        summary.getShopNo(), summary.getPartnerNo(), summary.getTransactionMonth());
                continue;
            }
            BigDecimal amount = truncate(taxIncluded);
            String searchKey = summary.getShopNo() + "_" + summary.getPartnerCode();

            MFJournalCsv record;
            if (summary.isOtakeGarbageBag()) {
                String garbageKey = "g_" + searchKey;
                MfAccountMaster master = garbageBagAccountMasterMap.get(garbageKey);
                if (master == null) {
                    String label = String.format("%s (shopNo=%d, partnerCode=%s, month=%s) ゴミ袋マスタ未登録",
                            garbageKey, summary.getShopNo(), summary.getPartnerCode(), summary.getTransactionMonth());
                    log.warn("CSV 出力からスキップ: {}", label);
                    skipped.add(label);
                    continue;
                }
                record = MFJournalCsv.builder()
                        .transactionNo(String.valueOf(currentTransactionNo++))
                        .transactionDate(summary.getTransactionMonth().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))
                        .debitAccount("未収入金")
                        .debitSubAccount(master.getSubAccountName())
                        .debitDepartment("")
                        .debitPartner("")
                        .debitTaxCategory("対象外")
                        .debitInvoice("")
                        .debitAmount(formatAmount(amount))
                        .creditAccount("仮払金")
                        .creditSubAccount("ゴミ袋／大竹市")
                        .creditDepartment("")
                        .creditPartner("")
                        .creditTaxCategory("対象外")
                        .creditInvoice("")
                        .creditAmount(formatAmount(amount))
                        .summary(master.getSearchKey() + ": " + master.getSubAccountName())
                        .tag("")
                        .memo("")
                        .build();
            } else {
                MfAccountMaster master = accountMasterMap.get(searchKey);
                if (master == null) {
                    String label = String.format("%s (shopNo=%d, partnerCode=%s, month=%s) 売掛金マスタ未登録",
                            searchKey, summary.getShopNo(), summary.getPartnerCode(), summary.getTransactionMonth());
                    log.warn("CSV 出力からスキップ: {}", label);
                    skipped.add(label);
                    continue;
                }
                String creditDepartment = "物販事業部";
                String creditSubAccount = "物販売上高";
                if (StringUtil.isEqual(summary.getPartnerCode(), CLEAN_LAB_PARTNER_CODE)) {
                    creditDepartment = "クリーンラボ";
                    creditSubAccount = "クリーンラボ売上高";
                }
                record = MFJournalCsv.builder()
                        .transactionNo(String.valueOf(currentTransactionNo++))
                        .transactionDate(summary.getTransactionMonth().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))
                        .debitAccount("売掛金")
                        .debitSubAccount(master.getSubAccountName())
                        .debitDepartment("")
                        .debitPartner("")
                        .debitTaxCategory("対象外")
                        .debitInvoice("")
                        .debitAmount(formatAmount(amount))
                        .creditAccount("売上高")
                        .creditSubAccount(creditSubAccount)
                        .creditDepartment(creditDepartment)
                        .creditPartner("")
                        .creditTaxCategory(getTaxType(summary.getTaxRate()))
                        .creditInvoice("")
                        .creditAmount(formatAmount(amount))
                        .summary(master.getSearchKey() + ": " + master.getSubAccountName())
                        .tag("")
                        .memo("")
                        .build();
            }
            writer.write(formatCsvRecord(record));
            written++;
        }
        return written;
    }

    private Map<String, MfAccountMaster> createAccountMasterMap() {
        List<MfAccountMaster> list = mfAccountMasterService.findByFinancialStatementItemAndAccountName("売掛金", "売掛金");
        return toUniqueMap(list, "売掛金");
    }

    private Map<String, MfAccountMaster> createGarbageBagAccountMasterMap() {
        List<MfAccountMaster> list = mfAccountMasterService.findByFinancialStatementItemAndAccountName("未収入金", "未収入金");
        return toUniqueMap(list, "未収入金");
    }

    private Map<String, MfAccountMaster> toUniqueMap(List<MfAccountMaster> list, String label) {
        Map<String, MfAccountMaster> map = new HashMap<>();
        for (MfAccountMaster master : list) {
            String key = master.getSearchKey();
            if (map.containsKey(key)) {
                throw new IllegalStateException(String.format(
                        "%sのアカウントマスタで重複キー: %s (%s / %s)", label, key, map.get(key), master));
            }
            map.put(key, master);
        }
        return map;
    }

    private String formatCsvRecord(MFJournalCsv r) {
        return String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                r.getTransactionNo(), r.getTransactionDate(),
                r.getDebitAccount(), r.getDebitSubAccount(), r.getDebitDepartment(), r.getDebitPartner(),
                r.getDebitTaxCategory(), r.getDebitInvoice(), r.getDebitAmount(),
                r.getCreditAccount(), r.getCreditSubAccount(), r.getCreditDepartment(), r.getCreditPartner(),
                r.getCreditTaxCategory(), r.getCreditInvoice(), r.getCreditAmount(),
                r.getSummary(), r.getTag(), r.getMemo());
    }

    private String getTaxType(BigDecimal taxRate) {
        if (taxRate == null) return "";
        if (taxRate.compareTo(BigDecimal.valueOf(10)) == 0) return "課税売上 10%";
        if (taxRate.compareTo(BigDecimal.valueOf(8)) == 0) return "課売 (軽)8%";
        if (taxRate.compareTo(BigDecimal.ZERO) == 0) return "非売";
        return "";
    }

    private BigDecimal truncate(BigDecimal v) {
        return v.setScale(0, RoundingMode.DOWN);
    }

    private String formatAmount(BigDecimal v) {
        return truncate(v).toPlainString();
    }

    /**
     * CSV出力対象の売掛金サマリーに CSV出力済みマーカーを付ける。
     * {@code tax_included_amount} / {@code tax_excluded_amount} に {@code *_change} の値をコピーする。
     * （買掛側と同じ仕様: CSV出力済みの確定金額を保持）
     */
    public void markExported(List<TAccountsReceivableSummary> summaries) {
        for (TAccountsReceivableSummary s : summaries) {
            if (s.getTaxIncludedAmount() == null && s.getTaxIncludedAmountChange() != null) {
                s.setTaxIncludedAmount(s.getTaxIncludedAmountChange());
            }
            if (s.getTaxExcludedAmount() == null && s.getTaxExcludedAmountChange() != null) {
                s.setTaxExcludedAmount(s.getTaxExcludedAmountChange());
            }
        }
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3790ms:
package jp.co.oda32.batch.finance;

import jp.co.oda32.batch.finance.model.InvoiceVerificationSummary;
import jp.co.oda32.batch.finance.service.InvoiceVerifier;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.constant.PaymentType;
import jp.co.oda32.constant.TaxType;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.model.order.TOrderDetail;
import jp.co.oda32.domain.service.finance.TAccountsReceivableSummaryService;
import jp.co.oda32.domain.service.master.MPartnerService;
import jp.co.oda32.domain.service.order.TOrderDetailService;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 売掛金額を集計し、TAccountsReceivableSummaryテーブルに登録するTasklet
 * <ul>
 *   <li>集計元: {@code t_order_detail}（SMILE受注連携）</li>
 *   <li>集計キー: 店舗 × invoice_partner_code(= 請求先) × 税率 × ゴミ袋フラグ × (都度現金時のみ注文番号)</li>
 *   <li>検証: {@link InvoiceVerifier} に委譲（請求書 t_invoice と突合）</li>
 *   <li>検証NGも保存（画面で手動確定できるように）</li>
 *   <li>verified_manually=true 行は再集計でも上書きしない</li>
 *   <li>締め日タイプ指定 (all/15/20/month_end) で対象グループを絞れる</li>
 * </ul>
 *
 * @author k_oda
 * @modified 2026/04/17 - 検証ロジックを InvoiceVerifier に抽出、締め日タイプ指定、検証NG保存、手動確定保護 を追加
 *                       （設計書 design-accounts-receivable-mf.md）
 * @since 2024/08/31
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class TAccountsReceivableSummaryTasklet implements Tasklet {

    // 大竹市のゴミ袋のgoods_code一覧
    private static final Set<String> OTAKE_GARBAGE_BAG_GOODS_CODES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "00100001", "00100003", "00100005",
                    "00100101", "00100103", "00100105"
            ))
    );

    // 締め日タイプ（ジョブパラメータ）
    public static final String CUTOFF_TYPE_ALL = "all";
    public static final String CUTOFF_TYPE_15 = "15";
    public static final String CUTOFF_TYPE_20 = "20";
    public static final String CUTOFF_TYPE_MONTH_END = "month_end";

    private final TAccountsReceivableSummaryService tAccountsReceivableSummaryService;
    private final TOrderDetailService tOrderDetailService;
    private final MPartnerService mPartnerService;
    private final InvoiceVerifier invoiceVerifier;

    // --- 処理全体で共有するキャッシュデータ ---
    private Map<Integer, String> partnerInvoiceCodeMap; // partner_no -> invoice_partner_code
    private Map<ShopInvoiceKey, Integer> invoicePartnerCodeToPartnerNoMap; // (shopNo, invoice_partner_code) -> partnerNo (請求先)
    private Map<Integer, Integer> partnerCutoffDateMap; // partner_no -> cutoff_date

    // --- 各締め日タイプごとの得意先リスト (partner_no) ---
    private List<Integer> monthEndPartnerNos;
    private List<Integer> cutoff20PartnerNos;
    private List<Integer> cutoff15PartnerNos;

    @Value("#{jobParameters['targetDate']}")
    private String targetDate; // 例: "20260420"

    @Value("#{jobParameters['cutoffType']}")
    private String cutoffType; // "all" | "15" | "20" | "month_end"（null/空は "all" 扱い）

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String effectiveCutoffType = (cutoffType == null || cutoffType.isBlank()) ? CUTOFF_TYPE_ALL : cutoffType;
        log.info("売掛金集計バッチ処理を開始します。 targetDate={}, cutoffType={}", targetDate, effectiveCutoffType);
        try {
            initializeCache();
            LocalDate targetDateAsDate = LocalDate.parse(targetDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            log.info("処理対象年月: {}", YearMonth.from(targetDateAsDate));

            preloadPartnerData();

            boolean all = CUTOFF_TYPE_ALL.equals(effectiveCutoffType);
            if (all || CUTOFF_TYPE_MONTH_END.equals(effectiveCutoffType)) {
                processMonthEndCutoffPartners(targetDateAsDate);
            }
            if (all || CUTOFF_TYPE_20.equals(effectiveCutoffType)) {
                process20thCutoffPartners(targetDateAsDate);
            }
            if (all || CUTOFF_TYPE_15.equals(effectiveCutoffType)) {
                process15thCutoffPartners(targetDateAsDate);
            }

            log.info("売掛金集計バッチ処理が正常に完了しました。");
        } catch (Exception e) {
            log.error("売掛金集計バッチ処理中に致命的なエラーが発生しました。", e);
            contribution.setExitStatus(org.springframework.batch.core.ExitStatus.FAILED);
            throw new RuntimeException("売掛金集計バッチ処理がエラーで終了しました。", e);
        }
        return RepeatStatus.FINISHED;
    }

    private void initializeCache() {
        partnerInvoiceCodeMap = new HashMap<>();
        invoicePartnerCodeToPartnerNoMap = new HashMap<>();
        partnerCutoffDateMap = new HashMap<>();
        monthEndPartnerNos = new ArrayList<>();
        cutoff20PartnerNos = new ArrayList<>();
        cutoff15PartnerNos = new ArrayList<>();
    }

    private void preloadPartnerData() {
        log.info("得意先情報のプリロードを開始します。");
        List<MPartner> allPartners = mPartnerService.findAll();
        log.info("得意先マスタから {} 件の有効な得意先を読み込みました。", allPartners.size());

        int cashOnDeliveryCount = 0;
        for (MPartner partner : allPartners) {
            Integer partnerNo = partner.getPartnerNo();
            String partnerCode = partner.getPartnerCode();
            Integer shopNo = partner.getShopNo();
            Integer cutoffDate = partner.getCutoffDate() == null ? 0 : partner.getCutoffDate();
            String invoicePartnerCode = partner.getInvoicePartnerCode();
            String effectiveInvoiceCode = (invoicePartnerCode == null || invoicePartnerCode.isEmpty()) ? partnerCode : invoicePartnerCode;

            partnerInvoiceCodeMap.put(partnerNo, effectiveInvoiceCode);
            ShopInvoiceKey shopInvoiceKey = new ShopInvoiceKey(shopNo, effectiveInvoiceCode);
            invoicePartnerCodeToPartnerNoMap.put(shopInvoiceKey, partnerNo);
            partnerCutoffDateMap.put(partnerNo, cutoffDate);

            PaymentType paymentType = PaymentType.fromCutoffCode(cutoffDate);
            switch (paymentType) {
                case DAY_20:
                    cutoff20PartnerNos.add(partnerNo);
                    break;
                case DAY_15:
                    cutoff15PartnerNos.add(partnerNo);
                    break;
                case CASH_ON_DELIVERY:
                    monthEndPartnerNos.add(partnerNo);
                    cashOnDeliveryCount++;
                    break;
                case MONTH_END:
                default:
                    monthEndPartnerNos.add(partnerNo);
                    break;
            }
        }

        monthEndPartnerNos = monthEndPartnerNos.stream().distinct().collect(Collectors.toList());
        cutoff20PartnerNos = cutoff20PartnerNos.stream().distinct().collect(Collectors.toList());
        cutoff15PartnerNos = cutoff15PartnerNos.stream().distinct().collect(Collectors.toList());

        log.info("得意先情報のプリロード完了。月末締め:{}件, 20日締め:{}件, 15日締め:{}件, 都度現金払い:{}件",
                monthEndPartnerNos.size() - cashOnDeliveryCount,
                cutoff20PartnerNos.size(), cutoff15PartnerNos.size(), cashOnDeliveryCount);
    }

    private void processMonthEndCutoffPartners(LocalDate targetDateAsDate) {
        if (monthEndPartnerNos.isEmpty()) {
            log.info("月末締めグループ（都度現金払い含む）の対象得意先が存在しません。");
            return;
        }
        YearMonth currentMonth = YearMonth.from(targetDateAsDate);
        LocalDate periodStartDate = currentMonth.atDay(1);
        LocalDate periodEndDate = currentMonth.atEndOfMonth();
        log.info("--- 月末締めグループ（都度現金払い含む）の売掛金集計開始 ---");
        List<TAccountsReceivableSummary> summaries = calculateReceivableSummaries(periodStartDate, periodEndDate, monthEndPartnerNos);
        verifyAndSave(summaries, "月末締めグループ", periodEndDate);
    }

    private void process20thCutoffPartners(LocalDate targetDateAsDate) {
        if (cutoff20PartnerNos.isEmpty()) {
            log.info("20日締めの対象得意先が存在しません。");
            return;
        }
        LocalDate periodStartDate = YearMonth.from(targetDateAsDate).minusMonths(1).atDay(21);
        LocalDate periodEndDate = YearMonth.from(targetDateAsDate).atDay(20);
        log.info("--- 20日締めの売掛金集計開始 ---");
        List<TAccountsReceivableSummary> summaries = calculateReceivableSummaries(periodStartDate, periodEndDate, cutoff20PartnerNos);
        verifyAndSave(summaries, "20日締め", periodEndDate);
    }

    private void process15thCutoffPartners(LocalDate targetDateAsDate) {
        if (cutoff15PartnerNos.isEmpty()) {
            log.info("15日締めの対象得意先が存在しません。");
            return;
        }
        LocalDate periodStartDate = YearMonth.from(targetDateAsDate).minusMonths(1).atDay(16);
        LocalDate periodEndDate = YearMonth.from(targetDateAsDate).atDay(15);
        log.info("--- 15日締めの売掛金集計開始 ---");
        List<TAccountsReceivableSummary> summaries = calculateReceivableSummaries(periodStartDate, periodEndDate, cutoff15PartnerNos);
        verifyAndSave(summaries, "15日締め", periodEndDate);
    }

    /**
     * 集計結果を検証し、検証NGを含む全件を保存する。
     * <ul>
     *   <li>既存DB行と計算結果をマージ（PKが一致するDB行があればそれに計算値を反映）</li>
     *   <li>verified_manually=true の既存行はスキップ（保護）</li>
     *   <li>{@link InvoiceVerifier} に検証を委譲</li>
     *   <li>全件保存（検証NG含む、画面で手動確定できるよう）</li>
     * </ul>
     */
    private void verifyAndSave(List<TAccountsReceivableSummary> calculated,
                               String cutoffType, LocalDate targetPeriodEndDate) {
        if (calculated == null || calculated.isEmpty()) {
            log.info("{} の処理対象となる集計結果はありませんでした。", cutoffType);
            return;
        }
        log.info("{} の集計件数: {}", cutoffType, calculated.size());

        // 既存DB行とマージ。verified_manually=true はスキップ。
        List<TAccountsReceivableSummary> toVerify = new ArrayList<>();
        int skippedManualCount = 0;
        for (TAccountsReceivableSummary calc : calculated) {
            TAccountsReceivableSummary existing = tAccountsReceivableSummaryService.getByPK(
                    calc.getShopNo(), calc.getPartnerNo(), calc.getTransactionMonth(),
                    calc.getTaxRate(), calc.isOtakeGarbageBag()
            );
            if (existing != null && Boolean.TRUE.equals(existing.getVerifiedManually())) {
                skippedManualCount++;
                log.debug("手動確定済みのため上書きスキップ: shopNo={}, partnerNo={}, month={}, taxRate={}%",
                        existing.getShopNo(), existing.getPartnerNo(),
                        existing.getTransactionMonth(), existing.getTaxRate());
                continue;
            }
            if (existing != null) {
                // 既存行の集計フィールドだけ上書き。検証系は verify() で更新される。
                existing.setPartnerCode(calc.getPartnerCode());
                existing.setTaxIncludedAmountChange(calc.getTaxIncludedAmountChange());
                existing.setTaxExcludedAmountChange(calc.getTaxExcludedAmountChange());
                existing.setCutoffDate(calc.getCutoffDate());
                existing.setOrderNo(calc.getOrderNo());
                toVerify.add(existing);
            } else {
                toVerify.add(calc);
            }
        }

        // 検証ロジックは InvoiceVerifier に委譲
        InvoiceVerificationSummary result = invoiceVerifier.verify(toVerify, targetPeriodEndDate);
        log.info("{} の検証結果: 一致={}, 不一致={}, 請求書なし={}, 上様上書き={}, 四半期特殊={}, 手動スキップ(tasklet側)={}",
                cutoffType, result.getMatchedCount(), result.getMismatchCount(),
                result.getNotFoundCount(), result.getJosamaOverwriteCount(),
                result.getQuarterlySpecialCount(), skippedManualCount);

        // 全件保存（検証NG含む）
        int saved = 0;
        for (TAccountsReceivableSummary summary : toVerify) {
            try {
                tAccountsReceivableSummaryService.save(summary);
                saved++;
            } catch (Exception e) {
                log.error("売掛金集計の保存中にエラー: shopNo={}, partnerNo={}, partnerCode={}, month={}",
                        summary.getShopNo(), summary.getPartnerNo(),
                        summary.getPartnerCode(), summary.getTransactionMonth(), e);
            }
        }
        log.info("{} の保存件数: {}", cutoffType, saved);
    }

    private List<TAccountsReceivableSummary> calculateReceivableSummaries(
            LocalDate startDate, LocalDate endDate, List<Integer> targetPartnerNos) {
        log.info("売掛金集計計算を開始します。期間: {} ～ {}, 対象得意先数: {}", startDate, endDate, targetPartnerNos.size());
        if (targetPartnerNos.isEmpty()) {
            return new ArrayList<>();
        }
        List<TOrderDetail> orderDetails = tOrderDetailService.findByPartnerNosAndDateRange(
                targetPartnerNos, startDate, endDate, Flag.NO);
        log.info("期間内の注文詳細を取得しました。件数: {}", orderDetails.size());
        if (orderDetails.isEmpty()) {
            return new ArrayList<>();
        }

        Map<SummaryKey, List<TOrderDetail>> detailsBySummaryKey = new HashMap<>();
        Map<SummaryKey, PartnerBillingInfo> keyToBillingInfoMap = new HashMap<>();
        int skippedDetailCount = 0;

        for (TOrderDetail detail : orderDetails) {
            Integer shopNo = detail.getShopNo();
            Integer orderPartnerNo = detail.getTOrder().getPartnerNo();
            String invoicePartnerCode = this.partnerInvoiceCodeMap.get(orderPartnerNo);
            if (invoicePartnerCode == null) {
                log.error("注文詳細(ID:{}, OrderNo:{})の得意先番号 {} に対応する請求コードが見つかりません。スキップ。",
                        detail.getOrderDetailNo(), detail.getOrderNo(), orderPartnerNo);
                skippedDetailCount++;
                continue;
            }
            ShopInvoiceKey shopInvoiceKey = new ShopInvoiceKey(shopNo, invoicePartnerCode);
            Integer billingPartnerNo = this.invoicePartnerCodeToPartnerNoMap.get(shopInvoiceKey);
            if (billingPartnerNo == null) {
                log.error("注文詳細(ID:{}, OrderNo:{})の店舗:{}, 請求コード:{} に対応する請求先得意先番号が見つかりません。スキップ。",
                        detail.getOrderDetailNo(), detail.getOrderNo(), shopNo, invoicePartnerCode);
                skippedDetailCount++;
                continue;
            }
            Integer billingCutoffDate = this.partnerCutoffDateMap.get(billingPartnerNo);
            if (billingCutoffDate == null) {
                log.error("請求先得意先番号 {} の締め日情報が見つかりません。スキップ。", billingPartnerNo);
                skippedDetailCount++;
                continue;
            }

            PaymentType paymentType = PaymentType.fromCutoffCode(billingCutoffDate);
            boolean isCashOnDelivery = paymentType == PaymentType.CASH_ON_DELIVERY;
            boolean isLongPartnerCode = invoicePartnerCode.length() >= 7;
            int summaryPartnerNo;
            Integer summaryOrderNo = null;
            String summaryPartnerCode;
            Integer effectiveCutoffDate = billingCutoffDate;

            if (isCashOnDelivery) {
                // 都度現金払いは「月初?月末の合計 vs 月次請求書」で突合するため、
                // 注文単位ではなく月次集約する (order_no を SummaryKey に含めない)。
                // DB PK = (shop, partner, month, tax_rate, is_otake) なので、order_no を
                // SummaryKey に入れると複数行を作ろうとしても PK 衝突で最後の1件しか残らない。
                summaryPartnerNo = billingPartnerNo;
                summaryOrderNo = null;
                summaryPartnerCode = invoicePartnerCode;
            } else if (isLongPartnerCode) {
                // 得意先コードが7桁以上なら「上様」(999999) + 都度現金払い
                summaryPartnerNo = -999999;
                summaryPartnerCode = "999999";
                summaryOrderNo = detail.getOrderNo();
                effectiveCutoffDate = PaymentType.CASH_ON_DELIVERY.getCutoffCode();
                if (summaryOrderNo == null) {
                    skippedDetailCount++;
                    continue;
                }
            } else {
                summaryPartnerNo = billingPartnerNo;
                summaryPartnerCode = invoicePartnerCode;
            }

            SummaryKey key = new SummaryKey(
                    shopNo, summaryPartnerNo,
                    detail.getTaxRate() != null ? detail.getTaxRate() : BigDecimal.ZERO,
                    OTAKE_GARBAGE_BAG_GOODS_CODES.contains(detail.getGoodsCode()),
                    summaryOrderNo
            );
            final String finalPartnerCode = summaryPartnerCode;
            final Integer finalCutoffDate = effectiveCutoffDate;
            keyToBillingInfoMap.computeIfAbsent(key, k -> new PartnerBillingInfo(finalPartnerCode, finalCutoffDate));
            detailsBySummaryKey.computeIfAbsent(key, k -> new ArrayList<>()).add(detail);
        }

        if (skippedDetailCount > 0) {
            log.warn("請求情報の解決に失敗し、{} 件の注文詳細がスキップされました。", skippedDetailCount);
        }
        log.info("注文詳細のグループ化完了。グループ数: {}", detailsBySummaryKey.size());

        List<TAccountsReceivableSummary> summaries = new ArrayList<>();
        for (Map.Entry<SummaryKey, List<TOrderDetail>> entry : detailsBySummaryKey.entrySet()) {
            SummaryKey key = entry.getKey();
            List<TOrderDetail> detailsInGroup = entry.getValue();
            PartnerBillingInfo billingInfo = keyToBillingInfoMap.get(key);
            if (billingInfo == null) continue;

            BigDecimal totalAmountExcludingTax = BigDecimal.ZERO;
            BigDecimal totalOriginalAmount = BigDecimal.ZERO;

            for (TOrderDetail detail : detailsInGroup) {
                totalAmountExcludingTax = totalAmountExcludingTax.add(calculateAmountExcludingTax(detail));
                BigDecimal totalAmount = detail.getTotalAmount();
                if (totalAmount != null) {
                    totalOriginalAmount = totalOriginalAmount.add(totalAmount);
                }
            }

            BigDecimal calculatedTaxAmount = BigDecimal.ZERO;
            BigDecimal taxRate = key.getTaxRate();
            if (taxRate.compareTo(BigDecimal.ZERO) > 0) {
                calculatedTaxAmount = totalAmountExcludingTax
                        .multiply(taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.DOWN))
                        .setScale(0, RoundingMode.DOWN);
            }

            boolean allTaxableInclude = detailsInGroup.stream()
                    .allMatch(d -> TaxType.TAXABLE_INCLUDE.equals(TaxType.purse(d.getTaxType())));

            BigDecimal totalAmountIncludingTax = allTaxableInclude
                    ? totalOriginalAmount
                    : totalAmountExcludingTax.add(calculatedTaxAmount);

            BigDecimal taxExcludedAmountTruncated = totalAmountExcludingTax.setScale(0, RoundingMode.DOWN);
            BigDecimal taxAmount = allTaxableInclude
                    ? totalOriginalAmount.subtract(totalAmountExcludingTax)
                    : calculatedTaxAmount;
            BigDecimal taxAmountTruncated = taxAmount.setScale(0, RoundingMode.DOWN);

            BigDecimal originalTaxIncludedAmount = allTaxableInclude ? totalOriginalAmount : totalAmountIncludingTax;
            BigDecimal originalTaxIncludedAmountTruncated = originalTaxIncludedAmount.setScale(0, RoundingMode.DOWN);
            BigDecimal taxIncludedAmountTruncated = taxExcludedAmountTruncated.add(taxAmountTruncated);

            BigDecimal diff = originalTaxIncludedAmountTruncated.subtract(taxIncludedAmountTruncated);
            if (diff.abs().compareTo(BigDecimal.ONE) == 0) {
                log.warn("消費税計算に1円の誤差を検出したため請求書金額に合わせます: partnerNo={}, 元={}, 計算={}",
                        key.getPartnerNo(), originalTaxIncludedAmountTruncated, taxIncludedAmountTruncated);
                taxIncludedAmountTruncated = originalTaxIncludedAmountTruncated;
            }

            TAccountsReceivableSummary summary = TAccountsReceivableSummary.builder()
                    .shopNo(key.getShopNo())
                    .partnerNo(key.getPartnerNo())
                    .partnerCode(billingInfo.getPartnerCode())
                    .transactionMonth(endDate)
                    .taxRate(key.getTaxRate())
                    .taxIncludedAmountChange(taxIncludedAmountTruncated)
                    .taxExcludedAmountChange(taxExcludedAmountTruncated)
                    .isOtakeGarbageBag(key.isOtakeGarbageBag())
                    .cutoffDate(billingInfo.getCutoffDate())
                    .orderNo(key.getOrderNo())
                    .mfExportEnabled(false) // 検証前は false
                    .verifiedManually(false)
                    .build();
            summaries.add(summary);
        }

        log.info("売掛金集計計算完了。生成されたSummary件数: {}", summaries.size());
        return summaries;
    }

    private BigDecimal calculateAmountExcludingTax(TOrderDetail detail) {
        BigDecimal totalAmount = detail.getTotalAmount();
        TaxType taxType = TaxType.purse(detail.getTaxType());
        BigDecimal taxRate = detail.getTaxRate();
        if (totalAmount == null) return BigDecimal.ZERO;
        if (taxRate == null) taxRate = BigDecimal.ZERO;

        switch (Objects.requireNonNull(taxType, "TaxType が決定できません: " + detail.getTaxType())) {
            case TAX_EXCLUDE:
                return totalAmount;
            case TAXABLE_INCLUDE:
                if (taxRate.compareTo(BigDecimal.ZERO) == 0) return totalAmount;
                BigDecimal divider = BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.DOWN));
                return totalAmount.divide(divider, 10, RoundingMode.DOWN);
            case TAX_FREE:
                return totalAmount;
            default:
                log.error("未知の税区分: OrderDetail ID={}, TaxType={}", detail.getOrderDetailNo(), taxType);
                return totalAmount;
        }
    }

    // --- Helper Classes ---
    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode
    @ToString
    private static class SummaryKey {
        private final Integer shopNo;
        private final Integer partnerNo;
        private final BigDecimal taxRate;
        private final boolean isOtakeGarbageBag;
        private final Integer orderNo;
    }

    @Getter
    @AllArgsConstructor
    @ToString
    private static class PartnerBillingInfo {
        private final String partnerCode;
        private final Integer cutoffDate;
    }

    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode
    @ToString
    private static class ShopInvoiceKey {
        private final Integer shopNo;
        private final String invoicePartnerCode;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
バックエンドを見る限り、AR は「集計表」「検証結果」「CSV出力対象」「手動確定」が同じ行に混在しています。この形だと、業務イベントの履歴ではなく現在値だけが残るため、訂正・締め変更・入金消込の説明能力がかなり弱くなります。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content frontend/components/pages/finance/accounts-receivable.tsx' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content frontend/components/pages/finance/AccountsReceivableVerifyDialog.tsx' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content frontend/components/pages/finance/AccountsReceivableAggregateDialog.tsx' in C:\project\odamitsu-data-hub
2026-05-04T01:55:08.038263Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/**/*.java,frontend/**/*.tsx,claudedocs/*.md -Pattern 'B-CART','shop=1','第1事業部','m_partner_group','partner_group','t_invoice','invoice_partner_code','入金','消込','allocation','receivable' -CaseSensitive:"'$false | Select-Object Path,LineNumber,Line'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/**/*.java,frontend/**/*.tsx,claudedocs/*.md -Pattern 'B-CART','shop=1','第1事業部','m_partner_group','partner_group','t_invoice','invoice_partner_code','入金','消込','allocation','receivable' -CaseSensitive:"'$false | Select-Object Path,LineNumber,Line' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/**/*.java,frontend/**/*.tsx,claudedocs/*.md -Pattern 'B-CART','shop=1','第1事業部','m_partner_group','partner_group','t_invoice','invoice_partner_code','入金','消込','allocation','receivable' -CaseSensitive:"'$false | Select-Object Path,LineNumber,Line'` rejected: blocked by policy
 succeeded in 1616ms:
'use client'

import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Switch } from '@/components/ui/switch'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { formatCurrency } from '@/lib/utils'
import { Unlock } from 'lucide-react'
import type { AccountsReceivable } from '@/types/accounts-receivable'

interface Props {
  row: AccountsReceivable | null
  onClose: () => void
  onSubmit: (taxIncludedAmount: number, taxExcludedAmount: number, note: string, mfExportEnabled: boolean) => void
  onReleaseManualLock: () => void
  submitting: boolean
  releasing: boolean
  isAdmin: boolean
}

/**
 * 売掛金の手動確定ダイアログ。買掛側 VerifyDialog と対称。
 * 税込・税抜を個別に入力できる（売掛は税率別に行が分かれているため）。
 */
export function AccountsReceivableVerifyDialog({
  row,
  onClose,
  onSubmit,
  onReleaseManualLock,
  submitting,
  releasing,
  isAdmin,
}: Props) {
  const rowKey = row
    ? `${row.shopNo}-${row.partnerNo}-${row.transactionMonth}-${String(row.taxRate)}-${row.isOtakeGarbageBag}`
    : null
  const prevKeyRef = useRef<string | null>(null)
  const [taxIncluded, setTaxIncluded] = useState<string>('')
  const [taxExcluded, setTaxExcluded] = useState<string>('')
  const [note, setNote] = useState<string>('')
  const [mfExport, setMfExport] = useState<boolean>(true)

  useEffect(() => {
    if (rowKey !== prevKeyRef.current) {
      prevKeyRef.current = rowKey
      if (row) {
        // 請求書金額があればそれを初期値に（差額を吸収する定番操作）
        const defaultInc = row.invoiceAmount ?? row.taxIncludedAmount ?? row.taxIncludedAmountChange ?? 0
        const defaultExc = row.taxExcludedAmount ?? row.taxExcludedAmountChange ?? 0
        setTaxIncluded(String(defaultInc))
        setTaxExcluded(String(defaultExc))
        setNote(row.verificationNote ?? '')
        // F-W9: 現行値を尊重。既存行が代引等で OFF ならそのまま、未設定は true デフォルト。
        setMfExport(row.mfExportEnabled ?? true)
        // NOTE: `?? true` を維持 (完全 false デフォルトにすると通常ケースで checkbox OFF になり UX 悪化)。
        // row.mfExportEnabled が false で保存されている行は確実に OFF で開く。
      } else {
        setTaxIncluded('')
        setTaxExcluded('')
        setNote('')
        setMfExport(true)
      }
    }
  }, [rowKey, row])

  const open = row !== null

  const handleSubmit = () => {
    const inc = Number(taxIncluded)
    const exc = Number(taxExcluded)
    if (!Number.isFinite(inc) || !Number.isFinite(exc)) {
      toast.error('金額が不正です')
      return
    }
    if (note.length > 500) {
      toast.error('備考は500字以内で入力してください')
      return
    }
    onSubmit(inc, exc, note, mfExport)
  }

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) onClose() }}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>売掛金 検証確定</DialogTitle>
        </DialogHeader>
        {row && (
          <div className="space-y-3 text-sm">
            <div className="grid grid-cols-2 gap-2">
              <div><span className="text-muted-foreground">得意先: </span>{row.partnerCode} {row.partnerName ?? ''}</div>
              <div><span className="text-muted-foreground">税率: </span>{Number(row.taxRate)}%</div>
              <div><span className="text-muted-foreground">取引月: </span>{row.transactionMonth}</div>
              <div><span className="text-muted-foreground">ショップ: </span>{row.shopNo}</div>
              {row.isOtakeGarbageBag && (
                <div className="col-span-2"><span className="text-muted-foreground">種別: </span>大竹市ゴミ袋</div>
              )}
              {row.orderNo != null && (
                <div><span className="text-muted-foreground">注文No: </span>{row.orderNo}</div>
              )}
            </div>
            <div className="rounded border p-2 space-y-1">
              <div className="flex justify-between"><span>集計金額(税込)</span><span className="tabular-nums">{formatCurrency(row.taxIncludedAmountChange ?? 0)}</span></div>
              <div className="flex justify-between"><span>集計金額(税抜)</span><span className="tabular-nums">{formatCurrency(row.taxExcludedAmountChange ?? 0)}</span></div>
              <div className="flex justify-between"><span>請求書金額(税込)</span><span className="tabular-nums">{row.invoiceAmount == null ? '-' : formatCurrency(row.invoiceAmount)}</span></div>
              <div className="flex justify-between">
                <span>差額</span>
                <span className={row.verificationResult === 0 ? 'text-red-600 tabular-nums' : 'tabular-nums'}>
                  {row.verificationDifference == null ? '-' : formatCurrency(row.verificationDifference)}
                </span>
              </div>
            </div>
            {row.verifiedManually && (
              <div className="rounded border border-green-300 bg-green-50 p-2 text-xs text-green-800">
                このレコードは手動確定済みです。次回 再集計・再検証で上書きされません。
              </div>
            )}
            <div>
              <Label htmlFor="tax-included">確定金額(税込)</Label>
              <Input
                id="tax-included"
                type="number"
                value={taxIncluded}
                onChange={(e) => setTaxIncluded(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="tax-excluded">確定金額(税抜)</Label>
              <Input
                id="tax-excluded"
                type="number"
                value={taxExcluded}
                onChange={(e) => setTaxExcluded(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="verification-note">備考（最大500字）</Label>
              <Textarea
                id="verification-note"
                rows={3}
                maxLength={500}
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="例: 請求書No.A-001で確認"
              />
            </div>
            <div className="flex items-center gap-2">
              <Switch id="mf-export" checked={mfExport} onCheckedChange={setMfExport} />
              <Label htmlFor="mf-export" className="cursor-pointer">MF CSV出力対象にする</Label>
            </div>
          </div>
        )}
        <DialogFooter className="gap-2">
          {row?.verifiedManually && isAdmin && (
            <Button
              variant="outline"
              onClick={onReleaseManualLock}
              disabled={releasing || submitting}
              className="mr-auto"
            >
              <Unlock className="mr-1 h-4 w-4" />
              {releasing ? '解除中...' : '手動確定解除'}
            </Button>
          )}
          <Button variant="outline" onClick={onClose}>キャンセル</Button>
          <Button onClick={handleSubmit} disabled={submitting || !taxIncluded || !taxExcluded}>
            {submitting ? '更新中...' : '確定'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1620ms:
'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops, usePartners } from '@/hooks/use-master-data'
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
import { formatCurrency, normalizeForSearch } from '@/lib/utils'
import { emptyPage, type Paginated } from '@/types/paginated'
import {
  AlertCircle,
  CheckCircle2,
  FileDown,
  RefreshCw,
  ShieldCheck,
  Upload,
  XCircle,
} from 'lucide-react'
import { toast } from 'sonner'
import {
  type AccountsReceivable,
  type AccountsReceivableSummary,
  type VerificationFilter,
  type CutoffType,
  type BulkVerifyResponse,
  type AggregateResponse,
  VERIFICATION_FILTER_LABELS,
  defaultDateRange,
  pkToPath,
} from '@/types/accounts-receivable'
import { AccountsReceivableVerifyDialog } from './AccountsReceivableVerifyDialog'
import { AccountsReceivableAggregateDialog } from './AccountsReceivableAggregateDialog'
import { InvoiceImportDialog } from './InvoiceImportDialog'

const PAGE_SIZE = 50
/** 「全件表示」ON 時のページサイズ上限（実運用の月次売掛は 1000 行超えない想定）。 */
const ALL_PAGE_SIZE = 10000

interface SearchParams {
  shopNo?: number
  partnerNo?: number
  fromDate: string
  toDate: string
  verificationFilter: VerificationFilter
}

export default function AccountsReceivablePage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const isAdmin = user?.shopNo === 0

  const defaults = useMemo(() => defaultDateRange(), [])
  const initialSearch: SearchParams = useMemo(() => ({
    shopNo: user?.shopNo && user.shopNo !== 0 ? user.shopNo : undefined,
    partnerNo: undefined,
    fromDate: defaults.fromDate,
    toDate: defaults.toDate,
    verificationFilter: 'all',
  }), [user?.shopNo, defaults.fromDate, defaults.toDate])

  // フォームへの入力状態（一文字ずつ反映）
  const [search, setSearch] = useState<SearchParams>(initialSearch)
  // 実際に一覧に適用されている条件（検索ボタン押下時のみ更新）
  const [appliedSearch, setAppliedSearch] = useState<SearchParams>(initialSearch)

  // user は非同期に localStorage から復元されるため、初回マウント時は null → undefined shopNo で
  // 固まるバグがあった (F-1)。user が復元されたら非adminの shopNo を後追いで注入する。
  useEffect(() => {
    if (user && user.shopNo !== 0) {
      setSearch((prev) => prev.shopNo === undefined ? { ...prev, shopNo: user.shopNo } : prev)
      setAppliedSearch((prev) => prev.shopNo === undefined ? { ...prev, shopNo: user.shopNo } : prev)
    }
  }, [user])

  const [page, setPage] = useState(0)
  // 全件表示モード: ページング無効で一度に ALL_PAGE_SIZE 件取得して合計を検算できるようにする
  const [showAll, setShowAll] = useState(false)
  const [verifyRow, setVerifyRow] = useState<AccountsReceivable | null>(null)
  const [aggregateOpen, setAggregateOpen] = useState(false)
  const [invoiceImportOpen, setInvoiceImportOpen] = useState(false)
  const [confirmBulkVerify, setConfirmBulkVerify] = useState(false)
  const [confirmExport, setConfirmExport] = useState(false)

  const { data: shopsData } = useShops(isAdmin)
  // 得意先絞り込み用。shop 未選択 (admin が「すべて」) の場合は partners API が使えないため
  // SearchableSelect を disabled にする。
  const partnersQuery = usePartners(search.shopNo ?? (isAdmin ? undefined : user?.shopNo))
  const partnerOptions = useMemo(() => (partnersQuery.data ?? []).map((p) => ({
    value: String(p.partnerNo),
    label: `${p.partnerCode} ${p.partnerName}`,
  })), [partnersQuery.data])

  // クエリは appliedSearch のみに依存する（キーストローク毎に発火しない）
  const effectivePageSize = showAll ? ALL_PAGE_SIZE : PAGE_SIZE
  const effectivePage = showAll ? 0 : page
  const listQuery = useQuery({
    queryKey: ['accounts-receivable', appliedSearch, effectivePage, effectivePageSize],
    queryFn: async () => {
      const params = new URLSearchParams()
      if (appliedSearch.shopNo != null) params.set('shopNo', String(appliedSearch.shopNo))
      if (appliedSearch.partnerNo != null) params.set('partnerNo', String(appliedSearch.partnerNo))
      if (appliedSearch.fromDate) params.set('fromDate', appliedSearch.fromDate)
      if (appliedSearch.toDate) params.set('toDate', appliedSearch.toDate)
      if (appliedSearch.verificationFilter !== 'all') params.set('verificationFilter', appliedSearch.verificationFilter)
      params.set('page', String(effectivePage))
      params.set('size', String(effectivePageSize))
      return api.get<Paginated<AccountsReceivable>>(`/finance/accounts-receivable?${params.toString()}`)
    },
  })

  const summaryQuery = useQuery({
    queryKey: ['accounts-receivable-summary', appliedSearch.shopNo, appliedSearch.partnerNo, appliedSearch.fromDate, appliedSearch.toDate],
    queryFn: async () => {
      const params = new URLSearchParams()
      if (appliedSearch.shopNo != null) params.set('shopNo', String(appliedSearch.shopNo))
      if (appliedSearch.partnerNo != null) params.set('partnerNo', String(appliedSearch.partnerNo))
      if (appliedSearch.fromDate) params.set('fromDate', appliedSearch.fromDate)
      if (appliedSearch.toDate) params.set('toDate', appliedSearch.toDate)
      return api.get<AccountsReceivableSummary>(`/finance/accounts-receivable/summary?${params.toString()}`)
    },
  })

  const verifyMutation = useMutation({
    mutationFn: async (args: { row: AccountsReceivable; taxIncludedAmount: number; taxExcludedAmount: number; note: string; mfExportEnabled: boolean }) => {
      return api.put<AccountsReceivable>(
        `/finance/accounts-receivable/${pkToPath(args.row)}/verify`,
        {
          taxIncludedAmount: args.taxIncludedAmount,
          taxExcludedAmount: args.taxExcludedAmount,
          note: args.note || null,
          mfExportEnabled: args.mfExportEnabled,
        },
      )
    },
    onSuccess: () => {
      toast.success('検証を確定しました')
      setVerifyRow(null)
      invalidate()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const releaseMutation = useMutation({
    mutationFn: async (row: AccountsReceivable) => {
      return api.delete(`/finance/accounts-receivable/${pkToPath(row)}/manual-lock`)
    },
    onSuccess: () => {
      toast.success('手動確定を解除しました')
      setVerifyRow(null)
      invalidate()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const mfExportToggleMutation = useMutation({
    mutationFn: async (args: { row: AccountsReceivable; enabled: boolean }) => {
      return api.patch<AccountsReceivable>(
        `/finance/accounts-receivable/${pkToPath(args.row)}/mf-export`,
        { enabled: args.enabled },
      )
    },
    onSuccess: () => invalidate(),
    onError: (e: Error) => toast.error(e.message),
  })
  // F-W10: columns の useMemo 依存には mutation オブジェクト全体ではなく mutate 関数だけを置く。
  const mfExportToggleMutate = mfExportToggleMutation.mutate

  const aggregateMutation = useMutation({
    mutationFn: async (args: { targetDate: string; cutoffType: CutoffType }) => {
      return api.post<AggregateResponse>('/finance/accounts-receivable/aggregate', args)
    },
    onSuccess: (r) => {
      toast.success(`再集計を開始しました (cutoffType=${r.cutoffType})。完了後に再度検索してください`)
      setAggregateOpen(false)
    },
    onError: (e: Error) => {
      if (e instanceof ApiError && e.status === 403) {
        toast.error('再集計には管理者権限が必要です')
      } else {
        toast.error(e.message)
      }
    },
  })

  const bulkVerifyMutation = useMutation({
    mutationFn: async () => {
      return api.post<BulkVerifyResponse>('/finance/accounts-receivable/bulk-verify', {
        shopNo: appliedSearch.shopNo ?? null,
        fromDate: appliedSearch.fromDate,
        toDate: appliedSearch.toDate,
      })
    },
    onSuccess: (r) => {
      toast.success(
        `一括検証完了: 一致 ${r.matchedCount} / 不一致 ${r.mismatchCount} / 請求書なし ${r.notFoundCount}`
        + (r.skippedManualCount > 0 ? ` / 手動スキップ ${r.skippedManualCount}` : ''),
      )
      if (r.reconciledPartners > 0) {
        const details = r.reconciledDetails?.length ? `\n${r.reconciledDetails.slice(0, 5).join(', ')}` +
          (r.reconciledDetails.length > 5 ? ` ...他 ${r.reconciledDetails.length - 5}件` : '') : ''
        toast.info(
          `請求書の締め日に合わせて ${r.reconciledPartners} 得意先を自動再集計しました` +
          ` (旧${r.reconciledDeletedRows}件→新${r.reconciledInsertedRows}件)` +
          (r.reconciledSkippedManualPartners > 0 ? ` / 手動確定スキップ ${r.reconciledSkippedManualPartners}件` : '') +
          details,
          { duration: 10000 },
        )
      }
      setConfirmBulkVerify(false)
      invalidate()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const invalidate = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['accounts-receivable'] })
    queryClient.invalidateQueries({ queryKey: ['accounts-receivable-summary'] })
  }, [queryClient])

  const handleExportCsv = useCallback(() => {
    // F-W4: api.download で統一 (localStorage 直読 + fetch 直書きを除去)
    ;(async () => {
      try {
        const params = new URLSearchParams()
        params.set('fromDate', appliedSearch.fromDate)
        params.set('toDate', appliedSearch.toDate)
        const { blob, filename } = await api.download(
          `/finance/accounts-receivable/export-mf-csv?${params.toString()}`,
        )
        const suggest = filename ?? 'accounts_receivable_to_sales_journal.csv'
        const a = document.createElement('a')
        const url = URL.createObjectURL(blob)
        a.href = url
        a.download = suggest
        document.body.appendChild(a)
        a.click()
        a.remove()
        // revoke は非同期ダウンロード完了を待つ
        setTimeout(() => URL.revokeObjectURL(url), 1000)
        toast.success('MF CSV をダウンロードしました')
        setConfirmExport(false)
        invalidate()
      } catch (e) {
        toast.error((e as Error).message || 'CSVダウンロードに失敗しました')
      }
    })()
  }, [appliedSearch.fromDate, appliedSearch.toDate, invalidate])

  const page$ = listQuery.data ?? emptyPage<AccountsReceivable>()
  const summary = summaryQuery.data

  // テーブル内絞込 (現在のページ 50 行に対して適用)
  const [tableFilter, setTableFilter] = useState('')
  const filteredRows = useMemo(() => {
    if (!tableFilter.trim()) return page$.content
    const needle = normalizeForSearch(tableFilter.toLowerCase())
    return page$.content.filter((r) =>
      Object.values(r).some((v) =>
        normalizeForSearch(String(v ?? '').toLowerCase()).includes(needle)
      )
    )
  }, [page$.content, tableFilter])

  // 絞込後の税込/税抜/請求書金額の合計
  const tableTotals = useMemo(() => {
    let inc = 0, exc = 0, invoice = 0
    for (const r of filteredRows) {
      inc += Number(r.taxIncludedAmountChange ?? 0)
      exc += Number(r.taxExcludedAmountChange ?? 0)
      invoice += Number(r.invoiceAmount ?? 0)
    }
    return { inc, exc, invoice }
  }, [filteredRows])

  const columns: Column<AccountsReceivable>[] = useMemo(() => [
    { key: 'verificationResult', header: '検証', render: (r) => <VerificationBadge row={r} /> },
    { key: 'shopNo', header: '店舗' },
    { key: 'partnerCode', header: '得意先Code' },
    { key: 'partnerName', header: '得意先名', render: (r) => r.partnerName ?? '-' },
    { key: 'cutoffDate', header: '締め日', render: (r) => cutoffDateLabel(r.cutoffDate) },
    { key: 'transactionMonth', header: '取引日' },
    { key: 'taxRate', header: '税率', render: (r) => `${Number(r.taxRate)}%` },
    {
      key: 'taxIncludedAmountChange',
      header: '税込金額',
      render: (r) => <span className="tabular-nums">{formatCurrency(r.taxIncludedAmountChange ?? 0)}</span>,
    },
    {
      key: 'taxExcludedAmountChange',
      header: '税抜金額',
      render: (r) => <span className="tabular-nums">{formatCurrency(r.taxExcludedAmountChange ?? 0)}</span>,
    },
    {
      key: 'invoiceAmount',
      header: '請求書金額',
      render: (r) => r.invoiceAmount == null ? '-' : <span className="tabular-nums">{formatCurrency(r.invoiceAmount)}</span>,
    },
    {
      key: 'verificationDifference',
      header: '差額',
      render: (r) => {
        if (r.verificationDifference == null) return '-'
        const cls = r.verificationResult === 0 ? 'text-red-600 tabular-nums' : 'tabular-nums'
        return <span className={cls}>{formatCurrency(r.verificationDifference)}</span>
      },
    },
    {
      key: 'verifiedManually',
      header: '手動',
      render: (r) => r.verifiedManually ? <CheckCircle2 className="inline h-4 w-4 text-green-600" /> : '-',
    },
    {
      key: 'mfExportEnabled',
      header: 'MF',
      render: (r) => (
        <Switch
          checked={r.mfExportEnabled ?? false}
          onCheckedChange={(checked) => {
            mfExportToggleMutate({ row: r, enabled: checked })
          }}
          onClick={(e) => e.stopPropagation()}
        />
      ),
    },
    {
      key: 'verificationNote',
      header: '備考',
      render: (r) => r.verificationNote ? (
        <span className="block truncate max-w-[150px]" title={r.verificationNote}>{r.verificationNote}</span>
      ) : '-',
    },
    { key: 'invoiceNo', header: '請求書No', render: (r) => r.invoiceNo ?? '-' },
    // mutation の関数参照は安定 (TanStack Query の仕様)。mutation オブジェクトに依存すると
    // isPending 等が変わるたび columns が再生成されるため、mutate 関数のみに依存させる (F-W10)。
  ], [mfExportToggleMutate])

  if (listQuery.isLoading && !listQuery.data) {
    return <LoadingSpinner />
  }
  if (listQuery.error) {
    return <ErrorMessage message={(listQuery.error as Error).message} />
  }

  return (
    <div className="space-y-4">
      <PageHeader
        title="売掛金一覧"
        actions={
          <div className="flex flex-wrap gap-2">
            <Button size="sm" variant="outline" onClick={() => setAggregateOpen(true)} disabled={!isAdmin}>
              <RefreshCw className="mr-1 h-4 w-4" />再集計
            </Button>
            <Button size="sm" variant="outline" onClick={() => setInvoiceImportOpen(true)}>
              <Upload className="mr-1 h-4 w-4" />請求書取込
            </Button>
            <Button size="sm" variant="outline" onClick={() => setConfirmBulkVerify(true)}>
              <ShieldCheck className="mr-1 h-4 w-4" />一括検証
            </Button>
            <Button size="sm" onClick={() => setConfirmExport(true)}>
              <FileDown className="mr-1 h-4 w-4" />MF CSV出力
            </Button>
          </div>
        }
      />

      {/* 検索フォーム */}
      <div className="rounded border p-3 space-y-2">
        <div className="grid grid-cols-1 md:grid-cols-5 gap-2">
          {isAdmin && (
            <div>
              <Label>店舗</Label>
              <SearchableSelect
                options={(shopsData ?? []).map((s) => ({ value: String(s.shopNo), label: `${s.shopNo}: ${s.shopName}` }))}
                value={search.shopNo != null ? String(search.shopNo) : ''}
                onValueChange={(v) => setSearch((prev) => ({ ...prev, shopNo: v ? Number(v) : undefined, partnerNo: undefined }))}
                placeholder="すべて"
                clearable
              />
            </div>
          )}
          <div>
            <Label htmlFor="from-date">期間(開始)</Label>
            <Input
              id="from-date"
              type="date"
              value={search.fromDate}
              onChange={(e) => setSearch((prev) => ({ ...prev, fromDate: e.target.value }))}
            />
          </div>
          <div>
            <Label htmlFor="to-date">期間(終了)</Label>
            <Input
              id="to-date"
              type="date"
              value={search.toDate}
              onChange={(e) => setSearch((prev) => ({ ...prev, toDate: e.target.value }))}
            />
          </div>
          <div>
            <Label>得意先</Label>
            <SearchableSelect
              value={search.partnerNo != null ? String(search.partnerNo) : ''}
              onValueChange={(v) => setSearch((prev) => ({ ...prev, partnerNo: v ? Number(v) : undefined }))}
              options={partnerOptions}
              placeholder={search.shopNo == null && isAdmin ? '店舗を先に選択' : 'すべて'}
              clearable
              disabled={search.shopNo == null && isAdmin}
            />
          </div>
          <div>
            <Label htmlFor="verification-filter">検証</Label>
            <Select
              value={search.verificationFilter}
              onValueChange={(v) => setSearch((prev) => ({ ...prev, verificationFilter: v as VerificationFilter }))}
            >
              <SelectTrigger id="verification-filter">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {(Object.keys(VERIFICATION_FILTER_LABELS) as VerificationFilter[]).map((k) => (
                  <SelectItem key={k} value={k}>{VERIFICATION_FILTER_LABELS[k]}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
        <div className="flex justify-end">
          <Button size="sm" onClick={() => { setPage(0); setAppliedSearch(search) }}>検索</Button>
        </div>
      </div>

      {/* サマリアラート: 一致も含めて常に表示（未検証 or 不一致があるときだけ警告色、
          全件一致なら成功色） */}
      {summary && (summary.matchedCount + summary.unverifiedCount + summary.unmatchedCount) > 0 && (
        (() => {
          const hasIssue = summary.unverifiedCount > 0 || summary.unmatchedCount > 0
          const cls = hasIssue
            ? 'border-yellow-300 bg-yellow-50 text-yellow-900'
            : 'border-green-300 bg-green-50 text-green-900'
          const Icon = hasIssue ? AlertCircle : CheckCircle2
          return (
            <div className={`rounded border p-2 text-sm flex items-center gap-2 ${cls}`}>
              <Icon className="h-4 w-4" />
              一致 {summary.matchedCount}件 / 未検証 {summary.unverifiedCount}件 / 不一致 {summary.unmatchedCount}件
              {summary.unmatchedCount > 0 && (
                <> （差額合計 {formatCurrency(summary.unmatchedDifferenceSum)}）</>
              )}
            </div>
          )
        })()
      )}

      {/* テーブル内絞込 + 合計 + 全件表示 */}
      <div className="flex flex-wrap items-center justify-between gap-2 rounded border p-2 text-sm">
        <div className="flex items-center gap-3 flex-1 min-w-[320px] max-w-2xl">
          <Input
            placeholder={showAll ? '全件の中から絞込...' : '表示中のページ内で絞込...'}
            value={tableFilter}
            onChange={(e) => setTableFilter(e.target.value)}
            className="h-9 text-sm max-w-sm"
          />
          <label className="flex items-center gap-2 whitespace-nowrap cursor-pointer select-none">
            <Switch
              checked={showAll}
              onCheckedChange={(v) => { setShowAll(v); setPage(0) }}
            />
            <span className="text-xs">全件表示（合計検算用）</span>
          </label>
        </div>
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs">
          <span>
            表示中 <b className="tabular-nums">{filteredRows.length}</b>件
            {(tableFilter || !showAll) && (
              <span className="text-muted-foreground">
                {' '}/ {showAll ? '全件' : 'ページ内'} {page$.content.length}件
                {!showAll && <> / 総件数 {page$.totalElements}件</>}
              </span>
            )}
          </span>
          <span>税込合計 <b className="tabular-nums">{formatCurrency(tableTotals.inc)}</b></span>
          <span className="text-muted-foreground">税抜 {formatCurrency(tableTotals.exc)}</span>
          <span className="text-muted-foreground">請求書 {formatCurrency(tableTotals.invoice)}</span>
        </div>
      </div>

      {/* テーブル */}
      <DataTable
        data={filteredRows}
        columns={columns}
        rowKey={(r) => `${r.shopNo}-${r.partnerNo}-${r.transactionMonth}-${String(r.taxRate)}-${r.isOtakeGarbageBag}`}
        onRowClick={(r) => setVerifyRow(r)}
        serverPagination={{
          page: effectivePage,
          pageSize: effectivePageSize,
          totalElements: page$.totalElements,
          totalPages: showAll ? 1 : page$.totalPages,
          onPageChange: setPage,
        }}
      />

      <AccountsReceivableVerifyDialog
        row={verifyRow}
        onClose={() => setVerifyRow(null)}
        onSubmit={(inc, exc, note, mfExport) => {
          if (!verifyRow) return
          verifyMutation.mutate({ row: verifyRow, taxIncludedAmount: inc, taxExcludedAmount: exc, note, mfExportEnabled: mfExport })
        }}
        onReleaseManualLock={() => {
          if (!verifyRow) return
          releaseMutation.mutate(verifyRow)
        }}
        submitting={verifyMutation.isPending}
        releasing={releaseMutation.isPending}
        isAdmin={isAdmin}
      />

      <AccountsReceivableAggregateDialog
        open={aggregateOpen}
        onOpenChange={setAggregateOpen}
        defaultTargetDate={search.toDate}
        onSubmit={(targetDate, cutoffType) => aggregateMutation.mutate({ targetDate, cutoffType })}
        submitting={aggregateMutation.isPending}
      />

      <InvoiceImportDialog
        open={invoiceImportOpen}
        onOpenChange={(o) => {
          setInvoiceImportOpen(o)
          if (!o) invalidate()
        }}
      />

      <ConfirmDialog
        open={confirmBulkVerify}
        onOpenChange={setConfirmBulkVerify}
        title="一括検証"
        description={`期間 ${search.fromDate} ～ ${search.toDate} の売掛金を請求書と一括検証します。手動確定済みの行はスキップされます。`}
        confirmLabel={bulkVerifyMutation.isPending ? '実行中...' : '実行'}
        onConfirm={() => bulkVerifyMutation.mutate()}
      />

      <ConfirmDialog
        open={confirmExport}
        onOpenChange={setConfirmExport}
        title="MF CSV出力"
        description={`期間 ${search.fromDate} ～ ${search.toDate} の検証済み売掛金をMF CSVで出力します。`}
        confirmLabel="ダウンロード"
        onConfirm={handleExportCsv}
      />
    </div>
  )
}

function VerificationBadge({ row }: { row: AccountsReceivable }) {
  if (row.verificationResult === 1) {
    return <Badge className="bg-green-100 text-green-800 hover:bg-green-100"><CheckCircle2 className="mr-1 h-3 w-3" />一致</Badge>
  }
  if (row.verificationResult === 0) {
    return <Badge variant="destructive"><XCircle className="mr-1 h-3 w-3" />不一致</Badge>
  }
  return <Badge variant="outline">未検証</Badge>
}

function cutoffDateLabel(cutoff: number | null | undefined): string {
  if (cutoff == null) return '-'
  if (cutoff === -1) return '都度現金'
  if (cutoff === 0) return '月末'
  return `${cutoff}日`
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2532ms:
'use client'

import { useState, useEffect } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { RefreshCw } from 'lucide-react'
import { toast } from 'sonner'
import { type CutoffType, CUTOFF_TYPE_LABELS, toYyyyMmDd } from '@/types/accounts-receivable'

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  defaultTargetDate: string // yyyy-MM-dd
  onSubmit: (targetDate: string, cutoffType: CutoffType) => void
  submitting: boolean
}

/**
 * 売掛金集計バッチ起動ダイアログ。
 * 締め日タイプをラジオボタンで選択（15日/20日/月末/すべて）。
 */
export function AccountsReceivableAggregateDialog({
  open, onOpenChange, defaultTargetDate, onSubmit, submitting,
}: Props) {
  const [targetDate, setTargetDate] = useState(defaultTargetDate)
  const [cutoffType, setCutoffType] = useState<CutoffType>('all')

  useEffect(() => {
    if (open) {
      setTargetDate(defaultTargetDate)
      setCutoffType('all')
    }
  }, [open, defaultTargetDate])

  const handleSubmit = () => {
    if (!targetDate) {
      toast.error('対象日を指定してください')
      return
    }
    onSubmit(toYyyyMmDd(targetDate), cutoffType)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>売掛金 再集計</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 text-sm">
          <div>
            <Label htmlFor="target-date">対象日 (yyyy-MM-dd)</Label>
            <Input
              id="target-date"
              type="date"
              value={targetDate}
              onChange={(e) => setTargetDate(e.target.value)}
            />
            <p className="mt-1 text-xs text-muted-foreground">
              指定した日を基準に、選択した締め日タイプの期間を集計します
            </p>
          </div>
          <div>
            <Label htmlFor="cutoff-type">締め日タイプ</Label>
            <Select value={cutoffType} onValueChange={(v) => setCutoffType(v as CutoffType)}>
              <SelectTrigger id="cutoff-type" className="mt-1">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {(Object.keys(CUTOFF_TYPE_LABELS) as CutoffType[]).map((t) => (
                  <SelectItem key={t} value={t}>
                    {CUTOFF_TYPE_LABELS[t]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="rounded border border-yellow-200 bg-yellow-50 p-2 text-xs text-yellow-900">
            ※ 手動確定済み（verified_manually=true）の行は上書きされません
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>キャンセル</Button>
          <Button onClick={handleSubmit} disabled={submitting}>
            <RefreshCw className="mr-1 h-4 w-4" />
            {submitting ? '実行中...' : '再集計実行'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

2026-05-04T01:55:16.752860Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File | Select-String -Pattern B-CART | Select-Object Path,LineNumber,Line'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File | Select-String -Pattern B-CART | Select-Object Path,LineNumber,Line' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File | Select-String -Pattern B-CART | Select-Object Path,LineNumber,Line'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File | Select-String -Pattern m_partner_group | Select-Object Path,LineNumber,Line' in C:\project\odamitsu-data-hub
2026-05-04T01:55:16.754018Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File | Select-String -Pattern m_partner_group | Select-Object Path,LineNumber,Line'` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File | Select-String -Pattern m_partner_group | Select-Object Path,LineNumber,Line'` rejected: blocked by policy
2026-05-04T01:55:16.759503Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File | Select-String -Pattern invoice_partner_code | Select-Object Path,LineNumber,Line'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File | Select-String -Pattern invoice_partner_code | Select-Object Path,LineNumber,Line' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File | Select-String -Pattern invoice_partner_code | Select-Object Path,LineNumber,Line'` rejected: blocked by policy
2026-05-04T01:55:16.765056Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File | Select-String -Pattern 入金 | Select-Object Path,LineNumber,Line'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File | Select-String -Pattern 入金 | Select-Object Path,LineNumber,Line' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse -File | Select-String -Pattern 入金 | Select-Object Path,LineNumber,Line'` rejected: blocked by policy
2026-05-04T01:55:21.588058Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'cmd /c findstr /S /N /I "invoice_partner_code" *.*'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'cmd /c findstr /S /N /I "invoice_partner_code" *.*' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'cmd /c findstr /S /N /I "invoice_partner_code" *.*'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem backend/src/main/java/jp/co/oda32/domain/model/master -File' in C:\project\odamitsu-data-hub
 succeeded in 1440ms:

    Directory: C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\master

Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
-a---          2026/02/24     8:21            457 MAsana.java
-a---          2026/02/24     8:21           2554 MCompany.java
-a---          2026/02/24     8:22            583 MDeliveryPerson.java
-a---          2026/02/24     8:22           2354 MLoginUser.java
-a---          2026/03/31    19:55           1345 MMaker.java
-a---          2026/02/24     8:22           3000 MPartner.java
-a---          2026/02/24     8:22           1489 MPartnerCategory.java
-a---          2026/02/24     8:22           1711 MPaymentSupplier.java
-a---          2026/02/24     9:12           1341 MShop.java
-a---          2026/02/24     8:22           2049 MShopLinkedFile.java
-a---          2026/02/24     8:22           1271 MSmartMat.java
-a---          2026/02/24     8:22           1758 MSupplier.java
-a---          2026/02/24     8:22           1833 MSupplierShopMapping.java
-a---          2026/02/24     8:23           1517 MTaxRate.java
-a---          2026/02/24     8:23           1895 MWarehouse.java
-a---          2026/02/24     8:23           7999 WSmilePartner.java

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/master/MPartner.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem backend/src/main/java/jp/co/oda32/domain/model/finance -File' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/TInvoice.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/master/MPaymentSupplier.java' in C:\project\odamitsu-data-hub
 succeeded in 1640ms:

    Directory: C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance

Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
-a---          2026/02/24     8:22           1370 MfAccountMaster.java
-a---          2026/04/13    18:38           1040 MMfClientMapping.java
-a---          2026/04/21    10:41           1637 MMfEnumTranslation.java
-a---          2026/04/13    18:38           2456 MMfJournalRule.java
-a---          2026/04/21     8:31           2119 MMfOauthClient.java
-a---          2026/02/24     8:22            827 MMfSubAccount.java
-a---          2026/04/13    18:38           1002 MPartnerGroup.java
-a---          2026/04/14    18:49           2187 MPaymentMfRule.java
-a---          2026/04/24    13:25           2803 MSupplierOpeningBalance.java
-a---          2026/04/23    14:46           6149 TAccountsPayableSummary.java
-a---          2026/04/17    10:07           2929 TAccountsReceivableSummary.java
-a---          2026/04/13    18:38            981 TCashbookImportHistory.java
-a---          2026/04/23    15:23           1725 TConsistencyReview.java
-a---          2026/02/24     8:22           1875 TInvoice.java
-a---          2026/04/21     8:23           1203 TMfOauthState.java
-a---          2026/04/21     8:31           1970 TMfOauthToken.java
-a---          2026/04/16     8:39           3466 TPaymentMfAuxRow.java
-a---          2026/04/14    18:49           1876 TPaymentMfImportHistory.java

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1767ms:
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

 succeeded in 2705ms:
package jp.co.oda32.domain.model.master;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jp.co.oda32.constant.PaymentType;
import jp.co.oda32.domain.model.IEntity;
import jp.co.oda32.domain.validation.ShopEntity;
import lombok.*;
import org.hibernate.Hibernate;

import jakarta.persistence.*;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 得意先マスタEntity
 *
 * @author k_oda
 * @since 2018/04/11
 */
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Builder
@Entity
@AllArgsConstructor
@Table(name = "m_partner")
@ShopEntity
public class MPartner implements IEntity {
    @Id
    @Column(name = "partner_no")
    @SequenceGenerator(name = "m_partner_partner_no_seq_gen", sequenceName = "m_partner_partner_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_partner_partner_no_seq_gen")
    private Integer partnerNo;
    @Column(name = "company_no")
    private Integer companyNo;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "partner_name")
    private String partnerName;
    @Column(name = "abbreviated_partner_name")
    private String abbreviatedPartnerName;
    @Column(name = "partner_code")
    private String partnerCode;
    @Column(name = "last_order_date")
    private LocalDate lastOrderDate;
    @Column(name = "cutoff_date")
    private Integer cutoffDate;
    @Column(name = "note")
    private String note;
    @Column(name = "partner_category_code")
    private String partnerCategoryCode;
    @Column(name = "parent_partner_no")
    private Integer parentPartnerNo;
    @Column(name = "is_include_tax_display")
    private boolean isIncludeTaxDisplay;
    @Column(name = "invoice_partner_code")
    private String invoicePartnerCode;
    @Column(name = "del_flg")
    private String delFlg;
    @Column(name = "add_date_time")
    private Timestamp addDateTime;
    @Column(name = "add_user_no")
    private Integer addUserNo;
    @Column(name = "modify_date_time")
    private Timestamp modifyDateTime;
    @Column(name = "modify_user_no")
    private Integer modifyUserNo;

    @JsonIgnore
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_no", insertable = false, updatable = false)
    @ToString.Exclude
    private MCompany mCompany;

    public MCompany getCompany() {
        return this.mCompany;
    }

    /**
     * 支払タイプを取得します
     *
     * @return 支払タイプ
     */
    public PaymentType getPaymentType() {
        return PaymentType.fromCutoffCode(this.cutoffDate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        MPartner mPartner = (MPartner) o;
        return partnerNo != null && Objects.equals(partnerNo, mPartner.partnerNo);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3663ms:
package jp.co.oda32.domain.model.master;

import jp.co.oda32.domain.model.IEntity;
import jp.co.oda32.domain.validation.ShopEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 支払先マスタEntity
 *
 * @author k_oda
 * @since 2019/06/05
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_payment_supplier")
@ShopEntity
public class MPaymentSupplier implements IEntity {
    @Id
    @Column(name = "payment_supplier_no")
    @SequenceGenerator(name = "m_payment_supplier_payment_supplier_no_seq_gen", sequenceName = "m_payment_supplier_payment_supplier_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_payment_supplier_payment_supplier_no_seq_gen")
    private Integer paymentSupplierNo;
    @Column(name = "payment_supplier_code")
    private String paymentSupplierCode;
    @Column(name = "payment_supplier_name")
    private String paymentSupplierName;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "tax_timing_code")
    private BigDecimal taxTimingCode;
    @Column(name = "tax_timing")
    private String taxTiming;
    @Column(name = "cutoff_date")
    private Integer cutoffDate;

    @Column(name = "del_flg")
    private String delFlg;
    @Column(name = "add_date_time")
    private Timestamp addDateTime;
    @Column(name = "add_user_no")
    private Integer addUserNo;
    @Column(name = "modify_date_time")
    private Timestamp modifyDateTime;
    @Column(name = "modify_user_no")
    private Integer modifyUserNo;
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/MPartnerGroup.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/TAccountsPayableSummary.java' in C:\project\odamitsu-data-hub
2026-05-04T01:55:41.565772Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem backend/src/main/java/jp/co/oda32/domain/repository/finance -File | Where-Object {$_.Name -like '"'*PartnerGroup*'} | Select-Object -ExpandProperty FullName"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem backend/src/main/java/jp/co/oda32/domain/repository/finance -File | Where-Object {$_.Name -like '"'*PartnerGroup*'} | Select-Object -ExpandProperty FullName" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem backend/src/main/java/jp/co/oda32/domain/repository/finance -File | Where-Object {$_.Name -like '"'*PartnerGroup*'} | Select-Object -ExpandProperty FullName"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/order/TOrder.java' in C:\project\odamitsu-data-hub
 succeeded in 1558ms:
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

 succeeded in 1651ms:
package jp.co.oda32.domain.model.order;

import jp.co.oda32.domain.model.AbstractCompanyEntity;
import jp.co.oda32.domain.model.ICompanyEntity;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.validation.CompanyEntity;
import lombok.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 注文Entity
 *
 * @author k_oda
 * @since 2017/05/11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_order")
@CompanyEntity
public class TOrder extends AbstractCompanyEntity implements ICompanyEntity {
    @Id
    @Column(name = "order_no")
    @SequenceGenerator(name = "t_order_order_no_seq_gen", sequenceName = "t_order_order_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "t_order_order_no_seq_gen")
    private Integer orderNo;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "company_no")
    private Integer companyNo;
    @Column(name = "company_name")
    private String companyName;
    @Column(name = "order_status")
    private String orderStatus;
    @Column(name = "order_date_time")
    private LocalDateTime orderDateTime;
    @Column(name = "total_price")
    private BigDecimal totalPrice;
    @Column(name = "tax_total_price")
    private BigDecimal taxTotalPrice;
    @Column(name = "note")
    private String note;
    @Column(name = "order_route")
    private String orderRoute;
    @Column(name = "destination_no")
    private Integer destinationNo;
    @Column(name = "payment_method")
    private String paymentMethod;
    @Column(name = "b_cart_order_id")
    private Long bCartOrderId;
    @Column(name = "b_cart_order_code")
    private Long bCartOrderCode; // 受注番号 (整数, 最大255桁)
    @Column(name = "processing_serial_number") // smile処理連番
    private Long processingSerialNumber;

    @Column(name = "partner_no")
    private Integer partnerNo;
    @Column(name = "partner_code")
    private String partnerCode;

    @Column(name = "del_flg")
    private String delFlg;
    @Column(name = "add_date_time")
    private Timestamp addDateTime;
    @Column(name = "add_user_no")
    private Integer addUserNo;
    @Column(name = "modify_date_time")
    private Timestamp modifyDateTime;
    @Column(name = "modify_user_no")
    private Integer modifyUserNo;
    @OneToOne
    @JoinColumn(name = "company_no", insertable = false, updatable = false)
    private MCompany company;
    @OneToMany
    @JoinColumn(name = "order_no", insertable = false, updatable = false)
    private List<TOrderDetail> orderDetailList;
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2618ms:
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
     * 検証時の請求額（振込明細や手入力で提示された税込金額）。
     * 買掛集計(`tax_included_amount_change`)との比較元となる値で、
     * MF出力スナップショット `tax_included_amount` とは別管理。
     * 再集計バッチでは上書きされない（手動/Excel検証の結果を保持するため）。
     */
    @Column(name = "verified_amount")
    private BigDecimal verifiedAmount;

    /**
     * 振込明細 Excel 由来の税抜確定額。verifiedAmount (税込) と対で保持し、
     * MF CSV 出力の「仕入高」金額に使われる。null の場合は自社計算 (taxExcludedAmountChange) を使用。
     * V026 で追加 (2026-04-23)。
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

2026-05-04T01:55:48.784519Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$i=0; Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/TAccountsReceivableSummary.java | ForEach-Object { $i++; if($i -ge 20 -and $i -le 90){ '"'{0}:{1}' -f "'$i, $_ } }'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$i=0; Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/TAccountsReceivableSummary.java | ForEach-Object { $i++; if($i -ge 20 -and $i -le 90){ '"'{0}:{1}' -f "'$i, $_ } }' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$i=0; Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/TAccountsReceivableSummary.java | ForEach-Object { $i++; if($i -ge 20 -and $i -le 90){ '"'{0}:{1}' -f "'$i, $_ } }'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/TAccountsReceivableSummary.java | Select-Object -First 90' in C:\project\odamitsu-data-hub
 succeeded in 1492ms:
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
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/TAccountsReceivableSummary.java | Select-Object -Skip 90 -First 25' in C:\project\odamitsu-data-hub
 succeeded in 1470ms:
    @Column(name = "verification_difference")
    private BigDecimal verificationDifference;

    // 突合した請求書ID（t_invoice.invoice_id, 監査用）
    @Column(name = "invoice_no")
    private Integer invoiceNo;
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/dto/finance/AccountsReceivableResponse.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/dto/finance/AccountsReceivableBulkVerifyResponse.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/dto/finance/AccountsReceivableBulkVerifyRequest.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/TPaymentMfImportHistory.java' in C:\project\odamitsu-data-hub
 succeeded in 1572ms:
package jp.co.oda32.dto.finance;

import com.fasterxml.jackson.annotation.JsonProperty;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.domain.model.master.MPartner;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 売掛金一覧画面の一行DTO。
 * 買掛側 {@link AccountsPayableResponse} と対称。
 */
@Data
@Builder
public class AccountsReceivableResponse {
    private Integer shopNo;
    private Integer partnerNo;
    private String partnerCode;
    private String partnerName;
    private LocalDate transactionMonth;
    private BigDecimal taxRate;

    /**
     * 大竹市ゴミ袋フラグ。
     * <p>{@code @JsonProperty("isOtakeGarbageBag")} は Lombok 生成の {@code isOtakeGarbageBag()} getter を
     * Jackson がデフォルトで {@code "otakeGarbageBag"} にマッピングしてしまう問題への対策。
     * フロントエンドは {@code isOtakeGarbageBag} として読むため明示指定する。
     */
    @JsonProperty("isOtakeGarbageBag")
    private boolean isOtakeGarbageBag;

    private Integer cutoffDate;
    private Integer orderNo;

    private BigDecimal taxIncludedAmount;
    private BigDecimal taxExcludedAmount;
    private BigDecimal taxIncludedAmountChange;
    private BigDecimal taxExcludedAmountChange;

    private BigDecimal invoiceAmount;
    private BigDecimal verificationDifference;
    private Integer invoiceNo;

    private Integer verificationResult;
    private Boolean mfExportEnabled;
    private Boolean verifiedManually;
    private String verificationNote;

    public static AccountsReceivableResponse from(TAccountsReceivableSummary ar, MPartner partner) {
        return AccountsReceivableResponse.builder()
                .shopNo(ar.getShopNo())
                .partnerNo(ar.getPartnerNo())
                .partnerCode(ar.getPartnerCode())
                .partnerName(partner != null ? partner.getPartnerName() : null)
                .transactionMonth(ar.getTransactionMonth())
                .taxRate(ar.getTaxRate())
                .isOtakeGarbageBag(ar.isOtakeGarbageBag())
                .cutoffDate(ar.getCutoffDate())
                .orderNo(ar.getOrderNo())
                .taxIncludedAmount(ar.getTaxIncludedAmount())
                .taxExcludedAmount(ar.getTaxExcludedAmount())
                .taxIncludedAmountChange(ar.getTaxIncludedAmountChange())
                .taxExcludedAmountChange(ar.getTaxExcludedAmountChange())
                .invoiceAmount(ar.getInvoiceAmount())
                .verificationDifference(ar.getVerificationDifference())
                .invoiceNo(ar.getInvoiceNo())
                .verificationResult(ar.getVerificationResult())
                .mfExportEnabled(ar.getMfExportEnabled())
                .verifiedManually(ar.getVerifiedManually())
                .verificationNote(ar.getVerificationNote())
                .build();
    }

    public static AccountsReceivableResponse from(TAccountsReceivableSummary ar) {
        return from(ar, null);
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1670ms:
package jp.co.oda32.dto.finance;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 売掛金一括検証のレスポンス。
 */
@Data
@Builder
public class AccountsReceivableBulkVerifyResponse {
    private int matchedCount;
    private int mismatchCount;
    private int notFoundCount;
    private int skippedManualCount;
    private int josamaOverwriteCount;
    private int quarterlySpecialCount;

    /** 請求書の closing_date に合わせて自動再集計した得意先件数。 */
    private int reconciledPartners;
    /** 再集計で削除された旧 AR 行の件数。 */
    private int reconciledDeletedRows;
    /** 再集計で新規挿入された AR 行の件数。 */
    private int reconciledInsertedRows;
    /** 手動確定済のため再集計をスキップした得意先件数。 */
    private int reconciledSkippedManualPartners;
    /** 再集計された得意先の一覧（UI で確認用、"partner_code (YYYY-MM: 月末)" 形式）。 */
    private List<String> reconciledDetails;
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2553ms:
package jp.co.oda32.domain.model.finance;

import jakarta.persistence.*;
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

    @Column(name = "csv_body")
    private byte[] csvBody;

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

 succeeded in 3463ms:
package jp.co.oda32.dto.finance;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 売掛金の一括検証リクエスト（画面の検索条件範囲を対象）。
 */
@Data
public class AccountsReceivableBulkVerifyRequest {
    private Integer shopNo;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
**Cluster E 批判的レビュー**

### 1. 売掛金テーブルが「台帳」ではなく「現在値キャッシュ」になっている
- Severity: Critical
- カテゴリ: 設計選択 / 業務整合
- 内容: [TAccountsReceivableSummary.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/TAccountsReceivableSummary.java) は、集計値、検証結果、手動確定値、MF出力可否、請求書突合結果を同一行の現在値として保持している。これでは「いつ、誰が、どの根拠で、なぜMF出力対象にしたか」が履歴として残らない。月次締め後の再集計や請求書訂正が発生すると、過去の判断過程を復元できない。
- 推奨アクション: `t_ar_verification_event` / `t_ar_export_lot` / `t_ar_export_line` のようなイベント・ロット・明細を分離し、summary は派生ビューまたは最新状態に限定する。

### 2. 「売掛金」と言いながら売上発生額だけを見ており、残高管理になっていない
- Severity: Critical
- カテゴリ: 設計選択 / 業務整合
- 内容: [TInvoice.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/TInvoice.java) には `previousBalance`, `totalPayment`, `carryOverBalance`, `currentBillingAmount` があるが、[InvoiceVerifier.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java) は主に `netSalesIncludingTax` だけを突合している。これは売上帳CSVには有効だが、売掛金残高や入金消込の正しさは検証していない。画面名が「売掛金一覧」だと、運用者は残高・回収状況まで保証されていると誤解する。
- 推奨アクション: 「売上仕訳連携」と「売掛残高・入金消込」を明確に分ける。AR台帳として扱うなら、前回残高 + 当月売上 - 入金 = 繰越残高の検証を必須にする。

### 3. `invoice_partner_code` と `m_partner_group` の役割が二重化している
- Severity: Major
- カテゴリ: 設計選択
- 内容: AR集計は [MPartner.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/master/MPartner.java) の `invoicePartnerCode` で請求先集約している。一方で [MPartnerGroup.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/MPartnerGroup.java) も partner code のグルーピングを持つ。どちらが請求単位、どちらがMF補助科目・表示単位なのかがコード上で分離されていないため、グループ変更時にARとMFマスタの意味がずれる。
- 推奨アクション: `invoice_partner_code` は請求・突合単位、`m_partner_group` は会計/表示/分析単位など、責務を設計書とDB制約で固定する。可能なら請求先を独立エンティティ化する。

### 4. 締め日の履歴を持たないため、過去月再集計が現在マスタに汚染される
- Severity: Major
- カテゴリ: エッジケース
- 内容: [TAccountsReceivableSummaryTasklet.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/batch/finance/TAccountsReceivableSummaryTasklet.java) は実行時点の `m_partner.cutoff_date` を使って期間を切っている。得意先が 15日締めから20日締めに変わった場合、過去月を再集計すると当時の締め条件ではなく現在条件で再計算される。`t_invoice.closing_date` に寄せる Reconciler は救済策だが、締め条件の有効開始日・終了日を表す正規データではない。
- 推奨アクション: `m_partner_billing_terms_history` のような有効日付き締め条件を持つ。過去月再集計は「対象期間日時点の契約条件」を参照する。

### 5. `mfExportEnabled` の判定責務が散在し、説明可能な状態遷移になっていない
- Severity: Major
- カテゴリ: 設計選択 / 業務整合
- 内容: `mfExportEnabled` は [InvoiceVerifier.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java)、[TAccountsReceivableSummaryService.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsReceivableSummaryService.java)、Reconciler、UIスイッチ、CSV抽出条件の複数箇所で意味付けされている。結果として「自動一致だからON」「手動でON」「特殊運用でON」「再集計でOFF」が同じ boolean に潰れる。監査時に、出力対象になった理由と責任者を説明できない。
- 推奨アクション: `export_decision_status` と `export_decision_reason` を導入し、状態遷移を単一サービスに集約する。例: `AUTO_MATCHED`, `MANUAL_APPROVED`, `SPECIAL_RULE_APPROVED`, `BLOCKED_MISMATCH`。

### 6. Sales Journal CSV をDBから再生成可能にするには、現在のsummary参照では足りない
- Severity: Major
- カテゴリ: 代替案
- 内容: [SalesJournalCsvService.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/SalesJournalCsvService.java) は出力時点のAR行とMF勘定科目マスタからCSVを組み立てる。後日、補助科目マスタ、得意先名、部門ルール、税区分ルールが変わると、同じ期間を再生成しても当時のCSVと一致しない。DBから再生成可能にするなら「元データ」ではなく「生成済み仕訳行」の固定が必要。
- 推奨アクション: CSV生成時に `t_ar_sales_journal_export_lot` と `t_ar_sales_journal_export_line` を作り、借方/貸方/摘要/金額/ルール版を保存する。CSVはその固定明細から何度でも再出力する。

### 7. 月次締め後の訂正請求を「差し替え」で扱う設計になっている
- Severity: Major
- カテゴリ: エッジケース
- 内容: 現状は再集計・再検証で同じsummary行の `*_change`, `invoiceAmount`, `verificationResult` を更新する設計に寄っている。締め後に得意先から訂正請求、返品、値引が来た場合、過去月を更新するのか、当月に調整仕訳を切るのかがモデルに存在しない。会計上は過去CSVを再出力するより、訂正月に差額仕訳を残す方が監査しやすい。
- 推奨アクション: 締め済み期間は immutable にし、訂正は `adjustment` 行として別明細化する。元請求・訂正請求・調整仕訳をリンクするキーを持たせる。

### 8. 入金消込の追跡可能性がない
- Severity: Critical
- カテゴリ: 業務整合
- 内容: `TInvoice` には `totalPayment` と `paymentDate` があるが、入金額がどの請求書・どの売掛明細に充当されたかを表すテーブルが見当たらない。複数請求を一括入金、手数料控除、過入金、相殺があると、請求書単位の回収状況を説明できない。売掛金管理で最も監査されやすい「この入金は何を消したのか」が空白になっている。
- 推奨アクション: `t_receipt`, `t_receipt_allocation` を追加し、入金元、根拠ファイル、操作ユーザ、按分先請求書、差額理由を保持する。

### 9. キャンセル・返品・値引が純売上にネットされ、会計処理の選択肢を失っている
- Severity: Major
- カテゴリ: エッジケース
- 内容: `InvoiceVerifier` は請求書の `netSalesIncludingTax` とAR集計額の一致に寄せているため、キャンセル・返品・値引が同月内に混ざると純額だけが残る。MF仕訳では売上高のマイナスでよいのか、売上値引・返品勘定に分けるのかという会計方針が表現されない。設計書では値引はPhase 1スコープ外だが、同月内発生分は既に純売上に混入する。
- 推奨アクション: 注文明細の符号・理由・伝票種別をAR明細に保持し、CSV変換時に勘定科目を分岐できるようにする。少なくとも純額化された値引額を検出して警告する。

### 10. B-CART / shop=1 統合の境界がAR集計キーに表れていない
- Severity: Major
- カテゴリ: エッジケース
- 内容: AR集計は注文詳細の `shopNo` と請求先コードをキーにしており、`InvoiceVerifier` の shop=1 強制はクリーンラボだけに限定されている。第1事業部得意先のB-CART注文が shop=1 に統合される運用があるなら、請求書側 shop と注文側 shop の対応を個別特殊コードで吸収するのは危険。店舗横断の請求単位が増えるたびに、突合漏れまたは二重集計が発生する。
- 推奨アクション: `billing_shop_no` / `source_shop_no` / `accounting_shop_no` を分けるマッピングを導入する。特殊得意先コードではなく、請求単位マスタで制御する。

### 11. bulkVerify は同期APIではなく、監査可能なジョブにすべき
- Severity: Major
- カテゴリ: 代替案
- 内容: [AccountsReceivableController.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java) の `bulkVerify` は検証、再集計、保存をHTTPリクエスト内で実行する。結果DTOは返るが、実行者、対象条件、開始終了時刻、入力請求書版、件数サマリが永続化されない。検証処理は会計判断なので、単なる画面操作ではなくジョブ履歴として残すべき。
- 推奨アクション: `bulkVerify` を非同期ジョブ化し、`t_ar_verification_job` に条件・実行者・結果・ログを保存する。画面はジョブ履歴と差分結果を確認する形にする。

### 12. CutoffReconciler は「締め確定」操作として独立させるべき
- Severity: Major
- カテゴリ: 代替案
- 内容: Reconciler は請求書締め日を正としてAR行を作り直す強い操作であり、検証の前処理ではない。これは「この得意先のこの月の締め日は請求書に合わせて確定する」という業務判断に近い。暗黙起動の問題自体は既存レビューにあるが、より根本的には責務が検証APIに混ざっている。
- 推奨アクション: `締め差異検出 → 差異一覧 → 締め確定 → 再集計 → 検証` の明示フローに分ける。締め確定結果は有効日付き締め履歴へ反映する。

### 13. 買掛金との「対称構造」を優先しすぎている
- Severity: Major
- カテゴリ: 設計選択
- 内容: 買掛は支払先・支払予定・Excel振込明細との突合が中心だが、売掛は請求先階層、締め日変更、入金消込、訂正請求、回収残高が中心になる。[TAccountsPayableSummary.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/TAccountsPayableSummary.java) とARを似せると、UIやDTOは揃うが業務上必要な軸が欠落する。ARはAPの左右反転ではなく、請求・回収ライフサイクルとして設計すべき。
- 推奨アクション: 画面構造の対称性は維持してよいが、データモデルは `請求`, `売上発生`, `入金`, `消込`, `調整`, `MF出力` に分解する。

**Opus が見落としていた最重要 3 件**

1. 売掛金管理として最重要の入金消込テーブルがなく、複数請求への入金按分を追跡できない。
2. `t_invoice` の残高系項目を使わず純売上だけを突合しており、AR残高の正しさを保証していない。
3. CSV再生成可能性にはsummaryではなく、生成済み仕訳ロット・仕訳明細の固定保存が必要。
**Cluster E 批判的レビュー**

### 1. 売掛金テーブルが「台帳」ではなく「現在値キャッシュ」になっている
- Severity: Critical
- カテゴリ: 設計選択 / 業務整合
- 内容: [TAccountsReceivableSummary.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/TAccountsReceivableSummary.java) は、集計値、検証結果、手動確定値、MF出力可否、請求書突合結果を同一行の現在値として保持している。これでは「いつ、誰が、どの根拠で、なぜMF出力対象にしたか」が履歴として残らない。月次締め後の再集計や請求書訂正が発生すると、過去の判断過程を復元できない。
- 推奨アクション: `t_ar_verification_event` / `t_ar_export_lot` / `t_ar_export_line` のようなイベント・ロット・明細を分離し、summary は派生ビューまたは最新状態に限定する。

### 2. 「売掛金」と言いながら売上発生額だけを見ており、残高管理になっていない
- Severity: Critical
- カテゴリ: 設計選択 / 業務整合
- 内容: [TInvoice.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/TInvoice.java) には `previousBalance`, `totalPayment`, `carryOverBalance`, `currentBillingAmount` があるが、[InvoiceVerifier.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java) は主に `netSalesIncludingTax` だけを突合している。これは売上帳CSVには有効だが、売掛金残高や入金消込の正しさは検証していない。画面名が「売掛金一覧」だと、運用者は残高・回収状況まで保証されていると誤解する。
- 推奨アクション: 「売上仕訳連携」と「売掛残高・入金消込」を明確に分ける。AR台帳として扱うなら、前回残高 + 当月売上 - 入金 = 繰越残高の検証を必須にする。

### 3. `invoice_partner_code` と `m_partner_group` の役割が二重化している
- Severity: Major
- カテゴリ: 設計選択
- 内容: AR集計は [MPartner.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/master/MPartner.java) の `invoicePartnerCode` で請求先集約している。一方で [MPartnerGroup.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/MPartnerGroup.java) も partner code のグルーピングを持つ。どちらが請求単位、どちらがMF補助科目・表示単位なのかがコード上で分離されていないため、グループ変更時にARとMFマスタの意味がずれる。
- 推奨アクション: `invoice_partner_code` は請求・突合単位、`m_partner_group` は会計/表示/分析単位など、責務を設計書とDB制約で固定する。可能なら請求先を独立エンティティ化する。

### 4. 締め日の履歴を持たないため、過去月再集計が現在マスタに汚染される
- Severity: Major
- カテゴリ: エッジケース
- 内容: [TAccountsReceivableSummaryTasklet.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/batch/finance/TAccountsReceivableSummaryTasklet.java) は実行時点の `m_partner.cutoff_date` を使って期間を切っている。得意先が 15日締めから20日締めに変わった場合、過去月を再集計すると当時の締め条件ではなく現在条件で再計算される。`t_invoice.closing_date` に寄せる Reconciler は救済策だが、締め条件の有効開始日・終了日を表す正規データではない。
- 推奨アクション: `m_partner_billing_terms_history` のような有効日付き締め条件を持つ。過去月再集計は「対象期間日時点の契約条件」を参照する。

### 5. `mfExportEnabled` の判定責務が散在し、説明可能な状態遷移になっていない
- Severity: Major
- カテゴリ: 設計選択 / 業務整合
- 内容: `mfExportEnabled` は [InvoiceVerifier.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java)、[TAccountsReceivableSummaryService.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/TAccountsReceivableSummaryService.java)、Reconciler、UIスイッチ、CSV抽出条件の複数箇所で意味付けされている。結果として「自動一致だからON」「手動でON」「特殊運用でON」「再集計でOFF」が同じ boolean に潰れる。監査時に、出力対象になった理由と責任者を説明できない。
- 推奨アクション: `export_decision_status` と `export_decision_reason` を導入し、状態遷移を単一サービスに集約する。例: `AUTO_MATCHED`, `MANUAL_APPROVED`, `SPECIAL_RULE_APPROVED`, `BLOCKED_MISMATCH`。

### 6. Sales Journal CSV をDBから再生成可能にするには、現在のsummary参照では足りない
- Severity: Major
- カテゴリ: 代替案
- 内容: [SalesJournalCsvService.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/SalesJournalCsvService.java) は出力時点のAR行とMF勘定科目マスタからCSVを組み立てる。後日、補助科目マスタ、得意先名、部門ルール、税区分ルールが変わると、同じ期間を再生成しても当時のCSVと一致しない。DBから再生成可能にするなら「元データ」ではなく「生成済み仕訳行」の固定が必要。
- 推奨アクション: CSV生成時に `t_ar_sales_journal_export_lot` と `t_ar_sales_journal_export_line` を作り、借方/貸方/摘要/金額/ルール版を保存する。CSVはその固定明細から何度でも再出力する。

### 7. 月次締め後の訂正請求を「差し替え」で扱う設計になっている
- Severity: Major
- カテゴリ: エッジケース
- 内容: 現状は再集計・再検証で同じsummary行の `*_change`, `invoiceAmount`, `verificationResult` を更新する設計に寄っている。締め後に得意先から訂正請求、返品、値引が来た場合、過去月を更新するのか、当月に調整仕訳を切るのかがモデルに存在しない。会計上は過去CSVを再出力するより、訂正月に差額仕訳を残す方が監査しやすい。
- 推奨アクション: 締め済み期間は immutable にし、訂正は `adjustment` 行として別明細化する。元請求・訂正請求・調整仕訳をリンクするキーを持たせる。

### 8. 入金消込の追跡可能性がない
- Severity: Critical
- カテゴリ: 業務整合
- 内容: `TInvoice` には `totalPayment` と `paymentDate` があるが、入金額がどの請求書・どの売掛明細に充当されたかを表すテーブルが見当たらない。複数請求を一括入金、手数料控除、過入金、相殺があると、請求書単位の回収状況を説明できない。売掛金管理で最も監査されやすい「この入金は何を消したのか」が空白になっている。
- 推奨アクション: `t_receipt`, `t_receipt_allocation` を追加し、入金元、根拠ファイル、操作ユーザ、按分先請求書、差額理由を保持する。

### 9. キャンセル・返品・値引が純売上にネットされ、会計処理の選択肢を失っている
- Severity: Major
- カテゴリ: エッジケース
- 内容: `InvoiceVerifier` は請求書の `netSalesIncludingTax` とAR集計額の一致に寄せているため、キャンセル・返品・値引が同月内に混ざると純額だけが残る。MF仕訳では売上高のマイナスでよいのか、売上値引・返品勘定に分けるのかという会計方針が表現されない。設計書では値引はPhase 1スコープ外だが、同月内発生分は既に純売上に混入する。
- 推奨アクション: 注文明細の符号・理由・伝票種別をAR明細に保持し、CSV変換時に勘定科目を分岐できるようにする。少なくとも純額化された値引額を検出して警告する。

### 10. B-CART / shop=1 統合の境界がAR集計キーに表れていない
- Severity: Major
- カテゴリ: エッジケース
- 内容: AR集計は注文詳細の `shopNo` と請求先コードをキーにしており、`InvoiceVerifier` の shop=1 強制はクリーンラボだけに限定されている。第1事業部得意先のB-CART注文が shop=1 に統合される運用があるなら、請求書側 shop と注文側 shop の対応を個別特殊コードで吸収するのは危険。店舗横断の請求単位が増えるたびに、突合漏れまたは二重集計が発生する。
- 推奨アクション: `billing_shop_no` / `source_shop_no` / `accounting_shop_no` を分けるマッピングを導入する。特殊得意先コードではなく、請求単位マスタで制御する。

### 11. bulkVerify は同期APIではなく、監査可能なジョブにすべき
- Severity: Major
- カテゴリ: 代替案
- 内容: [AccountsReceivableController.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/api/finance/AccountsReceivableController.java) の `bulkVerify` は検証、再集計、保存をHTTPリクエスト内で実行する。結果DTOは返るが、実行者、対象条件、開始終了時刻、入力請求書版、件数サマリが永続化されない。検証処理は会計判断なので、単なる画面操作ではなくジョブ履歴として残すべき。
- 推奨アクション: `bulkVerify` を非同期ジョブ化し、`t_ar_verification_job` に条件・実行者・結果・ログを保存する。画面はジョブ履歴と差分結果を確認する形にする。

### 12. CutoffReconciler は「締め確定」操作として独立させるべき
- Severity: Major
- カテゴリ: 代替案
- 内容: Reconciler は請求書締め日を正としてAR行を作り直す強い操作であり、検証の前処理ではない。これは「この得意先のこの月の締め日は請求書に合わせて確定する」という業務判断に近い。暗黙起動の問題自体は既存レビューにあるが、より根本的には責務が検証APIに混ざっている。
- 推奨アクション: `締め差異検出 → 差異一覧 → 締め確定 → 再集計 → 検証` の明示フローに分ける。締め確定結果は有効日付き締め履歴へ反映する。

### 13. 買掛金との「対称構造」を優先しすぎている
- Severity: Major
- カテゴリ: 設計選択
- 内容: 買掛は支払先・支払予定・Excel振込明細との突合が中心だが、売掛は請求先階層、締め日変更、入金消込、訂正請求、回収残高が中心になる。[TAccountsPayableSummary.java](C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/TAccountsPayableSummary.java) とARを似せると、UIやDTOは揃うが業務上必要な軸が欠落する。ARはAPの左右反転ではなく、請求・回収ライフサイクルとして設計すべき。
- 推奨アクション: 画面構造の対称性は維持してよいが、データモデルは `請求`, `売上発生`, `入金`, `消込`, `調整`, `MF出力` に分解する。

**Opus が見落としていた最重要 3 件**

1. 売掛金管理として最重要の入金消込テーブルがなく、複数請求への入金按分を追跡できない。
2. `t_invoice` の残高系項目を使わず純売上だけを突合しており、AR残高の正しさを保証していない。
3. CSV再生成可能性にはsummaryではなく、生成済み仕訳ロット・仕訳明細の固定保存が必要。
tokens used
107,430
