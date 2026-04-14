package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.model.order.TDelivery;
import jp.co.oda32.domain.model.order.TDeliveryDetail;
import jp.co.oda32.domain.model.order.TOrder;
import jp.co.oda32.domain.model.order.TOrderDetail;
import jp.co.oda32.domain.model.smile.WSmileOrderOutputFile;
import jp.co.oda32.domain.service.order.TDeliveryDetailService;
import jp.co.oda32.domain.service.order.TDeliveryService;
import jp.co.oda32.domain.service.order.TOrderDetailService;
import jp.co.oda32.domain.service.order.TOrderService;
import jp.co.oda32.domain.service.smile.WSmileOrderOutputFileService;
import jp.co.oda32.util.CollectionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * smile売上データから処理連番を更新するタスクレットクラス
 *
 * @author k_oda
 * @since 2024/05/13
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class SmileProcessingSerialNumberUpdateTasklet implements Tasklet {
    private final WSmileOrderOutputFileService wSmileOrderOutputFileService;
    private final TOrderService tOrderService;
    private final TOrderDetailService tOrderDetailService;
    private final TDeliveryService tDeliveryService;
    private final TDeliveryDetailService tDeliveryDetailService;

    Map<Map<Integer, String>, List<TDeliveryDetail>> existDeliveryMap = new HashMap<>();

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

        this.modifiedOrderProcess();

        return RepeatStatus.FINISHED;
    }

    private void modifiedOrderProcess() throws Exception {
        // 更新が必要な行
        int pageNumber = 0;

        List<WSmileOrderOutputFile> wSmileOrderOutputFiles;
        do {
            wSmileOrderOutputFiles = wSmileOrderOutputFileService.handleWSmileOrderOutputFiles(pageNumber);

            // Process each file in the list here
            // 注文更新処理
            updateModifiedOrder(wSmileOrderOutputFiles);
            pageNumber++;
        } while (!wSmileOrderOutputFiles.isEmpty());
    }

    private void updateModifiedOrder(List<WSmileOrderOutputFile> modifiedOrderList) throws Exception {
        // 商品コードか出荷数量が変更になっている
        // modifiedOrderListから処理連番まとまりのMapを作成する
        Map<Long, List<WSmileOrderOutputFile>> renbanMap = modifiedOrderList.stream()
                .collect(Collectors.groupingBy(WSmileOrderOutputFile::getShoriRenban));
        // renbanMapをforでまわす
        for (Map.Entry<Long, List<WSmileOrderOutputFile>> entry : renbanMap.entrySet()) {
            // 注文・注文明細
            updateOrder(entry.getValue());
        }
    }

    private void updateOrder(List<WSmileOrderOutputFile> modifiedOrderFileList) throws Exception {
        // 処理連番ごとにまわしているので伝票日付のチェックは1回で良い
        TDelivery tDelivery = null;
        TOrder tOrder = null;
        // shop_no,denpyou_bangouのListのMapを作成
        Map<Integer, List<String>> tDeliveryMap = modifiedOrderFileList.stream()
                .collect(Collectors.groupingBy(
                        WSmileOrderOutputFile::getShopNo,                   // キーになるshopNoを指定
                        Collectors.mapping(
                                WSmileOrderOutputFile::getDenpyouBangou,         // 各要素に対して伝票番号を取得
                                Collectors.toList()                            // 取得した伝票番号をリストにする
                        )
                ));
        // tDeliveryMapをforでまわす
        for (Map.Entry<Integer, List<String>> entry : tDeliveryMap.entrySet()) {
            List<TDeliveryDetail> tDeliveryDetailList = this.tDeliveryDetailService.findBySlipNoList(entry.getKey(), entry.getValue());
            // existDeliveryMapに値をつめる
            tDeliveryDetailList.forEach(tDeliveryDetail -> {
                Map<Integer, String> key = new HashMap<>();
                key.put(tDeliveryDetail.getShopNo(), tDeliveryDetail.getSlipNo());

                // 同じkeyだったらリストに追加する
                this.existDeliveryMap.computeIfAbsent(key, k -> new ArrayList<>()).add(tDeliveryDetail);
            });
        }
        for (WSmileOrderOutputFile modifiedOrderFile : modifiedOrderFileList) {
            System.out.printf("処理連番更新 伝票日付%s 処理連番:%d 伝票番号:%s 行:%d shop_no:%d 得意先:%s 商品コード:%s 商品名:%s%n"
                    , modifiedOrderFile.getDenpyouHizuke()
                    , modifiedOrderFile.getShoriRenban()
                    , modifiedOrderFile.getDenpyouBangou()
                    , modifiedOrderFile.getGyou()
                    , modifiedOrderFile.getShopNo()
                    , modifiedOrderFile.getTokuisakiMei1()
                    , modifiedOrderFile.getShouhinCode()
                    , modifiedOrderFile.getShouhinMei());
            // shop_noとslip_noのMapを作成
            Map<Integer, String> sameSlipNoKey = new HashMap<>();
            sameSlipNoKey.put(modifiedOrderFile.getShopNo(), modifiedOrderFile.getDenpyouBangou());
            try {
                List<TDeliveryDetail> sameSlipNoTDeliveryList = this.existDeliveryMap.get(sameSlipNoKey);
                if (CollectionUtil.isEmpty(sameSlipNoTDeliveryList)) {
                    log.info(String.format("対象の出荷明細が見つかりません。ショップ番号:%d 伝票番号:%s", modifiedOrderFile.getShopNo(), modifiedOrderFile.getDenpyouBangou()));
                    continue;
                }
                List<TDeliveryDetail> sameGyoList = sameSlipNoTDeliveryList.stream()
                        .filter(tDeliveryDetail -> Objects.equals(tDeliveryDetail.getDeliveryDetailNo(), modifiedOrderFile.getGyou()))
                        .collect(Collectors.toList());

                if (CollectionUtil.isEmpty(sameGyoList)) {
                    log.info(String.format("対象の出荷明細が見つかりません。ショップ番号:%d 伝票番号:%s 行番号:%d", modifiedOrderFile.getShopNo(), modifiedOrderFile.getDenpyouBangou(), modifiedOrderFile.getGyou()));
                    continue;
                } else if (sameGyoList.size() > 1) {
                    log.info(String.format("対象の出荷明細が複数見つかりました。ショップ番号:%d 伝票番号:%s 行番号:%d", modifiedOrderFile.getShopNo(), modifiedOrderFile.getDenpyouBangou(), modifiedOrderFile.getGyou()));
                    continue;
                }
                TDeliveryDetail tDeliveryDetail = sameGyoList.get(0);
                if (tDeliveryDetail.getProcessingSerialNumber() == null) {
                    tDeliveryDetail.setProcessingSerialNumber(modifiedOrderFile.getShoriRenban());
                    this.tDeliveryDetailService.update(tDeliveryDetail);
                }
                if (tDelivery == null) {
                    tDelivery = tDeliveryDetail.getTDelivery();
                    if (tDelivery.getProcessingSerialNumber() == null) {
                        tDelivery.setProcessingSerialNumber(modifiedOrderFile.getShoriRenban());
                        this.tDeliveryService.update(tDelivery);
                    }
                }
                TOrderDetail tOrderDetail = tDeliveryDetail.getTOrderDetail();
                if (tOrderDetail.getProcessingSerialNumber() == null) {
                    tOrderDetail.setProcessingSerialNumber(modifiedOrderFile.getShoriRenban());
                    tOrderDetailService.update(tOrderDetail);
                }
                if (tOrder == null) {
                    tOrder = tOrderDetail.getTOrder();
                    if (tOrder.getProcessingSerialNumber() == null) {
                        tOrder.setProcessingSerialNumber(modifiedOrderFile.getShoriRenban());
                        this.tOrderService.update(tOrder);
                    }
                }
            } catch (Exception e) {
                log.error("Error occurred " + e.getMessage());
            }
        }
    }
}
