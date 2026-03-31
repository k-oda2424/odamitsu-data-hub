package jp.co.oda32.domain.model;

import java.io.Serializable;
import java.sql.Timestamp;

public interface IEntity extends Serializable {
    void setAddUserNo(Integer loginUserNo);
    void setAddDateTime(Timestamp addDateTime);
    void setModifyUserNo(Integer loginUserNo);
    void setModifyDateTime(Timestamp modifyDateTime);
    void setDelFlg(String delFlg);
    String getDelFlg();
    Integer getShopNo();
}
