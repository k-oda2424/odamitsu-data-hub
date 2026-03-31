package jp.co.oda32.domain.service.goods;

import jp.co.oda32.dto.goods.GoodsCreateForm;
import jp.co.oda32.dto.goods.GoodsModifyForm;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.repository.goods.GoodsRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.service.util.LoginUserUtil;
import jp.co.oda32.domain.specification.goods.GoodsSpecification;
import jp.co.oda32.util.GoodsUtil;
import jp.co.oda32.util.StringUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import java.util.List;

/**
 * 商品Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2017/05/11
 */
@Service
public class MGoodsService extends CustomService {
    private final GoodsRepository goodsRepository;
    private GoodsSpecification goodsSpecification = new GoodsSpecification();
    private EntityManager entityManager;

    @Autowired
    public MGoodsService(GoodsRepository goodsRepository, EntityManager entityManager) {
        this.goodsRepository = goodsRepository;
        this.entityManager = entityManager;
    }

    public List<MGoods> findAll() {
        return goodsRepository.findAll();
    }

    /**
     * 商品番号で検索し、商品Entityを返します。
     *
     * @param goodsNo 商品番号
     * @return 商品Entity
     */
    public MGoods getByGoodsNo(Integer goodsNo) {
        return goodsRepository.findById(goodsNo).orElse(null);
    }

    /**
     * 商品名で検索し、リストで返します。
     * 削除フラグが立っていない商品を検索します。
     *
     * @param goodsName 商品名
     * @return 商品リスト
     */
    public List<MGoods> findByGoodsName(String goodsName) {
        return goodsRepository.findByGoodsNameAndDelFlg(goodsName, Flag.NO.getValue());
    }

    /**
     * JANCODEで検索し、リストで返します。
     * 削除フラグが立っていない商品を検索します。
     *
     * @param janCode janCode
     * @return 商品リスト
     */
    public MGoods getByJanCode(String janCode) {
        return goodsRepository.getByJanCodeAndDelFlg(janCode, Flag.NO.getValue());
    }


    public List<MGoods> findByNotExistWSalesGoods(String goodsName, String keyword, String janCode, Integer makerNo) {
        return this.goodsRepository.findAll(Specification
                .where(this.goodsSpecification.leftJoinWSalesGoods())
                .and(this.goodsSpecification.goodsNameContains(goodsName))
                .and(this.goodsSpecification.keywordContains(keyword))
                .and(this.goodsSpecification.makerNoContains(makerNo))
                .and(this.goodsSpecification.janCodeContains(janCode))
                .and(this.goodsSpecification.delFlgContains(Flag.NO)));
    }

    public List<MGoods> find(Integer goodsNo, String goodsName, String keyword, String janCode, Integer makerNo, Flag delFlg) {
        return this.goodsRepository.findAll(Specification
                .where(this.goodsSpecification.goodsNoContains(goodsNo))
                .and(this.goodsSpecification.goodsNameContains(goodsName))
                .and(this.goodsSpecification.keywordContains(keyword))
                .and(this.goodsSpecification.makerNoContains(makerNo))
                .and(this.goodsSpecification.janCodeContains(janCode))
                .and(this.goodsSpecification.delFlgContains(delFlg)));
    }

    /**
     * 商品を登録します。
     *
     * @param goodsCreateForm 商品登録フォーム
     * @return 登録した商品Entity
     */
    public MGoods insert(GoodsCreateForm goodsCreateForm) throws Exception {
        MGoods saveGoods = new MGoods();
        BeanUtils.copyProperties(goodsCreateForm, saveGoods);
        saveGoods.setApplyReducedTaxRate(goodsCreateForm.isApplyReducedTaxRateFlg());
        return this.insert(this.goodsRepository, saveGoods);
    }

    /**
     * 商品を更新します。
     *
     * @param goodsModifyForm 商品更新フォーム
     * @return 更新した商品Entity
     * @throws Exception システム例外
     */
    public MGoods update(GoodsModifyForm goodsModifyForm) throws Exception {
        MGoods updateGoods = this.goodsRepository.findById(goodsModifyForm.getGoodsNo()).orElseThrow();
        // 更新対象カラムの設定
        updateGoods.setGoodsName(goodsModifyForm.getGoodsName());
        updateGoods.setJanCode(goodsModifyForm.getJanCode());
        updateGoods.setKeyword(goodsModifyForm.getKeyword());
        updateGoods.setMakerNo(goodsModifyForm.getMakerNo());
        updateGoods.setSpecification(goodsModifyForm.getSpecification());
        updateGoods.setCaseContainNum(goodsModifyForm.getCaseContainNum());
        updateGoods.setApplyReducedTaxRate(goodsModifyForm.isApplyReducedTaxRateFlg());
        return this.update(this.goodsRepository, updateGoods);
    }

    /**
     * 商品を登録します。
     *
     * @param goods 商品登録フォーム
     * @return 登録した商品Entity
     */
    public MGoods insert(MGoods goods) throws Exception {
        // goods_nameから@金額を削除
        String goodsName = GoodsUtil.removePriceFromName(goods.getGoodsName());
        goods.setGoodsName(goodsName);
        return this.insert(this.goodsRepository, goods);
    }

    /**
     * 商品を更新します。
     *
     * @param goods 商品更新フォーム
     * @return 更新した商品Entity
     * @throws Exception システム例外
     */
    public MGoods update(MGoods goods) throws Exception {
        // goods_nameから@金額を削除
        String goodsName = GoodsUtil.removePriceFromName(goods.getGoodsName());
        goods.setGoodsName(goodsName);
        return this.update(this.goodsRepository, goods);
    }

    /**
     * 削除フラグを立てます
     *
     * @param goodsModifyForm 更新対象
     * @throws Exception システム例外
     */
    public void delete(GoodsModifyForm goodsModifyForm) throws Exception {
        MGoods updateMaker = this.goodsRepository.findById(goodsModifyForm.getGoodsNo()).orElseThrow();
        updateMaker.setDelFlg(Flag.YES.getValue());
        this.update(this.goodsRepository, updateMaker);
    }

    /**
     * 販売商品WORKに登録されていない商品マスタEntityリストを返します。
     *
     * @param mGoods 検索する商品条件用Entity
     * @return 販売商品WORKに登録されていない商品マスタEntityリスト
     * @throws Exception 例外発生時
     */
    @SuppressWarnings("unchecked")
    public List<MGoods> findByNotExistWSalesGoods(MGoods mGoods) throws Exception {
        Integer loginUserShopNo = LoginUserUtil.getLoginUserInfo().getUser().getShopNo();
        StringBuilder sql = new StringBuilder();
        sql.append("select g.* from m_goods g ");
        sql.append("left join w_sales_goods wsg on g.goods_no = wsg.goods_no and wsg.shop_no = :shopNo ");
        sql.append("where wsg.goods_no is null");
        if (!StringUtil.isEmpty(mGoods.getGoodsName())) {
            sql.append(" and g.goods_name like :goodsName");
        }
        if (!StringUtil.isEmpty(mGoods.getKeyword())) {
            sql.append(" and g.keyword like :keyword");
        }
        if (!StringUtil.isEmpty(mGoods.getJanCode())) {
            sql.append(" and g.jan_code = :janCode");
        }
        if (mGoods.getMakerNo() != null) {
            sql.append(" and g.maker_no = :makerNo");
        }

        var query = entityManager.createNativeQuery(sql.toString(), MGoods.class);
        query.setParameter("shopNo", loginUserShopNo);
        if (!StringUtil.isEmpty(mGoods.getGoodsName())) {
            query.setParameter("goodsName", "%" + mGoods.getGoodsName() + "%");
        }
        if (!StringUtil.isEmpty(mGoods.getKeyword())) {
            query.setParameter("keyword", "%" + mGoods.getKeyword() + "%");
        }
        if (!StringUtil.isEmpty(mGoods.getJanCode())) {
            query.setParameter("janCode", mGoods.getJanCode());
        }
        if (mGoods.getMakerNo() != null) {
            query.setParameter("makerNo", mGoods.getMakerNo());
        }
        return query.getResultList();
    }
}
