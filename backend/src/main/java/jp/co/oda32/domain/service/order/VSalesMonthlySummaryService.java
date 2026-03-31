package jp.co.oda32.domain.service.order;

import jp.co.oda32.domain.model.VSalesMonthlySummary;
import jp.co.oda32.domain.repository.VSalesMonthlySummaryRepository;
import jp.co.oda32.domain.specification.VSalesMonthlySummarySpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 月間売上マテリアライズドビューのサービスクラス
 *
 * @author k_oda
 * @since 2020/03/13
 */
@Service
@RequiredArgsConstructor
public class VSalesMonthlySummaryService {
    final VSalesMonthlySummaryRepository vSalesMonthlySummaryRepository;
    VSalesMonthlySummarySpecification vSalesMonthlySummarySpecification = new VSalesMonthlySummarySpecification();

    public List<VSalesMonthlySummary> findByShopNo(int shopNo) {
        return this.vSalesMonthlySummaryRepository.findByShopNo(shopNo);
    }

    public List<VSalesMonthlySummary> find(Integer shopNo, LocalDate month, LocalDate dateFrom, LocalDate dateTo) {
        return this.vSalesMonthlySummaryRepository.findAll(Specification
                .where(this.vSalesMonthlySummarySpecification.shopNoContains(shopNo))
                .and(this.vSalesMonthlySummarySpecification.monthContains(month))
                .and(this.vSalesMonthlySummarySpecification.monthRangeContains(dateFrom, dateTo))
        );
    }

    public void refresh() {
        this.vSalesMonthlySummaryRepository.refreshMaterializedView();
    }
}
