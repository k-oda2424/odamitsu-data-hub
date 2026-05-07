package jp.co.oda32.domain.service.finance;

import jakarta.persistence.EntityManager;

import java.time.LocalDate;

/**
 * G2-M2 fix (Codex Major #2, 2026-05-06): 買掛金の verified_amount / verification_source 書込経路
 * (BULK / MANUAL / MF_OVERRIDE) を、(shop_no, transaction_month) 単位で直列化するための共通 util。
 *
 * <p>従来 {@code PaymentMfImportService.applyVerification} のみが advisory lock を取っていたが、
 * <ul>
 *   <li>{@link TAccountsPayableSummaryService#verify} (UI 手入力 → MANUAL_VERIFICATION)</li>
 *   <li>{@link ConsistencyReviewService#applyMfOverride} (整合性レポート → MF_OVERRIDE)</li>
 * </ul>
 * は lock 不在で last-write-wins の race を起こし得た。
 * 例: applyVerification (BULK) → applyMfOverride (MF_OVERRIDE) → applyVerification (BULK 再 upload)
 * の並列実行で source 列が再度 BULK で上書きされ、整合性レビューの副作用が消失する等。
 *
 * <p>本 util を 3 経路すべての先頭で呼ぶことで、PostgreSQL {@code pg_advisory_xact_lock} により
 * (shop_no, transaction_month) 単位で書込操作を直列化する。{@code pg_advisory_xact_lock} は
 * トランザクション境界で自動解放されるため、解放漏れリスクは無い。
 *
 * <p>キー設計:
 * <pre>{@code
 * lockKey = ((long) shopNo << 32) | (transactionMonth.toEpochDay() & 0xFFFF_FFFFL)
 * }</pre>
 * shopNo は将来マルチテナント化時の競合回避マージン。{@code toEpochDay()} は 1970-01-01 起点で
 * 約 1970-2061 年範囲なら 21 bit 程度なので、下位 32 bit には十分収まる。
 */
public final class FinancePayableLock {

    private FinancePayableLock() {}

    /**
     * (shopNo, transactionMonth) ペアから 64bit advisory lock key を計算する。
     * 同一ペアなら同一 key。同一 transactionMonth でも shopNo が違えば別 key となり、
     * 第1事業部 (shop=1) と第2事業部 (将来) の処理は独立に並走できる。
     */
    public static long computePayableLockKey(int shopNo, LocalDate transactionMonth) {
        if (transactionMonth == null) {
            throw new IllegalArgumentException("transactionMonth must not be null");
        }
        return ((long) shopNo << 32) | (transactionMonth.toEpochDay() & 0xFFFF_FFFFL);
    }

    /**
     * (shopNo, transactionMonth) 単位の advisory lock を取得する。
     * 呼び出し元の {@link org.springframework.transaction.annotation.Transactional @Transactional} 境界で
     * 自動解放されるため、解放呼び出しは不要。
     *
     * <p>同一 key を取った別 tx は先行 tx の commit/rollback 完了まで待たされる。
     * これにより BULK / MANUAL / MF_OVERRIDE の 3 書込経路が 1 度に 1 経路ずつしか走らない。
     */
    public static void acquire(EntityManager entityManager, int shopNo, LocalDate transactionMonth) {
        long lockKey = computePayableLockKey(shopNo, transactionMonth);
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:k)")
                .setParameter("k", lockKey)
                .getSingleResult();
    }
}
