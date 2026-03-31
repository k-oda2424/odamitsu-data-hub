package jp.co.oda32.domain.service.bcart;

import jp.co.oda32.domain.model.bcart.productSets.BCartGroupPrice;
import jp.co.oda32.domain.repository.bcart.BCartGroupPriceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BCartGroupPriceService {

    @Autowired
    private BCartGroupPriceRepository bCartGroupPriceRepository;

    public List<BCartGroupPrice> findByProductSetId(Long productSetId) {
        return bCartGroupPriceRepository.findByProductSetId(productSetId);
    }

    public BCartGroupPrice save(BCartGroupPrice bCartGroupPrice) {
        return bCartGroupPriceRepository.save(bCartGroupPrice);
    }

    @Transactional
    public List<BCartGroupPrice> saveAll(List<BCartGroupPrice> bCartGroupPriceList) {
        return bCartGroupPriceRepository.saveAll(bCartGroupPriceList);
    }

    @Transactional
    public void deleteAll(List<BCartGroupPrice> bCartGroupPriceList) {
        bCartGroupPriceRepository.deleteInBatch(bCartGroupPriceList);
    }
}
