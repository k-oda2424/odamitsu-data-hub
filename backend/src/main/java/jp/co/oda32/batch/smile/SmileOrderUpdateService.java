package jp.co.oda32.batch.smile;

import jp.co.oda32.constant.CompanyType;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.model.order.TDelivery;
import jp.co.oda32.domain.model.order.TDeliveryDetail;
import jp.co.oda32.domain.model.order.TOrder;
import jp.co.oda32.domain.model.order.TOrderDetail;
import jp.co.oda32.domain.model.smile.WSmileOrderOutputFile;
import jp.co.oda32.util.BigDecimalUtil;
import jp.co.oda32.util.DeliveryUtil;
import jp.co.oda32.util.PartnerRegister;
import jp.co.oda32.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SMILEからの受注データを更新処理するクラス
 * トランザクションをかけるメソッドはこのクラスに記載する
 *
 * @author k_oda
 * @since 2024/05/20
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class SmileOrderUpdateService extends AbstractSmileOrderImportService {
    @Autowired
    DeliveryUtil deliveryUtil;
    @Autowired
    PartnerRegister partnerRegister;

    public List<WSmileOrderOutputFile> findModifiedOrders(int pageSize, int pageNumber) {
        return this.wSmileOrderOutputFileService.findModifiedOrders(pageSize, pageNumber);
    }

    public void preProcess(List<WSmileOrderOutputFile> modifiedOrderList) {
        // shop_no,tokuisaki_codeのListのMapを作成
        Map<Integer, List<String>> partnerMap = modifiedOrderList.stream()
                .collect(Collectors.groupingBy(
                        WSmileOrderOutputFile::getShopNo,
                        Collectors.mapping(WSmileOrderOutputFile::getTokuisakiCode, Collectors.toList())
                ));

        // partnerMapをforでまわす
        for (Map.Entry<Integer, List<String>> entry : partnerMap.entrySet()) {
            List<MPartner> mPartnerList = this.mPartnerService.findByPartnerCodeList(entry.getKey(), entry.getValue());
            // mPartnerMapに値をつめる
            mPartnerList.forEach(mPartner -> {
                PartnerKey partnerKey = new PartnerKey(mPartner.getShopNo(), mPartner.getPartnerCode());
                mPartnerMap.put(partnerKey, mPartner);
            });
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateOrder(int shopNo, Long shorirenban, List<WSmileOrderOutputFile> modifiedOrderFileList) throws Exception {
        // 注文・注文明細
        TOrder existOrder = this.tOrderService.getByUniqKey(shopNo, shorirenban);
        if (existOrder == null) {
            log.error(String.format("注文更新処理でt_orderが見つかりません。shop_no:%d 処理連番:%d", shopNo, shorirenban));
            return;
        }
        List<TOrderDetail> tOrderDetailList = existOrder.getOrderDetailList();
        List<TOrderDetail> updatedTOrderDetailList = new ArrayList<>();
        boolean isDeliveryDateChecked = false;
        boolean isOrderChecked = false;
        List<TDeliveryDetail> existTDeliveryList = null;

        for (WSmileOrderOutputFile modifiedOrderFile : modifiedOrderFileList) {
            TOrderDetail existTOrderDetail = tOrderDetailList.stream()
                    .filter(orderDetail -> Objects.equals(orderDetail.getShopNo(), modifiedOrderFile.getShopNo()))
                    .filter(orderDetail -> Objects.equals(orderDetail.getProcessingSerialNumber(), modifiedOrderFile.getShoriRenban()))
                    .filter(orderDetail -> Objects.equals(orderDetail.getOrderDetailNo(), modifiedOrderFile.getGyou()))
                    .findFirst().orElse(null);

            if (existTOrderDetail == null) {
                log.error(String.format("出荷明細登録時に注文明細が見つかりません。処理連番:%d 明細番号:%d", modifiedOrderFile.getShoriRenban(), modifiedOrderFile.getGyou()));
                continue;
            }

            if (existTDeliveryList == null) {
                existTDeliveryList = this.tDeliveryDetailService.findByDeliveryNo(existTOrderDetail.getDeliveryNo());
            }

            TDeliveryDetail existTDeliveryDetail = existTDeliveryList.stream()
                    .filter(tDeliveryDetail -> tDeliveryDetail.getOrderDetailNo().equals(existTOrderDetail.getOrderDetailNo()))
                    .findFirst().orElse(null);

            if (existTDeliveryDetail == null) {
                log.error(String.format("出荷情報が取得できません。注文番号:%d 注文明細番号:%d", existTOrderDetail.getOrderNo(), existTOrderDetail.getOrderNo()));
                throw new Exception("出荷情報が取得できません。");
            }

            if (!Objects.equals(existTOrderDetail.getShopNo(), modifiedOrderFile.getShopNo())) {
                log.info(String.format("ショップ番号が変更されました。shop_no:%d 処理連番:%s 行:%d 変更後shop_no：%d", existTOrderDetail.getShopNo(), existOrder.getProcessingSerialNumber(), modifiedOrderFile.getGyou(), modifiedOrderFile.getShopNo()));
                existTOrderDetail.setShopNo(modifiedOrderFile.getShopNo());
            }
            if (!Objects.equals(existTOrderDetail.getOrderNum(), modifiedOrderFile.getSuuryou())) {
                log.info(String.format("注文数量が変更されました。shop_no:%d 処理連番:%s 行:%d 商品コード:%s 旧数量:%s 変更後数量：%s", existTOrderDetail.getShopNo(), existOrder.getProcessingSerialNumber(), modifiedOrderFile.getGyou(), existTOrderDetail.getGoodsCode(), existTOrderDetail.getOrderNum(), modifiedOrderFile.getSuuryou()));
                existTOrderDetail.setOrderNum(modifiedOrderFile.getSuuryou());
                existTDeliveryDetail.setDeliveryNum(modifiedOrderFile.getSuuryou());
            }

            if (!StringUtil.isEqual(existTOrderDetail.getGoodsCode(), modifiedOrderFile.getShouhinCode())) {
                log.info(String.format("商品コードが変更されました。shop_no:%d 処理連番:%s 行:%d 旧商品コード:%s 変更後商品コード：%s", existTOrderDetail.getShopNo(), existOrder.getProcessingSerialNumber(), modifiedOrderFile.getGyou(), existTOrderDetail.getGoodsCode(), modifiedOrderFile.getShouhinCode()));
                existTOrderDetail.setGoodsCode(modifiedOrderFile.getShouhinCode());
                existTDeliveryDetail.setGoodsCode(modifiedOrderFile.getShouhinCode());
            }
            if (!StringUtil.isEqual(existTOrderDetail.getGoodsName(), modifiedOrderFile.getShouhinMei())) {
                log.info(String.format("商品名が変更されました。shop_no:%d 処理連番:%s 行:%d 旧商品名:%s 変更後商品名：%s", existTOrderDetail.getShopNo(), existOrder.getProcessingSerialNumber(), modifiedOrderFile.getGyou(), existTOrderDetail.getGoodsName(), modifiedOrderFile.getShouhinMei()));
                existTOrderDetail.setGoodsName(modifiedOrderFile.getShouhinMei());
            }
            if (!BigDecimalUtil.isEqual(existTOrderDetail.getGoodsPrice(), modifiedOrderFile.getTanka())) {
                log.info(String.format("商品単価が変更されました。shop_no:%d 処理連番:%s 行:%d 旧単価:%s 変更後単価：%s", existTOrderDetail.getShopNo(), existOrder.getProcessingSerialNumber(), modifiedOrderFile.getGyou(), existTOrderDetail.getGoodsPrice(), modifiedOrderFile.getTanka()));
                existTOrderDetail.setGoodsPrice(modifiedOrderFile.getTanka());
            }
            if (!BigDecimalUtil.isEqual(existTOrderDetail.getTaxRate(), modifiedOrderFile.getShouhizeiritsu())) {
                log.info(String.format("消費税率が変更されました。shop_no:%d 処理連番:%s 行:%d 旧消費税率:%s 変更後消費税率：%s", existTOrderDetail.getShopNo(), existOrder.getProcessingSerialNumber(), modifiedOrderFile.getGyou(), existTOrderDetail.getTaxRate(), modifiedOrderFile.getShouhizeiritsu()));
                existTOrderDetail.setTaxRate(modifiedOrderFile.getShouhizeiritsu());
            }
            if (!StringUtil.isEqual(existTOrderDetail.getTaxType(), modifiedOrderFile.getKazeiKubun())) {
                log.info(String.format("課税区分が変更されました。shop_no:%d 処理連番:%s 行:%d 旧課税区分:%s 変更後課税区分：%s", existTOrderDetail.getShopNo(), existOrder.getProcessingSerialNumber(), modifiedOrderFile.getGyou(), existTOrderDetail.getTaxType(), modifiedOrderFile.getKazeiKubun()));
                existTOrderDetail.setTaxType(modifiedOrderFile.getKazeiKubun());
            }
            if (!isDeliveryDateChecked) {
                TDelivery tDelivery = this.tDeliveryService.getByPK(existTOrderDetail.getDeliveryNo());
                if (!tDelivery.getSlipDate().equals(modifiedOrderFile.getDenpyouHizuke())) {
                    log.info(String.format("納品日が変更されました。shop_no:%d 処理連番:%s 旧納品日:%s 変更後納品日：%s", existTOrderDetail.getShopNo(), existOrder.getProcessingSerialNumber(), tDelivery.getSlipDate(), modifiedOrderFile.getDenpyouHizuke()));
                    tDelivery.setSlipDate(modifiedOrderFile.getDenpyouHizuke());
                    tDeliveryService.update(tDelivery);
                }
                isDeliveryDateChecked = true;
            }
            if (!isOrderChecked) {
                if (!StringUtil.isEqual(existOrder.getPartnerCode(), modifiedOrderFile.getTokuisakiCode()) && Objects.equals(existOrder.getShopNo(), modifiedOrderFile.getShopNo())) {
                    log.info(String.format("得意先コード変更　shop_no:%d 処理連番:%s 旧得意先コード:%s 変更後得意先コード:%s", existTOrderDetail.getShopNo(), existOrder.getProcessingSerialNumber(), existOrder.getPartnerCode(), modifiedOrderFile.getTokuisakiCode()));

                    // 新しい得意先情報を取得
                    PartnerKey partnerKey = new PartnerKey(modifiedOrderFile.getShopNo(), modifiedOrderFile.getTokuisakiCode());
                    MPartner newPartner = this.mPartnerMap.get(partnerKey);
                    int partnerNo = newPartner.getPartnerNo();
                    Integer companyNo = newPartner.getCompanyNo();
                    String companyName = newPartner.getMCompany().getCompanyName();

                    // t_orderの更新
                    existOrder.setPartnerCode(modifiedOrderFile.getTokuisakiCode());
                    existOrder.setPartnerNo(partnerNo);
                    existOrder.setCompanyNo(companyNo);
                    existOrder.setCompanyName(companyName);
                    existOrder.setShopNo(modifiedOrderFile.getShopNo());

                    // t_deliveryの更新
                    TDelivery tDelivery = this.tDeliveryService.getByPK(existTOrderDetail.getDeliveryNo());
                    if (!StringUtil.isEqual(tDelivery.getPartnerCode(), modifiedOrderFile.getTokuisakiCode())) {
                        log.info(String.format("出荷情報の得意先コードを更新します。delivery_no:%d 旧得意先コード:%s 変更後得意先コード:%s", tDelivery.getDeliveryNo(), tDelivery.getPartnerCode(), modifiedOrderFile.getTokuisakiCode()));
                        tDelivery.setPartnerCode(modifiedOrderFile.getTokuisakiCode());
                    }
                    if (!Objects.equals(tDelivery.getCompanyNo(), companyNo)) {
                        log.info(String.format("出荷情報の会社番号を更新します。delivery_no:%d 旧会社番号:%d 変更後会社番号:%d", tDelivery.getDeliveryNo(), tDelivery.getCompanyNo(), companyNo));
                        tDelivery.setCompanyNo(companyNo);
                    }
                    this.tDeliveryService.update(tDelivery);

                    // t_order_detailの全明細のcompany_noを更新
                    for (TOrderDetail detail : tOrderDetailList) {
                        if (!Objects.equals(detail.getCompanyNo(), companyNo)) {
                            log.info(String.format("注文明細の会社番号を更新します。order_no:%d order_detail_no:%d 旧会社番号:%d 変更後会社番号:%d", detail.getOrderNo(), detail.getOrderDetailNo(), detail.getCompanyNo(), companyNo));
                            detail.setCompanyNo(companyNo);
                            this.tOrderDetailService.update(detail);
                        }
                    }

                    // t_delivery_detailの全明細のcompany_noを更新
                    for (TDeliveryDetail deliveryDetail : existTDeliveryList) {
                        if (!Objects.equals(deliveryDetail.getCompanyNo(), companyNo)) {
                            log.info(String.format("出荷明細の会社番号を更新します。delivery_no:%d delivery_detail_no:%d 旧会社番号:%d 変更後会社番号:%d", deliveryDetail.getDeliveryNo(), deliveryDetail.getDeliveryDetailNo(), deliveryDetail.getCompanyNo(), companyNo));
                            deliveryDetail.setCompanyNo(companyNo);
                            this.tDeliveryDetailService.update(deliveryDetail);
                        }
                    }
                }
                isOrderChecked = true;
            }
            this.tOrderDetailService.update(existTOrderDetail);
            this.tDeliveryDetailService.update(existTDeliveryDetail);
            updatedTOrderDetailList.add(existTOrderDetail);
        }
        existOrder.setOrderDetailList(updatedTOrderDetailList);
        // t_orderの合計金額の更新
        calculateTotalAmount(existOrder);
        this.tOrderService.update(existOrder);
    }

    protected void updatePartner(List<WSmileOrderOutputFile> modifiedOrderList) throws Exception {
        List<String> doneCodeList = new ArrayList<>();
        List<WSmileOrderOutputFile> modifiableList = new ArrayList<>(modifiedOrderList);
        modifiableList.sort(Comparator.comparing(WSmileOrderOutputFile::getDenpyouHizuke).reversed());

        for (WSmileOrderOutputFile modifiedOrder : modifiableList) {
            String partnerCode = modifiedOrder.getTokuisakiCode();
            if (doneCodeList.contains(partnerCode)) {
                continue;
            }

            PartnerKey partnerKey = new PartnerKey(modifiedOrder.getShopNo(), modifiedOrder.getTokuisakiCode());
            MPartner existsPartner = mPartnerMap.get(partnerKey);

            if (existsPartner == null) {
                // 得意先の登録
                String partnerName = String.format("%s %s", modifiedOrder.getTokuisakiMei1(), modifiedOrder.getTokuisakiMei2());
                MPartner newPartner = MPartner.builder()
                        .partnerCode(modifiedOrder.getTokuisakiCode())
                        .partnerName(partnerName)
                        .abbreviatedPartnerName(modifiedOrder.getTokuisakiRyakushou())
                        .shopNo(modifiedOrder.getShopNo())
                        .build();
                MCompany newCompany = MCompany.builder()
                        .companyName(partnerName)
                        .abbreviatedCompanyName(modifiedOrder.getTokuisakiRyakushou())
                        .shopNo(modifiedOrder.getShopNo())
                        .companyType(CompanyType.PARTNER.getValue())
                        .build();
                newPartner = this.partnerRegister.register(newPartner, newCompany);
                PartnerKey newPartnerKey = new PartnerKey(modifiedOrder.getShopNo(), newPartner.getPartnerCode());
                this.mPartnerMap.put(newPartnerKey, newPartner);
                existsPartner = newPartner;
            }

            boolean isUpdate = false;
            MCompany mCompany = existsPartner.getMCompany();
            String partnerName = String.format("%s %s", modifiedOrder.getTokuisakiMei1(), modifiedOrder.getTokuisakiMei2());

            if (!StringUtil.isEqual(existsPartner.getPartnerName(), partnerName)) {
                log.info(String.format("得意先名が更新されます。old:%s,new:%s", existsPartner.getPartnerName(), partnerName));
                existsPartner.setPartnerName(partnerName);
                mCompany.setCompanyName(partnerName);
                isUpdate = true;
            }

            if (!StringUtil.isEqual(existsPartner.getAbbreviatedPartnerName(), modifiedOrder.getTokuisakiRyakushou())) {
                log.info(String.format("得意先名略称が更新されます。old:%s,new:%s", existsPartner.getAbbreviatedPartnerName(), modifiedOrder.getTokuisakiRyakushou()));
                existsPartner.setAbbreviatedPartnerName(modifiedOrder.getTokuisakiRyakushou());
                mCompany.setAbbreviatedCompanyName(modifiedOrder.getTokuisakiRyakushou());
                isUpdate = true;
            }

            if (existsPartner.getLastOrderDate() == null || existsPartner.getLastOrderDate().isBefore(modifiedOrder.getDenpyouHizuke())) {
                log.info(String.format("得意先最終注文日が更新されます。old:%s,new:%s", existsPartner.getLastOrderDate(), modifiedOrder.getDenpyouHizuke()));
                existsPartner.setLastOrderDate(modifiedOrder.getDenpyouHizuke());
                isUpdate = true;
            }

            if (isUpdate) {
                this.mPartnerService.update(existsPartner);
                this.mCompanyService.update(mCompany);
            }

            doneCodeList.add(partnerCode);
        }
    }
}
