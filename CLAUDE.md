# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# 小田光データ連携基盤 (OdaMitsu Data Hub)

## 基本情報
- プロジェクト名: oda-data-hub
- システム正式名称: 小田光データ連携基盤 (OdaMitsu Data Hub / ODH)
- 開発企業: 小田光株式会社
- 技術スタック:
  - バックエンド: Java 21, Spring Boot 3.3.x, PostgreSQL 17, Gradle 8.x
  - フロントエンド: Next.js 16, React 19, TypeScript, shadcn/ui
- 主要システム連携: SMILE（基幹システム）, B-CART（ECシステム）, マネーフォワード
- 旧システム: stock-app（Spring Boot 2.1.1 / C:\project\stock-app）

## システム概要
小田光株式会社のデータ連携基盤。SMILE（基幹システム）、B-CART（ECシステム）、マネーフォワード（会計）との外部システム連携を中核とし、在庫・仕入・受注・財務データの統合管理を行う。

旧システム（stock-app）の全面刷新として構築。旧システムを運用継続しながら段階的に機能を移行する。

## アーキテクチャ

### 全体構成（フロントエンド / バックエンド分離）
```
odamitsu-data-hub/
├── backend/             # Spring Boot REST API + Batch
│   ├── src/main/java/jp/co/oda32/
│   ├── build.gradle
│   └── settings.gradle
├── frontend/            # React SPA
│   ├── src/
│   ├── package.json
│   └── vite.config.ts
└── CLAUDE.md
```

### バックエンド（Spring Boot）
- **REST API**: JSON API（`@RestController`）- フロントエンドとの通信
- **バッチアプリケーション**: プロファイル `batch` - 外部システム連携・データ処理
- **プロファイル**: `web`（API サーバー）, `batch`（バッチ処理）

### フロントエンド（React SPA）
- **ビルドツール**: Vite
- **言語**: TypeScript（strict mode）
- **UIフレームワーク**: shadcn/ui
- **ルーティング**: TanStack Router
- **テーブル**: TanStack Table
- **フォーム**: React Hook Form + Zod
- **API通信**: TanStack Query + fetch（または axios）
- **認証**: JWT or Session（Spring Security連携）

## フロントエンド技術スタック詳細

### shadcn/ui 構成
- **Radix UI** (`@radix-ui/*`): ヘッドレスUIプリミティブ
- **Tailwind CSS** + `tailwind-merge`: スタイリング
- **class-variance-authority** (`cva`): バリアント管理
- **Lucide React**: アイコン

### ディレクトリ構造（frontend）
```
frontend/src/
├── components/
│   ├── ui/               # shadcn/ui コンポーネント（Card, Button, Dialog, Select 等）
│   ├── layout/           # レイアウト（Sidebar, Header, Footer）
│   └── features/         # 業務機能コンポーネント
│       ├── dashboard/
│       ├── goods/        # 商品管理
│       ├── order/        # 注文管理
│       ├── stock/        # 在庫管理
│       ├── purchase/     # 仕入管理
│       ├── estimate/     # 見積管理
│       ├── finance/      # 財務・会計
│       ├── bcart/        # B-CART連携
│       ├── smile/        # SMILE連携
│       └── master/       # マスタ管理
├── hooks/                # カスタムフック
├── lib/                  # ユーティリティ（cn(), api client 等）
├── pages/                # ページコンポーネント（ルーティング対応）
├── types/                # 型定義（API レスポンス、ドメインモデル）
└── App.tsx
```

### shadcn/ui コンポーネント利用方針
- `components/ui/` 配下に shadcn/ui コンポーネントを配置
- `npx shadcn@latest add <component>` でコンポーネントを追加
- カスタマイズはコンポーネントファイルを直接編集
- `lib/utils.ts` に `cn()` ヘルパー関数を配置

### 主要UIコンポーネントマッピング（旧→新）
| 旧（stock-app） | 新（oda-data-hub） |
|----------------|-------------------|
| Bootstrap Table + DataTables | TanStack Table + shadcn/ui Table |
| jQuery AJAX | TanStack Query |
| Bootstrap Modal / window.open() | shadcn/ui Dialog |
| Bootstrap Form + @Validated | React Hook Form + Zod |
| Bootstrap Select + Select2 | shadcn/ui Select / Combobox |
| Bootstrap DateTimePicker | shadcn/ui DatePicker (date-fns) |
| Bootstrap Navbar + MetisMenu | shadcn/ui Sidebar + Navigation |
| FontAwesome | Lucide React |
| Chart.js / ApexCharts | Recharts（shadcn/ui Charts） |
| Thymeleaf th:if / th:each | React 条件レンダリング / map() |

## バックエンド技術スタック詳細

### フレームワーク・ライブラリ
- Java 21
- Spring Boot 3.3.x
- Spring Batch 5.x（JobBuilder / StepBuilder API）
- Spring Security 6.x（SecurityFilterChain方式）
- Spring Data JPA 3.x（Hibernate 6.x）
- Jakarta EE（jakarta.persistence, jakarta.validation, jakarta.servlet）
- Lombok 1.18.30+
- Gradle 8.x

### パッケージ構造（backend）
```
backend/src/main/java/jp/co/oda32/
├── api/                  # REST Controller（JSON API）
│   ├── auth/             # 認証API
│   ├── goods/            # 商品API
│   ├── order/            # 注文API
│   ├── stock/            # 在庫API
│   ├── purchase/         # 仕入API
│   ├── estimate/         # 見積API
│   ├── finance/          # 財務API
│   ├── bcart/            # B-CART API
│   └── master/           # マスタAPI
├── batch/                # バッチ処理
│   ├── bcart/            # B-CART連携バッチ
│   ├── finance/          # 財務バッチ
│   ├── goods/            # 商品バッチ
│   ├── purchase/         # 仕入バッチ
│   └── smile/            # SMILE連携バッチ
├── config/               # アプリケーション設定
├── domain/
│   ├── model/            # JPAエンティティ
│   ├── repository/       # データアクセス層
│   ├── service/          # ビジネスロジック
│   ├── specification/    # クエリ仕様
│   └── validation/       # カスタムバリデーション
├── dto/                  # APIリクエスト/レスポンスDTO
├── exception/            # 例外ハンドリング（@RestControllerAdvice）
└── util/                 # ユーティリティ
```

### データベース
- PostgreSQL 17（旧システムは9.6、データ移行は`pg_dump`→`pg_restore`で実施）
- Hibernate Dialect: 明示指定不要（Hibernate 6.x 自動検出）
- 接続: Spring Data JPA / Hibernate 6.x
- JDBC Driver: `org.postgresql:postgresql:42.7.x`
- 認証: scram-sha-256（PG14以降デフォルト）
- PostgreSQL固有型: jsonb, text[], bigint[] は `hypersistence-utils-hibernate-63` で対応

### REST API 設計方針
- ベースパス: `/api/v1/`
- JSON レスポンス（Jackson）
- ページネーション: Spring Data `Pageable` → JSON レスポンス
- エラーハンドリング: `@RestControllerAdvice` で統一的なエラーレスポンス
- 認証: Spring Security + JWT or Session
- CORS: フロントエンド開発サーバー（localhost:5173）を許可

### 旧システムからの主要変更点
| 項目 | 旧（stock-app） | 新（oda-data-hub） |
|------|----------------|-------------------|
| フロントエンド | Thymeleaf + jQuery + Bootstrap 4 | React + TypeScript + shadcn/ui |
| API層 | @Controller（テンプレート返却）| @RestController（JSON API）|
| Spring Boot | 2.1.1.RELEASE | 3.3.x |
| Java | 指定なし | 21 |
| 名前空間 | javax.* | jakarta.* |
| Spring Batch | JobBuilderFactory / StepBuilderFactory | JobBuilder / StepBuilder + JobRepository |
| Spring Security | WebSecurityConfigurerAdapter | @Bean SecurityFilterChain |
| Spring Data JPA | getOne() | getReferenceById() / findById() |
| Hibernate | 5.x (PostgreSQL95Dialect) | 6.x (自動検出、明示指定不要) |
| PostgreSQL | 9.6 | 18 |
| JDBC Driver | 42.2.27 | 42.7.x |
| hibernate-types | hibernate-types-52 | hypersistence-utils-hibernate-63 |
| Gradle | 5.6.4 | 8.x |

## 開発コマンド

### バックエンド
```bash
# ビルド
cd backend && ./gradlew build

# API サーバー起動（開発）
cd backend && ./gradlew bootRun --args='--spring.profiles.active=web,dev'

# バッチ実行
java -jar backend/build/libs/oda-data-hub.jar --spring.profiles.active=batch,dev --spring.batch.job.name=<ジョブ名>

# テスト
cd backend && ./gradlew test
```

### フロントエンド
```bash
# 依存インストール
cd frontend && npm install

# 開発サーバー起動（localhost:5173）
cd frontend && npm run dev

# ビルド
cd frontend && npm run build

# 型チェック
cd frontend && npx tsc --noEmit

# Lint
cd frontend && npm run lint
```

### 主要バッチジョブ（旧システムから移行）
- `accountsPayableAggregation`: 買掛金集計
- `accountsPayableVerification`: 買掛金検証
- `purchaseJournalIntegration`: 買掛仕入CSV出力
- `purchaseFileImport`: 仕入ファイル取込
- `smilePaymentImport`: SMILE支払情報取込

## 設定・環境

### プロファイル
- `dev`: 開発環境
- `prod`: 本番環境
- `batch`: バッチ処理用
- `web`: API サーバー用

### バックエンド設定ファイル
- `application.yml`: 共通設定
- `application-dev.yml`: 開発環境設定（CORS: localhost:5173 許可）
- `application-prod.yml`: 本番環境設定
- `application-batch.yml`: バッチ専用設定
- `application-web.yml`: API サーバー専用設定

## 重要な実装パターン

### エンティティ設計
- 複合主キーは`@Embeddable`クラスを使用
- 論理削除: `del_flg`カラム（'0'=有効, '1'=削除）
- 共通フィールド: 会社番号（`company_no`）、店舗番号（`shop_no`）

### REST API パターン
- Controller は薄く保ち、ビジネスロジックは Service 層に委譲
- リクエスト/レスポンスは DTO で定義（Entity を直接返さない）
- バリデーションは `@Valid` + Jakarta Bean Validation
- ページネーション: `Pageable` パラメータ → `Page<DTO>` レスポンス

### バッチ処理パターン（Spring Batch 5.x）
- `JobBuilder` + `JobRepository` を直接注入（`@EnableBatchProcessing` は使用しない）
- ジョブ名 + "Job"でBean名規則（例：`purchaseFileImportJob`）
- Tasklet vs Chunk処理の使い分け
- ワークテーブル（`w_*`）→本テーブル（`t_*`）のUPSERT方式

### Spring Security（Spring Security 6.x）
- `@Bean SecurityFilterChain` でHTTPセキュリティ設定
- `requestMatchers()` でURLパターンマッチ
- `authorizeHttpRequests()` で認可設定
- CORS 設定: 開発時は localhost:5173 を許可

### フロントエンド実装パターン
- ページ単位でコンポーネントを分割（pages/ → features/）
- API通信は TanStack Query でキャッシュ・再取得管理
- フォームは React Hook Form + Zod でバリデーション
- テーブルは TanStack Table + shadcn/ui Table でソート・フィルタ・ページネーション
- モーダル（マスタ選択等）は shadcn/ui Dialog
- トースト通知は shadcn/ui Toast（sonner）
- 検索可能プルダウン（Select2相当）は `SearchableSelect`（Popover + Command/cmdk）
  - 仕入先・メーカー等のマスタ選択に使用
  - `clearable={false}` で必須フィールド（再クリックで解除しない）
  - `clearable={true}`（デフォルト）で検索フォーム用（クリアボタン付き）

## 外部システム連携

### SMILE連携（基幹システム）
- 支払情報: CSVファイル経由でw_smile_payment→t_smile_paymentへUPSERT
- 受注データ: w_smile_order_output_file経由

### B-CART連携（ECシステム）
- REST API経由
- 商品・注文・在庫データの双方向同期
- 出荷ステータス管理: 未発送、発送指示、発送済、対象外（EXCLUDED）

### マネーフォワード連携（会計）
- CSV出力による仕訳データ連携
- 買掛金・売掛金データの定期連携

## 画面一覧（旧システムから移行）

### ダッシュボード
- ダッシュボード（チャート・集計表示）

### 商品管理（goods）
- 商品マスタ一覧 / 登録 / 編集
- 販売商品一覧 / 登録 / 編集
- 取引先商品一覧 / 編集

### 注文管理（order）
- 受注一覧（実装済: 注文明細レベル表示、11カラム、ステータスBadge、日時範囲検索、初期検索なし）
- 取引先商品発注入力

### 在庫管理（stock）
- 在庫一覧
- 在庫ログ
- 在庫登録

### 仕入管理（purchase）
- 仕入入力 / 確認 / 完了（多段階フォーム）
- 仕入一覧
- 発注入力 / 一覧 / 確認 / 完了
- 仕入価格一覧（実装済: 検索フォーム、行クリックで変更予定Dialog入力）
- 仕入価格変更一覧（実装済: 検索フォーム、反映ステータスBadge）
- 仕入価格変更一括入力（実装済: ヘッダー＋明細の動的行追加、商品コードでの価格自動取得）
- AI見積取込（実装済: Claude Code PDF解析→ステージング→仕入先突合→商品突合/新規作成→仕入価格変更予定反映）

### 見積管理（estimate）
- 見積一覧（実装済: 検索フォーム、ステータスバッジ、admin店舗選択、初期検索なし）
- 見積入力 / 確認
- 見積明細一覧

### 財務・会計（finance）
- 買掛金一覧 / 詳細
- 請求書一覧 / 詳細

### B-CART連携（bcart）
- 出荷情報入力

### マスタ管理（master）
- メーカーマスタ / 登録 / 編集
- 倉庫マスタ / 登録 / 編集
- 仕入先店舗マッピング一覧 / インポート

### 認証
- ログイン
- ユーザー一覧 / 登録 / 編集

## 注意事項

### バッチ開発
- `@EnableBatchProcessing` は使用しない（Spring Boot 3.x で自動設定）
- 同一パラメータでの重複実行不可（Spring Batch仕様）
- 大量データ処理時はチャンクサイズ調整必須
- ワークテーブルのTRUNCATE処理はトランザクション管理に注意

### フロントエンド開発
- `components/ui/` は shadcn/ui のコンポーネント配置先（直接編集可）
- TypeScript strict mode を遵守
- コンポーネントは関数コンポーネント + hooks パターン
- スタイリングは Tailwind CSS クラスで統一（CSS ファイル原則不要）

### 旧システムとの並行運用
- 旧システム（stock-app）は C:\project\stock-app で運用継続
- 同一データベースを参照（移行期間中）
- 機能単位で段階的に新システムへ切り替え
