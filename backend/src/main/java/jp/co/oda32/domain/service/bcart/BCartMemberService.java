package jp.co.oda32.domain.service.bcart;

/**
 * B-Cartの会員情報のサービスクラス
 *
 * @author k_oda
 * @since 2023/06/09
 */

import jp.co.oda32.domain.model.bcart.BCartMember;
import jp.co.oda32.domain.repository.bcart.BCartMemberRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Log4j2
public class BCartMemberService {

    private final BCartMemberRepository bCartMemberRepository;

    @Autowired
    public BCartMemberService(BCartMemberRepository bCartMemberRepository) {
        this.bCartMemberRepository = bCartMemberRepository;
    }

    public BCartMember getByBCartCustomerId(Long bCartCustomerId) {
        return this.bCartMemberRepository.findById(bCartCustomerId).orElseThrow();
    }

    public List<BCartMember> findBySmilePartnerMasterNotLinked() {
        return this.bCartMemberRepository.findBySmilePartnerMasterLinkedFalse();
    }

    public void updateMembers(List<BCartMember> members) {
        for (BCartMember member : members) {
            BCartMember existingMember = bCartMemberRepository.findById(member.getId()).orElse(null);
            if (existingMember != null) {
                // Update existing member
                // APIから取得したextIdが空の場合、既存のextIdを保持
                if (member.getExtId() == null || member.getExtId().isEmpty()) {
                    if (existingMember.getExtId() != null && !existingMember.getExtId().isEmpty()) {
                        log.info("会員ID: {} - APIからextIdが取得できませんでしたが、既存のextId({})を保持します",
                                member.getId(), existingMember.getExtId());
                        member.setExtId(existingMember.getExtId());
                    }
                } else {
                    // APIから取得したextIdが有効な場合、ログ出力
                    if (existingMember.getExtId() == null || existingMember.getExtId().isEmpty()) {
                        log.info("会員ID: {} - extIdを新規設定します: {}", member.getId(), member.getExtId());
                    } else if (!member.getExtId().equals(existingMember.getExtId())) {
                        log.info("会員ID: {} - extIdを更新します: {} -> {}",
                                member.getId(), existingMember.getExtId(), member.getExtId());
                    }
                }
                bCartMemberRepository.save(member);
            } else {
                // Save new member
                if (member.getExtId() != null && !member.getExtId().isEmpty()) {
                    log.info("会員ID: {} - 新規会員を登録します（extId: {}）", member.getId(), member.getExtId());
                } else {
                    log.warn("会員ID: {} - 新規会員を登録しますが、extIdが設定されていません", member.getId());
                }
                bCartMemberRepository.save(member);
            }
        }
    }

    /**
     * b_cart_memberテーブル内でext_idがNULLでなく、
     * かつm_partnerテーブルのpartner_codeには存在しないレコードを検索します
     *
     * @return b_cart_memberテーブル内でext_idがNULLでなく、かつm_partnerテーブルのpartner_codeには存在しないレコード
     */
    public List<BCartMember> fetchNonPartneredMembers() {
        return this.bCartMemberRepository.fetchNonPartneredMembers();
    }

    public BCartMember save(BCartMember bCartMember) {
        return this.bCartMemberRepository.save(bCartMember);
    }

    public void save(List<BCartMember> bCartMemberList) {
        for (BCartMember bCartMember : bCartMemberList) {
            this.save(bCartMember);
        }
    }
}
