package jp.co.oda32.domain.model.master;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "m_asana")
public class MAsana {

    @Id
    @Column(name = "user_id")
    private BigDecimal userId;

    @Column(name = "user_name")
    private String userName;
}
