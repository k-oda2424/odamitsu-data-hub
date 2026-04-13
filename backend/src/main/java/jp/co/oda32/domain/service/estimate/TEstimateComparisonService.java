package jp.co.oda32.domain.service.estimate;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.estimate.TComparisonDetail;
import jp.co.oda32.domain.model.estimate.TComparisonGroup;
import jp.co.oda32.domain.model.estimate.TEstimateComparison;
import jp.co.oda32.domain.repository.estimate.TComparisonDetailRepository;
import jp.co.oda32.domain.repository.estimate.TComparisonGroupRepository;
import jp.co.oda32.domain.repository.estimate.TEstimateComparisonRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.CommonSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class TEstimateComparisonService extends CustomService {

    private final TEstimateComparisonRepository comparisonRepository;
    private final TComparisonGroupRepository groupRepository;
    private final TComparisonDetailRepository detailRepository;

    @Autowired
    public TEstimateComparisonService(
            TEstimateComparisonRepository comparisonRepository,
            TComparisonGroupRepository groupRepository,
            TComparisonDetailRepository detailRepository) {
        this.comparisonRepository = comparisonRepository;
        this.groupRepository = groupRepository;
        this.detailRepository = detailRepository;
    }

    @Transactional(readOnly = true)
    public TEstimateComparison getByComparisonNo(int comparisonNo) {
        return comparisonRepository.findWithGroupsAndDetailsByComparisonNo(comparisonNo).orElse(null);
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<TEstimateComparison> find(
            Integer shopNo, Integer partnerNo,
            List<String> statusList,
            LocalDate dateFrom, LocalDate dateTo,
            String title) {
        CommonSpecification cs = new CommonSpecification();
        return comparisonRepository.findAll(
                Specification.where(cs.delFlgContains(Flag.NO))
                        .and(shopNo != null ?
                                (root, q, cb) -> cb.equal(root.get("shopNo"), shopNo) : null)
                        .and(partnerNo != null ?
                                (root, q, cb) -> cb.equal(root.get("partnerNo"), partnerNo) : null)
                        .and(statusList != null && !statusList.isEmpty() ?
                                (root, q, cb) -> root.get("comparisonStatus").in(statusList) : null)
                        .and(dateFrom != null ?
                                (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("comparisonDate"), dateFrom) : null)
                        .and(dateTo != null ?
                                (root, q, cb) -> cb.lessThanOrEqualTo(root.get("comparisonDate"), dateTo) : null)
                        .and(title != null && !title.isBlank() ?
                                (root, q, cb) -> cb.like(root.get("title"), "%" + escapeLike(title) + "%", '\\') : null)
        );
    }

    public TEstimateComparison insertHeader(TEstimateComparison entity) throws Exception {
        return insert(comparisonRepository, entity);
    }

    public TEstimateComparison updateHeader(TEstimateComparison entity) throws Exception {
        return update(comparisonRepository, entity);
    }

    public TEstimateComparison deleteHeader(TEstimateComparison entity) throws Exception {
        return delete(comparisonRepository, entity);
    }

    public TComparisonGroup insertGroup(TComparisonGroup entity) throws Exception {
        return insert(groupRepository, entity);
    }

    public TComparisonDetail insertDetail(TComparisonDetail entity) throws Exception {
        return insert(detailRepository, entity);
    }

    @Transactional
    public void softDeleteGroupsAndDetails(int comparisonNo) {
        detailRepository.softDeleteByComparisonNo(comparisonNo);
        groupRepository.softDeleteByComparisonNo(comparisonNo);
    }

    /**
     * 更新時: 既存グループ・明細を物理削除（softDelete だと PK 衝突するため）
     */
    @Transactional
    public void physicalDeleteGroupsAndDetails(int comparisonNo) {
        List<TComparisonDetail> details = detailRepository.findByComparisonNo(comparisonNo);
        detailRepository.deleteAll(details);
        List<TComparisonGroup> groups = groupRepository.findByComparisonNo(comparisonNo);
        groupRepository.deleteAll(groups);
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
