package jp.co.oda32.domain.service.bcart;

import jp.co.oda32.domain.model.bcart.BCartProducts;
import jp.co.oda32.dto.bcart.BCartProductDescriptionUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * B-CART商品説明更新Service
 * 説明文の更新と変更履歴の記録をトランザクション内で実行する
 */
@Service
@RequiredArgsConstructor
public class BCartProductDescriptionService {

    private final BCartProductsService productsService;
    private final BCartChangeHistoryService changeHistoryService;

    @Transactional
    public Optional<BCartProducts> updateDescription(Integer productId,
                                                     BCartProductDescriptionUpdateRequest request,
                                                     Integer userNo) {
        return productsService.findById(productId).map(entity -> {
            // 変更フィールドごとに履歴を記録
            recordIfChanged(productId, "name", entity.getName(), request.getName(), entity, userNo);
            recordIfChanged(productId, "catch_copy", entity.getCatchCopy(), request.getCatchCopy(), entity, userNo);
            recordIfChanged(productId, "description", entity.getDescription(), request.getDescription(), entity, userNo);
            recordIfChanged(productId, "prepend_text", entity.getPrependText(), request.getPrependText(), entity, userNo);
            recordIfChanged(productId, "append_text", entity.getAppendText(), request.getAppendText(), entity, userNo);
            recordIfChanged(productId, "middle_text", entity.getMiddleText(), request.getMiddleText(), entity, userNo);
            recordIfChanged(productId, "rv_prepend_text", entity.getRvPrependText(), request.getRvPrependText(), entity, userNo);
            recordIfChanged(productId, "rv_append_text", entity.getRvAppendText(), request.getRvAppendText(), entity, userNo);
            recordIfChanged(productId, "rv_middle_text", entity.getRvMiddleText(), request.getRvMiddleText(), entity, userNo);
            recordIfChanged(productId, "meta_title", entity.getMetaTitle(), request.getMetaTitle(), entity, userNo);
            recordIfChanged(productId, "meta_keywords", entity.getMetaKeywords(), request.getMetaKeywords(), entity, userNo);
            recordIfChanged(productId, "meta_description", entity.getMetaDescription(), request.getMetaDescription(), entity, userNo);

            // フィールド更新（nullでない場合のみ）
            if (request.getName() != null) entity.setName(request.getName());
            if (request.getCatchCopy() != null) entity.setCatchCopy(request.getCatchCopy());
            if (request.getDescription() != null) entity.setDescription(request.getDescription());
            if (request.getPrependText() != null) entity.setPrependText(request.getPrependText());
            if (request.getAppendText() != null) entity.setAppendText(request.getAppendText());
            if (request.getMiddleText() != null) entity.setMiddleText(request.getMiddleText());
            if (request.getRvPrependText() != null) entity.setRvPrependText(request.getRvPrependText());
            if (request.getRvAppendText() != null) entity.setRvAppendText(request.getRvAppendText());
            if (request.getRvMiddleText() != null) entity.setRvMiddleText(request.getRvMiddleText());
            if (request.getMetaTitle() != null) entity.setMetaTitle(request.getMetaTitle());
            if (request.getMetaKeywords() != null) entity.setMetaKeywords(request.getMetaKeywords());
            if (request.getMetaDescription() != null) entity.setMetaDescription(request.getMetaDescription());

            return productsService.save(entity);
        });
    }

    private void recordIfChanged(Integer productId, String fieldName,
                                 String oldValue, String newValue,
                                 BCartProducts snapshot, Integer userNo) {
        if (newValue != null && !Objects.equals(oldValue, newValue)) {
            changeHistoryService.recordChange(
                    "PRODUCT", Long.valueOf(productId), "DESCRIPTION",
                    fieldName, oldValue, newValue, null, userNo);
        }
    }
}
