package jp.co.oda32.domain.repository.bcart;

import jp.co.oda32.domain.model.bcart.BCartOrder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * BCartOrderRepositoryCustomの実装クラス
 */
@Repository
public class BCartOrderRepositoryCustomImpl implements BCartOrderRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public BCartOrder saveWithNativeSql(BCartOrder bCartOrder) {
        if (bCartOrder.getId() == null) {
            // 新規登録の場合はデフォルトのJPAメソッドを使用
            // ただし、JSONB型のフィールドはnullにしてから保存
            bCartOrder.setCustomerCustoms(null);
            bCartOrder.setCustoms(null);
            bCartOrder.setOrderTotals(null);
            entityManager.persist(bCartOrder);
            return bCartOrder;
        }

        // カスタムSQLで更新
        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE b_cart_order SET ");
        sql.append("cod_cost = :cod_cost, ");
        sql.append("admin_message = :admin_message, ");
        sql.append("affiliate_id = :affiliate_id, ");
        sql.append("code = :code, ");
        sql.append("customer_address1 = :customer_address1, ");
        sql.append("customer_address2 = :customer_address2, ");
        sql.append("customer_address3 = :customer_address3, ");
        sql.append("customer_comp_name = :customer_comp_name, ");
        // JSOBカラムはNULLに設定
        sql.append("customer_customs = NULL, ");
        sql.append("customer_department = :customer_department, ");
        sql.append("customer_email = :customer_email, ");
        sql.append("customer_ext_id = :customer_ext_id, ");
        sql.append("customer_id = :customer_id, ");
        sql.append("customer_message = :customer_message, ");
        sql.append("customer_mobile_phone = :customer_mobile_phone, ");
        sql.append("customer_name = :customer_name, ");
        sql.append("customer_parent_id = :customer_parent_id, ");
        sql.append("customer_pref = :customer_pref, ");
        sql.append("customer_price_group_id = :customer_price_group_id, ");
        sql.append("customer_salesman_id = :customer_salesman_id, ");
        sql.append("customer_tel = :customer_tel, ");
        sql.append("customer_zip = :customer_zip, ");
        // JSOBカラムはNULLに設定
        sql.append("customs = NULL, ");
        sql.append("enquete1 = :enquete1, ");
        sql.append("enquete2 = :enquete2, ");
        sql.append("enquete3 = :enquete3, ");
        sql.append("enquete4 = :enquete4, ");
        sql.append("enquete5 = :enquete5, ");
        sql.append("estimate_id = :estimate_id, ");
        sql.append("final_price = :final_price, ");
        sql.append("get_point = :get_point, ");
        sql.append("memo = :memo, ");
        sql.append("order_totals = NULL, ");
        sql.append("ordered_at = :ordered_at, ");
        sql.append("payment = :payment, ");
        sql.append("payment_at = :payment_at, ");
        sql.append("shipping_cost = :shipping_cost, ");
        sql.append("status = :status, ");
        sql.append("tax = :tax, ");
        sql.append("tax_rate = :tax_rate, ");
        sql.append("total_price = :total_price, ");
        sql.append("use_point = :use_point ");
        sql.append("WHERE id = :id");

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("cod_cost", bCartOrder.getCODCost());
        query.setParameter("admin_message", bCartOrder.getAdminMessage());
        query.setParameter("affiliate_id", bCartOrder.getAffiliateId());
        query.setParameter("code", bCartOrder.getCode());
        query.setParameter("customer_address1", bCartOrder.getCustomerAddress1());
        query.setParameter("customer_address2", bCartOrder.getCustomerAddress2());
        query.setParameter("customer_address3", bCartOrder.getCustomerAddress3());
        query.setParameter("customer_comp_name", bCartOrder.getCustomerCompName());
        // customer_customs は NULL に設定するので省略
        query.setParameter("customer_department", bCartOrder.getCustomerDepartment());
        query.setParameter("customer_email", bCartOrder.getCustomerEmail());
        query.setParameter("customer_ext_id", bCartOrder.getCustomerExtId());
        query.setParameter("customer_id", bCartOrder.getCustomerId());
        query.setParameter("customer_message", bCartOrder.getCustomerMessage());
        query.setParameter("customer_mobile_phone", bCartOrder.getCustomerMobilePhone());
        query.setParameter("customer_name", bCartOrder.getCustomerName());
        query.setParameter("customer_parent_id", bCartOrder.getCustomerParentId());
        query.setParameter("customer_pref", bCartOrder.getCustomerPref());
        query.setParameter("customer_price_group_id", bCartOrder.getCustomerPriceGroupId());
        query.setParameter("customer_salesman_id", bCartOrder.getCustomerSalesmanId());
        query.setParameter("customer_tel", bCartOrder.getCustomerTel());
        query.setParameter("customer_zip", bCartOrder.getCustomerZip());
        // customs は NULL に設定するので省略
        query.setParameter("enquete1", bCartOrder.getEnquete1());
        query.setParameter("enquete2", bCartOrder.getEnquete2());
        query.setParameter("enquete3", bCartOrder.getEnquete3());
        query.setParameter("enquete4", bCartOrder.getEnquete4());
        query.setParameter("enquete5", bCartOrder.getEnquete5());
        query.setParameter("estimate_id", bCartOrder.getEstimateId());
        query.setParameter("final_price", bCartOrder.getFinalPrice());
        query.setParameter("get_point", bCartOrder.getGetPoint());
        query.setParameter("memo", bCartOrder.getMemo());
        // order_totals は NULL に設定するので省略
        query.setParameter("ordered_at", bCartOrder.getOrderedAt());
        query.setParameter("payment", bCartOrder.getPayment());
        query.setParameter("payment_at", bCartOrder.getPaymentAt());
        query.setParameter("shipping_cost", bCartOrder.getShippingCost());
        query.setParameter("status", bCartOrder.getStatus());
        query.setParameter("tax", bCartOrder.getTax());
        query.setParameter("tax_rate", bCartOrder.getTaxRate());
        query.setParameter("total_price", bCartOrder.getTotalPrice());
        query.setParameter("use_point", bCartOrder.getUsePoint());
        query.setParameter("id", bCartOrder.getId());

        query.executeUpdate();

        return bCartOrder;
    }
}
