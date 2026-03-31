package jp.co.oda32.domain.service.bcart;

import jp.co.oda32.domain.model.bcart.BCartProducts;
import jp.co.oda32.domain.repository.bcart.BCartProductsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BCartProductsService {

    private final BCartProductsRepository bCartProductsRepository;

    @Autowired
    public BCartProductsService(BCartProductsRepository bCartProductsRepository) {
        this.bCartProductsRepository = bCartProductsRepository;
    }

    @Transactional
    public BCartProducts save(BCartProducts bCartProducts) {
        return bCartProductsRepository.save(bCartProducts);
    }
}