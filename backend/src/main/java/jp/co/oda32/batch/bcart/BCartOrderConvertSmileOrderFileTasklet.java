package jp.co.oda32.batch.bcart;

import jp.co.oda32.constant.*;
import jp.co.oda32.domain.model.bcart.*;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.model.master.MTaxRate;
import jp.co.oda32.domain.service.bcart.*;
import jp.co.oda32.domain.service.goods.WSalesGoodsService;
import jp.co.oda32.domain.service.master.MTaxRateService;
import jp.co.oda32.util.BigDecimalUtil;
import jp.co.oda32.util.DateTimeUtil;
import jp.co.oda32.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static jp.co.oda32.constant.Constants.SHIPPING_FEE_PRODUCT_CODE;

/**
 * ＜B-Cart→Smile連携＞
 * b_cart_logistics(出荷情報) から
 * TSmileOrderImportFile(自社システム用受注データ)へ変換し、DBに登録するTasklet。
 *
 * <p>「出荷指示」ステータスの b_cart_logistics を取得し、
 * まだ TSmileOrderImportFile に登録されていない出荷だけを取り込み、
 * 通常商品 + 送料行を含めて SmileOrderImportFile に変換する。</p>
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class BCartOrderConvertSmileOrderFileTasklet implements Tasklet {

    private final TSmileOrderImportFileService TSmileOrderImportFileService;
    /**
     * ケース販売品番の接頭辞 (商品No.が "case_〇〇" ならケース販売とみなす)
     */
    private static final String PRODUCT_NO_CASE_PREFIX = "case_";
    private final BCartMemberService bCartMemberService;
    private final DeliveryMappingService deliveryMappingService;
    private final MTaxRateService mTaxRateService;
    private final WSalesGoodsService wSalesGoodsService;
    private final JdbcTemplate jdbcTemplate;
    /**
     * bCartCustomerId -> BCartMember のキャッシュマップ
     */
    private final Map<Long, BCartMember> bCartMemberMap = new HashMap<>();
    private final BCartLogisticsService bCartLogisticsService;

    private final BCartOrderProductService bCartOrderProductService;
    /**
     * 現在の消費税率
     */
    private MTaxRate nowTaxRate;

    /**
     * バッチ実行メソッド。
     * <ol>
     *     <li>出荷指示ステータスの b_cart_logistics を取得</li>
     *     <li>既に登録済みの TSmileOrderImportFile を除外</li>
     *     <li>変換処理 (convertSmileOrderImportFile) を実行</li>
     *     <li>重複しないレコードだけ DB に登録</li>
     * </ol>
     */
    @Override
    public RepeatStatus execute(@NotNull StepContribution contribution,
                                @NotNull ChunkContext chunkContext) throws Exception {

        // 1. 消費税率の取得
        this.nowTaxRate = mTaxRateService.getTaxRate();
        if (this.nowTaxRate == null) {
            throw new Exception("BCartOrderConvertOrderTaskletバッチで消費税率を取得できませんでした。");
        }

        // 2. 出荷指示の物流データを取得
        List<BCartLogistics> bCartLogisticsList = bCartLogisticsService
                .findByStatus(BcartShipmentStatus.SHIPPING_INSTRUCTED.getDisplayName());
        // 納品日が設定されていない場合もはじく
        bCartLogisticsList = bCartLogisticsList.stream().filter(bCartLogistics -> bCartLogistics.getArrivalDate() != null).collect(Collectors.toList());
        if (bCartLogisticsList.isEmpty()) {
            log.info("出荷指示の物流データが存在しないため、処理を終了します。");
            return RepeatStatus.FINISHED;
        }

        // 3. 既存の TSmileOrderImportFile を取得 (b_cart_logistics_id 単位)
        List<Long> bCartLogisticsIdList = bCartLogisticsList.stream()
                .map(BCartLogistics::getId)
                .distinct()
                .collect(Collectors.toList());

        List<TSmileOrderImportFile> existTSmileOrderImportFileList =
                TSmileOrderImportFileService.findBybCartLogisticsIdIn(bCartLogisticsIdList);

        // 4. 既存登録済みの b_cart_logistics_id 一覧
        Set<Long> existLogisticsIdSet = existTSmileOrderImportFileList.stream()
                .map(TSmileOrderImportFile::getBCartLogisticsId)
                .collect(Collectors.toSet());

        // 5. 未登録 (TSmileOrderImportFile に無い) の bCartLogistics のみ抽出
        List<BCartLogistics> unregisteredLogisticsList = bCartLogisticsList.stream()
                .filter(logistics -> !existLogisticsIdSet.contains(logistics.getId()))
                .collect(Collectors.toList());
        if (unregisteredLogisticsList.isEmpty()) {
            log.info("全ての出荷指示データが既に登録済みのため、処理を終了します。");
            return RepeatStatus.FINISHED;
        }

        List<BCartOrderProduct> allOrderProducts = unregisteredLogisticsList.stream()
                .flatMap(logistics -> logistics.getBCartOrderProductList().stream())
                .collect(Collectors.toList());
        // BCartOrderのマップを作成（キー: logisticsId, 値: 対応するBCartOrder）
        Map<Long, BCartOrder> orderMap = new HashMap<>();
        for (BCartOrderProduct orderProduct : allOrderProducts) {
            if (orderProduct.getBCartOrder() != null && !orderMap.containsKey(orderProduct.getLogisticsId())) {
                orderMap.put(orderProduct.getLogisticsId(), orderProduct.getBCartOrder());
            }
        }

        // 7. 未登録の出荷データを TSmileOrderImportFile に変換
        List<TSmileOrderImportFile> TSmileOrderImportFileList = new ArrayList<>();
        for (BCartLogistics bCartLogistics : unregisteredLogisticsList) {
            TSmileOrderImportFileList.addAll(convertSmileOrderImportFile(bCartLogistics, orderMap));
        }

        // 7. 変換結果から 既存レコードと重複していないものを抽出
        List<TSmileOrderImportFile> filteredTSmileOrderImportFileList = new ArrayList<>();
        for (TSmileOrderImportFile newRecord : TSmileOrderImportFileList) {
            boolean isDuplicate = existTSmileOrderImportFileList.stream().anyMatch(existingRecord ->
                    Objects.equals(newRecord.getSlipNumber(), existingRecord.getSlipNumber()) &&
                            StringUtil.isEqual(newRecord.getProductCode(), existingRecord.getProductCode()) &&
                            BigDecimalUtil.isEqual(newRecord.getQuantity(), existingRecord.getQuantity()) &&
                            Objects.equals(newRecord.getBCartLogisticsId(), existingRecord.getBCartLogisticsId())
            );
            if (!isDuplicate) {
                filteredTSmileOrderImportFileList.add(newRecord);
            }
        }

        // 8. 行番号と処理連番を付与
        List<TSmileOrderImportFile> finalImportFiles =
                assignLineNumbersAndProcessingSerialNumbers(filteredTSmileOrderImportFileList);

        // 9. DB登録
        TSmileOrderImportFileService.save(finalImportFiles);

        return RepeatStatus.FINISHED;
    }

    /**
     * bCartLogistics (出荷情報) から
     * 自社システム用の受注データ(TSmileOrderImportFile)を生成する。
     * @param bCartLogistics 出荷情報
     * @param orderMap 事前に取得したlogisticsIdとBCartOrderのマップ
     */
    private List<TSmileOrderImportFile> convertSmileOrderImportFile(BCartLogistics bCartLogistics, Map<Long, BCartOrder> orderMap) throws Exception {
        List<TSmileOrderImportFile> importFileList = new ArrayList<>();

        // 1. 紐づく BCartOrder (受注) をマップから取得
        BCartOrder bCartOrder = orderMap.get(bCartLogistics.getId());
        // 2. ベース用の TSmileOrderImportFile を作り、共有プロパティを設定
        TSmileOrderImportFile baseImportFile = createBaseSmileOrderImportFile(bCartOrder);
        setSharedProperties(bCartOrder, baseImportFile);

        // 3. 通常商品を処理 (bCartLogistics に紐づく商品リスト)
        processOrderProductList(bCartLogistics, importFileList, baseImportFile);

        // 4. 送料行が bCartLogistics と紐づいていない場合があるため、追加対応
        addShippingFeeLineIfNeeded(bCartLogistics, bCartOrder, importFileList, baseImportFile);

        return importFileList;
    }

    /**
     * 引数なしメソッドは既存コードとの互換性のために維持
     */
    private List<TSmileOrderImportFile> convertSmileOrderImportFile(BCartLogistics bCartLogistics) throws Exception {
        return convertSmileOrderImportFile(bCartLogistics, new HashMap<>());
    }

    /**
     * bCartLogistics に紐づく商品を処理して、TSmileOrderImportFile に変換する。
     */
    private void processOrderProductList(
            BCartLogistics bCartLogistics,
            List<TSmileOrderImportFile> importFileList,
            TSmileOrderImportFile baseImportFile
    ) throws Exception {

        // bCartLogistics に紐づく商品明細のみ取得
        for (BCartOrderProduct orderProduct : bCartLogistics.getBCartOrderProductList()) {
            processOrderProduct(bCartLogistics, importFileList, orderProduct, baseImportFile);
        }
    }

    /**
     * 単一の商品行を TSmileOrderImportFile に変換する。
     * bCartLogistics が NOT_SHIPPED(未発送) であればスキップする。
     */
    private void processOrderProduct(
            BCartLogistics bCartLogistics,
            List<TSmileOrderImportFile> importFileList,
            BCartOrderProduct orderProduct,
            TSmileOrderImportFile baseImportFile
    ) throws Exception {

        BCartOrder bCartOrder = orderProduct.getBCartOrder();
        String orderCustomerCompanyName = bCartOrder.getCustomerCompName() + bCartOrder.getCustomerDepartment();

        // 出荷ステータスが "未発送" の場合は何もしない
        if (BcartShipmentStatus.NOT_SHIPPED.getDisplayName().equals(bCartLogistics.getStatus())) {
            return;
        }

        // 出荷指示中/出荷済み等であれば、detailFile を作って取り込む
        TSmileOrderImportFile detail = createDetailFile(
                baseImportFile,
                orderProduct,
                bCartLogistics,
                orderCustomerCompanyName,
                bCartOrder.getCustomerId()
        );

        // 納品日(=arrivalDate) が設定されていない場合はログを出してスキップ
        if (detail.getSlipDate() == null) {
            log.info("smile連携ファイルデータ作成バッチ: 納品日が設定されていません。"
                            + " bcartOrderId:{}, 商品名:{}",
                    bCartOrder.getId(), orderProduct.getProductName());
            return;
        }

        importFileList.add(detail);
    }

    /**
     * bCartOrder の orderProductList から「送料行」(productName=送料等) を探し、
     * まだ bCartLogistics に紐づいていないものを一緒に取り込む。
     */
    private void addShippingFeeLineIfNeeded(
            BCartLogistics bCartLogistics,
            BCartOrder bCartOrder,
            List<TSmileOrderImportFile> importFileList,
            TSmileOrderImportFile baseImportFile
    ) throws Exception {

        // 1. orderProductList を、「まだ bCartLogistics と無関係」かつ 「送料行」でフィルタ
        List<BCartOrderProduct> shippingFeeProducts = bCartOrder.getOrderProductList().stream()
                .filter(p -> p.getBCartLogistics() == null)
                .filter(p -> {
                    String code = getSmileProductCode(p);
                    return Objects.equals(code, SHIPPING_FEE_PRODUCT_CODE);
                })
                .collect(Collectors.toList());

        // 2. 対象が無ければ何もしない
        if (shippingFeeProducts.isEmpty()) {
            return;
        }

        // 3. 同じ出荷ID(=slipNumber) で送料行を作成
        String orderCustomerCompanyName = bCartOrder.getCustomerCompName() + bCartOrder.getCustomerDepartment();
        for (BCartOrderProduct shippingProduct : shippingFeeProducts) {
            TSmileOrderImportFile detail = createDetailFile(
                    baseImportFile,
                    shippingProduct,
                    bCartLogistics,
                    orderCustomerCompanyName,
                    bCartOrder.getCustomerId()
            );
            importFileList.add(detail);
        }
    }

    /**
     * bCartOrder から、共通プロパティだけを持つベース行を作る。
     * (顧客コードなど、全商品行に共通する情報が設定される)
     */
    private TSmileOrderImportFile createBaseSmileOrderImportFile(BCartOrder bCartOrder) {
        TSmileOrderImportFile importFile = new TSmileOrderImportFile();
        // 登録確認用: b_cart_order_id を仕込んでおく
        importFile.setBCartOrderId(bCartOrder.getId());
        return importFile;
    }

    /**
     * bCartOrder 共通のプロパティを TSmileOrderImportFile に反映する。
     */
    private void setSharedProperties(BCartOrder bCartOrder,
                                     TSmileOrderImportFile importFile) throws Exception {

        // 得意先コードのマッピング (bCartMember から extId を取得)
        String customerCode = customerCodeMapper(bCartOrder.getCustomerId());
        importFile.setCustomerCode(customerCode);

        // 得意先会社名(最大48文字)
        String companyName = StringUtil.limitHalfWidthAndFullWidth(
                StringUtil.convertToHalfWidthIncludingKatakana(bCartOrder.getCustomerCompName()), 48
        );
        importFile.setCustomerCompName(companyName);

        // 明細区分等の共通項目
        importFile.setDetailType(SmileOrderDetailType.NORMAL.getCode());
        importFile.setBillingType(SmileBillingType.NORMAL.getCode());
        importFile.setAccountsReceivableType(SmileAccountsReceivableType.NORMAL.getCode());
        importFile.setTransactionType(SmileTransactionType.NORMAL.getCode());
        importFile.setTransactionTypeAttribute(SmileTransactionTypeAttribute.NORMAL.getCode());
        importFile.setSlipConsumptionTaxCalculationType(SmileSlipConsumptionTaxCalculationType.NON.getCode());

        // 担当者コード (仮固定)
        importFile.setPersonInChargeCode("5"); // 小田一輝

        // 備考: 管理者メッセージ or 顧客メッセージを優先設定
        String adminMessage = bCartOrder.getAdminMessage();
        String customerMessage = bCartOrder.getCustomerMessage();
        if (StringUtil.isEmpty(adminMessage) && customerMessage != null) {
            adminMessage = customerMessage;
        }
        if (adminMessage != null) {
            adminMessage = adminMessage.replace("\n", "").replace("\r", "");
            adminMessage = StringUtil.limitHalfWidthAndFullWidth(
                    StringUtil.convertToHalfWidthIncludingKatakana(adminMessage),
                    36
            );
        }
        importFile.setRemarks(adminMessage);
    }

    /**
     * 詳細行(TSmileOrderImportFile)を作成し、商品情報/物流情報をセット。
     */
    private TSmileOrderImportFile createDetailFile(TSmileOrderImportFile baseFile,
                                                   BCartOrderProduct orderProduct,
                                                   BCartLogistics bCartLogistics,
                                                   String orderCustomerCompanyName,
                                                   Long bCartCustomerId) {

        // baseFile のコピーを生成
        TSmileOrderImportFile detail = new TSmileOrderImportFile();
        BeanUtils.copyProperties(baseFile, detail);

        // 伝票番号は bCartLogistics.id を使用
        int slipNumber = bCartLogistics.getId().intValue();
        detail.setSlipNumber(slipNumber);

        // 商品プロパティ設定
        setProductProperties(orderProduct, detail);

        // 計算系プロパティ設定 (数量や単価など)
        setCalculatedProperties(orderProduct, detail, bCartCustomerId);

        // 物流情報を反映 (納品先, slipDate, 伝票番号, etc.)
        setLogisticsProperties(bCartLogistics, detail, bCartCustomerId, orderCustomerCompanyName, detail.getCustomerCode());

        return detail;
    }

    /**
     * 商品コード/名称等を TSmileOrderImportFile に設定する。
     */
    private void setProductProperties(BCartOrderProduct orderProduct, TSmileOrderImportFile detail) {
        String productCode = getSmileProductCode(orderProduct);
        WSalesGoods wSalesGoods = wSalesGoodsService.getByShopNoAndGoodsCode(
                OfficeShopNo.B_CART_ORDER.getValue(), productCode
        );

        // 登録済みマスタが無ければ手入力商品として扱う
        if (wSalesGoods == null) {
            detail.setProductCode(Constants.FIXED_PRODUCT_CODE);
            String convertedName = StringUtil.convertToHalfWidthIncludingKatakana(orderProduct.getProductName());
            detail.setProductName(StringUtil.limitHalfWidthAndFullWidth(convertedName, 36));
            detail.setLineSummary2(productCode); // BCartで入力した品番を備考に設定
            return;
        }

        // 商品コード / 商品名 (smile上の名称)
        detail.setProductCode(productCode);
        String productName = (wSalesGoods.getMGoods().getSmileGoodsName() == null)
                ? wSalesGoods.getGoodsName()
                : wSalesGoods.getMGoods().getSmileGoodsName();
        detail.setProductName(productName);
    }

    /**
     * 数量・単価・消費税率などを計算して設定する。
     */
    private void setCalculatedProperties(BCartOrderProduct orderProduct,
                                         TSmileOrderImportFile detail,
                                         Long bCartCustomerId) {

        // セット販売か(=set_nameに「単品」が含まれない) をチェック
        String setName = orderProduct.getSetName();
        BigDecimal unitQuantity = null;
        if (setName != null && !setName.contains("単品")) {
            // ケース入数等が格納されている
            unitQuantity = orderProduct.getSetQuantity();
            // ケース数
            detail.setOrderProCount(orderProduct.getOrderProCount());
        }
        detail.setSetQuantity(unitQuantity);

        // バラ単位数量(=セット入数×ケース数)
        // B-Cart API から null で返ってくるケースに備え、null は 0 扱いで safe に計算
        BigDecimal setQ = orderProduct.getSetQuantity() != null ? orderProduct.getSetQuantity() : BigDecimal.ZERO;
        BigDecimal proCount = orderProduct.getOrderProCount() != null ? orderProduct.getOrderProCount() : BigDecimal.ZERO;
        BigDecimal quantity = setQ.multiply(proCount);
        if (setQ.signum() == 0 || proCount.signum() == 0) {
            log.warn("B-Cart 商品の setQuantity または orderProCount が 0/null です。商品ID={}, 商品名={}, setQuantity={}, orderProCount={}",
                    orderProduct.getProductId(), orderProduct.getProductName(), orderProduct.getSetQuantity(), orderProduct.getOrderProCount());
        }
        detail.setQuantity(quantity);

        // 原価(=仕入価格) の設定
        try {
            BigDecimal purchaseGoodsPrice = new BigDecimal(orderProduct.getSetCustom3());
            detail.setOriginalUnitPrice(purchaseGoodsPrice);
            detail.setCostAmount(purchaseGoodsPrice.multiply(quantity));
        } catch (Exception e) {
            log.error("仕入価格のキャストに失敗しました。b-cartの商品マスタの仕入価格に数値以外が入っていないか確認してください。" +
                            " b-cart商品ID:{}, 商品名:{}{}",
                    orderProduct.getProductId(), orderProduct.getProductName(), orderProduct.getSetName());
            detail.setOriginalUnitPrice(BigDecimal.ZERO);
            detail.setCostAmount(BigDecimal.ZERO);
        }

        // 売価(単価)
        detail.setUnitPrice(orderProduct.getUnitPrice());

        // 小計(単価×数量)
        BigDecimal amount = detail.getUnitPrice().multiply(quantity);
        detail.setAmount(amount);

        // 課税区分は外税
        detail.setTaxType(SmileTaxType.TAX_EXCLUDED.getCode());

        // 消費税率 (例: 0.10 なら10を設定)
        BigDecimal taxRate = orderProduct.getTaxRate().multiply(new BigDecimal(100));
        detail.setTaxRate(taxRate);
        // 消費税区分(軽減/通常)
        detail.setConsumptionTaxClassification(getConsumptionTaxType(taxRate).getCode());

        // bCartMember の設定がある場合、行摘要2に商品コードを入れる (主にゆうあい用)
        BCartMember bCartMember = this.bCartMemberMap.get(bCartCustomerId);
        if (bCartMember != null && bCartMember.isNeedSmileOrderFileGoodsCode()) {
            detail.setLineSummary2(detail.getProductCode());
        }
    }

    /**
     * 物流情報(納品日, 納品先コードなど)を反映する。
     */
    private void setLogisticsProperties(BCartLogistics bCartLogistics,
                                        TSmileOrderImportFile detail,
                                        Long bCartCustomerId,
                                        String orderCustomerCompanyName,
                                        String customerCode) {

        // 納品日
        detail.setSlipDate(DateTimeUtil.stringHaifunToLocalDate(bCartLogistics.getArrivalDate()));

        // 納品先(会社名+部署が異なる場合のみ、deliveryCodeを取得)
        String deliveryCompName = bCartLogistics.getCompName() + bCartLogistics.getDepartment();
        if (!StringUtil.isEqual(deliveryCompName, orderCustomerCompanyName)) {
            String deliveryCode = deliveryCodeMapper(bCartLogistics, bCartCustomerId, deliveryCompName, customerCode);
            detail.setDeliveryCode(deliveryCode);
            detail.setDeliveryCompName(deliveryCompName);
        }

        // b_cart_logistics_id を登録 (出荷ID)
        detail.setBCartLogisticsId(bCartLogistics.getId());

        // 軽減税率の場合、頭を8にする
        if (detail.getConsumptionTaxClassification().equals(ConsumptionTaxType.REDUCED.getCode())) {
            // 軽減税率の場合、伝票番号を分ける必要がある 頭を8にする
            String slipNumberString = detail.getSlipNumber().toString();
            if (slipNumberString.length() == 8 && slipNumberString.startsWith("2")) {
                String newSlipNumberString = "8" + slipNumberString.substring(1);
                Integer newSlipNumber = Integer.parseInt(newSlipNumberString);
                detail.setSlipNumber(newSlipNumber);
            }
        }
    }

    /**
     * bCartMember から extId を取得し、Smileの得意先コードとして返す。
     * マッピング情報がない場合は例外をスロー。
     */
    private String customerCodeMapper(Long bCartCustomerId) throws Exception {
        BCartMember bCartMember = this.bCartMemberMap.get(bCartCustomerId);

        // 1. すでにキャッシュがあれば extId を返す
        if (bCartMember != null && bCartMember.getExtId() != null) {
            return bCartMember.getExtId();
        }

        // 2. キャッシュに無ければDB検索
        if (bCartMember == null) {
            bCartMember = bCartMemberService.getByBCartCustomerId(bCartCustomerId);
        }

        // 3. 未登録の場合はエラー
        if (bCartMember == null || StringUtil.isEmpty(bCartMember.getExtId())) {
            String msg = (bCartMember == null)
                    ? String.format("会員情報が見つかりません。b_cart_member.id:%sを確認してください。", bCartCustomerId)
                    : String.format("会員情報が見つかりません。ext_idが未設定です。b_cart_member.id:%s", bCartCustomerId);
            log.error(msg + " 会員情報連携バッチを先に起動してください。");
            throw new Exception(msg + " 会員情報連携バッチを先に起動してください。");
        }

        // 4. キャッシュに格納して返す
        this.bCartMemberMap.put(bCartCustomerId, bCartMember);
        return bCartMember.getExtId();
    }

    /**
     * b-cart の納品先名を SMILE納品先コードに変換する。
     * 既存の DeliveryMapping に登録が無ければ作成し、既存と同一でなければ更新する。
     */
    private String deliveryCodeMapper(BCartLogistics bCartLogistics,
                                      Long bCartCustomerId,
                                      String deliveryName,
                                      String customerCode) {
        // 同一の bCartCustomerId に紐づく 納品先マッピング一覧を取得
        List<DeliveryMapping> deliveryMappingList = deliveryMappingService.findBybCartCustomerId(bCartCustomerId);

        // 1. 既に bCartLogistics.getDestinationCode() があるかを確認
        if (bCartLogistics.getDestinationCode() != null) {
            Optional<DeliveryMapping> existingOpt = deliveryMappingList.stream()
                    .filter(dm -> StringUtil.isEqual(dm.getSmileDeliveryCode(), bCartLogistics.getDestinationCode()))
                    .findFirst();
            if (existingOpt.isPresent()) {
                return existingOpt.get().getSmileDeliveryCode();
            }
        }

        // 2. b_cart_logistics.destinationCode が未登録 or nullの場合、deliveryName と一致するかを確認
        DeliveryMapping existingMapping = deliveryMappingList.stream()
                .filter(dm -> dm.getDeliveryName().equals(deliveryName))
                .findFirst()
                .orElse(null);

        // 3. 新たに DeliveryMapping を作成して比較
        DeliveryMapping saveMapping = new DeliveryMapping();
        saveMapping.setBCartCustomerId(bCartCustomerId);
        saveMapping.setBCartDestinationCode(bCartLogistics.getDestinationCode());
        saveMapping.setDeliveryName(deliveryName);
        saveMapping.setZip(bCartLogistics.getZip());
        saveMapping.setAddress1(bCartLogistics.getPref() + bCartLogistics.getAddress1());
        saveMapping.setAddress2(bCartLogistics.getAddress2());
        saveMapping.setAddress3(bCartLogistics.getAddress3());
        saveMapping.setPartnerCode(customerCode);
        saveMapping.setDeliveryIndex(null);
        saveMapping.setRecipientName1(bCartLogistics.getName());
        saveMapping.setPhoneNumber(bCartLogistics.getTel());

        // 4. 既に存在するマッピングがあれば、実データに差分がある場合のみ更新
        //    Lombok @Data の equals は id も含めて比較してしまう（saveMapping.id=null vs existing.id=設定値）ため、
        //    常に unequal になり毎回 save が走る。ここではビジネスデータフィールドのみを比較する。
        if (existingMapping != null) {
            boolean dataChanged =
                    !java.util.Objects.equals(existingMapping.getBCartDestinationCode(), saveMapping.getBCartDestinationCode())
                    || !java.util.Objects.equals(existingMapping.getDeliveryName(), saveMapping.getDeliveryName())
                    || !java.util.Objects.equals(existingMapping.getZip(), saveMapping.getZip())
                    || !java.util.Objects.equals(existingMapping.getAddress1(), saveMapping.getAddress1())
                    || !java.util.Objects.equals(existingMapping.getAddress2(), saveMapping.getAddress2())
                    || !java.util.Objects.equals(existingMapping.getAddress3(), saveMapping.getAddress3())
                    || !java.util.Objects.equals(existingMapping.getPartnerCode(), saveMapping.getPartnerCode())
                    || !java.util.Objects.equals(existingMapping.getRecipientName1(), saveMapping.getRecipientName1())
                    || !java.util.Objects.equals(existingMapping.getPhoneNumber(), saveMapping.getPhoneNumber());
            if (dataChanged) {
                saveMapping.setId(existingMapping.getId());
                saveMapping.setSmileDeliveryCode(existingMapping.getSmileDeliveryCode());
                saveMapping.setSmileCsvOutputted(false);
                deliveryMappingService.save(saveMapping);
            }
            return existingMapping.getSmileDeliveryCode();
        }

        // 5. 新規登録 (6桁の連番コードを割り振る)
        // MAX(code)+1 の race condition を吸収するため、衝突時は MAX を引き直して
        // 次の空き番号にリトライする allocate メソッドを使う。
        return deliveryMappingService.allocateSmileDeliveryCodeAndSave(saveMapping, bCartCustomerId);
    }

    /**
     * 税率から通常or軽減税率を判定し返す。
     */
    private ConsumptionTaxType getConsumptionTaxType(BigDecimal taxRate) {
        if (taxRate.compareTo(this.nowTaxRate.getReducedTaxRate()) == 0) {
            return ConsumptionTaxType.REDUCED;
        }
        return ConsumptionTaxType.REGULAR;
    }

    /**
     * BCartOrderProduct から 符号化した商品コードを返す。
     * 「送料」と判定できる場合は SHIPPING_FEE_PRODUCT_CODE を返し、
     * それ以外は 商品No.(case_を除去) を返す。
     */
    private String getSmileProductCode(BCartOrderProduct bCartOrderProduct) {
        // 商品名が "送料" なら固定の送料コード
        if (StringUtil.isEqual(bCartOrderProduct.getProductName(), "送料")) {
            return SHIPPING_FEE_PRODUCT_CODE;
        }

        String productNo = bCartOrderProduct.getProductNo();
        // "case_" で始まる場合は取り除く
        if (productNo.startsWith(PRODUCT_NO_CASE_PREFIX)) {
            productNo = productNo.substring(PRODUCT_NO_CASE_PREFIX.length());
        }
        // "_" が含まれる場合、区切り文字とみなし最後の要素を採用
        else if (productNo.contains("_")) {
            String[] parts = productNo.split("_");
            productNo = parts[parts.length - 1];
        }
        return productNo;
    }

    /**
     * 伝票番号(slipNumber)ごとに行番号を振り、処理連番も採番する。
     */
    public List<TSmileOrderImportFile> assignLineNumbersAndProcessingSerialNumbers(
            List<TSmileOrderImportFile> importFileList
    ) {
        Map<Integer, Integer> lineNumberMap = new HashMap<>();
        Map<Integer, Long> processingSerialNumberMap = new HashMap<>();

        for (TSmileOrderImportFile detail : importFileList) {
            Integer slipNumber = detail.getSlipNumber();

            // 行番号をインクリメント
            int currentLineNumber = lineNumberMap.getOrDefault(slipNumber, 0) + 1;
            detail.setLineNumber(currentLineNumber);
            lineNumberMap.put(slipNumber, currentLineNumber);

            // 初めての slipNumber ならシーケンスから処理連番を取得
            if (!processingSerialNumberMap.containsKey(slipNumber)) {
                long nextPsn = getNextProcessingSerialNumber();
                processingSerialNumberMap.put(slipNumber, nextPsn);
            }
            detail.setProcessingSerialNumber(processingSerialNumberMap.get(slipNumber));
            detail.setPsnUpdated(false);
        }

        return importFileList;
    }

    /**
     * シーケンス(w_smile_order_import_file_processing_serial_number_seq) から
     * 次の処理連番を取得する。
     */
    public Long getNextProcessingSerialNumber() {
        String sql = "SELECT nextval('w_smile_order_import_file_processing_serial_number_seq')";
        return jdbcTemplate.queryForObject(sql, Long.class);
    }
}
