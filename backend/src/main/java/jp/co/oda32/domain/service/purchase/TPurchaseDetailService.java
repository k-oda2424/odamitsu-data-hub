package jp.co.oda32.domain.service.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.embeddable.TPurchaseDetailPK;
import jp.co.oda32.domain.model.purchase.TPurchaseDetail;
import jp.co.oda32.domain.repository.purchase.TPurchaseDetailRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.purchase.TPurchaseDetailSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 仕入明細Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2019/06/02
 */
@Service
public class TPurchaseDetailService extends CustomService {
    private final TPurchaseDetailRepository tPurchaseDetailRepository;
    private TPurchaseDetailSpecification tPurchaseDetailSpecification = new TPurchaseDetailSpecification();

    @Autowired
    public TPurchaseDetailService(TPurchaseDetailRepository tPurchaseDetailRepository) {
        this.tPurchaseDetailRepository = tPurchaseDetailRepository;
    }

    public List<TPurchaseDetail> findByPurchaseNo(Integer purchaseNo) {
        return tPurchaseDetailRepository.findByPurchaseNo(purchaseNo);
    }

    public List<TPurchaseDetail> findByStockProcessFlg(Flag stockProcessFlg) {
        return this.tPurchaseDetailRepository.findAll(Specification
                .where(this.tPurchaseDetailSpecification.stockProcessFlgContains(stockProcessFlg)));
    }

    public TPurchaseDetail getByPK(TPurchaseDetailPK pk) {
        return this.tPurchaseDetailRepository.getByPurchaseNoAndPurchaseDetailNo(pk.getPurchaseNo(), pk.getPurchaseDetailNo());
    }

    public List<TPurchaseDetail> findByNotInputSendOrder(LocalDate purchaseDateFrom) {
        return this.tPurchaseDetailRepository.findAll(Specification
                .where(this.tPurchaseDetailSpecification.sendOrderNoIsNull())
                .and(this.tPurchaseDetailSpecification.purchaseDateContains(purchaseDateFrom, null))
                .and(this.tPurchaseDetailSpecification.delFlgContains(Flag.NO)));
    }

    public List<TPurchaseDetail> find(Integer shopNo, Integer companyNo, Integer purchaseNo, Integer purchaseDetailNo
            , Integer warehouseNo, Integer goodsNo, String goodsCode, LocalDate purchaseDateFrom, LocalDate purchaseDateTo, Integer supplierNo
            , Flag stockProcessFlg, Flag delFlg) {
        return this.tPurchaseDetailRepository.findAll(Specification
                .where(this.tPurchaseDetailSpecification.shopNoContains(shopNo))
                .and(this.tPurchaseDetailSpecification.companyNoContains(companyNo))
                .and(this.tPurchaseDetailSpecification.purchaseNoContains(purchaseNo))
                .and(this.tPurchaseDetailSpecification.purchaseDetailNoContains(purchaseDetailNo))
                .and(this.tPurchaseDetailSpecification.warehouseNoContains(warehouseNo))
                .and(this.tPurchaseDetailSpecification.goodsNoContains(goodsNo))
                .and(this.tPurchaseDetailSpecification.goodsCodeContains(goodsCode))
                .and(this.tPurchaseDetailSpecification.purchaseDateContains(purchaseDateFrom, purchaseDateTo))
                .and(this.tPurchaseDetailSpecification.supplierNoContains(supplierNo))
                .and(this.tPurchaseDetailSpecification.stockProcessFlgContains(stockProcessFlg))
                .and(this.tPurchaseDetailSpecification.delFlgContains(delFlg)));
    }

    /**
     * 仕入伝票日付の範囲で検索します
     *
     * @param slipDateFrom 伝票日付from
     * @param slipDateTo   伝票日付to
     * @param delFlg       削除フラグ
     * @return 伝票日付の範囲の検索結果
     */
    public List<TPurchaseDetail> find(LocalDate slipDateFrom, LocalDate slipDateTo, Flag delFlg) {
        return this.tPurchaseDetailRepository.findAll(Specification
                .where(this.tPurchaseDetailSpecification.purchaseDateContains(slipDateFrom, slipDateTo))
                .and(this.tPurchaseDetailSpecification.delFlgContains(delFlg)));
    }

    public List<TPurchaseDetail> findLatestPurchasePrice() {
        return this.tPurchaseDetailRepository.findLatestPurchasePrice();
    }

    public int updateAllPurchasePriceReflect() {
        return this.tPurchaseDetailRepository.updateAllPurchasePriceReflect();
    }

    /**
     * 仕入明細を更新します。
     *
     * @param updatePurchaseDetail 更新する仕入明細
     * @return 更新した仕入明細Entity
     * @throws Exception システム例外
     */
    public TPurchaseDetail update(TPurchaseDetail updatePurchaseDetail) throws Exception {
        TPurchaseDetail tPurchaseDetail = this.getByPK(TPurchaseDetailPK.builder()
                .purchaseNo(updatePurchaseDetail.getPurchaseNo())
                .purchaseDetailNo(updatePurchaseDetail.getPurchaseDetailNo())
                .build());
        if (tPurchaseDetail == null) {
            // 存在しない
            throw new Exception(String.format("更新対象の仕入番号が見つかりません。purchaseNo:%d purchaseDetailNo:%d", updatePurchaseDetail.getPurchaseNo(), updatePurchaseDetail.getPurchaseDetailNo()));
        }
        return this.update(this.tPurchaseDetailRepository, updatePurchaseDetail);
    }

    public List<TPurchaseDetail> update(List<TPurchaseDetail> purchaseDetailList) throws Exception {
        List<TPurchaseDetail> updatedPurchaseDetailList = new ArrayList<>();
        for (TPurchaseDetail purchaseDetail : purchaseDetailList) {
            updatedPurchaseDetailList.add(this.update(purchaseDetail));
        }
        return updatedPurchaseDetailList;
    }

    /**
     * 仕入明細を登録します。
     *
     * @param tPurchaseDetail 仕入明細Entity
     * @return 登録した仕入明細Entity
     */
    public TPurchaseDetail insert(TPurchaseDetail tPurchaseDetail) throws Exception {
        return this.insert(this.tPurchaseDetailRepository, tPurchaseDetail);
    }

    public List<TPurchaseDetail> insert(List<TPurchaseDetail> tPurchaseDetailList) throws Exception {
        List<TPurchaseDetail> insertList = new ArrayList<>();
        for (TPurchaseDetail tPurchaseDetail : tPurchaseDetailList) {
            TPurchaseDetail detail = this.insert(tPurchaseDetail);
            insertList.add(detail);
        }
        return insertList;
    }

    public void deleteDetailList(int purchaseNo) {
        this.tPurchaseDetailRepository.deletePurchaseDetailByPurchaseNo(purchaseNo);
    }

    public List<TPurchaseDetail> findDeletTPurchaseDetailList() {
        return this.tPurchaseDetailRepository.findDeletTPurchaseDetailList();
    }

    /**
     * 仕入明細を物理的に削除します。
     *
     * @param deleteEntity 削除対象Entity
     * @throws Exception 例外発生時
     */
    public void deletePermanently(TPurchaseDetail deleteEntity) throws Exception {
        this.deletePermanently(this.tPurchaseDetailRepository, deleteEntity);
    }
}
