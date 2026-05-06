package jp.co.oda32.api.finance;

import jakarta.validation.Valid;
import jp.co.oda32.domain.service.data.LoginUser;
import jp.co.oda32.domain.service.finance.mf.MfOpeningBalanceService;
import jp.co.oda32.domain.service.util.LoginUserUtil;
import jp.co.oda32.dto.finance.MfOpeningBalanceFetchResponse;
import jp.co.oda32.dto.finance.SupplierOpeningBalanceResponse;
import jp.co.oda32.dto.finance.SupplierOpeningBalanceUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * supplier 毎の前期繰越 (期首残) API。
 * MF journal #1 から自動取得 + 手動補正で運用。
 *
 * @since 2026-04-24
 */
@RestController
@RequestMapping("/api/v1/finance/supplier-opening-balance")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class SupplierOpeningBalanceController {

    private final MfOpeningBalanceService service;

    @GetMapping
    public ResponseEntity<SupplierOpeningBalanceResponse> list(
            @RequestParam Integer shopNo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate openingDate) {
        // SF-G04: shop 権限ガード。Cluster F SF-C-N5 round 2 fix の fail-closed パターン採用。
        // admin (shopNo=0) は任意 shopNo 参照可、非 admin は自店舗のみ参照可。
        Integer effective = LoginUserUtil.resolveEffectiveShopNo(shopNo);
        if (effective != null && !effective.equals(shopNo)) {
            throw new AccessDeniedException("他ショップの期首残データへのアクセス権限がありません");
        }
        return ResponseEntity.ok(service.list(shopNo, openingDate));
    }

    /** MF /journals から journal #1 を取得して upsert (手動補正は保持)。admin 限定。 */
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PostMapping("/fetch-from-mf")
    public ResponseEntity<MfOpeningBalanceFetchResponse> fetchFromMf(
            @RequestParam Integer shopNo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate openingDate,
            @AuthenticationPrincipal LoginUser user) {
        Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
        return ResponseEntity.ok(service.fetchFromMfJournalOne(shopNo, openingDate, userNo));
    }

    /** 手動補正 (adjustment_reason は非ゼロ時に必須)。admin 限定。 */
    @PreAuthorize("@loginUserSecurityBean.isAdmin()")
    @PutMapping("/manual-adjustment")
    public ResponseEntity<?> updateManualAdjustment(
            @Valid @RequestBody SupplierOpeningBalanceUpdateRequest req,
            @AuthenticationPrincipal LoginUser user) {
        Integer userNo = user == null ? null : user.getUser().getLoginUserNo();
        service.updateManualAdjustment(req, userNo);
        return ResponseEntity.noContent().build();
    }
}
