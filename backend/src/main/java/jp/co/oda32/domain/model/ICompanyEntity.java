package jp.co.oda32.domain.model;

import jp.co.oda32.domain.model.master.MCompany;

public interface ICompanyEntity extends IEntity {
    Integer getCompanyNo();
    MCompany getCompany();
}
