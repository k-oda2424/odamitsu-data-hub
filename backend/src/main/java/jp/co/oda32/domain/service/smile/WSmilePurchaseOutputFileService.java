package jp.co.oda32.domain.service.smile;

import jp.co.oda32.domain.model.embeddable.WSmilePurchaseOutputFilePK;
import jp.co.oda32.domain.model.smile.WSmilePurchaseOutputFile;
import jp.co.oda32.domain.repository.smile.WSmilePurchaseOutputFileRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

/**
 * WSmilePurchaseOutputFileのサービスクラス
 *
 * @author k_oda
 * @since 2024/05/08
 */
@Service
@Transactional
@Log4j2
public class WSmilePurchaseOutputFileService {

    private final WSmilePurchaseOutputFileRepository repository;

    @Autowired
    public WSmilePurchaseOutputFileService(WSmilePurchaseOutputFileRepository repository) {
        this.repository = repository;
    }

    /**
     * 全てのWSmilePurchaseOutputFileを取得
     *
     * @return リスト形式のWSmilePurchaseOutputFile
     */
    public List<WSmilePurchaseOutputFile> findAll() {
        return repository.findAll();
    }

    /**
     * 主キーでWSmilePurchaseOutputFileを取得
     *
     * @param pk 主キー
     * @return Optional形式のWSmilePurchaseOutputFile
     */
    public Optional<WSmilePurchaseOutputFile> findById(WSmilePurchaseOutputFilePK pk) {
        return repository.findById(pk);
    }

    /**
     * WSmilePurchaseOutputFileを保存
     *
     * @param entity 保存するエンティティ
     * @return 保存されたエンティティ
     */
    public WSmilePurchaseOutputFile save(WSmilePurchaseOutputFile entity) {
        return repository.save(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void truncateTable() {
        log.info("WSmilePurchaseOutputFileテーブルをTRUNCATEします");
        repository.truncateTable();
        log.info("WSmilePurchaseOutputFileテーブルのTRUNCATE完了");
    }

    /**
     * WSmilePurchaseOutputFileを更新
     *
     * @param entity 更新するエンティティ
     * @return 更新されたエンティティ
     */
    public WSmilePurchaseOutputFile update(WSmilePurchaseOutputFile entity) {
        return repository.save(entity);
    }

    public Page<WSmilePurchaseOutputFile> findNewPurchases(Pageable pageable) {
        return repository.findNewPurchases(pageable);
    }

    public Page<WSmilePurchaseOutputFile> findModifiedPurchases(Pageable pageable) {
        return repository.findModifiedPurchases(pageable);
    }

    public List<WSmilePurchaseOutputFile> findByShopNoAndShoriRenban(int shopNo, long shorirenban) {
        return this.repository.findByShopNoAndShoriRenban(shopNo, shorirenban);
    }
}
