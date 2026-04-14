package jp.co.oda32.domain.service.bcart;

import jp.co.oda32.constant.BCartOrderStatus;
import jp.co.oda32.constant.BcartShipmentStatus;
import jp.co.oda32.constant.Constants;
import jp.co.oda32.constant.OfficeShopNo;
import jp.co.oda32.domain.model.bcart.BCartLogistics;
import jp.co.oda32.domain.model.bcart.BCartOrder;
import jp.co.oda32.domain.model.bcart.BCartOrderProduct;
import jp.co.oda32.domain.model.bcart.TSmileOrderImportFile;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.service.goods.WSalesGoodsService;
import jp.co.oda32.domain.service.util.LoginUserUtil;
import jp.co.oda32.dto.bcart.BCartShippingInputResponse;
import jp.co.oda32.dto.bcart.BCartShippingSaveResponse;
import jp.co.oda32.dto.bcart.BCartShippingUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * B-Cart 出荷情報入力画面の業務ロジック。
 * 旧 stock-app の BCartShippingInputController を REST ベースに移植したもの。
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class BCartShippingInputService {

    private static final String PRODUCT_NO_CASE_PREFIX = "case_";

    private final BCartLogisticsService bCartLogisticsService;
    private final BCartOrderService bCartOrderService;
    private final TSmileOrderImportFileService tSmileOrderImportFileService;
    private final WSalesGoodsService wSalesGoodsService;

    /**
     * 出荷一覧を取得する。
     * <p>
     * 注意事項:
     * <ul>
     *   <li>partnerCode 指定時のフィルタは SMILE 突合済み行の customerCode に対して行う（旧実装踏襲）。
     *       SMILE 未連携の行は partnerCode 指定時に非表示になる。</li>
     *   <li>TODO: 件数が大きくなった場合は {@code BCartOrderProduct} / {@code BCartOrder} の EAGER ロードを
     *       EntityGraph で最適化する。</li>
     *   <li>TODO: ショップ横断チェックは B-CART が shop_no=1 固定運用のため未実装。運用が変わる場合はガード追加。</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public List<BCartShippingInputResponse> search(List<BcartShipmentStatus> statuses, String partnerCode) {
        checkBCartShopAccess();
        List<String> statusStrings;
        if (statuses == null || statuses.isEmpty()) {
            statusStrings = Arrays.asList(
                    BcartShipmentStatus.NOT_SHIPPED.getDisplayName(),
                    BcartShipmentStatus.SHIPPING_INSTRUCTED.getDisplayName());
        } else {
            statusStrings = statuses.stream()
                    .map(BcartShipmentStatus::getDisplayName)
                    .collect(Collectors.toList());
        }
        List<BCartLogistics> logisticsList = bCartLogisticsService.findByStatusIn(statusStrings);

        // 旧システム処理済みの不整合データを除外:
        // 紐づく BCartOrder が全て "完了" の logistics は既処理とみなして非表示
        logisticsList = logisticsList.stream()
                .filter(this::hasActiveOrder)
                .collect(Collectors.toList());

        List<BCartShippingInputResponse> responses = buildResponseList(logisticsList);

        if (partnerCode != null && !partnerCode.isBlank()) {
            String needle = partnerCode.trim();
            responses = responses.stream()
                    .filter(r -> r.partnerCode() != null && r.partnerCode().contains(needle))
                    .collect(Collectors.toList());
        }
        return responses;
    }

    @Transactional
    public BCartShippingSaveResponse saveAll(List<BCartShippingUpdateRequest> requests) {
        checkBCartShopAccess();
        if (requests == null || requests.isEmpty()) {
            return new BCartShippingSaveResponse(0, Collections.emptyList());
        }
        List<Long> logisticsIds = requests.stream()
                .map(BCartShippingUpdateRequest::bCartLogisticsId)
                .distinct()
                .toList();

        Map<Long, BCartShippingUpdateRequest> requestMap = requests.stream()
                .collect(Collectors.toMap(BCartShippingUpdateRequest::bCartLogisticsId, r -> r, (a, b) -> a));

        List<BCartLogistics> fetched = bCartLogisticsService.findByIdIn(logisticsIds);
        List<BCartLogistics> targets = new ArrayList<>();
        List<Long> skippedIds = new ArrayList<>();
        for (BCartLogistics l : fetched) {
            if (BcartShipmentStatus.SHIPPED.getDisplayName().equals(l.getStatus()) && l.isBCartCsvExported()) {
                skippedIds.add(l.getId());
            } else {
                targets.add(l);
            }
        }

        // adminMessage を同一 order 内で複数 logistics が更新しようとした場合、
        // 「dirty フラグ付きで最後に来たリクエストの値」を採用する（決定論的）。
        // targets の反復順は Repository の返却順（= logisticsIds 順）に依存するため、
        // 明示的に orderId 単位で最後の値を計算する。
        Map<Long, String> orderIdToAdminMessage = new HashMap<>();
        for (BCartLogistics logistics : targets) {
            BCartShippingUpdateRequest req = requestMap.get(logistics.getId());
            if (req == null || !req.adminMessageDirty() || logistics.getBCartOrderProductList() == null) continue;
            for (BCartOrderProduct product : logistics.getBCartOrderProductList()) {
                BCartOrder order = product.getBCartOrder();
                if (order != null) {
                    orderIdToAdminMessage.put(order.getId(), req.adminMessage());
                }
            }
        }

        List<BCartOrder> orderUpdates = new ArrayList<>();
        Set<Long> orderIdApplied = new HashSet<>();
        for (BCartLogistics logistics : targets) {
            BCartShippingUpdateRequest req = requestMap.get(logistics.getId());
            if (req == null) continue;
            logistics.setDeliveryCode(req.deliveryCode());
            // b_cart_logistics.shipment_date は String カラムのため ISO 形式で固定化する
            logistics.setShipmentDate(req.shipmentDate() == null
                    ? null
                    : DateTimeFormatter.ISO_LOCAL_DATE.format(req.shipmentDate()));
            logistics.setStatus(req.shipmentStatus().getDisplayName());
            logistics.setMemo(req.memo());

            if (logistics.getBCartOrderProductList() != null) {
                for (BCartOrderProduct product : logistics.getBCartOrderProductList()) {
                    BCartOrder order = product.getBCartOrder();
                    if (order == null) continue;
                    if (!orderIdToAdminMessage.containsKey(order.getId())) continue;
                    if (orderIdApplied.add(order.getId())) {
                        order.setAdminMessage(orderIdToAdminMessage.get(order.getId()));
                        orderUpdates.add(order);
                    }
                }
            }
        }

        bCartLogisticsService.save(targets);
        if (!orderUpdates.isEmpty()) {
            bCartOrderService.save(orderUpdates);
        }
        syncBCartOrderStatus(targets);

        return new BCartShippingSaveResponse(targets.size(), skippedIds);
    }

    @Transactional
    public void bulkUpdateStatus(List<Long> logisticsIds, BcartShipmentStatus status) {
        checkBCartShopAccess();
        if (logisticsIds == null || logisticsIds.isEmpty() || status == null) {
            return;
        }
        List<BCartLogistics> targets = bCartLogisticsService.findByIdIn(logisticsIds).stream()
                .filter(l -> !(BcartShipmentStatus.SHIPPED.getDisplayName().equals(l.getStatus()) && l.isBCartCsvExported()))
                .collect(Collectors.toList());

        for (BCartLogistics logistics : targets) {
            logistics.setStatus(status.getDisplayName());
        }
        bCartLogisticsService.save(targets);
        syncBCartOrderStatus(targets);
    }

    /* =========================================================
     *  Internal helpers
     * ========================================================= */

    /**
     * B-CART 出荷情報は shop_no={@link OfficeShopNo#B_CART_ORDER} 固定運用。
     * admin (shopNo=0) または B-CART ショップのユーザーのみ許可する。
     */
    private void checkBCartShopAccess() {
        Integer loginShopNo;
        try {
            loginShopNo = LoginUserUtil.getLoginUserInfo().getUser().getShopNo();
        } catch (Exception e) {
            throw new AccessDeniedException("ログインしていません");
        }
        if (loginShopNo == null) {
            throw new AccessDeniedException("ショップが設定されていません");
        }
        if (loginShopNo == 0 || loginShopNo == OfficeShopNo.B_CART_ORDER.getValue()) {
            return;
        }
        throw new AccessDeniedException("B-CART 出荷情報の操作権限がありません (shopNo=" + loginShopNo + ")");
    }

    /**
     * 紐づく BCartOrder のうち少なくとも 1 つが "完了" 以外 (未処理/処理中等) の場合 true。
     * 旧 stock-app が注文を "完了" にしたが b_cart_logistics を更新し忘れた過去データを画面から除外する目的。
     */
    private boolean hasActiveOrder(BCartLogistics logistics) {
        if (logistics.getBCartOrderProductList() == null || logistics.getBCartOrderProductList().isEmpty()) {
            return true; // order が紐づいていない特殊ケースは表示を維持
        }
        List<BCartOrder> orders = logistics.getBCartOrderProductList().stream()
                .map(BCartOrderProduct::getBCartOrder)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (orders.isEmpty()) return true;
        return orders.stream().anyMatch(o -> !BCartOrderStatus.COMPLETED.getStatus().equals(o.getStatus()));
    }

    private List<BCartShippingInputResponse> buildResponseList(List<BCartLogistics> logisticsList) {
        if (logisticsList == null || logisticsList.isEmpty()) return Collections.emptyList();

        List<Long> logisticsIds = logisticsList.stream()
                .map(BCartLogistics::getId)
                .distinct()
                .collect(Collectors.toList());

        // SMILE 連携前後どちらの t_smile_order_import_file も突合対象にする
        // (psn_updated=false の仮発番状態でも商品情報・伝票番号は取得できる)
        // 送料レコードは除外する
        List<TSmileOrderImportFile> smileFiles = tSmileOrderImportFileService.findBybCartLogisticsIdIn(logisticsIds).stream()
                .filter(r -> !Constants.SHIPPING_FEE_PRODUCT_CODE.equals(r.getProductCode()))
                .collect(Collectors.toList());

        // readOnly tx 内で entity を mutate すると flush 事故の原因になるため、
        // setQuantity のゼロ/null 補正は キー生成時に局所変数で行う
        Map<BCartLogisticsKey, List<TSmileOrderImportFile>> grouped = smileFiles.stream()
                .collect(Collectors.groupingBy(s -> {
                    BigDecimal setQuantity = normalizeSetQuantity(s.getSetQuantity());
                    return new BCartLogisticsKey(
                            s.getBCartLogisticsId(),
                            resolveSmileProductCode(s.getProductName(), s.getProductCode()),
                            setQuantity,
                            s.getQuantity());
                }));

        grouped.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .forEach(e -> log.warn("重複したキーが見つかりました: {} ({} 件)", e.getKey(), e.getValue().size()));

        Map<BCartLogisticsKey, TSmileOrderImportFile> smileMap = new HashMap<>();
        grouped.forEach((k, v) -> smileMap.put(k, v.get(0)));

        List<BCartShippingInputResponse> responses = new ArrayList<>();
        for (BCartLogistics logistics : logisticsList) {
            String shipmentDate = logistics.getShipmentDate();
            if (shipmentDate == null || shipmentDate.isEmpty()) {
                shipmentDate = logistics.getArrivalDate();
            }
            StringBuilder memoBuilder = new StringBuilder();
            if (logistics.getDueDate() != null && !logistics.getDueDate().isEmpty()) {
                memoBuilder.append(logistics.getDueDate());
            }
            if (logistics.getMemo() != null && !logistics.getMemo().isEmpty()) {
                memoBuilder.append(logistics.getMemo());
            }
            String adminMessage = logistics.getBCartOrderProductList() == null
                    ? null
                    : logistics.getBCartOrderProductList().stream()
                    .map(BCartOrderProduct::getBCartOrder)
                    .filter(Objects::nonNull)
                    .map(BCartOrder::getAdminMessage)
                    .filter(Objects::nonNull)
                    .distinct()
                    .findFirst()
                    .orElse(null);

            List<String> goodsInfo = new ArrayList<>();
            List<Long> smileSerialNoList = new ArrayList<>();
            String partnerCode = null;
            String partnerName = null;

            if (logistics.getBCartOrderProductList() != null) {
                for (BCartOrderProduct product : logistics.getBCartOrderProductList()) {
                    BigDecimal setQuantity = normalizeSetQuantity(product.getSetQuantity());
                    BigDecimal orderProCount = product.getOrderProCount() != null ? product.getOrderProCount() : BigDecimal.ONE;
                    String productCode = resolveSmileProductCode(product.getProductName(), product.getProductNo());
                    BCartLogisticsKey key = new BCartLogisticsKey(
                            logistics.getId(),
                            productCode,
                            setQuantity,
                            orderProCount.multiply(setQuantity));

                    TSmileOrderImportFile smile = smileMap.get(key);
                    if (smile == null) {
                        log.info("該当の出荷情報が見つかりません。SMILE連携から売上明細取込を行ってください。商品名:{} productCode:{} bcartLogisticsId:{} 入数:{} 注文数:{}",
                                product.getProductName(), productCode, product.getLogisticsId(), setQuantity, orderProCount);
                        continue;
                    }
                    if (partnerCode == null) {
                        partnerCode = smile.getCustomerCode();
                        partnerName = smile.getCustomerCompName();
                    }
                    goodsInfo.add(smile.getProductCode() + "：" + product.getProductName() + ":" + smile.getQuantity());
                    // smile 連番は SMILE 連携済み (psn_updated=true) の場合のみ採用する
                    if (smile.isPsnUpdated() && smile.getProcessingSerialNumber() != null) {
                        smileSerialNoList.add(smile.getProcessingSerialNumber());
                    }
                }
            }
            smileSerialNoList = smileSerialNoList.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());

            // SMILE 未突合時でも BCartOrder から得意先情報を取得してフォールバック
            if (partnerCode == null || partnerName == null) {
                BCartOrder fallbackOrder = logistics.getBCartOrderProductList() == null ? null
                        : logistics.getBCartOrderProductList().stream()
                        .map(BCartOrderProduct::getBCartOrder)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
                if (fallbackOrder != null) {
                    if (partnerCode == null) partnerCode = fallbackOrder.getCustomerExtId();
                    if (partnerName == null) partnerName = fallbackOrder.getCustomerCompName();
                }
            }

            responses.add(BCartShippingInputResponse.builder()
                    .bCartLogisticsId(logistics.getId())
                    .partnerCode(partnerCode)
                    .partnerName(partnerName)
                    .deliveryCompName(logistics.getCompName())
                    .deliveryCode(logistics.getDeliveryCode())
                    .shipmentDate(shipmentDate)
                    .memo(memoBuilder.toString())
                    .adminMessage(adminMessage)
                    .shipmentStatus(logistics.getStatus())
                    .goodsInfo(goodsInfo)
                    .smileSerialNoList(smileSerialNoList)
                    .bCartCsvExported(logistics.isBCartCsvExported())
                    .build());
        }
        return responses;
    }

    /** setQuantity が null または 0 の場合は 1 とみなす (旧実装 convertSetQuantity 踏襲) */
    private static BigDecimal normalizeSetQuantity(BigDecimal value) {
        if (value == null || BigDecimal.ZERO.compareTo(value) == 0) {
            return BigDecimal.ONE;
        }
        return value;
    }

    String resolveSmileProductCode(String goodsName, String goodsCode) {
        if ("送料".equals(goodsName)) {
            return Constants.SHIPPING_FEE_PRODUCT_CODE;
        }
        if (goodsCode == null) {
            return Constants.FIXED_PRODUCT_CODE;
        }
        String normalized = goodsCode;
        if (normalized.startsWith(PRODUCT_NO_CASE_PREFIX)) {
            normalized = normalized.substring(PRODUCT_NO_CASE_PREFIX.length());
        } else if (normalized.contains("_")) {
            String[] parts = normalized.split("_");
            normalized = parts[parts.length - 1];
        }
        WSalesGoods wSalesGoods = wSalesGoodsService.getByShopNoAndGoodsCode(OfficeShopNo.B_CART_ORDER.getValue(), normalized);
        if (wSalesGoods == null) {
            return Constants.FIXED_PRODUCT_CODE;
        }
        return normalized;
    }

    private void syncBCartOrderStatus(List<BCartLogistics> updatedLogistics) {
        if (updatedLogistics == null || updatedLogistics.isEmpty()) return;

        Map<Long, String> updatedStatusMap = updatedLogistics.stream()
                .collect(Collectors.toMap(BCartLogistics::getId, BCartLogistics::getStatus, (a, b) -> b));

        Set<Long> affectedOrderIds = new HashSet<>();
        for (BCartLogistics logistics : updatedLogistics) {
            if (logistics.getBCartOrderProductList() == null) continue;
            for (BCartOrderProduct product : logistics.getBCartOrderProductList()) {
                if (product.getOrderId() != null) affectedOrderIds.add(product.getOrderId());
            }
        }
        if (affectedOrderIds.isEmpty()) return;

        List<BCartOrder> orders = bCartOrderService.findWithProductsByIdIn(new ArrayList<>(affectedOrderIds));
        List<BCartOrder> toSave = new ArrayList<>();
        for (BCartOrder order : orders) {
            if (order.getOrderProductList() == null) continue;
            List<BCartLogistics> relatedLogistics = order.getOrderProductList().stream()
                    .map(BCartOrderProduct::getBCartLogistics)
                    .filter(Objects::nonNull)
                    .collect(Collectors.collectingAndThen(Collectors.toMap(
                            BCartLogistics::getId, l -> l, (a, b) -> a), m -> new ArrayList<>(m.values())));

            for (BCartLogistics l : relatedLogistics) {
                String newStatus = updatedStatusMap.get(l.getId());
                if (newStatus != null) {
                    l.setStatus(newStatus);
                }
            }
            boolean allShipped = !relatedLogistics.isEmpty() && relatedLogistics.stream()
                    .allMatch(l -> BcartShipmentStatus.SHIPPED.getDisplayName().equals(l.getStatus()));
            order.setStatus(allShipped ? BCartOrderStatus.COMPLETED.getStatus() : BCartOrderStatus.PROCESSING.getStatus());
            toSave.add(order);
        }
        if (!toSave.isEmpty()) {
            bCartOrderService.save(toSave);
        }
    }

    /**
     * SMILE 連携データとの突合キー。
     */
    public record BCartLogisticsKey(Long logisticsId, String productCode, BigDecimal setQuantity, BigDecimal quantity) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BCartLogisticsKey that)) return false;
            return Objects.equals(logisticsId, that.logisticsId)
                    && Objects.equals(productCode, that.productCode)
                    && compareDecimal(setQuantity, that.setQuantity) == 0
                    && compareDecimal(quantity, that.quantity) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    logisticsId,
                    productCode,
                    setQuantity == null ? null : setQuantity.stripTrailingZeros(),
                    quantity == null ? null : quantity.stripTrailingZeros());
        }

        private static int compareDecimal(BigDecimal a, BigDecimal b) {
            if (a == null && b == null) return 0;
            if (a == null) return -1;
            if (b == null) return 1;
            return a.compareTo(b);
        }
    }
}
