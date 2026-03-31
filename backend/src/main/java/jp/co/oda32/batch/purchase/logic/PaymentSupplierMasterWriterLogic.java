package jp.co.oda32.batch.purchase.logic;

import jp.co.oda32.batch.purchase.PurchaseMasterFile;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

/**
 * 支払先を登録します
 *
 * @author k_oda
 * @since 2019/06/21
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class PaymentSupplierMasterWriterLogic {
    @NonNull
    private MPaymentSupplierService mPaymentSupplierService;

    public MPaymentSupplier register(Integer shopNo, PurchaseMasterFile item) throws Exception {
        MPaymentSupplier exist = this.mPaymentSupplierService.getByPaymentSupplierCode(shopNo, item.get仕入先コード());
        if (exist != null) {
            // 登録されている場合はスキップする 更新の場合は画面で修正
            log.info(String.format("支払先登録済なのでスキップします。支払先名:%s 支払先コード:%s", item.get仕入先名１(), item.get仕入先コード()));
            return exist;
        }
        MPaymentSupplier paymentSupplier = MPaymentSupplier.builder()
                .paymentSupplierCode(item.get仕入先コード())
                .paymentSupplierName(item.get仕入先名１())
                .cutoffDate(item.get締日１().intValue())
                .taxTimingCode(item.get消費税通知区分())
                .taxTiming(item.get消費税通知区分名())
                .shopNo(shopNo)
                .build();
        return this.mPaymentSupplierService.insert(paymentSupplier);
    }
}
