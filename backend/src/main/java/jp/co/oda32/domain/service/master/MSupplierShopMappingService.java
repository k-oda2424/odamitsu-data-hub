package jp.co.oda32.domain.service.master;

import jp.co.oda32.domain.model.master.MSupplierShopMapping;
import jp.co.oda32.domain.repository.master.MSupplierShopMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MSupplierShopMappingService {

    private final MSupplierShopMappingRepository mSupplierShopMappingRepository;

    /**
     * ソースのショップ番号と仕入先コードから対応するマッピングを検索
     */
    public Optional<MSupplierShopMapping> findBySourceShopNoAndSupplierCode(Integer shopNo, String supplierCode) {
        return mSupplierShopMappingRepository.findBySourceShopNoAndSupplierCode(shopNo, supplierCode);
    }

    /**
     * ターゲットのショップ番号と仕入先コードに対応するすべてのマッピングを検索
     */
    public List<MSupplierShopMapping> findAllByTargetShopNoAndSupplierCode(Integer shopNo, String supplierCode) {
        return mSupplierShopMappingRepository.findAllByTargetShopNoAndSupplierCode(shopNo, supplierCode);
    }

    /**
     * マッピングを保存
     */
    @Transactional
    public MSupplierShopMapping save(MSupplierShopMapping mapping) {
        return mSupplierShopMappingRepository.save(mapping);
    }

    /**
     * マッピングを削除（論理削除）
     */
    @Transactional
    public void delete(MSupplierShopMapping mapping) {
        mapping.setDelFlg("1");
        mSupplierShopMappingRepository.save(mapping);
    }

    /**
     * 全てのマッピングを取得します。
     */
    @Transactional(readOnly = true)
    public List<MSupplierShopMapping> findAll() {
        return mSupplierShopMappingRepository.findAll();
    }

    /**
     * マッピングIDによる検索
     */
    @Transactional(readOnly = true)
    public Optional<MSupplierShopMapping> findById(Integer mappingId) {
        return mSupplierShopMappingRepository.findById(mappingId);
    }

    /**
     * 特定のショップ番号の全仕入先マッピングを取得します。
     */
    @Transactional(readOnly = true)
    public List<MSupplierShopMapping> findBySourceShopNo(Integer shopNo) {
        return mSupplierShopMappingRepository.findBySourceShopNo(shopNo);
    }

    /**
     * CSVからマッピングデータを一括インポートします。
     * CSV形式: source_shop_no,source_supplier_code,target_shop_no,target_supplier_code
     */
    @Transactional
    public void importFromCsv(InputStream inputStream, Integer loginUserNo) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // ヘッダー行をスキップ
                }

                String[] fields = line.split(",");
                if (fields.length != 4) {
                    continue; // 不正な行はスキップ
                }

                try {
                    Integer sourceShopNo = Integer.parseInt(fields[0].trim());
                    String sourceSupplierCode = fields[1].trim();
                    Integer targetShopNo = Integer.parseInt(fields[2].trim());
                    String targetSupplierCode = fields[3].trim();

                    // 既存のマッピングを検索
                    Optional<MSupplierShopMapping> existingMapping =
                            findBySourceShopNoAndSupplierCode(sourceShopNo, sourceSupplierCode);

                    Timestamp now = Timestamp.valueOf(LocalDateTime.now());

                    if (existingMapping.isPresent()) {
                        // 既存マッピングの更新
                        MSupplierShopMapping mapping = existingMapping.get();
                        mapping.setTargetShopNo(targetShopNo);
                        mapping.setTargetSupplierCode(targetSupplierCode);
                        mapping.setModifyDateTime(now);
                        mapping.setModifyUserNo(loginUserNo);
                        mapping.setDelFlg("0"); // 削除フラグを解除

                        save(mapping);
                    } else {
                        // 新規マッピングの作成
                        MSupplierShopMapping mapping = MSupplierShopMapping.builder()
                                .sourceShopNo(sourceShopNo)
                                .sourceSupplierCode(sourceSupplierCode)
                                .targetShopNo(targetShopNo)
                                .targetSupplierCode(targetSupplierCode)
                                .delFlg("0")
                                .addDateTime(now)
                                .addUserNo(loginUserNo)
                                .build();

                        save(mapping);
                    }
                } catch (NumberFormatException e) {
                    // 数値変換に失敗した行はスキップ
                    continue;
                }
            }
        }
    }
}
