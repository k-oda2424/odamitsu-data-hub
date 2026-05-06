OpenAI Codex v0.128.0 (research preview)
--------
workdir: C:\project\odamitsu-data-hub
model: gpt-5.5
provider: openai
approval: never
sandbox: read-only
reasoning effort: none
reasoning summaries: none
session id: 019dfbc6-723a-7eb1-844d-17a9e77f4278
--------
user
あなたは MF 連携・OAuth・暗号化の上級レビュアーです。
小田光データ連携基盤 (`C:/project/odamitsu-data-hub`) で実装された **MF 連携基盤系 4 件** を批判的にレビューしてください。

## レビュー対象 (Group 1: MF 連携基盤)

### P1-01: MF tenant binding 必須化
- 新規: `backend/src/main/resources/db/migration/V032__add_mf_tenant_binding.sql`
- 新規: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfTenantResponse.java`
- 新規: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfTenantMismatchException.java`
- 改修: `MMfOauthClient.java`, `MfApiClient.java`, `MfOauthService.java`, `MfTokenStatus.java`, `FinanceExceptionHandler.java`, `MfIntegrationController.java`
- 改修: `frontend/types/mf-integration.ts`, `frontend/components/pages/finance/mf-integration.tsx`

### P1-04: refresh_token 540 日寿命予兆 banner
- 改修: `MfTokenStatus.java`, `MfOauthService.java` (getStatus 拡張)
- 改修: `frontend/types/mf-integration.ts`
- 新規: `frontend/components/common/MfReAuthBanner.tsx`
- 改修: `frontend/components/layout/AppLayout.tsx`

### P1-05: OAuth 鍵分離
- 新規: `backend/src/main/resources/db/migration/V033__reencrypt_mf_oauth_secrets.java`
- 新規: `backend/src/main/java/jp/co/oda32/util/OauthCryptoUtil.java`
- 改修: `application.yml`, `application-dev.yml`, `application-prod.yml`, `application-test.yml`
- 改修: `MfOauthService.java`, `MMfOauthClient.java`
- 新規: `backend/scripts/gen-oauth-key.ps1`, `claudedocs/runbook-mf-oauth-keys.md`

### T6: Scope 変更検知
- 新規: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfScopeConstants.java`
- 改修: `MfTokenStatus.java`, `MfOauthService.java`
- 改修: `frontend/types/mf-integration.ts`
- 新規: `frontend/components/common/MfScopeBanner.tsx`

## 批判的レビュー観点

### セキュリティ系 (重点)
1. OauthCryptoUtil の AES-GCM 実装 (IV 生成、tag length、PBKDF2 iteration count) は CryptoUtil と等価か? 弱い設定はないか?
2. V033 migration での旧鍵→新鍵 再暗号化が atomic か? 失敗時のリカバリ?
3. MfTenantMismatchException 409 マッピングが情報漏洩リスクないか? (敵対者に「別 tenant が存在」を知らせる)
4. refresh_token 540 日寿命の判定が「現 active token の add_date_time」依存 → MF が rotation しない設計の場合に誤判定しないか?
5. MfApiClient.getTenant の呼び出し頻度は適切か? (callback と refresh 後のみ vs 毎回)

### 整合性・運用系
6. tenant binding の検証順序 (callback 内): state 検証 → exchange → tenant 取得 → tenant 一致検証 → token 永続化、の順で race condition なし?
7. dev fallback (旧鍵) と prod env (新鍵) が混在する状況 (例: テスト環境で旧鍵 + prod で新鍵) で V033 migration が正しく動くか?
8. MfReAuthBanner の閾値判定 (60/30/14/7/0) で edge case (= 0 直前、time zone 跨ぎ等) での挙動
9. MfScopeConstants の REQUIRED_SCOPES と Frontend MF_REQUIRED_SCOPES の同期義務 → 将来的に乖離した場合の検知方法は?
10. 既存 prod 認可済 client は次回 refresh で自動 binding 確定する見込みだが、その期間中の挙動 (mfTenantId=NULL の状態で API 呼び出し)

### 設計系
11. tenant binding と scope 検出を同じ MfTokenStatus に詰めているが、責務分離は適切か? (将来的に拡張時の影響)
12. OauthCryptoUtil と CryptoUtil の構造的重複 → 共通親クラスや utility 化の余地
13. P1-04 banner と T6 banner が同じ status API を共有 (queryKey) しているが、他に独立 banner が増えた時のスケーラビリティ
14. V033 Java migration が Spring 起動前に env 必須 → 開発者が忘れた時の DX (= setup 手順)

### 想定外攻撃シナリオ
15. admin が誤って authorize_url / token_url / api_base_url を改竄 (DD-F-MJ-6) → tenant binding でも防げないシナリオ
16. MF 側で revoke された token の状態で getStatus を呼ぶと何が起きるか?
17. tenant_bound_at が NULL の旧データで mismatch 検証はどう動くか? (= 初回認可扱いで素通り? それとも警告?)
18. dev 環境で env が未設定で起動 → V033 fail-fast → 開発者の混乱を最小化できているか?

## 出力フォーマット

Markdown、日本語、簡潔に (max 600 行)。各指摘は:
- Severity: Critical / Major / Minor
- カテゴリ: セキュリティ / 整合性 / 運用 / 設計
- 内容 (3-5 文)
- 推奨アクション (1-2 文)

最後に「Opus 実装で見落としていた最重要 3 件」を明記。


codex
`using-superpowers` と、レビュー依頼なので `requesting-code-review`、対象がSpring/Javaを含むため `spring-framework-patterns` を確認してから進めます。まずレビュー観点に必要な差分と該当ファイルを読みます。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content C:\\Users\\kazuki\\.agents\\skills\\using-superpowers\\SKILL.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content C:\\Users\\kazuki\\.agents\\skills\\requesting-code-review\\SKILL.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content C:\\Users\\kazuki\\.agents\\skills\\spring-framework-patterns\\SKILL.md" in C:\project\odamitsu-data-hub
 succeeded in 1866ms:
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

 succeeded in 2837ms:
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

 succeeded in 4045ms:
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
レビュー用のスキル内容を確認しました。ここからは対象ファイルと既存実装を横断して、ユーザー指定の18観点に沿ってリスクを優先度順に絞り込みます。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'rg --files backend/src/main/java backend/src/main/resources frontend/components frontend/types backend/scripts claudedocs | rg "(V032|V033|MfTenant|MfApiClient|MfOauthService|MfTokenStatus|FinanceExceptionHandler|MfIntegrationController|OauthCryptoUtil|CryptoUtil|MfScope|MfReAuthBanner|MfScopeBanner|mf-integration|AppLayout|m_f_oauth|MMfOauthClient|application.*yml|gen-oauth-key|runbook-mf-oauth)"' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git status --short' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'rg "class CryptoUtil|record MfTokenStatus|class MfOauthService|interface MMfOauthClient|class MfApiClient|MfTenantMismatch|tenant|scope|refresh|oauth" backend/src/main/java backend/src/main/resources frontend/components frontend/types -n' in C:\project\odamitsu-data-hub
 succeeded in 1592ms:
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

2026-05-06T05:33:36.903751Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 4.8 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg "class CryptoUtil|record MfTokenStatus|class MfOauthService|interf 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

2026-05-06T05:33:36.903757Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 4.8 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg --files backend/src/main/java backend/src/main/resources frontend/ 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 exited 1 in 4810ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg --files backend/src/main/java backend/src/main/resources frontend/ 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 exited 1 in 4779ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg "class CryptoUtil|record MfTokenStatus|class MfOauthService|interf 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

codex
この環境では `rg` が使えないため、PowerShell の `Get-ChildItem` / `Select-String` に切り替えます。対象外の大量変更がある作業ツリーなので、レビュー対象ファイルだけを読む形に絞ります。
2026-05-06T05:33:48.813153Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse backend/src/main/java,backend/src/main/resources,frontend/components,frontend/types,backend/scripts,claudedocs | Where-Object { $_.Name -match '"'V032|V033|MfTenant|MfApiClient|MfOauthService|MfTokenStatus|FinanceExceptionHandler|MfIntegrationController|OauthCryptoUtil|CryptoUtil|MfScope|MfReAuthBanner|MfScopeBanner|mf-integration|AppLayout|MMfOauthClient|application.*yml|gen-oauth-key|runbook-mf-oauth' } | Select-Object -ExpandProperty FullName"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse backend/src/main/java,backend/src/main/resources,frontend/components,frontend/types,backend/scripts,claudedocs | Where-Object { $_.Name -match '"'V032|V033|MfTenant|MfApiClient|MfOauthService|MfTokenStatus|FinanceExceptionHandler|MfIntegrationController|OauthCryptoUtil|CryptoUtil|MfScope|MfReAuthBanner|MfScopeBanner|mf-integration|AppLayout|MMfOauthClient|application.*yml|gen-oauth-key|runbook-mf-oauth' } | Select-Object -ExpandProperty FullName" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Recurse backend/src/main/java,backend/src/main/resources,frontend/components,frontend/types | Select-String -Pattern 'class CryptoUtil|record MfTokenStatus|class MfOauthService|interface MMfOauthClient|class MfApiClient|MfTenantMismatch|tenant|scope|refresh|oauth' | Select-Object Path,LineNumber,Line | Format-List" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Recurse backend/src/main/java,backend/src/main/resources,frontend/components,frontend/types | Select-String -Pattern 'class CryptoUtil|record MfTokenStatus|class MfOauthService|interface MMfOauthClient|class MfApiClient|MfTenantMismatch|tenant|scope|refresh|oauth' | Select-Object Path,LineNumber,Line | Format-List"` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse backend/src/main/java,backend/src/main/resources,frontend/components,frontend/types,backend/scripts,claudedocs | Where-Object { $_.Name -match '"'V032|V033|MfTenant|MfApiClient|MfOauthService|MfTokenStatus|FinanceExceptionHandler|MfIntegrationController|OauthCryptoUtil|CryptoUtil|MfScope|MfReAuthBanner|MfScopeBanner|mf-integration|AppLayout|MMfOauthClient|application.*yml|gen-oauth-key|runbook-mf-oauth' } | Select-Object -ExpandProperty FullName"` rejected: blocked by policy
2026-05-06T05:33:48.813790Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Recurse backend/src/main/java,backend/src/main/resources,frontend/components,frontend/types | Select-String -Pattern 'class CryptoUtil|record MfTokenStatus|class MfOauthService|interface MMfOauthClient|class MfApiClient|MfTenantMismatch|tenant|scope|refresh|oauth' | Select-Object Path,LineNumber,Line | Format-List"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse backend/src/main/java | Select-Object -ExpandProperty FullName' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse backend/src/main/resources | Select-Object -ExpandProperty FullName' in C:\project\odamitsu-data-hub
2026-05-06T05:34:09.143253Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse frontend/components/common,frontend/components/layout,frontend/components/pages/finance,frontend/types | Select-Object -ExpandProperty FullName'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse frontend/components/common,frontend/components/layout,frontend/components/pages/finance,frontend/types | Select-Object -ExpandProperty FullName' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Recurse frontend/components/common,frontend/components/layout,frontend/components/pages/finance,frontend/types | Select-Object -ExpandProperty FullName'` rejected: blocked by policy
 succeeded in 1823ms:
C:\project\odamitsu-data-hub\backend\src\main\resources\config
C:\project\odamitsu-data-hub\backend\src\main\resources\db
C:\project\odamitsu-data-hub\backend\src\main\resources\config\application-batch.yml
C:\project\odamitsu-data-hub\backend\src\main\resources\config\application-dev.yml
C:\project\odamitsu-data-hub\backend\src\main\resources\config\application-prod.yml
C:\project\odamitsu-data-hub\backend\src\main\resources\config\application-web.yml
C:\project\odamitsu-data-hub\backend\src\main\resources\config\application.yml
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V001__create_nfkc_function.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V002__create_quote_import_tables.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V003__alter_quote_import_detail_add_status.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V004__rename_quote_import_detail_to_t.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V005__create_nfkc_indexes.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V006__create_bcart_categories.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V007__create_estimate_comparison_tables.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V008__create_mf_cashbook_tables.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V009__alter_accounts_payable_summary_add_verification_fields.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V010__alter_estimate_add_auto_generated.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V011__create_payment_mf_tables.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V012__add_order_delivery_fk.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V013__add_order_delivery_status_check.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V014__alter_accounts_payable_summary_add_verified_amount.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V015__create_order_delivery_indexes.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V016__create_payment_mf_aux_row.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V017__fix_accounts_payable_summary_mf_export_enabled.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V018__alter_accounts_receivable_summary_add_verification_fields.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V019__create_mf_integration_tables.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V020__drop_mf_account_mapping.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V021__mf_oauth_state_and_token_uniq.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V022__create_mf_enum_translation.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V023__alter_accounts_payable_summary_add_mf_transfer_date.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V024__alter_accounts_payable_summary_add_opening_balance.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V025__alter_accounts_payable_summary_add_payment_settled.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V026__alter_accounts_payable_summary_add_verified_tax_excluded.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V027__create_consistency_review.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V028__create_supplier_opening_balance.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V029__fix_delivery_mapping_smile_code.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V030__deprecate_old_payable_summary_jobs.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V031__create_t_invoice_and_m_partner_group.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V032__add_mf_tenant_binding.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V034__payment_mf_import_history_protection.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V035__document_verified_amount_invariant.sql
C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V036__finance_audit_log.sql
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2030ms:
C:\project\odamitsu-data-hub\backend\src\main\java\db
C:\project\odamitsu-data-hub\backend\src\main\java\jp
C:\project\odamitsu-data-hub\backend\src\main\java\db\migration
C:\project\odamitsu-data-hub\backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\annotation
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\aop
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\audit
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\config
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\exception
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\security
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\BatchApplication.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\WebApplication.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\annotation\ApplicationType.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\annotation\ApplicationTypeCondition.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\annotation\SkipShopCheck.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\aop\ShopCheckAop.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\admin
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\auth
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\batch
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\bcart
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\comparison
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\dashboard
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\estimate
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\finance
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\goods
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\master
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\order
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\purchase
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\stock
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\user
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\admin\AuditLogController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\auth\AuthController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\batch\BatchController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\bcart\BCartCategoryController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\bcart\BCartController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\bcart\BCartPendingChangesController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\bcart\BCartProductController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\bcart\BCartProductSetsController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\comparison\EstimateComparisonController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\dashboard\DashboardController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\estimate\EstimateController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\finance\AccountsReceivableController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\finance\CashBookController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\finance\FinanceController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\finance\MfClientMappingController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationDebugController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\finance\MfJournalRuleController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\finance\PaymentMfImportController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\finance\SupplierOpeningBalanceController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\goods\GoodsController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\goods\PartnerGoodsController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\goods\SalesGoodsController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\master\MasterController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\order\OrderController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\purchase\PurchaseController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\purchase\PurchasePriceChangePlanController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\purchase\PurchasePriceController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\purchase\QuoteImportController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\purchase\SendOrderController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\purchase\SupplierQuoteDataController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\stock\StockController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\api\user\UserController.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\audit\AuditExclude.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\audit\AuditLog.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\audit\FinanceAuditAspect.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\audit\FinanceAuditWriter.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\estimate
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\goods
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\order
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\util
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\BatchJobCatalog.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\ExitStatusChangeListener.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\ISmileFile.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\JobStartEndListener.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\config
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\BCartCategoriesSyncTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\BCartCategoriesUpdateTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\BCartLogisticsCsv.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\BCartLogisticsCsvOutputTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\BCartMemberDeliveryImportTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\BCartMemberImportTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\BCartOrderConvertSmileOrderFileTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\BCartOrderProcessingSerialNumberUpdateTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\BCartOrderRegisterTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\BCartProductDescriptionUpdateTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\BCartProductSetsImportTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\BCartProductsImportTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\RegisterBCartMemberTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\SmileDestinationFileOutPutTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\SmileOrderFileOutPutTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\SmileOrderImportCsvHeader.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\SmilePartnerFileOutPutTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\SmilePartnerImportCsvHeader.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\config\BCartCategorySyncConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\config\BCartHttpClientConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\config\BCartLogisticsCsvExportConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\config\BCartMemberUpdateConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\config\BCartOrderImportConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\config\BCartProductDescriptionUpdateConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\config\BCartProductsImportConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\estimate\config
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\estimate\CustomParentGoodsPriceChange.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\estimate\ModifyEstimateNoMPartnerGoodsTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\estimate\ParentEstimateCreatedTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\estimate\ParentPartnerPriceChangePlanCreateTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\estimate\PartnerPriceChangePlanCreateTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\estimate\PartnerPriceChangeReflectTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\estimate\PriceChangeToEstimateCreateTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\estimate\config\PartnerPriceChangePlanCreateConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\config
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\helper
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\model
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\service
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\AccountsPayableAggregationTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\AccountsPayableBackfillTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\AccountsPayableSummaryInitTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\AccountsPayableSummaryTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\AccountsPayableToPurchaseJournalTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\AccountsPayableVerificationReportTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\AccountsPayableVerificationTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\AccountsReceivableToSalesJournalTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\MFJournalCsv.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\TAccountsReceivableSummaryTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\config\AccountsPayableAggregationConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\config\AccountsPayableBackfillConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\config\AccountsPayableSummaryConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\config\AccountsPayableVerificationConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\config\AccountsReceivableSummaryConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\config\PurchaseJournalIntegrationConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\config\SalesJournalIntegrationConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\helper\TaxCalculationHelper.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\model\InvoiceVerificationSummary.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\model\SummaryKey.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\model\TaxAggregationResult.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\model\TaxBreakdown.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\model\VerificationResult.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\service\AccountsPayableSummaryCalculator.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\service\AccountsReceivableCutoffReconciler.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\service\InvoiceVerifier.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\service\PayableMonthlyAggregator.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\finance\service\SmilePaymentVerifier.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\goods\{config}
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\goods\config
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\goods\logic
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\goods\GoodsFile.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\goods\GoodsFileProcessor.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\goods\GoodsFileReader.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\goods\GoodsFileWriter.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\goods\config\GoodsFileImportConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\goods\logic\AbstractGoodsFileWriterLogic.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\goods\logic\ExistGoodsWriterLogic.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\goods\logic\GoodsFileWriterFactory.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\goods\logic\IGoodsFileWriterLogic.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\goods\logic\NewGoodsWriterLogic.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\goods\logic\SalesGoodsCreateService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\order\logic
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\order\CustomSmileOrderFile.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\order\OrderStatusUpdateTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\order\PartnerGoodsSyncTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\order\StockListKey.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\order\VSalesMonthlySummaryRefreshTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\order\logic\ISmileOrderFileWriterLogic.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase\{config}
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase\config
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase\logic
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase\ExtPurchaseFile.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase\PurchaseFile.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase\PurchaseFileProcessor.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase\PurchaseFileReader.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase\PurchaseFileWriter.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase\PurchaseLinkSendOrderTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase\PurchaseMasterFile.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase\PurchaseMasterFileProcessor.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase\PurchaseMasterFileReader.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase\PurchaseMasterFileWriter.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase\PurchasePriceChangeReflectTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase\PurchasePriceCreateTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase\ShopNoAwareItemReader.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase\config\PurchaseFileImportConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase\logic\PaymentSupplierMasterWriterLogic.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\purchase\logic\SupplierMasterWriterLogic.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\config
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\AbstractSmileOrderImportService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\AbstractSmilePurchaseImportService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\DeleteSmileOrderProcessor.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\DeleteSmilePurchaseProcessor.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\NewSmileOrderProcessor.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\NewSmilePurchaseProcessor.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\PartnerFile.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\PartnerFileProcessor.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\PartnerFileReader.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\PartnerFileWriter.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\PartnerKey.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\PartnerSaveTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SalesGoodsKey.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\ShoriRenbanKey.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SmileOrderDeletionProcessor.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SmileOrderFile.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SmileOrderFileProcessor.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SmileOrderFileReader.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SmileOrderFileWriter.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SmileOrderImportService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SmileOrderImportTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SmileOrderUpdateService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SmilePaymentFile.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SmilePaymentFileReader.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SmilePaymentProcessor.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SmilePaymentWorkTableInitTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SmilePaymentWriter.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SmileProcessingSerialNumberUpdateTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SmilePurchaseDeleteService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SmilePurchaseImportService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SmilePurchaseImportTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SmilePurchaseUpdateService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\SupplierKey.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\UpdateSmileOrderProcessor.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\UpdateSmilePurchaseProcessor.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\WSmileOrderOutputFileTrancateTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\WSmilePartnerTrancateTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\WSmilePurchaseOutputFileTrancateTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\config\SmileOrderFileImportConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\smile\config\SmilePaymentImportConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\AbstractInvoiceDateManagement.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\AppropriateStockCalculate.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\AppropriateStockCalculateTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\DeliveryRegisterForm.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\ExtInventoryFile.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\IAppropriateStock.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\IAppropriateStockCalculate.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\InventoryAfterTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\InventoryBeforeTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\InventoryFile.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\InventoryFileProcessor.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\InventoryFileReader.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\InventoryFileWriter.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\PurchaseStockTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\ShopAppropriateStockCalculateTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\SmartMatCurrentInfo.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\SmartMatDeliveryRegisterTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\SmartMatGoodsInfo.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\StockManager.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\WarehouseAppropriateStockCalculateTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\util\FileManagerTasklet.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\config\BcartShipmentStatusConverter.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\config\CorsConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\config\JwtAuthenticationFilter.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\config\JwtTokenProvider.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\config\SecurityConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\BCartApiConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\BCartOrderStatus.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\BcartShipmentStatus.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\CompanyType.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\Constants.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\ConsumptionTaxType.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\DeliveryDetailStatus.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\DeliveryStatus.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\EstimateStatus.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\FinanceConstants.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\FinancePeriodConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\Flag.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\OfficeCode.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\OfficeShopNo.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\OrderDetailStatus.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\OrderRoute.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\OrderStatus.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\PaymentType.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\SendOrderDetailStatus.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\SmileAccountsReceivableType.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\SmileBillingType.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\SmileMeisaiKubun.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\SmileOrderDetailType.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\SmileSlipConsumptionTaxCalculationType.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\SmileTaxType.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\SmileTransactionType.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\SmileTransactionTypeAttribute.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\StockLogReason.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\TaxCategory.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\TaxType.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\constant\UnitType.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\validation
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\audit
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\estimate
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\goods
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\master
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\order
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\purchase
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\query
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\smile
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\stock
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\AbstractCompanyEntity.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\CommonAjaxObject.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\ICompanyEntity.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\IEntity.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\TAccessLog.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\VSalesMonthlySummary.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\audit\FinanceAuditLog.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\products
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\productSets
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartCategories.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartCategoriesApiResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartChangeHistory.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartLogistics.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartMember.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartMemberApiResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartMemberOtherAddresses.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartOrder.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartOrderProduct.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartOrdersApiResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartProducts.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartProductsApiResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartProductSets.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartProductSetsApiResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\BCartVolumeDiscount.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\CustomerMapping.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\DeliveryMapping.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\TSmileOrderImportFile.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\products\BCartProductCustom.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\products\BCartProductSetCustom.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\productSets\BCartGroupPrice.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\productSets\BCartSpecialPrice.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\productSets\Customs.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\bcart\productSets\StockParent.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\BCartGroupPricePK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\BCartOrderProductPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\BCartProductSetCustomPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\BCartSpecialPricePK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\CodePK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\MDeliveryPersonPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\MPartnerGoodsPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\MSalesGoodsPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\MSupplierOpeningBalancePK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\ReturnDetailPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\TAccountsPayableSummaryPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\TAccountsReceivableSummaryPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\TComparisonDetailPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\TComparisonGroupPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\TConsistencyReviewPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\TDeliveryDetailPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\TEstimateDetailPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\TOrderDetailPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\TPurchaseDetailPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\TReturnDetailPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\TSendOrderDetailPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\TShopAppropriateStockPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\TStockPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\TWarehouseAppropriateStockPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\VEstimateGoodsPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\VEstimateGoodsSpecialPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\VSalesMonthlySummaryPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\WSalesGoodsPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\WSmileOrderOutputFilePK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\WSmilePartnerPK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\embeddable\WSmilePurchaseOutputFilePK.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\estimate\IVEstimateGoods.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\estimate\TComparisonDetail.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\estimate\TComparisonGroup.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\estimate\TEstimate.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\estimate\TEstimateComparison.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\estimate\TEstimateDetail.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\estimate\VEstimateGoods.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\estimate\VEstimateGoodsSpecial.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\CutoffType.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\MfAccountMaster.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\MMfClientMapping.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\MMfEnumTranslation.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\MMfJournalRule.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\MMfOauthClient.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\MMfSubAccount.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\MPartnerGroup.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\MPaymentMfRule.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\MSupplierOpeningBalance.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsPayableSummary.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\TAccountsReceivableSummary.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\TCashbookImportHistory.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\TConsistencyReview.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\TInvoice.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthState.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\TPaymentMfAuxRow.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\finance\TPaymentMfImportHistory.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\goods\CustomGoodsUnit.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\goods\IPartnerGoodsPriceChangePlan.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\goods\ISalesGoods.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\goods\MGoods.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\goods\MGoodsUnit.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\goods\MPartnerGoods.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\goods\MPartnerGoodsPriceChangePlan.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\goods\MSalesGoods.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\goods\WSalesGoods.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\master\MAsana.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\master\MCompany.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\master\MDeliveryPerson.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\master\MLoginUser.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\master\MMaker.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\master\MPartner.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\master\MPartnerCategory.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\master\MPaymentSupplier.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\master\MShop.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\master\MShopLinkedFile.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\master\MSmartMat.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\master\MSupplier.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\master\MSupplierShopMapping.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\master\MTaxRate.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\master\MWarehouse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\master\WSmilePartner.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\order\IOrderDetailEntity.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\order\MDeliveryDestination.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\order\TDelivery.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\order\TDeliveryDetail.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\order\TOrder.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\order\TOrderDetail.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\order\TReturn.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\order\TReturnDetail.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\purchase\MPurchasePrice.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\purchase\MPurchasePriceChangePlan.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\purchase\MPurchasePriceLog.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\purchase\TPurchase.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\purchase\TPurchaseDetail.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\purchase\TQuoteImportDetail.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\purchase\TQuoteImportHeader.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\purchase\TSendOrder.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\purchase\TSendOrderDetail.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\query\CustomSalesGoods.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\smile\ISmileGoodsFile.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\smile\TSmilePayment.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\smile\WSmileOrderOutputFile.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePayment.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\smile\WSmilePurchaseOutputFile.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\stock\IStockEntity.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\stock\TShopAppropriateStock.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\stock\TStock.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\stock\TStockLog.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\model\stock\TWarehouseAppropriateStock.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\audit
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\bcart
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\estimate
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\goods
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\master
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\order
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\purchase
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\smile
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\stock
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\AccessLogRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\VSalesMonthlySummaryRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\audit\FinanceAuditLogRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\bcart\BCartCategoriesRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\bcart\BCartChangeHistoryRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\bcart\BCartGroupPriceRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\bcart\BCartLogisticsRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\bcart\BCartMemberOtherAddressesRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\bcart\BCartMemberRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\bcart\BCartOrderProductRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\bcart\BCartOrderRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\bcart\BCartOrderRepositoryCustom.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\bcart\BCartOrderRepositoryCustomImpl.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\bcart\BCartProductSetsRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\bcart\BCartProductsRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\bcart\BCartSpecialPriceRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\bcart\BCartVolumeDiscountRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\bcart\CustomerMappingRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\bcart\DeliveryMappingRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\bcart\TSmileOrderImportFileRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\estimate\TComparisonDetailRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\estimate\TComparisonGroupRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\estimate\TEstimateComparisonRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\estimate\TEstimateDetailRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\estimate\TEstimateRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\estimate\VEstimateGoodsRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\estimate\VEstimateGoodsSpecialRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance\MfAccountMasterRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance\MMfClientMappingRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance\MMfEnumTranslationRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance\MMfJournalRuleRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance\MMfOauthClientRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance\MMfSubAccountRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance\MPartnerGroupRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance\MPaymentMfRuleRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance\MSupplierOpeningBalanceRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance\TAccountsPayableSummaryRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance\TAccountsReceivableSummaryRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance\TCashbookImportHistoryRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance\TConsistencyReviewRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance\TInvoiceRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance\TMfOauthStateRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance\TMfOauthTokenRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance\TPaymentMfAuxRowRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\finance\TPaymentMfImportHistoryRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\goods\GoodsRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\goods\GoodsUnitRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\goods\MPartnerGoodsPriceChangePlanRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\goods\MPartnerGoodsRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\goods\MSalesGoodsRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\goods\WSalesGoodsRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\master\CompanyRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\master\LoginUserRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\master\MakerRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\master\MAsanaRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\master\MDeliveryDestinationRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\master\MDeliveryPersonRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\master\MShopLinkedFileRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\master\MSmartMatRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\master\MSupplierRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\master\MSupplierShopMappingRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\master\MTaxRateRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\master\MWarehouseRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\master\PartnerRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\master\ShopRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\master\WSmilePartnerRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\order\DeletedOrderRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\order\TDeliveryDetailRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\order\TDeliveryRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\order\TOrderDetailRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\order\TOrderRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\order\TReturnDetailRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\order\TReturnRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\purchase\MPaymentSupplierRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\purchase\MPurchasePriceChangePlanRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\purchase\MPurchasePriceLogRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\purchase\MPurchasePriceRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\purchase\TPurchaseDetailRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\purchase\TPurchaseRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\purchase\TQuoteImportDetailRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\purchase\TQuoteImportHeaderRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\purchase\TSendOrderDetailRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\purchase\TSendOrderRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\smile\TSmilePaymentRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\smile\WSmileOrderOutputFileRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\smile\WSmilePaymentRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\smile\WSmilePurchaseOutputFileRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\stock\TShopAppropriateStockRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\stock\TStockLogRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\stock\TStockRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\repository\stock\TWarehouseAppropriateStockRepository.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\audit
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\data
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\estimate
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\goods
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\login
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\master
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\order
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\purchase
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\smile
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\stock
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\util
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\CommonService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\CustomService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\audit\AuditLogQueryService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\BCartCategoriesService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\BCartChangeHistoryService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\BCartGroupPriceService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\BCartLogisticsService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\BCartMemberOtherAddressesService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\BCartMemberService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\BCartOrderProductService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\BCartOrderService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\BCartPendingChangesQueryService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\BCartProductDescriptionService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\BCartProductSetsPricingService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\BCartProductSetsReflectService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\BCartProductSetsService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\BCartProductsService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\BCartReflectTransactionService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\BCartShippingInputService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\BCartSpecialPriceService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\BCartVolumeDiscountService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\CustomerMappingService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\DeliveryMappingService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\bcart\TSmileOrderImportFileService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\data\AccessLogService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\data\LoginUser.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\estimate\EstimateCompareService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\estimate\EstimateComparisonCreateService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\estimate\EstimateCreateService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\estimate\EstimateGoodsSearchService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\estimate\EstimatePdfService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\estimate\TEstimateComparisonService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\estimate\TEstimateDetailService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\estimate\TEstimateService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\estimate\VEstimateGoodsService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\estimate\VEstimateGoodsSpecialService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\AccountingStatusService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableIntegrityService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsPayableLedgerService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\AccountsReceivableBulkVerifyService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\CashBookConvertService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\ConsistencyReviewService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\InvoiceImportService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\MfAccountMasterService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\MfHealthCheckService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\MfPeriodConstants.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\MfTaxResolver.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\MMfClientMappingService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\MMfJournalRuleService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\MMfSubAccountService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\MPartnerGroupService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\PayableBalanceCalculator.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCellReader.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfCsvWriter.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfExcelParser.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfImportService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\PaymentMfRuleService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\PurchaseJournalCsvService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\SalesJournalCsvService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\SupplierBalancesService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsPayableSummaryService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\TAccountsReceivableSummaryService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\TInvoiceService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfAccount.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfAccountsResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfAccountSyncService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfBalanceReconcileService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfDebugApiClient.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfEnumTranslationService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfHttpClientConfig.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfJournal.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfJournalCacheService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfJournalFetcher.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfJournalReconcileService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfJournalsResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthStateStore.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningBalanceService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningJournalDetector.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfPaymentAggregator.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfReAuthRequiredException.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfScopeConstants.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfScopeInsufficientException.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfSupplierLedgerService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfTax.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfTaxesResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfTenantMismatchException.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfTenantResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfTokenResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfTokenStatus.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfTrialBalanceBsResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\goods\CommonSalesGoodsService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\goods\MGoodsService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\goods\MGoodsUnitService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\goods\MPartnerGoodsPriceChangePlanService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\goods\MPartnerGoodsService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\goods\MSalesGoodsService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\goods\WSalesGoodsService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\login\LoginUserService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\master\MAsanaService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\master\MCompanyService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\master\MDeliveryPersonService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\master\MMakerService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\master\MPartnerService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\master\MPaymentSupplierService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\master\MShopLinkedFileService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\master\MShopService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\master\MSmartMatService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\master\MSupplierService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\master\MSupplierShopMappingService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\master\MTaxRateService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\master\MWarehouseService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\master\WSmilePartnerService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\order\MDeliveryDestinationService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\order\TDeliveryDetailService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\order\TDeliveryService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\order\TOrderDetailService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\order\TOrderService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\order\TReturnDetailService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\order\TReturnService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\order\VSalesMonthlySummaryService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\purchase\MPurchasePriceChangePlanService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\purchase\MPurchasePriceLogService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\purchase\MPurchasePriceService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\purchase\QuoteImportService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\purchase\SendOrderCreateService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\purchase\TPurchaseDetailService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\purchase\TPurchaseService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\purchase\TSendOrderDetailService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\purchase\TSendOrderService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\smile\TSmilePaymentService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\smile\WSmileOrderOutputFileService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\smile\WSmilePurchaseOutputFileService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\stock\TShopAppropriateStockService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\stock\TStockLogService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\stock\TStockService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\stock\TWarehouseAppropriateStockService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\util\LoginUserUtil.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\bcart
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\estimate
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\finance
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\goods
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\master
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\order
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\purchase
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\smile
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\stock
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\CommonSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\MPartnerGoodsPriceChangePlanSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\VSalesMonthlySummarySpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\bcart\BCartOrderSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\estimate\TEstimateDetailSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\estimate\TEstimateSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\estimate\VEstimateGoodsSpecialSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\estimate\VEstimateGoodsSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\finance\TInvoiceSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\goods\GoodsSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\goods\MGoodsUnitSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\goods\MPartnerGoodsSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\goods\MSalesGoodsSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\goods\WSalesGoodsSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\master\LoginUserSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\master\MakerSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\master\MCompanySpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\master\MShopSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\master\MSmartMatSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\master\MSupplierSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\master\MTaxRateSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\master\PartnerSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\master\WarehouseSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\order\TDeliveryDetailSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\order\TDeliverySpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\order\TOrderDetailSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\order\TOrderSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\order\TReturnDetailSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\order\TReturnSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\purchase\MPurchasePriceChangePlanSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\purchase\MPurchasePriceLogSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\purchase\MPurchasePriceSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\purchase\TPurchaseDetailSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\purchase\TPurchaseSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\purchase\TSendOrderDetailSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\stock\TStockLogSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\specification\stock\TStockSpecification.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\validation\CompanyEntity.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\validation\ShopEntity.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\audit
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\auth
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\batch
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\bcart
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\common
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\comparison
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\dashboard
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\estimate
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\goods
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\master
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\order
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\stock
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\user
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\audit\AuditLogResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\auth\LoginRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\auth\LoginResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\auth\UserInfoResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\bcart\BCartCategoryResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\bcart\BCartCategoryUpdateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\bcart\BCartChangeHistoryResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\bcart\BCartPendingChangeResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\bcart\BCartProductDescriptionUpdateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\bcart\BCartProductResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\bcart\BCartProductSetPricingRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\bcart\BCartProductSetPricingResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\bcart\BCartReflectRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\bcart\BCartReflectResult.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\bcart\BCartShippingBulkStatusRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\bcart\BCartShippingInputResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\bcart\BCartShippingSaveResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\bcart\BCartShippingUpdateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\common\PageResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\comparison\ComparisonCreateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\comparison\ComparisonDetailCreateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\comparison\ComparisonDetailResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\comparison\ComparisonGroupCreateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\comparison\ComparisonGroupResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\comparison\ComparisonResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\comparison\ComparisonStatusUpdateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\dashboard\SalesSummaryResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\estimate\CompareGoodsResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\estimate\EstimateCreateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\estimate\EstimateDetailCreateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\estimate\EstimateDetailResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\estimate\EstimateGoodsSearchResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\estimate\EstimateResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\estimate\EstimateStatusUpdateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\estimate\IEstimateDetail.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\cashbook
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\paymentmf
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\AccountingStatusResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\AccountsPayableLedgerResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\AccountsPayableResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\AccountsPayableSummaryProjection.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\AccountsPayableSummaryResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\AccountsPayableVerifyRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\AccountsReceivableAggregateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\AccountsReceivableBulkVerifyRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\AccountsReceivableBulkVerifyResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\AccountsReceivableResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\AccountsReceivableSummaryResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\AccountsReceivableVerifyRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\BulkPaymentDateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\ConsistencyReviewRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\ConsistencyReviewResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\ErrorResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\IntegrityReportResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\InvoiceImportResult.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\InvoiceResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\MfEnumTranslationRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\MfEnumTranslationResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\MfExportToggleRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\MfHealthResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\MfOauthClientRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\MfOauthClientResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\MfOpeningBalanceFetchResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\MfSupplierLedgerResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\PartnerGroupRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\PartnerGroupResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\PaymentDateUpdateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\PurchaseJournalExportPreviewResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\SupplierBalancesResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\SupplierOpeningBalanceResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\SupplierOpeningBalanceUpdateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\cashbook\CashBookPreviewResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\cashbook\CashBookPreviewRow.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\cashbook\MfClientMappingRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\cashbook\MfClientMappingResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\cashbook\MfJournalRuleRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\cashbook\MfJournalRuleResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\paymentmf\AppliedWarning.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\paymentmf\DuplicateWarning.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\paymentmf\PaymentMfAuxRowResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\paymentmf\PaymentMfHistoryResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\paymentmf\PaymentMfPreviewResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\paymentmf\PaymentMfPreviewRow.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\paymentmf\PaymentMfRuleRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\paymentmf\PaymentMfRuleResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\finance\paymentmf\VerifiedExportPreviewResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\goods\GoodsCreateForm.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\goods\GoodsCreateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\goods\GoodsDetailResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\goods\GoodsModifyForm.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\goods\GoodsResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\goods\OrderHistoryResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\goods\PartnerGoodsDetailResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\goods\PartnerGoodsResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\goods\PartnerGoodsUpdateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\goods\SalesGoodsCreateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\goods\SalesGoodsDetailResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\goods\SalesGoodsResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\goods\SalesGoodsUpdateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\master\DeliveryDestinationResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\master\MakerCreateForm.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\master\MakerCreateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\master\MakerModifyForm.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\master\MakerResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\master\PartnerCreateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\master\PartnerResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\master\PaymentSupplierResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\master\ShopLinkedFileResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\master\ShopLinkedFileUpdateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\master\ShopResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\master\SupplierCreateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\master\SupplierResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\master\WarehouseCreateForm.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\master\WarehouseCreateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\master\WarehouseModifyForm.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\master\WarehouseResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\order\OrderDetailResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\order\OrderResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\PurchaseDetailResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\PurchaseHeaderResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\PurchaseListResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\PurchasePriceChangePlanBulkRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\PurchasePriceChangePlanCreateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\PurchasePriceChangePlanResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\PurchasePriceCreateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\PurchasePriceResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\PurchaseResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\QuoteImportCreateNewRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\QuoteImportCreateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\QuoteImportDetailResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\QuoteImportHeaderResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\QuoteImportMatchRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\QuoteImportSupplierMatchRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\SendOrderCreateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\SendOrderDetailResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\SendOrderDetailStatusUpdateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\SendOrderResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\SupplierQuoteDataResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\purchase\SupplierQuoteHistoryResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\stock\AppropriateStockEntity.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\stock\StockCreateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\stock\StockLogResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\stock\StockResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\user\UserCreateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\user\UserResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\dto\user\UserUpdateRequest.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\exception\FinanceBusinessException.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\exception\FinanceInternalException.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\exception\GlobalExceptionHandler.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\security\LoginUserSecurityBean.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\gson
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\BigDecimalUtil.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\CollectionUtil.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\CryptoUtil.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\DateTimeUtil.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\DeliveryUtil.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\FileUtil.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\GoodsUtil.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\KatakanaHalfToFullConverter.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\OauthCryptoUtil.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\OrderUtil.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\PartnerRegister.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\PurchaseUtil.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\SensitiveLogMasker.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\StaticsCalculator.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\StringUtil.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\gson\bcart
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\gson\CustomDateTypeAdapter.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\gson\CustomNumberDeserializer.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\gson\GsonJsonElementType.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\gson\IntegerTypeAdapter.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\gson\LocalDateTypeAdapter.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\gson\bcart\productSets
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\gson\bcart\productSets\GroupPriceDeserializer.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\gson\bcart\productSets\SpecialPriceDeserializer.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\gson\bcart\productSets\StockParentDeserializer.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\util\gson\bcart\productSets\VolumeDiscountDeserializer.java
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/util/CryptoUtil.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/util/OauthCryptoUtil.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/db/migration/V033__reencrypt_mf_oauth_secrets.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/resources/db/migration/V032__add_mf_tenant_binding.sql' in C:\project\odamitsu-data-hub
 succeeded in 1836ms:
package jp.co.oda32.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * AES-256 (GCM) ベースの対称暗号化ユーティリティ。
 * <p>
 * {@code app.crypto.key} (パスフレーズ) と {@code app.crypto.salt} (hex 文字列) を
 * application.yml or 環境変数で設定する。主に MF OAuth Client Secret / access_token /
 * refresh_token の DB 保存時に利用する。
 *
 * <ul>
 *   <li>鍵が未設定 or 長さ不足なら起動時に fail fast（ログに鍵は出さない）</li>
 *   <li>Spring Security の {@link Encryptors#delux} を使用（PBKDF2 + AES/GCM）</li>
 *   <li>encrypt/decrypt の出力は <b>Hex エンコード文字列</b>。内部実装は
 *       {@code HexEncodingTextEncryptor(stronger(...))} で、出力は IV(16B) + CipherText + GMAC tag を
 *       連結した hex。Base64 ではない。</li>
 *   <li>salt は <b>hex 文字列必須</b> ({@code PBKDF2} 用)。Base64 や非 hex 文字を渡すと
 *       実行時に {@code IllegalArgumentException: Detected a Non-hex character} で起動失敗する。</li>
 * </ul>
 *
 * @since 2026/04/20
 */
@Component
@Log4j2
public class CryptoUtil {

    private final String password;
    private final String salt;
    private TextEncryptor encryptor;

    public CryptoUtil(@Value("${app.crypto.key:}") String password,
                      @Value("${app.crypto.salt:}") String salt) {
        this.password = password;
        this.salt = salt;
    }

    @PostConstruct
    void init() {
        if (password == null || password.isBlank() || password.length() < 16) {
            throw new IllegalStateException(
                    "app.crypto.key が未設定または短すぎます（16 文字以上必須）。環境変数 APP_CRYPTO_KEY を設定してください。");
        }
        if (salt == null || salt.isBlank()) {
            throw new IllegalStateException(
                    "app.crypto.salt が未設定です。16 byte 以上の hex 文字列（例: 0123456789abcdef）を環境変数 APP_CRYPTO_SALT で設定してください。");
        }
        // hex 以外が混ざっていると delux() 内部で PBKDF2 salt デコード時に例外になる。
        // 起動時に fail-fast してメッセージを明確にする (B-W1)。
        if (!HEX_ONLY.matcher(salt).matches()) {
            throw new IllegalStateException(
                    "app.crypto.salt は hex 文字列 (0-9a-f) のみ受け付けます。現在値は hex 以外を含みます。");
        }
        if (salt.length() < 16) {
            throw new IllegalStateException(
                    "app.crypto.salt は 16 文字以上 (= 8 byte) の hex 必須。推奨 32 文字 (16 byte)。");
        }
        // Encryptors.delux: PBKDF2 + AES/GCM/NoPadding + 16byte IV + Hex エンコード出力
        this.encryptor = Encryptors.delux(password, salt);
        log.info("CryptoUtil 初期化完了（AES-256 GCM, Hex 出力）");
    }

    /** 平文を暗号化して Hex 文字列を返す (IV+CipherText+GMAC tag 連結)。null は null を返す。 */
    public String encrypt(String plain) {
        if (plain == null) return null;
        return encryptor.encrypt(plain);
    }

    /** Hex 暗号文を復号して平文を返す。null は null を返す。 */
    public String decrypt(String encrypted) {
        if (encrypted == null) return null;
        return encryptor.decrypt(encrypted);
    }

    private static final java.util.regex.Pattern HEX_ONLY = java.util.regex.Pattern.compile("^[0-9a-fA-F]+$");
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1891ms:
-- V032: m_mf_oauth_client に MF クラウド会計 tenant binding 列を追加
--
-- 背景 (DESIGN-DECISION P1-01 / Cluster F DD-F-04):
--   現行 OAuth フローは access_token / refresh_token のみを保存しており、
--   別会社 MF を誤認可しても callback / refresh とも成功扱いになる。
--   翌期 (2026-06-21) の MF アプリ切替前に、tenant API (/v2/tenant) で取得した
--   tenant_id を保存し、callback 時 / refresh 後 / 任意のタイミングで一致検証を
--   行えるようにする (別会社誤接続検知)。
--
-- 列:
--   - mf_tenant_id     : MF tenant API のレスポンス id。callback 直後に保存し、
--                        以後の access_token 利用時に一致確認する。
--                        NULL は「未バインド (旧データ互換)」を示し、初回 callback で
--                        binding 確定する扱い。
--   - mf_tenant_name   : 同 tenant 名 (UI 表示用)。
--   - tenant_bound_at  : binding 確定タイムスタンプ。NULL なら未バインド。
--
-- 互換性:
--   既存 prod/dev 環境の m_mf_oauth_client レコードは初回適用時 NULL のままとなる。
--   `MfOauthService` 側で「mf_tenant_id IS NULL なら次回 callback で確定」扱いに
--   フォールバックする (Cluster F DD-F-04 設計)。
ALTER TABLE m_mf_oauth_client
    ADD COLUMN IF NOT EXISTS mf_tenant_id    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS mf_tenant_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS tenant_bound_at TIMESTAMP;

COMMENT ON COLUMN m_mf_oauth_client.mf_tenant_id    IS 'MF tenant API (/v2/tenant) で取得した tenant id。callback 直後保存、refresh 前後で一致検証';
COMMENT ON COLUMN m_mf_oauth_client.mf_tenant_name  IS '同 tenant 名 (表示用)';
COMMENT ON COLUMN m_mf_oauth_client.tenant_bound_at IS 'tenant binding 確定タイムスタンプ。NULL なら未バインド (旧データ互換)';
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1950ms:
package jp.co.oda32.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * MF OAuth クライアント / トークン専用 AES-256 (GCM) 暗号化ユーティリティ (P1-05 案 C.3)。
 * <p>
 * 既存 {@link CryptoUtil} (社内 password 等) とは <b>別の鍵 / salt</b> を使う。
 * これにより、リポジトリに commit 済みの dev fallback (CryptoUtil 用) が万一 prod で
 * 流入しても、MF OAuth client_secret / access_token / refresh_token は復号できないようにする。
 *
 * <p>対象カラム:
 * <ul>
 *   <li>{@code m_mf_oauth_client.client_secret_enc}</li>
 *   <li>{@code t_mf_oauth_token.access_token_enc}</li>
 *   <li>{@code t_mf_oauth_token.refresh_token_enc}</li>
 * </ul>
 *
 * <p>{@code app.crypto.oauth-key} / {@code app.crypto.oauth-salt} は <b>dev / prod 共通で env 必須</b>。
 * 未設定なら起動時に fail-fast する。鍵生成 / セットアップ手順は
 * {@code claudedocs/runbook-mf-oauth-keys.md} を参照。
 *
 * @since 2026/05/04
 */
@Component
@Log4j2
public class OauthCryptoUtil {

    private final String password;
    private final String salt;
    private TextEncryptor encryptor;

    public OauthCryptoUtil(@Value("${app.crypto.oauth-key:}") String password,
                           @Value("${app.crypto.oauth-salt:}") String salt) {
        this.password = password;
        this.salt = salt;
    }

    @PostConstruct
    void init() {
        if (password == null || password.isBlank() || password.length() < 16) {
            throw new IllegalStateException(
                    "app.crypto.oauth-key が未設定または短すぎます（16 文字以上必須）。"
                            + "環境変数 APP_CRYPTO_OAUTH_KEY を設定してください。"
                            + "詳細は claudedocs/runbook-mf-oauth-keys.md (Step 1-2) を参照。");
        }
        if (salt == null || salt.isBlank()) {
            throw new IllegalStateException(
                    "app.crypto.oauth-salt が未設定です。16 byte 以上の hex 文字列"
                            + "（例: 0123456789abcdef0123456789abcdef）を環境変数 APP_CRYPTO_OAUTH_SALT で設定してください。"
                            + "詳細は claudedocs/runbook-mf-oauth-keys.md (Step 1-2) を参照。");
        }
        if (!HEX_ONLY.matcher(salt).matches()) {
            throw new IllegalStateException(
                    "app.crypto.oauth-salt は hex 文字列 (0-9a-f) のみ受け付けます。現在値は hex 以外を含みます。");
        }
        if (salt.length() < 16) {
            throw new IllegalStateException(
                    "app.crypto.oauth-salt は 16 文字以上 (= 8 byte) の hex 必須。推奨 32 文字 (16 byte)。");
        }
        // CryptoUtil と同じ AES-256/GCM + PBKDF2 + Hex 出力。鍵 / salt のみ別。
        this.encryptor = Encryptors.delux(password, salt);
        log.info("OauthCryptoUtil 初期化完了（MF OAuth 専用 AES-256 GCM, Hex 出力）");
    }

    /** 平文を暗号化して Hex 文字列を返す。null は null を返す。 */
    public String encrypt(String plain) {
        if (plain == null) return null;
        return encryptor.encrypt(plain);
    }

    /** Hex 暗号文を復号して平文を返す。null は null を返す。 */
    public String decrypt(String encrypted) {
        if (encrypted == null) return null;
        return encryptor.decrypt(encrypted);
    }

    private static final java.util.regex.Pattern HEX_ONLY = java.util.regex.Pattern.compile("^[0-9a-fA-F]+$");
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2951ms:
package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.sql.Connection;
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
 * <p>動作:
 * <ol>
 *   <li>環境変数から旧鍵 (未設定なら application-dev.yml の dev fallback) と新鍵を取得</li>
 *   <li>新鍵 (oauth-key/salt) が未設定なら fail-fast (本 migration をスキップさせない)</li>
 *   <li>autoCommit OFF で各テーブルを SELECT → 旧鍵 decrypt → 新鍵 encrypt → UPDATE</li>
 *   <li>全件成功なら commit、途中で失敗したら rollback して Flyway に例外伝播 → migration 失敗</li>
 * </ol>
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
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            int clientCount = reencrypt(conn, "m_mf_oauth_client", "id", "client_secret_enc", oldCipher, newCipher);
            int accessCount = reencrypt(conn, "t_mf_oauth_token", "id", "access_token_enc", oldCipher, newCipher);
            int refreshCount = reencrypt(conn, "t_mf_oauth_token", "id", "refresh_token_enc", oldCipher, newCipher);
            conn.commit();
            System.out.printf(
                    "[V033] OAuth secrets re-encrypted: client_secret=%d, access_token=%d, refresh_token=%d%n",
                    clientCount, accessCount, refreshCount);
        } catch (Exception e) {
            conn.rollback();
            throw new IllegalStateException(
                    "[V033] 再暗号化に失敗しました。旧鍵 (APP_CRYPTO_KEY/SALT) が変わっている可能性があります。"
                            + " env を元に戻すか、claudedocs/runbook-mf-oauth-keys.md の鍵紛失復旧手順を参照。"
                            + " 元例外: " + e.getMessage(), e);
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }

    /**
     * 指定テーブルの暗号化カラムを「旧鍵で decrypt → 新鍵で encrypt」して UPDATE する。
     * NULL 行はスキップ。autoCommit OFF 前提 (commit / rollback は呼び出し側)。
     *
     * @return 再暗号化した行数
     */
    private static int reencrypt(Connection conn, String table, String pkColumn, String column,
                                 TextEncryptor oldCipher, TextEncryptor newCipher) throws Exception {
        String selectSql = "SELECT " + pkColumn + ", " + column + " FROM " + table
                + " WHERE " + column + " IS NOT NULL";
        String updateSql = "UPDATE " + table + " SET " + column + " = ? WHERE " + pkColumn + " = ?";
        int count = 0;
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
                    throw new IllegalStateException(
                            "[V033] " + table + "." + column + " (" + pkColumn + "=" + pk + ") の復号に失敗。"
                                    + " 旧鍵が一致していません: " + e.getMessage(), e);
                }
                String newEnc = newCipher.encrypt(plain);
                update.setString(1, newEnc);
                update.setInt(2, pk);
                update.addBatch();
                count++;
            }
            if (count > 0) {
                update.executeBatch();
            }
        }
        return count;
    }

    private static String envOrDefault(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/MMfOauthClient.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfTokenStatus.java' in C:\project\odamitsu-data-hub
 succeeded in 1764ms:
package jp.co.oda32.domain.service.finance.mf;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * MF OAuth2 トークン状態（画面表示用）。
 * <p>
 * P1-01 (DD-F-04): {@code mfTenantId} / {@code mfTenantName} / {@code tenantBoundAt}
 * を追加。別会社 MF の誤接続検知のため、UI で「連携先: {tenantName} (id: {tenantId})」を
 * 表示する。tenantBoundAt が NULL なら旧データ互換 (初回 callback で binding 確定予定)。
 * <p>
 * P1-04 (案 α): {@code refreshTokenIssuedAt} / {@code daysUntilReauth} を追加。
 * MF refresh_token は 540 日寿命のため、現 active token の {@code add_date_time} を
 * 発行日として残日数を算出し、グローバル top header の予兆 banner で表示する。
 * {@code reAuthRequired} は「{@code daysUntilReauth} ? 0」のとき true (= 既に失効)。
 * <p>
 * T6 (2026-05-04): {@code missingScopes} / {@code extraScopes} / {@code scopeOk} を追加。
 * admin が画面で {@code scope} を編集して必須 scope を消した場合に、関連 API が
 * 403 で失敗するまで気付かない問題への対応。{@link MfScopeConstants#analyze(String)}
 * で解析し、{@code MfScopeBanner} で UI 警告を出す。
 */
public record MfTokenStatus(
        boolean configured,         // Client ID/Secret が DB に登録済みか
        boolean connected,          // 有効な access_token があるか（期限切れ含む）
        Instant expiresAt,          // access_token 有効期限（未接続時 null）
        String scope,               // トークンの scope
        Instant lastRefreshedAt,    // 最後に token 更新した時刻
        @JsonProperty("reAuthRequired") boolean reAuthRequired,         // refresh_token が期限切れ (daysUntilReauth ? 0) で再認可必須
        String mfTenantId,          // バインド済 MF tenant id (P1-01)
        String mfTenantName,        // バインド済 MF tenant 名 (P1-01)
        Instant tenantBoundAt,      // tenant binding 確定時刻 (P1-01)
        @JsonProperty("refreshTokenIssuedAt") Instant refreshTokenIssuedAt, // 現 active token の add_date_time (P1-04)
        @JsonProperty("daysUntilReauth") Integer daysUntilReauth,           // 540 日 - 経過日数 (P1-04、未接続時 null)
        List<String> missingScopes, // T6: 必須だが scope に含まれていないもの (空 list = OK)
        List<String> extraScopes,   // T6: scope に含まれているが必須ではないもの (空 list = OK、警告レベル)
        @JsonProperty("scopeOk") boolean scopeOk // T6: missingScopes.isEmpty() の short-hand (UI 判定用)
) {}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1786ms:
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
            log.info("MF tenant binding 初回確定 clientId={} tenantId={} tenantName={}",
                    client.getId(), observedId, observedName);
            client.setMfTenantId(observedId);
            client.setMfTenantName(observedName);
            client.setTenantBoundAt(now);
            client.setModifyDateTime(now);
            client.setModifyUserNo(userNo);
            clientRepository.save(client);
            return;
        }

        if (!client.getMfTenantId().equals(observedId)) {
            // 不一致 = 別会社 MF を認可した可能性
            log.error("MF tenant 不一致を検知 clientId={} bound={} observed={}",
                    client.getId(), client.getMfTenantId(), observedId);
            throw new MfTenantMismatchException(client.getMfTenantId(), observedId,
                    "既存連携と異なる MF tenant です: 既存=" + client.getMfTenantId()
                            + " / 新規=" + observedId
                            + ". 旧連携を解除してから再認可してください");
        }

        // 一致: 名称差分があれば追従更新 (id 不変なので safe)
        if (observedName != null && !observedName.equals(client.getMfTenantName())) {
            log.info("MF tenant 名称を更新 clientId={} tenantId={} oldName={} newName={}",
                    client.getId(), observedId, client.getMfTenantName(), observedName);
            client.setMfTenantName(observedName);
            client.setModifyDateTime(now);
            client.setModifyUserNo(userNo);
            clientRepository.save(client);
        }
    }

    private void persistToken(MMfOauthClient client, MfTokenResponse res, Integer userNo) {
        Timestamp now = Timestamp.from(Instant.now());
        // SF-21: refresh_token は MF が返さないこともある (rotation OFF の場合)。
        // null fallback は **既存 active token の refresh_token_enc を再利用** する。
        // softDeleteActiveTokens 実行前に既存 token を読む必要があるため順序に注意。
        String refreshTokenEnc;
        if (res.refreshToken() != null) {
            refreshTokenEnc = cryptoUtil.encrypt(res.refreshToken());
        } else {
            refreshTokenEnc = tokenRepository
                    .findFirstByClientIdAndDelFlgOrderByIdDesc(client.getId(), "0")
                    .map(TMfOauthToken::getRefreshTokenEnc)
                    .orElseThrow(() -> new IllegalStateException(
                            "MF token レスポンスに refresh_token が無く、再利用可能な既存 token も存在しません"));
        }
        tokenRepository.softDeleteActiveTokens(client.getId(), now, userNo);
        long expiresInSec = res.expiresIn() != null ? res.expiresIn() : 3600L;
        Timestamp expiresAt = Timestamp.from(Instant.now().plus(expiresInSec, ChronoUnit.SECONDS));
        TMfOauthToken token = TMfOauthToken.builder()
                .clientId(client.getId())
                .accessTokenEnc(cryptoUtil.encrypt(res.accessToken()))
                .refreshTokenEnc(refreshTokenEnc)
                .tokenType(res.tokenType() != null ? res.tokenType() : "Bearer")
                .expiresAt(expiresAt)
                .scope(res.scope())
                .delFlg("0")
                .addDateTime(now)
                .addUserNo(userNo)
                .build();
        tokenRepository.save(token);
    }

    /** redirect_uri のホワイトリスト検証。完全一致でのみ受け付ける。 */
    private static void validateRedirectUri(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("redirect_uri が未入力です");
        }
        if (!ALLOWED_REDIRECT_URIS.contains(uri)) {
            throw new IllegalArgumentException(
                    "redirect_uri が許可リストにありません。許可済み: " + ALLOWED_REDIRECT_URIS);
        }
        try {
            URI u = URI.create(uri);
            if (u.getScheme() == null || (!u.getScheme().equals("http") && !u.getScheme().equals("https"))) {
                throw new IllegalArgumentException("redirect_uri の scheme は http/https のみ");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("redirect_uri が URI として不正: " + uri, e);
        }
    }

    /** {@link #buildAuthorizeUrl(Integer)} 戻り値。 */
    public record AuthorizeUrl(String url, String state) {}

    /**
     * {@link #loadActiveTokenSnapshot()} 戻り値。refresh 判定や HTTP 呼び出しで使う。
     * <p>SF-02: client_secret / access_token / refresh_token は機密値のため
     * {@link #toString()} で {@code ***} に置換 (ログ漏洩対策)。
     */
    public record TokenSnapshot(
            MMfOauthClient client,
            String plainSecret,
            String accessToken,
            String plainRefresh,
            Instant expiresAt) {
        public boolean isFresh(long marginSeconds) {
            return Instant.now().isBefore(expiresAt.minusSeconds(marginSeconds));
        }

        @Override
        public String toString() {
            return "TokenSnapshot[clientId=" + (client != null ? client.getId() : null)
                    + ", plainSecret=***, accessToken=***, plainRefresh=***, expiresAt=" + expiresAt + "]";
        }
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1868ms:
package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.util.SensitiveLogMasker;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * マネーフォワードクラウド会計 API への低レベル HTTP クライアント。
 * <p>
 * OAuth2 の token 交換・refresh のみ担当。Journal / accounts 取得は別 Service から
 * {@link #restClient()} を経由して呼ぶ。再認可が必要なエラーは
 * {@link MfReAuthRequiredException} に変換する。
 *
 * <p>SF-01: timeout 設定済み {@link RestClient} (mfRestClient) を field injection で利用。
 * <p>SF-04: error body のロギングは {@link SensitiveLogMasker#mask(String)} 経由でマスキング。
 * <p>SF-07: basic auth header は RFC 6749 §2.3.1 準拠で URL encode してから base64。
 * <p>SF-08: 429/5xx は {@link #executeWithRetry(Supplier, String)} 経由で指数バックオフ。
 * <p>SF-10: {@link #urlEncode(String)} は null を fail-fast。
 * <p>SF-11: URL 組み立ては {@link UriComponentsBuilder} を使用。
 * <p>SF-21: postToken は access_token のみ必須 (refresh_token null fallback は呼び出し側)。
 *
 * @since 2026/04/20
 */
@Component
@Log4j2
public class MfApiClient {

    /** 429/5xx 時のリトライ間隔 (指数バックオフ): 1s → 2s → 4s。 */
    private static final long[] RETRY_BACKOFFS_MS = {1000L, 2000L, 4000L};

    private final RestClient restClient;

    public MfApiClient(@Qualifier("mfRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 認可 URL を組み立てる（ブラウザをリダイレクトするための URL）。
     * PKCE S256 を使う: {@code code_challenge} と {@code code_challenge_method=S256} を付与する (B-4)。
     */
    public String buildAuthorizeUrl(MMfOauthClient client, String state, String codeChallenge) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(client.getAuthorizeUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", client.getClientId())
                .queryParam("redirect_uri", client.getRedirectUri())
                .queryParam("scope", client.getScope())
                .queryParam("state", state);
        if (codeChallenge != null && !codeChallenge.isEmpty()) {
            b.queryParam("code_challenge", codeChallenge)
                    .queryParam("code_challenge_method", "S256");
        }
        return b.build().encode(StandardCharsets.UTF_8).toUriString();
    }

    /**
     * authorization code を access/refresh token に交換する（CLIENT_SECRET_BASIC + PKCE code_verifier）。
     */
    public MfTokenResponse exchangeCodeForToken(
            MMfOauthClient client, String clientSecret, String code, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", client.getRedirectUri());
        if (codeVerifier != null && !codeVerifier.isEmpty()) {
            form.add("code_verifier", codeVerifier);
        }
        return executeWithRetry(
                () -> postToken(client.getTokenUrl(), form, basicAuthHeader(client.getClientId(), clientSecret)),
                "exchangeCodeForToken");
    }

    /** refresh_token で新しい access_token を取得する（CLIENT_SECRET_BASIC）。 */
    public MfTokenResponse refreshToken(MMfOauthClient client, String clientSecret, String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return executeWithRetry(
                () -> postToken(client.getTokenUrl(), form, basicAuthHeader(client.getClientId(), clientSecret)),
                "refreshToken");
    }

    /**
     * 認可されたリクエスト用の RestClient を返す（呼び出し側で Authorization ヘッダを付ける）。
     * MfJournalService など高レベル Service が利用する想定。
     */
    public RestClient restClient() {
        return restClient;
    }

    /**
     * GET /api/v3/journals で指定期間の仕訳一覧を取得。
     * @param startDate 開始日 (yyyy-MM-dd)
     * @param endDate   終了日 (yyyy-MM-dd)
     */
    public MfJournalsResponse listJournals(MMfOauthClient client, String accessToken,
                                            String startDate, String endDate, int page, int perPage) {
        String url = UriComponentsBuilder.fromHttpUrl(client.getApiBaseUrl() + "/api/v3/journals")
                .queryParam("start_date", startDate)
                .queryParam("end_date", endDate)
                .queryParam("page", page)
                .queryParam("per_page", perPage)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        return executeWithRetry(() -> {
            try {
                MfJournalsResponse res = restClient.get()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .retrieve()
                        .body(MfJournalsResponse.class);
                return res != null ? res : new MfJournalsResponse(null);
            } catch (HttpClientErrorException e) {
                HttpStatusCode status = e.getStatusCode();
                String body = e.getResponseBodyAsString();
                log.warn("MF /journals 取得失敗: status={}, body={}", status.value(), SensitiveLogMasker.mask(body));
                if (status.value() == 401) {
                    throw new MfReAuthRequiredException("再認証が必要です。詳細は管理者ログを参照してください", e);
                }
                if (status.value() == 403) {
                    throw new MfScopeInsufficientException("mfc/accounting/journal.read",
                            "MF scope 不足です (mfc/accounting/journal.read 必要)。クライアント設定更新 + 再認証してください", e);
                }
                throw e;
            }
        }, "listJournals");
    }

    /** GET /api/v3/taxes で税区分一覧を取得。 */
    public MfTaxesResponse listTaxes(MMfOauthClient client, String accessToken) {
        String url = client.getApiBaseUrl() + "/api/v3/taxes";
        return executeWithRetry(() -> {
            try {
                MfTaxesResponse res = restClient.get()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .retrieve()
                        .body(MfTaxesResponse.class);
                return res != null ? res : new MfTaxesResponse(null);
            } catch (HttpClientErrorException e) {
                HttpStatusCode status = e.getStatusCode();
                String body = e.getResponseBodyAsString();
                log.warn("MF /taxes 取得失敗: status={}, body={}", status.value(), SensitiveLogMasker.mask(body));
                if (status.value() == 401) {
                    throw new MfReAuthRequiredException("再認証が必要です。詳細は管理者ログを参照してください", e);
                }
                throw e;
            }
        }, "listTaxes");
    }

    /**
     * GET /api/v3/reports/trial_balance_bs で貸借対照表試算表を取得。
     * <p>
     * Phase 0 実測 (設計書 §1) で確定した仕様:
     * <ul>
     *   <li>query は {@code end_date=YYYY-MM-DD} のみ。period / from / date 等は unsupported</li>
     *   <li>closing_balance が end_date 時点の累積残を表す</li>
     *   <li>sub_account 粒度は含まれない (account leaf のみ)</li>
     * </ul>
     * scope {@code mfc/accounting/report.read} が必要。不足時は 403 →
     * {@link MfScopeInsufficientException} に変換。
     *
     * @param endDate 月末日 (yyyy-MM-dd)、締め日 20日想定
     */
    public MfTrialBalanceBsResponse getTrialBalanceBs(MMfOauthClient client, String accessToken,
                                                       String endDate) {
        String url = UriComponentsBuilder.fromHttpUrl(client.getApiBaseUrl() + "/api/v3/reports/trial_balance_bs")
                .queryParam("end_date", endDate)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        return executeWithRetry(() -> {
            try {
                MfTrialBalanceBsResponse res = restClient.get()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .retrieve()
                        .body(MfTrialBalanceBsResponse.class);
                return res != null ? res : new MfTrialBalanceBsResponse(null, endDate, null, null);
            } catch (HttpClientErrorException e) {
                HttpStatusCode status = e.getStatusCode();
                String body = e.getResponseBodyAsString();
                log.warn("MF /trial_balance_bs 取得失敗: status={}, body={}", status.value(), SensitiveLogMasker.mask(body));
                if (status.value() == 401) {
                    throw new MfReAuthRequiredException("再認証が必要です。詳細は管理者ログを参照してください", e);
                }
                if (status.value() == 403) {
                    throw new MfScopeInsufficientException("mfc/accounting/report.read",
                            "MF scope 不足です (mfc/accounting/report.read)。クライアント設定に scope を追加 → 再認証してください", e);
                }
                throw e;
            }
        }, "getTrialBalanceBs");
    }

    /**
     * GET /v2/tenant で MF クラウド会計の tenant (= 連携先事業者) 情報を取得。
     * <p>
     * P1-01 / DD-F-04: 別会社 MF への誤接続を検知するため、callback 直後と
     * refresh 後に呼び出して {@code mf_tenant_id} の一致確認に使う。scope
     * {@code mfc/admin/tenant.read} が必要。不足時は 403 →
     * {@link MfScopeInsufficientException} に変換する。
     * <p>
     * 401 (token 失効) は {@link MfReAuthRequiredException} に変換 (他 endpoint と同様)。
     */
    public MfTenantResponse getTenant(MMfOauthClient client, String accessToken) {
        String url = client.getApiBaseUrl() + "/v2/tenant";
        return executeWithRetry(() -> {
            try {
                MfTenantResponse res = restClient.get()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .retrieve()
                        .body(MfTenantResponse.class);
                if (res == null || res.id() == null || res.id().isBlank()) {
                    throw new IllegalStateException("MF /v2/tenant が tenant id を返却しませんでした");
                }
                return res;
            } catch (HttpClientErrorException e) {
                HttpStatusCode status = e.getStatusCode();
                String body = e.getResponseBodyAsString();
                log.warn("MF /v2/tenant 取得失敗: status={}, body={}", status.value(), SensitiveLogMasker.mask(body));
                if (status.value() == 401) {
                    throw new MfReAuthRequiredException("再認証が必要です。詳細は管理者ログを参照してください", e);
                }
                if (status.value() == 403) {
                    throw new MfScopeInsufficientException("mfc/admin/tenant.read",
                            "MF scope 不足です (mfc/admin/tenant.read 必要)。クライアント設定の scope を更新 → 再認証してください", e);
                }
                throw e;
            }
        }, "getTenant");
    }

    /**
     * GET /api/v3/accounts で勘定科目一覧を取得。
     * @param client 有効な MMfOauthClient
     * @param accessToken Bearer token
     * @return 勘定科目リスト（subAccounts を含む）
     */
    public MfAccountsResponse listAccounts(MMfOauthClient client, String accessToken) {
        String url = client.getApiBaseUrl() + "/api/v3/accounts";
        return executeWithRetry(() -> {
            try {
                MfAccountsResponse res = restClient.get()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .retrieve()
                        .body(MfAccountsResponse.class);
                return res != null ? res : new MfAccountsResponse(null);
            } catch (HttpClientErrorException e) {
                HttpStatusCode status = e.getStatusCode();
                String body = e.getResponseBodyAsString();
                log.warn("MF /accounts 取得失敗: status={}, body={}", status.value(), SensitiveLogMasker.mask(body));
                if (status.value() == 401) {
                    throw new MfReAuthRequiredException("再認証が必要です。詳細は管理者ログを参照してください", e);
                }
                throw e;
            }
        }, "listAccounts");
    }

    private MfTokenResponse postToken(String tokenUrl, MultiValueMap<String, String> form, String basicAuth) {
        try {
            MfTokenResponse res = restClient.post()
                    .uri(tokenUrl)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MfTokenResponse.class);
            // SF-21: refresh_token は MF が返さないこともある (refresh_token_rotation 設定次第)。
            // access_token のみ必須。refresh_token null の場合は呼び出し側で既存値を流用する。
            if (res == null || res.accessToken() == null) {
                throw new IllegalStateException("MF token エンドポイントから不正なレスポンス (access_token なし)");
            }
            return res;
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            String body = e.getResponseBodyAsString();
            log.warn("MF token エンドポイント失敗: status={}, body={}", status.value(), SensitiveLogMasker.mask(body));
            if (status.value() == 400 || status.value() == 401) {
                throw new MfReAuthRequiredException(
                        "再認証が必要です。詳細は管理者ログを参照してください", e);
            }
            throw e;
        }
    }

    /**
     * SF-07: RFC 6749 §2.3.1 準拠の HTTP Basic 認証ヘッダを生成。
     * client_id / client_secret に予約文字 (例: {@code :}) や非 ASCII が含まれる場合に備えて、
     * base64 する前に application/x-www-form-urlencoded で URL encode する。
     */
    private static String basicAuthHeader(String clientId, String clientSecret) {
        Objects.requireNonNull(clientId, "clientId が null");
        Objects.requireNonNull(clientSecret, "clientSecret が null");
        String encId = URLEncoder.encode(clientId, StandardCharsets.UTF_8);
        String encSecret = URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
        String raw = encId + ":" + encSecret;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** SF-10: null in は fail-fast。これまで暗黙に空文字に変換していたバグの再発防止。 */
    private static String urlEncode(String s) {
        Objects.requireNonNull(s, "url-encoded 引数が null");
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /**
     * SF-08: MF API 呼び出しの共通リトライラッパ。
     * <ul>
     *   <li>429 (Too Many Requests) と 5xx を retryable とみなす</li>
     *   <li>1s → 2s → 4s の指数バックオフ (最大 3 回)</li>
     *   <li>{@link MfReAuthRequiredException}/{@link MfScopeInsufficientException} は即時 throw</li>
     * </ul>
     */
    private <T> T executeWithRetry(Supplier<T> op, String operation) {
        int attempt = 0;
        while (true) {
            try {
                return op.get();
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && attempt < RETRY_BACKOFFS_MS.length) {
                    long wait = RETRY_BACKOFFS_MS[attempt];
                    log.warn("[mf-api] {} 429, {}ms sleep して retry ({}回目)", operation, wait, attempt + 1);
                    sleepQuietly(wait);
                    attempt++;
                    continue;
                }
                throw e;
            } catch (HttpServerErrorException e) {
                if (attempt < RETRY_BACKOFFS_MS.length) {
                    long wait = RETRY_BACKOFFS_MS[attempt];
                    log.warn("[mf-api] {} {} server error, {}ms sleep して retry ({}回目)",
                            operation, e.getStatusCode().value(), wait, attempt + 1);
                    sleepQuietly(wait);
                    attempt++;
                    continue;
                }
                throw e;
            }
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2701ms:
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

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfScopeConstants.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfTenantMismatchException.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/api/finance/FinanceExceptionHandler.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfTenantResponse.java' in C:\project\odamitsu-data-hub
 succeeded in 1891ms:
package jp.co.oda32.domain.service.finance.mf;

/**
 * MF tenant binding 不一致 (DESIGN-DECISION P1-01 / Cluster F DD-F-04)。
 * <p>
 * 認可済 client に保存された {@code mf_tenant_id} と、callback / refresh 後に
 * 取得した tenant id が一致しない場合に throw する。
 * 別会社 MF を誤って認可した／別企業のクライアント情報で再認可した、などを示す。
 * <p>
 * Controller では 409 (CONFLICT) で返却する想定。{@link MfReAuthRequiredException}
 * と区別したいため別例外型にする (再認可するだけでは復旧せず、tenant 解除手順が必要)。
 *
 * @since 2026-05-04 (P1-01)
 */
public class MfTenantMismatchException extends RuntimeException {

    /** 既存 client に保存されている tenant id (バインド済み)。 */
    private final String boundTenantId;

    /** 今回 MF API から取得した tenant id (新規/不一致)。 */
    private final String observedTenantId;

    public MfTenantMismatchException(String boundTenantId, String observedTenantId, String message) {
        super(message);
        this.boundTenantId = boundTenantId;
        this.observedTenantId = observedTenantId;
    }

    public String getBoundTenantId() {
        return boundTenantId;
    }

    public String getObservedTenantId() {
        return observedTenantId;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1977ms:
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

 succeeded in 2923ms:
package jp.co.oda32.domain.service.finance.mf;

import java.util.List;
import java.util.Set;

/**
 * MF 連携で必須となる OAuth scope の typed list (T6)。
 * <p>
 * フロント {@code frontend/types/mf-integration.ts} の {@code MF_REQUIRED_SCOPES}
 * と同期させること。新たに必須 scope を追加する場合、両方を同時に更新し、
 * Javadoc / コメントの説明も合わせて修正する。
 *
 * <p>現状の必須 scope (Phase 1 仕訳突合 + tenant binding):
 * <ul>
 *   <li>{@code mfc/accounting/journal.read} - 仕訳取得 (突合用)</li>
 *   <li>{@code mfc/accounting/accounts.read} - 勘定科目取得</li>
 *   <li>{@code mfc/accounting/offices.read} - 事業所取得</li>
 *   <li>{@code mfc/accounting/taxes.read} - 税区分取得</li>
 *   <li>{@code mfc/accounting/report.read} - 試算表取得</li>
 *   <li>{@code mfc/admin/tenant.read} - tenant binding (P1-01)</li>
 * </ul>
 *
 * <p>scope 変更検知ロジック ({@link #analyze(String)}):
 * admin が {@code m_mf_oauth_client.scope} を編集して必須 scope を消した場合、
 * 関連 API が 403 で失敗するまで気付かない。本クラスを使って banner で予兆検知する。
 *
 * @since 2026-05-04 (T6 Scope 変更検知 + 警告 UI)
 */
public final class MfScopeConstants {

    public static final List<String> REQUIRED_SCOPES = List.of(
            "mfc/accounting/journal.read",
            "mfc/accounting/accounts.read",
            "mfc/accounting/offices.read",
            "mfc/accounting/taxes.read",
            "mfc/accounting/report.read",
            "mfc/admin/tenant.read"
    );

    private MfScopeConstants() {}

    /**
     * 与えられた scope 文字列 (DB 上の {@code scope} カラム値、空白区切り) を解析し、
     * 必須 scope に対する不足 / 追加を返す。
     * <p>
     * {@code missing} に要素があれば該当 API は 403 になるため再認可必須 (banner 赤表示)。
     * {@code extra} は警告レベル (動作には影響しない管理上の指標)。
     *
     * @param actualScopes DB 値 (例: {@code "mfc/accounting/journal.read mfc/accounting/accounts.read"})。
     *                     null / blank は scope 未設定として扱う (= 全 missing)。
     * @return ScopeAnalysis (missing / extra のリスト、いずれも immutable)
     */
    public static ScopeAnalysis analyze(String actualScopes) {
        Set<String> actual = (actualScopes == null || actualScopes.isBlank())
                ? Set.of()
                : Set.of(actualScopes.trim().split("\\s+"));
        List<String> missing = REQUIRED_SCOPES.stream()
                .filter(s -> !actual.contains(s))
                .toList();
        List<String> extra = actual.stream()
                .filter(s -> !REQUIRED_SCOPES.contains(s))
                .sorted()
                .toList();
        return new ScopeAnalysis(missing, extra);
    }

    /**
     * scope 解析結果。
     *
     * @param missing 必須だが現 scope に含まれていないもの (要素あり = 警告)
     * @param extra   現 scope に含まれているが必須ではないもの (要素あり = 情報)
     */
    public record ScopeAnalysis(List<String> missing, List<String> extra) {}
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3861ms:
package jp.co.oda32.api.finance;

import jakarta.validation.Valid;
import jp.co.oda32.domain.model.finance.MMfEnumTranslation;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.service.finance.mf.MfAccountSyncService;
import jp.co.oda32.domain.service.finance.mf.MfBalanceReconcileService;
import jp.co.oda32.domain.service.finance.mf.MfEnumTranslationService;
import jp.co.oda32.domain.service.finance.mf.MfJournalReconcileService;
import jp.co.oda32.domain.service.finance.mf.MfOauthService;
import jp.co.oda32.domain.service.finance.mf.MfReAuthRequiredException;
import jp.co.oda32.domain.service.finance.mf.MfTenantMismatchException;
import jp.co.oda32.domain.service.finance.mf.MfTokenStatus;
import jp.co.oda32.domain.service.util.LoginUserUtil;
import jp.co.oda32.dto.finance.MfEnumTranslationRequest;
import jp.co.oda32.dto.finance.MfEnumTranslationResponse;
import jp.co.oda32.dto.finance.MfOauthClientRequest;
import jp.co.oda32.dto.finance.MfOauthClientResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * マネーフォワードクラウド会計 連携（Phase 1: OAuth 基盤 + 仕訳突合）。
 * <p>
 * 設計書: claudedocs/design-mf-integration-status.md
 *
 * <p>SF-12: ログインユーザ取得は {@link LoginUserUtil#getLoginUserInfo()} に統一。
 * <p>SF-13: 診断用 endpoint は {@link MfIntegrationDebugController} に分離。
 * <p>SF-15: 401/403/422 のレスポンス組み立ては {@link FinanceExceptionHandler} に集約。
 *
 * @since 2026/04/20
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/finance/mf-integration")
@PreAuthorize("@loginUserSecurityBean.isAdmin()")
@RequiredArgsConstructor
public class MfIntegrationController {

    private final MfOauthService mfOauthService;
    private final MfEnumTranslationService enumTranslationService;
    private final MfAccountSyncService accountSyncService;
    private final MfJournalReconcileService reconcileService;
    private final MfBalanceReconcileService balanceReconcileService;

    // ---- クライアント設定 (admin 登録用) ----

    @GetMapping("/oauth/client")
    public ResponseEntity<MfOauthClientResponse> getClient() {
        return mfOauthService.findActiveClient()
                .map(MfOauthClientResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(MfOauthClientResponse.builder().build()));
    }

    @PutMapping("/oauth/client")
    public ResponseEntity<MfOauthClientResponse> upsertClient(
            @Valid @RequestBody MfOauthClientRequest request) throws Exception {
        MMfOauthClient entity = MMfOauthClient.builder()
                .clientId(request.getClientId())
                .redirectUri(request.getRedirectUri())
                .scope(request.getScope())
                .authorizeUrl(request.getAuthorizeUrl())
                .tokenUrl(request.getTokenUrl())
                .apiBaseUrl(request.getApiBaseUrl())
                .build();
        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
        MMfOauthClient saved = mfOauthService.upsertClient(entity, request.getClientSecret(), userNo);
        return ResponseEntity.ok(MfOauthClientResponse.from(saved));
    }

    // ---- OAuth フロー ----

    /** 接続ステータスを返す（画面トップの表示用）。 */
    @GetMapping("/oauth/status")
    public ResponseEntity<MfTokenStatus> status() {
        return ResponseEntity.ok(mfOauthService.getStatus());
    }

    /**
     * 認可 URL を組み立てて返す。state + PKCE code_verifier は DB ストア (B-3/B-4) で TTL 管理。
     * フロントは受け取った URL を新タブで開く。
     */
    @GetMapping("/oauth/authorize-url")
    public ResponseEntity<?> authorizeUrl() throws Exception {
        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
        MfOauthService.AuthorizeUrl issued = mfOauthService.buildAuthorizeUrl(userNo);
        return ResponseEntity.ok(Map.of("url", issued.url(), "state", issued.state()));
    }

    /**
     * MF からのリダイレクト後、フロントの callback ページが code/state を POST してくる。
     * state + code_verifier (PKCE) が DB ストアで検証できれば token 交換を行う。
     *
     * <p>SF-04: token endpoint エラー時のメッセージは汎用化 (詳細は server log のみ)。
     * このため {@link MfReAuthRequiredException} だけは {@link FinanceExceptionHandler} に
     * 委譲せずローカルで処理する (専用文言が必要)。
     */
    @PostMapping("/oauth/callback")
    public ResponseEntity<?> callback(@RequestBody Map<String, String> body) throws Exception {
        String code = body.get("code");
        String state = body.get("state");
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "code/state が必要です"));
        }
        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
        try {
            mfOauthService.handleCallback(code, state, userNo);
            return ResponseEntity.ok(mfOauthService.getStatus());
        } catch (IllegalArgumentException e) {
            log.warn("MF OAuth callback: state 検証失敗 userNo={} message={}", userNo, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "state が不正または有効期限切れです。再度認可をやり直してください。"));
        } catch (MfReAuthRequiredException e) {
            // SF-04: ユーザー向けメッセージは汎用化、詳細 (token endpoint body) はサーバーログのみ
            log.warn("MF OAuth callback: 再認証が必要 userNo={}", userNo, e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "再認証が必要です。詳細は管理者ログを参照してください"));
        } catch (MfTenantMismatchException e) {
            // P1-01: 別会社 MF を認可した場合、運用者に明示的なメッセージを返す。
            log.error("MF OAuth callback: tenant binding 不一致 userNo={} bound={} observed={}",
                    userNo, e.getBoundTenantId(), e.getObservedTenantId());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", e.getMessage(), "code", "MF_TENANT_MISMATCH"));
        }
    }

    /** 接続を切断（DB トークンを論理削除）。 */
    @PostMapping("/oauth/revoke")
    public ResponseEntity<MfTokenStatus> revoke() throws Exception {
        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
        mfOauthService.revoke(userNo);
        return ResponseEntity.ok(mfOauthService.getStatus());
    }

    // ---- enum 翻訳辞書 ----

    @GetMapping("/enum-translations")
    public ResponseEntity<List<MfEnumTranslationResponse>> listTranslations() {
        List<MfEnumTranslationResponse> list = enumTranslationService.findAll().stream()
                .map(MfEnumTranslationResponse::from).toList();
        return ResponseEntity.ok(list);
    }

    /** 渡されたリストで翻訳辞書を洗い替え。 */
    @PutMapping("/enum-translations")
    public ResponseEntity<List<MfEnumTranslationResponse>> replaceTranslations(
            @Valid @RequestBody List<MfEnumTranslationRequest> requests) throws Exception {
        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
        List<MMfEnumTranslation> entities = requests.stream()
                .map(r -> MMfEnumTranslation.builder()
                        .enumKind(r.getEnumKind())
                        .englishCode(r.getEnglishCode())
                        .japaneseName(r.getJapaneseName())
                        .build())
                .toList();
        enumTranslationService.upsertAll(entities, userNo);
        return listTranslations();
    }

    /** MF API /accounts と既存 mf_account_master を突合して、英→日マッピングを自動学習。 */
    @PostMapping("/enum-translations/auto-seed")
    public ResponseEntity<?> autoSeedTranslations() throws Exception {
        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
        MfEnumTranslationService.AutoSeedResult result = enumTranslationService.autoSeed(userNo);
        return ResponseEntity.ok(Map.of(
                "added", result.added(),
                "unresolved", result.unresolved()
        ));
    }

    // ---- 仕訳突合 ----

    /**
     * 指定取引月について、MF /journals と自社 CSV 出力元データを突合する。
     * transactionMonth は yyyy-MM-dd（通常は締め日）。
     */
    @GetMapping("/reconcile")
    public ResponseEntity<?> reconcile(
            @RequestParam("transactionMonth")
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate transactionMonth) {
        MfJournalReconcileService.ReconcileReport report = reconcileService.reconcile(transactionMonth);
        return ResponseEntity.ok(report);
    }

    /**
     * 指定月末時点の MF 試算表 buying 残高と自社 累積残の突合 (Phase B 最小版)。
     * 設計書: claudedocs/design-supplier-partner-ledger-balance.md §5
     */
    @GetMapping("/balance-reconcile")
    public ResponseEntity<?> balanceReconcile(
            @RequestParam("period")
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate period) {
        MfBalanceReconcileService.BalanceReconcileReport report =
                balanceReconcileService.reconcile(period);
        return ResponseEntity.ok(report);
    }

    // ---- 勘定科目同期 (mf_account_master 洗い替え) ----

    @GetMapping("/account-sync/preview")
    public ResponseEntity<?> previewAccountSync() {
        MfAccountSyncService.SyncResult result = accountSyncService.preview();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/account-sync/apply")
    public ResponseEntity<?> applyAccountSync() throws Exception {
        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
        MfAccountSyncService.SyncResult result = accountSyncService.apply(userNo);
        return ResponseEntity.ok(result);
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 4807ms:
package jp.co.oda32.domain.service.finance.mf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * MF tenant API ({@code GET /v2/tenant}) のレスポンス最小フィールド。
 * <p>
 * P1-01 / Cluster F DD-F-04 で導入: 別会社 MF 誤接続検知のために
 * tenant id / 名 を保持する。MF API は他にもフィールドを返すが、
 * binding 用途には id / name のみ参照する ({@link JsonIgnoreProperties} で無視)。
 *
 * @since 2026-05-04 (P1-01)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MfTenantResponse(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name
) {}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content frontend/types/mf-integration.ts' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content frontend/components/common/MfReAuthBanner.tsx' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content frontend/components/common/MfScopeBanner.tsx' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content frontend/components/layout/AppLayout.tsx' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content frontend/components/pages/finance/mf-integration.tsx' in C:\project\odamitsu-data-hub
 succeeded in 1857ms:
'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { AlertCircle, AlertTriangle } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import type { MfTokenStatus } from '@/types/mf-integration'

/**
 * MF refresh_token 540 日寿命の予兆 banner (P1-04 案 α)。
 * <p>
 * グローバル top header (AppLayout) に常設し、`daysUntilReauth` に応じて
 * 4 段階の severity (黄/橙/赤/赤強調) で再認可を促す。? 0 は失効済 = エラー表示。
 * <p>
 * - `staleTime` 5 分 + `refetchInterval` 1 時間で API 負荷を最小化
 * - 未接続 (`connected: false`) は banner 非表示 (mf-integration 画面で別 UI 担当)
 * - `daysUntilReauth > 60` は非表示 (まだ早すぎ)
 */

const THRESHOLD_60 = 60
const THRESHOLD_30 = 30
const THRESHOLD_14 = 14
const THRESHOLD_7 = 7

export function MfReAuthBanner() {
  const { isAuthenticated } = useAuth()

  const { data, isError } = useQuery<MfTokenStatus>({
    queryKey: ['mf-oauth-status'],
    queryFn: () => api.get<MfTokenStatus>('/finance/mf-integration/oauth/status'),
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000, // 5 分間 cache
    refetchInterval: 60 * 60 * 1000, // 1 時間ごとに refetch
    retry: false, // 401/403 等で再試行しない
  })

  if (!isAuthenticated) return null
  if (isError || !data) return null
  if (!data.connected) return null
  if (data.daysUntilReauth == null || data.daysUntilReauth > THRESHOLD_60) return null

  const days = data.daysUntilReauth

  // severity 判定
  let variant: 'default' | 'destructive' = 'default'
  let className = ''
  let title = ''
  let message = ''

  if (days <= 0) {
    variant = 'destructive'
    title = 'MF 連携停止中'
    message = 'MF refresh_token が失効しました。再認可してください。'
  } else if (days <= THRESHOLD_7) {
    variant = 'destructive'
    className = 'animate-pulse'
    title = `緊急: あと ${days} 日で MF 連携停止`
    message = '直ちに再認可してください。'
  } else if (days <= THRESHOLD_14) {
    variant = 'destructive'
    title = `MF 再認可必須 (あと ${days} 日)`
    message = '2 週間以内に再認可しないと連携が停止します。'
  } else if (days <= THRESHOLD_30) {
    variant = 'default'
    className = 'border-amber-500 bg-amber-50 text-amber-900'
    title = `MF 再認可推奨 (あと ${days} 日)`
    message = '今月中に再認可してください。'
  } else {
    // 31-60
    variant = 'default'
    className = 'border-yellow-500 bg-yellow-50 text-yellow-900'
    title = `MF 再認可予定 (あと ${days} 日)`
    message = '計画的に再認可作業を準備してください。'
  }

  const Icon = variant === 'destructive' ? AlertCircle : AlertTriangle

  return (
    <Alert variant={variant} className={className}>
      <Icon className="h-4 w-4" />
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription className="flex w-full items-center justify-between gap-3">
        <span>{message}</span>
        <Button asChild variant="outline" size="sm">
          <Link href="/finance/mf-integration">再認可画面へ</Link>
        </Button>
      </AlertDescription>
    </Alert>
  )
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1981ms:
export interface MfOauthClientResponse {
  id: number | null
  clientId: string | null
  clientSecretConfigured: boolean
  redirectUri: string | null
  scope: string | null
  authorizeUrl: string | null
  tokenUrl: string | null
  apiBaseUrl: string | null
}

export interface MfOauthClientRequest {
  clientId: string
  /** 新規登録時は必須、更新時は空で既存値を維持。 */
  clientSecret?: string
  redirectUri: string
  scope: string
  authorizeUrl: string
  tokenUrl: string
  apiBaseUrl: string
}

export interface MfTokenStatus {
  configured: boolean
  connected: boolean
  expiresAt: string | null
  scope: string | null
  lastRefreshedAt: string | null
  reAuthRequired: boolean
  /** バインド済 MF tenant id (P1-01)。NULL なら未バインド (旧データ互換、次回 callback で確定)。 */
  mfTenantId: string | null
  /** バインド済 MF tenant 名 (P1-01、UI 表示用)。 */
  mfTenantName: string | null
  /** tenant binding 確定時刻 ISO-8601 (P1-01)。 */
  tenantBoundAt: string | null
  /** 現 active token の add_date_time = refresh_token 発行日 ISO-8601 (P1-04 案 α)。 */
  refreshTokenIssuedAt: string | null
  /** 540 日 - 経過日数。? 0 で失効、? 60 で予兆 banner 表示 (P1-04 案 α)。未接続時 null。 */
  daysUntilReauth: number | null
  /**
   * T6 (2026-05-04): 必須だが現 scope 設定に含まれていない scope 一覧。
   * 要素ありの場合、関連 API が 403 で失敗する可能性あり。`MfScopeBanner` で警告表示。
   * Backend `MfScopeConstants.REQUIRED_SCOPES` と同期 (=`MF_REQUIRED_SCOPES` 定数)。
   */
  missingScopes: string[]
  /**
   * T6: 現 scope に含まれているが必須ではない scope 一覧。動作影響なし、管理上の指標。
   */
  extraScopes: string[]
  /** T6: missingScopes.isEmpty() の short-hand。false なら `MfScopeBanner` を表示。 */
  scopeOk: boolean
}

export type JournalKind = 'PURCHASE' | 'SALES' | 'PAYMENT'

export const JOURNAL_KIND_LABELS: Record<JournalKind, string> = {
  PURCHASE: '仕入仕訳',
  SALES: '売上仕訳',
  PAYMENT: '買掛支払',
}

export type MfEnumKind = 'FINANCIAL_STATEMENT' | 'CATEGORY'

export const MF_ENUM_KIND_LABELS: Record<MfEnumKind, string> = {
  FINANCIAL_STATEMENT: '財務諸表区分',
  CATEGORY: '大分類',
}

export interface MfEnumTranslation {
  id: number | null
  enumKind: MfEnumKind
  englishCode: string
  japaneseName: string
}

export interface MfAccountSyncSample {
  accountName: string
  subAccountName: string | null
  category: string | null
  taxClassification: string | null
}

export interface MfAccountSyncFieldDiff {
  accountName: string
  subAccountName: string | null
  changes: string
}

export interface MfAccountSyncResult {
  applied: boolean
  insertCount: number
  updateCount: number
  deleteCount: number
  insertSamples: MfAccountSyncSample[]
  updateSamples: MfAccountSyncFieldDiff[]
  deleteSamples: MfAccountSyncSample[]
  unknownEnums: string[]
}

export interface MfLocalRowSummary {
  source: string
  shopNo: number | null
  partyNo: number | null
  partyCode: string | null
  partyName: string | null
  taxRate: number | null
  amount: number
  note: string | null
  unmatched: boolean
}

export interface MfBranchSummary {
  journalId: string
  journalNumber: number | null
  transactionDate: string | null
  debitAccount: string | null
  debitSubAccount: string | null
  creditAccount: string | null
  creditSubAccount: string | null
  tradePartnerName: string | null
  taxName: string | null
  amount: number | null
  enteredBy: string | null
  unmatched: boolean
}

export interface MfReconcileRow {
  kind: JournalKind
  kindLabel: string
  localCount: number
  localAmount: number
  mfCount: number
  mfAmount: number
  countDiff: number
  amountDiff: number
  matched: boolean
  localItems: MfLocalRowSummary[]
  mfItems: MfBranchSummary[]
}

export interface MfUnclassifiedBreakdown {
  debitAccount: string | null
  creditAccount: string | null
  count: number
  totalAmount: number
}

export interface MfReconcileReport {
  transactionMonth: string
  fetchedAt: string
  rows: MfReconcileRow[]
  mfUnknownBranchCount: number
  unclassified: MfUnclassifiedBreakdown[]
}

// ---- Phase B: 残高突合 (design-supplier-partner-ledger-balance.md §5) ----

export interface MfPayableBalance {
  /** MF 試算表 買掛金 closing_balance。 */
  mfClosing: number
  /** 自社 summary 全 row の closing 合計 (mf_export_enabled 問わず)。 */
  selfClosingAll: number
  /** MF 突合対象 row のみ (mfExportEnabled=true OR verifiedManually=true OR isPaymentOnly=true) の closing 合計。 */
  selfClosingForMf: number
  /** mfClosing - selfClosingAll。 */
  diffAll: number
  /** mfClosing - selfClosingForMf (メイン突合指標、期首残込み)。 */
  diffForMf: number
  selfRowCount: number
  selfMfTargetRowCount: number
  /** MF 試算表に「買掛金」account が見つかったか。 */
  mfAccountFound: boolean
  /** MF 期首残 = 自社 backfill 起点の前月末時点の MF 買掛金 closing (Phase B'' 追加)。 */
  mfOpeningBalance: number
  /** mfOpeningBalance を取得した MF 基準日 (yyyy-MM-dd)。 */
  openingReferenceDate: string
  /**
   * 期首残調整後の差分 = mfClosing - mfOpeningBalance - selfClosingForMf。
   * 2025-06-20 以降の累積のみで比較した純粋な乖離。
   * 残差は 2025-06?2025-12 の verified_amount 欠落 (過去振込明細未取込) が主因。
   */
  diffForMfAdjusted: number
}

export interface MfBalanceReconcileReport {
  period: string // yyyy-MM-dd
  fetchedAt: string
  mfEndDate: string
  payable: MfPayableBalance
}

// Phase 1 (仕訳突合) で必要な scope:
// - mfc/accounting/journal.read: 仕訳取得
// - mfc/accounting/accounts.read: 勘定科目マスタ（将来的な mf_account_master CSV 取込自動化用）
// - mfc/accounting/offices.read: 事業者情報
// - mfc/accounting/taxes.read: 税区分マスタ
// Phase 2 (試算表/残高突合, design-supplier-partner-ledger-balance.md §3) で追加:
// - mfc/accounting/report.read: 試算表 (trial_balance_bs) 取得
// スコープは space 区切り。scope 追加時は画面で「クライアント設定」更新保存 → 「再認証」が必要。
//
// (F-6) redirectUri のデフォルトは利用環境によって異なる:
//  - dev: `${window.location.origin}/finance/mf-integration/callback`
//  - prod: 同上 (同一オリジン)
// ハードコードはやめて、実行時にブラウザから組み立てる。SSR では window が無いため
// フォールバック文字列を返し、クライアント側 useEffect 等で上書きする想定。
export const MF_CALLBACK_PATH = '/finance/mf-integration/callback'

export function defaultMfRedirectUri(): string {
  if (typeof window !== 'undefined' && window.location?.origin) {
    return window.location.origin + MF_CALLBACK_PATH
  }
  // SSR / build 時のフォールバック (実際には runtime で上書きされる想定)
  return MF_CALLBACK_PATH
}

// SF-16: scope は配列で定義し、検証 (含有チェック) や追加修正をしやすくする。
// space 区切り文字列の手書きはタイポが入りやすく、追加忘れが原因で API エラーが起きるため。
//
// P1-01 (DD-F-04): mfc/admin/tenant.read は MF tenant API (/v2/tenant) 呼び出しに必須。
// 別会社 MF への誤接続検知のため、scope 追加 → 「クライアント設定」更新保存 → 「再認証」が必要。
//
// T6 (2026-05-04): backend `MfScopeConstants.REQUIRED_SCOPES` と必ず同期させること。
// 必須 scope を増減する場合は両方を同時更新し、関連 Javadoc/コメントも合わせて修正する。
// backend `MfOauthService.getStatus()` がこの一覧と DB scope を比較して `MfTokenStatus.missingScopes`
// を返し、`MfScopeBanner` で UI 警告を出す仕組みになっている。
export const MF_REQUIRED_SCOPES = [
  'mfc/accounting/journal.read',
  'mfc/accounting/accounts.read',
  'mfc/accounting/offices.read',
  'mfc/accounting/taxes.read',
  'mfc/accounting/report.read',
  'mfc/admin/tenant.read',
] as const

export const MF_DEFAULT_SCOPE = MF_REQUIRED_SCOPES.join(' ')

export const MF_DEFAULT_CONFIG: Omit<MfOauthClientRequest, 'clientId' | 'clientSecret'> = {
  redirectUri: defaultMfRedirectUri(),
  scope: MF_DEFAULT_SCOPE,
  authorizeUrl: 'https://api.biz.moneyforward.com/authorize',
  tokenUrl: 'https://api.biz.moneyforward.com/token',
  apiBaseUrl: 'https://api-accounting.moneyforward.com',
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1983ms:
'use client'

import type { ReactNode } from 'react'
import { SidebarProvider } from '@/components/ui/sidebar'
import { AppSidebar } from './Sidebar'
import { Header } from './Header'
import { MfReAuthBanner } from '@/components/common/MfReAuthBanner'
import { MfScopeBanner } from '@/components/common/MfScopeBanner'

export function AppLayout({ children }: { children: ReactNode }) {
  return (
    <SidebarProvider>
      <div className="flex h-screen w-full">
        <div className="print:hidden">
          <AppSidebar />
        </div>
        <div className="flex flex-1 flex-col overflow-hidden">
          <Header />
          {/* P1-04 案 α: MF refresh_token 540 日寿命予兆 banner。
              header の直下、main scroll 領域の外に固定して全画面で常時表示する。
              非表示条件 (未接続 / N>60 / 認証前) は MfReAuthBanner 内部で判定。
              T6 (2026-05-04): MfScopeBanner も並列配置。両 banner は queryKey
              'mf-oauth-status' を共有し、TanStack Query cache で 1 fetch にまとめる。
              非表示条件 (未接続 / scopeOk=true / 認証前) は内部で判定。 */}
          <div className="px-4 pt-2 print:hidden empty:hidden">
            <MfReAuthBanner />
            <MfScopeBanner />
          </div>
          <main className="flex-1 overflow-auto p-6 print:p-0 print:overflow-visible">{children}</main>
        </div>
      </div>
    </SidebarProvider>
  )
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2050ms:
'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { AlertCircle } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import type { MfTokenStatus } from '@/types/mf-integration'

/**
 * MF OAuth scope 不足検知 banner (T6, 2026-05-04)。
 * <p>
 * グローバル top header (AppLayout) に常設し、admin が画面上で
 * 必須 scope を削除した場合に警告を出す。{@link MfReAuthBanner}
 * (refresh_token 寿命) と独立した責務で、banner ロジックを分離する。
 * <ul>
 *   <li>severity 固定 = destructive (赤): 関連 API が 403 で失敗する可能性あり</li>
 *   <li>未接続時は表示しない (mf-integration 画面で別途対応)</li>
 *   <li>scopeOk=true (= missingScopes 空) なら表示しない</li>
 *   <li>queryKey は {@link MfReAuthBanner} と共有 ('mf-oauth-status')。
 *       TanStack Query の cache 共有で 1 度の fetch で両 banner が判定する。</li>
 * </ul>
 */
export function MfScopeBanner() {
  const { isAuthenticated } = useAuth()

  const { data, isError } = useQuery<MfTokenStatus>({
    queryKey: ['mf-oauth-status'],
    queryFn: () => api.get<MfTokenStatus>('/finance/mf-integration/oauth/status'),
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000, // 5 分間 cache
    refetchInterval: 60 * 60 * 1000, // 1 時間ごとに refetch
    retry: false,
  })

  if (!isAuthenticated) return null
  if (isError || !data) return null
  // 未接続なら mf-integration 画面で別 UI を出すため banner 非表示
  if (!data.connected) return null
  // 必須 scope が揃っていれば表示しない
  if (data.scopeOk) return null

  return (
    <Alert variant="destructive">
      <AlertCircle className="h-4 w-4" />
      <AlertTitle>MF 連携 scope 不足</AlertTitle>
      <AlertDescription className="flex w-full items-start justify-between gap-3">
        <div className="flex-1">
          <p>以下の必須 scope が現設定に含まれていません。再認可するまで関連 API が 403 で失敗する可能性があります。</p>
          <ul className="ml-4 mt-1 list-disc text-xs">
            {data.missingScopes.map((s) => (
              <li key={s}>
                <code>{s}</code>
              </li>
            ))}
          </ul>
          <p className="mt-2 text-xs">
            対応: 「クライアント設定」で scope を修正し、「再認証」を実行してください。
          </p>
        </div>
        <Button asChild variant="outline" size="sm">
          <Link href="/finance/mf-integration">再認可画面へ</Link>
        </Button>
      </AlertDescription>
    </Alert>
  )
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2080ms:
'use client'

import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Link2, Link2Off, RefreshCw, AlertCircle, CheckCircle2 } from 'lucide-react'
import { toast } from 'sonner'
import type {
  MfOauthClientRequest,
  MfOauthClientResponse,
  MfTokenStatus,
} from '@/types/mf-integration'
import { MF_DEFAULT_CONFIG } from '@/types/mf-integration'
import { MfEnumTranslationTab } from './MfEnumTranslationTab'
import { MfAccountSyncTab } from './MfAccountSyncTab'
import { MfReconcileTab } from './MfReconcileTab'
import { MfBalanceReconcileTab } from './MfBalanceReconcileTab'

export function MfIntegrationPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const queryClient = useQueryClient()

  const [form, setForm] = useState<MfOauthClientRequest>({
    clientId: '',
    clientSecret: '',
    ...MF_DEFAULT_CONFIG,
  })

  const clientQuery = useQuery({
    queryKey: ['mf-oauth-client'],
    queryFn: () => api.get<MfOauthClientResponse>('/finance/mf-integration/oauth/client'),
    enabled: isAdmin,
  })

  const statusQuery = useQuery({
    queryKey: ['mf-oauth-status'],
    queryFn: () => api.get<MfTokenStatus>('/finance/mf-integration/oauth/status'),
    enabled: isAdmin,
    refetchInterval: 60_000,
  })

  // 初回ロード時: 既存設定があれば form にマージ (F-4 render-time setState 回避)
  // SF-17: 再レンダリングのたびに発火しないよう、依存配列を「実体差し替え」を示す key field のみに絞る。
  useEffect(() => {
    const c = clientQuery.data
    if (!c || !c.clientId) return
    setForm((prev) => ({
      ...prev,
      clientId: c.clientId ?? prev.clientId,
      clientSecret: '', // secret は返却されない
      redirectUri: c.redirectUri ?? MF_DEFAULT_CONFIG.redirectUri,
      scope: c.scope ?? MF_DEFAULT_CONFIG.scope,
      authorizeUrl: c.authorizeUrl ?? MF_DEFAULT_CONFIG.authorizeUrl,
      tokenUrl: c.tokenUrl ?? MF_DEFAULT_CONFIG.tokenUrl,
      apiBaseUrl: c.apiBaseUrl ?? MF_DEFAULT_CONFIG.apiBaseUrl,
    }))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clientQuery.data?.id, clientQuery.data?.clientId])

  // SF-05: callback タブからの BroadcastChannel メッセージを受け取り、status を即時 invalidate して画面反映する。
  // window.open を noopener,noreferrer で開いているため、window.opener / postMessage は使えない。
  useEffect(() => {
    let ch: BroadcastChannel | null = null
    try {
      ch = new BroadcastChannel('mf-oauth')
      ch.onmessage = (event) => {
        const data = event.data as { type?: string; source?: string; message?: string } | null
        if (!data || data.source !== 'odamitsu-data-hub') return
        if (data.type === 'connected') {
          toast.success('MF 連携が完了しました')
          queryClient.invalidateQueries({ queryKey: ['mf-oauth-status'] })
        } else if (data.type === 'failed') {
          toast.error(`MF 連携に失敗: ${data.message ?? 'unknown'}`)
        }
      }
    } catch { /* BroadcastChannel 非対応環境では無視 */ }
    return () => {
      try { ch?.close() } catch { /* ignore */ }
    }
  }, [queryClient])

  const saveMutation = useMutation({
    mutationFn: (body: MfOauthClientRequest) =>
      api.put<MfOauthClientResponse>('/finance/mf-integration/oauth/client', body),
    onSuccess: () => {
      toast.success('OAuth クライアント設定を保存しました')
      queryClient.invalidateQueries({ queryKey: ['mf-oauth-client'] })
      queryClient.invalidateQueries({ queryKey: ['mf-oauth-status'] })
      setForm((prev) => ({ ...prev, clientSecret: '' })) // 保存後は UI 側の secret をクリア
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const connectMutation = useMutation({
    mutationFn: () => api.get<{ url: string }>('/finance/mf-integration/oauth/authorize-url'),
    onSuccess: (res) => {
      // SF-05: 新タブで MF 認可ページを開く。
      // BroadcastChannel 経由で通知するため window.opener は不要 → noopener,noreferrer でセキュリティ強化。
      window.open(res.url, '_blank', 'noopener,noreferrer')
      toast.info('MF 認可ページを新しいタブで開きました。完了後、このタブに自動反映されます。')
    },
    onError: (e: Error) => {
      if (e instanceof ApiError && e.status === 422) {
        toast.error('クライアント設定が未登録です。先に Client ID/Secret を保存してください。')
      } else {
        toast.error(e.message)
      }
    },
  })

  const revokeMutation = useMutation({
    mutationFn: () => api.post<MfTokenStatus>('/finance/mf-integration/oauth/revoke'),
    onSuccess: () => {
      toast.success('MF 連携を切断しました')
      queryClient.invalidateQueries({ queryKey: ['mf-oauth-status'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  if (!isAdmin) {
    return (
      <div className="rounded border border-amber-300 bg-amber-50 p-4 text-sm text-amber-800">
        <AlertCircle className="mr-1 inline h-4 w-4" />
        このページは管理者のみ利用可能です。
      </div>
    )
  }

  if (clientQuery.isLoading || statusQuery.isLoading) return <LoadingSpinner />

  const status = statusQuery.data
  const client = clientQuery.data

  const handleSave = () => {
    if (!form.clientId.trim()) {
      toast.error('Client ID は必須です')
      return
    }
    if (!client?.clientSecretConfigured && !form.clientSecret?.trim()) {
      toast.error('新規登録時は Client Secret が必須です')
      return
    }
    // secret が空なら送信しない（既存値維持）
    const body: MfOauthClientRequest = { ...form }
    if (!body.clientSecret?.trim()) {
      delete (body as Partial<MfOauthClientRequest>).clientSecret
    }
    saveMutation.mutate(body)
  }

  const formatTime = (iso: string | null) =>
    iso ? new Date(iso).toLocaleString('ja-JP') : '-'

  return (
    <div className="space-y-4">
      <PageHeader title="MF 連携状況" description="マネーフォワードクラウド会計 API 連携設定" />

      <Tabs defaultValue="connection">
        <TabsList>
          <TabsTrigger value="connection">接続</TabsTrigger>
          <TabsTrigger value="translations" disabled={!status?.connected}>
            enum 翻訳辞書
          </TabsTrigger>
          <TabsTrigger value="account-sync" disabled={!status?.connected}>
            勘定科目同期
          </TabsTrigger>
          <TabsTrigger value="reconcile" disabled={!status?.connected}>
            仕訳突合
          </TabsTrigger>
          <TabsTrigger value="balance-reconcile" disabled={!status?.connected}>
            残高突合
          </TabsTrigger>
        </TabsList>

        <TabsContent value="connection" className="mt-3 space-y-4">
      {/* 接続ステータスカード */}
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-base">接続ステータス</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {status?.connected ? (
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <Badge className="bg-emerald-600 hover:bg-emerald-700">
                  <CheckCircle2 className="mr-1 h-3.5 w-3.5" />
                  接続中
                </Badge>
                <span className="text-xs text-muted-foreground">
                  有効期限: {formatTime(status.expiresAt)}
                  {status.scope && ` / scope: ${status.scope}`}
                </span>
              </div>
              {/* P1-01 (DD-F-04): バインド済 MF tenant 情報。別会社 MF 誤接続検知のため必ず表示。 */}
              {status.mfTenantId ? (
                <div className="rounded border border-emerald-200 bg-emerald-50 p-2 text-xs text-emerald-900">
                  <div>
                    連携先: <span className="font-medium">{status.mfTenantName ?? '(名称未取得)'}</span>{' '}
                    <span className="text-muted-foreground">
                      (tenant id: <span className="font-mono">{status.mfTenantId}</span>)
                    </span>
                  </div>
                  {status.tenantBoundAt && (
                    <div className="mt-0.5 text-[11px] text-emerald-800/80">
                      バインド日時: {formatTime(status.tenantBoundAt)}
                    </div>
                  )}
                  {/* P1-04 案 α: refresh_token 540 日寿命の残日数。banner と同じ source データ。 */}
                  {status.daysUntilReauth != null && (
                    <div className="mt-0.5 text-[11px] text-emerald-800/80">
                      refresh_token 残日数: {status.daysUntilReauth} 日
                      {status.refreshTokenIssuedAt && (
                        <> (発行: {formatTime(status.refreshTokenIssuedAt)} / 寿命 540 日)</>
                      )}
                    </div>
                  )}
                </div>
              ) : (
                <div className="rounded border border-amber-200 bg-amber-50 p-2 text-xs text-amber-900">
                  <AlertCircle className="mr-1 inline h-3.5 w-3.5" />
                  MF tenant が未バインドです (旧データ)。次回 token refresh 時に自動でバインドされます。
                </div>
              )}
            </div>
          ) : status?.configured ? (
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <Badge variant="secondary">未接続</Badge>
                <span className="text-xs text-muted-foreground">
                  クライアント設定済み。下の「接続」ボタンで認可を開始できます。
                </span>
              </div>
              {/* 過去にバインドされた tenant 情報 (revoke 後はクリアされる) */}
              {status.mfTenantId && (
                <div className="rounded border border-slate-200 bg-slate-50 p-2 text-xs text-slate-700">
                  前回連携先: <span className="font-medium">{status.mfTenantName ?? '(名称未取得)'}</span>{' '}
                  <span className="text-muted-foreground">
                    (tenant id: <span className="font-mono">{status.mfTenantId}</span>)
                  </span>
                </div>
              )}
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <Badge variant="outline">未設定</Badge>
              <span className="text-xs text-muted-foreground">
                下のフォームで Client ID / Client Secret を登録してください。
              </span>
            </div>
          )}
          <div className="flex gap-2">
            <Button
              size="sm"
              onClick={() => connectMutation.mutate()}
              disabled={!status?.configured || connectMutation.isPending}
            >
              <Link2 className="mr-1 h-4 w-4" />
              {status?.connected ? '再認証' : '接続'}
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => revokeMutation.mutate()}
              disabled={!status?.connected || revokeMutation.isPending}
            >
              <Link2Off className="mr-1 h-4 w-4" />
              切断
            </Button>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => queryClient.invalidateQueries({ queryKey: ['mf-oauth-status'] })}
            >
              <RefreshCw className="mr-1 h-4 w-4" />
              再読込
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* OAuth クライアント設定フォーム */}
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-base">OAuth クライアント設定</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="text-xs text-muted-foreground">
            MF アプリポータル (
            <a
              className="text-blue-600 hover:underline"
              href="https://app-portal.moneyforward.com/"
              target="_blank"
              rel="noopener noreferrer"
            >
              app-portal.moneyforward.com
            </a>
            ) で発行した Client ID / Client Secret を登録します。Secret は暗号化して保存され、画面には再表示されません。
          </p>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div>
              <Label htmlFor="clientId">Client ID</Label>
              <Input
                id="clientId"
                value={form.clientId}
                onChange={(e) => setForm({ ...form, clientId: e.target.value })}
                placeholder="MF アプリポータルで発行された Client ID"
                autoComplete="off"
              />
            </div>
            <div>
              <Label htmlFor="clientSecret">
                Client Secret
                {client?.clientSecretConfigured && (
                  <span className="ml-2 text-xs text-emerald-600">（登録済み・空のままで維持）</span>
                )}
              </Label>
              <Input
                id="clientSecret"
                type="password"
                value={form.clientSecret ?? ''}
                onChange={(e) => setForm({ ...form, clientSecret: e.target.value })}
                placeholder={
                  client?.clientSecretConfigured ? '変更する場合のみ入力' : '新規登録時は必須'
                }
                autoComplete="new-password"
              />
            </div>
            <div className="md:col-span-2">
              <Label htmlFor="redirectUri">Redirect URI</Label>
              <Input
                id="redirectUri"
                value={form.redirectUri}
                onChange={(e) => setForm({ ...form, redirectUri: e.target.value })}
              />
              <p className="mt-1 text-[11px] text-muted-foreground">
                アプリポータルに登録した redirect URI と完全に一致している必要があります。
              </p>
            </div>
            <div>
              <Label htmlFor="scope">Scope</Label>
              <Input
                id="scope"
                value={form.scope}
                onChange={(e) => setForm({ ...form, scope: e.target.value })}
              />
            </div>
            <div>
              <Label htmlFor="apiBaseUrl">API Base URL</Label>
              <Input
                id="apiBaseUrl"
                value={form.apiBaseUrl}
                onChange={(e) => setForm({ ...form, apiBaseUrl: e.target.value })}
              />
            </div>
            <div>
              <Label htmlFor="authorizeUrl">Authorize URL</Label>
              <Input
                id="authorizeUrl"
                value={form.authorizeUrl}
                onChange={(e) => setForm({ ...form, authorizeUrl: e.target.value })}
              />
            </div>
            <div>
              <Label htmlFor="tokenUrl">Token URL</Label>
              <Input
                id="tokenUrl"
                value={form.tokenUrl}
                onChange={(e) => setForm({ ...form, tokenUrl: e.target.value })}
              />
            </div>
          </div>

          <div className="flex gap-2">
            <Button onClick={handleSave} disabled={saveMutation.isPending}>
              {saveMutation.isPending ? '保存中...' : '保存'}
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* 最終更新情報 */}
      {status?.connected && (
        <Card>
          <CardContent className="pt-4 text-xs text-muted-foreground">
            トークン最終更新: {formatTime(status.lastRefreshedAt)}
          </CardContent>
        </Card>
      )}

      {/* 診断（dev のみ）: MF API の生レスポンスを取得して shape 確認 */}
      {status?.connected && <MfDiagnosticsCard />}
        </TabsContent>

        <TabsContent value="translations" className="mt-3">
          <MfEnumTranslationTab />
        </TabsContent>

        <TabsContent value="account-sync" className="mt-3">
          <MfAccountSyncTab />
        </TabsContent>

        <TabsContent value="reconcile" className="mt-3">
          <MfReconcileTab />
        </TabsContent>

        <TabsContent value="balance-reconcile" className="mt-3">
          <MfBalanceReconcileTab />
        </TabsContent>
      </Tabs>
    </div>
  )
}

function MfDiagnosticsCard() {
  const [result, setResult] = useState<{ label: string; json: unknown } | null>(null)
  const [loading, setLoading] = useState<'accounts' | 'taxes' | 'journals' | null>(null)
  // journals 用の取引月。デフォルトは前月 20 日（20 日締めの典型値）。
  const [journalDate, setJournalDate] = useState<string>(() => {
    const now = new Date()
    const prev = new Date(now.getFullYear(), now.getMonth() - 1, 20)
    return `${prev.getFullYear()}-${String(prev.getMonth() + 1).padStart(2, '0')}-${String(prev.getDate()).padStart(2, '0')}`
  })

  const fetch = async (kind: 'accounts' | 'taxes' | 'journals') => {
    setLoading(kind)
    setResult(null)
    try {
      let path: string
      if (kind === 'accounts') path = 'accounts-raw'
      else if (kind === 'taxes') path = 'taxes-raw'
      else path = `journals-raw?transactionMonth=${journalDate}`
      const json = await api.get<unknown>(`/finance/mf-integration/debug/${path}`)
      setResult({ label: kind, json })
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : (e as Error).message
      toast.error(`${kind} 取得失敗: ${msg}`)
    } finally {
      setLoading(null)
    }
  }

  const copyToClipboard = async () => {
    if (!result) return
    try {
      await navigator.clipboard.writeText(JSON.stringify(result.json, null, 2))
      toast.success('JSON をクリップボードにコピーしました')
    } catch {
      toast.error('コピー失敗')
    }
  }

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-base">診断（MF API 生レスポンス確認）</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <p className="text-xs text-muted-foreground">
          勘定科目同期機能の設計用に、MF API の生レスポンス shape を確認します。dev プロファイルのみ動作。
        </p>
        <div className="flex gap-2">
          <Button
            size="sm"
            variant="outline"
            onClick={() => fetch('accounts')}
            disabled={loading !== null}
          >
            {loading === 'accounts' ? '取得中...' : 'accounts 取得（3件）'}
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={() => fetch('taxes')}
            disabled={loading !== null}
          >
            {loading === 'taxes' ? '取得中...' : 'taxes 取得（5件）'}
          </Button>
          {result && (
            <Button size="sm" variant="ghost" onClick={copyToClipboard}>
              JSON コピー
            </Button>
          )}
        </div>
        <div className="flex items-center gap-2">
          <Label htmlFor="journal-date" className="text-xs">
            取引日:
          </Label>
          <Input
            id="journal-date"
            type="date"
            className="h-8 w-40 text-xs"
            value={journalDate}
            onChange={(e) => setJournalDate(e.target.value)}
          />
          <Button
            size="sm"
            variant="outline"
            onClick={() => fetch('journals')}
            disabled={loading !== null}
          >
            {loading === 'journals' ? '取得中...' : 'journals 取得（3件）'}
          </Button>
        </div>
        {result && (
          <pre className="max-h-96 overflow-auto rounded bg-muted p-3 text-[11px] font-mono">
            {JSON.stringify(result.json, null, 2)}
          </pre>
        )}
      </CardContent>
    </Card>
  )
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/resources/config/application.yml' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/resources/config/application-dev.yml' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/resources/config/application-prod.yml' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/test/resources/config/application-test.yml' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/scripts/gen-oauth-key.ps1' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content claudedocs/runbook-mf-oauth-keys.md' in C:\project\odamitsu-data-hub
 succeeded in 2001ms:
spring:
  main:
    allow-bean-definition-overriding: true
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: none
    properties:
      hibernate:
        jdbc:
          lob:
            non_contextual_creation: true
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
  jackson:
    serialization:
      write-dates-as-timestamps: false
    default-property-inclusion: non_null
  # Flyway: DB migration 自動適用。
  # baseline-on-migrate のデフォルトは false (B-W12)。
  #   - 新規環境: 空 DB で起動するとき V001? を全件適用（baseline-on-migrate 不要）
  #   - 既存環境 (prod, 手動で V001?V018 まで適用済): app-prod.yml 側で baseline-on-migrate=true
  #     + baseline-version=18 を明示することで、初回起動時だけ V001?V018 をスキップする。
  #   - 誤って空 DB に対して baseline-on-migrate=true を有効にすると V001?V018 が適用されず
  #     V019+ だけ走って schema が壊れるため、デフォルトでは無効にして事故を防ぐ。
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
    validate-on-migrate: true

server:
  port: 8090

# 外部API接続設定
# B-CART アクセストークンは環境変数 BCART_ACCESS_TOKEN 経由で必ず設定する。
# ソースコードや git 管理対象ファイルに直接書き込まないこと。
bcart:
  api:
    access-token: ${BCART_ACCESS_TOKEN:}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2056ms:
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    username: sa
    password:
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
  batch:
    job:
      enabled: false
    jdbc:
      initialize-schema: always
  # テストは H2 + Hibernate create-drop で完結。
  # Flyway の本番 migration は PostgreSQL 固有構文 (IS DISTINCT FROM 等) を含むため H2 では動かない → 無効化。
  flyway:
    enabled: false

cors:
  allowed-origins: http://localhost:5173

jwt:
  secret: test-secret-key-odamitsu-data-hub-must-be-at-least-256-bits-long-for-hmac
  expiration-ms: 3600000

# テスト用のダミートークン（実際の B-CART API には到達しない）。
# BCartApiConfig#init() の空文字検証だけを通すための placeholder。
bcart:
  api:
    access-token: test-dummy-bcart-token

# AES 暗号鍵 (テスト用ダミー)。
# crypto.key/salt: 汎用暗号化用 (CryptoUtil)。
# crypto.oauth-key/salt: MF OAuth 専用 (OauthCryptoUtil, P1-05 案 C.3)。
#   prod では env 必須だがテストでは固定ダミーで充足させる。
app:
  crypto:
    key: test-crypto-key-odamitsu-data-hub-2026-16chars-min
    salt: 0123456789abcdef0123456789abcdef
    oauth-key: test-oauth-key-odamitsu-data-hub-2026-16chars-min
    oauth-salt: fedcba9876543210fedcba9876543210

logging:
  level:
    jp.co.oda32: DEBUG
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2133ms:
spring:
  datasource:
    url: jdbc:postgresql://localhost:55544/oda32db
    username: oda32
    password: oda32
    driver-class-name: org.postgresql.Driver

cors:
  allowed-origins: http://localhost:5173,http://localhost:3000,http://192.168.1.55:3000

app:
  # 開発環境のみ: 500 レスポンスに例外クラス名/メッセージを含める
  expose-exception-detail: true
  # AES 暗号鍵 / salt。
  # crypto.key/salt: 社内 password 等の汎用暗号化用。dev fallback あり (MEMORY 方針)。
  # crypto.oauth-key/salt: MF OAuth 専用 (P1-05 案 C.3)。dev でも env 必須・fallback 無し。
  #   未設定なら起動時に IllegalStateException で fail-fast する。
  #   セットアップは backend/scripts/gen-oauth-key.ps1 + claudedocs/runbook-mf-oauth-keys.md 参照。
  crypto:
    key: ${APP_CRYPTO_KEY:dev-odamitsu-data-hub-crypto-key-2026}
    salt: ${APP_CRYPTO_SALT:3f6a2d8c9e1b4a7d5c0f8e2a9b6d1c3f}
    oauth-key: ${APP_CRYPTO_OAUTH_KEY:}
    oauth-salt: ${APP_CRYPTO_OAUTH_SALT:}

# JWT secret: dev は固定値フォールバック。prod は env 必須 (application-prod.yml)。
jwt:
  secret: ${JWT_SECRET:dev-secret-key-odamitsu-data-hub-2024-must-be-at-least-256-bits-long}
  expiration-ms: 86400000

# 開発環境用の B-CART アクセストークン。
# 既に git 履歴に流出している旧トークンを踏襲しているだけで、本来は失効＆ローテーション予定。
# 本番では BCART_ACCESS_TOKEN 環境変数経由で最新トークンを注入すること（application.yml 側）。
# ローカルで環境変数を設定済みの場合は、下行よりもそちらが優先される。
bcart:
  api:
    access-token: ${BCART_ACCESS_TOKEN:eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJhdWQiOiI5IiwianRpIjoiZTJkZGQyZGE0YzZkOTkwMzEwNzdiZWIwMWIwNTU1ZmNkOWU2NGZiZjk4YzI3YmQyOGJjYzUwZWNhYzM2NDdmZDM3NzBiNGQ4MjIzY2VlYjkiLCJpYXQiOjE2Nzg5NTkyOTksIm5iZiI6MTY3ODk1OTI5OSwiZXhwIjoxNzEwNTgxNjk5LCJzdWIiOiI1NjAiLCJzY29wZXMiOlsicHJvZHVjdHMtcmVhZCIsInByb2R1Y3RzLXdyaXRlIiwicHJvZHVjdF9jdXN0b21zLXJlYWQiLCJwcm9kdWN0X3NldHMtcmVhZCIsInByb2R1Y3Rfc2V0cy13cml0ZSIsInByb2R1Y3Rfc2V0X29wdGlvbnMtcmVhZCIsInByb2R1Y3Rfc3RvY2stcmVhZCIsInByb2R1Y3Rfc2V0X2N1c3RvbXMtcmVhZCIsInByb2R1Y3Rfc3RvY2std3JpdGUiLCJjYXRlZ29yaWVzLXdyaXRlIiwiY2F0ZWdvcmllcy1yZWFkIiwiY3VzdG9tZXJzLXJlYWQiLCJjdXN0b21lcnMtd3JpdGUiLCJjdXN0b21lcl9jdXN0b21zLXJlYWQiLCJvdGhlcl9hZGRyZXNzZXMtcmVhZCIsIm90aGVyX2FkZHJlc3Nlcy13cml0ZSIsIm9yZGVyX3N0YXR1c2VzLXJlYWQiLCJvcmRlcl9zdGF0dXNlcy13cml0ZSIsIm9yZGVycy1yZWFkIiwib3JkZXJzLXdyaXRlIiwib3JkZXJfcHJvZHVjdHMtcmVhZCIsIm9yZGVyX3Byb2R1Y3RzLXdyaXRlIiwib3JkZXJfY3VzdG9tcy1yZWFkIiwibG9naXN0aWNzLXJlYWQiLCJsb2dpc3RpY3Mtd3JpdGUiLCJ2aWV3X2dyb3Vwcy1yZWFkIiwicHJpY2VfZ3JvdXBzLXJlYWQiLCJzaGlwcGluZ19ncm91cHMtcmVhZCIsInByb2R1Y3RfZmVhdHVyZXMtcmVhZCIsInByb2R1Y3RfZmVhdHVyZXMtd3JpdGUiLCJib29rbWFya3MtcmVhZCIsImJvb2ttYXJrcy13cml0ZSIsInBvaW50cy1yZWFkIiwicG9pbnRzLXdyaXRlIl19.X2YfuagC_vnMl9rJeD5-3GL6eKIO1yVdoQH8vAogRTWul7RfWS9LYgz0ojmePRUdZaffNcKY_hhA5pqr5TKqUlMTsy59UEary0bS-DGt1h_i099ENRTb2N1EMggMO5nW0dyWyODag7_80TBDlMg7px9ZHm5k2qDX4xcxmVAru1IY2sDkj1ky3pX7769UpSacy2NVkRTnVk1AaaVEyQeyHNZXkA876HB0jKCKC8UDJ_xokueiJxA0JFxt9-QJ6IhDFqipLbyHXgUDfxQoMtbcSHeSqzMYL_i__n7Mg5U9yMzDMJ-36-4McwvkjnwiBt8pbioeZUuERjgQRGtIbTaQl61enJ1nHisZlWl4y4ZCkAo4Vrone84gpFETIWe4W8AZ2C3FFFBg_mOGFwGgYyh2KzNImiCOZU76_cuYwKWo6jFJp_Vh_s7b63RNcy9xyF1tPOa8EnYTocaMM-lDTj5_36dKhaunJv65QKIcOs8L6s9lY5cmj4mjW4dLweQMa_PapE1nYXf1AENZkgLnTj95O5XZC_Ohh_ZcIOZjc2lCQcoZDPVny4n1GaNxbw8dxI9Zx4d-sZT1k3YzpCOn8xU7uns2Q2G6KDmQZEGY2-_D0kkvEbrK8w5AN1Xca6ymIo4MnqhB4zgMF_jRgnRqBzFhuFyrBk5yobqElPpWN1CJNBs}

logging:
  level:
    org.hibernate.SQL: INFO
    org.hibernate.type.descriptor.sql.BasicBinder: OFF
    jp.co.oda32: INFO
    # PageImpl 直返しの警告を抑制（FE が flat PageImpl 形状を前提としているため VIA_DTO に切替えると破壊的変更）
    org.springframework.data.web.config.SpringDataWebConfiguration$PageModule$WarningLoggingModifier: ERROR
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2183ms:
# MF OAuth 用 暗号鍵生成 script (P1-05 案 C.3)
#
# 用途:
#   APP_CRYPTO_OAUTH_KEY  (パスフレーズ, 32 byte 乱数を base64)
#   APP_CRYPTO_OAUTH_SALT (PBKDF2 salt, 16 byte 乱数を hex)
#   を生成する。OauthCryptoUtil は salt を hex 必須としているため、salt は hex 出力にする。
#
# 実行例:
#   cd backend\scripts
#   .\gen-oauth-key.ps1
#
# 生成後の手順 (claudedocs/runbook-mf-oauth-keys.md Step 2):
#   1. IntelliJ Run Configuration -> Environment Variables に下記を追加
#      APP_CRYPTO_OAUTH_KEY=<上で表示された key>
#      APP_CRYPTO_OAUTH_SALT=<上で表示された salt>
#   2. backend を起動 -> Flyway V033 ログ "[V033] OAuth secrets re-encrypted: ..." を確認
#
# 注意:
#   - dev / prod で必ず別の鍵を生成すること
#   - 既存暗号化データは V033 が自動で再暗号化するので、手動 DELETE 不要
#   - 生成した key/salt は秘匿情報。git に commit しない / Slack 等に貼らない

$keyBytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($keyBytes)
$key = [Convert]::ToBase64String($keyBytes)

$saltBytes = New-Object byte[] 16
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($saltBytes)
$salt = -join ($saltBytes | ForEach-Object { $_.ToString("x2") })

Write-Host ""
Write-Host "=== APP_CRYPTO_OAUTH_KEY (32 byte base64) ===" -ForegroundColor Green
Write-Host $key
Write-Host ""
Write-Host "=== APP_CRYPTO_OAUTH_SALT (16 byte hex) ===" -ForegroundColor Green
Write-Host $salt
Write-Host ""
Write-Host "次の手順: claudedocs/runbook-mf-oauth-keys.md の Step 2 (IntelliJ Run Configuration)" -ForegroundColor Yellow
Write-Host ""
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2187ms:
# Runbook: MF OAuth 暗号鍵セットアップ・運用 (P1-05 案 C.3)

## 1. 概要

MF (マネーフォワード) OAuth クライアントとトークンを DB 保存する際に使用する AES-256/GCM 暗号鍵
(`app.crypto.oauth-key` / `app.crypto.oauth-salt`) のセットアップ・運用手順。

### なぜ専用鍵なのか (P1-05 経緯)

従来は `app.crypto.key` / `app.crypto.salt` 1 つの鍵で以下すべてを暗号化していた:

- 社内 password 系の `_enc` カラム
- `m_mf_oauth_client.client_secret_enc`
- `t_mf_oauth_token.access_token_enc`
- `t_mf_oauth_token.refresh_token_enc`

`app.crypto.key` / `app.crypto.salt` は dev fallback がリポジトリに commit 済みで、
万一その値が prod 環境変数の代替で使われると MF API token (= 第三者の業務システム閲覧/操作権) も
復号可能になるリスクがあった。

P1-05 案 C.3 で MF OAuth 関連 3 カラムだけを **専用鍵** (`app.crypto.oauth-key`/`oauth-salt`) に分離。
新鍵は **dev / prod とも env 必須** とし、リポジトリに dev fallback を持たせない (= 鍵が無いと起動失敗)。
社内 password 系の鍵は従来どおり dev fallback 維持 (MEMORY.md `feedback_dev_config_fallbacks` 方針)。

### 対象カラム

| テーブル | カラム |
|---|---|
| `m_mf_oauth_client` | `client_secret_enc` |
| `t_mf_oauth_token` | `access_token_enc` |
| `t_mf_oauth_token` | `refresh_token_enc` |

---

## 2. 初回セットアップ手順 (dev 環境)

### Step 1: 鍵生成

```powershell
cd C:\project\odamitsu-data-hub\backend\scripts
.\gen-oauth-key.ps1
```

出力例:

```
=== APP_CRYPTO_OAUTH_KEY (32 byte base64) ===
abcDEF...== (44 文字 base64)

=== APP_CRYPTO_OAUTH_SALT (16 byte hex) ===
0123456789abcdef0123456789abcdef
```

### Step 2: IntelliJ Run Configuration に env var 追加

1. IntelliJ → Run → Edit Configurations
2. `WebApplication` (バックエンド起動) を選択
3. `Modify options` → `Environment variables` を有効化
4. 下記 2 行を追加 (Step 1 の出力値):
   ```
   APP_CRYPTO_OAUTH_KEY=abcDEF...==
   APP_CRYPTO_OAUTH_SALT=0123456789abcdef0123456789abcdef
   ```
5. `BatchApplication` 用の Run Configuration にも同様に追加 (バッチ実行時も DB 復号する場合)

### Step 3: 起動 → Flyway V033 確認

`./gradlew bootRun --args='--spring.profiles.active=web,dev'` でバックエンド起動。

ログに以下が出れば成功:

```
[V033] OAuth secrets re-encrypted: client_secret=N, access_token=M, refresh_token=L
```

(初回 / 既に MF 連携設定が無い環境では N=M=L=0)

その後、`OauthCryptoUtil 初期化完了（MF OAuth 専用 AES-256 GCM, Hex 出力）` が出れば
Spring Bean も無事に立ち上がっている。

---

## 3. 本番デプロイ手順

### Step 1: 本番用の鍵生成

dev とは **必ず別の鍵** を生成する。`gen-oauth-key.ps1` を本番デプロイ作業端末で再実行。

### Step 2: 本番サーバーの env 設定

デプロイ方式に応じて以下のいずれかで env var を設定:

#### systemd service の場合

```ini
# /etc/systemd/system/odamitsu-backend.service
[Service]
Environment="APP_CRYPTO_OAUTH_KEY=...本番用鍵..."
Environment="APP_CRYPTO_OAUTH_SALT=...本番用 salt..."
```

`systemctl daemon-reload && systemctl restart odamitsu-backend`

#### docker-compose の場合

```yaml
# docker-compose.yml
services:
  backend:
    environment:
      APP_CRYPTO_OAUTH_KEY: ${APP_CRYPTO_OAUTH_KEY}
      APP_CRYPTO_OAUTH_SALT: ${APP_CRYPTO_OAUTH_SALT}
```

`.env` ファイル (compose と同じディレクトリ、git ignored) に値を記載。

#### Kubernetes Secret の場合

```bash
kubectl create secret generic mf-oauth-keys \
  --from-literal=APP_CRYPTO_OAUTH_KEY="..." \
  --from-literal=APP_CRYPTO_OAUTH_SALT="..."
```

Deployment manifest で `envFrom: secretRef: name: mf-oauth-keys` で注入。

### Step 3: V033 migration の実行

本番初回デプロイ時:
- 既存環境の `m_mf_oauth_client.client_secret_enc` と `t_mf_oauth_token.*_enc` は
  **旧鍵** (`APP_CRYPTO_KEY`/`SALT`) で暗号化されている前提
- V033 が起動時に自動で旧鍵 → 新鍵への再暗号化を実行
- 旧鍵環境変数が prod に設定済みなら V033 は env から旧鍵を取り、再暗号化
- 旧鍵環境変数が無い場合 V033 は失敗する → 旧鍵を一時的に env に設定して再起動

実行ログで件数を確認:

```
[V033] OAuth secrets re-encrypted: client_secret=1, access_token=1, refresh_token=1
```

### Step 4: 旧鍵スコープ縮小 (任意)

V033 完了後、`APP_CRYPTO_KEY`/`SALT` は MF OAuth 復号には不要になる
(社内 password 等の汎用暗号化用途のみ)。prod 上での権限 / 露出範囲を縮小することを検討する。

---

## 4. 鍵ローテーション手順 (定期 / 漏洩時)

### Step 1: 新鍵生成

```powershell
.\gen-oauth-key.ps1
```

新しい `APP_CRYPTO_OAUTH_KEY_v2` / `APP_CRYPTO_OAUTH_SALT_v2` を生成。

### Step 2: backend 停止

ローテーション中は MF API 連携が一時停止する。短時間で完了する見込みでも事前周知推奨。

### Step 3: 新 Flyway migration 追加

`V034__rotate_mf_oauth_secrets.java` を `V033` と同じパッケージに作成し、
旧鍵 (`APP_CRYPTO_OAUTH_KEY`) → 新鍵 (`APP_CRYPTO_OAUTH_KEY_v2`) で再暗号化する処理を実装。

V033 を雛形にコピーし、env var 名のみ変更:

```java
String oldKey = System.getenv("APP_CRYPTO_OAUTH_KEY");
String newKey = System.getenv("APP_CRYPTO_OAUTH_KEY_v2");
// 旧 fallback は無い (V033 と異なり、必ず env 必須)
if (oldKey == null || ...) throw new IllegalStateException("...");
```

### Step 4: env 切替 + 再起動

1. `APP_CRYPTO_OAUTH_KEY_v2` / `APP_CRYPTO_OAUTH_SALT_v2` を env に追加
2. backend 起動 → V034 が自動実行
3. ログで件数確認後、env 上で `APP_CRYPTO_OAUTH_KEY` を **新値で上書き** + `_v2` 環境変数を削除
4. 再度 backend 再起動

(あるいは V034 完了後に env 名を変えず単純に値を上書きするだけでも可。手順を 1 段階減らせるが、
V034 内で「新鍵が旧鍵と異なる」ことを assert する仕組みが必要)

---

## 5. 鍵紛失時の復旧手順

`APP_CRYPTO_OAUTH_KEY` / `SALT` が消失して既存 `_enc` データを復号不能になった場合:

### Step 1: 既存暗号化データの削除

```sql
-- m_mf_oauth_client.client_secret_enc は NOT NULL なので一旦行ごと削除する判断もあり
-- (画面から再登録する前提)
DELETE FROM t_mf_oauth_token;          -- 全 token を削除 (revoke 相当)
DELETE FROM m_mf_oauth_client;         -- client 設定も削除して画面から再登録
```

### Step 2: 新鍵を生成 + env 設定 (Step 1?2 と同じ)

### Step 3: backend 再起動

V033 は `WHERE *_enc IS NOT NULL` で対象 0 件になるので無事完了。

### Step 4: MF 連携を画面から再認可

1. 管理画面 `/finance/mf-integration` を開く
2. Client ID / Client Secret を再入力 (MF 開発者ポータルから取得)
3. 「認可開始」→ MF ログインフロー → callback で再 binding

---

## 6. トラブルシューティング

### 起動時 `IllegalStateException: app.crypto.oauth-key が未設定または短すぎます`

→ Step 1-2 を実施。env が読まれていない場合は IntelliJ Run Configuration 再起動 / shell 再起動。

### Flyway V033 失敗 (`BadPaddingException` / `AEADBadTagException`)

→ 旧鍵 (`APP_CRYPTO_KEY`/`SALT`) が変わった可能性。
   - 旧 env を一時的に元に戻して再起動
   - 思い当たらない場合は **鍵紛失復旧手順 (§5)** を実施

### `[V033] APP_CRYPTO_OAUTH_KEY と APP_CRYPTO_OAUTH_SALT は必須です。`

→ V033 migration が新鍵を取得できなかった。Step 1-2 を実施してから再起動。

### 既に migration 済みで起動

→ `flyway_schema_history` で `version=33` が `success=true` で記録されているか確認:

```sql
SELECT version, description, success, installed_on
FROM flyway_schema_history
WHERE version = '33';
```

success なら V033 は再実行されない (Flyway の正常動作)。

### `OauthCryptoUtil 初期化完了` のログが出ない

→ Spring が `OauthCryptoUtil` Bean を検出していない。`@ComponentScan` 範囲確認 (`jp.co.oda32` 配下なので
   通常は問題なし)。

### テスト実行時 (`./gradlew test`) に Bean 解決失敗

→ `backend/src/test/resources/config/application-test.yml` に
   `app.crypto.oauth-key` / `oauth-salt` が定義されているか確認。
   既定で固定ダミー値を入れてあるので、消したり変更したりしないこと。

---

## 7. 関連ファイル

- `backend/src/main/java/jp/co/oda32/util/OauthCryptoUtil.java` ? MF OAuth 専用暗号化 Bean
- `backend/src/main/java/jp/co/oda32/util/CryptoUtil.java` ? 汎用暗号化 Bean (社内 password 等)
- `backend/src/main/java/db/migration/V033__reencrypt_mf_oauth_secrets.java` ? 再暗号化 Java migration
- `backend/scripts/gen-oauth-key.ps1` ? 鍵生成 PowerShell script
- `backend/src/main/resources/config/application.yml`
- `backend/src/main/resources/config/application-dev.yml`
- `backend/src/main/resources/config/application-prod.yml`
- `backend/src/test/resources/config/application-test.yml`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java` ? `OauthCryptoUtil` を使用
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2202ms:
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:55544}/${DB_NAME:oda32db}
    username: ${DB_USERNAME:oda32}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: ${DB_POOL_SIZE:20}
      minimum-idle: 5
      idle-timeout: 300000
      max-lifetime: 1200000
      connection-timeout: 30000
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true
  # prod 環境は V001?V018 を手動で適用済みの既存 DB を想定し、baseline=18 でスキップする (B-W12)。
  # 新規環境立ち上げ時は一時的に FLYWAY_BASELINE_ON_MIGRATE=false / FLYWAY_BASELINE_VERSION= にして
  # 空 DB から V001 を走らせること。
  flyway:
    baseline-on-migrate: ${FLYWAY_BASELINE_ON_MIGRATE:true}
    baseline-version: ${FLYWAY_BASELINE_VERSION:18}

server:
  port: ${SERVER_PORT:8090}
  shutdown: graceful
  tomcat:
    max-threads: 200
    accept-count: 100

cors:
  allowed-origins: ${CORS_ORIGINS:https://odamitsu.example.com}

jwt:
  secret: ${JWT_SECRET}
  expiration-ms: ${JWT_EXPIRATION:86400000}

# AES 暗号化鍵。本番は必ず環境変数で注入すること。
# crypto.key/salt: 社内 password 等の汎用暗号化用。
# crypto.oauth-key/salt: MF OAuth 専用 (P1-05 案 C.3)。dev / prod とも env 必須。
#   セットアップは backend/scripts/gen-oauth-key.ps1 + claudedocs/runbook-mf-oauth-keys.md 参照。
app:
  crypto:
    key: ${APP_CRYPTO_KEY}
    salt: ${APP_CRYPTO_SALT}
    oauth-key: ${APP_CRYPTO_OAUTH_KEY}
    oauth-salt: ${APP_CRYPTO_OAUTH_SALT}

logging:
  level:
    root: WARN
    org.hibernate.SQL: OFF
    org.hibernate.type.descriptor.sql: OFF
    jp.co.oda32: INFO
    org.springframework.security: WARN
  file:
    name: ${LOG_PATH:./logs}/application.log
  logback:
    rollingpolicy:
      max-file-size: 50MB
      max-history: 30
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/TMfOauthToken.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/repository/finance/TMfOauthTokenRepository.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/repository/finance/MMfOauthClientRepository.java' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/resources/db/migration/V019__create_mf_integration_tables.sql' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/resources/db/migration/V021__mf_oauth_state_and_token_uniq.sql' in C:\project\odamitsu-data-hub
 succeeded in 1905ms:
package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MMfOauthClientRepository extends JpaRepository<MMfOauthClient, Integer> {

    /** 有効な (del_flg='0') クライアント設定を 1 件返す（通常 1 レコード運用）。 */
    Optional<MMfOauthClient> findFirstByDelFlgOrderByIdDesc(String delFlg);
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1970ms:
-- 2026-04-21: MF OAuth state を DB 永続化 (B-3)、t_mf_oauth_token に active 一意制約 (B-W2)、PKCE サポート (B-4)
-- 設計書: claudedocs/design-mf-integration-status.md

-- ========================================================================
-- t_mf_oauth_state: OAuth2 認可フロー state + PKCE code_verifier 永続化
-- ========================================================================
-- 旧実装は ConcurrentHashMap 管理 (MfOauthStateStore) だったため
-- マルチ JVM / 再起動で state が消失し認可不可になる問題があった。
-- state + code_verifier (PKCE S256) を DB に短期保管し、expired を sweep する。
CREATE TABLE IF NOT EXISTS t_mf_oauth_state (
    state               VARCHAR(64)   PRIMARY KEY,
    user_no             INTEGER,
    code_verifier       VARCHAR(128)  NOT NULL,
    expires_at          TIMESTAMP     NOT NULL,
    add_date_time       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mf_oauth_state_expires
    ON t_mf_oauth_state(expires_at);

COMMENT ON TABLE  t_mf_oauth_state         IS 'MF OAuth2 認可フローの state (CSRF 防止) + PKCE code_verifier。TTL 10 分。';
COMMENT ON COLUMN t_mf_oauth_state.state   IS '認可リクエスト時に発行 → callback で検証 (verifyAndConsume)';
COMMENT ON COLUMN t_mf_oauth_state.code_verifier IS 'PKCE S256 の verifier。authorize URL には S256(code_verifier) を code_challenge として送る。';

-- ========================================================================
-- t_mf_oauth_token: active 1 件保証 (B-W2)
-- ========================================================================
-- 同一 client_id で del_flg='0' が必ず 1 件以下になるよう partial unique index を追加。
-- softDeleteActiveTokens → save の race や例外コミットでアクティブ 2 件状態になるのを防ぐ。
CREATE UNIQUE INDEX IF NOT EXISTS uq_mf_oauth_token_active
    ON t_mf_oauth_token(client_id)
    WHERE del_flg = '0';
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2893ms:
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

 succeeded in 3883ms:
package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.TMfOauthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

@Repository
public interface TMfOauthTokenRepository extends JpaRepository<TMfOauthToken, Long> {

    /** 指定クライアントの有効トークン（del_flg='0'）。通常 1 件。 */
    Optional<TMfOauthToken> findFirstByClientIdAndDelFlgOrderByIdDesc(Integer clientId, String delFlg);

    /** 新規トークン保存前に旧レコードを一括論理削除する。 */
    @Modifying
    @Query("UPDATE TMfOauthToken t SET t.delFlg='1', t.modifyDateTime=:now, t.modifyUserNo=:userNo " +
            "WHERE t.clientId = :clientId AND t.delFlg='0'")
    int softDeleteActiveTokens(@Param("clientId") Integer clientId,
                               @Param("now") Timestamp now,
                               @Param("userNo") Integer userNo);
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 4854ms:
-- 2026-04-20: マネーフォワードクラウド会計 API 連携 Phase 1
-- 設計書: claudedocs/design-mf-integration-status.md
-- OAuth2 クライアント設定 / トークン永続化 / 勘定科目マッピングの 3 テーブル

-- ========================================================================
-- m_mf_oauth_client: OAuth2 クライアント設定（通常 1 レコード運用）
-- ========================================================================
CREATE TABLE IF NOT EXISTS m_mf_oauth_client (
    id                  SERIAL       PRIMARY KEY,
    client_id           VARCHAR(255) NOT NULL,
    client_secret_enc   VARCHAR(2000) NOT NULL, -- AES-256 (GCM) 暗号化
    redirect_uri        VARCHAR(500) NOT NULL,
    scope               VARCHAR(500) NOT NULL,
    authorize_url       VARCHAR(500) NOT NULL,
    token_url           VARCHAR(500) NOT NULL,
    api_base_url        VARCHAR(500) NOT NULL,
    del_flg             CHAR(1)      NOT NULL DEFAULT '0',
    add_date_time       TIMESTAMP    NOT NULL,
    add_user_no         INTEGER,
    modify_date_time    TIMESTAMP,
    modify_user_no      INTEGER
);

COMMENT ON TABLE  m_mf_oauth_client IS 'MF クラウド会計 API OAuth2 クライアント設定。通常 1 レコード。del_flg=0 が有効。';
COMMENT ON COLUMN m_mf_oauth_client.client_secret_enc IS 'AES-256 暗号化済み Client Secret。CryptoUtil で復号。';
COMMENT ON COLUMN m_mf_oauth_client.redirect_uri IS 'アプリポータルに登録する redirect URI。開発は http://localhost:3000/finance/mf-integration/callback';
COMMENT ON COLUMN m_mf_oauth_client.scope IS 'OAuth2 スコープ。Phase 1 は public read。';

-- ========================================================================
-- t_mf_oauth_token: トークン永続化（最新 1 件 active、履歴は論理削除）
-- ========================================================================
CREATE TABLE IF NOT EXISTS t_mf_oauth_token (
    id                  BIGSERIAL    PRIMARY KEY,
    client_id           INTEGER      NOT NULL REFERENCES m_mf_oauth_client(id),
    access_token_enc    VARCHAR(4000) NOT NULL,
    refresh_token_enc   VARCHAR(4000) NOT NULL,
    token_type          VARCHAR(50)  NOT NULL DEFAULT 'Bearer',
    expires_at          TIMESTAMP    NOT NULL,
    scope               VARCHAR(500),
    del_flg             CHAR(1)      NOT NULL DEFAULT '0',
    add_date_time       TIMESTAMP    NOT NULL,
    add_user_no         INTEGER,
    modify_date_time    TIMESTAMP,
    modify_user_no      INTEGER
);

CREATE INDEX IF NOT EXISTS idx_mf_oauth_token_active
    ON t_mf_oauth_token(client_id, del_flg);

COMMENT ON TABLE  t_mf_oauth_token IS 'MF API OAuth2 トークン。同一 client_id で del_flg=0 は 1 レコードのみ active 運用（新規取得時は旧レコードを del_flg=1 化）。';
COMMENT ON COLUMN t_mf_oauth_token.access_token_enc IS 'AES-256 暗号化済み access token';
COMMENT ON COLUMN t_mf_oauth_token.refresh_token_enc IS 'AES-256 暗号化済み refresh token';
COMMENT ON COLUMN t_mf_oauth_token.expires_at IS 'access_token の有効期限（UTC）。残り 5 分未満で自動 refresh。';

-- ========================================================================
-- m_mf_account_mapping: MF 勘定科目 → 自社仕訳種別(借方/貸方) マッピング
-- ========================================================================
CREATE TABLE IF NOT EXISTS m_mf_account_mapping (
    id                  SERIAL       PRIMARY KEY,
    journal_kind        VARCHAR(20)  NOT NULL, -- PURCHASE / SALES / PAYMENT
    side                VARCHAR(10)  NOT NULL, -- DEBIT / CREDIT
    mf_account_id       VARCHAR(100) NOT NULL, -- MF /api/v3/accounts の id
    mf_account_name     VARCHAR(100) NOT NULL, -- 最終同期時の name（表示用キャッシュ）
    del_flg             CHAR(1)      NOT NULL DEFAULT '0',
    add_date_time       TIMESTAMP    NOT NULL,
    add_user_no         INTEGER,
    modify_date_time    TIMESTAMP,
    modify_user_no      INTEGER,
    CONSTRAINT chk_mf_mapping_kind CHECK (journal_kind IN ('PURCHASE','SALES','PAYMENT')),
    CONSTRAINT chk_mf_mapping_side CHECK (side IN ('DEBIT','CREDIT')),
    CONSTRAINT uq_mf_mapping_active UNIQUE (journal_kind, side, mf_account_id, del_flg)
);

COMMENT ON TABLE  m_mf_account_mapping IS 'MF 勘定科目(id) と自社仕訳種別(PURCHASE/SALES/PAYMENT) × 借方/貸方 のマッピング。admin が画面で編集。洗い替え運用。';
COMMENT ON COLUMN m_mf_account_mapping.mf_account_id IS 'MF GET /api/v3/accounts のレスポンス id';
COMMENT ON COLUMN m_mf_account_mapping.mf_account_name IS '最終同期時の name をキャッシュ。表示用で、突合判定には使わない（id ベース）。';
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
実装の形が見えてきました。重点リスクは、暗号化移行の再実行不能性、未バインド旧トークンの扱い、tenant mismatch 応答の情報露出、refresh_token 残日数ロジックの rotation 前提あたりに集中しています。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java -Pattern 'handleCallback|consumeStateAndLoadClient|persistTokenAndTenantInNewTx|getValidAccessToken|getStatus|bindOrVerifyTenant|persistToken|REFRESH_TOKEN_LIFETIME_DAYS|revoke|client.getMfTenantId\\(\\) == null'" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/db/migration/V033__reencrypt_mf_oauth_secrets.java -Pattern 'DEV_FALLBACK|envOrDefault|APP_CRYPTO_OAUTH|conn.setAutoCommit|commit|rollback|reencrypt|SELECT|UPDATE|decrypt|encrypt'" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/jp/co/oda32/api/finance/FinanceExceptionHandler.java,backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java -Pattern 'MfTenantMismatch|ex.getMessage|bound|observed|CONFLICT|callback'" in C:\project\odamitsu-data-hub
2026-05-06T05:35:25.943779Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/jp/co/oda32/api/finance/FinanceExceptionHandler.java,backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java -Pattern 'MfTenantMismatch|ex.getMessage|bound|observed|CONFLICT|callback'"` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/jp/co/oda32/api/finance/FinanceExceptionHandler.java,backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java -Pattern 'MfTenantMismatch|ex.getMessage|bound|observed|CONFLICT|callback'"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java -Pattern 'fromHttpUrl|client.getApiBaseUrl|client.getAuthorizeUrl|client.getTokenUrl|getTenant|/v2/tenant|basicAuthHeader'" in C:\project\odamitsu-data-hub
2026-05-06T05:35:25.946747Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path frontend/components/common/MfReAuthBanner.tsx,frontend/components/common/MfScopeBanner.tsx,frontend/types/mf-integration.ts,frontend/components/layout/AppLayout.tsx -Pattern 'THRESHOLD|daysUntilReauth|MF_REQUIRED_SCOPES|queryKey|refetchInterval|MfReAuthBanner|MfScopeBanner|scopeOk'"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path frontend/components/common/MfReAuthBanner.tsx,frontend/components/common/MfScopeBanner.tsx,frontend/types/mf-integration.ts,frontend/components/layout/AppLayout.tsx -Pattern 'THRESHOLD|daysUntilReauth|MF_REQUIRED_SCOPES|queryKey|refetchInterval|MfReAuthBanner|MfScopeBanner|scopeOk'" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path frontend/components/common/MfReAuthBanner.tsx,frontend/components/common/MfScopeBanner.tsx,frontend/types/mf-integration.ts,frontend/components/layout/AppLayout.tsx -Pattern 'THRESHOLD|daysUntilReauth|MF_REQUIRED_SCOPES|queryKey|refetchInterval|MfReAuthBanner|MfScopeBanner|scopeOk'"` rejected: blocked by policy
 succeeded in 1857ms:

backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:126:     *   <li>read-only tx: state 消
費 + client 取得 ({@link #consumeStateAndLoadClient})</li>
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:128:     *   <li>REQUIRES_NEW tx: token
 永続化 ({@link #persistTokenInNewTx})</li>
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:133:    public void handleCallback(Stri
ng code, String state, Integer userNo) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:135:        CallbackContext ctx = self.
consumeStateAndLoadClient(state, userNo);
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:144:        self.persistTokenAndTenantI
nNewTx(ctx.client().getId(), token, tenant, userNo);
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:149:    public CallbackContext consumeS
tateAndLoadClient(String state, Integer userNo) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:162:    public void persistTokenInNewTx
(Integer clientId, MfTokenResponse token, Integer userNo) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:165:        persistToken(client, token,
 userNo);
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:178:    public void persistTokenAndTena
ntInNewTx(
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:182:        bindOrVerifyTenant(client, 
tenant, userNo);
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:183:        persistToken(client, token,
 userNo);
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:186:    /** {@link #consumeStateAndLoad
Client} 戻り値。 */
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:203:    public String getValidAccessTok
en() {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:251:        persistToken(client, refres
hed, null);
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:266:        bindOrVerifyTenant(client, 
tenant, null);
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:267:        persistToken(client, refres
hed, null);
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:271:    private static final long REFRE
SH_TOKEN_LIFETIME_DAYS = 540L;
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:273:    public MfTokenStatus getStatus(
) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:308:            long remaining = REFRES
H_TOKEN_LIFETIME_DAYS - elapsedDays;
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:321:     * (P1-01: 別 MF tenant への再
認可を許可するため)。MF 側 revoke は現時点でベストエフォート省略。
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:324:    @AuditLog(table = "t_mf_oauth_t
oken", operation = "revoke", captureArgsAsAfter = true)
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:325:    public void revoke(Integer user
No) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:332:            log.info("MF revoke: te
nant binding をクリアします clientId={} previousTenantId={} previousTenantName={}",
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:351:    private void bindOrVerifyTenant
(MMfOauthClient client, MfTenantResponse tenant, Integer userNo) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:359:        if (client.getMfTenantId() 
== null) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:393:    private void persistToken(MMfOa
uthClient client, MfTokenResponse res, Integer userNo) {

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1914ms:

backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:59:        UriComponentsBuilder b = UriCom
ponentsBuilder.fromHttpUrl(client.getAuthorizeUrl())
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:85:                () -> postToken(client.
getTokenUrl(), form, basicAuthHeader(client.getClientId(), clientSecret)),
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:95:                () -> postToken(client.
getTokenUrl(), form, basicAuthHeader(client.getClientId(), clientSecret)),
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:114:        String url = UriComponentsBuil
der.fromHttpUrl(client.getApiBaseUrl() + "/api/v3/journals")
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:149:        String url = client.getApiBase
Url() + "/api/v3/taxes";
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:187:        String url = UriComponentsBuil
der.fromHttpUrl(client.getApiBaseUrl() + "/api/v3/reports/trial_balance_bs")
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:218:     * GET /v2/tenant で MF クラウド会
計の tenant (= 連携先事業者) 情報を取得。
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:227:    public MfTenantResponse getTenant(
MMfOauthClient client, String accessToken) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:228:        String url = client.getApiBase
Url() + "/v2/tenant";
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:238:                    throw new IllegalS
tateException("MF /v2/tenant が tenant id を返却しませんでした");
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:244:                log.warn("MF /v2/tenan
t 取得失敗: status={}, body={}", status.value(), SensitiveLogMasker.mask(body));
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:254:        }, "getTenant");
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:264:        String url = client.getApiBase
Url() + "/api/v3/accounts";
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:318:    private static String basicAuthHea
der(String clientId, String clientSecret) {

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1925ms:

backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:5:import org.springframework.security.crypto.e
ncrypt.Encryptors;
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:6:import org.springframework.security.crypto.e
ncrypt.TextEncryptor;
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:17: * 新鍵 ({@code APP_CRYPTO_OAUTH_KEY} / {@c
ode APP_CRYPTO_OAUTH_SALT}) で再暗号化する。
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:28: *   <li>autoCommit OFF で各テーブルを SELE
CT → 旧鍵 decrypt → 新鍵 encrypt → UPDATE</li>
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:29: *   <li>全件成功なら commit、途中で失敗し
たら rollback して Flyway に例外伝播 → migration 失敗</li>
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:36:public class V033__reencrypt_mf_oauth_secre
ts extends BaseJavaMigration {
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:43:    private static final String DEV_FALLBAC
K_KEY = "dev-odamitsu-data-hub-crypto-key-2026";
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:44:    private static final String DEV_FALLBAC
K_SALT = "3f6a2d8c9e1b4a7d5c0f8e2a9b6d1c3f";
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:48:        String oldKey = envOrDefault("APP_C
RYPTO_KEY", DEV_FALLBACK_KEY);
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:49:        String oldSalt = envOrDefault("APP_
CRYPTO_SALT", DEV_FALLBACK_SALT);
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:50:        String newKey = System.getenv("APP_
CRYPTO_OAUTH_KEY");
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:51:        String newSalt = System.getenv("APP
_CRYPTO_OAUTH_SALT");
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:55:                    "[V033] APP_CRYPTO_OAUT
H_KEY と APP_CRYPTO_OAUTH_SALT は必須です。"
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:60:        TextEncryptor oldCipher = Encryptor
s.delux(oldKey, oldSalt);
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:61:        TextEncryptor newCipher = Encryptor
s.delux(newKey, newSalt);
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:64:        boolean originalAutoCommit = conn.g
etAutoCommit();
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:65:        conn.setAutoCommit(false);
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:67:            int clientCount = reencrypt(con
n, "m_mf_oauth_client", "id", "client_secret_enc", oldCipher, newCipher);
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:68:            int accessCount = reencrypt(con
n, "t_mf_oauth_token", "id", "access_token_enc", oldCipher, newCipher);
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:69:            int refreshCount = reencrypt(co
nn, "t_mf_oauth_token", "id", "refresh_token_enc", oldCipher, newCipher);
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:70:            conn.commit();
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:72:                    "[V033] OAuth secrets r
e-encrypted: client_secret=%d, access_token=%d, refresh_token=%d%n",
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:75:            conn.rollback();
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:81:            conn.setAutoCommit(originalAuto
Commit);
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:86:     * 指定テーブルの暗号化カラムを「旧鍵で
 decrypt → 新鍵で encrypt」して UPDATE する。
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:87:     * NULL 行はスキップ。autoCommit OFF 前
提 (commit / rollback は呼び出し側)。
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:91:    private static int reencrypt(Connection
 conn, String table, String pkColumn, String column,
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:92:                                 TextEncryp
tor oldCipher, TextEncryptor newCipher) throws Exception {
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:93:        String selectSql = "SELECT " + pkCo
lumn + ", " + column + " FROM " + table
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:95:        String updateSql = "UPDATE " + tabl
e + " SET " + column + " = ? WHERE " + pkColumn + " = ?";
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:97:        try (Statement select = conn.create
Statement();
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:98:             ResultSet rs = select.executeQ
uery(selectSql);
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:99:             PreparedStatement update = con
n.prepareStatement(updateSql)) {
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:105:                    plain = oldCipher.decr
ypt(oldEnc);
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:111:                String newEnc = newCipher.
encrypt(plain);
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:112:                update.setString(1, newEnc
);
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:113:                update.setInt(2, pk);
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:114:                update.addBatch();
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:118:                update.executeBatch();
backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:124:    private static String envOrDefault(Str
ing name, String defaultValue) {

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/jp/co/oda32/api/finance/FinanceExceptionHandler.java -Pattern 'MfTenantMismatch|ex.getMessage|bound|observed|CONFLICT'" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java -Pattern 'MfTenantMismatch|e.getMessage|bound|observed|CONFLICT|callback'" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path frontend/components/common/MfReAuthBanner.tsx -Pattern 'THRESHOLD|daysUntilReauth|queryKey|refetchInterval|days <= 0'" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path frontend/components/common/MfScopeBanner.tsx -Pattern 'queryKey|refetchInterval|scopeOk|missingScopes'" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path frontend/types/mf-integration.ts -Pattern 'MF_REQUIRED_SCOPES|daysUntilReauth|scopeOk|missingScopes|MF_DEFAULT_SCOPE'" in C:\project\odamitsu-data-hub
 succeeded in 3661ms:

frontend\components\common\MfReAuthBanner.tsx:15: * グローバル top header (AppLayout) に常設し、`daysUntilReauth` に応
じて
frontend\components\common\MfReAuthBanner.tsx:18: * - `staleTime` 5 分 + `refetchInterval` 1 時間で API 負荷を最小化
frontend\components\common\MfReAuthBanner.tsx:20: * - `daysUntilReauth > 60` は非表示 (まだ早すぎ)
frontend\components\common\MfReAuthBanner.tsx:23:const THRESHOLD_60 = 60
frontend\components\common\MfReAuthBanner.tsx:24:const THRESHOLD_30 = 30
frontend\components\common\MfReAuthBanner.tsx:25:const THRESHOLD_14 = 14
frontend\components\common\MfReAuthBanner.tsx:26:const THRESHOLD_7 = 7
frontend\components\common\MfReAuthBanner.tsx:32:    queryKey: ['mf-oauth-status'],
frontend\components\common\MfReAuthBanner.tsx:36:    refetchInterval: 60 * 60 * 1000, // 1 時間ごとに refetch
frontend\components\common\MfReAuthBanner.tsx:43:  if (data.daysUntilReauth == null || data.daysUntilReauth > THRESHOLD
_60) return null
frontend\components\common\MfReAuthBanner.tsx:45:  const days = data.daysUntilReauth
frontend\components\common\MfReAuthBanner.tsx:53:  if (days <= 0) {
frontend\components\common\MfReAuthBanner.tsx:57:  } else if (days <= THRESHOLD_7) {
frontend\components\common\MfReAuthBanner.tsx:62:  } else if (days <= THRESHOLD_14) {
frontend\components\common\MfReAuthBanner.tsx:66:  } else if (days <= THRESHOLD_30) {

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 4769ms:

frontend\components\common\MfScopeBanner.tsx:21: *   <li>scopeOk=true (= missingScopes 空) なら表示しない</li>
frontend\components\common\MfScopeBanner.tsx:22: *   <li>queryKey は {@link MfReAuthBanner} と共有 ('mf-oauth-status')
。
frontend\components\common\MfScopeBanner.tsx:30:    queryKey: ['mf-oauth-status'],
frontend\components\common\MfScopeBanner.tsx:34:    refetchInterval: 60 * 60 * 1000, // 1 時間ごとに refetch
frontend\components\common\MfScopeBanner.tsx:43:  if (data.scopeOk) return null
frontend\components\common\MfScopeBanner.tsx:53:            {data.missingScopes.map((s) => (

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 5783ms:

backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:5:import jp.co.oda32.domain.service.finance.
mf.MfTenantMismatchException;
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:32:        log.warn("MF re-auth required: {}
", ex.getMessage());
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:34:                .body(ErrorResponse.of(ex
.getMessage(), "MF_REAUTH_REQUIRED"));
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:40:        log.warn("MF scope insufficient (
requiredScope={}): {}", ex.getRequiredScope(), ex.getMessage());
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:42:                .body(ErrorResponse.withS
cope(ex.getMessage(), "MF_SCOPE_INSUFFICIENT", ex.getRequiredScope()));
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:46:     * MF tenant binding 不一致 (P1-01 / 
DD-F-04) → 409 CONFLICT。
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:53:    @ExceptionHandler(MfTenantMismatchExc
eption.class)
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:54:    public ResponseEntity<ErrorResponse> 
handleMfTenantMismatch(MfTenantMismatchException ex) {
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:55:        log.error("MF tenant mismatch det
ected: bound={} observed={}",
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:56:                ex.getBoundTenantId(), ex
.getObservedTenantId());
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:57:        return ResponseEntity.status(Http
Status.CONFLICT)
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:58:                .body(ErrorResponse.of(ex
.getMessage(), "MF_TENANT_MISMATCH"));
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:73:        log.warn("Finance IllegalStateExc
eption: {}", ex.getMessage(), ex);
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:88:        log.info("Finance business except
ion: code={}, message={}", ex.getErrorCode(), ex.getMessage());
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:91:                .body(ErrorResponse.of(ex
.getMessage(), code));
backend\src\main\java\jp\co\oda32\api\finance\FinanceExceptionHandler.java:104:        log.warn("Finance internal excep
tion: {}", ex.getMessage(), ex);

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 6829ms:

backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationController.java:12:import jp.co.oda32.domain.service.finance
.mf.MfTenantMismatchException;
backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationController.java:99:     * MF からのリダイレクト後、フロント
の callback ページが code/state を POST してくる。
backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationController.java:106:    @PostMapping("/oauth/callback")
backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationController.java:107:    public ResponseEntity<?> callback(@R
equestBody Map<String, String> body) throws Exception {
backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationController.java:115:            mfOauthService.handleCallbac
k(code, state, userNo);
backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationController.java:118:            log.warn("MF OAuth callback:
 state 検証失敗 userNo={} message={}", userNo, e.getMessage());
backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationController.java:123:            log.warn("MF OAuth callback:
 再認証が必要 userNo={}", userNo, e);
backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationController.java:126:        } catch (MfTenantMismatchExcepti
on e) {
backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationController.java:128:            log.error("MF OAuth callback
: tenant binding 不一致 userNo={} bound={} observed={}",
backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationController.java:129:                    userNo, e.getBoundTe
nantId(), e.getObservedTenantId());
backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationController.java:130:            return ResponseEntity.status
(HttpStatus.CONFLICT)
backend\src\main\java\jp\co\oda32\api\finance\MfIntegrationController.java:131:                    .body(Map.of("messag
e", e.getMessage(), "code", "MF_TENANT_MISMATCH"));

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 7809ms:

frontend\types\mf-integration.ts:39:  daysUntilReauth: number | null
frontend\types\mf-integration.ts:43:   * Backend `MfScopeConstants.REQUIRED_SCOPES` と同期 (=`MF_REQUIRED_SCOPES` 定数)
。
frontend\types\mf-integration.ts:45:  missingScopes: string[]
frontend\types\mf-integration.ts:50:  /** T6: missingScopes.isEmpty() の short-hand。false なら `MfScopeBanner` を表示
。 */
frontend\types\mf-integration.ts:51:  scopeOk: boolean
frontend\types\mf-integration.ts:224:// backend `MfOauthService.getStatus()` がこの一覧と DB scope を比較して `MfTokenS
tatus.missingScopes`
frontend\types\mf-integration.ts:226:export const MF_REQUIRED_SCOPES = [
frontend\types\mf-integration.ts:235:export const MF_DEFAULT_SCOPE = MF_REQUIRED_SCOPES.join(' ')
frontend\types\mf-integration.ts:239:  scope: MF_DEFAULT_SCOPE,

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/jp/co/oda32/util/CryptoUtil.java -Pattern 'Encryptors.delux|password.length|salt.length|HEX_ONLY'" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/java/jp/co/oda32/util/OauthCryptoUtil.java -Pattern 'Encryptors.delux|password.length|salt.length|HEX_ONLY'" in C:\project\odamitsu-data-hub
2026-05-06T05:35:52.271114Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/resources/config/application-dev.yml,backend/src/main/resources/config/application-prod.yml,backend/src/test/resources/config/application-test.yml -Pattern 'oauth-key|oauth-salt|APP_CRYPTO_KEY|APP_CRYPTO_OAUTH|flyway|baseline'"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/resources/config/application-dev.yml,backend/src/main/resources/config/application-prod.yml,backend/src/test/resources/config/application-test.yml -Pattern 'oauth-key|oauth-salt|APP_CRYPTO_KEY|APP_CRYPTO_OAUTH|flyway|baseline'" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend/src/main/resources/config/application-dev.yml,backend/src/main/resources/config/application-prod.yml,backend/src/test/resources/config/application-test.yml -Pattern 'oauth-key|oauth-salt|APP_CRYPTO_KEY|APP_CRYPTO_OAUTH|flyway|baseline'"` rejected: blocked by policy
 succeeded in 8371ms:

backend\src\main\java\jp\co\oda32\util\OauthCryptoUtil.java:46:        if (password == null || password.isBlank() || pa
ssword.length() < 16) {
backend\src\main\java\jp\co\oda32\util\OauthCryptoUtil.java:58:        if (!HEX_ONLY.matcher(salt).matches()) {
backend\src\main\java\jp\co\oda32\util\OauthCryptoUtil.java:62:        if (salt.length() < 16) {
backend\src\main\java\jp\co\oda32\util\OauthCryptoUtil.java:67:        this.encryptor = Encryptors.delux(password, salt
);
backend\src\main\java\jp\co\oda32\util\OauthCryptoUtil.java:83:    private static final java.util.regex.Pattern HEX_ONL
Y = java.util.regex.Pattern.compile("^[0-9a-fA-F]+$");

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 9372ms:

backend\src\main\java\jp\co\oda32\util\CryptoUtil.java:19: *   <li>Spring Security の {@link Encryptors#delux} を使用（
PBKDF2 + AES/GCM）</li>
backend\src\main\java\jp\co\oda32\util\CryptoUtil.java:45:        if (password == null || password.isBlank() || passwor
d.length() < 16) {
backend\src\main\java\jp\co\oda32\util\CryptoUtil.java:55:        if (!HEX_ONLY.matcher(salt).matches()) {
backend\src\main\java\jp\co\oda32\util\CryptoUtil.java:59:        if (salt.length() < 16) {
backend\src\main\java\jp\co\oda32\util\CryptoUtil.java:63:        // Encryptors.delux: PBKDF2 + AES/GCM/NoPadding + 16b
yte IV + Hex エンコード出力
backend\src\main\java\jp\co\oda32\util\CryptoUtil.java:64:        this.encryptor = Encryptors.delux(password, salt);
backend\src\main\java\jp\co\oda32\util\CryptoUtil.java:80:    private static final java.util.regex.Pattern HEX_ONLY = j
ava.util.regex.Pattern.compile("^[0-9a-fA-F]+$");

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
**指摘事項**

1. Severity: **Critical**  
   カテゴリ: セキュリティ / 運用  
   内容: `V033__reencrypt_mf_oauth_secrets.java` は migration 内で `conn.commit()` を明示実行していますが、Flyway の schema history 記録と完全に同一 atomic unit とは言い切れません。DB 更新 commit 後、Flyway 履歴記録前にプロセス停止すると、暗号文は新鍵済みなのに `V033` は未適用扱いとなり、次回起動で旧鍵復号を試みて復旧困難になります。暗号化 version/key id カラムもないため、自動判別できません。  
   推奨アクション: migration 内の手動 commit を避け、Flyway transaction に委ねる設計へ寄せてください。少なくとも `oauth_encryption_version` または migration sentinel を追加し、再実行時に新鍵済みを検出できるようにしてください。

2. Severity: **Major**  
   カテゴリ: セキュリティ / 運用  
   内容: `V033` は `System.getenv()` のみで鍵を読みます。Spring の `application-*.yml`、JVM system property、Secret Manager、Kubernetes file mount 経由の設定は migration から見えません。一方で `OauthCryptoUtil` は Spring property 経由なので、migration と runtime で鍵解決経路がずれます。  
   推奨アクション: Flyway migration 用の鍵注入方式を運用で明文化するだけでなく、可能なら Spring-aware migration か、起動前の独立 re-encrypt job に分離してください。

3. Severity: **Major**  
   カテゴリ: セキュリティ  
   内容: tenant mismatch のレスポンスが `ex.getMessage()` をそのまま返しており、既存 tenant id と観測 tenant id を client に露出します。管理者限定 endpoint とはいえ、攻撃者が admin session を奪った場合や内部不正では「別 tenant が存在する」「既存連携先 id」が分かります。`FinanceExceptionHandler` と `MfIntegrationController` の callback catch の両方で同じ露出があります。  
   推奨アクション: client には汎用文言と `MF_TENANT_MISMATCH` のみ返し、tenant id/name は監査ログ限定にしてください。

4. Severity: **Major**  
   カテゴリ: 整合性 / セキュリティ  
   内容: `mf_tenant_id == null` の既存認可済 client は、access_token が fresh な間は `getValidAccessToken()` で tenant 検証されません。検証は callback と refresh 後のみなので、未バインド旧データは最大で access token 期限または利用タイミング次第で NULL のまま業務 API を実行できます。設計コメントの「次回 refresh で自動 binding」は正しいですが、その前の API 呼び出しは tenant 未検証です。  
   推奨アクション: `mfTenantId == null` かつ token connected の場合は、最初の `getValidAccessToken()` で `/v2/tenant` を強制取得して binding してください。

5. Severity: **Major**  
   カテゴリ: 整合性 / 運用  
   内容: refresh_token 540 日判定は現 active token の `add_date_time` に依存していますが、`persistToken()` は MF が `refresh_token` を返さない rotation OFF 時も新 token row を insert します。その場合、実際の refresh_token 発行日は古いままなのに `add_date_time` が更新され、残日数が過大評価されます。コードコメントの「早めに出るだけ」と逆で、警告が遅れる可能性があります。  
   推奨アクション: `refresh_token_issued_at` を token row に明示保持し、refresh_token が null で既存値流用なら発行時刻も旧値を引き継いでください。

6. Severity: **Major**  
   カテゴリ: セキュリティ  
   内容: `authorize_url` / `token_url` / `api_base_url` は admin が自由入力でき、tenant binding では改竄先が本物の MF かどうかを保証できません。悪意ある token endpoint に `client_id/client_secret` を Basic 送信する SSRF/credential exfiltration の経路になります。redirect URI は whitelist されていますが、OAuth endpoint 群は whitelist されていません。  
   推奨アクション: MF 公式 host の allowlist と `https` 強制を `upsertClient()` で実装してください。dev 例外が必要なら profile 条件付きで限定してください。

7. Severity: **Major**  
   カテゴリ: 整合性  
   内容: callback は `state consume → token exchange → tenant get → persist` の順で概ね正しいですが、client 設定の snapshot を HTTP 呼び出し中に保持します。その間に別 admin が client 設定や tenant binding を変更しても、最後の `persistTokenAndTenantInNewTx()` は id だけで再取得し、現在の client に対して古い token response を保存し得ます。  
   推奨アクション: persist 時に `clientId/tokenUrl/apiBaseUrl` などの snapshot 一致確認、または client row の optimistic lock/version を入れてください。

8. Severity: **Major**  
   カテゴリ: セキュリティ  
   内容: `OauthCryptoUtil` は `CryptoUtil` と同じ `Encryptors.delux()` で、IV/tag などは実質同等です。ただし PBKDF2 iteration count は Spring Security 実装依存でコード上から固定・検査できず、鍵の entropy も「16文字以上」しか見ていません。生成スクリプト利用時は十分でも、手入力の弱い passphrase を拒否できません。  
   推奨アクション: OAuth 鍵は base64 32 bytes など形式を検証し、KMS/SecretKeySpec 方式か、少なくとも iteration count を明示できる実装へ移行検討してください。

9. Severity: **Minor**  
   カテゴリ: 設計  
   内容: `OauthCryptoUtil` と `CryptoUtil` はほぼ完全に重複しています。現在は意図が読みやすい一方、salt validation、エラーメッセージ、将来の暗号方式更新が二重管理になります。片方だけ修正されるリスクがあります。  
   推奨アクション: 共通の `TextEncryptorFactory` または abstract base に validation と初期化を集約し、property prefix だけ分ける構成がよいです。

10. Severity: **Major**  
    カテゴリ: 整合性 / 設計  
    内容: backend の `MfScopeConstants.REQUIRED_SCOPES` と frontend の `MF_REQUIRED_SCOPES` はコメントで同期義務を示すだけです。将来どちらかだけ変更されると、UI の初期値と backend の警告判定が乖離します。これは scope 不足の検知機能そのものの信頼性を落とします。  
    推奨アクション: `/oauth/required-scopes` のように backend から配信するか、少なくともテストで frontend 定数と backend 定数の一致を検証してください。

11. Severity: **Minor**  
    カテゴリ: 運用 / UI  
    内容: `MfReAuthBanner` は `daysUntilReauth` の整数日だけで 60/30/14/7/0 判定しています。backend は `ChronoUnit.DAYS.between(issuedAt, Instant.now())` なので、期限当日の「残り数時間」でも `1` と表示され得ます。timezone 表示は `toLocaleString('ja-JP')` で端末依存です。  
    推奨アクション: backend で `refreshTokenExpiresAt` を返し、UI は日時と残時間ベースで「24時間未満」などを表示してください。

12. Severity: **Minor**  
    カテゴリ: 設計  
    内容: `MfTokenStatus` に接続状態、tenant binding、refresh_token 寿命、scope drift が詰め込まれています。現状は単一 status API として便利ですが、banner が増えるほど全 UI が同じ DTO の変更影響を受けます。`MfReAuthBanner` と `MfScopeBanner` は queryKey 共有で効率はよいものの、責務境界は曖昧です。  
    推奨アクション: API は維持しても、backend 内部では `TokenHealth`, `TenantBindingStatus`, `ScopeHealth` のように生成責務を分けると拡張しやすいです。

13. Severity: **Major**  
    カテゴリ: 運用  
    内容: MF 側で token が revoke された状態で `getStatus()` を呼んでも、DB の token 有無と期限だけを見るため connected=true のままです。実際の API 呼び出し時に 401 で初めて `MfReAuthRequiredException` になります。グローバル banner も revoke を検知できません。  
    推奨アクション: 明示的な「接続確認」操作、または低頻度の health check endpoint で 401 を status に反映する設計を追加してください。

14. Severity: **Minor**  
    カテゴリ: 運用 / DX  
    内容: dev でも OAuth 鍵 env 必須にした方針は安全側ですが、`V033` と `OauthCryptoUtil` の両方で fail-fast するため、初回開発者は起動不能になります。runbook はありますが、エラーは環境変数名中心で、プロファイル別の最短復旧手順までは自動化されていません。  
    推奨アクション: `./backend/scripts/gen-oauth-key.ps1 -SetUserEnv` のようなセットアップ補助、または dev 起動前チェック script を用意してください。

**補足評価**

- `MfApiClient.getTenant()` の呼び出し頻度は callback と refresh 後のみで、通常時の API 負荷は抑えられています。ただし未バインド旧 token だけは初回利用時に強制検証すべきです。
- `V033` の DML 自体は rollback 可能な形ですが、手動 commit と暗号化 version 不在が運用上の弱点です。
- AES-GCM 実装は `CryptoUtil` と等価ですが、「等価だから十分」とは言い切れず、鍵強度と KDF パラメータの明示性が不足しています。

**Opus 実装で見落としていた最重要 3 件**

1. `V033` の commit 後・Flyway 履歴前クラッシュで、新鍵済みデータを旧鍵で再 migration しようとして詰む点。  
2. rotation OFF の refresh_token で `add_date_time` を発行日扱いし、540日警告が遅れる点。  
3. `authorize_url/token_url/api_base_url` 改竄による client_secret 流出は tenant binding では防げない点。
tokens used
120,757
**指摘事項**

1. Severity: **Critical**  
   カテゴリ: セキュリティ / 運用  
   内容: `V033__reencrypt_mf_oauth_secrets.java` は migration 内で `conn.commit()` を明示実行していますが、Flyway の schema history 記録と完全に同一 atomic unit とは言い切れません。DB 更新 commit 後、Flyway 履歴記録前にプロセス停止すると、暗号文は新鍵済みなのに `V033` は未適用扱いとなり、次回起動で旧鍵復号を試みて復旧困難になります。暗号化 version/key id カラムもないため、自動判別できません。  
   推奨アクション: migration 内の手動 commit を避け、Flyway transaction に委ねる設計へ寄せてください。少なくとも `oauth_encryption_version` または migration sentinel を追加し、再実行時に新鍵済みを検出できるようにしてください。

2. Severity: **Major**  
   カテゴリ: セキュリティ / 運用  
   内容: `V033` は `System.getenv()` のみで鍵を読みます。Spring の `application-*.yml`、JVM system property、Secret Manager、Kubernetes file mount 経由の設定は migration から見えません。一方で `OauthCryptoUtil` は Spring property 経由なので、migration と runtime で鍵解決経路がずれます。  
   推奨アクション: Flyway migration 用の鍵注入方式を運用で明文化するだけでなく、可能なら Spring-aware migration か、起動前の独立 re-encrypt job に分離してください。

3. Severity: **Major**  
   カテゴリ: セキュリティ  
   内容: tenant mismatch のレスポンスが `ex.getMessage()` をそのまま返しており、既存 tenant id と観測 tenant id を client に露出します。管理者限定 endpoint とはいえ、攻撃者が admin session を奪った場合や内部不正では「別 tenant が存在する」「既存連携先 id」が分かります。`FinanceExceptionHandler` と `MfIntegrationController` の callback catch の両方で同じ露出があります。  
   推奨アクション: client には汎用文言と `MF_TENANT_MISMATCH` のみ返し、tenant id/name は監査ログ限定にしてください。

4. Severity: **Major**  
   カテゴリ: 整合性 / セキュリティ  
   内容: `mf_tenant_id == null` の既存認可済 client は、access_token が fresh な間は `getValidAccessToken()` で tenant 検証されません。検証は callback と refresh 後のみなので、未バインド旧データは最大で access token 期限または利用タイミング次第で NULL のまま業務 API を実行できます。設計コメントの「次回 refresh で自動 binding」は正しいですが、その前の API 呼び出しは tenant 未検証です。  
   推奨アクション: `mfTenantId == null` かつ token connected の場合は、最初の `getValidAccessToken()` で `/v2/tenant` を強制取得して binding してください。

5. Severity: **Major**  
   カテゴリ: 整合性 / 運用  
   内容: refresh_token 540 日判定は現 active token の `add_date_time` に依存していますが、`persistToken()` は MF が `refresh_token` を返さない rotation OFF 時も新 token row を insert します。その場合、実際の refresh_token 発行日は古いままなのに `add_date_time` が更新され、残日数が過大評価されます。コードコメントの「早めに出るだけ」と逆で、警告が遅れる可能性があります。  
   推奨アクション: `refresh_token_issued_at` を token row に明示保持し、refresh_token が null で既存値流用なら発行時刻も旧値を引き継いでください。

6. Severity: **Major**  
   カテゴリ: セキュリティ  
   内容: `authorize_url` / `token_url` / `api_base_url` は admin が自由入力でき、tenant binding では改竄先が本物の MF かどうかを保証できません。悪意ある token endpoint に `client_id/client_secret` を Basic 送信する SSRF/credential exfiltration の経路になります。redirect URI は whitelist されていますが、OAuth endpoint 群は whitelist されていません。  
   推奨アクション: MF 公式 host の allowlist と `https` 強制を `upsertClient()` で実装してください。dev 例外が必要なら profile 条件付きで限定してください。

7. Severity: **Major**  
   カテゴリ: 整合性  
   内容: callback は `state consume → token exchange → tenant get → persist` の順で概ね正しいですが、client 設定の snapshot を HTTP 呼び出し中に保持します。その間に別 admin が client 設定や tenant binding を変更しても、最後の `persistTokenAndTenantInNewTx()` は id だけで再取得し、現在の client に対して古い token response を保存し得ます。  
   推奨アクション: persist 時に `clientId/tokenUrl/apiBaseUrl` などの snapshot 一致確認、または client row の optimistic lock/version を入れてください。

8. Severity: **Major**  
   カテゴリ: セキュリティ  
   内容: `OauthCryptoUtil` は `CryptoUtil` と同じ `Encryptors.delux()` で、IV/tag などは実質同等です。ただし PBKDF2 iteration count は Spring Security 実装依存でコード上から固定・検査できず、鍵の entropy も「16文字以上」しか見ていません。生成スクリプト利用時は十分でも、手入力の弱い passphrase を拒否できません。  
   推奨アクション: OAuth 鍵は base64 32 bytes など形式を検証し、KMS/SecretKeySpec 方式か、少なくとも iteration count を明示できる実装へ移行検討してください。

9. Severity: **Minor**  
   カテゴリ: 設計  
   内容: `OauthCryptoUtil` と `CryptoUtil` はほぼ完全に重複しています。現在は意図が読みやすい一方、salt validation、エラーメッセージ、将来の暗号方式更新が二重管理になります。片方だけ修正されるリスクがあります。  
   推奨アクション: 共通の `TextEncryptorFactory` または abstract base に validation と初期化を集約し、property prefix だけ分ける構成がよいです。

10. Severity: **Major**  
    カテゴリ: 整合性 / 設計  
    内容: backend の `MfScopeConstants.REQUIRED_SCOPES` と frontend の `MF_REQUIRED_SCOPES` はコメントで同期義務を示すだけです。将来どちらかだけ変更されると、UI の初期値と backend の警告判定が乖離します。これは scope 不足の検知機能そのものの信頼性を落とします。  
    推奨アクション: `/oauth/required-scopes` のように backend から配信するか、少なくともテストで frontend 定数と backend 定数の一致を検証してください。

11. Severity: **Minor**  
    カテゴリ: 運用 / UI  
    内容: `MfReAuthBanner` は `daysUntilReauth` の整数日だけで 60/30/14/7/0 判定しています。backend は `ChronoUnit.DAYS.between(issuedAt, Instant.now())` なので、期限当日の「残り数時間」でも `1` と表示され得ます。timezone 表示は `toLocaleString('ja-JP')` で端末依存です。  
    推奨アクション: backend で `refreshTokenExpiresAt` を返し、UI は日時と残時間ベースで「24時間未満」などを表示してください。

12. Severity: **Minor**  
    カテゴリ: 設計  
    内容: `MfTokenStatus` に接続状態、tenant binding、refresh_token 寿命、scope drift が詰め込まれています。現状は単一 status API として便利ですが、banner が増えるほど全 UI が同じ DTO の変更影響を受けます。`MfReAuthBanner` と `MfScopeBanner` は queryKey 共有で効率はよいものの、責務境界は曖昧です。  
    推奨アクション: API は維持しても、backend 内部では `TokenHealth`, `TenantBindingStatus`, `ScopeHealth` のように生成責務を分けると拡張しやすいです。

13. Severity: **Major**  
    カテゴリ: 運用  
    内容: MF 側で token が revoke された状態で `getStatus()` を呼んでも、DB の token 有無と期限だけを見るため connected=true のままです。実際の API 呼び出し時に 401 で初めて `MfReAuthRequiredException` になります。グローバル banner も revoke を検知できません。  
    推奨アクション: 明示的な「接続確認」操作、または低頻度の health check endpoint で 401 を status に反映する設計を追加してください。

14. Severity: **Minor**  
    カテゴリ: 運用 / DX  
    内容: dev でも OAuth 鍵 env 必須にした方針は安全側ですが、`V033` と `OauthCryptoUtil` の両方で fail-fast するため、初回開発者は起動不能になります。runbook はありますが、エラーは環境変数名中心で、プロファイル別の最短復旧手順までは自動化されていません。  
    推奨アクション: `./backend/scripts/gen-oauth-key.ps1 -SetUserEnv` のようなセットアップ補助、または dev 起動前チェック script を用意してください。

**補足評価**

- `MfApiClient.getTenant()` の呼び出し頻度は callback と refresh 後のみで、通常時の API 負荷は抑えられています。ただし未バインド旧 token だけは初回利用時に強制検証すべきです。
- `V033` の DML 自体は rollback 可能な形ですが、手動 commit と暗号化 version 不在が運用上の弱点です。
- AES-GCM 実装は `CryptoUtil` と等価ですが、「等価だから十分」とは言い切れず、鍵強度と KDF パラメータの明示性が不足しています。

**Opus 実装で見落としていた最重要 3 件**

1. `V033` の commit 後・Flyway 履歴前クラッシュで、新鍵済みデータを旧鍵で再 migration しようとして詰む点。  
2. rotation OFF の refresh_token で `add_date_time` を発行日扱いし、540日警告が遅れる点。  
3. `authorize_url/token_url/api_base_url` 改竄による client_secret 流出は tenant binding では防げない点。
