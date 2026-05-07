OpenAI Codex v0.128.0 (research preview)
--------
workdir: C:\project\odamitsu-data-hub
model: gpt-5.5
provider: openai
approval: never
sandbox: read-only
reasoning effort: none
reasoning summaries: none
session id: 019dfcef-e922-7ce0-92a8-05f56f1e0058
--------
user
commit 1b812b7
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content -Path C:/Users/kazuki/.agents/skills/using-superpowers/SKILL.md' in C:\project\odamitsu-data-hub
 succeeded in 1705ms:
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

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content -Path C:/Users/kazuki/.agents/skills/requesting-code-review/SKILL.md' in C:\project\odamitsu-data-hub
 succeeded in 1730ms:
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

2026-05-06T10:58:09.457336Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git status --short; git rev-parse --show-toplevel; git show --stat --oneline --decorate --no-renames 1b812b7'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git status --short; git rev-parse --show-toplevel; git show --stat --oneline --decorate --no-renames 1b812b7' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git status --short; git rev-parse --show-toplevel; git show --stat --oneline --decorate --no-renames 1b812b7'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git status --short' in C:\project\odamitsu-data-hub
 succeeded in 5218ms:
 M .claude/settings.local.json
?? claudedocs/codex-review-1b812b7-mf.md
?? claudedocs/codex-review-65e80f6-audit.md
?? claudedocs/codex-review-a0bbcac-finance.md
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
warning: unable to access 'C:\Users\kazuki/.config/git/ignore': Permission denied
warning: unable to access 'C:\Users\kazuki/.config/git/ignore': Permission denied

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git show --stat --oneline --decorate --no-renames 1b812b7' in C:\project\odamitsu-data-hub
 succeeded in 1377ms:
1b812b7 feat(mf): MF 連携セキュリティ強化 (tenant binding / OAuth 鍵分離 / scope 検知 / 寿命管理)
 backend/scripts/gen-oauth-key.ps1                  |  39 ++
 .../V033__reencrypt_mf_oauth_secrets.java          | 172 +++++++
 .../oda32/api/finance/MfIntegrationController.java | 506 ++-----------------
 .../api/finance/MfIntegrationDebugController.java  | 364 ++++++++++++++
 .../oda32/domain/model/finance/MMfOauthClient.java |  20 +-
 .../oda32/domain/model/finance/TMfOauthToken.java  |  15 +
 .../finance/TMfOauthStateRepository.java           |  15 +
 .../oda32/domain/service/finance/mf/MfAccount.java |   5 +-
 .../service/finance/mf/MfAccountSyncService.java   | 301 ++++++++++++
 .../service/finance/mf/MfAccountsResponse.java     |  11 +-
 .../domain/service/finance/mf/MfApiClient.java     | 360 +++++++++-----
 .../service/finance/mf/MfDebugApiClient.java       |  59 +++
 .../finance/mf/MfEnumTranslationService.java       | 225 +++++++++
 .../service/finance/mf/MfHttpClientConfig.java     |  38 ++
 .../oda32/domain/service/finance/mf/MfJournal.java |  39 ++
 .../service/finance/mf/MfJournalFetcher.java       |  67 ++-
 .../finance/mf/MfJournalReconcileService.java      | 547 +++++++++++++++++++++
 .../service/finance/mf/MfJournalsResponse.java     |  15 +
 .../service/finance/mf/MfOauthHostAllowlist.java   | 118 +++++
 .../domain/service/finance/mf/MfOauthService.java  | 314 +++++++++++-
 .../service/finance/mf/MfOauthStateStore.java      |  29 +-
 .../finance/mf/MfOpeningBalanceService.java        | 415 ++++++++++++++++
 .../finance/mf/MfOpeningJournalDetector.java       | 107 ++++
 .../service/finance/mf/MfPaymentAggregator.java    |  75 ++-
 .../service/finance/mf/MfScopeConstants.java       |  74 +++
 .../finance/mf/MfSupplierLedgerService.java        |  28 +-
 .../co/oda32/domain/service/finance/mf/MfTax.java  |  16 +
 .../domain/service/finance/mf/MfTaxesResponse.java |  16 +
 .../finance/mf/MfTenantBindingFailedException.java |  33 ++
 .../finance/mf/MfTenantMismatchException.java      |  36 ++
 .../service/finance/mf/MfTenantResponse.java       |  19 +
 .../domain/service/finance/mf/MfTokenResponse.java |  11 +-
 .../domain/service/finance/mf/MfTokenStatus.java   |  33 +-
 .../java/jp/co/oda32/util/OauthCryptoUtil.java     |  84 ++++
 .../src/main/resources/config/application-dev.yml  |  18 +-
 .../src/main/resources/config/application-prod.yml |   7 +-
 .../db/migration/V032__add_mf_tenant_binding.sql   |  29 ++
 .../V037__add_oauth_encryption_version.sql         |  50 ++
 .../V042__add_refresh_token_issued_at.sql          |  24 +
 .../finance/mf/MfAccountSyncServiceTest.java       | 252 ++++++++++
 .../finance/mf/MfEnumTranslationServiceTest.java   | 185 +++++++
 .../finance/mf/MfOauthHostAllowlistTest.java       | 198 ++++++++
 .../mf/MfOauthServiceForcedBindingTest.java        | 283 +++++++++++
 .../mf/MfOauthServiceRefreshTokenIssuedAtTest.java | 257 ++++++++++
 .../finance/mf/MfOpeningJournalDetectorTest.java   | 147 ++++++
 .../src/test/resources/config/application-test.yml |   7 +-
 frontend/components/common/MfReAuthBanner.tsx      | 104 ++++
 frontend/components/common/MfScopeBanner.tsx       |  69 +++
 .../pages/finance/mf-integration-callback.tsx      |  29 +-
 .../components/pages/finance/mf-integration.tsx    | 127 +++--
 frontend/types/mf-integration.ts                   |  59 ++-
 51 files changed, 5320 insertions(+), 731 deletions(-)
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content -Path C:/Users/kazuki/.agents/skills/spring-framework-patterns/SKILL.md' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content -Path C:/Users/kazuki/.agents/skills/typescript-react-reviewer/SKILL.md' in C:\project\odamitsu-data-hub
 succeeded in 2795ms:
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

 succeeded in 3900ms:
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
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git diff 1b812b7''^ 1b812b7 -- backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationDebugController.java backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java' in C:\project\odamitsu-data-hub
 succeeded in 3582ms:
diff --git a/backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java b/backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java
index 0e7aa18..b5d33a0 100644
--- a/backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java
+++ b/backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java
@@ -1,22 +1,17 @@
 package jp.co.oda32.api.finance;
 
-import com.fasterxml.jackson.databind.JsonNode;
 import jakarta.validation.Valid;
 import jp.co.oda32.domain.model.finance.MMfEnumTranslation;
 import jp.co.oda32.domain.model.finance.MMfOauthClient;
-import jp.co.oda32.domain.service.data.LoginUser;
 import jp.co.oda32.domain.service.finance.mf.MfAccountSyncService;
-import jp.co.oda32.domain.service.finance.mf.MfApiClient;
-import jp.co.oda32.domain.service.finance.mf.MfEnumTranslationService;
 import jp.co.oda32.domain.service.finance.mf.MfBalanceReconcileService;
+import jp.co.oda32.domain.service.finance.mf.MfEnumTranslationService;
 import jp.co.oda32.domain.service.finance.mf.MfJournalReconcileService;
 import jp.co.oda32.domain.service.finance.mf.MfOauthService;
 import jp.co.oda32.domain.service.finance.mf.MfReAuthRequiredException;
-import jp.co.oda32.domain.service.finance.mf.MfScopeInsufficientException;
+import jp.co.oda32.domain.service.finance.mf.MfTenantMismatchException;
 import jp.co.oda32.domain.service.finance.mf.MfTokenStatus;
-import jp.co.oda32.domain.service.smile.TSmilePaymentService;
-import jp.co.oda32.domain.model.smile.TSmilePayment;
-import org.springframework.core.env.Environment;
+import jp.co.oda32.domain.service.util.LoginUserUtil;
 import jp.co.oda32.dto.finance.MfEnumTranslationRequest;
 import jp.co.oda32.dto.finance.MfEnumTranslationResponse;
 import jp.co.oda32.dto.finance.MfOauthClientRequest;
@@ -26,7 +21,6 @@ import lombok.extern.slf4j.Slf4j;
 import org.springframework.http.HttpStatus;
 import org.springframework.http.ResponseEntity;
 import org.springframework.security.access.prepost.PreAuthorize;
-import org.springframework.security.core.annotation.AuthenticationPrincipal;
 import org.springframework.web.bind.annotation.*;
 
 import java.util.List;
@@ -37,23 +31,24 @@ import java.util.Map;
  * <p>
  * 設計書: claudedocs/design-mf-integration-status.md
  *
+ * <p>SF-12: ログインユーザ取得は {@link LoginUserUtil#getLoginUserInfo()} に統一。
+ * <p>SF-13: 診断用 endpoint は {@link MfIntegrationDebugController} に分離。
+ * <p>SF-15: 401/403/422 のレスポンス組み立ては {@link FinanceExceptionHandler} に集約。
+ *
  * @since 2026/04/20
  */
 @Slf4j
 @RestController
 @RequestMapping("/api/v1/finance/mf-integration")
-@PreAuthorize("hasRole('ADMIN')")
+@PreAuthorize("@loginUserSecurityBean.isAdmin()")
 @RequiredArgsConstructor
 public class MfIntegrationController {
 
     private final MfOauthService mfOauthService;
-    private final MfApiClient mfApiClient;
-    private final Environment environment;
     private final MfEnumTranslationService enumTranslationService;
     private final MfAccountSyncService accountSyncService;
     private final MfJournalReconcileService reconcileService;
     private final MfBalanceReconcileService balanceReconcileService;
-    private final jp.co.oda32.domain.repository.smile.TSmilePaymentRepository tSmilePaymentRepository;
 
     // ---- クライアント設定 (admin 登録用) ----
 
@@ -67,8 +62,7 @@ public class MfIntegrationController {
 
     @PutMapping("/oauth/client")
     public ResponseEntity<MfOauthClientResponse> upsertClient(
-            @Valid @RequestBody MfOauthClientRequest request,
-            @AuthenticationPrincipal LoginUser user) {
+            @Valid @RequestBody MfOauthClientRequest request) throws Exception {
         MMfOauthClient entity = MMfOauthClient.builder()
                 .clientId(request.getClientId())
                 .redirectUri(request.getRedirectUri())
@@ -77,7 +71,7 @@ public class MfIntegrationController {
                 .tokenUrl(request.getTokenUrl())
                 .apiBaseUrl(request.getApiBaseUrl())
                 .build();
-        Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
+        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
         MMfOauthClient saved = mfOauthService.upsertClient(entity, request.getClientSecret(), userNo);
         return ResponseEntity.ok(MfOauthClientResponse.from(saved));
     }
@@ -95,31 +89,28 @@ public class MfIntegrationController {
      * フロントは受け取った URL を新タブで開く。
      */
     @GetMapping("/oauth/authorize-url")
-    public ResponseEntity<?> authorizeUrl(@AuthenticationPrincipal LoginUser user) {
-        try {
-            Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
-            MfOauthService.AuthorizeUrl issued = mfOauthService.buildAuthorizeUrl(userNo);
-            return ResponseEntity.ok(Map.of("url", issued.url(), "state", issued.state()));
-        } catch (IllegalStateException e) {
-            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
-                    .body(Map.of("message", e.getMessage()));
-        }
+    public ResponseEntity<?> authorizeUrl() throws Exception {
+        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
+        MfOauthService.AuthorizeUrl issued = mfOauthService.buildAuthorizeUrl(userNo);
+        return ResponseEntity.ok(Map.of("url", issued.url(), "state", issued.state()));
     }
 
     /**
      * MF からのリダイレクト後、フロントの callback ページが code/state を POST してくる。
      * state + code_verifier (PKCE) が DB ストアで検証できれば token 交換を行う。
+     *
+     * <p>SF-04: token endpoint エラー時のメッセージは汎用化 (詳細は server log のみ)。
+     * このため {@link MfReAuthRequiredException} だけは {@link FinanceExceptionHandler} に
+     * 委譲せずローカルで処理する (専用文言が必要)。
      */
     @PostMapping("/oauth/callback")
-    public ResponseEntity<?> callback(
-            @RequestBody Map<String, String> body,
-            @AuthenticationPrincipal LoginUser user) {
+    public ResponseEntity<?> callback(@RequestBody Map<String, String> body) throws Exception {
         String code = body.get("code");
         String state = body.get("state");
         if (code == null || code.isBlank() || state == null || state.isBlank()) {
             return ResponseEntity.badRequest().body(Map.of("message", "code/state が必要です"));
         }
-        Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
+        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
         try {
             mfOauthService.handleCallback(code, state, userNo);
             return ResponseEntity.ok(mfOauthService.getStatus());
@@ -128,18 +119,23 @@ public class MfIntegrationController {
             return ResponseEntity.status(HttpStatus.FORBIDDEN)
                     .body(Map.of("message", "state が不正または有効期限切れです。再度認可をやり直してください。"));
         } catch (MfReAuthRequiredException e) {
+            // SF-04: ユーザー向けメッセージは汎用化、詳細 (token endpoint body) はサーバーログのみ
+            log.warn("MF OAuth callback: 再認証が必要 userNo={}", userNo, e);
             return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
-                    .body(Map.of("message", e.getMessage()));
-        } catch (IllegalStateException e) {
-            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
-                    .body(Map.of("message", e.getMessage()));
+                    .body(Map.of("message", "再認証が必要です。詳細は管理者ログを参照してください"));
+        } catch (MfTenantMismatchException e) {
+            // P1-01: 別会社 MF を認可した場合、運用者に明示的なメッセージを返す。
+            log.error("MF OAuth callback: tenant binding 不一致 userNo={} bound={} observed={}",
+                    userNo, e.getBoundTenantId(), e.getObservedTenantId());
+            return ResponseEntity.status(HttpStatus.CONFLICT)
+                    .body(Map.of("message", e.getMessage(), "code", "MF_TENANT_MISMATCH"));
         }
     }
 
     /** 接続を切断（DB トークンを論理削除）。 */
     @PostMapping("/oauth/revoke")
-    public ResponseEntity<MfTokenStatus> revoke(@AuthenticationPrincipal LoginUser user) {
-        Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
+    public ResponseEntity<MfTokenStatus> revoke() throws Exception {
+        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
         mfOauthService.revoke(userNo);
         return ResponseEntity.ok(mfOauthService.getStatus());
     }
@@ -156,9 +152,8 @@ public class MfIntegrationController {
     /** 渡されたリストで翻訳辞書を洗い替え。 */
     @PutMapping("/enum-translations")
     public ResponseEntity<List<MfEnumTranslationResponse>> replaceTranslations(
-            @Valid @RequestBody List<MfEnumTranslationRequest> requests,
-            @AuthenticationPrincipal LoginUser user) {
-        Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
+            @Valid @RequestBody List<MfEnumTranslationRequest> requests) throws Exception {
+        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
         List<MMfEnumTranslation> entities = requests.stream()
                 .map(r -> MMfEnumTranslation.builder()
                         .enumKind(r.getEnumKind())
@@ -172,21 +167,13 @@ public class MfIntegrationController {
 
     /** MF API /accounts と既存 mf_account_master を突合して、英→日マッピングを自動学習。 */
     @PostMapping("/enum-translations/auto-seed")
-    public ResponseEntity<?> autoSeedTranslations(@AuthenticationPrincipal LoginUser user) {
-        try {
-            Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
-            MfEnumTranslationService.AutoSeedResult result = enumTranslationService.autoSeed(userNo);
-            return ResponseEntity.ok(Map.of(
-                    "added", result.added(),
-                    "unresolved", result.unresolved()
-            ));
-        } catch (MfReAuthRequiredException e) {
-            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
-                    .body(Map.of("message", e.getMessage()));
-        } catch (IllegalStateException e) {
-            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
-                    .body(Map.of("message", e.getMessage()));
-        }
+    public ResponseEntity<?> autoSeedTranslations() throws Exception {
+        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
+        MfEnumTranslationService.AutoSeedResult result = enumTranslationService.autoSeed(userNo);
+        return ResponseEntity.ok(Map.of(
+                "added", result.added(),
+                "unresolved", result.unresolved()
+        ));
     }
 
     // ---- 仕訳突合 ----
@@ -200,14 +187,8 @@ public class MfIntegrationController {
             @RequestParam("transactionMonth")
             @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
             java.time.LocalDate transactionMonth) {
-        try {
-            MfJournalReconcileService.ReconcileReport report = reconcileService.reconcile(transactionMonth);
-            return ResponseEntity.ok(report);
-        } catch (MfReAuthRequiredException e) {
-            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
-        } catch (IllegalStateException e) {
-            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", e.getMessage()));
-        }
+        MfJournalReconcileService.ReconcileReport report = reconcileService.reconcile(transactionMonth);
+        return ResponseEntity.ok(report);
     }
 
     /**
@@ -219,410 +200,23 @@ public class MfIntegrationController {
             @RequestParam("period")
             @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
             java.time.LocalDate period) {
-        try {
-            MfBalanceReconcileService.BalanceReconcileReport report =
-                    balanceReconcileService.reconcile(period);
-            return ResponseEntity.ok(report);
-        } catch (MfReAuthRequiredException e) {
-            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
-        } catch (MfScopeInsufficientException e) {
-            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
-                    "message", e.getMessage(),
-                    "requiredScope", e.getRequiredScope()));
-        } catch (IllegalStateException e) {
-            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", e.getMessage()));
-        }
+        MfBalanceReconcileService.BalanceReconcileReport report =
+                balanceReconcileService.reconcile(period);
+        return ResponseEntity.ok(report);
     }
 
     // ---- 勘定科目同期 (mf_account_master 洗い替え) ----
 
     @GetMapping("/account-sync/preview")
     public ResponseEntity<?> previewAccountSync() {
-        try {
-            MfAccountSyncService.SyncResult result = accountSyncService.preview();
-            return ResponseEntity.ok(result);
-        } catch (MfReAuthRequiredException e) {
-            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
-        } catch (IllegalStateException e) {
-            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", e.getMessage()));
-        }
+        MfAccountSyncService.SyncResult result = accountSyncService.preview();
+        return ResponseEntity.ok(result);
     }
 
     @PostMapping("/account-sync/apply")
-    public ResponseEntity<?> applyAccountSync(@AuthenticationPrincipal LoginUser user) {
-        try {
-            Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
-            MfAccountSyncService.SyncResult result = accountSyncService.apply(userNo);
-            return ResponseEntity.ok(result);
-        } catch (MfReAuthRequiredException e) {
-            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
-        } catch (IllegalStateException e) {
-            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", e.getMessage()));
-        }
-    }
-
-    // ---- 診断用（勘定科目同期実装前のレスポンス shape 調査） ----
-    // dev/test プロファイルのみ動作。prod では 404 を返す (B-W4)。
-
-    @GetMapping("/debug/accounts-raw")
-    public ResponseEntity<?> debugAccountsRaw() {
-        if (!isDevProfile()) return notFound();
-        return fetchRawWithFirstN("/api/v3/accounts", 3);
-    }
-
-    /**
-     * t_smile_payment を voucher_date 範囲で集計する診断 endpoint (Phase B'' 遡及充填の前準備)。
-     * 月×仕入先コード単位で payment_amount を合計して返す。
-     * dev プロファイルのみ。
-     */
-    @GetMapping("/debug/smile-payment-monthly")
-    public ResponseEntity<?> debugSmilePaymentMonthly(
-            @RequestParam("fromDate")
-            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
-            java.time.LocalDate fromDate,
-            @RequestParam("toDate")
-            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
-            java.time.LocalDate toDate) {
-        if (!isDevProfile()) return notFound();
-        List<TSmilePayment> list = tSmilePaymentRepository.findByVoucherDateBetween(fromDate, toDate);
-        // 月ごとに集計 (yyyy-MM) + supplier_code 単位
-        java.util.Map<String, java.math.BigDecimal> byMonthTotal = new java.util.TreeMap<>();
-        java.util.Map<String, Integer> byMonthCount = new java.util.TreeMap<>();
-        java.util.Map<String, java.util.Map<String, java.math.BigDecimal>> byMonthSupplier = new java.util.TreeMap<>();
-        for (TSmilePayment p : list) {
-            if (p.getVoucherDate() == null) continue;
-            String ym = p.getVoucherDate().toString().substring(0, 7);
-            byMonthTotal.merge(ym,
-                    p.getPaymentAmount() != null ? p.getPaymentAmount() : java.math.BigDecimal.ZERO,
-                    java.math.BigDecimal::add);
-            byMonthCount.merge(ym, 1, Integer::sum);
-            byMonthSupplier.computeIfAbsent(ym, k -> new java.util.HashMap<>())
-                    .merge(p.getSupplierCode() != null ? p.getSupplierCode() : "",
-                            p.getPaymentAmount() != null ? p.getPaymentAmount() : java.math.BigDecimal.ZERO,
-                            java.math.BigDecimal::add);
-        }
-        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
-        result.put("totalRows", list.size());
-        result.put("byMonth", byMonthTotal.entrySet().stream()
-                .map(e -> {
-                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
-                    m.put("month", e.getKey());
-                    m.put("count", byMonthCount.get(e.getKey()));
-                    m.put("totalAmount", e.getValue());
-                    m.put("supplierCount", byMonthSupplier.get(e.getKey()).size());
-                    return m;
-                }).toList());
+    public ResponseEntity<?> applyAccountSync() throws Exception {
+        Integer userNo = LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
+        MfAccountSyncService.SyncResult result = accountSyncService.apply(userNo);
         return ResponseEntity.ok(result);
     }
-
-    /**
-     * 貸借対照表 試算表 API の生レスポンスを返す（Phase 0 スパイク用）。
-     * 関連設計書: claudedocs/design-supplier-partner-ledger-balance.md §3.0
-     *
-     * <p>MF 側の必須 query は仕様未確定のため任意 param を透過で渡す。
-     * 代表的な呼び方:
-     * <pre>
-     *   GET /debug/trial-balance-raw?from=2026-02-21&to=2026-03-20
-     *   GET /debug/trial-balance-raw?period=2026-03-20
-     * </pre>
-     * 403 は scope 不足 (mfc/accounting/report.read 未取得) を意味するため
-     * UI 再認可を促すメッセージで返す。
-     */
-    @GetMapping("/debug/trial-balance-raw")
-    public ResponseEntity<?> debugTrialBalanceRaw(@RequestParam Map<String, String> params) {
-        if (!isDevProfile()) return notFound();
-        String query = params.entrySet().stream()
-                .map(e -> e.getKey() + "=" + java.net.URLEncoder.encode(
-                        e.getValue(), java.nio.charset.StandardCharsets.UTF_8))
-                .collect(java.util.stream.Collectors.joining("&"));
-        String path = "/api/v3/reports/trial_balance_bs" + (query.isEmpty() ? "" : "?" + query);
-        try {
-            MMfOauthClient client = mfOauthService.findActiveClient()
-                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
-            String accessToken = mfOauthService.getValidAccessToken();
-            JsonNode raw = mfApiClient.getRaw(client, accessToken, path);
-            return ResponseEntity.ok(raw);
-        } catch (MfReAuthRequiredException e) {
-            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
-                    .body(Map.of("message", e.getMessage()));
-        } catch (org.springframework.web.client.HttpClientErrorException e) {
-            if (e.getStatusCode().value() == 403) {
-                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
-                        "message", "scope 不足です。frontend/types/mf-integration.ts に"
-                                + " mfc/accounting/report.read を追加済みの状態で、"
-                                + "/finance/mf-integration 画面から「再認証」してください。",
-                        "mfBody", e.getResponseBodyAsString()));
-            }
-            return ResponseEntity.status(e.getStatusCode())
-                    .body(Map.of("message", "MF API error", "mfBody", e.getResponseBodyAsString()));
-        } catch (IllegalStateException e) {
-            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
-                    .body(Map.of("message", e.getMessage()));
-        }
-    }
-
-    @GetMapping("/debug/taxes-raw")
-    public ResponseEntity<?> debugTaxesRaw() {
-        if (!isDevProfile()) return notFound();
-        return fetchRawWithFirstN("/api/v3/taxes", 5);
-    }
-
-    /**
-     * 指定取引月の MF 仕訳を最初 3 件だけ返す（shape 調査用）。
-     * 例: /debug/journals-raw?transactionMonth=2026-03-20
-     */
-    @GetMapping("/debug/journals-raw")
-    public ResponseEntity<?> debugJournalsRaw(
-            @RequestParam("transactionMonth") String transactionMonth) {
-        if (!isDevProfile()) return notFound();
-        // start_date=end_date=transactionMonth で 1 日分のみ取得
-        String path = "/api/v3/journals?start_date=" + transactionMonth
-                + "&end_date=" + transactionMonth
-                + "&per_page=3";
-        return fetchRawWithFirstN(path, 3);
-    }
-
-    /**
-     * 指定勘定科目の全 branch を期間内で列挙し、journal 単位で credit/debit の釣り合いを判定する。
-     * 不一致 journal が「ペアになっていない仕訳」を含む journal。
-     * 例: /debug/account-journals?accountName=仕入資金複合&startDate=2026-02-21&endDate=2026-03-20
-     */
-    @GetMapping("/debug/account-journals")
-    public ResponseEntity<?> debugAccountJournals(
-            @RequestParam("accountName") String accountName,
-            @RequestParam("startDate") String startDate,
-            @RequestParam("endDate") String endDate) {
-        if (!isDevProfile()) return notFound();
-        try {
-            MMfOauthClient client = mfOauthService.findActiveClient()
-                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
-            String accessToken = mfOauthService.getValidAccessToken();
-
-            java.util.List<java.util.Map<String, Object>> balancedJournals = new java.util.ArrayList<>();
-            java.util.List<java.util.Map<String, Object>> unbalancedJournals = new java.util.ArrayList<>();
-
-            int page = 1;
-            final int perPage = 1000;
-            while (true) {
-                var res = mfApiClient.listJournals(client, accessToken, startDate, endDate, page, perPage);
-                var journals = res.items();
-                if (journals.isEmpty()) break;
-                for (var j : journals) {
-                    if (j.branches() == null) continue;
-                    java.math.BigDecimal deb = java.math.BigDecimal.ZERO;
-                    java.math.BigDecimal cre = java.math.BigDecimal.ZERO;
-                    java.util.List<java.util.Map<String, Object>> hitBranches = new java.util.ArrayList<>();
-                    for (var b : j.branches()) {
-                        var d = b.debitor();
-                        var c = b.creditor();
-                        boolean hit = false;
-                        if (d != null && accountName.equals(d.accountName())) {
-                            if (d.value() != null) deb = deb.add(d.value());
-                            hit = true;
-                        }
-                        if (c != null && accountName.equals(c.accountName())) {
-                            if (c.value() != null) cre = cre.add(c.value());
-                            hit = true;
-                        }
-                        if (hit) {
-                            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
-                            row.put("debitAccount", d != null ? d.accountName() : null);
-                            row.put("debitSub", d != null ? d.subAccountName() : null);
-                            row.put("debitValue", d != null ? d.value() : null);
-                            row.put("creditAccount", c != null ? c.accountName() : null);
-                            row.put("creditSub", c != null ? c.subAccountName() : null);
-                            row.put("creditValue", c != null ? c.value() : null);
-                            row.put("remark", b.remark());
-                            hitBranches.add(row);
-                        }
-                    }
-                    if (hitBranches.isEmpty()) continue;
-                    java.math.BigDecimal diff = cre.subtract(deb);
-                    java.util.Map<String, Object> jEntry = new java.util.LinkedHashMap<>();
-                    jEntry.put("journalNumber", j.number());
-                    jEntry.put("transactionDate", j.transactionDate() != null ? j.transactionDate().toString() : null);
-                    jEntry.put("memo", j.memo());
-                    jEntry.put("debitSum", deb);
-                    jEntry.put("creditSum", cre);
-                    jEntry.put("diff", diff);
-                    jEntry.put("branches", hitBranches);
-                    if (diff.signum() == 0) balancedJournals.add(jEntry);
-                    else unbalancedJournals.add(jEntry);
-                }
-                if (journals.size() < perPage) break;
-                page++;
-            }
-
-            return ResponseEntity.ok(java.util.Map.of(
-                    "accountName", accountName,
-                    "startDate", startDate,
-                    "endDate", endDate,
-                    "balancedJournalCount", balancedJournals.size(),
-                    "unbalancedJournalCount", unbalancedJournals.size(),
-                    "unbalancedJournals", unbalancedJournals,
-                    "balancedSamples", balancedJournals.stream().limit(3).toList()
-            ));
-        } catch (MfReAuthRequiredException e) {
-            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
-                    .body(Map.of("message", e.getMessage()));
-        } catch (IllegalStateException e) {
-            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
-                    .body(Map.of("message", e.getMessage()));
-        }
-    }
-
-    /**
-     * 指定勘定科目の残高推移を計算する（/journals 全件走査）。
-     * 例: /debug/account-trend?accountName=仕入資金複合&startDate=2025-10-01&endDate=2026-02-20
-     */
-    @GetMapping("/debug/account-trend")
-    public ResponseEntity<?> debugAccountTrend(
-            @RequestParam("accountName") String accountName,
-            @RequestParam("startDate") String startDate,
-            @RequestParam("endDate") String endDate) {
-        if (!isDevProfile()) return notFound();
-        try {
-            MMfOauthClient client = mfOauthService.findActiveClient()
-                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
-            String accessToken = mfOauthService.getValidAccessToken();
-
-            java.math.BigDecimal totalDebit = java.math.BigDecimal.ZERO;
-            java.math.BigDecimal totalCredit = java.math.BigDecimal.ZERO;
-            java.util.TreeMap<java.time.LocalDate, java.math.BigDecimal[]> daily = new java.util.TreeMap<>();
-            int hitCount = 0;
-
-            int page = 1;
-            final int perPage = 1000;
-            while (true) {
-                jp.co.oda32.domain.service.finance.mf.MfJournalsResponse res =
-                        mfApiClient.listJournals(client, accessToken, startDate, endDate, page, perPage);
-                java.util.List<jp.co.oda32.domain.service.finance.mf.MfJournal> journals = res.items();
-                if (journals.isEmpty()) break;
-                for (jp.co.oda32.domain.service.finance.mf.MfJournal j : journals) {
-                    if (j.branches() == null) continue;
-                    for (jp.co.oda32.domain.service.finance.mf.MfJournal.MfBranch b : j.branches()) {
-                        var deb = b.debitor();
-                        var cre = b.creditor();
-                        boolean hit = false;
-                        java.math.BigDecimal dv = java.math.BigDecimal.ZERO;
-                        java.math.BigDecimal cv = java.math.BigDecimal.ZERO;
-                        if (deb != null && accountName.equals(deb.accountName())) {
-                            if (deb.value() != null) {
-                                dv = deb.value();
-                                totalDebit = totalDebit.add(dv);
-                            }
-                            hit = true;
-                        }
-                        if (cre != null && accountName.equals(cre.accountName())) {
-                            if (cre.value() != null) {
-                                cv = cre.value();
-                                totalCredit = totalCredit.add(cv);
-                            }
-                            hit = true;
-                        }
-                        if (hit) {
-                            hitCount++;
-                            java.time.LocalDate d = j.transactionDate();
-                            if (d != null) {
-                                java.math.BigDecimal[] arr = daily.computeIfAbsent(d,
-                                        k -> new java.math.BigDecimal[]{java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO});
-                                arr[0] = arr[0].add(dv);
-                                arr[1] = arr[1].add(cv);
-                            }
-                        }
-                    }
-                }
-                if (journals.size() < perPage) break;
-                page++;
-            }
-
-            // 累積残高推移（負債側として credit - debit 方式で計算）
-            java.util.List<java.util.Map<String, Object>> trend = new java.util.ArrayList<>();
-            java.math.BigDecimal running = java.math.BigDecimal.ZERO;
-            for (var e : daily.entrySet()) {
-                java.math.BigDecimal d = e.getValue()[0];
-                java.math.BigDecimal c = e.getValue()[1];
-                running = running.add(c).subtract(d);
-                trend.add(java.util.Map.of(
-                        "date", e.getKey().toString(),
-                        "debit", d,
-                        "credit", c,
-                        "runningBalance", running
-                ));
-            }
-
-            return ResponseEntity.ok(java.util.Map.of(
-                    "accountName", accountName,
-                    "startDate", startDate,
-                    "endDate", endDate,
-                    "hitBranchCount", hitCount,
-                    "totalDebit", totalDebit,
-                    "totalCredit", totalCredit,
-                    "balanceAsLiability", totalCredit.subtract(totalDebit),
-                    "balanceAsAsset", totalDebit.subtract(totalCredit),
-                    "trend", trend
-            ));
-        } catch (MfReAuthRequiredException e) {
-            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
-                    .body(Map.of("message", e.getMessage()));
-        } catch (IllegalStateException e) {
-            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
-                    .body(Map.of("message", e.getMessage()));
-        }
-    }
-
-    private boolean isDevProfile() {
-        for (String p : environment.getActiveProfiles()) {
-            if ("dev".equals(p) || "test".equals(p)) return true;
-        }
-        return false;
-    }
-
-    private static ResponseEntity<?> notFound() {
-        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Not found"));
-    }
-
-    private ResponseEntity<?> fetchRawWithFirstN(String path, int sampleSize) {
-        try {
-            MMfOauthClient client = mfOauthService.findActiveClient()
-                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
-            String accessToken = mfOauthService.getValidAccessToken();
-            JsonNode raw = mfApiClient.getRaw(client, accessToken, path);
-            return ResponseEntity.ok(trimToSample(raw, sampleSize));
-        } catch (MfReAuthRequiredException e) {
-            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
-                    .body(Map.of("message", e.getMessage()));
-        } catch (IllegalStateException e) {
-            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
-                    .body(Map.of("message", e.getMessage()));
-        }
-    }
-
-    /**
-     * レスポンスが配列 or {data:[...]} / {accounts:[...]} 等のいずれでも最初 N 件だけ残す。
-     * shape 調査が目的なので wrapper と中身の両方が見えるように。
-     */
-    private JsonNode trimToSample(JsonNode raw, int n) {
-        if (raw == null) return null;
-        if (raw.isArray()) {
-            com.fasterxml.jackson.databind.node.ArrayNode arr =
-                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
-            for (int i = 0; i < Math.min(n, raw.size()); i++) arr.add(raw.get(i));
-            return arr;
-        }
-        if (raw.isObject()) {
-            com.fasterxml.jackson.databind.node.ObjectNode copy = raw.deepCopy();
-            raw.fieldNames().forEachRemaining(field -> {
-                JsonNode v = copy.get(field);
-                if (v != null && v.isArray()) {
-                    com.fasterxml.jackson.databind.node.ArrayNode arr =
-                            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
-                    for (int i = 0; i < Math.min(n, v.size()); i++) arr.add(v.get(i));
-                    copy.set(field, arr);
-                }
-            });
-            return copy;
-        }
-        return raw;
-    }
 }
diff --git a/backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationDebugController.java b/backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationDebugController.java
new file mode 100644
index 0000000..a3a4490
--- /dev/null
+++ b/backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationDebugController.java
@@ -0,0 +1,364 @@
+package jp.co.oda32.api.finance;
+
+import com.fasterxml.jackson.databind.JsonNode;
+import jp.co.oda32.domain.model.finance.MMfOauthClient;
+import jp.co.oda32.domain.model.smile.TSmilePayment;
+import jp.co.oda32.domain.repository.smile.TSmilePaymentRepository;
+import jp.co.oda32.domain.service.finance.mf.MfApiClient;
+import jp.co.oda32.domain.service.finance.mf.MfDebugApiClient;
+import jp.co.oda32.domain.service.finance.mf.MfJournal;
+import jp.co.oda32.domain.service.finance.mf.MfJournalsResponse;
+import jp.co.oda32.domain.service.finance.mf.MfOauthService;
+import jp.co.oda32.domain.service.finance.mf.MfReAuthRequiredException;
+import lombok.RequiredArgsConstructor;
+import lombok.extern.slf4j.Slf4j;
+import org.springframework.context.annotation.Profile;
+import org.springframework.http.HttpStatus;
+import org.springframework.http.ResponseEntity;
+import org.springframework.security.access.prepost.PreAuthorize;
+import org.springframework.web.bind.annotation.*;
+
+import java.math.BigDecimal;
+import java.time.LocalDate;
+import java.util.ArrayList;
+import java.util.HashMap;
+import java.util.LinkedHashMap;
+import java.util.List;
+import java.util.Map;
+import java.util.TreeMap;
+
+/**
+ * SF-13: MF 連携の診断用 endpoint 群 (dev/test プロファイル限定)。
+ * <p>
+ * 旧 {@link MfIntegrationController} の {@code /debug/*} 群を移動。
+ * {@code @Profile} で dev/test のみ Bean 化されるため prod では Spring が認識せず 404 になる
+ * (旧実装の各 endpoint 内 {@code isDevProfile()} ガードは不要に)。
+ *
+ * @since 2026-05-04 (SF-13)
+ */
+@Slf4j
+@Profile({"dev", "test"})
+@RestController
+@RequestMapping("/api/v1/finance/mf-integration/debug")
+@PreAuthorize("@loginUserSecurityBean.isAdmin()")
+@RequiredArgsConstructor
+public class MfIntegrationDebugController {
+
+    private final MfOauthService mfOauthService;
+    private final MfApiClient mfApiClient;
+    private final MfDebugApiClient mfDebugApiClient;
+    private final TSmilePaymentRepository tSmilePaymentRepository;
+
+    @GetMapping("/accounts-raw")
+    public ResponseEntity<?> debugAccountsRaw() {
+        return fetchRawWithFirstN("/api/v3/accounts", 3);
+    }
+
+    /**
+     * t_smile_payment を voucher_date 範囲で集計する診断 endpoint (Phase B'' 遡及充填の前準備)。
+     * 月×仕入先コード単位で payment_amount を合計して返す。
+     */
+    @GetMapping("/smile-payment-monthly")
+    public ResponseEntity<?> debugSmilePaymentMonthly(
+            @RequestParam("fromDate")
+            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
+            LocalDate fromDate,
+            @RequestParam("toDate")
+            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
+            LocalDate toDate) {
+        List<TSmilePayment> list = tSmilePaymentRepository.findByVoucherDateBetween(fromDate, toDate);
+        Map<String, BigDecimal> byMonthTotal = new TreeMap<>();
+        Map<String, Integer> byMonthCount = new TreeMap<>();
+        Map<String, Map<String, BigDecimal>> byMonthSupplier = new TreeMap<>();
+        for (TSmilePayment p : list) {
+            if (p.getVoucherDate() == null) continue;
+            String ym = p.getVoucherDate().toString().substring(0, 7);
+            byMonthTotal.merge(ym,
+                    p.getPaymentAmount() != null ? p.getPaymentAmount() : BigDecimal.ZERO,
+                    BigDecimal::add);
+            byMonthCount.merge(ym, 1, Integer::sum);
+            byMonthSupplier.computeIfAbsent(ym, k -> new HashMap<>())
+                    .merge(p.getSupplierCode() != null ? p.getSupplierCode() : "",
+                            p.getPaymentAmount() != null ? p.getPaymentAmount() : BigDecimal.ZERO,
+                            BigDecimal::add);
+        }
+        Map<String, Object> result = new LinkedHashMap<>();
+        result.put("totalRows", list.size());
+        result.put("byMonth", byMonthTotal.entrySet().stream()
+                .map(e -> {
+                    Map<String, Object> m = new LinkedHashMap<>();
+                    m.put("month", e.getKey());
+                    m.put("count", byMonthCount.get(e.getKey()));
+                    m.put("totalAmount", e.getValue());
+                    m.put("supplierCount", byMonthSupplier.get(e.getKey()).size());
+                    return m;
+                }).toList());
+        return ResponseEntity.ok(result);
+    }
+
+    /**
+     * 貸借対照表 試算表 API の生レスポンスを返す（Phase 0 スパイク用）。
+     * 関連設計書: claudedocs/design-supplier-partner-ledger-balance.md §3.0
+     *
+     * <p>MF 側の必須 query は仕様未確定のため任意 param を透過で渡す。
+     * 代表的な呼び方:
+     * <pre>
+     *   GET /debug/trial-balance-raw?from=2026-02-21&to=2026-03-20
+     *   GET /debug/trial-balance-raw?period=2026-03-20
+     * </pre>
+     * 403 は scope 不足 (mfc/accounting/report.read 未取得) を意味するため
+     * UI 再認可を促すメッセージで返す。
+     */
+    @GetMapping("/trial-balance-raw")
+    public ResponseEntity<?> debugTrialBalanceRaw(@RequestParam Map<String, String> params) {
+        String query = params.entrySet().stream()
+                .map(e -> e.getKey() + "=" + java.net.URLEncoder.encode(
+                        e.getValue(), java.nio.charset.StandardCharsets.UTF_8))
+                .collect(java.util.stream.Collectors.joining("&"));
+        String path = "/api/v3/reports/trial_balance_bs" + (query.isEmpty() ? "" : "?" + query);
+        try {
+            MMfOauthClient client = mfOauthService.findActiveClient()
+                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
+            String accessToken = mfOauthService.getValidAccessToken();
+            JsonNode raw = mfDebugApiClient.getRaw(client, accessToken, path);
+            return ResponseEntity.ok(raw);
+        } catch (org.springframework.web.client.HttpClientErrorException e) {
+            if (e.getStatusCode().value() == 403) {
+                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
+                        "message", "scope 不足です。frontend/types/mf-integration.ts に"
+                                + " mfc/accounting/report.read を追加済みの状態で、"
+                                + "/finance/mf-integration 画面から「再認証」してください。"));
+            }
+            return ResponseEntity.status(e.getStatusCode())
+                    .body(Map.of("message", "MF API error"));
+        }
+    }
+
+    @GetMapping("/taxes-raw")
+    public ResponseEntity<?> debugTaxesRaw() {
+        return fetchRawWithFirstN("/api/v3/taxes", 5);
+    }
+
+    /**
+     * 指定取引月の MF 仕訳を最初 3 件だけ返す（shape 調査用）。
+     * 例: /debug/journals-raw?transactionMonth=2026-03-20
+     */
+    @GetMapping("/journals-raw")
+    public ResponseEntity<?> debugJournalsRaw(
+            @RequestParam("transactionMonth") String transactionMonth) {
+        String path = "/api/v3/journals?start_date=" + transactionMonth
+                + "&end_date=" + transactionMonth
+                + "&per_page=3";
+        return fetchRawWithFirstN(path, 3);
+    }
+
+    /**
+     * 指定勘定科目の全 branch を期間内で列挙し、journal 単位で credit/debit の釣り合いを判定する。
+     * 不一致 journal が「ペアになっていない仕訳」を含む journal。
+     * 例: /debug/account-journals?accountName=仕入資金複合&startDate=2026-02-21&endDate=2026-03-20
+     */
+    @GetMapping("/account-journals")
+    public ResponseEntity<?> debugAccountJournals(
+            @RequestParam("accountName") String accountName,
+            @RequestParam("startDate") String startDate,
+            @RequestParam("endDate") String endDate) {
+        MMfOauthClient client = mfOauthService.findActiveClient()
+                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
+        String accessToken = mfOauthService.getValidAccessToken();
+
+        List<Map<String, Object>> balancedJournals = new ArrayList<>();
+        List<Map<String, Object>> unbalancedJournals = new ArrayList<>();
+
+        int page = 1;
+        final int perPage = 1000;
+        while (true) {
+            MfJournalsResponse res = mfApiClient.listJournals(client, accessToken, startDate, endDate, page, perPage);
+            List<MfJournal> journals = res.items();
+            if (journals.isEmpty()) break;
+            for (MfJournal j : journals) {
+                if (j.branches() == null) continue;
+                BigDecimal deb = BigDecimal.ZERO;
+                BigDecimal cre = BigDecimal.ZERO;
+                List<Map<String, Object>> hitBranches = new ArrayList<>();
+                for (MfJournal.MfBranch b : j.branches()) {
+                    MfJournal.MfSide d = b.debitor();
+                    MfJournal.MfSide c = b.creditor();
+                    boolean hit = false;
+                    if (d != null && accountName.equals(d.accountName())) {
+                        if (d.value() != null) deb = deb.add(d.value());
+                        hit = true;
+                    }
+                    if (c != null && accountName.equals(c.accountName())) {
+                        if (c.value() != null) cre = cre.add(c.value());
+                        hit = true;
+                    }
+                    if (hit) {
+                        Map<String, Object> row = new LinkedHashMap<>();
+                        row.put("debitAccount", d != null ? d.accountName() : null);
+                        row.put("debitSub", d != null ? d.subAccountName() : null);
+                        row.put("debitValue", d != null ? d.value() : null);
+                        row.put("creditAccount", c != null ? c.accountName() : null);
+                        row.put("creditSub", c != null ? c.subAccountName() : null);
+                        row.put("creditValue", c != null ? c.value() : null);
+                        row.put("remark", b.remark());
+                        hitBranches.add(row);
+                    }
+                }
+                if (hitBranches.isEmpty()) continue;
+                BigDecimal diff = cre.subtract(deb);
+                Map<String, Object> jEntry = new LinkedHashMap<>();
+                jEntry.put("journalNumber", j.number());
+                jEntry.put("transactionDate", j.transactionDate() != null ? j.transactionDate().toString() : null);
+                jEntry.put("memo", j.memo());
+                jEntry.put("debitSum", deb);
+                jEntry.put("creditSum", cre);
+                jEntry.put("diff", diff);
+                jEntry.put("branches", hitBranches);
+                if (diff.signum() == 0) balancedJournals.add(jEntry);
+                else unbalancedJournals.add(jEntry);
+            }
+            if (journals.size() < perPage) break;
+            page++;
+        }
+
+        return ResponseEntity.ok(Map.of(
+                "accountName", accountName,
+                "startDate", startDate,
+                "endDate", endDate,
+                "balancedJournalCount", balancedJournals.size(),
+                "unbalancedJournalCount", unbalancedJournals.size(),
+                "unbalancedJournals", unbalancedJournals,
+                "balancedSamples", balancedJournals.stream().limit(3).toList()
+        ));
+    }
+
+    /**
+     * 指定勘定科目の残高推移を計算する（/journals 全件走査）。
+     * 例: /debug/account-trend?accountName=仕入資金複合&startDate=2025-10-01&endDate=2026-02-20
+     */
+    @GetMapping("/account-trend")
+    public ResponseEntity<?> debugAccountTrend(
+            @RequestParam("accountName") String accountName,
+            @RequestParam("startDate") String startDate,
+            @RequestParam("endDate") String endDate) {
+        MMfOauthClient client = mfOauthService.findActiveClient()
+                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
+        String accessToken = mfOauthService.getValidAccessToken();
+
+        BigDecimal totalDebit = BigDecimal.ZERO;
+        BigDecimal totalCredit = BigDecimal.ZERO;
+        TreeMap<LocalDate, BigDecimal[]> daily = new TreeMap<>();
+        int hitCount = 0;
+
+        int page = 1;
+        final int perPage = 1000;
+        while (true) {
+            MfJournalsResponse res = mfApiClient.listJournals(client, accessToken, startDate, endDate, page, perPage);
+            List<MfJournal> journals = res.items();
+            if (journals.isEmpty()) break;
+            for (MfJournal j : journals) {
+                if (j.branches() == null) continue;
+                for (MfJournal.MfBranch b : j.branches()) {
+                    MfJournal.MfSide deb = b.debitor();
+                    MfJournal.MfSide cre = b.creditor();
+                    boolean hit = false;
+                    BigDecimal dv = BigDecimal.ZERO;
+                    BigDecimal cv = BigDecimal.ZERO;
+                    if (deb != null && accountName.equals(deb.accountName())) {
+                        if (deb.value() != null) {
+                            dv = deb.value();
+                            totalDebit = totalDebit.add(dv);
+                        }
+                        hit = true;
+                    }
+                    if (cre != null && accountName.equals(cre.accountName())) {
+                        if (cre.value() != null) {
+                            cv = cre.value();
+                            totalCredit = totalCredit.add(cv);
+                        }
+                        hit = true;
+                    }
+                    if (hit) {
+                        hitCount++;
+                        LocalDate d = j.transactionDate();
+                        if (d != null) {
+                            BigDecimal[] arr = daily.computeIfAbsent(d,
+                                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
+                            arr[0] = arr[0].add(dv);
+                            arr[1] = arr[1].add(cv);
+                        }
+                    }
+                }
+            }
+            if (journals.size() < perPage) break;
+            page++;
+        }
+
+        List<Map<String, Object>> trend = new ArrayList<>();
+        BigDecimal running = BigDecimal.ZERO;
+        for (Map.Entry<LocalDate, BigDecimal[]> e : daily.entrySet()) {
+            BigDecimal d = e.getValue()[0];
+            BigDecimal c = e.getValue()[1];
+            running = running.add(c).subtract(d);
+            trend.add(Map.of(
+                    "date", e.getKey().toString(),
+                    "debit", d,
+                    "credit", c,
+                    "runningBalance", running
+            ));
+        }
+
+        return ResponseEntity.ok(Map.of(
+                "accountName", accountName,
+                "startDate", startDate,
+                "endDate", endDate,
+                "hitBranchCount", hitCount,
+                "totalDebit", totalDebit,
+                "totalCredit", totalCredit,
+                "balanceAsLiability", totalCredit.subtract(totalDebit),
+                "balanceAsAsset", totalDebit.subtract(totalCredit),
+                "trend", trend
+        ));
+    }
+
+    private ResponseEntity<?> fetchRawWithFirstN(String path, int sampleSize) {
+        try {
+            MMfOauthClient client = mfOauthService.findActiveClient()
+                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
+            String accessToken = mfOauthService.getValidAccessToken();
+            JsonNode raw = mfDebugApiClient.getRaw(client, accessToken, path);
+            return ResponseEntity.ok(trimToSample(raw, sampleSize));
+        } catch (MfReAuthRequiredException e) {
+            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
+                    .body(Map.of("message", e.getMessage()));
+        }
+    }
+
+    /**
+     * レスポンスが配列 or {data:[...]} / {accounts:[...]} 等のいずれでも最初 N 件だけ残す。
+     * shape 調査が目的なので wrapper と中身の両方が見えるように。
+     */
+    private JsonNode trimToSample(JsonNode raw, int n) {
+        if (raw == null) return null;
+        if (raw.isArray()) {
+            com.fasterxml.jackson.databind.node.ArrayNode arr =
+                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
+            for (int i = 0; i < Math.min(n, raw.size()); i++) arr.add(raw.get(i));
+            return arr;
+        }
+        if (raw.isObject()) {
+            com.fasterxml.jackson.databind.node.ObjectNode copy = raw.deepCopy();
+            raw.fieldNames().forEachRemaining(field -> {
+                JsonNode v = copy.get(field);
+                if (v != null && v.isArray()) {
+                    com.fasterxml.jackson.databind.node.ArrayNode arr =
+                            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
+                    for (int i = 0; i < Math.min(n, v.size()); i++) arr.add(v.get(i));
+                    copy.set(field, arr);
+                }
+            });
+            return copy;
+        }
+        return raw;
+    }
+}
diff --git a/backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java b/backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java
index c8061f2..22dc9a1 100644
--- a/backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java
+++ b/backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java
@@ -1,9 +1,9 @@
 package jp.co.oda32.domain.service.finance.mf;
 
-import com.fasterxml.jackson.databind.JsonNode;
 import jp.co.oda32.domain.model.finance.MMfOauthClient;
-import lombok.RequiredArgsConstructor;
+import jp.co.oda32.util.SensitiveLogMasker;
 import lombok.extern.log4j.Log4j2;
+import org.springframework.beans.factory.annotation.Qualifier;
 import org.springframework.http.HttpHeaders;
 import org.springframework.http.HttpStatusCode;
 import org.springframework.http.MediaType;
@@ -11,11 +11,15 @@ import org.springframework.stereotype.Component;
 import org.springframework.util.LinkedMultiValueMap;
 import org.springframework.util.MultiValueMap;
 import org.springframework.web.client.HttpClientErrorException;
+import org.springframework.web.client.HttpServerErrorException;
 import org.springframework.web.client.RestClient;
+import org.springframework.web.util.UriComponentsBuilder;
 
 import java.net.URLEncoder;
 import java.nio.charset.StandardCharsets;
 import java.util.Base64;
+import java.util.Objects;
+import java.util.function.Supplier;
 
 /**
  * マネーフォワードクラウド会計 API への低レベル HTTP クライアント。
@@ -24,32 +28,45 @@ import java.util.Base64;
  * {@link #restClient()} を経由して呼ぶ。再認可が必要なエラーは
  * {@link MfReAuthRequiredException} に変換する。
  *
+ * <p>SF-01: timeout 設定済み {@link RestClient} (mfRestClient) を field injection で利用。
+ * <p>SF-04: error body のロギングは {@link SensitiveLogMasker#mask(String)} 経由でマスキング。
+ * <p>SF-07: basic auth header は RFC 6749 §2.3.1 準拠で URL encode してから base64。
+ * <p>SF-08: 429/5xx は {@link #executeWithRetry(Supplier, String)} 経由で指数バックオフ。
+ * <p>SF-10: {@link #urlEncode(String)} は null を fail-fast。
+ * <p>SF-11: URL 組み立ては {@link UriComponentsBuilder} を使用。
+ * <p>SF-21: postToken は access_token のみ必須 (refresh_token null fallback は呼び出し側)。
+ *
  * @since 2026/04/20
  */
 @Component
 @Log4j2
-@RequiredArgsConstructor
 public class MfApiClient {
 
-    private final RestClient.Builder restClientBuilder;
+    /** 429/5xx 時のリトライ間隔 (指数バックオフ): 1s → 2s → 4s。 */
+    private static final long[] RETRY_BACKOFFS_MS = {1000L, 2000L, 4000L};
+
+    private final RestClient restClient;
+
+    public MfApiClient(@Qualifier("mfRestClient") RestClient restClient) {
+        this.restClient = restClient;
+    }
 
     /**
      * 認可 URL を組み立てる（ブラウザをリダイレクトするための URL）。
      * PKCE S256 を使う: {@code code_challenge} と {@code code_challenge_method=S256} を付与する (B-4)。
      */
     public String buildAuthorizeUrl(MMfOauthClient client, String state, String codeChallenge) {
-        StringBuilder sb = new StringBuilder(client.getAuthorizeUrl());
-        sb.append(client.getAuthorizeUrl().contains("?") ? "&" : "?");
-        sb.append("response_type=code");
-        sb.append("&client_id=").append(urlEncode(client.getClientId()));
-        sb.append("&redirect_uri=").append(urlEncode(client.getRedirectUri()));
-        sb.append("&scope=").append(urlEncode(client.getScope()));
-        sb.append("&state=").append(urlEncode(state));
+        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(client.getAuthorizeUrl())
+                .queryParam("response_type", "code")
+                .queryParam("client_id", client.getClientId())
+                .queryParam("redirect_uri", client.getRedirectUri())
+                .queryParam("scope", client.getScope())
+                .queryParam("state", state);
         if (codeChallenge != null && !codeChallenge.isEmpty()) {
-            sb.append("&code_challenge=").append(urlEncode(codeChallenge));
-            sb.append("&code_challenge_method=S256");
+            b.queryParam("code_challenge", codeChallenge)
+                    .queryParam("code_challenge_method", "S256");
         }
-        return sb.toString();
+        return b.build().encode(StandardCharsets.UTF_8).toUriString();
     }
 
     /**
@@ -64,7 +81,9 @@ public class MfApiClient {
         if (codeVerifier != null && !codeVerifier.isEmpty()) {
             form.add("code_verifier", codeVerifier);
         }
-        return postToken(client.getTokenUrl(), form, basicAuthHeader(client.getClientId(), clientSecret));
+        return executeWithRetry(
+                () -> postToken(client.getTokenUrl(), form, basicAuthHeader(client.getClientId(), clientSecret)),
+                "exchangeCodeForToken");
     }
 
     /** refresh_token で新しい access_token を取得する（CLIENT_SECRET_BASIC）。 */
@@ -72,7 +91,9 @@ public class MfApiClient {
         MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
         form.add("grant_type", "refresh_token");
         form.add("refresh_token", refreshToken);
-        return postToken(client.getTokenUrl(), form, basicAuthHeader(client.getClientId(), clientSecret));
+        return executeWithRetry(
+                () -> postToken(client.getTokenUrl(), form, basicAuthHeader(client.getClientId(), clientSecret)),
+                "refreshToken");
     }
 
     /**
@@ -80,31 +101,7 @@ public class MfApiClient {
      * MfJournalService など高レベル Service が利用する想定。
      */
     public RestClient restClient() {
-        return restClientBuilder.build();
-    }
-
-    /**
-     * 指定パスの MF API を生 JSON のまま取得する（診断用）。
-     */
-    public JsonNode getRaw(MMfOauthClient client, String accessToken, String path) {
-        String url = client.getApiBaseUrl() + path;
-        try {
-            JsonNode node = restClientBuilder.build().get()
-                    .uri(url)
-                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
-                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
-                    .retrieve()
-                    .body(JsonNode.class);
-            return node;
-        } catch (HttpClientErrorException e) {
-            HttpStatusCode status = e.getStatusCode();
-            String body = e.getResponseBodyAsString();
-            log.warn("MF {} 取得失敗: status={}, body={}", path, status.value(), body);
-            if (status.value() == 401) {
-                throw new MfReAuthRequiredException("MF API 認証失敗。再認証してください: " + body, e);
-            }
-            throw e;
-        }
+        return restClient;
     }
 
     /**
@@ -114,54 +111,61 @@ public class MfApiClient {
      */
     public MfJournalsResponse listJournals(MMfOauthClient client, String accessToken,
                                             String startDate, String endDate, int page, int perPage) {
-        String url = client.getApiBaseUrl() + "/api/v3/journals"
-                + "?start_date=" + urlEncode(startDate)
-                + "&end_date=" + urlEncode(endDate)
-                + "&page=" + page
-                + "&per_page=" + perPage;
-        try {
-            MfJournalsResponse res = restClientBuilder.build().get()
-                    .uri(url)
-                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
-                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
-                    .retrieve()
-                    .body(MfJournalsResponse.class);
-            return res != null ? res : new MfJournalsResponse(null);
-        } catch (HttpClientErrorException e) {
-            HttpStatusCode status = e.getStatusCode();
-            String body = e.getResponseBodyAsString();
-            log.warn("MF /journals 取得失敗: status={}, body={}", status.value(), body);
-            if (status.value() == 401) {
-                throw new MfReAuthRequiredException("MF API 認証失敗。再認証してください: " + body, e);
+        String url = UriComponentsBuilder.fromHttpUrl(client.getApiBaseUrl() + "/api/v3/journals")
+                .queryParam("start_date", startDate)
+                .queryParam("end_date", endDate)
+                .queryParam("page", page)
+                .queryParam("per_page", perPage)
+                .build()
+                .encode(StandardCharsets.UTF_8)
+                .toUriString();
+        return executeWithRetry(() -> {
+            try {
+                MfJournalsResponse res = restClient.get()
+                        .uri(url)
+                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
+                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
+                        .retrieve()
+                        .body(MfJournalsResponse.class);
+                return res != null ? res : new MfJournalsResponse(null);
+            } catch (HttpClientErrorException e) {
+                HttpStatusCode status = e.getStatusCode();
+                String body = e.getResponseBodyAsString();
+                log.warn("MF /journals 取得失敗: status={}, body={}", status.value(), SensitiveLogMasker.mask(body));
+                if (status.value() == 401) {
+                    throw new MfReAuthRequiredException("再認証が必要です。詳細は管理者ログを参照してください", e);
+                }
+                if (status.value() == 403) {
+                    throw new MfScopeInsufficientException("mfc/accounting/journal.read",
+                            "MF scope 不足です (mfc/accounting/journal.read 必要)。クライアント設定更新 + 再認証してください", e);
+                }
+                throw e;
             }
-            if (status.value() == 403) {
-                throw new MfScopeInsufficientException("mfc/accounting/journal.read",
-                        "MF scope 不足です (mfc/accounting/journal.read 必要)。クライアント設定更新 + 再認証してください: " + body, e);
-            }
-            throw e;
-        }
+        }, "listJournals");
     }
 
     /** GET /api/v3/taxes で税区分一覧を取得。 */
     public MfTaxesResponse listTaxes(MMfOauthClient client, String accessToken) {
         String url = client.getApiBaseUrl() + "/api/v3/taxes";
-        try {
-            MfTaxesResponse res = restClientBuilder.build().get()
-                    .uri(url)
-                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
-                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
-                    .retrieve()
-                    .body(MfTaxesResponse.class);
-            return res != null ? res : new MfTaxesResponse(null);
-        } catch (HttpClientErrorException e) {
-            HttpStatusCode status = e.getStatusCode();
-            String body = e.getResponseBodyAsString();
-            log.warn("MF /taxes 取得失敗: status={}, body={}", status.value(), body);
-            if (status.value() == 401) {
-                throw new MfReAuthRequiredException("MF API 認証失敗。再認証してください: " + body, e);
+        return executeWithRetry(() -> {
+            try {
+                MfTaxesResponse res = restClient.get()
+                        .uri(url)
+                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
+                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
+                        .retrieve()
+                        .body(MfTaxesResponse.class);
+                return res != null ? res : new MfTaxesResponse(null);
+            } catch (HttpClientErrorException e) {
+                HttpStatusCode status = e.getStatusCode();
+                String body = e.getResponseBodyAsString();
+                log.warn("MF /taxes 取得失敗: status={}, body={}", status.value(), SensitiveLogMasker.mask(body));
+                if (status.value() == 401) {
+                    throw new MfReAuthRequiredException("再認証が必要です。詳細は管理者ログを参照してください", e);
+                }
+                throw e;
             }
-            throw e;
-        }
+        }, "listTaxes");
     }
 
     /**
@@ -180,29 +184,74 @@ public class MfApiClient {
      */
     public MfTrialBalanceBsResponse getTrialBalanceBs(MMfOauthClient client, String accessToken,
                                                        String endDate) {
-        String url = client.getApiBaseUrl() + "/api/v3/reports/trial_balance_bs"
-                + "?end_date=" + urlEncode(endDate);
-        try {
-            MfTrialBalanceBsResponse res = restClientBuilder.build().get()
-                    .uri(url)
-                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
-                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
-                    .retrieve()
-                    .body(MfTrialBalanceBsResponse.class);
-            return res != null ? res : new MfTrialBalanceBsResponse(null, endDate, null, null);
-        } catch (HttpClientErrorException e) {
-            HttpStatusCode status = e.getStatusCode();
-            String body = e.getResponseBodyAsString();
-            log.warn("MF /trial_balance_bs 取得失敗: status={}, body={}", status.value(), body);
-            if (status.value() == 401) {
-                throw new MfReAuthRequiredException("MF API 認証失敗。再認証してください: " + body, e);
+        String url = UriComponentsBuilder.fromHttpUrl(client.getApiBaseUrl() + "/api/v3/reports/trial_balance_bs")
+                .queryParam("end_date", endDate)
+                .build()
+                .encode(StandardCharsets.UTF_8)
+                .toUriString();
+        return executeWithRetry(() -> {
+            try {
+                MfTrialBalanceBsResponse res = restClient.get()
+                        .uri(url)
+                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
+                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
+                        .retrieve()
+                        .body(MfTrialBalanceBsResponse.class);
+                return res != null ? res : new MfTrialBalanceBsResponse(null, endDate, null, null);
+            } catch (HttpClientErrorException e) {
+                HttpStatusCode status = e.getStatusCode();
+                String body = e.getResponseBodyAsString();
+                log.warn("MF /trial_balance_bs 取得失敗: status={}, body={}", status.value(), SensitiveLogMasker.mask(body));
+                if (status.value() == 401) {
+                    throw new MfReAuthRequiredException("再認証が必要です。詳細は管理者ログを参照してください", e);
+                }
+                if (status.value() == 403) {
+                    throw new MfScopeInsufficientException("mfc/accounting/report.read",
+                            "MF scope 不足です (mfc/accounting/report.read)。クライアント設定に scope を追加 → 再認証してください", e);
+                }
+                throw e;
             }
-            if (status.value() == 403) {
-                throw new MfScopeInsufficientException("mfc/accounting/report.read",
-                        "MF scope 不足です (mfc/accounting/report.read)。クライアント設定に scope を追加 → 再認証してください: " + body, e);
+        }, "getTrialBalanceBs");
+    }
+
+    /**
+     * GET /v2/tenant で MF クラウド会計の tenant (= 連携先事業者) 情報を取得。
+     * <p>
+     * P1-01 / DD-F-04: 別会社 MF への誤接続を検知するため、callback 直後と
+     * refresh 後に呼び出して {@code mf_tenant_id} の一致確認に使う。scope
+     * {@code mfc/admin/tenant.read} が必要。不足時は 403 →
+     * {@link MfScopeInsufficientException} に変換する。
+     * <p>
+     * 401 (token 失効) は {@link MfReAuthRequiredException} に変換 (他 endpoint と同様)。
+     */
+    public MfTenantResponse getTenant(MMfOauthClient client, String accessToken) {
+        String url = client.getApiBaseUrl() + "/v2/tenant";
+        return executeWithRetry(() -> {
+            try {
+                MfTenantResponse res = restClient.get()
+                        .uri(url)
+                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
+                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
+                        .retrieve()
+                        .body(MfTenantResponse.class);
+                if (res == null || res.id() == null || res.id().isBlank()) {
+                    throw new IllegalStateException("MF /v2/tenant が tenant id を返却しませんでした");
+                }
+                return res;
+            } catch (HttpClientErrorException e) {
+                HttpStatusCode status = e.getStatusCode();
+                String body = e.getResponseBodyAsString();
+                log.warn("MF /v2/tenant 取得失敗: status={}, body={}", status.value(), SensitiveLogMasker.mask(body));
+                if (status.value() == 401) {
+                    throw new MfReAuthRequiredException("再認証が必要です。詳細は管理者ログを参照してください", e);
+                }
+                if (status.value() == 403) {
+                    throw new MfScopeInsufficientException("mfc/admin/tenant.read",
+                            "MF scope 不足です (mfc/admin/tenant.read 必要)。クライアント設定の scope を更新 → 再認証してください", e);
+                }
+                throw e;
             }
-            throw e;
-        }
+        }, "getTenant");
     }
 
     /**
@@ -213,56 +262,115 @@ public class MfApiClient {
      */
     public MfAccountsResponse listAccounts(MMfOauthClient client, String accessToken) {
         String url = client.getApiBaseUrl() + "/api/v3/accounts";
-        try {
-            MfAccountsResponse res = restClientBuilder.build().get()
-                    .uri(url)
-                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
-                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
-                    .retrieve()
-                    .body(MfAccountsResponse.class);
-            return res != null ? res : new MfAccountsResponse(null);
-        } catch (HttpClientErrorException e) {
-            HttpStatusCode status = e.getStatusCode();
-            String body = e.getResponseBodyAsString();
-            log.warn("MF /accounts 取得失敗: status={}, body={}", status.value(), body);
-            if (status.value() == 401) {
-                throw new MfReAuthRequiredException("MF API 認証失敗。再認証してください: " + body, e);
+        return executeWithRetry(() -> {
+            try {
+                MfAccountsResponse res = restClient.get()
+                        .uri(url)
+                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
+                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
+                        .retrieve()
+                        .body(MfAccountsResponse.class);
+                return res != null ? res : new MfAccountsResponse(null);
+            } catch (HttpClientErrorException e) {
+                HttpStatusCode status = e.getStatusCode();
+                String body = e.getResponseBodyAsString();
+                log.warn("MF /accounts 取得失敗: status={}, body={}", status.value(), SensitiveLogMasker.mask(body));
+                if (status.value() == 401) {
+                    throw new MfReAuthRequiredException("再認証が必要です。詳細は管理者ログを参照してください", e);
+                }
+                throw e;
             }
-            throw e;
-        }
+        }, "listAccounts");
     }
 
     private MfTokenResponse postToken(String tokenUrl, MultiValueMap<String, String> form, String basicAuth) {
         try {
-            MfTokenResponse res = restClientBuilder.build().post()
+            MfTokenResponse res = restClient.post()
                     .uri(tokenUrl)
                     .header(HttpHeaders.AUTHORIZATION, basicAuth)
                     .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                     .body(form)
                     .retrieve()
                     .body(MfTokenResponse.class);
-            if (res == null || res.accessToken() == null || res.refreshToken() == null) {
-                throw new IllegalStateException("MF token エンドポイントから不正なレスポンス");
+            // SF-21: refresh_token は MF が返さないこともある (refresh_token_rotation 設定次第)。
+            // access_token のみ必須。refresh_token null の場合は呼び出し側で既存値を流用する。
+            if (res == null || res.accessToken() == null) {
+                throw new IllegalStateException("MF token エンドポイントから不正なレスポンス (access_token なし)");
             }
             return res;
         } catch (HttpClientErrorException e) {
             HttpStatusCode status = e.getStatusCode();
             String body = e.getResponseBodyAsString();
-            log.warn("MF token エンドポイント失敗: status={}, body={}", status.value(), body);
+            log.warn("MF token エンドポイント失敗: status={}, body={}", status.value(), SensitiveLogMasker.mask(body));
             if (status.value() == 400 || status.value() == 401) {
                 throw new MfReAuthRequiredException(
-                        "MF トークン交換に失敗しました（再認証が必要）: " + body, e);
+                        "再認証が必要です。詳細は管理者ログを参照してください", e);
             }
             throw e;
         }
     }
 
+    /**
+     * SF-07: RFC 6749 §2.3.1 準拠の HTTP Basic 認証ヘッダを生成。
+     * client_id / client_secret に予約文字 (例: {@code :}) や非 ASCII が含まれる場合に備えて、
+     * base64 する前に application/x-www-form-urlencoded で URL encode する。
+     */
     private static String basicAuthHeader(String clientId, String clientSecret) {
-        String raw = clientId + ":" + clientSecret;
+        Objects.requireNonNull(clientId, "clientId が null");
+        Objects.requireNonNull(clientSecret, "clientSecret が null");
+        String encId = URLEncoder.encode(clientId, StandardCharsets.UTF_8);
+        String encSecret = URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
+        String raw = encId + ":" + encSecret;
         return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
     }
 
+    /** SF-10: null in は fail-fast。これまで暗黙に空文字に変換していたバグの再発防止。 */
     private static String urlEncode(String s) {
-        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
+        Objects.requireNonNull(s, "url-encoded 引数が null");
+        return URLEncoder.encode(s, StandardCharsets.UTF_8);
+    }
+
+    /**
+     * SF-08: MF API 呼び出しの共通リトライラッパ。
+     * <ul>
+     *   <li>429 (Too Many Requests) と 5xx を retryable とみなす</li>
+     *   <li>1s → 2s → 4s の指数バックオフ (最大 3 回)</li>
+     *   <li>{@link MfReAuthRequiredException}/{@link MfScopeInsufficientException} は即時 throw</li>
+     * </ul>
+     */
+    private <T> T executeWithRetry(Supplier<T> op, String operation) {
+        int attempt = 0;
+        while (true) {
+            try {
+                return op.get();
+            } catch (HttpClientErrorException e) {
+                if (e.getStatusCode().value() == 429 && attempt < RETRY_BACKOFFS_MS.length) {
+                    long wait = RETRY_BACKOFFS_MS[attempt];
+                    log.warn("[mf-api] {} 429, {}ms sleep して retry ({}回目)", operation, wait, attempt + 1);
+                    sleepQuietly(wait);
+                    attempt++;
+                    continue;
+                }
+                throw e;
+            } catch (HttpServerErrorException e) {
+                if (attempt < RETRY_BACKOFFS_MS.length) {
+                    long wait = RETRY_BACKOFFS_MS[attempt];
+                    log.warn("[mf-api] {} {} server error, {}ms sleep して retry ({}回目)",
+                            operation, e.getStatusCode().value(), wait, attempt + 1);
+                    sleepQuietly(wait);
+                    attempt++;
+                    continue;
+                }
+                throw e;
+            }
+        }
+    }
+
+    private static void sleepQuietly(long ms) {
+        try {
+            Thread.sleep(ms);
+        } catch (InterruptedException ie) {
+            Thread.currentThread().interrupt();
+        }
     }
 }
diff --git a/backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java b/backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java
index 49d4bc9..c0de967 100644
--- a/backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java
+++ b/backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java
@@ -2,11 +2,12 @@ package jp.co.oda32.domain.service.finance.mf;
 
 import jakarta.persistence.EntityManager;
 import jakarta.persistence.PersistenceContext;
+import jp.co.oda32.audit.AuditLog;
 import jp.co.oda32.domain.model.finance.MMfOauthClient;
 import jp.co.oda32.domain.model.finance.TMfOauthToken;
 import jp.co.oda32.domain.repository.finance.MMfOauthClientRepository;
 import jp.co.oda32.domain.repository.finance.TMfOauthTokenRepository;
-import jp.co.oda32.util.CryptoUtil;
+import jp.co.oda32.util.OauthCryptoUtil;
 import lombok.RequiredArgsConstructor;
 import lombok.extern.log4j.Log4j2;
 import org.springframework.beans.factory.annotation.Autowired;
@@ -55,8 +56,10 @@ public class MfOauthService {
     private final MMfOauthClientRepository clientRepository;
     private final TMfOauthTokenRepository tokenRepository;
     private final MfApiClient mfApiClient;
-    private final CryptoUtil cryptoUtil;
+    private final OauthCryptoUtil cryptoUtil;
     private final MfOauthStateStore stateStore;
+    /** G1-M5: MF OAuth エンドポイント URL のホスト検証 (credential exfiltration 対策)。 */
+    private final MfOauthHostAllowlist hostAllowlist;
 
     @PersistenceContext
     private EntityManager entityManager;
@@ -79,6 +82,11 @@ public class MfOauthService {
     public MMfOauthClient upsertClient(MMfOauthClient requested, String plainClientSecret, Integer userNo) {
         // redirect_uri はホワイトリスト完全一致 (B-4)。ADMIN でも任意 URL 設定は不可。
         validateRedirectUri(requested.getRedirectUri());
+        // G1-M5: OAuth endpoint host allowlist 検証。
+        // 攻撃者制御 URL に client_secret を Basic auth で流す経路を遮断する。
+        hostAllowlist.validate(requested.getAuthorizeUrl(), "authorizeUrl");
+        hostAllowlist.validate(requested.getTokenUrl(), "tokenUrl");
+        hostAllowlist.validate(requested.getApiBaseUrl(), "apiBaseUrl");
         Timestamp now = Timestamp.from(Instant.now());
         MMfOauthClient existing = clientRepository.findFirstByDelFlgOrderByIdDesc("0").orElse(null);
         if (existing != null) {
@@ -117,9 +125,37 @@ public class MfOauthService {
         return new AuthorizeUrl(url, issued.state());
     }
 
-    /** MF からの callback を処理し、トークンを DB 保存して接続状態にする。state 検証 + PKCE 込み。 */
-    @Transactional
+    /**
+     * MF からの callback を処理し、トークンを DB 保存して接続状態にする。state 検証 + PKCE 込み。
+     *
+     * <p>SF-20: HTTP 呼び出しを tx 外に分離する 3 段構成。
+     * <ol>
+     *   <li>read-only tx: state 消費 + client 取得 ({@link #consumeStateAndLoadClient})</li>
+     *   <li>tx 外: MF token endpoint への HTTP (= 数秒〜数十秒) を呼ぶ</li>
+     *   <li>REQUIRES_NEW tx: token 永続化 ({@link #persistTokenInNewTx})</li>
+     * </ol>
+     * これで MF API のレイテンシ中に DB connection を占有しない (B-W6 パターンに統一)。
+     */
+    @AuditLog(table = "t_mf_oauth_token", operation = "auth_callback",
+            pkExpression = "{'userNo': #a2}",
+            captureArgsAsAfter = true)
     public void handleCallback(String code, String state, Integer userNo) {
+        // 1) state 消費 + client 読み取り (短い tx)
+        CallbackContext ctx = self.consumeStateAndLoadClient(state, userNo);
+        // 2) tx 外で HTTP (token 交換)
+        MfTokenResponse token = mfApiClient.exchangeCodeForToken(
+                ctx.client(), ctx.plainSecret(), code, ctx.codeVerifier());
+        // 3) tx 外で HTTP (P1-01: tenant binding 検証)
+        //    別会社 MF を誤認可した場合に、ここで MfTenantMismatchException を throw して停止する。
+        //    既存 client.mfTenantId が NULL (旧データ互換) なら初回 binding として確定する。
+        MfTenantResponse tenant = mfApiClient.getTenant(ctx.client(), token.accessToken());
+        // 4) 永続化のみ別 tx (token + tenant binding)
+        self.persistTokenAndTenantInNewTx(ctx.client().getId(), token, tenant, userNo);
+    }
+
+    /** SF-20: state 消費 + client 取得を 1 つの短い tx で行う。 */
+    @Transactional
+    public CallbackContext consumeStateAndLoadClient(String state, Integer userNo) {
         Optional<String> verifierOpt = stateStore.verifyAndConsume(state, userNo);
         if (verifierOpt.isEmpty()) {
             throw new IllegalArgumentException("MF OAuth state 検証失敗 (期限切れ or 改ざん)");
@@ -127,10 +163,45 @@ public class MfOauthService {
         MMfOauthClient client = findActiveClient()
                 .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
         String plainSecret = cryptoUtil.decrypt(client.getClientSecretEnc());
-        MfTokenResponse token = mfApiClient.exchangeCodeForToken(client, plainSecret, code, verifierOpt.get());
+        return new CallbackContext(client, plainSecret, verifierOpt.get());
+    }
+
+    /** SF-20: token 永続化のみ REQUIRES_NEW で実行 (HTTP の長い tx を避ける)。 */
+    @Transactional(propagation = Propagation.REQUIRES_NEW)
+    public void persistTokenInNewTx(Integer clientId, MfTokenResponse token, Integer userNo) {
+        MMfOauthClient client = clientRepository.findById(clientId)
+                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が消えています: " + clientId));
+        persistToken(client, token, userNo);
+    }
+
+    /**
+     * P1-01: token 永続化 + tenant binding を 1 つの tx でまとめて実行。
+     * <p>
+     * 既存 client に {@code mf_tenant_id} が保存されている場合は一致確認し、
+     * 不一致なら {@link MfTenantMismatchException} を throw して tx を rollback する
+     * (= token も保存しない)。これにより別会社 MF への誤認可で業務データを汚染しない。
+     * <p>
+     * 一致 or 初回認可 (mf_tenant_id IS NULL) は client の tenant 列を更新して save する。
+     */
+    @Transactional(propagation = Propagation.REQUIRES_NEW)
+    public void persistTokenAndTenantInNewTx(
+            Integer clientId, MfTokenResponse token, MfTenantResponse tenant, Integer userNo) {
+        MMfOauthClient client = clientRepository.findById(clientId)
+                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が消えています: " + clientId));
+        bindOrVerifyTenant(client, tenant, userNo);
         persistToken(client, token, userNo);
     }
 
+    /** {@link #consumeStateAndLoadClient} 戻り値。 */
+    public record CallbackContext(MMfOauthClient client, String plainSecret, String codeVerifier) {
+        @Override
+        public String toString() {
+            // SF-02 同様: plainSecret/codeVerifier はマスキング
+            return "CallbackContext[clientId=" + (client != null ? client.getId() : null)
+                    + ", plainSecret=***, codeVerifier=***]";
+        }
+    }
+
     /**
      * 有効な access_token を返す（期限切れなら refresh を試みる）。突合 Service が利用。
      * <p>HTTP refresh とDB永続化を同一 tx で行うと、MF API のレイテンシ中に DB コネクション占有する
@@ -141,6 +212,30 @@ public class MfOauthService {
     public String getValidAccessToken() {
         // 1) 既存 token の読み取り (短い read-only tx)
         TokenSnapshot snap = self.loadActiveTokenSnapshot();
+
+        // G1-M3: 未バインド client (P1-01 導入前の旧データ) は fresh パスでも強制 binding。
+        // P1-01 は refresh 経路でだけ tenant 検証する設計だったため、access_token が fresh
+        // な間は別会社 MF を誤認可していても業務 API が通ってしまう穴があった。
+        // ここで /v2/tenant を呼んで mf_tenant_id を確定する。fetch 失敗時は
+        // MfTenantBindingFailedException を throw して業務 API 全体を停止する (P1-01 と同じ厳格動作)。
+        if (snap.client.getMfTenantId() == null && snap.isFresh(REFRESH_MARGIN_SECONDS)) {
+            log.info("MF client.mf_tenant_id IS NULL: 強制 tenant binding を実行 clientId={}",
+                    snap.client.getId());
+            MfTenantResponse tenant;
+            try {
+                tenant = mfApiClient.getTenant(snap.client, snap.accessToken);
+            } catch (MfTenantBindingFailedException e) {
+                throw e;
+            } catch (RuntimeException e) {
+                throw new MfTenantBindingFailedException(
+                        "未バインド client の tenant 取得に失敗しました clientId=" + snap.client.getId(), e);
+            }
+            // tx 内で binding 確定 (token は触らない、既に有効)
+            self.bindTenantInNewTx(snap.client.getId(), tenant);
+            // bind 完了。access_token 自体は変わっていないので snap の値をそのまま返却。
+            return snap.accessToken;
+        }
+
         if (snap.isFresh(REFRESH_MARGIN_SECONDS)) {
             return snap.accessToken;
         }
@@ -149,11 +244,33 @@ public class MfOauthService {
         log.info("MF access_token を refresh します（expiresAt={}）", snap.expiresAt);
         MfTokenResponse refreshed = mfApiClient.refreshToken(snap.client, snap.plainSecret, snap.plainRefresh);
 
-        // 3) 永続化のみ REQUIRES_NEW で直列化
-        self.persistRefreshedToken(snap.client.getId(), refreshed);
+        // 3) P1-01: refresh 後の tenant 一致検証 (別会社 client_secret に差し替えられた等を検知)。
+        //    通常 access_token 利用時は呼ばず、refresh 経路でだけ確認することで負荷を抑える。
+        //    旧データ (mf_tenant_id IS NULL) の場合は初回 binding として確定する。
+        MfTenantResponse tenant = mfApiClient.getTenant(snap.client, refreshed.accessToken());
+
+        // 4) 永続化のみ REQUIRES_NEW で直列化 (tenant binding も同 tx で確定)
+        self.persistRefreshedTokenAndTenant(snap.client.getId(), refreshed, tenant);
         return refreshed.accessToken();
     }
 
+    /**
+     * G1-M3: token は変更せず tenant binding のみ確定する (未バインド旧 client の救済)。
+     * <p>
+     * fresh access_token を保持したまま、{@code /v2/tenant} 観測値を client に書き込む。
+     * 既存 binding がある場合 (= 別 thread が先に binding 確定済) は
+     * {@link #bindOrVerifyTenant} の一致検証ロジックで観測 tenant と突合し、
+     * 不一致なら {@link MfTenantMismatchException} で rollback する。
+     */
+    @Transactional(propagation = Propagation.REQUIRES_NEW)
+    public void bindTenantInNewTx(Integer clientId, MfTenantResponse tenant) {
+        MMfOauthClient client = clientRepository.findById(clientId)
+                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が消えています: " + clientId));
+        // userNo は system 由来 (= null) で記録。bindOrVerifyTenant 内で modify_user_no に直接 set するので
+        // null 許容。手動 callback (handleCallback / persistTokenAndTenantInNewTx) は userNo を渡すため区別される。
+        bindOrVerifyTenant(client, tenant, null);
+    }
+
     /** access_token 取得のための既存 token スナップショット。短い read-only tx で取る。 */
     @Transactional(readOnly = true)
     public TokenSnapshot loadActiveTokenSnapshot() {
@@ -184,44 +301,201 @@ public class MfOauthService {
         persistToken(client, refreshed, null);
     }
 
+    /**
+     * P1-01: refresh 後の token + tenant binding 検証を 1 つの tx でまとめて永続化。
+     * tenant 不一致なら {@link MfTenantMismatchException} で rollback (= 古い token のまま残す)。
+     */
+    @Transactional(propagation = Propagation.REQUIRES_NEW)
+    public void persistRefreshedTokenAndTenant(
+            Integer clientId, MfTokenResponse refreshed, MfTenantResponse tenant) {
+        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:k)")
+                .setParameter("k", REFRESH_LOCK_KEY)
+                .getSingleResult();
+        MMfOauthClient client = clientRepository.findById(clientId)
+                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が消えています: " + clientId));
+        bindOrVerifyTenant(client, tenant, null);
+        persistToken(client, refreshed, null);
+    }
+
+    /** MF refresh_token の公称寿命 (P1-04 案 α: rotation 想定で現 active token から起算)。 */
+    private static final long REFRESH_TOKEN_LIFETIME_DAYS = 540L;
+
     public MfTokenStatus getStatus() {
         Optional<MMfOauthClient> client = findActiveClient();
         if (client.isEmpty()) {
-            return new MfTokenStatus(false, false, null, null, null, false);
+            // T6: 未設定時は scope 解析対象なし (= scopeOk=true で banner 非表示)
+            return new MfTokenStatus(false, false, null, null, null, false,
+                    null, null, null, null, null,
+                    List.of(), List.of(), true, false);
         }
+        MMfOauthClient c = client.get();
+        Instant boundAt = c.getTenantBoundAt() != null ? c.getTenantBoundAt().toInstant() : null;
+        // T6: client.scope (admin が画面で編集できる「次回認可で要求する scope」) を解析。
+        // 現 active token の scope (t.getScope()) ではなく client 側を見るのは、
+        // admin が必須 scope を削除した時点で (再認可前でも) 構成ドリフトを検知するため。
+        MfScopeConstants.ScopeAnalysis scopeAnalysis = MfScopeConstants.analyze(c.getScope());
+        boolean scopeOk = scopeAnalysis.missing().isEmpty();
         Optional<TMfOauthToken> token = tokenRepository
-                .findFirstByClientIdAndDelFlgOrderByIdDesc(client.get().getId(), "0");
+                .findFirstByClientIdAndDelFlgOrderByIdDesc(c.getId(), "0");
         if (token.isEmpty()) {
-            return new MfTokenStatus(true, false, null, null, null, false);
+            return new MfTokenStatus(true, false, null, null, null, false,
+                    c.getMfTenantId(), c.getMfTenantName(), boundAt, null, null,
+                    scopeAnalysis.missing(), scopeAnalysis.extra(), scopeOk, false);
         }
         TMfOauthToken t = token.get();
         Instant exp = t.getExpiresAt().toInstant();
         Instant lastRefreshed = t.getModifyDateTime() != null
                 ? t.getModifyDateTime().toInstant()
                 : t.getAddDateTime().toInstant();
-        return new MfTokenStatus(true, true, exp, t.getScope(), lastRefreshed, false);
+        // G1-M4 (2026-05-06): add_date_time でなく refresh_token_issued_at を使用する。
+        // rotation OFF (= MF レスポンスに refresh_token が無く旧 token を流用) の場合、
+        // softDeleteActiveTokens + 新 row insert で add_date_time は毎回更新されるため
+        // 540 日寿命の起点として誤検知 (残日数過大評価) していた。
+        // 旧 row の値を継承する refresh_token_issued_at を使うことで rotation 設定に依らず正確になる。
+        // 旧データ救済として NULL fallback で add_date_time を残す (V042 backfill で実質発生しない想定)。
+        Instant refreshTokenIssuedAt = t.getRefreshTokenIssuedAt() != null
+                ? t.getRefreshTokenIssuedAt().toInstant()
+                : (t.getAddDateTime() != null ? t.getAddDateTime().toInstant() : null);
+        Integer daysUntilReauth = null;
+        boolean reAuthRequired = false;
+        boolean reAuthExpired = false;
+        if (refreshTokenIssuedAt != null) {
+            long elapsedDays = ChronoUnit.DAYS.between(refreshTokenIssuedAt, Instant.now());
+            long remaining = REFRESH_TOKEN_LIFETIME_DAYS - elapsedDays;
+            if (remaining < 0) {
+                // G1-M4: 540 日超過 = 期限切れ。再認可必須 (UI では最高 severity の destructive banner で表示)。
+                reAuthExpired = true;
+                reAuthRequired = true;
+                // UI 表示用に 0 にクランプ (負値表示は不自然)。
+                daysUntilReauth = 0;
+            } else {
+                // int 安全キャスト (540 日基準で int の範囲を逸脱しない)
+                daysUntilReauth = (int) Math.max(Math.min(remaining, Integer.MAX_VALUE), Integer.MIN_VALUE);
+                reAuthRequired = daysUntilReauth <= 0;
+            }
+        }
+        return new MfTokenStatus(true, true, exp, t.getScope(), lastRefreshed, reAuthRequired,
+                c.getMfTenantId(), c.getMfTenantName(), boundAt,
+                refreshTokenIssuedAt, daysUntilReauth,
+                scopeAnalysis.missing(), scopeAnalysis.extra(), scopeOk, reAuthExpired);
     }
 
-    /** 接続切断。DB トークンを論理削除する（MF 側 revoke は現時点でベストエフォート省略）。 */
+    /**
+     * 接続切断。DB トークンを論理削除し、tenant binding もクリアする
+     * (P1-01: 別 MF tenant への再認可を許可するため)。MF 側 revoke は現時点でベストエフォート省略。
+     */
     @Transactional
+    @AuditLog(table = "t_mf_oauth_token", operation = "revoke",
+            pkExpression = "{'userNo': #a0}",
+            captureArgsAsAfter = true)
     public void revoke(Integer userNo) {
         MMfOauthClient client = findActiveClient().orElse(null);
         if (client == null) return;
-        tokenRepository.softDeleteActiveTokens(client.getId(), Timestamp.from(Instant.now()), userNo);
+        Timestamp now = Timestamp.from(Instant.now());
+        tokenRepository.softDeleteActiveTokens(client.getId(), now, userNo);
+        // P1-01: tenant binding をクリアし、次回 callback で別 tenant に bind 可能とする。
+        if (client.getMfTenantId() != null) {
+            log.info("MF revoke: tenant binding をクリアします clientId={} previousTenantId={} previousTenantName={}",
+                    client.getId(), client.getMfTenantId(), client.getMfTenantName());
+            client.setMfTenantId(null);
+            client.setMfTenantName(null);
+            client.setTenantBoundAt(null);
+            client.setModifyDateTime(now);
+            client.setModifyUserNo(userNo);
+            clientRepository.save(client);
+        }
+    }
+
+    /**
+     * P1-01: tenant binding の検証 + 必要なら確定。
+     * <ul>
+     *   <li>既存 {@code mf_tenant_id} が NULL → 初回 binding として確定 (旧データ互換)</li>
+     *   <li>既存 {@code mf_tenant_id} が一致 → tenant_name のみ更新 (改名追従)</li>
+     *   <li>既存 {@code mf_tenant_id} が不一致 → {@link MfTenantMismatchException} を throw</li>
+     * </ul>
+     */
+    private void bindOrVerifyTenant(MMfOauthClient client, MfTenantResponse tenant, Integer userNo) {
+        if (tenant == null || tenant.id() == null || tenant.id().isBlank()) {
+            throw new IllegalStateException("MF tenant id を取得できませんでした");
+        }
+        String observedId = tenant.id();
+        String observedName = tenant.name();
+        Timestamp now = Timestamp.from(Instant.now());
+
+        if (client.getMfTenantId() == null) {
+            // 初回 binding (旧データ互換 or 初回認可)
+            log.info("MF tenant binding 初回確定 clientId={} tenantId={} tenantName={}",
+                    client.getId(), observedId, observedName);
+            client.setMfTenantId(observedId);
+            client.setMfTenantName(observedName);
+            client.setTenantBoundAt(now);
+            client.setModifyDateTime(now);
+            client.setModifyUserNo(userNo);
+            clientRepository.save(client);
+            return;
+        }
+
+        if (!client.getMfTenantId().equals(observedId)) {
+            // 不一致 = 別会社 MF を認可した可能性
+            log.error("MF tenant 不一致を検知 clientId={} bound={} observed={}",
+                    client.getId(), client.getMfTenantId(), observedId);
+            throw new MfTenantMismatchException(client.getMfTenantId(), observedId,
+                    "既存連携と異なる MF tenant です: 既存=" + client.getMfTenantId()
+                            + " / 新規=" + observedId
+                            + ". 旧連携を解除してから再認可してください");
+        }
+
+        // 一致: 名称差分があれば追従更新 (id 不変なので safe)
+        if (observedName != null && !observedName.equals(client.getMfTenantName())) {
+            log.info("MF tenant 名称を更新 clientId={} tenantId={} oldName={} newName={}",
+                    client.getId(), observedId, client.getMfTenantName(), observedName);
+            client.setMfTenantName(observedName);
+            client.setModifyDateTime(now);
+            client.setModifyUserNo(userNo);
+            clientRepository.save(client);
+        }
     }
 
     private void persistToken(MMfOauthClient client, MfTokenResponse res, Integer userNo) {
         Timestamp now = Timestamp.from(Instant.now());
+        // SF-21: refresh_token は MF が返さないこともある (rotation OFF の場合)。
+        // null fallback は **既存 active token の refresh_token_enc を再利用** する。
+        // softDeleteActiveTokens 実行前に既存 token を読む必要があるため順序に注意。
+        // G1-M4: refresh_token_issued_at の継承元としても旧 row を参照するので、ここで一括取得する。
+        Optional<TMfOauthToken> previousToken = tokenRepository
+                .findFirstByClientIdAndDelFlgOrderByIdDesc(client.getId(), "0");
+        String refreshTokenEnc;
+        if (res.refreshToken() != null) {
+            refreshTokenEnc = cryptoUtil.encrypt(res.refreshToken());
+        } else {
+            refreshTokenEnc = previousToken
+                    .map(TMfOauthToken::getRefreshTokenEnc)
+                    .orElseThrow(() -> new IllegalStateException(
+                            "MF token レスポンスに refresh_token が無く、再利用可能な既存 token も存在しません"));
+        }
+        // G1-M4: refresh_token の真の発行日を判定。
+        //  - rotation 動作 (新 refresh_token 受領) → 今を発行日とする
+        //  - rotation OFF (旧 refresh_token 流用) → 旧 row の値を継承
+        //  - 旧 row なし (= 初回認可) → now (rotation 有無に関わらず安全な fallback)
+        Timestamp refreshTokenIssuedAt;
+        if (res.refreshToken() != null) {
+            refreshTokenIssuedAt = now;
+        } else {
+            refreshTokenIssuedAt = previousToken
+                    .map(TMfOauthToken::getRefreshTokenIssuedAt)
+                    .orElse(now);
+        }
         tokenRepository.softDeleteActiveTokens(client.getId(), now, userNo);
         long expiresInSec = res.expiresIn() != null ? res.expiresIn() : 3600L;
         Timestamp expiresAt = Timestamp.from(Instant.now().plus(expiresInSec, ChronoUnit.SECONDS));
         TMfOauthToken token = TMfOauthToken.builder()
                 .clientId(client.getId())
                 .accessTokenEnc(cryptoUtil.encrypt(res.accessToken()))
-                .refreshTokenEnc(cryptoUtil.encrypt(res.refreshToken()))
+                .refreshTokenEnc(refreshTokenEnc)
                 .tokenType(res.tokenType() != null ? res.tokenType() : "Bearer")
                 .expiresAt(expiresAt)
                 .scope(res.scope())
+                .refreshTokenIssuedAt(refreshTokenIssuedAt)
                 .delFlg("0")
                 .addDateTime(now)
                 .addUserNo(userNo)
@@ -251,7 +525,11 @@ public class MfOauthService {
     /** {@link #buildAuthorizeUrl(Integer)} 戻り値。 */
     public record AuthorizeUrl(String url, String state) {}
 
-    /** {@link #loadActiveTokenSnapshot()} 戻り値。refresh 判定や HTTP 呼び出しで使う。 */
+    /**
+     * {@link #loadActiveTokenSnapshot()} 戻り値。refresh 判定や HTTP 呼び出しで使う。
+     * <p>SF-02: client_secret / access_token / refresh_token は機密値のため
+     * {@link #toString()} で {@code ***} に置換 (ログ漏洩対策)。
+     */
     public record TokenSnapshot(
             MMfOauthClient client,
             String plainSecret,
@@ -261,5 +539,11 @@ public class MfOauthService {
         public boolean isFresh(long marginSeconds) {
             return Instant.now().isBefore(expiresAt.minusSeconds(marginSeconds));
         }
+
+        @Override
+        public String toString() {
+            return "TokenSnapshot[clientId=" + (client != null ? client.getId() : null)
+                    + ", plainSecret=***, accessToken=***, plainRefresh=***, expiresAt=" + expiresAt + "]";
+        }
     }
 }
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

2026-05-06T10:58:45.431099Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path backend/src/main/resources/db/migration | Select-Object -ExpandProperty Name | Sort-Object'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path backend/src/main/resources/db/migration | Select-Object -ExpandProperty Name | Sort-Object' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Path backend/src/main/resources/db/migration | Select-Object -ExpandProperty Name | Sort-Object'` rejected: blocked by policy
2026-05-06T10:58:48.713953Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git ls-tree -r --name-only 1b812b7 -- backend/src/main/resources/db/migration'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git ls-tree -r --name-only 1b812b7 -- backend/src/main/resources/db/migration' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git ls-tree -r --name-only 1b812b7 -- backend/src/main/resources/db/migration'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem backend/src/main/resources/db/migration' in C:\project\odamitsu-data-hub
 succeeded in 11276ms:

    Directory: C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration

Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
-a---          2026/04/13    18:38            262 V001__create_nfkc_function.sql
-a---          2026/04/13    18:38           1379 V002__create_quote_import_tables.sql
-a---          2026/04/13    18:38            327 V003__alter_quote_import_detail_add_status.sql
-a---          2026/04/13    18:38            223 V004__rename_quote_import_detail_to_t.sql
-a---          2026/04/13    18:38           3109 V005__create_nfkc_indexes.sql
-a---          2026/04/13    18:38           2449 V006__create_bcart_categories.sql
-a---          2026/04/13    18:38           2655 V007__create_estimate_comparison_tables.sql
-a---          2026/05/06    16:44          10191 V008__create_mf_cashbook_tables.sql
-a---          2026/04/14    18:49            642 V009__alter_accounts_payable_summary_add_verification_fields.sql
-a---          2026/04/14    18:49            191 V010__alter_estimate_add_auto_generated.sql
-a---          2026/04/14    18:49          22779 V011__create_payment_mf_tables.sql
-a---          2026/04/16    19:05           6275 V012__add_order_delivery_fk.sql
-a---          2026/04/16    18:53           6909 V013__add_order_delivery_status_check.sql
-a---          2026/04/15    15:48            696 V014__alter_accounts_payable_summary_add_verified_amount.sql
-a---          2026/04/16    18:44           4925 V015__create_order_delivery_indexes.sql
-a---          2026/04/16     8:38           2855 V016__create_payment_mf_aux_row.sql
-a---          2026/04/15    18:33            895 V017__fix_accounts_payable_summary_mf_export_enabled.sql
-a---          2026/04/17    10:07           2203 V018__alter_accounts_receivable_summary_add_verification_fields.sql
-a---          2026/04/20    10:24           4791 V019__create_mf_integration_tables.sql
-a---          2026/04/20    14:30            460 V020__drop_mf_account_mapping.sql
-a---          2026/04/21     8:22           2043 V021__mf_oauth_state_and_token_uniq.sql
-a---          2026/04/21    10:41           2058 V022__create_mf_enum_translation.sql
-a---          2026/04/21    19:10            979 V023__alter_accounts_payable_summary_add_mf_transfer_date.sql
-a---          2026/04/22     8:44           1458 V024__alter_accounts_payable_summary_add_opening_balance.sql
-a---          2026/04/22    10:20           2079 V025__alter_accounts_payable_summary_add_payment_settled.sql
-a---          2026/04/23    14:56           2239 V026__alter_accounts_payable_summary_add_verified_tax_excluded.sql
-a---          2026/04/23    15:23           1726 V027__create_consistency_review.sql
-a---          2026/04/24    13:25           3262 V028__create_supplier_opening_balance.sql
-a---          2026/04/28     9:16           2649 V029__fix_delivery_mapping_smile_code.sql
-a---          2026/05/04    11:17           3112 V030__deprecate_old_payable_summary_jobs.sql
-a---          2026/05/04    12:55           8694 V031__create_t_invoice_and_m_partner_group.sql
-a---          2026/05/06     9:10           1975 V032__add_mf_tenant_binding.sql
-a---          2026/05/06    10:55           1151 V034__payment_mf_import_history_protection.sql
-a---          2026/05/06    15:31           1251 V035__document_verified_amount_invariant.sql
-a---          2026/05/06    13:56           2230 V036__finance_audit_log.sql
-a---          2026/05/06    14:49           3027 V037__add_oauth_encryption_version.sql
-a---          2026/05/06    15:31           2127 V038__extend_payment_mf_aux_rule_kind.sql
-a---          2026/05/06    15:31           2058 V039__update_verified_amount_comment.sql
-a---          2026/05/06    15:48           3132 V040__add_verification_source.sql
-a---          2026/05/06    18:56           2055 V041__create_offset_journal_rule.sql
-a---          2026/05/06    19:15           1280 V042__add_refresh_token_issued_at.sql

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/db/migration/V033__reencrypt_mf_oauth_secrets.java' in C:\project\odamitsu-data-hub
 succeeded in 5563ms:
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
 * <p>動作 (idempotent 化済 / C1 fix):
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
            int accessCount = reencrypt(conn, "t_mf_oauth_token", "id",
                    "access_token_enc", oldCipher, newCipher, tokenHasVersion);
            int refreshCount = reencrypt(conn, "t_mf_oauth_token", "id",
                    "refresh_token_enc", oldCipher, newCipher, tokenHasVersion);
            System.out.printf(
                    "[V033] OAuth secrets re-encrypted: client_secret=%d, access_token=%d, refresh_token=%d"
                            + " (clientHasVersion=%b, tokenHasVersion=%b)%n",
                    clientCount, accessCount, refreshCount, clientHasVersion, tokenHasVersion);
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
     * 指定テーブルの暗号化カラムを「旧鍵で decrypt → 新鍵で encrypt」して UPDATE する。
     * <p>
     * {@code hasVersionColumn=true} のときは {@code oauth_encryption_version=1} の行のみ処理し、
     * UPDATE で同時に {@code version=2} へ進める。version 列が無い旧環境では NULL 行以外を全件処理する
     * (V037 適用前のため version 概念が未導入)。
     * <p>
     * autoCommit ON 前提: 1 行 UPDATE = 即 commit。途中で例外発生してもそれまでの行は確定済み。
     *
     * @return 再暗号化した行数
     */
    private static int reencrypt(Connection conn, String table, String pkColumn, String column,
                                 TextEncryptor oldCipher, TextEncryptor newCipher,
                                 boolean hasVersionColumn) throws Exception {
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
                // 行単位 commit (autoCommit=true) → executeBatch ではなく即時 executeUpdate
                update.executeUpdate();
                count++;
            }
        }
        return count;
    }

    /** 指定テーブルに指定カラムが存在するか判定 (大文字小文字無視)。 */
    private static boolean columnExists(Connection conn, String table, String column) throws Exception {
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getColumns(null, null, table, column)) {
            if (rs.next()) return true;
        }
        // PostgreSQL identifier は通常 lower-case 格納。明示的に lower でも試す。
        try (ResultSet rs = md.getColumns(null, null, table.toLowerCase(), column.toLowerCase())) {
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
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/resources/db/migration/V037__add_oauth_encryption_version.sql' in C:\project\odamitsu-data-hub
 succeeded in 6766ms:
-- ============================================================
-- V037: MF OAuth 関連 _enc カラムに encryption version 列を追加
-- ============================================================
-- C1 (Codex 批判 Critical): V033 (Java migration) は旧鍵 → 新鍵への一括再暗号化を
-- 行う際、Flyway 履歴記録前にプロセス停止すると、暗号文は新鍵化済みなのに
-- V033 未適用扱い → 次回起動で旧鍵 decrypt 試行 → 復旧困難になっていた。
--
-- 本 migration で「行ごとの暗号化バージョン」を追跡できる列を追加し、
-- V033 を idempotent (途中再開可能) にする土台を提供する。
--
--   version=1: 旧鍵 (CryptoUtil / APP_CRYPTO_KEY+SALT) で暗号化
--   version=2: 新鍵 (OauthCryptoUtil / APP_CRYPTO_OAUTH_KEY+SALT) で暗号化
--
-- V033 が既に成功適用済の環境では、列追加後に「全行 version=2」とマークする
-- (DEFAULT 1 のままだと再実行時に旧鍵 decrypt 試行してしまうため)。
-- 新規環境では V033 → V037 の順で適用される: V033 は version 列が無いため
-- 「全行旧鍵」前提で動作 (version 列存在チェック付き)、その後 V037 が DEFAULT 1
-- で列追加 → V033 適用済マーカーで全行 2 に更新する。
--
-- @since 2026-05-04
-- ============================================================

ALTER TABLE m_mf_oauth_client
    ADD COLUMN IF NOT EXISTS oauth_encryption_version SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE t_mf_oauth_token
    ADD COLUMN IF NOT EXISTS oauth_encryption_version SMALLINT NOT NULL DEFAULT 1;

COMMENT ON COLUMN m_mf_oauth_client.oauth_encryption_version IS
    'OAuth 暗号化バージョン (1=旧鍵 APP_CRYPTO_KEY/SALT, 2=新鍵 APP_CRYPTO_OAUTH_KEY/SALT)。V033 idempotent 化用。';
COMMENT ON COLUMN t_mf_oauth_token.oauth_encryption_version IS
    'OAuth 暗号化バージョン (1=旧鍵 APP_CRYPTO_KEY/SALT, 2=新鍵 APP_CRYPTO_OAUTH_KEY/SALT)。V033 idempotent 化用。';

-- V033 既適用環境のマーカー: 全行を version=2 にセット
-- (V037 適用前の本番では V033 が一括再暗号化済み = 全行新鍵)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '033' AND success = true) THEN
        UPDATE m_mf_oauth_client SET oauth_encryption_version = 2;
        UPDATE t_mf_oauth_token SET oauth_encryption_version = 2;
        RAISE NOTICE 'V037: V033 適用済のため全行を oauth_encryption_version=2 にマークしました';
    ELSE
        RAISE NOTICE 'V037: V033 未適用 (新規環境 or V037 先行適用)。oauth_encryption_version=1 default のまま';
    END IF;
END $$;

-- 部分再暗号化時の partial scan 用 index (件数は少ないが filter 効率化のため)
CREATE INDEX IF NOT EXISTS idx_mf_oauth_client_enc_ver
    ON m_mf_oauth_client (oauth_encryption_version);
CREATE INDEX IF NOT EXISTS idx_mf_oauth_token_enc_ver
    ON t_mf_oauth_token (oauth_encryption_version);
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/MMfOauthClient.java; Get-Content backend/src/main/java/jp/co/oda32/domain/model/finance/TMfOauthToken.java; Get-Content backend/src/main/resources/db/migration/V032__add_mf_tenant_binding.sql; Get-Content backend/src/main/resources/db/migration/V042__add_refresh_token_issued_at.sql' in C:\project\odamitsu-data-hub
 succeeded in 3041ms:
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

    /**
     * G1-M4 (2026-05-06): refresh_token の実発行日。
     * <p>
     * MF token レスポンスに refresh_token が含まれていた場合 = rotation 動作時は {@code now()} を、
     * 含まれていなかった場合 = rotation OFF (旧 token 流用) 時は **旧 active row の値を継承する**。
     * <p>
     * これにより rotation OFF でも 540 日寿命カウントが add_date_time と乖離せず、
     * {@code MfOauthService.getStatus()} の残日数判定が rotation 設定に依らず正確になる。
     */
    @Column(name = "refresh_token_issued_at", nullable = false)
    private Timestamp refreshTokenIssuedAt;

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
-- G1-M4 (2026-05-06): refresh_token の真の発行日を持つカラムを追加。
-- rotation OFF (= MF レスポンスに refresh_token なし、旧 token 流用) でも
-- 新 row を insert する仕様 (persistToken の softDeleteActiveTokens + insert) のため、
-- add_date_time は実発行日と乖離する。本カラムは MF レスポンスに refresh_token があれば now()、
-- なければ旧 row 値を継承して、真の発行日 (= 540 日寿命の起点) を保持する。

ALTER TABLE t_mf_oauth_token
    ADD COLUMN IF NOT EXISTS refresh_token_issued_at TIMESTAMP;

-- backfill: 既存 row は add_date_time をコピー (= 旧仕様の挙動と一致)
UPDATE t_mf_oauth_token
SET refresh_token_issued_at = add_date_time
WHERE refresh_token_issued_at IS NULL;

-- backfill 完了後 NOT NULL 化
ALTER TABLE t_mf_oauth_token
    ALTER COLUMN refresh_token_issued_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_mf_oauth_token_refresh_issued
    ON t_mf_oauth_token (refresh_token_issued_at);

COMMENT ON COLUMN t_mf_oauth_token.refresh_token_issued_at IS
    'G1-M4 (2026-05-06): refresh_token の実発行日。rotation 動作時 = now()、'
    'rotation OFF (流用) 時 = 旧 row の値を継承。getStatus() の 540 日判定で使用。';
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/util/OauthCryptoUtil.java; Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java' in C:\project\odamitsu-data-hub
 succeeded in 3615ms:
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
    /** G1-M5: MF OAuth エンドポイント URL のホスト検証 (credential exfiltration 対策)。 */
    private final MfOauthHostAllowlist hostAllowlist;

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
        // G1-M5: OAuth endpoint host allowlist 検証。
        // 攻撃者制御 URL に client_secret を Basic auth で流す経路を遮断する。
        hostAllowlist.validate(requested.getAuthorizeUrl(), "authorizeUrl");
        hostAllowlist.validate(requested.getTokenUrl(), "tokenUrl");
        hostAllowlist.validate(requested.getApiBaseUrl(), "apiBaseUrl");
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
    @AuditLog(table = "t_mf_oauth_token", operation = "auth_callback",
            pkExpression = "{'userNo': #a2}",
            captureArgsAsAfter = true)
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

        // G1-M3: 未バインド client (P1-01 導入前の旧データ) は fresh パスでも強制 binding。
        // P1-01 は refresh 経路でだけ tenant 検証する設計だったため、access_token が fresh
        // な間は別会社 MF を誤認可していても業務 API が通ってしまう穴があった。
        // ここで /v2/tenant を呼んで mf_tenant_id を確定する。fetch 失敗時は
        // MfTenantBindingFailedException を throw して業務 API 全体を停止する (P1-01 と同じ厳格動作)。
        if (snap.client.getMfTenantId() == null && snap.isFresh(REFRESH_MARGIN_SECONDS)) {
            log.info("MF client.mf_tenant_id IS NULL: 強制 tenant binding を実行 clientId={}",
                    snap.client.getId());
            MfTenantResponse tenant;
            try {
                tenant = mfApiClient.getTenant(snap.client, snap.accessToken);
            } catch (MfTenantBindingFailedException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new MfTenantBindingFailedException(
                        "未バインド client の tenant 取得に失敗しました clientId=" + snap.client.getId(), e);
            }
            // tx 内で binding 確定 (token は触らない、既に有効)
            self.bindTenantInNewTx(snap.client.getId(), tenant);
            // bind 完了。access_token 自体は変わっていないので snap の値をそのまま返却。
            return snap.accessToken;
        }

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

    /**
     * G1-M3: token は変更せず tenant binding のみ確定する (未バインド旧 client の救済)。
     * <p>
     * fresh access_token を保持したまま、{@code /v2/tenant} 観測値を client に書き込む。
     * 既存 binding がある場合 (= 別 thread が先に binding 確定済) は
     * {@link #bindOrVerifyTenant} の一致検証ロジックで観測 tenant と突合し、
     * 不一致なら {@link MfTenantMismatchException} で rollback する。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void bindTenantInNewTx(Integer clientId, MfTenantResponse tenant) {
        MMfOauthClient client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が消えています: " + clientId));
        // userNo は system 由来 (= null) で記録。bindOrVerifyTenant 内で modify_user_no に直接 set するので
        // null 許容。手動 callback (handleCallback / persistTokenAndTenantInNewTx) は userNo を渡すため区別される。
        bindOrVerifyTenant(client, tenant, null);
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
                    List.of(), List.of(), true, false);
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
                    scopeAnalysis.missing(), scopeAnalysis.extra(), scopeOk, false);
        }
        TMfOauthToken t = token.get();
        Instant exp = t.getExpiresAt().toInstant();
        Instant lastRefreshed = t.getModifyDateTime() != null
                ? t.getModifyDateTime().toInstant()
                : t.getAddDateTime().toInstant();
        // G1-M4 (2026-05-06): add_date_time でなく refresh_token_issued_at を使用する。
        // rotation OFF (= MF レスポンスに refresh_token が無く旧 token を流用) の場合、
        // softDeleteActiveTokens + 新 row insert で add_date_time は毎回更新されるため
        // 540 日寿命の起点として誤検知 (残日数過大評価) していた。
        // 旧 row の値を継承する refresh_token_issued_at を使うことで rotation 設定に依らず正確になる。
        // 旧データ救済として NULL fallback で add_date_time を残す (V042 backfill で実質発生しない想定)。
        Instant refreshTokenIssuedAt = t.getRefreshTokenIssuedAt() != null
                ? t.getRefreshTokenIssuedAt().toInstant()
                : (t.getAddDateTime() != null ? t.getAddDateTime().toInstant() : null);
        Integer daysUntilReauth = null;
        boolean reAuthRequired = false;
        boolean reAuthExpired = false;
        if (refreshTokenIssuedAt != null) {
            long elapsedDays = ChronoUnit.DAYS.between(refreshTokenIssuedAt, Instant.now());
            long remaining = REFRESH_TOKEN_LIFETIME_DAYS - elapsedDays;
            if (remaining < 0) {
                // G1-M4: 540 日超過 = 期限切れ。再認可必須 (UI では最高 severity の destructive banner で表示)。
                reAuthExpired = true;
                reAuthRequired = true;
                // UI 表示用に 0 にクランプ (負値表示は不自然)。
                daysUntilReauth = 0;
            } else {
                // int 安全キャスト (540 日基準で int の範囲を逸脱しない)
                daysUntilReauth = (int) Math.max(Math.min(remaining, Integer.MAX_VALUE), Integer.MIN_VALUE);
                reAuthRequired = daysUntilReauth <= 0;
            }
        }
        return new MfTokenStatus(true, true, exp, t.getScope(), lastRefreshed, reAuthRequired,
                c.getMfTenantId(), c.getMfTenantName(), boundAt,
                refreshTokenIssuedAt, daysUntilReauth,
                scopeAnalysis.missing(), scopeAnalysis.extra(), scopeOk, reAuthExpired);
    }

    /**
     * 接続切断。DB トークンを論理削除し、tenant binding もクリアする
     * (P1-01: 別 MF tenant への再認可を許可するため)。MF 側 revoke は現時点でベストエフォート省略。
     */
    @Transactional
    @AuditLog(table = "t_mf_oauth_token", operation = "revoke",
            pkExpression = "{'userNo': #a0}",
            captureArgsAsAfter = true)
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
        // G1-M4: refresh_token_issued_at の継承元としても旧 row を参照するので、ここで一括取得する。
        Optional<TMfOauthToken> previousToken = tokenRepository
                .findFirstByClientIdAndDelFlgOrderByIdDesc(client.getId(), "0");
        String refreshTokenEnc;
        if (res.refreshToken() != null) {
            refreshTokenEnc = cryptoUtil.encrypt(res.refreshToken());
        } else {
            refreshTokenEnc = previousToken
                    .map(TMfOauthToken::getRefreshTokenEnc)
                    .orElseThrow(() -> new IllegalStateException(
                            "MF token レスポンスに refresh_token が無く、再利用可能な既存 token も存在しません"));
        }
        // G1-M4: refresh_token の真の発行日を判定。
        //  - rotation 動作 (新 refresh_token 受領) → 今を発行日とする
        //  - rotation OFF (旧 refresh_token 流用) → 旧 row の値を継承
        //  - 旧 row なし (= 初回認可) → now (rotation 有無に関わらず安全な fallback)
        Timestamp refreshTokenIssuedAt;
        if (res.refreshToken() != null) {
            refreshTokenIssuedAt = now;
        } else {
            refreshTokenIssuedAt = previousToken
                    .map(TMfOauthToken::getRefreshTokenIssuedAt)
                    .orElse(now);
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
                .refreshTokenIssuedAt(refreshTokenIssuedAt)
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

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java' in C:\project\odamitsu-data-hub
 succeeded in 3965ms:
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

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfTokenStatus.java; Get-Content frontend/types/mf-integration.ts; Get-Content frontend/components/pages/finance/mf-integration.tsx' in C:\project\odamitsu-data-hub
 succeeded in 3623ms:
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
 * <p>
 * G1-M4 (2026-05-06): {@code reAuthExpired} を追加。{@code refresh_token_issued_at} 起点で
 * 540 日寿命を超過した場合 true。{@code reAuthRequired} は {@code remaining ? 0} で立つが、
 * {@code reAuthExpired} は **負値 (= 既に超過)** の状態のみを示す。UI banner で最上位 severity
 * (期限超過、destructive banner) を出すために独立フラグとして公開する。
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
        @JsonProperty("refreshTokenIssuedAt") Instant refreshTokenIssuedAt, // refresh_token の真の発行日 (G1-M4)
        @JsonProperty("daysUntilReauth") Integer daysUntilReauth,           // 540 日 - 経過日数 (P1-04、未接続時 null、超過時は 0 にクランプ)
        List<String> missingScopes, // T6: 必須だが scope に含まれていないもの (空 list = OK)
        List<String> extraScopes,   // T6: scope に含まれているが必須ではないもの (空 list = OK、警告レベル)
        @JsonProperty("scopeOk") boolean scopeOk, // T6: missingScopes.isEmpty() の short-hand (UI 判定用)
        @JsonProperty("reAuthExpired") boolean reAuthExpired // G1-M4: 540 日超過 = 期限超過の明示フラグ (UI 最上位 severity)
) {}
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
  /**
   * refresh_token の真の発行日 ISO-8601 (G1-M4 / P1-04 案 α)。
   * <p>
   * rotation 動作時 = 現 active row の発行日、rotation OFF (旧 token 流用) 時 = 旧 row の値を継承。
   * これにより rotation 設定に依らず 540 日寿命カウントが正確になる。
   */
  refreshTokenIssuedAt: string | null
  /**
   * 540 日 - 経過日数。? 0 で失効、? 60 で予兆 banner 表示 (P1-04 案 α)。
   * 期限超過時は 0 にクランプ (`reAuthExpired=true` で識別)。未接続時 null。
   */
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
  /**
   * G1-M4 (2026-05-06): refresh_token が 540 日寿命を超過した = 既に再認可必須の状態。
   * `MfReAuthBanner` で最上位 severity (destructive、期限超過メッセージ) として表示する独立フラグ。
   * `reAuthRequired` は ? 0 で立つが、`reAuthExpired` は **負値 (= 既に超過)** の状態のみを示す。
   */
  reAuthExpired: boolean
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
'use client'

import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '@/lib/api-client'
import { handleApiError } from '@/lib/api-error-handler'
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
    onError: (e) => handleApiError(e, { fallbackMessage: '保存に失敗しました' }),
  })

  const connectMutation = useMutation({
    mutationFn: () => api.get<{ url: string }>('/finance/mf-integration/oauth/authorize-url'),
    onSuccess: (res) => {
      // SF-05: 新タブで MF 認可ページを開く。
      // BroadcastChannel 経由で通知するため window.opener は不要 → noopener,noreferrer でセキュリティ強化。
      window.open(res.url, '_blank', 'noopener,noreferrer')
      toast.info('MF 認可ページを新しいタブで開きました。完了後、このタブに自動反映されます。')
    },
    onError: (e) => {
      // 422: クライアント設定が未登録 (= 業務メッセージとして固定文言で誘導)
      if (e instanceof ApiError && e.status === 422) {
        toast.error('クライアント設定が未登録です。先に Client ID/Secret を保存してください。')
        return
      }
      // それ以外は MF_HOST_NOT_ALLOWED 等を含めて handleApiError へ委譲
      handleApiError(e, { fallbackMessage: 'MF 認可 URL の取得に失敗しました' })
    },
  })

  const revokeMutation = useMutation({
    mutationFn: () => api.post<MfTokenStatus>('/finance/mf-integration/oauth/revoke'),
    onSuccess: () => {
      toast.success('MF 連携を切断しました')
      queryClient.invalidateQueries({ queryKey: ['mf-oauth-status'] })
    },
    onError: (e) => handleApiError(e, { fallbackMessage: '切断に失敗しました' }),
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
      handleApiError(e, { fallbackMessage: `${kind} 取得失敗` })
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
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationDebugController.java' in C:\project\odamitsu-data-hub
 succeeded in 5038ms:
package jp.co.oda32.api.finance;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.smile.TSmilePayment;
import jp.co.oda32.domain.repository.smile.TSmilePaymentRepository;
import jp.co.oda32.domain.service.finance.mf.MfApiClient;
import jp.co.oda32.domain.service.finance.mf.MfDebugApiClient;
import jp.co.oda32.domain.service.finance.mf.MfJournal;
import jp.co.oda32.domain.service.finance.mf.MfJournalsResponse;
import jp.co.oda32.domain.service.finance.mf.MfOauthService;
import jp.co.oda32.domain.service.finance.mf.MfReAuthRequiredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * SF-13: MF 連携の診断用 endpoint 群 (dev/test プロファイル限定)。
 * <p>
 * 旧 {@link MfIntegrationController} の {@code /debug/*} 群を移動。
 * {@code @Profile} で dev/test のみ Bean 化されるため prod では Spring が認識せず 404 になる
 * (旧実装の各 endpoint 内 {@code isDevProfile()} ガードは不要に)。
 *
 * @since 2026-05-04 (SF-13)
 */
@Slf4j
@Profile({"dev", "test"})
@RestController
@RequestMapping("/api/v1/finance/mf-integration/debug")
@PreAuthorize("@loginUserSecurityBean.isAdmin()")
@RequiredArgsConstructor
public class MfIntegrationDebugController {

    private final MfOauthService mfOauthService;
    private final MfApiClient mfApiClient;
    private final MfDebugApiClient mfDebugApiClient;
    private final TSmilePaymentRepository tSmilePaymentRepository;

    @GetMapping("/accounts-raw")
    public ResponseEntity<?> debugAccountsRaw() {
        return fetchRawWithFirstN("/api/v3/accounts", 3);
    }

    /**
     * t_smile_payment を voucher_date 範囲で集計する診断 endpoint (Phase B'' 遡及充填の前準備)。
     * 月×仕入先コード単位で payment_amount を合計して返す。
     */
    @GetMapping("/smile-payment-monthly")
    public ResponseEntity<?> debugSmilePaymentMonthly(
            @RequestParam("fromDate")
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam("toDate")
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate toDate) {
        List<TSmilePayment> list = tSmilePaymentRepository.findByVoucherDateBetween(fromDate, toDate);
        Map<String, BigDecimal> byMonthTotal = new TreeMap<>();
        Map<String, Integer> byMonthCount = new TreeMap<>();
        Map<String, Map<String, BigDecimal>> byMonthSupplier = new TreeMap<>();
        for (TSmilePayment p : list) {
            if (p.getVoucherDate() == null) continue;
            String ym = p.getVoucherDate().toString().substring(0, 7);
            byMonthTotal.merge(ym,
                    p.getPaymentAmount() != null ? p.getPaymentAmount() : BigDecimal.ZERO,
                    BigDecimal::add);
            byMonthCount.merge(ym, 1, Integer::sum);
            byMonthSupplier.computeIfAbsent(ym, k -> new HashMap<>())
                    .merge(p.getSupplierCode() != null ? p.getSupplierCode() : "",
                            p.getPaymentAmount() != null ? p.getPaymentAmount() : BigDecimal.ZERO,
                            BigDecimal::add);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRows", list.size());
        result.put("byMonth", byMonthTotal.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("month", e.getKey());
                    m.put("count", byMonthCount.get(e.getKey()));
                    m.put("totalAmount", e.getValue());
                    m.put("supplierCount", byMonthSupplier.get(e.getKey()).size());
                    return m;
                }).toList());
        return ResponseEntity.ok(result);
    }

    /**
     * 貸借対照表 試算表 API の生レスポンスを返す（Phase 0 スパイク用）。
     * 関連設計書: claudedocs/design-supplier-partner-ledger-balance.md §3.0
     *
     * <p>MF 側の必須 query は仕様未確定のため任意 param を透過で渡す。
     * 代表的な呼び方:
     * <pre>
     *   GET /debug/trial-balance-raw?from=2026-02-21&to=2026-03-20
     *   GET /debug/trial-balance-raw?period=2026-03-20
     * </pre>
     * 403 は scope 不足 (mfc/accounting/report.read 未取得) を意味するため
     * UI 再認可を促すメッセージで返す。
     */
    @GetMapping("/trial-balance-raw")
    public ResponseEntity<?> debugTrialBalanceRaw(@RequestParam Map<String, String> params) {
        String query = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + java.net.URLEncoder.encode(
                        e.getValue(), java.nio.charset.StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
        String path = "/api/v3/reports/trial_balance_bs" + (query.isEmpty() ? "" : "?" + query);
        try {
            MMfOauthClient client = mfOauthService.findActiveClient()
                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
            String accessToken = mfOauthService.getValidAccessToken();
            JsonNode raw = mfDebugApiClient.getRaw(client, accessToken, path);
            return ResponseEntity.ok(raw);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 403) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "message", "scope 不足です。frontend/types/mf-integration.ts に"
                                + " mfc/accounting/report.read を追加済みの状態で、"
                                + "/finance/mf-integration 画面から「再認証」してください。"));
            }
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("message", "MF API error"));
        }
    }

    @GetMapping("/taxes-raw")
    public ResponseEntity<?> debugTaxesRaw() {
        return fetchRawWithFirstN("/api/v3/taxes", 5);
    }

    /**
     * 指定取引月の MF 仕訳を最初 3 件だけ返す（shape 調査用）。
     * 例: /debug/journals-raw?transactionMonth=2026-03-20
     */
    @GetMapping("/journals-raw")
    public ResponseEntity<?> debugJournalsRaw(
            @RequestParam("transactionMonth") String transactionMonth) {
        String path = "/api/v3/journals?start_date=" + transactionMonth
                + "&end_date=" + transactionMonth
                + "&per_page=3";
        return fetchRawWithFirstN(path, 3);
    }

    /**
     * 指定勘定科目の全 branch を期間内で列挙し、journal 単位で credit/debit の釣り合いを判定する。
     * 不一致 journal が「ペアになっていない仕訳」を含む journal。
     * 例: /debug/account-journals?accountName=仕入資金複合&startDate=2026-02-21&endDate=2026-03-20
     */
    @GetMapping("/account-journals")
    public ResponseEntity<?> debugAccountJournals(
            @RequestParam("accountName") String accountName,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        MMfOauthClient client = mfOauthService.findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        String accessToken = mfOauthService.getValidAccessToken();

        List<Map<String, Object>> balancedJournals = new ArrayList<>();
        List<Map<String, Object>> unbalancedJournals = new ArrayList<>();

        int page = 1;
        final int perPage = 1000;
        while (true) {
            MfJournalsResponse res = mfApiClient.listJournals(client, accessToken, startDate, endDate, page, perPage);
            List<MfJournal> journals = res.items();
            if (journals.isEmpty()) break;
            for (MfJournal j : journals) {
                if (j.branches() == null) continue;
                BigDecimal deb = BigDecimal.ZERO;
                BigDecimal cre = BigDecimal.ZERO;
                List<Map<String, Object>> hitBranches = new ArrayList<>();
                for (MfJournal.MfBranch b : j.branches()) {
                    MfJournal.MfSide d = b.debitor();
                    MfJournal.MfSide c = b.creditor();
                    boolean hit = false;
                    if (d != null && accountName.equals(d.accountName())) {
                        if (d.value() != null) deb = deb.add(d.value());
                        hit = true;
                    }
                    if (c != null && accountName.equals(c.accountName())) {
                        if (c.value() != null) cre = cre.add(c.value());
                        hit = true;
                    }
                    if (hit) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("debitAccount", d != null ? d.accountName() : null);
                        row.put("debitSub", d != null ? d.subAccountName() : null);
                        row.put("debitValue", d != null ? d.value() : null);
                        row.put("creditAccount", c != null ? c.accountName() : null);
                        row.put("creditSub", c != null ? c.subAccountName() : null);
                        row.put("creditValue", c != null ? c.value() : null);
                        row.put("remark", b.remark());
                        hitBranches.add(row);
                    }
                }
                if (hitBranches.isEmpty()) continue;
                BigDecimal diff = cre.subtract(deb);
                Map<String, Object> jEntry = new LinkedHashMap<>();
                jEntry.put("journalNumber", j.number());
                jEntry.put("transactionDate", j.transactionDate() != null ? j.transactionDate().toString() : null);
                jEntry.put("memo", j.memo());
                jEntry.put("debitSum", deb);
                jEntry.put("creditSum", cre);
                jEntry.put("diff", diff);
                jEntry.put("branches", hitBranches);
                if (diff.signum() == 0) balancedJournals.add(jEntry);
                else unbalancedJournals.add(jEntry);
            }
            if (journals.size() < perPage) break;
            page++;
        }

        return ResponseEntity.ok(Map.of(
                "accountName", accountName,
                "startDate", startDate,
                "endDate", endDate,
                "balancedJournalCount", balancedJournals.size(),
                "unbalancedJournalCount", unbalancedJournals.size(),
                "unbalancedJournals", unbalancedJournals,
                "balancedSamples", balancedJournals.stream().limit(3).toList()
        ));
    }

    /**
     * 指定勘定科目の残高推移を計算する（/journals 全件走査）。
     * 例: /debug/account-trend?accountName=仕入資金複合&startDate=2025-10-01&endDate=2026-02-20
     */
    @GetMapping("/account-trend")
    public ResponseEntity<?> debugAccountTrend(
            @RequestParam("accountName") String accountName,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        MMfOauthClient client = mfOauthService.findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        String accessToken = mfOauthService.getValidAccessToken();

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        TreeMap<LocalDate, BigDecimal[]> daily = new TreeMap<>();
        int hitCount = 0;

        int page = 1;
        final int perPage = 1000;
        while (true) {
            MfJournalsResponse res = mfApiClient.listJournals(client, accessToken, startDate, endDate, page, perPage);
            List<MfJournal> journals = res.items();
            if (journals.isEmpty()) break;
            for (MfJournal j : journals) {
                if (j.branches() == null) continue;
                for (MfJournal.MfBranch b : j.branches()) {
                    MfJournal.MfSide deb = b.debitor();
                    MfJournal.MfSide cre = b.creditor();
                    boolean hit = false;
                    BigDecimal dv = BigDecimal.ZERO;
                    BigDecimal cv = BigDecimal.ZERO;
                    if (deb != null && accountName.equals(deb.accountName())) {
                        if (deb.value() != null) {
                            dv = deb.value();
                            totalDebit = totalDebit.add(dv);
                        }
                        hit = true;
                    }
                    if (cre != null && accountName.equals(cre.accountName())) {
                        if (cre.value() != null) {
                            cv = cre.value();
                            totalCredit = totalCredit.add(cv);
                        }
                        hit = true;
                    }
                    if (hit) {
                        hitCount++;
                        LocalDate d = j.transactionDate();
                        if (d != null) {
                            BigDecimal[] arr = daily.computeIfAbsent(d,
                                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                            arr[0] = arr[0].add(dv);
                            arr[1] = arr[1].add(cv);
                        }
                    }
                }
            }
            if (journals.size() < perPage) break;
            page++;
        }

        List<Map<String, Object>> trend = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, BigDecimal[]> e : daily.entrySet()) {
            BigDecimal d = e.getValue()[0];
            BigDecimal c = e.getValue()[1];
            running = running.add(c).subtract(d);
            trend.add(Map.of(
                    "date", e.getKey().toString(),
                    "debit", d,
                    "credit", c,
                    "runningBalance", running
            ));
        }

        return ResponseEntity.ok(Map.of(
                "accountName", accountName,
                "startDate", startDate,
                "endDate", endDate,
                "hitBranchCount", hitCount,
                "totalDebit", totalDebit,
                "totalCredit", totalCredit,
                "balanceAsLiability", totalCredit.subtract(totalDebit),
                "balanceAsAsset", totalDebit.subtract(totalCredit),
                "trend", trend
        ));
    }

    private ResponseEntity<?> fetchRawWithFirstN(String path, int sampleSize) {
        try {
            MMfOauthClient client = mfOauthService.findActiveClient()
                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
            String accessToken = mfOauthService.getValidAccessToken();
            JsonNode raw = mfDebugApiClient.getRaw(client, accessToken, path);
            return ResponseEntity.ok(trimToSample(raw, sampleSize));
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * レスポンスが配列 or {data:[...]} / {accounts:[...]} 等のいずれでも最初 N 件だけ残す。
     * shape 調査が目的なので wrapper と中身の両方が見えるように。
     */
    private JsonNode trimToSample(JsonNode raw, int n) {
        if (raw == null) return null;
        if (raw.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode arr =
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            for (int i = 0; i < Math.min(n, raw.size()); i++) arr.add(raw.get(i));
            return arr;
        }
        if (raw.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode copy = raw.deepCopy();
            raw.fieldNames().forEachRemaining(field -> {
                JsonNode v = copy.get(field);
                if (v != null && v.isArray()) {
                    com.fasterxml.jackson.databind.node.ArrayNode arr =
                            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
                    for (int i = 0; i < Math.min(n, v.size()); i++) arr.add(v.get(i));
                    copy.set(field, arr);
                }
            });
            return copy;
        }
        return raw;
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command Get-ChildItem in C:\project\odamitsu-data-hub
 succeeded in 4515ms:

    Directory: C:\project\odamitsu-data-hub

Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
d----          2026/05/06    16:44                .claude
d----          2026/05/06    19:35                .github
d----          2026/04/21    19:52                .idea
d----          2026/05/06    19:43                backend
d----          2026/04/16    17:55                backup
d----          2026/05/06    19:57                claudedocs
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
-a---          2026/04/14    18:49           3554 CLAUDE.md
-a---          2026/05/06    19:36            953 README.md

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem backend' in C:\project\odamitsu-data-hub
 succeeded in 7250ms:

    Directory: C:\project\odamitsu-data-hub\backend

Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
d----          2026/02/24     9:04                .gradle
d----          2026/05/06    19:43                build
d----          2026/03/31    19:44                claudedocs
d----          2026/02/24     8:18                gradle
d----          2026/05/06    10:25                scripts
d----          2026/02/24     8:18                src
-a---          2026/04/23    11:39          55866 _int_after.json
-a---          2026/04/23    11:30          56491 _int.json
-a---          2026/04/23    11:42          56489 _int11m.json
-a---          2026/04/23    11:42          26245 _int6m.json
-a---          2026/04/23    11:37          32188 _mfj_6445.json
-a---          2026/04/23    11:47          20136 _sb_2510.json
-a---          2026/05/06    15:32          38282 bootRun-migrate.log
-a---          2026/05/06    15:57          37367 bootRun-v040.log
-a---          2026/05/06    19:41          37807 bootRun-v042.log
-a---          2026/04/20     8:45           2829 build.gradle
-a---          2026/02/24     8:19           5296 gradlew
-a---          2026/02/24     8:19           2260 gradlew.bat
-a---          2026/02/24     8:19             39 settings.gradle
-a---          2026/05/06    19:44           8779 test-after-major.log
-a---          2026/05/06    15:33            541 test-full.log

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/api/finance/FinanceExceptionHandler.java' in C:\project\odamitsu-data-hub
 succeeded in 7554ms:
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

2026-05-06T11:00:41.719553Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$i=0; Get-Content backend/src/main/resources/db/migration/V037__add_oauth_encryption_version.sql | ForEach-Object { $i++; if($i -ge 27 -and $i -le 35){ '"'{0,4}: {1}' -f "'$i, $_ }}'` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$i=0; Get-Content backend/src/main/resources/db/migration/V037__add_oauth_encryption_version.sql | ForEach-Object { $i++; if($i -ge 27 -and $i -le 35){ '"'{0,4}: {1}' -f "'$i, $_ }}' in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command '$i=0; Get-Content backend/src/main/resources/db/migration/V037__add_oauth_encryption_version.sql | ForEach-Object { $i++; if($i -ge 27 -and $i -le 35){ '"'{0,4}: {1}' -f "'$i, $_ }}'` rejected: blocked by policy
2026-05-06T11:00:46.355619Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "@'
from pathlib import Path
p=Path('backend/src/main/resources/db/migration/V037__add_oauth_encryption_version.sql')
for i,line in enumerate(p.read_text(encoding='utf-8').splitlines(),1):
    if 25<=i<=36:
        print(f'{i}: {line}')
'@ | python -"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "@'
from pathlib import Path
p=Path('backend/src/main/resources/db/migration/V037__add_oauth_encryption_version.sql')
for i,line in enumerate(p.read_text(encoding='utf-8').splitlines(),1):
    if 25<=i<=36:
        print(f'{i}: {line}')
'@ | python -" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "@'
from pathlib import Path
p=Path('backend/src/main/resources/db/migration/V037__add_oauth_encryption_version.sql')
for i,line in enumerate(p.read_text(encoding='utf-8').splitlines(),1):
    if 25<=i<=36:
        print(f'{i}: {line}')
'@ | python -"` rejected: blocked by policy
2026-05-06T11:00:49.925865Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "python -c \"from pathlib import Path; lines=Path('backend/src/main/resources/db/migration/V037__add_oauth_encryption_version.sql').read_text(encoding='utf-8').splitlines(); [print(f'{i}: {line}') for i,line in enumerate(lines,1) if 25<=i<=36]\""` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "python -c \"from pathlib import Path; lines=Path('backend/src/main/resources/db/migration/V037__add_oauth_encryption_version.sql').read_text(encoding='utf-8').splitlines(); [print(f'{i}: {line}') for i,line in enumerate(lines,1) if 25<=i<=36]\"" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "python -c \"from pathlib import Path; lines=Path('backend/src/main/resources/db/migration/V037__add_oauth_encryption_version.sql').read_text(encoding='utf-8').splitlines(); [print(f'{i}: {line}') for i,line in enumerate(lines,1) if 25<=i<=36]\""` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n \"ALTER TABLE\" backend\\src\\main\\resources\\db\\migration\\V037__add_oauth_encryption_version.sql" in C:\project\odamitsu-data-hub
 succeeded in 1566ms:
23:ALTER TABLE m_mf_oauth_client
25:ALTER TABLE t_mf_oauth_token
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthHostAllowlist.java' in C:\project\odamitsu-data-hub
 succeeded in 1746ms:
package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.exception.FinanceBusinessException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * MF OAuth エンドポイント URL のホスト allowlist (G1-M5)。
 * <p>admin が {@code authorizeUrl} / {@code tokenUrl} / {@code apiBaseUrl} を自由入力できる従来仕様では、
 * 攻撃者制御の URL に client_secret を Basic auth で送信する経路 (credential exfiltration) が
 * 残っていた。本クラスで MF 公式ホスト + dev 用 localhost のみ許可する。
 *
 * <h3>許可ホスト (production)</h3>
 * <ul>
 *   <li>{@code api.biz.moneyforward.com} (authorize / token)</li>
 *   <li>{@code api-accounting.moneyforward.com} (api base)</li>
 * </ul>
 *
 * <h3>dev profile 追加許可</h3>
 * <ul>
 *   <li>{@code localhost} / {@code 127.0.0.1} ? テスト用 mock OAuth サーバー想定。
 *       prod では絶対に許可しない (profile=dev/test のみ)。</li>
 * </ul>
 *
 * <p>scheme は https 必須。dev profile かつ localhost ホストのみ http を許可する
 * (mock OAuth サーバーは TLS なしで起動するケースが多いため)。
 *
 * @since 2026-05-06 (G1-M5)
 */
@Component
@Log4j2
public class MfOauthHostAllowlist {

    /** MF_HOST_NOT_ALLOWED エラーコード (FinanceExceptionHandler で 400 にマップされる)。 */
    public static final String ERROR_CODE = "MF_HOST_NOT_ALLOWED";

    /** prod でも常に許可される MF 公式ホスト。 */
    private static final Set<String> PRODUCTION_HOSTS = Set.of(
            "api.biz.moneyforward.com",
            "api-accounting.moneyforward.com"
    );

    /** dev profile でのみ追加許可されるホスト。 */
    private static final Set<String> DEV_HOSTS = Set.of(
            "localhost",
            "127.0.0.1"
    );

    private final boolean devProfile;

    public MfOauthHostAllowlist(@Value("${spring.profiles.active:}") String activeProfiles) {
        this.devProfile = activeProfiles != null
                && (activeProfiles.contains("dev") || activeProfiles.contains("test"));
        log.info("MF OAuth allowlist 初期化: prodHosts={}, devHostsEnabled={} (activeProfiles={})",
                PRODUCTION_HOSTS, this.devProfile, activeProfiles);
    }

    /**
     * URL のホストが allowlist に含まれるか検証する。
     *
     * @param url       検証対象 URL (authorizeUrl / tokenUrl / apiBaseUrl)
     * @param fieldName エラーメッセージに含めるフィールド名
     * @throws FinanceBusinessException 違反時 ({@link #ERROR_CODE})
     */
    public void validate(String url, String fieldName) {
        if (url == null || url.isBlank()) {
            throw new FinanceBusinessException(fieldName + " は必須です。", ERROR_CODE);
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new FinanceBusinessException(
                    fieldName + " は有効な URL ではありません: " + url, ERROR_CODE);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            throw new FinanceBusinessException(
                    fieldName + " の scheme/host が解釈できません: " + url, ERROR_CODE);
        }
        // scheme: https 必須。dev profile かつ localhost に限り http も許可。
        if (!"https".equalsIgnoreCase(scheme)) {
            boolean isDevLocalhost = devProfile
                    && DEV_HOSTS.contains(host)
                    && "http".equalsIgnoreCase(scheme);
            if (!isDevLocalhost) {
                throw new FinanceBusinessException(
                        fieldName + " は https のみ許可されます: " + url, ERROR_CODE);
            }
        }
        // host: allowlist に含まれるか
        if (PRODUCTION_HOSTS.contains(host)) {
            return;
        }
        if (devProfile && DEV_HOSTS.contains(host)) {
            return;
        }
        throw new FinanceBusinessException(
                fieldName + " のホストは許可されていません: " + host
                        + " (allowlist: " + allowlistDescription() + ")",
                ERROR_CODE);
    }

    private String allowlistDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(", ", PRODUCTION_HOSTS));
        if (devProfile) {
            sb.append(" + dev: ").append(String.join(", ", DEV_HOSTS));
        }
        return sb.toString();
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/resources/config/application-dev.yml; Get-Content backend/src/main/resources/config/application-prod.yml' in C:\project\odamitsu-data-hub
 succeeded in 1695ms:
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
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n \"ALLOWED_REDIRECT_URIS localhost odamitsu\" backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\mf\\MfOauthService.java frontend\\types\\mf-integration.ts backend\\src\\main\\resources\\config\\application-prod.yml" in C:\project\odamitsu-data-hub
 succeeded in 1349ms:
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:48:    private static final Set<String> ALLOWED_REDIRECT_URIS = Set.of(
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:49:            "http://localhost:3000/finance/mf-integration/callback",
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:50:            "https://odamitsu-data-hub.local/finance/mf-integration/callback"
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:511:        if (!ALLOWED_REDIRECT_URIS.contains(uri)) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:513:                    "redirect_uri が許可リストにありません。許可済み: " + ALLOWED_REDIRECT_URIS);
backend\src\main\resources\config\application-prod.yml:3:    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:55544}/${DB_NAME:oda32db}
backend\src\main\resources\config\application-prod.yml:38:  allowed-origins: ${CORS_ORIGINS:https://odamitsu.example.com}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content frontend/package.json' in C:\project\odamitsu-data-hub
 succeeded in 1618ms:
{
  "name": "frontend",
  "private": true,
  "version": "0.1.0",
  "scripts": {
    "dev": "next dev -H 0.0.0.0",
    "build": "next build",
    "start": "next start",
    "lint": "next lint"
  },
  "dependencies": {
    "@hookform/resolvers": "^5.2.2",
    "@tanstack/react-query": "^5.90.21",
    "class-variance-authority": "^0.7.1",
    "clsx": "^2.1.1",
    "cmdk": "^1.1.1",
    "lucide-react": "^0.575.0",
    "next": "^16.1.6",
    "next-themes": "^0.4.6",
    "radix-ui": "^1.4.3",
    "react": "^19.2.4",
    "react-dom": "^19.2.4",
    "react-hook-form": "^7.71.2",
    "recharts": "^3.7.0",
    "sonner": "^2.0.7",
    "tailwind-merge": "^3.5.0",
    "zod": "^4.3.6"
  },
  "devDependencies": {
    "@playwright/test": "^1.58.2",
    "@tailwindcss/postcss": "^4.2.1",
    "@types/node": "^25.3.0",
    "@types/react": "^19.2.14",
    "@types/react-dom": "^19.2.3",
    "eslint": "^9.39.3",
    "eslint-config-next": "^16.1.6",
    "tailwindcss": "^4.2.1",
    "typescript": "^5.9.3"
  }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfScopeConstants.java; Get-Content frontend/components/common/MfScopeBanner.tsx; Get-Content frontend/components/common/MfReAuthBanner.tsx' in C:\project\odamitsu-data-hub
 succeeded in 1616ms:
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
  // G1-M4: 期限超過 (540 日寿命を超過済) は最上位 severity で先に判定する。
  // 通常の severity 分岐は daysUntilReauth ? 60 の範囲のみ扱う。
  if (!data.reAuthExpired && (data.daysUntilReauth == null || data.daysUntilReauth > THRESHOLD_60)) {
    return null
  }

  const days = data.daysUntilReauth ?? 0

  // severity 判定
  let variant: 'default' | 'destructive' = 'default'
  let className = ''
  let title = ''
  let message = ''

  if (data.reAuthExpired) {
    // G1-M4: 540 日寿命を超過済。最上位 severity (destructive + pulse) で再認可を強制する。
    variant = 'destructive'
    className = 'animate-pulse'
    title = 'MF refresh_token 期限超過、再認可必須'
    message =
      'refresh_token の 540 日寿命を超過しました。MF 連携が機能しません。直ちに再認可してください。'
  } else if (days <= 0) {
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

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n \"/oauth/status status\" backend\\src\\main\\java\\jp\\co\\oda32\\api\\finance\\MfIntegrationController.java" in C:\project\odamitsu-data-hub
2026-05-06T11:01:33.511236Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 1.3 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
FINDSTR: /a は無視されました。
FINDSTR: /u は無視されました。
FINDSTR: /t は無視されました。
FINDSTR: /h は無視されました。
FINDSTR: // は無視されました。
FINDSTR: /t は無視されました。
FINDSTR: /a は無視されました。
FINDSTR: /t は無視されました。
FINDSTR: /u は無視されました。
FINDSTR: /  は無視されました。
FINDSTR: /t は無視されました。
FINDSTR: /a は無視されました。
FINDSTR: /t は無視されました。
FINDSTR: /u は無視されました。

 exited 1 in 1265ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
FINDSTR: /a は無視されました。
FINDSTR: /u は無視されました。
FINDSTR: /t は無視されました。
FINDSTR: /h は無視されました。
FINDSTR: // は無視されました。
FINDSTR: /t は無視されました。
FINDSTR: /a は無視されました。
FINDSTR: /t は無視されました。
FINDSTR: /u は無視されました。
FINDSTR: /  は無視されました。
FINDSTR: /t は無視されました。
FINDSTR: /a は無視されました。
FINDSTR: /t は無視されました。
FINDSTR: /u は無視されました。

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n \"status\" backend\\src\\main\\java\\jp\\co\\oda32\\api\\finance\\MfIntegrationController.java" in C:\project\odamitsu-data-hub
 succeeded in 1243ms:
32: * 設計書: claudedocs/design-mf-integration-status.md
82:    @GetMapping("/oauth/status")
83:    public ResponseEntity<MfTokenStatus> status() {
119:            return ResponseEntity.status(HttpStatus.FORBIDDEN)
124:            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
130:            return ResponseEntity.status(HttpStatus.CONFLICT)
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

2026-05-06T11:01:40.314842Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "more +75 backend\\src\\main\\java\\jp\\co\\oda32\\api\\finance\\MfIntegrationController.java"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "more +75 backend\\src\\main\\java\\jp\\co\\oda32\\api\\finance\\MfIntegrationController.java" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "more +75 backend\\src\\main\\java\\jp\\co\\oda32\\api\\finance\\MfIntegrationController.java"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/test-after-major.log -Tail 80' in C:\project\odamitsu-data-hub
 succeeded in 1626ms:
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\SmileDestinationFileOutPutTasklet.java:63: 警告: [deprecation] CSVFormatのwithHeader(String...)は推奨されません
                .withHeader("得意先コード", "納品先コード", "納品先名", "納品先索引", "荷受け人名１", "荷受け人名２", "郵便番号", "住所１", "住所２", "住所３", "電話番号", "ＦＡＸ番号", "マスター検索表示区分")
                ^
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\SmileDestinationFileOutPutTasklet.java:64: 警告: [deprecation] CSVFormatのwithQuoteMode(QuoteMode)は推奨されません
                .withQuoteMode(QuoteMode.ALL_NON_NULL)
                ^
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\SmileDestinationFileOutPutTasklet.java:65: 警告: [deprecation] CSVFormatのwithEscape(char)は推奨されません
                .withEscape('\\'); // set escape character
                ^
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\SmileOrderFileOutPutTasklet.java:60: 警告: [deprecation] CSVFormatのwithHeader(String...)は推奨されません
                .withHeader(SmileOrderImportCsvHeader.CSV_HEADERS)
                ^
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\bcart\SmileOrderFileOutPutTasklet.java:61: 警告: [deprecation] CSVFormatのwithQuoteMode(QuoteMode)は推奨されません
                .withQuoteMode(QuoteMode.ALL_NON_NULL);
                ^
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\batch\stock\SmartMatDeliveryRegisterTasklet.java:108: 警告: [deprecation] ResponseEntityのgetStatusCodeValue()は推奨されません
            log.info(String.format("Response Status Code %s", responseEntity.getStatusCodeValue()));
                                                                            ^
警告13個

> Task :processResources
> Task :classes

> Task :compileTestJava
C:\project\odamitsu-data-hub\backend\src\test\java\jp\co\oda32\domain\service\finance\mf\MfEnumTranslationServiceTest.java:82: 警告: [unchecked] 無検査メソッド呼出し: クラス ArgumentCaptorのメソッド forClassは指定された型に適用されます
        ArgumentCaptor<List<MMfEnumTranslation>> captor = ArgumentCaptor.forClass(List.class);
                                                                                 ^
  期待値: Class<S>
  検出値:    Class<List>
  S,Uが型変数の場合:
    メソッド <U,S>forClass(Class<S>)で宣言されているS extends U
    メソッド <U,S>forClass(Class<S>)で宣言されているU extends Object
C:\project\odamitsu-data-hub\backend\src\test\java\jp\co\oda32\domain\service\finance\mf\MfEnumTranslationServiceTest.java:82: 警告: [unchecked] 無検査変換
        ArgumentCaptor<List<MMfEnumTranslation>> captor = ArgumentCaptor.forClass(List.class);
                                                                                 ^
  期待値: ArgumentCaptor<List<MMfEnumTranslation>>
  検出値:    ArgumentCaptor
C:\project\odamitsu-data-hub\backend\src\test\java\jp\co\oda32\domain\service\finance\mf\MfEnumTranslationServiceTest.java:117: 警告: [unchecked] 無検査メソッド呼出し: クラス ArgumentCaptorのメソッド forClassは指定された型に適用されます
        ArgumentCaptor<List<MMfEnumTranslation>> captor = ArgumentCaptor.forClass(List.class);
                                                                                 ^
  期待値: Class<S>
  検出値:    Class<List>
  S,Uが型変数の場合:
    メソッド <U,S>forClass(Class<S>)で宣言されているS extends U
    メソッド <U,S>forClass(Class<S>)で宣言されているU extends Object
C:\project\odamitsu-data-hub\backend\src\test\java\jp\co\oda32\domain\service\finance\mf\MfEnumTranslationServiceTest.java:117: 警告: [unchecked] 無検査変換
        ArgumentCaptor<List<MMfEnumTranslation>> captor = ArgumentCaptor.forClass(List.class);
                                                                                 ^
  期待値: ArgumentCaptor<List<MMfEnumTranslation>>
  検出値:    ArgumentCaptor
C:\project\odamitsu-data-hub\backend\src\test\java\jp\co\oda32\domain\service\finance\mf\MfEnumTranslationServiceTest.java:159: 警告: [unchecked] 無検査メソッド呼出し: クラス ArgumentCaptorのメソッド forClassは指定された型に適用されます
        ArgumentCaptor<List<MMfEnumTranslation>> captor = ArgumentCaptor.forClass(List.class);
                                                                                 ^
  期待値: Class<S>
  検出値:    Class<List>
  S,Uが型変数の場合:
    メソッド <U,S>forClass(Class<S>)で宣言されているS extends U
    メソッド <U,S>forClass(Class<S>)で宣言されているU extends Object
C:\project\odamitsu-data-hub\backend\src\test\java\jp\co\oda32\domain\service\finance\mf\MfEnumTranslationServiceTest.java:159: 警告: [unchecked] 無検査変換
        ArgumentCaptor<List<MMfEnumTranslation>> captor = ArgumentCaptor.forClass(List.class);
                                                                                 ^
  期待値: ArgumentCaptor<List<MMfEnumTranslation>>
  検出値:    ArgumentCaptor
C:\project\odamitsu-data-hub\backend\src\test\java\jp\co\oda32\domain\service\finance\PaymentMfImportServiceAuxRowTest.java:194: 警告: [deprecation] PaymentMfImportServiceのapplyVerification(String,Integer)は推奨されません
        service.applyVerification(preview.getUploadId(), 1);
               ^
警告7個

> Task :processTestResources
> Task :testClasses

> Task :test
Picked up JAVA_TOOL_OPTIONS: -Dfile.encoding=UTF-8

Java HotSpot(TM) 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

[Incubating] Problems report is available at: file:///C:/project/odamitsu-data-hub/backend/build/reports/problems/problems-report.html

BUILD SUCCESSFUL in 53s
5 actionable tasks: 5 executed
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n \"hasVersionColumn WHERE\" backend\\src\\main\\java\\db\\migration\\V033__reencrypt_mf_oauth_secrets.java" in C:\project\odamitsu-data-hub
 succeeded in 1520ms:
110:     * {@code hasVersionColumn=true} のときは {@code oauth_encryption_version=1} の行のみ処理し、
120:                                 boolean hasVersionColumn) throws Exception {
121:        String whereVersion = hasVersionColumn
125:                + " WHERE " + column + " IS NOT NULL" + whereVersion;
126:        String updateSql = hasVersionColumn
128:                        + " WHERE " + pkColumn + " = ? AND " + VERSION_COLUMN + " = 1"
129:                : "UPDATE " + table + " SET " + column + " = ? WHERE " + pkColumn + " = ?";
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n \"V033 V037 033 037\" backend\\bootRun-v042.log backend\\bootRun-migrate.log" in C:\project\odamitsu-data-hub
 succeeded in 1563ms:
backend\bootRun-v042.log:54:2026-05-06T19:41:46.467+09:00  INFO 74292 --- [           main] o.f.core.internal.command.DbMigrate      : Successfully applied 2 migrations to schema "public", now at version v042 (execution time 00:00.033s)
backend\bootRun-migrate.log:54:2026-05-06T15:31:54.668+09:00  INFO 68760 --- [           main] o.f.core.internal.command.DbMigrate      : Migrating schema "public" to version "037 - add oauth encryption version"
backend\bootRun-migrate.log:55:2026-05-06T15:31:54.684+09:00  INFO 68760 --- [           main] o.f.c.i.s.DefaultSqlScriptExecutor       : DB: V037: V033 適用済のため全行を oauth_encryption_version=2 にマークしました
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git diff 1b812b7''^ 1b812b7 -- backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java | findstr /n "ALLOWED_REDIRECT_URIS localhost odamitsu"' in C:\project\odamitsu-data-hub
2026-05-06T11:02:13.028455Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 1.5 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 exited 1 in 1488ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

2026-05-06T11:02:16.593227Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git show 1b812b7''^:backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java > $env:TEMP'"\\old_mfoauth.txt; findstr /n \"ALLOWED_REDIRECT_URIS localhost odamitsu\" "'$env:TEMP'"\\old_mfoauth.txt"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git show 1b812b7''^:backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java > $env:TEMP'"\\old_mfoauth.txt; findstr /n \"ALLOWED_REDIRECT_URIS localhost odamitsu\" "'$env:TEMP'"\\old_mfoauth.txt" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git show 1b812b7''^:backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java > $env:TEMP'"\\old_mfoauth.txt; findstr /n \"ALLOWED_REDIRECT_URIS localhost odamitsu\" "'$env:TEMP'"\\old_mfoauth.txt"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'git show 1b812b7''^:backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java' in C:\project\odamitsu-data-hub
 succeeded in 1550ms:
package jp.co.oda32.domain.service.finance.mf;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.TMfOauthToken;
import jp.co.oda32.domain.repository.finance.MMfOauthClientRepository;
import jp.co.oda32.domain.repository.finance.TMfOauthTokenRepository;
import jp.co.oda32.util.CryptoUtil;
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
    private final CryptoUtil cryptoUtil;
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

    /** MF からの callback を処理し、トークンを DB 保存して接続状態にする。state 検証 + PKCE 込み。 */
    @Transactional
    public void handleCallback(String code, String state, Integer userNo) {
        Optional<String> verifierOpt = stateStore.verifyAndConsume(state, userNo);
        if (verifierOpt.isEmpty()) {
            throw new IllegalArgumentException("MF OAuth state 検証失敗 (期限切れ or 改ざん)");
        }
        MMfOauthClient client = findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        String plainSecret = cryptoUtil.decrypt(client.getClientSecretEnc());
        MfTokenResponse token = mfApiClient.exchangeCodeForToken(client, plainSecret, code, verifierOpt.get());
        persistToken(client, token, userNo);
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

        // 3) 永続化のみ REQUIRES_NEW で直列化
        self.persistRefreshedToken(snap.client.getId(), refreshed);
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

    public MfTokenStatus getStatus() {
        Optional<MMfOauthClient> client = findActiveClient();
        if (client.isEmpty()) {
            return new MfTokenStatus(false, false, null, null, null, false);
        }
        Optional<TMfOauthToken> token = tokenRepository
                .findFirstByClientIdAndDelFlgOrderByIdDesc(client.get().getId(), "0");
        if (token.isEmpty()) {
            return new MfTokenStatus(true, false, null, null, null, false);
        }
        TMfOauthToken t = token.get();
        Instant exp = t.getExpiresAt().toInstant();
        Instant lastRefreshed = t.getModifyDateTime() != null
                ? t.getModifyDateTime().toInstant()
                : t.getAddDateTime().toInstant();
        return new MfTokenStatus(true, true, exp, t.getScope(), lastRefreshed, false);
    }

    /** 接続切断。DB トークンを論理削除する（MF 側 revoke は現時点でベストエフォート省略）。 */
    @Transactional
    public void revoke(Integer userNo) {
        MMfOauthClient client = findActiveClient().orElse(null);
        if (client == null) return;
        tokenRepository.softDeleteActiveTokens(client.getId(), Timestamp.from(Instant.now()), userNo);
    }

    private void persistToken(MMfOauthClient client, MfTokenResponse res, Integer userNo) {
        Timestamp now = Timestamp.from(Instant.now());
        tokenRepository.softDeleteActiveTokens(client.getId(), now, userNo);
        long expiresInSec = res.expiresIn() != null ? res.expiresIn() : 3600L;
        Timestamp expiresAt = Timestamp.from(Instant.now().plus(expiresInSec, ChronoUnit.SECONDS));
        TMfOauthToken token = TMfOauthToken.builder()
                .clientId(client.getId())
                .accessTokenEnc(cryptoUtil.encrypt(res.accessToken()))
                .refreshTokenEnc(cryptoUtil.encrypt(res.refreshToken()))
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

    /** {@link #loadActiveTokenSnapshot()} 戻り値。refresh 判定や HTTP 呼び出しで使う。 */
    public record TokenSnapshot(
            MMfOauthClient client,
            String plainSecret,
            String accessToken,
            String plainRefresh,
            Instant expiresAt) {
        public boolean isFresh(long marginSeconds) {
            return Instant.now().isBefore(expiresAt.minusSeconds(marginSeconds));
        }
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfHttpClientConfig.java; Get-Content backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfDebugApiClient.java' in C:\project\odamitsu-data-hub
 succeeded in 1724ms:
package jp.co.oda32.domain.service.finance.mf;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * MF API 呼び出し用の共通 {@link RestClient} Bean 定義。
 * <p>
 * connect/read timeout を明示設定して、ハングや MF 側の遅延で
 * すべての ledger / reconcile / health check 操作が永久ブロックされる
 * DoS を回避する (SF-01)。
 * <p>
 * {@link jp.co.oda32.batch.bcart.config.BCartHttpClientConfig} を参考。
 *
 * @since 2026-05-04 (SF-01)
 */
@Configuration
public class MfHttpClientConfig {

    /** MF API への TCP 接続確立タイムアウト。 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** MF API レスポンス受信タイムアウト (大きめのページ取得を許容)。 */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    @Bean(name = "mfRestClient")
    public RestClient mfRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
package jp.co.oda32.domain.service.finance.mf;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.util.SensitiveLogMasker;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * SF-14: MF API への診断用 HTTP クライアント (dev/test プロファイル限定)。
 * <p>
 * 旧 {@code MfApiClient.getRaw} を本クラスに移し、prod ビルドでは Bean が存在しないようにする。
 * 通常運用の {@link MfApiClient} と分離することで「本番でも生 JSON を返せる経路」を物理的に消す。
 *
 * @since 2026-05-04 (SF-14)
 */
@Component
@Profile({"dev", "test"})
@Log4j2
public class MfDebugApiClient {

    private final RestClient restClient;

    public MfDebugApiClient(@Qualifier("mfRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 指定パスの MF API を生 JSON のまま取得する（診断用）。
     * 401 は {@link MfReAuthRequiredException} に変換。
     */
    public JsonNode getRaw(MMfOauthClient client, String accessToken, String path) {
        String url = client.getApiBaseUrl() + path;
        try {
            return restClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            String body = e.getResponseBodyAsString();
            log.warn("MF {} 取得失敗 (debug): status={}, body={}",
                    path, status.value(), SensitiveLogMasker.mask(body));
            if (status.value() == 401) {
                throw new MfReAuthRequiredException("再認証が必要です。詳細は管理者ログを参照してください", e);
            }
            throw e;
        }
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "findstr /n \"String updateSql VERSION_COLUMN\" backend\\src\\main\\java\\db\\migration\\V033__reencrypt_mf_oauth_secrets.java" in C:\project\odamitsu-data-hub
 succeeded in 1410ms:
51:    private static final String DEV_FALLBACK_KEY = "dev-odamitsu-data-hub-crypto-key-2026";
52:    private static final String DEV_FALLBACK_SALT = "3f6a2d8c9e1b4a7d5c0f8e2a9b6d1c3f";
54:    private static final String VERSION_COLUMN = "oauth_encryption_version";
58:        String oldKey = envOrDefault("APP_CRYPTO_KEY", DEV_FALLBACK_KEY);
59:        String oldSalt = envOrDefault("APP_CRYPTO_SALT", DEV_FALLBACK_SALT);
60:        String newKey = System.getenv("APP_CRYPTO_OAUTH_KEY");
61:        String newSalt = System.getenv("APP_CRYPTO_OAUTH_SALT");
79:            boolean clientHasVersion = columnExists(conn, "m_mf_oauth_client", VERSION_COLUMN);
80:            boolean tokenHasVersion = columnExists(conn, "t_mf_oauth_token", VERSION_COLUMN);
118:    private static int reencrypt(Connection conn, String table, String pkColumn, String column,
121:        String whereVersion = hasVersionColumn
122:                ? " AND " + VERSION_COLUMN + " = 1"
124:        String selectSql = "SELECT " + pkColumn + ", " + column + " FROM " + table
126:        String updateSql = hasVersionColumn
127:                ? "UPDATE " + table + " SET " + column + " = ?, " + VERSION_COLUMN + " = 2"
128:                        + " WHERE " + pkColumn + " = ? AND " + VERSION_COLUMN + " = 1"
133:             PreparedStatement update = conn.prepareStatement(updateSql)) {
136:                String oldEnc = rs.getString(2);
137:                String plain;
145:                String newEnc = newCipher.encrypt(plain);
146:                update.setString(1, newEnc);
157:    private static boolean columnExists(Connection conn, String table, String column) throws Exception {
168:    private static String envOrDefault(String name, String defaultValue) {
169:        String v = System.getenv(name);
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
The OAuth key migration can leave token data partially encrypted with mismatched keys in supported/retry scenarios, which breaks token decryption after deployment. These are blocking migration correctness issues.

Full review comments:

- [P1] Re-encrypt both token columns before marking the row — C:\project\odamitsu-data-hub\backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:126-128
  When `oauth_encryption_version` already exists, this helper sets the row to version 2 after re-encrypting a single column. For `t_mf_oauth_token`, the access-token pass marks the row as version 2, so the following refresh-token pass filters it out with `oauth_encryption_version = 1`, leaving `refresh_token_enc` encrypted with the old key while runtime now uses `OauthCryptoUtil` and will fail to decrypt it.

- [P2] Add the version marker before the re-encryption migration — C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V037__add_oauth_encryption_version.sql:23-25
  Because this is V037, normal Flyway ordering runs the row-committing V033 re-encryption before these marker columns exist, so V033 cannot record which rows were already converted. If V033 commits some rows and then fails before Flyway records success, the next run will try to decrypt already-new-key ciphertext with the old key and the advertised idempotent recovery path still fails.
The OAuth key migration can leave token data partially encrypted with mismatched keys in supported/retry scenarios, which breaks token decryption after deployment. These are blocking migration correctness issues.

Full review comments:

- [P1] Re-encrypt both token columns before marking the row — C:\project\odamitsu-data-hub\backend\src\main\java\db\migration\V033__reencrypt_mf_oauth_secrets.java:126-128
  When `oauth_encryption_version` already exists, this helper sets the row to version 2 after re-encrypting a single column. For `t_mf_oauth_token`, the access-token pass marks the row as version 2, so the following refresh-token pass filters it out with `oauth_encryption_version = 1`, leaving `refresh_token_enc` encrypted with the old key while runtime now uses `OauthCryptoUtil` and will fail to decrypt it.

- [P2] Add the version marker before the re-encryption migration — C:\project\odamitsu-data-hub\backend\src\main\resources\db\migration\V037__add_oauth_encryption_version.sql:23-25
  Because this is V037, normal Flyway ordering runs the row-committing V033 re-encryption before these marker columns exist, so V033 cannot record which rows were already converted. If V033 commits some rows and then fails before Flyway records success, the next run will try to decrypt already-new-key ciphertext with the old key and the advertised idempotent recovery path still fails.
