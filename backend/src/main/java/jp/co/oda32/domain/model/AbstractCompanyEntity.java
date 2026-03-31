package jp.co.oda32.domain.model;

import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.model.master.MPartner;

public abstract class AbstractCompanyEntity implements ICompanyEntity {
    @Override
    public Integer getShopNo() {
        MCompany mCompany = getCompany();
        if (mCompany == null) {
            return null;
        }
        MPartner partner = mCompany.getPartner();
        if (partner != null) {
            return partner.getShopNo();
        }
        return mCompany.getShopNo();
    }
}
