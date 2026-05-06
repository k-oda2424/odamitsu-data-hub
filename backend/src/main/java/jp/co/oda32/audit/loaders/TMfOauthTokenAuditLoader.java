package jp.co.oda32.audit.loaders;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.oda32.audit.AuditEntityLoader;
import jp.co.oda32.domain.repository.finance.MMfOauthClientRepository;
import jp.co.oda32.domain.repository.finance.TMfOauthTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * T2 (C5): {@code t_mf_oauth_token} の before/after snapshot loader。
 * <p>
 * PK は {@code userNo} (handleCallback / revoke の操作実行者) で識別するが、
 * 実 entity は active client (del_flg='0') の active token (del_flg='0') を
 * 同 client_id 配下から取得する (1 client につき active token は 1 件のみ運用)。
 * <p>
 * 復号した access/refresh token は {@code @AuditExclude} により JSON 出力から除外される。
 *
 * @since 2026-05-04 (C5)
 */
@Component
@RequiredArgsConstructor
public class TMfOauthTokenAuditLoader implements AuditEntityLoader {

    private final MMfOauthClientRepository clientRepository;
    private final TMfOauthTokenRepository tokenRepository;

    @Override
    public String table() {
        return "t_mf_oauth_token";
    }

    @Override
    public Optional<Object> loadByPk(JsonNode pkJson) {
        try {
            return clientRepository.findFirstByDelFlgOrderByIdDesc("0")
                    .flatMap(client -> tokenRepository.findFirstByClientIdAndDelFlgOrderByIdDesc(client.getId(), "0"))
                    .map(o -> (Object) o);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
