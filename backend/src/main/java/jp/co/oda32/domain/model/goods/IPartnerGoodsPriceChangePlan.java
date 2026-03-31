package jp.co.oda32.domain.model.goods;

import jp.co.oda32.domain.model.IEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @author k_oda
 * @since 2022/12/30
 */
public interface IPartnerGoodsPriceChangePlan extends IEntity {
    Integer getPartnerGoodsPriceChangePlanNo();


    void setPartnerGoodsPriceChangePlanNo(Integer partnerGoodsPriceChangePlanNo);


    Integer getShopNo();


    void setShopNo(Integer shopNo);


    String getChangeReason();


    void setChangeReason(String changeReason);


    Integer getCompanyNo();


    void setCompanyNo(Integer companyNo);


    String getPartnerCode();


    void setPartnerCode(String partnerCode);


    Integer getPartnerNo();


    void setPartnerNo(Integer partnerNo);


    Integer getDestinationNo();


    void setDestinationNo(Integer destinationNo);


    String getGoodsCode();


    void setGoodsCode(String goodsCode);


    String getJanCode();


    void setJanCode(String janCode);


    BigDecimal getBeforePrice();


    void setBeforePrice(BigDecimal beforePrice);


    BigDecimal getAfterPrice();


    void setAfterPrice(BigDecimal afterPrice);


    LocalDate getChangePlanDate();


    void setChangePlanDate(LocalDate changePlanDate);


    String getGoodsName();


    void setGoodsName(String goodsName);


    BigDecimal getBeforePurchasePrice();


    void setBeforePurchasePrice(BigDecimal beforePurchasePrice);


    BigDecimal getAfterPurchasePrice();


    void setAfterPurchasePrice(BigDecimal afterPurchasePrice);


    java.sql.Timestamp getAddDateTime();


    void setAddDateTime(java.sql.Timestamp addDateTime);


    Integer getAddUserNo();


    void setAddUserNo(Integer addUserNo);


    java.sql.Timestamp getModifyDateTime();


    void setModifyDateTime(java.sql.Timestamp modifyDateTime);


    Integer getModifyUserNo();


    void setModifyUserNo(Integer modifyUserNo);


    String getDelFlg();


    void setDelFlg(String delFlg);


    boolean isEstimateCreated();


    void setEstimateCreated(boolean estimateCreated);


    Integer getGoodsNo();


    void setGoodsNo(Integer goodsNo);


    boolean isPartnerPriceReflect();


    void setPartnerPriceReflect(boolean partnerPriceReflect);


    String getNote();


    void setNote(String note);


    boolean isDeficitFlg();


    void setDeficitFlg(boolean deficitFlg);


    BigDecimal getChangeContainNum();


    void setChangeContainNum(BigDecimal changeContainNum);


    Integer getEstimateNo();


    void setEstimateNo(Integer estimateNo);


    Integer getEstimateDetailNo();


    void setEstimateDetailNo(Integer estimateDetailNo);


    Integer getParentChangePlanNo();


    void setParentChangePlanNo(Integer parentChangePlanNo);

}
