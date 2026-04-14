package jp.co.oda32.domain.service.bcart;

import com.google.gson.Gson;
import jp.co.oda32.domain.model.bcart.BCartProductSets;
import jp.co.oda32.domain.repository.bcart.BCartProductSetsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BCartProductSetsService {
    private final BCartProductSetsRepository bCartProductSetsRepository;

    public BCartProductSets getByPK(Long id) {
        return this.bCartProductSetsRepository.findById(id).orElse(null);
    }

    /**
     * B-Cartシステムに価格反映できていない商品のリストを取得します
     *
     * @return B-Cartシステムに価格反映できていない商品のリスト
     */
    public List<BCartProductSets> findNotBCartPriceReflected() {
        return this.bCartProductSetsRepository.findBybCartPriceReflectedIsFalse();
    }

    public BCartProductSets save(BCartProductSets bCartProductSets) {
        Gson gson = new Gson();

        // Serialize the fields
        String groupPriceJson = gson.toJson(bCartProductSets.getGroupPriceMap());
        String specialPriceJson = gson.toJson(bCartProductSets.getSpecialPriceMap());
        String volumeDiscountJson = gson.toJson(bCartProductSets.getVolumeDiscountMap());
        String stockParentJson = gson.toJson(bCartProductSets.getStockParentMap());
        String customsJson = gson.toJson(bCartProductSets.getCustomsList());

        // Set the JSON strings to the entity
        bCartProductSets.setGroupPrice(jsonStringNullConvert(groupPriceJson));
        bCartProductSets.setSpecialPrice(jsonStringNullConvert(specialPriceJson));
        bCartProductSets.setVolumeDiscount(jsonStringNullConvert(volumeDiscountJson));
        bCartProductSets.setStockParent(jsonStringNullConvert(stockParentJson));
        bCartProductSets.setCustomsForDB(jsonStringNullConvert(customsJson));
        // Save the entity
        return bCartProductSetsRepository.save(bCartProductSets);
    }

    public void updateBCartPriceReflected(BCartProductSets bCartProductSets) {
        bCartProductSets.setBCartPriceReflected(true);
        this.bCartProductSetsRepository.save(bCartProductSets);
    }

    private String jsonStringNullConvert(String jsonStr) {
        if ("null".equals(jsonStr)) {
            return null;
        }
        return jsonStr;
    }
}
