package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.model.smile.WSmilePayment;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * SMILE支払情報のプロセッサークラス
 * CSVファイルから読み込んだデータをWSmilePaymentエンティティに変換します
 *
 * @author ai_assistant
 * @since 2025/05/02
 */
@Component
@StepScope
@Log4j2
public class SmilePaymentProcessor implements ItemProcessor<SmilePaymentFile, WSmilePayment> {

    /**
     * SmilePaymentFileからWSmilePaymentエンティティに変換します
     *
     * @param item SmilePaymentFile
     * @return 変換されたWSmilePaymentエンティティ、変換できない場合はnull
     */
    @Override
    public WSmilePayment process(SmilePaymentFile item) {
        try {
            if (item == null) {
                return null;
            }

            WSmilePayment payment = new WSmilePayment();

            // 各フィールドをエンティティにマッピング
            payment.setVoucherDate(item.get伝票日付());
            payment.setYearMonth(item.get年月度());
            payment.setVoucherNo(item.get伝票番号());
            payment.setProcessingSerialNumber(item.get処理連番());
            payment.setLineNo(item.get行());
            payment.setSupplierCode(item.get仕入先コード());
            payment.setSupplierName1(item.get仕入先名１());
            payment.setSupplierName2(item.get仕入先名２());
            payment.setSupplierNameAbbr(item.get仕入先名略称());
            payment.setOfficeCode(item.get営業所コード());
            payment.setOfficeName(item.get営業所名());
            payment.setDepartmentCode(item.get部門コード());
            payment.setDepartmentName(item.get部門名());
            payment.setAreaCode(item.get地区コード());
            payment.setAreaName(item.get地区名());
            payment.setIndustryCode(item.get業種コード());
            payment.setIndustryName(item.get業種名());
            payment.setSupplierClass4Code(item.get仕入先分類４コード());
            payment.setSupplierClass4Name(item.get仕入先分類４名());
            payment.setSupplierClass5Code(item.get仕入先分類５コード());
            payment.setSupplierClass5Name(item.get仕入先分類５名());
            payment.setSupplierClass6Code(item.get仕入先分類６コード());
            payment.setSupplierClass6Name(item.get仕入先分類６名());
            payment.setSupplierClass7Code(item.get仕入先分類７コード());
            payment.setSupplierClass7Name(item.get仕入先分類７名());
            payment.setSupplierClass8Code(item.get仕入先分類８コード());
            payment.setSupplierClass8Name(item.get仕入先分類８名());
            payment.setSupplierClass9Code(item.get仕入先分類９コード());
            payment.setSupplierClass9Name(item.get仕入先分類９名());
            payment.setTransactionType(item.get取引区分());
            payment.setTransactionTypeName(item.get取引区分名());
            payment.setTransactionTypeAttribute(item.get取引区分属性());
            payment.setTransactionTypeAttributeName(item.get取引区分属性名());
            payment.setPaymentAmount(item.get支払額());
            payment.setSettlementDueDate(item.get決済予定日());
            payment.setNoteCode(item.get備考コード());
            payment.setNote(item.get備考());
            payment.setLoginId(item.getログインID());
            payment.setLoginName(item.getログイン名());
            payment.setOperationDate(item.get操作日付());
            payment.setDataOccurrenceType(item.getデータ発生区分());
            payment.setCounterProcessingSerialNumber(item.get相手処理連番());
            payment.setCheckmarkType(item.getチェックマーク区分());
            payment.setCheckmarkTypeName(item.getチェックマーク区分名());

            // 取込日を設定
            payment.setImportDate(LocalDate.now());

            return payment;
        } catch (Exception e) {
            log.error("データの変換中にエラーが発生しました: {}", e.getMessage());
            return null; // nullを返すとこの項目はスキップされる
        }
    }
}
