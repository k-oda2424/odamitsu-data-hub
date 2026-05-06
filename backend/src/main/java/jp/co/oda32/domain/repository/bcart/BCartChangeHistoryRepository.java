package jp.co.oda32.domain.repository.bcart;

import jp.co.oda32.domain.model.bcart.BCartChangeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BCartChangeHistoryRepository extends JpaRepository<BCartChangeHistory, Long> {
    List<BCartChangeHistory> findByTargetTypeAndTargetIdOrderByChangedAtDesc(String targetType, Long targetId);

    List<BCartChangeHistory> findBybCartReflectedIsFalseAndTargetType(String targetType);

    /**
     * 特定商品セットの未反映 history を field_name でフィルタして全件取得。
     * 反映処理での aggregate 用。
     */
    @Query("SELECT h FROM BCartChangeHistory h "
            + "WHERE h.targetType = 'PRODUCT_SET' "
            + "AND h.targetId = :setId "
            + "AND h.bCartReflected = false "
            + "AND h.fieldName IN :fieldNames "
            + "ORDER BY h.changedAt ASC")
    List<BCartChangeHistory> findUnreflectedForProductSet(@Param("setId") Long setId,
                                                          @Param("fieldNames") List<String> fieldNames);

    /**
     * 指定された history ID リストを反映済みにマーク。
     * ループ実行中の競合を避けるため ID 限定で UPDATE する。
     */
    @Modifying
    @Query("UPDATE BCartChangeHistory h "
            + "SET h.bCartReflected = true, h.bCartReflectedAt = CURRENT_TIMESTAMP "
            + "WHERE h.id IN :ids")
    int markReflectedByIds(@Param("ids") List<Long> ids);

    /**
     * 未反映商品セットの product_set_id 一覧（ALL 反映用）。
     */
    @Query("SELECT DISTINCT h.targetId FROM BCartChangeHistory h "
            + "WHERE h.targetType = 'PRODUCT_SET' "
            + "AND h.bCartReflected = false "
            + "AND h.fieldName IN :fieldNames")
    List<Long> findUnreflectedProductSetIds(@Param("fieldNames") List<String> fieldNames);
}
