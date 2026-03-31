package jp.co.oda32.domain.repository.master;

/**
 * 配送担当者マスタのリポジトリクラス
 *
 * @author k_oda
 * @since 2024/05/04
 */

import jp.co.oda32.domain.model.embeddable.MDeliveryPersonPK;
import jp.co.oda32.domain.model.master.MDeliveryPerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MDeliveryPersonRepository extends JpaRepository<MDeliveryPerson, MDeliveryPersonPK> {

}
