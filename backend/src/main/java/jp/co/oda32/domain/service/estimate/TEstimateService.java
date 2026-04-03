package jp.co.oda32.domain.service.estimate;

import jp.co.oda32.constant.EstimateStatus;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.estimate.TEstimate;
import jp.co.oda32.domain.repository.estimate.TEstimateRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.estimate.TEstimateSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 見積Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2022/10/24
 */
@Service
public class TEstimateService extends CustomService {
    private final TEstimateRepository tEstimateRepository;
    private final TEstimateSpecification tEstimateSpecification = new TEstimateSpecification();

    @Autowired
    public TEstimateService(TEstimateRepository salesGoodsRepository) {
        this.tEstimateRepository = salesGoodsRepository;
    }

    public TEstimate getByEstimateNo(int estimateNo) {
        return this.tEstimateRepository.findById(estimateNo).orElseThrow();
    }

    public List<TEstimate> find(Integer shopNo, Integer estimateNo, Integer partnerNo, String goodsName, String goodsCode, List<String> estimateStatusList, LocalDate estimateDateFrom, LocalDate estimateDateTo, LocalDate priceChangeDateFrom, LocalDate priceChangeDateTo, BigDecimal profitRate, Flag delFlg) {
        return find(shopNo, estimateNo, partnerNo, null, goodsName, goodsCode, estimateStatusList, estimateDateFrom, estimateDateTo, priceChangeDateFrom, priceChangeDateTo, profitRate, delFlg);
    }

    public List<TEstimate> find(Integer shopNo, Integer estimateNo, Integer partnerNo, String partnerName, String goodsName, String goodsCode, List<String> estimateStatusList, LocalDate estimateDateFrom, LocalDate estimateDateTo, LocalDate priceChangeDateFrom, LocalDate priceChangeDateTo, BigDecimal profitRate, Flag delFlg) {
        return this.tEstimateRepository.findAll(Specification
                .where(this.tEstimateSpecification.shopNoContains(shopNo))
                .and(this.tEstimateSpecification.estimateNoContains(estimateNo))
                .and(this.tEstimateSpecification.partnerNoContains(partnerNo))
                .and(this.tEstimateSpecification.partnerNameContains(partnerName))
                .and(this.tEstimateSpecification.goodsNamesContains(goodsName))
                .and(this.tEstimateSpecification.goodsCodeContains(goodsCode))
                .and(this.tEstimateSpecification.estimateStatusListContains(estimateStatusList))
                .and(this.tEstimateSpecification.estimateDateContains(estimateDateFrom, estimateDateTo))
                .and(this.tEstimateSpecification.priceChangeDateRangeContains(priceChangeDateFrom, priceChangeDateTo))
                .and(this.tEstimateSpecification.profitRateContains(profitRate))
                .and(this.tEstimateSpecification.delFlgContains(delFlg)));
    }

    public List<TEstimate> find(Integer shopNo, Integer partnerNo, LocalDate priceChangeDate, List<String> estimateStatusList) {
        return this.tEstimateRepository.findAll(Specification
                .where(this.tEstimateSpecification.shopNoContains(shopNo))
                .and(this.tEstimateSpecification.partnerNoContains(partnerNo))
                .and(this.tEstimateSpecification.priceChangeDateContains(priceChangeDate))
                .and(this.tEstimateSpecification.estimateStatusListContains(estimateStatusList))
                .and(this.tEstimateSpecification.delFlgContains(Flag.NO)));
    }

    /**
     * 反映する見積を検索します
     *
     * @param shopNo        ショップ番号
     * @param referenceDate 参照日(基本的に現在日付)
     * @return 反映する見積リスト
     */
    public List<TEstimate> findReflectEstimate(Integer shopNo, LocalDate referenceDate) {
        return this.tEstimateRepository.findAll(Specification
                .where(this.tEstimateSpecification.shopNoContains(shopNo))
                .and(this.tEstimateSpecification.priceChangeDatePastContains(referenceDate))
                .and(this.tEstimateSpecification.estimateStatusListContains(EstimateStatus.getNotifiedStatusCodeList()))
                .and(this.tEstimateSpecification.delFlgContains(Flag.NO)));
    }

    /**
     * 作成する見積に被りがあるかを検索する用
     *
     * @param shopNo             ショップ番号
     * @param partnerNo          得意先番号
     * @param estimateStatusList 見積ステータスリスト
     * @param priceChangeDate    価格変更日
     * @param delFlg             削除フラグ
     * @return 検索結果
     */
    public List<TEstimate> find(Integer shopNo, Integer partnerNo, List<String> estimateStatusList, LocalDate priceChangeDate, Flag delFlg) {
        return this.tEstimateRepository.findAll(Specification
                .where(this.tEstimateSpecification.shopNoContains(shopNo))
                .and(this.tEstimateSpecification.partnerNoContains(partnerNo))
                .and(this.tEstimateSpecification.estimateStatusListContains(estimateStatusList))
                .and(this.tEstimateSpecification.priceChangeDateContains(priceChangeDate))
                .and(this.tEstimateSpecification.delFlgContains(delFlg)));
    }

    /**
     * 親見積が作成された子見積を検索します。
     *
     * @return 親見積が作成された子見積
     */
    public List<TEstimate> findChildrenEstimate() {
        return this.tEstimateRepository.findChildrenEstimate();
    }

    /**
     * 見積を更新します。
     *
     * @param tEstimate 見積Entity
     * @return 更新した見積Entity
     * @throws Exception システム例外
     */
    public TEstimate update(TEstimate tEstimate) throws Exception {
        return this.update(this.tEstimateRepository, tEstimate);
    }

    /**
     * 見積を登録します。
     *
     * @param tEstimate 見積Entity
     * @return 登録した見積Entity
     */
    public TEstimate insert(TEstimate tEstimate) throws Exception {
        return this.insert(this.tEstimateRepository, tEstimate);
    }

    /**
     * 見積を削除します。（DelFlgを立てる）
     *
     * @param tEstimate 見積Entity
     * @return 登録した見積Entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TEstimate delete(TEstimate tEstimate) throws Exception {
        return this.delete(this.tEstimateRepository, tEstimate);
    }

}
