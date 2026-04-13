package jp.co.oda32.api.bcart;

import jakarta.validation.Valid;
import jp.co.oda32.domain.model.bcart.BCartCategories;
import jp.co.oda32.domain.service.bcart.BCartCategoriesService;
import jp.co.oda32.domain.service.bcart.BCartChangeHistoryService;
import jp.co.oda32.dto.bcart.BCartCategoryResponse;
import jp.co.oda32.dto.bcart.BCartCategoryUpdateRequest;
import jp.co.oda32.dto.bcart.BCartChangeHistoryResponse;
import jp.co.oda32.domain.service.data.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/bcart/categories")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class BCartCategoryController {

    private final BCartCategoriesService categoriesService;
    private final BCartChangeHistoryService changeHistoryService;

    /**
     * カテゴリ一覧（ツリー構造）
     */
    @GetMapping
    public ResponseEntity<List<BCartCategoryResponse>> listCategories() {
        List<BCartCategories> all = categoriesService.findAll();

        Map<Integer, List<BCartCategories>> childrenMap = all.stream()
                .filter(c -> c.getParentCategoryId() != null)
                .collect(Collectors.groupingBy(BCartCategories::getParentCategoryId));

        // 商品数カウント用のマップはPhase2で追加予定
        List<BCartCategoryResponse> tree = all.stream()
                .filter(c -> c.getParentCategoryId() == null)
                .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                .map(parent -> {
                    BCartCategoryResponse resp = BCartCategoryResponse.from(parent);
                    List<BCartCategories> children = childrenMap.getOrDefault(parent.getId(), List.of());
                    resp.setChildren(children.stream()
                            .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                            .map(BCartCategoryResponse::from)
                            .collect(Collectors.toList()));
                    return resp;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(tree);
    }

    /**
     * カテゴリ詳細
     */
    @GetMapping("/{categoryId}")
    public ResponseEntity<BCartCategoryResponse> getCategory(@PathVariable Integer categoryId) {
        return categoriesService.findById(categoryId)
                .map(entity -> {
                    BCartCategoryResponse resp = BCartCategoryResponse.from(entity);
                    // 親カテゴリ名
                    if (entity.getParentCategoryId() != null) {
                        categoriesService.findById(entity.getParentCategoryId())
                                .ifPresent(parent -> resp.setParentCategoryName(parent.getName()));
                    }
                    // 子カテゴリ
                    List<BCartCategories> children = categoriesService.findByParentCategoryId(categoryId);
                    resp.setChildren(children.stream()
                            .map(BCartCategoryResponse::from)
                            .collect(Collectors.toList()));
                    return ResponseEntity.ok(resp);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * カテゴリ更新
     */
    @PutMapping("/{categoryId}")
    public ResponseEntity<BCartCategoryResponse> updateCategory(
            @PathVariable Integer categoryId,
            @Valid @RequestBody BCartCategoryUpdateRequest request,
            @AuthenticationPrincipal LoginUser loginUser) {

        return categoriesService.findById(categoryId)
                .map(entity -> {
                    // 楽観的ロックチェック（Hibernateの@Versionに頼らず手動比較）
                    if (!entity.getVersion().equals(request.getVersion())) {
                        return ResponseEntity.status(409).<BCartCategoryResponse>build();
                    }

                    // 変更前スナップショットを履歴に保存
                    changeHistoryService.recordChange(
                            "CATEGORY", Long.valueOf(categoryId), "CATEGORY",
                            null, entity.getName(), request.getName(),
                            entity, loginUser.getUser().getLoginUserNo());

                    entity.setName(request.getName());
                    entity.setDescription(request.getDescription());
                    entity.setRvDescription(request.getRvDescription());
                    entity.setParentCategoryId(request.getParentCategoryId());
                    entity.setMetaTitle(request.getMetaTitle());
                    entity.setMetaKeywords(request.getMetaKeywords());
                    entity.setMetaDescription(request.getMetaDescription());
                    entity.setPriority(request.getPriority());
                    entity.setFlag(request.getFlag());
                    entity.setBCartReflected(false);

                    BCartCategories saved = categoriesService.save(entity);
                    return ResponseEntity.ok(BCartCategoryResponse.from(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * カテゴリの変更履歴
     */
    @GetMapping("/{categoryId}/history")
    public ResponseEntity<List<BCartChangeHistoryResponse>> getCategoryHistory(
            @PathVariable Integer categoryId) {
        List<BCartChangeHistoryResponse> history = changeHistoryService
                .findByTarget("CATEGORY", Long.valueOf(categoryId))
                .stream()
                .map(BCartChangeHistoryResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(history);
    }
}
