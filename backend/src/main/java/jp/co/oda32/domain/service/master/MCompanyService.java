package jp.co.oda32.domain.service.master;

import jp.co.oda32.constant.CompanyType;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.repository.master.CompanyRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.master.MCompanySpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 会社Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2018/11/23
 */
@Service
public class MCompanyService extends CustomService {
    private final CompanyRepository companyRepository;
    private final MCompanySpecification companySpecification = new MCompanySpecification();

    @Autowired
    public MCompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    /**
     * delフラグを考慮した全検索
     *
     * @return 会社Entityのリスト
     */
    public List<MCompany> findAll() {
        return super.defaultFindAll(this.companyRepository);
    }

    /**
     * 会社種類で検索し、配列で返します。
     *
     * @param companyType 会社種類
     * @return 会社リスト
     */
    public List<MCompany> findByCompanyType(String companyType) {
        return this.companyRepository.findAll(Specification
                .where(this.companySpecification.companyTypeContains(companyType))
                .and(this.companySpecification.delFlgContains(Flag.NO)));
    }

    /**
     * 会社種類で検索し、配列で返します。
     *
     * @param companyType 会社種類
     * @return 会社リスト
     */
    public List<MCompany> findByShopNoAndCompanyType(Integer shopNo, String companyType) {
        return this.companyRepository.findByShopNoAndCompanyTypeAndDelFlg(shopNo, companyType, Flag.NO.getValue());
    }

    public List<MCompany> find(Integer companyNo, String companyName, String companyType, Flag delFlg) {

        return this.companyRepository.findAll(Specification
                .where(this.companySpecification.companyNoContains(companyNo))
                .and(this.companySpecification.companyNameContains(companyName))
                .and(this.companySpecification.companyTypeContains(companyType))
                .and(this.companySpecification.delFlgContains(delFlg)));
    }

    /**
     * 会社番号で検索し、会社Entityを返します。
     * 会社番号「0」は初期値なので空とみなす
     *
     * @param companyNo 会社番号
     * @return 会社Entity
     */
    public MCompany getByCompanyNo(Integer companyNo) {
        Optional<MCompany> mCompanyOptional = companyRepository.findById(companyNo);
        return mCompanyOptional.orElse(null);
    }

    /**
     * ショップ番号で検索し、会社Entityを返します。
     *
     * @param shopNo ショップ番号
     * @return 会社マスタEntity
     */
    public MCompany getByShopNo(Integer shopNo) {
        return this.companyRepository.getByShopNoAndCompanyType(shopNo, CompanyType.SHOP.getValue());
    }

    /**
     * 得意先番号で検索し、会社Entityを返します。
     * ショップの場合は得意先番号はnull以外の実態はuniqueになっている。
     *
     * @param partnerNo 得意先番号
     * @return 会社マスタEntity
     */
    public MCompany getByPartnerNo(Integer partnerNo) {
        return this.companyRepository.getByPartnerNo(partnerNo);
    }

    /**
     * 会社を登録します。
     *
     * @param company 会社登録フォーム
     * @return 登録した会社Entity
     */
    public MCompany insert(MCompany company) throws Exception {
        return this.insert(this.companyRepository, company);
    }

    /**
     * 会社を更新します。
     *
     * @param company 会社更新フォーム
     * @return 更新した会社Entity
     * @throws Exception システム例外
     */
    public MCompany update(MCompany company) throws Exception {
        return this.update(this.companyRepository, company);
    }

    /**
     * 削除フラグを立てます
     *
     * @param company 更新対象
     * @throws Exception システム例外
     */
    public void delete(MCompany company) throws Exception {
        MCompany updateCompany = this.companyRepository.findById(company.getCompanyNo()).orElseThrow();
        updateCompany.setDelFlg(Flag.YES.getValue());
        this.update(this.companyRepository, updateCompany);
    }
}
