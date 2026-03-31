package jp.co.oda32.batch.smile;

import jp.co.oda32.constant.CompanyType;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.model.master.WSmilePartner;
import jp.co.oda32.domain.service.master.MCompanyService;
import jp.co.oda32.domain.service.master.MPartnerService;
import jp.co.oda32.domain.service.master.WSmilePartnerService;
import jp.co.oda32.util.PartnerRegister;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Smile得意先ワークテーブルを使用して
 * 本システムに得意先データを登録、更新するタスクレットクラス
 *
 * @author
 * @since 2024/05/08
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class PartnerSaveTasklet implements Tasklet {

    private final WSmilePartnerService wSmilePartnerService;
    private final MPartnerService mPartnerService;
    private final MCompanyService mCompanyService;
    private final PartnerRegister partnerRegister;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.info("SMILE得意先登録・更新処理開始");
        processPartners();
        log.info("SMILE得意先登録・更新処理終了");
        return RepeatStatus.FINISHED;
    }

    @Transactional
    public void processPartners() {
        // 修正点: findAll() を使用してすべてのレコードを取得
        List<WSmilePartner> partnersToProcess = wSmilePartnerService.findAll();
        log.info("処理対象の得意先数: {}", partnersToProcess.size());

        for (WSmilePartner wsp : partnersToProcess) {
            try {
                MPartner existingPartner = mPartnerService.getByUniqueKey(wsp.getShopNo(), wsp.get得意先コード());

                String wspPartnerName = (wsp.get得意先名1() != null ? wsp.get得意先名1() : "")
                        + (wsp.get得意先名2() != null ? wsp.get得意先名2() : "");
                String wspAbbreviatedName = wsp.get得意先名略称() != null ? wsp.get得意先名略称() : "";

                if (existingPartner != null) {
                    // 差分チェック
                    boolean needsUpdate = false;

                    if (equalsStrings(existingPartner.getPartnerName(), wspPartnerName)) {
                        existingPartner.setPartnerName(wspPartnerName);
                        needsUpdate = true;
                    }

                    if (equalsStrings(existingPartner.getAbbreviatedPartnerName(), wspAbbreviatedName)) {
                        existingPartner.setAbbreviatedPartnerName(wspAbbreviatedName);
                        needsUpdate = true;
                    }

                    // 他のフィールドの差分チェックと更新が必要であればここに追加

                    if (needsUpdate) {
                        MPartner updatedPartner = mPartnerService.update(existingPartner);
                        log.info("更新済みパートナー: {}", updatedPartner.getPartnerNo());

                        MCompany associatedCompany = mCompanyService.getByCompanyNo(updatedPartner.getCompanyNo());
                        if (associatedCompany != null) {
                            associatedCompany.setCompanyName(updatedPartner.getPartnerName());
                            associatedCompany.setAbbreviatedCompanyName(updatedPartner.getAbbreviatedPartnerName());
                            // 他の必要なフィールドもここで設定

                            mCompanyService.update(associatedCompany);
                            log.info("更新済み会社: {}", associatedCompany.getCompanyNo());
                        }
                    } else {
                        log.info("得意先コード {} は更新不要です", wsp.get得意先コード());
                    }

                } else {
                    // 新規パートナーの登録
                    MPartner newPartner = MPartner.builder()
                            .partnerCode(wsp.get得意先コード())
                            .partnerName(wspPartnerName)
                            .abbreviatedPartnerName(wspAbbreviatedName)
                            .shopNo(wsp.getShopNo())
                            .build();
                    // 必要な他のフィールドも設定
                    newPartner.setDelFlg("0");

                    MCompany newCompany = MCompany.builder()
                            .companyName(newPartner.getPartnerName())
                            .abbreviatedCompanyName(newPartner.getAbbreviatedPartnerName())
                            .shopNo(wsp.getShopNo())
                            .companyType(CompanyType.PARTNER.getValue())
                            .build();
                    // PartnerRegisterを使用して登録
                    MPartner registeredPartner = partnerRegister.register(newPartner, newCompany);
                    log.info("新規登録済みパートナー: {}", registeredPartner.getPartnerNo());
                }
            } catch (Exception e) {
                log.error("得意先コード {} の処理中にエラーが発生しました: {}", wsp.get得意先コード(), e.getMessage(), e);
                // エラー時の処理
                // エラーを記録した上で処理を継続
            }
        }
    }

    /**
     * 文字列の比較を行います。null セーフです。
     */
    private boolean equalsStrings(String str1, String str2) {
        return !(str1 == null ? "" : str1).equals(str2 == null ? "" : str2);
    }
}
