package jp.co.oda32.domain.service.bcart;

import jp.co.oda32.domain.model.bcart.productSets.BCartSpecialPrice;
import jp.co.oda32.domain.repository.bcart.BCartSpecialPriceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BCartSpecialPriceService {

    @Autowired
    private BCartSpecialPriceRepository bCartSpecialPriceRepository;

    public List<BCartSpecialPrice> findByProductSetId(Long productSetId) {
        return this.bCartSpecialPriceRepository.findByProductSetId(productSetId);
    }

    @Transactional
    public BCartSpecialPrice save(BCartSpecialPrice bCartSpecialPrice) {
        return bCartSpecialPriceRepository.save(bCartSpecialPrice);
    }

    @Transactional
    public void saveAll(List<BCartSpecialPrice> bCartSpecialPriceList) {
        for (BCartSpecialPrice bCartSpecialPrice : bCartSpecialPriceList) {
            this.save(bCartSpecialPrice);
        }
    }

    @Transactional
    public void deleteAll(List<BCartSpecialPrice> bCartSpecialPriceList) {
        bCartSpecialPriceRepository.deleteAll(bCartSpecialPriceList);
    }
}
