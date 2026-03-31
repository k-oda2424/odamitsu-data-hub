package jp.co.oda32.batch.purchase;

import jp.co.oda32.domain.model.master.MShopLinkedFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.file.MultiResourceItemReader;
import org.springframework.core.io.Resource;

import java.nio.file.Paths;
import java.util.List;

@Slf4j
public class ShopNoAwareItemReader extends MultiResourceItemReader<PurchaseFile> {

    private List<MShopLinkedFile> shopLinkedFileList;  // 各リソースに対応するショップ情報を保持
    private Resource[] resources;

    // コンストラクタ
    public ShopNoAwareItemReader(List<MShopLinkedFile> shopLinkedFileList) {
        this.shopLinkedFileList = shopLinkedFileList;
    }

    @Override
    public void setResources(Resource[] resources) {
        this.resources = resources;
        super.setResources(resources);
    }

    @Override
    public PurchaseFile read() throws Exception {
        // 1行読み込む
        PurchaseFile purchaseFile = super.read();

        if (purchaseFile == null) {
            return null;
        }

        // リソースのファイル名から対応するshop_noを設定
        if (resources != null) {
            for (Resource resource : resources) {
                String currentFilename = resource.getFilename();
                if (currentFilename != null) {
                    MShopLinkedFile shopLinkedFile = shopLinkedFileList.stream()
                            .filter(file -> {
                                String fullPathFilename = Paths.get(file.getSmilePurchaseFileName()).getFileName().toString();
                                return currentFilename.equals(fullPathFilename);
                            })
                            .findFirst()
                            .orElse(null);

                    if (shopLinkedFile != null && purchaseFile.getShopNo() == 0) {
                        purchaseFile.setShopNo(shopLinkedFile.getShopNo());
                        break;
                    }
                }
            }

            if (purchaseFile.getShopNo() == 0) {
                log.warn("対応するファイル名が見つかりません");
            }
        }

        return purchaseFile;
    }
}
