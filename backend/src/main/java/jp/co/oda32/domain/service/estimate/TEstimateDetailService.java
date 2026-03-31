package jp.co.oda32.domain.service.estimate;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.estimate.TEstimateDetail;
import jp.co.oda32.domain.repository.estimate.TEstimateDetailRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.estimate.TEstimateDetailSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 見積明細Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2022/10/24
 */
@Service
public class TEstimateDetailService extends CustomService {
    private final TEstimateDetailRepository tEstimateDetailRepository;
    private final TEstimateDetailSpecification tEstimateDetailSpecification = new TEstimateDetailSpecification();

    @Autowired
    public TEstimateDetailService(TEstimateDetailRepository tEstimateDetailRepository) {
        this.tEstimateDetailRepository = tEstimateDetailRepository;
    }

    public List<TEstimateDetail> findByEstimateNo(int estimateNo) {
        return tEstimateDetailRepository.findByEstimateNo(estimateNo);
    }

    public List<TEstimateDetail> findForUpdateEstimateDetailStatus(String estimateDetailStatus, Flag delFlg) {
        return this.tEstimateDetailRepository.findAll(Specification
                .where(this.tEstimateDetailSpecification.estimateDetailStatusContains(estimateDetailStatus))
                .and(this.tEstimateDetailSpecification.delFlgContains(delFlg)));
    }

    public List<TEstimateDetail> findByEstimateNoList(List<Integer> estimateNoList) {
        return this.tEstimateDetailRepository.findAll(Specification
                .where(this.tEstimateDetailSpecification.estimateNoListContains(estimateNoList)));
    }

    public TEstimateDetail getByPK(int estimateNo, int estimateDetailNo) {
        return this.tEstimateDetailRepository.getByEstimateNoAndEstimateDetailNo(estimateNo, estimateDetailNo);
    }

    public List<TEstimateDetail> findByGoodsAndCompany(int goodsNo, int companyNo) {
        return this.tEstimateDetailRepository.findAll(
                Specification
                        .where(this.tEstimateDetailSpecification.goodsNoContains(goodsNo)
                                .and(this.tEstimateDetailSpecification.companyNoContains(companyNo)))
        );
    }

    /**
     * 商品情報を設定します。
     *
     * @return 更新した行数
     */
    public int updateGoodsInfo() {
        return this.tEstimateDetailRepository.updateGoodsInfo();
    }

    /**
     * 見積明細を更新します。
     *
     * @param updateEstimateDetail 更新する見積明細
     * @return 更新した見積明細Entity
     * @throws Exception システム例外
     */
    public TEstimateDetail update(TEstimateDetail updateEstimateDetail) throws Exception {
        TEstimateDetail tEstimateDetail = this.getByPK(updateEstimateDetail.getEstimateNo(), updateEstimateDetail.getEstimateDetailNo());
        if (tEstimateDetail == null) {
            // 存在しない
            throw new Exception(String.format("更新対象の見積番号が見つかりません。estimateNo:%d estimateDetailNo:%d", updateEstimateDetail.getEstimateNo(), updateEstimateDetail.getEstimateDetailNo()));
        }
        return this.update(this.tEstimateDetailRepository, updateEstimateDetail);
    }

    public List<TEstimateDetail> update(List<TEstimateDetail> estimateDetailList) throws Exception {
        List<TEstimateDetail> updatedEstimateDetailList = new ArrayList<>();
        for (TEstimateDetail estimateDetail : estimateDetailList) {
            updatedEstimateDetailList.add(this.update(estimateDetail));
        }
        return updatedEstimateDetailList;
    }

    /**
     * 見積明細を登録します。
     *
     * @param tEstimateDetail 見積明細Entity
     * @return 登録した見積明細Entity
     */
    public TEstimateDetail insert(TEstimateDetail tEstimateDetail) throws Exception {
        return this.insert(this.tEstimateDetailRepository, tEstimateDetail);
    }

    /**
     * 見積明細をまとめてinsertします。
     * delete insertする場合、同じEntityを使用して行うとエラーになるので、@Transactional(propagation = Propagation.REQUIRES_NEW)を付ける必要がある
     * <a href="https://stackoverflow.com/questions/26388848/why-am-i-getting-deleted-instance-passed-to-merge-when-merging-the-entity-first">...</a>
     *
     * @param tEstimateDetailList insertする見積明細リスト
     * @return insert結果
     * @throws Exception 例外
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<TEstimateDetail> insert(List<TEstimateDetail> tEstimateDetailList) throws Exception {
        List<TEstimateDetail> insertList = new ArrayList<>();
        for (TEstimateDetail tEstimateDetail : tEstimateDetailList) {
            insertList.add(this.insert(tEstimateDetail));
        }
        return insertList;
    }

    /**
     * 見積明細をまとめて物理削除します。
     *
     * @param estimateNo 削除する見積番号
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(int estimateNo) {
        this.tEstimateDetailRepository.deleteByEstimateNo(estimateNo);
    }

    /**
     * 引数の見積番号の見積明細のDEL_FLGを1にします
     *
     * @param estimateNo 見積番号
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteByDelFlg(int estimateNo) {
        this.tEstimateDetailRepository.deleteByDelFlg(estimateNo);
    }
}
