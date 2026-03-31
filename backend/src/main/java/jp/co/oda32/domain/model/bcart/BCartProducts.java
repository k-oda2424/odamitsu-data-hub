package jp.co.oda32.domain.model.bcart;

import com.google.gson.annotations.SerializedName;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import lombok.*;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.util.Date;
import java.util.List;

/**
 * B-CART商品マスタ（基本）
 *
 * @author k_oda
 * @since 2023/04/21
 */
@Getter
@Setter
@RequiredArgsConstructor
@Builder
@Entity
@AllArgsConstructor
@Table(name = "b_cart_products")
public class BCartProducts {
    @Id
    @Column(name = "id", nullable = false)
    @SerializedName("id")
    private Integer id; // 商品ID

    @Column(name = "main_no", length = 255)
    @SerializedName("main_no")
    private String mainNo; // 商品管理番号

    @Column(name = "name", length = 255)
    @SerializedName("name")
    private String name; // 商品名

    @Column(name = "catch_copy", length = 65535)
    @SerializedName("catch_copy")
    private String catchCopy; // キャッチコピー

    @Column(name = "category_id")
    @SerializedName("category_id")
    private Integer categoryId; // カテゴリID

    @Column(name = "sub_category_id", length = 255)
    @SerializedName("sub_category_id")
    private String subCategoryId; // サブカテゴリID（カンマ区切り）

    @Column(name = "feature_id1")
    @SerializedName("feature_id1")
    private Integer featureId1; // 特集ID1

    @Column(name = "feature_id2")
    @SerializedName("feature_id2")
    private Integer featureId2; // 特集ID2

    @Column(name = "feature_id3")
    @SerializedName("feature_id3")
    private Integer featureId3; // 特集ID3

    @Column(name = "made_in", length = 255)
    @SerializedName("made_in")
    private String madeIn; // 生産地

    @Column(name = "size", length = 65535)
    @SerializedName("size")
    private String size; // サイズ

    @Column(name = "sozai", length = 65535)
    @SerializedName("sozai")
    private String sozai; // 素材

    @Column(name = "caution", length = 65535)
    @SerializedName("caution")
    private String caution; // 注意事項

    @Column(name = "tag", length = 21)
    @SerializedName("tag")
    private String tag; // 商品特徴（new/recommend/limitedなど）

    @Column(name = "description", length = 65535)
    @SerializedName("description")
    private String description; // 説明

    @Column(name = "meta_title", length = 255)
    @SerializedName("meta_title")
    private String metaTitle; // META情報タイトル

    @Column(name = "meta_keywords", length = 255)
    @SerializedName("meta_keywords")
    private String metaKeywords; // META情報キーワード

    @Column(name = "meta_description", length = 255)
    @SerializedName("meta_description")
    private String metaDescription; // META情報説明

    @Column(name = "image", length = 255)
    @SerializedName("image")
    private String image; // 商品画像パス
    @SerializedName("view_group_filter")
    private String viewGroupFilter; // 商品非表示グループ（表示グループIDをカンマ区切り）

    @Column(name = "visible_customer_id", length = 65535)
    @SerializedName("visible_customer_id")
    private String visibleCustomerId; // 例外的に表示させる会員ID（会員IDをカンマ区切り）

    @Column(name = "prepend_text", length = 65535)
    @SerializedName("prepend_text")
    private String prependText; // 上部フリースペース（PC表示）

    @Column(name = "append_text", length = 65535)
    @SerializedName("append_text")
    private String appendText; // 下部フリースペース（PC表示）

    @Column(name = "middle_text", length = 65535)
    @SerializedName("middle_text")
    private String middleText; // 中部フリースペース（PC表示）

    @Column(name = "rv_prepend_text", length = 65535)
    @SerializedName("rv_prepend_text")
    private String rvPrependText; // 上部フリースペース（レスポンシブ表示）

    @Column(name = "rv_append_text", length = 65535)
    @SerializedName("rv_append_text")
    private String rvAppendText; // 下部フリースペース（レスポンシブ表示）

    @Column(name = "rv_middle_text", length = 65535)
    @SerializedName("rv_middle_text")
    private String rvMiddleText; // 中部フリースペース（レスポンシブ表示）

    @Column(name = "file_download", length = 255)
    @SerializedName("file_download")
    private String fileDownload; // ダウンロードファイルパス

    @Type(value = ListArrayType.class, parameters = {
            @org.hibernate.annotations.Parameter(name = ListArrayType.SQL_ARRAY_TYPE, value = "text")
    })
    @Column(name = "customs", columnDefinition = "text[]")
    @SerializedName("customs")
    private List<Object> customs; //  カスタム項目

    @Column(name = "hanbai_start")
    @SerializedName("hanbai_start")
    private Date hanbaiStart; // 販売期間開始（Y-m-d H:i:s）

    @Column(name = "hanbai_end")
    @SerializedName("hanbai_end")
    private Date hanbaiEnd; // 販売期間終了（Y-m-d H:i:s）

    @Column(name = "recommend_product_id", length = 65535)
    @SerializedName("recommend_product_id")
    private String recommendProductId; // レコメンドで表示させる商品ID（商品IDをカンマ区切り）

    @Column(name = "view_pattern")
    @SerializedName("view_pattern")
    private Integer viewPattern; // 商品一覧表示パターン（0〜5。標準は0）

    @Column(name = "priority")
    @SerializedName("priority")
    private Integer priority; // 表示順（-32,768 ～ 32,767）

    @Column(name = "flag", length = 3)
    @SerializedName("flag")
    private String flag; // 状態（'表示', '非表示'）

    @Column(name = "updated_at")
    @SerializedName("updated_at")
    private Date updatedAt; // 更新日（Y-m-d H:i:s）

    @OneToMany(mappedBy = "bCartProducts", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<BCartProductSets> bCartProductSets;
}
