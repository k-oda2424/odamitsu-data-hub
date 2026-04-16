# 09. SMILE連携仕様書

## 目次

1. [概要](#1-概要)
2. [連携アーキテクチャ](#2-連携アーキテクチャ)
3. [ファイル配置・設定](#3-ファイル配置設定)
4. [受注データ連携（売上明細）](#4-受注データ連携売上明細)
   - 4.1 [CSVフォーマット定義（SmileOrderFile）](#41-csvフォーマット定義smileorderfile)
   - 4.2 [ワークテーブルエンティティ（WSmileOrderOutputFile）](#42-ワークテーブルエンティティwsmileorderoutputfile)
   - 4.3 [バッチジョブ構成](#43-バッチジョブ構成)
   - 4.4 [プロセッサーによるデータ加工ルール](#44-プロセッサーによるデータ加工ルール)
   - 4.5 [新規受注登録処理](#45-新規受注登録処理)
   - 4.6 [受注更新処理](#46-受注更新処理)
   - 4.7 [受注削除処理](#47-受注削除処理)
5. [仕入データ連携](#5-仕入データ連携)
   - 5.1 [ワークテーブルエンティティ（WSmilePurchaseOutputFile）](#51-ワークテーブルエンティティwsmilePurchaseoutputfile)
   - 5.2 [新規仕入登録処理](#52-新規仕入登録処理)
   - 5.3 [仕入更新処理](#53-仕入更新処理)
   - 5.4 [仕入削除処理](#54-仕入削除処理)
6. [支払情報連携](#6-支払情報連携)
   - 6.1 [CSVフォーマット定義（SmilePaymentFile）](#61-csvフォーマット定義smilepaymentfile)
   - 6.2 [エンティティ定義（WSmilePayment / TSmilePayment）](#62-エンティティ定義wsmilepayment--tsmilepayment)
   - 6.3 [バッチジョブ構成](#63-バッチジョブ構成)
   - 6.4 [インポートフロー詳細](#64-インポートフロー詳細)
7. [得意先マスタ連携](#7-得意先マスタ連携)
8. [リポジトリ定義](#8-リポジトリ定義)
9. [サービス定義](#9-サービス定義)
10. [定数・Enum定義](#10-定数enum定義)
11. [トランザクション管理](#11-トランザクション管理)
12. [エラーハンドリング](#12-エラーハンドリング)
13. [ファイル一覧](#13-ファイル一覧)

---

## 1. 概要

### 1.1 システム概要

SMILE（スマイル）は小田光株式会社が使用する基幹システム（ERP）である。stock-appシステムはSMILEとCSVファイル交換方式で連携し、以下のデータを双方向に同期する。

| 連携種別 | 方向 | 内容 |
|----------|------|------|
| 売上明細（受注）取込 | SMILE → stock-app | SMILE受注CSVをstock-appの注文・出荷テーブルへ登録 |
| 仕入データ取込 | SMILE → stock-app | SMILE仕入CSVをstock-appの仕入テーブルへ登録 |
| 支払情報取込 | SMILE → stock-app | SMILE支払CSVをt_smile_paymentへUPSERT |
| 得意先マスタ取込 | SMILE → stock-app | SMILE得意先CSVをm_partnerへ同期 |

### 1.2 連携方式

- **ファイル形式**: CSV（カンマ区切り）
- **文字エンコーディング**: Unicode（UTF-16LE with BOM）
- **ファイル取得方法**: SMILEが所定ディレクトリに出力、バッチがファイルを読み込む
- **処理パターン**: ワークテーブル（`w_*`）に一括取込後、本テーブル（`t_*`）へUPSERT

---

## 2. 連携アーキテクチャ

```
SMILE（基幹システム）
    │
    │ CSVファイル出力
    ▼
入力ディレクトリ（input/）
    │
    │ バッチ読み込み（FlatFileItemReader / MultiResourceItemReader）
    ▼
CSVフォーマットクラス
（SmileOrderFile / ExtPurchaseFile / SmilePaymentFile / PartnerFile）
    │
    │ ItemProcessor（データ加工・バリデーション）
    ▼
ワークテーブル（w_smile_order_output_file / w_smile_purchase_output_file / w_smile_payment）
    │
    │ Tasklet（新規・更新・削除の振り分け）
    ▼
本テーブル（t_order / t_delivery / t_purchase / t_smile_payment）
    │
    │ 後処理（在庫割当・受注ステータス更新・月次集計更新）
    ▼
在庫・売上集計テーブル
```

---

## 3. ファイル配置・設定

### 3.1 バッチ設定ファイル

**パス**: `src/main/resources/config/application-batch.yml`

```yaml
spring:
  batch:
    job:
      enabled: false  # 明示的にSpringBatchアプリケーションが実行する
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQL95Dialect
  datasource:
    hikari:
      auto-commit: false

batch:
  accounts-receivable:
    invoice-amount-tolerance: 5
```

### 3.2 店舗別ファイルパス設定（MShopLinkedFile）

**テーブル**: `m_shop_linked_file`

| フィールド名 | カラム名 | 型 | 説明 |
|-------------|---------|-----|------|
| smileOrderInputFileName | smile_order_input_file_name | String | SMILE受注CSVの入力ファイルパス |
| smilePurchaseFileName | smile_purchase_file_name | String | SMILE仕入CSVのファイルパス |
| smileOrderOutputFileName | smile_order_output_file_name | String | SMILE受注出力ファイルパス |
| smilePartnerOutputFileName | smile_partner_output_file_name | String | SMILE得意先出力ファイルパス |
| smileDestinationOutputFileName | smile_destination_output_file_name | String | SMILE届け先出力ファイルパス |
| smileGoodsImportFileName | smile_goods_import_file_name | String | SMILE商品取込ファイルパス |

複数店舗の入力ファイルは`MShopLinkedFileService.getSmileOrderInputFileNames()`で取得し、`MultiResourceItemReader`で複数ファイルを一括処理する。

### 3.3 デフォルト入力ファイル

- **受注CSV**: `input/smile_order_import.csv`（クラスパス相対）
- **得意先CSV**: `input/partner_import.csv`（クラスパス相対、`ClassLoader.getSystemResource()`で取得）
- **支払CSV**: ジョブパラメータ `inputFile` で実行時指定

---

## 4. 受注データ連携（売上明細）

### 4.1 CSVフォーマット定義（SmileOrderFile）

**クラス**: `jp.co.oda32.batch.smile.SmileOrderFile`
**メソッド**: `SmileOrderFile.getSmileOrderFileFormat()`（String[]を返す）

CSVの全155カラムを以下に定義する。カラム順はインデックス0から始まる。

| インデックス | カラム名 | Javaフィールド型 | 備考 |
|------------|---------|---------------|------|
| 0 | 伝票日付 | String | "yyyyMMdd"形式、get時にLocalDateへ変換（ResolverStyle.STRICT） |
| 1 | 年月度 | String | |
| 2 | 伝票番号 | BigDecimal | get時にtoString()で文字列変換 |
| 3 | 処理連番 | Long | |
| 4 | 明細区分 | BigDecimal | get時にtoString()で文字列変換 |
| 5 | 明細区分名 | String | |
| 6 | 行 | Integer | |
| 7 | 得意先コード | String | |
| 8 | 得意先名1 | String | |
| 9 | 得意先名2 | String | |
| 10 | 得意先名略称 | String | |
| 11 | 得意先営業所コード | String | shopNo決定に使用 |
| 12 | 得意先営業所名 | String | |
| 13 | 得意先部門コード | String | |
| 14 | 得意先部門名 | String | |
| 15 | 得意先地区コード | String | |
| 16 | 得意先地区名 | String | |
| 17 | 得意先業種コード | String | |
| 18 | 得意先業種名 | String | |
| 19 | 得意先グループコード | String | |
| 20 | 得意先グループ名 | String | |
| 21 | 得意先単価ランクコード | String | |
| 22 | 得意先単価ランク名 | String | |
| 23 | 得意先分類6コード | String | |
| 24 | 得意先分類6名 | String | |
| 25 | 得意先分類7コード | String | |
| 26 | 得意先分類7名 | String | |
| 27 | 得意先分類8コード | String | |
| 28 | 得意先分類8名 | String | |
| 29 | 得意先分類9コード | String | |
| 30 | 得意先分類9名 | String | |
| 31 | 請求先コード | String | |
| 32 | 請求先名 | String | |
| 33 | 請求先営業所コード | String | |
| 34 | 請求先営業所名 | String | |
| 35 | 請求先部門コード | String | |
| 36 | 請求先部門名 | String | |
| 37 | 請求先地区コード | String | |
| 38 | 請求先地区名 | String | |
| 39 | 請求先業種コード | String | |
| 40 | 請求先業種名 | String | |
| 41 | 請求先グループコード | String | |
| 42 | 請求先グループ名 | String | |
| 43 | 請求先単価ランクコード | String | |
| 44 | 請求先単価ランク名 | String | |
| 45 | 請求先分類6コード | String | |
| 46 | 請求先分類6名 | String | |
| 47 | 請求先分類7コード | String | |
| 48 | 請求先分類7名 | String | |
| 49 | 請求先分類8コード | String | |
| 50 | 請求先分類8名 | String | |
| 51 | 請求先分類9コード | String | |
| 52 | 請求先分類9名 | String | |
| 53 | 納品先コード | String | |
| 54 | 納品先名 | String | TDelivery.destinationNameにセット |
| 55 | 担当者コード | String | |
| 56 | 担当者名 | String | |
| 57 | 担当者分類0コード | String | |
| 58 | 担当者分類0名 | String | |
| 59 | 担当者分類1コード | String | |
| 60 | 担当者分類1名 | String | |
| 61 | 担当者分類2コード | String | |
| 62 | 担当者分類2名 | String | |
| 63 | 担当者分類3コード | String | |
| 64 | 担当者分類3名 | String | |
| 65 | 担当者分類4コード | String | |
| 66 | 担当者分類4名 | String | |
| 67 | 担当者分類5コード | String | |
| 68 | 担当者分類5名 | String | |
| 69 | 担当者分類6コード | String | |
| 70 | 担当者分類6名 | String | |
| 71 | 担当者分類7コード | String | |
| 72 | 担当者分類7名 | String | |
| 73 | 担当者分類8コード | String | |
| 74 | 担当者分類8名 | String | |
| 75 | 担当者分類9コード | String | |
| 76 | 担当者分類9名 | String | |
| 77 | 請求 | BigDecimal | |
| 78 | 請求区分名 | String | |
| 79 | 売掛区分 | BigDecimal | get時にtoString()変換、TOrder.paymentMethodにセット |
| 80 | 売掛区分名 | String | |
| 81 | 取引区分 | BigDecimal | |
| 82 | 取引区分名 | String | |
| 83 | 取引区分属性 | BigDecimal | |
| 84 | 取引区分属性名 | String | |
| 85 | 商品コード | String | |
| 86 | 商品名 | String | |
| 87 | メーカーコード | String | |
| 88 | メーカー名 | String | |
| 89 | 商品分類コード | String | |
| 90 | 商品分類名 | String | |
| 91 | 商品分類2コード | String | |
| 92 | 商品分類2名 | String | |
| 93 | 商品分類3コード | String | |
| 94 | 商品分類3名 | String | |
| 95 | 商品分類4コード | String | |
| 96 | 商品分類4名 | String | |
| 97 | 商品分類5コード | String | |
| 98 | 商品分類5名 | String | |
| 99 | 商品分類6コード | String | |
| 100 | 商品分類6名 | String | |
| 101 | 商品分類7コード | String | |
| 102 | 商品分類7名 | String | |
| 103 | 商品分類8コード | String | |
| 104 | 商品分類8名 | String | |
| 105 | 商品分類9コード | String | |
| 106 | 商品分類9名 | String | |
| 107 | 入数 | BigDecimal | 1未満の場合は1に補正 |
| 108 | 個数 | BigDecimal | ケース注文数量 |
| 109 | 個数単位 | String | |
| 110 | 数量 | BigDecimal | 0の場合は1に補正（警告ログ出力） |
| 111 | 数量単位 | String | |
| 112 | 単価 | BigDecimal | 0かつ金額あり→金額÷数量で算出（ROUND_HALF_UP, 2桁） |
| 113 | 金額 | BigDecimal | |
| 114 | 原単価 | BigDecimal | TOrderDetail.purchasePriceにセット |
| 115 | 原価金額 | BigDecimal | |
| 116 | 粗利 | BigDecimal | |
| 117 | 単価掛率 | BigDecimal | |
| 118 | 課税区分 | BigDecimal | get時にtoString()変換 |
| 119 | 課税区分名 | String | |
| 120 | 消費税率 | BigDecimal | |
| 121 | 内消費税等 | BigDecimal | |
| 122 | 行摘要コード | String | |
| 123 | 行摘要1 | String | |
| 124 | 行摘要2 | String | |
| 125 | 備考コード | String | "00010"の場合は直送フラグをtrue |
| 126 | 備考 | String | |
| 127 | ログインＩＤ | String | |
| 128 | ログイン名 | String | |
| 129 | 操作日付 | String | |
| 130 | 受注番号 | BigDecimal | |
| 131 | 受注行 | BigDecimal | |
| 132 | オーダー番号 | String | |
| 133 | 見積処理連番 | BigDecimal | |
| 134 | 見積行 | BigDecimal | |
| 135 | 自動生成区分 | BigDecimal | |
| 136 | 自動生成区分名 | String | |
| 137 | 伝票消費税計算区分 | BigDecimal | |
| 138 | 伝票消費税計算区分名 | String | |
| 139 | データ発生区分 | BigDecimal | |
| 140 | 相手処理連番 | BigDecimal | |
| 141 | 入力パターン番号 | String | |
| 142 | 入力パターン名 | String | |
| 143 | 不使用伝票番号 | String | |
| 144 | 相手伝票番号 | String | |
| 145 | コード | String | |
| 146 | 不使用課税区分 | String | |
| 147 | 不使用コード | String | |
| 148 | 直送区分 | String | |
| 149 | 社店コード | String | |
| 150 | 分類コード | String | |
| 151 | 伝票区分 | String | |
| 152 | 取引先コード | String | |
| 153 | 売単価 | BigDecimal | |
| 154 | 相手商品コード | String | |
| 155 | チェックマーク区分 | BigDecimal | |
| 156 | チェックマーク区分名 | String | |
| 157 | 消費税分類 | Integer | |
| 158 | 消費税分類名 | String | |

**補足フィールド**（CSVカラム外、処理中にセット）:
- `shopNo` (int): プロセッサーが伝票番号・営業所コードから決定

### 4.2 ワークテーブルエンティティ（WSmileOrderOutputFile）

**クラス**: `jp.co.oda32.domain.model.smile.WSmileOrderOutputFile`
**テーブル名**: `w_smile_order_output_file`
**実装インターフェース**: `ISmileGoodsFile`

#### 複合主キー（@IdClass）

**クラス**: `jp.co.oda32.domain.model.embeddable.WSmileOrderOutputFilePK`

| フィールド名 | カラム名 | 型 | 説明 |
|------------|---------|-----|------|
| shoriRenban | shori_renban | Long | 処理連番 |
| gyou | gyou | Integer | 行番号 |
| shopNo | shop_no | Integer | 店舗番号（旧松山事業所と伝票番号が重複するため） |

#### 全フィールド定義

| フィールド名 | カラム名 | 型 | 説明 |
|------------|---------|-----|------|
| shoriRenban | shori_renban | Long | 処理連番（PK） |
| gyou | gyou | Integer | 行（PK） |
| shopNo | shop_no | Integer | 店舗番号（PK） |
| meisaikubun | meisaikubun | String | 明細区分 |
| meisaikubunMei | meisaikubun_mei | String | 明細区分名 |
| denpyouHizuke | denpyou_hizuke | LocalDate | 伝票日付 |
| nengetsudo | nengetsudo | String | 年月度 |
| denpyouBangou | denpyou_bangou | String | 伝票番号 |
| tokuisakiCode | tokuisaki_code | String | 得意先コード |
| tokuisakiMei1 | tokuisaki_mei1 | String | 得意先名1 |
| tokuisakiMei2 | tokuisaki_mei2 | String | 得意先名2 |
| tokuisakiRyakushou | tokuisaki_ryakushou | String | 得意先名略称 |
| tokuisakiEigyoshoCode | tokuisaki_eigyosho_code | String | 得意先営業所コード |
| tokuisakiEigyoshoMei | tokuisaki_eigyosho_mei | String | 得意先営業所名 |
| tokuisakiBumonCode | tokuisaki_bumon_code | String | 得意先部門コード |
| tokuisakiBumonMei | tokuisaki_bumon_mei | String | 得意先部門名 |
| tokuisakiChikuCode | tokuisaki_chiku_code | String | 得意先地区コード |
| tokuisakiChikuMei | tokuisaki_chiku_mei | String | 得意先地区名 |
| tokuisakiGyoushuCode | tokuisaki_gyoushu_code | String | 得意先業種コード |
| tokuisakiGyoushuMei | tokuisaki_gyoushu_mei | String | 得意先業種名 |
| tokuisakiGroupCode | tokuisaki_group_code | String | 得意先グループコード |
| tokuisakiGroupMei | tokuisaki_group_mei | String | 得意先グループ名 |
| tokuisakiTankaRankCode | tokuisaki_tanka_rank_code | String | 得意先単価ランクコード |
| tokuisakiTankaRankMei | tokuisaki_tanka_rank_mei | String | 得意先単価ランク名 |
| seikyusakiCode | seikyusaki_code | String | 請求先コード |
| seikyusakiMei | seikyusaki_mei | String | 請求先名 |
| seikyusakiEigyoshoCode | seikyusaki_eigyosho_code | String | 請求先営業所コード |
| seikyusakiEigyoshoMei | seikyusaki_eigyosho_mei | String | 請求先営業所名 |
| seikyusakiBumonCode | seikyusaki_bumon_code | String | 請求先部門コード |
| seikyusakiBumonMei | seikyusaki_bumon_mei | String | 請求先部門名 |
| seikyusakiChikuCode | seikyusaki_chiku_code | String | 請求先地区コード |
| seikyusakiChikuMei | seikyusaki_chiku_mei | String | 請求先地区名 |
| seikyusakiGyoushuCode | seikyusaki_gyoushu_code | String | 請求先業種コード |
| seikyusakiGyoushuMei | seikyusaki_gyoushu_mei | String | 請求先業種名 |
| nouhinSakiCode | nouhin_saki_code | String | 納品先コード |
| nouhinSakiMei | nouhin_saki_mei | String | 納品先名 |
| tantoushaCode | tantousha_code | String | 担当者コード |
| tantoushaMei | tantousha_mei | String | 担当者名 |
| tantoushaBunrui0Code | tantousha_bunrui0_code | String | 担当者分類0コード |
| tantoushaBunrui0Mei | tantousha_bunrui0_mei | String | 担当者分類0名 |
| tantoushaBunrui1Code | tantousha_bunrui1_code | String | 担当者分類1コード |
| tantoushaBunrui1Mei | tantousha_bunrui1_mei | String | 担当者分類1名 |
| urikakeKubun | urikake_kubun | String | 売掛区分（TOrder.paymentMethodへ） |
| shouhinCode | shouhin_code | String | 商品コード |
| shouhinMei | shouhin_mei | String | 商品名 |
| makerCode | maker_code | String | メーカーコード |
| makerMei | maker_mei | String | メーカー名 |
| torihikiKubun | torihiki_kubun | BigDecimal | 取引区分 |
| torihikiKubunMei | torihiki_kubun_mei | String | 取引区分名 |
| shouhinBunruiCode | shouhin_bunrui_code | String | 商品分類コード |
| shouhinBunruiMei | shouhin_bunrui_mei | String | 商品分類名 |
| shouhinBunrui2Code | shouhin_bunrui2_code | String | 商品分類2コード |
| shouhinBunrui2Mei | shouhin_bunrui2_mei | String | 商品分類2名 |
| shouhinBunrui3Code | shouhin_bunrui3_code | String | 商品分類3コード |
| shouhinBunrui3Mei | shouhin_bunrui3_mei | String | 商品分類3名 |
| shouhinBunrui4Code | shouhin_bunrui4_code | String | 商品分類4コード |
| shouhinBunrui4Mei | shouhin_bunrui4_mei | String | 商品分類4名 |
| shouhinBunrui5Code | shouhin_bunrui5_code | String | 商品分類5コード |
| shouhinBunrui5Mei | shouhin_bunrui5_mei | String | 商品分類5名 |
| shouhinBunrui6Code | shouhin_bunrui6_code | String | 商品分類6コード |
| shouhinBunrui6Mei | shouhin_bunrui6_mei | String | 商品分類6名 |
| shouhinBunrui7Code | shouhin_bunrui7_code | String | 商品分類7コード |
| shouhinBunrui7Mei | shouhin_bunrui7_mei | String | 商品分類7名 |
| shouhinBunrui8Code | shouhin_bunrui8_code | String | 商品分類8コード |
| shouhinBunrui8Mei | shouhin_bunrui8_mei | String | 商品分類8名 |
| shouhinBunrui9Code | shouhin_bunrui9_code | String | 商品分類9コード |
| shouhinBunrui9Mei | shouhin_bunrui9_mei | String | 商品分類9名 |
| irisu | irisu | BigDecimal | 入数 |
| kosuu | kosuu | BigDecimal | 個数 |
| kosuuTanni | kosuu_tanni | String | 個数単位 |
| suuryou | suuryou | BigDecimal | 数量（TOrderDetail.orderNumへ） |
| suuryouTanni | suuryou_tanni | String | 数量単位 |
| tanka | tanka | BigDecimal | 単価（TOrderDetail.goodsPriceへ） |
| kingaku | kingaku | BigDecimal | 金額 |
| genTanka | gen_tanka | BigDecimal | 原単価（TOrderDetail.purchasePriceへ） |
| genkaKingaku | genka_kingaku | BigDecimal | 原価金額 |
| arari | arari | BigDecimal | 粗利 |
| tankaKakuritsu | tanka_kakuritsu | BigDecimal | 単価掛率 |
| kazeiKubun | kazei_kubun | String | 課税区分（TOrderDetail.taxTypeへ） |
| kazeiKubunMei | kazei_kubun_mei | String | 課税区分名 |
| shouhizeiritsu | shouhizeiritsu | BigDecimal | 消費税率（TOrderDetail.taxRateへ） |
| naiShouhizeiEtc | nai_shouhizei_etc | BigDecimal | 内消費税等 |
| gyouTekiyouCode | gyou_tekiyou_code | String | 行摘要コード |
| gyouTekiyou1 | gyou_tekiyou1 | String | 行摘要1 |
| gyouTekiyou2 | gyou_tekiyou2 | String | 行摘要2 |
| bikoCode | biko_code | String | 備考コード（"00010"で直送フラグ） |
| biko | biko | String | 備考 |
| loginId | login_id | String | ログインID |
| loginMei | login_mei | String | ログイン名 |
| sousaHizuke | sousa_hizuke | String | 操作日付 |
| juchuuBangou | juchuu_bangou | BigDecimal | 受注番号 |
| juchuuGyou | juchuu_gyou | BigDecimal | 受注行 |
| orderBangou | order_bangou | String | オーダー番号 |
| mitumoriShoriRenban | mitumori_shori_renban | BigDecimal | 見積処理連番 |
| mitumoriGyou | mitumori_gyou | BigDecimal | 見積行 |
| jidouSeiseiKubun | jidou_seisei_kubun | BigDecimal | 自動生成区分 |
| jidouSeiseiKubunMei | jidou_seisei_kubun_mei | String | 自動生成区分名 |
| denpyouShouhizeiKeisanKubun | denpyou_shouhizei_keisan_kubun | BigDecimal | 伝票消費税計算区分 |
| denpyouShouhizeiKeisanKubunMei | denpyou_shouhizei_keisan_kubun_mei | String | 伝票消費税計算区分名 |
| dataHasseiKubun | data_hassei_kubun | BigDecimal | データ発生区分 |
| aiteShoriRenban | aite_shori_renban | BigDecimal | 相手処理連番 |
| nyuuryokuPatternBangou | nyuuryoku_pattern_bangou | String | 入力パターン番号 |
| nyuuryokuPatternMei | nyuuryoku_pattern_mei | String | 入力パターン名 |
| fushiyouDenpyouBangou | fushiyou_denpyou_bangou | String | 不使用伝票番号 |
| aiteDenpyouBangou | aite_denpyou_bangou | String | 相手伝票番号 |
| code | code | String | コード |
| fushiyouKazeiKubun | fushiyou_kazei_kubun | String | 不使用課税区分 |
| fushiyouCode | fushiyou_code | String | 不使用コード |
| chokusouKubun | chokusou_kubun | String | 直送区分 |
| shotenCode | shoten_code | String | 社店コード |
| bunruiCode | bunrui_code | String | 分類コード |
| denpyouKubun | denpyou_kubun | String | 伝票区分 |
| torihikiCode | torihiki_code | String | 取引先コード |
| uriTanka | uri_tanka | BigDecimal | 売単価 |
| aiteShouhinCode | aite_shouhin_code | String | 相手商品コード |
| chekkumakuKubun | chekkumaku_kubun | BigDecimal | チェックマーク区分 |
| chekkumakuKubunMei | chekkumaku_kubun_mei | String | チェックマーク区分名 |
| shouhizeiBunrui | shouhizei_bunrui | Integer | 消費税分類 |
| shouhizeiBunruiMei | shouhizei_bunrui_mei | String | 消費税分類名 |

### 4.3 バッチジョブ構成

**ジョブ名**: `smileOrderFileImport`
**Bean名**: `smileOrderFileImportJob`
**設定クラス**: `jp.co.oda32.batch.smile.config.SmileOrderFileImportConfig`
**エントリポイントクラス**: `jp.co.oda32.SmileOrderFileImportBatch`

#### ステップフロー

```
smileOrderFileImportStep (Chunk=500)
    ├── Reader:    SmileOrderFileReader
    ├── Processor: SmileOrderFileProcessor
    └── Writer:    SmileOrderFileWriter
         ↓
stockAllocateStep (Tasklet)
    └── StockAllocateTasklet
         ↓
orderStatusUpdateStep (Tasklet)
    └── OrderStatusUpdateTasklet
         ↓
shopAppropriateStockCalculateStep (Tasklet)
    └── ShopAppropriateStockCalculateTasklet
         ↓
vSalesMonthlySummaryRefreshStep (Tasklet)
    └── VSalesMonthlySummaryRefreshTasklet
         ↓
fileMoveStep (Tasklet)
    └── FileManagerTasklet（処理済みファイルを移動）
```

#### Chunk処理設定

| 項目 | 値 |
|------|-----|
| チャンクサイズ | 500 |
| フォールトトレラント | なし |
| リスナー | ExitStatusChangeListener |
| RunIdIncrementer | あり（同一パラメータの重複実行を回避） |

#### Reader（SmileOrderFileReader）

- `MShopLinkedFileService`から店舗別の入力ファイルパス一覧を取得
- `MultiResourceItemReader`で複数CSVファイルを順次処理
- `FlatFileItemReader`の設定:
  - エンコーディング: `"Unicode"`（UTF-16LE with BOM）
  - ヘッダースキップ: `setLinesToSkip(1)`
  - トークナイザー: `DelimitedLineTokenizer`（デフォルト区切り文字カンマ）
  - マッパー: `BeanWrapperFieldSetMapper`（`setDistanceLimit(0)`で曖昧一致無効化）
  - フィールド名: `SmileOrderFile.getSmileOrderFileFormat()`の戻り値を使用

#### Writer（SmileOrderFileWriter）

- プロセッサーから受け取った`SmileOrderFile`リストを`WSmileOrderOutputFile`に変換
- `WSmileOrderOutputFileService.save()`で一括保存（`saveAll`）
- ワークテーブルに存在しない場合はINSERT、存在する場合はUPDATE

### 4.4 プロセッサーによるデータ加工ルール

**クラス**: `jp.co.oda32.batch.smile.SmileOrderFileProcessor`

| ルール | 条件 | 処理 |
|--------|------|------|
| shopNo決定 | 伝票番号が8桁かつ"8"または"9"で始まる | `OfficeShopNo.B_CART_ORDER` |
| shopNo決定 | 得意先営業所コードでOfficeCode.DAINI | `OfficeShopNo.DAINI` |
| shopNo決定 | 得意先営業所コードでOfficeCode.CLEAN_LABO | `OfficeShopNo.CLEAN_LABO` |
| shopNo決定 | 得意先営業所コードでOfficeCode.DAIICHI | `OfficeShopNo.DAIICHI` |
| shopNo決定 | 得意先営業所コードでOfficeCode.INNER_PURCHASE | `OfficeShopNo.INNER_PURCHASE` |
| shopNo決定 | 得意先営業所コードがnull/不明 | `OfficeShopNo.INNER_ORDER` |
| 手打ち得意先 | 得意先コード = "999999" | 得意先名1のMD5ハッシュ（16進数）を得意先コードにセット |
| 社内売掛スキップ | shopNo = DAINI かつ 得意先コード = "910005" | null返却（スキップ） |
| 消費税行スキップ | 明細区分 = SmileMeisaiKubun.TAX | null返却（スキップ） |
| 数量補正 | 数量 = 0 | 数量を1にセット（WARNログ） |
| 手打ち商品 | 商品コード = "99999999" | 商品名のMD5ハッシュを商品コードにセット、商品名に"（手入力）"プレフィックス付加 |
| 入数補正 | 入数 < 1 | 入数を1にセット |
| 単価算出 | 単価 = 0 かつ 金額 ≠ 0 | 金額 ÷ 数量（絶対値）、ROUND_HALF_UP, 小数点2桁 |
| 仕入原価警告 | 原単価 ≤ 0 | WARNログのみ（スキップしない） |

### 4.5 新規受注登録処理

**クラス**: `jp.co.oda32.batch.smile.NewSmileOrderProcessor`
**呼び出し元**: `SmileOrderImportTasklet.execute()`

#### 新規対象の判定クエリ（WSmileOrderOutputFileRepository.findNewOrders）

```sql
SELECT wsoof.* FROM w_smile_order_output_file wsoof
LEFT JOIN t_delivery_detail td
ON wsoof.shori_renban = td.processing_serial_number
AND wsoof.gyou = td.order_detail_no
AND td.shop_no = wsoof.shop_no
WHERE td.processing_serial_number IS NULL
ORDER BY wsoof.shop_no, wsoof.shori_renban
```

`t_delivery_detail`に`processing_serial_number`が存在しないレコードが新規対象。

#### 登録処理フロー（@Transactional(REQUIRES_NEW)）

1. **得意先処理（partnerProcess）**
   - 得意先コードで`MPartner`を検索
   - 存在しない場合: `MPartner` + `MCompany`を新規登録
   - 存在する場合: スキップ（更新はSmileOrderUpdateServiceが担当）

2. **商品処理（goodsProcess）**
   - 商品コードで`MGoods`を検索
   - 存在しない場合: `MGoods`を新規登録

3. **TOrder生成**
   - `processingSerialNumber` = `shoriRenban`
   - `shopNo` = CSVから取得
   - `companyNo` = 得意先の`companyNo`
   - `orderStatus` = `OrderStatus.RECEIPT`（受注）
   - `orderDateTime` = `denpyouHizuke`（伝票日付）から変換
   - `paymentMethod` = `urikakeKubun`（売掛区分）
   - `partnerNo` = 得意先の`partnerNo`
   - `partnerCode` = `tokuisakiCode`

4. **TOrderDetail生成**
   - `goodsNo` = 商品マスタの`goodsNo`
   - `goodsCode` = `shouhinCode`
   - `orderNum` = `suuryou`（数量）
   - `goodsPrice` = `tanka`（単価）
   - `taxRate` = `shouhizeiritsu`（消費税率）
   - `taxType` = `kazeiKubun`（課税区分）
   - `purchasePrice` = `genTanka`（原単価）

5. **TDelivery生成**
   - `directShippingFlg` = `bikoCode == "00010"`
   - `slipNo` = `denpyouBangou`（伝票番号）
   - `destinationName` = `nouhinSakiMei`（納品先名）

6. **TDeliveryDetail生成**
   - `processingSerialNumber` = `shoriRenban`
   - `deliveryDetailStatus`:
     - `denpyouHizuke`が過去日付 → `DeliveryDetailStatus.DELIVERED`（発送済）
     - `denpyouHizuke`が未来日付 → `DeliveryDetailStatus.WAIT_SHIPPING`（発送待ち）

7. **合計計算**
   - `TOrder.totalAmount` = 全`TOrderDetail`の`goodsPrice × orderNum`合計
   - `TDelivery.totalAmount` = 出荷詳細の合計

### 4.6 受注更新処理

**クラス**: `jp.co.oda32.batch.smile.UpdateSmileOrderProcessor`
**サービス**: `jp.co.oda32.batch.smile.SmileOrderUpdateService`

#### 更新対象の判定クエリ（WSmileOrderOutputFileRepository.findModifiedOrders）

以下のいずれかが変更された場合に更新対象となる:
- `delivery_num` ≠ `suuryou`（数量）
- `goods_code` ≠ `shouhin_code`（商品コード）
- `slip_date` ≠ `denpyou_hizuke`（伝票日付）
- `goods_name` ≠ `shouhin_mei`（商品名）
- `goods_price` ≠ `tanka`（商品単価）
- `tax_rate` ≠ `shouhizeiritsu`（消費税率）
- `tax_type` ≠ `kazei_kubun`（課税区分）
- `partner_code` ≠ `tokuisaki_code`（得意先コード）

#### 更新処理（@Transactional(REQUIRES_NEW)）

- **TOrderDetail更新**: `shopNo`, `orderNum`, `goodsCode`, `goodsName`, `goodsPrice`, `taxRate`, `taxType`を差分更新
- **TDelivery更新**: `slipDate`を差分更新
- **TOrder更新**: `partnerCode`が変更された場合、`partnerNo`, `companyNo`, `companyName`を連動更新し、全明細の`companyNo`も更新
- **合計再計算**: `calculateTotalAmount()`を呼び出して合計金額を再算出

#### 得意先情報更新（updatePartner）

- 得意先名（`partnerName`）に差異がある場合に更新
- 得意先名略称（`abbreviatedPartnerName`）に差異がある場合に更新
- 最終注文日（`lastOrderDate`）を更新

### 4.7 受注削除処理

**クラス**: `jp.co.oda32.batch.smile.DeleteSmileOrderProcessor`
**サービス**: `jp.co.oda32.batch.smile.SmileOrderDeletionProcessor`

#### 削除対象の判定

`t_delivery_detail`にデータがあるが`w_smile_order_output_file`に対応レコードが存在しない出荷明細を検索。削除対象は伝票日付の範囲（CSVに含まれる最小・最大日付）内のレコードに限定。

#### 削除処理（deleteOrder）

物理削除の順序（外部キー制約を考慮）:
1. `t_delivery_detail`（出荷明細）
2. `t_order_detail`（注文明細）
3. `t_delivery`（出荷ヘッダー）
4. `t_order`（注文ヘッダー）

削除後、`t_order`および`t_delivery`の合計金額を再計算。

---

## 5. 仕入データ連携

**バッチジョブ**: `purchaseFileImport`
**設定クラス**: `jp.co.oda32.batch.purchase.config.PurchaseFileImportConfig`
**エントリポイント**: `jp.co.oda32.PurchaseFileImportBatch`

CSVフォーマットは`ExtPurchaseFile`クラスで定義されており、フィールド数はSmileOrderFileと同様に多数ある。仕入CSVはSMILEの仕入明細モジュールから出力される。

### 事業部別CSV（重要な運用前提）

SMILE は第1事業部／第2事業部でシステム分離されており、仕入CSVが2本独立に生成される:

| shop_no | ファイル例 | shori_renban 番号帯 | 備考 |
|---|---|---|---|
| 1 | `purchase_import.csv` | 330000台 | 第1事業部 SMILE |
| 2 | `purchase_import2_YYYYMMDD.csv` | 80000台 | 第2事業部 SMILE |

`m_shop_linked_file.smile_purchase_file_name` に登録された全ファイルを `ShopNoAwareItemReader` が
順次取込。shori_renban は shop_no が違えば衝突しても別伝票扱い（実質ユニークキー =
`ext_purchase_no + shop_no`）。

**ShopNoAwareItemReader のバグ修正（履歴）**: 旧実装は `resources[]` 配列の先頭から検索していたため、
複数ファイル取込時に全行が先頭ファイルの shop_no になるバグがあった。現在は `setDelegate()` を
override して `setResource()` フックで現在リソースを追跡し、1行ごとに正しい shop_no を付与する
（詳細: `DD_04_仕入管理.md` §6.2）。

### 5.1 ワークテーブルエンティティ（WSmilePurchaseOutputFile）

**クラス**: `jp.co.oda32.domain.model.smile.WSmilePurchaseOutputFile`
**テーブル名**: `w_smile_purchase_output_file`
**実装インターフェース**: `ISmileGoodsFile`

#### 複合主キー（@IdClass）

**クラス**: `jp.co.oda32.domain.model.embeddable.WSmilePurchaseOutputFilePK`

| フィールド名 | カラム名 | 型 | 説明 |
|------------|---------|-----|------|
| shoriRenban | shori_renban | Long | 処理連番 |
| gyou | gyou | Integer | 行番号 |
| shopNo | shop_no | Integer | 店舗番号 |
| meisaikubun | meisaikubun | int | 明細区分（PK含む） |

#### 全フィールド定義

**基本情報**

| フィールド名 | カラム名 | 型 | 説明 |
|------------|---------|-----|------|
| shoriRenban | shori_renban | Long | 処理連番（PK） |
| gyou | gyou | Integer | 行（PK） |
| shopNo | shop_no | Integer | 店舗番号（PK） |
| meisaikubun | meisaikubun | int | 明細区分（PK） |
| companyNo | company_no | Integer | 仕入会社番号（小田光旧事業部） |
| denpyouHizuke | denpyou_hizuke | LocalDate | 伝票日付 |
| nengetsudo | nengetsudo | String | 年月度 |
| denpyouBangou | denpyou_bangou | BigDecimal | 伝票番号 |
| meisaikubunMei | meisaikubun_mei | String | 明細区分名 |

**仕入先情報**

| フィールド名 | カラム名 | 型 | 説明 |
|------------|---------|-----|------|
| shiiresakiCode | shiiresaki_code | String | 仕入先コード |
| shiiresakiMei1 | shiiresaki_mei1 | String | 仕入先名1 |
| shiiresakiMei2 | shiiresaki_mei2 | String | 仕入先名2 |
| shiiresakiRyakushou | shiiresaki_ryakushou | String | 仕入先名略称 |
| shiiresakiEigyoshoCode | shiiresaki_eigyosho_code | String | 仕入先営業所コード（TPurchase.departmentNoへ） |
| shiiresakiEigyoshoMei | shiiresaki_eigyosho_mei | String | 仕入先営業所名 |
| shiiresakiBumonCode | shiiresaki_bumon_code | String | 仕入先部門コード |
| shiiresakiBumonMei | shiiresaki_bumon_mei | String | 仕入先部門名 |
| shiiresakiChikuCode | shiiresaki_chiku_code | String | 仕入先地区コード |
| shiiresakiChikuMei | shiiresaki_chiku_mei | String | 仕入先地区名 |
| shiiresakiGyoushuCode | shiiresaki_gyoushu_code | String | 仕入先業種コード |
| shiiresakiGyoushuMei | shiiresaki_gyoushu_mei | String | 仕入先業種名 |
| shiiresakiBunrui4Code | shiiresaki_bunrui4_code | String | 仕入先分類４コード |
| shiiresakiBunrui4Mei | shiiresaki_bunrui4_mei | String | 仕入先分類４名 |
| shiiresakiBunrui5Code | shiiresaki_bunrui5_code | String | 仕入先分類５コード |
| shiiresakiBunrui5Mei | shiiresaki_bunrui5_mei | String | 仕入先分類５名 |
| shiiresakiBunrui6Code | shiiresaki_bunrui6_code | String | 仕入先分類６コード |
| shiiresakiBunrui6Mei | shiiresaki_bunrui6_mei | String | 仕入先分類６名 |
| shiiresakiBunrui7Code | shiiresaki_bunrui7_code | String | 仕入先分類７コード |
| shiiresakiBunrui7Mei | shiiresaki_bunrui7_mei | String | 仕入先分類７名 |
| shiiresakiBunrui8Code | shiiresaki_bunrui8_code | String | 仕入先分類８コード |
| shiiresakiBunrui8Mei | shiiresaki_bunrui8_mei | String | 仕入先分類８名 |
| shiiresakiBunrui9Code | shiiresaki_bunrui9_code | String | 仕入先分類９コード |
| shiiresakiBunrui9Mei | shiiresaki_bunrui9_mei | String | 仕入先分類９名 |

**支払先情報**

| フィールド名 | カラム名 | 型 | 説明 |
|------------|---------|-----|------|
| shiharaisakiCode | shiharaisaki_code | String | 支払先コード |
| shiharaisakiMei | shiharaisaki_mei | String | 支払先名 |
| shiharaisakiEigyoshoCode | shiharaisaki_eigyosho_code | String | 支払先営業所コード |
| shiharaisakiEigyoshoMei | shiharaisaki_eigyosho_mei | String | 支払先営業所名 |
| shiharaisakiBumonCode | shiharaisaki_bumon_code | String | 支払先部門コード |
| shiharaisakiBumonMei | shiharaisaki_bumon_mei | String | 支払先部門名 |
| shiharaisakiChikuCode | shiharaisaki_chiku_code | String | 支払先地区コード |
| shiharaisakiChikuMei | shiharaisaki_chiku_mei | String | 支払先地区名 |
| shiharaisakiGyoushuCode | shiharaisaki_gyoushu_code | String | 支払先業種コード |
| shiharaisakiGyoushuMei | shiharaisaki_gyoushu_mei | String | 支払先業種名 |

**担当者情報（分類0〜9）**

| フィールド名 | カラム名 | 型 | 説明 |
|------------|---------|-----|------|
| tantoushaCode | tantousha_code | String | 担当者コード |
| tantoushaMei | tantousha_mei | String | 担当者名 |
| tantoushaBunrui0Code | tantousha_bunrui0_code | String | 担当者分類０コード |
| tantoushaBunrui0Mei | tantousha_bunrui0_mei | String | 担当者分類０名 |
| tantoushaBunrui1Code | tantousha_bunrui1_code | String | 担当者分類１コード |
| tantoushaBunrui1Mei | tantousha_bunrui1_mei | String | 担当者分類１名 |
| tantoushaBunrui2Code | tantousha_bunrui2_code | String | 担当者分類２コード |
| tantoushaBunrui2Mei | tantousha_bunrui2_mei | String | 担当者分類２名 |
| tantoushaBunrui3Code | tantousha_bunrui3_code | String | 担当者分類３コード |
| tantoushaBunrui3Mei | tantousha_bunrui3_mei | String | 担当者分類３名 |
| tantoushaBunrui4Code | tantousha_bunrui4_code | String | 担当者分類４コード |
| tantoushaBunrui4Mei | tantousha_bunrui4_mei | String | 担当者分類４名 |
| tantoushaBunrui5Code | tantousha_bunrui5_code | String | 担当者分類５コード |
| tantoushaBunrui5Mei | tantousha_bunrui5_mei | String | 担当者分類５名 |
| tantoushaBunrui6Code | tantousha_bunrui6_code | String | 担当者分類６コード |
| tantoushaBunrui6Mei | tantousha_bunrui6_mei | String | 担当者分類６名 |
| tantoushaBunrui7Code | tantousha_bunrui7_code | String | 担当者分類７コード |
| tantoushaBunrui7Mei | tantousha_bunrui7_mei | String | 担当者分類７名 |
| tantoushaBunrui8Code | tantousha_bunrui8_code | String | 担当者分類８コード |
| tantoushaBunrui8Mei | tantousha_bunrui8_mei | String | 担当者分類８名 |
| tantoushaBunrui9Code | tantousha_bunrui9_code | String | 担当者分類９コード |
| tantoushaBunrui9Mei | tantousha_bunrui9_mei | String | 担当者分類９名 |

**取引・支払情報**

| フィールド名 | カラム名 | 型 | 説明 |
|------------|---------|-----|------|
| shiharai | shiharai | BigDecimal | 支払 |
| shiharaiKubunMei | shiharai_kubun_mei | String | 支払区分名 |
| kaikakeKubun | kaikake_kubun | BigDecimal | 買掛区分 |
| kaikakeKubunMei | kaikake_kubun_mei | String | 買掛区分名 |
| torihikiKubun | torihiki_kubun | BigDecimal | 取引区分 |
| torihikiKubunMei | torihiki_kubun_mei | String | 取引区分名 |
| torihikiKubunZokusei | torihiki_kubun_zokusei | BigDecimal | 取引区分属性 |
| torihikiKubunZokuseiMei | torihiki_kubun_zokusei_mei | String | 取引区分属性名 |

**商品情報**

| フィールド名 | カラム名 | 型 | 説明 |
|------------|---------|-----|------|
| shouhinCode | shouhin_code | String | 商品コード |
| shouhinMei | shouhin_mei | String | 商品名 |
| makerCode | maker_code | String | メーカーコード |
| makerMei | maker_mei | String | メーカー名 |
| shouhinBunruiCode | shouhin_bunrui_code | String | 商品分類コード |
| shouhinBunruiMei | shouhin_bunrui_mei | String | 商品分類名 |
| irisu | irisu | BigDecimal | 入数 |
| kosuu | kosuu | BigDecimal | 個数 |
| kosuuTanni | kosuu_tanni | String | 個数単位 |
| suuryou | suuryou | BigDecimal | 数量 |
| suuryouTanni | suuryou_tanni | String | 数量単位 |
| tanka | tanka | BigDecimal | 単価 |
| kingaku | kingaku | BigDecimal | 金額（税抜小計、値引明細はここに入る） |
| tankaKakuritsu | tanka_kakuritsu | BigDecimal | 単価掛率 |
| kazeiKubun | kazei_kubun | String | 課税区分 |
| kazeiKubunMei | kazei_kubun_mei | String | 課税区分名 |
| shouhizeiritsu | shouhizeiritsu | BigDecimal | 消費税率 |
| uchishouhizei | uchishouhizei | BigDecimal | 内消費税 |

**摘要・備考・操作情報**

| フィールド名 | カラム名 | 型 | 説明 |
|------------|---------|-----|------|
| gyouTekiyouCode | gyou_tekiyou_code | String | 行摘要コード |
| gyouTekiyou1 | gyou_tekiyou1 | String | 行摘要1 |
| gyouTekiyou2 | gyou_tekiyou2 | String | 行摘要2 |
| bikoCode | biko_code | String | 備考コード |
| biko | biko | String | 備考 |
| loginId | login_id | String | ログインID |
| loginMei | login_mei | String | ログイン名 |
| sousaHizuke | sousa_hizuke | String | 操作日付 |

**仕入固有情報**

| フィールド名 | カラム名 | 型 | 説明 |
|------------|---------|-----|------|
| hacchuuBangou | hacchuu_bangou | BigDecimal | 発注番号 |
| hacchuuGyou | hacchuu_gyou | BigDecimal | 発注行 |
| orderNo | order_no | String | オーダー番号 |
| jidouSeiseiKubun | jidou_seisei_kubun | BigDecimal | 自動生成区分 |
| jidouSeiseiKubunMei | jidou_seisei_kubun_mei | String | 自動生成区分名 |
| denpyouShouhizeiKeisanKubun | denpyou_shouhizei_keisan_kubun | BigDecimal | 伝票消費税計算区分 |
| denpyouShouhizeiKeisanKubunMei | denpyou_shouhizei_keisan_kubun_mei | String | 伝票消費税計算区分名 |
| dataHasseiKubun | data_hassei_kubun | BigDecimal | データ発生区分 |
| aiteShoriRenban | aite_shori_renban | BigDecimal | 相手処理連番 |
| nyuuryokuPatternNo | nyuuryoku_pattern_no | String | 入力パターンNo |
| nyuuryokuPatternMei | nyuuryoku_pattern_mei | String | 入力パターン名 |
| checkmarkKubun | checkmark_kubun | BigDecimal | チェックマーク区分 |
| checkmarkKubunMei | checkmark_kubun_mei | String | チェックマーク区分名 |
| shouhizeiBunrui | shouhizei_bunrui | Integer | 消費税分類 |
| shouhizeiBunruiMei | shouhizei_bunrui_mei | String | 消費税分類名 |

#### ワークテーブル初期化（WSmilePurchaseOutputFileTrancateTasklet）

**クラス**: `jp.co.oda32.batch.smile.WSmilePurchaseOutputFileTrancateTasklet`

処理フロー:
1. `SELECT COUNT(*)` でクリア前行数を確認
2. 行数が0の場合はスキップ
3. `TRUNCATE TABLE w_smile_purchase_output_file RESTART IDENTITY`を実行
4. 失敗した場合は`DELETE FROM w_smile_purchase_output_file`にフォールバック
5. クリア後の行数を再確認し、0でなければ例外をスロー

### 5.2 新規仕入登録処理

**クラス**: `jp.co.oda32.batch.smile.NewSmilePurchaseProcessor`
**サービス**: `jp.co.oda32.batch.smile.SmilePurchaseImportService`

#### 新規対象の判定クエリ（WSmilePurchaseOutputFileRepository.findNewPurchases）

```sql
SELECT wspof.* FROM w_smile_purchase_output_file wspof
LEFT JOIN t_purchase_detail tpd
ON wspof.shori_renban = tpd.ext_purchase_no
AND wspof.gyou = tpd.purchase_detail_no
AND tpd.shop_no = wspof.shop_no
WHERE tpd.ext_purchase_no IS NULL
AND wspof.shouhin_code NOT IN ('00000021','00000023')
ORDER BY wspof.shop_no, wspof.shori_renban
```

`findModifiedPurchases` も同じ `NOT IN` 条件を持つ。

#### 第2事業部月次集約行の除外（重要）

`goods_code IN ('00000021','00000023')` は SMILE に手入力される事務処理用行で、
実仕入は shop_no=2 側に別途存在するため本テーブル（`t_purchase` / `t_purchase_detail`）には
入れずに除外する。

| goods_code | 意味 |
|---|---|
| `00000021` | 第2事業部 10%課税商品の月次集約 |
| `00000023` | 第2事業部 8%課税商品の月次集約 |

**設計思想**: 買掛集計は shop_no=2 の個別仕入（生データ）を源泉とし、経理手入力の集約
(00000021/00000023) には依存しない。手入力漏れでも集計は自動計算される運用。

ワークテーブル自体は CSV 内容を verbatim で保持する方針のため、除外はこの検索段階で行う。
定数: `FinanceConstants.DIVISION2_AGGREGATE_GOODS_CODES`。

#### スキップ条件

- 商品名に"消費税"が含まれる場合
- 商品コードが空（null または空文字）の場合

#### 登録処理フロー（@Transactional(REQUIRES_NEW)）

1. **仕入先処理（supplierProcess）**
   - 仕入先コードが空の場合: nullを使用
   - 仕入先コードがある場合: `MSupplier`を検索、存在しなければ新規登録

2. **TPurchase生成**
   - `extPurchaseNo` = `shoriRenban`（処理連番）
   - `departmentNo` = `shiiresakiEigyoshoCode`（仕入先営業所コード）
   - `taxType` = `kazeiKubun`（課税区分）
   - `purchaseCode` = `denpyouBangou`（伝票番号）

3. **TPurchaseDetail生成**
   - `taxCategory`の決定:
     - `TaxType.TAX_FREE` → `TaxCategory.EXEMPT`（非課税）
     - それ以外 → `shouhizeiBunrui`（消費税分類）で判定

4. **合計計算**
   - `TPurchase.totalAmount`を再計算

### 5.3 仕入更新処理

**クラス**: `jp.co.oda32.batch.smile.UpdateSmilePurchaseProcessor`
**サービス**: `jp.co.oda32.batch.smile.SmilePurchaseUpdateService`

#### 更新対象の判定クエリ（WSmilePurchaseOutputFileRepository.findModifiedPurchases）

以下のいずれかが変更された場合に更新対象:
- `goods_num` ≠ `suuryou`（数量）
- `goods_code` ≠ `shouhin_code`（商品コード）
- `purchase_date` ≠ `denpyou_hizuke`（仕入日付）
- `goods_name` ≠ `shouhin_mei`（商品名）
- `goods_price` ≠ `tanka`（単価）
- `COALESCE(subtotal, 0)` ≠ `COALESCE(kingaku, 0)`（税抜小計）
- `tax_rate` ≠ `shouhizeiritsu`（消費税率）
- `tax_type` ≠ `kazei_kubun`（課税区分）

#### 更新処理（@Transactional(REQUIRES_NEW)）

- `TPurchaseDetail`: 各フィールドを差分更新
- `TPurchase`: 仕入日付を差分更新
- 合計再計算

### 5.4 仕入削除処理

**クラス**: `jp.co.oda32.batch.smile.DeleteSmilePurchaseProcessor`
**サービス**: `jp.co.oda32.batch.smile.SmilePurchaseDeleteService`

#### 削除対象の判定

`t_purchase`にデータがあるが`w_smile_purchase_output_file`に対応レコードが存在しない仕入を、CSVに含まれる仕入日付の範囲で検出（`detectAndDeletePurchasesByDate()`）。

#### 削除処理（deletePurchase）

物理削除の順序:
1. `t_purchase_detail`（仕入明細）
2. `t_purchase`（仕入ヘッダー）

削除後、`t_purchase`の合計金額を再計算。

---

## 6. 支払情報連携

### 6.1 CSVフォーマット定義（SmilePaymentFile）

**クラス**: `jp.co.oda32.batch.smile.SmilePaymentFile`
**メソッド**: `SmilePaymentFile.getSmilePaymentFileFormat()`

全フィールドは`String`型として定義し、型変換はgetterメソッドで行う。

| インデックス | カラム名 | Javaフィールド型 | 型変換処理 |
|------------|---------|---------------|----------|
| 0 | 伝票日付 | String | get時にLocalDate変換（"yyyyMMdd"形式、失敗時null） |
| 1 | 年月度 | String | - |
| 2 | 伝票番号 | String | - |
| 3 | 処理連番 | String | get時にLong.parseLong（失敗時null） |
| 4 | 行 | String | get時にInteger.parseInt（失敗時null） |
| 5 | 仕入先コード | String | - |
| 6 | 仕入先名１ | String | - |
| 7 | 仕入先名２ | String | - |
| 8 | 仕入先名略称 | String | - |
| 9 | 営業所コード | String | - |
| 10 | 営業所名 | String | - |
| 11 | 部門コード | String | - |
| 12 | 部門名 | String | - |
| 13 | 地区コード | String | - |
| 14 | 地区名 | String | - |
| 15 | 業種コード | String | - |
| 16 | 業種名 | String | - |
| 17 | 仕入先分類４コード | String | - |
| 18 | 仕入先分類４名 | String | - |
| 19 | 仕入先分類５コード | String | - |
| 20 | 仕入先分類５名 | String | - |
| 21 | 仕入先分類６コード | String | - |
| 22 | 仕入先分類６名 | String | - |
| 23 | 仕入先分類７コード | String | - |
| 24 | 仕入先分類７名 | String | - |
| 25 | 仕入先分類８コード | String | - |
| 26 | 仕入先分類８名 | String | - |
| 27 | 仕入先分類９コード | String | - |
| 28 | 仕入先分類９名 | String | - |
| 29 | 取引区分 | String | - |
| 30 | 取引区分名 | String | - |
| 31 | 取引区分属性 | String | - |
| 32 | 取引区分属性名 | String | - |
| 33 | 支払額 | String | get時にnew BigDecimal（失敗時null） |
| 34 | 決済予定日 | String | get時にLocalDate変換（null/"0"/空文字の場合はnull） |
| 35 | 備考コード | String | - |
| 36 | 備考 | String | - |
| 37 | ログインID | String | - |
| 38 | ログイン名 | String | - |
| 39 | 操作日付 | String | get時にLocalDate変換（失敗時null） |
| 40 | データ発生区分 | String | - |
| 41 | 相手処理連番 | String | get時にLong.parseLong（失敗時null） |
| 42 | チェックマーク区分 | String | - |
| 43 | チェックマーク区分名 | String | - |

**合計カラム数**: 44

### 6.2 エンティティ定義（WSmilePayment / TSmilePayment）

**WSmilePayment クラス**: `jp.co.oda32.domain.model.smile.WSmilePayment`
**テーブル名**: `w_smile_payment`

**TSmilePayment クラス**: `jp.co.oda32.domain.model.smile.TSmilePayment`
**テーブル名**: `t_smile_payment`

両テーブルは同一フィールド構成。

#### 複合主キー

**WSmilePayment**: `WSmilePayment.WSmilePaymentId`（内部クラス）
**TSmilePayment**: `TSmilePayment.TSmilePaymentId`（内部クラス）

| フィールド名 | カラム名 | 型 |
|------------|---------|-----|
| processingSerialNumber | processing_serial_number | Long |
| lineNo | line_no | Integer |

#### 全フィールド定義

| フィールド名 | カラム名 | 型 | 説明 |
|------------|---------|-----|------|
| processingSerialNumber | processing_serial_number | Long | 処理連番（PK） |
| lineNo | line_no | Integer | 行（PK） |
| voucherDate | voucher_date | LocalDate | 伝票日付 |
| yearMonth | yearmonth | String | 年月度 |
| voucherNo | voucher_no | String | 伝票番号 |
| supplierCode | supplier_code | String | 仕入先コード |
| supplierName1 | supplier_name1 | String | 仕入先名１ |
| supplierName2 | supplier_name2 | String | 仕入先名２ |
| supplierNameAbbr | supplier_name_abbr | String | 仕入先名略称 |
| officeCode | office_code | String | 営業所コード |
| officeName | office_name | String | 営業所名 |
| departmentCode | department_code | String | 部門コード |
| departmentName | department_name | String | 部門名 |
| areaCode | area_code | String | 地区コード |
| areaName | area_name | String | 地区名 |
| industryCode | industry_code | String | 業種コード |
| industryName | industry_name | String | 業種名 |
| supplierClass4Code | supplier_class4_code | String | 仕入先分類４コード |
| supplierClass4Name | supplier_class4_name | String | 仕入先分類４名 |
| supplierClass5Code | supplier_class5_code | String | 仕入先分類５コード |
| supplierClass5Name | supplier_class5_name | String | 仕入先分類５名 |
| supplierClass6Code | supplier_class6_code | String | 仕入先分類６コード |
| supplierClass6Name | supplier_class6_name | String | 仕入先分類６名 |
| supplierClass7Code | supplier_class7_code | String | 仕入先分類７コード |
| supplierClass7Name | supplier_class7_name | String | 仕入先分類７名 |
| supplierClass8Code | supplier_class8_code | String | 仕入先分類８コード |
| supplierClass8Name | supplier_class8_name | String | 仕入先分類８名 |
| supplierClass9Code | supplier_class9_code | String | 仕入先分類９コード |
| supplierClass9Name | supplier_class9_name | String | 仕入先分類９名 |
| transactionType | transaction_type | String | 取引区分 |
| transactionTypeName | transaction_type_name | String | 取引区分名 |
| transactionTypeAttribute | transaction_type_attribute | String | 取引区分属性 |
| transactionTypeAttributeName | transaction_type_attribute_name | String | 取引区分属性名 |
| paymentAmount | payment_amount | BigDecimal | 支払額 |
| settlementDueDate | settlement_due_date | LocalDate | 決済予定日 |
| noteCode | note_code | String | 備考コード |
| note | note | String | 備考 |
| loginId | login_id | String | ログインID |
| loginName | login_name | String | ログイン名 |
| operationDate | operation_date | LocalDate | 操作日付 |
| dataOccurrenceType | data_occurrence_type | String | データ発生区分 |
| counterProcessingSerialNumber | counter_processing_serial_number | Long | 相手処理連番 |
| checkmarkType | checkmark_type | String | チェックマーク区分 |
| checkmarkTypeName | checkmark_type_name | String | チェックマーク区分名 |
| importDate | import_date | LocalDate | 取込日（バッチ実行日） |

**WSmilePayment固有メソッド**:
- `toTSmilePayment()`: `WSmilePayment`を`TSmilePayment`に変換して返す

### 6.3 バッチジョブ構成

**ジョブ名**: `smilePaymentImport`
**Bean名**: `smilePaymentImportJob`
**エントリポイント**: `jp.co.oda32.SmilePaymentImportBatch`

#### ステップフロー

```
smilePaymentWorkTableInitStep (Tasklet)
    └── SmilePaymentWorkTableInitTasklet（w_smile_paymentをTRUNCATE）
         ↓
smilePaymentImportStep (Chunk=100, FaultTolerant)
    ├── Reader:    SmilePaymentFileReader
    ├── Processor: SmilePaymentProcessor
    └── Writer:    SmilePaymentWriter
```

#### Chunk処理設定

| 項目 | 値 |
|------|-----|
| チャンクサイズ | 100 |
| フォールトトレラント | あり |
| スキップ対象例外 | `Exception.class`（全例外） |
| スキップ上限 | 10000件 |
| ジョブパラメータ | `inputFile`（ファイルパス） |

#### Reader（SmilePaymentFileReader）

- ジョブパラメータ `inputFile` からファイルパスを`FileSystemResource`で取得
- エンコーディング: `"Unicode"`（UTF-16LE with BOM）
- ヘッダー行検出: 最初の行に"伝票日付"が含まれる場合は`setLinesToSkip(1)`
- トークナイザー: `tokenizer.setStrict(false)`（フィールド数不一致を許容）

#### Processor（SmilePaymentProcessor）

- `SmilePaymentFile` → `WSmilePayment` へ変換
- `importDate` = `LocalDate.now()`をセット
- 変換エラー（例外）時は`null`を返却（スキップ対象）

#### Writer（SmilePaymentWriter）

1. `TSmilePaymentService.saveAllToWorkTable(validItems)` でワークテーブルに一括保存
2. 伝票日付でレコードをグループ化
3. 各伝票日付について`TSmilePaymentService.synchronizePaymentData(voucherDate)`でUPSERT実行
4. 伝票日付がnullのレコードはWARNログのみ（処理スキップ）

### 6.4 インポートフロー詳細

#### ワークテーブル初期化（SmilePaymentWorkTableInitTasklet）

**クラス**: `jp.co.oda32.batch.smile.SmilePaymentWorkTableInitTasklet`

- `PlatformTransactionManager`を使用した手動トランザクション管理
- `JdbcTemplate.execute("TRUNCATE TABLE w_smile_payment")`でワークテーブルをクリア
- DDL（TRUNCATE）はJPA管理外のため`JdbcTemplate`を使用

#### 同期処理（TSmilePaymentService.synchronizePaymentData）

**クラス**: `jp.co.oda32.domain.service.smile.TSmilePaymentService`

```
同期処理フロー（伝票日付単位）:
1. w_smile_paymentから指定伝票日付のレコードを取得
2. WSmilePayment.toTSmilePayment()でTSmilePaymentに変換
3. TSmilePaymentRepository.saveAll()でt_smile_paymentへUPSERT
4. WSmilePaymentRepositoryから当日の処理連番一覧を取得
5. TSmilePaymentRepository.deleteByVoucherDateAndProcessingSerialNumberNotIn()
   でワークに存在しない処理連番のt_smile_paymentレコードを削除
```

これにより、CSVに含まれない（削除された）支払情報が自動的にt_smile_paymentから除去される。

---

## 7. 得意先マスタ連携

### 7.1 バッチジョブ構成

**ジョブ名**: 得意先インポートジョブ（Partner Import Job）

#### ステップフロー

```
wSmilePartnerTruncateStep (Tasklet)
    └── WSmilePartnerTrancateTasklet（w_smile_partnerをTRUNCATE）
         ↓
partnerFileImportStep (Chunk)
    ├── Reader:    PartnerFileReader
    ├── Processor: PartnerFileProcessor
    └── Writer:    PartnerFileWriter（w_smile_partnerへ保存）
         ↓
partnerSaveStep (Tasklet)
    └── PartnerSaveTasklet（w_smile_partner → m_partner / m_company）
```

### 7.2 PartnerFileReader

**クラス**: `jp.co.oda32.batch.smile.PartnerFileReader`

- 読み込みファイル: `input/partner_import.csv`（クラスパス相対、`ClassLoader.getSystemResource()`で取得）
- エンコーディング: `"Unicode"`
- `ItemStreamReader`を実装

### 7.3 PartnerFileProcessor

**クラス**: `jp.co.oda32.batch.smile.PartnerFileProcessor`

| スキップ条件 | 処理 |
|-------------|------|
| 得意先コードが空 | null返却 |
| 得意先コード = `Constants.FIXED_PARTNER_CODE` | null返却 |
| 得意先名1または略称に"休止"が含まれる | null返却 |

### 7.4 PartnerFileWriter

**クラス**: `jp.co.oda32.batch.smile.PartnerFileWriter`

- `BeanUtils.copyProperties`で`WSmilePartner`に変換
- `OfficeCode.purse(営業所コード)`で`shopNo`を決定
- `WSmilePartnerService.save()`でワークテーブルに保存

### 7.5 PartnerSaveTasklet

**クラス**: `jp.co.oda32.batch.smile.PartnerSaveTasklet`

処理フロー:
1. `WSmilePartnerService.findAll()`で全レコードを取得
2. `MPartnerRepository`で得意先コード検索
3. 既存の場合: `partnerName`, `abbreviatedPartnerName`を差分更新
4. 存在しない場合: `MPartner` + `MCompany`を新規登録

### 7.6 処理連番更新（SmileProcessingSerialNumberUpdateTasklet）

**クラス**: `jp.co.oda32.batch.smile.SmileProcessingSerialNumberUpdateTasklet`

SMILEからの取込データ（`torihikiKubun=1`）に対して処理連番を付与する補助タスクレット:

1. `WSmileOrderOutputFileService.handleWSmileOrderOutputFiles()` でtorihikiKubun=1のレコードをページング取得
2. 伝票番号（`denpyouBangou`）で`t_delivery_detail`を検索
3. `processingSerialNumber`がnullの場合: `shoriRenban`を`processingSerialNumber`にセット
4. `TDelivery`, `TOrder`, `TOrderDetail`にも同様に処理連番をセット

---

## 8. リポジトリ定義

### 8.1 WSmileOrderOutputFileRepository

**クラス**: `jp.co.oda32.domain.repository.smile.WSmileOrderOutputFileRepository`
**継承**: `JpaRepository<WSmileOrderOutputFile, WSmileOrderOutputFilePK>`

| メソッド名 | 説明 |
|-----------|------|
| `findNewOrders(Pageable)` | ワークテーブルにあるがt_delivery_detailに存在しないレコードを取得（新規対象） |
| `findModifiedOrders(Pageable)` | t_delivery_detailと差異があるレコードを取得（更新対象） |
| `truncateTable()` | `TRUNCATE TABLE w_smile_order_output_file`（nativeQuery、@Modifying） |
| `findByTorihikiKubun(BigDecimal, Pageable)` | 取引区分でフィルタリング |
| `findByShopNoAndShoriRenban(int, long)` | 店舗番号と処理連番で検索 |

### 8.2 WSmilePurchaseOutputFileRepository

**クラス**: `jp.co.oda32.domain.repository.smile.WSmilePurchaseOutputFileRepository`
**継承**: `JpaRepository<WSmilePurchaseOutputFile, WSmilePurchaseOutputFilePK>`

| メソッド名 | 説明 |
|-----------|------|
| `findNewPurchases(Pageable)` | ワークテーブルにあるがt_purchase_detailに存在しないレコードを取得 |
| `findModifiedPurchases(Pageable)` | t_purchase_detailと差異があるレコードを取得（COALESCEでsubtotalのnull対応） |
| `truncateTable()` | `TRUNCATE TABLE w_smile_purchase_output_file RESTART IDENTITY` |
| `findByShopNoAndShoriRenban(int, long)` | 店舗番号と処理連番で検索 |

### 8.3 TSmilePaymentRepository

**クラス**: `jp.co.oda32.domain.repository.smile.TSmilePaymentRepository`
**継承**: `JpaRepository<TSmilePayment, TSmilePayment.TSmilePaymentId>`

| メソッド名 | 説明 |
|-----------|------|
| `findByVoucherDate(LocalDate)` | 伝票日付で検索 |
| `findByYearMonth(String)` | 年月度で検索 |
| `findBySupplierCode(String)` | 仕入先コードで検索 |
| `findBySupplierCodeAndYearMonth(String, String)` | 仕入先コードと年月度で検索 |
| `deleteByVoucherDateAndProcessingSerialNumberNotIn(LocalDate, List<Long>)` | UPSERT後の不要レコード物理削除 |

### 8.4 WSmilePaymentRepository

**クラス**: `jp.co.oda32.domain.repository.smile.WSmilePaymentRepository`
**継承**: `JpaRepository<WSmilePayment, WSmilePayment.WSmilePaymentId>`

| メソッド名 | 説明 |
|-----------|------|
| `findByVoucherDate(LocalDate)` | 伝票日付で検索 |
| `findDistinctProcessingSerialNumbersByVoucherDate(LocalDate)` | 処理連番の重複排除リスト取得 |
| `truncateTable()` | `TRUNCATE TABLE w_smile_payment`（nativeQuery） |

---

## 9. サービス定義

### 9.1 TSmilePaymentService

**クラス**: `jp.co.oda32.domain.service.smile.TSmilePaymentService`

| メソッド名 | 説明 |
|-----------|------|
| `synchronizePaymentData(LocalDate voucherDate)` | 指定日付のw_smile_payment→t_smile_paymentへUPSERT、ワークに存在しない処理連番はt_smile_paymentから削除 |
| `saveAllToWorkTable(List<WSmilePayment>)` | `wSmilePaymentRepository.saveAll()`で一括保存 |

### 9.2 WSmileOrderOutputFileService

**クラス**: `jp.co.oda32.domain.service.smile.WSmileOrderOutputFileService`

| メソッド名 | 説明 |
|-----------|------|
| `save(WSmileOrderOutputFile)` | 単件保存 |
| `saveAll(List<WSmileOrderOutputFile>)` | 一括保存 |
| `truncateTable()` | ワークテーブルクリア |
| `handleWSmileOrderOutputFiles(Pageable)` | `torihikiKubun=1`のレコードをページング取得 |

### 9.3 WSmilePurchaseOutputFileService

**クラス**: `jp.co.oda32.domain.service.smile.WSmilePurchaseOutputFileService`

| メソッド名 | 説明 |
|-----------|------|
| `truncateTable()` | `@Transactional(propagation = Propagation.REQUIRES_NEW)`で独立したトランザクションでTRUNCATE実行 |

---

## 10. 定数・Enum定義

### 10.1 OfficeCode（営業所コード）

**クラス**: `jp.co.oda32.constant.OfficeCode`

SMILEの営業所コードとstock-appの店舗番号（OfficeShopNo）のマッピングに使用。

| 定数名 | 説明 |
|--------|------|
| DAINI | 第二営業所 |
| CLEAN_LABO | クリーンラボ |
| DAIICHI | 第一営業所（松山） |
| INNER_PURCHASE | 社内仕入 |
| INNER_ORDER | 社内注文 |

`OfficeCode.purse(String officeCode)` で文字列から定数へ変換。

### 10.2 OfficeShopNo（店舗番号）

**クラス**: `jp.co.oda32.constant.OfficeShopNo`

| 定数名 | getValue() | 説明 |
|--------|-----------|------|
| DAINI | （整数値） | 第二営業所 |
| DAIICHI | （整数値） | 第一営業所 |
| CLEAN_LABO | （整数値） | クリーンラボ |
| B_CART_ORDER | （整数値） | B-CART受注 |
| INNER_ORDER | （整数値） | 社内注文 |
| INNER_PURCHASE | （整数値） | 社内仕入 |

### 10.3 SmileMeisaiKubun（明細区分）

**クラス**: `jp.co.oda32.constant.SmileMeisaiKubun`

| 定数名 | 説明 |
|--------|------|
| TAX | 消費税行（スキップ対象） |

`SmileMeisaiKubun.TAX.getValue()`でString値を取得し、`明細区分`と比較。

### 10.4 ISmileGoodsFile（インターフェース）

**クラス**: `jp.co.oda32.domain.model.smile.ISmileGoodsFile`

`WSmileOrderOutputFile`と`WSmilePurchaseOutputFile`が実装する共通インターフェース。

| メソッド | 型 | 説明 |
|---------|-----|------|
| `getShouhinMei()` | String | 商品名 |
| `getKazeiKubun()` | String | 課税区分 |
| `getShouhizeiBunrui()` | Integer | 消費税分類 |
| `getIrisu()` | BigDecimal | 入数 |
| `getShouhinCode()` | String | 商品コード |
| `getShopNo()` | Integer | 店舗番号 |
| `getTanka()` | BigDecimal | 単価 |

---

## 11. トランザクション管理

### 11.1 新規・更新・削除処理のトランザクション

受注・仕入のインポートタスクレットは1レコードずつ個別トランザクションで処理する。

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void newOrderRegister(WSmileOrderOutputFile record) {
    // 1レコードに対する処理
    // 失敗しても他のレコードの処理には影響しない
}
```

この設計により:
- 1件の処理失敗が他のレコードに影響しない
- StackOverflowError回避（親トランザクションのロールバック伝播を防ぐ）

### 11.2 TRUNCATE処理のトランザクション

DDL（TRUNCATE）はSpringのトランザクション管理外のため、以下の方法で処理する:

| ケース | 方法 |
|--------|------|
| `SmilePaymentWorkTableInitTasklet` | `PlatformTransactionManager`で手動トランザクション管理 + `JdbcTemplate.execute()` |
| `WSmilePurchaseOutputFileTrancateTasklet` | `EntityManager.createNativeQuery().executeUpdate()` |
| `WSmileOrderOutputFileRepository.truncateTable()` | `@Modifying(clearAutomatically = true)` + nativeQuery |

### 11.3 ページング処理

大量データ処理はページング（`PageRequest`）で分割処理:

```java
Pageable pageable = PageRequest.of(0, pageSize);
Page<WSmileOrderOutputFile> page = repository.findNewOrders(pageable);
while (!page.isEmpty()) {
    for (WSmileOrderOutputFile record : page.getContent()) {
        service.newOrderRegister(record);
    }
    // 次ページ
    if (page.hasNext()) {
        page = repository.findNewOrders(page.nextPageable());
    } else {
        break;
    }
}
```

---

## 12. エラーハンドリング

### 12.1 受注インポートのエラー処理

| エラー種別 | 対応 |
|-----------|------|
| ファイルが存在しない | バッチ異常終了 |
| CSVパースエラー（単件） | スキップ（faultTolerantは設定されていないため注意） |
| 得意先が存在しない | 新規登録して処理続行 |
| 商品が存在しない | 新規登録して処理続行 |
| TRUNCATE失敗 | DELETEにフォールバック（WSmilePurchaseOutputFileTrancateTasklet） |
| 1レコード登録失敗 | 独立トランザクションによりそのレコードのみロールバック |

### 12.2 支払情報インポートのエラー処理

| エラー種別 | 対応 |
|-----------|------|
| 変換エラー（SmilePaymentProcessor） | null返却（スキップ） |
| 全例外 | faultTolerant + skip(Exception.class) skipLimit=10000 |
| 伝票日付がnull | WARNログのみ（t_smile_paymentへの同期はスキップ） |

### 12.3 ログ出力

| ログレベル | 出力タイミング |
|-----------|-------------|
| INFO | 各ステップの開始・終了、TRUNCATE前後の行数 |
| WARN | 数量0補正、仕入原価0未満、TRUNCATE失敗してDELETEに切り替え |
| ERROR | TRUNCATE後にレコードが残存、テーブルクリア全体失敗 |

---

## 13. ファイル一覧

### 13.1 バッチ処理クラス

| ファイルパス | 役割 |
|------------|------|
| `src/main/java/jp/co/oda32/SmileOrderFileImportBatch.java` | 受注取込バッチエントリポイント |
| `src/main/java/jp/co/oda32/SmilePaymentImportBatch.java` | 支払情報取込バッチエントリポイント |
| `src/main/java/jp/co/oda32/batch/smile/config/SmileOrderFileImportConfig.java` | 受注取込ジョブ設定 |
| `src/main/java/jp/co/oda32/batch/smile/SmileOrderFile.java` | 売上明細CSVフォーマット（155カラム） |
| `src/main/java/jp/co/oda32/batch/smile/SmileOrderFileReader.java` | 売上明細CSVリーダー |
| `src/main/java/jp/co/oda32/batch/smile/SmileOrderFileProcessor.java` | 売上明細CSVプロセッサー（加工・フィルタリング） |
| `src/main/java/jp/co/oda32/batch/smile/SmileOrderFileWriter.java` | 売上明細CSVライター（ワークテーブル保存） |
| `src/main/java/jp/co/oda32/batch/smile/SmileOrderImportTasklet.java` | 受注新規・更新・削除の統合タスクレット |
| `src/main/java/jp/co/oda32/batch/smile/NewSmileOrderProcessor.java` | 新規受注登録処理 |
| `src/main/java/jp/co/oda32/batch/smile/SmileOrderImportService.java` | 新規受注サービス |
| `src/main/java/jp/co/oda32/batch/smile/UpdateSmileOrderProcessor.java` | 受注更新処理 |
| `src/main/java/jp/co/oda32/batch/smile/SmileOrderUpdateService.java` | 受注更新サービス |
| `src/main/java/jp/co/oda32/batch/smile/DeleteSmileOrderProcessor.java` | 受注削除処理 |
| `src/main/java/jp/co/oda32/batch/smile/SmileOrderDeletionProcessor.java` | 受注削除サービス |
| `src/main/java/jp/co/oda32/batch/smile/WSmileOrderOutputFileTrancateTasklet.java` | 売上明細ワークテーブルTRUNCATE |
| `src/main/java/jp/co/oda32/batch/smile/SmilePurchaseImportTasklet.java` | 仕入新規・更新・削除の統合タスクレット |
| `src/main/java/jp/co/oda32/batch/smile/NewSmilePurchaseProcessor.java` | 新規仕入登録処理 |
| `src/main/java/jp/co/oda32/batch/smile/SmilePurchaseImportService.java` | 新規仕入サービス |
| `src/main/java/jp/co/oda32/batch/smile/UpdateSmilePurchaseProcessor.java` | 仕入更新処理 |
| `src/main/java/jp/co/oda32/batch/smile/SmilePurchaseUpdateService.java` | 仕入更新サービス |
| `src/main/java/jp/co/oda32/batch/smile/DeleteSmilePurchaseProcessor.java` | 仕入削除処理 |
| `src/main/java/jp/co/oda32/batch/smile/SmilePurchaseDeleteService.java` | 仕入削除サービス |
| `src/main/java/jp/co/oda32/batch/smile/WSmilePurchaseOutputFileTrancateTasklet.java` | 仕入ワークテーブルTRUNCATE（失敗時DELETEフォールバック） |
| `src/main/java/jp/co/oda32/batch/smile/SmilePaymentFile.java` | 支払情報CSVフォーマット（44カラム） |
| `src/main/java/jp/co/oda32/batch/smile/SmilePaymentFileReader.java` | 支払情報CSVリーダー |
| `src/main/java/jp/co/oda32/batch/smile/SmilePaymentProcessor.java` | 支払情報CSVプロセッサー |
| `src/main/java/jp/co/oda32/batch/smile/SmilePaymentWriter.java` | 支払情報CSVライター（UPSERT） |
| `src/main/java/jp/co/oda32/batch/smile/SmilePaymentWorkTableInitTasklet.java` | w_smile_paymentのTRUNCATE（手動トランザクション） |
| `src/main/java/jp/co/oda32/batch/smile/PartnerFile.java` | 得意先CSVフォーマット（172カラム） |
| `src/main/java/jp/co/oda32/batch/smile/PartnerFileReader.java` | 得意先CSVリーダー |
| `src/main/java/jp/co/oda32/batch/smile/PartnerFileProcessor.java` | 得意先CSVプロセッサー |
| `src/main/java/jp/co/oda32/batch/smile/PartnerFileWriter.java` | 得意先CSVライター |
| `src/main/java/jp/co/oda32/batch/smile/PartnerSaveTasklet.java` | 得意先ワーク→m_partnerへ同期タスクレット |
| `src/main/java/jp/co/oda32/batch/smile/WSmilePartnerTrancateTasklet.java` | 得意先ワークテーブルTRUNCATE |
| `src/main/java/jp/co/oda32/batch/smile/SmileProcessingSerialNumberUpdateTasklet.java` | 処理連番更新タスクレット |

### 13.2 ドメインモデル・リポジトリ・サービス

| ファイルパス | 役割 |
|------------|------|
| `src/main/java/jp/co/oda32/domain/model/smile/WSmileOrderOutputFile.java` | 売上明細ワークテーブルエンティティ |
| `src/main/java/jp/co/oda32/domain/model/smile/WSmilePurchaseOutputFile.java` | 仕入ワークテーブルエンティティ |
| `src/main/java/jp/co/oda32/domain/model/smile/TSmilePayment.java` | 支払情報本テーブルエンティティ |
| `src/main/java/jp/co/oda32/domain/model/smile/WSmilePayment.java` | 支払情報ワークテーブルエンティティ |
| `src/main/java/jp/co/oda32/domain/model/smile/ISmileGoodsFile.java` | 商品情報共通インターフェース |
| `src/main/java/jp/co/oda32/domain/model/embeddable/WSmileOrderOutputFilePK.java` | 売上明細ワーク複合PK |
| `src/main/java/jp/co/oda32/domain/model/embeddable/WSmilePurchaseOutputFilePK.java` | 仕入ワーク複合PK |
| `src/main/java/jp/co/oda32/domain/repository/smile/WSmileOrderOutputFileRepository.java` | 売上明細ワークリポジトリ |
| `src/main/java/jp/co/oda32/domain/repository/smile/WSmilePurchaseOutputFileRepository.java` | 仕入ワークリポジトリ |
| `src/main/java/jp/co/oda32/domain/repository/smile/TSmilePaymentRepository.java` | 支払情報本テーブルリポジトリ |
| `src/main/java/jp/co/oda32/domain/repository/smile/WSmilePaymentRepository.java` | 支払情報ワークリポジトリ |
| `src/main/java/jp/co/oda32/domain/service/smile/TSmilePaymentService.java` | 支払情報同期サービス |
| `src/main/java/jp/co/oda32/domain/service/smile/WSmileOrderOutputFileService.java` | 売上明細ワークサービス |
| `src/main/java/jp/co/oda32/domain/service/smile/WSmilePurchaseOutputFileService.java` | 仕入ワークサービス |

### 13.3 設定ファイル

| ファイルパス | 役割 |
|------------|------|
| `src/main/resources/config/application-batch.yml` | バッチ専用設定（job.enabled=false等） |
| `src/main/resources/config/schema-postgresql.sql` | DBスキーマ定義 |

---

*作成日: 2026-02-23*
*対象システム: stock-app (C:/project/stock-app)*
*対象バージョン: Spring Boot 2.1.1 / PostgreSQL 9.6*
