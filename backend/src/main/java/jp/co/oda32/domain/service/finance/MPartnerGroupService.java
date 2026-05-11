package jp.co.oda32.domain.service.finance;

import jp.co.oda32.audit.AuditLog;
import jp.co.oda32.domain.model.finance.MPartnerGroup;
import jp.co.oda32.domain.repository.finance.MPartnerGroupRepository;
import jp.co.oda32.dto.finance.PartnerGroupRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * パートナーグループ Service。
 *
 * <ul>
 *   <li>SF-04: partnerCodes を 6 桁 0 埋め正規化 + dedup</li>
 *   <li>SF-18: クラスレベル {@code @Transactional(readOnly=true)} + 書込みは個別 override</li>
 *   <li>SF-19: 取得系は JOIN FETCH 経由で N+1 を回避</li>
 *   <li>SF-20: 削除を論理削除化 (del_flg='1')</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MPartnerGroupService {

    private final MPartnerGroupRepository repository;

    public List<MPartnerGroup> findByShopNo(Integer shopNo) {
        // SF-19: JOIN FETCH で N+1 回避 / SF-20: del_flg='0' のみ
        return shopNo != null
                ? repository.findActiveByShopNoFetchMembers(shopNo)
                : repository.findAllActiveFetchMembers();
    }

    public MPartnerGroup findById(Integer id) {
        // SF-19: partnerCodes (@ElementCollection LAZY) を JOIN FETCH で eager 取得し、
        // 呼び出し元 (TOrderDetailService 等) の Service-layer から LazyInitializationException を防ぐ。
        return repository.findActiveByIdFetchMembers(id).orElse(null);
    }

    @Transactional
    @AuditLog(table = "m_partner_group", operation = "INSERT",
            pkExpression = "{'groupName': #a0.groupName, 'shopNo': #a0.shopNo}",
            captureArgsAsAfter = true)
    public MPartnerGroup create(PartnerGroupRequest request) {
        MPartnerGroup group = MPartnerGroup.builder()
                .groupName(request.getGroupName())
                .shopNo(request.getShopNo())
                .delFlg("0")
                .partnerCodes(normalizeAndDedup(request.getPartnerCodes()))
                .build();
        return repository.save(group);
    }

    @Transactional
    @AuditLog(table = "m_partner_group", operation = "UPDATE",
            pkExpression = "{'partnerGroupId': #a0}",
            captureArgsAsAfter = true)
    public MPartnerGroup update(Integer id, PartnerGroupRequest request) {
        MPartnerGroup group = repository.findById(id)
                .filter(g -> "0".equals(g.getDelFlg()))
                .orElseThrow(() -> new IllegalArgumentException("グループが見つかりません: id=" + id));
        group.setGroupName(request.getGroupName());
        // 非 admin の shopNo 強制上書きは Controller 側で実施済 (SF-03)。
        // ここでは渡された shopNo を信頼して反映 (admin の他 shop 移管を許容)。
        if (request.getShopNo() != null) {
            group.setShopNo(request.getShopNo());
        }
        // SF-04: dedup + 正規化済 list を再代入 (clear/addAll で要素入れ替え)
        List<String> normalized = normalizeAndDedup(request.getPartnerCodes());
        group.getPartnerCodes().clear();
        group.getPartnerCodes().addAll(normalized);
        return repository.save(group);
    }

    @Transactional
    @AuditLog(table = "m_partner_group", operation = "DELETE",
            pkExpression = "{'partnerGroupId': #a0}",
            captureArgsAsAfter = true)
    public void delete(Integer id) {
        // SF-20: 物理削除 → 論理削除化
        MPartnerGroup group = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("グループが見つかりません: id=" + id));
        if (!"0".equals(group.getDelFlg())) {
            // 既に削除済 → 冪等で OK
            return;
        }
        group.setDelFlg("1");
        repository.save(group);
    }

    /**
     * SF-04: partnerCode リストを 6 桁 0 埋めに正規化 + dedup (LinkedHashSet で順序保持)。
     *
     * <p>例: ["29", "  000029  ", "<181>", "181"] → ["000029", "000181"]
     */
    private List<String> normalizeAndDedup(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String code : raw) {
            String normalized = normalizePartnerCode(code);
            if (normalized != null) {
                seen.add(normalized);
            }
        }
        return List.copyOf(seen);
    }

    /**
     * 得意先コードを 6 桁 0 埋めに正規化する。
     * - "<009896>" → "009896"
     * - "29" → "000029"
     * - " 181 " → "000181"
     * - 数値変換不能 / null / 空文字 → null (caller でスキップ)
     *
     * <p>SF-04: {@link InvoiceImportService#convertPartnerCode(String)} と同等のルール。
     */
    private String normalizePartnerCode(String raw) {
        if (raw == null) return null;
        String code = raw.replace("<", "").replace(">", "").trim();
        if (code.isEmpty()) return null;
        try {
            long numericCode = Long.parseLong(code);
            return String.format("%06d", numericCode);
        } catch (NumberFormatException e) {
            // 非数値 partnerCode は dedup 対象から除外する (Excel 側でも convertPartnerCode が throw する)
            return null;
        }
    }
}
