package jp.co.oda32.domain.service.bcart;

/**
 * @author k_oda
 * @since 2023/04/10
 */

import jp.co.oda32.domain.model.bcart.TSmileOrderImportFile;
import jp.co.oda32.domain.repository.bcart.TSmileOrderImportFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TSmileOrderImportFileService {

    private final TSmileOrderImportFileRepository TSmileOrderImportFileRepository;

    @Transactional
    public TSmileOrderImportFile save(TSmileOrderImportFile TSmileOrderImportFile) {
        return TSmileOrderImportFileRepository.save(TSmileOrderImportFile);
    }

    public void save(List<TSmileOrderImportFile> list) {
        for (TSmileOrderImportFile TSmileOrderImportFile : list) {
            this.save(TSmileOrderImportFile);
        }
    }

//    /**
//     * Smileでの処理番号が設定されていない(=未連携)行の配列を返します。
//     *
//     * @return Smileでの処理番号が設定されていない(= 未連携)行の配列
//     */
//    public List<SmileOrderImportFile> findByProcessingSerialNumberIsNull() {
//        return this.smileOrderImportFileRepository.findByProcessingSerialNumberIsNull();
//    }

    public List<TSmileOrderImportFile> findByBCartOrderIdIn(List<Long> bCartOrderIdList) {
        return this.TSmileOrderImportFileRepository.findBybCartOrderIdIn(bCartOrderIdList);
    }

    public List<TSmileOrderImportFile> findBybCartLogisticsIdIn(List<Long> bCartLogisticsIds) {
        return this.TSmileOrderImportFileRepository.findBybCartLogisticsIdIn(bCartLogisticsIds);
    }

    /**
     * csv出力していない行を取得します。
     *
     * @return csv出力していない行
     */
    public List<TSmileOrderImportFile> findByCsvExportedFalse() {
        return this.TSmileOrderImportFileRepository.findByCsvExportedFalse();
    }

    /**
     * Asanaへタスク登録していない行を取得します
     *
     * @return Asanaへタスク登録していない行
     */
    public List<TSmileOrderImportFile> findByAsanaTaskAddFalseAndPsnUpdatedTrue() {
        return this.TSmileOrderImportFileRepository.findByAsanaTaskAddFalseAndPsnUpdatedTrue();
    }

    public List<TSmileOrderImportFile> findBySlipNumberAndProductCode(Integer slipNo, String productCode) {
        return this.TSmileOrderImportFileRepository.findBySlipNumberAndProductCode(slipNo, productCode);
    }

    /**
     * t_delivery_detail テーブルの processing_serial_number を
     * w_smile_order_import_file テーブルに反映します。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProcessingSerialNumbers() {
        TSmileOrderImportFileRepository.updateProcessingSerialNumbers();
    }
}
