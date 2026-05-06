package jp.co.oda32.dto.finance.paymentmf;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * P1-08 L2: preview 時、同 (shop, transferDate) で過去に applyVerification (確定処理)
 * が実行済の場合に返される警告情報。
 *
 * <p>業務的意味: 「この月は既に手動確定済 = verified_manually=true 行が存在する」=
 * 再確定すると確定値を上書きする (ただし verified_manually=true 行は保護対象、Q3-(ii))。
 */
public record AppliedWarning(
        OffsetDateTime appliedAt,
        Integer appliedByUserNo,
        LocalDate transactionMonth,
        LocalDate transferDate
) {}
