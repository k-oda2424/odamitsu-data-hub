package jp.co.oda32.batch.smile;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.*;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * SMILE支払明細CSVファイル読み込みReaderクラス
 *
 * @author ai_assistant
 * @since 2025/05/02
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class SmilePaymentFileReader implements ItemReader<SmilePaymentFile> {

    private FlatFileItemReader<SmilePaymentFile> reader;
    private String inputFile;

    /**
     * アイテムを読み込み、次に進みます。
     *
     * @return 処理すべきアイテム、終了時はnull
     */
    @Override
    public SmilePaymentFile read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        return reader != null ? reader.read() : null;
    }

    /**
     * ステップ実行前の初期化処理
     */
    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        // ジョブパラメータからファイルパスを取得
        this.inputFile = stepExecution.getJobParameters().getString("inputFile");

        if (inputFile == null || inputFile.trim().isEmpty()) {
            log.error("inputFile パラメータが指定されていません");
            throw new IllegalArgumentException("inputFile パラメータが指定されていません");
        }

        // ファイルの存在確認
        Path path = Paths.get(inputFile);
        if (!Files.exists(path)) {
            log.error("指定されたファイルが存在しません: {}", inputFile);
            throw new IllegalArgumentException("指定されたファイルが存在しません: " + inputFile);
        }

        log.info("SMILEからの支払明細CSVを読み込みます: {}", inputFile);

        // ExecutionContext を取得
        ExecutionContext jobContext = stepExecution.getJobExecution().getExecutionContext();

        // CSVファイル読み込み設定
        this.reader = new FlatFileItemReader<>();
        this.reader.setResource(new FileSystemResource(path));
        this.reader.setEncoding("Unicode"); // SMILEファイルのエンコーディング
        this.reader.setLinesToSkip(0); // ヘッダーなしを想定

        // 最初の行を読んでヘッダー行かどうか確認し、ヘッダー行ならスキップするよう設定
        try {
            String firstLine = Files.lines(path, java.nio.charset.Charset.forName("Unicode")).findFirst().orElse("");
            if (firstLine.contains("伝票日付")) {
                this.reader.setLinesToSkip(1);
                log.info("ヘッダー行を検出しました。1行目をスキップします");
            }
        } catch (Exception e) {
            log.warn("ヘッダー行チェック中にエラーが発生しました: {}", e.getMessage());
        }

        // 行のマッピング設定
        DefaultLineMapper<SmilePaymentFile> lineMapper = new DefaultLineMapper<>();

        // トークナイザの設定（CSVの列をフィールドに分解）
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setDelimiter(",");
        tokenizer.setQuoteCharacter('"');
        tokenizer.setNames(SmilePaymentFile.getSmilePaymentFileFormat());
        tokenizer.setStrict(false); // フィールド数が一致しなくてもエラーにしない
        lineMapper.setLineTokenizer(tokenizer);

        // フィールドセットマッパーの設定（分解されたフィールドをオブジェクトにマッピング）
        BeanWrapperFieldSetMapper<SmilePaymentFile> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
        fieldSetMapper.setTargetType(SmilePaymentFile.class);
        fieldSetMapper.setDistanceLimit(0); // 曖昧一致を無効化
        lineMapper.setFieldSetMapper(fieldSetMapper);

        this.reader.setLineMapper(lineMapper);

        // リーダーをオープン
        this.reader.open(jobContext);
    }
}
