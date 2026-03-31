package jp.co.oda32.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ファイルに関するUtilクラス
 *
 * @author k_oda
 * @since 2023/04/20
 */
public class FileUtil {

    public static void renameCurrentFile(String outputFilePath) {
        String currentDateTime = DateTimeUtil.getNowTimestampStr();
        String fileNameWithoutExtension = outputFilePath.substring(0, outputFilePath.lastIndexOf('.'));
        String fileExtension = outputFilePath.substring(outputFilePath.lastIndexOf('.'));
        String renamedOutputFilePath = fileNameWithoutExtension + "_" + currentDateTime + fileExtension;

        Path originalFilePath = Paths.get(outputFilePath);
        Path backupFilePath = Paths.get(renamedOutputFilePath);

        if (Files.exists(originalFilePath)) {
            try {
                Files.move(originalFilePath, backupFilePath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
