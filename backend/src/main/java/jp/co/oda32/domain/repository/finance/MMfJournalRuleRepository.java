package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.MMfJournalRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MMfJournalRuleRepository extends JpaRepository<MMfJournalRule, Integer> {

    List<MMfJournalRule> findByDelFlgOrderByDescriptionCAscPriorityAsc(String delFlg);
}
