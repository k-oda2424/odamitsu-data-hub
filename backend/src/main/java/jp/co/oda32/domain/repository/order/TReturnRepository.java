package jp.co.oda32.domain.repository.order;

import jp.co.oda32.domain.model.order.TReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 返品(t_return)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2018/11/29
 */
public interface TReturnRepository extends JpaRepository<TReturn, Integer>, JpaSpecificationExecutor<TReturn> {
    List<TReturn> findAll();

    TReturn getByShopNoAndReturnSlipNo(@Param("shopNo") Integer shopNo, @Param("returnSlipNo") String returnSlipNo);
}
