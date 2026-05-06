# 認可マトリクス (T4)

## 1. 統一方針
全 Controller の admin 判定を `@PreAuthorize("@loginUserSecurityBean.isAdmin()")` で統一。
`hasRole('ADMIN')` / `authentication.principal.shopNo == 0` の 2 系統並存を撤廃。

## 2. LoginUser.isAdmin() 仕様

判定式: `MLoginUser.getShopNo() == 0` かつ `Authorities` に `ROLE_ADMIN` を含む。
両者が乖離する場合 (DB 設定不整合等) は `false` (fail-safe)。

### shopNo の解決
`MLoginUser.getShopNo()` は CompanyType を見て返す:
- ADMIN: `OfficeShopNo.ADMIN.getValue()` = 0 固定
- SHOP: `m_company.shop_no`
- PARTNER: `m_company.m_partner.shop_no`
- 該当なし: -1

### Authority の付与
`LoginUser.resolveRoles()` で CompanyType に応じて付与:
- ADMIN → ROLE_USER + ROLE_ADMIN
- SHOP → ROLE_USER + ROLE_SHOP
- PARTNER → ROLE_USER + ROLE_PARTNER

ユーザー判断 (Q1=(a)) により shopNo=0 と ROLE_ADMIN は常に同期する前提。
不整合発生時は fail-safe で admin 権限を拒否。

## 3. 認可レベル

| レベル | アノテーション | 用途 |
|---|---|---|
| 認証なし | (なし、SecurityFilterChain で制御) | `/api/v1/auth/login` |
| 認証あり | `@PreAuthorize("isAuthenticated()")` | 一般 endpoint (class level) |
| admin 限定 | `@PreAuthorize("@loginUserSecurityBean.isAdmin()")` | マスタ更新、バッチ実行、MF 連携、財務処理など |
| shop user 自 shop 限定 | `LoginUserUtil.resolveEffectiveShopNo()` (service 層) | 個別 endpoint で shop_no を要求するクエリ |

`isAdmin()` と `resolveEffectiveShopNo()` は別目的:
- `isAdmin()`: admin 専用画面/操作のゲート
- `resolveEffectiveShopNo()`: 認証済 shop user が他 shop データを覗けないようにする IDOR ガード

両者は併用可能 (例: admin endpoint 内で更に shop 単位の絞り込み、または認証済全員が自 shop データのみ閲覧)。

## 4. SpEL Bean 仕様

```java
@Component("loginUserSecurityBean")
public class LoginUserSecurityBean {
    public boolean isAdmin();      // SpEL: @loginUserSecurityBean.isAdmin()
    public boolean isShopUser();   // SpEL: @loginUserSecurityBean.isShopUser()
}
```

未認証 / principal が `LoginUser` でない場合は両方 false (fail-safe)。

## 5. 移行履歴

### T4 (2026-05-06): 認可マトリクス統一
**Before** (3 系統並存):
1. `@PreAuthorize("hasRole('ADMIN')")` — 29 箇所
2. `@PreAuthorize("authentication.principal.shopNo == 0")` — 8 箇所
3. service 層 `LoginUserUtil.resolveEffectiveShopNo()` — 個別

**After** (1+2 を統一):
- `@PreAuthorize("@loginUserSecurityBean.isAdmin()")` — 47 箇所 (Finance + その他 13 ファイル)
- `LoginUserUtil.resolveEffectiveShopNo()` は別目的のため継続

### 関連 fix
- P1-01 (Cluster F): MF tenant binding (admin 限定)
- P1-05 (Cluster F): OAuth 鍵分離 (admin 限定)
- Cluster A round 2 CR-N5: `LoginUserUtil` fail-closed 化

## 6. 移行影響

### 既存の SpEL `principal.shopNo == 0` の問題
`LoginUser` クラスは `org.springframework.security.core.userdetails.User` を継承し、
`shopNo` プロパティを持たない。SpEL `principal.shopNo` は `LoginUser.getShopNo()` を期待するが、
実際には NoSuchPropertyError か silent null 評価される。
T4 で本 SpEL を撤廃し、`isAdmin()` 経由で `MLoginUser.getShopNo()` を参照することで意図通りに動作。

### 既存の `hasRole('ADMIN')` との等価性
ユーザー判断 (Q1=(a)) により shopNo=0 ⇔ ROLE_ADMIN が常に同期する前提下で、
`isAdmin()` (両方判定) は `hasRole('ADMIN')` (片方判定) と挙動等価。
不整合時は `isAdmin()` の方が厳格 (両方 true でないと拒否)。

## 7. テスト戦略

### 単体テスト
- `LoginUserAdminTest` (5 ケース): isAdmin() 境界条件
  - shopNo=0 + ROLE_ADMIN → true
  - shopNo=0 + ROLE_SHOP → false (fail-safe)
  - 不正 CompanyType (shopNo=-1) → false
  - shopNo null (companyType=null) → false
  - admin の isShopUser 判定補助
- `LoginUserSecurityBeanTest` (5 ケース): SpEL Bean 経由判定
  - admin principal → isAdmin=true / isShopUser=false
  - shop principal → isAdmin=false / isShopUser=true
  - 未認証 → 両方 false
  - anonymous authentication → 両方 false
  - principal が LoginUser 以外 → 両方 false

### Controller integration test
本プロジェクトでは `@WithMockUser` を使った Controller integration test は未実装
(2026-05-06 時点)。今後追加する場合は `@WithUserDetails` で `LoginUser` を直接 mock
するか、`SecurityContextHolder` 経由で `LoginUser` を組み立てる必要がある
(`@WithMockUser(roles="ADMIN")` は principal が `User` になるため `isAdmin()` が
fail-safe で false を返してしまう)。

## 8. 関連 DESIGN-DECISION
- T4 (本ドキュメント): 認可マトリクス統一
- T1: 数字の権威階層 (admin = 全画面 / 全 shop データ閲覧可能)
- P1-01: MF tenant binding (admin 限定)
- P1-05: OAuth 鍵分離 (admin 限定)
- C-N5 round 2: `LoginUserUtil` fail-closed (anonymous → AccessDeniedException)
