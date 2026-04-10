package jp.co.oda32.api.bcart;

import jakarta.validation.Valid;
import jp.co.oda32.domain.model.bcart.BCartCategories;
import jp.co.oda32.domain.model.bcart.BCartProducts;
import jp.co.oda32.domain.service.bcart.BCartCategoriesService;
import jp.co.oda32.domain.service.bcart.BCartChangeHistoryService;
import jp.co.oda32.domain.service.bcart.BCartProductDescriptionService;
import jp.co.oda32.domain.service.bcart.BCartProductsService;
import jp.co.oda32.domain.service.data.LoginUser;
import jp.co.oda32.dto.bcart.BCartChangeHistoryResponse;
import jp.co.oda32.dto.bcart.BCartProductDescriptionUpdateRequest;
import jp.co.oda32.dto.bcart.BCartProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/bcart/products")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class BCartProductController {

    private final BCartProductsService productsService;
    private final BCartCategoriesService categoriesService;
    private final BCartChangeHistoryService changeHistoryService;
    private final BCartProductDescriptionService descriptionService;

    @GetMapping
    public ResponseEntity<List<BCartProductResponse>> listProducts(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String flag) {

        List<BCartProducts> products = productsService.search(name, categoryId, flag);
        Map<Integer, String> categoryNameMap = categoriesService.findAll().stream()
                .collect(Collectors.toMap(BCartCategories::getId, BCartCategories::getName, (a, b) -> a));

        return ResponseEntity.ok(products.stream()
                .map(p -> BCartProductResponse.from(p, categoryNameMap.get(p.getCategoryId())))
                .toList());
    }

    @GetMapping("/{productId}")
    public ResponseEntity<BCartProductResponse> getProduct(@PathVariable Integer productId) {
        return productsService.findByIdWithSets(productId)
                .map(entity -> {
                    String categoryName = entity.getCategoryId() != null
                            ? categoriesService.findById(entity.getCategoryId())
                                .map(BCartCategories::getName).orElse(null)
                            : null;
                    return ResponseEntity.ok(BCartProductResponse.from(entity, categoryName));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{productId}/description")
    public ResponseEntity<BCartProductResponse> updateDescription(
            @PathVariable Integer productId,
            @Valid @RequestBody BCartProductDescriptionUpdateRequest request,
            @AuthenticationPrincipal LoginUser loginUser) {

        return descriptionService.updateDescription(productId, request, loginUser.getUser().getLoginUserNo())
                .map(saved -> {
                    String categoryName = saved.getCategoryId() != null
                            ? categoriesService.findById(saved.getCategoryId())
                                .map(BCartCategories::getName).orElse(null)
                            : null;
                    return ResponseEntity.ok(BCartProductResponse.from(saved, categoryName));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{productId}/history")
    public ResponseEntity<List<BCartChangeHistoryResponse>> getHistory(@PathVariable Integer productId) {
        return ResponseEntity.ok(changeHistoryService
                .findByTarget("PRODUCT", Long.valueOf(productId))
                .stream().map(BCartChangeHistoryResponse::from).toList());
    }
}
