package jp.co.oda32.domain.service.goods;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.goods.MGoodsUnit;
import jp.co.oda32.domain.repository.goods.GoodsUnitRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.goods.MGoodsUnitSpecification;
import jp.co.oda32.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品単位Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2017/05/11
 */
@Service
public class MGoodsUnitService extends CustomService {
    private final GoodsUnitRepository goodsUnitRepository;
    private final MGoodsUnitSpecification goodsUnitSpecification = new MGoodsUnitSpecification();

    @Autowired
    public MGoodsUnitService(GoodsUnitRepository goodsRepository) {
        this.goodsUnitRepository = goodsRepository;
    }

    public List<MGoodsUnit> findAll() {
        return goodsUnitRepository.findAll();
    }

    public MGoodsUnit getByGoodsUnitNo(Integer goodsUnitNo) {
        return goodsUnitRepository.findById(goodsUnitNo).orElse(null);
    }

    /**
     * 商品番号で検索し、商品単位Entityリストを返します。
     * 削除フラグがたっていないもののみ
     *
     * @param goodsNo 商品番号
     * @return 商品単位Entity配列
     */
    public List<MGoodsUnit> findByGoodsNo(Integer goodsNo) {
        return this.goodsUnitRepository.findByGoodsNo(goodsNo)
                .stream()
                .filter(goodsUnit -> goodsUnit.getDelFlg().equals(Flag.NO.getValue()) || StringUtil.isEmpty(goodsUnit.getDelFlg()))
                .collect(Collectors.toList());
    }

    /**
     * ユニークキーで検索します
     *
     * @param goodsNo  商品番号
     * @param unitType 商品単位種別
     * @return 商品単位Entity
     */
    public MGoodsUnit getByUniqKey(Integer goodsNo, String unitType) {
        return this.goodsUnitRepository.getByGoodsNoAndUnit(goodsNo, unitType);
    }

    public List<MGoodsUnit> findByGoodsNoList(List<Integer> goodsNoList) {
        return this.goodsUnitRepository.findAll(Specification
                .where(this.goodsUnitSpecification.goodsNoListContains(goodsNoList)));
    }

    public List<MGoodsUnit> findByUnitNoList(List<Integer> unitNoList) {
        return this.goodsUnitRepository.findAll(Specification
                .where(this.goodsUnitSpecification.unitNoListContains(unitNoList)));
    }

    /**
     * 商品単位を登録します。
     *
     * @param goodsUnit 商品単位Entity
     * @return 登録した商品単位Entity
     */
    public MGoodsUnit insert(MGoodsUnit goodsUnit) throws Exception {
        return this.insert(this.goodsUnitRepository, goodsUnit);
    }

    /**
     * 商品単位を更新します。
     *
     * @param goodsUnit 商品単位Entity
     * @return 更新した商品単位Entity
     * @throws Exception システム例外
     */
    public MGoodsUnit update(MGoodsUnit goodsUnit) throws Exception {
        return this.update(this.goodsUnitRepository, goodsUnit);
    }

    /**
     * 削除フラグを立てます
     *
     * @param goodsUnit 更新対象
     * @throws Exception システム例外
     */
    public void delete(MGoodsUnit goodsUnit) throws Exception {
        MGoodsUnit updateGoodsUnit = this.goodsUnitRepository.findById(goodsUnit.getUnitNo()).orElseThrow();
        updateGoodsUnit.setDelFlg(Flag.YES.getValue());
        this.update(this.goodsUnitRepository, updateGoodsUnit);
    }
}
