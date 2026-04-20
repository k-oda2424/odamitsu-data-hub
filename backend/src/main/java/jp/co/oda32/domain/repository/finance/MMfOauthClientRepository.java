package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MMfOauthClientRepository extends JpaRepository<MMfOauthClient, Integer> {

    /** 有効な (del_flg='0') クライアント設定を 1 件返す（通常 1 レコード運用）。 */
    Optional<MMfOauthClient> findFirstByDelFlgOrderByIdDesc(String delFlg);
}
