package jp.co.oda32.domain.service.master;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.master.MShop;
import jp.co.oda32.domain.repository.master.ShopRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.master.MShopSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ショップEntity操作用サービスクラス
 *
 * @author k_oda
 * @since 2017/05/11
 */
@Service
public class MShopService extends CustomService {
    private final ShopRepository shopRepository;
    private MShopSpecification mShopSpecification = new MShopSpecification();

    @Autowired
    public MShopService(ShopRepository shopRepository) {
        this.shopRepository = shopRepository;
    }

    public List<MShop> findAll() {
        return shopRepository.findAll();
    }

    public List<MShop> findByShopNoList(List<Integer> shopNoList) {
        return this.shopRepository.findAll(Specification
                .where(this.mShopSpecification.shopNoListContains(shopNoList)));

    }

    /**
     * ショップ番号で検索し、ショップEntityを返します。
     *
     * @param shopNo ショップ番号
     * @return ショップEntity
     */
    public MShop getByShopNo(Integer shopNo) {
        return shopRepository.findById(shopNo).orElse(null);
    }

    /**
     * ショップ名で検索し、リストで返します。
     *
     * @param shopName ショップ名
     * @return ショップリスト
     */
    public List<MShop> findByShopName(String shopName) {
        return shopRepository.findByShopName(shopName);
    }

    /**
     * ショップを登録します。
     *
     * @param shop ショップ登録フォーム
     * @return 登録したショップEntity
     */
    public MShop insert(MShop shop) throws Exception {
        return this.insert(this.shopRepository, shop);
    }

    /**
     * ショップを更新します。
     *
     * @param shop ショップ更新フォーム
     * @return 更新したショップEntity
     * @throws Exception システム例外
     */
    public MShop update(MShop shop) throws Exception {
        return this.update(this.shopRepository, shop);
    }

    /**
     * 削除フラグを立てます
     *
     * @param shop 更新対象
     * @throws Exception システム例外
     */
    public void delete(MShop shop) throws Exception {
        MShop updateShop = this.shopRepository.findById(shop.getShopNo()).orElseThrow();
        updateShop.setDelFlg(Flag.YES.getValue());
        this.update(this.shopRepository, updateShop);
    }
}
