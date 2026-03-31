# 認証・セキュリティ・セッション管理 仕様書

**対象システム**: stock-app（小田光株式会社 社内システム）
**技術スタック**: Spring Boot 2.1.1 / Spring Security / PostgreSQL 9.6
**作成日**: 2026-02-23

---

## 目次

1. [認証方式（ログイン処理フロー）](#1-認証方式ログイン処理フロー)
2. [ユーザーモデル（MLoginUser エンティティ定義）](#2-ユーザーモデルmloginuser-エンティティ定義)
3. [パスワード管理（暗号化方式）](#3-パスワード管理暗号化方式)
4. [セッション管理（タイムアウト・リダイレクト）](#4-セッション管理タイムアウトリダイレクト)
5. [セキュリティ設定（CSRF・URL制御・認可）](#5-セキュリティ設定csrfurl制御認可)
6. [ユーザー管理画面（一覧・登録・編集）](#6-ユーザー管理画面一覧登録編集)
7. [ロール・権限管理](#7-ロール権限管理)
8. [アクセスログ](#8-アクセスログ)

---

## 1. 認証方式（ログイン処理フロー）

### 1.1 認証方式概要

Spring Security のフォームログイン認証を使用している。ユーザーはログインIDとパスワードを入力してログインする。認証処理は `LoginUserService`（`UserDetailsService` 実装）が担当する。

### 1.2 関連クラス

| クラス | パッケージ | 役割 |
|---|---|---|
| `SecurityConfig` | `jp.co.oda32` | Spring Security 全体設定 |
| `LoginUserService` | `jp.co.oda32.domain.service.login` | `UserDetailsService` 実装・認証処理 |
| `LoginUser` | `jp.co.oda32.domain.service.data` | 認証済みユーザー情報の保持（`UserDetails` 実装） |
| `LoginController` | `jp.co.oda32.app.login` | ログインフォーム表示コントローラー |
| `CustomAuthenticationSuccessHandler` | `jp.co.oda32.config` | 認証成功時ハンドラー |

### 1.3 ログイン処理フロー

```
[ブラウザ]
    │
    ▼
GET /loginForm
    │
    ▼
[LoginController#loginForm()]
    │  クエリパラメータ確認:
    │  - error   → errorMessage をモデルに追加
    │  - timeout → timeoutMessage をモデルに追加
    │  セッション属性 LAST_ACCESSED_URL_BEFORE_TIMEOUT があれば
    │             redirectMessage をモデルに追加
    ▼
テンプレート: login/loginForm.html を返却
    │
    ▼
[ブラウザ] login_id / password を入力して POST /login
    │
    ▼
[Spring Security UsernamePasswordAuthenticationFilter]
    │  usernameParameter: "login_id"
    │  passwordParameter: "password"
    ▼
[LoginUserService#loadUserByUsername(loginId)]
    │  LoginUserRepository#findByLoginId(loginId) でDBからユーザー取得
    │  ユーザーが存在しない → UsernameNotFoundException をスロー
    │  ユーザーが存在する  → LoginUser(MLoginUser) を返却
    ▼
[Spring Security] パスワード照合（BCrypt）
    │  照合失敗 → /loginForm?error にリダイレクト
    │  照合成功 ↓
    ▼
[CustomAuthenticationSuccessHandler#onAuthenticationSuccess()]
    │  優先順位:
    │  1. セッション属性 LAST_ACCESSED_URL_BEFORE_TIMEOUT が存在し、
    │     ログイン関連URL以外の場合 → その URL にリダイレクト
    │  2. Spring Security が保存していたリクエスト（SavedRequest）がある場合
    │     → その URL にリダイレクト
    │  3. 上記いずれも該当しない場合 → /dashboard にリダイレクト
    ▼
[ダッシュボード or 元のアクセス先ページ]
```

### 1.4 ログアウト処理フロー

| 項目 | 設定値 |
|---|---|
| ログアウト処理URL | `POST /logout`（`AntPathRequestMatcher` 使用） |
| ログアウト成功後リダイレクト先 | `/loginForm` |
| セッション破棄 | 有効（`invalidateHttpSession(true)`） |
| 削除クッキー | `JSESSIONID` |

### 1.5 ログインフォーム仕様

- テンプレートファイル: `src/main/resources/templates/login/loginForm.html`
- POST 先: `/login`
- フォームフィールド:
  - `login_id`（テキスト、必須）
  - `password`（パスワード、必須）
- メッセージ種別:
  - `errorMessage` - 認証失敗（赤背景）: 「ログインIDまたはパスワードが正しくありません。」
  - `timeoutMessage` - セッションタイムアウト（黄背景）: 「セッションがタイムアウトしました。再度ログインしてください。」
  - `redirectMessage` - タイムアウト後ログイン時（青背景）: 「ログイン後、前回アクセスしていたページに戻ります。」

### 1.6 `LoginUser`（UserDetails 実装）

```java
// jp.co.oda32.domain.service.data.LoginUser
public class LoginUser extends org.springframework.security.core.userdetails.User {
    private final MLoginUser loginUser;

    public LoginUser(MLoginUser user) {
        super(user.getLoginId(), user.getPassword(),
              AuthorityUtils.createAuthorityList("ROLE_USER"));
        this.loginUser = user;
    }
}
```

全ユーザーに `ROLE_USER` が付与される（単一ロール設計）。

---

## 2. ユーザーモデル（MLoginUser エンティティ定義）

### 2.1 エンティティ基本情報

| 項目 | 値 |
|---|---|
| クラス名 | `MLoginUser` |
| パッケージ | `jp.co.oda32.domain.model.master` |
| テーブル名 | `m_login_user` |
| 主キー生成 | シーケンス `m_login_user_login_user_no_seq`（`allocationSize = 1`） |
| インターフェース | `IEntity` |
| バリデーション | `@ShopEntity` |

### 2.2 フィールド定義

| フィールド名（Java） | カラム名（DB） | 型 | 説明 |
|---|---|---|---|
| `loginUserNo` | `login_user_no` | `Integer` | 主キー。ログインユーザー番号（自動採番） |
| `userName` | `user_name` | `String` | ユーザー表示名 |
| `password` | `password` | `String` | BCrypt ハッシュ済みパスワード |
| `loginId` | `login_id` | `String` | ログイン時に使用するID（一意） |
| `companyNo` | `company_no` | `Integer` | 所属会社番号（`m_company` の外部キー） |
| `companyType` | `company_type` | `String` | 会社種類（`admin` / `shop` / `partner`） |
| `addDateTime` | `add_date_time` | `Timestamp` | 登録日時 |
| `addUserNo` | `add_user_no` | `Integer` | 登録者ユーザー番号 |
| `modifyDateTime` | `modify_date_time` | `Timestamp` | 更新日時 |
| `modifyUserNo` | `modify_user_no` | `Integer` | 更新者ユーザー番号 |
| `delFlg` | `del_flg` | `String` | 論理削除フラグ（`'0'`=有効、`'1'`=削除） |
| `company` | -（`company_no` 外部キー） | `MCompany` | 所属会社エンティティ（読み取り専用） |

### 2.3 `getShopNo()` ロジック

`IEntity` インターフェースで定義された `getShopNo()` メソッドは、`companyType` に基づいて以下の shop_no を返す：

| `companyType` 値 | `CompanyType` 列挙値 | 返却する shop_no |
|---|---|---|
| `"admin"` | `ADMIN` | `0`（`OfficeShopNo.ADMIN.getValue()`） |
| `"shop"` | `SHOP` | `company.getShopNo()`（所属ショップのshop_no） |
| `"partner"` | `PARTNER` | `company.getPartner().getShopNo()`（取引先の所属ショップshop_no） |
| 該当なし | -（null） | `-1` |

### 2.4 関連エンティティ（MCompany）

`MLoginUser` は `MCompany` と `@OneToOne` で紐付く（`company_no` による結合、`insertable = false, updatable = false`）。

`MCompany` の主要フィールド:

| フィールド名 | カラム名 | 説明 |
|---|---|---|
| `companyNo` | `company_no` | 主キー |
| `shopNo` | `shop_no` | 店舗番号 |
| `partnerNo` | `partner_no` | 取引先番号 |
| `companyName` | `company_name` | 会社名 |
| `companyType` | `company_type` | 会社種類 |

---

## 3. パスワード管理（暗号化方式）

### 3.1 暗号化アルゴリズム

BCrypt（`BCryptPasswordEncoder`）を使用する。

```java
// SecurityConfig.AuthenticationConfiguration より
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

StrengthはSpring Securityデフォルト値（strength=10）を使用。

### 3.2 パスワード暗号化の適用箇所

| 処理 | クラス | 説明 |
|---|---|---|
| ユーザー登録 | `LoginUserService#insert()` | `passwordEncoder.encode()` でハッシュ化してDB保存 |
| パスワード更新 | `LoginUserService#update()` | `loginUserModifyForm.getPassword() != null` の場合のみハッシュ化して更新 |
| ログイン認証 | Spring Security 内部 | `passwordEncoder.matches()` によるハッシュ照合 |

### 3.3 パスワードバリデーション（登録・更新共通）

- 最小文字数: 5文字（`@Size(min = 5)`）
- 最大文字数: 16文字（`@Size(max = 16)`）
- 確認用パスワードとの一致チェック: `@Confirm(field = "password")` アノテーション（`ConfirmValidator` による検証）

### 3.4 旧パスワード検証（更新時）

パスワード更新フォーム（`LoginUserModifyForm`）では `@OldPassword(field = "oldPassword")` アノテーションにより旧パスワードの正当性を検証する。

`OldPasswordValidator` の検証ロジック:
1. セッションからログインユーザーのハッシュ済みパスワードを取得
2. 入力された旧パスワードを `passwordEncoder.encode()` でハッシュ化
3. 両者を比較（照合）し、不一致の場合はバリデーションエラー

**注意**: 現在の実装では `encode()` 同士の比較を行っているが、BCryptの仕様上、同一平文でも毎回異なるソルトが使用されるため、`passwordEncoder.matches(rawPassword, encodedPassword)` による比較が正しい方式である。

---

## 4. セッション管理（タイムアウト・リダイレクト）

### 4.1 セッションタイムアウト設定

`src/main/resources/config/application.yml` にて設定：

```yaml
spring:
  session:
    timeout: 3600  # 3600秒 = 1時間

server:
  servlet:
    session:
      timeout: 3600s  # 1時間（明示的な単位付き）
```

タイムアウト時間: **1時間（3600秒）**

### 4.2 同時セッション制御

```java
// SecurityConfig より
http.sessionManagement()
    .maximumSessions(1)               // 同時セッション数を1に制限
    .maxSessionsPreventsLogin(false)  // 新しいログインを許可（古いセッションを無効化）
```

同一ユーザーが複数ブラウザからログインした場合、古いセッションが無効化され、新しいセッションが有効になる。

### 4.3 セッション固定攻撃対策

```java
.sessionFixation().changeSessionId()
```

ログイン成功時にセッションIDを変更することでセッション固定攻撃を防ぐ。

### 4.4 セッションタイムアウト検知フィルター（SessionTimeoutFilter）

クラス: `jp.co.oda32.filter.SessionTimeoutFilter`（`OncePerRequestFilter` 継承）

フィルターチェーン上の位置: `UsernamePasswordAuthenticationFilter` の前（`addFilterBefore`）

**動作**:

1. **認証済みユーザーのアクセス時**（GETリクエスト、Ajax以外、除外URL以外）:
   - 現在のURLとクエリパラメータをセッション属性 `LAST_ACCESSED_URL` に保存
   - ダッシュボード（`/dashboard`）はURLとして保存しない

2. **未認証（セッション切れ）時**:
   - セッション属性 `LAST_ACCESSED_URL` が存在する場合、`LAST_ACCESSED_URL_BEFORE_TIMEOUT` に移動（rename）
   - これにより、ログイン後のリダイレクト先として利用できる

**記録対象外URL（`EXCLUDED_URLS`）**:

```
/loginForm, /login, /logout, /error, /dashboard,
/css/, /js/, /images/, /fonts/, /favicon.ico,
/api/, /batch
```

### 4.5 セッションタイムアウト時のリダイレクト戦略（SessionTimeoutRedirectStrategy）

クラス: `jp.co.oda32.config.SessionTimeoutRedirectStrategy`（`InvalidSessionStrategy` 実装）

セッション無効検知時の動作:

| リクエスト種別 | 動作 |
|---|---|
| Ajax リクエスト（`X-Requested-With: XMLHttpRequest`） | HTTP 401 を返却し、JSONで `{"error":"Session timeout","redirectUrl":"/loginForm?timeout=true"}` を返す |
| 通常リクエスト（ログインフォーム・ログイン関連URL） | `forward` で `/loginForm?timeout=true` に転送（リダイレクトループ防止） |
| 通常リクエスト（その他） | `/loginForm?timeout=true` にリダイレクト |

### 4.6 ログイン成功時のリダイレクト（CustomAuthenticationSuccessHandler）

クラス: `jp.co.oda32.config.CustomAuthenticationSuccessHandler`
（`SavedRequestAwareAuthenticationSuccessHandler` 継承）

設定値:
- デフォルトターゲットURL: `/dashboard`
- `alwaysUseDefaultTargetUrl`: `false`（保存されたリクエストを優先）

リダイレクト優先順位（高い順）:

1. セッション属性 `LAST_ACCESSED_URL_BEFORE_TIMEOUT` が存在し、ログイン関連URL以外 → そのURLへリダイレクト後、属性を削除
2. Spring Security が保存した `SavedRequest` が存在し、ログイン関連URL以外 → 保存されたURLへリダイレクト
3. 上記いずれも該当しない → `/dashboard` へリダイレクト

**ログイン関連URLの判定条件**（`isLoginRelatedUrl()` メソッド）:
- `/loginForm` を含む
- `/login` を含む
- `/logout` を含む
- `/error` を含む
- ルートパス `/` と完全一致
- 空文字列

---

## 5. セキュリティ設定（CSRF・URL制御・認可）

### 5.1 全体設定クラス

クラス: `jp.co.oda32.SecurityConfig`
アノテーション: `@Configuration`, `@EnableWebSecurity`
継承: `WebSecurityConfigurerAdapter`

### 5.2 静的リソースの除外設定

Spring Security のセキュリティチェック対象外となるパス（`WebSecurity#ignoring()`）:

```
/favicon.ico
/css/**
/js/**
/images/**
/fonts/**
```

これらは認証・認可チェックが一切適用されない。

### 5.3 URL認可設定（HttpSecurity）

```java
http.authorizeRequests()
    .antMatchers("/loginForm").permitAll()   // ログインフォームは全員許可
    .anyRequest().authenticated();            // それ以外は認証必須
```

| URLパターン | アクセス制御 |
|---|---|
| `/loginForm` | 全員許可（`permitAll()`） |
| その他全てのURL | 認証済みユーザーのみ（`authenticated()`） |

注: ソースコード上に `antMatchers("/admin/**").permitAll()` がコメントアウトされている（将来の検討項目）。

### 5.4 フォームログイン設定

| 設定項目 | 値 |
|---|---|
| ログイン処理URL | `POST /login` |
| ログインページURL | `/loginForm` |
| 認証失敗時リダイレクト先 | `/loginForm?error` |
| 認証成功ハンドラー | `CustomAuthenticationSuccessHandler` |
| デフォルト成功リダイレクト先 | `/dashboard` |
| ユーザー名パラメータ名 | `login_id` |
| パスワードパラメータ名 | `password` |

### 5.5 CSRF保護

Spring Security のデフォルト設定を使用しており、CSRF保護は**有効**である。明示的に `csrf().disable()` は行っていない。

Thymeleaf テンプレートでは `th:action="@{/login}"` のように Thymeleaf + Spring Security 統合を使用しているため、フォーム送信時にCSRFトークンが自動付与される。

### 5.6 セッション作成ポリシー

```java
.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
```

必要な場合にのみセッションを作成する（デフォルト動作）。

### 5.7 認証プロバイダー設定

クラス: `SecurityConfig.AuthenticationConfiguration`（`GlobalAuthenticationConfigurerAdapter` 継承）

```java
auth.userDetailsService(this.userDetailsService)  // LoginUserService
    .passwordEncoder(passwordEncoder);             // BCryptPasswordEncoder
```

---

## 6. ユーザー管理画面（一覧・登録・編集）

### 6.1 画面一覧

| 画面名 | URL | HTTPメソッド | テンプレート |
|---|---|---|---|
| 管理者一覧 | `/loginUserList` | GET / POST | `login/login_user_list.html` |
| 管理者登録フォーム | `/loginUserCreateForm` | GET | `login/login_user_create_form.html` |
| 管理者登録処理 | `/loginUserCreate` | POST | - |
| 管理者登録完了 | - | - | `login/login_user_create_complete.html` |
| 管理者更新フォーム | `/loginUserModifyForm` | GET | `login/login_user_modify_form.html` |
| 管理者更新処理 | `/loginUserModify` | POST | - |
| 管理者更新完了 | - | - | `login/login_user_modify_complete.html` |

### 6.2 管理者一覧画面

**コントローラー**: `LoginUserListController`（`jp.co.oda32.app.login`）

**GET `/loginUserList`**:
- `LoginUserService#findAll()` で全ユーザーを取得
- 現在のログインユーザー情報をコンソールに出力（`loginUserNo`, `userName`）
- `loginUserList` と `loginUserListForm` をモデルに追加

**POST `/loginUserList`**（検索）:
- `LoginUserListForm` のバリデーション後、`LoginUserService#find()` で絞り込み検索
- 結果を `loginUserNo` で昇順ソートして表示

**検索フォーム（LoginUserListForm）**:

| フィールド | 型 | 説明 |
|---|---|---|
| `loginUserNo` | `Integer` | 管理者番号（完全一致） |
| `userName` | `String` | 管理者名（完全一致） |
| `loginId` | `String` | ログインID（完全一致） |

**検索条件（LoginUserSpecification）**:
- `loginUserNoContains`: 管理者番号の完全一致
- `userNameContains`: ユーザー名の完全一致
- `loginIdContains`: ログインIDの完全一致
- `delFlgContains`: 論理削除フラグ（削除済みを除外）

### 6.3 管理者登録画面

**コントローラー**: `LoginUserCreateController`（`jp.co.oda32.app.login`）

**GET `/loginUserCreateForm`**: 空の `LoginUserCreateForm` を返す

**POST `/loginUserCreate`**: フォームバリデーション後、`LoginUserService#insert()` を呼び出す

**登録フォーム（LoginUserCreateForm）**:

| フィールド | 型 | バリデーション | 説明 |
|---|---|---|---|
| `loginUserNo` | `Integer` | - | ユーザー番号（自動採番のため入力不要） |
| `loginId` | `String` | `@NotBlank` | ログインID |
| `password` | `String` | `@NotBlank`, `@Size(min=5, max=16)` | パスワード |
| `confirmPassword` | `String` | `@Confirm(field="password")` | パスワード確認 |
| `userName` | `String` | `@NotBlank` | ユーザー表示名 |
| `priority` | `String` | - | 権限（任意入力） |

**登録処理の特記事項**:
- `companyNo` は固定値 `1` が設定される
- `companyType` は `CompanyType.ADMIN.getValue()` = `"admin"` が設定される
- パスワードは BCrypt でハッシュ化して保存

### 6.4 管理者更新画面

**コントローラー**: `LoginUserModifyController`（`jp.co.oda32.app.login`）

**GET `/loginUserModifyForm`**:
- 現在ログイン中のユーザー情報を取得して `LoginUserModifyForm` に設定
- パスワードフィールドをクリア（`setPassword(null)`）して表示

**POST `/loginUserModify`**: フォームバリデーション後、`LoginUserService#update()` を呼び出す

**更新フォーム（LoginUserModifyForm）**:

| フィールド | 型 | バリデーション | 説明 |
|---|---|---|---|
| `loginUserNo` | `Integer` | - | ユーザー番号 |
| `loginId` | `String` | `@NotBlank` | ログインID |
| `oldPassword` | `String` | `@NotBlank`, `@OldPassword` | 現在のパスワード |
| `password` | `String` | `@Size(min=5, max=16)` | 新しいパスワード |
| `confirmPassword` | `String` | `@Confirm(field="password")` | 新パスワード確認 |
| `userName` | `String` | `@NotBlank` | ユーザー表示名 |
| `priority` | `String` | - | 権限 |

**更新処理の特記事項**:
- `LoginUserRepository#findByLoginId()` で既存ユーザーを取得して上書き
- `password` が null の場合はパスワード更新をスキップ
- `@OldPassword` バリデーションにより旧パスワードの正当性を事前検証

### 6.5 リポジトリ（LoginUserRepository）

クラス: `jp.co.oda32.domain.repository.master.LoginUserRepository`
継承: `JpaRepository<MLoginUser, Integer>`, `JpaSpecificationExecutor<MLoginUser>`

| メソッド | 説明 |
|---|---|
| `findAll()` | 全ユーザーを取得 |
| `findByUserName(String userName)` | ユーザー名でリスト取得 |
| `findByLoginId(String loginId)` | ログインIDで1件取得（認証時に使用） |

---

## 7. ロール・権限管理

### 7.1 Spring Security ロール

現在の実装では、全ての認証済みユーザーに単一ロール **`ROLE_USER`** が付与される。

```java
// LoginUser コンストラクタより
super(user.getLoginId(), user.getPassword(),
      AuthorityUtils.createAuthorityList("ROLE_USER"));
```

URL単位でのロールベースアクセス制御（例: `hasRole("ADMIN")`）は未実装。

### 7.2 アプリケーション独自の会社種類（CompanyType）

Spring Security のロールとは別に、エンティティ `companyType` フィールドによってユーザーの種別を区別する。

| 列挙値 | DB格納値 | 説明 | shop_no |
|---|---|---|---|
| `ADMIN` | `"admin"` | 管理者（会社全体を管理） | `0`（固定） |
| `SHOP` | `"shop"` | ショップ担当者 | 所属ショップのshop_no |
| `PARTNER` | `"partner"` | 取引先担当者 | 取引先の所属ショップshop_no |

クラス: `jp.co.oda32.constant.CompanyType`

### 7.3 営業所コード（OfficeShopNo）

`shop_no` の具体的な値は以下の列挙型で定義されている。

クラス: `jp.co.oda32.constant.OfficeShopNo`

| 列挙値 | `shop_no` 値 | 説明 |
|---|---|---|
| `ADMIN` | `0` | 管理者 |
| `DAIICHI` | `1` | 第1事業部 |
| `DAINI` | `2` | 第2事業部 |
| `CLEAN_LABO` | `3` | クリーンラボ事業部 |
| `INNER_PURCHASE` | `1` | 社内仕入 |
| `INNER_ORDER` | `1` | 社内売上 |
| `B_CART_ORDER` | `1` | B-Cart売上 |

### 7.4 ログインユーザー情報の取得方法

認証済みユーザーのエンティティ情報は `LoginUserUtil` 経由で取得する。

```java
// 使用例
MLoginUser loginUser = LoginUserUtil.getLoginUserInfo().getUser();
```

クラス: `jp.co.oda32.domain.service.util.LoginUserUtil`

内部では `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` から `LoginUser` オブジェクトを取得し、`MLoginUser` を返す。

---

## 8. アクセスログ

### 8.1 概要

全HTTPリクエストをDBのアクセスログテーブル（`t_access_log`）に記録する。

### 8.2 関連クラス

| クラス | パッケージ | 役割 |
|---|---|---|
| `LogSettingFilter` | `jp.co.oda32.filter` | アクセスログ記録フィルター |
| `TAccessLog` | `jp.co.oda32.domain.model` | アクセスログエンティティ |
| `AccessLogService` | `jp.co.oda32.domain.service.data` | アクセスログ保存サービス |
| `AccessLogRepository` | `jp.co.oda32.domain.repository` | アクセスログリポジトリ |

### 8.3 アクセスログエンティティ（TAccessLog）

テーブル名: `t_access_log`
主キー: `access_id`（シーケンス `t_access_log_access_id_seq`）

| フィールド名（Java） | カラム名 | 型 | 制約 | 説明 |
|---|---|---|---|---|
| `accessId` | `access_id` | `Integer` | PK, auto | アクセスID |
| `accessTime` | `access_time` | `LocalDateTime` | - | アクセス日時 |
| `loginId` | `login_id` | `String` | - | ログインID（未認証時はnull） |
| `ipAddress` | `ip_address` | `String` | - | クライアントIPアドレス |
| `uri` | `uri` | `String` | - | リクエストURI |
| `method` | `method` | `String` | - | HTTPメソッド（GET/POST等） |
| `sessionId` | `session_id` | `String` | - | セッションID |
| `key1`～`key10` | `key1`～`key10` | `String` | max 100文字 | リクエストパラメータ名（1～10番目） |
| `value1`～`value10` | `value1`～`value10` | `String` | max 1000文字 | リクエストパラメータ値（1～10番目） |
| `keyOther` | `key_other` | `String` | max 1000文字 | 11番目以降のパラメータ名（`\|` 区切り） |
| `valueOther` | `value_other` | `String` | max 4000文字 | 11番目以降のパラメータ値（`\|` 区切り） |

### 8.4 LogSettingFilter の動作

クラス: `jp.co.oda32.filter.LogSettingFilter`（`javax.servlet.Filter` 実装）
`@Service` アノテーションで Spring Bean として登録。

**記録タイミング**: リクエスト処理の**完了後**（`filterChain.doFilter()` の後）

**記録スキップ条件**: リクエストURIにドット（`.`）が含まれる場合（静的ファイル）

```
例: /css/style.css, /js/app.js, /images/logo.png → スキップ
例: /loginUserList, /dashboard → 記録
```

**記録内容**:
1. アクセス日時（`LocalDateTime.now()`）
2. HTTPメソッド（`request.getMethod()`）
3. ログインID（`SecurityContextHolder` から取得、未認証時は null）
4. IPアドレス（`request.getRemoteAddr()`）
5. URI（`request.getRequestURI()`）
6. セッションID（`request.getSession().getId()`）
7. リクエストパラメータ（最大10件まで key/value ペアとして記録、11件目以降は `keyOther`/`valueOther` に `|` 区切りで結合）

**エラーハンドリング**:
- ログ保存時の例外はスタックトレースを出力するが、処理は継続する（ログ失敗がリクエスト処理に影響しない）

### 8.5 リポジトリ

```java
public interface AccessLogRepository
    extends JpaRepository<TAccessLog, Integer>,
            JpaSpecificationExecutor<TAccessLog> {
    // 標準メソッドのみ（カスタムクエリなし）
}
```

---

## 付録：主要URLパスまとめ

| URL | メソッド | 説明 | 認証要否 |
|---|---|---|---|
| `/loginForm` | GET | ログインフォーム表示 | 不要 |
| `/login` | POST | 認証処理（Spring Security） | 不要 |
| `/logout` | POST | ログアウト処理 | 不要 |
| `/dashboard` | GET | ダッシュボード（ログイン後トップ） | 要 |
| `/loginUserList` | GET/POST | 管理者一覧・検索 | 要 |
| `/loginUserCreateForm` | GET | 管理者登録フォーム | 要 |
| `/loginUserCreate` | POST | 管理者登録処理 | 要 |
| `/loginUserModifyForm` | GET | 管理者更新フォーム（自分のみ） | 要 |
| `/loginUserModify` | POST | 管理者更新処理 | 要 |

## 付録：セッション属性まとめ

| 属性名 | 設定クラス | 内容 |
|---|---|---|
| `LAST_ACCESSED_URL` | `SessionTimeoutFilter` | 認証済みユーザーの最後のアクセスURL（クエリパラメータ含む） |
| `LAST_ACCESSED_URL_BEFORE_TIMEOUT` | `SessionTimeoutFilter` | タイムアウト前の最後のURLに改名されたもの（ログイン後リダイレクト用） |

## 付録：バリデーションアノテーションまとめ

| アノテーション | クラス | バリデーター | 説明 |
|---|---|---|---|
| `@Confirm(field="password")` | `LoginUserCreateForm`, `LoginUserModifyForm` | `ConfirmValidator` | パスワードと確認用パスワードの一致チェック |
| `@OldPassword(field="oldPassword")` | `LoginUserModifyForm` | `OldPasswordValidator` | 現在のパスワードの正当性チェック |
| `@ShopEntity` | `MLoginUser`, `MCompany` | - | ショップエンティティであることを示す（詳細はドメインバリデーション仕様参照） |
