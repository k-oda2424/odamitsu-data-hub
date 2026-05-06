package jp.co.oda32.domain.service.finance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;

/**
 * G2-M10 (V040, 2026-05-06): {@code applyVerification} の手動確定保護判定テスト。
 *
 * <p>旧実装は {@code verification_note} 接頭辞文字列 ({@code "振込明細検証 "}) で BULK/MANUAL を推定し、
 * ユーザが偶然この接頭辞で始まる note を手入力すると保護が外れる偽判定リスクがあった。
 * 新実装は {@code verification_source} 列で経路を明示判定する。
 */
class PaymentMfImportServiceManualLockTest {

    @Test
    void MANUAL_VERIFICATION由来は保護される() {
        List<TAccountsPayableSummary> group = List.of(
            buildRow(true, FinanceConstants.VERIFICATION_SOURCE_MANUAL, "ユーザ手入力"),
            buildRow(true, FinanceConstants.VERIFICATION_SOURCE_MANUAL, null)
        );

        boolean locked = PaymentMfImportService.isAnyManuallyLocked(group);

        assertThat(locked).isTrue();
    }

    @Test
    void BULK_VERIFICATION由来は保護されない_再upload上書きOK() {
        // applyVerification 由来は再 upload で上書きOK
        List<TAccountsPayableSummary> group = List.of(
            buildRow(true, FinanceConstants.VERIFICATION_SOURCE_BULK, "振込明細検証 file.xlsx 2026-04-25"),
            buildRow(true, FinanceConstants.VERIFICATION_SOURCE_BULK, "振込明細検証 file.xlsx 2026-04-25")
        );

        boolean locked = PaymentMfImportService.isAnyManuallyLocked(group);

        assertThat(locked).isFalse();
    }

    @Test
    void MF_OVERRIDE由来は保護されない_再upload上書きOK() {
        List<TAccountsPayableSummary> group = List.of(
            buildRow(true, FinanceConstants.VERIFICATION_SOURCE_MF_OVERRIDE, "整合性レポート MF_APPLY")
        );

        boolean locked = PaymentMfImportService.isAnyManuallyLocked(group);

        assertThat(locked).isFalse();
    }

    @Test
    void 税率別1行でもMANUALなら全体保護() {
        // 8% 行は BULK、10% 行は MANUAL の混在 → MANUAL あり → 保護
        List<TAccountsPayableSummary> group = List.of(
            buildRow(true, FinanceConstants.VERIFICATION_SOURCE_BULK, "振込明細検証 ..."),
            buildRow(true, FinanceConstants.VERIFICATION_SOURCE_MANUAL, "8% だけ手修正")
        );

        boolean locked = PaymentMfImportService.isAnyManuallyLocked(group);

        assertThat(locked).isTrue();
    }

    /**
     * G2-M10 ケース: BULK 接頭辞に偽装した note を手入力された MANUAL 行。
     * 旧実装は {@code note.startsWith("振込明細検証 ")} で BULK と誤判定し保護が外れていた。
     * 新実装は source 列が MANUAL のため正しく保護される。
     */
    @Test
    void BULK接頭辞に偽装したMANUAL_noteでも保護される() {
        List<TAccountsPayableSummary> group = List.of(
            buildRow(true, FinanceConstants.VERIFICATION_SOURCE_MANUAL,
                     "振込明細検証 偽装ファイル.xlsx 2026-04-25") // ← 偶然 BULK 接頭辞
        );

        boolean locked = PaymentMfImportService.isAnyManuallyLocked(group);

        // 旧実装: note 接頭辞 BULK と誤判定 → false (保護外れ)
        // 新実装: source=MANUAL → true (正しく保護)
        assertThat(locked).isTrue();
    }

    @Test
    void verifiedManually_false行は判定対象外() {
        // verified_manually=false の行は MANUAL でも判定対象外 (release 後など)
        List<TAccountsPayableSummary> group = List.of(
            buildRow(false, FinanceConstants.VERIFICATION_SOURCE_MANUAL, "解除済"),
            buildRow(false, null, null)
        );

        boolean locked = PaymentMfImportService.isAnyManuallyLocked(group);

        assertThat(locked).isFalse();
    }

    @Test
    void source_NULLは保護されない() {
        // V040 backfill 漏れ等で source=NULL かつ verified_manually=true の行は保護されない
        // (legacy 動作との後方互換は AccountsPayableResponse で別途吸収)
        List<TAccountsPayableSummary> group = List.of(
            buildRow(true, null, "古いデータ")
        );

        boolean locked = PaymentMfImportService.isAnyManuallyLocked(group);

        assertThat(locked).isFalse();
    }

    private TAccountsPayableSummary buildRow(boolean verifiedManually, String source, String note) {
        TAccountsPayableSummary s = new TAccountsPayableSummary();
        s.setVerifiedManually(verifiedManually);
        s.setVerificationSource(source);
        s.setVerificationNote(note);
        return s;
    }
}
