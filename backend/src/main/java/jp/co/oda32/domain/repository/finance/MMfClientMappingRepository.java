package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.MMfClientMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MMfClientMappingRepository extends JpaRepository<MMfClientMapping, Integer> {

    List<MMfClientMapping> findByDelFlgOrderByAliasAsc(String delFlg);

    Optional<MMfClientMapping> findByAliasAndDelFlg(String alias, String delFlg);
}
