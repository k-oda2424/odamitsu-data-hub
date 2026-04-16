package jp.co.oda32.batch.smile;

import jp.co.oda32.batch.goods.logic.SalesGoodsCreateService;
import jp.co.oda32.constant.*;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.model.order.*;
import jp.co.oda32.domain.model.smile.WSmileOrderOutputFile;
import jp.co.oda32.util.BigDecimalUtil;
import jp.co.oda32.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SMILEからの新規受注データを処理するクラス
 * トランザクションをかけるメソッドはこのクラスに記載する
 *
 * @author k_oda
 * @since 2024/05/16
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class SmileOrderImportService extends AbstractSmileOrderImportService {

    @Autowired
    SalesGoodsCreateService salesGoodsCreateService;

    public Page<WSmileOrderOutputFile> findNewOrders(Pageable firstPageable) {
        return wSmileOrderOutputFileService.findNewOrders(firstPageable);
    }

    public void preProcess(List<WSmileOrderOutputFile> newOrderList) {
        // ページごとに preProcess が呼ばれるため、前回の map を必ずクリアする（メモリ肥大・古いキャッシュヒット防止）
        wSalesGoodsMap.clear();
        mPartnerMap.clear();

        // shop_no,shouhin_codeのListのMapを作成
        Map<Integer, List<String>> goodsMap = newOrderList.stream()
                .collect(Collectors.groupingBy(
                        WSmileOrderOutputFile::getShopNo,
                        Collectors.mapping(WSmileOrderOutputFile::getShouhinCode, Collectors.toList())
                ));

        for (Map.Entry<Integer, List<String>> entry : goodsMap.entrySet()) {
            List<WSalesGoods> wSalesGoodsList = this.wSalesGoodsService.findByShopNoAndGoodsCode(entry.getKey(), entry.getValue());
            wSalesGoodsList.forEach(wSalesGoods -> {
                SalesGoodsKey salesGoodsKey = new SalesGoodsKey(wSalesGoods.getShopNo(), wSalesGoods.getGoodsCode());
                wSalesGoodsMap.put(salesGoodsKey, wSalesGoods);
            });
        }

        // shop_no,tokuisaki_codeのListのMapを作成
        Map<Integer, List<String>> partnerMap = newOrderList.stream()
                .collect(Collectors.groupingBy(
                        WSmileOrderOutputFile::getShopNo,
                        Collectors.mapping(WSmileOrderOutputFile::getTokuisakiCode, Collectors.toList())
                ));

        for (Map.Entry<Integer, List<String>> entry : partnerMap.entrySet()) {
            List<MPartner> mPartnerList = this.mPartnerService.findByPartnerCodeList(entry.getKey(), entry.getValue());
            mPartnerList.forEach(mPartner -> {
                PartnerKey partnerKey = new PartnerKey(mPartner.getShopNo(), mPartner.getPartnerCode());
                mPartnerMap.put(partnerKey, mPartner);
            });
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void newOrderRegister(int shopNo, long processingSerialNumber, List<WSmileOrderOutputFile> newOrderFileDataList) throws Exception {
        TOrder newOrder = this.orderProcess(shopNo, processingSerialNumber, newOrderFileDataList);
        this.deliveryCreateProcess(newOrder, newOrderFileDataList);
    }

    public TOrder orderProcess(int shopNo, long processingSerialNumber, List<WSmileOrderOutputFile> newOrderList) throws Exception {
        TOrder insertTOrder = null;
        BigDecimal totalTaxPrice = BigDecimal.ZERO;
        List<TOrderDetail> tOrderDetailList = new ArrayList<>();
        newOrderList.sort(Comparator.comparing(WSmileOrderOutputFile::getGyou));
        for (WSmileOrderOutputFile newOrder : newOrderList) {
            log.info(String.format("新規注文処理 shop_no:%d 伝票日付:%s 処理連番:%d 行番号:%d 得意先名:%s 商品コード:%s 商品名:%s ", newOrder.getShopNo(), newOrder.getDenpyouHizuke(), newOrder.getShoriRenban(), newOrder.getGyou(), newOrder.getTokuisakiMei1(), newOrder.getShouhinCode(), newOrder.getShouhinMei()));
            List<WSmileOrderOutputFile> list = this.wSmileOrderOutputFileService.findByShopNoAndShoriRenban(newOrder.getShopNo(), newOrder.getShoriRenban());
            if (!list.isEmpty()) {
                list = list.stream().filter(wSmileOrderOutputFile -> Objects.equals(wSmileOrderOutputFile.getGyou(), newOrder.getGyou())).collect(Collectors.toList());
                if (list.isEmpty()) {
                    log.warn(String.format("shopNo:%d shoriRenban:%d gyou:%d は存在しません。", newOrder.getShopNo(), newOrder.getShoriRenban(), newOrder.getGyou()));
                    continue;
                }
            }
            if (shopNo != newOrder.getShopNo()) {
                log.warn(String.format("shopNoが異なります。処理中のshopNo:%d newOrderのshopNo:%d", shopNo, newOrder.getShopNo()));
            }
            if (insertTOrder == null) {
                try {
                    insertTOrder = this.tOrderService.getByUniqKey(newOrder.getShopNo(), processingSerialNumber);
                } catch (Exception e) {
                    // 見つからない場合は新規登録処理
                }

                if (insertTOrder == null) {
                    LocalDate slipDate = newOrder.getDenpyouHizuke();
                    PartnerKey partnerKey = new PartnerKey(newOrder.getShopNo(), newOrder.getTokuisakiCode());
                    MPartner partner = this.mPartnerMap.get(partnerKey);

                    if (partner == null) {
                        partner = this.partnerProcess(newOrder);
                    }

                    MCompany company = partner.getCompany();
                    MDeliveryDestination destination = this.getDestination(partner, newOrder);

                    insertTOrder = TOrder.builder()
                            .processingSerialNumber(newOrder.getShoriRenban())
                            .shopNo(newOrder.getShopNo())
                            .companyNo(company.getCompanyNo())
                            .companyName(partner.getPartnerName())
                            .orderStatus(OrderStatus.RECEIPT.getValue())
                            .orderDateTime(slipDate == null ? null : DateTimeUtil.localDateToLocalDateTime(slipDate))
                            .paymentMethod(newOrder.getUrikakeKubun())
                            .destinationNo(destination.getDestinationNo())
                            .note(newOrder.getBiko())
                            .processingSerialNumber(processingSerialNumber)
                            .orderRoute(OrderRoute.OTHER.getValue())
                            .partnerNo(partner.getPartnerNo())
                            .partnerCode(newOrder.getTokuisakiCode())
                            .build();

                    insertTOrder = this.tOrderService.insert(insertTOrder);
                }
            }

            BigDecimal markupRatio = null;
            try {
                markupRatio = BigDecimalUtil.divide(BigDecimalUtil.multiply(BigDecimalUtil.subtract(newOrder.getTanka(), newOrder.getGenTanka()), new BigDecimal(100)), newOrder.getTanka());
            } catch (Exception e) {
                log.warn("利益率の計算に失敗しました: " + e.getMessage());
            }

            SalesGoodsKey salesGoodsKey = new SalesGoodsKey(newOrder.getShopNo(), newOrder.getShouhinCode());
            WSalesGoods wSalesGoods = this.wSalesGoodsMap.get(salesGoodsKey);

            if (wSalesGoods == null) {
                log.warn(String.format("手入力商品です。商品マスタに商品コード[%s]が存在しません。", newOrder.getShouhinCode()));
                wSalesGoods = this.salesGoodsCreateService.goodsProcess(newOrder);
                this.wSalesGoodsMap.put(salesGoodsKey, wSalesGoods);
            }

            TOrderDetail orderDetail = TOrderDetail.builder()
                    .orderNo(insertTOrder.getOrderNo())
                    .orderDetailNo(newOrder.getGyou())
                    .shopNo(insertTOrder.getShopNo())
                    .companyNo(insertTOrder.getCompanyNo())
                    .orderDetailStatus(OrderDetailStatus.RECEIPT.getCode())
                    .goodsNo(wSalesGoods.getGoodsNo())
                    .goodsCode(newOrder.getShouhinCode())
                    .orderNum(newOrder.getSuuryou())
                    .goodsPrice(newOrder.getTanka())
                    .goodsName(newOrder.getShouhinMei())
                    .taxRate(BigDecimalUtil.requireTaxRate(newOrder.getShouhizeiritsu(),
                            String.format("shori_renban=%d, gyou=%d, shouhin_code=%s", newOrder.getShoriRenban(), newOrder.getGyou(), newOrder.getShouhinCode())))
                    .taxType(newOrder.getKazeiKubun())
                    .deliveryNo(null)
                    .deliveryDetailNo(null)
                    .note(newOrder.getBiko())
                    .purchasePrice(newOrder.getGenTanka())
                    .markupRatio(markupRatio)
                    .processingSerialNumber(processingSerialNumber)
                    .build();

            orderDetail = this.tOrderDetailService.insert(orderDetail);
            tOrderDetailList.add(orderDetail);
        }

        if (insertTOrder == null) {
            throw new Exception("t_orderが生成できませんでした。");
        }

        insertTOrder.setOrderDetailList(tOrderDetailList);
        calculateTotalAmount(insertTOrder);
        return this.tOrderService.update(insertTOrder);
    }

    private void deliveryCreateProcess(TOrder newOrder, List<WSmileOrderOutputFile> wSmileOrderOutputFileList) throws Exception {
        TDelivery tDelivery = null;
        List<TOrderDetail> orderDetailList = newOrder.getOrderDetailList();

        for (WSmileOrderOutputFile wSmileOrderOutputFile : wSmileOrderOutputFileList) {
            DeliveryDetailStatus deliveryDetailStatus;

            if (tDelivery == null) {
                // ページングのはざまで登録されているかもしれない
                tDelivery = this.tDeliveryService.getByUniqKey(newOrder.getShopNo(), newOrder.getProcessingSerialNumber());
                if (tDelivery == null) {
                    // 登録処理
                    tDelivery = this.registerTDelivery(newOrder, wSmileOrderOutputFile);
                }
            }

            TOrderDetail tOrderDetail = orderDetailList.stream()
                    .filter(orderDetail -> Objects.equals(orderDetail.getProcessingSerialNumber(), wSmileOrderOutputFile.getShoriRenban()))
                    .filter(orderDetail -> Objects.equals(orderDetail.getOrderDetailNo(), wSmileOrderOutputFile.getGyou()))
                    .findFirst().orElse(null);

            if (tOrderDetail == null) {
                throw new Exception(String.format("出荷明細登録時に注文明細が見つかりません。処理連番:%d 明細番号:%d", wSmileOrderOutputFile.getShoriRenban(), wSmileOrderOutputFile.getGyou()));
            }

            LocalDate slipDate = wSmileOrderOutputFile.getDenpyouHizuke();
            if (slipDate == null || LocalDate.now().isAfter(slipDate)) {
                deliveryDetailStatus = DeliveryDetailStatus.DELIVERED;
            } else {
                deliveryDetailStatus = DeliveryDetailStatus.WAIT_SHIPPING;
            }
            TDeliveryDetail tDeliveryDetail = new TDeliveryDetail();
            // goodsPrice は TDeliveryDetail 側で廃止済みのため除外する
            // （setGoodsPrice は UnsupportedOperationException を投げる）。
            BeanUtils.copyProperties(tOrderDetail, tDeliveryDetail, "goodsPrice");
            tDeliveryDetail.setDeliveryNo(tDelivery.getDeliveryNo());
            tDeliveryDetail.setDeliveryDetailNo(wSmileOrderOutputFile.getGyou());
            tDeliveryDetail.setOrderDetailNo(wSmileOrderOutputFile.getGyou());
            tDeliveryDetail.setDeliveryDetailStatus(deliveryDetailStatus.getValue());
            tDeliveryDetail.setSlipNo(wSmileOrderOutputFile.getDenpyouBangou());
            tDeliveryDetail.setProcessingSerialNumber(wSmileOrderOutputFile.getShoriRenban());
            tDeliveryDetail = this.tDeliveryDetailService.insert(tDeliveryDetail);

            tOrderDetail.setDeliveryNo(tDeliveryDetail.getDeliveryNo());
            tOrderDetail.setDeliveryDetailNo(tDeliveryDetail.getDeliveryDetailNo());
            this.tOrderDetailService.update(tOrderDetail);
        }
    }

    private TDelivery registerTDelivery(TOrder newOrder, WSmileOrderOutputFile wSmileOrderOutputFile) throws Exception {

        LocalDate slipDate = wSmileOrderOutputFile.getDenpyouHizuke();
        LocalDate deliveryPlanDate;
        LocalDate deliveryDate = null;
        DeliveryStatus deliveryStatus;

        if (slipDate == null || LocalDate.now().isAfter(slipDate)) {
            deliveryStatus = DeliveryStatus.DELIVERED;
            deliveryDate = slipDate;
            deliveryPlanDate = slipDate;
        } else {
            deliveryStatus = DeliveryStatus.WAIT_SHIPPING;
            deliveryPlanDate = slipDate;
        }

        TDelivery tDelivery = TDelivery.builder()
                .shopNo(newOrder.getShopNo())
                .companyNo(newOrder.getCompanyNo())
                .processingSerialNumber(wSmileOrderOutputFile.getShoriRenban())
                .directShippingFlg("00010".equals(wSmileOrderOutputFile.getBikoCode()) ? Flag.YES.getValue() : Flag.NO.getValue())
                .slipNo(wSmileOrderOutputFile.getDenpyouBangou())
                .slipDate(wSmileOrderOutputFile.getDenpyouHizuke())
                .destinationNo(newOrder.getDestinationNo())
                .destinationName(wSmileOrderOutputFile.getNouhinSakiMei())
                .totalPrice(newOrder.getTotalPrice())
                .taxTotalPrice(newOrder.getTaxTotalPrice())
                .deliveryStatus(deliveryStatus.getValue())
                .deliveryPlanDate(deliveryPlanDate)
                .deliveryDate(deliveryDate)
                .partnerCode(wSmileOrderOutputFile.getTokuisakiCode())
                .build();

        return this.tDeliveryService.insert(tDelivery);
    }

    /**
     * 得意先が存在するかを確認し、登録されていなければ登録する処理
     */
    public MPartner partnerProcess(WSmileOrderOutputFile newPartnerContainsRecord) throws Exception {
        int shopNo = newPartnerContainsRecord.getShopNo();
        String partnerCode = newPartnerContainsRecord.getTokuisakiCode();
        MPartner existPartner = this.mPartnerService.getByUniqueKey(shopNo, partnerCode);
        if (existPartner != null) {
            PartnerKey partnerKey = new PartnerKey(shopNo, partnerCode);
            this.mPartnerMap.put(partnerKey, existPartner);
            return existPartner;
        }
        String partnerName = String.format("%s %s", newPartnerContainsRecord.getTokuisakiMei1(), newPartnerContainsRecord.getTokuisakiMei2());
        MPartner partner = MPartner.builder()
                .shopNo(newPartnerContainsRecord.getShopNo())
                .partnerCode(partnerCode)
                .partnerName(partnerName)
                .abbreviatedPartnerName(newPartnerContainsRecord.getTokuisakiRyakushou())
                .lastOrderDate(newPartnerContainsRecord.getDenpyouHizuke())
                .build();
        MPartner savePartner = this.mPartnerService.insert(partner);
        // m_company登録処理
        MCompany company = MCompany.builder()
                .companyType(CompanyType.PARTNER.getValue())
                .taxPattern(null)
                .partnerNo(savePartner.getPartnerNo())
                .shopNo(newPartnerContainsRecord.getShopNo())
                .companyName(partnerName)
                .abbreviatedCompanyName(newPartnerContainsRecord.getTokuisakiRyakushou())
                .build();
        MCompany saveCompany = this.mCompanyService.insert(company);
        // company_noをセットして更新
        savePartner.setCompanyNo(company.getCompanyNo());
        savePartner = this.mPartnerService.update(savePartner);
        savePartner.setMCompany(saveCompany);
        PartnerKey partnerKey = new PartnerKey(shopNo, partnerCode);
        this.mPartnerMap.put(partnerKey, savePartner);
        return savePartner;

    }
}
