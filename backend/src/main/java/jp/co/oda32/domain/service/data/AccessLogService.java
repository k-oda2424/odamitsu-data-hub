package jp.co.oda32.domain.service.data;


import jp.co.oda32.domain.model.TAccessLog;
import jp.co.oda32.domain.repository.AccessLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * アクセスログEntity操作用サービスクラス
 *
 * @author k_oda
 * @since 2017/05/02
 */
@Service
public class AccessLogService {
    private final AccessLogRepository accessLogRepository;

    @Autowired
    public AccessLogService(AccessLogRepository accessLogRepository) {
        this.accessLogRepository = accessLogRepository;
    }

    /**
     * アクセスログを登録します。
     *
     * @param accessLog アクセスログEntity
     * @throws Exception システム例外
     */
    public void insert(TAccessLog accessLog) throws Exception {
        this.accessLogRepository.save(accessLog);
    }
}
