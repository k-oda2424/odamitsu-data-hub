package jp.co.oda32.batch.util;

import jp.co.oda32.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ファイル操作タスクレット
 *
 * @author k_oda
 * @since 2019/10/01
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class FileManagerTasklet implements Tasklet {
    private Resource resources;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        File srcFile = this.resources.getFile();

        String sb = srcFile.getParentFile().getParent() +
                File.separator +
                "completed" +
                File.separator +
                srcFile.getName() +
                "_" +
                DateTimeUtil.getNowTimestampStr();
        Path destPath = Paths.get(sb);
        try {
            Files.move(srcFile.toPath(), destPath);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return RepeatStatus.FINISHED;
    }

    public void setResources(Resource resources) {
        this.resources = resources;
    }

}
