package jp.co.oda32.domain.service.smile;

import jp.co.oda32.domain.model.embeddable.WSmileOrderOutputFilePK;
import jp.co.oda32.domain.model.smile.WSmileOrderOutputFile;
import jp.co.oda32.domain.repository.smile.WSmileOrderOutputFileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class WSmileOrderOutputFileService {

    private final WSmileOrderOutputFileRepository repository;

    @Autowired
    public WSmileOrderOutputFileService(WSmileOrderOutputFileRepository repository) {
        this.repository = repository;
    }

    public WSmileOrderOutputFile save(WSmileOrderOutputFile outputFile) {
        return repository.save(outputFile);
    }

    public Optional<WSmileOrderOutputFile> findById(WSmileOrderOutputFilePK id) {
        return repository.findById(id);
    }

    public Page<WSmileOrderOutputFile> findNewOrders(Pageable pageable) {
        return repository.findNewOrders(pageable);
    }

    public List<WSmileOrderOutputFile> findModifiedOrders(int pageSize, int pageNumber) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<WSmileOrderOutputFile> page = this.repository.findModifiedOrders(pageable);
        return page.getContent();
    }

    public void truncateTable() {
        repository.truncateTable();
    }

    public List<WSmileOrderOutputFile> findByShopNoAndShoriRenban(int shopNo, long shorirenban) {
        return this.repository.findByShopNoAndShoriRenban(shopNo, shorirenban);
    }

    public List<WSmileOrderOutputFile> handleWSmileOrderOutputFiles(int pageNumber) {
        int pageSize = 1000;

        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<WSmileOrderOutputFile> page = repository.findByTorihikiKubun(new BigDecimal(1), pageable);

        return page.getContent();
    }
}
