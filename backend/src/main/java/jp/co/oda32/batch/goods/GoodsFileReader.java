package jp.co.oda32.batch.goods;

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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品ファイル取り込みバッチReaderクラス
 *
 * @author k_oda
 * @since 2018/06/26
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class GoodsFileReader implements ItemReader<GoodsFile> {
    @Autowired
    protected final MShopLinkedFileService mShopLinkedFileService;
    private MultiResourceItemReader<GoodsFile> reader;

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
    public GoodsFile read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        return reader.read();
    }

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        List<MShopLinkedFile> mShopLinkedFileList = this.mShopLinkedFileService.findAll();
        // SMILE注文入力ファイル名が指定されているものだけに絞る
        mShopLinkedFileList = mShopLinkedFileList.stream()
                .filter(shopLinkedFile -> !StringUtil.isEmpty(shopLinkedFile.getSmileGoodsImportFileName()))
                .collect(Collectors.toList());
        ExecutionContext jobContext = stepExecution.getJobExecution()
                .getExecutionContext();
        // MultiResourceItemReaderを初期化
        this.reader = new MultiResourceItemReader<>();
        this.reader.setStrict(false);
        this.reader.setResources(mShopLinkedFileList.stream()
                .map(shopLinkedFile -> Paths.get(shopLinkedFile.getSmileGoodsImportFileName()))
                .filter(path -> {
                    if (!Files.exists(path)) {
                        log.warn("File does not exist: " + path);
                        return false;
                    }
                    return true;
                })
                .map(FileSystemResource::new).toArray(FileSystemResource[]::new));
        FlatFileItemReader<GoodsFile> fileReader = new FlatFileItemReader<>();
        fileReader.setEncoding("Unicode");
        fileReader.setLineMapper(new DefaultLineMapper<GoodsFile>() {{
            setLineTokenizer(new DelimitedLineTokenizer() {{
                setNames(GoodsFile.getGoodsFileFormat());
            }});
            setFieldSetMapper(new BeanWrapperFieldSetMapper<GoodsFile>() {{
                setTargetType(GoodsFile.class);
                // 商品名など～名の日本語フィールドが多いので自動で曖昧一致されるのでされないようにするため設定
                setDistanceLimit(0);
            }});
        }});
        // デリゲートを設定
        this.reader.setDelegate(fileReader);
        // ジョブのコンテキストを開く
        this.reader.open(jobContext);
    }
}
