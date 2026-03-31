package jp.co.oda32.domain.service.order;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.order.TReturn;
import jp.co.oda32.domain.repository.order.TReturnRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.order.TReturnSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 返品Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2018/11/23
 */
@Service
public class TReturnService extends CustomService {
    private final TReturnRepository tReturnRepository;
    private TReturnSpecification tReturnSpecification = new TReturnSpecification();

    @Autowired
    public TReturnService(TReturnRepository tReturnRepository) {
        this.tReturnRepository = tReturnRepository;
    }

    public List<TReturn> findAll() {
        return tReturnRepository.findAll();
    }

    /**
     * ユニークキーで検索します。
     *
     * @param shopNo       ショップ番号
     * @param returnSlipNo 返品伝票番号
     * @return 返品Entity
     */
    public TReturn getByShopNoAndReturnSlipNo(Integer shopNo, String returnSlipNo) {
        return tReturnRepository.getByShopNoAndReturnSlipNo(shopNo, returnSlipNo);
    }

    public TReturn getByPK(Integer returnNo) {
        return this.tReturnRepository.findById(returnNo).orElseThrow();
    }

    public List<TReturn> find(Integer shopNo, Integer companyNo, String orderStatus, LocalDateTime returnDateFrom, LocalDateTime returnDateTo, String returnSlipDate, Flag delFlg) {
        return this.tReturnRepository.findAll(Specification
                .where(this.tReturnSpecification.shopNoContains(shopNo))
                .and(this.tReturnSpecification.companyNoContains(companyNo))
                .and(this.tReturnSpecification.returnStatusContains(orderStatus))
                .and(this.tReturnSpecification.returnDateTimeContains(returnDateFrom, returnDateTo))
                .and(this.tReturnSpecification.returnSlipDateContains(returnSlipDate))
                .and(this.tReturnSpecification.delFlgContains(delFlg)));
    }

    /**
     * 返品を更新します。
     *
     * @param updateReturn 返品Entity
     * @return 更新した返品Entity
     * @throws Exception システム例外
     */
    public TReturn update(TReturn updateReturn) throws Exception {
        TReturn tReturn = this.getByPK(updateReturn.getReturnNo());
        if (tReturn == null) {
            // 存在しない
            throw new Exception(String.format("更新対象の返品番号が見つかりません。orderNo:%d", updateReturn.getReturnNo()));
        }
        return this.update(this.tReturnRepository, updateReturn);
    }

    /**
     * 返品を登録します。
     *
     * @param tReturn 返品Entity
     * @return 登録した返品Entity
     */
    public TReturn insert(TReturn tReturn) throws Exception {
        return this.insert(this.tReturnRepository, tReturn);
    }
}
