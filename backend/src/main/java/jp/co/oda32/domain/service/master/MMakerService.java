package jp.co.oda32.domain.service.master;

import jp.co.oda32.dto.master.MakerCreateForm;
import jp.co.oda32.dto.master.MakerModifyForm;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.master.MMaker;
import jp.co.oda32.domain.repository.master.MakerRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.master.MakerSpecification;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * メーカーEntity操作用サービスクラス
 *
 * @author k_oda
 * @since 2017/05/11
 */
@Service
public class MMakerService extends CustomService {
    private final MakerRepository makerRepository;
    private MakerSpecification makerSpecification = new MakerSpecification();

    @Autowired
    public MMakerService(MakerRepository makerRepository) {
        this.makerRepository = makerRepository;
    }

    /**
     * delフラグを考慮した全検索
     *
     * @return メーカーEntityのリスト
     */
    public List<MMaker> findAll() {
        return super.defaultFindAll(this.makerRepository);
    }

    /**
     * メーカー番号で検索し、メーカーEntityを返します。
     *メーカー番号「0」は初期値なので空とみなす
     * @param makerNo メーカー番号
     * @return メーカーEntity
     */
    public MMaker getByMakerNo(Integer makerNo) {
        Optional<MMaker> mMakerOptional = makerRepository.findById(makerNo);
        return mMakerOptional.orElse(null);
    }

    /**
     * メーカー名で検索し、リストを返します。
     *
     * @param makerName メーカー名
     * @return メーカーマスタEntityリスト
     */
    public List<MMaker> findByMakerName(String makerName) {
        return this.makerRepository.findByMakerName(makerName);
    }

    /**
     * 検索条件を指定して検索します。
     *
     * @param makerNo   メーカー番号
     * @param makerName メーカー名
     * @param delFlg    削除フラグ
     * @return メーカーEntityリスト
     */
    public List<MMaker> find(Integer makerNo, String makerName, Flag delFlg) {
        return this.makerRepository.findAll(Specification
                .where(this.makerSpecification.makerNoContains(makerNo))
                .and(this.makerSpecification.makerNameContains(makerName))
                .and(this.makerSpecification.delFlgContains(delFlg)));
    }

    /**
     * メーカーを登録します。
     *
     * @param makerCreateForm メーカー登録フォーム
     * @return 登録したメーカーEntity
     */
    public MMaker insert(MakerCreateForm makerCreateForm) throws Exception {
        MMaker saveMaker = new MMaker();
        BeanUtils.copyProperties(makerCreateForm, saveMaker);
        return this.insert(this.makerRepository, saveMaker);
    }

    /**
     * メーカーを更新します。
     *
     * @param makerModifyForm メーカー更新フォーム
     * @return 更新したメーカーEntity
     * @throws Exception システム例外
     */
    public MMaker update(MakerModifyForm makerModifyForm) throws Exception {
        MMaker updateMaker = this.makerRepository.findById(makerModifyForm.getMakerNo()).orElseThrow();
        // 更新対象カラムの設定
        updateMaker.setMakerName(makerModifyForm.getMakerName());
        return this.update(this.makerRepository, updateMaker);
    }

    /**
     * 削除フラグを立てます
     *
     * @param makerModifyForm 更新対象
     * @throws Exception システム例外
     */
    public void delete(MakerModifyForm makerModifyForm) throws Exception {
        MMaker updateMaker = this.makerRepository.findById(makerModifyForm.getMakerNo()).orElseThrow();
        updateMaker.setDelFlg(Flag.YES.getValue());
        this.update(this.makerRepository, updateMaker);
    }

    /**
     * メーカーを登録します。
     *
     * @param maker メーカー登録フォーム
     * @return 登録したメーカーEntity
     */
    public MMaker insert(MMaker maker) throws Exception {
        return this.insert(this.makerRepository, maker);
    }

    /**
     * メーカーを更新します。
     *
     * @param maker メーカー更新フォーム
     * @return 更新したメーカーEntity
     * @throws Exception システム例外
     */
    public MMaker update(MMaker maker) throws Exception {
        return this.update(this.makerRepository, maker);
    }

    /**
     * 削除フラグを立てます
     *
     * @param maker 更新対象
     * @throws Exception システム例外
     */
    public void delete(MMaker maker) throws Exception {
        MMaker updateMaker = this.makerRepository.findById(maker.getMakerNo()).orElseThrow();
        updateMaker.setDelFlg(Flag.YES.getValue());
        this.update(this.makerRepository, updateMaker);
    }
}
