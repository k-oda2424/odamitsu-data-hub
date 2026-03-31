package jp.co.oda32.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * アクセスログEntity
 *
 * @author k_oda
 * @since 2017/11/06
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_access_log")
public class TAccessLog {

    @Id
    @Column(name = "access_id")
    @SequenceGenerator(name = "t_access_log_access_id_seq_gen", sequenceName = "t_access_log_access_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "t_access_log_access_id_seq_gen")
    private Integer accessId;
    @Column(name = "access_time")
    private LocalDateTime accessTime;
    @Column(name = "login_id")
    private String loginId;
    @Column(name = "ip_address")
    private String ipAddress;
    @Column(name = "uri")
    private String uri;

    @Column(name = "key1")
    @Size(max = 100)
    private String key1;

    @Column(name = "value1")
    @Size(max = 1000)
    private String value1;

    @Column(name = "key2")
    @Size(max = 100)
    private String key2;

    @Column(name = "value2")
    @Size(max = 1000)
    private String value2;

    @Column(name = "key3")
    @Size(max = 100)
    private String key3;

    @Column(name = "value3")
    @Size(max = 1000)
    private String value3;

    @Column(name = "key4")
    @Size(max = 100)
    private String key4;

    @Column(name = "value4")
    @Size(max = 1000)
    private String value4;

    @Column(name = "key5")
    @Size(max = 100)
    private String key5;

    @Column(name = "value5")
    @Size(max = 1000)
    private String value5;

    @Column(name = "key6")
    @Size(max = 100)
    private String key6;

    @Column(name = "value6")
    @Size(max = 1000)
    private String value6;

    @Column(name = "key7")
    @Size(max = 100)
    private String key7;

    @Column(name = "value7")
    @Size(max = 1000)
    private String value7;

    @Column(name = "key8")
    @Size(max = 100)
    private String key8;

    @Column(name = "value8")
    @Size(max = 1000)
    private String value8;

    @Column(name = "key9")
    @Size(max = 100)
    private String key9;

    @Column(name = "value9")
    @Size(max = 1000)
    private String value9;

    @Column(name = "key10")
    @Size(max = 100)
    private String key10;

    @Column(name = "value10")
    @Size(max = 1000)
    private String value10;

    @Column(name = "key_other")
    @Size(max = 1000)
    private String keyOther;

    @Column(name = "value_other")
    @Size(max = 4000)
    private String valueOther;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "method")
    private String method;
}
