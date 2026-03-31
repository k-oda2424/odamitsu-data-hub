package jp.co.oda32.domain.repository.master;

import jp.co.oda32.domain.model.master.MShopLinkedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * ショップ関連ファイルマスタ(m_shop_linked_file)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2021/08/05
 */
public interface MShopLinkedFileRepository extends JpaRepository<MShopLinkedFile, Integer>, JpaSpecificationExecutor<MShopLinkedFile> {
    List<MShopLinkedFile> findAll();

    MShopLinkedFile getByShopNo(Integer shopNo);

}
