package jp.co.oda32.domain.service.master;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.repository.master.PartnerRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.master.PartnerSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 得意先Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2018/11/23
 */
@Service
public class MPartnerService extends CustomService {
    private final PartnerRepository partnerRepository;
    private final PartnerSpecification partnerSpecification = new PartnerSpecification();

    @Autowired
    public MPartnerService(PartnerRepository partnerRepository) {
        this.partnerRepository = partnerRepository;
    }

    /**
     * delフラグを考慮した全検索
     *
     * @return 得意先Entityのリスト
     */
    public List<MPartner> findAll() {
        return super.defaultFindAll(this.partnerRepository);
    }

    /**
     * 得意先番号で検索し、得意先Entityを返します。
     * 得意先番号「0」は初期値なので空とみなす
     *
     * @param partnerNo 得意先番号
     * @return 得意先Entity
     */
    public MPartner getByPartnerNo(Integer partnerNo) {
        Optional<MPartner> mPartnerOptional = partnerRepository.findById(partnerNo);
        return mPartnerOptional.orElse(null);
    }

    /**
     * 得意先名で検索し、リストを返します。
     *
     * @param partnerName 得意先名
     * @return 得意先マスタEntityリスト
     */
    public List<MPartner> findByPartnerName(String partnerName) {
        return this.partnerRepository.findByPartnerName(partnerName);
    }

    public List<MPartner> findByParentPartnerNo(int parentPartnerNo) {
        return this.partnerRepository.findByParentPartnerNo(parentPartnerNo);
    }
    /**
     * ユニークキーで検索します。
     *
     * @param shopNo      ショップ番号
     * @param partnerCode 得意先コード
     * @return 得意先Entity
     */
    public MPartner getByUniqueKey(Integer shopNo, String partnerCode) {
        return this.partnerRepository.getByShopNoAndPartnerCode(shopNo, partnerCode);
    }

    /**
     * 検索条件を指定して検索します。
     *
     * @param partnerNo         得意先番号
     * @param partnerName       得意先名
     * @param partnerCode       得意先コード
     * @param lastOrderDateFrom 最終注文日From
     * @param lastOrderDateTo   最終注文日To
     * @param delFlg            削除フラグ
     * @return 得意先Entityリスト
     */
    public List<MPartner> find(Integer partnerNo, String partnerName, String partnerCode, LocalDate lastOrderDateFrom, LocalDate lastOrderDateTo, Flag delFlg) {
        return this.partnerRepository.findAll(Specification
                .where(this.partnerSpecification.partnerNoContains(partnerNo))
                .and(this.partnerSpecification.partnerNameContains(partnerName))
                .and(this.partnerSpecification.partnerCodeContains(partnerCode))
                .and(this.partnerSpecification.lastOrderDateContains(lastOrderDateFrom, lastOrderDateTo))
                .and(this.partnerSpecification.delFlgContains(delFlg)));
    }

    /**
     * 得意先番号リストで検索します
     *
     * @return 検索結果
     */
    public List<MPartner> findByPartnerNoList(List<Integer> partnerNoList) {
        return this.partnerRepository.findAll(Specification
                .where(this.partnerSpecification.partnerNoListContains(partnerNoList))
                .and(this.partnerSpecification.delFlgContains(Flag.NO)));
    }

    /**
     * 検索条件を指定して検索します。
     *
     * @param parentPartnerNo 親得意先番号
     * @return 親得意先が持つ子得意先Entityリスト
     */
    public List<MPartner> findChildrenPartner(Integer parentPartnerNo) {
        return this.partnerRepository.findAll(Specification
                .where(this.partnerSpecification.parentPartnerNoContains(parentPartnerNo))
                .and(this.partnerSpecification.delFlgContains(Flag.NO)));
    }

    /**
     * 検索条件を指定して検索します。
     *
     * @return 親得意先を持つ子得意先Entityリスト
     */
    public List<MPartner> findHasParentPartner() {
        return this.partnerRepository.findAll(Specification
                .where(this.partnerSpecification.hasParentPartnerContains())
                .and(this.partnerSpecification.delFlgContains(Flag.NO)));
    }

    public List<MPartner> findByShopNo(Integer shopNo, String partnerName, String partnerCode) {
        return this.partnerRepository.findAll(Specification
                .where(this.partnerSpecification.shopNoContains(shopNo))
                .and(this.partnerSpecification.partnerNameContains(partnerName))
                .and(this.partnerSpecification.partnerCodeContains(partnerCode))
                .and(this.partnerSpecification.delFlgContains(Flag.NO)));
    }

    public List<MPartner> findByPartnerCodeList(Integer shopNo, List<String> partnerCodeList) {
        return this.partnerRepository.findAll(Specification
                .where(this.partnerSpecification.shopNoContains(shopNo))
                .and(this.partnerSpecification.partnerCodeListContains(partnerCodeList))
                .and(this.partnerSpecification.delFlgContains(Flag.NO)));
    }

    /**
     * 得意先を登録します。
     *
     * @param partner 得意先登録フォーム
     * @return 登録した得意先Entity
     */
    public MPartner insert(MPartner partner) throws Exception {
        return this.insert(this.partnerRepository, partner);
    }

    /**
     * 得意先を更新します。
     *
     * @param partner 得意先更新フォーム
     * @return 更新した得意先Entity
     * @throws Exception システム例外
     */
    public MPartner update(MPartner partner) throws Exception {
        return this.update(this.partnerRepository, partner);
    }

    /**
     * 削除フラグを立てます
     *
     * @param partner 更新対象
     * @throws Exception システム例外
     */
    public void delete(MPartner partner) throws Exception {
        MPartner updatePartner = this.partnerRepository.findById(partner.getPartnerNo()).orElseThrow();
        updatePartner.setDelFlg(Flag.YES.getValue());
        this.update(this.partnerRepository, updatePartner);
    }
}
