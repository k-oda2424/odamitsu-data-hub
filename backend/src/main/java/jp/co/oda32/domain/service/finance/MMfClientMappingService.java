package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.MMfClientMapping;
import jp.co.oda32.domain.repository.finance.MMfClientMappingRepository;
import jp.co.oda32.domain.service.util.LoginUserUtil;
import jp.co.oda32.dto.finance.cashbook.MfClientMappingRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MMfClientMappingService {

    private final MMfClientMappingRepository repository;

    private Integer currentUserNo() {
        try {
            return LoginUserUtil.getLoginUserInfo().getUser().getLoginUserNo();
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<MMfClientMapping> findAll() {
        return repository.findByDelFlgOrderByAliasAsc("0");
    }

    @Transactional
    public MMfClientMapping create(MfClientMappingRequest req) {
        repository.findByAliasAndDelFlg(req.getAlias(), "0")
                .ifPresent(e -> { throw new IllegalArgumentException("aliasが既に登録されています: " + req.getAlias()); });
        Integer userNo = currentUserNo();
        LocalDateTime now = LocalDateTime.now();
        MMfClientMapping e = MMfClientMapping.builder()
                .alias(req.getAlias())
                .mfClientName(req.getMfClientName())
                .delFlg("0")
                .addDateTime(now)
                .addUserNo(userNo)
                .modifyDateTime(now)
                .modifyUserNo(userNo)
                .build();
        return repository.save(e);
    }

    @Transactional
    public MMfClientMapping update(Integer id, MfClientMappingRequest req) {
        MMfClientMapping e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("マッピングが見つかりません: id=" + id));
        if (!e.getAlias().equals(req.getAlias())) {
            repository.findByAliasAndDelFlg(req.getAlias(), "0")
                    .ifPresent(dup -> { throw new IllegalArgumentException("aliasが既に登録されています: " + req.getAlias()); });
        }
        e.setAlias(req.getAlias());
        e.setMfClientName(req.getMfClientName());
        e.setModifyDateTime(LocalDateTime.now());
        e.setModifyUserNo(currentUserNo());
        return repository.save(e);
    }

    @Transactional
    public void delete(Integer id) {
        MMfClientMapping e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("マッピングが見つかりません: id=" + id));
        e.setDelFlg("1");
        e.setModifyDateTime(LocalDateTime.now());
        e.setModifyUserNo(currentUserNo());
        repository.save(e);
    }
}
