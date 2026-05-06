OpenAI Codex v0.128.0 (research preview)
--------
workdir: C:\project\odamitsu-data-hub
model: gpt-5.5
provider: openai
approval: never
sandbox: read-only
reasoning effort: none
reasoning summaries: none
session id: 019dfbc8-a4a4-74e0-a22a-64edbf98e2fa
--------
user
あなたはテスト戦略・QA・開発運用の上級レビュアーです。
小田光データ連携基盤 (`C:/project/odamitsu-data-hub`) で実装された **テスト/ガイドライン系 1 件** を批判的にレビューしてください。

## レビュー対象 (Group 4: テスト / ガイドライン = T3 案 D'')

### T3 案 D'': DEFER (マスタ履歴化) + 開発運用ガイドライン + ゴールデンマスタ拡充
- 新規: `claudedocs/runbook-finance-recalc-impact-analysis.md` (8 章)
- enabled: `PaymentMfImportServiceGoldenMasterTest.java` (2 ケース、@Disabled 解除)
- enabled: `PaymentMfImportServiceAuxRowTest.java` (2 ケース、@Disabled 解除)
- 新規: `PayableMonthlyAggregatorGoldenTest.java` (14 ケース、6 シナリオ Nested)
- 新規: `SupplierBalancesServiceGoldenTest.java` (8 ケース、6 シナリオ)

### 経緯
- T3 元提案 (マスタ履歴化) はユーザー実需と乖離 → DEFER
- 真の問題: 「改修・試行錯誤での集計ロジック変更」による過去月再計算の不整合
- 解決策: ゴールデンマスタテストで意図外変動を検知 + 改修時手順 runbook で運用

## 批判的レビュー観点

### Runbook 系
1. 改修標準手順 (Step 1-8) の現実性: 経理改修ごとに毎回 8 ステップ実施は workload 大きすぎないか?
2. Step 3-5 の API スナップショット取得方式は admin 認証が必要 → CI 自動化が困難。CI/CD で機械的にチェックする手段は?
3. Step 7 の差分判定 (意図通り / 副作用 / 期間外波及) で「意図通りか副作用か」の判定基準が主観的 → 自動判定基準の追加余地
4. Step 8 「過去確定月の保護ルール」が runbook 推奨レベル → DB レベル制約 (例: 確定済み月は UPDATE 禁止 trigger) の実装余地

### ゴールデンマスタテスト系 (重点)
5. **PayableMonthlyAggregatorGoldenTest 14 ケース** の Scenario 1-6 で **網羅性は十分か?** (= Codex 想定の edge case でカバーされていないものは?)
   - 単一/税率混在/payment-only/MF debit auto/MF debit manual protect/SupplierAgg
   - 不足: closing balance の符号 (= 残高がマイナスになるケース)、税率変更月跨ぎ、決算期跨ぎ
6. **SupplierBalancesServiceGoldenTest 8 ケース** の網羅性
   - MATCH/MINOR/MAJOR/EDGE/MF_MISSING/SELF_MISSING/opening 注入/期首残仕訳除外
   - 不足: 同 supplier 複数 sub_account hit、MF 未マッチ supplier (unmatchedSuppliers)、cumulative 巻き戻し
7. **MfOpeningJournalDetector の判定が過敏** (credit-only 買掛金仕訳を全て opening 判定) という発見 → 本体ロジックの妥当性が疑問
8. fixture 形式 (JSON / Java インライン / external file?) の選択妥当性
9. 改修時の fixture 更新フロー → user 環境作業が必要なテスト (PaymentMfImport) と完全 unit test (Aggregator) の **混在** で開発者が混乱しないか?

### CI 統合系
10. `.github/workflows/` 不在を確認 → CI/CD パイプライン未整備で「ゴールデンマスタテストが実行されない」リスクが残る? `gradlew test` は手動実行のみ?
11. ゴールデンマスタテストの fail 時の通知メカニズム (= 誰が気付くか) は?

### MfOpeningJournalDetector 過敏判定の波及
12. T3 で発覚した「Detector 過敏判定」は **本来なら別 task で修正すべき Critical**。SupplierBalancesService テスト fixture で workaround しているが、本番運用で誤判定発生時の影響範囲は?
13. workaround (`payableJournal` helper の dummy debit branch 追加) が将来の改修で取れた場合、再発リスク

### 開発運用 (DX) 系
14. runbook §5 PaymentMfImport fixture 更新手順が user 環境作業 (実 Excel 取込) → 開発者交代時のオンボーディング難易度
15. テスト fail 時の修正コスト: golden master が落ちた時、開発者は「期待値が古い」のか「コードバグ」のかを瞬時に判断できる?
16. T3 DEFER の判断 (マスタ履歴化不要) を後悔しないために、将来マスタ変更が発生した時の決断ポイント (= rerun T3 議論する trigger)

### 設計書 vs 実装の乖離
17. runbook で「ゴールデンマスタは CI で自動実行」と書いているが CI 未整備 → ドキュメント-実装乖離

## 出力フォーマット

Markdown、日本語、簡潔に (max 400 行、Group 4 は規模小)。各指摘は:
- Severity: Critical / Major / Minor
- カテゴリ: テスト網羅 / Runbook / CI / DX / Detector
- 内容 (3-5 文)
- 推奨アクション (1-2 文)

最後に「Opus 実装で見落としていた最重要 3 件」を明記。


codex
`using-superpowers` と、レビュー依頼なので該当するテスト/レビュー系スキルを確認してから進めます。まず対象ファイルとテスト構成を読んで、実装とドキュメントの食い違いを優先して見ます。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path C:\\Users\\kazuki\\.agents\\skills\\using-superpowers\\SKILL.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path . -Force | Select-Object Name,Mode,Length' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path C:\\Users\\kazuki\\.agents\\skills\\requesting-code-review\\SKILL.md" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path . -Force | Select-Object Name,Mode,Length'` rejected: blocked by policy
2026-05-06T05:35:32.672765Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path . -Force | Select-Object Name,Mode,Length'` rejected: blocked by policy
 succeeded in 7398ms:
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

 succeeded in 8345ms:
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
レビュー用のサブエージェント手順はスキル側にありますが、このセッションではユーザーが並列エージェントを明示していないため使いません。ここからはローカルで差分、対象テスト、runbook、CI 有無を直接確認します。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'rg --files -g "*.java" -g "*.md" -g "*.yml" -g "*.yaml"' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git status --short' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git log --oneline -5' in C:\project\odamitsu-data-hub
 succeeded in 5633ms:
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

2026-05-06T05:35:56.283111Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 8.4 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg --files -g "*.java" -g "*.md" -g "*.yml" -g "*.yaml"
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 exited 1 in 8357ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg --files -g "*.java" -g "*.md" -g "*.yml" -g "*.yaml"
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 succeeded in 9112ms:
4ca40fb feat(finance): paymentSettled を MF journal debit 由来に切替 (案 A)
3561d3c feat(finance): 整合性レポート差分確認機能 (案 X+Y、V027) + UI 改善
ae82913 feat(finance): 買掛帳の MF 比較を累積残 + 期間明示で強化
fb29a4f feat(finance): 振込明細 Excel の税抜確定額と自動調整額を DB 保持 (V026)
ddfc303 feat(finance): supplier 累積残一覧 + MF 連携ヘルスチェック (軸 D+E)
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
`rg` はこの環境に入っていないため、PowerShell のファイル列挙で代替します。作業ツリーはかなり大きく汚れているので、今回の対象ファイルに絞ってレビューします。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path claudedocs\\runbook-finance-recalc-impact-analysis.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\test\\java\\jp\\co\\oda32\\domain\\service\\finance\\PayableMonthlyAggregatorGoldenTest.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\test\\java\\jp\\co\\oda32\\domain\\service\\finance\\SupplierBalancesServiceGoldenTest.java" in C:\project\odamitsu-data-hub
 succeeded in 1695ms:
# Runbook: Finance 集計ロジック改修 影響範囲分析

作成日: 2026-05-06
対象: 経理関連の集計 Service / Calculator / Tasklet 改修時 (T3 案 D'' = T3 DEFER + 開発運用ガイドライン + ゴールデンマスタ拡充)
関連: T3 設計検討、P1-03 案 D-2 (PaymentMfImport supplier 別 attribute 展開)

---

## 1. 目的

集計ロジックの改修・試行錯誤で **過去確定月の集計値が意図せず変動する問題** を防ぐ。

具体的には、以下のような事故を CI レベルで検知し、merge 前に手戻りさせる:

- `PayableMonthlyAggregator.applyPaymentSettled` の按分式変更で過去 6 ヶ月の `payment_amount_settled_*` が変わる
- `SupplierBalancesService.accumulateMfJournals` の journal フィルタ変更で全 supplier の MF debit 累積が ±10?30 円ずれる
- `PaymentMfImportService` の振込手数料値引按分ロジック変更で過去 CSV と差分が出る
- `PayableBalanceCalculator.effectiveChangeTaxIncluded` の手動確定行優先ロジック変更で過去月の closing が変動する

---

## 2. T3 (マスタ履歴化) との関係

| 観点 | T3 (マスタ履歴化) | 本 runbook |
|---|---|---|
| 解決対象 | マスタ変更 (税率/勘定科目/仕入先紐付け 等) による過去月再集計の不整合 | **改修ロジック変更** による過去月再集計の不整合 |
| 必要性 | 実需 (マスタの過去変更頻度) が低く DEFER | 改修頻度が高いため即時必要 |
| 実装方式 | マスタテーブルへの SCD Type 2 + as-of 引き当て | 開発運用ガイドライン + ゴールデンマスタ単体テスト |
| 本プロジェクト方針 | DEFER (実需発生まで保留) | **本 runbook で代替** |

両者は独立した問題。本プロジェクトでは T3 は DEFER し、本 runbook で対処する。マスタ変更起因の集計ずれが実運用で観測されたら T3 を再検討する。

---

## 3. 改修時の標準手順

### Step 1: 改修対象の特定

改修対象の Service / Calculator / Tasklet を列挙:

- 計算式 (合計、按分、税率逆算、累積)
- 集計範囲 (期間、フィルタ条件、`isPaymentOnly` 等のマーカー)
- 出力形式 (DTO 構造、フィールド)

代表的な対象:

| 種別 | クラス |
|---|---|
| Aggregator | `PayableMonthlyAggregator`, `MfPaymentAggregator` |
| Calculator | `PayableBalanceCalculator`, `MfTaxResolver` |
| CSV Writer | `PaymentMfCsvWriter`, `CashBookConvertService`, `PurchaseJournalCsvService` |
| Service (集計表示) | `SupplierBalancesService`, `AccountsPayableLedgerService`, `AccountsPayableIntegrityService`, `MfSupplierLedgerService` |
| Tasklet | `AccountsPayableAggregationTasklet`, `AccountsPayableBackfillTasklet` |
| Import Service | `PaymentMfImportService`, `InvoiceImportService`, `CashBookConvertService` |

### Step 2: 影響を受ける Table / CSV / 画面の特定

| 種別 | 候補 |
|---|---|
| 書き込み先 Table | `t_accounts_payable_summary`, `t_accounts_receivable_summary`, `t_payment_mf_import_history`, `t_payment_mf_aux_row`, `t_consistency_review` |
| 出力 CSV | MF 仕入仕訳, MF 振込仕訳, MF 出納帳, MF 売上仕訳 |
| 画面表示 | 累積残一覧, 整合性レポート, 買掛帳, 累積残 supplier 詳細, MF ヘルスチェック |

### Step 3: 改修前のスナップショット取得 (main branch)

```bash
# main branch でテスト環境を起動
git checkout main
cd backend && ./gradlew bootRun --args='--spring.profiles.active=web,dev'

# 主要 API の出力を JSON で保存 (Authorization は dev 環境の cookie でも可)
# 例: 買掛帳 (1 supplier ÷ 5 ヶ月) と累積残一覧
curl -H "Cookie: SESSION=..." \
  "http://localhost:8090/api/v1/finance/accounts-payable-ledger?shopNo=1&supplierNo=22&fromMonth=2025-12-20&toMonth=2026-04-20" \
  > snapshot-before-ledger-22.json

curl -H "Cookie: SESSION=..." \
  "http://localhost:8090/api/v1/finance/supplier-balances?shopNo=1&asOfMonth=2026-04-20" \
  > snapshot-before-balances.json

curl -H "Cookie: SESSION=..." \
  "http://localhost:8090/api/v1/finance/integrity-report?shopNo=1&asOfMonth=2026-04-20" \
  > snapshot-before-integrity.json

# t_accounts_payable_summary の現状を CSV ダンプ (psql 直接)
psql -h localhost -p 55544 -U postgres -d odamitsu \
  -c "\copy (SELECT shop_no, supplier_no, transaction_month, tax_rate, opening_balance_tax_included, tax_included_amount_change, payment_amount_settled_tax_included, verified_amount, is_payment_only, verified_manually FROM t_accounts_payable_summary WHERE shop_no=1 ORDER BY supplier_no, transaction_month, tax_rate) TO 'snapshot-before-summary.csv' CSV HEADER"
```

### Step 4: 改修実施 (feature branch)

```bash
git checkout -b feature/improve-xxx
# 改修コミット (compileJava + 関連 test PASS まで)
cd backend && ./gradlew compileJava test --tests '*Aggregator*' --tests '*Balances*' --tests '*PaymentMf*' --tests '*CashBook*'
```

### Step 5: 改修後のスナップショット取得

Step 3 と **同じ API / SQL** を実行、結果を `snapshot-after-*.json` / `snapshot-after-summary.csv` に保存。

> NOTE: 改修内容によっては再集計バッチの実行が必要 (`AccountsPayableAggregationTasklet` / `AccountsPayableBackfillTasklet`)。
> その場合は **再集計後の snapshot を取る前に**、t_accounts_payable_summary を pg_dump でバックアップしておく。

### Step 6: 差分分析

```bash
# JSON を整形して diff
diff <(jq -S . snapshot-before-ledger-22.json) <(jq -S . snapshot-after-ledger-22.json) > diff-ledger-22.txt

# CSV の差分行数 / 影響 supplier 数を集計
diff snapshot-before-summary.csv snapshot-after-summary.csv > diff-summary.txt
wc -l diff-summary.txt
grep -E "^[<>]" diff-summary.txt | awk -F',' '{print $2}' | sort -u | wc -l   # 影響 supplier 数
```

### Step 7: 差分の妥当性判定

| 差分の種類 | 判定 | 対応 |
|---|---|---|
| 意図通りの変化 (改修目的) | OK | そのまま merge (PR 説明に明記) |
| 過去月への副作用 (= 確定済月への影響) | NG | 改修見直し or 旧ロジック条件分岐保持 |
| 期間外への波及 | 要確認 | 設計レビュー |
| 手動確定行 (`verified_manually=true`) の値変動 | NG (必ず保護) | 改修見直し (上書き分岐の漏れ) |
| 売上 / 売掛側への波及 (買掛改修なのに) | NG | 影響範囲特定の見直し |

### Step 8: 過去確定月の保護ルール

**原則**: 締め月以前の集計値は不変であること。

具体的判定:

- `transaction_month <= 直近確定締め月` の行で値が変わる → **NG**
- 期首前 (`m_supplier_opening_balance`) の取扱変化 → 要レビュー
- 手動確定行 (`verified_manually=true`) の値変動 → **NG** (必ず保護)
- payment-only 行 (`is_payment_only=true`) の生成 / 削除条件変更 → 要確認 (生成タイミング合意済みか)

直近確定締め月の判定基準:

- 経理締めが完了し MF への CSV 出力 (検証済 CSV / paymentmf-import) 済の月は不変
- `t_payment_mf_aux_row` に補助行が保存済の (transaction_month, transfer_date) は不変

---

## 4. ゴールデンマスタテストの位置付け

CI で常時実行されるゴールデンマスタテストで機械的に検出する。

### 対象テスト

| テスト名 | 対象 Service | fixture |
|---|---|---|
| `CashBookConvertServiceGoldenMasterTest` | `CashBookConvertService` | `src/test/resources/cashbook/*.xlsx` + `*.csv` (12 本) |
| `PaymentMfImportServiceGoldenMasterTest` | `PaymentMfImportService.convert()` | `src/test/resources/paymentmf/*.xlsx` + `*.csv` (2 本、enabled) |
| `PaymentMfImportServiceAuxRowTest` | `PaymentMfImportService.applyVerification()` | 同上 (enabled) |
| `PayableMonthlyAggregatorGoldenTest` | `PayableMonthlyAggregator` | 純 Java builder fixture (`backend/src/test/java/.../PayableMonthlyAggregatorGoldenTest.java`、14 ケース) |
| `SupplierBalancesServiceGoldenTest` | `SupplierBalancesService` | 純 Java builder fixture (`backend/src/test/java/.../SupplierBalancesServiceGoldenTest.java`、8 ケース) |

### 改修時のフロー

1. 改修開始 (feature branch)
2. `./gradlew test` でゴールデンマスタが fail するか確認
3. fail した場合の判断:
   - **意図通り** → fixture 更新 (§5 / §6 参照) → commit
   - **意図外** → 改修見直し (過去確定月への副作用)
4. 全 PASS まで繰り返し
5. PR に「改修影響範囲」セクションを記載

### Fixture 更新の原則

- fixture 更新は **必ず user 環境** で実 Excel / 実 DB 経由で再生成する (agent 環境では実 Excel / MF 接続不可)
- 更新前に旧 fixture を git に残す (revert 用バックアップ)
- PR 説明に「fixture 更新理由 + 差分件数 + 検収ログ」を記載

---

## 5. PaymentMfImport ゴールデンマスタ Fixture 更新手順

### 前提条件

- backend が起動 (web,dev profile、port 8090)
- admin login 済み
- 振込明細 Excel が手元に存在する (例: `H:\Dropbox\自分用\小田光\マネーフォワード用\買掛処理用\振込み明細08-4-20.xlsx`)
- `m_payment_mf_rule` が seed SQL (`V011__create_payment_mf_tables.sql`) で初期化済み

### 手順

1. user 環境で対象月の振込明細 Excel を準備
2. UI で `/finance/payment-mf-import` を開く
3. Excel をアップロード (preview 取得、`uploadId` 確認)
4. 内容を画面で検収 (PAYABLE / PAYABLE_FEE / PAYABLE_DISCOUNT / PAYABLE_EARLY / PAYABLE_OFFSET / DIRECT_PURCHASE_* / EXPENSE / SUMMARY 行を目視確認)
5. 「検証実行」ボタンで `applyVerification` 実行 (補助行が `t_payment_mf_aux_row` に保存される)
6. 「CSV ダウンロード」で MF CSV 取得
7. CSV を `backend/src/test/resources/paymentmf/買掛仕入MFインポートファイル_YYYYMMDD_v3.csv` に保存
   (既存命名規則踏襲、案 D 適用後は `_v3` で区別)
8. `PaymentMfImportServiceGoldenMasterTest` の `@CsvSource` を更新:
   ```java
   "振込み明細08-4-20.xlsx, 買掛仕入MFインポートファイル_20260420_v3.csv"
   ```
9. `@Disabled` を削除
10. `./gradlew test --tests '*PaymentMfImportServiceGoldenMasterTest*'` で PASS 確認
11. `PaymentMfImportServiceAuxRowTest` も同様に再有効化 (新 assertion: SUMMARY 行が 0 件、PAYABLE_* 副行が supplier 単位に展開される)

### 注意事項

- CSV エンコーディング: **CP932 + LF** (cashbook の UTF-8 BOM+CRLF と異なる)
- 金額末尾に半角スペース付与
- 取引日列 = 締め日 (`transactionMonth`、前月20日) で固定 (送金日ではない、2026-04-15 統一)
- supplier 別 attribute 展開後、SUMMARY 行は出力されない (`PAYABLE_FEE` / `PAYABLE_DISCOUNT` / `PAYABLE_EARLY` / `PAYABLE_OFFSET` で代替)

---

## 6. PayableMonthlyAggregator / SupplierBalancesService Fixture 更新手順

### PayableMonthlyAggregator

純 Java unit test。fixture は test code 内 builder で表現:

1. `backend/src/test/java/jp/co/oda32/batch/finance/service/PayableMonthlyAggregatorGoldenTest.java` を編集
   - 入力: `row(supplierNo, month, taxRate)` builder で各 row を作成
   - 期待値: `assertThat(...).isEqualByComparingTo("...")` で BigDecimal 比較
2. シナリオ追加時は `@Nested class ScenarioN_xxx` を追加
3. `./gradlew test --tests '*PayableMonthlyAggregatorGoldenTest*'` で PASS 確認

### SupplierBalancesService

1. `backend/src/test/java/jp/co/oda32/domain/service/finance/SupplierBalancesServiceGoldenTest.java` を編集
   - 入力: `row(supplierNo, month, taxRate)` + `payableJournal(date, subAccount, credit, debit)` で構築
   - master / opening / MF account はそれぞれ `stubMaster` / `stubOpeningEmpty` / `stubMfAccount` helper で stub
2. `./gradlew test --tests '*SupplierBalancesServiceGoldenTest*'` で PASS 確認

### 注意事項

- 純 Java builder fixture なので **DB 接続も MF 接続も不要** (agent 環境でも編集可)
- BigDecimal の比較は必ず `isEqualByComparingTo("...")` (scale 違いで isEqualTo は失敗する)
- LocalDate は `LocalDate.of(2026, 4, 20)` か定数 `AS_OF` / `MARCH_20` を使用
- `payableJournal` helper は **debit 買掛金 ダミー branch** を含めて isOpeningCandidate=false を保証
  ({@link MfOpeningJournalDetector#isOpeningCandidate} は credit-only な買掛金仕訳を opening と誤判定するため)

---

## 7. 改修担当者向けチェックリスト

PR 作成時に以下を満たしていることを確認:

- [ ] 改修対象の Service / Calculator を列挙した
- [ ] 影響を受ける Table / CSV / 画面を列挙した
- [ ] main branch でのスナップショット取得済 (§3 Step 3)
- [ ] feature branch でのスナップショット取得済 (§3 Step 5)
- [ ] 差分分析を実施し、過去確定月への副作用がないことを確認した (§3 Step 7-8)
- [ ] ゴールデンマスタテスト (`CashBookConvert*`, `PaymentMfImport*`, `*Aggregator*`, `*Balances*`) が PASS する
- [ ] 手動確定行 (`verified_manually=true`) が改修で上書きされないことを確認した
- [ ] CI で全テスト PASS
- [ ] PR 説明に以下のセクションを記載:
  - 改修影響範囲 (Service / Table / CSV / 画面)
  - 過去月への影響 (なし / あり + 理由)
  - fixture 更新の有無 (あり/なし、ある場合は理由)
  - スナップショット差分件数 (supplier 数 / 行数 / 金額レンジ)

---

## 8. 関連ドキュメント

- `claudedocs/design-payment-mf-import.md` ? PaymentMfImport 仕様 (P1-03 案 D-2 適用後の構造)
- `claudedocs/design-supplier-balances-health.md` ? SupplierBalancesService の設計 (軸 D+E)
- `claudedocs/design-integrity-report.md` ? AccountsPayableIntegrityService の設計 (軸 B+C)
- `claudedocs/design-phase-b-prime-payment-settled.md` ? PayableMonthlyAggregator の設計 (Phase B')
- `claudedocs/design-source-of-truth-hierarchy.md` ? verified_amount 不変条件
- `MEMORY.md` (project_residual_overcount.md 参照) ? 旧バッチ起因の過去月過剰計上事例
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

2026-05-06T05:36:08.917617Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 1.8 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
Get-Content: 
Line |
   2 |  Get-Content -Path backend\src\test\java\jp\co\oda32\domain\service\fi 乧
     |  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     | Cannot find path 'C:\project\odamitsu-data-hub\backend\src\test\java\jp\co\oda32\domain\service\finance\PayableMonthlyAggregatorGoldenTest.java' because it does not exist.

 exited 1 in 1762ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
Get-Content: 
Line |
   2 |  Get-Content -Path backend\src\test\java\jp\co\oda32\domain\service\fi 乧
     |  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     | Cannot find path 'C:\project\odamitsu-data-hub\backend\src\test\java\jp\co\oda32\domain\service\finance\PayableMonthlyAggregatorGoldenTest.java' because it does not exist.

 succeeded in 2958ms:
package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.repository.finance.MfAccountMasterRepository;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.service.finance.mf.MfJournal;
import jp.co.oda32.domain.service.finance.mf.MfJournal.MfBranch;
import jp.co.oda32.domain.service.finance.mf.MfJournal.MfSide;
import jp.co.oda32.domain.service.finance.mf.MfJournalCacheService;
import jp.co.oda32.domain.service.finance.mf.MfJournalCacheService.CachedResult;
import jp.co.oda32.domain.service.finance.mf.MfOauthService;
import jp.co.oda32.domain.service.finance.mf.MfOpeningBalanceService;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import jp.co.oda32.dto.finance.SupplierBalancesResponse;
import jp.co.oda32.dto.finance.SupplierBalancesResponse.SupplierBalanceRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SupplierBalancesService} のゴールデンマスタ単体テスト。
 * <p>
 * T3 案 D'' Phase 3 で追加 (2026-05-06)。
 * 改修起因の集計値変動を検知するため、純 Java fixture (builder-based) で代表シナリオを locked-in する。
 * <p>
 * 本テストが fail した時の判断:
 * <ul>
 *   <li><b>意図通りの仕様変更</b> → 期待値を改修目的に合致した値に更新 (PR で根拠を明記)</li>
 *   <li><b>意図外の副作用</b> → 改修見直し (sub_account 解決ロジック、journal フィルタ、closing 算出等)</li>
 * </ul>
 * <p>
 * 詳細手順: {@code claudedocs/runbook-finance-recalc-impact-analysis.md} §3 §4 §6
 * <p>
 * シナリオ:
 * <ul>
 *   <li>全 supplier MATCH (差分 0)</li>
 *   <li>MINOR (差分 ≦ 1000) / MAJOR (差分 > 1000) の閾値判定</li>
 *   <li>MF_MISSING (self あり / MF なし)</li>
 *   <li>SELF_MISSING (MF あり / self なし)</li>
 *   <li>opening 注入 (期首残あり supplier の closing 加算)</li>
 *   <li>journal #1 (期首残仕訳) は accumulation から除外</li>
 * </ul>
 *
 * @since 2026-05-06 (T3 案 D'' Phase 3)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplierBalancesServiceGoldenTest {

    private static final Integer SHOP = 1;
    private static final LocalDate AS_OF = LocalDate.of(2026, 4, 20);
    private static final LocalDate FETCH_FROM = MfPeriodConstants.MF_JOURNALS_FETCH_FROM; // 2025-05-20

    private MfOauthService mfOauthService;
    private MfJournalCacheService journalCache;
    private MfAccountMasterRepository mfAccountMasterRepository;
    private TAccountsPayableSummaryRepository summaryRepository;
    private MPaymentSupplierService paymentSupplierService;
    private MfOpeningBalanceService openingBalanceService;
    private SupplierBalancesService service;

    @BeforeEach
    void setup() {
        mfOauthService = mock(MfOauthService.class);
        journalCache = mock(MfJournalCacheService.class);
        mfAccountMasterRepository = mock(MfAccountMasterRepository.class);
        summaryRepository = mock(TAccountsPayableSummaryRepository.class);
        paymentSupplierService = mock(MPaymentSupplierService.class);
        openingBalanceService = mock(MfOpeningBalanceService.class);

        service = new SupplierBalancesService(
                mfOauthService, journalCache, mfAccountMasterRepository,
                summaryRepository, paymentSupplierService, openingBalanceService);

        // MF OAuth 系は token 取得まで mock
        MMfOauthClient client = new MMfOauthClient();
        when(mfOauthService.findActiveClient()).thenReturn(Optional.of(client));
        when(mfOauthService.getValidAccessToken()).thenReturn("dummy-token");
    }

    // ============================================================
    // Scenario 1: MATCH (self closing == MF balance, 差分 0)
    // ============================================================

    @Test
    @DisplayName("Scenario 1: 単一 supplier MATCH (self closing == MF balance)")
    void scenario_1_single_supplier_match() {
        // self: supplier 100 / 4/20 / 10% / change 50,000 → closing 50,000 (opening 0, payment 0)
        TAccountsPayableSummary self = row(100, AS_OF, "10")
                .taxIncludedAmountChange(new BigDecimal("50000"))
                .taxExcludedAmountChange(new BigDecimal("45455"))
                .build();
        stubSelfRows(List.of(self));
        stubMaster(supplier(100, "S100", "仕入先A"));
        stubMfAccount("仕入先A_sub", "S100");
        stubMfJournals(List.of(
                payableJournal(LocalDate.of(2026, 4, 1), "仕入先A_sub", new BigDecimal("50000"), null)
        ));
        stubOpeningEmpty();

        SupplierBalancesResponse resp = service.generate(SHOP, AS_OF, false);

        assertThat(resp.getRows()).hasSize(1);
        SupplierBalanceRow r = resp.getRows().get(0);
        assertThat(r.getSupplierNo()).isEqualTo(100);
        assertThat(r.getSelfBalance()).isEqualByComparingTo("50000");
        assertThat(r.getMfBalance()).isEqualByComparingTo("50000");
        assertThat(r.getDiff()).isEqualByComparingTo("0");
        assertThat(r.getStatus()).isEqualTo("MATCH");
        assertThat(r.getMasterRegistered()).isTrue();
    }

    // ============================================================
    // Scenario 2: MINOR / MAJOR 閾値判定
    // ============================================================

    @Test
    @DisplayName("Scenario 2-MINOR: 差分 500 → MINOR 分類")
    void scenario_2_minor_diff() {
        TAccountsPayableSummary self = row(101, AS_OF, "10")
                .taxIncludedAmountChange(new BigDecimal("50500"))
                .taxExcludedAmountChange(new BigDecimal("45909"))
                .build();
        stubSelfRows(List.of(self));
        stubMaster(supplier(101, "S101", "仕入先B"));
        stubMfAccount("仕入先B_sub", "S101");
        stubMfJournals(List.of(
                payableJournal(LocalDate.of(2026, 4, 1), "仕入先B_sub", new BigDecimal("50000"), null)
        ));
        stubOpeningEmpty();

        SupplierBalancesResponse resp = service.generate(SHOP, AS_OF, false);
        SupplierBalanceRow r = resp.getRows().get(0);
        // self closing = 50500, MF = 50000, diff = 500
        assertThat(r.getDiff()).isEqualByComparingTo("500");
        assertThat(r.getStatus()).isEqualTo("MINOR");
    }

    @Test
    @DisplayName("Scenario 2-MAJOR: 差分 5000 → MAJOR 分類")
    void scenario_2_major_diff() {
        TAccountsPayableSummary self = row(102, AS_OF, "10")
                .taxIncludedAmountChange(new BigDecimal("55000"))
                .taxExcludedAmountChange(new BigDecimal("50000"))
                .build();
        stubSelfRows(List.of(self));
        stubMaster(supplier(102, "S102", "仕入先C"));
        stubMfAccount("仕入先C_sub", "S102");
        stubMfJournals(List.of(
                payableJournal(LocalDate.of(2026, 4, 1), "仕入先C_sub", new BigDecimal("50000"), null)
        ));
        stubOpeningEmpty();

        SupplierBalancesResponse resp = service.generate(SHOP, AS_OF, false);
        SupplierBalanceRow r = resp.getRows().get(0);
        assertThat(r.getDiff()).isEqualByComparingTo("5000");
        assertThat(r.getStatus()).isEqualTo("MAJOR");
    }

    @Test
    @DisplayName("Scenario 2-EDGE: 差分 100 (= MATCH_TOLERANCE) は MATCH 判定")
    void scenario_2_edge_match_tolerance() {
        TAccountsPayableSummary self = row(103, AS_OF, "10")
                .taxIncludedAmountChange(new BigDecimal("50100"))
                .taxExcludedAmountChange(new BigDecimal("45545"))
                .build();
        stubSelfRows(List.of(self));
        stubMaster(supplier(103, "S103", "仕入先D"));
        stubMfAccount("仕入先D_sub", "S103");
        stubMfJournals(List.of(
                payableJournal(LocalDate.of(2026, 4, 1), "仕入先D_sub", new BigDecimal("50000"), null)
        ));
        stubOpeningEmpty();

        SupplierBalancesResponse resp = service.generate(SHOP, AS_OF, false);
        SupplierBalanceRow r = resp.getRows().get(0);
        // diff = 100, MATCH_TOLERANCE = 100 → MATCH (≦ で判定)
        assertThat(r.getDiff()).isEqualByComparingTo("100");
        assertThat(r.getStatus()).isEqualTo("MATCH");
    }

    // ============================================================
    // Scenario 3: MF_MISSING (self あり / MF なし)
    // ============================================================

    @Test
    @DisplayName("Scenario 3: MF_MISSING (self closing > 0 / MF activity 0)")
    void scenario_3_mf_missing() {
        TAccountsPayableSummary self = row(200, AS_OF, "10")
                .taxIncludedAmountChange(new BigDecimal("30000"))
                .taxExcludedAmountChange(new BigDecimal("27273"))
                .build();
        stubSelfRows(List.of(self));
        stubMaster(supplier(200, "S200", "仕入先E"));
        // master 登録あるが activity 0
        stubMfAccount("仕入先E_sub", "S200");
        stubMfJournals(List.of()); // MF journal 無し
        stubOpeningEmpty();

        SupplierBalancesResponse resp = service.generate(SHOP, AS_OF, false);
        SupplierBalanceRow r = resp.getRows().get(0);
        assertThat(r.getStatus()).isEqualTo("MF_MISSING");
        assertThat(r.getSelfBalance()).isEqualByComparingTo("30000");
        assertThat(r.getMfBalance()).isEqualByComparingTo("0");
    }

    // ============================================================
    // Scenario 4: SELF_MISSING (MF あり / self なし)
    // ============================================================

    @Test
    @DisplayName("Scenario 4: SELF_MISSING (MF activity あり / self 未登録)")
    void scenario_4_self_missing() {
        // self 行は無し
        stubSelfRows(List.of());
        stubMaster(supplier(300, "S300", "仕入先F"));
        stubMfAccount("仕入先F_sub", "S300");
        stubMfJournals(List.of(
                payableJournal(LocalDate.of(2026, 4, 1), "仕入先F_sub", new BigDecimal("20000"), null)
        ));
        stubOpeningEmpty();

        SupplierBalancesResponse resp = service.generate(SHOP, AS_OF, false);
        assertThat(resp.getRows()).hasSize(1);
        SupplierBalanceRow r = resp.getRows().get(0);
        assertThat(r.getStatus()).isEqualTo("SELF_MISSING");
        assertThat(r.getSelfBalance()).isEqualByComparingTo("0");
        assertThat(r.getMfBalance()).isEqualByComparingTo("20000");
    }

    // ============================================================
    // Scenario 5: opening 注入 (期首残あり supplier の closing 加算)
    // ============================================================

    @Test
    @DisplayName("Scenario 5: 期首残あり supplier の self closing に opening を加算")
    void scenario_5_opening_balance_injection() {
        TAccountsPayableSummary self = row(400, AS_OF, "10")
                .taxIncludedAmountChange(new BigDecimal("10000"))
                .taxExcludedAmountChange(new BigDecimal("9091"))
                .build();
        stubSelfRows(List.of(self));
        stubMaster(supplier(400, "S400", "仕入先G"));
        stubMfAccount("仕入先G_sub", "S400");
        stubMfJournals(List.of(
                payableJournal(LocalDate.of(2026, 4, 1), "仕入先G_sub", new BigDecimal("110000"), null)
        ));
        // 期首残: supplier 400 に 100,000
        when(openingBalanceService.getEffectiveBalanceMap(SHOP, MfPeriodConstants.SELF_BACKFILL_START))
                .thenReturn(Map.of(400, new BigDecimal("100000")));

        SupplierBalancesResponse resp = service.generate(SHOP, AS_OF, false);
        SupplierBalanceRow r = resp.getRows().get(0);
        // self closing = current_closing(10000) + opening(100000) = 110,000
        assertThat(r.getSelfBalance()).isEqualByComparingTo("110000");
        assertThat(r.getMfBalance()).isEqualByComparingTo("110000");
        assertThat(r.getStatus()).isEqualTo("MATCH");
        assertThat(r.getSelfOpening()).isEqualByComparingTo("100000");
    }

    // ============================================================
    // Scenario 6: journal #1 (期首残仕訳) は accumulation から除外
    // ============================================================

    @Test
    @DisplayName("Scenario 6: 期首残高仕訳 (credit-only / debit に買掛金 無し) は accumulation から除外")
    void scenario_6_opening_journal_excluded() {
        TAccountsPayableSummary self = row(500, AS_OF, "10")
                .taxIncludedAmountChange(new BigDecimal("50000"))
                .taxExcludedAmountChange(new BigDecimal("45455"))
                .build();
        stubSelfRows(List.of(self));
        stubMaster(supplier(500, "S500", "仕入先H"));
        stubMfAccount("仕入先H_sub", "S500");
        // 通常 journal (debit に買掛金あり) と opening journal (credit のみ) を混在
        MfJournal openingJournal = new MfJournal(
                "j-opening", LocalDate.of(2025, 6, 21), 1, true, "opening", "期首残",
                List.of(
                        // credit only (debit は他の科目) → opening 判定対象
                        new MfBranch(
                                new MfSide(null, "買掛金", null, "仕入先H_sub", null, "対象外", null, new BigDecimal("999999")),
                                new MfSide(null, "繰越利益剰余金", null, null, null, "対象外", null, new BigDecimal("999999")),
                                "opening")
                )
        );
        MfJournal regularJournal = payableJournal(LocalDate.of(2026, 4, 1), "仕入先H_sub", new BigDecimal("50000"), null);

        stubMfJournals(List.of(openingJournal, regularJournal));
        // opening は openingBalanceService 経由で別途注入 (今回は 0 に統一して journal #1 除外を verify)
        stubOpeningEmpty();

        SupplierBalancesResponse resp = service.generate(SHOP, AS_OF, false);
        SupplierBalanceRow r = resp.getRows().get(0);
        // opening journal が除外されていれば MF balance = 50000 (regular のみ)
        assertThat(r.getMfBalance()).isEqualByComparingTo("50000");
        assertThat(r.getDiff()).isEqualByComparingTo("0");
        assertThat(r.getStatus()).isEqualTo("MATCH");
    }

    // ============================================================
    // 共通 stub helpers
    // ============================================================

    private void stubSelfRows(List<TAccountsPayableSummary> rows) {
        when(summaryRepository
                .findByShopNoAndTransactionMonthBetweenOrderBySupplierNoAscTransactionMonthAscTaxRateAsc(
                        eq(SHOP), eq(FETCH_FROM), eq(AS_OF)))
                .thenReturn(rows);
    }

    private void stubMaster(MPaymentSupplier... suppliers) {
        when(paymentSupplierService.findByShopNo(SHOP)).thenReturn(java.util.Arrays.asList(suppliers));
    }

    private void stubMfAccount(String subAccountName, String supplierCode) {
        // service.buildMfSubToCodes() が findAll() を呼ぶ前提
        MfAccountMaster m = new MfAccountMaster();
        m.setReportName("貸借対照表");
        m.setCategory("負債");
        m.setFinancialStatementItem("買掛金");
        m.setAccountName("買掛金");
        m.setSubAccountName(subAccountName);
        m.setSearchKey(supplierCode);
        when(mfAccountMasterRepository.findAll()).thenReturn(List.of(m));
    }

    private void stubMfJournals(List<MfJournal> journals) {
        when(journalCache.getOrFetch(eq(SHOP), any(), any(), eq(FETCH_FROM), eq(AS_OF), anyBoolean()))
                .thenReturn(new CachedResult(journals, java.time.Instant.parse("2026-04-21T00:00:00Z")));
    }

    private void stubOpeningEmpty() {
        when(openingBalanceService.getEffectiveBalanceMap(SHOP, MfPeriodConstants.SELF_BACKFILL_START))
                .thenReturn(new HashMap<>());
    }

    private MPaymentSupplier supplier(Integer no, String code, String name) {
        return MPaymentSupplier.builder()
                .paymentSupplierNo(no)
                .paymentSupplierCode(code)
                .paymentSupplierName(name)
                .shopNo(SHOP)
                .delFlg("0")
                .build();
    }

    /**
     * 通常仕訳: credit に買掛金 (購入) + debit に買掛金 (支払) を 1 つの journal に同居させる。
     * <p>
     * {@link jp.co.oda32.domain.service.finance.mf.MfOpeningJournalDetector#isOpeningCandidate}
     * は「全 branch credit に買掛金 AND どの branch にも debit 買掛金が無い」を opening 判定とするため、
     * 単純な購入仕訳 (credit 買掛金 / debit 仕入高) も opening として除外されてしまう。
     * <p>
     * 本 helper は **debit に買掛金が含まれる payment branch をダミーで追加** することで
     * isOpeningCandidate=false を確実にし、accumulateMfJournals が credit/debit を集計する
     * 通常経路を test できるようにする。
     * <p>
     * 計算: mfBalance = credit - debit。MATCH 用には debit=0 ダミー branch を加える。
     *
     * @param credit 通常購入の credit (買掛金)
     * @param debit  支払の debit (買掛金)
     */
    private MfJournal payableJournal(LocalDate transactionDate, String subAccountName,
                                      BigDecimal credit, BigDecimal debit) {
        BigDecimal cr = credit != null ? credit : BigDecimal.ZERO;
        BigDecimal de = debit != null ? debit : BigDecimal.ZERO;
        // Branch 1: credit 買掛金 (購入)
        MfBranch purchaseBr = new MfBranch(
                new MfSide(null, "買掛金", null, subAccountName, null, "対象外", null, cr),
                new MfSide(null, "仕入高", null, null, null, "課税仕入10%", null, cr),
                "purchase"
        );
        // Branch 2: debit 買掛金 (支払、最小 1 円のダミー)
        // value=0 でも debit に買掛金が存在すれば isOpeningCandidate=false になる
        MfBranch paymentBr = new MfBranch(
                new MfSide(null, "普通預金", null, null, null, "対象外", null, de),
                new MfSide(null, "買掛金", null, subAccountName, null, "対象外", null, de),
                "payment"
        );
        return new MfJournal(
                "j-" + transactionDate, transactionDate, 100, true, "regular", "",
                List.of(purchaseBr, paymentBr)
        );
    }

    private TAccountsPayableSummary.TAccountsPayableSummaryBuilder row(
            Integer supplierNo, LocalDate month, String taxRate) {
        return TAccountsPayableSummary.builder()
                .shopNo(SHOP)
                .supplierNo(supplierNo)
                .supplierCode("S" + supplierNo)
                .transactionMonth(month)
                .taxRate(new BigDecimal(taxRate))
                .openingBalanceTaxIncluded(BigDecimal.ZERO)
                .openingBalanceTaxExcluded(BigDecimal.ZERO)
                .paymentAmountSettledTaxIncluded(BigDecimal.ZERO)
                .paymentAmountSettledTaxExcluded(BigDecimal.ZERO)
                .autoAdjustedAmount(BigDecimal.ZERO)
                .verifiedManually(false)
                .isPaymentOnly(false);
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Path backend\\src\\test\\java -Recurse -Filter PayableMonthlyAggregatorGoldenTest.java | Select-Object -ExpandProperty FullName" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path .github -Recurse -Force -ErrorAction SilentlyContinue | Select-Object FullName,Length' in C:\project\odamitsu-data-hub
2026-05-06T05:36:17.822421Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path .github -Recurse -Force -ErrorAction SilentlyContinue | Select-Object FullName,Length'` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path .github -Recurse -Force -ErrorAction SilentlyContinue | Select-Object FullName,Length'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\test\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportServiceGoldenMasterTest.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\test\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportServiceAuxRowTest.java" in C:\project\odamitsu-data-hub
 succeeded in 2026ms:
package jp.co.oda32.domain.service.finance;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jp.co.oda32.domain.model.finance.MPaymentMfRule;
import jp.co.oda32.domain.model.finance.TPaymentMfAuxRow;
import jp.co.oda32.domain.repository.finance.MPaymentMfRuleRepository;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.repository.finance.TPaymentMfAuxRowRepository;
import jp.co.oda32.domain.repository.finance.TPaymentMfImportHistoryRepository;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * applyVerification 時に補助行 (EXPENSE/SUMMARY/DIRECT_PURCHASE) が正しく aux テーブルに
 * 洗い替え保存されるかを検証するユニットテスト。
 * ルールマスタは最小限のダミー (SUMMARY はルール不要で自動生成される) を渡すだけで良く、
 * 振込手数料値引 / 早払収益の SUMMARY 2件が確実に保存されることを検証する。
 *
 * <p><b>P1-03 案 D 適用 (2026-05-04) で fixture/前提が更新必要</b>:
 * 案 D で SUMMARY 集約 (合計仕訳 2 行) を撤去し、supplier 別 PAYABLE_FEE/EARLY 副行に展開した。
 * 旧テストは「SUMMARY 行は必ず 2 件」を assert しているが、案 D 後は SUMMARY 行が
 * 0 件になる (代わりに副行が PAYABLE_* として preview に展開)。
 *
 * <p>新 fixture (supplier 別 attribute を含む実 Excel) で aux 保存対象が EXPENSE/DIRECT_PURCHASE
 * のみになることを assert する新テストを別途用意する想定。それまでは {@link Disabled}。
 *
 * <p>TODO(T3 案 D'' Phase 2): user 環境で fixture 再生成 + 新 assertion 整備後に @Disabled 解除。
 * 詳細手順 = {@code claudedocs/runbook-finance-recalc-impact-analysis.md} §5。
 * 新 assertion の方針:
 * <ul>
 *   <li>SUMMARY 行は 0 件 (supplier 別 PAYABLE_* に展開されたため)</li>
 *   <li>aux 保存対象は EXPENSE / DIRECT_PURCHASE_* / 仕入相殺 系のみ</li>
 *   <li>PAYABLE_FEE / PAYABLE_DISCOUNT / PAYABLE_EARLY / PAYABLE_OFFSET は preview に含まれるが aux 不保存</li>
 *   <li>洗い替え (delete + saveAll) のキー (shop, transactionMonth, transferDate) は変わらず</li>
 * </ul>
 */
@Disabled("P1-03 案 D 適用 (2026-05-04): SUMMARY 集約撤去・supplier 別 PAYABLE_* 展開に変更。"
        + "「SUMMARY 行は必ず 2 件」assertion が無効化される。"
        + "TODO(T3 案 D'' Phase 2): user 環境で fixture 再生成 + 新 assertion 整備後に再有効化。"
        + "手順 = claudedocs/runbook-finance-recalc-impact-analysis.md §5")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentMfImportServiceAuxRowTest {

    @Mock MPaymentMfRuleRepository ruleRepository;
    @Mock TPaymentMfImportHistoryRepository historyRepository;
    @Mock TAccountsPayableSummaryRepository payableRepository;
    @Mock TAccountsPayableSummaryService payableService;
    @Mock TPaymentMfAuxRowRepository auxRowRepository;
    @Mock EntityManager entityManager;
    @Mock Query advisoryLockQuery;
    @InjectMocks PaymentMfImportService service;

    private static final Path FIXTURE_DIR = Paths.get("src/test/resources/paymentmf");

    @BeforeEach
    void setup() {
        // ルールマスタは空 (= すべて UNREGISTERED 扱い) でも SUMMARY 2 件は自動生成される
        when(ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0"))
                .thenReturn(Collections.<MPaymentMfRule>emptyList());
        when(payableRepository.findByShopNoAndSupplierCodeAndTransactionMonth(any(), anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(payableRepository.findByShopNoAndSupplierCodeInAndTransactionMonth(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        // EntityManager / advisory lock はテスト対象外。発火したら即 NO-OP で返す。
        // @InjectMocks はコンストラクタ注入で停止するため、@PersistenceContext フィールドは
        // ReflectionTestUtils で明示的にセットする。
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        when(entityManager.createNativeQuery(anyString())).thenReturn(advisoryLockQuery);
        when(advisoryLockQuery.setParameter(anyString(), any())).thenReturn(advisoryLockQuery);
        when(advisoryLockQuery.getSingleResult()).thenReturn(1);
    }

    @Test
    void applyVerification後にaux削除と保存が呼ばれる_SUMMARY2件は必ず保存される() throws Exception {
        List<TPaymentMfAuxRow> saved = applyFixtureAndCaptureSaved("振込み明細08-2-5.xlsx");

        // (shop=1, txMonth=2026-01-20, transferDate=2026-02-05) で delete が呼ばれる
        verify(auxRowRepository).deleteByShopAndTransactionMonthAndTransferDate(
                eq(1), eq(java.time.LocalDate.of(2026, 1, 20)),
                eq(java.time.LocalDate.of(2026, 2, 5)));

        assertFalse(saved.isEmpty(), "aux 行が 1 件以上保存される");

        // PAYABLE は aux に入らない
        for (TPaymentMfAuxRow r : saved) {
            assertFalse("PAYABLE".equals(r.getRuleKind()),
                    "PAYABLE 行は aux に保存されない: " + r.getRuleKind());
        }

        // SUMMARY 2 件（振込手数料値引 + 早払収益）が確実に含まれる
        long summaryCount = saved.stream().filter(r -> "SUMMARY".equals(r.getRuleKind())).count();
        assertEquals(2, summaryCount, "SUMMARY 行は必ず 2 件 (振込手数料値引 + 早払収益)");

        // すべて transactionMonth と transferDate が正しい
        for (TPaymentMfAuxRow r : saved) {
            assertEquals(java.time.LocalDate.of(2026, 1, 20), r.getTransactionMonth());
            assertEquals(java.time.LocalDate.of(2026, 2, 5), r.getTransferDate());
            assertEquals(Integer.valueOf(1), r.getShopNo());
            assertTrue(r.getSequenceNo() != null && r.getSequenceNo() >= 0);
            assertTrue(r.getAddDateTime() != null);
            assertEquals(Integer.valueOf(1), r.getAddUserNo());
        }
    }

    @Test
    void 再アップロード時はdelete2回_最終saveAll内容が2回目と一致する() throws Exception {
        // 1 回目: 洗い替え対象のキー (shop=1, 2026-01-20, 2026-02-05) で delete → saveAll
        List<TPaymentMfAuxRow> first = applyFixtureAndCaptureSaved("振込み明細08-2-5.xlsx");
        int firstCount = first.size();
        assertTrue(firstCount > 0, "1回目で補助行が保存される");

        // 2 回目: 同じファイルを再アップロード（洗い替え挙動を検証）
        List<TPaymentMfAuxRow> second = applyFixtureAndCaptureSaved("振込み明細08-2-5.xlsx");

        // delete は計 2 回（各 applyVerification で 1 回ずつ）呼ばれる
        verify(auxRowRepository, org.mockito.Mockito.times(2))
                .deleteByShopAndTransactionMonthAndTransferDate(
                        eq(1), eq(java.time.LocalDate.of(2026, 1, 20)),
                        eq(java.time.LocalDate.of(2026, 2, 5)));

        // 2 回目の saveAll は 1 回目と同件数（同一 Excel なので内容も同等）
        assertEquals(firstCount, second.size(),
                "再アップロードで保存件数が変わらない（洗い替え後の最新状態が同一）");
    }

    /**
     * Fixture を preview → applyVerification し、直近の saveAll() で渡された全 aux 行を返す。
     * 呼び出しの都度 saveAll のキャプチャは累積するため、最後の呼び出し分を返す。
     */
    private List<TPaymentMfAuxRow> applyFixtureAndCaptureSaved(String fixtureName) throws Exception {
        byte[] xlsxBytes = Files.readAllBytes(FIXTURE_DIR.resolve(fixtureName));
        MockMultipartFile multipart = new MockMultipartFile(
                "file", fixtureName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);
        PaymentMfPreviewResponse preview = service.preview(multipart);
        service.applyVerification(preview.getUploadId(), 1);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<TPaymentMfAuxRow>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(auxRowRepository, atLeastOnce()).saveAll(captor.capture());
        List<List<TPaymentMfAuxRow>> allCalls = captor.getAllValues();
        return allCalls.get(allCalls.size() - 1);
    }

    // Mockito.eq の簡易ショートカット
    private static <T> T eq(T v) { return org.mockito.ArgumentMatchers.eq(v); }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2100ms:
package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.MPaymentMfRule;
import jp.co.oda32.domain.repository.finance.MPaymentMfRuleRepository;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.repository.finance.TPaymentMfImportHistoryRepository;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 参照CSV（運用中の買掛仕入MFインポートファイル）と意味等価比較する回帰テスト。
 * シードSQLから m_payment_mf_rule を構築して Mockito 注入。
 *
 * <p><b>P1-03 案 D 適用 (2026-05-04) で fixture 更新が必要</b>:
 * 既存 fixture (買掛仕入MFインポートファイル_*.csv) は SUMMARY 集約 (振込手数料値引/早払収益 合計仕訳 2 行)
 * 構造を前提に作成されている。supplier 別 attribute 案 D 採用後は PAYABLE 主行が
 * 振込金額に切替わり、副行 (PAYABLE_FEE/PAYABLE_DISCOUNT/PAYABLE_EARLY/PAYABLE_OFFSET)
 * が supplier 単位で展開される構造に変わる。
 *
 * <p>新 fixture は実 Excel
 * ({@code H:\Dropbox\自分用\小田光\マネーフォワード用\買掛処理用\振込み明細08-4-20.xlsx} 等)
 * を user 環境で取込→出力→検収後に置き換える運用とする。
 * それまでの間、本クラス全体を {@link Disabled} でマークし fixture 再生成を待つ。
 *
 * <p>TODO(T3 案 D'' Phase 2): user 環境で fixture 再生成後に @Disabled 解除。
 * 再生成手順は {@code claudedocs/runbook-finance-recalc-impact-analysis.md} §5 参照。
 * 必要なもの:
 * <ul>
 *   <li>振込明細 Excel (例: 振込み明細08-4-20.xlsx)</li>
 *   <li>backend 起動 + admin login</li>
 *   <li>POST /api/v1/finance/payment-mf-import/convert で preview 取得</li>
 *   <li>POST /api/v1/finance/payment-mf-import/verify で applyVerification 実行</li>
 *   <li>GET /api/v1/finance/payment-mf-import/csv?uploadId=... で CSV ダウンロード</li>
 *   <li>{@code src/test/resources/paymentmf/買掛仕入MFインポートファイル_YYYYMMDD_v3.csv} に保存</li>
 *   <li>{@code @CsvSource} を新 fixture に更新</li>
 *   <li>{@code @Disabled} を削除して PASS 確認</li>
 * </ul>
 */
@Disabled("P1-03 案 D 適用 (2026-05-04): supplier 別 attribute 構造に変更。"
        + "既存 fixture (SUMMARY 集約 2 行構造) は無効。"
        + "TODO(T3 案 D'' Phase 2): user 環境で fixture 再生成後に再有効化。"
        + "手順 = claudedocs/runbook-finance-recalc-impact-analysis.md §5")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentMfImportServiceGoldenMasterTest {

    @Mock MPaymentMfRuleRepository ruleRepository;
    @Mock TPaymentMfImportHistoryRepository historyRepository;
    @Mock TAccountsPayableSummaryRepository payableRepository;
    @InjectMocks PaymentMfImportService service;

    private static final Path FIXTURE_DIR = Paths.get("src/test/resources/paymentmf");
    private static final Path SEED_SQL = Paths.get("src/main/resources/db/migration/V011__create_payment_mf_tables.sql");
    private static final Charset CP932 = Charset.forName("MS932");

    private List<MPaymentMfRule> rules;

    @BeforeEach
    void loadSeed() throws Exception {
        String sql = Files.readString(SEED_SQL, java.nio.charset.StandardCharsets.UTF_8);
        rules = parseRules(sql);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "振込み明細08-2-5.xlsx,  買掛仕入MFインポートファイル_20260205.csv",
            "振込み明細08-2-20.xlsx, 買掛仕入MFインポートファイル_20260220_v2.csv"
    })
    void 意味等価比較(String xlsx, String goldenCsv) throws Exception {
        when(ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0")).thenReturn(rules);
        when(payableRepository.findByShopNoAndSupplierCodeAndTransactionMonth(any(), anyString(), any()))
                .thenReturn(Collections.emptyList());

        byte[] xlsxBytes = Files.readAllBytes(FIXTURE_DIR.resolve(xlsx));
        MockMultipartFile multipart = new MockMultipartFile(
                "file", xlsx,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);
        PaymentMfPreviewResponse preview = service.preview(multipart);
        assertEquals(0, preview.getErrorCount(),
                "未登録送り先あり: " + preview.getUnregisteredSources());

        byte[] actual = service.convert(preview.getUploadId(), null);
        byte[] expected = Files.readAllBytes(FIXTURE_DIR.resolve(goldenCsv));

        List<String[]> a = parseCsv(actual);
        List<String[]> e = parseCsv(expected);
        assertEquals(e.size(), a.size(),
                "行数不一致 (" + xlsx + ") expected=" + e.size() + " actual=" + a.size());
        for (int i = 0; i < e.size(); i++) {
            String[] er = e.get(i);
            String[] ar = a.get(i);
            assertEquals(er.length, ar.length, "列数不一致 行" + (i + 1));
            for (int j = 0; j < er.length; j++) {
                assertEquals(er[j], ar[j],
                        "不一致 " + xlsx + " 行" + (i + 1) + " 列" + (j + 1));
            }
        }
    }

    // ---- Seed SQL パーサ ----
    private static final Pattern RULE_INSERT = Pattern.compile(
            "INSERT INTO m_payment_mf_rule.*?VALUES\\s*(.*?);", Pattern.DOTALL);

    static List<MPaymentMfRule> parseRules(String sql) {
        List<MPaymentMfRule> out = new ArrayList<>();
        Matcher m = RULE_INSERT.matcher(sql);
        int idSeq = 1;
        while (m.find()) {
            for (String[] t : splitTuples(m.group(1))) {
                out.add(MPaymentMfRule.builder()
                        .id(idSeq++)
                        .sourceName(t[0])
                        .paymentSupplierCode(nullable(t[1]))
                        .ruleKind(t[2])
                        .debitAccount(t[3])
                        .debitSubAccount(nullable(t[4]))
                        .debitDepartment(nullable(t[5]))
                        .debitTaxCategory(t[6])
                        .creditAccount(t[7])
                        .creditSubAccount(nullable(t[8]))
                        .creditDepartment(nullable(t[9]))
                        .creditTaxCategory(t[10])
                        .summaryTemplate(t[11])
                        .tag(nullable(t[12]))
                        .priority(Integer.parseInt(t[13].trim()))
                        .delFlg("0")
                        .build());
            }
        }
        return out;
    }

    static String nullable(String v) { return (v == null || "NULL".equals(v)) ? null : v; }

    static List<String[]> splitTuples(String block) {
        List<String[]> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < block.length(); i++) {
            char c = block.charAt(i);
            if (inQuote) {
                if (c == '\'') {
                    if (i + 1 < block.length() && block.charAt(i + 1) == '\'') { cur.append('\''); i++; }
                    else { inQuote = false; cur.append(c); }
                } else cur.append(c);
            } else {
                if (c == '\'') { inQuote = true; cur.append(c); }
                else if (c == '(') { depth++; if (depth == 1) { cur.setLength(0); continue; } cur.append(c); }
                else if (c == ')') { depth--; if (depth == 0) { out.add(splitTuple(cur.toString())); cur.setLength(0); continue; } cur.append(c); }
                else if (depth > 0) cur.append(c);
            }
        }
        return out;
    }

    static String[] splitTuple(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inQuote) {
                if (c == '\'') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '\'') { cur.append('\''); i++; }
                    else inQuote = false;
                } else cur.append(c);
            } else {
                if (c == '\'') inQuote = true;
                else if (c == ',') { out.add(cur.toString().trim()); cur.setLength(0); }
                else cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out.toArray(new String[0]);
    }

    // ---- CSV パーサ (CP932) ----
    private static List<String[]> parseCsv(byte[] bytes) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new java.io.ByteArrayInputStream(bytes), CP932))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) continue;
                rows.add(line.split(",", -1));
            }
        }
        return rows;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2178ms:
C:\project\odamitsu-data-hub\backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
重要なズレが出ました。レビュー対象の説明では PaymentMfImport 系 2 テストは enabled とされていますが、実ファイル上はどちらも `@Disabled` のままで、runbook の「CI で常時実行」と矛盾しています。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\test\\java\\jp\\co\\oda32\\batch\\finance\\service\\PayableMonthlyAggregatorGoldenTest.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\mf\\MfOpeningJournalDetector.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "if (Test-Path .github) { Get-ChildItem -LiteralPath .github -Recurse -Force | Select-Object FullName,Length } else { Write-Output '.github missing' }" in C:\project\odamitsu-data-hub
2026-05-06T05:36:29.157284Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "if (Test-Path .github) { Get-ChildItem -LiteralPath .github -Recurse -Force | Select-Object FullName,Length } else { Write-Output '.github missing' }"` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "if (Test-Path .github) { Get-ChildItem -LiteralPath .github -Recurse -Force | Select-Object FullName,Length } else { Write-Output '.github missing' }"` rejected: blocked by policy
 succeeded in 1649ms:
package jp.co.oda32.domain.service.finance.mf;

import java.util.List;

/**
 * MF 買掛金 期首残高仕訳 (opening balance journal) の判定/検出ロジックを集約する util。
 * <p>
 * 旧実装は {@link MfJournalFetcher#isPayableOpeningJournal} と
 * {@code MfOpeningBalanceService.findOpeningJournal} に同種の判定ロジックが分散しており、
 * sub_account_name の null 扱いが両側で非対称 (G-impl-7) だった。
 * 本クラスで base 述語 {@link #isOpeningCandidate} を 1 箇所に集約し、
 * 取込側 ({@link #findBest}) のみが branch 数 (= 補助科目登録 supplier 数) で best を選ぶ adapter として動作する。
 * <p>
 * 判定 base: 「全 branch の credit 側に 買掛金 が出現し、debit 側に 買掛金 が一切無い複合仕訳」。
 * journalNumber == 1 の固定判定はしない (#1 以外の opening 仕訳を取りこぼすため)。
 * sub_account_name の null チェックは判定 base には含めない (持たない opening row は credit branch カウントから除外するだけ)。
 *
 * @since 2026-05-04 (SF-G08)
 */
public final class MfOpeningJournalDetector {

    private static final String MF_ACCOUNT_PAYABLE = "買掛金";

    private MfOpeningJournalDetector() {}

    /**
     * 買掛金 期首残高仕訳 (opening balance journal) かを判定する base 述語。
     * 判定: 全 branch の credit 側に 買掛金 が出現し、debit 側に 買掛金 が一切無い複合仕訳。
     * <p>
     * {@code m_supplier_opening_balance} へ取り込み済みのため、ledger / integrity / supplier-balances 側の
     * 月次 accumulation からは除外する (二重計上防止)。
     */
    public static boolean isOpeningCandidate(MfJournal j) {
        if (j == null || j.branches() == null || j.branches().isEmpty()) return false;
        boolean hasPayableCredit = false;
        for (MfJournal.MfBranch br : j.branches()) {
            MfJournal.MfSide cr = br.creditor();
            MfJournal.MfSide de = br.debitor();
            if (de != null && MF_ACCOUNT_PAYABLE.equals(de.accountName())) {
                return false; // debit 側に 買掛金 = 通常仕訳
            }
            if (cr != null && MF_ACCOUNT_PAYABLE.equals(cr.accountName())) {
                hasPayableCredit = true;
            }
        }
        return hasPayableCredit;
    }

    /**
     * 取り込み用: opening 候補の中から sub_account_name 付き credit branch 数が最大の journal を選ぶ。
     * <p>同 transactionDate に複数の opening 候補がある場合、より多くの supplier を持つ
     * (= 期首残高仕訳と推定される) journal を採用する。
     *
     * @return 最大 branch 数の opening journal、候補が無ければ null
     */
    public static MfJournal findBest(List<MfJournal> journals) {
        if (journals == null || journals.isEmpty()) return null;
        MfJournal best = null;
        int bestCount = 0;
        for (MfJournal j : journals) {
            if (!isOpeningCandidate(j)) continue;
            int payableCreditBranches = countPayableCreditWithSubAccount(j);
            if (payableCreditBranches > bestCount) {
                best = j;
                bestCount = payableCreditBranches;
            }
        }
        return best;
    }

    /** sub_account_name を持つ 買掛金 credit branch 数を返す (取込判定用)。 */
    private static int countPayableCreditWithSubAccount(MfJournal j) {
        if (j.branches() == null) return 0;
        int n = 0;
        for (MfJournal.MfBranch br : j.branches()) {
            MfJournal.MfSide cr = br.creditor();
            if (cr != null && MF_ACCOUNT_PAYABLE.equals(cr.accountName())
                    && cr.subAccountName() != null) {
                n++;
            }
        }
        return n;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1669ms:
package jp.co.oda32.batch.finance.service;

import jp.co.oda32.batch.finance.service.PayableMonthlyAggregator.PrevMonthData;
import jp.co.oda32.batch.finance.service.PayableMonthlyAggregator.SupplierAgg;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PayableMonthlyAggregator} のゴールデンマスタ単体テスト。
 * <p>
 * T3 案 D'' Phase 3 で追加 (2026-05-06)。
 * 改修起因の集計値変動を検知するため、純 Java fixture (builder-based) で代表シナリオを locked-in する。
 * <p>
 * 本テストが fail した時の判断:
 * <ul>
 *   <li><b>意図通りの仕様変更</b> → 期待値を改修目的に合致した値に更新 (PR で根拠を明記)</li>
 *   <li><b>意図外の副作用</b> → 改修見直し (過去確定月への影響、按分式バグ、手動確定行保護漏れ等)</li>
 * </ul>
 * <p>
 * 詳細手順: {@code claudedocs/runbook-finance-recalc-impact-analysis.md} §3 §4 §6
 * <p>
 * シナリオ:
 * <ul>
 *   <li>{@link Scenario1_SingleSupplierSingleMonth}: 単一 supplier × 単月 (10% 税率のみ) の按分基本</li>
 *   <li>{@link Scenario2_MultipleSuppliersMultipleTaxRates}: 複数 supplier × 税率混在 (8% + 10%) の按分</li>
 *   <li>{@link Scenario3_PaymentOnlyRowGeneration}: 前月 paid>0 / 当月 change=0 の payment-only 行生成</li>
 *   <li>{@link Scenario4_MfDebitOverrideAutoOnly}: MF debit 上書き (案 A) ? 全行 auto</li>
 *   <li>{@link Scenario5_MfDebitOverrideManualProtect}: MF debit 上書き ? 手動確定行 protect (案 A)</li>
 * </ul>
 *
 * @since 2026-05-06 (T3 案 D'' Phase 3)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayableMonthlyAggregatorGoldenTest {

    private static final Integer SHOP = 1;
    private static final LocalDate APRIL_20 = LocalDate.of(2026, 4, 20);
    private static final LocalDate MARCH_20 = LocalDate.of(2026, 3, 20);
    private static final BigDecimal RATE_8 = new BigDecimal("8");
    private static final BigDecimal RATE_10 = new BigDecimal("10");

    private TAccountsPayableSummaryService summaryService;
    private PayableMonthlyAggregator aggregator;

    @BeforeEach
    void setup() {
        summaryService = mock(TAccountsPayableSummaryService.class);
        aggregator = new PayableMonthlyAggregator(summaryService);
    }

    // ============================================================
    // Scenario 1: 単一 supplier × 単月 (10% 税率のみ)
    // ============================================================

    @Nested
    @DisplayName("Scenario 1: 単一 supplier × 単月 (10% のみ)")
    class Scenario1_SingleSupplierSingleMonth {

        @Test
        @DisplayName("前月 closing → 当月 opening 繰越 + 当月 change → closing")
        void single_supplier_single_month() {
            // 前月 (3/20): change 100,000 / payment 0 → closing 100,000
            TAccountsPayableSummary prev = row(101, MARCH_20, RATE_10)
                    .openingBalanceTaxIncluded(BigDecimal.ZERO)
                    .openingBalanceTaxExcluded(BigDecimal.ZERO)
                    .taxIncludedAmountChange(new BigDecimal("100000"))
                    .taxExcludedAmountChange(new BigDecimal("90909"))
                    .paymentAmountSettledTaxIncluded(BigDecimal.ZERO)
                    .paymentAmountSettledTaxExcluded(BigDecimal.ZERO)
                    .build();

            when(summaryService.findByTransactionMonth(MARCH_20)).thenReturn(List.of(prev));

            // 当月 (4/20): change 50,000 (新規仕入)
            TAccountsPayableSummary curr = row(101, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("50000"))
                    .taxExcludedAmountChange(new BigDecimal("45455"))
                    .build();

            PrevMonthData prevData = aggregator.buildPrevMonthData(APRIL_20);
            aggregator.applyOpenings(List.of(curr), prevData);

            // 当月 opening = 前月 closing = 100,000 (繰越)
            assertThat(curr.getOpeningBalanceTaxIncluded()).isEqualByComparingTo("100000");
            assertThat(curr.getOpeningBalanceTaxExcluded()).isEqualByComparingTo("90909");
        }

        @Test
        @DisplayName("前月 paid>0 → 当月 payment_settled に按分 (単一行は全額)")
        void payment_settled_single_row() {
            // 前月: change 100,000 / verified 100,000 (paid)
            TAccountsPayableSummary prev = row(102, MARCH_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("100000"))
                    .taxExcludedAmountChange(new BigDecimal("90909"))
                    .verifiedAmount(new BigDecimal("100000"))
                    .verifiedManually(true)
                    .build();
            when(summaryService.findByTransactionMonth(MARCH_20)).thenReturn(List.of(prev));

            // 当月 (4/20): change 30,000
            TAccountsPayableSummary curr = row(102, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("30000"))
                    .taxExcludedAmountChange(new BigDecimal("27273"))
                    .build();

            PrevMonthData prevData = aggregator.buildPrevMonthData(APRIL_20);
            aggregator.applyPaymentSettled(List.of(curr), prevData);

            // 単一行のため前月 paid 全額が当月行に乗る
            assertThat(curr.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("100000");
            // 税抜は paidIncl × changeExcl / changeIncl = 100000 × 27273 / 30000 = 90910 (DOWN)
            // ただし単一行 = 最終行のため paidExcl 直接代入。SupplierAgg.paidExcl = 100000 × 90909 / 100000 = 90909
            assertThat(curr.getPaymentAmountSettledTaxExcluded()).isEqualByComparingTo("90909");
        }
    }

    // ============================================================
    // Scenario 2: 複数 supplier × 税率混在
    // ============================================================

    @Nested
    @DisplayName("Scenario 2: 複数 supplier × 税率混在 (8% + 10%)")
    class Scenario2_MultipleSuppliersMultipleTaxRates {

        @Test
        @DisplayName("supplier 内税率別 change 比で payment 按分 + 最終行端数吸収")
        void multi_tax_rate_apportionment() {
            // 前月: supplier 201, 8% change 30,000 + 10% change 70,000 → paid 集計 95,000 (全行同値)
            TAccountsPayableSummary prev8 = row(201, MARCH_20, RATE_8)
                    .taxIncludedAmountChange(new BigDecimal("30000"))
                    .taxExcludedAmountChange(new BigDecimal("27778"))
                    .verifiedAmount(new BigDecimal("95000"))
                    .build();
            TAccountsPayableSummary prev10 = row(201, MARCH_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("70000"))
                    .taxExcludedAmountChange(new BigDecimal("63636"))
                    .verifiedAmount(new BigDecimal("95000"))
                    .build();
            when(summaryService.findByTransactionMonth(MARCH_20)).thenReturn(List.of(prev8, prev10));

            // 当月: 同じ supplier に 8% / 10% の change あり
            TAccountsPayableSummary curr8 = row(201, APRIL_20, RATE_8)
                    .taxIncludedAmountChange(new BigDecimal("20000"))
                    .taxExcludedAmountChange(new BigDecimal("18519"))
                    .build();
            TAccountsPayableSummary curr10 = row(201, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("80000"))
                    .taxExcludedAmountChange(new BigDecimal("72727"))
                    .build();

            PrevMonthData prevData = aggregator.buildPrevMonthData(APRIL_20);
            aggregator.applyPaymentSettled(new ArrayList<>(List.of(curr8, curr10)), prevData);

            // 全行 paid 合計 = 95,000 (全行同値ロジックで代表値、SUM 190,000 にしない)
            BigDecimal totalPaidIncl = curr8.getPaymentAmountSettledTaxIncluded()
                    .add(curr10.getPaymentAmountSettledTaxIncluded());
            assertThat(totalPaidIncl).isEqualByComparingTo("95000");

            // 8% 行 = 95000 × 20000 / 100000 = 19000 (DOWN)
            assertThat(curr8.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("19000");
            // 10% (最終行) = 95000 - 19000 = 76000
            assertThat(curr10.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("76000");
        }

        @Test
        @DisplayName("change=0 supplier は applyPaymentSettled で skip (payment-only に委ねる)")
        void change_zero_supplier_skipped() {
            // 前月: supplier 202 に paid 50,000 / change 50,000
            TAccountsPayableSummary prev = row(202, MARCH_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("50000"))
                    .taxExcludedAmountChange(new BigDecimal("45455"))
                    .verifiedAmount(new BigDecimal("50000"))
                    .build();
            when(summaryService.findByTransactionMonth(MARCH_20)).thenReturn(List.of(prev));

            // 当月: 同 supplier に change 0 (=支払のみ完了で当月仕入無し)
            TAccountsPayableSummary curr = row(202, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(BigDecimal.ZERO)
                    .taxExcludedAmountChange(BigDecimal.ZERO)
                    .build();

            PrevMonthData prevData = aggregator.buildPrevMonthData(APRIL_20);
            aggregator.applyPaymentSettled(new ArrayList<>(List.of(curr)), prevData);

            // applyPaymentSettled は change=0 で skip → payment は 0 のまま (payment-only 行で別処理)
            assertThat(curr.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("0");
        }
    }

    // ============================================================
    // Scenario 3: payment-only 行生成 (R2 反映)
    // ============================================================

    @Nested
    @DisplayName("Scenario 3: payment-only 行生成")
    class Scenario3_PaymentOnlyRowGeneration {

        @Test
        @DisplayName("前月 paid>0 / 当月 supplier 不在 → payment-only 行生成")
        void payment_only_row_for_absent_supplier() {
            // 前月: supplier 301 に paid 80,000
            TAccountsPayableSummary prev = row(301, MARCH_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("80000"))
                    .taxExcludedAmountChange(new BigDecimal("72727"))
                    .verifiedAmount(new BigDecimal("80000"))
                    .build();
            when(summaryService.findByTransactionMonth(MARCH_20)).thenReturn(List.of(prev));

            // 当月: 別 supplier (302) しかいない → 301 は payment-only 対象
            TAccountsPayableSummary other = row(302, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("10000"))
                    .taxExcludedAmountChange(new BigDecimal("9091"))
                    .build();

            PrevMonthData prevData = aggregator.buildPrevMonthData(APRIL_20);
            Map<String, TAccountsPayableSummary> currMap = new HashMap<>();
            currMap.put(PayableMonthlyAggregator.rowKey(other), other);

            List<TAccountsPayableSummary> generated = aggregator.generatePaymentOnlyRows(
                    prevData, List.of(other), APRIL_20, currMap);

            assertThat(generated).hasSize(1);
            TAccountsPayableSummary po = generated.get(0);
            assertThat(po.getSupplierNo()).isEqualTo(301);
            assertThat(po.getTransactionMonth()).isEqualTo(APRIL_20);
            assertThat(po.getIsPaymentOnly()).isTrue();
            assertThat(po.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("80000");
            // opening = 前月 closing 合計 = 80000 - 0 = 80000 (前月 payment_settled = 0)
            assertThat(po.getOpeningBalanceTaxIncluded()).isEqualByComparingTo("80000");
            assertThat(po.getVerifiedAmount()).isNull();
            assertThat(po.getVerifiedManually()).isFalse();
            assertThat(po.getVerificationNote()).isEqualTo("[payment-only] " + APRIL_20);
        }

        @Test
        @DisplayName("手動確定行は payment-only で上書きされない (検証済み振込明細を保護)")
        void manual_verified_row_protected_from_payment_only() {
            // 前月: supplier 303 に paid 60,000
            TAccountsPayableSummary prev = row(303, MARCH_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("60000"))
                    .taxExcludedAmountChange(new BigDecimal("54545"))
                    .verifiedAmount(new BigDecimal("60000"))
                    .build();
            when(summaryService.findByTransactionMonth(MARCH_20)).thenReturn(List.of(prev));

            // 当月: 同 supplier に change=0 だが手動確定済み行が既に存在
            TAccountsPayableSummary manual = row(303, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(BigDecimal.ZERO)
                    .taxExcludedAmountChange(BigDecimal.ZERO)
                    .verifiedAmount(new BigDecimal("70000"))
                    .verifiedManually(true)
                    .build();

            PrevMonthData prevData = aggregator.buildPrevMonthData(APRIL_20);
            Map<String, TAccountsPayableSummary> currMap = new HashMap<>();
            currMap.put(PayableMonthlyAggregator.rowKey(manual), manual);

            List<TAccountsPayableSummary> generated = aggregator.generatePaymentOnlyRows(
                    prevData, List.of(manual), APRIL_20, currMap);

            // 手動確定行は payment-only で上書きされない
            assertThat(generated).isEmpty();
            assertThat(manual.getVerifiedAmount()).isEqualByComparingTo("70000");
            assertThat(manual.getVerifiedManually()).isTrue();
            // payment-only マーカーも付かない
            assertThat(manual.getIsPaymentOnly()).isNotEqualTo(Boolean.TRUE);
        }
    }

    // ============================================================
    // Scenario 4: MF debit 上書き (案 A) ? 全行 auto
    // ============================================================

    @Nested
    @DisplayName("Scenario 4: MF debit 上書き (案 A) ? 全行 auto")
    class Scenario4_MfDebitOverrideAutoOnly {

        @Test
        @DisplayName("MF debit が verified より大きい時は MF debit 値で上書き")
        void mf_debit_overrides_verified_amount() {
            // 当月: supplier 401 に 8% / 10% の change あり (改修後 paymentSettled は 0 想定)
            TAccountsPayableSummary curr8 = row(401, APRIL_20, RATE_8)
                    .taxIncludedAmountChange(new BigDecimal("30000"))
                    .taxExcludedAmountChange(new BigDecimal("27778"))
                    .build();
            TAccountsPayableSummary curr10 = row(401, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("70000"))
                    .taxExcludedAmountChange(new BigDecimal("63636"))
                    .build();

            // MF debit = 100,000 (= 当月買掛金 debit 合計)
            Map<Integer, BigDecimal> mfDebit = Map.of(401, new BigDecimal("100000"));

            aggregator.overrideWithMfDebit(new ArrayList<>(List.of(curr8, curr10)), mfDebit, APRIL_20);

            // 8% 行 = 100000 × 30000 / 100000 = 30000 (DOWN)
            assertThat(curr8.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("30000");
            // 10% (最終行) = 100000 - 30000 = 70000
            assertThat(curr10.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("70000");

            // 合計 = 100,000
            BigDecimal sum = curr8.getPaymentAmountSettledTaxIncluded()
                    .add(curr10.getPaymentAmountSettledTaxIncluded());
            assertThat(sum).isEqualByComparingTo("100000");
        }

        @Test
        @DisplayName("MF debit map に supplier が無い時は何もしない")
        void no_mf_debit_supplier_no_op() {
            TAccountsPayableSummary curr = row(402, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("50000"))
                    .taxExcludedAmountChange(new BigDecimal("45455"))
                    .paymentAmountSettledTaxIncluded(new BigDecimal("12345"))
                    .build();

            aggregator.overrideWithMfDebit(new ArrayList<>(List.of(curr)),
                    Map.of(999, new BigDecimal("99999")), APRIL_20);

            // 既存値が残る (改修されない)
            assertThat(curr.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("12345");
        }

        @Test
        @DisplayName("空 / null map は no-op")
        void null_or_empty_map_no_op() {
            TAccountsPayableSummary curr = row(403, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("50000"))
                    .taxExcludedAmountChange(new BigDecimal("45455"))
                    .paymentAmountSettledTaxIncluded(new BigDecimal("11111"))
                    .build();

            aggregator.overrideWithMfDebit(new ArrayList<>(List.of(curr)), null, APRIL_20);
            assertThat(curr.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("11111");

            aggregator.overrideWithMfDebit(new ArrayList<>(List.of(curr)), Map.of(), APRIL_20);
            assertThat(curr.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("11111");
        }
    }

    // ============================================================
    // Scenario 5: MF debit 上書き ? 手動確定行 protect
    // ============================================================

    @Nested
    @DisplayName("Scenario 5: MF debit 上書き ? 手動確定行 protect")
    class Scenario5_MfDebitOverrideManualProtect {

        @Test
        @DisplayName("手動確定行 paymentSettled は MF debit で上書きされず、残額のみ auto 行に按分")
        void manual_row_payment_settled_protected() {
            // supplier 501: 手動確定行 (8%) と 自動行 (10%) が混在
            TAccountsPayableSummary manual8 = row(501, APRIL_20, RATE_8)
                    .taxIncludedAmountChange(new BigDecimal("30000"))
                    .taxExcludedAmountChange(new BigDecimal("27778"))
                    .verifiedManually(true)
                    .paymentAmountSettledTaxIncluded(new BigDecimal("28000"))
                    .paymentAmountSettledTaxExcluded(new BigDecimal("25926"))
                    .build();
            TAccountsPayableSummary auto10 = row(501, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("70000"))
                    .taxExcludedAmountChange(new BigDecimal("63636"))
                    .build();

            // MF debit = 98,000 → 手動 28,000 を引いた 70,000 を auto 行に上書き
            Map<Integer, BigDecimal> mfDebit = Map.of(501, new BigDecimal("98000"));

            aggregator.overrideWithMfDebit(new ArrayList<>(List.of(manual8, auto10)), mfDebit, APRIL_20);

            // 手動確定行は値が変わらない (保護)
            assertThat(manual8.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("28000");
            assertThat(manual8.getPaymentAmountSettledTaxExcluded()).isEqualByComparingTo("25926");
            // 自動行 = 70,000 (残額) ? 単独 auto 行のため最終行 = 全額
            assertThat(auto10.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("70000");
        }

        @Test
        @DisplayName("全行手動確定の supplier は overrideWithMfDebit で skip")
        void all_manual_rows_skipped() {
            TAccountsPayableSummary manual = row(502, APRIL_20, RATE_10)
                    .taxIncludedAmountChange(new BigDecimal("50000"))
                    .taxExcludedAmountChange(new BigDecimal("45455"))
                    .verifiedManually(true)
                    .paymentAmountSettledTaxIncluded(new BigDecimal("47000"))
                    .paymentAmountSettledTaxExcluded(new BigDecimal("42727"))
                    .build();

            Map<Integer, BigDecimal> mfDebit = Map.of(502, new BigDecimal("60000"));
            aggregator.overrideWithMfDebit(new ArrayList<>(List.of(manual)), mfDebit, APRIL_20);

            // 手動確定行は値が変わらない (skip)
            assertThat(manual.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("47000");
            assertThat(manual.getPaymentAmountSettledTaxExcluded()).isEqualByComparingTo("42727");
        }
    }

    // ============================================================
    // Scenario 6: SupplierAgg 計算 (verified 全行同値 → 代表値、不一致 → SUM)
    // ============================================================

    @Nested
    @DisplayName("Scenario 6: SupplierAgg 計算")
    class Scenario6_SupplierAgg {

        @Test
        @DisplayName("verified 全行同値 → 代表値 (二重計上回避)")
        void all_same_verified_uses_representative() {
            List<TAccountsPayableSummary> group = List.of(
                    row(601, MARCH_20, RATE_8)
                            .taxIncludedAmountChange(new BigDecimal("30000"))
                            .taxExcludedAmountChange(new BigDecimal("27778"))
                            .verifiedAmount(new BigDecimal("100000"))
                            .build(),
                    row(601, MARCH_20, RATE_10)
                            .taxIncludedAmountChange(new BigDecimal("70000"))
                            .taxExcludedAmountChange(new BigDecimal("63636"))
                            .verifiedAmount(new BigDecimal("100000"))
                            .build()
            );

            SupplierAgg agg = PayableMonthlyAggregator.computeSupplierAgg(group);
            assertThat(agg.paidIncl()).isEqualByComparingTo("100000");
            assertThat(agg.changeInclTotal()).isEqualByComparingTo("100000");
        }

        @Test
        @DisplayName("verified 不一致 → SUM (DB 異常検知の安全側挙動)")
        void mismatched_verified_falls_back_to_sum() {
            List<TAccountsPayableSummary> group = List.of(
                    row(602, MARCH_20, RATE_8)
                            .taxIncludedAmountChange(new BigDecimal("30000"))
                            .taxExcludedAmountChange(new BigDecimal("27778"))
                            .verifiedAmount(new BigDecimal("100000"))
                            .build(),
                    row(602, MARCH_20, RATE_10)
                            .taxIncludedAmountChange(new BigDecimal("70000"))
                            .taxExcludedAmountChange(new BigDecimal("63636"))
                            .verifiedAmount(new BigDecimal("50000"))   // ← 不一致
                            .build()
            );

            SupplierAgg agg = PayableMonthlyAggregator.computeSupplierAgg(group);
            // 不一致 → SUM = 100000 + 50000 = 150000
            assertThat(agg.paidIncl()).isEqualByComparingTo("150000");
        }

        @Test
        @DisplayName("payment-only 行は paid 算出から除外 (R4)")
        void payment_only_row_excluded_from_paid() {
            List<TAccountsPayableSummary> group = List.of(
                    row(603, MARCH_20, RATE_10)
                            .taxIncludedAmountChange(new BigDecimal("50000"))
                            .taxExcludedAmountChange(new BigDecimal("45455"))
                            .verifiedAmount(new BigDecimal("50000"))
                            .build(),
                    row(603, MARCH_20, RATE_10)
                            .isPaymentOnly(true)
                            .verifiedAmount(new BigDecimal("99999"))   // payment-only は無視
                            .build()
            );

            SupplierAgg agg = PayableMonthlyAggregator.computeSupplierAgg(group);
            assertThat(agg.paidIncl()).isEqualByComparingTo("50000");
        }
    }

    // ============================================================
    // 共通 builder
    // ============================================================

    private TAccountsPayableSummary.TAccountsPayableSummaryBuilder row(
            Integer supplierNo, LocalDate month, BigDecimal taxRate) {
        return TAccountsPayableSummary.builder()
                .shopNo(SHOP)
                .supplierNo(supplierNo)
                .supplierCode("S" + supplierNo)
                .transactionMonth(month)
                .taxRate(taxRate)
                .openingBalanceTaxIncluded(BigDecimal.ZERO)
                .openingBalanceTaxExcluded(BigDecimal.ZERO)
                .paymentAmountSettledTaxIncluded(BigDecimal.ZERO)
                .paymentAmountSettledTaxExcluded(BigDecimal.ZERO)
                .autoAdjustedAmount(BigDecimal.ZERO)
                .verifiedManually(false)
                .isPaymentOnly(false);
    }

}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

2026-05-06T05:36:38.613301Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Force | Where-Object { $_.Name -eq '"'.github' } | Select-Object Name,Mode"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Force | Where-Object { $_.Name -eq '"'.github' } | Select-Object Name,Mode" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Path backend\\src\\test\\resources\\paymentmf -Force | Select-Object Name,Length" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Force | Where-Object { $_.Name -eq '"'.github' } | Select-Object Name,Mode"` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Path backend\\src\\test\\resources\\paymentmf -Force | Select-Object Name,Length"` rejected: blocked by policy
2026-05-06T05:36:38.613755Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Path backend\\src\\test\\resources\\paymentmf -Force | Select-Object Name,Length"` rejected: blocked by policy
2026-05-06T05:36:38.617302Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path backend -Filter build.gradle* -Force | Select-Object Name,Length'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path backend -Filter build.gradle* -Force | Select-Object Name,Length' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path backend -Filter build.gradle* -Force | Select-Object Name,Length'` rejected: blocked by policy
codex
追加で、Aggregator の runbook 記載パスが実際の package と違っています。ドキュメントは `domain/service/finance` と案内していますが、実体は `batch/finance/service` 配下です。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path claudedocs\\runbook-finance-recalc-impact-analysis.md -Pattern \"CI|Step 3|Step 7|Step 8|PaymentMfImport|PayableMonthlyAggregator|domain/service/finance|過去確定月|Fixture\"" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\test\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportServiceGoldenMasterTest.java -Pattern \"@Disabled|CsvSource|TODO|既存 fixture\"" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\test\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportServiceAuxRowTest.java -Pattern \"@Disabled|SUMMARY|TODO|案 D\"" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\test\\java\\jp\\co\\oda32\\batch\\finance\\service\\PayableMonthlyAggregatorGoldenTest.java -Pattern \"Scenario|DisplayName|closing|tax|決算|payment-only|manual|SupplierAgg\"" in C:\project\odamitsu-data-hub
 succeeded in 1724ms:

claudedocs\runbook-finance-recalc-impact-analysis.md:5:関連: T3 設計検討、P1-03 案 D-2 (PaymentMfImport supplier 別 att
ribute 展開)
claudedocs\runbook-finance-recalc-impact-analysis.md:11:集計ロジックの改修・試行錯誤で **過去確定月の集計値が意図せず変
動する問題** を防ぐ。
claudedocs\runbook-finance-recalc-impact-analysis.md:13:具体的には、以下のような事故を CI レベルで検知し、merge 前に手
戻りさせる:
claudedocs\runbook-finance-recalc-impact-analysis.md:15:- `PayableMonthlyAggregator.applyPaymentSettled` の按分式変更で
過去 6 ヶ月の `payment_amount_settled_*` が変わる
claudedocs\runbook-finance-recalc-impact-analysis.md:17:- `PaymentMfImportService` の振込手数料値引按分ロジック変更で過
去 CSV と差分が出る
claudedocs\runbook-finance-recalc-impact-analysis.md:49:| Aggregator | `PayableMonthlyAggregator`, `MfPaymentAggregator
` |
claudedocs\runbook-finance-recalc-impact-analysis.md:54:| Import Service | `PaymentMfImportService`, `InvoiceImportServ
ice`, `CashBookConvertService` |
claudedocs\runbook-finance-recalc-impact-analysis.md:64:### Step 3: 改修前のスナップショット取得 (main branch)
claudedocs\runbook-finance-recalc-impact-analysis.md:100:Step 3 と **同じ API / SQL** を実行、結果を `snapshot-after-*.
json` / `snapshot-after-summary.csv` に保存。
claudedocs\runbook-finance-recalc-impact-analysis.md:117:### Step 7: 差分の妥当性判定
claudedocs\runbook-finance-recalc-impact-analysis.md:127:### Step 8: 過去確定月の保護ルール
claudedocs\runbook-finance-recalc-impact-analysis.md:147:CI で常時実行されるゴールデンマスタテストで機械的に検出する。
claudedocs\runbook-finance-recalc-impact-analysis.md:151:| テスト名 | 対象 Service | fixture |
claudedocs\runbook-finance-recalc-impact-analysis.md:154:| `PaymentMfImportServiceGoldenMasterTest` | `PaymentMfImportS
ervice.convert()` | `src/test/resources/paymentmf/*.xlsx` + `*.csv` (2 本、enabled) |
claudedocs\runbook-finance-recalc-impact-analysis.md:155:| `PaymentMfImportServiceAuxRowTest` | `PaymentMfImportService
.applyVerification()` | 同上 (enabled) |
claudedocs\runbook-finance-recalc-impact-analysis.md:156:| `PayableMonthlyAggregatorGoldenTest` | `PayableMonthlyAggreg
ator` | 純 Java builder fixture (`backend/src/test/java/.../PayableMonthlyAggregatorGoldenTest.java`、14 ケース) |
claudedocs\runbook-finance-recalc-impact-analysis.md:157:| `SupplierBalancesServiceGoldenTest` | `SupplierBalancesServi
ce` | 純 Java builder fixture (`backend/src/test/java/.../SupplierBalancesServiceGoldenTest.java`、8 ケース) |
claudedocs\runbook-finance-recalc-impact-analysis.md:164:   - **意図通り** → fixture 更新 (§5 / §6 参照) → commit
claudedocs\runbook-finance-recalc-impact-analysis.md:165:   - **意図外** → 改修見直し (過去確定月への副作用)
claudedocs\runbook-finance-recalc-impact-analysis.md:169:### Fixture 更新の原則
claudedocs\runbook-finance-recalc-impact-analysis.md:171:- fixture 更新は **必ず user 環境** で実 Excel / 実 DB 経由で
再生成する (agent 環境では実 Excel / MF 接続不可)
claudedocs\runbook-finance-recalc-impact-analysis.md:172:- 更新前に旧 fixture を git に残す (revert 用バックアップ)
claudedocs\runbook-finance-recalc-impact-analysis.md:173:- PR 説明に「fixture 更新理由 + 差分件数 + 検収ログ」を記載
claudedocs\runbook-finance-recalc-impact-analysis.md:177:## 5. PaymentMfImport ゴールデンマスタ Fixture 更新手順
claudedocs\runbook-finance-recalc-impact-analysis.md:196:8. `PaymentMfImportServiceGoldenMasterTest` の `@CsvSource` を
更新:
claudedocs\runbook-finance-recalc-impact-analysis.md:201:10. `./gradlew test --tests '*PaymentMfImportServiceGoldenMast
erTest*'` で PASS 確認
claudedocs\runbook-finance-recalc-impact-analysis.md:202:11. `PaymentMfImportServiceAuxRowTest` も同様に再有効化 (新 as
sertion: SUMMARY 行が 0 件、PAYABLE_* 副行が supplier 単位に展開される)
claudedocs\runbook-finance-recalc-impact-analysis.md:213:## 6. PayableMonthlyAggregator / SupplierBalancesService Fixtu
re 更新手順
claudedocs\runbook-finance-recalc-impact-analysis.md:215:### PayableMonthlyAggregator
claudedocs\runbook-finance-recalc-impact-analysis.md:217:純 Java unit test。fixture は test code 内 builder で表現:
claudedocs\runbook-finance-recalc-impact-analysis.md:219:1. `backend/src/test/java/jp/co/oda32/batch/finance/service/Pa
yableMonthlyAggregatorGoldenTest.java` を編集
claudedocs\runbook-finance-recalc-impact-analysis.md:221:   - 期待値: `assertThat(...).isEqualByComparingTo("...")` で 
BigDecimal 比較
claudedocs\runbook-finance-recalc-impact-analysis.md:223:3. `./gradlew test --tests '*PayableMonthlyAggregatorGoldenTes
t*'` で PASS 確認
claudedocs\runbook-finance-recalc-impact-analysis.md:227:1. `backend/src/test/java/jp/co/oda32/domain/service/finance/S
upplierBalancesServiceGoldenTest.java` を編集
claudedocs\runbook-finance-recalc-impact-analysis.md:234:- 純 Java builder fixture なので **DB 接続も MF 接続も不要** (
agent 環境でも編集可)
claudedocs\runbook-finance-recalc-impact-analysis.md:235:- BigDecimal の比較は必ず `isEqualByComparingTo("...")` (scale
 違いで isEqualTo は失敗する)
claudedocs\runbook-finance-recalc-impact-analysis.md:248:- [ ] main branch でのスナップショット取得済 (§3 Step 3)
claudedocs\runbook-finance-recalc-impact-analysis.md:250:- [ ] 差分分析を実施し、過去確定月への副作用がないことを確認し
た (§3 Step 7-8)
claudedocs\runbook-finance-recalc-impact-analysis.md:251:- [ ] ゴールデンマスタテスト (`CashBookConvert*`, `PaymentMfIm
port*`, `*Aggregator*`, `*Balances*`) が PASS する
claudedocs\runbook-finance-recalc-impact-analysis.md:253:- [ ] CI で全テスト PASS
claudedocs\runbook-finance-recalc-impact-analysis.md:257:  - fixture 更新の有無 (あり/なし、ある場合は理由)
claudedocs\runbook-finance-recalc-impact-analysis.md:264:- `claudedocs/design-payment-mf-import.md` ? PaymentMfImport 
仕様 (P1-03 案 D-2 適用後の構造)
claudedocs\runbook-finance-recalc-impact-analysis.md:267:- `claudedocs/design-phase-b-prime-payment-settled.md` ? Payab
leMonthlyAggregator の設計 (Phase B')

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2707ms:

backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceGoldenMasterTest.java:12:import org.juni
t.jupiter.params.provider.CsvSource;
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceGoldenMasterTest.java:42: * 既存 fixture
 (買掛仕入MFインポートファイル_*.csv) は SUMMARY 集約 (振込手数料値引/早払収益 合計仕訳 2 行)
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceGoldenMasterTest.java:52: * <p>TODO(T3 
案 D'' Phase 2): user 環境で fixture 再生成後に @Disabled 解除。
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceGoldenMasterTest.java:62: *   <li>{@code
 @CsvSource} を新 fixture に更新</li>
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceGoldenMasterTest.java:63: *   <li>{@code
 @Disabled} を削除して PASS 確認</li>
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceGoldenMasterTest.java:66:@Disabled("P1-0
3 案 D 適用 (2026-05-04): supplier 別 attribute 構造に変更。"
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceGoldenMasterTest.java:67:        + "既存
 fixture (SUMMARY 集約 2 行構造) は無効。"
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceGoldenMasterTest.java:68:        + "TODO
(T3 案 D'' Phase 2): user 環境で fixture 再生成後に再有効化。"
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceGoldenMasterTest.java:92:    @CsvSource(
{

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3759ms:

backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:4:import jp.co.oda32.ba
tch.finance.service.PayableMonthlyAggregator.SupplierAgg;
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:8:import org.junit.jupi
ter.api.DisplayName;
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:43: *   <li>{@link Scen
ario1_SingleSupplierSingleMonth}: 単一 supplier × 単月 (10% 税率のみ) の按分基本</li>
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:44: *   <li>{@link Scen
ario2_MultipleSuppliersMultipleTaxRates}: 複数 supplier × 税率混在 (8% + 10%) の按分</li>
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:45: *   <li>{@link Scen
ario3_PaymentOnlyRowGeneration}: 前月 paid>0 / 当月 change=0 の payment-only 行生成</li>
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:46: *   <li>{@link Scen
ario4_MfDebitOverrideAutoOnly}: MF debit 上書き (案 A) ? 全行 auto</li>
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:47: *   <li>{@link Scen
ario5_MfDebitOverrideManualProtect}: MF debit 上書き ? 手動確定行 protect (案 A)</li>
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:72:    // Scenario 1: 
単一 supplier × 単月 (10% 税率のみ)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:76:    @DisplayName("Sc
enario 1: 単一 supplier × 単月 (10% のみ)")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:77:    class Scenario1_
SingleSupplierSingleMonth {
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:80:        @DisplayName
("前月 closing → 当月 opening 繰越 + 当月 change → closing")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:82:            // 前月 
(3/20): change 100,000 / payment 0 → closing 100,000
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:84:                    
.openingBalanceTaxIncluded(BigDecimal.ZERO)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:85:                    
.openingBalanceTaxExcluded(BigDecimal.ZERO)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:86:                    
.taxIncludedAmountChange(new BigDecimal("100000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:87:                    
.taxExcludedAmountChange(new BigDecimal("90909"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:88:                    
.paymentAmountSettledTaxIncluded(BigDecimal.ZERO)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:89:                    
.paymentAmountSettledTaxExcluded(BigDecimal.ZERO)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:96:                    
.taxIncludedAmountChange(new BigDecimal("50000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:97:                    
.taxExcludedAmountChange(new BigDecimal("45455"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:103:            // 当月
 opening = 前月 closing = 100,000 (繰越)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:104:            assertT
hat(curr.getOpeningBalanceTaxIncluded()).isEqualByComparingTo("100000");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:105:            assertT
hat(curr.getOpeningBalanceTaxExcluded()).isEqualByComparingTo("90909");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:109:        @DisplayNam
e("前月 paid>0 → 当月 payment_settled に按分 (単一行は全額)")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:113:                   
 .taxIncludedAmountChange(new BigDecimal("100000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:114:                   
 .taxExcludedAmountChange(new BigDecimal("90909"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:116:                   
 .verifiedManually(true)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:122:                   
 .taxIncludedAmountChange(new BigDecimal("30000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:123:                   
 .taxExcludedAmountChange(new BigDecimal("27273"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:130:            assertT
hat(curr.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("100000");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:132:            // ただ
し単一行 = 最終行のため paidExcl 直接代入。SupplierAgg.paidExcl = 100000 × 90909 / 100000 = 90909
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:133:            assertT
hat(curr.getPaymentAmountSettledTaxExcluded()).isEqualByComparingTo("90909");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:138:    // Scenario 2: 
複数 supplier × 税率混在
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:142:    @DisplayName("S
cenario 2: 複数 supplier × 税率混在 (8% + 10%)")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:143:    class Scenario2
_MultipleSuppliersMultipleTaxRates {
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:146:        @DisplayNam
e("supplier 内税率別 change 比で payment 按分 + 最終行端数吸収")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:147:        void multi_
tax_rate_apportionment() {
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:150:                   
 .taxIncludedAmountChange(new BigDecimal("30000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:151:                   
 .taxExcludedAmountChange(new BigDecimal("27778"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:155:                   
 .taxIncludedAmountChange(new BigDecimal("70000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:156:                   
 .taxExcludedAmountChange(new BigDecimal("63636"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:163:                   
 .taxIncludedAmountChange(new BigDecimal("20000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:164:                   
 .taxExcludedAmountChange(new BigDecimal("18519"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:167:                   
 .taxIncludedAmountChange(new BigDecimal("80000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:168:                   
 .taxExcludedAmountChange(new BigDecimal("72727"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:175:            BigDeci
mal totalPaidIncl = curr8.getPaymentAmountSettledTaxIncluded()
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:176:                   
 .add(curr10.getPaymentAmountSettledTaxIncluded());
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:180:            assertT
hat(curr8.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("19000");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:182:            assertT
hat(curr10.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("76000");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:186:        @DisplayNam
e("change=0 supplier は applyPaymentSettled で skip (payment-only に委ねる)")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:190:                   
 .taxIncludedAmountChange(new BigDecimal("50000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:191:                   
 .taxExcludedAmountChange(new BigDecimal("45455"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:198:                   
 .taxIncludedAmountChange(BigDecimal.ZERO)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:199:                   
 .taxExcludedAmountChange(BigDecimal.ZERO)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:205:            // appl
yPaymentSettled は change=0 で skip → payment は 0 のまま (payment-only 行で別処理)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:206:            assertT
hat(curr.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("0");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:211:    // Scenario 3: 
payment-only 行生成 (R2 反映)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:215:    @DisplayName("S
cenario 3: payment-only 行生成")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:216:    class Scenario3
_PaymentOnlyRowGeneration {
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:219:        @DisplayNam
e("前月 paid>0 / 当月 supplier 不在 → payment-only 行生成")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:223:                   
 .taxIncludedAmountChange(new BigDecimal("80000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:224:                   
 .taxExcludedAmountChange(new BigDecimal("72727"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:229:            // 当月
: 別 supplier (302) しかいない → 301 は payment-only 対象
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:231:                   
 .taxIncludedAmountChange(new BigDecimal("10000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:232:                   
 .taxExcludedAmountChange(new BigDecimal("9091"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:247:            assertT
hat(po.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("80000");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:248:            // open
ing = 前月 closing 合計 = 80000 - 0 = 80000 (前月 payment_settled = 0)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:249:            assertT
hat(po.getOpeningBalanceTaxIncluded()).isEqualByComparingTo("80000");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:251:            assertT
hat(po.getVerifiedManually()).isFalse();
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:252:            assertT
hat(po.getVerificationNote()).isEqualTo("[payment-only] " + APRIL_20);
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:256:        @DisplayNam
e("手動確定行は payment-only で上書きされない (検証済み振込明細を保護)")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:257:        void manual
_verified_row_protected_from_payment_only() {
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:260:                   
 .taxIncludedAmountChange(new BigDecimal("60000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:261:                   
 .taxExcludedAmountChange(new BigDecimal("54545"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:267:            TAccoun
tsPayableSummary manual = row(303, APRIL_20, RATE_10)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:268:                   
 .taxIncludedAmountChange(BigDecimal.ZERO)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:269:                   
 .taxExcludedAmountChange(BigDecimal.ZERO)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:271:                   
 .verifiedManually(true)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:276:            currMap
.put(PayableMonthlyAggregator.rowKey(manual), manual);
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:279:                   
 prevData, List.of(manual), APRIL_20, currMap);
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:281:            // 手動
確定行は payment-only で上書きされない
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:283:            assertT
hat(manual.getVerifiedAmount()).isEqualByComparingTo("70000");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:284:            assertT
hat(manual.getVerifiedManually()).isTrue();
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:285:            // paym
ent-only マーカーも付かない
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:286:            assertT
hat(manual.getIsPaymentOnly()).isNotEqualTo(Boolean.TRUE);
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:291:    // Scenario 4: 
MF debit 上書き (案 A) ? 全行 auto
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:295:    @DisplayName("S
cenario 4: MF debit 上書き (案 A) ? 全行 auto")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:296:    class Scenario4
_MfDebitOverrideAutoOnly {
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:299:        @DisplayNam
e("MF debit が verified より大きい時は MF debit 値で上書き")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:303:                   
 .taxIncludedAmountChange(new BigDecimal("30000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:304:                   
 .taxExcludedAmountChange(new BigDecimal("27778"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:307:                   
 .taxIncludedAmountChange(new BigDecimal("70000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:308:                   
 .taxExcludedAmountChange(new BigDecimal("63636"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:317:            assertT
hat(curr8.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("30000");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:319:            assertT
hat(curr10.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("70000");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:322:            BigDeci
mal sum = curr8.getPaymentAmountSettledTaxIncluded()
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:323:                   
 .add(curr10.getPaymentAmountSettledTaxIncluded());
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:328:        @DisplayNam
e("MF debit map に supplier が無い時は何もしない")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:331:                   
 .taxIncludedAmountChange(new BigDecimal("50000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:332:                   
 .taxExcludedAmountChange(new BigDecimal("45455"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:333:                   
 .paymentAmountSettledTaxIncluded(new BigDecimal("12345"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:340:            assertT
hat(curr.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("12345");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:344:        @DisplayNam
e("空 / null map は no-op")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:347:                   
 .taxIncludedAmountChange(new BigDecimal("50000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:348:                   
 .taxExcludedAmountChange(new BigDecimal("45455"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:349:                   
 .paymentAmountSettledTaxIncluded(new BigDecimal("11111"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:353:            assertT
hat(curr.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("11111");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:356:            assertT
hat(curr.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("11111");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:361:    // Scenario 5: 
MF debit 上書き ? 手動確定行 protect
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:365:    @DisplayName("S
cenario 5: MF debit 上書き ? 手動確定行 protect")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:366:    class Scenario5
_MfDebitOverrideManualProtect {
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:369:        @DisplayNam
e("手動確定行 paymentSettled は MF debit で上書きされず、残額のみ auto 行に按分")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:370:        void manual
_row_payment_settled_protected() {
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:372:            TAccoun
tsPayableSummary manual8 = row(501, APRIL_20, RATE_8)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:373:                   
 .taxIncludedAmountChange(new BigDecimal("30000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:374:                   
 .taxExcludedAmountChange(new BigDecimal("27778"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:375:                   
 .verifiedManually(true)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:376:                   
 .paymentAmountSettledTaxIncluded(new BigDecimal("28000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:377:                   
 .paymentAmountSettledTaxExcluded(new BigDecimal("25926"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:380:                   
 .taxIncludedAmountChange(new BigDecimal("70000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:381:                   
 .taxExcludedAmountChange(new BigDecimal("63636"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:387:            aggrega
tor.overrideWithMfDebit(new ArrayList<>(List.of(manual8, auto10)), mfDebit, APRIL_20);
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:390:            assertT
hat(manual8.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("28000");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:391:            assertT
hat(manual8.getPaymentAmountSettledTaxExcluded()).isEqualByComparingTo("25926");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:393:            assertT
hat(auto10.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("70000");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:397:        @DisplayNam
e("全行手動確定の supplier は overrideWithMfDebit で skip")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:398:        void all_ma
nual_rows_skipped() {
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:399:            TAccoun
tsPayableSummary manual = row(502, APRIL_20, RATE_10)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:400:                   
 .taxIncludedAmountChange(new BigDecimal("50000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:401:                   
 .taxExcludedAmountChange(new BigDecimal("45455"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:402:                   
 .verifiedManually(true)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:403:                   
 .paymentAmountSettledTaxIncluded(new BigDecimal("47000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:404:                   
 .paymentAmountSettledTaxExcluded(new BigDecimal("42727"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:408:            aggrega
tor.overrideWithMfDebit(new ArrayList<>(List.of(manual)), mfDebit, APRIL_20);
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:411:            assertT
hat(manual.getPaymentAmountSettledTaxIncluded()).isEqualByComparingTo("47000");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:412:            assertT
hat(manual.getPaymentAmountSettledTaxExcluded()).isEqualByComparingTo("42727");
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:417:    // Scenario 6: 
SupplierAgg 計算 (verified 全行同値 → 代表値、不一致 → SUM)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:421:    @DisplayName("S
cenario 6: SupplierAgg 計算")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:422:    class Scenario6
_SupplierAgg {
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:425:        @DisplayNam
e("verified 全行同値 → 代表値 (二重計上回避)")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:429:                   
         .taxIncludedAmountChange(new BigDecimal("30000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:430:                   
         .taxExcludedAmountChange(new BigDecimal("27778"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:434:                   
         .taxIncludedAmountChange(new BigDecimal("70000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:435:                   
         .taxExcludedAmountChange(new BigDecimal("63636"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:440:            Supplie
rAgg agg = PayableMonthlyAggregator.computeSupplierAgg(group);
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:446:        @DisplayNam
e("verified 不一致 → SUM (DB 異常検知の安全側挙動)")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:450:                   
         .taxIncludedAmountChange(new BigDecimal("30000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:451:                   
         .taxExcludedAmountChange(new BigDecimal("27778"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:455:                   
         .taxIncludedAmountChange(new BigDecimal("70000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:456:                   
         .taxExcludedAmountChange(new BigDecimal("63636"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:461:            Supplie
rAgg agg = PayableMonthlyAggregator.computeSupplierAgg(group);
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:467:        @DisplayNam
e("payment-only 行は paid 算出から除外 (R4)")
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:471:                   
         .taxIncludedAmountChange(new BigDecimal("50000"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:472:                   
         .taxExcludedAmountChange(new BigDecimal("45455"))
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:477:                   
         .verifiedAmount(new BigDecimal("99999"))   // payment-only は無視
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:481:            Supplie
rAgg agg = PayableMonthlyAggregator.computeSupplierAgg(group);
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:491:            Integer
 supplierNo, LocalDate month, BigDecimal taxRate) {
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:497:                .ta
xRate(taxRate)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:498:                .op
eningBalanceTaxIncluded(BigDecimal.ZERO)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:499:                .op
eningBalanceTaxExcluded(BigDecimal.ZERO)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:500:                .pa
ymentAmountSettledTaxIncluded(BigDecimal.ZERO)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:501:                .pa
ymentAmountSettledTaxExcluded(BigDecimal.ZERO)
backend\src\test\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregatorGoldenTest.java:503:                .ve
rifiedManually(false)

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 4779ms:

backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:8:import jp.co.oda32.dom
ain.repository.finance.TAccountsPayableSummaryRepository;
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:41: * applyVerification 
時に補助行 (EXPENSE/SUMMARY/DIRECT_PURCHASE) が正しく aux テーブルに
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:43: * ルールマスタは最小
限のダミー (SUMMARY はルール不要で自動生成される) を渡すだけで良く、
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:44: * 振込手数料値引 / 
早払収益の SUMMARY 2件が確実に保存されることを検証する。
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:46: * <p><b>P1-03 案 D 
適用 (2026-05-04) で fixture/前提が更新必要</b>:
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:47: * 案 D で SUMMARY 集
約 (合計仕訳 2 行) を撤去し、supplier 別 PAYABLE_FEE/EARLY 副行に展開した。
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:48: * 旧テストは「SUMMAR
Y 行は必ず 2 件」を assert しているが、案 D 後は SUMMARY 行が
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:54: * <p>TODO(T3 案 D'' 
Phase 2): user 環境で fixture 再生成 + 新 assertion 整備後に @Disabled 解除。
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:58: *   <li>SUMMARY 行は
 0 件 (supplier 別 PAYABLE_* に展開されたため)</li>
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:64:@Disabled("P1-03 案 D
 適用 (2026-05-04): SUMMARY 集約撤去・supplier 別 PAYABLE_* 展開に変更。"
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:65:        + "「SUMMARY 
行は必ず 2 件」assertion が無効化される。"
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:66:        + "TODO(T3 案
 D'' Phase 2): user 環境で fixture 再生成 + 新 assertion 整備後に再有効化。"
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:74:    @Mock TAccountsPa
yableSummaryRepository payableRepository;
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:75:    @Mock TAccountsPa
yableSummaryService payableService;
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:85:        // ルールマス
タは空 (= すべて UNREGISTERED 扱い) でも SUMMARY 2 件は自動生成される
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:102:    void applyVerifi
cation後にaux削除と保存が呼ばれる_SUMMARY2件は必ず保存される() throws Exception {
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:118:        // SUMMARY 2
 件（振込手数料値引 + 早払収益）が確実に含まれる
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:119:        long summary
Count = saved.stream().filter(r -> "SUMMARY".equals(r.getRuleKind())).count();
backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:120:        assertEquals
(2, summaryCount, "SUMMARY 行は必ず 2 件 (振込手数料値引 + 早払収益)");

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\test\\java\\jp\\co\\oda32\\domain\\service\\finance\\SupplierBalancesServiceGoldenTest.java -Pattern \"Scenario|DisplayName|payableJournal|OpeningJournalDetector|dummy|unmatched|subAccount|status|opening\"" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\SupplierBalancesService.java -Pattern \"unmatched|accumulate|OpeningJournalDetector|subToCodes|buildMfSubToCodes|status|cumulative|opening\"" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\mf\\MfOpeningJournalDetector.java -Pattern \"isOpeningCandidate|credit|debit|journalNumber|findBest\"" in C:\project\odamitsu-data-hub
 succeeded in 1793ms:

backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:15:import jp.co.oda32.d
omain.service.finance.mf.MfOpeningBalanceService;
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:20:import org.junit.jup
iter.api.DisplayName;
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:61: *   <li>opening 注
入 (期首残あり supplier の closing 加算)</li>
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:80:    private MfOpenin
gBalanceService openingBalanceService;
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:90:        openingBalan
ceService = mock(MfOpeningBalanceService.class);
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:94:                summ
aryRepository, paymentSupplierService, openingBalanceService);
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:99:        when(mfOauth
Service.getValidAccessToken()).thenReturn("dummy-token");
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:103:    // Scenario 1: 
MATCH (self closing == MF balance, 差分 0)
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:107:    @DisplayName("S
cenario 1: 単一 supplier MATCH (self closing == MF balance)")
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:108:    void scenario_1
_single_supplier_match() {
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:109:        // self: su
pplier 100 / 4/20 / 10% / change 50,000 → closing 50,000 (opening 0, payment 0)
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:118:                pay
ableJournal(LocalDate.of(2026, 4, 1), "仕入先A_sub", new BigDecimal("50000"), null)
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:120:        stubOpening
Empty();
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:130:        assertThat(
r.getStatus()).isEqualTo("MATCH");
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:135:    // Scenario 2: 
MINOR / MAJOR 閾値判定
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:139:    @DisplayName("S
cenario 2-MINOR: 差分 500 → MINOR 分類")
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:140:    void scenario_2
_minor_diff() {
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:149:                pay
ableJournal(LocalDate.of(2026, 4, 1), "仕入先B_sub", new BigDecimal("50000"), null)
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:151:        stubOpening
Empty();
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:157:        assertThat(
r.getStatus()).isEqualTo("MINOR");
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:161:    @DisplayName("S
cenario 2-MAJOR: 差分 5000 → MAJOR 分類")
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:162:    void scenario_2
_major_diff() {
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:171:                pay
ableJournal(LocalDate.of(2026, 4, 1), "仕入先C_sub", new BigDecimal("50000"), null)
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:173:        stubOpening
Empty();
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:178:        assertThat(
r.getStatus()).isEqualTo("MAJOR");
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:182:    @DisplayName("S
cenario 2-EDGE: 差分 100 (= MATCH_TOLERANCE) は MATCH 判定")
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:183:    void scenario_2
_edge_match_tolerance() {
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:192:                pay
ableJournal(LocalDate.of(2026, 4, 1), "仕入先D_sub", new BigDecimal("50000"), null)
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:194:        stubOpening
Empty();
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:200:        assertThat(
r.getStatus()).isEqualTo("MATCH");
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:204:    // Scenario 3: 
MF_MISSING (self あり / MF なし)
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:208:    @DisplayName("S
cenario 3: MF_MISSING (self closing > 0 / MF activity 0)")
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:209:    void scenario_3
_mf_missing() {
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:219:        stubOpening
Empty();
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:223:        assertThat(
r.getStatus()).isEqualTo("MF_MISSING");
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:229:    // Scenario 4: 
SELF_MISSING (MF あり / self なし)
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:233:    @DisplayName("S
cenario 4: SELF_MISSING (MF activity あり / self 未登録)")
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:234:    void scenario_4
_self_missing() {
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:240:                pay
ableJournal(LocalDate.of(2026, 4, 1), "仕入先F_sub", new BigDecimal("20000"), null)
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:242:        stubOpening
Empty();
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:247:        assertThat(
r.getStatus()).isEqualTo("SELF_MISSING");
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:253:    // Scenario 5: 
opening 注入 (期首残あり supplier の closing 加算)
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:257:    @DisplayName("S
cenario 5: 期首残あり supplier の self closing に opening を加算")
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:258:    void scenario_5
_opening_balance_injection() {
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:267:                pay
ableJournal(LocalDate.of(2026, 4, 1), "仕入先G_sub", new BigDecimal("110000"), null)
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:270:        when(openin
gBalanceService.getEffectiveBalanceMap(SHOP, MfPeriodConstants.SELF_BACKFILL_START))
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:275:        // self clo
sing = current_closing(10000) + opening(100000) = 110,000
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:278:        assertThat(
r.getStatus()).isEqualTo("MATCH");
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:279:        assertThat(
r.getSelfOpening()).isEqualByComparingTo("100000");
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:283:    // Scenario 6: 
journal #1 (期首残仕訳) は accumulation から除外
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:287:    @DisplayName("S
cenario 6: 期首残高仕訳 (credit-only / debit に買掛金 無し) は accumulation から除外")
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:288:    void scenario_6
_opening_journal_excluded() {
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:296:        // 通常 jou
rnal (debit に買掛金あり) と opening journal (credit のみ) を混在
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:297:        MfJournal o
peningJournal = new MfJournal(
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:298:                "j-
opening", LocalDate.of(2025, 6, 21), 1, true, "opening", "期首残",
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:300:                   
     // credit only (debit は他の科目) → opening 判定対象
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:304:                   
             "opening")
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:307:        MfJournal r
egularJournal = payableJournal(LocalDate.of(2026, 4, 1), "仕入先H_sub", new BigDecimal("50000"), null);
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:309:        stubMfJourn
als(List.of(openingJournal, regularJournal));
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:310:        // opening 
は openingBalanceService 経由で別途注入 (今回は 0 に統一して journal #1 除外を verify)
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:311:        stubOpening
Empty();
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:315:        // opening 
journal が除外されていれば MF balance = 50000 (regular のみ)
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:318:        assertThat(
r.getStatus()).isEqualTo("MATCH");
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:336:    private void st
ubMfAccount(String subAccountName, String supplierCode) {
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:343:        m.setSubAcc
ountName(subAccountName);
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:353:    private void st
ubOpeningEmpty() {
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:354:        when(openin
gBalanceService.getEffectiveBalanceMap(SHOP, MfPeriodConstants.SELF_BACKFILL_START))
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:371:     * {@link jp.co
.oda32.domain.service.finance.mf.MfOpeningJournalDetector#isOpeningCandidate}
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:372:     * は「全 branc
h credit に買掛金 AND どの branch にも debit 買掛金が無い」を opening 判定とするため、
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:373:     * 単純な購入仕
訳 (credit 買掛金 / debit 仕入高) も opening として除外されてしまう。
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:376:     * isOpeningCan
didate=false を確実にし、accumulateMfJournals が credit/debit を集計する
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:384:    private MfJourn
al payableJournal(LocalDate transactionDate, String subAccountName,
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:390:                new
 MfSide(null, "買掛金", null, subAccountName, null, "対象外", null, cr),
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:395:        // value=0 
でも debit に買掛金が存在すれば isOpeningCandidate=false になる
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:398:                new
 MfSide(null, "買掛金", null, subAccountName, null, "対象外", null, de),
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:415:                .op
eningBalanceTaxIncluded(BigDecimal.ZERO)
backend\src\test\java\jp\co\oda32\domain\service\finance\SupplierBalancesServiceGoldenTest.java:416:                .op
eningBalanceTaxExcluded(BigDecimal.ZERO)

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1852ms:

backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:14:import jp.co.oda32.domain.serv
ice.finance.mf.MfOpeningBalanceService;
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:21:import org.springframework.htt
p.HttpStatus;
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:24:import org.springframework.web
.server.ResponseStatusException;
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:62:    private final MfOpeningBal
anceService openingBalanceService;
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:66:            throw new Response
StatusException(HttpStatus.BAD_REQUEST, "shopNo は必須です");
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:77:            throw new Response
StatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:81:            throw new Response
StatusException(HttpStatus.BAD_REQUEST,
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:107:        Map<String, Set<Strin
g>> mfSubToCodes = buildMfSubToCodes();
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:110:        Map<String, MfAccum> 
mfBySub = accumulateMfJournals(allJournals, resolvedMonth);
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:121:        // --- 前期繰越 (supp
lier 単位期首残) ? 自社側 opening/closing に加算 ---
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:123:        // self/MF 両側ともに
 opening は m_supplier_opening_balance.effectiveBalance 経由で注入する。
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:124:        // self 側のみ openin
gMap 注入を行い、t_accounts_payable_summary の transactionMonth=2025-05-20 行は
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:125:        // accumulateSelf() 
内で opening_balance_tax_included を読まない (CR-G01 二重計上防止)。
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:126:        Map<Integer, BigDecim
al> openingMap = new HashMap<>(
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:127:                openingBalanc
eService.getEffectiveBalanceMap(shopNo, MfPeriodConstants.SELF_BACKFILL_START));
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:138:            SelfAccum self = 
accumulateSelf(e.getValue(), resolvedMonth);
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:139:            BigDecimal openin
g = nz(openingMap.remove(supplierNo));
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:140:            self.opening = se
lf.opening.add(opening);
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:141:            self.closing = se
lf.closing.add(opening);
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:143:            Set<String> match
edSubs = resolveMatchedSubs(mfSubToCodes, sup.getPaymentSupplierCode(), mfBySub.keySet());
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:157:            Set<String> codes
 = mfSubToCodes.getOrDefault(subName, Set.of());
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:170:            // guessedNo が解
決されていれば opening をここでも注入 (self 未登録だが期首残を持つ supplier)
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:171:            BigDecimal openin
g = guessedNo == null ? BigDecimal.ZERO : nz(openingMap.remove(guessedNo));
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:176:                    .selfBala
nce(opening)
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:178:                    .diff(ope
ning.subtract(mf.credit.subtract(mf.debit)))
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:179:                    .status("
SELF_MISSING")
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:181:                    .selfOpen
ing(opening)
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:182:                    .selfChan
geCumulative(BigDecimal.ZERO)
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:183:                    .selfPaym
entCumulative(BigDecimal.ZERO)
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:184:                    .mfCredit
Cumulative(mf.credit)
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:185:                    .mfDebitC
umulative(mf.debit)
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:190:        // 3) opening のみあ
る supplier (self 未登録 かつ MF 側 activity なし) を残り処理
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:191:        for (Map.Entry<Intege
r, BigDecimal> e : openingMap.entrySet()) {
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:193:            BigDecimal openin
g = e.getValue();
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:194:            if (opening.signu
m() == 0) continue;
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:198:            self.opening = op
ening;
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:199:            self.closing = op
ening;
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:200:            Set<String> match
edSubs = resolveMatchedSubs(mfSubToCodes, sup.getPaymentSupplierCode(), mfBySub.keySet());
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:218:    private Map<String, Set<S
tring>> buildMfSubToCodes() {
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:231:    private Map<String, MfAcc
um> accumulateMfJournals(List<MfJournal> journals, LocalDate asOfMonth) {
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:232:        // SF-G01: 期首残高仕
訳 (journal #1) は m_supplier_opening_balance 経由で別途注入するため
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:235:        // opening は Supplie
rBalancesService.generate() の openingMap (effectiveBalanceMap) で
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:236:        // self.opening + bui
ldRow への注入として加算される。
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:240:            if (MfJournalFetc
her.isPayableOpeningJournal(j)) continue; // 期首残高仕訳は除外 (前期繰越として別管理)
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:261:    private SelfAccum accumul
ateSelf(List<TAccountsPayableSummary> rows, LocalDate asOfMonth) {
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:262:        // CR-G01: opening の
取得先は m_supplier_opening_balance.effectiveBalance に統一。
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:264:        // opening_balance_ta
x_included を累積していたが、generate() 側の openingMap 注入と
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:277:    private Set<String> resol
veMatchedSubs(Map<String, Set<String>> mfSubToCodes,
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:281:        for (Map.Entry<String
, Set<String>> e : mfSubToCodes.entrySet()) {
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:287:        for (Map.Entry<String
, Set<String>> e : mfSubToCodes.entrySet()) {
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:311:        String status = class
ify(self, mf, diff, masterRegistered);
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:319:                .status(statu
s)
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:321:                .selfOpening(
self.opening)
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:322:                .selfChangeCu
mulative(self.change)
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:323:                .selfPaymentC
umulative(self.payment)
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:324:                .mfCreditCumu
lative(mf.credit)
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:325:                .mfDebitCumul
ative(mf.debit)
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:348:            switch (r.getStat
us()) {
backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java:395:        BigDecimal opening = 
BigDecimal.ZERO;

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2865ms:

backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:11: * 本クラスで base 述語 {@
link #isOpeningCandidate} を 1 箇所に集約し、
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:12: * 取込側 ({@link #findBes
t}) のみが branch 数 (= 補助科目登録 supplier 数) で best を選ぶ adapter として動作する。
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:14: * 判定 base: 「全 branch 
の credit 側に 買掛金 が出現し、debit 側に 買掛金 が一切無い複合仕訳」。
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:15: * journalNumber == 1 の固
定判定はしない (#1 以外の opening 仕訳を取りこぼすため)。
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:16: * sub_account_name の nul
l チェックは判定 base には含めない (持たない opening row は credit branch カウントから除外するだけ)。
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:28:     * 判定: 全 branch の 
credit 側に 買掛金 が出現し、debit 側に 買掛金 が一切無い複合仕訳。
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:33:    public static boolean 
isOpeningCandidate(MfJournal j) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:35:        boolean hasPayable
Credit = false;
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:37:            MfJournal.MfSi
de cr = br.creditor();
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:38:            MfJournal.MfSi
de de = br.debitor();
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:40:                return fal
se; // debit 側に 買掛金 = 通常仕訳
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:43:                hasPayable
Credit = true;
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:46:        return hasPayableC
redit;
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:50:     * 取り込み用: opening
 候補の中から sub_account_name 付き credit branch 数が最大の journal を選ぶ。
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:56:    public static MfJourna
l findBest(List<MfJournal> journals) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:61:            if (!isOpening
Candidate(j)) continue;
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:62:            int payableCre
ditBranches = countPayableCreditWithSubAccount(j);
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:63:            if (payableCre
ditBranches > bestCount) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:65:                bestCount 
= payableCreditBranches;
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:71:    /** sub_account_name 
を持つ 買掛金 credit branch 数を返す (取込判定用)。 */
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:72:    private static int cou
ntPayableCreditWithSubAccount(MfJournal j) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java:76:            MfJournal.MfSi
de cr = br.creditor();

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

2026-05-06T05:37:13.299220Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'cmd /c if exist .github (dir /a .github) else (echo .github missing)'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'cmd /c if exist .github (dir /a .github) else (echo .github missing)' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'cmd /c if exist .github (dir /a .github) else (echo .github missing)'` rejected: blocked by policy
2026-05-06T05:37:16.941199Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git ls-tree -r HEAD -- .github'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git ls-tree -r HEAD -- .github' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git ls-tree -r HEAD -- .github'` rejected: blocked by policy
2026-05-06T05:37:20.052783Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git ls-tree -r HEAD | Select-String -Pattern "workflows|github"'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git ls-tree -r HEAD | Select-String -Pattern "workflows|github"' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git ls-tree -r HEAD | Select-String -Pattern "workflows|github"'` rejected: blocked by policy
codex
**指摘**

1. **Severity: Critical / カテゴリ: CI**
   内容: `PaymentMfImportServiceGoldenMasterTest` はレビュー対象では enabled とされていますが、実装上はまだ `@Disabled` です。[PaymentMfImportServiceGoldenMasterTest.java](/C:/project/odamitsu-data-hub/backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfImportServiceGoldenMasterTest.java:66) さらに `@CsvSource` も旧 fixture のままで、P1-03 案 D 後の `_v3` fixture へ更新されていません。[PaymentMfImportServiceGoldenMasterTest.java](/C:/project/odamitsu-data-hub/backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfImportServiceGoldenMasterTest.java:92) runbook は「2 本、enabled」「CI で常時実行」と書いているため、実装と運用文書が正面から矛盾しています。
   推奨アクション: enabled と言う前に fixture を再生成し、`@Disabled` を削除してください。未対応なら runbook 側も「未有効」と明記すべきです。

2. **Severity: Critical / カテゴリ: CI**
   内容: `PaymentMfImportServiceAuxRowTest` も `@Disabled` のままで、テスト名・assertion は旧仕様の「SUMMARY 2 件」をまだ期待しています。[PaymentMfImportServiceAuxRowTest.java](/C:/project/odamitsu-data-hub/backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfImportServiceAuxRowTest.java:64) runbook は「SUMMARY 行が 0 件、PAYABLE_* 副行が supplier 単位」と書いていますが、実テストは逆の期待値です。[PaymentMfImportServiceAuxRowTest.java](/C:/project/odamitsu-data-hub/backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfImportServiceAuxRowTest.java:120) これは「ゴールデンマスタ拡充」ではなく、重要な回帰検知面がまだ空いている状態です。
   推奨アクション: 新仕様の assertion に書き換えてから有効化してください。特に `SUMMARY=0`、`PAYABLE_*` は aux 不保存、洗い替えキー維持を固定すべきです。

3. **Severity: Critical / カテゴリ: Detector**
   内容: `MfOpeningJournalDetector.isOpeningCandidate` は「買掛金 credit があり、買掛金 debit がない」だけで opening 候補にします。[MfOpeningJournalDetector.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningJournalDetector.java:33) テスト fixture 側も、通常の credit-only 買掛金購入仕訳が opening と誤判定されることをコメントで認め、dummy debit branch で回避しています。[SupplierBalancesServiceGoldenTest.java](/C:/project/odamitsu-data-hub/backend/src/test/java/jp/co/oda32/domain/service/finance/SupplierBalancesServiceGoldenTest.java:371) これはテストの工夫ではなく、本番集計から通常購入仕訳を除外し得る会計ロジック欠陥です。
   推奨アクション: Detector 修正を別 Critical task に切り出してください。少なくとも journal number、摘要、取引日、branch 数、sub_account 分布など複合条件にすべきです。

4. **Severity: Major / カテゴリ: CI**
   内容: runbook は「CI レベルで検知」「CI で常時実行」と明記しています。[runbook-finance-recalc-impact-analysis.md](/C:/project/odamitsu-data-hub/claudedocs/runbook-finance-recalc-impact-analysis.md:13) 一方、ユーザー提示どおり `.github/workflows/` が未整備なら、`./gradlew test` は開発者の手動実行に依存します。通知先も定義されていないため、fail しても誰も気付かない運用になります。
   推奨アクション: 最低限 `pull_request` で `backend ./gradlew test` を実行する workflow を追加し、required check 化してください。

5. **Severity: Major / カテゴリ: Runbook**
   内容: Step 3-5 のスナップショット取得は admin cookie、dev DB、手動 curl、psql dump に依存しています。[runbook-finance-recalc-impact-analysis.md](/C:/project/odamitsu-data-hub/claudedocs/runbook-finance-recalc-impact-analysis.md:64) これは経理改修ごとに毎回実施するには重く、CI にも載せにくいです。特に main checkout、bootRun、ログイン、API 保存、diff まで人手前提なので、運用定着しない可能性が高いです。
   推奨アクション: `backend/scripts/finance-snapshot` のような機械実行スクリプトに落とし、認証不要の test profile fixture で再現できる範囲を切り出してください。

6. **Severity: Major / カテゴリ: Runbook**
   内容: Step 7 の差分判定は「意図通り」「副作用」「期間外波及」の判断が主観的です。[runbook-finance-recalc-impact-analysis.md](/C:/project/odamitsu-data-hub/claudedocs/runbook-finance-recalc-impact-analysis.md:117) PR レビューアが会計仕様を毎回再判断する必要があり、ゴールデンマスタの自動検知という目的と噛み合っていません。確定月、手動確定、対象 supplier、対象月範囲など、機械判定できる条件がまだルール化されていません。
   推奨アクション: `allowed-diff.yml` のような差分許可リストを設け、対象月・対象 supplier・対象カラム以外の変動は自動 NG にしてください。

7. **Severity: Major / カテゴリ: Runbook**
   内容: Step 8 の過去確定月保護は runbook の原則止まりです。[runbook-finance-recalc-impact-analysis.md](/C:/project/odamitsu-data-hub/claudedocs/runbook-finance-recalc-impact-analysis.md:127) 実装上の DB 制約やアプリケーションガードがなければ、バッチ再実行や手動 SQL で確定月を書き換えられます。会計データの不変条件としてはドキュメント推奨では弱いです。
   推奨アクション: 確定月テーブルを定義し、`t_accounts_payable_summary` などに UPDATE/DELETE guard を入れる設計を検討してください。

8. **Severity: Major / カテゴリ: テスト網羅**
   内容: `PayableMonthlyAggregatorGoldenTest` は 6 シナリオで基本動作、税率混在、payment-only、MF debit override、manual protect、SupplierAgg を固定しています。[PayableMonthlyAggregatorGoldenTest.java](/C:/project/odamitsu-data-hub/backend/src/test/java/jp/co/oda32/batch/finance/service/PayableMonthlyAggregatorGoldenTest.java:72) ただし closing balance がマイナスになるケース、税率変更月跨ぎ、決算期跨ぎ、複数月連鎖の巻き戻しはありません。過去月再計算の事故を防ぐ目的なら、単月 unit fixture だけでは弱いです。
   推奨アクション: マイナス残、税率境界、期首・期末境界、2-3 ヶ月連鎖の fixture を追加してください。

9. **Severity: Major / カテゴリ: テスト網羅**
   内容: `SupplierBalancesServiceGoldenTest` は MATCH/MINOR/MAJOR/MISSING/opening/journal 除外を押さえています。[SupplierBalancesServiceGoldenTest.java](/C:/project/odamitsu-data-hub/backend/src/test/java/jp/co/oda32/domain/service/finance/SupplierBalancesServiceGoldenTest.java:103) しかし同一 supplier が複数 sub_account に hit するケース、MF 側 supplier 未マッチ、`unmatchedSuppliers` 的な出力、累積範囲の巻き戻しは未固定です。`SupplierBalancesService` 本体は sub_account 解決や MF 未対応行の処理を持つため、ここが空いているのは実運用リスクです。[SupplierBalancesService.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:143)
   推奨アクション: 複数 sub_account、未マッチ MF sub_account、asOfMonth 変更時の累積再計算を追加してください。

10. **Severity: Major / カテゴリ: DX**
    内容: fixture 更新フローが混在しています。PaymentMfImport は user 環境で実 Excel/UI/admin login が必要ですが、Aggregator/Balances は純 Java fixture です。[runbook-finance-recalc-impact-analysis.md](/C:/project/odamitsu-data-hub/claudedocs/runbook-finance-recalc-impact-analysis.md:169) この違いは runbook にあるものの、開発者から見ると「どの fail は自分で直せるのか」「どの fail は user 作業待ちか」が分かりにくいです。
    推奨アクション: テストを `unit-golden` と `external-fixture-golden` に分類し、Gradle task 名と更新責任者を分けてください。

11. **Severity: Minor / カテゴリ: Runbook**
    内容: runbook §6 の `PayableMonthlyAggregator` パスは現在は正しいですが、§4 の表は `.../PayableMonthlyAggregatorGoldenTest.java` と省略され、Payment/Supplier と粒度が揃っていません。[runbook-finance-recalc-impact-analysis.md](/C:/project/odamitsu-data-hub/claudedocs/runbook-finance-recalc-impact-analysis.md:156) 実体は `jp/co/oda32/batch/finance/service` 配下です。[PayableMonthlyAggregatorGoldenTest.java](/C:/project/odamitsu-data-hub/backend/src/test/java/jp/co/oda32/batch/finance/service/PayableMonthlyAggregatorGoldenTest.java:1) 小さいズレですが、オンボーディング時には迷います。
    推奨アクション: §4 の fixture パスも完全パスに統一してください。

12. **Severity: Minor / カテゴリ: DX**
    内容: golden master fail 時の判断基準が「意図通りなら期待値更新、意図外なら修正」に留まっています。[runbook-finance-recalc-impact-analysis.md](/C:/project/odamitsu-data-hub/claudedocs/runbook-finance-recalc-impact-analysis.md:160) これでは新任開発者が「期待値が古い」のか「コードが壊れた」のかを即断しにくいです。特に会計ロジックでは、少額差分、端数、確定月、manual row の扱いが判断軸になります。
    推奨アクション: fail triage 表を追加し、変更可能カラム、絶対 NG カラム、許容差、レビュー必須条件を明文化してください。

**T3 DEFER 判断について**

T3 を今すぐ実装しない判断自体は妥当寄りです。ただし再議論 trigger が「実運用で観測されたら」だけだと遅いです。税率・勘定科目・supplier 紐付け・MF sub_account mapping のいずれかを過去月に遡って変更する要求が出た時点で、T3 を再開する条件を runbook に明記すべきです。

**検証メモ**

読み取り専用 sandbox のため `./gradlew test` は実行していません。`.github` の直接確認コマンドも環境ポリシーで拒否されましたが、少なくとも対象ファイル上は PaymentMfImport 系が disabled のままで、runbook の CI 前提とは一致していません。

**Opus 実装で見落としていた最重要 3 件**

1. PaymentMfImport 系 2 テストが実際には enabled ではなく、`@Disabled` のまま。
2. `MfOpeningJournalDetector` の過敏判定を fixture workaround で隠しており、本番集計欠陥として扱えていない。
3. runbook が「CI で常時実行」と書く一方、CI/通知/required check が未整備で、ゴールデンマスタが運用上発火しない。
tokens used
142,340
**指摘**

1. **Severity: Critical / カテゴリ: CI**
   内容: `PaymentMfImportServiceGoldenMasterTest` はレビュー対象では enabled とされていますが、実装上はまだ `@Disabled` です。[PaymentMfImportServiceGoldenMasterTest.java](/C:/project/odamitsu-data-hub/backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfImportServiceGoldenMasterTest.java:66) さらに `@CsvSource` も旧 fixture のままで、P1-03 案 D 後の `_v3` fixture へ更新されていません。[PaymentMfImportServiceGoldenMasterTest.java](/C:/project/odamitsu-data-hub/backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfImportServiceGoldenMasterTest.java:92) runbook は「2 本、enabled」「CI で常時実行」と書いているため、実装と運用文書が正面から矛盾しています。
   推奨アクション: enabled と言う前に fixture を再生成し、`@Disabled` を削除してください。未対応なら runbook 側も「未有効」と明記すべきです。

2. **Severity: Critical / カテゴリ: CI**
   内容: `PaymentMfImportServiceAuxRowTest` も `@Disabled` のままで、テスト名・assertion は旧仕様の「SUMMARY 2 件」をまだ期待しています。[PaymentMfImportServiceAuxRowTest.java](/C:/project/odamitsu-data-hub/backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfImportServiceAuxRowTest.java:64) runbook は「SUMMARY 行が 0 件、PAYABLE_* 副行が supplier 単位」と書いていますが、実テストは逆の期待値です。[PaymentMfImportServiceAuxRowTest.java](/C:/project/odamitsu-data-hub/backend/src/test/java/jp/co/oda32/domain/service/finance/PaymentMfImportServiceAuxRowTest.java:120) これは「ゴールデンマスタ拡充」ではなく、重要な回帰検知面がまだ空いている状態です。
   推奨アクション: 新仕様の assertion に書き換えてから有効化してください。特に `SUMMARY=0`、`PAYABLE_*` は aux 不保存、洗い替えキー維持を固定すべきです。

3. **Severity: Critical / カテゴリ: Detector**
   内容: `MfOpeningJournalDetector.isOpeningCandidate` は「買掛金 credit があり、買掛金 debit がない」だけで opening 候補にします。[MfOpeningJournalDetector.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOpeningJournalDetector.java:33) テスト fixture 側も、通常の credit-only 買掛金購入仕訳が opening と誤判定されることをコメントで認め、dummy debit branch で回避しています。[SupplierBalancesServiceGoldenTest.java](/C:/project/odamitsu-data-hub/backend/src/test/java/jp/co/oda32/domain/service/finance/SupplierBalancesServiceGoldenTest.java:371) これはテストの工夫ではなく、本番集計から通常購入仕訳を除外し得る会計ロジック欠陥です。
   推奨アクション: Detector 修正を別 Critical task に切り出してください。少なくとも journal number、摘要、取引日、branch 数、sub_account 分布など複合条件にすべきです。

4. **Severity: Major / カテゴリ: CI**
   内容: runbook は「CI レベルで検知」「CI で常時実行」と明記しています。[runbook-finance-recalc-impact-analysis.md](/C:/project/odamitsu-data-hub/claudedocs/runbook-finance-recalc-impact-analysis.md:13) 一方、ユーザー提示どおり `.github/workflows/` が未整備なら、`./gradlew test` は開発者の手動実行に依存します。通知先も定義されていないため、fail しても誰も気付かない運用になります。
   推奨アクション: 最低限 `pull_request` で `backend ./gradlew test` を実行する workflow を追加し、required check 化してください。

5. **Severity: Major / カテゴリ: Runbook**
   内容: Step 3-5 のスナップショット取得は admin cookie、dev DB、手動 curl、psql dump に依存しています。[runbook-finance-recalc-impact-analysis.md](/C:/project/odamitsu-data-hub/claudedocs/runbook-finance-recalc-impact-analysis.md:64) これは経理改修ごとに毎回実施するには重く、CI にも載せにくいです。特に main checkout、bootRun、ログイン、API 保存、diff まで人手前提なので、運用定着しない可能性が高いです。
   推奨アクション: `backend/scripts/finance-snapshot` のような機械実行スクリプトに落とし、認証不要の test profile fixture で再現できる範囲を切り出してください。

6. **Severity: Major / カテゴリ: Runbook**
   内容: Step 7 の差分判定は「意図通り」「副作用」「期間外波及」の判断が主観的です。[runbook-finance-recalc-impact-analysis.md](/C:/project/odamitsu-data-hub/claudedocs/runbook-finance-recalc-impact-analysis.md:117) PR レビューアが会計仕様を毎回再判断する必要があり、ゴールデンマスタの自動検知という目的と噛み合っていません。確定月、手動確定、対象 supplier、対象月範囲など、機械判定できる条件がまだルール化されていません。
   推奨アクション: `allowed-diff.yml` のような差分許可リストを設け、対象月・対象 supplier・対象カラム以外の変動は自動 NG にしてください。

7. **Severity: Major / カテゴリ: Runbook**
   内容: Step 8 の過去確定月保護は runbook の原則止まりです。[runbook-finance-recalc-impact-analysis.md](/C:/project/odamitsu-data-hub/claudedocs/runbook-finance-recalc-impact-analysis.md:127) 実装上の DB 制約やアプリケーションガードがなければ、バッチ再実行や手動 SQL で確定月を書き換えられます。会計データの不変条件としてはドキュメント推奨では弱いです。
   推奨アクション: 確定月テーブルを定義し、`t_accounts_payable_summary` などに UPDATE/DELETE guard を入れる設計を検討してください。

8. **Severity: Major / カテゴリ: テスト網羅**
   内容: `PayableMonthlyAggregatorGoldenTest` は 6 シナリオで基本動作、税率混在、payment-only、MF debit override、manual protect、SupplierAgg を固定しています。[PayableMonthlyAggregatorGoldenTest.java](/C:/project/odamitsu-data-hub/backend/src/test/java/jp/co/oda32/batch/finance/service/PayableMonthlyAggregatorGoldenTest.java:72) ただし closing balance がマイナスになるケース、税率変更月跨ぎ、決算期跨ぎ、複数月連鎖の巻き戻しはありません。過去月再計算の事故を防ぐ目的なら、単月 unit fixture だけでは弱いです。
   推奨アクション: マイナス残、税率境界、期首・期末境界、2-3 ヶ月連鎖の fixture を追加してください。

9. **Severity: Major / カテゴリ: テスト網羅**
   内容: `SupplierBalancesServiceGoldenTest` は MATCH/MINOR/MAJOR/MISSING/opening/journal 除外を押さえています。[SupplierBalancesServiceGoldenTest.java](/C:/project/odamitsu-data-hub/backend/src/test/java/jp/co/oda32/domain/service/finance/SupplierBalancesServiceGoldenTest.java:103) しかし同一 supplier が複数 sub_account に hit するケース、MF 側 supplier 未マッチ、`unmatchedSuppliers` 的な出力、累積範囲の巻き戻しは未固定です。`SupplierBalancesService` 本体は sub_account 解決や MF 未対応行の処理を持つため、ここが空いているのは実運用リスクです。[SupplierBalancesService.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/SupplierBalancesService.java:143)
   推奨アクション: 複数 sub_account、未マッチ MF sub_account、asOfMonth 変更時の累積再計算を追加してください。

10. **Severity: Major / カテゴリ: DX**
    内容: fixture 更新フローが混在しています。PaymentMfImport は user 環境で実 Excel/UI/admin login が必要ですが、Aggregator/Balances は純 Java fixture です。[runbook-finance-recalc-impact-analysis.md](/C:/project/odamitsu-data-hub/claudedocs/runbook-finance-recalc-impact-analysis.md:169) この違いは runbook にあるものの、開発者から見ると「どの fail は自分で直せるのか」「どの fail は user 作業待ちか」が分かりにくいです。
    推奨アクション: テストを `unit-golden` と `external-fixture-golden` に分類し、Gradle task 名と更新責任者を分けてください。

11. **Severity: Minor / カテゴリ: Runbook**
    内容: runbook §6 の `PayableMonthlyAggregator` パスは現在は正しいですが、§4 の表は `.../PayableMonthlyAggregatorGoldenTest.java` と省略され、Payment/Supplier と粒度が揃っていません。[runbook-finance-recalc-impact-analysis.md](/C:/project/odamitsu-data-hub/claudedocs/runbook-finance-recalc-impact-analysis.md:156) 実体は `jp/co/oda32/batch/finance/service` 配下です。[PayableMonthlyAggregatorGoldenTest.java](/C:/project/odamitsu-data-hub/backend/src/test/java/jp/co/oda32/batch/finance/service/PayableMonthlyAggregatorGoldenTest.java:1) 小さいズレですが、オンボーディング時には迷います。
    推奨アクション: §4 の fixture パスも完全パスに統一してください。

12. **Severity: Minor / カテゴリ: DX**
    内容: golden master fail 時の判断基準が「意図通りなら期待値更新、意図外なら修正」に留まっています。[runbook-finance-recalc-impact-analysis.md](/C:/project/odamitsu-data-hub/claudedocs/runbook-finance-recalc-impact-analysis.md:160) これでは新任開発者が「期待値が古い」のか「コードが壊れた」のかを即断しにくいです。特に会計ロジックでは、少額差分、端数、確定月、manual row の扱いが判断軸になります。
    推奨アクション: fail triage 表を追加し、変更可能カラム、絶対 NG カラム、許容差、レビュー必須条件を明文化してください。

**T3 DEFER 判断について**

T3 を今すぐ実装しない判断自体は妥当寄りです。ただし再議論 trigger が「実運用で観測されたら」だけだと遅いです。税率・勘定科目・supplier 紐付け・MF sub_account mapping のいずれかを過去月に遡って変更する要求が出た時点で、T3 を再開する条件を runbook に明記すべきです。

**検証メモ**

読み取り専用 sandbox のため `./gradlew test` は実行していません。`.github` の直接確認コマンドも環境ポリシーで拒否されましたが、少なくとも対象ファイル上は PaymentMfImport 系が disabled のままで、runbook の CI 前提とは一致していません。

**Opus 実装で見落としていた最重要 3 件**

1. PaymentMfImport 系 2 テストが実際には enabled ではなく、`@Disabled` のまま。
2. `MfOpeningJournalDetector` の過敏判定を fixture workaround で隠しており、本番集計欠陥として扱えていない。
3. runbook が「CI で常時実行」と書く一方、CI/通知/required check が未整備で、ゴールデンマスタが運用上発火しない。
