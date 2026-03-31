# 02 ダッシュボード・共通レイアウト仕様

## 目次

1. [ダッシュボード画面仕様](#1-ダッシュボード画面仕様)
   - 1.1 [画面概要](#11-画面概要)
   - 1.2 [バッチ実行パネル（上段4枚）](#12-バッチ実行パネル上段4枚)
   - 1.3 [売上推移チャート](#13-売上推移チャート)
   - 1.4 [受注から出荷の流れ（業務フローガイド）](#14-受注から出荷の流れ業務フローガイド)
2. [共通レイアウト仕様](#2-共通レイアウト仕様)
   - 2.1 [レイアウト構造](#21-レイアウト構造)
   - 2.2 [ヘッダー（トップナビゲーションバー）](#22-ヘッダートップナビゲーションバー)
   - 2.3 [サイドバー（左メニュー）](#23-サイドバー左メニュー)
   - 2.4 [フッター](#24-フッター)
3. [共通コンポーネント仕様](#3-共通コンポーネント仕様)
   - 3.1 [メッセージ・通知表示](#31-メッセージ通知表示)
   - 3.2 [日付入力ピッカー](#32-日付入力ピッカー)
   - 3.3 [ポップアップ検索ダイアログ](#33-ポップアップ検索ダイアログ)
   - 3.4 [データテーブル（一覧表示）](#34-データテーブル一覧表示)
   - 3.5 [セッションタイムアウト処理](#35-セッションタイムアウト処理)
   - 3.6 [CSRFトークン管理](#36-csrfトークン管理)
4. [ログイン画面仕様](#4-ログイン画面仕様)
5. [使用ライブラリ一覧](#5-使用ライブラリ一覧)
6. [URLパターン一覧](#6-urlパターン一覧)

---

## 1. ダッシュボード画面仕様

### 1.1 画面概要

| 項目 | 値 |
|------|-----|
| URL | `/dashboard` |
| テンプレートファイル | `src/main/resources/templates/dashboard.html` |
| コントローラー | `jp.co.oda32.app.DashboardController` |
| バッチコントローラー | `jp.co.oda32.app.DashboardBatchController` |
| レイアウト | `layout.html`（Thymeleaf Layout Dialect使用） |
| 認証 | 要ログイン（Spring Security管理） |

ダッシュボードはログイン成功後に表示される最初の画面であり、以下の2つの主要セクションで構成される。

- **上段（4列グリッド）**: バッチ実行パネル × 4個
- **下段（8:4分割）**: 売上推移チャート + 業務フローガイド

ログインユーザーの会社種別（`companyType`）によってデータのスコープが変わる。

- `admin`（管理者）: 全ショップの売上データを集計・表示
- `shop`（ショップ担当者）: 自ショップのみの売上データを表示
- `partner`（取引先担当者）: 所属ショップの売上データを表示

### 1.2 バッチ実行パネル（上段4枚）

ダッシュボード上段には横4列（Bootstrap `col-lg-3 col-md-6`）で4枚のパネルが並ぶ。各パネルはバッチ起動ボタンを持ち、Ajax非同期でジョブを実行する。

#### パネル1：新規受注取込

| 項目 | 値 |
|------|-----|
| パネル色 | 黄（`panel-yellow`） |
| アイコン | `fa-shopping-cart`（Font Awesome） |
| ボタンID | `bCartOrderImportButton` |
| ステータス表示先 | `#bCartOrderImportBatchStatusText` |
| Ajaxエンドポイント | `POST /executeBCartOrderImportBatch` |
| 対象Beanジョブ | `bCartOrderImportJobForWEB` |

**説明文**: 小田光オンライン（B-CART）から新規受注データを取り込み、SMILEへの売上明細連携ファイルを作成する。出力先: `\\Smile-srv\共有\業務内容\ネットショップ関連\連携ファイル\smileへ連携`

**ジョブパラメータ**: `JobID`（実行時刻のミリ秒値を自動付与、重複実行防止のため）

#### パネル2：売上明細取込

| 項目 | 値 |
|------|-----|
| パネル色 | 赤（`panel-red`） |
| アイコン | `fa-support`（Font Awesome） |
| ボタンID | `oneDayCloseBatchButton` |
| フォームID | `oneDayCloseBatchForm` |
| ステータス表示先 | `#oneDayCloseBatchStatusText` |
| Ajaxエンドポイント | `POST /executeOneDayCloseBatch` |
| 対象Beanジョブ | `smileOrderImportAndBCartOrderUpdateJob` |

**説明文**: SMILEから売上明細データファイルを取り込む。事前にSMILEの【随時業務 > テキスト出力（明細）> 売上明細】でファイルを出力する必要がある。

**ジョブパラメータ（フォームのhidden項目）**:

| パラメータ名 | input ID | デフォルト値 | 説明 |
|-------------|----------|-------------|------|
| `shopNo` | `shopNo` | `1` | ショップ番号 |
| `warehouseNo` | `warehouseNo` | `10` | 倉庫番号 |
| `spanMonths` | `spanMonths` | `3` | 取込対象月数 |
| `JobID` | - | 実行時刻ms | 重複実行防止 |

#### パネル3：出荷実績CSV

| 項目 | 値 |
|------|-----|
| パネル色 | ブルー（`panel-primary`） |
| アイコン | `fa-comments`（Font Awesome） |
| ボタンID | `bCartLogisticsCsvExportButton` |
| ステータス表示先 | `#bCartLogisticsCsvExportBatchStatusText` |
| Ajaxエンドポイント | `POST /executeBCartLogisticsCsvExportBatch` |
| 対象Beanジョブ | `bCartLogisticsCsvExportJobForWEB` |

**説明文**: 小田光オンライン（B-CART）に取り込む出荷実績CSVを作成する。出力先: `\\Smile-srv\共有\業務内容\ネットショップ関連\連携ファイル\小田光オンラインへ連携`

**ジョブパラメータ**: `JobID`（実行時刻のミリ秒値を自動付与）

#### パネル4：新規会員取込

| 項目 | 値 |
|------|-----|
| パネル色 | グリーン（`panel-green`） |
| アイコン | `fa-tasks`（Font Awesome） |
| ボタンID | `bCartMemberUpdateButton` |
| ステータス表示先 | `#bCartMemberUpdateButtonBatchStatusText` |
| Ajaxエンドポイント | `POST /executeBCartMemberUpdateBatch` |
| 対象Beanジョブ | `bCartMemberUpdateJobForWEB` |

**説明文**: 小田光オンラインから新規会員情報を取り込み、SMILEへ連携するファイルを生成する。出力先: `\\Smile-srv\共有\業務内容\ネットショップ関連\連携ファイル\smileへ連携`

**ジョブパラメータ**: `JobID`（実行時刻のミリ秒値を自動付与）

#### バッチ実行の共通仕様

バッチ実行ボタンのクリック→完了確認まで、以下のフローで動作する。

```
[ボタンクリック]
    ↓
ステータステキストを「実行中…」に変更
    ↓
Ajax POST → バッチ実行エンドポイント
    ↓（成功時）
レスポンスの success キーに JobExecution ID が返る
    ↓
checkJobStatus(jobId, statusTextSelector) を呼び出し
    ↓
GET /job/status/{jobId} を5秒間隔でポーリング
    ↓
COMPLETED → ステータステキストを「完了」に変更
FAILED    → ステータステキストを「実行失敗」に変更
その他    → 5秒後に再ポーリング
```

ジョブステータス確認API:

| 項目 | 値 |
|------|-----|
| エンドポイント | `GET /job/status/{jobId}` |
| コントローラー | `jp.co.oda32.batch.BatchStatusAPI` |
| レスポンス | `BatchStatus` 列挙値（COMPLETED / FAILED / UNKNOWN 等） |
| ポーリング間隔 | 5,000ms |

CSRFトークンはAjaxリクエストヘッダー（`X-CSRF-TOKEN`）に自動付与される（`commonAjax.js`の`setCsrfTokenToAjaxHeader()`関数による）。

### 1.3 売上推移チャート

ダッシュボード下段左（`col-lg-8`）に棒グラフで売上推移を表示する。

| 項目 | 値 |
|------|-----|
| チャートライブラリ | Chart.js（`/js/Chart.min.js`） |
| チャート種別 | 棒グラフ（`type: "bar"`） |
| canvas要素ID | `orderPriceTransition` |
| データソース | PostgreSQLマテリアライズドビュー `v_sales_monthly_summary` |
| Entityクラス | `jp.co.oda32.domain.model.VSalesMonthlySummary` |
| サービスクラス | `jp.co.oda32.domain.service.order.VSalesMonthlySummaryService` |

**取得期間**: サーバー側で処理時点から2年前まで（`LocalDate.now().plusMonths(1)` から2年前）

**会計期（期）の定義**: 7月始まり・翌年6月終わり

- 7月以降の場合: 当年7月が今期開始
- 7月より前の場合: 前年7月が今期開始

**横軸ラベル（12ヶ月）**:

```
7月, 8月, 9月, 10月, 11月, 12月, 1月, 2月, 3月, 4月, 5月, 6月
```

**データセット**:

| データセット | ラベル | 色 | 対象期間 |
|-------------|--------|-----|---------|
| 今期 | 「今期」 | `rgb(255, 0, 0)`（赤） | 今期7月〜現在月 |
| 前期 | 「前期」 | `rgba(100, 100, 255, 1)`（青） | 前期7月〜前期6月 |

**チャートオプション**:

- レスポンシブ対応（`responsive: true`）
- Y軸はゼロ基点（`beginAtZero: true`, `min: 0`）

**複数ショップ対応**: 管理者（`admin`）や、複数ショップを持つユーザーの場合、同月のデータを`salesTotal`で合算（Java Stream APIの`Collectors.toMap`マージ処理）して表示する。

**マテリアライズドビュー**: `v_sales_monthly_summary`は`REFRESH MATERIALIZED VIEW`で更新可能。更新は`VSalesMonthlySummaryService.refresh()`から実行される。

### 1.4 受注から出荷の流れ（業務フローガイド）

ダッシュボード下段右（`col-lg-4`）に、受注から出荷完了までの業務手順を番号付きリスト（Bootstrap `list-group`）で表示する。

| ステップ | 内容 | リンク先 |
|---------|------|---------|
| ① | 小田光オンラインに得意先から注文が入る | `https://odamitsu.i13.bcart.jp/admin/order/list`（B-CART管理画面、別タブ） |
| ② | B-CART管理画面から「新規注文」内「未発送」を確認し、在庫に問題なければ発送状況を「発送指示」に変更・納品日入力 | `https://odamitsu.i13.bcart.jp/admin/order/list`（B-CART管理画面、別タブ） |
| ③ | ダッシュボード左上の「新規受注取込」バッチを起動 | `#`（ページ内） |
| ④ | SMILEで【随時業務 > テキスト取込（明細）> 売上明細】で③のファイルを取込 | `#`（ページ内） |
| ⑤ | 必要な場合はSMILEで取り込んだ受注伝票を出力 | `#`（ページ内） |
| ⑥ | SMILEの【随時業務 > テキスト出力（明細）> 売上明細】でファイルを出力し、「売上明細取込」バッチを起動してシステムに取り込む | `#`（ページ内） |
| ⑦ | 左メニューのB-Cart出荷処理で連携済み受注の出荷ステータスを「出荷済」に変更して更新 | `#`（ページ内） |
| ⑧ | 「出荷実績CSV」バッチを起動して小田光オンラインへ出荷完了連携 | `#`（ページ内） |
| ⑨ | B-CART管理画面【受注管理 > 出荷実績インポート】から出荷実績CSVをインポート | `https://odamitsu.i13.bcart.jp/admin/logistics/csv/import`（B-CART管理画面、別タブ） |

パネル最下部に補足テキストあり: 「新規得意先が登録された場合は、Smile用得意先コードを作成して、小田光オンラインの会員＞貴社コードに得意先コードを設定します。設定後、一番右の新規会員取込バッチを起動し、生成した会員登録用ファイルをSmileへ取り込んでください。」

---

## 2. 共通レイアウト仕様

### 2.1 レイアウト構造

全画面（ログイン画面を除く）は **Thymeleaf Layout Dialect** を使用し、`layout.html` を基底レイアウトとして継承する。

```html
<html xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorator="layout">
```

**テンプレートファイル**: `src/main/resources/templates/layout.html`

HTML構造:

```
#wrapper
├── <nav>  ← サイドバー + トップナビゲーション（layout.html の th:fragment="navi"）
│   ├── .navbar（固定トップバー）
│   │   ├── .navbar-header（ブランド名リンク）
│   │   ├── .navbar-top-links（アラートドロップダウン + ユーザードロップダウン）
│   │   └── .navbar-default.sidebar（左サイドバー）
│   │       └── #side-menu（MetisMenu）
└── #page-wrapper  ← 各画面のコンテンツ領域
    └── .container-fluid
        └── （各画面の内容）
```

各コンテンツテンプレートでナビゲーションを展開する記述:

```html
<nav layout:fragment="navi" th:remove="tag"></nav>
```

### 2.2 ヘッダー（トップナビゲーションバー）

| 項目 | 値 |
|------|-----|
| CSSクラス | `navbar navbar-inverse navbar-fixed-top` |
| スタイル | 黒背景の固定トップバー |
| テーマ | Bootstrap `navbar-inverse`（ダーク） |

#### ブランドエリア

- テキスト: 「小田光」
- リンク先: `/dashboard`
- CSSクラス: `navbar-brand`

#### モバイル用トグルボタン

小画面（768px未満）では `navbar-toggle` ボタンでサイドバーを展開/折りたたむ。`data-toggle="collapse"`, `data-target=".navbar-collapse"` を使用。

#### 右上メニュー（`.navbar-right.navbar-top-links`）

**アラートドロップダウン**（`fa-bell` アイコン）:

現状はサンプルデータのみ表示（実際のアラート機能は未実装）。
- New Comment（4 minutes ago）
- 3 New Followers（12 minutes ago）
- Message Sent（4 minutes ago）
- New Task（4 minutes ago）
- Server Rebooted（4 minutes ago）
- "See All Alerts" リンク

**ユーザードロップダウン**（`fa-user` アイコン）:

ログインユーザーの会社種別によって表示名が変わる。

| 会社種別（`companyType`） | 表示内容 |
|--------------------------|---------|
| `admin` | 「管理者」 |
| `shop` | ショップ名（`company.shop.shopName`） |
| `partner` | パートナー名（`company.partner.partnerName`） |

ユーザー名（`userName`）は会社種別表示の後に表示される。

ドロップダウンメニュー項目:

| 項目 | リンク先 | アイコン |
|------|---------|---------|
| User Profile | `#`（未実装） | `fa-user` |
| ログイン情報修正 | `loginUserModifyForm` | `fa-gear` |
| 管理者作成 | `loginUserCreateForm` | `fa-gear` |
| Logout | `/logout`（Spring Securityのログアウト処理） | `fa-sign-out` |

### 2.3 サイドバー（左メニュー）

| 項目 | 値 |
|------|-----|
| CSSクラス | `.navbar-default.sidebar` |
| メニューID | `#side-menu` |
| 使用プラグイン | MetisMenu（`metisMenu.min.js`） |
| 動作 | クリックでサブメニューをアコーディオン展開 |
| アクティブ判定 | `startmin.js`がURLマッチングで `active` クラスを付与 |

#### サイドバー最上部

検索ボックス（入力欄 + 検索ボタン）が配置されているが、現状はUI表示のみ（機能未実装）。

#### メニュー階層

以下の順序でメニューが並ぶ（全て `fa-cube` または個別アイコン）:

| メニュー項目 | アイコン | サブメニュー | URL |
|------------|---------|------------|-----|
| Dashboard | `fa-dashboard` | なし | `/dashboard` |
| 見積 | `fa-cube` | あり | - |
| 　└ 見積一覧 | - | - | `/estimateList` |
| 　└ 見積作成 | - | - | `/estimateInput` |
| 商品を管理する | `fa-cube` | あり | - |
| 　└ 商品マスタ | - | - | `/goodsMaster` |
| 　└ 販売商品WORK | - | - | `/salesGoodsWork` |
| 　└ 販売商品マスタ | - | - | `/salesGoodsMaster` |
| 　└ 得意先別販売商品 | - | - | `/partnerGoodsMaster` |
| 　└ カテゴリ設定 | - | - | `/categoryMaster` |
| 注文を管理する | `fa-cube` | あり | - |
| 　└ 注文登録 | - | - | `/orderInput` |
| 　└ 受注一覧 | - | - | `/orderList` |
| 　└ 入荷待一覧 | - | - | `/backorderedList` |
| 　└ 出荷待一覧 | - | - | `/deliveryPlanList` |
| 　└ 注文履歴 | - | - | `/orderHistoryList` |
| 　└ 返品登録 | - | - | `/returnInput` |
| 　└ 請求を確認する | - | - | `/finance/invoice/list` |
| 仕入を管理する | `fa-cube` | あり | - |
| 　└ 発注警告一覧 | - | - | `/purchaseWarn` |
| 　└ 発注入力 | - | - | `/sendOrderInput` |
| 　└ 発注一覧 | - | - | `/sendOrderList` |
| 　└ 仕入入力 | - | - | `/purchaseInput` |
| 　└ 仕入一覧 | - | - | `/purchaseList` |
| 　└ 仕入価格 | - | - | `/mPurchasePriceList` |
| 　└ 仕入価格変更予定一覧 | - | - | `/purchasePriceChangeList` |
| 　└ 仕入価格変更予定作成 | - | - | `/purchasePriceChangeListInput` |
| 在庫を管理する | `fa-cube` | あり | - |
| 　└ 在庫一覧 | - | - | `/stockList` |
| 　└ 在庫履歴 | - | - | `/stockLog` |
| B-Cart出荷管理 | `fa-cube` | あり | - |
| 　└ B-Cart出荷待ち一覧 | - | - | `/bcart/shippingInputForm` |
| マスタを管理する | `fa-cube` | あり | - |
| 　└ ショップマスタ | - | - | `/shopMaster` |
| 　└ メーカーマスタ | - | - | `/makerMaster` |
| 　└ 会社マスタ | - | - | `/companyMaster` |
| 　└ 倉庫マスタ | - | - | `/warehouseMaster` |
| Charts | `fa-bar-chart-o` | あり（テンプレート残骸） | - |
| Tables | `fa-table` | なし | `tables.html`（未使用） |
| Forms | `fa-edit` | なし | `forms.html`（未使用） |
| UI Elements | `fa-wrench` | あり（テンプレート残骸） | - |
| Multi-Level Dropdown | `fa-sitemap` | あり（テンプレート残骸） | - |
| Sample Pages | `fa-files-o` | あり（テンプレート残骸） | - |

備考: Charts / Tables / Forms / UI Elements / Multi-Level Dropdown / Sample Pages はStartAdmin UIテンプレートの残骸であり、実際の業務機能としては使用していない。

#### アクティブメニューの自動強調

`startmin.js`が`window.location`を現在のURLと照合し、URLが一致するメニューアンカーに`active`クラスを付与する。親のサブメニューは`in`クラスで展開状態になる。

### 2.4 フッター

```html
<footer th:fragment="footer" class="no_print">
</footer>
```

フッターはHTMLタグのみ定義されており、現時点ではコンテンツ（表示内容）はなし。`no_print`クラスにより印刷時は非表示となる。

---

## 3. 共通コンポーネント仕様

### 3.1 メッセージ・通知表示

各一覧画面・操作完了画面では、Bootstrapのアラートコンポーネントを使用してメッセージを表示する。

**成功メッセージ（緑）**:

```html
<div class="alert alert-success" role="alert"
     th:if="${messageInfo}" th:text="${messageInfo}">
    登録完了などのメッセージ
</div>
```

Model属性名: `messageInfo`

**警告メッセージ（赤）**:

```html
<div class="alert alert-danger" role="alert"
     th:if="${messageWarning}" th:text="${messageWarning}">
    警告メッセージ
</div>
```

Model属性名: `messageWarning`

**ログイン画面のメッセージ**:

| 状況 | Model属性名 | スタイル | 表示内容 |
|------|------------|---------|---------|
| 認証失敗 | `errorMessage` | `.error-message`（赤） | 「ログインIDまたはパスワードが正しくありません。」 |
| セッションタイムアウト | `timeoutMessage` | `.timeout-message`（黄） | 「セッションがタイムアウトしました。再度ログインしてください。」 |
| タイムアウト後リダイレクト | `redirectMessage` | `.redirect-message`（青） | 「ログイン後、前回アクセスしていたページに戻ります。」 |

### 3.2 日付入力ピッカー

共通JavaScript（`commonAjax.js`）により、ページロード時に自動初期化される。

**日時入力（`.datetimepicker`クラス）**:

| 項目 | 値 |
|------|-----|
| ライブラリ | bootstrap-datetimepicker（`bootstrap-datetimepicker.min.js`） |
| フォーマット | `YYYY/MM/DD HH:mm` |
| ロケール | `ja`（日本語） |
| デフォルト日付 | 現在日時 |
| ヘッダー形式 | `YYYY年 MM月` |
| 閉じるボタン | あり（`showClose: true`） |

**日付入力のみ（`.datepicker`クラス）**:

| 項目 | 値 |
|------|-----|
| フォーマット | `YYYY/MM/DD` |
| ロケール | `ja`（日本語） |
| デフォルト日付 | なし（指定なし） |

日本語ツールチップ（共通）: 閉じる、月を選択、前月/次月、年を選択、前年/次年、日付を選択、期間選択 等

### 3.3 ポップアップ検索ダイアログ

マスタ検索はウィンドウポップアップ（`window.open`）で別ウィンドウを開いて選択する方式を採用している。

| ポップアップ種別 | トリガークラス/ID | URL | サイズ |
|----------------|----------------|-----|--------|
| 会社マスタ検索 | `.companySearch` | `/companyMasterPop?companyNo=&companyType=` | 800×600 |
| 仕入先マスタ検索 | `button.supplierSearch` | `/supplierMasterPop` | 800×600 |
| 商品マスタ検索 | `.goodsMasterSearch` | `/goodsMasterPop?janCode=` | 800×600 |
| 販売商品検索 | `.salesGoodsSearch` | `/salesGoodsPop` | 800×600 |

ポップアップウィンドウの共通オプション: `scrollbars=yes`

### 3.4 データテーブル（一覧表示）

複数の一覧画面でDataTablesライブラリを使用して、ソート・ページネーション・検索機能を実現している。

| 項目 | 値 |
|------|-----|
| ライブラリ | jQuery DataTables（`jquery.dataTables.min.js`） |
| Bootstrapテーマ | `dataTables.bootstrap.min.js` / `dataTables.bootstrap.css` |
| レスポンシブ対応 | `dataTables.responsive.css` |
| 行グループ化 | `dataTables.rowsGroup.js`（一部画面） |
| インライン編集 | `jquery.tabledit.js`（在庫一覧等） |

DataTablesを使用している主な画面:
- 在庫一覧（`/stockList`）
- 仕入一覧（`/purchaseList`）
- 注文一覧（`/orderList`）
- 請求書一覧（`/finance/invoice/list`）
- 見積一覧（`/estimateList`）

### 3.5 セッションタイムアウト処理

| 項目 | 値 |
|------|-----|
| タイムアウト時間 | 3,600秒（1時間） |
| 設定ファイル | `src/main/resources/config/application.yml` |
| フィルタークラス | `jp.co.oda32.filter.SessionTimeoutFilter` |
| 処理クラス | `jp.co.oda32.config.SessionTimeoutRedirectStrategy` |

**セッションタイムアウト検知フィルター（`SessionTimeoutFilter`）の動作**:

1. 認証済みユーザーのGETリクエスト（Ajax除く）で現在URLをセッションに保存（`LAST_ACCESSED_URL`属性）
2. ただし以下のURLは記録対象外: `/loginForm`, `/login`, `/logout`, `/error`, `/dashboard`, `/css/`, `/js/`, `/images/`, `/fonts/`, `/favicon.ico`, `/api/`, `/batch`
3. 未認証になった場合、保存されているURLを`LAST_ACCESSED_URL_BEFORE_TIMEOUT`属性に移動

**Ajaxセッションタイムアウト処理（`commonAjax.js`）**:

Ajaxリクエストで`401`ステータスが返ってきた場合:
- レスポンスJSONに`error: "Session timeout"`が含まれる場合: アラート表示後、`redirectUrl`または`/loginForm?timeout=true`にリダイレクト
- その他の401: アラート表示後、`/loginForm?timeout=true`にリダイレクト

**同時セッション制限**: 同一ユーザーの同時セッション数は1に制限（新しいログインで古いセッションを無効化）。

### 3.6 CSRFトークン管理

Spring SecurityのCSRF保護が有効になっている。

**HTMLメタタグ（`layout.html` の`<head>`内）**:

```html
<meta name="_csrf" th:content="${_csrf.token}"/>
<meta name="_csrf_header" th:content="${_csrf.headerName}"/>
```

**Ajax自動付与（`commonAjax.js` の`setCsrfTokenToAjaxHeader()`関数）**:

```javascript
var token = $("meta[name='_csrf']").attr("content");
var header = $("meta[name='_csrf_header']").attr("content");
$(document).ajaxSend(function (e, xhr, options) {
    xhr.setRequestHeader(header, token);
});
```

全Ajaxリクエストに自動的にCSRFトークンをヘッダーとして付与する。

---

## 4. ログイン画面仕様

| 項目 | 値 |
|------|-----|
| URL | `/loginForm` |
| テンプレートファイル | `src/main/resources/templates/login/loginForm.html` |
| コントローラー | `jp.co.oda32.app.login.LoginController` |
| 認証処理URL | `POST /login` |
| 認証成功時遷移先 | `/dashboard` |
| 認証失敗時遷移先 | `/loginForm?error` |
| ログアウト後遷移先 | `/loginForm` |

**入力フィールド**:

| フィールド | name属性 | type | プレースホルダー |
|-----------|---------|------|----------------|
| ログインID | `login_id` | `text` | 「ログインIDを入力してください」 |
| パスワード | `password` | `password` | 「パスワードを入力してください」 |

**パスワード暗号化**: BCrypt（`BCryptPasswordEncoder`）

**セキュリティ設定（`SecurityConfig.java`）**:

- `/loginForm` のみ認証不要（`permitAll()`）
- 静的リソース（`/favicon.ico`, `/css/**`, `/js/**`, `/images/**`, `/fonts/**`）は認証不要
- それ以外の全リクエストは認証必須

**ログアウト設定**:

- ログアウトURL: `/logout`
- セッション破棄: あり（`invalidateHttpSession(true)`）
- クッキー削除: `JSESSIONID`

---

## 5. 使用ライブラリ一覧

### サーバーサイド

| ライブラリ | バージョン（参考） | 用途 |
|-----------|----------------|------|
| Spring Boot | 2.1.1 | フレームワーク |
| Spring Security | Spring Boot依存 | 認証・認可 |
| Spring Batch | Spring Boot依存 | バッチ処理 |
| Thymeleaf | Spring Boot依存 | テンプレートエンジン |
| Thymeleaf Layout Dialect | - | レイアウト継承（`layout:decorator`） |
| JPA / Hibernate | Spring Boot依存 | DBアクセス |
| Lombok | - | Boilerplate削減 |

### クライアントサイド（静的リソース）

| ライブラリ | ファイル名 | 用途 |
|-----------|-----------|------|
| jQuery | `jquery.min.js` | DOM操作・Ajax通信 |
| Bootstrap | `bootstrap.min.js` / `bootstrap.min.css` | UIフレームワーク |
| MetisMenu | `metisMenu.min.js` / `metisMenu.min.css` | サイドバーのアコーディオンメニュー |
| Chart.js | `Chart.min.js` | ダッシュボードの棒グラフ |
| Raphaël | `raphael.min.js` | ベクターグラフィック（Chart.js補助） |
| Morris.js | `morris.min.js` / `morris.css` | グラフ描画（現在未使用） |
| Flot | `flot/*.js` / `flot-data.js` | グラフ描画（現在未使用） |
| jQuery DataTables | `dataTables/*.js` / `dataTables/*.css` | 一覧テーブルのソート・検索・ページネーション |
| bootstrap-datetimepicker | `bootstrap-datetimepicker.min.js` / `.css` | 日付・日時入力ピッカー |
| moment.js | `moment.min.js` / `moment.ja.js` | 日時フォーマット（datetimepicker依存） |
| Underscore.js | `underscore-min.js` | JavaScript ユーティリティ（Ajax系で使用） |
| Select2 | `select2.min.js` / `select2.min.css` | 拡張セレクトボックス（請求書一覧等） |
| ApexCharts | `apexcharts.min.js` | グラフ描画（現在未使用、将来用） |
| Font Awesome | `font-awesome.min.css` | アイコンフォント |
| NotoSerifCJKjp | `NotoSerifCJKjp-*.otf` | 日本語フォント（印刷用） |

### カスタムJavaScript

| ファイル | パス | 用途 |
|---------|------|------|
| `commonAjax.js` | `/js/commonAjax.js` | CSRF設定・Ajax共通処理・日付ピッカー初期化・ポップアップ検索 |
| `startmin.js` | `/js/startmin.js` | MetisMenuの初期化・サイドバーレスポンシブ対応・アクティブメニュー自動判定 |
| `dashboard.js` | `/js/dashboard.js` | バッチ実行ボタンのAjax処理・ジョブステータスポーリング |

### カスタムCSS

| ファイル | パス | 用途 |
|---------|------|------|
| `startmin.css` | `/css/startmin.css` | メインテーマCSS（StartAdmin UIカスタマイズ） |
| `timeline.css` | `/css/timeline.css` | タイムラインUIコンポーネント |
| `static.css` | `/css/static.css` | 共通スタイル補完 |
| `login_form.css` | `/css/other/login_form.css` | ログイン画面専用スタイル |

---

## 6. URLパターン一覧

### ダッシュボード関連

| URL | メソッド | コントローラー | 説明 |
|-----|---------|--------------|------|
| `/dashboard` | GET | `DashboardController#dashboard` | ダッシュボード画面表示 |
| `/executeBCartOrderImportBatch` | POST | `DashboardBatchController#executeBCartOrderImportBatch` | 新規受注取込バッチ実行 |
| `/executeOneDayCloseBatch` | POST | `DashboardBatchController#executeOneDayCloseBatch` | 売上明細取込バッチ実行 |
| `/executeBCartLogisticsCsvExportBatch` | POST | `DashboardBatchController#executeBCartLogisticsCsvExportBatch` | 出荷実績CSV出力バッチ実行 |
| `/executeBCartMemberUpdateBatch` | POST | `DashboardBatchController#executeBCartMemberUpdateBatch` | 新規会員取込バッチ実行 |
| `/job/status/{jobId}` | GET | `BatchStatusAPI#getJobStatus` | バッチジョブステータス確認 |

### 認証関連

| URL | メソッド | コントローラー | 説明 |
|-----|---------|--------------|------|
| `/loginForm` | GET | `LoginController#loginForm` | ログイン画面表示 |
| `/login` | POST | Spring Security | 認証処理 |
| `/logout` | POST | Spring Security | ログアウト処理 |

### ナビゲーション（サイドバー）

| URL | 画面名 |
|-----|--------|
| `/estimateList` | 見積一覧 |
| `/estimateInput` | 見積作成 |
| `/goodsMaster` | 商品マスタ |
| `/salesGoodsWork` | 販売商品WORK |
| `/salesGoodsMaster` | 販売商品マスタ |
| `/partnerGoodsMaster` | 得意先別販売商品 |
| `/categoryMaster` | カテゴリ設定 |
| `/orderInput` | 注文登録 |
| `/orderList` | 受注一覧 |
| `/backorderedList` | 入荷待一覧 |
| `/deliveryPlanList` | 出荷待一覧 |
| `/orderHistoryList` | 注文履歴 |
| `/returnInput` | 返品登録 |
| `/finance/invoice/list` | 請求書一覧 |
| `/purchaseWarn` | 発注警告一覧 |
| `/sendOrderInput` | 発注入力 |
| `/sendOrderList` | 発注一覧 |
| `/purchaseInput` | 仕入入力 |
| `/purchaseList` | 仕入一覧 |
| `/mPurchasePriceList` | 仕入価格 |
| `/purchasePriceChangeList` | 仕入価格変更予定一覧 |
| `/purchasePriceChangeListInput` | 仕入価格変更予定作成 |
| `/stockList` | 在庫一覧 |
| `/stockLog` | 在庫履歴 |
| `/bcart/shippingInputForm` | B-Cart出荷待ち一覧 |
| `/shopMaster` | ショップマスタ |
| `/makerMaster` | メーカーマスタ |
| `/companyMaster` | 会社マスタ |
| `/warehouseMaster` | 倉庫マスタ |

### ポップアップ・Ajax

| URL | メソッド | 説明 |
|-----|---------|------|
| `/companyMasterPop` | GET/POST | 会社マスタポップアップ |
| `/supplierMasterPop` | POST | 仕入先マスタポップアップ |
| `/goodsMasterPop` | GET | 商品マスタポップアップ |
| `/salesGoodsPop` | POST | 販売商品ポップアップ |
| `/getPartnerMapForAjax` | POST | ショップ選択時の得意先リスト取得（Ajax） |
| `/getPartnerByPartnerCodeForAjax` | POST | 得意先コードによる得意先検索（Ajax） |
| `/getWarehouseMapByCompanyNoForAjax` | POST | 会社番号による倉庫リスト取得（Ajax） |
| `/getSupplierBySupplierCodeForAjax` | POST | 仕入先コードによる仕入先検索（Ajax） |
