# システム概要仕様書

**プロジェクト名**: stock-app（小田光社内システム）
**ドキュメントバージョン**: 1.0
**作成日**: 2026-02-23
**対象リポジトリ**: C:\project\stock-app

---

## 目次

1. [システム概要](#1-システム概要)
2. [技術スタック](#2-技術スタック)
3. [アーキテクチャ概要](#3-アーキテクチャ概要)
4. [外部システム連携概要](#4-外部システム連携概要)
5. [プロファイル構成と設定](#5-プロファイル構成と設定)
6. [主要ドメイン一覧と概要](#6-主要ドメイン一覧と概要)
7. [ディレクトリ構成](#7-ディレクトリ構成)

---

## 1. システム概要

### 1.1 目的・背景

stock-app は小田光株式会社（`jp.co.oda32`）の社内システムである。もともと在庫管理システムとして開発が始まり、現在は以下の機能群を持つ複合システムへと発展している。

- 在庫管理・発注管理
- 売上・仕入帳管理
- 外部システム（SMILE、B-CART、マネーフォワード）との定期連携
- 財務・会計仕訳データの自動生成

プロジェクトの `group` は `com.odamitsu`、アーティファクト名は `stock-app`、バージョンは `0.0.1-SNAPSHOT` として `build.gradle` に定義されている。

### 1.2 対象ユーザー

システムには 3 種類のユーザー種別が存在する（`MLoginUser` エンティティの `company_type` フィールドおよび `CompanyType` 列挙型で管理）。

| ユーザー種別 | 説明 | `shop_no` |
|---|---|---|
| ADMIN（管理者） | 全営業所データにアクセス可能 | 0（固定） |
| SHOP（営業所ユーザー） | 自営業所のデータのみ参照可能 | 所属 `company` の `shop_no` |
| PARTNER（取引先ユーザー） | 取引先の所属ショップのデータのみ参照可能 | 取引先 `partner` の `shop_no` |

### 1.3 営業所構成

`OfficeShopNo` 列挙型で定義されている営業所コードは以下の通り。

| 定数名 | 値 | 説明 |
|---|---|---|
| `ADMIN` | 0 | 管理者 |
| `DAIICHI` | 1 | 第1事業部 |
| `DAINI` | 2 | 第2事業部 |
| `CLEAN_LABO` | 3 | クリーンラボ事業部 |
| `INNER_PURCHASE` | 1 | 社内仕入 |
| `INNER_ORDER` | 1 | 社内売上 |
| `B_CART_ORDER` | 1 | BCart売上 |

---

## 2. 技術スタック

### 2.1 言語・フレームワーク

| 項目 | バージョン | 備考 |
|---|---|---|
| Java | 未指定（Spring Boot 2.1.1 は Java 8〜11 を想定） | `build.gradle` に `sourceCompatibility` 明示なし |
| Spring Boot | **2.1.1.RELEASE** | `build.gradle` の `springBootVersion` |
| Spring Batch | Spring Boot 2.1.1 管理バージョン | `spring-boot-starter-batch` 依存 |
| Spring Security | Spring Boot 2.1.1 管理バージョン | `spring-boot-starter-security` 依存 |
| Spring Data JPA | Spring Boot 2.1.1 管理バージョン | `spring-boot-starter-data-jpa` 依存 |
| Spring AOP | Spring Boot 2.1.1 管理バージョン | `spring-boot-starter-aop` 依存 |
| Thymeleaf | **2.1.1.RELEASE**（starter 経由） | `spring-boot-starter-thymeleaf:2.1.1.RELEASE` |
| Kotlin stdlib | **1.6.10** | OkHttp 依存のために追加 |

### 2.2 データベース

| 項目 | バージョン |
|---|---|
| PostgreSQL | **9.6**（CLAUDE.md 記載） |
| PostgreSQL JDBC Driver | **42.2.27** |
| Hibernate Dialect | `PostgreSQL95Dialect`（`application-batch.yml`） |
| Hibernate Types | **2.14.0**（`com.vladmihalcea:hibernate-types-52`） |
| 接続プール | HikariCP（Spring Boot 標準）、バッチは `auto-commit: false` |

### 2.3 ビルドツール・その他ライブラリ

| ライブラリ | バージョン | 用途 |
|---|---|---|
| Gradle | Spring Boot Plugin 2.1.1 使用 | ビルド管理 |
| Lombok | **1.18.4** | ボイラープレート削減（`@Data`、`@Builder`、`@RequiredArgsConstructor` 等） |
| Log4j2 | **2.20.0** | ロギング（`log4j-core`、`log4j-api`） |
| OkHttp3 | **4.9.3** | B-CART REST API 呼び出し |
| Gson | **2.8.9** | JSON シリアライズ/デシリアライズ |
| Jackson Databind | **2.13.0** | JSON 処理 |
| Jackson YAML | **2.15.2** | YAML 処理（`jackson-dataformat-yaml`） |
| Apache Commons CSV | **1.8** | CSV ファイル読み書き |
| Apache Commons Lang3 | **3.12.0** | 文字列・オブジェクト操作ユーティリティ |
| Apache POI OOXML | **5.2.3** | Excel ファイル生成 |
| org.json | **20230618** | JSON 処理補助 |
| Asana SDK | **1.0.0** | Asana タスク連携（`com.asana:asana`） |
| Thymeleaf Layout Dialect | Spring Boot 管理 | レイアウトテンプレート |
| Thymeleaf Spring Security | Spring Boot 管理 | テンプレート内セキュリティ表現 |
| Thymeleaf Java8 Time | **3.0.0.RELEASE** | Java 8 日時型の Thymeleaf サポート |
| jQuery | **3.6.2**（WebJar） | フロントエンド JavaScript |
| Bootstrap | **4.1.2**（WebJar） | フロントエンド CSS フレームワーク |
| spring-boot-devtools | Spring Boot 管理 | 開発中のホットリロード |
| spring-boot-properties-migrator | Spring Boot 管理（runtimeOnly） | プロパティ移行補助 |

### 2.4 ビルド成果物

`build.gradle` の `bootJar` 設定により、実行可能 JAR として `app.jar` が生成される。メインクラスは `jp.co.oda32.WebApplication` が指定されている（バッチは実行時引数でプロファイルを切り替える）。

```groovy
bootJar {
    launchScript()
    archiveFileName = 'app.jar'
    mainClassName = 'jp.co.oda32.WebApplication'
}
```

---

## 3. アーキテクチャ概要

### 3.1 アプリケーション二分割構成

このシステムは **1 つの JAR** で Web アプリケーションとバッチアプリケーションを共存させ、起動時の Spring Profile によって動作モードを切り替える設計となっている。

#### Web アプリケーション

- エントリポイント: `jp.co.oda32.WebApplication`
- アノテーション: `@SpringBootApplication`、`@Profile("web")`
- 起動コマンド例:

```bash
java -jar app.jar --spring.profiles.active=web,dev
```

#### バッチアプリケーション

- エントリポイント: `jp.co.oda32.BatchApplication`
- アノテーション: `@SpringBootApplication`、`@Profile("batch")`、`@EnableTransactionManagement`
- Web コンテキストを無効化して起動（`WebApplicationType.NONE`）
- コマンドライン引数 `--spring.batch.job.name=<ジョブ名>` でジョブを一つ指定して実行
- Bean 名規則: ジョブ名 + `"Job"` の文字列で Spring コンテキストから Job Bean を検索
- 起動コマンド例:

```bash
java -jar app.jar --spring.profiles.active=batch,dev --spring.batch.job.name=purchaseFileImport
```

`BatchApplication` は古い実行クラス（`AccountsPayableSummaryBatch`、`AccountsReceivableSummaryBatch`、`PurchaseJournalIntegrationBatch`）を `@ComponentScan` の `excludeFilters` で除外している。

### 3.2 レイヤー構成（DDD 指向）

```
jp.co.oda32/
├── app/                  (Presentation Layer - Web コントローラー・フォーム)
│   ├── *Controller.java  (@Controller - Thymeleaf テンプレートへの連携)
│   └── *Form.java        (フォームバインディングオブジェクト)
├── batch/                (Batch Layer - Spring Batch Tasklet/Chunk処理)
│   └── */config/         (Job・Step 設定クラス)
├── domain/
│   ├── model/            (Domain Model Layer - JPA @Entity)
│   ├── repository/       (Repository Layer - Spring Data JPA インターフェース)
│   ├── service/          (Service Layer - ビジネスロジック)
│   ├── specification/    (Specification Layer - JPA Criteria クエリ仕様)
│   └── validation/       (カスタムバリデーション)
├── config/               (設定クラス群)
├── aop/                  (横断的関心事 - AOP)
├── annotation/           (カスタムアノテーション)
├── constant/             (定数・列挙型)
├── filter/               (Servlet フィルター)
├── exception/            (カスタム例外)
├── util/                 (ユーティリティクラス)
└── thymeleaf/            (Thymeleaf カスタム拡張)
```

### 3.3 セキュリティ設計

`SecurityConfig`（`jp.co.oda32.SecurityConfig`）が Spring Security の設定を担う。

- 認証方式: フォーム認証（`/loginForm` → `/login`）
- パスワードハッシュ: `BCryptPasswordEncoder`
- ユーザー詳細サービス: `LoginUserService`
- セッションタイムアウト: **3600 秒（1 時間）**（`application.yml`）
- 同時セッション数: **1**（新しいログインで古いセッションを無効化）
- セッション固定攻撃対策: `changeSessionId()`
- セッションタイムアウト検知: `SessionTimeoutFilter`（`UsernamePasswordAuthenticationFilter` の前に挿入）
- タイムアウト後リダイレクト: `SessionTimeoutRedirectStrategy` でタイムアウト前にアクセスしていた URL を `LAST_ACCESSED_URL_BEFORE_TIMEOUT` セッション属性に保存し、ログイン成功後に `CustomAuthenticationSuccessHandler` が復元リダイレクトを行う
- CSRF 保護: Spring Security デフォルト有効
- 静的リソースはセキュリティ除外: `/favicon.ico`、`/css/**`、`/js/**`、`/images/**`、`/fonts/**`

### 3.4 データアクセス設計

#### エンティティ命名規則

| プレフィックス | 意味 |
|---|---|
| `m_` | マスタテーブル (`M` prefix クラス) |
| `t_` | トランザクションテーブル (`T` prefix クラス) |
| `w_` | ワークテーブル (`W` prefix クラス) - 一時データ格納用 |
| `v_` | ビュー (`V` prefix クラス) |

#### 共通設計パターン

- **論理削除**: `del_flg` カラム（`'0'` = 有効、`'1'` = 削除）
- **共通フィールド**: `company_no`（会社番号）、`shop_no`（店舗番号）
- **複合主キー**: `@Embeddable` クラスで定義（例: `TAccountsPayableSummaryPK`、`TStockPK`）
- **共通インターフェース**: `IEntity`（`getShopNo()` メソッドを持つ）、`ICompanyEntity`
- **基底クラス**: `AbstractCompanyEntity`（`company_no` を持つエンティティの親クラス）

#### AOP によるアクセス制御

`ShopCheckAop`（`jp.co.oda32.aop.ShopCheckAop`）が Service 層の `get*`・`find*` メソッドをインターセプトし、ログインユーザーの `shop_no` に一致するエンティティのみを返すようフィルタリングを行う。管理者（`shop_no = 0`）は全データにアクセス可能。

### 3.5 バッチ処理設計

Spring Batch の Job/Step/Tasklet/Chunk モデルを採用。

#### Chunk 処理（大量データ向け）

Reader → Processor → Writer のパイプライン構成。チャンクサイズの例:
- 仕入ファイル取込: `chunk(500)`
- SMILE 注文ファイル取込: `chunk(500)`
- 商品ファイル取込: `chunk(10)`
- SMILE 支払情報取込: `chunk(100)`

#### Tasklet 処理（単発処理向け）

ワークテーブルの TRUNCATE、データ集計、ステータス更新など単発処理に使用。

#### ワークテーブル UPSERT パターン

外部システムデータは以下のフローで取り込む。
1. ワークテーブル（`w_*`）を TRUNCATE
2. 外部ファイル/API からデータをワークテーブルに投入
3. ワークテーブルから本テーブル（`t_*`）へ UPSERT

TRUNCATE は DDL のため `JdbcTemplate` で直接実行し、JPA トランザクション外で処理する。

#### ジョブ実行時の重複防止

Spring Batch のジョブパラメータに `time=System.currentTimeMillis()` を自動付与することで、同一ジョブを繰り返し実行できる。

### 3.6 バリデーション設定

`WebConfig`（`jp.co.oda32.WebConfig`）で国際化メッセージを設定。
- メッセージファイル: `classpath:config/ValidationMessages_ja_JP.properties`
- エンコード: UTF-8

---

## 4. 外部システム連携概要

### 4.1 SMILE（基幹システム）連携

SMILE は社内の基幹 ERP システム。連携はすべて **CSV ファイル経由**。

#### 4.1.1 SMILE 受注データ取込

| 項目 | 内容 |
|---|---|
| ジョブ名 | `smileOrderFileImport` |
| 設定クラス | `SmileOrderFileImportConfig` |
| 入力ファイル | `input/smile_order_import.csv` |
| 処理フロー | CSV 読込 → `WSmileOrderOutputFile` ワークテーブル投入 → 注文・在庫更新 |
| 関連 Step | `smileOrderFileImportStep`（chunk:500） → `stockAllocateStep` → `orderStatusUpdateStep` → `shopAppropriateStockCalculateStep` → `vSalesMonthlySummaryRefreshStep` → `fileMoveStep` |
| 主要クラス | `SmileOrderFileReader`、`SmileOrderFileProcessor`、`SmileOrderFileWriter` |

#### 4.1.2 SMILE 仕入データ取込

| 項目 | 内容 |
|---|---|
| ジョブ名 | `purchaseFileImport` |
| 設定クラス | `PurchaseFileImportConfig` |
| 処理フロー | `w_smile_purchase_output_file` TRUNCATE → CSV 読込 → ワークテーブル投入 → 本テーブル UPSERT → 発注リンク → 仕入単価作成 |
| 関連 Step | `wSmilePurchaseFileTruncateStep` → `purchaseFileImportStep`（chunk:500） → `smilePurchaseImportStep` → `purchaseLinkSendOrderStep` → `purchasePriceCreateStep` |
| 主要クラス | `PurchaseFileReader`、`PurchaseFileProcessor`、`PurchaseFileWriter`、`SmilePurchaseImportTasklet` |

#### 4.1.3 SMILE 支払情報取込

| 項目 | 内容 |
|---|---|
| 旧ジョブ | `smilePaymentImport`（`SmilePaymentImportBatch` クラス - 独立 Spring Boot アプリ方式） |
| 新ジョブ | `accountsPayableVerification`（`AccountsPayableVerificationConfig` 設定） |
| 処理フロー（新） | `w_smile_payment` TRUNCATE → CSV 読込 → ワークテーブル投入（chunk:100、スキップ上限:10000） → 照合検証 → 検証レポート出力 |
| 関連テーブル | `w_smile_payment`（ワーク）→ `t_smile_payment`（本テーブル） |
| 主要クラス | `SmilePaymentFileReader`、`SmilePaymentProcessor`、`SmilePaymentWriter`、`AccountsPayableVerificationTasklet`、`AccountsPayableVerificationReportTasklet` |

#### 4.1.4 SMILE 取引先・商品連携

- 取引先ファイル: `PartnerFile` / `PartnerFileReader` / `PartnerFileProcessor` / `PartnerFileWriter`
- 商品ファイル: `GoodsFile` / `GoodsFileReader`（ジョブ: `goodsFileImport`、chunk:10）
- パートナーファイル出力: `SmilePartnerFileOutPutTasklet`
- 注文ファイル出力: `SmileOrderFileOutPutTasklet`

### 4.2 B-CART（EC システム）連携

B-CART は EC プラットフォーム。連携は **REST API 経由**（OkHttp3 + Gson を使用）。

#### 4.2.1 主要 Tasklet 一覧

| Tasklet クラス | 処理内容 |
|---|---|
| `BCartProductsImportTasklet` | B-CART から商品データを取得・更新 |
| `BCartProductSetsImportTasklet` | B-CART からセット商品データを取得・更新 |
| `BCartMemberImportTasklet` | B-CART から会員データを取得 |
| `BCartMemberDeliveryImportTasklet` | B-CART から配送先情報を取得 |
| `BCartOrderRegisterTasklet` | 注文を B-CART に登録 |
| `BCartOrderConvertSmileOrderFileTasklet` | B-CART 注文を SMILE 注文ファイル形式に変換 |
| `BCartGoodsPriceUpdateTasklet` | B-CART 上の商品価格を更新 |
| `BCartGoodsPriceTableUpdateTasklet` | 価格テーブルを更新 |
| `BCartLogisticsCsvOutputTasklet` | 出荷情報 CSV を出力 |
| `RegisterBCartMemberTasklet` | 会員を B-CART に登録 |
| `SmileDestinationFileOutPutTasklet` | SMILE 向け配送先ファイルを出力 |

#### 4.2.2 出荷ステータス管理

B-CART の出荷ステータスは以下の 4 種類。

| ステータス | 説明 |
|---|---|
| 未発送 | 出荷前 |
| 発送指示 | 出荷指示済み |
| 発送済 | 出荷完了 |
| 対象外（EXCLUDED） | 一覧から除外（不要な項目をフィルタリングするために使用） |

#### 4.2.3 Web 画面連携

`BCartShippingInputController` / `BCartShippingSearchForm` で出荷情報入力画面を提供。

#### 4.2.4 B-CART API 設定

API エンドポイント例: `https://oda32.biz/api/deliveryRegister`（`application.yml` の `delivery.register.api.url`）

### 4.3 マネーフォワード連携

マネーフォワードは会計クラウドサービス。連携は **CSV 出力**（マネーフォワード仕訳インポート形式）。

#### 4.3.1 仕入帳（買掛金）CSV 出力

| 項目 | 内容 |
|---|---|
| ジョブ名 | `purchaseJournalIntegration` |
| 設定クラス | `PurchaseJournalIntegrationConfig` |
| Tasklet | `AccountsPayableToPurchaseJournalTasklet` |
| 出力クラス | `MFJournalCsv`（マネーフォワード仕訳 CSV 形式） |
| 処理内容 | 買掛金サマリー（`t_accounts_payable_summary`）から仕訳データを CSV 生成 |

#### 4.3.2 売上帳（売掛金）CSV 出力

| 項目 | 内容 |
|---|---|
| ジョブ名 | `salesJournalIntegration` |
| 設定クラス | `SalesJournalIntegrationConfig` |
| Tasklet | `AccountsReceivableToSalesJournalTasklet` |
| 処理内容 | 売掛金サマリー（`t_accounts_receivable_summary`）から仕訳データを CSV 生成 |

#### 4.3.3 買掛金集計ジョブ

| ジョブ名 | 設定クラス | 処理内容 |
|---|---|---|
| `accountsPayableAggregation` | `AccountsPayableAggregationConfig` | 買掛金サマリーテーブルを初期化後、仕入データから買掛金を集計 |

集計処理の Step 構成:
1. `accountsPayableSummaryInitStep`（`AccountsPayableSummaryInitTasklet`）- テーブル初期化
2. `accountsPayableAggregationStep`（`AccountsPayableAggregationTasklet`）- 集計実行

設定値: `batch.accounts-receivable.invoice-amount-tolerance=5`（請求書金額との許容差額: 5 円）

### 4.4 Asana 連携

Asana（プロジェクト管理ツール）へのタスク追加バッチが存在する。

| クラス | 説明 |
|---|---|
| `AsanaTaskAddBatch` | Asana タスク追加バッチのエントリポイント |
| `AsanaTaskAddTasklet` | Asana API でタスクを登録する Tasklet |
| `AsanaTask` | タスク情報モデル |

ライブラリ: `com.asana:asana:1.0.0`

---

## 5. プロファイル構成と設定

### 5.1 プロファイル一覧

| プロファイル名 | 設定ファイル | 用途 |
|---|---|---|
| （デフォルト） | `application.yml` | 全環境共通設定 |
| `dev` | `application-dev.yml` | 開発環境固有設定 |
| `prod` | `application-prod.yml` | 本番環境固有設定 |
| `batch` | `application-batch.yml` | バッチアプリ固有設定 |
| `web` | `application-web.yml` | Web アプリ固有設定 |

### 5.2 application.yml（共通設定）

```yaml
spring:
  profiles:
    active: dev  # デフォルトプロファイル
  main:
    allow-bean-definition-overriding: true
  jpa:
    properties:
      hibernate:
        jdbc:
          lob:
            non_contextual_creation: true
  session:
    timeout: 3600  # 1時間

server:
  servlet:
    session:
      timeout: 3600s  # 1時間

spring.thymeleaf.cache: false
delivery.register.api.url: https://oda32.biz/api/deliveryRegister
```

### 5.3 application-dev.yml（開発環境）

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:55543/oda32db
    username: oda32
    password: oda32
    driverClassName: org.postgresql.Driver
logging:
  level:
    org.hibernate.SQL: OFF
    org.hibernate.type.descriptor.sql.BasicBinder: OFF
```

ローカル開発は PostgreSQL をポート `55543` で接続（Docker ポートフォワーディングが前提）。

### 5.4 application-prod.yml（本番環境）

```yaml
spring:
  datasource:
    url: jdbc:postgresql://oda32-postgres:5432/oda32db
    username: oda32
    password: oda32
    driverClassName: org.postgresql.Driver
  jpa:
    open-in-view: true
logging:
  level:
    org.hibernate.SQL: info
```

本番環境は Docker コンテナ名 `oda32-postgres` で接続。環境変数でオーバーライド可能。

### 5.5 application-batch.yml（バッチ設定）

```yaml
spring:
  batch:
    job:
      enabled: false  # 自動実行を無効化（BatchApplicationが明示的に起動）
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQL95Dialect
  datasource:
    hikari:
      auto-commit: false

batch:
  accounts-receivable:
    invoice-amount-tolerance: 5  # 請求書金額との許容差額（円）
```

### 5.6 application-web.yml（Web 設定）

```yaml
spring:
  batch:
    job:
      enabled: false
```

Web アプリ起動時にバッチジョブが自動実行されないよう無効化。

---

## 6. 主要ドメイン一覧と概要

### 6.1 master（マスタ管理）

会社・ユーザー・取引先・仕入先・倉庫などの基本マスタを管理。

| エンティティ | テーブル | 概要 |
|---|---|---|
| `MLoginUser` | `m_login_user` | ログインユーザー（ID/パスワード/会社番号/種別）|
| `MCompany` | `m_company` | 会社マスタ（`shop_no` を持つ）|
| `MPartner` | `m_partner` | 取引先マスタ（`shop_no` を持つ）|
| `MSupplier` | `m_supplier` | 仕入先マスタ |
| `MSupplierShopMapping` | `m_supplier_shop_mapping` | 仕入先と営業所のマッピング |
| `MPaymentSupplier` | `m_payment_supplier` | 支払先マスタ |
| `MPartnerCategory` | `m_partner_category` | 取引先カテゴリーマスタ |
| `MWarehouse` | `m_warehouse` | 倉庫マスタ |
| `MDeliveryPerson` | `m_delivery_person` | 配達員マスタ（複合PK: `MDeliveryPersonPK`） |
| `MMaker` | `m_maker` | メーカーマスタ |
| `MShop` | `m_shop` | 店舗マスタ |
| `MShopLinkedFile` | `m_shop_linked_file` | 店舗連携ファイル設定 |
| `MTaxRate` | `m_tax_rate` | 税率マスタ |
| `MAsana` | `m_asana` | Asana 連携設定マスタ |
| `MSmartMat` | `m_smart_mat` | SmartMat 連携設定マスタ |
| `WSmilePartner` | `w_smile_partner` | SMILE 取引先ワークテーブル（複合PK: `WSmilePartnerPK`） |

### 6.2 goods（商品管理）

商品マスタ・売価・仕入価格を管理。

| エンティティ | テーブル | 概要 |
|---|---|---|
| `MGoods` | `m_goods` | 商品マスタ |
| `MGoodsUnit` | `m_goods_unit` | 商品単位マスタ |
| `MSalesGoods` | `m_sales_goods` | 売価マスタ（複合PK: `MSalesGoodsPK`） |
| `MPartnerGoods` | `m_partner_goods` | 取引先別商品マスタ（複合PK: `MPartnerGoodsPK`） |
| `MPartnerGoodsPriceChangePlan` | - | 取引先商品価格変更計画 |
| `WSalesGoods` | `w_sales_goods` | 売価ワークテーブル（複合PK: `WSalesGoodsPK`） |

Batch: `goodsFileImport`（`GoodsFileImportConfig`）で商品ファイル CSV を取込。

### 6.3 stock（在庫管理）

在庫の入出庫・適正在庫を管理。もともとのシステムの中核機能。

| エンティティ | テーブル | 概要 |
|---|---|---|
| `TStock` | `t_stock` | 在庫テーブル（複合PK: `TStockPK`） |
| `TStockLog` | `t_stock_log` | 在庫変動ログ |
| `TShopAppropriateStock` | `t_shop_appropriate_stock` | 店舗別適正在庫（複合PK: `TShopAppropriateStockPK`） |
| `TWarehouseAppropriateStock` | `t_warehouse_appropriate_stock` | 倉庫別適正在庫（複合PK: `TWarehouseAppropriateStockPK`） |

Web 画面: `TStockListController`、`TStockCreateController`、`TStockLogController`

### 6.4 order（注文・配送管理）

受注から配送までを管理。

| エンティティ | テーブル | 概要 |
|---|---|---|
| `TOrder` | `t_order` | 受注ヘッダー |
| `TOrderDetail` | `t_order_detail` | 受注明細（複合PK: `TOrderDetailPK`） |
| `TDelivery` | `t_delivery` | 配送ヘッダー |
| `TDeliveryDetail` | `t_delivery_detail` | 配送明細（複合PK: `TDeliveryDetailPK`） |
| `TReturn` | `t_return` | 返品ヘッダー |
| `TReturnDetail` | `t_return_detail` | 返品明細（複合PK: `TReturnDetailPK`） |
| `MDeliveryDestination` | `m_delivery_destination` | 配送先マスタ |
| `VSalesMonthlySummary` | `v_sales_monthly_summary` | 月別売上サマリービュー（複合PK: `VSalesMonthlySummaryPK`） |

Batch: `StockAllocateTasklet`（在庫引当）、`OrderStatusUpdateTasklet`（受注ステータス更新）

### 6.5 purchase（仕入管理）

仕入・発注を管理。

| エンティティ | テーブル | 概要 |
|---|---|---|
| `TPurchase` | `t_purchase` | 仕入ヘッダー |
| `TPurchaseDetail` | `t_purchase_detail` | 仕入明細（複合PK: `TPurchaseDetailPK`） |
| `TSendOrder` | `t_send_order` | 発注ヘッダー |
| `TSendOrderDetail` | `t_send_order_detail` | 発注明細（複合PK: `TSendOrderDetailPK`） |
| `MPurchasePrice` | `m_purchase_price` | 仕入単価マスタ |
| `MPurchasePriceChangePlan` | - | 仕入単価変更計画 |
| `MPurchasePriceLog` | - | 仕入単価変更ログ |

Web 画面: `PurchaseListController`、`PurchaseInputController`、`SendOrderListController` 等

### 6.6 estimate（見積管理）

見積作成・確認を管理。

| エンティティ | テーブル | 概要 |
|---|---|---|
| `TEstimate` | `t_estimate` | 見積ヘッダー |
| `TEstimateDetail` | `t_estimate_detail` | 見積明細（複合PK: `TEstimateDetailPK`） |
| `VEstimateGoods` | `v_estimate_goods` | 見積商品ビュー（複合PK: `VEstimateGoodsPK`） |
| `VEstimateGoodsSpecial` | `v_estimate_goods_special` | 特価見積商品ビュー（複合PK: `VEstimateGoodsSpecialPK`） |

### 6.7 finance（財務・会計連携）

マネーフォワード仕訳連携・買掛金・売掛金・請求書管理。

| エンティティ | テーブル | 概要 |
|---|---|---|
| `TAccountsPayableSummary` | `t_accounts_payable_summary` | 買掛金サマリー（複合PK: `TAccountsPayableSummaryPK`） |
| `TAccountsReceivableSummary` | `t_accounts_receivable_summary` | 売掛金サマリー（複合PK: `TAccountsReceivableSummaryPK`） |
| `TInvoice` | `t_invoice` | 請求書 |
| `MfAccountMaster` | `mf_account_master` | マネーフォワード勘定科目マスタ |
| `MMfSubAccount` | `m_mf_sub_account` | マネーフォワード補助科目マスタ |

Batch Service: `AccountsPayableSummaryCalculator`、`SmilePaymentVerifier`、`TaxCalculationHelper`

### 6.8 bcart（B-CART EC 連携）

B-CART EC システムとのデータ同期を管理。

| エンティティ | テーブル | 概要 |
|---|---|---|
| `BCartProducts` | `bcart_products` | B-CART 商品情報 |
| `BCartProductSets` | `bcart_product_sets` | B-CART セット商品情報 |
| `BCartOrder` | `bcart_order` | B-CART 受注情報 |
| `BCartOrderProduct` | `bcart_order_product` | B-CART 受注明細（複合PK: `BCartOrderProductPK`） |
| `BCartMember` | `bcart_member` | B-CART 会員情報 |
| `BCartMemberOtherAddresses` | - | B-CART 会員追加住所 |
| `BCartLogistics` | - | B-CART 物流情報 |
| `BCartVolumeDiscount` | - | 数量割引設定 |
| `BCartGroupPrice` | - | グループ価格（複合PK: `BCartGroupPricePK`） |
| `BCartSpecialPrice` | - | 特価設定（複合PK: `BCartSpecialPricePK`） |
| `CustomerMapping` | - | B-CART 顧客マッピング |
| `DeliveryMapping` | - | B-CART 配送マッピング |
| `TSmileOrderImportFile` | `t_smile_order_import_file` | SMILE 注文インポートファイル管理 |

### 6.9 smile（SMILE 基幹システム連携）

SMILE からの受注・仕入データをワークテーブルで管理。

| エンティティ | テーブル | 概要 |
|---|---|---|
| `WSmileOrderOutputFile` | `w_smile_order_output_file` | SMILE 受注出力ファイルワーク（複合PK: `WSmileOrderOutputFilePK`） |
| `WSmilePurchaseOutputFile` | `w_smile_purchase_output_file` | SMILE 仕入出力ファイルワーク（複合PK: `WSmilePurchaseOutputFilePK`） |
| `WSmilePayment` | `w_smile_payment` | SMILE 支払情報ワーク |
| `TSmilePayment` | `t_smile_payment` | SMILE 支払情報本テーブル |

---

## 7. ディレクトリ構成

### 7.1 プロジェクトルート

```
C:\project\stock-app\
├── build.gradle                    # Gradle ビルド設定（Spring Boot 2.1.1.RELEASE）
├── CLAUDE.md                       # Claude Code 向けプロジェクト概要ドキュメント
├── DATABASE.md                     # DB 設計仕様書
├── BATCH.md                        # バッチ処理仕様書
├── README_BATCH_MIGRATION.md       # バッチ起動方式改善ドキュメント
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── jp/co/oda32/        # メインパッケージ
│   │   └── resources/
│   │       ├── config/             # 設定ファイル群
│   │       └── templates/          # Thymeleaf テンプレート
│   └── test/
│       └── java/
│           └── jp/co/oda32/        # テストクラス
└── .idea/                          # IntelliJ IDEA プロジェクト設定
```

### 7.2 Java ソース（`src/main/java/jp/co/oda32/`）

```
jp/co/oda32/
│
├── WebApplication.java             # Webアプリエントリポイント（@Profile("web")）
├── BatchApplication.java           # バッチアプリエントリポイント（@Profile("batch")）
├── SecurityConfig.java             # Spring Security 設定
├── ApiConfig.java                  # RestTemplate Bean 定義
├── WebConfig.java                  # MessageSource・バリデーション設定
│
├── [旧バッチエントリポイント群]
│   ├── AccountsPayableAggregationBatch.java   # 旧: 買掛金集計（@EnableBatchProcessing）
│   ├── AccountsPayableSummaryBatch.java       # 旧: 買掛金サマリー
│   ├── AccountsPayableVerificationBatch.java  # 旧: 買掛金検証
│   ├── AccountsReceivableSummaryBatch.java    # 旧: 売掛金サマリー
│   ├── SmilePaymentImportBatch.java           # 旧: SMILE支払情報取込
│   ├── PurchaseFileImportBatch.java           # B-CART関連バッチ起動クラス
│   ├── BCartOrderImportBatch.java             # B-CART注文取込バッチ
│   ├── BCartMemberUpdateBatch.java            # B-CART会員更新バッチ
│   └── [その他 *Batch.java]                  # 各種機能別バッチクラス
│
├── annotation/                     # カスタムアノテーション
│   ├── ApplicationType.java        # アプリタイプ指定（"web"/"batch"）
│   ├── ApplicationTypeCondition.java
│   ├── BigIntegerArrayTypeDescriptor.java
│   └── LongArrayType.java
│
├── aop/                            # AOP
│   └── ShopCheckAop.java           # Service の get*/find* メソッドで shop_no 整合チェック
│
├── app/                            # Presentation Layer（Web コントローラー）
│   ├── DashboardController.java    # ダッシュボード
│   ├── DashboardBatchController.java # ダッシュボードからのバッチ起動
│   ├── MenuController.java         # メニュー
│   ├── AjaxController.java         # Ajax API
│   ├── api/
│   │   └── PartnerApiController.java   # REST API エンドポイント
│   ├── bcart/                      # B-CART 出荷情報画面
│   ├── estimate/                   # 見積管理画面
│   ├── finance/                    # 財務（請求書一覧）画面
│   ├── goods/                      # 商品管理画面
│   ├── login/                      # ログイン・ユーザー管理画面
│   ├── master/                     # マスタ管理画面
│   ├── order/                      # 受注管理画面
│   ├── purchase/                   # 仕入・発注管理画面
│   └── stock/                      # 在庫管理画面
│
├── batch/                          # Batch Layer（Spring Batch）
│   ├── AbstractJobListener.java    # ジョブリスナー基底クラス
│   ├── JobStartEndListener.java    # ジョブ開始/終了ログリスナー
│   ├── ExitStatusChangeListener.java # 終了ステータス変更リスナー
│   ├── BatchStatusAPI.java         # バッチステータス API
│   ├── asana/                      # Asana 連携バッチ
│   ├── bcart/                      # B-CART 連携バッチ Tasklet 群
│   ├── estimate/                   # 見積関連バッチ Tasklet 群
│   ├── finance/                    # 財務・会計バッチ Tasklet 群
│   │   ├── config/                 # ジョブ設定クラス
│   │   │   ├── AccountsPayableAggregationConfig.java
│   │   │   ├── AccountsPayableVerificationConfig.java
│   │   │   ├── AccountsPayableSummaryConfig.java
│   │   │   ├── AccountsReceivableSummaryConfig.java
│   │   │   ├── PurchaseJournalIntegrationConfig.java
│   │   │   └── SalesJournalIntegrationConfig.java
│   │   ├── helper/                 # 税計算ヘルパー
│   │   ├── model/                  # バッチ用モデル（集計結果等）
│   │   └── service/                # バッチ用サービス
│   ├── goods/                      # 商品ファイル取込バッチ
│   │   └── config/GoodsFileImportConfig.java
│   ├── order/                      # 注文関連バッチ Tasklet 群
│   ├── purchase/                   # 仕入ファイル取込バッチ
│   │   └── config/PurchaseFileImportConfig.java
│   ├── smile/                      # SMILE 連携バッチ Tasklet 群
│   │   └── config/SmileOrderFileImportConfig.java
│   ├── stock/                      # 在庫関連バッチ Tasklet 群
│   └── util/
│       └── FileManagerTasklet.java # ファイル移動 Tasklet
│
├── config/                         # インフラ設定クラス
│   ├── CustomAuthenticationSuccessHandler.java  # ログイン成功後リダイレクト処理
│   └── SessionTimeoutRedirectStrategy.java      # セッションタイムアウト処理
│
├── constant/                       # 定数・列挙型
│   └── OfficeShopNo.java           # 営業所コード定義
│
├── controller/                     # 追加コントローラー
│   ├── finance/                    # 財務コントローラー
│   └── master/                     # マスタコントローラー
│
├── domain/
│   ├── model/                      # JPA エンティティ
│   │   ├── IEntity.java            # 全エンティティ共通インターフェース（getShopNo()）
│   │   ├── ICompanyEntity.java     # company_no エンティティインターフェース
│   │   ├── AbstractCompanyEntity.java  # company_no エンティティ基底クラス
│   │   ├── TAccessLog.java         # アクセスログ
│   │   ├── VSalesMonthlySummary.java   # 月別売上サマリービュー
│   │   ├── embeddable/             # 複合主キークラス（@Embeddable）
│   │   ├── bcart/                  # B-CART エンティティ
│   │   ├── estimate/               # 見積エンティティ
│   │   ├── finance/                # 財務エンティティ
│   │   ├── goods/                  # 商品エンティティ
│   │   ├── master/                 # マスタエンティティ
│   │   ├── order/                  # 受注エンティティ
│   │   ├── purchase/               # 仕入エンティティ
│   │   ├── query/                  # クエリ結果用モデル
│   │   ├── smile/                  # SMILE エンティティ
│   │   └── stock/                  # 在庫エンティティ
│   ├── repository/                 # Spring Data JPA Repository インターフェース
│   │   ├── bcart/
│   │   ├── estimate/
│   │   ├── finance/
│   │   ├── goods/
│   │   ├── master/
│   │   ├── order/
│   │   ├── purchase/
│   │   ├── smile/
│   │   └── stock/
│   ├── service/                    # Service クラス
│   │   ├── bcart/
│   │   ├── data/
│   │   ├── estimate/
│   │   ├── finance/
│   │   ├── goods/
│   │   ├── login/
│   │   │   └── LoginUserService.java  # UserDetailsService 実装
│   │   ├── master/
│   │   ├── order/
│   │   ├── purchase/
│   │   ├── smile/
│   │   ├── stock/
│   │   └── util/
│   │       └── LoginUserUtil.java     # ログインユーザー情報取得ユーティリティ
│   ├── specification/              # JPA Specification（クエリ仕様）
│   │   ├── bcart/
│   │   ├── estimate/
│   │   ├── finance/
│   │   ├── goods/
│   │   ├── master/
│   │   ├── order/
│   │   ├── purchase/
│   │   └── stock/
│   └── validation/                 # カスタムバリデーションアノテーション
│
├── exception/                      # カスタム例外クラス
├── filter/
│   └── SessionTimeoutFilter.java   # セッションタイムアウト検知 Servlet フィルター
├── jpa/                            # JPA 拡張設定
├── thymeleaf/                      # Thymeleaf カスタム拡張
└── util/                           # 汎用ユーティリティ
    └── gson/                       # Gson カスタムシリアライザー
        └── bcart/                  # B-CART JSON 変換ユーティリティ
```

### 7.3 リソース（`src/main/resources/`）

```
resources/
├── config/
│   ├── application.yml             # 共通設定
│   ├── application-dev.yml         # 開発環境設定
│   ├── application-prod.yml        # 本番環境設定
│   ├── application-batch.yml       # バッチ設定
│   ├── application-web.yml         # Web 設定
│   ├── schema-postgresql.sql       # DB スキーマ定義
│   └── ValidationMessages_ja_JP.properties  # バリデーションメッセージ
└── templates/                      # Thymeleaf テンプレート
    ├── layout.html                 # 共通レイアウト（Thymeleaf Layout Dialect）
    ├── dashboard.html              # ダッシュボード
    ├── loginForm.html              # ログイン画面
    ├── bcart/                      # B-CART 画面
    │   └── bcart_shipping_info_input.html  # 出荷情報入力
    ├── estimate/                   # 見積画面（5画面）
    ├── finance/                    # 財務画面
    │   ├── accountsPayable/        # 買掛金（detail/list）
    │   └── invoice/                # 請求書（detail/list）
    ├── goods/                      # 商品管理画面（12画面）
    ├── login/                      # ログイン・ユーザー管理画面（5画面）
    ├── master/                     # マスタ管理画面（10画面）
    ├── order/                      # 受注管理画面（2画面）
    ├── purchase/                   # 仕入・発注管理画面（14画面）
    └── stock/                      # 在庫管理画面（3画面）
```

---

## 付録: バッチジョブ一覧

| ジョブ名 | Bean 名 | 設定クラス | 処理内容 |
|---|---|---|---|
| `accountsPayableAggregation` | `accountsPayableAggregationJob` | `AccountsPayableAggregationConfig` | 買掛金集計（集計のみ） |
| `accountsPayableVerification` | `accountsPayableVerificationJob` | `AccountsPayableVerificationConfig` | 買掛金検証（SMILE支払照合+レポート） |
| `purchaseJournalIntegration` | `purchaseJournalIntegrationJob` | `PurchaseJournalIntegrationConfig` | 買掛金→仕入帳 CSV 出力（MF連携） |
| `salesJournalIntegration` | `salesJournalIntegrationJob` | `SalesJournalIntegrationConfig` | 売掛金→売上帳 CSV 出力（MF連携） |
| `purchaseFileImport` | `purchaseFileImportJob` | `PurchaseFileImportConfig` | 仕入ファイル取込（SMILE連携） |
| `smileOrderFileImport` | `smileOrderFileImportJob` | `SmileOrderFileImportConfig` | SMILE 受注ファイル取込 |
| `goodsFileImport` | `goodsFileImportJob` | `GoodsFileImportConfig` | 商品ファイル取込 |

---

## 付録: 今後の移行計画

CLAUDE.md に記載されている将来の技術的負債・移行計画。

| 項目 | 内容 |
|---|---|
| Spring Boot 3.x 移行 | Jakarta EE 対応が必要（`javax.*` → `jakarta.*` の一括置換） |
| `@EnableBatchProcessing` | Spring Boot 3.x では削除予定 |
| `JobBuilderFactory` / `StepBuilderFactory` | Spring Boot 3.x では廃止。`JobRepository`、`TransactionManager` を直接利用する方式に変更が必要 |

各バッチ設定クラス（`PurchaseFileImportConfig`、`AccountsPayableAggregationConfig` 等）には Spring Boot 3.x 移行時の変更方針がコメントとして記載されている。
