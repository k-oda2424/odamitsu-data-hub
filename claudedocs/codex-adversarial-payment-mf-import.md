OpenAI Codex v0.128.0 (research preview)
--------
workdir: C:\project\odamitsu-data-hub
model: gpt-5.5
provider: openai
approval: never
sandbox: read-only
reasoning effort: none
reasoning summaries: none
session id: 019df0aa-2fb7-7051-91a3-fd09ce2824b3
--------
user
あなたは経理・会計連携の上級レビュアーです。Cluster C (買掛仕入 MF 変換 - Payment MF Import) の批判的レビューを実施してください。

## プロジェクト
小田光データ連携基盤 (`C:/project/odamitsu-data-hub`)
- Java 21 / Spring Boot 3.3.x / PostgreSQL 17 / Next.js 16
- 振込み明細 Excel(5日/20日払い) → MF 買掛仕入 CSV 変換

## レビュー対象 (Cluster C)
**Backend** (主要):
- `backend/src/main/java/jp/co/oda32/api/finance/PaymentMfImportController.java`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfRuleService.java`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfExcelParser.java`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfCellReader.java`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfCsvWriter.java`
- Entity: MPaymentMfRule, TPaymentMfImportHistory, TPaymentMfAuxRow

**Frontend**:
- `frontend/components/pages/finance/payment-mf-import.tsx`
- `frontend/components/pages/finance/payment-mf-rules.tsx`
- `frontend/components/pages/finance/payment-mf-history.tsx`
- `frontend/components/pages/finance/VerifiedCsvExportDialog.tsx`
- `frontend/components/pages/finance/PaymentMfAuxRowsTable.tsx`

## 既存レビュー (重複回避)
- `claudedocs/design-review-payment-mf-import.md` (Critical 3 / Major 6 / Minor 7)
- `claudedocs/code-review-payment-mf-import.md` (Critical 2 / Major 5 / Minor 7)
- `claudedocs/design-payment-mf-import.md`, `claudedocs/design-payment-mf-aux-rows.md`

既出 (重複させない):
- `/convert` `/verify` API の認可漏れ、§5.1 と Javadoc 整合不良、`applyVerification` 重複書込、`fmtAmount` null 時 CSV 契約違反

## 批判的レビューの観点
Opus はコード品質と既知のバグを網羅した。**あなたの仕事は根本的な問い直し**:

1. **設計選択の前提**
   - 「振込明細 Excel → CSV」という処理形態が本当に正しいか (MF API 直接連携でなく Excel 経由する理由)
   - 「5日/20日締め固定」「前月20日締め突合」のハードコード前提
   - PAYABLE→DIRECT_PURCHASE 自動降格の業務妥当性 (経理判断を黙って自動化していないか)
   - aux_row (補助行) を別テーブルに持つ設計 vs PAYABLE と統合する設計の比較

2. **代替アーキテクチャ**
   - Excel パーサーをサーバー側で持つ vs クライアント側 (ブラウザ Excel.js) で持つ
   - `m_payment_mf_rule` ベースのルールエンジン vs ML/データ駆動マッチング
   - CSV 出力を即時 vs ジョブキュー経由 (大量振込時のメモリ・タイムアウト)

3. **エッジケース / 失敗モード**
   - Excel フォーマット変更 (列追加/削除/列順入替) 時の検知
   - 同一振込明細を 2 回取込 (重複防止のキー設計)
   - 振込日が 5/20 以外 (緊急振込、月末振込) のケース
   - 税率混在行の按分誤差 (BigDecimal 累積誤差)
   - aux_row が UNIQUE (shop, month, transfer_date) で衝突した場合
   - CP932 で表現できない文字 (㎡, ①, 旧字体) の振る舞い

4. **ビジネス整合性 / 監査**
   - 「100円閾値で一致」基準は税理士監査で説明可能か
   - 振込明細と請求書の不一致をどう扱うべきか (現在の挙動 vs 理想)
   - 検証済 CSV 出力で「いつ誰がどの行を確定したか」が DB から完全追跡可能か
   - 自動 PAYABLE→DIRECT_PURCHASE 降格を行ったことが監査ログに残るか

## 出力フォーマット
Markdown、日本語、簡潔に (max 600 行)。各指摘:
- Severity: Critical / Major / Minor
- カテゴリ: 設計選択 / 代替案 / エッジケース / 業務整合
- 内容 (3-5 文)
- 推奨アクション (1-2 文)

最後に「Opus が見落としていた最重要 3 件」を明記。


codex
using-superpowers スキルを確認してから、レビュー対象と既存レビューを読みます。今回はコード変更ではなく批判的レビューなので、まず重複回避用の文書と実装の前提を突き合わせます。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path C:\\Users\\kazuki\\.agents\\skills\\using-superpowers\\SKILL.md" in C:\project\odamitsu-data-hub
 succeeded in 1675ms:
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
requesting-code-review と Spring/React 系のレビュー観点を最小限参照します。ユーザー指定どおり、既出バグの再掲ではなく設計前提・監査・失敗モードに寄せて確認します。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path C:\\Users\\kazuki\\.agents\\skills\\requesting-code-review\\SKILL.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path C:\\Users\\kazuki\\.agents\\skills\\spring-framework-patterns\\SKILL.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path C:\\Users\\kazuki\\.agents\\skills\\typescript-react-reviewer\\SKILL.md" in C:\project\odamitsu-data-hub
 succeeded in 1769ms:
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

 succeeded in 2027ms:
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

 succeeded in 2757ms:
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

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path claudedocs\\design-review-payment-mf-import.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path claudedocs\\code-review-payment-mf-import.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path claudedocs\\design-payment-mf-import.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path claudedocs\\design-payment-mf-aux-rows.md" in C:\project\odamitsu-data-hub
 succeeded in 1771ms:
# コードレビュー: 買掛仕入 MF 変換 (Cluster C)

レビュー日: 2026-05-04
対象ブランチ: `refactor/code-review-fixes`
レビュアー: Opus サブエージェント
対象実装:
- Backend: `PaymentMfImportController` / `PaymentMfImportService` / `PaymentMfRuleService` / `PaymentMfExcelParser` / `PaymentMfCellReader` / `PaymentMfCsvWriter` / Entity 3 種 / Repository 3 種 / DTO 7 種
- Frontend: `payment-mf-import.tsx` / `payment-mf-rules.tsx` / `payment-mf-history.tsx` / `VerifiedCsvExportDialog.tsx` / `PaymentMfAuxRowsTable.tsx` / `types/payment-mf.ts`
- DB: `V011__create_payment_mf_tables.sql` / `V016__create_payment_mf_aux_row.sql`

前提: 設計レビュー (`claudedocs/design-review-payment-mf-import.md`, Critical 3 / Major 6 / Minor 7) で指摘済みの課題は再掲しない。コード固有の問題に絞る。

## サマリー

- 総指摘件数: **Blocker 0 / Critical 2 / Major 5 / Minor 7**
- 承認状態: **Needs Revision**

新発見トップ:
1. **C-CODE-1**: `applyVerification` で書込側の `verified_amount` (税込) と整合性チェック側の `payable` 集計が「振込明細の `invoice` 全額を税率行ごとに重複書込」する構造になっており、税率行が 2 行ある仕入先で `verified_amount` 合計 = 元の 2 倍になる（設計レビュー C-3 と関連するが、書込ロジック側の重複も伴う点は未指摘）。
2. **C-CODE-2**: `PaymentMfCsvWriter#fmtAmount` が金額末尾に常に半角スペースを付与する仕様だが、空 (`null`) のときは空文字列を返すため、行内で「金額カラムだけ末尾スペースなし」の差分が CSV 出力に混入し、MF 取込側の strict パーサで弾かれるリスクがある。

ファイル: `claudedocs/code-review-payment-mf-import.md`

---

## Critical 指摘

### C-CODE-1. `applyVerification` の `verified_amount` 書込が税率行ごとに重複し、合計値が二重計上される

**箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java:269-287`

```java
for (TAccountsPayableSummary s : list) {
    s.setVerificationResult(isMatched ? 1 : 0);
    s.setPaymentDifference(difference);
    s.setVerifiedManually(true);
    s.setMfExportEnabled(isMatched);
    s.setVerificationNote(adjustNote);
    s.setVerifiedAmount(invoice);                   // ← 全行に同じ invoice (税込総額) を書く
    BigDecimal taxRate = s.getTaxRate() != null ? s.getTaxRate() : BigDecimal.TEN;
    BigDecimal divisor = BigDecimal.valueOf(100).add(taxRate);
    BigDecimal invoiceTaxExcl = invoice.multiply(BigDecimal.valueOf(100))
            .divide(divisor, 0, java.math.RoundingMode.DOWN);
    s.setVerifiedAmountTaxExcluded(invoiceTaxExcl); // ← 全行に逆算同値
    s.setAutoAdjustedAmount(autoAdjusted);
    s.setMfTransferDate(cached.getTransferDate());
    payableService.save(s);
}
```

**問題**:
- `list` は同一 `(supplierCode, transactionMonth)` の税率別 N 行（10% / 8%軽 / 8%旧軽 など）。
- `invoice` は振込明細の請求額 1 件 (税込総額) で、ループ内で「全税率行に同値を書込」している。
- 後段 `exportVerifiedCsv` (L477-505) と `buildVerifiedExportPreview` (L613-639) は `sumVerifiedAmountForGroup` (L417-429) で「全行同値なら代表値、そうでなければ SUM」というヒューリスティックでこの構造を救っている（コメント L411-414 にも明記）が、これは **書込側のバグを読み取り側で吸収する** 構造で、不変条件として脆い:
  1. `verified_amount` の DB 値だけ見ると、税率 10% 行 \1,100 + 8% 行 \1,100 = \2,200 (二重計上された姿) で「合計」を出す他コード（外部レポート、SQL ad-hoc 集計、`/finance/accounts-payable-ledger` など）が誤った数字を出す。
  2. `verifiedAmount` を SUM したくない読者向けに `sumVerifiedAmountForGroup` を使うことを **強制する規約** がコードコメントにしか存在しない。
  3. 手動 verify は税率別に異なる値を入れる前提（コメント L411 「手動 verify では税率別に異なる値が入り得る」）なので、今後画面側で 1 supplier に複数税率行を一括 verify する UI を作ると `allSame=false` 経路に入って自動的に SUM になり、振込明細一括検証経路と判別できない (両者とも `verifiedManually=true`)。
- `verified_amount_tax_excluded` も同様に「全税率行で逆算同値」になり、税率 10% 行と 8% 行で同じ税抜額が入る（実際には 10% 行は \1,000 / 8% 行は \1,019 等で異なるべき）。`exportVerifiedCsv` 側 (L518-538) は「PAYABLE 1 行 = supplier 集約」しか出力しないため、CSV 上は税抜の按分情報が消える。

**影響**:
- **財務整合性**: `verified_amount` の DB SUM と `payment_mf` CSV 出力額が一致しない。経理が `t_accounts_payable_summary` を直接 SQL で集計したときの「検証済み総額」と、ダウンロード CSV の「合計額」(`X-Total-Amount`) が乖離する。
- **監査証跡**: `auto_adjusted_amount` も全行に同値が書かれるため、税率別の調整明細が DB から復元不能。
- 設計レビュー C-3 は「複数税率仕入先の精度問題」として既出だが、本指摘はそれに加えて **書込スキーマ自体が二重計上を内包している** 点を強調する。`sumVerifiedAmountForGroup` が無いと壊れる API が今後増えるたびに保守コストが上がる。

**修正案** (どれかを採用):
1. **代表行のみ書込**: `list.get(0)` のみ `verifiedAmount` を書き、他税率行は `verifiedAmount = null` (または 0) で残す。集計は常に「非 null 1 行のみ採用」で固定化する。`autoAdjustedAmount` / `verifiedAmountTaxExcluded` も代表行のみ。
2. **税率別按分書込**: 振込明細 Excel に税率内訳が無いため、DB 側の `taxIncludedAmountChange` または `taxIncludedAmount` の比率で `invoice` を按分し、各税率行に按分後の値を書く。これなら `verified_amount` の DB SUM = invoice で一意。`verified_amount_tax_excluded` も税率別に正しく逆算される。
3. （短期回避）コメント L258-263 の「全税率行に同値を書く」を `verified_amount_total` のような **集約値専用カラム** に切り出し、`verified_amount` は税率別に分けて書く。`sumVerifiedAmountForGroup` を捨てて `findVerifiedForMfExport` 側で sum する。

いずれにしても **書込仕様 = 読取仕様** の不変条件を JavaDoc / DB COMMENT に明示する必要あり。

### C-CODE-2. `PaymentMfCsvWriter#fmtAmount` の末尾スペース仕様が NULL 金額行で破綻する

**箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfCsvWriter.java:95-98`

```java
private static String fmtAmount(Long v) {
    if (v == null) return "";          // ← null は空文字（末尾スペースなし）
    return v + " ";                    // 金額後ろに半角スペース（既存運用CSVに合わせる）
}
```

**問題**:
- 既存運用 CSV は「金額カラムは必ず末尾半角スペース付き」というフォーマット契約 (コメント L97 で明示)。MF 取込側の strict パーサがこのスペースを期待してフィールド検出している場合、`debitAmount=null` の行で `,,` のように「スペース無しの空フィールド」が混入すると形式違反になる。
- 現状の呼び出し経路では `debitAmount` / `creditAmount` は常に非 null だが、`PaymentMfPreviewRow` の builder 経由で構築する際に Setter 経由で null をセットする経路 (例: `applyVerification` で逆算失敗時のフォールバック) があれば破綻する。
- `exportVerifiedCsv` (L518-538) で `amount = sumVerifiedAmountForGroup(group)` (L504) が `0L` を返したケースは `if (amount == 0L) continue;` (L505) でスキップされるが、`buildPreview` の SUMMARY 行 (L878-895) は `fee = cached.getSummarySourceFee() == null ? 0L : ...` で 0L を許容しているため CSV 出力時に「金額 0 + 末尾スペース」になる (これは仕様通り)。null 出力経路は DTO の null fallback 次第。
- 防御的にも「末尾スペース必須」が契約なら、null も `" "` (スペース 1 文字) または `"0 "` で統一すべき。

**影響**:
- 現状の経路では null は出ないが、フォーマット契約の不変条件をコードで強制していないため、DTO 経路追加時に CSV 形式違反の回帰が発生しうる。MF 取込でエラーが出たときに「どの行が壊しているか」を CSV を目視確認しないと分からない。

**修正案**:
```java
private static String fmtAmount(Long v) {
    long amount = v == null ? 0L : v;
    return amount + " ";
}
```
あるいは Javadoc で「`debitAmount` / `creditAmount` は非 null を前提」とし、`toCsvLine` 入口で `Objects.requireNonNull` で防御する。

---

## Major 指摘

### M-CODE-1. `PaymentMfImportController#convert` / `verify` が成功時に履歴 ID を返さず、UI から「いま CSV 化したファイルの履歴」を辿れない

**箇所**: `backend/src/main/java/jp/co/oda32/api/finance/PaymentMfImportController.java:66-97`, `PaymentMfImportService.java:159-173, 1063-1087`

```java
public byte[] convert(String uploadId, Integer userNo) {
    ...
    saveHistory(cached, preview, csv, userNo);   // ← History の id は捨てる
    return csv;
}
```

**問題**:
- `saveHistory` (L1063-1087) は `historyRepository.save(h)` の戻り値 `TPaymentMfImportHistory` を破棄しており、生成された `history.id` を呼び元に返さない。
- フロント (`payment-mf-import.tsx:86-107`) はダウンロード成功後に `toast.success('CSVをダウンロードしました')` を出すだけで、生成された変換履歴行へのリンク (例: `/finance/payment-mf-history?highlightId=42`) を提示できない。
- 同様に `verify` も `VerifyResult` に履歴 ID が含まれず、後追いでどの Excel を反映したかを辿るには `cached.getFileName()` を別画面で検索する必要がある。
- `saveHistory` は `try/catch (Exception)` で例外を握り潰すため (L1084-1086) 履歴保存失敗を呼び元が知る術もない。「CSV だけダウンロードできて履歴に残らない」状態が静かに発生し得る。

**影響**:
- 監査証跡として「いつ・誰が・どのファイルを変換したか」を MF 取込エラー発生時に追跡したいケースで UI 導線が無く、運用者が「履歴一覧画面」を別途開いて推測する必要がある。
- CLAUDE.md の「DTO 分離」原則とは逆に、Controller が `byte[]` を直接返しているため履歴 ID をヘッダー (`X-History-Id` 等) で返すか、DTO ラッパーへの変更が必要。

**修正案**:
- `saveHistory` の戻り値で `Integer historyId` を返し、Controller の Response Header `X-History-Id` に詰める (`exportVerified` の `X-Skipped-Suppliers` と同じパターン)。
- `saveHistory` の例外握り潰しは「履歴保存失敗」を `log.error` だけでなく `X-History-Saved: false` ヘッダーで UI に伝える。フロントで `toast.warning('履歴保存に失敗しました')` を出す。

### M-CODE-2. `applyVerification` 内で例外発生時に既に書込済みの `t_accounts_payable_summary` 行と `t_payment_mf_aux_row` 削除分がトランザクションロールバックされても、**advisory lock は次の試行で問題ないが、advisory lock が獲得後 Excel パース失敗するケース** で UX が悪い

**箇所**: `PaymentMfImportService.java:184-318`

```java
@Transactional
public VerifyResult applyVerification(String uploadId, Integer userNo) {
    CachedUpload cached = getCached(uploadId);   // ← IllegalArgumentException の可能性
    if (cached.getTransferDate() == null) {
        throw new IllegalArgumentException(...);  // ← lock 取得前に throw
    }
    LocalDate txMonth = deriveTransactionMonth(cached.getTransferDate());
    acquireAdvisoryLock(txMonth);                 // ← ここで lock 取得
    ...
    PaymentMfPreviewResponse preview = buildPreview(uploadId, cached);   // ← ここで例外が起きると lock は @Transactional 終了で解放されるが、
                                                                          //    payable 反映分は ロールバック、補助行 delete は反映済みになる
    saveAuxRowsForVerification(cached, preview, txMonth, userNo);
    ...
}
```

**問題**:
- L293 の `buildPreview` は uploadId のキャッシュが期限切れ (`cleanExpired` が同時に走っている等) で `IllegalArgumentException` を投げ得るが、その時点で L195-288 の payable 反映ループが終わって commit 待ちのため、`@Transactional` でロールバックされる。
- ただし `saveAuxRowsForVerification` の L332 `auxRowRepository.deleteByShopAndTransactionMonthAndTransferDate` は `flushAutomatically=true, clearAutomatically=true` (Repository L30) なので、DELETE は flush 済み。後続の `saveAll` で例外が起きると、ロールバックで DELETE はやり直されるが、advisory lock のおかげで次の試行が直列化されるため最終的に整合する。
- しかし **半反映状態** (payable は更新済み・aux row は空) が中間で見えるケースがあり、別 tx で並行リードしている `/finance/accounts-payable` 画面が「verified=true なのに補助行が無い」一瞬を観測する可能性がある (Read Committed のデフォルト分離レベル)。
- また `saveAuxRowsForVerification` の delete + insert の間で例外が起きると、ロールバックされても **advisory lock が tx 終了で解放され**、次に来た tx は「補助行が一時的に空の状態」を読まずに済む。整合性は守られるが、buildPreview の N+1 解消 (B-W11) のおかげで preview を `applyVerification` 内で再構築するコスト (L293) はそのまま残っている (設計レビュー C-3-1 で既出)。

**影響**:
- 直接の不具合は無い (advisory lock + @Transactional でカバー済み) が、`buildPreview` が `applyVerification` の commit 直前にも走るため、**preview 結果が rule マスタの最新版で再評価され、UI で見ていたプレビューと反映結果がズレるケース** がある。例: ユーザーが preview した直後、別の admin がルールを編集 → `applyVerification` 内 buildPreview で別のルールにヒット → UI 上は「PAYABLE で一致」と見えていた行が DB 上 EXPENSE に反映される。
- この race の防御策として `cached` (キャッシュ) のスナップショット時点でロックされた rules も保持する案が考えられるが、現状未実装。

**修正案**:
- `applyVerification` 入口で `final List<MPaymentMfRule> snapshotRules = ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0");` を取り、`buildPreview` にも同 snapshot を渡す signature を追加する。preview API と applyVerification API で同一 rule snapshot を使う。
- もしくは `buildPreview` を `applyVerification` 内では呼ばず、L195-288 の PAYABLE 反映ループ内で同時に補助行を組み立てるリファクタ (preview 構築は preview/convert 専用にする)。

### M-CODE-3. `buildPreview` の動的 DIRECT_PURCHASE 降格で `summaryTemplate="{source_name}"` 固定が、降格元 PAYABLE ルールの摘要設定を破棄する

**箇所**: `PaymentMfImportService.java:794-806`

```java
if (rule != null && e.afterTotal && "PAYABLE".equals(rule.getRuleKind())) {
    rule = MPaymentMfRule.builder()
            .sourceName(rule.getSourceName())
            .ruleKind("DIRECT_PURCHASE")
            .debitAccount("仕入高")
            .debitSubAccount(null)
            .debitDepartment(null)
            .debitTaxCategory("課税仕入 10%")
            .creditAccount("資金複合")
            .creditTaxCategory("対象外")
            .summaryTemplate("{source_name}")  // ← 元 rule の summaryTemplate 破棄
            .build();
}
```

**問題**:
- 設計レビュー M-1 で「`creditDepartment` 等が NULL のままで…」と指摘されているが、`summaryTemplate` の破棄も同種の見落とし。元 PAYABLE ルールが `summaryTemplate="{source_name} 仕入分"` のように追加情報を持っていた場合、20日払いセクションでは `{source_name}` のみになる。
- DIRECT_PURCHASE が 20日払いセクション (`afterTotal=true`) で実出力される頻度は低いが、ワタキューセイモアなど運用上明示的に DIRECT_PURCHASE 登録している supplier (設計レビュー M-1) と「降格して DIRECT_PURCHASE 化される supplier」で摘要書式が混在し、CSV を MF にインポートした後の検索性が悪くなる。
- `tag` フィールドも builder で指定されておらず、降格時に欠落する。

**影響**:
- MF 仕訳の摘要 / タグの不統一。経理側で MF 取込後に検索性が落ちる。
- 設計レビュー M-1 と合わせて「降格ロジックの責務分散」を解消する際、`tag` / `summaryTemplate` も含めた完全な降格ルール変換を `MPaymentMfRule#deriveDirectPurchaseRule()` に切り出すべき。

**修正案**:
```java
if (rule != null && e.afterTotal && "PAYABLE".equals(rule.getRuleKind())) {
    rule = MPaymentMfRule.builder()
            .sourceName(rule.getSourceName())
            .ruleKind("DIRECT_PURCHASE")
            .debitAccount("仕入高")
            .debitTaxCategory("課税仕入 10%")
            .creditAccount("資金複合")
            .creditTaxCategory("対象外")
            .summaryTemplate(rule.getSummaryTemplate() != null
                ? rule.getSummaryTemplate() : "{source_name}")
            .tag(rule.getTag())
            .build();
}
```

### M-CODE-4. `PaymentMfImportController#history` で shop_no=1 がハードコード、admin shop 切替に追従していない

**箇所**: `PaymentMfImportController.java:188-193`

```java
@GetMapping("/history")
public ResponseEntity<List<PaymentMfHistoryResponse>> history() {
    return ResponseEntity.ok(
            historyRepository.findByShopNoAndDelFlgOrderByTransferDateDescIdDesc(1, "0")
                    .stream().map(PaymentMfHistoryResponse::from).toList());
}
```

**問題**:
- 同 Controller の `convert` / `verify` / `exportVerified` は `FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO` (= 1) 経由で shop_no を取得しているのに対し、ここだけリテラル `1` がハードコード。
- 設計上「買掛仕入は shop=1 固定」(`FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO` Javadoc) なので動作上は問題ないが、将来 shop_no を変える / マルチショップ展開する際に grep で発見しづらい。
- フロント `payment-mf-history.tsx` は「shop 切替不要」「admin でも他 shop を見ない」前提で実装されているが、設計書 §11 等にも明示記述がない。
- `PaymentMfImportService#listAuxRows` (L695-703), `exportVerifiedCsv` (L466), `applyVerification` (L220) は全て `FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO` を使っており、Controller の history だけが落ちている。

**影響**:
- リテラル散在による grep 漏れリスク。直接の不具合は無い。

**修正案**:
- `1` → `FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO` に統一。

### M-CODE-5. `PaymentMfRulesPage` の検索フィルタが `paymentSupplierCode.includes(raw)` で大文字小文字未統一、`debitSubAccount` 検索もキー正規化なし

**箇所**: `frontend/components/pages/finance/payment-mf-rules.tsx:95-105`

```js
const filtered = useMemo(() => {
    const raw = search.toLowerCase()
    const q = normalizeName(raw)
    return rules.filter((r) => {
        if (!raw) return true
        const name = normalizeName(r.sourceName.toLowerCase())
        return name.includes(q)
            || (r.debitSubAccount ?? '').toLowerCase().includes(raw)
            || (r.paymentSupplierCode ?? '').includes(raw)   // ← case-insensitive じゃない
    })
}, [rules, search])
```

**問題**:
- `paymentSupplierCode` は数値 6 桁 + ゼロ埋めなので大文字小文字は実質関係ないが、`debitSubAccount` は `toLowerCase()` のみ正規化されており、`normalizeName` (株式会社/㈱ 除去等) を通していない。
- 振込明細未登録一覧 (`/finance/payment-mf-import` の「マッピングマスタを確認」ボタン → `?q=...`) から遷移するとき、`q` が「サラヤ ㈱」のような会社名フォーマットで来るのに対し、検索対象の `r.debitSubAccount` には会社名が入らない (借方補助科目は商品種別等) ため、`debitSubAccount` 部分の `includes` 判定はほぼ常に false で実害は無い。
- ただし `r.sourceName` 側は `normalizeName` 済みで OR 判定の最初の項で正規化通過しているのに、後段 OR 項目は素のテキスト比較で **挙動が分岐しているのに見た目で分からない**。

**影響**:
- ユーザーが借方補助で「カミイソ」と入れても何もマッチしない、というケース (実害は限定的)。
- 検索ロジックの保守時に「どの列が正規化対象か」が直感的でない。

**修正案**:
```js
const filtered = useMemo(() => {
    const raw = search.toLowerCase()
    const q = normalizeName(raw)
    return rules.filter((r) => {
        if (!raw) return true
        const name = normalizeName(r.sourceName.toLowerCase())
        const sub = normalizeName((r.debitSubAccount ?? '').toLowerCase())
        const code = (r.paymentSupplierCode ?? '').toLowerCase()
        return name.includes(q) || sub.includes(q) || code.includes(raw)
    })
}, [rules, search])
```

---

## Minor 指摘

### m-CODE-1. `PaymentMfPreviewRow` が `@Data` + `@Builder` + 19 フィールドで肥大化、検証ロジックなし

**箇所**: `backend/src/main/java/jp/co/oda32/dto/finance/paymentmf/PaymentMfPreviewRow.java:14-44`

- 19 個の `private` フィールドが羅列され、`PAYABLE` / `EXPENSE` / `DIRECT_PURCHASE` / `SUMMARY` / `UNREGISTERED` の 5 種類のレコードを 1 クラスで表現している。各種別で意味のあるフィールドが異なる (UNREGISTERED は `errorType` だけ意味があり、他は null) が、TypeScript 側 (`types/payment-mf.ts:4-31`) ですべて nullable union として漏れ伝わっている。
- `excelRowIndex: int` (primitive) なのに TypeScript 側で `excelRowIndex: number` で 0 が「無効」と区別できない (UNREGISTERED 行も 0 ではないため運用上 OK だが将来的にバグ温床)。

**修正案**: Sealed クラス階層 (`PreviewPayableRow` / `PreviewExpenseRow` / `PreviewSummaryRow` / `PreviewErrorRow`) に分け、フロント側でも discriminated union (`type: 'PAYABLE' | ...`) で型安全化。CLAUDE.md の「Small files (200-400 lines)」原則と合致。

### m-CODE-2. `PaymentMfRuleService#normalizeCompanyName` の正規表現コンパイルが毎呼び出しで走る

**箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfRuleService.java:185-201`

```java
s = s.replaceAll("\\[[^\\]]*\\]", "");
s = s.replaceAll("株式会社|有限会社|合同会社|合資会社|合名会社", "");
s = s.replaceAll("[松竹梅手]\\s*$", "");
s = s.replaceAll("[\\s\\u3000,.\\-・。、]", "");
```

- `String#replaceAll` は内部で `Pattern.compile` を毎回呼ぶ。`backfillPaymentSupplierCodes` (L114-169) で全 PAYABLE ルール × 全 `MPaymentSupplier` のマトリクスでこの関数が呼ばれるため、規模が大きくなると CPU を食う (現状 93 ルール × 数百 supplier ≒ 数万呼び出し)。

**修正案**: `static final Pattern` 4 本を切り出し、`p.matcher(s).replaceAll("")` で再利用。

### m-CODE-3. `PaymentMfHistoryResponse` が `csvBody` の有無 (`csv_body` は BLOB) を返さず、UI で「再 DL 可能か」事前判定不可

**箇所**: `backend/src/main/java/jp/co/oda32/dto/finance/paymentmf/PaymentMfHistoryResponse.java:16-44`, `payment-mf-history.tsx:62-68`

- 履歴一覧で全行に「再DL」ボタンを出しているが、`csvBody` が NULL のレコード (V011 から手動投入された旧データ等) があるとボタン押下時に 404 になる。事前に `hasCsv: boolean` を返してボタンを disable した方が UX が良い。
- 設計書 `design-payment-mf-import.md` ではこのフィールドは触れられていない。

**修正案**: `PaymentMfHistoryResponse.hasCsv = h.getCsvBody() != null && h.getCsvBody().length > 0` を追加し、フロントで `disabled={!h.hasCsv}` 制御。

### m-CODE-4. `PaymentMfImportService.cleanExpired` の `@Scheduled(fixedDelay)` がトランザクション外でログも吐かない

**箇所**: `PaymentMfImportService.java:1114-1118`

```java
@Scheduled(fixedDelay = 5 * 60 * 1000)
void cleanExpired() {
    long now = System.currentTimeMillis();
    cache.entrySet().removeIf(e -> e.getValue().getExpiresAt() < now);
}
```

- `cleanExpired` 実行時に何件削除されたかログが無いため、運用中の cache 規模把握が困難。
- `package-private` メソッドだが `@Scheduled` は public 不要 (Spring 5.0+) なので問題なし。

**修正案**:
```java
int before = cache.size();
cache.entrySet().removeIf(e -> e.getValue().getExpiresAt() < now);
int removed = before - cache.size();
if (removed > 0) log.debug("PaymentMf cache: {}件期限切れ削除 (残{}件)", removed, cache.size());
```

### m-CODE-5. `payment-mf-import.tsx` の `confirmVerify` ダイアログが `loadingState` を持たない

**箇所**: `frontend/components/pages/finance/payment-mf-import.tsx:404-411`

```jsx
<ConfirmDialog
    open={confirmVerify}
    onOpenChange={setConfirmVerify}
    ...
    onConfirm={() => preview && verifyMut.mutate(preview.uploadId)}
/>
```

- `ConfirmDialog` 自体に `loading` prop がないか、または UI で「反映中…」を表示しないため、verify ボタン (L226-231) は disabled 化されているが、ダイアログを **再度開いて再連打** することで `verifyMut.mutate` が複数回走るリスクがある。advisory lock で DB 整合は取られるが、一括検証を 2 回投げると 2 回目は冗長 (`verified_manually=true` の上書き)。
- 設計レビュー C-1 (admin 必須化) と合わせて、verify はワンショットボタンで多重実行ガードが必要。

**修正案**:
- `ConfirmDialog` の `onConfirm` 後に `setConfirmVerify(false)` を呼んでダイアログを閉じる。あるいはダイアログ開閉ロジックを `verifyMut.isPending` で disabled 化する。

### m-CODE-6. `PaymentMfAuxRowsTable` の `useEffect` が `query.data` の参照変化で発火するため、毎 refetch で `onCountChange` が呼ばれる

**箇所**: `frontend/components/pages/finance/PaymentMfAuxRowsTable.tsx:46-53`

```jsx
const onCountChangeRef = useRef(onCountChange)
useEffect(() => {
    onCountChangeRef.current = onCountChange
}, [onCountChange])

useEffect(() => {
    if (query.data) onCountChangeRef.current?.(query.data.length)
}, [query.data])
```

- TanStack Query は `staleTime` 内でも `data` の参照を維持する (`structuralSharing` デフォルト ON) ため、件数が同じなら ref 同値で再発火しないが、件数が変わったときだけでなく、refetch で配列内容のみ変わったときも参照が変わって発火する。`onCountChange` 自体は冪等なので実害は小さい。
- ただし `query.data` ではなく `query.data?.length` を依存配列に入れる方が意図が明確 (件数のみで再通知)。

**修正案**:
```js
useEffect(() => {
    if (query.data) onCountChangeRef.current?.(query.data.length)
}, [query.data?.length])
```

### m-CODE-7. `payment-mf-import.tsx` の `download` 関数が `transferDate` 不在時に `'unknown'` を CSV ファイル名に入れる

**箇所**: `frontend/components/pages/finance/payment-mf-import.tsx:86-107`

```js
const date = preview.transferDate?.replaceAll('-', '') ?? 'unknown'
const suggest = filename ?? `買掛仕入MFインポートファイル_${date}.csv`
```

- バックエンド側 `PaymentMfImportController#convert` (L71) は `payment_mf.csv` 固定でファイル名を返すため、フロント側のフォールバック `suggest` は使われない (filename は常に decode 成功して `買掛仕入MFインポートファイル.csv` が来る)。
- ただし `convert` が `_${yyyymmdd}` を返さない (設計レビュー m-1 で既出) ため、フロント fallback の `_${date}` パターンとも食い違っており、結果的に「日付付き」or「日付なし」のどちらが来るか保証されていない。
- バックエンド修正 (設計レビュー m-1 で `_${yyyymmdd}` 付与を提案) と合わせてフロント fallback も整合させる必要あり。

**修正案**:
- バックエンド `convert` の filename を `payment_mf_${yyyymmdd}.csv` に変更し、UTF-8 側を `買掛仕入MFインポートファイル_${yyyymmdd}.csv` に揃える (これは設計レビュー m-1 で既出)。
- フロント側は `filename ?? ...` の fallback 自体を削除し、「filename がレスポンスに無いケースは異常」として `throw` する方が defensive。

---

## 設計レビューに無いコード固有の発見まとめ

| # | 場所 | 内容 | Severity |
|---|---|---|---|
| C-CODE-1 | `PaymentMfImportService.java:269-287` | `verified_amount` 税率行二重書込 | Critical |
| C-CODE-2 | `PaymentMfCsvWriter.java:95-98` | `fmtAmount` の null 時に末尾スペース欠落 | Critical |
| M-CODE-1 | `PaymentMfImportController.java:66-97`, `PaymentMfImportService.java:1063-1087` | 履歴 ID を呼び元に返さない | Major |
| M-CODE-2 | `PaymentMfImportService.java:184-318` | `applyVerification` 内 buildPreview の rule snapshot 不整合 race | Major |
| M-CODE-3 | `PaymentMfImportService.java:794-806` | DIRECT_PURCHASE 動的降格で `summaryTemplate` / `tag` 欠落 | Major |
| M-CODE-4 | `PaymentMfImportController.java:188-193` | `history` で shop_no=1 リテラル | Major |
| M-CODE-5 | `payment-mf-rules.tsx:95-105` | 検索フィルタの正規化非統一 | Major |
| m-CODE-1 | `PaymentMfPreviewRow.java:14-44` | DTO の 5 種別 union が 1 クラス | Minor |
| m-CODE-2 | `PaymentMfRuleService.java:185-201` | 正規表現コンパイル都度実行 | Minor |
| m-CODE-3 | `PaymentMfHistoryResponse.java`, `payment-mf-history.tsx` | `hasCsv` フラグ未提供 | Minor |
| m-CODE-4 | `PaymentMfImportService.java:1114-1118` | `cleanExpired` ログ無し | Minor |
| m-CODE-5 | `payment-mf-import.tsx:404-411` | `ConfirmDialog` 多重実行ガード不足 | Minor |
| m-CODE-6 | `PaymentMfAuxRowsTable.tsx:46-53` | useEffect 依存が `query.data` で過剰発火 | Minor |
| m-CODE-7 | `payment-mf-import.tsx:86-107` | filename fallback が backend と食い違い | Minor |

---

## レビューチェックリスト結果

### Spring Boot 観点

| 項目 | 結果 | コメント |
|---|---|---|
| Layer 違反 | OK | Controller→Service→Repository の階層遵守 |
| `@Transactional` 配置 | OK | `applyVerification` / `exportVerifiedCsv` (read-write) / `buildVerifiedExportPreview` (`readOnly=true`) / `saveVerifiedExportHistory` (`REQUIRES_NEW`) / `listAuxRows` (`readOnly=true`) と適切 |
| 自己注入パターン | OK | `@Lazy PaymentMfImportService self` で REQUIRES_NEW proxy 確保 (L66-72)。コメントに意図記載あり |
| Constructor injection | OK | `@RequiredArgsConstructor` ではなく明示 constructor (L101-111)。`@Autowired @Lazy private` のみ field injection だが REQUIRES_NEW 用で必要悪 |
| N+1 解消 | OK | `findByShopNoAndSupplierCodeInAndTransactionMonth` で IN 化済 (`buildPreview` L770-774, `applyVerification` L217-222) |
| バリデーション | △ | `PaymentMfRuleRequest` の `ruleKind` が `@NotBlank` のみで値域 (`PAYABLE`/`EXPENSE`/`DIRECT_PURCHASE`) 未強制。`@Pattern` または `enum` 化推奨 (設計レビューでも既出) |
| Migration 安全性 | △ | V011 の `INSERT INTO m_payment_mf_rule` に `ON CONFLICT DO NOTHING` 無く、Flyway repair / 二重実行で重複混入リスク (設計レビューで既出) |
| トランザクション境界 | △ | `applyVerification` の advisory lock は `@Transactional` 内で取得、tx 終了で自動解放。設計レビュー M-3 (shop_no 非含意) も既出 |
| 例外設計 | △ | `saveHistory` (L1063-1087) と `saveVerifiedExportHistory` 呼び出し (L582-584) の例外握り潰しが「履歴保存失敗を呼び元に返さない」設計。M-CODE-1 と関連 |

### Next.js 観点

| 項目 | 結果 | コメント |
|---|---|---|
| 'use client' 配置 | OK | 全ページ先頭に適切に付与 |
| TanStack Query 使用 | OK | `staleTime` 設定済み (`payment-mf-rules.tsx:50` 60_000, `VerifiedCsvExportDialog.tsx:47` 10_000, `PaymentMfAuxRowsTable.tsx:41` 30_000) |
| TypeScript strict | OK | `null` / nullable の扱いは概ね適切 |
| useEffect 依存配列 | △ | `PaymentMfAuxRowsTable.tsx:53` の `[query.data]` は `[query.data?.length]` の方が意図的 (m-CODE-6) |
| エラーハンドリング | OK | `ApiError` instanceof 判定 + toast (`VerifiedCsvExportDialog.tsx:88-91`) |
| アクセシビリティ | △ | `payment-mf-import.tsx` の Badge / 絵文字 (??????) は色覚多様性に配慮した文字併記済み |
| ファイル size | OK | `payment-mf-import.tsx` 467 行で CLAUDE.md 上限 800 ギリギリ。関数分割は将来検討 |

### Payment MF 固有観点

| 項目 | 結果 | コメント |
|---|---|---|
| CSV 形式 (CP932 + LF + 末尾半角スペース) | △ | `PaymentMfCsvWriter` で実装。null 時の末尾スペース欠落リスク (C-CODE-2) |
| afterTotal フラグ (PAYABLE→DIRECT_PURCHASE 降格) | △ | `summaryTemplate` / `tag` 欠落 (M-CODE-3)、設計レビュー M-1 と複合 |
| aux_row 洗い替え保存 | OK | `flushAutomatically=true, clearAutomatically=true` で DELETE→INSERT の順序保証 (Repository L30) |
| Excel パーサ異常系 | △ | `PaymentMfCellReader#readLongCell` の long overflow silent truncate (設計レビュー m-7 既出)。FORMULA セルでの string→numeric フォールバックは適切 (`readLongCell` L75-77) |
| 100円閾値判定 | OK | `FinanceConstants.PAYMENT_REPORT_MINOR_DIFFERENCE` で集約。`abs().compareTo(threshold) <= 0` (BigDecimal) と `Math.abs(diff) <= long_threshold` (long) の両方使用、scale 0 前提で問題なし |
| BigDecimal scale | △ | `toLongFloor` (L403-405) で `setScale(0, DOWN).longValueExact()` 呼んでおり、scale > 0 の値を切り捨て → 軽微な丸め差は発生しうるが運用想定範囲内 |
| advisory lock キー | △ | `transactionMonth.toEpochDay() & 0xFFFF_FFFFL` で shop_no を含まず (設計レビュー M-3 既出) |
| verified_amount の二重計上 | NG | C-CODE-1。書込スキーマ自体に欠陥 |

---

## 評価まとめ

設計レビューが指摘した課題群 (Critical 3 / Major 6 / Minor 7) と独立に、**コード固有の Critical 2 / Major 5 / Minor 7** を発見した。

特に **C-CODE-1 (verified_amount 二重計上)** は、設計レビュー C-3 で「税抜逆算の精度問題」として部分的に触れられているが、**書込スキーマ自体が二重計上を内包**しており、`sumVerifiedAmountForGroup` という読取側のヒューリスティックでしか整合性を保てない構造になっている点が本レビューでの新発見。マージ前に書込仕様を整理する必要がある。

**C-CODE-2 (CSV 末尾スペース欠落)** はフォーマット契約違反の潜在リスクで、現状の経路では発生しないが防御的修正を推奨。

Major 群は履歴 ID 返却 (M-CODE-1)、降格ルール完全性 (M-CODE-3)、shop_no リテラル (M-CODE-4) など、運用追跡性とリファクタリング耐性に関わる課題。

Minor 群は DTO 設計、UI 多重実行ガード、検索フィルタ等の品質改善で、時間が許す範囲で対応すれば良い。

設計レビュー Critical 群と本レビュー C-CODE-1/C-CODE-2 の解消をマージ前条件とし、Major 群は v2.1 ドットリリースで対応するのが現実的。
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1892ms:
# 設計レビュー: 買掛仕入 MF 変換 (Cluster C)

レビュー日: 2026-05-04
対象設計書: `claudedocs/design-payment-mf-import.md`, `claudedocs/design-payment-mf-aux-rows.md`
レビュアー: Opus サブエージェント

## サマリー

- 総指摘件数: **Critical 3 / Major 6 / Minor 7**
- 承認状態: **Needs Revision**

実装は設計の意図にほぼ沿っており、ゴールデンマスタテスト 2 本 PASS、N+1 解消や advisory lock など改善が積み上がっている。一方で「設計書 §5.1 と実装の食い違いが設計書側でも認知されているのに是正されていない」「PreAuthorize の付け方が `convert` で抜けている」「20日払いセクションの DIRECT_PURCHASE 降格が PAYABLE 突合・税抜逆算と整合していない」など、**運用整合性 / セキュリティ / データ品質** に関わる Critical/Major が複数存在する。マージ前に少なくとも Critical の解消と Major の方針確認が望ましい。

---

## Critical 指摘

### C-1. `PaymentMfImportController#convert` のロール権限が落ちている (admin だけのはずの CSV 生成が一般ユーザでも実行可能)

- **箇所**: `backend/src/main/java/jp/co/oda32/api/finance/PaymentMfImportController.java:66-83`
- **問題**:
  - `/convert/{uploadId}` は `@PreAuthorize("isAuthenticated()")` のみで、`hasRole('ADMIN')` が付いていない (クラス宣言 L33 の継承だけ)。
  - 同じ Controller 内の `/export-verified` (L109) / `/aux-rows` (L171) / `/rules` PUT/DELETE (L227, L236) / `/rules/backfill-codes` (L248) はすべて `hasRole('ADMIN')` 必須。
  - `verify` (L87) も同じく一般ユーザ実行可能で、買掛金一覧 `t_accounts_payable_summary` の `verified_amount` / `verified_manually=true` を一括書き込みする破壊的操作。
- **影響**: 一般ユーザがマスタ整備中の Excel を誤アップロードして convert/verify するだけで、本番の `t_accounts_payable_summary` (PAYABLE) と `t_payment_mf_aux_row` (補助行) が洗い替えされる。会計仕訳の根拠データを誰でも上書きできる状態。
- **修正案**:
  ```java
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/convert/{uploadId}")
  public ResponseEntity<?> convert(...)

  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/verify/{uploadId}")
  public ResponseEntity<?> verify(...)
  ```
  あわせて `/preview` (L43) と `/preview/{uploadId}` (L57) は閲覧目的なので `isAuthenticated()` 維持で OK だが、`POST` で副作用ありに見えるため `@PostMapping` → `@GetMapping` を再検討するか、Controller 上部に「副作用なし」コメントを追加すること。

### C-2. 設計書 §5.1 の送金日マッピング記述が運用実態と矛盾したまま放置されている

- **箇所**: `claudedocs/design-payment-mf-import.md:174-194` (§5.1) と `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java:996-1013` (`deriveTransactionMonth` の Javadoc)
- **問題**:
  - 設計書 §5.1 は「**現行ルール: 5日払い・20日払いともに前月20日締めに統一**」と書かれており、コードと一致しているように見える。しかし実装側 Javadoc は「※ design-payment-mf-import.md §5.1 の『20日→当月20日締め』記述は運用実態と異なるため**次回設計書更新で是正すること**」と明示しており、`MEMORY.md` にも「20日→当月20日記述は運用実態と異なる」と記録されている。
  - つまり「設計書の §5.1 表は最新化されている」のに「コード側 Javadoc は古い設計書の旧記述を是正対象として参照している」状態。設計書か Javadoc のどちらかが嘘をついており、レビュー時に「どちらが事実か」を読者が判別できない。
  - feedback 「設計書とコードが食い違う時は運用実態を優先」(MEMORY.md より) に従うべき箇所だが、運用実態 = 「両方とも前月20日締め」が事実なら、設計書 §5.1 の本文 (現行ルールセクション) はコードと一致するため、Javadoc の「§5.1 の記述は運用実態と異なる」コメントは古い情報を残しており、是正済みなのに「未是正」と読者に錯覚させる。
- **影響**: 新メンバーが「設計書が間違っているらしい → 自分で運用ヒアリングしないと信用できない」と判断し、二重確認コストが恒常化する。逆に Javadoc を信じて設計書を「直さなきゃ」と再修正する改ざんリスク。
- **修正案**:
  - **どちらが事実か確定** → 運用 (経理) に再確認 → 確定値で両方を統一。
  - Javadoc 側の「※ design-payment-mf-import.md §5.1 ... 次回設計書更新で是正すること」は、設計書本文が現行に合っているなら削除する。残す場合は「§5.1 旧記述 (rev1)」のように **どのリビジョンの記述に対するメモか** を明示する。
  - 設計書 §5.1 末尾の「（旧仕様では 20日払いは『当月20日締め分』と突合していたが…）」は履歴としては有用だが、これも残骸として誤読されやすいので「旧仕様 (?2026-04-14)」のように日付を入れて archived マーク化する。

### C-3. `PaymentMfImportService#applyVerification` 内で `buildPreview` が 2 回走り、税率別集計の整合性も `payable` 集計と齟齬する

- **箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/PaymentMfImportService.java:184-305` (特に L228-288 と L293-294)
- **問題**:
  1. **二重 buildPreview**: メソッド冒頭で `List<MPaymentMfRule> rules = ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0")` + `payablesByCode` を独自に組み立てて PAYABLE 反映ループを回す (L195-288)。直後の L293 で `PaymentMfPreviewResponse preview = buildPreview(uploadId, cached);` を呼び、補助行保存に渡している。`buildPreview` (L738-944) も内部でまったく同じ rules ロードと `payablesByCode` 構築をやり直す。コメント (L290-292) は「N+1 が 2 周していた」のを 1 周に減らした、と書かれているが、現状でも rules / payables の DB ヒットは 2 度発生している (`buildPreview` 用 + applyVerification 用)。
  2. **税抜逆算ロジックの精度問題**: L276-281 で `BigDecimal divisor = BigDecimal.valueOf(100).add(taxRate)` → `invoice * 100 / divisor (DOWN)`。`taxRate` が 10 (%) の場合 `100 / 110 ≒ 0.909...`。`invoice = 1100` なら 1000 円ぴったりになるが、複数税率行 (10% と 8%) が同一 supplier にある場合、両方とも `invoice` 全額 (110%税込) を税抜化するため、合算すると **元の税込総額より大きい税抜合計** が DB に残る。コメント L276 に「単一税率前提。複数税率の仕入先は手動調整で対応」と但し書きはあるが、UI でこの状態を検出する仕組みがなく、後段の税額計算で誤差が積もる。
  3. **payable 突合は `taxIncludedAmountChange ?? taxIncludedAmount` を税率行 SUM (L247-252) しているが、税抜逆算は税率行ごとに同じ `invoice` (税込総額) を投入** している。突合対象 (税込合計) と書込対象 (税率別税抜) のスケールが違う。突合判定 (`isMatched`) と書込が一致前提なので、運用実態として「単一税率行しかない」「同一税率の重複行のみ」に依存している。
- **影響**:
  - 1 は性能ロス (rules + payables × 2 ヒット)。実害は中。
  - 2/3 は買掛金集計の財務整合に直結。複数税率 supplier が出現した瞬間に `verified_amount_tax_excluded` が壊れ、検証済みCSV出力 (`exportVerifiedCsv`) で MF に渡る金額が誤る。コメント上は「手動調整」と書かれているが、UI 側でアラート表示も無いため気付けない。
- **修正案**:
  - 1: PAYABLE 反映ループに渡すのと、`saveAuxRowsForVerification` に渡すのを **同一の preview** にする。冒頭 `PaymentMfPreviewResponse preview = buildPreview(uploadId, cached);` を最初に 1 回だけ作り、L228 のループも `preview.getRows()` の PAYABLE 行に対して反映する形にリファクタすると DB ヒット数も理解難度も下がる。
  - 2/3: 複数税率 supplier の検出ロジックを追加し、検出時は警告ログ + UI 警告 (`VerifyResult` に `multiTaxRateSuppliers: List<String>` を追加) する。あるいは、税率別の請求額内訳を Excel 側で持っていない以上、複数税率 supplier は一括検証対象から除外して必ず手動 verify に回す方針を明示。

---

## Major 指摘

### M-1. 20日払いセクション (`afterTotal=true`) の `PAYABLE → DIRECT_PURCHASE` 降格処理が責務分散している

- **箇所**: `PaymentMfImportService.java:794-806` (`buildPreview` 内)
- **問題**:
  - 合計行以降の PAYABLE ヒットを「`MPaymentMfRule.builder()` で新しい DIRECT_PURCHASE ルールを動的生成」して差し替える、という処理を `buildPreview` 内に直書きしている。
  - 設計書 §5.3 では「ルックアップ統一」を謳っているが、実装は「ルックアップ後に書き換え」しており、設計と実装で責務の置き場所がズレている。
  - 動的生成ルールには `creditDepartment` 等が NULL のままで、もとの PAYABLE ルールに `creditDepartment` が設定されていた場合に消失する。現在のシードでは PAYABLE 側の `credit_department` は全部 NULL なので問題は表面化していないが、将来 PAYABLE ルール側に `credit_department` を追加した瞬間に DIRECT_PURCHASE 側で落ちる。
  - 同じ「降格」ロジックが `applyVerification` 側にはなく、`afterTotal` 行は L230 で `skipped++` で抜けるため、PAYABLE rule が存在する supplier でも `t_accounts_payable_summary` への一括検証反映から外れる。`buildPreview` だけが DIRECT_PURCHASE 化するため、CSV 出力結果と verified 状態 (DB) で挙動が異なる。
- **影響**:
  - DIRECT_PURCHASE で出力された supplier が買掛金一覧で「未検証」のまま残る。経理が後追いで気付くまで `t_accounts_payable_summary` のステータスが正しくない。
  - PAYABLE ルールに将来カラム追加すると DIRECT_PURCHASE の出力欠落が発生する潜在バグ。
- **修正案**:
  - 「`afterTotal=true` かつ PAYABLE ヒット → DIRECT_PURCHASE 降格」を `MPaymentMfRule#deriveDirectPurchaseRule()` などのメソッドに切り出し、buildPreview と applyVerification の両方から呼ぶ。
  - DIRECT_PURCHASE ルールが m_payment_mf_rule に明示登録されている supplier (例: ワタキューセイモア㈱) と、PAYABLE ルールから動的降格する supplier の **どちらが優先されるか** を設計書 §5.3 に追記。現状はコード上、`afterTotal=true` セクションのレコードがまず PAYABLE/EXPENSE/DIRECT_PURCHASE のいずれかを byCode/bySource で引き、PAYABLE のみ降格する仕様。

### M-2. `PaymentMfImportService#cache` がノードローカルでマルチインスタンス HA に弱い (設計書未記載)

- **箇所**: `PaymentMfImportService.java:99` (`Map<String, CachedUpload> cache`)
- **問題**:
  - コード側コメント (L93-98) に「single-instance 前提」「マルチ化するときは Redis 等に寄せること (B-W9)」と記載されているが、設計書 (`design-payment-mf-import.md` §7) には `uploadIdキャッシュ` の項に「cashbook-import と同じパターン」とだけあり、HA 制約は触れていない。
  - 5日払い Excel と 20日払い Excel の `applyVerification` を同時に行うケースで、Web ノードが 2 台あれば preview / convert / verify が別ノードに分散して 404 になる。
- **影響**: 将来 LB 配下に複数 Web ノードを置いた瞬間、誰も気付かないところで preview/convert が壊れる。
- **修正案**:
  - 設計書 §7 に「single-instance 前提」「将来マルチ化時は Redis/PostgreSQL に寄せる候補」を追記。
  - 当面の運用ガード: アプリ起動時のヘルスチェックで `cache.size() > 0` を Prometheus に出すか、Cookie sticky を nginx 側で確実にする運用注記を README に追加。

### M-3. `applyVerification` の advisory lock キー設計が shop_no を反映していない

- **箇所**: `PaymentMfImportService.java:86-87, 312-318`
- **問題**:
  - `ADVISORY_LOCK_CLASS = 0x7041_4D46` (高位32bit) + `transactionMonth.toEpochDay()` (低位32bit) という構造で、`shop_no` がキーに含まれていない。
  - 現状 shop_no = 1 固定 (`FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO`) なのでセーフだが、将来 shop_no=2 を追加した場合、別 shop の同じ取引月で待たされる。逆に同じ shop の同じ月では `transactionMonth` 一致で適切にシリアライズされる。
- **影響**: マルチショップ展開時に競合誤判定。`MEMORY.md` の B-CART 事業部統合方針で「将来統合前提」とあるが、買掛仕入は事業部別に運用され続ける可能性が残る。
- **修正案**:
  - 32bit 全部使うなら `transactionMonth.toEpochDay() & 0xFFFFFF` (24bit ≒ 4 万年) + `shop_no & 0xFF` (256 shop) など。シンプルには Long.hashCode((shopNo + ":" + transactionMonth).hashCode()) でも十分。
  - 設計書 §7 に advisory lock の存在自体が書かれていないため追記する (運用上「同時アップロードは直列化される」は重要な仕様)。

### M-4. `exportVerifiedCsv` の supplier 集約で「ルール未登録 / 非PAYABLE」を skip している間に `payment-supplier-code` 補完漏れが見逃される

- **箇所**: `PaymentMfImportService.java:495-502` (`exportVerifiedCsv`) および `buildVerifiedExportPreview` L627-634
- **問題**:
  - `t_accounts_payable_summary.supplier_code` で PAYABLE ルールを `byCode.get(s.getSupplierCode())` 引きするが、ルールに `payment_supplier_code` 未設定だと `byCode` に登録されない (L484-489)。
  - 結果として「PAYABLE ルールはあるが backfill-codes が走っていない supplier」は CSV から除外され、`skippedSuppliers` リストに「ルール未登録」と誤表示される (実際は「コード未補完」)。
  - `buildPreview` 側 (L851-857) は `rulesMissingSupplierCode` を別フィールドで返すように分岐しているのに、`exportVerifiedCsv` 側は同じ罠を踏んでいる。
- **影響**: 一括検証は PASS するが検証済みCSV出力で消える supplier が出る。経理が「ルール未登録」表示を信じてマスタ追加→重複ルールができる二次被害の可能性。
- **修正案**:
  - `exportVerifiedCsv` でも「PAYABLE ルールはあるが code 未補完」を別ステータスで `skippedSuppliers` に詰める (`"<code> (supplier_no=X) ルール `<source>` の payment_supplier_code 未補完。/finance/payment-mf-rules で『支払先コード自動補完』を実行してください")` 等)。
  - もしくは `bySource` ルックアップを fallback として追加し、source_name が一致するルールを採用する。ただし supplier 名が複数候補ある場合は誤マッチするので backfill 指示の方が安全。

### M-5. `applyVerification` の補助行洗い替えで「Excel 内の sequence_no」と「CSV 出力時のソート順」が一致しない可能性

- **箇所**: `PaymentMfImportService.java:340-372` (`saveAuxRowsForVerification`) と `PaymentMfPreviewResponse#getRows` の生成順 (`buildPreview` L788-895)
- **問題**:
  - `seq` は `preview.getRows()` を上から走査して PAYABLE / UNREGISTERED を skip しつつ +1 する。PAYABLE 行が間に挟まる Excel (本社仕入セクションに固定費送り先が混在するケース) では `sequence_no` が「0,1,5,8,10,...」のように飛ぶことはなく連番だが、**Excel 内の物理出現順 (rowIndex) とは一致しない**。設計書 §3.1 (claudedocs/design-payment-mf-aux-rows.md:54) では「Excel 内の出現順 (CSV出力順序維持)」と説明されているが、実装は「PAYABLE を除いた縮約順序」になっている。
  - CSV 出力時 (`exportVerifiedCsv` L545) は `transferDate ASC, sequenceNo ASC` で読むため、5日 Excel と 20日 Excel が混在する月の出力で、5日分の SUMMARY 2 行が EXPENSE 群より先に CSV に出てしまう (sequence_no が小さい順だが、SUMMARY は L878-895 の `rows.add` で末尾に追加されるため最大の seq になり、結果的に末尾に来る ? これは現状動く)。ただし将来 SUMMARY 行を中間に置く Excel 形式変更があると壊れる。
- **影響**: 直近では運用通り。ただし設計書の「Excel 内の出現順」記述と乖離があり、Excel 形式の変化に脆い。
- **修正案**:
  - `seq` は `e.rowIndex` (PaymentMfExcelParser.ParsedEntry のフィールド L156) を採用する。SUMMARY 行は Excel に明細としては存在しないので、「合計行 rowIndex + 0.5 / +0.6」相当のソートキーを別途持たせる。
  - または「Excel 出現順を維持」を取り下げ、設計書 §4.3 の出力順序 (PAYABLE → 5日EXPENSE → 5日SUMMARY → 20日EXPENSE → 20日SUMMARY → DIRECT_PURCHASE) を明示する代わりに、`sequence_no` を `(ruleKind優先度, rowIndex)` の合成キーとして設計書に明示する。

### M-6. `MPaymentMfRule` Entity が `IEntity` 未実装で共通の `del_flg` 取扱いから外れている

- **箇所**: `backend/src/main/java/jp/co/oda32/domain/model/finance/MPaymentMfRule.java:11-83`
- **問題**:
  - プロジェクト規約 (`CLAUDE.md` 「論理削除: del_flg、IEntity インターフェース」) に従えば `IEntity` を implement すべきだが未実装。`TPaymentMfImportHistory` 同様。
  - `TPaymentMfAuxRow` (L143-174) は `IEntity` を意識的に実装せず物理削除運用と明記しているのに対し、`MPaymentMfRule` は `del_flg` を持っているのに `IEntity` 抜きで `delete()` 内で手動セット (`PaymentMfRuleService.java:82-89`)。
  - 監査フィールドも手動 (`addDateTime` / `addUserNo` / `modifyDateTime` / `modifyUserNo`)。`CustomService` を通せば共通化できる。
- **影響**: 規約違反。直接の不具合は無いが、将来「IEntity 経由で del_flg 検出」する横断機能 (例: `IGenericRepository` で論理削除フィルタ) を入れたときに漏れる。
- **修正案**:
  - `MPaymentMfRule implements IEntity` で `getDelFlg/setDelFlg` を実装し、`PaymentMfRuleService` を `CustomService` 経由に寄せる。`add_user_no` / `modify_user_no` の手書きを共通処理に委譲できる。
  - 既存テスト (Golden Master) はリフレクションで触っていないので互換維持可能。

---

## Minor 指摘

### m-1. `PaymentMfImportController#convert` で生成する CSV ファイル名が `payment_mf.csv` 固定で、HTTP ヘッダの `filename*=UTF-8''` 側だけ「買掛仕入MFインポートファイル.csv」を指している

- **箇所**: `PaymentMfImportController.java:71-77`
- **問題**: ASCII 互換 `filename` 部分が日付なし固定。`/export-verified` (L120) は `_yyyymmdd` 付きで揃っている。
- **修正案**: `convert` でも `cached.getTransferDate()` から `_yyyymmdd` を埋める。古いブラウザ向けに半角ファイル名も `payment_mf_${yyyymmdd}.csv` にする。

### m-2. `PaymentMfRuleService#backfillPaymentSupplierCodes` が dryRun=true でも `repository.findByDelFlgOrderByPriorityAscIdAsc("0")` を毎回実行

- **箇所**: `PaymentMfRuleService.java:97-178`
- **問題**: dryRun は admin が画面ボタンから繰り返し叩く想定。100件超のルール × MPaymentSupplier の名寄せを毎回 in-memory でやるのは妥当だが、ルール件数が増えると O(N×M) が無視できなくなる。
- **修正案**: 結果キャッシュは不要だが、`@Transactional(readOnly = true)` を dryRun=true 用に分けると read-only 最適化が効く (実装は `@Transactional` のみで read-write のまま)。

### m-3. `PaymentMfImportService#deriveTransactionMonth` が Service の package-private static でなくインスタンスメソッド

- **箇所**: `PaymentMfImportService.java:1011-1013`
- **問題**: `LocalDate` の純粋関数 (`transferDate.minusMonths(1).withDayOfMonth(20)`) で `this` 依存無し。テスト時に Service 全体をビルドする必要がある。
- **修正案**: `static LocalDate deriveTransactionMonth(LocalDate transferDate)` にする。`PaymentMfExcelParser` などの static utility 群と一貫する。

### m-4. `TPaymentMfAuxRow` の物理削除運用と `IEntity` 整合の説明が JavaDoc にしかない

- **箇所**: `TPaymentMfAuxRow.java:13-30`, `design-payment-mf-aux-rows.md:165-174`
- **問題**: 設計書 §3.2 Q2 に「論理削除を使わない理由」は書かれているが、`IEntity` の実装方針 (no-op del_flg) は設計書では言及されない。Entity 側 JavaDoc 経由でしか読み取れない。
- **修正案**: 設計書 §3.3 の Entity 説明に「`IEntity#getDelFlg` は常に `'0'` を返す no-op 実装。共通リポジトリのソフトデリートフィルタを通すためだけのスタブ」と追記。実装が `IEntity` を実装していないなら、その方針も明示。
- **追記**: 現状実装 (TPaymentMfAuxRow.java) は `IEntity` を **実装していない**。設計書 §3.3 のサンプルコードでは `implements IEntity` として書かれているが、L172-174 の `@Override` 付き no-op メソッドが現実装には無い。設計書サンプルと実装で差がある。

### m-5. `exportVerifiedCsv` の例外時に advisory lock を取得後 `auxRowRepository.find...` で空かつ DB 行も空のとき `IllegalStateException` (L471-475) を投げるが、advisory_xact_lock は tx 終了時に自動解放されるため OK だが Javadoc に明示無し

- **箇所**: `PaymentMfImportService.java:460-475`
- **問題**: advisory_xact_lock は @Transactional の境界で auto release という前提が JavaDoc にない (L312-318 にはある)。読者が安全性を確認するために L312 までスクロールする必要がある。
- **修正案**: `exportVerifiedCsv` の Javadoc にも「advisory lock は @Transactional 境界で自動解放されるため、early return しても解放漏れなし」を追記。

### m-6. PAYABLE ルールのシードが `payment_supplier_code = NULL` で 93 件投入され、運用直後の backfill 必須

- **箇所**: `backend/src/main/resources/db/migration/V011__create_payment_mf_tables.sql:55-153`
- **問題**: 設計書 §11 確定事項に「`m_payment_mf_rule` PAYABLE 74件に `payment_supplier_code` を自動補完済み (2026-04-15)」とある (実数 93 件と乖離があるが)。新規 deploy 環境ではシード直後 backfill 必須だが、この依存が migration の手順書に書かれていない。
- **修正案**: V012 等で「シード直後に `backfillPaymentSupplierCodes(dryRun=false)` 相当を実行する」ステップを SQL でやるか、起動時 ApplicationRunner で 1 回実行する仕組みを追加。少なくとも設計書 §10 実装順 と V011 ヘッダコメントに「migration 後に admin が backfill ボタンを押すこと」と明示。

### m-7. `PaymentMfCellReader#readLongCell` がオーバーフロー時に static long cast で silent truncate

- **箇所**: `PaymentMfCellReader.java:64-82`
- **問題**: `(long) cell.getNumericCellValue()` は double → long の暗黙キャストで、`Long.MAX_VALUE` を超える double は `Long.MAX_VALUE` に飽和する (Java 仕様)。Excel の請求額が `1e20` 等の異常値だった場合に検出できない。
- **修正案**: `if (Double.isNaN(d) || d > Long.MAX_VALUE || d < Long.MIN_VALUE) return null;` の上限ガード追加。あるいは `Math.round(d)` を使い、`AccountsPayable` 想定上限 (例: 100億円) でリジェクトする。

---

## 設計書 vs 実装の乖離

| # | 場所 | 設計書 | 実装 | 影響 |
|---|---|---|---|---|
| D-1 | §5.1 送金日マッピング | 「現行ルール: 5日/20日とも前月20日締め」(明示済) | コード Javadoc に「§5.1 の旧記述は是正対象」と残存 | C-2 で詳述。設計書とコードでどちらが最新か読み取れない |
| D-2 | §3.3 (`design-payment-mf-aux-rows.md`) | `TPaymentMfAuxRow implements IEntity { @Override no-op }` | `IEntity` 実装無し (L30 `@Table` 直書き) | m-4 / M-6。実装簡易化の判断は妥当だが設計書が古い |
| D-3 | §4.1 PAYABLE 反映ループ | `for (ParsedEntry e : cached.getEntries()) { /* PAYABLE 反映 */ }` の擬似コード | 実装は事前 codesToReconcile 集約 + payablesByCode 一括ロードで N+1 解消 | 設計書サンプルが N+1 ナイーブ実装になっている。 §4.1 を実装に合わせて更新するか「概念フロー」と注記 |
| D-4 | §11 確定事項 PAYABLE 74件 | `m_payment_mf_rule` PAYABLE 74件 | V011 シードは PAYABLE 93件 (シードに19件追加された?) | 設計書数値が古い。脚注で更新日付を明示 |
| D-5 | §7 uploadId キャッシュ | 「cashbook-import と同じパターン」 | 実装 L93-99 に「single-instance 前提」「マルチ化時は Redis 等」と明記 | M-2。設計書に HA 制約を追記 |
| D-6 | §7 advisory lock | 言及なし | `pg_advisory_xact_lock(transactionMonth.toEpochDay)` で applyVerification/exportVerifiedCsv を直列化 | M-3。設計書追記必要 |
| D-7 | §6.3 (`design-payment-mf-aux-rows.md`) 警告判定 | `transferDate 範囲 = transactionMonth + 1ヶ月の 5日 と 20日` | 実装 L662-681 は `PAYMENT_DATE_MIDMONTH_CUTOFF = 15` 境界の前半/後半判定 (土日振替対応) | 実装側が現実に合わせて改善されている。設計書を実装に追従させる |
| D-8 | §10.5 5日/20日整合性チェック | 「v2 では緩く (警告ログ) でスタート」 | 実装 L658-681 で警告メッセージ生成済み (緩い実装) | 設計通り |

**特筆**: §5.1 (D-1) は `MEMORY.md` の feedback「設計書とコードが食い違う時は運用実態を優先」「ゴールデンマスタが empty stub している箇所は PASS が正当性を保証しない」に直撃する案件。ゴールデンマスタテスト (`PaymentMfImportServiceGoldenMasterTest`) は `payableRepository.findByShopNoAndSupplierCodeAndTransactionMonth` を `Collections.emptyList()` で stub しているため、突合ロジックの妥当性は **テストでは検証されていない**。CSV のフォーマット等価性は確認できているが、`deriveTransactionMonth` のずれが起きても PASS する状態。手元の運用検証で「実 t_accounts_payable_summary との突合がきちんと動いているか」を別途確認することを強く推奨。

---

## レビューチェックリスト結果

### Spring Boot 観点

| 項目 | 結果 | コメント |
|---|---|---|
| Layer 違反 | OK | Controller→Service→Repository の階層遵守。`PaymentMfImportController` は薄い |
| `@Transactional` 配置 | 概ね OK / 1 NG | `applyVerification` / `exportVerifiedCsv` / `saveVerifiedExportHistory(REQUIRES_NEW)` 適切。`PaymentMfRuleService#findAll` (L30) は readOnly 指定無し → m-2 |
| N+1 解消 | OK | `payableRepository.findByShopNoAndSupplierCodeInAndTransactionMonth` で IN 化済み (L40)。コメント B-W11 に解消経緯記載 |
| DI | OK | constructor injection (`PaymentMfImportService` L101) + `@Lazy` 自己注入で REQUIRES_NEW proxy 確保 (L66-72) は妥当 |
| DTO 変換 | OK | `PaymentMfPreviewRow.builder()` / `PaymentMfRuleResponse.from(entity)` 等。Entity を直接返さない |
| バリデーション | △ | `PaymentMfRuleRequest` は `@NotBlank` 散発。`ruleKind` の値域 (PAYABLE/EXPENSE/DIRECT_PURCHASE) は `@Pattern` または ENUM 化が望ましい |
| Migration 安全性 | OK | V011 で `IF NOT EXISTS`。V016 補助行 migration も `IF NOT EXISTS` + 部分インデックス。ただし re-run 時の seed 重複は防げない (`INSERT INTO m_payment_mf_rule` に `ON CONFLICT DO NOTHING` 無し)。本番環境で V011 を 2 度流すと PAYABLE 93件 × 2 になる潜在リスク |

### Payment MF 固有観点

| 項目 | 結果 | コメント |
|---|---|---|
| CSV 形式 (CP932 + LF + 末尾半角スペース) | OK | `PaymentMfCsvWriter.java:29-30, 95-98` で実装済。Charset は `Charset.forName("MS932")` で例外時クラッシュ無し |
| afterTotal フラグ (PAYABLE→DIRECT_PURCHASE 降格) | △ | M-1 参照。`buildPreview` のみで降格、`applyVerification` は skip と非対称 |
| 送金日→取引月マッピング (前月20日締め固定) | OK / 設計書NG | 実装 (`deriveTransactionMonth` L1011) は `transferDate.minusMonths(1).withDayOfMonth(20)` で運用実態通り。C-2 / D-1 |
| 補助行洗い替え (UNIQUE 制約・再投入) | OK / △ | 物理 DELETE + saveAll で `(shop_no, transaction_month, transfer_date)` 単位の洗い替え。UNIQUE 制約は migration に無いが、洗い替え運用なので DB 層には不要。M-5 (sequence_no の Excel 順) は別問題 |
| 100円閾値突合 | OK | `FinanceConstants.PAYMENT_REPORT_MINOR_DIFFERENCE` (L75) で集約、コード散らばり無し |
| Excel パーサ異常系 | △ | `PaymentMfCellReader#readLongCell` で long overflow を silent truncate (m-7)。NaN/Inf もハンドル不十分 |
| 設計書とコードの食い違い | NG | D-1 ? D-7 (特に D-1) |

---

## 評価まとめ

実装品質自体は高く、N+1 対応・advisory lock・self injection (REQUIRES_NEW)・cache TTL/サイズ制御など、本番運用を意識した防御コードが入っている。ゴールデンマスタテスト 2 本 PASS で CSV 出力の正確性も担保されている。

しかし以下の観点で **マージ前に対応すべき** 課題が残る:

1. **C-1 セキュリティ**: `convert` / `verify` の admin 必須化は最優先 (ロール権限抜け)
2. **C-2 / D-1 設計書整合**: §5.1 と Javadoc の矛盾解消。再発防止のため運用に再確認
3. **C-3 / M-5 データ整合**: 複数税率 supplier の検出、sequence_no の Excel 順整合

その他 Major は v2.1 ドット リリースで対応可能だが、特に **M-1 (DIRECT_PURCHASE 降格の責務分散)** は将来的なバグ温床なので近いリリースで整理推奨。Minor は時間が許す範囲で。

設計書側の更新項目 (D-2, D-4, D-5, D-6) は本レビュー後に実装担当者が一括反映すれば、後続レビュアーが「設計書 vs コード」の二重確認をしなくて済む。
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2899ms:
# 買掛仕入MFインポート（Payment MF Import）設計書

作成日: 2026-04-14
ステータス: Draft
関連: `/finance/cashbook-import`（既存）、`/finance/accounts-payable`（既存）

## 1. 目的

経理担当者が作成する **振込み明細Excel**（月2回、5日/20日払い）から、MoneyForward仕訳インポート用CSV（買掛仕入MFインポートファイル_{yyyymmdd}.csv）を生成する。同時に既存の **買掛金一覧（`t_accounts_payable_summary`）** と突合し、金額差異・未登録仕入先を画面上でハイライトして検出する。

### 現行フロー（移行前）
- Excel の `MF変換` シート内の VLOOKUP/参照式で `支払い明細` シートから生成
- `変換MAP` シートで送り先名→MF補助科目をひきあて
- 手作業で CSV を csv 保存して MF にインポート

### 移行後
1. 画面で振込み明細 xlsx をアップロード
2. プレビューで 買掛金一覧との突合結果を確認（??一致/??金額差異/??買掛金なし）
3. 未登録送り先を画面上でマスタ登録・編集
4. CSVをダウンロード & 変換履歴を保存

---

## 2. 入力仕様（振込明細Excel）

月2回（5日払い・20日払い）で発行される2バリエーションの Excel を単一のパーサで処理する。ファイル間で**シート名・列レイアウトが異なる**ため、**ヘッダ行からの動的な列マップ構築**で差分を吸収する。

### 2.1 シート名の自動判別

変換対象シートは以下の優先順位で検出（最初にマッチしたもの）:
1. `支払い明細`（20日払い形式）
2. `振込明細`（5日払い形式）

`福通運賃明細` `MF変換` `変換MAP` は**除外**。

### 2.2 列マップ（ヘッダ行ベース）

2行目のヘッダ文字列で列位置を動的に特定する。必須キーは `送り先` / `請求額` / `送料相手` / `早払い`。

| ファイル | A | B | C | D | E | F | G | H | I | J |
|---|---|---|---|---|---|---|---|---|---|---|
| 20日払い（`支払い明細`） | (空) | 送り先 | 請求額 | 打込金額 | 振込金額 | 送料相手 | 値引 | **早払い** | 相殺 | 備考 |
| 5日払い（`振込明細`） | 仕入コード | 送り先 | 請求額 | 打込金額 | 振込金額 | 送料相手 | 自社 | 値引 | **早払い** | 相殺 |

- A列（仕入コード = 支払先コード）: 20日払いはヘッダ空でも数値が入る、5日払いはヘッダ明示
- G列の `自社` / `値引`, I列の `早払い` / `相殺` が**ファイル間で位置ずれ**するため、ヘッダ名ルックアップが必須

**送金日**: `E1`（両ファイル共通の固定位置）
**データ開始行**: 3行目から

### 2.3 仕訳対象外のメタ行スキップ

B列文字列の正規化完全一致 / 前方一致で除外（正規化 = 全角空白除去・trim）:
- 完全一致: `合計` / `小計` / `その他計` / `本社仕入 合計` / `請求額` / `打ち込み額`
- 前方一致: `20日払い振込手数料` / `5日払い振込手数料`
- または A・B・C 列すべて空
- 明細の**「合計」行**だけは特別扱い: スキップはするが、その行のF列（送料相手合計）・早払列合計をサマリー仕訳の生成元として保存

### 2.4 特殊サマリー仕訳

「合計」行から下記の2行を必ず生成する（値が 0 でも生成。既存運用に合わせる）。

| 項目 | 借方 | 貸方 | 摘要 |
|---|---|---|---|
| 振込手数料値引 | 資金複合 / 対象外 | 仕入値引・戻し高 / 物販事業部 / 課税仕入-返還等10% | `振込手数料値引／{D}日払い分` |
| 早払収益 | 資金複合 / 対象外 | 早払収益 / 物販事業部 / 非課税売上 | `早払収益／{D}日払い分` |

- `{D}` = 送金日(`E1`)の**日部分**（5 or 20）
- 金額は合計行の送料相手列 / 早払い列（列マップでヘッダ名参照）

### 2.5 無視する列

以下は仕訳には利用しない（プレビュー画面でも表示しない）:
- `打込金額` / `振込金額`（仕訳金額は`請求額`のみ使用）
- `自社` / `値引` / `相殺` / `備考`
- `福通運賃明細` シート（紙運用の参考資料）

---

## 3. 出力仕様（MFインポートCSV）

### フォーマット
- エンコーディング: **CP932（Shift_JIS）**（cashbook-import は UTF-8 BOM だが、MF買掛仕入の既存運用は CP932。過去CSVから検証済）
- 改行: **LF**（既存CSVに準拠。cashbookのCRLFとは異なる点に注意）
- 区切り: カンマ、囲み文字なし
- 金額後ろに半角スペース（`289027 ` のように出力。既存運用に合わせる）
- 日付書式: `YYYY/M/D`（ゼロパディングなし）

### 列構成（19列）
`取引No, 取引日, 借方勘定科目, 借方補助科目, 借方部門, 借方取引先, 借方税区分, 借方インボイス, 借方金額(円), 貸方勘定科目, 貸方補助科目, 貸方部門, 貸方取引先, 貸方税区分, 貸方インボイス, 貸方金額(円), 摘要, タグ, メモ`

### 行種別

| 種別 | 借方 | 貸方 | 備考 |
|---|---|---|---|
| 買掛金支払 | 買掛金/{MF補助科目}/対象外 | 資金複合/対象外 | 支払先コードあり |
| 運賃（仕入紐付） | 荷造運賃/物販事業部/課税仕入10% | 資金複合/対象外 | 送り先名固定ルール |
| 消耗品費 | 消耗品費/対象外 | 資金複合/対象外 | 〃 |
| 車両費 | 車両費/対象外 | 資金複合/対象外 | 〃 |
| 仕入高 | 仕入高/課税仕入10% | 資金複合/対象外 | 〃（20日払いセクション含む） |
| 振込手数料値引 | 資金複合/対象外 | 仕入値引・戻し高/物販事業部/課税仕入-返還等10% | サマリー行F列 |
| 早払収益 | 資金複合/対象外 | 早払収益/物販事業部/非課税売上 | サマリー行H列 |

---

## 4. マスタ設計

### 方針: **1本に統合した `m_payment_mf_rule`**

既存cashbookが `m_mf_journal_rule`（ルール）+ `m_mf_client_mapping`（得意先名マッピング）の2本構成である理由は、cashbookは「摘要キーワード→複数ルール」「得意先名は都度人名/法人名が入る」構造だから。
対して買掛仕入MFは「送り先名 = 1:1 で仕訳ルールが決まる」ため、**送り先名をキーにした単一テーブル**で十分かつ運用が簡単。補助科目カラムを nullable にして買掛金行（MF補助科目必須）と固定費行（不要）を兼用する。

### テーブル定義

```sql
CREATE TABLE m_payment_mf_rule (
    id                      SERIAL PRIMARY KEY,
    source_name             VARCHAR(200) NOT NULL,   -- 支払い明細B列（送り先名）
    payment_supplier_code   VARCHAR(20),             -- m_payment_supplier.payment_supplier_code（NULLなら固定費行）
    rule_kind               VARCHAR(20)  NOT NULL,   -- 'PAYABLE' | 'EXPENSE' | 'DIRECT_PURCHASE'

    debit_account           VARCHAR(50)  NOT NULL,   -- 例: 買掛金 / 荷造運賃 / 消耗品費 / 車両費 / 仕入高
    debit_sub_account       VARCHAR(100),            -- MF補助科目（PAYABLEは必須、EXPENSEはNULL）
    debit_department        VARCHAR(50),             -- 例: 物販事業部
    debit_tax_category      VARCHAR(30)  NOT NULL,   -- 例: 対象外 / 課税仕入 10%

    credit_account          VARCHAR(50)  NOT NULL DEFAULT '資金複合',
    credit_sub_account      VARCHAR(100),
    credit_department       VARCHAR(50),
    credit_tax_category     VARCHAR(30)  NOT NULL DEFAULT '対象外',

    summary_template        VARCHAR(200) NOT NULL,   -- 摘要。{sub_account} / {source_name} プレースホルダ対応
    tag                     VARCHAR(100),
    priority                INTEGER      NOT NULL DEFAULT 100,

    del_flg          VARCHAR(1) NOT NULL DEFAULT '0',
    add_date_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    add_user_no      INTEGER,
    modify_date_time TIMESTAMP,
    modify_user_no   INTEGER
);
CREATE INDEX idx_payment_mf_rule_source ON m_payment_mf_rule(source_name) WHERE del_flg = '0';
CREATE INDEX idx_payment_mf_rule_code   ON m_payment_mf_rule(payment_supplier_code) WHERE del_flg = '0';
```

**ルックアップ優先順位**:
1. `payment_supplier_code` 一致（買掛金行のマッチ。支払先コードは振込明細Aに数値、m_payment_supplierは文字列のため型揃え要）
2. `source_name` 正規化一致（全角空格・半角空格・㈱/（株）を正規化して比較）
3. どちらもNG → ??未登録（画面で登録フローへ）

### シード（初期データ）

過去CSV（20260120, 20260205, 20260220）から抽出した変換MAP 40行弱 +

```sql
-- 固定費行（payment_supplier_code NULL）
INSERT INTO m_payment_mf_rule (source_name, rule_kind, debit_account, debit_department, debit_tax_category, credit_account, credit_department, credit_tax_category, summary_template, tag) VALUES
('福山通運',                'EXPENSE', '荷造運賃', '物販事業部', '課税仕入 10%', '資金複合', NULL, '対象外', '{source_name}', '物販部 運賃'),
('サクマ運輸㈱',            'EXPENSE', '荷造運賃', '物販事業部', '課税仕入 10%', '資金複合', NULL, '対象外', '{source_name}', '物販部 運賃'),
('ティックトランスポート㈲', 'EXPENSE', '荷造運賃', NULL,        '課税仕入 10%', '資金複合', NULL, '対象外', '{source_name}', '物販部 運賃'),
('ヨハネ印刷㈱',             'EXPENSE', '仕入高',   NULL,        '課税仕入 10%', '資金複合', NULL, '対象外', '{source_name}', '物販部 仕入 '),
('リコージャパン㈱',         'EXPENSE', '消耗品費', NULL,        '対象外',       '資金複合', NULL, '対象外', '{source_name}', '物販部 事務用品費'),
('広島トヨペット㈱',         'EXPENSE', '車両費',   NULL,        '対象外',       '資金複合', NULL, '対象外', '{source_name}', '車両費'),
-- 以下 ナカガワ/中国エンゼル/ビバ/ワタキューセイモア/ハウスホールドジャパン/奈良半商店/シルバー化成工業所 等
;
```

買掛金行は支払先コードでルックアップするため、`payment_supplier_code` セット + `debit_account='買掛金'` + `debit_sub_account='MF正式名'` のルールを `m_payment_supplier` からJOINして初期登録（マイグレーション1回）。

---

## 5. 突合ロジック（買掛金一覧との）

### 5.1 取引月の決定ルール（重要: 仕様変更あり）

送金日(`E1`)の日付から、以下で買掛金一覧の `transaction_month` を決定:

**現行ルール（`PaymentMfImportService#deriveTransactionMonth`）**:
5日払い・20日払いともに **前月20日締め** に統一する。

```java
transactionMonth = transferDate.minusMonths(1).withDayOfMonth(20)
```

| 送金日 | 突合対象の取引月 |
|---|---|
| 2026/2/5 | 2026-01-20（前月20日締め分） |
| 2026/2/20 | 2026-01-20（前月20日締め分） |

理由: 20日払いの振込明細は「前月20日締め分」の支払いであり、当月20日締めの集計はまだ
確定していないため。これにより集計タイミングと突合タイミングが整合する。

（旧仕様では 20日払いは「当月20日締め分」と突合していたが、集計確定前に突合が走るケースが
あるため前月締めに統一した。）

### 5.2 突合処理

```
for each 買掛金行 in 支払い明細:
    payableSummary = find(t_accounts_payable_summary,
        shopNo=1, paymentSupplierCode=excelCode, transactionMonth=送金日の取引月)
    if not payableSummary:
        status = ?? '買掛金なし'
    else if abs(payableSummary.verified_amount - 請求額) <= 100円:
        status = ?? '一致'
    else:
        status = ?? '金額差異'
        diff = payableSummary.verified_amount - 請求額
```

100円閾値は既存 `SmilePaymentVerifier` と同じ扱い。固定費行（運賃等・20日払いセクションの仕入高行）は突合対象外で常に白表示。

### 5.3 行のセクション判定（統一ロジック）

20日払いファイルは本社仕入（買掛金）/ その他固定費 / 20日払い仕入高 の3セクションだが、**セクション境界を意識せず全行を統一ルックアップ**で処理する:

```
for each 明細行 (メタ行を除く):
    if 仕入コード(A列) が数値:
        ルール = m_payment_mf_rule.find_by_payment_supplier_code(code)
    else:
        ルール = m_payment_mf_rule.find_by_source_name_normalized(送り先名)

    if not ルール:
        status = ?? '未登録' / 仕訳なし
    else if ルール.rule_kind = 'PAYABLE':
        買掛金突合（§5.2）
    else:  # EXPENSE / DIRECT_PURCHASE
        突合スキップ・白表示
```

`rule_kind` は3値:
- `PAYABLE`: 買掛金仕訳（買掛金/補助科目/対象外 vs 資金複合/対象外）→ 突合対象
- `EXPENSE`: 費用計上（荷造運賃・消耗品費・車両費など）→ 突合対象外
- `DIRECT_PURCHASE`: 直接仕入高計上（20日払いセクションのワタキューセイモア等）→ 突合対象外

---

## 6. 変換履歴

```sql
CREATE TABLE t_payment_mf_import_history (
    id                SERIAL PRIMARY KEY,
    shop_no           INTEGER     NOT NULL,
    transfer_date     DATE        NOT NULL,      -- 送金日（E1）
    source_filename   VARCHAR(255) NOT NULL,
    csv_filename      VARCHAR(255) NOT NULL,
    row_count         INTEGER     NOT NULL,
    total_amount      BIGINT      NOT NULL,
    unmatched_count   INTEGER     NOT NULL,      -- ??件数
    diff_count        INTEGER     NOT NULL,      -- ??件数
    csv_body          BYTEA,                     -- 再ダウンロード用
    add_date_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    add_user_no       INTEGER
);
```

履歴画面は今フェーズでは**一覧表示のみ**（再ダウンロードリンク付き）。

---

## 7. バックエンド

### エンドポイント

| Method | Path | 用途 |
|---|---|---|
| POST | `/api/v1/finance/payment-mf-import/preview` | xlsx アップロード → プレビュー返却（`uploadId` 含む） |
| GET  | `/api/v1/finance/payment-mf-import/preview/{uploadId}` | プレビュー再取得（マスタ編集後） |
| POST | `/api/v1/finance/payment-mf-import/convert/{uploadId}` | CSV生成 + 履歴保存 → CSV返却 |
| POST | `/api/v1/finance/payment-mf-import/bulk-verify/{uploadId}` | 買掛金一括検証（`verified_amount` / `verifiedManually=true` をセット） |
| GET  | `/api/v1/finance/payment-mf-import/history` | 履歴一覧 |
| GET  | `/api/v1/finance/payment-mf-import/history/{id}/csv` | 履歴からCSV再ダウンロード |
| GET/POST/PUT/DELETE | `/api/v1/finance/payment-mf-rules` | マスタCRUD（cashbookのmf-rulesと同様） |
| POST | `/api/v1/finance/payment-mf/rules/backfill-codes?dryRun=true\|false` | 支払先コード自動補完（admin） |

#### `payment_supplier_code` 自動補完（backfill-codes）

PAYABLE ルールで `payment_supplier_code` が未設定のものに対し、`source_name` を正規化して
`m_payment_supplier.payment_supplier_name` と名寄せマッチし、コードを自動補完する。

正規化ルール:
- NFKC 正規化
- 「株式会社」「㈱」「（株）」を除去
- 「支店」などの営業所記号（[20] 等）を除去
- 全/半角空白を除去

`dryRun=true` でプレビュー（対象件数・マッチ内訳を返却）、`dryRun=false` で実適用。
画面の「支払先コード自動補完」ボタン（admin、`/finance/payment-mf-rules` ヘッダー）から起動。

### 正規化・マッチングユーティリティ

- `normalizePaymentSupplierCode`: Excel 側の仕入コード（2-3桁、例: `12`）を DB 形式（6桁ゼロ埋め、×100、例: `001200`）に正規化
- `deriveTransactionMonth`: 5日払い・20日払いともに前月20日締めに統一（§5.1）
- reconcileCode fallback: Excel 側にコードが無い（送り先名のみ）行でも、バックフィルで埋めたルール側のコードを使って `t_accounts_payable_summary` と突合可能

### クラス
- `PaymentMfImportController` ? API層
- `PaymentMfImportService` ? Excel解析 + ルール適用 + 突合 + CSV生成
- `PaymentMfRuleService` / `PaymentMfRuleController` ? マスタCRUD
- `MPaymentMfRule` / `TPaymentMfImportHistory` ? Entity
- `PaymentMfImportPreviewResponse` ? `{ uploadId, transferDate, rows: [{source, amount, rule?, status, matchedPayable?}], summary: {matched, diff, unmatched} }`

### uploadIdキャッシュ
cashbook-import と同じパターン: `ConcurrentHashMap<String, ParsedWorkbook>` + `@Scheduled` TTL 30分 + `enforceCacheLimit(100)`。

### 依存
- Excel解析: 既存 Apache POI（cashbook-import と共通）
- CSV出力: `OutputStreamWriter(Charset.forName("MS932"))` + 明示 `\n` 改行

---

## 8. フロントエンド

### 画面構成

**`/finance/payment-mf-import`** ? 3ステップ（cashbook-import踏襲）
1. **Step1 Upload**: xlsxドロップ + アップロード
2. **Step2 Preview**:
   - ヘッダ: 送金日、件数サマリ（??/??/??）、合計金額
   - テーブル: 送り先 / 支払先コード / 請求額 / 買掛金額 / 差額 / 仕訳 / ステータス
   - ??行は「マスタ登録」ボタン → ダイアログで `m_payment_mf_rule` 登録
   - ??行は買掛金一覧画面へのリンク（`/finance/accounts-payable?supplierNo=X&transactionMonth=Y`）
   - マスタ更新後は同 `uploadId` でプレビュー再取得
3. **Step3 Download**: CSVダウンロードボタン

#### BulkVerifyDialog（振込明細で一括検証）

プレビュー後、買掛金サマリへ一括検証する確認ダイアログ。強化機能:

- **未登録セクション**: 右上に「マスタマスタを確認」ボタン（`/finance/payment-mf-rules` へ遷移）
- 各未登録行の「マスタで検索」リンク: `?q=<送り先名>` で検索語プリフィル
- **買掛金なし行一覧**: 赤色ボックス、行ごとにコード・送り先名・金額を表示
- 実行時: `POST /api/v1/finance/payment-mf-import/bulk-verify/{uploadId}`
  - `PaymentMfImportService#applyVerification` が `t_accounts_payable_summary` に以下をセット
    - `verified_amount = Excel請求額`
    - `verified_manually = true`
    - `verification_note = 「振込明細 yyyy-MM-dd 一括検証」` 等
  - 税率別複数行には同一 `verified_amount` を同期セット
  - 一致判定（100円閾値）→ `verification_result`, `payment_difference` を更新

**`/finance/payment-mf-rules`** ? マスタCRUD
cashbook の `/finance/mf-journal-rules` のUIを流用。強化機能:

- **ルール複製ボタン**: 各行に Copy アイコン。既存ルールを雛形に新規ルール作成
- **「支払先コード自動補完」ボタン**（admin、ヘッダー）: `POST /payment-mf/rules/backfill-codes`
  - `dryRun=true` でプレビュー（対象件数・マッチ内訳）
  - 一括適用で `dryRun=false`
- 会社名正規化検索（株式会社 / ㈱ / 空白ゆれを吸収して比較）
- `?q=<検索語>` URLパラメータで検索語プリフィル対応（未登録行リンクから誘導）

**`/finance/payment-mf-history`** ? 変換履歴
日付降順一覧 + 再ダウンロード（後回し可）。

### サイドバー
「見積・財務」グループに「買掛仕入MF変換」「買掛仕入MFルール」追加。

---

## 9. テスト計画（概要）

- **ゴールデンマスタテスト**: 過去の振込み明細xlsx 3本以上（20260120 / 20260205 / 20260220）と対応CSV を固定ディレクトリに置き、Java版で再生成してバイト等価（CP932+LF）を検証。
- **ユニット**: Excel解析、メタ行スキップ、送り先名正規化、ルール適用優先順、突合判定（100円閾値・境界）
- **E2E**: アップロード→プレビュー→マスタ登録→CSVダウンロード→履歴確認

---

## 10. 実装順

1. マスタテーブル作成 + シード投入（過去CSVから抽出）
2. Entity / Repository / Service（Excel解析 + ルール適用）
3. Controller + DTO
4. ゴールデンマスタテスト（CI化）
5. Frontend（upload → preview → download）
6. 買掛金突合ロジック + ハイライト
7. マスタCRUD画面
8. 変換履歴

---

## 11. 決定事項 & 残論点

### 確定
- **シート構造差分**: 5日払い（`振込明細`）/ 20日払い（`支払い明細`）は**列レイアウトがずれる**（G列の自社/値引、H/I列の早払位置など）。→ ヘッダ名での列マップ動的構築で吸収（§2.2）。
- **取引月決定**: 5日払い・20日払いともに **前月20日締め分** と突合（§5.1, 仕様変更済み）。
- **合計行の判定**: B列文字列 `合計` でヒット。位置は固定しない（§2.3）。
- **無視する列**: 自社 / 値引 / 相殺 / 備考 / 打込金額 / 振込金額（§2.5）。
- **シード出典**: MVP時点で **`変換MAP` シート（56件想定）全件**を `m_payment_mf_rule` に投入（PAYABLE扱い）。加えて過去3ヶ月CSVから固定費行（EXPENSE/DIRECT_PURCHASE）約15件を抽出して追加。
- **一括検証の永続化**: `t_accounts_payable_summary.verified_amount` + `verified_manually=true` に書込。税率別複数行には同一値を同期。
- **支払先コード正規化**: Excel 2-3桁コード → DB 6桁ゼロ埋め（×100）に `normalizePaymentSupplierCode` で変換。
- **reconcile fallback**: Excel 側にコードが無い行でも、バックフィル済ルールのコードで突合可能。
- **バックフィル運用**: `m_payment_mf_rule` PAYABLE 74件に `payment_supplier_code` を自動補完済み（2026-04-15）。

### 残論点（実装中に詰める）
- `m_payment_supplier.payment_supplier_code` のDB内実型（VARCHAR/INT）確認 → Excel A列との照合時の型変換。
- 過去の `変換MAP` シートで値が `None` の行（例: `中国鉄管継手㈱`）の扱い → `m_payment_mf_rule` に登録しない or 登録してMF補助科目NULL運用にするか。
- CSVファイル名規約 `買掛仕入MFインポートファイル_{yyyymmdd}.csv`（送金日ベース）で固定。
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3988ms:
# 検証済み買掛金CSV出力 ? 補助行（EXPENSE/SUMMARY/DIRECT_PURCHASE）の永続化設計

作成日: 2026-04-15
ステータス: **Approved (2026-04-15)**
関連: `design-payment-mf-import.md`, `design-accounts-payable.md`
対象機能: `/finance/accounts-payable` の「検証済みCSV出力」拡張

## 承認事項（2026-04-15 ユーザー確認済み）
1. タブ順序: 1=買掛金一覧、2=MF補助行
2. タブ名横に件数バッジ表示（例: `MF補助行 (10)`）
3. 補助行は表示のみ（編集・削除なし）
4. 空状態文言: §6.3 末尾の案内文を採用
5. 補助行タブには transactionMonth/取引日列は表示しない（CSV 出力時のみ使用）

---

## 1. 背景・目的

### 既存の「検証済みCSV出力」機能（v1）の制約
`t_accounts_payable_summary` の verified 行から MF仕訳CSVを直接生成する機能を実装したが、**PAYABLE（買掛金）行のみ**しか出力できない。実運用の MF インポートCSVには以下の **補助行** が含まれており、これらは振込明細Excelからしか取得できないため Excel 再アップロードが必須になっている。

### 補助行の種別と取得元
| 行種別 | 例 | 出処（Excel 内） | DB 保持 |
|---|---|---|---|
| **PAYABLE** | 買掛金/竹の子の里㈱ → 資金複合 | 明細行（合計行より前） | ? `t_accounts_payable_summary` |
| **EXPENSE** | 荷造運賃/福山通運、消耗品費/リコージャパン㈱、車両費/広島トヨペット㈱ | 明細行（合計行より前、ルール=`EXPENSE`） | ? |
| **DIRECT_PURCHASE** | 仕入高/ワタキューセイモア（20日払いのみ） | 合計行**後**のセクション | ? |
| **SUMMARY 振込手数料値引** | 資金複合→仕入値引・戻し高 | 合計行 F列（送料相手合計） | ? |
| **SUMMARY 早払収益** | 資金複合→早払収益 | 合計行 H列（早払合計） | ? |

### ゴール
**5日払いExcel と 20日払いExcel の両方を「振込明細で一括検証」した後**、検証済みCSV出力 1 操作で **全行種をマージした統合CSV** を出力できるようにする。これにより:
- 経理担当者が 5日 と 20日 の Excel を別々に CSV 化して手動結合する作業がゼロになる
- 既存の手動結合工程で発生していた漏れ・重複ミスを排除できる
- マスタ修正（payment_mf_rule の借方/貸方変更）後に**Excel再アップロード不要で再生成**可能

---

## 2. 現状フロー vs 設計後フロー

### 現状（v1）
```
[5日Excel] → applyVerification → t_accounts_payable_summary 更新（PAYABLE のみ）
[5日Excel] → convert            → CSV (5日分: PAYABLE+EXPENSE+SUMMARY+DIRECT_PURCHASE) ? 履歴保存
[20日Excel] → applyVerification → t_accounts_payable_summary 更新（PAYABLE のみ）
[20日Excel] → convert           → CSV (20日分) ? 履歴保存
                ↓ 経理担当が手動マージ
        買掛仕入MFインポートファイル_{締め日}.csv
```

検証済みCSV出力（v1）:
```
取引月指定 → t_accounts_payable_summary（PAYABLE のみ）→ CSV (PAYABLE のみ)
```

### 設計後（v2）
```
[5日Excel] → applyVerification → t_accounts_payable_summary 更新（PAYABLE）
                              + t_payment_mf_aux_row 更新（EXPENSE/SUMMARY/DIRECT_PURCHASE）
                              + t_payment_mf_import_history 履歴保存（既存）
[20日Excel] → applyVerification → 同上（5日分と並存して保存）
```

検証済みCSV出力（v2）:
```
取引月指定 → t_accounts_payable_summary（PAYABLE）
          + t_payment_mf_aux_row（EXPENSE/SUMMARY/DIRECT_PURCHASE × 5日 + 20日）
          → 統合CSV（手動マージ不要）
```

---

## 3. データモデル

### 3.1 新規テーブル `t_payment_mf_aux_row`

```sql
CREATE TABLE t_payment_mf_aux_row (
    aux_row_id          BIGSERIAL PRIMARY KEY,
    shop_no             INTEGER NOT NULL DEFAULT 1,           -- FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO
    transaction_month   DATE    NOT NULL,                     -- 締め日 (前月20日)。CSV 取引日列
    transfer_date       DATE    NOT NULL,                     -- 出処Excelの送金日 (5日 or 20日)
    rule_kind           VARCHAR(20) NOT NULL,                 -- 'EXPENSE' / 'SUMMARY' / 'DIRECT_PURCHASE'
    sequence_no         INTEGER NOT NULL,                     -- Excel内の出現順 (CSV出力順序維持)
    source_name         VARCHAR(255) NOT NULL,                -- Excel B列の送り先名 (SUMMARYは "振込手数料値引" 等)
    payment_supplier_code VARCHAR(20),                        -- Excel A列 (EXPENSEで参照、SUMMARYはNULL)
    amount              NUMERIC NOT NULL,
    debit_account       VARCHAR(50)  NOT NULL,
    debit_sub_account   VARCHAR(50),
    debit_department    VARCHAR(50),
    debit_tax           VARCHAR(30)  NOT NULL,
    credit_account      VARCHAR(50)  NOT NULL,
    credit_sub_account  VARCHAR(50),
    credit_department   VARCHAR(50),
    credit_tax          VARCHAR(30)  NOT NULL,
    summary             VARCHAR(255),                         -- 摘要欄
    tag                 VARCHAR(50),
    source_filename     VARCHAR(255),                         -- 出処 Excel ファイル名 (トレーサビリティ用)
    add_date_time       TIMESTAMP    NOT NULL,
    add_user_no         INTEGER,
    modify_date_time    TIMESTAMP,
    modify_user_no      INTEGER,

    -- 同一 (shop_no, transaction_month, transfer_date) で再アップロードされたら洗い替えする
    -- 物理キーは aux_row_id だが、再アップロード時の重複を防ぐため
    -- (shop_no, transaction_month, transfer_date) で DELETE → INSERT
    CONSTRAINT chk_rule_kind CHECK (rule_kind IN ('EXPENSE','SUMMARY','DIRECT_PURCHASE'))
);

CREATE INDEX idx_payment_mf_aux_tx_month
    ON t_payment_mf_aux_row(shop_no, transaction_month);

CREATE INDEX idx_payment_mf_aux_transfer
    ON t_payment_mf_aux_row(shop_no, transaction_month, transfer_date);
```

### 3.2 設計上の判断ポイント

#### Q1. なぜ `t_accounts_payable_summary` に統合せず別テーブルか？
- `t_accounts_payable_summary` は **仕入明細から集計された買掛金** が本質。EXPENSE/SUMMARY/DIRECT_PURCHASE はそもそも仕入明細から導出されない（経費・補正項目）
- 集計バッチ（`AccountsPayableSummaryCalculator`）の責務と混在する
- PK が (shop_no, supplier_no, transaction_month, tax_rate) で、SUMMARY 行（supplier 概念がない）には合わない

#### Q2. なぜ `del_flg` を使わず物理 DELETE → INSERT で洗い替え？
- 補助行は「Excel スナップショットの再現」用途で、過去履歴の保全は `t_payment_mf_import_history.csv_body` で別管理
- 論理削除すると洗い替え時のクエリが複雑化（`del_flg='0'` フィルタ + 重複対応）
- 同一送金日Excel を再アップロードしたら最新の値で完全置換、というシンプルな運用

#### Q3. なぜ verified_manually 相当のフラグを持たないか？
- 補助行は **Excel 由来のみ** で、UI から手入力する経路を作らない（v2 スコープ外）
- v3 で必要になれば後付けで `is_manual` 等を追加できる

#### Q4. (shop_no, transaction_month, transfer_date) で重複防止する理由
- 同じ送金日の Excel を再アップロードしたら：旧データ削除 → 新データ挿入で完全置換
- 5日 と 20日 は別 transfer_date で並存（両方が aux_row として保存される）
- v3 で「片方の Excel だけ修正したい」需要が出たときに、もう片方を巻き込まずに更新可能

### 3.3 Entity / Repository

```java
@Entity @Data @Builder
@Table(name = "t_payment_mf_aux_row")
public class TPaymentMfAuxRow implements IEntity {
    @Id @GeneratedValue(strategy = IDENTITY) @Column(name = "aux_row_id")
    private Long auxRowId;

    private Integer shopNo;
    private LocalDate transactionMonth;
    private LocalDate transferDate;
    private String ruleKind;
    private Integer sequenceNo;
    private String sourceName;
    private String paymentSupplierCode;
    private BigDecimal amount;
    private String debitAccount;
    private String debitSubAccount;
    private String debitDepartment;
    private String debitTax;
    private String creditAccount;
    private String creditSubAccount;
    private String creditDepartment;
    private String creditTax;
    private String summary;
    private String tag;
    private String sourceFilename;
    private LocalDateTime addDateTime;
    private Integer addUserNo;
    private LocalDateTime modifyDateTime;
    private Integer modifyUserNo;

    // del_flg 持たない（物理削除運用）
    @Override public String getDelFlg() { return "0"; }
    @Override public void setDelFlg(String s) { /* no-op */ }
}
```

```java
public interface TPaymentMfAuxRowRepository extends JpaRepository<TPaymentMfAuxRow, Long> {
    @Modifying
    @Query("DELETE FROM TPaymentMfAuxRow r WHERE r.shopNo = :shopNo " +
           "AND r.transactionMonth = :txMonth AND r.transferDate = :transferDate")
    int deleteByShopNoAndTransactionMonthAndTransferDate(
            @Param("shopNo") Integer shopNo,
            @Param("txMonth") LocalDate transactionMonth,
            @Param("transferDate") LocalDate transferDate);

    // 検証済みCSV出力用: 取引月の全送金日分を出力順で取得
    List<TPaymentMfAuxRow> findByShopNoAndTransactionMonthOrderByTransferDateAscSequenceNoAsc(
            Integer shopNo, LocalDate transactionMonth);
}
```

---

## 4. 処理フロー

### 4.1 書き込み: `applyVerification` 拡張

`PaymentMfImportService.applyVerification(uploadId, userNo)` を以下のように拡張:

```java
@Transactional
public VerifyResult applyVerification(String uploadId, Integer userNo) {
    CachedUpload cached = getCached(uploadId);
    LocalDate txMonth = deriveTransactionMonth(cached.getTransferDate());
    LocalDate transferDate = cached.getTransferDate();

    // 1. 既存処理: PAYABLE → t_accounts_payable_summary 更新（変更なし）
    PaymentMfPreviewResponse preview = buildPreview(uploadId, cached);
    for (ParsedEntry e : cached.getEntries()) {
        // ... PAYABLE 反映処理 ...
    }

    // 2. NEW: 補助行（EXPENSE/SUMMARY/DIRECT_PURCHASE）→ t_payment_mf_aux_row 洗い替え
    auxRepository.deleteByShopNoAndTransactionMonthAndTransferDate(
            ACCOUNTS_PAYABLE_SHOP_NO, txMonth, transferDate);

    int seq = 0;
    for (PaymentMfPreviewRow row : preview.getRows()) {
        if ("PAYABLE".equals(row.getRuleKind())) continue;        // PAYABLE は別管理
        if ("UNREGISTERED".equals(row.getErrorType())) continue;  // ルール未登録はスキップ

        auxRepository.save(TPaymentMfAuxRow.builder()
            .shopNo(ACCOUNTS_PAYABLE_SHOP_NO)
            .transactionMonth(txMonth)
            .transferDate(transferDate)
            .ruleKind(row.getRuleKind())   // EXPENSE / SUMMARY / DIRECT_PURCHASE
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
            .addDateTime(LocalDateTime.now())
            .addUserNo(userNo)
            .build());
    }

    return ...;
}
```

**重要な不変条件**:
- `applyVerification` は同一トランザクション内で PAYABLE 反映と aux 洗い替えを行う（部分失敗で aux だけ更新されることはない）
- 補助行の DELETE → INSERT は `(shop_no, transaction_month, transfer_date)` 単位。5日Excel をアップロードしても 20日分は影響を受けない

### 4.2 読み出し: `exportVerifiedCsv` 拡張

```java
public VerifiedExportResult exportVerifiedCsv(LocalDate transactionMonth, Integer userNo) {
    // 1. 既存: PAYABLE 行を t_accounts_payable_summary から構築
    List<PaymentMfPreviewRow> payableRows = buildPayableRowsFromSummary(transactionMonth);

    // 2. NEW: 補助行を t_payment_mf_aux_row から取得、PaymentMfPreviewRow に変換
    List<TPaymentMfAuxRow> auxRows = auxRepository
        .findByShopNoAndTransactionMonthOrderByTransferDateAscSequenceNoAsc(
            ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth);
    List<PaymentMfPreviewRow> auxConverted = auxRows.stream()
        .map(this::auxToPreviewRow).toList();

    // 3. 結合: PAYABLE → AUX (5日分EXPENSE → 5日分SUMMARY → 20日分EXPENSE → 20日分SUMMARY → DIRECT_PURCHASE)
    List<PaymentMfPreviewRow> all = new ArrayList<>();
    all.addAll(payableRows);
    all.addAll(auxConverted);

    byte[] csv = toCsvBytes(all, transactionMonth);
    saveVerifiedExportHistory(transactionMonth, all.size(), totalAmount, csv, userNo);

    return VerifiedExportResult.builder()
        .csv(csv)
        .rowCount(all.size())
        .auxRowCount(auxConverted.size())
        .payableRowCount(payableRows.size())
        ...
        .build();
}
```

### 4.3 CSV 出力順序

既存運用 CSV と完全一致させるため:

1. **PAYABLE**（買掛金）? `t_accounts_payable_summary` の supplier_no 順
2. **EXPENSE**（5日分）→ **EXPENSE**（20日分） ? Excel 内の出現順 (`sequence_no`)
3. **SUMMARY 振込手数料値引**（5日分）→ **SUMMARY 早払収益**（5日分）
4. **SUMMARY**（20日分） ? 同上
5. **DIRECT_PURCHASE**（20日分） ? Excel 末尾セクション

`transferDate ASC, sequence_no ASC` ソートで自然と上記順序になる。EXPENSE と SUMMARY の混在順序は Excel 内出現順を保つ。

### 4.4 取引日列の扱い

CSV の取引日列は **全行 `transactionMonth`（前月20日）固定**。
- v1 で既存 Excel フローも統一済み → 補助行も同じ扱いで矛盾なし

---

## 5. API 設計

### 5.1 既存エンドポイント変更なし
- `POST /finance/payment-mf/verify/{uploadId}` ? 内部処理で aux 行も保存するが、レスポンス互換維持

### 5.2 既存 `GET /finance/payment-mf/export-verified` レスポンス拡張

```jsonc
// 既存（HTTPヘッダ）
X-Row-Count: 30           // 全行数
X-Total-Amount: 1234567   // 全行合計

// NEW（追加ヘッダ）
X-Payable-Count: 24       // PAYABLE 行数
X-Aux-Count: 6            // 補助行数（EXPENSE+SUMMARY+DIRECT_PURCHASE）
X-Missing-Aux-Months: ""  // 補助行が見つからない transferDate (例: "5日Excelが未取込")
```

### 5.3 補助行一覧API（v2 で実装する）

タブ切り替え UI（§6.2）から呼び出される。

```
GET /finance/payment-mf/aux-rows?transactionMonth=2026-01-20
→ 200 OK
[
  {
    "auxRowId": 123,
    "transferDate": "2026-02-05",
    "ruleKind": "EXPENSE",
    "sequenceNo": 0,
    "sourceName": "福山通運",
    "paymentSupplierCode": "054200",
    "amount": 110506,
    "debitAccount": "荷造運賃",
    "debitSubAccount": null,
    "debitDepartment": "物販事業部",
    "debitTax": "課税仕入 10%",
    "creditAccount": "資金複合",
    ...
    "summary": "物販部 運賃",
    "tag": null,
    "sourceFilename": "振込み明細08-2-5.xlsx"
  },
  ...
]
```

ソート: `transferDate ASC, sequenceNo ASC`（CSV出力順序と一致）

### 5.4 補助行削除API（v3 候補）
- `DELETE /finance/payment-mf/aux-rows?transactionMonth=...&transferDate=...` ? 手動削除
- v2 では不要（再アップロードで洗い替えされるため）

---

## 6. UI 変更

### 6.1 `/finance/accounts-payable` ページのタブ切り替え化（メイン変更）

**ご要望**: DB 保存だけだと中身が見えないので、買掛金一覧と同じページに補助行を別タブで可視化する。

**現状（v1）の構成**:
```
[/finance/accounts-payable]
  PageHeader (検索条件 + アクションボタン群)
  ├ 振込明細で一括検証 ボタン
  ├ 検証済みCSV出力 ボタン (admin)
  ├ 仕入明細取込/再集計/再検証(SMILE) ボタン (admin)
  サマリアラート (未検証/不一致件数)
  DataTable (買掛金一覧 = PAYABLE)
```

**変更後（v2）の構成**:
```
[/finance/accounts-payable]
  PageHeader (検索条件 + アクションボタン群) ? 既存と同じ
  サマリアラート ? 既存と同じ

  ┌─ Tabs (取引月・ショップで両タブ共通フィルタ) ─────────┐
  │ [買掛金一覧]  [MF補助行 (n件)]                        │
  ├──────────────────────────────────────────────────────┤
  │                                                       │
  │  選択中のタブに応じた DataTable                       │
  │                                                       │
  └──────────────────────────────────────────────────────┘
```

### 6.2 タブ1: 買掛金一覧（既存と同じ内容）

現状の DataTable をそのまま表示。既存機能（行クリック→検証ダイアログ、MF出力 Switch、仕入先名→/purchases drilldown 等）に変更なし。

タブラベル横に件数バッジ:
```
[買掛金一覧 (24)]   24 = 表示中ページの全件数 or 取引月の総件数
```

### 6.3 タブ2: MF補助行（新規）

`GET /finance/payment-mf/aux-rows?transactionMonth=...` の結果を DataTable で表示:

| 送金日 | 種別 | 送り先 | 借方 | 借方部門 | 借方税区分 | 金額 | 貸方 | 貸方税区分 | 摘要 | ソース |
|---|---|---|---|---|---|---|---|---|---|---|
| 2026-02-05 | EXPENSE | 福山通運 | 荷造運賃 | 物販事業部 | 課税仕入 10% | \110,506 | 資金複合 | 対象外 | 物販部 運賃 | 振込み明細08-2-5.xlsx |
| 2026-02-05 | EXPENSE | サクマ運輸㈱ | 荷造運賃 | 物販事業部 | 課税仕入 10% | \74,294 | 資金複合 | 対象外 | 物販部 運賃 | 〃 |
| 2026-02-05 | SUMMARY | 振込手数料値引 | 資金複合 | - | 対象外 | \17,160 | 仕入値引・戻し高 | 課税仕入-返還等 10% | 振込手数料値引／5日払い分 | 〃 |
| 2026-02-05 | SUMMARY | 早払収益 | 資金複合 | - | 対象外 | \32,614 | 早払収益 | 非課税売上 | 早払収益／5日払い分 | 〃 |
| 2026-02-20 | EXPENSE | 広島トヨペット㈱ | 車両費 | - | 対象外 | \159,489 | 資金複合 | 対象外 | 車両費 | 振込み明細08-2-20.xlsx |
| 2026-02-20 | DIRECT_PURCHASE | ワタキューセイモア | 仕入高 | - | 課税仕入 10% | \250,000 | 資金複合 | 対象外 | ワタキューセイモア | 〃 |
| ... |

**特徴**:
- 種別は色付き Badge（EXPENSE=青、SUMMARY=黄、DIRECT_PURCHASE=紫）
- 送金日（5日 / 20日）でグルーピング表示（DataTable のソート維持）
- ソース列でどの Excel 由来か追跡可能（誤取込時のリカバリ）
- 件数 0 件時の空状態:
  ```
  ?? この取引月の補助行は登録されていません。
  振込明細Excel を「振込明細で一括検証」からアップロードすると、
  EXPENSE/SUMMARY/DIRECT_PURCHASE 行が自動的に保存されます。
  ```

**操作**:
- v2: 表示のみ（編集・削除 UI は無し）
- v3: 不要な行の手動削除、ルール再適用ボタン等を検討

### 6.4 検証済みCSV出力ダイアログの拡張

タブ表示で内訳が見えるためダイアログは軽量のままでも OK。ただし「DLボタン押す前に件数確認したい」需要に応えるため、サマリ表示は追加:

```
[ダイアログ: 検証済みCSV出力]
  対象取引月: 2026-01-20

  内訳:
    買掛金 (一致 + MF出力ON)        24件
    補助行 (5日Excel由来)            5件 (EXPENSE 3 + SUMMARY 2)
    補助行 (20日Excel由来)           5件 (EXPENSE 2 + SUMMARY 2 + DIRECT_PURCHASE 1)
    ----
    CSV 行数合計                    34件

  ?? 5日払いExcel の補助行が見つかりません
  → 「振込明細で一括検証」から 5日払い Excel をアップロードしてください
  ※警告は片方/両方のExcelが未取込の場合に表示

  [キャンセル]  [CSVダウンロード]
```

### 6.5 タブ実装方針

- shadcn/ui の `Tabs` コンポーネント (`@/components/ui/tabs`) を使用（既に使用例あり）
- タブ状態は URL クエリパラメータ `?tab=payable` or `?tab=aux` で永続化（リロード耐性）
- 取引月・ショップ選択は両タブで共通の検索条件として保持
- 補助行タブは「取引月のみ」をフィルタ（shop_no=1 固定なのでショップ選択は無視）

### 6.6 補助行プレビュー API（CSV ダイアログ用、追加）

```
GET /finance/payment-mf/export-verified/preview?transactionMonth=...
→ {
    payableCount: 24,
    payableTotal: 1234567,
    auxBreakdown: [
      { transferDate: "2026-02-05", ruleKind: "EXPENSE",  count: 3 },
      { transferDate: "2026-02-05", ruleKind: "SUMMARY",  count: 2 },
      { transferDate: "2026-02-20", ruleKind: "EXPENSE",  count: 2 },
      { transferDate: "2026-02-20", ruleKind: "SUMMARY",  count: 2 },
      { transferDate: "2026-02-20", ruleKind: "DIRECT_PURCHASE", count: 1 },
    ],
    warnings: ["5日払い Excel(2026-02-05) の補助行が見つかりません"]
  }
```

警告判定ロジック:
- 取引月の transferDate 範囲 = `transactionMonth + 1ヶ月` の 5日 と 20日
- 補助行に該当 transferDate が無い → 警告

実装優先度: 中（先にタブ表示と CSV 出力を実装し、プレビュー API は後付けでも可）

---

## 7. テスト方針

### 7.1 ユニットテスト（新規）

`PaymentMfImportServiceAuxRowTest`:
- T1: applyVerification 後 aux テーブルに EXPENSE/SUMMARY/DIRECT_PURCHASE が保存される
- T2: 同一 (txMonth, transferDate) で再 applyVerification → 古い aux 行が削除され新規挿入のみ残る
- T3: 5日 Excel 後に 20日 Excel を applyVerification → 5日分は維持、20日分が追加
- T4: PAYABLE 行は aux テーブルに入らない（責務分離）
- T5: UNREGISTERED 行は aux に入らない（CSV に出ないため）

### 7.2 結合テスト（新規）

`VerifiedCsvExportIntegrationTest`:
- I1: 5日Excel + 20日Excel 両方 applyVerification → 検証済みCSV出力 → 行数・順序・金額が手動結合 CSV と一致
- I2: 5日Excel のみ applyVerification → 検証済みCSV出力 → PAYABLE + 5日分補助行のみ、警告ヘッダに「20日Excel未取込」

### 7.3 既存ゴールデンマスタテスト
- `PaymentMfImportServiceGoldenMasterTest`: 影響なし（applyVerification を呼ばず convert のみ実施）
- 念のため aux Repository を Mock 注入

### 7.4 マイグレーションテスト
- 既存 t_accounts_payable_summary に検証済み行があるが aux テーブルが空 → 検証済みCSV出力 → PAYABLE のみ出力（v1 互換動作）
- → 既存ユーザは Excel を再 applyVerification するまで補助行が付与されない（明示）

---

## 8. 移行・互換性

### 8.1 DB マイグレーション
1. `create_payment_mf_aux_row.sql` ? 新規テーブル + インデックス作成
2. 既存データへの遡及書き込みは **しない**（過去履歴の補助行を再現できないため、再アップロードしない限り PAYABLE のみ）

### 8.2 既存機能との互換性
| 機能 | v1 挙動 | v2 挙動 |
|---|---|---|
| `convert` (Excel→CSV) | 全行種を含む CSV | 変更なし |
| `applyVerification` | PAYABLE のみ DB 反映 | PAYABLE + aux 行を DB 反映 |
| `exportVerifiedCsv` | PAYABLE のみ CSV | PAYABLE + aux 行を結合 CSV |
| 履歴ダウンロード | 過去 CSV を再DL | 変更なし |

### 8.3 ロールバック手順
- aux テーブル DROP のみ
- `applyVerification` のコード差分を revert
- `exportVerifiedCsv` の差分を revert
- 既存 `t_accounts_payable_summary` には影響なし

---

## 9. 影響範囲（修正対象ファイル一覧）

### Backend
- **新規**:
  - `backend/src/main/resources/sql/create_payment_mf_aux_row.sql`
  - `backend/src/main/java/jp/co/oda32/domain/model/finance/TPaymentMfAuxRow.java`
  - `backend/src/main/java/jp/co/oda32/domain/repository/finance/TPaymentMfAuxRowRepository.java`
- **変更**:
  - `PaymentMfImportService.java`
    - `applyVerification`: aux 行洗い替え追加
    - `exportVerifiedCsv`: aux 行マージ追加
    - `auxToPreviewRow` private 変換ヘルパ追加
- **テスト**:
  - `PaymentMfImportServiceAuxRowTest`（新規）
  - `VerifiedCsvExportIntegrationTest`（新規）

### Frontend
- **変更**:
  - `components/pages/finance/accounts-payable.tsx`
    - **タブ切り替え追加**（Tabs コンポーネント導入、タブ1=既存買掛金一覧、タブ2=新規 MF補助行）
    - `?tab=...` URL クエリでタブ状態永続化
    - `VerifiedCsvExportDialog`: 内訳プレビュー表示追加
    - 警告メッセージ（補助行欠落時）
  - `types/payment-mf.ts`
    - `PaymentMfAuxRow` 型定義追加
    - `VerifiedExportPreview` 型定義追加（プレビュー API レスポンス）
- **新規**:
  - `components/pages/finance/PaymentMfAuxRowsTable.tsx`（タブ2 の中身。DataTable + 種別Badge）
- **任意（UX 向上）**:
  - 補助行が空のときの空状態コンポーネント

---

## 10. 未確定事項・今後の検討

### 10.1 補助行を手入力で追加する経路は v2 で作るか
- **不要**（Excel 経由が現実的、UI で直接編集する需要は薄い）
- v3 で「ルール変更後に再生成」需要が出たときに検討

### 10.2 取引月をまたぐ補助行
- 通常運用では補助行は (締め日 = 20日) と 1:1 だが、誤って違う送金日の Excel を別取引月扱いで applyVerification されたら？
- → `deriveTransactionMonth(transferDate)` で締め日が一意に決まるので、Excel 内容が同じなら矛盾は起きない
- → 異常系として「予期しない transferDate がある場合の警告」を出すかは実装時判断

### 10.3 履歴テーブルとの関係
- `t_payment_mf_import_history` には Excel 取込時の CSV body が保存される（既存）
- 検証済みCSV出力時も「verified-export_*」名で履歴に残す（v1 で実装済）
- aux 行から再構成した CSV と Excel 由来 CSV が並存する状態になるが、ファイル名で識別可能

### 10.4 ルールマスタ修正の反映
- `m_payment_mf_rule` を変更しても、既に保存済みの `t_payment_mf_aux_row` には反映されない（スナップショット保存のため）
- 反映したい場合: 振込明細 Excel を再 applyVerification する
- これは仕様として明記し、UI 側で「補助行は Excel 取込時点のスナップショット」と注記

### 10.5 5日払い・20日払いの整合性チェック
- 5日Excel と 20日Excel の transferDate が同じ取引月に紐づくこと（締め日が同じ）を Insert 時に検証するか？
- 厳密にやるなら applyVerification の引数として「期待 transactionMonth」を要求
- 緩く運用するなら警告ログのみ
- → v2 では緩く（警告ログ）でスタート、誤検知が出たら厳密化

---

## 11. 実装サイズ見積もり

| カテゴリ | 工数感 |
|---|---|
| DB マイグレーション + Entity + Repository | S |
| Service `applyVerification` 拡張 | S |
| Service `exportVerifiedCsv` 拡張 | M |
| 補助行一覧API (`GET /aux-rows`) | S |
| 補助行プレビュー API (`GET /export-verified/preview`) | S |
| ユニットテスト 5本 | M |
| 結合テスト 2本 | M |
| Frontend タブ切り替え + 補助行 DataTable | M |
| Frontend ダイアログ内訳プレビュー | S |
| **合計** | **M?L (2?3日相当)** |

---

## 12. レビュー観点

レビュー時に特に確認していただきたい点:

1. **テーブル名/カラム名**: `t_payment_mf_aux_row` で良いか。命名規約に沿うか
2. **物理削除運用**: 論理削除ではなく洗い替え方式で問題ないか
3. **CSV 出力順序**: §4.3 の順序は既存運用 CSV と一致するか
4. **補助行プレビュー API**: 必須か任意か
5. **警告表示**: 5日/20日 片方の Excel が未取込の場合の挙動
6. **ルールマスタ修正の反映方針**: §10.4 の運用ルール
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
既存レビューで出ていない論点に絞るため、実装から「暗黙の業務ルール」「監査証跡」「フォーマット検知」の根拠を拾っています。特にパーサ、履歴、補助行、CSV writer の境界を見ます。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\api\\finance\\PaymentMfImportController.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfExcelParser.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfCsvWriter.java" in C:\project\odamitsu-data-hub
 succeeded in 1846ms:
package jp.co.oda32.api.finance;

import jakarta.validation.Valid;
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

    @PostMapping("/convert/{uploadId}")
    public ResponseEntity<?> convert(@PathVariable String uploadId,
                                     @AuthenticationPrincipal LoginUser user) {
        try {
            byte[] csv = importService.convert(uploadId, user == null ? null : user.getUser().getLoginUserNo());
            String fileName = "買掛仕入MFインポートファイル.csv";
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv; charset=Shift_JIS"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"payment_mf.csv\"; filename*=UTF-8''" + encoded)
                    .body(csv);
        } catch (IllegalStateException e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ---- 買掛金一覧への一括検証反映 ----

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
    @PreAuthorize("hasRole('ADMIN')")
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
                            "attachment; filename=\"payment_mf.csv\"; filename*=UTF-8''" + encoded)
                    .header("X-Row-Count", String.valueOf(result.getRowCount()))
                    .header("X-Total-Amount", String.valueOf(result.getTotalAmount()))
                    .header("X-Skipped-Count", String.valueOf(suppliers.size()))
                    .header("X-Skipped-Suppliers", skippedHeader)
                    .body(result.getCsv());
        } catch (java.time.format.DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "日付形式が不正です (yyyy-MM-dd)"));
        } catch (IllegalStateException e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 検証済みCSV出力のプレビュー。ダイアログで件数確認 + 警告表示用。
     */
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/aux-rows")
    public ResponseEntity<?> auxRows(
            @RequestParam("transactionMonth") String transactionMonth) {
        try {
            java.time.LocalDate txMonth = java.time.LocalDate.parse(transactionMonth);
            List<PaymentMfAuxRowResponse> rows = importService.listAuxRows(txMonth);
            return ResponseEntity.ok(rows);
        } catch (java.time.format.DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "日付形式が不正です (yyyy-MM-dd)"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ---- 履歴 ----

    @GetMapping("/history")
    public ResponseEntity<List<PaymentMfHistoryResponse>> history() {
        return ResponseEntity.ok(
                historyRepository.findByShopNoAndDelFlgOrderByTransferDateDescIdDesc(1, "0")
                        .stream().map(PaymentMfHistoryResponse::from).toList());
    }

    @GetMapping("/history/{id}/csv")
    public ResponseEntity<?> historyCsv(@PathVariable Integer id) {
        try {
            byte[] csv = importService.getHistoryCsv(id);
            String fileName = "買掛仕入MFインポートファイル.csv";
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv; charset=Shift_JIS"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"payment_mf.csv\"; filename*=UTF-8''" + encoded)
                    .body(csv);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ---- ルールマスタCRUD ----

    @GetMapping("/rules")
    public ResponseEntity<List<PaymentMfRuleResponse>> rules() {
        return ResponseEntity.ok(ruleService.findAll().stream()
                .map(PaymentMfRuleResponse::from).toList());
    }

    @PostMapping("/rules")
    public ResponseEntity<PaymentMfRuleResponse> createRule(@Valid @RequestBody PaymentMfRuleRequest req,
                                                            @AuthenticationPrincipal LoginUser user) {
        // 未登録送り先の追加は一般ユーザもOK（UX優先）
        return ResponseEntity.ok(PaymentMfRuleResponse.from(
                ruleService.create(req, user == null ? null : user.getUser().getLoginUserNo())));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/rules/{id}")
    public ResponseEntity<PaymentMfRuleResponse> updateRule(@PathVariable Integer id,
                                                            @Valid @RequestBody PaymentMfRuleRequest req,
                                                            @AuthenticationPrincipal LoginUser user) {
        return ResponseEntity.ok(PaymentMfRuleResponse.from(
                ruleService.update(id, req, user == null ? null : user.getUser().getLoginUserNo())));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/rules/{id}")
    public ResponseEntity<?> deleteRule(@PathVariable Integer id,
                                        @AuthenticationPrincipal LoginUser user) {
        ruleService.delete(id, user == null ? null : user.getUser().getLoginUserNo());
        return ResponseEntity.noContent().build();
    }

    /**
     * PAYABLE ルールの payment_supplier_code を m_payment_supplier から一括補完。
     * dryRun=true でプレビューのみ、false で実際にDB更新。
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/rules/backfill-codes")
    public ResponseEntity<?> backfillCodes(
            @RequestParam(defaultValue = "true") boolean dryRun,
            @AuthenticationPrincipal LoginUser user) {
        var result = ruleService.backfillPaymentSupplierCodes(
                dryRun, user == null ? null : user.getUser().getLoginUserNo());
        return ResponseEntity.ok(result);
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1851ms:
package jp.co.oda32.domain.service.finance;

import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewRow;

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
            throw new IllegalStateException("CSV出力に失敗しました", e);
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
        if (v == null) return "";
        return v + " "; // 金額後ろに半角スペース（既存運用CSVに合わせる）
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2797ms:
package jp.co.oda32.domain.service.finance;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.time.LocalDate;
import java.util.ArrayList;
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
 *   <li>3 行目以降: 明細行 → 合計行 → 明細行（20日払いセクション）。</li>
 * </ul>
 *
 * <p>ステートレスな純粋関数ユーティリティ。Bean 化せず、呼び出し側は
 * {@link #selectSheet(Workbook)} / {@link #parseSheet(Sheet)} を直接呼ぶ。
 * 数値コードの 6 桁正規化は {@link PaymentMfImportService#normalizePaymentSupplierCode}
 * を利用する（PAYABLE 用のみ、同パッケージ内で共有）。
 */
final class PaymentMfExcelParser {

    private PaymentMfExcelParser() {}

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
     * 合計行の前は {@code afterTotal=false}、合計行以降は {@code afterTotal=true}（= 20日払いセクション）。
     */
    static ParsedWorkbook parseSheet(Sheet sheet) {
        ParsedWorkbook out = new ParsedWorkbook();
        out.entries = new ArrayList<>();

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
        String sourceName;
        Long amount;
        /** 合計行より後の行 (= 20日払いセクション) に含まれる明細か */
        boolean afterTotal;
    }

    /** parseSheet の戻り値: 明細エントリ一覧 + 合計行サマリ値 + 送金日。 */
    static class ParsedWorkbook {
        List<ParsedEntry> entries;
        LocalDate transferDate;
        Long summarySourceFee;
        Long summaryEarlyPayment;
        Long summaryTransferAmount;
        Long summaryInvoiceTotal;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3874ms:
package jp.co.oda32.domain.service.finance;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.model.finance.MPaymentMfRule;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.finance.TPaymentMfAuxRow;
import jp.co.oda32.domain.model.finance.TPaymentMfImportHistory;
import jp.co.oda32.domain.repository.finance.MPaymentMfRuleRepository;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.repository.finance.TPaymentMfAuxRowRepository;
import jp.co.oda32.domain.repository.finance.TPaymentMfImportHistoryRepository;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfAuxRowResponse;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewResponse;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfPreviewRow;
import jp.co.oda32.dto.finance.paymentmf.VerifiedExportPreviewResponse;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
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
            throw new IllegalStateException(
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

        int matched = 0, diff = 0, notFound = 0, skipped = 0;
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

        return VerifyResult.builder()
                .transferDate(cached.getTransferDate())
                .transactionMonth(txMonth)
                .matchedCount(matched)
                .diffCount(diff)
                .notFoundCount(notFound)
                .skippedCount(skipped)
                .unmatchedSuppliers(unmatchedSuppliers)
                .build();
    }

    /**
     * 同一 (shop_no, transaction_month) に対する applyVerification / exportVerifiedCsv を
     * 直列化する。PostgreSQL の {@code pg_advisory_xact_lock} は現在のトランザクション終了時に
     * 自動解放されるため、解放漏れリスクが無い。
     */
    private void acquireAdvisoryLock(LocalDate transactionMonth) {
        long lockKey = ((long) ADVISORY_LOCK_CLASS << 32)
                | (transactionMonth.toEpochDay() & 0xFFFF_FFFFL);
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:k)")
                .setParameter("k", lockKey)
                .getSingleResult();
    }

    /**
     * applyVerification 時に EXPENSE/SUMMARY/DIRECT_PURCHASE 行を
     * {@code t_payment_mf_aux_row} に洗い替え保存する。
     * PAYABLE 行は {@code t_accounts_payable_summary} 側で管理するためここでは対象外。
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
        private List<String> unmatchedSuppliers;
    }

    public byte[] getHistoryCsv(Integer id) {
        return historyRepository.findById(id)
                .map(TPaymentMfImportHistory::getCsvBody)
                .orElseThrow(() -> new IllegalArgumentException("履歴が見つかりません: " + id));
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
            throw new IllegalStateException(
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

            // PAYABLE 行の取引日は mf_transfer_date (= Excel 振込明細の送金日) を優先。
            // 同一 supplier に複数税率行があって mf_transfer_date がバラバラのケースは想定外だが、
            // 最初の非 null を採用する (振込明細一括検証時は全税率行に同じ値が入る)。
            java.time.LocalDate txDate = group.stream()
                    .map(TAccountsPayableSummary::getMfTransferDate)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            String sourceName = rule.getSourceName();
            PaymentMfPreviewRow row = PaymentMfPreviewRow.builder()
                    .paymentSupplierCode(s.getSupplierCode())
                    .sourceName(sourceName)
                    .amount(amount)
                    .transactionDate(txDate)
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
        // aux.transferDate (Excel 振込明細の送金日) を取引日として使う。
        for (TPaymentMfAuxRow aux : auxRows) {
            long amount = aux.getAmount() == null ? 0L : toLongFloor(aux.getAmount());
            totalAmount += amount;
            csvRows.add(PaymentMfPreviewRow.builder()
                    .paymentSupplierCode(aux.getPaymentSupplierCode())
                    .sourceName(aux.getSourceName())
                    .amount(amount)
                    .transactionDate(aux.getTransferDate())
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
            throw new IllegalStateException("CSV 出力可能な行がありません（ルール未登録のみ・補助行なし）: " + skipped);
        }

        // CSV 取引日列は 締め日 = transactionMonth を使用
        byte[] csv = PaymentMfCsvWriter.toCsvBytes(csvRows, transactionMonth);
        try {
            // REQUIRES_NEW を proxy 経由で発動させるため self. で自己呼び出しする
            // (this. だと Spring AOP を経由せず REQUIRES_NEW が無視され、履歴保存失敗時に
            //  本体 tx が巻き戻って手動検証結果が失われる)。
            self.saveVerifiedExportHistory(transactionMonth, csvRows.size(), totalAmount, csv, userNo);
        } catch (Exception e) {
            log.error("検証済み CSV 出力履歴の保存に失敗（CSV は正常完了）: transactionMonth={}", transactionMonth, e);
        }

        return VerifiedExportResult.builder()
                .csv(csv)
                .rowCount(csvRows.size())
                .payableCount(payableCount)
                .auxCount(auxCount)
                .totalAmount(totalAmount)
                .skippedSuppliers(skipped)
                .transactionMonth(transactionMonth)
                .build();
    }

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
        Map<String, VerifiedExportPreviewResponse.AuxBreakdownItem> breakdown = new LinkedHashMap<>();
        Set<LocalDate> seenTransferDates = new java.util.LinkedHashSet<>();
        for (TPaymentMfAuxRow aux : auxRows) {
            seenTransferDates.add(aux.getTransferDate());
            String key = aux.getTransferDate() + "|" + aux.getRuleKind();
            VerifiedExportPreviewResponse.AuxBreakdownItem item =
                    breakdown.computeIfAbsent(key, k -> VerifiedExportPreviewResponse.AuxBreakdownItem.builder()
                            .transferDate(aux.getTransferDate())
                            .ruleKind(aux.getRuleKind())
                            .count(0)
                            .totalAmount(0L)
                            .build());
            item.setCount(item.getCount() + 1);
            item.setTotalAmount(item.getTotalAmount() + (aux.getAmount() == null ? 0L : toLongFloor(aux.getAmount())));
        }

        // 警告: 取引月の翌月に 5日払い相当 / 20日払い相当の送金分が揃っているか確認。
        // 土日祝による振替で 5日 が 4日/6日/7日、20日 が 19日/21日 等に前後するため、
        // 翌月の前半(1日?境界日-1)を「5日払い相当」、後半(境界日?末日)を「20日払い相当」として判定する。
        // 境界日は FinanceConstants.PAYMENT_DATE_MIDMONTH_CUTOFF で定数化。
        List<String> warnings = new ArrayList<>();
        LocalDate nextMonthStart = transactionMonth.plusMonths(1).withDayOfMonth(1);
        LocalDate nextMonthEnd = nextMonthStart.withDayOfMonth(nextMonthStart.lengthOfMonth());
        LocalDate midMonth = nextMonthStart.withDayOfMonth(FinanceConstants.PAYMENT_DATE_MIDMONTH_CUTOFF);

        boolean hasFirstHalf = seenTransferDates.stream()
                .anyMatch(d -> !d.isBefore(nextMonthStart) && d.isBefore(midMonth));
        boolean hasSecondHalf = seenTransferDates.stream()
                .anyMatch(d -> !d.isBefore(midMonth) && !d.isAfter(nextMonthEnd));

        if (!hasFirstHalf) {
            warnings.add("5日払い相当 Excel（" + nextMonthStart + " ? " + midMonth.minusDays(1)
                    + " の送金分）の補助行が見つかりません。"
                    + "「振込明細で一括検証」から 5日払い Excel をアップロードしてください");
        }
        if (!hasSecondHalf) {
            warnings.add("20日払い相当 Excel（" + midMonth + " ? " + nextMonthEnd
                    + " の送金分）の補助行が見つかりません。"
                    + "「振込明細で一括検証」から 20日払い Excel をアップロードしてください");
        }

        return VerifiedExportPreviewResponse.builder()
                .transactionMonth(transactionMonth)
                .payableCount(payableCount)
                .payableTotalAmount(payableTotal)
                .auxBreakdown(new ArrayList<>(breakdown.values()))
                .warnings(warnings)
                .skippedSuppliers(skipped)
                .build();
    }

    /** 補助行一覧を取引月指定で返す（タブ表示用）。 */
    @Transactional(readOnly = true)
    public List<PaymentMfAuxRowResponse> listAuxRows(LocalDate transactionMonth) {
        if (transactionMonth == null) throw new IllegalArgumentException("transactionMonth が未指定です");
        return auxRowRepository
                .findByShopNoAndTransactionMonthOrderByTransferDateAscSequenceNoAsc(
                        FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth)
                .stream()
                .map(PaymentMfAuxRowResponse::from)
                .toList();
    }

    /**
     * 履歴保存は別トランザクションにして、履歴保存失敗が CSV 生成本体の結果に影響しないようにする。
     * 例外は握り潰さず呼び元へ伝播し、呼び元でログ＋ユーザ警告に変換する。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveVerifiedExportHistory(LocalDate transactionMonth,
                                             int rowCount, long totalAmount, byte[] csv, Integer userNo) {
        String yyyymmdd = transactionMonth.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String csvFile = "買掛仕入MFインポートファイル_" + yyyymmdd + ".csv";
        String source = "verified-export_" + transactionMonth;
        TPaymentMfImportHistory h = TPaymentMfImportHistory.builder()
                .shopNo(FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO)
                // 履歴の transferDate カラムは既存フロー互換で締め日をそのまま入れる
                // (送金日は CSV に含まれず、検証済み出力では意味を持たないため)
                .transferDate(transactionMonth)
                .sourceFilename(source)
                .csvFilename(csvFile)
                .rowCount(rowCount)
                .totalAmount(totalAmount)
                .matchedCount(rowCount)
                .diffCount(0)
                .unmatchedCount(0)
                .csvBody(csv)
                .addDateTime(LocalDateTime.now())
                .addUserNo(userNo)
                .build();
        historyRepository.save(h);
    }

    // ===========================================================
    // Preview building (rule matching + 買掛金 reconciliation)
    // ===========================================================

    private PaymentMfPreviewResponse buildPreview(String uploadId, CachedUpload cached) {
        List<MPaymentMfRule> rules = ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0");
        Map<String, MPaymentMfRule> byCode = new LinkedHashMap<>();
        Map<String, MPaymentMfRule> bySource = new LinkedHashMap<>();
        for (MPaymentMfRule r : rules) {
            if (r.getPaymentSupplierCode() != null && !r.getPaymentSupplierCode().isBlank()) {
                byCode.putIfAbsent(r.getPaymentSupplierCode().trim(), r);
            }
            bySource.putIfAbsent(normalize(r.getSourceName()), r);
        }

        LocalDate txMonth = cached.getTransferDate() == null ? null
                : deriveTransactionMonth(cached.getTransferDate());

        // N+1 解消 (B-W11): エントリ全走査より先に reconcileCode を集め、対象月の payable を一括ロード。
        // 以前は reconcile() 呼び出しの度に findByShopNoAndSupplierCodeAndTransactionMonth が走っていた。
        Map<String, List<TAccountsPayableSummary>> payablesByCode = java.util.Collections.emptyMap();
        if (txMonth != null) {
            Set<String> reconcileCodes = new java.util.LinkedHashSet<>();
            for (PaymentMfExcelParser.ParsedEntry e : cached.getEntries()) {
                MPaymentMfRule rule = null;
                if (e.supplierCode != null) rule = byCode.get(e.supplierCode);
                if (rule == null) rule = bySource.get(normalize(e.sourceName));
                if (rule == null) continue;
                if (e.afterTotal && "PAYABLE".equals(rule.getRuleKind())) continue; // DIRECT_PURCHASE 扱い
                if (!"PAYABLE".equals(rule.getRuleKind())) continue;
                String code = e.supplierCode != null ? e.supplierCode
                        : (rule.getPaymentSupplierCode() != null && !rule.getPaymentSupplierCode().isBlank()
                                ? rule.getPaymentSupplierCode() : null);
                if (code != null) reconcileCodes.add(code);
            }
            if (!reconcileCodes.isEmpty()) {
                payablesByCode = payableRepository
                        .findByShopNoAndSupplierCodeInAndTransactionMonth(
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

        for (PaymentMfExcelParser.ParsedEntry e : cached.getEntries()) {
            MPaymentMfRule rule = null;
            if (e.supplierCode != null) rule = byCode.get(e.supplierCode);
            if (rule == null) rule = bySource.get(normalize(e.sourceName));

            // 合計行以降(20日払いセクション) は買掛金(PAYABLE)ではなく仕入高(DIRECT_PURCHASE)扱い
            if (rule != null && e.afterTotal && "PAYABLE".equals(rule.getRuleKind())) {
                rule = MPaymentMfRule.builder()
                        .sourceName(rule.getSourceName())
                        .ruleKind("DIRECT_PURCHASE")
                        .debitAccount("仕入高")
                        .debitSubAccount(null)
                        .debitDepartment(null)
                        .debitTaxCategory("課税仕入 10%")
                        .creditAccount("資金複合")
                        .creditTaxCategory("対象外")
                        .summaryTemplate("{source_name}")
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
                    .creditAmount(e.amount)
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
        }

        // 合計行からサマリー仕訳（振込手数料値引・早払収益）を末尾2行として追加
        if (cached.getTransferDate() != null) {
            int d = cached.getTransferDate().getDayOfMonth();
            long fee = cached.getSummarySourceFee() == null ? 0L : cached.getSummarySourceFee();
            long early = cached.getSummaryEarlyPayment() == null ? 0L : cached.getSummaryEarlyPayment();
            rows.add(PaymentMfPreviewRow.builder()
                    .ruleKind("SUMMARY")
                    .sourceName("振込手数料値引")
                    .amount(fee)
                    .debitAccount("資金複合").debitTax("対象外").debitAmount(fee)
                    .creditAccount("仕入値引・戻し高").creditDepartment("物販事業部")
                    .creditTax("課税仕入-返還等 10%").creditAmount(fee)
                    .summary("振込手数料値引／" + d + "日払い分")
                    .matchStatus("NA").build());
            rows.add(PaymentMfPreviewRow.builder()
                    .ruleKind("SUMMARY")
                    .sourceName("早払収益")
                    .amount(early)
                    .debitAccount("資金複合").debitTax("対象外").debitAmount(early)
                    .creditAccount("早払収益").creditDepartment("物販事業部")
                    .creditTax("非課税売上").creditAmount(early)
                    .summary("早払収益／" + d + "日払い分")
                    .matchStatus("NA").build());
        }

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
                        .build();

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
     *       <li>1桁入力は×100扱い（"0" のみは 0 → "000000" となり下流で不一致となる）。</li>
     *     </ul>
     *   </li>
     *   <li>非数字・空文字は trim した元文字列を返却（下流で不一致としてログされる）。</li>
     * </ul>
     * <p>パッケージプライベート: 単体テスト向け。外部からは呼ばない。
     */
    static String normalizePaymentSupplierCode(String raw) {
        if (raw == null) return null;
        String digits = raw.trim();
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) return digits;
        try {
            long n = Long.parseLong(digits);
            if (digits.length() <= 4) n = n * 100L;
            String s = Long.toString(n);
            if (s.length() >= 6) return s;
            return "0".repeat(6 - s.length()) + s;
        } catch (NumberFormatException ex) {
            return digits;
        }
    }

    /**
     * 送金日 → 対応する買掛金の取引月（締め日）を決定する。
     * <p>運用実態: 5日送金・20日送金ともに「前月20日締め」の買掛金を支払う。
     * <ul>
     *   <li>5日送金  = 前月20日締め残高の主精算</li>
     *   <li>20日送金 = 同じ前月20日締め残高の2回目精算（早払い／残金）。
     *       20日は「当月の締め日」でもあるが、その日の送金は当日時点で既に確定している
     *       前月締め残高に対して行うのが運用。当月分(2026-04-20締め)は翌月5日送金で支払う。</li>
     * </ul>
     * <p>※ design-payment-mf-import.md §5.1 の「20日→当月20日締め」記述は運用実態と異なるため
     * 次回設計書更新で是正すること。
     * <p>例) 2026/02/05 → 2026-01-20
     *       2026/02/20 → 2026-01-20
     *       2026/04/20 → 2026-03-20
     */
    LocalDate deriveTransactionMonth(LocalDate transferDate) {
        return transferDate.minusMonths(1).withDayOfMonth(20);
    }

    private static class ReconcileResult {
        String status; Long payableAmount; Long diff; Integer supplierNo;
    }

    /**
     * 事前取得した payable リストから突合判定する (N+1 解消版)。
     * list が null/空なら UNMATCHED。
     */
    private ReconcileResult reconcileFromPayables(List<TAccountsPayableSummary> list, Long invoiceAmount) {
        ReconcileResult r = new ReconcileResult();
        if (list == null || list.isEmpty()) {
            r.status = "UNMATCHED";
            return r;
        }
        long payable = 0L;
        Integer supplierNo = null;
        for (TAccountsPayableSummary s : list) {
            BigDecimal v = s.getTaxIncludedAmountChange() != null
                    ? s.getTaxIncludedAmountChange()
                    : s.getTaxIncludedAmount();
            if (v != null) payable += toLongFloor(v);
            if (supplierNo == null) supplierNo = s.getSupplierNo();
        }
        r.payableAmount = payable;
        r.diff = payable - invoiceAmount;
        r.supplierNo = supplierNo;
        r.status = Math.abs(r.diff) <= FinanceConstants.PAYMENT_REPORT_MINOR_DIFFERENCE_LONG
                ? "MATCHED" : "DIFF";
        return r;
    }

    // Excel シート選択・解析は {@link PaymentMfExcelParser} に分離。


    // ===========================================================
    // CSV generation
    // ===========================================================

    /**
     * CSV バイト列を生成する。
     * <p>第2引数 {@code transactionDate} は CSV「取引日」列に入れる値で、小田光の
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
            throw new IllegalArgumentException("xlsxファイルのみ対応しています");
        String ct = file.getContentType();
        if (ct != null && !ct.isBlank() && !XLSX_CONTENT_TYPE.equals(ct)
                && !"application/octet-stream".equals(ct)) {
            throw new IllegalArgumentException("xlsxファイルのみ対応しています");
        }
    }

    private CachedUpload getCached(String uploadId) {
        CachedUpload c = cache.get(uploadId);
        if (c == null || c.getExpiresAt() < System.currentTimeMillis())
            throw new IllegalArgumentException("アップロードが見つからないか期限切れです。再アップロードしてください");
        return c;
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    void cleanExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue().getExpiresAt() < now);
    }

    /**
     * enforceCacheLimit と cache.put を 1 つの synchronized ブロックに入れ、
     * 「サイズ閾値チェック直後に別スレッドが put し上限を超える」race を排除する。
     */
    private synchronized void putCacheAtomically(String uploadId, CachedUpload cached) {
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            long now = System.currentTimeMillis();
            cache.entrySet().removeIf(e -> e.getValue().getExpiresAt() < now);
            int guard = MAX_CACHE_ENTRIES + 10;
            while (cache.size() >= MAX_CACHE_ENTRIES && guard-- > 0) {
                String oldest = cache.entrySet().stream()
                        .min(Comparator.comparingLong(e -> e.getValue().getExpiresAt()))
                        .map(Map.Entry::getKey).orElse(null);
                if (oldest == null) break;
                cache.remove(oldest);
            }
        }
        cache.put(uploadId, cached);
    }

    // Excel セル読み出し・文字列正規化は {@link PaymentMfCellReader} に分離。

    /** 他クラスから参照されているためシム関数として残す。実装は {@link PaymentMfCellReader#normalize}。 */
    static String normalize(String s) {
        return PaymentMfCellReader.normalize(s);
    }

    // ===========================================================
    // DTOs (internal)
    // ===========================================================

    @Data
    @Builder
    static class CachedUpload {
        private List<PaymentMfExcelParser.ParsedEntry> entries;
        private LocalDate transferDate;
        private Long summarySourceFee;
        private Long summaryEarlyPayment;
        /** 合計行 振込金額列 の値（参考情報）。 */
        private Long summaryTransferAmount;
        /** 合計行 請求額列 の値（明細読取り整合性チェック用）。 */
        private Long summaryInvoiceTotal;
        private String fileName;
        private long expiresAt;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "rg -n \"exportVerifiedCsv|buildVerifiedExportPreview|saveVerifiedExportHistory|buildPreview|saveAuxRowsForVerification|sumVerifiedAmount|auto|history|transactionDate|auxToPreviewRow\" backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\MPaymentMfRule.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\TPaymentMfImportHistory.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\TPaymentMfAuxRow.java" in C:\project\odamitsu-data-hub
 succeeded in 1786ms:
package jp.co.oda32.domain.model.finance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 買掛仕入MF 補助行 Entity。
 * <p>振込明細 Excel を {@code applyVerification} した際、PAYABLE(買掛金) 以外の
 * EXPENSE / SUMMARY / DIRECT_PURCHASE 行を永続化する。検証済みCSV出力時に
 * {@code t_accounts_payable_summary}(PAYABLE) と結合して完全な MF 仕訳 CSV を
 * Excel 再アップロードなしで再生成するための拠り所となる。
 *
 * <p>PK は {@code aux_row_id}（サロゲート）。論理的な一意性は
 * (shop_no, transaction_month, transfer_date) 単位で物理削除→再挿入で保持する。
 * 設計書: {@code claudedocs/design-payment-mf-aux-rows.md}
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_payment_mf_aux_row")
public class TPaymentMfAuxRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "aux_row_id")
    private Long auxRowId;

    @Column(name = "shop_no", nullable = false)
    private Integer shopNo;

    /** 小田光締め日(前月20日)。CSV 取引日列にも使用。 */
    @Column(name = "transaction_month", nullable = false)
    private LocalDate transactionMonth;

    /** 出処 Excel の送金日 (5日 or 20日)。(transaction_month, transfer_date) で洗い替え。 */
    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    /** EXPENSE / SUMMARY / DIRECT_PURCHASE */
    @Column(name = "rule_kind", nullable = false, length = 20)
    private String ruleKind;

    /** Excel 内の出現順。CSV 出力順序を保つため保存。 */
    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "source_name", nullable = false)
    private String sourceName;

    @Column(name = "payment_supplier_code", length = 20)
    private String paymentSupplierCode;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "debit_account", nullable = false, length = 50)
    private String debitAccount;

    @Column(name = "debit_sub_account", length = 50)
    private String debitSubAccount;

    @Column(name = "debit_department", length = 50)
    private String debitDepartment;

    @Column(name = "debit_tax", nullable = false, length = 30)
    private String debitTax;

    @Column(name = "credit_account", nullable = false, length = 50)
    private String creditAccount;

    @Column(name = "credit_sub_account", length = 50)
    private String creditSubAccount;

    @Column(name = "credit_department", length = 50)
    private String creditDepartment;

    @Column(name = "credit_tax", nullable = false, length = 30)
    private String creditTax;

    @Column(name = "summary")
    private String summary;

    @Column(name = "tag", length = 50)
    private String tag;

    @Column(name = "source_filename")
    private String sourceFilename;

    @Column(name = "add_date_time", nullable = false)
    private LocalDateTime addDateTime;

    @Column(name = "add_user_no")
    private Integer addUserNo;

    @Column(name = "modify_date_time")
    private LocalDateTime modifyDateTime;

    @Column(name = "modify_user_no")
    private Integer modifyUserNo;
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2744ms:
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

2026-05-04T01:47:07.527466Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 3.5 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg -n "exportVerifiedCsv|buildVerifiedExportPreview|saveVerifiedExpor 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 exited 1 in 3480ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg -n "exportVerifiedCsv|buildVerifiedExportPreview|saveVerifiedExpor 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 succeeded in 3702ms:
package jp.co.oda32.domain.model.finance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_payment_mf_rule")
public class MPaymentMfRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "source_name", nullable = false)
    private String sourceName;

    @Column(name = "payment_supplier_code")
    private String paymentSupplierCode;

    @Column(name = "rule_kind", nullable = false)
    private String ruleKind;

    @Column(name = "debit_account", nullable = false)
    private String debitAccount;

    @Column(name = "debit_sub_account")
    private String debitSubAccount;

    @Column(name = "debit_department")
    private String debitDepartment;

    @Column(name = "debit_tax_category", nullable = false)
    private String debitTaxCategory;

    @Column(name = "credit_account", nullable = false)
    @Builder.Default
    private String creditAccount = "資金複合";

    @Column(name = "credit_sub_account")
    private String creditSubAccount;

    @Column(name = "credit_department")
    private String creditDepartment;

    @Column(name = "credit_tax_category", nullable = false)
    @Builder.Default
    private String creditTaxCategory = "対象外";

    @Column(name = "summary_template", nullable = false)
    private String summaryTemplate;

    @Column(name = "tag")
    private String tag;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 100;

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

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java -Pattern \"exportVerifiedCsv|buildVerifiedExportPreview|saveVerifiedExportHistory|buildPreview|saveAuxRowsForVerification|sumVerifiedAmount|auto|history|transactionDate|auxToPreviewRow|missing\"" in C:\project\odamitsu-data-hub
 succeeded in 1554ms:

backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:10:import jp.co.oda32.domain.model
.finance.TPaymentMfImportHistory;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:14:import jp.co.oda32.domain.repos
itory.finance.TPaymentMfImportHistoryRepository;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:26:import org.springframework.bean
s.factory.annotation.Autowired;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:58:    private final TPaymentMfImp
ortHistoryRepository historyRepository;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:70:    @Autowired
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:83:     * 取引月単位の applyVerifi
cation / exportVerifiedCsv を直列化する advisory lock 用キー。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:102:                              
    TPaymentMfImportHistoryRepository historyRepository,
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:107:        this.historyRepository
 = historyRepository;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:147:            return buildPrevie
w(uploadId, cached);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:153:        return buildPreview(up
loadId, cached);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:161:        PaymentMfPreviewRespon
se preview = buildPreview(uploadId, cached);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:171:        saveHistory(cached, pr
eview, csv, userNo);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:263:            // auto_adjusted_a
mount に自動調整額 (verified - 元の自社計算) を記録 (監査証跡)。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:264:            BigDecimal autoAdj
usted = isMatched ? invoice.subtract(payable) : BigDecimal.ZERO;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:265:            String adjustNote 
= isMatched && autoAdjusted.signum() != 0
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:267:                      + " (" +
 (autoAdjusted.signum() > 0 ? "+" : "") + "\" + autoAdjusted + ")"
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:282:                s.setAutoAdjus
tedAmount(autoAdjusted);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:292:        // (従来は saveAuxRows
ForVerification 内で再 buildPreview していたため N+1 が 2 周していた)。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:293:        PaymentMfPreviewRespon
se preview = buildPreview(uploadId, cached);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:294:        saveAuxRowsForVerifica
tion(cached, preview, txMonth, userNo);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:308:     * 同一 (shop_no, transact
ion_month) に対する applyVerification / exportVerifiedCsv を
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:327:     *                （再 bui
ldPreview で N+1 を再度走らせないため）。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:329:    private void saveAuxRowsFo
rVerification(CachedUpload cached, PaymentMfPreviewResponse preview,
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:392:    public byte[] getHistoryCs
v(Integer id) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:393:        return historyReposito
ry.findById(id)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:394:                .map(TPaymentM
fImportHistory::getCsvBody)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:417:    private static long sumVer
ifiedAmountForGroup(List<TAccountsPayableSummary> group) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:461:    public VerifiedExportResul
t exportVerifiedCsv(LocalDate transactionMonth, Integer userNo) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:504:            long amount = sumV
erifiedAmountForGroup(group);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:522:                    .transacti
onDate(txDate)
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:552:                    .transacti
onDate(aux.getTransferDate())
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:581:            self.saveVerifiedE
xportHistory(transactionMonth, csvRows.size(), totalAmount, csv, userNo);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:602:    public VerifiedExportPrevi
ewResponse buildVerifiedExportPreview(LocalDate transactionMonth) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:635:            long amount = sumV
erifiedAmountForGroup(group);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:710:    public void saveVerifiedEx
portHistory(LocalDate transactionMonth,
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:715:        TPaymentMfImportHistor
y h = TPaymentMfImportHistory.builder()
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:731:        historyRepository.save
(h);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:738:    private PaymentMfPreviewRe
sponse buildPreview(String uploadId, CachedUpload cached) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:782:        Set<String> rulesMissi
ngCode = new java.util.LinkedHashSet<>();
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:856:                rulesMissingCo
de.add(code + " " + e.sourceName);
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:941:                .rulesMissingS
upplierCode(new ArrayList<>(rulesMissingCode))
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1055:     * <p>第2引数 {@code tran
sactionDate} は CSV「取引日」列に入れる値で、小田光の
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1060:    // History
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1063:    private void saveHistory(
CachedUpload cached, PaymentMfPreviewResponse preview,
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1069:            TPaymentMfImportH
istory h = TPaymentMfImportHistory.builder()
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java:1083:            historyRepository
.save(h);

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

2026-05-04T01:47:23.423143Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$p='"'backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java'; Get-Content "'$p | Select-Object -Skip 380 -First 230'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$p='"'backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java'; Get-Content "'$p | Select-Object -Skip 380 -First 230' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$p='"'backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java'; Get-Content "'$p | Select-Object -Skip 600 -First 130' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$p='"'backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java'; Get-Content "'$p | Select-Object -Skip 380 -First 230'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$p='"'backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java'; Get-Content "'$p | Select-Object -Skip 730 -First 230' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfCellReader.java" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$p='"'backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java'; Get-Content "'$p | Select-Object -Skip 600 -First 130'` rejected: blocked by policy
2026-05-04T01:47:23.424519Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$p='"'backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java'; Get-Content "'$p | Select-Object -Skip 600 -First 130'` rejected: blocked by policy
2026-05-04T01:47:23.424996Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$p='"'backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java'; Get-Content "'$p | Select-Object -Skip 730 -First 230'` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$p='"'backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java'; Get-Content "'$p | Select-Object -Skip 730 -First 230'` rejected: blocked by policy
 succeeded in 1428ms:
package jp.co.oda32.domain.service.finance;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;

import java.time.LocalDate;

/**
 * Excel セルを型安全に読み出すユーティリティ。
 * <p>ステートレスな純粋関数のみ提供する（Bean 化しない）。
 * テストから直接呼べるよう package-private で公開。
 */
final class PaymentMfCellReader {

    private PaymentMfCellReader() {}

    /** 改行・復帰文字を {@code '_'} で置換する（CSV や DB への混入防止）。 */
    static String sanitize(String s) {
        if (s == null) return null;
        return s.replaceAll("[\\r\\n]", "_");
    }

    /** 全角空白・前後空白除去・連続空白1個化で、比較用に正規化する。 */
    static String normalize(String s) {
        if (s == null) return "";
        return s.replace('\u3000', ' ').strip().replaceAll("\\s+", " ");
    }

    /**
     * セル値を文字列として読み出す。NUMERIC は整数ならそのまま整数、
     * そうでなければ double 文字列化。FORMULA は文字列→数値の順でフォールバック。
     */
    static String readStringCell(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d))
                    yield String.valueOf((long) d);
                yield String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        double d2 = cell.getNumericCellValue();
                        if (d2 == Math.floor(d2)) yield String.valueOf((long) d2);
                        yield String.valueOf(d2);
                    } catch (Exception e2) { yield null; }
                }
            }
            default -> null;
        };
    }

    /**
     * セル値を Long として読み出す。文字列の "1,234" などはカンマ除去してパース。
     * 非数値・空欄は null を返す。
     */
    static Long readLongCell(Cell cell) {
        if (cell == null) return null;
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> (long) cell.getNumericCellValue();
                case STRING -> {
                    String s = cell.getStringCellValue().trim();
                    if (s.isEmpty()) yield null;
                    try { yield Long.parseLong(s.replace(",", "")); }
                    catch (NumberFormatException e) { yield null; }
                }
                case FORMULA -> {
                    try { yield (long) cell.getNumericCellValue(); }
                    catch (Exception e) { yield null; }
                }
                default -> null;
            };
        } catch (Exception e) { return null; }
    }

    /**
     * セル値を LocalDate として読み出す。日付書式セル → シリアル値 → null の順でフォールバック。
     */
    static LocalDate readDateCell(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate();
                }
                double d = cell.getNumericCellValue();
                // Excelの日付シリアル値範囲で date formatted が効かないケースのフォールバック
                if (d > 40000 && d < 100000) {
                    return DateUtil.getLocalDateTime(d).toLocalDate();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java | Select-Object -Skip 380 -First 230" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java | Select-Object -Skip 600 -First 130" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java | Select-Object -Skip 730 -First 230" in C:\project\odamitsu-data-hub
 succeeded in 1763ms:
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
        Map<String, VerifiedExportPreviewResponse.AuxBreakdownItem> breakdown = new LinkedHashMap<>();
        Set<LocalDate> seenTransferDates = new java.util.LinkedHashSet<>();
        for (TPaymentMfAuxRow aux : auxRows) {
            seenTransferDates.add(aux.getTransferDate());
            String key = aux.getTransferDate() + "|" + aux.getRuleKind();
            VerifiedExportPreviewResponse.AuxBreakdownItem item =
                    breakdown.computeIfAbsent(key, k -> VerifiedExportPreviewResponse.AuxBreakdownItem.builder()
                            .transferDate(aux.getTransferDate())
                            .ruleKind(aux.getRuleKind())
                            .count(0)
                            .totalAmount(0L)
                            .build());
            item.setCount(item.getCount() + 1);
            item.setTotalAmount(item.getTotalAmount() + (aux.getAmount() == null ? 0L : toLongFloor(aux.getAmount())));
        }

        // 警告: 取引月の翌月に 5日払い相当 / 20日払い相当の送金分が揃っているか確認。
        // 土日祝による振替で 5日 が 4日/6日/7日、20日 が 19日/21日 等に前後するため、
        // 翌月の前半(1日?境界日-1)を「5日払い相当」、後半(境界日?末日)を「20日払い相当」として判定する。
        // 境界日は FinanceConstants.PAYMENT_DATE_MIDMONTH_CUTOFF で定数化。
        List<String> warnings = new ArrayList<>();
        LocalDate nextMonthStart = transactionMonth.plusMonths(1).withDayOfMonth(1);
        LocalDate nextMonthEnd = nextMonthStart.withDayOfMonth(nextMonthStart.lengthOfMonth());
        LocalDate midMonth = nextMonthStart.withDayOfMonth(FinanceConstants.PAYMENT_DATE_MIDMONTH_CUTOFF);

        boolean hasFirstHalf = seenTransferDates.stream()
                .anyMatch(d -> !d.isBefore(nextMonthStart) && d.isBefore(midMonth));
        boolean hasSecondHalf = seenTransferDates.stream()
                .anyMatch(d -> !d.isBefore(midMonth) && !d.isAfter(nextMonthEnd));

        if (!hasFirstHalf) {
            warnings.add("5日払い相当 Excel（" + nextMonthStart + " ? " + midMonth.minusDays(1)
                    + " の送金分）の補助行が見つかりません。"
                    + "「振込明細で一括検証」から 5日払い Excel をアップロードしてください");
        }
        if (!hasSecondHalf) {
            warnings.add("20日払い相当 Excel（" + midMonth + " ? " + nextMonthEnd
                    + " の送金分）の補助行が見つかりません。"
                    + "「振込明細で一括検証」から 20日払い Excel をアップロードしてください");
        }

        return VerifiedExportPreviewResponse.builder()
                .transactionMonth(transactionMonth)
                .payableCount(payableCount)
                .payableTotalAmount(payableTotal)
                .auxBreakdown(new ArrayList<>(breakdown.values()))
                .warnings(warnings)
                .skippedSuppliers(skipped)
                .build();
    }

    /** 補助行一覧を取引月指定で返す（タブ表示用）。 */
    @Transactional(readOnly = true)
    public List<PaymentMfAuxRowResponse> listAuxRows(LocalDate transactionMonth) {
        if (transactionMonth == null) throw new IllegalArgumentException("transactionMonth が未指定です");
        return auxRowRepository
                .findByShopNoAndTransactionMonthOrderByTransferDateAscSequenceNoAsc(
                        FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO, transactionMonth)
                .stream()
                .map(PaymentMfAuxRowResponse::from)
                .toList();
    }

    /**
     * 履歴保存は別トランザクションにして、履歴保存失敗が CSV 生成本体の結果に影響しないようにする。
     * 例外は握り潰さず呼び元へ伝播し、呼び元でログ＋ユーザ警告に変換する。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveVerifiedExportHistory(LocalDate transactionMonth,
                                             int rowCount, long totalAmount, byte[] csv, Integer userNo) {
        String yyyymmdd = transactionMonth.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String csvFile = "買掛仕入MFインポートファイル_" + yyyymmdd + ".csv";
        String source = "verified-export_" + transactionMonth;
        TPaymentMfImportHistory h = TPaymentMfImportHistory.builder()
                .shopNo(FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO)
                // 履歴の transferDate カラムは既存フロー互換で締め日をそのまま入れる
                // (送金日は CSV に含まれず、検証済み出力では意味を持たないため)
                .transferDate(transactionMonth)
                .sourceFilename(source)
                .csvFilename(csvFile)
                .rowCount(rowCount)
                .totalAmount(totalAmount)
                .matchedCount(rowCount)
                .diffCount(0)
                .unmatchedCount(0)
                .csvBody(csv)
                .addDateTime(LocalDateTime.now())
                .addUserNo(userNo)
                .build();
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1927ms:
        historyRepository.save(h);
    }

    // ===========================================================
    // Preview building (rule matching + 買掛金 reconciliation)
    // ===========================================================

    private PaymentMfPreviewResponse buildPreview(String uploadId, CachedUpload cached) {
        List<MPaymentMfRule> rules = ruleRepository.findByDelFlgOrderByPriorityAscIdAsc("0");
        Map<String, MPaymentMfRule> byCode = new LinkedHashMap<>();
        Map<String, MPaymentMfRule> bySource = new LinkedHashMap<>();
        for (MPaymentMfRule r : rules) {
            if (r.getPaymentSupplierCode() != null && !r.getPaymentSupplierCode().isBlank()) {
                byCode.putIfAbsent(r.getPaymentSupplierCode().trim(), r);
            }
            bySource.putIfAbsent(normalize(r.getSourceName()), r);
        }

        LocalDate txMonth = cached.getTransferDate() == null ? null
                : deriveTransactionMonth(cached.getTransferDate());

        // N+1 解消 (B-W11): エントリ全走査より先に reconcileCode を集め、対象月の payable を一括ロード。
        // 以前は reconcile() 呼び出しの度に findByShopNoAndSupplierCodeAndTransactionMonth が走っていた。
        Map<String, List<TAccountsPayableSummary>> payablesByCode = java.util.Collections.emptyMap();
        if (txMonth != null) {
            Set<String> reconcileCodes = new java.util.LinkedHashSet<>();
            for (PaymentMfExcelParser.ParsedEntry e : cached.getEntries()) {
                MPaymentMfRule rule = null;
                if (e.supplierCode != null) rule = byCode.get(e.supplierCode);
                if (rule == null) rule = bySource.get(normalize(e.sourceName));
                if (rule == null) continue;
                if (e.afterTotal && "PAYABLE".equals(rule.getRuleKind())) continue; // DIRECT_PURCHASE 扱い
                if (!"PAYABLE".equals(rule.getRuleKind())) continue;
                String code = e.supplierCode != null ? e.supplierCode
                        : (rule.getPaymentSupplierCode() != null && !rule.getPaymentSupplierCode().isBlank()
                                ? rule.getPaymentSupplierCode() : null);
                if (code != null) reconcileCodes.add(code);
            }
            if (!reconcileCodes.isEmpty()) {
                payablesByCode = payableRepository
                        .findByShopNoAndSupplierCodeInAndTransactionMonth(
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

        for (PaymentMfExcelParser.ParsedEntry e : cached.getEntries()) {
            MPaymentMfRule rule = null;
            if (e.supplierCode != null) rule = byCode.get(e.supplierCode);
            if (rule == null) rule = bySource.get(normalize(e.sourceName));

            // 合計行以降(20日払いセクション) は買掛金(PAYABLE)ではなく仕入高(DIRECT_PURCHASE)扱い
            if (rule != null && e.afterTotal && "PAYABLE".equals(rule.getRuleKind())) {
                rule = MPaymentMfRule.builder()
                        .sourceName(rule.getSourceName())
                        .ruleKind("DIRECT_PURCHASE")
                        .debitAccount("仕入高")
                        .debitSubAccount(null)
                        .debitDepartment(null)
                        .debitTaxCategory("課税仕入 10%")
                        .creditAccount("資金複合")
                        .creditTaxCategory("対象外")
                        .summaryTemplate("{source_name}")
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
                    .creditAmount(e.amount)
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
        }

        // 合計行からサマリー仕訳（振込手数料値引・早払収益）を末尾2行として追加
        if (cached.getTransferDate() != null) {
            int d = cached.getTransferDate().getDayOfMonth();
            long fee = cached.getSummarySourceFee() == null ? 0L : cached.getSummarySourceFee();
            long early = cached.getSummaryEarlyPayment() == null ? 0L : cached.getSummaryEarlyPayment();
            rows.add(PaymentMfPreviewRow.builder()
                    .ruleKind("SUMMARY")
                    .sourceName("振込手数料値引")
                    .amount(fee)
                    .debitAccount("資金複合").debitTax("対象外").debitAmount(fee)
                    .creditAccount("仕入値引・戻し高").creditDepartment("物販事業部")
                    .creditTax("課税仕入-返還等 10%").creditAmount(fee)
                    .summary("振込手数料値引／" + d + "日払い分")
                    .matchStatus("NA").build());
            rows.add(PaymentMfPreviewRow.builder()
                    .ruleKind("SUMMARY")
                    .sourceName("早払収益")
                    .amount(early)
                    .debitAccount("資金複合").debitTax("対象外").debitAmount(early)
                    .creditAccount("早払収益").creditDepartment("物販事業部")
                    .creditTax("非課税売上").creditAmount(early)
                    .summary("早払収益／" + d + "日払い分")
                    .matchStatus("NA").build());
        }

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
                        .build();

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
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3178ms:
    @Builder
    public static class VerifyResult {
        private LocalDate transferDate;
        private LocalDate transactionMonth;
        private int matchedCount;
        private int diffCount;
        private int notFoundCount;
        private int skippedCount;
        private List<String> unmatchedSuppliers;
    }

    public byte[] getHistoryCsv(Integer id) {
        return historyRepository.findById(id)
                .map(TPaymentMfImportHistory::getCsvBody)
                .orElseThrow(() -> new IllegalArgumentException("履歴が見つかりません: " + id));
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
            throw new IllegalStateException(
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

            // PAYABLE 行の取引日は mf_transfer_date (= Excel 振込明細の送金日) を優先。
            // 同一 supplier に複数税率行があって mf_transfer_date がバラバラのケースは想定外だが、
            // 最初の非 null を採用する (振込明細一括検証時は全税率行に同じ値が入る)。
            java.time.LocalDate txDate = group.stream()
                    .map(TAccountsPayableSummary::getMfTransferDate)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            String sourceName = rule.getSourceName();
            PaymentMfPreviewRow row = PaymentMfPreviewRow.builder()
                    .paymentSupplierCode(s.getSupplierCode())
                    .sourceName(sourceName)
                    .amount(amount)
                    .transactionDate(txDate)
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
        // aux.transferDate (Excel 振込明細の送金日) を取引日として使う。
        for (TPaymentMfAuxRow aux : auxRows) {
            long amount = aux.getAmount() == null ? 0L : toLongFloor(aux.getAmount());
            totalAmount += amount;
            csvRows.add(PaymentMfPreviewRow.builder()
                    .paymentSupplierCode(aux.getPaymentSupplierCode())
                    .sourceName(aux.getSourceName())
                    .amount(amount)
                    .transactionDate(aux.getTransferDate())
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
            throw new IllegalStateException("CSV 出力可能な行がありません（ルール未登録のみ・補助行なし）: " + skipped);
        }

        // CSV 取引日列は 締め日 = transactionMonth を使用
        byte[] csv = PaymentMfCsvWriter.toCsvBytes(csvRows, transactionMonth);
        try {
            // REQUIRES_NEW を proxy 経由で発動させるため self. で自己呼び出しする
            // (this. だと Spring AOP を経由せず REQUIRES_NEW が無視され、履歴保存失敗時に
            //  本体 tx が巻き戻って手動検証結果が失われる)。
            self.saveVerifiedExportHistory(transactionMonth, csvRows.size(), totalAmount, csv, userNo);
        } catch (Exception e) {
            log.error("検証済み CSV 出力履歴の保存に失敗（CSV は正常完了）: transactionMonth={}", transactionMonth, e);
        }

        return VerifiedExportResult.builder()
                .csv(csv)
                .rowCount(csvRows.size())
                .payableCount(payableCount)
                .auxCount(auxCount)
                .totalAmount(totalAmount)
                .skippedSuppliers(skipped)
                .transactionMonth(transactionMonth)
                .build();
    }

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

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Path backend\\src\\main\\resources\\db\\migration -Filter '*payment_mf*' | Select-Object -ExpandProperty Name" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path frontend\\components\\pages\\finance\\VerifiedCsvExportDialog.tsx" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path frontend\\components\\pages\\finance\\payment-mf-import.tsx" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path frontend\\components\\pages\\finance\\PaymentMfAuxRowsTable.tsx" in C:\project\odamitsu-data-hub
 succeeded in 1959ms:
'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api, ApiError } from '@/lib/api-client'
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
import { FileDown } from 'lucide-react'
import type { VerifiedExportPreview } from '@/types/payment-mf'

interface Props {
  open: boolean
  onOpenChange: (o: boolean) => void
  transactionMonth: string
}

/**
 * 検証済み買掛金データから直接 MF 仕訳 CSV を生成するダイアログ。
 * <p>PAYABLE + 補助行(EXPENSE/SUMMARY/DIRECT_PURCHASE) を結合した完全な MF CSV を出力。
 * CSV 取引日列は 小田光の締め日(前月20日) = transactionMonth 固定。
 * 支払日は MF の銀行データ連携で自動付与されるため CSV には含めない。
 */
export function VerifiedCsvExportDialog({
  open,
  onOpenChange,
  transactionMonth,
}: Props) {
  const [downloading, setDownloading] = useState(false)

  const previewQuery = useQuery({
    queryKey: ['verified-export-preview', transactionMonth],
    queryFn: () => {
      const sp = new URLSearchParams()
      sp.set('transactionMonth', transactionMonth)
      return api.get<VerifiedExportPreview>(`/finance/payment-mf/export-verified/preview?${sp.toString()}`)
    },
    enabled: open,
    staleTime: 10_000,
  })

  const download = async () => {
    setDownloading(true)
    try {
      const sp = new URLSearchParams()
      sp.set('transactionMonth', transactionMonth)
      const { blob, filename, headers } = await api.download(
        `/finance/payment-mf/export-verified?${sp.toString()}`,
      )
      const yyyymmdd = transactionMonth.replaceAll('-', '')
      const suggest = filename ?? `買掛仕入MFインポートファイル_${yyyymmdd}.csv`
      const a = document.createElement('a')
      const url = URL.createObjectURL(blob)
      a.href = url
      a.download = suggest
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      // revoke は非同期ダウンロード完了を待つ（同期 revoke は大容量 CSV で稀に失敗する）
      setTimeout(() => URL.revokeObjectURL(url), 1000)

      const rowCount = headers.get('X-Row-Count') ?? '?'
      const totalAmountNum = Number(headers.get('X-Total-Amount'))
      const totalAmountStr = Number.isFinite(totalAmountNum) ? totalAmountNum.toLocaleString() : '?'
      const skippedCount = Number(headers.get('X-Skipped-Count') ?? 0)
      const skippedRaw = headers.get('X-Skipped-Suppliers')
      // バックエンドは "|" 区切りで supplier 名に含まれるカンマとの衝突を回避し、
      // 各要素は encodeURIComponent 済み。デコード失敗時は生テキストを提示する。
      let skipped = ''
      if (skippedRaw) {
        skipped = skippedRaw.split('|')
          .map((seg) => { try { return decodeURIComponent(seg) } catch { return seg } })
          .join(', ')
      }
      toast.success(`CSV出力: ${rowCount}件 / 合計 ${totalAmountStr}円`)
      if (skippedCount > 0) {
        toast.warning(`ルール未登録のため除外: ${skippedCount}件\n${skipped}`)
      }
      onOpenChange(false)
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : (e as Error).message
      toast.error(`ダウンロード失敗: ${msg}`)
    } finally {
      setDownloading(false)
    }
  }

  const preview = previewQuery.data
  const auxTotalRows = preview?.auxBreakdown.reduce((sum, b) => sum + b.count, 0) ?? 0

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>検証済み買掛金からMF CSV出力</DialogTitle>
        </DialogHeader>
        <div className="space-y-3 text-sm">
          <p className="text-xs text-muted-foreground">
            <b>買掛金一覧（検証結果=一致 かつ MF出力=ON）</b>と
            <b>MF補助行（振込明細で一括検証した EXPENSE/SUMMARY/DIRECT_PURCHASE）</b>
            を結合して MF仕訳CSV を生成します。
          </p>
          <div>
            <Label>対象取引月（CSV 取引日列）</Label>
            <Input value={transactionMonth} readOnly disabled className="bg-muted" />
            <p className="text-[11px] text-muted-foreground mt-1">
              小田光の締め日(前月20日)。支払日は MF の銀行データ連携で自動付与されるため CSV には含みません。
            </p>
          </div>

          {previewQuery.isLoading && (
            <div className="text-xs text-muted-foreground">内訳を集計中...</div>
          )}
          {preview && (
            <div className="rounded border p-3 space-y-2 text-xs">
              <div className="font-medium">内訳</div>
              <div className="flex justify-between">
                <span>買掛金（一致 + MF出力ON）</span>
                <span className="tabular-nums">
                  {preview.payableCount}件 / {preview.payableTotalAmount.toLocaleString()}円
                </span>
              </div>
              {preview.auxBreakdown.length === 0 ? (
                <div className="text-amber-700">
                  補助行なし（振込明細Excelで一括検証するとここに内訳が表示されます）
                </div>
              ) : (
                <>
                  {preview.auxBreakdown.map((b) => (
                    <div key={`${b.transferDate}-${b.ruleKind}`} className="flex justify-between">
                      <span>
                        補助行 {b.transferDate} {b.ruleKind}
                      </span>
                      <span className="tabular-nums">
                        {b.count}件 / {b.totalAmount.toLocaleString()}円
                      </span>
                    </div>
                  ))}
                </>
              )}
              <div className="border-t pt-1 flex justify-between font-medium">
                <span>CSV 行数合計</span>
                <span className="tabular-nums">{preview.payableCount + auxTotalRows}件</span>
              </div>
              {preview.warnings.length > 0 && (
                <div className="mt-2 space-y-1">
                  {preview.warnings.map((w) => (
                    <div key={w} className="text-[11px] text-amber-800 bg-amber-50 rounded border border-amber-300 p-1.5">
                      ?? {w}
                    </div>
                  ))}
                </div>
              )}
              {preview.skippedSuppliers.length > 0 && (
                <details className="mt-2 text-[11px] text-red-800">
                  <summary className="cursor-pointer select-none">
                    ルール未登録でCSV除外: {preview.skippedSuppliers.length}件
                    <span className="ml-1 text-muted-foreground">（クリックで内訳表示）</span>
                  </summary>
                  <ul className="mt-1 ml-4 list-disc space-y-0.5">
                    {preview.skippedSuppliers.map((s, i) => (
                      <li key={`${s}-${i}`} className="font-mono">{s}</li>
                    ))}
                  </ul>
                  <p className="mt-1 text-[10px] text-muted-foreground">
                    対応: <b>買掛仕入MFルール マスタ</b>（/finance/payment-mf-rules）で
                    supplier_code を指定した PAYABLE ルールを追加すると、次回から CSV に含まれます。
                  </p>
                </details>
              )}
            </div>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={downloading}>
            キャンセル
          </Button>
          <Button onClick={download} disabled={downloading || !preview || (preview.payableCount + auxTotalRows) === 0}>
            <FileDown className="mr-1 h-4 w-4" />
            {downloading ? '生成中...' : 'CSVダウンロード'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2026ms:
'use client'

import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { PageHeader } from '@/components/features/common/PageHeader'
import { ConfirmDialog } from '@/components/features/common/ConfirmDialog'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import {
  Select, SelectTrigger, SelectValue, SelectContent, SelectItem,
} from '@/components/ui/select'
import { Download, Upload, AlertCircle, Scale, History, CheckCheck } from 'lucide-react'
import Link from 'next/link'
import { toast } from 'sonner'
import type {
  PaymentMfPreviewResponse, PaymentMfPreviewRow,
  PaymentMfRule, PaymentMfRuleRequest, RuleKind,
  PaymentMfVerifyResult,
} from '@/types/payment-mf'
import { RULE_KINDS, TAX_CATEGORIES } from '@/types/payment-mf'

type RuleDialogState = { sourceName: string; amount: number | null } | null

export default function PaymentMfImportPage() {
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<PaymentMfPreviewResponse | null>(null)
  const [ruleDialog, setRuleDialog] = useState<RuleDialogState>(null)
  const [form, setForm] = useState<PaymentMfRuleRequest>(blankRuleRequest())
  const [confirmVerify, setConfirmVerify] = useState(false)

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
    onError: (e: Error) => toast.error(e.message),
  })

  const rePreviewMut = useMutation({
    mutationFn: async (uploadId: string) =>
      api.post<PaymentMfPreviewResponse>(`/finance/payment-mf/preview/${uploadId}`),
    onSuccess: (r) => {
      setPreview(r)
      if (r.errorCount === 0) toast.success('すべてのエラーが解消されました')
    },
    onError: (e: Error) => toast.error(e.message),
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
    onError: (e: Error) => toast.error(e.message),
  })

  const verifyMut = useMutation({
    mutationFn: async (uploadId: string) =>
      api.post<PaymentMfVerifyResult>(`/finance/payment-mf/verify/${uploadId}`),
    onSuccess: (r) => {
      toast.success(
        `買掛金一覧に反映しました（一致 ${r.matchedCount} / 差異 ${r.diffCount} / 買掛金なし ${r.notFoundCount}）`
      )
    },
    onError: (e: Error) => toast.error(`反映失敗: ${e.message}`),
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
      toast.error(`ダウンロード失敗: ${(e as Error).message}`)
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
        <div className="rounded bg-muted/50 p-3 text-xs space-y-2 leading-relaxed">
          <div className="font-medium text-foreground">どのファイルを選べばいい？</div>
          <ul className="list-disc pl-5 space-y-1">
            <li>
              経理が作成した <b>「振込み明細〇〇.xlsx」</b>（例: <code>振込み明細08-2-5.xlsx</code>）を
              そのままアップロードしてください。ファイル名の末尾 <code>-5</code> が5日払い、
              <code>-20</code> が20日払いです（シートは自動判別）。
            </li>
            <li>
              保管場所の目安:
              <br />
              <code>\\Smile-srv\共有\BKUP-001\Documents\Documents\買掛関係\支払\{'{'}年{'}'}\{'{'}支払月{'}'}\</code>
            </li>
            <li>
              取引月（買掛金突合先）の決まり方:
              <ul className="list-[circle] pl-5">
                <li>
                  送金日が <b>5日・20日</b> いずれも <b>前月20日締め</b> の買掛が対象です。
                </li>
                <li>例: 2026/1/5 支払 → 取引月 <b>2025-12-20</b></li>
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
                  <th className="p-1 text-right">請求額</th>
                  <th className="p-1 text-right">買掛金</th>
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
        title="買掛金一覧へ反映"
        description="この突合結果で買掛金一覧を検証確定します。よろしいですか？（verified_manually=true として手動確定扱いになります）"
        confirmLabel="反映する"
        onConfirm={() => preview && verifyMut.mutate(preview.uploadId)}
      />
    </div>
  )
}

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
  if (status === 'MATCHED') return <span className="text-green-700">?? 一致</span>
  if (status === 'DIFF')
    return supplierNo && txMonth ? (
      <Link
        className="text-amber-700 underline"
        href={`/finance/accounts-payable?supplierNo=${supplierNo}&transactionMonth=${txMonth}`}
      >
        ?? 差異
      </Link>
    ) : <span className="text-amber-700">?? 差異</span>
  if (status === 'UNMATCHED') return <span className="text-red-600">?? 買掛金なし</span>
  if (status === 'NA') return <span className="text-muted-foreground">対象外</span>
  return <span className="text-muted-foreground">?</span>
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2944ms:
'use client'

import { useEffect, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { Badge } from '@/components/ui/badge'
import { formatCurrency } from '@/lib/utils'
import type { PaymentMfAuxRow, AuxRuleKind } from '@/types/payment-mf'

interface Props {
  transactionMonth: string // yyyy-MM-dd
  onCountChange?: (count: number) => void
}

const RULE_KIND_LABELS: Record<AuxRuleKind, string> = {
  EXPENSE: 'EXPENSE',
  SUMMARY: 'SUMMARY',
  DIRECT_PURCHASE: 'DIRECT_PURCHASE',
}

function AuxRuleKindBadge({ kind }: { kind: AuxRuleKind }) {
  const classes: Record<AuxRuleKind, string> = {
    EXPENSE: 'bg-blue-100 text-blue-800 hover:bg-blue-200',
    SUMMARY: 'bg-amber-100 text-amber-800 hover:bg-amber-200',
    DIRECT_PURCHASE: 'bg-purple-100 text-purple-800 hover:bg-purple-200',
  }
  return <Badge className={classes[kind]}>{RULE_KIND_LABELS[kind]}</Badge>
}

export function PaymentMfAuxRowsTable({ transactionMonth, onCountChange }: Props) {
  const query = useQuery({
    queryKey: ['payment-mf-aux-rows', transactionMonth],
    queryFn: () => {
      const sp = new URLSearchParams()
      sp.set('transactionMonth', transactionMonth)
      return api.get<PaymentMfAuxRow[]>(`/finance/payment-mf/aux-rows?${sp.toString()}`)
    },
    staleTime: 30_000,
  })

  // onCountChange は親側のインライン関数である可能性があるため ref に保持し、
  // コールバック参照の変化で effect が再発火するのを避ける（依存配列は data のみ）。
  const onCountChangeRef = useRef(onCountChange)
  useEffect(() => {
    onCountChangeRef.current = onCountChange
  }, [onCountChange])

  useEffect(() => {
    if (query.data) onCountChangeRef.current?.(query.data.length)
  }, [query.data])

  const columns: Column<PaymentMfAuxRow>[] = [
    { key: 'transferDate', header: '送金日', sortable: true },
    {
      key: 'ruleKind',
      header: '種別',
      render: (r) => <AuxRuleKindBadge kind={r.ruleKind} />,
    },
    { key: 'sourceName', header: '送り先', sortable: true },
    { key: 'debitAccount', header: '借方勘定' },
    { key: 'debitDepartment', header: '借方部門', render: (r) => r.debitDepartment ?? '' },
    { key: 'debitTax', header: '借方税区分' },
    {
      key: 'amount',
      header: '金額',
      render: (r) => <span className="tabular-nums">{formatCurrency(r.amount)}</span>,
    },
    { key: 'creditAccount', header: '貸方勘定' },
    { key: 'creditTax', header: '貸方税区分' },
    { key: 'summary', header: '摘要', render: (r) => r.summary ?? '' },
    {
      key: 'sourceFilename',
      header: 'ソース',
      render: (r) => (
        <span className="text-xs text-muted-foreground" title={r.sourceFilename ?? ''}>
          {r.sourceFilename ?? '-'}
        </span>
      ),
    },
  ]

  if (query.isLoading) return <LoadingSpinner />
  if (query.isError) return <ErrorMessage onRetry={() => query.refetch()} />

  const rows = query.data ?? []

  if (rows.length === 0) {
    return (
      <div className="rounded border border-amber-300 bg-amber-50 p-6 text-sm text-amber-900">
        <p className="font-medium">この取引月の補助行は登録されていません。</p>
        <p className="mt-2">
          振込明細Excel を「振込明細で一括検証」からアップロードすると、
          EXPENSE/SUMMARY/DIRECT_PURCHASE 行が自動的に保存されます。
        </p>
      </div>
    )
  }

  return <DataTable data={rows} columns={columns} rowKey={(r) => r.auxRowId} />
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 4013ms:
V011__create_payment_mf_tables.sql
V016__create_payment_mf_aux_row.sql
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\resources\\db\\migration\\V011__create_payment_mf_tables.sql | Select-Object -First 220" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\resources\\db\\migration\\V016__create_payment_mf_aux_row.sql" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfRuleService.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path frontend\\types\\payment-mf.ts" in C:\project\odamitsu-data-hub
 succeeded in 1844ms:
-- 2026-04-16: 買掛仕入MF 補助行テーブル新設
-- EXPENSE (荷造運賃・消耗品費・車両費等) / SUMMARY (振込手数料値引・早払収益) /
-- DIRECT_PURCHASE (20日払いセクション仕入高) を保持し、検証済みCSV出力から
-- PAYABLE(t_accounts_payable_summary) と結合して完全な MF 仕訳CSVを再生成する。
-- 設計書: claudedocs/design-payment-mf-aux-rows.md

CREATE TABLE IF NOT EXISTS t_payment_mf_aux_row (
    aux_row_id            BIGSERIAL PRIMARY KEY,
    shop_no               INTEGER      NOT NULL DEFAULT 1,
    transaction_month     DATE         NOT NULL,
    transfer_date         DATE         NOT NULL,
    rule_kind             VARCHAR(20)  NOT NULL,
    sequence_no           INTEGER      NOT NULL,
    source_name           VARCHAR(255) NOT NULL,
    payment_supplier_code VARCHAR(20),
    amount                NUMERIC      NOT NULL,
    debit_account         VARCHAR(50)  NOT NULL,
    debit_sub_account     VARCHAR(50),
    debit_department      VARCHAR(50),
    debit_tax             VARCHAR(30)  NOT NULL,
    credit_account        VARCHAR(50)  NOT NULL,
    credit_sub_account    VARCHAR(50),
    credit_department     VARCHAR(50),
    credit_tax            VARCHAR(30)  NOT NULL,
    summary               VARCHAR(255),
    tag                   VARCHAR(50),
    source_filename       VARCHAR(255),
    add_date_time         TIMESTAMP    NOT NULL,
    add_user_no           INTEGER,
    modify_date_time      TIMESTAMP,
    modify_user_no        INTEGER,
    CONSTRAINT chk_payment_mf_aux_rule_kind
        CHECK (rule_kind IN ('EXPENSE','SUMMARY','DIRECT_PURCHASE'))
);

CREATE INDEX IF NOT EXISTS idx_payment_mf_aux_tx_month
    ON t_payment_mf_aux_row(shop_no, transaction_month);

CREATE INDEX IF NOT EXISTS idx_payment_mf_aux_transfer
    ON t_payment_mf_aux_row(shop_no, transaction_month, transfer_date);

COMMENT ON TABLE  t_payment_mf_aux_row IS '買掛仕入MF 補助行 (EXPENSE/SUMMARY/DIRECT_PURCHASE)。振込明細Excel applyVerification 時に (shop_no, transaction_month, transfer_date) 単位で洗い替え保存。検証済みCSV出力で PAYABLE と結合される。';
COMMENT ON COLUMN t_payment_mf_aux_row.transaction_month IS '小田光締め日(前月20日)。CSV 取引日列にも使用。';
COMMENT ON COLUMN t_payment_mf_aux_row.transfer_date     IS '出処 Excel の送金日 (5日 or 20日)。同一 (transaction_month, transfer_date) で再アップロードされたら物理削除→再挿入で洗い替え。';
COMMENT ON COLUMN t_payment_mf_aux_row.rule_kind         IS 'EXPENSE / SUMMARY / DIRECT_PURCHASE';
COMMENT ON COLUMN t_payment_mf_aux_row.sequence_no       IS 'Excel 内の出現順 (CSV 出力順序維持用)';
COMMENT ON COLUMN t_payment_mf_aux_row.source_filename   IS 'トレーサビリティ用 Excel ファイル名';
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1911ms:
-- 振込み明細Excel → MoneyForward買掛仕入CSV 変換用マスタ/履歴テーブル

-- ルールマスタ（送り先名 or 支払先コードから仕訳属性を決定）
CREATE TABLE IF NOT EXISTS m_payment_mf_rule (
    id                      SERIAL PRIMARY KEY,
    source_name             VARCHAR(200) NOT NULL,   -- 振込明細 B列(送り先名)
    payment_supplier_code   VARCHAR(20),             -- m_payment_supplier.payment_supplier_code（NULLなら固定費行）
    rule_kind               VARCHAR(20)  NOT NULL,   -- 'PAYABLE' | 'EXPENSE' | 'DIRECT_PURCHASE'

    debit_account           VARCHAR(50)  NOT NULL,
    debit_sub_account       VARCHAR(100),
    debit_department        VARCHAR(50),
    debit_tax_category      VARCHAR(30)  NOT NULL,

    credit_account          VARCHAR(50)  NOT NULL DEFAULT '資金複合',
    credit_sub_account      VARCHAR(100),
    credit_department       VARCHAR(50),
    credit_tax_category     VARCHAR(30)  NOT NULL DEFAULT '対象外',

    summary_template        VARCHAR(200) NOT NULL,   -- {sub_account} / {source_name} プレースホルダ対応
    tag                     VARCHAR(100),
    priority                INTEGER      NOT NULL DEFAULT 100,

    del_flg          VARCHAR(1) NOT NULL DEFAULT '0',
    add_date_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    add_user_no      INTEGER,
    modify_date_time TIMESTAMP,
    modify_user_no   INTEGER
);
CREATE INDEX IF NOT EXISTS idx_payment_mf_rule_source ON m_payment_mf_rule(source_name) WHERE del_flg = '0';
CREATE INDEX IF NOT EXISTS idx_payment_mf_rule_code ON m_payment_mf_rule(payment_supplier_code) WHERE del_flg = '0';

-- 変換履歴
CREATE TABLE IF NOT EXISTS t_payment_mf_import_history (
    id                SERIAL PRIMARY KEY,
    shop_no           INTEGER      NOT NULL,
    transfer_date     DATE         NOT NULL,
    source_filename   VARCHAR(255) NOT NULL,
    csv_filename      VARCHAR(255) NOT NULL,
    row_count         INTEGER      NOT NULL,
    total_amount      BIGINT       NOT NULL,
    matched_count     INTEGER      NOT NULL DEFAULT 0,
    diff_count        INTEGER      NOT NULL DEFAULT 0,
    unmatched_count   INTEGER      NOT NULL DEFAULT 0,
    csv_body          BYTEA,
    del_flg           VARCHAR(1)   NOT NULL DEFAULT '0',
    add_date_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    add_user_no       INTEGER,
    modify_date_time  TIMESTAMP,
    modify_user_no    INTEGER
);
CREATE INDEX IF NOT EXISTS idx_payment_mf_history_date ON t_payment_mf_import_history(transfer_date DESC) WHERE del_flg = '0';

-- ======================================================================
-- Seed: PAYABLE ルール（変換MAPシートから93件）
-- ======================================================================
INSERT INTO m_payment_mf_rule
    (source_name, payment_supplier_code, rule_kind, debit_account, debit_sub_account, debit_department, debit_tax_category,
     credit_account, credit_sub_account, credit_department, credit_tax_category, summary_template, tag, priority)
VALUES
    ('ぬまご', NULL, 'PAYABLE', '買掛金', '（株）ぬまご', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('イトマン ㈱', NULL, 'PAYABLE', '買掛金', 'イトマン（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('オルディ 株式会社', NULL, 'PAYABLE', '買掛金', 'オルディ株式会社', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('カネカ食品販売 ㈱', NULL, 'PAYABLE', '買掛金', 'カネカ食品販売 ㈱', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('カミイソ産商 ㈱', NULL, 'PAYABLE', '買掛金', 'カミイソ産商（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('カミ商事', NULL, 'PAYABLE', '買掛金', 'カミ商事（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('キンセイ', NULL, 'PAYABLE', '買掛金', '（株）キンセイ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ケイティケイ ㈱', NULL, 'PAYABLE', '買掛金', 'ｋｔｋ ケイティケイ（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('コトブキ製紙', NULL, 'PAYABLE', '買掛金', 'コトブキ製紙（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ゴークラ', NULL, 'PAYABLE', '買掛金', 'ゴークラ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('シモジマ', NULL, 'PAYABLE', '買掛金', '（株）シモジマ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('シャディ', NULL, 'PAYABLE', '買掛金', 'シャディ（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('シルバー化成', NULL, 'PAYABLE', '買掛金', '（有）シルバー化成工業所', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('シーバイエス ㈱', NULL, 'PAYABLE', '買掛金', 'シーバイエス（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('シーピー化成 ㈱', NULL, 'PAYABLE', '買掛金', 'シービー化成（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ツバメ工業', NULL, 'PAYABLE', '買掛金', 'ツバメ工業（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ツボイ', NULL, 'PAYABLE', '買掛金', 'ツボイ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('トーヨ', NULL, 'PAYABLE', '買掛金', '（株）トーヨ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ナカパック産業 ㈱', NULL, 'PAYABLE', '買掛金', 'ナカパック産業 ㈱', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('ハヤシ商事', NULL, 'PAYABLE', '買掛金', 'ハヤシ商事（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ヒノマル', NULL, 'PAYABLE', '買掛金', 'ヒノマル（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('フクヤ', NULL, 'PAYABLE', '買掛金', '（株）フクヤ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('フレンド', NULL, 'PAYABLE', '買掛金', '（株）フレンド', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ホリアキ 株式会社', NULL, 'PAYABLE', '買掛金', 'ホリアキ（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ユニ・チャーム ㈱', NULL, 'PAYABLE', '買掛金', 'ユニ・チャーム(株)', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ララ', NULL, 'PAYABLE', '買掛金', '（株）ララ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ ちどり産業', NULL, 'PAYABLE', '買掛金', '㈱ ちどり産業', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ やしき', NULL, 'PAYABLE', '買掛金', '（株）やしき', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ よし与工房', NULL, 'PAYABLE', '買掛金', '（株）よし与工房', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ アルファ', NULL, 'PAYABLE', '買掛金', '㈱ アルファ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ ササガワ', NULL, 'PAYABLE', '買掛金', '㈱ ササガワ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ スズカ未来', NULL, 'PAYABLE', '買掛金', '（株）スズカ未来', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ セイコー', NULL, 'PAYABLE', '買掛金', '（株）セイコー', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ ニューパック住友', NULL, 'PAYABLE', '買掛金', '㈱ ニューパック住友', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ パックタケヤマ', NULL, 'PAYABLE', '買掛金', '（株）パックタケヤマ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ フォーデック', NULL, 'PAYABLE', '買掛金', '㈱ フォーデック', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ プライム.ハラ', NULL, 'PAYABLE', '買掛金', '㈱ プライム.ハラ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ ベルベ', NULL, 'PAYABLE', '買掛金', '㈱ ベルベ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ ユタカフードパック', NULL, 'PAYABLE', '買掛金', '㈱ ユタカフードパック', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ 中国リス食品販売', NULL, 'PAYABLE', '買掛金', '㈱ 中国リス食品販売', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ 中村製紙所', NULL, 'PAYABLE', '買掛金', '㈱ 中村製紙所', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ 丸三', NULL, 'PAYABLE', '買掛金', '（株）丸三', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ 四国フソウ', NULL, 'PAYABLE', '買掛金', '㈱ 四国フソウ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ 大阪包装社', NULL, 'PAYABLE', '買掛金', '（株）大阪包装社', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ 太幸', NULL, 'PAYABLE', '買掛金', '（株）太幸', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ 奈良半商店', NULL, 'PAYABLE', '買掛金', '㈱ 奈良半商店', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ 尚美堂', NULL, 'PAYABLE', '買掛金', '（株）尚美堂', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈱ 福重', NULL, 'PAYABLE', '買掛金', '㈱ 福重', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ 藤井包材', NULL, 'PAYABLE', '買掛金', '㈱ 藤井包材', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈱ ﾊﾗﾌﾟﾚｯｸｽ松山支店', NULL, 'PAYABLE', '買掛金', '（株）ハラプレックス', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('㈲ タイヨー', NULL, 'PAYABLE', '買掛金', '㈲ タイヨー', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈲ トックブランシュ', NULL, 'PAYABLE', '買掛金', '㈲ トックブランシュ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈲ 東予割箸', NULL, 'PAYABLE', '買掛金', '㈲ 東予割箸', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('三友商事', NULL, 'PAYABLE', '買掛金', '三友商事（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('三昭紙業', NULL, 'PAYABLE', '買掛金', '三昭紙業（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('三木商事', NULL, 'PAYABLE', '買掛金', '三木商事（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('三興化学工業', NULL, 'PAYABLE', '買掛金', '三興化学工業（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('中国鉄管継手㈱', NULL, 'PAYABLE', '買掛金', '中国鉄管継手㈱', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('中川製袋化工 ㈱', NULL, 'PAYABLE', '買掛金', '中川製袋化工（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('中日綿業', NULL, 'PAYABLE', '買掛金', '中日綿業（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('信和 ㈱', NULL, 'PAYABLE', '買掛金', '信和 ㈱', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('信越ファインテック ㈱', NULL, 'PAYABLE', '買掛金', '信越ファインテック（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('八家化学工業 ㈱', NULL, 'PAYABLE', '買掛金', '八家化学工業 ㈱', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('八幡浜紙業', NULL, 'PAYABLE', '買掛金', '八幡浜紙業（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('前谷紙工業', NULL, 'PAYABLE', '買掛金', '前谷紙工業（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('大高製紙', NULL, 'PAYABLE', '買掛金', '大高製紙（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('大黒工業㈱', NULL, 'PAYABLE', '買掛金', '大黒工業（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('天満紙器 株式会社', NULL, 'PAYABLE', '買掛金', '天満紙器（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('広島共和物産㈱', NULL, 'PAYABLE', '買掛金', '広島共和物産（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('広川 ㈱', NULL, 'PAYABLE', '買掛金', '広川 ㈱', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('日本製紙クレシア', NULL, 'PAYABLE', '買掛金', '日本製紙クレシア（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('旭創業 ㈱', NULL, 'PAYABLE', '買掛金', '（株）旭創業', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('木村アルミ箔 ㈱', NULL, 'PAYABLE', '買掛金', '木村アルミ箔（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('木野川紙業 株式会社', NULL, 'PAYABLE', '買掛金', '木野川紙業（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('東光 株式会社', NULL, 'PAYABLE', '買掛金', '（株）東光', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('東芝エルイーソリューション㈱', NULL, 'PAYABLE', '買掛金', '東芝エルイーソリューション㈱', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('松田製袋', NULL, 'PAYABLE', '買掛金', '（株）松田製袋', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('柳井紙工', NULL, 'PAYABLE', '買掛金', '柳井紙工（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('森川 ㈱', NULL, 'PAYABLE', '買掛金', '森川（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('河野製紙', NULL, 'PAYABLE', '買掛金', '河野製紙（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('泉 製紙 ㈱', NULL, 'PAYABLE', '買掛金', '泉製紙（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('王子ネピア 広島', NULL, 'PAYABLE', '買掛金', '王子ネピア（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('王子ネピア 直需', NULL, 'PAYABLE', '買掛金', '王子ネピア（直需）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('王子製袋', NULL, 'PAYABLE', '買掛金', '王子製袋（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('田宮事務機', NULL, 'PAYABLE', '買掛金', '（株）田宮事務器', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('福助工業㈱', NULL, 'PAYABLE', '買掛金', '福助工業（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('立石春洋堂', NULL, 'PAYABLE', '買掛金', '（株）立石春洋堂', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('花王 業務品 KPS', NULL, 'PAYABLE', '買掛金', '花王プロフェショナルｻｰﾋﾞｽ', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('花王 業務品 KPS施設', NULL, 'PAYABLE', '買掛金', '花王 業務品 KPS施設', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('西日本衛材', NULL, 'PAYABLE', '買掛金', '西日本衛材（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('酒井美化工業 ㈱', NULL, 'PAYABLE', '買掛金', '酒井美化工業 ㈱', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('金星製紙', NULL, 'PAYABLE', '買掛金', '金星製紙（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100),
    ('ＡＲＣ', NULL, 'PAYABLE', '買掛金', 'ＡＲＣ（株）', NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{sub_account}', NULL, 100);

-- ======================================================================
-- Seed: 固定費ルール（EXPENSE / DIRECT_PURCHASE, 過去CSVから抽出）
-- ======================================================================
INSERT INTO m_payment_mf_rule
    (source_name, payment_supplier_code, rule_kind, debit_account, debit_sub_account, debit_department, debit_tax_category,
     credit_account, credit_sub_account, credit_department, credit_tax_category, summary_template, tag, priority)
VALUES
    -- 運賃（課税仕入10%）
    ('福山通運',                   NULL, 'EXPENSE', '荷造運賃', NULL, '物販事業部', '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', '物販部 運賃', 100),
    ('福山通運 第３事業部',        NULL, 'EXPENSE', '荷造運賃', NULL, '物販事業部', '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('サクマ運輸㈱',               NULL, 'EXPENSE', '荷造運賃', NULL, '物販事業部', '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', '物販部 運賃', 100),
    ('ティックトランスポート㈲',   NULL, 'EXPENSE', '荷造運賃', NULL, NULL,         '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', '物販部 運賃', 100),
    -- 仕入高（その他セクション）
    ('ヨハネ印刷㈱',               NULL, 'DIRECT_PURCHASE', '仕入高', NULL, NULL, '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', '物販部 仕入 ', 100),
    ('㈱ ナカガワ',                NULL, 'DIRECT_PURCHASE', '仕入高', NULL, NULL, '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', '物販部 仕入 ', 100),
    ('中国エンゼル㈱',             NULL, 'DIRECT_PURCHASE', '仕入高', NULL, NULL, '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', '物販部 仕入 ', 100),
    ('㈱ ビバ',                    NULL, 'DIRECT_PURCHASE', '仕入高', NULL, NULL, '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    -- 消耗品費 / 車両費
    ('リコージャパン㈱',           NULL, 'EXPENSE', '消耗品費', NULL, NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', '物販部 事務用品費', 100),
    ('広島トヨペット㈱',           NULL, 'EXPENSE', '車両費',   NULL, NULL, '対象外', '資金複合', NULL, NULL, '対象外', '{source_name}', '車両費', 100),
    -- 20日払いセクション（仕入高・課税仕入10%）
    ('ワタキューセイモア㈱',       NULL, 'DIRECT_PURCHASE', '仕入高', NULL, NULL, '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('ハウスホールドジャパン㈱',   NULL, 'DIRECT_PURCHASE', '仕入高', NULL, NULL, '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('シンワ ㈱',                  NULL, 'DIRECT_PURCHASE', '仕入高', NULL, NULL, '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('㈲ シルバー化成工業所',      NULL, 'DIRECT_PURCHASE', '仕入高', NULL, NULL, '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100),
    ('アスト株式会社',             NULL, 'DIRECT_PURCHASE', '仕入高', NULL, NULL, '課税仕入 10%', '資金複合', NULL, NULL, '対象外', '{source_name}', NULL, 100);
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2873ms:
package jp.co.oda32.domain.service.finance;

import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.model.finance.MPaymentMfRule;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.repository.finance.MPaymentMfRuleRepository;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import jp.co.oda32.dto.finance.paymentmf.PaymentMfRuleRequest;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentMfRuleService {

    private final MPaymentMfRuleRepository repository;
    private final MPaymentSupplierService paymentSupplierService;

    public List<MPaymentMfRule> findAll() {
        return repository.findByDelFlgOrderByPriorityAscIdAsc("0");
    }

    @Transactional
    public MPaymentMfRule create(PaymentMfRuleRequest req, Integer userNo) {
        MPaymentMfRule rule = MPaymentMfRule.builder()
                .sourceName(req.getSourceName())
                .paymentSupplierCode(blankToNull(req.getPaymentSupplierCode()))
                .ruleKind(req.getRuleKind())
                .debitAccount(req.getDebitAccount())
                .debitSubAccount(blankToNull(req.getDebitSubAccount()))
                .debitDepartment(blankToNull(req.getDebitDepartment()))
                .debitTaxCategory(req.getDebitTaxCategory())
                .creditAccount(defaultStr(req.getCreditAccount(), "資金複合"))
                .creditSubAccount(blankToNull(req.getCreditSubAccount()))
                .creditDepartment(blankToNull(req.getCreditDepartment()))
                .creditTaxCategory(defaultStr(req.getCreditTaxCategory(), "対象外"))
                .summaryTemplate(req.getSummaryTemplate())
                .tag(blankToNull(req.getTag()))
                .priority(req.getPriority() == null ? 100 : req.getPriority())
                .delFlg("0")
                .addDateTime(LocalDateTime.now())
                .addUserNo(userNo)
                .build();
        return repository.save(rule);
    }

    @Transactional
    public MPaymentMfRule update(Integer id, PaymentMfRuleRequest req, Integer userNo) {
        MPaymentMfRule r = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ルールが見つかりません: " + id));
        r.setSourceName(req.getSourceName());
        r.setPaymentSupplierCode(blankToNull(req.getPaymentSupplierCode()));
        r.setRuleKind(req.getRuleKind());
        r.setDebitAccount(req.getDebitAccount());
        r.setDebitSubAccount(blankToNull(req.getDebitSubAccount()));
        r.setDebitDepartment(blankToNull(req.getDebitDepartment()));
        r.setDebitTaxCategory(req.getDebitTaxCategory());
        r.setCreditAccount(defaultStr(req.getCreditAccount(), "資金複合"));
        r.setCreditSubAccount(blankToNull(req.getCreditSubAccount()));
        r.setCreditDepartment(blankToNull(req.getCreditDepartment()));
        r.setCreditTaxCategory(defaultStr(req.getCreditTaxCategory(), "対象外"));
        r.setSummaryTemplate(req.getSummaryTemplate());
        r.setTag(blankToNull(req.getTag()));
        if (req.getPriority() != null) r.setPriority(req.getPriority());
        r.setModifyDateTime(LocalDateTime.now());
        r.setModifyUserNo(userNo);
        return repository.save(r);
    }

    @Transactional
    public void delete(Integer id, Integer userNo) {
        MPaymentMfRule r = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ルールが見つかりません: " + id));
        r.setDelFlg("1");
        r.setModifyDateTime(LocalDateTime.now());
        r.setModifyUserNo(userNo);
        repository.save(r);
    }

    /**
     * PAYABLE ルールのうち payment_supplier_code 未設定のものを、
     * m_payment_supplier の payment_supplier_name と名寄せして一括補完する。
     *
     * @param dryRun true の場合はDBを更新せず、マッチ結果のみ返す
     */
    @Transactional
    public BackfillResult backfillPaymentSupplierCodes(boolean dryRun, Integer userNo) {
        List<MPaymentMfRule> rules = repository.findByDelFlgOrderByPriorityAscIdAsc("0");
        List<MPaymentSupplier> suppliers = paymentSupplierService.findByShopNo(FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO);

        // 正規化名 → 候補支払先リスト（複数ヒット検知のため List）
        Map<String, List<MPaymentSupplier>> byNormName = new LinkedHashMap<>();
        for (MPaymentSupplier s : suppliers) {
            if (s.getPaymentSupplierName() == null) continue;
            String key = normalizeCompanyName(s.getPaymentSupplierName());
            if (key.isEmpty()) continue;
            byNormName.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        List<BackfillItem> items = new ArrayList<>();
        int matched = 0, ambiguous = 0, notFound = 0, skipped = 0;

        for (MPaymentMfRule rule : rules) {
            if (!"PAYABLE".equals(rule.getRuleKind())) { skipped++; continue; }
            if (rule.getPaymentSupplierCode() != null && !rule.getPaymentSupplierCode().isBlank()) {
                skipped++;
                continue;
            }
            String normSource = normalizeCompanyName(rule.getSourceName());

            // 完全一致 → 部分一致(include)の順に探索。
            // 部分一致は「マスタ名が振込明細(rule.sourceName)の部分文字列」の一方向のみ。
            // 両方向 contains にすると "カミイソ" と "カミイソ産商" が相互ヒットする過剰マッチが発生する。
            // 部分一致の最小桁数を 4 文字に引き上げて誤マッチをさらに抑制する
            // （3 文字だと "abc" を含むだけで無関係な会社にマッチするリスクがあるため）。
            List<MPaymentSupplier> cands = byNormName.getOrDefault(normSource, List.of());
            if (cands.isEmpty() && normSource.length() >= 4) {
                cands = new ArrayList<>();
                for (var e : byNormName.entrySet()) {
                    if (e.getKey().length() >= 4 && normSource.contains(e.getKey())) {
                        cands.addAll(e.getValue());
                    }
                }
            }
            // 正規化後4文字未満は誤マッチ温床のため一律マッチ対象外
            if (normSource.length() < 4) cands = List.of();

            BackfillItem.BackfillItemBuilder b = BackfillItem.builder()
                    .ruleId(rule.getId())
                    .sourceName(rule.getSourceName());

            if (cands.isEmpty()) {
                items.add(b.status("NOT_FOUND").build());
                notFound++;
            } else if (cands.size() == 1 || cands.stream().map(MPaymentSupplier::getPaymentSupplierCode).distinct().count() == 1) {
                // 候補が1件、または複数でも同一支払先コード（登録重複）なら採用
                MPaymentSupplier pick = cands.get(0);
                items.add(b.status("MATCHED")
                        .candidateCode(pick.getPaymentSupplierCode())
                        .candidateName(pick.getPaymentSupplierName())
                        .build());
                matched++;
                if (!dryRun) {
                    rule.setPaymentSupplierCode(pick.getPaymentSupplierCode());
                    rule.setModifyDateTime(LocalDateTime.now());
                    rule.setModifyUserNo(userNo);
                    repository.save(rule);
                }
            } else {
                cands.sort(Comparator.comparing(MPaymentSupplier::getPaymentSupplierCode));
                List<String> alts = new ArrayList<>();
                for (MPaymentSupplier s : cands) {
                    alts.add(s.getPaymentSupplierCode() + " " + s.getPaymentSupplierName());
                }
                items.add(b.status("AMBIGUOUS").alternatives(alts).build());
                ambiguous++;
            }
        }

        return BackfillResult.builder()
                .dryRun(dryRun)
                .matchedCount(matched)
                .ambiguousCount(ambiguous)
                .notFoundCount(notFound)
                .skippedCount(skipped)
                .items(items)
                .build();
    }

    /**
     * 会社名表記ゆれ（全半角・株式会社/㈱・支店記号[20]など）を吸収した正規化名を返す。
     * マスタ名寄せ専用。UI上の検索正規化よりも厳しめ。
     */
    static String normalizeCompanyName(String raw) {
        if (raw == null) return "";
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        // [20], [5], [20竹], [20手] など店舗・支払サイト注記を除去
        s = s.replaceAll("\\[[^\\]]*\\]", "");
        // 全角括弧 → 半角扱いは NFKC でされる。(株)/(有) 表記を除去
        s = s.replace("(株)", "").replace("(有)", "")
             .replace("㈱", "").replace("㈲", "");
        // 会社種別語を除去
        s = s.replaceAll("株式会社|有限会社|合同会社|合資会社|合名会社", "");
        // 支店種別を除去（松/竹/梅/手 ? 末尾一文字のみ）
        // 単語中に現れると過剰マッチになるため、末尾一致のみ削る
        s = s.replaceAll("[松竹梅手]\\s*$", "");
        // 空白と記号を除去
        s = s.replaceAll("[\\s\\u3000,.\\-・。、]", "");
        return s.trim().toLowerCase();
    }

    @Data
    @Builder
    public static class BackfillResult {
        private boolean dryRun;
        private int matchedCount;
        private int ambiguousCount;
        private int notFoundCount;
        private int skippedCount;
        private List<BackfillItem> items;
    }

    @Data
    @Builder
    public static class BackfillItem {
        private Integer ruleId;
        private String sourceName;
        /** MATCHED / AMBIGUOUS / NOT_FOUND */
        private String status;
        private String candidateCode;
        private String candidateName;
        private List<String> alternatives;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
    private static String defaultStr(String v, String d) {
        return (v == null || v.isBlank()) ? d : v;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3892ms:
export type RuleKind = 'PAYABLE' | 'EXPENSE' | 'DIRECT_PURCHASE' | 'SUMMARY'
export type MatchStatus = 'MATCHED' | 'DIFF' | 'UNMATCHED' | 'NA'

export interface PaymentMfPreviewRow {
  excelRowIndex: number
  paymentSupplierCode: string | null
  sourceName: string | null
  amount: number | null

  ruleKind: RuleKind | null
  debitAccount: string | null
  debitSubAccount: string | null
  debitDepartment: string | null
  debitTax: string | null
  debitAmount: number | null
  creditAccount: string | null
  creditSubAccount: string | null
  creditDepartment: string | null
  creditTax: string | null
  creditAmount: number | null
  summary: string | null
  tag: string | null

  matchStatus: MatchStatus | null
  payableAmount: number | null
  payableDiff: number | null
  supplierNo: number | null

  errorType: string | null
  errorMessage: string | null
}

export interface PaymentMfPreviewResponse {
  uploadId: string
  fileName: string
  transferDate: string | null        // ISO LocalDate
  transactionMonth: string | null

  totalRows: number
  totalAmount: number
  matchedCount: number
  diffCount: number
  unmatchedCount: number
  errorCount: number

  rows: PaymentMfPreviewRow[]
  unregisteredSources: string[]
  /** PAYABLE ルールがマッチしたが payment_supplier_code 未設定の送り先（CSV除外予備軍）。 */
  rulesMissingSupplierCode: string[]
  /** 請求額-値引-早払 と 振込金額合計 の整合性チェック結果。 */
  amountReconciliation: AmountReconciliation | null
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
  creditTaxCategory?: string
  summaryTemplate: string
  tag?: string | null
  priority?: number
}

export interface PaymentMfHistory {
  id: number
  shopNo: number
  transferDate: string
  sourceFilename: string
  csvFilename: string
  rowCount: number
  totalAmount: number
  matchedCount: number
  diffCount: number
  unmatchedCount: number
  addDateTime: string
}

export interface PaymentMfVerifyResult {
  transferDate: string
  transactionMonth: string
  matchedCount: number
  diffCount: number
  notFoundCount: number
  skippedCount: number
  unmatchedSuppliers: string[]
}

export type BackfillStatus = 'MATCHED' | 'AMBIGUOUS' | 'NOT_FOUND'

export interface BackfillItem {
  ruleId: number
  sourceName: string
  status: BackfillStatus
  candidateCode?: string | null
  candidateName?: string | null
  alternatives?: string[] | null
}

export interface BackfillResult {
  dryRun: boolean
  matchedCount: number
  ambiguousCount: number
  notFoundCount: number
  skippedCount: number
  items: BackfillItem[]
}

/** 買掛仕入MF 補助行（EXPENSE/SUMMARY/DIRECT_PURCHASE）1 件。 */
export interface PaymentMfAuxRow {
  auxRowId: number
  transactionMonth: string          // yyyy-MM-dd
  transferDate: string              // yyyy-MM-dd
  ruleKind: 'EXPENSE' | 'SUMMARY' | 'DIRECT_PURCHASE'
  sequenceNo: number
  sourceName: string
  paymentSupplierCode: string | null
  amount: number
  debitAccount: string
  debitSubAccount: string | null
  debitDepartment: string | null
  debitTax: string
  creditAccount: string
  creditSubAccount: string | null
  creditDepartment: string | null
  creditTax: string
  summary: string | null
  tag: string | null
  sourceFilename: string | null
}

export type AuxRuleKind = PaymentMfAuxRow['ruleKind']

/** 検証済みCSV出力ダイアログのプレビュー用レスポンス。 */
export interface VerifiedExportPreview {
  transactionMonth: string
  payableCount: number
  payableTotalAmount: number
  auxBreakdown: {
    transferDate: string
    ruleKind: AuxRuleKind
    count: number
    totalAmount: number
  }[]
  warnings: string[]
  skippedSuppliers: string[]
}

export const RULE_KINDS: RuleKind[] = ['PAYABLE', 'EXPENSE', 'DIRECT_PURCHASE']

export const TAX_CATEGORIES = [
  '対象外',
  '課税仕入 10%',
  '課税仕入-返還等 10%',
  '非課税売上',
] as const
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
レビューの核は見えてきました。実装上は変換できる形になっていますが、取引日・重複取込・Excel レイアウト検知・監査ログの粒度に、運用を誤らせる前提が残っています。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java -Pattern \"transactionDate\\(txDate\\)|transactionDate\\(aux.getTransferDate\\)|toCsvBytes\\(csvRows, transactionMonth\\)|deriveTransactionMonth\\(|e.afterTotal && \\\"PAYABLE\\\"|setMfTransferDate|saveAuxRowsForVerification|setVerificationNote|setAutoAdjustedAmount|saveHistory|saveVerifiedExportHistory\"" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfExcelParser.java -Pattern \"buildColumnMap|colMap.get\\(\\\"請求額\\\"\\)|colMap.get\\(\\\"送料相手\\\"\\)|colMap.get\\(\\\"早払い\\\"\\)|summaryCaptured|afterTotal|if \\(amount == null|selectSheet|for \\(int c = 0; c <= 10\"" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfCsvWriter.java -Pattern \"MS932|OutputStreamWriter|safe\\(|fmtAmount\"" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\resources\\db\\migration\\V016__create_payment_mf_aux_row.sql -Pattern \"CREATE TABLE|CONSTRAINT|CREATE INDEX|UNIQUE|COMMENT ON COLUMN\"" in C:\project\odamitsu-data-hub
2026-05-04T01:48:13.793361Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 1.7 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
Select-String: 
Line |
   2 |  Select-String -Path backend\src\main\java\jp\co\oda32\domain\service\ …
     |  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     | A positional parameter cannot be found that accepts argument '請求額\\)|colMap.get\(\送料相手\\)|colMap.get\(\早払い\\)|summaryCaptured|afterTotal|if \(amount == null|selectSheet|for \(int c = 0; c <= 10'.

 exited 1 in 1674ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
Select-String: 
Line |
   2 |  Select-String -Path backend\src\main\java\jp\co\oda32\domain\service\ …
     |  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     | A positional parameter cannot be found that accepts argument '請求額\\)|colMap.get\(\送料相手\\)|colMap.get\(\早払い\\)|summaryCaptured|afterTotal|if \(amount == null|selectSheet|for \(int c = 0; c <= 10'.

 succeeded in 1790ms:

backend\src\main\resources\db\migration\V016__create_payment_mf_aux_row.sql:7:CREATE TABLE IF NOT EXISTS t_payment_mf_a
ux_row (
backend\src\main\resources\db\migration\V016__create_payment_mf_aux_row.sql:32:    CONSTRAINT chk_payment_mf_aux_rule_k
ind
backend\src\main\resources\db\migration\V016__create_payment_mf_aux_row.sql:36:CREATE INDEX IF NOT EXISTS idx_payment_m
f_aux_tx_month
backend\src\main\resources\db\migration\V016__create_payment_mf_aux_row.sql:39:CREATE INDEX IF NOT EXISTS idx_payment_m
f_aux_transfer
backend\src\main\resources\db\migration\V016__create_payment_mf_aux_row.sql:43:COMMENT ON COLUMN t_payment_mf_aux_row.t
ransaction_month IS '小田光締め日(前月20日)。CSV 取引日列にも使用。';
backend\src\main\resources\db\migration\V016__create_payment_mf_aux_row.sql:44:COMMENT ON COLUMN t_payment_mf_aux_row.t
ransfer_date     IS '出処 Excel の送金日 (5日 or 20日)。同一 (transaction_month, transfer_date) で再アップロードされた
ら物理削除→再挿入で洗い替え。';
backend\src\main\resources\db\migration\V016__create_payment_mf_aux_row.sql:45:COMMENT ON COLUMN t_payment_mf_aux_row.r
ule_kind         IS 'EXPENSE / SUMMARY / DIRECT_PURCHASE';
backend\src\main\resources\db\migration\V016__create_payment_mf_aux_row.sql:46:COMMENT ON COLUMN t_payment_mf_aux_row.s
equence_no       IS 'Excel 内の出現順 (CSV 出力順序維持用)';
backend\src\main\resources\db\migration\V016__create_payment_mf_aux_row.sql:47:COMMENT ON COLUMN t_payment_mf_aux_row.s
ource_filename   IS 'トレーサビリティ用 Excel ファイル名';

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1881ms:

backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:7:import java.io.OutputStreamWriter;
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:17: * <p>文字コードは CP932 (MS932)、
改行は LF、金額末尾に半角スペースを付与する
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:29:    private static final Charset CS
V_CHARSET = Charset.forName("MS932");
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:41:     * 仕訳行リストを CP932 (MS932)
 + LF の CSV バイト列に変換する。
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:54:        try (OutputStreamWriter w =
 new OutputStreamWriter(baos, CSV_CHARSET)) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:75:        cols.add(safe(r.getDebitAcc
ount()));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:76:        cols.add(safe(r.getDebitSub
Account()));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:77:        cols.add(safe(r.getDebitDep
artment()));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:79:        cols.add(safe(r.getDebitTax
()));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:81:        cols.add(fmtAmount(r.getDeb
itAmount()));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:82:        cols.add(safe(r.getCreditAc
count()));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:83:        cols.add(safe(r.getCreditSu
bAccount()));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:84:        cols.add(safe(r.getCreditDe
partment()));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:86:        cols.add(safe(r.getCreditTa
x()));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:88:        cols.add(fmtAmount(r.getCre
ditAmount()));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:89:        cols.add(safe(r.getSummary(
)));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:90:        cols.add(safe(r.getTag()));
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:95:    private static String fmtAmount
(Long v) {
backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java:100:    private static String safe(Str
ing s) {

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

2026-05-04T01:48:15.033876Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 2.9 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
Select-String: 
Line |
   2 |  Select-String -Path backend\src\main\java\jp\co\oda32\domain\service\ 乧
     |  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     | A positional parameter cannot be found that accepts argument 'PAYABLE\|setMfTransferDate|saveAuxRowsForVerification|setVerificationNote|setAutoAdjustedAmount|saveHistory|saveVerifiedExportHistory'.

 exited 1 in 2914ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
Select-String: 
Line |
   2 |  Select-String -Path backend\src\main\java\jp\co\oda32\domain\service\ 乧
     |  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     | A positional parameter cannot be found that accepts argument 'PAYABLE\|setMfTransferDate|saveAuxRowsForVerification|setVerificationNote|setAutoAdjustedAmount|saveHistory|saveVerifiedExportHistory'.

2026-05-04T01:48:25.347745Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java -SimpleMatch -Pattern 'transactionDate(txDate)','transactionDate(aux.getTransferDate())','toCsvBytes(csvRows, transactionMonth)','deriveTransactionMonth','e.afterTotal && \"PAYABLE\"','setMfTransferDate','setVerificationNote','setAutoAdjustedAmount','saveHistory','saveVerifiedExportHistory'"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java -SimpleMatch -Pattern 'transactionDate(txDate)','transactionDate(aux.getTransferDate())','toCsvBytes(csvRows, transactionMonth)','deriveTransactionMonth','e.afterTotal && \"PAYABLE\"','setMfTransferDate','setVerificationNote','setAutoAdjustedAmount','saveHistory','saveVerifiedExportHistory'" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfExcelParser.java -SimpleMatch -Pattern 'buildColumnMap','colMap.get(\"請求額\")','colMap.get(\"送料相手\")','colMap.get(\"早払い\")','summaryCaptured','afterTotal','amount == null','selectSheet','for (int c = 0; c <= 10'" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path frontend\\components\\pages\\finance\\payment-mf-import.tsx -SimpleMatch -Pattern '二重取込','verifyMut.mutate','confirmed','取引月','5日・20日','description='" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfRuleService.java -SimpleMatch -Pattern 'create(PaymentMfRuleRequest','ruleKind(req.getRuleKind())','paymentSupplierService.findByShopNo','normalizeCompanyName','dryRun'" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfImportService.java -SimpleMatch -Pattern 'transactionDate(txDate)','transactionDate(aux.getTransferDate())','toCsvBytes(csvRows, transactionMonth)','deriveTransactionMonth','e.afterTotal && \"PAYABLE\"','setMfTransferDate','setVerificationNote','setAutoAdjustedAmount','saveHistory','saveVerifiedExportHistory'"` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfExcelParser.java -SimpleMatch -Pattern 'buildColumnMap','colMap.get(\"請求額\")','colMap.get(\"送料相手\")','colMap.get(\"早払い\")','summaryCaptured','afterTotal','amount == null','selectSheet','for (int c = 0; c <= 10'"` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path frontend\\components\\pages\\finance\\payment-mf-import.tsx -SimpleMatch -Pattern '二重取込','verifyMut.mutate','confirmed','取引月','5日・20日','description='"` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfRuleService.java -SimpleMatch -Pattern 'create(PaymentMfRuleRequest','ruleKind(req.getRuleKind())','paymentSupplierService.findByShopNo','normalizeCompanyName','dryRun'"` rejected: blocked by policy
2026-05-04T01:48:25.348231Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfExcelParser.java -SimpleMatch -Pattern 'buildColumnMap','colMap.get(\"請求額\")','colMap.get(\"送料相手\")','colMap.get(\"早払い\")','summaryCaptured','afterTotal','amount == null','selectSheet','for (int c = 0; c <= 10'"` rejected: blocked by policy
2026-05-04T01:48:25.348661Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path frontend\\components\\pages\\finance\\payment-mf-import.tsx -SimpleMatch -Pattern '二重取込','verifyMut.mutate','confirmed','取引月','5日・20日','description='"` rejected: blocked by policy
2026-05-04T01:48:25.349101Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\PaymentMfRuleService.java -SimpleMatch -Pattern 'create(PaymentMfRuleRequest','ruleKind(req.getRuleKind())','paymentSupplierService.findByShopNo','normalizeCompanyName','dryRun'"` rejected: blocked by policy
codex
**批判的レビュー結果**

### 1. Critical / 業務整合
**検証済みCSV出力だけ「取引日」が送金日になり、通常変換CSVと意味がズレる**  
`PaymentMfCsvWriter` は行別 `transactionDate` を優先します。`convert` は fallback の `transactionMonth` を使う一方、`exportVerifiedCsv` は PAYABLE に `mfTransferDate`、aux に `transferDate` を入れているため、検証済CSVでは取引日が 2/5 や 2/20 になります。設計書・UI 文言は「CSV取引日列は締め日 = transactionMonth 固定」と説明しており、MF上で期間帰属が変わるリスクがあります。  
**推奨アクション:** `exportVerifiedCsv` の行別 `transactionDate` 設定を廃止し、全行 `transactionMonth` 固定に統一してください。送金日は監査用カラムとしてDB・履歴に残し、CSV取引日には使わない方針を明文化します。

### 2. Critical / 業務整合
**PAYABLE→DIRECT_PURCHASE 自動降格が「経理判断」ではなく暗黙ルールになっている**  
合計行以降の PAYABLE ルールを自動で DIRECT_PURCHASE に変換していますが、これは買掛金支払ではなく仕入高計上へ会計処理を変える判断です。画面上の明示承認、理由、適用件数、対象 supplier の監査ログがなく、後から「なぜ買掛金でなく仕入高になったか」を説明しにくい構造です。既出レビューは実装責務の分散を指摘していますが、根本問題は「経理判断をシステムが黙って代行している」点です。  
**推奨アクション:** 自動降格を廃止し、DIRECT_PURCHASE は明示ルール登録のみ許可するか、降格候補としてプレビューに出して admin 承認を必須にしてください。

### 3. Critical / エッジケース
**同一振込明細の再取込・古いExcelの上書きに対する防止キーがない**  
aux 行は `(shop, transaction_month, transfer_date)` 単位で洗い替えされますが、Excel内容のハッシュ、import batch ID、確定状態の世代管理がありません。同じ送金日の古いExcelを再アップロードすると、最新の補助行と買掛金検証状態を静かに上書きできます。履歴は残りますが、どの履歴が現行DB状態を作ったかが完全には結び付きません。  
**推奨アクション:** source file hash + transferDate + transactionMonth の取込台帳を作り、同一・旧版・差分あり再取込を警告またはブロックしてください。PAYABLE と aux に `import_history_id` を持たせるのが望ましいです。

### 4. Major / 設計選択
**Excel経由を恒久前提にしており、MF API直接連携を検討した形跡が弱い**  
現設計は「ExcelをアップロードしてCSVを作る」前提ですが、目的は MF への仕訳投入です。Excelは経理の現行作業物として妥当な中間入力ですが、恒久API境界にすると、フォーマット変更・ローカルファイル管理・再取込・CSV文字化けをアプリ側が背負い続けます。MF API直接連携を採らない理由が「既存運用がCSV」以上に記録されていません。  
**推奨アクション:** 当面はExcel/CSVでよいですが、ADRとして「MF APIを採らない理由、再検討条件、CSV運用の責任範囲」を残してください。

### 5. Major / エッジケース
**Excelフォーマット変更の検知が弱く、欠落列が0円補助行として流れる可能性がある**  
パーサはヘッダ名ベースですが、必須扱いは主に `送り先` と `請求額` だけです。`送料相手`、`早払い`、`振込金額` が見つからない場合でも処理は続き、SUMMARY が 0 円または整合性チェック不能に近い値になります。列順入替には強い一方、列名変更・重複ヘッダ・合計行の意味変更には弱いです。  
**推奨アクション:** 5日/20日別に必須列セットと許容列セットを定義し、欠落・重複・未知の重大列変更は preview 段階でブロックしてください。

### 6. Major / 業務整合
**100円閾値一致が自動調整と結び付いており、監査説明として粗い**  
100円以内を一致扱いにすること自体は運用上あり得ますが、現在は `autoAdjustedAmount` と note に落とすだけで、閾値適用の承認者・理由分類・税込/税抜差・振込手数料差などの区別がありません。税理士監査では「なぜ100円まで許容か」「その差額はどの勘定で吸収したか」が問われます。  
**推奨アクション:** 閾値一致を `EXACT_MATCH` / `THRESHOLD_MATCH` に分け、閾値一致は承認ログと差額理由を必須化してください。

### 7. Major / エッジケース
**CP932で表現できない文字が `?` に置換され、検知されない**  
`OutputStreamWriter(..., MS932)` は未マップ文字をデフォルトで置換するため、`㎡`、丸数字、異体字などが摘要・タグ・取引先名に入るとCSV上で別文字になります。これはMF取込前に気づけず、履歴CSVにも破損後の値が残ります。  
**推奨アクション:** `CharsetEncoder` を `CodingErrorAction.REPORT` で使い、変換不能文字を行番号・項目名付きで preview エラーにしてください。

### 8. Major / 設計選択
**aux_row は別テーブルで妥当だが、PAYABLEとの統合出力を支える共通ヘッダがない**  
補助行を `t_accounts_payable_summary` に混ぜない判断は妥当です。ただし PAYABLE と aux が同じ import batch に属する保証がなく、検証済CSV出力時に「このPAYABLE群とこの補助行群が同じExcelセット由来」と証明できません。別テーブル設計なら、なおさら共通の取込単位が必要です。  
**推奨アクション:** `t_payment_mf_import_batch` を導入し、PAYABLE更新・aux行・履歴CSVを同じ batch ID に紐付けてください。

### 9. Major / 代替案
**サーバー側Excelパーサは安全だが、プレビューで原本差分を説明できない**  
サーバー側POIは一貫性・監査性の面で妥当です。一方、ユーザーが見ているExcelとサーバーが抽出したセルの差分を画面で追えず、列ズレや合計行誤認の説明責任が弱いです。ブラウザExcel.jsに寄せる必要はありませんが、現設計は「サーバーが読んだ結果」を検証するUIが薄いです。  
**推奨アクション:** サーバー側維持でよいので、検出シート名、検出列、合計行番号、除外行数、読取合計を preview に表示してください。

### 10. Major / 代替案
**ルールエンジンが手作業マスタ前提で、候補提示と確信度がない**  
`m_payment_mf_rule` の明示ルールは会計処理の制御として妥当です。ただし未登録時に「類似候補」「過去登録」「支払先コード一致だが名前違い」などの候補提示が弱く、人間が新規ルールを増やしやすいです。MLは過剰ですが、データ駆動の候補提示は必要です。  
**推奨アクション:** MLではなく deterministic な候補スコアリングを先に入れてください。名前正規化、支払先コード、過去Excel、MF補助科目を使った候補一覧で十分です。

### 11. Minor / 代替案
**即時CSV生成は現規模なら妥当だが、上限超過時の設計がない**  
20MB・10000行制限があり、現実の振込明細なら同期処理で十分です。ただし検証済CSV出力はDB集約と履歴BLOB保存を含むため、将来大量化した場合のタイムアウトや重複クリック対策が弱くなります。  
**推奨アクション:** 今すぐジョブキュー化は不要です。処理時間・行数・CSVサイズを履歴に残し、閾値超過時だけ非同期ジョブへ移行する基準を決めてください。

### 12. Minor / 業務整合
**一般ユーザーのルール追加が、会計マスタ変更としては軽すぎる**  
未登録送り先の追加を一般ユーザーにも許可していますが、借方勘定・税区分・摘要テンプレを含むため会計処理の変更です。誤ったEXPENSE/PAYABLE登録は次回以降の仕訳に継続影響します。  
**推奨アクション:** 一般ユーザーは「候補ルール申請」までにし、確定は admin または経理責任者に限定してください。

**Opus が見落としていた最重要 3 件**

1. `exportVerifiedCsv` だけCSV取引日が送金日化し、通常変換CSVと期間帰属がズレる点。  
2. PAYABLE→DIRECT_PURCHASE 自動降格が、実装問題ではなく会計判断の無承認自動化になっている点。  
3. 再取込の一意性が transferDate 粒度しかなく、古いExcelで現行の検証状態を静かに上書きできる点。
tokens used
133,944
**批判的レビュー結果**

### 1. Critical / 業務整合
**検証済みCSV出力だけ「取引日」が送金日になり、通常変換CSVと意味がズレる**  
`PaymentMfCsvWriter` は行別 `transactionDate` を優先します。`convert` は fallback の `transactionMonth` を使う一方、`exportVerifiedCsv` は PAYABLE に `mfTransferDate`、aux に `transferDate` を入れているため、検証済CSVでは取引日が 2/5 や 2/20 になります。設計書・UI 文言は「CSV取引日列は締め日 = transactionMonth 固定」と説明しており、MF上で期間帰属が変わるリスクがあります。  
**推奨アクション:** `exportVerifiedCsv` の行別 `transactionDate` 設定を廃止し、全行 `transactionMonth` 固定に統一してください。送金日は監査用カラムとしてDB・履歴に残し、CSV取引日には使わない方針を明文化します。

### 2. Critical / 業務整合
**PAYABLE→DIRECT_PURCHASE 自動降格が「経理判断」ではなく暗黙ルールになっている**  
合計行以降の PAYABLE ルールを自動で DIRECT_PURCHASE に変換していますが、これは買掛金支払ではなく仕入高計上へ会計処理を変える判断です。画面上の明示承認、理由、適用件数、対象 supplier の監査ログがなく、後から「なぜ買掛金でなく仕入高になったか」を説明しにくい構造です。既出レビューは実装責務の分散を指摘していますが、根本問題は「経理判断をシステムが黙って代行している」点です。  
**推奨アクション:** 自動降格を廃止し、DIRECT_PURCHASE は明示ルール登録のみ許可するか、降格候補としてプレビューに出して admin 承認を必須にしてください。

### 3. Critical / エッジケース
**同一振込明細の再取込・古いExcelの上書きに対する防止キーがない**  
aux 行は `(shop, transaction_month, transfer_date)` 単位で洗い替えされますが、Excel内容のハッシュ、import batch ID、確定状態の世代管理がありません。同じ送金日の古いExcelを再アップロードすると、最新の補助行と買掛金検証状態を静かに上書きできます。履歴は残りますが、どの履歴が現行DB状態を作ったかが完全には結び付きません。  
**推奨アクション:** source file hash + transferDate + transactionMonth の取込台帳を作り、同一・旧版・差分あり再取込を警告またはブロックしてください。PAYABLE と aux に `import_history_id` を持たせるのが望ましいです。

### 4. Major / 設計選択
**Excel経由を恒久前提にしており、MF API直接連携を検討した形跡が弱い**  
現設計は「ExcelをアップロードしてCSVを作る」前提ですが、目的は MF への仕訳投入です。Excelは経理の現行作業物として妥当な中間入力ですが、恒久API境界にすると、フォーマット変更・ローカルファイル管理・再取込・CSV文字化けをアプリ側が背負い続けます。MF API直接連携を採らない理由が「既存運用がCSV」以上に記録されていません。  
**推奨アクション:** 当面はExcel/CSVでよいですが、ADRとして「MF APIを採らない理由、再検討条件、CSV運用の責任範囲」を残してください。

### 5. Major / エッジケース
**Excelフォーマット変更の検知が弱く、欠落列が0円補助行として流れる可能性がある**  
パーサはヘッダ名ベースですが、必須扱いは主に `送り先` と `請求額` だけです。`送料相手`、`早払い`、`振込金額` が見つからない場合でも処理は続き、SUMMARY が 0 円または整合性チェック不能に近い値になります。列順入替には強い一方、列名変更・重複ヘッダ・合計行の意味変更には弱いです。  
**推奨アクション:** 5日/20日別に必須列セットと許容列セットを定義し、欠落・重複・未知の重大列変更は preview 段階でブロックしてください。

### 6. Major / 業務整合
**100円閾値一致が自動調整と結び付いており、監査説明として粗い**  
100円以内を一致扱いにすること自体は運用上あり得ますが、現在は `autoAdjustedAmount` と note に落とすだけで、閾値適用の承認者・理由分類・税込/税抜差・振込手数料差などの区別がありません。税理士監査では「なぜ100円まで許容か」「その差額はどの勘定で吸収したか」が問われます。  
**推奨アクション:** 閾値一致を `EXACT_MATCH` / `THRESHOLD_MATCH` に分け、閾値一致は承認ログと差額理由を必須化してください。

### 7. Major / エッジケース
**CP932で表現できない文字が `?` に置換され、検知されない**  
`OutputStreamWriter(..., MS932)` は未マップ文字をデフォルトで置換するため、`㎡`、丸数字、異体字などが摘要・タグ・取引先名に入るとCSV上で別文字になります。これはMF取込前に気づけず、履歴CSVにも破損後の値が残ります。  
**推奨アクション:** `CharsetEncoder` を `CodingErrorAction.REPORT` で使い、変換不能文字を行番号・項目名付きで preview エラーにしてください。

### 8. Major / 設計選択
**aux_row は別テーブルで妥当だが、PAYABLEとの統合出力を支える共通ヘッダがない**  
補助行を `t_accounts_payable_summary` に混ぜない判断は妥当です。ただし PAYABLE と aux が同じ import batch に属する保証がなく、検証済CSV出力時に「このPAYABLE群とこの補助行群が同じExcelセット由来」と証明できません。別テーブル設計なら、なおさら共通の取込単位が必要です。  
**推奨アクション:** `t_payment_mf_import_batch` を導入し、PAYABLE更新・aux行・履歴CSVを同じ batch ID に紐付けてください。

### 9. Major / 代替案
**サーバー側Excelパーサは安全だが、プレビューで原本差分を説明できない**  
サーバー側POIは一貫性・監査性の面で妥当です。一方、ユーザーが見ているExcelとサーバーが抽出したセルの差分を画面で追えず、列ズレや合計行誤認の説明責任が弱いです。ブラウザExcel.jsに寄せる必要はありませんが、現設計は「サーバーが読んだ結果」を検証するUIが薄いです。  
**推奨アクション:** サーバー側維持でよいので、検出シート名、検出列、合計行番号、除外行数、読取合計を preview に表示してください。

### 10. Major / 代替案
**ルールエンジンが手作業マスタ前提で、候補提示と確信度がない**  
`m_payment_mf_rule` の明示ルールは会計処理の制御として妥当です。ただし未登録時に「類似候補」「過去登録」「支払先コード一致だが名前違い」などの候補提示が弱く、人間が新規ルールを増やしやすいです。MLは過剰ですが、データ駆動の候補提示は必要です。  
**推奨アクション:** MLではなく deterministic な候補スコアリングを先に入れてください。名前正規化、支払先コード、過去Excel、MF補助科目を使った候補一覧で十分です。

### 11. Minor / 代替案
**即時CSV生成は現規模なら妥当だが、上限超過時の設計がない**  
20MB・10000行制限があり、現実の振込明細なら同期処理で十分です。ただし検証済CSV出力はDB集約と履歴BLOB保存を含むため、将来大量化した場合のタイムアウトや重複クリック対策が弱くなります。  
**推奨アクション:** 今すぐジョブキュー化は不要です。処理時間・行数・CSVサイズを履歴に残し、閾値超過時だけ非同期ジョブへ移行する基準を決めてください。

### 12. Minor / 業務整合
**一般ユーザーのルール追加が、会計マスタ変更としては軽すぎる**  
未登録送り先の追加を一般ユーザーにも許可していますが、借方勘定・税区分・摘要テンプレを含むため会計処理の変更です。誤ったEXPENSE/PAYABLE登録は次回以降の仕訳に継続影響します。  
**推奨アクション:** 一般ユーザーは「候補ルール申請」までにし、確定は admin または経理責任者に限定してください。

**Opus が見落としていた最重要 3 件**

1. `exportVerifiedCsv` だけCSV取引日が送金日化し、通常変換CSVと期間帰属がズレる点。  
2. PAYABLE→DIRECT_PURCHASE 自動降格が、実装問題ではなく会計判断の無承認自動化になっている点。  
3. 再取込の一意性が transferDate 粒度しかなく、古いExcelで現行の検証状態を静かに上書きできる点。
