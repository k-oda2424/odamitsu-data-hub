OpenAI Codex v0.128.0 (research preview)
--------
workdir: C:\project\odamitsu-data-hub
model: gpt-5.5
provider: openai
approval: never
sandbox: read-only
reasoning effort: none
reasoning summaries: none
session id: 019df0a5-9352-7d02-a010-4392202cfe1f
--------
user
あなたはセキュリティと OAuth/SaaS 連携の上級レビュアーです。Cluster F (MF 連携基盤: OAuth + ヘルスチェック) の批判的レビューを実施してください。

## プロジェクト
小田光データ連携基盤 (`C:/project/odamitsu-data-hub`)
- Java 21 / Spring Boot 3.3.x / PostgreSQL 17 / Next.js 16
- マネーフォワードクラウド会計との OAuth2 (PKCE+BASIC) 連携基盤

## レビュー対象 (Cluster F)
**Backend** (主要):
- `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/` 配下全体
  - MfApiClient, MfTokenService, MfJournalFetcher, MfHealthCheckService, MfBalanceReconcileService 等
- Repository: MMfOauthClientRepository, TMfOauthTokenRepository, MfAccountMasterRepository
- Controller: MF 連携の Controller 群

**Frontend**:
- `frontend/components/pages/finance/mf-integration.tsx`
- `frontend/components/pages/finance/mf-health.tsx`
- `frontend/components/pages/finance/mf-integration-callback.tsx`
- 各種 MF タブ: MfEnumTranslationTab.tsx, MfReconcileTab.tsx, MfAccountSyncTab.tsx, MfBalanceReconcileTab.tsx

## 既存レビュー (重複回避)
- `claudedocs/design-review-mf-integration.md` (Opus 設計レビュー、Critical 3 / Major 7 / Minor 8)
- `claudedocs/code-review-mf-integration.md` (Opus コードレビュー、Critical 2 / Major 8 / Minor 11)
- `claudedocs/design-mf-integration-status.md` (設計書)

既出指摘 (重複させない):
- dev salt ハードコード、token leak、window.open noreferrer 矛盾、ヘルス画面 0 固定誤表示
- RestClient timeout 無限、token record の auto toString 漏洩、@Transactional + HTTP、state atomicity、refresh rotation off、StrictMode 二重消費

## 批判的レビューの観点
Opus は実装上のセキュリティ欠陥を網羅した。**あなたの仕事は別の角度から問い直すこと**:

1. **OAuth/トークン基盤の根本前提**
   - MF クラウド側のトークンライフサイクル (有効期限、ローテーション、無効化) と本実装の整合
   - リフレッシュトークン枯渇時の業務影響 (経理業務の停止)
   - マルチテナント (会社別 MF 契約) を考慮した設計か
   - クライアント秘密 (client_secret) の鍵管理ポリシー (KMS / Vault / DB暗号化のどこに置くべきか)

2. **代替アーキテクチャ**
   - OAuth クライアント情報を DB ではなく Spring Cloud Config / 環境変数 / Vault で管理する案
   - MF API 呼び出しを synchronous client から非同期 message queue ベースに切り替える案 (rate limit + リトライ集中管理)
   - Backend for Frontend (BFF) パターンで callback を BFF が受け、token を Frontend に返さない設計

3. **エッジケース / 失敗モード**
   - MF 側がメンテナンス窓 / API 廃止 / scope 変更した場合
   - 同一ユーザーが複数 MF 契約に同時連携した場合
   - 認可コード再利用攻撃 (CSRF + リプレイ)
   - MF API 仕様変更 (列追加/削除) による breaking change の検知方法
   - クライアント秘密ローテーション時の旧 token 失効タイミング

4. **ビジネス整合性 / 運用**
   - 「MF 認証が切れた」をいつ誰が知るか (アラート設計)
   - 監査要件: いつ誰が MF と連携設定を変更したか
   - GDPR/個人情報保護法上、MF 経由でやり取りされる取引先個人情報の扱い

## 出力フォーマット
Markdown、日本語、簡潔に (max 600 行)。各指摘は:
- Severity: Critical / Major / Minor
- カテゴリ: 設計選択 / 代替案 / エッジケース / 運用整合
- 内容 (3-5 文)
- 推奨アクション (1-2 文)

最後に「Opus が見落としていた最重要 3 件」を明記。


codex
`using-superpowers` と、レビュー対象が Spring/OAuth 基盤なので `spring-framework-patterns` を確認してから進めます。既存レビューとの重複を避けるため、まず関連ドキュメントと対象コードの形を読みます。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'Get-ChildItem -Force' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content C:\\Users\\kazuki\\.agents\\skills\\using-superpowers\\SKILL.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content C:\\Users\\kazuki\\.agents\\skills\\spring-framework-patterns\\SKILL.md" in C:\project\odamitsu-data-hub
 succeeded in 1642ms:

    Directory: C:\project\odamitsu-data-hub

Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
d----          2026/04/30    15:20                .claude
d--h-          2026/04/28     9:23                .git
d----          2026/04/21    19:52                .idea
d----          2026/04/23    11:47                backend
d----          2026/04/16    17:55                backup
d----          2026/05/04    10:41                claudedocs
d----          2026/03/31    19:46                C?projectodamitsu-data-hubclaudedocs
d----          2026/04/01     2:26                data
d----          2026/05/01    13:07                docs
d----          2026/05/01    16:31                frontend
d----          2026/02/24    18:31                frontend-vite-backup
d----          2026/04/30    15:20                SQL
d----          2026/04/13    18:17                test-results
-a---          2026/04/23    11:27           1591 _query_residual.sql
-a---          2026/04/21     8:55           1301 .env.local
-a---          2026/04/21     8:39            662 .gitignore
-a---          2026/04/14    18:49           3554 CLAUDE.md
-a---          2026/04/01     3:23             50 README.md

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 1646ms:
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

 succeeded in 1960ms:
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
既存レビューの指摘リストとコードの責務分割を照合します。今回は修正ではなくレビューなので、実装欠陥の再掲ではなく「前提・運用・失敗時の業務停止」に寄せて見ます。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content claudedocs\\design-review-mf-integration.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content claudedocs\\code-review-mf-integration.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content claudedocs\\design-mf-integration-status.md" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'rg --files backend/src/main/java/jp/co/oda32/domain/service/finance/mf backend/src/main/java | rg "(Mf|MF|mf|Oauth|OAuth|Controller|Repository)"' in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command 'rg --files frontend/components/pages/finance | rg "(mf-|Mf|mf)"' in C:\project\odamitsu-data-hub
 succeeded in 2136ms:
# 設計レビュー: MF 連携基盤 (Cluster F)

レビュー日: 2026-05-04
対象設計書: `claudedocs/design-mf-integration-status.md`
レビュアー: Opus サブエージェント
対象ブランチ: `refactor/code-review-fixes`

---

## サマリー

- 総指摘件数: **Critical 3 / Major 7 / Minor 8** (合計 18 件)
- 承認状態: **Needs Revision**
- 主要懸念:
  1. **Critical-1**: `app.crypto.salt` の dev fallback 値がリポジトリにハードコード (application-dev.yml:21)。同じ値が prod 採用された場合、暗号化済 client_secret / access_token / refresh_token が攻撃者により復号可能。`feedback_dev_config_fallbacks` で「触らない方針」とされているが、暗号化対象が高機微な OAuth 資産になった以上、ガード強化を再評価すべき。
  2. **Critical-2**: `MfApiClient.postToken()` が token endpoint のエラーレスポンスを生でログ出力 + 例外メッセージ伝搬 (`MfApiClient.java:251,254`)。MF 側 OAuth エラー応答は仕様により `error_description` 内に reflected `code` / `state` / 部分的な token を含むケースがあり、ログ漏洩リスク。
  3. **Critical-3**: `mf-integration.tsx:103` で `window.open(res.url, '_blank', 'noreferrer')` を `noopener` 抜きで開いている。`noreferrer` は付くが、`noopener` を明示的に外しているため、認可後 callback タブが `window.opener` 経由で親タブの `location` に書き込み可能 (tabnabbing)。MF 自体は信頼できるが、`authorize_url` を DB から動的に注入できる設計のため、admin 権限を持つユーザーが authorize_url を改竄できれば任意 URL の開放につながる。
- ヘルスチェック画面の anomaly 集計 (`verifyDiffCount`/`continuityBreakCount`/`monthGapCount`) は **0 固定で実装途中** (`MfHealthCheckService.java:103-105`)。frontend (`mf-health.tsx:145-148`) は「(予定)」表記で UX としてはハンドリングされているが、`judgeHealth()` (`mf-health.ts:60`) が anomaly 合計を `red`/`yellow` 判定の根拠にしているため、実態と乖離した「健全」表示が常時出る危険性あり。後述 Major-7 で詳述。

---

## Critical 指摘

### Critical-1: `app.crypto.salt` の dev fallback がリポジトリにハードコード

- **箇所**: `backend/src/main/resources/config/application-dev.yml:21`
  ```yaml
  crypto:
    key: ${APP_CRYPTO_KEY:dev-odamitsu-data-hub-crypto-key-2026}
    salt: ${APP_CRYPTO_SALT:3f6a2d8c9e1b4a7d5c0f8e2a9b6d1c3f}
  ```
- **問題**:
  1. salt 値そのものが git 履歴に commit されている。AES-256/GCM の機密性は salt の秘匿性に依存しないが、PBKDF2 出力 (= 実際の AES 鍵) はパスフレーズ + salt の組合せで一意決定するため、key が leak した瞬間に salt 既知で完全復号可能。
  2. dev fallback の存在自体が「.env を作らずに動く」ことを正当化し、prod 設定漏れに気付きにくい。`CryptoUtil.init` (`CryptoUtil.java:43-66`) は salt が空白なら fail-fast するが、env 設定があれば fallback は使われない仕組み (`${APP_CRYPTO_KEY:default}` は env 未設定時のみ default 採用) のため、本番で env を入れ忘れた場合「dev と同じ鍵で起動できてしまう」。
- **影響**: prod に dev fallback がそのまま流れ込んだ場合、攻撃者は git 履歴から salt + key を取得し、`m_mf_oauth_client.client_secret_enc` / `t_mf_oauth_token.access_token_enc` / `refresh_token_enc` を復号可能。MF OAuth client_secret 漏洩 → MF 上で他社振りすまし、access_token 漏洩 → 仕訳・買掛・売掛閲覧。
- **修正案**:
  1. `application-prod.yml` (line 47-48) は env 必須なので OK だが、`application.yml` (デフォルトプロファイル) で `app.crypto.key/salt` を **空文字に明示し fail-fast に到達** することを保証する E2E テストを追加。
  2. `MEMORY.md` の `feedback_dev_config_fallbacks` で dev fallback 据え置き方針があるが、**MF token + client_secret は機密性が高い**ため、(a) OAuth 関連カラムの `_enc` 用に専用 KMS / Vault 鍵を導入する、もしくは (b) fallback 値を「明らかにダミー」(`__SET_APP_CRYPTO_KEY_AND_SALT_VIA_ENV__`) に変えて、起動はするが暗号化を試みると例外 → 開発者が即気付く構成に変更する選択肢を再検討。
  3. 暗号鍵ローテーション設計が未定義 (`CryptoUtil` 単一鍵)。「鍵更新時は `client_secret_enc` / `*_token_enc` を DELETE → 再認可」と application-dev.yml 16-18 行目コメントには書いてあるが、運用手順書としてマージ前に `claudedocs/runbook-mf-key-rotation.md` を作成すべき。

### Critical-2: token エンドポイントの error body を生ログ出力

- **箇所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java:251`
  ```java
  log.warn("MF token エンドポイント失敗: status={}, body={}", status.value(), body);
  ...
  throw new MfReAuthRequiredException(
          "MF トークン交換に失敗しました（再認証が必要）: " + body, e);
  ```
- **問題**:
  1. MF OAuth2 token endpoint は `error: invalid_grant` 等のエラー応答に `error_description` を含む。実装によっては reflected な `code` / partial token / client_id を含むことがあり、ログレベル `WARN` でアプリログに残る。
  2. body 全体を `MfReAuthRequiredException` のメッセージに連結し、`MfIntegrationController.callback()` (line 132) で `Map.of("message", e.getMessage())` として **API レスポンス body にそのまま返却**。フロント `mf-integration-callback.tsx:55` でメッセージを画面表示。MF が将来エラーレスポンスフォーマットを変更し token を含むようになった場合、即漏洩。
  3. `MfApiClient.java:104,135,161,198,229` の他の API 呼び出しでも同パターン (`body=...` をログ出力)。
- **影響**: 機密情報の意図しない leak。コードレビュー指針 `### セキュリティ` (`CLAUDE.md` グローバル) の "No hardcoded secrets" + 「機微情報のログ出力チェック」観点で違反。
- **修正案**:
  1. `body` をログに出す場合は **token-like / secret-like パターン (`/[a-zA-Z0-9_-]{20,}/`) を `***` に置換**するマスキングユーティリティを `CryptoUtil` 隣に新設 (`SensitiveLogMasker.mask(body)`)。
  2. ユーザー向けエラーメッセージは `body` を含めず汎用文言 (`"再認証が必要です。詳細は管理者ログを参照してください"`) にし、詳細は `log.warn` のみ。
  3. token 取得失敗時に limited な structured log (`status`, `error`, `error_description` のみ抽出) を出す方針へ移行。

### Critical-3: `noopener` を明示的に外して新タブ起動 (tabnabbing)

- **箇所**: `frontend/components/pages/finance/mf-integration.tsx:101-104`
  ```ts
  // 親タブから opener アクセスできるよう noopener は付けない（post-message 通知のため）。
  window.open(res.url, '_blank', 'noreferrer')
  ```
- **問題**:
  1. `noreferrer` は referrer header を抑制するが、HTML 仕様上 `noreferrer` を含むと **暗黙的に `noopener` も適用される** ため、`window.opener` は実は子タブ側から `null` になる。**つまりコメント「親タブから opener アクセスできるよう」は誤り** で、`mf-integration-callback.tsx:46-50` の `window.opener?.postMessage(...)` は実装上 **動かない可能性が高い**。実機で動いているなら、`noreferrer` の opener 抑制ロジックがブラウザ依存 (古い Edge 等) の可能性。
  2. 仮に `noreferrer` を外して `noopener` だけ付けないバリエーションに直すと、子タブ (MF 側) から `window.opener.location = "悪意 URL"` で親タブを書き換え可能 (リバース tabnabbing)。MF 公式は信頼できるが、`authorize_url` 自体が DB 設定 (`m_mf_oauth_client.authorize_url`) で admin 編集可能 (`MfIntegrationController.upsertClient`, line 68-83) のため、admin アカウント乗っ取り → 攻撃 URL 注入 → tabnabbing チェーンが成立する。
- **影響**: post-message 通知機能が実は動いていない (= 検証不足) + 設計意図と実装の乖離。修正方向次第ではセキュリティ低下。
- **修正案**:
  1. **正しい方法**: `BroadcastChannel` API (`new BroadcastChannel('mf-oauth')`) もしくは `localStorage` イベントで親⇔子タブ通信。`window.opener` 依存を削除し、`window.open(url, '_blank', 'noopener,noreferrer')` で両方付ける。
  2. `authorize_url` も `redirect_uri` 同様 ALLOWED list で完全一致検証する (`MfOauthService.ALLOWED_REDIRECT_URIS` のパターンを `authorize_url`/`token_url`/`api_base_url` にも展開)。MF 公式 URL 以外は弾く。
  3. 検証手順: 現状 `window.opener?.postMessage` が実際に発火しているか、Chrome DevTools で MF 認可後のタブで `window.opener` の値を確認 (おそらく `null`)。代替パスを実装後、E2E テストで通知到達を確認。

---

## Major 指摘

### Major-1: refresh_token rotation 後の旧 token 即時無効化処理がない

- **箇所**: `MfOauthService.persistRefreshedToken` → `persistToken` (`MfOauthService.java:177-230`)
- **問題**: refresh で新 access_token + refresh_token を取得後、旧 refresh_token を MF 側で revoke していない。MF は通常 refresh_token を rotate (新発行で旧失効) する仕様だが、rotate しないモード (refresh_token sliding) もあり、API 仕様未確認。`exchangeCodeForToken` のレスポンスに **同一 refresh_token がそのまま返る場合**、旧 refresh_token の有効期限が切れない限り使い回し可能で、過去 leak のリスクが残り続ける。
- **影響**: refresh_token 漏洩時の影響範囲拡大。
- **修正案**: `revoke(userNo)` (`MfOauthService.java:206-211`) は DB 論理削除のみ。設計書 §9 にも「MF 側 revoke はベストエフォート省略」とあるが、`POST /oauth/revoke` (MF 側) を呼び出して失効を伝える実装を追加。refresh 時も旧 refresh_token と新 refresh_token が異なる場合のみ旧側を MF に revoke。

### Major-2: state TTL 検証で `<` 比較に等号がない

- **箇所**: `MfOauthStateStore.verifyAndConsume` (`MfOauthStateStore.java:75`)
  ```java
  if (e.getExpiresAt().toInstant().isBefore(Instant.now())) {
      log.info("MF OAuth state 有効期限切れ");
      return Optional.empty();
  }
  ```
- **問題**: `isBefore` は厳密小なり比較で同タイムスタンプは expired 扱いにならない (= 1ms 単位で expired の境界が「expires 時刻 + 1ms 以降」になる)。設計書 §4.4 の「TTL 10 分」を厳格適用するなら `!Instant.now().isBefore(expiresAt)` の方が安全。
- **影響**: 軽微 (せいぜい 1 リクエストの境界事例) だが、テスト失敗時の挙動が分かりにくい。
- **修正案**: `if (!Instant.now().isBefore(e.getExpiresAt().toInstant()))` に統一。`MfOauthService.TokenSnapshot.isFresh` (`MfOauthService.java:261`) と整合。

### Major-3: callback の `userNo` 一致検証が「同一ブラウザ前提」

- **箇所**: `MfOauthStateStore.verifyAndConsume` (`MfOauthStateStore.java:80-84`)
- **問題**: `state` 発行時の `userNo` と callback 時の `userNo` を比較する設計。callback が **新タブで開かれ、JWT (Authorization header) を持って `POST /oauth/callback` を呼ぶ** ため、同じブラウザの localStorage / cookie に JWT が共有されている前提。
  1. JWT が cookie ではなく Authorization header の場合、認可フロー中に admin がログアウト → 別 admin がログインすると userNo 不一致で弾く動作になるが、ユーザーフレンドリーなエラー文言なし (`MfIntegrationController.java:127` で 403 のみ)。
  2. `userNo == null` (= 認証されていない callback) のケースを `null/null` 一致で通している (`MfOauthStateStore.java:80`)。`SecurityConfig.java:43-45` で `/api/v1/auth/login` 以外は authenticated 必須なので実際には起きないはずだが、設計上の防御層が薄い。
- **影響**: 多重 admin 運用時に分かりにくい挙動。
- **修正案**:
  1. `userNo == null` 同士の許容を削除し、null は常に reject。
  2. 403 レスポンスに「state 発行ユーザーと callback 実行ユーザーが異なります」の文言を追加。

### Major-4: `MfApiClient` が retry ロジックを持たず、`MfJournalFetcher` 側で重複実装

- **箇所**: `MfApiClient.java:115-143` (`listJournals`), `MfJournalFetcher.callListJournalsWithRetry` (`MfJournalFetcher.java:140-158`)
- **問題**: 設計書 §5 で「`MfApiClient` 実装は Spring 6 `RestClient` + `Retry`(Resilience4j) で 429/5xx リトライ」と明記されているが、現実装は **RestClient のみで retry/backoff なし**。`MfJournalFetcher` だけが 429 retry をやっており、`getRaw` / `listAccounts` / `listTaxes` / `getTrialBalanceBs` は retry なし。`exchangeCodeForToken` / `refreshToken` も同様。
- **影響**:
  1. `MfBalanceReconcileService` の trial_balance_bs 呼び出し (`MfBalanceReconcileService.java:59,69`) が 429 で即落ちる。
  2. MF API レート制限 (Operations per second) 違反時に失敗が多発し、ユーザー操作で再実行が必要。
  3. token refresh 自体は通常 1 リクエストで済むため重要度は低いが、複数 admin が同時操作すると失敗ケースあり (advisory lock で直列化はしているが、MF 側は別)。
- **修正案**:
  1. `MfApiClient` 内に共通 `executeWithRetry(Supplier<T>, String operation)` を追加し、429 + 5xx は指数バックオフ retry。設計書 §5 と整合。
  2. timeout (connect 5s / read 30s) も設計書記載だが、`restClientBuilder` の build にタイムアウト設定が無い。`RestClient.Builder` に `requestFactory(ClientHttpRequestFactory)` を Bean 化し、Apache HttpClient5 / SimpleClientHttpRequestFactory で接続/読取タイムアウトを設定。

### Major-5: `MfJournalCacheService` が TTL なしのインメモリで OOM リスクあり

- **箇所**: `MfJournalCacheService.java:21-35` および `cachesByShop` (line 43)
- **問題**: 設計コメント (line 22-26) に「TTL なし、サーバー再起動まで保持」と明記。`MEMORY.md` の「初回 12.98s、cache hit 75ms」記載通り効果は大きいが:
  1. 1739 journals × 24 ヶ月 × shop 数 = メモリ蓄積。`MfJournal` レコードは `branches[]` を含む比較的大きなオブジェクト (`MfJournal.java`)。
  2. shop ごとの cache map を `synchronized` で守る大きな単一 lock (`getOrFetch` メソッド全体が synchronized) のため、高並列時に他 shop の操作も待ち。
  3. 手動 `invalidateAll(shopNo)` のみで、自動失効 / size cap なし。MF 側で過去 journal が修正された場合に検知不能。
- **影響**: 中長期運用で OOM および古いデータでの判断ミス。
- **修正案**:
  1. Caffeine Cache で `expireAfterWrite=24h` + `maximumSize=10000 entries` を設定 (`MfJournalCacheService` 内部実装を Caffeine に置換)。
  2. `synchronized` を shop 単位の `ReentrantLock` に分割。
  3. invalidateAll を「shop × month range 指定」で部分破棄できるように API 拡張 (`/finance/mf-health/cache/invalidate?shopNo=&fromMonth=&toMonth=`)。
  4. 設計書 §5 / §10 にキャッシュ戦略の記述がそもそも無い。`design-mf-integration-status.md` に Phase 1.5 として追記が必要。

### Major-6: redirect_uri ホワイトリストに admin 編集パスがない一方、authorize_url/token_url/api_base_url は admin 編集可

- **箇所**: `MfOauthService.ALLOWED_REDIRECT_URIS` (line 47-50), `MfIntegrationController.upsertClient` (line 68-83)
- **問題**:
  1. redirect_uri は完全一致 ALLOW list でガード (B-4)、これは正解。
  2. しかし `authorize_url` / `token_url` / `api_base_url` は admin が画面から自由設定可能 (`MfOauthClientRequest.java:25-32`)。admin アカウント乗っ取り or 内部不正で「`authorize_url=https://attacker.example/oauth`」に変更 → 認可フローで admin の MF 認可情報が攻撃者に流れる可能性。
  3. token endpoint も同様 (token leak 経路)、api_base_url も同様 (将来書き込み権限取得時に偽 API へ機微データ流出)。
- **影響**: admin 権限濫用 / 内部不正時の被害拡大。
- **修正案**:
  1. authorize_url / token_url / api_base_url を application.yml の固定値に移し、admin 編集不可にする (clientId / clientSecret / redirectUri / scope のみ編集可能)。
  2. もしくは `*.moneyforward.com` ドメインに限定する正規表現バリデーションを `MfOauthService.upsertClient` に追加。
  3. 設計書 §13 にも「アプリポータル登録時の URL は固定」と書くべき。

### Major-7: ヘルスチェックの anomaly 集計が 0 固定で実質的に「健全」誤表示

- **箇所**:
  - Backend: `MfHealthCheckService.java:103-105`
    ```java
    .verifyDiffCount(0L)
    .continuityBreakCount(0L)
    .monthGapCount(0L)
    ```
  - Frontend: `mf-health.tsx:145-148`, `mf-health.ts:60` (`anomalyTotal` 計算)
- **問題**:
  1. backend が 0 固定。`AccountsPayableLedgerService.summarize` (`AccountsPayableLedgerService.java:293-326`) では supplier 単位で実装済みのため、 **shop 単位で集計するロジックを util 化** すれば実装可能 (MEMORY.md の `Next Session Pickup` でも `PayableAnomalyCounter` util 化として残課題化)。
  2. frontend `judgeHealth()` (`mf-health.ts:60`) が `anomalyTotal = negativeClosingCount + verifyDiffCount + continuityBreakCount + monthGapCount` で判定。0 固定の 3 値が常に 0 のため、negativeClosingCount のみが anomaly 判定の根拠になる。実際には verifyDiff / continuityBreak / monthGap が複数発生している可能性があるのに「健全」表示される。
  3. 「(予定)」ラベルで UI 上は分かるが、ユーザーがそれに気付かないと判断ミスにつながる (例: 「健全」と表示されているから精算手続を進める)。
- **影響**: ヘルスチェック画面の主機能が機能不全。実運用で UX を信頼できない。
- **修正案**:
  1. `PayableAnomalyCounter` util を新設し、`AccountsPayableLedgerService` の anomaly 検出ロジック (line 293-310) を抽出。`MfHealthCheckService` から shop 単位の全 supplier を回して合算。
  2. もしくは `t_accounts_payable_summary` に anomaly カラムを永続化する案も検討 (毎回スキャンせず済む)。
  3. それまでの間は frontend `judgeHealth()` を修正し、 0 固定 3 値を **判定から除外** する (UI 表示はしつつ、判定には使わない)。コメントで `// TODO: backend 実装後に judgement に組み込む` を明記。
  4. 設計書 `design-supplier-balances-health.md` §4.3 を更新し「Phase 1: negativeClosing のみ実装、Phase 2 で残り 3 種を追加」と Phase 分割を明記。

---

## Minor 指摘

### Minor-1: `MfReAuthRequiredException` の発生条件が広すぎ

- **箇所**: `MfApiClient.java:104, 135, 161, 198, 229` 全 401 → `MfReAuthRequiredException`
- **問題**: 401 = 一律「再認証必要」だが、access_token expired は refresh で済む (再ログイン不要)。`MfOauthService.getValidAccessToken` (line 141-155) で 5 分前 refresh するため通常起きないが、稀に MF 側で revoke された場合と区別がつかない。
- **修正案**: `MfReAuthRequiredException` に `cause: TOKEN_EXPIRED | TOKEN_REVOKED | REFRESH_TOKEN_EXPIRED | UNKNOWN` enum を持たせ、UI でメッセージを切り替える。

### Minor-2: `MfTokenStatus.reAuthRequired` が常に false

- **箇所**: `MfOauthService.getStatus` (`MfOauthService.java:187-203`)
- **問題**: `MfTokenStatus` (`MfTokenStatus.java`) に `reAuthRequired` フィールドがあるが、`getStatus()` 内では常に `false` を返している。refresh_token 期限を判定するロジックがない。MF の refresh_token 期限 (通常 30 日) を保存していない (`t_mf_oauth_token` schema にない) ため計算不能。
- **修正案**: refresh_token の発行日時から経過時間で「29 日以上経過なら reAuthRequired=true」と推測ロジックを追加。または MF API に refresh 試行を一度行い 401 なら true にする probe 機能。

### Minor-3: `MfApiClient.basicAuthHeader` の Base64 エンコード前 raw が `clientId:clientSecret` のまま (RFC 6749 適合性)

- **箇所**: `MfApiClient.java:260-263`
- **問題**: RFC 6749 §2.3.1 では client_id/client_secret を **`application/x-www-form-urlencoded` でエンコードしてから** Base64 化することを要求。現実装は raw 文字列をそのまま結合。MF の client_secret に `:` や `+` などが含まれる可能性は低いが、仕様準拠のため修正推奨。
- **修正案**: `URLEncoder.encode(clientId, UTF_8) + ":" + URLEncoder.encode(clientSecret, UTF_8)` に変更。

### Minor-4: `MfIntegrationController` の `@AuthenticationPrincipal LoginUser user` で `user==null` チェックが冗長

- **箇所**: `MfIntegrationController.java:71,80,98,100,116,122,141,142,160,161,177` 等
- **問題**: `@PreAuthorize("hasRole('ADMIN')")` (line 45) で必ず認証済みのため、`user == null` は到達不能。冗長な null check が散在。
- **修正案**: `LoginUserUtil.getLoginUserInfo()` のような共通 util に集約 (`FinanceController.java:275` で既に使用)。`MfIntegrationController` も同パターンに統一。

### Minor-5: `MfIntegrationController` に debug endpoint が 6 個混在 (構造設計上の問題)

- **箇所**: `MfIntegrationController.java:267-572` の `/debug/*` 群
- **問題**: 本番 API と debug API が同一 Controller。`isDevProfile()` でガードはあるが、line 数が膨大 (Controller 全体 628 行のうち 300 行以上が debug)。CLAUDE.md の「small files (200-400 lines)」原則違反。
- **修正案**: `MfIntegrationDebugController` に分離し、`@Profile({"dev","test"})` でクラス丸ごとガード。

### Minor-6: `MfOauthService.ALLOWED_REDIRECT_URIS` が hardcoded で環境ごとに変更不可

- **箇所**: `MfOauthService.java:47-50`
- **問題**: 本番 URL `https://odamitsu-data-hub.local/...` がコード上に固定。dev / staging / prod で URL が違う場合にコード変更が必要。`feedback_dev_config_fallbacks` で fallback 据え置き方針はあるが、URL 自体は秘密ではないので環境変数化推奨。
- **修正案**: `@Value("${app.mf.allowed-redirect-uris:http://localhost:3000/finance/mf-integration/callback}")` で list 受け取り。

### Minor-7: `MfApiClient.listJournals` の URL 組み立てに RestClient の uriBuilder 未使用

- **箇所**: `MfApiClient.java:117-121`
- **問題**: `+ "?start_date=" + urlEncode(...)` で文字列連結。`RestClient.uri(URI uri)` + `UriComponentsBuilder` を使うべき。
- **修正案**: `restClient().get().uri(uriBuilder -> uriBuilder.path("/api/v3/journals").queryParam("start_date", startDate)...)` に統一。

### Minor-8: `mf-integration-callback.tsx` で `useEffect` 依存配列に `sp` のみで `setPhase` 等が含まれない

- **箇所**: `frontend/components/pages/finance/mf-integration-callback.tsx:63`
- **問題**: React 19 + Next.js 16 環境では eslint-react-hooks が `setPhase`/`setMessage` を依存配列必須としないが、`sp` は `useSearchParams()` の戻り値で参照値が変わらない場合があり、`code/state` が同じでも reload 時に effect が走らない可能性。
- **修正案**: 一度だけ実行する callback としては `useEffect(() => { ... }, [])` + 内部で `useSearchParams` 取得 or React 19 の Action パターンで直叩き。

---

## セキュリティ観点まとめ (OAuth/トークン管理)

| 項目 | 現状 | 評価 |
|------|------|------|
| **Authorization Code Grant + PKCE S256** | `MfOauthStateStore.computeCodeChallenge` (line 99-107) で SHA-256 + base64url-no-padding 実装。`MfApiClient.exchangeCodeForToken` (line 58-68) で code_verifier 送信 | OK (RFC 7636 準拠) |
| **state CSRF 防止** | `MfOauthStateStore` で `randomUrlSafe(32)` (256bit) 発行、DB 永続化、TTL 10 分、userNo 一致検証 | OK (Major-2/3 を除けば堅牢) |
| **Client Secret 暗号化** | `CryptoUtil` (Encryptors.delux = PBKDF2 + AES-256/GCM) で `m_mf_oauth_client.client_secret_enc` 保存 | OK (Critical-1 の鍵管理を除けば良好) |
| **access_token / refresh_token 暗号化** | 同 CryptoUtil で `t_mf_oauth_token.access_token_enc` / `refresh_token_enc` 保存 | OK |
| **token logging masking** | `log.info("MF access_token を refresh します（expiresAt={}）", snap.expiresAt)` 等で **token 値は出していない** が `body=...` 全体出力 (Critical-2) | NG |
| **redirect_uri ホワイトリスト** | ALLOW list 完全一致 (`MfOauthService.ALLOWED_REDIRECT_URIS`) | OK (Minor-6 で env 化推奨) |
| **rate limit / 429 対策** | `MfJournalFetcher` のみ実装、それ以外は未対応 (Major-4) | NG |
| **token rotation 後の旧 token 失効** | DB 論理削除 + active 1 件 unique index (`V021`) のみ。MF 側 revoke 未実装 | 部分的 (Major-1) |
| **scope 最小権限** | `mfc/accounting/journal.read` 等読み取り中心。書き込み権限なし | OK |
| **CLIENT_SECRET_BASIC** | `Authorization: Basic base64(id:secret)` で認証。RFC 6749 §2.3.1 の URL エンコード未対応 (Minor-3) | 軽微 NG |
| **同時 refresh race** | PostgreSQL `pg_advisory_xact_lock(REFRESH_LOCK_KEY)` で直列化 (`MfOauthService.java:179-181`) | OK (シングル DB 前提なら堅牢) |
| **callback の opener 経由通信** | `noreferrer` 付き window.open + `window.opener.postMessage` で通知。実は動かない可能性 (Critical-3) | NG |
| **DB schema integrity** | `V021` で `t_mf_oauth_token` active 1 件 unique partial index、`t_mf_oauth_state` PK + expires index | OK |

---

## 設計書 vs 実装の乖離

### 1. 勘定科目マッピングのテーブル設計が変更された

- **設計書 §6 (line 188-216)**: `m_mf_account_mapping` テーブルに admin が画面で `journal_kind × side × mf_account_id` を登録する設計。
- **実装**: `V020__drop_mf_account_mapping.sql` で削除済み。代わりに `mf_account_master` (旧: 手動 CSV 取込のテーブル) を `MfAccountSyncService` で MF API から自動同期する方針に変更。マッピングは `MfJournalReconcileService.PURCHASE_DEBIT/PURCHASE_CREDIT` 等のハードコード定数 (`MfJournalReconcileService.java:60-78`) に集約。
- **影響**: 設計書が古い。マージ前に **設計書 §6 を「mf_account_master 自動同期に変更」と書き換える** 必要あり。

### 2. リトライ実装が設計書と異なる

- **設計書 §5 (line 142-146)**: 「Spring 6 RestClient + Retry(Resilience4j) で 429/502/503/504 で最大 3 回リトライ (指数バックオフ 1s, 2s, 4s)」
- **実装**: Resilience4j 未使用。`MfJournalFetcher.callListJournalsWithRetry` (line 140-158) のみ手動 retry (1s/2s/4s)、他の API は retry なし (Major-4 参照)。
- **影響**: 設計通り全 API に retry 適用すべき。

### 3. 「公式 PoC は同時 1 リクエスト」記述に対する実装

- **設計書 §9 (line 273)**: 「同時 1 リクエスト、429 で待機」
- **実装**: 同時 1 リクエスト制約は MF 側のレート制限であり、`MfJournalFetcher` がページ間 350ms sleep + 429 retry で対応 (`RATE_LIMIT_SLEEP_MS=350`)。一方、複数 admin が同時に画面操作した場合の同時リクエスト制御はなし。
- **影響**: 軽微。複数 admin が同時 reconcile すると 429 多発リスクあり。Phase 2 で application-level rate limiter (Bucket4j 等) 導入推奨。

### 4. キャッシュ層の設計が後付け

- **設計書**: `MfJournalCacheService` に関する記述なし (Phase 1 設計書発行は 2026-04-20、cache 実装は 2026-04-23)。
- **影響**: cache invalidation strategy / TTL / OOM 対策 / 整合性 (MF 側修正の検知) が設計書未記載のため、レビューが薄い。Major-5 参照。

### 5. ヘルスチェック画面が設計書範囲外

- **設計書 (mf-integration-status)**: ヘルスチェック画面の言及なし (`design-supplier-balances-health.md` 別ファイル)。
- **影響**: Cluster F として一体化レビューする際に整合性チェックが分散。`design-mf-integration-status.md` に「軸 E ヘルスチェックは別設計書 `design-supplier-balances-health.md` 参照」と相互参照を追加すべき。

### 6. Redirect URI 設計

- **設計書 §7 (line 234-240)**: `http://localhost:3000/finance/mf-integration/callback` 1 個のみ言及。
- **実装**: `MfOauthService.ALLOWED_REDIRECT_URIS` で `http://localhost:3000/...` + `https://odamitsu-data-hub.local/...` の 2 個許可。
- **影響**: 設計書 §7 を更新し本番 URL も明記すべき。

### 7. 残高突合 (`MfBalanceReconcileService`) は別設計書

- **実装**: `MfBalanceReconcileService` は `design-supplier-partner-ledger-balance.md` §5 由来。
- **影響**: Phase 1 設計書範囲外なのでレビュー外だが、Cluster F としては OAuth 共通基盤を使うため、ヘルスチェック同様 cross-reference が望ましい。

---

## レビューチェックリスト結果

### Spring Boot / バックエンド観点

- [x] `@PreAuthorize("hasRole('ADMIN')")` でクラスレベル権限制御 (`MfIntegrationController.java:45`)
- [x] `@Transactional` 適切に付与 (Service 層、`MfOauthService` 内 readOnly / REQUIRES_NEW 区別)
- [x] DTO/Entity 分離 (`MfOauthClientRequest` / `MfOauthClientResponse` / `MMfOauthClient`)
- [x] `@Valid` + Jakarta Validation (`MfOauthClientRequest` の `@NotBlank` 等)
- [x] `@PrePersist` / `@PreUpdate` で監査フィールド自動補完 (`MMfOauthClient.java:70-78`)
- [x] Repository は JpaRepository 継承 + custom query を `@Query` で記述
- [ ] **Resilience4j 未導入** (Major-4)
- [ ] **RestClient タイムアウト未設定** (Major-4)
- [ ] **Controller 行数超過** (`MfIntegrationController` 628 行、CLAUDE.md 「max 800」 OK だが debug 分離推奨, Minor-5)
- [x] `LoginUserUtil` 共通化済 (FinanceController) ? MfIntegrationController も統一余地あり (Minor-4)

### OAuth2 / セキュリティ観点

- [x] PKCE S256 実装 (RFC 7636 準拠)
- [x] state DB 永続化 + TTL + userNo 検証 (B-3, Major-2/3 で軽微指摘)
- [x] redirect_uri ホワイトリスト完全一致 (B-4)
- [x] CLIENT_SECRET_BASIC (RFC 6749 §2.3.1) ? Minor-3 軽微
- [x] AES-256/GCM 暗号化 (CryptoUtil)
- [ ] **暗号鍵管理** (Critical-1)
- [ ] **token 関連エラーボディの masking** (Critical-2)
- [ ] **window.open noopener/tabnabbing** (Critical-3)
- [ ] **MF 側 revoke 未呼出** (Major-1)
- [ ] **authorize_url/token_url admin 編集可** (Major-6)
- [x] active token unique partial index (V021)
- [x] state TTL sweep 機能 (`MfOauthStateStore.sweep`)

### MF 連携固有観点

- [x] PKCE 必須 / state CSRF 対策実装済
- [x] 期首残 (`MfOpeningBalanceService`) で fiscal year 境界の特殊 journal を構造判定 (`isPayableOpeningJournal`)
- [x] Journal Cache 共通化 (`MfJournalCacheService`)
- [ ] **キャッシュ TTL / size cap 未設定** (Major-5)
- [x] 勘定科目マスタ自動同期 (`MfAccountSyncService`)
- [x] enum translation 機能で英→日翻訳辞書を管理 (`MfEnumTranslationService`)
- [x] scope 不足の 403 ハンドリング (`MfScopeInsufficientException`)
- [ ] **ヘルスチェック anomaly 集計 0 固定** (Major-7)
- [x] 配列 endpoint で per_page=1000 + pagination safeguard (max 50 pages, `MfJournalFetcher`)
- [x] fiscal year 境界 fallback (`buildStartDateCandidates`)

### フロントエンド観点

- [x] admin 限定表示 (`isAdmin = user?.shopNo === 0`)
- [x] TanStack Query で fetch + invalidate
- [x] `MF_DEFAULT_CONFIG` で初期値定数化 (`mf-integration.ts:194-200`)
- [x] post-message origin チェック (`mf-integration.tsx:72`)
- [ ] **`window.opener` post-message 動作確認** (Critical-3 で実機検証必要)
- [x] callback 後 `queryClient.invalidateQueries` で即時反映
- [x] secret 入力フィールドは `type="password"` + `autoComplete="new-password"`
- [x] 1 度保存後は secret を空にして UI 上から消す (`mf-integration.tsx:93`)

---

## 総合所見

OAuth2 + PKCE + state DB 永続化 + active 1 件保証 + 暗号化保存といった **基本設計は極めて堅牢**で、Critical-1 (鍵管理) を除けば商用導入できる水準。Critical-2 (ログ漏洩) と Critical-3 (window.open ハンドリング) も軽微な修正で対応可能。Major レベルでは **rate limit/retry の網羅 (Major-4)** と **ヘルスチェック anomaly 実装 (Major-7)** が運用品質に直結するため最優先で着手すべき。

設計書 (design-mf-integration-status.md) は Phase 1 の最初に書かれて以降アップデートされておらず、`m_mf_account_mapping` 廃止、cache 追加、ヘルスチェック画面追加、scope 拡張、Phase B/B''/B''' 対応などが設計書に反映されていない。Cluster F 全体を 1 本の生きた設計書としてマージするか、もしくは設計書を Phase ごとに分割して相互参照する運用ルールを `MEMORY.md` に追加するのが望ましい。

---

レビュアー: Opus サブエージェント
最終更新: 2026-05-04
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2227ms:
# マネーフォワードクラウド会計 連携状況確認画面（Phase 1: 仕訳突合）設計書

作成日: 2026-04-20
対象ブランチ: `feat/mf-integration-status`（新設予定）
前提調査: `memory/reference_mf_api.md`（2026-03-26 全プラン開放・公式 REST API / MCP）

## 1. 目的と業務上の意味

自社が月次で出力してきたマネーフォワード向け仕訳 CSV（買掛仕入・売掛売上・買掛支払）について、**MF 側に正しく取り込まれたかを画面上で確認**する。現状はユーザーが MF の画面を開いて目視で件数・合計金額を確認しているため、取り込み漏れや重複・金額相違の検知が属人的。

本画面は `GET /api/v3/journals` を自動で叩き、**取引月 × 仕訳種別**ごとに「自社 DB 上で出力した件数・合計額」と「MF 側の件数・合計額」を並べて表示し、差異があればバッジでハイライトする。

### Phase 分割

| Phase | 範囲 |
|---|---|
| **Phase 1（本設計）** | OAuth2 基盤 + 仕訳突合（件数・合計額レベル） |
| Phase 2 | 試算表（BS/PL）突合、未検証残高のドリルダウン |
| Phase 3 | MCP 連携（Claude Code からの自動仕訳登録） |

## 2. スコープ

### 本設計に含むもの

- OAuth2 Authorization Code フローによる MF 公式 REST API 認可
- `m_mf_oauth_client` / `t_mf_oauth_token` テーブル新設（Client ID/Secret 設定 + リフレッシュトークン永続化）
- `MfApiClient` Service 新設（トークン自動更新 + 429 リトライ）
- `MfJournalReconcileService` Service 新設（取引月 × 仕訳種別で突合）
- `/api/v1/finance/mf-integration/*` エンドポイント新設（ADMIN 限定）
  - `GET /oauth/authorize-url` ? 認可 URL 取得
  - `POST /oauth/callback` ? code → token 交換
  - `GET /oauth/status` ? トークン有効性・有効期限
  - `POST /oauth/revoke` ? トークン失効
  - `GET /reconcile` ? 指定月の仕訳突合結果
- 画面 `/finance/mf-integration` 新設（admin のみ表示）
  - OAuth 接続ボタン・接続状態バッジ
  - 取引月セレクタ（default = 当月前月 20 日）
  - 仕訳種別ごとの件数・合計額並列表示
  - 差異ハイライト + ドリルダウン（差異明細）

### 本設計に含まないもの

- MCP サーバー連携（Phase 3）
- 仕訳の登録・修正（MF 側は参照のみ、書込は従来 CSV 運用を継続）
- 試算表突合（Phase 2）
- 未処理明細取得（MF 側 API 未提供）
- マルチテナント対応（1 社 1 トークン運用）

## 3. 突合対象の仕訳種別とキー

MF 側 `GET /api/v3/journals` のレスポンスから、以下の条件で 3 種類の「自社由来の仕訳」を抽出する：

| 種別 | 借方 | 貸方 | 自社出力元 |
|---|---|---|---|
| **仕入仕訳** | 仕入高 | 買掛金 | `purchaseJournalIntegration` / 画面 DL |
| **売上仕訳** | 売掛金 | 売上高（物販／クリーンラボ売上高） | `salesJournalIntegration` / 画面 DL |
| **買掛支払** | 買掛金 | 現金・預金（MF 銀行連携で付与） | `payment-mf/export-verified` / Excel 経由 |

**判定ロジック**: MF 側 journal を借方勘定 / 貸方勘定のペアで分類。「仕入高 × 買掛金」なら仕入仕訳、「売掛金 × 売上高」なら売上仕訳、「買掛金 × 現金・預金系」なら買掛支払。勘定科目名は `m_mf_account_mapping`（後述）で許容名を定義し、表記ゆれを吸収。

### 突合キー

| 項目 | 自社 DB 集計 | MF 取得 |
|---|---|---|
| 取引月 | `transaction_month`（買掛/売掛）or 締め日 | `transaction_date` |
| 仕訳種別 | 出力元機能 | 勘定科目ペアで判定 |
| 比較粒度 | 件数 + 合計金額 | 件数 + 合計金額 |
| 許容差 | 0 円（件数）／ 0 円（合計額） |

集計は「件数」と「税込合計額」のみ。**個別の supplier 単位での突合は Phase 2 以降**（MF 側 journal に supplier コードを直接持たないため、補助科目・取引先で照合する設計が必要で複雑度が上がる）。

## 4. OAuth2 トークン管理

### フロー選択: Authorization Code Grant

MF 公式は Authorization Code のみ対応（Client Credentials 非対応）。したがって初回のみ admin ユーザーがブラウザで MF にログインして認可を行い、得られた refresh_token を DB に永続化して以降はサーバー側で access_token を更新する。

### DB スキーマ（新規 2 テーブル）

```sql
-- OAuth クライアント設定（通常 1 レコード）
CREATE TABLE m_mf_oauth_client (
    id                  SERIAL PRIMARY KEY,
    client_id           VARCHAR(255) NOT NULL,
    client_secret_enc   VARCHAR(512) NOT NULL,  -- AES-256 で暗号化（既存 CryptoUtil 流用）
    redirect_uri        VARCHAR(500) NOT NULL,
    scope               VARCHAR(500) NOT NULL,
    authorize_url       VARCHAR(500) NOT NULL,  -- https://app-portal.moneyforward.com/oauth/authorize
    token_url           VARCHAR(500) NOT NULL,  -- https://app-portal.moneyforward.com/oauth/token
    api_base_url        VARCHAR(500) NOT NULL,  -- https://api-accounting.moneyforward.com
    del_flg             CHAR(1) NOT NULL DEFAULT '0',
    add_date_time       TIMESTAMP NOT NULL,
    add_user_no         INT,
    modify_date_time    TIMESTAMP,
    modify_user_no      INT
);

-- トークン永続化（最新 1 レコード保持 + 履歴は論理削除で残す）
CREATE TABLE t_mf_oauth_token (
    id                  SERIAL PRIMARY KEY,
    client_id           INT NOT NULL REFERENCES m_mf_oauth_client(id),
    access_token_enc    VARCHAR(2000) NOT NULL,
    refresh_token_enc   VARCHAR(2000) NOT NULL,
    token_type          VARCHAR(50) NOT NULL DEFAULT 'Bearer',
    expires_at          TIMESTAMP NOT NULL,   -- access_token の有効期限
    scope               VARCHAR(500),
    del_flg             CHAR(1) NOT NULL DEFAULT '0',
    add_date_time       TIMESTAMP NOT NULL,
    add_user_no         INT,
    modify_date_time    TIMESTAMP,
    modify_user_no      INT
);
CREATE INDEX idx_mf_oauth_token_active ON t_mf_oauth_token(client_id, del_flg);
```

### トークン更新ロジック

1. `MfApiClient.getValidAccessToken()`: 有効期限まで残り 5 分未満 or 期限切れなら `refresh_token` で新 access_token を取得 → DB 更新
2. refresh_token 期限切れ（通常 30 日）時は 401 を返し、画面に「再認証してください」を表示
3. 同時アクセスによるトークン二重更新を避けるため `SELECT ... FOR UPDATE` or Spring `@Transactional(isolation = SERIALIZABLE)` で直列化

### Client Secret の取り扱い

- application.yml では **扱わない**（各環境で DB 投入）
- `CryptoUtil.encrypt/decrypt` で AES-256 化して保存（既存 `LoginUser.password` と同じ仕組み）
- 暗号鍵は既存の `app.crypto.key` 環境変数を流用

## 5. MF API クライアント実装

### `MfApiClient`（Service）

```java
public interface MfApiClient {
    JournalPage listJournals(LocalDate startDate, LocalDate endDate, int page, int perPage);
    TokenStatus getTokenStatus();
    String buildAuthorizeUrl(String state);
    TokenResponse exchangeCodeForToken(String code);
    void revokeToken();
}
```

- 実装は Spring 6 `RestClient` + `Retry`（Resilience4j）
- 429 (`Retry-After` ヘッダ尊重) / 502 / 503 / 504 で最大 3 回リトライ（指数バックオフ 1s, 2s, 4s）
- 401 は refresh_token フローを 1 回だけ試行 → 失敗時は `MfReAuthRequiredException` を投げる
- タイムアウト: connect 5s / read 30s
- ログには token は出さず、request-id 相当の `X-Request-Id` のみ記録

### ページング

`per_page=10000`（MF 上限）+ 取引月単位の検索なら基本 1 ページで収まる想定。合計件数が 10000 超なら `page` を進める（Phase 1 は想定しなくてよいが実装はしておく）。

## 6. 突合 Service

### `MfJournalReconcileService`

```java
public record ReconcileRow(
    JournalKind kind,         // PURCHASE / SALES / PAYMENT
    long localCount,
    BigDecimal localAmount,
    long mfCount,
    BigDecimal mfAmount,
    long countDiff,           // mf - local
    BigDecimal amountDiff,    // mf - local
    boolean matched
) {}

public record ReconcileReport(
    LocalDate transactionMonth,
    LocalDateTime fetchedAt,
    List<ReconcileRow> rows,
    boolean reAuthRequired    // MfReAuthRequiredException を catch した場合 true
) {}
```

**アルゴリズム**:

1. MF から `listJournals(startDate=月初1日, endDate=月末日, perPage=10000)` で取引を取得（月単位）
2. 各 journal を勘定科目ペアで分類（`m_mf_account_mapping` を使用）
3. 自社側は以下のクエリで期待値を取得：
   - 仕入: `t_accounts_payable_summary` で `transaction_month = :m` AND `mf_export_enabled=true` AND `tax_included_amount IS NOT NULL`（= CSV 出力済み行）を supplier × tax_rate で集約した件数・合計
   - 売上: 同じく `t_accounts_receivable_summary`
   - 支払: `t_payment_mf_import_history` の確定済みレコードから件数・合計
4. 両者を ReconcileRow に整形

### 勘定科目マッピング（動的取得 + 画面で編集）

MF 側の勘定科目は `GET /api/v3/accounts` で取得できる（`{ id, name, account_group, category, sub_accounts[] }`）。固定マッピングではなく、**admin が画面上で MF の勘定科目 id を選んで仕訳種別と紐付ける**方式にする：

```sql
CREATE TABLE m_mf_account_mapping (
    id              SERIAL PRIMARY KEY,
    journal_kind    VARCHAR(20) NOT NULL,  -- PURCHASE / SALES / PAYMENT
    side            VARCHAR(10) NOT NULL,  -- DEBIT / CREDIT
    mf_account_id   VARCHAR(100) NOT NULL, -- MF `/accounts` で取得した id
    mf_account_name VARCHAR(100) NOT NULL, -- 同期時の name（表示用キャッシュ）
    del_flg         CHAR(1) NOT NULL DEFAULT '0',
    add_date_time   TIMESTAMP NOT NULL,
    add_user_no     INT,
    modify_date_time TIMESTAMP,
    modify_user_no  INT,
    UNIQUE (journal_kind, side, mf_account_id, del_flg)
);
```

**初期シード**: `GET /accounts` で取れた科目のうち、name が `"仕入高" / "買掛金" / "売掛金" / "売上高" / "未収入金" / "仮払金" / "普通預金" / "当座預金" / "現金"` に一致するものを自動で初期マッピング（admin は画面で確認・修正）。

**マッピング編集 UI**: `/finance/mf-integration` 内にタブ「勘定科目マッピング」を追加。
- 左: `GET /accounts` の結果一覧（カテゴリでグルーピング）
- 右: 仕訳種別 × 借方/貸方ごとに SearchableSelect（複数可）で MF 科目を選択
- 「保存」で `m_mf_account_mapping` を全置換（洗い替え）

**突合時の分類ロジック**:
1. MF journal の debit `account_id` / credit `account_id` を取得
2. マッピングから `(journal_kind, side, mf_account_id)` を引き、debit+credit の種別が一致すれば分類確定
3. 一致しない journal は「種別不明」として別欄で件数のみ表示（運用中のマッピング漏れ検知）

## 7. Controller API 設計

ベースパス: `/api/v1/finance/mf-integration` / `@PreAuthorize("hasRole('ADMIN')")`

| Method | Path | 概要 |
|---|---|---|
| GET  | `/oauth/status` | `{ connected: bool, expiresAt, scope, lastRefreshedAt }` |
| GET  | `/oauth/authorize-url?state=...` | `{ url }` ? フロントがそのまま window.open |
| POST | `/oauth/callback` | `{ code, state }` → token 保存 → `{ connected: true }` |
| POST | `/oauth/revoke` | DB トークン物理削除 + MF 側 revoke エンドポイント呼び出し |
| GET  | `/reconcile?transactionMonth=YYYY-MM-DD` | `ReconcileReport` |

callback は CSRF 防止の `state` を session / signed cookie で検証。

### Redirect URI

admin は現状 `http://localhost:3000` でアプリにアクセスしているため、**MF アプリポータルに `http://localhost:3000/finance/mf-integration/callback` を登録**する。

- OAuth の redirect は「admin の PC のブラウザ」が自分自身に戻るだけなので外部公開不要
- 将来社内サーバーにデプロイしたら同画面から admin 自身が追加の URI をアプリポータルに登録すれば使い回せる（MF は 1 アプリに複数 URI 登録可）
- フロー: 画面「接続」押下 → 新タブで MF authorize_url 起動 → admin が MF で認可 → `http://localhost:3000/finance/mf-integration/callback?code=...&state=...` に戻ってくる → Next.js ページが `POST /oauth/callback` で backend に code/state を渡す → backend が token_url に exchange → DB 永続化

追加の `m_mf_oauth_client.redirect_uri` 初期値 = `http://localhost:3000/finance/mf-integration/callback`

## 8. 画面仕様 `/finance/mf-integration`

```
┌──────────────────────────────────────────────────────────┐
│ MF 連携状況                                              │
├──────────────────────────────────────────────────────────┤
│ [接続状態] ● 接続中  scope: write public  有効期限: 14:32 │
│                                     [再認証] [切断]       │
├──────────────────────────────────────────────────────────┤
│ 対象取引月: [ 2026-03 ▼ ]                   [突合実行]    │
├──────────────────────────────────────────────────────────┤
│ 種別      自社件数  自社合計  MF件数  MF合計   差分       │
│ 仕入仕訳    28件  \1,234,567    28  \1,234,567 ? 一致   │
│ 売上仕訳    42件  \9,876,543    41  \9,876,123 ?? 1件     │
│ 買掛支払    15件    \987,654    15    \987,654 ? 一致   │
│                                                 [詳細]   │
└──────────────────────────────────────────────────────────┘
```

- 未接続時は接続ボタンのみ表示、突合 UI はグレーアウト
- 「突合実行」押下 → `GET /reconcile` を TanStack Query で叩く（cache 60 秒）
- 差分行は `Badge variant="destructive"`、一致行は `variant="secondary"`
- 「詳細」は Phase 2 以降（supplier 単位での内訳）

## 9. セキュリティ・運用上の注意

- **Client Secret 管理**: DB に AES-256 暗号化、鍵は `app.crypto.key` 環境変数、バックアップから漏れないよう `backup/` 除外ルールを既存運用に合わせる
- **token logging**: Log4j2 のカスタム MessageConverter でトークン/secret をマスク（`****`）
- **画面の認可**: `@PreAuthorize("hasRole('ADMIN')")` + フロントは `user.shopNo===0` で表示切替
- **スコープ**: 初期は `public read`（仕訳参照のみ）。Phase 3 で書き込み追加時に再認可
- **revoke**: UI から切断時は DB 物理削除 + MF revoke エンドポイントも呼ぶ（ベストエフォート）
- **利用規約遵守**: 利用規約 第 9 条第 3 号「通常意図しない効果を及ぼす外部ツール」に抵触しないよう、レート制限を尊重（同時 1 リクエスト、429 で待機）

## 10. テスト戦略

- Unit: `MfApiClient` のリトライ・トークン更新ロジック（WireMock で MF モック）
- Integration: 突合 Service の分類ロジック（固定 journals JSON 入力 → ReconcileRow 出力）
- E2E (Playwright): 接続ステータス表示・突合結果表示・差分ハイライト（MF API は `page.route` でモック）

## 11. 実装フェーズ（Phase 1 内のタスク順）

1. **Infra**: DB マイグレーション `m_mf_oauth_client` / `t_mf_oauth_token` / `m_mf_account_mapping`
2. **Backend OAuth**: `MfApiClient` 骨格、`authorize-url` / `callback` / `status` / `revoke` Endpoint
3. **Backend Journal**: `listJournals` + `MfJournalReconcileService` + `/reconcile` Endpoint
4. **Frontend 接続 UI**: 接続/切断ボタン、ステータス表示
5. **Frontend 突合 UI**: 取引月セレクタ、結果テーブル、差分ハイライト
6. **E2E + Unit テスト**
7. **本番投入**: アプリポータルで本番 Client 登録、DB に投入、OAuth 認可フロー実施

## 12. 確定事項（2026-04-20 ユーザー回答）

1. **接続主体**: 会社共通 1 アカウント（admin が初回認可、全管理者が共有）
2. **勘定科目マッピング**: `GET /accounts` で動的取得 + 画面で編集（固定初期値は name 一致で自動シード、admin が修正可能）
3. **自社側の支払件数**: `t_payment_mf_import_history` を正とする
4. **突合タイミング**: 画面を開いても自動実行しない。「突合実行」ボタン押下時のみ API を叩く
5. **Redirect URI**: `http://localhost:3000/finance/mf-integration/callback`（admin は localhost 運用のため外部公開不要）

## 13. 実装着手前の TODO（ユーザー側）

1. MF アプリポータル https://app-portal.moneyforward.com/ で新規アプリ登録
   - redirect URI: `http://localhost:3000/finance/mf-integration/callback`
   - scope: `public read`（Phase 1 は参照のみ）
2. Client ID / Client Secret を控えておく（実装後に画面 or DB 直投入で登録）
3. 仕訳を登録しているマネーフォワードクラウド会計の事業者が 1 つのみか確認（複数ある場合は事業者選択 UI が Phase 2 で必要）

## 参考

- MF API 仕様書: https://developers.api-accounting.moneyforward.com/
- MF 開発者サイト: https://developers.biz.moneyforward.com/
- アプリポータル: https://app-portal.moneyforward.com/
- 本プロジェクト既存 MF 関連:
  - `design-payment-mf-import.md` / `design-payment-mf-aux-rows.md`
  - `design-accounts-receivable-mf.md`
  - `design-mf-cashbook-import.md`
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3220ms:
# コードレビュー: MF 連携基盤 (Cluster F)

レビュー日: 2026-05-04
ブランチ: `refactor/code-review-fixes`
レビュアー: Opus サブエージェント
対象スコープ:
- `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/**`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/MfHealthCheckService.java`
- `backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java`
- `backend/src/main/java/jp/co/oda32/dto/finance/MfOauthClient*.java`
- `backend/src/main/java/jp/co/oda32/domain/model/finance/{MMfOauthClient,TMfOauthToken}.java`
- `backend/src/main/java/jp/co/oda32/domain/repository/finance/{MMfOauthClientRepository,TMfOauthTokenRepository,MfAccountMasterRepository}.java`
- `frontend/components/pages/finance/mf-integration{,-callback}.tsx` / `mf-health.tsx` / `Mf*Tab.tsx`
- `frontend/types/{mf-integration,mf-health}.ts`

## 前提

設計レビュー (`claudedocs/design-review-mf-integration.md`) で報告済の指摘 (Critical 3 / Major 7 / Minor 8) は本レビューでは **再掲しない**。本レビューは設計レビューでカバーされなかったコード固有の問題、特に
- 暗号鍵 / token のライフサイクル詳細
- HTTP クライアント設定 (timeout/retry)
- DB 永続化トランザクション境界
- フロント側のセキュリティ詳細
- Controller / Service の保守性

に絞った。

## サマリー

- 総指摘件数: **Blocker 0 / Critical 2 / Major 8 / Minor 11** (合計 21 件)
- 承認状態: **Needs Revision**
- セキュリティ系の新発見 (設計レビュー未指摘):
  1. **C-impl-1**: `RestClient.Builder` の DI を **Bean 化せずデフォルト共有** で取得しており、connect/read timeout が **無限** になる可能性。MF 側 hang で全 admin スレッドが thread pool ごと枯渇する Denial-of-Service 性のリスク。
  2. **C-impl-2**: `MfApiClient.exchangeCodeForToken` / `refreshToken` のレスポンス body を `MfTokenResponse` (record) に直接バインドした際、**MF が将来追加するフィールド** (`id_token`, `refresh_token_expires_in` 等) は捨てられる。それ自体は OK だが `toString()` が record 自動生成で `accessToken` 値そのものを含むため、Lombok `@Slf4j` 等の意図しない `log.debug("res={}", res)` 一行で **生 access_token がログ漏洩** する。現状は出していないが防御層が無い。

詳細は下記。

---

## Critical

### C-impl-1: `RestClient` の connect/read timeout が未設定 (DoS リスク)

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java:34`
  ```java
  private final RestClient.Builder restClientBuilder;
  ...
  public RestClient restClient() {
      return restClientBuilder.build();
  }
  ```
- **問題**:
  - Spring Boot のデフォルト `RestClient.Builder` は `JdkClientHttpRequestFactory` 系で、`requestFactory()` を明示しない限り **read timeout も connect timeout も無限**。
  - Project 内の `backend/src/main/java/jp/co/oda32/config/` 配下を全 Grep してもカスタム `ClientHttpRequestFactory` の Bean 定義は無し (`Glob backend/src/main/java/jp/co/oda32/config/*.java` 結果に http 設定なし)。
  - 設計レビュー Major-4 で「timeout 未設定」は記載されているが、リトライと混在して扱われていたためコード固有の DoS 性 (= MF API hang で `MfOauthService.getValidAccessToken` 経由のすべての ledger / reconcile / health check 操作が永久ブロック) を再強調する。
- **影響**:
  - MF 側 LB が SYN-ACK を返さないケースや slow loris で、Tomcat default の 200 worker thread が全てブロック → ヘルスチェック含むすべての `/api/**` リクエストが 503 化。
  - `RestClient` は `Mono` ではなく **同期 blocking** なので InterruptedException で抜けることもできず、process kill しか復旧手段なし。
- **修正案**:
  ```java
  @Configuration
  class MfHttpClientConfig {
      @Bean(name = "mfRestClient")
      RestClient mfRestClient() {
          var factory = new SimpleClientHttpRequestFactory();
          factory.setConnectTimeout(Duration.ofSeconds(5));
          factory.setReadTimeout(Duration.ofSeconds(30));
          return RestClient.builder().requestFactory(factory).build();
      }
  }
  ```
  かつ `MfApiClient` で `@Qualifier("mfRestClient") RestClient client` を field injection。共有 `RestClient.Builder` から builder を都度 `build()` する現行実装も、毎回 factory 構築のコストはあるが thread-safe ではあるので timeout 設定さえあれば問題はない。
- **追加注意**: `BCartHttpClientConfig` (`backend/src/main/java/jp/co/oda32/batch/bcart/config/BCartHttpClientConfig.java`) は B-CART 用に既存しているため、それを参考に MF 用も追加するのが既存パターンと整合。

---

### C-impl-2: `MfTokenResponse` の `toString()` が `access_token` / `refresh_token` を漏洩

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfTokenResponse.java:8`
  ```java
  public record MfTokenResponse(
          @JsonProperty("access_token") String accessToken,
          @JsonProperty("refresh_token") String refreshToken,
          ...
  ) {}
  ```
- **問題**:
  - Java `record` は `toString()` を自動生成し全フィールドを含む (`MfTokenResponse[accessToken=eyJhbGc..., refreshToken=...]`)。
  - 今回コード上 `log.warn("...{}", token)` 等の暴露経路は無いが、**例外スタックトレース** (Spring AOP の方が `arg` を toString して error log に出すケースあり) や `@RestController` の global exception handler で argument が dump されるケース、デバッグ時の `System.out.println(res)` で偶発的に流出する。
  - 設計レビュー Critical-2 (body 全体ログ) と独立した「DTO 自身が漏洩源」になる構造的問題。
- **影響**:
  - 一度漏洩した access_token は最大 1 時間有効。refresh_token は 30 日相当 (MF 仕様)。攻撃者に渡れば MF 仕訳閲覧。
- **修正案**:
  - record で `toString` を override:
    ```java
    public record MfTokenResponse(...) {
        @Override public String toString() {
            return "MfTokenResponse[tokenType=" + tokenType
                + ", expiresIn=" + expiresIn
                + ", scope=" + scope
                + ", accessToken=***, refreshToken=***]";
        }
    }
    ```
  - 同様の対処を `MfOauthService.TokenSnapshot` (`MfOauthService.java:255`) にも適用。`plainSecret`, `accessToken`, `plainRefresh` がフィールドにあり同じリスク。
  - グローバルに `@JsonIgnoreProperties` ではなく `Object#toString` を完全に潰すこと。

---

## Major

### M-impl-1: `MfOauthService.handleCallback` の `cryptoUtil.decrypt(client_secret)` が DB tx 内で実行される

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java:121`
  ```java
  @Transactional
  public void handleCallback(String code, String state, Integer userNo) {
      Optional<String> verifierOpt = stateStore.verifyAndConsume(state, userNo);
      ...
      String plainSecret = cryptoUtil.decrypt(client.getClientSecretEnc());
      MfTokenResponse token = mfApiClient.exchangeCodeForToken(client, plainSecret, code, verifierOpt.get());
      persistToken(client, token, userNo);
  }
  ```
- **問題**:
  - `getValidAccessToken()` (line 141) では「HTTP 呼び出しは tx 外、永続化は REQUIRES_NEW」というパターン (= B-W6) を採用しているが、`handleCallback` だけが **HTTP 呼び出し (= MF token endpoint への 5?数十秒) を `@Transactional` 内で行う**。
  - MF token endpoint レイテンシ + slow MF response 中、DB connection が pool から占有されたまま。同時に複数 admin が認可フローを試行すると HikariCP のコネクションプールを枯渇させる。
- **影響**:
  - 高頻度操作ではないので即障害ではないが、設計上の一貫性を欠く。`getValidAccessToken` で工夫した B-W6 ガードが callback だけ抜けている。
- **修正案**:
  - `handleCallback` も同じパターンに分割:
    1. read-only tx で client + verifier 読み取り (state は使い捨てなのでこの時点で消費)
    2. tx 外で `exchangeCodeForToken`
    3. REQUIRES_NEW で `persistToken`
  - もしくはコメントで「callback は admin が手動で 1 回しか呼ばないため tx 内 HTTP 許容」と明示する (運用前提 + テスト確認)。

### M-impl-2: `MfOauthStateStore.verifyAndConsume` が delete を「条件チェック前」に実行 (race + 攻撃時 enumeration)

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthStateStore.java:74-86`
  ```java
  TMfOauthState e = opt.get();
  // 使い捨てなので即削除
  stateRepository.deleteById(state);
  if (e.getExpiresAt().toInstant().isBefore(Instant.now())) {
      log.info("MF OAuth state 有効期限切れ");
      return Optional.empty();
  }
  if (e.getUserNo() == null) {
      if (userNo != null) return Optional.empty();
  } else if (!e.getUserNo().equals(userNo)) {
      return Optional.empty();
  }
  ```
- **問題**:
  1. **意図的問題**: 期限切れでも、ユーザ不一致でも、レコードはまず削除される。これは「同じ state を 2 回使えないようにする」ためで設計通り。ただし攻撃者が無効 state をブルートフォースで送ると、たまたま誰かの有効 state が偶然衝突した場合に **正規ユーザーの認可フローを巻き込んで破壊** できる (DoS)。state は 256bit ランダムなので衝突確率は実用上ゼロだが、設計コメントが無い。
  2. **race**: `findById → deleteById` が別 SQL なので、極稀に同 state を 2 リクエスト同時処理した場合に両方が「取り出せた」と判定し callback が 2 回走る (片方は token 上書きされる)。トークン上書きは uniq partial index で active 1 件保証なのでデータ破損はないが、verifier の 2 重消費自体は仕様違反 (RFC 7636 §4.6)。
  3. PostgreSQL の `DELETE ... RETURNING *` を使えば 1 SQL で atomic に取り出せる。
- **影響**: 軽微 (実害はほぼ無いが、ベストプラクティス的には削除と検証を atomic にすべき)。
- **修正案**:
  - JPQL で `@Modifying @Query("DELETE FROM TMfOauthState s WHERE s.state=:s") Optional<TMfOauthState>` は JPA 単体だと returning 不可なので、native `DELETE FROM t_mf_oauth_state WHERE state = ? RETURNING user_no, code_verifier, expires_at` で 1 SQL atomic 化。

### M-impl-3: callback 後の `m_mf_oauth_client.modifyDateTime` が更新されず監査が欠落

- **場所**: `backend/src/main/java/jp/co/oda32/domain/model/finance/MMfOauthClient.java:77`
  ```java
  @PreUpdate
  void preUpdate() {
      modifyDateTime = Timestamp.from(java.time.Instant.now());
  }
  ```
  および `MfOauthService.persistToken` (line 213-230) → `tokenRepository.save(token)` のみで `clientRepository.save(client)` は呼ばない。
- **問題**: token は新規 insert で `t_mf_oauth_token.add_date_time` に履歴が残るが、`m_mf_oauth_client` 自体は更新されない。「最終認可成功時刻」を保持するカラム (例: `last_authorized_at`) が無いため、運用 admin が画面で「いつ認可した token か」を厳密に追えるのは `t_mf_oauth_token` の最新 `add_date_time` のみ。
- **影響**:
  - `MfTokenStatus.lastRefreshedAt` (`MfOauthService.java:199-201`) は `t.modifyDateTime ?? t.addDateTime` で代替しているが、refresh と新規認可を区別不能。
- **修正案**:
  - `MMfOauthClient` に `last_authorized_at` カラム追加 + `handleCallback` の最後で client.lastAuthorizedAt = now() で save。
  - もしくは `t_mf_oauth_token` に `obtain_kind` enum (`AUTHORIZATION_CODE` / `REFRESH_TOKEN`) を追加。

### M-impl-4: `MfApiClient.postToken` のレスポンス検証が緩く `NullPointerException` リスク

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java:244-247`
  ```java
  if (res == null || res.accessToken() == null || res.refreshToken() == null) {
      throw new IllegalStateException("MF token エンドポイントから不正なレスポンス");
  }
  ```
- **問題**:
  - `expiresIn` が null の場合 (MF が省略した場合) `MfOauthService.persistToken` (line 216) で `res.expiresIn() != null ? res.expiresIn() : 3600L` と fallback しているので OK。
  - しかし `tokenType` が null の場合は `MfTokenResponse` 自身でなく `persistToken` (line 222) で `res.tokenType() != null ? res.tokenType() : "Bearer"` と fallback するので OK。
  - 一方 `scope` は MF が省略した場合 `null` のまま `t_mf_oauth_token.scope` に保存 (column nullable=true なので OK)、後続で `MfTokenStatus.scope` に流す時も null OK。
  - **ただし** `refresh_token` が省略 (= MF が rotate しない sliding mode) の場合、`postToken` が `IllegalStateException` を投げて再認可必要扱いになる。MF 仕様上 refresh_token rotation を ON/OFF 切替できる場合、sliding mode の admin は永久に認可できない。
- **影響**: MF 側設定次第では機能不全。
- **修正案**:
  - refresh_token が null の場合は **既存の DB 上 refresh_token を維持** する (= rotate なし)、access_token のみ更新するパスを追加。`MfOauthService.persistToken` に「refresh が null なら旧値を再利用」を実装。

### M-impl-5: `MfJournalCacheService` が shop 単位の cache を **全プロセス共通の `synchronized` メソッド**で守る

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfJournalCacheService.java:56`
  ```java
  public synchronized CachedResult getOrFetch(Integer shopNo, ...) {
  ```
- **問題**:
  - 設計レビュー Major-5 で「synchronized 単一 lock で他 shop も待ち」と既出だが、**コード固有の細部**として `getStats()` (line 91) は **non-synchronized** で `cachesByShop.entrySet()` を回す。`MonthlyCache.fetchedAtByMonth` は ConcurrentHashMap なので iteration は安全だが、`getOrFetch` 中の `storeFetched` (line 137-141) で
    ```java
    cache.journalsByMonth.put(month, byMonth.getOrDefault(month, List.of()));
    cache.fetchedMonths.add(month);
    cache.fetchedAtByMonth.put(month, now);
    ```
    の 3 行は連続 mutation だが**個別の atomic** で、`getStats` 中に途中状態 (months だけ更新、fetchedAt 未更新) を読む可能性がある。実害は「最古/最新時刻が一瞬ずれる」程度なので軽微だが、テスト時の flake 原因になりうる。
- **影響**: 軽微 (UI 表示のみ、永続化や判断ロジックには使われない)。
- **修正案**:
  - `getStats()` も同じ `synchronized` か、各 ShopStats 構築を `cache` 単位で synchronized ブロックに入れる。
  - または `MonthlyCache` を不変な snapshot record に置き換え、更新時は CAS で全体差し替えするパターン (Caffeine の AsyncCache に置換すれば全部解決)。

### M-impl-6: `MfHealthCheckService` が `mfOauthService.getStatus()` を呼び **かつ** `journalCache.getStats()` を呼ぶが両者の整合性チェックなし

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/MfHealthCheckService.java:55-67`
- **問題**:
  - `getStatus()` で「OAuth 接続中」を返しても、journalCache が空なら過去 fetch がなく、本当に MF と疎通できているかは不明。逆に cache が古いまま OAuth が disconnect された場合、画面では「OAuth: 未接続 / cache: 12 月分保持」と一見矛盾した状態が表示される (実際にはキャッシュが残っているだけ)。
  - 設計レビュー Major-7 (anomaly 0 固定) と並ぶ「health 画面の信頼性」問題。`judgeHealth` (`frontend/types/mf-health.ts:60`) は OAuth disconnect を red 判定するが、cache 古さは無視するため、MF 側が修正された journal は検知できない。
- **影響**: 中 (UX の信頼性に直結)。
- **修正案**:
  - `MfHealthCheckService.check()` の最後で軽量 ping (`/api/v3/offices` 等で 200 を確認) を追加し、`MfOauthStatus` に `apiReachable` フィールドを追加。失敗時は yellow 判定。
  - cache の `oldestFetchedAt` が 24h 以上前なら frontend で yellow 判定に組み込む。

### M-impl-7: `MfIntegrationController.callback` が `@PreAuthorize("hasRole('ADMIN')")` クラスレベルで保護されているが、callback URL 自体は MF からブラウザリダイレクト経由

- **場所**: `backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java:45,113`
- **問題**:
  - クラスレベル `@PreAuthorize("hasRole('ADMIN')")` は **API 呼び出し時** の認可。ブラウザの redirect から `/finance/mf-integration/callback` は frontend ページであり、そこから JS で `POST /api/v1/finance/mf-integration/oauth/callback` を呼ぶ際に admin の JWT が Authorization header に乗る。
  - frontend の `mf-integration-callback.tsx:39-40` が `api.post('/finance/mf-integration/oauth/callback', { code, state })` を呼ぶ際、`api-client.ts:18` で `localStorage.getItem('token')` を Authorization に乗せる。
  - **問題点**: callback URL が MF 経由なので、攻撃者が **被害者管理者を別の認可リクエストにリダイレクト** させて被害者の admin token で他社の MF アカウントを bind することが可能 (= state 検証で防げているはず)。
  - state 検証 (`MfOauthStateStore.verifyAndConsume`) で「state 発行ユーザーと callback 時 userNo 一致」を見ているため、攻撃者の admin の state を被害者 admin が踏むケースは弾けるが、その逆 (被害者 state を攻撃者が踏む) も `userNo` 比較で弾く。OK。
  - ただし「state 発行ユーザーが既にログアウト → 別 admin がログイン → 同じブラウザで callback」の場合、設計レビュー Major-3 で指摘の通り 403 が返るが、**メッセージが汎用すぎて運用混乱**。
- **影響**: 軽微 + UX 不良。
- **修正案**:
  - 設計レビュー Major-3 と連動して、403 レスポンスに「state は別 admin で発行されています。発行した admin で callback を完了してください」と明示。

### M-impl-8: フロント `mf-integration-callback.tsx` の `useEffect` が StrictMode で 2 回実行 → state 二重消費

- **場所**: `frontend/components/pages/finance/mf-integration-callback.tsx:22-63`
  ```ts
  useEffect(() => {
      const code = sp.get('code')
      const state = sp.get('state')
      ...
      api.post(...).then(...)
  }, [sp])
  ```
- **問題**:
  - React 19 + Next.js 16 dev mode の `<StrictMode>` (`app/layout.tsx` で有効化されている前提) では `useEffect` が 2 回実行される。1 回目で `verifyAndConsume` が state を消費 (DELETE) し、2 回目で `findById` が空になり 403。dev 環境での DX を著しく損なう。
  - 設計レビュー Minor-8 で「依存配列 sp のみ」と指摘済みだが、**StrictMode 二重実行**自体への対策が無い。
- **影響**: dev 環境での認可フロー検証が常に 403 になり、開発者に「実装バグでは?」という誤解を生む。
- **修正案**:
  ```tsx
  const calledRef = useRef(false)
  useEffect(() => {
      if (calledRef.current) return
      calledRef.current = true
      ...
  }, [])
  ```
  もしくは React 19 の `Action` を使い、useEffect 自体を排除する。

---

## Minor

### m-impl-1: `MfApiClient.urlEncode` が `null` を空文字に置換 (silent failure)

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java:265-267`
  ```java
  private static String urlEncode(String s) {
      return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
  }
  ```
- **問題**: 必須パラメータ (state, code_challenge, redirect_uri 等) が null だと空文字で encode され、MF 側で 400 エラーになる。原因特定が難しい。
- **修正案**: `Objects.requireNonNull(s, "url-encoded 引数が null")` で fail-fast、または `Optional` を返すバージョンを別メソッド化。

### m-impl-2: `MfApiClient.buildAuthorizeUrl` が String concatenation で組み立て、URL injection の余地

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java:40-53`
  ```java
  StringBuilder sb = new StringBuilder(client.getAuthorizeUrl());
  sb.append(client.getAuthorizeUrl().contains("?") ? "&" : "?");
  ```
- **問題**:
  - `client.authorizeUrl` 自体に `?fragment` や `#fragment` が含まれている場合の挙動が未テスト。
  - 設計レビュー Major-6 で admin 編集可能と指摘済 → ALLOW list 化されれば軽微化。
- **修正案**: `UriComponentsBuilder.fromHttpUrl(authorizeUrl).queryParam(...).build().toUriString()` を使用。MF 側はクエリ順で動作仕様化されていないので OK。

### m-impl-3: `TMfOauthToken` に refresh_token 発行日時カラムが無い

- **場所**: `backend/src/main/java/jp/co/oda32/domain/model/finance/TMfOauthToken.java:43`
- **問題**: `expires_at` は access_token の有効期限のみ。refresh_token の発行日時は `add_date_time` から推測するしかなく、設計レビュー Minor-2 (`reAuthRequired` が常に false) と連動して機能不全。
- **修正案**: `refresh_token_issued_at TIMESTAMP NOT NULL` カラム追加 + `getStatus()` で `Instant.now() > issuedAt + 29日` なら true。

### m-impl-4: `MfAccountSyncService.apply` の DELETE が物理削除 (audit log 残らない)

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfAccountSyncService.java:62-64`
  ```java
  if (!plan.toDelete.isEmpty()) {
      mfAccountMasterRepository.deleteAllInBatch(plan.toDelete);
  }
  ```
- **問題**:
  - `mf_account_master` を物理削除すると、後で「あの sub_account_name は何だった?」を遡及できない。MF 側が誤って削除して再同期した場合の復旧が手動対応に。
  - frontend `MfAccountSyncTab.tsx:101` で「物理削除」と注意喚起しているが、admin が誤実行すると取り返せない。
- **修正案**:
  - `mf_account_master` に `del_flg` を追加して論理削除に変更 (B-CART 配下と整合)、もしくは削除前に `mf_account_master_archive` テーブルにコピー。

### m-impl-5: `MfApiClient.getRaw` の戻り型が `JsonNode` で型安全性ゼロ (debug only)

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfApiClient.java:89-108`
- **問題**: dev 限定 endpoint (`MfIntegrationController.debug*`) でしか使われていない。本番では `isDevProfile()` ガードがあるため OK だが、Service 層に debug-only メソッドが混在しているのは責務分離違反。
- **修正案**: `MfApiClient` から `getRaw` を削除し、`MfIntegrationController.debugTrialBalanceRaw` 等を新設の `MfDebugApiClient` に分離。設計レビュー Minor-5 (Controller debug 分離) と連動。

### m-impl-6: `MfJournalReconcileService.reconcile` が単一 page (per_page=10000) で取得し pagination 未対応

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfJournalReconcileService.java:56,96`
  ```java
  private static final int PER_PAGE = 10000;
  ...
  List<MfJournal> mfJournals = mfApiClient.listJournals(client, accessToken, date, date, 1, PER_PAGE).items();
  ```
- **問題**:
  - 1 日分なので通常 10000 件以下で済むが、月末締め日に大量取込が走ると超える可能性あり。`MfJournalFetcher` のような pagination loop が無いため、page 2 以降が落ちて静かにデータ抜け。
  - MF 側 `per_page` の上限仕様が記載されていないが、設計書 §5 で「max 1000」想定なら 10000 指定は仕様違反でレスポンス 400 の可能性。
- **修正案**:
  - `MfJournalFetcher.fetchJournalsForPeriod` を再利用 (期間 1 日でも問題なし、cache も自動で乗る)。

### m-impl-7: `MfOauthService.upsertClient` が Lombok `@Data` の MMfOauthClient に対し setter で mutate (immutability 原則違反)

- **場所**: `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java:84-95`
  ```java
  existing.setClientId(requested.getClientId());
  if (plainClientSecret != null && !plainClientSecret.isBlank()) {
      existing.setClientSecretEnc(cryptoUtil.encrypt(plainClientSecret));
  }
  ...
  ```
- **問題**:
  - CLAUDE.md 「Immutability: always create new objects, never mutate」原則違反。
  - JPA Entity は managed state なので setter mutation で動作はするが、`requested` と `existing` の両 instance を持って手動 copy する現在の書き方はバグ温床 (今後フィールド追加時に copy 漏れ)。
- **修正案**: `existing.toBuilder().clientId(...).clientSecretEnc(...).modifyDateTime(...).build()` で immutable copy → save。Lombok `@Builder(toBuilder = true)` を `MMfOauthClient` に追加。

### m-impl-8: フロント `MF_DEFAULT_CONFIG.scope` がハードコードされた space-separated 文字列

- **場所**: `frontend/types/mf-integration.ts:196`
  ```ts
  scope: 'mfc/accounting/journal.read mfc/accounting/accounts.read mfc/accounting/offices.read mfc/accounting/taxes.read mfc/accounting/report.read',
  ```
- **問題**: scope を追加・削除する際に文字列編集ミスでスペース重複や typo が起きやすい。typo すると MF 側で「無効 scope」エラーになる。
- **修正案**: `const SCOPES = ['mfc/accounting/journal.read', ...] as const; export const MF_DEFAULT_SCOPE = SCOPES.join(' ')` で配列管理 + Set で重複検知。

### m-impl-9: フロント `mf-integration.tsx` の `useEffect` (line 54-67) が `clientQuery.data` 全体を依存に

- **場所**: `frontend/components/pages/finance/mf-integration.tsx:67`
  ```tsx
  }, [clientQuery.data])
  ```
- **問題**:
  - TanStack Query は同じデータでも new object reference を返すことがあり、`clientQuery.data` 参照変化で effect が無限走り (form リセット → query refetch ループ) する可能性。
  - 現状 `setForm((prev) => ...)` で merge しているため値が変わらないと React は re-render しないはずだが、refetchInterval があると毎分 effect が走って `setForm` を呼ぶ → 何も変わらないが React 19 の StrictMode で警告が出る場合あり。
- **修正案**: `[clientQuery.data?.id, clientQuery.data?.clientId]` 等、key field のみ依存配列に。

### m-impl-10: フロント `mf-health.tsx:144` の `unverifiedCount` が summary 由来 + anomaly 由来の **2 重カウント**

- **場所**: `frontend/components/pages/finance/mf-health.tsx:131-144`
  ```tsx
  <Row label="未検証">{data.summary.unverifiedCount}</Row>
  ...
  <Row label="未検証 (当月)">{data.anomalies.unverifiedCount}</Row>
  ```
- **問題**:
  - 同じ「未検証件数」が summary タブと anomaly タブの両方に表示される。`MfHealthCheckService.buildAnomalyStats` (`MfHealthCheckService.java:94-96`) で `summary.unverifiedCount` と同じ計算をしている。設計レビュー Major-7 で 0 固定の他 3 種別と並列に「未検証」のみ実装されている形だが、**重複表示**で UX が冗長。
- **修正案**: anomaly 側の「未検証 (当月)」は summary と同値なので、anomaly カードからは削除し summary 側でハイライト表示に統一。

### m-impl-11: `MfIntegrationController` が `MfBalanceReconcileService` 以外で `MfScopeInsufficientException` を 403 にマップしていない

- **場所**: `backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java:204-211, 240-248, 252-262`
- **問題**:
  - `/reconcile` (`reconcile()` line 198) と `/account-sync/preview` `/apply` のハンドラは `MfReAuthRequiredException` (401) と `IllegalStateException` (422) しか catch しない。
  - 内部で `MfApiClient.listJournals` (line 137) や `listAccounts` は 403 → `MfScopeInsufficientException` を投げるが、Controller がこれをキャッチしないため Spring の global exception handler (おそらく 500) に流れる。
- **影響**: scope 不足時のユーザー誘導が `/balance-reconcile` だけ綺麗で、他は 500 エラー画面。
- **修正案**: `@ExceptionHandler(MfScopeInsufficientException.class)` を Controller に追加して全エンドポイントで 403 + `requiredScope` を返却。

---

## OAuth / トークン管理 セキュリティチェックリスト (コード固有)

設計レビューと重複しない項目に絞った再評価。

| 項目 | 結果 | コメント |
|---|---|---|
| HTTP timeout 設定 | NG (C-impl-1) | RestClient.Builder のデフォルト = 無限 |
| HTTP retry 全 API 適用 | NG (Major-4 既出) | journals のみ |
| token DTO の toString マスキング | NG (C-impl-2) | record auto toString が漏洩源 |
| TokenSnapshot record の toString マスキング | NG (C-impl-2) | 同上、`MfOauthService.java:255` |
| state DELETE と verify の atomic 化 | NG (M-impl-2) | findById → deleteById 別 SQL |
| handleCallback の HTTP tx 外実行 | NG (M-impl-1) | `getValidAccessToken` パターンと不整合 |
| refresh_token 発行日時保持 | NG (m-impl-3) | re-auth 警告ロジックに必要 |
| client 側 refresh_token rotation 不要対応 | NG (M-impl-4) | sliding mode で永久に再認可になるバグ |
| state 同時 callback race | 軽微 NG (M-impl-2) | uniq partial index で永続データは保護されるが、verifier が 2 重消費される |
| audit (last_authorized_at) | NG (M-impl-3) | refresh と新規認可を区別不能 |
| account_sync 物理削除 | 軽微 NG (m-impl-4) | audit 残らない |
| react StrictMode callback 二重実行 | NG (M-impl-8) | dev のみだが体験不良 |
| journals pagination 一貫適用 | NG (m-impl-6) | reconcile で page 2 以降未取得 |

---

## 設計レビューと重複指摘の再確認 (コード上の状態)

| 設計レビュー指摘 | 該当コード | コード上の対応状況 |
|---|---|---|
| Critical-1 (salt fallback) | `application-dev.yml:21` | 未対応 (運用ポリシーで据え置き) |
| Critical-2 (token error body log) | `MfApiClient.java:251` 等 | 未対応、本レビュー C-impl-2 と独立した別経路あり |
| Critical-3 (window.open noreferrer) | `mf-integration.tsx:103` | 未対応、本レビュー M-impl-8 と関連 (StrictMode 別問題) |
| Major-1 (refresh_token rotation revoke) | `MfOauthService.java:206-211` | 未対応 |
| Major-2 (state TTL `<` 比較) | `MfOauthStateStore.java:75` | 未対応、本レビュー M-impl-2 で atomic 化提案 |
| Major-3 (callback userNo) | `MfOauthStateStore.java:80-84` | 未対応、本レビュー M-impl-7 で UX 改善提案 |
| Major-4 (retry 全 API 適用) | `MfApiClient.java:115-143` 等 | 未対応、本レビュー C-impl-1 で timeout もセットで指摘 |
| Major-5 (cache TTL/OOM) | `MfJournalCacheService.java:21-43` | 未対応、本レビュー M-impl-5 で `getStats` 整合性追記 |
| Major-6 (admin 編集可 URL) | `MfOauthService.upsertClient` line 79 | 未対応 |
| Major-7 (anomaly 0 固定) | `MfHealthCheckService.java:103-105` | 未対応、本レビュー m-impl-10 で表示重複追記 |

---

## 対応表 (本レビュー指摘のみ)

| ID | 判定 | 推奨対応 |
|---|---|---|
| C-impl-1 | Must Fix | `MfHttpClientConfig` Bean 追加、`SimpleClientHttpRequestFactory` で connect 5s / read 30s |
| C-impl-2 | Must Fix | `MfTokenResponse` / `TokenSnapshot` の `toString()` を override |
| M-impl-1 | Should Fix | `handleCallback` を read-only tx → tx 外 HTTP → REQUIRES_NEW persist に分割 |
| M-impl-2 | Should Fix | `DELETE ... RETURNING` で state を atomic 取り出し |
| M-impl-3 | Should Fix | `m_mf_oauth_client.last_authorized_at` カラム追加 |
| M-impl-4 | Should Fix | refresh_token null fallback (旧値維持) を `persistToken` に追加 |
| M-impl-5 | Could Fix | `getStats()` を `synchronized` 化、または Caffeine 置換 |
| M-impl-6 | Should Fix | health check に MF 軽量 ping 追加、cache age 警告 |
| M-impl-7 | Could Fix | 403 エラーメッセージ詳細化 |
| M-impl-8 | Must Fix (DX) | callback `useEffect` を `useRef` で once-only 実行 |
| m-impl-1 | Could Fix | urlEncode null fail-fast |
| m-impl-2 | Could Fix | `UriComponentsBuilder` 移行 |
| m-impl-3 | Should Fix | refresh_token_issued_at カラム追加 |
| m-impl-4 | Could Fix | `mf_account_master` 論理削除化 |
| m-impl-5 | Could Fix | debug client 分離 |
| m-impl-6 | Should Fix | `MfJournalFetcher` 再利用 |
| m-impl-7 | Could Fix | `@Builder(toBuilder=true)` で immutable copy |
| m-impl-8 | Could Fix | scope 配列管理 |
| m-impl-9 | Could Fix | useEffect 依存配列を id のみに |
| m-impl-10 | Could Fix | anomaly タブから unverified 削除 |
| m-impl-11 | Should Fix | `MfScopeInsufficientException` を全エンドポイントで 403 |

**Approval status**: Needs Revision → C-impl-1, C-impl-2, M-impl-8, m-impl-6 を最優先で対応すれば商用導入可能水準に到達。

---

## 総評

設計レビューで OAuth 基盤としての堅牢性が確認されており、コード実装も大筋で設計通り (PKCE / state DB 永続化 / 暗号化 / advisory lock 等)。本レビューでは **設計書では見えにくい実装層のリスク** を中心に指摘した:

- **Critical 2 件はいずれも「漏洩経路の追加」**: ログ経由 (設計レビュー C-2) と DTO toString 経由 (本 C-impl-2) は別物で両対応必要。RestClient timeout 未設定 (C-impl-1) は OAuth と無関係に DoS リスク。
- **Major 8 件のうち M-impl-1/2/3/4 は MF 側挙動依存の堅牢性問題** (sliding mode、state race、audit、HTTP tx 占有)。運用開始前に対応推奨。
- **M-impl-8 は dev 体験を著しく損なう** ため StrictMode 対応必須。
- 設計書 (`design-mf-integration-status.md`) の更新も必要 (m_mf_account_mapping 廃止、cache 追加、ヘルスチェック画面、scope 拡張、Phase B/B''/B''' 対応) ? 設計レビュー §「設計書 vs 実装の乖離」参照。

レビュアー: Opus サブエージェント
最終更新: 2026-05-04
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

2026-05-04T01:41:38.956130Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 5.8 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg --files backend/src/main/java/jp/co/oda32/domain/service/finance/m 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 exited 1 in 5790ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg --files backend/src/main/java/jp/co/oda32/domain/service/finance/m 乧
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

2026-05-04T01:41:41.701833Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 8.5 seconds
Output:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg --files frontend/components/pages/finance | rg "(mf-|Mf|mf)"
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

 exited 1 in 8534ms:
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.
rg: 
Line |
   2 |  rg --files frontend/components/pages/finance | rg "(mf-|Mf|mf)"
     |  ~~
     | The term 'rg' is not recognized as a name of a cmdlet, function, script file, or executable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.

web search: 
web search: Money Forward Cloud Accounting API OAuth refresh token expires in rotation revoke scope official
web search: 
web search: site:developers.biz.moneyforward.com/docs/api/auth token endpoint refresh_token expires_in Money Forward
codex
MF公式ドキュメントも確認しました。アクセストークン 1 時間、リフレッシュトークン 540 日、かつ「利用済み refresh_token は再利用不可・更新時に新 refresh_token も発行・古い access_token も無効化」という前提が明記されているため、この実装の業務停止リスクは refresh 失敗検知と rotation 永続化の設計に集中します。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Recurse backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\mf -File | Select-Object -ExpandProperty FullName" in C:\project\odamitsu-data-hub
2026-05-04T01:42:03.729668Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Recurse backend\\src\\main\\java -File | Where-Object { "'$_.Name -match '"'(Mf|MF|Oauth|OAuth|Controller|Repository)' } | Select-Object -ExpandProperty FullName"` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Recurse backend\\src\\main\\java -File | Where-Object { "'$_.Name -match '"'(Mf|MF|Oauth|OAuth|Controller|Repository)' } | Select-Object -ExpandProperty FullName" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Recurse frontend\\components\\pages\\finance -File | Where-Object { "'$_.Name -match '"'(mf-|Mf|mf)' } | Select-Object -ExpandProperty FullName" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem backend\\src\\main\\resources\\db\\migration -File | Where-Object { "'$_.Name -match '"'(mf|MF|oauth|Oauth)' } | Select-Object -ExpandProperty FullName" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Recurse backend\\src\\main\\java -File | Where-Object { "'$_.Name -match '"'(Mf|MF|Oauth|OAuth|Controller|Repository)' } | Select-Object -ExpandProperty FullName"` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Recurse frontend\\components\\pages\\finance -File | Where-Object { "'$_.Name -match '"'(mf-|Mf|mf)' } | Select-Object -ExpandProperty FullName"` rejected: blocked by policy
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem backend\\src\\main\\resources\\db\\migration -File | Where-Object { "'$_.Name -match '"'(mf|MF|oauth|Oauth)' } | Select-Object -ExpandProperty FullName"` rejected: blocked by policy
2026-05-04T01:42:03.730877Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem -Recurse frontend\\components\\pages\\finance -File | Where-Object { "'$_.Name -match '"'(mf-|Mf|mf)' } | Select-Object -ExpandProperty FullName"` rejected: blocked by policy
2026-05-04T01:42:03.732058Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem backend\\src\\main\\resources\\db\\migration -File | Where-Object { "'$_.Name -match '"'(mf|MF|oauth|Oauth)' } | Select-Object -ExpandProperty FullName"` rejected: blocked by policy
 succeeded in 1513ms:
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfAccount.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfAccountsResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfAccountSyncService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfBalanceReconcileService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfEnumTranslationService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfJournal.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfJournalCacheService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfJournalFetcher.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfJournalReconcileService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfJournalsResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthStateStore.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOpeningBalanceService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfPaymentAggregator.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfReAuthRequiredException.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfScopeInsufficientException.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfSupplierLedgerService.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfTax.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfTaxesResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfTokenResponse.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfTokenStatus.java
C:\project\odamitsu-data-hub\backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfTrialBalanceBsResponse.java
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\mf\\MfOauthService.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\mf\\MfApiClient.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\mf\\MfOauthStateStore.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\api\\finance\\MfIntegrationController.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\MfHealthCheckService.java" in C:\project\odamitsu-data-hub
 succeeded in 2124ms:
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

 succeeded in 2218ms:
package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.model.finance.TMfOauthState;
import jp.co.oda32.domain.repository.finance.TMfOauthStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * MF OAuth 認可フローの CSRF 防止 state + PKCE code_verifier を DB 永続化する (B-3 / B-4)。
 * <p>
 * 旧実装は ConcurrentHashMap でインメモリ保管していたが、マルチ JVM / 再起動で state が
 * 消失する問題があった。{@code t_mf_oauth_state} テーブルで永続化し、TTL 10 分で sweep。
 *
 * <p>PKCE (S256): {@link #issue(Integer)} で code_verifier を生成して DB に保存、
 * {@link #computeCodeChallenge(String)} で SHA-256(verifier) の base64url を計算し、
 * authorize URL の {@code code_challenge} に乗せる。callback 時に
 * {@link #verifyAndConsume(String, Integer)} で取り出した verifier を token endpoint に送る。
 *
 * @since 2026-04-20 (rewritten 2026-04-21)
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class MfOauthStateStore {

    private static final long TTL_SECONDS = 600L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TMfOauthStateRepository stateRepository;

    /** 新しい state + code_verifier を発行して DB 保存する。期限切れの古いエントリは都度掃除。 */
    @Transactional
    public Issued issue(Integer userNo) {
        sweep();
        String state = randomUrlSafe(32);
        String codeVerifier = randomUrlSafe(64); // RFC 7636: 43-128 chars
        Timestamp expiresAt = Timestamp.from(Instant.now().plusSeconds(TTL_SECONDS));
        Timestamp now = Timestamp.from(Instant.now());
        stateRepository.save(TMfOauthState.builder()
                .state(state)
                .userNo(userNo)
                .codeVerifier(codeVerifier)
                .expiresAt(expiresAt)
                .addDateTime(now)
                .build());
        return new Issued(state, codeVerifier, computeCodeChallenge(codeVerifier));
    }

    /**
     * state を検証して消費する（以降同じ state は使えない）。
     *
     * @param state  callback で受け取った state
     * @param userNo 呼び出し元のログインユーザ番号。発行時と一致する必要がある。
     * @return 有効かつユーザ一致なら code_verifier を返す。不正なら empty。
     */
    @Transactional
    public Optional<String> verifyAndConsume(String state, Integer userNo) {
        if (state == null) return Optional.empty();
        Optional<TMfOauthState> opt = stateRepository.findById(state);
        if (opt.isEmpty()) return Optional.empty();
        TMfOauthState e = opt.get();
        // 使い捨てなので即削除
        stateRepository.deleteById(state);
        if (e.getExpiresAt().toInstant().isBefore(Instant.now())) {
            log.info("MF OAuth state 有効期限切れ");
            return Optional.empty();
        }
        // userNo が両側 null なら一致、片方だけ null なら不一致
        if (e.getUserNo() == null) {
            if (userNo != null) return Optional.empty();
        } else if (!e.getUserNo().equals(userNo)) {
            return Optional.empty();
        }
        return Optional.of(e.getCodeVerifier());
    }

    private void sweep() {
        stateRepository.deleteExpired(Timestamp.from(Instant.now()));
    }

    private static String randomUrlSafe(int byteLen) {
        byte[] bytes = new byte[byteLen];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** PKCE S256: SHA-256(verifier) を base64url (no padding) でエンコード。 */
    public static String computeCodeChallenge(String codeVerifier) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 が利用できません", e);
        }
    }

    /** issue() 戻り値: state + verifier + challenge。 */
    public record Issued(String state, String codeVerifier, String codeChallenge) {}
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2288ms:
package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.service.finance.mf.MfJournalCacheService;
import jp.co.oda32.domain.service.finance.mf.MfOauthService;
import jp.co.oda32.domain.service.finance.mf.MfTokenStatus;
import jp.co.oda32.dto.finance.MfHealthResponse;
import jp.co.oda32.dto.finance.MfHealthResponse.AnomalyStats;
import jp.co.oda32.dto.finance.MfHealthResponse.CacheStats;
import jp.co.oda32.dto.finance.MfHealthResponse.MfOauthStatus;
import jp.co.oda32.dto.finance.MfHealthResponse.ShopCacheInfo;
import jp.co.oda32.dto.finance.MfHealthResponse.SummaryStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MF 連携ヘルスチェックサービス (軸 E)。
 * <p>
 * MF OAuth 状態 / 買掛金 summary 集計 / anomaly / journals cache を 1 レスポンスで返す。
 * <p>
 * 設計書: claudedocs/design-supplier-balances-health.md §4
 *
 * @since 2026-04-23
 */
@Service
@Log4j2
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MfHealthCheckService {

    private final MfOauthService mfOauthService;
    private final MfJournalCacheService journalCache;
    private final TAccountsPayableSummaryRepository summaryRepository;

    public MfHealthResponse check(Integer shopNo) {
        return MfHealthResponse.builder()
                .checkedAt(Instant.now())
                .shopNo(shopNo)
                .mfOauth(buildOauthStatus())
                .summary(buildSummaryStats(shopNo))
                .anomalies(buildAnomalyStats(shopNo))
                .cache(buildCacheStats())
                .build();
    }

    private MfOauthStatus buildOauthStatus() {
        MfTokenStatus s = mfOauthService.getStatus();
        Long expiresInHours = null;
        if (s.connected() && s.expiresAt() != null) {
            long secs = Duration.between(Instant.now(), s.expiresAt()).getSeconds();
            expiresInHours = Math.max(0, secs / 3600);
        }
        return MfOauthStatus.builder()
                .connected(s.connected())
                .tokenExpiresAt(s.expiresAt())
                .scope(s.scope())
                .expiresInHours(expiresInHours)
                .build();
    }

    private SummaryStats buildSummaryStats(Integer shopNo) {
        Optional<LocalDate> latest = summaryRepository.findLatestTransactionMonth(shopNo);
        if (latest.isEmpty()) {
            return SummaryStats.builder()
                    .latestMonth(null)
                    .totalCount(0L).verifiedCount(0L).unverifiedCount(0L).mfExportEnabledCount(0L)
                    .build();
        }
        LocalDate m = latest.get();
        long total = summaryRepository.countByShopNoAndTransactionMonth(shopNo, m);
        long unverified = summaryRepository.countByShopNoAndTransactionMonthAndVerificationResult(shopNo, m, 0);
        long mfExport = summaryRepository.countByShopNoAndTransactionMonthAndMfExportEnabled(shopNo, m, true);
        return SummaryStats.builder()
                .latestMonth(m)
                .totalCount(total)
                .verifiedCount(total - unverified)
                .unverifiedCount(unverified)
                .mfExportEnabledCount(mfExport)
                .build();
    }

    private AnomalyStats buildAnomalyStats(Integer shopNo) {
        long negative = summaryRepository.countNegativeClosings(shopNo);
        Optional<LocalDate> latest = summaryRepository.findLatestTransactionMonth(shopNo);
        long unverified = latest
                .map(m -> summaryRepository.countByShopNoAndTransactionMonthAndVerificationResult(shopNo, m, 0))
                .orElse(0L);
        // verifyDiff / continuityBreak / monthGap は Phase B''(light) 以降の anomaly 検出ロジックが
        // 買掛帳画面側 (PayableLedgerService) で supplier 単位に出すもの。shop 単位の集計は未実装のため
        // 当面は 0 を返す (MEMORY.md の anomaly 種別参照)。次フェーズで PayableAnomalyCounter を util 化予定。
        return AnomalyStats.builder()
                .negativeClosingCount(negative)
                .unverifiedCount(unverified)
                .verifyDiffCount(0L)
                .continuityBreakCount(0L)
                .monthGapCount(0L)
                .build();
    }

    private CacheStats buildCacheStats() {
        List<MfJournalCacheService.ShopStats> stats = journalCache.getStats();
        List<ShopCacheInfo> infos = new ArrayList<>();
        for (MfJournalCacheService.ShopStats s : stats) {
            infos.add(ShopCacheInfo.builder()
                    .shopNo(s.shopNo())
                    .monthsCount(s.monthsCount())
                    .oldestFetchedAt(s.oldestFetchedAt())
                    .newestFetchedAt(s.newestFetchedAt())
                    .build());
        }
        return CacheStats.builder().cachedShops(infos).build();
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2343ms:
package jp.co.oda32.domain.service.finance.mf;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * マネーフォワードクラウド会計 API への低レベル HTTP クライアント。
 * <p>
 * OAuth2 の token 交換・refresh のみ担当。Journal / accounts 取得は別 Service から
 * {@link #restClient()} を経由して呼ぶ。再認可が必要なエラーは
 * {@link MfReAuthRequiredException} に変換する。
 *
 * @since 2026/04/20
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class MfApiClient {

    private final RestClient.Builder restClientBuilder;

    /**
     * 認可 URL を組み立てる（ブラウザをリダイレクトするための URL）。
     * PKCE S256 を使う: {@code code_challenge} と {@code code_challenge_method=S256} を付与する (B-4)。
     */
    public String buildAuthorizeUrl(MMfOauthClient client, String state, String codeChallenge) {
        StringBuilder sb = new StringBuilder(client.getAuthorizeUrl());
        sb.append(client.getAuthorizeUrl().contains("?") ? "&" : "?");
        sb.append("response_type=code");
        sb.append("&client_id=").append(urlEncode(client.getClientId()));
        sb.append("&redirect_uri=").append(urlEncode(client.getRedirectUri()));
        sb.append("&scope=").append(urlEncode(client.getScope()));
        sb.append("&state=").append(urlEncode(state));
        if (codeChallenge != null && !codeChallenge.isEmpty()) {
            sb.append("&code_challenge=").append(urlEncode(codeChallenge));
            sb.append("&code_challenge_method=S256");
        }
        return sb.toString();
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
        return postToken(client.getTokenUrl(), form, basicAuthHeader(client.getClientId(), clientSecret));
    }

    /** refresh_token で新しい access_token を取得する（CLIENT_SECRET_BASIC）。 */
    public MfTokenResponse refreshToken(MMfOauthClient client, String clientSecret, String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return postToken(client.getTokenUrl(), form, basicAuthHeader(client.getClientId(), clientSecret));
    }

    /**
     * 認可されたリクエスト用の RestClient を返す（呼び出し側で Authorization ヘッダを付ける）。
     * MfJournalService など高レベル Service が利用する想定。
     */
    public RestClient restClient() {
        return restClientBuilder.build();
    }

    /**
     * 指定パスの MF API を生 JSON のまま取得する（診断用）。
     */
    public JsonNode getRaw(MMfOauthClient client, String accessToken, String path) {
        String url = client.getApiBaseUrl() + path;
        try {
            JsonNode node = restClientBuilder.build().get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(JsonNode.class);
            return node;
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            String body = e.getResponseBodyAsString();
            log.warn("MF {} 取得失敗: status={}, body={}", path, status.value(), body);
            if (status.value() == 401) {
                throw new MfReAuthRequiredException("MF API 認証失敗。再認証してください: " + body, e);
            }
            throw e;
        }
    }

    /**
     * GET /api/v3/journals で指定期間の仕訳一覧を取得。
     * @param startDate 開始日 (yyyy-MM-dd)
     * @param endDate   終了日 (yyyy-MM-dd)
     */
    public MfJournalsResponse listJournals(MMfOauthClient client, String accessToken,
                                            String startDate, String endDate, int page, int perPage) {
        String url = client.getApiBaseUrl() + "/api/v3/journals"
                + "?start_date=" + urlEncode(startDate)
                + "&end_date=" + urlEncode(endDate)
                + "&page=" + page
                + "&per_page=" + perPage;
        try {
            MfJournalsResponse res = restClientBuilder.build().get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(MfJournalsResponse.class);
            return res != null ? res : new MfJournalsResponse(null);
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            String body = e.getResponseBodyAsString();
            log.warn("MF /journals 取得失敗: status={}, body={}", status.value(), body);
            if (status.value() == 401) {
                throw new MfReAuthRequiredException("MF API 認証失敗。再認証してください: " + body, e);
            }
            if (status.value() == 403) {
                throw new MfScopeInsufficientException("mfc/accounting/journal.read",
                        "MF scope 不足です (mfc/accounting/journal.read 必要)。クライアント設定更新 + 再認証してください: " + body, e);
            }
            throw e;
        }
    }

    /** GET /api/v3/taxes で税区分一覧を取得。 */
    public MfTaxesResponse listTaxes(MMfOauthClient client, String accessToken) {
        String url = client.getApiBaseUrl() + "/api/v3/taxes";
        try {
            MfTaxesResponse res = restClientBuilder.build().get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(MfTaxesResponse.class);
            return res != null ? res : new MfTaxesResponse(null);
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            String body = e.getResponseBodyAsString();
            log.warn("MF /taxes 取得失敗: status={}, body={}", status.value(), body);
            if (status.value() == 401) {
                throw new MfReAuthRequiredException("MF API 認証失敗。再認証してください: " + body, e);
            }
            throw e;
        }
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
        String url = client.getApiBaseUrl() + "/api/v3/reports/trial_balance_bs"
                + "?end_date=" + urlEncode(endDate);
        try {
            MfTrialBalanceBsResponse res = restClientBuilder.build().get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(MfTrialBalanceBsResponse.class);
            return res != null ? res : new MfTrialBalanceBsResponse(null, endDate, null, null);
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            String body = e.getResponseBodyAsString();
            log.warn("MF /trial_balance_bs 取得失敗: status={}, body={}", status.value(), body);
            if (status.value() == 401) {
                throw new MfReAuthRequiredException("MF API 認証失敗。再認証してください: " + body, e);
            }
            if (status.value() == 403) {
                throw new MfScopeInsufficientException("mfc/accounting/report.read",
                        "MF scope 不足です (mfc/accounting/report.read)。クライアント設定に scope を追加 → 再認証してください: " + body, e);
            }
            throw e;
        }
    }

    /**
     * GET /api/v3/accounts で勘定科目一覧を取得。
     * @param client 有効な MMfOauthClient
     * @param accessToken Bearer token
     * @return 勘定科目リスト（subAccounts を含む）
     */
    public MfAccountsResponse listAccounts(MMfOauthClient client, String accessToken) {
        String url = client.getApiBaseUrl() + "/api/v3/accounts";
        try {
            MfAccountsResponse res = restClientBuilder.build().get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(MfAccountsResponse.class);
            return res != null ? res : new MfAccountsResponse(null);
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            String body = e.getResponseBodyAsString();
            log.warn("MF /accounts 取得失敗: status={}, body={}", status.value(), body);
            if (status.value() == 401) {
                throw new MfReAuthRequiredException("MF API 認証失敗。再認証してください: " + body, e);
            }
            throw e;
        }
    }

    private MfTokenResponse postToken(String tokenUrl, MultiValueMap<String, String> form, String basicAuth) {
        try {
            MfTokenResponse res = restClientBuilder.build().post()
                    .uri(tokenUrl)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MfTokenResponse.class);
            if (res == null || res.accessToken() == null || res.refreshToken() == null) {
                throw new IllegalStateException("MF token エンドポイントから不正なレスポンス");
            }
            return res;
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            String body = e.getResponseBodyAsString();
            log.warn("MF token エンドポイント失敗: status={}, body={}", status.value(), body);
            if (status.value() == 400 || status.value() == 401) {
                throw new MfReAuthRequiredException(
                        "MF トークン交換に失敗しました（再認証が必要）: " + body, e);
            }
            throw e;
        }
    }

    private static String basicAuthHeader(String clientId, String clientSecret) {
        String raw = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2422ms:
package jp.co.oda32.api.finance;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jp.co.oda32.domain.model.finance.MMfEnumTranslation;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.service.data.LoginUser;
import jp.co.oda32.domain.service.finance.mf.MfAccountSyncService;
import jp.co.oda32.domain.service.finance.mf.MfApiClient;
import jp.co.oda32.domain.service.finance.mf.MfEnumTranslationService;
import jp.co.oda32.domain.service.finance.mf.MfBalanceReconcileService;
import jp.co.oda32.domain.service.finance.mf.MfJournalReconcileService;
import jp.co.oda32.domain.service.finance.mf.MfOauthService;
import jp.co.oda32.domain.service.finance.mf.MfReAuthRequiredException;
import jp.co.oda32.domain.service.finance.mf.MfScopeInsufficientException;
import jp.co.oda32.domain.service.finance.mf.MfTokenStatus;
import jp.co.oda32.domain.service.smile.TSmilePaymentService;
import jp.co.oda32.domain.model.smile.TSmilePayment;
import org.springframework.core.env.Environment;
import jp.co.oda32.dto.finance.MfEnumTranslationRequest;
import jp.co.oda32.dto.finance.MfEnumTranslationResponse;
import jp.co.oda32.dto.finance.MfOauthClientRequest;
import jp.co.oda32.dto.finance.MfOauthClientResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * マネーフォワードクラウド会計 連携（Phase 1: OAuth 基盤 + 仕訳突合）。
 * <p>
 * 設計書: claudedocs/design-mf-integration-status.md
 *
 * @since 2026/04/20
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/finance/mf-integration")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class MfIntegrationController {

    private final MfOauthService mfOauthService;
    private final MfApiClient mfApiClient;
    private final Environment environment;
    private final MfEnumTranslationService enumTranslationService;
    private final MfAccountSyncService accountSyncService;
    private final MfJournalReconcileService reconcileService;
    private final MfBalanceReconcileService balanceReconcileService;
    private final jp.co.oda32.domain.repository.smile.TSmilePaymentRepository tSmilePaymentRepository;

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
            @Valid @RequestBody MfOauthClientRequest request,
            @AuthenticationPrincipal LoginUser user) {
        MMfOauthClient entity = MMfOauthClient.builder()
                .clientId(request.getClientId())
                .redirectUri(request.getRedirectUri())
                .scope(request.getScope())
                .authorizeUrl(request.getAuthorizeUrl())
                .tokenUrl(request.getTokenUrl())
                .apiBaseUrl(request.getApiBaseUrl())
                .build();
        Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
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
    public ResponseEntity<?> authorizeUrl(@AuthenticationPrincipal LoginUser user) {
        try {
            Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
            MfOauthService.AuthorizeUrl issued = mfOauthService.buildAuthorizeUrl(userNo);
            return ResponseEntity.ok(Map.of("url", issued.url(), "state", issued.state()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * MF からのリダイレクト後、フロントの callback ページが code/state を POST してくる。
     * state + code_verifier (PKCE) が DB ストアで検証できれば token 交換を行う。
     */
    @PostMapping("/oauth/callback")
    public ResponseEntity<?> callback(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal LoginUser user) {
        String code = body.get("code");
        String state = body.get("state");
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "code/state が必要です"));
        }
        Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
        try {
            mfOauthService.handleCallback(code, state, userNo);
            return ResponseEntity.ok(mfOauthService.getStatus());
        } catch (IllegalArgumentException e) {
            log.warn("MF OAuth callback: state 検証失敗 userNo={} message={}", userNo, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "state が不正または有効期限切れです。再度認可をやり直してください。"));
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /** 接続を切断（DB トークンを論理削除）。 */
    @PostMapping("/oauth/revoke")
    public ResponseEntity<MfTokenStatus> revoke(@AuthenticationPrincipal LoginUser user) {
        Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
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
            @Valid @RequestBody List<MfEnumTranslationRequest> requests,
            @AuthenticationPrincipal LoginUser user) {
        Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
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
    public ResponseEntity<?> autoSeedTranslations(@AuthenticationPrincipal LoginUser user) {
        try {
            Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
            MfEnumTranslationService.AutoSeedResult result = enumTranslationService.autoSeed(userNo);
            return ResponseEntity.ok(Map.of(
                    "added", result.added(),
                    "unresolved", result.unresolved()
            ));
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("message", e.getMessage()));
        }
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
        try {
            MfJournalReconcileService.ReconcileReport report = reconcileService.reconcile(transactionMonth);
            return ResponseEntity.ok(report);
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", e.getMessage()));
        }
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
        try {
            MfBalanceReconcileService.BalanceReconcileReport report =
                    balanceReconcileService.reconcile(period);
            return ResponseEntity.ok(report);
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

    // ---- 勘定科目同期 (mf_account_master 洗い替え) ----

    @GetMapping("/account-sync/preview")
    public ResponseEntity<?> previewAccountSync() {
        try {
            MfAccountSyncService.SyncResult result = accountSyncService.preview();
            return ResponseEntity.ok(result);
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/account-sync/apply")
    public ResponseEntity<?> applyAccountSync(@AuthenticationPrincipal LoginUser user) {
        try {
            Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
            MfAccountSyncService.SyncResult result = accountSyncService.apply(userNo);
            return ResponseEntity.ok(result);
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", e.getMessage()));
        }
    }

    // ---- 診断用（勘定科目同期実装前のレスポンス shape 調査） ----
    // dev/test プロファイルのみ動作。prod では 404 を返す (B-W4)。

    @GetMapping("/debug/accounts-raw")
    public ResponseEntity<?> debugAccountsRaw() {
        if (!isDevProfile()) return notFound();
        return fetchRawWithFirstN("/api/v3/accounts", 3);
    }

    /**
     * t_smile_payment を voucher_date 範囲で集計する診断 endpoint (Phase B'' 遡及充填の前準備)。
     * 月×仕入先コード単位で payment_amount を合計して返す。
     * dev プロファイルのみ。
     */
    @GetMapping("/debug/smile-payment-monthly")
    public ResponseEntity<?> debugSmilePaymentMonthly(
            @RequestParam("fromDate")
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate fromDate,
            @RequestParam("toDate")
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate toDate) {
        if (!isDevProfile()) return notFound();
        List<TSmilePayment> list = tSmilePaymentRepository.findByVoucherDateBetween(fromDate, toDate);
        // 月ごとに集計 (yyyy-MM) + supplier_code 単位
        java.util.Map<String, java.math.BigDecimal> byMonthTotal = new java.util.TreeMap<>();
        java.util.Map<String, Integer> byMonthCount = new java.util.TreeMap<>();
        java.util.Map<String, java.util.Map<String, java.math.BigDecimal>> byMonthSupplier = new java.util.TreeMap<>();
        for (TSmilePayment p : list) {
            if (p.getVoucherDate() == null) continue;
            String ym = p.getVoucherDate().toString().substring(0, 7);
            byMonthTotal.merge(ym,
                    p.getPaymentAmount() != null ? p.getPaymentAmount() : java.math.BigDecimal.ZERO,
                    java.math.BigDecimal::add);
            byMonthCount.merge(ym, 1, Integer::sum);
            byMonthSupplier.computeIfAbsent(ym, k -> new java.util.HashMap<>())
                    .merge(p.getSupplierCode() != null ? p.getSupplierCode() : "",
                            p.getPaymentAmount() != null ? p.getPaymentAmount() : java.math.BigDecimal.ZERO,
                            java.math.BigDecimal::add);
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("totalRows", list.size());
        result.put("byMonth", byMonthTotal.entrySet().stream()
                .map(e -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
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
    @GetMapping("/debug/trial-balance-raw")
    public ResponseEntity<?> debugTrialBalanceRaw(@RequestParam Map<String, String> params) {
        if (!isDevProfile()) return notFound();
        String query = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + java.net.URLEncoder.encode(
                        e.getValue(), java.nio.charset.StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
        String path = "/api/v3/reports/trial_balance_bs" + (query.isEmpty() ? "" : "?" + query);
        try {
            MMfOauthClient client = mfOauthService.findActiveClient()
                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
            String accessToken = mfOauthService.getValidAccessToken();
            JsonNode raw = mfApiClient.getRaw(client, accessToken, path);
            return ResponseEntity.ok(raw);
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 403) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "message", "scope 不足です。frontend/types/mf-integration.ts に"
                                + " mfc/accounting/report.read を追加済みの状態で、"
                                + "/finance/mf-integration 画面から「再認証」してください。",
                        "mfBody", e.getResponseBodyAsString()));
            }
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("message", "MF API error", "mfBody", e.getResponseBodyAsString()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/debug/taxes-raw")
    public ResponseEntity<?> debugTaxesRaw() {
        if (!isDevProfile()) return notFound();
        return fetchRawWithFirstN("/api/v3/taxes", 5);
    }

    /**
     * 指定取引月の MF 仕訳を最初 3 件だけ返す（shape 調査用）。
     * 例: /debug/journals-raw?transactionMonth=2026-03-20
     */
    @GetMapping("/debug/journals-raw")
    public ResponseEntity<?> debugJournalsRaw(
            @RequestParam("transactionMonth") String transactionMonth) {
        if (!isDevProfile()) return notFound();
        // start_date=end_date=transactionMonth で 1 日分のみ取得
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
    @GetMapping("/debug/account-journals")
    public ResponseEntity<?> debugAccountJournals(
            @RequestParam("accountName") String accountName,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        if (!isDevProfile()) return notFound();
        try {
            MMfOauthClient client = mfOauthService.findActiveClient()
                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
            String accessToken = mfOauthService.getValidAccessToken();

            java.util.List<java.util.Map<String, Object>> balancedJournals = new java.util.ArrayList<>();
            java.util.List<java.util.Map<String, Object>> unbalancedJournals = new java.util.ArrayList<>();

            int page = 1;
            final int perPage = 1000;
            while (true) {
                var res = mfApiClient.listJournals(client, accessToken, startDate, endDate, page, perPage);
                var journals = res.items();
                if (journals.isEmpty()) break;
                for (var j : journals) {
                    if (j.branches() == null) continue;
                    java.math.BigDecimal deb = java.math.BigDecimal.ZERO;
                    java.math.BigDecimal cre = java.math.BigDecimal.ZERO;
                    java.util.List<java.util.Map<String, Object>> hitBranches = new java.util.ArrayList<>();
                    for (var b : j.branches()) {
                        var d = b.debitor();
                        var c = b.creditor();
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
                            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
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
                    java.math.BigDecimal diff = cre.subtract(deb);
                    java.util.Map<String, Object> jEntry = new java.util.LinkedHashMap<>();
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

            return ResponseEntity.ok(java.util.Map.of(
                    "accountName", accountName,
                    "startDate", startDate,
                    "endDate", endDate,
                    "balancedJournalCount", balancedJournals.size(),
                    "unbalancedJournalCount", unbalancedJournals.size(),
                    "unbalancedJournals", unbalancedJournals,
                    "balancedSamples", balancedJournals.stream().limit(3).toList()
            ));
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 指定勘定科目の残高推移を計算する（/journals 全件走査）。
     * 例: /debug/account-trend?accountName=仕入資金複合&startDate=2025-10-01&endDate=2026-02-20
     */
    @GetMapping("/debug/account-trend")
    public ResponseEntity<?> debugAccountTrend(
            @RequestParam("accountName") String accountName,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        if (!isDevProfile()) return notFound();
        try {
            MMfOauthClient client = mfOauthService.findActiveClient()
                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
            String accessToken = mfOauthService.getValidAccessToken();

            java.math.BigDecimal totalDebit = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalCredit = java.math.BigDecimal.ZERO;
            java.util.TreeMap<java.time.LocalDate, java.math.BigDecimal[]> daily = new java.util.TreeMap<>();
            int hitCount = 0;

            int page = 1;
            final int perPage = 1000;
            while (true) {
                jp.co.oda32.domain.service.finance.mf.MfJournalsResponse res =
                        mfApiClient.listJournals(client, accessToken, startDate, endDate, page, perPage);
                java.util.List<jp.co.oda32.domain.service.finance.mf.MfJournal> journals = res.items();
                if (journals.isEmpty()) break;
                for (jp.co.oda32.domain.service.finance.mf.MfJournal j : journals) {
                    if (j.branches() == null) continue;
                    for (jp.co.oda32.domain.service.finance.mf.MfJournal.MfBranch b : j.branches()) {
                        var deb = b.debitor();
                        var cre = b.creditor();
                        boolean hit = false;
                        java.math.BigDecimal dv = java.math.BigDecimal.ZERO;
                        java.math.BigDecimal cv = java.math.BigDecimal.ZERO;
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
                            java.time.LocalDate d = j.transactionDate();
                            if (d != null) {
                                java.math.BigDecimal[] arr = daily.computeIfAbsent(d,
                                        k -> new java.math.BigDecimal[]{java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO});
                                arr[0] = arr[0].add(dv);
                                arr[1] = arr[1].add(cv);
                            }
                        }
                    }
                }
                if (journals.size() < perPage) break;
                page++;
            }

            // 累積残高推移（負債側として credit - debit 方式で計算）
            java.util.List<java.util.Map<String, Object>> trend = new java.util.ArrayList<>();
            java.math.BigDecimal running = java.math.BigDecimal.ZERO;
            for (var e : daily.entrySet()) {
                java.math.BigDecimal d = e.getValue()[0];
                java.math.BigDecimal c = e.getValue()[1];
                running = running.add(c).subtract(d);
                trend.add(java.util.Map.of(
                        "date", e.getKey().toString(),
                        "debit", d,
                        "credit", c,
                        "runningBalance", running
                ));
            }

            return ResponseEntity.ok(java.util.Map.of(
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
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    private boolean isDevProfile() {
        for (String p : environment.getActiveProfiles()) {
            if ("dev".equals(p) || "test".equals(p)) return true;
        }
        return false;
    }

    private static ResponseEntity<?> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Not found"));
    }

    private ResponseEntity<?> fetchRawWithFirstN(String path, int sampleSize) {
        try {
            MMfOauthClient client = mfOauthService.findActiveClient()
                    .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
            String accessToken = mfOauthService.getValidAccessToken();
            JsonNode raw = mfApiClient.getRaw(client, accessToken, path);
            return ResponseEntity.ok(trimToSample(raw, sampleSize));
        } catch (MfReAuthRequiredException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
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
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\MMfOauthClient.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\TMfOauthToken.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\domain\\repository\\finance\\MMfOauthClientRepository.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\java\\jp\\co\\oda32\\domain\\repository\\finance\\TMfOauthTokenRepository.java" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content frontend\\components\\pages\\finance\\mf-integration.tsx" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content frontend\\components\\pages\\finance\\mf-integration-callback.tsx" in C:\project\odamitsu-data-hub
 succeeded in 2110ms:
package jp.co.oda32.domain.model.finance;

import jakarta.persistence.*;
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

    /** AES-256 暗号化済み Client Secret。CryptoUtil で復号する。 */
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

 succeeded in 2190ms:
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

 succeeded in 3123ms:
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

 succeeded in 4150ms:
package jp.co.oda32.domain.model.finance;

import jakarta.persistence.*;
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

    @Column(name = "access_token_enc", nullable = false)
    private String accessTokenEnc;

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

 succeeded in 5113ms:
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
  // clientQuery.data.id をキーにして、レコード差し替え時のみ反映する。
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
  }, [clientQuery.data])

  // F-3: callback タブからの post-message を受け取り、status を即時 invalidate して画面反映する。
  useEffect(() => {
    const handler = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return
      const data = event.data as { type?: string; source?: string; message?: string } | null
      if (!data || data.source !== 'odamitsu-data-hub') return
      if (data.type === 'mf-connected') {
        toast.success('MF 連携が完了しました')
        queryClient.invalidateQueries({ queryKey: ['mf-oauth-status'] })
      } else if (data.type === 'mf-connection-failed') {
        toast.error(`MF 連携に失敗: ${data.message ?? 'unknown'}`)
      }
    }
    window.addEventListener('message', handler)
    return () => window.removeEventListener('message', handler)
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
      // 新タブで MF 認可ページを開く
      // 親タブから opener アクセスできるよう noopener は付けない（post-message 通知のため）。
      window.open(res.url, '_blank', 'noreferrer')
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
          ) : status?.configured ? (
            <div className="flex items-center gap-2">
              <Badge variant="secondary">未接続</Badge>
              <span className="text-xs text-muted-foreground">
                クライアント設定済み。下の「接続」ボタンで認可を開始できます。
              </span>
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

 succeeded in 6017ms:
'use client'

import { useEffect, useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { api, ApiError } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { CheckCircle2, Loader2, XCircle } from 'lucide-react'

type Phase = 'processing' | 'success' | 'error'

/**
 * MF からのリダイレクト後、URL の code/state を backend に POST して token 交換する。
 * 成功したら親画面 (/finance/mf-integration) に戻る。
 */
export function MfIntegrationCallbackPage() {
  const router = useRouter()
  const sp = useSearchParams()
  const [phase, setPhase] = useState<Phase>('processing')
  const [message, setMessage] = useState<string>('')

  useEffect(() => {
    const code = sp.get('code')
    const state = sp.get('state')
    const error = sp.get('error')
    const errorDesc = sp.get('error_description')

    if (error) {
      setPhase('error')
      setMessage(`MF から拒否されました: ${error}${errorDesc ? ' - ' + errorDesc : ''}`)
      return
    }
    if (!code || !state) {
      setPhase('error')
      setMessage('code/state が URL に含まれていません。')
      return
    }

    api
      .post<{ connected: boolean }>('/finance/mf-integration/oauth/callback', { code, state })
      .then(() => {
        setPhase('success')
        setMessage('MF との接続に成功しました。')
        // F-3: 親タブ (mf-integration 画面) に通知して statusQuery を即時 invalidate させる。
        try {
          window.opener?.postMessage(
            { type: 'mf-connected', source: 'odamitsu-data-hub' },
            window.location.origin,
          )
        } catch { /* opener が閉じていても無視 */ }
      })
      .catch((e: unknown) => {
        setPhase('error')
        const msg = e instanceof ApiError ? e.message : (e as Error).message
        setMessage(`接続に失敗しました: ${msg}`)
        try {
          window.opener?.postMessage(
            { type: 'mf-connection-failed', source: 'odamitsu-data-hub', message: msg },
            window.location.origin,
          )
        } catch { /* ignore */ }
      })
  }, [sp])

  return (
    <div className="mx-auto max-w-lg space-y-4 py-12">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">
            {phase === 'processing' && (
              <span className="flex items-center gap-2">
                <Loader2 className="h-5 w-5 animate-spin" />
                MF 連携を処理中...
              </span>
            )}
            {phase === 'success' && (
              <span className="flex items-center gap-2 text-emerald-700">
                <CheckCircle2 className="h-5 w-5" />
                接続成功
              </span>
            )}
            {phase === 'error' && (
              <span className="flex items-center gap-2 text-destructive">
                <XCircle className="h-5 w-5" />
                接続失敗
              </span>
            )}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm">
          {message && <p>{message}</p>}
          {phase !== 'processing' && (
            <Button size="sm" onClick={() => router.push('/finance/mf-integration')}>
              連携状況画面に戻る
            </Button>
          )}
          {phase === 'processing' && (
            <p className="text-xs text-muted-foreground">
              このタブは処理完了後、自動的に親画面に反映されます。
              完了後このタブは閉じて問題ありません。
            </p>
          )}
          {phase === 'success' && (
            <p className="text-xs text-muted-foreground">
              親画面 (MF 連携状況) に通知済み。このタブは閉じて構いません。
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-ChildItem backend\\src\\main\\resources\\db\\migration | Select-Object Name" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content frontend\\components\\pages\\finance\\mf-health.tsx" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content frontend\\components\\pages\\finance\\MfAccountSyncTab.tsx" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content frontend\\components\\pages\\finance\\MfBalanceReconcileTab.tsx" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content frontend\\types\\mf-integration.ts" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content frontend\\types\\mf-health.ts" in C:\project\odamitsu-data-hub
 succeeded in 2322ms:
/**
 * 軸 E: MF 連携ヘルスチェック 型定義。
 * 設計書: claudedocs/design-supplier-balances-health.md §4
 */

export interface MfOauthStatus {
  connected: boolean
  tokenExpiresAt: string | null
  scope: string | null
  expiresInHours: number | null
}

export interface SummaryStats {
  latestMonth: string | null
  totalCount: number
  verifiedCount: number
  unverifiedCount: number
  mfExportEnabledCount: number
}

export interface AnomalyStats {
  negativeClosingCount: number
  unverifiedCount: number
  verifyDiffCount: number
  continuityBreakCount: number
  monthGapCount: number
}

export interface ShopCacheInfo {
  shopNo: number
  monthsCount: number
  oldestFetchedAt: string | null
  newestFetchedAt: string | null
}

export interface CacheStats {
  cachedShops: ShopCacheInfo[]
}

export interface MfHealthResponse {
  checkedAt: string
  shopNo: number
  mfOauth: MfOauthStatus
  summary: SummaryStats
  anomalies: AnomalyStats
  cache: CacheStats
}

export type HealthLevel = 'green' | 'yellow' | 'red'

/**
 * 設計書 §4.3 の判定ロジック:
 * - ??: token 期限切れ OR negativeClosing > 0 OR anomaly 合計 > 10
 * - ??: token 残り 24h 以内 OR unverifiedCount > 0 OR anomaly 合計 > 0
 * - ??: 上記以外
 */
export function judgeHealth(res: MfHealthResponse): HealthLevel {
  const a = res.anomalies
  const anomalyTotal =
    a.negativeClosingCount + a.verifyDiffCount + a.continuityBreakCount + a.monthGapCount
  const tokenExpired = !res.mfOauth.connected || (res.mfOauth.expiresInHours ?? 0) <= 0
  if (tokenExpired || a.negativeClosingCount > 0 || anomalyTotal > 10) return 'red'
  const tokenNear = (res.mfOauth.expiresInHours ?? 9999) < 24
  if (tokenNear || res.summary.unverifiedCount > 0 || anomalyTotal > 0) return 'yellow'
  return 'green'
}

export const HEALTH_COLOR: Record<HealthLevel, string> = {
  green: 'text-green-600',
  yellow: 'text-amber-600',
  red: 'text-red-600',
}

export const HEALTH_LABEL: Record<HealthLevel, string> = {
  green: '健全',
  yellow: '注意',
  red: '要対応',
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2398ms:

Name
----
V001__create_nfkc_function.sql
V002__create_quote_import_tables.sql
V003__alter_quote_import_detail_add_status.sql
V004__rename_quote_import_detail_to_t.sql
V005__create_nfkc_indexes.sql
V006__create_bcart_categories.sql
V007__create_estimate_comparison_tables.sql
V008__create_mf_cashbook_tables.sql
V009__alter_accounts_payable_summary_add_verification_fields.sql
V010__alter_estimate_add_auto_generated.sql
V011__create_payment_mf_tables.sql
V012__add_order_delivery_fk.sql
V013__add_order_delivery_status_check.sql
V014__alter_accounts_payable_summary_add_verified_amount.sql
V015__create_order_delivery_indexes.sql
V016__create_payment_mf_aux_row.sql
V017__fix_accounts_payable_summary_mf_export_enabled.sql
V018__alter_accounts_receivable_summary_add_verification_fields.sql
V019__create_mf_integration_tables.sql
V020__drop_mf_account_mapping.sql
V021__mf_oauth_state_and_token_uniq.sql
V022__create_mf_enum_translation.sql
V023__alter_accounts_payable_summary_add_mf_transfer_date.sql
V024__alter_accounts_payable_summary_add_opening_balance.sql
V025__alter_accounts_payable_summary_add_payment_settled.sql
V026__alter_accounts_payable_summary_add_verified_tax_excluded.sql
V027__create_consistency_review.sql
V028__create_supplier_opening_balance.sql
V029__fix_delivery_mapping_smile_code.sql

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2452ms:
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

export const MF_DEFAULT_CONFIG: Omit<MfOauthClientRequest, 'clientId' | 'clientSecret'> = {
  redirectUri: defaultMfRedirectUri(),
  scope: 'mfc/accounting/journal.read mfc/accounting/accounts.read mfc/accounting/offices.read mfc/accounting/taxes.read mfc/accounting/report.read',
  authorizeUrl: 'https://api.biz.moneyforward.com/authorize',
  tokenUrl: 'https://api.biz.moneyforward.com/token',
  apiBaseUrl: 'https://api-accounting.moneyforward.com',
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2498ms:
'use client'

import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { api, ApiError } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { AlertCircle, Eye, Loader2, PlayCircle } from 'lucide-react'
import { toast } from 'sonner'
import type { MfAccountSyncResult } from '@/types/mf-integration'

/**
 * mf_account_master を MF API と同期するタブ。
 * 1. 「プレビュー」で差分計算（dry-run）
 * 2. 差分を確認後「適用」で DB 洗い替え
 */
export function MfAccountSyncTab() {
  const [preview, setPreview] = useState<MfAccountSyncResult | null>(null)
  const [confirmOpen, setConfirmOpen] = useState(false)

  const previewMutation = useMutation({
    mutationFn: () =>
      api.get<MfAccountSyncResult>('/finance/mf-integration/account-sync/preview'),
    onSuccess: (res) => {
      setPreview(res)
      setConfirmOpen(true)
    },
    onError: (e: Error) => {
      if (e instanceof ApiError && e.status === 401) {
        toast.error('MF 認証エラー。「接続」タブで再認証してください。')
      } else {
        toast.error(e.message)
      }
    },
  })

  const applyMutation = useMutation({
    mutationFn: () =>
      api.post<MfAccountSyncResult>('/finance/mf-integration/account-sync/apply'),
    onSuccess: (res) => {
      toast.success(
        `同期完了: 追加 ${res.insertCount} / 更新 ${res.updateCount} / 削除 ${res.deleteCount}`,
      )
      setPreview(res)
      setConfirmOpen(false)
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const hasChanges =
    preview != null &&
    (preview.insertCount > 0 || preview.updateCount > 0 || preview.deleteCount > 0)

  return (
    <div className="space-y-3">
      <Card>
        <CardContent className="pt-4 space-y-3">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium">勘定科目同期</p>
              <p className="text-xs text-muted-foreground">
                MF クラウド会計から勘定科目マスタ (<code>/accounts</code> +{' '}
                <code>/taxes</code>) を取得し、enum 翻訳辞書で日本語化して
                <code>mf_account_master</code> と突合。追加・更新・削除の差分をプレビュー後、適用できます。
              </p>
            </div>
            <Button
              onClick={() => previewMutation.mutate()}
              disabled={previewMutation.isPending}
            >
              {previewMutation.isPending ? (
                <>
                  <Loader2 className="mr-1 h-4 w-4 animate-spin" />
                  プレビュー中...
                </>
              ) : (
                <>
                  <Eye className="mr-1 h-4 w-4" />
                  プレビュー
                </>
              )}
            </Button>
          </div>

          <div className="rounded border border-amber-300 bg-amber-50 p-3 text-xs text-amber-800">
            <div className="flex items-start gap-2">
              <AlertCircle className="h-4 w-4 mt-0.5 shrink-0" />
              <div>
                <p className="font-medium">適用時の注意</p>
                <ul className="list-disc ml-4 mt-1 space-y-0.5">
                  <li>
                    MF に存在しない既存行は <b>物理削除</b> されます（「運用中の勘定科目が MF から消えた」状態のみ該当）。
                  </li>
                  <li>
                    英語 enum の日本語化に失敗した場合は英語のまま保存されます。先に「enum 翻訳辞書」タブで確認してください。
                  </li>
                  <li>
                    税区分 (<code>tax_classification</code>) は MF <code>/taxes</code> の <code>name</code> で上書きします。
                  </li>
                  <li>
                    <b>更新件数が多く出ることがあります</b>: <code>display_order</code> は MF 返却順の連番で保存しているため、
                    MF 側で勘定科目を 1 つ追加・並び替えするだけで、それ以降の全行の順序が 1 つずつずれて大量 UPDATE になります。
                    内容自体は変わっていないので問題ありません。
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>勘定科目同期 プレビュー</DialogTitle>
            <DialogDescription>
              以下の差分を <code>mf_account_master</code> に適用します。問題なければ「適用」を押してください。
            </DialogDescription>
          </DialogHeader>

          {preview && (
            <div className="space-y-3 text-sm">
              <div className="flex gap-2">
                <Badge className="bg-emerald-600 hover:bg-emerald-700">
                  追加 {preview.insertCount}
                </Badge>
                <Badge className="bg-blue-600 hover:bg-blue-700">
                  更新 {preview.updateCount}
                </Badge>
                <Badge variant="destructive">削除 {preview.deleteCount}</Badge>
                {preview.unknownEnums.length > 0 && (
                  <Badge variant="outline" className="border-amber-500 text-amber-700">
                    未翻訳 enum {preview.unknownEnums.length}
                  </Badge>
                )}
              </div>

              {preview.unknownEnums.length > 0 && (
                <div className="rounded border border-amber-300 bg-amber-50 p-3 text-xs text-amber-800">
                  <p className="font-medium mb-1">翻訳辞書に未登録の enum（英語のまま保存されます）</p>
                  <ul className="list-disc ml-4 font-mono">
                    {preview.unknownEnums.map((e) => (
                      <li key={e}>{e}</li>
                    ))}
                  </ul>
                </div>
              )}

              {preview.insertSamples.length > 0 && (
                <Section title="追加（サンプル先頭 10 件）">
                  <SampleTable
                    rows={preview.insertSamples.map((r) => ({
                      label: fmtKey(r.accountName, r.subAccountName),
                      detail: `${r.category ?? '-'} / 税: ${r.taxClassification ?? '-'}`,
                    }))}
                  />
                </Section>
              )}

              {preview.updateSamples.length > 0 && (
                <Section title="更新（サンプル先頭 10 件）">
                  <SampleTable
                    rows={preview.updateSamples.map((r) => ({
                      label: fmtKey(r.accountName, r.subAccountName),
                      detail: r.changes,
                    }))}
                  />
                </Section>
              )}

              {preview.deleteSamples.length > 0 && (
                <Section title="削除（サンプル先頭 10 件）">
                  <SampleTable
                    rows={preview.deleteSamples.map((r) => ({
                      label: fmtKey(r.accountName, r.subAccountName),
                      detail: `${r.category ?? '-'} / 税: ${r.taxClassification ?? '-'}`,
                    }))}
                  />
                </Section>
              )}

              {!hasChanges && (
                <p className="text-xs text-muted-foreground">
                  差分なし。MF と <code>mf_account_master</code> は既に同期済みです。
                </p>
              )}
            </div>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmOpen(false)}>
              キャンセル
            </Button>
            <Button
              onClick={() => applyMutation.mutate()}
              disabled={!hasChanges || applyMutation.isPending}
            >
              {applyMutation.isPending ? (
                <>
                  <Loader2 className="mr-1 h-4 w-4 animate-spin" />
                  適用中...
                </>
              ) : (
                <>
                  <PlayCircle className="mr-1 h-4 w-4" />
                  適用
                </>
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <details className="rounded border" open>
      <summary className="cursor-pointer select-none bg-muted/50 px-3 py-1.5 text-xs font-medium">
        {title}
      </summary>
      <div className="p-2">{children}</div>
    </details>
  )
}

function SampleTable({ rows }: { rows: { label: string; detail: string }[] }) {
  return (
    <table className="w-full text-xs">
      <tbody>
        {rows.map((r, i) => (
          <tr key={i} className="border-t first:border-t-0">
            <td className="px-2 py-1 w-1/3 font-mono">{r.label}</td>
            <td className="px-2 py-1 text-muted-foreground">{r.detail}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function fmtKey(accountName: string, subAccountName: string | null): string {
  return subAccountName ? `${accountName} / ${subAccountName}` : accountName
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3402ms:
'use client'

import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { api, ApiError } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { AlertCircle, CheckCircle2, Loader2, Play } from 'lucide-react'
import { toast } from 'sonner'
import type { MfBalanceReconcileReport } from '@/types/mf-integration'

/**
 * MF 残高突合タブ (Phase B 最小版)。
 * 取引月末日を選んで「突合実行」→ MF 試算表 /trial_balance_bs の買掛金 closing と
 * 自社 t_accounts_payable_summary の累積残 (opening + change) 合計を比較して差分表示。
 * 設計書: claudedocs/design-supplier-partner-ledger-balance.md §5
 */
export function MfBalanceReconcileTab() {
  const [period, setPeriod] = useState<string>(() => {
    const now = new Date()
    const prev = new Date(now.getFullYear(), now.getMonth() - 1, 20)
    return `${prev.getFullYear()}-${String(prev.getMonth() + 1).padStart(2, '0')}-${String(prev.getDate()).padStart(2, '0')}`
  })
  const [report, setReport] = useState<MfBalanceReconcileReport | null>(null)

  const runMutation = useMutation({
    mutationFn: (d: string) =>
      api.get<MfBalanceReconcileReport>(`/finance/mf-integration/balance-reconcile?period=${d}`),
    onSuccess: (res) => {
      setReport(res)
      const adjusted = res.payable.diffForMfAdjusted
      if (adjusted === 0) {
        toast.success('残高が完全一致しました（期首残調整後）。')
      } else if (Math.abs(adjusted) < 100000) {
        toast.success(`期首残調整後の差分 \${fmt(adjusted)} (許容範囲内)。`)
      } else {
        toast.warning(`期首残調整後の差分 \${fmt(adjusted)} あり。詳細を確認してください。`)
      }
    },
    onError: (e: Error) => {
      if (e instanceof ApiError && e.status === 401) {
        toast.error('MF 認証エラー。「接続」タブで再認証してください。')
      } else if (e instanceof ApiError && e.status === 403) {
        toast.error('scope 不足です。「接続」タブで scope に report.read が含まれているか確認し、再認証してください。')
      } else {
        toast.error(e.message)
      }
    },
  })

  const fmt = (n: number) => n.toLocaleString('ja-JP')

  return (
    <div className="space-y-3">
      <Card>
        <CardContent className="pt-4 space-y-3">
          <div className="flex items-end gap-3">
            <div>
              <Label htmlFor="balance-period">月末日</Label>
              <Input
                id="balance-period"
                type="date"
                value={period}
                onChange={(e) => setPeriod(e.target.value)}
                className="w-44"
              />
            </div>
            <Button
              onClick={() => runMutation.mutate(period)}
              disabled={runMutation.isPending}
            >
              {runMutation.isPending ? (
                <Loader2 className="mr-1 h-4 w-4 animate-spin" />
              ) : (
                <Play className="mr-1 h-4 w-4" />
              )}
              残高突合
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">
            自社 t_accounts_payable_summary の累積残 (opening + change) 合計と、
            MF 試算表 /trial_balance_bs の「買掛金」closing_balance を比較します。
            Phase A の opening_balance 列が前提です。
          </p>
        </CardContent>
      </Card>

      {report && (
        <Card>
          <CardContent className="pt-4 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold">買掛金 残高突合 ({report.mfEndDate} 時点)</h3>
              {report.payable.diffForMfAdjusted === 0 ? (
                <span className="flex items-center gap-1 text-emerald-600 text-sm">
                  <CheckCircle2 className="h-4 w-4" /> 期首残調整後 一致
                </span>
              ) : Math.abs(report.payable.diffForMfAdjusted) < 100000 ? (
                <span className="flex items-center gap-1 text-emerald-600 text-sm">
                  <CheckCircle2 className="h-4 w-4" /> 期首残調整後 ?一致 (±10万円以内)
                </span>
              ) : (
                <span className="flex items-center gap-1 text-amber-700 text-sm">
                  <AlertCircle className="h-4 w-4" /> 期首残調整後も差分あり
                </span>
              )}
            </div>

            {!report.payable.mfAccountFound && (
              <div role="alert" className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
                MF 試算表に「買掛金」account が見つかりませんでした。MF 側データを確認してください。
              </div>
            )}

            <table className="w-full text-sm tabular-nums">
              <thead>
                <tr className="border-b text-left">
                  <th className="py-2">項目</th>
                  <th className="py-2 text-right">金額</th>
                  <th className="py-2 text-right">件数</th>
                </tr>
              </thead>
              <tbody>
                <tr className="border-b">
                  <td className="py-2">MF 買掛金 closing</td>
                  <td className="py-2 text-right">\{fmt(report.payable.mfClosing)}</td>
                  <td className="py-2 text-right text-muted-foreground">?</td>
                </tr>
                <tr className="border-b text-muted-foreground">
                  <td className="py-2">
                    <span className="mr-1">?</span>MF 期首残 ({report.payable.openingReferenceDate})
                  </td>
                  <td className="py-2 text-right">\{fmt(report.payable.mfOpeningBalance)}</td>
                  <td className="py-2 text-right">?</td>
                </tr>
                <tr className="border-b">
                  <td className="py-2">MF 期間累積 (期首残控除後)</td>
                  <td className="py-2 text-right">\{fmt(report.payable.mfClosing - report.payable.mfOpeningBalance)}</td>
                  <td className="py-2 text-right text-muted-foreground">?</td>
                </tr>
                <tr className="border-b">
                  <td className="py-2">自社 累積残 (MF 突合対象のみ)</td>
                  <td className="py-2 text-right">\{fmt(report.payable.selfClosingForMf)}</td>
                  <td className="py-2 text-right">{report.payable.selfMfTargetRowCount}</td>
                </tr>
                <tr className="border-b bg-muted/30 font-medium">
                  <td className="py-2">
                    差分 (期首残調整後)
                    <span className="ml-2 text-xs text-muted-foreground font-normal">
                      = MF 期間累積 ? 自社累積残
                    </span>
                  </td>
                  <td className={`py-2 text-right ${
                    report.payable.diffForMfAdjusted === 0 ? 'text-emerald-600'
                    : Math.abs(report.payable.diffForMfAdjusted) < 100000 ? 'text-emerald-600'
                    : 'text-amber-700'
                  }`}>
                    \{fmt(report.payable.diffForMfAdjusted)}
                  </td>
                  <td className="py-2 text-right text-muted-foreground">?</td>
                </tr>
                <tr className="border-b text-muted-foreground">
                  <td className="py-2">差分 (生値: MF ? 自社 MF 対象)</td>
                  <td className="py-2 text-right">\{fmt(report.payable.diffForMf)}</td>
                  <td className="py-2 text-right">?</td>
                </tr>
                <tr className="border-b text-muted-foreground">
                  <td className="py-2">自社 累積残 (全 row)</td>
                  <td className="py-2 text-right">\{fmt(report.payable.selfClosingAll)}</td>
                  <td className="py-2 text-right">{report.payable.selfRowCount}</td>
                </tr>
                <tr className="text-muted-foreground">
                  <td className="py-2">差分 (MF ? 自社 全)</td>
                  <td className={`py-2 text-right ${report.payable.diffAll === 0 ? 'text-emerald-600' : ''}`}>
                    \{fmt(report.payable.diffAll)}
                  </td>
                  <td className="py-2 text-right">?</td>
                </tr>
              </tbody>
            </table>

            <p className="text-xs text-muted-foreground">
              「MF 突合対象」は <code>mf_export_enabled=true</code> または <code>verified_manually=true</code> の row のみ。
              「全 row」はその他 row も含めた自社サマリ合計 (参考値)。
              メインの突合指標は <b>差分 (MF ? 自社 MF対象)</b>。
            </p>
            <p className="text-xs text-muted-foreground">
              Phase B' 適用後の closing は <code>opening + change ? payment_settled</code> の T 勘定定義。
              自社 DB は 2025-06-20 から累積を始めるため、MF 期首残 (
              <code>{report.payable.openingReferenceDate}</code> 時点 \{fmt(report.payable.mfOpeningBalance)}
              ) は常に既知差として残る。メイン突合指標は <b>差分 (期首残調整後)</b>。
            </p>
            <p className="text-xs text-muted-foreground">
              調整後もまだ差が残る場合、主因は 2025-06?2025-12 の <code>verified_amount</code> 欠落
              (振込明細 Excel 取込機能は 2026-04-14 実装以降)。
              本質解決は過去振込明細 Excel を遡及取込するか、別途 Phase B''' で t_smile_payment 連携を追加すること。
            </p>
            <p className="text-xs text-muted-foreground">
              仕入先別ドリルダウンは本 Phase 未対応。
              MF 試算表は account 単位で sub_account 粒度を含まないため、
              内訳特定は /journals 累積 fallback で後続対応予定。
            </p>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 4342ms:
'use client'

import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { api, ApiError } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops } from '@/hooks/use-master-data'
import { PageHeader } from '@/components/features/common/PageHeader'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { CheckCircle2, AlertTriangle, XCircle, RefreshCw, Trash2, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import {
  HEALTH_COLOR,
  HEALTH_LABEL,
  judgeHealth,
  type HealthLevel,
  type MfHealthResponse,
} from '@/types/mf-health'

export function MfHealthPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const [shopNo, setShopNo] = useState<number | undefined>(isAdmin ? 1 : user?.shopNo)

  const shopsQuery = useShops(isAdmin)
  const shopOptions = (shopsQuery.data ?? []).map((s) => ({
    value: String(s.shopNo),
    label: `${s.shopNo}: ${s.shopName}`,
  }))

  const healthQuery = useQuery({
    queryKey: ['mf-health', shopNo],
    queryFn: async () => {
      if (shopNo === undefined) throw new Error('ショップ未選択')
      return api.get<MfHealthResponse>(`/finance/mf-health?shopNo=${shopNo}`)
    },
    enabled: shopNo !== undefined,
  })

  const invalidateCache = useMutation({
    mutationFn: async () => {
      if (shopNo === undefined) throw new Error('ショップ未選択')
      await api.post(`/finance/mf-health/cache/invalidate?shopNo=${shopNo}`, {})
    },
    onSuccess: () => {
      toast.success('MF journals キャッシュを破棄しました')
      healthQuery.refetch()
    },
    onError: (e: Error) => {
      toast.error(e instanceof ApiError ? e.message : String(e))
    },
  })

  const data = healthQuery.data
  const level: HealthLevel = data ? judgeHealth(data) : 'yellow'

  return (
    <div className="space-y-4">
      <PageHeader title="MF 連携ヘルスチェック" />

      <Card>
        <CardContent className="pt-4 flex flex-wrap items-center gap-3">
          {isAdmin && (
            <div className="flex items-center gap-2">
              <Label>ショップ</Label>
              <div className="w-60">
                <SearchableSelect
                  value={shopNo !== undefined ? String(shopNo) : ''}
                  onValueChange={(v) => setShopNo(v ? Number(v) : undefined)}
                  options={shopOptions}
                  placeholder="選択してください"
                  clearable
                />
              </div>
            </div>
          )}
          <Button variant="outline" size="sm"
                  onClick={() => healthQuery.refetch()}
                  disabled={healthQuery.isFetching}>
            {healthQuery.isFetching ? <Loader2 className="mr-1 h-3 w-3 animate-spin" /> : <RefreshCw className="mr-1 h-3 w-3" />}
            再読み込み
          </Button>
          {data && (
            <div className="ml-auto flex items-center gap-2">
              <HealthBadge level={level} />
              <span className="text-xs text-muted-foreground">
                checked: {new Date(data.checkedAt).toLocaleString('ja-JP')}
              </span>
            </div>
          )}
        </CardContent>
      </Card>

      {data && (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <Card>
            <CardHeader><CardTitle className="text-sm">MF OAuth 状態</CardTitle></CardHeader>
            <CardContent className="space-y-2 text-sm">
              <Row label="接続">
                {data.mfOauth.connected
                  ? <span className="text-green-600">接続中</span>
                  : <span className="text-red-600">未接続</span>}
              </Row>
              <Row label="Scope">
                <span className="font-mono text-xs">{data.mfOauth.scope ?? '-'}</span>
              </Row>
              <Row label="有効期限">
                {data.mfOauth.tokenExpiresAt
                  ? new Date(data.mfOauth.tokenExpiresAt).toLocaleString('ja-JP')
                  : '-'}
                {data.mfOauth.expiresInHours !== null && (
                  <span className={`ml-2 text-xs ${data.mfOauth.expiresInHours < 24 ? 'text-red-600' : 'text-muted-foreground'}`}>
                    (残り {data.mfOauth.expiresInHours}h)
                  </span>
                )}
              </Row>
              <Link href="/finance/mf-integration" className="text-xs text-blue-600 hover:underline">
                MF 連携状況を開く →
              </Link>
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle className="text-sm">買掛金 summary ({data.summary.latestMonth ?? 'データなし'})</CardTitle></CardHeader>
            <CardContent className="space-y-2 text-sm">
              <Row label="合計行数">{data.summary.totalCount}</Row>
              <Row label="検証済み">{data.summary.verifiedCount}</Row>
              <Row label="未検証"><span className={data.summary.unverifiedCount > 0 ? 'text-amber-600' : ''}>{data.summary.unverifiedCount}</span></Row>
              <Row label="MF 出力 ON">{data.summary.mfExportEnabledCount}</Row>
              <Link href="/finance/accounts-payable" className="text-xs text-blue-600 hover:underline">
                買掛金一覧を開く →
              </Link>
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle className="text-sm">アノマリー</CardTitle></CardHeader>
            <CardContent className="space-y-2 text-sm">
              <Row label="負 closing"><span className={data.anomalies.negativeClosingCount > 0 ? 'text-red-600 font-medium' : ''}>{data.anomalies.negativeClosingCount}</span></Row>
              <Row label="未検証 (当月)"><span className={data.anomalies.unverifiedCount > 0 ? 'text-amber-600' : ''}>{data.anomalies.unverifiedCount}</span></Row>
              <Row label="検証差 (予定)">{data.anomalies.verifyDiffCount}</Row>
              <Row label="連続性断絶 (予定)">{data.anomalies.continuityBreakCount}</Row>
              <Row label="月ギャップ (予定)">{data.anomalies.monthGapCount}</Row>
              <p className="text-xs text-muted-foreground">※ 一部は supplier 単位検出で買掛帳画面のみ (shop 集計は次フェーズ予定)</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle className="text-sm">MF journals キャッシュ</CardTitle>
                <Button variant="ghost" size="sm"
                        onClick={() => {
                          if (window.confirm(`shop=${shopNo} の MF journals キャッシュを全破棄します。次回は MF API 再取得。`)) {
                            invalidateCache.mutate()
                          }
                        }}
                        disabled={invalidateCache.isPending}>
                  <Trash2 className="mr-1 h-3 w-3" /> 破棄
                </Button>
              </div>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              {data.cache.cachedShops.length === 0 && (
                <p className="text-muted-foreground">キャッシュなし</p>
              )}
              {data.cache.cachedShops.map((s) => (
                <div key={s.shopNo} className="space-y-1 rounded border p-2">
                  <Row label="shop">{s.shopNo}</Row>
                  <Row label="保持月数">{s.monthsCount} 月</Row>
                  <Row label="最古">
                    {s.oldestFetchedAt ? new Date(s.oldestFetchedAt).toLocaleString('ja-JP') : '-'}
                  </Row>
                  <Row label="最新">
                    {s.newestFetchedAt ? new Date(s.newestFetchedAt).toLocaleString('ja-JP') : '-'}
                  </Row>
                </div>
              ))}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="text-sm">{children}</span>
    </div>
  )
}

function HealthBadge({ level }: { level: HealthLevel }) {
  const Icon = level === 'green' ? CheckCircle2 : level === 'yellow' ? AlertTriangle : XCircle
  return (
    <span className={`flex items-center gap-1 text-lg font-semibold ${HEALTH_COLOR[level]}`}>
      <Icon className="h-5 w-5" />
      {HEALTH_LABEL[level]}
    </span>
  )
}
InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

codex
実装側では、OAuth client/token が「全社 1 件」前提で repository も `findFirstByDelFlgOrderByIdDesc` に寄っています。MF の事業者情報 API は別スコープで tenant を返せるため、現状は「どの MF 事業者に接続したか」をサーバーが永続的に検証できない形です。
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\mf\\MfOauthService.java -Pattern \"ALLOWED_REDIRECT_URIS|findActiveClient|buildAuthorizeUrl|handleCallback|getValidAccessToken|persistRefreshedToken|getStatus|revoke|persistToken|TokenSnapshot\"" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\service\\finance\\mf\\MfApiClient.java -Pattern \"buildAuthorizeUrl|exchangeCodeForToken|refreshToken|restClient\\(|listJournals|listTaxes|getTrialBalanceBs|listAccounts|postToken|basicAuthHeader\"" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\MMfOauthClient.java -Pattern \"class MMfOauthClient|clientId|clientSecretEnc|authorizeUrl|tokenUrl|apiBaseUrl|addUserNo|modifyUserNo\"" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\domain\\model\\finance\\TMfOauthToken.java -Pattern \"class TMfOauthToken|clientId|accessTokenEnc|refreshTokenEnc|expiresAt|scope|addUserNo|modifyUserNo\"" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path frontend\\types\\mf-integration.ts -Pattern \"scope:|authorizeUrl|tokenUrl|apiBaseUrl|mfc/admin/tenant.read\"" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path frontend\\types\\mf-health.ts -Pattern \"tokenExpired|expiresInHours|anomalyTotal|judgeHealth\"" in C:\project\odamitsu-data-hub
 succeeded in 2306ms:

backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:47:    private static final Set<String>
 ALLOWED_REDIRECT_URIS = Set.of(
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:73:    public Optional<MMfOauthClient> 
findActiveClient() {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:112:    public AuthorizeUrl buildAuthor
izeUrl(Integer userNo) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:113:        MMfOauthClient client = fin
dActiveClient()
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:116:        String url = mfApiClient.bu
ildAuthorizeUrl(client, issued.state(), issued.codeChallenge());
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:122:    public void handleCallback(Stri
ng code, String state, Integer userNo) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:127:        MMfOauthClient client = fin
dActiveClient()
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:131:        persistToken(client, token,
 userNo);
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:141:    public String getValidAccessTok
en() {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:143:        TokenSnapshot snap = self.l
oadActiveTokenSnapshot();
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:153:        self.persistRefreshedToken(
snap.client.getId(), refreshed);
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:159:    public TokenSnapshot loadActive
TokenSnapshot() {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:160:        MMfOauthClient client = fin
dActiveClient()
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:165:        return new TokenSnapshot(
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:178:    public void persistRefreshedTok
en(Integer clientId, MfTokenResponse refreshed) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:184:        persistToken(client, refres
hed, null);
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:187:    public MfTokenStatus getStatus(
) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:188:        Optional<MMfOauthClient> cl
ient = findActiveClient();
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:205:    /** 接続切断。DB トークンを論理
削除する（MF 側 revoke は現時点でベストエフォート省略）。 */
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:207:    public void revoke(Integer user
No) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:208:        MMfOauthClient client = fin
dActiveClient().orElse(null);
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:213:    private void persistToken(MMfOa
uthClient client, MfTokenResponse res, Integer userNo) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:237:        if (!ALLOWED_REDIRECT_URIS.
contains(uri)) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:239:                    "redirect_uri 
が許可リストにありません。許可済み: " + ALLOWED_REDIRECT_URIS);
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:251:    /** {@link #buildAuthorizeUrl(I
nteger)} 戻り値。 */
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:254:    /** {@link #loadActiveTokenSnap
shot()} 戻り値。refresh 判定や HTTP 呼び出しで使う。 */
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfOauthService.java:255:    public record TokenSnapshot(

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2391ms:

backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:24: * {@link #restClient()} を経由して呼ぶ
。再認可が必要なエラーは
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:40:    public String buildAuthorizeUrl(MMf
OauthClient client, String state, String codeChallenge) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:58:    public MfTokenResponse exchangeCode
ForToken(
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:67:        return postToken(client.getToke
nUrl(), form, basicAuthHeader(client.getClientId(), clientSecret));
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:71:    public MfTokenResponse refreshToken
(MMfOauthClient client, String clientSecret, String refreshToken) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:74:        form.add("refresh_token", refre
shToken);
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:75:        return postToken(client.getToke
nUrl(), form, basicAuthHeader(client.getClientId(), clientSecret));
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:82:    public RestClient restClient() {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:115:    public MfJournalsResponse listJour
nals(MMfOauthClient client, String accessToken,
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:146:    public MfTaxesResponse listTaxes(M
MfOauthClient client, String accessToken) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:181:    public MfTrialBalanceBsResponse ge
tTrialBalanceBs(MMfOauthClient client, String accessToken,
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:214:    public MfAccountsResponse listAcco
unts(MMfOauthClient client, String accessToken) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:235:    private MfTokenResponse postToken(
String tokenUrl, MultiValueMap<String, String> form, String basicAuth) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:244:            if (res == null || res.acc
essToken() == null || res.refreshToken() == null) {
backend\src\main\java\jp\co\oda32\domain\service\finance\mf\MfApiClient.java:260:    private static String basicAuthHea
der(String clientId, String clientSecret) {

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 3389ms:

backend\src\main\java\jp\co\oda32\domain\model\finance\MMfOauthClient.java:21:public class MMfOauthClient {
backend\src\main\java\jp\co\oda32\domain\model\finance\MMfOauthClient.java:29:    private String clientId;
backend\src\main\java\jp\co\oda32\domain\model\finance\MMfOauthClient.java:33:    private String clientSecretEnc;
backend\src\main\java\jp\co\oda32\domain\model\finance\MMfOauthClient.java:42:    private String authorizeUrl;
backend\src\main\java\jp\co\oda32\domain\model\finance\MMfOauthClient.java:45:    private String tokenUrl;
backend\src\main\java\jp\co\oda32\domain\model\finance\MMfOauthClient.java:48:    private String apiBaseUrl;
backend\src\main\java\jp\co\oda32\domain\model\finance\MMfOauthClient.java:58:    private Integer addUserNo;
backend\src\main\java\jp\co\oda32\domain\model\finance\MMfOauthClient.java:64:    private Integer modifyUserNo;

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 4362ms:

backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java:23:public class TMfOauthToken {
backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java:31:    private Integer clientId;
backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java:34:    private String accessTokenEnc;
backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java:37:    private String refreshTokenEnc;
backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java:44:    private Timestamp expiresAt;
backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java:46:    @Column(name = "scope")
backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java:47:    private String scope;
backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java:57:    private Integer addUserNo;
backend\src\main\java\jp\co\oda32\domain\model\finance\TMfOauthToken.java:63:    private Integer modifyUserNo;

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 5334ms:

frontend\types\mf-health.ts:10:  expiresInHours: number | null
frontend\types\mf-health.ts:57:export function judgeHealth(res: MfHealthResponse): HealthLevel {
frontend\types\mf-health.ts:59:  const anomalyTotal =
frontend\types\mf-health.ts:61:  const tokenExpired = !res.mfOauth.connected || (res.mfOauth.expiresInHours ?? 0) <= 0
frontend\types\mf-health.ts:62:  if (tokenExpired || a.negativeClosingCount > 0 || anomalyTotal > 10) return 'red'
frontend\types\mf-health.ts:63:  const tokenNear = (res.mfOauth.expiresInHours ?? 9999) < 24
frontend\types\mf-health.ts:64:  if (tokenNear || res.summary.unverifiedCount > 0 || anomalyTotal > 0) return 'yellow'

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 6312ms:

frontend\types\mf-integration.ts:6:  scope: string | null
frontend\types\mf-integration.ts:7:  authorizeUrl: string | null
frontend\types\mf-integration.ts:8:  tokenUrl: string | null
frontend\types\mf-integration.ts:9:  apiBaseUrl: string | null
frontend\types\mf-integration.ts:17:  scope: string
frontend\types\mf-integration.ts:18:  authorizeUrl: string
frontend\types\mf-integration.ts:19:  tokenUrl: string
frontend\types\mf-integration.ts:20:  apiBaseUrl: string
frontend\types\mf-integration.ts:27:  scope: string | null
frontend\types\mf-integration.ts:170:// Phase 1 (仕訳突合) で必要な scope:
frontend\types\mf-integration.ts:196:  scope: 'mfc/accounting/journal.read mfc/accounting/accounts.read mfc/accounting/
offices.read mfc/accounting/taxes.read mfc/accounting/report.read',
frontend\types\mf-integration.ts:197:  authorizeUrl: 'https://api.biz.moneyforward.com/authorize',
frontend\types\mf-integration.ts:198:  tokenUrl: 'https://api.biz.moneyforward.com/token',
frontend\types\mf-integration.ts:199:  apiBaseUrl: 'https://api-accounting.moneyforward.com',

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\java\\jp\\co\\oda32\\**\\*.java -Pattern \"tenant|office|事業者|shopNo|mfc/admin/tenant\"" in C:\project\odamitsu-data-hub
2026-05-04T01:43:11.037903Z ERROR codex_core::tools::router: error=`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\resources\\db\\migration\\V019__create_mf_integration_tables.sql,backend\\src\\main\\resources\\db\\migration\\V021__mf_oauth_state_and_token_uniq.sql -Pattern \"CREATE TABLE|m_mf_oauth_client|t_mf_oauth_token|UNIQUE|INDEX|client_id|shop|tenant\""` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\resources\\db\\migration\\V019__create_mf_integration_tables.sql,backend\\src\\main\\resources\\db\\migration\\V021__mf_oauth_state_and_token_uniq.sql -Pattern \"CREATE TABLE|m_mf_oauth_client|t_mf_oauth_token|UNIQUE|INDEX|client_id|shop|tenant\"" in C:\project\odamitsu-data-hub
 declined in 0ms:
`"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Select-String -Path backend\\src\\main\\resources\\db\\migration\\V019__create_mf_integration_tables.sql,backend\\src\\main\\resources\\db\\migration\\V021__mf_oauth_state_and_token_uniq.sql -Pattern \"CREATE TABLE|m_mf_oauth_client|t_mf_oauth_token|UNIQUE|INDEX|client_id|shop|tenant\""` rejected: blocked by policy
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\resources\\db\\migration\\V019__create_mf_integration_tables.sql" in C:\project\odamitsu-data-hub
exec
"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content backend\\src\\main\\resources\\db\\migration\\V021__mf_oauth_state_and_token_uniq.sql" in C:\project\odamitsu-data-hub
 succeeded in 1892ms:
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

 succeeded in 2052ms:

backend\src\main\java\jp\co\oda32\aop\ShopCheckAop.java:29:        if (entity.getShopNo() == null) {
backend\src\main\java\jp\co\oda32\aop\ShopCheckAop.java:32:        Integer loginUserShopNo = LoginUserUtil.getLoginUser
Info().getUser().getShopNo();
backend\src\main\java\jp\co\oda32\aop\ShopCheckAop.java:33:        if (loginUserShopNo == null) {
backend\src\main\java\jp\co\oda32\aop\ShopCheckAop.java:36:        if (loginUserShopNo == 0 || entity.getShopNo().equal
s(loginUserShopNo)) {
backend\src\main\java\jp\co\oda32\aop\ShopCheckAop.java:57:        if (entityList.stream().anyMatch(entity -> entity.ge
tShopNo() == null)) {
backend\src\main\java\jp\co\oda32\aop\ShopCheckAop.java:60:        Integer loginUserShopNo;
backend\src\main\java\jp\co\oda32\aop\ShopCheckAop.java:62:            loginUserShopNo = LoginUserUtil.getLoginUserInfo
().getUser().getShopNo();
backend\src\main\java\jp\co\oda32\aop\ShopCheckAop.java:67:        if (loginUserShopNo == null || loginUserShopNo == -1
) {
backend\src\main\java\jp\co\oda32\aop\ShopCheckAop.java:70:        if (loginUserShopNo == 0) {
backend\src\main\java\jp\co\oda32\aop\ShopCheckAop.java:74:                .filter(entity -> entity.getShopNo() != null
)
backend\src\main\java\jp\co\oda32\aop\ShopCheckAop.java:75:                .filter(entity -> entity.getShopNo().equals(
loginUserShopNo))
backend\src\main\java\jp\co\oda32\batch\ISmileFile.java:10:    int getShopNo();
backend\src\main\java\jp\co\oda32\batch\ISmileFile.java:12:    void setShopNo(int shopNo);
backend\src\main\java\jp\co\oda32\constant\OfficeCode.java:9:public enum OfficeCode {
backend\src\main\java\jp\co\oda32\constant\OfficeCode.java:19:    OfficeCode(String value) {
backend\src\main\java\jp\co\oda32\constant\OfficeCode.java:23:    public static OfficeCode purse(String key) {
backend\src\main\java\jp\co\oda32\constant\OfficeCode.java:24:        for (OfficeCode companyType : values()) {
backend\src\main\java\jp\co\oda32\constant\OfficeShopNo.java:3:public enum OfficeShopNo {
backend\src\main\java\jp\co\oda32\constant\OfficeShopNo.java:14:    OfficeShopNo(int value) {
backend\src\main\java\jp\co\oda32\constant\OfficeShopNo.java:18:    public static OfficeShopNo purse(int key) {
backend\src\main\java\jp\co\oda32\constant\OfficeShopNo.java:19:        for (OfficeShopNo shopNo : values()) {
backend\src\main\java\jp\co\oda32\constant\OfficeShopNo.java:20:            if (shopNo.getValue() == key) {
backend\src\main\java\jp\co\oda32\constant\OfficeShopNo.java:21:                return shopNo;

InvalidOperation: Cannot set property. Property setting is supported only on core types in this language mode.

 succeeded in 2799ms:
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

codex
# Cluster F 批判的レビュー

参照: MF公式ドキュメントでは、OAuth token endpoint は `access_token` と `refresh_token` を返し、access token は 1時間、refresh token は 540日、一度利用した refresh token は再利用不可、refresh 成功時に古い access token も無効化される、と説明されています。revoke endpoint は access/refresh token を無効化し、無効・取消済 token でも成功扱いです。  
Sources: [MF token endpoint](https://developers.biz.moneyforward.com/docs/api/auth/create-token/), [MF manual step 2](https://developers.biz.moneyforward.com/docs/tutorials/getting-started-api-call-manually/step-2/), [MF revoke](https://developers.biz.moneyforward.com/docs/api/auth/revoke-token/), [MF tenant API](https://developers.biz.moneyforward.com/docs/api/auth/get-tenant/)

## 指摘

### 1. 接続先 MF 事業者の同一性を永続検証していない
- Severity: Critical
- カテゴリ: 設計選択
- 内容: 現状は `m_mf_oauth_client` / `t_mf_oauth_token` が通常 1 レコード前提で、MF 側の tenant id / tenant number / tenant name を保存していません。[MMfOauthClient.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/MMfOauthClient.java:21) と [TMfOauthToken.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/TMfOauthToken.java:23) にも会社識別子がありません。MF 公式には `mfc/admin/tenant.read` で事業者情報取得 API があるため、認可直後に「どの MF 事業者を連携したか」を固定できるはずです。誤って別会社・別契約の MF に再認可すると、仕訳突合や残高突合が静かに別会社データで動くリスクがあります。
- 推奨アクション: `mfc/admin/tenant.read` を必須 scope に追加し、callback 直後に `/v2/tenant` を取得して `mf_tenant_id/name` を保存してください。以後の refresh・API 呼び出し前後で保存済み tenant と一致検証する設計にしてください。

### 2. refresh token 540日寿命を前提にした失効予兆管理がない
- Severity: Critical
- カテゴリ: 運用整合
- 内容: MF 公式は refresh token 540日、access token 1時間と明記していますが、現状の `getStatus()` は access token の `expiresAt` だけを返し、refresh token の期限・発行日・最終成功 refresh を業務監視に使っていません。[MfOauthService.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java:187) は `reAuthRequired=false` 固定のため、540日満了や長期未使用に近づいても経理担当が事前に知れません。refresh token が切れると、画面操作時に初めて再認可要求となり、月次締めや支払照合のタイミングで業務停止します。
- 推奨アクション: `refresh_token_issued_at`, `last_refresh_succeeded_at`, `refresh_failure_count`, `reauth_deadline_at` を永続化してください。期限 60/30/14/7 日前の段階的アラートと、管理者画面の赤表示を追加してください。

### 3. refresh 成功後の永続化失敗が「再利用不可 token 喪失」になり得る
- Severity: Critical
- カテゴリ: エッジケース
- 内容: MF 公式では一度利用した refresh token は再利用不可で、refresh 成功時に新 refresh token が発行されます。現実装は HTTP refresh 後に `persistRefreshedToken()` で DB 保存しますが、HTTP 成功後から DB commit までに障害が起きると、DB には再利用不可の旧 refresh token だけが残ります。[MfOauthService.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java:141) の構造上、この失敗は次回 refresh 時に初めて顕在化し、即再認可が必要になります。これは単なる実装バグではなく、OAuth provider が rotation を強制する場合の業務継続性リスクです。
- 推奨アクション: refresh 成功レスポンスを暗号化して `token_refresh_attempt` 的なステージング表に先行保存し、commit 後に active 化する二相永続化を検討してください。少なくとも refresh 成功後の DB 保存失敗は Critical アラートにして、直ちに再認可手順へ誘導してください。

### 4. マルチテナント設計が「shop」でも「MF契約」でも閉じていない
- Severity: Major
- カテゴリ: 設計選択
- 内容: 設計書は「1 社 1 トークン運用」としていますが、実装は `findFirstByDelFlgOrderByIdDesc()` で最後の有効 client を全機能が共有します。[MMfOauthClientRepository.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/repository/finance/MMfOauthClientRepository.java:10) この構造では、将来「店舗別法人」「グループ会社」「本番/検証 MF 事業者」を追加すると、全 API がどの tenant の token を使うか曖昧になります。フロントのヘルスチェックは `shopNo` を選べますが、OAuth token 側には `shopNo` や tenant mapping がありません。
- 推奨アクション: 今すぐ複数契約対応しない場合でも、DB に `tenant_key` または `company_scope` を追加し、全 MF API 呼び出しは明示的な接続 ID を受け取る形にしてください。最低限、全社単一前提を DB 制約と運用手順に明文化してください。

### 5. client_secret を業務 DB に置く設計が権限境界を曖昧にしている
- Severity: Major
- カテゴリ: 代替案
- 内容: `client_secret_enc` は暗号化されていますが、アプリが DB と暗号鍵の両方にアクセスできるため、アプリ侵害時には復号可能です。[MMfOauthClient.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/MMfOauthClient.java:33) client_secret は「アプリケーションの身元」であり、業務データと同じバックアップ・同じ DBA 権限で扱うべきではありません。DB 暗号化は漏洩時の遅延策であって、鍵管理ポリシーではありません。
- 推奨アクション: client_id/secret は Vault / KMS / Spring Cloud Config + secret backend に寄せ、DB には secret version と接続設定だけを置く案を優先してください。DB 保存を続ける場合も、鍵分離、復号監査、secret rotation runbook を必須にしてください。

### 6. client_secret ローテーションの移行期間を扱えない
- Severity: Major
- カテゴリ: エッジケース
- 内容: `upsertClient()` は既存 client を in-place 更新し、旧 secret を保持しません。[MfOauthService.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java:79) MF アプリポータル側で secret を切り替える際、旧 secret で発行済み refresh token が新 secret で refresh 可能か、または旧 secret が一定期間必要かを設計が吸収できません。切替タイミングを誤ると、全社共通 token が同時に使えなくなります。
- 推奨アクション: `client_secret_version` と旧 secret の短期併存を設計してください。rotation は「新 secret 登録 → refresh 試験 → tenant 一致確認 → 旧 secret 廃棄」の手順にしてください。

### 7. scope 変更を破壊的変更として管理していない
- Severity: Major
- カテゴリ: エッジケース
- 内容: scope は画面入力文字列で、現在の default に `mfc/admin/tenant.read` がありません。[mf-integration.ts](/C:/project/odamitsu-data-hub/frontend/types/mf-integration.ts:196) MF 側が scope 名を変更・追加必須化・廃止した場合、既存 token は権限不足になり、機能ごとに 403 が出るまで検知できません。さらに「scope 更新保存」と「再認可」が別操作なので、設定だけ変わって token は旧 scope のままという不整合が起きます。
- 推奨アクション: 必須 scope をコード上の typed list として定義し、status に `missingScopes` / `extraScopes` を返してください。scope 設定変更後は connected を yellow/red に落とし、再認可完了まで連携機能を抑止してください。

### 8. 同期 API 呼び出しがジョブ化されておらず、失敗の再開点がない
- Severity: Major
- カテゴリ: 代替案
- 内容: 仕訳突合、勘定科目同期、残高突合は画面操作から同期的に MF API を呼びます。レート制限やメンテナンスで途中失敗した場合、どの期間・ページ・tenant・scope で失敗したかを永続的に追えません。特に `MfAccountSyncTab` の「適用」は差分 preview と apply の間に MF 側データが変わる可能性もあります。[MfAccountSyncTab.tsx](/C:/project/odamitsu-data-hub/frontend/components/pages/finance/MfAccountSyncTab.tsx:29)
- 推奨アクション: MF API 呼び出しは `mf_sync_job` と queue worker に寄せ、job id、対象期間、tenant、attempt、rate-limit wait、結果 checksum を保存してください。UI は `202 Accepted + job polling` にし、リトライと失敗通知を集中管理してください。

### 9. callback がフロントタブ依存で、BFFとして完結していない
- Severity: Major
- カテゴリ: 代替案
- 内容: token 自体はフロントに返していませんが、callback は Next.js ページが URL の `code/state` を読み、admin JWT 付きで backend に POST する設計です。[mf-integration-callback.tsx](/C:/project/odamitsu-data-hub/frontend/components/pages/finance/mf-integration-callback.tsx:22) つまり OAuth 完了はブラウザ localStorage/JWT/タブ通信に依存し、サーバー側だけで完結しません。企業向け SaaS 連携では、callback endpoint を BFF/backend が直接受けて state cookie/session と照合し、完了画面だけを返す方が監査・再試行・CSRF 境界が明確です。
- 推奨アクション: redirect URI を backend/BFF callback に変更し、server-side で code exchange と token 保存を完了してください。フロントには connection result id だけを返し、token や authorization code を UI 層の責務から外してください。

### 10. MF API 仕様変更の検知が運用に組み込まれていない
- Severity: Major
- カテゴリ: エッジケース
- 内容: MF API の typed DTO は、列追加には比較的強い一方、列削除・型変更・意味変更には静かに `null` や空配列として流れ、突合結果の誤判定になり得ます。公式ドキュメントにも API 更新・変更はアプリポータルのお知らせや変更ポリシー確認が必要とされています。現状は dev debug endpoint で手動確認する色が強く、CI 上の contract fixture や schema drift 検知が見えません。
- 推奨アクション: journals/accounts/taxes/trial_balance の代表 JSON fixture を固定し、DTO deserialize と業務分類の contract test を CI に入れてください。週次で sandbox/本番 read-only probe を走らせ、未知 shape や必須 field 欠落を Slack/メール通知してください。

### 11. 「MF認証が切れた」を誰が知るかが未設計
- Severity: Major
- カテゴリ: 運用整合
- 内容: 画面上の status/health はありますが、担当者への push 型通知がありません。[mf-health.ts](/C:/project/odamitsu-data-hub/frontend/types/mf-health.ts:57) 認証切れ、refresh 失敗、scope 不足、tenant mismatch、API 503 継続は経理業務の停止条件ですが、利用者が画面を開くまで発見できません。月次締め前日に初検知する運用は、SaaS 連携基盤として脆いです。
- 推奨アクション: 毎朝の scheduled health probe と、Critical 条件の即時通知を実装してください。通知先は「システム管理者」だけでなく「経理運用責任者」も設定できるようにしてください。

### 12. 監査証跡が変更事実を説明できない
- Severity: Major
- カテゴリ: 運用整合
- 内容: `add_user_no` / `modify_user_no` はありますが、何を変更したか、なぜ切断したか、どの tenant からどの tenant に変わったか、scope がどう変わったかを監査できません。[MfIntegrationController.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java:68) OAuth client 設定、再認可、revoke、勘定科目同期 apply は監査イベントとして扱うべき操作です。現状の行更新履歴だけでは内部不正や誤操作の事後調査に弱いです。
- 推奨アクション: `mf_integration_audit_log` を追加し、actor、operation、before/after hash、tenant、scope、IP/user-agent、reason を保存してください。secret/token は保存せず、fingerprint のみ残してください。

### 13. MF由来の個人情報・取引先情報の分類と保持期限がない
- Severity: Major
- カテゴリ: 運用整合
- 内容: MF journals には取引先名、補助科目、摘要、入力者など個人情報・取引先機密になり得る情報が含まれます。キャッシュ、debug raw endpoint、画面表示、ログ方針を個人情報保護法/GDPR 相当のデータ分類に載せていません。特に debug raw は shape 調査目的でも、実データを広く返すと管理者画面がデータ持ち出し口になります。
- 推奨アクション: MF データを「会計機密 + 個人情報含有可能」と分類し、キャッシュ TTL、debug endpoint の本番無効化、export 禁止、アクセス監査を定義してください。DPA/委託先管理・削除要求時の対象範囲も文書化してください。

### 14. メンテナンス・API廃止時の degraded mode がない
- Severity: Minor
- カテゴリ: エッジケース
- 内容: MF 側が 503 やメンテナンスを返す場合、公式 tenant API も 503 を返し得るとされています。現状は画面操作の失敗として扱うだけで、「MF一時停止中」と「認証切れ」と「自社データ不整合」を切り分ける運用状態がありません。キャッシュが残っている場合も、それが新鮮な根拠か過去データかを業務判断に使いにくいです。
- 推奨アクション: `provider_status` を health に追加し、直近成功時刻・連続失敗回数・Retry-After を表示してください。MF停止中は再認可を促さず、再試行予定時刻を出してください。

### 15. OAuth client 情報を DB 編集可能にする範囲が広すぎる
- Severity: Minor
- カテゴリ: 代替案
- 内容: `authorizeUrl`, `tokenUrl`, `apiBaseUrl`, `scope` を admin 画面から編集できます。[mf-integration.tsx](/C:/project/odamitsu-data-hub/frontend/components/pages/finance/mf-integration.tsx:282) 既出の URL 改ざんリスクとは別に、運用統制上「環境ごとの固定接続先」と「業務担当者が変更してよい client_id/secret」を分けるべきです。本番で endpoint を手入力する構造は、変更管理・レビュー・監査の外で連携先を変えられることを意味します。
- 推奨アクション: endpoint と必須 scope は Spring Cloud Config / 環境変数 / Vault config に寄せ、画面編集不可にしてください。画面から変更できる値は client credential 登録と再認可操作に限定してください。

## Opus が見落としていた最重要 3 件

1. **MF tenant binding 不在**: どの MF 事業者に接続したかを保存・検証しておらず、別会社 MF への誤接続が静かに業務データ不整合になります。  
2. **refresh token 540日・再利用不可を前提にした業務継続設計不足**: refresh 成功後の DB 保存失敗や期限予兆未検知が、月次経理停止に直結します。  
3. **運用監査と個人情報統制の不足**: OAuth 設定変更、再認可、revoke、MF 生データ閲覧が、監査・通知・保持期限の設計に載っていません。
tokens used
126,488
# Cluster F 批判的レビュー

参照: MF公式ドキュメントでは、OAuth token endpoint は `access_token` と `refresh_token` を返し、access token は 1時間、refresh token は 540日、一度利用した refresh token は再利用不可、refresh 成功時に古い access token も無効化される、と説明されています。revoke endpoint は access/refresh token を無効化し、無効・取消済 token でも成功扱いです。  
Sources: [MF token endpoint](https://developers.biz.moneyforward.com/docs/api/auth/create-token/), [MF manual step 2](https://developers.biz.moneyforward.com/docs/tutorials/getting-started-api-call-manually/step-2/), [MF revoke](https://developers.biz.moneyforward.com/docs/api/auth/revoke-token/), [MF tenant API](https://developers.biz.moneyforward.com/docs/api/auth/get-tenant/)

## 指摘

### 1. 接続先 MF 事業者の同一性を永続検証していない
- Severity: Critical
- カテゴリ: 設計選択
- 内容: 現状は `m_mf_oauth_client` / `t_mf_oauth_token` が通常 1 レコード前提で、MF 側の tenant id / tenant number / tenant name を保存していません。[MMfOauthClient.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/MMfOauthClient.java:21) と [TMfOauthToken.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/TMfOauthToken.java:23) にも会社識別子がありません。MF 公式には `mfc/admin/tenant.read` で事業者情報取得 API があるため、認可直後に「どの MF 事業者を連携したか」を固定できるはずです。誤って別会社・別契約の MF に再認可すると、仕訳突合や残高突合が静かに別会社データで動くリスクがあります。
- 推奨アクション: `mfc/admin/tenant.read` を必須 scope に追加し、callback 直後に `/v2/tenant` を取得して `mf_tenant_id/name` を保存してください。以後の refresh・API 呼び出し前後で保存済み tenant と一致検証する設計にしてください。

### 2. refresh token 540日寿命を前提にした失効予兆管理がない
- Severity: Critical
- カテゴリ: 運用整合
- 内容: MF 公式は refresh token 540日、access token 1時間と明記していますが、現状の `getStatus()` は access token の `expiresAt` だけを返し、refresh token の期限・発行日・最終成功 refresh を業務監視に使っていません。[MfOauthService.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java:187) は `reAuthRequired=false` 固定のため、540日満了や長期未使用に近づいても経理担当が事前に知れません。refresh token が切れると、画面操作時に初めて再認可要求となり、月次締めや支払照合のタイミングで業務停止します。
- 推奨アクション: `refresh_token_issued_at`, `last_refresh_succeeded_at`, `refresh_failure_count`, `reauth_deadline_at` を永続化してください。期限 60/30/14/7 日前の段階的アラートと、管理者画面の赤表示を追加してください。

### 3. refresh 成功後の永続化失敗が「再利用不可 token 喪失」になり得る
- Severity: Critical
- カテゴリ: エッジケース
- 内容: MF 公式では一度利用した refresh token は再利用不可で、refresh 成功時に新 refresh token が発行されます。現実装は HTTP refresh 後に `persistRefreshedToken()` で DB 保存しますが、HTTP 成功後から DB commit までに障害が起きると、DB には再利用不可の旧 refresh token だけが残ります。[MfOauthService.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java:141) の構造上、この失敗は次回 refresh 時に初めて顕在化し、即再認可が必要になります。これは単なる実装バグではなく、OAuth provider が rotation を強制する場合の業務継続性リスクです。
- 推奨アクション: refresh 成功レスポンスを暗号化して `token_refresh_attempt` 的なステージング表に先行保存し、commit 後に active 化する二相永続化を検討してください。少なくとも refresh 成功後の DB 保存失敗は Critical アラートにして、直ちに再認可手順へ誘導してください。

### 4. マルチテナント設計が「shop」でも「MF契約」でも閉じていない
- Severity: Major
- カテゴリ: 設計選択
- 内容: 設計書は「1 社 1 トークン運用」としていますが、実装は `findFirstByDelFlgOrderByIdDesc()` で最後の有効 client を全機能が共有します。[MMfOauthClientRepository.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/repository/finance/MMfOauthClientRepository.java:10) この構造では、将来「店舗別法人」「グループ会社」「本番/検証 MF 事業者」を追加すると、全 API がどの tenant の token を使うか曖昧になります。フロントのヘルスチェックは `shopNo` を選べますが、OAuth token 側には `shopNo` や tenant mapping がありません。
- 推奨アクション: 今すぐ複数契約対応しない場合でも、DB に `tenant_key` または `company_scope` を追加し、全 MF API 呼び出しは明示的な接続 ID を受け取る形にしてください。最低限、全社単一前提を DB 制約と運用手順に明文化してください。

### 5. client_secret を業務 DB に置く設計が権限境界を曖昧にしている
- Severity: Major
- カテゴリ: 代替案
- 内容: `client_secret_enc` は暗号化されていますが、アプリが DB と暗号鍵の両方にアクセスできるため、アプリ侵害時には復号可能です。[MMfOauthClient.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/model/finance/MMfOauthClient.java:33) client_secret は「アプリケーションの身元」であり、業務データと同じバックアップ・同じ DBA 権限で扱うべきではありません。DB 暗号化は漏洩時の遅延策であって、鍵管理ポリシーではありません。
- 推奨アクション: client_id/secret は Vault / KMS / Spring Cloud Config + secret backend に寄せ、DB には secret version と接続設定だけを置く案を優先してください。DB 保存を続ける場合も、鍵分離、復号監査、secret rotation runbook を必須にしてください。

### 6. client_secret ローテーションの移行期間を扱えない
- Severity: Major
- カテゴリ: エッジケース
- 内容: `upsertClient()` は既存 client を in-place 更新し、旧 secret を保持しません。[MfOauthService.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java:79) MF アプリポータル側で secret を切り替える際、旧 secret で発行済み refresh token が新 secret で refresh 可能か、または旧 secret が一定期間必要かを設計が吸収できません。切替タイミングを誤ると、全社共通 token が同時に使えなくなります。
- 推奨アクション: `client_secret_version` と旧 secret の短期併存を設計してください。rotation は「新 secret 登録 → refresh 試験 → tenant 一致確認 → 旧 secret 廃棄」の手順にしてください。

### 7. scope 変更を破壊的変更として管理していない
- Severity: Major
- カテゴリ: エッジケース
- 内容: scope は画面入力文字列で、現在の default に `mfc/admin/tenant.read` がありません。[mf-integration.ts](/C:/project/odamitsu-data-hub/frontend/types/mf-integration.ts:196) MF 側が scope 名を変更・追加必須化・廃止した場合、既存 token は権限不足になり、機能ごとに 403 が出るまで検知できません。さらに「scope 更新保存」と「再認可」が別操作なので、設定だけ変わって token は旧 scope のままという不整合が起きます。
- 推奨アクション: 必須 scope をコード上の typed list として定義し、status に `missingScopes` / `extraScopes` を返してください。scope 設定変更後は connected を yellow/red に落とし、再認可完了まで連携機能を抑止してください。

### 8. 同期 API 呼び出しがジョブ化されておらず、失敗の再開点がない
- Severity: Major
- カテゴリ: 代替案
- 内容: 仕訳突合、勘定科目同期、残高突合は画面操作から同期的に MF API を呼びます。レート制限やメンテナンスで途中失敗した場合、どの期間・ページ・tenant・scope で失敗したかを永続的に追えません。特に `MfAccountSyncTab` の「適用」は差分 preview と apply の間に MF 側データが変わる可能性もあります。[MfAccountSyncTab.tsx](/C:/project/odamitsu-data-hub/frontend/components/pages/finance/MfAccountSyncTab.tsx:29)
- 推奨アクション: MF API 呼び出しは `mf_sync_job` と queue worker に寄せ、job id、対象期間、tenant、attempt、rate-limit wait、結果 checksum を保存してください。UI は `202 Accepted + job polling` にし、リトライと失敗通知を集中管理してください。

### 9. callback がフロントタブ依存で、BFFとして完結していない
- Severity: Major
- カテゴリ: 代替案
- 内容: token 自体はフロントに返していませんが、callback は Next.js ページが URL の `code/state` を読み、admin JWT 付きで backend に POST する設計です。[mf-integration-callback.tsx](/C:/project/odamitsu-data-hub/frontend/components/pages/finance/mf-integration-callback.tsx:22) つまり OAuth 完了はブラウザ localStorage/JWT/タブ通信に依存し、サーバー側だけで完結しません。企業向け SaaS 連携では、callback endpoint を BFF/backend が直接受けて state cookie/session と照合し、完了画面だけを返す方が監査・再試行・CSRF 境界が明確です。
- 推奨アクション: redirect URI を backend/BFF callback に変更し、server-side で code exchange と token 保存を完了してください。フロントには connection result id だけを返し、token や authorization code を UI 層の責務から外してください。

### 10. MF API 仕様変更の検知が運用に組み込まれていない
- Severity: Major
- カテゴリ: エッジケース
- 内容: MF API の typed DTO は、列追加には比較的強い一方、列削除・型変更・意味変更には静かに `null` や空配列として流れ、突合結果の誤判定になり得ます。公式ドキュメントにも API 更新・変更はアプリポータルのお知らせや変更ポリシー確認が必要とされています。現状は dev debug endpoint で手動確認する色が強く、CI 上の contract fixture や schema drift 検知が見えません。
- 推奨アクション: journals/accounts/taxes/trial_balance の代表 JSON fixture を固定し、DTO deserialize と業務分類の contract test を CI に入れてください。週次で sandbox/本番 read-only probe を走らせ、未知 shape や必須 field 欠落を Slack/メール通知してください。

### 11. 「MF認証が切れた」を誰が知るかが未設計
- Severity: Major
- カテゴリ: 運用整合
- 内容: 画面上の status/health はありますが、担当者への push 型通知がありません。[mf-health.ts](/C:/project/odamitsu-data-hub/frontend/types/mf-health.ts:57) 認証切れ、refresh 失敗、scope 不足、tenant mismatch、API 503 継続は経理業務の停止条件ですが、利用者が画面を開くまで発見できません。月次締め前日に初検知する運用は、SaaS 連携基盤として脆いです。
- 推奨アクション: 毎朝の scheduled health probe と、Critical 条件の即時通知を実装してください。通知先は「システム管理者」だけでなく「経理運用責任者」も設定できるようにしてください。

### 12. 監査証跡が変更事実を説明できない
- Severity: Major
- カテゴリ: 運用整合
- 内容: `add_user_no` / `modify_user_no` はありますが、何を変更したか、なぜ切断したか、どの tenant からどの tenant に変わったか、scope がどう変わったかを監査できません。[MfIntegrationController.java](/C:/project/odamitsu-data-hub/backend/src/main/java/jp/co/oda32/api/finance/MfIntegrationController.java:68) OAuth client 設定、再認可、revoke、勘定科目同期 apply は監査イベントとして扱うべき操作です。現状の行更新履歴だけでは内部不正や誤操作の事後調査に弱いです。
- 推奨アクション: `mf_integration_audit_log` を追加し、actor、operation、before/after hash、tenant、scope、IP/user-agent、reason を保存してください。secret/token は保存せず、fingerprint のみ残してください。

### 13. MF由来の個人情報・取引先情報の分類と保持期限がない
- Severity: Major
- カテゴリ: 運用整合
- 内容: MF journals には取引先名、補助科目、摘要、入力者など個人情報・取引先機密になり得る情報が含まれます。キャッシュ、debug raw endpoint、画面表示、ログ方針を個人情報保護法/GDPR 相当のデータ分類に載せていません。特に debug raw は shape 調査目的でも、実データを広く返すと管理者画面がデータ持ち出し口になります。
- 推奨アクション: MF データを「会計機密 + 個人情報含有可能」と分類し、キャッシュ TTL、debug endpoint の本番無効化、export 禁止、アクセス監査を定義してください。DPA/委託先管理・削除要求時の対象範囲も文書化してください。

### 14. メンテナンス・API廃止時の degraded mode がない
- Severity: Minor
- カテゴリ: エッジケース
- 内容: MF 側が 503 やメンテナンスを返す場合、公式 tenant API も 503 を返し得るとされています。現状は画面操作の失敗として扱うだけで、「MF一時停止中」と「認証切れ」と「自社データ不整合」を切り分ける運用状態がありません。キャッシュが残っている場合も、それが新鮮な根拠か過去データかを業務判断に使いにくいです。
- 推奨アクション: `provider_status` を health に追加し、直近成功時刻・連続失敗回数・Retry-After を表示してください。MF停止中は再認可を促さず、再試行予定時刻を出してください。

### 15. OAuth client 情報を DB 編集可能にする範囲が広すぎる
- Severity: Minor
- カテゴリ: 代替案
- 内容: `authorizeUrl`, `tokenUrl`, `apiBaseUrl`, `scope` を admin 画面から編集できます。[mf-integration.tsx](/C:/project/odamitsu-data-hub/frontend/components/pages/finance/mf-integration.tsx:282) 既出の URL 改ざんリスクとは別に、運用統制上「環境ごとの固定接続先」と「業務担当者が変更してよい client_id/secret」を分けるべきです。本番で endpoint を手入力する構造は、変更管理・レビュー・監査の外で連携先を変えられることを意味します。
- 推奨アクション: endpoint と必須 scope は Spring Cloud Config / 環境変数 / Vault config に寄せ、画面編集不可にしてください。画面から変更できる値は client credential 登録と再認可操作に限定してください。

## Opus が見落としていた最重要 3 件

1. **MF tenant binding 不在**: どの MF 事業者に接続したかを保存・検証しておらず、別会社 MF への誤接続が静かに業務データ不整合になります。  
2. **refresh token 540日・再利用不可を前提にした業務継続設計不足**: refresh 成功後の DB 保存失敗や期限予兆未検知が、月次経理停止に直結します。  
3. **運用監査と個人情報統制の不足**: OAuth 設定変更、再認可、revoke、MF 生データ閲覧が、監査・通知・保持期限の設計に載っていません。
