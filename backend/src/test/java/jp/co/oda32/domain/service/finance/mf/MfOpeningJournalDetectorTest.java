package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.service.finance.MfPeriodConstants;
import jp.co.oda32.domain.service.finance.mf.MfJournal.MfBranch;
import jp.co.oda32.domain.service.finance.mf.MfJournal.MfSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MfOpeningJournalDetector} の単体テスト。
 * <p>
 * C8 (Codex 批判 Critical) 修正で判定基準を厳格化:
 * <ol>
 *   <li>{@code number == 1} (MF 会計年度 1 件目)</li>
 *   <li>{@code transactionDate == MF_FISCAL_YEAR_START}</li>
 *   <li>credit に買掛金 / debit に買掛金が無い (二重防御)</li>
 * </ol>
 * いずれか欠ければ false (= 通常仕訳扱いで accumulation に含める)。
 *
 * @since 2026-05-04 (C8 fix)
 */
class MfOpeningJournalDetectorTest {

    private static final LocalDate FISCAL_START = MfPeriodConstants.MF_FISCAL_YEAR_START;

    @Test
    @DisplayName("number=1 + transactionDate=fiscal_start + credit-only → true")
    void opening_journal_full_match_returns_true() {
        MfJournal j = build(1, FISCAL_START,
                creditOnlyPayableBranch("仕入先A_sub", new BigDecimal("100000")));
        assertThat(MfOpeningJournalDetector.isOpeningCandidate(j)).isTrue();
    }

    @Test
    @DisplayName("number=2 (= 通常購入仕訳) → false")
    void number_not_one_returns_false() {
        MfJournal j = build(2, FISCAL_START,
                creditOnlyPayableBranch("仕入先A_sub", new BigDecimal("100000")));
        assertThat(MfOpeningJournalDetector.isOpeningCandidate(j)).isFalse();
    }

    @Test
    @DisplayName("number=null (安全側) → false")
    void number_null_returns_false() {
        MfJournal j = build(null, FISCAL_START,
                creditOnlyPayableBranch("仕入先A_sub", new BigDecimal("100000")));
        assertThat(MfOpeningJournalDetector.isOpeningCandidate(j)).isFalse();
    }

    @Test
    @DisplayName("transactionDate != fiscal_year_start → false")
    void transaction_date_mismatch_returns_false() {
        MfJournal j = build(1, FISCAL_START.plusDays(1),
                creditOnlyPayableBranch("仕入先A_sub", new BigDecimal("100000")));
        assertThat(MfOpeningJournalDetector.isOpeningCandidate(j)).isFalse();
    }

    @Test
    @DisplayName("transactionDate=null → false")
    void transaction_date_null_returns_false() {
        MfJournal j = build(1, null,
                creditOnlyPayableBranch("仕入先A_sub", new BigDecimal("100000")));
        assertThat(MfOpeningJournalDetector.isOpeningCandidate(j)).isFalse();
    }

    @Test
    @DisplayName("number=1 + fiscal_start でも debit 側に買掛金が混在 → false (二重防御)")
    void debit_payable_present_returns_false() {
        MfBranch credit = creditOnlyPayableBranch("仕入先A_sub", new BigDecimal("100000"));
        MfBranch debitPayable = new MfBranch(
                new MfSide(null, "普通預金", null, null, null, "対象外", null, new BigDecimal("50000")),
                new MfSide(null, "買掛金", null, "仕入先A_sub", null, "対象外", null, new BigDecimal("50000")),
                "payment"
        );
        MfJournal j = build(1, FISCAL_START, credit, debitPayable);
        assertThat(MfOpeningJournalDetector.isOpeningCandidate(j)).isFalse();
    }

    @Test
    @DisplayName("credit 側に買掛金が一切無い (例: 売掛金のみの opening) → false")
    void credit_payable_absent_returns_false() {
        MfBranch other = new MfBranch(
                new MfSide(null, "売掛金", null, "得意先A_sub", null, "対象外", null, new BigDecimal("100000")),
                new MfSide(null, "繰越利益剰余金", null, null, null, "対象外", null, new BigDecimal("100000")),
                "ar opening"
        );
        MfJournal j = build(1, FISCAL_START, other);
        assertThat(MfOpeningJournalDetector.isOpeningCandidate(j)).isFalse();
    }

    @Test
    @DisplayName("journal=null → false (NPE 回避)")
    void null_journal_returns_false() {
        assertThat(MfOpeningJournalDetector.isOpeningCandidate(null)).isFalse();
    }

    @Test
    @DisplayName("branches=null/empty → false")
    void empty_branches_returns_false() {
        MfJournal jNull = new MfJournal("j", FISCAL_START, 1, true, "opening", "", null);
        MfJournal jEmpty = new MfJournal("j", FISCAL_START, 1, true, "opening", "", List.of());
        assertThat(MfOpeningJournalDetector.isOpeningCandidate(jNull)).isFalse();
        assertThat(MfOpeningJournalDetector.isOpeningCandidate(jEmpty)).isFalse();
    }

    @Test
    @DisplayName("findBest: 候補なし → null、候補ありなら最大 sub_account 数の journal")
    void find_best_picks_max_sub_account_count() {
        MfJournal small = build(1, FISCAL_START,
                creditOnlyPayableBranch("仕入先A_sub", new BigDecimal("100000")));
        MfJournal big = build(1, FISCAL_START,
                creditOnlyPayableBranch("仕入先B_sub", new BigDecimal("200000")),
                creditOnlyPayableBranch("仕入先C_sub", new BigDecimal("300000")));
        MfJournal regular = build(2, FISCAL_START,
                creditOnlyPayableBranch("仕入先D_sub", new BigDecimal("999999")));
        // big は credit 買掛金 sub_account 2 個 → 採用
        MfJournal best = MfOpeningJournalDetector.findBest(List.of(small, big, regular));
        assertThat(best).isSameAs(big);

        assertThat(MfOpeningJournalDetector.findBest(List.of(regular))).isNull();
        assertThat(MfOpeningJournalDetector.findBest(List.of())).isNull();
        assertThat(MfOpeningJournalDetector.findBest(null)).isNull();
    }

    // ------- helpers -------

    private static MfJournal build(Integer number, LocalDate transactionDate, MfBranch... branches) {
        return new MfJournal(
                "j-test", transactionDate, number, true, "opening", "期首残",
                List.of(branches));
    }

    /** 期首残候補 branch: credit 買掛金 / debit 繰越利益剰余金 (sub_account 付与)。 */
    private static MfBranch creditOnlyPayableBranch(String subAccountName, BigDecimal value) {
        return new MfBranch(
                new MfSide(null, "買掛金", null, subAccountName, null, "対象外", null, value),
                new MfSide(null, "繰越利益剰余金", null, null, null, "対象外", null, value),
                "opening"
        );
    }
}
