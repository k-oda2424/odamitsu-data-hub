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

# Review Guidelines

このリポジトリでは、AIレビュー時に以下を優先する。

## Repository Context

- 特定の技術スタックを固定前提にしない。
- 実際の言語、フレームワーク、DB、ライブラリ、実行環境は、リポジトリ内の設定ファイル、依存関係、ディレクトリ構成、既存コードから判断する。
- 不明な技術・仕様・業務ルールは断定せず、Open Questions として扱う。
- 検出した技術のバージョンで使えない機能を安易に提案しない。

## Review Priorities

1. データ破壊・データ不整合
2. 認証・認可の欠落
3. Injection / XSS / CSRF / 情報漏洩などのセキュリティリスク
4. 業務仕様・ドメインルールへの影響
5. トランザクション・排他制御・永続化処理
6. テスト不足
7. 保守性・可読性
8. パフォーマンス
9. UI / UX
10. 命名・軽微なスタイル

## Architecture Rules

- 画面、API、業務ロジック、永続化、外部連携の責務を混ぜすぎない。
- 業務ルールは追跡しやすい場所に置く。
- DB更新や外部連携ではトランザクション境界・失敗時の挙動を明確にする。
- 画面表示用のデータ構造と永続化用のデータ構造を不用意に混ぜない。
- フロントエンドでは、画面状態、APIレスポンス、フォーム入力の扱いを曖昧にしない。
- 検出した技術スタックに対して自然な設計を優先する。
- 特定の技術・バージョンで使えない機能を前提にしない。

## Severity

- P0: リリース不可。データ破壊、重大なセキュリティ、致命的障害、業務停止。
- P1: 修正必須。業務影響の大きいバグ、認証認可ミス、重大な回帰。
- P2: 修正推奨。保守性、テスト不足、性能劣化リスク。
- P3: 提案。軽微な可読性、命名、将来改善。

## Review Do Not

- 根拠のない推測を断定しない。
- 好みの問題を P1 以上にしない。
- 大量の細かい指摘で重大な問題を埋もれさせない。
- 既存仕様を無視して理想論だけで指摘しない。
- 存在しない技術スタックを前提にしない。
- 検出したバージョンで使えない機能を提案しない。
- AIレビューを最終判断にしない。最終判断は人間が行う。

## Done Means

- 主要なテストが通る。
- 変更理由が説明できる。
- 業務影響範囲が明確。
- エラー時の挙動が確認されている。
- DB変更がある場合、移行・ロールバック方針がある。
- 外部連携がある場合、失敗時・再試行・タイムアウトの扱いが明確。
