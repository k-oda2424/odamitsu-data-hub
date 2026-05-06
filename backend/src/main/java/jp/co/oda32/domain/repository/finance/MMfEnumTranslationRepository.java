package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.MMfEnumTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MMfEnumTranslationRepository extends JpaRepository<MMfEnumTranslation, Integer> {

    List<MMfEnumTranslation> findAllByDelFlgOrderByEnumKindAscEnglishCodeAsc(String delFlg);

    Optional<MMfEnumTranslation> findByEnumKindAndEnglishCodeAndDelFlg(
            String enumKind, String englishCode, String delFlg);
}
