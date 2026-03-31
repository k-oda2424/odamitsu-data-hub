package jp.co.oda32.batch.smile;

import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.*;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * 得意先ファイル取り込みバッチReaderクラス
 *
 * @author k_oda
 * @return T the item to be processed
 * @throws ParseException                if there is a problem parsing the current record
 * (but the next one may still be valid)
 * @throws NonTransientResourceException if there is a fatal exception in
 * the underlying resource. After throwing this exception implementations
 * should endeavour to return null from subsequent calls to read.
 * @throws UnexpectedInputException      if there is an uncategorised problem
 * with the input data. Assume potentially transient, so subsequent calls to
 * read might succeed.
 * @throws Exception                     if an there is a non-specific error.
 * @since 2024/06/11
 * Reads a piece of input data and advance to the next one. Implementations
 * <strong>must</strong> return <code>null</code> at the end of the input
 * data set. In a transactional setting, caller might get the same item
 * twice from successive calls (or otherwise), if the first call was in a
 * transaction that rolled back.
 */
@Log4j2
@Component
public class PartnerFileReader implements ItemStreamReader<PartnerFile> {
    private FlatFileItemReader<PartnerFile> reader;

    private String filePath = "input/partner_import.csv";
    private String fileEncoding = "Unicode";

    @Override
    public PartnerFile read() throws Exception {
        if (reader == null) {
            throw new IllegalStateException("Readerは適切に初期化されていません。FileReaderがnullです。");
        }
        return reader.read();
    }

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) throws IOException, URISyntaxException {
        ExecutionContext jobContext = stepExecution.getJobExecution().getExecutionContext();
        URL fileUrl = ClassLoader.getSystemResource(filePath);
        if (fileUrl == null) {
            log.warn("提供されたファイルパスがクラスパス上に見つかりません：" + filePath);
            return;
        }
        reader = createReader(jobContext, fileUrl);
    }

    private FlatFileItemReader<PartnerFile> createReader(ExecutionContext context, URL fileUrl) throws IOException {
        FlatFileItemReader<PartnerFile> reader = new FlatFileItemReader<>();
        reader.setResource(new UrlResource(fileUrl));
        reader.setEncoding(fileEncoding);
        reader.setLineMapper(new DefaultLineMapper<PartnerFile>() {{
            setLineTokenizer(new DelimitedLineTokenizer() {{
                setNames(PartnerFile.getPartnerFileFormat());
            }});
            setFieldSetMapper(new BeanWrapperFieldSetMapper<PartnerFile>() {{
                setTargetType(PartnerFile.class);
            }});
        }});
        // Rest of your code
        reader.open(context);
        return reader;
    }

    // New methods for ItemStreamReader interface
    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        if (reader == null) {
            return;
        }
        reader.open(executionContext);
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        if (reader == null) {
            return;
        }
        reader.update(executionContext);
    }

    @Override
    public void close() throws ItemStreamException {
        if (reader == null) {
            return;
        }
        reader.close();
    }

}
