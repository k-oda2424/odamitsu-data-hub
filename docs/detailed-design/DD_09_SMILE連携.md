# DD_09 SMILE連携 詳細プログラミング設計書

**文書番号**: DD_09
**作成日**: 2026-02-23
**対象システム**: stock-app (C:/project/stock-app)
**対象バージョン**: Spring Boot 2.1.1 / PostgreSQL 9.6
**基本設計書**: `docs/09_smile_integration.md`

---

## 目次

1. [CSV入力仕様](#1-csv入力仕様)
2. [受注データ連携設計](#2-受注データ連携設計)
3. [仕入データ連携設計](#3-仕入データ連携設計)
4. [支払情報連携設計](#4-支払情報連携設計)
5. [得意先マスタ連携設計](#5-得意先マスタ連携設計)
6. [注文ファイル出力設計](#6-注文ファイル出力設計)
7. [パートナーファイル出力設計](#7-パートナーファイル出力設計)
8. [ワークテーブルTRUNCATE設計](#8-ワークテーブルtruncate設計)
9. [ファイル移動設計](#9-ファイル移動設計)
10. [リポジトリ設計](#10-リポジトリ設計)
11. [トランザクション管理](#11-トランザクション管理)

---

## 1. CSV入力仕様

### 1.1 共通仕様: UTF-16LE BOM読み込み処理

SMILE基幹システムが出力するCSVファイルはすべてUTF-16LE with BOM（Unicode）エンコーディングである。Spring BatchのFlatFileItemReaderで読み込む際、以下の設定を行う。

```java
FlatFileItemReader<T> reader = new FlatFileItemReader<>();
reader.setEncoding("Unicode"); // UTF-16LE with BOM
reader.setLineMapper(new DefaultLineMapper<T>() {{
    setLineTokenizer(new DelimitedLineTokenizer() {{
        setNames(/* CSVフォーマット配列 */);
    }});
    setFieldSetMapper(new BeanWrapperFieldSetMapper<T>() {{
        setTargetType(targetClass);
        setDistanceLimit(0); // 日本語フィールド名の曖昧一致を無効化
    }});
}});
```

**エンコーディング処理の詳細**:

| 設定項目 | 値 | 理由 |
|---------|-----|------|
| `setEncoding()` | `"Unicode"` | Java標準の`Charset.forName("Unicode")`でUTF-16LE BOMが解釈される |
| `setDistanceLimit()` | `0` | 日本語の「名」「コード」等を含むフィールド名が多く、デフォルトの編集距離による曖昧マッチが誤マッピングを引き起こすため無効化 |
| `setStrict()` | `false`（支払CSV） | フィールド数不一致を許容。不足フィールドはnullとしてマッピング |

**BOM処理**: `FlatFileItemReader`はJavaの`InputStreamReader`を通じてBOMを自動的にスキップする。明示的なBOM除去処理は不要。

---

### 1.2 SmileOrderFile（売上明細CSV）

**クラス**: `jp.co.oda32.batch.smile.SmileOrderFile`
**フォーマット取得メソッド**: `SmileOrderFile.getSmileOrderFileFormat()` (String[]返却)
**合計カラム数**: 159（インデックス0〜158）

#### カラム定義一覧

| # | カラム名 | Java型 | バリデーション・変換処理 |
|---|---------|--------|----------------------|
| 0 | 伝票日付 | String | getter内で`DateTimeFormatter.ofPattern("yyyyMMdd")`、`ResolverStyle.STRICT`でLocalDateに変換。パース失敗時は例外 |
| 1 | 年月度 | String | なし |
| 2 | 伝票番号 | BigDecimal | getter内で`toString()`によりString変換 |
| 3 | 処理連番 | Long | なし |
| 4 | 明細区分 | BigDecimal | getter内で`toString()`によりString変換。`SmileMeisaiKubun.TAX`と比較しスキップ判定に使用 |
| 5 | 明細区分名 | String | なし |
| 6 | 行 | Integer | なし |
| 7 | 得意先コード | String | `"999999"`の場合、得意先名1のMD5ハッシュ（16進数）に置換 |
| 8 | 得意先名1 | String | なし |
| 9 | 得意先名2 | String | なし |
| 10 | 得意先名略称 | String | なし |
| 11 | 得意先営業所コード | String | `OfficeCode.purse()`でshopNo決定に使用 |
| 12 | 得意先営業所名 | String | なし |
| 13 | 得意先部門コード | String | なし |
| 14 | 得意先部門名 | String | なし |
| 15 | 得意先地区コード | String | なし |
| 16 | 得意先地区名 | String | なし |
| 17 | 得意先業種コード | String | なし |
| 18 | 得意先業種名 | String | なし |
| 19 | 得意先グループコード | String | なし |
| 20 | 得意先グループ名 | String | なし |
| 21 | 得意先単価ランクコード | String | なし |
| 22 | 得意先単価ランク名 | String | なし |
| 23〜30 | 得意先分類6〜9コード/名 | String | なし |
| 31 | 請求先コード | String | なし |
| 32 | 請求先名 | String | なし |
| 33 | 請求先営業所コード | String | なし |
| 34 | 請求先営業所名 | String | なし |
| 35 | 請求先部門コード | String | なし |
| 36 | 請求先部門名 | String | なし |
| 37〜52 | 請求先地区〜分類9コード/名 | String | なし |
| 53 | 納品先コード | String | なし |
| 54 | 納品先名 | String | `TDelivery.destinationName`にセット |
| 55 | 担当者コード | String | なし |
| 56 | 担当者名 | String | なし |
| 57〜76 | 担当者分類0〜9コード/名 | String | なし |
| 77 | 請求 | BigDecimal | なし |
| 78 | 請求区分名 | String | なし |
| 79 | 売掛区分 | BigDecimal | getter内で`toString()`変換。`TOrder.paymentMethod`にセット |
| 80 | 売掛区分名 | String | なし |
| 81 | 取引区分 | BigDecimal | なし |
| 82 | 取引区分名 | String | なし |
| 83 | 取引区分属性 | BigDecimal | なし |
| 84 | 取引区分属性名 | String | なし |
| 85 | 商品コード | String | `"99999999"`の場合、商品名のMD5ハッシュ（16進数）に置換し、商品名に`"（手入力）"`プレフィックス付加 |
| 86 | 商品名 | String | なし |
| 87 | メーカーコード | String | なし |
| 88 | メーカー名 | String | なし |
| 89〜106 | 商品分類1〜9コード/名 | String | なし |
| 107 | 入数 | BigDecimal | 1未満の場合は`BigDecimal.ONE`に補正 |
| 108 | 個数 | BigDecimal | ケース注文数量 |
| 109 | 個数単位 | String | なし |
| 110 | 数量 | BigDecimal | 0の場合は`BigDecimal.ONE`に補正（WARNログ: `"数量が0以下なので数量1に変更しました。伝票番号:%s 伝票明細番号:%d 商品コード:%s"`） |
| 111 | 数量単位 | String | なし |
| 112 | 単価 | BigDecimal | 0かつ金額!=0の場合: `金額 / 数量.abs()`（`ROUND_HALF_UP`, scale=2）で算出 |
| 113 | 金額 | BigDecimal | なし |
| 114 | 原単価 | BigDecimal | 0以下の場合WARNログ出力（`"仕入原価が0円未満です。伝票番号：%s,行：%s,仕入原価：%s"`）。`TOrderDetail.purchasePrice`にセット |
| 115 | 原価金額 | BigDecimal | なし |
| 116 | 粗利 | BigDecimal | なし |
| 117 | 単価掛率 | BigDecimal | なし |
| 118 | 課税区分 | BigDecimal | getter内で`toString()`変換。`TOrderDetail.taxType`にセット |
| 119 | 課税区分名 | String | なし |
| 120 | 消費税率 | BigDecimal | `TOrderDetail.taxRate`にセット |
| 121 | 内消費税等 | BigDecimal | なし |
| 122 | 行摘要コード | String | なし |
| 123 | 行摘要1 | String | なし |
| 124 | 行摘要2 | String | なし |
| 125 | 備考コード | String | `"00010"`の場合は直送フラグを`true`にセット |
| 126 | 備考 | String | なし |
| 127 | ログインID | String | なし |
| 128 | ログイン名 | String | なし |
| 129 | 操作日付 | String | なし |
| 130 | 受注番号 | BigDecimal | なし |
| 131 | 受注行 | BigDecimal | なし |
| 132 | オーダー番号 | String | なし |
| 133 | 見積処理連番 | BigDecimal | なし |
| 134 | 見積行 | BigDecimal | なし |
| 135 | 自動生成区分 | BigDecimal | なし |
| 136 | 自動生成区分名 | String | なし |
| 137 | 伝票消費税計算区分 | BigDecimal | なし |
| 138 | 伝票消費税計算区分名 | String | なし |
| 139 | データ発生区分 | BigDecimal | なし |
| 140 | 相手処理連番 | BigDecimal | なし |
| 141 | 入力パターン番号 | String | なし |
| 142 | 入力パターン名 | String | なし |
| 143 | 不使用伝票番号 | String | なし |
| 144 | 相手伝票番号 | String | なし |
| 145 | コード | String | なし |
| 146 | 不使用課税区分 | String | なし |
| 147 | 不使用コード | String | なし |
| 148 | 直送区分 | String | なし |
| 149 | 社店コード | String | なし |
| 150 | 分類コード | String | なし |
| 151 | 伝票区分 | String | なし |
| 152 | 取引先コード | String | なし |
| 153 | 売単価 | BigDecimal | なし |
| 154 | 相手商品コード | String | なし |
| 155 | チェックマーク区分 | BigDecimal | なし |
| 156 | チェックマーク区分名 | String | なし |
| 157 | 消費税分類 | Integer | なし |
| 158 | 消費税分類名 | String | なし |

**補足フィールド**（CSVカラム外、Processor処理中にセット）:

| フィールド名 | 型 | セット元 |
|------------|-----|--------|
| shopNo | int | `SmileOrderFileProcessor.getShopNoFromItem()`で伝票番号・営業所コードから決定 |

---

### 1.3 ExtPurchaseFile（仕入明細CSV）

**クラス**: `jp.co.oda32.batch.purchase.ExtPurchaseFile`
**備考**: SmileOrderFileと同様の多数カラム構成。仕入先系フィールドが得意先系フィールドに置換される。

仕入CSVの特徴:
- 仕入先コード・仕入先名がSmileOrderFileの得意先コード・得意先名に対応
- 支払先情報（支払先コード、支払先営業所コード等）がSmileOrderFileの請求先に対応
- 発注番号・発注行がSmileOrderFileの受注番号・受注行に対応
- 明細区分が複合主キーの一部として使用される（SmileOrderFileでは主キーに含まれない）

---

### 1.4 SmilePaymentFile（支払情報CSV）

**クラス**: `jp.co.oda32.batch.smile.SmilePaymentFile`
**フォーマット取得メソッド**: `SmilePaymentFile.getSmilePaymentFileFormat()` (String[]返却)
**合計カラム数**: 44（インデックス0〜43）

**重要設計方針**: 全フィールドをString型として定義し、型変換はgetterメソッド内で行う。変換失敗時はnullを返却する。

| # | カラム名 | Java宣言型 | getter戻り型 | 変換処理 |
|---|---------|----------|------------|---------|
| 0 | 伝票日付 | String | LocalDate | `DateTimeFormatter.ofPattern("yyyyMMdd")`、`ResolverStyle.STRICT`で変換。失敗時null |
| 1 | 年月度 | String | String | なし |
| 2 | 伝票番号 | String | String | なし |
| 3 | 処理連番 | String | Long | `Long.parseLong()`で変換。失敗時null |
| 4 | 行 | String | Integer | `Integer.parseInt()`で変換。失敗時null |
| 5 | 仕入先コード | String | String | なし |
| 6 | 仕入先名1 | String | String | なし |
| 7 | 仕入先名2 | String | String | なし |
| 8 | 仕入先名略称 | String | String | なし |
| 9 | 営業所コード | String | String | なし |
| 10 | 営業所名 | String | String | なし |
| 11 | 部門コード | String | String | なし |
| 12 | 部門名 | String | String | なし |
| 13 | 地区コード | String | String | なし |
| 14 | 地区名 | String | String | なし |
| 15 | 業種コード | String | String | なし |
| 16 | 業種名 | String | String | なし |
| 17〜28 | 仕入先分類4〜9コード/名 | String | String | なし |
| 29 | 取引区分 | String | String | なし |
| 30 | 取引区分名 | String | String | なし |
| 31 | 取引区分属性 | String | String | なし |
| 32 | 取引区分属性名 | String | String | なし |
| 33 | 支払額 | String | BigDecimal | `new BigDecimal(value)`で変換。失敗時null |
| 34 | 決済予定日 | String | LocalDate | null/"0"/空文字の場合はnull。それ以外は`yyyyMMdd`形式でLocalDate変換 |
| 35 | 備考コード | String | String | なし |
| 36 | 備考 | String | String | なし |
| 37 | ログインID | String | String | なし |
| 38 | ログイン名 | String | String | なし |
| 39 | 操作日付 | String | LocalDate | `yyyyMMdd`形式でLocalDate変換。失敗時null |
| 40 | データ発生区分 | String | String | なし |
| 41 | 相手処理連番 | String | Long | `Long.parseLong()`で変換。失敗時null |
| 42 | チェックマーク区分 | String | String | なし |
| 43 | チェックマーク区分名 | String | String | なし |

---

### 1.5 PartnerFile（得意先マスタCSV）

**クラス**: `jp.co.oda32.batch.smile.PartnerFile`
**フォーマット取得メソッド**: `PartnerFile.getPartnerFileFormat()` (String[]返却)
**合計カラム数**: 172

得意先マスタCSVの主要フィールド:

| # | カラム名 | 型 | 備考 |
|---|---------|-----|------|
| 0 | 得意先コード | String | 空文字またはConstants.FIXED_PARTNER_CODEの場合はスキップ |
| 1 | 得意先名1 | String | "休止"を含む場合はスキップ |
| 2 | 得意先名2 | String | なし |
| 3 | 得意先名略称 | String | "休止"を含む場合はスキップ |
| - | 営業所コード | String | `OfficeCode.purse()`でshopNo決定に使用 |

---

## 2. 受注データ連携設計

### 2.1 SmileOrderFileImportConfig Job/Step構成図

**ソースファイル**: `src/main/java/jp/co/oda32/batch/smile/config/SmileOrderFileImportConfig.java`

```
┌─────────────────────────────────────────────────────────────┐
│ Job: smileOrderFileImport                                    │
│ Bean名: smileOrderFileImportJob                              │
│ Incrementer: RunIdIncrementer                                │
│ Listener: JobStartEndListener                                │
│ エントリポイント: SmileOrderFileImportBatch                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Step 1: smileOrderFileImportStep [Chunk<SmileOrderFile, SmileOrderFile>(500)]
│  ┌───────────────────────────────────────────────────────┐   │
│  │ Reader:    SmileOrderFileReader                       │   │
│  │ Processor: SmileOrderFileProcessor                    │   │
│  │ Writer:    SmileOrderFileWriter                       │   │
│  │ Listener:  ExitStatusChangeListener                   │   │
│  └───────────────────────────────────────────────────────┘   │
│          │                                                    │
│          ▼                                                    │
│  Step 2: stockAllocateStep [Tasklet]                         │
│  └── StockAllocateTasklet（在庫割当）                          │
│          │                                                    │
│          ▼                                                    │
│  Step 3: orderStatusUpdateStep [Tasklet]                     │
│  └── OrderStatusUpdateTasklet（受注ステータス更新）             │
│          │                                                    │
│          ▼                                                    │
│  Step 4: shopAppropriateStockCalculateStep [Tasklet]         │
│  └── ShopAppropriateStockCalculateTasklet（適正在庫計算）       │
│          │                                                    │
│          ▼                                                    │
│  Step 5: vSalesMonthlySummaryRefreshStep [Tasklet]           │
│  └── VSalesMonthlySummaryRefreshTasklet（月次集計更新）         │
│          │                                                    │
│          ▼                                                    │
│  Step 6: fileMoveStep [Tasklet]                              │
│  └── FileManagerTasklet（処理済みファイルをcompleted/へ移動）    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**Job定義の実装コード**:

```java
@Bean
public Job smileOrderFileImportJob() {
    return jobBuilderFactory.get("smileOrderFileImport")
            .incrementer(new RunIdIncrementer())
            .listener(smileOrderJobListener())
            .flow(smileOrderFileImportStep())
            .next(stockAllocateStep())
            .next(orderStatusUpdateStep())
            .next(shopAppropriateStockCalculateStep())
            .next(vSalesMonthlySummaryRefreshStep())
            .next(fileMoveStep())
            .end()
            .build();
}
```

**Chunk設定**: `.<SmileOrderFile, SmileOrderFile>chunk(500)`

### 2.2 SmileOrderFileReader 詳細処理フロー

**ソースファイル**: `src/main/java/jp/co/oda32/batch/smile/SmileOrderFileReader.java`
**インターフェース**: `ItemReader<SmileOrderFile>`

```
┌── @BeforeStep(StepExecution) ──────────────────────────────┐
│                                                             │
│  1. MShopLinkedFileService.findAll()                        │
│     └── 全MShopLinkedFileレコード取得                         │
│                                                             │
│  2. smileOrderInputFileNameが非空のレコードのみフィルタ       │
│     └── stream().filter(!StringUtil.isEmpty(...))           │
│                                                             │
│  3. ファイルパス → Path変換、存在確認                          │
│     └── Files.exists(path) で存在チェック                    │
│     └── 存在しない場合: log.warn出力、スキップ(false)         │
│                                                             │
│  4. MultiResourceItemReader初期化                            │
│     ├── setStrict(false): ファイル0件でもエラーにしない       │
│     └── setResources(FileSystemResource[])                  │
│                                                             │
│  5. FlatFileItemReader<SmileOrderFile>初期化                 │
│     ├── setEncoding("Unicode")                              │
│     ├── DelimitedLineTokenizer                              │
│     │   └── setNames(SmileOrderFile.getSmileOrderFileFormat())
│     └── BeanWrapperFieldSetMapper                           │
│         ├── setTargetType(SmileOrderFile.class)             │
│         └── setDistanceLimit(0)                             │
│                                                             │
│  6. reader.setDelegate(fileReader)                          │
│  7. reader.open(jobContext)                                 │
└─────────────────────────────────────────────────────────────┘
```

**注意**: ヘッダースキップ（`setLinesToSkip`）は受注CSVでは設定されていない。SMILEの売上明細出力にはヘッダー行が含まれない前提。

### 2.3 SmileOrderFileProcessor 詳細処理フロー

**ソースファイル**: `src/main/java/jp/co/oda32/batch/smile/SmileOrderFileProcessor.java`
**インターフェース**: `ItemProcessor<SmileOrderFile, SmileOrderFile>`

```
process(SmileOrderFile item):
    │
    ├── [1] shopNo決定: getShopNoFromItem(item)
    │   ├── 伝票番号.length()==8 && ("8"or"9"始まり) → B_CART_ORDER(1)
    │   ├── OfficeCode.purse(得意先営業所コード)
    │   │   ├── DAINI       → OfficeShopNo.DAINI(2)
    │   │   ├── CLEAN_LABO  → OfficeShopNo.CLEAN_LABO(3)
    │   │   ├── DAIICHI     → OfficeShopNo.DAIICHI(1)
    │   │   ├── INNER_PURCHASE → OfficeShopNo.INNER_PURCHASE(1)
    │   │   ├── INNER_ORDER → OfficeShopNo.INNER_ORDER(1)
    │   │   └── null(未知)  → OfficeShopNo.INNER_ORDER(1)
    │   └── default → 0
    │
    ├── [2] 手打ち得意先チェック
    │   └── 得意先コード == "999999"
    │       └── DigestUtils.md5DigestAsHex(得意先名1.getBytes()) → 得意先コードに上書き
    │
    ├── [3] 社内売掛スキップ
    │   └── shopNo == DAINI(2) && 得意先コード == "910005"
    │       └── return null (スキップ)
    │
    ├── [4] 消費税行スキップ
    │   └── 明細区分 == SmileMeisaiKubun.TAX.getValue()
    │       └── return null (スキップ)
    │
    ├── [5] 数量0補正
    │   └── 数量 == 0
    │       ├── log.warn("数量が0以下なので数量1に変更しました...")
    │       └── 数量 = BigDecimal.ONE
    │
    ├── [6] 手打ち商品チェック
    │   └── 商品コード == "99999999"
    │       ├── 商品コード = DigestUtils.md5DigestAsHex(商品名.getBytes())
    │       └── 商品名 = "（手入力）" + 商品名
    │
    ├── [7] 入数補正
    │   └── 入数 < 1
    │       └── 入数 = BigDecimal.ONE
    │
    ├── [8] 単価算出
    │   └── 単価 == 0 && 金額 != 0
    │       └── 単価 = 金額 / 数量.abs()  [ROUND_HALF_UP, scale=2]
    │
    ├── [9] 仕入原価警告
    │   └── 原単価 <= 0
    │       └── log.warn("仕入原価が0円未満です...") ※スキップしない
    │
    └── return item
```

### 2.4 SmileOrderFileWriter 詳細処理フロー

**ソースファイル**: `src/main/java/jp/co/oda32/batch/smile/SmileOrderFileWriter.java`
**インターフェース**: `ItemWriter<SmileOrderFile>`
**スコープ**: `@StepScope`

```
write(List<SmileOrderFile> items):
    │
    for each SmileOrderFile item:
    │
    ├── [1] INFOログ出力
    │   └── "伝票日付:%s,伝票番号:%s,明細番号:%s,得意先：%s,商品名：%s"
    │
    ├── [2] WSmileOrderOutputFile変換
    │   ├── new WSmileOrderOutputFile()
    │   └── wSmileOrderOutputFile.convertSmileOrderFile(item)
    │       └── SmileOrderFileの各フィールドをWSmileOrderOutputFileの対応フィールドにマッピング
    │
    └── [3] 保存
        └── wSmileOrderOutputFileService.save(wSmileOrderOutputFile)
            └── JpaRepository.save()  ※存在すればUPDATE、存在しなければINSERT
```

### 2.5 WSmileOrderOutputFileエンティティ設計

**ソースファイル**: `src/main/java/jp/co/oda32/domain/model/smile/WSmileOrderOutputFile.java`
**テーブル名**: `w_smile_order_output_file`
**実装インターフェース**: `ISmileGoodsFile`
**アノテーション**: `@Entity`, `@Data`, `@Builder`, `@IdClass(WSmileOrderOutputFilePK.class)`

#### 複合主キー

**クラス**: `jp.co.oda32.domain.model.embeddable.WSmileOrderOutputFilePK`

```java
@Data
public class WSmileOrderOutputFilePK implements Serializable {
    private Long shoriRenban;   // 処理連番
    private Integer gyou;       // 行番号
    private Integer shopNo;     // 店舗番号
}
```

**主キー設計の背景**: 旧松山事業所と現在の事業所で伝票番号（処理連番）が重複するため、shopNoを複合主キーに含めている。

#### エンティティ - 本テーブルマッピング関係

| WSmileOrderOutputFileフィールド | マッピング先テーブル | マッピング先カラム |
|------------------------------|------------------|-----------------|
| shoriRenban | TOrder/TDeliveryDetail | processing_serial_number |
| denpyouHizuke | TOrder | order_date_time（LocalDateTime変換） |
| tokuisakiCode | TOrder | partner_code |
| urikakeKubun | TOrder | payment_method |
| shouhinCode | TOrderDetail | goods_code |
| shouhinMei | TOrderDetail | goods_name |
| suuryou | TOrderDetail | order_num |
| tanka | TOrderDetail | goods_price |
| genTanka | TOrderDetail | purchase_price |
| kazeiKubun | TOrderDetail | tax_type |
| shouhizeiritsu | TOrderDetail | tax_rate |
| denpyouBangou | TDelivery | slip_no |
| nouhinSakiMei | TDelivery | destination_name |
| bikoCode | TDelivery | direct_shipping_flg（"00010"でtrue） |

### 2.6 新規/更新/削除の判定ロジック

#### 新規対象判定（findNewOrders）

```sql
SELECT wsoof.* FROM w_smile_order_output_file wsoof
LEFT JOIN t_delivery_detail td
ON wsoof.shori_renban = td.processing_serial_number
AND wsoof.gyou = td.order_detail_no
AND td.shop_no = wsoof.shop_no
WHERE td.processing_serial_number IS NULL
ORDER BY wsoof.shop_no, wsoof.shori_renban
```

**判定ロジック**: `t_delivery_detail`に該当する`processing_serial_number`が存在しないレコードが新規対象。

**登録処理フロー（NewSmileOrderProcessor / SmileOrderImportService）**:

```
@Transactional(propagation = Propagation.REQUIRES_NEW)
newOrderRegister(WSmileOrderOutputFile record):
    │
    ├── [1] partnerProcess: MPartner検索/新規登録
    │   ├── 得意先コードで検索
    │   ├── 不存在: MPartner + MCompany新規登録
    │   └── 存在: スキップ
    │
    ├── [2] goodsProcess: MGoods検索/新規登録
    │   ├── 商品コードで検索
    │   └── 不存在: MGoods新規登録
    │
    ├── [3] TOrder生成
    │   ├── processingSerialNumber = shoriRenban
    │   ├── shopNo = CSVから取得
    │   ├── companyNo = 得意先.companyNo
    │   ├── orderStatus = OrderStatus.RECEIPT
    │   ├── orderDateTime = denpyouHizuke → LocalDateTime
    │   ├── paymentMethod = urikakeKubun
    │   ├── partnerNo = 得意先.partnerNo
    │   └── partnerCode = tokuisakiCode
    │
    ├── [4] TOrderDetail生成
    │   ├── goodsNo = MGoods.goodsNo
    │   ├── goodsCode = shouhinCode
    │   ├── orderNum = suuryou
    │   ├── goodsPrice = tanka
    │   ├── taxRate = shouhizeiritsu
    │   ├── taxType = kazeiKubun
    │   └── purchasePrice = genTanka
    │
    ├── [5] TDelivery生成
    │   ├── directShippingFlg = (bikoCode == "00010")
    │   ├── slipNo = denpyouBangou
    │   └── destinationName = nouhinSakiMei
    │
    ├── [6] TDeliveryDetail生成
    │   ├── processingSerialNumber = shoriRenban
    │   └── deliveryDetailStatus:
    │       ├── denpyouHizuke < 現在日 → DELIVERED
    │       └── denpyouHizuke >= 現在日 → WAIT_SHIPPING
    │
    └── [7] 合計計算
        ├── TOrder.totalAmount = SUM(goodsPrice * orderNum)
        └── TDelivery.totalAmount = 出荷明細合計
```

#### 更新対象判定（findModifiedOrders）

```sql
SELECT wsoof.* FROM w_smile_order_output_file wsoof
LEFT JOIN t_delivery_detail td ON wsoof.shori_renban = td.processing_serial_number
AND wsoof.gyou = td.order_detail_no AND td.shop_no = wsoof.shop_no
LEFT JOIN t_order_detail od ON td.order_no = od.order_no AND td.order_detail_no = od.order_detail_no
LEFT JOIN t_order o ON od.order_no = o.order_no
WHERE (td.delivery_num != wsoof.suuryou
OR td.goods_code != wsoof.shouhin_code
OR wsoof.denpyou_hizuke != (SELECT d.slip_date FROM t_delivery d WHERE td.delivery_no = d.delivery_no)
OR od.goods_name != wsoof.shouhin_mei
OR od.goods_price != wsoof.tanka
OR od.tax_rate != wsoof.shouhizeiritsu
OR od.tax_type != wsoof.kazei_kubun
OR o.partner_code != wsoof.tokuisaki_code)
ORDER BY wsoof.shop_no, wsoof.shori_renban
```

**差分更新フィールド一覧**:

| 比較元(ワーク) | 比較先(本テーブル) | 更新対象テーブル |
|-------------|---------------|-------------|
| suuryou | delivery_num / order_num | TDeliveryDetail / TOrderDetail |
| shouhin_code | goods_code | TDeliveryDetail / TOrderDetail |
| denpyou_hizuke | slip_date | TDelivery |
| shouhin_mei | goods_name | TOrderDetail |
| tanka | goods_price | TOrderDetail |
| shouhizeiritsu | tax_rate | TOrderDetail |
| kazei_kubun | tax_type | TOrderDetail |
| tokuisaki_code | partner_code | TOrder（+ 関連テーブルのcompanyNo連動更新） |

**更新処理（SmileOrderUpdateService.updateOrder）**:

```
@Transactional(propagation = Propagation.REQUIRES_NEW)
updateOrder(int shopNo, Long shorirenban, List<WSmileOrderOutputFile>):
    │
    ├── [1] TOrder取得: tOrderService.getByUniqKey(shopNo, shorirenban)
    │
    ├── [2] 明細ごとのループ処理
    │   ├── TOrderDetail特定: shopNo + shoriRenban + gyouで一致するもの
    │   ├── TDeliveryDetail特定: orderDetailNoで一致するもの
    │   ├── shopNo差分 → TOrderDetail.shopNo更新
    │   ├── 数量差分  → TOrderDetail.orderNum + TDeliveryDetail.deliveryNum更新
    │   ├── 商品コード差分 → TOrderDetail.goodsCode + TDeliveryDetail.goodsCode更新
    │   ├── 商品名差分   → TOrderDetail.goodsName更新
    │   ├── 単価差分     → TOrderDetail.goodsPrice更新
    │   ├── 消費税率差分 → TOrderDetail.taxRate更新
    │   └── 課税区分差分 → TOrderDetail.taxType更新
    │
    ├── [3] 納品日差分チェック（1回のみ）
    │   └── slip_date != denpyou_hizuke → TDelivery.slipDate更新
    │
    ├── [4] 得意先コード差分チェック（1回のみ）
    │   └── partner_code != tokuisaki_code
    │       ├── TOrder: partnerCode, partnerNo, companyNo, companyName更新
    │       ├── TDelivery: partnerCode, companyNo更新
    │       ├── 全TOrderDetail: companyNo更新
    │       └── 全TDeliveryDetail: companyNo更新
    │
    └── [5] 合計再計算: calculateTotalAmount(existOrder)
```

#### 削除対象判定

`t_delivery_detail`にデータが存在するが、`w_smile_order_output_file`に対応レコードがない出荷明細を、CSVに含まれる伝票日付の最小・最大範囲内で検出。

**物理削除順序**（外部キー制約考慮）:

```
1. t_delivery_detail  (出荷明細)
2. t_order_detail     (注文明細)
3. t_delivery         (出荷ヘッダー)
4. t_order            (注文ヘッダー)
```

削除後、`t_order`および`t_delivery`の合計金額を再計算。

---

## 3. 仕入データ連携設計

### 3.1 PurchaseFileImportConfig Job/Step構成図

**ソースファイル**: `src/main/java/jp/co/oda32/batch/purchase/config/PurchaseFileImportConfig.java`

```
┌──────────────────────────────────────────────────────────────┐
│ Job: purchaseFileImport                                       │
│ Bean名: purchaseFileImportJob                                 │
│ Incrementer: RunIdIncrementer                                 │
│ Listener: JobStartEndListener, exitApplicationListener        │
│ エントリポイント: PurchaseFileImportBatch                      │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  Step 1: wSmilePurchaseFileTruncateStep [Tasklet]             │
│  └── WSmilePurchaseOutputFileTrancateTasklet                  │
│      └── w_smile_purchase_output_fileをTRUNCATE               │
│          │                                                    │
│          ▼                                                    │
│  Step 2: purchaseFileImportStep [Chunk<PurchaseFile, ExtPurchaseFile>(500)]
│  ┌───────────────────────────────────────────────────────┐    │
│  │ Reader:    PurchaseFileReader                         │    │
│  │ Processor: PurchaseFileProcessor                      │    │
│  │ Writer:    PurchaseFileWriter                         │    │
│  │ Listener:  ExitStatusChangeListener                   │    │
│  └───────────────────────────────────────────────────────┘    │
│          │                                                    │
│          ▼                                                    │
│  Step 3: smilePurchaseImportStep [Tasklet]                    │
│  └── SmilePurchaseImportTasklet                               │
│      └── ワークテーブル→本テーブルへ新規/更新/削除処理          │
│          │                                                    │
│          ▼                                                    │
│  Step 4: purchaseLinkSendOrderStep [Tasklet]                  │
│  └── PurchaseLinkSendOrderTasklet                             │
│          │                                                    │
│          ▼                                                    │
│  Step 5: purchasePriceCreateStep [Tasklet]                    │
│  └── PurchasePriceCreateTasklet                               │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 WSmilePurchaseOutputFileエンティティ設計

**ソースファイル**: `src/main/java/jp/co/oda32/domain/model/smile/WSmilePurchaseOutputFile.java`
**テーブル名**: `w_smile_purchase_output_file`
**実装インターフェース**: `ISmileGoodsFile`

#### 複合主キー（4フィールド）

**クラス**: `jp.co.oda32.domain.model.embeddable.WSmilePurchaseOutputFilePK`

```java
@Data
public class WSmilePurchaseOutputFilePK implements Serializable {
    private Long shoriRenban;      // 処理連番
    private Integer gyou;          // 行番号
    private Integer shopNo;        // 店舗番号
    private int meisaikubun;       // 明細区分（受注側と異なり主キーに含む）
}
```

**受注ワークとの差異**: 仕入ワークでは`meisaikubun`（明細区分）が主キーに含まれる（4フィールド複合主キー）。受注ワークは3フィールド（shoriRenban, gyou, shopNo）。

#### フィールドグループ構成

| カテゴリ | フィールド数 | 主要フィールド |
|---------|-----------|-------------|
| 基本情報 | 9 | shoriRenban, gyou, shopNo, meisaikubun, companyNo, denpyouHizuke, nengetsudo, denpyouBangou, meisaikubunMei |
| 仕入先情報 | 18 | shiiresakiCode, shiiresakiMei1〜2, shiiresakiRyakushou, shiiresakiEigyoshoCode/Mei, 分類4〜9 |
| 支払先情報 | 10 | shiharaisakiCode, shiharaisakiMei, shiharaisakiEigyoshoCode/Mei, 部門/地区/業種 |
| 担当者情報 | 22 | tantoushaCode, tantoushaMei, 分類0〜9 |
| 取引・支払情報 | 8 | shiharai, shiharaiKubunMei, kaikakeKubun/Mei, torihikiKubun/Mei, torihikiKubunZokusei/Mei |
| 商品情報 | 16 | shouhinCode, shouhinMei, makerCode/Mei, shouhinBunruiCode/Mei, irisu, kosuu, suuryou, tanka, kingaku等 |
| 摘要・備考 | 8 | gyouTekiyouCode, gyouTekiyou1/2, bikoCode, biko, loginId, loginMei, sousaHizuke |
| 仕入固有情報 | 12 | hacchuuBangou, hacchuuGyou, orderNo, jidouSeiseiKubun等 |

### 3.3 ワークテーブルTRUNCATE → 投入 → 本テーブルUPSERTの処理シーケンス

```
┌─ Phase 1: TRUNCATE ─────────────────────────────────────────┐
│ WSmilePurchaseOutputFileTrancateTasklet.execute()            │
│                                                              │
│  1. SELECT COUNT(*) FROM w_smile_purchase_output_file        │
│     └── クリア前行数確認                                      │
│                                                              │
│  2. 行数 == 0 → スキップ（RepeatStatus.FINISHED）            │
│                                                              │
│  3. TRUNCATE TABLE w_smile_purchase_output_file              │
│     RESTART IDENTITY                                         │
│     └── EntityManager.createNativeQuery().executeUpdate()    │
│                                                              │
│  4. TRUNCATE失敗時フォールバック:                              │
│     DELETE FROM w_smile_purchase_output_file                  │
│                                                              │
│  5. SELECT COUNT(*) で検証                                    │
│     └── 0以外: RuntimeException("テーブルクリア処理が完全に    │
│         実行されませんでした")                                 │
└──────────────────────────────────────────────────────────────┘
         │
         ▼
┌─ Phase 2: CSV → ワークテーブル投入 ─────────────────────────┐
│ purchaseFileImportStep (Chunk=500)                           │
│                                                              │
│  Reader:  PurchaseFileReader                                 │
│  ├── MultiResourceItemReader / FlatFileItemReader            │
│  └── Encoding: "Unicode" (UTF-16LE BOM)                     │
│                                                              │
│  Processor: PurchaseFileProcessor                            │
│  ├── shopNo決定（受注と同様のロジック）                        │
│  ├── 手打ち仕入先チェック                                     │
│  └── 数量/単価補正                                            │
│                                                              │
│  Writer:  PurchaseFileWriter                                 │
│  └── WSmilePurchaseOutputFile変換 → save()                   │
└──────────────────────────────────────────────────────────────┘
         │
         ▼
┌─ Phase 3: ワーク → 本テーブルUPSERT ────────────────────────┐
│ SmilePurchaseImportTasklet.execute()                          │
│                                                              │
│  3a. 新規登録 (NewSmilePurchaseProcessor)                    │
│  ├── findNewPurchases(pageable): ワークにあるがt_purchaseに   │
│  │   存在しないレコード取得                                   │
│  ├── スキップ: 商品名に"消費税"含む or 商品コード空             │
│  ├── MSupplier検索/新規登録                                   │
│  ├── TPurchase生成 (extPurchaseNo=shoriRenban)               │
│  ├── TPurchaseDetail生成 (TaxCategory決定ロジック含む)        │
│  └── TPurchase.totalAmount再計算                              │
│                                                              │
│  3b. 更新処理 (UpdateSmilePurchaseProcessor)                 │
│  ├── findModifiedPurchases(pageable): 差分検出                │
│  ├── TPurchaseDetail: 各フィールド差分更新                    │
│  ├── TPurchase: 仕入日付差分更新                              │
│  └── 合計再計算                                               │
│                                                              │
│  3c. 削除処理 (DeleteSmilePurchaseProcessor)                 │
│  ├── 本テーブルにありワークに対応なし → 削除対象              │
│  │   （仕入日付の最小〜最大範囲内）                           │
│  ├── 物理削除順: t_purchase_detail → t_purchase              │
│  └── TPurchase合計再計算                                      │
└──────────────────────────────────────────────────────────────┘
```

---

## 4. 支払情報連携設計

### 4.1 AccountsPayableVerificationConfig 支払取込Step構成

**ソースファイル**: `src/main/java/jp/co/oda32/batch/finance/config/AccountsPayableVerificationConfig.java`

支払情報取込は2つのジョブコンテキストで使用される。

#### (A) スタンドアロン実行: SmilePaymentImportBatch

```
┌──────────────────────────────────────────────────────────────┐
│ Job: smilePaymentImportJob                                    │
│ エントリポイント: SmilePaymentImportBatch                      │
│ Incrementer: RunIdIncrementer                                 │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  Step 1: smilePaymentImportStep                               │
│  [Chunk<SmilePaymentFile, WSmilePayment>(100), FaultTolerant] │
│  ┌─────────────────────────────────────────────────────┐      │
│  │ Reader:    SmilePaymentFileReader                   │      │
│  │ Processor: SmilePaymentProcessor                    │      │
│  │ Writer:    SmilePaymentWriter                       │      │
│  │ skip(Exception.class), skipLimit(10000)             │      │
│  └─────────────────────────────────────────────────────┘      │
└──────────────────────────────────────────────────────────────┘
```

#### (B) 買掛金検証ジョブ内ステップ: AccountsPayableVerificationConfig

```
┌──────────────────────────────────────────────────────────────┐
│ Job: accountsPayableVerification                              │
│ Bean名: accountsPayableVerificationJob                        │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  Step 1: smilePaymentWorkTableInitStep [Tasklet]              │
│  └── SmilePaymentWorkTableInitTasklet                         │
│      └── TRUNCATE TABLE w_smile_payment                       │
│          │                                                    │
│          ▼                                                    │
│  Step 2: smilePaymentImportStep                               │
│  [Chunk<SmilePaymentFile, WSmilePayment>(100), FaultTolerant] │
│  ┌─────────────────────────────────────────────────────┐      │
│  │ Reader:    SmilePaymentFileReader                   │      │
│  │ Processor: SmilePaymentProcessor                    │      │
│  │ Writer:    SmilePaymentWriter                       │      │
│  │ skip(Exception.class), skipLimit(10000)             │      │
│  └─────────────────────────────────────────────────────┘      │
│          │                                                    │
│          ▼                                                    │
│  Step 3: accountsPayableVerificationStep [Tasklet]            │
│  └── AccountsPayableVerificationTasklet                       │
│          │                                                    │
│          ▼                                                    │
│  Step 4: accountsPayableVerificationReportStep [Tasklet]      │
│  └── AccountsPayableVerificationReportTasklet                 │
└──────────────────────────────────────────────────────────────┘
```

### 4.2 WSmilePayment / TSmilePaymentエンティティ設計

両テーブルは同一フィールド構成。`WSmilePayment.toTSmilePayment()`メソッドで変換可能。

**WSmilePayment**: `jp.co.oda32.domain.model.smile.WSmilePayment` (テーブル: `w_smile_payment`)
**TSmilePayment**: `jp.co.oda32.domain.model.smile.TSmilePayment` (テーブル: `t_smile_payment`)

#### 複合主キー

| エンティティ | 内部クラス名 | PK構成 |
|------------|-----------|--------|
| WSmilePayment | `WSmilePayment.WSmilePaymentId` | processingSerialNumber(Long) + lineNo(Integer) |
| TSmilePayment | `TSmilePayment.TSmilePaymentId` | processingSerialNumber(Long) + lineNo(Integer) |

#### 全フィールド定義

| フィールド名 | カラム名 | 型 | 説明 |
|------------|---------|-----|------|
| processingSerialNumber | processing_serial_number | Long | 処理連番（PK） |
| lineNo | line_no | Integer | 行（PK） |
| voucherDate | voucher_date | LocalDate | 伝票日付 |
| yearMonth | yearmonth | String | 年月度 |
| voucherNo | voucher_no | String | 伝票番号 |
| supplierCode | supplier_code | String | 仕入先コード |
| supplierName1 | supplier_name1 | String | 仕入先名1 |
| supplierName2 | supplier_name2 | String | 仕入先名2 |
| supplierNameAbbr | supplier_name_abbr | String | 仕入先名略称 |
| officeCode | office_code | String | 営業所コード |
| officeName | office_name | String | 営業所名 |
| departmentCode | department_code | String | 部門コード |
| departmentName | department_name | String | 部門名 |
| areaCode | area_code | String | 地区コード |
| areaName | area_name | String | 地区名 |
| industryCode | industry_code | String | 業種コード |
| industryName | industry_name | String | 業種名 |
| supplierClass4Code〜9Name | supplier_class4_code〜9_name | String | 仕入先分類4〜9 |
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
| importDate | import_date | LocalDate | 取込日（バッチ実行日。`LocalDate.now()`） |

### 4.3 UPSERT処理のSQL相当ロジック

**サービス**: `TSmilePaymentService.synchronizePaymentData(LocalDate voucherDate)`

```
synchronizePaymentData(voucherDate):
    │
    ├── [1] ワークテーブルからデータ取得
    │   └── wSmilePaymentRepository.findByVoucherDate(voucherDate)
    │       → List<WSmilePayment> workPayments
    │
    ├── [2] 処理連番セット取得
    │   └── wSmilePaymentRepository
    │       .findDistinctProcessingSerialNumbersByVoucherDate(voucherDate)
    │       → Set<Long> workProcessingSerialNumbers
    │       JPQL: SELECT DISTINCT w.processingSerialNumber
    │             FROM WSmilePayment w
    │             WHERE w.voucherDate = :voucherDate
    │
    ├── [3] UPSERT（INSERT or UPDATE）
    │   ├── workPayments.stream()
    │   │   .map(WSmilePayment::toTSmilePayment)  // W→T変換
    │   │   .collect(Collectors.toList())
    │   └── tSmilePaymentRepository.saveAll(tSmilePayments)
    │       └── JPA: 主キー存在 → UPDATE / 不存在 → INSERT
    │       ※SQL相当:
    │       INSERT INTO t_smile_payment (...) VALUES (...)
    │       ON CONFLICT (processing_serial_number, line_no)
    │       DO UPDATE SET voucher_date=..., supplier_code=..., ...
    │
    └── [4] 不要レコード削除（CSVから消えた支払情報の除去）
        └── tSmilePaymentRepository
            .deleteByVoucherDateAndProcessingSerialNumberNotIn(
                voucherDate, workProcessingSerialNumbers)
            JPQL: DELETE FROM TSmilePayment t
                  WHERE t.voucherDate = :voucherDate
                  AND t.processingSerialNumber NOT IN :processingSerialNumbers
```

**SmilePaymentWriter内の処理順序**:

```java
@Override
@Transactional
public void write(List<? extends WSmilePayment> items) {
    // 1. null除外
    List<WSmilePayment> validItems = items.stream()
            .filter(Objects::nonNull).collect(Collectors.toList());

    // 2. ワークテーブル一括保存
    tSmilePaymentService.saveAllToWorkTable(validItems);

    // 3. 伝票日付でグループ化
    Map<LocalDate, List<WSmilePayment>> paymentsByDate = new HashMap<>();
    for (WSmilePayment payment : validItems) {
        LocalDate voucherDate = payment.getVoucherDate();
        if (voucherDate != null) {
            paymentsByDate.computeIfAbsent(voucherDate, k -> new ArrayList<>())
                          .add(payment);
        } else {
            log.warn("伝票日付がnullのデータがあります: {}",
                     payment.getProcessingSerialNumber());
        }
    }

    // 4. 伝票日付単位で同期実行
    for (LocalDate voucherDate : paymentsByDate.keySet()) {
        tSmilePaymentService.synchronizePaymentData(voucherDate);
    }
}
```

---

## 5. 得意先マスタ連携設計

### 5.1 PartnerFileReader 処理フロー

**ソースファイル**: `src/main/java/jp/co/oda32/batch/smile/PartnerFileReader.java`
**インターフェース**: `ItemStreamReader<PartnerFile>`

```
┌── @BeforeStep(StepExecution) ──────────────────────────────┐
│                                                             │
│  1. ClassLoader.getSystemResource("input/partner_import.csv")
│     └── null: log.warn出力、readerはnullのまま              │
│                                                             │
│  2. FlatFileItemReader<PartnerFile>作成                      │
│     ├── setResource(new UrlResource(fileUrl))               │
│     ├── setEncoding("Unicode")                              │
│     ├── DelimitedLineTokenizer                              │
│     │   └── setNames(PartnerFile.getPartnerFileFormat())    │
│     └── BeanWrapperFieldSetMapper                           │
│         └── setTargetType(PartnerFile.class)                │
│                                                             │
│  3. reader.open(context)                                    │
└─────────────────────────────────────────────────────────────┘
```

**注意点**:
- `ClassLoader.getSystemResource()`を使用するため、ファイルはクラスパス上に配置が必要
- `setDistanceLimit(0)`は設定されていない（受注側と差異あり）

### 5.2 PartnerFileProcessor 処理フロー

**ソースファイル**: `src/main/java/jp/co/oda32/batch/smile/PartnerFileProcessor.java`
**インターフェース**: `ItemProcessor<PartnerFile, PartnerFile>`

```
process(PartnerFile item):
    │
    ├── [1] 得意先コード空チェック
    │   └── StringUtil.isEmpty(得意先コード) → return null (スキップ)
    │
    ├── [2] 手打ち得意先チェック
    │   └── 得意先コード == Constants.FIXED_PARTNER_CODE → return null (スキップ)
    │
    ├── [3] 休止チェック
    │   ├── 得意先名1.contains("休止") → return null (スキップ)
    │   └── 得意先名略称.contains("休止") → return null (スキップ)
    │
    └── return item
```

### 5.3 PartnerFileWriter 処理フロー

**ソースファイル**: `src/main/java/jp/co/oda32/batch/smile/PartnerFileWriter.java`
**インターフェース**: `ItemWriter<PartnerFile>`
**スコープ**: `@StepScope`

```
write(List<PartnerFile> items):
    │
    for each PartnerFile item:
    │
    ├── [1] ログ出力
    │   └── "partner_code:%s,得意先名:%s"
    │
    ├── [2] WSmilePartner変換
    │   └── BeanUtils.copyProperties(item, wSmilePartner)
    │       ※ フィールド名が一致するプロパティを自動コピー
    │
    ├── [3] shopNo決定
    │   └── getShopNoFromItem(item)
    │       ├── OfficeCode.purse(営業所コード)
    │       ├── DAINI       → OfficeShopNo.DAINI(2)
    │       ├── CLEAN_LABO  → OfficeShopNo.CLEAN_LABO(3)
    │       ├── DAIICHI     → OfficeShopNo.DAIICHI(1)
    │       ├── INNER_PURCHASE → OfficeShopNo.INNER_PURCHASE(1)
    │       ├── INNER_ORDER → OfficeShopNo.INNER_ORDER(1)
    │       └── null(未知)  → OfficeShopNo.INNER_ORDER(1)
    │
    └── [4] 保存
        └── wSmilePartnerService.save(wSmilePartner)
```

### 5.4 PartnerSaveTasklet 処理フロー

**ソースファイル**: `src/main/java/jp/co/oda32/batch/smile/PartnerSaveTasklet.java`

```
execute():
    │
    ├── [1] WSmilePartnerService.findAll() → 全ワークレコード取得
    │
    └── for each WSmilePartner:
        │
        ├── [2] MPartnerRepository: 得意先コードで検索
        │
        ├── [3a] 既存の場合:
        │   ├── partnerName差分 → MPartner.partnerName更新
        │   ├── abbreviatedPartnerName差分 → MPartner.abbreviatedPartnerName更新
        │   └── MCompanyの対応フィールドも連動更新
        │
        └── [3b] 存在しない場合:
            ├── MPartner新規作成
            │   ├── partnerCode = 得意先コード
            │   ├── partnerName = 得意先名1 + 得意先名2
            │   ├── abbreviatedPartnerName = 得意先名略称
            │   └── shopNo = shopNo
            │
            └── MCompany新規作成
                ├── companyName = 得意先名1 + 得意先名2
                ├── abbreviatedCompanyName = 得意先名略称
                └── companyType = CompanyType.PARTNER
```

### 5.5 ジョブ全体構成

```
┌──────────────────────────────────────────────────────────────┐
│ 得意先インポートジョブ (Partner Import Job)                     │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  Step 1: wSmilePartnerTruncateStep [Tasklet]                  │
│  └── WSmilePartnerTrancateTasklet                             │
│      └── w_smile_partnerをTRUNCATE                            │
│          │                                                    │
│          ▼                                                    │
│  Step 2: partnerFileImportStep [Chunk]                        │
│  ┌─────────────────────────────────────────────────────┐      │
│  │ Reader:    PartnerFileReader                        │      │
│  │ Processor: PartnerFileProcessor                     │      │
│  │ Writer:    PartnerFileWriter (→ w_smile_partner)    │      │
│  └─────────────────────────────────────────────────────┘      │
│          │                                                    │
│          ▼                                                    │
│  Step 3: partnerSaveStep [Tasklet]                            │
│  └── PartnerSaveTasklet                                       │
│      └── w_smile_partner → m_partner / m_company 同期         │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

---

## 6. 注文ファイル出力設計

### 6.1 SmileOrderFileOutPutTasklet

**ソースファイル**: `src/main/java/jp/co/oda32/batch/bcart/SmileOrderFileOutPutTasklet.java`
**役割**: B-CART経由の受注データをSMILE取込用CSVとして出力する

#### 出力CSV仕様

| 項目 | 値 |
|------|-----|
| 文字エンコーディング | UTF-8（StandardCharsets.UTF_8） |
| CSVフォーマット | Apache Commons CSV（CSVFormat.DEFAULT） |
| 引用符モード | QuoteMode.ALL_NON_NULL（null以外全カラムをダブルクォート） |
| 出力先 | MShopLinkedFile.smileOrderOutputFileName（B_CART_ORDER店舗） |
| 既存ファイル | FileUtil.renameCurrentFile()でリネーム退避 |

#### 出力CSVヘッダー（SmileOrderImportCsvHeader.CSV_HEADERS）

全57カラム:

| # | ヘッダー名 | データソース |
|---|----------|-----------|
| 0 | 伝票日付 | DateTimeUtil.localDateToSlipDate(slipDate) |
| 1 | 伝票No | slipNumber |
| 2 | 処理連番 | processingSerialNumber |
| 3 | 明細区分 | detailType |
| 4 | 行 | lineNumber |
| 5 | 得意先コード | customerCode |
| 6 | 得意先名1 | customerCompName |
| 7 | 得意先名2 | customerCompName2 |
| 8 | 納品先コード | deliveryCode |
| 9 | 納品先名 | deliveryCompName |
| 10 | 担当者コード | personInChargeCode |
| 11 | 請求区分 | billingType |
| 12 | 売掛区分 | accountsReceivableType |
| 13 | 取引区分 | transactionType |
| 14 | 取引区分属性 | transactionTypeAttribute |
| 15 | 商品コード | productCode |
| 16 | 商品名 | productName |
| 17 | 入数 | setQuantity |
| 18 | 個数 | orderProCount |
| 19 | 個数単位 | countUnit |
| 20 | 数量 | quantity |
| 21 | 数量単位 | quantityUnit |
| 22 | 単価 | unitPrice |
| 23 | 金額 | amount |
| 24 | 原単価 | originalUnitPrice |
| 25 | 原価金額 | costAmount |
| 26 | 粗利 | grossMargin |
| 27 | 単価掛率 | unitPriceMargin |
| 28 | 課税区分 | taxType |
| 29 | 消費税率 | taxRate |
| 30 | 内消費税等 | internalConsumptionTax |
| 31 | 行摘要コード | lineSummaryCode |
| 32 | 行摘要1 | lineSummary1 |
| 33 | 行摘要2 | lineSummary2 |
| 34 | 備考コード | remarksCode |
| 35 | 備考 | remarks |
| 36 | 受注No | orderNumber |
| 37 | 受注行 | orderLine |
| 38 | 見積処理連番 | quoteProcessingSerialNumber |
| 39 | 見積行 | quoteLine |
| 40 | 自動生成区分 | autoGeneratedType |
| 41 | 伝票消費税計算区分 | slipConsumptionTaxCalculationType |
| 42 | データ発生区分 | dataOccurrenceType |
| 43 | 相手処理連番 | counterpartProcessingSerialNumber |
| 44 | 入力パターンNo | inputPatternNumber |
| 45 | 伝票番号 | slipSerialNumber |
| 46 | 相手伝票番号 | counterpartSlipSerialNumber |
| 47 | コード | code1 |
| 48 | コード | code2 |
| 49 | 社店コード | companyStoreCode |
| 50 | 分類コード | classificationCode |
| 51 | 伝票区分 | slipType |
| 52 | 取引先コード | tradingPartnerCode |
| 53 | 売単価 | sellingUnitPrice |
| 54 | 相手商品コード | counterpartProductCode |
| 55 | チェックマーク区分 | checkMarkType |
| 56 | 消費税分類 | consumptionTaxClassification |

#### 処理フロー

```
execute():
    │
    ├── [1] TSmileOrderImportFileService.findByCsvExportedFalse()
    │   └── csvExported=false（未連携）のレコード一覧取得
    │
    ├── [2] MShopLinkedFileService.getByShopNo(B_CART_ORDER)
    │   └── B-CART受注店舗の出力ファイルパス取得
    │
    ├── [3] ソート
    │   └── slipNumber昇順 → lineNumber昇順
    │       （軽減税率混在時のSMILE連番取得仕様に対応）
    │
    ├── [4] CSV出力
    │   ├── FileUtil.renameCurrentFile(outputFilePath)  ← 既存ファイル退避
    │   └── CSVPrinter出力 → flush
    │
    └── [5] csvExported=true に更新
        └── TSmileOrderImportFileService.save(updatedOutputList)
```

---

## 7. パートナーファイル出力設計

### 7.1 SmilePartnerFileOutPutTasklet

**ソースファイル**: `src/main/java/jp/co/oda32/batch/bcart/SmilePartnerFileOutPutTasklet.java`
**役割**: B-CART会員データをSMILE得意先マスタ取込用CSVとして出力する

#### 出力CSV仕様

| 項目 | 値 |
|------|-----|
| 文字エンコーディング | UTF-8（StandardCharsets.UTF_8） |
| CSVフォーマット | Apache Commons CSV（CSVFormat.DEFAULT） |
| 引用符モード | QuoteMode.ALL_NON_NULL |
| 出力先 | MShopLinkedFile.smilePartnerOutputFileName（B_CART_ORDER店舗） |
| 既存ファイル | FileUtil.renameCurrentFile()でリネーム退避 |

#### 出力CSVヘッダー（SmilePartnerImportCsvHeader.CSV_HEADERS）

全74カラム:

| # | ヘッダー名 | データソース | 備考 |
|---|----------|-----------|------|
| 0 | 得意先コード | bCartMember.extId | 文字6桁 |
| 1 | 得意先名1 | bCartMember.compName | 文字48桁 |
| 2 | 得意先名2 | null | 文字48桁 |
| 3 | 得意先名略称 | null | 文字32桁 |
| 4 | 得意先名索引 | StringUtil.convertToHalfWidthIncludingKatakana(compNameKana) | 文字10桁、半角カナ変換 |
| 5 | 郵便番号 | bCartMember.zip | 文字10桁 |
| 6 | 住所1 | bCartMember.pref + bCartMember.address1 | 文字48桁、都道府県+住所結合 |
| 7 | 住所2 | bCartMember.address2 | 文字48桁 |
| 8 | 住所3 | bCartMember.address3 | 文字48桁 |
| 9 | カスタマバーコード | null | 文字20桁 |
| 10 | 電話番号 | bCartMember.tel | 文字15桁 |
| 11 | FAX番号 | bCartMember.fax | 文字15桁 |
| 12〜21 | 営業所〜分類9 | null | 各文字8桁 |
| 22 | 請求先コード | null | 文字6桁 |
| 23 | 請求先区分 | 0 | 整数1桁 |
| 24 | 担当者コード | "000005" | 固定値 |
| 25〜37 | 締日〜与信限度額 | 0/null | |
| 38 | 売上単価計算区分 | 0 | |
| 39 | 単価掛率 | null | |
| 40 | 単価ランク | 0 | |
| 41 | 単価処理区分 | 0 | |
| 42 | 単価処理単位 | 0.01 | |
| 43 | 金額処理区分 | 2 | |
| 44 | 課税対象区分 | 0 | |
| 45〜46 | 消費税売上/上代単価設定区分 | 0 | |
| 47 | 単価変換区分 | 0 | |
| 48 | 消費税通知区分 | 1 | |
| 49 | 消費税計算処理区分 | 0 | |
| 50 | 消費税計算処理単位 | 1 | |
| 51 | 消費税分解処理区分 | 0 | |
| 52〜55 | 請求書出力〜請求消費税算出単位 | 0/null | |
| 56〜57 | 売掛前残高/前回請求残高 | 0 | |
| 58 | 相殺仕入先コード | null | |
| 59 | 日付印字区分 | 0 | |
| 60 | 相手先担当者名 | tantoLastName + tantoFirstName | |
| 61 | 取引 | 0 | |
| 62〜64 | 会社名パターン | "0000" | |
| 65〜70 | マスター検索表示〜商品コード入力モード | 0 | |
| 71 | 個別設定入力行数 | 0 | |
| 72〜73 | 社店/分類/伝票区分/取引先コード | null | |
| 74〜75 | 有効期間開始/終了日 | null | |

#### 処理フロー

```
execute():
    │
    ├── [1] BCartMemberService.findBySmilePartnerMasterNotLinked()
    │   └── smilePartnerMasterLinked=false（未連携）のB-CART会員取得
    │
    ├── [2] MShopLinkedFileService.getByShopNo(B_CART_ORDER)
    │   └── B-CART受注店舗の出力ファイルパス取得
    │
    ├── [3] CSV出力
    │   ├── FileUtil.renameCurrentFile(outputFilePath)
    │   └── CSVPrinter出力
    │
    └── [4] smilePartnerMasterLinked=true に更新
        └── BCartMemberService.save(updatedOutputList)
```

---

## 8. ワークテーブルTRUNCATE設計

### 8.1 TRUNCATE処理パターン一覧

SMILE連携で使用されるワークテーブルのTRUNCATE処理は3パターン存在する。

| # | Tasklet/Repository | 対象テーブル | 実装方式 | トランザクション管理 |
|---|-------------------|-----------|---------|-----------------|
| A | SmilePaymentWorkTableInitTasklet | w_smile_payment | JdbcTemplate.execute() | PlatformTransactionManager手動管理 |
| B | WSmilePurchaseOutputFileTrancateTasklet | w_smile_purchase_output_file | EntityManager.createNativeQuery() | @Transactional(javax.transaction) |
| C | WSmileOrderOutputFileRepository.truncateTable() | w_smile_order_output_file | @Modifying + nativeQuery | @Transactional(javax.transaction) |

### 8.2 パターンA: JdbcTemplate + 手動トランザクション

**ソースファイル**: `src/main/java/jp/co/oda32/batch/smile/SmilePaymentWorkTableInitTasklet.java`

```java
@Override
public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    TransactionStatus status = null;
    try {
        // 手動トランザクション開始
        status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // DDL実行
        jdbcTemplate.execute("TRUNCATE TABLE w_smile_payment");

        // コミット
        transactionManager.commit(status);
        return RepeatStatus.FINISHED;
    } catch (Exception e) {
        // ロールバック
        if (status != null && !status.isCompleted()) {
            transactionManager.rollback(status);
        }
        throw e;
    }
}
```

**設計判断理由**: TRUNCATEはDDL文であり、PostgreSQLではトランザクション内で実行可能だが、JPA管理外のため`JdbcTemplate`を使用。Spring Batchのstepトランザクションとの干渉を避けるため手動トランザクション管理を採用。

### 8.3 パターンB: EntityManager + フォールバック

**ソースファイル**: `src/main/java/jp/co/oda32/batch/smile/WSmilePurchaseOutputFileTrancateTasklet.java`

```java
@Override
@Transactional
public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    // 前行数確認
    Number beforeCount = (Number) entityManager
        .createNativeQuery("SELECT COUNT(*) FROM w_smile_purchase_output_file")
        .getSingleResult();

    if (beforeCount.intValue() == 0) return RepeatStatus.FINISHED;

    try {
        // TRUNCATE試行
        entityManager.createNativeQuery(
            "TRUNCATE TABLE w_smile_purchase_output_file RESTART IDENTITY")
            .executeUpdate();
        entityManager.flush();
    } catch (Exception truncateException) {
        // フォールバック: DELETE
        int deletedRows = entityManager.createNativeQuery(
            "DELETE FROM w_smile_purchase_output_file").executeUpdate();
        entityManager.flush();
    }

    // 後行数検証
    Number afterCount = (Number) entityManager
        .createNativeQuery("SELECT COUNT(*) FROM w_smile_purchase_output_file")
        .getSingleResult();

    if (afterCount.intValue() != 0) {
        throw new RuntimeException("テーブルクリア処理が完全に実行されませんでした");
    }

    return RepeatStatus.FINISHED;
}
```

**`RESTART IDENTITY`**: シーケンスのリセットを伴うTRUNCATE。PostgreSQL固有構文。

### 8.4 パターンC: @Modifying + nativeQuery

**ソースファイル**: `src/main/java/jp/co/oda32/domain/repository/smile/WSmileOrderOutputFileRepository.java`

```java
@Modifying
@Transactional
@Query(value = "TRUNCATE TABLE w_smile_order_output_file", nativeQuery = true)
void truncateTable();
```

**WSmilePurchaseOutputFileRepository**:
```java
@Modifying(clearAutomatically = true)
@Transactional
@Query(value = "TRUNCATE TABLE w_smile_purchase_output_file RESTART IDENTITY", nativeQuery = true)
void truncateTable();
```

**`clearAutomatically = true`の効果**: TRUNCATE実行後、永続性コンテキストを自動クリアし、古いエンティティキャッシュが残ることを防止。

### 8.5 トランザクション境界の注意点

| 注意点 | 詳細 |
|--------|------|
| DDLのロールバック | PostgreSQLではDDL（TRUNCATE）もトランザクション内で実行でき、ロールバック可能。ただしMySQLなどでは暗黙コミットとなる |
| JPAキャッシュ | TRUNCATE後は`EntityManager.clear()`または`@Modifying(clearAutomatically = true)`で永続性コンテキストをクリアすること |
| Spring Batchトランザクション | Taskletは自身のトランザクション内で実行されるため、JdbcTemplateのDDL実行はSpring Batchのトランザクションに巻き込まれる可能性がある |
| フォールバック戦略 | TRUNCATEが権限不足やロック競合で失敗する場合に備え、DELETEへのフォールバックを実装 |

---

## 9. ファイル移動設計

### 9.1 FileManagerTasklet

**ソースファイル**: `src/main/java/jp/co/oda32/batch/util/FileManagerTasklet.java`

#### 入力 → アーカイブフロー

```
execute():
    │
    ├── [1] ソースファイル取得
    │   └── this.resources.getFile()
    │       → 例: /path/to/input/smile_order_import.csv
    │
    ├── [2] 移動先パス構築
    │   ├── srcFile.getParentFile().getParent() → /path/to
    │   ├── + "/completed/"
    │   ├── + srcFile.getName() → smile_order_import.csv
    │   └── + "_" + DateTimeUtil.getNowTimestampStr()
    │       → 例: /path/to/completed/smile_order_import.csv_20260223153045
    │
    ├── [3] Files.move(srcFile.toPath(), destPath)
    │   └── 成功: 入力ファイルがcompletedディレクトリに移動
    │
    └── [4] 例外ハンドリング
        └── catch(Exception): log.error(e.getMessage())
            ※ 例外は握りつぶし、ステップは正常完了扱い
```

#### ディレクトリ構成

```
/path/to/
├── input/
│   └── smile_order_import.csv          ← 入力ファイル（処理前）
└── completed/
    ├── smile_order_import.csv_20260222100000  ← アーカイブ（タイムスタンプ付）
    └── smile_order_import.csv_20260221100000
```

#### SmileOrderFileImportConfigでの設定

```java
// SmileOrderFileImportConfig内
@Value("input/smile_order_import.csv")
private Resource inputResources;

public Step fileMoveStep() {
    this.fileManagerTasklet.setResources(inputResources);
    return stepBuilderFactory.get("fileMoveStep")
            .tasklet(fileManagerTasklet)
            .build();
}
```

**注意**: `inputResources`はクラスパスリソースとして解決される。`FileManagerTasklet`は`Resource.getFile()`でFileオブジェクトを取得するため、JAR内のリソースでは動作しない（ファイルシステム上に配置が必要）。

---

## 10. リポジトリ設計

### 10.1 WSmileOrderOutputFileRepository

**ソースファイル**: `src/main/java/jp/co/oda32/domain/repository/smile/WSmileOrderOutputFileRepository.java`
**継承**: `JpaRepository<WSmileOrderOutputFile, WSmileOrderOutputFilePK>`

| メソッド | 戻り型 | クエリ種別 | 説明 |
|---------|-------|---------|------|
| `findNewOrders(Pageable)` | `Page<WSmileOrderOutputFile>` | @Query(nativeQuery) | ワークにあるがt_delivery_detailに存在しないレコード取得。LEFT JOIN + WHERE IS NULL方式 |
| `findModifiedOrders(Pageable)` | `Page<WSmileOrderOutputFile>` | @Query(nativeQuery) | t_delivery_detail/t_order_detail/t_orderと結合し、8フィールドの差分をOR条件で検出。サブクエリでt_deliveryのslip_dateも比較 |
| `truncateTable()` | `void` | @Modifying @Query(nativeQuery) | `TRUNCATE TABLE w_smile_order_output_file` |
| `findByTorihikiKubun(BigDecimal, Pageable)` | `Page<WSmileOrderOutputFile>` | Spring Data命名規則 | 取引区分フィルタリング。処理連番更新タスクレットで使用 |
| `findByShopNoAndShoriRenban(int, long)` | `List<WSmileOrderOutputFile>` | Spring Data命名規則 | 店舗番号+処理連番で検索 |

#### findNewOrdersクエリ詳細

```sql
-- データ取得クエリ
SELECT wsoof.* FROM w_smile_order_output_file wsoof
LEFT JOIN t_delivery_detail td
  ON wsoof.shori_renban = td.processing_serial_number
  AND wsoof.gyou = td.order_detail_no
  AND td.shop_no = wsoof.shop_no
WHERE td.processing_serial_number IS NULL
ORDER BY wsoof.shop_no, wsoof.shori_renban

-- カウントクエリ（ページング用）
SELECT COUNT(*) FROM w_smile_order_output_file wsoof
LEFT JOIN t_delivery_detail td
  ON wsoof.shori_renban = td.processing_serial_number
  AND wsoof.gyou = td.order_detail_no
  AND td.shop_no = wsoof.shop_no
WHERE td.processing_serial_number IS NULL
```

#### findModifiedOrdersクエリ詳細

```sql
SELECT wsoof.* FROM w_smile_order_output_file wsoof
LEFT JOIN t_delivery_detail td
  ON wsoof.shori_renban = td.processing_serial_number
  AND wsoof.gyou = td.order_detail_no
  AND td.shop_no = wsoof.shop_no
LEFT JOIN t_order_detail od
  ON td.order_no = od.order_no
  AND td.order_detail_no = od.order_detail_no
LEFT JOIN t_order o
  ON od.order_no = o.order_no
WHERE (
  td.delivery_num != wsoof.suuryou
  OR td.goods_code != wsoof.shouhin_code
  OR wsoof.denpyou_hizuke != (
    SELECT d.slip_date FROM t_delivery d WHERE td.delivery_no = d.delivery_no
  )
  OR od.goods_name != wsoof.shouhin_mei
  OR od.goods_price != wsoof.tanka
  OR od.tax_rate != wsoof.shouhizeiritsu
  OR od.tax_type != wsoof.kazei_kubun
  OR o.partner_code != wsoof.tokuisaki_code
)
ORDER BY wsoof.shop_no, wsoof.shori_renban
```

### 10.2 WSmilePurchaseOutputFileRepository

**ソースファイル**: `src/main/java/jp/co/oda32/domain/repository/smile/WSmilePurchaseOutputFileRepository.java`
**継承**: `JpaRepository<WSmilePurchaseOutputFile, WSmilePurchaseOutputFilePK>`

| メソッド | 戻り型 | クエリ種別 | 説明 |
|---------|-------|---------|------|
| `findNewPurchases(Pageable)` | `Page<WSmilePurchaseOutputFile>` | @Query(nativeQuery) | ワークにあるがt_purchase_detailに存在しないレコード |
| `findModifiedPurchases(Pageable)` | `Page<WSmilePurchaseOutputFile>` | @Query(nativeQuery) | t_purchase_detail/t_purchaseと結合し8フィールド差分検出。`COALESCE(subtotal, 0)`でnull対応 |
| `truncateTable()` | `void` | @Modifying(clearAutomatically=true) @Query(nativeQuery) | `TRUNCATE TABLE w_smile_purchase_output_file RESTART IDENTITY` |
| `findByShopNoAndShoriRenban(int, long)` | `List<WSmilePurchaseOutputFile>` | Spring Data命名規則 | 店舗番号+処理連番で検索 |

#### findModifiedPurchasesクエリ詳細

```sql
SELECT wspof.* FROM w_smile_purchase_output_file wspof
JOIN t_purchase_detail tpd
  ON wspof.shori_renban = tpd.ext_purchase_no
  AND wspof.gyou = tpd.purchase_detail_no
  AND tpd.shop_no = wspof.shop_no
JOIN t_purchase tp
  ON tpd.purchase_no = tp.purchase_no
WHERE (
  tpd.goods_num != wspof.suuryou
  OR tpd.goods_code != wspof.shouhin_code
  OR wspof.denpyou_hizuke != tp.purchase_date
  OR tpd.goods_name != wspof.shouhin_mei
  OR tpd.goods_price != wspof.tanka
  OR COALESCE(tpd.subtotal, 0) != COALESCE(wspof.kingaku, 0)
  OR tpd.tax_rate != wspof.shouhizeiritsu
  OR tpd.tax_type != wspof.kazei_kubun
)
ORDER BY wspof.shop_no, wspof.shori_renban
```

**COALESCE使用の理由**: 値引明細の場合、金額（kingaku/subtotal）にnullが入り得るため、null同士の比較が`true`にならない問題を回避。

### 10.3 TSmilePaymentRepository

**ソースファイル**: `src/main/java/jp/co/oda32/domain/repository/smile/TSmilePaymentRepository.java`
**継承**: `JpaRepository<TSmilePayment, TSmilePayment.TSmilePaymentId>`

| メソッド | 戻り型 | クエリ種別 | 説明 |
|---------|-------|---------|------|
| `findByVoucherDate(LocalDate)` | `List<TSmilePayment>` | Spring Data命名規則 | 伝票日付で検索 |
| `findByYearMonth(String)` | `List<TSmilePayment>` | Spring Data命名規則 | 年月度で検索 |
| `findBySupplierCode(String)` | `List<TSmilePayment>` | Spring Data命名規則 | 仕入先コードで検索 |
| `findBySupplierCodeAndYearMonth(String, String)` | `List<TSmilePayment>` | Spring Data命名規則 | 仕入先コード+年月度で検索 |
| `deleteByVoucherDateAndProcessingSerialNumberNotIn(LocalDate, Set<Long>)` | `int` | @Modifying @Query(JPQL) | UPSERT後の不要レコード削除 |

#### 不要レコード削除クエリ

```sql
-- JPQL
DELETE FROM TSmilePayment t
WHERE t.voucherDate = :voucherDate
AND t.processingSerialNumber NOT IN :processingSerialNumbers
```

### 10.4 WSmilePaymentRepository

**ソースファイル**: `src/main/java/jp/co/oda32/domain/repository/smile/WSmilePaymentRepository.java`
**継承**: `JpaRepository<WSmilePayment, WSmilePayment.WSmilePaymentId>`

| メソッド | 戻り型 | クエリ種別 | 説明 |
|---------|-------|---------|------|
| `findByVoucherDate(LocalDate)` | `List<WSmilePayment>` | Spring Data命名規則 | 伝票日付で検索 |
| `findDistinctProcessingSerialNumbersByVoucherDate(LocalDate)` | `Set<Long>` | @Query(JPQL) | 処理連番の重複排除セット。UPSERT後の削除対象判定に使用 |
| `truncateTable()` | `void` | @Modifying @Transactional(REQUIRED) @Query(nativeQuery) | `TRUNCATE TABLE w_smile_payment` |

---

## 11. トランザクション管理

### 11.1 Chunk処理のcommit-interval設計

| ジョブ | ステップ | チャンクサイズ | 理由 |
|-------|---------|------------|------|
| smileOrderFileImport | smileOrderFileImportStep | **500** | 売上明細は1伝票あたり複数行。500行でメモリとI/Oのバランスを取る |
| purchaseFileImport | purchaseFileImportStep | **500** | 仕入明細も同様のデータ量 |
| smilePaymentImport | smilePaymentImportStep | **100** | 支払情報はWriter内でUPSERT処理を含むためChunk単位での同期処理コストが高い |

#### commit-intervalによるトランザクション境界

```
┌── Chunk(500) ──────────────────────────────────────────────┐
│                                                             │
│  [トランザクション開始]                                       │
│                                                             │
│  for i = 1 to 500:                                          │
│    item = reader.read()                                     │
│    processedItem = processor.process(item)                  │
│    → processedItems に蓄積                                   │
│                                                             │
│  writer.write(processedItems)  ← 500件まとめて書き込み      │
│                                                             │
│  [トランザクションコミット]                                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 11.2 skip-limit設計

| ジョブ | faultTolerant | skip対象 | skipLimit | 設計理由 |
|-------|-------------|---------|-----------|---------|
| smileOrderFileImport | **なし** | - | - | 売上明細の欠損は許容できないため、エラー時はジョブ異常終了 |
| purchaseFileImport | **なし** | - | - | 仕入明細も同様 |
| smilePaymentImport | **あり** | `Exception.class` | **10000** | 支払データは部分的に取り込めればよい。CSVのパースエラーや型変換エラーを許容 |

```java
// 支払情報取込のFaultTolerant設定
stepBuilderFactory.get("smilePaymentImportStep")
    .<SmilePaymentFile, WSmilePayment>chunk(100)
    .reader(smilePaymentFileReader)
    .processor(smilePaymentProcessor)
    .writer(smilePaymentWriter)
    .faultTolerant()
    .skip(Exception.class)    // 全例外をスキップ対象
    .skipLimit(10000)         // 10000件まで許容
    .build();
```

### 11.3 新規/更新/削除処理のトランザクション分離

受注・仕入のインポートタスクレット（SmileOrderImportTasklet, SmilePurchaseImportTasklet）では、1レコードずつ独立したトランザクションで処理する。

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void newOrderRegister(WSmileOrderOutputFile record) {
    // 1レコードの処理
    // 失敗しても他レコードの処理には影響しない
}
```

#### REQUIRES_NEW採用の設計判断

| 観点 | 説明 |
|------|------|
| 障害分離 | 1件のレコード処理失敗が、他のレコードに波及しない |
| StackOverflow回避 | 親トランザクションのロールバック伝播による連鎖障害を防止 |
| メモリ効率 | 各トランザクション完了時にJPAの永続性コンテキストがフラッシュされる |
| デメリット | レコード毎にトランザクション制御のオーバーヘッドが発生 |

### 11.4 ページング処理パターン

大量データの新規/更新/削除処理は`PageRequest`でページング分割する。

```java
// WSmileOrderOutputFileService内
public List<WSmileOrderOutputFile> handleWSmileOrderOutputFiles(int pageNumber) {
    int pageSize = 1000;
    Pageable pageable = PageRequest.of(pageNumber, pageSize);
    Page<WSmileOrderOutputFile> page = repository.findByTorihikiKubun(
        new BigDecimal(1), pageable);
    return page.getContent();
}
```

**ページングのイテレーションパターン**:

```java
Pageable pageable = PageRequest.of(0, pageSize);
Page<WSmileOrderOutputFile> page = repository.findNewOrders(pageable);
while (!page.isEmpty()) {
    for (WSmileOrderOutputFile record : page.getContent()) {
        service.newOrderRegister(record);  // REQUIRES_NEW
    }
    if (page.hasNext()) {
        page = repository.findNewOrders(page.nextPageable());
    } else {
        break;
    }
}
```

**注意**: `findNewOrders`はLEFT JOINの結果で新規対象を検出するため、1ページ処理完了後に本テーブルにデータが挿入されると、次ページ取得時に結果セットがずれる可能性がある。これはREQUIRES_NEWトランザクションの即時コミットが原因であり、pageNumber=0を常に使用し、ページが空になるまで繰り返すパターンが推奨される。

### 11.5 ワークテーブルTRUNCATEのトランザクション管理まとめ

| パターン | 実装方式 | トランザクション管理 | PostgreSQL固有要素 |
|---------|---------|-----------------|------------------|
| SmilePaymentWorkTableInitTasklet | JdbcTemplate + PlatformTransactionManager | 手動管理（begin/commit/rollback明示） | なし |
| WSmilePurchaseOutputFileTrancateTasklet | EntityManager.createNativeQuery() | @Transactional(javax.transaction) | RESTART IDENTITY |
| WSmileOrderOutputFileRepository | @Modifying nativeQuery | @Transactional(javax.transaction) | なし |
| WSmilePaymentRepository | @Modifying nativeQuery | @Transactional(propagation=REQUIRED) | なし |
| WSmilePurchaseOutputFileService | Service経由でRepository呼出 | @Transactional(propagation=REQUIRES_NEW) | RESTART IDENTITY |

---

*以上*
