package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.service.finance.MfPeriodConstants;

import java.util.List;

/**
 * MF 買掛金 期首残高仕訳 (opening balance journal) の判定/検出ロジックを集約する util。
 * <p>
 * 旧実装は {@link MfJournalFetcher#isPayableOpeningJournal} と
 * {@code MfOpeningBalanceService.findOpeningJournal} に同種の判定ロジックが分散しており、
 * sub_account_name の null 扱いが両側で非対称 (G-impl-7) だった。
 * 本クラスで base 述語 {@link #isOpeningCandidate} を 1 箇所に集約し、
 * 取込側 ({@link #findBest}) のみが branch 数 (= 補助科目登録 supplier 数) で best を選ぶ adapter として動作する。
 * <p>
 * <b>C8 (Codex 批判 Critical) 修正後の判定基準</b>:
 * 旧版は「credit に買掛金, debit に買掛金が無い」だけで判定 → 通常購入仕訳 (credit 買掛金/debit 仕入高) まで
 * opening と誤判定し、月次 accumulation から除外されていた。テスト側で workaround dummy debit を追加する
 * という形で本番欠陥を隠蔽していた。
 * <p>
 * 期首残高仕訳は MF 仕様上、会計年度開始 (= {@link MfPeriodConstants#MF_FISCAL_YEAR_START}) の
 * 仕訳 番号 1 として登録される。複合条件で厳密化:
 * <ol>
 *   <li>{@code number == 1} (MF 会計年度 1 件目)</li>
 *   <li>{@code transactionDate == MF_FISCAL_YEAR_START}</li>
 *   <li>credit 側に 買掛金 が出現し、debit 側に 買掛金 が一切無い (= 通常購入仕訳でない二重防御)</li>
 * </ol>
 * いずれか欠けると {@code false} (= 通常仕訳扱いで accumulation に含める)。
 * sub_account_name の null チェックは判定 base には含めない (持たない opening row は credit branch カウントから除外するだけ)。
 *
 * @since 2026-05-04 (SF-G08), C8 fix 2026-05-04
 */
public final class MfOpeningJournalDetector {

    private static final String MF_ACCOUNT_PAYABLE = "買掛金";

    private MfOpeningJournalDetector() {}

    /**
     * 買掛金 期首残高仕訳 (opening balance journal) かを判定する base 述語。
     * <p>判定: number==1 AND transactionDate==MF_FISCAL_YEAR_START AND
     * (全 branch の credit 側に 買掛金 が出現 AND debit 側に 買掛金 が一切無い)。
     * <p>
     * {@code m_supplier_opening_balance} へ取り込み済みのため、ledger / integrity / supplier-balances 側の
     * 月次 accumulation からは除外する (二重計上防止)。
     */
    public static boolean isOpeningCandidate(MfJournal j) {
        if (j == null || j.branches() == null || j.branches().isEmpty()) return false;

        // 必須 1: MF 会計年度 1 件目仕訳 (期首残高仕訳は仕様上 #1)
        if (j.number() == null || j.number() != 1) return false;

        // 必須 2: 取引日 == 会計年度開始日
        if (j.transactionDate() == null) return false;
        if (!j.transactionDate().equals(MfPeriodConstants.MF_FISCAL_YEAR_START)) return false;

        // 補助 (二重防御): credit 側に 買掛金 + debit 側に 買掛金 が一切無い
        boolean hasPayableCredit = false;
        for (MfJournal.MfBranch br : j.branches()) {
            MfJournal.MfSide cr = br.creditor();
            MfJournal.MfSide de = br.debitor();
            if (de != null && MF_ACCOUNT_PAYABLE.equals(de.accountName())) {
                return false; // debit 側に 買掛金 = 通常仕訳
            }
            if (cr != null && MF_ACCOUNT_PAYABLE.equals(cr.accountName())) {
                hasPayableCredit = true;
            }
        }
        return hasPayableCredit;
    }

    /**
     * 取り込み用: opening 候補の中から sub_account_name 付き credit branch 数が最大の journal を選ぶ。
     * <p>同 transactionDate に複数の opening 候補がある場合、より多くの supplier を持つ
     * (= 期首残高仕訳と推定される) journal を採用する。
     *
     * @return 最大 branch 数の opening journal、候補が無ければ null
     */
    public static MfJournal findBest(List<MfJournal> journals) {
        if (journals == null || journals.isEmpty()) return null;
        MfJournal best = null;
        int bestCount = 0;
        for (MfJournal j : journals) {
            if (!isOpeningCandidate(j)) continue;
            int payableCreditBranches = countPayableCreditWithSubAccount(j);
            if (payableCreditBranches > bestCount) {
                best = j;
                bestCount = payableCreditBranches;
            }
        }
        return best;
    }

    /** sub_account_name を持つ 買掛金 credit branch 数を返す (取込判定用)。 */
    private static int countPayableCreditWithSubAccount(MfJournal j) {
        if (j.branches() == null) return 0;
        int n = 0;
        for (MfJournal.MfBranch br : j.branches()) {
            MfJournal.MfSide cr = br.creditor();
            if (cr != null && MF_ACCOUNT_PAYABLE.equals(cr.accountName())
                    && cr.subAccountName() != null) {
                n++;
            }
        }
        return n;
    }
}
