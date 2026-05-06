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
  - `GET /oauth/authorize-url` — 認可 URL 取得
  - `POST /oauth/callback` — code → token 交換
  - `GET /oauth/status` — トークン有効性・有効期限
  - `POST /oauth/revoke` — トークン失効
  - `GET /reconcile` — 指定月の仕訳突合結果
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

### G1-M3: 未バインド client 強制 binding（2026-05-06 追加）

P1-01 で導入した tenant binding は **refresh 経路でしか tenant 検証しない** 設計だった。これにより以下の穴が残っていた:

| 状態 | P1-01 の挙動 | リスク |
|---|---|---|
| `mf_tenant_id` 設定済 + token expired → refresh | refresh 後に `/v2/tenant` で一致検証 | OK (P1-01 の本流) |
| `mf_tenant_id` 設定済 + token fresh | tenant 検証スキップ | OK（バインド済なので別会社差替不可）|
| **`mf_tenant_id` IS NULL + token fresh**（旧データ） | tenant 検証スキップで業務 API が通る | **NG: 別会社 MF を誤認可していても気付けない** |
| `mf_tenant_id` IS NULL + token expired → refresh | refresh 後に初回 binding 確定 | OK |

本番 DB には P1-01 導入前から認可済みの client (`m_mf_oauth_client.id=1`, `mf_tenant_id IS NULL`) が 1 件残存しており、その access_token が fresh な間は tenant 検証ゼロで業務 API が実行できる状態になっていた。

#### 対応

`MfOauthService.getValidAccessToken()` に `mf_tenant_id IS NULL` 判定を追加し、fresh パスでも強制的に `/v2/tenant` を呼び出して binding を確定する。

```text
loadActiveTokenSnapshot()
  ↓
mfTenantId == null && fresh ?  ──── YES ──→ getTenant() ──→ bindTenantInNewTx() ──→ access_token 返却
  ↓ NO                                      (失敗時)
fresh ?                                     MfTenantBindingFailedException (= 業務 API 停止)
  ↓ NO
refreshToken() → getTenant() → persistRefreshedTokenAndTenant()  (P1-01 経路)
```

`bindTenantInNewTx()` は token は触らず tenant 列のみ確定する `REQUIRES_NEW` メソッド。`bindOrVerifyTenant()` を再利用するため、別 thread が先に binding 済の場合は一致検証で `MfTenantMismatchException` が throw される (race condition も安全)。

#### エラー区別

| 例外 | HTTP status | code | 意味 |
|---|---|---|---|
| `MfTenantMismatchException` (P1-01) | 409 CONFLICT | `MF_TENANT_MISMATCH` | 既存バインドと観測 tenant が **異なる** = 別会社 MF 誤認可 |
| `MfTenantBindingFailedException` (G1-M3, 新規) | 503 SERVICE_UNAVAILABLE | `MF_TENANT_BINDING_FAILED` | 初回 binding 試行で `/v2/tenant` 自体が **失敗** = MF 一時障害 / scope 不足等 |

503 を選択した理由: MF API が一時的に応答しない可能性（リトライ可能）。409 は構成不整合（人間判断必要）で意味が異なる。

#### 旧データ運用シナリオ

1. 本番 DB に `id=1, mf_tenant_id IS NULL` が 1 件存在
2. デプロイ後、最初の業務 API 呼び出し (=任意の `getValidAccessToken` 経路) で強制 binding が試行される
3. 成功 → `mf_tenant_id` / `mf_tenant_name` / `tenant_bound_at` が確定し、以後は通常 fresh path で透過
4. 失敗 → 503 が業務 API から返却。admin に「MF 連携設定を確認」を促し、`/finance/mf-integration` で再認可する

#### 実装範囲

- `MfTenantBindingFailedException`（新規）
- `FinanceExceptionHandler.handleMfTenantBindingFailed`（503 + 汎用化メッセージ）
- `MfOauthService.getValidAccessToken`（NULL チェック分岐 + 強制 binding）
- `MfOauthService.bindTenantInNewTx`（token 不変、tenant のみ確定）
- `MfOauthServiceForcedBindingTest`（9 ケース）+ `FinanceExceptionHandlerTest`（503 マッピング 1 ケース）

#### 残課題

- `MfTokenStatus.tenantBindingPending` フラグ + frontend banner 表示は **未実装**（強制 binding が次の API 呼び出しで成功すれば自動解消するため、admin が気付かなくても運用上の実害は低い）。必要なら別 task で追加。

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
| GET  | `/oauth/authorize-url?state=...` | `{ url }` — フロントがそのまま window.open |
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
│ 仕入仕訳    28件  ¥1,234,567    28  ¥1,234,567 ✅ 一致   │
│ 売上仕訳    42件  ¥9,876,543    41  ¥9,876,123 ⚠️ 1件     │
│ 買掛支払    15件    ¥987,654    15    ¥987,654 ✅ 一致   │
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

### 9.1 OAuth endpoint host allowlist（G1-M5、2026-05-06）

**背景**: `m_mf_oauth_client` の `authorize_url` / `token_url` / `api_base_url` は admin が画面で自由入力可能。攻撃者が ADMIN 権限を取得した場合（あるいは内部犯）に攻撃者制御の token endpoint を登録すると、`MfApiClient` が `client_secret` を Basic auth で攻撃者サーバーに POST し、credential exfiltration が成立する。

**対策**: `MfOauthHostAllowlist` (`jp.co.oda32.domain.service.finance.mf`) で URL ホストを allowlist 検証する。`MfOauthService.upsertClient` から `validateRedirectUri` と同じ位置で 3 フィールドを呼び出す。

| profile | 許可ホスト | 許可 scheme |
|---|---|---|
| production（`dev`/`test` を含まない） | `api.biz.moneyforward.com`, `api-accounting.moneyforward.com` | `https` のみ |
| `dev` / `test` | 上記 + `localhost`, `127.0.0.1` | `https`、加えて `localhost`/`127.0.0.1` のみ `http` も許可 |

違反時は `FinanceBusinessException(ERROR_CODE = "MF_HOST_NOT_ALLOWED")` を投げ、`FinanceExceptionHandler.handleFinanceBusiness` 経由で 400 Bad Request にマップされる。完全一致比較（`Set.contains`）でサブドメイン攻撃（`api.biz.moneyforward.com.evil.com` 等）も拒否する。

allowlist 変更は本クラスのコード変更を伴うため、追加/変更には PR レビューが必須となる（DB / 環境変数経由では変更できない）。テスト: `MfOauthHostAllowlistTest` (16 ケース) で prod / dev / scheme / 入力検証をカバー。

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

## 14. T6: Scope 変更検知 + 警告 UI（2026-05-04 追加）

### 背景

`m_mf_oauth_client.scope` は admin が画面 (`/finance/mf-integration` クライアント設定) で編集可能。誤って必須 scope を消してしまうと、再認可するまで気付かず関連 API が 403 で失敗し続ける運用リスクがある。P1-01 で tenant binding は実装済のため、残タスクは scope 変更検知のみ。

### 必須 Scope の typed 定義

backend / frontend の両方に同期した typed list を持つ。新規必須 scope の追加時は両方を同時更新する。

| 場所 | シンボル |
|---|---|
| backend | `MfScopeConstants.REQUIRED_SCOPES` (`jp.co.oda32.domain.service.finance.mf.MfScopeConstants`) |
| frontend | `MF_REQUIRED_SCOPES` (`frontend/types/mf-integration.ts`) |

現状 (Phase 1 仕訳突合 + tenant binding) の必須 6 scope:

```
mfc/accounting/journal.read    — 仕訳取得
mfc/accounting/accounts.read   — 勘定科目取得
mfc/accounting/offices.read    — 事業所取得
mfc/accounting/taxes.read      — 税区分取得
mfc/accounting/report.read     — 試算表取得
mfc/admin/tenant.read          — tenant binding (P1-01)
```

### Scope 解析ロジック

`MfScopeConstants.analyze(String)` が DB の `m_mf_oauth_client.scope` (空白区切り) を解析し、不足/過剰を返す。

```java
public record ScopeAnalysis(List<String> missing, List<String> extra) {}
```

- `missing`: 必須だが現 scope に含まれていない (要素あり = banner 赤表示の根拠)
- `extra`: 現 scope に含まれているが必須ではない (動作影響なし、管理上の指標)

`MfOauthService.getStatus()` は `client.getScope()` (= 現 active token の `t.getScope()` ではなく admin が設定した値) を解析対象にする。これにより admin が必須 scope を削除した時点で、再認可前でも構成ドリフトを検知できる。

### MfTokenStatus 拡張フィールド

`MfTokenStatus` record (= `GET /api/v1/finance/mf-integration/oauth/status` レスポンス) に T6 で 3 フィールド追加:

| フィールド | 型 | 意味 |
|---|---|---|
| `missingScopes` | `List<String>` | 不足 scope (空 list = OK) |
| `extraScopes` | `List<String>` | 過剰 scope (空 list = OK、警告レベル) |
| `scopeOk` | `boolean` | `missingScopes.isEmpty()` の short-hand (UI 判定用) |

### Frontend Banner 表示条件

`MfScopeBanner` (`frontend/components/common/MfScopeBanner.tsx`) を新規作成し、`AppLayout` の top header 直下に `MfReAuthBanner` (P1-04 案 α) と並列配置する。

severity 固定 = `destructive` (赤)。表示条件:

| 条件 | 表示 |
|---|---|
| 未認証 (`!isAuthenticated`) | × |
| API エラー / data なし | × |
| 未接続 (`connected=false`) | × (mf-integration 画面で別 UI) |
| `scopeOk=true` | × |
| 上記以外 | ○ (赤 banner、`missingScopes` を `<ul>` で列挙、再認可画面リンク) |

両 banner は同じ `useQuery` キー (`'mf-oauth-status'`) を共有するため、TanStack Query の cache 機構で API call は 1 回にまとまる。

### P1-01 (tenant binding) との関係

T6 の前提条件として P1-01 が完了している必要がある (= `mfc/admin/tenant.read` が必須 scope に含まれている)。P1-01 と T6 の責務分担:

| 検知対象 | 責務 |
|---|---|
| 別会社 MF 誤接続 (tenant ID 不一致) | P1-01 `MfTenantMismatchException` (callback / refresh で throw) |
| 旧 client 未バインド (mf_tenant_id IS NULL) で fresh token 利用 | G1-M3 `MfTenantBindingFailedException` (強制 binding 失敗時) — 詳細は §4 G1-M3 |
| scope 不足 (admin の編集ミス) | T6 `MfScopeBanner` (UI 警告) |

### 実装ファイル

- `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfScopeConstants.java` (新規)
- `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfTokenStatus.java` (3 フィールド追加)
- `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java` (`getStatus()` で解析)
- `frontend/types/mf-integration.ts` (`MfTokenStatus` interface に 3 フィールド追加)
- `frontend/components/common/MfScopeBanner.tsx` (新規)
- `frontend/components/layout/AppLayout.tsx` (`MfReAuthBanner` の隣に配置)

## 15. G1-M4: refresh_token_issued_at カラム追加 + 期限超過 banner（2026-05-06）

### 背景

P1-04 案 α では `refresh_token` の 540 日寿命を `t_mf_oauth_token.add_date_time` 起点で算出していたが、
`MfOauthService.persistToken` は **softDeleteActiveTokens + 新 row insert** で動作しており、
rotation OFF (= MF レスポンスに `refresh_token` が含まれず旧値を流用するケース) でも `add_date_time` は毎回更新される。
結果として、refresh するたびに残日数カウントがリセットされ「540 日切れ警告が遅れる」過大評価バグが残っていた。

### 対応: refresh_token の真の発行日を別カラムで保持

`t_mf_oauth_token` に `refresh_token_issued_at TIMESTAMP NOT NULL` を追加 (V042)。
`persistToken` で以下のロジックで決定する。

| MF token レスポンスの refresh_token | 旧 active row | 採用値 |
|---|---|---|
| あり (rotation 動作) | あり / なし | `now()` |
| なし (rotation OFF) | あり | **旧 row の `refresh_token_issued_at` を継承** |
| なし | なし | `now()` (fallback、初回認可で起き得ない構成) |

`getStatus()` は `refresh_token_issued_at` を起点に経過日数を計算するように変更。
これにより rotation 設定に依らず 540 日寿命カウントが正確になる。

### V042 migration

```sql
ALTER TABLE t_mf_oauth_token
    ADD COLUMN IF NOT EXISTS refresh_token_issued_at TIMESTAMP;
UPDATE t_mf_oauth_token
SET refresh_token_issued_at = add_date_time
WHERE refresh_token_issued_at IS NULL;
ALTER TABLE t_mf_oauth_token
    ALTER COLUMN refresh_token_issued_at SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_mf_oauth_token_refresh_issued
    ON t_mf_oauth_token (refresh_token_issued_at);
```

backfill は既存 24 行 (本番) を `add_date_time` でコピー = 旧仕様の挙動と一致。
適用後 1 周目の token refresh で rotation OFF なら旧値継承、rotation 動作なら新値で上書きされる。

### MfTokenStatus 拡張: reAuthExpired フラグ

期限超過 (= 540 日経過後の負値領域) を **最上位 severity** で UI に強調表示するため、
`MfTokenStatus.reAuthExpired: boolean` を新規追加。

| state | reAuthRequired | reAuthExpired | daysUntilReauth |
|---|---|---|---|
| `remaining > 7` | false | false | int 値 |
| `remaining ≤ 7 && remaining ≥ 0` | true (= 既存挙動) | false | int 値 |
| `remaining < 0` (G1-M4 強化) | true | **true** | 0 (clamp) |

`reAuthRequired` だけだと「7 日切ったら true」「540 日超過しても true」が同じ true になり UI で判別不可だった。
`reAuthExpired` を独立フラグにすることで、Banner severity 階層を以下のように拡張する:

| 状態 | severity | banner |
|---|---|---|
| `reAuthExpired=true` (G1-M4 新規) | destructive + animate-pulse + 期限超過メッセージ | 「MF refresh_token 期限超過、再認可必須」 |
| `daysUntilReauth ≤ 0` (= reAuthRequired のうち超過していない) | destructive | 「MF 連携停止中」 |
| `daysUntilReauth ≤ 7` | destructive + animate-pulse | 「緊急: あと N 日で MF 連携停止」 |
| `daysUntilReauth ≤ 14` | destructive | 「MF 再認可必須 (あと N 日)」 |
| `daysUntilReauth ≤ 30` | amber | 「MF 再認可推奨 (あと N 日)」 |
| `daysUntilReauth ≤ 60` | yellow | 「MF 再認可予定 (あと N 日)」 |
| 上記以外 | 非表示 | — |

### 実装ファイル

- `backend/src/main/resources/db/migration/V042__add_refresh_token_issued_at.sql` (新規)
- `backend/src/main/java/jp/co/oda32/domain/model/finance/TMfOauthToken.java` (`refreshTokenIssuedAt` フィールド追加)
- `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java` (`persistToken` で継承ロジック / `getStatus` で issued_at 参照 + reAuthExpired)
- `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfTokenStatus.java` (`reAuthExpired` フィールド追加)
- `frontend/types/mf-integration.ts` (`reAuthExpired` 追加)
- `frontend/components/common/MfReAuthBanner.tsx` (期限超過 case を最上位 severity に追加)
- `backend/src/test/java/jp/co/oda32/domain/service/finance/mf/MfOauthServiceRefreshTokenIssuedAtTest.java` (5 ケース、全 PASS)

### 残課題

- 既存 banner の `daysUntilReauth ≤ 0` 分岐は到達不能 (期限超過は必ず `reAuthExpired=true` で先行ヒット) になったが、
  防御的に残しておく (将来 `reAuthExpired` 算出ロジックに不具合が出ても banner は表示される)
- prod 適用時は backfill 後 `flyway_schema_history` で `version='42'` の success を確認

## 参考

- MF API 仕様書: https://developers.api-accounting.moneyforward.com/
- MF 開発者サイト: https://developers.biz.moneyforward.com/
- アプリポータル: https://app-portal.moneyforward.com/
- 本プロジェクト既存 MF 関連:
  - `design-payment-mf-import.md` / `design-payment-mf-aux-rows.md`
  - `design-accounts-receivable-mf.md`
  - `design-mf-cashbook-import.md`
