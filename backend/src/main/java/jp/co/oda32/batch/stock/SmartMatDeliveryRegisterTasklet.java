package jp.co.oda32.batch.stock;

import jp.co.oda32.constant.DeliveryDetailStatus;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.master.MSmartMat;
import jp.co.oda32.domain.model.order.TDeliveryDetail;
import jp.co.oda32.domain.service.master.MSmartMatService;
import jp.co.oda32.domain.service.order.TDeliveryDetailService;
import jp.co.oda32.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 小田光スマートマット管理アプリに納品を登録するAPIを呼び出すタスクレットクラス
 *
 * @author k_oda
 * @since 2020/01/09
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class SmartMatDeliveryRegisterTasklet implements Tasklet {
    @Value("${delivery.register.api.url}")
    private String DELIVERY_REGISTER_API_URL = "http://smart-mat.local:8880/api/deliveryRegister";
    @Value("#{jobParameters['spanMonths']}")
    protected Integer spanMonths;
    @NonNull
    MSmartMatService mSmartMatService;
    @NonNull
    TDeliveryDetailService tDeliveryDetailService;
    @NonNull
    RestTemplate restTemplate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // マットマスタ検索
        List<MSmartMat> matList = this.mSmartMatService.findAll();
        List<Integer> companyNoList = matList.stream().map(MSmartMat::getCompanyNo).distinct().collect(Collectors.toList());
        List<Integer> goodsNoList = matList.stream().map(MSmartMat::getGoodsNo).distinct().collect(Collectors.toList());
        // 会社番号と商品番号で出荷明細テーブルを検索（連携済フラグが立っていないもの）
        LocalDate deliveredTo = LocalDate.now();
        LocalDate deliveredFrom = deliveredTo.minusMonths(spanMonths);
        List<TDeliveryDetail> tDeliveryDetailList = this.tDeliveryDetailService.find(companyNoList, goodsNoList, null, deliveredFrom, deliveredTo, null, null, Flag.NO.getValue(), Flag.NO);
        tDeliveryDetailList = tDeliveryDetailList.stream()
                .filter(tDeliveryDetail -> DeliveryDetailStatus.getNotCancelStatus().contains(DeliveryDetailStatus.purse(tDeliveryDetail.getDeliveryDetailStatus())))
                .collect(Collectors.toList());
        if (tDeliveryDetailList.isEmpty()) {
            log.info("処理対象がありません。");
            return RepeatStatus.FINISHED;
        }
        // マットマスタの商品のみ取り出す
        for (TDeliveryDetail tDeliveryDetail : tDeliveryDetailList) {
            for (MSmartMat mat : matList) {
                if (mat.getGoodsNo().equals(tDeliveryDetail.getGoodsNo()) && mat.getCompanyNo().equals(tDeliveryDetail.getCompanyNo())) {
                    this.executeSmartMatAPI(mat, tDeliveryDetail);
                }
            }
        }
        return RepeatStatus.FINISHED;
    }

    private void executeSmartMatAPI(MSmartMat mat, TDeliveryDetail deliveryDetail) {
        //setting up the request headers
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add("X-Odamitsu-Api-Key", mat.getMCompany().getMatApiKey());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DateTimeUtil.TIMESTAMP_FORMAT);
        try {
            //setting up the request body
            String deliveryDateTime = deliveryDetail.getTDelivery().getDeliveryDate().atStartOfDay().format(formatter);
            DeliveryRegisterForm deliveryRegisterForm = DeliveryRegisterForm.builder()
                    .matId(mat.getMatId())
                    .goodsCode(deliveryDetail.getGoodsCode())
                    .deliveryDateTime(deliveryDateTime)
                    .deliveryNum(deliveryDetail.getDeliveryNum())
                    .extOrderNo(deliveryDetail.getSlipNo())
                    .build();
            //request entity is created with request body and headers
            HttpEntity<DeliveryRegisterForm> requestEntity = new HttpEntity<>(deliveryRegisterForm, requestHeaders);
            log.info(String.format("DELIVERY_REGISTER_API_URL:%s", DELIVERY_REGISTER_API_URL));
            ResponseEntity<DeliveryRegisterForm> responseEntity = this.restTemplate.exchange(DELIVERY_REGISTER_API_URL,
                    HttpMethod.POST,
                    requestEntity,
                    DeliveryRegisterForm.class
            );
            log.info(String.format("Response Header %s", responseEntity.getHeaders()));
            log.info(String.format("Response Status Code %s", responseEntity.getStatusCode()));
            log.info(String.format("Response Status Code %s", responseEntity.getStatusCodeValue()));
            log.info(String.format("Response body %s", responseEntity.getBody()));
            if (responseEntity.getStatusCode().equals(HttpStatus.CREATED)) {
                // 連携済フラグを立てる
                deliveryDetail.setMatApiFlg(Flag.YES.getValue());
                tDeliveryDetailService.update(deliveryDetail);
            } else {
                // 登録失敗
                log.warn(String.format("スマートマット納品登録API登録失敗:%s", Objects.requireNonNull(requestEntity.getBody()).getMessage()));
            }
        } catch (Exception e) {
            log.error(String.format("スマートマット納品登録APIでエラーが発生しました。%s, trace:%s", e.getMessage(), Arrays.toString(e.getStackTrace())));
        }
    }
}
