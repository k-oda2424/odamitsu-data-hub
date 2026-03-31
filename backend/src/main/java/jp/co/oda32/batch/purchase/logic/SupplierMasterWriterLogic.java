package jp.co.oda32.batch.purchase.logic;

import jp.co.oda32.batch.purchase.PurchaseMasterFile;
import jp.co.oda32.domain.model.master.MSupplier;
import jp.co.oda32.domain.service.master.MSupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

/**
 * 仕入先マスタ連携バッチ
 * 仕入先を登録するクラス
 *
 * @author k_oda
 * @since 2019/06/21
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class SupplierMasterWriterLogic {
    @NonNull
    private MSupplierService mSupplierService;

    public MSupplier register(Integer shopNo, Integer paymentSupplierNo, PurchaseMasterFile item) throws Exception {
        MSupplier exist = this.mSupplierService.getByUniqueKey(shopNo, item.get仕入先コード());
        if (exist != null) {
            // 登録されている場合はスキップする 更新の場合は画面で修正
            log.info(String.format("仕入先登録済なのでスキップします。仕入先名:%s 仕入先コード:%s", item.get仕入先名１(), item.get仕入先コード()));
            return exist;
        }
        MSupplier mSupplier = MSupplier.builder()
                .supplierCode(item.get仕入先コード())
                .supplierName(item.get仕入先名１())
                .paymentSupplierNo(paymentSupplierNo)
                .shopNo(shopNo)
                .build();
        return this.mSupplierService.insert(mSupplier);
    }
}
