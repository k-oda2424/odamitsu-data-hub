package jp.co.oda32.domain.repository.master;

import jp.co.oda32.domain.model.master.MMaker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * メーカーマスタ(m_maker)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2018/04/11
 */
public interface MakerRepository extends JpaRepository<MMaker, Integer>, JpaSpecificationExecutor<MMaker> {
    List<MMaker> findAll();

    List<MMaker> findByMakerName(@Param("makerName") String makerName);

}
