package jp.co.oda32.batch.purchase;

import jp.co.oda32.domain.model.master.MShopLinkedFile;
import jp.co.oda32.domain.service.master.MShopLinkedFileService;
import jp.co.oda32.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.*;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.MultiResourceItemReader;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * スマイル仕入ファイル取り込みバッチReaderクラス
 *
 * @author k_oda
 * @since 2018/06/26
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class PurchaseFileReader implements ItemReader<PurchaseFile> {
    @Autowired
    protected final MShopLinkedFileService mShopLinkedFileService;
    private MultiResourceItemReader<PurchaseFile> reader;

    /**
     * Reads a piece of input data and advance to the next one. Implementations
     * <strong>must</strong> return <code>null</code> at the end of the input
     * data set. In a transactional setting, caller might get the same item
     * twice from successive calls (or otherwise), if the first call was in a
     * transaction that rolled back.
     *
     * @return T the item to be processed
     * @throws ParseException                if there is a problem parsing the current record
     *                                       (but the next one may still be valid)
     * @throws NonTransientResourceException if there is a fatal exception in
     *                                       the underlying resource. After throwing this exception implementations
     *                                       should endeavour to return null from subsequent calls to read.
     * @throws UnexpectedInputException      if there is an uncategorised problem
     *                                       with the input data. Assume potentially transient, so subsequent calls to
     *                                       read might succeed.
     * @throws Exception                     if an there is a non-specific error.
     */
    @Override
    public PurchaseFile read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        return reader.read();
    }

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        List<MShopLinkedFile> mShopLinkedFileList = new ArrayList<>();
        try {
            // DBからデータを取得する
            log.info("DBからショップ連携ファイル情報を取得します");
            mShopLinkedFileList = this.mShopLinkedFileService.findAll();
            log.info("取得成功: {}件のレコードを取得しました", mShopLinkedFileList.size());

            mShopLinkedFileList = mShopLinkedFileList.stream()
                    .filter(shopLinkedFile -> !StringUtil.isEmpty(shopLinkedFile.getSmilePurchaseFileName()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // DBアクセスに失敗した場合は処理を終了する
            log.error("DBからのデータ取得に失敗しました。処理を終了します。", e);
            return;
        }

        if (mShopLinkedFileList.isEmpty()) {
            // 対象ファイルが存在しない場合は処理を終了する
            log.warn("対象となるファイルが存在しません。処理を終了します。");
            return;
        }

        log.info("処理対象ファイル数: {}", mShopLinkedFileList.size());
        for (MShopLinkedFile file : mShopLinkedFileList) {
            log.info("対象ファイル: {}, ショップNo: {}", file.getSmilePurchaseFileName(), file.getShopNo());
        }

        FileSystemResource[] resources = mShopLinkedFileList.stream()
                .map(shopLinkedFile -> new FileSystemResource(shopLinkedFile.getSmilePurchaseFileName()))
                .toArray(FileSystemResource[]::new);

        ExecutionContext jobContext = stepExecution.getJobExecution().getExecutionContext();

        // カスタムリーダーを使用
        this.reader = new ShopNoAwareItemReader(mShopLinkedFileList);
        this.reader.setStrict(false);
        this.reader.setResources(resources);

        FlatFileItemReader<PurchaseFile> fileReader = new FlatFileItemReader<>();
        fileReader.setEncoding("Unicode");
        fileReader.setLineMapper(new DefaultLineMapper<PurchaseFile>() {{
            setLineTokenizer(new DelimitedLineTokenizer() {{
                setNames(PurchaseFile.getPurchaseFileFormat());
            }});
            setFieldSetMapper(new BeanWrapperFieldSetMapper<PurchaseFile>() {{
                setTargetType(PurchaseFile.class);
                setDistanceLimit(0);
            }});
        }});

        this.reader.setDelegate(fileReader);
        this.reader.open(jobContext);
    }
}
