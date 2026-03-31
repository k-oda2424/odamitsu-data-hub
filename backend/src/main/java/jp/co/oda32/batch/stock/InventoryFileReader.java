package jp.co.oda32.batch.stock;

import lombok.RequiredArgsConstructor;
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
 * 棚卸ファイル取り込みバッチReaderクラス
 *
 * @author k_oda
 * @since 2019/07/15
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class InventoryFileReader implements ItemReader<InventoryFile> {
    private FlatFileItemReader<InventoryFile> reader;

    @Override
    public InventoryFile read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        return reader.read();
    }

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        ExecutionContext jobContext = stepExecution.getJobExecution()
                .getExecutionContext();
        this.reader = new FlatFileItemReader<>();
        this.reader.setResource(new ClassPathResource("input/inventory_import.csv"));
        this.reader.setEncoding("Unicode");
        this.reader.setLineMapper(new DefaultLineMapper<InventoryFile>() {{
            setLineTokenizer(new DelimitedLineTokenizer() {{
                setNames(InventoryFile.getInventoryFileFormat());
            }});
            setFieldSetMapper(new BeanWrapperFieldSetMapper<InventoryFile>() {{
                setTargetType(InventoryFile.class);
            }});
        }});
        this.reader.open(jobContext);
    }
}
