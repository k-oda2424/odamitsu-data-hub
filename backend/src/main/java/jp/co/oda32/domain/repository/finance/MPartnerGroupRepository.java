package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.MPartnerGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MPartnerGroupRepository extends JpaRepository<MPartnerGroup, Integer> {

    List<MPartnerGroup> findByShopNoOrderByGroupNameAsc(Integer shopNo);
}
