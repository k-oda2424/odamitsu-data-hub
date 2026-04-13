package jp.co.oda32.domain.model.bcart;

import com.google.gson.annotations.SerializedName;
import lombok.*;

import jakarta.persistence.*;
import java.sql.Timestamp;

/**
 * B-CARTカテゴリマスタ
 * <p>
 * B-CART APIから同期されるカテゴリ情報。2階層構造（親→子）。
 * B-CARTミラーテーブルのため、IEntity/監査カラムは使用しない（既存b_cart_productsと同一方針）。
 * <p>
 * b_cart_reflected 状態遷移:
 * - API同期で取込 → TRUE（B-CARTと同期済み）
 * - 本システムで編集 → FALSE（未反映）
 * - 反映バッチでPATCH成功 → TRUE
 */
@Getter
@Setter
@RequiredArgsConstructor
@Builder
@Entity
@AllArgsConstructor
@Table(name = "b_cart_categories")
public class BCartCategories {
    @Id
    @Column(name = "id", nullable = false)
    @SerializedName("id")
    private Integer id;

    @Column(name = "name", length = 255, nullable = false)
    @SerializedName("name")
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    @SerializedName("description")
    private String description;

    @Column(name = "rv_description", columnDefinition = "TEXT")
    @SerializedName("rv_description")
    private String rvDescription;

    @Column(name = "parent_category_id")
    @SerializedName("parent_category_id")
    private Integer parentCategoryId;

    @Column(name = "header_image", length = 255)
    @SerializedName("header_image")
    private String headerImage;

    @Column(name = "banner_image", length = 255)
    @SerializedName("banner_image")
    private String bannerImage;

    @Column(name = "menu_image", length = 255)
    @SerializedName("menu_image")
    private String menuImage;

    @Column(name = "meta_title", length = 255)
    @SerializedName("meta_title")
    private String metaTitle;

    @Column(name = "meta_keywords", length = 500)
    @SerializedName("meta_keywords")
    private String metaKeywords;

    @Column(name = "meta_description", length = 500)
    @SerializedName("meta_description")
    private String metaDescription;

    @Column(name = "priority", nullable = false)
    @SerializedName("priority")
    private Integer priority;

    @Column(name = "flag", nullable = false)
    @SerializedName("flag")
    private Integer flag;

    @Builder.Default
    @Column(name = "b_cart_reflected", nullable = false)
    private boolean bCartReflected = true;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt; // B-CART APIにはupdated_atなし。ローカル更新時に@PreUpdateで設定

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = new Timestamp(System.currentTimeMillis());
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Timestamp(System.currentTimeMillis());
    }
}
