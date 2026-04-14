package jp.co.oda32.batch.bcart;

import jp.co.oda32.constant.OfficeShopNo;
import jp.co.oda32.domain.model.bcart.BCartMember;
import jp.co.oda32.domain.model.master.MShopLinkedFile;
import jp.co.oda32.domain.service.bcart.BCartMemberService;
import jp.co.oda32.domain.service.master.MShopLinkedFileService;
import jp.co.oda32.util.FileUtil;
import jp.co.oda32.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Smile連携用SmilePartnerImportFileデータをCSV出力するタスクレットクラス
 *
 * @author k_oda
 * @since 2023/06/27
 */
@Component
@Log4j2
@RequiredArgsConstructor
@StepScope
public class SmilePartnerFileOutPutTasklet implements Tasklet {
    private final BCartMemberService bCartMemberService;
    private final MShopLinkedFileService mShopLinkedFileService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // 連携していない行を取得
        List<BCartMember> smilePartnerNotLinkedList = this.bCartMemberService.findBySmilePartnerMasterNotLinked();
        MShopLinkedFile mShopLinkedFile = this.mShopLinkedFileService.getByShopNo(OfficeShopNo.B_CART_ORDER.getValue());
        if (mShopLinkedFile == null) {
            throw new Exception("SmileOrderFileOutPutTasklet：ショップ連携ファイルEntityが取得できませんでした。");
        }
        exportToCsv(smilePartnerNotLinkedList, mShopLinkedFile.getSmilePartnerOutputFileName());
        return RepeatStatus.FINISHED;
    }

    private void exportToCsv(List<BCartMember> bCartMemberList, String outputFilePath) {
        FileUtil.renameCurrentFile(outputFilePath);
        CSVFormat csvFormat = CSVFormat.DEFAULT
                .withHeader(SmilePartnerImportCsvHeader.CSV_HEADERS)
                .withQuoteMode(QuoteMode.ALL_NON_NULL);

        // 伝票番号、明細番号で並べ替えをする。途中で軽減税率が混ざった場合、smileが別の連番を取得する仕様のため
        try (FileOutputStream fos = new FileOutputStream(outputFilePath);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             BufferedWriter writer = new BufferedWriter(osw);
             CSVPrinter csvPrinter = new CSVPrinter(writer, csvFormat)) {

            for (BCartMember bCartMember : bCartMemberList) {
                csvPrinter.printRecord(
                        bCartMember.getExtId(),// 得意先コード	文字 6桁
                        bCartMember.getCompName(),// 得意先名１	文字 48桁
                        null,// 得意先名２	文字 48桁
                        null,// 得意先名略称	文字 32桁
                        StringUtil.convertToHalfWidthIncludingKatakana(bCartMember.getCompNameKana()),// 得意先名索引	文字 10桁
                        bCartMember.getZip(),// 郵便番号	文字 10桁
                        bCartMember.getPref() + bCartMember.getAddress1(),// 住所１	文字 48桁
                        bCartMember.getAddress2(),// 住所２	文字 48桁
                        bCartMember.getAddress3(),// 住所３	文字 48桁
                        null,// カスタマバーコード	文字 20桁
                        bCartMember.getTel(),// 電話番号	文字 15桁
                        bCartMember.getFax(),// ＦＡＸ番号	文字 15桁
                        null,// 営業所	文字 8桁
                        null,// 部門	文字 8桁
                        null,// 地区	文字 8桁
                        null,// 業種	文字 8桁
                        null,// グループ	文字 8桁
                        null,// 単価ランク	文字 8桁
                        null,// 分類６	文字 8桁
                        null,// 分類７	文字 8桁
                        null,// 分類８	文字 8桁
                        null,// 分類９	文字 8桁
                        null,// 請求先コード	文字 6桁
                        0,// 請求先区分	整数 1桁
                        "000005",// 担当者コード	文字 6桁
                        0,// 締日１	整数 2桁
                        null,// 締日２	整数 2桁
                        null,// 締日３	整数 2桁
                        0,// 入金日１	整数 2桁
                        null,// 入金日２	整数 2桁
                        null,// 入金日３	整数 2桁
                        null,// 入金サイクル１	整数 1桁
                        null,// 入金サイクル２	整数 1桁
                        null,// 入金サイクル３	整数 1桁
                        null,// 入金条件１	整数 1桁
                        null,// 入金条件２	整数 1桁
                        null,// 入金条件３	整数 1桁
                        null,// 与信限度額	整数 13桁
                        0,// 売上単価計算区分	整数 1桁
                        null,// 単価掛率	整数 3桁、小数 1桁
                        0,// 単価ランク	整数 1桁
                        0,// 単価処理区分	整数 1桁
                        0.01,// 単価処理単位	整数 5桁、小数 2桁
                        2,// 金額処理区分	整数 1桁
                        0,// 課税対象区分	整数 1桁
                        0,// 消費税売上単価設定区分	整数 1桁
                        0,// 消費税上代単価設定区分	整数 1桁
                        0,// 単価変換区分	整数 1桁
                        1,// 消費税通知区分	整数 1桁
                        0,// 消費税計算処理区分	整数 1桁
                        1,// 消費税計算処理単位	整数 5桁
                        0,// 消費税分解処理区分	整数 1桁
                        0,// 請求書出力タイプ	整数 1桁
                        null,// 請求書出力形式	整数 1桁
                        null,// 得意先台帳出力形式	整数 1桁
                        null,// 請求消費税算出単位	整数 1桁
                        0,// 売掛前残高	整数 13桁
                        0,// 前回請求残高	整数 13桁
                        null,// 相殺仕入先コード	文字 6桁
                        0,// 日付印字区分	整数 1桁
                        bCartMember.getTantoLastName() + bCartMember.getTantoFirstName(),// 相手先担当者名	文字 32桁
                        0,// 取引	整数 1桁
                        "0000",// 売上伝票会社名パターン	文字 4桁
                        "0000",// 見積書会社名パターン	文字 4桁
                        "0000",// 請求書会社名パターン	文字 4桁
                        0,// マスター検索表示区分	整数 1桁
                        0,// 伝票出力区分	整数 1桁
                        0,// 商品変換コード伝票出力	整数 1桁
                        0,// 商品変換コード見積書出力	整数 1桁
                        0,// 商品変換コード請求書出力	整数 1桁
                        0,// 商品ｺｰﾄﾞ入力ﾓｰﾄﾞ初期値	整数 1桁
                        0,// 個別設定入力行数	整数 3桁
                        null,// 社店コード	文字 6桁
                        null,// 分類コード	文字 4桁
                        null,// 伝票区分	文字 2桁
                        null,// 取引先コード	文字 6桁
                        null,// 有効期間開始日	整数 8桁
                        null// 有効期間終了日	整数 8桁
                );
            }
            csvPrinter.flush();
            List<BCartMember> updatedOutputList = bCartMemberList.stream()
                    .peek(bCartMember -> bCartMember.setSmilePartnerMasterLinked(true))
                    .collect(Collectors.toList());
            this.bCartMemberService.save(updatedOutputList);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
