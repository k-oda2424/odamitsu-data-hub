package jp.co.oda32.api.bcart;

import jakarta.validation.Valid;
import jp.co.oda32.domain.service.bcart.BCartPendingChangesQueryService;
import jp.co.oda32.domain.service.bcart.BCartProductSetsReflectService;
import jp.co.oda32.dto.bcart.BCartPendingChangeResponse;
import jp.co.oda32.dto.bcart.BCartReflectRequest;
import jp.co.oda32.dto.bcart.BCartReflectResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/bcart/pending-changes")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class BCartPendingChangesController {

    private static final int MAX_REFLECT_BATCH = 200;

    private final BCartPendingChangesQueryService queryService;
    private final BCartProductSetsReflectService reflectService;

    /**
     * 未反映の B-CART 変更点を商品セット単位で集約して返す。
     */
    @GetMapping
    public ResponseEntity<List<BCartPendingChangeResponse>> list() {
        return ResponseEntity.ok(queryService.findPendingChanges());
    }

    /**
     * 件数のみ返す（サイドバーバッジ等で使用）。
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> count() {
        return ResponseEntity.ok(Map.of("count", queryService.countPendingProductSets()));
    }

    /**
     * 指定商品セットの変更を B-CART API へ PATCH 反映する。
     * - `productSetIds`: 反映対象のID リスト
     * - `all=true`: 全件対象
     * 件数上限 200。超過時は 400 BAD_REQUEST。
     */
    @PostMapping("/reflect")
    public ResponseEntity<?> reflect(@Valid @RequestBody BCartReflectRequest request) {
        List<Long> ids;
        if (Boolean.TRUE.equals(request.getAll())) {
            ids = reflectService.findAllUnreflectedProductSetIds();
        } else if (request.getProductSetIds() != null) {
            ids = request.getProductSetIds();
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "productSetIds または all=true を指定してください"));
        }

        if (ids.size() > MAX_REFLECT_BATCH) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "1 回の反映は最大 " + MAX_REFLECT_BATCH + " 件までです（指定: " + ids.size() + " 件）",
                    "limit", MAX_REFLECT_BATCH,
                    "requested", ids.size()
            ));
        }

        BCartReflectResult result = reflectService.reflect(ids);
        return ResponseEntity.ok(result);
    }
}
