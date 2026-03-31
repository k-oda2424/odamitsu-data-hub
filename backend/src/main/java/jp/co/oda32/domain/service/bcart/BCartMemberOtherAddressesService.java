package jp.co.oda32.domain.service.bcart;


import jp.co.oda32.domain.model.bcart.BCartMemberOtherAddresses;
import jp.co.oda32.domain.repository.bcart.BCartMemberOtherAddressesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BCartMemberOtherAddressesService {
    private final BCartMemberOtherAddressesRepository repository;

    @Transactional
    public BCartMemberOtherAddresses save(BCartMemberOtherAddresses memberOtherAddresses) {
        return repository.save(memberOtherAddresses);
    }
}
