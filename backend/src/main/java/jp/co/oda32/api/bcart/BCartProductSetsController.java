package jp.co.oda32.api.bcart;

import jakarta.validation.Valid;
import jp.co.oda32.domain.service.bcart.BCartProductSetsPricingService;
import jp.co.oda32.domain.service.data.LoginUser;
import jp.co.oda32.dto.bcart.BCartProductSetPricingRequest;
import jp.co.oda32.dto.bcart.BCartProductSetPricingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bcart/product-sets")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class BCartProductSetsController {

    private final BCartProductSetsPricingService pricingService;

    /**
     * B-CART商品セットの単価・配送サイズを更新する。
     * 変更があれば履歴に記録し、b_cart_price_reflected=false にマークする。
     */
    @PutMapping("/{setId}/pricing")
    public ResponseEntity<BCartProductSetPricingResponse> updatePricing(
            @PathVariable Long setId,
            @Valid @RequestBody BCartProductSetPricingRequest request,
            @AuthenticationPrincipal LoginUser loginUser) {
        return pricingService.update(setId, request, loginUser.getUser().getLoginUserNo())
                .map(BCartProductSetPricingResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
