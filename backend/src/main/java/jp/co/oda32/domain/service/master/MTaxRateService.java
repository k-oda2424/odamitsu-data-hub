package jp.co.oda32.domain.service.master;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.master.MTaxRate;
import jp.co.oda32.domain.repository.master.MTaxRateRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.master.MTaxRateSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 消費税率マスタEntity操作用サービスクラス
 *
 * @author k_oda
 * @since 2020/01/21
 */
@Service
@EnableAutoConfiguration
@RequiredArgsConstructor
public class MTaxRateService extends CustomService {
    private final MTaxRateRepository mTaxRateRepository;
    private final MTaxRateSpecification mTaxRateSpecification = new MTaxRateSpecification();

    /**
     * 現在の消費税率を取得します。
     *
     * @return 現在の消費税率
     */
    public MTaxRate getTaxRate() throws Exception {
        List<MTaxRate> mTaxRateList = this.mTaxRateRepository.findAll(Specification
                .where(this.mTaxRateSpecification.periodFromContains())
                .and(this.mTaxRateSpecification.periodToContains())
                .and(this.mTaxRateSpecification.delFlgContains(Flag.NO)));
        if (mTaxRateList.isEmpty()) {
            throw new Exception("消費税率マスタが取得できませんでした。");
        }
        return mTaxRateList.stream().max(Comparator.comparing(MTaxRate::getTaxRateNo)).get();
    }
}
