# 得意先商品一括同期 改善設計書

## 1. 現状の問題

### 現在のフロー（PartnerGoodsSyncTasklet → PartnerGoodsSyncProcessor）
```
1. 全年間注文数量を0にリセット（1 UPDATE文）
2. 過去1年の注文がある得意先を全件取得
3. 得意先ごとにループ:
   a. その得意先の注文明細を全件取得（SELECT × 1）
   b. goodsNo + destinationNo でグループ化
   c. グループごとにループ:
      i.  getByPK() で既存チェック（SELECT × 1）← ★N+1
      ii. INSERT or UPDATE（1件ずつ）← ★N+1
```

### ボトルネック
- **N+1クエリ**: 得意先1社あたり、商品×納品先の組み合わせ数だけ `getByPK` + `save` が走る
- **例**: 得意先200社 × 平均50商品 = **10,000回の個別SELECT + 10,000回のINSERT/UPDATE**
- 注文明細の取得も得意先ごとに別クエリ（200回のSELECT）

## 2. 改善方針

**ワークテーブル（w_smile_order_output_file）のデータを直接集計し、SQLのINSERT ... ON CONFLICTで一括UPSERTする。**

### 改善後のフロー
```
1. 全年間注文数量を0にリセット（1 UPDATE文）← 既存と同じ
2. t_order_detail を過去1年分で集計し、一括UPSERT（1〜2 SQL文）
```

### 期待効果
- **クエリ数**: 10,000+ → 2〜3
- **トランザクション数**: 200+ → 1
- **処理時間**: 大幅短縮

## 3. 一括UPSERT SQL

```sql
INSERT INTO m_partner_goods (
    partner_no, goods_no, destination_no, shop_no, company_no,
    goods_code, goods_name, goods_price,
    order_num_per_year, last_sales_date,
    del_flg, add_date_time, add_user_no
)
SELECT
    o.partner_no,
    od.goods_no,
    o.destination_no,
    o.shop_no,
    o.company_no,
    od.goods_code,
    od.goods_name,
    od.goods_price,
    SUM(od.order_num - od.cancel_num - od.return_num) AS order_num_per_year,
    MAX(o.order_date_time::date) AS last_sales_date,
    '0',
    NOW(),
    :batchUserId
FROM t_order_detail od
JOIN t_order o ON od.order_no = o.order_no
WHERE o.order_date_time >= :oneYearAgo
  AND o.del_flg = '0'
  AND od.del_flg = '0'
  AND o.destination_no IS NOT NULL
GROUP BY o.partner_no, od.goods_no, o.destination_no,
         o.shop_no, o.company_no, od.goods_code, od.goods_name, od.goods_price
ON CONFLICT (partner_no, goods_no, destination_no)
DO UPDATE SET
    order_num_per_year = EXCLUDED.order_num_per_year,
    last_sales_date = GREATEST(m_partner_goods.last_sales_date, EXCLUDED.last_sales_date),
    modify_date_time = NOW(),
    modify_user_no = :batchUserId
```

※ goods_code, goods_name, goods_price は最新の注文明細から取得する必要があるため、サブクエリまたはDISTINCT ON で最新行を取得。

## 4. 実装詳細

### 4.1 改善案A: ネイティブSQL（推奨）

`MPartnerGoodsRepository` にネイティブクエリを追加。

```java
@Modifying
@Query(nativeQuery = true, value = """
    INSERT INTO m_partner_goods (
        partner_no, goods_no, destination_no, shop_no, company_no,
        goods_code, goods_name, goods_price,
        order_num_per_year, last_sales_date,
        del_flg, add_date_time
    )
    SELECT
        o.partner_no, od.goods_no, o.destination_no,
        o.shop_no, o.company_no,
        latest.goods_code, latest.goods_name, latest.goods_price,
        SUM(od.order_num - od.cancel_num - od.return_num),
        MAX(o.order_date_time::date),
        '0', NOW()
    FROM t_order_detail od
    JOIN t_order o ON od.order_no = o.order_no
    JOIN LATERAL (
        SELECT od2.goods_code, od2.goods_name, od2.goods_price
        FROM t_order_detail od2
        JOIN t_order o2 ON od2.order_no = o2.order_no
        WHERE od2.goods_no = od.goods_no
          AND o2.partner_no = o.partner_no
          AND o2.destination_no = o.destination_no
        ORDER BY o2.order_date_time DESC
        LIMIT 1
    ) latest ON true
    WHERE o.order_date_time >= :oneYearAgo
      AND o.del_flg = '0'
      AND od.del_flg = '0'
      AND o.destination_no IS NOT NULL
    GROUP BY o.partner_no, od.goods_no, o.destination_no,
             o.shop_no, o.company_no,
             latest.goods_code, latest.goods_name, latest.goods_price
    ON CONFLICT (partner_no, goods_no, destination_no)
    DO UPDATE SET
        order_num_per_year = EXCLUDED.order_num_per_year,
        last_sales_date = GREATEST(m_partner_goods.last_sales_date, EXCLUDED.last_sales_date),
        modify_date_time = NOW()
    """)
int bulkUpsertFromOrderDetails(@Param("oneYearAgo") LocalDateTime oneYearAgo);
```

### 4.2 Tasklet の変更

```java
@Override
public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    // Step 1: 全年間注文数量を0にリセット
    int cleared = partnerGoodsService.updateAllClearOrderNumPerYear();
    log.info("全年間注文数量を0に更新: {}件", cleared);

    // Step 2: 一括UPSERT
    LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
    int upserted = partnerGoodsRepository.bulkUpsertFromOrderDetails(oneYearAgo);
    log.info("得意先商品一括同期完了: {}件", upserted);

    return RepeatStatus.FINISHED;
}
```

## 5. 変更ファイル

| ファイル | 変更内容 |
|---------|---------|
| `MPartnerGoodsRepository.java` | `bulkUpsertFromOrderDetails()` ネイティブクエリ追加 |
| `PartnerGoodsSyncTasklet.java` | ループ処理を一括SQL呼び出しに置換 |
| `PartnerGoodsSyncProcessor.java` | 不要になるため削除 or 残置 |

## 6. リスク

| リスク | 対策 |
|--------|------|
| ON CONFLICT に必要なユニーク制約がない | DBのm_partner_goodsテーブルにUNIQUE(partner_no, goods_no, destination_no)が存在するか確認。なければ追加 |
| LATERAL JOINのパフォーマンス | t_order_detailにgoods_no, t_orderにpartner_noのインデックスが必要 |
| 既存の `PartnerGoodsSyncProcessor` を使う他の処理がないか | grep で確認。Taskletからのみ呼ばれていれば安全に削除可 |
| add_user_no の設定 | ネイティブSQLではCustomServiceの監査処理が動かない。バッチIDまたはNULLを直接設定 |

## 7. 複雑度: 低

Repositoryに1メソッド追加、Taskletを2行に簡素化するだけ。
