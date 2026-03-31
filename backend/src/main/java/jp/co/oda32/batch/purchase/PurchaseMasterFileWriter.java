package jp.co.oda32.batch.purchase;

import jp.co.oda32.batch.purchase.logic.PaymentSupplierMasterWriterLogic;
import jp.co.oda32.batch.purchase.logic.SupplierMasterWriterLogic;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import org.springframework.batch.item.Chunk;

import java.util.List;

/**
 * 仕入マスタファイル取り込みバッチWriterクラス
 *
 * @author k_oda
 * @since 2018/07/19
 */
@Component
@StepScope
@Log4j2
@RequiredArgsConstructor
public class PurchaseMasterFileWriter implements ItemWriter<PurchaseMasterFile> {
    @Value("#{jobParameters['shopNo']}")
    private Integer shopNo;
    @NonNull
    private PaymentSupplierMasterWriterLogic paymentSupplierMasterWriterLogic;
    @NonNull
    private SupplierMasterWriterLogic supplierMasterWriterLogic;


    /**
     * Process the supplied data element. Will not be called with any null items
     * in normal operation.
     *
     * @param items items to be written
     * @throws Exception if there are errors. The framework will catch the
     *                   exception and convert or rethrow it as appropriate.
     */
    @Override
    public void write(Chunk<? extends PurchaseMasterFile> items) throws Exception {
        if (this.shopNo == null) {
            throw new Exception("shop番号がnullです。バッチ起動引数に指定してください。 --shopNo 番号");
        }
        for (PurchaseMasterFile item : items) {
            log.info(String.format("仕入先コード:%s,仕入先名1:%s,支払先コード:%s,支払先名:%s"
                    , item.get仕入先コード(), item.get仕入先名１(), item.get支払先コード(), item.get支払先名()));
            MPaymentSupplier mPaymentSupplier = this.paymentSupplierMasterWriterLogic.register(this.shopNo, item);
            this.supplierMasterWriterLogic.register(this.shopNo, mPaymentSupplier.getPaymentSupplierNo(), item);
        }
    }

}
