package jp.co.oda32.batch.bcart;

import jp.co.oda32.constant.CompanyType;
import jp.co.oda32.constant.OfficeShopNo;
import jp.co.oda32.domain.model.bcart.BCartMember;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.service.bcart.BCartMemberService;
import jp.co.oda32.domain.service.master.MCompanyService;
import jp.co.oda32.domain.service.master.MPartnerService;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@AllArgsConstructor
@StepScope
@Log4j2
@Component
public class RegisterBCartMemberTasklet implements Tasklet {

    private final BCartMemberService bCartMemberService;
    private final MCompanyService mCompanyService;
    private final MPartnerService mPartnerService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

        List<BCartMember> nonPartneredMembers = bCartMemberService.fetchNonPartneredMembers();

        for (BCartMember nonPartneredMember : nonPartneredMembers) {
            MCompany mCompany = registerMCompany(nonPartneredMember);
            MPartner mPartner = registerMPartner(nonPartneredMember, mCompany);
            updateMCompany(mCompany, mPartner.getPartnerNo());
        }
        return RepeatStatus.FINISHED;
    }

    private MCompany registerMCompany(final BCartMember nonPartneredMember) throws Exception {
        MCompany mCompany = MCompany.builder()
                .shopNo(OfficeShopNo.B_CART_ORDER.getValue())
                .companyName(nonPartneredMember.getCompName())
                .companyType(CompanyType.PARTNER.getValue())
                .abbreviatedCompanyName(nonPartneredMember.getCompName())
                .build();
        try {
            return mCompanyService.insert(mCompany);
        } catch (Exception e) {
            log.error("MCompanyの登録に失敗しました: {}", e.getMessage());
            throw e;
        }
    }

    private MPartner registerMPartner(final BCartMember nonPartneredMember, MCompany mCompany) throws Exception {
        MPartner mPartner = MPartner.builder()
                .shopNo(OfficeShopNo.B_CART_ORDER.getValue())
                .partnerCode(nonPartneredMember.getExtId())
                .partnerName(nonPartneredMember.getCompName())
                .abbreviatedPartnerName(nonPartneredMember.getCompName())
                .isIncludeTaxDisplay(false)
                .companyNo(mCompany.getCompanyNo())
                .build();
        try {
            return mPartnerService.insert(mPartner);
        } catch (Exception e) {
            log.error("MPartnerの登録に失敗しました: {}", e.getMessage());
            throw e;
        }
    }

    private void updateMCompany(MCompany mCompany, int partnerNo) throws Exception {
        mCompany.setPartnerNo(partnerNo);
        try {
            mCompanyService.update(mCompany);
        } catch (Exception e) {
            log.error("MCompanyの更新に失敗しました: {}", e.getMessage());
            throw e;
        }
    }
}
