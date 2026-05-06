package jp.co.oda32.domain.model.finance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * MF API の英語 enum (financial_statement_type / category) を既存 mf_account_master の
 * 日本語値に翻訳するための辞書テーブル。
 */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "m_mf_enum_translation")
public class MMfEnumTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    /** FINANCIAL_STATEMENT / CATEGORY */
    @Column(name = "enum_kind", nullable = false, length = 50)
    private String enumKind;

    @Column(name = "english_code", nullable = false, length = 100)
    private String englishCode;

    @Column(name = "japanese_name", nullable = false, length = 255)
    private String japaneseName;

    @Column(name = "del_flg", nullable = false, length = 1)
    @Builder.Default
    private String delFlg = "0";

    @Column(name = "add_date_time", nullable = false)
    private Timestamp addDateTime;

    @Column(name = "add_user_no")
    private Integer addUserNo;

    @Column(name = "modify_date_time")
    private Timestamp modifyDateTime;

    @Column(name = "modify_user_no")
    private Integer modifyUserNo;

    @PrePersist
    void prePersist() {
        if (addDateTime == null) addDateTime = Timestamp.from(Instant.now());
    }

    @PreUpdate
    void preUpdate() {
        modifyDateTime = Timestamp.from(Instant.now());
    }
}
