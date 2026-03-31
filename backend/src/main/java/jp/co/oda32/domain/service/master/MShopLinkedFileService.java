package jp.co.oda32.domain.service.master;

import jp.co.oda32.domain.model.master.MShopLinkedFile;
import jp.co.oda32.domain.repository.master.MShopLinkedFileRepository;
import jp.co.oda32.domain.service.CustomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ショップ連携ファイルEntity操作用サービスクラス
 *
 * @author k_oda
 * @since 2021/08/05
 */
@Service
public class MShopLinkedFileService extends CustomService {
    private final MShopLinkedFileRepository mShopLinkedFileRepository;

    @Autowired
    public MShopLinkedFileService(MShopLinkedFileRepository shopRepository) {
        this.mShopLinkedFileRepository = shopRepository;
    }

    public List<MShopLinkedFile> findAll() {
        return mShopLinkedFileRepository.findAll();
    }

    /**
     * PKのshopNoで検索します。
     *
     * @param shopNo ショップ番号
     * @return ショップ番号に紐づくショップ連携ファイルEntity
     */
    public MShopLinkedFile getByShopNo(Integer shopNo) {
        return this.mShopLinkedFileRepository.getByShopNo(shopNo);
    }
}
