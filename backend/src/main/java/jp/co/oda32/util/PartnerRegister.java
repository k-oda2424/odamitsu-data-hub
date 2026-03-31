package jp.co.oda32.util;

import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.service.master.MCompanyService;
import jp.co.oda32.domain.service.master.MPartnerService;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

/**
 * 得意先を登録するクラス
 *
 * @author k_oda
 * @since 2024/09/01
 */
@AllArgsConstructor
@StepScope
@Log4j2
@Component
public class PartnerRegister {
    private final MCompanyService mCompanyService;
    private final MPartnerService mPartnerService;

    public MPartner register(MPartner newPartner, MCompany newCompany) throws Exception {
        MCompany mCompany = registerMCompany(newCompany);
        MPartner mPartner = registerMPartner(newPartner, mCompany);
        mCompany = updateMCompany(mCompany, mPartner.getPartnerNo());
        mPartner.setMCompany(mCompany);
        return mPartner;
    }

    private MCompany registerMCompany(MCompany newCompany) throws Exception {
        try {
            return mCompanyService.insert(newCompany);
        } catch (Exception e) {
            log.error("MCompanyの登録に失敗しました: {}", e.getMessage());
            throw e;
        }
    }

    private MPartner registerMPartner(MPartner newPartner, MCompany mCompany) throws Exception {
        newPartner.setCompanyNo(mCompany.getCompanyNo());
        try {
            return mPartnerService.insert(newPartner);
        } catch (Exception e) {
            log.error("MPartnerの登録に失敗しました: {}", e.getMessage());
            throw e;
        }
    }

    private MCompany updateMCompany(MCompany mCompany, int partnerNo) throws Exception {
        mCompany.setPartnerNo(partnerNo);
        try {
            return mCompanyService.update(mCompany);
        } catch (Exception e) {
            log.error("MCompanyの更新に失敗しました: {}", e.getMessage());
            throw e;
        }
    }
}
