# Runbook: MF OAuth 暗号鍵セットアップ・運用 (P1-05 案 C.3)

## 1. 概要

MF (マネーフォワード) OAuth クライアントとトークンを DB 保存する際に使用する AES-256/GCM 暗号鍵
(`app.crypto.oauth-key` / `app.crypto.oauth-salt`) のセットアップ・運用手順。

### なぜ専用鍵なのか (P1-05 経緯)

従来は `app.crypto.key` / `app.crypto.salt` 1 つの鍵で以下すべてを暗号化していた:

- 社内 password 系の `_enc` カラム
- `m_mf_oauth_client.client_secret_enc`
- `t_mf_oauth_token.access_token_enc`
- `t_mf_oauth_token.refresh_token_enc`

`app.crypto.key` / `app.crypto.salt` は dev fallback がリポジトリに commit 済みで、
万一その値が prod 環境変数の代替で使われると MF API token (= 第三者の業務システム閲覧/操作権) も
復号可能になるリスクがあった。

P1-05 案 C.3 で MF OAuth 関連 3 カラムだけを **専用鍵** (`app.crypto.oauth-key`/`oauth-salt`) に分離。
新鍵は **dev / prod とも env 必須** とし、リポジトリに dev fallback を持たせない (= 鍵が無いと起動失敗)。
社内 password 系の鍵は従来どおり dev fallback 維持 (MEMORY.md `feedback_dev_config_fallbacks` 方針)。

### 対象カラム

| テーブル | カラム |
|---|---|
| `m_mf_oauth_client` | `client_secret_enc` |
| `t_mf_oauth_token` | `access_token_enc` |
| `t_mf_oauth_token` | `refresh_token_enc` |

---

## 2. 初回セットアップ手順 (dev 環境)

### Step 1: 鍵生成

```powershell
cd C:\project\odamitsu-data-hub\backend\scripts
.\gen-oauth-key.ps1
```

出力例:

```
=== APP_CRYPTO_OAUTH_KEY (32 byte base64) ===
abcDEF...== (44 文字 base64)

=== APP_CRYPTO_OAUTH_SALT (16 byte hex) ===
0123456789abcdef0123456789abcdef
```

### Step 2: IntelliJ Run Configuration に env var 追加

1. IntelliJ → Run → Edit Configurations
2. `WebApplication` (バックエンド起動) を選択
3. `Modify options` → `Environment variables` を有効化
4. 下記 2 行を追加 (Step 1 の出力値):
   ```
   APP_CRYPTO_OAUTH_KEY=abcDEF...==
   APP_CRYPTO_OAUTH_SALT=0123456789abcdef0123456789abcdef
   ```
5. `BatchApplication` 用の Run Configuration にも同様に追加 (バッチ実行時も DB 復号する場合)

### Step 3: 起動 → Flyway V033 確認

`./gradlew bootRun --args='--spring.profiles.active=web,dev'` でバックエンド起動。

ログに以下が出れば成功:

```
[V033] OAuth secrets re-encrypted: client_secret=N, access_token=M, refresh_token=L
```

(初回 / 既に MF 連携設定が無い環境では N=M=L=0)

その後、`OauthCryptoUtil 初期化完了（MF OAuth 専用 AES-256 GCM, Hex 出力）` が出れば
Spring Bean も無事に立ち上がっている。

---

## 3. 本番デプロイ手順

### Step 1: 本番用の鍵生成

dev とは **必ず別の鍵** を生成する。`gen-oauth-key.ps1` を本番デプロイ作業端末で再実行。

### Step 2: 本番サーバーの env 設定

デプロイ方式に応じて以下のいずれかで env var を設定:

#### systemd service の場合

```ini
# /etc/systemd/system/odamitsu-backend.service
[Service]
Environment="APP_CRYPTO_OAUTH_KEY=...本番用鍵..."
Environment="APP_CRYPTO_OAUTH_SALT=...本番用 salt..."
```

`systemctl daemon-reload && systemctl restart odamitsu-backend`

#### docker-compose の場合

```yaml
# docker-compose.yml
services:
  backend:
    environment:
      APP_CRYPTO_OAUTH_KEY: ${APP_CRYPTO_OAUTH_KEY}
      APP_CRYPTO_OAUTH_SALT: ${APP_CRYPTO_OAUTH_SALT}
```

`.env` ファイル (compose と同じディレクトリ、git ignored) に値を記載。

#### Kubernetes Secret の場合

```bash
kubectl create secret generic mf-oauth-keys \
  --from-literal=APP_CRYPTO_OAUTH_KEY="..." \
  --from-literal=APP_CRYPTO_OAUTH_SALT="..."
```

Deployment manifest で `envFrom: secretRef: name: mf-oauth-keys` で注入。

### Step 3: V033 migration の実行

本番初回デプロイ時:
- 既存環境の `m_mf_oauth_client.client_secret_enc` と `t_mf_oauth_token.*_enc` は
  **旧鍵** (`APP_CRYPTO_KEY`/`SALT`) で暗号化されている前提
- V033 が起動時に自動で旧鍵 → 新鍵への再暗号化を実行
- 旧鍵環境変数が prod に設定済みなら V033 は env から旧鍵を取り、再暗号化
- 旧鍵環境変数が無い場合 V033 は失敗する → 旧鍵を一時的に env に設定して再起動

実行ログで件数を確認:

```
[V033] OAuth secrets re-encrypted: client_secret=1, access_token=1, refresh_token=1
```

### Step 4: 旧鍵スコープ縮小 (任意)

V033 完了後、`APP_CRYPTO_KEY`/`SALT` は MF OAuth 復号には不要になる
(社内 password 等の汎用暗号化用途のみ)。prod 上での権限 / 露出範囲を縮小することを検討する。

---

## 4. 鍵ローテーション手順 (定期 / 漏洩時)

### Step 1: 新鍵生成

```powershell
.\gen-oauth-key.ps1
```

新しい `APP_CRYPTO_OAUTH_KEY_v2` / `APP_CRYPTO_OAUTH_SALT_v2` を生成。

### Step 2: backend 停止

ローテーション中は MF API 連携が一時停止する。短時間で完了する見込みでも事前周知推奨。

### Step 3: 新 Flyway migration 追加

`V034__rotate_mf_oauth_secrets.java` を `V033` と同じパッケージに作成し、
旧鍵 (`APP_CRYPTO_OAUTH_KEY`) → 新鍵 (`APP_CRYPTO_OAUTH_KEY_v2`) で再暗号化する処理を実装。

V033 を雛形にコピーし、env var 名のみ変更:

```java
String oldKey = System.getenv("APP_CRYPTO_OAUTH_KEY");
String newKey = System.getenv("APP_CRYPTO_OAUTH_KEY_v2");
// 旧 fallback は無い (V033 と異なり、必ず env 必須)
if (oldKey == null || ...) throw new IllegalStateException("...");
```

### Step 4: env 切替 + 再起動

1. `APP_CRYPTO_OAUTH_KEY_v2` / `APP_CRYPTO_OAUTH_SALT_v2` を env に追加
2. backend 起動 → V034 が自動実行
3. ログで件数確認後、env 上で `APP_CRYPTO_OAUTH_KEY` を **新値で上書き** + `_v2` 環境変数を削除
4. 再度 backend 再起動

(あるいは V034 完了後に env 名を変えず単純に値を上書きするだけでも可。手順を 1 段階減らせるが、
V034 内で「新鍵が旧鍵と異なる」ことを assert する仕組みが必要)

---

## 5. 鍵紛失時の復旧手順

`APP_CRYPTO_OAUTH_KEY` / `SALT` が消失して既存 `_enc` データを復号不能になった場合:

### Step 1: 既存暗号化データの削除

```sql
-- m_mf_oauth_client.client_secret_enc は NOT NULL なので一旦行ごと削除する判断もあり
-- (画面から再登録する前提)
DELETE FROM t_mf_oauth_token;          -- 全 token を削除 (revoke 相当)
DELETE FROM m_mf_oauth_client;         -- client 設定も削除して画面から再登録
```

### Step 2: 新鍵を生成 + env 設定 (Step 1〜2 と同じ)

### Step 3: backend 再起動

V033 は `WHERE *_enc IS NOT NULL` で対象 0 件になるので無事完了。

### Step 4: MF 連携を画面から再認可

1. 管理画面 `/finance/mf-integration` を開く
2. Client ID / Client Secret を再入力 (MF 開発者ポータルから取得)
3. 「認可開始」→ MF ログインフロー → callback で再 binding

---

## 6'. encryption version カラム (V037 / C1 idempotent 化)

### Codex P1 修正履歴 (2026-05-06)

| 修正 | 内容 |
|---|---|
| **P1**: refresh_token_enc が旧鍵のまま残るバグ | 旧 V033 は `t_mf_oauth_token` を `reencrypt(access_token_enc)` → `reencrypt(refresh_token_enc)` の **2 回呼び出し** で処理していた。1 回目で `version=1 → 2` にマークしたため、2 回目では `WHERE version=1` がヒット 0 件 → refresh_token_enc が旧鍵のまま残り、起動後の decrypt が失敗 → MF 連携全停止の致命バグ。**修正**: `reencryptMfTokens(conn, oldCipher, newCipher, hasVersion)` で **両カラムを 1 つの UPDATE で同時に再暗号化** + version=2 マーク。 |
| **P1 fix #2**: decrypt 失敗で全停止 | 旧実装は decrypt 失敗で `IllegalStateException` を即 throw していた。version 列なし環境で V033 が部分 commit 後に再実行されると、新鍵化済の row を旧鍵で decrypt 試行 → 失敗 → migration 全体停止になる。**修正**: 失敗時は WARN ログ (`System.err.printf`) を出して **当該カラムを温存** (= 既に新鍵化済と仮定)、別カラム / 別行の再暗号化は継続。 |
| **P2**: V037 の番号順 | V037 が V033 より後の番号で適用されるため、新規環境で V033 → V037 の順に走る。V033 は `DatabaseMetaData.getColumns()` で version 列の有無を動的判定し列なしなら全行を一度きり再処理 → V037 が列追加 + flyway_schema_history マーカーで全行 version=2 にマーク、で連携。番号入れ替え (V032.5 にリネーム) は本番影響大なため見送り、上記 P1 fix #2 の skip ロジックでフォールバック対応。 |

#### 本番 DB への影響
**なし**。本番 (`oda32-postgres17`) は V033 / V037 既に成功適用済 + 全 24 行 (`m_mf_oauth_client` 1 + `t_mf_oauth_token` 23) とも `oauth_encryption_version=2` を確認済。今回の Java 修正は **将来 deploy 時の安全性向上** が目的。検証 SQL:
```sql
SELECT 'm_mf_oauth_client' AS tbl, oauth_encryption_version, COUNT(*) AS cnt
FROM m_mf_oauth_client GROUP BY oauth_encryption_version
UNION ALL
SELECT 't_mf_oauth_token' AS tbl, oauth_encryption_version, COUNT(*) AS cnt
FROM t_mf_oauth_token GROUP BY oauth_encryption_version;
-- 期待: 全行 oauth_encryption_version = 2
```

#### テスト
`backend/src/test/java/db/migration/V033ReencryptionTest.java` (6 ケース):
- `reencryptMfTokens_両カラム同時更新_versionMarker` ← P1 主要回帰テスト
- `reencryptMfTokens_既新鍵化済はskip` (idempotent)
- `reencryptMfTokens_decrypt失敗時はskip温存` (P1 fix #2)
- `reencrypt_singleColumn_clientSecret`
- `reencryptMfTokens_両NULL行はマーカーのみ`
- `reencryptMfTokens_version列なし`

実行: `cd backend && JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 ./gradlew test --tests '*V033*' --tests '*Reencryption*'`

### 経緯
旧 V033 は autoCommit OFF + 全件まとめて commit 方式で、Flyway 履歴記録前にプロセス停止すると
「暗号文は新鍵化済み・migration 履歴は未適用」のミスマッチ状態になり、次回起動で旧鍵 decrypt 試行 →
復旧困難になっていた (Codex 批判 Critical C1)。

### 対策: oauth_encryption_version 列導入 (V037)
`m_mf_oauth_client` / `t_mf_oauth_token` に `oauth_encryption_version SMALLINT NOT NULL DEFAULT 1` を追加。

| version | 意味 |
|---------|------|
| 1 | 旧鍵 (`APP_CRYPTO_KEY` / `APP_CRYPTO_SALT`) で暗号化 |
| 2 | 新鍵 (`APP_CRYPTO_OAUTH_KEY` / `APP_CRYPTO_OAUTH_SALT`) で暗号化 |

V033 は autoCommit ON + 行単位 commit で動作:
- 列が存在すれば `WHERE version=1` で対象を絞り、UPDATE で `version=2` を同時セット
- 途中で例外発生してもそれまで update 済みの行は確定済み (version=2)
- 再実行時は version=2 行を skip → 残った version=1 行から自動再開

V037 は V033 が既に成功適用済 (flyway_schema_history で `version='033' AND success=true`) を検出すると
全行を `version=2` にマークする (V037 列追加前に新鍵化完了している前提)。

### 起動時の混在チェック (運用)
全行が `version=2` になっているか定期確認:

```sql
SELECT 'm_mf_oauth_client' AS tbl, oauth_encryption_version, COUNT(*) AS cnt
FROM m_mf_oauth_client GROUP BY oauth_encryption_version
UNION ALL
SELECT 't_mf_oauth_token' AS tbl, oauth_encryption_version, COUNT(*) AS cnt
FROM t_mf_oauth_token GROUP BY oauth_encryption_version;
```

`version=1` 行が残っていれば V033 が未完了 → backend 再起動で再開、または手動で V033 を再走させる。

---

## 6. トラブルシューティング

### 起動時 `IllegalStateException: app.crypto.oauth-key が未設定または短すぎます`

→ Step 1-2 を実施。env が読まれていない場合は IntelliJ Run Configuration 再起動 / shell 再起動。

### Flyway V033 失敗 (`BadPaddingException` / `AEADBadTagException`)

→ 旧鍵 (`APP_CRYPTO_KEY`/`SALT`) が変わった可能性。
   - 旧 env を一時的に元に戻して再起動
   - 思い当たらない場合は **鍵紛失復旧手順 (§5)** を実施

### `[V033] APP_CRYPTO_OAUTH_KEY と APP_CRYPTO_OAUTH_SALT は必須です。`

→ V033 migration が新鍵を取得できなかった。Step 1-2 を実施してから再起動。

### 既に migration 済みで起動

→ `flyway_schema_history` で `version=33` が `success=true` で記録されているか確認:

```sql
SELECT version, description, success, installed_on
FROM flyway_schema_history
WHERE version = '33';
```

success なら V033 は再実行されない (Flyway の正常動作)。

### `OauthCryptoUtil 初期化完了` のログが出ない

→ Spring が `OauthCryptoUtil` Bean を検出していない。`@ComponentScan` 範囲確認 (`jp.co.oda32` 配下なので
   通常は問題なし)。

### テスト実行時 (`./gradlew test`) に Bean 解決失敗

→ `backend/src/test/resources/config/application-test.yml` に
   `app.crypto.oauth-key` / `oauth-salt` が定義されているか確認。
   既定で固定ダミー値を入れてあるので、消したり変更したりしないこと。

---

## 7. 関連ファイル

- `backend/src/main/java/jp/co/oda32/util/OauthCryptoUtil.java` — MF OAuth 専用暗号化 Bean
- `backend/src/main/java/jp/co/oda32/util/CryptoUtil.java` — 汎用暗号化 Bean (社内 password 等)
- `backend/src/main/java/db/migration/V033__reencrypt_mf_oauth_secrets.java` — 再暗号化 Java migration (idempotent / C1 fix 済)
- `backend/src/main/resources/db/migration/V037__add_oauth_encryption_version.sql` — encryption version 列追加 (C1 idempotent 化)
- `backend/scripts/gen-oauth-key.ps1` — 鍵生成 PowerShell script
- `backend/src/main/resources/config/application.yml`
- `backend/src/main/resources/config/application-dev.yml`
- `backend/src/main/resources/config/application-prod.yml`
- `backend/src/test/resources/config/application-test.yml`
- `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthService.java` — `OauthCryptoUtil` を使用

---

## 8. OAuth endpoint host allowlist (G1-M5、2026-05-06)

### 8.1 何を防ぐか

`m_mf_oauth_client.authorize_url` / `token_url` / `api_base_url` は admin 画面で自由入力可能だった。
`MfApiClient` は `token_url` に対して `client_secret` を Basic auth (HTTP Authorization ヘッダ) で
POST するため、攻撃者制御 URL を登録された場合 client_secret が攻撃者サーバーに送信される
(credential exfiltration) リスクがあった。G1-M5 で `MfOauthHostAllowlist` によりホストを
allowlist 検証する。

### 8.2 許可ホスト

| profile | 許可ホスト | 許可 scheme |
|---|---|---|
| production (`spring.profiles.active` に `dev` / `test` を含まない) | `api.biz.moneyforward.com`, `api-accounting.moneyforward.com` | `https` のみ |
| `dev` / `test` | 上記 + `localhost`, `127.0.0.1` | `https` + (`localhost`/`127.0.0.1` のみ `http` 可) |

ホスト一致は完全一致 (`Set.contains`)。サブドメイン (`api.biz.moneyforward.com.evil.com` 等) や
ポートの異なる版 (`localhost:9000`) は host 部分での比較なので OK / NG は host のみで決まる。

### 8.3 allowlist 変更時の手順

allowlist の値はコード内の `MfOauthHostAllowlist.PRODUCTION_HOSTS` / `DEV_HOSTS` 定数に直書きされている
(攻撃者が DB / 環境変数経由で書き換える経路を持たないため)。変更が必要な場合:

1. `backend/src/main/java/jp/co/oda32/domain/service/finance/mf/MfOauthHostAllowlist.java` を編集
2. `MfOauthHostAllowlistTest` に新ホストの許可テストを追加
3. PR を作成し、セキュリティレビュー (= MF が公式に発表したエンドポイント変更であることの確認) を受ける
4. 本設計書 §9.1 と本 runbook §8.2 の表を更新

MF 側のエンドポイントが変わった場合 (例: API バージョン変更で `api-accounting-v2.moneyforward.com` 等) は、
**移行期間中は両方のホストを allowlist に残す**。旧ホスト削除は移行完了後にもう 1 PR で行う。

### 8.4 違反時の挙動

`upsertClient` で違反検出時に `FinanceBusinessException(message, "MF_HOST_NOT_ALLOWED")` が throw され、
`FinanceExceptionHandler.handleFinanceBusiness` が **HTTP 400 Bad Request** + 元メッセージ (フィールド名と
拒否ホスト) で返す。画面では「authorizeUrl のホストは許可されていません: xxx (allowlist: ...)」のような
メッセージが admin に表示される。

### 8.5 既存 DB データへの影響

本番 DB は MF 公式ホスト (`https://api.biz.moneyforward.com/...`、`https://api-accounting.moneyforward.com/...`)
で登録済のため、allowlist 適用後も既存値は通る (= 既存連携は引き続き動作する)。
万一過去に攻撃者ホストを登録していた場合は、`upsertClient` で再保存しようとした時点で 400 になるため、
SQL で直接該当行の `authorize_url` / `token_url` / `api_base_url` を MF 公式ホストに書き戻してから
画面操作を行うこと。

---

## 9. refresh_token 540 日 expire 時の手順 (G1-M4、2026-05-06)

### 9.1 何が起きるか

`refresh_token` は MF 仕様で 540 日寿命。期限超過すると以下の挙動になる:

1. グローバル top header の `MfReAuthBanner` が **最上位 severity** (赤 + animate-pulse) で
   「**MF refresh_token 期限超過、再認可必須**」を表示する
2. `MfTokenStatus.reAuthExpired = true` / `daysUntilReauth = 0` (clamp) が API レスポンスに乗る
3. 業務側で MF API を呼ぶと `MfReAuthRequiredException` (HTTP 401) が発生し、関連処理が停止する

### 9.2 540 日寿命の起点: refresh_token_issued_at カラム (V042)

V042 で `t_mf_oauth_token.refresh_token_issued_at TIMESTAMP NOT NULL` を導入。
`persistToken` は以下のロジックで値を決める:

- MF レスポンスに `refresh_token` あり (rotation 動作) → `now()`
- MF レスポンスに `refresh_token` なし (rotation OFF、旧 token 流用) → **旧 active row の値を継承**
- 旧 row なし (= 初回認可) → `now()`

これにより rotation OFF でも 540 日カウントが `add_date_time` リセットの影響を受けず正確になる。

### 9.3 期限超過 banner が出たら

#### Step 1: 画面から再認可

1. admin (`shopNo=0`) でログイン
2. top header の赤 banner「再認可画面へ」ボタンをクリック → `/finance/mf-integration` に遷移
3. 既存の Client ID / Client Secret 設定を確認 (再入力不要)
4. **「認可開始」ボタン** を押下 → MF ログイン画面が新タブで開く
5. MF にログインし、scope 同意画面で「許可」を押下 (連携先 = 既存と同じ tenant であることを確認)
6. callback で自動的に元画面に戻り、新 `access_token` + `refresh_token` を保存
7. banner が消えることを確認

#### Step 2: 確認

`/finance/mf-integration` 画面下部の token 詳細で以下を確認:

```
refresh_token 残日数: 540 日 (発行: 2026-05-06 14:23:01 / 寿命 540 日)
```

DB 直接確認:

```sql
SELECT id, client_id, refresh_token_issued_at, add_date_time, modify_date_time, del_flg
FROM t_mf_oauth_token
WHERE del_flg = '0'
ORDER BY id DESC;
```

新 row が 1 件、`refresh_token_issued_at = add_date_time ≈ now()` になっていれば成功。

### 9.4 タイミング: 60 日前から事前計画

`MfReAuthBanner` は残日数 60 日以下で予兆 banner を出す。**60 日前 / 30 日前 / 14 日前 / 7 日前** で
severity が段階的に上がるため、admin はこのタイミングを見て計画的に再認可作業を実施する。

期限超過まで放置すると Step 1 で MF 側の追加認証 (二要素認証等) が即時通せない場合に
業務 API が完全停止する時間が発生し得る。**残日数 30 日切ったら必ず再認可** を運用ルールとする。

### 9.5 トラブルシューティング

#### 「認可開始」を押しても MF 画面が開かない

- Client ID / Redirect URI がアプリポータル登録と一致しているか確認
- ブラウザのポップアップブロックを解除

#### 再認可後も banner が消えない

- ブラウザ強制リロード (Ctrl+Shift+R) で TanStack Query cache をクリア
- それでも消えなければ:
  ```sql
  SELECT refresh_token_issued_at, add_date_time
  FROM t_mf_oauth_token
  WHERE del_flg = '0';
  ```
  で `refresh_token_issued_at` が `now()` 近辺になっているか確認。古いままなら V042 適用前の row の可能性 →
  `UPDATE t_mf_oauth_token SET refresh_token_issued_at = add_date_time WHERE del_flg='0' AND refresh_token_issued_at IS NULL;`
  で修正してから再度ブラウザリロード。

#### 期限超過後 MF 側も connect 不可

MF アプリポータル (`https://app-portal.moneyforward.com/`) でアプリの認可履歴を確認。
旧認可が残っていれば revoke してから再度認可を実施。それでも通らない場合は MF サポートに問い合わせ。
