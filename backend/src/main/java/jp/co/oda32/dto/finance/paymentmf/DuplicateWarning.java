package jp.co.oda32.dto.finance.paymentmf;

import java.time.OffsetDateTime;

/**
 * P1-08 L1: preview 時、同一 SHA-256 ハッシュの Excel が過去に取込済の場合に返される警告情報。
 *
 * <p>業務的意味: 「内容が完全に同じ Excel が以前取り込まれている」=
 * 修正版の意図的取込でなければ重複取込の可能性が高い。
 */
public record DuplicateWarning(
        OffsetDateTime previousUploadedAt,
        String previousFilename,
        Integer previousUploadedByUserNo
) {}
