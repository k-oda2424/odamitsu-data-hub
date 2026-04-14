# 小田光データ連携基盤 (OdaMitsu Data Hub)

## 技術スタック
- **バックエンド**: Java 21, Spring Boot 3.3.x, Spring Batch 5.x, PostgreSQL 17, Gradle 8.x
- **フロントエンド**: Next.js 16, React 19, TypeScript strict, shadcn/ui, TanStack Query
- **主要連携**: SMILE（基幹）, B-CART（EC）, マネーフォワード（会計）
- **旧システム**: stock-app（Spring Boot 2.1.1 / C:\project\stock-app）— 並行運用中

## 開発コマンド
```bash
# バックエンド
cd backend && ./gradlew bootRun --args='--spring.profiles.active=web,dev'
cd backend && ./gradlew test

# フロントエンド
cd frontend && npm run dev     # localhost:3000
cd frontend && npx tsc --noEmit
cd frontend && npx playwright test

# バッチ（IntelliJ: BatchApplication, args: --spring.profiles.active=batch,dev --spring.batch.job.name=<job> shopNo=1）
```

## プロファイル
- `web` + `dev`: APIサーバー開発（port 8090）
- `batch` + `dev`: バッチ実行
- Next.js が `/api/*` → `localhost:8090/api/*` にリライト

## 重要な実装パターン

### バックエンド
- **Controller は薄く**: ビジネスロジックは Service 層に委譲
- **DTO分離**: Entity を直接返さない。`Response.from(entity)` ファクトリメソッド
- **バリデーション**: `@Valid` + Jakarta Bean Validation
- **論理削除**: `del_flg`（'0'=有効, '1'=削除）、`IEntity` インターフェース
- **CustomService**: `insert()`/`update()`/`delete()` で共通処理（監査フィールド、ショップ権限チェック）
- **バッチ**: `@EnableBatchProcessing` 不使用。Bean名 = ジョブ名 + "Job"。`@Value("#{jobParameters['shopNo']}")` でshopNo取得

### フロントエンド
- **ページ構成**: `app/(authenticated)/xxx/page.tsx` → `components/pages/xxx.tsx`
- **初期検索なし**: `searchParams` を `null` で初期化、`enabled: searchParams !== null`
- **admin判定**: `user.shopNo === 0` → ショップ選択を表示
- **検索プルダウン**: `SearchableSelect`（Popover + Command/cmdk）— `clearable` で必須/任意を切替
- **トースト**: sonner
- **shadcn/ui追加**: `npx shadcn@latest add <component>`

## エンティティ設計
- 複合主キー: `@Embeddable` クラス
- 共通フィールド: `company_no`, `shop_no`, `del_flg`, `add_date_time`, `add_user_no`, `modify_date_time`, `modify_user_no`
- ワークテーブル `w_*` → 本テーブル `t_*` / `m_*`

## 開発プロセス
- **増分レビュー必須**: 機能追加・バグ修正・デバッグコード投入のたびに `tsc --noEmit` + `./gradlew compileJava` + E2E (モック PASS だけでなく実バックエンド疎通を最低 1 パス) を実施。`/code-review` は常にブランチ差分全体を対象
- **デバッグ用コードはマーキング**: `detail` レスポンス、擬似 state、`null as unknown as X` などは投入時に TaskCreate で整理対象に登録、マージ前に再確認
- **JVM 再起動が必要な変更**（新 Bean / Repository method / @JsonProperty / Converter 等）を入れたらユーザーに再起動依頼を明示し、curl で疎通確認してから UI で検証

## 注意事項
- `javax.*` ではなく `jakarta.*`
- Spring Batch メタデータテーブルは 5.x スキーマ（`parameter_name` / `create_time`）
- PostgreSQL固有型: `hypersistence-utils-hibernate-63` で対応
- CORS: `localhost:5173,localhost:3000` を許可（dev）
