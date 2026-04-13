# ユーザー管理画面 設計書

## 1. 概要
旧システム（stock-app）のLoginUserList/Create/Modify機能を新システムに移行する。
ログインユーザーの一覧・検索・新規作成・編集・削除（論理削除）機能を提供する。
admin（shopNo=0）のみアクセス可能。

## 2. 画面設計

### 2.1 ユーザー一覧画面（/masters/users）
- マスタ管理の中の1タブではなく、サイドバー「外部連携・マスタ」セクションに独立メニューとして配置
- 検索条件: ユーザー名（部分一致）、ログインID（部分一致）
- 一覧表示列: No, ユーザー名, ログインID, 権限種別, 登録日時
- 操作: 編集ボタン、削除ボタン（ゴミ箱アイコン）
- 右上に「新規登録」ボタン
- admin以外はこのページにアクセスできない（サイドバーにも非表示）

### 2.2 ユーザー新規登録ダイアログ
- AlertDialogではなくDialog（shadcn/ui）でモーダル表示
- フィールド:
  - ログインID（必須、一意制約）
  - ユーザー名（必須）
  - パスワード（必須、5〜16文字）
  - パスワード確認（必須、パスワードと一致）
- 登録成功後: トースト通知 + 一覧自動リフレッシュ + ダイアログ閉じる

### 2.3 ユーザー編集ダイアログ
- Dialogでモーダル表示
- フィールド:
  - ログインID（必須）
  - ユーザー名（必須）
  - 新しいパスワード（任意、空欄なら変更しない、入力時は5〜16文字）
  - パスワード確認（パスワード入力時のみ必須）
- 旧パスワード入力は不要（admin操作のため）
- 更新成功後: トースト通知 + 一覧リフレッシュ + ダイアログ閉じる

### 2.4 削除確認ダイアログ
- AlertDialogで確認
- 論理削除（del_flg = '1'）

## 3. API設計

### 3.1 エンドポイント一覧
| Method | Path | 説明 | 認可 |
|--------|------|------|------|
| GET | /api/v1/users | ユーザー一覧（検索対応） | ROLE_ADMIN |
| GET | /api/v1/users/{loginUserNo} | ユーザー詳細取得 | ROLE_ADMIN |
| POST | /api/v1/users | ユーザー新規登録 | ROLE_ADMIN |
| PUT | /api/v1/users/{loginUserNo} | ユーザー更新 | ROLE_ADMIN |
| DELETE | /api/v1/users/{loginUserNo} | ユーザー論理削除 | ROLE_ADMIN |

### 3.2 リクエスト/レスポンスDTO

#### UserCreateRequest
```java
{
  "loginId": String,     // @NotBlank
  "userName": String,    // @NotBlank
  "password": String     // @NotBlank @Size(min=5, max=16)
}
```

#### UserUpdateRequest
```java
{
  "loginId": String,     // @NotBlank
  "userName": String,    // @NotBlank
  "password": String     // @Size(min=5, max=16)、null/空なら変更しない
}
```

#### UserResponse
```java
{
  "loginUserNo": Integer,
  "loginId": String,
  "userName": String,
  "companyNo": Integer,
  "companyType": String,
  "shopNo": Integer,
  "addDateTime": String
}
```

### 3.3 バリデーション
- loginId: 一意制約チェック（作成時・更新時に他ユーザーと重複しないこと）
- password: 5〜16文字（作成時は必須、更新時は任意）
- パスワードはBCryptでハッシュ化して保存

## 4. バックエンド実装

### 4.1 新規作成ファイル
- `backend/src/main/java/jp/co/oda32/api/user/UserController.java`
- `backend/src/main/java/jp/co/oda32/dto/user/UserCreateRequest.java`
- `backend/src/main/java/jp/co/oda32/dto/user/UserUpdateRequest.java`
- `backend/src/main/java/jp/co/oda32/dto/user/UserResponse.java`

### 4.2 既存ファイル変更
- `LoginUserService.java` — findAll(検索条件付き)、update、deleteメソッド追加
- `SecurityConfig.java` — `/api/v1/users/**` に ROLE_ADMIN 制限追加

### 4.3 認可制御
- UserControllerの全メソッドに `@PreAuthorize("hasRole('ADMIN')")` を付与
- SecurityConfigでは /api/v1/users/** をauthenticatedのままにし、メソッドレベルで制御

## 5. フロントエンド実装

### 5.1 新規作成ファイル
- `frontend/app/(authenticated)/masters/users/page.tsx` — ルーティング
- `frontend/components/pages/master/users.tsx` — ユーザー管理ページコンポーネント
- `frontend/types/user.ts` — 型定義

### 5.2 既存ファイル変更
- `frontend/components/layout/Sidebar.tsx` — 「ユーザー管理」メニュー追加（admin限定表示）

### 5.3 フォームバリデーション（Zod）
```typescript
const createSchema = z.object({
  loginId: z.string().min(1, 'ログインIDは必須です'),
  userName: z.string().min(1, 'ユーザー名は必須です'),
  password: z.string().min(5, 'パスワードは5文字以上').max(16, 'パスワードは16文字以下'),
  confirmPassword: z.string(),
}).refine(d => d.password === d.confirmPassword, {
  message: 'パスワードが一致しません', path: ['confirmPassword']
})

const updateSchema = z.object({
  loginId: z.string().min(1, 'ログインIDは必須です'),
  userName: z.string().min(1, 'ユーザー名は必須です'),
  password: z.string().max(16).optional().or(z.literal('')),
  confirmPassword: z.string().optional().or(z.literal('')),
}).refine(d => !d.password || d.password === d.confirmPassword, {
  message: 'パスワードが一致しません', path: ['confirmPassword']
}).refine(d => !d.password || d.password.length >= 5, {
  message: 'パスワードは5文字以上', path: ['password']
})
```

## 6. セキュリティ考慮事項
- パスワードはBCryptハッシュ化（既存パターン踏襲）
- APIレスポンスにパスワードを含めない
- ROLE_ADMIN以外のアクセスは403
- 自分自身の削除は許可しない（バックエンドでチェック）
