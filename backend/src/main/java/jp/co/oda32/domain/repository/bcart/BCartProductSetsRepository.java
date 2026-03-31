package jp.co.oda32.domain.repository.bcart;


import jp.co.oda32.domain.model.bcart.BCartProductSets;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author k_oda
 * @since 2023/04/21
 */
@Repository
public interface BCartProductSetsRepository extends JpaRepository<BCartProductSets, Long> {

//    @Modifying
//    @Query(value = "INSERT INTO b_cart_product_sets (id, customs, description, group_price, jan_code, jodai, jodai_type, location_no, max_order, min_order, name, option_ids, priority, product_id, product_no, quantity, set_flag, shipping_group_id, shipping_size, special_price, stock, stock_few, stock_flag, stock_parent, stock_view_id, tax_type_id, unit, unit_price, updated_at, view_group_filter, visible_customer_id, volume_discount) VALUES (:id, cast(:customs as jsonb), :description, cast(:groupPrice as jsonb), :janCode, :jodai, :jodaiType, :locationNo, :maxOrder, :minOrder, :name, :optionIds, :priority, :productId, :productNo, :quantity, :setFlag, :shippingGroupId, :shippingSize, cast(:specialPrice as jsonb), :stock, :stockFew, :stockFlag, cast(:stockParent as jsonb), :stockViewId, :taxTypeId, :unit, :unitPrice, :updatedAt, :viewGroupFilter, :visibleCustomerId, cast(:volumeDiscount as jsonb))", nativeQuery = true)
//    void insertData(Long id, String customs, String description, String groupPrice, String janCode, BigDecimal jodai, String jodaiType, String locationNo, BigDecimal maxOrder, BigDecimal minOrder, String name, String optionIds, int priority, Long productId, String productNo, int quantity, String setFlag, int shippingGroupId, BigDecimal shippingSize, String specialPrice, int stock, BigDecimal stockFew, Integer stockFlag, String stockParent, int stockViewId, int taxTypeId, String unit, BigDecimal unitPrice, Date updatedAt, String viewGroupFilter, String visibleCustomerId, String volumeDiscount);
//
//    @Modifying
//    @Query(value = "UPDATE b_cart_product_sets SET customs = cast(:customs as jsonb), description = :description, group_price = cast(:groupPrice as jsonb), jan_code = :janCode, jodai = :jodai, jodai_type = :jodaiType, location_no = :locationNo, max_order = :maxOrder, min_order = :minOrder, name = :name, option_ids = :optionIds, priority = :priority, product_id = :productId, product_no = :productNo, quantity = :quantity, set_flag = :setFlag, shipping_group_id = :shippingGroupId, shipping_size = :shippingSize, special_price = cast(:specialPrice as jsonb), stock = :stock, stock_few = :stockFew, stock_flag = :stockFlag, stock_parent = cast(:stockParent as jsonb), stock_view_id = :stockViewId, tax_type_id = :taxTypeId, unit = :unit, unit_price = :unitPrice, updated_at = :updatedAt, view_group_filter = :viewGroupFilter, visible_customer_id = :visibleCustomerId, volume_discount = cast(:volumeDiscount as jsonb) WHERE id = :id", nativeQuery = true)
//    void updateData(Long id, String customs, String description, String groupPrice, String janCode, BigDecimal jodai, String jodaiType, String locationNo, BigDecimal maxOrder, BigDecimal minOrder, String name, String optionIds, int priority, Long productId, String productNo, int quantity, String setFlag, int shippingGroupId, BigDecimal shippingSize, String specialPrice, int stock, BigDecimal stockFew, Integer stockFlag, String stockParent, int stockViewId, int taxTypeId, String unit, BigDecimal unitPrice, Date updatedAt, String viewGroupFilter, String visibleCustomerId, String volumeDiscount);

    BCartProductSets getById(@NotNull Long id);

    List<BCartProductSets> findBybCartPriceReflectedIsFalse();

    boolean existsById(@NotNull Long id);
}
