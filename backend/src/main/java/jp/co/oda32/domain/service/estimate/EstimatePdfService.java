package jp.co.oda32.domain.service.estimate;

import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jp.co.oda32.domain.model.estimate.TEstimate;
import jp.co.oda32.domain.model.estimate.TEstimateDetail;
import jp.co.oda32.dto.estimate.EstimateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EstimatePdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy年M月d日");

    public byte[] generatePdf(TEstimate estimate, String userName) throws Exception {
        BaseFont bf = BaseFont.createFont("HeiseiKakuGo-W5", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font f20b = new Font(bf, 20, Font.BOLD);   // 御見積書タイトル
        Font f14b = new Font(bf, 14, Font.BOLD);   // 得意先名, 会社名
        Font f11  = new Font(bf, 11);               // 本文
        Font f10  = new Font(bf, 10);               // テーブル内
        Font f10b = new Font(bf, 10, Font.BOLD);    // テーブルヘッダ, ラベル
        Font f9   = new Font(bf, 9);                // 小さめ

        // 得意先名の取得
        EstimateResponse resp = EstimateResponse.from(estimate);
        String partnerName = resp.getPartnerName() != null ? resp.getPartnerName() : "";

        String validityText = estimate.getPriceChangeDate() != null
                && estimate.getPriceChangeDate().equals(estimate.getEstimateDate())
                ? "御見積日より1ヵ月"
                : fmtDate(estimate.getPriceChangeDate()) + " 納品分より";

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 30, 30);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        // ========== 1行目: 御見積書（中央） + 御見積日（右） ==========
        PdfPTable row1 = table(3, 100, 1f, 1f, 1f);
        row1.addCell(noBorderCell("", f11, Element.ALIGN_LEFT));
        row1.addCell(noBorderCell("御見積書", f20b, Element.ALIGN_CENTER));
        row1.addCell(noBorderCell("御見積日　" + fmtDate(estimate.getEstimateDate()), f11, Element.ALIGN_RIGHT, Element.ALIGN_BOTTOM));
        doc.add(row1);
        doc.add(spacer(8));

        // ========== 2行目: 得意先名 御中（下線付き） ==========
        // 担当者名がある場合: 「{得意先名}　{担当者名} 様」
        // 担当者名がない場合: 「{得意先名} 御中」
        String recipientName = estimate.getRecipientName();
        boolean hasRecipient = recipientName != null && !recipientName.isBlank();
        String nameText = partnerName + "　";
        String suffixText;
        String middleText = "";
        if (hasRecipient) {
            middleText = recipientName + " ";
            suffixText = "様";
        } else {
            suffixText = "御中";
        }
        float nameWidth = bf.getWidthPoint(nameText, 14)
                + bf.getWidthPoint(middleText, 14)
                + bf.getWidthPoint(suffixText, 12) + 10;
        // A4の有効幅（ポイント）: 595 - 36*2 = 523
        float pageContentWidth = PageSize.A4.getWidth() - 72;
        float ulWidthPct = Math.min((nameWidth / pageContentWidth) * 100 + 2, 80);

        Paragraph ptn = new Paragraph();
        Chunk cName = new Chunk(nameText, f14b);
        ptn.add(cName);
        if (hasRecipient) {
            ptn.add(new Chunk(middleText, f14b));
        }
        Chunk cSuffix = new Chunk(suffixText, new Font(bf, 12));
        ptn.add(cSuffix);
        doc.add(ptn);
        PdfPTable ul = table(1, ulWidthPct);
        PdfPCell ulc = new PdfPCell();
        ulc.setFixedHeight(2);
        ulc.setBorder(Rectangle.BOTTOM);
        ulc.setBorderWidthBottom(1.5f);
        ulc.setBorderColorBottom(Color.BLACK);
        ul.addCell(ulc);
        ul.setHorizontalAlignment(Element.ALIGN_LEFT);
        doc.add(ul);
        doc.add(spacer(6));

        // ========== 3行目: 挨拶文（左55%） + 会社情報（右45%） ==========
        PdfPTable row3 = table(2, 100, 55f, 45f);

        // --- 左: 挨拶文 + 受け渡し場所 + 有効期限 ---
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPaddingRight(15);

        Paragraph greet = new Paragraph("下記の通り御見積り申し上げますので\n何卒ご用命賜りますようお願い申し上げます。", f11);
        greet.setLeading(16);
        greet.setSpacingAfter(10);
        leftCell.addElement(greet);

        // 条件テーブル
        PdfPTable condTbl = table(2, 85, 35f, 65f);
        condTbl.setHorizontalAlignment(Element.ALIGN_LEFT);
        String deliveryLocation = "貴社指定場所";
        if (estimate.getMDeliveryDestination() != null
                && estimate.getMDeliveryDestination().getDestinationName() != null
                && !estimate.getMDeliveryDestination().getDestinationName().isBlank()) {
            deliveryLocation = estimate.getMDeliveryDestination().getDestinationName();
        }
        addCondRow(condTbl, "受け渡し場所", deliveryLocation, f10b, f10);
        addCondRow(condTbl, "有効期限", validityText, f10b, f10);
        leftCell.addElement(condTbl);
        row3.addCell(leftCell);

        // --- 右: 会社情報 ---
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        // 内側を左寄せにするためネストテーブル使用
        PdfPTable companyTbl = table(1, 100);
        companyTbl.setHorizontalAlignment(Element.ALIGN_RIGHT);
        companyTbl.addCell(noBorderCell("小田光株式会社", f14b, Element.ALIGN_LEFT));
        companyTbl.addCell(noBorderCell("〒739-0615 広島県大竹市元町四丁目2-10", f10, Element.ALIGN_LEFT));
        companyTbl.addCell(noBorderCell("TEL 0827-53-2227 / FAX 0827-53-2228", f10, Element.ALIGN_LEFT));
        companyTbl.addCell(noBorderCell("登録番号:T5240001028409", f10, Element.ALIGN_LEFT));
        PdfPCell staffCell = noBorderCell("担当：" + (userName != null ? userName : ""), f10, Element.ALIGN_LEFT);
        staffCell.setPaddingTop(6);
        companyTbl.addCell(staffCell);
        rightCell.addElement(companyTbl);
        row3.addCell(rightCell);
        doc.add(row3);
        doc.add(spacer(5));

        // ========== 要件（社内メモnoteは印刷しない） ==========
        if (estimate.getRequirement() != null && !estimate.getRequirement().isBlank()) {
            PdfPTable reqBox = table(1, 100);
            PdfPCell rc = new PdfPCell(new Phrase("要件: " + estimate.getRequirement(), f10));
            rc.setPadding(5);
            rc.setBorderColor(Color.GRAY);
            reqBox.addCell(rc);
            doc.add(reqBox);
            doc.add(spacer(3));
        }

        // ========== 単位：円（右寄せ） ==========
        Paragraph unitP = new Paragraph("単位：円", f9);
        unitP.setAlignment(Element.ALIGN_RIGHT);
        doc.add(unitP);
        doc.add(spacer(2));

        // ========== 商品テーブル ==========
        PdfPTable prodTbl = table(6, 100, 12f, 30f, 12f, 8f, 15f, 23f);
        Color headerBg = new Color(240, 240, 240);
        String[] hdrs = {"ｺｰﾄﾞ", "商品名", "単価※", "入数", "ｹｰｽ価格※", "備考"};
        int[] hAlign = {Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_LEFT};
        for (int i = 0; i < hdrs.length; i++) {
            PdfPCell hc = new PdfPCell(new Phrase(hdrs[i], f10b));
            hc.setBackgroundColor(headerBg);
            hc.setHorizontalAlignment(hAlign[i]);
            hc.setPadding(4);
            hc.setBorderWidthTop(1f);
            hc.setBorderWidthBottom(1f);
            hc.setBorderWidthLeft(0);
            hc.setBorderWidthRight(0);
            hc.setBorderColorTop(Color.BLACK);
            hc.setBorderColorBottom(Color.BLACK);
            prodTbl.addCell(hc);
        }

        List<TEstimateDetail> details = estimate.getTEstimateDetailList().stream()
                .sorted(Comparator.comparingInt(TEstimateDetail::getDisplayOrder)
                        .thenComparing(d -> d.getGoodsCode() != null ? d.getGoodsCode() : ""))
                .toList();

        for (TEstimateDetail d : details) {
            BigDecimal price = d.getGoodsPrice() != null ? d.getGoodsPrice() : BigDecimal.ZERO;
            BigDecimal cNum = d.getChangeContainNum() != null ? d.getChangeContainNum()
                    : (d.getContainNum() != null ? d.getContainNum() : BigDecimal.ONE);
            BigDecimal casePrice = price.multiply(cNum);

            addProdCell(prodTbl, d.getGoodsCode(), f10, Element.ALIGN_LEFT);
            addProdCell(prodTbl, d.getGoodsName(), f10, Element.ALIGN_LEFT);
            addProdCell(prodTbl, fmtNum(price), f10, Element.ALIGN_RIGHT);
            addProdCell(prodTbl, cNum.stripTrailingZeros().toPlainString(), f10, Element.ALIGN_RIGHT);
            addProdCell(prodTbl, fmtNum(casePrice), f10b, Element.ALIGN_RIGHT);
            addProdCell(prodTbl, d.getDetailNote(), f9, Element.ALIGN_LEFT);
        }
        doc.add(prodTbl);
        doc.add(spacer(5));

        // ========== 消費税表示 ==========
        Paragraph taxP = new Paragraph(
                estimate.isIncludeTaxDisplay()
                        ? "※上記価格は税込です。"
                        : "※上記価格に消費税は含まれておりません。", f9);
        taxP.setAlignment(Element.ALIGN_RIGHT);
        doc.add(taxP);

        // ========== 提案文（明細テーブルの後に表示） ==========
        if (estimate.getProposalMessage() != null && !estimate.getProposalMessage().isBlank()) {
            doc.add(spacer(8));
            Paragraph proposal = new Paragraph(estimate.getProposalMessage(), f11);
            proposal.setLeading(16);
            doc.add(proposal);
        }

        doc.close();
        return baos.toByteArray();
    }

    // --- ヘルパーメソッド ---

    private PdfPTable table(int cols, float widthPct, float... colWidths) throws DocumentException {
        PdfPTable t = new PdfPTable(cols);
        t.setWidthPercentage(widthPct);
        if (colWidths.length > 0) t.setWidths(colWidths);
        return t;
    }

    private PdfPCell noBorderCell(String text, Font font, int hAlign) {
        return noBorderCell(text, font, hAlign, Element.ALIGN_MIDDLE);
    }

    private PdfPCell noBorderCell(String text, Font font, int hAlign, int vAlign) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(hAlign);
        c.setVerticalAlignment(vAlign);
        c.setPadding(2);
        return c;
    }

    private void addCondRow(PdfPTable tbl, String label, String value, Font lFont, Font vFont) {
        PdfPCell lc = new PdfPCell(new Phrase(label, lFont));
        lc.setBorder(Rectangle.BOTTOM);
        lc.setBorderWidthBottom(0.5f);
        lc.setBorderColorBottom(Color.GRAY);
        lc.setPadding(4);
        tbl.addCell(lc);
        PdfPCell vc = new PdfPCell(new Phrase(value, vFont));
        vc.setBorder(Rectangle.BOTTOM);
        vc.setBorderWidthBottom(0.5f);
        vc.setBorderColorBottom(Color.GRAY);
        vc.setPadding(4);
        tbl.addCell(vc);
    }

    private void addProdCell(PdfPTable tbl, String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", font));
        c.setHorizontalAlignment(align);
        c.setPadding(4);
        c.setBorderWidthTop(0);
        c.setBorderWidthLeft(0);
        c.setBorderWidthRight(0);
        c.setBorderWidthBottom(0.5f);
        c.setBorderColorBottom(Color.LIGHT_GRAY);
        tbl.addCell(c);
    }

    private Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(height);
        return p;
    }

    private String fmtDate(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : "-";
    }

    private String fmtNum(BigDecimal num) {
        if (num == null) return "-";
        return NumberFormat.getNumberInstance(Locale.JAPAN).format(num);
    }
}
