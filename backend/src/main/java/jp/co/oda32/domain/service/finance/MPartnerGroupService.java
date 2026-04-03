package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.MPartnerGroup;
import jp.co.oda32.domain.repository.finance.MPartnerGroupRepository;
import jp.co.oda32.dto.finance.PartnerGroupRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MPartnerGroupService {

    private final MPartnerGroupRepository repository;

    @Transactional(readOnly = true)
    public List<MPartnerGroup> findByShopNo(Integer shopNo) {
        return shopNo != null
                ? repository.findByShopNoOrderByGroupNameAsc(shopNo)
                : repository.findAll();
    }

    @Transactional(readOnly = true)
    public MPartnerGroup findById(Integer id) {
        return repository.findById(id).orElse(null);
    }

    @Transactional
    public MPartnerGroup create(PartnerGroupRequest request) {
        MPartnerGroup group = MPartnerGroup.builder()
                .groupName(request.getGroupName())
                .shopNo(request.getShopNo())
                .partnerCodes(request.getPartnerCodes())
                .build();
        return repository.save(group);
    }

    @Transactional
    public MPartnerGroup update(Integer id, PartnerGroupRequest request) {
        MPartnerGroup group = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("グループが見つかりません: id=" + id));
        group.setGroupName(request.getGroupName());
        group.getPartnerCodes().clear();
        group.getPartnerCodes().addAll(request.getPartnerCodes());
        return repository.save(group);
    }

    @Transactional
    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("グループが見つかりません: id=" + id);
        }
        repository.deleteById(id);
    }
}
