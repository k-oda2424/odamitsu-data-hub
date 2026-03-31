package jp.co.oda32.domain.repository.master;

import jp.co.oda32.domain.model.master.MShop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ショップマスタ(m_shop)のリポジトリインターフェース
 */
@Repository
public interface ShopRepository extends JpaRepository<MShop, Integer>, JpaSpecificationExecutor<MShop> {
    List<MShop> findAll();

    List<MShop> findByShopName(@Param("shopName") String shopName);
}
