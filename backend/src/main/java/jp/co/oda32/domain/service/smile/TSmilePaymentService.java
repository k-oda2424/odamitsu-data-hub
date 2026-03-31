package jp.co.oda32.domain.service.smile;

import jp.co.oda32.domain.model.smile.TSmilePayment;
import jp.co.oda32.domain.model.smile.WSmilePayment;
import jp.co.oda32.domain.repository.smile.TSmilePaymentRepository;
import jp.co.oda32.domain.repository.smile.WSmilePaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SMILE支払情報テーブルのサービスクラス
 *
 * @author ai_assistant
 * @since 2025/05/02
 */
@Service
@Transactional
public class TSmilePaymentService {

    private final TSmilePaymentRepository tSmilePaymentRepository;
    private final WSmilePaymentRepository wSmilePaymentRepository;

    @Autowired
    public TSmilePaymentService(TSmilePaymentRepository tSmilePaymentRepository,
                                WSmilePaymentRepository wSmilePaymentRepository) {
        this.tSmilePaymentRepository = tSmilePaymentRepository;
        this.wSmilePaymentRepository = wSmilePaymentRepository;
    }

    /**
     * ワークテーブルをトランケートします
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void truncateWorkTable() {
        System.out.println("ワークテーブルのトランケートを開始します...");
        wSmilePaymentRepository.truncateTable();
        System.out.println("ワークテーブルのトランケートが完了しました");
    }

    /**
     * ワークテーブルにデータを一括保存します
     *
     * @param payments 保存するデータのリスト
     * @return 保存されたデータのリスト
     */
    @Transactional
    public List<WSmilePayment> saveAllToWorkTable(List<WSmilePayment> payments) {
        return wSmilePaymentRepository.saveAll(payments);
    }

    /**
     * 指定された伝票日付のデータをワークテーブルから本番テーブルに同期します
     *
     * @param voucherDate 伝票日付
     */
    @Transactional
    public void synchronizePaymentData(LocalDate voucherDate) {
        // ワークテーブルから指定伝票日付のデータを取得
        List<WSmilePayment> workPayments = wSmilePaymentRepository.findByVoucherDate(voucherDate);

        // ワークテーブルの処理連番セットを取得
        Set<Long> workProcessingSerialNumbers = wSmilePaymentRepository.findDistinctProcessingSerialNumbersByVoucherDate(voucherDate);

        // ワークテーブルのデータを本番テーブルに保存（UPSERT）
        if (!workPayments.isEmpty()) {
            List<TSmilePayment> tSmilePayments = workPayments.stream()
                    .map(WSmilePayment::toTSmilePayment)
                    .collect(Collectors.toList());

            tSmilePaymentRepository.saveAll(tSmilePayments);
        }

        // ワークテーブルに存在しないSMILEで削除された処理連番のデータを削除
        if (!workProcessingSerialNumbers.isEmpty()) {
            tSmilePaymentRepository.deleteByVoucherDateAndProcessingSerialNumberNotIn(voucherDate, workProcessingSerialNumbers);
        }
    }

    /**
     * 全てのSMILE支払情報を取得します
     *
     * @return 全てのTSmilePaymentのリスト
     */
    public List<TSmilePayment> findAll() {
        return tSmilePaymentRepository.findAll();
    }

    /**
     * 指定されたIDのSMILE支払情報を取得します
     *
     * @param id 複合キー
     * @return TSmilePaymentオブジェクト（存在しない場合はnull）
     */
    public TSmilePayment findById(TSmilePayment.TSmilePaymentId id) {
        return tSmilePaymentRepository.findById(id).orElse(null);
    }

    /**
     * SMILE支払情報を保存します
     *
     * @param payment 保存するTSmilePayment
     * @return 保存されたTSmilePayment
     */
    @Transactional
    public TSmilePayment save(TSmilePayment payment) {
        return tSmilePaymentRepository.save(payment);
    }

    /**
     * 複数のSMILE支払情報を保存します
     *
     * @param payments 保存するTSmilePaymentのリスト
     * @return 保存されたTSmilePaymentのリスト
     */
    @Transactional
    public List<TSmilePayment> saveAll(List<TSmilePayment> payments) {
        return tSmilePaymentRepository.saveAll(payments);
    }

    /**
     * 伝票日付で絞り込んだSMILE支払情報を取得します
     *
     * @param voucherDate 伝票日付
     * @return 指定された伝票日付に対応するTSmilePaymentのリスト
     */
    public List<TSmilePayment> findByVoucherDate(LocalDate voucherDate) {
        return tSmilePaymentRepository.findByVoucherDate(voucherDate);
    }

    /**
     * 年月度で絞り込んだSMILE支払情報を取得します
     *
     * @param yearMonth 年月度
     * @return 指定された年月度に対応するTSmilePaymentのリスト
     */
    public List<TSmilePayment> findByYearMonth(String yearMonth) {
        return tSmilePaymentRepository.findByYearMonth(yearMonth);
    }

    /**
     * 仕入先コードで絞り込んだSMILE支払情報を取得します
     *
     * @param supplierCode 仕入先コード
     * @return 指定された仕入先コードに対応するTSmilePaymentのリスト
     */
    public List<TSmilePayment> findBySupplierCode(String supplierCode) {
        return tSmilePaymentRepository.findBySupplierCode(supplierCode);
    }

    /**
     * SMILE支払情報を削除します
     *
     * @param payment 削除するTSmilePayment
     */
    @Transactional
    public void delete(TSmilePayment payment) {
        tSmilePaymentRepository.delete(payment);
    }

    /**
     * 複数のSMILE支払情報を削除します
     *
     * @param payments 削除するTSmilePaymentのリスト
     */
    @Transactional
    public void deleteAll(List<TSmilePayment> payments) {
        tSmilePaymentRepository.deleteAll(payments);
    }
}
