package jp.co.oda32.domain.repository.bcart;

import jp.co.oda32.domain.model.bcart.BCartOrder;

/**
 * BCartOrderRepositoryのカスタム拡張インターフェース
 */
public interface BCartOrderRepositoryCustom {

    /**
     * ネイティブSQLを使用してBCartOrderをデータベースに保存します
     * JSONB型のカラムはNULLとして処理します
     *
     * @param bCartOrder 保存するエンティティ
     * @return 保存されたエンティティ
     */
    BCartOrder saveWithNativeSql(BCartOrder bCartOrder);
}
