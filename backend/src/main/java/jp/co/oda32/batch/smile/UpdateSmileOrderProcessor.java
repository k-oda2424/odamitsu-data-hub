package jp.co.oda32.batch.smile;

import jp.co.oda32.domain.model.smile.WSmileOrderOutputFile;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * smile売上明細データから更新されたデータを処理するTasklet
 *
 * @author k_oda
 * @since 2024/05/13
 */
@Log4j2
@Component
public class UpdateSmileOrderProcessor {
    @Autowired
    SmileOrderUpdateService smileOrderUpdateService;

    public void modifiedOrderProcess() throws Exception {
        // 更新が必要な行
        int pageNumber = 0;

        List<WSmileOrderOutputFile> modifiedOrderList;
        int pageSize = 1000;
        do {
            modifiedOrderList = this.smileOrderUpdateService.findModifiedOrders(pageSize, pageNumber);
            log.info(String.format("受注情報更新中:%d件～%d件", pageNumber * pageSize + 1, pageNumber * pageSize + modifiedOrderList.size()));
            // 前処理
            smileOrderUpdateService.preProcess(modifiedOrderList);

            // 得意先名など変更があるか確認
            smileOrderUpdateService.updatePartner(modifiedOrderList);

            // 注文更新処理
            updateModifiedOrder(modifiedOrderList);
            pageNumber++;
        } while (!modifiedOrderList.isEmpty());
    }


    private void updateModifiedOrder(List<WSmileOrderOutputFile> modifiedOrderList) throws Exception {
        // modifiedOrderListから処理連番とショップ番号の組み合わせでまとまりのMapを作成する
        Map<String, List<WSmileOrderOutputFile>> renbanAndShopNoMap = modifiedOrderList.stream()
                .collect(Collectors.groupingBy(
                        file -> file.getShoriRenban() + "_" + file.getShopNo()
                ));

        // renbanAndShopNoMapをforでまわす
        for (Map.Entry<String, List<WSmileOrderOutputFile>> entry : renbanAndShopNoMap.entrySet()) {
            String[] keys = entry.getKey().split("_");
            Long shoriRenban = Long.parseLong(keys[0]); // 処理連番
            int shopNo = Integer.parseInt(keys[1]); // ショップ番号
            // 必要に応じてshopNoも使用して処理を行う
            smileOrderUpdateService.updateOrder(shopNo, shoriRenban, entry.getValue());
        }
    }

}
