package jp.co.oda32.batch.purchase;

import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.*;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 商品ファイル取り込みバッチReaderクラス
 *
 * @author k_oda
 * @since 2018/06/26
 */
@Log4j2
@Component
public class PurchaseMasterFileReader implements ItemReader<PurchaseMasterFile> {
    private FlatFileItemReader<PurchaseMasterFile> reader;

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
    public PurchaseMasterFile read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        return reader.read();
    }

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        ExecutionContext jobContext = stepExecution.getJobExecution()
                .getExecutionContext();
        this.reader = new FlatFileItemReader<>();
        this.reader.setResource(new ClassPathResource("purchase_master_import.csv"));
        this.reader.setEncoding("Unicode");
        this.reader.setLineMapper(new DefaultLineMapper<PurchaseMasterFile>() {{
            setLineTokenizer(new DelimitedLineTokenizer() {{
                setNames(PurchaseMasterFile.getPurchaseMasterFileFormat());
            }});
            setFieldSetMapper(new BeanWrapperFieldSetMapper<PurchaseMasterFile>() {{
                setTargetType(PurchaseMasterFile.class);
            }});
        }});
        this.reader.open(jobContext);
    }
}
